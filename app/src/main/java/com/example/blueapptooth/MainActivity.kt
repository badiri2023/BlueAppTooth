package com.example.blueapptooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity(), BLEconnDialog.BLEConnectionCallback {

    private lateinit var rvDispositivos: RecyclerView
    private lateinit var adapter: DispositivosAdapter
    // Unificamos la lista al tipo DispositivoBluetooth
    private val listaParaMostrar = mutableListOf<DispositivoBluetooth>()

    private val REQUEST_CODE_PERMISSIONS = 123
    private var bleDialog: BLEconnDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inicializar UI
        rvDispositivos = findViewById(R.id.rvDispositivos)
        rvDispositivos.layoutManager = LinearLayoutManager(this)

        // Inicializamos el adapter una sola vez
        adapter = DispositivosAdapter(listaParaMostrar) { item ->
            showBLEDialog(item.device) // Usamos el objeto BluetoothDevice interno
        }
        rvDispositivos.adapter = adapter

        // 2. Comprobar permisos y cargar dispositivos
        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        val permisos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val faltanPermisos = permisos.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (faltanPermisos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, faltanPermisos.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            cargarDispositivosEmparejados()
        }
    }

    @SuppressLint("MissingPermission")
    private fun cargarDispositivosEmparejados() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Por favor, activa el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        listaParaMostrar.clear()

        // Obtenemos los dispositivos vinculados del sistema
        val bondedDevices = bluetoothAdapter.bondedDevices
        for (device in bondedDevices) {
            // Creamos nuestro objeto de "envoltura" para el RecyclerView
            val nuevoItem = DispositivoBluetooth(
                nombre = device.name ?: "Desconocido",
                macAddress = device.address,
                device = device
            )
            listaParaMostrar.add(nuevoItem)
        }

        // Notificamos al adapter que los datos han cambiado
        adapter.notifyDataSetChanged()
    }

    private fun showBLEDialog(device: BluetoothDevice) {
        // Lanzamos el diálogo que gestiona la conexión GATT
        bleDialog = BLEconnDialog(this, device, this)
        bleDialog?.show()
    }

    // --- Implementación de Callbacks del Diálogo ---

    override fun onConnectionSuccess(gatt: BluetoothGatt) {
        // Aquí podrías hacer algo extra si quisieras,
        // pero el diálogo ya muestra "Conectado"
    }

    override fun onConnectionFailed(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionCancelled() {
        runOnUiThread {
            Toast.makeText(this, "Conexión cancelada", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onReceivedImage(file: File) {
        runOnUiThread {
            Toast.makeText(this, "¡Imagen guardada: ${file.name}!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            cargarDispositivosEmparejados()
        }
    }
}
