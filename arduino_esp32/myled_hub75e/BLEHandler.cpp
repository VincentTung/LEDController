#include "BLEHandler.h"
#include "ESP32-HUB75-MatrixPanel-I2S-DMA.h"
#include <AnimatedGIF.h>
#include <LittleFS.h>
#include <math.h>
#include <string.h>
#include <stdlib.h>
#include <string>
#include "esp_task_wdt.h"
#include "ClockManager.h"
#include "esp_heap_caps.h"

#define FILESYSTEM LittleFS

// ============================================================================
// PSRAM支持实现
// ============================================================================

bool isPSRAMAvailable() {
    #if ENABLE_PSRAM_SUPPORT
    // 尝试分配一小块内存来检测PSRAM是否可用
    void* test_ptr = heap_caps_malloc(1024, MALLOC_CAP_SPIRAM);
    if (test_ptr != NULL) {
        heap_caps_free(test_ptr);
        return true;
    }
    #endif
    return false;
}

size_t getPSRAMSize() {
    #if ENABLE_PSRAM_SUPPORT
    // 获取PSRAM总大小
    return heap_caps_get_total_size(MALLOC_CAP_SPIRAM);
    #endif
    return 0;
}

void* psram_malloc(size_t size) {
    #if ENABLE_PSRAM_SUPPORT
    // 尝试在PSRAM中分配，如果失败则回退到内部RAM
    void* ptr = heap_caps_malloc(size, MALLOC_CAP_SPIRAM);
    if (ptr != NULL) {
        return ptr;
    }
    #endif
    // 回退到内部RAM
    return malloc(size);
}

void psram_free(void* ptr) {
    if (ptr != NULL) {
        // 使用heap_caps_free，它会自动处理PSRAM和内部RAM的释放
        heap_caps_free(ptr);
    }
}

bool isPSRAMPointer(void* ptr) {
    #if ENABLE_PSRAM_SUPPORT
    if (psramFound() && ptr != NULL) {
        // 简化检查：如果PSRAM可用，假设通过psram_malloc分配的就是PSRAM指针
        // 在实际使用中，通过记录分配方式来跟踪
        return true; // 简化实现，实际项目中可以维护一个分配记录
    }
    #endif
    return false;
}

// ============================================================================
// 内存优化和清理函数实现
// ============================================================================

void optimizeMemoryForGIF() {
    printInfo("optimizeMemoryForGIF", "开始GIF内存优化");
    
    // 直接使用激进清理，因为GIF需要大量内存
    aggressiveMemoryCleanupForGIF();
    
    // 输出优化后的内存状态
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    size_t psramSize = getPSRAMSize();
    
    printInfo("optimizeMemoryForGIF", ("内存优化完成 - 内部RAM: " + String(freeHeap / 1024) + " KB, 最小: " + String(minFreeHeap / 1024) + " KB").c_str());
    if (psramSize > 0) {
        printInfo("optimizeMemoryForGIF", ("PSRAM: " + String(psramSize / 1024) + " KB").c_str());
    }
}

// 激进的内存清理函数，专门用于GIF显示前
void aggressiveMemoryCleanupForGIF() {
    printInfo("aggressiveMemoryCleanupForGIF", "执行激进内存清理（为GIF显示）");
    
    // 1. 清理可能存在的旧临时文件（但保留正在接收的文件）
    if (FILESYSTEM.exists("/temp.gif")) {
        // 检查文件是否正在被使用（通过检查文件大小是否为0或很小）
        File checkFile = FILESYSTEM.open("/temp.gif", "r");
        if (checkFile && checkFile.size() < 100) { // 小于100字节说明是旧文件
            checkFile.close();
            FILESYSTEM.remove("/temp.gif");
            printInfo("aggressiveMemoryCleanupForGIF", "清理旧的临时GIF文件");
        } else if (checkFile) {
            checkFile.close();
            printInfo("aggressiveMemoryCleanupForGIF", "保留正在使用的GIF文件");
        }
    }
    
    // 2. 停止可能正在运行的显示任务（仅在GIF显示前）
    // 停止滚动文本显示
    if (BLEHandler::instance && BLEHandler::instance->isScrollText) {
        *(BLEHandler::instance->isScrollText) = false;
        printInfo("aggressiveMemoryCleanupForGIF", "停止滚动文本显示");
    }
    
    // 停止时钟模式
    if (BLEHandler::instance && BLEHandler::instance->clockManager) {
        BLEHandler::instance->clockManager->setClockMode(false);
        printInfo("aggressiveMemoryCleanupForGIF", "停止时钟模式");
    }
    
    // 停止GIF显示
    if (BLEHandler::instance && BLEHandler::instance->isShowGIF) {
        *(BLEHandler::instance->isShowGIF) = false;
        printInfo("aggressiveMemoryCleanupForGIF", "停止GIF显示");
    }
    
    // 3. 清理BLE接收缓冲区
    if (BLEHandler::instance && BLEHandler::instance->controlCallbacks) {
        BLEHandler::instance->controlCallbacks->resetReceive();
        printInfo("aggressiveMemoryCleanupForGIF", "清理BLE接收缓冲区");
    }
    
    // 4. 强制内存整理
    for (int i = 0; i < 5; i++) {
        ESP.getFreeHeap(); // 触发内存整理
        yield(); // 让出CPU时间
        esp_task_wdt_reset(); // 喂狗
        delay(10);
    }
    
    printInfo("aggressiveMemoryCleanupForGIF", "激进内存清理完成");
}


void defragmentMemory() {
    printInfo("defragmentMemory", "开始内存碎片整理");
    
    size_t beforeFree = ESP.getFreeHeap();
    size_t beforeMinFree = ESP.getMinFreeHeap();
    
    // 多次触发内存整理
    for (int i = 0; i < 5; i++) {
        ESP.getFreeHeap(); // 触发内存整理
        yield();
        esp_task_wdt_reset();
        delay(5);
    }
    
    size_t afterFree = ESP.getFreeHeap();
    size_t afterMinFree = ESP.getMinFreeHeap();
    
    printInfo("defragmentMemory", ("碎片整理完成 - 可用内存: " + String(beforeFree / 1024) + " -> " + String(afterFree / 1024) + " KB").c_str());
    printInfo("defragmentMemory", ("最小可用: " + String(beforeMinFree / 1024) + " -> " + String(afterMinFree / 1024) + " KB").c_str());
}

bool checkMemoryForGIF(size_t requiredSize) {
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    size_t psramSize = getPSRAMSize();
    
    // 计算实际可用内存（考虑碎片化）
    size_t availableMemory = minFreeHeap;
    if (psramSize > 0) {
        availableMemory += psramSize;
    }
    
    // 需要比文件大小多50%的缓冲内存
    size_t requiredMemory = requiredSize * 3 / 2;
    
    printInfo("checkMemoryForGIF", ("内存检查 - 需要: " + String(requiredMemory / 1024) + " KB, 可用: " + String(availableMemory / 1024) + " KB").c_str());
    printInfo("checkMemoryForGIF", ("内部RAM: " + String(freeHeap / 1024) + " KB, 最小: " + String(minFreeHeap / 1024) + " KB").c_str());
    
    if (psramSize > 0) {
        printInfo("checkMemoryForGIF", ("PSRAM: " + String(psramSize / 1024) + " KB").c_str());
    }
    
    return availableMemory >= requiredMemory;
}

// 前向声明
extern void displayGIF(char *fileName);

// 静态成员变量初始化
uint8_t* ControlCharacteristicCallbacks::dataBuffer = NULL;
int ControlCharacteristicCallbacks::receivedBytes = 0;
int ControlCharacteristicCallbacks::expectedBytes = 0;
int ControlCharacteristicCallbacks::expectedChunks = 0;
int ControlCharacteristicCallbacks::receivedChunks = 0;
bool ControlCharacteristicCallbacks::isReceiving = false;
bool ControlCharacteristicCallbacks::isHeaderReceived = false;
unsigned long ControlCharacteristicCallbacks::lastReceiveTime = 0;

// 全局静态变量，用于保存目标时间字符串
char savedTargetString[10] = "";


// GIF静态成员变量初始化
uint8_t* GIFCharacteristicCallbacks::gifDataBuffer = NULL;
int GIFCharacteristicCallbacks::gifReceivedBytes = 0;
int GIFCharacteristicCallbacks::gifExpectedBytes = 0;
int GIFCharacteristicCallbacks::gifExpectedChunks = 0;
int GIFCharacteristicCallbacks::gifReceivedChunks = 0;
bool GIFCharacteristicCallbacks::gifIsReceiving = false;
bool GIFCharacteristicCallbacks::gifIsHeaderReceived = false;
unsigned long GIFCharacteristicCallbacks::gifLastReceiveTime = 0;
bool GIFCharacteristicCallbacks::gifUseFileMode = false;
unsigned long GIFCharacteristicCallbacks::gifResetDelayTime = 0;

// BLEHandler静态实例指针初始化
BLEHandler* BLEHandler::instance = nullptr;

// ControlCharacteristicCallbacks 实现
ControlCharacteristicCallbacks::ControlCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* scrollFlag, bool* gifFlag,
                                                             void (*textSizeFunc)(int), void (*scrollSpeedFunc)(int),
                                                             void (*displayFunc)(char*, bool), void (*freeTextFunc)(),
                                                             void (*clearFunc)(), void (*brightnessFunc)(int),
                                                             void (*refreshRateFunc)(int), void (*clockModeFunc)(bool)) {
    dma_display = display;
    isScrollText = scrollFlag;
    isShowGIF = gifFlag;
    setTextSize = textSizeFunc;
    setTextScrollSpeed = scrollSpeedFunc;
    displayText = displayFunc;
    freeScrollText = freeTextFunc;
    clear = clearFunc;
    setLedBrightness = brightnessFunc;
    setClockMode = clockModeFunc;
    setRefreshRate = refreshRateFunc;
}

void ControlCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    uint8_t *data = pCharacteristic->getData();
    int dataLength = pCharacteristic->getLength();
    
    printBLEInfo("ControlCharacteristicCallbacks", ("数据长度=" + String(dataLength)).c_str());
    
    if (dataLength == 0) {
        DEBUG_PRINTLN("接收到空数据");
        return;
    }
    
    // 检查是否正在接收图像数据
    if (isReceiving || isHeaderReceived) {
        // 图像数据，使用原有的图像处理逻辑
        handleImageCommand(data, dataLength);
    } else {
        // 检查是否是图像数据头（以数字开头且包含逗号）
        std::string value = pCharacteristic->getValue();
        if (value.length() > 0 && isdigit(value[0]) && value.find(',') != std::string::npos) {
            // 图像数据头，开始接收图像数据
            handleImageCommand(data, dataLength);
        } else {
            // 文本命令，解析命令类型
            printBLEInfo("ControlCharacteristicCallbacks", value.c_str());
            
            // 解析命令类型（第一个字符）
            if (value.length() > 0) {
                char commandType = value[0];
                std::string commandData = value.substr(1);
                
                switch (commandType) {
                    case BLE_CMD_TEXT: // 静态文本
                        handleTextCommand(commandData);
                        break;
                    case BLE_CMD_SCROLL: // 滚动文本
                        handleScrollTextCommand(commandData);
                        break;
                    case BLE_CMD_BRIGHTNESS: // 亮度控制
                        handleBrightnessCommand(commandData);
                        break;
                    case BLE_CMD_FILL_SCREEN: // 全屏填充
                        handleFillScreenCommand(commandData);
                        break;
                    case BLE_CMD_FILL_PIXEL: // 单像素填充
                        handleFillPixelCommand(commandData);
                        break;
                    case BLE_CMD_REFRESH_RATE: // 刷新频率
                        handleRefreshRateCommand(commandData);
                        break;
                    case BLE_CMD_IMAGE: // 图片显示
                        handleImageCommand(commandData);
                        break;
                    case BLE_CMD_CLOCK: // 时钟显示
                        handleClockCommand(commandData);
                        break;
                    case BLE_CMD_TIMER_GAME: // 计时游戏命令
                        handleTimerGameCommand(commandData);
                        break;
                    default:
                        printInfo("ControlCharacteristicCallbacks", ("未知命令类型: " + String(commandType)).c_str());
                        break;
                }
            }
        }
    }
}

void ControlCharacteristicCallbacks::handleTextCommand(std::string value) {
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("显示文本，已清除GIF文件");
        }
    }
    
    // 禁用时钟模式
    setClockMode(false);
    
    // 复制字符串以避免strtok修改原始字符串
    char* valueCopy = strdup(value.c_str());
    if (valueCopy == NULL) {
        DEBUG_PRINTLN("内存分配失败");
        return;
    }
    
    char *token = strtok(valueCopy, ",");
    if (token != NULL) {
        setTextSize(atoi(token));
        token = strtok(NULL, ",");
        if (token != NULL) {
            displayText(token, false);
        }
    }
    
    free(valueCopy);
}

void ControlCharacteristicCallbacks::handleScrollTextCommand(std::string value) {
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("显示滚动文本，已清除GIF文件");
        }
    }
    
    // 禁用时钟模式
    setClockMode(false);
    
    // 复制字符串以避免strtok修改原始字符串
    char* valueCopy = strdup(value.c_str());
    if (valueCopy == NULL) {
        DEBUG_PRINTLN("内存分配失败");
        return;
    }
    
    char *token = strtok(valueCopy, ",");
    if (token != NULL) {
        setTextSize(atoi(token));
        token = strtok(NULL, ",");
        if (token != NULL) {
            setTextScrollSpeed(atoi(token));
            token = strtok(NULL, ",");
            if (token != NULL) {
                displayText(token, true);
            }
        }
    }
    
    free(valueCopy);
}

void ControlCharacteristicCallbacks::handleImageCommand(uint8_t* data, int length) {
    printImageInfo("handleImageCommand", ("接收到图像数据，长度: " + String(length)).c_str());
    
    if (isReceiving && (millis() - lastReceiveTime > RECEIVE_TIMEOUT)) {
        DEBUG_PRINTLN("接收超时，重置接收状态");
        resetReceive();
    }
    
    lastReceiveTime = millis();
    
    if (!isHeaderReceived) {
        handleImageHeader(data, length);
    } else {
        handleImageDataChunk(data, length);
    }
}

void ControlCharacteristicCallbacks::handleBrightnessCommand(std::string value) {
    printBLEInfo("handleBrightnessCommand", ("ble brightness recv:" + String(value.c_str())).c_str());
    
    int brightness = atoi(value.c_str());
    if (isValidBrightness(brightness)) {
        setLedBrightness(brightness);
    }
}

void ControlCharacteristicCallbacks::handleClockCommand(std::string value) {
    printBLEInfo("handleClockCommand", ("ble clock recv:" + String(value.c_str())).c_str());
    
    // 解析命令格式：C1,HH:MM:SS 或 C0
    int commaPos = value.find(',');
    bool enableClock = false;
    
    if (commaPos != std::string::npos) {
        // 包含时间信息：C1,HH:MM:SS
        std::string modeStr = value.substr(0, commaPos);
        std::string timeStr = value.substr(commaPos + 1);
        
        enableClock = (atoi(modeStr.c_str()) == 1);
        
        if (enableClock && !timeStr.empty()) {
            // 解析时间戳
            printInfo("handleClockCommand", ("解析时间戳字符串: '" + String(timeStr.c_str()) + "'").c_str());
            unsigned long timestamp = strtoul(timeStr.c_str(), NULL, 10);
            
            if (timestamp > 0) {
                printInfo("handleClockCommand", ("解析到时间戳: " + String(timestamp)).c_str());
                // 设置手机时间戳
                if (BLEHandler::instance) {
                    printInfo("handleClockCommand", "BLEHandler实例存在");
                    if (BLEHandler::instance->clockManager) {
                        printInfo("handleClockCommand", "ClockManager存在，开始设置时间戳");
                        BLEHandler::instance->clockManager->setTimestampFromPhone(timestamp);
                        printInfo("handleClockCommand", ("收到手机时间戳: " + String(timestamp)).c_str());
                    } else {
                        printError("handleClockCommand", "ClockManager为null");
                    }
                } else {
                    printError("handleClockCommand", "BLEHandler实例为null");
                }
            } else {
                printError("handleClockCommand", ("时间戳解析失败或无效: " + String(timestamp)).c_str());
            }
        }
    } else {
        // 只有模式：C1 或 C0
        enableClock = (atoi(value.c_str()) == 1);
    }
    
    // 停止其他显示模式
    if (enableClock) {
        *isScrollText = false;
        *isShowGIF = false;
        freeScrollText();
        
        // 清理GIF文件
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("时钟模式，已清除GIF文件");
        }
    }
    
    // 设置时钟模式（在时间设置之后）
    setClockMode(enableClock);
    
    printInfo("handleClockCommand", enableClock ? "启用时钟模式" : "禁用时钟模式");
}


void ControlCharacteristicCallbacks::handleFillScreenCommand(std::string value) {
    DEBUG_PRINTLN("FillScreenCommand_recev");
    
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("满屏操作，已清除GIF文件");
        }
    }
    
    int isClear = atoi(value.c_str());
    
    if (isClear) {
        clear();
    } else {
        dma_display->fillScreen(0xFFFF); // 白色
    }
}

void ControlCharacteristicCallbacks::handleFillPixelCommand(std::string value) {
    if (value.length() > 0) {
        char *token;
        int values[3];
        int count = 0;

        char* valueCopy = strdup(value.c_str());
        token = strtok(valueCopy, ",");

        while (token != NULL && count < 3) {
            values[count] = atoi(token);
            count++;
            token = strtok(NULL, ",");
        }

        if (count == 3) {
            if (values[2] == 0) {
                dma_display->writePixel(values[0], values[1], 0x0000); // 黑色
            } else {
                dma_display->writePixel(values[0], values[1], 0xFFFF); // 白色
            }
        }
        
        free(valueCopy);
    }
}

void ControlCharacteristicCallbacks::handleRefreshRateCommand(std::string value) {
    printBLEInfo("handleRefreshRateCommand", ("ble refresh rate recv:" + String(value.c_str())).c_str());
    
    int refreshRate = atoi(value.c_str());
    if (refreshRate >= 10 && refreshRate <= 200) {
        setRefreshRate(refreshRate);
    }
}

void ControlCharacteristicCallbacks::handleImageCommand(std::string value) {
    printBLEInfo("handleImageCommand", ("图片命令接收: " + String(value.c_str())).c_str());
    
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            printInfo("handleImageCommand", "图片命令，已清除GIF文件");
        }
    }
    
    // 停止滚动文本
    if (*isScrollText) {
        *isScrollText = false;
        freeScrollText();
    }
    
    // 禁用时钟模式
    setClockMode(false);
    
    // 清屏
    clear();
    
    // 这里可以添加图片显示逻辑
    // 目前只是清屏，后续可以添加图片解码和显示功能
    printInfo("handleImageCommand", "图片命令处理完成，已清屏");
}

void ControlCharacteristicCallbacks::handleImageHeader(uint8_t* data, int length) {
    char headerStr[HEADER_BUFFER_SIZE];
    if (length < sizeof(headerStr) - 1) {
        memcpy(headerStr, data, length);
        headerStr[length] = '\0';
    } else {
        DEBUG_PRINTLN("头信息过长，重置接收");
        resetReceive();
        return;
    }
    
    printImageInfo("handleImageHeader", ("接收到头信息: " + String(headerStr)).c_str());
    
    // 禁用时钟模式（涂鸦功能）
    setClockMode(false);
    
    // 复制字符串以避免strtok修改原始字符串
    char* headerCopy = strdup(headerStr);
    if (headerCopy == NULL) {
        DEBUG_PRINTLN("内存分配失败");
        resetReceive();
        return;
    }
    
    char* token = strtok(headerCopy, ",");
    if (token == NULL) {
        DEBUG_PRINTLN("头信息格式错误，重置接收");
        free(headerCopy);
        resetReceive();
        return;
    }
    
    expectedBytes = atoi(token);
    token = strtok(NULL, ",");
    if (token == NULL) {
        DEBUG_PRINTLN("头信息格式错误，重置接收");
        free(headerCopy);
        resetReceive();
        return;
    }
    
    expectedChunks = atoi(token);
    free(headerCopy);
    
    printImageInfo("handleImageHeader", ("解析头信息成功: 总大小=" + String(expectedBytes) + ", 分块数=" + String(expectedChunks)).c_str());
    
    if (!isValidDataSize(expectedBytes)) {
        DEBUG_PRINTLN("数据大小不合理，重置接收");
        resetReceive();
        return;
    }
    
    if (dataBuffer != NULL) {
        free(dataBuffer);
    }
    dataBuffer = (uint8_t*)malloc(expectedBytes);
    if (dataBuffer == NULL) {
        DEBUG_PRINTLN("内存分配失败，重置接收");
        resetReceive();
        return;
    }
    
    receivedBytes = 0;
    receivedChunks = 0;
    isReceiving = true;
    isHeaderReceived = true;
    
    DEBUG_PRINTLN("开始接收图像数据块");
}

