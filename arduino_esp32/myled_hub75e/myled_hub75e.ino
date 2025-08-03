/*
 * MyLED HUB75E - 优化版本
 * 使用模块化设计，提高代码可维护性和可读性
 */

// ============================================================================
// 库文件包含
// ============================================================================
#include "ESP32-VirtualMatrixPanel-I2S-DMA.h"
#include <string.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include "FS.h"
#include <LittleFS.h>
#include <AnimatedGIF.h>

// ============================================================================
// 配置和工具类包含
// ============================================================================
#include "config.h"
#include "utils.h"

// ============================================================================
// 全局变量
// ============================================================================
MatrixPanel_I2S_DMA* dma_display = nullptr;
BLEServer* pServer = nullptr;
BLEService* pService = nullptr;

// 显示状态
DisplayState currentDisplayState = STATE_IDLE;
bool isShowGIF = false;
bool isScrollText = false;

// 文本滚动相关
char* scrollTextContent = nullptr;
int scrollTextTimeDelay = 20;
int scrollXMove = -1;
int scrollTextXPosition = PANEL_RES_X;
int scrollTextYPosition = 0;
int scrollTextSpeed = 1;
unsigned long isAnimationDue;
int16_t xOne, yOne;
uint16_t scrollTextWidth, scrollTextHeight;
int textSize = 1;
bool isTextWrap = false;

// GIF相关
AnimatedGIF gif;
File f;
int x_offset, y_offset;
unsigned long start_tick = 0;

// 颜色定义
uint16_t myBLACK, myWHITE, myRED, myGREEN, myBLUE;

// ============================================================================
// 工具函数
// ============================================================================

/**
 * 安全释放内存
 */
void safeFreeScrollText() {
    SAFE_FREE(scrollTextContent);
}

/**
 * 设置LED亮度
 */
void setLedBrightness(int value) {
    if (dma_display) {
        dma_display->setBrightness8(value);
    }
}

/**
 * 清屏
 */
void clear() {
    if (dma_display) {
        dma_display->fillScreen(myBLACK);
    }
}

/**
 * 设置文本大小
 */
void setTextSize(int size) {
    textSize = size;
    if (dma_display) {
        dma_display->setTextSize(textSize);
    }
}

/**
 * 设置文本滚动速度
 */
void setTextScrollSpeed(int speed) {
    scrollTextSpeed = speed;
    switch (scrollTextSpeed) {
        case SPEED_LOW:
            scrollXMove = SCROLL_OFFSET_LOW;
            scrollTextTimeDelay = SCROLL_TIME_DELAY_LOW;
            break;
        case SPEED_MEDIUM:
            scrollXMove = SCROLL_OFFSET_MEDIUM;
            scrollTextTimeDelay = SCROLL_TIME_DELAY_MEDIUM;
            break;
        case SPEED_FAST:
            scrollXMove = SCROLL_OFFSET_FAST;
            scrollTextTimeDelay = SCROLL_TIME_DELAY_FAST;
            break;
        default:
            break;
    }
}

/**
 * 显示文本
 */
void displayText(const char* textContent, bool isScroll) {
    DEBUG_PRINTF("displayText: %s, isScroll: %d\n", textContent, isScroll);
    
    isScrollText = false;
    currentDisplayState = isScroll ? STATE_SCROLLING_TEXT : STATE_SHOWING_TEXT;
    
    // 防止不同步，导致内存出错
    delay(50);
    safeFreeScrollText();
    clear();
    
    if (isScroll) {
        isTextWrap = false;
        dma_display->setTextWrap(false);
        scrollTextContent = MemoryUtils::safeStringDup(textContent);
        if (scrollTextContent) {
            isScrollText = true;
        }
    } else {
        dma_display->setCursor(0, 0);
        isTextWrap = true;
        dma_display->setTextWrap(true);
        dma_display->printlnUTF8(textContent);
    }
}

// ============================================================================
// GIF相关函数
// ============================================================================

