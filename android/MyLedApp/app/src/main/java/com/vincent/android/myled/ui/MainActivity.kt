package com.vincent.android.myled.ui

import VTBLECallback
import android.Manifest
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.tbruyelle.rxpermissions2.RxPermissions
import com.vincent.android.myled.R
import com.vincent.android.myled.ble.VTBluetoothUtil
import com.vincent.android.myled.led.LEDController
import com.vincent.android.myled.utils.LED_DEFAULT_BRIGHTNESS
import com.vincent.android.myled.utils.REQUEST_ENABLE_BLUETOOTH
import com.vincent.android.myled.utils.logd
import io.reactivex.functions.Consumer

/**
 * 首页
 *
 */
class MainActivity : VTBaseActivity(), VTBLECallback {
    
    // UI组件
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceAddress: TextView
    private lateinit var tvBrightnessValue: TextView
    private lateinit var etStaticText: EditText
    private lateinit var etScrollText: EditText
    private lateinit var sbBrightness: SeekBar
    private lateinit var btnReconnect: Button

    private lateinit var tvStaticTextSize: TextView
    private lateinit var sbStaticTextSize: SeekBar

    private lateinit var tvScrollTextSize: TextView
    private lateinit var sbScrollTextSize: SeekBar

    private lateinit var tvScrollTextSpeed: TextView
    private lateinit var sbScrollTextSpeed: SeekBar

    // 业务逻辑
    private val ledController: LEDController = LEDController.getInstance()
    private val mRxPermission = RxPermissions(this)
    
