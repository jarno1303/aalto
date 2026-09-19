# Aalto – Current State

Last verified: 2026-09-02

## Current status

### PHYSICALLY VERIFIED PASS
- Two-device Firebase live sync works automatically and very fast.
- Favorite add/delete sync works.
- Favorite long-press drag-to-reorder works.
- Drag motion is smooth and follows the finger continuously.
- Reorder persistence works across app restart.

### KNOWN-GOOD / PROTECTED
- Current sync architecture is known-good.
- Playback critical path was not modified by the latest reorder work.
- Local-first behavior must remain intact.
- SYNC MAY FAIL. RADIO MAY NOT.

## Product direction
- Less friction. More fluidity.
- Speed / Car / Sync / Calm.
- One tap = radio plays.
- Feature count has no value by itself.

## Current implementation note
Latest completed work:
- smooth long-press favorite drag-to-reorder
- pixel-level drag movement
- animated surrounding item placement
- persistence only on drop
- existing reorder sync path reused

## Protected subsystems
Do not modify without a reproduced defect relevant to the current task:
- SyncEngine
- SyncModel
- AaltoSyncCoordinator
- FirestoreRemoteSyncTransport
- RadioPlayer / PlaybackService critical path

## Next development
Continue product-quality development according to AGENTS.md.

Before risky sync/architecture work:
- preserve this Git known-good baseline
- create a dedicated commit/branch
- reproduce the defect before changing known-good code

## Verification discipline
CODE PASS and PHYSICAL PASS are different states.
Never claim physical acceptance unless it has actually been tested on a device.

## UI/UX polish (branch `ui-ux-polish`, 2026-09-17)

State: CODE WRITTEN. NOT YET BUILT, UNIT-TESTED OR PHYSICALLY VERIFIED.
The session that wrote it had no Android SDK, so compile and device checks are still open.

Changes:
- connecting state ("Yhdistetään…") and retry after playback error
- RadioPlayer: prepare again when retrying the same stream; toggle() compares preferredStreamUrl
- edge-to-edge with bar icons following the app theme; theme choice (System/Light/Dark)
- mini player on Search and Favorites; playing from a list no longer switches tab
- home shows "Omat asemat" (favorites); popular stations only when there are no favorites
- previous/next own station: buttons, swipe on logo, Night Screen controls
- Night Screen: tap reveals controls, low brightness, keep screen on
- static playing halo (no infinite pulse), typography scale, non-colour playing indicator
- search: clear button, IME search, recent stations, loading skeleton
- MainActivity.kt split into screen/component/theme files
- adaptive launcher icon; all UI strings in strings.xml

Not changed (protected): SyncEngine, SyncModel, AaltoSyncCoordinator, FirestoreRemoteSyncTransport, PlaybackService.
Open: MediaSession next/previous for steering wheel / Android Auto needs PlaybackService work and physical testing.

Physical checks needed: tap-to-play feedback, retry, pause on catalog stations, mini player,
favorite drag-and-drop (unchanged logic, new row styling), Night Screen controls and brightness,
landscape layout, light/dark/system theme switching and system bar icons.

## UI polish round 2 (ui-ux-polish)
- Portrait home: compact Now Playing card + grid of own stations (rounded-square logo tiles on white).
- Built-in stations fetch a logo from Radio Browser by name (cached in SharedPreferences `aalto_logo_lookup`).
- Sleep timer (SleepTimer.kt): 15-90 min, pauses PlaybackService through its own MediaController.
- Song title from stream metadata (RadioPlayer.nowPlayingTrack, read-only onMediaMetadataChanged listener).
- usesCleartextTraffic=true for http streams/logos.
Physical checks: sleep timer with screen off, song titles on ICY/HLS stations, logo lookup for Yle stations.

