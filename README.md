<div align="center">
  <img src="docs/flare-auto-icon.svg" width="256" height="256" alt="Flare Auto icon" />
  <h1>Flare Auto</h1>
  <p><strong>An open-source launcher designed for car head units.</strong></p>
  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0--only-blue.svg" alt="License: GPL-3.0-only" /></a>
    <a href="https://github.com/Afshaal/flare-auto/pulls"><img src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg" alt="Pull requests welcome" /></a>
  </p>
</div>

Flare Auto runs on the head unit itself. CarPlay and Android Auto require a separate compatible app and hardware.

---

## The dashboard

The home screen brings together a clock, date, optional weather or sunrise/sunset information, connection indicators and a media card. Choose which cards appear, swap their positions, adjust the split, and configure the bottom dock with your preferred shortcuts.

The media card can show the current track, artist, artwork and playback controls when a compatible app provides them. The app drawer supports hidden apps, icon packs and individual custom icons.

## Screenshots

| Dark red theme | Dark teal theme |
| --- | --- |
| ![Flare Auto home screen with red accents](docs/screenshots/home-current.png) | ![Flare Auto home screen with teal accents](docs/screenshots/home-second.png) |

Screenshots show a demonstration media track and artwork.

---

## Features

**Home screen**

- Configurable clock, media and app-shortcut cards; adjust card positions and split.
- Media title, artist, artwork, playback controls and seek bar when supported by the source.
- Customizable dock, shortcut order, labels and app assignments.
- Wi-Fi, Bluetooth and supported cellular indicators.
- Interactive Home customization: hold cards to change or swap them, configure corner widgets, and arrange dock shortcuts.
- Separate vehicle-name and trim-badge colors, clock styles, and Home/app-drawer/settings scale controls.

**Make it yours**

- Dark themes, accent color, optional glass-style cards and gradient wallpapers.
- Custom images for Home, the app drawer and screensaver.
- Changeable vehicle label and badge text.
- App drawer with hidden apps, installed icon-pack support and individual icon choices.
- Clock or image screensaver with an optional idle timeout and selectable clock styles.
- One wallpaper collection for Home, the app drawer and screensaver, with independent flip controls for gradients.
- Optional animations and drawer pages that follow a swipe with a gentle fade.

**Startup**

- Optionally launch an app or the screensaver after startup.
- Choose a radio or Bluetooth startup source where the head unit supports it, or leave the source to the system.

**Optional online features**

- Enable hourly weather forecasts to download and cache them for offline use. Choose an offline age limit (two days by default); expired or unavailable forecasts fall back to sunrise/sunset.
- Use **Launcher settings → About → Check for updates** to check the latest stable GitHub release manually. There are no automatic update checks. A downloaded update must match the app's package and signing key and have a higher version code, then Android asks you to confirm installation.

## Requirements

| | |
| --- | --- |
| Minimum Android | Android 8.0 (API 26) |
| Target Android | Android 9 (API 28) |
| Orientation | Landscape |
| Network | Not required for launcher operation |
| Validation | v1.1.0 on an Android 9 head unit |

Other devices may behave differently. Vendor specific shortcuts and startup source selection depend on the head unit and installed apps.

## Installation

1. Download the APK attached to the GitHub release and copy it to a USB drive or another accessible location on the head unit.
2. Open the APK with the head unit’s file manager and follow Android’s installation prompts.
3. Open Flare Auto and select it as the default Home app when prompted, or use the **Default launcher** option in Launcher settings.
4. Configure app shortcuts in Launcher settings. Grant **Media access** to display supported media sessions, and grant location access or enter coordinates for sunrise and sunset.

## Recommended setup

Flare Auto is designed primarily for low-performance Android head units with limited RAM. On these devices, disabling Android's interface animations can make navigation feel quicker.

### CarPlay and Android Auto

Flare Auto provides shortcuts to projection apps; it does not provide CarPlay or Android Auto itself.

