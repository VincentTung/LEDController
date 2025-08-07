#ifndef CONFIG_H
#define CONFIG_H

// ============================================================================
// 硬件配置
// ============================================================================

// HUB75E LED矩阵配置
#define PANEL_RES_X 64      // LED矩阵宽度
#define PANEL_RES_Y 64      // LED矩阵高度
#define PANEL_CHAIN 2       // 矩阵链长度

// GPIO引脚定义
#define PIN_E 32            // E引脚
#define PIN_B 22            // B引脚


// ============================================================================
// 滚动文本配置
// ============================================================================

// 滚动文本延迟
#define SCROLL_TIME_DELAY_LOW 30
#define SCROLL_TIME_DELAY_MEDIUM 30
#define SCROLL_TIME_DELAY_FAST 30

// 滚动文本偏移
#define SCROLL_OFFSET_LOW -1
#define SCROLL_OFFSET_MEDIUM -2
#define SCROLL_OFFSET_FAST -3

// ============================================================================
// BLE配置
// ============================================================================

// BLE服务UUID
#define BLE_SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

// BLE特征值UUID
#define BLE_CHARACTERISTIC_TEXT_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define BLE_CHARACTERISTIC_TEXT_SCROLL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a3"
#define BLE_CHARACTERISTIC_DRAW_NORMAL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a7"
#define BLE_CHARACTERISTIC_DRAW_COLORFUL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a6"
#define BLE_CHARACTERISTIC_BRIGHTNESS_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"
#define BLE_CHARACTERISTIC_FILL_SCREEN_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a4"
#define BLE_CHARACTERISTIC_FILL_PIXEL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a5"
#define BLE_CHARACTERISTIC_GIF_UUID "beb5483e-36e1-4688-b7f5-ea07361b26b1"
#define BLE_CHARACTERISTIC_REFRESH_RATE_UUID "beb5483e-36e1-4688-b7f5-ea07361b26b2"

// BLE设备名称
#define BLE_DEVICE_NAME "MyLED"

// ============================================================================
// 显示配置
// ============================================================================

// 默认显示文本
#define LED_DEFAULT_TEXT "未连接未连接未连接未连接未连接未连接未连接未连接"

// 亮度配置
#define LED_DEFAULT_BRIGHTNAESS 60    // 默认亮度 (0-255)
#define LED_MIN_BRIGHTNESS 10          // 最小亮度
#define LED_MAX_BRIGHTNESS 255         // 最大亮度

// 刷新频率配置
#define LED_DEFAULT_REFRESH_RATE 100   // 默认刷新频率 (Hz)
#define LED_MIN_REFRESH_RATE 50        // 最小刷新频率 (Hz)
#define LED_MAX_REFRESH_RATE 500       // 最大刷新频率 (Hz)

// 文本配置
#define DEFAULT_TEXT_SIZE 1            // 默认字体大小
#define DEFAULT_SCROLL_SPEED 50        // 默认滚动速度 (ms)

// 颜色定义（使用不同的名称避免冲突）
#define COLOR_BLACK 0x0000
#define COLOR_WHITE 0xFFFF

// ============================================================================
// 数据接收配置
// ============================================================================

// 图像数据接收配置
#define MAX_IMAGE_SIZE 4096            // 最大图像数据大小 (字节)
#define BLE_CHUNK_SIZE 510             // BLE数据块大小，与App端保持一致（MTU-2）
#define BLE_MTU_SIZE 512               // BLE MTU大小，与App端保持一致
#define RECEIVE_TIMEOUT 3000           // 接收超时时间 (ms)
#define HEADER_BUFFER_SIZE 50          // 头信息缓冲区大小

// 64x64图像数据大小 (每个像素1位)
#define IMAGE_64x64_SIZE 512           // 64*64/8 = 512字节

// ============================================================================
// 动画配置
// ============================================================================

// GIF动画配置
#define GIF_FRAME_DELAY 100            // GIF帧延迟 (ms)
#define MAX_GIF_FRAMES 100             // 最大GIF帧数

