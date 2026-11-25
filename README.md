# Seamwork Studio

Android-first garment design studio for the Samsung Galaxy Tab S10 Lite and S Pen.

## Android target

- Android 16 / API 36 is the primary target.
- Landscape-first tablet interface; minimum Android API 24.
- Local project data and reference metadata use SQLite.
- Verify S Pen behavior, photo selection, rotation, edge-to-edge layout and PDF output on a physical Android 16 tablet before release.

## Run locally

1. Install Node.js LTS.
2. Run `npm install`.
3. Run `npx expo start`.
4. For a native Android development build, run `npx expo run:android` with the Android toolchain installed.

## Current foundation

- Create, open and delete garment projects.
- Five workspaces: Inspiration, Sketchbook, Pattern Designer, Fabric Layout, and Print & Export.
- SQLite storage for projects and per-project inspiration references.
- Android 16 target SDK configuration.

## Roadmap

1. Complete Inspiration: photo import, Pinterest/web references and collections.
2. S Pen sketchbook with multiple views and adjustable reference overlays.
3. Vector pattern drafting with millimetre dimensions, grid and seam allowance.
4. Fabric tagging, nesting and yardage estimates.
5. Letter/A4/A0 tiled PDF export, projector mode and calibration sheets.

Pattern geometry will be stored in millimetres, independently of screen zoom and grid spacing.
