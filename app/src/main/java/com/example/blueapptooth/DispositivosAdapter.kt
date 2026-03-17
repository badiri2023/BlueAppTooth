package com.example.blueapptooth

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DispositivosAdapter(
    private val lista: List<DispositivoBluetooth>,
    private val alHacerClick: (DispositivoBluetooth) -> Unit
) : RecyclerView.Adapter<DispositivosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvDetalles: TextView = view.findViewById(R.id.tvDetalles)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Inflamos el XML de la fila que diseñaste
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dispositivo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dispositivo = lista[position]
        holder.tvNombre.text = dispositivo.nombre
        holder.tvDetalles.text = dispositivo.macAddress

        holder.itemView.setOnClickListener {
            alHacerClick(dispositivo)
        }
    }

    override fun getItemCount() = lista.size
}
