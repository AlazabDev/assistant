package com.alazab.assistant

/**
 * Real-time operational states of the Alazab Enterprise SMS Gateway.
 */
sealed class GatewayState(
    val code: String,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String
) {
    /**
     * Gateway service is turned off or disabled.
     */
    object Stopped : GatewayState(
        code = "STOPPED",
        titleAr = "معطلة",
        titleEn = "Disabled",
        descriptionAr = "البوابة متوقفة حالياً. قم بتفعيل المفتاح لتشغيل خدمة المزامنة والإرسال التلقائي."
    )

    /**
     * Gateway is fully operational, connected to the cellular modem, and ready to dispatch messages.
     */
    object Ready : GatewayState(
        code = "READY",
        titleAr = "جاهزة ومتصلة",
        titleEn = "Ready",
        descriptionAr = "البوابة متصلة بالشبكة ومستعدة لإرسال واستقبال الرسائل فورياً عبر الخط الرسمي (+201004006620)."
    )

    /**
     * Gateway is actively sending a message through the cellular modem.
     */
    data class Sending(
        val recipient: String,
        val taskId: String,
        val partCount: Int = 1,
        val startedAtMs: Long = System.currentTimeMillis()
    ) : GatewayState(
        code = "SENDING",
        titleAr = "جاري الإرسال...",
        titleEn = "Sending",
        descriptionAr = "يتم الآن إرسال رسالة ($partCount أجزاء) إلى $recipient عبر الخط الرسمي (+201004006620)."
    )

    /**
     * Gateway encountered a cellular, limit, or network error.
     */
    data class Error(
        val errorMessage: String,
        val timestamp: Long = System.currentTimeMillis()
    ) : GatewayState(
        code = "ERROR",
        titleAr = "تنبيه / خطأ",
        titleEn = "Error",
        descriptionAr = errorMessage
    )
}

/**
 * Comprehensive connection telemetry for the +201004006620 line.
 */
data class GatewayConnectionTelemetry(
    val officialPhone: String = AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE,
    val carrierName: String = "فودافون مصر (Vodafone Egypt)",
    val state: GatewayState = GatewayState.Stopped,
    val isServiceRunning: Boolean = false,
    val isSimReady: Boolean = true,
    val pendingQueueCount: Int = 0,
    val sentTodayCount: Int = 0,
    val dailyLimit: Int = 100,
    val sentThisMonthCount: Int = 0,
    val monthlyLimit: Int = 2000,
    val lastActiveTimestamp: Long = 0L,
    val lastDispatchedRecipient: String? = null,
    val lastDispatchedTaskId: String? = null,
    val lastDispatchedStatus: String? = null, // "SENT", "DELIVERED", "FAILED"
    val lastDispatchedTime: String? = null,
    val lastSuccessTimestamp: Long = 0L,
    val lastErrorTimestamp: Long = 0L,
    val lastErrorMessage: String? = null,
    val lastHeartbeatMs: Long = System.currentTimeMillis()
)