void ControlCharacteristicCallbacks::handleImageDataChunk(uint8_t* data, int length) {
    if (!isReceiving || dataBuffer == NULL) {
        DEBUG_PRINTLN("接收状态错误，重置接收");
        resetReceive();
        return;
    }
    
    if (receivedBytes + length > expectedBytes) {
        DEBUG_PRINTLN("数据超出预期长度，重置接收");
        resetReceive();
        return;
    }
    
    memcpy(dataBuffer + receivedBytes, data, length);
    receivedBytes += length;
    receivedChunks++;
    
    printImageInfo("handleImageDataChunk", ("接收数据块 " + String(receivedChunks) + "/" + String(expectedChunks) + 
                       ", 累积字节: " + String(receivedBytes) + "/" + String(expectedBytes)).c_str());
    
    if (receivedBytes >= expectedBytes) {
        DEBUG_PRINTLN("图像数据接收完成，开始绘制");
        drawCompleteImage();
        resetReceive();
    }
}

void ControlCharacteristicCallbacks::drawCompleteImage() {
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("显示图像，已清除GIF文件");
        }
    }
    
    *isScrollText = false;
    delay(50);
    freeScrollText();
    clear();
    
    int imageSize = sqrt(expectedBytes * 8);
    if (imageSize * imageSize / 8 != expectedBytes) {
        imageSize = 64;
    }
    
    printImageInfo("drawCompleteImage", ("绘制图像，尺寸: " + String(imageSize) + "x" + String(imageSize)).c_str());
    dma_display->drawBitmap(0, 0, dataBuffer, imageSize, imageSize, 0xFFFF); // 使用白色
    DEBUG_PRINTLN("图像绘制完成");
}

void ControlCharacteristicCallbacks::checkTimeout() {
    if (isReceiving && (millis() - lastReceiveTime > RECEIVE_TIMEOUT)) {
        DEBUG_PRINTLN("主循环检测到接收超时，重置接收状态");
        resetReceive();
    }
}

void ControlCharacteristicCallbacks::resetReceive() {
    if (dataBuffer != NULL) {
        free(dataBuffer);
        dataBuffer = NULL;
    }
    receivedBytes = 0;
    expectedBytes = 0;
    expectedChunks = 0;
    receivedChunks = 0;
    isReceiving = false;
    isHeaderReceived = false;
    lastReceiveTime = 0;
}






// BrightnessCharacteristicCallbacks 实现
BrightnessCharacteristicCallbacks::BrightnessCharacteristicCallbacks(void (*brightnessFunc)(int)) {
    setLedBrightness = brightnessFunc;
}

void BrightnessCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    std::string value = pCharacteristic->getValue();
    printBLEInfo("BrightnessCharacteristicCallbacks", ("ble brightness recv:" + String(value.c_str())).c_str());
    
    int brightness = atoi(value.c_str());
    if (isValidBrightness(brightness)) {
        setLedBrightness(brightness);
        
        // 发送亮度值通知给客户端
        char brightnessStr[8];
        snprintf(brightnessStr, sizeof(brightnessStr), "%d", brightness);
        pCharacteristic->setValue((uint8_t*)brightnessStr, strlen(brightnessStr));
        pCharacteristic->notify();
        printBLEInfo("BrightnessCharacteristicCallbacks", ("ble brightness notify:" + String(brightnessStr)).c_str());
    }
}

// 在读取亮度特征时，返回设备信息与当前亮度，便于App一次读取
void BrightnessCharacteristicCallbacks::onRead(BLECharacteristic *pCharacteristic) {
    int currentBrightness = LED_DEFAULT_BRIGHTNAESS;
    if (BLEHandler::instance != nullptr) {
        currentBrightness = BLEHandler::instance->getCurrentBrightness();
    }
    String info = String("FW:") + FIRMWARE_VERSION + ",RES:" + String(PANEL_RES_X) + "x" + String(PANEL_RES_Y) + ",BR:" + String(currentBrightness);
    pCharacteristic->setValue((uint8_t*)info.c_str(), info.length());
    printBLEInfo("BrightnessCharacteristicCallbacks", (String("ble brightness onRead info:") + info).c_str());
}


// GIFCharacteristicCallbacks 实现
GIFCharacteristicCallbacks::GIFCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* scrollFlag, bool* gifFlag,
                                                     void (*freeTextFunc)(), AnimatedGIF* gifDecoder) {
    dma_display = display;
    isScrollText = scrollFlag;
    isShowGIF = gifFlag;
    freeScrollText = freeTextFunc;
    gif = gifDecoder;
}

void GIFCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    uint8_t *v = pCharacteristic->getData();
    int dataLength = pCharacteristic->getLength();
    
    printBLEInfo("GIFCharacteristicCallbacks", ("数据长度=" + String(dataLength)).c_str());
    
    // 更新最后接收时间
    gifLastReceiveTime = millis();
    
    // 检查数据包类型
    if (dataLength >= 2) {
        uint8_t packetType = v[0];
        uint8_t chunkIndex = v[1];
        
        printBLEInfo("GIFCharacteristicCallbacks", ("GIF数据包类型: " + String(packetType) + ", 块索引: " + String(chunkIndex)).c_str());
        
        // 打印前几个字节用于调试
        String hexData = "数据包前8字节: ";
        for (int i = 0; i < min(8, dataLength); i++) {
            hexData += String(v[i], HEX) + " ";
        }
        printBLEInfo("GIFCharacteristicCallbacks", hexData.c_str());
        
        if (packetType == 0x01) {  // 头信息包
            DEBUG_PRINTLN("收到头信息包");
            // 如果正在接收数据，先重置
            if (gifIsReceiving) {
                DEBUG_PRINTLN("收到新的头信息包，重置之前的接收状态");
                resetGIFReceive();
            }
            // 头信息包现在也是510字节，但只需要前4字节的文件大小信息
            handleGIFHeader(v + 2, 4);
        } else if (packetType == 0x02) {  // 数据包
            printInfo("GIFCharacteristicCallbacks", ("收到GIF数据包，块索引: " + String(chunkIndex) + 
                        ", 当前接收状态: gifIsReceiving=" + String(gifIsReceiving) + ", gifIsHeaderReceived=" + String(gifIsHeaderReceived)).c_str());
            
            // 检查是否正在接收数据
            if (gifIsReceiving && gifIsHeaderReceived) {
            // 检查超时 - 增加到60秒，给更多时间完成传输
            if (millis() - gifLastReceiveTime > 60000) {
                DEBUG_PRINTLN("GIF接收超时，重置接收状态");
                resetGIFReceive();
            } else {
                handleGIFDataChunk(v + 2, dataLength - 2);
            }
            } else {
                // 检查是否在延迟重置期间
                if (gifResetDelayTime > 0 && millis() < gifResetDelayTime) {
                    DEBUG_PRINTLN("收到数据包但正在延迟重置期间，忽略");
                } else {
                    DEBUG_PRINTLN("收到数据包但未在接收状态，忽略");
                    // 如果收到数据包但没有头信息，且不是刚完成传输的情况，才重置状态
                    if (!gifIsHeaderReceived && gifReceivedBytes == 0) {
                        DEBUG_PRINTLN("未收到头信息包且没有接收数据，重置接收状态");
                        resetGIFReceive();
                    } else {
                        DEBUG_PRINTLN("忽略数据包：可能是传输完成后的残留数据");
                    }
                }
            }
        } else {
            DEBUG_PRINTLN("未知的GIF数据包类型");
        }
    } else {
        DEBUG_PRINTLN("GIF数据包长度不足");
    }
}

