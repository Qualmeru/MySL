# MySL

A small Android app that shows live departures from your favourite SL stops in Stockholm: buses, commuter trains, metro, trams and boats.

It uses SL's open [Transport API](https://www.trafiklab.se/api/our-apis/sl/transport/), which needs no API key.

## Features

- **Favourites:** search all of SL's ~6,500 stops and stations and add the ones you use. Remove them from the stop's ⋮ menu.
- **Live times:** the next 10 departures per stop, with line, destination, time left, clock time and platform/track. Cancellations and disruptions are highlighted.
- **Auto refresh:** updates every minute while the app is open. Pull down or tap ⟳ to refresh right away.
- **Direction filter:** each side of the street or each platform is a separate direction. Untick the ones you don't need, e.g. only trains towards the city.
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
| [`SlApi.kt`](app/src/main/java/se/mybus/SlApi.kt) | Calls the SL API, retries on rate limits (HTTP 429) and on SL's occasional empty answers |
| [`MainActivity.kt`](app/src/main/java/se/mybus/MainActivity.kt) | Departures screen, refresh loop, direction filter, settings menu |
| [`StopSearchScreen.kt`](app/src/main/java/se/mybus/StopSearchScreen.kt) | Stop search for adding favourites |
| [`Favorites.kt`](app/src/main/java/se/mybus/Favorites.kt) / [`Settings.kt`](app/src/main/java/se/mybus/Settings.kt) | Saved favourites, filters, theme and language |
| [`Strings.kt`](app/src/main/java/se/mybus/Strings.kt) | All Swedish and English texts |
| [`ThemeIcons.kt`](app/src/main/java/se/mybus/ThemeIcons.kt) / [`Flags.kt`](app/src/main/java/se/mybus/Flags.kt) | Vector icons for the settings menu |

Built with Kotlin and Jetpack Compose (Material 3). No third-party networking libraries.
