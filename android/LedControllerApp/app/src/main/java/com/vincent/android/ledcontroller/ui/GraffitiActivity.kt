package com.vincent.android.ledcontroller.ui

import android.os.Bundle
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.databinding.ActivityGraffitiBinding
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.android.ledcontroller.widget.PixelViewListener
import com.vincent.library.base.ui.VTBaseActivity
import com.vincent.library.base.util.logd
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * 涂鸦
 * todo 添加彩色
 */
class GraffitiActivity : VTBaseActivity(), PixelViewListener {
    companion object {
        private const val PIXEL_DRAW_DELAY_MS = 1000L // 像素绘制延迟时间（毫秒）
        private const val DEFAULT_REAL_TIME_STATE = false
    }
    
    private lateinit var binding: ActivityGraffitiBinding
    private var isRealTime = DEFAULT_REAL_TIME_STATE
    private val ledController: LEDController = LEDController.getInstance()
    
    // 协程相关
    private var pixelJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGraffitiBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initViews()
        ledController.fillScreen(true)
    }
    
    
    override fun getStatusBarColor(): Int {
        return R.color.yellow
    }
    

    private fun initViews() {
        initTitle(R.string.draw_a_picture, R.color.yellow)
        binding.pv.setListener(this)
        initRealTimeSwitch()
        
        // 初始化按钮点击事件
        initButtonClickListeners()
        
        setMode(binding.pv.dragMode)
    }
    
    private fun initRealTimeSwitch() {
        binding.sw.apply {
            isChecked = isRealTime
            setOnCheckedChangeListener { _, isChecked ->
                isRealTime = isChecked
            }
        }
    }
    
    private fun initButtonClickListeners() {
        binding.btnUpdate.setOnClickListener {
            drawToLED()
        }
        
        binding.btnDrag.setOnClickListener {
            setMode(true)
        }
        
        binding.btnFill.setOnClickListener {
            setMode(false)
        }

        binding.btnAllBlack.setOnClickListener {
            fillAllBlack()
        }
        
        binding.btnAllWhite.setOnClickListener {
            fillAllWhite()
        }
    }

    private fun setMode(isDrawMode: Boolean) {
        binding.pv.dragMode = isDrawMode
        
        val (dragRes, fillRes) = if (isDrawMode) {
            R.drawable.drag_enable to R.drawable.fill_disable
        } else {
            R.drawable.drag_disable to R.drawable.fill_enable
        }
        
        binding.btnDrag.setImageResource(dragRes)
        binding.btnFill.setImageResource(fillRes)
    }

    /**
     * 画到LED屏幕上去
     */
    private fun drawToLED() {
        when {
            binding.pv.isAllBlack -> ledController.fillScreen(true)
            binding.pv.isAllWhite -> ledController.fillScreen(false)
            else -> ledController.drawNormalCanvas(binding.pv.c51ModData)
        }
    }
    
    /**
     * 填充全黑
     */
    private fun fillAllBlack() {
        fillScreen { binding.pv.fillAllBlack() }
    }
    
    /**
     * 填充全白
     */
    private fun fillAllWhite() {
        fillScreen { binding.pv.fillAllWhite() }
    }
    
    /**
     * 通用填充屏幕方法
     */
    private fun fillScreen(action: () -> Unit) {
        action()
        if (isRealTime) {
            drawToLED()
        }
    }

    override fun pixelOnTouch(x: Int, y: Int, color: Int) {
        if (!isRealTime || isFinishing || isDestroyed) return
        
        // 参数验证
        if (x < 0 || y < 0) {
            logd("无效的像素坐标: x=$x, y=$y")
            return
        }
        
        logd("actionUpCallback: x=$x, y=$y, color=$color")
        
        // 取消之前的绘制任务
        pixelJob?.cancel()
        
        // 启动新的绘制任务
        pixelJob = lifecycleScope.launch {
            try {
                delay(PIXEL_DRAW_DELAY_MS)
                if (!isFinishing && !isDestroyed) {
                    ledController.draw1Pixel(x, y, color)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logd("绘制像素时出错: ${e.message}")
            }
        }
    }
}

