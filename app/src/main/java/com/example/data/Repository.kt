package com.example.data

import kotlinx.coroutines.flow.Flow

class OTRepository(private val database: AppDatabase) {
    val workerDao = database.workerDao()
    val overtimeLogDao = database.overtimeLogDao()
    val settingDao = database.settingDao()

    // Workers
    val allWorkers: Flow<List<Worker>> = workerDao.getAllWorkers()
    suspend fun getWorkersSync() = workerDao.getWorkersSync()
    suspend fun insertWorkers(workers: List<Worker>) = workerDao.insertWorkers(workers)
    suspend fun insertWorker(worker: Worker) = workerDao.insertWorker(worker)
    suspend fun deleteWorkerById(id: String) = workerDao.deleteWorkerById(id)
    suspend fun clearAllWorkers() = workerDao.clearAllWorkers()

    // Overtime Logs
    val allLogs: Flow<List<OvertimeLog>> = overtimeLogDao.getAllLogs()
    suspend fun getLogsSync() = overtimeLogDao.getLogsSync()
    fun getLogsForDate(date: String): Flow<List<OvertimeLog>> = overtimeLogDao.getLogsForDate(date)
    suspend fun getLogsForDateSync(date: String) = overtimeLogDao.getLogsForDateSync(date)
    suspend fun insertLog(log: OvertimeLog) = overtimeLogDao.insertLog(log)
    suspend fun insertLogs(logs: List<OvertimeLog>) = overtimeLogDao.insertLogs(logs)
    suspend fun deleteLog(id: String) = overtimeLogDao.deleteLog(id)
    suspend fun clearAllLogs() = overtimeLogDao.clearAllLogs()

    // Settings
    fun getSetting(key: String): Flow<Setting?> = settingDao.getSetting(key)
    suspend fun getSettingSync(key: String): Setting? = settingDao.getSettingSync(key)
    suspend fun insertSetting(key: String, value: String) = settingDao.insertSetting(Setting(key, value))
    suspend fun clearAllSettings() = settingDao.clearAllSettings()

    // Global clear
    suspend fun clearAll() {
        database.clearAllTables()
    }
}
