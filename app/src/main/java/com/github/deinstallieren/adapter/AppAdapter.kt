package com.github.deinstallieren.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.deinstallieren.R
import com.github.deinstallieren.databinding.ItemAppBinding
import com.github.deinstallieren.model.AppItem

class AppAdapter(
    private val onItemClick: (AppItem, View) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private val fullList = mutableListOf<AppItem>()
    private val displayedList = mutableListOf<AppItem>()

    fun submitList(list: List<AppItem>) {
        fullList.clear()
        fullList.addAll(list)
        displayedList.clear()
        displayedList.addAll(list)
        notifyDataSetChanged()
    }

    fun filter(query: String, filterType: Int) {
        val filtered = fullList.filter { item ->
            val matchesType = when (filterType) {
                1 -> item.isSystem
                2 -> !item.isSystem
                else -> true
            }
            val matchesQuery = item.appName.contains(query, ignoreCase = true) ||
                    item.packageName.contains(query, ignoreCase = true)
            matchesType && matchesQuery
        }
        displayedList.clear()
        displayedList.addAll(filtered)
        notifyDataSetChanged()
    }

    fun removeItem(packageName: String) {
        val index = displayedList.indexOfFirst { it.packageName == packageName }
        if (index != -1) {
            displayedList.removeAt(index)
            fullList.removeAll { it.packageName == packageName }
            notifyItemRemoved(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(displayedList[position])
    }

    override fun getItemCount(): Int = displayedList.size

    inner class AppViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppItem) {
            binding.tvAppName.text = item.appName
            binding.tvPackageName.text = item.packageName

            if (item.icon != null) {
                binding.ivAppIcon.setImageDrawable(item.icon)
            } else {
                binding.ivAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            when {
                item.isChipset -> {
                    binding.tvBadge.visibility = View.VISIBLE
                    binding.tvBadge.text = itemView.context.getString(R.string.badge_chipset)
                }
                !item.isEnabled -> {
                    binding.tvBadge.visibility = View.VISIBLE
                    binding.tvBadge.text = itemView.context.getString(R.string.badge_disabled)
                }
                item.isSuspended -> {
                    binding.tvBadge.visibility = View.VISIBLE
                    binding.tvBadge.text = itemView.context.getString(R.string.badge_suspended)
                }
                else -> {
                    binding.tvBadge.visibility = View.GONE
                }
            }

            binding.root.alpha = if (!item.isEnabled || item.isSuspended) 0.5f else 1.0f

            binding.root.setOnClickListener {
                onItemClick(item, binding.root)
            }
        }
    }
}