    // 状态管理
    private var isConnected = false
    private var currentBrightness = LED_DEFAULT_BRIGHTNESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logd("onCreate")
        setContentView(R.layout.activity_main)
        initView()
        checkBluetoothAndPermissions()
    }

    private fun initView() {
        initTitle(R.string.app_name, false)
        initDeviceInfoViews()
        initBrightnessControl()
        initStaticTextControls()
        initScrollTextControls()
        initActionButtons()
    }

    private fun initDeviceInfoViews() {
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvDeviceAddress = findViewById(R.id.tv_device_address)
        btnReconnect = findViewById<Button>(R.id.btn_reconnect).apply {
            setOnClickListener { reconnect() }
        }
        updateDeviceInfo()
    }

    private fun initBrightnessControl() {
        tvBrightnessValue = findViewById(R.id.tv_brightness_value)
        sbBrightness = findViewById<SeekBar>(R.id.sb_brightness).apply {
            progress = currentBrightness
            setOnSeekBarChangeListener(createBrightnessSeekBarListener())
        }
        updateBrightnessDisplay()
    }

    private fun initStaticTextControls() {
        etStaticText = findViewById(R.id.et_static_text)
        tvStaticTextSize = findViewById(R.id.tv_static_textsize)
        sbStaticTextSize = findViewById<SeekBar>(R.id.sb_static_textsize).apply {
            setOnSeekBarChangeListener(createTextSizeSeekBarListener(tvStaticTextSize))
        }

        findViewById<Button>(R.id.btn_send_static_text).setOnClickListener {
            sendStaticText()
        }
    }

    private fun initScrollTextControls() {
        etScrollText = findViewById(R.id.et_scroll_text)
        tvScrollTextSize = findViewById(R.id.tv_scroll_textsize)
        sbScrollTextSize = findViewById<SeekBar>(R.id.sb_scroll_textsize).apply {
            setOnSeekBarChangeListener(createTextSizeSeekBarListener(tvScrollTextSize))
        }

        tvScrollTextSpeed = findViewById(R.id.tv_scroll_text_speed)
        sbScrollTextSpeed = findViewById<SeekBar>(R.id.sb_scroll_text_speed).apply {
            setOnSeekBarChangeListener(createSpeedSeekBarListener())
        }

        findViewById<Button>(R.id.btn_send_scroll_text).setOnClickListener {
            sendScrollText()
        }
    }

    private fun initActionButtons() {
        findViewById<TextView>(R.id.btn_draw_normal).setOnClickListener {
            startActivity(Intent(this, DrawNormalActivity::class.java))
        }
        
        findViewById<TextView>(R.id.btn_draw_gif).setOnClickListener {
            startActivity(Intent(this, GIFActivity::class.java))
        }
    }

    private fun createBrightnessSeekBarListener() = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            // 实时更新显示，但不立即发送到设备
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            seekBar?.let {
                currentBrightness = it.progress
                updateBrightnessDisplay()
                if (isConnected) {
                    ledController.setBrightness(currentBrightness)
                }
            }
        }
    }

    private fun createTextSizeSeekBarListener(textView: TextView) = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            seekBar?.let {
                textView.text = when (it.progress) {
                    1 -> "小"
                    2 -> "中"
                    3 -> "大"
                    else -> ""
                }
            }
        }
    }

    private fun createSpeedSeekBarListener() = object : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            seekBar?.let {
                tvScrollTextSpeed.text = when (it.progress) {
                    1 -> "慢"
                    2 -> "中"
                    3 -> "快"
                    else -> ""
                }
            }
        }
    }

    private fun sendStaticText() {
        val text = etStaticText.text.trim().toString()
        if (text.isNotEmpty()) {
            if (isConnected) {
                ledController.drawStaticText(text, sbStaticTextSize.progress)
                hideKeyboard(this)
                showToast("静态文字已发送")
            } else {
                showToast("设备未连接")
            }
        } else {
            showToast("请输入文字内容")
        }
    }

    private fun sendScrollText() {
        val text = etScrollText.text.trim().toString()
        if (text.isNotBlank()) {
            if (isConnected) {
                ledController.drawScrollingText(text, sbScrollTextSize.progress, sbScrollTextSpeed.progress)
                hideKeyboard(this)
                showToast("滚动文字已发送")
            } else {
                showToast("设备未连接")
            }
        } else {
            showToast("请输入文字内容")
        }
    }

    private fun checkBluetoothAndPermissions() {
        if (!VTBluetoothUtil.isEnable()) {
            turnOnBluetooth()
        } else {
            checkLocationPermission()
        }
    }

    private fun reconnect() {
        if (isConnected) {
            showToast("设备已连接")
            return
        }
        checkLocationPermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                checkLocationPermission()
            } else {
                showToast(R.string.turn_on_failed)
            }
        }
    }

    @SuppressLint("CheckResult")
    private fun checkLocationPermission() {
        if (!mRxPermission.isGranted(ACCESS_FINE_LOCATION)||!mRxPermission.isGranted(BLUETOOTH_SCAN)||!mRxPermission.isGranted(BLUETOOTH_CONNECT)) {
            mRxPermission.request(ACCESS_FINE_LOCATION,BLUETOOTH_SCAN,BLUETOOTH_CONNECT)
                .subscribe(Consumer { isGranted ->
                    if (isGranted) {
                        connectToDevice()
                    } else {
                        showToast(R.string.require_permission_failed)
                    }
                })
        } else {
            connectToDevice()
        }
    }

    private fun connectToDevice() {
        startLoading("连接中...")
        ledController.connect(this)
    }

    @SuppressLint("MissingPermission")
    private fun turnOnBluetooth() {
        if(!mRxPermission.isGranted(BLUETOOTH_CONNECT)){
            mRxPermission.request(BLUETOOTH_CONNECT)
            return
        }
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun showToast(stringId: Int) {
        Toast.makeText(this, stringId, Toast.LENGTH_SHORT).show()
    }

    private fun updateDeviceInfo() {
        tvDeviceName.text = "设备名称："
        tvDeviceAddress.text = ""
    }

    private fun updateBrightnessDisplay() {
        tvBrightnessValue.text = "$currentBrightness%"
    }

    //////BLE 回调相关///////

    override fun onCheckCharacteristicSuccess() {
        ledController.drawDefault()
        sbBrightness.progress = LED_DEFAULT_BRIGHTNESS
        currentBrightness = LED_DEFAULT_BRIGHTNESS
        updateBrightnessDisplay()
    }

    override fun onDisConnected() {
        runOnUiThread {
            isConnected = false
            updateDeviceInfo()
            showToast(R.string.disconnected)
            btnReconnect.isEnabled = true
        }
    }

    override fun onConnecting() {
        runOnUiThread {
            // 可以在这里显示连接状态
        }
    }

    override fun onScanFailed() {
        runOnUiThread {
            isConnected = false
            updateDeviceInfo()
            stopLoading()
            showToast(R.string.find_no_device)
        }
    }

    override fun onConnected(name: String?, address: String?) {
        runOnUiThread {
            isConnected = true
            name?.let {
                tvDeviceName.text = "设备名称: $it"
            }
            address?.let {
                tvDeviceAddress.text = it
            }

            showToast(R.string.connected)
            stopLoading()
            btnReconnect.isEnabled = false
        }
    }

    override fun writeDataCallback(isSuccess: Boolean) {
        runOnUiThread {
            showToast(if (isSuccess) R.string.write_success else R.string.write_failed)
        }
    }

    private fun hideKeyboard(context: Activity) {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var view = context.currentFocus
        if (view == null) {
            view = View(context)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
