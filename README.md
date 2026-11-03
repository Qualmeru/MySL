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
./gradlew assemblePersonalDebug assembleFriendDebug
```

There are two flavours:

| Flavour    | Starting favourites            | APK                                                     |
|------------|--------------------------------|---------------------------------------------------------|
| `personal` | Frihetsvägen, Helenelunds station | `app/build/outputs/apk/personal/debug/app-personal-debug.apk` |
| `friend`   | None                           | `app/build/outputs/apk/friend/debug/app-friend-debug.apk`     |

The starting favourites only apply on a fresh install. After that, the app keeps whatever you've saved.

On Windows, [`build.ps1`](build.ps1) builds both and copies them to `build\MySL.apk` and `build\MySL-friend.apk`. It expects JDK 17 and the SDK in `C:\tmp\android-tools`, and uses low-memory settings so it builds on a busy machine.

## Installing

Copy the APK to the phone and open it, allowing installs from that source when Android asks. Or, with USB debugging on:

```bash
adb install -r build/MySL.apk
```

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
