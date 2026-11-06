# MySL

A small Android app that shows live departures from your favourite SL stops in Stockholm: buses, commuter trains, metro, trams and boats.

It uses SL's open [Transport API](https://www.trafiklab.se/api/our-apis/sl/transport/), which needs no API key.

## Features

- **Favourites:** search all of SL's ~6,500 stops and stations and add the ones you use, or tap **Stops near me** to list the closest ones (asks for location the first time; "approximate" is enough). Reorder or remove them from the stop's ⋮ menu.
- **Live times:** the next 10 departures per stop, with line, destination, time left, clock time and platform/track. Cancellations and disruptions are highlighted.
- **Auto refresh:** updates every minute while the app is open. Pull down or tap ⟳ to refresh right away.
- **Direction and line filters:** each side of the street or each platform is a separate direction, and each line can be hidden too. Untick what you don't need in the stop's ⋮ → Choose directions, e.g. only trains towards the city, or no 553H. The stop's header says how many directions, lines and departures are hidden.
- **Home-screen widgets:** *All favourite stops* scrolls through every favourite; *One stop* shows a single stop you pick and fits a 2×2 spot. Both follow the stops' filters, refresh every 15 minutes (or on ↻), and drop departures as they leave.
- **Disruption alerts:** optional (⋮ menu → Disruption alerts). A notification when one of your stops gets a new disruption or a cancelled departure on a line you haven't hidden. Checked every 15 minutes with the widget's refresh, so it adds no extra requests to SL.
- **Backup, no login:** favourites, filters and settings are included in Android's own backup to the phone's Google account, and come back on a reinstall or a new phone. Departures, widget setup and other caches are left out. To move them by hand (or between copies signed with different keys), ⋮ → **Share favourites** sends them as one line of text, and ⋮ → **Import favourites** on the other phone adds them.
- **Line colours:** badges use SL's colours: red and blue buses, pink commuter trains, green/red/blue metro lines.
- **Theme:** system default, light or dark.
- **Language:** Swedish or English. System default picks Swedish if the phone is in Swedish.

## Building

Open the project in Android Studio and press Run, or build from the command line with JDK 17 and the Android SDK (platform 35):

```bash
./gradlew assembleStandardDebug assemblePresetDebug
```

There are two flavours:

| Flavour    | Starting favourites               | APK                                                           |
|------------|-----------------------------------|---------------------------------------------------------------|
| `standard` | None, you add your own            | `app/build/outputs/apk/standard/debug/app-standard-debug.apk` |
| `preset`   | Frihetsvägen, Helenelunds station | `app/build/outputs/apk/preset/debug/app-preset-debug.apk`     |

The starting favourites only apply on a fresh install. After that, the app keeps whatever you've saved.

Release builds are shrunk with R8 (about 2 MB instead of 12), keep only the English and Swedish texts of the libraries, and leave out Kotlin metadata nothing reads. The rules for what must survive R8 are in [`app/proguard-rules.pro`](app/proguard-rules.pro).

## Tests

```bash
./gradlew testStandardDebugUnitTest
```

Unit tests in [`app/src/test`](app/src/test/java/se/mybus) cover the parts that don't need a phone: reading SL's answers (including its "Quota has been exceeded" refusals), saving favourites and reading older saves, the share/import code, the direction and line filters, stop search and "near me", error messages, both languages, and which disruptions get announced.

[`scripts/check-release-dex.sh`](scripts/check-release-dex.sh) checks a release APK for code R8 must not remove: things Android or a library only reaches by reflection, which would otherwise only show up as a crash on the phone. Add a line there for every new activity, receiver or widget action.

### Continuous integration

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on every push to any branch and on every pull request: the unit tests for both flavours, Android Lint (errors fail, e.g. using an API newer than minSdk), a debug and a release build, and the R8 check. The release workflow runs the same tests and check before it publishes anything.

On Windows, [`build.ps1`](build.ps1) builds the release versions of both, signed with the release key, and copies them to `build\MySL.apk` (standard) and `build\MySL-preset.apk`. It expects JDK 17 and the SDK in `C:\tmp\android-tools`, and uses low-memory settings so it builds on a busy machine.

