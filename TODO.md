# Ideas for later

Things noticed while building 1.1 and 1.2 that are worth doing, roughly most useful first.
Tick them off or delete them as they're done.

## Reliability

- [ ] **One shared rate limit for SL.** The app polls every minute while open and the background job every 15 minutes, each on its own. SL sometimes answers "Quota has been exceeded"; when it does, everything should back off together for a while instead of each part retrying.
- [ ] **Test on a real phone before each release.** There are no on-device tests: nearby stops, alerts, both widgets, share/import and backup restore are only checked by hand. A short checklist in the README, or Compose UI tests run on an emulator in CI, would catch what the unit tests can't.
- [ ] **Try a backup restore once:** `adb shell bmgr backupnow se.mybus`, uninstall, reinstall, check the favourites came back. (Use a test phone; uninstalling clears the data if the restore doesn't work.)
- [ ] **Keep departures across screen rotation.** They live in `remember` in `App()`, so rotating the phone empties them and shows "Loading…" until the next fetch. A `ViewModel` would keep them.

## Features

- [ ] **Alerts that fit the day:** quiet hours or only on weekday commute times, alerts per stop, and skipping SL's minor information messages (they come with an `importance_level`).
- [ ] **One-stop widget for any stop**, not only favourites, and a "choose again" tap when its stop has been removed (today it says to remove and re-add the widget).
- [ ] **Widget follows the app's theme setting.** It follows the phone's light/dark mode even if the app is set to always light or dark.
- [ ] **Show how many departures are hidden in the widget too**, as an option (the app shows it in the stop header).
- [ ] **Update check:** tell the user when a newer release is on GitHub (one request to the releases API at most once a day), since sideloaded apps don't update by themselves.
- [ ] **Search by line** ("where does the 179 stop near me"). SL's Transport API has no line-to-stops lookup; it would need Trafiklab's GTFS data or another API.
- [ ] **Share favourites as a QR code** as well as text, for passing them to a phone next to you.

## Accessibility

- [ ] **TalkBack:** line badges and rows should read as one sentence, e.g. "Bus 178 to Jakobsberg in 7 minutes, stand A", rather than separate fragments.
- [ ] Check text sizes and contrast with the phone's largest font setting, in the app and the widgets.

## Size and upkeep

- [ ] **Update dependencies.** Lint lists newer Compose BOM, Glance, WorkManager, Activity and Lifecycle versions, and targetSdk 35 is no longer the newest. Update one at a time and run CI after each.
- [ ] **Split `MainActivity.kt`** (departures screen, filter dialog and settings menu) into a file per screen.
- [ ] **APK size:** about 1.9 MB now. What's left is mostly code (Compose, Glance, WorkManager) and Glance's own widget layouts. Publishing as an App Bundle (Play Store) would let each phone download only its own CPU's native library; leaving out the x86 ones would save about 20 KB.
- [ ] **Back up the release key** (`mysl-release.jks` and `keystore.properties`) somewhere safe if that isn't done yet. Without it, no update can install over existing copies.
