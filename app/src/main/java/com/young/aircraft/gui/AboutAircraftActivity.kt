package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.ui.theme.TextMuted
import com.young.aircraft.viewmodel.AboutAircraftViewModel
import com.young.aircraft.viewmodel.AboutAircraftUiState
import com.young.aircraft.viewmodel.ImageLoadState

// Panel visuals lifted from device_info_hero_bg / device_info_card_bg /
// device_info_item_bg / the legacy outlined MaterialButton styling.
private val HeroBg = Color(0x3300FF88)
private val CardBg = Color(0x20252A3A)
private val CardBorder = Color(0x2200FF88)
private val ItemBg = Color(0x1A252A3A)
private val ItemBorder = Color(0x33FFFFFF)
private val GaugeBg = Color(0x18FFFFFF)
private val CtaBg = Color(0x2600FF88)
private val CtaBorder = Color(0x6600FF88)
private val SpecLabel = Color(0x88FFFFFF)
private val SectionLabel = Color(0x66FFFFFF)
private val SpecDivider = Color(0x10FFFFFF)

class AboutAircraftActivity : AppCompatActivity() {

    private lateinit var viewModel: AboutAircraftViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this, AboutAircraftViewModel.Factory(this))[AboutAircraftViewModel::class.java]

        setContent {
            AircraftTheme {
                val state by viewModel.uiState.collectAsState()
                AboutAircraftScreen(
                    state = state,
                    onBack = { finish() },
                    onOpenRepo = ::openRepo,
                    onImageClick = ::openProjectImageDetails,
                    onImageLoadStarted = viewModel::onImageLoadStarted,
                    onImageLoadSuccess = viewModel::onImageLoadSuccess,
                    onImageLoadError = viewModel::onImageLoadError
                )
            }
        }
    }

    private fun openRepo() {
        startActivity(Intent(Intent.ACTION_VIEW, viewModel.uiState.value.githubUrl.toUri()))
    }

    private fun openProjectImageDetails() {
        val projectImage = SupperBannerItem(
            name = getString(R.string.about_aircraft_title),
            description = getString(R.string.about_banner_summary),
            image = SupperBannerImage.Network(AircraftConstants.Urls.CONTACT_US_QR_CODE)
        )
        startActivity(ShowImageDetailsActivity.createIntent(this, projectImage))
    }
}

@Composable
internal fun AboutAircraftScreen(
    state: AboutAircraftUiState,
    onBack: () -> Unit,
    onOpenRepo: () -> Unit,
    onImageClick: () -> Unit,
    onImageLoadStarted: () -> Unit,
    onImageLoadSuccess: () -> Unit,
    onImageLoadError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        AboutHeader(onBack = onBack)
        NeonDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            HeroPanel(state = state, onOpenRepo = onOpenRepo)

            ProjectImageCard(
                state = state,
                onClick = onImageClick,
                onImageLoadStarted = onImageLoadStarted,
                onImageLoadSuccess = onImageLoadSuccess,
                onImageLoadError = onImageLoadError
            )

            SectionHeader(titleRes = R.string.about_section_overview)

            Surface(
                modifier = Modifier.padding(top = 10.dp),
                shape = RoundedCornerShape(12.dp),
                color = CardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Text(
                    text = state.description,
                    color = TextBody,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }

            SectionHeader(titleRes = R.string.about_section_specs)
            StatsRow()
            SpecsCard()

            SectionHeader(titleRes = R.string.about_section_source)
            GithubCard(onOpenRepo = onOpenRepo)

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun AboutHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HeaderBackground)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag("btn_back")
                .padding(start = 4.dp)
                .size(48.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_header_back),
                contentDescription = stringResource(R.string.history_back),
                tint = AccentGreen
            )
        }
        Text(
            text = stringResource(R.string.about_aircraft_title),
            modifier = Modifier.align(Alignment.Center),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp
        )
    }
}

