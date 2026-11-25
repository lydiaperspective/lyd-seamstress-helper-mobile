# Android Studio testing

This is an Expo-managed app. The native Gradle project is generated from the app configuration by GitHub Actions.

1. Open the repository on GitHub and select **Actions**.
2. Select **Prepare Android Studio project**.
3. Select **Run workflow** on the `main` branch.
4. Wait for the workflow to finish. It runs Expo prebuild, builds a debug APK, and commits the generated `android/` directory.
5. Pull the latest `main` branch or download the updated repository ZIP.
6. In Android Studio, choose **Open** and select the repository's **android** folder.
7. Allow Gradle sync to finish. Connect the Android 16 tablet with USB debugging enabled, select it as the run target, and press **Run**.

The workflow run also has a downloadable `seamwork-studio-debug-apk` artifact for installing the test APK directly.

Use JDK 17. Accept Android Studio prompts to install any missing Android SDK or Gradle components. Test S Pen input and photo access on the actual tablet; those behaviors cannot be verified by a cloud build alone.
