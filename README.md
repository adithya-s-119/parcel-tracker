# Parcel

A quiet Android app that reads your order emails, finds the promised delivery date, and puts every arrival on one calendar — plus a home-screen widget for the next few deliveries.

## What it does

- **Connect Gmail** with read-only access (`gmail.readonly`). Nothing is sent, edited, or deleted.
- **Scans order and shipping emails** from Amazon, Flipkart, Myntra, Ajio, Meesho, Nykaa, Croma, eBay, Etsy, Walmart, and more.
- **Extracts the promised delivery date** from natural language:
  - `Arriving Thursday, November 12`
  - `Delivery by Tue, 12 Nov`
  - `Expected delivery: 25/11/2026`
  - `Arriving tomorrow`
  - `Delivery in 3 days`
  - `Arriving Nov 12 – Nov 15` (uses the end of the window)
  - It avoids return windows, order dates, and promotional dates.
- **Merges updates** from shipping and delivered emails into one delivery, using the order number or email thread.
- **Calendar** with month navigation, delivery dots, and a day-by-day view.
- **Orders list** with search, filters, and manual edits. You can fix a date, change the status, or add an order by hand.
- **Home-screen widget** showing the next few deliveries in date order. Resize it for more.
- **Daily reminder** at 9 AM for deliveries arriving today or tomorrow.
- **Works without Gmail** — skip sign-in and add deliveries manually.
- **Private by design** — emails are parsed on-device. There is no backend and no analytics.

## Screens

| Calendar | Orders | Widget |
| --- | --- | --- |
| Month grid with delivery dots and a selected-day list | Search, filters, and editable deliveries | Next deliveries, sorted by date |

## Tech

- Kotlin, Jetpack Compose, Material 3
- Room for local storage
- DataStore for settings
- WorkManager for background sync and reminders
- Glance for the home-screen widget
- Gmail REST API through OkHttp
- Google Identity Services `AuthorizationClient` for OAuth

## Requirements

- Android 8.0 (API 26) or newer
- A Google account
- Android Studio (Ladybug or newer) or the Android command-line SDK
- JDK 17+

## Google Cloud setup (required for Gmail sign-in)

Parcel identifies itself with an **Android OAuth client**. There is no client ID in the source code — Google matches the package name and signing certificate.

1. Open [Google Cloud Console](https://console.cloud.google.com/) and create (or choose) a project.
2. **APIs & Services → Library** → enable **Gmail API**.
3. **APIs & Services → OAuth consent screen**:
   - Choose **External**.
   - Add your Google account as a **test user** (or publish the app later).
   - Add the scope `https://www.googleapis.com/auth/gmail.readonly`.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Android**
   - Package name: `com.parceltracker.app`
   - SHA-1: your debug (or release) signing certificate fingerprint.
5. Build and install the app. Sign in with a test user account.

### Get your debug SHA-1

After the first build (or if `~/.android/debug.keystore` already exists):

```powershell
.\scripts\get-sha1.ps1
```

Or manually:

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

Copy the **SHA1** value into the Android OAuth client.

> **Note:** `gmail.readonly` is a restricted Gmail scope. For personal use, keep the OAuth consent screen in **Testing** and add your account as a test user. Publishing to production requires Google verification.

If sign-in shows a `DEVELOPER_ERROR` (code 10), the package name or SHA-1 does not match an Android OAuth client. Fix the client, then reinstall the app.

## Build

```powershell
# From the project root
$env:JAVA_HOME = "C:\path\to\jdk-17-or-21"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Install on a connected device or emulator:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### Android SDK packages

```powershell
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## How the parser works

`OrderEmailParser` is rule-based and intentionally conservative:

1. Identify order-related emails (order number, "your order", shipping/delivery language).
2. Find delivery-date candidates from month names, numeric dates, weekdays, relative days, and ranges.
3. Score each candidate by nearby delivery language and penalize returns, refunds, order dates, promotions, and dates far in the past.
4. Prefer explicit dates over inferred weekdays, and the end of a delivery window.
5. Merge multiple emails for the same order; delivered emails set the actual date, and user edits are preserved.

Unit tests cover Amazon, Flipkart, ranges, return-window avoidance, relative dates, weekdays, tracking numbers, and promotional false positives.

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## Background sync

- A WorkManager job checks Gmail every 6 hours when automatic sync is on.
- Access tokens are kept in memory. When the app reopens, Google Identity Services silently re-authorizes using the saved account. If consent has expired, the calendar shows a **Reconnect** banner.
- Reminder notifications are scheduled daily at 9 AM local time. Android 13+ asks for notification permission.

## Privacy

- No server, no analytics, no third-party email processing.
- Gmail data is read through the official API with read-only scope.
- Emails and delivery records are stored in a local Room database.
- The access token lives in memory only.
- **Delete all data** in Settings removes the database, Gmail connection, and settings.

## Limitations

- Date extraction is rule-based; unusual email layouts may be missed. Every delivery can be edited manually.
- The Gmail query uses the `Purchases` category plus order/shipping subject terms over the chosen scan window (90 / 180 / 365 days).
- Background sync needs the OAuth consent to still be valid; otherwise open the app and reconnect.
- The release build is not minified by default. Add your release signing config and enable shrinking when you ship it.

## Project structure

```
app/src/main/java/com/parceltracker/app/
├── MainActivity.kt
├── ParcelApp.kt
├── AppContainer.kt
├── data/          # Room, Gmail API, OAuth, parser, sync
├── notifications/ # Daily delivery reminders
├── ui/            # Compose screens, theme, view model
├── widget/        # Glance home-screen widget
└── work/          # WorkManager jobs
```

## License

Private project. All rights reserved.
