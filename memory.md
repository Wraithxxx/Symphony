# Symphony development memory

Last consolidated: 2026-07-20

This file is the durable conceptual reference for this Symphony fork. It records the product requirements, relevant architecture, decisions, verified implementation history, rejected approaches, build procedure, and rules for future stages. `Problems.md` tracks the five resolved foundational defects, `Features.md` tracks Stage 6 onward, and `tests.md` owns their regression checklists; this file explains how the pieces fit together and why the decisions were made.

## Current snapshot

- Baseline source analyzed and baseline debug APK preserved.
- Problem 1, Android audio-focus interruption handling, is implemented and successfully verified on the user's Android phone.
- The later Bluetooth-earbud resume experiment was unsuccessful because Android routed the media command outside Symphony. It was fully reverted and must not be treated as part of Stage 1.
- Problem 2 / Stage 2 is implemented and phone-verified. The user confirmed that relative and slider seeking cross the former absolute/restored-position boundary without snapping back.
- Problem 3 / Stage 3 is implemented and phone-verified. It adds early cached-queue restoration, explicit readiness, pending Play, restored-seek gating, coherent UI/media-session state, and generation guards.
- Problem 4 / Stage 4 is implemented and phone-verified. It publishes track identity, position, duration, readiness, and playing state atomically and rejects old-player callbacks.
- Problem 5 / Stage 5 is implemented and phone-verified. Foreground and manual refreshes commit an off-screen library snapshot without stopping playback.
- Feature 1 / Stage 6 is implemented as a verified test candidate. Single audio files can be permanently deleted through provider-aware SAF transactions; focused phone acceptance is pending.
- Feature 2 / Stage 7 is implemented as a verified cyclic-navigation candidate. Manual Previous and Next cross queue boundaries regardless of how an endpoint was reached; repeat modes remain limited to automatic completion behavior.
- The immediate next actions are focused Stage 7 cyclic-navigation testing and the still-pending focused Stage 6 deletion acceptance tests.
- Android 9 and newer must remain supported. The project currently has `minSdk 28`, `targetSdk 34`, and `compileSdk 35`.

## Product and usage requirements

This is not only a short-song player. The intended library contains music, podcasts, and audio stories ranging from roughly 2–3 minutes to 6–7 hours.

The storage library changes frequently, usually every day. New podcasts and stories are downloaded often and completed files are deleted soon afterward. Future designs must therefore prioritize:

- Accurate seeking and position persistence for multi-hour media.
- `Long` millisecond values throughout playback, persistence, and media-session state. Integer narrowing is permitted only at an Android API boundary that requires it, after range validation.
- No meaningful loss of progress after process death, restart, rescan, or library changes.
- Incremental, non-destructive library synchronization.
- Stable song identity across scans where the underlying file is unchanged.
- First-class in-app deletion through Android's Storage Access Framework where provider permissions allow it.
- A stable, separately archived APK after every completed stage so each change can be tested independently and rolled back.

## Architectural model

### Composition and lifecycle

`Symphony.kt` is the application-level `AndroidViewModel` and composition root. It owns settings, databases, the library service (`Groove`), the playback service (`Radio`), permissions, and translations. Its hook interface propagates application and activity lifecycle events to services.

### Library subsystem: Groove

`Groove` coordinates media discovery and the live repositories for songs, albums, artists, album artists, genres, and playlists.

- `MediaExposer` scans user-selected Storage Access Framework locations and exposes media records.
- Room-backed stores cache song metadata, artwork, lyrics, playlists, and persistent state.
- Repository instances hold the live in-memory library used by the UI and playback lookups.
- Current refresh behavior is destructive when reset options are used and is not designed for frequent idempotent refreshes. Re-emitting cached records can duplicate or recalculate repository state, while resetting repositories temporarily removes records needed by active playback.

This means automatic refresh must not be implemented by simply calling the existing fetch function more frequently. Problem 5 requires a snapshot/diff/commit design.

### Playback subsystem: Radio

`Radio` coordinates the current `RadioPlayer`, queue, focus, media session, notification, playback effects, sleep timer, and event publication.

- `RadioPlayer` is a Symphony adapter around Media3 ExoPlayer 1.8.1. It preserves the app's focus, queue, notification, gapless, speed/pitch, and playback-snapshot infrastructure while using Media3's progressive extractors and coordinated asynchronous seeking.
- `RadioQueue` owns the logical queue, index, shuffle, loop, and persisted queue restoration.
- `RadioFocus` owns Android audio-focus requests and callbacks.
- `RadioSession` bridges playback to Android media controls and the notification.
- `RadioObservatory` converts Radio events into `StateFlow` values consumed by Compose.
- `RadioShorty` provides small command helpers for UI, notification, and controller actions.

The player now has a seek coordinator around platform `MediaPlayer.OnSeekCompleteListener`. The surrounding app still does not fully expose preparing, buffering, ready, and audibly advancing as distinct UI states; that remaining mismatch is central to Problems 3 and 4.

### UI

The UI is Jetpack Compose. The now-playing screens, mini-player, queue, notification, and media session observe related state through different paths. Future playback work should publish a coherent snapshot or generation-aware events so metadata from one track cannot be shown with position or readiness from another track.

## Baseline

The untouched baseline APK is retained permanently at:

`artifacts/baseline/Symphony-baseline-debug.apk`

Baseline SHA-256:

`6f648e334a14c796a120853f53977d6011e13a80a92c099424793050d67c09fd`

Baseline package and compatibility:

- Package: `io.github.zyrouge.symphony.debug`
- Version: `2024.12.115-debug` (`versionCode 115`)
- Minimum SDK: 28 / Android 9
- Target SDK: 34
- Debug signed with APK Signature Scheme v2

The baseline exists to distinguish upstream behavior from regressions introduced by this development effort. It must not be overwritten.

## Stage 1: audio-focus interruption recovery

### Reported problem

When any application or system component interrupted Symphony with audio, Symphony stopped and did not resume after a temporary interruption ended. This was app-agnostic and not specific to YouTube, Instagram, or any other named application.

### Root cause

The old focus callback marked playback for restoration and then used the normal pause path. The normal pause path abandoned the audio-focus request. Once focus was abandoned, Symphony could lose the future `AUDIOFOCUS_GAIN` callback required to perform the pending resume.

The old code also did not model transient loss, ducking loss, and permanent loss as distinct state transitions, even though Android gives them different semantics.

### Final behavior

- Temporary focus loss while actively playing pauses immediately without abandoning the focus request.
- A later focus gain resumes only if that temporary loss interrupted active playback.
- Temporary focus loss while already paused does not schedule a resume.
- Manual pause or stop cancels pending automatic recovery.
- Ducking loss lowers volume and focus gain restores volume without restarting playback.
- Permanent focus loss clears pending recovery, pauses, and abandons focus. Symphony does not automatically compete with the new media application.
- The existing **Ignore audio focus loss** behavior remains respected.

### Implementation

`RadioFocusState.kt` was added as a pure state machine. It records one pending recovery action: none, resume, or restore volume. It exposes explicit loss and gain actions so Android callbacks remain thin and testable.

`RadioFocus.kt` now:

- Separates `AUDIOFOCUS_LOSS_TRANSIENT`, `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`, `AUDIOFOCUS_LOSS`, and `AUDIOFOCUS_GAIN`.
- Retains the focus request during a transient pause.
- Abandons focus after permanent loss.
- Clears stale recovery state on explicit focus requests and user cancellation.

`Radio.kt` now has different paths for different intent:

- `pause()` is a user pause. It cancels pending recovery and abandons focus.
- `pauseForAudioFocusLoss()` is immediate and does not itself abandon transient focus.
- `resume()` explicitly requests focus.
- `resumeAfterAudioFocusGain()` resumes without making a redundant focus request from inside the gain callback.
- `stop()` and `pauseInstant()` clear pending recovery and abandon focus.
- `duck()` and `restoreVolume()` adjust volume independently of playback state.

### Tests and verification

`RadioFocusStateTest.kt` covers:

- Resume only when transient loss interrupted active playback.
- Repeated transient events retaining the original pending resume.
- Duck and restore without resume.
- Permanent loss clearing temporary recovery.
- Manual cancellation preventing recovery.
- Ignore-loss behavior scheduling no action.