// GIF数据处理方法实现
void GIFCharacteristicCallbacks::handleGIFHeader(uint8_t* data, int length) {
    printInfo("handleGIFHeader", ("处理头信息: 数据长度=" + String(length)).c_str());
    
    if (length < 4) {
        DEBUG_PRINTLN("头信息长度不足");
        return;
    }
    
    // 解析头信息：总字节数 (4字节)
    gifExpectedBytes = (data[0] << 24) | (data[1] << 16) | (data[2] << 8) | data[3];
    
    printBLEInfo("handleGIFHeader", ("头信息: 期望接收 " + String(gifExpectedBytes) + " 字节").c_str());
    printInfo("handleGIFHeader", ("头信息字节: " + String(data[0], HEX) + " " + String(data[1], HEX) + " " + String(data[2], HEX) + " " + String(data[3], HEX)).c_str());
    
    // 检查GIF文件大小是否合理
    if (gifExpectedBytes <= 0 || gifExpectedBytes > GIF_MAX_FILE_SIZE) {
        printInfo("handleGIFHeader", ("GIF文件大小不合理: " + String(gifExpectedBytes) + " 字节 (最大1MB)").c_str());
        resetGIFReceive();
        return;
    }
    
    // 如果当前正在显示GIF，先停止显示
    if (*isShowGIF) {
        DEBUG_PRINTLN("检测到正在播放GIF，先停止播放");
        *isShowGIF = false;
        // 给一点时间让GIF播放停止，并喂狗
        for (int i = 0; i < 10; i++) {
            yield();
            esp_task_wdt_reset();
            delay(1);
        }
    }
    
    // 在接收GIF前进行激进内存清理
    printInfo("handleGIFHeader", "开始GIF接收前的激进内存清理");
    aggressiveMemoryCleanupForGIF();
    
    // 动态调整文件大小阈值，根据可用内存情况
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    size_t memoryThreshold = GIF_MEMORY_THRESHOLD_DEFAULT;  // 默认阈值
    
    // 检查PSRAM可用性
    bool psramAvailable = isPSRAMAvailable();
    size_t psramSize = getPSRAMSize();
    
    if (psramAvailable) {
        // 有PSRAM时使用更高的阈值
        memoryThreshold = GIF_MEMORY_THRESHOLD_PSRAM;
        printInfo("handleGIFHeader", ("PSRAM可用: " + String(psramSize / 1024) + " KB").c_str());
    } else {
        // 根据内部RAM动态调整阈值
        if (freeHeap > 200 * 1024) {  // 如果可用内存超过200KB
            memoryThreshold = GIF_MEMORY_THRESHOLD_HIGH;  // 提高阈值
        } else if (freeHeap < 100 * 1024) {  // 如果可用内存少于100KB
            memoryThreshold = GIF_MEMORY_THRESHOLD_LOW;   // 降低阈值
        }
    }
    
    printInfo("handleGIFHeader", ("动态内存阈值: " + String(memoryThreshold / 1024) + " KB, 可用内存: " + String(freeHeap / 1024) + " KB").c_str());
    
    // 对于大文件，直接写入文件系统，不占用大量内存
    if (gifExpectedBytes > memoryThreshold) {
        DEBUG_PRINTLN("大文件模式：直接写入文件系统");
        
        // 删除可能存在的旧文件（重新接收GIF时清除）
        if (FILESYSTEM.exists("/temp.gif")) {
            yield(); // 喂狗
            esp_task_wdt_reset();
            FILESYSTEM.remove("/temp.gif");
            yield(); // 喂狗
            esp_task_wdt_reset();
            DEBUG_PRINTLN("重新接收GIF，已清除旧文件");
        }
        
        // 创建文件
        File tempFile = FILESYSTEM.open("/temp.gif", "w");
        if (!tempFile) {
            DEBUG_PRINTLN("无法创建临时GIF文件");
            resetGIFReceive();
            return;
        }
        tempFile.close();
        
        // 不分配大缓冲区，使用小缓冲区进行流式处理
        gifDataBuffer = NULL;
        gifUseFileMode = true;  // 设置文件模式标志
    } else {
        // 小文件使用内存缓冲区
        DEBUG_PRINTLN("小文件模式：使用内存缓冲区");
        
        // 使用之前获取的内存信息，避免重复调用
        printInfo("handleGIFHeader", ("可用堆内存: " + String(freeHeap) + " 字节, 最小可用: " + String(minFreeHeap) + " 字节").c_str());
        
        // 更保守的内存检查：需要比文件大小多50%的可用内存
        size_t requiredMemory = gifExpectedBytes * GIF_MEMORY_MULTIPLIER;
        if (freeHeap < requiredMemory) {
            printInfo("handleGIFHeader", ("内存不足，需要 " + String(requiredMemory) + " 字节，可用 " + String(freeHeap) + " 字节").c_str());
            resetGIFReceive();
            return;
        }
        
        printInfo("handleGIFHeader", ("内存检查通过，需要 " + String(requiredMemory) + " 字节，可用 " + String(freeHeap) + " 字节").c_str());
        
        // 分配缓冲区
        if (gifDataBuffer != NULL) {
            psram_free(gifDataBuffer);
            gifDataBuffer = NULL;
        }
        
        // 优先尝试PSRAM分配
        if (psramAvailable) {
            gifDataBuffer = (uint8_t*)psram_malloc(gifExpectedBytes);
            if (gifDataBuffer != NULL) {
                printInfo("handleGIFHeader", "使用PSRAM分配GIF缓冲区成功");
            } else {
                printInfo("handleGIFHeader", "PSRAM分配失败，尝试内部RAM");
                gifDataBuffer = (uint8_t*)malloc(gifExpectedBytes);
            }
        } else {
            // 没有PSRAM时使用内部RAM
            gifDataBuffer = (uint8_t*)malloc(gifExpectedBytes);
        }
        
        if (gifDataBuffer == NULL) {
            DEBUG_PRINTLN("GIF缓冲区分配失败，尝试内存碎片整理");
            
            // 记录当前内存状态
            size_t currentFreeHeap = ESP.getFreeHeap();
            size_t currentMinFreeHeap = ESP.getMinFreeHeap();
            printInfo("handleGIFHeader", ("分配失败时内存状态: 可用 " + String(currentFreeHeap) + " 字节, 最小可用 " + String(currentMinFreeHeap) + " 字节").c_str());
            
            // 尝试内存碎片整理
            ESP.getFreeHeap(); // 触发内存整理
            
            // 再次尝试分配（优先PSRAM）
            if (psramAvailable) {
                gifDataBuffer = (uint8_t*)psram_malloc(gifExpectedBytes);
                if (gifDataBuffer == NULL) {
                    gifDataBuffer = (uint8_t*)malloc(gifExpectedBytes);
                }
            } else {
                gifDataBuffer = (uint8_t*)malloc(gifExpectedBytes);
            }
            
            if (gifDataBuffer == NULL) {
                DEBUG_PRINTLN("内存碎片整理后仍分配失败，切换到文件模式");
                
                // 记录整理后的内存状态
                currentFreeHeap = ESP.getFreeHeap();
                printInfo("handleGIFHeader", ("内存碎片整理后: 可用 " + String(currentFreeHeap) + " 字节").c_str());
                
                // 删除可能存在的旧文件（重新接收GIF时清除）
                if (FILESYSTEM.exists("/temp.gif")) {
                    yield(); // 喂狗
                    esp_task_wdt_reset();
                    FILESYSTEM.remove("/temp.gif");
                    yield(); // 喂狗
                    esp_task_wdt_reset();
                    DEBUG_PRINTLN("内存分配失败，切换到文件模式，已清除旧文件");
                }
                
                // 创建文件
                File tempFile = FILESYSTEM.open("/temp.gif", "w");
                if (!tempFile) {
                    DEBUG_PRINTLN("无法创建临时GIF文件");
                    resetGIFReceive();
                    return;
                }
                tempFile.close();
                
                // 切换到文件模式
                gifDataBuffer = NULL;
                gifUseFileMode = true;  // 明确设置为文件模式
            } else {
                DEBUG_PRINTLN("内存碎片整理后分配成功");
                gifUseFileMode = false;  // 确保设置为内存模式
            }
        } else {
            gifUseFileMode = false;  // 确保设置为内存模式
        }
    }
    
    // 根据实际模式设置标志
    printInfo("handleGIFHeader", ("GIF模式设置: 文件模式=" + String(gifUseFileMode ? "是" : "否")).c_str());
    
    // 计算期望的数据块数 - 使用动态MTU大小
    // App端发送的数据块大小是 MTU-2，即 512-2 = 510字节
    int chunkSize = 510;  // 与App端保持一致
    gifExpectedChunks = (gifExpectedBytes + chunkSize - 1) / chunkSize;
    gifReceivedChunks = 0;
    gifReceivedBytes = 0;
    gifIsReceiving = true;
    gifIsHeaderReceived = true;
    
    // 记录开始接收时的内存状态
    size_t startFreeHeap = ESP.getFreeHeap();
    printInfo("handleGIFHeader", ("GIF接收开始: 内存状态 " + String(startFreeHeap) + " 字节").c_str());
    
    printBLEInfo("handleGIFHeader", ("GIF开始接收: 期望 " + String(gifExpectedChunks) + " 个数据块").c_str());
    DEBUG_PRINTLN("GIF头信息处理完成，开始接收数据包");
}

void GIFCharacteristicCallbacks::handleGIFDataChunk(uint8_t* data, int length) {
    if (!gifIsReceiving || !gifIsHeaderReceived) {
        DEBUG_PRINTLN("GIF数据接收状态错误");
        return;
    }
    
    // 禁用时钟模式
    if (BLEHandler::instance && BLEHandler::instance->clockManager) {
        BLEHandler::instance->clockManager->setClockMode(false);
    }
    
    // 检查是否超出预期大小
    if (gifReceivedBytes + length > gifExpectedBytes) {
        DEBUG_PRINTLN("GIF数据超出预期大小");
        resetGIFReceive();
        return;
    }
    
    // 定期检查内存状态
    if (gifReceivedChunks % GIF_MEMORY_CHECK_INTERVAL == 0) {
        size_t currentFreeHeap = ESP.getFreeHeap();
        if (currentFreeHeap < 10000) {  // 如果可用内存少于10KB
            printInfo("handleGIFDataChunk", ("内存不足警告: 当前可用 " + String(currentFreeHeap) + " 字节").c_str());
        }
    }
    
    // 根据文件模式标志选择处理方式
        printInfo("handleGIFDataChunk", ("GIF数据块处理: 文件模式=" + String(gifUseFileMode ? "是" : "否") + ", 缓冲区=" + String(gifDataBuffer ? "已分配" : "未分配")).c_str());
    
    if (gifUseFileMode) {
        // 大文件：直接写入文件系统
        File tempFile = FILESYSTEM.open("/temp.gif", "a");
        if (!tempFile) {
            DEBUG_PRINTLN("无法打开临时GIF文件进行写入");
            resetGIFReceive();
            return;
        }
        
        yield(); // 喂狗
        esp_task_wdt_reset();
        size_t written = tempFile.write(data, length);
        yield(); // 喂狗
        esp_task_wdt_reset();
        tempFile.close();
        
        if (written != length) {
            DEBUG_PRINTLN("GIF文件写入失败");
            resetGIFReceive();
            return;
        }
    } else {
        // 小文件：使用内存缓冲区
        if (gifDataBuffer == NULL) {
            DEBUG_PRINTLN("GIF缓冲区为空");
            return;
        }
        
        // 复制数据到缓冲区
        yield(); // 喂狗
        esp_task_wdt_reset();
        memcpy(gifDataBuffer + gifReceivedBytes, data, length);
        yield(); // 喂狗
        esp_task_wdt_reset();
    }
    
    gifReceivedBytes += length;
    gifReceivedChunks++;
    
    printBLEInfo("handleGIFDataChunk", ("GIF数据块接收: " + String(gifReceivedChunks) + "/" + String(gifExpectedChunks) + 
                     ", 已接收 " + String(gifReceivedBytes) + "/" + String(gifExpectedBytes) + " 字节").c_str());
    
    // 定期输出详细进度
    if (gifReceivedChunks % GIF_PROGRESS_REPORT_INTERVAL == 0) {
        size_t currentFreeHeap = ESP.getFreeHeap();
        printInfo("handleGIFDataChunk", ("GIF接收进度: " + String(gifReceivedChunks) + "/" + String(gifExpectedChunks) + " 块 (" + String((gifReceivedChunks * 100) / gifExpectedChunks) + "%), 内存: " + String(currentFreeHeap) + " 字节").c_str());
    }
    
    // 检查是否接收完成 - 允许一定的容错范围
    if (gifReceivedBytes >= gifExpectedBytes || gifReceivedChunks >= gifExpectedChunks) {
        printBLEInfo("handleGIFDataChunk", ("GIF数据接收完成: " + String(gifReceivedBytes) + "/" + String(gifExpectedBytes) + " 字节, " + String(gifReceivedChunks) + "/" + String(gifExpectedChunks) + " 块").c_str());
        
        // 如果接收的字节数不足，但块数够了，可能是最后一块数据不完整
        if (gifReceivedBytes < gifExpectedBytes) {
            printInfo("handleGIFDataChunk", ("警告: 接收字节数不足，但块数已满。期望 " + String(gifExpectedBytes) + " 字节，实际 " + String(gifReceivedBytes) + " 字节").c_str());
        }
        
        // 记录接收完成时的内存状态
        size_t endFreeHeap = ESP.getFreeHeap();
        printInfo("handleGIFDataChunk", ("GIF接收完成: 内存状态 " + String(endFreeHeap) + " 字节").c_str());
        printInfo("handleGIFDataChunk", ("GIF接收统计: 总块数=" + String(gifReceivedChunks) + ", 总字节=" + String(gifReceivedBytes) + ", 平均每块=" + String(gifReceivedBytes / gifReceivedChunks) + " 字节").c_str());
        
        DEBUG_PRINTLN("=== GIF数据接收完成，准备显示 ===");
        
        // 异步处理GIF显示，不阻塞BLE接收
        prepareGIFForDisplay();
        
        // 设置延迟重置时间，给主循环时间处理显示
        gifResetDelayTime = millis() + 5000; // 5秒后重置状态
        gifLastReceiveTime = millis(); // 更新最后接收时间
        DEBUG_PRINTLN("GIF接收完成，将在5秒后重置状态");
    }
    
    // 检查接收超时 - 使用更长的超时时间
    if (gifIsReceiving && (millis() - gifLastReceiveTime > 60000)) {  // 60秒超时
        DEBUG_PRINTLN("GIF接收时间过长，重置状态");
        resetGIFReceive();
    }
}