@Composable
private fun HeroPanel(state: AboutAircraftUiState, onOpenRepo: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = HeroBg
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                text = stringResource(R.string.about_banner_badge),
                color = TextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(GaugeBg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 14.dp)
            )

            Text(
                text = stringResource(R.string.about_banner_summary),
                color = TextBody,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(modifier = Modifier.padding(top = 14.dp)) {
                BadgeChip(text = state.versionText, tint = AccentGreen)
                Spacer(modifier = Modifier.width(8.dp))
                BadgeChip(text = state.platformText, tint = TextBright)
                Spacer(modifier = Modifier.width(8.dp))
                BadgeChip(text = state.stackBadge, tint = TextBright)
            }

            GithubCta(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag("github_cta_primary"),
                onClick = onOpenRepo
            )
        }
    }
}

@Composable
private fun BadgeChip(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(GaugeBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun GithubCta(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(CtaBg, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = stringResource(R.string.about_source_cta),
            color = AccentGreen,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ProjectImageCard(
    state: AboutAircraftUiState,
    onClick: () -> Unit,
    onImageLoadStarted: () -> Unit,
    onImageLoadSuccess: () -> Unit,
    onImageLoadError: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(400.dp),
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Box(modifier = Modifier.padding(2.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(AircraftConstants.Urls.CONTACT_US_QR_CODE)
                    .crossfade(true)
                    .listener(
                        onStart = { _ -> onImageLoadStarted() },
                        onSuccess = { _, _ -> onImageLoadSuccess() },
                        onError = { _, _ -> onImageLoadError() }
                    )
                    .build(),
                contentDescription = stringResource(R.string.about_aircraft_title),
                // ic_placeholder is a <shape> drawable that painterResource cannot load;
                // the dark card plus spinner/fallback overlay cover those states instead.
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .testTag("project_image")
                    .clickable(onClick = onClick)
            )

            if (state.imageLoadState == ImageLoadState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentGreen
                )
            } else if (state.imageLoadState == ImageLoadState.Error) {
                Text(
                    text = stringResource(R.string.about_me_image_unavailable),
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(titleRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(AccentGreen)
        )
        Text(
            text = stringResource(titleRes),
            color = SectionLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun StatsRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StatCard(label = stringResource(R.string.about_stat_levels), value = "10", modifier = Modifier.weight(1f))
        StatCard(label = stringResource(R.string.about_stat_enemies), value = "15", modifier = Modifier.weight(1f))
        StatCard(label = stringResource(R.string.about_stat_jets), value = "4", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = ItemBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, ItemBorder)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            Text(
                text = label,
                color = SpecLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.15.sp
            )
            Text(
                text = value,
                color = AccentGreen,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun SpecsCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column {
            SpecRow(labelRes = R.string.about_spec_engine, valueRes = R.string.about_spec_engine_val, valueColor = TextBody)
            SpecDividerRow()
            SpecRow(labelRes = R.string.about_spec_fps, valueRes = R.string.about_spec_fps_val, valueColor = AccentGreen)
            SpecDividerRow()
            SpecRow(labelRes = R.string.about_spec_difficulty, valueRes = R.string.about_spec_difficulty_val, valueColor = TextBody)
            SpecDividerRow()
            SpecRow(labelRes = R.string.about_spec_powerups, valueRes = R.string.about_spec_powerups_val, valueColor = TextBody)
        }
    }
}

@Composable
private fun SpecDividerRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SpecDivider)
    )
}

@Composable
private fun SpecRow(labelRes: Int, valueRes: Int, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(
            text = stringResource(labelRes),
            color = SpecLabel,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(valueRes),
            color = valueColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun GithubCard(onOpenRepo: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .testTag("github_source_card")
            .clickable(onClick = onOpenRepo),
        shape = RoundedCornerShape(12.dp),
        color = CardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.about_github_label),
                color = TextBright,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(GaugeBg, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            Text(
                text = stringResource(R.string.about_source_summary),
                color = TextBody,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp)
            )

            Row(
                modifier = Modifier.padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.about_me_project_repo_url),
                    color = AccentGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                GithubCta(onClick = onOpenRepo)
            }
        }
    }
}
