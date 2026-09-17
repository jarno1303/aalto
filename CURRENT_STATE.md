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
