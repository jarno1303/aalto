# AGENTS.md — Aalto Engineering Constitution

**Project:** Aalto Android internet radio  
**Package:** `fi.aalto.radio`  
**Purpose of this file:** stable product, architecture, UX, quality, and agent-execution rules.  
**Audience:** Codex and any coding agent working in this repository.

---

## 0. PRIME DIRECTIVE

Aalto is not trying to win by feature count.
### PRODUCT EVOLUTION LAW

**A new version should not add user work. It should remove it.**

Aalto does not measure progress by how many features are added.

A release is better when the user can accomplish the same or greater value with:
- fewer taps
- fewer decisions
- less waiting
- less configuration
- less visible complexity
- less need to understand how the software works

Prefer removing friction over adding capability.

Prefer making an existing workflow dramatically better over creating a new workflow.

Feature deletion is a valid product improvement.

Before adding a feature, ask:

1. Does this remove friction?
2. Does this improve fluidity, speed, reliability, or calm?
3. Do users genuinely need it?
4. Can it exist without complicating the primary listening path?
5. If this feature disappeared, would users actually miss it?

If the answers are weak, do not build it.

**Less friction. More fluidity.**


Aalto wins by making radio feel unusually fast, calm, reliable, and effortless.

### Product promise

> **This just works.**

### UX doctrine

> **Less friction. More fluidity.**

Every important interaction should feel:

- immediate,
- obvious,
- continuous,
- calm,
- forgiving,
- reliable.

### Architectural law

> **SYNC MAY FAIL. RADIO MAY NOT.**

Cloud, authentication, Firestore, Room maintenance, metadata, analytics, discovery, or any future backend feature must never become a prerequisite for starting an already-visible station.

### Four product pillars

1. **SPEED** — Aalto should feel instantaneous.
2. **CAR** — Aalto should feel like a radio that belongs in a car.
3. **SYNC** — Aalto should make the user's radio state appear everywhere without management.
4. **CALM** — Aalto should stay out of the user's way.

**Feature count has zero value by itself.**

If a change adds complexity without clearly strengthening at least one pillar, do not add it.

---

## 1. SOURCE OF TRUTH AND TASK STARTUP

Before editing code:

1. Read this `AGENTS.md` completely.
2. Read `CURRENT_STATE.md` if it exists.
3. Inspect the actual source involved in the task.
4. Inspect recent relevant tests.
5. Check repository/workspace state before changing anything.
6. Preserve unrelated user changes.
7. Establish the smallest safe scope.

If Git is unavailable at this workspace root, say so once and use the actual source tree as the source of truth. Do not pretend a Git diff exists.

Do not modify generated files under `build/` as source.

Do not infer a bug from an assumption when it can be verified first.

### Before diagnosing multi-device Sync

Verify the test environment before changing code:

- both devices are running the intended latest APK,
- both devices have network connectivity,
- both devices are signed into Aalto when cloud Sync is being tested,
- both devices use the same intended Google/Firebase account for same-account convergence,
- the receiving app is foregrounded when testing foreground live convergence,
- ADB is connected to the serial actually being inspected,
- local-only state is not being mistaken for authenticated cloud state.

A failed test caused by setup/auth/device mismatch is **not** evidence that Sync code is broken.

Never patch production code merely to satisfy an unverified diagnosis.

---

## 2. CORE PRODUCT MODEL

Aalto is a **radio utility**, not a content portal.

The user should not feel that they are operating a complicated media app.

The ideal mental model is:

> **Open → tap → radio plays.**

Phone and web may organize, search, and manage.

The car primarily listens.

Sync connects the environments invisibly.

The desired user reaction is not:

> “This app has many features.”

It is:

> “Why do other radio apps feel heavier than this?”

---

## 3. NON-NEGOTIABLE CORE UX

### 3.1 Station card

**Tap station card → play immediately.**

Do not insert:

- station detail screen before playback,
- second Play button,
- confirmation dialog,
- account requirement,
- network prerequisite unrelated to the stream,
- database operation that must finish before issuing playback.

### 3.2 Favorite action

Heart behavior is independent from playback:

