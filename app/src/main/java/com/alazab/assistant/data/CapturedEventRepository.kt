package com.alazab.assistant.data

import android.content.Context
import android.util.Log
import com.alazab.assistant.network.HookClient
import com.alazab.assistant.network.SupabaseClient
import kotlinx.coroutines.flow.Flow

class CapturedEventRepository(
    private val dao: CapturedEventDao,
    private val smsTaskDao: SmsTaskDao,
    private val hookClient: HookClient,
    private val context: Context
) {
    val allEvents: Flow<List<CapturedEvent>> = dao.getAllEvents()
    val allSmsTasks: Flow<List<SmsTask>> = smsTaskDao.getAllSmsTasks()
    private val supabaseClient = SupabaseClient()

    suspend fun insertAndSync(event: CapturedEvent): Long {
        Log.d("AzabAssistant", "Inserting event from provider ${event.provider}")
        val id = dao.insertEvent(event)
        val insertedEvent = event.copy(id = id.toInt())

        // Retrieve server endpoints from SharedPreferences
        val prefs = context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("backend_url", "https://ais-dev-qzkqa7lcuz6u5kqe52owcv-6579453338.europe-west1.run.app/payment-phone-hook") ?: ""
        val apiKey = prefs.getString("api_key", "azab-secret-api-key-2026") ?: ""

        val success = hookClient.sendEvent(insertedEvent, url, apiKey)
        if (success) {
            dao.markAsSynced(insertedEvent.id)
            saveLastSyncStatus(true)
        } else {
            saveLastSyncStatus(false)
        }

        // Supabase integration
        val supabaseEnabled = prefs.getBoolean("supabase_sync_enabled", false)
        if (supabaseEnabled) {
            val supabaseUrl = prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co"
            val supabaseKey = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: ""
            if (supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                Log.d("CapturedEventRepository", "Syncing event to Supabase: ${insertedEvent.id}")
                val supabaseSuccess = supabaseClient.insertEvent(supabaseUrl, supabaseKey, insertedEvent)
                if (supabaseSuccess && !success) {
                    // If webhook failed but Supabase succeeded, we still consider it synced in some way,
                    // or just log it
                    dao.markAsSynced(insertedEvent.id)
                    saveLastSyncStatus(true)
                }
            }
        }

        return id
    }

    suspend fun syncOfflineQueue(): Boolean {
        val unsynced = dao.getUnsyncedEvents()
        if (unsynced.isEmpty()) {
            saveLastSyncStatus(true)
            return true
        }

        val prefs = context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("backend_url", "https://ais-dev-qzkqa7lcuz6u5kqe52owcv-6579453338.europe-west1.run.app/payment-phone-hook") ?: ""
        val apiKey = prefs.getString("api_key", "azab-secret-api-key-2026") ?: ""

        val supabaseEnabled = prefs.getBoolean("supabase_sync_enabled", false)
        val supabaseUrl = prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co"
        val supabaseKey = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: ""

        var allSuccessful = true
        for (event in unsynced) {
            var success = hookClient.sendEvent(event, url, apiKey)
            var supabaseSuccess = false
            if (supabaseEnabled && supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                supabaseSuccess = supabaseClient.insertEvent(supabaseUrl, supabaseKey, event)
            }
            if (success || supabaseSuccess) {
                dao.markAsSynced(event.id)
            } else {
                allSuccessful = false
            }
        }
        saveLastSyncStatus(allSuccessful)
        return allSuccessful
    }

    private fun saveLastSyncStatus(success: Boolean) {
        val prefs = context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
        val timeString = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        prefs.edit().putString("last_sync_status", if (success) "Successful at $timeString" else "Failed at $timeString").apply()
    }

    // Sms outbox tasks local operations
    suspend fun getPendingSmsTasks(): List<SmsTask> = smsTaskDao.getPendingTasks()
    suspend fun insertSmsTask(task: SmsTask) = smsTaskDao.insertTask(task)
    suspend fun insertSmsTasksIgnore(tasks: List<SmsTask>) = smsTaskDao.insertTasksIgnore(tasks)
    suspend fun updateSmsTask(task: SmsTask) = smsTaskDao.updateTask(task)
    suspend fun updateSmsTaskStatus(taskId: String, status: String, lastAttempt: String, errorMessage: String?) =
        smsTaskDao.updateTaskStatus(taskId, status, lastAttempt, errorMessage)
    suspend fun clearAllTasks() = smsTaskDao.clearAllTasks()

    suspend fun clearAll() = dao.clearAllEvents()
}
