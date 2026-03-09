package com.young.aircraft.gui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.databinding.ItemHistoryBinding

class HistoryAdapter(
    private val items: MutableList<PlayerGameData>,
    private val onDelete: (PlayerGameData, Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val rankColor = when (position) {
            0 -> Color.parseColor("#FFD700") // gold
            1 -> Color.parseColor("#C0C0C0") // silver
            2 -> Color.parseColor("#CD7F32") // bronze
            else -> Color.parseColor("#FFD54F")
        }
        holder.binding.textRank.text = "#${position + 1}"
        holder.binding.textRank.setTextColor(rankColor)
        holder.binding.textPlayerId.text = item.playerId.take(8) + "\u2026"
        holder.binding.textLevel.text = "Lv.${item.level}"
        holder.binding.textScore.text = item.score.toString()
        holder.binding.btnDelete.setOnClickListener {
            onDelete(item, holder.bindingAdapterPosition)
        }
    }

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, items.size - position)
    }

    override fun getItemCount(): Int = items.size
}
