#include "BLEHandler.h"
#include "ESP32-HUB75-MatrixPanel-I2S-DMA.h"
#include <AnimatedGIF.h>
#include <LittleFS.h>
#include <math.h>
#include <string.h>
#include <stdlib.h>
#include <string>
#include "esp_task_wdt.h"

#define FILESYSTEM LittleFS

// 前向声明
extern void displayGIF(char *fileName);

// 静态成员变量初始化
uint8_t* DrawNormalCharacteristicCallbacks::dataBuffer = NULL;
int DrawNormalCharacteristicCallbacks::receivedBytes = 0;
int DrawNormalCharacteristicCallbacks::expectedBytes = 0;
int DrawNormalCharacteristicCallbacks::expectedChunks = 0;
int DrawNormalCharacteristicCallbacks::receivedChunks = 0;
bool DrawNormalCharacteristicCallbacks::isReceiving = false;
bool DrawNormalCharacteristicCallbacks::isHeaderReceived = false;
unsigned long DrawNormalCharacteristicCallbacks::lastReceiveTime = 0;

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

// BLEHandler静态实例指针初始化
BLEHandler* BLEHandler::instance = nullptr;

// TextCharacteristicCallbacks 实现
TextCharacteristicCallbacks::TextCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* gifFlag,
                                                       void (*textSizeFunc)(int), void (*displayFunc)(char*, bool)) {
    dma_display = display;
    isShowGIF = gifFlag;
    setTextSize = textSizeFunc;
    displayText = displayFunc;
}

void TextCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        // 清理GIF文件
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("显示文本，已清除GIF文件");
        }
    }
    std::string value = pCharacteristic->getValue();
    BLE_DEBUG_PRINTF("TextCharacteristicCallbacks: %s\n", value.c_str());
    
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

// TextScrollCharacteristicCallbacks 实现
TextScrollCharacteristicCallbacks::TextScrollCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* gifFlag,
                                                                   void (*textSizeFunc)(int), void (*scrollSpeedFunc)(int),
                                                                   void (*displayFunc)(char*, bool)) {
    dma_display = display;
    isShowGIF = gifFlag;
    setTextSize = textSizeFunc;
    setTextScrollSpeed = scrollSpeedFunc;
    displayText = displayFunc;
}

void TextScrollCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        // 清理GIF文件
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("显示滚动文本，已清除GIF文件");
        }
    }
    std::string value = pCharacteristic->getValue();
    BLE_DEBUG_PRINTF("TextScrollCharacteristicCallbacks: %s\n", value.c_str());
    
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

// DrawNormalCharacteristicCallbacks 实现
DrawNormalCharacteristicCallbacks::DrawNormalCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, bool* scrollFlag,
                                                                   void (*freeTextFunc)(), void (*clearFunc)(), bool* gifFlag) {
    dma_display = display;
    isScrollText = scrollFlag;
    freeScrollText = freeTextFunc;
    clear = clearFunc;
    isShowGIF = gifFlag;
}

void DrawNormalCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    uint8_t *v = pCharacteristic->getData();
    int dataLength = pCharacteristic->getLength();
    
    IMAGE_DEBUG_PRINTF("接收到数据，长度: %d\n", dataLength);
    
    if (isReceiving && (millis() - lastReceiveTime > RECEIVE_TIMEOUT)) {
        DEBUG_PRINTLN("接收超时，重置接收状态");
        resetReceive();
    }
    
    lastReceiveTime = millis();
    
    if (!isHeaderReceived) {
        handleHeader(v, dataLength);
    } else {
        handleDataChunk(v, dataLength);
    }
}

