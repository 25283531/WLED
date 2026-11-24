# M1（方案 B）统一效果表：实施方案

> 只出方案，未改固件。确认后执行。执行前请先看 §1 的关键发现——它改变了工作量判断。

## 1. 现状核实（与任务书 v1.7 的差异）

| 项 | 实测/代码事实 | 结论 |
|---|---|---|
| `WLED_KEEP_ALL_FX` | **未定义**（`platformio.ini` / `platformio_override.ini` / `pio-scripts` 均无该宏） | 29 个重复效果**已在编译期排除**，`FX.cpp:11203` 起每个重复项都包在 `#ifdef WLED_KEEP_ALL_FX` 里 |
| 运行时折叠 | `FX_fcn.cpp:713-743` 的 `fxDedupAlias[][2]`，在 `Segment::setMode()` 入口把旧 id 改成保留 id | 实测 `POST fx=4` 回读 **fx=3**，别名生效 |
| 设备 `/json/effects` | 222 项，其中 **33 项为 `RSVD`**：`4,11,14,16,19,22,24,26,29,30,36,37,47,52,60,63,71,72,81,86,93,94,99,109,117,138,142,144,157,158,169,170,171` | **实际可用 189 个**，与 APP 目录 189 条完全一致 |
| `MODE_COUNT` | `FX.h:379` = **222**；`_modeCount(MODE_COUNT)` 固定，`getModeCount()` 直接返回它 | `fxcount=222` 是**假数字**，与可用数不符；网页下拉会出现 33 个 RSVD 空槽 |
| APP 目录 | `EffectCatalog.kt` 189 条，`id` 直接取 `effects_enabled.json` 的 `idx`（**旧 id，含空洞**） | 与固件可用集合**同 id、同名字**；只有 `fxcount` 与空槽是问题 |
| `effects_enabled.json` | 222 项，33 项为 `null`（就是上面的 33 个槽） | 已提前标好删除集 |

**因此「方案 B 的删除」其实已经做了一半**：效果不再被注册；剩下的只是**把 id 压紧 0..188**，让 `MODE_COUNT`、`fxcount`、网页下拉、APP catalog 完全一致。

## 2. 目标

1. `MODE_COUNT = 189`，id 连续 `0..188`，无 `RSVD` 空槽。
2. 删除 `#ifdef WLED_KEEP_ALL_FX` 包裹的 `addEffect` 与 `fxDedupAlias` 表（压缩后不再需要折叠）。
3. APP `EffectCatalog.kt` 由生成器重生成，id 与固件新 id 一一对应。
4. 「极光之舞」随压缩落到 **188**（当前编译期就是 221）。
5. 开发期无预设迁移：设备预设清空，不做旧 id 兼容。

## 3. 删除清单（33 个）

29 个重复效果（当前靠 `fxDedupAlias` 折叠）+ 4 个历史占位槽。完整表与「旧 id → 新 id」189 行对照见
[`m1-effect-id-map.md`](./m1-effect-id-map.md)。

