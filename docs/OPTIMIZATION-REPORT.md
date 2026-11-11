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
