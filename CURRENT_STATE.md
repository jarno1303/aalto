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

## Catalog favourites in Sync (branch `sync-catalog-favorites`, 2026-09-19)

State: CODE WRITTEN, NOT YET BUILT. Touches protected Sync (LocalRadioDao, SyncModel, coordinator).

Defect (found by reading, reproduced by tests): a favourite for a non-built-in station was sent
without station data (payload came only from `StationCatalog`). The receiving device lacking that
station hit the favorites -> stations foreign key, the exception aborted the whole remote batch,
the cursor never advanced and Sync stopped at that change for good.
Fix: payload from built-in, registered catalog or stored station row (repository and bootstrap);
receiver skips a favourite upsert/delete whose station it cannot know ("missing_station") instead
of throwing; bootstrap re-sends once any favourite whose upserts never carried station data.
Tests: catalogFavoriteReachesOtherDeviceWithItsStation, favoriteWithoutStationDataDoesNotStopSync,
catalogFavoriteSentWithoutStationIsSentAgainOnceWithIt.

## Own stations / custom stream URL (branch `custom-stream-url`, 2026-09-19)

State: PHYSICAL PASS (owner, 2026-09-19): add, edit, invalid address rejected. Aalto Plus feature (docs/AALTO_PLUS.md); no playback or Sync
engine change.
- Favourites end with "Lisää oma asema" (`CustomStationUi.kt`). The single Plus check is on that
  row; without Plus a short explanation. Added stations always keep playing and syncing.
- Before saving, `StreamProbe` reads headers and a few bytes: audio/HLS/pls/m3u or Icecast
  headers pass; the stream's icy-name is the default name. Ids start with `custom-`.
- Stored as a station row + favourite; the favourite carries the station, so other devices get it.
- `StationRepository.observeFavoriteIds` now loads favourites known only from the database into
  memory before emitting, so synced catalog and own stations show on the phone UI too (the car
  already found them through StationLookup).
Tests: CustomStationsTest, SyncEngineTest.customStationAppearsOnOtherDevice.
Editing: long press an own station on the home grid -> "Muokkaa asemaa" (never behind Plus).
Same id and place; the new address is probed, StreamMemory is cleared for the station, and an
UPSERT_FAVORITE carries the new station to other devices (`updateFavoriteStationAndEnqueueMutation`).
Own stations are re-read from the database on every favourites emission. Another device may still
try its remembered old address first; stream fallback then moves to the new one.

## Flags, all own countries, several genres (branch `country-flags`, 2026-09-19)

State: PHYSICAL PASS (owner, 2026-09-19). UI and catalog loading only.
- Flags (emoji from the country code, `CountryFlags.kt`) on search's country line, its menu and the
  Maat list; in search / favourite rows only for stations outside the phone's own country.
- Search country menu: "Kaikki omat maat" (when more than one country is followed) loads and shows
  every followed country; default stays one country (the home country), `RadioCountryPreference.allOwn`.
- Genre chips toggle; several at once match any of them; "Kaikki" clears.

## Song on car / Bluetooth / media card (branch `now-playing-metadata`, 2026-09-19)

