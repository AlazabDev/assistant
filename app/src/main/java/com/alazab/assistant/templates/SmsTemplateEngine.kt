package com.alazab.assistant.templates

import com.alazab.assistant.AlazabEnterpriseConfig
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-performance template rendering engine supporting English and Arabic placeholders.
 */
object SmsTemplateEngine {

    private val random = SecureRandom()
    private val placeholderPattern = Pattern.compile("\\{([^{}]+)\\}")

    /**
     * Standard variable definitions supported by the engine.
     */
    val STANDARD_VARIABLES: List<VariableDescriptor> = listOf(
        VariableDescriptor(
            key = "phone",
            labelAr = "رقم الهاتف",
            sampleValue = "+201004006620",
            description = "رقم هاتف العميل أو المستلم",
            generatorType = VariableGeneratorType.ENTERPRISE_PHONE
        ),
        VariableDescriptor(
            key = "otp",
            labelAr = "رمز التحقق (OTP)",
            sampleValue = "849201",
            description = "كود التحقق لمرة واحدة أو تأكيد الدخول",
            generatorType = VariableGeneratorType.RANDOM_OTP_6
        ),
        VariableDescriptor(
            key = "order_id",
            labelAr = "رقم الطلب",
            sampleValue = "AZ-7821",
            description = "رقم مرجع الصيانة أو الفاتورة",
            generatorType = VariableGeneratorType.RANDOM_ORDER_ID
        ),
        VariableDescriptor(
            key = "name",
            labelAr = "اسم العميل",
            sampleValue = "أحمد عبد الرحمن",
            description = "اسم صاحب الحساب أو طالب الخدمة",
            generatorType = VariableGeneratorType.NONE
        ),
        VariableDescriptor(
            key = "tech_name",
            labelAr = "اسم الفني",
            sampleValue = "م/ محمود العزب",
            description = "الفني المسؤول عن تنفيذ طلب الصيانة",
            generatorType = VariableGeneratorType.NONE
        ),
        VariableDescriptor(
            key = "service",
            labelAr = "نوع الخدمة",
            sampleValue = "صيانة تكييفات مركزية",
            description = "وصف الخدمة أو الصيانة المطلوبة",
            generatorType = VariableGeneratorType.NONE
        ),
        VariableDescriptor(
            key = "amount",
            labelAr = "المبلغ المالي",
            sampleValue = "500 ج.م",
            description = "قيمة الدفعة أو الفاتورة المسددة",
            generatorType = VariableGeneratorType.NONE
        ),
        VariableDescriptor(
            key = "date",
            labelAr = "التاريخ والوقت",
            sampleValue = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date()),
            description = "موعد الزيارة أو توقيت العملية",
            generatorType = VariableGeneratorType.CURRENT_TIMESTAMP
        ),
        VariableDescriptor(
            key = "link",
            labelAr = "الرابط المختصر",
            sampleValue = "https://alazab.eg/p/track",
            description = "رابط إتمام التعيين أو تقييم الخدمة",
            generatorType = VariableGeneratorType.NONE
        )
    )

    /**
     * Map of Arabic alias keys to standardized keys.
     */
    private val arabicAliases = mapOf(
        "رقم_الهاتف" to "phone",
        "الهاتف" to "phone",
        "الموبايل" to "phone",
        "رمز_التحقق" to "otp",
        "كود_التحقق" to "otp",
        "الرمز" to "otp",
        "رقم_الطلب" to "order_id",
        "الطلب" to "order_id",
        "الكود" to "order_id",
        "الاسم" to "name",
        "اسم_العميل" to "name",
        "اسم_الفني" to "tech_name",
        "الفني" to "tech_name",
        "الخدمة" to "service",
        "المبلغ" to "amount",
        "القيمة" to "amount",
        "التاريخ" to "date",
        "الموعد" to "date",
        "الرابط" to "link"
    )

    /**
     * Extracts all variable keys inside `{key}` placeholders.
     */
    fun extractVariables(templateBody: String): List<String> {
        val variables = mutableListOf<String>()
        val matcher = placeholderPattern.matcher(templateBody)
        while (matcher.find()) {
            val rawKey = matcher.group(1)?.trim() ?: continue
            val normalizedKey = normalizeVariableKey(rawKey)
            if (!variables.contains(normalizedKey)) {
                variables.add(normalizedKey)
            }
        }
        return variables
    }

    /**
     * Normalizes a key (trim, lowercase, resolve Arabic aliases).
     */
    fun normalizeVariableKey(key: String): String {
        val trimmed = key.trim().lowercase(Locale.ROOT)
        return arabicAliases[trimmed] ?: trimmed
    }

    /**
     * Renders a template body with provided variable values.
     * Leaves missing values with their variable placeholder or fallback label.
     */
    fun renderMessage(templateBody: String, values: Map<String, String>): String {
        val matcher = placeholderPattern.matcher(templateBody)
        val sb = StringBuffer()

        // Normalize keys in values map
        val normalizedValues = mutableMapOf<String, String>()
        for ((k, v) in values) {
            normalizedValues[normalizeVariableKey(k)] = v
        }

        while (matcher.find()) {
            val rawKey = matcher.group(1)?.trim() ?: ""
            val normalizedKey = normalizeVariableKey(rawKey)
            val replacement = normalizedValues[normalizedKey]
            val safeReplacement = if (!replacement.isNullOrBlank()) {
                replacement
            } else {
                // If not provided, fallback to sample value or placeholder
                val descriptor = STANDARD_VARIABLES.find { it.key == normalizedKey }
                descriptor?.sampleValue ?: "{$rawKey}"
            }
            // Use Matcher.quoteReplacement to avoid backslash issues in replacement strings
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(safeReplacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    /**
     * Generates a secure random numeric OTP.
     */
    fun generateRandomOtp(digits: Int = 6): String {
        val min = if (digits == 4) 1000 else 100000
        val max = if (digits == 4) 9999 else 999999
        val value = min + random.nextInt(max - min + 1)
        return value.toString()
    }

    /**
     * Generates a realistic Enterprise Order ID.
     */
    fun generateRandomOrderId(prefix: String = "AZ"): String {
        val num = 1000 + random.nextInt(9000)
        return "$prefix-$num"
    }

    /**
     * Returns current formatted date/time.
     */
    fun getCurrentTimestampFormatted(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date())
    }

    /**
     * Factory list of pre-configured Enterprise templates for Alazab Enterprise.
     */
    fun getDefaultEnterpriseTemplates(): List<SmsTemplate> {
        val list = listOf(
            // 1. Login OTP
            SmsTemplate(
                id = "tpl_auth_otp_login",
                title = "رمز التحقق (OTP) لتسجيل الدخول",
                category = SmsTemplateCategory.AUTH_OTP,
                rawBody = "مؤسسة العزب: رمز التحقق السريع لتسجيل الدخول هو: {otp}. الرمز صالح لمدة 5 دقائق. لا تشاركه مع أي شخص حرصاً على سرية وأمان حسابك.",
                entityIdDefault = "AUTH_OTP",
                isBuiltIn = true
            ),

            // 2. Forgot / Reset Password OTP
            SmsTemplate(
                id = "tpl_password_reset_otp",
                title = "استعادة / نسيان كلمة المرور",
                category = SmsTemplateCategory.PASSWORD_RESET,
                rawBody = "مؤسسة العزب: تم طلب إعادة تعيين كلمة المرور لرقم {phone}. رمز التأكيد هو: {otp}. لإكمال التعيين فوراً: {link}. إذا لم تكن أنت، يرجى تجاهل هذه الرسالة.",
                entityIdDefault = "PWD_RESET",
                isBuiltIn = true
            ),

            // 3. Password Changed Confirmation
            SmsTemplate(
                id = "tpl_password_changed_confirm",
                title = "تأكيد تغيير كلمة المرور بنجاح",
                category = SmsTemplateCategory.PASSWORD_RESET,
                rawBody = "مؤسسة العزب: تم تحديث كلمة المرور لحسابكم بنجاح بتاريخ {date}. إذا لم تقم بهذا الإجراء، يرجى التواصل فوراً مع الخط الرسمي +201004006620.",
                entityIdDefault = "PWD_UPDATED",
                isBuiltIn = true
            ),

            // 4. New Maintenance Request Confirmation
            SmsTemplate(
                id = "tpl_maintenance_received",
                title = "استلام وتأكيد طلب صيانة جديد",
                category = SmsTemplateCategory.MAINTENANCE,
                rawBody = "مؤسسة العزب للصيانة: تم استلام طلبكم رقم #{order_id} لخدمة ({service}) بنجاح. سيتم تعيين الفني والتواصل معكم هاتفياً في أقرب وقت. شكراً لثقتكم بنا.",
                entityIdDefault = "MAINT_CONFIRM",
                isBuiltIn = true
            ),

            // 5. Technician Dispatched
            SmsTemplate(
                id = "tpl_technician_dispatched",
                title = "تعيين الفني وتفاصيل موعد الزيارة",
                category = SmsTemplateCategory.MAINTENANCE,
                rawBody = "مؤسسة العزب: تم تعيين الفني المختص ({tech_name}) لمتابعة طلب الصيانة #{order_id}. الفني في طريقه إليكم الآن أو سيتواصل معكم هاتفياً خلال لحظات.",
                entityIdDefault = "TECH_DISPATCH",
                isBuiltIn = true
            ),

            // 6. Payment Receipt / Vodafone Cash / InstaPay
            SmsTemplate(
                id = "tpl_payment_received_receipt",
                title = "تأكيد استلام دفعة مالية / إيصال سداد",
                category = SmsTemplateCategory.PAYMENTS,
                rawBody = "مؤسسة العزب: تم استلام دفعة مالية بقيمة {amount} بنجاح للطلب #{order_id} عبر المحفظة الإلكترونية/إنستاباي. نشكركم لثقتكم ويسعدنا دائماً خدمتكم.",
                entityIdDefault = "PAYMENT_RECEIPT",
                isBuiltIn = true
            ),

            // 7. Order Completed & Customer Feedback
            SmsTemplate(
                id = "tpl_order_completed_review",
                title = "إغلاق طلب الصيانة وتقييم الخدمة",
                category = SmsTemplateCategory.CUSTOMERS,
                rawBody = "مؤسسة العزب: عزيزنا العميل {name}، تم إتمام طلب الصيانة #{order_id} بنجاح. نرجو التكرم بمشاركتنا تقييمكم لجودة الخدمة والفني عبر الرابط: {link}",
                entityIdDefault = "ORDER_COMPLETED",
                isBuiltIn = true
            ),

            // 8. Urgent Periodic Maintenance Reminder
            SmsTemplate(
                id = "tpl_urgent_maintenance_reminder",
                title = "تنبيه عاجل / موعد صيانة دورية",
                category = SmsTemplateCategory.ALERTS,
                rawBody = "مؤسسة العزب: تذكير بموعد الصيانة الدورية المعتمد للطلب #{order_id} بتاريخ {date}. يرجى تأكيد تواجدكم أو طلب موعد بديل بالرد على هذه الرسالة.",
                entityIdDefault = "URGENT_ALERT",
                isBuiltIn = true
            )
        )

        return list.map { it.copy(variables = extractVariables(it.rawBody)) }
    }
}
