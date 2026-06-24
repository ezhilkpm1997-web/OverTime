package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Worker::class, OvertimeLog::class, Setting::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workerDao(): WorkerDao
    abstract fun overtimeLogDao(): OvertimeLogDao
    abstract fun settingDao(): SettingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ot_tracker_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