void GIFDraw(GIFDRAW* pDraw) {
    uint8_t* s;
    uint16_t* d, * usPalette, usTemp[320];
    int x, y, iWidth;

    iWidth = pDraw->iWidth;
    if (iWidth > dma_display->width())
        iWidth = dma_display->width();

    usPalette = pDraw->pPalette;
    y = pDraw->iY + pDraw->y;

    s = pDraw->pPixels;
    if (pDraw->ucDisposalMethod == 2) {
        for (x = 0; x < iWidth; x++) {
            if (s[x] == pDraw->ucTransparent)
                s[x] = pDraw->ucBackground;
        }
        pDraw->ucHasTransparency = 0;
    }
    
    if (pDraw->ucHasTransparency) {
        uint8_t* pEnd, c, ucTransparent = pDraw->ucTransparent;
        int x, iCount;
        pEnd = s + pDraw->iWidth;
        x = 0;
        iCount = 0;
        while (x < pDraw->iWidth) {
            c = ucTransparent - 1;
            d = usTemp;
            while (c != ucTransparent && s < pEnd) {
                c = *s++;
                if (c == ucTransparent) {
                    s--;
                } else {
                    *d++ = usPalette[c];
                    iCount++;
                }
            }
            if (iCount) {
                for (int xOffset = 0; xOffset < iCount; xOffset++) {
                    dma_display->drawPixel(x + xOffset, y, usTemp[xOffset]);
                }
                x += iCount;
                iCount = 0;
            }
            c = ucTransparent;
            while (c == ucTransparent && s < pEnd) {
                c = *s++;
                if (c == ucTransparent)
                    iCount++;
                else
                    s--;
            }
            if (iCount) {
                x += iCount;
                iCount = 0;
            }
        }
    } else {
        s = pDraw->pPixels;
        for (x = 0; x < pDraw->iWidth; x++) {
            dma_display->drawPixel(x, y, usPalette[*s++]);
        }
    }
}

void* GIFOpenFile(const char* fname, int32_t* pSize) {
    DEBUG_PRINTF("Playing gif: %s\n", fname);
    f = FILESYSTEM.open(fname);
    if (f) {
        *pSize = f.size();
        return (void*)&f;
    }
    return NULL;
}

void GIFCloseFile(void* pHandle) {
    File* f = static_cast<File*>(pHandle);
    if (f != NULL)
        f->close();
}

int32_t GIFReadFile(GIFFILE* pFile, uint8_t* pBuf, int32_t iLen) {
    int32_t iBytesRead;
    iBytesRead = iLen;
    File* f = static_cast<File*>(pFile->fHandle);
    if ((pFile->iSize - pFile->iPos) < iLen)
        iBytesRead = pFile->iSize - pFile->iPos - 1;
    if (iBytesRead <= 0)
        return 0;
    iBytesRead = (int32_t)f->read(pBuf, iBytesRead);
    pFile->iPos = f->position();
    return iBytesRead;
}

int32_t GIFSeekFile(GIFFILE* pFile, int32_t iPosition) {
    File* f = static_cast<File*>(pFile->fHandle);
    f->seek(iPosition);
    pFile->iPos = (int32_t)f->position();
    return pFile->iPos;
}

void ShowGIF(const char* name) {
    DEBUG_PRINTF("ShowGIF: %s\n", name);
    start_tick = millis();

    if (gif.open(name, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
        x_offset = (dma_display->width() - gif.getCanvasWidth()) / 2;
        if (x_offset < 0) x_offset = 0;
        y_offset = (dma_display->height() - gif.getCanvasHeight()) / 2;
        if (y_offset < 0) y_offset = 0;
        
        DEBUG_PRINTF("Successfully opened GIF; Canvas size = %d x %d\n", 
                     gif.getCanvasWidth(), gif.getCanvasHeight());
        
        while (gif.playFrame(true, NULL) && isShowGIF) {
            if ((millis() - start_tick) > 8000) {
                break;
            }
        }
        gif.close();
    }
}

void displayGIF(const char* fileName) {
    clear();
    ShowGIF(fileName);
}

// ============================================================================
// BLE回调类
// ============================================================================

class TextCharacteristicCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        isShowGIF = false;
        std::string value = pCharacteristic->getValue();
        DEBUG_PRINTF("TextCharacteristicCallbacks: %s\n", value.c_str());
        
        auto parts = StringUtils::splitString(value, ',');
        if (parts.size() >= 2) {
            setTextSize(atoi(parts[0].c_str()));
            displayText(parts[1].c_str(), false);
        }
    }
};

class TextScrollCharacteristicCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        isShowGIF = false;
        std::string value = pCharacteristic->getValue();
        DEBUG_PRINTF("TextScrollCharacteristicCallbacks: %s\n", value.c_str());
        
        auto parts = StringUtils::splitString(value, ',');
        if (parts.size() >= 3) {
            setTextSize(atoi(parts[0].c_str()));
            setTextScrollSpeed(atoi(parts[1].c_str()));
            displayText(parts[2].c_str(), true);
        }
    }
};

class GIFCharacteristicCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        DEBUG_PRINTF("GIFCharacteristicCallbacks: %s\n", value.c_str());
        
        isScrollText = false;
        delay(50);
        safeFreeScrollText();
        
        scrollTextContent = MemoryUtils::safeStringDup(value.c_str());
        if (scrollTextContent) {
            gif.begin(LITTLE_ENDIAN_PIXELS);
            isShowGIF = true;
            currentDisplayState = STATE_SHOWING_GIF;
        }
    }
};

class DrawNormalCharacteristicCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        uint8_t* v = pCharacteristic->getData();
        isScrollText = false;
        delay(50);
        safeFreeScrollText();
        clear();
        dma_display->drawBitmap(0, 0, v, 64, 64, myWHITE);
        currentDisplayState = STATE_DRAWING;
    }
};

class FillPixelCharacteristicCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        if (!value.empty()) {
            int values[3];
            int count = StringUtils::parseCommaSeparatedInts(value, values, 3);
            if (count == 3) {
                if (values[2] == 0) {
                    dma_display->writePixel(values[0], values[1], myBLACK);
                } else {
                    dma_display->writePixel(values[0], values[1], myWHITE);
                }
            }
        }
    }
};

class FillScreenCharacteristicCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        DEBUG_PRINTF("FillScreenCharacteristic_recev: %s\n", value.c_str());
        
        int isClear = atoi(value.c_str());
        if (isClear) {
            clear();
        } else {
            dma_display->fillScreen(myWHITE);
        }
    }
};

class BrightnessCharacteristicCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* pCharacteristic) override {
        std::string value = pCharacteristic->getValue();
        DEBUG_PRINTF("BLE brightness recv: %s\n", value.c_str());
        
        int brightness = atoi(value.c_str());
        setLedBrightness(brightness);
    }
};

class MyBLEServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        DEBUG_PRINTLN("BLE Connected");
    }
    
    void onDisconnect(BLEServer* pServer) override {
        DEBUG_PRINTLN("BLE Disconnected");
        setTextSize(1);
        displayText(LED_DEFAULT_TEXT, false);
        pServer->getAdvertising()->start();
    }
};

// ============================================================================
// 初始化函数
// ============================================================================

void initLED() {
    HUB75_I2S_CFG mxconfig(
        PANEL_RES_X,
        PANEL_RES_Y,
        PANEL_CHAIN
    );
    mxconfig.gpio.e = PIN_E;
    mxconfig.gpio.b = PIN_B;
    mxconfig.clkphase = false;
    mxconfig.driver = HUB75_I2S_CFG::FM6124;

    dma_display = new MatrixPanel_I2S_DMA(mxconfig);
    setLedBrightness(LED_DEFAULT_BRIGHTNAESS);

    if (!dma_display->begin()) {
        ErrorHandler::handleInitError(ERROR_INIT_FAILED, "I2S memory allocation failed");
        return;
    }

    // 初始化颜色
    myBLACK = dma_display->color565(0, 0, 0);
    myWHITE = dma_display->color565(255, 255, 255);
    myRED = dma_display->color565(255, 0, 0);
    myGREEN = dma_display->color565(0, 255, 0);
    myBLUE = dma_display->color565(0, 0, 255);

    pinMode(0, INPUT);
    dma_display->setTextColor(myWHITE);
    setTextSize(textSize);
    dma_display->setTextWrap(isTextWrap);
    
    DEBUG_PRINTLN("LED Display initialized successfully");
}

