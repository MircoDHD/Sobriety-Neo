# Changes in this fork

This file lists what differs from the upstream project
([seaglade/Sobriety](https://github.com/seaglade/Sobriety), last upstream commit `ad94ed6`).
Newest entries first.

## v11.0.0 - new look, statistics, savings in the current streak

First regular release. Signed with a private release key (earlier previews used a public debug
key, so they can not be updated in place: export, uninstall, install, import).

### Added
- **Two variants (product flavors):** `neo` (new look, main app, keeps the app id) and `classic`
  (original look, "Sobriety Neo Classic", app id suffix `.classic`).
- **New main screen (neo):** swipeable tabs (ViewPager2) with an optional "All" tab and user tabs
  filled by a rule (priority, running, stopped) and/or by hand; "Manage tabs" screen; sort order
  setting; new cards with a progress ring towards the next milestone; per-tracker hidden buttons
  (relapse always visible); true black (AMOLED) theme overlay.
- **Display options per tracker (both):** counter / countdown to the next milestone / both;
  maximum number of units and smallest unit shown (`DisplaySettings.kt`, `TimeFormat.kt`).
- **Statistics screen (both):** selector ("All" + one chip per tracker, remembered), four tiles
  with the key numbers in a short format with at most two units ("3 mo 2 w";
  `compactDurationUnits()` in `shared/.../Statistics.kt`, `compactDurationText()` in
  `utils/TimeFormat.kt`), and one chart at a time: relapses per month, or (per tracker, via a
  toggle) attempt lengths with the average of finished attempts. A hint replaces the chart while
  there is no relapse. Small built-in bar chart view (no chart library).
- **Savings in the current streak:** every saving is also calculated for the running attempt
  (`SavingTotal.currentStreak`, `SavingsSummary.timeCurrentStreakMinutes`, `hasCurrentStreak`),
  without subtracting relapse costs. Cards show "saved · this streak"; the Savings screen shows
  total, this streak and this month.
- **Default milestones (both):** 1 day, 1 week, 1/3/6 months, 1/2/5 years for new trackers;
  opt-out switch, editable list, "add to existing entries".
- **Formatted explanation of every setting** (HTML) behind the ⓘ on the settings screen.

### Changed
- Savings: new category **Time** (hours per day/week/month); the old "time saved per day" is moved
  into a Time saving when the data is loaded. The name is optional for money and time.
- Relapse dialog and note-after-relapse moved to `utils/TrackerDialogs.kt` (shared by both UIs).
- Data format: new key `display`.
- Releases are signed with a private key and published by hand; CI only runs the tests and
  builds the debug APKs (workflow artifact).

## v10.2.0-neo.1-preview - savings, explanations, diagnostics

### Added
- **Savings categories and totals** (`shared/.../SavingsCalculator.kt`). Each saving has a category
  (money / other) and a period (per day / week / month). Totals are calculated as
  `amount per day × sober time of all attempts − relapse costs`, in total and for the current
  calendar month. Money and time saved are shown on the cards of the main screen and as a totals
  card on the Savings screen.
- **Relapse costs.** Logging a relapse opens a bottom sheet with optional fields for money and time
  spent. They are stored in `Addiction.relapseCosts` and subtracted from the savings.
- **Info buttons (ⓘ)** in the top bar of every screen and in the relapse and saving dialogs
  (`utils/InfoUtils.kt`, texts `info_*` in `strings.xml`).
- **Optional diagnostics log** (`utils/DiagnosticsLog.kt`, `SobrietyApp.kt`). Off by default,
  anonymous, stored on the device only, exportable from the settings.
- Unit tests for the savings calculation and for storing the new fields.

### Changed
- Data format: two new keys `savings_meta` and `relapse_costs`. Files from older versions load
  unchanged; the original app ignores the new keys.
- The time picker for "time saved per day" opens at the current value.
- CI: publishes a release only for pushes to `master` or builds started by hand; shows compiler
  errors and failed tests as annotations on the run page.

## v10.1.0-neo.1 - bug fixes and code comments

### Fixed
- **Counter showed one day too much.** The released v10.0.0 counted days by calendar date but hours by
  elapsed time, so after a relapse at 22:00 it showed "1 day 3 hours" at 01:00. All time units now come
  from one calculation (`ElapsedTime`), which is also correct when the clocks change (daylight saving).
  Upstream had fixed the main part in February 2023 but never released it.
- **Progress bar for days** could go above 100% for some start dates; it now uses the real length of the
  running month.
- **Crash on every start** when the setting "Number of Relapses" was left empty (or set to a huge number).
  The setting is now validated and a bad stored value falls back to the default.
- **Data loss / crash loop after the app was killed while saving.** The data file is now written
  atomically. A damaged file no longer stops the app from starting: it is kept as `Sobriety.cache.corrupt`
  and a message is shown.
- **Import**: a broken or empty file no longer crashes the app; importing names that already exist adds
  "Name (2)" instead of creating duplicates. **Export** now truncates the target file (no leftover text
  when overwriting a longer file).
- **Summary screen** kept counting for stopped addictions and crashed in the first second after a relapse
  (and for an addiction that was stopped before it started).
- **Memory/battery leak:** every card and the summary screen started an endless one-second coroutine that
  was never stopped. There is now one timer per screen that only runs while the screen is visible.
- **Savings amount** accepts a decimal comma ("4,50"); it used to crash.
- **Milestones** use the same calendar months as the counter (they used 30.4375 days per month), cannot
  exceed 100%, and a huge number no longer crashes. Milestones of stopped addictions keep their
  progress at the stop time.
- **Waiting ("future") addictions** are started by the regular timer and when the app opens, and the new
  status is saved. Before, a one-off `Handler` was used that was lost when the screen was recreated.
- **Return after the app process was killed:** detail screens (summary, timeline, notes, savings,
  milestones) crashed because the static list was empty; they now reload the data.
- Button taps during list changes (`adapterPosition == -1`) are ignored instead of crashing.
- Milestone list: "5 days" and "5 weeks" are no longer treated as the same item.

### Added
- Unit tests for the time calculation and the data model (`shared/src/commonTest`).
- "Report an issue" in the settings opens the issue page of this fork.
- Explanatory comments (KDoc) for all Kotlin files, the Gradle files and the manifest.
- `ARCHITECTURE.md`.
- CI builds a debug APK on every push and publishes it on the Releases page.

### Changed
- App name "Sobriety Neo", application id `io.github.mircodhd.sobrietyneo` (installs next to the original).
- Debug builds are signed with a fixed, public key (`app/fork-debug.keystore`) so that updates install over
  each other. This key must not be used for store releases.
- Version `v10.1.0-neo.1` (versionCode 23).

### Known / not changed on purpose
- `allowBackup` is still `true` (Android cloud backup); it is a privacy decision, see the comment in
  `AndroidManifest.xml`.
- The look of the app is unchanged. A new design follows in a later step.