## Signing

Android only installs an update if it's signed with the same key as the copy already on the phone. So every APK that gets shared is signed with one release key, whichever PC builds it.

The key is **not in git**. To sign, put a `keystore.properties` next to `settings.gradle.kts`:

```properties
storeFile=C:/Users/<you>/.android/mysl-release.jks
storePassword=...
keyAlias=mysl
keyPassword=...
```

Use forward slashes in `storeFile`. With that file present, both debug and release builds are signed with the release key, so you can switch between them without losing data. Without it, Gradle warns and falls back to the PC's own debug key, and `build.ps1` refuses to build.

### Automatic release builds

[`.github/workflows/android-release.yml`](.github/workflows/android-release.yml) builds both APKs on GitHub whenever a tag like `1.0.2` or `v1.0.2` is pushed, or a release is published with a new tag. It attaches `MySL.apk`, `MySL-preset.apk` and `SHA256SUMS.txt` to the release. Write the notes before tagging, in [`release-notes/<version>.md`](release-notes) (first line `# Title`, then the notes); the release gets them. If you wrote the release in GitHub's form instead, its title and notes are kept. It can also be run by hand from the Actions tab.

It signs with the same release key, taken from two repository secrets (**Settings → Secrets and variables → Actions → New repository secret**):

| Secret | Value |
|--------|-------|
| `MYSL_KEYSTORE_BASE64` | The `.jks` file as base64 |
| `MYSL_KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |

Without them the workflow stops instead of publishing APKs that won't install over existing copies.

**Back up `mysl-release.jks` and `keystore.properties` somewhere safe.** If they're lost, updates can no longer install over existing copies, and everyone has to uninstall (losing their favourites) to get a new version. To build releases on another PC, copy both files there.

## Installing

Copy the APK to the phone and open it, allowing installs from that source when Android asks. Or, with USB debugging on:

```bash
adb install -r build/MySL.apk
```

Versions from 1.0.1 on are signed with the release key. If you have an earlier copy, uninstall it once before installing; after that, updates install over each other and keep your favourites.

## Code

| File | What it does |
|------|--------------|
| [`SlApi.kt`](app/src/main/java/se/mybus/SlApi.kt) | Calls the SL API, retries on rate limits (HTTP 429) and on SL's occasional empty answers, and turns SL's `{"error": ...}` answers into errors |
| [`MainActivity.kt`](app/src/main/java/se/mybus/MainActivity.kt) | Departures screen, refresh loop, direction and line filter dialog, settings menu |
| [`StopSearchScreen.kt`](app/src/main/java/se/mybus/StopSearchScreen.kt) / [`Search.kt`](app/src/main/java/se/mybus/Search.kt) | Stop search and "Stops near me" for adding favourites |
| [`PhoneLocation.kt`](app/src/main/java/se/mybus/PhoneLocation.kt) | The phone's position, from Android's own location service (no Google Play services) |
| [`Filters.kt`](app/src/main/java/se/mybus/Filters.kt) | Which departures a stop shows; shared by the app and the widgets |
| [`Disruptions.kt`](app/src/main/java/se/mybus/Disruptions.kt) | Disruption alerts: what's new since last time, and the notifications |
| [`Favorites.kt`](app/src/main/java/se/mybus/Favorites.kt) / [`Settings.kt`](app/src/main/java/se/mybus/Settings.kt) | Saved favourites, filters, theme, language and alerts |
| [`Strings.kt`](app/src/main/java/se/mybus/Strings.kt) | All Swedish and English texts |
| [`widget/`](app/src/main/java/se/mybus/widget) | Both home-screen widgets, the stop picker for the one-stop widget, and the 15-minute background fetch |
| [`ThemeIcons.kt`](app/src/main/java/se/mybus/ThemeIcons.kt) / [`Flags.kt`](app/src/main/java/se/mybus/Flags.kt) | Vector icons for the settings menu |

Built with Kotlin and Jetpack Compose (Material 3). No third-party networking libraries.
