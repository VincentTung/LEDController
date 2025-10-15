#include "GIFManager.h"
MatrixPanel_I2S_DMA* GIFManager::static_dma_display = nullptr;

GIFManager::GIFManager(MatrixPanel_I2S_DMA* display, AnimatedGIF* gifDecoder) 
    : dma_display(display), gif(gifDecoder), f(), x_offset(0), y_offset(0),
      gifInitialized(false), lastGifFrameTime(0), gifFrameDelay(0), 
      gifLoopMode(true), start_tick(0) {
    static_dma_display = display;
}

GIFManager::~GIFManager() {
    cleanup();
}

void GIFManager::GIFDraw(GIFDRAW *pDraw) {
    uint8_t *s;
    uint16_t *d, *usPalette, usTemp[320];
    int x, y, iWidth;

    iWidth = pDraw->iWidth;
    if (iWidth > static_dma_display->width())
        iWidth = static_dma_display->width();

    usPalette = pDraw->pPalette;
    y = pDraw->iY + pDraw->y;  // current line
    static bool palettePrinted = false;
    if (!palettePrinted && usPalette != nullptr) {
        palettePrinted = true;
        printInfo("GIFDraw", "调色板颜色值:");
        for (int i = 0; i < min(8, 256); i++) {
            uint16_t color = usPalette[i];
            uint8_t r = (color >> 8) & 0xF8;
            uint8_t g = (color >> 3) & 0xFC;
            uint8_t b = (color << 3) & 0xF8;
            printInfo("GIFDraw", ("颜色" + String(i) + ": 0x" + String(color, HEX) + " -> R:" + String(r) + " G:" + String(g) + " B:" + String(b)).c_str());
        }
    }

    s = pDraw->pPixels;
    if (pDraw->ucDisposalMethod == 2)  // restore to background color
    {
        for (x = 0; x < iWidth; x++) {
            if (s[x] == pDraw->ucTransparent)
                s[x] = pDraw->ucBackground;
        }
        pDraw->ucHasTransparency = 0;
    }
    // Apply the new pixels to the main image
    if (pDraw->ucHasTransparency)  // if transparency used
    {
        uint8_t *pEnd, c, ucTransparent = pDraw->ucTransparent;
        int x, iCount;
        pEnd = s + pDraw->iWidth;
        x = 0;
        iCount = 0;  // count non-transparent pixels
        while (x < pDraw->iWidth) {
            c = ucTransparent - 1;
            d = usTemp;
            while (c != ucTransparent && s < pEnd) {
                c = *s++;
                if (c == ucTransparent)  // done, stop
                {
                    s--;  // back up to treat it like transparent
                } else  // opaque
                {
                    // 依据配置的颜色顺序进行通道映射
                    uint16_t color = usPalette[c];
                    uint8_t r = (color >> 8) & 0xF8;
                    uint8_t g = (color >> 3) & 0xFC;
                    uint8_t b = (color << 3) & 0xF8;
                    uint8_t outR, outG, outB;
                    #if (LED_COLOR_ORDER == COLOR_ORDER_RGB)
                        outR = r; outG = g; outB = b;
                    #elif (LED_COLOR_ORDER == COLOR_ORDER_RBG)
                        outR = r; outG = b; outB = g;
                    #elif (LED_COLOR_ORDER == COLOR_ORDER_GRB)
                        outR = g; outG = r; outB = b;
                    #elif (LED_COLOR_ORDER == COLOR_ORDER_GBR)
                        outR = g; outG = b; outB = r;
                    #elif (LED_COLOR_ORDER == COLOR_ORDER_BRG)
                        outR = b; outG = r; outB = g;
                    #elif (LED_COLOR_ORDER == COLOR_ORDER_BGR)
                        outR = b; outG = g; outB = r;
                    #else
                        outR = r; outG = b; outB = g; // 兼容当前默认行为（RBG）
                    #endif
                    uint16_t mapped565 = ((outR & 0xF8) << 8) | ((outG & 0xFC) << 3) | (outB >> 3);
                    *d++ = mapped565;
                    iCount++;
                }
            }            // while looking for opaque pixels
            if (iCount)  // any opaque pixels?
            {
                for (int xOffset = 0; xOffset < iCount; xOffset++) {
                    static_dma_display->drawPixel(x + xOffset, y, usTemp[xOffset]);  // 565 Color Format
                }
                x += iCount;
                iCount = 0;
            }
            // no, look for a run of transparent pixels
            c = ucTransparent;
            while (c == ucTransparent && s < pEnd) {
                c = *s++;
                if (c == ucTransparent)
                    iCount++;
                else
                    s--;
            }
            if (iCount) {
                x += iCount;  // skip these
                iCount = 0;
            }
        }
    } else  // does not have transparency
    {
        s = pDraw->pPixels;
        // Translate the 8-bit pixels through the RGB565 palette
        for (x = 0; x < pDraw->iWidth; x++) {
            // 依据配置的颜色顺序进行通道映射
            uint16_t color = usPalette[*s++];
            uint8_t r = (color >> 8) & 0xF8;
            uint8_t g = (color >> 3) & 0xFC;
            uint8_t b = (color << 3) & 0xF8;
            uint8_t outR, outG, outB;
            #if (LED_COLOR_ORDER == COLOR_ORDER_RGB)
                outR = r; outG = g; outB = b;
            #elif (LED_COLOR_ORDER == COLOR_ORDER_RBG)
                outR = r; outG = b; outB = g;
            #elif (LED_COLOR_ORDER == COLOR_ORDER_GRB)
                outR = g; outG = r; outB = b;
            #elif (LED_COLOR_ORDER == COLOR_ORDER_GBR)
                outR = g; outG = b; outB = r;
            #elif (LED_COLOR_ORDER == COLOR_ORDER_BRG)
                outR = b; outG = r; outB = g;
            #elif (LED_COLOR_ORDER == COLOR_ORDER_BGR)
                outR = b; outG = g; outB = r;
            #else
                outR = r; outG = b; outB = g; // 兼容当前默认行为（RBG）
            #endif
            uint16_t mapped565 = ((outR & 0xF8) << 8) | ((outG & 0xFC) << 3) | (outB >> 3);
            static_dma_display->drawPixel(x, y, mapped565);  // color 565
        }
    }
}

