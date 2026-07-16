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

    val pingResult = MutableStateFlow<String?>(null)
    val smsSendResult = MutableStateFlow<String?>(null)

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage

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
                smsSendResult.value = "Error: All fields are required"
                _toastMessage.emit("Please fill in recipient, Entity ID, and message")
                return@launch
            }

            Log.d("AzabAssistant", "Attempting to send operational SMS: UseCase=$useCase, EntityID=$entityId")

            try {
                val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
                } else {
                    SmsManager.getDefault()
                }

                // Add entity identifier to the text message to satisfy audit requirements
                val finalMessage = "$messageText\n[Ref: $entityId | $useCase]"

                val sentIntent = PendingIntent.getBroadcast(
                    context, 0, Intent("SMS_SENT"), PendingIntent.FLAG_IMMUTABLE
                )
                val deliveredIntent = PendingIntent.getBroadcast(
                    context, 0, Intent("SMS_DELIVERED"), PendingIntent.FLAG_IMMUTABLE
                )

                smsManager.sendTextMessage(recipient, null, finalMessage, sentIntent, deliveredIntent)
                Log.d("AzabAssistant", "Operational SMS sent via manager")

                // Store outgoing event
                val timeString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
                val outgoingEvent = CapturedEvent(
                    provider = "Azab System",
                    channel = "sms",
                    direction = "outgoing",
                    rawText = finalMessage,
                    sender = "Device",
                    receivedAtDevice = timeString,
                    classification = "SYSTEM",
                    entityId = entityId
                )

                repository.insertAndSync(outgoingEvent)
                smsSendResult.value = "Sent successfully"
                _toastMessage.emit("Operational SMS dispatched successfully")

            } catch (e: Exception) {
                Log.e("AzabAssistant", "Failed to send operational SMS", e)
                smsSendResult.value = "Error: ${e.localizedMessage}"
                _toastMessage.emit("Sending failed: ${e.localizedMessage}")
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