Stage 1 passed unit tests, debug assembly, and Android lint. The user then successfully verified the behavior on a physical Android phone. This device result is the reason Problem 1 is marked implemented rather than merely build-complete.

Archived Stage 1 APK:

`artifacts/timeline/stage-01-audio-focus/Symphony-stage-1-audio-focus-debug.apk`

SHA-256:

`67344ba90ff235798fd19986ef9a62cfb87e4e3211fd53d2ff1c0537a0f05220`

## Rejected Stage 1.1 experiment: Bluetooth resume after video

The requested experiment was to keep the correct permanent-loss behavior—no automatic resume over video—but allow an earbud tap to resume Symphony after the video stopped.

An in-scope attempt added explicit media-session flags, direct media-key mapping, and idempotent Play/Pause commands. The code could handle the command correctly if Android delivered it to Symphony. On the test phone, Android retained media-session routing elsewhere, so the earbud command did not reach Symphony and the feature did not work.

The experiment was fully reverted:

- Direct Bluetooth media-key handling was removed.
- The extra media-button tests were removed.
- The experimental APK and checksum were deleted.
- Stage 1 audio-focus behavior was preserved.

Conceptual lesson: Symphony can correctly process a media command it receives, but it cannot guarantee that Android routes a Bluetooth command to its inactive/older media session after another video application became the current media owner. Do not reintroduce this experiment unless the requirement or platform strategy materially changes.

## Stage 2: completion-aware seek coordination

### Reported problem

After an absolute slider seek or restoration to a saved non-zero position, the selected timestamp became a lower boundary for relative backward seeking. The slider could still enter the earlier region, but repeated backward controls could not cross the earlier absolute target. This affected every tested format and duration, including long-form audio.

### Confirmed implementation defects

Android `MediaPlayer.seekTo()` is asynchronous, but Symphony treated it as synchronous, immediately read the prior platform position, and had no `OnSeekCompleteListener`. Relative commands therefore calculated from decoder state that could disagree with the user's requested target. Android also processes at most one seek at a time and retains only the latest queued request, which Symphony did not model.

Symphony used the legacy `seekTo(Int)` overload, equivalent to previous-sync seeking, and narrowed a `Long` position before calling the player. Forward and backward seek duration settings also accidentally shared the same preference key.

### First implementation and failed device result

- Added `RadioSeekState`, a synchronized pure state machine with one active seek and one newest pending target.
- `RadioPlayer.playbackPosition` reports the latest requested target while seeking. UI timers and relative controls therefore cannot regress to a stale decoder position.
- `OnSeekCompleteListener` advances to the newest pending target or settles on the confirmed position.
- Replaced legacy integer seeking with `seekTo(long, MediaPlayer.SEEK_CLOSEST)`.
- All targets are clamped only to the real track duration, with `Long` millisecond arithmetic retained end to end.
- Destroying a player resets its coordinator, and callbacks on unusable players are ignored.
- Exposed a seek-completion listener for Stage 3's restored-readiness and pending-Play work.
- Assigned independent backward and forward preference keys and added one-time migration of the previously shared value.

This build failed on the phone. Both a backward-button request and a slider request to the same nearby earlier timestamp flashed the target and then snapped back to the original boundary. The platform player was confirming the old position, so target calculation and UI routing were not the remaining cause.

### Media3 experiment and rollback

- Replaced the internal platform `MediaPlayer` instance with Media3 ExoPlayer 1.8.1 while keeping Symphony's `RadioPlayer` interface and higher-level architecture.
- Uses `ProgressiveMediaSource`, Media3's bundled extractors, and constant-bitrate approximate seeking where supported.
- Retains the seek coordinator and settles it from Media3 position-discontinuity callbacks.
- Uses the main player looper for creation, restoration, commands, callbacks, and 100 ms position updates; background consumers read cached snapshots.
- Disables ExoPlayer's internal focus management so the verified Stage 1 `RadioFocus` policy remains authoritative.
- Preserves fading, ducking, speed, pitch, audio-session ID, gapless next-player preparation, completion, and errors through the existing wrapper contract.
- Media3 1.8.1 was selected because current 1.10.1 requires compile SDK 36; 1.8.1 is compatible with this project's compile SDK 35 and avoids an unrelated Gradle/SDK migration.

The Media3 implementation passed compilation, all 17 unit tests, lint, APK assembly, signature verification, and Android 9 metadata checks, but the app closed during startup on the physical phone. A later ADB capture on the user's Motorola G57 Power identified the exact exception: `NoClassDefFoundError: androidx.startup.R$string`, thrown by `androidx.startup.AppInitializer` before Symphony's activity was created. APK comparison showed that the failed APK contained the `androidx_startup` resource value and a reference to `R$string`, but omitted the generated `androidx.startup.R$string` class; the verified Stage 1 APK contained both the reference and class. This establishes an incomplete/stale APK packaging output rather than a Media3 runtime or device-compatibility failure. The failed APK remains withdrawn, the Media3 source remains reverted, and any renewed experiment must use a clean build and verify the generated Startup class before device installation.

After diagnosis, the verified Stage 1 APK was reinstalled with `adb install -r`, preserving app data. It launched successfully on the Motorola G57 Power, remained alive, and produced no `AndroidRuntime` startup exception.

### Rebuilt Stage 2 candidate

Stage 2 was restarted from the verified Stage 1 state. The Media3 ExoPlayer 1.8.1 wrapper was rebuilt around one main-looper engine, cached playback snapshots for background readers, the existing serialized seek coordinator, `ProgressiveMediaSource`, bundled extractors, and constant-bitrate approximate seeking. Media3's internal audio-focus handling remains disabled so Stage 1's verified `RadioFocus` policy stays authoritative. AndroidX Startup 1.1.1 is declared directly.

The clean candidate passed all 17 unit tests, Android lint, APK assembly, v2 signature verification, and Android 9 metadata verification. Before installation, `dexdump` confirmed that `androidx.startup.R$string` exists as a public class in the final APK. It installed with app data preserved and remained alive through queue-restoration time on the Motorola G57 Power with no AndroidRuntime or ExoPlayer error. The user verified the original backward-seek and slider snap-back reproduction as fixed.

Archived successful Stage 2 APK:

`artifacts/timeline/stage-02-media3-seek/Symphony-stage-2-media3-seek-debug.apk`

SHA-256:

`56919586e2dec8fc1d38b7f034a4ae6833ffa67f897eb639c67927c1b2c4aeca`

### Automated verification

The Stage 2 source passed 17 unit tests in total, including six focus tests, six seek-state tests, the independent-key test, and four existing format/parser tests.

Safe rollback APK remains archived at `artifacts/timeline/stage-01-audio-focus/Symphony-stage-1-audio-focus-debug.apk` with SHA-256 `67344ba90ff235798fd19986ef9a62cfb87e4e3211fd53d2ff1c0537a0f05220`.

Both failed Stage 2 APKs were removed rather than archived. Only the rebuilt, phone-verified Media3 APK is retained as the successful Stage 2 timeline build.

## Stage 3: robust cold restoration and pending Play

Stage 2 already removed the observed silent-delay symptom and the user confirmed a cold-start test, but the architecture still waited for a full storage scan, discarded Play before preparation, did not gate start on restored-seek completion, and exposed no readiness state. Stage 3 implements those guarantees rather than relying on the faster decoder alone.

- `RadioPlaybackState` models Idle, Restoring, Preparing, Seeking, Ready, Playing, and Error plus a separately cancelable pending-Play intent.
- Play during restore/preparation/seek is retained and starts once only after preparation and seek completion. Pause, Stop, error, and explicit cancellation clear it.
- The intended restored timestamp remains authoritative for UI and persistence while the decoder is preparing.
- Saved queue records are queried from Room before the full filesystem scan. Cached IDs are filtered coherently, the current song is seeded for UI/playback, and the scan continues in parallel.
- Cached songs remain available as playback fallbacks for upcoming queue items without prematurely publishing the entire cached library.
- `SongRepository.onSong()` is idempotent so the later scan cannot duplicate the early-seeded current path.
- Player callbacks and asynchronous media-session artwork updates are generation-guarded.
- Full and mini-player controls show a cancellable progress indicator for pending Play. The media session reports Buffering until actual playback begins.
- Restoration milestones are logged using elapsed realtime.

