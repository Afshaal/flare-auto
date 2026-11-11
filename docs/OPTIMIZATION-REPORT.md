# Optimisation report

## Baseline (commit `79e345e`, before any change)

### Build
| Metric | Value |
|---|---|
| Clean `assembleDebug assembleRelease lintDebug testDebugUnitTest` | success, 61 s |
| Kotlin compiler warnings (unique) | 42 (39 main sources, 3 debug harnesses) — all deprecations or missing `@Deprecated` on overrides |
| Lint | 0 errors, 5,319 warnings: 3,497 UnusedResources, 1,701 VectorPath, 24 ApplySharedPref, 22 DiscouragedApi, 14 RtlHardcoded, 11 ViewConstructor, 5 ClickableViewAccessibility, 4 UsableSpace, 2 ObsoleteSdkInt, 2 GradleOverrides, 1 RedundantLabel, 1 ExportedReceiver |
| Unit tests | 12 / 12 pass (6 classes) |
| Debug APK | 10,089,938 B (download estimate 9,506,058 B) |
| Release APK (unsigned, no R8, no resource shrinking) | 9,136,367 B (download estimate 8,703,737 B) |
| Method references | debug 43,760 (3 dex files) · release 43,006 (1 dex) |
| Resources | 3,498 drawables (14 MB `res/`), 3.5 MB `assets/` (incl. 3.3 MB `world.map`) |
| Source | 87 Kotlin files, ~8,900 lines |

### Device (BlueStacks, Android 9, `127.0.0.1:5555`; release build of `79e345e` signed with the local key)
| Metric | Value |
|---|---|
| Cold start `am start -S -W` (5 runs) | 22,892 ms (first run after install, includes dex optimisation), then 157 / 126 / 148 / 120 ms → median of runs 2–5: **137 ms** |
| Idle on Home: total PSS / Java heap / native heap | 46,793 KB / 2,332 KB / 6,688 KB |
| App drawer open: total PSS / Java / native | 50,664 KB / 3,976 KB / 10,544 KB |
| Back on Home after drawer: total PSS / Java / native | 51,860 KB / 4,348 KB / 10,392 KB |
| Idle CPU on Home (`top`, 3 samples) | 0.0 % |
| Drawer open + 8 page swipes (`gfxinfo`) | 295 frames, 1 janky (0.34 %), p50 5 ms, p90 14 ms, p99 16 ms |
| Home rebuild after a settings change | not measured (no timing hook in the app) |

The baseline device run used `adb shell input` for the drawer swipes; see `DECISIONS.md` D-02.

## After (commit fdfa56d and later)

| Metric | Baseline | After |
|---|---|---|
| Kotlin compiler warnings (unique) | 42 | 0 |
| Lint | 0 errors / 5,319 warnings | 0 errors / 1,797 warnings (1,689 are long icon paths) |
| Unit tests | 12 | 41, all pass |
| Release APK (unsigned) | 9,136,367 B | 7,659,987 B (R8 + resource shrinking) |
| Release method references | 43,006 | 12,630 |
| Signed release APK | 9,428,608 B | 7,948,024 B (−15.7 %) |

A/B on the same BlueStacks state (`docs/metrics-ab.txt`; first single run in `docs/metrics-after.txt`, 7 cold starts each, median of runs 2–7):
cold start 196 ms → 192 ms; idle PSS 50,645 → 51,005 KB (single sample); idle CPU 0 % both. No measurable
change on this emulator; the earlier 137 ms / 46.8 MB baseline was taken in a different emulator state.
Drawer jank was not re-measured (needs `adb shell input`, outside the allowed commands).

To measure the drawer yourself (uses `input`):

```sh
adb shell dumpsys gfxinfo app.flare.auto reset
# open the drawer, swipe 8 pages, then:
adb shell dumpsys gfxinfo app.flare.auto | grep -E 'Total frames|Janky|percentile'
adb shell dumpsys meminfo app.flare.auto | grep -E 'TOTAL|Java Heap|Native Heap'
```

Home rebuild time after a settings change has no timing hook and was not measured.

### Verified on BlueStacks (Android 9)
* Every screen cold-starts and resumes with an empty crash buffer, in both the normal debug build and the debug
  build shrunk with the release R8 rules (`-Pflare.minifyDebug=true`).
* The existing check harnesses pass: Redesign 8/8 (editor drafts, profile isolation), Optimization 8/8
  (forecast cache, icon roles, legacy wallpaper indexes), Limit 4/4 (dialogs, deferred images), FeatureChecks
  (profile and layout isolation).
* Missing navigation pack: "Offline navigation pack not installed", no crash.
* Every drawable looked up by name (3,470 Simple Icons + brand map + 3 static) is present in the release APK.

