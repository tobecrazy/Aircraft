package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.DividerGreen
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.utils.DebugTools
import com.young.aircraft.viewmodel.PrivacyPolicyViewModel
import java.util.Locale

private val CardBg = Color(0xDD1B1F2B)
private val RejectRed = Color(0xFFFF5555)
private val RejectBg = Color(0x1A161A26)
private val RejectBgPressed = Color(0x33FF5555)
private val AcceptBgPressed = Color(0xFF00CC66)
private const val DISABLED_ALPHA = 0.3f
private const val FADE_MS = 300
private const val PULSE_MS = 1500

// Injected on page load to match the game aesthetic. Monospace font override was
// removed deliberately — it causes layout issues on different screen sizes.
private const val DARK_THEME_JS =
    """
    (function() {
        var style = document.createElement('style');
        style.textContent = 'body { background-color: #0F1118 !important; color: #CCFFFFFF !important; padding: 8px !important; } a { color: #00FF88 !important; } h1,h2,h3 { color: #00FF88 !important; }';
        document.head.appendChild(style);
    })()
    """

/**
 * Cinematic privacy policy acceptance gate — the app's entry point.
 *
 * FLOW: check pref → skip to Onboarding if accepted / show cinematic screen if not
 * Accept → save pref → OnboardingActivity | Reject → finishAffinity()
 */
class PrivacyPolicyAcceptActivity : AppCompatActivity() {
    private lateinit var viewModel: PrivacyPolicyViewModel
    private var starFieldView: StarFieldView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this, PrivacyPolicyViewModel.Factory(this))[PrivacyPolicyViewModel::class.java]

        // Already accepted → route to onboarding gate (it handles its own skip).
        // Must stay ahead of setContent — this is the MAIN LAUNCHER entry regression guard.
        if (viewModel.isAlreadyAccepted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            AircraftTheme {
                PrivacyPolicyAcceptScreen(
                    onStarFieldCreated = { starFieldView = it },
                    onAccept = ::acceptAndContinue,
                    onReject = ::finishAffinity
                )
            }
        }
    }

    internal fun acceptAndContinue() {
        viewModel.acceptPolicy()
        startActivity(Intent(this, OnboardingActivity::class.java))
        finish()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Forward touch activity to StarFieldView for idle timer reset
        starFieldView?.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        starFieldView?.stopAnimation()
        super.onDestroy()
    }
}

@Composable
internal fun PrivacyPolicyAcceptScreen(
    onStarFieldCreated: (StarFieldView) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var acceptUnlocked by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        AndroidView(
            factory = { ctx ->
                StarFieldView(ctx).also {
                    it.startAnimation()
                    onStarFieldCreated(it)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("star_field")
        )

        Column(modifier = Modifier.fillMaxSize()) {
            MissionHeader()
            NeonDivider()

            PolicyWebViewCard(
                onContentEndReached = { acceptUnlocked = true },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("policy_web_view")
            )

            NeonDivider()

            BottomActions(unlocked = acceptUnlocked, onAccept = onAccept, onReject = onReject)
        }
    }
}

@Composable
private fun MissionHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.mission_briefing_title),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PolicyWebViewCard(onContentEndReached: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = BorderStroke(1.dp, DividerGreen)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0x000F1118)
                    DebugTools.enableWebViewDebugging()
                    settings.apply {
                        allowFileAccess = true
                        javaScriptEnabled = true
                        loadsImagesAutomatically = true
                        useWideViewPort = true
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        loadWithOverviewMode = true
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(DARK_THEME_JS, null)

                            // If content is short enough to fit without scrolling,
                            // unlock immediately
                            view?.postDelayed({
                                if (view != null && !view.canScrollVertically(1)) {
                                    onContentEndReached()
                                }
                            }, 500)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            // Fallback: unlock even if the WebView fails to load
                            if (request?.isForMainFrame == true) {
                                onContentEndReached()
                            }
                        }
                    }

                    setOnScrollChangeListener { v, _, _, _, _ ->
                        val wv = v as WebView
                        if (!wv.canScrollVertically(1)) onContentEndReached()
                    }

                    val page = if (Locale.getDefault().language == AircraftConstants.PrivacyPolicy.LANG_ZH) {
                        AircraftConstants.PrivacyPolicy.ASSET_ZH
                    } else {
                        AircraftConstants.PrivacyPolicy.ASSET_EN
                    }
                    loadUrl("${AircraftConstants.PrivacyPolicy.ASSET_PREFIX}$page")
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(1.dp)
        )
    }
}

@Composable
internal fun BottomActions(unlocked: Boolean, onAccept: () -> Unit, onReject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AcceptButton(unlocked = unlocked, onClick = onAccept, modifier = Modifier.weight(1f))
            TacticalActionButton(
                text = stringResource(
                    R.string.privacy_policy_reject_tactical,
                    stringResource(R.string.privacy_policy_reject)
                ),
                enabled = true,
                baseColor = RejectBg,
                pressedColor = RejectBgPressed,
                contentColor = RejectRed,
                border = BorderStroke(1.dp, RejectRed),
                onClick = onReject,
                testTag = "btn_reject"
            )
        }
    }
}

/** Locked at 30% alpha until the document end is reached; then fades in and pulses. */
@Composable
private fun AcceptButton(unlocked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fadeAlpha by animateFloatAsState(
        targetValue = if (unlocked) 1f else DISABLED_ALPHA,
        animationSpec = tween(FADE_MS),
        label = "accept_fade"
    )
    val pulse = rememberInfiniteTransition(label = "accept_pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
        label = "accept_pulse_alpha"
    )
    // Fade first; hand over to the neon pulse once the fade completes.
    val alpha = if (unlocked && fadeAlpha >= 1f) pulse.value else fadeAlpha

    TacticalActionButton(
        text = stringResource(
            R.string.privacy_policy_accept_tactical,
            stringResource(R.string.privacy_policy_accept)
        ),
        enabled = unlocked,
        alpha = alpha,
        baseColor = AccentGreen,
        pressedColor = AcceptBgPressed,
        contentColor = BackgroundDark,
        border = null,
        onClick = onClick,
        testTag = "btn_accept",
        modifier = modifier
    )
}

@Composable
private fun TacticalActionButton(
    text: String,
    enabled: Boolean,
    baseColor: Color,
    pressedColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    alpha: Float = 1f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .height(38.dp)
            .alpha(alpha)
            .testTag(testTag),
        shape = RoundedCornerShape(4.dp),
        color = if (pressed) pressedColor else baseColor,
        border = border
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0F1118, widthDp = 412, heightDp = 892)
@Composable
private fun PrivacyPolicyAcceptScreenPreview() {
    AircraftTheme {
        PrivacyPolicyAcceptScreen(onStarFieldCreated = {}, onAccept = {}, onReject = {})
    }
}
