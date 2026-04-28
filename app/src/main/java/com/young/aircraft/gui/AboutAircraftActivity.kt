package com.young.aircraft.gui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.net.toUri
import androidx.core.view.updatePadding
import coil.load
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import com.young.aircraft.databinding.ActivityAboutAircraftBinding

class AboutAircraftActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutAircraftBinding
    private val githubUrl by lazy { getString(R.string.about_me_project_repo_url) }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutAircraftBinding.inflate(layoutInflater)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContent) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.rootContent)

        binding.btnBack.setOnClickListener { finish() }

        setupBanner()
        setupDescription()
        setupGithubLink()
        loadProjectImage()
    }

    @SuppressLint("SetTextI18n")
    private fun setupBanner() {
        binding.tvVersionBadge.text = getString(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME)
        binding.tvPlatformBadge.text = "Android ${android.os.Build.VERSION.RELEASE}"
        binding.tvStackBadge.text = getString(R.string.about_stack_badge)
    }

    private fun setupDescription() {
        binding.tvDescription.text = getString(R.string.about_description)
    }

    private fun setupGithubLink() {
        binding.tvGithubUrl.text = githubUrl
        val openRepo = {
            startActivity(Intent(Intent.ACTION_VIEW, githubUrl.toUri()))
        }
        binding.llGithubLink.setOnClickListener { openRepo() }
        binding.btnOpenGithubPrimary.setOnClickListener { openRepo() }
        binding.btnOpenGithubSecondary.setOnClickListener { openRepo() }
    }

    private fun loadProjectImage() {
        val imageUrl = "https://images.cnblogs.com/cnblogs_com/tobecrazy/432338/o_250810143405_Card.png"
        binding.ivProject.load(imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
            listener(
                onStart = {
                    binding.progressImage.visibility = View.VISIBLE
                    binding.tvImageFallback.visibility = View.GONE
                    binding.ivProject.visibility = View.INVISIBLE
                },
                onSuccess = { _, _ ->
                    binding.progressImage.visibility = View.GONE
                    binding.ivProject.visibility = View.VISIBLE
                },
                onError = { _, _ ->
                    binding.progressImage.visibility = View.GONE
                    binding.tvImageFallback.visibility = View.VISIBLE
                }
            )
        }
    }
}
