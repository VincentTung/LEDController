#ifndef GIF_MANAGER_H
#define GIF_MANAGER_H

#include "config.h"
#include "debug.h"
#include <AnimatedGIF.h>
#include <LittleFS.h>
#include "ESP32-HUB75-MatrixPanel-I2S-DMA.h"

#define FILESYSTEM LittleFS

class GIFManager {
private:
    MatrixPanel_I2S_DMA* dma_display;
    AnimatedGIF* gif;
    File f;
    int x_offset, y_offset;
    
    // GIF播放控制变量
    bool gifInitialized;
    unsigned long lastGifFrameTime;
    int gifFrameDelay;
    bool gifLoopMode;
    unsigned long start_tick;
    
    // 静态回调函数
    static void GIFDraw(GIFDRAW *pDraw);
    static void* GIFOpenFile(const char *fname, int32_t *pSize);
    static void GIFCloseFile(void *pHandle);
    static int32_t GIFReadFile(GIFFILE *pFile, uint8_t *pBuf, int32_t iLen);
    static int32_t GIFSeekFile(GIFFILE *pFile, int32_t iPosition);
    
    // 静态显示对象指针（用于回调函数）
    static MatrixPanel_I2S_DMA* static_dma_display;
    
public:
    GIFManager(MatrixPanel_I2S_DMA* display, AnimatedGIF* gifDecoder);
    ~GIFManager();
    
    // 公共接口
    void showGIF(char *name);
    bool initGIFPlayer();
    bool playGIFFrame();
    void stopGIFPlayer();
    void cleanup();
    
    // 设置函数
    void setFrameDelay(int delay);
    void setLoopMode(bool loop);
    
    // 状态查询
    bool isInitialized() const { return gifInitialized; }
    bool isPlaying() const { return gifInitialized; }
};

#endif // GIF_MANAGER_H