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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val smsSentReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val taskId = intent?.getStringExtra("taskId") ?: return
            val reportUrl = intent.getStringExtra("reportUrl") ?: ""
            val deviceId = intent.getStringExtra("deviceId") ?: ""
            val apiKey = intent.getStringExtra("apiKey") ?: ""
            val phoneNumber = intent.getStringExtra("phoneNumber") ?: ""
            val messageText = intent.getStringExtra("messageText") ?: ""
            
            val timeNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
            val isSuccess = resultCode == Activity.RESULT_OK
            val status = if (isSuccess) "SENT" else "FAILED"
            val errorMsg = if (isSuccess) null else getSmsErrorDescription(resultCode)
            
            serviceScope.launch {
                try {
                    val task = repository.getSmsTask(taskId)
                    if (task != null) {
                        if (isSuccess) {
                            val todayCount = prefs.getInt("sms_sent_today_count", 0)
                            val monthCount = prefs.getInt("sms_sent_this_month_count", 0)
                            prefs.edit()
                                .putInt("sms_sent_today_count", todayCount + 1)
                                .putInt("sms_sent_this_month_count", monthCount + 1)
                                .apply()

                            repository.updateSmsTaskStatus(taskId, "SENT", timeNow, null)
                            hookClient.reportSmsStatus(reportUrl, taskId, deviceId, "SENT", null, apiKey)
                            
                            val supabaseEnabled = prefs.getBoolean("supabase_sync_enabled", false)
                            val supabaseUrl = prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co"
                            val supabaseKey = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: ""
                            if (supabaseEnabled && supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                                supabaseClient.reportSmsStatus(supabaseUrl, supabaseKey, taskId, "SENT", null)
                            }

                            // Record outbound history
                            val outgoingEvent = CapturedEvent(
                                provider = "مؤسسة العزب (+201004006620)",
                                channel = "sms",
                                direction = "outgoing",
                                rawText = messageText,
                                sender = AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE,
                                receivedAtDevice = timeNow,
                                classification = "ENTERPRISE_DISPATCH",
                                entityId = taskId
                            )
                            repository.insertAndSync(outgoingEvent)

                            updateTelemetry { current ->
                                current.copy(
                                    state = GatewayState.Ready,
                                    sentTodayCount = todayCount + 1,
                                    sentThisMonthCount = monthCount + 1,
                                    lastDispatchedRecipient = phoneNumber,
                                    lastDispatchedTaskId = taskId,
                                    lastDispatchedStatus = "SENT",
                                    lastDispatchedTime = timeNow,
                                    lastSuccessTimestamp = System.currentTimeMillis()
                                )
                            }
                            Log.d("SmsGatewayService", "Successfully processed SMS SENT confirmation for task $taskId")
                        } else {
                            val newRetry = task.retryCount + 1
                            val newStatus = if (newRetry >= 3) "FAILED" else "PENDING"
                            
                            val updatedTask = task.copy(
                                status = newStatus,
                                retryCount = newRetry,
                                lastAttemptAt = timeNow,
                                errorMessage = errorMsg
                            )
                            repository.updateSmsTask(updatedTask)

                            updateTelemetry { current ->
                                current.copy(
                                    state = GatewayState.Error(errorMsg ?: "فشل تسليم الرسالة لشبكة المحمول"),
                                    lastDispatchedRecipient = phoneNumber,
                                    lastDispatchedTaskId = taskId,
                                    lastDispatchedStatus = "FAILED",
                                    lastDispatchedTime = timeNow,
                                    lastErrorTimestamp = System.currentTimeMillis(),
                                    lastErrorMessage = errorMsg
                                )
                            }
                            
                            if (newStatus == "FAILED") {
                                hookClient.reportSmsStatus(reportUrl, taskId, deviceId, "FAILED", errorMsg, apiKey)
                                val supabaseEnabled = prefs.getBoolean("supabase_sync_enabled", false)
                                val supabaseUrl = prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co"
                                val supabaseKey = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: ""
                                if (supabaseEnabled && supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                                    supabaseClient.reportSmsStatus(supabaseUrl, supabaseKey, taskId, "FAILED", errorMsg)
                                }
                            }
                            Log.e("SmsGatewayService", "Failed to send SMS for task $taskId, retryCount: $newRetry, error: $errorMsg")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SmsGatewayService", "Error handling SMS_SENT broadcast", e)
                }
            }
        }
    }

    private val smsDeliveredReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val taskId = intent?.getStringExtra("taskId") ?: return
            val reportUrl = intent.getStringExtra("reportUrl") ?: ""
            val deviceId = intent.getStringExtra("deviceId") ?: ""
            val apiKey = intent.getStringExtra("apiKey") ?: ""
            
            val timeNow = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
            val isSuccess = resultCode == Activity.RESULT_OK
            
            serviceScope.launch {
                try {
                    if (isSuccess) {
                        repository.updateSmsTaskStatus(taskId, "DELIVERED", timeNow, null)
                        hookClient.reportSmsStatus(reportUrl, taskId, deviceId, "DELIVERED", null, apiKey)
                        
                        val supabaseEnabled = prefs.getBoolean("supabase_sync_enabled", false)
                        val supabaseUrl = prefs.getString("supabase_url", "https://bxuhcbfdoaflsgbxiqei.supabase.co") ?: "https://bxuhcbfdoaflsgbxiqei.supabase.co"
                        val supabaseKey = prefs.getString("supabase_key", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ4dWhjYmZkb2FmbHNnYnhpcWVpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjkwMTU1MDIsImV4cCI6MjA4NDU5MTUwMn0.W60YuL2zn9De95Jwgc7jn4_NLMdOVINHO6NUWY6yRlQ") ?: ""
                        if (supabaseEnabled && supabaseUrl.isNotEmpty() && supabaseKey.isNotEmpty()) {
                            supabaseClient.reportSmsStatus(supabaseUrl, supabaseKey, taskId, "DELIVERED", null)
                        }
                        updateTelemetry { current ->
                            current.copy(
                                lastDispatchedStatus = "DELIVERED"
                            )
                        }
                        Log.d("SmsGatewayService", "Successfully processed SMS DELIVERED confirmation for task $taskId")
                    } else {
                        Log.w("SmsGatewayService", "SMS delivery report for task $taskId reported code $resultCode (not delivered)")
                    }
                } catch (e: Exception) {
                    Log.e("SmsGatewayService", "Error handling SMS_DELIVERED broadcast", e)
                }
            }
        }
    }

    private fun getSmsErrorDescription(resultCode: Int): String {
        return AlazabEnterpriseConfig.getSmsErrorDescription(resultCode)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "sms_gateway_channel"
        private const val NOTIFICATION_ID = 1337
        
        private val _telemetry = MutableStateFlow(
            GatewayConnectionTelemetry(
                officialPhone = AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE,
                carrierName = "فودافون مصر (Vodafone Egypt)",
                state = GatewayState.Stopped
            )
        )
        val telemetry: StateFlow<GatewayConnectionTelemetry> = _telemetry.asStateFlow()

        fun updateTelemetry(update: (GatewayConnectionTelemetry) -> GatewayConnectionTelemetry) {
            _telemetry.value = update(_telemetry.value)
        }

        fun start(context: Context) {
            updateTelemetry { it.copy(isServiceRunning = true, state = GatewayState.Ready) }
            val intent = Intent(context, SmsGatewayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            updateTelemetry { it.copy(isServiceRunning = false, state = GatewayState.Stopped) }
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

        val isEnabled = prefs.getBoolean("sms_pull_enabled", false)
        val todayCount = prefs.getInt("sms_sent_today_count", 0)
        val monthCount = prefs.getInt("sms_sent_this_month_count", 0)
        val todayLimit = prefs.getInt("daily_sms_limit", 100)
        val monthLimit = prefs.getInt("monthly_sms_limit", 2000)

        updateTelemetry { current ->
            current.copy(
                isServiceRunning = true,
                state = if (isEnabled) GatewayState.Ready else GatewayState.Stopped,
                sentTodayCount = todayCount,
                dailyLimit = todayLimit,
                sentThisMonthCount = monthCount,
                monthlyLimit = monthLimit,
                lastHeartbeatMs = System.currentTimeMillis()
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsSentReceiver, android.content.IntentFilter("com.alazab.assistant.SMS_SENT"), Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(smsDeliveredReceiver, android.content.IntentFilter("com.alazab.assistant.SMS_DELIVERED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(smsSentReceiver, android.content.IntentFilter("com.alazab.assistant.SMS_SENT"))
            registerReceiver(smsDeliveredReceiver, android.content.IntentFilter("com.alazab.assistant.SMS_DELIVERED"))
        }

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
                    updateTelemetry { it.copy(state = GatewayState.Stopped) }
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
                    updateTelemetry { it.copy(lastHeartbeatMs = System.currentTimeMillis()) }

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
                    updateTelemetry { it.copy(pendingQueueCount = pendingQueue.size) }
                    if (pendingQueue.isNotEmpty()) {
                        updateNotification("جاري إرسال عدد ${pendingQueue.size} رسالة عبر +201004006620...")
                        for (task in pendingQueue) {
                            sendAndReportSms(task, reportUrl, deviceId, apiKey)
                            delay(1500L) // Safe cellular inter-message pacing for Vodafone Egypt
                        }
                        val remainingCount = repository.getPendingSmsTasks().size
                        updateTelemetry { current ->
                            current.copy(
                                pendingQueueCount = remainingCount,
                                state = if (current.state is GatewayState.Sending) GatewayState.Ready else current.state
                            )
                        }
                    } else {
                        updateNotification("بوابة العزب نشطة (+201004006620) | لا توجد رسائل معلقة")
                        updateTelemetry { current ->
                            if (current.state is GatewayState.Sending || current.state is GatewayState.Stopped) {
                                current.copy(state = GatewayState.Ready, pendingQueueCount = 0)
                            } else current.copy(pendingQueueCount = 0)
                        }
                    }

                } catch (e: Exception) {
                    Log.e("SmsGatewayService", "Error in gateway loop", e)
                    val errorMsg = e.localizedMessage ?: "Unknown error"
                    updateTelemetry { it.copy(state = GatewayState.Error("خطأ في الاتصال بالبوابة: $errorMsg"), lastErrorMessage = errorMsg, lastErrorTimestamp = System.currentTimeMillis()) }
                    updateNotification("خطأ في الاتصال بالبوابة: $errorMsg")
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
            updateTelemetry { it.copy(state = GatewayState.Error(errorMsg), lastErrorMessage = errorMsg, lastErrorTimestamp = System.currentTimeMillis()) }
            repository.updateSmsTaskStatus(task.taskId, "FAILED", timeNow, errorMsg)
            hookClient.reportSmsStatus(reportUrl, task.taskId, deviceId, "FAILED", errorMsg, apiKey)
            reportSupabase("FAILED", errorMsg)
            return
        }

        if (monthCount >= monthLimit) {
            val errorMsg = "Monthly SMS limit exceeded ($monthCount/$monthLimit)"
            updateTelemetry { it.copy(state = GatewayState.Error(errorMsg), lastErrorMessage = errorMsg, lastErrorTimestamp = System.currentTimeMillis()) }
            repository.updateSmsTaskStatus(task.taskId, "FAILED", timeNow, errorMsg)
            hookClient.reportSmsStatus(reportUrl, task.taskId, deviceId, "FAILED", errorMsg, apiKey)
            reportSupabase("FAILED", errorMsg)
            return
        }

        val normalizedNumber = AlazabEnterpriseConfig.normalizePhoneNumber(task.phoneNumber)
        if (normalizedNumber.isBlank() || normalizedNumber.length < 9) {
            val errorMsg = "رقم هاتف المستلم غير صحيح (${task.phoneNumber})"
            updateTelemetry { it.copy(state = GatewayState.Error(errorMsg), lastErrorMessage = errorMsg, lastErrorTimestamp = System.currentTimeMillis()) }
            repository.updateSmsTaskStatus(task.taskId, "FAILED", timeNow, errorMsg)
            hookClient.reportSmsStatus(reportUrl, task.taskId, deviceId, "FAILED", errorMsg, apiKey)
            reportSupabase("FAILED", errorMsg)
            return
        }

        // Physically send SMS with safe multipart splitting and UCS-2 Arabic support
        try {
            val encodingInfo = AlazabEnterpriseConfig.analyzeSmsEncoding(task.messageText)
            updateTelemetry { current ->
                current.copy(
                    state = GatewayState.Sending(
                        recipient = normalizedNumber,
                        taskId = task.taskId,
                        partCount = encodingInfo.partCount
                    ),
                    lastDispatchedRecipient = normalizedNumber,
                    lastDispatchedTaskId = task.taskId,
                    lastDispatchedTime = timeNow,
                    lastActiveTimestamp = System.currentTimeMillis()
                )
            }

            // Define PendingIntents for SENT and DELIVERED status confirmation
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            
            val sentIntent = PendingIntent.getBroadcast(
                this,
                task.taskId.hashCode(),
                Intent("com.alazab.assistant.SMS_SENT").apply {
                    putExtra("taskId", task.taskId)
                    putExtra("reportUrl", reportUrl)
                    putExtra("deviceId", deviceId)
                    putExtra("apiKey", apiKey)
                    putExtra("phoneNumber", normalizedNumber)
                    putExtra("messageText", task.messageText)
                },
                flags
            )

            val deliveryIntent = PendingIntent.getBroadcast(
                this,
                task.taskId.hashCode() + 1,
                Intent("com.alazab.assistant.SMS_DELIVERED").apply {
                    putExtra("taskId", task.taskId)
                    putExtra("reportUrl", reportUrl)
                    putExtra("deviceId", deviceId)
                    putExtra("apiKey", apiKey)
                    putExtra("phoneNumber", normalizedNumber)
                    putExtra("messageText", task.messageText)
                },
                flags
            )

            // Update status to DISPATCHED locally to prevent double processing in the loop
            repository.updateSmsTaskStatus(task.taskId, "DISPATCHED", timeNow, null)

            // Safe multipart transmission using enterprise engine
            val partsCount = AlazabEnterpriseConfig.sendSmsSafely(
                context = this,
                recipient = normalizedNumber,
                messageText = task.messageText,
                sentIntent = sentIntent,
                deliveryIntent = deliveryIntent
            )
            Log.d("SmsGatewayService", "Physically dispatched SMS to $normalizedNumber ($partsCount parts) for task ${task.taskId}")

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
        updateTelemetry { it.copy(isServiceRunning = false, state = GatewayState.Stopped) }
        try {
            unregisterReceiver(smsSentReceiver)
            unregisterReceiver(smsDeliveredReceiver)
        } catch (e: Exception) {
            Log.e("SmsGatewayService", "Unregister failed", e)
        }
        serviceJob.cancel()
    }
}
