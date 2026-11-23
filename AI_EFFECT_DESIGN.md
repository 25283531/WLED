# AI 灯效生成与收藏功能 — 详细设计（未来计划）

文档状态：设计完成，尚未实施。
最后更新：2026-11-23

## 0. 决策记录

| 议题 | 本次决策 | 说明 |
|---|---|---|
| AI 云端接入 | **暂缓，列入未来计划** | Key 策略未定，见第 7 节三方案对比 |
| 收藏存储 | **App 本地数据库优先** | 复用现有 Room 多平台数据库 |
| 本轮交付 | **详细设计文档** | 不修改任何代码 |

---

## 1. 背景与目标

用户希望：在 App 内输入自然语言想法，由 AI 生成灯效并下发到灯本体执行；用户可收藏喜欢的灯效，实现一键快速切换，避免在一长串灯效列表中反复查找。

目标拆解为两个可独立交付的能力：

1. **收藏与快速切换**（一期，不依赖 AI）：保存任意一组灯效参数，一键恢复；可收藏设备当前正在播放的灯效。
2. **AI 生成灯效参数**（二期，依赖 Key 决策）：把文字描述翻译成固件可执行的一组参数。

---

## 2. 核心约束：AI 不能"生成灯效算法"

这是整个设计的出发点，必须明确：

- WLED 的灯效是**编译进固件的 C++ 函数**。灯效 ID 在 `firmware/wled00/FX.h:143-373` 以 `#define` 定义为 0–220（`MODE_COUNT=221`）；`FX.cpp:11041` 起的 `setupEffectData()` 通过 `addEffect(id, &mode_xxx, name)` 把函数指针注册进 `_mode[]` 表；运行时 `FX_fcn.cpp:1492` 以 `_mode[seg.mode]()` 按数字下标调用。
- 对外的 JSON API **只暴露参数，不暴露算法**：`/json/effects` 返回名称数组，`/json/fxdata` 返回参数元数据；控制端只能发一个数字 ID（`fx`）。
- 固件侧**无本地 AI，也无调用云端 LLM 的 HTTP 客户端**（网络能力是作为 HTTP/WS server 接收状态）。因此 AI 只能在 App 或云端完成，不能让灯做 AI。

**结论：AI 的产出物必须是一组"参数配方"，由固件既有的灯效引擎渲染。** 好消息是现有参数的组合空间极大（221 种效果 × 速度 × 强度 × 调色板 × 三色 × 自定义参数），足以表达绝大多数想法，且固件零改动。

---

## 3. 架构总览

```
[用户输入想法]
      |
      v
[App: AI service] --HTTPS--> [云端 LLM]    (二期，Key 待决策)
      |  输出参数 JSON（严格 schema + 校验）
      v
[App: 参数下发] --WebSocket/HTTP--> [固件 deserializeState] --> [_mode[fx]()] --> [灯带]
      ^
      |
[App: 收藏库 Room] <-- 收藏当前 / 应用收藏
```

分层职责：

| 层 | 职责 | 是否改动固件 |
|---|---|---|
| AI 层（App/云端） | 文字 → 参数 JSON，输出校验 | 否 |
| App 下发层 | 把参数组装成 `State`，经 WS/HTTP 发送 | 否 |
| 固件层 | 用既有引擎渲染 | 否 |
| 收藏层（App） | 参数持久化与一键恢复 | 否 |

---

## 4. 参数协议设计

### 4.1 现有能力矩阵

来源：`firmware/wled00/json.cpp:288-358`（`deserializeSegment`）。"App 模型"指 `Segment.kt` / `State.kt` 现状。

