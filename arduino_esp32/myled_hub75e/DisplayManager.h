#ifndef DISPLAY_MANAGER_H
#define DISPLAY_MANAGER_H

#include "config.h"
#include "debug.h"
#include "ESP32-HUB75-MatrixPanel-I2S-DMA.h"

class DisplayManager {
private:
    MatrixPanel_I2S_DMA* dma_display;
    int currentBrightness;
    
public:
    DisplayManager();
    ~DisplayManager();
    
    // 初始化
    bool initLED();
    void initColors();
    
    // 显示控制
    void clear();
    void setLedBrightness(int value);
    void setRefreshRate(int refreshRate);
    void setTextSize(int size);
    void setTextColor(uint16_t color);
    void setTextWrap(bool wrap);
    
    // 显示功能
    void displayText(char *textContent, bool isScroll);
    void displayGIF(char *fileName);
    
    // 状态查询
    int getCurrentBrightness() const { return currentBrightness; }
    int getCurrentRefreshRate() const;
    MatrixPanel_I2S_DMA* getDisplay() const { return dma_display; }
    
    // 文件系统相关
    void listDir(const char *dir, uint8_t levels);
    
private:
    // 内部辅助函数
    void printInfo(const char* function, const char* message);
    void printError(const char* function, const char* message);
};

#endif // DISPLAY_MANAGER_H