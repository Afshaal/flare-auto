# Pack selection, renderer settings and the Pakistan pack (maplibre-r5-packs), 2026-11-13

APK: `work/FlareAuto-v1.2.0-maplibre-r5-packs.apk` (1.2.0 / 4, same local signing key as r4,
SHA-256 `cc0091052ecd53f3e4470d929d61eaa6c0dfa3ec72170ef6b03042e1b9bdcb84`). Not installed on the Civic.
Backup tags before this work: `backup-pre-pakistan-integration` (app and tools repos).

## Changes
* **Several packs side by side.** `NavigationPack.installed / select / choose`: Settings › Offline maps › Navigation
  pack lists every folder under `navigation/` that has a `build-info.json` (unusable ones with their problem) and
  stores the choice device-wide (`navigation_pack` prefs, key `selected`; no driver-profile key added or changed).
  Without a usable choice: the first folder by name that reads as ready, else the first (old behaviour when only
  one pack is installed). A chosen pack that later breaks is reported, never silently replaced.
* **No mixed state.** Maps, search, routing and guidance all read the selected pack. Selecting bumps the map data
  revision, so Home and the full map rebuild on their next resume. Routes carry the pack the screen shows and fail
  ("pack changed; reopen the map") instead of being computed on another pack; the shared route cache key includes
  the pack. Checksum verification is remembered per pack (the old single stamp is still honoured).
* **Renderer settings.** "3D vector map" toggle → "Map renderer": MapLibre · 3D / Mapsforge · 2D (same
  `offline_map_vector` key). The row shows the renderer actually used ("3D unsupported" without OpenGL ES 3 or on a
  CPU without the native library; "no 3D map in pack"). MapLibre shows Map orientation; Mapsforge shows Other
  regions; pack, following, centre and span are shared. Choosing MapLibre on an unsupported unit explains why and
  keeps Mapsforge.
* Debug only: `PackSwitchCheckActivity` (`SmokeActivity --es open packs`); `-Pflare.debugIdSuffix=.integration`
  installs a debug build next to the real app.

Not taken from Antigravity's launcher copy: its `NavigationCheckActivity` rewrite (it enabled offline maps in the
driver's settings, wrote a mock `lahore` pack into the real pack folder and set private guidance fields by
reflection) and its `applicationId` change.

## Tests
* Unit: 69 pass (new `PackSelectionTest`: installed list, selection, removed/broken packs, per-pack files, renderer row).
* BlueStacks Android 9 (x86_64), separate package `app.flare.auto.integration`, its IPv4 traffic rejected with
  iptables (ip6tables not permitted): `PackSwitchCheckActivity` 21 PASS / 0 FAIL — Lahore r5 and Pakistan r2,
  search (Latin, Urdu, relation landmarks), routing in/out of coverage, stale-pack route refused.
  UI: pack chooser, renderer rows for both renderers, Mapsforge map + search "data darbar" + route on Lahore, switch
  to Pakistan + MapLibre with the map in the background (map rebuilt, route recalculated), Pakistan search.
* Not tested: the Home map widget after a switch (test profile had no map card), real Civic GPU / storage speed.
