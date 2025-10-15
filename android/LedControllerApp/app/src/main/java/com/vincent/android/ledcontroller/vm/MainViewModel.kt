package com.vincent.android.ledcontroller.vm

import android.widget.SeekBar
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vincent.android.ledcontroller.constants.LedConstants.BRIGHTNESS_MAX_VALUE
import com.vincent.android.ledcontroller.constants.LedConstants.LED_DEFAULT_BRIGHTNESS
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.android.ledcontroller.model.LEDDevice
import com.vincent.library.base.util.logd
import com.vincent.library.ble.logic.VTBLEDevicesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * MainActivity的ViewModel
 * 管理设备状态和UI逻辑
 */
class MainViewModel : ViewModel() {
    companion object {
        private const val TAG = "MainViewModel"
    }

    private val ledController: LEDController = LEDController.Companion.getInstance()
    // 设备状态管理
    private val _deviceState = MutableStateFlow(LEDDevice.Companion.createDefault())
    val deviceState: StateFlow<LEDDevice> = _deviceState.asStateFlow()

    // 亮度管理（仅状态，不持有视图引用）
    private val _brightnessPercent = MutableStateFlow(LED_DEFAULT_BRIGHTNESS)
    val brightnessPercent: StateFlow<Int> = _brightnessPercent.asStateFlow()

    init {
        // 开始观察设备状态变化
        observeDeviceState()
    }

    /**
     * 观察设备状态变化
     */
    private fun observeDeviceState() {
        ledController.ledDevice
            .onEach { device ->
                onDeviceStateChanged(device)
            }
            .launchIn(viewModelScope)
    }

    /**
     * 设备状态变化处理
     */
    private fun onDeviceStateChanged(device: LEDDevice) {
        logd(TAG, "设备状态变化:")
        logd(TAG, "  设备名称: ${device.name}")
        logd(TAG, "  MAC地址: ${device.macAddress}")
        logd(TAG, "  分辨率: ${device.resolution}")
        logd(TAG, "  亮度: ${device.brightness}%")
        logd(TAG, "  固件版本: ${device.firmwareVersion}")
        logd(TAG, "  连接状态: ${device.connectionStateDescription}")
        logd(TAG, "  配对状态: ${if (device.isBonded) "已配对" else "未配对"}")
        logd(TAG, "  MTU: ${device.mtu}")
        logd(TAG, "  PHY: TX=${device.txPhy}, RX=${device.rxPhy}")

        // 更新设备状态
        _deviceState.value = device
        
        // 当设备亮度变化时，更新亮度百分比
        if (device.brightness != _brightnessPercent.value) {
            _brightnessPercent.value = device.brightness
            logd(TAG, "亮度百分比更新: ${device.brightness}%")
        }
    }
    

    /**
     * 检查设备是否已连接
     */
    fun isDeviceConnected(): Boolean = _deviceState.value.isConnected

    /**
     * 获取当前设备状态
     */
    fun getCurrentDeviceState(): LEDDevice = _deviceState.value

    /**
     * 获取设备信息摘要
     */
    fun getDeviceInfoSummary(): String {
        val device = _deviceState.value
        return buildString {
            append("设备信息:\n")
            append("- 名称: ${if (device.name.isNotEmpty()) device.name else "未知"}\n")
            append("- 地址: ${if (device.macAddress.isNotEmpty()) device.macAddress else "未知"}\n")
            append("- 连接状态: ${device.connectionStateDescription}\n")
            append("- 配对状态: ${if (device.isBonded) "已配对" else "未配对"}\n")
            if (device.mtu > 0) append("- MTU: ${device.mtu}\n")
            if (device.txPhy > 0 || device.rxPhy > 0) append("- PHY: TX=${device.txPhy}, RX=${device.rxPhy}\n")
        }
    }

    /**
     * 获取连接质量信息
     */
    fun getConnectionQualityInfo(): String {
        return ledController.getDeviceConnectionQualityInfo()
    }

    /**
     * 清除保存的设备信息
     */
    fun clearSavedDevice() {
        logd(TAG, "=== 清除保存的设备信息 ===")
        VTBLEDevicesManager.clearSavedDevice()
        ledController.reinitializeBLE()
        logd(TAG, "设备信息清除完成")
    }

    /**
     * 连接设备
     */
    fun connect() {
        logd(TAG, "连接设备")
        ledController.connect()
    }

    /**
     * 重新连接设备
     */
    fun reconnect() {
        logd(TAG, "重新连接设备")
        ledController.reconnect()
    }

    // ==================== 亮度管理 ====================

    /**
     * 提供 SeekBar 监听器，由 Activity 设置到视图。
     */
    fun createBrightnessSeekBarListener() = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            seekBar?.let {
                _brightnessPercent.value = it.progress
                if (isDeviceConnected()) {
                    ledController.setNotiValue(_brightnessPercent.value)
                }
            }
        }
    }


    /**
     * 处理从ESP32接收到的亮度值
     */
    fun onBrightnessReceived(brightness: Int) {
        logd(TAG, "=== 收到ESP32的亮度值 ===")
        logd(TAG, "亮度值: $brightness")

        // 将ESP32的亮度值转换为百分比显示
        val percent = (brightness * 100 / BRIGHTNESS_MAX_VALUE).coerceIn(0, 100)
        _brightnessPercent.value = percent

        logd(TAG, "亮度值已更新到界面: ${percent}%")
    }

    /**
     * 获取当前亮度值
     */
    fun getCurrentBrightness(): Int = brightnessPercent.value
    


}