package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.young.aircraft.R
import com.young.aircraft.providers.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Tactical theme colors (matching existing XML theme)
private val BackgroundDark = Color(0xFF0F1118)
private val HeaderBg = Color(0xFF161A26)
private val AccentGreen = Color(0xFF00FF88)
private val DividerGreen = Color(0x4400FF88)
private val TextBody = Color(0xCCFFFFFF.toInt())
private val TextSkip = Color(0x88FFFFFF.toInt())
private val ButtonTextDark = Color(0xFF0F1118)

/**
 * 2-screen onboarding carousel — controls tutorial + power-ups overview.
 * Migrated to Jetpack Compose with HorizontalPager, animated transitions,
 * and entrance effects. StarFieldView is wrapped via AndroidView.
 *
 * GATE: check onboarding_completed → skip to LaunchActivity if done / show carousel if not
 * Skip or Launch → save pref → LaunchActivity
 */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private var starFieldView: StarFieldView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        settingsRepository = SettingsRepository(this)

        // Gate: skip if already completed
        if (settingsRepository.isOnboardingCompleted()) {
            startActivity(Intent(this, LaunchActivity::class.java))
            finish()
            return
        }

        setContent {
            MaterialTheme {
                OnboardingScreen(
                    onSkip = { completeOnboarding() },
                    onLaunch = { completeOnboarding() },
                    onStarFieldCreated = { starFieldView = it }
                )
            }
        }
    }

    private fun completeOnboarding() {
        settingsRepository.setOnboardingCompleted(true)
        startActivity(Intent(this, LaunchActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        starFieldView?.stopAnimation()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        starFieldView?.onUserActivity()
        return super.dispatchTouchEvent(ev)
    }
}

// ---------------------------------------------------------------------------
// Composables
// ---------------------------------------------------------------------------

@Composable
private fun OnboardingScreen(
    onSkip: () -> Unit,
    onLaunch: () -> Unit,
    onStarFieldCreated: (StarFieldView) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Animated star field background
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

        // Layer 2: Content overlay
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingHeader(onSkip = onSkip)
            NeonDivider()

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .testTag("onboarding_pager")
            ) { page ->
                when (page) {
                    0 -> ControlsPage(isActive = pagerState.currentPage == 0)
                    1 -> PowerupsPage(isActive = pagerState.currentPage == 1)
                }
            }

            NeonDivider()

            OnboardingBottomBar(
                pagerState = pagerState,
                onNext = { scope.launch { pagerState.animateScrollToPage(1) } },
                onLaunch = onLaunch
            )
        }
    }
}

@Composable
private fun OnboardingHeader(onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = AccentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = stringResource(R.string.onboarding_skip),
                color = TextSkip,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkip
                    )
                    .padding(end = 16.dp)
                    .testTag("btn_skip")
            )
        }
    }
}

@Composable
private fun NeonDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DividerGreen)
    )
}

// ---------------------------------------------------------------------------
// Page: Controls
// ---------------------------------------------------------------------------

@Composable
private fun ControlsPage(isActive: Boolean) {
    // Staggered entrance animation
    var showItems by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (isActive) {
            delay(100)
            showItems = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = showItems,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -40 }
        ) {
            Text(
                text = stringResource(R.string.onboarding_controls_title),
                color = AccentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = showItems,
            enter = fadeIn(tween(400, delayMillis = 100)) +
                    slideInVertically(tween(400, delayMillis = 100)) { -40 }
        ) {
            Image(
                painter = painterResource(R.drawable.jet_plane_2),
                contentDescription = stringResource(R.string.onboarding_controls_title),
                modifier = Modifier.size(100.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val instructions = listOf(
            R.string.onboarding_drag_to_move,
            R.string.onboarding_auto_fire,
            R.string.onboarding_collect_powerups
        )
        instructions.forEachIndexed { index, resId ->
            AnimatedVisibility(
                visible = showItems,
                enter = fadeIn(tween(400, delayMillis = 200 + index * 100)) +
                        slideInVertically(tween(400, delayMillis = 200 + index * 100)) { -30 }
            ) {
                Text(
                    text = stringResource(resId),
                    color = TextBody,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page: Power-ups
// ---------------------------------------------------------------------------

@Composable
private fun PowerupsPage(isActive: Boolean) {
    var showItems by remember { mutableStateOf(false) }
    LaunchedEffect(isActive) {
        if (isActive) {
            delay(100)
            showItems = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = showItems,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -40 }
        ) {
            Text(
                text = stringResource(R.string.onboarding_powerups_title),
                color = AccentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        val powerups = listOf(
            R.drawable.red_heart_1 to R.string.onboarding_hp_restore,
            R.drawable.shield_1 to R.string.onboarding_invincibility,
            R.drawable.red_box_1 to R.string.onboarding_aoe_rocket
        )
        powerups.forEachIndexed { index, (iconRes, textRes) ->
            AnimatedVisibility(
                visible = showItems,
                enter = fadeIn(tween(400, delayMillis = 100 + index * 120)) +
                        slideInVertically(tween(400, delayMillis = 100 + index * 120)) { -30 }
            ) {
                PowerupRow(
                    iconRes = iconRes,
                    textRes = textRes,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(
            visible = showItems,
            enter = fadeIn(tween(400, delayMillis = 500)) +
                    slideInVertically(tween(400, delayMillis = 500)) { -30 }
        ) {
            Text(
                text = stringResource(R.string.onboarding_collect_all),
                color = AccentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun PowerupRow(
    iconRes: Int,
    textRes: Int,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = stringResource(textRes),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(textRes),
            color = TextBody,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom bar: indicators + next/launch button
// ---------------------------------------------------------------------------

@Composable
private fun OnboardingBottomBar(
    pagerState: PagerState,
    onNext: () -> Unit,
    onLaunch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Page indicators
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(2) { index ->
                val alpha by animateFloatAsState(
                    targetValue = if (pagerState.currentPage == index) 1f else 0.3f,
                    animationSpec = tween(300),
                    label = "indicator_alpha_$index"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(AccentGreen)
                        .testTag("indicator_$index")
                )
            }
        }

        // NEXT / LAUNCH button
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AccentGreen)
                .clickable {
                    if (pagerState.currentPage < 1) onNext() else onLaunch()
                }
                .testTag("btn_next"),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = pagerState.currentPage == 1,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "button_text"
            ) { isLastPage ->
                Text(
                    text = stringResource(
                        if (isLastPage) R.string.onboarding_launch
                        else R.string.onboarding_next
                    ),
                    color = ButtonTextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
