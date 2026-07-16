package com.alazab.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed")
            val prefs = context.getSharedPreferences("azab_assistant_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("sms_pull_enabled", false)
            if (enabled) {
                Log.d("BootReceiver", "SMS Pulling is enabled, starting SmsGatewayService...")
                SmsGatewayService.start(context)
            }
        }
    }
}
