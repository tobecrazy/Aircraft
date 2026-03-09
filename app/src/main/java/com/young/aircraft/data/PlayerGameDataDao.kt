package com.young.aircraft.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PlayerGameDataDao {
    @Insert
    suspend fun insert(data: PlayerGameData)

    @Delete
    suspend fun delete(data: PlayerGameData)

    @Query("SELECT * FROM player_game_data WHERE player_id = :playerId ORDER BY timestamp DESC")
    suspend fun getByPlayerId(playerId: String): List<PlayerGameData>

    @Query("SELECT * FROM player_game_data ORDER BY score DESC")
    suspend fun getAllByScoreDesc(): List<PlayerGameData>

    @Query("SELECT SUM(score) FROM player_game_data WHERE player_id = :playerId")
    suspend fun getTotalScore(playerId: String): Long?

    @Query("DELETE FROM player_game_data WHERE player_id = :playerId")
    suspend fun deleteByPlayerId(playerId: String)
}
