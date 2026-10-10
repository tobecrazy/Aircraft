package com.young.developtools.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.young.developtools.DevTools
import com.young.developtools.repository.ApiHistoryRecord
import com.young.developtools.repository.ApiDebugRepository
import com.young.developtools.repository.ApiDebugRequest
import com.young.developtools.repository.ApiDebugResponse
import com.young.developtools.utils.DevLog
import com.young.developtools.utils.apidebug.BodyError
import com.young.developtools.utils.apidebug.CurlRequest
import com.young.developtools.utils.apidebug.HeaderError
import com.young.developtools.utils.apidebug.UrlError
import com.young.developtools.utils.apidebug.parseHeaders
import com.young.developtools.utils.apidebug.validateBody
import com.young.developtools.utils.apidebug.validateHeaders
import com.young.developtools.utils.apidebug.validateUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ApiDebugUiState(
    val url: String = "",
    val method: String = "GET",
    val headers: String = "Content-Type: application/json",
    val requestBody: String = "",
    val isLoading: Boolean = false,
    val response: ApiDebugResponse? = null,
    val error: String? = null,
    val urlError: UrlError? = UrlError.EMPTY,
    val headerErrors: List<HeaderError> = emptyList(),
    val bodyError: BodyError? = null
) {
    /** Send is only allowed when every field validates and no request is running. */
    val canSend: Boolean
        get() = urlError == null && headerErrors.isEmpty() && bodyError == null && !isLoading
}

class ApiDebugToolViewModel(
    context: Context
) : ViewModel() {

    private val repository = ApiDebugRepository(
DevTools.resolveHistoryStore(context)
    )

    private val _uiState = MutableStateFlow(ApiDebugUiState())
    val uiState: StateFlow<ApiDebugUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<ApiHistoryRecord>> = repository.getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url).refreshValidation()
    }

    fun updateMethod(method: String) {
        _uiState.value = _uiState.value.copy(method = method).refreshValidation()
    }

    fun updateHeaders(headers: String) {
        _uiState.value = _uiState.value.copy(headers = headers).refreshValidation()
    }

    fun updateRequestBody(body: String) {
        _uiState.value = _uiState.value.copy(requestBody = body).refreshValidation()
    }

    /** Re-runs all field validations for the current inputs. */
    private fun ApiDebugUiState.refreshValidation(): ApiDebugUiState {
        return copy(
            urlError = validateUrl(url),
            headerErrors = validateHeaders(headers),
            bodyError = validateBody(method, requestBody)
        )
    }

    fun sendRequest() {
        val currentState = _uiState.value.refreshValidation()
        _uiState.value = currentState
        if (!currentState.canSend) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(isLoading = true, error = null, response = null)

            try {
                val headersMap = parseHeaders(currentState.headers)
                val request = ApiDebugRequest(
                    url = currentState.url,
                    method = currentState.method,
                    headers = headersMap,
                    body = if (currentState.requestBody.isNotBlank()) currentState.requestBody else null
                )

                val result = repository.executeRequest(request)
                result.fold(
                    onSuccess = { response ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            response = response,
                            error = null
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "Unknown error"
                        )
                    }
                )
            } catch (e: Exception) {
                DevLog.e(TAG, "Failed to send request: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun clearAll() {
        _uiState.value = ApiDebugUiState()
    }

    fun loadFromHistory(history: ApiHistoryRecord) {
        viewModelScope.launch {
            try {
                _uiState.value = stateFromHistory(history)
            } catch (e: Exception) {
                DevLog.e(TAG, "Failed to load from history: ${e.message}", e)
            }
        }
    }

    fun loadHistoryById(id: Long) {
        viewModelScope.launch {
            try {
                val history = repository.getHistoryById(id) ?: return@launch
                _uiState.value = stateFromHistory(history)
            } catch (e: Exception) {
                DevLog.e(TAG, "Failed to load history #$id: ${e.message}", e)
            }
        }
    }

    private fun stateFromHistory(history: ApiHistoryRecord): ApiDebugUiState {
        val headersMap = com.google.gson.Gson().fromJson(
            history.requestHeaders,
            Map::class.java
        ) as? Map<String, String> ?: emptyMap()

        val headersString = headersMap.entries.joinToString("\n") { "${it.key}: ${it.value}" }

        return ApiDebugUiState(
            url = history.url,
            method = history.method,
            headers = headersString,
            requestBody = history.requestBody ?: "",
            response = null,
            error = null
        ).refreshValidation()
    }

    /** Fills all fields from a parsed cURL command. */
    fun importCurl(curl: CurlRequest) {
        val headersString = curl.headers.joinToString("\n") { "${it.first}: ${it.second}" }
        _uiState.value = ApiDebugUiState(
            url = curl.url,
            method = curl.method,
            headers = headersString,
            requestBody = curl.body ?: "",
            response = null,
            error = null
        ).refreshValidation()
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteHistory(id)
            } catch (e: Exception) {
                DevLog.e(TAG, "Failed to delete history: ${e.message}", e)
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearAllHistory()
            } catch (e: Exception) {
                DevLog.e(TAG, "Failed to clear history: ${e.message}", e)
            }
        }
    }

    fun formatJsonResponse(json: String): String = repository.formatJson(json)

    fun parseStoredResponseHeaders(json: String?): Map<String, List<String>> =
        repository.parseResponseHeaders(json)

    fun parseStoredRequestHeaders(json: String?): Map<String, String> =
        repository.parseRequestHeaders(json)

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ApiDebugToolViewModel::class.java)) {
                return ApiDebugToolViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "ApiDebugToolViewModel"
    }
}
