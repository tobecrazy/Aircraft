package com.young.supperbanner

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupperBannerViewColorsTest {

    private fun newBanner(): SupperBannerView {
        val view = SupperBannerView(ApplicationProvider.getApplicationContext())
        view.setItems(
            listOf(
                SupperBannerItem("一", "第一张", SupperBannerImage.Local(R.drawable.ic_placeholder)),
                SupperBannerItem("二", "第二张", SupperBannerImage.Local(R.drawable.ic_placeholder))
            )
        )
        return view
    }

    /** Indicator dots are the only TextViews carrying a GradientDrawable background. */
    private fun indicators(root: View): List<TextView> {
        val found = mutableListOf<TextView>()
        fun walk(view: View) {
            if (view is TextView && view.background is GradientDrawable) found.add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(root)
        return found
    }

    @Test
    fun `defaults match the documented palette`() {
        val view = newBanner()
        assertEquals(
            SupperBannerColors.DEFAULT_BACKGROUND,
            (view.background as GradientDrawable).color?.defaultColor
        )
        assertEquals(SupperBannerIndicatorColors.DEFAULT_FILL_SELECTED, fillOf(indicators(view)[0]))
        assertEquals(
            SupperBannerIndicatorColors.DEFAULT_FILL_UNSELECTED,
            fillOf(indicators(view)[1])
        )
    }

    @Test
    fun `setColors repaints container and indicators`() {
        val view = newBanner()
        val custom = SupperBannerColors(
            background = 0xFF102030.toInt(),
            indicator = SupperBannerIndicatorColors(
                fillSelected = 0xFFAABBCC.toInt(),
                fillUnselected = 0xFF334455.toInt()
            )
        )

        view.setColors(custom)

        assertEquals(custom.background, (view.background as GradientDrawable).color?.defaultColor)
        assertEquals(custom.indicator.fillSelected, fillOf(indicators(view)[0]))
        assertEquals(custom.indicator.fillUnselected, fillOf(indicators(view)[1]))
    }

    @Test
    fun `copy overrides a single field without touching the rest`() {
        val colors = SupperBannerColors().copy(background = 0xFF000000.toInt())
        assertEquals(0xFF000000.toInt(), colors.background)
        assertEquals(SupperBannerColors.DEFAULT_TITLE, colors.title)
        assertEquals(
            SupperBannerIndicatorColors.DEFAULT_FILL_SELECTED,
            colors.indicator.fillSelected
        )
    }

    private fun fillOf(dot: TextView): Int? = (dot.background as GradientDrawable).color?.defaultColor
}
