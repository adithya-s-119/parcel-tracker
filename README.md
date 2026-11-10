# Parcel

A quiet Android app that reads your order emails, finds the promised delivery date, and puts every arrival on one calendar — plus a home-screen widget for the next few deliveries.

## What it does

- **Connect Gmail** with read-only access (`gmail.readonly`). Nothing is sent, edited, or deleted.
- **Or use a Gmail app password + IMAP** — free, automatic, and no Google Cloud project.
- **Or skip email entirely** and share an order email to Parcel, or add deliveries by hand.
- **Read delivery texts (SMS)** on-device to catch courier and store delivery updates.
- **Scans order and shipping emails** from Amazon, Flipkart, Myntra, Ajio, Meesho, Nykaa, Croma, eBay, Etsy, Walmart, Robu, Robocraze, Hubtronics, Xcluma, Probots, Sharvie Electronics, FlyRobo, Robokits, ThinkRobotics, RhydoLABZ, and more.
- **Extracts the promised delivery date** from natural language:
  - `Arriving Thursday, November 12`
  - `Delivery by Tue, 12 Nov`
  - `Expected delivery: 25/11/2026`
  - `Arriving tomorrow`
  - `Delivery in 3 days`
  - `Arriving Nov 12 – Nov 15` (uses the end of the window)
  - It avoids return windows, order dates, and promotional dates.
- **Merges updates** from shipping and delivered emails into one delivery, using the order number, tracking number, or email thread.
- **Deduplicates across Gmail and SMS** so the same parcel appears once, even when one source only has the product name.
- **Tracks real delivery states**: scheduled, shipped, delayed, ready for pickup, out for delivery, delivery failed, delivered, cancelled, and returned to sender.
- **Filters promotional email and SMS** so offers and cashback blasts do not become fake deliveries.
- **Calendar** with month navigation, delivery dots, and a day-by-day view.
- **Orders list** with search, filters, and manual edits. You can fix a date, change the status, see the full product name, or add an order by hand.
- **Six color themes** in Settings → Appearance (Forest, Ocean, Terracotta, Plum, Indigo, Rose).
- **Home-screen widget** showing the next few deliveries in date order with large, readable text and the selected theme. Resize it for more.
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

## Three ways to fill your calendar

| Mode | Needs Google Cloud? | What you need | Automatic? |
| --- | --- | --- | --- |
| Google sign-in (OAuth) | Yes, one-time setup | Normal Google account + 2FA | Yes |
| Gmail app password (IMAP) | No | 2-Step Verification + a 16-character app password | Yes |
| Share-to-Parcel / manual | No | Nothing | Per email / manual |

### Gmail app password + IMAP (free, no Google Cloud)

This is the free automatic path that avoids OAuth and Google Cloud.

1. Turn on **2-Step Verification** in your Google Account.
2. Open **App passwords** (`https://myaccount.google.com/apppasswords`).
3. Create one named **Parcel** and copy the 16-character password.
4. In Parcel, open **Settings → Use Gmail app password**, enter your Gmail address and the app password.

Parcel connects to `imap.gmail.com` over TLS, opens Gmail **read-only**, and scans for order emails. The app password is encrypted with a key in the Android Keystore and can be revoked at any time.

App passwords are not available for every account: work/school accounts, Advanced Protection accounts, and accounts without 2FA may not offer them. In those cases, use Google sign-in or Share-to-Parcel.

### Delivery texts (SMS)

Parcel can read delivery-related SMS from stores and couriers such as Amazon, Flipkart, Delhivery, Blue Dart, Ekart, DTDC, FedEx, and India Post.

Enable **Settings → Delivery texts**. Android will ask for SMS permission. Messages are parsed on-device and never uploaded.

> SMS access is a restricted permission. This build is intended for personal sideloading. Google Play requires a policy declaration and approval for apps that read SMS.

### Share to Parcel (zero setup)

Open an order email in Gmail → **Share** → **Parcel**. The app reads the shared email, extracts the store, item, order number, and delivery date, then opens a confirmation sheet.

No Google account, password, or Cloud project is needed for this mode.

## Google Cloud setup (optional, for Google sign-in)

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

### "Sign-in was cancelled" troubleshooting

Open **Sign-in help** in the app (on the welcome screen or in Settings). It shows the installed app's package name, real signing SHA-1, and Google Play services status. Check:

1. **Google Play services** is available. Emulators need a **Google Play** system image, and a Google account must be added inside the emulator.
2. The Android OAuth client uses exactly the package name and SHA-1 shown in the help dialog. Reinstall the APK after changing the client.
3. **Gmail API** is enabled in the Google Cloud project.
4. The OAuth consent screen includes the `gmail.readonly` scope and your account is listed as a **test user**.
5. You did not tap outside the Google account/consent sheet.

The app logs the underlying failure with the tag `ParcelAuth`:

```powershell
adb logcat -s ParcelAuth
```

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

- A WorkManager job checks Gmail (OAuth or IMAP) and SMS every 6 hours when automatic sync is on.
- OAuth access tokens are kept in memory. When the app reopens, Google Identity Services silently re-authorizes using the saved account. If consent has expired, the calendar shows a **Reconnect** banner.
- In app-password mode, the 16-character password is encrypted with an Android Keystore key and used only for read-only IMAP. It is never logged or uploaded.
- Reminder notifications are scheduled daily at 9 AM local time. Android 13+ asks for notification permission.

## Privacy

- No server, no analytics, no third-party email processing.
- Gmail is read through the official API (read-only scope) or IMAP (read-only folder), depending on the sign-in mode.
- Delivery SMS is read on-device only.
- Emails, texts, and delivery records are stored in a local Room database.
- OAuth access tokens live in memory only; the IMAP app password is encrypted at rest with Android Keystore.
- **Delete all data** in Settings removes the database, account connection, SMS settings, and app settings.

## Limitations

- Date extraction is rule-based; unusual email or SMS layouts may be missed. Every delivery can be edited manually.
- The Gmail API query uses the `Purchases` category plus order/shipping subject terms over the chosen scan window (90 / 180 / 365 days).
- IMAP searches Gmail's All Mail folder over the same window. It requires an app password.
- SMS scanning requires the READ_SMS/RECEIVE_SMS permissions and is best suited to personal sideloaded builds.
- Background OAuth sync needs the consent to still be valid; otherwise open the app and reconnect.
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
