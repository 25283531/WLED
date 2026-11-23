# WLED App 界面现代化与硬件固化 — 设计评审

版本：v1.0（评审稿）
日期：2026-11-23
范围：Android 优先；iOS 二期移植
本轮交付：设计文档，不含代码改动

---

## 1. 背景与目标

现状问题（基于代码核查）：

1. **观感割裂**：设备详情页整体是 WebView，直接嵌入固件自带的 WLED 网页（`DeviceDetail.kt:65-69` → `DeviceWebView.kt:192-199` 加载 `http://<设备IP>/`）。主题本身已是 Material3 + 动态取色（`Theme.kt`），并不差；拉低观感的是网页。
2. **硬件参数暴露**：固件网页的 `settings_leds.htm`、`settings_pin.htm` 等页面允许用户改 LED 数量、引脚、排列。而本项目硬件已固定。
3. **语言不符预期**：当前支持中/英/法/德四语言（`AppLanguage.kt:12-23`、`locales_config.xml`），但默认固定为中文、需手动切换。目标为**保留四语言、改为跟随系统自动切换、默认中文**。

三项需求指向同一结构性解法：**App 完全去 WebView，用原生界面承载所有日常控制；固件网页仅保留为调试/极客入口。**

目标：

| 编号 | 目标 | 衡量标准 |
|---|---|---|
| G1 | 界面现代化 | 统一的视觉体系；无网页嵌入；操作路径 ≤ 3 层 |
| G2 | 规格自适应 | App 不硬编码灯珠数/矩阵；从设备 `/json/info` 动态读取并按实际尺寸渲染 |
| G3 | 四语言跟随系统 | 保留中/英/法/德；系统语言匹配则用之，否则默认中文；无手动选择器 |
| G4 | 保留极客入口 | 隐藏入口可打开固件网页做调试 |
| G5 | 收藏与快速切换 | 底部导航"收藏"页承载用户收藏的灯效 |
| G6 | 灯效可视化 | 每种灯效有 GIF 动图预览 |

非目标（本轮不做）：iOS 实现、AI 灯效生成（见 `AI_EFFECT_DESIGN.md`）。

---

## 2. 设计原则

1. **光即内容**：灯效以色环/色卡直观呈现，减少文字列表。
2. **一屏一目的**：控制、选效、调色各自独立，不堆叠。
3. **默认即正确**：硬件已固化，用户无需配置任何参数，开箱即用。
4. **深色优先**：灯具类产品在暗环境下使用，深色为默认观感；同时支持浅色与跟随系统。
5. **原生手感**：遵循 Material3 组件与动效规范，不使用网页。

---

## 3. 视觉设计体系（Design Tokens）

### 3.1 色彩

| Token | 深色值 | 浅色值 | 用途 |
|---|---|---|---|
| `bg` | `#14161B` | `#F7F9FF` | 页面底色 |
| `surface` | `#1D2028` | `#FFFFFF` | 卡片 |
| `surfaceHigh` | `#22262E` | `#F1F3F9` | 次级容器、输入框 |
| `outline` | `#2E323C` | `#DEE3EB` | 描边 |
| `primary` | `#54C8A0` | `#0F6E56` | 主操作、强调（青绿，象征光/能量） |
| `onPrimary` | `#0E1512` | `#FFFFFF` | 主色上的文字 |
| `textPrimary` | `#EDEFF4` | `#181C20` | 正文 |
| `textSecondary` | `#8E95A3` | `#5F5E5A` | 辅助说明 |
| `success` | `#7FD9B4` | `#3B6D11` | 状态提示（如"已锁定"） |
| `danger` | `#F09595` | `#A32D2D` | 删除/错误 |

说明：现有 `Color.kt` 的 Material3 色板保留为浅色回退；新增品牌主色 `#54C8A0`（可通过动态取色在 Android 12+ 覆盖）。

### 3.2 字体与字号

沿用 `Type.kt` 的 Material3 字阶，关键层级：

| 层级 | 字号/字重 | 用途 |
|---|---|---|
| Display | 28sp / 500 | 设备名（详情页顶部） |
| Title | 20sp / 500 | 页面标题 |
| Body | 15sp / 400 | 正文、列表项 |
| Label | 13sp / 400 | 辅助信息、单位 |
| Caption | 12sp / 400 | 时间戳、提示 |

### 3.3 形状与间距

- 卡片圆角：20dp；次级容器 14dp；按钮 12dp；chip 全圆。
- 间距基数 4dp；常用 8/12/16/24；页面左右留白 16dp。
- 卡片描边 0.5dp（深色 `#2E323C`），不使用投影。
- 触控目标 ≥ 48dp。

### 3.4 动效

- 灯效色环切换：300ms 颜色渐变（`animateColorAsState`）。
- 页面转场：Material3 默认共享轴。
- 亮度滑块：拖动即时反馈，松手提交（避免高频网络请求）。

---

## 4. 信息架构与导航

```
设备列表（首页）
 ├─ 点击设备 → 设备控制页（原 WebView 位置）
 │    └─ 底部导航（4 项）：控制 | 灯效 | 收藏 | 设置
 │         ├─ 控制：色环 + 开关 + 亮度 + 快捷场景 + 多灯同步 + 定时关灯
 │         ├─ 灯效：搜索 + 分类（含"字画"）+ 网格 + GIF 预览
 │         ├─ 收藏：用户收藏的灯效，一键切换
 │         └─ 设置：语言(只读) | 主题 | 关于 | 高级入口(隐藏)
 └─ 添加设备（保留现有流程）
```

