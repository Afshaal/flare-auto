# Decisions log

Ambiguous choices made during the optimisation / release-preparation pass, with the conservative option taken.

| ID | Decision | Why |
|---|---|---|
| D-01 | Work in `flare-auto-profiles-local/`, not `flare-auto-kotlin-6677f69/`. | The brief names the older snapshot, but all maps / widgets / profiles / routing work lives in `flare-auto-profiles-local/`. Confirmed by the owner before any change. |
| D-02 | Device measurements use only `install -r`, `am start`, `dumpsys`, `top`, `logcat`. The drawer jank run from the baseline (which used `adb shell input`) is not repeated. | Ground rule 6 lists the allowed adb commands; `input` and `push` are not on it. The exact commands are given in the report for the owner to run. |
| D-03 | ~~Baseline device numbers are the ones recorded by the previous session.~~ **Superseded by D-17.** | — |
| D-04 | Deleted `CustomizeActivity` and `DockEditorActivity`. | Declared but never launched by the release app (only a debug harness opened one); `GridEditorActivity` replaced both. Removing them removed most of the duplicated dock/split/`getChildAt` code. |
| D-05 | Item 16 (preview hack): only the static `HomeActivity.instance` remained; replaced by `HomeActivity.requestRebuild()` (a counter Home checks on resume). | The grid editor already rendered through a standalone `HomeCards` against a draft copy of the preferences. `prefs.clear()` does not notify listeners on API 28, so Settings › Reset needs an explicit signal. |
| D-06 | Kept a quirk: a saved dock without the drawer slot gets the five legacy slots appended. | Existing behaviour; pinned in `DockConfigTest` rather than "fixed". |
| D-07 | Pre-seeded drawer tiles (Files, Browser, stock head-unit screens, projection apps) are shown only when installed. | Requested in item 20. Identical on the Civic when those apps are present; previously a missing app showed a tile that only toasted an error. |
| D-08 | Screensaver vehicle label uses `letterSpacing = 0.25` for every name instead of the literal "C I V I C". | Requested in item 21; 0.25 em ≈ one Roboto space, so "CIVIC" looks the same. Custom names now get the same spacing. |
| D-09 | `simple-icons-index.json` kept next to `simple-icons-picker.json`. | It is not derivable: 140 of its 3,602 keys are aliases absent from the picker list. |
| D-10 | Kept one `allNetworks` call (in `StatusIcon.networkUp`) with a justified `@Suppress`. | Callback-only state would show "disconnected" for a moment on every Home rebuild; there is no synchronous replacement below API 31. |
| D-11 | Kept `Gravity.LEFT/RIGHT` (12 RtlHardcoded), `File.usableSpace` (3 UsableSpace), fixed landscape orientation and `getIdentifier` (DiscouragedApi). | START/END would mirror layouts under an RTL locale such as Urdu; `getAllocatableBytes` would change when map downloads are allowed; the unit is landscape-only; 3,470 icons are looked up by name. All would change behaviour. |
| D-12 | GraphHopper and Mapsforge are kept whole by R8 (`-keep`). | The router only runs with the navigation pack installed, so a shrinking mistake would show up first in the car. Library code around them is still shrunk (43,006 → 12,630 method references). |
| D-13 | `org.json` added as a **test-only** dependency. | `android.jar` ships stubs, so JSON code (dock migration, pack `build-info.json`) cannot be unit-tested otherwise. Not packaged in the APK. |
| D-14 | Navigation pack lives in `<external files>/navigation/<region>/`, verified against `checksums.txt` once and cached until files change. | Brief B9. The previous code unzipped `assets/maps/lahore-navigation.zip`, which never existed. |
| D-15 | The existing, unwired `OfflineGuidance` panel is shown on the full-screen offline map (`OfflineMapActivity`) only. | "Finish up to what the existing code clearly intends" without redesigning Home: the panel was written for a map host (`paintRoute`, `center`). |
| D-16 | Added debug-only `SmokeActivity` and `NavigationCheckActivity`; check harnesses also log to logcat. | Lets every screen and the navigation pack be checked with `am start` + `logcat` only (D-02). Not in release builds. |
| D-17 | Final device comparison is an A/B on the same emulator state (baseline APK then new APK, 7 cold starts each). | The earlier baseline was recorded in a different emulator state (profile prompt / process history), so comparing against it would mislead. |
| D-18 | Ran the debug `FeatureChecksActivity`, which creates and selects a "Feature test" profile, then its `cleanup` step. | The harness is part of the project's checks; `cleanup` restored the driver registry from its own backup afterwards. |
| D-19 | The Wagah regression trip starts 1.2 km before the border gate; a must-fail Attari case was added. | The original point snapped to the trunk road on the Indian side of the border barrier, which GraphHopper correctly never routes through. |
