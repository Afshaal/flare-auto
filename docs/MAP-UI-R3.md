# 1.2.0-maplibre-r3: map UI, offline search, settings, drawer, saver, profile picker

Branch `maps/maplibre` (app) and `maps/maplibre` (flare-lahore-tools). Builds on `FlareAuto-v1.2.0-maplibre-r2.apk`;
the tested baseline is still tag `baseline-1.2.0-r2`. Version stays **1.2.0 / versionCode 4**, same signing key, so
it can be swapped with the other 1.2.0 builds with `install -r` (settings are kept).

| What | Where |
|---|---|
| Release APK | `work/FlareAuto-v1.2.0-maplibre-r3.apk` — 31,438,294 bytes (30.0 MB), SHA-256 `753afec715f05752eddb921ef3ac49c55d0789a58a8fc5dc959175313cce8105`, signer certificate SHA-256 `98b99cc3…173a0cbc` (same as r2) |
| Size before (r2) | `work/FlareAuto-v1.2.0-maplibre-r2.apk` — 35,435,649 bytes (33.8 MB), 3,757 files; r3 has 347 files (Simple Icons removed): **−4.0 MB** |
| Navigation pack | revision 4, `lahore-20261109-r4`, `work/flare-lahore-tools/dist/lahore/` (88.5 MB) |
| Decisions | D-26 … D-42 in [DECISIONS.md](DECISIONS.md) |
| Screenshots (BlueStacks) | [screenshots/r3/](screenshots/r3/) |

## Update the navigation pack (r3 → r4)

Only the search database changed; the graph, Mapsforge map and vector map are byte-identical to r3. Copy three
files over the installed pack (USB / file manager into `Android/data/app.flare.auto/files/navigation/lahore/`,
or adb):

```sh
adb push dist/lahore/search.sqlite dist/lahore/build-info.json dist/lahore/checksums.txt /sdcard/Android/data/app.flare.auto/files/navigation/lahore/
```

Settings › Offline maps › Navigation pack then reads **Lahore · revision 4**. The r3 app works with the r4 pack and
this app works with the r3 pack (search without area hints and without punctuation-free matching), so the APK and
the pack can be updated in either order.

## What changed (27 items)

### Map
1. **Full-screen map** has no header: the map fills the screen, a round back button sits top-left (system back
   works too). Square corners on the full map; the Home card keeps its rounded corners.
2. **Route missing on the full map** — confirmed: guidance restored the cached route before the MapLibre view
   existed and `openVector()` never passed it on. The vector view now receives the route (and the trimmed part)
   when it is created. Tested: open the full map from the widget mid-route, and after an app restart.
3. **Travelled route removed**: only the line from the car onwards is drawn (`NavGeometry.remaining`, driven by
   the guidance position on both renderers).
4. **Closer zoom**: max zoom 19.5 → 20.5; following starts at z17.4 on the full map and z16.9 in the widget (was
   16.5).
5. **Marker** 44 dp → 32 dp; the white arrow fills more of the disc.
6. **Attribution**: a small "i" button (full map) opens the attribution dialog with "© OpenStreetMap contributors";
   the widget shows a tiny "© OSM". Fallback / outside-coverage messages appear as a small top pill only when needed.

### Full-screen navigation
7. **Turn card** (top-left): turn arrow drawn from the GraphHopper sign, distance to the turn, road name cut with
   "…". Status messages (calculating, waiting for GPS, off route, arrived) use the same card.
8. **Trip card** (bottom-left): arrival time, remaining time, distance; a star to save the destination and **End**.
9. **Controls** (right): drawn icons for recenter, 3D heading-up / north-up (icon shows the current mode), voice
   on/mute, and one zoom pill.
10. **Search button**: round magnifier at the top of the controls.

### Home map widget
11. No "Where to?" pill; a tap opens the full map (search is there).
12. Compact turn indicator top-left (arrow + distance).
13. Trip bar bottom-centre (arrival · time · distance).
14. Always follows GPS (independent of the "Follow GPS" setting), no gestures. Spoken directions unchanged.

### Offline search
15. Full-screen search: empty field, **Home** and **Work** slots, **Saved** (starred) and **Recent** (last 8
    destinations, "Clear"). Stored per driver profile. Hold a result (or a saved/recent place) → Set as Home / Work;
    hold Home/Work → Remove.
16. Result lines: name, then kind · street · area (no coordinates), distance on the right.
17. Duplicates grouped (same OSM feature; same name nearby — the specific kind wins over a generic one); distinct
    places with the same name stay, told apart by area and distance.
18. `gor` finds **G.O.R.**; punctuation and accents are ignored; word prefixes match; ranked by match quality then
    distance from the GPS fix (map centre without one).

### Launcher settings and app drawer
19. Offline maps tab: 8 rows instead of 16 (D-38). Mapsforge region actions are in "Other regions".
20. Drawer uses each app's installed icon; the Civic drawn tiles stay for stock screens and system tools; no
    automatic tinting / monochrome (`dock_mono` no longer read).
21. Icon packs (tested with Whicons) and per-app images still work (long-press an app → Choose image / Use
    automatic icon).
