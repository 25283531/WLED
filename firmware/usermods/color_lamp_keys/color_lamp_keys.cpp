// color-lamp-96mm-cloud: 亮度+/亮度- 按键处理 (按钮索引 2 = IO3, 3 = IO7)
// 按钮0(IO0 KEY_POWER): WLED原生 短按开关 / 长按随机色
// 按钮1(IO1 KEY_MODE) : WLED原生 短按下一个效果 / 长按调亮度
#include "wled.h"

#define CLK_STEP     16     // 单击步进
#define CLK_REPEAT   120    // 长按连续调节间隔(ms)
#define CLK_HOLD     400    // 进入连续调节前的延时(ms)

class ColorLampKeys : public Usermod {
  unsigned long tPress[2] = {0,0}, tRep[2] = {0,0};
  bool wasDown[2] = {false,false};

  void step(int dir) {
    int nb = (int)bri + dir * CLK_STEP;
    if (nb > 255) nb = 255;
    if (nb < 1)   nb = 1;
    if (bri == 0 && dir > 0) nb = CLK_STEP;   // 关灯状态下按亮度+ 直接开灯
    if (nb == bri) return;
    bri = nb;
    stateUpdated(CALL_MODE_BUTTON);
  }
 public:
  void setup() override {}
  void loop() override {}
  bool handleButton(uint8_t b) override {
    if (b != 2 && b != 3) return false;          // 其余按钮交给WLED
    uint8_t i = b - 2;
    int dir = (b == 2) ? +1 : -1;
    bool down = isButtonPressed(b);
    unsigned long now = millis();
    if (down && !wasDown[i]) { tPress[i] = now; tRep[i] = now; step(dir); }
    else if (down && now - tPress[i] > CLK_HOLD && now - tRep[i] > CLK_REPEAT) { tRep[i] = now; step(dir); }
    wasDown[i] = down;
    return true;
  }
};

static ColorLampKeys color_lamp_keys;
REGISTER_USERMOD(color_lamp_keys);
