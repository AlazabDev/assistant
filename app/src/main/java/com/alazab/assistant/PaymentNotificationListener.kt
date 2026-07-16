package com.alazab.assistant

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

class PaymentNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("AzabAssistant", "PaymentNotificationListener connected successfully")
        val prefs = getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("listener_connected", true).apply()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("AzabAssistant", "PaymentNotificationListener disconnected")
        val prefs = getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("listener_connected", false).apply()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: ""
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        
        val fullText = "$title : $text" + (if (subText.isNotEmpty()) " ($subText)" else "")
        Log.d("AzabAssistant", "Notification detected from package: $packageName -> $fullText")

        // Filter and capture payment applications
        val isInstaPay = packageName == "com.egyptianbanks.instapay"
        val isFawry = packageName.contains("fawry", ignoreCase = true) || 
                       title.contains("fawry", ignoreCase = true) || 
                       text.contains("fawry", ignoreCase = true) ||
                       title.contains("فوري", ignoreCase = true) ||
                       text.contains("فوري", ignoreCase = true)
        
        val isVodafone = packageName.contains("vodafone", ignoreCase = true) || 
                         title.contains("vodafone", ignoreCase = true) || 
                         text.contains("vodafone", ignoreCase = true)

        val isRelevant = isInstaPay || isFawry || isVodafone || 
                         title.contains("تم إرسال", ignoreCase = true) || 
                         text.contains("تم إرسال", ignoreCase = true) ||
                         title.contains("تحويل", ignoreCase = true) ||
                         text.contains("تحويل", ignoreCase = true) ||
                         title.contains("محفظة", ignoreCase = true) ||
                         text.contains("محفظة", ignoreCase = true)

        if (!isRelevant) {
            return
        }

        val provider = when {
            isInstaPay -> "InstaPay"
            isFawry -> "Fawry"
            packageName.contains("vodafone", ignoreCase = true) -> "Vodafone Cash"
            packageName.contains("qnb", ignoreCase = true) -> "QNB"
            packageName.contains("cib", ignoreCase = true) -> "CIB"
            else -> "Payment Application"
        }

        // Classification & Reference ID extraction
        val (entityId, classification) = CapturedEvent.parseNotificationAndClassify(packageName, title, text)

        val timeString = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(sbn.postTime))

        val event = CapturedEvent(
            provider = provider,
            channel = "notification",
            direction = "incoming",
            rawText = fullText,
            sender = packageName,
            receivedAtDevice = timeString,
            classification = classification,
            entityId = entityId
        )

        val db = AppDatabase.getDatabase(this)
        val repository = CapturedEventRepository(db.capturedEventDao(), db.smsTaskDao(), HookClient(), this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.insertAndSync(event)
                
                val prefs = getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("last_notification_text", "[$packageName] $fullText")
                    .apply()
            } catch (e: Exception) {
                Log.e("AzabAssistant", "Error saving payment notification event", e)
            }
        }
    }
}