| 字段 | 含义 | 范围 | 固件支持 | App 模型 |
|---|---|---|---|---|
| `fx` | 灯效 ID | 0–220（去重后部分为 RSVD） | 是 | 有 |
| `sx` | 速度 | 0–255 | 是 | 有 |
| `ix` | 强度 | 0–255 | 是 | 有 |
| `pal` | 调色板 ID | 0–`getPaletteCount()` | 是 | 有 |
| `col` | 主/辅/第三色 RGBW | 每组 3–4 个 0–255 | 是 | 有（`List<List<Int>>`） |
| `bri` | 亮度 | 0–255 | 是 | 有（State/Segment 均有） |
| `on` | 开关 | bool | 是 | 有 |
| `transition` | 过渡时长 | 0–65535（0.1s 单位） | 是 | 有（State） |
| `c1`/`c2`/`c3` | 效果自定义参数 | 0–255 / 0–255 / 0–31 | 是 | **缺** |
| `o1`/`o2`/`o3` | 效果自定义开关 | bool | 是 | **缺** |
| `ps` | 调用预设 | 1–250 | 是 | 有 |
| `psave` | 保存为预设 | 1–250 | 是 | **缺** |

### 4.2 AI 输出 Schema（一期建议）

只使用 App 已有字段，零模型改动：

```json
{
  "name": "深海脉冲",
  "on": true,
  "bri": 200,
  "transition": 7,
  "seg": [{
    "fx": 9,
    "sx": 160,
    "ix": 220,
    "pal": 0,
    "col": [[0, 80, 255, 0], [0, 0, 60, 0], [0, 0, 0, 0]]
  }]
}
```

`name` 用于收藏条目命名，不发给固件。`col` 为 RGBW 四元组，W 通常为 0。

### 4.3 校验规则（防 AI 幻觉）

下发前必须通过客户端白名单校验：

1. `fx` 必须属于**当前设备可用效果列表**——从 `GET /json/effects` 拉取后过滤掉 `RSVD` 条目。本项目删了 29 个效果，固件 `FX_fcn.cpp:711-750` 虽有别名映射兜底（旧 ID 自动转到保留效果），但列表展示必须过滤，否则出现"幽灵选项"。
2. `sx`/`ix`/`bri` 钳制到 0–255；`transition` 钳制到合理上限。
3. `col` 每组长度 3–4，每个分量 0–255。
4. `pal` 不超过 `info.palcount`（`Info.kt` 已解析该字段，目前未使用，正好复用）。
5. 名称长度上限（如 40 字符），拒绝空 JSON / 非 JSON 输出。

### 4.4 Prompt 设计要点

- 把**当前设备的可用效果清单**（ID + 名称）与调色板清单注入 system prompt——这样 AI 不会输出被删掉的 ID，也让名字贴合本设备。
- 使用 JSON mode / response_format 约束输出，配合 few-shot 示例（2–3 个"想法 → 参数"样本）。
- 明确要求"只输出 JSON，不要解释文字"。

---

## 5. 下发链路设计（关键红利）

**推荐走 WebSocket**：`DeviceConnectionManager.kt:616` 的 `sendState(macAddress, patch: State)` 已能承载完整 `seg`（含 `col`），序列化走 `WebsocketClient.kt:377-414` 的 `json.encodeToString(state)`，`defaultJson` 配置 `explicitNulls=false` 会省略 null 字段。

- **零模型改动、零固件改动**即可下发 fx/sx/ix/pal/col/bri/on/seg。
- 固件侧 WS 文本帧（`wled00/ws.cpp:53,65`）与 HTTP POST `/json`（`wled00/wled_server.cpp:422-450`）**走同一套 `deserializeState`**（`json.cpp:370-575`），所以两条链路等效。
- 卡点：`sendState` 要求设备有活跃 WS session（在线设备）；离线设备不可用。
- HTTP 备选：`DeviceApi.kt:42` 的 `postJson` 目前只收 `JsonPost`（仅 on/bri/v），需放宽签名为 `State` 才能发灯效，但可做（模型已就绪）。

三个可选增强（按优先级后置）：

1. **补 c1/c2/c3 + o1/o2/o3** 到 `Segment.kt`：让 AI 能控制部分效果的"高级滑块"，改动小（1 个模型文件 + 序列化测试）。建议至少预留字段。
2. **固件端收藏 `psave`/`ps`**：收藏写进设备 `/presets.json`（LittleFS），断网可用、换手机可切。需在 `State.kt` 补 `psave`，走 WS 或放宽后的 HTTP。二期再做。
3. **自定义调色板上传**：固件靠 `/paletteN.json` 上传（`colors.cpp:267-318`），App 无原生接口。一期用 `col` 三色 + 内置 `pal` 即可。

