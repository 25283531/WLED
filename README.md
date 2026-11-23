# Color Lamp 96mm Cloud — WLED 定制

| 目录 | 内容 |
|---|---|
| `firmware/` | WLED 固件（ESP32-C3），已按原理图适配：IO10 灯带、IO4 模拟麦克风音频律动、IO0/IO1/IO3/IO7 按键 |
| `app/` | WLED 手机 App（Android Compose + iOS SwiftUI，Kotlin Multiplatform） |
| `SCH_*.svg`, `DXF_*` | 电路原理图 |

## 固件
- 编译：`cd firmware && pio run -e color_lamp_c3 -t upload`
- 灯效去重：相似度 >80% 的 29 个灯效已合并，见 `firmware/docs_FX_DEDUP.md`。旧预设里的灯效 ID 会自动映射到保留的灯效。加 `-D WLED_KEEP_ALL_FX` 可恢复全部灯效。

## App
- 设置页重新整理为「通用 / 设备列表 / 关于」三组
- 新增语言选择：简体中文（默认）、English、Français、Deutsch
  - Android 13+ 使用系统分应用语言；Android 7–12 通过 Context 包装
  - iOS 通过 `environment(\.locale)` 即时切换，同时写入 AppleLanguages

## GitHub Actions 自动构建
| 工作流 | 触发 | 产物 (Actions → 对应运行 → Artifacts) |
|---|---|---|
| `Android APP` | 推送 `app/**` 或手动 | `android-apk`：debug APK（可直接安装）+ release APK |
| `iOS APP` | 推送 iOS/shared 或手动 | `ios-unsigned-ipa`：未签名 IPA（需自签/侧载） |
| `Firmware` | 推送 `firmware/**` 或手动 | `firmware-color_lamp_c3`：OTA bin 等 |

打 tag `app-v1.0.0` / `fw-v1.0.0` 会自动创建 GitHub Release 并附带安装包。
Android 正式签名：在仓库 Settings → Secrets 添加 `ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。
