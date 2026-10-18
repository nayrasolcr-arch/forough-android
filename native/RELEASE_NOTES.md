# Forough (فروغ) 2.4.0 — Android

**فروغ؛ پنجره‌ای روشن به اینترنت آزاد** · Forough — a bright window to the open internet

## What's new in 2.4.0 — the "Rescue Engine + Extreme mode + vivid UI" release

**فارسی:**
- **موتور نجات (Rescue Engine)**: بر اساس لاگ واقعی شما (۱۷۰۱ تلاش اسکن CDN بی‌پاسخ، صفر تونل موفق، QUIC کاملاً مسدود)، حالت CDN حالا وقتی همهٔ فرونت‌ها بمیرند بی‌نهایت منتظر نمی‌ماند — موتور نجات به‌طور خودکار پله‌پله اوج می‌گیرد: (۱) قطع QUIC و رفتن به پروتکل‌های فقط-TCP، (۲) گسترش به کل مجموعه پروتکل‌ها (همهٔ میک‌ها + Shadowsocks + SSH)، (۳) حالت اکسترم با همهٔ ترنسپورت‌ها و آزادسازی منطقه. هر پله روی صفحه نمایش داده می‌شود و بعد از اولین اتصال موفق، موتور نجات آرام می‌گیرد.
- **حالت جدید «اکسترم» برای فیلترینگ شدید و قطعی**: همهٔ ترنسپورت‌ها هم‌زمان — میک در همهٔ حالت‌ها (CDN، بدون فرونت، HTTP)، QUIC، Shadowsocks، SSH/OSSH، اسکن CDN و کانالیوت (inproxy). در تنظیمات، انتخاب پروتکل.
- **فال‌بک در حالت CDN**: حالت CDN Fronting حالا دنبالهٔ فال‌بک دارد؛ اگر همهٔ لبه‌های CDN مرده باشند، هسته می‌تواند با پروتکل‌های جایگزین وصل شود و دیگر «چرخش بی‌پایان» رخ نمی‌دهد.
- **تاکتیک‌ها دوباره روشن شدند**: پارامترهای تنظیم‌شده از طرف شبکهٔ سایفون (tactics) دوباره فعال‌اند — مکانیزم اصلی تطبیق خودکار با تغییر مسدودسازی‌ها؛ این هم پایداری اتصال و هم سرعت را بالا می‌برد.
- **ورکرهای بیشتر**: استخر اتصال موازی حالا همیشه اعمال می‌شود — پیش‌فرض ۶۴ و قابل انتخاب تا **۲۵۶ ورکر** (۶۴/۹۶/۱۲۸/۱۹۲/۲۵۶) برای بیشترین فشار اتصال موازی روی شبکه‌های فیلترشده.
- **گرافیک کاملاً تازه**: مجموعه آیکون‌های اختصاصی گرادیانی دست‌ساز (رادار، برق، نبض، تنظیمات، اشتراک، لاگ، کره، شعله، سپر، نجات) با هالهٔ نوری؛ دکمهٔ پاور با گرادیان زنده (آبی کبود ← سبز زمردی ← آتش نارنجی-رز) + درخشش چرخان؛ حلقهٔ اتصال گرادیانی؛ بج «مرحلهٔ نجات» روی پنل اتصال؛ شفق قطبی پنج‌رنگه و پرنورتر؛ آیکون‌های نوار بالا و کارت‌های بخش همگی گرادیانی شدند.

**English:**
- **Rescue Engine**: built from your real field log (1,701 unanswered CDN scan attempts, zero tunnels, QUIC fully blocked). CDN mode no longer waits forever when every front dies — the engine auto-escalates: (1) drop QUIC and go TCP-only, (2) expand to the full protocol set (all meek forms + Shadowsocks + SSH), (3) EXTREME — every transport with the region preference released. Each stage is surfaced live in the UI; the ladder disarms on first successful connect.
- **New "Extreme" mode for heavy filtering / total shutdown**: every transport at once — meek in all forms (CDN, unfronted, HTTP), QUIC, Shadowsocks, SSH/OSSH, the CDN scan, and conduit (inproxy). Selectable in Settings.
- **CDN mode fallback tail**: CDN Fronting now carries fallback protocols after the CDN set — if every CDN edge is dead, the core can still establish a tunnel instead of spinning.
- **Tactics re-enabled**: the Psiphon network's remote-tuned parameters (tactics) are active again — the primary auto-adaptation mechanism when blocks shift; improves both connection survival and throughput.
- **More workers**: the parallel dial pool is now always applied — default 64, selectable up to **256 workers** (64/96/128/192/256) for maximum parallel dial pressure.
- **Vivid UI refresh**: a hand-drawn gradient icon set with chromatic halos (radar, bolt, pulse, sliders, share, scroll, globe, flame, shield, rescue); the power button is now a living gradient (royal blue → emerald → orange-rose fire) with a rotating sheen; a gradient connection ring; a "rescue stage" badge on the connection panel; a brighter five-field aurora; gradient app-bar and section-card icons throughout.

