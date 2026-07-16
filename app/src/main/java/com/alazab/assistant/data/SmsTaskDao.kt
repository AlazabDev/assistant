package com.alazab.assistant.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTaskDao {
    @Query("SELECT * FROM sms_tasks ORDER BY createdAt DESC")
    fun getAllSmsTasks(): Flow<List<SmsTask>>

    @Query("SELECT * FROM sms_tasks WHERE status = 'PENDING' OR (status = 'FAILED' AND retryCount < 3) ORDER BY createdAt ASC")
    suspend fun getPendingTasks(): List<SmsTask>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTask(task: SmsTask): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTasksIgnore(tasks: List<SmsTask>)

    @Update
    suspend fun updateTask(task: SmsTask)

    @Query("UPDATE sms_tasks SET status = :status, errorMessage = :errorMessage, lastAttemptAt = :lastAttempt WHERE taskId = :taskId")
    suspend fun updateTaskStatus(taskId: String, status: String, lastAttempt: String, errorMessage: String?)

    @Query("DELETE FROM sms_tasks")
    suspend fun clearAllTasks()
}
