# DevProbe

**DevProbe** is an on-device Android developer toolkit for reverse-engineering APKs, catching crashes automatically, and profiling app resource usage — all analysis runs locally, nothing is ever uploaded.

Built with Jetpack Compose and Material 3 Expressive.

## Features

### 🔍 APK Inspector
Import any APK and get a full breakdown, entirely offline:
- **Overview dashboard** — security score gauge, KPI cards, findings-by-severity chart, size-breakdown donut chart
- **Manifest** — activities, services, receivers, providers, intent filters
- **Permissions** — categorized by risk (normal / dangerous / signature / special)
- **Security findings** — a deterministic rule engine flags cleartext traffic, exported components, weak crypto, debuggable builds, and more
- **Signing & certificates** — v1/v2/v3/v3.1 scheme detection, certificate chain details, expiry/self-signed/debug-cert flags
- **Size breakdown** — DEX / native libs / resources / assets, largest files
- **Bytecode analysis** — class/method/field counts, multidex detection, obfuscation signal
- **File explorer** — browse the APK's zip contents with a **Text** (syntax-highlighted, line-numbered), **Hex**, **Raw**, or **Image** preview depending on file type
- **Strings & secrets** — scans DEX/resource/native strings for hardcoded API keys, JWTs, private keys, URLs, and emails
- **Class & native library browsers** — inspect DEX classes/methods and ELF native libraries (exported/imported symbols, architecture)
- **PDF export** — generate a complete, formatted report and save it straight to Downloads

### 🐛 Crash Log Analyzer
- Paste or import a crash log / stack trace and get structured exception, thread, and stack-frame parsing
- **Automatic background detection** — grant a one-time ADB permission and DevProbe tails the system log, auto-detecting and saving any app's crash as a session (no manual copy-paste)
- On-device AI explanations for crashes and security findings (fully local template engine, no cloud calls)
- Export findings to PDF

### 📊 App Profiler
Per-app resource usage over Today / 7 days / 30 days:
- Storage (app / data / cache size)
- Network usage (Wi-Fi / mobile)
- Foreground usage time & launch count

Gated behind the standard "Usage access" Settings toggle — no root, no ADB.

### 📱 Device Diagnostics
OS version, security patch level, hardware, memory, and storage at a glance.

## Design principles

- **Local-first** — no backend, no telemetry, no root, no accessibility-service tricks. Every analysis runs on-device.
- **Deterministic analysis, optional AI interpretation** — the security rule engine produces facts; AI (when enabled) only explains them, never invents them.
- **No bottom nav** — Home is a single navigation hub; every other screen is a detail view reached from it and left via the back arrow.
- Every binary format (AXML, APK signing block, DEX, ELF, resources.arsc) is parsed from scratch — no third-party reverse-engineering libraries.

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3 Expressive — `MaterialExpressiveTheme`, `MotionScheme.expressive()`)
- **Room** for session persistence
- **kotlinx.serialization** for structured result storage
- **Navigation Compose**
- Hand-rolled Canvas-based charts (donut, bar, gauge) and a PDF renderer (`PdfDocument`/`Canvas`) — no charting or PDF library dependency
- Manual DI via a single `AppContainer` (no Hilt/Koin — the app is small enough not to need it)

## Requirements

- Android Studio (latest stable)
- JDK 17+
- Android SDK 37 (compile/target), min SDK 24

## Getting started

```bash
git clone https://github.com/prateekthakur272/DevProbe.git
cd DevProbe
./gradlew assembleDebug
```

Or open the project in Android Studio and run the `app` configuration on a device/emulator.

### Running tests

```bash
./gradlew testDebugUnitTest
```

## Permissions

| Permission | Why |
|---|---|
| `READ_LOGS` | Powers automatic background crash detection. Protected on modern Android — granted via a one-time `adb shell pm grant` command shown in Settings, not requestable in-app. |
| `PACKAGE_USAGE_STATS` | Powers the App Profiler. Granted via the standard "Usage access" system Settings screen. |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*` | Required for the background crash-monitoring service and its status/alert notifications. |
| `WRITE_EXTERNAL_STORAGE` (≤ Android 9) | Needed to save exported PDF reports to Downloads on pre-scoped-storage devices only. |

None of these are required to use the core APK inspection or manual log analysis features.

## Project structure

```
app/src/main/java/dev/prateekthakur/devprobe/
├── data/            # Parsers, analyzers, repositories, PDF/report generation
├── domain/          # Models and use cases
├── presentation/    # Compose screens, ViewModels, shared UI components
└── di/              # Manual dependency container
```

## Author

**Prateek Thakur** — [github.com/prateekthakur272](https://github.com/prateekthakur272)
