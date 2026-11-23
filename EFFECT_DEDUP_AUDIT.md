# 灯效去重审核与 GIF 预览方案

日期：2026-11-23
状态：审核报告，删除执行待确认范围

---

## 1. 现状盘点

| 指标 | 数值 | 说明 |
|---|---|---|
| 固件 `addEffect` 调用总数 | 217 | `FX.cpp` 的 `setupEffectData()` |
| 上次去重已屏蔽 | 29 | 用 `#ifdef WLED_KEEP_ALL_FX` 包裹，加宏可恢复 |
| 当前启用 | **187** | 1D 类 133 + 2D/粒子类 54 |
| 灯珠配置 | 60 颗 WS2812 | `platformio_override.ini:32` |

去重机制：`FX.cpp` 中被合并的效果包在 `#ifdef WLED_KEEP_ALL_FX ... #endif // dedup: ~ 保留项` 中，`FX_fcn.cpp:713` 的 `fxDedupAlias` 表把旧 ID 映射到保留 ID。**这是可逆的软删除，优于物理删除源码。**

---

## 2. 审核方法

1. 从 4 个源文件提取每个效果的实现函数体（182/187 成功）；
2. 两两计算代码相似度（difflib，阈值 ≥0.80）；
3. 识别"薄封装共享基础函数"的效果组——**最客观的重复证据**；
4. 按名称语义归族，供人工判断。

> 注意：代码相似度会被"共享模板代码"抬高（如随机选灯、时间步进），因此高相似 ≠ 真重复，需结合调用关系判断。

---

## 3. 客观重复组（共享同一实现，建议处理）

| 组 | 效果 A | 效果 B | 证据 | 建议 |
|---|---|---|---|---|
| 擦除类 | `Wipe` (COLOR_WIPE) | `Sweep` (COLOR_SWEEP) | 二者均调用同一 `color_wipe(mirror, sweep)`，仅 sweep 布尔不同 | 保留 Wipe，删除 Sweep（或反之） |
| 重力类 | `Gravcenter` (GRAVCENTER) | `Gravimeter` (GRAVIMETER) | 均调用 `mode_gravcenter_base(0)` / `(2)`，仅参数不同 | 保留 Gravcenter，删除 Gravimeter |

这两组是"同一函数不同入参"，视觉差异最小，属**高置信度重复**。

---

## 4. 同族变体（建议保留，除非追求极简）

以下按名称归族，实现有差异、视觉可辨，**不建议默认删除**；若追求极简可选择性精简：

| 族 | 成员 |
|---|---|
| Chase 追逐 | Chase、Chase Flash、Chase Flash Rnd、Chase 3、Rainbow Runner |
| Sparkle 闪光 | Sparkle Dark、Sparkle、Glitter、Solid Glitter |
| Twinkle 闪烁 | Twinkle、Colortwinkles、Fairytwinkle、Twinklefox、Twinkleup |
| Fireworks 烟花 | Fireworks、Fireworks 1D、Fireworks Starburst、PS Fireworks、PS Fireworks 1D |
| Fire 火焰 | Fire 2012、Fire Flicker、Noisefire、PS Fire、PS Fire 1D |
| Noise 噪声 | Fill Noise、Noise 1、Noise 4、Noise Pal、Midnoise、Noisemeter、Noisemove |
| Candle 烛光 | Candle、Candle Multi |
| Meteor 流星 | Meteor、Multi Comet |
| Stream 流动 | Stream、Stream 2 |

---

## 5. 2D / 粒子类（54 个）—— 前提已满足（2026-11-23 修正）

这 54 个效果（`FX_MODE_2D*` 与 `FX_MODE_PARTICLE*`）需要二维矩阵坐标系。

**结论：本设备满足前提，无需隐藏或删除。** 硬件为 9 列 × 23 颗（207 颗）S 形缠绕在 φ50mm 圆管外壁，属标准蛇形 2D 布线；只需在固件 LED Preferences 配 `panel W=9 / H=23 / vertical=1 / serpentine`，"字画"分类与这 54 个效果均可正常渲染（详见 `APP_REDESIGN_SPEC.md` 7.2）。