---

# Forough (فروغ) 2.3.0 — Android

**فروغ؛ پنجره‌ای روشن به اینترنت آزاد** · Forough — a bright window to the open internet

## What's new in 2.3.0 — the "engine + 64 workers + shine" release

**فارسی:**
- **هستهٔ موتور تقویت شد (speed+)**: حالت بیست حالا با **۶۴ ورکر موازی** (بجای ۳۰) لبه‌ها و سرورها را هم‌زمان امتحان می‌کند؛ اسکن CDN با پوشش دوبرابر (fanout ۱۶) و حافظهٔ کیفیت لبه‌ها با اندازه‌گیری زمان واقعی برقراری هر مسیر، همیشه روی **سریع‌ترین لبهٔ CDN** وصل می‌شود.
- **بهینه‌سازی توربو شبکه (BBR)**: گزینهٔ جدید «بهینه‌سازی توربو» — کنترل ازدحام BBR، بافر سوکت ۴ مگابایتی و keepalive تنظیم‌شده روی همهٔ اتصال‌های تونل؛ روی شبکه‌های پرِ‌تأخیر و پرِ‌لود (که رایج‌ترین عامل افت سرعت دانلود/آپلود است) نرخ پایدار انتقال به‌طور محسوس بالاتر می‌رود. پیش‌فرض: روشن.
- **تنظیم ورکرهای اتصال**: در تنظیمات می‌توانید تعداد اتصال‌های موازی را بین ۴۸ تا ۱۲۸ انتخاب کنید (پیش‌فرض ۶۴).
- **نظم polling بهینه شد**: فاصلهٔ poll حالت بیکار meek کوتاه‌تر و بازگشت از حالت بیکار سریع‌تر شد — تعامل (وب‌گردی، چت) بعد از وقفه فوری‌تر جواب می‌دهد.
- **گرافیک و انیمیشن روان‌تر**: چهارمین میدان شفق قطبی (بنفش) برای عمق بیشتر؛ **اعداد سرعت با شمارش نرم (odometer)** به‌جای پرش بین مقادیر؛ موج نور آهسته روی حلقهٔ اتصال هنگام اتصال موفق؛ درخشش (shimmer) روی کارت‌های سرعت وقتی به اوج می‌رسند.

**English:**
- **Stronger engine (speed+)**: beast mode now dials with **64 parallel workers** (up from 30); the CDN scan covers twice as many candidates per round (fanout 16) and the edge-quality cache now measures real per-route establishment time, so reconnects always land on the **fastest measured CDN edge**.
- **Turbo network optimizations (BBR)**: new "Turbo network optimizations" switch — BBR congestion control, 4 MB socket buffers and tuned keepalives on every tunnel dial. On lossy, high-latency mobile networks (the usual cause of low upload/download), sustained throughput rises noticeably. Default: on.
- **Connection workers setting**: pick the parallel dial pool size from 48 to 128 in Settings (default 64).
- **Smoother idle polling**: shorter meek idle poll interval and gentler backoff — interactive traffic (browsing, chat) resumes instantly after idle periods.
- **Smoother graphics & motion**: a fourth aurora field (violet) for extra depth; **odometer-style smooth counting** on the speed cards; a slow light sweep riding the connection ring while connected; a shimmer highlight on speed cards at session peak.

---

# Forough (فروغ) 2.2.0 — Android

**فروغ؛ پنجره‌ای روشن به اینترنت آزاد** · Forough — a bright window to the open internet

## What's new in 2.2.0 — the "share + speed + shine" release

