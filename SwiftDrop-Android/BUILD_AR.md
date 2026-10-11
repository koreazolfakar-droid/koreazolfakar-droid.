# كيف تشغّل SwiftDrop أول نسخة

**الحالة:** هذا مشروع مصدر (Source code) وليس APK جاهز. لم يُبنَ ببيئة Android SDK بعد.

## أسرع طريقة للحصول على APK باستخدام GitHub Actions
1. افتح GitHub وأنشئ مستودعاً جديداً باسم `SwiftDrop-Android`، مستقلاً عن مشروع Generals وSpace Call.
2. ارفع محتويات مجلد المشروع من ملف ZIP، مع إظهار المجلد المخفي `.github`.
3. افتح `Actions` ثم شغّل workflow باسم `Android APK / tests` (يمكنه التشغيل تلقائياً عند push أيضاً).
4. إذا نجح Build والاختبارات، تجد ملف `swiftdrop-alpha-debug-apk` في Artifacts داخل التشغيل؛ نزّل الملف وثبّت `app-debug.apk` على هاتفين Android.
5. هاتف المستقبل: Receive. هاتف المرسل: Send > اختَر ملفات > الجهاز الآخر. طابق رمز الأمان على الهاتفين ثم اقبل.
6. الملفات المستقبلة تظهر في Downloads/SwiftDrop على Android 10 وأحدث.

## جهاز الكمبيوتر
افتح المجلد بواسطة Android Studio مع Android SDK 36 وJDK 17 وGradle 8.13.

## ملاحظات مهمة
- لا يوجد QR Scanner ولا استكمال بعد انقطاع النقل ولا ضمان Wi-Fi 7 في هذه النسخة.
- لا تنشره للمستخدمين قبل نجاح البناء واختبار النقل الحقيقي بين هاتفين.
- إذا كان جهازك بدون Google Play Services فلن يعمل نقل Nearby حتى نضيف بديل Wi-Fi Direct.
- النسخة مرتبطة بواجهة التطبيق؛ لا تدوّر الشاشة أثناء نقل ملف كبير في النسخة الحالية.

## أمر البناء
بعد تثبيت Android SDK 36 وGradle 8.13 وJDK 17:
- على Windows: شغّل `build-apk.bat`
- على Linux/macOS: شغّل `./build-apk.sh`
- الملف الناتج بعد نجاح البناء: `app/build/outputs/apk/debug/app-debug.apk`

ملاحظة: هذه السكربتات لا تحتوي على APK جاهز؛ يجب تنفيذ البناء بالأدوات المذكورة.
