# Call Assistant (مساعد المكالمات)

تطبيق Android (Kotlin + Jetpack Compose) بيلخّص تسجيلات المكالمات بـ Gemini،
ويحفظ اسم المتصل، ويضيف المواعيد للتقويم مع تذكير، ويعرض نافذة بآخر ملخص لما نفس الرقم يتصل.

## البناء
- GitHub Actions: أي push بيبني `app-debug.apk` ويرفعه كـ artifact باسم `CallAssistant-APK`.
- محلياً: JDK 17 و Gradle 8.7 ثم `gradle assembleDebug`.

## الإعداد
مفتاح Gemini من https://aistudio.google.com ويتحط من شاشة الإعدادات في التطبيق، وبعدها الأذونات.
