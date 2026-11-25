# Native Android Studio project

Seamwork Studio is a native Android application using **Kotlin and Jetpack Compose**.

## Open in Android Studio

1. Open the repository on GitHub and select **Actions**.
2. Run **Build native Android app** using **Run workflow**. This generates the Gradle wrapper, builds a debug APK, and saves the APK as an artifact.
3. Clone or download the updated repository after the workflow completes.
4. In Android Studio, choose **Open** and select the repository root (the folder containing `settings.gradle.kts`).
5. Install Android SDK Platform 36 when prompted and use JDK 17.
6. Select an Android 16 emulator or connect your tablet with USB debugging enabled, then press **Run**.

You can also download `seamwork-studio-debug-apk` from the workflow run to install the test build.

The application module is `app`; application ID: `com.lydiaperspective.seamworkstudio`.

## Current state

The Compose app includes a tablet-oriented project dashboard, create/delete interactions, and navigation into all five garment workspaces. Project data currently lives in Compose state and does not persist across restarts. Inspiration management, S Pen canvas tools, vector pattern drafting, fabric nesting and PDF export remain future implementation work.

The old Expo/React Native prototype is retained as a legacy reference. Android Studio should open the repository root and build the native `app` module.
