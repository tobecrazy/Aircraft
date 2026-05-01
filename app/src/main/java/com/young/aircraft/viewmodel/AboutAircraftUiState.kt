package com.young.aircraft.viewmodel

sealed class ImageLoadState {
    data object Loading : ImageLoadState()
    data object Success : ImageLoadState()
    data object Error : ImageLoadState()
}

data class AboutAircraftUiState(
    val imageLoadState: ImageLoadState = ImageLoadState.Loading,
    val versionText: String = "",
    val platformText: String = "",
    val stackBadge: String = "",
    val githubUrl: String = "",
    val description: String = ""
)
