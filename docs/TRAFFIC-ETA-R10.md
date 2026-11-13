# Typical traffic adjustment of offline ETAs (maplibre-r10-eta), 2026-11-13

APK: `work/FlareAuto-v1.2.0-maplibre-r10-eta.apk` (1.2.0 / 4, same local signing key, SHA-256
`6a98129804a2a7b3f6345125d1b4005080da715d140c0bdde0644588e742b3c4`). Baseline r9 APK unchanged. Not on the Civic.

**These multipliers are user settings, not calibrated or measured traffic.** No live data, network or Gemini
observations are used, and route selection is unchanged (GraphHopper CH, `MAX_OPTION_FACTOR` 1.6 option filter,
reroute triggers all as before).

## Settings (Offline maps, per driver profile, new keys only)
"Typical traffic adjustment" (off by default) with a short explanation; when on: peak delay multiplier (1.1–3.0×,
default 2.0×), morning 07:30–09:30, evening 16:30–19:30, "School dismissal (Jail Road)" switch and its time
13:30–15:30, days (default Monday–Friday). Keys: `offline_eta_traffic`, `offline_eta_peak` (tenths),
`offline_eta_morning`, `offline_eta_evening`, `offline_eta_school`, `offline_eta_school_time`, `offline_eta_days`
(bit 0 = Monday); journey length `offline_nav_trip_m`.

## Model (`TrafficEta`)
* Base time: GraphHopper's per-instruction times are now kept (`NavRoute.stepMillis`, summing to `millis`). Turn
  time that GraphHopper adds to the route total but not to instructions is given to the instructions after the
  first (each starts with a manoeuvre). Remaining base time = later instructions + the current one interpolated by
  distance (constant speed only inside one instruction). Routes without instruction times fall back to the old
  average-speed estimate. `millis` and `stepMillis` are never modified.
* Factor per eligible stretch: `1 + (peak - 1) * clamp((D - 5)/5, 0, 1) * weight`, D = the journey's planned
  length in km. `weight` ramps 0→1 over the first 15 minutes of a window and back over the last 15; 0 outside the
  windows and on unselected days. Morning/evening apply to the Wapda Town–Barkat area, the school window to the
  Jail Road corridor; overlaps take the highest factor, never a product. Outside areas/windows: the base estimate.
* D is fixed by the journey's first route, kept on reroutes and restoration (pref above, shared by the Home widget
  and the full map) and cleared on a new destination, arrival or End. While choosing between options each option is
  scaled by its own length.
* Per route, cumulative base / area / corridor / both times per geometry point are built once on the routing
  thread (cached per route); progress updates only interpolate. The schedule part is re-read about every 30 s while
  guidance is shown (a 30 s refresh of the shown estimates on the guidance's existing handler; it stops when the
  map or Home closes). No GPS requests, routing or background work were added.

## Areas (built from the Lahore pack's OSM data, reviewable in `TrafficEta.kt`)
* **Wapda Town – Barkat Market** (morning, evening): 8-point polygon from south-west of Wapda Town (31.437,
  74.270) to past Kalma Chowk, covering Johar Town, Maulana Shaukat Ali Road, Akbar Chowk, Faisal Town, Model Town
  Link Road, New Garden Town and Barkat Market (31.500, 74.321). Its west edge stays east of 74.262, where the M-2,
  N-5 and Thokar Niaz Baig flyover lie in OSM (all at or west of it); no Ring Road in this band. The Canal Road
  (trunk) stretch inside it counts as a connecting road. Stretches are classified by segment midpoint.
* **Jail Road** (school dismissal): the 25 mapped Jail Road ways (Qurtaba Chowk to Sherpao Bridge; OSM ids in the
  source), each simplified to within 5 m; a stretch counts within 40 m of them and within 30° of their direction
  (crossing streets do not). "Kacha Jail Road" and "Link Jail Road" are not included.
* Everything else (rest of Lahore, other packs, motorways, Ring Road) keeps the base estimate.

## Displays using the one estimate
Route overview list and selected-route trip card; active navigation (full map and Home widget, arrival clock and
remaining time); restored navigation and reroutes; "Other routes" comparisons, computed from the car's current place
on both routes (the shared stretch before the split), refreshed on each 30 s tick and when an alternative is passed.

## Tests
Unit 98 pass (new `TrafficEtaTest`: off / non-peak / outside area; 5, 7.5 and 10 km scaling; weekday and window
boundaries; overlap without compounding; partial area coverage; mixed-speed remaining time; turn-time split; journey
length kept through reroutes; preview summary = navigation estimate; corridor direction and area edges). Lint 0
errors.

BlueStacks (r10 installed over the main app, data kept; a temporary debug copy only supplied simulated GPS and the
Home widget check, then was removed with its network rule). Friday 18:28 (evening window), Wapda Town → Barkat
Market, 11.3 km: adjustment off 12 min (arrival 6:42); on 24 min (6:55) in the overview, active navigation and after
restarting the app (restored, 24 min); Home widget 24 min (6:57 PM a minute later). The setting was switched back to
its default (off) and the test journey ended.

## Limits / Civic checks
The multipliers, windows and areas are assumptions to review, not calibrated data. Area membership uses segment
midpoints (a long segment straddling the edge counts wholly in or out). Turn time is spread evenly over the
manoeuvres. Check on the Civic: the estimates on your usual Wapda Town – Barkat Market and Jail Road trips at the
configured times, and whether the polygon matches the roads you consider part of that commute.
