package com.alazab.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.alazab.assistant.data.AppDatabase
import com.alazab.assistant.data.CapturedEvent
import com.alazab.assistant.data.CapturedEventRepository
import com.alazab.assistant.network.HookClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d("AzabSmsReceiver", "SMS received action triggered")
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
            
            val db = AppDatabase.getDatabase(context)
            val repository = CapturedEventRepository(db.capturedEventDao(), db.smsTaskDao(), HookClient(), context)
            
            for (sms in messages) {
                val sender = sms.originatingAddress ?: "Unknown"
                val body = sms.messageBody ?: ""
                val timestamp = sms.timestampMillis
                
                Log.d("AzabSmsReceiver", "Received SMS from: $sender")
                Log.d("AzabSmsReceiver", "SMS Body: $body")
                
                // Identify provider
                val provider = when {
                    sender.contains("Vodafone", ignoreCase = true) || sender.contains("VF", ignoreCase = true) -> "Vodafone Cash"
                    sender.contains("QNB", ignoreCase = true) -> "QNB"
                    sender.contains("CIB", ignoreCase = true) -> "CIB"
                    sender.contains("IPN", ignoreCase = true) || body.contains("IPN", ignoreCase = true) -> "IPN"
                    sender.contains("InstaPay", ignoreCase = true) -> "InstaPay"
                    sender.contains("Fawry", ignoreCase = true) -> "Fawry"
                    else -> "SMS Provider"
                }
                
                // Use robust helper parsing to classify and extract Reference ID (entityId)
                val (entityId, classification) = CapturedEvent.parseSmsAndClassify(body, sender)
                
                val timeString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(timestamp))
                
                val event = CapturedEvent(
                    provider = provider,
                    channel = "sms",
                    direction = "incoming",
                    rawText = body,
                    sender = sender,
                    receivedAtDevice = timeString,
                    classification = classification,
                    entityId = entityId
                )
                
                // Save and sync in coroutine
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repository.insertAndSync(event)
                        
                        // Update last received SMS in preferences
                        val prefs = context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putString("last_sms_text", "$sender: $body")
                            .apply()
                    } catch (e: Exception) {
                        Log.e("AzabSmsReceiver", "Error saving received SMS event", e)
                    }
                }
            }
        }
    }
}
