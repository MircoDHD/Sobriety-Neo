# Sobriety Neo

**A recovery companion for Android: private, offline and free.**

Sobriety Neo tracks how long you have stayed away from anything you want to quit. It shows the time
since your last relapse, a countdown to your next milestone, what you have saved in money and time,
statistics with charts, daily notes and a timeline, organized in tabs you set up yourself.
All data stays on your device. There are no ads, no trackers, no accounts and no subscriptions.

Sobriety Neo is a community fork of **[Sobriety](https://github.com/seaglade/Sobriety)** by
[seaglade](https://github.com/seaglade) and its contributors. The original app is a wonderful piece of work and the foundation of everything
here. Its last release (v10.0.0) was published in January 2023 and the repository has not been updated
since the end of 2024. This fork continues where it left off: it fixes bugs, documents the code and adds
new features step by step.

[![Android CI](https://github.com/MircoDHD/Sobriety-Neo/actions/workflows/ci.yml/badge.svg)](https://github.com/MircoDHD/Sobriety-Neo/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Contents

- [Download and install](#download-and-install)
- [Moving your data from the original app](#moving-your-data-from-the-original-app)
- [What's new](#whats-new)
- [What has been fixed](#what-has-been-fixed)
- [Diagnostics (optional)](#diagnostics-optional)
- [Roadmap](#roadmap)
- [How this project is made](#how-this-project-is-made)
- [Disclaimer](#disclaimer)
- [Thank you](#thank-you)
- [License](#license)

---

## Download and install

1. Open the [Releases page](https://github.com/MircoDHD/Sobriety-Neo/releases) on your phone.
2. Tap the newest entry and download the `.apk` file of the variant you want:
   - **`…-neo-release.apk` – Sobriety Neo**: the new look with tabs, progress rings and many options.
   - **`…-classic-release.apk` – Sobriety Neo Classic**: the look of the original app, with all the
     new features that fit into it.
3. Open the file. Android may ask you to allow installing apps from your browser or file manager.

Both variants can be installed side by side, but each keeps its own data. To move your entries from
one to the other, use **Settings → Data → Export / Import**.

**Coming from a preview version (v10.2.0 or v11.0.0 preview, `…-debug.apk`)?** From v11.0.0 on the
app is signed with its own private key, so Android can not install it over a preview. Export your
data first (**Settings → Data → Export**), uninstall the preview, install the new version and import
the file. Later updates install normally again.

Sobriety Neo has its own app ID (`io.github.mircodhd.sobrietyneo`). It installs **next to** the original
Sobriety app and does not replace or touch it.

## Moving your data from the original app

1. In the original Sobriety app: **Settings → Data → Export**, and save the file.
2. In Sobriety Neo: **Settings → Data → Import**, and choose that file.

Your entries are added to Sobriety Neo. If an entry with the same name already exists, the imported
one is renamed, for example "Smoking (2)", so nothing is overwritten.

---

## What's new

**v11.0.0: the new look**
- **New main screen.** Cards with a progress ring that fills up towards your next milestone, the
  number of days inside, and the money and time you saved.
- **Tabs.** Swipe between tabs you set up yourself. A tab can be filled automatically (for example
  "high priority" or "running") or by hand, or both. An "All" tab shows everything and can be
  hidden. Cards can be sorted by priority, streak or name.
- **Display options per entry.** Show the counter, a countdown to the next milestone, or both.
  Choose how exact the time is shown (for example only years, or hours and minutes without
  seconds); the time is still tracked exactly. In the new look you can also hide card buttons you do
  not need (the relapse button always stays).
- **Statistics.** Choose "All" or one entry; tiles with the key numbers in short form ("3 mo 2 w")
  and one chart at a time: relapses per month or the length of your attempts.
- **Savings in your current streak.** Cards and the Savings screen show what you saved in total and
  since your last relapse, for example the time you got back with "8 hours per week".
- **Default milestones.** New entries get 1 day, 1 week, 1, 3 and 6 months, and 1, 2 and 5 years.
  The set can be changed or turned off in the settings, and removed from any entry.
- **Savings:** time is now a category like money, and the name is optional for money and time.
- **True black (AMOLED)** dark theme.
- **Detailed explanation of every setting** behind the ⓘ button on the settings screen.
- **Two variants:** Sobriety Neo (new look) and Sobriety Neo Classic (original look).

**v10.2.0 (preview)**
- **Savings that add up.** Every saving now has a category (money or other) and a period (per day,
  week or month). The app calculates what you have saved, in total and in the current month.
  Money and time saved are shown directly on the main screen next to the counter.
- **Honest totals after a relapse.** When you log a relapse you can optionally enter how much money
  and time you spent. Both are subtracted from your savings.
- **Explanations everywhere.** Every screen has an ⓘ button that explains what it does.
- **Optional diagnostics.** An anonymous log that helps to find problems, off by default
  (see [below](#diagnostics-optional)).
- **No background activity.** The counters are calculated from the saved times. The app only
  updates the display while it is open and does nothing in the background.

---

## What has been fixed

Version `v10.1.0-neo.1` focuses on reliability. The full list with technical details is in
[CHANGELOG-FORK.md](CHANGELOG-FORK.md).

**Counter and time calculation**
- **The counter showed one day too many.** After a relapse in the evening, the counter jumped to
  "1 day" at midnight even though only a few hours had passed. Days, hours and minutes now come from
  a single calculation, which also stays correct when the clocks change for daylight saving time.
- The day progress bar could go above 100% for some start dates.
- Milestones now use the same calendar months as the counter, can no longer go above 100%, and keep
  their progress when tracking is stopped.
- Entries scheduled to start in the future now start reliably, even if the app was closed.

**Crashes and data safety**
- The app no longer crashes on every start when the setting "Number of Relapses" is left empty.
- Data is now saved atomically, so it can no longer be lost or corrupted if the app is closed while
  saving. A damaged data file no longer prevents the app from starting; it is kept as a copy and you
  are notified.
- A broken or empty import file no longer crashes the app. Exporting over an existing file no longer
  leaves old content behind.
- Detail screens (summary, timeline, notes, savings, milestones) no longer crash after Android has
  closed the app in the background.
- Entering an amount with a decimal comma ("4,50") or a very large milestone number no longer crashes
  the app.
- The summary screen no longer keeps counting for stopped entries and no longer crashes right after a
  relapse.

**Performance**
- Every card used to start its own timer that never stopped, which drained memory and battery. There
  is now a single timer per screen that only runs while the screen is visible.

**For developers**
- Detailed comments for all Kotlin files, the build files and the manifest.
- Two product flavors (`neo` and `classic`) that share the data model, the detail screens and all
  logic; see [ARCHITECTURE.md](ARCHITECTURE.md).
- Unit tests for the time calculation and the data model.
- An architecture overview: [ARCHITECTURE.md](ARCHITECTURE.md).
- Automatic checks: every push runs the tests and builds the app. Releases are published by hand.

---

## Roadmap

The work is done in stages. Each stage is tested on a real device before the next one starts.

### Stage 1: Bug fixes and documentation ✅
Everything listed under [What has been fixed](#what-has-been-fixed).

### Stage 2: Savings, explanations and diagnostics ✅ (preview)
Everything listed under [What's new](#whats-new).

### Stage 3: Display options, statistics and a new look ✅ (v11.0.0)
Everything listed under [v11.0.0](#whats-new). Both variants get the display options, the milestone
countdown, statistics and default milestones; the new look adds tabs, the new cards, button choices
and the true black theme.

### Stage 4: More features

**Planned first** (in this order)

| | Feature |
|:-:|---|
| ![1](https://img.shields.io/badge/1-2ea44f?style=flat-square) | Log a relapse at a chosen date and time, and edit or delete past entries |
| ![2](https://img.shields.io/badge/2-55b33c?style=flat-square) | Home screen widget |
| ![3](https://img.shields.io/badge/3-80c22a?style=flat-square) | Show the streak in different ways, for example as total days ("45 days") |
| ![4](https://img.shields.io/badge/4-aacf1c?style=flat-square) | Tutorials on first start, split into short topics, skippable and available again in the settings |
| ![5](https://img.shields.io/badge/5-d4d212?style=flat-square) | Notification when a milestone is reached |
| ![6](https://img.shields.io/badge/6-f1c40f?style=flat-square) | App lock with PIN or fingerprint |

**Planned next**
- Achievement badges
- A "help now" screen for difficult moments: your personal reasons, breathing exercise, distraction ideas
- Daily check-in with mood and a short note
- Craving log: when, how strong, what triggered it

**Ideas for later**
- **Healthy habits that work for you.** Log healthy habits (for example jogging, meditation, a cold
  shower) once, for all entries. For each entry you rate how well a habit helps against cravings,
  together with your mood. Over time the app shows whether a habit was done (or skipped) before a
  relapse and whether it helped, so you can recognize the patterns that lead up to a relapse.
- Automatic local backups
- Personal challenges, for example "30 days"
- More translations

The roadmap is a plan, not a promise. Priorities may change.

---

## Diagnostics (optional)

Sobriety Neo can keep an anonymous technical log on your phone. It is **off by default** and only
helps to find performance problems and crashes.

- Turn it on in **Settings → Diagnostics → Collect diagnostic logs**.
- Recorded: start-up and screen loading times, how long saving and loading take, the number of
  entries, whether the counter runs smoothly, and crashes (error type and code location only).
- Never recorded: names of your entries, notes, amounts, relapse dates, or anything that identifies
  you or your phone.
- The log stays on your phone (max. 256 KB). It only leaves your phone if you export it
  (**Settings → Diagnostics → Export**) and send it to someone yourself.

---

## How this project is made

This project is **vibe-coded**. Most of the code changes were written with the help of an AI coding
assistant. I describe what I want, review the result and test it on my own phone. Every change is
covered by the automatic build, and the core time calculation has its own unit tests.

Sobriety Neo is developed **mainly for my own use**. I am sharing it because it may be useful to others
as well. Bug reports and suggestions are welcome in the
[Issues](https://github.com/MircoDHD/Sobriety-Neo/issues), but I cannot promise support, fixes or a
fixed release schedule.

---

## Disclaimer

**Use this app at your own risk.**

- Sobriety Neo is provided **"as is", without warranty of any kind**, express or implied, including but
  not limited to the warranties of merchantability and fitness for a particular purpose. See sections 15
  and 16 of the [GNU General Public License v3](LICENSE).
- In no event shall the authors or contributors be liable for any claim, damages or other liability
  arising from the use of this app, including loss of data.
- **This app is not a medical device and does not provide medical, psychological or therapeutic
  advice.** It is a personal tracking tool and is no substitute for professional help.
- **If you are struggling with addiction or are in crisis, please reach out** to a doctor, a counseling
  service, a support group or your local emergency number.
- All data is stored only on your device. You are responsible for your own backups. Use
  **Settings → Data → Export** regularly.
- This is an unofficial fork. It is not affiliated with, endorsed by, or supported by the authors of the
  original Sobriety app. Please do not report problems with Sobriety Neo to the original project.

---

## Thank you

A big and heartfelt thank you to **[seaglade](https://github.com/seaglade)**, who created Sobriety, and to everyone who contributed
to the original project. Sobriety was built with a clear and kind goal: a recovery tool that is free,
respects your privacy and never profits from people's struggles. This fork exists because that work
was so good that it was worth continuing.

Thank you to everyone who contributed to the original project, including (by GitHub username):

- [seaglade](https://github.com/seaglade), creator and maintainer
- [unbiaseduser-github](https://github.com/unbiaseduser-github)
- [cubandle](https://github.com/cubandle)
- [mvnowak](https://github.com/mvnowak)
- [CastelJeremy](https://github.com/CastelJeremy)
- [Eric-Jim-Lab](https://github.com/Eric-Jim-Lab)
- [IzzySoft](https://github.com/IzzySoft), for distributing the app through the IzzyOnDroid repository
- [Poussinou](https://github.com/Poussinou)
- [Donnnno](https://github.com/Donnnno), for the Sobriety icon in the [Arcticons](https://github.com/Donnnno/Arcticons) icon pack
- and everyone else who contributed code, translations, bug reports and ideas

The original project's README, including the story of how Sobriety began, is preserved unchanged in
[docs/ORIGINAL-README.md](docs/ORIGINAL-README.md).

---

## License

Sobriety Neo is free software, licensed under the **GNU General Public License v3.0**, the same license
as the original Sobriety app. See [LICENSE](LICENSE).

You may use, study, share and modify it. If you distribute a modified version, it must also be licensed
under the GPL v3 and its source code must be available.

Copyright © the original Sobriety authors and contributors.
Modifications © 2026 MircoDHD.
