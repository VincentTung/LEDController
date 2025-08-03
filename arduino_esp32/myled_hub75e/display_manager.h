#ifndef DISPLAY_MANAGER_H
#define DISPLAY_MANAGER_H

#include "config.h"
#include "utils.h"
#include "ESP32-VirtualMatrixPanel-I2S-DMA.h"
#include <AnimatedGIF.h>

// ============================================================================
// 显示管理器类
// ============================================================================
class DisplayManager {
private:
    MatrixPanel_I2S_DMA* dma_display;
    AnimatedGIF gif;
    
    // 显示状态
    DisplayState currentState;
    bool isShowGIF;
    bool isScrollText;
    
    // 文本滚动相关
    char* scrollTextContent;
    int scrollTextTimeDelay;
    int scrollXMove;
    int scrollTextXPosition;
    int scrollTextYPosition;
    int scrollTextSpeed;
    unsigned long isAnimationDue;
    int16_t xOne, yOne;
    uint16_t scrollTextWidth, scrollTextHeight;
    int textSize;
    bool isTextWrap;
    
    // GIF相关
    char* currentGifPath;
    File gifFile;
    int x_offset, y_offset;
    unsigned long start_tick;
    
    // 颜色定义
    uint16_t myBLACK;
    uint16_t myWHITE;
    uint16_t myRED;
    uint16_t myGREEN;
    uint16_t myBLUE;
    
    // 文件系统
    String gifDir;
    char filePath[256];
    
public:
    DisplayManager();
    ~DisplayManager();
    
    /**
     * 初始化显示
     * @return 是否成功
     */
    bool init();
    
    /**
     * 清理资源
     */
    void cleanup();
    
    /**
     * 设置亮度
     * @param value 亮度值 (0-255)
     */
    void setBrightness(int value);
    
    /**
     * 清屏
     */
    void clear();
    
    /**
     * 显示文本
     * @param textContent 文本内容
     * @param isScroll 是否滚动显示
     * @return 是否成功
     */
    bool displayText(const char* textContent, bool isScroll = false);
    
    /**
     * 设置文本大小
     * @param size 文本大小
     */
    void setTextSize(int size);
    
    /**
     * 设置文本滚动速度
     * @param speed 滚动速度 (1-3)
     */
    void setTextScrollSpeed(int speed);
    
    /**
     * 显示GIF
     * @param fileName GIF文件名
     * @return 是否成功
     */
    bool displayGIF(const char* fileName);
    
    /**
     * 停止GIF播放
     */
    void stopGIF();
    
    /**
     * 绘制位图
     * @param data 位图数据
     * @param width 宽度
     * @param height 高度
     * @param color 颜色
     * @return 是否成功
     */
    bool drawBitmap(const uint8_t* data, int width, int height, uint16_t color);
    
    /**
     * 填充像素
     * @param x X坐标
     * @param y Y坐标
     * @param color 颜色 (0=黑色, 1=白色)
     * @return 是否成功
     */
    bool fillPixel(int x, int y, int color);
    
    /**
     * 填充屏幕
     * @param isBlack 是否填充黑色
     * @return 是否成功
     */
    bool fillScreen(bool isBlack);
    
    /**
     * 更新显示（需要在主循环中调用）
     */
    void update();
    
    /**
     * 获取当前状态
     * @return 显示状态
     */
    DisplayState getCurrentState() const { return currentState; }
    
    /**
     * 设置当前状态
     * @param state 新的状态
     */
    void setCurrentState(DisplayState state) { currentState = state; }
    
    /**
     * 检查是否正在显示GIF
     * @return 是否显示GIF
     */
    bool isShowingGIF() const { return isShowGIF; }
    
    /**
     * 检查是否正在滚动文本
     * @return 是否滚动文本
     */
    bool isScrollingText() const { return isScrollText; }
    
    /**
     * 获取显示对象指针
     * @return 显示对象指针
     */
    MatrixPanel_I2S_DMA* getDisplay() const { return dma_display; }

private:
    /**
     * 初始化颜色
     */
    void initColors();
    
    /**
     * 释放滚动文本内存
     */
    void freeScrollText();
    
    /**
     * 释放GIF内存
     */
    void freeGifMemory();
    
    /**
     * 更新文本滚动
     */
    void updateTextScroll();
    
    /**
     * 更新GIF播放
     */
    void updateGifPlayback();
    
    /**
     * GIF绘制回调
     */
    static void GIFDraw(GIFDRAW* pDraw);
    
    /**
     * GIF文件打开回调
     */
    static void* GIFOpenFile(const char* fname, int32_t* pSize);
    
    /**
     * GIF文件关闭回调
     */
    static void GIFCloseFile(void* pHandle);
    
    /**
     * GIF文件读取回调
     */
    static int32_t GIFReadFile(GIFFILE* pFile, uint8_t* pBuf, int32_t iLen);
    
    /**
     * GIF文件定位回调
     */
    static int32_t GIFSeekFile(GIFFILE* pFile, int32_t iPosition);
    
    /**
     * 显示GIF文件
     * @param name 文件名
     */
    void showGIF(const char* name);
    
    /**
     * 列出目录内容（调试用）
     * @param dir 目录路径
     * @param levels 递归层级
     */
    void listDir(const char* dir, uint8_t levels = 0);
};

// 全局显示管理器实例
extern DisplayManager* g_displayManager;

#endif // DISPLAY_MANAGER_H 