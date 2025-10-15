#include "ClockManager.h"
#include "ESP32-HUB75-MatrixPanel-I2S-DMA.h"

ClockManager::ClockManager(MatrixPanel_I2S_DMA* display) 
    : dma_display(display), timeClient(nullptr), wifi_ssid(nullptr), wifi_password(nullptr),
      isClockMode(false), wifiConnected(false), lastTimeUpdate(0), wifiConnectionStartTime(0),
      lastHour(-1), lastMinute(-1), lastSecond(-1), needsFullRedraw(true),
      phoneTimestamp(0), phoneTimeReceived(false), lastPhoneTimeUpdate(0),
      stopBLEFunc(nullptr), startBLEFunc(nullptr) {
    // 初始化时钟布局
    initClockLayout();
}

ClockManager::~ClockManager() {
    if (timeClient != nullptr) {
        delete timeClient;
        timeClient = nullptr;
    }
}

void ClockManager::initClockLayout() {
    // 根据LED矩阵尺寸调整布局
    if (PANEL_RES_X == 64 && PANEL_RES_Y == 64) {
        // 64x64: 上下布局，放大表盘
        CLOCK_CENTER_X = 32;  // 水平居中
        CLOCK_CENTER_Y = 25;  // 偏上位置
        CLOCK_RADIUS = 20;    // 放大表盘
        DATE_X = 0;           // 不显示日期（64x64空间有限）
        DATE_Y = 0;
        DATE_SIZE = 1;
        DIGITAL_TIME_X = 0;   // 水平居中
        DIGITAL_TIME_Y = 48;  // 表盘下方，进一步优化位置
        DIGITAL_TIME_SIZE = 1; // 保持字体大小
        } else if (PANEL_RES_X == 128 && PANEL_RES_Y == 64) {
        // 128x64: 左右布局，右侧上部分显示日期，下部分显示时间
        CLOCK_CENTER_X = 22;  // 左侧位置，进一步左移
        CLOCK_CENTER_Y = 32;  // 垂直居中
        CLOCK_RADIUS = 18;    // 进一步缩小表盘
        DATE_X = 45;          // 右侧日期位置
        DATE_Y = 10;          // 右侧上部分，再上移4像素
        DATE_SIZE = 1;        // 日期字号1
        DIGITAL_TIME_X = 45;  // 右侧时间位置
        DIGITAL_TIME_Y = 32;  // 右侧下部分，下移4像素
        DIGITAL_TIME_SIZE = 2; // 时间字号2
         } else {
        // 默认布局（保持原有设计）
        CLOCK_CENTER_X = 32;
        CLOCK_CENTER_Y = 20;
        CLOCK_RADIUS = 16;
        DATE_X = 0;           // 不显示日期
        DATE_Y = 0;
        DATE_SIZE = 1;
        DIGITAL_TIME_X = 0;
        DIGITAL_TIME_Y = 40;
        DIGITAL_TIME_SIZE = 1;
       
    }
}

bool ClockManager::initClock(const char* ssid, const char* password) {
    wifi_ssid = ssid;
    wifi_password = password;
    printInfo("ClockManager", "初始化时钟功能");
    wifiConnected = false;
    lastTimeUpdate = 0;
    return true;
}

void ClockManager::setBLEControlFunctions(void (*stopBLE)(), void (*startBLE)()) {
    stopBLEFunc = stopBLE;
    startBLEFunc = startBLE;
}

void ClockManager::setTimestampFromPhone(unsigned long timestamp) {
    phoneTimestamp = timestamp;
    phoneTimeReceived = true;
    lastPhoneTimeUpdate = millis();
    
    // 显示UTC时间和东8区时间对比
    time_t utcTime = (time_t)timestamp;
    time_t localTime = utcTime + TIMEZONE_OFFSET * 3600;
    
    struct tm* utcInfo = gmtime(&utcTime);
    struct tm* localInfo = gmtime(&localTime);
    
    printInfo("ClockManager", ("收到手机时间戳: " + String(timestamp)).c_str());
    printInfo("ClockManager", ("UTC时间: " + String(utcInfo->tm_hour) + ":" + String(utcInfo->tm_min) + ":" + String(utcInfo->tm_sec)).c_str());
    printInfo("ClockManager", ("东8区时间: " + String(localInfo->tm_hour) + ":" + String(localInfo->tm_min) + ":" + String(localInfo->tm_sec)).c_str());
}

