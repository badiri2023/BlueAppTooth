package com.example.blueapptooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*

class BLEconnDialog(
    context: Context,
    private val device: BluetoothDevice,
    private val connectionCallback: BLEConnectionCallback
) : Dialog(context) {

    interface BLEConnectionCallback {
        fun onConnectionSuccess(gatt: BluetoothGatt)
        fun onConnectionFailed(error: String)
        fun onConnectionCancelled()
        fun onReceivedImage(file: File)
    }

    // Views - Alineados con dialog_ble_conn.xml
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceAddress: TextView
    private lateinit var tvStatus: TextView
    private lateinit var ivPhoto: ImageView // Cambiado tvImage por ivPhoto (ID: imageView)
    private lateinit var progressBar: ProgressBar
    private lateinit var btnConnect: Button
    private lateinit var btnCancel: Button

    // BLE UUIDs (Asegúrate que coincidan con tu código de ESP32)
    private val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    private var bluetoothGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isConnecting = false
    private val CONNECTION_TIMEOUT = 10000L
    private val RECEIVE_TIMEOUT = 30000L

    // Variables para la reconstrucción de la foto
    private val receivedData = ByteArrayOutputStream()
    private lateinit var receivedFile: File
    private var totalSize = 0
    private var isReceiving = false
    private var received = false
    private var packetCount = 0

    @SuppressLint("MissingPermission")
    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting) {
            disconnect()
            connectionCallback.onConnectionFailed("Timeout de conexión")
            dismiss()
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_ble_conn)

        // Inicialización de Views usando los IDs del XML
        tvDeviceName = findViewById(R.id.tvDeviceName)
        tvDeviceAddress = findViewById(R.id.tvDeviceAddress)
        tvStatus = findViewById(R.id.tvStatus)
        ivPhoto = findViewById(R.id.imageView)
        progressBar = findViewById(R.id.progressBar)
        btnConnect = findViewById(R.id.btnConnect)
        btnCancel = findViewById(R.id.btnCancel)

        // Datos del dispositivo
        tvDeviceName.text = device.name ?: "Dispositivo desconocido"
        tvDeviceAddress.text = device.address

        btnConnect.setOnClickListener {
            if (received) {
                connectionCallback.onReceivedImage(receivedFile)
                dismiss()
            } else if (!isConnecting) {
                connectToDevice()
            }
        }

        btnCancel.setOnClickListener {
            cancelConnection()
        }

        connectToDevice()
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        if (isConnecting) return
        isConnecting = true

        tvStatus.text = "Conectando..."
        btnConnect.isEnabled = false
        progressBar.isIndeterminate = true

        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        handler.removeCallbacks(connectionTimeoutRunnable)
                        isConnecting = false
                        tvStatus.text = "Conectado. Negociando MTU..."
                        gatt.requestMtu(517)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnecting = false
                        tvStatus.text = "Desconectado"
                        btnConnect.isEnabled = true
                        btnConnect.text = "Reconectar"
                    }
                }
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Descubrir servicios después de un breve delay
                handler.postDelayed({ gatt.discoverServices() }, 600)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU cambiado a: $mtu")
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

                characteristic?.let {
                    gatt.setCharacteristicNotification(it, true)
                    val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)

                    handler.post { tvStatus.text = "Esperando datos..." }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                @Suppress("DEPRECATION")
                handleIncomingData(characteristic.value)
            }
        }
    }

    private fun handleIncomingData(data: ByteArray) {
        handler.post {
            // 1. Detectar fin de transmisión (4 bytes de 0xFF)
            if (data.size == 4 && data.all { it == (-1).toByte() }) {
                if (isReceiving) completePhotoTransfer()
                return@post
            }

            // 2. Primer paquete: Tamaño de la imagen (4 bytes Little Endian)
            if (!isReceiving && data.size == 4) {
                totalSize = (data[0].toInt() and 0xFF) or
                        ((data[1].toInt() and 0xFF) shl 8) or
                        ((data[2].toInt() and 0xFF) shl 16) or
                        ((data[3].toInt() and 0xFF) shl 24)

                isReceiving = true
                receivedData.reset()
                packetCount = 0
                progressBar.isIndeterminate = false
                progressBar.max = totalSize
                progressBar.progress = 0
                tvStatus.text = "Recibiendo: 0 / $totalSize bytes"
                return@post
            }

            // 3. Acumular paquetes de datos
            if (isReceiving) {
                receivedData.write(data)
                packetCount++
                val currentSize = receivedData.size()
                progressBar.progress = currentSize

                if (packetCount % 5 == 0) {
                    tvStatus.text = "Recibiendo: $currentSize / $totalSize bytes"
                }
            }
        }
    }

    private fun completePhotoTransfer() {
        isReceiving = false
        tvStatus.text = "Procesando imagen..."

        try {
            // El ESP32 suele enviar la imagen en Base64 para evitar bytes de control
            val base64String = receivedData.toString().trim()
            val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
            savePhoto(decodedBytes)
        } catch (e: Exception) {
            tvStatus.text = "Error al decodificar imagen"
            Log.e(TAG, "Error: ${e.message}")
        }
    }

    private fun savePhoto(imageData: ByteArray) {
        try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val cameraDir = File(picturesDir, "ESP32_Camera")
            if (!cameraDir.exists()) cameraDir.mkdirs()

            receivedFile = File(cameraDir, "IMG_${System.currentTimeMillis()}.jpg")
            FileOutputStream(receivedFile).use { it.write(imageData) }

            ivPhoto.setImageURI(receivedFile.toUri())
            tvStatus.text = "✅ Guardado: ${receivedFile.name}"
            received = true
            btnConnect.isEnabled = true
            btnConnect.text = "Finalizar"

            // Notificar a la galería
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, receivedFile.toUri()))
        } catch (e: Exception) {
            tvStatus.text = "Error al guardar archivo"
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    private fun cancelConnection() {
        handler.removeCallbacks(connectionTimeoutRunnable)
        disconnect()
        connectionCallback.onConnectionCancelled()
        dismiss()
    }

    companion object {
        private const val TAG = "BLEconnDialog"
    }
}
