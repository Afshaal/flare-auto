# Public release roadmap (draft, decisions pending)

The owner's personal Lahore build keeps full offline navigation and stays separate from the public release.

## Plan
* One APK with both map views: standard Mapsforge and tilted MapLibre, selectable in settings, with automatic
  fallback to Mapsforge (OpenGL ES 2 units, missing native code, driver failures).
* Users choose a region and download or import a compatible offline map.
* The map shows GPS position and heading without a routing engine.
* A "send destination to another app" button (Google Maps, Waze, OsmAnd…) instead of built-in worldwide routing.
* A separate guide for turn-by-turn options; user-built routing data only as unsupported / experimental, if at all.

## To decide / confirm before promising coverage
* **Mapsforge source:** official mapsforge.org download server (OSM, ODbL); check that in-app downloads by many
  users are acceptable.
* **MapLibre vector source and format:** proposed Protomaps schema + PMTiles (daily worldwide builds, region
  extracts with `pmtiles extract`, style rewrite needed). Avoid hosted sources with commercial terms
  (e.g. MapTiler). Alternatives: self-hosted region files; import-only.
* **Label glyphs:** bundle Latin in the APK; optional downloads for Arabic, CJK and other scripts.
* **ABIs:** add armeabi-v7a for 32-bit-only units (~40 MB universal APK), or per-ABI APKs with updater support.
* **Release hygiene:** GPL-3.0 source publication; remove personal defaults (CIVIC, RS, Lahore); review the update
  checker URL and Civic-specific behaviour; location permission explanation; Play Store would require a newer
  targetSdk (separate decision).
