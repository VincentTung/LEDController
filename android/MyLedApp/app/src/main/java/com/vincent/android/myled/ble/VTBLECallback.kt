interface VTBLECallback {

    fun onCheckCharacteristicSuccess();
    fun onDisConnected()
    fun onConnecting()
    fun onScanFailed()
    fun onConnected(name: String?,address:String?)
    fun writeDataCallback(isSuccess: Boolean)
    
    // 亮度通知回调
    fun onBrightnessReceived(brightness: Int)
    
    // MTU协商相关回调
    fun onMtuNegotiationSuccess(mtu: Int)
    fun onMtuNegotiationFailed(requestedMtu: Int, actualMtu: Int)
    
    // PHY协商相关回调
    fun onPhyNegotiationSuccess(txPhy: Int, rxPhy: Int)
    fun onPhyNegotiationFailed(requestedPhy: Int, actualPhy: Int)
    fun onPhyReadSuccess(txPhy: Int, rxPhy: Int)
    fun onPhyReadFailed()
    fun onPhyUpdateSuccess(txPhy: Int, rxPhy: Int)
    fun onPhyUpdateFailed()
    
    // 配对相关回调
    fun onBondingSuccess(name: String?, address: String?)
}

