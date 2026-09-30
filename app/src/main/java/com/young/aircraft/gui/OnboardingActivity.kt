package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.young.aircraft.ui.maxContentWidth
import com.young.aircraft.viewmodel.OnboardingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.ui.theme.TextMuted
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.NeonDivider

// Tactical theme colors (matching existing XML theme)

/** Page count of the carousel — kept in one place so pages and indicators cannot drift apart. */
private const val PAGE_COUNT = 4

/**
 * 4-screen onboarding carousel — controls tutorial, field equipment, mission brief,
 * and the standalone puzzle mode.
 * Migrated to Jetpack Compose with HorizontalPager, animated transitions,
 * and entrance effects. StarFieldView is wrapped via AndroidView.
 *
 * GATE: check onboarding_completed → skip to LaunchActivity if done / show carousel if not
 * Skip or Launch → save pref → LaunchActivity
 */
class OnboardingActivity : AppCompatActivity() {
    private lateinit var viewModel: OnboardingViewModel
    private var starFieldView: StarFieldView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        viewModel = ViewModelProvider(this, OnboardingViewModel.Factory(this))[OnboardingViewModel::class.java]

        // Gate: skip if already completed
        if (viewModel.isAlreadyCompleted()) {
            startActivity(Intent(this, LaunchActivity::class.java))
            finish()
            return
        }

        setContent {
            AircraftTheme {
                OnboardingScreen(
                    onSkip = { completeOnboarding() },
                    onLaunch = { completeOnboarding() },
                    onStarFieldCreated = { starFieldView = it }
                )
            }
        }
    }

    private fun completeOnboarding() {
        viewModel.completeOnboarding()
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
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
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

        // Layer 2: Content overlay. Full width on purpose — header/divider/bottom bar
        // span the window; pager pages center their fixed-size content themselves.
        Column(modifier = Modifier.fillMaxSize()) {
            OnboardingHeader(onSkip = onSkip)
            NeonDivider()

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .testTag("onboarding_pager")
            ) { page ->
                val isActive = pagerState.currentPage == page
                when (page) {
                    0 -> ControlsPage(isActive)
                    1 -> PowerupsPage(isActive)
                    2 -> BulletPage(
                        titleRes = R.string.onboarding_mission_title,
                        bullets = listOf(
                            R.string.onboarding_levels_boss,
                            R.string.onboarding_difficulty_fire,
                            R.string.onboarding_hall_of_heroes
                        ),
                        isActive = isActive
                    )
                    else -> BulletPage(
                        titleRes = R.string.onboarding_puzzle_title,
                        bullets = listOf(
                            R.string.onboarding_puzzle_entry,
                            R.string.onboarding_puzzle_drag,
                            R.string.onboarding_puzzle_scans
                        ),
                        isActive = isActive
                    )
                }
            }

            NeonDivider()

            OnboardingBottomBar(
                pagerState = pagerState,
                onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
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
            .background(HeaderBackground)
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
                color = TextMuted,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .heightIn(min = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkip
                    )
                    .padding(horizontal = 16.dp)
                    .testTag("btn_skip")
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared page scaffold: accent title + staggered entrance for the body
// ---------------------------------------------------------------------------

/**
 * Common page frame. Reveals the title, then hands [content] a `visible` flag the body
 * animates on — [content] stays in ColumnScope so body items keep the centered arrangement.
 */
@Composable
private fun OnboardingPage(
    titleRes: Int,
    isActive: Boolean,
    content: @Composable ColumnScope.(visible: Boolean) -> Unit
) {
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
            .maxContentWidth()
            .padding(32.dp),
        // Body items align left so every bullet starts at the same edge; the title
        // centres itself so wrapping lines do not shift the list's ragged right side.
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = showItems,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -40 }
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(titleRes),
                    color = AccentGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        content(showItems)
    }
}

/** One staggered body element; [delayMillis] spaces items out after the title lands. */
@Composable
private fun StaggeredItem(
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400, delayMillis = delayMillis)) +
                slideInVertically(tween(400, delayMillis = delayMillis)) { -30 },
        modifier = modifier
    ) {
        content()
    }
}

/** Body line. The ▶ marker lives in the localized string, as it always has. */
@Composable
private fun BulletLine(textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        color = TextBody,
        fontSize = 16.sp,
        fontFamily = FontFamily.Monospace,
        modifier = modifier
    )
}

/** Text-only page: a title plus evenly staggered bullet lines. */
@Composable
private fun BulletPage(titleRes: Int, bullets: List<Int>, isActive: Boolean) {
    OnboardingPage(titleRes, isActive) { showItems ->
        bullets.forEachIndexed { index, resId ->
            StaggeredItem(showItems, delayMillis = 100 + index * 120) {
                BulletLine(resId, modifier = Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page: Controls
// ---------------------------------------------------------------------------

@Composable
private fun ControlsPage(isActive: Boolean) {
    OnboardingPage(R.string.onboarding_controls_title, isActive) { showItems ->
        StaggeredItem(showItems, delayMillis = 100) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.jet_plane_2),
                    // Decorative: the instruction lines below say what the plane does.
                    contentDescription = null,
                    modifier = Modifier.size(100.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val instructions = listOf(
            R.string.onboarding_drag_to_move,
            R.string.onboarding_auto_fire,
            R.string.onboarding_collect_powerups
        )
        instructions.forEachIndexed { index, resId ->
            StaggeredItem(showItems, delayMillis = 200 + index * 100) {
                BulletLine(resId, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Page: Power-ups
// ---------------------------------------------------------------------------

@Composable
private fun PowerupsPage(isActive: Boolean) {
    OnboardingPage(R.string.onboarding_powerups_title, isActive) { showItems ->
        val powerups = listOf(
            R.drawable.red_heart_1 to R.string.onboarding_hp_restore,
            R.drawable.shield_1 to R.string.onboarding_invincibility,
            R.drawable.red_box_1 to R.string.onboarding_aoe_rocket,
            R.drawable.timer_1 to R.string.onboarding_time_freeze
        )
        powerups.forEachIndexed { index, (iconRes, textRes) ->
            StaggeredItem(showItems, delayMillis = 100 + index * 120) {
                PowerupRow(
                    iconRes = iconRes,
                    textRes = textRes,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        StaggeredItem(showItems, delayMillis = 580) {
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
            // Decorative: the label beside it is read out immediately after.
            contentDescription = null,
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
            .background(HeaderBackground)
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
            repeat(PAGE_COUNT) { index ->
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
                .height(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(AccentGreen)
                .clickable {
                    if (pagerState.currentPage < PAGE_COUNT - 1) onNext() else onLaunch()
                }
                .testTag("btn_next"),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = pagerState.currentPage == PAGE_COUNT - 1,
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
                    color = BackgroundDark,
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
