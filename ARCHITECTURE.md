# Architecture

A short map of the code for people who want to read, fix or extend it.
All user-visible texts are in English in `app/src/main/res/values/strings.xml`.

## Modules

| Module | Contains | Depends on Android? |
|---|---|---|
| `shared/` (Kotlin Multiplatform) | data model, time calculation, JSON conversion, unit tests | only `androidMain/CacheHandler.kt` |
| `app/` | all screens (activities), list adapters, layouts, settings | yes |

The iOS port in the upstream project reuses `shared/`. Keep everything that does not need Android there.

## Data

Everything is one `List<Addiction>` that is stored as a single JSON file `Sobriety.cache` in the private
app storage.

- `Addiction` (`shared/.../Addiction.kt`): name, status (Ongoing / Stopped / Future), `history`,
  priority, notes, savings, milestones.
- `history` is a `LinkedHashMap<Long, Long>`: attempt start (epoch ms) -> attempt end (epoch ms, `0` while the
  attempt is running). The **last entry is the current attempt**; its key is the "last relapse".
- `AddictionSerializer`: hand written JSON format. It also reads the keys of older app versions
  (`last_relapse`, `is_stopped`). **Do not change existing keys**, existing user files depend on them.
- `CacheHandler` (`shared/androidMain`): load / save (atomic) and export / import through the file picker.

## Time

- `ElapsedTime.between(start, end)` is the **only** place that turns two timestamps into
  years / months / weeks / days / hours / minutes / seconds. Use it for everything the user sees as a
  running counter. It uses calendar arithmetic in the device time zone.
- `convertRangeToString` / `convertRangeToPercentList` (`app/.../utils/Utils.kt`) format its result as text
  and progress bars.
- `convertSecondsToString` is only for *durations without a date* (averages) and uses approximate month
  lengths.
- `Addiction.milestoneGoalMillis` calculates milestone dates with the same calendar arithmetic.

## Screens (`app/.../activities`)

| Activity | Purpose |
|---|---|
| `Main` | list of addictions; owns the static list `Main.addictions` and the one-second timer |
| `Create` | new addiction (name, start, priority) |
| `Summary` | progress bars per unit |
| `Timeline` | list of all attempts |
| `DailyNotes`, `Savings`, `Milestones` | the "more" menu of a card |
| `Settings` | preferences, export / import |

Detail screens receive only the **position** of the addiction in `Main.addictions` (`EXTRA_ADDICTION_POSITION`)
and get the object with `loadAddictionFromIntent()`, which also reloads the data from disk if Android has
killed the process.

## The one-second timer

Counters must update every second but only while visible. `Main` and `Summary` each own a `Handler` that
runs in `onResume` and stops in `onPause`. `Main` also starts waiting (`Future`) addictions in that timer
(`Addiction.activateIfDue`). The card adapter redraws only the counter text (payload `PAYLOAD_TICK`).

## Tests and builds

- Unit tests: `./gradlew :shared:testDebugUnitTest`
- Debug APK: `./gradlew :app:assembleDebug` (needs the Android SDK)
- GitHub Actions (`.github/workflows/ci.yml`) does both on every push and publishes the APK as a pre-release.

## Where to start for common changes

- A new field on an addiction: add it to `Addiction`, append an element in `AddictionSerializer` (do not renumber
  existing ones), give it a default in `deserialize` so old files still load.
- A new setting: `res/xml/root_preferences.xml` + a getter in `utils/PreferenceUtils.kt`.
- A new screen: new activity + layout, register it in `AndroidManifest.xml`, open it with
  `EXTRA_ADDICTION_POSITION`, and use `loadAddictionFromIntent()` in `onCreate`.
