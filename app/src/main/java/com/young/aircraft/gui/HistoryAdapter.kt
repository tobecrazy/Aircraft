package com.young.aircraft.gui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.young.aircraft.R
import com.young.aircraft.data.GameDifficulty
import com.young.aircraft.data.PlayerGameData
import com.young.aircraft.databinding.ItemHistoryBinding
import java.text.NumberFormat
import java.util.Locale

class HistoryAdapter(
    private val items: MutableList<PlayerGameData>,
    private val onDelete: (PlayerGameData, Int) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    companion object {
        // 7-color rainbow cycle: red, orange, yellow, green, cyan, blue, purple
        private val RAINBOW_COLORS = intArrayOf(
            Color.parseColor("#FF4444"), // red
            Color.parseColor("#FF8C00"), // orange
            Color.parseColor("#FFD700"), // yellow
            Color.parseColor("#00CC66"), // green
            Color.parseColor("#00CED1"), // cyan
            Color.parseColor("#4488FF"), // blue
            Color.parseColor("#AA66CC"), // purple
        )
    }

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.binding.root.context
        val accentColor = RAINBOW_COLORS[position % RAINBOW_COLORS.size]

        // Item card background with accent-tinted stroke
        holder.binding.itemRoot.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f * ctx.resources.displayMetrics.density
            setColor(Color.argb(0x1A, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))
            setStroke(
                (1 * ctx.resources.displayMetrics.density).toInt(),
                Color.argb(0x55, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            )
        }

        // Rank circle
        holder.binding.textRank.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(accentColor)
        }
        holder.binding.textRank.text = (position + 1).toString()
        holder.binding.textRank.setTextColor(Color.parseColor("#1B1F2B"))

        // Player ID: truncate to 6 chars + ellipsis
        holder.binding.textPlayerId.text = if (item.playerId.length > 6) {
            item.playerId.take(6) + "\u2026"
        } else {
            item.playerId
        }

        // Score with comma separators
        holder.binding.textScore.text = NumberFormat.getNumberInstance(Locale.US).format(item.score)

        // Level
        holder.binding.textLevel.text = "Lv.${item.level}"

        // Difficulty badge
        val (badgeRes, badgeTextColor, badgeText) = when (GameDifficulty.fromPersistedValue(item.difficulty)) {
            GameDifficulty.EASY -> Triple(R.drawable.badge_easy, Color.parseColor("#00FF88"), ctx.getString(R.string.difficulty_easy))
            GameDifficulty.HARD -> Triple(R.drawable.badge_hard, Color.parseColor("#FF4444"), ctx.getString(R.string.difficulty_hard))
            GameDifficulty.NORMAL -> Triple(R.drawable.badge_normal, Color.parseColor("#FFFF00"), ctx.getString(R.string.difficulty_normal))
        }
        holder.binding.textDifficulty.setBackgroundResource(badgeRes)
        holder.binding.textDifficulty.setTextColor(badgeTextColor)
        holder.binding.textDifficulty.text = badgeText

        // Delete
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
