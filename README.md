<div align="center">

<img src="docs/banner.png" alt="Forough — فروغ" width="720" />

# فروغ | Forough

**فارسی** · [English](#english)

«فروغ؛ پنجره‌ای روشن به اینترنت آزاد»

</div>

## فارسی

### فروغ چیست؟

فروغ یک کلاینت وی‌پی‌‌ان اندرویدِ سریع و نگهداری‌شده توسط جامعه است که بر پایه کلاینت متن‌باز [سایفون (Psiphon)](https://github.com/Psiphon-Inc/psiphon-android) ساخته شده است. فروغ رابط کاربری فارسی‌محور دارد و کنترل‌های بیشتری برای اتصال در اختیار شما می‌گذارد: انتخاب ناحیه (کشور خروجی)، انتخاب پروتکل، تنظیمات CDN Fronting با اسکنر داخلی، حالت Beast، اشتراک‌گذاری پروکسی در شبکه محلی و حالت مخفی‌سازی ظاهر برنامه.

نمایش صادقانه: موتور اتصال فروغ همان شبکه و زیرساخت Psiphon است؛ بنابراین ارجاع‌های «Psiphon» در برنامه و مستندات به آن زیرساخت اشاره دارند و حفظ شده‌اند.

### امکانات

- **رابط کاربری فارسی** به‌همراه پشتیبانی از چند زبان
- **انتخاب ناحیه خروجی** (کشور پروکسی) با فهرست پرچم‌ها
- **انتخاب پروتکل** و حالت‌های اتصال مختلف (OSSH/VPN و حالت‌های CDN-fronted)
- **حالت Beast** برای اسکن تهاجمی‌تر لبه‌های CDN و یافتن مسیر اتصال
- **تنظیمات CDN Fronting**: فهرست سفارشی IP و SNI، نمایش مسیر اسکن CDN در گزارش‌ها
- **حالت Conduit**: خودکار / اختصاصی / عمومی با زمان‌بیک قابل تنظیم
- **اشتراک‌گذاری پروکسی در شبکه محلی** (HTTP و SOCKS5 با احراز هویت اختیاری) برای استفاده سایر دستگاه‌ها از تونل
- **حالت مخفی (Stealth)**: تغییر آیکون و نام برنامه در لانچر (ماشین‌حساب، هواشناسی، یادداشت‌ها، ساعت) و اعلان‌های متناسب با همان ظاهر
- **هشدار ترافیک ناامن (MalAware)** و مسدودسازی خودکار سایت‌های مخرب
- **NFC Bump** برای انتقال تنظیمات اتصال به دستگاه دیگر
- حذف صفحات حامی مالی، بدون تبلیغ
- بدون نیاز به حساب کاربری؛ رایگان و متن‌باز

### نصب

1. به بخش [Releases](https://github.com/nayrasolcr-arch/forough-android/releases) بروید.
2. آخرین فایل `Forough-<نسخه>.apk` را دانلود کنید.
3. فایل را باز کرده و نصب را تأیید کنید (برای نصب از خارج از فروشگاه، لازم است «نصب از منابع ناشناس» برای مرورگر یا فایل‌منیجر شما فعال باشد).

### ساخت از سورس (Android Studio / Gradle)

```bash
git clone https://github.com/nayrasolcr-arch/forough-android
cd forough-android
# حداقل: JDK 8+، Android SDK 35، NDK (برای tun2socks)
./gradlew assembleRelease
# خروجی: app/build/outputs/apk/release/Forough-release.apk
```

نکته: ساخت نهاییِ منتشرشده در CI به مقادیر مخفی (کلید امضا، فهرست سرورها و کلید MaxMind) نیاز دارد که فقط در مخزن اصلی سازمان موجود است؛ به همین دلیل ساخت لوکال برای توسعه و آزمایش است.

### سلب مسئولیت

این برنامه به **هیچ‌وجه** توسط Psiphon Inc. توسعه نیافته، تأیید یا حمایت نشده و وابسته به آن نیست.

برای نسخه رسمی سایفون به https://psiphon.ca مراجعه کنید.

این نرم‌افزار بدون هیچ‌گونه ضمانتی ارائه می‌شود؛ استفاده از آن بر عهده خود کاربر است.

### مجوز

منتشرشده تحت مجوز [GNU GPL v3.0](LICENSE) — همان مجوز کلاینت اندروید Psiphon.

### کلاینت‌های هم‌خانواده

- 🐧 لینوکس: [forough-linux](https://github.com/nayrasolcr-arch/forough-linux)
- 🪟 ویندوز: [forough-windows](https://github.com/nayrasolcr-arch/forough-windows)

---

<a id="english"></a>
## English

### What is Forough?

Forough is a fast, community-maintained Android VPN client built on the [Psiphon open-source client](https://github.com/Psiphon-Inc/psiphon-android). It ships a Persian-first UI and gives you extra connection controls: egress region selection, protocol selection, CDN fronting settings with a built-in scanner, Beast mode, LAN proxy sharing, and app-disguise stealth mode.

Honest attribution: Forough connects through the Psiphon network and infrastructure, so "Psiphon" references in the app and docs refer to that upstream network and are kept on purpose.

### Features

- **Persian-first UI** with multi-language support
- **Egress region selection** (proxy country) with flag picker
- **Protocol selection** and connection modes (OSSH/VPN and CDN-fronted variants)
- **Beast mode** for more aggressive CDN edge scanning
- **CDN fronting settings**: custom IP & SNI lists, CDN scan route shown in the logs
- **Conduit mode**: auto / dedicated / public with configurable timeout
- **LAN proxy sharing** (HTTP + SOCKS5 with optional authentication) so other devices can use your tunnel
- **Stealth mode**: change the app icon and name in the launcher (Calculator, Weather, Notes, Clock) with matching stealth notifications
- **MalAware unsafe-traffic alerts** with automatic malicious-site blocking
- **NFC Bump** to transfer connection info to another device
- No sponsor pages, no ads
- No account required; free and open source

### Install

1. Go to [Releases](https://github.com/nayrasolcr-arch/forough-android/releases).
2. Download the latest `Forough-<version>.apk`.
3. Open the APK and confirm the install (you may need to allow "install unknown apps" for your browser or file manager).

### Build from source (Android Studio / Gradle)

```bash
git clone https://github.com/nayrasolcr-arch/forough-android
cd forough-android
# Requires: JDK 8+, Android SDK 35, NDK (for tun2socks)
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/Forough-release.apk
```

Note: the published CI build needs repository secrets (signing keystore, server list, MaxMind key) that exist only on the upstream organization repo, so a local build is intended for development and testing.

### Disclaimer

This application is **NOT** developed, endorsed, sponsored, or affiliated with Psiphon Inc. in any way.

For the official Psiphon application, visit: https://psiphon.ca

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND. USE AT YOUR OWN RISK.

### License

Licensed under the [GNU General Public License v3.0](LICENSE) (GPL-3.0), the same license as the original Psiphon Android client.

### Sibling clients

- 🐧 Linux: [forough-linux](https://github.com/nayrasolcr-arch/forough-linux)
- 🪟 Windows: [forough-windows](https://github.com/nayrasolcr-arch/forough-windows)