// 辅助函数：重置GIF接收状态但不删除文件
void GIFCharacteristicCallbacks::resetGIFReceiveStateOnly() {
    gifReceivedBytes = 0;
    gifExpectedBytes = 0;
    gifExpectedChunks = 0;
    gifReceivedChunks = 0;
    gifIsReceiving = false;
    gifIsHeaderReceived = false;
    gifLastReceiveTime = 0;
}

void GIFCharacteristicCallbacks::prepareGIFForDisplay() {
    if (gifReceivedBytes <= 0) {
        DEBUG_PRINTLN("数据无效，无法显示");
        // 只重置状态，不删除文件，让主循环处理清理
        resetGIFReceiveStateOnly();
        return;
    }
    
    // 检测数据类型：检查是否为GIF文件
    bool isGifFile = false;
    
    if (gifUseFileMode) {
        // 文件模式：从文件中读取前6字节检查GIF头
        File tempFile = FILESYSTEM.open("/temp.gif", "r");
        if (tempFile && tempFile.size() >= 6) {
            uint8_t header[6];
            tempFile.read(header, 6);
            tempFile.close();
            
            // 检查GIF文件头 "GIF87a" 或 "GIF89a"
            if ((header[0] == 'G' && header[1] == 'I' && header[2] == 'F' &&
                 header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a')) {
                isGifFile = true;
                printInfo("prepareGIFForDisplay", "文件模式：检测到GIF文件");
            } else {
                printInfo("prepareGIFForDisplay", "文件模式：检测到普通图片文件");
                printInfo("prepareGIFForDisplay", ("文件头: " + String(header[0], HEX) + " " + String(header[1], HEX) + " " + String(header[2], HEX) + " " + String(header[3], HEX) + " " + String(header[4], HEX) + " " + String(header[5], HEX)).c_str());
            }
        } else {
            printInfo("prepareGIFForDisplay", "文件模式：无法读取文件头");
        }
    } else {
        // 内存模式：从缓冲区检查GIF头
        if (gifDataBuffer != nullptr && gifReceivedBytes >= 6) {
            // 检查GIF文件头 "GIF87a" 或 "GIF89a"
            if ((gifDataBuffer[0] == 'G' && gifDataBuffer[1] == 'I' && gifDataBuffer[2] == 'F' &&
                 gifDataBuffer[3] == '8' && (gifDataBuffer[4] == '7' || gifDataBuffer[4] == '9') && gifDataBuffer[5] == 'a')) {
                isGifFile = true;
                printInfo("prepareGIFForDisplay", "内存模式：检测到GIF文件");
            } else {
                printInfo("prepareGIFForDisplay", "内存模式：检测到普通图片文件");
            }
        }
    }
    
    if (!isGifFile) {
        // 处理普通图片
        handleImageDisplay();
        return;
    }
    
    // 检查可用内存
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    printInfo("prepareGIFForDisplay", ("GIF显示前内存检查: 可用 " + String(freeHeap) + " 字节, 最小可用 " + String(minFreeHeap) + " 字节").c_str());
    
    // 如果可用内存太少，直接拒绝显示（不进行清理，避免删除GIF文件）
    if (freeHeap < GIF_DISPLAY_MIN_MEMORY) {
        printInfo("prepareGIFForDisplay", ("内存不足，无法显示GIF。可用内存: " + String(freeHeap) + " 字节，需要: " + String(GIF_DISPLAY_MIN_MEMORY) + " 字节").c_str());
        // 只重置状态，不删除文件，让主循环处理清理
        resetGIFReceiveStateOnly();
        return;
    }
    
    // 根据文件模式标志检查文件
    if (gifUseFileMode) {
        // 大文件模式：文件已经在接收过程中写入
        if (!FILESYSTEM.exists("/temp.gif")) {
            DEBUG_PRINTLN("临时GIF文件不存在");
            // 只重置状态，不删除文件，让主循环处理清理
            resetGIFReceiveStateOnly();
            return;
        }
        
        // 检查文件大小
        File tempFile = FILESYSTEM.open("/temp.gif", "r");
        if (!tempFile) {
            DEBUG_PRINTLN("无法打开临时GIF文件进行读取");
            // 只重置状态，不删除文件，让主循环处理清理
            resetGIFReceiveStateOnly();
            return;
        }
        
        size_t fileSize = tempFile.size();
        tempFile.close();
        
        printInfo("prepareGIFForDisplay", ("文件模式：文件大小检查 - 期望 " + String(gifReceivedBytes) + " 字节, 实际 " + String(fileSize) + " 字节").c_str());
        
        if (fileSize != gifReceivedBytes) {
            printInfo("prepareGIFForDisplay", ("文件大小不匹配: 期望 " + String(gifReceivedBytes) + " 字节, 实际 " + String(fileSize) + " 字节").c_str());
            // 允许一定的容错范围（最后几个字节可能不完整）
            if (fileSize < gifReceivedBytes - 100) {  // 如果差异超过100字节，才认为失败
                printInfo("prepareGIFForDisplay", "文件大小差异过大，重置状态");
                resetGIFReceiveStateOnly();
                return;
            } else {
                printInfo("prepareGIFForDisplay", "文件大小差异在容错范围内，继续处理");
            }
        }
        
        printBLEInfo("prepareGIFForDisplay", ("大文件GIF已保存: " + String(fileSize) + " 字节").c_str());
    } else {
        // 小文件模式：需要将内存中的数据写入文件
        if (gifDataBuffer == NULL) {
            DEBUG_PRINTLN("GIF缓冲区为空");
            // 只重置状态，不删除文件，让主循环处理清理
            resetGIFReceiveStateOnly();
            return;
        }
        
        File tempFile = FILESYSTEM.open("/temp.gif", "w");
        if (!tempFile) {
            DEBUG_PRINTLN("无法创建临时GIF文件");
            // 只重置状态，不删除文件，让主循环处理清理
            gifReceivedBytes = 0;
            gifExpectedBytes = 0;
            gifExpectedChunks = 0;
            gifReceivedChunks = 0;
            gifIsReceiving = false;
            gifIsHeaderReceived = false;
            gifLastReceiveTime = 0;
            return;
        }
        
        yield(); // 喂狗
        esp_task_wdt_reset();
        size_t written = tempFile.write(gifDataBuffer, gifReceivedBytes);
        yield(); // 喂狗
        esp_task_wdt_reset();
        tempFile.close();
        
        if (written != gifReceivedBytes) {
            DEBUG_PRINTLN("GIF文件写入失败");
            // 只重置状态，不删除文件，让主循环处理清理
            gifReceivedBytes = 0;
            gifExpectedBytes = 0;
            gifExpectedChunks = 0;
            gifReceivedChunks = 0;
            gifIsReceiving = false;
            gifIsHeaderReceived = false;
            gifLastReceiveTime = 0;
            return;
        }
        
        printBLEInfo("prepareGIFForDisplay", ("小文件GIF已保存: " + String(written) + " 字节").c_str());
    }
    
    // 显示GIF前再次检查内存
    freeHeap = ESP.getFreeHeap();
    printInfo("prepareGIFForDisplay", ("GIF显示前最终内存检查: 可用 " + String(freeHeap) + " 字节").c_str());
    
    if (freeHeap < GIF_DISPLAY_FINAL_MIN_MEMORY) {
        printInfo("prepareGIFForDisplay", ("最终内存检查失败，取消GIF显示。可用内存: " + String(freeHeap) + " 字节").c_str());
        // 只重置状态，不删除文件，让主循环处理清理
        gifReceivedBytes = 0;
        gifExpectedBytes = 0;
        gifExpectedChunks = 0;
        gifReceivedChunks = 0;
        gifIsReceiving = false;
        gifIsHeaderReceived = false;
        gifLastReceiveTime = 0;
        return;
    }
    
    // 停止滚动文本
    *isScrollText = false;
    delay(50);
    freeScrollText();
    
    // 设置GIF显示标志，让主循环处理显示
    *isShowGIF = true;
    
    DEBUG_PRINTLN("GIF准备完成，等待主循环显示");
    printInfo("prepareGIFForDisplay", ("GIF文件大小: " + String(gifReceivedBytes) + " 字节").c_str());
    printInfo("prepareGIFForDisplay", ("当前可用内存: " + String(ESP.getFreeHeap()) + " 字节").c_str());
    printInfo("prepareGIFForDisplay", ("GIF显示标志已设置: isShowGIF=" + String(*isShowGIF)).c_str());
    
    // 验证文件确实存在
    if (FILESYSTEM.exists("/temp.gif")) {
        File verifyFile = FILESYSTEM.open("/temp.gif", "r");
        if (verifyFile) {
            size_t verifySize = verifyFile.size();
            verifyFile.close();
            printInfo("prepareGIFForDisplay", ("文件验证成功: /temp.gif 存在，大小 " + String(verifySize) + " 字节").c_str());
        } else {
            printError("prepareGIFForDisplay", "文件验证失败: 无法打开 /temp.gif");
        }
    } else {
        printError("prepareGIFForDisplay", "文件验证失败: /temp.gif 不存在");
    }
}

void GIFCharacteristicCallbacks::loadAndDisplayGIF() {
    // 这个方法现在只用于同步显示，保留以兼容性
    prepareGIFForDisplay();
    if (*isShowGIF) {
        displayGIF((char*)"/temp.gif");
        cleanupAfterDisplay();
    }
}

// 当GIF显示完成或停止时调用此函数清理资源
void GIFCharacteristicCallbacks::cleanupAfterDisplay() {
    // 删除临时文件（只在播放完成后删除）
    if (FILESYSTEM.exists("/temp.gif")) {
        FILESYSTEM.remove("/temp.gif");
        DEBUG_PRINTLN("GIF播放完成，已删除临时文件");
    }
    
    // 释放内存缓冲区
    if (gifDataBuffer != NULL) {
        psram_free(gifDataBuffer);
        gifDataBuffer = NULL;
        DEBUG_PRINTLN("GIF播放完成，已释放内存缓冲区");
    }
    
    // 重置状态变量
    gifReceivedBytes = 0;
    gifExpectedBytes = 0;
    gifExpectedChunks = 0;
    gifReceivedChunks = 0;
    gifIsReceiving = false;
    gifIsHeaderReceived = false;
    gifLastReceiveTime = 0;
    gifUseFileMode = false;
    
    DEBUG_PRINTLN("GIF播放完成，资源清理完毕");
}

void GIFCharacteristicCallbacks::resetGIFReceive() {
    // 释放内存缓冲区
    if (gifDataBuffer != NULL) {
        psram_free(gifDataBuffer);
        gifDataBuffer = NULL;
    }
    
    // 删除临时文件（只在接收错误时删除）
    if (FILESYSTEM.exists("/temp.gif")) {
        // 尝试删除文件，如果失败则记录但不强制
        if (!FILESYSTEM.remove("/temp.gif")) {
            DEBUG_PRINTLN("GIF接收错误，无法删除临时文件（可能正在使用中）");
        } else {
            DEBUG_PRINTLN("GIF接收错误，已删除临时文件");
        }
    }
    
    // 重置所有状态变量
    gifReceivedBytes = 0;
    gifExpectedBytes = 0;
    gifExpectedChunks = 0;
    gifReceivedChunks = 0;
    gifIsReceiving = false;
    gifIsHeaderReceived = false;
    gifLastReceiveTime = 0;
    gifUseFileMode = false;
    
    DEBUG_PRINTLN("GIF接收状态已重置，内存和文件已清理");
}

void GIFCharacteristicCallbacks::checkGIFTimeout() {
    if (gifIsReceiving && (millis() - gifLastReceiveTime > 60000)) {  // 60秒超时
        DEBUG_PRINTLN("GIF接收超时，重置状态");
        printInfo("checkGIFTimeout", ("超时详情: 已接收 " + String(gifReceivedChunks) + "/" + String(gifExpectedChunks) + " 块, " + String(gifReceivedBytes) + "/" + String(gifExpectedBytes) + " 字节").c_str());
        resetGIFReceive();
    }
}

void GIFCharacteristicCallbacks::checkDelayedReset() {
    if (gifResetDelayTime > 0 && millis() >= gifResetDelayTime) {
        DEBUG_PRINTLN("延迟重置时间到，重置GIF接收状态");
        // 只重置状态，不删除文件
        gifReceivedBytes = 0;
        gifExpectedBytes = 0;
        gifExpectedChunks = 0;
        gifReceivedChunks = 0;
        gifIsReceiving = false;
        gifIsHeaderReceived = false;
        gifLastReceiveTime = 0;
        gifResetDelayTime = 0;
        DEBUG_PRINTLN("GIF接收状态已延迟重置");
    }
}

bool GIFCharacteristicCallbacks::isReceivingGIF() {
    return gifIsReceiving;
}

// 系统启动时清理残留文件
void GIFCharacteristicCallbacks::cleanupOnStartup() {
    // 删除可能存在的临时GIF文件（启动时清理残留文件）
    if (FILESYSTEM.exists("/temp.gif")) {
        FILESYSTEM.remove("/temp.gif");
        DEBUG_PRINTLN("启动时清理：已删除残留的临时GIF文件");
    }
    
    // 确保状态变量被重置
    if (gifDataBuffer != NULL) {
        psram_free(gifDataBuffer);
        gifDataBuffer = NULL;
        DEBUG_PRINTLN("启动时清理：已释放残留的内存缓冲区");
    }
    
    // 重置所有状态
    gifReceivedBytes = 0;
    gifExpectedBytes = 0;
    gifExpectedChunks = 0;
    gifReceivedChunks = 0;
    gifIsReceiving = false;
    gifIsHeaderReceived = false;
    gifLastReceiveTime = 0;
    gifUseFileMode = false;
    
    // 内存优化：触发内存整理
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    printInfo("cleanupOnStartup", ("启动时内存状态: 可用 " + String(freeHeap) + " 字节, 最小可用 " + String(minFreeHeap) + " 字节").c_str());
    
    // 如果内存碎片化严重，尝试进行内存整理
    if (freeHeap - minFreeHeap > GIF_MEMORY_FRAGMENTATION_THRESHOLD) {
        DEBUG_PRINTLN("检测到内存碎片化，进行内存整理");
        ESP.getFreeHeap(); // 触发内存整理
        freeHeap = ESP.getFreeHeap();
        printInfo("cleanupOnStartup", ("内存整理后: 可用 " + String(freeHeap) + " 字节").c_str());
    }
    
    DEBUG_PRINTLN("GIF系统启动清理完成");
}

// MyBLEServerCallbacks 实现
MyBLEServerCallbacks::MyBLEServerCallbacks(MatrixPanel_I2S_DMA* display, void (*textSizeFunc)(int),
                                         void (*displayFunc)(char*, bool)) {
    dma_display = display;
    setTextSize = textSizeFunc;
    displayText = displayFunc;
}

void MyBLEServerCallbacks::onConnect(BLEServer *pServer) {
    DEBUG_PRINTLN("设备连接");
    
    // 延迟发送当前亮度值，确保连接稳定
    delay(100);
    
    // 获取当前亮度值并发送通知
    if (BLEHandler::instance != nullptr) {
        int brightnessToSend = BLEHandler::instance->getCurrentBrightness();
        BLEHandler::sendCurrentBrightnessStatic(brightnessToSend);
    }
}

void MyBLEServerCallbacks::onDisconnect(BLEServer *pServer) {
    setTextSize(DEFAULT_TEXT_SIZE);
    // 设置白色文本颜色，避免与游戏失败的红色混淆
    dma_display->setTextColor(dma_display->color565(255, 255, 255)); // 白色
    displayText((char*)LED_DEFAULT_TEXT, false);
    
    // 断开连接时清除GIF文件
    if (FILESYSTEM.exists("/temp.gif")) {
        FILESYSTEM.remove("/temp.gif");
        DEBUG_PRINTLN("设备断开连接，已清除GIF文件");
    }
    
    pServer->getAdvertising()->start();
    DEBUG_PRINTLN("设备断开连接，重新开始广播");
}

// BLEHandler 实现
BLEHandler::BLEHandler(MatrixPanel_I2S_DMA* display, AnimatedGIF* gifDecoder,
                     void (*textSizeFunc)(int), void (*scrollSpeedFunc)(int),
                     void (*displayFunc)(char*, bool), void (*freeTextFunc)(),
                     void (*clearFunc)(), void (*brightnessFunc)(int),
                     void (*refreshRateFunc)(int), void (*clockModeFunc)(bool),
                     int (*getBrightnessFunc)(), bool* scrollFlag, bool* gifFlag,
                     ClockManager* clockMgr) {
    dma_display = display;
    gif = gifDecoder;
    setTextSizeFunc = textSizeFunc;
    setTextScrollSpeedFunc = scrollSpeedFunc;
    displayTextFunc = displayFunc;
    freeScrollTextFunc = freeTextFunc;
    this->clearFunc = clearFunc;
    setLedBrightnessFunc = brightnessFunc;
    setRefreshRateFunc = refreshRateFunc;
    setClockModeFunc = clockModeFunc;
    getCurrentBrightnessFunc = getBrightnessFunc;
    isScrollText = scrollFlag;
    isShowGIF = gifFlag;
    clockManager = clockMgr;
    
    // 设置静态实例指针
    instance = this;
}

void BLEHandler::init() {
    DEBUG_PRINTLN("初始化BLE");
    
    // 内存优化：在BLE初始化前进行内存整理
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    printInfo("init", ("BLE初始化前内存状态: 可用 " + String(freeHeap) + " 字节, 最小可用 " + String(minFreeHeap) + " 字节").c_str());
    
    // 如果内存碎片化严重，进行内存整理
    if (freeHeap - minFreeHeap > GIF_BLE_FRAGMENTATION_THRESHOLD) {
        DEBUG_PRINTLN("检测到内存碎片化，进行内存整理");
        ESP.getFreeHeap(); // 触发内存整理
        freeHeap = ESP.getFreeHeap();
        printInfo("init", ("内存整理后: 可用 " + String(freeHeap) + " 字节").c_str());
    }

    BLEDevice::init(BLE_DEVICE_NAME);

    // 设置BLE MTU大小
    BLEDevice::setMTU(BLE_MTU_SIZE);
    printInfo("init", ("BLE MTU设置为" + String(BLE_MTU_SIZE) + "字节").c_str());
    
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyBLEServerCallbacks(dma_display, setTextSizeFunc, displayTextFunc));
    
    pService = pServer->createService(BLE_SERVICE_UUID);
    
    createCharacteristics();
    
    pService->start();
    
    DEBUG_PRINTLN("BLE初始化完成");
}

