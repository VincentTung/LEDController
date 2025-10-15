package com.vincent.library.ble.logic

import VTBLECallback

class VTDefaultBLECallback : VTBLECallback {
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

    override fun onNotifyValueReceived(notiValue: Int) {
         
    }

    override fun onCharacteristicReadValue(value: String) {
        
    }

    override fun onMtuNegotiationSuccess(mtu: Int) {
         
    }

    override fun onMtuNegotiationFailed(requestedMtu: Int, actualMtu: Int) {
         
    }

    override fun onPhyNegotiationSuccess(txPhy: Int, rxPhy: Int) {
         
    }

    override fun onPhyNegotiationFailed(requestedPhy: Int, actualPhy: Int) {
         
    }

    override fun onPhyReadSuccess(txPhy: Int, rxPhy: Int) {
         
    }

    override fun onPhyReadFailed() {
         
    }

    override fun onPhyUpdateSuccess(txPhy: Int, rxPhy: Int) {
         
    }

    override fun onPhyUpdateFailed() {
         
    }

    override fun onBondingSuccess(name: String?, address: String?) {
         
    }
}