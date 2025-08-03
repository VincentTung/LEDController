package com.vincent.android.myled.app

import android.app.Application
import com.vincent.android.myled.led.LEDController
import com.vincent.android.myled.utils.ConfigManager

class MyLEDApp: Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 初始化配置管理器
        ConfigManager.init(this)
        
        // 初始化LED控制器
        LEDController.getInstance().initBLE(this)
    }
}