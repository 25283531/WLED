# 固件环境对照与构建/烧录注意（v1.6）

## 结论

- 主线平台：**立创实战派 ESP32-S3**（WROOM-1-N16R8：16 MB Flash / 8 MB OPI PSRAM），实机测试就在这上面做。
- **`firmware/esp32lichuang/` 与 env `esp32lichuang` 保留且为必需**：当前产品原型开发用的就是它，CI 必须构建。
- 布局：**9 列 × 23 颗 = 207**；S 形走线；1 号位在第 1 列底部；**第 1 列在最左**（`DEFAULT_MATRIX_RIGHT_START=0`）。
- 空位：**第 46 颗（第 2 列底部）已物理拆除、数据线短接**，实际 206 颗、整链前移 1 格；用 `/ledmap.json` 修正，
  该逻辑位置（`(1,22)`，下标 199）不分配物理灯；APP 同格留空。**不要**把 `count` 改成 206。
- 外观：实物为 φ50 mm 圆柱，9 列贴外表面，正视只见**第 2–8 列**；设备页只取 **7 列 × 23 行** 发光区，逻辑矩阵仍 9×23。
- 麦克风：原理图定稿为**双路 ZTS6216 模拟麦 + ES7210 四通道 ADC**（「数字硅麦」表述已作废）：
  I²C `0x41`（SCL=IO2、SDA=IO1）；I²S BCK=IO14、WS=IO13、MCLK=IO38、DI=IO12。
  ES8311 是 DAC（I²C `0x18`，DO=IO45）。ES7210 I²C 初始化已内置在 usermod（`AR_ES7210`，`dmType=4`，MCLK/LRCK=512）。
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
| 麦克风 | `SR_DMTYPE=4`、`AR_ES7210=1`、`ES7210_ADDR=0x41`、`ES7210_SDA=1`、`ES7210_SCL=2`、`I2S_SDPIN=12`、`I2S_CKPIN=14`、`I2S_WSPIN=13`、`MCLK_PIN=38`（I²C 初始化已内置） |
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
| 音频 | `um.AudioReactive = {enabled:true, digitalmic:{type:4, pin:[12,13,14,38]}}`；`info.u` → `Audio Source=["I2S digital"," - quiet"]`、`Sound Processing=["running"]` | v1.6 OTA 后初始化通过；v1.7 播放音乐实测 `peak 53–97%`（10 次 9 有声），`GEQ(139) sx=64` 功率 452–1999 mA 随节拍变化，真拾音与律动均通过 |
| 第 46 颗 | 物理拆除 + 数据线短接（实际 206 颗，链前移 1 格） | `/ledmap.json` 207 项映射修正，该位置指向死包 206；APP 留空 |

> **重要（历史根因）**：刷机初期 `count` 仍为 346 / 15×23，是设备残留旧 `/ledmap.json`（width=15、height=23、346 项）
> 覆盖了 `cfg.hw.led.matrix`。当时必须 `GET /edit?func=delete&path=/ledmap.json` 删除后重启，布局才生效；
> 旧文件已备份到 `work/backup/e80690a6c20c/ledmap.json`。
>
> **v1.6 起**：`/ledmap.json` 是**有意部署**的修正文件（`work/ledmap.json`，9×23、207 项、`map[199]=206`），
> **不要再删**；它存在时依然优先于 `cfg.hw.led.matrix`，所以今后调整面板/颗数必须先删它、改 cfg、再按新几何重写。
> 写入用 `POST /upload`（multipart，字段 `data`），写入后 `GET /ledmap.json` 回读比对，再 `GET /win&RB` 重启，
> 回读 `info.leds.count=207`、`matrix=9×23`。实测三锚点：`(0,22)` 白在第 1 列最底、`(1,22)` 不亮、`(2,22)` 紫在第 3 列最底。

## 构建（CI-only）

1. 工作流 `.github/workflows/firmware.yml` 增加 `esp32lichuang` 目标：`pio run -e esp32lichuang`。
2. 产物：artifact `firmware-esp32lichuang`（`firmware.bin`、`bootloader.bin`、`partitions.bin`、`spiffs.bin`、`firmware.elf`）；
   OTA 单文件 `ColorLamp-S3-C01-ota.bin`；tag `fw-v*` 时附到 Release。
3. 校验：env 必须存在（`pio project config | grep 'env:esp32lichuang'`），产物非空。
4. 本地只读设备、跑脚本、写文档；不执行本地编译。

## 烧录

1. **OTA（首选）**
   `POST http://10.0.1.172/update`，`multipart/form-data`，字段 `update`，文件 `ColorLamp-S3-C01-ota.bin`。
   跨 release name 刷写（本地构建 vs 设备上的 release）会报 `Firmware release name mismatch`；
   `skipValidation` 必须作为**表单字段**（`curl -F "skipValidation=1" -F "update=@..."`），放 URL 查询串无效。
   刷后校验 `GET /json/info` 的 `ver`、`fxcount`（T5 后为 189）、`product=C01`。
   样例（v1.6 实测）：`curl.exe -F "skipValidation=1" -F "update=@ColorLamp-S3-C01-ota.bin" http://10.0.1.172/update` → `Update successful! Rebooting now...`。
2. **USB 救砖**
   `esptool.py --chip esp32s3 write_flash 0x0 <firmware.bin>`（按实际串口与分区偏移）。
3. **刷前备份**
   `GET /json/cfg`、`GET /json/presets` 存到 `work/backup/<mac>/`（仅留档）。
4. **刷后校正**
   写入 207 / 9×23 / pin 10；若设备上有**旧的** `/ledmap.json`（如 15×23/346 项）先删除（否则面板配置不生效），
   再上传 v1.6 修正文件 `work/ledmap.json`，重启校验 `info.leds.count=207`、`matrix=9×23`；最后验证字幕方向（`c3=16`）与 2D 效果方向。

## 风险

- `cfg` 与 `info` 的灯数/矩阵不一致时，优先怀疑残留 `ledmap.json`；只改 cfg 不会生效。
- 测试亮度上限：白光 ≤50%、彩色 ≤60%，避免超出灯珠供电能力。
- flash/PSRAM 参数错误会导致无法启动；必须保留 USB 恢复路径。
- OTA 分区不足会失败；4 MB 版需裁剪资源（与 T3 语言包、T6 预览资源体积相关）。
- 上游 WLED 无 ES7210 驱动，本项目已补 `ES7210Source`；寄存器配错会得到全零/爆音；I²C IO1/IO2 与按键 IO1 冲突已处理。
- T5 删除效果会改变 `fx` id，固件与 APP catalog 必须成套发布，禁止单独降级。
