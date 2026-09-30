package com.young.supperbanner

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.max

/**
 * Builds the [ViewPager2.PageTransformer] for a transition. `null` means "no transformer", which is
 * how ViewPager2 is told to go back to its default slide.
 */
internal object SupperBannerTransformers {

    fun create(transition: SupperBannerTransition, density: Float): ViewPager2.PageTransformer? {
        val t = transition.sanitized()
        return when (t.effect) {
            SupperBannerEffect.NONE -> null
            SupperBannerEffect.FADE -> Fade
            SupperBannerEffect.ZOOM_OUT -> ZoomOut(t)
            SupperBannerEffect.DEPTH -> Depth(t)
            SupperBannerEffect.CUBE -> Cube(t, density)
            SupperBannerEffect.ROTATION_GATE -> RotationGate(t, density)
            SupperBannerEffect.COVERFLOW -> Coverflow(t, density)
            SupperBannerEffect.STACK -> Stack(t, density)
            SupperBannerEffect.PARALLAX -> Parallax(t)
            SupperBannerEffect.ACCORDION -> Accordion(t)
            SupperBannerEffect.SHADER -> Shader(t, density)
        }
    }
}

/** Every transform runs from a clean slate: ViewPager2 recycles pages, so stale state must go. */
private fun View.resetTransform() {
    alpha = 1f
    translationX = 0f
    translationY = 0f
    scaleX = 1f
    scaleY = 1f
    rotation = 0f
    rotationY = 0f
    pivotX = width / 2f
    pivotY = height / 2f
    elevation = 0f
    setRenderEffect(null)
}

private fun Float.clampPosition() = coerceIn(-1f, 1f)

private object Fade : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        page.alpha = 1f - abs(position.clampPosition())
    }
}

private class ZoomOut(private val t: SupperBannerTransition) : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val offset = abs(position.clampPosition())
        val scale = 1f - (1f - t.minScale) * offset
        page.scaleX = scale
        page.scaleY = scale
        page.alpha = 1f - (1f - t.minAlpha) * offset
    }
}

private class Depth(private val t: SupperBannerTransition) : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val p = position.clampPosition()
        if (p < 0f) {
            // Leaving: rides the pager at full size, so it reads as a card being pulled away.
            page.translationX = page.width * p
        } else {
            // Entering: hold it in place and grow it up from the back.
            val scale = 1f - (1f - t.minScale) * p
            page.scaleX = scale
            page.scaleY = scale
            page.translationX = -page.width * p
            page.alpha = 1f - p * (1f - t.minAlpha)
        }
    }
}

private class Cube(private val t: SupperBannerTransition, private val density: Float) :
    ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val p = position.clampPosition()
        page.pivotX = if (p < 0f) page.width.toFloat() else 0f
        page.pivotY = page.height / 2f
        page.cameraDistance = t.cameraDistance * density
        page.rotationY = p * t.maxRotation
    }
}

private class RotationGate(private val t: SupperBannerTransition, private val density: Float) :
    ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val p = position.clampPosition()
        page.pivotX = page.width / 2f
        page.pivotY = page.height / 2f
        page.cameraDistance = t.cameraDistance * density
        page.rotationY = p * t.maxRotation
        page.alpha = 1f - abs(p) * (1f - t.minAlpha)
    }
}

private class Coverflow(private val t: SupperBannerTransition, private val density: Float) :
    ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val p = position.clampPosition()
        val offset = abs(p)
        val scale = 1f - (1f - t.minScale) * offset
        page.scaleX = scale
        page.scaleY = scale
        page.pivotY = page.height / 2f
        page.cameraDistance = t.cameraDistance * density
        page.rotationY = -t.coverflowAngle * p
        // Pull side pages toward the centre so they fan instead of stacking at the edges.
        page.translationX = -page.width * p * (1f - t.minScale)
        page.alpha = 1f - offset * (1f - t.minAlpha)
    }
}

private class Stack(private val t: SupperBannerTransition, private val density: Float) :
    ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val p = position.clampPosition()
        val offset = abs(p)
        val scale = 1f - (1f - t.minScale) * offset
        page.scaleX = scale
        page.scaleY = scale
        page.translationX = page.width * t.stackSpread * p
        page.translationY = page.height * 0.12f * p
        page.alpha = 1f - offset * (1f - t.minAlpha)
        page.elevation = (1f - offset) * 8f * density
    }
}

private class Parallax(private val t: SupperBannerTransition) : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val p = position.clampPosition()
        // Lag behind the pager. Translating a page widens the gap to its neighbour by
        // width * fraction, so the page is scaled up by the same amount to keep the seam covered.
        page.translationX = -page.width * p * t.parallaxFraction
        val cover = 1f + 2f * t.parallaxFraction
        page.scaleX = cover
        page.scaleY = cover
    }
}

private class Accordion(private val t: SupperBannerTransition) : ViewPager2.PageTransformer {
    override fun transformPage(page: View, position: Float) {
        page.resetTransform()
        val p = position.clampPosition()
        page.pivotX = if (p < 0f) page.width.toFloat() else 0f
        page.pivotY = page.height / 2f
        page.scaleX = max(0.1f, 1f - abs(p))
        page.alpha = 1f - abs(p) * (1f - t.minAlpha)
    }
}

/**
 * Circular AGSL dissolve. Swap [SOURCE] for another shader to get ripple / curl / mosaic; the
 * uniforms below are the contract it has to honour.
 */
private class Shader(private val t: SupperBannerTransition, private val density: Float) :
    ViewPager2.PageTransformer {

    private var shader: RuntimeShader? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) buildShader() else null
    } catch (_: RuntimeException) {
        // AGSL compilation throws on a bad source string. Never take the view down for a transition.
        null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun buildShader(): RuntimeShader {
        val instance = RuntimeShader(SOURCE)
        instance.setFloatUniform("edge", 0.12f)
        instance.setFloatUniform("minScale", t.minScale)
        return instance
    }

    override fun transformPage(page: View, position: Float) {
        val p = position.clampPosition()
        val active = shader
        if (active == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // ponytail: shader unavailable -> fade. Add a real fallback only if a host needs one.
            page.resetTransform()
            page.alpha = 1f - abs(p)
            return
        }
        page.resetTransform()
        val reveal = 1f - abs(p)
        active.setFloatUniform("reveal", reveal)
        active.setFloatUniform("center", page.width / 2f, page.height / 2f)
        active.setFloatUniform("maxRadius", max(page.width, page.height) / 2f)
        page.setRenderEffect(RenderEffect.createRuntimeShaderEffect(active, "content"))
    }

    private companion object {
        const val SOURCE = """
            uniform shader content;
            uniform float2 center;
            uniform float maxRadius;
            uniform float reveal;
            uniform float edge;
            uniform float minScale;

            half4 main(float2 fragCoord) {
                half4 c = content.eval(fragCoord);
                float d = distance(fragCoord, center) / maxRadius;
                float threshold = (1.0 - reveal) * 1.15;
                float mask = smoothstep(threshold, threshold + edge, d);
                float scale = 1.0 - (1.0 - minScale) * (1.0 - reveal);
                float2 mapped = center + (fragCoord - center) / scale;
                half4 shrunk = content.eval(mapped);
                half4 outColor = mix(c, shrunk, 1.0 - reveal);
                if (outColor.a * mask < 0.01) {
                    discard;
                }
                return half4(outColor.rgb, outColor.a * mask);
            }
        """
    }
}
