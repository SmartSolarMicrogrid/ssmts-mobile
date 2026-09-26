# SSMTS Mobile — Smart Solar Microgrid Trading System

> **Pure native Android** application (Kotlin + XML views + SQLite) for Prosumers and Grid
> Operators of the Smart Solar Microgrid Trading System. No cross-platform frameworks.

Part of the SSMTS suite:

| Repo | Stack | Purpose |
|---|---|---|
| `backend` | ASP.NET Core 8 + MongoDB | REST API (`http://localhost:5000/api/v1`) |
| `ssmts-web` | React 19 + Vite | Backoffice / Operator web portal |
| **`ssmts-mobile`** | **Native Android (Kotlin)** | **Prosumer + Grid Operator mobile app** |

---

## Features

### Prosumer mode
- **Account control** — self-registration with **NIC as the primary key** (BR-09 format
  validation), profile editing (name, phone, address) and account deactivation requests
  (blocked while active bookings exist — BR-10).
- **Local SQLite user management** — registered/logged-in users cached in a plain
  `SQLiteOpenHelper` database (`users` table, NIC primary key). No Room, no ORM.
- **Reservations** — book energy Export (sell) / Import (buy) slots within the next 7 days
  (BR-01), modify or cancel before the 12-hour cutoff (BR-02/BR-03), min 0.5 kWh (BR-05).
- **QR pass dispatch** — once approved, a server-signed (HMAC-SHA256, BR-12) transaction QR
  is rendered on-device with a 6-digit backup code and validity window.
- **Dashboard** — active/pending booking counts, completed transfers, energy exported and
  imported, net earnings, upcoming bookings.
- **Bookings list** — history / pending / upcoming tabs with free-text search
  (reservation no, station, status).
- **Nearby stations** — Google Maps view of grid nodes via the geospatial `nearby` API,
  with per-station pricing, hours, bays and a direct booking shortcut.

### Grid Operator mode
- Operator sign-in with node-scoped access (BR-11).
- **Scan** the prosumer's QR pass with the camera (or enter the backup code), **verify**
  against the server and transition the booking to InProgress (BR-12: signature, time
  window, single use).
- **Finalize** the transfer with start/end meter readings — server computes actual kWh and
  settlement value from the snapshotted unit price (BR-13).
- Approve / reject pending reservations and monitor daily station stats.

---

## Tech stack

- **Language:** Kotlin 2.1 · **UI:** XML views + Material 3 · **min SDK 26, target SDK 35**
- **Local DB:** raw `SQLiteOpenHelper` (local user management)
- **Networking:** Retrofit 2 + Gson + OkHttp (JWT bearer interceptor)
- **Maps:** Google Maps SDK + Fused Location
- **QR:** ZXing core (generation) + zxing-android-embedded (scanning)
- **Build:** Gradle 8.11 (wrapper included), AGP 8.7

---

## Prerequisites

- **JDK 17+** (tested with JDK 23) — `JAVA_HOME` must point to the JDK folder (no `\bin`).
- **Android SDK** with platform 35 (`ANDROID_HOME` set). Android Studio is *not* required.
- The **SSMTS backend** running at `http://localhost:5000`.

## Configuration

Create `local.properties` in the project root (never committed):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

The API base URL is `http://10.0.2.2:5000/api/v1` (emulator → host loopback), set in
`app/build.gradle.kts` (`API_BASE_URL`). For a physical phone, change it to your PC's LAN IP.

## Build & run (command line, no Android Studio)

```powershell
# 1. Build the debug APK
.\gradlew.bat assembleDebug

# 2. Start an emulator (any AVD with API 33+), e.g.:
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd ssmts

# 3. Install and launch
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.ssmts.mobile/.ui.auth.LoginActivity
```

Physical phone: enable **USB debugging**, plug in, then `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

## Demo flow

1. Register a prosumer (NIC e.g. `991234567V`) → sign in.
2. Book a slot at a station (map or Book Slot), operator approves (mobile operator mode or web portal).
3. Open the booking → **Show QR Pass**.
4. Operator: **Scan Transaction QR** → verify → enter meter readings → complete transfer.
5. Dashboard reflects energy totals and net earnings.

---