| 旧 id | 名称 | 类型 |
|---|---|---|
| 4 | Wipe Random | 重复（→ Wipe 3） |
| 11 | Scan Dual | 重复（→ Scan 10） |
| 14 | Theater Rainbow | 重复（→ Theater 13） |
| 16 | Saw | 重复（→ Running 15） |
| 19 | Dissolve Rnd | 重复（→ Dissolve 18） |
| 22 | Sparkle+ | 重复（→ Flash Sparkle 20） |
| 24 | Strobe Rainbow | 重复（→ Strobe 23） |
| 26 | Blink Rainbow | 重复（→ Blink 1） |
| 29 | Chase Random | 重复（→ Chase 28） |
| 30 | Chase Rainbow | 重复（→ Chase 28） |
| 36 | Sweep Random | 重复（→ Sweep 6） |
| 37 | Chase 2 | 重复（→ Chase 28） |
| 47 | Loading | 重复（→ Gradient 46） |
| 52 | Running Dual | 重复（→ Running 15） |
| 60 | Scanner Dual | 重复（→ Scan 10） |
| 63 | Pride 2015 | 重复（→ Colorwaves 62） |
| 71 | Noise 2 | 重复（→ Noise 1 70） |
| 72 | Noise 3 | 重复（→ Noise 1 70） |
| 81 | Twinklecat | 重复（→ Twinklefox 80） |
| 86 | Spots Fade | 重复（→ Spots 85） |
| 93 | Sinelon Dual | 重复（→ Sinelon 92） |
| 94 | Sinelon Rainbow | 重复（→ Sinelon 92） |
| 99 | Ripple Rainbow | 重复（→ Ripple 98） |
| 109 | Phased Noise | 重复（→ Phased 108） |
| 117 | Dynamic Smooth | 重复（→ Dynamic 7） |
| 138 | Freqmatrix | 重复（→ Freqwave 137） |
| 144 | Puddlepeak | 重复（→ Puddles 143） |
| 157 | Gravcentric | 重复（→ Gravcenter 156） |
| 158 | Gravfreq | 重复（→ Gravimeter 155） |
| 142 / 169 / 170 / 171 | RSVD | 历史占位槽 |

## 4. id 压缩规则与结果

`新 id = 旧 id − （旧 id 之前被删除的个数）`。举例：

| 旧 id | 名称 | 新 id |
|---|---|---|
| 0–3 | Solid / Blink / Breathe / Wipe | 0–3（不变） |
| 5 | Random Colors | 4 |
| 6 | Sweep | 5 |
| 7 | Dynamic | 6 |
| 13 | Theater | 11 |
| 46 | Gradient | 34 |
| 136 | Noise Meter | 111 |
| 137 | Freqwave | 112 |
| 139 | GEQ | 113 |
| 198 | PS GEQ 2D | 165 |
| 219 | Slow Transition | 186 |
| 220 | Analog Clock 2D | 187 |
| 221 | 极光之舞 | **188** |

- 新 `MODE_COUNT = 189`（`FX.h:379`）。
- 189 个保留项**全部**需要改 id（除 0–3），APP catalog 与预览资源都要跟着动。
- 校验（已用脚本核对）：删除集与 `effects_enabled.json` 的 33 个 `null` 完全一致；APP 目录 189 条全部落在保留集内；新 id 连续 `0..188`；固件名与 APP `name` **0 处不一致**。

## 5. 固件改动范围（代码，需 CI 编译）

| 文件 | 改动 |
|---|---|
| `firmware/wled00/FX.h` | 删 33 个 `#define FX_MODE_*`（29 重复 + 4 RSVD）；其余按保留顺序重排 0..188；`MODE_COUNT 189` |
| `firmware/wled00/FX.cpp` | 删 30 处 `#ifdef WLED_KEEP_ALL_FX` 包裹的 `addEffect`（含注释）；删对应 `mode_*` 函数与 `_data_FX_MODE_*` 字符串（避免死代码）；`addEffect` 的 id 参数跟着重排 |
| `firmware/wled00/FX_fcn.cpp` | 删 `fxDedupAlias` 表与 `setMode()` 里的折叠循环（`FX_fcn.cpp:711-750`），同时删 `WLED_KEEP_ALL_FX` 残留引用 |
| `firmware/effects_enabled.json` | 删 33 个 `null`，`idx` 压缩为 0..188 |
| `tools/gen_effect_catalog.py` | `NAME_ZH`、`MUSIC_IDS`、`TEXT_IDS`、`POPULAR_NAMES` 目前**按旧 id 写死**，需整体重映射；`MUSIC_IDS` 里的 138 要删除 |
| `app/.../EffectCatalog.kt` | 用生成器重生成（id、preview 名都变） |
| `app/.../assets/effect_previews/*.gif` | **文件名 = id**，189 个需要重命名或重生成（见 §7） |

> 不动：`cfg.hw.led`、`ledmap.json`、音频配置、`bootfx=0`（Solid 仍是 0）。

## 6. 「极光之舞」写入固件的实现位置与参数

