package com.young.aircraft.gui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.young.aircraft.R

class SupperBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val viewPager = ViewPager2(context)
    private val infoPanel = LinearLayout(context)
    private val titleView = TextView(context)
    private val descriptionView = TextView(context)
    private val indicatorContainer = LinearLayout(context)
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = BannerAdapter(
        onItemClick = { position ->
            items.getOrNull(position)?.let { item ->
                onBannerClickListener?.invoke(item, position)
            }
        }
    )
    private val autoPlayRunnable = object : Runnable {
        override fun run() {
            if (autoPlayEnabled && items.size > 1 && isAttachedToWindow) {
                viewPager.setCurrentItem((viewPager.currentItem + 1) % items.size, true)
                scheduleNext()
            }
        }
    }

    private var items: List<SupperBannerItem> = emptyList()
    private var autoPlayEnabled = true
    private var transitionTimeMillis = SupperBannerConfig.DEFAULT_TRANSITION_TIME_MS
    private var showImageInfo = true
    private var showIndicator = true
    private var onBannerClickListener: ((SupperBannerItem, Int) -> Unit)? = null
    private var indicatorCustomizer: ((TextView, Boolean, Int) -> Unit)? = null

    init {
        clipToOutline = true
        background = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(Color.parseColor("#151A24"))
            setStroke(dp(1), Color.parseColor("#2AFFFFFF"))
        }

        viewPager.adapter = adapter
        viewPager.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderInfo(position)
                renderIndicators(position)
                restartAutoPlay()
            }
        })
        addView(viewPager)

        setupInfoPanel()
        setupIndicatorContainer()
    }

    fun setItems(newItems: List<SupperBannerItem>) {
        items = newItems
        adapter.submitItems(newItems)
        viewPager.setCurrentItem(0, false)
        renderInfo(0)
        renderIndicators(0)
        restartAutoPlay()
    }

    fun setAutoPlayEnabled(enabled: Boolean) {
        autoPlayEnabled = enabled
        restartAutoPlay()
    }

    fun setTransitionTimeMillis(timeMillis: Long) {
        transitionTimeMillis = SupperBannerConfig.coerceTransitionTimeMillis(timeMillis)
        restartAutoPlay()
    }

    fun setShowImageInfo(show: Boolean) {
        showImageInfo = show
        renderInfo(viewPager.currentItem)
    }

    fun setShowIndicator(show: Boolean) {
        showIndicator = show
        renderIndicators(viewPager.currentItem)
    }

    fun setOnBannerClickListener(listener: ((SupperBannerItem, Int) -> Unit)?) {
        onBannerClickListener = listener
    }

    fun setIndicatorCustomizer(customizer: ((TextView, Boolean, Int) -> Unit)?) {
        indicatorCustomizer = customizer
        renderIndicators(viewPager.currentItem)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartAutoPlay()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(autoPlayRunnable)
        super.onDetachedFromWindow()
    }

    private fun setupInfoPanel() {
        infoPanel.orientation = LinearLayout.VERTICAL
        infoPanel.gravity = Gravity.BOTTOM
        infoPanel.setPadding(dp(14), dp(24), dp(14), dp(14))
        infoPanel.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#CC050812"))
        )
        infoPanel.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )

        titleView.setTextColor(Color.WHITE)
        titleView.textSize = 15f
        titleView.typeface = Typeface.MONOSPACE
        titleView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)

        descriptionView.setTextColor(Color.parseColor("#CBD5E8"))
        descriptionView.textSize = 11f
        descriptionView.typeface = Typeface.MONOSPACE
        descriptionView.setPadding(0, dp(4), 0, 0)

        infoPanel.addView(titleView)
        infoPanel.addView(descriptionView)
        addView(infoPanel)
    }

    private fun setupIndicatorContainer() {
        indicatorContainer.orientation = LinearLayout.HORIZONTAL
        indicatorContainer.gravity = Gravity.CENTER
        indicatorContainer.layoutParams = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            setMargins(0, dp(12), dp(12), 0)
        }
        addView(indicatorContainer)
    }

    private fun renderInfo(position: Int) {
        val item = items.getOrNull(position)
        infoPanel.isVisible = showImageInfo && item != null
        if (item != null) {
            titleView.text = item.name
            descriptionView.text = item.description
        }
    }

    private fun renderIndicators(selectedPosition: Int) {
        indicatorContainer.removeAllViews()
        indicatorContainer.isVisible = showIndicator && items.size > 1
        if (!indicatorContainer.isVisible) return

        items.forEachIndexed { index, _ ->
            val dot = TextView(context).apply {
                text = (index + 1).toString()
                gravity = Gravity.CENTER
                textSize = 10f
                typeface = Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                    marginStart = if (index == 0) 0 else dp(6)
                }
            }
            applyDefaultIndicatorStyle(dot, index == selectedPosition)
            indicatorCustomizer?.invoke(dot, index == selectedPosition, index)
            indicatorContainer.addView(dot)
        }
    }

    private fun applyDefaultIndicatorStyle(view: TextView, selected: Boolean) {
        view.setTextColor(if (selected) Color.parseColor("#07100B") else Color.parseColor("#CCFFFFFF"))
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (selected) Color.parseColor("#00FF88") else Color.parseColor("#442A3342"))
            setStroke(dp(1), if (selected) Color.parseColor("#AAFFFFFF") else Color.parseColor("#55FFFFFF"))
        }
    }

    private fun restartAutoPlay() {
        handler.removeCallbacks(autoPlayRunnable)
        if (autoPlayEnabled && items.size > 1 && isAttachedToWindow) {
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        handler.postDelayed(autoPlayRunnable, transitionTimeMillis)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class BannerAdapter(
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

        private val items = mutableListOf<SupperBannerItem>()

        fun submitItems(newItems: List<SupperBannerItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            return BannerViewHolder(imageView, onItemClick)
        }

        override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        private class BannerViewHolder(
            private val imageView: ImageView,
            private val onItemClick: (Int) -> Unit
        ) : RecyclerView.ViewHolder(imageView) {

            init {
                imageView.setOnClickListener { onItemClick(bindingAdapterPosition) }
            }

            fun bind(item: SupperBannerItem) {
                when (val image = item.image) {
                    is SupperBannerImage.Local -> imageView.setImageResource(image.resId)
                    is SupperBannerImage.Network -> imageView.load(image.url) {
                        crossfade(true)
                        placeholder(R.drawable.ic_placeholder)
                        error(R.drawable.ic_placeholder)
                    }
                }
            }
        }
    }
}
