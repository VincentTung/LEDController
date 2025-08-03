package com.vincent.android.myled.led

import VTBLECallback
import android.content.Context
import com.vincent.android.myled.ble.VTBLEController
import com.vincent.android.myled.ble.logd
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_BRIGHTNESS_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_DRAW_NORMAL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_FILL_PIXEL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_FILL_SCREEN_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_GIF_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_TEXT_SCROLL_UUID
import com.vincent.android.myled.utils.LED_CHARACTERISTIC_TEXT_UUID
import com.vincent.android.myled.utils.LED_DEFAULT_BRIGHTNESS
import com.vincent.android.myled.utils.LED_DEFAULT_DISPLAY_TEXT
import com.vincent.android.myled.utils.LED_DEVICE_ADDRESS
import com.vincent.android.myled.utils.LED_MINIMUM_BRIGHTNESS
import com.vincent.android.myled.utils.LED_SERVICE_UUID
import com.vincent.android.myled.utils.logd

/**
 * LED控制器
 *
 */
class LEDController private constructor() {

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private var connectionState = ConnectionState.DISCONNECTED
    private lateinit var mContext: Context
    private lateinit var mBLEController: VTBLEController
    private var isInitialized = false

    fun initBLE(context: Context) {
        if (!isInitialized) {
            mContext = context.applicationContext // 使用applicationContext避免内存泄漏
            mBLEController = VTBLEController(
                mContext, LED_DEVICE_ADDRESS, LED_SERVICE_UUID,
                LED_CHARACTERISTIC_TEXT_UUID
            )
            isInitialized = true
            logd("LEDController initialized")
        }
    }

    fun getConnectionState(): ConnectionState = connectionState

    companion object {
        @Volatile
        private var INSTANCE: LEDController? = null

        fun getInstance(): LEDController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LEDController().also { INSTANCE = it }
            }
        }
    }

    /**
     * LED屏幕连接
     */
    fun connect(callback: VTBLECallback) {
        if (!isInitialized) {
            logd("LEDController not initialized")
            return
        }
        
        connectionState = ConnectionState.CONNECTING
        mBLEController.scan(object : VTBLECallback by callback {
            override fun onConnected(name: String?, address: String?) {
                connectionState = ConnectionState.CONNECTED
                callback.onConnected(name, address)
            }

            override fun onDisConnected() {
                connectionState = ConnectionState.DISCONNECTED
                callback.onDisConnected()
            }

            override fun onScanFailed() {
                connectionState = ConnectionState.ERROR
                callback.onScanFailed()
            }
        })
    }

    /**
     * 显示静态文字
     */
    fun drawStaticText(text: String, fontSize: Int = 1) {
        if (connectionState != ConnectionState.CONNECTED) {
            logd("Device not connected, cannot send text")
            return
        }
        
        if (text.isBlank()) {
            logd("Text is empty")
            return
        }

        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_TEXT_UUID,
            "$fontSize,$text"
        )
    }

    /**
     * 显示滚动文字
     */
    fun drawScrollingText(text: String, fontSize: Int = 1, speed: Int = 1) {
        if (connectionState != ConnectionState.CONNECTED) {
            logd("Device not connected, cannot send scrolling text")
            return
        }
        
        if (text.isBlank()) {
            logd("Text is empty")
            return
        }

        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_TEXT_SCROLL_UUID,
            "$fontSize,$speed,$text"
        )
    }

    /**
     * 显示单色图绘制图
     */
    fun drawNormalCanvas(byteArray: ByteArray) {
        if (connectionState != ConnectionState.CONNECTED) {
            logd("Device not connected, cannot send canvas data")
            return
        }
        
        if (byteArray.isEmpty()) {
            logd("Canvas data is empty")
            return
        }

        mBLEController.sendBytes(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_DRAW_NORMAL_UUID,
            byteArray
        )
    }

    /**
     *  设置亮度
     */
    fun setBrightness(value: Int) {
        if (connectionState != ConnectionState.CONNECTED) {
            logd("Device not connected, cannot set brightness")
            return
        }
        
        val brightness: Int = (255 / 100.0f * value).toInt()
        val finalBrightness = if (brightness == 0) LED_MINIMUM_BRIGHTNESS else brightness
        
        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_BRIGHTNESS_UUID,
            finalBrightness.toString()
        )
    }

    /**
     * 绘制一个像素
     */
    fun draw1Pixel(x: Int, y: Int, color: Int) {
        if (connectionState != ConnectionState.CONNECTED) {
            logd("Device not connected, cannot draw pixel")
            return
        }
        
        val text = "$x,$y,$color"
        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_FILL_PIXEL_UUID,
            text
        )
    }

    /**
     *  填充屏幕
     *   isClear为true的时候黑屏，反之白屏
     */
    fun fillScreen(isClear: Boolean) {
        if (connectionState != ConnectionState.CONNECTED) {
            logd("Device not connected, cannot fill screen")
            return
        }
        
        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_FILL_SCREEN_UUID,
            if (isClear) "1" else "0"
        )
    }

    /**
     *  绘制图片
     */
    fun drawImage(imagePath: String) {
        // TODO: 待实现
        logd("drawImage not implemented yet")
    }
    
    /**
     *  绘制Gif动画
     */
    fun drawGif(gifPath: String) {
        if (connectionState != ConnectionState.CONNECTED) {
            logd("Device not connected, cannot draw GIF")
            return
        }
        
        if (gifPath.isBlank()) {
            logd("GIF path is empty")
            return
        }

        mBLEController.sendText(
            LED_SERVICE_UUID,
            LED_CHARACTERISTIC_GIF_UUID,
            gifPath
        )
    }

    /**
     * 显示彩色绘制图
     */
    fun drawColorfulCanvas(data: IntArray) {
        // TODO: 待实现
        logd("drawColorfulCanvas not implemented yet")
    }

    fun drawDefault() {
        drawStaticText(LED_DEFAULT_DISPLAY_TEXT)
        setBrightness(LED_DEFAULT_BRIGHTNESS)
    }
}