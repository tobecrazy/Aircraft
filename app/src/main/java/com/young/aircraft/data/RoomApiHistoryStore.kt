package com.young.aircraft.data

import android.content.Context
import com.young.aircraft.providers.DatabaseProvider
import com.young.developtools.repository.ApiHistoryRecord
import com.young.developtools.repository.ApiHistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * :app implementation of the dev-tools history contract, backed by the Room
 * `api_request_history` table (the table and its migrations stay here).
 * Wired via DevTools.historyStoreProvider in AircraftApplication.
 */
class RoomApiHistoryStore(context: Context) : ApiHistoryStore {
    private val dao = DatabaseProvider.getDatabase(context.applicationContext).apiRequestHistoryDao()

    override fun observeAll(): Flow<List<ApiHistoryRecord>> =
        dao.getAllHistory().map { rows -> rows.map { it.toRecord() } }

    override suspend fun getById(id: Long): ApiHistoryRecord? =
        dao.getById(id)?.toRecord()

    override suspend fun insert(record: ApiHistoryRecord) {
        dao.insert(record.toEntity())
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun clear() = dao.deleteAll()

    private fun ApiRequestHistory.toRecord() = ApiHistoryRecord(
        id = id,
        timestamp = timestamp,
        url = url,
        method = method,
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        responseCode = responseCode,
        responseBody = responseBody,
        responseHeaders = responseHeaders,
        responseTime = responseTime,
        error = error
    )

    private fun ApiHistoryRecord.toEntity() = ApiRequestHistory(
        url = url,
        method = method,
        requestHeaders = requestHeaders,
        requestBody = requestBody,
        responseCode = responseCode,
        responseBody = responseBody,
        responseHeaders = responseHeaders,
        responseTime = responseTime,
        error = error
    )
}
