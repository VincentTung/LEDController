#ifndef BLE_HANDLER_H
#define BLE_HANDLER_H

#include "config.h"
#include "debug.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// 前向声明
class MatrixPanel_I2S_DMA;
class AnimatedGIF;

// ============================================================================
// BLE回调类声明
// ============================================================================

/**
 * 静态文本特征值回调
 */
class TextCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    bool* isShowGIF;
    void (*setTextSize)(int);
    void (*displayText)(char*, bool);
    
public:
    TextCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* gifFlag, 
                               void (*textSizeFunc)(int), void (*displayFunc)(char*, bool));
    void onWrite(BLECharacteristic *pCharacteristic);
};

/**
 * 滚动文本特征值回调
 */
class TextScrollCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    bool* isShowGIF;
    void (*setTextSize)(int);
    void (*setTextScrollSpeed)(int);
    void (*displayText)(char*, bool);
    
public:
    TextScrollCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* gifFlag,
                                     void (*textSizeFunc)(int), void (*scrollSpeedFunc)(int),
                                     void (*displayFunc)(char*, bool));
    void onWrite(BLECharacteristic *pCharacteristic);
};

/**
 * 图像绘制特征值回调
 */
class DrawNormalCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    bool* isScrollText;
    bool* isShowGIF;
    void (*freeScrollText)();
    void (*clear)();
    
    // 静态成员变量用于数据接收
    static uint8_t* dataBuffer;
    static int receivedBytes;
    static int expectedBytes;
    static int expectedChunks;
    static int receivedChunks;
    static bool isReceiving;
    static bool isHeaderReceived;
    static unsigned long lastReceiveTime;
    
public:
        DrawNormalCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* scrollFlag,
                                    void (*freeTextFunc)(), void (*clearFunc)(), bool* gifFlag);
    void onWrite(BLECharacteristic *pCharacteristic);
    
    static void checkTimeout();
    
private:
    void handleHeader(uint8_t* data, int length);
    void handleDataChunk(uint8_t* data, int length);
    void drawCompleteImage();
    static void resetReceive();
};

/**
 * 单像素填充特征值回调
 */
class FillPixelCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    
public:
    FillPixelCharacteristicCallbacks(MatrixPanel_I2S_DMA* display);
    void onWrite(BLECharacteristic *pCharacteristic);
};

/**
 * 屏幕填充特征值回调
 */
class FillScreenCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    bool* isShowGIF;
    void (*clear)();
    
public:
    FillScreenCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, void (*clearFunc)(), bool* gifFlag);
    void onWrite(BLECharacteristic *pCharacteristic);
};

/**
 * 亮度控制特征值回调
 */
class BrightnessCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    void (*setLedBrightness)(int);
    
public:
    BrightnessCharacteristicCallbacks(void (*brightnessFunc)(int));
    void onWrite(BLECharacteristic *pCharacteristic);
};

/**
 * 刷新频率控制特征值回调
 */
class RefreshRateCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    void (*setRefreshRateFunc)(int);
    
public:
    RefreshRateCharacteristicCallbacks(void (*refreshRateFunc)(int));
    void onWrite(BLECharacteristic *pCharacteristic);
};

/**
 * GIF显示特征值回调
 */
class GIFCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    bool* isScrollText;
    bool* isShowGIF;
    void (*freeScrollText)();
    AnimatedGIF* gif;
    
    // 静态成员变量用于GIF数据接收
    static uint8_t* gifDataBuffer;
    static int gifReceivedBytes;
    static int gifExpectedBytes;
    static int gifExpectedChunks;
    static int gifReceivedChunks;
    static bool gifIsReceiving;
    static bool gifIsHeaderReceived;
    static unsigned long gifLastReceiveTime;
    //标记是否使用文件模式
    static bool gifUseFileMode; 
    
public:
    GIFCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* scrollFlag, bool* gifFlag,
                              void (*freeTextFunc)(), AnimatedGIF* gifDecoder);
    void onWrite(BLECharacteristic *pCharacteristic);
    
    // 静态方法
    static void checkGIFTimeout();
    static void cleanupOnStartup();
    static void cleanupAfterDisplay();
    //检查是否正在接收GIF数据
    static bool isReceivingGIF(); 
    
private:
    void handleGIFHeader(uint8_t* data, int length);
    void handleGIFDataChunk(uint8_t* data, int length);
    void prepareGIFForDisplay();
    void loadAndDisplayGIF();
    static void resetGIFReceive();
    static void resetGIFReceiveStateOnly();
};

/**
 * BLE服务器回调
 */
class MyBLEServerCallbacks : public BLEServerCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    void (*setTextSize)(int);
    void (*displayText)(char*, bool);
    
public:
    MyBLEServerCallbacks(MatrixPanel_I2S_DMA* display, void (*textSizeFunc)(int),
                        void (*displayFunc)(char*, bool));
    void onConnect(BLEServer* pServer);
    void onDisconnect(BLEServer* pServer);
};

// ============================================================================
// BLE处理类
// ============================================================================

class BLEHandler {
private:
    BLEServer* pServer;
    BLEService* pService;
    MatrixPanel_I2S_DMA* dma_display;
    AnimatedGIF* gif;
    
    // 特征值指针
    BLECharacteristic* pBrightnessCharacteristic;
    
    // 回调函数指针
    void (*setTextSizeFunc)(int);
    void (*setTextScrollSpeedFunc)(int);
    void (*displayTextFunc)(char*, bool);
    void (*freeScrollTextFunc)();
    void (*clearFunc)();
    void (*setLedBrightnessFunc)(int);
    void (*setRefreshRateFunc)(int);
    
    // 状态标志
    bool* isScrollText;
    bool* isShowGIF;
    
    // 静态实例指针，用于在回调中访问
    static BLEHandler* instance;
    
public:
    BLEHandler(MatrixPanel_I2S_DMA* display, AnimatedGIF* gifDecoder,
               void (*textSizeFunc)(int), void (*scrollSpeedFunc)(int),
               void (*displayFunc)(char*, bool), void (*freeTextFunc)(),
               void (*clearFunc)(), void (*brightnessFunc)(int),
               void (*refreshRateFunc)(int), bool* scrollFlag, bool* gifFlag);
    
    void init();
    void startAdvertising();
    void stopAdvertising();
    
    // 新增方法：发送当前亮度值
    void sendCurrentBrightness(int brightness);
    
    // 静态方法：发送当前亮度值（用于在回调中调用）
    static void sendCurrentBrightnessStatic(int brightness);
    
private:
    void createCharacteristics();
    void setupCallbacks();
};

#endif // BLE_HANDLER_H 