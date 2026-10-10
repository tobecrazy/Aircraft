package com.young.developtools.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Room-free history record. The :app implementation maps this to ApiRequestHistory. */
data class ApiHistoryRecord(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val url: String,
    val method: String,
    val requestHeaders: String,
    val requestBody: String?,
    val responseCode: Int?,
    val responseBody: String?,
    val responseHeaders: String?,
    val responseTime: Long?,
    val error: String?
)

/**
 * History persistence behind an interface because Room stays in :app.
 * :app provides [RoomApiHistoryStore][com.young.aircraft.data.RoomApiHistoryStore]
 * via [DevTools.historyStoreProvider][com.young.developtools.DevTools.historyStoreProvider].
 */
interface ApiHistoryStore {
    fun observeAll(): Flow<List<ApiHistoryRecord>>
    suspend fun getById(id: Long): ApiHistoryRecord?
    suspend fun insert(record: ApiHistoryRecord)
    suspend fun deleteById(id: Long)
    suspend fun clear()
}

/** Fallback when the host wires no provider: keeps the tools usable, memory-only. */
class InMemoryApiHistoryStore : ApiHistoryStore {
    private val records = mutableListOf<ApiHistoryRecord>()
    private val flow = MutableStateFlow<List<ApiHistoryRecord>>(emptyList())
    private var seq = 0L

    override fun observeAll(): Flow<List<ApiHistoryRecord>> = flow.asStateFlow()

    override suspend fun getById(id: Long): ApiHistoryRecord? =
        synchronized(this) { records.firstOrNull { it.id == id } }

    override suspend fun insert(record: ApiHistoryRecord) {
        synchronized(this) {
            records.add(0, record.copy(id = ++seq))
            flow.value = records.toList()
        }
    }

    override suspend fun deleteById(id: Long) {
        synchronized(this) {
            records.removeAll { it.id == id }
            flow.value = records.toList()
        }
    }

    override suspend fun clear() {
        synchronized(this) {
            records.clear()
            flow.value = emptyList()
        }
    }
}