void initBLE() {
    BLEDevice::init(LED_DEVICE_NAME);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyBLEServerCallbacks());
    pService = pServer->createService(BLE_SERVICE_UUID);

    // 创建特征
    BLECharacteristic* pCharacGIF = pService->createCharacteristic(
        BLE_CHARACTERISTIC_GIF_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
    pCharacGIF->setCallbacks(new GIFCharacteristicCallbacks());

    BLECharacteristic* pCharacText = pService->createCharacteristic(
        BLE_CHARACTERISTIC_TEXT_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
    pCharacText->setCallbacks(new TextCharacteristicCallbacks());

    BLECharacteristic* pCharacTextScroll = pService->createCharacteristic(
        BLE_CHARACTERISTIC_TEXT_SCROLL_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE);
    pCharacTextScroll->setCallbacks(new TextScrollCharacteristicCallbacks());

    BLECharacteristic* pCharacDrawNormal = pService->createCharacteristic(
        BLE_CHARACTERISTIC_DRAW_NORMAL_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacDrawNormal->setCallbacks(new DrawNormalCharacteristicCallbacks());

    BLECharacteristic* pCharacFillScreen = pService->createCharacteristic(
        BLE_CHARACTERISTIC_FILL_SCREEN_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacFillScreen->setCallbacks(new FillScreenCharacteristicCallbacks());

    BLECharacteristic* pCharacFillPixel = pService->createCharacteristic(
        BLE_CHARACTERISTIC_FILL_PIXEL_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacFillPixel->setCallbacks(new FillPixelCharacteristicCallbacks());

    BLECharacteristic* pCharacBrightness = pService->createCharacteristic(
        BLE_CHARACTERISTIC_BRIGHTNESS_UUID,
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_NOTIFY);
    pCharacBrightness->setCallbacks(new BrightnessCharacteristicCallbacks());

    pService->start();
    pServer->getAdvertising()->start();
    
    DEBUG_PRINTLN("BLE initialized successfully");
}

// ============================================================================
// Arduino 主函数
// ============================================================================

void setup() {
    Serial.begin(115200);
    DEBUG_PRINTLN("MyLED HUB75E Starting...");
    
    if (!LittleFS.begin(FORMAT_LITTLEFS_IF_FAILED)) {
        ErrorHandler::handleInitError(ERROR_INIT_FAILED, "LittleFS Mount Failed");
        return;
    }
    
    DebugUtils::printMemoryInfo();
    
    initLED();
    initBLE();
    displayText(LED_DEFAULT_TEXT, false);
    
    DEBUG_PRINTLN("Setup completed successfully");
}

void loop() {
    // 更新文本滚动
    if (isScrollText && scrollTextContent) {
        unsigned long now = millis();
        if (now > isAnimationDue) {
            dma_display->flipDMABuffer();
            isAnimationDue = now + scrollTextTimeDelay;
            scrollTextXPosition += scrollXMove;

            dma_display->getTextBounds(scrollTextContent, scrollTextXPosition, scrollTextYPosition, 
                                      &xOne, &yOne, &scrollTextWidth, &scrollTextHeight);
            if (scrollTextXPosition + scrollTextWidth <= 0) {
                scrollTextXPosition = PANEL_RES_X;
            }

            dma_display->setCursor(scrollTextXPosition, scrollTextYPosition);
            dma_display->clearScreen();
            dma_display->printlnUTF8(scrollTextContent);
        }
    }

    // 更新GIF播放
    if (isShowGIF && scrollTextContent) {
        displayGIF(scrollTextContent);
    }
    
    // 定期打印调试信息
    static unsigned long lastDebugTime = 0;
    if (millis() - lastDebugTime > 30000) { // 每30秒打印一次
        DebugUtils::printMemoryInfo();
        DebugUtils::printDisplayState(currentDisplayState);
        lastDebugTime = millis();
    }
} 