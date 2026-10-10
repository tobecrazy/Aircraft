package com.young.developtools.repository

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.young.developtools.utils.DevLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ApiDebugRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: String?
)

data class ApiDebugResponse(
    val statusCode: Int,
    val statusMessage: String,
    val responseTime: Long,
    val responseSize: Long,
    val body: String,
    val headers: Map<String, List<String>>
)

class ApiDebugRepository(
    private val historyStore: ApiHistoryStore
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    suspend fun executeRequest(request: ApiDebugRequest): Result<ApiDebugResponse> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var responseCode: Int? = null
        var responseBody: String? = null
        var responseHeaders: Map<String, List<String>>? = null
        var responseTime: Long? = null
        var error: String? = null

        try {
            val requestBuilder = Request.Builder().url(request.url)

            // Add headers
            request.headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // Add body if method supports it
            if (request.method in listOf("POST", "PUT", "PATCH") && !request.body.isNullOrBlank()) {
                val contentType = request.headers["Content-Type"]?.toMediaType()
                    ?: "application/json; charset=utf-8".toMediaType()
                requestBuilder.method(request.method, request.body.toRequestBody(contentType))
            } else {
                requestBuilder.method(request.method, null)
            }

            val okHttpRequest = requestBuilder.build()
            val response = client.newCall(okHttpRequest).execute()

            responseCode = response.code
            responseBody = response.body?.string() ?: ""
            responseHeaders = response.headers.toMultimap()
            responseTime = System.currentTimeMillis() - startTime

            val debugResponse = ApiDebugResponse(
                statusCode = response.code,
                statusMessage = response.message,
                responseTime = responseTime,
                responseSize = responseBody.length.toLong(),
                body = responseBody,
                headers = responseHeaders
            )

            // Save to history
            saveToHistory(
                request = request,
                responseCode = responseCode,
                responseBody = responseBody,
                responseHeaders = responseHeaders,
                responseTime = responseTime,
                error = null
            )

            Result.success(debugResponse)
        } catch (e: Exception) {
            DevLog.e(TAG, "API request failed: ${e.message}", e)
            responseTime = System.currentTimeMillis() - startTime
            error = e.message ?: "Unknown error"

            // Save error to history
            saveToHistory(
                request = request,
                responseCode = responseCode,
                responseBody = responseBody,
                responseHeaders = responseHeaders,
                responseTime = responseTime,
                error = error
            )

            Result.failure(e)
        }
    }

    private suspend fun saveToHistory(
        request: ApiDebugRequest,
        responseCode: Int?,
        responseBody: String?,
        responseHeaders: Map<String, List<String>>?,
        responseTime: Long?,
        error: String?
    ) {
        try {
            val history = ApiHistoryRecord(
                url = request.url,
                method = request.method,
                requestHeaders = gson.toJson(request.headers),
                requestBody = request.body,
                responseCode = responseCode,
                responseBody = responseBody,
                responseHeaders = responseHeaders?.let { gson.toJson(it) },
                responseTime = responseTime,
                error = error
            )
            historyStore.insert(history)
        } catch (e: Exception) {
            DevLog.e(TAG, "Failed to save to history: ${e.message}", e)
        }
    }

    fun getAllHistory(): Flow<List<ApiHistoryRecord>> = historyStore.observeAll()

    suspend fun getHistoryById(id: Long): ApiHistoryRecord? = historyStore.getById(id)

    suspend fun deleteHistory(id: Long) = historyStore.deleteById(id)

    suspend fun clearAllHistory() = historyStore.clear()

    fun formatJson(json: String): String {
        return try {
            val jsonElement = gson.fromJson(json, Any::class.java)
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            json // Return original if not valid JSON
        }
    }

    /** Decodes stored request headers back to a map; empty when absent or corrupt. */
    fun parseRequestHeaders(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                Map::class.java,
                String::class.java,
                String::class.java
            ).type
            gson.fromJson<Map<String, String>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            DevLog.e(TAG, "Failed to parse stored request headers: ${e.message}", e)
            emptyMap()
        }
    }

    /** Decodes stored response headers back to a map; empty when absent or corrupt. */
    fun parseResponseHeaders(json: String?): Map<String, List<String>> {        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val type = com.google.gson.reflect.TypeToken.getParameterized(
                Map::class.java,
                String::class.java,
                com.google.gson.reflect.TypeToken.getParameterized(List::class.java, String::class.java).type
            ).type
            gson.fromJson<Map<String, List<String>>>(json, type) ?: emptyMap()
        } catch (e: Exception) {
            DevLog.e(TAG, "Failed to parse stored response headers: ${e.message}", e)
            emptyMap()
        }
    }

    companion object {
        private const val TAG = "ApiDebugRepository"
    }
}
