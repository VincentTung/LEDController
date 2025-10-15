package com.vincent.android.ledcontroller.ui

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.tbruyelle.rxpermissions2.RxPermissions
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.constants.LedConstants.LED_DEFAULT_DISPLAY_TEXT
import com.vincent.android.ledcontroller.databinding.ActivityMainBinding
import com.vincent.android.ledcontroller.logic.DialogManager
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.android.ledcontroller.model.LEDDevice
import com.vincent.library.ble.logic.BLEConnectionState
import com.vincent.android.ledcontroller.utils.ImageProcessor
import com.vincent.android.ledcontroller.vm.MainViewModel
import com.vincent.library.base.ui.VTBaseActivity
import com.vincent.library.base.util.VTToastUtil
import com.vincent.library.base.util.logd
import com.vincent.library.ble.util.VTBluetoothUtil
import io.reactivex.functions.Consumer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * 首页
 *
 */
class MainActivity : VTBaseActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val CLOCK_TOGGLE_DELAY_MS = 500L
    }

    private lateinit var binding: ActivityMainBinding
    private var isShowClock = false

    private val ledController: LEDController by lazy { LEDController.getInstance() }
    private val rxPermission: RxPermissions by lazy { RxPermissions(this) }

    private lateinit var viewModel: MainViewModel

    private val imageProcessor: ImageProcessor by lazy { createImageProcessor() }
    private val dialogManager: DialogManager by lazy { createDialogManager() }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkLocationPermission()
        } else {
            VTToastUtil.show(this, R.string.turn_on_failed)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { imageProcessor.processSelectedImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logd(TAG, "onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViewModel()
        initView()
        initObservers()
        checkBluetoothAndPermissions()
    }

    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
    }

    private fun createImageProcessor(): ImageProcessor {
        return ImageProcessor(
            context = this,
            isConnected = { viewModel.isDeviceConnected() },
            showToast = { message -> VTToastUtil.show(this, message) },
            showProgressDialog = { dialogManager.showProgressDialog() },
            updateProgressDialog = { progress -> dialogManager.updateProgressDialog(progress) },
            hideProgressDialog = { dialogManager.hideProgressDialog() }
        )
    }
    
    private fun createDialogManager(): DialogManager {
        return DialogManager(
            context = this,
            isConnected = { viewModel.isDeviceConnected() },
            showToast = { message -> VTToastUtil.show(this, message) },
            onClearDevice = { clearSavedDevice() }
        )
    }

    private fun initView() {
        initTitle(R.string.app_name, R.color.white, false)
        initDeviceInfoViews()
        initBrightnessControl()
        initActionButtons()
    }

    private fun initDeviceInfoViews() {
        // 设置按钮点击监听器
        binding.btnReconnect.setOnClickListener { 
            reconnectDevice()
        }
        binding.btnReconnect.setOnLongClickListener {
            showBondingDialog()
            true
        }
        binding.btnPhyInfo.setOnClickListener { 
            showPhyInfo()
        }
    }

    private fun initObservers() {
        viewModel.deviceState
            .onEach { device ->
                onDeviceStateChanged(device)
            }
            .launchIn(lifecycleScope)

        // 订阅亮度百分比并更新 UI
        viewModel.brightnessPercent
            .onEach { percent ->
                binding.sbBrightness.progress = percent
                binding.tvBrightnessValue.text = "$percent%"
            }
            .launchIn(lifecycleScope)
    }
    
    /**
     * 设备状态变化回调
     */
    private fun onDeviceStateChanged(device: LEDDevice) {
        logd(TAG, "设备状态变化:")
        logd(TAG, "  设备名称: ${device.name}")
        logd(TAG, "  MAC地址: ${device.macAddress}")
        logd(TAG, "  分辨率: ${device.resolution}")
        logd(TAG, "  连接状态: ${device.connectionStateDescription}")
        logd(TAG, "  配对状态: ${if (device.isBonded) "已配对" else "未配对"}")
        logd(TAG, "  MTU: ${device.mtu}")
        logd(TAG, "  PHY: TX=${device.txPhy}, RX=${device.rxPhy}")

        updateUIForDeviceState(device)
    }
    
    /**
     * 根据设备状态更新UI
     */
    private fun updateUIForDeviceState(device: LEDDevice) {
        updateDeviceInfoDisplay(device)
        updateConnectionStatusUI(device.connectionState)
    }
    
    /**
     * 更新设备信息显示
     */
    private fun updateDeviceInfoDisplay(device: LEDDevice) {
        binding.tvDeviceName.text = buildString {
            append("设备名称：")
            append(device.name.takeIf { it.isNotEmpty() } ?: "未知")
        }
        binding.tvDeviceAddress.text = buildString {
            append("MAC：")
            append(device.macAddress.takeIf { it.isNotEmpty() } ?: "未知")
        }
        binding.tvDeviceResolution.text = buildString {
            append("分辨率：")
            append(device.resolution.toString())
        }
    }
    
    /**
     * 更新连接状态UI
     */
    private fun updateConnectionStatusUI(connectionState: BLEConnectionState) {
        val (iconRes, isEnabled, alpha) = when (connectionState) {
            BLEConnectionState.CONNECTED, BLEConnectionState.READY -> {
                // 连接成功，关闭加载对话框
                stopLoading()
                Triple(R.drawable.ic_green_circle, true, 1.0f)
            }
            BLEConnectionState.CONNECTING -> Triple(
                R.drawable.ic_grey_circle, false, 0.5f
            )
            BLEConnectionState.DISCONNECTED -> {
                isShowClock = false
                // 连接断开，关闭加载对话框并显示提示
                stopLoading()
                VTToastUtil.show(this, "设备连接断开")
                Triple(R.drawable.ic_grey_circle, true, 1.0f)
            }
            BLEConnectionState.ERROR -> {
                // 连接失败，关闭加载对话框并显示提示
                stopLoading()
                VTToastUtil.show(this, "连接失败")
                Triple(R.drawable.ic_grey_circle, true, 1.0f)
            }
            BLEConnectionState.DISCOVERING -> Triple(
                R.drawable.ic_grey_circle, false, 0.5f
            )
        }
        
        binding.tvDeviceName.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        binding.btnReconnect.apply {
            this.isEnabled = isEnabled
            this.alpha = alpha
        }
    }

    private fun initBrightnessControl() {
        binding.sbBrightness.progress = viewModel.getCurrentBrightness()
        binding.sbBrightness.setOnSeekBarChangeListener(viewModel.createBrightnessSeekBarListener())
        binding.tvBrightnessValue.text = "${viewModel.getCurrentBrightness()}%"
    }


    private fun initActionButtons() {
        binding.btnDrawText.setOnClickListener {
            startActivity(Intent(this, TextActivity::class.java))
        }

        binding.btnDrawNormal.setOnClickListener {
            startActivity(Intent(this, GraffitiActivity::class.java))
        }

        binding.btnDrawImage.setOnClickListener {
            pickImageFile()
        }

        binding.btnDrawGif.setOnClickListener {
            startActivity(Intent(this, GIFListActivity::class.java))
        }

        // 时钟控制按钮
        binding.btnClock.setOnClickListener {
            toggleClockState()
        }


        // 计时游戏按钮
        binding.btnGame.setOnClickListener {
            startTimerGame()
        }

        // 设置按钮
        binding.btnSetting.setOnClickListener {
            showSettingsDialog()
        }
    }


    /**
     * 切换时钟状态
     */
    private fun toggleClockState() {
        if (!viewModel.isDeviceConnected()) {
            VTToastUtil.show(this, getString(R.string.device_not_connected_short))
            return
        }
        
        isShowClock = !isShowClock
        toggleClock(isShowClock)
        
        if (!isShowClock) {
            lifecycleScope.launch {
                delay(CLOCK_TOGGLE_DELAY_MS)
                ledController.drawStaticText(LED_DEFAULT_DISPLAY_TEXT)
            }
        }
    }
    
    private fun toggleClock(enable: Boolean) {
        if (enable) {
            ledController.enableClock()
            VTToastUtil.show(this, getString(R.string.clock_enabled))
        } else {
            ledController.disableClock()
            VTToastUtil.show(this, getString(R.string.clock_disabled))
        }
    }


    private fun checkBluetoothAndPermissions() {
        if (!VTBluetoothUtil.isEnable(this)) {
            logd(TAG, "蓝牙未启用，请求启用蓝牙")
            turnOnBluetooth()
        } else {
            logd(TAG, "蓝牙已启用，检查权限")
            checkLocationPermission()
        }
    }

    private fun clearSavedDevice() {
        viewModel.clearSavedDevice()
        VTToastUtil.show(this, R.string.cleared_saved_device)
    }

    /**
     * 重连设备 - 检查蓝牙状态后重连
     */
    private fun reconnectDevice() {
        logd(TAG, "用户点击重连按钮")
        
        // 检查蓝牙状态
        if (!VTBluetoothUtil.isEnable(this)) {
            logd(TAG, "重连时发现蓝牙未启用，先开启蓝牙")
            turnOnBluetooth()
            return
        }
        
        // 检查权限
        if (!hasRequiredBluetoothPermissions()) {
            logd(TAG, "重连时发现权限不足，重新请求权限")
            checkLocationPermission()
            return
        }
        
        // 蓝牙和权限都正常，执行重连
        logd(TAG, "蓝牙和权限检查通过，开始重连")
        viewModel.reconnect()
    }


    @SuppressLint("CheckResult")
    private fun checkLocationPermission() {
        val permissionsToRequest = getRequiredPermissions()
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest)
        } else {
            logd(TAG, "所有权限已授予，开始连接设备")
            connectToDevice()
        }
    }
    
    /**
     * 获取需要请求的权限列表
     */
    private fun getRequiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要新的蓝牙权限
            listOfNotNull(
                BLUETOOTH_SCAN.takeIf { !rxPermission.isGranted(BLUETOOTH_SCAN) },
                BLUETOOTH_CONNECT.takeIf { !rxPermission.isGranted(BLUETOOTH_CONNECT) }
            )
        } else {
            // Android 11及以下需要位置权限
            listOfNotNull(
                ACCESS_FINE_LOCATION.takeIf { !rxPermission.isGranted(ACCESS_FINE_LOCATION) }
            )
        }
    }
    
    /**
     * 请求权限
     */
    private fun requestPermissions(permissions: List<String>) {
        logd(TAG, "请求权限: ${permissions.joinToString(", ")}")
        rxPermission.request(*permissions.toTypedArray())
            .subscribe(Consumer { isGranted ->
                logd(TAG, "权限请求结果: $isGranted")
                if (isGranted) {
                    logd(TAG, "权限授予成功，开始连接设备")
                    connectToDevice()
                } else {
                    logd(TAG, "权限授予失败")
                    VTToastUtil.show(this, R.string.require_permission_failed)
                }
            })
    }

    private fun connectToDevice() {
        logd(TAG, "开始连接设备")

        if (!validateConnectionPrerequisites()) {
            return
        }

        logd(TAG, "所有检查通过，开始连接设备")
        startLoading(getString(R.string.connecting_device))
        viewModel.connect()
    }
    
    /**
     * 验证连接前置条件
     */
    private fun validateConnectionPrerequisites(): Boolean {
        // 检查权限
        if (!hasRequiredBluetoothPermissions()) {
            logd(TAG, "权限不足，重新请求权限")
            checkLocationPermission()
            return false
        }

        // 检查蓝牙状态
        if (!VTBluetoothUtil.isEnable(this)) {
            logd(TAG, "蓝牙未启用，尝试开启蓝牙")
            turnOnBluetooth()
            return false
        }
        
        return true
    }

    /**
     * 检查是否有足够的蓝牙权限
     */
    private fun hasRequiredBluetoothPermissions(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要新的蓝牙权限
            val hasScan = rxPermission.isGranted(BLUETOOTH_SCAN)
            val hasConnect = rxPermission.isGranted(BLUETOOTH_CONNECT)
            logd(
                TAG,
                "Android 12+ 权限检查: BLUETOOTH_SCAN=$hasScan, BLUETOOTH_CONNECT=$hasConnect"
            )
            hasScan && hasConnect
        } else {
            // Android 11及以下需要位置权限
            val hasLocation = rxPermission.isGranted(ACCESS_FINE_LOCATION)
            logd(TAG, "Android 11及以下权限检查: ACCESS_FINE_LOCATION=$hasLocation")
            logd(TAG, "Android 11及以下版本，BLUETOOTH和BLUETOOTH_ADMIN为安装时权限")

            val bluetoothEnabled = VTBluetoothUtil.isEnable(this)
            logd(TAG, "蓝牙适配器状态: $bluetoothEnabled")

            hasLocation && bluetoothEnabled
        }
        logd(TAG, "权限检查结果: $result")
        return result
    }

    @SuppressLint("MissingPermission")
    private fun turnOnBluetooth() {
        // 检查是否有足够的权限来启用蓝牙
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rxPermission.isGranted(BLUETOOTH_CONNECT)
        } else {
            // Android 11及以下版本，BLUETOOTH_CONNECT权限不存在，直接返回true
            true
        }

        if (!hasPermission) {
            logd(TAG, "缺少BLUETOOTH_CONNECT权限，先请求权限")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                rxPermission.request(BLUETOOTH_CONNECT)
                    .subscribe(
                        { granted ->
                            if (granted) {
                                logd(TAG, "BLUETOOTH_CONNECT权限授予成功，现在开启蓝牙")
                                requestBluetoothEnable()
                            } else {
                                logd(TAG, "BLUETOOTH_CONNECT权限授予失败")
                                VTToastUtil.show(this, "需要蓝牙权限才能开启蓝牙")
                            }
                        },
                        { error ->
                            logd(TAG, "权限请求出错: ${error.message}")
                            VTToastUtil.show(this, "权限请求失败")
                        }
                    )
            }
            return
        }

        requestBluetoothEnable()
    }

    @SuppressLint("MissingPermission")
    private fun requestBluetoothEnable() {
        logd(TAG, "请求开启蓝牙")
        
        // 检查Activity状态
        if (isFinishing || isDestroyed) {
            logd(TAG, "Activity已销毁，无法开启蓝牙")
            return
        }
        
        try {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } catch (e: Exception) {
            logd(TAG, "启动蓝牙开启对话框失败: ${e.message}")
            VTToastUtil.show(this, "无法开启蓝牙对话框")
        }
    }


    private fun showPhyInfo() {
        dialogManager.showPhyInfoDialog()
    }



    private fun showBondingDialog() {
        dialogManager.showBondingDialog()
    }

    private fun showSettingsDialog() {
        dialogManager.showSettingsDialog()
    }

    private fun pickImageFile() {
        pickImageLauncher.launch(getString(R.string.image_type))
    }


    /**
     * 启动计时游戏
     */
    private fun startTimerGame() {
        if (!viewModel.isDeviceConnected()) {
            VTToastUtil.show(this, getString(R.string.device_not_connected))
            return
        }

        val intent = Intent(this, TimerGameActivity::class.java)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}