void ClockManager::setClockMode(bool enable) {
    isClockMode = enable;
    if (enable) {
        printInfo("ClockManager", "启用时钟模式");
        needsFullRedraw = true; // 启用时钟时触发完全重绘
        if (phoneTimeReceived) {
            printInfo("ClockManager", "已收到手机时间数据，开始显示时钟");
        } else {
            printInfo("ClockManager", "等待手机发送时间数据...");
        }
    } else {
        printInfo("ClockManager", "禁用时钟模式");
        if (dma_display != nullptr) {
            dma_display->clearScreen();
        }
        // 重置状态
        lastHour = -1;
        lastMinute = -1;
        lastSecond = -1;
    }
}

void ClockManager::updateClock() {
    if (!isClockMode || dma_display == nullptr) return;
    
    // 检查更新间隔
    unsigned long currentTime = millis();
    if (currentTime - lastTimeUpdate < TIME_UPDATE_INTERVAL) {
        return;
    }
    lastTimeUpdate = currentTime;
    
    // 获取当前时间（使用手机发送的时间或模拟时间）
    int hour, minute, second;
    struct tm* ptm;
    getCurrentTime(hour, minute, second, ptm);
    
    // 检查分钟是否变化
    bool minuteChanged = (minute != lastMinute);
    bool hourChanged = (hour != lastHour);
    
    if (minuteChanged || hourChanged || needsFullRedraw) {
        // 分钟或小时变化时，全部清屏
        dma_display->clearScreen();
        needsFullRedraw = false;
        
        // 绘制时钟表盘
        drawClockFace();
        
        // 在128x64布局中绘制日期
        if (PANEL_RES_X == 128 && PANEL_RES_Y == 64 && DATE_X > 0) {
            drawDate(ptm);
        }
        
        // 绘制数字时间
        drawDigitalTime(hour, minute, second);
    } else {
        // 只有秒数变化，只清除表盘区域
        clearClockArea();
        
        // 绘制时钟表盘
        drawClockFace();
        
        // 绘制数字时间（可能包含秒数变化）
        drawDigitalTime(hour, minute, second);
    }
    
    // 绘制时钟指针（总是绘制，因为指针位置会变化）
    drawClockHands(hour, minute, second);
    
    // 更新上次的时间值
    lastHour = hour;
    lastMinute = minute;
    lastSecond = second;
}

