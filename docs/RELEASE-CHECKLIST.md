# Release checklist

## Before every release

1. **License.** Read [LICENSING.md](LICENSING.md); the Dashline-derived files make the APK GPL-3.0-only as a whole.
2. **Vehicle labels.** `Defaults` in `src/app/flare/auto/KnownApps.kt` is empty (generic build); earlier
   installs keep CIVIC / RS through `ProfileStore.keepVehicleLabels`.
3. **Brand marks.** Review the trademark use listed in LICENSING.md (CarPlay / Android Auto buttons).
4. **Version.** Bump `versionCode` and `versionName` in `app/build.gradle.kts` (the manifest no longer repeats them).
   The in-app updater only offers APKs with a higher version code and the same signing key.
5. **Signing.** Copy `keystore.properties.example` to `keystore.properties` (git-ignored) or set the `FLARE_*`
   environment variables. Never commit the keystore or passwords.
6. **Build and checks.**
   ```sh
   ./gradlew clean assembleRelease testDebugUnitTest lintDebug
   ```
   Lint must report 0 errors. Release APK: `app/build/outputs/apk/release/`.
7. **Shrinker check.** The app's own drawables (`ic_launcher`, `ic_carplay_mark`, `ic_android_auto_mark`) must be in
   the release APK (nothing is looked up by name any more since the Simple Icons catalog was removed in r3):
   ```sh
   apkanalyzer resources names --config default --type drawable app/build/outputs/apk/release/app-release.apk | grep -c "ic_launcher\|ic_carplay_mark\|ic_android_auto_mark"   # 3
   ```
   For a runtime check of the R8 rules, build the debug harnesses with the release rules and run them on a device:
   ```sh
   ./gradlew assembleDebug -Pflare.minifyDebug=true
   adb shell am start -S -W -n app.flare.auto/.SmokeActivity --es open apps    # also settings, editor, picker, hidden, map, saver, nav
   adb shell am start -S -W -n app.flare.auto/.OptimizationChecksActivity
   adb logcat -s FlareChecks FlareNav
   ```
   Then reinstall the signed release build.
8. **Device test** on the head unit: Home, editor, dock, map, navigation and an update over the previous release.
9. **Navigation pack.** Build it with `flare-lahore-tools` (`./build-lahore.sh`); it is *not* in the APK. If you publish it,
   keep `build-info.json` and the ODbL notice with it (see LICENSING.md).
10. **GitHub release.** Attach the signed APK. `UpdateChecker` only accepts assets under
    `https://github.com/Afshaal/flare-auto/releases/download/`.

## If the applicationId or Kotlin package is renamed later

The id `app.flare.auto` is both the Android `applicationId` and the Kotlin package. Places to change:

| Where | What |
|---|---|
| `app/build.gradle.kts` | `namespace` and `applicationId` |
| `AndroidManifest.xml` | 15 fully qualified component names (13 activities, `UpdateFileProvider`, `MediaAccess`) |
| `app/src/debug/AndroidManifest.xml`, `app/src/debug/res/xml/widget_probe.xml` | debug harness components |
| `src/app/flare/auto/`, `app/src/debug/java/app/flare/auto/`, `test/app/flare/auto/` | directory names and the `package` line of 99 Kotlin files |
| `app/proguard-debug-checks.pro` | `-keepclassmembers class app.flare.auto.UpdateChecker` |
| `src/app/flare/auto/UpdateChecker.kt` | GitHub owner/repository (`Afshaal/flare-auto`) if the repository moves |
| Docs | README, CREDITS.md, this file, flare-lahore-tools README (`Android/data/app.flare.auto/files/navigation`) |

The `UpdateFileProvider` authority (`${applicationId}.updates`) and `content://<package>.updates/…` are derived
automatically.

**Changing the applicationId makes Android treat it as a different app.** Existing installs keep the old app;
the new one starts empty and the in-app updater cannot move users across. Lost with the old id:

* Preferences: `home` (Family profile), `driver_<uuid>` (each custom profile), `drivers` (profile list, startup
  mode, default driver), `forecast_cache`, `offline_map_data`, `startup_device`, `navigation_pack`.
* Files: custom wallpapers and imported icon images (`files/*wallpaper_*`), downloaded or imported map regions
  (`files/offline_maps/`), the copied navigation pack (`Android/data/<id>/files/navigation/`).
* System grants and bindings: notification access (media card), location permission, "install unknown apps"
  permission, the default Home app choice, and every hosted app widget (widget ids belong to the old package).

Renaming only the Kotlin package (keeping `applicationId`) keeps all of that, but changes component class names:
the default-Home choice and notification access are stored by component name and would have to be granted again.
