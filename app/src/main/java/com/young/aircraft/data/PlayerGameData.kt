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
    @ColumnInfo(name = "player_name")
    val playerName: String? = null,
    @ColumnInfo(name = "level")
    val level: Int,
    @ColumnInfo(name = "score")
    val score: Long,
    @ColumnInfo(name = "jet_plane_res")
    val jetPlaneRes: Int = 0,
    @ColumnInfo(name = "jet_plane_index")
    val jetPlaneIndex: Int = 0,
    @ColumnInfo(name = "difficulty")
    val difficulty: String = "1.0",
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
