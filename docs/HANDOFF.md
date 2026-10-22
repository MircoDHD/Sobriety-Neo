# Handoff: from the claude.ai cloud session to local Claude Code

Written on 2026-10-22. Up to now, all work was done by Claude in a cloud session on claude.ai that
had push access to this repository. From now on the owner develops **locally** with Claude Code
and releases versions on purpose. This file contains everything the next session needs.

---

## 1. Repositories and state

| What | Where / value |
|---|---|
| Fork (this repo) | https://github.com/MircoDHD/Sobriety-Neo (owner: `MircoDHD`) |
| Upstream | https://github.com/seaglade/Sobriety (unmaintained; issues were never read, see 7) |
| Default branch | `master`, at commit `bcc68f7` (v11.0.0 preview) |
| This handoff | branch `handoff/claude-code` (CLAUDE.md, this file, CI release change) |
| Old work branches | `stage1/bugfixes-and-comments`, `stage2/savings-info-diagnostics`, `stage3/new-ui`; all merged into `master`, can be deleted |
| Current version | `versionName = v11.0.0-neo.1-preview`, `versionCode = 25` |
| Latest release | `build-10` (pre-release, both APKs) |
| Older releases | `build-1`, `build-2` (stage 1), `build-6` (stage 2) |

Everything that was ever built is on GitHub. Nothing exists only in the cloud session.

### Getting started locally

```bash
git clone https://github.com/MircoDHD/Sobriety-Neo.git
cd Sobriety-Neo
git checkout handoff/claude-code   # review, then merge into master (see below)
./gradlew :shared:testDebugUnitTest
./gradlew :app:assembleDebug
```

Merging `handoff/claude-code` into `master` and pushing it does **not** create a release any more
(see the CI change in section 5).

---

## 2. History (what was done, in stages)

The owner wanted staged work: one step, then the owner tests the APK on the phone, then the next step.

### Stage 1: bug fixes and comments (tested by the owner ✅)
- **Main bug:** the counter showed exactly one day too many a few hours after a relapse. Cause:
  the released v10.0.0 used `Period.between(LocalDate…)` (counts midnights) for days but
  `Duration` for hours. Fixed with one central, calendar-correct and DST-correct calculation
  `ElapsedTime` (kotlinx-datetime `periodUntil` in the device time zone) + unit tests.
- Crash on every start with an empty "Number of Relapses" setting; non-atomic saving (now
  `AtomicFile`, a corrupt file is kept as `Sobriety.cache.corrupt`); import crashes and duplicate
  names ("Name (2)"); export did not truncate; summary kept counting for stopped trackers and
  crashed right after a relapse; day bar > 100 %; milestones used 30.4375-day months; decimal
  comma crash; huge milestone numbers; endless per-card coroutines (memory/battery leak, now one
  Handler timer per visible screen); "future" start via lost Handler; crashes after process death
  (detail screens reload the data); clicks with position −1; milestone DiffUtil.
- KDoc/comments everywhere, `ARCHITECTURE.md`, `CHANGELOG-FORK.md`, new README (vibe-coded note,
  disclaimer, credits by GitHub username), CI with tests and APK release.
- App renamed to Sobriety Neo, own app id, fixed public debug signing key.

### Stage 2: savings, explanations, diagnostics (tested ✅, build-6)
- Savings with categories and periods, totals (all time + current month) on the main screen,
  optional money/time spent when logging a relapse (subtracted from savings).
- ⓘ explanation on every screen and in dialogs.
- Opt-in anonymous diagnostics log (`DiagnosticsLog`), exportable. The owner sent one log: start
  2–15 ms, load ~10 ms, save 5–9 ms, screens 7–19 ms, no slow ticks, no crashes.

### Stage 3: new look + classic variant (build-10, being tested)
- Two flavors: `neo` (new UI, main app) and `classic` (original UI as extra app).
- Both: display options per tracker (counter / countdown to next milestone / both; max units;
  smallest unit), statistics screen with bar charts, default milestones (1 d, 1 w, 1/3/6 mo,
  1/2/5 y; opt-out, editable, "add to existing entries"), time as a savings category (old
  "time saved per day" is migrated), optional saving names, formatted per-setting explanation.
- Neo only: swipeable tabs (ViewPager2) with "All" tab + user tabs filled by rule and/or by hand,
  "Manage tabs" screen, card sort setting, new cards with progress ring, hideable card buttons
  (relapse always visible), true black (AMOLED) theme.
