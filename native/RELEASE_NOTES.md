# Forough (فروغ) 2.1.0 — Android

**فروغ؛ پنجره‌ای روشن به اینترنت آزاد** · Forough — a bright window to the open internet

## What's new in 2.1.0 — the "fluid + powerful" release

**فارسی:**
- **انیمیشن واقعی و روان**: حلقهٔ وضعیت زنده دور دکمهٔ اتصال (کمان چرخان هنگام اتصال، هالهٔ سبز آرام وقتی متصل است)، فیزیک فنری روی همهٔ دکمه‌ها، شمارنده‌های سرعت با انیمیشن جابه‌جایی رقم‌ها، ورود پلکانی اجزا در هر صفحه و گذار اسلاید+زوم بین صفحه‌ها.
- **قطع اتصال فوری — دکمه دیگر گیر نمی‌کند**: علت اصلی «گیر کردن» پیدا و رفع شد: teardown سنگین (خاموش‌کردن tun2socks و موتور) روی ترد اصلی اجرا می‌شد و رابط را چند ثانیه قفل می‌کرد. حالا با اولین لمسِ قطع، رابط بلافاصله آزاد می‌شود و خاموش‌سازی در پس‌زمینه انجام می‌گیرد.
- **نمایش وضعیت کامل هنگام اتصال**: کپسول‌های سرعت زندهٔ دانلود/آپلود، نمودار ترافیک نرم‌تر با نمونه‌برداری دو برابر سریع‌تر، نوتیفیکیشن با کرنومتر زمان اتصال + سرعت لحظه‌ای + منطقه/پروتکل، و پیام تأیید «متصل شد» با لرزش.
- **قابلیت‌های تقویتی جدید**:
  - **تقسیم تونل (Split Tunneling)**: انتخاب برنامه‌هایی که از VPN عبور می‌کنند یا از آن خارج می‌مانند — با جستجو، آیکون و انتخاب چندتایی.
  - **کاشی Quick Settings**: وصل/قطع مستقیم از سایه‌نوار اندروید بدون باز کردن برنامه.
  - **اتصال خودکار هنگام باز شدن برنامه**.
  - **استثنا از بهینه‌سازی باتری**: یک‌ضرب از داخل تنظیمات (برای شیائومی/سامسونگ/هوآوی).
  - **میان‌بر Always-on VPN و کلید قطعی (Kill Switch)**: باز کردن مستقیم تنظیمات VPN اندروید.
  - **بازخورد لرزشی** روی اتصال/قطع/دکمه‌ها (قابل خاموش‌کردن).
- **تایپوگرافی جدید**: فونت وزیرمتن برای فارسی و لاتین — متن فارسی بالاخره درست و خوش‌خوان نمایش داده می‌شود.
- **پاسخ فوری تست سرعت**: نتیجهٔ تست سرعت حالا مستقیم به‌شکل پیام نمایش داده می‌شود (علاوه بر لاگ و عیب‌یابی).
- همهٔ دکمه‌ها بازبینی شدند و همه با بازخورد (لرزش/پیام/انیمیشن) کار می‌کنند: اسکنر، تست سرعت، عیب‌یابی، لاگ‌ها، تنظیمات، انتخاب برنامه‌ها و منوی بیشتر.

**English:**
- **Real, fluid motion**: a live status ring around the power button (rotating arc while connecting, calm green glow when connected), spring physics on every button, odometer-style animated speed digits, staggered component entrances and slide+zoom screen transitions.
- **Instant disconnect — the button never sticks again**: root cause fixed — the heavy teardown (tun2socks + engine shutdown) used to run on the main thread and froze the UI for seconds. The UI now unlocks on the very first tap while the teardown finishes in the background.
- **Full connected feedback**: live down/up speed capsules, a smoother traffic graph at double the sampling rate, a notification with an uptime chronometer + live speeds + region/protocol, and a haptic "connected" greeting.
- **New power features**: per-app **split tunneling** with a searchable app picker, a **Quick Settings tile**, **auto-connect on launch**, a **battery-optimization exemption** helper, an **Always-on VPN / kill switch** shortcut, and optional **haptic feedback**.
- **New typography**: Vazirmatn for Persian + Latin — Persian text finally renders properly and beautifully.
- **Speed test answers in the UI**: the result now surfaces as a toast in addition to the logs/diagnostics.
- Every button was audited and now responds with haptics/feedback: scanner, speed test, diagnostics, logs, settings, app picker and the overflow menu.

---

The **native Forough Android client** — an exact port of the Forough Linux desktop client:

- Same engine: the Forough psiphon-tunnel-core fork (quality-ranked CDN edges, beast mode)
- Same features: CDN edge scanner, speed test, live traffic graph, egress region + protocol selection, CDN fronting overrides (custom IP/SNI), OSL lists, in-proxy (conduit) with personal compartment, diagnostics report
- Same theme: the libadwaita-style dark look of the desktop client, full Persian RTL with English
- Whole-device VPN via Android VpnService (tun2socks)

## نصب / Install (فارسی)

1. فایل APK را دانلود و نصب کنید (اجازهٔ «نصب از منابع ناشناس» را بدهید). نسخه‌های قبلی مستقیم ارتقا می‌یابند (امضای یکسان).
2. اولین بار اتصال VPN را تأیید کنید.
3. دکمهٔ اتصال را بزنید — دقیقاً مثل نسخهٔ لینوکس. برای راحتی بیشتر، کاشی فروغ را به Quick Settings اضافه کنید.

## Notes

- Signed with the Forough community distribution key (stable across releases) — install directly over 2.0.2/2.0.1/2.0.0.
- The engine remains the Forough psiphon-tunnel-core fork; the CDN Fronting mode stays the factory default for the fastest measured connections.
