# Seamwork Studio

A tablet-first garment design studio for Android, built natively with **Kotlin and Jetpack Compose**. Primary target: Android 16 and Samsung Galaxy Tab with S Pen.

## Open in Android Studio

1. Run **Actions → Build native Android app → Run workflow** on GitHub once to generate the Gradle wrapper and build a debug APK.
2. Clone/download the updated repository and open its root (the folder containing `settings.gradle.kts`) in Android Studio.
3. Install Android SDK Platform 36 if prompted, use JDK 17, and allow Gradle sync.
4. Run the `app` configuration on an Android 16 emulator or connected tablet.

See [ANDROID_STUDIO.md](ANDROID_STUDIO.md).

## Native stack

- Kotlin + Jetpack Compose + Material 3
- Compile SDK 36 / Target SDK 36 / Minimum SDK 24
- Application ID: `com.lydiaperspective.seamworkstudio`
- Java 17

Each garment project will contain five workspaces: Inspiration, Sketchbook, Pattern Designer, Fabric Layout, and Print & Export. The native starter currently provides the project dashboard, create/delete interactions, and entry screens for all five workspaces. Persistent storage and workspace editing tools are next.

The previous Expo/React Native prototype remains temporarily as a legacy reference. The native Android app in `app/` is now the active implementation.
