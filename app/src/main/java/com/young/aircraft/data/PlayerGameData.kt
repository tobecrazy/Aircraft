package com.young.aircraft.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_game_data")
data class PlayerGameData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "player_id")
    val playerId: String,
    @ColumnInfo(name = "level")
    val level: Int,
    @ColumnInfo(name = "score")
    val score: Long,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