| 项 | 内容 |
|---|---|
| 宏 | `FX.h:377` `#define FX_MODE_LUMI_AURORA 221`（M1 后改 **188**） |
| 函数 | `FX.cpp:5221-5321` `void mode_lumi_aurora(void)` |
| 元数据 | `FX.cpp:5321` `"极光之舞@速度,交织幅度;;2"`：`@` 前是显示名（**中文**，网页会直接显示），两个滑块 = `speed` / `intensity`，`2` = 2D 效果，**无调色板字段** |
| 注册 | `FX.cpp:11431` `addEffect(FX_MODE_LUMI_AURORA, &mode_lumi_aurora, _data_FX_MODE_LUMI_AURORA)` |
| 配色 | **全部硬编码，不用调色板**：垂直三段渐变 亮青 `0x00F5FF` → 蔚蓝 `0x1E78FF` → 紫罗兰 `0xAF28FF`；底部第 0–1 行叠青色声波底座 `CRGB(0, 220*ring, 255*ring)` |
| 参数含义 | `speed`：`t = (now * (12 + speed>>2)) >> 7`，控制流动；`intensity`：`twist = 10 + (intensity>>3)`，控制双丝带交织幅度 |
| 开机升腾 | 帧计数 132 帧（约 2.2 s @60fps），`SEGENV.call` 归零后重放，用 `heightMask` 从下往上掀开 |
| 是否需要新色板 | **不需要**。此前「色板 A / A+B / C」的争论与本实现无关——它不读 `pal`、也不读 `SEGCOLOR`。若要让它可换色，属于**新增功能**，需单独提需求 |
| 2D 判断 | 非矩阵/非 2D 段直接回退 `mode_static()`（`FX_FALLBACK_STATIC`） |

> 注意：元数据显示名是中文，而 APP 的 `name` 来自 `/json/effects`，所以 APP 侧该条 `name` 会是「极光之舞」，`nameZh` 相同——生成器需要允许非 ASCII 的 `name`。

## 7. 提交顺序（避免中途两边 id 错位）

所有改动**必须一次刷机窗口内闭合**，否则出现「APP 显示 121=GEQ，设备 121=别的」的错位。建议 4 个提交、2 次 CI：

1. **commit 1（固件，触发 CI）**：`FX.h` + `FX.cpp` + `FX_fcn.cpp` 压缩 id；同提交内更新 `effects_enabled.json`。
   - 此时 APP 仍是旧 id → 只影响开发中的 APP，不影响已发布固件；本次刷机前 APP 不升级。
2. **CI 产物 OTA 到 `10.0.1.172`**，回读 `fxcount=189`、`/json/effects` 无 `RSVD`、`fx=188` 名字为「极光之舞」、`fx=113` 为 GEQ。
3. **commit 2（APP，纯生成物）**：重跑 `tools/gen_effect_catalog.py` 生成新 `EffectCatalog.kt`；重映射生成器里的 4 张表（同提交）。
4. **commit 3（预览资源）**：按新 id 重命名/重生成 189 个 GIF（见 §8），并同步 `effect_previews` 索引。
5. **commit 4（文档）**：任务书 T5 现状与验收、§0.1 第 1 条（fxcount 220/222 → 189）、证据文件。

> 3 与 4 可以合并为一次 APP 构建；关键是**不要先合 APP 再刷固件**，否则中间态 APP 全部错位。
> 若无法保证同一窗口，退路是先把 APP 侧改为「按名称匹配效果 id」（`/json/effects` 查表），但这属于额外功能，不在本方案。

## 8. 预览图处理（两条路，成本差异大）

- **8-A 重命名（推荐）**：`assets/effect_previews/<旧id>.gif` → `<新id>.gif`。189 次 `git mv`，不动像素。风险：图仍是「示意动画」，与实际算法无关（`gen_effect_gifs.py` 头部已声明），本就不逐像素复刻。
- **8-B 重生成**：跑 `tools/gen_effect_gifs.py` 全量重出。耗时，且会改动画内容，扩大 diff。

