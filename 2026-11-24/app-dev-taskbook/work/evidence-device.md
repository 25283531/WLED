# 设备实测证据（2026-11-24）

设备：`10.0.1.172`，`GET /json`、`GET /json/info`、`GET /json/cfg`、`GET /json/state`。

> **v1.1 更正**：下列 `count=345`、`matrix 15×23` 是设备上的**错误配置**，实际硬件为 **9 列 × 23 颗 = 207**，
> 1 号位在第 1 列底部，第 46 颗（第 2 列底部）损坏，由 **APP 端留黑点**（不补偿），固件不跳过。
> 数据保留原样作为「错误配置」证据。

## 基本信息

```json
info.name = "WLED"
info.ver = "16.0.1"
info.arch = "ESP32-S3"
info.fxcount = 220
info.palcount = 73
info.um = [32]                    // 32 = USERMOD_ID_AUDIOREACTIVE
info.leds = {"count":345,"matrix":{"w":15,"h":23},"bootps":0,"maxseg":64,"maxpwr":4500}
info.fs = {"used":28,"total":12451}   // KB
```

## 硬件配置（config 与 info 不一致，待校正）

```json
cfg.hw.led.total = 345
cfg.hw.led.ins   = [{"start":0,"len":206,"pin":[10],"type":22,"maxpwr":4500,"ledma":55}]
cfg.hw.led.matrix= {"mpc":1,"panels":[{"b":true,"r":false,"v":true,"s":true,"x":0,"y":0,"h":23,"w":9}]}
cfg.def          = {"ps":0,"on":true,"bri":128}
cfg.wifi         = {"sleep":false,"phy":false,"txpwr":78}   // 不含 SSID/密码
```

## 音频 usermod

```json
cfg.um.AudioReactive = {
  "enabled": false,
  "add-palettes": false,
  "digitalmic": {"type":1, "pin":[32,15,14,-1]},   // 1 = 通用 I2S
  "config": {"squelch":10, "gain":60, "AGC":0},
  "frequency": {"scale":3},
  "dynamics": {"limiter":true, "rise":80, "fall":1400},
  "sync": {"port":11988, "mode":0}
}
```

### 开启实验（已还原）

```http
POST /json/state  {"AudioReactive":{"enabled":true}}
```

`GET /json/info.u.AudioReactive`：

```
Audio Source: not initialized - check pin settings
Sound Processing: suspended
```

结论：`digitalmic.type=1`（I2S，引脚 32/15/14）在当前板上初始化失败。
v1.3 按原理图核实：麦克风为**双路 ZTS6216 模拟麦 + ES7210 四通道 ADC**（I²C 0x41；I²S BCK=IO14、WS=IO13、MCLK=IO38、DI=IO12），
不是单颗数字硅麦；WLED 无 ES7210 驱动，需自补 I²C 初始化。实验后已撤销：

```http
POST /json/state  {"AudioReactive":{"enabled":false}}
```

## 效果与字幕

- `GET /json.effects` 共 220 条；`122 = Scrolling Text`、`139 = GEQ`、`186 = Akemi`、`53 = Image` 均在。
- 当前段落示例：`fx=75, pal=2, ix=60, c1=128, c2=255, c3=0`（`c3=0` 在固件里即上下颠倒）。
- `POST {"seg":[{"fx":221}]}` 返回 `{"success":true}`，回读仍为原值 → 设备无 221。

## 复现命令

```powershell
curl -s http://10.0.1.172/json/info
curl -s http://10.0.1.172/json/cfg
curl -s http://10.0.1.172/json/state
curl -s -X POST -H "Content-Type: application/json" --data-binary "@build/ar_enable.json" http://10.0.1.172/json/state
```

## M0 刷机后验证（v1.5 追加）

刷入 CI 产物 `ColorLamp-S3-C01-ota.bin`（`POST /update`，带 `skipValidation=1` 绕过旧 release 名校验）。

### 1. 启动 / WiFi / info 可读

