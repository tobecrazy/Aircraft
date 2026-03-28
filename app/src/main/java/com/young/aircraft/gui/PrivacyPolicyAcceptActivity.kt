package com.young.aircraft.gui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityPrivacyPolicyAcceptBinding
import com.young.aircraft.providers.SettingsRepository
import java.util.Locale

/**
 * Cinematic privacy policy acceptance gate — the app's entry point.
 *
 * FLOW: check pref → skip to Onboarding if accepted / show cinematic screen if not
 * Accept → save pref → OnboardingActivity | Reject → finishAffinity()
 */
class PrivacyPolicyAcceptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPrivacyPolicyAcceptBinding
    private lateinit var settingsRepository: SettingsRepository
    private var acceptPulseAnimator: ObjectAnimator? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        settingsRepository = SettingsRepository(this)

        // Already accepted → route to onboarding gate (it handles its own skip)
        if (settingsRepository.isPrivacyPolicyAccepted()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityPrivacyPolicyAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set localized tactical button text
        binding.btnAccept.text = getString(R.string.privacy_policy_accept_tactical, getString(R.string.privacy_policy_accept))
        binding.btnReject.text = getString(R.string.privacy_policy_reject_tactical, getString(R.string.privacy_policy_reject))

        // Start star field animation
        binding.starField.startAnimation()

        // Configure WebView
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        binding.webView.apply {
            setBackgroundColor(0x000F1118)
            settings.allowFileAccess = true
            settings.javaScriptEnabled = true
            settings.loadsImagesAutomatically = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
            settings.loadWithOverviewMode = true

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Inject dark theme CSS to match game aesthetic
                    view?.evaluateJavascript(
                        """
                        (function() {
                            var style = document.createElement('style');
                            style.textContent = 'body { background-color: #0F1118 !important; color: #CCFFFFFF !important; font-family: monospace !important; padding: 8px !important; } a { color: #00FF88 !important; } h1,h2,h3 { color: #00FF88 !important; }';
                            document.head.appendChild(style);
                        })()
                        """.trimIndent(),
                        null
                    )
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    // Fallback: enable buttons even if WebView fails to load
                    if (request?.isForMainFrame == true) {
                        enableButtons()
                    }
                }
            }

            setOnScrollChangeListener { v, _, scrollY, _, _ ->
                val wv = v as WebView
                if (scrollY > 0 && !wv.canScrollVertically(1)) {
                    enableButtons()
                }
            }

            val page = if (Locale.getDefault().language == "zh") "privacy_policy.html" else "privacy_policy_en.html"
            loadUrl("file:///android_asset/$page")
        }

        // Reject button is always enabled
        binding.btnReject.isEnabled = true
        binding.btnReject.alpha = 1.0f
        binding.btnReject.setOnClickListener {
            finishAffinity()
        }

        // Accept button requires scrolling to bottom
        binding.btnAccept.isEnabled = false
        binding.btnAccept.alpha = 0.3f
        binding.btnAccept.setOnClickListener {
            settingsRepository.setPrivacyPolicyAccepted(true)
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // Forward touch activity to StarFieldView for idle timer reset
        if (::binding.isInitialized) {
            binding.starField.onUserActivity()
        }
        return super.dispatchTouchEvent(ev)
    }

    private var acceptButtonEnabled = false

    private fun enableButtons() {
        if (acceptButtonEnabled) return
        acceptButtonEnabled = true

        // Fade accept button from disabled (0.3) to enabled over 300ms
        binding.btnAccept.animate().alpha(1.0f).setDuration(300).start()
        binding.btnAccept.isEnabled = true

        // Start neon pulse animation on accept button
        acceptPulseAnimator = ObjectAnimator.ofFloat(binding.btnAccept, "alpha", 1.0f, 0.7f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            startDelay = 300 // wait for fade-in to complete
            start()
        }
    }

    override fun onDestroy() {
        // Cancel animator to prevent Activity leak
        acceptPulseAnimator?.cancel()
        acceptPulseAnimator = null
        if (::binding.isInitialized) {
            binding.starField.stopAnimation()
        }
        super.onDestroy()
    }
}
