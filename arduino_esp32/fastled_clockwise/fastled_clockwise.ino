/*
 * Clockwise Clock Display using HUB75E Library
 * 使用HUB75E库显示顺时针时钟
 * 
 * 引脚连接配置（HUB75E接口）：
 * ESP32 -> HUB75E
 * GPIO25 -> R1
 * GPIO26 -> G1  
 * GPIO27 -> B1
 * GPIO14 -> R2
 * GPIO12 -> G2
 * GPIO13 -> B2
 * GPIO23 -> A
 * GPIO19 -> B
 * GPIO5  -> C
 * GPIO17 -> D
 * GPIO32 -> E (64x64面板需要)
 * GPIO4  -> LAT
 * GPIO15 -> OE
 * GPIO16 -> CLK
 * GND    -> GND
 * 5V     -> VCC
 */

#include <ESP32-HUB75-MatrixPanel-I2S-DMA.h>
#include <WiFi.h>
#include <time.h>
#include <NTPClient.h>
#include <WiFiUdp.h>

// WiFi配置
const char* ssid = "111";
const char* password = "333";

// NTP配置
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP, "pool.ntp.org", 8*3600, 60000);

// 矩阵配置
#define PANEL_RES_X 64      // LED矩阵宽度
#define PANEL_RES_Y 64      // LED矩阵高度
#define PANEL_CHAIN 1       // 矩阵链长度

// GPIO引脚定义
#define PIN_E 32            // E引脚
#define PIN_B 22            // B引脚

// 默认配置
#define LED_DEFAULT_BRIGHTNAESS 60
#define LED_DEFAULT_REFRESH_RATE 100

// 时钟配置
#define CLOCK_CENTER_X 32
#define CLOCK_CENTER_Y 20
#define CLOCK_RADIUS 16

// 创建矩阵对象
MatrixPanel_I2S_DMA *dma_display = nullptr;

void setup() {
  Serial.begin(115200);
  Serial.println("Clockwise Clock 启动中...");
  
  // 初始化LED矩阵
  initLED();
  
  // 连接WiFi
  connectWiFi();
  
  // 初始化NTP客户端
  timeClient.begin();
  timeClient.update();
  
  Serial.println("时钟初始化完成！");
}

void loop() {
  // 更新NTP时间
  timeClient.update();
  
  // 显示时钟
  displayClock();
  
  delay(1000); // 每秒更新一次
}

void connectWiFi() {
  Serial.print("连接WiFi: ");
  Serial.println(ssid);
  
  WiFi.begin(ssid, password);
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 20) {
    delay(500);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.println("WiFi连接成功!");
    Serial.print("IP地址: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.println();
    Serial.println("WiFi连接失败，使用本地时间");
  }
}

bool initLED() {
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
  dma_display->setBrightness8(LED_DEFAULT_BRIGHTNAESS);

  // 分配内存并启动DMA显示
  if (not dma_display->begin()) {
    Serial.println("I2S memory allocation failed");
    return false;
  }

  pinMode(0, INPUT);
  
  // 设置文本属性
  dma_display->setTextColor(dma_display->color565(255, 255, 255));
  dma_display->setTextSize(2);
  dma_display->setTextWrap(false);

  return true;
}

void displayClock() {
  // 清屏
  dma_display->clearScreen();
  
  // 获取当前时间
  unsigned long epochTime = timeClient.getEpochTime();
  if (epochTime == 0) {
    // 如果NTP失败，使用本地时间
    epochTime = millis() / 1000;
  }
  
  struct tm *ptm = gmtime((time_t *)&epochTime);
  int hour = ptm->tm_hour;
  int minute = ptm->tm_min;
  int second = ptm->tm_sec;
  
  // 绘制时钟表盘
  drawClockFace();
  
  // 绘制时钟指针
  drawClockHands(hour, minute, second);
  
  // 绘制日期
  drawDate(ptm);
  
  // 绘制数字时间
  drawDigitalTime(hour, minute, second);
}