导航实现：现有 `Navigation3`（`MainNavHost.kt`）新增目的地键：

- `DeviceControlKey(mac)` — 控制页（底部导航"控制"）
- `EffectsKey(mac)` — 灯效库（"灯效"）
- `FavoritesKey(mac)` — 收藏（"收藏"）
- `SettingsKey` — 已存在，重构内容（"设置"）

四个选项卡由 `NavigationBar` 承载，固定在设备控制页底部。

---

## 5. 逐页设计规格

### 5.1 设备列表（首页）

保留现有卡片结构（`DeviceListItem.kt`），做以下增强：

- 设备名 + 在线状态点（替代文字"离线"）
- 右侧开关（保留）
- 亮度滑块（保留）
- **新增**：当前灯效色块小圆点（取 `state.seg[0].col[0]`），让列表就能看出每盏灯的颜色
- 空状态：插画 + "添加你的第一盏灯"
- 移除任何"更新/仓库"类与硬件相关的展示（OTA 入口保留在详情设置里）

### 5.2 设备控制页（替代 WebView，核心）

自上而下：

1. **顶部**：设备名（可点开重命名，保留 `DeviceEdit`）
2. **灯效色环**：直径 180dp 的圆形，中心显示当前灯效代表色；点击进入灯效库
3. **灯效名称**：当前效果名（来自 `/json/effects` 映射）
4. **开关 + 亮度**：一行开关；下方亮度滑条（1–255，显示百分比）
5. **快捷场景**：3–4 个横向卡片（收藏/常用预设）
6. **多灯同步**：开关 + 分组选择（可选同步组 1–8）。WLED 通过 UDP 同步包实现，App 侧写 `sync` 配置需调用 `/json` 的同步字段或向固件配置写入；一期可先做"加入/退出同步组"的快捷开关
7. **定时关灯**：时长选择（15/30/60/120 分钟）+ 目标亮度 + 淡出开关，下发 `nl: {on, dur, fade, tbri, mode}`（`Nightlight.kt` 已建模）
8. **颜色调节**：入口按钮，展开色轮或预设色板

交互：所有操作经 `DeviceConnectionManager.sendState(State(...))`（WebSocket 下发，已支持完整 `seg` 与 `nl`）。

> 说明：多灯同步在 WLED 中属于设备端配置（`sync` 段），原生实现需写 `/json/cfg`；若一期成本高，可先用"批量下发同一 State 到多台设备"的 App 侧方案近似实现。

### 5.3 灯效库

- 顶部搜索框（本地过滤名称）
- 分类 chips：**全部 / 静态 / 动态 / 音频 / 字画**
  - **字画**：收纳 2D 图像与文字效果，详见 5.3.1。
  - 静态/动态/音频：按效果 ID 人工维护的分类表（`/json/effects` 只给名称，无分类）
- 网格：每项为**GIF 动图预览**（替代静态色卡）+ 名称，2 列；GIF 未就绪时回退色卡圆
  - GIF 是**通用效果示意**，不按具体灯珠数/列数生成，一套图通用于所有硬件形态（详见 5.3.2）
- 数据来源：**新增** `DeviceApi.getEffects()` 请求 `GET /json/effects`，返回名称数组（下标即 ID），**过滤 `RSVD` 占位项**
- 点击 → 下发 `{"seg":{"fx":id}}` → 返回控制页并高亮
- 底部**预留**"AI 生成灯效"入口位（虚线卡片，本期禁用）

#### 5.3.1 字画：自定义文字 / 自定义图片

**自定义文字** = 固件效果 `Scrolling Text`（`FX_MODE_2DSCROLLTEXT` = **122**）。它没有独立的"文字"参数，**文字取自段落名 `seg.n`**（`FX.cpp:6480`）：

| 项目 | 实现 | 说明 |
|---|---|---|
| 文字内容 | `seg.n = "..."` | ESP32 上限 **64 字符**（`const.h:205` `WLED_MAX_SEGNAME_LEN`）；超长会被截断 |
| 方向 | **自动** | 文字宽度 > 矩阵宽度 → 横向滚动；≤ 矩阵宽度 → **纵向滚动**（`FX.cpp:6578-6587`）。不是开关，由字数和矩阵宽决定 |
| 纵向方向 | `ix`（intensity） | 走纵向时：`ix=255` 与 `ix=0` 为相反方向 |
| 横向反向 | `o3`（check3） | 走横向时反向滚动（`FX.cpp:6592`） |
| 滚动速度 | `sx`（speed） | 映射到 250ms→50ms 每步 |
| 文字旋转 | `c3`（custom3, 0-31） | `map(c3,0,31,-2..2)`：`-1`=顺时针 90°，`1`=逆时针 90°，`0`=正常，`±2`=倒置 |
| 字体 | `c2`（0-4）+ `o2` 用自定义字体 | — |
| 拖尾 | `c1` 高 4 位 | `fade_out(255 - (c1>>4))` |
| 动态内容 | `#DATE #TIME #HHMM #DD #MON #DAY …` | 段落名里写 token 会替换为日期/时间（`FX.cpp:6503-6518`） |

App 交互建议：用户输入文字 → App 预览"会自动横向还是纵向"（按 `文字宽度 vs matrix.w` 估算）→ 下发 `{"seg":{"fx":122,"n":"文本","sx":..,"ix":..}}`。
> 注意 `Segment.kt` 原本**缺 `n` 字段**，本次已补（见 5.3.3）。

