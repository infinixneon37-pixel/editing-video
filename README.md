# Editing Video Android

A lightweight Android video editing application built using Native Java and Android SDK.

![Platform](https://img.shields.io/badge/Platform-Android-brightgreen)
![API](https://img.shields.io/badge/API-30%2B-blue)
![Java](https://img.shields.io/badge/Java-Native-orange)
![Gradle](https://img.shields.io/badge/Build-Gradle-green)

---

## Overview

Editing Video Android is a native Android application designed to provide a simple and lightweight video editing interface.

The project focuses on:

- Native Android development
- Lightweight user interface
- Java-based implementation
- Fast performance
- Minimal dependencies
- Easy GitHub Actions integration
- Android Studio compatibility

---

## Features

### Native Android Application

Built entirely using Android SDK and Java without cross-platform frameworks.

### Modern User Interface

Simple and responsive interface optimized for Android devices.

### Video Editing Workflow

Provides a foundation for implementing video processing and editing features.

### Lightweight Architecture

Minimal project structure for easier maintenance and faster builds.

### GitHub Actions Ready

Can be built automatically using GitHub Actions CI/CD workflows.

---

## Project Structure

```text
app/
└── src/main/
    ├── AndroidManifest.xml
    ├── java/
    │   └── com/editingvideo/app/
    │       └── MainActivity.java
    │
    └── res/
        ├── drawable/
        │   ├── ic_notification.png
        │   └── logo.png
        │
        ├── layout/
        │   └── activity_main.xml
        │
        ├── mipmap-hdpi/
        ├── mipmap-mdpi/
        ├── mipmap-xhdpi/
        ├── mipmap-xxhdpi/
        ├── mipmap-xxxhdpi/
        │
        └── values/
            ├── colors.xml
            ├── strings.xml
            └── themes.xml
```

---

## Requirements

- Android 11 (API 30) or newer
- Android Studio
- Gradle
- JDK 17

---

## Build System

This project uses:

- Gradle Wrapper
- Android SDK
- Android Studio
- GitHub Actions

---

## Build Locally

Grant execution permission:

```bash
chmod +x gradlew
```

Build Debug APK:

```bash
./gradlew assembleDebug
```

Build Release APK:

```bash
./gradlew assembleRelease
```

---

## Output APK

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release APK:

```text
app/build/outputs/apk/release/app-release.apk
```

---

## Installation

Clone repository:

```bash
git clone https://github.com/alitatara5-gif/editing-video.git
```

Open the project using Android Studio and sync Gradle.

---

## Technologies Used

- Java
- Android SDK
- AndroidX
- Gradle
- GitHub Actions

---

## Resources

Main files:

```text
app/src/main/java/com/editingvideo/app/MainActivity.java
app/src/main/res/layout/activity_main.xml
app/src/main/AndroidManifest.xml
```

---

## Development Goals

This project is intended to provide:

- Native Android performance
- Clean architecture
- Easy maintenance
- Fast build process
- Expandable video editing features

---

## License

This project is licensed under the GPL-3.0 License.

See the LICENSE file for details.

---

## Disclaimer

This software is provided for educational and development purposes.

Users are responsible for ensuring compliance with applicable laws, copyright regulations, and platform policies when processing or editing media content.
