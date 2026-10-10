package com.young.aircraft.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiRequestHistoryDao {
    @Insert
    suspend fun insert(history: ApiRequestHistory): Long

    @Query("SELECT * FROM api_request_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllHistory(): Flow<List<ApiRequestHistory>>

    @Query("SELECT * FROM api_request_history WHERE id = :id")
    suspend fun getById(id: Long): ApiRequestHistory?

    @Query("DELETE FROM api_request_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM api_request_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM api_request_history")
    suspend fun getCount(): Int
}