void BLEHandler::createCharacteristics() {
    DEBUG_PRINTLN("创建BLE特征值");
    
    // 通用控制特征值 - 合并了除GIF外的所有控制功能
    BLECharacteristic *pCharacControl = pService->createCharacteristic(
        BLE_CHARACTERISTIC_CONTROL_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    controlCallbacks = new ControlCharacteristicCallbacks(dma_display, isScrollText, isShowGIF,
                                                         setTextSizeFunc, setTextScrollSpeedFunc, displayTextFunc,
                                                         freeScrollTextFunc, clearFunc, setLedBrightnessFunc, setRefreshRateFunc, setClockModeFunc);
    pCharacControl->setCallbacks(controlCallbacks);
    pControlCharacteristic = pCharacControl; // 保存控制特征指针
    
    // 亮度特征 - 保留用于亮度通知
    BLECharacteristic *pCharacBrightness = pService->createCharacteristic(
        BLE_CHARACTERISTIC_BRIGHTNESS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacBrightness->setCallbacks(new BrightnessCharacteristicCallbacks(setLedBrightnessFunc));
    pBrightnessCharacteristic = pCharacBrightness; // 保存亮度特征指针

    // 设备信息特征 - 固件版本与分辨率（只读）
    BLECharacteristic *pCharacDeviceInfo = pService->createCharacteristic(
        BLE_CHARACTERISTIC_DEVICE_INFO_UUID,
        BLECharacteristic::PROPERTY_READ);
    String deviceInfo = String("FW:") + FIRMWARE_VERSION + ",RES:" + String(PANEL_RES_X) + "x" + String(PANEL_RES_Y);
    pCharacDeviceInfo->setValue((uint8_t*)deviceInfo.c_str(), deviceInfo.length());
    pDeviceInfoCharacteristic = pCharacDeviceInfo;
    
    // GIF特征值 - 单独保留
    BLECharacteristic *pCharacGIF = pService->createCharacteristic(
        BLE_CHARACTERISTIC_GIF_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacGIF->setCallbacks(new GIFCharacteristicCallbacks(dma_display, isScrollText, isShowGIF,
                                                           freeScrollTextFunc, gif));
    
    DEBUG_PRINTLN("BLE特征值创建完成 - 使用合并特征值");
}

void BLEHandler::startAdvertising() {
    DEBUG_PRINTLN("开始BLE广播");
    pServer->getAdvertising()->start();
}

void BLEHandler::stopAdvertising() {
    DEBUG_PRINTLN("停止BLE广播");
    pServer->getAdvertising()->stop();
}

void BLEHandler::disconnectBLE() {
    DEBUG_PRINTLN("断开BLE连接");
    if (pServer != nullptr) {
        // 停止广播
        pServer->getAdvertising()->stop();
        
        // 获取连接的客户端数量
        int connectedCount = pServer->getConnectedCount();
        printInfo("disconnectBLE", ("当前连接的客户端数量: " + String(connectedCount)).c_str());
        
        // 断开所有连接的客户端
        for (int i = 0; i < connectedCount; i++) {
            uint16_t connId = pServer->getConnId();
            if (connId != 0) {
                printInfo("disconnectBLE", ("断开连接ID: " + String(connId)).c_str());
                pServer->disconnect(connId);
            }
        }
        
        // 等待连接完全断开
        delay(1000);
        
        // 再次检查连接状态
        connectedCount = pServer->getConnectedCount();
        printInfo("disconnectBLE", ("断开后连接的客户端数量: " + String(connectedCount)).c_str());
        
        if (connectedCount == 0) {
            printInfo("disconnectBLE", "BLE连接已完全断开");
        } else {
            printError("disconnectBLE", "BLE连接断开失败，仍有客户端连接");
            printInfo("disconnectBLE", "将尝试在WiFi连接过程中继续断开");
        }
    }
}

void BLEHandler::sendCurrentBrightness(int brightness) {
    if (pBrightnessCharacteristic != nullptr) {
        char brightnessStr[8];
        snprintf(brightnessStr, sizeof(brightnessStr), "%d", brightness);
        pBrightnessCharacteristic->setValue((uint8_t*)brightnessStr, strlen(brightnessStr));
        pBrightnessCharacteristic->notify();
        printBLEInfo("sendCurrentBrightness", ("ble send current brightness:" + String(brightnessStr)).c_str());
    }
}

void BLEHandler::sendCurrentBrightnessStatic(int brightness) {
    if (instance != nullptr) {
        instance->sendCurrentBrightness(brightness);
    }
}

int BLEHandler::getCurrentBrightness() {
    if (getCurrentBrightnessFunc != nullptr) {
        return getCurrentBrightnessFunc();
    }
    return LED_DEFAULT_BRIGHTNAESS; // 返回默认亮度
}

// 处理普通图片显示
void GIFCharacteristicCallbacks::handleImageDisplay() {
    printInfo("handleImageDisplay", "开始处理普通图片显示");
    
    if (gifDataBuffer == nullptr || gifReceivedBytes <= 0) {
        printError("handleImageDisplay", "图片数据无效");
        resetGIFReceive();
        return;
    }
    
    // 停止滚动文本，避免被覆盖
    *isScrollText = false;
    delay(50);
    freeScrollText();
    
    // 打印前几个字节用于调试
    String hexData = "图片数据前16字节: ";
    for (int i = 0; i < min(16, gifReceivedBytes); i++) {
        hexData += String(gifDataBuffer[i], HEX) + " ";
    }
    printInfo("handleImageDisplay", hexData.c_str());
    
    // 清屏
    dma_display->fillScreen(0x0000);
    
    // 简单的图片显示逻辑：将图片数据直接显示为像素
    // 这里假设图片数据是64x64的RGB565格式
    int width = 64;
    int height = 64;
    int expectedSize = width * height * 2; // RGB565 = 2 bytes per pixel
    
    printInfo("handleImageDisplay", ("期望数据大小: " + String(expectedSize) + " 字节, 实际接收: " + String(gifReceivedBytes) + " 字节").c_str());
    
    if (gifReceivedBytes >= expectedSize) {
        printInfo("handleImageDisplay", ("显示图片: " + String(width) + "x" + String(height) + " 像素").c_str());
        
        // 将图片数据绘制到LED矩阵
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelIndex = (y * width + x) * 2;
                if (pixelIndex + 1 < gifReceivedBytes) {
                    uint16_t color = (gifDataBuffer[pixelIndex] << 8) | gifDataBuffer[pixelIndex + 1];
                    dma_display->drawPixel(x, y, color);
                }
            }
        }
        
        printInfo("handleImageDisplay", "图片显示完成");
    } else {
        printError("handleImageDisplay", ("图片数据不足: 期望 " + String(expectedSize) + " 字节, 实际 " + String(gifReceivedBytes) + " 字节").c_str());
        
        // 即使数据不足，也尝试显示部分图片
        int availablePixels = gifReceivedBytes / 2;
        int displayWidth = min(width, availablePixels / height);
        int displayHeight = min(height, availablePixels / width);
        
        if (displayWidth > 0 && displayHeight > 0) {
            printInfo("handleImageDisplay", ("显示部分图片: " + String(displayWidth) + "x" + String(displayHeight) + " 像素").c_str());
            
            for (int y = 0; y < displayHeight; y++) {
                for (int x = 0; x < displayWidth; x++) {
                    int pixelIndex = (y * width + x) * 2;
                    if (pixelIndex + 1 < gifReceivedBytes) {
                        uint16_t color = (gifDataBuffer[pixelIndex] << 8) | gifDataBuffer[pixelIndex + 1];
                        dma_display->drawPixel(x, y, color);
                    }
                }
            }
        }
    }
    
    // 清理数据
    resetGIFReceive();
}

// ============================================================================
// 计时游戏命令处理
// ============================================================================

void ControlCharacteristicCallbacks::handleTimerGameCommand(std::string value) {
    printBLEInfo("handleTimerGameCommand", ("计时游戏命令: " + String(value.c_str())).c_str());
    
    // 停止其他显示模式
    if (*isShowGIF) {
        *isShowGIF = false;
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            printInfo("handleTimerGameCommand", "计时游戏命令，已清除GIF文件");
        }
    }
    
    if (*isScrollText) {
        *isScrollText = false;
        freeScrollText();
    }
    
    setClockMode(false);
    
    // 解析命令类型
    if (value.length() < 1) {
        printError("handleTimerGameCommand", "命令格式错误");
        return;
    }
    
    char subCommand = value[0];
    std::string commandData = value.length() > 1 ? value.substr(1) : "";
    
    switch (subCommand) {
        case 'S': // GS - 开始游戏，生成随机时间
            handleTimerGameStart();
            break;
        case 'T': // GT - 开始计时
            handleTimerGameTimerStart();
            break;
        case 'P': // GP - 停止计时
            handleTimerGameTimerStop();
            break;
        default:
            printError("handleTimerGameCommand", ("未知子命令: " + String(subCommand)).c_str());
            break;
    }
}