- Feedback so far: **statistics are too cluttered** → next task.

---

## 3. Next task: make the statistics screen clearer

File: `app/src/main/java/com/katiearose/sobriety/activities/Statistics.kt` (+ layouts
`activity_statistics.xml`, `item_statistics_section.xml`, view `views/BarChartView.kt`; the numbers
come from `shared/.../Statistics.kt`, which is tested and can stay).

**Why it is cluttered now:** one overview card plus one card per tracker, all below each other,
each with 5–6 lines of text and a chart. Durations use `convertSecondsToString`, which prints
very long texts ("1 month, 2 weeks, 3 days, 4 hours, 5 minutes and 6 seconds") for every value.

**Suggested direction** (confirm with the owner before building, then build in small steps):
1. One tracker at a time: a selector at the top (chips or tabs: "All" + each tracker) instead of
   all cards stacked.
2. Key numbers as a 2×2 grid of tiles with a big value and a small label: current streak, longest
   streak, relapses in the last 30 days, clean in total. Short durations ("45 d", "3 mo 2 w")
   instead of the long text; consider a short formatter based on `ElapsedTime`/`DisplaySettings`.
3. One chart at a time with a toggle (e.g. "Relapses per month" / "Attempt lengths"); clear axis
   meaning in the title, fewer labels.
4. Hide what has no data (e.g. no chart for a tracker with one attempt; "no relapses yet" message).
5. Keep the explanation behind the ⓘ (`info_statistics`), update it.
6. Works in light, dark and AMOLED; both variants use this screen.

---

## 4. Roadmap after that (owner's order)

**Planned first** (README shows them as a green→yellow priority list):
1. Log a relapse at a chosen date and time; edit or delete past entries
2. Home screen widget
3. Show the streak in different ways (e.g. total days "45 days")
4. Tutorial on first start: modular (several short tutorials for related features, with icons),
   skippable, can be started again in the settings
5. Notification when a milestone is reached
6. App lock (PIN / fingerprint)

**Planned next:** achievement badges, "help now" screen for difficult moments, daily check-in with
mood, craving log.

**Ideas for later** (in README only, do not build yet): healthy habits logged globally; per
addiction tracker the user rates how well a habit helps against cravings (with mood); analysis
whether a habit was done or skipped before relapses → recognize patterns before a relapse.
Also: automatic local backups, personal challenges, translations (German).

---

## 5. Changes made in this handoff branch

- This file. (`CLAUDE.md`, the project instructions for Claude Code, is kept locally only and is
  not part of the repository.)
- **CI release policy changed** (`.github/workflows/ci.yml`): pushes to branches, including
  `master`, no longer create releases. A release is created only by
  - pushing a tag `v*` (tag with "-" → pre-release, without → normal release), or
  - "Run workflow" in the Actions tab (test build `build-N`).
- `.gitignore`: ignores local Claude Code settings (`.claude/settings.local.json`).

---

## 6. Open points and known gaps

- `allowBackup="true"` in the manifest: Android cloud backup of sensitive data. Owner has not
  decided yet; ask before changing.
- New strings exist only in English; `values-tr` (Turkish) misses them (falls back to English).
- Store-ready release signing: debug builds use a public key. A real release needs a private key
  stored as a GitHub secret and a `release` signing config (R8 is enabled for release builds;
  check `proguard-rules.pro` for kotlinx-serialization before the first release build).
- GitHub repo "About" text still says "simple and minimal" (from upstream); the owner wants it
  changed (GitHub → repo page → gear icon next to "About"). It can not be changed from code.
- The neo info text mentions a "⚙" button; the icon on the card is a "tune" (sliders) icon.
- `app/src/classic/.../AddictionCardAdapter.kt` has an unused `getFormattedDate()`.
- Toolchain is old (AGP 7.3 / Gradle 7.5 / Kotlin 1.8 / SDK 33). Upgrading (AGP 8, Kotlin 2,
  SDK 35, Google Play target requirements) should be its own step with full testing.
- Upstream issues and feature requests (https://github.com/seaglade/Sobriety/issues) were never
  read (the cloud session could not reach them). Worth a look for bug reports.

---

## 7. Notes on how the cloud session worked (for reference)

- No Android SDK was available in the cloud; every build ran in GitHub Actions and errors were read
  from run annotations. Locally you can build and test directly, which is much faster.
- Unit tests live in `shared/src/commonTest` (JVM). There are no UI tests.
- Earlier planning documents of the owner are superseded by this file for development.
