package com.alazab.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CapturedEvent::class, SmsTask::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun capturedEventDao(): CapturedEventDao
    abstract fun smsTaskDao(): SmsTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "azab_assistant_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