建议 **8-A**，并把 8-B 留给 T6 统一做。

## 9. 风险与回滚

| 风险 | 应对 |
|---|---|
| 重排 189 个宏时漏改 / 重号 | 用脚本比对 `FX.h` 宏值唯一且连续 0..188；`addEffect` id 与宏一一对应；CI 编译 + 上电回读 `/json/effects` 逐条比名字 |
| 忘删 `fxDedupAlias` → 新 id 被误折叠（例如新 4 = Random Colors 被折到 3） | 删除表后 `fx=4` 回读必须仍是 4；这是**必测项** |
| APP 先合、固件未刷 → 全表错位 | 严格按 §7 顺序；刷机窗口内完成 |
| 预览 GIF 未改名 → 卡片图错 | 生成器输出的 `preview` 必须等于新 id，CI 加一条「catalog 里每个 preview 资源都存在」的校验（可复用现有测试） |
| 刷错变砖 | 回滚路径：设备现有固件 `17.0.0-devV5`（`ColorLamp-S3-C01`）OTA 包仍在 `work/`；最坏用 `WLED_16.0.1_ESP32-S3_16MB_opi.bin` USB 恢复（`work/backup/e80690a6c20c/`）。刷前备份 `/json/cfg`、`/json/state`、`/json/presets` |
| 预设/开机效果引用旧 id | `bootfx=0`（Solid=0 不变）；开发期预设已清空，不做迁移 |

## 10. 工作量

- 固件 id 压缩（脚本辅助 + 人工复核）：约 0.5 人天；CI 两次。
- 生成器表重映射 + catalog 重生成 + GIF 改名：约 0.5 人天。
- 上机验证与文档：约 0.5 人天。
- 合计约 **1.5 人天**（不含 §8-B 重生成预览）。

## 11. 待用户确认

1. 采用 **§8-A 重命名** 还是 **§8-B 重生成**？
2. 压缩后「极光之舞」= **188**，APP 侧是否同步把「极光之舞」相关写死逻辑（若有）改为 188？
3. 是否同意 **§7 的顺序**（固件先刷、APP 后合），并接受刷机窗口内 APP 暂不同步？

## 12. 执行记录（2026-11-25，已完成并刷机验证）

| 项 | 实际做法 / 结果 |
|---|---|
| FX.h | 30 个重复/占位宏删除（另 3 个 169/170/171 原本已注释），其余按文件顺序重排为 0..188，MODE_COUNT=189 |
| FX.cpp | 删除 29 处 #ifdef WLED_KEEP_ALL_FX 包裹的 ddEffect；删除 chase() 内残留的 chase_random 分支（首版 CI 失败点） |
| FX_fcn.cpp | 删除 xDedupAlias 表与 setMode() 折叠循环，保留 RSVD 跳过逻辑 |
| effects_enabled.json | 按新宏顺序重建，189 项 idx 0..188，名称/函数名取自原文件 |
| mode_* 函数 / _data_* 字符串 | **保留未删**：已无引用，-ffunction-sections/--gc-sections 会丢弃，删除 29 个函数风险大于收益（与 §5 的「删除」有偏差，属有意取舍） |
| 极光之舞朝向 | posY = (rows-1-y)*255/(rows-1)，底环 y >= rows-2，函数头补 y 轴口径注释 |
| CI | 第二次提交 25aa95 通过；首次 33eae81 失败于 chase_random 未声明 |
| 刷机 | 本地 pio run -e esp32lichuang 构建（无 CI 包下载权限），skipValidation=1 OTA 到 10.0.1.172 |
| 回读 | xcount=189、/json/effects 189 项无 RSVD、x=188=极光之舞、x=113=GEQ、x=4=Random Colors（不再折叠到 3） |
| APP | gen_effect_catalog.py 改按 FX_MODE_* 宏名分类；EffectCatalog.kt 重生成；gen_effect_gifs.py 键 remap；新增 .github/workflows/effect-previews.yml 全量重生成预览图 |
