# Widgets, profiles and offline maps — local preview, 2026-11-11

## Added
- Installed application widgets in the widget gallery using Android AppWidgetHost. Includes provider preview, Android binding permission, provider configuration, draft placement, resize options, removal and cancellation cleanup. Host listening follows Home's lifecycle. No AndroidX.
- More three-card presets: clock left/right/above, media left/right, columns, and three map/media/clock arrangements. Existing one/two-card choices retained; no four-card presets added.
- Long-press a custom driver profile to rename/change avatar or delete with confirmation. Active-profile deletion switches to Family. Settings, custom images and hosted widget instances owned by the deleted profile are removed. Family/Guest protected.
- Optional Offline maps Settings category: enable toggle, custom center / GPS, visible map span, GPS follow, map-card sizing via editor, manual region catalog, direct HTTPS source, download/import, cancellation/status, storage and delete controls.
- Native Mapsforge renderer and an unmodified 3.28 MB world-overview dataset included in the APK. Light render theme and custom dark render theme. Detailed regional files remain separate manual downloads/imports, shared across profiles.
- Maps can occupy the large card beside smaller media and clock cards. Pan/zoom/recenter and GPS position work without internet. Pan gestures stay with the map rather than changing Home pages.
- Renderer resources and GPS updates are stopped when the containing screen pauses/detaches. Tile cache follows measured viewport/overdraw; unnecessary square rotation buffers disabled for the north-up map. Renderer workers capped at two. File opening is separate from download IO so a transfer cannot hold up opening an existing map.
- Downloaded maps are validated before replacing previous data. A failed import/download keeps the old map. Downloads use HTTPS, have a 512 MB limit, and preserve a storage reserve. No automatic map downloads.

## Validation
- Release/debug builds and release-vital lint passed. 12 JVM tests passed, including hosted-instance identity/resize and malformed-ID cases.
- Eight Android model checks passed, including profile edit/delete, settings/image cleanup, default-profile fallback and protection of Family/Guest.
- BlueStacks Android 9: provider gallery, actual Android widget binding prompt, provider configuration, explicit placement/Save and live RemoteViews rendering on Home.
- Custom profile management menu and delete confirmation inspected on the emulator.
- Mapsforge region catalog navigation and manual region download tested using Monaco as a disposable test region (not bundled).
- Custom map coordinates, 2 km span and GPS follow controls saved correctly. Regional street rendering and out-of-coverage GPS notice verified.
- Cold-start Home map/media/clock layout rendered with emulator Wi-Fi and mobile data disabled. Connectivity restored afterwards.
- Invalid map import preserved the previous map byte-for-byte. Regional test map removed afterwards.
- Original Home and driver preferences restored and compared semantically after isolated tests. Previous APK preserved, package/version/signing unchanged. Signed release installed in BlueStacks.

## Limits / remaining choices
- This is map display and GPS positioning, not route calculation, address search or turn-by-turn/spoken navigation.
- No city/region was supplied for bundling detailed coverage. The bundled 2016-02-20 world file contains outlines, not streets. Detailed regions can be selected in Settings.
- Regional downloads use the provider's full published coverage. Map span controls the visible frame; it does not crop/rebuild the downloaded file to an arbitrary bounding box.
- Manual transfers do not resume across process death. Finished new map data is picked up on return to Home.
- All third-party providers/configuration screens, extreme screen sizes, actual Civic GPS, physical touch responsiveness and sustained head-unit performance have not been verified.
- Existing version remains 1.1.0 / code 3. No GitHub changes or car installation.

## Files
- APK: ../FlareAuto-widgets-offline-maps-preview.apk
- Evidence: ../flare-maps-widgets-check/
- Third-party notices: assets/notices/MAPS-NOTICE.md, LGPL-3.0.txt, androidsvg-LICENSE.txt, OSM-ODbL.html and CREDITS.md.