void DrawNormalCharacteristicCallbacks::handleHeader(uint8_t* data, int length) {
    char headerStr[HEADER_BUFFER_SIZE];
    if (length < sizeof(headerStr) - 1) {
        memcpy(headerStr, data, length);
        headerStr[length] = '\0';
    } else {
        DEBUG_PRINTLN("头信息过长，重置接收");
        resetReceive();
        return;
    }
    
    IMAGE_DEBUG_PRINTF("接收到头信息: %s\n", headerStr);
    
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
    
    IMAGE_DEBUG_PRINTF("解析头信息成功: 总大小=%d, 分块数=%d\n", expectedBytes, expectedChunks);
    
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

void DrawNormalCharacteristicCallbacks::handleDataChunk(uint8_t* data, int length) {
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
    
    IMAGE_DEBUG_PRINTF("接收数据块 %d/%d, 累积字节: %d/%d\n", 
                       receivedChunks, expectedChunks, receivedBytes, expectedBytes);
    
    if (receivedBytes >= expectedBytes) {
        DEBUG_PRINTLN("图像数据接收完成，开始绘制");
        drawCompleteImage();
        resetReceive();
    }
}

void DrawNormalCharacteristicCallbacks::drawCompleteImage() {
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        // 清理GIF文件
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
    
    IMAGE_DEBUG_PRINTF("绘制图像，尺寸: %dx%d\n", imageSize, imageSize);
    dma_display->drawBitmap(0, 0, dataBuffer, imageSize, imageSize, 0xFFFF); // 使用白色
    DEBUG_PRINTLN("图像绘制完成");
}

void DrawNormalCharacteristicCallbacks::checkTimeout() {
    if (isReceiving && (millis() - lastReceiveTime > RECEIVE_TIMEOUT)) {
        DEBUG_PRINTLN("主循环检测到接收超时，重置接收状态");
        resetReceive();
    }
}

void DrawNormalCharacteristicCallbacks::resetReceive() {
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

// FillPixelCharacteristicCallbacks 实现
FillPixelCharacteristicCallbacks::FillPixelCharacteristicCallbacks(MatrixPanel_I2S_DMA* display) {
    dma_display = display;
}

void FillPixelCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    std::string value = pCharacteristic->getValue();
    if (value.length() > 0) {
        char *token;
        int values[3];
        int count = 0;

        token = strtok((char *)value.c_str(), ",");

        while (token != NULL && count < 3) {
            values[count] = atoi(token);
            count++;
            token = strtok(NULL, ",");
        }

        if (values[2] == 0) {
            dma_display->writePixel(values[0], values[1], 0x0000); // 黑色
        } else {
            dma_display->writePixel(values[0], values[1], 0xFFFF); // 白色
        }
    }
}

// FillScreenCharacteristicCallbacks 实现
FillScreenCharacteristicCallbacks::FillScreenCharacteristicCallbacks(MatrixPanel_I2S_DMA* display, void (*clearFunc)(), bool* gifFlag) {
    dma_display = display;
    clear = clearFunc;
    isShowGIF = gifFlag;
}

void FillScreenCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    DEBUG_PRINTLN("FillScreenCharacteristic_recev");
    
    // 停止GIF显示并清理文件
    if (*isShowGIF) {
        *isShowGIF = false;
        // 清理GIF文件
        if (FILESYSTEM.exists("/temp.gif")) {
            FILESYSTEM.remove("/temp.gif");
            DEBUG_PRINTLN("满屏操作，已清除GIF文件");
        }
    }
    
    std::string value = pCharacteristic->getValue();
    DEBUG_PRINTLN(value.c_str());

    int isClear = atoi(value.c_str());

    if (isClear) {
        clear();
    } else {
        dma_display->fillScreen(0xFFFF); // 白色
    }
}

// BrightnessCharacteristicCallbacks 实现
BrightnessCharacteristicCallbacks::BrightnessCharacteristicCallbacks(void (*brightnessFunc)(int)) {
    setLedBrightness = brightnessFunc;
}

void BrightnessCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    std::string value = pCharacteristic->getValue();
    BLE_DEBUG_PRINTF("****ble brightness recv:%s*****\n", value.c_str());
    
    int brightness = atoi(value.c_str());
    if (isValidBrightness(brightness)) {
        setLedBrightness(brightness);
        
        // 发送亮度值通知给客户端
        char brightnessStr[8];
        snprintf(brightnessStr, sizeof(brightnessStr), "%d", brightness);
        pCharacteristic->setValue((uint8_t*)brightnessStr, strlen(brightnessStr));
        pCharacteristic->notify();
        BLE_DEBUG_PRINTF("****ble brightness notify:%s*****\n", brightnessStr);
    }
}