// 计时游戏全局变量
static unsigned long targetTimeMs = 0;
static unsigned long gameStartTime = 0;
static bool isTimerRunning = false;
static unsigned long lastUpdateTime = 0;

void ControlCharacteristicCallbacks::handleTimerGameStart() {
    printBLEInfo("handleTimerGameStart", "开始计时游戏，生成随机时间");
    
    // 停止其他显示模式
    *isScrollText = false;
    *isShowGIF = false;
    freeScrollText();
    
    // 清理GIF文件
    if (FILESYSTEM.exists("/temp.gif")) {
        FILESYSTEM.remove("/temp.gif");
        printInfo("handleTimerGameStart", "计时游戏开始，已清除GIF文件");
    }
    
    // 生成0-10秒之间的随机时间
    int randomSeconds = random(0, 11); // 0-10秒
    int randomMs = random(0, 100);     // 0-99毫秒
    targetTimeMs = randomSeconds * 1000 + randomMs; // 直接使用毫秒值
    
    // 格式化时间字符串并保存
    char timeString[10];
    snprintf(timeString, sizeof(timeString), "%02d:%02d", randomSeconds, randomMs);
    
    // 保存目标时间字符串，避免重新计算
    // 使用全局静态变量，确保在updateTimerGameDisplay中也能访问
    extern char savedTargetString[10];
    strcpy(savedTargetString, timeString);
    
    // 显示目标时间（顶部字体，根据屏幕尺寸调整）
    dma_display->setTextColor(dma_display->color565(0, 0, 255)); // 蓝色
    int targetFontSize = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 1; // 128x64使用字体大小2
    dma_display->setTextSize(targetFontSize);
    
    // 清屏
    clear();
    
    // 使用精确的文本宽度计算
    int16_t x1, y1;
    uint16_t w, h;
    dma_display->getTextBounds(timeString, 0, 0, &x1, &y1, &w, &h);
    int textWidth = w; // 使用库函数计算的精确宽度
    int x = (PANEL_RES_X - textWidth) / 2; // 所有屏幕尺寸都完全居中
    int y = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 8; // 128x64上移6像素
    
    dma_display->setCursor(x, y);
    dma_display->print(timeString);
    
    // 在下方显示00:00开始计时
    dma_display->setTextColor(dma_display->color565(0, 255, 0)); // 绿色
    int currentFontSize = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 1; // 128x64使用字体大小2
    dma_display->setTextSize(currentFontSize);
    
    // 使用精确的文本宽度计算
    int16_t startX1, startY1;
    uint16_t startW, startH;
    dma_display->getTextBounds("00:00", 0, 0, &startX1, &startY1, &startW, &startH);
    int startTextWidth = startW; // 使用库函数计算的精确宽度
    int startX = (PANEL_RES_X - startTextWidth) / 2; // 所有屏幕尺寸都完全居中
    int startY = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? (PANEL_RES_Y / 2) - 2 : (PANEL_RES_Y / 2) + 4; // 128x64上移6像素
    
    dma_display->setCursor(startX, startY);
    dma_display->print("00:00");
    
    isTimerRunning = false;
    
    String logMsg = "目标时间: " + String(timeString) + " (" + String(targetTimeMs) + "ms)";
    printInfo("handleTimerGameStart", logMsg.c_str());
}

