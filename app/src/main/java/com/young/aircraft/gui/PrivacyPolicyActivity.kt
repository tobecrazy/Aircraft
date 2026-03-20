package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.BuildConfig
import com.young.aircraft.databinding.ActivityPrivacyPolicyBinding
import java.util.Locale

class PrivacyPolicyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPrivacyPolicyBinding

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        binding = ActivityPrivacyPolicyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

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
            val page = if (Locale.getDefault().language == "zh") "privacy_policy.html" else "privacy_policy_en.html"
            loadUrl("file:///android_asset/$page")
        }
    }
}