- `♡` → add favorite,
- `♥` → remove favorite.

Tapping the heart must not:

- play the station,
- navigate,
- open details,
- require cloud acknowledgement.

Favorite UI updates locally first.

### 3.3 Official 3-step UX test

Give Aalto to a new user without instructions and ask them to:

1. start a radio station,
2. find a station they want,
3. make it a favorite.

Targets:

- visible station playback: **1 tap**,
- switch to another visible favorite: **1 tap**,
- known new station: approximately **2–3 actions**,
- search result playback: **1 tap**,
- favorite action: **1 tap**,
- at least **9/10 users** complete the full 3-step test without guidance.

If users need instructions, first simplify the UI. Do not reflexively add onboarding.

---

## 4. MAIN SCREEN — CALM IS A FEATURE

The main screen has an **element budget**.

For every persistent element ask:

> **Does the user need this during most listening sessions?**

If not, it probably belongs elsewhere.

### Main-screen priorities

Prefer:

- user's favorite/preset stations,
- clear currently-playing state,
- lightweight now-playing information,
- obvious Find/Search access,
- favorite action,
- one-tap Night Screen / screen-darkening action where appropriate.

Do not clutter the main listening surface with:

- news feeds,
- weather,
- podcast recommendation rails,
- generic recommendation feeds,
- EQ shortcuts,
- premium banners,
- ads,
- setup nags,
- unnecessary tabs,
- analytics/debug information,
- technical Sync controls.

**Omat / My Stations** is the user's quiet space.

Discovery does not intrude into it.
Premium does not intrude into it.
Podcast content does not intrude into it.
Aalto's own promotions do not interrupt it.

---

## 5. VISUAL AND MOTION QUALITY

Aalto should be visually premium without looking busy, decorative, or “gaming-like.”

### Visual direction

Prefer:

- restrained premium surfaces,
- excellent typography,
- clear station logos,
- subtle depth,
- limited accent glow,
- high contrast where needed,
- dark mode that works especially well in a car,
- light mode where appropriate,
- minimal visual noise.

Do not confuse “stellar” with more gradients, more glow, or more cards.

### Motion doctrine

Motion should communicate continuity, not decoration.

Animations must be:

- smooth,
- short,
- interruptible where appropriate,
- physically coherent,
- free of jank,
- subordinate to task completion.

Target smooth behavior on 60 / 90 / 120 Hz devices.

Do not put disk, database, Firebase, or network work in pointer-move or animation callbacks.

Avoid recomposing the entire screen for every drag pixel when a smaller state surface is possible.

### Haptics

Use haptics sparingly.

A single subtle haptic at the start of an intentional drag is preferable to repeated vibration while moving.

---

## 6. FAVORITE DRAG-AND-DROP STANDARD

When reorder UI is present, it must feel premium.

Required interaction:

**Long press → item lifts → follows finger continuously → surrounding items move smoothly → release commits final order.**

### Drag rules

- Stable station IDs / Compose keys are mandatory.
- The dragged item follows the finger at pixel-level resolution.
- Do not visually snap the dragged item row-by-row while the finger moves.
- Neighboring items animate smoothly out of the way.
- A subtle elevation and/or approximately `1.02` scale is acceptable while dragging.
- Smooth auto-scroll is required near list edges for long lists.
- Playback must never start because a drag began.
- Normal tap still plays immediately.
- Heart behavior remains independent.
- Cancelled drag leaves persisted order unchanged.

### Persistence rules

**No Room/database/network writes while the finger is moving.**

On successful drop only:

1. derive the final stable-ID order,
2. call the existing repository reorder path,
3. persist order atomically,
4. enqueue exactly the intended reorder mutation using the existing Sync architecture,
5. request asynchronous Sync through the existing coordinator.

Do not create a second reorder architecture in the UI.

---

## 7. NIGHT SCREEN VS DARK MODE

These are different concepts.

### Dark mode

A normal application appearance option, typically:

- System,
- Light,
- Dark.

Dark mode keeps the application fully usable.

### Night Screen / screen darkening

A one-action listening mode intended especially for dark car cabins.

Goal:

