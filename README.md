# LED Controller - ESP32 HUB75E LED矩阵控制器

一个基于ESP32和HUB75E LED矩阵的智能LED显示控制器项目，支持Android App远程控制，实现文字显示、图片绘制、GIF动画播放等功能。

## 📱 项目演示

🎬 **[观看完整演示视频](https://www.bilibili.com/video/BV13NtuezE3H/)**

## 🖼️ 项目展示

### 硬件展示
![ESP32开发板](./art/esp32_board.png)
*ESP32开发板与HUB75E LED矩阵模块*

![LED显示效果](./art/led_display.jpg)
*64x64 LED矩阵显示效果*

### Android应用界面
![应用主界面](./art/app_main.png)
*主界面 - 支持文字显示、亮度调节、设备连接管理*

![绘图功能](./art/app_paint.png)
*绘图功能 - 64x64像素画布，支持实时绘制*

![GIF播放](./art/app_gif_list.png)
*GIF播放 - 支持多种GIF动画播放*

## ✨ 主要特性

### 🎯 核心功能
- **文字显示**: 支持静态文字和滚动文字显示
- **图片绘制**: 64x64像素画布，支持实时绘制
- **GIF动画**: 内置多种GIF动画，支持循环播放
- **亮度调节**: 0-100%亮度调节
- **蓝牙控制**: 通过BLE协议远程控制

### 📱 Android应用特性
- **直观界面**: 简洁易用的用户界面
- **实时控制**: 实时调节亮度和绘制
- **设备管理**: 自动连接和重连功能
- **多语言支持**: 中文界面
- **配置保存**: 自动保存用户偏好设置

### 🔧 硬件特性
- **ESP32主控**: 强大的WiFi+蓝牙双模芯片
- **HUB75E接口**: 标准LED矩阵驱动接口
- **64x64分辨率**: 4096个LED像素点
- **高刷新率**: 流畅的显示效果
- **低功耗设计**: 节能环保

## 🛠️ 技术架构

### 硬件架构
```
ESP32 → HUB75E接口 → LED矩阵
  ↓
蓝牙BLE ← → Android App
```

### 软件架构
- **Arduino固件**: ESP32端控制程序
- **Android应用**: 用户控制界面
- **BLE协议**: 设备通信协议
- **图形处理**: 图像转换和显示算法

## 📦 项目结构

```
LEDController/
├── android/                    # Android应用
│   └── MyLedApp/
│       ├── app/src/main/
│       │   ├── java/          # 应用代码
│       │   ├── res/           # 资源文件
│       │   └── AndroidManifest.xml
│       └── build.gradle       # 构建配置
├── arduino_esp32/             # ESP32固件
│   └── myled_hub75e/
│       ├── myled_hub75e.ino   # 主程序
│       ├── config.h           # 配置文件
│       └── *.cpp/*.h          # 功能模块
├── art/                       # 项目图片
│   ├── app_main.png          # 应用主界面
│   ├── app_paint.png         # 绘图界面
│   ├── app_gif_list.png      # GIF列表
│   ├── esp32_board.png       # ESP32开发板
│   └── led_display.jpg       # LED显示效果
└── README.md                 # 项目说明
```

## 🚀 快速开始

### 硬件准备
1. **ESP32开发板** × 1
2. **HUB75E LED矩阵** (64x64) × 1
3. **连接线** 若干
4. **电源适配器** (5V/3A)

### 软件安装
1. **Arduino IDE** 或 **PlatformIO**
2. **Android Studio** (用于编译Android应用)
3. **Android设备** (Android 6.0+)

### 硬件连接
```
ESP32    →    HUB75E LED矩阵
3.3V     →    VCC
GND      →    GND
GPIO 2   →    A
GPIO 4   →    B
GPIO 16  →    C
GPIO 17  →    D
GPIO 5   →    E
GPIO 18  →    LAT
GPIO 23  →    OE
GPIO 19  →    CLK
GPIO 22  →    R1
GPIO 21  →    G1
GPIO 25  →    B1
GPIO 26  →    R2
GPIO 27  →    G2
GPIO 14  →    B2
```

### 固件烧录
1. 打开Arduino IDE
2. 选择ESP32开发板
3. 打开 `arduino_esp32/myled_hub75e/myled_hub75e.ino`
4. 编译并上传到ESP32

### Android应用安装
1. 使用Android Studio打开 `android/MyLedApp/`
2. 连接Android设备
3. 编译并安装应用

## 📖 使用说明

### 首次使用
1. 打开Android应用
2. 确保蓝牙已开启
3. 点击"重连"按钮
4. 等待设备连接成功

### 文字显示
1. 在"静态文字"输入框中输入文字
2. 调节文字大小（小/中/大）
3. 点击"发送"按钮

### 滚动文字
1. 在"滚动文字"输入框中输入文字
2. 调节文字大小和滚动速度
3. 点击"发送"按钮

### 绘图功能
1. 点击"绘图"按钮进入绘图界面
2. 选择绘制模式（拖拽/填充）
3. 在64x64画布上绘制图案
4. 开启"实时更新"可实时显示到LED

### GIF播放
1. 点击"GIF"按钮进入GIF列表
2. 选择要播放的GIF动画
3. 点击播放按钮

### 亮度调节
1. 在主界面调节亮度滑块
2. 亮度范围：0-100%
3. 设置会自动保存

## 🔧 配置说明

### ESP32配置
在 `arduino_esp32/myled_hub75e/config.h` 中：
```cpp
// LED矩阵配置
#define MATRIX_WIDTH 64
#define MATRIX_HEIGHT 64
#define MATRIX_PANELS 4

// 蓝牙配置
#define DEVICE_NAME "MyLED"
#define SERVICE_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
```

### Android配置
在 `android/MyLedApp/app/src/main/java/com/vincent/android/myled/utils/Constants.kt` 中：
```kotlin
// 设备地址
const val LED_DEVICE_ADDRESS = "B0:A7:32:FD:02:9E"
// 服务UUID
const val LED_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
```

## 🐛 故障排除

### 常见问题

**Q: 设备连接失败**
A: 检查蓝牙是否开启，设备是否在范围内，尝试重启应用

**Q: LED显示异常**
A: 检查硬件连接，确认电源电压稳定

**Q: 应用崩溃**
A: 检查Android版本兼容性，重新安装应用

**Q: 绘图功能无响应**
A: 确认设备已连接，检查实时更新开关


## 🤝 贡献指南

欢迎提交Issue和Pull Request！

### 开发环境
- Arduino IDE 1.8.x 或 PlatformIO
- Android Studio 4.x+
- ESP32开发板
- Android设备

### 代码规范
- 遵循Arduino和Android开发规范
- 添加适当的注释
- 保持代码简洁清晰

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 🙏 致谢

- ESP32 Arduino库
- HUB75E LED矩阵驱动库
- Android BLE库
- 所有贡献者和测试用户

## 📞 联系方式

- **项目主页**: [GitHub Repository](https://github.com/VincentTung/LEDController)
- **演示视频**: [Bilibili](https://www.bilibili.com/video/BV13NtuezE3H/)
- **问题反馈**: [Issues](https://github.com/VincentTung/LEDController/issues)

---

⭐ 如果这个项目对您有帮助，请给我们一个Star！