The final Stage 3 candidate passed a clean build, 25 unit tests, Android lint, APK assembly, v2 signature verification, Android 9 (`minSdk 28`) verification, and AndroidX Startup class inspection. On the Motorola G57 Power, the final ADB cold launch loaded the cached queue in 69 ms and completed player preparation plus restored seeking in 926 ms without AndroidRuntime, ExoPlayer, or duplicate-repository errors.

Archived successful Stage 3 APK:

`artifacts/timeline/stage-03-robust-cold-restore/Symphony-stage-3-robust-cold-restore-debug.apk`

SHA-256:

`e4c4c6f8caeb85dd8a152f4f76a432854f6e94e0469aef118aa11f97d0f74657`

## Stage 4: atomic track transitions

Problem 4 was caused by separately published queue identity and playback-position state. Compose could render the newly selected title while retaining the prior player's elapsed position until the replacement emitted its first timer update.

- `RadioPlaybackSnapshot` now carries the playback generation, song ID, queue index, position/duration, readiness, actual playing state, and pending-Play state as one immutable value.
- A new selection stages its zero position (or explicit restored position), new duration, and readiness before publishing the queue-index transition.
- The replacement path suppresses the old stopped-state publication, and automatic completion no longer performs a redundant pre-stop.
- Playback-position callbacks are accepted only from the current player instance and generation. Snapshot publication also rejects older generations.
- Full now-playing, seek bar, and mini-player state come from the unified snapshot. Changing generations clears temporary seek-drag state.
- Media-session and notification work validates the captured snapshot after asynchronous artwork loading so an older request cannot overwrite the selected track.

The Stage 4 candidate passed a clean build, all 29 unit tests, Android lint, v2 signature verification, `minSdk 28` / `targetSdk 34` inspection, and AndroidX Startup class inspection. The exact artifact installed with data preserved and cold-launched on the Motorola G57 Power in 1.9 seconds; the process remained alive and its log contained no AndroidRuntime, missing-class, ExoPlayer, or duplicate-path errors. The user then confirmed all focused Stage 4 tests passed.

Archived successful Stage 4 APK:

`artifacts/timeline/stage-04-atomic-track-transition/Symphony-stage-4-atomic-track-transition-debug.apk`

SHA-256:

`b959dbb5d4009d5722dd7f87554a24462305c0da12278f9da7393a7dec6599b1`

## Stage 5: non-destructive automatic library refresh

The former Rescan path explicitly stopped `Radio`, cleared repositories, and repopulated live state one item at a time. It lost playback progress, temporarily emptied the UI, duplicated repeat emissions, and could orphan queue IDs when a changed file was assigned a new parsed ID.

- `MediaExposer` now scans configured SAF trees into private song, URI, and folder snapshots. Live repositories remain untouched until traversal succeeds.
- Unchanged files reuse Room metadata. Changed files are reparsed using the cached stable ID for the same path, preserving queue and internal-playlist references.
- Songs and all derived album/artist/album-artist/genre indexes are rebuilt off the main thread and swapped without an empty-library phase.
- A failed tree open or traversal invalidates the candidate snapshot; the last valid library and playback session remain in place.
- Manual Rescan and metadata-cache refresh no longer call `radio.stop()`.
- `MainActivity.onResume()` now propagates a foreground event. `Groove` serializes scans and suppresses automatic repeats within two seconds, while manual actions remain forced.
- Deleted non-current tracks are removed from both original and shuffled queue forms, with the current song's index recalculated. Queue records are retained through commit so no live lookup disappears mid-transaction.
- An externally deleted current song is kept as a hidden in-memory tombstone for the active player; it is not shown in the library snapshot. This avoids a refresh-triggered stop and allows provider-dependent already-open playback to continue where possible.
- Playlist fetch is idempotent. Repeat-safe repository work also fixes stale artist/album-artist relationship caches and their aggregate album counts.
- Refresh diagnostics record elapsed time, added/changed/removed totals, library size, active song, and player-generation preservation.

The Stage 5 candidate passed a clean build, all 35 unit tests, Android lint, v2 signature verification, `minSdk 28` / `targetSdk 34` inspection, and AndroidX Startup class inspection. On the Motorola G57 Power, the installed artifact committed an initial eight-track snapshot in 1,401 ms and an unchanged foreground refresh in 85 ms. The latter preserved the existing player generation and produced no duplicate-path or runtime errors. The user then confirmed the focused storage-change and uninterrupted-playback tests passed.

Archived successful Stage 5 APK:

`artifacts/timeline/stage-05-library-refresh/Symphony-stage-5-library-refresh-debug.apk`

SHA-256:

`e507faa77214c33ead0560f7bdbd2725a68d6bc668c8ae5883104a97890377c6`

## Stage 6: provider-aware in-app deletion

Stage 6 builds deletion on the accepted Stage 5 snapshot transaction rather than adding a second ad-hoc removal path.

- Future media-folder selection persists read plus write access when granted and falls back to read-only safely. Older folder grants may require reselecting the same folder once.
- `DocumentFileX` captures provider flags and deletion proceeds only when the provider advertises delete support.
- The shared song menu exposes **Delete from device** in library lists and Now Playing. Confirmation shows title and path and explicitly warns that the action is permanent.
- `MediaDeletionService` serializes the provider operation with library refreshes. It deletes the actual document before changing any Symphony reference.
- Permission denial, unsupported providers, missing files, and provider failures return structured results and leave queue, playlists, caches, and library state untouched.
- A successful deletion removes every queue occurrence in original and shuffled orders. Current deletion advances coherently while preserving playing/paused intent; an empty queue stops cleanly.
- Internal playlist paths are removed and persisted before playlist reload. External M3U files and `.lrc` sidecars are intentionally not rewritten or deleted.
- Room song metadata, private artwork, and private lyrics cache are cleaned before one forced Stage 5 refresh reconciles derived indexes.
- Deletion and refresh share one mutex-backed library transaction, preventing a concurrent scan from reintroducing stale state.

Four queue-removal planner tests cover current, final-current, non-current, shuffled, and duplicate-reference behavior, increasing the suite from 35 to 39 tests.

The first Stage 6 packaged attempt passed JVM checks but physical-device smoke testing exposed an Android `VerifyError` in the generated translation serializer after six new localization fields pushed that already-large serializer beyond verifier limits. The phone was immediately restored to accepted Stage 5. The failed APK was overwritten, not archived. Stage 6 retains the deletion UI using local English action/result text until localization storage can be structurally split; it does not expand the unsafe generated serializer.

The corrected Stage 6 artifact was generated by a true clean no-daemon build and passed 39 tests, Android lint, v2 signing, `minSdk 28` / `targetSdk 34`, and Startup inspection. It installed with data preserved, cold-launched the real `MainActivity` in 2.0 seconds, remained alive, and completed a seven-track refresh in 1,407 ms with no verifier, runtime, ExoPlayer, or repository errors.

Archived prior Stage 6 candidate APK (focused deletion acceptance still pending):

`artifacts/timeline/stage-06-in-app-deletion/Symphony-stage-6-in-app-deletion-debug.apk`

SHA-256:

`9d5eece3fa62fc82a8c6f5cc501f5371cf01d5580d71dd80cd5a67c355baafaa`

## Core problem and feature map

Detailed foundational defect descriptions live in `Problems.md`; feature specifications live in `Features.md`; verification matrices live in `tests.md`. The summaries below preserve the agreed reasoning and dependencies.

### Problem 2: void/unseekable region after an absolute seek

Status: implemented and verified on the Motorola G57 Power.

After dragging to an absolute position, repeated relative backward seeks cannot cross the originally selected timestamp. Playback after reopening at a saved non-zero position creates the same boundary. The slider itself remains functional, every tested format and duration is affected, and the gap is defined by absolute positioning rather than by corrupt media.

Leading cause: asynchronous `MediaPlayer.seekTo()` calls are treated as immediately complete. Relative seeks can therefore calculate from stale platform position while an earlier absolute or relative seek is still pending. There is no `OnSeekCompleteListener`, no serialized desired-position state, and no generation guard against late callbacks.

Implemented coherent fix:

- Introduce a seek coordinator with one authoritative desired absolute position stored as `Long` milliseconds.
- Clamp positions to `[0, duration]` before the Android API boundary.
- Serialize platform seeks; if another command arrives during a seek, update the desired target and issue the newest target after completion.
- Use `OnSeekCompleteListener` to publish completion and reconcile actual position.
- Make slider, backward/forward buttons, media controls, and restored-position seeks use the same coordinator.
- Guard callbacks with a player/track generation so a destroyed player cannot update the replacement.
- Test rapid mixed-direction seeks and boundary crossing on multi-hour tracks.

### Problem 3: delayed/silent cold restoration

Status: implemented and verified on the Motorola G57 Power.

After reopening the app with a saved position, pressing Play immediately changes the control to Pause while audio remains silent for a noticeable interval. The restored timestamp also creates Problem 2's unseekable region.

The likely sequence is library fetch, queue restoration, asynchronous player preparation, and an asynchronous restored-position seek with no completion signal. The app can represent the player as playing before preparation/restored seeking is truly ready for audible advancement.

Proposed direction:

- Instrument startup milestones before optimizing.
- Restore cached library and current-song data before waiting for a complete storage scan.
- Retain a pending Play intent issued during restore/preparation/seek.
- Start only after preparation and the restored seek are complete.
- Expose restoring, preparing, seeking, ready, playing, and error states to UI and media session.
- Reuse Problem 2's seek coordinator instead of creating a separate restore-seek path.

### Problem 4: stale previous-track position during track change

Status: implemented and verified on the Motorola G57 Power.

When a new track is selected, its metadata changes before the previous track's position is cleared. The UI briefly combines the new title with the old timestamp and slider position because `RadioObservatory` retains the last playback position until the new player's timer emits.

Implemented fix:

- Publish an initial position immediately when staging a track: zero for a new selection or the saved position for restoration, with the new track's duration.
- Reset playing/readiness state as part of the same logical transition.
- Attach a player/track generation to updates so late callbacks are ignored.
- Prefer one coherent playback snapshot for track ID, position, duration, readiness, and playing state.
- Update Compose, notification, and media session from the same transition model.

Problems 2, 3, and 4 share asynchronous player-state weaknesses. They should use one seek/readiness/generation design even if delivered as separate testable APK stages.

### Problem 5: library changes require destructive manual rescan

Status: implemented and verified on the Motorola G57 Power.

New files are not visible until manual Rescan, and the current manual paths explicitly call `radio.stop()`, destroying the queue and current progress. Calling the existing fetch repeatedly is also unsafe because the live repositories are not designed as an idempotent incremental refresh.

Implemented architecture:

- Scan into a temporary snapshot.
- Diff added, changed, unchanged, and removed records.
- Build derived repositories from the completed snapshot.
- Commit a coherent library state without stopping or recreating the active player.
- Preserve stable file/song identities and the active song record through the commit.
- Trigger a debounced refresh on foreground/resume, optionally assisted by provider/storage change hints.
- Coalesce concurrent refresh requests and retain a manual non-destructive **Rescan now** action.

The exact automatic-refresh interval should be chosen after measuring scan cost on a realistically large library. Foreground/resume refresh plus change hints is the current recommendation.

### Feature 1: in-app deletion

Status: implemented as a verified Stage 6 test candidate; focused phone acceptance pending.

The app reads user-selected Storage Access Framework trees but currently persists only read permission and exposes no deletion operation. Existing folders may require one-time reauthorization for write access, and some document providers may remain read-only.

Implemented direction:

- Request and persist read plus write URI permission when supported.
- Detect missing write access and offer an explicit reauthorization flow.
- Check provider deletion capability and use `DocumentsContract.deleteDocument()`.
- Require a clear **Delete from device** confirmation.
- Delete the actual document first; update the library only after success.
- Feed successful removal into Problem 5's non-destructive library transaction.
- Reconcile queue, internal playlists, artwork, lyrics cache, and derived repositories.
- Define explicit behavior for the currently playing item, likely **Delete and play next**.
- Begin with single-file deletion; defer batch deletion until transaction semantics are proven.

Feature 1 follows and shares Problem 5's library-diff infrastructure. Implementing deletion with a full destructive rescan would have recreated the exact playback-loss problem that Stage 5 removed.

### Feature 2: boundary-free manual queue navigation

Status: implemented as a verified Stage 7 test candidate; focused phone acceptance pending.

The old manual controls treated the first and final queue entries as hard boundaries. An initial Stage 7 candidate recorded automatic final-to-first provenance, but the user selected a simpler and more dynamic policy: manual navigation should be cyclic at all times.

Selected policy:

- Previous at the first entry selects the final entry, however the first entry was reached.
- Next at the final entry selects and starts the first entry.
- The existing three-second rule remains: Previous first restarts a track that has played beyond three seconds, then a subsequent press at its beginning navigates to the final entry.
- Empty and single-entry queues do not create redundant player transitions.
- Repeat Off, Repeat Queue, and Repeat One govern automatic completion only.

Implemented architecture:

- `RadioQueueNavigation` is stateless and computes targets from only the current index and current queue size.
- The prior automatic-wrap state and structural revision machinery were removed entirely.
- Both Previous and Next capability checks and jump commands use the same cyclic planner.
- `RadioShorty.skip()` seeks a single-entry queue to zero instead of reconstructing its player in a paused state.
- Six pure unit tests cover first/final wrapping, middle navigation, two-entry cycles, single-entry behavior, and empty/invalid positions.
- `RadioSession` also explicitly resolves raw Bluetooth Previous/Skip Backward and Next/Skip Forward key events into the same `RadioShorty` commands used by UI and high-level media-session callbacks. It consumes key-up/repeat events to prevent double navigation and leaves Rewind/Fast Forward mapped to relative seeking.

Cold-start diagnosis later exposed a separate lifecycle race rather than a defect in the stateless navigation planner. `Radio.ready()` used to start cached restoration before `RadioSession` subscribed to the non-replaying update stream, and `RadioSession.update()` delayed session activation and playback-state publication until artwork loading completed. A fast restore could therefore be invisible to the session or leave Android with stale transport information during the period when Bluetooth ownership was being selected. Closing Developer Options was not causal; it was one way of forcing fresh process lifecycle behavior.

The cold-hardened implementation now starts the observatory and session before attaching cached restoration, performs an explicit initial snapshot synchronization, and synchronously publishes basic metadata, playback state, actions, and session activation before asynchronous artwork work. Generation checks still prevent stale artwork commits. Destruction now unsubscribes the update listener and releases `MediaSessionCompat`, while cancellation invalidates outstanding work and clears publication state. Five unit tests cover first publication, state-only updates, track changes, session recreation, and empty snapshots.

Physical-device diagnosis of the user's **Boult Audio Airbass** earbuds showed that both an isolated left-ear gesture and an isolated right-ear gesture arrive as the same `KEYCODE_MEDIA_NEXT` event. Symphony was confirmed as Android's active media-button session during both captures. Android supplied `deviceId=-1` and `source=0`, so no side identity remains available to the app. Symphony cannot implement left=Previous and right=Next for this hardware state; the earbuds must first be configured or repaired so their firmware emits distinct transport commands. Do not add timing heuristics or reinterpret all Next commands as Previous, because that would also break the right-ear Next gesture and other controllers.

The user also confirmed that the regular repeat icon stays gray across the final-to-first transition. This is `LoopMode.None`, not Repeat One, whose icon contains `1`. There is no hidden mode mutation: automatic completion only reads `currentLoopMode`. Manual navigation is now cyclic independently of that setting.

The cold-hardened cyclic candidate passed a true clean no-daemon build, all 54 unit tests, Android lint, v2 signing, `minSdk 28` / `targetSdk 34`, checksum verification, and AndroidX Startup class inspection. The exact artifact installed with app data preserved and completed a forced cold launch on the Motorola G57 Power in 1,874 ms. Core media-session state was published while restoration was still in progress, before decoder preparation completed; restored seeking completed in 664 ms. Starting playback made Symphony Android's selected media-button session, and an injected standard media Pause key was routed to and handled by that session. No relevant crash, verifier, or ExoPlayer error occurred. A genuine 7–8 hour device cooldown and Bluetooth gesture run remain manual acceptance checks.

Archived Stage 7 test APK:

`artifacts/timeline/stage-07-cyclic-queue-navigation/Symphony-stage-7-cyclic-queue-navigation-debug.apk`

SHA-256:

`6e58f3f90c27516e0608580b74c8821773fe9f1040992f68281d632564287e5b`

### Feature 3: persistent pinned media controls

Status: implemented as the verified Stage 8 candidate; extended idle acceptance remains pending.

Motorola's **Pin media player** exposed a lifecycle mismatch: Android retained the visual media control after the app task was dismissed, but Symphony had tied `Radio`, its service connection, and `MediaSessionCompat` to `MainActivity`. Dismissing the task therefore left Android displaying stale controls whose callbacks no longer had a live playback owner.

The accepted design makes playback application-scoped:

- `SymphonyApplication` owns the single shared `Radio`.
- `MainActivity` is only a UI client and no longer destroys playback when its task is removed.
- The service declares `stopWithTask=false`, returns `START_STICKY`, and maintains foreground state from player/session state.
- Active or paused media keeps the service and media session alive; an empty/stopped session exits foreground state.
- Notification, lock-screen, hardware, and Bluetooth commands continue through the same `RadioSession` callbacks after task removal.

This is not an immortal-process trick. Android may still kill the process, but sticky service recreation plus the cached queue/session restoration path can rebuild the live controller. The implementation deliberately avoids keeping an empty player resident.

The Stage 8 candidate passed a clean build, 59/59 tests, lint, signature, SDK, checksum, and Startup checks. On the Motorola G57 Power, removing the task left no Symphony activity/task while preserving the same foreground service, process, and media session; external Pause → Play → Pause remained operational.

Archived Stage 8 APK:

`artifacts/timeline/stage-08-persistent-pinned-media-controls/Symphony-stage-8-persistent-pinned-media-controls-debug.apk`

SHA-256:

`c92286cc94904c9c8c816545ebb9c70a014e68e83dc0ca9e80729c4472277016`

### Feature 4: optional long-track position retention

Status: implemented as the verified Stage 9 test candidate; full manual acceptance pending.

The feature deliberately avoids guessing whether a file is music, a podcast, or an audio story. It is off by default and uses a user-selected minimum duration as the only classification rule. The controls live under **Settings → Player → Playback progress**:

- **Remember track positions** opt-in switch.
- Minimum eligible duration from 5 to 180 minutes, in five-minute steps; default 20 minutes.
- **Clear remembered positions**, including the current stored count.
- A conditional **Play from beginning** song-menu action when a restorable checkpoint exists.
- No resume toast/snackbar/message, per the user's explicit preference.

Current-session restoration and optional long-term retention are separate contracts. The explicit cached queue position always wins when restoring the active session. The optional store is consulted when a qualifying track is selected again later. Disabling the feature stops reads and writes without destructively deleting existing entries, so re-enabling it can recover them; the clear action is the explicit destructive operation.

Implementation details:

- `PlaybackProgressPolicy` is pure and testable. Disabled or below-threshold tracks are ignored; positions below 10 seconds are cleared; near-completion positions are cleared using `max(30 seconds, 1% of duration)`.
- Exact positions and durations remain `Long` values, supporting multi-hour tracks without truncation.
- `PlaybackProgressStore` uses its own `playback_progress` preferences file, requiring no schema migration of the existing settings store.
- Each record carries song ID, document path, duration, modification time, file size, and update time. Path/duration/mtime/size must still match before restore, so a replaced file cannot inherit an older file's position.
- `RadioPlaybackProgress` writes at most every 15 seconds during ordinary playback and forces a checkpoint on pause, seek, track transition, and lifecycle persistence.
- Writes use `currentPlayerSongId`, not the queue's currently selected index, so asynchronous queue mutation cannot attribute a position to the wrong file.
- Every checkpoint also refreshes the cached current-session queue position, protecting Stage 8's long-running service from stale restart data even when optional retention is disabled.
- Clear-all suppresses the current song until the player changes, preventing the next periodic tick from immediately recreating what the user just cleared.
- Library reconciliation retains only live IDs, and successful deletion removes the deleted song's checkpoint.
- `Play from beginning` clears the long-term record, starts at explicit zero, and deletes once more after transition because outgoing-player checkpointing is intentionally mandatory.

The clean Stage 9 build passed 69/69 tests, lint, signature, SDK, checksum, and AndroidX Startup checks. On-device, the new settings UI rendered with the expected 20-minute default and no resume message. An 82-minute Opus story wrote a fingerprinted checkpoint near 61 seconds; switching away and returning selected the same track above that checkpoint. The remaining threshold, completion, file-replacement, cleanup, and genuine multi-hour cases are recorded in `tests.md`.

Archived Stage 9 test APK:

`artifacts/timeline/stage-09-track-position-retention/Symphony-stage-9-track-position-retention-debug.apk`

SHA-256:

`d1cfbb047d1acb6a809dd8445a935ccc6c7b2acc420492d9f5a192838e920682`

### Feature 5: long-press multi-track deletion

Status: implemented as the verified Stage 10 test candidate; destructive batch acceptance remains pending.

The feature extends Stage 6 rather than creating a second deletion system. The original song overflow menu still calls `MediaDeletionService.delete(songId)` and retains its existing confirmation and result categories. Standard `SongList` surfaces now add a local, screen-scoped selection mode:

- A long press selects the first underlying song ID.
- Normal taps toggle while selection is active and resume their original play behavior after exit.
- Selected rows use a visible container highlight and checkbox.
- The sort bar temporarily becomes a selection bar with count, close, select-all/deselect-all, and delete controls.
- Back exits selection without navigating away.
- Selection is pruned when the visible repository list changes.
- Duplicate list references intentionally collapse by song ID because deletion targets one physical file.
- Confirmation enumerates every title and path; it is scrollable for large batches.
- Successful IDs are removed from selection. Failed IDs remain selected after a partial operation.

Batch deletion is serialized under the same `Groove.withLibraryTransaction` mutex used by singular deletion and refresh. `deleteMany()` performs per-document provider checks and records each result independently. Physical deletion is always attempted before Symphony mutates a reference. After all attempts finish, only provider-confirmed successes are reconciled in one batch:

- `RadioQueueSongRemovalPlanner.removeAll()` filters original and shuffled queues together and computes one replacement.
- If the current player survives, its identity and adjusted index are preserved.
- If current media is deleted, the first survivor after its former position is preferred; the preceding final survivor is used at the tail.
- If no item survives, the player and queue stop cleanly.
- `Radio.removeDeletedSongs()` preserves playing/paused intent and clears Stage 9 progress before and after player transition.
- Internal playlists remove all successful paths in one update per affected playlist.
- Song, lyrics, and artwork caches are cleared for all successes.
- A single forced library snapshot refresh runs after the batch.
- Unsupported, permission-denied, missing, and provider-failed items do not block supported files and remain available for retry.

This atomic replacement decision matters when the current track and its immediate successor are both selected. Reusing the singular path in a loop would briefly stage the successor even though its file was about to be deleted, create repeated refreshes, and expose stale metadata. Stage 10 instead waits until the complete successful set is known.

Five new queue-planner tests cover current-plus-adjacent deletion, tail fallback, surviving-current identity, empty queue, and empty input. Five selection-policy tests cover add/remove toggles, duplicate collapse, pruning, and empty views. The full suite is now 79 tests.

On the Motorola G57 Power, a non-destructive smoke run selected two real tracks, showed the correct selection count and row states, and presented both exact titles and storage paths. Cancel left the files untouched. The original single-track overflow menu still contained **Delete from device**. No real multi-file deletion was performed automatically because the phone's media files are user data.

Archived Stage 10 test APK:

`artifacts/timeline/stage-10-multi-select-deletion/Symphony-stage-10-multi-select-deletion-debug.apk`

SHA-256:

`0e75e4ac56a0b0b6f29a204d0b51ce7f74c21d306c17e5056340eec118d74844`

## Recommended implementation order

1. Problem 4: atomic/generation-aware track transitions, sharing the readiness model.
2. Problem 5: snapshot/diff/commit library refresh and automatic refresh triggers.
3. Feature 1: deletion transaction and UI, built on the library-diff path.

Each item should still produce its own installable APK and receive phone validation before the next stage is treated as complete.

## Build and verification reference