```json
{"ver":"17.0.0-devV5","release":"ColorLamp-S3-C01","arch":"ESP32-S3","flash":16,"psram":7059104,
 "wifi":{"bssid":"5C:02:14:73:FF:14","rssi":-51,"signal":98,"channel":6,"ap":false},
 "brand":"LumiIsle","product":"C01","mac":"e80690a6c20c","ip":"10.0.1.172"}
```

### 2. 型号 C01

`info.brand="LumiIsle"`、`info.product="C01"`，来自编译期 `WLED_BRAND` / `WLED_PRODUCT_NAME`。

### 3. 灯珠布局（关键）

首次回读仍为 `count=346 / matrix=15×23`。根因：设备残留旧 `/ledmap.json`（`width=15,height=23`，346 项）覆盖面板配置。
删文件并重启后：

```json
info.leds = {"count":207,"matrix":{"w":9,"h":23},"bootfx":0,"maxpwr":4500}
state.seg[0] = {"start":0,"stop":9,"startY":0,"stopY":23,"len":9}
cfg.hw.led   = {"total":207,"matrix":{"mpc":1,"panels":[{"b":true,"r":false,"v":true,"s":true,"h":23,"w":9}]},
                "ins":[{"start":0,"len":207,"pin":[10],"type":22}]}
```

- 删除命令：`GET /edit?func=delete&path=/ledmap.json`，随后 `GET /win&RB` 重启（`POST /json/state {"rb":true}` 未触发重启）。
- 旧 ledmap 已备份：`work/backup/e80690a6c20c/ledmap.json`。
- 物理位置目视（第 1 列最左、1 号位左下、46 号坏点）需人眼确认；已按 §0.4 亮度上限（白光 `bri=128`）点亮第 2 列底部两颗作对照。

### 4. 开机默认不再强制极光之舞

`info.leds.bootfx=0`，未定义 `DEFAULT_BOOT_FX`；开机按 `cfg.def` / 用户设置执行。

### 5. ES7210 音频链路

临时开启后：

```json
info.u.AudioReactive = {"Audio Source":["not initialized"," - check pin settings"],
                        "Sound Processing":["suspended"]}
```

结论：**未通过**。WLED usermod 无 ES7210 驱动，M0 只写入 I²S 引脚与 `SR_DMTYPE=1`，需在 T2.3.1 补 I²C 寄存器初始化。已还原 `enabled=false`。

## v1.6：46 号拆除导致一位偏移，ledmap 修正（2026-11-24 实测）

### 硬件事实

第 46 颗（第 2 列底部）**已物理拆除，数据线短接**；灯带实际 **206 颗**，固件仍发 207 包，第 46 颗之后**整链前移 1 格**（末包无灯承接）。

### 偏移量化（三锚点，`bri=128`，最底一排 `startY=22/stopY=23`）

下发 3 段：`(x=0,y=22)` 白、`(x=1,y=22)` 红、`(x=2,y=22)` 紫；回读 `segcount=3`、坐标与颜色均生效。

| 逻辑位置 | 修正前实测（用户目视） | 修正后实测 |
|---|---|---|
| `(0,22)` 第 1 列最底 | 白在第 1 列最底 | 白在第 1 列最底 |
| `(1,22)` 第 2 列最底（第 46 颗原位） | **红在第 3 列最底** | **不亮** |
| `(2,22)` 第 3 列最底 | **紫在第 3 列倒数第二颗** | 紫在第 3 列最底 |

→ 两轮目视一致，**偏移恰好 1 格**；修正后红消失、紫回到应落位置，映射定稿。

### 修正文件与校验

`work/ledmap.json`：`{"width":9,"height":23,"map":[207 项]}`，按 `map[L]=p(L)-(p(L)>45)`，
`p(L)=x*23+(x 奇?y:22-y)`，被拆位置 `map[199]=206`（第 207 包无灯承接，等效熄灭）。

写入与回读：

```
POST /upload   (multipart, field=data, filename=ledmap.json)   # 用 curl -F "data=@work/ledmap.json"
GET  /ledmap.json                                              # 回读，与本地逐键比对一致
GET  /win&RB                                                   # 重启
GET  /json/info → leds.count=207, matrix={w:9,h:23}, bootfx=0   # 保持 207，矩阵未少格
GET  /ledmap.json → 内容仍在，重启后仍生效
```

