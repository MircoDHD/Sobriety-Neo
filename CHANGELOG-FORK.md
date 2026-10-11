# Changes in this fork

This file lists what differs from the upstream project
([seaglade/Sobriety](https://github.com/seaglade/Sobriety), last upstream commit `ad94ed6`).
Newest entries first.

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
