# دليل بناء وتثبيت تطبيق مؤسسة العزب (APK & AAB Build Guide)

تم إضافة اسكربت تشغيل مرن وشامل في المجلد الرئيسي للمشروع:
- `build.sh` أو `build-apk-aab.sh`

---

## 🚀 كيفية استخدام الاسكربت (Quick Usage)

يمكنك فتح التيرمينال داخل المشروع وتشغيل أي من الأوامر التالية:

### 1. بناء ملفات APK و AAB معاً (الوضع الافتراضي):
```bash
./build.sh
# أو
./build-apk-aab.sh
```

### 2. بناء ملف APK فقط (لتثبيته مباشرة على الهاتف):
```bash
./build.sh apk
```

### 3. بناء ملف AAB فقط (لنشر التطبيق على Google Play Console):
```bash
./build.sh aab
```

### 4. بناء نسخ الإنتاج (Release):
```bash
./build.sh release
```

### 5. تنظيف ملفات البناء المؤقتة:
```bash
./build.sh clean
```

---

## 📁 أين توجد الملفات بعد البناء؟

يقوم الاسكربت بنسخ وتنظيم الملفات في مجلد `dist/` مع تسمية واضحة:
- **ملف التثبيت على الهاتف:** `dist/AzabAssistant-v1.0-debug.apk`
- **حزمة متجر جوجل بلاي:** `dist/AzabAssistant-v1.0-debug.aab`

كما تتوفر أيضاً في مسارات Gradle القياسية:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/bundle/debug/app-debug.aab`

---

## 📱 كيفية تثبيت التطبيق على الهاتف:

### الطريقة الأولى (عبر كابل USB و ADB):
إذا كان الهاتف متصلاً بجهازك ومفعّل به وضع تصحيح أخطاء USB (USB Debugging):
```bash
adb install -r dist/AzabAssistant-v1.0-debug.apk
```

### الطريقة الثانية (نقل الملف مباشرة):
1. قم بنسخ ملف `AzabAssistant-v1.0-debug.apk` إلى ذاكرة الهاتف (عبر البلوتوث، WhatsApp، أو كابل USB).
2. افتح مدير الملفات على هاتفك واضغط على ملف الـ APK.
3. وافق على السماح بتثبيت التطبيقات من مصادر غير معروفة (Install unknown apps).
4. اضغط **تثبيت (Install)**.
