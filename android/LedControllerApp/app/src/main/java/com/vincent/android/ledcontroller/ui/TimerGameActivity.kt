package com.vincent.android.ledcontroller.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import com.vincent.android.ledcontroller.R
import com.vincent.android.ledcontroller.databinding.ActivityTimerGameBinding
import com.vincent.android.ledcontroller.logic.LEDController
import com.vincent.library.base.ui.VTBaseActivity
import com.vincent.library.base.util.logd

/**
 * 计时游戏Activity
 * 规则：LED屏幕随机显示目标时间，用户按下开始后LED开始计时，
 * 当计时到达目标时间时按下停止按钮即可获胜
 */
class TimerGameActivity : VTBaseActivity() {

    companion object {
        private const val TAG = "TimerGameActivity"
        private const val VIBRATOR_TIME = 200L
        private const val TONE_DURATION = 200
        private const val TONE_VOLUME = 100
    }

    private lateinit var binding: ActivityTimerGameBinding

    // 游戏状态
    private var gameState = GameState.READY
    private val ledController: LEDController by lazy { LEDController.getInstance() }

    private val vibrator: Vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val toneGenerator: ToneGenerator by lazy {
        ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
    }


    enum class GameState {
        READY,      // 准备状态
        RUNNING,    // 游戏进行中
        FINISHED    // 游戏结束
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logd(TAG, "onCreate")
        binding = ActivityTimerGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initView()
        initGame()
    }

    override fun getStatusBarColor(): Int {
        return R.color.yellow
    }

    private fun initView() {
        initTitle(R.string.start_timer_game, R.color.yellow, true)
        binding.btnControlGame.setOnClickListener {
            logd(TAG, "按钮被点击，当前状态: $gameState")
            provideFeedback()
            handleGameAction()
        }
    }

    /**
     * 提供用户反馈（震动和声音）
     */
    private fun provideFeedback() {
        vibrateButton()
        playSound()
    }

    /**
     * 处理游戏动作
     */
    private fun handleGameAction() {
        when (gameState) {
            GameState.READY -> startGame()
            GameState.RUNNING -> stopGame()
            GameState.FINISHED -> restartGame()
        }
    }

    private fun initGame() {
        ledController.startTimerGame()
        updateUI()
    }

    /**
     * 震动效果
     */
    private fun vibrateButton() {
        try {
            if (!vibrator.hasVibrator()) {
                logd(TAG, "设备不支持震动")
                return
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(
                    VIBRATOR_TIME,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
                vibrator.vibrate(vibrationEffect)
                logd(TAG, "使用新API震动")
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATOR_TIME)
                logd(TAG, "使用旧API震动")
            }
        } catch (e: Exception) {
            logd(TAG, "震动失败: ${e.message}")
        }
    }

    /**
     * 播放声音反馈
     */
    private fun playSound() {
        try {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, TONE_DURATION)
        } catch (e: Exception) {
            logd(TAG, "声音反馈失败: ${e.message}")
        }
    }

    private fun startGame() {
        if (gameState != GameState.READY) return

        gameState = GameState.RUNNING
        updateButtonText()
        ledController.startTimerCountdown()
        logd(TAG, "游戏开始")
    }

    private fun stopGame() {
        if (gameState != GameState.RUNNING) return

        gameState = GameState.FINISHED
        ledController.stopTimerCountdown()
        updateButtonText()
        logd(TAG, "游戏停止，等待ESP32判断结果")
    }

    private fun restartGame() {
        gameState = GameState.READY
        ledController.startTimerGame()
        updateButtonText()
        logd(TAG, "游戏重新开始")
    }

    /**
     * 更新按钮文本
     */
    private fun updateButtonText() {
        val buttonText = when (gameState) {
            GameState.READY -> getString(R.string.timer_game_start)
            GameState.RUNNING -> getString(R.string.timer_game_stop)
            GameState.FINISHED -> getString(R.string.timer_game_restart)
        }
        binding.btnControlGame.text = buttonText
    }


    private fun updateUI() {
        updateButtonText()
        binding.btnControlGame.isEnabled = true
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()

    }

    /**
     * 清理资源
     */
    private fun cleanup() {
        try {
            ledController.stopTimerCountdown()
            toneGenerator.release()
        } catch (e: Exception) {
            logd(TAG, "释放资源失败: ${e.message}")
        }
    }
}