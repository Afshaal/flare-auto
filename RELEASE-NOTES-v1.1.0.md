# Flare Auto v1.1.0

This release migrates Flare Auto to Kotlin and expands the launcher customization while keeping Android 8.0 as the minimum supported version.

## Added

- Interactive Home preview with card long-press editing, corner-widget selection, vehicle/trim colors, clock styles and dock arrangement.
- Category-based settings, sliding switches, optional animations and fading app-drawer paging.
- Optional hourly weather, offline forecast caching with a configurable expiry, and sunrise/sunset fallback.
- A shared wallpaper collection across Home, the app drawer and screensaver, with gradient orientation controls.
- Separate sizing controls for Home, the app drawer and settings.
- Manual stable-release update checks under About, with package, version and signer verification before Android's installer opens.

## Improved

- Event-driven media-session handling and temporary artwork retention for supported sessions.
- Shared weather downloads, background image/icon loading and cleanup of activity-owned update work.
- Home preview lifecycle, dialog button handling and wallpaper thumbnails.
- Gradle-based source builds and automated tests. Full lint completes without errors.

## Validation and known limitations

The release was tested in BlueStacks, including forecast-cache checks, synthetic media sessions and a locally signed test update through Android's installer. Civic radio/Bluetooth/DiPlay routing, vendor controls and sustained real-device performance still require head-unit testing. The Bluetooth/CarPlay artwork issue remains listed until confirmed on the Civic. No measured RAM/FPS improvement is claimed.

CarPlay and Android Auto still require separate compatible apps and hardware. Core launcher operation remains available offline; weather and update checks are optional. Existing settings and the app signing key are retained.