## Wake-up radio (MVP, ui-ux-polish)
Code: `alarm/` (AlarmSettings, AlarmScheduler, AlarmReceiver + AlarmBootReceiver, AlarmService, AlarmActivity), `AlarmDialog.kt`, alarm button in TopBar.
- setAlarmClock (exact, shown in status bar); USE_EXACT_ALARM (API 33+) / SCHEDULE_EXACT_ALARM (31-32).
- AlarmService is separate from PlaybackService: own ExoPlayer on USAGE_ALARM, 45 s volume ramp,
  built-in alarm tone if the stream is not playing within 15 s or fails, auto-stop after 30 min.
- Lock-screen view: Torkku 9 min / Jatka kuuntelua (hands over to PlaybackService via MediaController) / Lopeta.
- Re-armed after reboot, app update, time and time zone changes.
Test plan (physical): alarm in 2 min with screen locked; flight mode -> fallback tone; snooze; continue listening;
reboot with alarm set; weekday repeat; media volume 0 (alarm volume should apply); ColorOS battery settings overnight.
- Alarm UX (clock-app pattern): AlarmSheet bottom sheet, big time + switch, every change saved at once,
  toast "Herätys soi X t Y min kuluttua", "Tuleva herätys" notification 1 h before with "Ohita tämä kerta"
  (skip stored in `skip_at`), "Kokeile ääntä" as a text button, event log only in debug builds.

## Android Auto, stream fallback, widget, logos (ui-ux-polish)
Touches the protected playback path (PlaybackService) - needs physical verification before merge.
- PlaybackService is now a MediaLibraryService: browse tree Omat asemat / Viimeksi kuunnellut / Suositut,
  search (incl. voice query), items resolved by station id (`playback/StationLookup`).
- `playback/AaltoSessionPlayer` (ForwardingPlayer around the same ExoPlayer):
  - stream fallback: next address on error or 12 s buffering before first audio; .pls/.m3u resolved;
    errors hidden from controllers while a fallback is tried; working address remembered (`aalto_stream_memory`).
  - previous/next = own stations (steering wheel, car, headset, notification, widget).
- ExoPlayer now handles audio focus (USAGE_MEDIA) and uses WAKE_MODE_NETWORK.
- RadioPlayer builds items via `StationMediaItems` (all addresses in metadata extras), compares by station id,
  exposes `currentStationId`; MainActivity follows station changes made outside the app.
- Logos: `content://fi.aalto.radio.logos/<id>` (AaltoLogoProvider); bundled override `res/drawable/logo_<id>.png`;
  shared broadcaster logos from the name lookup are dropped so channels keep their own tile.
- Home screen widget (`widget/AaltoWidgetProvider`, RemoteViews): logo, station, song, play/pause, next.
Testing Android Auto: install Desktop Head Unit (SDK Manager > SDK Tools > Android Auto Desktop Head Unit Emulator),
enable Android Auto developer mode (tap version 10x), "Unknown sources" on, "Start head unit server", then run
`desktop-head-unit.exe` from `%LOCALAPPDATA%\Android\Sdk\extras\google\auto` with the phone connected via USB and
`adb forward tcp:5277 tcp:5277`.
- Suositut: curated per-country top list (`playback/CuratedStations`, FI from national listening research) ranks
  the catalog; everything else keeps catalog order. Unit tests in CuratedStationsTest.
- Alarm can be handled without opening the app: upcoming notification has "Ohita tämä kerta" and "Kytke pois",
  volume keys snooze a ringing alarm, and the alarm sheet warns if notifications are disabled.

## Checkpoint known-good-2026-09-17 (main)
ui-ux-polish merged into main (fast-forward) and tagged. Physically verified per docs/TESTILISTA.md:
normal playback, station switching, notification/widget controls, sleep timer, alarm (lock screen, snooze,
fallback tone, volume), Android Auto (browse, search, queue, previous/next). Unit tests green.
Treat this behavior as protected (AGENTS.md §27). Not pushed to origin yet.
Next: Crashlytics, Play listing requirements, closed beta (10-20 users). Later: Chromecast, more curated countries.

## Played songs / stream metadata (branch `ui-calm`, 2026-09-18)

