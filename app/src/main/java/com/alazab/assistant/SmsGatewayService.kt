package com.alazab.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alazab.assistant.data.AppDatabase
import com.alazab.assistant.data.CapturedEvent
import com.alazab.assistant.data.CapturedEventRepository
import com.alazab.assistant.data.SmsTask
import com.alazab.assistant.network.HookClient
import com.alazab.assistant.network.SupabaseClient
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class SmsGatewayService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var database: AppDatabase
    private lateinit var repository: CapturedEventRepository
    private lateinit var prefs: SharedPreferences
    private val hookClient = HookClient()
    private val supabaseClient = SupabaseClient()

    private var pollJob: Job? = null

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "sms_gateway_channel"
        private const val NOTIFICATION_ID = 1337
        
        fun start(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SmsGatewayService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("SmsGatewayService", "Service onCreate")
        database = AppDatabase.getDatabase(this)
        repository = CapturedEventRepository(database.capturedEventDao(), database.smsTaskDao(), hookClient, this)
        prefs = getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("تم بدء تشغيل بوابة الرسائل القصيرة تلقائياً"))

        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SmsGatewayService", "Service onStartCommand")
        // Always run foreground
        startForeground(NOTIFICATION_ID, buildNotification("بوابة إرسال الرسائل SMS نشطة وتعمل بالخلفية"))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startPollingLoop() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (isActive) {
                val isEnabled = prefs.getBoolean("sms_pull_enabled", false)
                if (!isEnabled) {
                    updateNotification("بوابة الرسائل بالخلفية (معطلة حالياً)")
                    delay(10000)
                    continue
                }

                try {
                    checkAndResetLimits()
                    val deviceId = prefs.getString("device_registration_id", "azab-phone-gateway") ?: "azab-phone-gateway"
                    val apiKey = prefs.getString("api_key", "azab-secret-api-key-2026") ?: ""
                    
                    // Resolve pull and report endpoints
                    val defaultBase = prefs.getString("backend_url", "") ?: ""
                    val defaultDerivedPull = defaultBase.replace("/payment-phone-hook", "") + "/sms/pull"
                    val defaultDerivedReport = defaultBase.replace("/payment-phone-hook", "") + "/sms/report"

                    val pullUrl = prefs.getString("sms_pull_url", defaultDerivedPull) ?: defaultDerivedPull
                    val reportUrl = prefs.getString("sms_report_url", defaultDerivedReport) ?: defaultDerivedReport

                    updateNotification("جاري فحص المهام من السيرفر...")

                    // 1. Pull tasks
                    val fetchedTasks = hookClient.pullSmsTasks(pullUrl, deviceId, apiKey)
                    if (fetchedTasks.isNotEmpty()) {
                        Log.d("SmsGatewayService", "Fetched ${fetchedTasks.size} SMS tasks from server")
                        // Insert tasks (with IGNORE conflict resolver to avoid double processing)
                        repository.insertSmsTasksIgnore(fetchedTasks)
                    }

                    // 1b. Pull tasks from Supabase if enabled
                    val supabaseEnabled = prefs.getBoolean("supabase_sync_enabled", false)
                    if (supabaseEnabled) {
                        val supabaseUrl = prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co"
                        val supabaseKey = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: ""
                        if (supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                            val fetchedSupabaseTasks = supabaseClient.pullSmsTasks(supabaseUrl, supabaseKey)
                            if (fetchedSupabaseTasks.isNotEmpty()) {
                                Log.d("SmsGatewayService", "Fetched ${fetchedSupabaseTasks.size} SMS tasks from Supabase")
                                repository.insertSmsTasksIgnore(fetchedSupabaseTasks)
                            }
                        }
                    }

                    // 2. Process pending outbox queue
                    val pendingQueue = repository.getPendingSmsTasks()
                    if (pendingQueue.isNotEmpty()) {
                        updateNotification("جاري إرسال عدد ${pendingQueue.size} رسالة قصيرة...")
                        for (task in pendingQueue) {
                            sendAndReportSms(task, reportUrl, deviceId, apiKey)
                        }
                    } else {
                        updateNotification("البوابة نشطة | لا توجد رسائل معلقة")
                    }

                } catch (e: Exception) {
                    Log.e("SmsGatewayService", "Error in gateway loop", e)
                    updateNotification("خطأ في الاتصال بالبوابة: ${e.localizedMessage}")
                }

                // Sleep according to configurable interval (default: 30s)
                val intervalSec = prefs.getInt("sms_pull_interval_sec", 30)
                delay(intervalSec * 1000L)
            }
        }
    }

    private suspend fun sendAndReportSms(task: SmsTask, reportUrl: String, deviceId: String, apiKey: String) {
        val todayLimit = prefs.getInt("daily_sms_limit", 100)
        val monthLimit = prefs.getInt("monthly_sms_limit", 2000)

        val todayCount = prefs.getInt("sms_sent_today_count", 0)
        val monthCount = prefs.getInt("sms_sent_this_month_count", 0)

        val timeNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        val supabaseEnabled = prefs.getBoolean("supabase_sync_enabled", false)
        val supabaseUrl = prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co"
        val supabaseKey = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: ""

        suspend fun reportSupabase(status: String, error: String?) {
            if (supabaseEnabled && supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                supabaseClient.reportSmsStatus(supabaseUrl, supabaseKey, task.taskId, status, error)
            }
        }

        if (todayCount >= todayLimit) {
            val errorMsg = "Daily SMS limit exceeded ($todayCount/$todayLimit)"
            repository.updateSmsTaskStatus(task.taskId, "FAILED", timeNow, errorMsg)
            hookClient.reportSmsStatus(reportUrl, task.taskId, deviceId, "FAILED", errorMsg, apiKey)
            reportSupabase("FAILED", errorMsg)
            return
        }

        if (monthCount >= monthLimit) {
            val errorMsg = "Monthly SMS limit exceeded ($monthCount/$monthLimit)"
            repository.updateSmsTaskStatus(task.taskId, "FAILED", timeNow, errorMsg)
            hookClient.reportSmsStatus(reportUrl, task.taskId, deviceId, "FAILED", errorMsg, apiKey)
            reportSupabase("FAILED", errorMsg)
            return
        }

        // Physically send SMS
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                this.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                SmsManager.getDefault()
            }

            // Standard sending
            smsManager.sendTextMessage(task.phoneNumber, null, task.messageText, null, null)
            Log.d("SmsGatewayService", "Physically dispatched SMS to ${task.phoneNumber}")

            // Increment local limit counters
            prefs.edit()
                .putInt("sms_sent_today_count", todayCount + 1)
                .putInt("sms_sent_this_month_count", monthCount + 1)
                .apply()

            // Update status to SENT locally and on server
            repository.updateSmsTaskStatus(task.taskId, "SENT", timeNow, null)
            hookClient.reportSmsStatus(reportUrl, task.taskId, deviceId, "SENT", null, apiKey)
            reportSupabase("SENT", null)

            // Also record this outbound message in general history
            val outgoingEvent = CapturedEvent(
                provider = "Gateway Outbox",
                channel = "sms",
                direction = "outgoing",
                rawText = task.messageText,
                sender = task.phoneNumber,
                receivedAtDevice = timeNow,
                classification = "SYSTEM",
                entityId = task.taskId
            )
            repository.insertAndSync(outgoingEvent)

        } catch (e: Exception) {
            Log.e("SmsGatewayService", "Failed to dispatch SMS", e)
            val newRetry = task.retryCount + 1
            val status = if (newRetry >= 3) "FAILED" else "PENDING"
            val errorMsg = e.localizedMessage ?: "Unknown sending exception"

            val updatedTask = task.copy(
                status = status,
                retryCount = newRetry,
                lastAttemptAt = timeNow,
                errorMessage = errorMsg
            )
            repository.updateSmsTask(updatedTask)

            if (status == "FAILED") {
                hookClient.reportSmsStatus(reportUrl, task.taskId, deviceId, "FAILED", errorMsg, apiKey)
                reportSupabase("FAILED", errorMsg)
            }
        }
    }

    private fun checkAndResetLimits() {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val monthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        val lastDay = prefs.getString("last_limit_reset_day", "")
        val lastMonth = prefs.getString("last_limit_reset_month", "")

        val editor = prefs.edit()
        if (lastDay != todayStr) {
            editor.putInt("sms_sent_today_count", 0)
            editor.putString("last_limit_reset_day", todayStr)
        }
        if (lastMonth != monthStr) {
            editor.putInt("sms_sent_this_month_count", 0)
            editor.putString("last_limit_reset_month", monthStr)
        }
        editor.apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "بوابة إرسال الرسائل بالخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "قناة إشعارات بوابة استقبال وإرسال الرسائل لمساعد العزاب"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("مساعد العزاب | بوابة SMS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SmsGatewayService", "Service onDestroy")
        serviceJob.cancel()
    }
}