- nearly black / OLED-black background,
- extremely low visual emission,
- only essential now-playing/playback information,
- no distracting navigation or decorative motion,
- tap can temporarily reveal controls if needed.

Night Screen must not be implemented as a complicated settings workflow.

---

## 8. PROTECTED PLAYBACK CRITICAL PATH

Playback speed is sacred.

Conceptual path:

```text
UI station tap
  → RadioPlayer.play(...)
  → PlaybackService / persistent player ownership
  → Media3 / ExoPlayer
  → stream request
  → audio
```

The exact current code is the source of truth, but the architectural intent is stable:

- playback is independent from Sync,
- playback is independent from Firebase availability,
- playback is independent from account state,
- playback must not wait for recents persistence,
- playback must not wait for favorite persistence,
- playback must not wait for metadata enrichment.

### Never add to station tap → player command path without strong proof

Avoid:

- Firestore reads/writes,
- auth refresh,
- cloud Sync,
- remote metadata calls,
- synchronous Room writes,
- synchronous disk access,
- player recreation,
- unrelated navigation,
- blocking coroutine work.

Record recents asynchronously after the playback command where practical.

### Player tuning

Do not blindly reduce buffer sizes or change ExoPlayer ownership to improve a benchmark.

Measure first.

Reliability is part of performance.

Do not trade stable playback for an attractive millisecond number.

---

## 9. PERFORMANCE IS A RELEASE REQUIREMENT

Use `AALTO_PERF` debug instrumentation as a protected measurement tool.

Relevant metrics include:

- cold app start → main UI ready,
- station tap → playback command,
- station tap → player ready,
- station tap → playback started when reliably measurable,
- station A → station B switch.

### Product targets

- UI response to a station tap: perceptually immediate.
- Aalto-owned overhead before opening the stream: target **< 100 ms**.
- Healthy-stream station change: target approximately **< 1 second** when the external stream permits it.
- Cold start → usable local stations: as close to **< 1 second** as realistically possible.
- Favorites/presets must render from local data without waiting for the network.

A 300 ms improvement in a core radio path may be worth more than a new secondary feature.

Do not add analytics SDKs merely to measure development performance.

Debug measurement should be lightweight and local unless a separate telemetry task explicitly authorizes production telemetry.

---

## 10. LOCAL-FIRST DATA LAW

Room/local state is the immediate user-facing source of truth.

User actions complete locally first.

Cloud is propagation and backup, not the authority required to make the UI respond.

### Stable station identity

Do not use a mutable stream URL as the only station identity.

For Radio Browser stations, prefer the stable `stationuuid` identity.

### Favorite mutation pattern

For add/remove/reorder:

- local state changes immediately,
- local persistence and durable outgoing intent are atomic where required,
- Sync happens asynchronously,
- network failure does not undo normal local use.

Use non-destructive Room migrations.

Never use a database replacement strategy that can cascade-delete unrelated child data.

---

## 11. SYNC — INVISIBLE, LOCAL-FIRST, DURABLE

Sync is a quality attribute, not a manual workflow.

Desired user belief:

> **If I do this in Aalto, it appears on my other Aalto device.**

The user should stop thinking about synchronization.

### Core Sync architecture

Current architectural components include concepts such as:

```text
UI
  → repository
  → Room

local mutation
  → durable outbox / sync_mutations
  → SyncEngine
  → RemoteSyncTransport
  → FirestoreRemoteSyncTransport
  → Firestore

foreground remote revision signal
  → AaltoSyncCoordinator
  → existing SyncEngine path
  → Room
  → observed UI state
```

Use the actual source as the exact implementation reference.

### Sync invariants

- Local-first always.
- User action appears locally immediately.
- Durable mutations survive process death.
- Mutation IDs are idempotent.
- Deletes use tombstones so stale/offline devices cannot resurrect removed favorites.
- Genuine cross-device stale protection must remain effective.
- Same-device causal intent must not be lost merely because several offline changes share an older server base revision.
- Reorder represents the latest intentional full order.
- Applying remote data must not create an outgoing echo mutation.
- Remote revision/listener logic triggers the existing Sync path; it does not implement a second conflict engine.
- Normal online changes should appear on other authenticated foreground devices automatically within a short period, without user action.
- Offline changes remain pending and converge when connectivity returns.
- Process death must not lose still-relevant pending user intent.
- There is no normal-use manual **Sync** button.

