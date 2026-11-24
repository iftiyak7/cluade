#!/usr/bin/env python3
"""Generates the bilingual static site (index + privacy, English and Bengali) from one template.

Usage: python3 build.py            (reads site.json; writes index.html, privacy.html, bn/...)
Output contains no inline scripts or styles, so the server's strict CSP applies unchanged.
"""
import html, json, os, pathlib

ROOT = pathlib.Path(__file__).parent
cfg = json.loads((ROOT / "site.json").read_text())

T = {
"en": dict(
  lang="en", dir="", other="bn/", other_label="বাংলা", home="./", privacy_href="privacy.html",
  title="Hayat — Your life, organized", desc="A private, offline-first Android app for tasks, routines, reminders, water, medicine, habits and Ibadah. In English and Bengali.",
  nav_features="Features", nav_privacy="Privacy", nav_download="Download",
  eyebrow="Android · English & বাংলা", h1="Your life,<br>organized.",
  lead="Tasks, routines, reminders, water, medicine, habits, notes and prayer times — in one calm, beautiful app that keeps everything on your phone.",
  cta="Download for Android", cta_note="Free · No account · Works offline", play="Get it on Google Play",
  stat1="23", stat1l="tools in one app", stat2="0", stat2l="accounts or trackers", stat3="100%", stat3l="your data on your phone",
  feat_h="Everything your day needs", feat_sub="Built for real routines — from Fajr to bedtime.",
  f=[("Tasks & routines","Plan your day on a timeline. Subtasks, priorities, repeats and one-tap completion.","mint"),
     ("Reminders that ring","A reliable alarm engine that survives reboots, time-zone changes and daylight saving.","lavender"),
     ("Water & medicine","Hydration goals, dose schedules, stock and refill reminders — gently tracked.","sky"),
     ("Ibadah","Offline prayer times for your city, prayer log, zikr counter, Quran goal, fasting and Hijri date.","amber"),
     ("Habits & progress","Streaks, consistency score and achievements earned from your own records.","pink"),
     ("Notes & journal","Checklists, mood and journal entries — lock private notes with your fingerprint.","mint")],
  screens_h="Designed to feel calm", screens_sub="Light and dark themes, smooth motion, large touch targets and full Bengali support.",
  priv_h="Your data never leaves your phone.", priv_lead="No sign-up. No analytics. No ads. Hayat stores everything in its private storage on your device.",
  p=[("No account","Open the app and start. Nothing to register."),("Encrypted backups","Export a password-protected backup file you keep."),("App lock","PIN and fingerprint lock, hidden from the app switcher.")],
  priv_link="Read the privacy policy",
  bn_h="বাংলায় সম্পূর্ণ", bn_p="Every screen in English and Bengali, with Bengali digits, Bangladesh prayer cities and Hijri dates. Switch any time.",
  faq_h="Questions", faq=[
    ("Does Hayat need the internet?","No. Every feature works offline. If you leave Announcements on, Hayat downloads a short public list of tips about once an hour — it sends nothing about you, and you can turn it off."),
    ("Where is my data stored?","Only on your phone. Make an encrypted backup file to move to a new phone."),
    ("Is the Islamic module required?","No. It is optional and can be turned on or off in Settings."),
    ("Will reminders always ring on time?","Hayat uses Android's exact alarms when allowed and shows you if your phone limits them. Some battery savers can delay alarms; the app explains how to fix that.")],
  foot_privacy="Privacy policy", foot_rights="Made with care in Bangladesh.",
  privacy_title="Privacy policy — Hayat", privacy_h="Privacy policy", updated="Last updated: November 2026",
  privacy_body=[
    ("Summary","Hayat is designed so that your personal data stays on your device. There is no account, no analytics, no advertising and no crash reporting service."),
    ("Data on your phone","Tasks, routines, reminders, health logs, prayer logs, notes, journal entries and settings are stored only in Hayat's private app storage on your phone. Android cloud backup and device transfer are disabled for this data."),
    ("Announcements","If Announcements is turned on (the default), the app downloads a public list of tips and updates from our server about once an hour while you are online. The request contains only the app's version number in the User-Agent and a cache tag; it contains no identifiers, cookies or personal data. Our server sees your IP address as part of any internet connection; it is used only in memory to prevent abuse and is not logged or stored. Turn this off in Settings → General → Announcements, which also deletes downloaded announcements."),
    ("Backups","Backups are files you create and choose where to save. They can be encrypted with your password (AES-256-GCM). We never receive them."),
    ("Permissions","Hayat asks for notifications and exact alarms (for reminders), and optionally biometrics (for app lock). It does not request location, contacts, SMS, storage or advertising identifiers. Prayer times use a city you choose."),
    ("Children","Hayat does not knowingly collect any data from anyone, including children."),
    ("Changes","If this policy changes, the new version will be published on this page with a new date."),
  ],
  contact_h="Contact",
),
"bn": dict(
  lang="bn", dir="../", other="../", other_label="English", home="./", privacy_href="privacy.html",
  title="হায়াত — আপনার জীবন, গোছানো", desc="কাজ, রুটিন, রিমাইন্ডার, পানি, ওষুধ, অভ্যাস ও ইবাদতের জন্য ব্যক্তিগত, অফলাইন-ফার্স্ট অ্যান্ড্রয়েড অ্যাপ। বাংলা ও ইংরেজিতে।",
  nav_features="ফিচার", nav_privacy="গোপনীয়তা", nav_download="ডাউনলোড",
  eyebrow="অ্যান্ড্রয়েড · বাংলা ও English", h1="আপনার জীবন,<br>গোছানো।",
  lead="কাজ, রুটিন, রিমাইন্ডার, পানি, ওষুধ, অভ্যাস, নোট ও নামাজের সময় — একটি শান্ত, সুন্দর অ্যাপে, সবকিছু আপনার ফোনেই থাকে।",
  cta="অ্যান্ড্রয়েডের জন্য ডাউনলোড", cta_note="ফ্রি · অ্যাকাউন্ট লাগে না · অফলাইনে চলে", play="Google Play-তে পান",
  stat1="২৩", stat1l="টি টুল এক অ্যাপে", stat2="০", stat2l="অ্যাকাউন্ট বা ট্র্যাকার", stat3="১০০%", stat3l="ডেটা আপনার ফোনে",
  feat_h="আপনার দিনের যা যা দরকার", feat_sub="বাস্তব রুটিনের জন্য তৈরি — ফজর থেকে ঘুম পর্যন্ত।",
  f=[("কাজ ও রুটিন","টাইমলাইনে দিন সাজান। সাবটাস্ক, অগ্রাধিকার, পুনরাবৃত্তি আর এক ট্যাপে সম্পন্ন।","mint"),
     ("ঠিক সময়ে রিমাইন্ডার","নির্ভরযোগ্য অ্যালার্ম ইঞ্জিন — রিস্টার্ট, টাইম জোন বদলেও কাজ করে।","lavender"),
     ("পানি ও ওষুধ","পানির লক্ষ্য, ওষুধের সময়সূচি, স্টক ও রিফিল রিমাইন্ডার।","sky"),
     ("ইবাদত","আপনার শহরের অফলাইন নামাজের সময়, নামাজের হিসাব, জিকির, কুরআনের লক্ষ্য, রোজা ও হিজরি তারিখ।","amber"),
     ("অভ্যাস ও অগ্রগতি","স্ট্রিক, ধারাবাহিকতা স্কোর আর নিজের রেকর্ড থেকে অর্জন।","pink"),
     ("নোট ও জার্নাল","চেকলিস্ট, মুড ও জার্নাল — ব্যক্তিগত নোট আঙুলের ছাপে লক করুন।","mint")],
  screens_h="শান্ত অনুভূতির জন্য ডিজাইন", screens_sub="লাইট ও ডার্ক থিম, মসৃণ অ্যানিমেশন, বড় টাচ এরিয়া আর পূর্ণ বাংলা সাপোর্ট।",
  priv_h="আপনার ডেটা ফোনের বাইরে যায় না।", priv_lead="সাইন-আপ নেই। অ্যানালিটিক্স নেই। বিজ্ঞাপন নেই। সবকিছু আপনার ফোনে হায়াতের নিজস্ব স্টোরেজে থাকে।",
  p=[("অ্যাকাউন্ট নেই","অ্যাপ খুলুন আর শুরু করুন।"),("এনক্রিপ্টেড ব্যাকআপ","পাসওয়ার্ড-সুরক্ষিত ব্যাকআপ ফাইল আপনার কাছে রাখুন।"),("অ্যাপ লক","পিন ও আঙুলের ছাপ লক।")],
  priv_link="গোপনীয়তা নীতি পড়ুন",
  bn_h="সম্পূর্ণ বাংলায়", bn_p="প্রতিটি স্ক্রিন বাংলা ও ইংরেজিতে — বাংলা সংখ্যা, বাংলাদেশের শহরের নামাজের সময় আর হিজরি তারিখসহ।",
  faq_h="প্রশ্নোত্তর", faq=[
    ("হায়াতে কি ইন্টারনেট লাগে?","না। সব ফিচার অফলাইনে চলে। ঘোষণা চালু থাকলে প্রায় ঘণ্টায় একবার টিপসের একটি ছোট পাবলিক তালিকা ডাউনলোড হয় — আপনার সম্পর্কে কিছুই পাঠানো হয় না, আর চাইলে বন্ধ করতে পারেন।"),
    ("আমার ডেটা কোথায় থাকে?","শুধু আপনার ফোনে। নতুন ফোনে নিতে এনক্রিপ্টেড ব্যাকআপ ফাইল বানান।"),
    ("ইসলামিক অংশ কি বাধ্যতামূলক?","না। সেটিংস থেকে চালু বা বন্ধ করা যায়।"),
    ("রিমাইন্ডার কি সবসময় ঠিক সময়ে বাজবে?","অনুমতি থাকলে হায়াত অ্যান্ড্রয়েডের এক্স্যাক্ট অ্যালার্ম ব্যবহার করে, আর ফোন সীমিত করলে তা দেখায়। কিছু ব্যাটারি সেভার দেরি করাতে পারে; অ্যাপে সমাধান বলা আছে।")],
  foot_privacy="গোপনীয়তা নীতি", foot_rights="বাংলাদেশে যত্ন নিয়ে তৈরি।",
  privacy_title="গোপনীয়তা নীতি — হায়াত", privacy_h="গোপনীয়তা নীতি", updated="সর্বশেষ হালনাগাদ: নভেম্বর ২০২৬",
  privacy_body=[
    ("সংক্ষেপে","হায়াত এমনভাবে তৈরি যে আপনার ব্যক্তিগত ডেটা আপনার ডিভাইসেই থাকে। কোনো অ্যাকাউন্ট, অ্যানালিটিক্স, বিজ্ঞাপন বা ক্র্যাশ রিপোর্টিং সার্ভিস নেই।"),
    ("ফোনে থাকা ডেটা","কাজ, রুটিন, রিমাইন্ডার, স্বাস্থ্য ও নামাজের হিসাব, নোট, জার্নাল ও সেটিংস শুধু আপনার ফোনে হায়াতের নিজস্ব স্টোরেজে থাকে। এই ডেটার জন্য অ্যান্ড্রয়েড ক্লাউড ব্যাকআপ ও ডিভাইস ট্রান্সফার বন্ধ।"),
    ("ঘোষণা","ঘোষণা চালু থাকলে (ডিফল্ট), অনলাইনে থাকাকালীন অ্যাপ প্রায় ঘণ্টায় একবার আমাদের সার্ভার থেকে টিপস ও আপডেটের একটি পাবলিক তালিকা ডাউনলোড করে। অনুরোধে শুধু অ্যাপের ভার্সন নম্বর (User-Agent) ও একটি ক্যাশ ট্যাগ থাকে; কোনো আইডি, কুকি বা ব্যক্তিগত তথ্য থাকে না। যেকোনো ইন্টারনেট সংযোগের মতো সার্ভার আপনার আইপি দেখে; এটি শুধু অপব্যবহার ঠেকাতে মেমরিতে ব্যবহৃত হয়, লগ বা সংরক্ষণ করা হয় না। সেটিংস → সাধারণ → ঘোষণা থেকে বন্ধ করুন; এতে ডাউনলোড করা ঘোষণাও মুছে যায়।"),
    ("ব্যাকআপ","ব্যাকআপ হলো আপনার তৈরি ফাইল, যা আপনি নিজে কোথায় রাখবেন ঠিক করেন। পাসওয়ার্ড দিয়ে এনক্রিপ্ট করা যায় (AES-256-GCM)। আমরা কখনো তা পাই না।"),
    ("অনুমতি","রিমাইন্ডারের জন্য নোটিফিকেশন ও এক্স্যাক্ট অ্যালার্ম, আর ঐচ্ছিকভাবে অ্যাপ লকের জন্য বায়োমেট্রিক। লোকেশন, কন্টাক্ট, এসএমএস, স্টোরেজ বা বিজ্ঞাপন আইডি চাওয়া হয় না। নামাজের সময়ের জন্য আপনি শহর বেছে নেন।"),
    ("শিশু","হায়াত কারো কাছ থেকেই জেনেশুনে কোনো ডেটা সংগ্রহ করে না, শিশুসহ।"),
    ("পরিবর্তন","নীতি বদলালে নতুন তারিখসহ এই পাতায় প্রকাশ করা হবে।"),
  ],
  contact_h="যোগাযোগ",
),
}

