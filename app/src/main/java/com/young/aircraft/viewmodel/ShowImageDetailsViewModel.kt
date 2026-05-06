package com.young.aircraft.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.aircraft.data.ImageDetails
import com.young.aircraft.data.ImageDetailsIntentContract
import com.young.aircraft.data.ImageDetailsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class ShowImageDetailsViewModel(
    @SuppressLint("StaticFieldLeak") private val context: Context,
    initialDetails: ImageDetails,
    private val httpClient: OkHttpClient = OkHttpClient()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ShowImageDetailsUiState(
            details = initialDetails,
            imageModel = initialDetails.toImageModel(context)
        )
    )
    val uiState: StateFlow<ShowImageDetailsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ImageDetailsEvent>()
    val events: SharedFlow<ImageDetailsEvent> = _events.asSharedFlow()

    fun saveImage(uri: Uri) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        when (val source = _uiState.value.details.source) {
                            is ImageDetailsSource.Local -> {
                                val bitmap = BitmapFactory.decodeResource(context.resources, source.resId)
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
                            }
                            is ImageDetailsSource.Network -> {
                                val request = Request.Builder().url(source.url).build()
                                httpClient.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful) return@use false
                                    response.body.byteStream().copyTo(output)
                                    true
                                }
                            }
                        }
                    } ?: false
                }.getOrDefault(false)
            }

            _uiState.update { it.copy(isSaving = false) }
            _events.emit(ImageDetailsEvent.SaveCompleted(saved))
        }
    }

    class Factory(
        context: Context,
        private val intent: Intent
    ) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val details = ImageDetailsIntentContract.fromIntent(intent)
                ?: throw IllegalArgumentException("Missing banner details")
            return ShowImageDetailsViewModel(appContext, details) as T
        }
    }
}

data class ShowImageDetailsUiState(
    val details: ImageDetails,
    val imageModel: Any,
    val isSaving: Boolean = false
)

sealed class ImageDetailsEvent {
    data class SaveCompleted(val saved: Boolean) : ImageDetailsEvent()
}
