#include "config.h"
#include "debug.h"
#include "BLEHandler.h"
#include "GIFManager.h"
#include "TextManager.h"
#include "DisplayManager.h"
#include "ClockManager.h"
#include "esp_task_wdt.h"

// library includes
#include <string.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include "FS.h"
#include <LittleFS.h>
#include <AnimatedGIF.h>
#define FILESYSTEM LittleFS
#define FORMAT_LITTLEFS_IF_FAILED true

// 全局管理类实例
DisplayManager* displayManager = nullptr;
TextManager* textManager = nullptr;
GIFManager* gifManager = nullptr;
BLEHandler* bleHandler = nullptr;
ClockManager* clockManager = nullptr;

// 全局状态变量
bool isShowGIF = false;
bool isClockMode = false;
AnimatedGIF gif;


// 全局状态变量
bool isScrollText = false;
// 初始化蓝牙
void initBLE() {
  printInfo("initBLE", "开始初始化BLE");
  
  // 创建BLE处理类实例
  bleHandler = new BLEHandler(
    displayManager->getDisplay(),  // LED显示对象
    &gif,                          // GIF解码器
    [](int size) { textManager->setTextSize(size); },           // 设置文本大小函数
    [](int speed) { textManager->setTextScrollSpeed(speed); },  // 设置滚动速度函数
    [](char* text, bool scroll) { textManager->displayText(text, scroll); }, // 显示文本函数
    []() { textManager->freeScrollText(); },                    // 释放滚动文本函数
    []() { displayManager->clear(); },                          // 清屏函数
    [](int brightness) { displayManager->setLedBrightness(brightness); }, // 设置亮度函数
    [](int rate) { displayManager->setRefreshRate(rate); },     // 设置刷新频率函数
    [](bool enable) { 
      isClockMode = enable; 
      if (clockManager) {
        clockManager->setClockMode(enable);
      }
    }, // 设置时钟模式函数
    []() { return displayManager->getCurrentBrightness(); },    // 获取当前亮度函数
    &isScrollText,         // 滚动文本标志
    &isShowGIF,           // GIF显示标志
    clockManager          // 时钟管理器
  );
  
  // 初始化BLE
  bleHandler->init();
  
  // 开始广播
  bleHandler->startAdvertising();
  
  printInfo("initBLE", "BLE初始化完成");
}

// 使用debug.h中定义的打印函数，无需重复定义
void setup() {
  Serial.begin(SERIAL_BAUD_RATE);
  delay(2000);
  if (!LittleFS.begin(FORMAT_LITTLEFS_IF_FAILED)) {
    printError("setup", "LittleFS Mount Failed");
    return;
  }
  printInfo("setup", "系统启动");
  
  // PSRAM检测和调试信息
  if (isPSRAMAvailable()) {
    size_t psramSize = getPSRAMSize();
    printInfo("setup", ("PSRAM可用: " + String(psramSize / 1024) + " KB").c_str());
  } else {
    printInfo("setup", "PSRAM不可用，使用内部RAM");
  }
  
  // 内存状态信息
  size_t freeHeap = ESP.getFreeHeap();
  size_t minFreeHeap = ESP.getMinFreeHeap();
  printInfo("setup", ("内部RAM: 可用 " + String(freeHeap / 1024) + " KB, 最小可用 " + String(minFreeHeap / 1024) + " KB").c_str());
  
  // 启动时清理GIF相关残留文件和内存
  GIFCharacteristicCallbacks::cleanupOnStartup();
  
  // 配置看门狗，延长超时时间
  esp_task_wdt_init(10, true); // 10秒超时
  esp_task_wdt_add(NULL);
  
  // 初始化管理类
  displayManager = new DisplayManager();
  if (!displayManager->initLED()) {
    printError("setup", "LED初始化失败");
    return;
  }
  
  textManager = new TextManager(displayManager->getDisplay());
  gifManager = new GIFManager(displayManager->getDisplay(), &gif);
  
  // 先初始化时钟管理器
  clockManager = new ClockManager(displayManager->getDisplay());
  initBLE();
  
  // 检查BLE初始化后的内存状态
  printInfo("setup", ("BLE初始化后内存状态: 可用 " + String(ESP.getFreeHeap()) + " 字节").c_str());
  
  
  textManager->displayText((char*)LED_DEFAULT_TEXT, false);
}

void loop() {
  // 喂狗，防止看门狗复位
  yield();
  esp_task_wdt_reset();
  
  // 检查图像数据接收超时
  ControlCharacteristicCallbacks::checkTimeout();
  
  // 检查GIF数据接收超时
  GIFCharacteristicCallbacks::checkGIFTimeout();
  
  // 检查延迟重置
  GIFCharacteristicCallbacks::checkDelayedReset();
  static unsigned long lastCleanupCheck = 0;
  if (millis() - lastCleanupCheck > 300000) {  // 5分钟
    lastCleanupCheck = millis();
    if (!isShowGIF && !GIFCharacteristicCallbacks::isReceivingGIF()) {
      DEBUG_PRINTLN("定期清理：清理未使用的GIF资源");
      GIFCharacteristicCallbacks::cleanupAfterDisplay();
    } else {
      printInfo("定期清理检查", ("isShowGIF=" + String(isShowGIF) + ", isReceivingGIF=" + String(GIFCharacteristicCallbacks::isReceivingGIF())).c_str());
    }
  }

  // 更新时钟显示
  if (isClockMode && clockManager != nullptr) {
    clockManager->updateClock();
  }
  
  // 更新滚动文本
  if (!isClockMode) {
    textManager->updateScrollText();
  }

  // 处理GIF显示
  if (isShowGIF && !isClockMode) {
    // 初始化GIF播放器（如果需要）
    if (!gifManager->isInitialized()) {
      // 在开始播放GIF前先清屏，确保没有残留内容
      displayManager->clear();
      if (!gifManager->initGIFPlayer()) {
        // 初始化失败，停止GIF显示并清理资源
        isShowGIF = false;
        DEBUG_PRINTLN("GIF播放器初始化失败");
        // 清理资源
        GIFCharacteristicCallbacks::cleanupAfterDisplay();
        return;
      }
    }
    
    // 播放GIF帧
    if (!gifManager->playGIFFrame()) {
      // 播放失败，停止GIF显示并清理资源
      isShowGIF = false;
      gifManager->stopGIFPlayer();
      DEBUG_PRINTLN("GIF播放失败，停止显示");
      // 清理资源
      GIFCharacteristicCallbacks::cleanupAfterDisplay();
    }
  } else {
    // 如果不需要显示GIF，停止播放器
    if (gifManager->isInitialized()) {
      gifManager->stopGIFPlayer();
    }
  }
  
  // 更新计时游戏显示
  if (bleHandler) {
    bleHandler->updateTimerGameDisplay();
  }
  
}  // end loop