**自定义图片** = 固件效果 `Image`（`FX_MODE_IMAGE` = **53**）。图片必须已存在于设备文件系统：

| 项目 | 实现 | 说明 |
|---|---|---|
| 文件 | LittleFS 根目录下的 **`.gif`** 文件 | `image_loader.cpp:129-145`：文件名取 `seg.n`，且必须以 `.gif` 结尾，否则直接失败 |
| 选择图片 | `seg.n = "myimage.gif"` | 名字即文件名 |
| 开关 | `WLED_ENABLE_GIF` | **本工程已开启**（`platformio.ini:271`，随 `esp32_all_variants` 继承） |
| 限制 | 同一时刻只有 1 个段落可播放图片 | `image_loader.cpp:119` `IMAGE_ERROR_SEG_LIMIT` |

##### 重要更正：固件已自带完整的「上传 + 按灯阵生成预览」工具

此前误判为"App 没有上传能力"，实际核查后确认：**WLED 自带 `PixelForge`**（`wled00/data/pixelforge/pixelforge.htm`，45KB），原 App 里那个「文件上传 + 自动预览」就是它——App 只是通过 WebView 的 `onShowFileChooser` 把系统文件选择器接进去（`DeviceWebview.kt:435-439`、`WebViewViewModel.onStartFileUpload`）。所以那不是 App 的功能，是网页的功能。

`PixelForge` 的完整链路（`pixelforge.htm:1109-1201`），也就是 App 去 WebView 后**必须原生复刻**的部分：

| 步骤 | 实现 |
|---|---|
| 1. 读灯阵规格 | `GET /json/info` → `leds.matrix.w/.h`；同时 `GET /json/state` 取段落，`is2D = (seg.dataset.h \|\| 1) > 1` |
| 2. 选图 | `<input type=file>`，支持 `gif / png / jpg / jpeg / bmp`；GIF 用 `omggif.js` 逐帧解码并处理 disposal（0/2/3） |
| 3. 裁剪 | 画布上拖拽裁剪框 `cr`（支持旋转 `rot`、缩放 `iS`、背景色 `bg`） |
| 4. 重采样 | `drawImage(rv, cr.x, cr.y, cr.w, cr.h, 0, 0, w, h)` 且 **`imageSmoothingEnabled = false`**（最近邻，避免糊边） |
| 5. 黑阈 | `blackTh(cctx)` 把接近纯黑的像素压成 0，省空间 |
| 6. 调色板 | **全部帧合并**后 `genPal()` 生成统一调色板（避免逐帧换色闪烁） |
| 7. 编码 | `GifWriter`（omggif），`grid(w)` 决定画布：1D 且 `w>320`（WLED GIF 尺寸上限）时补成 2D；2D 最大 256×256 |
| 8. 上传 | `POST /upload`，`FormData` 字段名 `file`，文件名 `<fn>.gif` |
| 9. 列表预览 | `GET /edit?list=/` 取文件列表，筛 gif/png/jpg/jpeg/bmp，每项用 GIF 本身作 `background-image` 动图预览，`title` 显示 `名 / WxH / KB` |

**关键结论：所谓「按硬件灯珠排布生成的预览图」，就是第 4 步的输出画布本身**——它的宽高等于灯阵的 `w × h`，一个像素对应一颗灯珠。所以 App 的预览不可能是固定的示意图，必须按 `leds.matrix` 实时渲染。这也正好印证第 7 节"规格自适应"的结论。

App 侧落地要点：

- `DeviceApi` 需新增 `uploadFile(bytes, fileName)` → `POST /upload`（`multipart` 字段 `file`）。现有 `updateDevice()` 打的是 `/update`（**固件 OTA**），不是文件上传，别混用。
- 若设备设了 PIN，`/upload` 会返回 401（`wled_server.cpp:206-210` `correctPIN` 校验），需先走 PIN 解锁。
- GIF 编码在 KMP 里没有标准库：Android 侧可用 `android.graphics` 解码 + 纯 Kotlin GIF 编码器，iOS 侧用 `ImageIO`（`CGImageDestination`）。这是本项工作的主要成本，**不是 UI**。
- 若一期不做 GIF 编码，降级方案：只做「裁剪 + 重采样预览」（给用户看清在灯阵上是什么效果）+ 上传**单帧 GIF**（静态图），把多帧动画留到二期。静态图也能被 `Image` 效果正常显示。


#### 5.3.2 GIF 预览规格（通用化）

GIF 是**效果示意**，不是某台设备的像素级仿真，因此**与灯珠数量、列数解耦**：

| 项目 | 取值 | 理由 |
|---|---|---|
| 画布 | **256 × 144（16:9）** | 一套图通用于 1D 灯带、2D 矩阵、圆管缠绕等所有形态 |
| 虚拟栅格 | 32 × 18，单格 8px（内缩 2px 成圆角点） | 保留 LED 点阵质感，同时不绑定真实尺寸 |
| 1D 类效果 | 中间 1 行 + 高斯模糊柔光叠加 | 模拟灯带/扩散罩的观感 |
| 2D 类效果 | 铺满整块 32×18 | — |
| 帧数 / 帧率 | 24 帧 @ 12fps（2 秒无缝循环） | — |
| 体积 | 单张 1–190KB，均值约 **37KB**；187 个约 **5–7MB** | 可接受；超限再降到 64 色或改 WebP |
| 首帧 | **必须有内容**（App 网格用它当静态缩略图） | 生成脚本已对每个效果做首帧亮度校验 |

