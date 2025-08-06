#ifndef DEBUG_H
#define DEBUG_H

#include "config.h"
#include <Arduino.h>
#include <esp_system.h>

// ============================================================================
// 调试宏定义
// ============================================================================

#if DEBUG_ENABLED
    #define DEBUG_PRINT(x) Serial.print(x)
    #define DEBUG_PRINTLN(x) Serial.println(x)
    #define DEBUG_PRINTF(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
    #define DEBUG_PRINT(x)
    #define DEBUG_PRINTLN(x)
    #define DEBUG_PRINTF(fmt, ...)
#endif

#if DEBUG_BLE
    #define BLE_DEBUG_PRINT(x) Serial.print(x)
    #define BLE_DEBUG_PRINTLN(x) Serial.println(x)
    #define BLE_DEBUG_PRINTF(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
    #define BLE_DEBUG_PRINT(x)
    #define BLE_DEBUG_PRINTLN(x)
    #define BLE_DEBUG_PRINTF(fmt, ...)
#endif

#if DEBUG_IMAGE
    #define IMAGE_DEBUG_PRINT(x) Serial.print(x)
    #define IMAGE_DEBUG_PRINTLN(x) Serial.println(x)
    #define IMAGE_DEBUG_PRINTF(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
    #define IMAGE_DEBUG_PRINT(x)
    #define IMAGE_DEBUG_PRINTLN(x)
    #define IMAGE_DEBUG_PRINTF(fmt, ...)
#endif

// ============================================================================
// 调试函数
// ============================================================================

/**
 * 打印错误信息
 */
inline void printError(const char* function, const char* message) {
    DEBUG_PRINTF("[ERROR] %s: %s\n", function, message);
}

/**
 * 打印警告信息
 */
inline void printWarning(const char* function, const char* message) {
    DEBUG_PRINTF("[WARNING] %s: %s\n", function, message);
}

/**
 * 打印信息
 */
inline void printInfo(const char* function, const char* message) {
    DEBUG_PRINTF("[INFO] %s: %s\n", function, message);
}

/**
 * 打印BLE调试信息
 */
inline void printBLEInfo(const char* function, const char* message) {
    BLE_DEBUG_PRINTF("[BLE] %s: %s\n", function, message);
}

/**
 * 打印图像调试信息
 */
inline void printImageInfo(const char* function, const char* message) {
    IMAGE_DEBUG_PRINTF("[IMAGE] %s: %s\n", function, message);
}

/**
 * 打印内存使用情况
 */
inline void printMemoryInfo(const char* function) {
    DEBUG_PRINTF("[MEMORY] %s: Free heap: %d bytes\n", function, ESP.getFreeHeap());
}

/**
 * 打印数据块信息
 */
inline void printChunkInfo(int chunkIndex, int totalChunks, int receivedBytes, int totalBytes) {
    IMAGE_DEBUG_PRINTF("[CHUNK] %d/%d, Bytes: %d/%d\n", 
                       chunkIndex, totalChunks, receivedBytes, totalBytes);
}

/**
 * 打印接收状态
 */
inline void printReceiveState(ReceiveState state) {
    const char* stateNames[] = {"IDLE", "HEADER", "DATA"};
    DEBUG_PRINTF("[STATE] Receive state: %s\n", stateNames[state]);
}

/**
 * 打印显示状态
 */
inline void printDisplayState(DisplayState state) {
    const char* stateNames[] = {"TEXT", "SCROLL", "IMAGE", "GIF"};
    DEBUG_PRINTF("[STATE] Display state: %s\n", stateNames[state]);
}

// ============================================================================
// 性能监控
// ============================================================================

/**
 * 开始性能计时
 */
inline unsigned long startTimer() {
    return millis();
}

/**
 * 结束性能计时并打印
 */
inline void endTimer(const char* function, unsigned long startTime) {
    unsigned long duration = millis() - startTime;
    DEBUG_PRINTF("[PERF] %s took %lu ms\n", function, duration);
}

// ============================================================================
// 数据验证
// ============================================================================

/**
 * 验证数据大小是否合理
 */
inline bool isValidDataSize(int size) {
    return size > 0 && size <= MAX_IMAGE_SIZE;
}

/**
 * 验证图像尺寸是否合理
 */
inline bool isValidImageSize(int width, int height) {
    return width > 0 && width <= PANEL_RES_X && 
           height > 0 && height <= PANEL_RES_Y;
}

/**
 * 验证亮度值是否合理
 */
inline bool isValidBrightness(int brightness) {
    return brightness >= LED_MIN_BRIGHTNESS && brightness <= LED_MAX_BRIGHTNESS;
}

#endif // DEBUG_H 