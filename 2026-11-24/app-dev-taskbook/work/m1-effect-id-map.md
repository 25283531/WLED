# M1（方案 B）效果表删除与 id 对照

- 固件当前情况：`MODE_COUNT=222`（0–221），其中 **33 个 RSVD 空槽**，可用 **189** 个
- APP 目录：189
- 删除：33（真实效果 29 + RSVD 占位 4）
- 删除后：**189**，新 `MODE_COUNT = 189`

## 删除清单

| 旧 id | 名称 | 说明 |
|---|---|---|
| 4 | Wipe Random | 重复效果（现由 fxDedupAlias 折叠） |
| 11 | Scan Dual | 重复效果（现由 fxDedupAlias 折叠） |
| 14 | Theater Rainbow | 重复效果（现由 fxDedupAlias 折叠） |
| 16 | Saw | 重复效果（现由 fxDedupAlias 折叠） |
| 19 | Dissolve Rnd | 重复效果（现由 fxDedupAlias 折叠） |
| 22 | Sparkle+ | 重复效果（现由 fxDedupAlias 折叠） |
| 24 | Strobe Rainbow | 重复效果（现由 fxDedupAlias 折叠） |
| 26 | Blink Rainbow | 重复效果（现由 fxDedupAlias 折叠） |
| 29 | Chase Random | 重复效果（现由 fxDedupAlias 折叠） |
| 30 | Chase Rainbow | 重复效果（现由 fxDedupAlias 折叠） |
| 36 | Sweep Random | 重复效果（现由 fxDedupAlias 折叠） |
| 37 | Chase 2 | 重复效果（现由 fxDedupAlias 折叠） |
| 47 | Loading | 重复效果（现由 fxDedupAlias 折叠） |
| 52 | Running Dual | 重复效果（现由 fxDedupAlias 折叠） |
| 60 | Scanner Dual | 重复效果（现由 fxDedupAlias 折叠） |
| 63 | Pride 2015 | 重复效果（现由 fxDedupAlias 折叠） |
| 71 | Noise 2 | 重复效果（现由 fxDedupAlias 折叠） |
| 72 | Noise 3 | 重复效果（现由 fxDedupAlias 折叠） |
| 81 | Twinklecat | 重复效果（现由 fxDedupAlias 折叠） |
| 86 | Spots Fade | 重复效果（现由 fxDedupAlias 折叠） |
| 93 | Sinelon Dual | 重复效果（现由 fxDedupAlias 折叠） |
| 94 | Sinelon Rainbow | 重复效果（现由 fxDedupAlias 折叠） |
| 99 | Ripple Rainbow | 重复效果（现由 fxDedupAlias 折叠） |
| 109 | Phased Noise | 重复效果（现由 fxDedupAlias 折叠） |
| 117 | Dynamic Smooth | 重复效果（现由 fxDedupAlias 折叠） |
| 138 | Freqmatrix | 重复效果（现由 fxDedupAlias 折叠） |
| 142 | RSVD | 占位槽 RSVD |
| 144 | Puddlepeak | 重复效果（现由 fxDedupAlias 折叠） |
| 157 | Gravcentric | 重复效果（现由 fxDedupAlias 折叠） |
| 158 | Gravfreq | 重复效果（现由 fxDedupAlias 折叠） |
| 169 | RSVD | 占位槽 RSVD |
| 170 | RSVD | 占位槽 RSVD |
| 171 | RSVD | 占位槽 RSVD |

## 删除前后 id 对照（保留项）

