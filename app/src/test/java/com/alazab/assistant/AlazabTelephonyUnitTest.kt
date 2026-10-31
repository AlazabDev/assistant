package com.alazab.assistant

import com.alazab.assistant.data.CapturedEvent
import org.junit.Assert.*
import org.junit.Test

class AlazabTelephonyUnitTest {

    @Test
    fun verifyOfficialEnterpriseNumberConstants() {
        assertEquals("+201004006620", AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE)
        assertEquals("01004006620", AlazabEnterpriseConfig.OFFICIAL_ENTERPRISE_PHONE_LOCAL)
        assertEquals("مؤسسة العزب", AlazabEnterpriseConfig.ENTERPRISE_NAME)
    }

    @Test
    fun testEgyptianPhoneNumberNormalization() {
        // Standard Egyptian local mobile
        assertEquals("+201004006620", AlazabEnterpriseConfig.normalizePhoneNumber("01004006620"))
        
        // International without plus
        assertEquals("+201004006620", AlazabEnterpriseConfig.normalizePhoneNumber("201004006620"))
        
        // Double zero prefix
        assertEquals("+201004006620", AlazabEnterpriseConfig.normalizePhoneNumber("00201004006620"))
        
        // Number with spaces, dashes, or brackets
        assertEquals("+201004006620", AlazabEnterpriseConfig.normalizePhoneNumber("0100 400 6620"))
        assertEquals("+201004006620", AlazabEnterpriseConfig.normalizePhoneNumber("+20 (100) 400-6620"))

        // Eastern Arabic numerals (٠١٠٠٤٠٠٦٦٢٠)
        assertEquals("+201004006620", AlazabEnterpriseConfig.normalizePhoneNumber("٠١٠٠٤٠٠٦٦٢٠"))
        assertEquals("+201004006620", AlazabEnterpriseConfig.normalizePhoneNumber("+٢٠١٠٠٤٠٠٦٦٢٠"))
    }

    @Test
    fun testIsEnterprisePhoneDetection() {
        assertTrue(AlazabEnterpriseConfig.isEnterprisePhone("+201004006620"))
        assertTrue(AlazabEnterpriseConfig.isEnterprisePhone("01004006620"))
        assertTrue(AlazabEnterpriseConfig.isEnterprisePhone("201004006620"))
        assertTrue(AlazabEnterpriseConfig.isEnterprisePhone("00201004006620"))
        assertTrue(AlazabEnterpriseConfig.isEnterprisePhone("٠١٠٠٤٠٠٦٦٢٠"))

        // Different number should be false
        assertFalse(AlazabEnterpriseConfig.isEnterprisePhone("01112223334"))
        assertFalse(AlazabEnterpriseConfig.isEnterprisePhone("+201200000000"))
        assertFalse(AlazabEnterpriseConfig.isEnterprisePhone(null))
        assertFalse(AlazabEnterpriseConfig.isEnterprisePhone(""))
    }

    @Test
    fun testArabicSmsEncodingAndMultipartBreakdown() {
        // Short Arabic message (<= 70 chars) = 1 part
        val shortArabic = "تم استلام دفعة الصيانة بنجاح"
        val shortInfo = AlazabEnterpriseConfig.analyzeSmsEncoding(shortArabic)
        assertTrue(shortInfo.isUnicodeArabic)
        assertEquals(1, shortInfo.partCount)
        assertEquals(70, shortInfo.maxCharsPerSingleSms)
        assertTrue(shortInfo.charactersRemainingInCurrentPart >= 0)

        // Long Arabic message (> 70 chars) = 2 or more parts (67 chars per part)
        val longArabic = "مرحباً بك في مؤسسة العزب، تم تأكيد سداد دفعة إيجار وصيانة المعدات الخاصة بك، كود العملية 123456789. شكراً لتعاملكم معنا."
        val longInfo = AlazabEnterpriseConfig.analyzeSmsEncoding(longArabic)
        assertTrue(longInfo.isUnicodeArabic)
        assertTrue("Long Arabic message should be multipart", longInfo.partCount >= 2)
    }

    @Test
    fun testEnglishGsm7SmsEncoding() {
        val englishText = "Hello from Alazab Enterprise. Your maintenance request #9988 is confirmed."
        val info = AlazabEnterpriseConfig.analyzeSmsEncoding(englishText)
        assertFalse(info.isUnicodeArabic)
        assertEquals(1, info.partCount)
        assertEquals(160, info.maxCharsPerSingleSms)
    }

    @Test
    fun testCapturedEventEnterpriseClassification() {
        // Message from official number
        val (entityId1, classification1) = CapturedEvent.parseSmsAndClassify(
            body = "تأكيد صيانة مؤسسة العزب [Ref: MAINT-8802 | MAINTENANCE]",
            sender = "+201004006620"
        )
        assertEquals("ENTERPRISE_OFFICIAL", classification1)
        assertEquals("MAINT", entityId1)

        // Message mentioning official number in body
        val (entityId2, classification2) = CapturedEvent.parseSmsAndClassify(
            body = "يرجى التواصل مع الرقم الرسمي لمؤسسة العزب +201004006620 كود 5543",
            sender = "Customer"
        )
        assertEquals("ENTERPRISE_OFFICIAL", classification2)
    }

