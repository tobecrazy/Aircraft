package com.young.supperbanner

/**
 * Page-to-page transition applied by [SupperBannerView.setTransition].
 *
 * The first nine are pure [androidx.viewpager2.widget.ViewPager2.PageTransformer] math and run on
 * every supported API. [SHADER] needs API 33+ and silently degrades to [FADE] below that, or if the
 * AGSL source fails to compile.
 */
enum class SupperBannerEffect {
    /** No transformer: the ViewPager2 default slide. */
    NONE,

    /** Cross-fade. The plainest option, good for auto-advancing slideshows. This is the default. */
    FADE,

    /** Outgoing page shrinks and fades as it leaves. */
    ZOOM_OUT,

    /** Incoming page starts small and "behind", growing into place. */
    DEPTH,

    /** 3D cube flip, pivoting on the leading edge. */
    CUBE,

    /** 3D door/cover flip, pivoting on the centre. */
    ROTATION_GATE,

    /** Centre page full size, side pages shrink, rotate and fade. */
    COVERFLOW,

    /** Tinder-style card stack: offset, shrunk and stacked. */
    STACK,

    /** Image layer lags behind the pager. */
    PARALLAX,

    /** Horizontal fold, pivoting on the leading edge. */
    ACCORDION,

    /** AGSL circular dissolve via [android.graphics.RuntimeShader]. API 33+, falls back to [FADE]. */
    SHADER
}

/**
 * Tuning for a [SupperBannerEffect]. Every field is a calibration knob, not a required setting —
 * the defaults are what each effect looks like out of the box, and most effects only read two or
 * three of them.
 *
 * @param minScale smallest scale a page reaches, for the scale-based effects.
 * @param minAlpha smallest alpha a page reaches, for the fade-based effects.
 * @param maxRotation degrees of Y rotation at a fully swiped page ([CUBE], [ROTATION_GATE]).
 * @param coverflowAngle degrees of Y rotation per page of offset ([COVERFLOW]).
 * @param parallaxFraction how much of the pager offset the image layer cancels ([PARALLAX]); 0
 *   pins the image in place, 1 leaves it moving at full speed, i.e. no parallax.
 * @param stackSpread sideways offset of a stacked page, as a fraction of page width ([STACK]).
 * @param cameraDistance 3D perspective distance in density-independent units ([CUBE], [ROTATION_GATE]).
 */
data class SupperBannerTransition(
    val effect: SupperBannerEffect = SupperBannerEffect.FADE,
    val minScale: Float = 0.85f,
    val minAlpha: Float = 0.15f,
    val maxRotation: Float = 90f,
    val coverflowAngle: Float = 30f,
    val parallaxFraction: Float = 0.5f,
    val stackSpread: Float = 0.55f,
    val cameraDistance: Float = 12_000f
) {
    internal fun sanitized(): SupperBannerTransition = copy(
        minScale = minScale.coerceIn(0.05f, 1f),
        minAlpha = minAlpha.coerceIn(0f, 1f),
        maxRotation = maxRotation.coerceIn(0f, 180f),
        coverflowAngle = coverflowAngle.coerceIn(0f, 90f),
        parallaxFraction = parallaxFraction.coerceIn(0f, 1f),
        stackSpread = stackSpread.coerceIn(0f, 1f),
        cameraDistance = if (cameraDistance < 1_000f) 1_000f else cameraDistance
    )
}
