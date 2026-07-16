package com.alazab.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captured_events")
data class CapturedEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceApp: String = "azab_assistant",
    val sourceAppPackage: String = "com.alazab.assistant",
    val ownerApp: String = "azab_payments",
    val ownerAppPackage: String = "com.alazab.payments",
    val deviceId: String = "azab-payment-phone-01",
    val provider: String,
    val channel: String, // "sms" or "notification"
    val direction: String, // "incoming" or "outgoing" or "unknown"
    val rawText: String,
    val sender: String,
    val receivedAtDevice: String,
    val isSynced: Boolean = false,
    val classification: String = "UNKNOWN",
    val entityId: String? = null
) {
    companion object {
        fun parseSmsAndClassify(body: String, sender: String): Pair<String?, String> {
            var entityId: String? = null
            var classification = "SUPPORTING_PROOF"
            val normalizedBody = body.replace("\n", " ").replace("\r", " ")

            val isVfCash = sender.contains("VF-Cash", ignoreCase = true) || sender.contains("Vodafone", ignoreCase = true)
            if (isVfCash) {
                if (body.contains("تم استلام") || body.contains("تم إرسال") || body.contains("رصيد") || body.contains("محفظت")) {
                    classification = "STRONG_PROOF"
                }
                val regexOp = Regex("العملية\\s*:?\\s*(\\d+)")
                val matchResult = regexOp.find(normalizedBody)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else if (sender.contains("QNB", ignoreCase = true) || sender.contains("CIB", ignoreCase = true) || sender.contains("IPN", ignoreCase = true) || sender.contains("InstaPay", ignoreCase = true) || body.contains("IPN", ignoreCase = true)) {
                if (body.contains("transfer sent", ignoreCase = true) || body.contains("transfer received", ignoreCase = true) || body.contains("Successful transaction", ignoreCase = true) || body.contains("تم تنفيذ تحويل", ignoreCase = true) || body.contains("charged for", ignoreCase = true)) {
                    classification = "STRONG_PROOF"
                }
                val regexRef = Regex("Ref#?\\s*([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedBody)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            }

            if (entityId == null) {
                val regexRefGen = Regex("Ref(?:erence)?\\s*(?:Number)?:?\\s*#?\\s*([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRefGen.find(normalizedBody)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            }

            return Pair(entityId, classification)
        }

        fun parseNotificationAndClassify(packageName: String, title: String, text: String): Pair<String?, String> {
            val fullText = "$title : $text"
            val normalizedText = fullText.replace("\n", " ").replace("\r", " ")
            var entityId: String? = null
            var classification = "SUPPORTING_PROOF"

            val isInstaPay = packageName == "com.egyptianbanks.instapay" || packageName.contains("instapay", ignoreCase = true)
            val isVodafone = packageName.contains("vodafone", ignoreCase = true) || packageName.contains("myservices", ignoreCase = true)
            val isFawry = packageName.contains("fawry", ignoreCase = true)

            if (isInstaPay) {
                classification = "STRONG_PROOF"
                val regexRef = Regex("Ref#?\\s*([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedText)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else if (isVodafone) {
                if (text.contains("تم استلام") || text.contains("تم إرسال") || text.contains("رصيد") || text.contains("محفظة") || text.contains("Cash", ignoreCase = true)) {
                    classification = "STRONG_PROOF"
                }
                val regexOp = Regex("العملية\\s*:?\\s*(\\d+)")
                val matchResult = regexOp.find(normalizedText)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else if (isFawry) {
                if (text.contains("تم دفع") || text.contains("ناجحة") || text.contains("تم استلام") || text.contains("Ref", ignoreCase = true)) {
                    classification = "STRONG_PROOF"
                }
                val regexRef = Regex("Ref#?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedText)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else {
                val isTransfer = text.contains("تم استلام") || text.contains("تم إرسال") || text.contains("تحويل") || text.contains("transfer", ignoreCase = true)
                if (isTransfer) {
                    classification = "STRONG_PROOF"
                }
                val regexRefGen = Regex("Ref(?:erence)?\\s*(?:Number)?:?\\s*#?\\s*([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRefGen.find(normalizedText)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            }

            return Pair(entityId, classification)
        }
    }
}
