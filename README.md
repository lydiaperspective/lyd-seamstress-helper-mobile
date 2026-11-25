# Seamwork Studio

A tablet-first garment design and pattern-making studio for Android, designed around the Samsung Galaxy Tab S10 Lite and S Pen.

## Current foundation

- Expo + React Native + TypeScript
- Landscape-first tablet layout
- Local SQLite project storage
- Create, open and delete garment projects
- Five project workspaces: Inspiration, Sketchbook, Pattern Designer, Fabric Layout, and Print & Export

## Run locally

1. Install Node.js (LTS).
2. Install dependencies: `npm install`
3. Start Expo: `npx expo start`
4. Open the project with Expo Go for an initial preview, or build a development APK for device testing.

For a native Android development build, configure the Android toolchain and run `npx expo run:android`.

## Planned milestones

1. Inspiration board: image imports, URL previews, Pinterest links, collections.
2. Sketchbook: multi-page drawing, reference overlays, opacity and layer controls.
3. Pattern designer: millimetre-based vector geometry, editable grid, curves, seam allowance, boolean shape operations, custom fabric tags.
4. Fabric layout: manual placement, automatic nesting, fabric usage estimates.
5. Print & export: Letter, A4, A0, projector output, tiled PDF and printer calibration page.

## Engineering principles

- Store geometry in physical units (millimetres), never screen pixels.
- Keep zoom and grid display separate from the actual canvas dimensions.
- Preserve editable vector geometry through layout and export.
- Treat printer calibration and scale verification as first-class print features.
