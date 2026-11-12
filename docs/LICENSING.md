# Licensing overview

This page lists every third-party component in Flare Auto and in the offline navigation pack, and what the
combination means. It does **not** choose a license; the owner decides. The repository currently ships
`LICENSE` = GNU GPL version 3, and the README states "GPL-3.0-only".

## Code compiled into the APK

| Component | Version | License | Where | Notes |
|---|---|---|---|---|
| Flare Auto | 1.1.0 | GPL-3.0-only (owner's choice, see `LICENSE`) | `src/` | — |
| Dashline MediaMonitor (adapted) | 2026 | **GPL-3.0-only** | `src/app/flare/auto/NowPlaying.kt`, `MediaGlyph.kt` | Modification notices kept in both files. |
| GraphHopper core + API | 1.0 | Apache-2.0 | Maven | Offline routing. |
| HPPC | 0.8.1 | Apache-2.0 | Maven (via GraphHopper) | |
| JTS Topology Suite | 1.15.1 | EPL-2.0 **or** EDL-1.0 (BSD-3-Clause), recipient's choice | Maven (via GraphHopper) | Use it under EDL-1.0, which is GPL-compatible. EPL-2.0 alone is not GPL-3.0 compatible unless the secondary-license clause applies. |
| Apache XML Graphics Commons | 2.3 | Apache-2.0 | Maven (via GraphHopper) | Mostly removed by R8. |
| Apache Commons IO | 1.3.1 | Apache-2.0 | Maven (via GraphHopper) | Mostly removed by R8. |
| SLF4J API | 1.7.30 | MIT | Maven (via GraphHopper) | No binding bundled (logging is a no-op). |
| Mapsforge map, reader, Android, themes | 0.25.0 | LGPL-3.0, sections 4(d) and 4(e) waived by Mapsforge | Maven | Full notice in `assets/notices/MAPS-NOTICE.md` and `LGPL-3.0.txt`. |
| AndroidSVG | 1.4 | Apache-2.0 | Maven (via Mapsforge) | `assets/notices/androidsvg-LICENSE.txt`. |
| MapLibre Native Android (OpenGL ES) | 13.0.2 | BSD-2-Clause | Maven `org.maplibre.gl:android-sdk-opengl` | 3D vector map; native `libmaplibre.so` for arm64-v8a and x86_64. |
| MapLibre gestures | 0.0.4 | BSD-2-Clause | Maven (via MapLibre) | |
| MapLibre GeoJSON / Turf | 6.0.1 | Apache-2.0 | Maven (via MapLibre) | |
| OkHttp / Okio | 4.12.0 / 3.6.0 | Apache-2.0 | Maven (via MapLibre) | Present for MapLibre's online sources; the app switches MapLibre's network off and its style uses only local files. |
| Gson, Timber | 2.10.1, 5.0.1 | Apache-2.0 | Maven (via MapLibre) | |
| AndroidX (annotation, core, fragment, lifecycle, interpolator…) and kotlinx-coroutines | various | Apache-2.0 | Maven (via MapLibre) | Pulled in by MapLibre; the app's own code still uses no AndroidX. |
| Kotlin standard library | 2.0.21 | Apache-2.0 | Maven | |
| JetBrains annotations | 13.0 | Apache-2.0 | Maven (compile-time) | |

Test-only, not in the APK: JUnit 4.13.2 (EPL-1.0), org.json 20231013 (public domain / JSON license).

## Assets and data in the APK

| Item | License / terms | Notes |
|---|---|---|
| ~~Simple Icons drawables and catalogs~~ | removed in 1.2.0-maplibre-r3 | No third-party brand artwork is bundled any more; the drawer shows each app's installed icon. The CarPlay / Android Auto button marks (`ic_carplay_mark`, `ic_android_auto_mark`) remain and are trademarks of their owners. |
| CarPlay and Android Auto marks (`ic_carplay_mark`, `ic_android_auto_mark`) | Trademarks of Apple Inc. and Google LLC | Used only to label buttons that open third-party projection apps. |
| Gradient palette | MIT (720pixel free-gradient-tools) | `GRADIENT-ATTRIBUTION.md`. |
| Bundled `assets/maps/world.map` | Map data © OpenStreetMap contributors, **ODbL 1.0** | Unmodified Mapsforge world overview (2016-02-20). |
| Map render theme (`assets/maps/dark.xml`) | Project's own, derived from the Mapsforge default theme (LGPL-3.0) | |

## Data used at runtime

| Service / data | Terms | Attribution in the app |
|---|---|---|
| OpenStreetMap data (world map, navigation pack map, routing graph, place search) | **ODbL 1.0**, © OpenStreetMap contributors | Map card credit line, offline map screen, destination search, Settings › Offline maps › Map credits, Settings › About › Open-source credits. |
| Open-Meteo weather (optional, off by default) | Data **CC BY 4.0**; free API for non-commercial use | Settings › About › Open-source credits; the enable dialog names the provider. |
| GitHub releases API (manual update check only) | GitHub terms | — |

## The offline navigation pack (built by flare-lahore-tools)

The pack (`gh/` routing graph, `lahore.map`, `search.sqlite`) is built from the Geofabrik Pakistan extract
of OpenStreetMap (ODbL 1.0). Tools used to build it, not shipped in the APK: GraphHopper 1.0 (Apache-2.0),
Osmosis 0.49.2 (public domain core; plugins under their own licenses), mapsforge-map-writer 0.25.0 (LGPL-3.0),
Planetiler 0.10.2 (Apache-2.0). Revision 3 adds `vector/`: vector tiles (another ODbL derivative database), a
style written for this project, and label glyphs converted from Noto Sans / Noto Sans Arabic (SIL OFL 1.1, licence
text shipped as `vector/FONTS-OFL.txt`). No Google or other proprietary map assets are used; the night style
is only inspired by common dark navigation maps.

* The routing graph and the search database are **derivative databases** of OpenStreetMap. If you distribute
  the pack publicly, ODbL requires you to keep the attribution, offer the derivative database (or the
  changes/method used to make it, e.g. the pipeline in flare-lahore-tools) under ODbL, and not add
  restrictions such as DRM. The rendered `.map` file is a database too, not a "produced work".
* Keep `build-info.json` (it records the source, date and license) with any copy of the pack.

## What the combination implies (for the owner to decide)

1. **GPL-3.0 governs the APK as a whole.** The Dashline-derived files are GPL-3.0-only, so any distributed
   build containing them must be licensed as a whole under GPL-3.0 (version 3 only; not "or later", because
   Dashline does not grant later versions). Replacing those two files with independent code would remove this
   constraint.
2. All other code dependencies are compatible with GPL-3.0: Apache-2.0, MIT, LGPL-3.0 (with Mapsforge's
   waiver), and JTS under its EDL-1.0 option.
3. Brand marks are not licensed by CC0; their use is governed by trademark law and brand guidelines.
4. OpenStreetMap-derived data (world map, navigation pack) is under ODbL regardless of the code license.
5. Open-Meteo's free tier is for non-commercial use; a commercial release would need their commercial plan or
   another weather source.