SCREENS = {"en": ["en-home", "en-progress", "en-home-dark", "en-ibadah", "en-calendar", "en-water"],
           "bn": ["bn-home", "bn-ibadah", "en-progress", "en-home-dark", "en-calendar", "en-water"]}

ICONS = [
  "M22 5.18 10.59 16.6l-4.24-4.24 1.41-1.41 2.83 2.83 10-10L22 5.18zM12 20c-4.41 0-8-3.59-8-8s3.59-8 8-8c1.57 0 3.04.46 4.28 1.25l1.45-1.45C16.1 2.67 14.13 2 12 2 6.48 2 2 6.48 2 12s4.48 10 10 10c1.73 0 3.36-.44 4.78-1.22l-1.5-1.5c-1 .46-2.11.72-3.28.72z",
  "M22 5.72l-4.6-3.86-1.29 1.53 4.6 3.86L22 5.72zM7.88 3.39L6.6 1.86 2 5.71l1.29 1.53 4.59-3.85zM12.5 8H11v6l4.75 2.85.75-1.23-4-2.37V8zM12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9c4.97 0 9-4.03 9-9s-4.03-9-9-9zm0 16c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z",
  "M12 2c-5.33 4.55-8 8.48-8 11.8 0 4.98 3.8 8.2 8 8.2s8-3.22 8-8.2c0-3.32-2.67-7.25-8-11.8z",
  "M14 2c1.82 0 3.53.5 5 1.35-2.99 1.73-5 4.95-5 8.65s2.01 6.92 5 8.65c-1.47.85-3.18 1.35-5 1.35-5.52 0-10-4.48-10-10S8.48 2 14 2z",
  "M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z",
  "M3 10h11v2H3zm0-2h11V6H3zm0 8h7v-2H3zm15.01-3.13.71-.71c.39-.39 1.02-.39 1.41 0l.71.71c.39.39.39 1.02 0 1.41l-.71.71-2.12-2.12zm-.71.71-5.3 5.3V21h2.12l5.3-5.3-2.12-2.12z",
]