生成脚本：`tools/gen_effect_gifs.py`（输出到 `app/effect_previews/`），已内置 15 个效果：

```
solid blink breathe wipe rainbow chase comet twinkle fire colorloop   (1D)
plasma fireworks matrix_rain text_h text_v                            (2D)
```

用法：`python tools/gen_effect_gifs.py [--only text_h] [--list]`。

#### 5.3.3 本次已补的模型字段

| 文件 | 新增 | 用途 |
|---|---|---|
| `model/wledapi/Leds.kt` | `matrix: Matrix?`（`w`/`h`）、`is2D`/`width`/`height` 派生属性 | 动态读取设备矩阵，不硬编码 |
| `model/wledapi/Segment.kt` | `n`（名称/文字）、`c1`/`c2`/`c3`、`o1`/`o2`/`o3` | 字画、AI 参数配方、收藏完整复现所需 |


### 5.4 收藏页

- 列表/网格展示用户收藏的灯效（名称 + GIF 缩略图）
- 一键切换：点击即下发保存的参数（`sendState(State(seg=[...]))`）
- 支持收藏"当前灯效"：从设备状态 `state.seg[0]` 提取 fx/颜色/参数保存
- 长按/侧滑删除、拖拽排序
- 数据存储：Room 新增 `FavoriteEffect` 表（详见 `AI_EFFECT_DESIGN.md` 第 6 节）

### 5.5 颜色调节

两种模式（择一或并存）：

- 预设色板：12–16 个常用色，一点即应用
- 色轮：HSV 选择器（需自绘或用 Canvas），实时预览

下发字段：`seg.col[0] = [r,g,b,0]`。注意：仅对支持颜色的效果有意义，纯特效可能忽略颜色。

### 5.6 设置页（重构）

| 分组 | 项 | 行为 |
|---|---|---|
| 通用 | 语言 | **只读**，显示"跟随系统（简体中文 / English / Français / Deutsch）" |
| 通用 | 主题 | 浅色/深色/跟随系统（保留） |
| 设备 | 设备信息 | 只读：IP、固件版本（来自 `Info`） |
| 设备 | 设备规格 | 只读，**动态拼接设备上报值**：`<leds.count> 颗 · <matrix.w>×<matrix.h>` +（`matrix.h>1` 时标注"蛇形/矩阵"，否则"线形"）。例："207 颗 · 9×23 · 蛇形" |
| 关于 | 版本、开源许可 | 保留 |
| 高级（隐藏） | 打开固件网页 | 连点"关于/版本"7 次解锁，用 WebView 打开 `http://<ip>/settings` |

移除：所有可编辑的硬件参数；语言手动切换（改为只读跟随系统）。

---

## 6. 技术方案：去 WebView

### 6.1 需要新增的原生能力

| 能力 | 现状 | 改动 |
|---|---|---|
| 读灯效列表 | **缺**（App 从不请求 `/json/effects`） | `DeviceApi` 新增 `getEffects(): ApiResponse<List<String>>`；解析为 `List<String>`，过滤 `RSVD` |
| 下发灯效 | WebSocket `sendState` 已支持完整 `State` | 新增 ViewModel 方法 `applyEffect(fx)` |
| 下发颜色 | `Segment.col` 已建模 | 新增 `applyColor(r,g,b)` |
| 开关/亮度 | 已实现（`setPower`/`setBrightness`） | 复用 |
| 设备信息 | 已实现（`getInfo`） | 复用 |

### 6.2 数据流

```
用户点选灯效
  → DeviceWebsocketListViewModel.applyEffect(fx)
  → DeviceConnectionManager.sendState(mac, State(seg=[Segment(fx=fx)]))
  → WebSocket 文本帧
  → 固件 ws.cpp → deserializeState → setMode(fx) → 渲染
```

### 6.3 需要保留的网页能力与对策

| 网页功能 | 去 WebView 后 | 对策 |
|---|---|---|
| 固件 OTA 升级 | 已有原生 `DeviceUpdate` 流程 | 保留原生 |
| WiFi 初始配网 | 依赖设备 AP + 网页 | 保留：首次配网仍走系统 Wi-Fi + 设备热点引导（或保留一次性 WebView） |
| 灯效高级参数（c1/c2/c3） | 暂无原生 | 一期不暴露；二期加"高级"折叠面板 |
| 预设/播放列表管理 | 暂无原生 | 一期不暴露；极客入口可用 |
| 硬件设置（LED/引脚） | **刻意移除** | 仅极客入口可访问 |

### 6.4 极客入口设计

- 触发：设置页版本号连点 7 次 → 解锁"高级"分组
- 行为：`Intent` 或内置 WebView 打开 `http://<设备IP>/settings`
- 提示：首次进入弹确认"此页面含硬件参数，修改可能导致设备异常"
- 安全：不在主流程暴露，不写入普通用户可见文案

---

## 7. 硬件参数：不固定，设备自描述 + App 动态适配

> **重要修正（2026-11-23）**：产品当前为原型形态（9 列 × 23 颗 = 207 颗，S 形贴在 φ50mm 圆管外壁，起点在底部），**不是最终形态**。因此 App **不应硬编码灯珠数量/列数**，而应每台设备自描述、App 动态读取、按实际尺寸渲染。

### 7.1 可行性与依据

WLED 本身是设备自描述的，规格通过 API 暴露：

