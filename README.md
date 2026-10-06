# TDEE

A minimalist, Material You TDEE tracker for Android. Log your weight and calories each day, and it
works out your real energy expenditure (TDEE) from your own data, then tells you how much to eat to
hit your weekly weight goal.

**Download:** grab the APK from the [latest release](https://github.com/97ryan/TDEE/releases/latest).

## Features

- **Home**: pick a day, log weight and calories, and see *Need to eat*, *TDEE*, *Calorie change
  needed* and your current *weight trend* per week.
- **Progress graph**: weight (left axis), calories and TDEE (right axis), each with daily dots and a
  smoothed trend line. Tap a series toggle to cycle *line + dots → dots → line → hidden*. Tap or drag
  on the graph to read values for any day. Ranges: 1M / 3M / 6M / 1Y / All.
- **Progress table**: every logged day with its TDEE. Tap a row to edit or delete it.
- **Settings**: weekly goal, days used for calculations, algorithm, calculation start date, default
  calorie value, theme (system / light / dark), dynamic colour, graph smoothing, kg/lb, kcal/kJ.
- **CSV import/export** in the original app's format.
- Material 3 with dynamic colour (Android 12+), themed icon, edge-to-edge, animated transitions.
- Fully offline. No accounts, no analytics, no Google Play Services.

## How TDEE is calculated

Energy balance: about 7716 kcal is stored or burned per kilogram of body weight (3500 kcal/lb).

```
TDEE          = average intake − 7716 × weight slope (kg/day)
Need to eat   = TDEE + goal (kg/week) × 7716 / 7
Change needed = Need to eat − average intake
```

The weight slope is a least-squares line through your weigh-ins, so a single heavy or light morning
barely moves it. Days missing a weigh-in or calories are simply skipped.

- **Classic** (default) uses the selected day plus the previous *N* days (N = "Days used for
  calculations", 21 by default). It was reverse-engineered from the original app's exported data and
  reproduces its TDEE values to within a few kcal.
- **Smoothed** fits the last 6 × N days with exponentially decaying weights (half-life N days). It
  moves about five times less from day to day and doesn't jump when an unusual day leaves the window.

## CSV format

```
"Date","Kilograms","Calories","TDEE"
"2026-10-05","79.7","","2925"
"2026-10-04","80.2","2500","2883"
```

Export always writes this format (with `Pounds` / `Kilojoules` headers if you use those units).
Import accepts it, plus headerless `yyyy-MM-dd,weight,calories` rows, unquoted fields, `;` or tab
delimiters, and an optional TDEE column (ignored, because it's recalculated). Importing replaces
entries on matching dates and keeps everything else.

## Building

Requires JDK 17 and the Android SDK (API 35).

```
./gradlew testDebugUnitTest assembleRelease
```

Every push is built and unit-tested by GitHub Actions (`.github/workflows/build.yml`), and the APK
is published to the GitHub Release for the current `versionName` (e.g. `v1.0.0`). Bump `versionName`
(and `versionCode`) in `app/build.gradle.kts` to start a new release.

Release builds are signed with `keystore/dev.jks` (password `tdee-dev`) so that every build installs
over the previous one. To sign with your own key, set `TDEE_KEYSTORE`, `TDEE_KEYSTORE_PASSWORD`,
`TDEE_KEY_ALIAS` and `TDEE_KEY_PASSWORD`.

## F-Droid readiness

Only open-source AndroidX/Jetpack dependencies from Google Maven and Maven Central. No proprietary
libraries, trackers or network access, and the dependency-info blob is disabled. Before submitting:
pick a final `applicationId`, add a licence, add `fastlane/metadata`, and exclude `keystore/`
(F-Droid builds and signs from source).
