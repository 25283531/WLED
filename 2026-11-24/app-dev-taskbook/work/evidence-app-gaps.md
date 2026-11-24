# APP 原生能力缺口证据

## WiFi

- 只有信号强度：`app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/components/DeviceNetworkStrenghtImage.kt`
  （读取 `stateInfo?.info?.wifi?.rssi`）。
- 无扫描/连接/AP 模式引导页面（全仓无 `WifiConfig`/`WifiSettings` 页面）。
- 设备配网在 web：`firmware/wled00/data/settings_wifi.htm` POST 到 `/settings/wifi`。

## 开机默认状态

- `app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/settings/SettingsScreen.kt:101-110`
  只读 `info.leds.bootps` 并展示文案：
  - `bootps == 0` → 使用编译期 `bootfx`（本产品 221）
  - `bootps > 0` → 加载对应预设
- 实测 `cfg.def = {"ps":0,"on":true,"bri":128}`，APP 无写入入口。

## 固件升级

- 旧链路存在：
  - `ui/homeScreen/deviceEdit/DeviceEdit.kt:231` → `UpdateInstallingDialog`
  - `ui/homeScreen/update/UpdateInstallingViewModel.kt`
  - `di/AppModule.kt:148` 注入
- 新 Isle 界面（`ui/settings/SettingsScreen.kt`、`ui/homeScreen/detail/DeviceDetail.kt`）**没有升级入口**。
- 固件支持 `POST /update`（`data/update.htm` 同源接口）。

## 图片

- 创建页只有「色彩 / 像素」两个模式：`ui/create/CreateScreen.kt`
- 像素画板：16×10 → GIF → `/upload` → `Image` 效果：
  `ui/create/CreateViewModel.kt:262-284`
- 无相册选择、无图片缩放/量化、无动图。

## 音频

- `AndroidManifest.xml` 无 `RECORD_AUDIO`、无 `FOREGROUND_SERVICE_MICROPHONE`。
- 全仓无 `AudioRecord`、无 FFT、无 UDP 音频发送。
- 「律动」只是把 `EffectMood.MUSIC` 的效果作为普通效果下发。

## 字幕

- `ui/create/CreateViewModel.kt` 只有 `speed`；`custom2` 固定 255（font 4 + 加粗）、`custom3` 固定 0（会颠倒）。
- 无字体位置（`ix`）、字体高低（`c2`）控件；无中文字体资源。