bool ClockManager::connectWiFi() {
    if (wifi_ssid == nullptr || wifi_password == nullptr) {
        printError("ClockManager", "WiFi配置无效");
        return false;
    }
    
    // 如果已经连接，直接返回成功
    if (WiFi.status() == WL_CONNECTED) {
        wifiConnected = true;
        printInfo("ClockManager", "WiFi已连接!");
        printInfo("ClockManager", ("IP地址: " + WiFi.localIP().toString()).c_str());
        return true;
    }
    
    // 如果还没有开始连接，开始连接
    if (WiFi.status() == WL_DISCONNECTED) {
        printInfo("ClockManager", ("开始连接WiFi: " + String(wifi_ssid)).c_str());
        printInfo("ClockManager", ("WiFi密码长度: " + String(strlen(wifi_password))).c_str());
        
        // 设置WiFi模式
        WiFi.mode(WIFI_STA);
        
        // 先扫描可用的WiFi网络
        printInfo("ClockManager", "扫描可用的WiFi网络...");
        int n = WiFi.scanNetworks();
        if (n == 0) {
            printError("ClockManager", "未找到任何WiFi网络");
        } else {
            printInfo("ClockManager", ("找到 " + String(n) + " 个WiFi网络").c_str());
            bool foundTarget = false;
            for (int i = 0; i < n; ++i) {
                String ssid = WiFi.SSID(i);
                int rssi = WiFi.RSSI(i);
                printInfo("ClockManager", ("  " + String(i+1) + ": " + ssid + " (RSSI: " + String(rssi) + ")").c_str());
                if (ssid == wifi_ssid) {
                    foundTarget = true;
                    printInfo("ClockManager", ("  -> 找到目标网络: " + ssid).c_str());
                }
            }
            if (!foundTarget) {
                printError("ClockManager", ("未找到目标网络: " + String(wifi_ssid)).c_str());
            }
        }
        
        // 开始连接
        WiFi.begin(wifi_ssid, wifi_password);
        wifiConnectionStartTime = millis();
        
        printInfo("ClockManager", "WiFi连接已启动，等待连接...");
        return false; // 连接需要时间，返回false
    }
    
    // 检查连接超时（30秒）
    if (millis() - wifiConnectionStartTime > 30000) {
        printError("ClockManager", "WiFi连接超时，将使用系统时间");
        WiFi.disconnect();
        wifiConnected = false; // 标记为连接失败，不再尝试连接
        return false;
    }
    
    // 检查连接状态
    if (WiFi.status() == WL_CONNECTED) {
        wifiConnected = true;
        printInfo("ClockManager", "WiFi连接成功!");
        printInfo("ClockManager", ("IP地址: " + WiFi.localIP().toString()).c_str());
        return true;
    }
    
    // 打印当前WiFi状态用于调试
    static unsigned long lastStatusPrint = 0;
    if (millis() - lastStatusPrint > 5000) { // 每5秒打印一次状态
        lastStatusPrint = millis();
        String statusStr = "WiFi状态: ";
        switch (WiFi.status()) {
            case WL_IDLE_STATUS: statusStr += "空闲"; break;
            case WL_NO_SSID_AVAIL: statusStr += "未找到SSID"; break;
            case WL_SCAN_COMPLETED: statusStr += "扫描完成"; break;
            case WL_CONNECTED: statusStr += "已连接"; break;
            case WL_CONNECT_FAILED: statusStr += "连接失败"; break;
            case WL_CONNECTION_LOST: statusStr += "连接丢失"; break;
            case WL_DISCONNECTED: statusStr += "已断开"; break;
            default: statusStr += "未知(" + String(WiFi.status()) + ")"; break;
        }
        printInfo("ClockManager", statusStr.c_str());
    }
    
    return false; // 仍在连接中
}

void ClockManager::drawClockFace() {
    // 绘制外圆
    dma_display->drawCircle(CLOCK_CENTER_X, CLOCK_CENTER_Y, CLOCK_RADIUS, dma_display->color565(255, 255, 255));
    
    // 绘制内圆
    dma_display->drawCircle(CLOCK_CENTER_X, CLOCK_CENTER_Y, CLOCK_RADIUS-2, dma_display->color565(100, 100, 100));
    
    // 根据表盘大小动态调整刻度长度
    int tickOuter = CLOCK_RADIUS - (CLOCK_RADIUS / 8);  // 刻度外端
    int tickInner = CLOCK_RADIUS - (CLOCK_RADIUS / 4);  // 刻度内端
    
    // 绘制12个刻度
    for (int i = 0; i < 12; i++) {
        float angle = i * 30.0 * PI / 180.0; // 30度间隔
        int x1 = CLOCK_CENTER_X + tickInner * cos(angle - PI/2);
        int y1 = CLOCK_CENTER_Y + tickInner * sin(angle - PI/2);
        int x2 = CLOCK_CENTER_X + tickOuter * cos(angle - PI/2);
        int y2 = CLOCK_CENTER_Y + tickOuter * sin(angle - PI/2);
        
        dma_display->drawLine(x1, y1, x2, y2, dma_display->color565(255, 255, 255));
    }
    
    // 绘制中心点（根据表盘大小调整）
    int centerRadius = (CLOCK_RADIUS > 20) ? 3 : 2;
    dma_display->fillCircle(CLOCK_CENTER_X, CLOCK_CENTER_Y, centerRadius, dma_display->color565(255, 255, 255));
}