void ControlCharacteristicCallbacks::handleTimerGameTimerStart() {
    printBLEInfo("handleTimerGameTimerStart", "开始计时");
    
    // 停止其他显示模式
    *isScrollText = false;
    *isShowGIF = false;
    freeScrollText();
    
    if (targetTimeMs == 0) {
        printError("handleTimerGameTimerStart", "目标时间未设置");
        return;
    }
    
    isTimerRunning = true;
    gameStartTime = millis();
    lastUpdateTime = gameStartTime;
    
    // 立即显示开始时间
    updateTimerGameDisplay();
    
    printInfo("handleTimerGameTimerStart", "计时开始");
}

void ControlCharacteristicCallbacks::handleTimerGameTimerStop() {
    printBLEInfo("handleTimerGameTimerStop", "停止计时");
    
    // 停止其他显示模式
    *isScrollText = false;
    *isShowGIF = false;
    freeScrollText();
    
    if (!isTimerRunning) {
        printError("handleTimerGameTimerStop", "计时未开始");
        return;
    }
    
    isTimerRunning = false;
    
    // 计算实际时间
    unsigned long actualTimeMs = millis() - gameStartTime;
    unsigned long timeDifference = abs((long)(actualTimeMs - targetTimeMs));
    
    // 判断结果（允许100毫秒误差）
    bool isWin = timeDifference <= 100;
    
    // 先设置颜色和字体，再清屏，避免显示错乱
    dma_display->setTextColor(dma_display->color565(0, 255, 0)); // 绿色
    int currentFontSize = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 1; // 128x64使用字体大小2
    dma_display->setTextSize(currentFontSize);
    
    // 清屏并显示结果
    clear();
    
    // 添加短暂延迟，确保清屏完成
    delay(10);
    
    // 计算最终时间字符串
    int finalSeconds = actualTimeMs / 1000;
    int finalMs = (actualTimeMs % 1000) / 10;
    char finalTimeString[10];
    snprintf(finalTimeString, sizeof(finalTimeString), "%02d:%02d", finalSeconds, finalMs);
    
    // 使用精确的文本宽度计算
    int16_t finalX1, finalY1;
    uint16_t finalW, finalH;
    dma_display->getTextBounds(finalTimeString, 0, 0, &finalX1, &finalY1, &finalW, &finalH);
    int finalTextWidth = finalW; // 使用库函数计算的精确宽度
    int finalX = (PANEL_RES_X - finalTextWidth) / 2; // 所有屏幕尺寸都完全居中
    int finalY = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? (PANEL_RES_Y / 2) - 2 : (PANEL_RES_Y / 2) + 4; // 128x64上移6像素
    
    dma_display->setCursor(finalX, finalY);
    dma_display->print(finalTimeString);
    
    // 在目标时间位置显示结果
    if (isWin) {
        // 设置WIN显示的颜色和字体
        dma_display->setTextColor(dma_display->color565(0, 255, 0)); // 绿色
        int resultFontSize = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 1; // 128x64使用字体大小2
        dma_display->setTextSize(resultFontSize);
        
        // 使用精确的文本宽度计算
        int16_t winX1, winY1;
        uint16_t winW, winH;
        dma_display->getTextBounds("WIN!", 0, 0, &winX1, &winY1, &winW, &winH);
        int textWidth = winW; // 使用库函数计算的精确宽度
        int x = (PANEL_RES_X - textWidth) / 2; // 所有屏幕尺寸都完全居中
        int y = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 8; // 128x64上移6像素
        
        dma_display->setCursor(x, y);
        dma_display->print("WIN!");
        printInfo("handleTimerGameTimerStop", "游戏胜利");
    } else {
        // 设置LOSE显示的颜色和字体
        dma_display->setTextColor(dma_display->color565(255, 0, 0)); // 红色
        int resultFontSize = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 1; // 128x64使用字体大小2
        dma_display->setTextSize(resultFontSize);
        
        // 使用精确的文本宽度计算
        int16_t loseX1, loseY1;
        uint16_t loseW, loseH;
        dma_display->getTextBounds("LOSE!", 0, 0, &loseX1, &loseY1, &loseW, &loseH);
        int textWidth = loseW; // 使用库函数计算的精确宽度
        int x = (PANEL_RES_X - textWidth) / 2; // 所有屏幕尺寸都完全居中
        int y = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 8; // 128x64上移6像素
        
        dma_display->setCursor(x, y);
        dma_display->print("LOSE!");
        printInfo("handleTimerGameTimerStop", "游戏失败");
    }
    
    // 清空保存的目标时间字符串，避免下次游戏时显示残留
    memset(savedTargetString, 0, sizeof(savedTargetString));
    
    // 重置目标时间
    targetTimeMs = 0;
    
    String logMsg = "实际时间: " + String(actualTimeMs) + "ms, 目标时间: " + String(targetTimeMs) + "ms, 误差: " + String(timeDifference) + "ms";
    printInfo("handleTimerGameTimerStop", logMsg.c_str());
}

void ControlCharacteristicCallbacks::startTimerGameUpdate() {
    // 简化实现，不需要复杂的定时更新
}

void ControlCharacteristicCallbacks::updateTimerGameDisplay() {
    if (isTimerRunning && targetTimeMs > 0) {
        unsigned long currentTime = millis();
        unsigned long elapsedTime = currentTime - gameStartTime;
        
        // 每50毫秒更新一次显示，让毫秒部分也能变化
        if (currentTime - lastUpdateTime >= 50) {
            // 格式化当前时间
            unsigned long seconds = elapsedTime / 1000;
            unsigned long milliseconds = (elapsedTime % 1000) / 10;
            
            char timeString[10];
            snprintf(timeString, sizeof(timeString), "%02lu:%02lu", seconds, milliseconds);
            
        // 清屏并显示当前时间
        clear();
        
        // 重新显示目标时间（顶部字体，根据屏幕尺寸调整）
        dma_display->setTextColor(dma_display->color565(0, 0, 255)); // 蓝色
        int targetFontSize = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 1; // 128x64使用字体大小2
        dma_display->setTextSize(targetFontSize);
        
        // 使用保存的目标时间字符串（不变）
        // 如果字符串为空，说明还没有设置目标时间，使用当前目标时间
        if (strlen(savedTargetString) == 0) {
            int targetSeconds = targetTimeMs / 1000;
            int targetMs = (targetTimeMs % 1000) / 10;
            snprintf(savedTargetString, sizeof(savedTargetString), "%02d:%02d", targetSeconds, targetMs);
        }
        
        // 使用精确的文本宽度计算
        int16_t targetX1, targetY1;
        uint16_t targetW, targetH;
        dma_display->getTextBounds(savedTargetString, 0, 0, &targetX1, &targetY1, &targetW, &targetH);
        int targetTextWidth = targetW; // 使用库函数计算的精确宽度
        int targetX = (PANEL_RES_X - targetTextWidth) / 2; // 所有屏幕尺寸都完全居中
        int targetY = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 8; // 128x64上移6像素
        
        dma_display->setCursor(targetX, targetY);
        dma_display->print(savedTargetString);
        
        // 显示当前时间（下方字体，根据屏幕尺寸调整）
        dma_display->setTextColor(dma_display->color565(0, 255, 0)); // 绿色
        int currentFontSize = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? 2 : 1; // 128x64使用字体大小2
        dma_display->setTextSize(currentFontSize);
        
        // 使用精确的文本宽度计算
        int16_t currentX1, currentY1;
        uint16_t currentW, currentH;
        dma_display->getTextBounds(timeString, 0, 0, &currentX1, &currentY1, &currentW, &currentH);
        int textWidth = currentW; // 使用库函数计算的精确宽度
        int x = (PANEL_RES_X - textWidth) / 2; // 所有屏幕尺寸都完全居中
        int y = (PANEL_RES_X == 128 && PANEL_RES_Y == 64) ? (PANEL_RES_Y / 2) - 2 : (PANEL_RES_Y / 2) + 4; // 128x64上移6像素
            
            dma_display->setCursor(x, y);
            dma_display->print(timeString);
            
            lastUpdateTime = currentTime;
        }
    }
}

// 更新计时游戏显示
void BLEHandler::updateTimerGameDisplay() {
    if (controlCallbacks) {
        controlCallbacks->updateTimerGameDisplay();
    }
}
