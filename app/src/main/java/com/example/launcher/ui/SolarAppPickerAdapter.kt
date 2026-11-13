package com.example.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.R
import com.example.launcher.data.AppInfo

class SolarAppPickerAdapter(
    private val allApps: List<AppInfo>,
    initialSelected: Set<String>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<SolarAppPickerAdapter.PickerVH>() {

    val selectedPackages = initialSelected.toMutableSet()
    private var displayedApps = allApps

    class PickerVH(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
        val selectionRing: View = view.findViewById(R.id.selection_ring)
        val checkBadge: View = view.findViewById(R.id.check_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PickerVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_solar_picker_app, parent, false)
        return PickerVH(view)
    }

    override fun onBindViewHolder(holder: PickerVH, position: Int) {
        val app = displayedApps[position]
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.label

        val isSelected = selectedPackages.contains(app.packageName)
        holder.selectionRing.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.checkBadge.visibility = if (isSelected) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            if (isSelected) {
                selectedPackages.remove(app.packageName)
            } else {
                selectedPackages.add(app.packageName)
            }
            notifyItemChanged(position)
            onSelectionChanged(selectedPackages.size)
        }
    }

    override fun getItemCount(): Int = displayedApps.size

    fun filter(query: String) {
        val q = query.trim().lowercase()
        displayedApps = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase().contains(q) }
        }
        notifyDataSetChanged()
    }
}