State: PHYSICAL PASS (media card, owner 2026-09-19). Touches AaltoSessionPlayer and PlaybackService (protected),
metadata only; no change to how a stream starts or plays.
Defect: the song travelled only in session extras (read by Aalto's own UI); a Bluetooth car,
Android Auto and the system media card read title/artist, which always held station name and genre.
Fix: `AaltoSessionPlayer.getMediaMetadata()` returns title = song, artist = station while a song
for the current station is known; `setAnnouncedTrack` notifies the session's listeners.
Physical checks: Bluetooth-only car display, Android Auto (DHU), system media card; station change
shows the new station name, not the previous song.

## Catalog servers (branch `catalog-servers`, 2026-09-19)

State: PHYSICAL PASS (owner 2026-09-19): catalog loads again after reinstall; airplane-mode test
shows the unavailable notice and "Yritä uudelleen" loads the catalog once online.
Reported: only ~10 built-in stations in search, Finland selected. The catalog asked one name,
all.api.radio-browser.info (one server per lookup); a down or slow server failed the whole catalog,
and the UI did not say so. Fix: servers looked up from DNS and tried in turn, with a fixed list of
known servers as fallback (`DefaultRadioBrowserBaseUrlProvider`); search shows "catalog unavailable"
with a retry button. Tests: RadioBrowserServersTest. Root cause confirmed: fresh reinstall (empty cache).

## First launch + wake-up radio layout (branch `first-launch`, 2026-09-20)
- New install only: a station picker instead of an empty start. "Tervetuloa Aaltoon. Ei mainoksia. Ei nyt eikä koskaan." Country chip with flag (changeable), 12 most-listened stations of that country as logo tiles; a tap picks the station and plays it; "Valmis · N asemaa" saves the picks as own stations in tap order (with Sync mutations) and playback continues; "Ohita" keeps the old default (Yle Klassinen, stored without a Sync mutation); "Etsi muita asemia" saves the picks and opens Search; "Onko sinulla jo Aalto-tili? Kirjaudu" signs in and closes the picker without adding a default.
- No account wall, no permission prompts. Offline: Aalto's built-in stations with a notice and "Yritä uudelleen".
- Decision stored in `aalto_first_launch` before the favourites migration runs: an existing user (Room migration done, or pre-Room favourites) never sees it; an interrupted first launch shows it again; finished = never again. With the picker pending, `prepareLocalData(seedDefaultFavorites = false)` adds no default favourite.
- One-time sync nudge (snackbar) after 7 days with 3+ own stations when not signed in.
- Wake-up radio sheet re-laid out as grouped cards: time + switch + status, notification warning with icon, day circles, the station as one row with logo (list opens in a dialog), "Lisäasetukset" card with snooze, volume and "Kokeile ääntä".
- No protected file touched (SyncEngine, SyncModel, transport, coordinator, player/service unchanged).
- CODE PASS: pending the user's build and unit tests (new FirstLaunchDecisionTest, 4 new StationRepositoryRoomTest cases). PHYSICAL PASS: not yet.

## Discovery and Now Playing ideas (same working tree as `first-launch`, 2026-09-20)
- World flag grid (`CountryGridSheet`): own countries first, then every country as flag tiles, with search by own-language or English name. Opened from Search's country menu ("Kaikki maat…") and from the first-launch country chip. A country chosen there shows at once, even before its stations load.
- Genre families (`Genres.kt`): each main genre has subgenres (Rock: klassinen rock, metalli, alternatiivinen, punk; new family Vuosikymmenet 60–2000-luku, oldies; etc.) in tinted cards with chips. Search chips: main genres, chosen subgenres up front, and "Lisää genrejä". Filter key format unchanged ("Pop|Metalli").
- Stream quality badge ("AAC · 128 kbps") on the Now Playing card while playing. Read from the decoder through a separate read-only MediaController (`playback/StreamFormat.kt`); the directory's codec and bitrate (new `RadioStation.declaredCodec/declaredBitrateKbps`) only fill in gaps, and only when the codec matches. No badge when nothing is known. Player and service files unchanged.
- Labelled actions: the compact card's timer and moon have captions; the roomy card (list hidden / landscape) centres play controls and shows round labelled buttons: Uniajastin, Kappaleet, Ääni (opens the audio sheet), Yönäkymä.
- Tests: StreamQualityTest, GenresTest, CountryGridTest. CODE PASS / PHYSICAL PASS: pending.
- Play Store screenshots: keep the "one promise per image + phone" style in mind for the listing (not built).

## Alarm reliability, automatic levelling, rewind (same working tree, 2026-09-20)
- Alarm permission: USE_EXACT_ALARM removed (Google Play allows it only for alarm-clock apps); SCHEDULE_EXACT_ALARM for all versions. Without it the alarm is still scheduled (inexact setAndAllowWhileIdle, may be minutes late) instead of silently not at all, and the alarm sheet shows a warning with "Salli tarkat hälytykset". Full-screen alarm permission (Android 14+) checked the same way. If the alarm service cannot start (inexact alarm), BackupAlarm rings the alarm tone as an insistent alarm notification. With no network at ring time the tone starts after 5 s instead of 20 s. ACCESS_NETWORK_STATE added.
- Automatic levelling (Plus): LoudnessProcessor (pass-through audio processor in ExoPlayer's sink) feeds a BS.1770 meter (LoudnessMeter, LUFS with gating). Per-station loudness stored in `aalto_loudness`; gain = −16 LUFS − measured (−12…+6 dB) + user's own adjustment (total −20…+9 dB). Applied at station start, and once gently (3 s ramp) when a first-time station has 20 s measured. Measuring runs for everyone; applying needs Plus and the switch in the audio sheet.
- Rewind: live MP3/AAC bytes the player reads are also written (ICY metadata stripped, titles kept with offsets) to a ring file in cache (RecordingDataSource). −30 s swaps the playing address to `aalto-timeshift://…?from=offset` (AaltoSessionPlayer.replaceCurrentUri, no station change) and a recorder connection keeps the ring filling; "Suora" swaps back. Errors while rewound fall back to live through the normal stream fallback. Free 30 s, Plus 30 min. HLS/Ogg/FLAC streams show no rewind controls. First device test (Yle): audio went back and "Palaa suoraan" worked, but the display showed about −0:57 after one −30: a new connection's burst of already-kept audio was appended again. Fixed (not yet re-tested): SeamAligner finds the ring's last 16 KB in the new connection and drops the repeat; gives up after 8 s or about a minute of data if there is no overlap. Log line `AALTO_TIMESHIFT seam: dropped …`.
- Protected files touched (reviewed, not device-tested): PlaybackService (renderers factory, media source factory, timeshift host, 1 s ticker, metadata refactor), AaltoSessionPlayer (replaceCurrentUri), StreamCandidates (timeshift address never a candidate).
- Tests: TimeshiftBufferTest, IcyStripperTest, LoudnessMeterTest. CODE PASS / PHYSICAL PASS: pending.
- Rewind rules (decided 2026-09-20, JarnoL): being behind stays until "Palaa suoraan", +30 to the edge, or a station change (model A, like a TV recorder). After any pause (own pause, phone call, Bluetooth disconnect, another app) playback always resumes live: AaltoSessionPlayer reloads at the live edge on play() after a pause (seekToDefaultPosition; rewound: Timeshift.live()), and after a call-type interruption longer than 5 s. Station change, alarm and car start live. The display counts behind in time (steady −0:30; grows only while playback stalls), not in bytes (the byte count jumped −0:29…−0:34 with Yle's ~5 s chunks). Behind is shown in the mini player too, and a one-time hint explains it.
- PHYSICAL PASS 2026-09-20 (JarnoL, OnePlus, Yle X): −30 shows a steady −0:30 (card and mini player), nothing repeated or jumped, one-time hint shown, "Palaa suoraan" works; pause → play resumed live (log: resume live after pause 9 s / 5 s); seam dropped 18–24 s of Yle's connect burst each time. PHYSICAL PASS 2026-09-20: alarm in flight mode rang the alarm tone (no network fallback). Not yet tested: phone call, Bluetooth disconnect, Plus 30 min, auto-levelling, alarm permission warnings.
- UI after device review (2026-09-20): +30 shown only when more than 30 s behind (otherwise it equals "Palaa suoraan"); no lasting Plus text on the player: at the limit ↺30 greys out and a tap shows a short toast (free only); night label "Yötila" (fits); with the quality badge the details line shows the genre only.

## Repository (2026-09-20)
- main fast-forwarded to catalog-servers (79b6438), tag known-good-2026-09-20. Remote: private https://github.com/jarno1303/aalto (origin); main, all branches and tags pushed by JarnoL. From now on: short branches merged into main as soon as they are verified.

## Levelling made clear, network return, sleep timer line (2026-09-20)
- Audio sheet: "Automaattinen tasaus" says what it does; a card shows the result for this station in one number (e.g. −7 dB) and how it is made up ("Tasaus −4 dB + oma säätösi −3 dB"), and which level stations are brought to. "Käytä tätä asemaa tasona" makes the station's measured loudness the target (held to −20…−12 LUFS; default −16), "Vakiotaso" goes back. The slider is "Oma säätö tälle asemalle" with a fine-tuning hint while levelling is on. Once, when levelling is on and stations have old own adjustments, a dialog offers to reset them (they would count twice); the reset syncs.
- Network return: when every address of a station has failed and the listener did not pause, the station plays again by itself when the network is validated again (system callback + 15 s backup check), for 15 minutes after the failure (AaltoSessionPlayer.retryIfWaitingForNetwork, PlaybackService). While offline the status line reads "Ei verkkoyhteyttä – jatkuu, kun yhteys palaa" (NetworkState).
- Alarm: the radio is tried for as long as the alarm rings (quick tries for the first minute, then every 15 s, and at once when the network returns), instead of giving up after 2 minutes; the tone keeps ringing meanwhile.
- Sleep timer: the quality badge is hidden while the timer runs, so "Uniajastin: … jäljellä" shows in full.
- Not yet device-tested. Tests: LoudnessMeterTest (reference target).
- Device review round 2 (2026-09-20): play button now in the exact middle of the compact card (equal-width sides), rewind row centred on it (three columns, +30 space kept empty); reference buttons stacked (the second one had been squeezed into a column of letters); the measuring card has a progress bar that fills with measured seconds and the sheet refreshes every second, so the result appears by itself (checkpoint every 2 s instead of 10 s).
- LevelSurvey: while levelling is on (Plus), all own stations without a value are measured one by one in the background by a second, silent player (volume 0, no audio focus, own meter; ~20 s of audible sound each, 60 s timeout, ~0.3 MB per station). The station being listened to is left to the player's own meter. Audio sheet lists "Omat asemat" with −x dB / progress bar / odottaa / ei saatu yhteyttä. Not yet device-tested.
- Sleep timer shown under its own button: label "Ajastin" when off (fits with large fonts), the minutes left in blue ("13 min") when on; the "Sammuu … min" line under the station name is gone, so the card stays balanced.
- PHYSICAL PASS 2026-09-20: sleep timer under its own button ("Ajastin" / "13 min") looks balanced and works (JarnoL).
- PHYSICAL PASS 2026-09-20 (JarnoL): network return (flight mode on → "Ei verkkoyhteyttä – jatkuu…" → off → station plays again by itself) and background measuring of own stations. Still open: phone call and Bluetooth disconnect resuming live.
