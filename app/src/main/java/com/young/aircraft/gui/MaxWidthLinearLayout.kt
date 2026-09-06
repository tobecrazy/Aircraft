package com.young.aircraft.gui

import android.content.Context
import android.util.AttributeSet
import android.view.View.MeasureSpec
import android.widget.LinearLayout

/**
 * Vertical LinearLayout whose width fills its parent up to [android:maxWidth],
 * so wide-screen content columns can be capped (values-sw600dp) and centered
 * with layout_gravity while match_parent children still fill the capped width.
 *
 * The platform LinearLayout ignores android:maxWidth and, when wrap_content,
 * does not expand for match_parent children — both break the content_max_width
 * pattern, hence this class. Pair it with:
 *   layout_width="wrap_content" | "match_parent"
 *   layout_gravity="center_horizontal"
 *   maxWidth="@dimen/content_max_width"
 */
class MaxWidthLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val maxWidthPx: Int

    init {
        val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.maxWidth))
        maxWidthPx = a.getDimensionPixelSize(0, -1)
        a.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        // EXACTLY(min(parent, max)) — fills like match_parent on phones (-1px dimen
        // = uncapped), caps and lets layout_gravity center it on wide screens, and
        // gives match_parent children a fixed spec so nothing collapses.
        val width = if (maxWidthPx in 1 until widthSize) maxWidthPx else widthSize
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}
