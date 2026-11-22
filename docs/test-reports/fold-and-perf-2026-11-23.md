# TPlay on Galaxy Z Fold (RFGL61K42HY), debug build, 2026-11-23

## End-to-end (real Real-Debrid account, public-domain Big Buck Bunny)
| Step | Result |
|---|---|
| `magnet:` link opened from adb VIEW intent | TPlay opens prefilled |
| Add -> Download -> Active | Sent, Downloading 22.8 MB/s, Ready |
| File on disk | `Movies/Big Buck Bunny.mp4`, 276,134,947 bytes (exact RD size) |
| Library | Title listed, 276.1 MB |
| Tap title (built-in player) | Plays, controls visible (screenshot reviewed) |
| Leave player | Library shows "Stopped at 0:39 of 10:34" (resume saved) |

## Fold / rotation during playback (position read from the player UI)
| Case | Position | Result |
|---|---|---|
| start | 1:27 | playing |
| HALF_OPENED (state 2) | 1:35 | kept playing |
| OPENED (state 3) | 1:43 | kept playing |
| landscape (user_rotation 1) | 1:51 | kept playing |
| portrait | 1:57 | kept playing |
| Activity recreations | 0 | configChanges work |
| Crashes / ANR | none | logcat clean |
CLOSED (state 0) was cycled once: activity stayed top, no crash.
Not done yet: a tabletop-specific layout (controls on the lower half), tested on the emulator `zfold7`.

## Performance (debug build, see perf-fold-run1.txt)
| Metric | Value |
|---|---|
| Cold start (5 runs) | 503-521 ms |
| PSS before / after 20 player open-close cycles | 193 MB / 213 MB (+20 MB, activity count stable) |
| Janky frames | 24.5% in debug while video played and menus were driven (needs release build + baseline profile) |
| Thermal status | 0 (none), AP 49.6 C after the loop |

## Bugs found by running it (fixed)
1. Vault key generation threw (`KeyGenParameterSpec` missing): the vault never worked on device.
2. Vault decrypt used the wrong length: "Bad arguments".
3. Original UI worker screens were hardcoded placeholders.

## Release build (R8 + shrink resources), same device
| Metric | Debug | Release |
|---|---|---|
| APK size | 25 MB | 3.4 MB |
| Cold start | 503-521 ms | 111-121 ms |
| Janky frames while driving menus and playing video | 24.5% | 6.4% (p90 7 ms, p95 9 ms) |
| Memory baseline (PSS) | 193 MB | 90-102 MB |
| Leak check: PSS after 10 / 30 open-close cycles | n/a | 119.5 MB / 120.4 MB (plateau, not growing); Activities: 1, ViewRootImpl: 1 |
Verdict: no activity or view leak; the +30 MB after the first player use is decoder native memory that stays flat.

## Tabletop and icon (real Fold, 2026-11-23 00:15)
- HALF_OPENED with a horizontal hinge: video fills the upper half, title and controls sit on the lower half (screenshot reviewed), playback resumed at the saved 3:08 and kept running. Logic covered by 4 unit tests (PostureTest).
- New adaptive icon (blue to violet gradient, rounded play triangle with a download arrow, plus a monochrome layer for themed icons) shows correctly in the launcher dock.
- Not run: the `zfold7` emulator and Compose instrumented tests (the real device was used instead).

## Streaming mode (2026-11-23, real Fold, real Real-Debrid)
- New default button "Stream": the app sends the magnet to Real-Debrid, waits until it is cached, saves only a subtitle file, and plays from the Real-Debrid HTTPS link. Storage used: 7 KB (vs 276 MB when downloading). "Download to phone" stays as the second button and is still used for RAR archives.
- Verified: Library shows "stream only", tapping it plays in VLC and in the built-in player (position moves, seek +10 s works).
- Bug found: Real-Debrid calls ran on the main thread when playing a library title (NetworkOnMainThread crash, app returned to launcher). Fixed by running the HTTP call on Dispatchers.IO.
- Subtitles: opensubtitles.com (key + login stored in the vault) with automatic fallback to opensubtitles.org (no key). The .com download endpoint answered 503 once, the retry on .org path is covered by tests.

## TMDB posters and calendar (2026-11-23, real Fold)
- Library shows the TMDB poster (screenshot reviewed). Posters are fetched when a title is added and backfilled for older titles; no key means the gradient tile stays.
- Calendar lists, per day, titles watched and titles added, each with its poster (screenshot reviewed). Days now use the phone's time zone (an entry added at 00:49 Dubai time had been filed under the 22nd).
- Adding the same movie again (same name and year) replaces the old entry and keeps its watch progress. Real-Debrid creates a new torrent id on every add, so the earlier tests had left duplicate rows and 6 duplicate torrents on the account; those test copies were deleted.
- 85 unit tests pass.

## Real-Debrid account in the Library (2026-11-23, real Fold, real account)
- New "Real-Debrid" tab in Library lists the 53 finished torrents of the account (name, year, size, "in library" tag), newest first, with Refresh and the Library search box. Tap a row to add it (stream only) and play it; the Add button only adds it.
- Verified: row tap opened VLC and played from Real-Debrid; Add put the title into Library > All as "stream only".
- Known limit: packs with several files (for example "Collection") play only the first file for now.
- Download to phone button hidden on the Add screen; RAR releases still download and unpack automatically.
- Magnet hook (Browse): the site cancels magnet clicks in its own script, so a capture-phase listener hands the link to the app. The hand-off was verified through the bridge; a real finger tap on the page's Magnet button was not completed in this session.