    @Test
    fun testGatewayStateHierarchyAndLabels() {
        val ready = GatewayState.Ready
        assertEquals("READY", ready.code)
        assertEquals("Ready", ready.titleEn)
        assertTrue(ready.descriptionAr.contains("+201004006620"))

        val sending = GatewayState.Sending(
            recipient = "+201004006620",
            taskId = "task_101",
            partCount = 2
        )
        assertEquals("SENDING", sending.code)
        assertEquals("Sending", sending.titleEn)
        assertEquals("+201004006620", sending.recipient)
        assertEquals(2, sending.partCount)
        assertTrue(sending.descriptionAr.contains("+201004006620"))

        val error = GatewayState.Error("Daily SMS limit exceeded")
        assertEquals("ERROR", error.code)
        assertEquals("Error", error.titleEn)
        assertEquals("Daily SMS limit exceeded", error.descriptionAr)

        val stopped = GatewayState.Stopped
        assertEquals("STOPPED", stopped.code)
        assertEquals("Disabled", stopped.titleEn)
    }

    @Test
    fun testGatewayConnectionTelemetryDefaults() {
        val telemetry = GatewayConnectionTelemetry()
        assertEquals("+201004006620", telemetry.officialPhone)
        assertTrue(telemetry.carrierName.contains("Vodafone"))
        assertEquals(GatewayState.Stopped, telemetry.state)
        assertEquals(0, telemetry.pendingQueueCount)
        assertEquals(0, telemetry.sentTodayCount)
        assertEquals(100, telemetry.dailyLimit)
        assertTrue(telemetry.isSimReady)
    }

    @Test
    fun testSmsTemplateEngineVariableExtraction() {
        val templateText = "مؤسسة العزب: رمز التحقق {otp} لرقم الهاتف {phone} طلب #{order_id}."
        val vars = com.alazab.assistant.templates.SmsTemplateEngine.extractVariables(templateText)
        assertTrue(vars.contains("otp"))
        assertTrue(vars.contains("phone"))
        assertTrue(vars.contains("order_id"))
        assertEquals(3, vars.size)

        // Test with Arabic placeholders
        val arabicText = "تأكيد الدخول: رمزك هو {رمز_التحقق} للرقم {رقم_الهاتف}"
        val arabicVars = com.alazab.assistant.templates.SmsTemplateEngine.extractVariables(arabicText)
        assertTrue(arabicVars.contains("otp"))
        assertTrue(arabicVars.contains("phone"))
    }

    @Test
    fun testSmsTemplateEngineRenderingAndArabicAliases() {
        val templateText = "مؤسسة العزب: تم طلب إعادة تعيين كلمة المرور لرقم {phone}. رمز التأكيد: {otp}. رابط: {link}"
        val values = mapOf(
            "phone" to "+201004006620",
            "otp" to "584920",
            "link" to "https://alazab.eg/reset"
        )
        val rendered = com.alazab.assistant.templates.SmsTemplateEngine.renderMessage(templateText, values)
        assertTrue(rendered.contains("+201004006620"))
        assertTrue(rendered.contains("584920"))
        assertTrue(rendered.contains("https://alazab.eg/reset"))
        assertFalse(rendered.contains("{phone}"))
        assertFalse(rendered.contains("{otp}"))

        // Test with Arabic keys in values
        val renderedWithArabicKeys = com.alazab.assistant.templates.SmsTemplateEngine.renderMessage(
            templateText,
            mapOf(
                "رقم_الهاتف" to "01004006620",
                "رمز_التحقق" to "123456",
                "الرابط" to "https://alazab.eg/auth"
            )
        )
        assertTrue(renderedWithArabicKeys.contains("01004006620"))
        assertTrue(renderedWithArabicKeys.contains("123456"))
        assertTrue(renderedWithArabicKeys.contains("https://alazab.eg/auth"))
    }

    @Test
    fun testSmsTemplateEngineGenerators() {
        val otp6 = com.alazab.assistant.templates.SmsTemplateEngine.generateRandomOtp(6)
        assertEquals(6, otp6.length)
        assertTrue(otp6.all { it.isDigit() })

        val otp4 = com.alazab.assistant.templates.SmsTemplateEngine.generateRandomOtp(4)
        assertEquals(4, otp4.length)
        assertTrue(otp4.all { it.isDigit() })

        val orderId = com.alazab.assistant.templates.SmsTemplateEngine.generateRandomOrderId("AZ")
        assertTrue(orderId.startsWith("AZ-"))
        assertTrue(orderId.length >= 6)
    }

    @Test
    fun testDefaultEnterpriseTemplatesCompleteness() {
        val templates = com.alazab.assistant.templates.SmsTemplateEngine.getDefaultEnterpriseTemplates()
        assertTrue("Must have at least 6 enterprise templates", templates.size >= 6)

        val otpTemplate = templates.find { it.category == com.alazab.assistant.templates.SmsTemplateCategory.AUTH_OTP }
        assertNotNull(otpTemplate)
        assertTrue(otpTemplate!!.variables.contains("otp"))

        val resetTemplate = templates.find { it.id == "tpl_password_reset_otp" }
        assertNotNull(resetTemplate)
        assertTrue(resetTemplate!!.variables.contains("phone"))
        assertTrue(resetTemplate.variables.contains("otp"))

        val maintTemplate = templates.find { it.category == com.alazab.assistant.templates.SmsTemplateCategory.MAINTENANCE }
        assertNotNull(maintTemplate)
        assertTrue(maintTemplate!!.variables.contains("order_id"))
    }
}


