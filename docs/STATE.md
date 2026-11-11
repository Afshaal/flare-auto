# Flare Auto — project state (2026-11-11, before the optimisation pass)

Two projects live side by side in `work/`:

| Project | Path | Git |
|---|---|---|
| Launcher app | `flare-auto-profiles-local/` | branch `optimize/claude-code`, base commit `79e345e` ("baseline: Codex work-in-progress snapshot") |
| Offline Lahore pack tools | `flare-lahore-tools/` | `master`, `98db508` (versions pinned) + one untracked file `tools/preprocess_restrictions.py` |

`flare-auto-kotlin-6677f69/` (named in the task brief) is an **older** snapshot (51 Kotlin files, 2026-11-10 13:02)
without the maps / widgets / profiles / routing work. The owner confirmed `flare-auto-profiles-local/` is the
canonical app. See `docs/DECISIONS.md` D-01.

## App (`flare-auto-profiles-local/`)

Kotlin, programmatic UI (no XML layouts, no AndroidX), `minSdk 26`, `targetSdk 28`, `compileSdk 35`,
version 1.1.0 / code 3. 87 Kotlin source files, ~8,900 lines. Dependencies: `graphhopper-core 1.0`,
`mapsforge-map-android 0.25.0`, `mapsforge-themes 0.25.0`, JUnit 4 for tests.

### Finished and shipped in 1.1.0
Home with clock / media / projection cards, app drawer with icon packs and Simple Icons, settings,
screensaver with notification banners, weather (Open-Meteo, cached) and solar times, light/dark/automatic
themes, startup actions, update checker (GitHub, signature-verified).

### Finished locally after 1.1.0 (not released)
* Multi-page grid Home editor (`GridEditorActivity`, `GridBoard`, `EditorDraft`) — the editor renders through a
  standalone `HomeCards` against a draft copy of the preferences; this already replaced the old
  "save/restore twenty fields of HomeActivity" preview hack for the grid editor.
* Driver profiles (`ProfileStore`, `ProfilesActivity`), per-profile preferences and images.
* Hosted Android app widgets (`HostedWidgets`, `WidgetPickerActivity`).
* Offline map card (Mapsforge) with bundled `assets/maps/world.map` (3.3 MB) and manual region download / import.

### Half done (where the work stopped)
* **Offline routing / guidance.** `OfflineRouting` (GraphHopper 1.0, memory-mapped), `OfflineGuidance`
  (turn-by-turn text + offline TTS), `OfflineDestinationPicker` (SQLite FTS search) and `NavGeometry` exist,
  but:
  * `OfflineGuidance` has **no caller** — no screen shows it;
  * `NavigationPack.ensure()` unzips `assets/maps/lahore-navigation.zip` **from inside the APK**, and that asset
    does not exist, so any routing / search call fails;
  * the Settings "Map credits" text still says routing is not supported.
* Legacy screens `CustomizeActivity` and `DockEditorActivity` are declared in the manifest but **nothing in
  the release app launches them** (only the debug harness `LimitChecksActivity` opens `CustomizeActivity`).
  They were superseded by `GridEditorActivity` and carry most of the duplicated layout/dock logic.

### Freshest evidence
`work/flare-widgets-maps-build.log` (20:43) — release build OK; `work/FlareAuto-widgets-offline-maps-preview.apk`
(20:41); `WIDGETS-MAPS-PREVIEW.md` lists what was validated on BlueStacks.

## Lahore tools (`flare-lahore-tools/`)

Gradle `libs` task fetches pinned jars (GraphHopper 1.0 reader, mapsforge-map-writer 0.25.0, Osmosis 0.49.2)
into `lib/`. `versions.properties` is the single source of truth (source PBF URL/date/SHA-256, bbox, GH
settings). Built by hand on 2026-11-11 (Codex, then Claude):

| Step | Output | State |
|---|---|---|
| Clip Pakistan PBF to bbox 31.05–31.85 N, 73.90–74.80 E | `lahore.osm.pbf` (10 MB) | done (`extract.log`) |
| Mapsforge map | `lahore.map` (7.4 MB) | done (`map-build.log`) |
| OSM XML export | `lahore.osm` (219 MB) | done (`xml-export.log`) |
| Search DB | `lahore-search.sqlite` (3.7 MB, 36,400 places) | done (`tools/build_search.py`) |
| GH graph, raw restrictions | `lahore-gh/` (62 MB) | done (`routes-build.log`, 3 sample routes OK) |
| GH graph with Codex's manual via-way fix | `lahore-driving-gh/` | done (`driving-build.log`) |
| Restriction preprocessor (all classes, audit JSON) | `tools/preprocess_restrictions.py` | **written, never run** (21:33) — input `work/dev/routing.osm` (21:30) exists |
| One-command pipeline, build-info.json, checksums, regression routes, metrics | — | **missing** |

`turn-restriction-audit.json` named in the brief does not exist yet; only Codex's two-entry
`tools/legacy/codex-turn-restriction-audit.json` (manual via-way conversions) does.
