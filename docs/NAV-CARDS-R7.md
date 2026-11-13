# Marker gliding, route overview, alternatives, new Home cards (maplibre-r7-navcards), 2026-11-13

APK: `work/FlareAuto-v1.2.0-maplibre-r7b-navcards.apk` (1.2.0 / 4, same local signing key,
SHA-256 `142b6de098ccf71bdb49c275ac2c3e51feca656466844d970b7d54c72bcec722`), with the device-test fixes below.
The first build, `r7-navcards` (`e66f339f…`), is kept but superseded. Not installed on the Civic.
Backup tag before this work: `backup-pre-nav-cards`.

## Navigation
* **Gliding marker (MapLibre).** Each fix animates the marker, and the camera while following, from where it is shown
  to the new fix over about the fix interval (250–1000 ms, ~30 fps), then it stays there: nothing is predicted when
  fixes stop. First fix, gaps over 5 s, jumps over 150 m, a hidden view or a re-opened map draw at once; a map
  gesture stops following. Mapsforge (2D) still moves per fix: animating its software-rendered tiles every frame
  would cost too much CPU on the AC8257.
* **Marker size and offset.** MapLibre marker 23 → 27 dp, Mapsforge dot 7 → 8 px. Offset: with simulated fixes lying
  exactly on the route, the MapLibre marker was drawn on the line and horizontally centred (BlueStacks, before any
  change), so the renderer has no rightward bias. The offset seen in the car comes from the position itself (GPS error,
  lane position against the mapped centre line). Fix: while navigating, a fix is drawn on the route only when
  accuracy ≤ 25 m, it is within its accuracy (≤ 20 m) of the route and the GPS bearing agrees within 35° at ≥ 2.5 m/s
  (slower or no bearing: within 6 m). Otherwise the raw GPS position is shown.
* **Street label** under the marker: only when the fix is snapped to the route, from the route instruction's road name;
  hidden otherwise (no guessing from nearby map labels). Mapsforge shows it while the map is centred on the car.
* **Route overview.** After choosing a destination the map shows a flat north-up view fitting all options (selected
  blue, others gray) with a list and Start; a tap selects. Guidance starts on Start, or when the car has moved 80 m.
  One route found: the overview shows just that one.
* **Alternatives while driving.** The options not chosen stay gray, with "Other routes" listing "via M-2 · +16 min" /
  "Similar ETA" (< 3 min) / "5 min faster"; a tap switches (one route calculation). Each alternative disappears once the
  car passes the point where it leaves the chosen route. No periodic recalculation; after a reroute or switch the
  comparison is cleared rather than shown with stale times.

### GraphHopper limits
GraphHopper 1.0 can compute real alternatives on our CH graph (`alternative_route`), but it took 5–25 s on BlueStacks,
so on-device options come from the PC-computed corridors (r6: Lahore, Islamabad, Rawalpindi, Multan, Karachi), each a
real GraphHopper route through one point of that alternative. Elsewhere only the primary route is shown.
`pass_through` and heading constraints are not supported with CH in 1.0.

### Peak-hour ETA: not added
There is no defensible offline basis: the packs contain OSM road data only (no observed or historical speeds), and the
times are GraphHopper's speed-profile estimates. Any "peak" factor would be invented, so the ETA is unchanged.

## Home cards
* **Album player** (`media_album`, Media): artwork upper left (note glyph when there is none), title (marquee) and
  artist (two lines, ellipsized) beside it, progress with elapsed / total below (shown only when the track has a
  duration), previous / play-pause / next in one row. Same views and callbacks as the existing players.
  Gallery sizes 4×3, 5×4, 8×6; minimum 3 rows.
* **Clock with date** (`clock_date`, Clocks): large time (12/24-hour setting, Home clock style, text-size setting),
  short accent line, date below. No corners or vehicle label, so the editor offers no corner / vehicle commands for it.
  Gallery sizes 3×2, 4×3, 8×6.
* Existing styles, layouts and preference values are unchanged; one media card per page still applies.

## Dashline-derived code (GPL-3.0-only, attribution kept, not modified)
`src/app/flare/auto/NowPlaying.kt` (media-session selection and metadata helpers) and `src/app/flare/auto/MediaGlyph.kt`
(playback-control geometry). The new album card reuses them unchanged. Attribution: `CREDITS.md`, `docs/LICENSING.md`,
`README.md`, the in-app credits (`Credits.kt`).

## Device-test fixes (r7b)
Found on BlueStacks with a separate test package (`.r7check`, network blocked, Lahore + Pakistan packs, simulated
Lahore → Islamabad drive) and fixed:
* **Intercity overview missed the routes.** The 3D map's minimum zoom (8) is too close for a 290 km trip, and the
  fitted camera copied MapLibre's padding twice. The overview now allows zoom 4 (the packs have tiles from 4) with an
  explicit centre and padding; leaving it restores zoom 8, zero padding and the driving zoom.
* **2D overview** was shifted under the route list (wrong sign of the left offset), and the 2D map stayed at overview
  zoom after Start; it now returns to the zoom used before the overview.
* **Alternative split point** ignores departures shorter than 2 km that rejoin the route (`NavMotion.divergesAt`).
  Lahore → Islamabad: the M-2 option really turns off 301 m from the start, so it is correctly dropped there.
* **Route line ahead of the gliding marker** at speed (up to one fix, ~30 m at 108 km/h): the trimmed line is now
  drawn when the marker reaches the fix, so the line always starts at the marker.
* **Route lists covered the centred marker and street label** on wide-density screens: list width now leaves about
  90 dp each side of the centre (minimum 220 dp).
* **2D gray alternative looked like a road** (its colour matched the 2D road colours): light gray with a dark casing.
* **Album player:** the artwork column now matches the square artwork, so the title and artist sit beside it on wide
  sizes.
* **Home editor did not save a new widget** when tick was pressed while it was still being placed ("Place 4×3" not
  tapped): the widget was dropped and only an empty new page was saved. Saving now places the pending widget first.

Verified on BlueStacks after the fixes: 3D and 2D overview fit both routes beside the list; Start and auto-start
(after 80 m); gray alternative and "Other routes · via M-2 · +15 min" until the split, then removed; marker snapped
on the route with the street label ("Gurumangat Road", "Gulberg Road"); gallery previews of Clock with date and
Album player not clipped; Album player saved via tick and shown on Home. Not checked on a device: Clock with date on
Home (gallery preview only), 5×4 / 8×6 sizes, the custom-widget flow.

## Tests
Unit: 82 pass (new `NavMotionTest`, card tests in `DashboardBehaviorTest`). Lint: no errors. BlueStacks (isolated test
package): gliding camera and larger marker on a simulated drive, route overview and Start. Device testing then stopped:
another process was using the same test install (it added a `lahore-composite` pack and was running UI automation), so
the street label, snapping, gray alternatives, the 2D view and the new cards were not checked on a device.
