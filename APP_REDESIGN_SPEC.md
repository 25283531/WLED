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
## 14. 实现记录（v8.1.0-test7，新图标 + 「极光之舞」出厂开机灯效）

本轮做两件事：**把 APP 图标换成螺旋光带**，并**新增一个与它同源同色的灯效「极光之舞」，
设为固件出厂默认开机灯效**。

### 14.1 APP 图标

画面：深空蓝灰底 + 居中自下而上螺旋上升的**双股光带** + 底部三圈同心青色椭圆涟漪。
光带配色自下而上：亮青 `#00F5FF` → 蔚蓝 `#1E78FF` → 紫罗兰 `#AF28FF`。

| 产物 | 说明 |
|---|---|
| `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_rgb*.png` | 5 密度 × 4 张（方形 / 圆形 / 前景 / 单色） |
| `drawable/ic_launcher_background.xml` | 深空蓝灰底渐变 |
| `drawable/ic_launcher_foreground.xml` | 螺旋光带 + 涟漪矢量层 |
| `drawable/ic_launcher_monochrome.xml` | Android 13+ 主题图标单色层 |
| `mipmap-anydpi-v26/ic_launcher_rgb{,_round}.xml` | API 26+ 自适应图标入口 |

生成脚本：`tools/gen_app_icon.py`（位图）与 `tools/gen_icon_vectors.py`（矢量层）。

### 14.2 固件灯效 221「极光之舞」

| 位置 | 内容 |
|---|---|
| `firmware/wled00/FX.h` | `#define FX_MODE_LUMI_AURORA 221`，`MODE_COUNT` 221 → **222**（顺延为当前最大编号 + 1） |
| `firmware/wled00/FX.cpp` | `mode_lumi_aurora()` 实现 + `_data_FX_MODE_LUMI_AURORA` 名称表 + `addEffect()` 挂载 |
| `firmware/effects_enabled.json` | 灯效白名单同步（App 目录与预览 GIF 均由此派生） |
| `firmware/platformio_override.ini` | `-D DEFAULT_BOOT_FX=221` |

实现要点：

- 竖直圆柱矩阵专用（`SEG_W` × `SEG_H`），非 2D 时走 `FX_FALLBACK_STATIC`。
- 两条极光丝带以不同相位沿圆周交织，**螺旋角度随高度递增**（`twist` 由 Intensity 滑块控制）。
- 高度主色调：底部亮青 → 中部蔚蓝 → 顶部紫罗兰，与 APP 图标完全同源。
- 底部三圈青色涟漪以 `beatsin8_t` 呼吸。
- **开机升腾动画**：`SEGENV.call` 帧计数推进，132 帧（约 2.2 秒）从底部升到顶部，
  未升腾区域保持暗场并带 32 像素羽化边缘。用帧计数而非毫秒时间戳，避免 `uint16_t` 回绕。

### 14.3 「出厂开机默认灯效」的实现机制（本轮查清）

这是本轮最需要说清楚的一点 —— **固件里段落的 `fx` 并不随重启持久化**：

- 本 fork 的 `Segment` 类**没有** `getConfig()` / `setConfig()`
- `cfg.json` 的序列化/反序列化里**没有** `"seg"` 数组（`cfg.cpp` 里只有 `"aseg"` 和同步用的 `if.sync.seg`）

所以设备每次重启，段落都由 `Segment` 构造函数 `, mode(DEFAULT_MODE)`（`FX.h:599`）决定。
这反证了 `DEFAULT_BOOT_FX=221` 是**唯一正确且充分**的固件落点。

固件的**开机预设**是另一条独立机制：

| 环节 | 位置 | 说明 |
|---|---|---|
| 定义 | `wled.h:382` | `WLED_GLOBAL byte bootPreset _INIT(0);` |
| 持久化 | `cfg.cpp:626` 读 / `cfg.cpp:1194` 写 | cfg.json 的 `def.ps` |
| 开机执行 | `wled.cpp:693-694` | `if (bootPreset > 0) applyPreset(bootPreset, CALL_MODE_INIT);` |
| 对外只读 | `json.cpp:731` | `/json/info` → `leds.bootps` |

`bootPreset = 0` 即「不加载预设」→ 回落到固件出厂默认灯效，正是本产品要的语义。

**写入路径有两个坑，都不能直接给 App 用**：

1. `bootps` 只在 `savePreset()` 内部被消费（`presets.cpp:240`），且是
   `bootPreset = sObj["bootps"] | bootPreset` —— **按位或**，无法干净地写 0；
   必须与 `psave` 同批下发（固件原生 Web UI 就是 `obj.bootps = pI; obj.psave = pI;`）。
2. `POST /settings` 是**全表单**接口，缺 `CA` 会把开机亮度置 0、缺 `GV` 会关掉 gamma 校正 ——
   不能用于 App 端的部分写入。

### 14.4 本轮新增的固件只读字段

