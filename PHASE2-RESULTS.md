# Phase 2 test report — Flare Auto redesign

Testing completed on Android 9 BlueStacks at 1280×720. Production Kotlin and manifest match the Phase 1 hashes exactly. Only debug fixtures were added. No GitHub changes or car install. The signed Phase 1 APK remains the build to correct in Phase 3.

## Passed
- Existing JVM suite: 10 tests, zero failures/errors.
- Debug build and full debug lint: zero lint errors (5,280 reported warnings/issues remain; many are bundled icon-resource and existing API/style warnings).
- Seven Android model checks: draft isolation, preference-type Bundle round trip, merge-safe Save, Undo snapshots, defensive set reads, catalog size validity and non-overlapping placement/player-group constraints.
- Thirty compact geometry cases across all current widget types at text sizes 85%, 100% and 120%.
- Visual widget gallery, size selection, replacement preview, explicit Place and Save.
- Appearance preview/Undo/Cancel; Save persists settings. Activity recreation retains a cancellable draft.
- Finite add-page panel and cancelling unsaved page creation.
- Selected edge-midpoint resizing, collision-safe persistence.
- Dock shortcut changes remain drafts; drag/drop reorders and saves the existing preference format.
- Ready-made layout preview, Use, Undo and Cancel.
- Profile creation persists the named profile. Cold process startup asks once; returning to Home does not ask again.
- Song-change popup uses the actual title, hides the right information slot, ignores duplicate metadata, expires after five seconds and restores the slot. Rapid changes show the latest song.
- Custom wallpaper import stages the image; Undo/Cancel preserve the saved image. Save produces a byte-identical imported file and persists wallpaper selection.

## Confirmed fixes / polish for Phase 3
1. **Corner setting disagrees with legacy settings (functional).** With `saver_solar=true` and no new slot keys, ScreenSaverActivity correctly shows solar information, but SettingsActivity reports Top-left information = `off`. Use one shared effective-slot resolver in both screens, including legacy fallback, and show friendly values. Evidence: `legacy-slot-visible.png`, `legacy-slot-settings-mismatch.png`, `confirmed-legacy-slot.json`.
2. **Profile creation is hard to discover with several profiles (UI).** Add New scrolls off the right edge, without a visible scroll cue. Creating a profile returns to the list’s beginning, so the new profile is off-screen even though it was saved. Keep Add New discoverable and reveal the created profile. Existing custom names can duplicate built-in names (two Guest entries were visible); preserve their data while making identities clear. Avatar choices also lack accessible names. Evidence: `fail-15.png`, registry verification in `ui-results.json`.
3. **Editor preview does not fully match Home (UI/source confirmed).** The editor builds its own top bar and dock: it omits Home’s conditional cellular indicator, uses a solid accent instead of Home’s fade and duplicates dock label/icon sizing rather than the shared Home sizing/monochrome rules. Consolidate rendering so saved Home matches the preview. Evidence: `editor.png`; GridEditorActivity.render versus HomeActivity.buildTopBar/buildDock.
4. **Short-card readability/touch sizing needs polish.** CardFrame keeps contents inside bounds by scaling the entire widget. This retains controls but also scales text and touch targets below their original size in short cards. Geometry checks pass; they do not establish comfortable touch sizes. Revisit responsive minimum sizes and layout choices on the Civic rather than relying solely on scaling.

## Test harness issues resolved
Earlier raw FAIL entries in `ui-results.json` include harness failures, not app failures: off-screen menu items, Back dismissing a dialog on BlueStacks’ hardware keyboard, Add New/new profile being off-screen, task reuse routing a launch to the old Activity, and unquoted broadcast values losing spaces in song titles. Successful reruns and direct saved-state/registry checks supersede those entries. A few UIAutomator dumps returned transient null/idle-state errors; retries succeeded.

## Limits
- No actual Civic install, full unit power cycle, MCU/audio/DiPlay routing test, sustained RAM/FPS measurement or exhaustive density matrix.
- Wallpaper import was exercised through the Activity result callback with a valid private test image; every external document-provider UI was not exercised.
- In-flight image-import recreation, notification queue/exit races, animation-off combinations and weather cache expiry should receive targeted checks during Phase 3 if their code changes.
- No broad “everything works” claim. Core tested flows pass; the issues above remain for Phase 3.
