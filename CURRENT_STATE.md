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
