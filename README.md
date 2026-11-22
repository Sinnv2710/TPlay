# TPlay

Personal Android app (Kotlin, Jetpack Compose) that streams movies through a Real-Debrid account.

- **Stream** a magnet link: the file stays on Real-Debrid, only subtitles are saved.
- **Library** with TMDB posters, resume position, history and a month calendar.
- **Real-Debrid tab** lists what is already on your account.
- **Browse** tab: in-app web view that catches magnet links.
- Built-in Media3 player (fold, tabletop, picture-in-picture) or VLC / MX Player.
- Subtitles from OpenSubtitles (.com with fallback to .org), cleanup presets, encrypted token vault.

Tokens (Real-Debrid, OpenSubtitles, TMDB) are entered in Settings and stored in the Android Keystore. Nothing secret is in this repo.

Build: `./gradlew assembleDebug testDebugUnitTest` (JDK 21, Android SDK 36). Plan: `docs/PLAN.md`, reports: `docs/test-reports/`.

## Notes
- TPlay is a client for **your own Real-Debrid account**. It ships no catalogue, no search of torrent sites and no start page: you choose the web address for the Browse tab in Settings, and you are responsible for what you download or watch and for following the laws and terms that apply to you.
- Obtainium updates: see `docs/RELEASING.md`.
- No license file is included yet, so by default all rights are reserved; add one if you want others to reuse the code.
