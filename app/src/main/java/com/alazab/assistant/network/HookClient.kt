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
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class HookClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun sendEvent(event: CapturedEvent, baseUrl: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("source_app", event.sourceApp)
                put("source_app_package", event.sourceAppPackage)
                put("owner_app", event.ownerApp)
                put("owner_app_package", event.ownerAppPackage)
                put("device_id", event.deviceId)
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
            val body = json.toString().toRequestBody(mediaType)

            Log.d("AzabAssistant", "Sending event payload to $baseUrl: $json")

            val request = Request.Builder()
                .url(baseUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .build()

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (success) {
                    Log.d("AzabAssistant", "Event uploaded successfully: Code ${response.code}")
                } else {
                    Log.e("AzabAssistant", "Event upload failed: Code ${response.code}, Msg: ${response.message}")
                }
                success
            }
        } catch (e: Exception) {
            Log.e("AzabAssistant", "Error during hook execution", e)
            false
        }
    }

    suspend fun pingHook(baseUrl: String, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // A simple ping can be a dummy event or a GET if supported, but let's send a dummy test event
            val dummyEvent = CapturedEvent(
                provider = "ping_test",
                channel = "system",
                direction = "unknown",
                rawText = "Ping connection test",
                sender = "system",
                receivedAtDevice = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                classification = "SYSTEM"
            )
            val json = JSONObject().apply {
                put("source_app", dummyEvent.sourceApp)
                put("source_app_package", dummyEvent.sourceAppPackage)
                put("owner_app", dummyEvent.ownerApp)
                put("owner_app_package", dummyEvent.ownerAppPackage)
                put("device_id", dummyEvent.deviceId)
                put("provider", dummyEvent.provider)
                put("channel", dummyEvent.channel)
                put("direction", dummyEvent.direction)
                put("raw_text", dummyEvent.rawText)
                put("sender", dummyEvent.sender)
                put("received_at_device", dummyEvent.receivedAtDevice)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(baseUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("AzabAssistant", "Error pinging hook", e)
            false
        }
    }

    suspend fun pullSmsTasks(pullUrl: String, deviceId: String, apiKey: String): List<SmsTask> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("device_id", deviceId)
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(pullUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .build()

            Log.d("AzabAssistant", "Polling SMS Tasks from $pullUrl with payload: $json")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("AzabAssistant", "Failed to pull SMS tasks: Code ${response.code}")
                    return@withContext emptyList()
                }

                val responseBodyStr = response.body?.string() ?: ""
                Log.d("AzabAssistant", "Pulled tasks response: $responseBodyStr")

                val list = mutableListOf<SmsTask>()
                if (responseBodyStr.trim().startsWith("[")) {
                    val array = org.json.JSONArray(responseBodyStr)
                    val timeString = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val id = obj.optString("id", "") ?: obj.optString("taskId", "")
                        val phone = obj.optString("phone_number", "") ?: obj.optString("phoneNumber", "")
                        val text = obj.optString("message_text", "") ?: obj.optString("message", "")
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
                } else if (responseBodyStr.trim().startsWith("{")) {
                    val obj = JSONObject(responseBodyStr)
                    val tasksArr = obj.optJSONArray("tasks")
                    val timeString = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                    if (tasksArr != null) {
                        for (i in 0 until tasksArr.length()) {
                            val tObj = tasksArr.getJSONObject(i)
                            val id = tObj.optString("id", "") ?: tObj.optString("taskId", "")
                            val phone = tObj.optString("phone_number", "") ?: tObj.optString("phoneNumber", "")
                            val text = tObj.optString("message_text", "") ?: tObj.optString("message", "")
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
                    } else {
                        // Check if the single object is a task itself
                        val id = obj.optString("id", "") ?: obj.optString("taskId", "")
                        val phone = obj.optString("phone_number", "") ?: obj.optString("phoneNumber", "")
                        val text = obj.optString("message_text", "") ?: obj.optString("message", "")
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
                }
                list
            }
        } catch (e: Exception) {
            Log.e("AzabAssistant", "Error pulling SMS tasks", e)
            emptyList()
        }
    }

    suspend fun reportSmsStatus(reportUrl: String, taskId: String, deviceId: String, status: String, error: String?, apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("task_id", taskId)
                put("device_id", deviceId)
                put("status", status)
                if (error != null) {
                    put("error", error)
                }
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(reportUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("x-api-key", apiKey)
                .build()

            Log.d("AzabAssistant", "Reporting task $taskId status to $reportUrl: $json")

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                if (success) {
                    Log.d("AzabAssistant", "Successfully reported status for task: $taskId")
                } else {
                    Log.e("AzabAssistant", "Failed to report status for task: $taskId. Code: ${response.code}")
                }
                success
            }
        } catch (e: Exception) {
            Log.e("AzabAssistant", "Error reporting SMS task status", e)
            false
        }
    }
}