The project is an Android/Kotlin/Compose app using Java 17, Android Gradle Plugin 8.7.3, Kotlin 2.1.0, Room 2.6.1, AndroidX Media 1.7.0, and Media3 ExoPlayer 1.8.1 as the playback backend.

Environment established for this workspace:

- Android SDK: `C:\Users\baula\AppData\Local\Android\Sdk`
- SDK Platform 35 and Build Tools 34/35 are installed.
- `local.properties` points Gradle to the SDK.
- Node dependencies are installed in the workspace.

Important clean-build prerequisite:

`npm.cmd run i18n:build`

Generated translation Kotlin and asset files are ignored by Git, so a clean checkout/build can fail if this step has not run.

Normal stage verification:

1. Run `npm.cmd run i18n:build` when generated i18n files may be absent or stale.
2. Run `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain --no-daemon`.
3. Run `.\gradlew.bat lintDebug --console=plain --no-daemon`.
4. Verify the archived APK using Android `apksigner`.
5. Inspect package, min SDK, and target SDK with `aapt dump badging`.
6. Compute and save SHA-256 beside the APK.
7. Install and validate the stage on a physical Android 9+ phone.

There were eight npm audit findings in installed development dependencies at setup time. They were pre-existing and were not changed because dependency remediation was outside the playback task. Do not describe them as caused by the Stage 1 changes.

## Artifact layout and retention policy

`artifacts/baseline/`

- Contains only the immutable upstream baseline APK and checksum.
- Never replace or rename the baseline as part of a feature stage.

`artifacts/test/`

- Contains only the latest stable APK offered for phone testing and its checksum.
- At the current snapshot, this contains only the Stage 14 paged-navigation candidate and its checksum.

`artifacts/timeline/`

- Contains previous stage builds after a newer build becomes the latest test build. Any stage still awaiting focused acceptance must remain labeled as a candidate in this memory and in `tests.md`; moving its files does not silently mark it accepted.
- Store each previous stage in a clearly named folder such as `stage-01-audio-focus/`.
- Each stage folder contains that stage's APK and checksum.
- Do not archive failed experiments or superseded intermediate builds as successful timeline stages.

When completing a new stage:

1. Move the existing pair from `test/` into its properly named `timeline/stage-NN-description/` folder.
2. Copy the new verified APK and checksum into `test/`.
3. Confirm `test/` contains exactly one APK and one checksum.
4. Verify the checksum after all moves/copies.
5. Update this memory, the applicable `Problems.md` or `Features.md` entry, `tests.md`, and the stage status after build verification; mark implemented only after phone verification.

## Development invariants

- Preserve unrelated user changes in the worktree.
- Fix causes in the existing architecture rather than layering timing delays over asynchronous races.
- Never make scans destroy playback state.
- Never report playing merely because a Play request exists; distinguish intent, readiness, and actual playback.
- Do not allow callbacks from an old player generation to mutate the new track's state.
- Keep the slider functional and accurate during all seek work.
- Treat audio-focus events by Android semantics, not by application names.
- A user pause/stop always overrides pending automatic behavior.
- Permanent focus loss must not trigger background focus competition.
- Preserve Android 9+ compatibility after every stage.
- A stage is not finished merely because it compiles: it requires focused tests, lint, a signed/verified APK, checksum, tracker updates, and physical-device feedback.

## Stage 11 conceptual record: physical filename rename

Stage 11 adds provider-backed filename changes without treating a path change as a new song. The overflow menu exposes **Rename file**, while the dialog protects the original extension so no rename operation implies format conversion. `MediaFilenamePolicy` rejects blank, reserved, path-bearing, and unchanged names before any mutation.

`MediaRenameService` is serialized by Groove's library mutex and requires the document provider's rename capability. It uses `DocumentsContract.renameDocument()`, then queries the returned URI and reparses the provider's actual display name with the old stable song ID. Internal playlists migrate old path to new path, Stage 9 progress migrates to the new fingerprint, and Room receives the updated cached song before one forced snapshot refresh. Queue entries need no rewriting because they reference stable IDs. External M3U content is intentionally not rewritten.

The clean Stage 11 candidate passed 84 tests, lint, assembly, v2 signature, SDK checks, checksum verification, installation, and cold launch on the Motorola G57 Power. Its SHA-256 is `7e8a0b49a39979b9805fe7c57df47f684ee79abab3e270b19d584e8baee51e8a`. Physical rename acceptance is intentionally left to a user-chosen test file.

## Stage 12 conceptual record: embedded metadata editing

Stage 12 edits the physical file's metadata rather than creating a Symphony-only overlay. The editor covers title, multi-value artist/album-artist/composer/genre tags, album, date/year, track/disc numbering, lyrics, and embedded artwork replacement/removal. Multi-values use semicolons or lines; commas remain part of a name. Blank fields remove only their exposed property, while properties outside the editor are preserved from the original TagLib property map.

The post-Stage-12 UI refinement removes Stage 11's separate **Rename file** entry. **Edit audio details** is now the single entry point and places the extension-protected filename field above all embedded metadata fields. Saving both kinds of changes uses one Groove library transaction, one active-decoder release/restore cycle, and one stable-ID migration. A rename that physically succeeds before a later tag-provider failure is reconciled as partial success rather than hidden or rolled back in app state.

All ordinary textual fields now use wrapping multiline Compose text inputs that expand with their content. This removes the inaccessible horizontally clipped end region that prevented manual caret/selection-handle dragging in long values. Lyrics remains a bounded eight-line editor with its own scrolling because it can be arbitrarily large.

The implementation pins `io.github.kyant0:taglib:1.0.5`. The newer 1.0.6 AAR was evaluated and rejected because it requires compile SDK 37, beyond this project's AGP 8.7.3 / compile SDK 35 baseline. The 1.0.5 AAR passes the existing metadata gate and provides file-descriptor metadata and artwork reads/writes without changing Symphony's `minSdk 28`.

The writer is absent from all scan and playback hot paths. It opens the persisted SAF document only after explicit Save, uses a seekable `rw` descriptor directly, and therefore avoids copying an entire long podcast merely to update a small tag block. A selected image is validated and capped at 20 MB. Provider capability, permission, missing-file, unsupported-format, and native failure states remain explicit.

Editing a current or gaplessly preloaded item first releases the corresponding decoder. The active item captures index, exact `Long` position, and playing/pending intent; after write, progress-fingerprint migration, Room update, and targeted repository publication, playback is rebuilt at the same position and intent. This avoids mutating a file beneath an open ExoPlayer descriptor.

The save-latency refinement closes the editor synchronously before launching the physical write in Groove's application coroutine scope, so dialog removal is not coupled to provider or tag-writer latency and composition disposal cannot cancel the save. Once the write succeeds, Symphony constructs the new immutable `Song` from the values it just committed, updates Room, lyrics/artwork caches, folder URI mapping, all derived repositories, and the media session directly. Song lists now observe the repository revision as well as their unchanged stable ID list, which fixes the stale-until-relaunch display. A future ordinary scan verifies the persisted fingerprint; no immediate whole-library scan or full audio reparse is needed.

The simplified editor now treats filename as Symphony's only title control: the Title input is absent, every save removes the embedded `TITLE` tag, and the extension-free filename is published as the immutable `Song.title`. Parsers and `SongRepository` also normalize untouched, cached, and cold-restored songs, making filename-as-title a global invariant rather than an edit-only side effect. The menu and dialog label are **Edit details**. Edit, singular-delete, and batch-delete result toasts are intentionally absent.

Storage mutations use a bounded verifier rather than an unbounded worker: write metadata, reread the exposed properties/artwork, or delete and query document absence; retry transient failure/mismatch after 120 ms and 350 ms. Permission denial, conflicts, missing input, and unsupported capabilities are permanent and are not retried because no algorithm can resolve them without changed external authority. Deletion reconciliation is now a targeted cache/repository/folder update and does not run a full library scan.

Three retry-controller regression tests cover immediate success, transient recovery on the final bounded attempt, and permanent-result termination without retry.

The latest Stage 12 candidate passed 93/93 tests, lint, assembly, v2 signing, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum, and AndroidX Startup verification. The exact APK installed with data preserved and cold-launched in 2.035 seconds on the Motorola G57 Power, remained alive, and emitted no relevant startup error. Current test APK SHA-256: `9eb08eab42877c9de66daad729d97753826d94d1e2e77bac4dc3c330e2304de4`.

