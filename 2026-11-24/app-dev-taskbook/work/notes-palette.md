# 调色板预览色偏：根因与修复（已改未提交）

## 现象

APP 调色板色块颜色与灯上实际渐变不一致。灯不受影响（下发的始终是 `seg.pal` 索引），
错的是 APP 侧用来画色块的 16 个颜色。

## 根因

`tools/gen_palette_catalog.py` 解析固件 `palettes.cpp` 的渐变调色板时，把每 4 字节
`(pos, r, g, b)` 当成 `(r, g, b)` 读取：

```python
for i in range(0, len(raw) - 3, 4):
    r, g, b = raw[i], raw[i + 1], raw[i + 2]   # raw[i] 实际是 pos
```

导致：
1. 颜色整体错位一字节；
2. 丢失色标位置 `pos`；
3. 6–8 个色标没有按 FastLED 规则展开成 16 色。

影响 60 个渐变调色板（id 13–72）；FastLED 形式（id 6–12）不受影响。

## 实测对比

- 固件 `Aurora_gp` 色标：
  `[(0,1,5,45),(64,0,200,23),(128,0,255,0),(170,0,243,45),(200,0,135,7),(255,1,5,45)]`
- 修复前 APP 生成（id 50 Aurora）：
  `000105, 4000C8, 8000FF, AA00F3, C80087, FF0105`（紫红系）
- 修复后：
  `01052D, 003527, 006622, 00981B, 00CB16, 00DA0F, 00E909, 00F703, 00FF00, 00F81B, 00F32D, 00B617, 007E0A, 005516, 002822, 01052D`（绿色系，中点为 00FF00）

## 修复

`tools/gen_palette_catalog.py`：

- 新增 `_expand_gradient(stops)`：按 `pos` 采样 16 个位置（0/16/…/240），逐通道线性插值；
- 解析改为 `(pos, r, g, b)`。

重新生成后：68 个调色板各 16 色；id 1–5（`*` 动态调色板）无固定色，色块改用中性底纹。

## 状态

已修改、未提交：
- `tools/gen_palette_catalog.py`
- `app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/effects/PaletteCatalog.kt`

## 同轮修复：字幕朝向

固件 `FX.cpp:6659-6660`：

```cpp
const int8_t rotate = map(SEGMENT.custom3, 0, 31, -2, 2);
// -2/+2 = upside down, 0 = normal
```

APP 之前下发 `custom3 = 0` → `rotate = -2`（上下颠倒）。已改为 `custom3 = 16`（`rotate = 0`），
常量 `SCROLLING_TEXT_ROTATE_NORMAL`，文件：
`app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/create/CreateViewModel.kt`。
