package com.example.blueapptooth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DispositivosAdapter(
    private val lista: List<DispositivoBluetooth>,
    private val alHacerClick: (DispositivoBluetooth) -> Unit // Función para manejar el clic
) : RecyclerView.Adapter<DispositivosAdapter.ViewHolder>() {

    // Clase interna para guardar las referencias a los elementos visuales
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvDetalles: TextView = view.findViewById(R.id.tvDetalles)
        // La imagen (ivIcono) no la tocamos porque es estática, siempre es el icono de bluetooth
    }

    // 1. Crea el diseño visual para cada fila
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dispositivo, parent, false)
        return ViewHolder(view)
    }

    // 2. Rellena los datos en cada fila
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dispositivo = lista[position]

        holder.tvNombre.text = dispositivo.nombre
        holder.tvDetalles.text = dispositivo.macAddress

        // Aquí configuramos el CLIC que pediste
        holder.itemView.setOnClickListener {
            alHacerClick(dispositivo) // Esto avisa al MainActivity
        }
    }

    // 3. Dice cuántos elementos hay en total
    override fun getItemCount() = lista.size
}