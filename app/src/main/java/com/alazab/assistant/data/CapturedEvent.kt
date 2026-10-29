package com.alazab.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.alazab.assistant.AlazabEnterpriseConfig

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

            // Check if from or directed to Alazab Enterprise official phone (+201004006620)
            val isOfficialEnterprise = AlazabEnterpriseConfig.isEnterprisePhone(sender) ||
                    body.contains("+201004006620") ||
                    body.contains("01004006620") ||
                    body.contains("مؤسسة العزب")

            val isVfCash = sender.contains("VF-Cash", ignoreCase = true) || sender.contains("Vodafone", ignoreCase = true) || sender.contains("12345")
            val isFawry = sender.contains("Fawry", ignoreCase = true) || body.contains("Fawry", ignoreCase = true)

            // Normalize Eastern Arabic numerals in body for clean regex matching
            val westernBody = AlazabEnterpriseConfig.normalizePhoneNumber(normalizedBody)

            if (isOfficialEnterprise) {
                classification = "ENTERPRISE_OFFICIAL"
                val regexRef = Regex("(?:\\[Ref:\\s*|Ref#?\\s*|مرجع:?\\s*|كود:?\\s*)([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedBody)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else if (isVfCash) {
                // Vodafone Cash incoming only = strong
                val isIncoming = body.contains("تم استلام") || body.contains("تم إيداع") || body.contains("تم ايداع") || body.contains("إليك") || body.contains("محول من") || body.contains("تحويل من")
                if (isIncoming) {
                    classification = "STRONG_PROOF"
                } else {
                    classification = "SUPPORTING_PROOF"
                }
                val regexOp = Regex("العملية\\s*:?\\s*([0-9٠-٩]+)")
                val matchResult = regexOp.find(normalizedBody)
                if (matchResult != null) {
                    entityId = AlazabEnterpriseConfig.normalizePhoneNumber(matchResult.groupValues[1])
                }
            } else if (isFawry) {
                // Fawry = standby
                classification = "STANDBY_PROOF"
                val regexRef = Regex("Ref#?\\s*([0-9٠-٩]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedBody)
                if (matchResult != null) {
                    entityId = AlazabEnterpriseConfig.normalizePhoneNumber(matchResult.groupValues[1])
                }
            } else if (sender.contains("QNB", ignoreCase = true) || sender.contains("CIB", ignoreCase = true) || sender.contains("IPN", ignoreCase = true) || sender.contains("InstaPay", ignoreCase = true) || body.contains("IPN", ignoreCase = true)) {
                // QNB / CIB / IPN transfer sent = supporting ONLY
                classification = "SUPPORTING_PROOF"
                val regexRef = Regex("Ref#?\\s*([a-zA-Z0-9٠-٩]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedBody)
                if (matchResult != null) {
                    entityId = AlazabEnterpriseConfig.normalizePhoneNumber(matchResult.groupValues[1])
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

            if (isVodafone) {
                // Vodafone Cash incoming only = strong
                val isIncoming = text.contains("تم استلام") || text.contains("تم إيداع") || text.contains("تم ايداع") || text.contains("إليك") || text.contains("محول من") || text.contains("تحويل من") || text.contains("Cash In", ignoreCase = true) || text.contains("Received", ignoreCase = true)
                if (isIncoming) {
                    classification = "STRONG_PROOF"
                } else {
                    classification = "SUPPORTING_PROOF"
                }
                val regexOp = Regex("العملية\\s*:?\\s*(\\d+)")
                val matchResult = regexOp.find(normalizedText)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else if (isFawry) {
                // Fawry = standby
                classification = "STANDBY_PROOF"
                val regexRef = Regex("Ref#?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedText)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else if (isInstaPay || packageName.contains("qnb", ignoreCase = true) || packageName.contains("cib", ignoreCase = true) || packageName.contains("ipn", ignoreCase = true)) {
                // QNB / CIB / IPN transfer sent = supporting ONLY
                classification = "SUPPORTING_PROOF"
                val regexRef = Regex("Ref#?\\s*([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
                val matchResult = regexRef.find(normalizedText)
                if (matchResult != null) {
                    entityId = matchResult.groupValues[1]
                }
            } else {
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
