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
class ClockManager;

// ============================================================================
// PSRAM支持函数声明
// ============================================================================

/**
 * 检查PSRAM是否可用
 * @return true if PSRAM is available, false otherwise
 */
bool isPSRAMAvailable();

/**
 * 获取PSRAM大小
 * @return PSRAM size in bytes, 0 if not available
 */
size_t getPSRAMSize();

/**
 * 在PSRAM中分配内存
 * @param size 要分配的内存大小
 * @return 分配的内存指针，失败返回NULL
 */
void* psram_malloc(size_t size);

/**
 * 释放PSRAM内存
 * @param ptr 要释放的内存指针
 */
void psram_free(void* ptr);

/**
 * 检查指针是否在PSRAM地址范围内
 * @param ptr 要检查的指针
 * @return true if pointer is in PSRAM range, false otherwise
 */
bool isPSRAMPointer(void* ptr);

// ============================================================================
// 内存优化和清理函数声明
// ============================================================================

/**
 * 为GIF显示优化内存（激进清理，确保有足够内存）
 */
void optimizeMemoryForGIF();

/**
 * 激进的内存清理函数，专门用于GIF显示前
 */
void aggressiveMemoryCleanupForGIF();

/**
 * 内存碎片整理
 */
void defragmentMemory();

/**
 * 检查是否有足够内存显示GIF
 * @param requiredSize 需要的内存大小
 * @return true if enough memory available, false otherwise
 */
bool checkMemoryForGIF(size_t requiredSize);

// ============================================================================
// BLE回调类声明
// ============================================================================

/**
 * 通用控制特征值回调 
 */
class ControlCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    MatrixPanel_I2S_DMA* dma_display;
    bool* isScrollText;
    bool* isShowGIF;
    void (*setTextSize)(int);
    void (*setTextScrollSpeed)(int);
    void (*displayText)(char*, bool);
    void (*freeScrollText)();
    void (*clear)();
    void (*setLedBrightness)(int);
    void (*setRefreshRate)(int);
    void (*setClockMode)(bool);
    
    // 静态成员变量用于图像数据接收
    static uint8_t* dataBuffer;
    static int receivedBytes;
    static int expectedBytes;
    static int expectedChunks;
    static int receivedChunks;
    static bool isReceiving;
    static bool isHeaderReceived;
    static unsigned long lastReceiveTime;
    
public:
    ControlCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* scrollFlag, bool* gifFlag,
                                  void (*textSizeFunc)(int), void (*scrollSpeedFunc)(int),
                                  void (*displayFunc)(char*, bool), void (*freeTextFunc)(),
                                  void (*clearFunc)(), void (*brightnessFunc)(int),
                                  void (*refreshRateFunc)(int), void (*clockModeFunc)(bool));
    void onWrite(BLECharacteristic *pCharacteristic);
    
    static void checkTimeout();
    
    // 更新计时游戏显示
    void updateTimerGameDisplay();
    
private:
    void handleTextCommand(std::string value);
    void handleScrollTextCommand(std::string value);
    void handleImageCommand(uint8_t* data, int length);
    void handleImageCommand(std::string value);
    void handleBrightnessCommand(std::string value);
    void handleClockCommand(std::string value);
    void handleFillScreenCommand(std::string value);
    void handleFillPixelCommand(std::string value);
    void handleRefreshRateCommand(std::string value);
    void handleTimerGameCommand(std::string value);
    void handleTimerGameStart();
    void handleTimerGameTimerStart();
    void handleTimerGameTimerStop();
    void startTimerGameUpdate();
    
    void handleImageHeader(uint8_t* data, int length);
    void handleImageDataChunk(uint8_t* data, int length);
    void drawCompleteImage();
    
public:
    static void resetReceive();
};

/**
 * 亮度控制特征值回调
 */
class BrightnessCharacteristicCallbacks : public BLECharacteristicCallbacks {
private:
    void (*setLedBrightness)(int);
    
public:
    BrightnessCharacteristicCallbacks(void (*brightnessFunc)(int));
    void onRead(BLECharacteristic *pCharacteristic) override;
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
    //延迟重置时间
    static unsigned long gifResetDelayTime; 
    
public:
    GIFCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* scrollFlag, bool* gifFlag,
                              void (*freeTextFunc)(), AnimatedGIF* gifDecoder);
    void onWrite(BLECharacteristic *pCharacteristic);
    
    // 静态方法
    static void checkGIFTimeout();
    static void checkDelayedReset();
    static void cleanupOnStartup();
    static void cleanupAfterDisplay();
    //检查是否正在接收GIF数据
    static bool isReceivingGIF(); 
    
private:
    void handleGIFHeader(uint8_t* data, int length);
    void handleGIFDataChunk(uint8_t* data, int length);
    void prepareGIFForDisplay();
    void loadAndDisplayGIF();
    void handleImageDisplay();
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
    BLECharacteristic* pControlCharacteristic;
    BLECharacteristic* pBrightnessCharacteristic;
    BLECharacteristic* pDeviceInfoCharacteristic;
    
    // 回调函数指针
    void (*setTextSizeFunc)(int);
    void (*setTextScrollSpeedFunc)(int);
    void (*displayTextFunc)(char*, bool);
    void (*freeScrollTextFunc)();
    void (*clearFunc)();
    void (*setLedBrightnessFunc)(int);
    void (*setRefreshRateFunc)(int);
    void (*setClockModeFunc)(bool);
    int (*getCurrentBrightnessFunc)();
    
public:
    // 静态实例指针
    static BLEHandler* instance;
    
    // 时钟管理器指针
    ClockManager* clockManager;
    
    // 状态标志（公共访问）
    bool* isScrollText;
    bool* isShowGIF;
    
    // 控制回调实例指针（公共访问）
    ControlCharacteristicCallbacks* controlCallbacks;
    
    BLEHandler(MatrixPanel_I2S_DMA* display, AnimatedGIF* gifDecoder,
               void (*textSizeFunc)(int), void (*scrollSpeedFunc)(int),
               void (*displayFunc)(char*, bool), void (*freeTextFunc)(),
               void (*clearFunc)(), void (*brightnessFunc)(int),
               void (*refreshRateFunc)(int), void (*clockModeFunc)(bool),
               int (*getBrightnessFunc)(), bool* scrollFlag, bool* gifFlag,
               ClockManager* clockMgr = nullptr);
    
    void init();
    void startAdvertising();
    void stopAdvertising();
    void disconnectBLE();
    
    // 发送当前亮度值
    void sendCurrentBrightness(int brightness);
    
    // 发送当前亮度值（用于在回调中调用）
    static void sendCurrentBrightnessStatic(int brightness);
    
    // 获取当前亮度值
    int getCurrentBrightness();
    
    // 更新计时游戏显示
    void updateTimerGameDisplay();
    
private:
    void createCharacteristics();
    void setupCallbacks();
};

#endif // BLE_HANDLER_H 