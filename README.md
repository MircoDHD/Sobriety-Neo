# Sobriety Neo

**A sobriety and habit-free counter for Android: private, offline and free.**

Sobriety Neo tracks how long you have stayed away from anything you want to quit. It shows the time
since your last relapse down to the second, your average time between relapses, milestones, savings,
daily notes and a timeline. All data stays on your device. There are no ads, no trackers, no accounts
and no subscriptions.

Sobriety Neo is a community fork of **[Sobriety](https://github.com/seaglade/Sobriety)** by Katie Rose
and its contributors. The original app is a wonderful piece of work and the foundation of everything
here. Its last release (v10.0.0) was published in January 2023 and the repository has not been updated
since the end of 2024. This fork continues where it left off: it fixes bugs, documents the code and adds
new features step by step.

[![Android CI](https://github.com/MircoDHD/Sobriety-Neo/actions/workflows/ci.yml/badge.svg)](https://github.com/MircoDHD/Sobriety-Neo/actions/workflows/ci.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## Contents

- [Download and install](#download-and-install)
- [Moving your data from the original app](#moving-your-data-from-the-original-app)
- [What has been fixed](#what-has-been-fixed)
- [Roadmap](#roadmap)
- [How this project is made](#how-this-project-is-made)
- [Disclaimer](#disclaimer)
- [Thank you](#thank-you)
- [License](#license)

---

## Download and install

1. Open the [Releases page](https://github.com/MircoDHD/Sobriety-Neo/releases) on your phone.
2. Tap the newest entry and download the `.apk` file.
3. Open the file. Android may ask you to allow installing apps from your browser or file manager.

Every change to the code builds a new test version automatically. These builds are marked
**Pre-release**: they are meant for testing and may contain bugs.

Sobriety Neo has its own app ID (`io.github.mircodhd.sobrietyneo`). It installs **next to** the original
Sobriety app and does not replace or touch it.

## Moving your data from the original app

1. In the original Sobriety app: **Settings → Data → Export**, and save the file.
2. In Sobriety Neo: **Settings → Data → Import**, and choose that file.

Your entries are added to Sobriety Neo. If an entry with the same name already exists, the imported
one is renamed, for example "Smoking (2)", so nothing is overwritten.

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
- Unit tests for the time calculation and the data model.
- An architecture overview: [ARCHITECTURE.md](ARCHITECTURE.md).
- Automatic builds: every push runs the tests and publishes an installable APK.

---

## Roadmap

The work is done in stages. Each stage is tested on a real device before the next one starts.

### Stage 1: Bug fixes and documentation ✅
Everything listed above.

### Stage 2: A modern look
- A fresh Material 3 design that follows your system colors and dark mode
- A large progress ring on the home screen with the current streak at a glance
- Clearer navigation with a bottom navigation bar
- A tidier detail page with tabs for summary, milestones, savings, notes and timeline
- Smooth animations and better readability on small and large screens

### Stage 3: New features
**Planned first**
- Option to show the streak as total days ("45 days") or split up ("1 month 2 weeks 1 day")
- Log a relapse at a chosen date and time, and edit or delete past entries
- Savings totals: money and time saved since the start
- Notifications when a milestone is reached, plus achievement badges
- Home screen widget
- App lock with PIN or fingerprint

**Planned next**
- A "help now" screen for difficult moments: your personal reasons, breathing exercise, distraction ideas
- Daily check-in with mood and a short note
- Craving log: when, how strong, what triggered it
- Statistics and charts about your progress

**Ideas for later**
- Automatic local backups
- Personal challenges, for example "30 days"
- Tracking positive habits, not only things to quit
- More translations

The roadmap is a plan, not a promise. Priorities may change.

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

A big and heartfelt thank you to **Katie Rose**, who created Sobriety, and to everyone who contributed
to the original project. Sobriety was built with a clear and kind goal: a recovery tool that is free,
respects your privacy and never profits from people's struggles. This fork exists because that work
was so good that it was worth continuing.

Thank you to everyone who contributed code, ideas, translations and testing to the original project,
including:

- **Katie Rose** ([seaglade](https://github.com/seaglade), formerly KiARC), creator and maintainer
- **JakeGonder**
- **cubandle**
- **unbiaseduser**
- **neoplus72**
- **CastelJeremy**
- **Eric-Jim-Lab**
- **Poussinou**
- **IzzyOnDroid**, for distributing the app
- **Donnnno**, for the Sobriety icon in the [Arcticons](https://github.com/Donnnno/Arcticons) icon pack
- all translators and everyone who reported bugs and shared ideas

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