### Live listener lifecycle

When authenticated and foregrounded, lightweight live revision observation may trigger Sync.

Listener lifecycle must be safe:

- start/re-register when foregrounded and authenticated,
- start appropriately after sign-in,
- stop on background,
- stop on sign-out,
- stop on close/disposal,
- ignore current/older revisions,
- suppress self/no-op events where appropriate,
- coalesce events to avoid sync storms.

### Protected known-good behavior

Two-device live Sync has been physically verified to work very quickly when both devices are correctly authenticated and running the intended build.

Treat that behavior as **known-good and protected**.

For unrelated tasks, do not modify Sync internals.

In particular, unrelated UI tasks should not edit these unless compilation truly requires it:

- `SyncEngine.kt`
- `SyncModel.kt`
- `FirestoreRemoteSyncTransport.kt`
- `AaltoSyncCoordinator.kt`
- Sync conflict/revision logic in DAO/repository code

If an unrelated task appears to require such a change, **STOP and explain why before editing**.

---

## 12. AUTHENTICATION AND ACCOUNT UX

Listening to radio does **not** require an account.

Aalto should work immediately after installation with local data.

Authentication is for cloud benefits such as Sync, backup, and multi-device continuity.

### Account principles

- no login wall before basic listening,
- signed-out mode remains useful,
- local favorites continue to work signed out,
- signing in should merge/bind existing local intent safely,
- same intended account should converge to the same cloud profile,
- sign-out must not break radio playback.

Do not log auth tokens.
Do not expose secrets.
Do not weaken UID-scoped Firestore security for convenience.

---

## 13. CAR-FIRST UX

Aalto in a car is a digital radio, not a phone dashboard copied onto a larger screen.

### Primary car surface

Prefer:

- large favorite/preset stations,
- one tap = play,
- obvious currently-playing state,
- recent stations,
- clear Find/Search,
- favorite action,
- minimal navigation.

Common driving actions should require approximately 1–2 interactions.

### Preset mental model

Next/Previous should prefer moving through the user's own preset/favorite sequence where platform integration permits, not arbitrary catalog stations.

### Search in car

The user must be able to discover and save a new station without first configuring it on a phone.

Use voice search where the platform supports it.

If a voice result is unambiguous, direct playback is preferable to an unnecessary results/details sequence.

### Platform order

Car development priority:

1. Android phone / Android head-unit experience,
2. Android Auto,
3. Android Automotive OS.

Do not redesign the core product around AAOS before the Android/Android Auto experience is excellent.

---

## 14. DISCOVERY AND SEARCH

Do not expose a raw enormous catalog as the main experience.

Mental structure:

- **Omat / My Stations** = user's own stations,
- **Löydä / Discover** = curated and locally relevant choices,
- **Haku / Search** = broad catalog access when needed.

Search result behavior uses the same mental model as station cards:

- tap result → play,
- heart → favorite.

Avoid:

```text
search
→ station details
→ Play
```

when the details step is not needed.

### Metadata quality layer

Aalto may build a quality layer over Radio Browser for:

- duplicate cleanup,
- canonical names,
- correct high-quality logos,
- aliases/search synonyms,
- country/local ranking,
- primary stream,
- fallback stream(s),
- stream health/history.

This quality layer should reduce user friction, not become visible complexity.

---

## 15. STREAM QUALITY AND FALLBACK

If a primary stream fails and a known healthy fallback exists, Aalto should attempt recovery automatically where safe.

The desired user experience is not:

> “Primary stream failed; choose an alternate URL.”

It is:

> **Aalto works.**

Do not route radiostream traffic through Aalto's cloud backend unless a future task explicitly changes the product architecture for a compelling reason.

Default audio path remains:

```text
radio station → user's device
```

---

## 16. FEATURE GATE — LESS FRICTION, MORE FLUIDITY

For every proposed feature ask:

> **Does this make Aalto faster/easier/better — or merely bigger?**

