package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.young.aircraft.R

class PrivacyPolicyActivity : AppCompatActivity() {
    lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)
        WebView.setWebContentsDebuggingEnabled(true);
        webView = findViewById(R.id.web_view)
        webView.settings.javaScriptEnabled = true;
        webView.webViewClient = WebViewClient()
        webView.apply {
            loadUrl("file:///android_asset/privacy policy.html")
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
            settings.loadWithOverviewMode = true
        }
    }
}