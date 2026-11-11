# Ideas for later

Things noticed while building 1.1 and 1.2 that are worth doing, sorted by priority: how much it matters to people using the app (or to being able to keep releasing it), weighed against the work. The area is in brackets.
Tick them off or delete them as they're done.

## High: do next

Cheap, or something that can't be undone if it goes wrong.

- [ ] *(Upkeep)* **Back up the release key** (`mysl-release.jks` and `keystore.properties`) somewhere safe if that isn't done yet. Without it, no update can install over existing copies.
- [ ] *(Reliability)* **Try a backup restore once:** `adb shell bmgr backupnow se.mybus`, uninstall, reinstall, check the favourites came back. (Use a test phone; uninstalling clears the data if the restore doesn't work.) It's the headline of 1.2.0 and hasn't been seen working yet.
- [x] *(Upkeep)* **Take the version from the release tag.** `versionCode` and `versionName` are edited by hand before each release, and nothing stops tag `1.3.0` from publishing an app that still says 1.2.0. Either derive them from the tag in `android-release.yml`, or have it fail when the tag and `versionName` differ. Done: the release workflow stops if the tag and `versionName` differ, or `versionCode` didn't go up (`scripts/check-release-version.sh`).
- [x] *(Reliability)* **One shared rate limit for SL.** The app polls every minute while open and the background job every 15 minutes, each on its own. SL sometimes answers "Quota has been exceeded"; when it does, everything should back off together for a while instead of each part retrying. Done: `SlCooldown.kt`, 1 minute doubling to 15.
- [x] *(Feature)* **Update check:** tell the user when a newer release is on GitHub (one request to the releases API at most once a day), since sideloaded apps don't update by themselves. Without it, friends stay on whatever version they first installed. Done: banner in the app (`UpdateCheck.kt`).
- [x] *(Reliability)* **Test on a real phone before each release.** There are no on-device tests: nearby stops, alerts, both widgets, share/import and backup restore are only checked by hand. A short checklist in the README, or Compose UI tests run on an emulator in CI, would catch what the unit tests can't. Done as a checklist: README, "Releasing". Compose UI tests on an emulator are still an option.

## Medium: worth doing soon

Noticeable improvements or small bugs, a few hours each.

- [x] *(Feature)* **Widget follows the app's theme setting.** It follows the phone's light/dark mode even if the app is set to always light or dark. Done: `WidgetStyles`; the widgets also redraw when the theme or language changes in the app.
- [x] *(Reliability)* **Keep departures across screen rotation.** They live in `remember` in `App()`, so rotating the phone empties them and shows "Loading…" until the next fetch. A `ViewModel` would keep them. Done more simply: `MainActivity` handles rotation and resizing itself (`android:configChanges`), so nothing is recreated.
- [x] *(Feature)* **Own app icon.** The manifest still uses Android's generic `ic_menu_directions`. An adaptive icon (SL-style, "MySL") with a monochrome layer for themed icons would make it easy to find, in the launcher and the widget picker. Done: a white bus front on SL blue, with a themed-icon layer.
- [ ] *(Feature)* **Alerts that fit the day:** quiet hours or only on weekday commute times, alerts per stop, and skipping SL's minor information messages (they come with an `importance_level`).
- [ ] *(Feature)* **One-stop widget for any stop**, not only favourites, and a "choose again" tap when its stop has been removed (today it says to remove and re-add the widget).
- [x] *(Accessibility)* **TalkBack:** line badges and rows should read as one sentence, e.g. "Bus 178 to Jakobsberg in 7 minutes, stand A", rather than separate fragments. Done: `Strings.describe`, used by the app's rows and the widgets'.
- [ ] *(Accessibility)* Check text sizes and contrast with the phone's largest font setting, in the app and the widgets.
- [ ] *(Upkeep)* **Update dependencies.** Lint lists newer Compose BOM, Glance, WorkManager, Activity and Lifecycle versions, and targetSdk 35 is no longer the newest. Update one at a time and run CI after each. Gradle 8.11 / AGP 8.7 also can't run on Java 25, which is why VS Code's Java extension offers "Upgrade Gradle to 9.x" when opening the project; upgrade Gradle and AGP together (Gradle 9 alone breaks AGP 8), not from that prompt.

## Low: nice to have

Bigger features, or small gains.

- [ ] *(Feature)* **"Leave now" reminder.** Tap a departure to get a notification a few minutes before you need to leave, updated if SL's expected time changes. The disruption alerts don't cover this: they're about notices, not a chosen departure.
- [ ] *(Feature)* **Show how many departures are hidden in the widget too**, as an option (the app shows it in the stop header).
- [ ] *(Feature)* **Share favourites as a QR code** as well as text, for passing them to a phone next to you.
- [ ] *(Feature)* **Search by line** ("where does the 179 stop near me"). SL's Transport API has no line-to-stops lookup; it would need Trafiklab's GTFS data or another API.
- [ ] *(Upkeep)* **Split `MainActivity.kt`** (departures screen, filter dialog and settings menu) into a file per screen.
- [ ] *(Size)* **APK size:** about 1.9 MB now. What's left is mostly code (Compose, Glance, WorkManager) and Glance's own widget layouts. Publishing as an App Bundle (Play Store) would let each phone download only its own CPU's native library; leaving out the x86 ones would save about 20 KB.