Also ask:

> **Does it reduce friction or improve fluidity in a frequent user task?**

And for main-screen elements:

> **Does the user need this during most listening sessions?**

And for releases:

> **Does at least one of Speed, Car, Sync, Calm improve without another clearly degrading?**

If the answer is no, do not add the feature by default.

### Lower-priority / evidence-gated features

Do not push these into the core experience without explicit scope and demonstrated user value:

- advanced EQ,
- widget proliferation,
- podcast feature-monster behavior,
- recommendation feeds,
- social features,
- news/weather portals,
- generic content-feed behavior,
- large dashboard surfaces,
- excessive settings.

Podcast Lite may exist later, but radio remains Aalto's identity.

---

## 17. QUIET MONETIZATION

Monetization must not poison the radio experience.

### Free experience must remain excellent

Do not deliberately make free listening annoying to force conversion.

Do not paywall:

- basic radio playback,
- fast playback,
- reliability,
- ordinary search,
- basic local favorites,
- normal station switching.

### Premium principle

Paid value should primarily extend continuity and convenience, for example:

- cloud/device Sync,
- web management,
- multiple personal lists,
- cloud backup,
- future advanced cross-device capabilities.

Premium should mean **more Aalto everywhere**, not **a deliberately worse radio unless you pay**.

### Never

Aalto-owned monetization must not:

- delay station start,
- insert an Aalto audio preroll before a station,
- interrupt station switching,
- clutter the main listening UI with recurring upsell banners,
- interfere with car use.

Upsell should be contextual and quiet.

---

## 18. ACCESSIBILITY AND TOUCH QUALITY

Do not sacrifice accessibility for visual minimalism.

Maintain:

- sensible touch target sizes,
- readable contrast,
- meaningful content descriptions where required,
- clear selected/playing/favorite state,
- non-color-only state communication where appropriate,
- accessibility alternatives for drag-only organization when the feature matures.

A “minimal” interface that is difficult to use is not Aalto-quality.

---

## 19. DEPENDENCY AND ARCHITECTURE DISCIPLINE

Do not upgrade dependencies during unrelated feature work.

Do not add a framework because it is fashionable.

Do not rewrite a working architecture merely because another architecture is theoretically cleaner.

Prefer:

- smallest correct change,
- existing project patterns,
- clear boundaries,
- measurable benefit,
- reversible changes.

Avoid:

- speculative abstractions,
- broad refactors during bug fixes,
- duplicate data paths,
- duplicate Sync engines,
- duplicate playback ownership,
- new state sources competing with Room without a deliberate architecture decision.

---

## 20. BUG-FIX DISCIPLINE

Before editing a bug:

1. reproduce or establish credible evidence,
2. locate the failing boundary,
3. prove the root cause,
4. write or identify a regression test,
5. make the smallest correct fix,
6. verify physical behavior when the bug is device/lifecycle/audio/gesture dependent.

Do not “fix” the subsystem adjacent to the symptom unless evidence points there.

A log such as `pull_count=1` does not by itself prove the incoming mutation application is broken; inspect authentication, account state, device identity, revision semantics, and whether the fetched mutation is expected to change local state.

When physical evidence contradicts a hypothesis, update the hypothesis — do not force the code to match the hypothesis.

---

## 21. LOGGING RULES

Use concise structured debug logging.

Existing useful domains include:

- `AALTO_PERF`
- `AaltoSync`
- `AALTO_SYNC`

Good diagnostic events explain transitions and decisions, e.g.:

```text
sync_requested
sync_start
push_count count=N
mutation_send ...
mutation_ack ...
pull_from_revision revision=N
pull_count count=N
live_listener_started
live_revision_seen remote=X local=Y
live_sync_requested
live_listener_stopped
sync_complete ...
```

Do not log:

- auth tokens,
- passwords,
- secrets,
- private user content unnecessarily.

Do not leave noisy temporary per-frame/per-pixel logs in production paths.

---

## 22. TESTING STRATEGY

Use the narrowest useful tests while iterating.

Do not repeatedly burn time/compute on the entire suite after every tiny edit.

### During implementation

Run:

- focused unit tests,
- focused repository/DAO tests,
- focused coordinator/Sync tests,
- compilation of the changed target where useful.

### At task completion

Unless the task explicitly defines a smaller gate, run once:

```powershell
.\gradlew.bat app:assembleDebug
.\gradlew.bat app:testDebugUnitTest
.\gradlew.bat app:lintDebug
.\gradlew.bat app:build
```

A green build is necessary but not sufficient for device-dependent behavior.

### Physical acceptance is mandatory for changes affecting

- playback,
- station switching performance,
- lifecycle,
- authentication,
- multi-device live Sync,
- offline/reconnect Sync,
- drag/drop feel,
- Android Auto / car UX,
- stream fallback.

Do not claim physical PASS if only automated tests ran.

---

## 23. SYNC CHAOS / RELIABILITY ACCEPTANCE

Sync is not production-quality based only on happy-path tests.

Relevant scenarios include:

- many favorites added,
- multiple reorders,
- one device offline,
- both devices making independent changes,
- add on A + add on B offline,
- delete while another device is stale,
- app killed with pending outbox work,
- restart offline,
- reconnect,
- Wi-Fi → mobile → offline → online,
- retryable Firestore/server failure,
- duplicate mutation delivery,
- stale cross-device reorder,
- rapid remote revision events,
- listener stop/restart lifecycle.

Acceptance principles:

- no lost intentional user mutations in known scenarios,
- no unwanted duplicates,
- deleted favorite does not resurrect,
- deterministic final order,
- local interactions remain immediate,
- no Sync failure slows radio playback.

---

## 24. RELEASE QUALITY SCORECARD

Evaluate meaningful releases approximately with this weighting:

| Area | Weight |
|---|---:|
| Speed | 25% |
| Car UX | 25% |
| Sync reliability | 25% |
| Discovery / search | 10% |
| Visual quality + Calm | 10% |
| Other | 5% |

**Feature count = 0%.**

A release is not better because it has more code or more screens.

---

## 25. 10-SECOND DEMO TEST

Aalto's core advantages should be visible without explanation.

### Speed

Tap station → sound changes almost immediately when the stream permits.

### Sync

Change a favorite on one authenticated device → it appears on another without manual refresh.

### Simplicity

New user taps a visible station → radio plays.

### Calm

The user is not confronted with feeds, banners, setup work, or unnecessary controls.

If the product advantage needs a long explanation, sharpen the experience.

---

## 26. DEVELOPMENT PHASE DISCIPLINE

Do not silently advance the roadmap during an unrelated task.

If the task is drag-and-drop, do not start Android Auto.
If the task is Sync reliability, do not start podcasts.
If the task is UI polish, do not refactor playback ownership.

Complete the requested scope, verify it, report it, and stop.

Potential roadmap sequence remains broadly:

1. ultrafast reliable radio core,
2. excellent local favorites/presets and organization,
3. local-first data foundation,
4. account + multi-device Sync,
5. Sync chaos/reliability hardening,
6. Android Auto / car UX,
7. station metadata quality + stream health/fallback,
8. device pairing + lightweight web management,
9. commercial/release hardening,
10. internationalization and additional platforms only after core validation,
11. Podcast Lite / widgets / EQ only from demonstrated user demand.

Exact current phase belongs in `CURRENT_STATE.md`, not in this stable constitution.

---

## 27. PROTECTED KNOWN-GOOD CHECKPOINT RULE

When a subsystem has been physically verified as excellent, treat it as protected.

Before risky work:

- create a Git checkpoint if Git exists, or
- create an Android Studio Local History label / other explicit backup if Git does not exist.

Do not destroy a known-good state to pursue a speculative improvement.

If accidental work was based on a false diagnosis, revert only that work and re-run the quality gate.

For currently verified Aalto behavior, especially protect:

- fast playback,
- local-first favorites,
- physically verified fast multi-device live Sync,
- auth-independent basic radio use.

---

## 27b. PAYWALL BOUNDARY RULE

Aalto Plus is additive only. Anything that is free today stays free forever:
a paid feature is never created by taking something away.

