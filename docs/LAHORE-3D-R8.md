# Lahore composite search, optional 3D buildings, editor selection (maplibre-r8-lahore3d), 2026-11-13

APK: `work/FlareAuto-v1.2.0-maplibre-r8-lahore3d.apk` (1.2.0 / 4, same local signing key,
SHA-256 `3964cbefabde11f7165677c61b06eb61dacd50b4e0d225a19a13019a2ed0debb`). Not installed on the Civic.
Test pack: `work/lahore-3d-preview/lahore-buildings-test/` (composite search + `vector/buildings.mbtiles`).
Search-only pack: `work/lahore-search-review/final/lahore-composite/`. Original `dist/next/lahore` untouched.

## What was already integrated (uncommitted working tree, kept)
`OfflineSearch` confidence tie-break and Overture grouping (with tests); `NavigationPack.VectorMap.buildings`
(optional `files.vector.buildings`, ignored when the file is missing); `offline_map_buildings` (default off),
"3D buildings" row under MapLibre settings ("Data not installed" for packs without it); a `FillExtrusionLayer`
from zoom 15.5 below `road-casing`, added only when the setting is on.

## Fixes in this round
* **Search attribution.** The composite index mixes OpenStreetMap, pak-geodata and Overture records, but the search
  screen and map attribution said only "© OpenStreetMap contributors". `Pack.searchCredit` now reads
  `search_sources.attribution` from build-info.json; the search status line and the map "i" dialog show it. Packs
  without it keep the OSM-only text. (Test: `compositeSearchAttributionComesFromTheManifest`.)
* **Home did not rebuild after toggling 3D buildings** (the Home map widget kept its old style): the key is now in
  `Keys.HOME_UI`, like the renderer setting.
* **Map zoom buttons barely worked while following** (r7 regression): the marker glide moves the camera every frame
  and cut the 250 ms zoom animation short. While following, zoom now applies at once. A fix at the same position and
  heading no longer starts a glide (parked car: no per-frame camera work).
* **Street label in the Home map widget** sat under the trip bar; it is shown on the full map only (as with 2D).
* **Editor: red overlay on a chosen widget removed.** Choosing a size (or "Add on a new page", or an installed app
  widget) now puts the widget on the page at the position the editor already chose and selects it like a tapped
  widget: normal appearance, outline and four mid-side resize handles. The placement mode with its tinted wash over
  every free spot is no longer entered. Add-widget button and the add-page "+" are unchanged.

## Pack verification (PC)
All checksums verify: lahore-buildings-test 79 files, lahore-composite 77, original lahore 77 (build-info.json is
not in checksums.txt, as before). Buildings MBTiles: zoom 15 only, 827 tiles, layer `building` with `height`,
`min_height`, `height_source`, `osm_id`; attribution "OpenStreetMap contributors". Graph, map and vector base files
are the original ones (per the 3D/search reports; checksums match). On the device the app's own check passed
(81 entries counted).

## Device test (BlueStacks Pie64, isolated `app.flare.auto.r8check`, network blocked by UID)
Note: when this round started no Flare package was installed on Pie64 (main app and earlier test installs had been
removed outside this session); only the r8check test package was installed.
* Search (offline, composite pack): Badshahi Mosque, Cinnabon, JT Phase 1, Emporium, Model Town, Gaddafi Stadium
  return sensible results; credit line shows all three sources. Urdu queries not tested (adb cannot type them).
  Seen: same-place duplicates from different sources ("Gaddafi stadium" / "Gaddafi Stadium", "Badshahi Mosque" /
  "Badshahi Mosque, Pakistan"), Overture noise ("Model Town · Home developer · Pakistan"), "Emporium" ranks
  Emporium Market before the mall.
* Routing: Gulberg → Badshahi Mosque 10 km / 12 min; overview, Start, guidance.
* 3D buildings: Off = flat footprints; On = extruded buildings at driving zoom (Anarkali / Mall Road), route line and
  road labels stay on top; below zoom 15.5 the layer is not drawn. On/off via settings reloads the map on return.
  Old pack (no building data): "Data not installed", map flat, no error, setting value kept. Mapsforge: flat map,
  the 3D row is hidden. Home map widget renders the buildings.
* Memory (emulator, full map, not Civic figures): total PSS about 96 MB with buildings on, 88 MB off, 74 MB on Home.
  Frame pacing not measured (MapLibre renders in a TextureView, not covered by gfxinfo).
* Editor: chosen widget (Offline map 4×3) placed and selected with outline + four handles, no red wash; tapping
  existing widgets same; resize from bottom, left, top and right; move by long-press drag; deselect on an empty
  cell; page swipe to the add-page card and back; "Add on a new page" (Clock with date); tick saved the layout
  (stored JSON checked), reopened editor shows it, Home shows both pages.

## Tests
Unit 86 pass; lint no errors (warnings remain). Tools: 10 search, 2 building tests pass.

## Licensing (not settled)
Retaining source IDs and listing sources is not a licensing decision. OSM data is ODbL 1.0; Overture Places and
pak-geodata have their own terms, and merged records may carry obligations from more than one source. Before any
public distribution: per-source licence review, complete attribution text in the app and pack, and a decision on
how merged (Produced Work / database) obligations apply. Until then this pack is a local test pack.

## Limits / Civic checks
3D heights are estimates (most are defaults or from floor counts). Check on the Civic: frame rate and heat with 3D
on while driving in the old city, readability of the route over buildings at night, memory over a long drive, the
search duplicates above, and the marker in a 3×3 Home map widget (the trip bar can cover it).