void ClockManager::drawClockHands(int hour, int minute, int second) {
    // 计算角度（顺时针）
    float hourAngle = (hour % 12) * 30.0 + minute * 0.5; // 小时指针
    float minuteAngle = minute * 6.0; // 分钟指针
    float secondAngle = second * 6.0; // 秒针
    
    // 转换为弧度
    hourAngle = hourAngle * PI / 180.0;
    minuteAngle = minuteAngle * PI / 180.0;
    secondAngle = secondAngle * PI / 180.0;
    
    // 根据表盘大小动态调整指针长度
    int hourLength = CLOCK_RADIUS - (CLOCK_RADIUS / 3);    // 小时指针长度
    int minuteLength = CLOCK_RADIUS - (CLOCK_RADIUS / 6);  // 分钟指针长度
    int secondLength = CLOCK_RADIUS - 2;                   // 秒针长度
    
    // 绘制小时指针（红色，较长）
    int hourX = CLOCK_CENTER_X + hourLength * cos(hourAngle - PI/2);
    int hourY = CLOCK_CENTER_Y + hourLength * sin(hourAngle - PI/2);
    dma_display->drawLine(CLOCK_CENTER_X, CLOCK_CENTER_Y, hourX, hourY, dma_display->color565(255, 0, 0));
    
    // 绘制分钟指针（绿色，中等长度）
    int minuteX = CLOCK_CENTER_X + minuteLength * cos(minuteAngle - PI/2);
    int minuteY = CLOCK_CENTER_Y + minuteLength * sin(minuteAngle - PI/2);
    dma_display->drawLine(CLOCK_CENTER_X, CLOCK_CENTER_Y, minuteX, minuteY, dma_display->color565(0, 255, 0));
    
    // 绘制秒针（蓝色，最长）
    int secondX = CLOCK_CENTER_X + secondLength * cos(secondAngle - PI/2);
    int secondY = CLOCK_CENTER_Y + secondLength * sin(secondAngle - PI/2);
    dma_display->drawLine(CLOCK_CENTER_X, CLOCK_CENTER_Y, secondX, secondY, dma_display->color565(0, 0, 255));
}

void ClockManager::clearClockArea() {
    // 只清除表盘区域，保留数字时间区域
    int clearRadius = CLOCK_RADIUS + 3; // 稍大于表盘半径，确保完全清除指针
    
    // 用黑色填充表盘区域
    for (int y = CLOCK_CENTER_Y - clearRadius; y <= CLOCK_CENTER_Y + clearRadius; y++) {
        for (int x = CLOCK_CENTER_X - clearRadius; x <= CLOCK_CENTER_X + clearRadius; x++) {
            if (x >= 0 && x < PANEL_RES_X && y >= 0 && y < PANEL_RES_Y) {
                int dx = x - CLOCK_CENTER_X;
                int dy = y - CLOCK_CENTER_Y;
                if (dx * dx + dy * dy <= clearRadius * clearRadius) {
                    dma_display->drawPixel(x, y, dma_display->color565(0, 0, 0));
                }
            }
        }
    }
}

