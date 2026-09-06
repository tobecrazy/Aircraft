package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.ui.theme.AccentGreen
import com.young.aircraft.ui.theme.AircraftTheme
import com.young.aircraft.ui.theme.BackgroundDark
import com.young.aircraft.ui.theme.HeaderBackground
import com.young.aircraft.ui.theme.NeonDivider
import com.young.aircraft.ui.theme.TextBody
import com.young.aircraft.ui.theme.TextBright
import com.young.aircraft.utils.DebugTools
import java.util.Locale

class PrivacyPolicyActivity : AppCompatActivity() {
    private var policyWebView: WebView? = null
    private var currentPolicyPage = ""
    private var hasMainFrameError = false

    // Overlay/chip state lives here so WebViewClient callbacks can mutate it directly.
    private var loadingVisible by mutableStateOf(true)
    private var errorVisible by mutableStateOf(false)
    private var languageChipText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Aircraft_Common)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        currentPolicyPage = resolveInitialPolicyPage()
        languageChipText = languageChipFor(currentPolicyPage)

        setContent {
            AircraftTheme {
                PrivacyPolicyScreen(
                    languageChip = languageChipText,
                    loadingVisible = loadingVisible,
                    errorVisible = errorVisible,
                    onBack = { finish() },
                    onRetry = { loadPolicyPage(currentPolicyPage) },
                    onWebViewCreated = ::createConfiguredWebView
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(context: Context): WebView {
        DebugTools.enableWebViewDebugging()

        return WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.useWideViewPort = true
            settings.loadsImagesAutomatically = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            settings.loadWithOverviewMode = true
            settings.displayZoomControls = false
            settings.builtInZoomControls = false
            settings.setSupportZoom(false)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    showLoadingState()
                    if (url != null) {
                        updateLanguageChip(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) {
                        currentPolicyPage = resolvePolicyPage(url)
                        updateLanguageChip(url)
                    }
                    if (!hasMainFrameError) {
                        showContentState()
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return handleNavigationRequest(request?.url?.toString())
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return handleNavigationRequest(url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        hasMainFrameError = true
                        showErrorState()
                    }
                }
            }
        }.also { webView ->
            policyWebView = webView
            loadPolicyPage(currentPolicyPage)
        }
    }

    private fun loadPolicyPage(page: String) {
        currentPolicyPage = page
        hasMainFrameError = false
        updateLanguageChip(page)
        showLoadingState()
        policyWebView?.loadUrl("$ASSET_PREFIX$page")
    }

    private fun handleNavigationRequest(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        return when {
            url.startsWith(ASSET_PREFIX) -> false
            url.startsWith("mailto:", ignoreCase = true) -> {
                launchExternalIntent(Intent.ACTION_SENDTO, url)
                true
            }

            url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) -> {
                launchExternalIntent(Intent.ACTION_VIEW, url)
                true
            }

            else -> false
        }
    }

    private fun launchExternalIntent(action: String, url: String) {
        runCatching {
            startActivity(Intent(action, url.toUri()))
        }
    }

    private fun showLoadingState() {
        loadingVisible = true
        errorVisible = false
    }

    private fun showContentState() {
        loadingVisible = false
        errorVisible = false
    }

    private fun showErrorState() {
        loadingVisible = false
        errorVisible = true
    }

    private fun updateLanguageChip(source: String) {
        languageChipText = languageChipFor(resolvePolicyPage(source))
    }

    private fun languageChipFor(page: String): String = when (page) {
        POLICY_ZH -> getString(R.string.privacy_policy_language_zh)
        else -> getString(R.string.privacy_policy_language_en)
    }

    private fun resolveInitialPolicyPage(): String {
        return if (Locale.getDefault().language == Locale.CHINESE.language) {
            POLICY_ZH
        } else {
            POLICY_EN
        }
    }

    private fun resolvePolicyPage(source: String): String {
        return if (source.endsWith(POLICY_ZH)) {
            POLICY_ZH
        } else {
            POLICY_EN
        }
    }

    override fun onDestroy() {
        policyWebView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroy()
    }

    private companion object {
        val POLICY_ZH = AircraftConstants.PrivacyPolicy.ASSET_ZH
        val POLICY_EN = AircraftConstants.PrivacyPolicy.ASSET_EN
        val ASSET_PREFIX = AircraftConstants.PrivacyPolicy.ASSET_PREFIX
    }
}