| 数据 | 来源 | 说明 |
|---|---|---|
| 灯珠总数 | `/json/info` → `leds.count`（固件 `json.cpp:724` `strip.getLengthTotal()`） | 运行期读取 |
| 矩阵宽 × 高 | `/json/info` → `leds.matrix.w` / `.h`（`json.cpp:736` `Segment::maxWidth/maxHeight`） | 2D 矩阵尺寸 |
| 蛇形走线 | 设备配置 `serpentine`（`set.cpp:933`、`cfg.cpp:210`） | **正好对应 S 形排布** |
| 多面板 | `MPC/P0W/P0H/P0S...`（`set.cpp:920-945`） | 支持分块 |

矩阵配置可在 **运行时**通过 LED Preferences 设置并存 `cfg.json`（`set.cpp:946` `deserializeMap()`），**无需重编译固件**。

### 7.2 当前原型的正确 2D 配置（易错点）

物理结构：9 列 × 每列 23 颗，**一列 23 颗连续走完再拐到下一列**（S 形），起点在底部。

对应 `strip.panel` 参数（依据 `FX_2Dfcn.cpp:104-117` 的坐标生成逻辑）：

| 参数 | 值 | 原因 |
|---|---|---|
| `MPC`（面板数） | `1` | 单一连续灯带 |
| `P0W` / `P0H` | `9` / `23` | 宽 = 列数，高 = 每列颗数 |
| `P0V`（vertical） | `1` | **关键**：置 1 时走线顺序为"先走完一列（高 23）再换列"，正好是物理走线；置 0 会变成"先走一行（宽 9）"，图像整个转 90° |
| `P0S`（serpentine） | 勾选 | 奇数列反向，形成 S 形 |
| `P0B`（bottomStart） | `1` | 数据链起点在**底部**（`FX_2Dfcn.cpp:110` 会翻转行序） |
| `P0R`（rightStart） | `0` 或 `1` | 起点在左下角填 `0`；若第一列在右侧填 `1` |
| 总灯珠数 | `207` | 9 × 23 |

> 校验方法：配好后 `GET /json/info` 应返回 `leds.count=207`、`leds.matrix={w:9,h:23}`。若 `matrix` 为 `{w:207,h:1}`，说明矩阵没生效。

### 7.3 三层做法

1. **固件层（一次性配置，非写死）**：
   - 当前 `PIXEL_COUNTS=60` 且未配矩阵，与原型 9×23=207 不符。
   - 两种改法，二选一：
     - **运行时（推荐，零重编译）**：网页 `http://<ip>/settings/2D` 设面板参数（对应 `set.cpp:916-949`，存 `cfg.json`）；总灯珠数在 `http://<ip>/settings/leds` 改 207。掉电保存，升级固件不丢。
     - **编译默认值（便于产线烧录）**：`platformio_override.ini` 把 `PIXEL_COUNTS=60` 改为 `207`，矩阵则需在 `cfg.cpp:195` 附近加一小段默认值注入（`hw.led.matrix`），否则新片子上电仍是 1D。
   - ⚠️ **顺带修正**：`ABL_MILLIAMPS_DEFAULT=2500` 对 207 颗偏小——满白理论约 207×55mA≈11.4A，2500mA 会把亮度压到约 20%。需按实际电源重设（如 5V/5A → 4000~4500）。
2. **App 层（零硬编码）**：
   - 启动/连接时读 `Info.leds.count` 与矩阵尺寸，UI、灯效预览 GIF 均按实际值渲染。
   - **需补模型**：`Leds.kt` 当前缺 `matrix` 字段，新增 `Matrix(w,h)`（约 1 个文件）。
3. **多型号/原型切换**：每台设备自带配置，App 通用；无需为每个型号出不同 App。

### 7.4 App 侧约束（保留）

1. 普通用户移除硬件编辑 UI（改参数走极客入口）。
2. 设置页"设备规格"改为**动态展示**设备上报的真实值（如"207 颗 · 9×23 · 蛇形"），而非写死文案。
3. 灯效分类中 2D/字画类**按设备是否 2D 动态显示**（`matrix.h>1` 才显示）。
4. 可选加固：固件侧对 `settings_leds` 写入加 PIN 保护（`settings_sec.htm` 已有机制）。

---

## 8. 多语言改造（保留四语言，跟随系统，默认中文）

原则：**不删除现有四语言资源**；取消手动选择器，改为启动时按系统语言自动匹配，匹配不到则回退中文。

### 8.1 Android

| 改动 | 文件 | 说明 |
|---|---|---|
| 语言枚举保留四项 | `util/AppLanguage.kt:12-23` | 保持 `Chinese/English/French/German` |
| 默认改中文 | 同上，`Default` 保持/明确为 `Chinese` | |
| 跟随系统 | `AppLanguageManager.applyOnStartup` | 读 `Locale.getDefault()` 匹配 zh/en/fr/de，未匹配→中文；**每次启动按系统重算，不写死** |
| 移除手动选择 | `Settings.kt:217-221` | 改为只读展示"跟随系统" |
| 资源 | `values/`(en)、`values-zh/`、`values-fr/`、`values-de/` | **全部保留** |
| 配置 | `locales_config.xml`、`build.gradle.kts:30` | **保持四语言不变** |
| 文案 | 四份 strings.xml | 新增文案需四语全加（`MissingTranslation` 为 fatal） |

