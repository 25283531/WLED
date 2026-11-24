# 灯效目录差集（APP `EffectCatalog.kt` vs 设备 `/json.effects`）

- 设备效果数：220（`fxcount=220`）
- APP 目录：189
- 同 id 名称一致：187；名称不一致：0
- APP 有、设备无：2
- 设备有、APP 无：33（29 个真实旧效果 + 4 个 `RSVD` 占位）

## APP 有、设备无

| id | 名称 | 说明 |
|---|---|---|
| 220 | Analog Clock 2D | 设备固件早于该效果 |
| 221 | 极光之舞（`FX_MODE_LUMI_AURORA`） | 源码已有，设备固件未包含 → T9 刷机后可用 |

## 设备有、APP 无（真实效果 29 个）

| id | 名称 |
|---|---|
| 4 | Wipe Random |
| 11 | Scan Dual |
| 14 | Theater Rainbow |
| 16 | Saw |
| 19 | Dissolve Rnd |
| 22 | Sparkle+ |
| 24 | Strobe Rainbow |
| 26 | Blink Rainbow |
| 29 | Chase Random |
| 30 | Chase Rainbow |
| 36 | Sweep Random |
| 37 | Chase 2 |
| 47 | Loading |
| 52 | Running Dual |
| 60 | Scanner Dual |
| 63 | Pride 2015 |
| 71 | Noise 2 |
| 72 | Noise 3 |
| 81 | Twinklecat |
| 86 | Spots Fade |
| 93 | Sinelon Dual |
| 94 | Sinelon Rainbow |
| 99 | Ripple Rainbow |
| 109 | Phased Noise |
| 117 | Dynamic Smooth |
| 138 | Freqmatrix（音频类） |
| 144 | Puddlepeak |
| 157 | Gravcentric |
| 158 | Gravfreq |

## 占位槽（非真实效果，APP 不应出卡片）

| id | 名称 |
|---|---|
| 142 | RSVD |
| 169 | RSVD |
| 170 | RSVD |
| 171 | RSVD |

## 预览资源

- `app/app/src/main/assets/effect_previews/` 共 189 个 GIF，与 catalog 条目一一对应。
- 生成脚本 `tools/gen_effect_gifs.py` 头部注释：
  「这是**示意动画**，不是固件算法的逐像素复刻」→ 与实机不一致的直接原因。
