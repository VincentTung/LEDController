#include "DisplayManager.h"
#include <LittleFS.h>

#define FILESYSTEM LittleFS

DisplayManager::DisplayManager() : dma_display(nullptr), currentBrightness(LED_DEFAULT_BRIGHTNAESS) {
}

DisplayManager::~DisplayManager() {
    if (dma_display != nullptr) {
        delete dma_display;
        dma_display = nullptr;
    }
}

bool DisplayManager::initLED() {
    HUB75_I2S_CFG mxconfig(
        PANEL_RES_X,  // module width
        PANEL_RES_Y,  // module height
        PANEL_CHAIN   // chain length
    );
    
    // 设置所有引脚定义
    mxconfig.gpio.r1 = R1_PIN;
    mxconfig.gpio.g1 = G1_PIN;
    mxconfig.gpio.b1 = B1_PIN;
    mxconfig.gpio.r2 = R2_PIN;
    mxconfig.gpio.g2 = G2_PIN;
    mxconfig.gpio.b2 = B2_PIN;
    mxconfig.gpio.a = A_PIN;
    mxconfig.gpio.b = B_PIN;
    mxconfig.gpio.c = C_PIN;
    mxconfig.gpio.d = D_PIN;
    mxconfig.gpio.e = E_PIN;
    mxconfig.gpio.lat = LAT_PIN;
    mxconfig.gpio.oe = OE_PIN;
    mxconfig.gpio.clk = CLK_PIN;
    
    mxconfig.clkphase = false;
    mxconfig.driver = HUB75_I2S_CFG::FM6124;

    // 创建矩阵对象
    dma_display = new MatrixPanel_I2S_DMA(mxconfig);

    // 设置默认亮度
    setLedBrightness(LED_DEFAULT_BRIGHTNAESS);  

    // 分配内存并启动DMA显示
    if (not dma_display->begin()) {
        ::printError("initLED", "I2S memory allocation failed");
        return false;
    }

    pinMode(0, INPUT);
    
    // 初始化颜色定义
    initColors();
    
    dma_display->setTextColor(dma_display->color565(255, 255, 255));
    dma_display->setTextSize(1);
    dma_display->setTextWrap(true);

    setRefreshRate(LED_DEFAULT_REFRESH_RATE);  // 使用配置文件中的默认刷新率
    return true;
}

void DisplayManager::initColors() {
    // 颜色定义将在需要时通过dma_display对象获取
    // 这里只是占位，实际颜色在initLED中设置
}

void DisplayManager::clear() {
    if (dma_display != nullptr) {
        dma_display->fillScreen(0x0000); // 直接使用黑色值
    }
}

void DisplayManager::setLedBrightness(int value) {
    currentBrightness = value;
    if (dma_display != nullptr) {
        dma_display->setBrightness8(value);
    }
}

void DisplayManager::setRefreshRate(int refreshRate) {
    if (dma_display != nullptr) {
        // 刷新率范围检查  
        // todo 双缓冲解决闪烁
        if (refreshRate < LED_MIN_REFRESH_RATE) refreshRate = LED_MIN_REFRESH_RATE;   // 最低刷新率，避免明显闪烁
        if (refreshRate > LED_MAX_REFRESH_RATE) refreshRate = LED_MAX_REFRESH_RATE;   // 最高刷新率，避免过高功耗
        
        ::printInfo("setRefreshRate", ("设置刷新频率: " + String(refreshRate) + "Hz").c_str());
        
        // 计算需要的I2S时钟频率
        // 刷新率 = I2S时钟频率 / (像素数 * 颜色深度 * 行数)
        // 对于64x32的屏幕，每行64像素，32行，16位颜色深度
        int totalPixels = PANEL_RES_X * PANEL_RES_Y * PANEL_CHAIN;
        int colorDepth = 16; // 16位颜色
        int rows = PANEL_RES_Y;
        
        // 计算所需的I2S时钟频率
        unsigned long requiredClock = (unsigned long)refreshRate * totalPixels * colorDepth * rows;
        
        ::printInfo("setRefreshRate", ("计算所需时钟频率: " + String(requiredClock) + "Hz").c_str());
        
        // 注意：实际的I2S时钟频率设置需要在库层面实现
        // 这里只是计算和记录，实际控制需要修改库代码
    }
}

void DisplayManager::setTextSize(int size) {
    if (dma_display != nullptr) {
        // 限制文本大小范围 (1-4)
        if (size < 1) size = 1;
        if (size > 4) size = 4;
        
        // 将4个档位映射到有效的字体大小范围 (1-4)
        // 由于Adafruit_GFX只支持整数字体大小，我们提供4个有效档位
        int actualTextSize;
        String sizeNames[] = {"", "极小", "小", "中", "大"};
        
        switch (size) {
            case 1: actualTextSize = 1; break;  // 极小 - 最小字体
            case 2: actualTextSize = 2; break;  // 小 - 小字体
            case 3: actualTextSize = 3; break;  // 中 - 中等字体
            case 4: actualTextSize = 4; break;  // 大 - 最大字体
            default: actualTextSize = 1; break;
        }
        
        dma_display->setTextSize(actualTextSize);
        
        // 输出调试信息
        if (size >= 1 && size <= 4) {
            ::printInfo("setTextSize", ("设置文本大小: 档位" + String(size) + " (" + sizeNames[size] + ") -> 实际大小" + String(actualTextSize)).c_str());
        }
    }
}

void DisplayManager::setTextColor(uint16_t color) {
    if (dma_display != nullptr) {
        dma_display->setTextColor(color);
    }
}

void DisplayManager::setTextWrap(bool wrap) {
    if (dma_display != nullptr) {
        dma_display->setTextWrap(wrap);
    }
}

void DisplayManager::displayText(char *textContent, bool isScroll) {
    if (dma_display != nullptr) {
        DEBUG_PRINT("displayText:");
        DEBUG_PRINT(textContent);
        DEBUG_PRINT(",isScroll:");
        DEBUG_PRINTLN(isScroll);

        clear();
        
        if (isScroll) {
            dma_display->setTextWrap(false);
            // 滚动文本的具体实现由TextManager处理
        } else {
            dma_display->setCursor(0, 0);
            dma_display->setTextWrap(true);
            dma_display->printlnUTF8(textContent);
        }
    }
}

void DisplayManager::displayGIF(char *fileName) {
    clear();
    // 非阻塞显示GIF，让主循环处理
    // 具体的GIF显示由GIFManager处理
}

int DisplayManager::getCurrentRefreshRate() const {
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

void DisplayManager::listDir(const char *dir, uint8_t levels) {
    ::printInfo("listDir", ("Listing directory: " + String(dir)).c_str());

    File root = FILESYSTEM.open(dir);
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
