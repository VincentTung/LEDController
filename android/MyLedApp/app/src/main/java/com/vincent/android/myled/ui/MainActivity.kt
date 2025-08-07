package com.vincent.android.myled.ui

import VTBLECallback
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import com.tbruyelle.rxpermissions2.RxPermissions
import com.vincent.android.myled.R
import com.vincent.android.myled.ble.VTBluetoothUtil
import com.vincent.android.myled.led.LEDController
import com.vincent.android.myled.utils.DeviceManager
import com.vincent.android.myled.utils.LED_DEFAULT_BRIGHTNESS
import com.vincent.android.myled.utils.REQUEST_ENABLE_BLUETOOTH
import com.vincent.android.myled.utils.ToastUtil
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
    private lateinit var btnPhyInfo: Button

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
        
        // 初始化DeviceManager
        DeviceManager.init(this)
        
        initView()
        
        // 显示保存的设备信息（如果有的话）
        updateDeviceInfo()
        
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
            setOnLongClickListener { 
                clearSavedDevice()
                true
            }
        }
        
        btnPhyInfo = findViewById<Button>(R.id.btn_phy_info).apply {
            setOnClickListener { showPhyInfo() }
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
        
        // 添加配对按钮（长按重连按钮显示配对信息）
        btnReconnect.setOnLongClickListener {
            showBondingDialog()
            true
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
                ToastUtil.show(this, "静态文字已发送")
            } else {
                ToastUtil.show(this, "设备未连接")
            }
        } else {
            ToastUtil.show(this, "请输入文字内容")
        }
    }

    private fun sendScrollText() {
        val text = etScrollText.text.trim().toString()
        if (text.isNotBlank()) {
            if (isConnected) {
                ledController.drawScrollingText(text, sbScrollTextSize.progress, sbScrollTextSpeed.progress)
                hideKeyboard(this)
                ToastUtil.show(this, "滚动文字已发送")
            } else {
                ToastUtil.show(this, "设备未连接")
            }
        } else {
            ToastUtil.show(this, "请输入文字内容")
        }
    }

    private fun checkBluetoothAndPermissions() {
        if (!VTBluetoothUtil.isEnable(this)) {
            turnOnBluetooth()
        } else {
            checkLocationPermission()
        }
    }

    private fun reconnect() {
        if (isConnected) {
            logd("=== MainActivity: 设备已连接，无需重连 ===")
            ToastUtil.show(this, R.string.device_already_connected)
            return
        }
        
        logd("=== MainActivity: 开始重连流程 ===")
        
        // 检查是否有保存的设备信息
        if (DeviceManager.hasSavedDevice()) {
            logd("发现保存的设备信息，将尝试直接连接")
            ToastUtil.show(this, R.string.reconnecting_saved_device)
        } else {
            logd("未发现保存的设备信息，将使用扫描模式")
            ToastUtil.show(this, R.string.no_saved_device)
        }
        
        checkLocationPermission()
    }
    
    private fun clearSavedDevice() {
        logd("=== MainActivity: 清除保存的设备信息 ===")
        DeviceManager.clearSavedDevice()
        // 重新初始化BLE控制器以使用默认设备地址
        ledController.reinitializeBLE()
        updateDeviceInfo()
        ToastUtil.show(this, R.string.cleared_saved_device)
        logd("设备信息清除完成")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                checkLocationPermission()
            } else {
                ToastUtil.show(this, R.string.turn_on_failed)
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
                        ToastUtil.show(this, R.string.require_permission_failed)
                    }
                })
        } else {
            connectToDevice()
        }
    }

    private fun connectToDevice() {
        logd("=== MainActivity: 开始连接设备 ===")
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

    private fun updateDeviceInfo() {
        if (isConnected) {
            // 如果已连接，显示当前连接信息（由onConnected方法设置）

        } else {
            tvDeviceName.text = "设备名称："
            tvDeviceAddress.text = ""
        }
    }

    private fun updateBrightnessDisplay() {
        runOnUiThread {
            tvBrightnessValue.text = "$currentBrightness%"
        }

    }

    //////BLE 回调相关///////

    override fun onCheckCharacteristicSuccess() {
        ledController.drawDefault()
        // 不再设置默认亮度，等待从ESP32接收当前亮度值
        logd("=== MainActivity: 连接成功，等待接收ESP32的亮度值 ===")
    }
    
    override fun onBrightnessReceived(brightness: Int) {
        logd("=== MainActivity: 收到ESP32的亮度值 ===")
        logd("亮度值: $brightness")
        
        // 将ESP32的亮度值转换为百分比显示
        val brightnessPercent = (brightness * 100 / 255).coerceIn(0, 100)
        currentBrightness = brightnessPercent
        
        runOnUiThread {
            sbBrightness.progress = currentBrightness
            updateBrightnessDisplay()
        }
        
        logd("亮度值已更新到界面: $brightnessPercent%")
    }

    override fun onDisConnected() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: 设备断开连接回调 ===")
                isConnected = false
                updateDeviceInfo()
                stopLoading()
                ToastUtil.show(this, R.string.disconnected)
                btnReconnect.isEnabled = true
            }
        }
    }

    override fun onConnecting() {
        runOnUiThread {
            // 可以在这里显示连接状态
        }
    }

    override fun onScanFailed() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: 扫描失败回调 ===")
                isConnected = false
                updateDeviceInfo()
                stopLoading()
                ToastUtil.show(this, R.string.find_no_device)
            }
        }
    }

    override fun onConnected(name: String?, address: String?) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: 设备连接成功回调 ===")
                logd("设备名称: ${name ?: "未知"}")
                logd("设备地址: ${address ?: "未知"}")
                
                isConnected = true
                name?.let {
                    tvDeviceName.text = "设备名称: $it"
                }
                address?.let {
                    tvDeviceAddress.text = it
                }

                ToastUtil.show(this, R.string.connected)
                stopLoading()
                btnReconnect.isEnabled = false
            }
        }
    }

    override fun writeDataCallback(isSuccess: Boolean) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                if(!isSuccess) {
                    ToastUtil.show(this, R.string.write_failed)
                }
            }
        }
    }
    
    // MTU协商相关回调
    override fun onMtuNegotiationSuccess(mtu: Int) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: MTU协商成功 ===")
                logd("协商后的MTU大小: $mtu 字节")
                
                when {
                    mtu >= 512 -> {
                        ToastUtil.show(this, getString(R.string.mtu_negotiation_success_optimal, mtu))
                    }
                    mtu >= 256 -> {
                        ToastUtil.show(this, getString(R.string.mtu_negotiation_success_good, mtu))
                    }
                    mtu >= 128 -> {
                        ToastUtil.show(this, getString(R.string.mtu_negotiation_success_normal, mtu))
                    }
                    else -> {
                        ToastUtil.show(this, getString(R.string.mtu_negotiation_success_slow, mtu))
                    }
                }
            }
        }
    }
    
    override fun onMtuNegotiationFailed(requestedMtu: Int, actualMtu: Int) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: MTU协商失败 ===")
                logd("请求的MTU大小: $requestedMtu 字节")
                logd("实际使用的MTU大小: $actualMtu 字节")
                
                ToastUtil.show(this, getString(R.string.mtu_negotiation_failed, actualMtu))
                logd("数据传输可能会较慢，但功能不受影响")
            }
        }
    }
    
    // PHY协商相关回调
    override fun onPhyNegotiationSuccess(txPhy: Int, rxPhy: Int) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: PHY协商成功 ===")
                logd("TX PHY: $txPhy, RX PHY: $rxPhy")
                
                val message = when (txPhy) {
                    2 -> getString(R.string.phy_negotiation_success_2m)
                    3 -> getString(R.string.phy_negotiation_success_coded)
                    else -> getString(R.string.phy_negotiation_success_1m)
                }
                ToastUtil.show(this, message)
            }
        }
    }
    
    override fun onPhyNegotiationFailed(requestedPhy: Int, actualPhy: Int) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: PHY协商失败 ===")
                logd("请求的PHY: $requestedPhy")
                logd("实际的PHY: $actualPhy")
                
                ToastUtil.show(this, getString(R.string.phy_negotiation_failed))
                logd("将使用默认PHY，功能不受影响")
            }
        }
    }
    
    override fun onPhyReadSuccess(txPhy: Int, rxPhy: Int) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: PHY读取成功 ===")
                logd("TX PHY: $txPhy, RX PHY: $rxPhy")
                
                val txPhyDesc = getPhyDescription(txPhy)
                val rxPhyDesc = getPhyDescription(rxPhy)
                ToastUtil.show(this, getString(R.string.phy_read_success, txPhyDesc, rxPhyDesc))
            }
        }
    }
    
    override fun onPhyReadFailed() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: PHY读取失败 ===")
                ToastUtil.show(this, getString(R.string.phy_read_failed))
            }
        }
    }
    
    override fun onPhyUpdateSuccess(txPhy: Int, rxPhy: Int) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: PHY更新成功 ===")
                logd("新的TX PHY: $txPhy, 新的RX PHY: $rxPhy")
                
                val txPhyDesc = getPhyDescription(txPhy)
                val rxPhyDesc = getPhyDescription(rxPhy)
                ToastUtil.show(this, getString(R.string.phy_update_success, txPhyDesc, rxPhyDesc))
            }
        }
    }
    
    override fun onPhyUpdateFailed() {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: PHY更新失败 ===")
                ToastUtil.show(this, getString(R.string.phy_update_failed))
            }
        }
    }
    
    // 配对相关回调
    override fun onBondingSuccess(name: String?, address: String?) {
        runOnUiThread {
            if (!isFinishing && !isDestroyed) {
                logd("=== MainActivity: 配对成功 ===")
                logd("设备名称: ${name ?: "未知"}")
                logd("设备地址: ${address ?: "未知"}")
                
                ToastUtil.show(this, R.string.bonding_success)
                updateDeviceInfo() // 更新设备信息显示
            }
        }
    }
    
    /**
     * 获取PHY描述信息
     */
    private fun getPhyDescription(phy: Int): String {
        return when (phy) {
            1 -> "1M PHY"
            2 -> "2M PHY"
            3 -> "Coded PHY"
            else -> "Unknown PHY ($phy)"
        }
    }
    
    /**
     * 显示PHY信息
     */
    private fun showPhyInfo() {
        logd("=== 显示PHY信息 ===")
        
        val phyInfo = StringBuilder()
        phyInfo.append("=== PHY连接信息 ===\n\n")
        
        // 设备支持情况
        val supports2M = ledController.isLe2MPhySupported()
        val supportsCoded = ledController.isLeCodedPhySupported()
        val supportedPhys = ledController.getSupportedPhys()
        
        phyInfo.append("设备PHY支持:\n")
        phyInfo.append("- 2M PHY: ${if (supports2M) "✓" else "✗"}\n")
        phyInfo.append("- Coded PHY: ${if (supportsCoded) "✓" else "✗"}\n")
        phyInfo.append("- 支持的PHY: $supportedPhys\n\n")
        
        if (isConnected) {
            // 当前连接信息
            val currentTxPhy = ledController.getCurrentTxPhy()
            val currentRxPhy = ledController.getCurrentRxPhy()
            val phyNegotiationSuccess = ledController.isPhyNegotiationSuccessful()
            
            phyInfo.append("当前连接状态:\n")
            phyInfo.append("- TX PHY: ${getPhyDescription(currentTxPhy)}\n")
            phyInfo.append("- RX PHY: ${getPhyDescription(currentRxPhy)}\n")
            phyInfo.append("- PHY协商: ${if (phyNegotiationSuccess) "✓ 成功" else "✗ 失败"}\n\n")
            
            // 连接质量信息
            val qualityInfo = ledController.getConnectionQualityInfo()
            phyInfo.append("连接质量:\n$qualityInfo\n")
        } else {
            phyInfo.append("设备未连接，无法获取PHY信息\n")
        }
        
        // 显示对话框
        android.app.AlertDialog.Builder(this)
            .setTitle("PHY连接信息")
            .setMessage(phyInfo.toString())
            .setPositiveButton("确定", null)
            .setNegativeButton("测试PHY") { _, _ ->
                if (isConnected) {
                    ledController.readCurrentPhy()
                    ToastUtil.show(this, "正在读取PHY信息...")
                } else {
                    ToastUtil.show(this, "设备未连接")
                }
            }
            .show()
    }

    private fun hideKeyboard(context: Activity) {
        val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        var view = context.currentFocus
        if (view == null) {
            view = View(context)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
    
    /**
     * 显示配对对话框
     */
    private fun showBondingDialog() {
        logd("=== 显示配对对话框 ===")
        
        val bondingInfo = StringBuilder()
        bondingInfo.append("=== 设备配对信息 ===\n\n")
        
        // 设备信息摘要
        val deviceInfo = ledController.getDeviceInfoSummary()
        bondingInfo.append("$deviceInfo\n\n")
        
        if (isConnected) {
            // 配对状态信息
            val bondingStatus = ledController.getBondingStatusInfo()
            bondingInfo.append("$bondingStatus\n\n")
            
            // 连接质量信息
            val qualityInfo = ledController.getConnectionQualityInfo()
            bondingInfo.append("$qualityInfo\n")
        } else {
            bondingInfo.append("设备未连接，无法获取配对状态\n")
        }
        
        // 显示对话框
        android.app.AlertDialog.Builder(this)
            .setTitle("设备配对信息")
            .setMessage(bondingInfo.toString())
            .setPositiveButton("确定", null)
            .setNegativeButton("请求配对") { _, _ ->
                if (isConnected) {
                    ledController.requestBonding()
                    ToastUtil.show(this, "正在请求配对...")
                } else {
                    ToastUtil.show(this, "设备未连接")
                }
            }
            .setNeutralButton("清除设备") { _, _ ->
                clearSavedDevice()
                ToastUtil.show(this, "设备信息已清除")
            }
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清理Toast，避免内存泄漏
        ToastUtil.clear()
    }
}
