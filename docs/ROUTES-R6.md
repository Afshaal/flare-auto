# Route options, city-first search, map opening (maplibre-r6-routes), 2026-11-13

APK: `work/FlareAuto-v1.2.0-maplibre-r6-routes.apk` (1.2.0 / 4, same local signing key,
SHA-256 `f503384a6d2c688df455360ed16e8389bede5705ab40ddbcd75de290de48fb6d`). Packs: Lahore r6, Pakistan r3
(`flare-lahore-tools/dist/next/`). Not installed on the Civic.

## Fixes
* **Map sweeping to the GPS position** (Home widget and full map): the map opened at the saved / pack centre
  (mid-Pakistan for the Pakistan pack); the cached fix arrived before MapLibre was ready, so the next fix eased
  ~1 s across hundreds of km, on every return to Home. Now the map opens at the last known GPS position when
  following, the first follow is instant, and camera moves over 1.5 km are never animated.
* **Karachi did not route**: the search's top "Karachi" was a piece of an administrative boundary line (an OSM way
  in open country, no road within 1 km). The search builder no longer indexes border-only ways (roads that are
  also borders stay), and results rank by text match, then place type (city, town, suburb / neighbourhood / area,
  village, other places, streets, generic rows), then distance.

## Route options (major cities)
Trips between Lahore, Islamabad, Rawalpindi, Multan and Karachi (each end within 40 km of the city centre) offer the
fastest route plus listed alternatives, e.g. Lahore → Islamabad "Fastest · 3 h 32 min · 294 km · via N-5" or
"+16 min · 376 km · via M-2". GraphHopper's own alternative search took 5–25 s on BlueStacks, so the alternatives
are computed once on the PC (`flare-lahore-tools/tools/java/RouteCorridors.java`) and stored as one point on each
alternative's own carriageway (`RouteCorridors.kt`, 20 corridors); the app routes "through that point" (CH,
0.9–2.9 s for all options on BlueStacks). The chosen option's point is saved (`offline_nav_via`, new key) and used
for reroutes until the car is within 2 km of it, so a wrong turn does not switch the trip to the other road. Times are
GraphHopper estimates without traffic. No options: Lahore ↔ Karachi, Multan ↔ Karachi (none found within 1.6× of
the fastest time), and trips outside the five cities.

## Tests
Unit 76 pass. BlueStacks (isolated test package, its IPv4 blocked): 32 PASS / 0 FAIL including options and
reroutes that keep the chosen road for 6 city pairs; UI: "karachi" → city first → 1,216 km route; Islamabad
options dialog → M-2 chosen → kept after an app restart; frames on map open / Home return show no sweep.
