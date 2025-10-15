package com.vincent.android.ledcontroller.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.databinding.ActivityTextBinding
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.library.base.ui.VTBaseActivity
import com.vincent.library.base.util.VTToastUtil

class TextActivity : VTBaseActivity() {
    
    companion object {
        private const val TEXT_SIZE_TINY = 1
        private const val TEXT_SIZE_SMALL = 2
        private const val TEXT_SIZE_MEDIUM = 3
        private const val TEXT_SIZE_LARGE = 4
        
        private const val SCROLL_SPEED_SLOW = 1
        private const val SCROLL_SPEED_MEDIUM = 2
        private const val SCROLL_SPEED_FAST = 3
    }

    private lateinit var binding: ActivityTextBinding
    private var isScrollText: Boolean = false

    private val ledController: LEDController by lazy { LEDController.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initTitle(R.string.text, R.color.yellow)
        setupListeners()
    }


    override fun getStatusBarColor(): Int {
        return R.color.yellow
    }
    private fun setupListeners() {
        setupScrollSwitch()
        setupTextSizeSeekBar()
        setupScrollSpeedSeekBar()
        setupSendButton()
    }
    
    /**
     * 设置滚动开关监听器
     */
    private fun setupScrollSwitch() {
        binding.switchScroll.setOnCheckedChangeListener { _, isChecked ->
            isScrollText = isChecked
            binding.rlScroll.visibility = if (isScrollText) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * 设置文字大小调节器
     */
    private fun setupTextSizeSeekBar() {
        binding.sbStaticTextsize.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            val sizeText = getTextSizeString(progress)
            binding.tvStaticTextsize.text = sizeText
        })
    }
    
    /**
     * 设置滚动速度调节器
     */
    private fun setupScrollSpeedSeekBar() {
        binding.sbScrollTextSpeed.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            val speedText = getScrollSpeedString(progress)
            binding.tvScrollTextSpeed.text = speedText
        })
    }
    
    /**
     * 设置发送按钮
     */
    private fun setupSendButton() {
        binding.btnSendStaticText.setOnClickListener {
            sendText()
        }
    }
    
    /**
     * SeekBar监听器
     */
    private fun createSeekBarListener(onProgressChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onProgressChanged(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }
    
    /**
     * 获取文字大小字符串
     */
    private fun getTextSizeString(progress: Int): String {
        return when (progress) {
            TEXT_SIZE_TINY -> getString(R.string.text_size_tiny)
            TEXT_SIZE_SMALL -> getString(R.string.text_size_small)
            TEXT_SIZE_MEDIUM -> getString(R.string.text_size_medium)
            TEXT_SIZE_LARGE -> getString(R.string.text_size_large)
            else -> getString(R.string.text_size_tiny)
        }
    }
    
    /**
     * 获取滚动速度字符串
     */
    private fun getScrollSpeedString(progress: Int): String {
        return when (progress) {
            SCROLL_SPEED_SLOW -> getString(R.string.scroll_speed_slow)
            SCROLL_SPEED_MEDIUM -> getString(R.string.scroll_speed_medium)
            SCROLL_SPEED_FAST -> getString(R.string.scroll_speed_fast)
            else -> getString(R.string.scroll_speed_slow)
        }
    }
    
    /**
     * 发送文字
     */
    private fun sendText() {
        val text = binding.etStaticText.text.toString().trim()
        if (text.isEmpty()) {
            VTToastUtil.show(this, getString(R.string.please_enter_text))
            return
        }
        
        val fontSize = binding.sbStaticTextsize.progress
        if (isScrollText) {
            val speed = binding.sbScrollTextSpeed.progress
            ledController.drawScrollingText(text, fontSize, speed)
        } else {
            ledController.drawStaticText(text, fontSize)
        }
        
        hideKeyboard()
        VTToastUtil.show(this, getString(R.string.text_sent))
    }

    /**
     * 隐藏软键盘
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.windowToken?.let { token ->
            imm.hideSoftInputFromWindow(token, 0)
        }
    }
}
