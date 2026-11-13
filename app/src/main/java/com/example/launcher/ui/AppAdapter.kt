package com.example.launcher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.R
import com.example.launcher.data.AppInfo

/** Shared adapter for home pages, dock and drawer. */
class AppAdapter(
    private var items: List<AppInfo>,
    private val showLabels: Boolean = true,
    private val onClick: (AppInfo) -> Unit,
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    fun submit(newItems: List<AppInfo>) {
        items = newItems
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val app = items[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.label.visibility = if (showLabels) android.view.View.VISIBLE else android.view.View.GONE
        holder.itemView.setOnClickListener { onClick(app) }
    }

    override fun getItemCount() = items.size
}

