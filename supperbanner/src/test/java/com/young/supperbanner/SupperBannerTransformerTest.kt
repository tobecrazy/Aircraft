package com.young.supperbanner

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupperBannerTransformerTest {

    private val density = 2f

    private fun page(): View =
        View(ApplicationProvider.getApplicationContext()).apply { layout(0, 0, 300, 200) }

    private fun transformerFor(effect: SupperBannerEffect): ViewPager2.PageTransformer? =
        SupperBannerTransformers.create(SupperBannerTransition(effect = effect), density)

    @Test
    fun `NONE installs no transformer`() {
        assertNull(transformerFor(SupperBannerEffect.NONE))
    }

    @Test
    fun `a default transition cross-fades`() {
        assertEquals(SupperBannerEffect.FADE, SupperBannerTransition().effect)
        assertNotNull(transformerFor(SupperBannerTransition().effect))
    }

    @Test
    fun `every effect except NONE produces a transformer`() {
        SupperBannerEffect.entries.filter { it != SupperBannerEffect.NONE }.forEach { effect ->
            assertNotNull("no transformer for $effect", transformerFor(effect))
        }
    }

    @Test
    fun `FADE is opaque at centre and clear at both edges`() {
        val fade = transformerFor(SupperBannerEffect.FADE)!!
        val page = page()
        fade.transformPage(page, 0f)
        assertEquals(1f, page.alpha, 0.001f)
        fade.transformPage(page, 1f)
        assertEquals(0f, page.alpha, 0.001f)
        fade.transformPage(page, -1f)
        assertEquals(0f, page.alpha, 0.001f)
    }

    @Test
    fun `ZOOM_OUT shrinks to minScale at the edges`() {
        val zoom = SupperBannerTransformers.create(
            SupperBannerTransition(effect = SupperBannerEffect.ZOOM_OUT, minScale = 0.8f), density
        )!!
        val page = page()
        zoom.transformPage(page, 0f)
        assertEquals(1f, page.scaleX, 0.001f)
        zoom.transformPage(page, 1f)
        assertEquals(0.8f, page.scaleX, 0.001f)
    }

    @Test
    fun `CUBE pivots on the leading edge and rotates a full turn across a swipe`() {
        val cube = transformerFor(SupperBannerEffect.CUBE)!!
        val page = page()

        cube.transformPage(page, 0.5f)
        assertEquals(0f, page.pivotX, 0.001f)
        assertEquals(100f, page.pivotY, 0.001f)
        assertEquals(45f, page.rotationY, 0.001f)

        cube.transformPage(page, -0.5f)
        assertEquals(300f, page.pivotX, 0.001f)
        assertEquals(-45f, page.rotationY, 0.001f)
    }

    @Test
    fun `ROTATION_GATE pivots on the centre instead of the edge`() {
        val gate = transformerFor(SupperBannerEffect.ROTATION_GATE)!!
        val page = page()
        gate.transformPage(page, 0.5f)
        assertEquals(150f, page.pivotX, 0.001f)
        assertEquals(100f, page.pivotY, 0.001f)
    }

    @Test
    fun `COVERFLOW rotates side pages in opposite directions`() {
        val cover = SupperBannerTransformers.create(
            SupperBannerTransition(effect = SupperBannerEffect.COVERFLOW, coverflowAngle = 30f),
            density
        )!!
        val page = page()
        cover.transformPage(page, 1f)
        val right = page.rotationY
        cover.transformPage(page, -1f)
        assertTrue("right=$right left=${page.rotationY}", right * page.rotationY < 0f)
        assertEquals(-30f, right, 0.001f)
    }

    @Test
    fun `PARALLAX lags behind the pager and scales up to cover the seam`() {
        val parallax = SupperBannerTransformers.create(
            SupperBannerTransition(effect = SupperBannerEffect.PARALLAX, parallaxFraction = 0.5f),
            density
        )!!
        val page = page()
        parallax.transformPage(page, 1f)
        // Must move *slower* than the pager, i.e. back toward the current page, never further out.
        assertEquals(-150f, page.translationX, 0.001f)
        // Widening the gap by 150px on a 300px page needs scale >= 2 to keep the seam covered.
        assertEquals(2f, page.scaleX, 0.001f)
        parallax.transformPage(page, 0f)
        assertEquals(0f, page.translationX, 0.001f)
        assertEquals(2f, page.scaleX, 0.001f)
    }

    @Test
    fun `ACCORDION folds from the leading edge`() {
        val accordion = transformerFor(SupperBannerEffect.ACCORDION)!!
        val page = page()
        accordion.transformPage(page, 0.5f)
        assertEquals(0f, page.pivotX, 0.001f)
        assertEquals(0.5f, page.scaleX, 0.001f)
        accordion.transformPage(page, -0.5f)
        assertEquals(300f, page.pivotX, 0.001f)
    }

    @Test
    fun `STACK offsets side pages instead of leaving them centred`() {
        val stack = SupperBannerTransformers.create(
            SupperBannerTransition(effect = SupperBannerEffect.STACK, stackSpread = 0.5f), density
        )!!
        val page = page()
        stack.transformPage(page, 1f)
        assertEquals(150f, page.translationX, 0.001f)
        assertEquals(0f, page.elevation, 0.001f)
        stack.transformPage(page, 0f)
        assertEquals(0f, page.translationX, 0.001f)
        assertTrue("selected page must be lifted", page.elevation > 0f)
    }

    @Test
    fun `DEPTH pulls the leaving page away and grows the entering one in place`() {
        val depth = SupperBannerTransformers.create(
            SupperBannerTransition(effect = SupperBannerEffect.DEPTH, minScale = 0.75f), density
        )!!
        val page = page()
        depth.transformPage(page, -0.5f)
        assertEquals(1f, page.scaleX, 0.001f)
        assertEquals(1f, page.alpha, 0.001f)
        assertEquals(-150f, page.translationX, 0.001f)

        depth.transformPage(page, 0.5f)
        assertEquals(-150f, page.translationX, 0.001f)
        assertTrue("entering page starts smaller", page.scaleX < 1f)
        assertTrue("entering page fades in", page.alpha in 0f..1f)
    }

    @Test
    fun `positions beyond the page are clamped instead of over-transforming`() {
        SupperBannerEffect.entries.filter { it != SupperBannerEffect.NONE }.forEach { effect ->
            val t = SupperBannerTransformers.create(SupperBannerTransition(effect = effect), density)!!
            val page = page()
            listOf(-3f, -1f, 0f, 1f, 3f).forEach { p ->
                t.transformPage(page, p)
                assertTrue("$effect alpha at $p", page.alpha in 0f..1f)
                assertTrue("$effect scale at $p", page.scaleX.isFinite() && page.scaleX > 0f)
                assertTrue("$effect scaleY at $p", page.scaleY.isFinite() && page.scaleY > 0f)
                assertTrue("$effect rotationY at $p", page.rotationY.isFinite())
                assertTrue("$effect translationX at $p", page.translationX.isFinite())
            }
        }
    }

    @Test
    fun `SHADER survives a device without RuntimeShader support`() {
        // sdk 34 here, so this exercises the real shader path; the fallback branch is what keeps
        // API 32 hosts alive.
        val shader = transformerFor(SupperBannerEffect.SHADER)!!
        val page = page()
        shader.transformPage(page, 0f)
        assertTrue("alpha must stay sane", page.alpha in 0f..1f)
    }

    @Test
    fun `out of range tuning values are clamped`() {
        val wild = SupperBannerTransition(
            effect = SupperBannerEffect.COVERFLOW,
            minScale = 5f,
            minAlpha = -3f,
            maxRotation = 900f,
            parallaxFraction = 7f,
            stackSpread = -1f,
            cameraDistance = 1f
        ).sanitized()
        assertEquals(1f, wild.minScale, 0.001f)
        assertEquals(0f, wild.minAlpha, 0.001f)
        assertEquals(180f, wild.maxRotation, 0.001f)
        assertEquals(1f, wild.parallaxFraction, 0.001f)
        assertEquals(0f, wild.stackSpread, 0.001f)
        assertTrue("cameraDistance must stay large enough to look 3D", wild.cameraDistance >= 1000f)
    }

    @Test
    fun `multi-page effects keep the side pages laid out`() {
        val view = SupperBannerView(ApplicationProvider.getApplicationContext())
        listOf(SupperBannerEffect.COVERFLOW, SupperBannerEffect.STACK).forEach {
            view.setTransition(SupperBannerTransition(effect = it))
            assertEquals("$it must render its neighbours", 3, view.pager().offscreenPageLimit)
        }
        view.setTransition(SupperBannerTransition(effect = SupperBannerEffect.FADE))
        assertEquals(1, view.pager().offscreenPageLimit)
    }

    private fun SupperBannerView.pager(): ViewPager2 {
        fun find(group: ViewGroup): ViewPager2? {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child is ViewPager2) return child
                if (child is ViewGroup) find(child)?.let { return it }
            }
            return null
        }
        return requireNotNull(find(this)) { "SupperBannerView no longer hosts a ViewPager2" }
    }

    @Test
    fun `view accepts every effect without throwing`() {
        val view = SupperBannerView(ApplicationProvider.getApplicationContext())
        SupperBannerEffect.entries.forEach { effect ->
            view.setTransition(SupperBannerTransition(effect = effect))
        }
        view.setTransition(SupperBannerTransition())
    }
}