void* GIFManager::GIFOpenFile(const char *fname, int32_t *pSize) {
    printInfo("GIFOpenFile", ("尝试打开GIF文件: " + String(fname)).c_str());
    
    File *file = new File(FILESYSTEM.open(fname));
    if (*file) {
        *pSize = file->size();
        printInfo("GIFOpenFile", ("文件打开成功，大小: " + String(*pSize) + " 字节").c_str());
        return (void *)file;
    } else {
        printError("GIFOpenFile", ("文件打开失败: " + String(fname)).c_str());
        delete file;
        return NULL;
    }
}

void GIFManager::GIFCloseFile(void *pHandle) {
    File *file = static_cast<File *>(pHandle);
    if (file != NULL) {
        file->close();
        delete file;
    }
}

int32_t GIFManager::GIFReadFile(GIFFILE *pFile, uint8_t *pBuf, int32_t iLen) {
    int32_t iBytesRead;
    iBytesRead = iLen;
    File *file = static_cast<File *>(pFile->fHandle);
    // Note: If you read a file all the way to the last byte, seek() stops working
    if ((pFile->iSize - pFile->iPos) < iLen)
        iBytesRead = pFile->iSize - pFile->iPos - 1;  // <-- ugly work-around
    if (iBytesRead <= 0)
        return 0;
    iBytesRead = (int32_t)file->read(pBuf, iBytesRead);
    pFile->iPos = file->position();
    return iBytesRead;
}

int32_t GIFManager::GIFSeekFile(GIFFILE *pFile, int32_t iPosition) {
    int i = micros();
    File *file = static_cast<File *>(pFile->fHandle);
    file->seek(iPosition);
    pFile->iPos = (int32_t)file->position();
    i = micros() - i;
    return pFile->iPos;
}