The boundary is specified in `docs/AALTO_PLUS.md`. Two rules bind the code:

1. **One entitlement check per feature, at its entry point.** Never inside a
   feature, never in a loop, never duplicated. If `isActive()` starts
   appearing in scattered places, stop and move it back to the boundary.
2. **The check fails open.** If billing is unreachable, a paying user keeps
   access. Locking a paying customer out on a plane is worse than letting a
   few free users in.

The paywall must never touch:

- the playback path (RadioPlayer, PlaybackService, AaltoSessionPlayer):
  the radio never stops or refuses to play because of an entitlement,
  not even when the entitlement check itself fails;
- the alarm: it must ring and be dismissable whatever the entitlement says,
  because an alarm is a promise, not a feature;
- Android Auto and the widget: no purchase prompts in a car, ever;
- Sync: the user's own stations are never locked to one device.

An agent that finds itself adding an entitlement check inside any of those
four has misread the task.

---

## 28. AGENT EFFICIENCY / CODEX USAGE RULES

Use reasoning budget on solving the problem, not repeatedly rediscovering the project.

### Prefer

- one well-scoped task,
- reading this file and current state first,
- targeted source inspection,
- targeted tests during iteration,
- one full quality gate at completion,
- concise diagnostic logging,
- concise completion report.

### Avoid

- re-auditing the entire repository for a tiny bug,
- repeatedly running the full build after every edit,
- unrelated dependency upgrades,
- speculative refactors,
- changing multiple architectural layers before proving the failing layer,
- rewriting working systems to match a hypothetical cleaner design.

If the requested change can be solved in the UI layer, keep it in the UI layer.

If the requested change can reuse an existing repository/DAO/Sync path, reuse it.

---

## 29. REQUIRED COMPLETION REPORT

At the end of a coding task, report concisely:

1. **Root cause / goal**
2. **Files changed**
3. **Exact implementation**
4. **What was deliberately not changed**
5. **Tests added/changed**
6. **Focused test results**
7. **Final Gradle gate results**
8. **Performance impact / whether playback critical path changed**
9. **Physical retest steps** when relevant
10. **Known remaining debt** directly related to this task

Do not claim success beyond the evidence.

Use explicit labels such as:

- `CODE PASS`
- `PHYSICAL PASS`
- `PENDING PHYSICAL ACCEPTANCE`
- `FAIL`

where useful.

---

## 30. FINAL ENGINEERING CHECKLIST

Before considering a change complete, answer all of these:

### SPEED

- Did station playback stay just as fast or become measurably better?
- Did I accidentally place disk/database/network/auth work before the playback command?

### CAR

- Did this make common car use easier, or at least not harder?
- Are targets obvious and touch-friendly?

### SYNC

- Did local-first behavior remain intact?
- Can Sync still fail without breaking radio?
- Did I preserve existing known-good convergence behavior?

### CALM

- Did I add visible clutter?
- Does this element really belong on the main surface?

### FLUIDITY

- Does the interaction feel continuous rather than stepwise or jerky?
- Is motion smooth on high-refresh-rate devices?
- Did I keep persistence/network work out of continuous gesture callbacks?

### RELIABILITY

- Is the user action durable across process death/restart where expected?
- Are failure/retry paths deterministic?

### SCOPE

- Did I change anything unrelated?
- If yes, was it truly necessary and explicitly justified?

If a change makes Aalto bigger without making it meaningfully faster, calmer, more fluid, more reliable, or better in the car, reconsider it.

---

# AALTO UX MANIFESTO

**Aalto is a radio the user should not need to learn.**

Open → tap → plays.  
Want something else → Find → tap → plays.  
Like it → heart.  
Want a different order → long press → fluid drag → release.  
Change it on one device → it appears on the others.  
If the network disappears → local use continues.  
If Sync fails → radio still plays.  
If a stream fails and Aalto can recover automatically → recover automatically.  
At night → one action can make the screen calm and dark.  
In the car → Aalto behaves like a radio, not a dashboard.

> **Fast radio. Car-first simplicity. Invisible Sync. Quiet UI. Quiet monetization.**

> **Less friction. More fluidity.**

> **This just works.**