"Soitetut kappaleet" reads the stream's own ICY / ID3 announcements
(`StreamTrack`), stores them through `TrackHistory` in Room (schema 6,
`played_tracks`, newest 300 rows) and offers a Spotify / YouTube search link
per row. Recording happens in `PlaybackService`, so it keeps working while the
app is in the background.

Why the song does not come from the player's combined `MediaMetadata`: the
MediaItem's own title (the station name) takes precedence there, so the stream's
announcement is never visible in it. The service therefore listens to
`onMetadata` and hands the line to controllers in the session extras
(`EXTRA_NOW_PLAYING_TRACK`).

What stations actually deliver (measured 2026-09-18, logcat tag `AALTO_TRACK`):

- **Kiss, Nova and other Bauer streams**: ICY works, but the title arrives only
  at a song boundary. Connecting mid-song gives `title=""` plus a `StreamUrl`
  ending in `eventdata/-1`, which is their "no current song" placeholder — that
  API answers `invalid paramaters`, so it is no help. Waiting for the next song
  is the only option, and it is correct behaviour, not a bug.
- **Radio Rock (Nelonen, HLS)**: no metadata at all. Its TS segments produce
  `PesReader: Unexpected start code prefix` every six seconds, i.e. an
  unreadable stream where the timed metadata would be. No player can show the
  song for it. Do not re-investigate.

## Station gain in Aalto Sync (branch `sync-station-gain`, 2026-09-19)

State: CODE PASS (app:testDebugUnitTest and app:assembleDebug green on 2026-09-19).
Firestore rules deployed 2026-09-19. PENDING PHYSICAL ACCEPTANCE.

Per-station gain (the -8..+8 dB slider) now travels between the user's devices. Free, as
docs/AALTO_PLUS.md says: no entitlement check anywhere. The equalizer stays per device.

- New sync entity `station_gain` / operation `SET_STATION_GAIN`, payload = dB as text.
- Room schema 7: table `station_gains` (MIGRATION_6_7). Rows are kept at 0 dB after a reset.
- Firestore: `users/{uid}/station_gains/{stationId}`, same stale/rebase protection as favorites.
  firestore.rules has a `station_gains` block (deployed to aaltodev 2026-09-19).
- Playback still reads the gain from SharedPreferences `aalto_audio` (playback path unchanged).
  `audio/StationGainSync` keeps SharedPreferences and Room in step; the slider hands its value
  to Sync on release (`onValueChangeFinished`), and a remote value for the playing station is
  applied immediately. Gains set before this version are queued once at startup.
- Protected Sync files were touched deliberately for this task: SyncModel (new enum values,
  payload, conflict policy), FirestoreRemoteSyncTransport (per-entity ref generalised to
  favorites + station gains), LocalRadioDao (new queries, one `when` branch). SyncEngine and
  AaltoSyncCoordinator unchanged.

Physical checks: both devices on the new APK and same account; set gain on A, hear it change on B
while the station plays; reset on B -> A back to 0; offline change then reconnect; existing
gains from before the update appear on the other device.
Known: a device still on an older APK skips gain mutations and will not backfill them later.

## Plus line before beta (branch `plus-boundary`, 2026-09-19)

State: CODE WRITTEN. NOT YET BUILT OR PHYSICALLY VERIFIED.

- `plus/PlusAccess.kt`: the one entitlement check. No billing yet, so release answers false;
  debug builds have an "Aalto Plus (debug)" switch in Settings.
- Played songs: free view = current day (`history/HistoryWindow`), Plus = all kept rows.
  Storage unchanged (300 rows), so nothing is lost. A quiet line says earlier days are kept.
- Equalizer: controls only with Plus; without it the audio sheet says it is a Plus feature.
- Checks live only in HistorySheet and AudioSheet (entry points). Playback, alarm, Auto,
  widget and Sync untouched.
Known debt: an equalizer switched on earlier stays applied without Plus (playback path is
not checked by design); the trial-end flow must switch it off once, with one explanation.
Physical checks: history shows only today with Plus off, everything with it on; EQ hidden/shown.