## Stage 13 conceptual record: minimal bottom navigation

Stage 13 begins the UI/UX phase without changing navigation semantics. The default Material 3 selected-item pill is made transparent. Each icon occupies a fixed 32 dp container; the inactive outlined icon is 24 dp and the active filled icon is 28 dp. Primary-color, size, and filled/outlined transitions use the same 160 ms timing, so selection changes remain subtle and spatially stable.

Label visibility remains controlled by the existing three-mode setting. In visible modes, the selected label also uses primary color and semibold weight. In invisible mode, no label is composed and the active icon's color, size, and filled form carry selection independently.

The Stage 12 final functional APK was archived under `artifacts/timeline/stage-12-final-functional/`. The Stage 13 candidate passed 93/93 tests, lint, assembly, signature, SDK, alignment, checksum, and Startup checks. It installed with settings preserved and cold-launched in 1.922 seconds. Real-device inspection matched the approved label-free mockup, and an Albums-to-Songs navigation transition completed with the process alive. Its archived APK SHA-256 is `90c9d6442c78cc1037693905c6105bf26e8b38967f185ff003d6d7a6cd7180a9`.

## Stage 14 conceptual record: two-page bottom navigation

Stage 14 replaces the vertically expanded ten-destination sheet with two clipped horizontal pages of five. It intentionally extends rather than replaces Stage 13's visual language: the selected item still has no Material pill, occupies the same fixed 32 dp icon container, and transitions between 24 dp outlined/muted and 28 dp filled/primary states over 160 ms. A four-dp two-dot indicator is the only persistent page affordance when transition buttons are disabled.

The existing `home_tabs` preference remains page one's migration source. `homeNavigationPages()` preserves its iteration order, keeps every existing selection, completes legacy two-to-four-item configurations using the approved defaults and remaining enum entries, caps page one at five, and derives page two as the exact complement. Both settings editors require five selections. Editing page two stores its complement back into page one, making duplicates, omissions, and unreachable destinations structurally impossible without introducing a second conflicting source of truth.

The navigation bar owns the gesture. `detectDragGesturesAfterLongPress` delays interception until Android recognizes a hold, emits the platform long-press haptic, and offsets toward the available page by 6 dp. Subsequent horizontal deltas directly offset two independently sized page rows inside a clipped bar. Movement beyond 18% of the measured width commits to the adjacent page; otherwise it returns. Both paths use a 220 ms snap. Edge resistance limits movement beyond the first or last page to 8% of the width. There is no vertical expansion, upward swipe action, active-tab sheet, or cyclic page wrap.

The optional edge controls are an additive accessibility path. Their glyphs are visually slim, but each uses a 48 dp `IconButton`; page content receives 40 dp horizontal padding while they are visible. Page one enables only right and page two only left. The toggle defaults off, persists immediately, and does not disable long-press dragging. Settings also carries an explicit gesture explanation.

`lastHomeNavigationPage` stores the visible page. If the active Home destination changes through another route, a keyed effect reveals its containing page and updates the stored value. The mini-player is outside the translated strip and never participates in the page animation.

Three unit tests establish the default approved split, legacy short-configuration completion, and a complete duplicate-free partition of all ten destinations. The final clean candidate passed 96/96 tests, lint, assembly, v2 signing, SDK 28/34 checks, 16 KB alignment, checksum verification, and explicit Dex confirmation of AndroidX Startup's resource and initializer classes. The first incremental APK was rejected after a device `VerifyError` in the already-large generated translation serializer; it was never archived. Stage 14's small amount of new UI copy therefore uses local strings instead of expanding that serializer.

Physical smoke testing on the Motorola G57 Power confirmed a 2.07-second clean cold launch, a live process with no relevant errors, bidirectional hold-and-drag navigation, complete five-item pages, stationary mini-player behavior, and bidirectional non-wrapping edge controls. The gesture explanation's final 56 dp start padding produces the same measured `x=109` left edge as the adjacent Settings row text. Page-assignment mutation, legacy migration, Android 9, and TalkBack remain focused manual regressions. Current test APK SHA-256: `5a2665bd0d2caea7bc43d42f7e406f5cdde36dd795fd74aca34d83a86e74fa95`.

## Stage 15 conceptual record: unified modal surfaces

Stage 15 replaces Symphony's mixture of floating Material dropdowns, centered dialogs and independently styled sheets with one app-owned modal language. `SymphonyModalSheet` owns the opaque `surfaceContainer`, 28 dp rounded top corners, standard drag handle, zero decorative tonal elevation and explicit system/IME inset strategy. `SymphonyMenu` adds an adaptive screen-relative maximum height and internal scrolling for context and choice content. Existing `ScaffoldDialog` callers now render through this foundation, preserving their domain logic while acquiring the same placement, shape, animation and action treatment.

The Home overflow is intentionally removed because it contained only two stable actions. Adjacent standard `IconButton`s expose Rescan and Settings directly without changing Groove scan behavior or navigation. Because one Search button on the leading side and two action buttons on the trailing side make Material's constrained title appear off-center, both app-bar sides reserve 96 dp. The Rescan and Settings glyphs shift inward by 4 dp inside their unchanged 48 dp targets, producing 40 dp visual center spacing without overlapping hit areas. On the 1080 px Motorola display, UI hierarchy bounds measured the title center at `x=539.5` versus the physical `x=540` center and the two action glyph centers 78 px/39 dp apart. Every remaining standard `DropdownMenu` call is migrated to `SymphonyMenu`; the pre-existing Now Playing extra-options sheet is also routed through `SymphonyModalSheet`. Android-owned permission, MediaStore authorization and picker interfaces remain outside this system.

Track actions receive the richer grouped variant approved in the Stage 15 mockup. The sheet reuses Coil's existing artwork request/cache, shows the track identity once, places Favorite, Play next, Add to queue and Add to playlist in a four-action row, retains the conditional `RestartAlt` Play-from-beginning action, groups navigation and file/detail work, and isolates deletion. No new media query or artwork decoder is introduced when opening the sheet. Other entities retain their conditional action logic inside titled, scrollable sheets.

All app-owned dialogs already converged through `ScaffoldDialog`, so changing that shared surface migrates information, confirmation, playlist, folder, settings choice/input, responsive-grid, playback speed, pitch and sleep-timer workflows without duplicating layout code. Singular and batch device deletion use the same sheet scaffold with a **Delete permanently** action. Their provider-aware bounded verification and retry algorithms are untouched. System confirmation may still follow when Android requires storage authorization.

The metadata editor is the expanded form variant. Its sheet uses the maximum safe scaffold height, keeps actions outside the independently scrolling form, and combines the manifest's `adjustResize` behavior with IME/navigation-bar padding. Every focusable metadata field has a `BringIntoViewRequester`; after the keyboard settles, the scroll container brings the focused field fully into view. Single-line text and numeric fields use keyboard Next to advance focus, while Lyrics remains multiline. Save still dismisses synchronously before the verified storage transaction continues in Groove's application scope.

The Add-to-playlist → New playlist flow no longer composes two simultaneous modal sheets. It swaps the parent sheet for the child, passes the pending song IDs into the new-playlist draft and dismisses the workflow after creation. Existing playlist cards in the add picker no longer expose nested overflow actions.

Physical intermediate testing on the Motorola G57 Power confirmed direct app-bar actions, the full grouped track sheet, the existing RestartAlt symbol, adaptive sorting, deletion confirmation without execution, expanded metadata layout, a genuinely visible IME, a fully visible focused field, Cancel/Save above the keyboard, cursor selection handles and keyboard Next advancing to Artists.

The true clean Stage 15 candidate passed all 96 unit tests, Android lint and APK assembly. The exact packaged artifact passed v2 signature verification, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum verification and explicit Dex confirmation of `androidx.startup.R$string`, `AppInitializer` and `InitializationProvider`. It installed and cold-launched on the Motorola G57 Power in 2.189 seconds with no relevant runtime error. Final packaged-build hierarchy bounds placed **Songs** at `[492,154][587,201]`, whose midpoint is `x=539.5`, the nearest possible half-pixel midpoint to the 1080 px screen center; Rescan and Settings glyph bounds were `[916,154][963,201]` and `[994,154][1041,201]`, retaining compact 39 dp visual spacing. The current APK SHA-256 is `2d53eb6f7aa800407c1a61ebf079ce9fab487d5def2e6efa7dc89fb65e52f7d7`.

