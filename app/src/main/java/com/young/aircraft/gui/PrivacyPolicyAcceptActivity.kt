package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.databinding.ActivityPrivacyPolicyAcceptBinding
import java.util.Locale

class PrivacyPolicyAcceptActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPrivacyPolicyAcceptBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // Already accepted → go straight to LaunchActivity
        val prefs = getSharedPreferences("aircraft_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("privacy_policy_accepted", false)) {
            startActivity(Intent(this, LaunchActivity::class.java))
            finish()
            return
        }

        binding = ActivityPrivacyPolicyAcceptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        binding.webView.apply {
            settings.allowFileAccess = true
            settings.javaScriptEnabled = true
            settings.loadsImagesAutomatically = true
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
            setOnScrollChangeListener { v, _, scrollY, _, _ ->
                val wv = v as WebView
                val contentHeight = (wv.contentHeight * wv.scale).toInt()
                val viewHeight = wv.height
                if (scrollY + viewHeight >= contentHeight - 10) {
                    enableButtons()
                }
            }
            val page = if (Locale.getDefault().language == "zh") "privacy_policy.html" else "privacy_policy_en.html"
            loadUrl("file:///android_asset/$page")
        }

        binding.btnAccept.setOnClickListener {
            prefs.edit().putBoolean("privacy_policy_accepted", true).apply()
            startActivity(Intent(this, LaunchActivity::class.java))
            finish()
        }

        binding.btnReject.setOnClickListener {
            finishAffinity()
        }
    }

    private fun enableButtons() {
        binding.btnAccept.apply { isEnabled = true; alpha = 1.0f }
        binding.btnReject.apply { isEnabled = true; alpha = 1.0f }
    }
}
