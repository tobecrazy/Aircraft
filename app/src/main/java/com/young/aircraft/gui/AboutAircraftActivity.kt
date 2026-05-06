package com.young.aircraft.gui

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.young.aircraft.R
import com.young.aircraft.data.AircraftConstants
import com.young.aircraft.databinding.ActivityAboutAircraftBinding
import com.young.aircraft.viewmodel.AboutAircraftViewModel
import com.young.aircraft.viewmodel.AboutAircraftUiState
import com.young.aircraft.viewmodel.ImageLoadState
import kotlinx.coroutines.launch

class AboutAircraftActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutAircraftBinding
    private lateinit var viewModel: AboutAircraftViewModel

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

        viewModel = ViewModelProvider(this, AboutAircraftViewModel.Factory(this))[AboutAircraftViewModel::class.java]

        binding.btnBack.setOnClickListener { finish() }

        setupGithubLink()
        loadProjectImage()
        setupProjectImageClick()
        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: AboutAircraftUiState) {
        binding.tvVersionBadge.text = state.versionText
        binding.tvPlatformBadge.text = state.platformText
        binding.tvStackBadge.text = state.stackBadge
        binding.tvDescription.text = state.description

        when (state.imageLoadState) {
            ImageLoadState.Loading -> {
                binding.progressImage.visibility = View.VISIBLE
                binding.tvImageFallback.visibility = View.GONE
                binding.ivProject.visibility = View.INVISIBLE
            }
            ImageLoadState.Success -> {
                binding.progressImage.visibility = View.GONE
                binding.ivProject.visibility = View.VISIBLE
                binding.tvImageFallback.visibility = View.GONE
            }
            ImageLoadState.Error -> {
                binding.progressImage.visibility = View.GONE
                binding.tvImageFallback.visibility = View.VISIBLE
                binding.ivProject.visibility = View.INVISIBLE
            }
        }
    }

    private fun setupGithubLink() {
        val openRepo = {
            val url = viewModel.uiState.value.githubUrl
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }
        binding.tvGithubUrl.text = viewModel.uiState.value.githubUrl
        binding.llGithubLink.setOnClickListener { openRepo() }
        binding.btnOpenGithubPrimary.setOnClickListener { openRepo() }
        binding.btnOpenGithubSecondary.setOnClickListener { openRepo() }
    }

    private fun loadProjectImage() {
        binding.ivProject.load(AircraftConstants.Urls.CONTACT_US_QR_CODE) {
            crossfade(true)
            placeholder(R.drawable.ic_placeholder)
            error(R.drawable.ic_placeholder)
            listener(
                onStart = { viewModel.onImageLoadStarted() },
                onSuccess = { _, _ -> viewModel.onImageLoadSuccess() },
                onError = { _, _ -> viewModel.onImageLoadError() }
            )
        }
    }

    private fun setupProjectImageClick() {
        binding.ivProject.setOnClickListener {
            val projectImage = SupperBannerItem(
                name = getString(R.string.about_aircraft_title),
                description = getString(R.string.about_banner_summary),
                image = SupperBannerImage.Network(AircraftConstants.Urls.CONTACT_US_QR_CODE)
            )
            startActivity(ShowImageDetailsActivity.createIntent(this, projectImage))
        }
    }
}
