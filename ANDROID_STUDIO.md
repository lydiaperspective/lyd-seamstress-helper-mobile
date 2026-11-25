# Native Android Studio project

Seamwork Studio is a native Android application using **Kotlin and Jetpack Compose**.

## Open in Android Studio

1. Clone or download this repository.
2. In Android Studio, choose **Open** and select the repository root (the folder containing `settings.gradle.kts`).
3. Install Android SDK Platform 36 when prompted.
4. Use JDK 17 for Gradle and allow Gradle sync to finish.
5. Select an Android 16 emulator or connect your tablet with USB debugging enabled.
6. Press **Run**.

The application module is `app`; application ID: `com.lydiaperspective.seamworkstudio`.

## Current state

The Compose app includes a tablet-oriented project dashboard, create/delete interactions, and navigation into all five garment workspaces. Project data currently lives in Compose state and does not persist across restarts. Inspiration management, S Pen canvas tools, vector pattern drafting, fabric nesting and PDF export remain future implementation work.

The old Expo/React Native prototype is retained as a legacy reference. Android Studio should open the repository root and build the native `app` module.
