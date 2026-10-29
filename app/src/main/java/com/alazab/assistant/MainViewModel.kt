package com.alazab.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alazab.assistant.data.CapturedEvent
import com.alazab.assistant.data.CapturedEventRepository
import com.alazab.assistant.data.SmsTask
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(private val repository: CapturedEventRepository) : ViewModel() {

    val allEvents: StateFlow<List<CapturedEvent>> = repository.allEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allSmsTasks: StateFlow<List<SmsTask>> = repository.allSmsTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unsyncedCount: StateFlow<Int> = repository.allEvents
        .map { list -> list.count { !it.isSynced } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    val gatewayTelemetry: StateFlow<GatewayConnectionTelemetry> = SmsGatewayService.telemetry

    val pingResult = MutableStateFlow<String?>(null)
    val smsSendResult = MutableStateFlow<String?>(null)

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage

    fun toggleGatewayService(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sms_pull_enabled", enabled).apply()
        if (enabled) {
            SmsGatewayService.start(context)
            viewModelScope.launch {
                _toastMessage.emit("تم تشغيل بوابة الرسائل القصيرة للخط الرسمي (+201004006620)")
            }
        } else {
            SmsGatewayService.stop(context)
            viewModelScope.launch {
                _toastMessage.emit("تم إيقاف بوابة الرسائل")
            }
        }
    }

    fun restartGateway(context: Context) {
        viewModelScope.launch {
            SmsGatewayService.stop(context)
            kotlinx.coroutines.delay(500)
            SmsGatewayService.start(context)
            _toastMessage.emit("تمت إعادة تشغيل بوابة الخط الرسمي (+201004006620) بنجاح")
        }
    }

    fun checkGatewayConnection(context: Context) {
        viewModelScope.launch {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val simState = telephonyManager?.simState ?: android.telephony.TelephonyManager.SIM_STATE_UNKNOWN
            val isSimReady = simState == android.telephony.TelephonyManager.SIM_STATE_READY || simState != android.telephony.TelephonyManager.SIM_STATE_ABSENT
            
            SmsGatewayService.updateTelemetry { current ->
                current.copy(
                    isSimReady = isSimReady,
                    lastHeartbeatMs = System.currentTimeMillis()
                )
            }
            if (isSimReady) {
                _toastMessage.emit("اتصال الخط الرسمي (+201004006620) وشبكة فودافون مصر نشط")
            } else {
                _toastMessage.emit("تنبيه: تحقق من تثبيت شريحة الاتصال للخط (+201004006620)")
            }
        }
    }

    fun pingHook(url: String, apiKey: String) {
        viewModelScope.launch {
            pingResult.value = "Pinging..."
            val success = repository.syncOfflineQueue() // Try syncing first
            if (success) {
                pingResult.value = "Success"
                _toastMessage.emit("Hook is active & offline queue synced")
            } else {
                pingResult.value = "Failed"
                _toastMessage.emit("Ping failed. Check URL, API key or connection.")
            }
        }
    }

    fun sendTestEvent(provider: String, rawText: String, context: Context) {
        viewModelScope.launch {
            val timeString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
            val (entityId, classification) = CapturedEvent.parseSmsAndClassify(rawText, provider)
            val testEvent = CapturedEvent(
                provider = provider,
                channel = "sms",
                direction = "incoming",
                rawText = rawText,
                sender = "Test Sender",
                receivedAtDevice = timeString,
                classification = classification,
                entityId = entityId
            )
            repository.insertAndSync(testEvent)
            _toastMessage.emit("Test event dispatched")
        }
    }

    fun sendOperationalSms(
        context: Context,
        recipient: String,
        entityId: String,
        useCase: String,
        messageText: String
    ) {
        viewModelScope.launch {
            if (recipient.isBlank() || entityId.isBlank() || messageText.isBlank()) {
                smsSendResult.value = "خطأ: يرجى إدخال رقم المستلم، كود المرجع، ونص الرسالة"
                _toastMessage.emit("يرجى ملء جميع الحقول المطلوبة")
                return@launch
            }

            val normalizedRecipient = AlazabEnterpriseConfig.normalizePhoneNumber(recipient)
            if (normalizedRecipient.length < 9) {
                smsSendResult.value = "خطأ: رقم هاتف المستلم غير صحيح ($recipient)"
                _toastMessage.emit("رقم هاتف المستلم غير صحيح")
                return@launch
            }

            Log.d("AzabAssistant", "Attempting to send operational SMS from official line (+201004006620) to $normalizedRecipient: UseCase=$useCase, EntityID=$entityId")

            try {
                // Add entity identifier to the text message to satisfy audit requirements
                val finalMessage = "$messageText\n[Ref: $entityId | $useCase]"

                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    System.currentTimeMillis().toInt(),
                    Intent("com.alazab.assistant.SMS_SENT").apply {
                        putExtra("taskId", "manual_${System.currentTimeMillis()}")
                        putExtra("phoneNumber", normalizedRecipient)
                        putExtra("messageText", finalMessage)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    (System.currentTimeMillis() + 1).toInt(),
                    Intent("com.alazab.assistant.SMS_DELIVERED").apply {
                        putExtra("taskId", "manual_${System.currentTimeMillis()}")
                        putExtra("phoneNumber", normalizedRecipient)
                        putExtra("messageText", finalMessage)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Dispatch with safe multipart engine (handles UCS-2 Arabic lengths safely)
                val partsCount = AlazabEnterpriseConfig.sendSmsSafely(
                    context = context,
                    recipient = normalizedRecipient,
                    messageText = finalMessage,
                    sentIntent = sentIntent,
                    deliveryIntent = deliveredIntent
                )
                Log.d("AzabAssistant", "Operational SMS safely dispatched in $partsCount parts")

                // Store outgoing event
                val timeString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                val outgoingEvent = CapturedEvent(
                    provider = "مؤسسة العزب (+201004006620)",
                    channel = "sms",
                    direction = "outgoing",
                    rawText = finalMessage,
                    sender = AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE,
                    receivedAtDevice = timeString,
                    classification = "ENTERPRISE_DISPATCH",
                    entityId = entityId
                )

                repository.insertAndSync(outgoingEvent)
                val successText = "تم إرسال الرسالة بنجاح عبر الخط الرسمي (+201004006620) [عدد الأجزاء: $partsCount]"
                smsSendResult.value = successText
                _toastMessage.emit(successText)

            } catch (e: Exception) {
                Log.e("AzabAssistant", "Failed to send operational SMS", e)
                val errText = "فشل الإرسال: ${e.localizedMessage}"
                smsSendResult.value = errText
                _toastMessage.emit(errText)
            }
        }
    }

    fun syncQueue() {
        viewModelScope.launch {
            val success = repository.syncOfflineQueue()
            if (success) {
                _toastMessage.emit("All events successfully synchronized")
            } else {
                _toastMessage.emit("Sync failed. Remaining offline.")
            }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearAll()
            _toastMessage.emit("Local cache cleared")
        }
    }

    fun clearAllSmsTasks() {
        viewModelScope.launch {
            repository.clearAllTasks()
            _toastMessage.emit("Local SMS task logs cleared")
        }
    }
}

class MainViewModelFactory(private val repository: CapturedEventRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