**فارسی:**
- **اشتراک‌گذاری پروکسی**: تونل فروغ حالا می‌تواند با بقیهٔ دستگاه‌ها به اشتراک گذاشته شود — گوشی دیگر، لپ‌تاپ، تلویزیون. سوییچ اشتراک‌گذاری پروکسی‌های HTTP/SOCKS را روی وای‌فای و هات‌اسپات باز می‌کند، صفحهٔ جدید «اشتراک‌گذاری پروکسی» آدرس‌ها را نشان می‌دهد، **QR کد** می‌سازد و مراحل راه‌اندازی را قدم‌به‌قدم توضیح می‌دهد. در منوی بیشتر و تنظیمات هم میان‌بر دارد.
- **سرعت پیش‌فرض به سطح مگابایت**: حالت پیش‌فرض اتصال به **خودکار (Auto)** تغییر کرد — همهٔ مکانیزم‌های CDN فرانتینگ (اسکن لبه‌ها، رتبه‌بندی کیفیت، گزینه‌های لبه) فعال می‌مانند اما پروتکل‌های مستقیم سریع (OSSH / QUIC-OSSH / Shadowsocks) هم اجازهٔ مسابقه دارند؛ روی شبکه‌هایی که حالت فقط-CDN را به کیلوبایت محدود می‌کرد، این تغییر سرعت را به مگابایت می‌رساند. حالت CDN Fronting همچنان برای شبکه‌های فیلترشده در تنظیمات هست.
- **MTU بهینه شد (۱۴۰۰)**: بسته‌های داخلی دیگر هنگام عبور از تونل تکه‌تکه نمی‌شوند؛ **HTTP/3 و پخش ویدیو** که قبلاً به TCP کند برمی‌گشت حالا روان باز می‌شود.
- **اسکنر دستی دیگر لگ نمی‌زنه**: ریشهٔ لگ پیدا شد — نتایج ۹۶+ ترد کارگر مستقیم روی state رابط کامپوز می‌نوشت و کل لیست ده‌ها بار در ثانیه بازترسیم می‌شد. حالا نتایج در بافر میانی جمع و هر ۱۲۰ میلی‌ثانیه یک‌بار روی ترد اصلی به رابط منتقل می‌شود؛ لیست نتایج هم Lazy با کلید پایدار و مرتب‌شدهٔ «سریع‌ترین اول» است، همراه چیپ‌های زندهٔ آماری و نوار سرعت برای هر لبه.
- **«IPهای فعال وصل نمی‌شدند» رفع شد**: قبلاً همهٔ IPهای زنده (حتی صدها IP زندهٔ TLS) خودکار تیک می‌خوردند و ترتیب برقراری تونل را روی لبه‌های مرده هدر می‌داد. حالا **۱۲ لبهٔ سریع‌تر** خودکار انتخاب می‌شوند، «استفاده از انتخاب‌ها» فقط ۱۶ لبهٔ برتر (سریع‌ترین اول) را می‌نویسد و پروتکل را هم خودکار روی CDN می‌گذارد؛ دکمهٔ جدید **«استفاده و اتصال»** در یک لمس اعمال + اتصال می‌کند.
- **گرافیک بیشتر، انیمیشن روان‌تر**: پس‌زمینهٔ زندهٔ شفق قطبی (aurora) پشت پنل اتصال — خاموش کم‌نور، متصل درخشان؛ انفجار ذرات سبز هنگام اتصال موفق؛ دو کمان چرخان مخالف هنگام برقراری؛ نوار شدت گرادیانی زیر سرعت‌ها؛ ردیف‌های نتایج اسکنر با بج رتبهٔ طلایی.

**English:**
- **Proxy sharing**: Forough's tunnel can now be shared with other devices — another phone, a laptop, a TV. The new **Proxy sharing** screen binds the HTTP/SOCKS proxies to the whole network (Wi-Fi + hotspot), shows the exact endpoints, generates a **QR code**, and walks through the setup. Available from the overflow menu and Settings.
- **Default connection speed: megabytes**: the default protocol mode is now **Auto** — all the CDN fronting machinery (edge scan, quality ranking, dial overrides) stays active, but the fast direct protocols (OSSH / QUIC-OSSH / Shadowsocks) get to compete too. On networks where CDN-only mode throttled sessions to kilobytes, this lifts throughput to megabytes. CDN Fronting remains available for heavily filtered networks.
- **Tuned MTU (1400)**: inner packets no longer fragment inside the tunnel; **HTTP/3 and video streaming** that used to fall back to slow TCP now flow smoothly.
- **Manual scan no longer lags**: root cause fixed — 96+ scanner worker threads wrote straight into Compose state and re-rendered the whole result list dozens of times per second. Results now stage in a buffer and fold into the UI on the main thread every 120 ms; the list is a lazy column with stable keys, sorted fastest-first, with live stat chips and a per-edge speed bar.
- **"Selected IPs don't connect" fixed**: previously every TLS-alive IP auto-checked (hundreds on a Cloudflare-wide scan) and the tunnel burned its establishment budget on dead fronting pairs. Now the **fastest 12** auto-check, "Use selected" writes only the top 16 (fastest first) and switches the protocol to CDN; the new **"Use selected & connect"** button applies and connects in one tap.
- **More graphics, smoother motion**: a living **aurora background** behind the connection panel (dim when idle, luminous when connected), a green **particle burst** on successful connect, **two counter-rotating arcs** while establishing, gradient intensity bars under the speed cards, and gold rank badges in the scanner results.

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

- Signed with the Forough community distribution key (stable across releases) — install directly over 2.1.0 and older.
- One-time settings migration: existing installs move from CDN-fronting-only to Auto on first launch of 2.2.0 (the measured-speed default). CDN Fronting stays selectable in Settings.
- The engine remains the Forough psiphon-tunnel-core fork.