踩坑记录：最初把空位写成 `-1`，`numPhy=206` 导致 WLED 补一个尾部幽灵像素，
`info.leds.count` 变 **208**；改为 `206` 后 `count` 恢复 207。

## v1.6：ES7210 固件 OTA 与音频初始化验证（2026-11-24）

### OTA

- 产物：`work/fw-es7210/ColorLamp-S3-C01-ota.bin`（1,277,600 B，SHA256 `1B73F1DD…E77190`，CI run `45429545563` / commit `3830390`）。
- 第一次失败：`Firmware release name mismatch: current='ColorLamp-S3-C01', uploaded='ColorLamp-S3-C01evV5'`。
  原因：`ota_update.cpp` 用 `request->getParam("skipValidation", true)` 读 **POST 表单字段**，
  放在 URL 查询串 `?skipValidation=1` 不生效。
- 正确命令（`-F "skipValidation=1"` 作为表单字段）：

```
curl.exe -F "skipValidation=1" -F "update=@ColorLamp-S3-C01-ota.bin" http://10.0.1.172/update
→ "Update successful! Rebooting now..."
```

### 刷后回读

```json
info.ver = "17.0.0-devV5", release="ColorLamp-S3-C01", product="C01"
info.leds = {"count":207,"matrix":{"w":9,"h":23},"fps":43,"maxpwr":4500,"bootfx":0}
ledmap.json = {"width":9,"height":23,"map":[207 项]}  # map[198]=0, map[199]=206, map[200]=45，OTA 未影响文件系统
```

### 音频配置与结果

`POST /json/cfg`（部分更新即可）：

```json
{"um":{"AudioReactive":{"enabled":true,"digitalmic":{"type":4,"pin":[12,13,14,38]}}}}
→ {"success":true}
```

回读 `cfg.um.AudioReactive = {enabled:true, digitalmic:{type:4, pin:[12,13,14,38]}}`，`GET /win&RB` 重启后（uptime≈186 s）：

```json
"u": {
  "AudioReactive":       ["<button ... {AudioReactive:{enabled:false}} ... icons on ...>"],
  "Audio Input Level":   ["... type=\"range\" value=128 ..."],
  "Audio Source":        ["I2S digital", " - quiet"],
  "Sound Processing":    ["running"],
  "Manual Gain":         [1.56, "x"],
  "UDP Sound Sync":      ["off"]
}
```

对比 M0 的失败态（`Audio Source: "not initialized" / " - check pin settings"`、`Sound Processing: "suspended"`）已消失。
`info.leds.count=207 / matrix 9×23` 保持不变。

结论：**ES7210 初始化通过、音频处理任务 running**；`- quiet` 表示当前环境无输入（未播放音乐），
实际拾音与静音不闪烁留待 T2 联调。

## v1.7：真拾音与 GEQ 律动实测（2026-11-24，用户现场播放音乐）

设备：`10.0.1.172`，`17.0.0-devV5`，`release=ColorLamp-S3-C01`，`count=207 / 9×23`，
`um.AudioReactive = {enabled:true, digitalmic:{type:4, pin:[12,13,14,38]}}`。
测试亮度：全局 `bri=153`（彩色 60%），未超 §0.4 约束。

### 1. 连续采样 `/json/info.u`（间隔 1.5 s，10 次）

| # | 时间 | Audio Source | Sound Processing | leds.pwr |
|---|---|---|---|---|
| 1 | 00:14:08 | `I2S digital` / `- peak  75%` | running | 427 |
| 2 | 00:14:09 | `I2S digital` / `- peak  75%` | running | 374 |
| 3 | 00:14:11 | `I2S digital` / `- peak  53%` | running | 457 |
| 4 | 00:14:12 | `I2S digital` / `- peak  85%` | running | 494 |
| 5 | 00:14:14 | `I2S digital` / `- peak  58%` | running | 337 |
| 6 | 00:14:15 | `I2S digital` / `- peak  97%` | running | 447 |
| 7 | 00:14:17 | `I2S digital` / `- quiet`     | running | 420 |
| 8 | 00:14:18 | `I2S digital` / `- peak  95%` | running | 590 |
| 9 | 00:14:20 | `I2S digital` / `- peak  95%` | running | 410 |
| 10 | 00:14:21 | `I2S digital` / `- peak  75%` | running | 649 |