// 滚动文本配置
#define SCROLL_TEXT_DELAY 50           // 滚动文本延迟 (ms)
#define SCROLL_X_MOVE 1                // 滚动X轴移动距离

// ============================================================================
// 调试配置
// ============================================================================

// 串口配置
#define SERIAL_BAUD_RATE 115200        // 串口波特率

// 调试开关
#define DEBUG_ENABLED 1                // 启用调试输出
#define DEBUG_BLE 1                    // 启用BLE调试
#define DEBUG_IMAGE 1                  // 启用图像调试

// ============================================================================
// 内存配置
// ============================================================================

// 缓冲区大小
#define TEXT_BUFFER_SIZE 256           // 文本缓冲区大小
#define IMAGE_BUFFER_SIZE 4096         // 图像缓冲区大小

// GIF内存管理配置
#define GIF_MEMORY_CONFIG_H

// 内存阈值配置（单位：字节）
#define GIF_MEMORY_THRESHOLD_DEFAULT     (50 * 1024)    // 默认50KB
#define GIF_MEMORY_THRESHOLD_HIGH        (100 * 1024)   // 高内存时100KB
#define GIF_MEMORY_THRESHOLD_LOW         (20 * 1024)    // 低内存时20KB

// 内存检查阈值
#define GIF_DISPLAY_MIN_MEMORY           (30 * 1024)    // 显示GIF最少需要30KB
#define GIF_DISPLAY_FINAL_MIN_MEMORY     (25 * 1024)    // 最终检查最少需要25KB
#define GIF_SHOW_MIN_MEMORY              (25 * 1024)    // ShowGIF最少需要25KB
#define GIF_PLAY_MIN_MEMORY              (15 * 1024)    // 播放时最少需要15KB

// 内存碎片化阈值
#define GIF_MEMORY_FRAGMENTATION_THRESHOLD (10 * 1024)  // 碎片超过10KB时整理
#define GIF_BLE_FRAGMENTATION_THRESHOLD   (15 * 1024)   // BLE初始化时碎片阈值

// 文件大小限制
#define GIF_MAX_FILE_SIZE                (1024 * 1024)  // 最大1MB
#define GIF_MEMORY_MULTIPLIER            (3.0 / 2.0)    // 内存需求倍数（150%）

// 超时配置
#define GIF_RECEIVE_TIMEOUT              (30000)        // 接收超时30秒
#define GIF_PLAY_TIMEOUT                 (8000)         // 播放超时8秒

// 进度报告间隔
#define GIF_PROGRESS_REPORT_INTERVAL     (5)            // 每5个数据块报告一次进度
#define GIF_MEMORY_CHECK_INTERVAL        (10)           // 每10个数据块检查一次内存

// 调试配置
#define GIF_DEBUG_MEMORY_CHECKS          (true)         // 启用内存检查调试
#define GIF_DEBUG_PROGRESS_REPORTS       (true)         // 启用进度报告调试

// ============================================================================
// 错误代码
// ============================================================================

#define ERROR_NONE 0
#define ERROR_MEMORY_ALLOCATION -1
#define ERROR_INVALID_DATA -2
#define ERROR_TIMEOUT -3
#define ERROR_BLE_WRITE -4

// ============================================================================
// 状态定义
// ============================================================================

// 显示状态
enum DisplayState {
    DISPLAY_STATE_TEXT,        // 显示文本
    DISPLAY_STATE_SCROLL,      // 显示滚动文本
    DISPLAY_STATE_IMAGE,       // 显示图像
    DISPLAY_STATE_GIF          // 显示GIF
};

// 接收状态
enum ReceiveState {
    RECEIVE_STATE_IDLE,        // 空闲状态
    RECEIVE_STATE_HEADER,      // 接收头信息
    RECEIVE_STATE_DATA         // 接收数据
};

// ============================================================================
// 文件路径配置
// ============================================================================

// 播放SD卡上的所有GIF文件
#define GIF_DIR "/gifs"  
#define GIF_FILE "/temp.gif"  
#endif // CONFIG_H 