package com.example.blueapptooth

import android.bluetooth.BluetoothDevice

data class DispositivoBluetooth(
    val nombre: String,
    val macAddress: String,
    val device: BluetoothDevice
)
