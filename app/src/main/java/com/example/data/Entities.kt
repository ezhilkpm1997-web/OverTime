package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workers")
data class Worker(
    @PrimaryKey val id: String,
    val name: String,
    val section: String,
    val shift: String // "Day" or "Night"
)

@Entity(tableName = "overtime_logs")
data class OvertimeLog(
    @PrimaryKey val id: String, // format: "date_workerId"
    val date: String,          // format: "yyyy-MM-dd"
    val workerId: String,
    val isChecked: Boolean,
    val mins: Int,
    val note: String
)

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)