// Hero/panel visuals lifted from settings_hero_bg / settings_chip_bg /
// settings_chip_active_bg / device_info_card_bg and the legacy overlay scrims.
private val HeroGradient = Brush.linearGradient(
    colors = listOf(Color(0x2E162033), Color(0x1F15242F))
)
private val HeroBorder = Color(0x3300FF88)
private val ChipBg = Color(0x18FFFFFF)
private val ChipBorder = Color(0x28FFFFFF)
private val ChipActiveBg = Color(0x2600FF88)
private val ChipActiveBorder = Color(0x6600FF88)
private val DocumentCardBg = Color(0x20252A3A)
private val DocumentCardBorder = Color(0x2200FF88)
private val LoadingScrim = Color(0xCC0F1118)
private val ErrorScrim = Color(0xE60F1118)
private val SectionLabel = Color(0x66FFFFFF)

@Composable
internal fun PrivacyPolicyScreen(
    languageChip: String,
    loadingVisible: Boolean,
    errorVisible: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onWebViewCreated: (Context) -> WebView
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .safeDrawingPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBackground)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("btn_back")
                    .padding(start = 4.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_header_back),
                    contentDescription = stringResource(R.string.history_back),
                    tint = AccentGreen
                )
            }
            Text(
                text = stringResource(R.string.privacy_policy_title),
                modifier = Modifier.align(Alignment.Center),
                color = AccentGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.25.sp
            )
        }
        NeonDivider()

        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            HeroPanel(languageChip = languageChip)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(AccentGreen)
                )
                Text(
                    text = stringResource(R.string.privacy_policy_document_header),
                    color = SectionLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            DocumentCard(
                loadingVisible = loadingVisible,
                errorVisible = errorVisible,
                onRetry = onRetry,
                onWebViewCreated = onWebViewCreated,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun HeroPanel(languageChip: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(HeroGradient, RoundedCornerShape(18.dp))
            .border(1.dp, HeroBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        ChipPill(text = stringResource(R.string.privacy_policy_badge), tint = TextBright)

        Text(
            text = stringResource(R.string.privacy_policy_banner_title),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 14.dp)
        )

        Text(
            text = stringResource(R.string.privacy_policy_summary),
            color = TextBody,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 8.dp)
        )

        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active-style chip for the resolved document language.
            Text(
                text = languageChip,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(ChipActiveBg, RoundedCornerShape(999.dp))
                    .border(1.dp, ChipActiveBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            ChipPill(text = stringResource(R.string.privacy_policy_source_chip), tint = TextBright)
        }
    }
}

@Composable
private fun ChipPill(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(ChipBg, RoundedCornerShape(999.dp))
            .border(1.dp, ChipBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun DocumentCard(
    loadingVisible: Boolean,
    errorVisible: Boolean,
    onRetry: () -> Unit,
    onWebViewCreated: (Context) -> WebView,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = DocumentCardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, DocumentCardBorder)
    ) {
        Box(modifier = Modifier.padding(1.dp)) {
            AndroidView(
                factory = onWebViewCreated,
                modifier = Modifier.fillMaxSize()
            )

            if (loadingVisible && !errorVisible) {
                LoadingOverlay(modifier = Modifier.matchParentSize())
            }
            if (errorVisible) {
                ErrorOverlay(onRetry = onRetry, modifier = Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun LoadingOverlay(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(LoadingScrim),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        // Static ring instead of an indeterminate spinner: infinite Compose animations keep the
        // Robolectric main looper permanently busy and stall ActivityScenario launch (~2min).
        Box(
            modifier = Modifier
                .size(22.dp)
                .border(3.dp, AccentGreen, androidx.compose.foundation.shape.CircleShape)
        )
        Text(
            text = stringResource(R.string.privacy_policy_loading),
            color = TextBright,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

@Composable
private fun ErrorOverlay(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(ErrorScrim).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.privacy_policy_error_title),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.privacy_policy_error_message),
            color = TextBody,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .background(ChipActiveBg, RoundedCornerShape(12.dp))
                .border(1.dp, ChipActiveBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("btn_retry")
        ) {
            Text(
                text = stringResource(R.string.privacy_policy_retry),
                color = AccentGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
