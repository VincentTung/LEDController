#ifndef TEXT_MANAGER_H
#define TEXT_MANAGER_H

#include "config.h"
#include "debug.h"
#include "ESP32-HUB75-MatrixPanel-I2S-DMA.h"

class TextManager {
private:
    MatrixPanel_I2S_DMA* dma_display;
    
    // 滚动文本相关变量
    int scrollTextTimeDelay;
    int scrollXMove;
    unsigned long isAnimationDue;
    int scrollTextXPosition;
    int scrollTextYPosition;
    int16_t xOne, yOne;
    uint16_t scrollTextWidth, scrollTextHeight;
    int textSize;
    bool isTextWrap;
    bool isScrollText;
    int scrollTextSpeed;
    
    // 滚动文本优化变量
    bool scrollTextNeedsRedraw;
    int lastScrollXPosition;
    unsigned long lastDrawTime;
    bool isDrawing;
    
    // 文本内容
    char* scrollTextContent;
    
    // 颜色定义
    uint16_t colorBlack;
    uint16_t colorWhite;
    uint16_t colorRed;
    uint16_t colorGreen;
    uint16_t colorBlue;
    
public:
    TextManager(MatrixPanel_I2S_DMA* display);
    ~TextManager();
    
    // 公共接口
    void displayText(char *textContent, bool isScroll);
    void freeScrollText();
    void setTextSize(int size);
    void setTextScrollSpeed(int speed);
    void updateScrollText();
    void clear();
    
    // 状态查询
    bool isScrollTextActive() const { return isScrollText; }
    int getTextSize() const { return textSize; }
    int getScrollSpeed() const { return scrollTextSpeed; }
    
    // 初始化颜色
    void initColors();
};

#endif // TEXT_MANAGER_H