- **Apple CarPlay:** Try [DiPlay](https://github.com/shihabal3amri/DiPlay) on compatible head units.
- **Android Auto:** Try [DiAuto](https://github.com/shihabal3amri/DiAuto) on compatible head units.

Install the appropriate app on the head unit, then assign it to the matching shortcut in **Launcher settings**. Already using ZLink or another compatible projection app? Assign it to the CarPlay or Android Auto button in Launcher settings. The button opens the selected app; connection and compatibility depend on that app and the head unit.

### Performance

To disable Android's interface animations:

1. Open **Android Settings → Developer options**.
2. Set **Window animation scale**, **Transition animation scale**, and **Animator duration scale** to **Animation off**.

This removes visual transitions and can make the interface feel more responsive. It won't necessarily improve playback or app performance. Flare Auto also has its own **Animations** switch under Appearance; Android's Animator duration scale must be enabled to see launcher animations.

If you're comfortable with ADB, review preinstalled apps and disable ones you've confirmed are unnecessary. Record your changes so you can reverse them, and test radio, Bluetooth and the backup camera afterward. System packages vary by device, so avoid applying a generic removal list.

## Roadmap

v1.1.0 completes the Kotlin migration and adds optional animations, weather caching, and expanded customization. Future work will keep the launcher lightweight for lower-performance head units.

- **More home-screen widgets:** Additional cards and options for arranging them.
- **Better media handling:** Improve source selection and investigate artwork disappearing after switching to Bluetooth.
- **More personalization:** Additional ways to configure shortcuts, icons, and card layouts.
- **Setup improvements:** Make permissions and first-time configuration easier to understand.
- **Performance:** Continue reducing unnecessary background work and memory use.

These are planned features and may change.

## Permissions and privacy

The launcher requests location for solar times and optional weather, network state for connection indicators, and Bluetooth access for status. Media access is optional and requires the Android notification-listener setting. Solar times use the last known device location or manually entered coordinates; when neither is available, the display shows `--:--`. The device time zone is used.

Weather is disabled by default. When enabled, the selected coordinates are sent to [Open-Meteo](https://open-meteo.com/) to download an hourly forecast; cached forecasts remain usable offline within the selected age limit. Manual update checks contact the public GitHub release API. APK downloads occur only after you choose Download; Android may ask you to allow installation from Flare Auto. No GitHub login or token is required in the app.

## Known limitations

- Switching to the head unit’s Bluetooth source during CarPlay playback can cause the media card to lose artwork. v1.1.0 improves session selection and temporary artwork retention, but the DiPlay routing combination still needs more results.
- Radio, Bluetooth, CarPlay and Android Auto shortcuts launch other apps; availability and behavior depend on the device.
- Initial source selection and startup actions are vendor-dependent.
- Sunset-based dimming is inactive when no solar location is available.

## Build from source

The project uses the Gradle wrapper, the Android Gradle Plugin, and the Kotlin Android plugin. The app uses Android framework APIs directly and does not depend on AndroidX.

### Prerequisites

- JDK 17 or newer.
- Android SDK Platform 35 and the Android SDK Build-Tools installed through Android Studio or `sdkmanager`.
- Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the SDK location if it is not in the default location.

The Gradle wrapper downloads Gradle and the declared build plugins on first use. Build a debug APK with:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Run the available local JVM tests with:

```sh
./gradlew testDebugUnitTest
```

All application sources and tests are Kotlin. Source files, resources, assets, manifest permissions and launcher behavior remain in the repository's existing top-level directories.

An APK signed with a different key cannot update an existing installation. Uninstalling the existing app can remove its data. Keep signing keys private.

## Credits and license

Copyright © 2026 Afshaal — Flare Auto. Licensed under GPL-3.0-only. Third-party components retain their respective copyrights.

Flare Auto includes media-session code adapted from [Dashline](https://github.com/metehankaygsz/dashline), copyright 2026 Metehan Kaygisiz, GPL-3.0-only. See [LICENSE](LICENSE) and [CREDITS.md](CREDITS.md).

Gradient credits are in [GRADIENT-ATTRIBUTION.md](GRADIENT-ATTRIBUTION.md). Simple Icons credits and notices are in [SIMPLE-ICONS-ATTRIBUTION.txt](SIMPLE-ICONS-ATTRIBUTION.txt) and `assets/`. Brand names and marks belong to their respective owners; their inclusion does not imply affiliation or endorsement. Review rights to bundled brand artwork before distribution.
