# AI 灯效生成与收藏：架构方案

> 本文基于对本仓库固件与 App 端的**实际核查**写成，不是设想稿。
> 文中每条「边界事实」都标注了代码位置，可复核。

## 一、先说边界：固件到底能被"编程"到什么程度

这是整个方案的地基，搞错了后面全是白设计。

### 1.1 灯效 id 是编译期常量，没有运行时注册机制

```
firmware/wled00/FX.h:379   #define MODE_COUNT 222
firmware/wled00/FX.h:981   uint8_t addEffect(uint8_t id, mode_ptr mode_fn, const char *mode_name);
```

`WS2812FX` 构造函数里把 222 个 `mode_*` 函数填进 `_mode` 数组（`FX.h:896-898`），
`addEffect` **只在构造期调用**。全仓库搜不到任何运行时注册用户灯效的入口：

```bash
grep -rn "custom.*fx\|FX_MODE_CUSTOM\|userEffect\|MODE_CUSTOM" firmware/wled00/FX.h firmware/wled00/FX.cpp
# 无结果
```

**推论：任何"AI 生成一个新灯效算法"的想法，在不改固件的前提下无法落地。**
App 写进 `seg.fx` 的必须是 0..221 之间的一个已有 id。

### 1.2 可编程的表达面只有这些字段

段落下发走 WebSocket 的 `seg` 补丁，能改的字段是（`firmware/wled00/FX.cpp:104` 起）：

| 字段 | 含义 | 值域 | 谁在用 |
|---|---|---|---|
| `fx` | 灯效 id | 0..221（编译期固定） | 所有效果 |
| `sx` | 速度 | 0..255 | 几乎所有效果 |
| `ix` | 强度 | 0..255 | 几乎所有效果 |
| `pal` | 调色板 id | 内置 + usermod + 用户自定义 | 颜色类效果 |
| `col` | 三个颜色槽 `[r,g,b]` | 0..255/通道 | 大多数效果 |
| `c1` `c2` `c3` | 效果自定义参数 | 0..255 | **仅部分效果** |
| `o1` `o2` `o3` | 效果自定义开关 | bool | **仅部分效果** |
| `bri` | 段落亮度 | 0..255 | 全部 |

### 1.3 `c1/c2/c3` 的语义因效果而异 —— 这是最大的坑

同一个 `c1` 在不同灯效里是完全不同的东西：

```
FX.cpp:2060   const int inputRotation = SEGMENT.custom1;   // 122 动态字幕：输入旋转角
FX.cpp:2187   uint8_t boost = (17+SEGMENT.custom3) * ...;   // 120 恶灵骑士：推进强度
FX.cpp:2203   uint8_t blurAmount = SEGMENT.custom2 >> 2;   // 模糊量
FX.cpp:1233   SEGENV.step = strip.now + SEGMENT.custom1 * 25;  // 步进时间
FX.cpp:145    uint32_t sourceid = SEGMENT.custom3;          // 调色板源 id
```

**推论：AI 生成 `c1..c3` 之前，必须知道"这个 fx 怎么解释这三个数"。**
一张 per-fx 的参数 schema 是必需品，不是加分项。

### 1.4 调色板可以由用户自定义

```
firmware/wled00/colors.cpp:270-308   customPalettes 的加载
firmware/wled00/colors.h:84          extern std::vector<CRGBPalette16> customPalettes;
firmware/wled00/FX_fcn.cpp:276       targetPalette = customPalettes[WLED_CUSTOM_PALETTE_ID_BASE - pal];
firmware/wled00/json.cpp:802         root["cpalcount"] = customPalettes.size();
```

用户调色板与内置调色板合并进 `/json/palettes` 的分页列表。
**这是 AI 能实打实"创造"出新视觉的一层** —— 不需要改固件。

### 1.5 段落灯效不随重启持久化

`cfg.json` 里没有 `"seg"` 数组（只有 `"aseg"` 与 `if.sync.seg`），
段落的 `fx` 每次开机都是 `Segment` 构造函数的 `mode(DEFAULT_MODE)`（`FX.h:599`）。
**推论：配方必须存在 App 侧**，不能指望设备记住。

## 二、AI 能生成的三层东西

按落地难度从低到高：

### L1 参数配方 —— 不改固件即可落地 ✅

一个配方 = `(fx, sx, ix, pal, col[3], c1..c3, o1..o3)` 的一个取值。

