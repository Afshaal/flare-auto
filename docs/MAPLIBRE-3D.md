# 3D vector map (MapLibre): build, install, test record

Branch `maps/maplibre` (app) and `maps/maplibre` (flare-lahore-tools). The tested baseline before this work is
tag `baseline-1.2.0-r2` / `work/FlareAuto-v1.2.0-r2.apk`.

## What changed

* The offline map uses MapLibre Native 13.0.2 (OpenGL ES 3) when the navigation pack has a display map. It shows
  a dark navy style, blue-grey roads, flat buildings, English street names where OSM has them, a vivid blue
  route under the labels, and a blue vehicle marker with a white arrow that lies flat on the road.
* Following GPS has two modes, set by the **3D / N** button on the map or Launcher settings › Offline maps ›
  Map orientation:
  * heading-up: the map turns with the car, tilted 55°, with the car in the lower part of the screen;
  * north-up: flat, north at the top, and the arrow turns instead.
  Heading comes from `HeadingFilter`, which trusts GPS bearing at 2.5 m/s or more, uses movement direction
  otherwise, and holds the last heading when stopped.
* The map has recenter, orientation and +/− buttons, and long-press to choose a destination. Search, routing,
  guidance, rerouting and route restoration are unchanged; they don't depend on the renderer.
* Mapsforge remains the second renderer. It is used for packs without `vector/`, for downloaded or imported
  `.map` regions, and for the world overview. It is also used when Launcher settings › Offline maps › **3D vector
  map** is off, or when MapLibre cannot start; the credit line then says why.
* MapLibre's network access is switched off (`MapLibre.setConnected(false)`), and the style references only
  files inside the pack.

## Files

| What | Where |
|---|---|
| APK (release, signed with the usual key) | `work/FlareAuto-v1.2.0-maplibre-r2.apk` (Home widget preview, below; SHA-256 `af3c9671…e8993822`, 35.4 MB). First 3D build, kept: `work/FlareAuto-v1.2.0-maplibre.apk`, 1.2.0 / versionCode 4, SHA-256 `506ce04e…ff49b97c`, 35.1 MB |
| Navigation pack r3 | `work/flare-lahore-tools/dist/lahore/` (85.0 MB; `vector/` is 13.4 MB) |
| Pack build | `python -I tools/pipeline.py` in flare-lahore-tools (stage `vector`; see its README) |

The version stays 1.2.0 / 4, like the baseline APK, so the two can be swapped over each other with `install -r`
and settings are kept. Bump `versionCode` before publishing either one through the in-app updater.

## Install the pack

Copy `dist/lahore` to `Android/data/app.flare.auto/files/navigation/lahore` (USB or file manager), or with adb:

```sh
adb push dist/lahore /sdcard/Android/data/app.flare.auto/files/navigation/
```

Over an existing revision-2 pack, copying only `vector/`, `build-info.json` and `checksums.txt` is enough.
Launcher settings › Offline maps › Navigation pack should still say "Lahore offline navigation", and the map
credit should read "© OpenStreetMap contributors · Offline 3D".

## BlueStacks results (Pie64, 1280×720, OpenGL ES 3.0, x86_64)

| Check | Result |
|---|---|
| Offline map display (full screen and Home card) | pass: dark style, English labels, buildings at z14+, rounded card clip |
| Search ("Liberty", 22 matches) and route | pass, debug and R8 release builds |
| Heading-up tilt, rotation, car near bottom | pass during a simulated 2.6 km drive; camera follows smoothly through turns |
| North-up toggle, drag stops following, recenter | pass |
| Turn instructions and ETA | pass (unchanged panel over the 3D map) |
| Off-route reroute and restore after restart | pass (restored destination rerouted from GPS; detour gave a new 2.0 km route) |
| Long-press destination | pass |
| "3D vector map" off: Mapsforge fallback | pass |
| MapLibre log | no errors or warnings; `connected - false` (offline) |
| Unit tests / lint | 51/51 tests (6 new `HeadingFilterTest`), lint 0 errors |

Performance in BlueStacks (not representative of the head unit):

| | Mapsforge | MapLibre |
|---|---|---|
| Home, fresh start, total PSS | 78 MB | 88 MB |
| Full map, following a route | — | 87 MB PSS, 24 MB native heap |
| UI frames while following | — | 604 frames, 0 janky, 5 ms p99 |

The APK grows from 7.9 MB to 35.1 MB because of `libmaplibre.so` (13.3 MB arm64-v8a + 13.6 MB x86_64). A Civic-only
build without x86_64 would be about 21 MB.

## Not testable here: check in the car

1. The 3D map opens. If the credit line says "3D map unavailable" or "3D map failed", the head unit's GPU or
   driver can't run MapLibre. Note the message; the map falls back to Mapsforge automatically.
2. Smoothness on the AC8257 GPU while following at 40–60 km/h, especially with music playing. If it stutters,
   switch to north-up or turn "3D vector map" off.
3. Real GPS heading: no spinning when stopped at lights, quick alignment after pulling away, behaviour in reverse
   and in parking lots.
4. Readability in sunlight. The style is night-only, regardless of the launcher's light theme.
5. Memory over a long drive (no growth) and that Home stays responsive.
6. Spoken directions over the 3D map (unchanged code, but still untested on the car).

## Home map widget (maplibre-r2)

> Superseded in maplibre-r3 ([MAP-UI-R3.md](MAP-UI-R3.md)): the "Where to?" pill and the text banner were replaced
> by a compact turn indicator and trip bar; the full map lost its header and gained new controls.

The Home map card is a read-only preview, like a CarPlay map tile:

* it shows the live map, position, heading-up or north-up following, and while navigating the active route and
  the live direction banner (the same panel as the full map, so distance, instruction and ETA match);
* map gestures are off and the card takes every touch, so panning a map can't fight the Home pager (horizontal
  drags still change Home pages);
* a tap anywhere opens the full interactive map; the **Where to?** pill (shown when no destination is set) opens
  it with destination search already open;
* on the full map the idle panel reads **Where to?** with a search icon;
* the route is shared in memory between the widget and the full map, so going back and forth does not recalculate
  it. A widget opened after a restart calculates it once from GPS, like the full map does.

Tested in BlueStacks (debug and release): no panning, the pill opens search, picking Liberty Market gives a
route on the full map and on the widget with the banner counting down, a tap opens the full map with navigation
continuing, stop returns the widget to "Where to?".