实现要点：`wrap()`（Android 12 及以下 Context 包装）与 `LocaleManager`（13+）逻辑保留，但语言值来源从"用户选择"改为"系统语言映射"。需在 `applyOnStartup` 中去掉"首次固定写中文"的分支，改为每次启动解析系统语言。

### 8.2 iOS（二期）

- `AppLanguage.swift`：保留四 case；`default = .chinese`
- 启动时读 `Locale.preferredLanguages` 匹配 zh-Hans/en/fr/de，未匹配→中文
- `Localizable.xcstrings`：保留 en、zh-Hans、fr-CA、de
- 移除设置页语言选择器，改为只读"跟随系统"

---

## 9. 实施阶段与工作量

以 Android 为主，按依赖排序：

| 阶段 | 内容 | 相对工作量 | 可独立验收 |
|---|---|---|---|
| P1 | 语言改造（跟随系统、默认中文、移除选择器） | 小 | 是 |
| P2 | 设计令牌落地（色彩/形状/字体统一） | 小 | 是 |
| P3 | 设备控制页原生实现（色环/开关/亮度/同步/定时） | 大 | 是 |
| P4 | 灯效库（`getEffects` + 网格 + 搜索 + 分类） | 大 | 是 |
| P5 | 收藏页（Room 表 + 一键切换） | 中 | 是 |
| P6 | 灯效 GIF 预览（生成 + 集成） | 中 | 是 |
| P7 | 颜色调节（预设色板 → 色轮） | 中 | 是 |
| P8 | 设置页重构 + 极客入口 | 中 | 是 |
| P9 | 移除 WebView 主流程、清理死代码 | 小 | 是 |
| P10 | iOS 移植 | 大 | 二期 |

关键路径：P3/P4 是核心，决定"去 WebView"是否成立；P5/P6 是本次新增需求。

---

## 10. 风险与开放问题

1. **2D 前提（已解除）**：`Image`（自定义图片）、`Scrolling Text`（自定义文字）以及 54 个 2D/粒子效果需要二维矩阵。原判断"原理图仅单路 `LED_DATA`"只说明**电气上是单条数据链**，与**逻辑上是否二维**无关——9×23 S 形串联本身就是标准的蛇形 2D 布线，配 `serpentine` 即可。
   - 待办：固件 `PIXEL_COUNTS` 当前 60，需改为 207 并配 `panel 9×23 + serpentine`（见 7.2）。
   - 若后续某型号确实是 1D 环形，App 侧已做动态判定（`matrix.h>1` 才显示字画/2D 分类），不会出现空分类。
2. **灯效分类**：`/json/effects` 只返回名称，无分类。需在客户端按名称/ID 维护分类表（静态/动态/音频/字画）。
3. **颜色对特效无效**：部分动态效果忽略 `col`，需在 UI 标注或仅对适用效果开放。
4. **首次配网**：完全去 WebView 后，设备热点配网流程需单独设计（可能保留一次性 WebView）。
5. **多灯同步**：WLED 同步是设备端 `sync` 配置，原生写入需 `/json/cfg`；App 侧近似方案为批量下发同一 State。
6. **动态取色干扰品牌色**：Android 12+ 动态取色可能覆盖 `#54C8A0`，需决定是否禁用。
7. **OTA 与硬件设置的边界**：极客入口若暴露 OTA，需防误操作。
8. **GIF 体积**：187 个效果若全部内置 GIF，需评估 APK 体积（建议缩放尺寸 + 限制帧数，或按需下载）。

---

## 11. 验收清单

- [ ] App 主流程无任何 WebView 页面
- [ ] 控制/灯效/颜色/亮度/开关全部原生可用
- [ ] 控制页含多灯同步与定时关灯
- [ ] 底部导航含 控制/灯效/收藏/设置 四项
- [ ] 收藏页可收藏当前灯效并一键切换
- [ ] 灯效库含"字画"分类（设备为 2D 时显示，且 `Image` / `Scrolling Text` 可正常渲染）
- [ ] App 内无硬编码灯珠数量/矩阵尺寸；设置页"设备规格"显示设备上报的真实值
- [ ] 换一台灯珠数/列数不同的设备，App 无需更新即可正常显示与控制
- [ ] 每种灯效有 GIF 预览动图
- [ ] 设置页无硬件参数可编辑项
- [ ] 系统切中文→中文，切英/法/德→对应语言，其他→中文
- [ ] 设置页语言项为只读"跟随系统"
- [ ] 连点版本 7 次可进入固件网页
- [ ] 浅色/深色/跟随系统三态正常
- [ ] 四语言资源完整，构建通过（`MissingTranslation` 无报错）

---

## 附录：关键代码位置

- 入口与导航：`ui/MainNavHost.kt`、`ui/MainActivity.kt:47-49`（语言包装）
- 现有详情页：`ui/homeScreen/detail/DeviceDetail.kt:35-79`
- 列表卡片：`ui/homeScreen/list/DeviceListItem.kt:64-129`
- 设置页：`ui/settingsScreen/Settings.kt:57-315`
- 主题：`ui/theme/Theme.kt`、`Color.kt`、`Type.kt`
- 下发：`shared/.../service/DeviceConnectionManager.kt:616`（sendState）
- API：`shared/.../service/api/DeviceApi.kt:37-46`
- 语言：`util/AppLanguage.kt`、`iosApp/wled/Model/AppLanguage.swift`
- 本地化：`res/values{, -zh, -fr, -de}/strings.xml`、`res/xml/locales_config.xml`、`app/build.gradle.kts:30`
- 固件硬件常量：`firmware/platformio_override.ini:31-34`
- 固件网页设置：`firmware/wled00/data/settings_leds.htm` 等