## Items

Commits (branch `optimize/claude-code`): `9ac3e15` docs/baseline · `5142c8a` consolidation · `a683d72`
navigation pack · `2014a89` tests · `6735308` release build · `fdfa56d` lint · `b0d5ff5` docs.
Lahore tools (branch `pipeline/claude-code`): `59b5682` pipeline · `7a7af49` validation · `36a254f` docs.

| # | Item | Status | Commit | Notes |
|---|---|---|---|---|
| 1 | dp/px helper | Done | 5142c8a | `Context.dp()` in `Ui.kt`; 9 local copies removed. |
| 2 | Background work | Done | 5142c8a | `Background.load()` delivers only to a live Activity; drawer, app picker, hidden apps, icon picker, icon-pack chooser, image import, forecast, async images. The update checker keeps its interruptible thread; routing and map transfers keep their single ordered threads (by design). |
| 3 | Launchable-app catalog | Done | 5142c8a, 2014a89 | `AppCatalog`: queried once, labels precomputed, invalidated on package add/remove/change and locale change. Icon-pack lookup still uses `getLaunchIntentForPackage` (different query; unchanged). |
| 4 | App icon loading | Done | 5142c8a | One `AppIcons` cache (~4 MB, lazy, off-main-thread). Hidden-apps no longer holds every icon; picker's private cache and executor removed. |
| 5 | Label/icon from stored component | Done | 5142c8a | `AppLookup.label` / `AppIcons.get`. |
| 6 | Launch by component | Done | 5142c8a | `AppLookup.launch/start/launcherIntent`; each call site keeps its exact intent shape. |
| 7 | `getIdentifier` per bind | Done | 5142c8a | Drawer resolves once per load; picker once per item; CarPlay/Android Auto marks are `R.drawable` references. |
| 8 | Network/Bluetooth status icons | Done | 5142c8a, fdfa56d | `StatusIcon` base: shared paint, glyph transform, guarded unregister (Wi-Fi previously unregistered without a guard). |
| 9 | `getChildAt` index hacks | Done | 5142c8a | `ValueRow`/`ToggleRow`/`StepperRow`, `TopBar.date`, thumbnail and card-frame fields. |
| 10 | Settings refresh switch | Done | 5142c8a, 2014a89 | Each row has a value provider (`SettingsValues`); the 35-case `when` and its dead "Screen Off wallpaper" case are gone. |
| 11 | Dock item types | Done | 5142c8a | `DockAction` enum (stored ids unchanged); `DockOrder` merged; legacy migration in one place. |
| 12 | Dock icon-size mapping | Done | 5142c8a | `DockConfig.iconCeiling` + `ICON_SIZES`; labels/mono read once per dock. `min(32, ceiling)` did not exist (N/A). |
| 13 | Split/layout constants | Done | 5142c8a | `LayoutPrefs` (35/65/58, `MAX_APP_SHORTCUTS` = 8) used by Home, editor, app widget screen. |
| 14 | Preference access | Done | 5142c8a | `Keys` (values unchanged, pinned by `KeysTest`), typed accessors; `HOME_UI` set checked against every key Home reads (47). |
| 15 | Magic numbers | Done | 5142c8a | Key codes, playback state, paint flags, 53 layout params, permissions, request codes. Gravity 17 / orientation 1 / Toast 0 / motion 0/1 were not present (N/A). |
| 16 | Layout-editor preview hack | Done | 5142c8a | Already rendered through standalone `HomeCards`; static `instance` removed (D-05). |
| 17 | No-op nesting | Done | 5142c8a | Media-card wrapper column and legacy split wrapper rows flattened. |
| 18 | Dead code | Done | 5142c8a | Two unreachable activities, `DockOrder`, `UiFlags`, 4 unused glyph kinds, dead package branches, `appIcon`, `maxSlots`, unused fields/imports, `EditorDraft` helpers. Dashline headers kept. |
| 19 | Glyph view in its own file | Done | 5142c8a | `NavGlyph.kt`. |
| 20 | Device-specific packages | Done | 5142c8a | `KnownApps`; no other file names a package. Pre-seeded tiles only when installed (D-07). |
| 21 | Personal/branding defaults | Done | 5142c8a | `Defaults` (CIVIC, RS); `CivicBadge` → `TrimBadge`; letter-spacing for any name (D-08); empty values hide labels. No Lahore solar default or ", Lahore" string existed (N/A). |
| 22 | Wallpaper decoding | N/A (already fine) | — | `CustomImages` already had an 8 MB LRU keyed by file, mtime, size and target size, decoded off the main thread. |
| 23 | Draw-path allocation | Done | 5142c8a | Editor placement loop no longer allocates per cell per frame; `MediaGlyph` path built once. Other views already allocated once. |
| 24 | Per-second work | Done | 5142c8a | Elapsed text only when the second changes; total only when duration changes; `formatMinutes` without `String.format`. |
| 25 | Drawer grid | Done / Skipped part | 5142c8a | Page size once, prefs and icon ids resolved at load. Reusing cell views: skipped (the paging animation builds the incoming page separately; risk outweighs gain). |
| 26 | Solar/location | Done | 5142c8a | Retry-while-unresolved and resume refresh were already present; removed the undeclared coarse-location check and `!!` parsing. |
| 27 | Themed dialog | Done | 5142c8a | Double styling did not exist (N/A); repeated `UiKit` allocations removed. |
| 28 | Wallpaper tables | Done | 5142c8a | One preset table, `flip()`, `CUSTOM_INDEX`; "Accent wave" label; indexes pinned by tests. |
| 29 | Duplicated data | Done | 5142c8a, fdfa56d | 25 `brand_*` drawables (byte-identical to `si_*`), accent-gradient copy, dock name arrays, manifest SDK/version duplicates. Icon index JSON kept (D-09). |
| 30 | Kotlin idioms | Done in touched code | 5142c8a… | ~30 files rewritten idiomatically. Untouched dense files (e.g. `GridEditorActivity`, `OfflineGuidance`, `GridBoard`) were only edited where needed, not restyled. |
| 31 | Warnings | Done | 5142c8a, fdfa56d | Kotlin 42 → 0. Lint 5,319 → 1,797; remainder documented (D-10, D-11). |
| 32 | R8 / resource shrinking | Done | 6735308 | Release 9.14 → 7.66 MB; by-name drawables verified; shrunk build exercised on device. Assets-based icon loading not proposed: the vector set did not hurt cold start in the A/B. |
| 33 | Release signing | Done | 6735308 | `keystore.properties` or `FLARE_*` env; unsigned otherwise. |
| 34 | Tests | Done | 2014a89 | 12 → 41: location-independent solar, keys, dock migration, layout/legacy keys, wallpaper table, settings values, app catalog rules, navigation pack. |
| 35 | README / .gitignore / credits | Done | 9ac3e15, b0d5ff5, 36a254f | |
| 36 | Licensing | Done | b0d5ff5 | `docs/LICENSING.md`; OSM credit added to destination search; Open-Meteo credit added. License left to the owner. |
| 37 | Secrets scan | Done | — | Both repos and full history: no keystores, passwords, tokens, keys or user paths. Notes: `local.properties` (git-ignored) holds a user path; the debug harness and `versions.properties` contain Lahore city-centre coordinates (not personal addresses). |
| 38 | Rename checklist | Done | b0d5ff5 | `docs/RELEASE-CHECKLIST.md`. |
| B1–B12 | Lahore pack | Done except on-device timing | 59b5682, 7a7af49, a683d72, 36a254f | See `flare-lahore-tools/README.md`. |