这就是**收藏功能的数据模型**（`FavoriteEffect`）已经有的东西。
本轮已把收藏从"只存 effectId"补成"连参数一起存、一起恢复"，
等于 P0 已经落地（见第六节）。

AI 在这一层的角色：**从自然语言描述里挑一组参数**。
"快一点、蓝紫色、有闪烁" → `(fx=某个闪烁类, sx=200, ix=180, pal=紫渐变, col=[...])`。

### L2 调色板 —— 不改固件即可落地 ✅

AI 输出 16~256 个色标，固件有 `customPalettes` 槽位可存。
这一层 AI 的创造力是**真实**的：不依赖任何已有 fx 的参数语义。

### L3 新算法 —— 必须改固件 ⚠️

要让"AI 生成一个没人写过的灯效"成立，只有两条路：

1. **加一个新 fx**：在 `FX.cpp` 写 `mode_xxx` 并 `addEffect`。这是传统做法，
   一次加一个，AI 帮不上忙（它写不出能在 ESP32 上跑且不崩的实时渲染代码）。
2. **加一个参数化解释器 fx**：一个通用的"由数据驱动"效果，
   把行为描述（几个正弦、几个色带、什么混合模式）编码成一段数据下发。
   AI 生成这段**数据**，固件解释执行。

路线 2 是 AI 灯效真正有想象力的地方，也是唯一能让"AI 造效果"成立的技术路径。
代价是要在固件里写一个通用渲染器 —— 这是**独立的固件项目**，不在 App 侧。

## 三、技术难点

| # | 难点 | 说明 | 缓解 |
|---|---|---|---|
| 1 | **参数语义歧义** | `c1..c3` 因效果而异（§1.3） | per-fx 参数 schema，由固件侧维护、随 `EffectCatalog` 一起生成 |
| 2 | **组合爆炸** | 222 × 256 × 256 × 72 × 256³ ≈ 6.8×10¹⁵ 种组合 | 不能穷举，必须靠预览筛选；这也是"AI + 预览"的价值所在 |
| 3 | **预览与实机不一致** | App 展示的 GIF 与设备实拍有差 | 已有同源渲染机制（`tools/gen_effect_gifs.py` 直接解析固件源码），可扩展到配方 |
| 4 | **AI 输出不可信** | 幻觉出不存在的 fx、越界值、非法 JSON | 结构化输出 + schema 校验 + 值域裁剪 + 失败降级到最近似的内置灯效 |
| 5 | **配方需要持久化与分享** | 设备不存，App 要存，还要能导出 | Room 新表 `EffectRecipe`，带 schema 版本号 |
| 6 | **AI 调用成本与隐私** | 每次生成都要调 LLM | 本地优先：先给参数空间的智能搜索/随机探索，AI 只在用户明确请求时介入 |

## 四、架构分层

```
┌─────────────────────────────────────────────────────────┐
│ L4 呈现层    灯效库（筛选/搜索/GIF 预览） · 创作页        │
├─────────────────────────────────────────────────────────┤
│ L3 生成层    AI 配方生成 · AI 调色板生成 · 参数空间探索   │
│              └─ 约束解码 + schema 校验 + 降级            │
├─────────────────────────────────────────────────────────┤
│ L2 配方层    EffectRecipe（Room）· 导入导出 · 版本化      │
│              per-fx 参数 schema（EffectCatalog 旁挂）     │
├─────────────────────────────────────────────────────────┤
│ L1 应用层    EffectCatalog（222 条，id↔fx）· 筛选 · 下发   │
├─────────────────────────────────────────────────────────┤
│ L0 固件层    seg.fx/sx/ix/pal/col/c1-c3/o1-o3 · customPalettes │
│              （222 个编译期灯效，无运行时注册）            │
└─────────────────────────────────────────────────────────┘
```

**关键设计：把"配方"和"灯效目录"分开。**
`EffectCatalog` 是固件的静态事实（id ↔ fx ↔ 名称 ↔ 中文名 ↔ 预览）；
`EffectRecipe` 是 App 侧的动态产物（一个具体配方）。
两者用 `fx` 关联，但不互相污染 —— 删掉一个配方不影响灯效目录。

## 五、分阶段落地建议