- ~~若硬件为 1D 环形灯带 → 隐藏 2D 类~~（不适用）
- 真正要做的只有一件事：把 `PIXEL_COUNTS` 从 60 改为 207 并补矩阵配置。
- 若将来有 1D 型号，App 侧按 `leds.matrix.h > 1` 动态显示/隐藏该分类即可，不必改固件。

---

## 6. 建议删除清单（分级，供确认）

| 级别 | 内容 | 数量 | 建议 |
|---|---|---|---|
| L1 确认重复 | Sweep、Gravimeter | 2 | 可直接软删除 |
| L2 同族冗余 | 各族中视觉最接近的 1 个（如 Chase Flash Rnd、Solid Glitter 等） | ~8 | 建议用 GIF 对比后再删 |
| ~~L3 2D 不适用~~ | ~~54 个 2D/粒子~~ | ~~54~~ | **已撤销**：2D 硬件成立，保留 |

保守方案：先执行 L1（2 个），L2 待 GIF 视觉确认后决定。**原 L3（54 个）因 2D 前提成立而作废。**

---

## 7. GIF 预览方案（已产出 15 个样张）

### 7.1 决策：GIF 与硬件解耦（2026-11-23）

GIF 是**效果示意**，不是某台设备的像素级仿真。一套图要能同时给 1D 灯带、2D 矩阵、圆管缠绕等不同形态的产品做展示，因此**不按具体灯珠数量/列数生成**。

| 项目 | 取值 |
|---|---|
| 画布 | **256 × 144（16:9）** |
| 虚拟栅格 | 32 × 18，单格 8px（内缩 2px 圆角点） |
| 1D 类 | 中间 1 行 + 高斯模糊柔光 |
| 2D 类 | 铺满整块栅格 |
| 帧数 | 24 帧 @ 12fps，2 秒无缝循环 |
| 调色板 | 128 色（由全部帧合图统计，避免稀疏效果被压成纯黑） |

### 7.2 已产出

`app/effect_previews/`，共 15 个，合计约 550KB（均值 37KB）：

| 类别 | 效果 |
|---|---|
| 1D（10 个） | solid · blink · breathe · wipe · rainbow · chase · comet · twinkle · fire · colorloop |
| 2D（5 个） | plasma · fireworks · matrix_rain · text_h · text_v |

生成脚本：`tools/gen_effect_gifs.py`
- `--list` 列出全部效果名；`--only <name..>` 只生成指定项
- 每个效果都会自动校验首帧亮度（App 网格用首帧当静态缩略图，全黑会导致缩略图看不见）
- 文字效果：`text_h` 横向滚动（宽文字，圆管上观感最佳）；`text_v` 纵向滚动（文字宽度 ≤ 矩阵宽时固件的默认行为）

### 7.3 剩余 172 个怎么补

| 路径 | 准确度 | 前提 | 说明 |
|---|---|---|---|
| **A. 真机抓帧** | 最高 | 设备在线 | 逐个切 `fx`，经 DDP/liveview 抓帧合成；与真实观感一致 |
| **B. 脚本模拟** | 示意 | 无需设备 | 在 `gen_effect_gifs.py` 里按效果语义补渲染函数；工作量大但可控 |

由于 GIF 已与硬件解耦，**路径 B 变得可接受**——不必精确复刻每个像素，只要传达"这是什么感觉"。建议：
1. 先按效果族补齐高频使用的（约 40–60 个）覆盖 80% 用户选择；
2. 剩余长尾用真机抓帧一次性批量生成；
3. 实在没有的，App 回退静态色卡（`EffectPreview` 已支持），不阻塞发版。

### 7.4 集成

- App 侧 `EffectPreview` 组件：固定 16:9 容器等比缩放，与设备灯珠数无关
- 187 个全内置约 **5–7MB**，可接受；偏大则降到 64 色或改 WebP 动图
- GIF 未就绪回退静态色卡

---

## 8. 待确认

1. ~~硬件是 1D 环形还是 2D 矩阵？~~ → **已确认：9×23 S 形 2D**，L3 作废。
2. **删除范围**：仅 L1（2 个）／L1+L2（约 10 个）。L3 已撤销。
3. **GIF 生成**：是否有在线设备可用于真机抓帧？无则走代码模拟（覆盖范围有限，2D 效果难以准确复现）。