### Not done because it would change behaviour
* `Gravity.LEFT/RIGHT` → `START/END` (would mirror layouts in RTL locales).
* `getAllocatableBytes` for map downloads (changes when downloads are allowed).
* "Fixing" the dock quirk in D-06.

## On-device test checklist (Civic)

1. **Install over the current app** (same key) and open Home: same layout, wallpaper, dock, clock style, vehicle
   name and badge as before; Settings values unchanged (theme, scales, saver options, app shortcuts).
2. **Profiles:** switch driver; each keeps its own layout and images.
3. **Home editor:** open, move/resize a card, change wallpaper to a custom image, Save; cancel discards.
4. **Drawer:** all apps present, hidden apps hidden, icon pack and per-app icons/colours/images correct; Audio,
   Radio, Bluetooth, Car settings tiles present; paging smooth.
5. **Dock:** each built-in action and app shortcut opens; labels/mono/size options apply.
6. **Wallpapers:** every preset on Home, drawer and screensaver, flip options, custom images.
7. **Media card:** title/artist/artwork, play/pause/next/previous, seek; CarPlay ↔ Bluetooth switching.
8. **Status icons:** Wi-Fi, hotspot, Bluetooth, mobile.
9. **Screensaver:** clock and image modes, banners, vehicle label spacing.
10. **Navigation pack:** `adb push dist/lahore /sdcard/Android/data/app.flare.auto/files/navigation/lahore`, then
    Settings › Offline maps › Navigation pack shows "Lahore offline navigation"; the offline map shows streets;
    search a destination, preview a route, drive with GPS guidance and voice. Debug build:
    `adb shell am start -n app.flare.auto/.SmokeActivity --es open nav` and `adb logcat -s FlareNav` for timings.