## Playback resumption (branch `playback-resumption`, 2026-09-19)

State: PHYSICAL PASS (2026-09-19, owner): Bluetooth/car play resumes noticeably better, song line
stays through reconnects. Touches PlaybackService (protected).

- `PlaybackService.LibraryCallback.onPlaybackResumption`: a "play" from a car, headset or the
  system media controls with nothing loaded (also with Aalto not running) starts the last
  station (`LastStationStore`), or the first own station on a fresh install.
- Manifest: `androidx.media3.session.MediaButtonReceiver` for `MEDIA_BUTTON`.
- Song line: `onMediaItemTransition` now clears the announced song only when the station id
  changes. Before, every reload of the same station (reconnect, fallback address, play after
  stop) cleared it, and most stations re-announce only at the next song.
- Nothing on the station-tap path changed.
Open: phone keeps playing (from the speaker, after a delay) when the car disconnects. Code has
`setHandleAudioBecomingNoisy(true)`; cause not yet verified. Suspect Android Auto disconnect not
sending AUDIO_BECOMING_NOISY. Needs logcat from a headset test and from the car before any fix.
Physical checks: kill Aalto, connect headset/car, press play -> last station starts; system media
controls show an Aalto resume card.

## Sync pull paging (branch `playback-resumption`, 2026-09-19)

State: BUILT (assembleDebug, 2026-09-19); confirm unit tests. Touches protected Sync
(SyncEngine, SyncModel, transports).

Defect: `receive` returns at most 75 changes and one `syncOnce` pulled once, with nothing
triggering the next pull, so a new device caught up one page per app start.
Fix: `syncOnce` pulls page after page while the cursor advances and
`RemoteSyncTransport.hasMoreAfter(count)` says the page was full (Firestore: count >= pageSize),
at most `MAX_PULL_PAGES` = 20 per sync. A caught-up device still makes exactly one query.
Tests: `newDeviceCatchesUpOnLongHistoryInOneSync`, `caughtUpDeviceMakesOnlyOnePullPerSync`.
Still open (before public release): the ledger (`sync_mutations` in Firestore, local
`sync_mutations` / `sync_applied_mutations`) is never pruned. Plan: new device bootstraps from the
state documents, then TTL on the ledger. Architectural, to be designed separately.

## Visual pass (branch `ui-visual-pass`, 2026-09-19)

State: CODE WRITTEN. NOT YET BUILT OR PHYSICALLY VERIFIED. No playback, Sync or alarm logic touched.
From a screenshot review against the Calm pillar:
- AaltoTheme: every Material 3 colour role defined (secondary/tertiary, surfaceContainer*,
  surfaceTint...). Sheets, dialogs, chips and switches no longer show Material's lavender default.
- Truncation: history title (two weighted children split the row), station tile names (one line
  when a word does not fit, instead of "SuomiR / ap"), mini player song (no soft wrap, ellipsis),
  history icon removed from the Now Playing song line (the line itself opens history).
- Station subtitle: one genre in Aalto's words (discovery category labels) instead of raw tags.
- Catalog duplicates of built-in stations dropped by name + country (`distinctByListing`).
- Favourites list: hearts grey instead of a column of blue. Sleep timer icon as quiet as the moon.
- Night Screen: station logo (dimmed) instead of initials, no "Toistaa", hint only while controls hidden.
- Settings: body text not grey, theme as radio rows, scrollable. Alarm: days as one row of seven,
  notification warning neutral. Audio: no tick dots on the gain slider, shorter hint.

Round 2 (same branch, not yet built): tile names always two lines, text shrinks in steps
(down to 75 %) until every word fits; no playing badge over tile logos; sheets paint their own
window's navigation bar (`MatchSheetNavigationBar`); Night Screen logo unframed under a black
veil; alarm volume slider without tick dots; widget logo frame has a night variant (the widget
follows the phone's theme, not Aalto's own theme setting).
