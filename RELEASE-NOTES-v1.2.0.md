# Flare Auto v1.2.0

Draft notes for the GitHub release. The release has not been published yet.

## Added

- Offline Lahore navigation: street map, destination search, driving routes and turn-by-turn guidance with
  optional offline spoken directions. Everything runs on the head unit with no internet. It needs the separate
  Lahore navigation pack built with `flare-lahore-tools` (not inside the APK).
- Long-press a point on the offline map to drive there.
- Three-card and map Home layout presets in the Home editor.

## Improved

- Turn instructions use English street names where OpenStreetMap has them (pack revision 2). Urdu-only street
  names are left out of the spoken instruction.
- A saved destination that is waiting for GPS can be stopped from the navigation panel.
- Screens go back to full screen after a dialog closes.
- The map credit no longer overlaps the scale bar or zoom buttons.
- Smaller release APK (R8 shrinking) and lint-clean build.

## Validation and known limitations

Tested in BlueStacks: map viewing, search, routing, simulated GPS drives with arrival and off-route
recalculation, restore after restart, profiles, Home editor presets, app widgets, and an update from 1.1.0 that
kept all settings. Not yet tested on the Civic: spoken directions, real GPS behaviour, and long drives.
At a standstill on a divided road the first instruction can be "make a U-turn" until the car moves.

Map data © OpenStreetMap contributors, ODbL. Existing settings and the app signing key are kept.
