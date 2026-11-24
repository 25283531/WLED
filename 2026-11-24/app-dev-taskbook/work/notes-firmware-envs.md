# 固件环境对照与构建/烧录注意（v1.5）

## 结论

- 主线平台：**立创实战派 ESP32-S3**（WROOM-1-N16R8：16 MB Flash / 8 MB OPI PSRAM），实机测试就在这上面做。
- **`firmware/esp32lichuang/` 与 env `esp32lichuang` 保留且为必需**：当前产品原型开发用的就是它，CI 必须构建。
- 布局：**9 列 × 23 颗 = 207**；S 形走线；1 号位在第 1 列底部；**第 1 列在最左**（`DEFAULT_MATRIX_RIGHT_START=0`）。
- 坏点：**第 46 颗（第 2 列底部）由 APP 端留黑点跳过**，不做相邻补偿；固件不跳过。
- 外观：实物为 φ50 mm 圆柱，9 列贴外表面，正视只见**第 2–8 列**；设备页只取 **7 列 × 23 行** 发光区，逻辑矩阵仍 9×23。
- 麦克风：原理图定稿为**双路 ZTS6216 模拟麦 + ES7210 四通道 ADC**（「数字硅麦」表述已作废）：
  I²C `0x41`（SCL=IO2、SDA=IO1）；I²S BCK=IO14、WS=IO13、MCLK=IO38、DI=IO12。
  ES8311 是 DAC（I²C `0x18`，DO=IO45）。WLED usermod 无 ES7210 驱动，需自补 I²C 初始化。
- 音频：APP 侧 **A 内录 + C 麦克风**，静音检测命中后弹框确认才降级，不静默切换。
- 预览：灯效 GIF 按**通用 32×18 画布**渲染，不按 9×23 灯珠排列。
- 开机默认：**取消「极光之舞」作为默认**，完全按用户设置执行。
- 测试亮度：**白光 ≤ 50%、彩色 ≤ 60%**；需要更高亮度先说明理由并取得同意。
- 构建：**只在 GitHub Actions 编译**，本地不装 JDK/SDK/PlatformIO。

## 目标 `firmware/platformio_override.ini`

| 项 | 目标值 |
|---|---|
| 默认 env | `esp32lichuang`（保留 `color_lamp_c3` 作历史对照） |
| 芯片/板 | `esp32s3` / `esp32-s3-devkitc-1`，`board_build.arduino.memory_type = qio_opi` |
| Flash/PSRAM | 16 MB / 8 MB OPI（N16R8） |
| LED 数据 | `DATA_PINS=10`（按原理图/实物核对） |
| 灯数 | `PIXEL_COUNTS=207`（9×23） |
| 矩阵 | `DEFAULT_MATRIX_VERTICAL=1`、`SERPENTINE=1`、`BOTTOM_START=1`、`RIGHT_START=0` |
| 默认效果 | **不定义 `DEFAULT_BOOT_FX`**，开机按用户设置 |
| 麦克风 | `SR_DMTYPE=1`、`I2S_SDPIN=12`、`I2S_CKPIN=14`、`I2S_WSPIN=13`、`MCLK_PIN=38`（ES7210 需额外 I²C 初始化） |
| 按键 | `BTNPIN=0,3,7`（**IO1 与 I²C SDA 冲突，已移除**） |
| 型号 | `WLED_BRAND="LumiIsle"`、`WLED_PRODUCT_NAME="C01"`、`WLED_RELEASE_NAME="ColorLamp-S3-C01"` |
| 限流 | `ABL_MILLIAMPS_DEFAULT=4500`（按实际电源容量复核） |

## 实测设备：`10.0.1.172`（S3，M0 刷机后）

| 项 | 实测 | 说明 |
|---|---|---|
| 芯片 | ESP32-S3 | 主线平台 |
| 固件 | `17.0.0-devV5`，`release=ColorLamp-S3-C01`，`brand=LumiIsle`，`product=C01` | M0 CI 产物，OTA 写入 |
| `/json/info.leds.count` | **207** | 已校正 |
| `/json/info.leds.matrix` | `{w:9,h:23}` | 已校正 |
| `cfg.hw.led.ins` | `[{start:0,len:207,pin:[10],type:22}]` | 与 info 一致 |
| `cfg.hw.led.matrix` | 9×23，`b=true,r=false,v=true,s=true` | S 形、底部起、第一列最左 |
| 开机默认 | `bootfx=0` | 不再强制极光之舞 |
| 音频 | `um.AudioReactive.enabled=false`；临时开启后仍报 `not initialized - check pin settings` | ES7210 驱动未实现（T2.3.1） |
| 坏点 | 第 46 颗（第 2 列底部）损坏 | **APP 端留黑点**，固件不动 |

> **重要**：刷机后 `count` 仍为 346 / 15×23 的根因是设备残留旧 `/ledmap.json`（width=15、height=23、346 项），
> 它会覆盖 `cfg.hw.led.matrix`。必须 `GET /edit?func=delete&path=/ledmap.json` 删除后重启，布局才生效。
> 已备份到 `work/backup/e80690a6c20c/ledmap.json`。

## 构建（CI-only）

1. 工作流 `.github/workflows/firmware.yml` 增加 `esp32lichuang` 目标：`pio run -e esp32lichuang`。
2. 产物：artifact `firmware-esp32lichuang`（`firmware.bin`、`bootloader.bin`、`partitions.bin`、`spiffs.bin`、`firmware.elf`）；
   OTA 单文件 `ColorLamp-S3-C01-ota.bin`；tag `fw-v*` 时附到 Release。
3. 校验：env 必须存在（`pio project config | grep 'env:esp32lichuang'`），产物非空。
4. 本地只读设备、跑脚本、写文档；不执行本地编译。

## 烧录

1. **OTA（首选）**
   `POST http://10.0.1.172/update`，`multipart/form-data`，字段 `update`，文件 `ColorLamp-S3-C01-ota.bin`；
   刷后校验 `GET /json/info` 的 `ver`、`fxcount`（T5 后为 189）、`product=C01`。
2. **USB 救砖**
   `esptool.py --chip esp32s3 write_flash 0x0 <firmware.bin>`（按实际串口与分区偏移）。
3. **刷前备份**
   `GET /json/cfg`、`GET /json/presets` 存到 `work/backup/<mac>/`（仅留档）。
4. **刷后校正**
   写入 207 / 9×23 / pin 10；**同时删除旧 `/ledmap.json`**（否则面板配置不生效），再重启校验 `info.leds.count=207`、`matrix=9×23`；最后验证字幕方向（`c3=16`）与 2D 效果方向。

## 风险

- `cfg` 与 `info` 的灯数/矩阵不一致时，优先怀疑残留 `ledmap.json`；只改 cfg 不会生效。
- 测试亮度上限：白光 ≤50%、彩色 ≤60%，避免超出灯珠供电能力。
- flash/PSRAM 参数错误会导致无法启动；必须保留 USB 恢复路径。
- OTA 分区不足会失败；4 MB 版需裁剪资源（与 T3 语言包、T6 预览资源体积相关）。
- ES7210 无 WLED 原生驱动，寄存器初始化错误会得到全零/爆音；I²C IO1/IO2 与按键 IO1 冲突已处理。
- T5 删除效果会改变 `fx` id，固件与 APP catalog 必须成套发布，禁止单独降级。