---

## 6. 收藏功能设计（一期，App 本地 Room）

### 6.1 数据模型

新增实体（建议表名 `FavoriteEffect`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | Long (PK, autoGenerate) | 主键 |
| `name` | String | 收藏名称（AI 生成或用户命名） |
| `paramsJson` | String | 完整参数 JSON（第 4.2 节 schema 的序列化结果） |
| `deviceMac` | String? | 可选：限定设备；为空表示通用 |
| `createdAt` | Long | 创建时间 |

用 `paramsJson` 单列存整段参数，避免为十几个字段逐列建表，也便于原样重发。

### 6.2 复用现有机制

- 数据库：`DevicesDatabase.kt:22-51`（Room 多平台，`BundledSQLiteDriver`，当前 version 10）。
- 新增：`FavoriteEffect.kt`（@Entity）、`FavoriteEffectDao.kt`（Flow 列表 / @Insert / @Delete）、`FavoriteEffectRepository.kt`（仿 `DeviceRepository.kt`）。
- 迁移：bump version 10→11，手写 `Migration_10_11`（仿 `migrations/DbMigration9To10.kt`），导出 `app/schemas/.../11.json`，并在 `DevicesDatabase.kt` 的 entities 与 `.addMigrations()` 注册。
- DI：在 `SharedDatabaseModule.kt:18-29` 注册 Dao 与 Repository。
- 备选（更小改动）：把 `favoriteEffects: List<FavoriteEffect>` 加进 `UserPreferences.kt:5-19`（kotlinx.serialization 原生支持嵌套 List），改动 3–4 文件，但无法单条查询、适合几十条以内。用户已选"数据库优先"，故以 Room 为主。

### 6.3 两个核心操作

- **收藏当前灯效**：设备状态经 WebSocket 到达 `DeviceStateInfo.kt:11`（`state: State`，含 `seg`），提取 `seg[0]` 的 fx/sx/ix/pal/col + bri，序列化为 `paramsJson` 存入。用户无需 AI 也能收藏网页里调好的灯效。
- **应用收藏**：点击收藏项 → 反序列化 `paramsJson` → `sendState(mac, State(...))`，无需再调 AI，实现秒切。

### 6.4 UI 入口

- 详情页当前是 WebView 空壳（`DeviceDetail.kt:35-79` / `DeviceView.swift:22-83`），需新建原生入口。建议：TopAppBar 增加"AI/收藏"按钮，唤出 BottomSheet 或独立页面。
- 导航：Android `MainNavHost.kt:39-60` 加 `FavoritesKey`；iOS `DeviceDetailRoute` (`:8-11`) 加 `.aiEffects/.favorites`。
- 表单模板可参考 `deviceAdd/DeviceAdd.kt`、`deviceEdit/DeviceEdit.kt`。

---

## 7. AI 接入方案（二期，待决策）

| 方案 | 做法 | 优点 | 代价 |
|---|---|---|---|
| **设置页自填 Key** | 用户填 OpenAI/兼容接口 Key，存 `UserPreferences`，App 直连 | 实现最快、无需后端 | 用户自备 Key；客户端持有密钥有泄露/合规风险 |
| **后端代理转发** | 部署代理服务保管 Key，App 调代理 | 用户零配置、密钥不落客户端 | 需部署运维成本 |
| **本地模板生成** | 预设规则 + 随机组合生成配方 | 离线、零成本、无隐私外发 | 创意上限低 |

工程准备（两方案共用）：

- 仿 `GithubApiEndpoints.kt:34-55`（baseUrl + Ktor）与 `GithubApi.kt:14-38`（Result 封装）新增 `AiApiEndpoints` / `AiApi`，约 100 行；在 `SharedNetworkModule.kt:15-23` 注册。
- `HttpClient` 已有（Android OkHttp / iOS Darwin），建议**另建 client 或 `config{}`**，因为现有实例绑定了设备超时/WebSocket/无鉴权。
- 网络权限已具备（AndroidManifest `INTERNET`）。
- 无密钥存储先例（无 buildConfigField / Secrets.xcconfig），需自行决策。
- 若采用云端方案，须更新 `app/PRIVACY.md` 明示用户输入会外发。