22. Simple Icons removed: 3,470 drawables, 4 assets, the picker, brand map, keep rules, attribution file; old
    Simple Icons choices fall back to the installed (or pack) icon.
23. Drawer header has no Home button.
24. New round back button (drawn chevron), flush with the drawer's page edge; used by all settings-style headers.
25. Values capitalised: Dark / Light / Automatic, Off / Weather / Both, Top left / Top right, 1 day, None hidden.

### Screen saver
26. Idle timeout: Off, **15 seconds**, **30 seconds**, 1 / 5 / 10 / 15 minutes (D-40).

### Driver profile picker
27. Profiles and Add New are equal-width tiles, icons and labels on the same lines, row centred; with more than
    four it scrolls sideways (the new profile is scrolled into view).

## BlueStacks test record (Pie64, 1280×720)

| Area | Check | Result |
|---|---|---|
| Full map | no header, back button, square edges | pass (`home2`, `restore-restart`) |
| | route visible when opened mid-navigation from the widget | pass (`map-restore`, taken before the trip-card width fix) |
| | route restored after app restart (reinstall), recalculated from GPS | pass (`restore-restart`) |
| | travelled part removed during a simulated 2.6 km drive | pass (`nav2`, `zoomin`) |
| | zoom in to z20.5 / smaller marker, readable arrow | pass (`zoomin`) |
| | "i" attribution dialog | pass (`attribution`) |
| | turn card (arrow, distance, road), trip card, star, End, arrival message | pass (`nav2`, `controls`, `arrived`) |
| | recenter, north-up ↔ 3D, mute ↔ voice | pass (`controls`) |
| Widget | no pill, tap opens full map, turn indicator + trip bar, follows GPS | pass (`widget-nav`, taken before the width fix that cut "400 m" to "400 r"; fixed and re-checked on the full map) |
| Search | empty state, Home/Work, star/unstar, recents persist after restart | pass (`search-recent`, `home-set`) |
| | `gor` → G.O.R. first with "Suburb · Upper Mall"; grouping; nearest first | pass (`search-gor`, `search-liberty2`) |
| | release app with the old r3 database | pass (`rel-olddb`) |
| Settings | Offline maps tab, capitalised values | pass (`settings-maps2`) |
| | idle timeout 15 s (debug) and 30 s (release) start the screensaver | pass (`timeout-dialog`, `saver-on`) |
| Drawer | installed icons, Civic tiles, no Home button, back button | pass (`drawer1`) |
| | icon pack (Whicons) and a per-app image, then reverted | pass (`drawer-pack`, `custom-icon`) |
| Profiles | centred, equal tiles with 3, 4 and 5 items; test profiles deleted, Family active | pass (`profiles-4`, `profiles-5`) |
| Build | `clean assembleRelease testDebugUnitTest lintDebug` | pass: 61/61 unit tests (10 new), lint 0 errors |
| Release | install -r over r2/debug, Home, map, search, saver; no crash in logcat | pass |

Not testable in BlueStacks: real GPS heading and speed, the AC8257 GPU at z20 with tilt, TTS with a car voice,
sunlight readability of the cards.

## Check in the Civic

1. Update the pack (three files above) and install the APK; Settings › Offline maps › Navigation pack shows
   "Lahore · revision 4".
2. Full map while driving: turn card readable at a glance, road names cut cleanly, the line behind the car
   disappears, the car stays in the lower part of the view, zoom feels right (zoom out once if it is too close).
3. Mute button stops a spoken instruction mid-sentence; unmute brings directions back at the next turn.
4. Home widget: turn indicator and trip bar while navigating, nothing over the map when not navigating; a tap opens
   the full map.
5. Search in the car with the head unit keyboard: `gor`, `liberty`, a few local names; set Home and Work.
6. Drawer: Civic tiles for Audio / Radio / Bluetooth / Car settings etc., real icons for other apps; if an icon pack
   is selected, check it still applies.
7. Profile picker on startup: row centred, labels aligned.
8. Screensaver at 15 s or 30 s if you want a shorter timeout (default remains your current setting).

## maplibre-r4 (follow-up)

`work/FlareAuto-v1.2.0-maplibre-r4.apk` — 31,438,294 bytes, SHA-256
`6376c0cbe248a5e5df5c143b2f0975faca985e062f8ca88608b1859555f9b227`, same key, 1.2.0 / 4. Same pack (revision 4).

* Trip card (full map): compact, three centred columns `11:58 / arrival`, `16 / min`, `15 / km` (metres under 1 km,
  `1:05 / hr` from one hour), arrival without AM/PM, lighter values; star and a smaller End pill (D-43).
* Settings › Offline maps › Map view span: 500 m and 1 km added (D-44).
* GPS marker 23 dp (−28 %) on the widget and the full map (D-45).
* Turn card offers "Stop navigation" while waiting for GPS (D-46).

Tested in BlueStacks (debug drive and release): `r4-trip-card`, `r4-widget`, `r4-span` in screenshots/r3/; 61/61 unit
tests, lint 0 errors, no crash. Navigation ended and span restored to 2 km after testing.
