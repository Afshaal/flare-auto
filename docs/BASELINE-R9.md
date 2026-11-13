# Baseline r9 (maplibre-r9-lahore), 2026-11-13

APK: `work/FlareAuto-v1.2.0-maplibre-r9-lahore.apk` (1.2.0 / 4, same local signing key, signer `98b99cc3…`).
Pack: `flare-lahore-tools/dist/next/lahore/` (pack id `lahore-20261109-r7`, shown as "Lahore"); previous r6 pack
kept at `flare-lahore-tools/dist/backup/lahore-r6/`. Pack tooling: flare-lahore-tools commit `2298326`
(`LAHORE-PACK.md`). Not installed on the Civic.

## Changes
* **Main Lahore pack.** The Latin-names pack (composite search, optional 3D buildings) is the main pack, folder
  `lahore` (the saved pack choice is unchanged), named "Lahore". Search shows `display_name` when the index has it;
  ranking still uses the matched original alias, so Urdu queries keep working (`launcher-names.patch`, already in
  the tree, kept). Pack names no longer show "revision N" or the folder name (folder only when two packs share a
  name).
* **3D map lag.** Measured on BlueStacks with temporary frame counters at the reported view (zoom ~12.4, tilt 53°,
  heading-up): every GPS fix started a 950 ms eased camera animation towards a camera the marker glide already
  moved (since r7), so the map re-rendered almost continuously, even parked (~660 renders/s uncapped on the
  emulator; 60 fps on a device). In addition the glide redrew the vehicle source and camera 30 times a second even
  where the car moves under a pixel per second. Fixes: `follow()` moves the camera only when following starts or
  the orientation changes (and once the style is ready); glide frames scale with the visible change
  (`NavMotion.glideFrameMs`: ~1.5 px or 1° per frame, max ~30/s, one step per fix on wide views); the street label is
  measured once per name. Result at the reported view: parked 0 renders and 0 % CPU; driving 1–3 updates/s
  instead of 30, about 19 % of one emulator core. Close zoom keeps ~20–30 updates/s (smooth). Tilt, heading-up,
  buildings and appearance unchanged; no new setting was needed (no visual trade-off was made).
* **Map info button removed.** Credits stay in Settings › About › Open-source credits, which now also lists the
  installed pack's name and search sources.
* **3D buildings** is a switch like the other on/off settings (dimmed, off, when the pack has no building data).
* **Editor pickers.** "Add widget" and "Layouts" open a full-screen drawer: category tabs on the left, a page of
  cards (live preview, name, size chips / "Use this layout") on the right; categories larger than one page are
  paged, not scrolled.

## Tests
Unit 88 pass; lint 0 errors (existing warnings remain). Tools: 17 pass (10 search/composite in the pipeline
Python, 5 names/finalize and 2 buildings in the names/buildings environment). Pack: all 79 checksums verify; data
files byte-identical to the reviewed Latin-names pack; app's own verification passed on BlueStacks.

BlueStacks (Pie64, main app installed fresh — no earlier install existed — then updated in place keeping data):
pack shown as "Lahore"; credits list the three search sources; 3D buildings switch; heading-up following from the
last known fix; the reported wide tilted view; route overview, search, routing (Lahore only); new pickers (add a
widget from the drawer, placed and selected). The isolated test package and its network rules were removed, so
the main app's single Lahore pack is the only one installed.

## Limits / Civic checks
Emulator frame rates are uncapped and its GPU is far faster, so check on the Civic: smoothness and heat at the
wide tilted view while driving and parked, close-zoom following, and the drawer at the head unit's resolution.
570 search rows and 98 map labels keep their original (Urdu) names; GraphHopper street names and spoken
instructions are not renamed. Composite search licensing is not settled for public distribution
(`flare-lahore-tools/COMPOSITE-SEARCH.md`).
