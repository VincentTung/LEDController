#ifndef BLE_MANAGER_H
#define BLE_MANAGER_H

#include "config.h"
#include "utils.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// ============================================================================
// BLE 回调基类
// ============================================================================
class BaseBLECharacteristicCallbacks : public BLECharacteristicCallbacks {
protected:
    // 通用的字符串解析方法
    int parseCommaSeparatedValues(const std::string& value, int* out, int maxCount) {
        return StringUtils::parseCommaSeparatedInts(value, out, maxCount);
    }
    
    // 通用的错误处理
    void handleBLEError(const char* operation, const char* details = nullptr) {
        DEBUG_PRINTF("BLE Error in %s", operation);
        if (details) {
            DEBUG_PRINTF(": %s", details);
        }
        DEBUG_PRINTLN();
    }
    
    // 通用的成功处理
    void handleBLESuccess(const char* operation) {
        DEBUG_PRINTF("BLE Success: %s\n", operation);
    }
};

// ============================================================================
// BLE 管理器类
// ============================================================================
class BLEManager {
private:
    BLEServer* pServer;
    BLEService* pService;
    bool isConnected;
    DisplayState currentState;
    
    // 特征指针
    BLECharacteristic* pCharacText;
    BLECharacteristic* pCharacTextScroll;
    BLECharacteristic* pCharacGIF;
    BLECharacteristic* pCharacDrawNormal;
    BLECharacteristic* pCharacDrawColorful;
    BLECharacteristic* pCharacFillScreen;
    BLECharacteristic* pCharacFillPixel;
    BLECharacteristic* pCharacBrightness;
    
    // 回调对象
    class TextCharacteristicCallbacks;
    class TextScrollCharacteristicCallbacks;
    class GIFCharacteristicCallbacks;
    class DrawNormalCharacteristicCallbacks;
    class DrawColorfulCharacteristicCallbacks;
    class FillScreenCharacteristicCallbacks;
    class FillPixelCharacteristicCallbacks;
    class BrightnessCharacteristicCallbacks;
    class ServerCallbacks;
    
public:
    BLEManager();
    ~BLEManager();
    
    /**
     * 初始化BLE
     * @return 是否成功
     */
    bool init();
    
    /**
     * 启动BLE服务
     * @return 是否成功
     */
    bool start();
    
    /**
     * 停止BLE服务
     */
    void stop();
    
    /**
     * 检查BLE连接状态
     * @return 是否连接
     */
    bool isBLEConnected() const { return isConnected; }
    
    /**
     * 获取当前显示状态
     * @return 显示状态
     */
    DisplayState getCurrentState() const { return currentState; }
    
    /**
     * 设置当前显示状态
     * @param state 新的显示状态
     */
    void setCurrentState(DisplayState state) { currentState = state; }
    
    /**
     * 发送通知
     * @param characteristic 特征指针
     * @param data 数据
     * @param length 数据长度
     * @return 是否成功
     */
    bool sendNotification(BLECharacteristic* characteristic, const uint8_t* data, size_t length);
    
    /**
     * 发送通知（字符串）
     * @param characteristic 特征指针
     * @param message 消息字符串
     * @return 是否成功
     */
    bool sendNotification(BLECharacteristic* characteristic, const std::string& message);

private:
    /**
     * 创建BLE特征
     * @param uuid 特征UUID
     * @param properties 特征属性
     * @param callbacks 回调对象
     * @return 特征指针
     */
    BLECharacteristic* createCharacteristic(const char* uuid, uint32_t properties, BLECharacteristicCallbacks* callbacks);
    
    /**
     * 设置特征描述符
     * @param characteristic 特征指针
     */
    void setupCharacteristicDescriptor(BLECharacteristic* characteristic);
};

// ============================================================================
// BLE 回调类实现
// ============================================================================

// 静态文本特征回调
class BLEManager::TextCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// 滚动文本特征回调
class BLEManager::TextScrollCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// GIF特征回调
class BLEManager::GIFCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// 黑白画图特征回调
class BLEManager::DrawNormalCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// 彩色画图特征回调
class BLEManager::DrawColorfulCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// 全屏填充特征回调
class BLEManager::FillScreenCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// 单像素填充特征回调
class BLEManager::FillPixelCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// 亮度控制特征回调
class BLEManager::BrightnessCharacteristicCallbacks : public BaseBLECharacteristicCallbacks {
public:
    void onWrite(BLECharacteristic* pCharacteristic) override;
};

// 服务器回调
class BLEManager::ServerCallbacks : public BLEServerCallbacks {
private:
    BLEManager* manager;
    
public:
    ServerCallbacks(BLEManager* mgr) : manager(mgr) {}
    
    void onConnect(BLEServer* pServer) override;
    void onConnect(BLEServer* pServer, esp_ble_gatts_cb_param_t* param) override;
    void onDisconnect(BLEServer* pServer) override;
};

#endif // BLE_MANAGER_H 