package com.young.aircraft.gui

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.young.aircraft.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

class AboutMeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContent { AboutMeScreen(onBack = { finish() }) }
    }
}

private val BackgroundDark = Color(0xFF0F1118)
private val HeaderBackground = Color(0xFF161A26)
private val AccentGreen = Color(0xFF00FF88)
private val CardBackground = Color(0x20252A3A)
private val CardBorder = Color(0x2200FF88)
private val TextPrimary = Color(0xFFCDD2E0)
private val TextSecondary = Color(0x88FFFFFF)
private val DividerGreen = Color(0x4400FF88)

@Composable
private fun AboutMeScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
    ) {
        AboutMeHeader(onBack)
        HorizontalDivider(thickness = 1.dp, color = DividerGreen)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            ProfileImageCard()
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = stringResource(R.string.about_me_developer_section_title))
            Spacer(modifier = Modifier.height(10.dp))
            AboutContentCard()
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(title = stringResource(R.string.about_me_project_section_title))
            Spacer(modifier = Modifier.height(10.dp))
            ProjectContentCard()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutMeHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(HeaderBackground)
    ) {
        Text(
            text = "\u25C0",
            color = AccentGreen,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { onBack() }
                .padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.about_me_title),
            color = AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.25.sp,
            modifier = Modifier.align(Alignment.Center)
        )
    }
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
private fun ProfileImageCard() {
    val cardShape = RoundedCornerShape(12.dp)
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                URL("https://images.cnblogs.com/cnblogs_com/tobecrazy/432338/o_250810143405_Card.png")
                    .openStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (bitmap != null) {
            imageBitmap = bitmap.asImageBitmap()
        } else {
            loadFailed = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CardBackground, cardShape)
            .border(1.dp, CardBorder, cardShape)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap ?: return,
                    contentDescription = stringResource(R.string.about_me_profile_content_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.FillWidth
                )
            }

            loadFailed -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.about_me_image_unavailable),
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = AccentGreen,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutContentCard() {
    val cardShape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CardBackground, cardShape)
            .border(1.dp, CardBorder, cardShape)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.about_me_content),
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun ProjectContentCard() {
    val cardShape = RoundedCornerShape(12.dp)
    val repoLine = stringResource(
        R.string.about_me_project_repo_line,
        stringResource(R.string.about_github_label),
        stringResource(R.string.about_me_project_repo_url)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(CardBackground, cardShape)
            .border(1.dp, CardBorder, cardShape)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.about_me_project_content, repoLine),
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 20.sp,
            textAlign = TextAlign.Start
        )
    }
}
