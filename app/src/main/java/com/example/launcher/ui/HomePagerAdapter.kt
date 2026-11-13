package com.example.launcher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.launcher.R
import com.example.launcher.data.AppInfo

/** One page = one grid of apps (iPhone-style home pages). */
class HomePagerAdapter(
    private val columns: Int,
    private val onClick: (AppInfo) -> Unit,
) : RecyclerView.Adapter<HomePagerAdapter.PageVH>() {

    private var pages: List<List<AppInfo>> = emptyList()

    class PageVH(val list: RecyclerView) : RecyclerView.ViewHolder(list)

    fun submit(apps: List<AppInfo>, pageSize: Int) {
        pages = apps.chunked(pageSize).ifEmpty { listOf(emptyList()) }
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val list = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_page, parent, false) as RecyclerView
        list.layoutManager = GridLayoutManager(parent.context, columns)
        return PageVH(list)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.list.adapter = AppAdapter(pages[position], onClick = onClick)
    }

    override fun getItemCount() = pages.size
}

