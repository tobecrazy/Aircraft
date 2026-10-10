package com.young.aircraft.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_request_history")
data class ApiRequestHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val url: String,
    val method: String,
    val requestHeaders: String, // JSON string
    val requestBody: String?,
    val responseCode: Int?,
    val responseBody: String?,
    val responseHeaders: String?, // JSON string of Map<String, List<String>>
    val responseTime: Long?,
    val error: String?
)
