#ifndef CONFIG_H
#define CONFIG_H

// ============================================================================
// 硬件配置
// ============================================================================

// HUB75E LED矩阵配置
#define PANEL_RES_X 128      // LED矩阵宽度
#define PANEL_RES_Y 64      // LED矩阵高度
#define PANEL_CHAIN 1       // 矩阵链长度

// // GPIO引脚定义
// #define ESP32S3 
#ifdef ESP32S3 
 
    #define R1_PIN   15
    #define G1_PIN   42
    #define B1_PIN   41
    #define R2_PIN   40
    #define G2_PIN   39
    #define B2_PIN   38
    #define A_PIN    37
    #define B_PIN    36
    #define C_PIN    35
    #define D_PIN    48
    #define E_PIN    -1 // required for 1/32 scan panels, like 64x64. Any available pin would do, i.e. IO32
    #define LAT_PIN   16
    #define OE_PIN    21
    #define CLK_PIN   47


#else 
     // wroom引脚修改这里
    #define R1_PIN    25
    #define G1_PIN    26
    #define B1_PIN    27
    #define R2_PIN    14
    #define G2_PIN    12
    #define B2_PIN    13

    #define A_PIN     23
    #define B_PIN     22
    #define C_PIN     5
    #define D_PIN     17
    #define E_PIN     32 // IMPORTANT: Change to a valid pin if using a 64x64px panel.
              
    #define LAT_PIN   4
    #define OE_PIN    15
    #define CLK_PIN   16
    
#endif  


// ============================================================================
// 滚动文本配置
// ============================================================================

// 滚动文本延迟 (进一步优化，减少闪烁)
#define SCROLL_TIME_DELAY_LOW 40      // 慢速：40ms (25fps)
#define SCROLL_TIME_DELAY_MEDIUM 20   // 中速：20ms (50fps) 
#define SCROLL_TIME_DELAY_FAST 10     // 快速：10ms (100fps)

// 滚动文本偏移 (优化后，更平滑)
#define SCROLL_OFFSET_LOW -1          // 慢速：每次移动1像素
#define SCROLL_OFFSET_MEDIUM -1       // 中速：每次移动1像素
#define SCROLL_OFFSET_FAST -1         // 快速：每次移动1像素

// ============================================================================
// BLE配置
// ============================================================================

// BLE服务UUID
#define BLE_SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"

// BLE特征值UUID
// 通用控制特征值 - 合并了除GIF外的所有控制功能
#define BLE_CHARACTERISTIC_CONTROL_UUID "beb5483e-36e1-4688-b7f5-ea07361b26c0"
// GIF显示特征值 - 单独保留
#define BLE_CHARACTERISTIC_GIF_UUID "beb5483e-36e1-4688-b7f5-ea07361b26b1"
// 亮度特征值 - 保留用于亮度通知
#define BLE_CHARACTERISTIC_BRIGHTNESS_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a9"
// 设备信息特征值 - 只读（固件版本、分辨率）
#define BLE_CHARACTERISTIC_DEVICE_INFO_UUID "beb5483e-36e1-4688-b7f5-ea07361b26f1"

// BLE设备名称
#define BLE_DEVICE_NAME "MyLED"

// BLE命令类型定义
#define BLE_CMD_TEXT 'T'              // 静态文本命令
#define BLE_CMD_SCROLL 'S'            // 滚动文本命令
#define BLE_CMD_BRIGHTNESS 'B'        // 亮度控制命令
#define BLE_CMD_FILL_SCREEN 'F'       // 全屏填充命令
#define BLE_CMD_FILL_PIXEL 'P'        // 单像素填充命令
#define BLE_CMD_REFRESH_RATE 'R'      // 刷新频率命令
#define BLE_CMD_IMAGE 'I'             // 图片显示命令
#define BLE_CMD_CLOCK 'C'             // 时钟显示命令
#define BLE_CMD_TIMER_GAME 'G'        // 计时游戏命令

// ============================================================================
// 时区配置
// ============================================================================

// 时区偏移（小时）
#define TIMEZONE_OFFSET 8  // 东8区（UTC+8）

// ============================================================================
// 显示配置
// ============================================================================

// 默认显示文本
#define LED_DEFAULT_TEXT "1未连接未连接未连接未连接未连接未连接未连接未连接"

// 亮度配置
#define LED_DEFAULT_BRIGHTNAESS 60    // 默认亮度 (0-255)
#define LED_MIN_BRIGHTNESS 10          // 最小亮度
#define LED_MAX_BRIGHTNESS 255         // 最大亮度

// 刷新率配置
#define LED_DEFAULT_REFRESH_RATE 80    // 默认刷新率 (Hz)
#define LED_MIN_REFRESH_RATE 30        // 最小刷新率 (Hz)
#define LED_MAX_REFRESH_RATE 150       // 最大刷新率 (Hz)


// 文本配置
#define DEFAULT_TEXT_SIZE 1            // 默认字体大小
#define DEFAULT_SCROLL_SPEED 50        // 默认滚动速度 (ms)

// 固件版本
#define FIRMWARE_VERSION "1.0.0"

// 颜色定义（使用不同的名称避免冲突）
#define COLOR_BLACK 0x0000
#define COLOR_WHITE 0xFFFF

// ---------------------------------------------------------------------------
// LED 颜色顺序配置（用于不同面板接线/颜色排列差异）
// 说明：设置 GIF 上屏时 RGB 通道的映射方式
// 选项：
//   COLOR_ORDER_RGB  - 不交换，标准 RGB（R->R, G->G, B->B）
//   COLOR_ORDER_RBG  - 交换 G/B（R->R, G->B, B->G）
//   COLOR_ORDER_GRB  - GRB（R->G, G->R, B->B）
//   COLOR_ORDER_GBR  - GBR（R->G, G->B, B->R）
//   COLOR_ORDER_BRG  - BRG（R->B, G->R, B->G）
//   COLOR_ORDER_BGR  - BGR（R->B, G->G, B->R）
#define COLOR_ORDER_RGB 0
#define COLOR_ORDER_RBG 1
#define COLOR_ORDER_GRB 2
#define COLOR_ORDER_GBR 3
#define COLOR_ORDER_BRG 4
#define COLOR_ORDER_BGR 5

#ifndef LED_COLOR_ORDER
#define LED_COLOR_ORDER COLOR_ORDER_RGB
#endif

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

// PSRAM支持配置
#define ENABLE_PSRAM_SUPPORT 1         // 启用PSRAM支持

// 缓冲区大小
#define TEXT_BUFFER_SIZE 256           // 文本缓冲区大小
#define IMAGE_BUFFER_SIZE 4096         // 图像缓冲区大小

// GIF内存管理配置
#define GIF_MEMORY_CONFIG_H

// 内存阈值配置（单位：字节）
#define GIF_MEMORY_THRESHOLD_DEFAULT     (50 * 1024)    // 默认50KB
#define GIF_MEMORY_THRESHOLD_HIGH        (100 * 1024)   // 高内存时100KB
#define GIF_MEMORY_THRESHOLD_LOW         (20 * 1024)    // 低内存时20KB
#define GIF_MEMORY_THRESHOLD_PSRAM       (500 * 1024)   // PSRAM可用时500KB

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