void GIFManager::showGIF(char *name) {
    printInfo("ShowGIF", ("播放GIF: " + String(name)).c_str());
    start_tick = millis();

    // 检查可用内存
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    printInfo("ShowGIF", ("GIF显示前内存状态: 可用 " + String(freeHeap) + " 字节, 最小可用 " + String(minFreeHeap) + " 字节").c_str());
    
    // 如果可用内存太少，拒绝显示GIF
    if (freeHeap < GIF_SHOW_MIN_MEMORY) {
        printInfo("ShowGIF", ("内存不足，无法显示GIF。可用内存: " + String(freeHeap) + " 字节").c_str());
        return;
    }

    if (gif->open(name, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
        x_offset = (dma_display->width() - gif->getCanvasWidth()) / 2;
        if (x_offset < 0) x_offset = 0;
        y_offset = (dma_display->height() - gif->getCanvasHeight()) / 2;
        if (y_offset < 0) y_offset = 0;
        printInfo("ShowGIF", ("成功打开GIF; 画布尺寸 = " + String(gif->getCanvasWidth()) + " x " + String(gif->getCanvasHeight())).c_str());
        Serial.flush();
        
        // 添加帧计数和内存监控
        int frameCount = 0;
        unsigned long lastMemoryCheck = millis();
        
        // 修改为循环播放，当播放完一帧后重新开始
        while (gifInitialized) {
            // 播放一帧
            if (!gif->playFrame(true, NULL)) {
                // 如果播放完一帧，重新开始播放
                gif->close();
                if (!gif->open(name, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
                    printInfo("ShowGIF", ("无法重新打开GIF文件: " + String(name)).c_str());
                    break;
                }
                continue;
            }
            
            frameCount++;
            
            // 每10帧检查一次内存状态
            if (frameCount % 10 == 0) {
                size_t currentFreeHeap = ESP.getFreeHeap();
                if (currentFreeHeap < 15000) {  // 降低到15KB
                    printInfo("ShowGIF", ("内存不足警告: 当前可用 " + String(currentFreeHeap) + " 字节，停止GIF播放").c_str());
                    break;
                }
                lastMemoryCheck = millis();
            }
            
            // 检查是否超时
            if ((millis() - start_tick) > 8000) {  // we'll get bored after about 8 seconds of the same looping gif
                printInfo("ShowGIF", ("GIF播放超时，已播放 " + String(frameCount) + " 帧").c_str());
                break;
            }
            
            // 让系统有时间处理其他任务，但不强制延迟
            yield();
        }
        
        printInfo("ShowGIF", ("GIF播放结束，总共播放 " + String(frameCount) + " 帧").c_str());
        gif->close();
        
        // 显示结束后的内存状态
        size_t endFreeHeap = ESP.getFreeHeap();
        printInfo("ShowGIF", ("GIF显示后内存状态: 可用 " + String(endFreeHeap) + " 字节").c_str());
    } else {
        printInfo("ShowGIF", ("无法打开GIF文件: " + String(name)).c_str());
    }
}

bool GIFManager::initGIFPlayer() {
    if (!gifInitialized) {
        // 检查临时GIF文件是否存在
        if (!FILESYSTEM.exists(GIF_FILE)) {
            printError("initGIFPlayer", ("GIF文件不存在: " + String(GIF_FILE)).c_str());
            return false;
        }
        
        // 检查文件大小
        File file = FILESYSTEM.open(GIF_FILE, "r");
        if (file) {
            size_t fileSize = file.size();
            file.close();
            printInfo("initGIFPlayer", ("GIF文件存在，大小: " + String(fileSize) + " 字节").c_str());
        } else {
            printError("initGIFPlayer", "无法打开GIF文件进行大小检查");
            return false;
        }
        
        // 先清屏，避免显示残留（只在初始化时清屏）
        dma_display->fillScreen(0x0000);
        
        // 打开临时GIF文件
        if (!gif->open(GIF_FILE, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
            printError("initGIFPlayer", ("无法打开GIF文件: " + String(GIF_FILE)).c_str());
            return false;
        }
        
        // 设置居中显示
        x_offset = (dma_display->width() - gif->getCanvasWidth()) / 2;
        if (x_offset < 0) x_offset = 0;
        y_offset = (dma_display->height() - gif->getCanvasHeight()) / 2;
        if (y_offset < 0) y_offset = 0;
        
        gifInitialized = true;
        lastGifFrameTime = millis();
        printInfo("initGIFPlayer", ("GIF播放器初始化成功，尺寸: " + String(gif->getCanvasWidth()) + " x " + String(gif->getCanvasHeight())).c_str());
    }
    return true;
}

bool GIFManager::playGIFFrame() {
    if (!gifInitialized) {
        return false;
    }
    
    // 尝试获取GIF的原始帧延迟，如果没有则使用设置的延迟或默认值
    int frameDelay = 30; // 默认30ms（约33FPS）
    
    // 尝试获取GIF的原始帧延迟
    if (gifFrameDelay > 0) {
        frameDelay = gifFrameDelay; // 使用手动设置的延迟
    } else {
        // 尝试获取GIF的原始延迟（如果库支持的话）
        // 注意：AnimatedGIF库可能没有getDelay()方法，这里先注释掉
        // int originalDelay = gif->getDelay();
        // if (originalDelay > 0) {
        //     frameDelay = originalDelay;
        // }
        
    
    }
    
    // 检查是否到了播放下一帧的时间
    if (millis() - lastGifFrameTime >= frameDelay) {
        // 播放一帧
        if (!gif->playFrame(true, NULL)) {
            // 播放完整个GIF，重新开始
            if (gifLoopMode) {
                gif->close();
                // 重新开始前不清屏，避免闪烁
                // dma_display->fillScreen(0x0000);  // 注释掉清屏操作
                if (!gif->open(GIF_FILE, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
                    DEBUG_PRINTLN("无法重新打开GIF文件");
                    gifInitialized = false;
                    return false;
                }
                DEBUG_PRINTLN("GIF重新开始播放");
            } else {
                // 不循环播放，停止
                gifInitialized = false;
                return false;
            }
        }
        lastGifFrameTime = millis();
    }
    return true;
}

void GIFManager::stopGIFPlayer() {
    if (gifInitialized) {
        gif->close();
        gifInitialized = false;
        // 停止播放时清屏，避免显示残留
        dma_display->fillScreen(0x0000);
        DEBUG_PRINTLN("GIF播放器已停止");
    }
}

void GIFManager::cleanup() {
    stopGIFPlayer();
}

void GIFManager::setFrameDelay(int delay) {
    gifFrameDelay = delay;
    printInfo("setFrameDelay", ("设置GIF帧延迟: " + String(delay) + "ms").c_str());
}

void GIFManager::setLoopMode(bool loop) {
    gifLoopMode = loop;
}