// RefreshRateCharacteristicCallbacks 实现
RefreshRateCharacteristicCallbacks::RefreshRateCharacteristicCallbacks(void (*refreshRateFunc)(int)) {
    setRefreshRateFunc = refreshRateFunc;
}

void RefreshRateCharacteristicCallbacks::onWrite(BLECharacteristic *pCharacteristic) {
    std::string value = pCharacteristic->getValue();
    BLE_DEBUG_PRINTF("****ble refresh rate recv:%s*****\n", value.c_str());
    
    int refreshRate = atoi(value.c_str());
    if (refreshRate >= 10 && refreshRate <= 200) {
        setRefreshRateFunc(refreshRate);
    }
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
    
    BLE_DEBUG_PRINTF("GIFCharacteristicCallbacks: 数据长度=%d\n", dataLength);
    
    // 更新最后接收时间
    gifLastReceiveTime = millis();
    
    // 检查数据包类型
    if (dataLength >= 2) {
        uint8_t packetType = v[0];
        uint8_t chunkIndex = v[1];
        
        BLE_DEBUG_PRINTF("GIF数据包类型: %d, 块索引: %d\n", packetType, chunkIndex);
        
        // 打印前几个字节用于调试
        BLE_DEBUG_PRINTF("数据包前8字节: ");
        for (int i = 0; i < min(8, dataLength); i++) {
            BLE_DEBUG_PRINTF("%02X ", v[i]);
        }
        BLE_DEBUG_PRINTF("\n");
        
        if (packetType == 0x01) {  // 头信息包
            DEBUG_PRINTLN("收到GIF头信息包");
            // 如果正在接收数据，先重置
            if (gifIsReceiving) {
                DEBUG_PRINTLN("收到新的头信息包，重置之前的接收状态");
                resetGIFReceive();
            }
            // 头信息包现在也是510字节，但只需要前4字节的文件大小信息
            handleGIFHeader(v + 2, 4);
        } else if (packetType == 0x02) {  // 数据包
            DEBUG_PRINTF("收到GIF数据包，块索引: %d, 当前接收状态: gifIsReceiving=%d, gifIsHeaderReceived=%d\n", 
                        chunkIndex, gifIsReceiving, gifIsHeaderReceived);
            
            // 检查是否正在接收数据
            if (gifIsReceiving && gifIsHeaderReceived) {
                // 检查超时 - 增加到30秒
                if (millis() - gifLastReceiveTime > 30000) {
                    DEBUG_PRINTLN("GIF接收超时，重置接收状态");
                    resetGIFReceive();
                } else {
                    handleGIFDataChunk(v + 2, dataLength - 2);
                }
            } else {
                DEBUG_PRINTLN("收到数据包但未在接收状态，忽略");
                // 如果收到数据包但没有头信息，重置状态
                if (!gifIsHeaderReceived) {
                    DEBUG_PRINTLN("未收到头信息包，重置接收状态");
                    resetGIFReceive();
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
    DEBUG_PRINTF("处理GIF头信息: 数据长度=%d\n", length);
    
    if (length < 4) {
        DEBUG_PRINTLN("GIF头信息长度不足");
        return;
    }
    
    // 解析头信息：总字节数 (4字节)
    gifExpectedBytes = (data[0] << 24) | (data[1] << 16) | (data[2] << 8) | data[3];
    
    BLE_DEBUG_PRINTF("GIF头信息: 期望接收 %d 字节\n", gifExpectedBytes);
    DEBUG_PRINTF("头信息字节: %02X %02X %02X %02X\n", data[0], data[1], data[2], data[3]);
    
    // 检查GIF文件大小是否合理
    if (gifExpectedBytes <= 0 || gifExpectedBytes > GIF_MAX_FILE_SIZE) {
        DEBUG_PRINTF("GIF文件大小不合理: %d 字节 (最大1MB)\n", gifExpectedBytes);
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
    
    // 动态调整文件大小阈值，根据可用内存情况
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    size_t memoryThreshold = GIF_MEMORY_THRESHOLD_DEFAULT;  // 默认阈值
    
    // 根据可用内存动态调整阈值
    if (freeHeap > 200 * 1024) {  // 如果可用内存超过200KB
        memoryThreshold = GIF_MEMORY_THRESHOLD_HIGH;  // 提高阈值
    } else if (freeHeap < 100 * 1024) {  // 如果可用内存少于100KB
        memoryThreshold = GIF_MEMORY_THRESHOLD_LOW;   // 降低阈值
    }
    
    DEBUG_PRINTF("动态内存阈值: %d KB, 可用内存: %d KB\n", memoryThreshold / 1024, freeHeap / 1024);
    
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
        DEBUG_PRINTF("可用堆内存: %d 字节, 最小可用: %d 字节\n", freeHeap, minFreeHeap);
        
        // 更保守的内存检查：需要比文件大小多50%的可用内存
        size_t requiredMemory = gifExpectedBytes * GIF_MEMORY_MULTIPLIER;
        if (freeHeap < requiredMemory) {
            DEBUG_PRINTF("内存不足，需要 %d 字节，可用 %d 字节\n", requiredMemory, freeHeap);
            resetGIFReceive();
            return;
        }
        
        DEBUG_PRINTF("内存检查通过，需要 %d 字节，可用 %d 字节\n", requiredMemory, freeHeap);
        
        // 分配缓冲区
        if (gifDataBuffer != NULL) {
            free(gifDataBuffer);
            gifDataBuffer = NULL;
        }
        
        // 尝试分配内存，如果失败则尝试大文件模式
        gifDataBuffer = (uint8_t*)malloc(gifExpectedBytes);
        if (gifDataBuffer == NULL) {
            DEBUG_PRINTLN("GIF缓冲区分配失败，尝试内存碎片整理");
            
            // 记录当前内存状态
            size_t currentFreeHeap = ESP.getFreeHeap();
            size_t currentMinFreeHeap = ESP.getMinFreeHeap();
            DEBUG_PRINTF("分配失败时内存状态: 可用 %d 字节, 最小可用 %d 字节\n", 
                        currentFreeHeap, currentMinFreeHeap);
            
            // 尝试内存碎片整理
            ESP.getFreeHeap(); // 触发内存整理
            
            // 再次尝试分配
            gifDataBuffer = (uint8_t*)malloc(gifExpectedBytes);
            if (gifDataBuffer == NULL) {
                DEBUG_PRINTLN("内存碎片整理后仍分配失败，切换到文件模式");
                
                // 记录整理后的内存状态
                currentFreeHeap = ESP.getFreeHeap();
                DEBUG_PRINTF("内存碎片整理后: 可用 %d 字节\n", currentFreeHeap);
                
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
    DEBUG_PRINTF("GIF模式设置: 文件模式=%s\n", gifUseFileMode ? "是" : "否");
    
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
    DEBUG_PRINTF("GIF接收开始: 内存状态 %d 字节\n", startFreeHeap);
    
    BLE_DEBUG_PRINTF("GIF开始接收: 期望 %d 个数据块\n", gifExpectedChunks);
    DEBUG_PRINTLN("GIF头信息处理完成，开始接收数据包");
}

void GIFCharacteristicCallbacks::handleGIFDataChunk(uint8_t* data, int length) {
    if (!gifIsReceiving || !gifIsHeaderReceived) {
        DEBUG_PRINTLN("GIF数据接收状态错误");
        return;
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
            DEBUG_PRINTF("内存不足警告: 当前可用 %d 字节\n", currentFreeHeap);
        }
    }
    
    // 根据文件模式标志选择处理方式
    DEBUG_PRINTF("GIF数据块处理: 文件模式=%s, 缓冲区=%s\n", 
                 gifUseFileMode ? "是" : "否", 
                 gifDataBuffer ? "已分配" : "未分配");
    
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
    
    BLE_DEBUG_PRINTF("GIF数据块接收: %d/%d, 已接收 %d/%d 字节\n", 
                     gifReceivedChunks, gifExpectedChunks, gifReceivedBytes, gifExpectedBytes);
    
    // 定期输出详细进度
    if (gifReceivedChunks % GIF_PROGRESS_REPORT_INTERVAL == 0) {
        size_t currentFreeHeap = ESP.getFreeHeap();
        DEBUG_PRINTF("GIF接收进度: %d/%d 块 (%d%%), 内存: %d 字节\n", 
                     gifReceivedChunks, gifExpectedChunks, 
                     (gifReceivedChunks * 100) / gifExpectedChunks,
                     currentFreeHeap);
    }
    
    // 检查是否接收完成
    if (gifReceivedBytes >= gifExpectedBytes) {
        BLE_DEBUG_PRINTF("GIF数据接收完成: %d 字节\n", gifReceivedBytes);
        
        // 记录接收完成时的内存状态
        size_t endFreeHeap = ESP.getFreeHeap();
        DEBUG_PRINTF("GIF接收完成: 内存状态 %d 字节\n", endFreeHeap);
        DEBUG_PRINTF("GIF接收统计: 总块数=%d, 总字节=%d, 平均每块=%d 字节\n", 
                     gifReceivedChunks, gifReceivedBytes, gifReceivedBytes / gifReceivedChunks);
        
        DEBUG_PRINTLN("=== GIF数据接收完成，准备显示 ===");
        // 异步处理GIF显示，不阻塞BLE接收
        prepareGIFForDisplay();
        // 只重置接收状态，不清理文件，让主循环处理显示和清理
        // 重置接收状态，防止后续数据包干扰
        gifReceivedBytes = 0;
        gifExpectedBytes = 0;
        gifExpectedChunks = 0;
        gifReceivedChunks = 0;
        gifIsReceiving = false;
        gifIsHeaderReceived = false;
        gifLastReceiveTime = 0;
        // 不调用resetGIFReceive()，因为会删除文件
    }
    
    // 检查接收超时
    if (gifIsReceiving && (millis() - gifLastReceiveTime > GIF_RECEIVE_TIMEOUT)) {
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
        DEBUG_PRINTLN("GIF数据无效，无法显示");
        // 只重置状态，不删除文件，让主循环处理清理
        resetGIFReceiveStateOnly();
        return;
    }
    
    // 检查可用内存
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    DEBUG_PRINTF("GIF显示前内存检查: 可用 %d 字节, 最小可用 %d 字节\n", freeHeap, minFreeHeap);
    
    // 如果可用内存太少，拒绝显示GIF
    if (freeHeap < GIF_DISPLAY_MIN_MEMORY) {
        DEBUG_PRINTF("内存不足，无法显示GIF。可用内存: %d 字节\n", freeHeap);
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
        
        if (fileSize != gifReceivedBytes) {
            DEBUG_PRINTF("文件大小不匹配: 期望 %d 字节, 实际 %d 字节\n", gifReceivedBytes, fileSize);
            // 只重置状态，不删除文件，让主循环处理清理
            resetGIFReceiveStateOnly();
            return;
        }
        
        BLE_DEBUG_PRINTF("大文件GIF已保存: %d 字节\n", gifReceivedBytes);
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
        
        BLE_DEBUG_PRINTF("小文件GIF已保存: %d 字节\n", written);
    }
    
    // 显示GIF前再次检查内存
    freeHeap = ESP.getFreeHeap();
    DEBUG_PRINTF("GIF显示前最终内存检查: 可用 %d 字节\n", freeHeap);
    
    if (freeHeap < GIF_DISPLAY_FINAL_MIN_MEMORY) {
        DEBUG_PRINTF("最终内存检查失败，取消GIF显示。可用内存: %d 字节\n", freeHeap);
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
    DEBUG_PRINTF("GIF文件大小: %d 字节\n", gifReceivedBytes);
    DEBUG_PRINTF("当前可用内存: %d 字节\n", ESP.getFreeHeap());
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
        free(gifDataBuffer);
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
        free(gifDataBuffer);
        gifDataBuffer = NULL;
    }
    
    // 删除临时文件（只在接收错误时删除）
    if (FILESYSTEM.exists("/temp.gif")) {
        FILESYSTEM.remove("/temp.gif");
        DEBUG_PRINTLN("GIF接收错误，已删除临时文件");
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
    if (gifIsReceiving && (millis() - gifLastReceiveTime > GIF_RECEIVE_TIMEOUT)) {
        DEBUG_PRINTLN("GIF接收超时，重置状态");
        resetGIFReceive();
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
        free(gifDataBuffer);
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
    DEBUG_PRINTF("启动时内存状态: 可用 %d 字节, 最小可用 %d 字节\n", freeHeap, minFreeHeap);
    
    // 如果内存碎片化严重，尝试进行内存整理
    if (freeHeap - minFreeHeap > GIF_MEMORY_FRAGMENTATION_THRESHOLD) {
        DEBUG_PRINTLN("检测到内存碎片化，进行内存整理");
        ESP.getFreeHeap(); // 触发内存整理
        freeHeap = ESP.getFreeHeap();
        DEBUG_PRINTF("内存整理后: 可用 %d 字节\n", freeHeap);
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
    // 使用全局变量currentBrightness获取当前亮度值
    extern int currentBrightness;
    int brightnessToSend = currentBrightness;
    
    // 通过BLEHandler发送当前亮度值
    BLEHandler::sendCurrentBrightnessStatic(brightnessToSend);
}

void MyBLEServerCallbacks::onDisconnect(BLEServer *pServer) {
    setTextSize(DEFAULT_TEXT_SIZE);
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
                     void (*refreshRateFunc)(int), bool* scrollFlag, bool* gifFlag) {
    dma_display = display;
    gif = gifDecoder;
    setTextSizeFunc = textSizeFunc;
    setTextScrollSpeedFunc = scrollSpeedFunc;
    displayTextFunc = displayFunc;
    freeScrollTextFunc = freeTextFunc;
    this->clearFunc = clearFunc;
    setLedBrightnessFunc = brightnessFunc;
    setRefreshRateFunc = refreshRateFunc;
    isScrollText = scrollFlag;
    isShowGIF = gifFlag;
    
    // 设置静态实例指针
    instance = this;
}

void BLEHandler::init() {
    DEBUG_PRINTLN("初始化BLE");
    
    // 内存优化：在BLE初始化前进行内存整理
    size_t freeHeap = ESP.getFreeHeap();
    size_t minFreeHeap = ESP.getMinFreeHeap();
    DEBUG_PRINTF("BLE初始化前内存状态: 可用 %d 字节, 最小可用 %d 字节\n", freeHeap, minFreeHeap);
    
    // 如果内存碎片化严重，进行内存整理
    if (freeHeap - minFreeHeap > GIF_BLE_FRAGMENTATION_THRESHOLD) {
        DEBUG_PRINTLN("检测到内存碎片化，进行内存整理");
        ESP.getFreeHeap(); // 触发内存整理
        freeHeap = ESP.getFreeHeap();
        DEBUG_PRINTF("内存整理后: 可用 %d 字节\n", freeHeap);
    }

    BLEDevice::init(BLE_DEVICE_NAME);

    // 设置BLE MTU大小
    BLEDevice::setMTU(BLE_MTU_SIZE);
    DEBUG_PRINTF("BLE MTU设置为%d字节\n", BLE_MTU_SIZE);
    
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyBLEServerCallbacks(dma_display, setTextSizeFunc, displayTextFunc));
    
    pService = pServer->createService(BLE_SERVICE_UUID);
    
    createCharacteristics();
    
    pService->start();
    
    DEBUG_PRINTLN("BLE初始化完成");
}

void BLEHandler::createCharacteristics() {
    DEBUG_PRINTLN("创建BLE特征值");
    
    // 静态文本
    BLECharacteristic *pCharacText = pService->createCharacteristic(
        BLE_CHARACTERISTIC_TEXT_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
    pCharacText->setCallbacks(new TextCharacteristicCallbacks(dma_display, isShowGIF, 
                                                             setTextSizeFunc, displayTextFunc));

    // 滚动文本
    BLECharacteristic *pCharacTextScroll = pService->createCharacteristic(
        BLE_CHARACTERISTIC_TEXT_SCROLL_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
    pCharacTextScroll->setCallbacks(new TextScrollCharacteristicCallbacks(dma_display, isShowGIF,
                                                                         setTextSizeFunc, setTextScrollSpeedFunc, displayTextFunc));

        // 画图(黑白)特征
    BLECharacteristic *pCharacDrawNormal = pService->createCharacteristic(
        BLE_CHARACTERISTIC_DRAW_NORMAL_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacDrawNormal->setCallbacks(new DrawNormalCharacteristicCallbacks(dma_display, isScrollText,
                                                                          freeScrollTextFunc, clearFunc, isShowGIF));

    // // 画图(彩色)特征
    // BLECharacteristic *pCharacDrawColorful = pService->createCharacteristic(
    //     BLE_CHARACTERISTIC_DRAW_COLORFUL_UUID,
    //     BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    // pCharacDrawColorful->setCallbacks(new DrawNormalCharacteristicCallbacks(dma_display, isScrollText,
    //                                                                        freeScrollTextFunc, clearFunc));

    // 亮度特征
    BLECharacteristic *pCharacBrightness = pService->createCharacteristic(
        BLE_CHARACTERISTIC_BRIGHTNESS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacBrightness->setCallbacks(new BrightnessCharacteristicCallbacks(setLedBrightnessFunc));
    pBrightnessCharacteristic = pCharacBrightness; // 保存亮度特征指针
    
    // // 刷新频率特征
    // BLECharacteristic *pCharacRefreshRate = pService->createCharacteristic(
    //     BLE_CHARACTERISTIC_REFRESH_RATE_UUID,
    //     BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    // pCharacRefreshRate->setCallbacks(new RefreshRateCharacteristicCallbacks(setRefreshRateFunc));
    
    // 满屏
    BLECharacteristic *pCharacFillScreen = pService->createCharacteristic(
        BLE_CHARACTERISTIC_FILL_SCREEN_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacFillScreen->setCallbacks(new FillScreenCharacteristicCallbacks(dma_display, clearFunc, isShowGIF));
    
    // 单像素
    BLECharacteristic *pCharacFillPixel = pService->createCharacteristic(
        BLE_CHARACTERISTIC_FILL_PIXEL_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacFillPixel->setCallbacks(new FillPixelCharacteristicCallbacks(dma_display));
    
    // GIF
    BLECharacteristic *pCharacGIF = pService->createCharacteristic(
        BLE_CHARACTERISTIC_GIF_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacGIF->setCallbacks(new GIFCharacteristicCallbacks(dma_display, isScrollText, isShowGIF,
                                                           freeScrollTextFunc, gif));
    
    DEBUG_PRINTLN("BLE特征值创建完成");
}

void BLEHandler::startAdvertising() {
    DEBUG_PRINTLN("开始BLE广播");
    pServer->getAdvertising()->start();
}

void BLEHandler::stopAdvertising() {
    DEBUG_PRINTLN("停止BLE广播");
    pServer->getAdvertising()->stop();
}

void BLEHandler::sendCurrentBrightness(int brightness) {
    if (pBrightnessCharacteristic != nullptr) {
        char brightnessStr[8];
        snprintf(brightnessStr, sizeof(brightnessStr), "%d", brightness);
        pBrightnessCharacteristic->setValue((uint8_t*)brightnessStr, strlen(brightnessStr));
        pBrightnessCharacteristic->notify();
        BLE_DEBUG_PRINTF("****ble send current brightness:%s*****\n", brightnessStr);
    }
}

void BLEHandler::sendCurrentBrightnessStatic(int brightness) {
    if (instance != nullptr) {
        instance->sendCurrentBrightness(brightness);
    }
}