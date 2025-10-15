package com.vincent.library.ble.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

object VTBluetoothUtil {

    fun isEnable(context: Context): Boolean {
        return getBluetoothAdapter(context)?.isEnabled == true
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }
}