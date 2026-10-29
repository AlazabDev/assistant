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
}

