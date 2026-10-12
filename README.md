# doordash-navigator

**DashNav** is an Android app that logs where you drive while dashing and turns it into accurate
mileage. GPS accuracy comes first. Gas costs and DoorDash earnings will be layered on top later.

## What works today

- **Start/End dash** button. A foreground service records every GPS fix straight to the on-device
  database, so a crash or the OS killing the app loses nothing. Tracking resumes after a restart
  or reboot.
- **Mileage filter** (`core/TrackFilter`). It drops inaccurate fixes and GPS jumps, ignores jitter
  while you're parked, and bridges signal gaps with a straight line, which slightly undercounts.
  Raw points are never changed, so improving the filter recalculates every past dash.
- **Work days** run from 4 AM to 4 AM (changeable in Settings). A dash belongs to the day it
  started on, so late-night dashes stay with their evening.
- **Day view** shows a map of every dash, a raw-GPS overlay with ignored points in red, signal-loss
  warnings, and odometer entry with a GPS-vs-odometer % difference.
- **Replay** of a whole work day: animated car, scrubber, speeds from 1× to 120×, skipping between
  dashes, "skip idle time", and a follow-car mode.
- **Export** raw points as GPX or CSV.

## Project layout

| Module | What it is |
| --- | --- |
| `core/` | Plain Kotlin with no Android dependencies: distance math, the mileage filter, work-day grouping, the replay timeline, and exports. Fast unit tests. |
| `app/` | The Android app: Room database, the tracking service, and the Jetpack Compose UI with osmdroid (OpenStreetMap) maps. |

## Building

You need Android Studio (recent enough for AGP 9.4) or JDK 17 plus the Android SDK
(compile SDK 37).

```sh
./gradlew :core:test          # filter/work-day/replay unit tests
./gradlew :app:assembleDebug  # APK at app/build/outputs/apk/debug/
```

To install on a phone, enable Developer options and USB debugging, then run
`./gradlew :app:installDebug` or use Run in Android Studio.

## First run on your phone

1. Allow location, then **"Allow all the time"**. Tracking needs this to resume on its own after a
   restart.
2. Allow notifications. The tracking notification shows live miles and has an End dash button.
3. Set battery to **Unrestricted**. Samsung, Xiaomi and other phones otherwise kill background
   tracking.
4. For the first few dashes, enter your odometer at the start and end of each dash. The GPS-vs-odometer
   difference shows how well the filter is doing. Aim for within 1–3%.

## Roadmap

1. ✅ GPS foundation: tracking, the filter, work days, replay, and export
2. Capture DoorDash notifications (raw) to learn their format
3. Parse earnings per delivery and show them on the replay timeline
4. Gas purchases and cost per mile
5. Reports: $/hr, $/mi, and a tax mileage log