def e(s): return html.escape(s, quote=True)

def head(t, title, desc, prefix, page):
    alt = "bn/" if t["lang"] == "en" else ""
    base = f"https://{cfg['domain']}/" if cfg.get("domain") else ""
    canon = (f'<link rel="canonical" href="{base}{"" if t["lang"]=="en" else "bn/"}{page}">\n'
             f'<link rel="alternate" hreflang="en" href="{base}{page}">\n<link rel="alternate" hreflang="bn" href="{base}bn/{page}">\n') if base else ""
    return f"""<!doctype html>
<html lang="{t['lang']}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{e(title)}</title>
<meta name="description" content="{e(desc)}">
<meta name="theme-color" content="#0f1a16">
<meta property="og:title" content="{e(title)}">
<meta property="og:description" content="{e(desc)}">
<meta property="og:image" content="{prefix}assets/screens/{SCREENS[t['lang']][0]}.png">
{canon}<link rel="icon" href="{prefix}assets/img/logo.svg" type="image/svg+xml">
<link rel="preload" href="{prefix}assets/fonts/hind_siliguri_regular.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="{prefix}assets/site.css">
<script src="{prefix}assets/site.js" defer></script>
</head>
<body class="lang-{t['lang']}">
"""

def nav(t, prefix, on_privacy=False):
    home = "./" if not on_privacy else "./"
    return f"""<header class="nav">
  <a class="brand" href="{home}"><img src="{prefix}assets/img/logo.svg" alt="" width="34" height="34"> {'Hayat' if t['lang']=='en' else 'হায়াত'}</a>
  <nav>
    <a href="./#features">{t['nav_features']}</a>
    <a href="{t['privacy_href']}">{t['nav_privacy']}</a>
    <a class="lang" href="{t['other']}{'privacy.html' if on_privacy else ''}" hreflang="{'bn' if t['lang']=='en' else 'en'}">{t['other_label']}</a>
    <a class="btn small" href="./#download">{t['nav_download']}</a>
  </nav>
</header>
"""

