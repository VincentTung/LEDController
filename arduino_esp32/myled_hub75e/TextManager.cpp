#include "TextManager.h"

TextManager::TextManager(MatrixPanel_I2S_DMA* display) 
    : dma_display(display), scrollTextTimeDelay(20), scrollXMove(-1),
      isAnimationDue(0), scrollTextXPosition(PANEL_RES_X), scrollTextYPosition(0),
      xOne(0), yOne(0), scrollTextWidth(0), scrollTextHeight(0),
      textSize(1), isTextWrap(false), isScrollText(false), scrollTextSpeed(1),
      scrollTextNeedsRedraw(false), lastScrollXPosition(-999), lastDrawTime(0),
      isDrawing(false), scrollTextContent(nullptr),
      colorBlack(0), colorWhite(0), colorRed(0), colorGreen(0), colorBlue(0) {
    
    initColors();
}

TextManager::~TextManager() {
    freeScrollText();
}

void TextManager::initColors() {
    colorBlack = dma_display->color565(0, 0, 0);
    colorWhite = dma_display->color565(255, 255, 255);
    colorRed = dma_display->color565(255, 0, 0);
    colorGreen = dma_display->color565(0, 255, 0);
    colorBlue = dma_display->color565(0, 0, 255);
}

void TextManager::displayText(char *textContent, bool isScroll) {
    dma_display->setTextColor(dma_display->color565(255, 255, 255)); // 白色
    printInfo("TextManager::displayText", ("开始显示文本: " + String(textContent) + ", 滚动: " + String(isScroll)).c_str());

    // 先停止之前的滚动文本
    isScrollText = false;
    // 防止不同步，导致内促出错
    delay(50);
    freeScrollText();
    clear();
    
    // 设置滚动状态
    isScrollText = isScroll;
    printInfo("TextManager::displayText", ("isScrollText设置为: " + String(isScrollText)).c_str());
    
    // 如果滚动
    if (isScroll) {
        isTextWrap = false;
        dma_display->setTextWrap(false);
        int len = strlen(textContent) + 1;  // +1 for the null terminator
        scrollTextContent = (char *)malloc(len * sizeof(char));
        if (scrollTextContent != NULL) {
            strcpy(scrollTextContent, textContent);
            printInfo("TextManager::displayText", ("滚动文本内容已设置: " + String(scrollTextContent)).c_str());
        } else {
            printError("TextManager::displayText", "内存分配失败");
        }
        // 初始化滚动位置
        scrollTextXPosition = PANEL_RES_X;
        scrollTextYPosition = 0;
        isAnimationDue = millis() + scrollTextTimeDelay;
        scrollTextNeedsRedraw = true;
        printInfo("TextManager::displayText", ("滚动位置初始化: X=" + String(scrollTextXPosition) + ", Y=" + String(scrollTextYPosition)).c_str());
    } else {
        dma_display->setCursor(0, 0);
        isTextWrap = true;
        dma_display->setTextWrap(true);
        dma_display->printlnUTF8(textContent);
        printInfo("TextManager::displayText", "静态文本已显示");
    }
}

void TextManager::freeScrollText() {
    if (scrollTextContent) {
        free(scrollTextContent);
        scrollTextContent = nullptr;
    }
}

void TextManager::setTextSize(int size) {
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
    
    textSize = actualTextSize;
    dma_display->setTextSize(textSize);
    
    // 输出调试信息
    if (size >= 1 && size <= 4) {
        printInfo("setTextSize", ("设置文本大小: 档位" + String(size) + " (" + sizeNames[size] + ") -> 实际大小" + String(actualTextSize)).c_str());
    }
}

void TextManager::setTextScrollSpeed(int speed) {
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

void TextManager::updateScrollText() {
    if (isScrollText && scrollTextContent) {
        unsigned long now = millis();
        
        // 位置更新逻辑
        if (now > isAnimationDue) {
            // 设置滚动时间
            isAnimationDue = now + scrollTextTimeDelay;

            // 更新位置
            scrollTextXPosition += scrollXMove;

            // 检查文本是否超出屏幕
            dma_display->getTextBounds(scrollTextContent, scrollTextXPosition, scrollTextYPosition, &xOne, &yOne, &scrollTextWidth, &scrollTextHeight);
            if (scrollTextXPosition + scrollTextWidth <= 0) {
                scrollTextXPosition = PANEL_RES_X;
            }

            // 只有位置真正改变时才标记重绘
            if (scrollTextXPosition != lastScrollXPosition) {
                scrollTextNeedsRedraw = true;
                lastScrollXPosition = scrollTextXPosition;
            }
        }
        
        // 绘制逻辑 - 限制绘制频率，减少闪烁
        if (scrollTextNeedsRedraw && !isDrawing && (now - lastDrawTime) > 8) { // 最小8ms间隔
            isDrawing = true;
            
            // 先切换缓冲区，再清屏绘制
            dma_display->flipDMABuffer();
            dma_display->clearScreen();
            dma_display->setCursor(scrollTextXPosition, scrollTextYPosition);
            dma_display->printlnUTF8(scrollTextContent);
            
            scrollTextNeedsRedraw = false;
            lastDrawTime = now;
            isDrawing = false;
            
            // 添加调试信息
            static unsigned long lastScrollDebugTime = 0;
            if (millis() - lastScrollDebugTime > 2000) { // 每2秒打印一次滚动信息
                lastScrollDebugTime = millis();
                printInfo("updateScrollText", ("滚动中: X=" + String(scrollTextXPosition) + ", 内容=" + String(scrollTextContent)).c_str());
            }
        }
    } else {
        // 添加调试信息
        static unsigned long lastDebugTime = 0;
        if (millis() - lastDebugTime > 1000) { // 每秒打印一次调试信息
            lastDebugTime = millis();
            if (!isScrollText) {
                printInfo("updateScrollText", "isScrollText = false");
            }
            if (!scrollTextContent) {
                printInfo("updateScrollText", "scrollTextContent = null");
            }
        }
    }
}

void TextManager::clear() {
    dma_display->fillScreen(0x0000); // 直接使用黑色值，避免使用未初始化的变量
    dma_display->setTextColor(dma_display->color565(255, 255, 255)); // 白色
}