---

## 8. 多语言改动面

| 平台 | 文件 | 注意 |
|---|---|---|
| Android | `values/strings.xml`、`values-zh/`、`values-fr/`、`values-de/` | `app/build.gradle.kts:89` 设 `MissingTranslation` 为 fatal，**四语必须全加否则构建失败** |
| iOS | `iosApp/wled/Localizable.xcstrings` | 法语 tag 是 **`fr-CA`** 不是 `fr`；key 为英文原文，每个 key 下需补 de/en/fr-CA/zh-Hans 四条 |

---

## 9. 分阶段路线图（未来计划）

| 阶段 | 内容 | 依赖 | 固件改动 |
|---|---|---|---|
| **P0** | 设计评审（本文档） | 无 | 无 |
| **P1** | 收藏与快速切换：Room 表 + 收藏当前灯效 + 一键恢复 + 两端 UI | 无 | 无 |
| **P2** | AI 接入：先定 Key 策略，再实现 AI service + Prompt + 校验 + 下发 | Key 决策 | 无 |
| **P3** | 增强：c1/c2/c3、固件端 psave 收藏、自定义调色板上传 | P1/P2 反馈 | 部分 |

关键路径：**P1 可立即启动且不依赖任何未决事项**（用户已定收藏用本地数据库、固件零改动）。P2 的唯一阻塞点是 Key 策略。

---

## 10. 风险与开放问题

1. **灯效去重冲突**：29 个效果已合并为 `RSVD`。AI 输出这些 ID 时，固件别名映射会兜底，但列表必须过滤，否则用户看到无响应选项。
2. **AI 幻觉/越界**：必须有第 4.3 节校验层，禁止把未校验的 JSON 直接下发。
3. **密钥安全与合规**：客户端持有 Key 有风险；云端方案需隐私政策同步。
4. **在线依赖**：WS 下发仅对在线设备有效；离线设备需 HTTP 路径（需放宽 `postJson`）。
5. **c1/c2/c3 缺失**：不补也能用；若 AI 想控制高级参数，需补模型字段。
6. **收藏跨设备语义**：`deviceMac` 是否限定需产品决策；留空则通用但可能因设备灯效集不同而不一致。

---

## 11. 关键代码位置索引

**固件**
- `firmware/wled00/FX.h:143-373` 效果 ID 定义；`FX.cpp:11041+` 注册；`FX_fcn.cpp:711-750` 去重别名映射、`:1492` 运行时调度
- `firmware/wled00/json.cpp:70-366` segment 参数解析；`:288-358` fx/sx/ix/pal/col/c1-c3/i；`:505-506` psave/ps；`:1241-1253` `/json/effects` 序列化
- `firmware/wled00/ws.cpp:53,65` WS 入口；`wled_server.cpp:422-450` HTTP 入口；`presets.cpp:218` savePreset
- `firmware/platformio_override.ini` PIXEL_COUNTS=60、custom_usermods；ESP32-C3 无 PSRAM，逐帧推流方案不推荐

**App**
- `shared/.../model/wledapi/State.kt:6-17`、`Segment.kt:6-24` 参数模型（缺 c1-c3/psave）
- `shared/.../service/api/DeviceApi.kt:42,79-89` postJson（仅 JsonPost）；`DeviceConnectionManager.kt:616` sendState（可发完整 State）
- `shared/.../repository/DevicesDatabase.kt:22-51,58-71` Room DB；`migrations/DbMigration9To10.kt` 迁移模板
- `shared/.../repository/UserPreferences*.kt` DataStore 与 Key 存储候选
- `shared/.../service/api/github/GithubApiEndpoints.kt` AI service 模板；`SharedNetworkModule.kt:15-23` DI
- `app/.../ui/homeScreen/detail/DeviceDetail.kt:35-79`、`iosApp/wled/View/DeviceView.swift:22-83` UI 入口
- 多语言：`app/src/main/res/values{,-zh,-fr,-de}/strings.xml`、`iosApp/wled/Localizable.xcstrings`
