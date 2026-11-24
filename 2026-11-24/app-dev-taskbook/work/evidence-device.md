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