| 旧 id | 名称 | 新 id | APP 是否需改 |
|---|---|---|---|
| 0 | Solid | 0 | 不变 |
| 1 | Blink | 1 | 不变 |
| 2 | Breathe | 2 | 不变 |
| 3 | Wipe | 3 | 不变 |
| 5 | Random Colors | 4 | 改 |
| 6 | Sweep | 5 | 改 |
| 7 | Dynamic | 6 | 改 |
| 8 | Colorloop | 7 | 改 |
| 9 | Rainbow | 8 | 改 |
| 10 | Scan | 9 | 改 |
| 12 | Fade | 10 | 改 |
| 13 | Theater | 11 | 改 |
| 15 | Running | 12 | 改 |
| 17 | Twinkle | 13 | 改 |
| 18 | Dissolve | 14 | 改 |
| 20 | Sparkle | 15 | 改 |
| 21 | Sparkle Dark | 16 | 改 |
| 23 | Strobe | 17 | 改 |
| 25 | Strobe Mega | 18 | 改 |
| 27 | Android | 19 | 改 |
| 28 | Chase | 20 | 改 |
| 31 | Chase Flash | 21 | 改 |
| 32 | Chase Flash Rnd | 22 | 改 |
| 33 | Rainbow Runner | 23 | 改 |
| 34 | Colorful | 24 | 改 |
| 35 | Traffic Light | 25 | 改 |
| 38 | Aurora | 26 | 改 |
| 39 | Stream | 27 | 改 |
| 40 | Scanner | 28 | 改 |
| 41 | Lighthouse | 29 | 改 |
| 42 | Fireworks | 30 | 改 |
| 43 | Rain | 31 | 改 |
| 44 | Tetrix | 32 | 改 |
| 45 | Fire Flicker | 33 | 改 |
| 46 | Gradient | 34 | 改 |
| 48 | Rolling Balls | 35 | 改 |
| 49 | Fairy | 36 | 改 |
| 50 | Two Dots | 37 | 改 |
| 51 | Fairytwinkle | 38 | 改 |
| 53 | Image | 39 | 改 |
| 54 | Chase 3 | 40 | 改 |
| 55 | Tri Wipe | 41 | 改 |
| 56 | Tri Fade | 42 | 改 |
| 57 | Lightning | 43 | 改 |
| 58 | ICU | 44 | 改 |
| 59 | Multi Comet | 45 | 改 |
| 61 | Stream 2 | 46 | 改 |
| 62 | Oscillate | 47 | 改 |
| 64 | Juggle | 48 | 改 |
| 65 | Palette | 49 | 改 |
| 66 | Fire 2012 | 50 | 改 |
| 67 | Colorwaves | 51 | 改 |
| 68 | Bpm | 52 | 改 |
| 69 | Fill Noise | 53 | 改 |
| 70 | Noise 1 | 54 | 改 |
| 73 | Noise 4 | 55 | 改 |
| 74 | Colortwinkles | 56 | 改 |
| 75 | Lake | 57 | 改 |
| 76 | Meteor | 58 | 改 |
| 77 | Copy Segment | 59 | 改 |
| 78 | Railway | 60 | 改 |
| 79 | Ripple | 61 | 改 |
| 80 | Twinklefox | 62 | 改 |
| 82 | Halloween Eyes | 63 | 改 |
| 83 | Solid Pattern | 64 | 改 |
| 84 | Solid Pattern Tri | 65 | 改 |
| 85 | Spots | 66 | 改 |
| 87 | Glitter | 67 | 改 |
| 88 | Candle | 68 | 改 |
| 89 | Fireworks Starburst | 69 | 改 |
| 90 | Fireworks 1D | 70 | 改 |
| 91 | Bouncing Balls | 71 | 改 |
| 92 | Sinelon | 72 | 改 |
| 95 | Popcorn | 73 | 改 |
| 96 | Drip | 74 | 改 |
| 97 | Plasma | 75 | 改 |
| 98 | Percent | 76 | 改 |
| 100 | Heartbeat | 77 | 改 |
| 101 | Pacifica | 78 | 改 |
| 102 | Candle Multi | 79 | 改 |
| 103 | Solid Glitter | 80 | 改 |
| 104 | Sunrise | 81 | 改 |
| 105 | Phased | 82 | 改 |
| 106 | Twinkleup | 83 | 改 |
| 107 | Noise Pal | 84 | 改 |
| 108 | Sine | 85 | 改 |
| 110 | Flow | 86 | 改 |
| 111 | Chunchun | 87 | 改 |
| 112 | Dancing Shadows | 88 | 改 |
| 113 | Washing Machine | 89 | 改 |
| 114 | Rotozoomer | 90 | 改 |
| 115 | Blends | 91 | 改 |
| 116 | TV Simulator | 92 | 改 |
| 118 | Spaceships | 93 | 改 |
| 119 | Crazy Bees | 94 | 改 |
| 120 | Ghost Rider | 95 | 改 |
| 121 | Blobs | 96 | 改 |
| 122 | Scrolling Text | 97 | 改 |
| 123 | Drift Rose | 98 | 改 |
| 124 | Distortion Waves | 99 | 改 |
| 125 | Soap | 100 | 改 |
| 126 | Octopus | 101 | 改 |
| 127 | Waving Cell | 102 | 改 |
| 128 | Pixels | 103 | 改 |
| 129 | Pixelwave | 104 | 改 |
| 130 | Juggles | 105 | 改 |
| 131 | Matripix | 106 | 改 |
| 132 | Gravimeter | 107 | 改 |
| 133 | Plasmoid | 108 | 改 |
| 134 | Puddles | 109 | 改 |
| 135 | Midnoise | 110 | 改 |
| 136 | Noisemeter | 111 | 改 |
| 137 | Freqwave | 112 | 改 |
| 139 | GEQ | 113 | 改 |
| 140 | Waterfall | 114 | 改 |
| 141 | Freqpixels | 115 | 改 |
| 143 | Noisefire | 116 | 改 |
| 145 | Noisemove | 117 | 改 |
| 146 | Noise2D | 118 | 改 |
| 147 | Perlin Move | 119 | 改 |
| 148 | Ripple Peak | 120 | 改 |
| 149 | Firenoise | 121 | 改 |
| 150 | Squared Swirl | 122 | 改 |
| 151 | PacMan | 123 | 改 |
| 152 | DNA | 124 | 改 |
| 153 | Matrix | 125 | 改 |
| 154 | Metaballs | 126 | 改 |
| 155 | Freqmap | 127 | 改 |
| 156 | Gravcenter | 128 | 改 |
| 159 | DJ Light | 129 | 改 |
| 160 | Funky Plank | 130 | 改 |
| 161 | Shimmer | 131 | 改 |
| 162 | Pulser | 132 | 改 |
| 163 | Blurz | 133 | 改 |
| 164 | Drift | 134 | 改 |
| 165 | Waverly | 135 | 改 |
| 166 | Sun Radiation | 136 | 改 |
| 167 | Colored Bursts | 137 | 改 |
| 168 | Julia | 138 | 改 |
| 172 | Game Of Life | 139 | 改 |
| 173 | Tartan | 140 | 改 |
| 174 | Polar Lights | 141 | 改 |
| 175 | Swirl | 142 | 改 |
| 176 | Lissajous | 143 | 改 |
| 177 | Frizzles | 144 | 改 |
| 178 | Plasma Ball | 145 | 改 |
| 179 | Flow Stripe | 146 | 改 |
| 180 | Hiphotic | 147 | 改 |
| 181 | Sindots | 148 | 改 |
| 182 | DNA Spiral | 149 | 改 |
| 183 | Black Hole | 150 | 改 |
| 184 | Wavesins | 151 | 改 |
| 185 | Rocktaves | 152 | 改 |
| 186 | Akemi | 153 | 改 |
| 187 | PS Volcano | 154 | 改 |
| 188 | PS Fire | 155 | 改 |
| 189 | PS Fireworks | 156 | 改 |
| 190 | PS Vortex | 157 | 改 |
| 191 | PS Fuzzy Noise | 158 | 改 |
| 192 | PS Ballpit | 159 | 改 |
| 193 | PS Box | 160 | 改 |
| 194 | PS Attractor | 161 | 改 |
| 195 | PS Impact | 162 | 改 |
| 196 | PS Waterfall | 163 | 改 |
| 197 | PS Spray | 164 | 改 |
| 198 | PS GEQ 2D | 165 | 改 |
| 199 | PS GEQ Nova | 166 | 改 |
| 200 | PS Ghost Rider | 167 | 改 |
| 201 | PS Blobs | 168 | 改 |
| 202 | PS DripDrop | 169 | 改 |
| 203 | PS Pinball | 170 | 改 |
| 204 | PS Dancing Shadows | 171 | 改 |
| 205 | PS Fireworks 1D | 172 | 改 |
| 206 | PS Sparkler | 173 | 改 |
| 207 | PS Hourglass | 174 | 改 |
| 208 | PS Spray 1D | 175 | 改 |
| 209 | PS 1D Balance | 176 | 改 |
| 210 | PS Chase | 177 | 改 |
| 211 | PS Starburst | 178 | 改 |
| 212 | PS GEQ 1D | 179 | 改 |
| 213 | PS Fire 1D | 180 | 改 |
| 214 | PS Sonic Stream | 181 | 改 |
| 215 | PS Sonic Boom | 182 | 改 |
| 216 | PS Springy | 183 | 改 |
| 217 | PS Galaxy | 184 | 改 |
| 218 | Color Clouds | 185 | 改 |
| 219 | Slow Transition | 186 | 改 |
| 220 | Analog Clock 2D | 187 | 改 |
| 221 | 极光之舞 | 188 | 改 |

