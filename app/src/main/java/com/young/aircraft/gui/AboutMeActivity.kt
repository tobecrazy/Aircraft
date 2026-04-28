package com.young.aircraft.gui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.young.aircraft.R

class AboutMeActivity : AppCompatActivity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MaterialTheme {
                AboutMeScreen(
                    onBack = { finish() },
                    onOpenRepo = {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                getString(R.string.about_me_project_repo_url).toUri()
                            )
                        )
                    }
                )
            }
        }
    }
}

private const val PROFILE_IMAGE_URL =
    "https://images.cnblogs.com/cnblogs_com/tobecrazy/432338/o_250810143405_Card.png"

private val BackgroundDark = Color(0xFF0F1118)
private val HeaderBackground = Color(0xFF161A26)
private val AccentGreen = Color(0xFF00FF88)
private val CardBackground = Color(0x20252A3A)
private val CardBorder = Color(0x2200FF88)
private val TextPrimary = Color(0xFFCDD2E0)
private val TextSecondary = Color(0x88FFFFFF)
private val DividerGreen = Color(0x4400FF88)
private val HeroGradientStart = Color(0xFF1B2234)
private val HeroGradientEnd = Color(0xFF112722)
private val HeroPanelBackground = Color(0x1AFFFFFF)

@Composable
private fun AboutMeScreen(
    onBack: () -> Unit,
    onOpenRepo: () -> Unit
) {
    val repoUrl = stringResource(R.string.about_me_project_repo_url)
    val repoLine = stringResource(
        R.string.about_me_project_repo_line,
        stringResource(R.string.about_github_label),
        repoUrl
    )
    val developerContent = stringResource(R.string.about_me_content)
    val projectContent = stringResource(R.string.about_me_project_content, repoLine)

    val developerParagraphs = remember(developerContent) { developerContent.toParagraphs() }
    val projectParagraphs = remember(projectContent) { projectContent.toParagraphs() }

    Scaffold(
        containerColor = BackgroundDark,
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
        ),
        topBar = {
            AboutMeTopBar(onBack = onBack)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("about_me_list"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                AboutMeHeroCard(
                    repoUrl = repoUrl,
                    onOpenRepo = onOpenRepo
                )
            }
            item {
                SectionHeader(title = stringResource(R.string.about_me_developer_section_title))
            }
            item {
                NarrativeCard(
                    title = stringResource(R.string.about_me_summary),
                    paragraphs = developerParagraphs
                )
            }
            item {
                SectionHeader(title = stringResource(R.string.about_me_project_section_title))
            }
            item {
                ProjectNarrativeCard(
                    title = stringResource(R.string.about_aircraft_title),
                    repoLine = repoLine,
                    paragraphs = projectParagraphs,
                    onOpenRepo = onOpenRepo
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AboutMeTopBar(onBack: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.about_me_title),
                color = AccentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.25.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_header_back),
                    contentDescription = stringResource(R.string.history_back),
                    tint = AccentGreen
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = HeaderBackground,
            titleContentColor = AccentGreen,
            navigationIconContentColor = AccentGreen
        ),
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .background(AccentGreen, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun AboutMeHeroCard(
    repoUrl: String,
    onOpenRepo: () -> Unit
) {
    val cardShape = RoundedCornerShape(20.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(HeroGradientStart, HeroGradientEnd)
                )
            )
            .border(1.dp, CardBorder, cardShape)
            .padding(18.dp)
    ) {
        val isWide = maxWidth >= 720.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeroImagePanel(
                    modifier = Modifier.weight(0.42f)
                )
                HeroTextPanel(
                    repoUrl = repoUrl,
                    onOpenRepo = onOpenRepo,
                    modifier = Modifier.weight(0.58f)
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                HeroImagePanel()
                HeroTextPanel(
                    repoUrl = repoUrl,
                    onOpenRepo = onOpenRepo
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HeroTextPanel(
    repoUrl: String,
    onOpenRepo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroChip(text = stringResource(R.string.app_name))

        Text(
            text = stringResource(R.string.about_me_title),
            color = Color.White,
            fontSize = 28.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = stringResource(R.string.about_me_summary),
            color = TextPrimary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            fontFamily = FontFamily.Monospace
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeroChip(text = stringResource(R.string.about_github_label), accent = true)
            HeroChip(text = stringResource(R.string.about_aircraft_title))
        }

        Button(
            onClick = onOpenRepo,
            modifier = Modifier.testTag("about_me_open_repo"),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentGreen,
                contentColor = BackgroundDark
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.about_github_label),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.15.sp
            )
        }

        SelectionContainer {
            Text(
                text = repoUrl,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun HeroChip(
    text: String,
    accent: Boolean = false
) {
    val background = if (accent) Color(0x2600FF88) else HeroPanelBackground
    val border = if (accent) Color(0x6600FF88) else Color(0x22FFFFFF)
    val textColor = if (accent) AccentGreen else Color(0xFFD8E0EF)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HeroImagePanel(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HeroPanelBackground)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .heightIn(min = 220.dp),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(PROFILE_IMAGE_URL)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.about_me_profile_content_description),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(HeroPanelBackground),
            error = ColorPainter(HeroPanelBackground)
        )
    }
}

@Composable
private fun NarrativeCard(
    title: String,
    paragraphs: List<String>
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectNarrativeCard(
    title: String,
    repoLine: String,
    paragraphs: List<String>,
    onOpenRepo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            SelectionContainer {
                Text(
                    text = repoLine,
                    color = AccentGreen,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            HorizontalDivider(color = DividerGreen)

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    paragraphs.forEach { paragraph ->
                        Text(
                            text = paragraph,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Button(
                onClick = onOpenRepo,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0x2600FF88),
                    contentColor = AccentGreen
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_github_label),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun String.toParagraphs(): List<String> {
    return split("\n\n")
        .map(String::trim)
        .filter(String::isNotEmpty)
}
