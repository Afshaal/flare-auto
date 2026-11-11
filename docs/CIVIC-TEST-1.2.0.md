# Flare Auto 1.2.0: test record and Civic checklist

APK: `work/FlareAuto-v1.2.0.apk`. Package `app.flare.auto`, versionCode 4, versionName 1.2.0.
Signed with the same key as 1.1.0 (certificate SHA-256 `98b99cc3…173a0cbc`).
APK SHA-256 `e763ba32f9c4287f9465ee1d7aa9145a32464d5d51ec956fcbf4b1bae345db8d`.
Built with `./gradlew clean assembleRelease testDebugUnitTest lintDebug`: 45/45 unit tests, 0 lint errors.
Navigation pack: `flare-lahore-tools/dist/lahore`, `lahore-20261109-r2` (52/52 regression routes).

## Passed in BlueStacks (Pie64, 1280×720)

| Area | Result |
|---|---|
| Update 1.1.0 → 1.2.0 (`install -r`) | installs over 1.1.0; layout, weather, badge, profiles and active profile kept |
| Navigation pack | Settings › Offline maps shows "Lahore offline navigation", map 7.1 MB |
| Map viewing | Lahore streets render offline on the full map and the Home map card |
| Search | "Liberty" gives 22 local matches with readable kinds |
| Routing | debug and R8 release builds; first route ~460 ms incl. load, later ~42 ms |
| Guidance | simulated 2.6 km drive to Liberty Market: steps advance, ETA updates, arrival shown |
| Off-route | simulated detour recalculates from the new position |
| Long-press | "Drive here?" then a route to the pressed point |
| Restart | active navigation restored; can now be stopped while waiting for GPS |
| Profiles | create, switch (own layout), rename, delete (falls back to Family) |
| Home editor | ready-made layouts incl. three-card and map presets; back discards unsaved edits |
| Widget picker | installed widgets listed with previews; system bind prompt; placed on a new page |

## Fixed during this test

- A restored destination waiting for GPS could not be cancelled: the panel only opened search.
- Map credit overlapped the scale bar and zoom buttons.
- Long-press on the map crashed (Mapsforge passes null arguments).
- Status bar stayed visible after closing a dialog.
- Urdu street names were passed to the English voice.
- Profile rename put the cursor before the existing name.

## Not testable in BlueStacks

- Spoken directions (no audio check), and which offline TTS voice the head unit has.
- Real GPS: heading, accuracy, tunnels and flyovers, start on a divided road.
- Head-unit performance over a long drive; vendor audio ducking during speech.

## Check on the Civic

1. Install `FlareAuto-v1.2.0.apk` over 1.1.0 (it must say "update", not "install"); settings stay.
2. Copy the pack to `Android/data/app.flare.auto/files/navigation/lahore/` if it is not there yet.
3. Grant location when asked; Settings › Offline maps › Use current GPS position.
4. Drive a known route: the blue dot follows the road, instructions change before each turn, ETA is sensible.
5. Spoken directions: audible, English, music lowers while speaking. If the toast says no offline voice is
   installed, install an offline English voice in Android TTS settings.
6. Miss a turn on purpose: a new route appears within about 15 s.
7. Home map card with GPS; screen stays responsive with media playing.
8. Profiles, widget picker and three-card layouts as in BlueStacks.

## Update checker

`UpdateVersion.compare` is unchanged since 1.1.0, so the copy on the Civic behaves like the tests:
`v1.2.0` > `1.1.0`, `v1.2.0` = `1.2.0`, `v1.2.0-beta.1` < `1.2.0`, `v1.10.0` > `1.2.0`, unparsable tags are
"comparison unavailable".

Still to check once the GitHub release is published:
- Tag `v1.2.0`, marked as the latest stable (not draft or pre-release: the checker reads `releases/latest`).
- Exactly one `.apk` asset, so the dialog offers **Download** instead of "Open release".
- On 1.1.0: About › Check for updates shows "Update available", downloads, verifies package, version code and
  signer, then opens Android's installer ("install unknown apps" permission the first time).
- After updating, the same check reports "No newer release".