void drawClockFace() {
  // 绘制外圆
  dma_display->drawCircle(CLOCK_CENTER_X, CLOCK_CENTER_Y, CLOCK_RADIUS, dma_display->color565(255, 255, 255));
  
  // 绘制内圆
  dma_display->drawCircle(CLOCK_CENTER_X, CLOCK_CENTER_Y, CLOCK_RADIUS-2, dma_display->color565(100, 100, 100));
  
  // 绘制12个刻度
  for (int i = 0; i < 12; i++) {
    float angle = i * 30.0 * PI / 180.0; // 30度间隔
    int x1 = CLOCK_CENTER_X + (CLOCK_RADIUS - 5) * cos(angle - PI/2);
    int y1 = CLOCK_CENTER_Y + (CLOCK_RADIUS - 5) * sin(angle - PI/2);
    int x2 = CLOCK_CENTER_X + (CLOCK_RADIUS - 2) * cos(angle - PI/2);
    int y2 = CLOCK_CENTER_Y + (CLOCK_RADIUS - 2) * sin(angle - PI/2);
    
    dma_display->drawLine(x1, y1, x2, y2, dma_display->color565(255, 255, 255));
  }
  
  // 绘制中心点
  dma_display->fillCircle(CLOCK_CENTER_X, CLOCK_CENTER_Y, 2, dma_display->color565(255, 255, 255));
}

void drawClockHands(int hour, int minute, int second) {
  // 计算角度（顺时针）
  float hourAngle = (hour % 12) * 30.0 + minute * 0.5; // 小时指针
  float minuteAngle = minute * 6.0; // 分钟指针
  float secondAngle = second * 6.0; // 秒针
  
  // 转换为弧度
  hourAngle = hourAngle * PI / 180.0;
  minuteAngle = minuteAngle * PI / 180.0;
  secondAngle = secondAngle * PI / 180.0;
  
  // 绘制小时指针（红色，较长）
  int hourX = CLOCK_CENTER_X + (CLOCK_RADIUS - 8) * cos(hourAngle - PI/2);
  int hourY = CLOCK_CENTER_Y + (CLOCK_RADIUS - 8) * sin(hourAngle - PI/2);
  dma_display->drawLine(CLOCK_CENTER_X, CLOCK_CENTER_Y, hourX, hourY, dma_display->color565(255, 0, 0));
  
  // 绘制分钟指针（绿色，中等长度）
  int minuteX = CLOCK_CENTER_X + (CLOCK_RADIUS - 4) * cos(minuteAngle - PI/2);
  int minuteY = CLOCK_CENTER_Y + (CLOCK_RADIUS - 4) * sin(minuteAngle - PI/2);
  dma_display->drawLine(CLOCK_CENTER_X, CLOCK_CENTER_Y, minuteX, minuteY, dma_display->color565(0, 255, 0));
  
  // 绘制秒针（蓝色，最长）
  int secondX = CLOCK_CENTER_X + (CLOCK_RADIUS - 2) * cos(secondAngle - PI/2);
  int secondY = CLOCK_CENTER_Y + (CLOCK_RADIUS - 2) * sin(secondAngle - PI/2);
  dma_display->drawLine(CLOCK_CENTER_X, CLOCK_CENTER_Y, secondX, secondY, dma_display->color565(0, 0, 255));
}

void drawDate(struct tm *ptm) {
  // 在时间上方显示日期
  dma_display->setTextColor(dma_display->color565(0, 255, 255));
  dma_display->setTextSize(1);
  
  char dateStr[12];
  sprintf(dateStr, "%04d-%02d-%02d", ptm->tm_year + 1900, ptm->tm_mon + 1, ptm->tm_mday);
  
  int textWidth = strlen(dateStr) * 6; // 每个字符6像素宽
  int startX = (PANEL_RES_X - textWidth) / 2;
  int startY = 45; // 在时间上方
  
  dma_display->setCursor(startX, startY);
  dma_display->print(dateStr);
}

void drawDigitalTime(int hour, int minute, int second) {
  // 在时钟下方显示数字时间
  dma_display->setTextColor(dma_display->color565(255, 255, 0));
  dma_display->setTextSize(1);
  
  char timeStr[10];
  sprintf(timeStr, "%02d:%02d:%02d", hour, minute, second);
  
  int textWidth = strlen(timeStr) * 6; // 每个字符6像素宽
  int startX = (PANEL_RES_X - textWidth) / 2;
  int startY = 55; // 在日期下方
  
  dma_display->setCursor(startX, startY);
  dma_display->print(timeStr);
}
