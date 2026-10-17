# Forough (فروغ) 2.0.2 — Android

**فروغ؛ پنجره‌ای روشن به اینترنت آزاد** · Forough — a bright window to the open internet

## What's new in 2.0.2

**فارسی:**
- **دکمهٔ بازگشت بین صفحه‌ها**: حالا همهٔ صفحه‌ها (اسکنر، تنظیمات، لاگ‌ها، درباره) دکمهٔ بازگشت در نوار بالا دارند و دکمهٔ فیزیکی/ژست بازگشت اندروید هم به صفحهٔ اصلی برمی‌گرداند.
- **رفع قطع‌نشدن اتصال**: بعد از اتصال موفق، دکمهٔ قطع (در برنامه و نوتیفیکیشن) درست کار می‌کند؛ سرویس دیگر بعد از قطع، خودش دوباره وصل نمی‌شود و کرش خاموشی ناگهانی سرویس هم رفع شد.
- **نمایش زندهٔ ترافیک**: نمودار ترافیک، سرعت لحظه‌ای بالا/پایین و مجموع ترافیک حالا واقعاً به‌روز می‌شوند؛ ردیف ترافیک هم مجموع و هم سرعت زنده را نشان می‌دهد و IP عمومی بعد از اتصال پر می‌شود.
- **حالت پیش‌فرض CDN**: حالت پیش‌فرض اتصال روی «CDN Fronting» تنظیم شد (سریع‌ترین حالت اندازه‌گیری‌شده) — تنظیمات قبلی یک‌بار به این حالت منتقل می‌شوند.
- **رابط کاربری روان‌تر**: انتقال انیمیشنی بین صفحه‌ها، رنگ و نبض نرم دکمهٔ اتصال هنگام برقراری، و محو شدن وضعیت‌ها — تم تیرهٔ libadwaita مثل قبل حفظ شده.
- **دکمه‌های تست سرعت و عیب‌یابی** حالا هنگام اتصال واقعاً فعال‌اند و وضعیت beast mode / اسکن CDN در پنل آمار زنده می‌شود.

**English:**
- **Back navigation between pages**: every sub-screen (scanner, settings, logs, about) now has a back arrow in the header, and the system back gesture/button returns from any screen to the main panel.
- **Fixed "cannot disconnect"**: the disconnect button (in-app and in the notification) now reliably stops the tunnel; the service no longer auto-reconnects after an explicit disconnect, and the silent foreground-service timeout crash on disconnect is fixed.
- **Live traffic display**: the traffic graph, live up/down rates, totals and public IP now actually update — the traffic row shows both totals and the live rate.
- **CDN as the default mode**: the factory-default protocol mode is now CDN Fronting (the measured-speed mode); previously stored settings migrate once.
- **Smoother UI**: animated screen transitions, soft connect-button color/pulse while establishing, cross-fading status labels — same libadwaita dark theme.
- **Speed-test / diagnostics header buttons** are now truly active while connected, and the beast-mode / CDN-scan status rows are live in the stats panel.

---

The **native Forough Android client** — an exact port of the Forough Linux desktop client:

- Same engine: the Forough psiphon-tunnel-core fork (quality-ranked CDN edges, beast mode)
- Same features: CDN edge scanner, speed test, live traffic graph, egress region + protocol selection, CDN fronting overrides (custom IP/SNI), OSL lists, in-proxy (conduit) with personal compartment, diagnostics report
- Same theme: the libadwaita-style dark look of the desktop client, full Persian RTL with English
- Whole-device VPN via Android VpnService (tun2socks)

## نصب / Install (فارسی)

1. فایل APK را دانلود و نصب کنید (اجازهٔ «نصب از منابع ناشناس» را بدهید). نسخه‌های قبلی مستقیم ارتقا می‌یابند (امضای یکسان).
2. اولین بار اتصال VPN را تأیید کنید.
3. دکمهٔ اتصال را بزنید — دقیقاً مثل نسخهٔ لینوکس.

## Notes

- Signed with the Forough community distribution key (stable across releases) — install directly over 2.0.1/2.0.0.
- Unofficial project, not affiliated with Psiphon Inc. Built on the open-source Psiphon network.
- Sources: https://github.com/nayrasolcr-arch/forough-android/tree/master/native