---

## 12. 实现记录（v8.1.0-test1，Android 首版）

本节记录**实际落地**的代码，与上文设计不一致处以本节为准。

### 12.1 已完成

| 项 | 实现 |
|---|---|
| 底部导航 | `ui/HomeScaffold.kt`：设备 / 灯效 / 控制 / 收藏 四个页签，各页签用 `SaveableStateHolder` 独立保存状态 |
| 去 WebView | `ui/homeScreen/detail/DeviceDetail.kt` 改为原生控制页；固件网页保留为标题栏「网页设置」，用系统浏览器打开（极客入口） |
| 灯效库 | `ui/effects/`：`EffectCatalog`（187 条，由固件生成）+ 搜索 + 分类 chips + GIF 预览网格 + 收藏 |
| 控制页 | `ui/control/`：开关、亮度、常用灯效 chips、速度/强度、颜色、多灯同步、定时关灯 |
| 收藏页 | `ui/favorites/` + Room `FavoriteEffect`（DB v11，`effectId` 唯一索引，`AutoMigration(10,11)`） |
| 语言 | `util/AppLanguage.kt` 改为跟随系统；系统语言不在 zh/en/fr/de 内时回落中文。设置页提供「跟随系统语言」开关 |
| 规格自适应 | 无任何硬编码灯珠数；`ui/components/DeviceSelector.kt` 的 `deviceSpecSummary()` 从 `leds.count` / `leds.matrix` 现算 |
| 设备 API | `DeviceApi` 新增 `getState()`、`getEffects()`、`uploadFile()`（`/upload`，字段名 `file`，为字画上传铺路） |
| 下发通道 | `DeviceConnectionManager.sendSegmentPatch()`：把单个段落变换合并进完整 `seg` 列表后经 WebSocket 下发，避免覆盖其他段落 |

### 12.2 与设计的偏离

- **分类改为「全部 / 常用 / 1D / 2D / 字画」**。原设计的「静态 / 动态 / 音频」无法从固件数据可靠推导：`/json/effects` 只给名称，`effects_enabled.json` 里也没有音频标记。宁可少一个分类，也不做猜错的分类。
- **灯效名与 id 固化在 App 里**（`EffectCatalog`），不依赖运行时 `/json/effects`。好处是离线可列、分类稳定；代价是固件增删灯效后需重新跑 `tools/gen_effect_catalog.py`。
- **收藏以「灯效」为单位**（每个 `effectId` 只有一条），不区分设备。收藏时快照当前的速度/强度/调色板/三色，调出时可复现观感。
- **GIF 打包进 APK assets**（612KB / 15 张），走 `ImageDecoder` 播放（API 28+），低版本退回首帧静态图。

### 12.3 未做（下一批）

1. **字画的自定义图片/文字上传**：`DeviceApi.uploadFile()` 已就绪，但缺少「选图 → 按 `matrix.w×h` 重采样 → 编码 GIF → 上传」的前端链路。编码是难点（KMP 无标准 GIF 编码器，需纯 Kotlin 实现或平台侧 `CGImageDestination`/`Bitmap` 方案）。
2. **WebView 死代码清理**：`ui/components/DeviceWebview.kt`、`WebViewViewModel.kt`、`WebViewHolder.kt` 及 3 个对应测试已无调用方，但为控制首版风险暂留，待构建验证通过后整体删除。
3. **iOS**：按要求搁置（其 CI 在 `Build app (no code signing)` 一步失败，属既有问题）。
4. **剩余 172 个灯效的 GIF**：目前 15 张覆盖常用效果，其余显示占位块。

### 12.4 相关脚本

- `tools/gen_effect_catalog.py`：从 `firmware/effects_enabled.json` 生成 `EffectCatalog.kt`
- `tools/gen_effect_gifs.py`：生成通用规格（256×144）预览 GIF
- `tools/add_android_strings.py`：四语言字符串同步（幂等）

## 13. 实现记录（v8.1.0-test5，按 Gemini 设计稿重做四栏）

本节**取代第 5 节（逐页设计）与第 12 节的部分描述**。设计目标从「功能齐全」转向「高频页面零视觉负担」，
视觉语言固定为深空灰玻璃拟态（不做浅色主题，也不跟随系统动态取色）。

### 13.1 四栏信息架构

| 页签 | 定位 | 关键交互 |
|---|---|---|
| 设备 | 高频日常主控台 | 顶栏：品牌名 + 在线状态 + 设备名 +「N 台设备」；右侧「多灯同步」「定时」两枚**微缩胶囊图标**紧贴电源圆钮。主体：灯管示意图 → 亮度 → 四枚律动模式胶囊（幻彩极光 / 音乐律动 / 动态字幕 / 睡眠夜灯）→ **2×2 快捷卡片** |
| 灯效库 | 全量浏览与收藏 | 顶部同四分类 Tab + 搜索（可折叠）；双列瀑布流展示全部 187 个效果，卡片右上角星标点亮即推送到首页快捷卡片 |
| 创作 | 自定义 | 顶部分段胶囊切「纯色氛围创作」/「像素图文创作」：色轮双锚点取色 + 流速/饱和度，或 16×10 像素画板 + 滚动字幕；「保存收藏」直接归入对应分类 |
| 设置 | 生态 / 硬件 / 系统 | 三大模块；固件未开放的能力（Matter 桥接、麦克风灵敏度、顶部触控校准）**明确标注「固件未开放」并给 Toast**，不做按下去没反应的假开关 |

