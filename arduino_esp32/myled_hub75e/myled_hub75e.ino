#include "config.h"
#include "debug.h"
#include "BLEHandler.h"
#include "esp_task_wdt.h"


// library includes
#include "ESP32-VirtualMatrixPanel-I2S-DMA.h"
#include <string.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include "FS.h"
#include <LittleFS.h>
#include <AnimatedGIF.h>
#define FILESYSTEM LittleFS
#define FORMAT_LITTLEFS_IF_FAILED true


MatrixPanel_I2S_DMA *dma_display = nullptr;

AnimatedGIF gif;
File f;
int x_offset, y_offset;
bool isShowGIF = false;

// BLE处理类实例
BLEHandler* bleHandler = nullptr;

// GIF播放控制变量
bool gifInitialized = false;
unsigned long lastGifFrameTime = 0;
int gifFrameDelay = 100; // 默认帧延迟100ms
bool gifLoopMode = true; // 循环播放模式


// 直接在LED矩阵上绘制图像
void GIFDraw(GIFDRAW *pDraw) {
  uint8_t *s;
  uint16_t *d, *usPalette, usTemp[320];
  int x, y, iWidth;

  iWidth = pDraw->iWidth;
  if (iWidth > dma_display->width())
    iWidth = dma_display->width();

  usPalette = pDraw->pPalette;
  y = pDraw->iY + pDraw->y;  // current line

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
          *d++ = usPalette[c];
          iCount++;
        }
      }            // while looking for opaque pixels
      if (iCount)  // any opaque pixels?
      {
        for (int xOffset = 0; xOffset < iCount; xOffset++) {
          dma_display->drawPixel(x + xOffset, y, usTemp[xOffset]);  // 565 Color Format
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
    // Translate the 8-bit pixels through the RGB565 palette (already byte reversed)
    for (x = 0; x < pDraw->iWidth; x++) {
      dma_display->drawPixel(x, y, usPalette[*s++]);  // color 565
    }
  }
} /* GIFDraw() */


void *GIFOpenFile(const char *fname, int32_t *pSize) {
  DEBUG_PRINT("Playing gif: ");
  DEBUG_PRINT(fname);
  DEBUG_PRINTLN("##");
  f = FILESYSTEM.open(fname);
  if (f) {
    *pSize = f.size();
    return (void *)&f;
  }
  return NULL;
} /* GIFOpenFile() */

void GIFCloseFile(void *pHandle) {
  File *f = static_cast<File *>(pHandle);
  if (f != NULL)
    f->close();
} /* GIFCloseFile() */

int32_t GIFReadFile(GIFFILE *pFile, uint8_t *pBuf, int32_t iLen) {
  int32_t iBytesRead;
  iBytesRead = iLen;
  File *f = static_cast<File *>(pFile->fHandle);
  // Note: If you read a file all the way to the last byte, seek() stops working
  if ((pFile->iSize - pFile->iPos) < iLen)
    iBytesRead = pFile->iSize - pFile->iPos - 1;  // <-- ugly work-around
  if (iBytesRead <= 0)
    return 0;
  iBytesRead = (int32_t)f->read(pBuf, iBytesRead);
  pFile->iPos = f->position();
  return iBytesRead;
} /* GIFReadFile() */

int32_t GIFSeekFile(GIFFILE *pFile, int32_t iPosition) {
  int i = micros();
  File *f = static_cast<File *>(pFile->fHandle);
  f->seek(iPosition);
  pFile->iPos = (int32_t)f->position();
  i = micros() - i;
  //  Serial.printf("Seek time = %d us\n", i);
  return pFile->iPos;
} /* GIFSeekFile() */

unsigned long start_tick = 0;