`/json/info` 的 `leds` 对象新增 `bootfx`（`json.cpp`）：

```cpp
leds[F("bootps")] = bootPreset;
leds[F("bootfx")] = DEFAULT_MODE;   // 编译期出厂默认灯效 id
```

App 据此显示**真实**开机状态，而不必硬编码 221（换固件版本也不用改 App）。
这是**只读**字段，不引入任何写入副作用。

### 14.5 App 端改动

| 位置 | 内容 |
|---|---|
| `model/wledapi/Leds.kt` | 新增 `bootps`（开机预设 id）与 `bootfx`（出厂默认灯效 id），均可空 |
| `ui/settings/SettingsScreen.kt` | 「开机默认状态」行由「只跳转网页」改为**展示设备实际状态** |
| `res/values{,-zh,-de,-fr}/strings.xml` | 新增 `isle_boot_state_factory` / `_effect` / `_preset` |

显示规则：

- `bootps > 0` → 「预设 #N」
- `bootps == 0` 且能查到 `bootfx` 对应灯效 → 灯效名（本产品为「极光之舞」）
- 设备老固件没有 `bootfx` 字段 → 「出厂默认」（降级文案，不假装知道具体灯效）

**App 侧刻意不做写入**：固件没有提供干净的单参数开机灯效写入口（见 14.3 的两个坑），
按 13.5 的既定原则「界面应该如实反映能力边界，而不是假装有」，
这一行点击仍引导到设备原生设置页 `http://<ip>/settings`，由固件自己的 UI 完成修改。

### 14.6 灯效 id 槽位对齐

App 的灯效目录与预览 GIF 都以**固件运行时 fx 值**为 id。
本轮新增 221 后发现此前的目录存在槽位错位，已按固件实际可用灯效重新生成：

- `tools/gen_effect_catalog.py` → `ui/effects/EffectCatalog.kt`（222 个条目，id 与固件一一对应）
- `tools/gen_effect_gifs.py` → `assets/effect_previews/<id>.gif`（189 张，按 id 命名，32×18）

### 14.7 关键代码位置

| 位置 | 内容 |
|---|---|
| `firmware/wled00/FX.h` | `FX_MODE_LUMI_AURORA 221`、`DEFAULT_BOOT_FX` 覆盖 `DEFAULT_MODE` |
| `firmware/wled00/FX.cpp` | `mode_lumi_aurora()` 与效果表挂载 |
| `firmware/platformio_override.ini` | `-D DEFAULT_BOOT_FX=221` |
| `firmware/wled00/json.cpp` | `/json/info` 的 `leds.bootfx` 只读字段 |
| `tools/gen_app_icon.py` / `tools/gen_icon_vectors.py` | 图标位图与矢量层生成 |
| `tools/gen_effect_catalog.py` / `tools/gen_effect_gifs.py` | 灯效目录与预览动图生成 |
| `tools/mock_wled_server.py` | 本地 mock 设备（已同步 `bootps` / `bootfx`） |

### 14.8 验收结果（CI + 模拟器）

