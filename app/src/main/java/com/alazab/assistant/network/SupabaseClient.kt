package com.alazab.assistant.network

import android.util.Log
import com.alazab.assistant.data.CapturedEvent
import com.alazab.assistant.data.SmsTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Inserts a CapturedEvent into Supabase 'captured_events' table
     */
    suspend fun insertEvent(url: String, key: String, event: CapturedEvent): Boolean = withContext(Dispatchers.IO) {
        try {
            if (url.isEmpty() || key.isEmpty()) return@withContext false

            val requestUrl = if (url.endsWith("/")) "${url}rest/v1/captured_events" else "$url/rest/v1/captured_events"

            val json = JSONObject().apply {
                put("provider", event.provider)
                put("channel", event.channel)
                put("direction", event.direction)
                put("raw_text", event.rawText)
                put("sender", event.sender)
                put("received_at_device", event.receivedAtDevice)
                put("classification", event.classification)
                put("entity_id", event.entityId)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .post(requestBody)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build()

            Log.d("SupabaseClient", "Sending event to Supabase URL: $requestUrl")

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("SupabaseClient", "Supabase insert event response success: $success, code: ${response.code}")
                success
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Error inserting event into Supabase", e)
            false
        }
    }

    /**
     * Pulls pending tasks from Supabase 'sms_tasks' table
     */
    suspend fun pullSmsTasks(url: String, key: String): List<SmsTask> = withContext(Dispatchers.IO) {
        try {
            if (url.isEmpty() || key.isEmpty()) return@withContext emptyList()

            // Filter for PENDING status in Supabase table
            val requestUrl = if (url.endsWith("/")) {
                "${url}rest/v1/sms_tasks?status=eq.PENDING"
            } else {
                "$url/rest/v1/sms_tasks?status=eq.PENDING"
            }

            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d("SupabaseClient", "Polling SMS tasks from Supabase URL: $requestUrl")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("SupabaseClient", "Failed to pull tasks from Supabase: Code ${response.code}")
                    return@withContext emptyList()
                }

                val responseBodyStr = response.body?.string() ?: ""
                Log.d("SupabaseClient", "Supabase pulled tasks raw json: $responseBodyStr")

                val list = mutableListOf<SmsTask>()
                val array = JSONArray(responseBodyStr)
                val timeString = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("task_id", "")
                    val phone = obj.optString("phone_number", "")
                    val text = obj.optString("message_text", "")
                    if (id.isNotEmpty() && phone.isNotEmpty() && text.isNotEmpty()) {
                        list.add(
                            SmsTask(
                                taskId = id,
                                phoneNumber = phone,
                                messageText = text,
                                status = "PENDING",
                                createdAt = timeString
                            )
                        )
                    }
                }
                list
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Error pulling tasks from Supabase", e)
            emptyList()
        }
    }

    /**
     * Reports status and optional errors of SMS tasks directly in Supabase table
     */
    suspend fun reportSmsStatus(url: String, key: String, taskId: String, status: String, error: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            if (url.isEmpty() || key.isEmpty()) return@withContext false

            val requestUrl = if (url.endsWith("/")) {
                "${url}rest/v1/sms_tasks?task_id=eq.$taskId"
            } else {
                "$url/rest/v1/sms_tasks?task_id=eq.$taskId"
            }

            val timeNow = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())

            val json = JSONObject().apply {
                put("status", status)
                put("last_attempt_at", timeNow)
                put("error_message", error ?: JSONObject.NULL)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(requestUrl)
                .patch(requestBody)
                .addHeader("apikey", key)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=minimal")
                .build()

            Log.d("SupabaseClient", "Reporting SMS task status to Supabase URL: $requestUrl")

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                Log.d("SupabaseClient", "Supabase update task response success: $success, code: ${response.code}")
                success
            }
        } catch (e: Exception) {
            Log.e("SupabaseClient", "Error reporting status to Supabase", e)
            false
        }
    }
}
