# WorkBalance for Android

[Русская версия](README_RU.md)

[![Android CI](https://github.com/MaxStriX324/WorkBalance-Android/actions/workflows/android.yml/badge.svg)](https://github.com/MaxStriX324/WorkBalance-Android/actions/workflows/android.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android)](https://developer.android.com/)

WorkBalance is a private, offline-first Android app for tracking check-ins, check-outs, required breaks, daily targets, and monthly time balance. It is designed for workplaces where hours may be redistributed between days as long as the monthly target is completed.

> WorkBalance is an independent planning tool, not an official attendance system. Always compare its results with your employer's rules and records.

## Highlights

- one-tap check-in and check-out;
- multiple work intervals during a day;
- editing and deleting records for any date;
- clear separation of required break and additional absence;
- configurable daily target and required break;
- presets for `8:00 + 1:00` and `4:00 + 0:30` schedules;
- daily and monthly balance, leave-time estimates, and monthly forecast;
- planned unavailable days without reducing the monthly target;
- month statistics and progress;
- Russian 2026 production calendar with transferred days off;
- optional Saratov Oblast calendar with Radonitsa;
- configurable shortened-workday handling;
- local forgotten check-in/check-out reminders without location access;
- break-ending reminder;
- home-screen widget and Quick Settings tile;
- complete JSON backup and restore;
- localized monthly CSV export;
- Russian and English interfaces with an in-app language switch;
- no account, advertising, analytics, or proprietary server.

## Break calculation

```text
Credited time = on-site time − max(0, required break − off-site time)
```

Example without leaving the site:

```text
09:00 check-in → 18:00 check-out
On-site: 9:00
Required break: 1:00
Credited: 8:00
```

Example with a one-hour off-site break:

```text
09:00 in → 13:00 out → 14:00 in → 18:00 out
On-site: 8:00
Off-site: 1:00
Additional deduction: 0:00
Credited: 8:00
```

See [calculation details](docs/CALCULATION.md), [production calendar notes](docs/PRODUCTION_CALENDAR.md), and [reminder rules](docs/REMINDERS.md).

## Privacy

Work records are stored in the app's local Room database. If Android system backup is enabled, Android may back up the database and preferences through the device's configured backup provider. The GitHub distribution uses internet access only to read the repository's public release list; that check can be disabled. Google Play and RuStore variants omit the in-app updater and the internet permission. JSON and CSV exports are created only when the user explicitly selects a destination through Android's system file picker.

Read the [privacy policy](PRIVACY.md) or its [Russian version](PRIVACY_RU.md).

## Build variants

| Variant | Intended distribution | Built-in GitHub update check | Internet permission |
|---|---|---:|---:|
| `github` | GitHub Releases and direct APK sharing | Yes | Yes |
| `play` | Google Play | No | No |
| `rustore` | RuStore | No | No |

All variants use the same application ID, database, and backup format. Updating an installed build preserves data when the new package has a higher version code and is signed with the same key.

## Build from source

Requirements:

- Android Studio with Android SDK Platform 36;
- JDK 17;
- internet access for the first Gradle sync.

Windows PowerShell:

```powershell
.\gradlew.bat testGithubDebugUnitTest assembleGithubDebug
```

Linux or macOS:

```bash
./gradlew testGithubDebugUnitTest assembleGithubDebug
```

The debug APK is created at:

```text
app/build/outputs/apk/github/debug/app-github-debug.apk
```

For signed APK/AAB instructions and store-specific tasks, see [docs/BUILDING.md](docs/BUILDING.md).

## Technology

- Kotlin and Jetpack Compose;
- Material 3;
- Room, Coroutines, and Flow;
- AlarmManager for local reminders;
- Android Storage Access Framework for import and export;
- min SDK 26, target SDK 36;
- JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.1.

## Current scope

The main calculation and editing workflow is usable and covered by unit tests. The next major validation step is comparison against anonymized real access-control exports. Planned work also includes future production-calendar packs, database migration tests, accessibility review, and store publication assets.

## Contributing

Bug reports and improvements are welcome. Never publish signing keys, real backups, employee names, employer names, badge identifiers, or internal documents. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Author and license

Created by Maksim ([MaxStriX324](https://github.com/MaxStriX324)).

Released under the [MIT License](LICENSE).
