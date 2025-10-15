#ifndef CLOCK_MANAGER_H
#define CLOCK_MANAGER_H

#include "config.h"
#include "debug.h"
#include <WiFi.h>
#include <time.h>
#include <NTPClient.h>
#include <WiFiUDP.h>

// 前向声明
class MatrixPanel_I2S_DMA;

/**
 * 时钟管理类
 * 负责时钟显示、NTP时间同步等功能
 * 目前WIFI和BLE同时工作导致WIFI无法连接，所以时间使用手机发送过来的事件。
 */
class ClockManager {
private:
    MatrixPanel_I2S_DMA* dma_display;
    WiFiUDP ntpUDP;
    NTPClient* timeClient;
    
    // 时钟配置 - 根据LED矩阵尺寸动态调整
    int CLOCK_CENTER_X;
    int CLOCK_CENTER_Y;
    int CLOCK_RADIUS;
    int DIGITAL_TIME_X;
    int DIGITAL_TIME_Y;
    int DIGITAL_TIME_SIZE;
    int DATE_X;
    int DATE_Y;
    int DATE_SIZE;
    
    // WiFi配置
    const char* wifi_ssid;
    const char* wifi_password;
    
    // 状态变量
    bool isClockMode;
    unsigned long lastTimeUpdate;
    unsigned long wifiConnectionStartTime;
    static const unsigned long TIME_UPDATE_INTERVAL = 500; // 0.5秒更新一次，减少闪烁
    
    // 防闪烁优化
    int lastHour, lastMinute, lastSecond;
    bool needsFullRedraw;
    
    // 手机时间相关（使用时间戳）
    unsigned long phoneTimestamp;
    bool phoneTimeReceived;
    unsigned long lastPhoneTimeUpdate;
    
    // BLE控制函数指针
    void (*stopBLEFunc)();
    void (*startBLEFunc)();
    
public:
    ClockManager(MatrixPanel_I2S_DMA* display);
    ~ClockManager();
    
    // 初始化时钟功能
    bool initClock(const char* ssid, const char* password);
    
    // 根据LED矩阵尺寸初始化时钟布局
    void initClockLayout();
    
    // 设置时钟模式
    void setClockMode(bool enable);
    
    // 更新时钟显示
    void updateClock();
    
    // 检查是否在时钟模式
    bool isInClockMode() const { return isClockMode; }
    
    // 检查WiFi连接状态
    bool isWiFiConnected() const { return wifiConnected; }
    
    // WiFi连接状态（供外部访问）
    bool wifiConnected;
    
    // 设置BLE控制函数
    void setBLEControlFunctions(void (*stopBLE)(), void (*startBLE)());
    
    // 设置手机发送的时间戳
    void setTimestampFromPhone(unsigned long timestamp);
    
private:
    // 连接WiFi
    bool connectWiFi();
    
    // 绘制时钟表盘
    void drawClockFace();
    
    // 绘制时钟指针
    void drawClockHands(int hour, int minute, int second);
    
    // 只清除表盘区域
    void clearClockArea();
    
    // 清除时钟指针区域
    void clearClockHands();
    
    // 绘制数字时间
    void drawDigitalTime(int hour, int minute, int second);
    
    // 绘制日期
    void drawDate(struct tm *ptm);
    
    // 获取当前时间
    void getCurrentTime(int& hour, int& minute, int& second, struct tm*& ptm);
};

#endif // CLOCK_MANAGER_H