**固件 CI**（`c05d398`，run [#45239866461](https://github.com/25283531/WLED/actions/runs/45239866461)）
全部步骤成功，`pio run -e color_lamp_c3` 编译通过：

```
JOB: Build color_lamp_c3 -> success
   - Verify color_lamp_c3 environment is defined => success
   - Run pio run -e color_lamp_c3                 => success
   - Collect binaries / upload-artifact            => success
```

产物 `firmware-color_lamp_c3.zip`（1,894,252 字节）内含
`WLED_17.0.0-devV5_ColorLamp-C3.bin`、`ColorLamp-C3-ota.bin`、`bootloader.bin`、`partitions.bin`。
在 bin 中检索到 `极光之舞`（UTF-8，偏移 17100）与 `bootfx` / `bootps` 字符串，
确认 221 号灯效与新增只读字段均已编入固件。

> 首次编译曾报 `FX.cpp:5262/5266: error: cannot convert 'CRGB' to 'uint32_t'`，
> 根因是 `color_blend()` 返回 `uint32_t` 而非 `CRGB`（`firmware/wled00/src/dependencies/fastled_slim/colors.h:64`），
> 已在 `c05d398` 修正为 `uint32_t ribbonColor = color_blend(...)` + `CRGB pixel(ribbonColor);`。

**Android CI**（run #45238765415）单测 + `assembleDebug` / `assembleRelease` 均成功，
artifact `android-apk`（44,665,308 字节）下载后安装到 `emulator-5554` 完成验收：

| 验收项 | 结果 |
|---|---|
| 启动器图标 | 深空蓝灰底 + 双股螺旋光带（底亮青 → 中蔚蓝 → 顶紫罗兰）+ 底部三圈青色涟漪 ✅ |
| 设置 → 系统与支持 → 「开机默认状态」 | 显示 **「极光之舞」** ✅（`leds.bootfx=221` → `Leds.defaultEffectId` → `EffectCatalog.nameZh`） |
| 灯效库「幻彩极光」末尾 | 「极光之舞」条目存在，缩略图为深底青蓝螺旋光带 ✅ |
| 预览 GIF | 连续三帧像素互不相同，动画在播 ✅ |
| 点击条目下发 | 设备 `seg.fx` 实测变为 **221** ✅ |

### 14.9 本轮新增脚本

| 脚本 | 用途 |
|---|---|
| `tools/ci_status.py` | 查询 Actions 运行状态（TLS 需重试、关闭证书校验）；令牌读 `GITHUB_TOKEN` 环境变量 |
| `tools/fetch_artifact.py` | 下载 Actions artifact；用 `curl -L` 跟随 302（urllib 会把 Authorization 带到 Azure Blob 被拒 401） |
| `tools/png_crop.py` | 纯 stdlib 的 PNG 裁剪 + 放大（本机无 PIL），支持 PNG 滤波 0-4 反算 |

### 14.10 未做

1. **App 端写入开机灯效**：固件缺干净入口（见 14.3），如需支持应先在固件加独立字段，
   而不是让 App 去拼 `psave` + `bootps`（会占用设备预设槽位）或调 `POST /settings`（有副作用）。
2. 旧 `homeScreen/` 列表链清理（见 13.5）。
3. iOS 仍搁置。

## 15. 实现记录（v8.1.0-test8，灯效库维度筛选 + 收藏参数闭环 + 重复度审计）

本节处理任务清单里剩下的四项（收藏选项卡、字画分类、多灯同步与定时关灯、灯效重复审核），
并顺带修掉一个此前没人注意到的**收藏功能半成品**问题。

### 15.1 灯效库：四 mood Tab → 扁平维度筛选（收藏 / 字画 / 常用）

第 13 节的灯效库顶部是四个律动模式 Tab（幻彩极光 / 音乐律动 / 动态字幕 / 睡眠夜灯）。
本轮把它换成一行可横向滚动的筛选胶囊：

```
全部 | 幻彩极光 | 音乐律动 | 动态字幕 | 睡眠夜灯 | 字画 | 收藏 | 常用
```

**为什么不做「模式 × 维度」的两维交叉**：两个维度会大量落空
（「幻彩极光」里没有「字画」，交叉后是空列表）。与其在界面上解释这条规则，
不如让用户一次只选一个意图 —— 所以做成**扁平的一维筛选**。

顺带复活了两处**定义了却从未被引用**的代码：`EffectCategory.ART`（字画）与
`filterEffects()` 纯函数。它们本来就在 `ui/effects/EffectFilters.kt` 里等着，
只是灯效库 UI 一直走的是 `EffectMood` 那条路。

| 筛选 | 判定 |
|---|---|
| 字画 | `EffectCategory.ART` —— 即 Image(53) + Scrolling Text(122)，固件里唯一直接消费用户内容的两个效果 |
| 收藏 | `EffectCatalog` 中 `id ∈ favoriteIds` |
| 常用 | `isPopular` 标记的 29 个 |
| 1D / 2D | `EffectCategory.ONE_D` / `TWO_D`（118 / 69 个，枚举保留但 UI 未露出） |

搜索此前只匹配英文名，现在**同时匹配中文名** —— 中文用户搜「极光」能命中
id 221「极光之舞」，此前搜不到。

### 15.2 收藏：从书签升级为可复现的配方

**发现的问题**：`FavoriteEffect` 表存了 `speed` / `intensity` / `palette` / 三个颜色，
但这四个字段**从未被任何代码读取用于下发**。收藏实际退化成了书签 ——
点收藏灯效只会切 `seg.fx`，收藏当时的观感（速度、调色）丢失。

修复：新增 shared 层的扩展函数

```kotlin
// app/shared/.../model/FavoriteEffect.kt
fun Segment.restoreFrom(favorite: FavoriteEffect?): Segment
```

它**只覆盖收藏时确实存下来的字段**：没存过的（比如当时设备就没上色）保持现状，
避免用默认值盖掉用户当前的调色。首页快捷卡片与灯效库页两条入口都改为连参数一起恢复。

为此把 `quickCards()` 的返回类型从 `List<EffectInfo>` 改成 `List<QuickCardItem>`
（携带对应的收藏记录）。注意数据类不能叫 `QuickCard` —— 同包已有私有的
`QuickCard` composable，同名会冲突。

**收藏从此有了独立入口**：灯效库的「收藏」筛选列出全部收藏，不再受
首页 2×2 快捷卡片（上限 4 张）截断。此前收藏超过 4 个就等于消失。

### 15.3 设备页：灯效参数调节（速度 / 强度 / 颜色）

第 5 节设计的是「控制选项卡」，第 13 节改成了四栏架构，
多灯同步与定时关灯最终落在**设备页顶部的两枚微缩胶囊 → 底部弹层**。

核查时发现真正的缺口不在同步/定时（那两块功能完整），而在**参数调节**：
旧链的 `ui/control/ControlScreen.kt` 独有「速度 / 强度 / 颜色」三项能力，
而四栏架构下的设备页**完全没有**。结果就是：收藏存下来的参数，
用户在界面上既看不到也改不了。

本轮把参数调节补进设备页，形态是**一行可展开的「灯效参数」**：

- 折叠时只占一行，显示「速度 60% · 强度 80%」摘要，不抢主控台的视觉重心
- 展开后露出速度滑块、强度滑块与一排预设色
- 预设色前三位刻意取产品招牌色（亮青 / 蔚蓝 / 紫罗兰），与灯效 221 和 App 图标同源

滑块提交时读自己的 state 而不是参数快照 —— 拖拽结束到重组之间快照可能是旧值，
直接用快照会提交偏小的数值（`BrightnessRow` 原本就是对的，这里照抄了它的写法）。

### 15.4 灯效重复度审计：真冗余 0 个，不动固件

新增 `tools/audit_effect_duplicates.py`。关键在于**不能只比 token 相似度**：

WLED 里大量灯效是「薄封装」，函数体只有一行，转调共享的核心实现并改一两个参数：

```cpp
void mode_blink(void)  { blink(SEGCOLOR(0), SEGCOLOR(1), false, true); }
void mode_strobe(void) { blink(SEGCOLOR(0), SEGCOLOR(1), true,  true); }
```

直接比 token 会把这两者判成 Jaccard = 1.000。所以脚本先把薄封装**下沉到核心实现**，
再比对实参：

| 判定 | 条件 | 处置 | 本轮结果 |
|---|---|---|---|
| `redundant` | 核心实现相同 **且 实参完全相同** | 可删 | **0 个** |
| `variant` | 核心实现相同，实参不同 | 保留（视觉可区分） | 4 对 |
| `similar` | 核心不同，token Jaccard ≥ 0.70 且行数比 ≥ 0.70 | 保留，人工抽查 | 4 对 |

**结论：189 个灯效里没有一个是「删掉无功能损失」的。** 同族关系全部来自两条：

1. **三条渲染路径**：同一视觉概念有 1D 灯带 / 2D 矩阵 / PS 粒子系统三个实现
   （焰火、追逐、舞影、团块、瀑布共 5 组），视觉差异明显
2. **参数化变体**：同一核心实现换参数（明灭/频闪、擦拭/扫光、烛光/多簇烛光、重力表/重力中心）

同时查明固件源头**已有人工去重**：`FX_MODE_METEOR_SMOOTH` 被注释合并进
`mode_meteor`，`FX_MODE_NOISE16_1` 有 `#endif // dedup:` 标记；
另有 30 个灯效位于 `#ifdef WLED_KEEP_ALL_FX` 块内，而该宏在本产品**编译时未定义**，
故不参与编译 —— 结论是 App 清单的 189 个与固件实际可用集合**完全一致，无遗漏**。

**处置：不动固件。** 从固件删灯效是破坏性的 —— 用户已保存的预设与 `seg.fx`
会指向被删 id，删除后这些引用回落到 id 0（Solid），表现为「设好的效果突然变纯色」。
189 个灯效在固件里各占一个数组项，成本可忽略。需要精简时应在
App 侧做展示层收敛，报告第四节的分组直接可用。

报告见 `docs/EFFECT_DUPLICATE_AUDIT.md`，机读结果见 `docs/effects_duplicate_candidates.json`。

### 15.5 AI 灯效生成与收藏：架构方案

见 `docs/AI_EFFECT_ARCHITECTURE.md`。要点：

- **固件没有运行时注册灯效的机制**（`addEffect` 只在构造期调用，`MODE_COUNT = 222`），
  所以「AI 生成新算法」必须改固件
- App 能编程的表达面只有 `fx / sx / ix / pal / col[3] / c1-c3 / o1-o3`
- **`c1..c3` 的语义因效果而异**（122 用 c1 做输入旋转、120 用 c3 做推进强度、
  部分效果用 c2 做模糊量），这是 AI 生成配方最大的坑，
  需要一张 per-fx 参数 schema —— 且该 schema 只能从固件源码自动提取
- AI 真正能「创造」的是 **L2 调色板**（固件有 `customPalettes` 槽位）与 **L1 参数配方**
- 分 5 阶段：P0 收藏即手工配方（已落地）→ P1 导入导出与预览 → P2 AI 生成配方 →
  P3 AI 生成调色板 → P4 固件加参数化解释器（独立立项）

### 15.6 关键代码位置

| 位置 | 内容 |
|---|---|
| `ui/effects/EffectFilters.kt` | `EffectFilter` 扩为扁平一维筛选；`filterEffects` 加 `favoriteIds` 参数并支持中文名搜索 |
| `ui/library/LibraryViewModel.kt` | `_filter` 状态；`visibleEffects` 改走 `filterEffects` |
| `ui/library/LibraryScreen.kt` | 四 mood Tab 换成筛选胶囊行；收藏为空时给专门文案 |
| `shared/.../model/FavoriteEffect.kt` | `Segment.restoreFrom()` 与 `unpackedColors` |
| `ui/device/DeviceHomeViewModel.kt` | `applyEffect` 带收藏参数；`setSpeed/setIntensity/setPrimaryColor`；`QuickCardItem` |
| `ui/device/DeviceScreen.kt` | 「灯效参数」可展开区（速度/强度/颜色） |
| `tools/audit_effect_duplicates.py` | 灯效重复度审计（薄封装下沉 + 实参比对） |
| `app/src/test/.../EffectFiltersTest.kt` | 筛选逻辑单测（用真实 `EffectCatalog`） |

### 15.7 与第 5 节的偏离

第 5 节设计的四栏是「控制 / 灯效 / 收藏 / 设置」，本轮**没有**照做，
原因与取舍：

| 第 5 节 | 实际 | 原因 |
|---|---|---|
| 独立「控制」选项卡 | 未加，能力并入设备页 | 第 13 节的 Gemini 设计稿把底部导航定为四栏（设备/灯效库/创作/设置），加第五栏会破坏已定稿的信息架构；参数调节做成设备页内可展开的一行，比独立页更贴近「一屏一目的」 |
| 独立「收藏」选项卡 | 未加，改为灯效库内的「收藏」筛选 | 同上。但收藏此前只能经首页 4 张快捷卡片消费，超出即消失；筛选式入口能列出全部收藏，且不增加底部栏负担 |
| 灯效分类「字画」 | 灯效库筛选行新增「字画」 | 落地为筛选维度而非独立 Tab，与「全部/收藏/常用」并列 |

### 15.8 未做

1. **旧 `homeScreen/` 列表链清理**（`DeviceListDetail` / `DeviceDetail` / `ControlScreen`）。
   `ui/control/ControlScreen.kt` 与 `ControlViewModel.kt` 现已是死代码 ——
   其独有能力（速度/强度/颜色）已迁入设备页。本轮未删，删除牵动
   `DeviceListDetailViewModel` 与 widget 引用，风险需单独评估。
2. **配方导入导出与配方预览**（P1）。
3. **per-fx 参数 schema**（P2 的前置）。
4. iOS 仍搁置。

## 16. 实现记录（v8.1.0-test8 续，滑块 tap 精度问题定位与修复）

第 15 节补的「灯效参数」区上线后，滑块被反馈「拖不动 / 不跟手」。上一阶段收尾时
给的结论是「`IsleSlider` 是既有组件，属组件通病」——**这个结论是错的**，
本节记录完整的定位过程与修复。

### 16.1 现象

设备页速度滑块：点任意位置，界面百分比几乎不变，但设备端 `seg.sx` 会在几个
极小值之间跳（实测只在 `0` 和 `5` 之间摆动）。

### 16.2 根因 A：换算做了两遍（双重百分比）

`DeviceScreen.kt` 里「比例 → 通道值」被换算了两次：

```kotlin
// EffectTuning：比例 → 小整数（这里就乘了 255/100）
onCommit = { onSpeed((localSpeed * MAX_CHANNEL_VALUE / PERCENT_MAX).roundToInt()) }

// DeviceScreen 回调：再乘一次 255/100
onSpeed = { value -> device?.let { viewModel.setSpeed(it, value * MAX_CHANNEL_VALUE / PERCENT_MAX) } }
```

两级相乘使最终输出被压缩到极小集合。把它写成纯函数模型后，与 4 个真机实测
观测点**逐点吻合**：

| 滑块位置 | 比例 | 旧链路输出 `sx` | 实测 |
|---|---|---|---|
| 0.0% | 0.000 | 0 | 0 |
| 25.0% | 0.250 | 2 | — |
| 50.0% | 0.500 | 2 | — |
| 67.3% | 0.673 | 5 | 5 |
| 76.9% | 0.769 | 5 | 5 |
| 100.0% | 1.000 | **7** | — |

可输出集合只有 `{0, 2, 5, 7}` —— **最大 7/255，实际可达量程 2.7%**。
界面恒显「速度 2%」也由此得到解释：`sx=5` → `5/255 = 1.96%` → 2%。

### 16.3 根因 B：`pointerInput(Unit)` 的陈旧闭包

`IsleSlider` 的手势协程写成 `pointerInput(Unit)`。Compose 的 `pointerInput`
以 key 为重启依据，key 恒为 `Unit` 时协程**永不重启**，块内捕获的 lambda
永久停留在**首次组合**的那一份。

上层 `EffectTuning` 用 `remember(speed)` 持有本地状态：设备回写使 `speed`
变化 → `remember` 重建 `MutableFloatState` → 而手势回调仍在写**已被废弃的旧
state**。表现为「圆头纹丝不动、后台数值却在跳」——这正是此前最矛盾的现象。

`CreateScreen` 的 `ColorWheel` 是同族问题（用旧的 `primary`/`secondary`
判主副色），一并修掉。

### 16.4 几何正确性的独立验证（排除误判）

用像素反推证明 `IsleSlider` 的比例计算本身没问题：滑块 Box 边界
`x ∈ [199, 1239]`（宽 1040），圆头中心 = `199 + clamp(ch/255 × 1040, 32, 1008)`。
亮度滑块多一层 `padding(horizontal = 12.dp)` 与 40dp 百分比文字，Box 为
`[199, 1151]`（宽 952）。

- 亮度 50% → 理论圆头 x=676，实测 677
- 速度 `sx=5` → 理论圆头 x=232（受 `thumbRadius` 钳位），实测 231

→ `offset.x / size.width` 无误，**Bug B 是组件缺陷，但 Bug A 才是主因**，
两者叠加才产生「拖不动」的现象。

### 16.5 修复

1. **换算收敛为单一入口**：新增 `app/shared/.../util/ChannelScale.kt`，
   提供 `normalizedToChannel` / `channelToNormalized` / `channelToPercent` /
   `percentToChannel` / `normalizedToPercent`，把亮度（`bri`）、速度（`seg.sx`）、
   强度（`seg.ix`）统一到同一个 0..255 通道刻度。
2. **删除页面内的 `PERCENT_MAX` / `MAX_CHANNEL_VALUE` 私有常量** ——
   重复换算的写法（`* 255 / 100`）从此在设备页无处可写。
3. **三个滑块统一「内部保留比例、提交时落通道值」**，调用方不再二次换算。
4. **`IsleSlider` / `ColorWheel` 用 `rememberUpdatedState`** 消除陈旧闭包。
5. `CreateViewModel` 的 `× 255` 收敛到 `normalizedToChannel`。

### 16.6 验收结果

`tools/verify_slider_tap.py` 在模拟器上按等分位置 tap 滑块，并从 mock WLED
读回通道值，同时用像素扫描比对圆头位置：

| 滑块 | 字段 | 全量程 | 单调 | 圆头跟随 |
|---|---|---|---|---|
| 速度 | `seg.sx` | 0 → 255 ✅ | ✅ | Δ = 0.0 px |
| 强度 | `seg.ix` | 0 → 255 ✅ | ✅ | Δ = 0.0 px |
| 亮度 | `bri` | 0 → 254 ✅ | ✅ | Δ ≤ 0.5 px |

拖动路径 4/4 命中（200→1000、1238→600、400→900、200→1238）。

CI：Android run 45302471919 **success**（含 `:shared:testAndroidHostTest`
新单测 `:app:testDebugUnitTest`）。

### 16.7 关键代码位置

| 位置 | 内容 |
|---|---|
| `shared/.../util/ChannelScale.kt` | 比例/百分比/通道值三向换算的单一入口 |
| `shared/.../commonTest/.../ChannelScaleTest.kt` | 换算单测 + 「旧实现只能输出 {0,2,5,7}」的失败形态锁定 |
| `ui/device/DeviceScreen.kt` | 三个滑块统一单级换算；删除页面私有刻度常量 |
| `ui/isle/IsleComponents.kt` | `IsleSlider` 用 `rememberUpdatedState` 消除陈旧闭包 |
| `ui/create/CreateScreen.kt` | `ColorWheel` 同族修复；百分比显示走统一入口 |
| `ui/create/CreateViewModel.kt` | 换算收敛到 `normalizedToChannel` |
| `tools/verify_slider_tap.py` | 滑块 tap/拖动端到端校验（tap→通道值→圆头像素三重比对） |

### 16.8 未做

1. 旧 `homeScreen/` 列表链清理（见 15.8）。
2. 配方导入导出与配方预览（P1）。
3. per-fx 参数 schema（P2 的前置）。
4. iOS 仍搁置。

## 17. 实现记录（v8.1.0-test8 续，App 图标位图化 + 固件「极光之舞」复核）

本轮的用户诉求是「把这张图作为 App 图标」，并配套一套 2D 圆柱矩阵专属灯效
「极光之舞（LumiIsle Aurora）」，每次通电从底座泛起青色声波涟漪，
随后两道青蓝→紫罗兰渐变的半透明丝绸极光交织盘旋升起。

### 17.1 固件侧复核：已 100% 就绪，编号是 221 而非 188

需求里给的实施步骤（`FX_MODE_LUMI_AURORA 188` + 粘贴实现代码）在**上一轮
v8.1.0-test7 已经全部落地**，且编号必须用 **221**：

| 位置 | 现状 |
|---|---|
| `wled00/FX.h:377` | `#define FX_MODE_LUMI_AURORA 221`（188 已被 `FX_MODE_PARTICLEFIRE` 占用） |
| `wled00/FX.h:379` | `#define MODE_COUNT 222` |
| `wled00/FX.cpp:5221` | `void mode_lumi_aurora(void)` 完整实现 |
| `wled00/FX.cpp:5321` | `"极光之舞@速度,交织幅度;;2"` 名称表 |
| `wled00/FX.cpp:11431` | `addEffect(FX_MODE_LUMI_AURORA, &mode_lumi_aurora, ...)` 模式表 |
| `firmware/platformio_override.ini:46` | `-D DEFAULT_BOOT_FX=221` → `FX.h` 的 `DEFAULT_MODE` |

**原样粘贴需求里的代码会编译不过**，仓库里的实现是适配过的版本，
差异已在 `FX.cpp` 的注释里逐条记录：

| 需求里的写法 | 本仓库的写法 | 原因 |
|---|---|---|
| `uint16_t mode_lumi_aurora(void)` | `void mode_lumi_aurora(void)` | `mode_ptr` 是 `void (*)(void)` |
| `sin8()` / `cos8()` / `beatsin8()` | `sin8_t()` / `cos8_t()` / `beatsin8_t()` | 本仓库数学函数带 `_t` 后缀 |
| `blend(CRGB, CRGB, uint8_t)` | `color_blend(uint32_t, uint32_t, uint8_t)` | 没有 `blend()`；`color_blend()` 返回 `uint32_t` 而非 `CRGB` |
| `SEGMENT.virtualWidth()` / `virtualHeight()` | `SEG_W` / `SEG_H` | 后者是 `Segment::vWidth()` / `vHeight()`，虚拟尺寸已正确处理 S 形绕线 |
| `SEGENV.aux0` 存毫秒计时 | `SEGENV.call` 帧计数 | `aux0` 是 `uint16_t`，每 65 秒回绕，做不了 2.2 秒的开机计时（改用 132 帧 ≈ 2.2s） |

另加了一处本仓库惯例的兜底：`if (!strip.isMatrix || !SEGMENT.is2D()) FX_FALLBACK_STATIC;`
—— 该效果是 2D 圆柱矩阵专属，1D 灯带上必须回落而不是画乱。

视觉特征与需求描述逐条对应：底部 0~1 行的青色声波涟漪（`beatsin8_t(18, 90, 210)` 呼吸）、
两条反向交织螺旋丝带（交叉处用 `qadd8` 饱和加法还原高亮叠加）、
自下而上 亮青 → 蔚蓝 → 紫罗兰 三段渐变、开���前 2.2 秒「由底向上点亮升腾」迎宾动画。

### 17.2 App 图标：从「程序化绘制」改为「原图位图」

此前 `tools/gen_app_icon.py` 是用 numpy + PIL **代码画**出来的矢量近似，
与用户手上的原图有形差。本轮把用户提供的 1920×1920 原图入库为
`docs/assets/lumi_aurora_icon_source.jpg`，脚本改为**位图驱动**。

关键前提是**主体必须落在自适应图标的中央 72/108 安全区内**，否则圆形 mask
会削掉丝带尖端或涟漪。实测原图主体位置：

| 指标 | 实测值 | 判据 |
|---|---|---|
| 主体 x 范围 | 24.4% – 75.6% | 安全区 18.5% – 81.5% ✅ |
| 主体 y 范围 | 15.6% – 85.9% | 安全区 18.5% – 81.5%（顶部尖端极窄、底部涟漪边缘，几乎无面积损失）✅ |
| 主体占画布 | 11.7% | 与原构图一致 |

结论：**整幅映射到 108dp 画布即可 100% 保真，无需缩放**。
已离线合成验证圆形 / 圆角方形 / 全出血三种 mask，丝带与涟漪均无裁切、
背景与前景之间无接缝或色差。

### 17.3 启动闪屏图标：去背景的彩色剪影

`windowSplashScreenAnimatedIcon` 引用 `mipmap/ic_launcher_rgb_foreground`。
若沿用带深空底的整图，闪屏会把图标居中画在圆形底上 → 露出一个深蓝方块。
本轮改用**去背景的彩色剪影**，按物理反解式还原主体色：

```
观测色 = 主体色 × α + 背景色 × (1 − α)   ⇒   主体色 = (观测色 − 背景色 × (1 − α)) / α
```

（直接用「观测色 − 背景色 × (1 − α)」会在半透明边缘留下一圈发暗的描边。）
同时把该资源从 legacy 尺寸（48dp）改为 **108dp 层尺寸**，避免被 SplashScreen
放大 4 倍后糊掉。

### 17.4 monochrome 主题图标

Android 13+ 的 themed icon 需要剪影。剪影同样取自原图：用大半径模糊
（σ=120px）估计背景场，取「与背景场的差」作 α —— 比固定亮度阈值更贴合
这张图的实际结构（丝带本体与涟漪亮度远高于背景，辉光平滑衰减到背景色）。
因此剪影与彩色版**严格同源**，不会出现两版图形对不齐。

### 17.5 顺带修掉一个既有 bug：圆标的圆形裁切从未生效

上一轮脚本里圆形版最后一步是 `img.convert("RGB")`，而 PIL 的 RGBA→RGB
转换会**丢弃 alpha 通道而保留 RGB 值**，于是圆形外的四角仍是原图色 ——
方标与圆标字节数完全相同（5 个密度皆然）。改为保留 alpha 的透明角。

### 17.6 开机默认：编译期默认 vs 设备端预设槽位

需求给的两步设备端操作**有效**，机制已核实：

| 步骤 | 链路 | 结果 |
|---|---|---|
| 勾选 Save to ID 填 1 | `index.js:2575` 发 `bootps=1` → `presets.cpp:238` `bootPreset = 1 \| bootPreset` | `bootPreset = 1`，槽位 1 存下当前状态 |
| Apply preset at boot 改 1 | `cfg.cpp:626` 读 `def["ps"]` / `cfg.cpp:1194` 写回 | `bootPreset = 1`（**直接赋值**，可覆盖按位或的结果） |
| 通电生效 | `wled.cpp:693` `applyPreset(bootPreset, CALL_MODE_INIT)` | 加载槽位 1（灯效 221 + Speed 115 + Intensity 140） |

但仓库里**已经有一层更稳的出厂默认**：`platformio_override.ini` 的
`-D DEFAULT_BOOT_FX=221` → `FX.h` 的 `DEFAULT_MODE`。
`wled.cpp:693` 的分支是 `bootPreset > 0 ? applyPreset(...) : 段落用 DEFAULT_MODE`，
所以两条路径的关系是：

| | 编译期默认（已生效） | 设备端预设槽位 1 |
|---|---|---|
| 触发条件 | `bootPreset == 0` | `bootPreset == 1` |
| 灯效 | 221「极光之舞」 | 槽位 1 里存的（用户存的就是 221） |
| speed / intensity | 固件默认值 | **115 / 140**（可按需求精调） |
| 稳定性 | 工厂即如此，不可被覆盖 | 槽位 1 会被后续「保存预设」覆盖 |
| 需要真机操作 | 否 | 是 |

App 端**只读**展示这两个字段（`json.cpp:731` 上报 `leds.bootps`、
`leds.bootfx`），不提供写入 —— 见 14.3 的两个坑（`bootps` 按位或、
`POST /settings` 是全表单接口）。

### 17.7 验收结果

- **固件 CI** run 45307680625 **success**（`pio run -e color_lamp_c3` 全步骤通过）
- **Android CI** run 45307660122 **success**
- **模拟器 emulator-5554**：
  - 启动闪屏 → 通透的青→蓝→紫发光丝带 + 底部青色涟漪，无深蓝方块 ✅
  - 桌面启动器图标 → 圆角方形 mask 下丝带与涟漪完整、无削切 ✅
- **App 灯效库**：条目 221「极光之舞」在册（`effects_enabled.json` 222 条 +
  `EffectCatalog.kt:231`）

### 17.8 关键代码位置

| 位置 | 内容 |
|---|---|
| `docs/assets/lumi_aurora_icon_source.jpg` | 图标唯一素材源（用户提供的原图） |
| `tools/gen_app_icon.py` | 改为位图驱动：legacy 方标/圆标、adaptive 前景层、闪屏图标、monochrome 剪影、iOS 1024 |
| `res/mipmap-*/ic_launcher_foreground_adaptive.png` | adaptive 前景层（边长 = legacy × 108/48） |
| `res/drawable/ic_launcher_{foreground,monochrome}.xml` | 改为 `<bitmap>` 引用位图层 |
| `res/drawable/ic_launcher_background.xml` | 深空对角渐变（采样自原图四角） |
| `iosApp/.../AppIcon.appiconset/wled-icon.png` | iOS 1024 AppIcon（原 `wled-icon-gray.png`） |
| `firmware/wled00/FX.{h,cpp}` | 灯效 221「极光之舞」实现与注册 |
| `firmware/platformio_override.ini` | `-D DEFAULT_BOOT_FX=221` 出厂默认 |

### 17.9 未做

1. 旧 `homeScreen/` 列表链清理（见 15.8）。
2. 配方导入导出与配方预览（P1）。
3. per-fx 参数 schema（P2 的前置）。
4. iOS 仍搁置（本轮只换了 AppIcon 位图，iOS CI 的既有 failure 未处理）。
5. 设备端「Save to ID 1 + Apply preset at boot = 1」需在真机上执行，
   仓库侧只保证两条路径都可用（见 17.6）。