void ClockManager::drawDigitalTime(int hour, int minute, int second) {
    // 根据布局显示数字时间
    dma_display->setTextColor(dma_display->color565(255, 255, 255)); // 白色
    dma_display->setTextSize(DIGITAL_TIME_SIZE);
    
    char timeStr[6];
    sprintf(timeStr, "%02d:%02d", hour, minute);
    
    // 使用Adafruit_GFX库的精确文本宽度计算
    int16_t x1, y1;
    uint16_t w, h;
    dma_display->getTextBounds(timeStr, 0, 0, &x1, &y1, &w, &h);
    int textWidth = w; // 使用库函数计算的精确宽度
    int startX, startY;
    
    if (DIGITAL_TIME_X == 0) {
        // 居中显示（64x64布局）
        startX = (PANEL_RES_X - textWidth) / 2;
        startY = DIGITAL_TIME_Y - (8 * DIGITAL_TIME_SIZE) / 2; // 垂直居中
        
        // 调试信息：打印居中计算
        static unsigned long lastDebugTime = 0;
        if (millis() - lastDebugTime > 5000) { // 每5秒打印一次
            lastDebugTime = millis();
            printInfo("drawDigitalTime", ("时间文本居中计算: 屏幕宽度=" + String(PANEL_RES_X) + 
                                        ", 文本宽度=" + String(textWidth) + 
                                        ", 起始X=" + String(startX) + 
                                        ", 结束X=" + String(startX + textWidth) +
                                        ", 时间=" + String(timeStr)).c_str());
        }
    } else {
        // 指定位置显示（128x64布局）
        startX = DIGITAL_TIME_X;
        startY = DIGITAL_TIME_Y - (8 * DIGITAL_TIME_SIZE) / 2; // 垂直居中
        
        // 边界检查：确保文本不会超出屏幕右边界
        int maxX = PANEL_RES_X - textWidth;
        if (startX > maxX) {
            startX = maxX; // 调整到屏幕边界内
            printInfo("drawDigitalTime", ("文本位置调整到边界内: " + String(startX)).c_str());
        }
    }
    
    dma_display->setCursor(startX, startY);
    dma_display->print(timeStr);
}

void ClockManager::drawDate(struct tm *ptm) {
    // 根据布局显示日期
    dma_display->setTextColor(dma_display->color565(255, 255, 255)); // 白色
    dma_display->setTextSize(DATE_SIZE);
    
    char dateStr[12];
    sprintf(dateStr, "%04d-%02d-%02d", ptm->tm_year + 1900, ptm->tm_mon + 1, ptm->tm_mday);
    
    int charWidth = 6 * DATE_SIZE; // 根据字体大小调整字符宽度
    int textWidth = strlen(dateStr) * charWidth;
    int startX, startY;
    
    if (DATE_X == 0) {
        // 居中显示（默认布局）
        startX = (PANEL_RES_X - textWidth) / 2;
        startY = 5; // 屏幕最上方
    } else {
        // 指定位置显示（128x64布局）
        startX = DATE_X;
        startY = DATE_Y;
    }
    
    dma_display->setCursor(startX, startY);
    dma_display->print(dateStr);
}

void ClockManager::getCurrentTime(int& hour, int& minute, int& second, struct tm*& ptm) {
    // 优先使用手机发送的时间戳，一旦收到就持续使用
    if (phoneTimeReceived) {
        // 计算当前时间戳（基于手机时间戳 + 经过的时间）
        unsigned long elapsedMs = millis() - lastPhoneTimeUpdate;
        unsigned long currentTimestamp = phoneTimestamp + (elapsedMs / 1000);
        
        // 转换为东8区时间（UTC+8）
        time_t timeValue = (time_t)currentTimestamp;
        timeValue += TIMEZONE_OFFSET * 3600; // 加时区偏移小时数
        
        struct tm* timeInfo = gmtime(&timeValue); // 使用gmtime因为已经手动加了时区偏移
        
        hour = timeInfo->tm_hour;
        minute = timeInfo->tm_min;
        second = timeInfo->tm_sec;
        ptm = timeInfo;
        return;
    }
    
    // 如果没有手机时间，使用模拟时间
    static unsigned long startTime = millis();
    unsigned long elapsedSeconds = (millis() - startTime) / 1000;

    unsigned long baseTime = 1704067200; // 2025-09-22 11:32:00 UTC
    unsigned long epochTime = baseTime + elapsedSeconds;
    
    ptm = gmtime((time_t *)&epochTime);
    hour = ptm->tm_hour;
    minute = ptm->tm_min;
    second = ptm->tm_sec;
}