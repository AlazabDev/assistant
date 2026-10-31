package com.alazab.assistant.templates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Categories for operational SMS templates.
 */
enum class SmsTemplateCategory(
    val id: String,
    val titleAr: String,
    val titleEn: String
) {
    ALL("all", "الكل", "All"),
    AUTH_OTP("auth", "المصادقة والأمان", "Authentication & OTP"),
    PASSWORD_RESET("password", "كلمة المرور والحسابات", "Password & Accounts"),
    MAINTENANCE("maintenance", "طلبات الصيانة", "Maintenance Requests"),
    PAYMENTS("payments", "المدفوعات والفواتير", "Payments & Invoices"),
    CUSTOMERS("customers", "خدمة العملاء والتقييم", "Customer Care"),
    ALERTS("alerts", "تنبيهات عاجلة", "Urgent Alerts");

    fun getIcon(): ImageVector {
        return when (this) {
            ALL -> Icons.Default.AllInclusive
            AUTH_OTP -> Icons.Default.Security
            PASSWORD_RESET -> Icons.Default.LockReset
            MAINTENANCE -> Icons.Default.Build
            PAYMENTS -> Icons.AutoMirrored.Filled.ReceiptLong
            CUSTOMERS -> Icons.Default.SentimentSatisfied
            ALERTS -> Icons.Default.NotificationsActive
        }
    }
}

/**
 * Standard variable definition and metadata for dynamic placeholders.
 */
data class VariableDescriptor(
    val key: String,
    val labelAr: String,
    val sampleValue: String,
    val description: String,
    val generatorType: VariableGeneratorType = VariableGeneratorType.NONE
)

enum class VariableGeneratorType {
    NONE,
    RANDOM_OTP_6,
    RANDOM_OTP_4,
    RANDOM_ORDER_ID,
    CURRENT_TIMESTAMP,
    ENTERPRISE_PHONE
}

/**
 * Operational SMS template model.
 */
data class SmsTemplate(
    val id: String,
    val title: String,
    val category: SmsTemplateCategory,
    val rawBody: String,
    val entityIdDefault: String = "ALAZAB_SMS",
    val isBuiltIn: Boolean = true,
    val variables: List<String> = emptyList()
)
