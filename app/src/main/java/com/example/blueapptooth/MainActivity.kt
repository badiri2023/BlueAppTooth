package com.example.blueapptooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var rvDispositivos: RecyclerView
    private lateinit var bluetoothAdapter: BluetoothAdapter

    // ESTO GESTIONA LA RESPUESTA DEL USUARIO CUANDO PIDE PERMISOS
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Si el usuario acepta, cargamos la lista. Si no, mostramos aviso.
            if (permissions.all { it.value }) {
                cargarDispositivos()
            } else {
                Toast.makeText(this, "Se necesitan permisos para ver los dispositivos", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Configurar RecyclerView
        rvDispositivos = findViewById(R.id.rvDispositivos)
        rvDispositivos.layoutManager = LinearLayoutManager(this)

        // 2. Inicializar Bluetooth
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        // Nota: en algunos dispositivos antiguos adapter puede ser null si no tienen BT
        if (bluetoothManager.adapter == null) {
            Toast.makeText(this, "Este dispositivo no tiene Bluetooth", Toast.LENGTH_LONG).show()
            return
        }
        bluetoothAdapter = bluetoothManager.adapter

        // 3. Comprobar y Pedir Permisos o Cargar Lista
        checkPermissionsAndLoad()
    }

    private fun checkPermissionsAndLoad() {
        // En Android 12 (S) o superior, necesitamos el permiso BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // Si no tenemos permiso, lo pedimos
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                return
            }
        }
        // Si estamos en Android antiguo o ya tenemos permiso:
        cargarDispositivos()
    }

    private fun cargarDispositivos() {
        // Verificación de seguridad redundante para que Android Studio no marque error en rojo
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }

        // --- AQUÍ OBTENEMOS LOS DATOS REALES ---
        val pairedDevices = bluetoothAdapter.bondedDevices // Dispositivos emparejados
        val listaVisual = mutableListOf<DispositivoBluetooth>()

        if (pairedDevices.isNullOrEmpty()) {
            Toast.makeText(this, "No hay dispositivos emparejados", Toast.LENGTH_SHORT).show()
        } else {
            pairedDevices.forEach { device ->
                val nombre = device.name ?: "Dispositivo Desconocido"
                val mac = device.address
                listaVisual.add(DispositivoBluetooth(nombre, mac))
            }
        }

        // --- CONECTAMOS CON EL ADAPTER ---
        val adapter = DispositivosAdapter(listaVisual) { dispositivo ->
            // Esta es la función lambda que se ejecuta al hacer CLICK
            mostrarDialogo(dispositivo)
        }

        rvDispositivos.adapter = adapter
    }

    private fun mostrarDialogo(device: DispositivoBluetooth) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(device.nombre)
        builder.setMessage("Dirección MAC:\n${device.macAddress}\n\nEstado:\nEmparejado en el sistema")
        builder.setIcon(R.drawable.baseline_bluetooth_24) // Usamos el mismo icono
        builder.setPositiveButton("Cerrar") { dialog, _ ->
            dialog.dismiss()
        }
        // Botón opcional para conectar (lógica no implementada, solo visual)
        builder.setNeutralButton("Conectar") { _, _ ->
            Toast.makeText(this, "Intentando conectar con ${device.nombre}...", Toast.LENGTH_SHORT).show()
        }

        builder.create().show()
    }
}