package com.young.aircraft.viewmodel

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.young.aircraft.BuildConfig
import com.young.aircraft.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AboutAircraftViewModel(
    githubUrl: String,
    versionText: String,
    platformText: String,
    stackBadge: String,
    description: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AboutAircraftUiState(
            imageLoadState = ImageLoadState.Loading,
            versionText = versionText,
            platformText = platformText,
            stackBadge = stackBadge,
            githubUrl = githubUrl,
            description = description
        )
    )
    val uiState: StateFlow<AboutAircraftUiState> = _uiState.asStateFlow()

    fun onImageLoadStarted() {
        _uiState.value = _uiState.value.copy(imageLoadState = ImageLoadState.Loading)
    }

    fun onImageLoadSuccess() {
        _uiState.value = _uiState.value.copy(imageLoadState = ImageLoadState.Success)
    }

    fun onImageLoadError() {
        _uiState.value = _uiState.value.copy(imageLoadState = ImageLoadState.Error)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val ctx = context.applicationContext
        private val githubUrl = ctx.getString(R.string.about_me_project_repo_url)
        private val versionText = ctx.getString(R.string.device_info_fmt_version, BuildConfig.VERSION_NAME)
        @Suppress("DEPRECATION")
        private val platformText = "Android ${Build.VERSION.RELEASE}"
        private val stackBadge = ctx.getString(R.string.about_stack_badge)
        private val description = ctx.getString(R.string.about_description)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AboutAircraftViewModel(
                githubUrl, versionText, platformText, stackBadge, description
            ) as T
        }
    }
}
