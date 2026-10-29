package com.alazab.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.alazab.assistant.data.AppDatabase
import com.alazab.assistant.data.CapturedEvent
import com.alazab.assistant.data.CapturedEventRepository
import com.alazab.assistant.network.HookClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Production-hardened SMS BroadcastReceiver for Alazab Enterprise (+201004006620).
 * Features:
 * - Uses goAsync() to guarantee execution completes without ANR or premature OS termination.
 * - Concatenates multipart incoming SMS parts into a single coherent message text.
 * - Prioritizes official enterprise messages from or to +201004006620.
 * - Extracts financial and operational confirmation references accurately.
 */
class SmsReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d("AzabSmsReceiver", "SMS_RECEIVED_ACTION captured")
        val messages: Array<SmsMessage>? = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.w("AzabSmsReceiver", "No messages found in SMS intent")
            return
        }

        // Use goAsync() to keep the receiver process active during database and network operations
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                // Group multipart messages by originating sender to reconstruct complete bodies
                val groupedMessages = messages.groupBy { it.originatingAddress ?: "Unknown" }

                val db = AppDatabase.getDatabase(context)
                val repository = CapturedEventRepository(
                    db.capturedEventDao(),
                    db.smsTaskDao(),
                    HookClient(),
                    context
                )
                val prefs = context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)

                for ((sender, parts) in groupedMessages) {
                    val fullBody = parts.joinToString(separator = "") { it.messageBody ?: "" }
                    val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val normalizedSender = AlazabEnterpriseConfig.normalizePhoneNumber(sender)
                    val isOfficialLine = AlazabEnterpriseConfig.isEnterprisePhone(sender) ||
                            fullBody.contains("+201004006620") ||
                            fullBody.contains("01004006620")

                    Log.d("AzabSmsReceiver", "Processing message from: $sender (Normalized: $normalizedSender, Official: $isOfficialLine)")
                    Log.d("AzabSmsReceiver", "Full reconstructed body: $fullBody")

                    // Identify provider with priority to Alazab Enterprise and Egyptian payment networks
                    val provider = when {
                        isOfficialLine -> "مؤسسة العزب (+201004006620)"
                        sender.contains("Vodafone", ignoreCase = true) || sender.contains("VF", ignoreCase = true) || sender == "12345" -> "Vodafone Cash"
                        sender.contains("IPN", ignoreCase = true) || sender.contains("InstaPay", ignoreCase = true) || fullBody.contains("InstaPay", ignoreCase = true) -> "InstaPay"
                        sender.contains("QNB", ignoreCase = true) -> "QNB"
                        sender.contains("CIB", ignoreCase = true) -> "CIB"
                        sender.contains("Fawry", ignoreCase = true) -> "Fawry"
                        else -> "SMS Provider ($sender)"
                    }

                    // Parse and classify
                    val (entityId, classification) = CapturedEvent.parseSmsAndClassify(fullBody, sender)

                    val timeString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(timestamp))

                    val event = CapturedEvent(
                        provider = provider,
                        channel = "sms",
                        direction = "incoming",
                        rawText = fullBody,
                        sender = if (isOfficialLine) AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE else sender,
                        receivedAtDevice = timeString,
                        classification = classification,
                        entityId = entityId
                    )

                    // Persist to Room database and dispatch sync to cloud hook
                    repository.insertAndSync(event)

                    // Update UI preferences
                    prefs.edit()
                        .putString("last_sms_text", "$provider: $fullBody")
                        .putString("last_sms_sender", sender)
                        .putLong("last_sms_timestamp", timestamp)
                        .apply()

                    Log.d("AzabSmsReceiver", "Successfully processed & persisted incoming SMS from $sender (Ref: $entityId, Class: $classification)")
                }
            } catch (e: Exception) {
                Log.e("AzabSmsReceiver", "Error processing incoming SMS intent", e)
            } finally {
                // Always finish the broadcast to release Android wakelock
                pendingResult.finish()
            }
        }
    }
}
