# Wolve 2.0

Dark WHOOP-style native Android app (Kotlin + Jetpack Compose) for BLE heart-rate straps. Works offline; the only network use is the optional AI Coach.
Pure JVM bytecode, no native libraries: runs natively on 64-bit (arm64-v8a, x86_64) and 32-bit devices.

## Get the APK without Android Studio (GitHub Actions)
1. Create a free GitHub repository and upload the contents of this folder (keep the `.github` folder).
2. Open the repository > Actions > "Build Wolve APK" > Run workflow (it also runs on every push).
3. When it finishes, open the run and download the artifact `Wolve-debug-apk` (contains app-debug.apk). Install it on your phone.
If the run shows a compile error, the log lists the file and line.

## Or build locally
Android Studio Koala+, JDK 17, open this folder, Build > Build APK(s).

## Menus (bottom bar)
- **Home**: Recovery / Strain / Sleep rings, Coach target, today's activities, stress monitor, journal, live heart rate.
- **Sleep**: overnight tracking, hypnogram, stages, efficiency, resting HR, HRV, breathing rate, 14-night chart.
- **Activity**: day strain, workouts with sport type, live heart-rate zones, calories, strain, zone history.
- **Health**: Health Monitor (HRV, resting HR, breathing vs your range), live heart/ECG, blood-pressure estimate, stress, trends.
- **More**: Coach, Journal, Trends, Stress monitor, Smart alarm, Devices, Profile, Backup and restore, About.

## New in 2.1
- Launcher icon and in-app logo are your wolf image (Titanium and Blue Titanium variants, switch in Settings > App icon).
- Body screen: your circulation illustration (male/female) with a heartbeat that follows the strap's real heart rate, and blood-flow particles whose speed follows it.
- Strap fix: Bluetooth client rewritten (Android 13+ callbacks, notification retry and pairing, battery level, watchdog), plus Diagnostics screen and shareable strap log.
- Settings hub modelled on the attached screenshots: streak, profile photo, profile (age, sex, weight, height, max HR), units, theme/accent/clock, display (reduce motion, day-cycle background, transparent cards, background image), bottom bar, app icon, strap, HRV window, health and wellness toggles, recalibrate, CSV export, automatic daily backups, rhythm (experimental).

## Earlier in 2.0
Sleep score 0-100, weekly recap with goals and achievements, guided breathwork (box, 4-7-8, coherence, sigh),
live heart-rate zone spectrum on Home, zone alerts, automatic workout detection, hydration and nap logging,
floating Wolve logo button (bottom left) that opens the AI Coach, AI Coach (Gemini and ChatGPT, any model your key allows):
daily briefing, training plan, 3-meal diet from age/weight/height, lab report reader (photo or PDF with Gemini),
backup as a ZIP of Excel workbooks plus a restore file.

## Features
Recovery (4-night calibration), day strain (Banister TRIMP), workouts with 5 HR zones + calories (Keytel),
stress monitor (HRV vs daytime baseline), journal with recovery insights, on-device coach (strain target, sleep need,
bedtime), smart alarm (system alarm clock + early wake from heart rate), 7/30-day trends, JSON backup/restore,
live ECG (Polar H10 class), blood-pressure estimate with cuff calibration, auto-reconnect, foreground service,
wolverine launcher icon.

## Not included (and why)
- WHOOP's proprietary strap protocol, SpO2 and skin temperature (sensors not exposed over standard Bluetooth).
- Cloud AI coach, home-screen widgets, Health Connect sync, self-hosted push, lifting set tracker.
- WHOOP's own algorithms: Recovery, Strain, Sleep and Stress are Wolve's models and will not match WHOOP numbers.

## Status
Written without an Android SDK: not compiled or run on a device by its author. Core algorithms were prototyped in
Python on synthetic data. Expect to tune thresholds on real nights and to fix small issues on first build.
