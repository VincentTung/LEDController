#ifndef CONFIG_H
#define CONFIG_H

// ============================================================================
// BLE 配置
// ============================================================================
#define BLE_SERVICE_ADDRESSS "B0:A7:32:FD:02:9E"
#define BLE_SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

// BLE 特征 UUID
#define BLE_CHARACTERISTIC_TEXT_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define BLE_CHARACTERISTIC_TEXT_SCROLL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a3"
#define BLE_CHARACTERISTIC_GIF_UUID "beb5483e-36e1-4688-b7f5-ea07361b26b1"
#define BLE_CHARACTERISTIC_DRAW_NORMAL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a7"
#define BLE_CHARACTERISTIC_DRAW_COLORFUL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a6"
#define BLE_CHARACTERISTIC_FILL_PIXEL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a5"
#define BLE_CHARACTERISTIC_FILL_SCREEN_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a4"
#define BLE_CHARACTERISTIC_BRIGHTNESS_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"

// ============================================================================
// LED 屏幕配置
// ============================================================================
#define PANEL_RES_X 64  // 单个面板模块的像素宽度
#define PANEL_RES_Y 64  // 单个面板模块的像素高度
#define PIN_E 32
#define PIN_B 22

#define NUM_ROWS 1                      // 链式面板的行数
#define NUM_COLS 1                      // 每行的面板数
#define PANEL_CHAIN NUM_ROWS * NUM_COLS // 总的面板链数

// ============================================================================
// 显示配置
// ============================================================================
#define LED_DEFAULT_BRIGHTNAESS 20      // 默认亮度
#define LED_DEFAULT_TEXT "未连接"       // 默认显示文本
#define LED_DEVICE_NAME "搞程序的阿翔"   // BLE设备名称

// ============================================================================
// 文本滚动配置
// ============================================================================
#define SCROLL_TIME_DELAY_LOW 30
#define SCROLL_TIME_DELAY_MEDIUM 30
#define SCROLL_TIME_DELAY_FAST 30

#define SCROLL_OFFSET_LOW -1
#define SCROLL_OFFSET_MEDIUM -2
#define SCROLL_OFFSET_FAST -3

// ============================================================================
// 文件系统配置
// ============================================================================
#define FILESYSTEM LittleFS
#define FORMAT_LITTLEFS_IF_FAILED true
#define GIF_DIR "/gifs"  // GIF文件目录

// ============================================================================
// 颜色定义
// ============================================================================
// 注意：这些颜色需要在dma_display初始化后重新定义
#define COLOR_BLACK_RGB565  0x0000
#define COLOR_WHITE_RGB565  0xFFFF
#define COLOR_RED_RGB565    0xF800
#define COLOR_GREEN_RGB565  0x07E0
#define COLOR_BLUE_RGB565   0x001F

// ============================================================================
// 错误码定义
// ============================================================================
#define ERROR_NONE 0
#define ERROR_INIT_FAILED -1
#define ERROR_MEMORY_ALLOCATION -2
#define ERROR_FILE_NOT_FOUND -3
#define ERROR_BLE_INIT_FAILED -4

// ============================================================================
// 状态定义
// ============================================================================
enum DisplayState {
    STATE_IDLE,
    STATE_SHOWING_TEXT,
    STATE_SCROLLING_TEXT,
    STATE_SHOWING_GIF,
    STATE_DRAWING
};

enum ScrollSpeed {
    SPEED_LOW = 1,
    SPEED_MEDIUM = 2,
    SPEED_FAST = 3
};

// ============================================================================
// 工具宏定义
// ============================================================================
#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))
#define MIN(a, b) ((a) < (b) ? (a) : (b))
#define MAX(a, b) ((a) > (b) ? (a) : (b))

// 安全的内存释放宏
#define SAFE_FREE(ptr) do { \
    if (ptr) { \
        free(ptr); \
        ptr = nullptr; \
    } \
} while(0)

// 调试输出宏
#ifdef DEBUG_MODE
    #define DEBUG_PRINT(x) Serial.print(x)
    #define DEBUG_PRINTLN(x) Serial.println(x)
    #define DEBUG_PRINTF(fmt, ...) Serial.printf(fmt, ##__VA_ARGS__)
#else
    #define DEBUG_PRINT(x)
    #define DEBUG_PRINTLN(x)
    #define DEBUG_PRINTF(fmt, ...)
#endif

#endif // CONFIG_H 