def footer(t, prefix):
    contact = f' · <a href="mailto:{e(cfg["contact_email"])}">{e(cfg["contact_email"])}</a>' if cfg.get("contact_email") else ""
    return f"""<footer class="foot">
  <div class="wrap row">
    <span><img src="{prefix}assets/img/logo.svg" alt="" width="22" height="22"> © Hayat · {t['foot_rights']}</span>
    <span><a href="{t['privacy_href']}">{t['foot_privacy']}</a>{contact}</span>
  </div>
</footer>
</body>
</html>
"""

def index(t):
    prefix = t["dir"]
    shots = SCREENS[t["lang"]]
    feats = "\n".join(f'<article class="feat {tone} reveal"><div class="ic"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="{ICONS[i]}"/></svg></div><h3>{e(h)}</h3><p>{e(p)}</p></article>' for i, (h, p, tone) in enumerate(t["f"]))
    gallery = "\n".join(f'<img class="shot reveal" src="{prefix}assets/screens/{s}.webp" alt="" loading="lazy" width="411" height="891">' for s in shots)
    privs = "\n".join(f'<div class="pi reveal"><h3>{e(h)}</h3><p>{e(p)}</p></div>' for h, p in t["p"])
    faq = "\n".join(f'<details class="reveal"><summary>{e(q)}</summary><p>{e(a)}</p></details>' for q, a in t["faq"])
    play = f'<a class="btn ghost" href="{e(cfg["play_url"])}">{t["play"]}</a>' if cfg.get("play_url") else ""
    return head(t, t["title"], t["desc"], prefix, "") + nav(t, prefix) + f"""
<main>
<section class="hero">
  <div class="wrap hero-grid">
    <div class="hero-copy">
      <p class="eyebrow reveal">{t['eyebrow']}</p>
      <h1 class="reveal">{t['h1']}</h1>
      <p class="lead reveal">{e(t['lead'])}</p>
      <div class="ctas reveal" id="download">
        <a class="btn" href="{e(cfg['apk_url'])}">{t['cta']}</a>{play}
      </div>
      <p class="note reveal">{t['cta_note']}</p>
      <div class="stats reveal">
        <div><b>{t['stat1']}</b><span>{t['stat1l']}</span></div>
        <div><b>{t['stat2']}</b><span>{t['stat2l']}</span></div>
        <div><b>{t['stat3']}</b><span>{t['stat3l']}</span></div>
      </div>
    </div>
    <div class="hero-phones">
      <img class="phone back" src="{prefix}assets/screens/{shots[2]}.webp" alt="" width="411" height="891">
      <img class="phone front" src="{prefix}assets/screens/{shots[0]}.webp" fetchpriority="high" alt="{e(t['title'])}" width="411" height="891">
    </div>
  </div>
</section>
<section class="wrap section" id="features">
  <h2 class="reveal">{t['feat_h']}</h2>
  <p class="sub reveal">{t['feat_sub']}</p>
  <div class="feats">{feats}</div>
</section>
<section class="section screens">
  <div class="wrap"><h2 class="reveal">{t['screens_h']}</h2><p class="sub reveal">{t['screens_sub']}</p></div>
  <div class="gallery" tabindex="0">{gallery}</div>
</section>
<section class="privacy-band">
  <div class="wrap">
    <h2 class="reveal">{t['priv_h']}</h2>
    <p class="lead reveal">{t['priv_lead']}</p>
    <div class="pis">{privs}</div>
    <a class="link reveal" href="{t['privacy_href']}">{t['priv_link']} →</a>
  </div>
</section>
<section class="wrap section bn-band reveal">
  <h2>{t['bn_h']}</h2><p class="sub">{t['bn_p']}</p>
</section>
<section class="wrap section faq">
  <h2 class="reveal">{t['faq_h']}</h2>
  {faq}
</section>
</main>
""" + footer(t, prefix)

def privacy(t):
    prefix = t["dir"]
    body = "\n".join(f"<h2>{e(h)}</h2>\n<p>{e(p)}</p>" for h, p in t["privacy_body"])
    contact = f'<h2>{t["contact_h"]}</h2>\n<p><a href="mailto:{e(cfg["contact_email"])}">{e(cfg["contact_email"])}</a></p>' if cfg.get("contact_email") else ""
    return head(t, t["privacy_title"], t["desc"], prefix, "privacy.html") + nav(t, prefix, True) + f"""
<main class="wrap doc">
  <h1>{t['privacy_h']}</h1>
  <p class="muted">{t['updated']}</p>
  {body}
  {contact}
</main>
""" + footer(t, prefix)

for lang, t in T.items():
    out = ROOT if lang == "en" else ROOT / "bn"
    out.mkdir(exist_ok=True)
    (out / "index.html").write_text(index(t))
    (out / "privacy.html").write_text(privacy(t))
print("site built", "(set domain/contact_email in site.json)" if not cfg.get("domain") else "")
