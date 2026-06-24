package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkerDao {
    @Query("SELECT * FROM workers ORDER BY section ASC, name ASC")
    fun getAllWorkers(): Flow<List<Worker>>

    @Query("SELECT * FROM workers ORDER BY section ASC, name ASC")
    suspend fun getWorkersSync(): List<Worker>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkers(workers: List<Worker>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorker(worker: Worker)

    @Query("DELETE FROM workers WHERE id = :id")
    suspend fun deleteWorkerById(id: String)

    @Query("DELETE FROM workers")
    suspend fun clearAllWorkers()
}

@Dao
interface OvertimeLogDao {
    @Query("SELECT * FROM overtime_logs")
    fun getAllLogs(): Flow<List<OvertimeLog>>

    @Query("SELECT * FROM overtime_logs")
    suspend fun getLogsSync(): List<OvertimeLog>

    @Query("SELECT * FROM overtime_logs WHERE date = :date")
    fun getLogsForDate(date: String): Flow<List<OvertimeLog>>

    @Query("SELECT * FROM overtime_logs WHERE date = :date")
    suspend fun getLogsForDateSync(date: String): List<OvertimeLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OvertimeLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<OvertimeLog>)

    @Query("DELETE FROM overtime_logs WHERE id = :id")
    suspend fun deleteLog(id: String)

    @Query("DELETE FROM overtime_logs")
    suspend fun clearAllLogs()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun getSetting(key: String): Flow<Setting?>

    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSettingSync(key: String): Setting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: Setting)

    @Query("DELETE FROM settings")
    suspend fun clearAllSettings()
}
