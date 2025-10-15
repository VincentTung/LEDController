package com.vincent.android.ledcontroller.app

import android.app.Application
import com.vincent.android.ledcontroller.logic.LEDController

class LedApp: Application() {

    override fun onCreate() {
        super.onCreate()
        LEDController.getInstance().init(this)
    }
}