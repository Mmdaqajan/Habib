# Habib Project - AI Coding Instructions

These notes describe the product direction and repository conventions for coding assistants working on Habib.

## Product direction

- Habib is a personal habit tracker. Prioritize quick daily logging and small, user-controlled steps toward building or improving habits.
- Android is the intended user-facing product. Django and browser templates are a prototype/reference only, not a separate web product.
- Keep core use offline. Habit logs, reminders, calendar events, and profile data stay in the app's local SQLite database. Do not add a server, accounts, cloud sync, social features, or unnecessary network dependencies without an explicit product decision.
- Avoid punitive streak mechanics or flows that add friction to daily logging.
- Do not read, edit, or implement roadmap notes such as `WhatsNext.md` unless explicitly asked.

## Repository map

```text
Habib/
├── android/                         # Native Android app (Java)
│   ├── app/src/main/                # Android manifest, UI, local DB, notifications, resources
│   ├── gradle/wrapper/              # Gradle wrapper configuration
│   ├── gradlew
│   └── gradlew.bat
├── apk/                             # Locally generated debug APK output
├── apps/                            # Django prototype/reference
├── config/                          # Django settings and URLs
├── templates/                       # Django prototype templates
├── README.md                        # User-facing overview and Android build instructions
├── docs.md                          # Product, architecture, and development documentation
└── readme-copilet.md                # These AI coding instructions
```

## Android conventions

- Application ID: `com.habib.mobile`; minimum Android version: API 26; target/compile SDK: 35.
- `MainActivity.java` owns the current programmatic UI and navigation. Keep Persian labels and right-to-left layouts consistent.
- `DbHelper.java` owns the `habib-local.db` SQLite schema and migrations. Increment the database version for schema changes and preserve existing user data.
- `BirthdayReminderScheduler.java`, `NotificationReceiver.java`, and `BootReceiver.java` implement local alarm/notification behavior. Android notification permission is required on Android 13 and later.
- Reminders saved by the user are currently local records; do not imply that regular recurring reminders trigger scheduled notifications unless that functionality is implemented.
- `android/local.properties`, Gradle caches, build outputs, personal databases/photos, and signing keys are machine-local/private and must not be committed.
- The generated APK is a debug build. Never place signing credentials in source control; use a signed release build for distribution.

## Build and validation

Use JDK 17 and Android SDK Platform/Build Tools 35. From the repository root in Windows PowerShell:

```powershell
.\android\gradlew.bat -p .\android copyDebugApk
.\android\gradlew.bat -p .\android lintDebug
```

`copyDebugApk` writes `apk/habib-mobile-debug.apk`. Run the smallest relevant validation after changes; device behavior should be checked on a real Android device when available. Gradle may need internet access to download the wrapper or SDK dependencies on a fresh machine, but the app's intended day-to-day use is offline.

## Django prototype

The Django project (`apps/`, `config/`, and `templates/`) is retained as a behavioral/design reference for mobile development. Keep prototype-only changes isolated from Android behavior, and do not treat Django as an Android backend unless the product direction changes explicitly.