## APP 目录需要改的 id（原 id → 新 id）

共 189 条，其中需要改 id 的 185 条：

```
0->0, 1->1, 2->2, 3->3, 5->4, 6->5, 7->6, 8->7, 9->8, 10->9, 12->10, 13->11, 15->12, 17->13, 18->14, 20->15, 21->16, 23->17, 25->18, 27->19, 28->20, 31->21, 32->22, 33->23, 34->24, 35->25, 38->26, 39->27, 40->28, 41->29, 42->30, 43->31, 44->32, 45->33, 46->34, 48->35, 49->36, 50->37, 51->38, 53->39, 54->40, 55->41, 56->42, 57->43, 58->44, 59->45, 61->46, 62->47, 64->48, 65->49, 66->50, 67->51, 68->52, 69->53, 70->54, 73->55, 74->56, 75->57, 76->58, 77->59, 78->60, 79->61, 80->62, 82->63, 83->64, 84->65, 85->66, 87->67, 88->68, 89->69, 90->70, 91->71, 92->72, 95->73, 96->74, 97->75, 98->76, 100->77, 101->78, 102->79, 103->80, 104->81, 105->82, 106->83, 107->84, 108->85, 110->86, 111->87, 112->88, 113->89, 114->90, 115->91, 116->92, 118->93, 119->94, 120->95, 121->96, 122->97, 123->98, 124->99, 125->100, 126->101, 127->102, 128->103, 129->104, 130->105, 131->106, 132->107, 133->108, 134->109, 135->110, 136->111, 137->112, 139->113, 140->114, 141->115, 143->116, 145->117, 146->118, 147->119, 148->120, 149->121, 150->122, 151->123, 152->124, 153->125, 154->126, 155->127, 156->128, 159->129, 160->130, 161->131, 162->132, 163->133, 164->134, 165->135, 166->136, 167->137, 168->138, 172->139, 173->140, 174->141, 175->142, 176->143, 177->144, 178->145, 179->146, 180->147, 181->148, 182->149, 183->150, 184->151, 185->152, 186->153, 187->154, 188->155, 189->156, 190->157, 191->158, 192->159, 193->160, 194->161, 195->162, 196->163, 197->164, 198->165, 199->166, 200->167, 201->168, 202->169, 203->170, 204->171, 205->172, 206->173, 207->174, 208->175, 209->176, 210->177, 211->178, 212->179, 213->180, 214->181, 215->182, 216->183, 217->184, 218->185, 219->186, 220->187, 221->188
```

## 校验

- 删除集与 APP 缺集完全一致：True
- APP 目录全部落在保留集内：True
- 保留项新 id 连续 0..188：True
