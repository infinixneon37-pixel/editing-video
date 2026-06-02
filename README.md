# BIGO Native Recorder

Modern Android Native WebView application for monitoring, browsing, and recording BIGO live streams using integrated FFmpeg processing.

![Platform](https://img.shields.io/badge/Platform-Android-brightgreen)
![API](https://img.shields.io/badge/API-30%2B-blue)
![Java](https://img.shields.io/badge/Java-Native-orange)
![FFmpeg](https://img.shields.io/badge/FFmpeg-Integrated-red)

---

## Overview

BIGO Native Recorder is a lightweight Android application built with native Java and Android WebView technology.

The application is designed for:

- BIGO live monitoring
- Stream session detection
- Native recording automation
- Integrated FFmpeg processing
- Lightweight Android deployment
- Remote GitHub Actions build workflow

This project focuses on simplicity, performance, and clean native integration without unnecessary dependencies.

---

## Features

### Native Android WebView
Fast and lightweight WebView implementation optimized for Android devices.

### FFmpeg Recording Engine
Integrated FFmpeg processing for native stream recording and media handling.

### Live Session Monitoring
Automatically detects and monitors active BIGO live sessions.

### Background Service
Uses Android foreground/background service architecture for stable monitoring.

### Offline Handling
Displays offline fallback page when network connectivity is unavailable.

### Minimal Native Architecture
Clean project structure optimized for GitHub remote builds and lightweight APK generation.

---

## Project Structure

```text
app/
 └── src/main/
     ├── java/com/webview/myapplication/
     │    ├── MainActivity.java
     │    ├── LiveMonitorService.java
     │    ├── RecorderTask.java
     │    └── RecorderUtils.java
     │
     ├── assets/
     │    └── offline.html
     │
     └── res/
```

---

## Requirements

- Android 11+
- API Level 30+
- Internet connection
- FFmpeg compatible streams

---

## Build System

This project uses:

- Gradle Wrapper
- GitHub Actions
- Remote Android build pipeline

APK builds are automatically generated through GitHub Actions workflow.

---

## GitHub Actions

Automatic build workflow includes:

- Checkout repository
- Setup JDK 17
- Build Debug APK
- Upload APK artifact

Workflow location:

```text
.github/workflows/build.yml
```

---

## Installation

Clone repository:

```bash
git clone https://github.com/alitatara5-gif/web-bigo.git
```

Open using Android Studio or build remotely using GitHub Actions.

---

## Build Locally

```bash
./gradlew assembleDebug
```

Generated APK:

```text
app/build/outputs/apk/debug/
```

---

## Technologies Used

- Java
- Android SDK
- Android WebView
- FFmpegKit
- Gradle
- GitHub Actions

---

## Notes

This project is optimized for:

- Lightweight deployment
- Native Android performance
- Remote CI/CD builds
- Minimal dependencies
- Simple maintenance workflow

---

## Disclaimer

This project is intended for educational and development purposes.

Users are responsible for complying with platform policies, copyright regulations, and local laws regarding media recording and stream usage.

---

## License

GPL-3.0 License
