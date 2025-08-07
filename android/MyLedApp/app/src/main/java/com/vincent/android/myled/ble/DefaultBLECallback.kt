package com.vincent.android.myled.ble

import VTBLECallback

class DefaultBLECallback : VTBLECallback {
    override fun onCheckCharacteristicSuccess() {

    }

    override fun onDisConnected() {

    }

    override fun onConnecting() {

    }

    override fun onScanFailed() {

    }

    override fun onConnected(name: String?,address:String?) {

    }

    override fun writeDataCallback(isSuccess: Boolean) {

    }

    // 亮度通知回调
    override fun onBrightnessReceived(brightness: Int) {
        // 默认实现，子类可以重写
    }

    // MTU协商相关回调
    override fun onMtuNegotiationSuccess(mtu: Int) {
        // 默认实现，子类可以重写
    }

    override fun onMtuNegotiationFailed(requestedMtu: Int, actualMtu: Int) {
        // 默认实现，子类可以重写
    }
    
    // PHY协商相关回调
    override fun onPhyNegotiationSuccess(txPhy: Int, rxPhy: Int) {
        // 默认实现，子类可以重写
    }

    override fun onPhyNegotiationFailed(requestedPhy: Int, actualPhy: Int) {
        // 默认实现，子类可以重写
    }

    override fun onPhyReadSuccess(txPhy: Int, rxPhy: Int) {
        // 默认实现，子类可以重写
    }

    override fun onPhyReadFailed() {
        // 默认实现，子类可以重写
    }

    override fun onPhyUpdateSuccess(txPhy: Int, rxPhy: Int) {
        // 默认实现，子类可以重写
    }

    override fun onPhyUpdateFailed() {
        // 默认实现，子类可以重写
    }
    
    // 配对相关回调
    override fun onBondingSuccess(name: String?, address: String?) {
        // 默认实现，子类可以重写
    }
}