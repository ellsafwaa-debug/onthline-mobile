# ONTHLINE — نسخة الموبايل (Android)

المشروع ده بيحوّل نفس تطبيق ONTHLINE (اللي شغال أصلاً كصفحة ويب واحدة، ونسخة
منه بقت برنامج ديسك توب) لتطبيق أندرويد حقيقي، باستخدام **Capacitor**.

## محتويات المشروع

```
onthline-mobile/
├── www/
│   └── index.html          ← نفس ملف التطبيق اللي رفعته، مع 3 إضافات:
│                              1) getGeoPosition() بيستخدم GPS حقيقي على أندرويد
│                              2) getMockLocationFlag() بيكشف فعليًا تطبيقات
│                                 تزييف الموقع (Fake GPS)
│                              3) AppUpdate: فحص تحديثات عن طريق GitHub Releases
├── android/                 ← المشروع الأصلي (Java/Gradle) اللي Capacitor ولّده
│   └── app/src/main/java/com/onthline/app/
│       ├── MainActivity.java
│       └── OnthlineLocationPlugin.java   ← بلجن مخصص لفحص GPS + Mock Location
├── capacitor.config.ts
├── package.json
└── .github/workflows/build.yml   ← بناء APK تلقائي عند كل push لـ main
```

## إزاي الموقع بقى دقيق على الموبايل؟

بلجن Android مخصص (`OnthlineLocationPlugin.java`) بيوصل لـ `LocationManager`
بتاع أندرويد مباشرة (GPS الحقيقي)، وبيرجّع في نفس الوقت `isMock` — علم
بيوضّح هل القراءة دي جايه من تطبيق "تزييف موقع" (Fake GPS) ولا لأ، عن طريق
`Location.isFromMockProvider()`. الاتنين بيجوا من نفس الـ fix بالظبط، فمفيش
احتمال تضارب بينهم.

## نظام التحديثات (بدل Google Play)

بما إن التوزيع هيكون APK مباشر (مش من المتجر)، مفيش تحديث تلقائي جاهز. الحل:
عند فتح التطبيق، بيقارن رقم نسخته الحالية بآخر إصدار منشور على GitHub
Releases بتاع المستودع (`AppUpdate.REPO` في index.html) — لو فيه أحدث، بيسأل
المستخدم يفتح صفحة التحميل في المتصفح ويثبّت النسخة الجديدة يدوي (تثبيت APK
مباشر مبيحصلش صامت، ده قيد من أندرويد نفسه لحماية المستخدم، مش تقصير في الكود).

## إزاي تبني نسخة جديدة

1. غيّر رقم النسخة في `android/app/build.gradle` (`versionCode` و `versionName`)
2. اعمل `git commit` و `git push` لـ `main`
3. GitHub Actions هيبني APK تلقائي وينشره في صفحة Releases بتاع المستودع
4. أي تطبيق مثبّت قديم هيلاقي التحديث تلقائيًا في المرة الجاية اللي يتفتح فيها

## التطوير محليًا (اختياري، لو حبيت تجرب قبل الـ push)

يحتاج Android Studio مثبّت على جهازك:

```bash
npm install
npx cap sync android
npx cap open android   # بيفتح المشروع في Android Studio
```

## الخطوة الجاية: iOS

المشروع مجهّز بحيث لو حبيت تضيف دعم iOS، الخطوة هتكون `npx cap add ios`
(محتاجة Mac + Xcode للبناء)، وهتحتاج كمان Apple Developer Program (99$/سنة)
عشان توزّع التطبيق حتى داخليًا بس.