首页快捷卡片规则（`DeviceHomeViewModel.quickCards`）：先取该模式下的收藏，不足 4 张用该模式的常用效果补齐，
**永远只有 4 张**，并与顶盖模式切换触摸键的循环列表保持一致。

### 13.2 灯光预览：按效果 id 命名的 32×18 动图

第 12 节「15 张 GIF / 256×144 通用画布」的做法有**两个错**，本节修正：

1. **文件与灯效可能错配**：旧做法维护「灯效名 → 文件名」的手工映射，名字拼错或漏配就会张冠李戴。
   现在文件名就是**效果 id**（`assets/effect_previews/<idx>.gif`，`0.gif` ~ `186.gif`），
   与 `seg.fx` 严格一一对应，`EffectCatalog` 的 `preview` 字段直接写 id 字符串 —— 机制上不可能错配。
2. **1D 灯效被画成一条光带**：产品是 32×18 的灯珠矩阵（原型为 9 列 × 23 颗 S 形贴片圆管），
   固件里 1D 效果也是沿**蛇形**铺满整块的。现在 `gen_effect_gifs.py` 的 `from_strip()` 先算一条
   576 颗的灯带，再由 `serpentine()` 按 S 形铺到矩阵，画出来才是实际观感。

规格：`CELL=4, GAP=1` → 画布 128×72；16 帧 @12fps；量化 40 色；叠一层 `GaussianBlur(2.6)` 的柔光副本。
全量 187 个共 **6.99 MB**。播放端 `EffectPreviewGif` 用 `ImageDecoder` + `AnimatedImageDrawable`（API 28+），
`CENTER_CROP` 铺满卡片，低版本退回首帧。

### 13.3 「动态字幕」：固件加粗点阵大字（已落地）

`100mm 黑透亚克力管 + 0.2mm 乳白扩散膜 + 20mm 空气混光腔` 会把细笔画晕开，因此字幕模式必须是
**加粗等宽点阵大字体**（笔画 2 颗灯珠宽、字高占满纵向 12 颗灯珠）+ **纯黑底 + 高亮单色**拉满对比度。

实现分成两半，两半共用同一套字形与加粗规则：

| 侧 | 改动 |
|---|---|
| 固件 | `FontManager::drawCharacter()` 增加 `bold` 参数：每个点亮像素沿字身横向再补一颗，1px 笔画变 2px；`mode_2Dscrollingtext` 在 **font 4（内置 5×12）且未旋转**时启用，并同步修正字宽与总宽以保持字距 |
| App | 下发滚动字幕时带 `seg.c2 = 255`（映射到 font 4），颜色固定 `[白, 黑, 黑]`，旋转 `c3 = 0` |
| 预览 | `tools/gen_effect_gifs.py` 直接解析 `firmware/wled00/src/font/font_5x12.h` 的点阵数据，按同一加粗规则渲染 `123.gif` / `38.gif` —— 预览与上机是同一套字形 |

字高 12/18、字距 1+1（加粗补偿），在 32 列的预览画布上一次可见约 5 个字，与设备上一致。

### 13.4 关键代码位置

| 位置 | 内容 |
|---|---|
| `ui/isle/IsleTheme.kt` | 颜色令牌 + 全屏柔光背景 |
| `ui/isle/IsleGlyphs.kt` | 24×24 细线图标集（`material-icons-core` 只有实心剪影，故手绘） |
| `ui/isle/IsleComponents.kt` | `GlassCard` / `IsleCapsule` / `GlowCircleButton` / `StarButton` / `IsleSlider` / `EffectArtwork` / `DeviceTube` |
| `ui/isle/SelectedDeviceStore.kt` | 四页签共享「当前设备」（单例，Koin `single`） |
| `ui/device/` | 首页 + `DeviceHomeViewModel`（电源/亮度/模式/快捷卡片/同步/定时） |
| `ui/library/` | 灯效库 + 搜索 + 星标 |
| `ui/create/` | 色轮 + 像素画板 + 字幕 + `GifEncoder` 上传 |
| `ui/settings/` | 三组设置 + 语言切换 |
| `util/GifEncoder.kt` | 手写最小 GIF89a 编码器（单帧、256 色、LZW 9→12 位） |

### 13.5 与设计的偏离

- **灯管图用 Canvas 现画**（`DeviceTube`），不用渲染图：颜色要跟着当前灯效变，静态图做不到。
- **`EffectMood` 是 App 侧概念**：固件没有「律动模式」，四分类由 `tools/gen_effect_catalog.py` 的 id 白名单硬编码
  （`AURORA` 156 / `MUSIC` 14 / `TEXT` 4 / `SLEEP` 13）。
- **外层套了一层深色 `MaterialTheme`**：光屿本身是固定深色，若系统处于浅色主题，弹层/涟漪/默认文字色会漏白。
- **旧的 `homeScreen/` 列表详情链未删除**：`DeviceListDetail` / `DeviceDetail` / `ControlScreen` 已无入口，
  但删除牵动 `DeviceListDetailViewModel` 与 widget 相关引用，为控制风险暂留。

### 13.6 未做

1. **Matter / 米家 / HomeKit 桥接**、**I2S 麦克风灵敏度**、**顶部四键触控校准**：固件均无接口。
2. 旧 `homeScreen/` 列表链清理（见 13.5）。
3. iOS 仍搁置。
