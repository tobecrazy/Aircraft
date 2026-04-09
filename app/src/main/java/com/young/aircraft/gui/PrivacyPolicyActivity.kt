package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.young.aircraft.R
import com.young.aircraft.BuildConfig
import com.young.aircraft.databinding.ActivityPrivacyPolicyBinding
import java.util.Locale

class PrivacyPolicyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPrivacyPolicyBinding
    private var currentPolicyPage = ""
    private var hasMainFrameError = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Aircraft_History)
        super.onCreate(savedInstanceState)

        binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        configureWindow()
        setContentView(binding.root)

        currentPolicyPage = resolveInitialPolicyPage()

        applyWindowInsets()
        setupHeader()
        setupHero()
        setupRetry()
        configureWebView()
        loadPolicyPage(currentPolicyPage)
    }

    private fun configureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            binding.header.updatePadding(top = systemBars.top)
            insets
        }
        ViewCompat.requestApplyInsets(binding.rootContent)
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupHero() {
        binding.tvPolicySummary.text = getString(R.string.privacy_policy_summary)
        binding.tvSourceChip.text = getString(R.string.privacy_policy_source_chip)
        updateLanguageChip(currentPolicyPage)
    }

    private fun setupRetry() {
        binding.btnRetry.setOnClickListener {
            loadPolicyPage(currentPolicyPage)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        binding.webView.apply {
            setBackgroundColor(Color.TRANSPARENT)
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
        }
    }

    private fun loadPolicyPage(page: String) {
        currentPolicyPage = page
        hasMainFrameError = false
        updateLanguageChip(page)
        showLoadingState()
        binding.webView.loadUrl("$ASSET_PREFIX$page")
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
        binding.loadingState.visibility = View.VISIBLE
        binding.errorState.visibility = View.GONE
    }

    private fun showContentState() {
        binding.loadingState.visibility = View.GONE
        binding.errorState.visibility = View.GONE
    }

    private fun showErrorState() {
        binding.loadingState.visibility = View.GONE
        binding.errorState.visibility = View.VISIBLE
    }

    private fun updateLanguageChip(source: String) {
        binding.tvLanguageChip.text = when (resolvePolicyPage(source)) {
            POLICY_ZH -> getString(R.string.privacy_policy_language_zh)
            else -> getString(R.string.privacy_policy_language_en)
        }
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
        if (::binding.isInitialized) {
            binding.webView.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
        }
        super.onDestroy()
    }

    private companion object {
        const val POLICY_ZH = "privacy_policy.html"
        const val POLICY_EN = "privacy_policy_en.html"
        const val ASSET_PREFIX = "file:///android_asset/"
    }
}