| 阶段 | 内容 | 改动面 | 价值 | 风险 |
|---|---|---|---|---|
| **P0** | 收藏 = 手工配方：存并恢复 `fx+sx+ix+pal+col` | App 端 | 高（已落地） | 低 |
| **P1** | 配方可导出/导入；配方预览（扩展 GIF 渲染器支持参数） | App 端 | 中 | 低 |
| **P2** | AI 生成 L1 配方：自然语言 → 受限 JSON → 校验 → 入库 | App + 云服务 | 高 | 中（AI 输出质量） |
| **P3** | AI 生成 L2 调色板 → 上传 `customPalettes` | App + 固件端点 | 中高 | 中（槽位数有限） |
| **P4** | 固件加参数化解释器 fx，AI 生成行为描述 | **固件大改** | 最高 | 高 |

**推荐顺序：P0 → P1 → P2 → P3 → P4。**
P2 之前必须先有 P1 的导入导出（否则配方出问题时用户无法备份与回滚），
P3 必须在 P1 之后（调色板本身也是配方的一部分，需要同一套版本化机制）。

P4 是独立项目，建议单独立项，不要混在 App 迭代里。

## 六、当前进度

| 阶段 | 状态 | 说明 |
|---|---|---|
| P0 | ✅ 已落地 | `FavoriteEffect` 存 `fx+sx+ix+pal+col1..3`；新增 shared 层 `Segment.restoreFrom()`，首页快捷卡片与灯效库两条入口都连参数一起恢复；只覆盖收藏时确实存下的字段，不用默认值盖掉用户当前调色 |
| P1 | ⏳ 未做 | 配方导入导出；`gen_effect_gifs.py` 支持参数化预览 |
| P2 | ⏳ 未做 | 需要先有 per-fx 参数 schema |
| P3 | ⏳ 未做 | 固件已有 `customPalettes` 槽位，缺上传链路 |
| P4 | ⏳ 未做 | 固件大改 |

### P2 的前置：per-fx 参数 schema

这是整条链路上**唯一必须由固件侧提供信息**的一环。现状是
`EffectCatalog` 只知道 id / 名称 / 分类 / mood / 预览，**不知道每个 fx 怎么解释 `c1..c3`**。

建议的 schema 形态（示意）：

```jsonc
{
  "fx": 122,
  "params": {
    "sx":  { "label": "速度",     "range": [0, 255] },
    "ix":  { "label": "强度",     "range": [0, 255] },
    "c1":  { "label": "输入旋转", "range": [0, 255] },  // 语义因 fx 而异
    "c2":  { "label": "模糊量",   "range": [0, 255] },
    "c3":  { "label": null,       "range": [0, 255] }   // 该 fx 不用这个参数
  }
}
```

没有它，AI 只能瞎猜 `c1..c3` 的含义，生成的配方在设备上多半表现异常。
**这是 P2 开工前必须先补的一环。**

## 七、风险与开放问题

1. **AI 输出质量的兜底**：生成结果必须能降级到"最近似的内置灯效 + 参数微调"，
   而不是给用户一个跑飞的效果。
2. **per-fx schema 的维护成本**：222 个 fx 各有一套参数语义，
   靠人工维护不现实，需要从固件源码自动提取（类似 `gen_effect_catalog.py` 的做法）。
3. **固件与 App 的版本错配**：固件换了版本，fx 编号与参数语义可能变，
   配方需要带固件版本号，否则老配方在新固件上会错位。
4. **P4 的实时渲染开销**：ESP32-C3 上跑通用解释器，
   每帧解析行为描述会吃掉可观的 CPU，需要把描述预编译成紧凑字节码再执行。
5. **iOS 侧**：本方案全部功能在 Android 端先行；iOS 按当前约定仍搁置，
   但配方数据模型放在 shared 模块，将来 iOS 接入不需要重做。

## 八、附：本轮核查用到的命令

```bash
# 固件有没有运行时注册灯效的机制
grep -rn "custom.*fx\|FX_MODE_CUSTOM\|userEffect" firmware/wled00/FX.h firmware/wled00/FX.cpp

# c1/c2/c3 分别被哪些效果怎么用
grep -rn "SEGMENT.custom[123]" firmware/wled00/FX.cpp

# 用户自定义调色板
grep -rn "customPalettes" firmware/wled00/*.cpp firmware/wled00/*.h

# 段落灯效是否持久化
grep -rn '"seg"' firmware/wled00/cfg.cpp
```
