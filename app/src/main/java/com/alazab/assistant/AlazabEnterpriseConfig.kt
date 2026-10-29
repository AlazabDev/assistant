package com.alazab.assistant

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import java.util.regex.Pattern
import kotlin.math.ceil

/**
 * Enterprise telecommunication configuration and utilities for Alazab Enterprise (مؤسسة العزب).
 * Designated official line: +201004006620 (Vodafone Egypt).
 */
object AlazabEnterpriseConfig {

    const val OFFICIAL_ENTERPRISE_PHONE = "+201004006620"
    const val OFFICIAL_ENTERPRISE_PHONE_LOCAL = "01004006620"
    const val OFFICIAL_ENTERPRISE_PHONE_INT_NO_PLUS = "201004006620"
    const val ENTERPRISE_NAME = "مؤسسة العزب"
    const val ENTERPRISE_NAME_EN = "Alazab Enterprise"
    const val OPERATOR_NAME = "Vodafone Egypt"

    /**
     * Normalizes Eastern Arabic digits (٠-٩) into Western digits (0-9)
     * and normalizes Egyptian phone numbers into standardized E.164 format (+201xxxxxxxxx).
     */
    fun normalizePhoneNumber(rawNumber: String): String {
        if (rawNumber.isBlank()) return ""

        // 1. Convert Eastern Arabic numerals to standard 0-9
        val builder = StringBuilder()
        for (ch in rawNumber.trim()) {
            when (ch) {
                '٠' -> builder.append('0')
                '١' -> builder.append('1')
                '٢' -> builder.append('2')
                '٣' -> builder.append('3')
                '٤' -> builder.append('4')
                '٥' -> builder.append('5')
                '٦' -> builder.append('6')
                '٧' -> builder.append('7')
                '٨' -> builder.append('8')
                '٩' -> builder.append('9')
                else -> builder.append(ch)
            }
        }

        // 2. Remove all spaces, hyphens, brackets
        var cleaned = builder.toString().replace(Regex("[\\s\\-\\(\\)\\[\\]\\.]"), "")

        // 3. Handle Egyptian formats:
        // Case: "00201004006620" -> "+201004006620"
        if (cleaned.startsWith("0020")) {
            cleaned = "+20" + cleaned.substring(4)
        }
        else if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2)
        }
        // Case: "201004006620" without plus -> "+201004006620"
        else if (cleaned.startsWith("201") && cleaned.length == 12) {
            cleaned = "+$cleaned"
        }
        // Case: "01004006620" -> "+201004006620"
        else if (cleaned.startsWith("01") && cleaned.length == 11) {
            cleaned = "+2" + cleaned
        }
        // Case: "1004006620" (missing leading zero) -> "+201004006620"
        else if ((cleaned.startsWith("10") || cleaned.startsWith("11") || cleaned.startsWith("12") || cleaned.startsWith("15")) && cleaned.length == 10) {
            cleaned = "+20$cleaned"
        }

        return cleaned
    }

    /**
     * Checks if a phone number matches Alazab Enterprise's official line (+201004006620).
     */
    fun isEnterprisePhone(number: String?): Boolean {
        if (number == null || number.isBlank()) return false
        val normalized = normalizePhoneNumber(number)
        return normalized == OFFICIAL_ENTERPRISE_PHONE ||
                normalized.endsWith("1004006620") ||
                number.contains("01004006620") ||
                number.contains("201004006620")
    }

    /**
     * Represents SMS breakdown metrics according to 3GPP TS 23.040.
     */
    data class SmsEncodingInfo(
        val isUnicodeArabic: Boolean,
        val totalCharacters: Int,
        val partCount: Int,
        val charactersRemainingInCurrentPart: Int,
        val maxCharsPerSingleSms: Int
    )

    /**
     * Detects whether text requires UCS-2 (Unicode for Arabic/emojis) or GSM 7-bit,
     * and computes exact segment count and remaining characters.
     */
    fun analyzeSmsEncoding(text: String): SmsEncodingInfo {
        val totalLength = text.length
        if (totalLength == 0) {
            return SmsEncodingInfo(
                isUnicodeArabic = false,
                totalCharacters = 0,
                partCount = 1,
                charactersRemainingInCurrentPart = 160,
                maxCharsPerSingleSms = 160
            )
        }

        // Check if any character is outside standard 7-bit GSM alphabet
        val isUnicode = text.any { ch -> ch.code > 127 }

        if (isUnicode) {
            // UCS-2 encoding (Arabic, etc.)
            // Single SMS: max 70 chars
            // Multipart: max 67 chars per part
            val partCount = if (totalLength <= 70) 1 else ceil(totalLength / 67.0).toInt()
            val remaining = if (partCount == 1) {
                70 - totalLength
            } else {
                (partCount * 67) - totalLength
            }
            return SmsEncodingInfo(
                isUnicodeArabic = true,
                totalCharacters = totalLength,
                partCount = partCount,
                charactersRemainingInCurrentPart = remaining,
                maxCharsPerSingleSms = 70
            )
        } else {
            // GSM 7-bit encoding
            // Single SMS: max 160 chars
            // Multipart: max 153 chars per part
            val partCount = if (totalLength <= 160) 1 else ceil(totalLength / 153.0).toInt()
            val remaining = if (partCount == 1) {
                160 - totalLength
            } else {
                (partCount * 153) - totalLength
            }
            return SmsEncodingInfo(
                isUnicodeArabic = false,
                totalCharacters = totalLength,
                partCount = partCount,
                charactersRemainingInCurrentPart = remaining,
                maxCharsPerSingleSms = 160
            )
        }
    }

    /**
     * Safely obtains the SmsManager instance according to Android OS version
     * and optional subscription ID.
     */
    fun getSmsManager(context: Context, subscriptionId: Int? = null): SmsManager {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val base = context.getSystemService(SmsManager::class.java)
                if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    base.createForSubscriptionId(subscriptionId)
                } else {
                    base
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                if (subscriptionId != null && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            Log.w("AlazabTelephony", "Failed to get subscription SmsManager, fallback to default", e)
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * Dispatches an SMS message safely:
     * - Automatically divides long messages (prevents IllegalArgumentException with Arabic texts)
     * - Uses sendTextMessage for single part
     * - Uses sendMultipartTextMessage for multiple parts
     * - Matches sent/delivered PendingIntents to all segments
     */
    fun sendSmsSafely(
        context: Context,
        recipient: String,
        messageText: String,
        sentIntent: PendingIntent?,
        deliveryIntent: PendingIntent?,
        subscriptionId: Int? = null
    ): Int {
        val normalizedRecipient = normalizePhoneNumber(recipient)
        require(normalizedRecipient.isNotBlank()) { "Recipient phone number is empty or invalid" }
        require(messageText.isNotBlank()) { "Message text is empty" }

        val smsManager = getSmsManager(context, subscriptionId)
        val parts = smsManager.divideMessage(messageText)

        Log.d("AlazabTelephony", "Dispatching SMS to $normalizedRecipient, parts count: ${parts.size}, chars: ${messageText.length}")

        if (parts.size <= 1) {
            smsManager.sendTextMessage(
                normalizedRecipient,
                null,
                if (parts.isEmpty()) messageText else parts[0],
                sentIntent,
                deliveryIntent
            )
        } else {
            val sentIntents = ArrayList<PendingIntent?>(parts.size)
            val deliveryIntents = ArrayList<PendingIntent?>(parts.size)

            for (i in parts.indices) {
                // Ensure the final part or each part carries the confirmation intent
                sentIntents.add(sentIntent)
                deliveryIntents.add(deliveryIntent)
            }

            smsManager.sendMultipartTextMessage(
                normalizedRecipient,
                null,
                parts,
                sentIntents,
                deliveryIntents
            )
        }

        return parts.size
    }

    /**
     * Maps Android telephony error codes to user-friendly Arabic & English descriptions.
     */
    fun getSmsErrorDescription(resultCode: Int): String {
        return when (resultCode) {
            SmsManager.RESULT_ERROR_GENERIC_FAILURE ->
                "فشل عام في شبكة الاتصالات (تأكد من رصيد الشريحة وتغطية الشبكة) [GENERIC_FAILURE]"
            SmsManager.RESULT_ERROR_NO_SERVICE ->
                "لا توجد تغطية شبكة محمول متاحة حالياً [NO_SERVICE]"
            SmsManager.RESULT_ERROR_NULL_PDU ->
                "خطأ في ترميز رسالة الـ PDU المرسلة [NULL_PDU]"
            SmsManager.RESULT_ERROR_RADIO_OFF ->
                "شريحة الاتصال معطلة أو وضع الطيران نشط [RADIO_OFF]"
            else ->
                "خطأ غير محدد في إرسال الرسالة القصيرة (رمز: $resultCode)"
        }
    }
}
