# WLED web 中文化：现状证据

## 结论

`firmware/wled00/data/` 下**没有任何国际化实现**：无 `lang/` 目录、无语言 JSON、无 `i18n` 脚本；
所有页面都是 `<html lang="en">`，文案硬编码英文。

## 页面清单

```
index.htm            index.js / index.css
settings.htm         settings_2D.htm  settings_dmx.htm  settings_leds.htm
settings_pin.htm     settings_pininfo.htm  settings_sec.htm  settings_sync.htm
settings_time.htm    settings_ui.htm       settings_um.htm   settings_wifi.htm
update.htm           welcome.htm           msg.htm           404.htm
liveview.htm         liveviewws2D.htm      edit.htm
pixart/              pixelforge/           pxmagic/          cpal/  icons-ui/
```

## 极客模式入口

`app/app/src/main/java/ca/cgagnier/wlednativeandroid/ui/homeScreen/detail/DeviceDetail.kt:34`
注释：「通过标题栏的『网页设置』用系统浏览器打开，作为调试/极客入口。」

→ 使用**系统浏览器**，APP 无法注入 JS，因此中文必须在固件侧实现。

## WiFi 表单

`data/settings_wifi.htm`：

```html
if (loc) d.Sf.action = getURL('/settings/wifi');
<form id="form_s" name="Sf" method="post">
<button type="submit">Save & Connect</button>
```

即 WiFi 凭据只能通过 `/settings/wifi` 表单写入；`/json/cfg` 的 `wifi` 段只有 `sleep/phy/txpwr`。

## Improv

`firmware/wled00/improv.cpp`（8.5 KB）存在，说明固件具备 BLE Improv 配网能力，
可作为 APP 原生 WiFi 配网的首选通道。

## 建议覆盖顺序

1. `index.htm`（首页/灯效/调色板/亮度）
2. `settings.htm` + `settings_wifi.htm` + `settings_leds.htm` + `settings_2D.htm`
3. `update.htm`、`msg.htm`、`welcome.htm`
4. 其余设置页与 `pixart/pixelforge/pxmagic` 工具页
