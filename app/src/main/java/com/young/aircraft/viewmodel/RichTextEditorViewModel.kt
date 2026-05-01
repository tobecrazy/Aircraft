package com.young.aircraft.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class RichTextEditorViewModel : ViewModel() {

    var isEditMode: Boolean = true
        private set

    fun switchToEditMode() {
        isEditMode = true
    }

    fun switchToPreviewMode() {
        isEditMode = false
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RichTextEditorViewModel() as T
        }
    }
}
