package com.alazab.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alazab.assistant.data.CapturedEvent
import com.alazab.assistant.data.CapturedEventRepository
import com.alazab.assistant.data.SmsTask
import com.alazab.assistant.templates.SmsTemplate
import com.alazab.assistant.templates.SmsTemplateCategory
import com.alazab.assistant.templates.SmsTemplateEngine
import com.alazab.assistant.templates.SmsTemplateRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val repository: CapturedEventRepository,
    private val templateRepository: SmsTemplateRepository
) : ViewModel() {

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

    // ==========================================
    // SMS Templates & Dynamic Variables Management
    // ==========================================

    val allTemplates: StateFlow<List<SmsTemplate>> = templateRepository.templates

    private val _selectedTemplate = MutableStateFlow<SmsTemplate?>(null)
    val selectedTemplate: StateFlow<SmsTemplate?> = _selectedTemplate.asStateFlow()

    private val _variableValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val variableValues: StateFlow<Map<String, String>> = _variableValues.asStateFlow()

    private val _isSendingFromTemplate = MutableStateFlow(false)
    val isSendingFromTemplate: StateFlow<Boolean> = _isSendingFromTemplate.asStateFlow()

    init {
        viewModelScope.launch {
            templateRepository.templates.collect { list ->
                if (_selectedTemplate.value == null && list.isNotEmpty()) {
                    selectTemplate(list.first())
                }
            }
        }
    }

    fun selectTemplate(template: SmsTemplate) {
        _selectedTemplate.value = template
        // Populate default values for variables if not present
        val currentVars = _variableValues.value.toMutableMap()
        for (variableKey in template.variables) {
            val normalized = SmsTemplateEngine.normalizeVariableKey(variableKey)
            if (!currentVars.containsKey(normalized) || currentVars[normalized].isNullOrBlank()) {
                val descriptor = SmsTemplateEngine.STANDARD_VARIABLES.find { it.key == normalized }
                val defaultVal = when (normalized) {
                    "otp" -> SmsTemplateEngine.generateRandomOtp(6)
                    "order_id" -> SmsTemplateEngine.generateRandomOrderId("AZ")
                    "date" -> SmsTemplateEngine.getCurrentTimestampFormatted()
                    "phone" -> AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE
                    else -> descriptor?.sampleValue ?: ""
                }
                currentVars[normalized] = defaultVal
            }
        }
        _variableValues.value = currentVars
    }

    fun updateVariable(key: String, value: String) {
        val normalized = SmsTemplateEngine.normalizeVariableKey(key)
        val updated = _variableValues.value.toMutableMap()
        updated[normalized] = value
        _variableValues.value = updated
    }

    fun generateOtpForVariable(key: String = "otp", digits: Int = 6) {
        val normalized = SmsTemplateEngine.normalizeVariableKey(key)
        val otp = SmsTemplateEngine.generateRandomOtp(digits)
        updateVariable(normalized, otp)
    }

    fun generateOrderIdForVariable(key: String = "order_id", prefix: String = "AZ") {
        val normalized = SmsTemplateEngine.normalizeVariableKey(key)
        val id = SmsTemplateEngine.generateRandomOrderId(prefix)
        updateVariable(normalized, id)
    }

    fun setEnterprisePhoneForVariable(key: String = "phone") {
        val normalized = SmsTemplateEngine.normalizeVariableKey(key)
        updateVariable(normalized, AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE)
    }

    fun setCurrentDateForVariable(key: String = "date") {
        val normalized = SmsTemplateEngine.normalizeVariableKey(key)
        updateVariable(normalized, SmsTemplateEngine.getCurrentTimestampFormatted())
    }

    fun addCustomTemplate(
        title: String,
        category: SmsTemplateCategory,
        rawBody: String,
        entityId: String
    ) {
        val created = templateRepository.addCustomTemplate(title, category, rawBody, entityId)
        selectTemplate(created)
        viewModelScope.launch {
            _toastMessage.emit("تمت إضافة القالب المخصص: $title")
        }
    }

    fun deleteCustomTemplate(templateId: String) {
        templateRepository.deleteCustomTemplate(templateId)
        viewModelScope.launch {
            _toastMessage.emit("تم حذف القالب المخصص")
        }
    }

    fun resetTemplatesToDefaults() {
        templateRepository.resetToDefaults()
        viewModelScope.launch {
            _toastMessage.emit("تمت استعادة قوالب المؤسسة الافتراضية")
        }
    }

    fun sendFromTemplate(context: Context) {
        val currentTpl = _selectedTemplate.value ?: return
        val currentVars = _variableValues.value
        val recipient = currentVars["phone"]?.trim() ?: ""

        if (recipient.isBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("يرجى إدخال رقم هاتف المستلم {phone} أولاً")
            }
            return
        }

        val normalizedRecipient = AlazabEnterpriseConfig.normalizePhoneNumber(recipient)
        if (normalizedRecipient.length < 9) {
            viewModelScope.launch {
                _toastMessage.emit("رقم هاتف المستلم غير صحيح ($recipient)")
            }
            return
        }

        val renderedMessage = SmsTemplateEngine.renderMessage(currentTpl.rawBody, currentVars)
        val entityId = currentVars["order_id"] ?: currentTpl.entityIdDefault

        viewModelScope.launch {
            _isSendingFromTemplate.value = true
            try {
                val sentIntent = PendingIntent.getBroadcast(
                    context,
                    System.currentTimeMillis().toInt(),
                    Intent("com.alazab.assistant.SMS_SENT").apply {
                        putExtra("taskId", "tpl_${System.currentTimeMillis()}")
                        putExtra("phoneNumber", normalizedRecipient)
                        putExtra("messageText", renderedMessage)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val deliveredIntent = PendingIntent.getBroadcast(
                    context,
                    (System.currentTimeMillis() + 1).toInt(),
                    Intent("com.alazab.assistant.SMS_DELIVERED").apply {
                        putExtra("taskId", "tpl_${System.currentTimeMillis()}")
                        putExtra("phoneNumber", normalizedRecipient)
                        putExtra("messageText", renderedMessage)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val partsCount = AlazabEnterpriseConfig.sendSmsSafely(
                    context = context,
                    recipient = normalizedRecipient,
                    messageText = renderedMessage,
                    sentIntent = sentIntent,
                    deliveryIntent = deliveredIntent
                )

                // Log outgoing event to repository
                val timeString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                val outgoingEvent = CapturedEvent(
                    provider = "مؤسسة العزب (+201004006620)",
                    channel = "sms",
                    direction = "outgoing",
                    rawText = renderedMessage,
                    sender = AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE,
                    receivedAtDevice = timeString,
                    classification = "TEMPLATE_${currentTpl.category.name}",
                    entityId = entityId
                )
                repository.insertAndSync(outgoingEvent)

                val successMsg = "تم إرسال الرسالة بنجاح عبر الخط الرسمي (+201004006620) [أجزاء: $partsCount]"
                smsSendResult.value = successMsg
                _toastMessage.emit(successMsg)

            } catch (e: Exception) {
                Log.e("AzabAssistant", "Failed to send SMS from template", e)
                val errMsg = "فشل الإرسال: ${e.localizedMessage}"
                smsSendResult.value = errMsg
                _toastMessage.emit(errMsg)
            } finally {
                _isSendingFromTemplate.value = false
            }
        }
    }

    // ==========================================
    // Gateway Controls & Operations
    // ==========================================

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
            val success = repository.syncOfflineQueue()
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

                val partsCount = AlazabEnterpriseConfig.sendSmsSafely(
                    context = context,
                    recipient = normalizedRecipient,
                    messageText = finalMessage,
                    sentIntent = sentIntent,
                    deliveryIntent = deliveredIntent
                )
                Log.d("AzabAssistant", "Operational SMS safely dispatched in $partsCount parts")

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

class MainViewModelFactory(
    private val repository: CapturedEventRepository,
    private val templateRepository: SmsTemplateRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, templateRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