void ShowGIF(char *name) {
  DEBUG_PRINTF("ShowGIF:%s\n", name);
  start_tick = millis();

  // 检查可用内存
  size_t freeHeap = ESP.getFreeHeap();
  size_t minFreeHeap = ESP.getMinFreeHeap();
  DEBUG_PRINTF("GIF显示前内存状态: 可用 %d 字节, 最小可用 %d 字节\n", freeHeap, minFreeHeap);
  
  // 如果可用内存太少，拒绝显示GIF
  if (freeHeap < GIF_SHOW_MIN_MEMORY) {
    DEBUG_PRINTF("内存不足，无法显示GIF。可用内存: %d 字节\n", freeHeap);
    return;
  }

  if (gif.open(name, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
    x_offset = (dma_display->width() - gif.getCanvasWidth()) / 2;
    if (x_offset < 0) x_offset = 0;
    y_offset = (dma_display->height() - gif.getCanvasHeight()) / 2;
    if (y_offset < 0) y_offset = 0;
    DEBUG_PRINTF("Successfully opened GIF; Canvas size = %d x %d\n", gif.getCanvasWidth(), gif.getCanvasHeight());
    Serial.flush();
    
    // 添加帧计数和内存监控
    int frameCount = 0;
    unsigned long lastMemoryCheck = millis();
    
    // 修改为循环播放，当播放完一帧后重新开始
    while (isShowGIF) {
      // 播放一帧
      if (!gif.playFrame(true, NULL)) {
        // 如果播放完一帧，重新开始播放
        gif.close();
        if (!gif.open(name, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
          DEBUG_PRINTF("无法重新打开GIF文件: %s\n", name);
          break;
        }
        continue;
      }
      
      frameCount++;
      
      // 每10帧检查一次内存状态
      if (frameCount % 10 == 0) {
        size_t currentFreeHeap = ESP.getFreeHeap();
        if (currentFreeHeap < 15000) {  // 降低到15KB
          DEBUG_PRINTF("内存不足警告: 当前可用 %d 字节，停止GIF播放\n", currentFreeHeap);
          break;
        }
        lastMemoryCheck = millis();
      }
      
      // 检查是否超时
      if ((millis() - start_tick) > 8000) {  // we'll get bored after about 8 seconds of the same looping gif
        DEBUG_PRINTF("GIF播放超时，已播放 %d 帧\n", frameCount);
        break;
      }
      
      // 添加短暂延迟，让系统有时间处理其他任务
      delay(1);
    }
    
    DEBUG_PRINTF("GIF播放结束，总共播放 %d 帧\n", frameCount);
    gif.close();
    
    // 显示结束后的内存状态
    size_t endFreeHeap = ESP.getFreeHeap();
    DEBUG_PRINTF("GIF显示后内存状态: 可用 %d 字节\n", endFreeHeap);
  } else {
    DEBUG_PRINTF("无法打开GIF文件: %s\n", name);
  }

} /* ShowGIF() */

void listDir(const char *dir, uint8_t levels) {
  DEBUG_PRINTF("Listing directory: %s\n", dir);

  File root = LittleFS.open(dir);
  if (!root) {
    DEBUG_PRINTLN("Failed to open directory");
    return;
  }
  if (!root.isDirectory()) {
    DEBUG_PRINTLN("Not a directory");
    return;
  }

  File file = root.openNextFile();
  while (file) {
    if (file.isDirectory()) {
      DEBUG_PRINT("  DIR : ");
      DEBUG_PRINTLN(file.name());
      if (levels) {
        listDir(file.name(), levels - 1);  // 递归遍历子目录
      }
    } else {
      DEBUG_PRINT("  FILE: ");
      DEBUG_PRINT(file.name());
      DEBUG_PRINT("  SIZE: ");
      DEBUG_PRINTLN(file.size());
    }
    file = root.openNextFile();
  }
}

// 初始化GIF播放器
bool initGIFPlayer() {
  if (!gifInitialized) {
    // 检查临时GIF文件是否存在
    if (!FILESYSTEM.exists(GIF_FILE)) {
      DEBUG_PRINTLN("GIF文件不存在");
      return false;
    }
    
    // 先清屏，避免显示残留
    clear();
    
    // 打开临时GIF文件
    if (!gif.open(GIF_FILE, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
      DEBUG_PRINTLN("无法打开GIF文件");
      return false;
    }
    
    // 设置居中显示
    x_offset = (dma_display->width() - gif.getCanvasWidth()) / 2;
    if (x_offset < 0) x_offset = 0;
    y_offset = (dma_display->height() - gif.getCanvasHeight()) / 2;
    if (y_offset < 0) y_offset = 0;
    
    gifInitialized = true;
    lastGifFrameTime = millis();
    DEBUG_PRINTF("GIF播放器初始化成功，尺寸: %d x %d\n", gif.getCanvasWidth(), gif.getCanvasHeight());
  }
  return true;
}

// 播放GIF帧
bool playGIFFrame() {
  if (!gifInitialized) {
    return false;
  }
  
  // 检查是否到了播放下一帧的时间
  if (millis() - lastGifFrameTime >= gifFrameDelay) {
    // 播放一帧
    if (!gif.playFrame(true, NULL)) {
      // 播放完整个GIF，重新开始
      if (gifLoopMode) {
        gif.close();
        // 重新开始前清屏，避免显示残留
        clear();
        if (!gif.open(GIF_FILE, GIFOpenFile, GIFCloseFile, GIFReadFile, GIFSeekFile, GIFDraw)) {
          DEBUG_PRINTLN("无法重新打开GIF文件");
          gifInitialized = false;
          return false;
        }
        DEBUG_PRINTLN("GIF重新开始播放");
      } else {
        // 不循环播放，停止
        gifInitialized = false;
        isShowGIF = false;
        return false;
      }
    }
    lastGifFrameTime = millis();
  }
  return true;
}

// 停止GIF播放
void stopGIFPlayer() {
  if (gifInitialized) {
    gif.close();
    gifInitialized = false;
    // 停止播放时清屏，避免显示残留
    clear();
    DEBUG_PRINTLN("GIF播放器已停止");
  }
}
// How fast it scrolls, Smaller == faster
int scrollTextTimeDelay = 20;  
int scrollXMove = -1;          // If positive it would scroll right
// For scrolling Text
unsigned long isAnimationDue;
int scrollTextXPosition = PANEL_RES_X;  // Will start one pixel off screen
int scrollTextYPosition = 0;            // This will center the tex
int16_t xOne, yOne;
uint16_t scrollTextWidth, scrollTextHeight;
int textSize = 1;
bool isTextWrap = false;
bool isScrollText = false;
// 文本滚动速度
int scrollTextSpeed = 1;


// 颜色定义将在initLED()中初始化
uint16_t colorBlack;
uint16_t colorWhite;
uint16_t colorRed;
uint16_t colorGreen;
uint16_t colorBlue;
//// range is 0-255, 0 - 0%, 255 - 100%
void setLedBrightness(int value) {
  dma_display->setBrightness8(value);
}
char *scrollTextContent = nullptr;
void freeScrollText() {
  if (scrollTextContent) {
    free(scrollTextContent);
    scrollTextContent = NULL;
  }
}
void displayText(char *textContent, bool isScroll) {
  DEBUG_PRINT("displayText:");
  DEBUG_PRINT(textContent);
  DEBUG_PRINT(",isScroll:");
  DEBUG_PRINTLN(isScroll);

  isScrollText = false;
  // 防止不同步，导致内促出错
  delay(50);
  freeScrollText();
  clear();
  // 如果滚动
  if (isScroll) {
    isTextWrap = false;
    dma_display->setTextWrap(false);
    int len = strlen(textContent) + 1;  // +1 for the null terminator
    scrollTextContent = (char *)malloc(len * sizeof(char));
    if (scrollTextContent != NULL) {
      strcpy(scrollTextContent, textContent);
    }
  } else {
    dma_display->setCursor(0, 0);
    isTextWrap = true;
    dma_display->setTextWrap(true);
    dma_display->printlnUTF8(textContent);
  }
  isScrollText = isScroll;
}
void clear() {
  dma_display->fillScreen(0x0000); // 直接使用黑色值，避免使用未初始化的变量
}

// BLE回调类已在 BLEHandler.h 中定义，这里不再重复定义

void displayGIF(char *fileName) {
  clear();
  // 非阻塞显示GIF，让主循环处理
  // ShowGIF(fileName);  // 注释掉阻塞调用
}

void setTextScrollSpeed(int speed) {

  scrollTextSpeed = speed;
  switch (scrollTextSpeed) {
    case 1:
      scrollXMove = SCROLL_OFFSET_LOW;
      scrollTextTimeDelay = SCROLL_TIME_DELAY_LOW;
      break;
    case 2:
      scrollXMove = SCROLL_OFFSET_MEDIUM;
      scrollTextTimeDelay = SCROLL_TIME_DELAY_MEDIUM;
      break;
    case 3:
      scrollXMove = SCROLL_OFFSET_FAST;
      scrollTextTimeDelay = SCROLL_TIME_DELAY_FAST;
      break;
    default:
      break;
  }
}
// 初始化蓝牙
void initBLE() {
  printInfo("initBLE", "开始初始化BLE");
  
  // 创建BLE处理类实例
  bleHandler = new BLEHandler(
    dma_display,           // LED显示对象
    &gif,                  // GIF解码器
    setTextSize,           // 设置文本大小函数
    setTextScrollSpeed,    // 设置滚动速度函数
    displayText,           // 显示文本函数
    freeScrollText,        // 释放滚动文本函数
    clear,                 // 清屏函数
    setLedBrightness,      // 设置亮度函数
    setRefreshRate,        // 设置刷新频率函数
    &isScrollText,         // 滚动文本标志
    &isShowGIF            // GIF显示标志
  );
  
  // 初始化BLE
  bleHandler->init();
  
  // 开始广播
  bleHandler->startAdvertising();
  
  printInfo("initBLE", "BLE初始化完成");
}

// 初始化LED
void initLED() {

  HUB75_I2S_CFG mxconfig(
    PANEL_RES_X,  // module width
    PANEL_RES_Y,  // module height
    PANEL_CHAIN   // chain length
  );
  mxconfig.gpio.e = PIN_E;
  mxconfig.gpio.b = PIN_B;
  mxconfig.clkphase = false;
  mxconfig.driver = HUB75_I2S_CFG::FM6124;

  // 创建矩阵对象
  dma_display = new MatrixPanel_I2S_DMA(mxconfig);

  // 设置默认亮度
  setLedBrightness(LED_DEFAULT_BRIGHTNAESS);  

  // 分配内存并启动DMA显示
  if (not dma_display->begin()) {
    printError("initLED", "I2S memory allocation failed");
  }

  pinMode(0, INPUT);
  
  // 初始化颜色定义
  colorBlack = dma_display->color565(0, 0, 0);
  colorWhite = dma_display->color565(255, 255, 255);
  colorRed = dma_display->color565(255, 0, 0);
  colorGreen = dma_display->color565(0, 255, 0);
  colorBlue = dma_display->color565(0, 0, 255);
  
  dma_display->setTextColor(colorWhite);
  setTextSize(textSize);
  dma_display->setTextWrap(isTextWrap);

  setRefreshRate(210);
}

void setTextSize(int size) {

  textSize = size;
  dma_display->setTextSize(textSize);
}

// 设置LED刷新频率
void setRefreshRate(int refreshRate) {
  if (dma_display != nullptr) {
    // 刷新率范围检查 (10Hz - 200Hz)
    if (refreshRate < 10) refreshRate = 10;
    if (refreshRate > 200) refreshRate = 200;
    
    printInfo("setRefreshRate", ("设置刷新频率: " + String(refreshRate) + "Hz").c_str());
    
    // 计算需要的I2S时钟频率
    // 刷新率 = I2S时钟频率 / (像素数 * 颜色深度 * 行数)
    // 对于64x32的屏幕，每行64像素，32行，16位颜色深度
    int totalPixels = PANEL_RES_X * PANEL_RES_Y * PANEL_CHAIN;
    int colorDepth = 16; // 16位颜色
    int rows = PANEL_RES_Y;
    
    // 计算所需的I2S时钟频率
    unsigned long requiredClock = (unsigned long)refreshRate * totalPixels * colorDepth * rows;
    
    printInfo("setRefreshRate", ("计算所需时钟频率: " + String(requiredClock) + "Hz").c_str());
    
    // 注意：实际的I2S时钟频率设置需要在库层面实现
    // 这里只是计算和记录，实际控制需要修改库代码
  }
}

// 获取当前刷新频率
int getCurrentRefreshRate() {
  if (dma_display != nullptr) {
    // 计算当前刷新率
    int totalPixels = PANEL_RES_X * PANEL_RES_Y * PANEL_CHAIN;
    int colorDepth = 16; // 16位颜色
    int rows = PANEL_RES_Y;
    
    // 假设默认I2S时钟频率为10MHz (库默认值)
    unsigned long defaultClock = 10000000; // 10MHz
    int refreshRate = defaultClock / (totalPixels * colorDepth * rows);
    
    return refreshRate;
  }
  return 0;
}
void setup() {

  Serial.begin(SERIAL_BAUD_RATE);
  if (!LittleFS.begin(FORMAT_LITTLEFS_IF_FAILED)) {
    printError("setup", "LittleFS Mount Failed");
    return;
  }
  printInfo("setup", "系统启动");
  
  // 启动时清理GIF相关残留文件和内存
  GIFCharacteristicCallbacks::cleanupOnStartup();
  
  // 配置看门狗，延长超时时间
  esp_task_wdt_init(10, true); // 10秒超时
  esp_task_wdt_add(NULL);
  
  initLED();
  initBLE();
  displayText(LED_DEFAULT_TEXT, false);
}

void loop() {
  // 喂狗，防止看门狗复位
  yield();
  esp_task_wdt_reset();
  
  // 检查图像数据接收超时
  DrawNormalCharacteristicCallbacks::checkTimeout();
  
  // 检查GIF数据接收超时
  GIFCharacteristicCallbacks::checkGIFTimeout();
  
  // 定期检查并清理长时间未使用的GIF资源（每5分钟检查一次）
  static unsigned long lastCleanupCheck = 0;
  if (millis() - lastCleanupCheck > 300000) {  // 5分钟
    lastCleanupCheck = millis();
    // 如果GIF没有在显示且没有在接收数据，清理资源
    // 注意：只有在没有在接收GIF数据时才清理
    if (!isShowGIF && !GIFCharacteristicCallbacks::isReceivingGIF()) {
      DEBUG_PRINTLN("定期清理：清理未使用的GIF资源");
      GIFCharacteristicCallbacks::cleanupAfterDisplay();
    } else {
      DEBUG_PRINTF("定期清理检查：isShowGIF=%d, isReceivingGIF=%d\n", 
                   isShowGIF, GIFCharacteristicCallbacks::isReceivingGIF());
    }
  }

  if (isScrollText && scrollTextContent) {


    unsigned long now = millis();
    if (now > isAnimationDue) {
      // 更新第二缓冲区
      dma_display->flipDMABuffer();

      // 设置滚动时间
      isAnimationDue = now + scrollTextTimeDelay;

      scrollTextXPosition += scrollXMove;

      // 检查文本是否超出屏幕
      dma_display->getTextBounds(scrollTextContent, scrollTextXPosition, scrollTextYPosition, &xOne, &yOne, &scrollTextWidth, &scrollTextHeight);
      if (scrollTextXPosition + scrollTextWidth <= 0) {
        scrollTextXPosition = PANEL_RES_X;
      }

      dma_display->setCursor(scrollTextXPosition, scrollTextYPosition);
      dma_display->clearScreen();

      dma_display->printlnUTF8(scrollTextContent);


    }
  }

  if (isShowGIF) {
    // 初始化GIF播放器（如果需要）
    if (!gifInitialized) {
      // 在开始播放GIF前先清屏，确保没有残留内容
      clear();
      if (!initGIFPlayer()) {
        // 初始化失败，停止GIF显示并清理资源
        isShowGIF = false;
        DEBUG_PRINTLN("GIF播放器初始化失败");
        // 清理资源
        GIFCharacteristicCallbacks::cleanupAfterDisplay();
        return;
      }
    }
    
    // 播放GIF帧
    if (!playGIFFrame()) {
      // 播放失败，停止GIF显示并清理资源
      isShowGIF = false;
      stopGIFPlayer();
      DEBUG_PRINTLN("GIF播放失败，停止显示");
      // 清理资源
      GIFCharacteristicCallbacks::cleanupAfterDisplay();
    }
  } else {
    // 如果不需要显示GIF，停止播放器
    if (gifInitialized) {
      stopGIFPlayer();
    }
  }
}  // end loop