## Stage 16 conceptual record: minimal multi-track selection

Stage 16 modernizes the Stage 10 multi-selection presentation without changing `SongSelection`, selected-ID ownership, batch deletion, or retry/reconciliation logic. `SongCard` no longer uses a filled Material Card and Checkbox during selection. Its flat outer Box draws an animated 8% primary tint and 3 dp leading accent directly, leaving no rounded selection boundary. Artwork is never covered; only unselected artwork receives a restrained 82% opacity while selection mode is active.

The trailing 48 dp region remains spatially identical to the former checkbox target. A 24 dp Canvas draws a 1.5 dp hollow ring for every track in selection mode. Selected state animates the ring to the primary colour and scales in a compact 4 dp centre dot over 160 ms, retaining a visible gap and deliberately containing no checkmark. The row carries Compose selected semantics, and tapping anywhere on it continues to toggle selection.

The contextual toolbar keeps Symphony's original `Icons.Filled.SelectAll` rather than introducing a new glyph. Its count becomes semibold, Delete remains the only error-colour action, and a subtle 0.5 dp divider replaces any toolbar container. `AnimatedContent` crossfades and shifts the sort/selection surfaces by one-fifth of their height over 120–160 ms.

The true clean build passed 96/96 tests, lint and assembly. The exact Stage 16 artifact passed v2 signature, SDK 28/34, 16 KB alignment, checksum and AndroidX Startup inspection, then cold-launched on the Motorola G57 Power in 2.168 seconds without a relevant runtime error. Real-device checks passed long-press entry, three-item selection, the ring/dot states, 7/7 Select all with Deselect all semantics, and Back exit; deletion was intentionally not invoked. Current test APK SHA-256: `369b99fdcd50fee23ff01a80ec4fe79d5d8b87502284597d9c71adfb4366aedf`.

## Stage 17 conceptual record: playback preferences and modern artwork

Repeat mode is a durable user preference, not transient queue state. `Settings.lastLoopMode` uses a synchronous committed write because a mode change can be followed immediately by process termination. `RadioQueue` initializes from it and writes only genuine changes; `RadioObservatory` emits the restored mode before its initial snapshot. Queue clearing and reconstruction therefore cannot reset it. Mode-specific content descriptions keep the restored state observable to accessibility services and device UI tests.

Embedded artwork previously recognized only JPEG, PNG, and GIF. `AudioArtwork.Format` now normalizes case, MIME parameters, filename-like aliases, and common provider variants for JPEG, PNG, GIF, BMP, WebP, HEIF, and HEIC. The editor rejects an explicit non-image provider MIME and otherwise lets `BitmapFactory` identify the decoded image. WebM remains unsupported because it is a media container, not a still cover-image format; extracting a frame would add a new video path for no benefit to ordinary embedded artwork.

Audio focus is always requested as a player invariant. The former **Require audio focus** and **Ignore audio focus** switches permitted a confusing four-state matrix, including contradictory combinations. They are replaced by `AudioInterruptionBehavior`: **Pause and resume automatically** or **Keep playing**. The first preserves Stage 1's focus-loss pause/resume contract and remains the default. The second ignores focus-loss callbacks and warns that audio can overlap. Existing installations migrate the legacy ignore-loss value to the matching enum; the obsolete require-focus value is intentionally not carried forward.

Artists and Album artists remain distinct destinations by explicit user decision.

The Stage 17 clean build passed 101/101 tests, lint and assembly. The exact artifact passed v2 signature, SDK 28/34, 16 KB alignment, checksum equality, and AndroidX Startup Dex inspection. A physical process-death test preserved Repeat queue. The consolidated settings surface was inspected on the Motorola G57 Power, and the exact packaged build installed and cold-launched in 3.017 seconds with its process alive, saved long-track state restored, and no fatal runtime error. Current test APK SHA-256: `f330a0863afc1bce4e6c50e3bbdaeb9a6adbc91a9fd03ebe191f771a8bf490b2`.

## Stage 18 conceptual record: compact navigation and modern queue

The Home navigation bar's previous fixed 80 dp content height was excessive at the default 1.0 font scale, especially because Material's internal item layout left roughly 9 dp between the visible icon and label. Stage 18 uses 64 dp whenever a label line can appear and 56 dp in label-free mode. Labels are rendered inside the icon slot as a deterministic 32 dp icon container, 2 dp gap, and reserved 16 dp text line. This bypasses Material's loose positioning while preserving `NavigationBarItem` selection, ripple, semantics, and full-width touch target. Active-only mode reserves an empty label line for inactive items, preventing vertical icon movement. The two-page gesture, dots, transition buttons, page persistence, 24/28 dp icon animation, and Android navigation inset remain unchanged.

Queue is intentionally retained as a full route rather than converted to a modal sheet. Its rows can open Stage 15 grouped track sheets; making the queue itself a sheet would stack modal layers and recreate the workflow problem Stage 15 removed. Modernization therefore targets the legacy visual and interaction model: permanent checkboxes become Stage 16 long-press selection, numbered artwork badges become a quiet leading index, and the active item receives a waveform marker, 6% primary tint, and 3 dp leading accent. Played rows use alpha rather than a blocking background mask.

The normal toolbar uses Playlist add for saving the whole queue and error-coloured Clear queue. Selection replaces it with Exit, Add selected to playlist, Select/Deselect all, and error-coloured Remove. Selected queue indices are sorted before mapping to IDs, preserving queue order. `AddToPlaylistDialog` now has an optional success callback: cancellation leaves selection intact, while an existing-playlist update or New playlist creation clears it only after the operation is committed. Existing callers retain their prior behavior through the default callback.

The Stage 18 clean build passed 101/101 tests, lint and assembly. The exact artifact passed v2 signature, SDK 28/34, 16 KB alignment, checksum equality, and AndroidX Startup Dex inspection. It installed with app data preserved and cold-launched in 2.195 seconds on the Motorola G57 Power with a live process and no fatal runtime error. Exact-build physical checks passed compact labelled navigation, queue normal/selection states, Select all, selection exit, and the Add selected picker with existing and New playlist paths. Destructive actions and an actual playlist mutation were intentionally not executed. Current test APK SHA-256: `20f3e45f63bfb9d1d38002e49137c9fde6d6912301fee330cceabf4e5a39f70c`.

## Stage 19 conceptual record: complete legacy-UI convergence

Stage 15 standardized containers, but several workflows retained their old inner
visual language. Stage 19 closes that gap through reusable flat choice, switch,
rounded preset, and ring-and-dot selection components. Settings choices, playback
presets, playlist selectors, onboarding, folder lists, and custom-value chips now
share one lightly tinted, artwork-safe presentation.

Separate-page lyrics is no longer a disguised full-screen navigation route. Now
Playing owns an expanded 90%-height modal sheet, and the old serialized destination
and transition branches are removed. The sheet retains live lyrics, screen-awake
behavior, seeking, and traditional playback controls.

Playlist song management reuses the selection language from Stages 16 and 18.
Add-to-playlist membership moves to the trailing edge rather than covering artwork.
Speed, pitch, and sleep timer use explicit active preset chips and switch rows.
Settings option cards and checkboxes are flattened without changing validation,
commit, cancellation, or ordering behavior.

`UiMessageBus` uses a bounded channel so feedback emitted before the Compose host
starts collecting is retained. `BaseView` owns the snackbar host; update, clipboard,
import/export, share, equalizer, and artwork messages use it. No Android Toast calls
remain. Verified deletion and metadata-save operations remain intentionally silent.

The Stage 19 candidate passed 101/101 unit tests, Android lint and APK assembly. The
exact artifact passed v2 signing, SDK 28/34, page-aware ZIP alignment and checksum
inspection, installed with data preserved, launched on the Motorola G57 Power, kept
its process alive, and emitted no AndroidRuntime crash. Destructive workflows were
not invoked. Current candidate SHA-256:
`2684467a124da7e14b3c61a837f8ec064a523e84be953ce1f40ba9e9eee4928e`.