`peak%` 采样前另有一次 `peak 80%`。**10 次中 9 次有声**，仅 1 次落到 `- quiet`（对应音乐间隙），
`Sound Processing` 始终 `running`，`leds.pwr` 337–649 mA 随音乐波动。

第二组 55 次采样（0.35 s 间隔，效果 PS GEQ 2D）：`peak%` 均值 **79.9**，`min=27 max=100`，**55/55 有声**。

### 2. 效果侧律动对照（同一音乐窗口，`bri=153`）

| 效果 | fx | 参数 | pwr min..max | Δ | 说明 |
|---|---|---|---|---|---|
| Solid | 0 | — | 2604..2604 | 0 | 静态基准（207 颗常亮） |
| GEQ (2D) | 139 | 默认 `sx=200` | 327..426 | 99 | 淡出过快，几乎全黑 |
| GEQ (2D) | 139 | `sx=64, ix=240, c1=64` | 452..1999 | **1547** | 柱状律动明显 |
| GEQ (2D) | 139 | `sx=64, ix=240, c1=255` | 327..1275 | 948 | 16 频带，亮度低于 4 频带 |
| PS GEQ 2D | 198 | `sx=155, ix=200` | 346..1083 | **737** | 与 `peak%` 正相关（corr +0.41） |
| PS GEQ 1D | 212 | 默认 | 327..557 | 230 | 可动但幅度小 |
| Freqwave | 137 | 默认 | 398..1278 | 880 | 有变化 |
| Ripple Peak | 148 | 默认 | 1708..2604 | 896 | 有变化 |
| Noise2D（反例，非音频） | 146 | — | 2712..2768 | 56 | 仅噪声自走，功率几乎不变 |

**结论：音频数据确实进入了效果层**，`GEQ(139)` / `PS GEQ 2D(198)` 均随节拍变化；用户目视确认「音律灯效测试正常」。
`GEQ(139)` 用默认 `sx=200` 时 `SEGMENT.fadeToBlackBy(speed)` 每帧淡出 200/255，柱高被瞬间抹掉，
功率恒 327 mA，**极易误判为「音频未到效果」**——验收必须把 `sx` 降到 ~64 或调高 `ix`。

> 一次 A/B 对照（关 `AudioReactive.enabled`）因当时音乐已停、`peak%` 全 0，数据被污染，**不作为结论**；
> 判定以参数扫描 + 用户目视确认为准。测试后已恢复 `enabled=true`，末尾回读 `Audio Source=["I2S digital"," - peak  91%"]`、`running`。

### 3. 复现命令

```powershell
# 单次/连续读
curl.exe -s http://10.0.1.172/json/info

# 设 GEQ 并给可见参数（彩色 60%）
curl.exe -s -X POST http://10.0.1.172/json/state -H "Content-Type: application/json" -d '{"on":true,"bri":153,"transition":0,"mainseg":0,"seg":[{"id":0,"start":0,"stop":9,"startY":0,"stopY":23,"on":true,"fx":139,"pal":0,"sx":64,"ix":240,"c1":64,"c2":64,"c3":16}]}'

# 切换 PS GEQ 2D
curl.exe -s -X POST http://10.0.1.172/json/state -H "Content-Type: application/json" -d '{"on":true,"bri":153,"transition":0,"mainseg":0,"seg":[{"id":0,"start":0,"stop":9,"startY":0,"stopY":23,"on":true,"fx":198,"pal":0,"sx":155,"ix":200}]}'
```

波形脚本：`%TEMP%\wled_audio_probe.py`（采样 `peak%`）、`wled_geq_variants.py`（参数扫描）、
`wled_geq_corr.py`（`peak%` 与 `pwr` 相关性）。




