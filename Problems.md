# Symphony core problem plan

This document tracks the problems discussed for this fork, their agreed solutions, and their implementation status. New problems will be added after they have been analyzed and discussed.

## Usage profile and design constraints

The fixes in this plan must support the following real-world usage rather than assume a conventional short-song library:

- The library contains music, podcasts, and audio stories.
- Track duration ranges from approximately 2â€“3 minutes to 6â€“7 hours.
- The library changes almost daily as podcasts and stories are downloaded and deleted after listening.
- Seeking, duration arithmetic, persisted positions, and media-session positions must remain `Long` millisecond values end-to-end. UI ratios may use floating-point values only for display and must convert back without introducing meaningful errors on multi-hour tracks.
- Playback progress for long-form content must be checkpointed safely enough that a process kill, crash, reboot, rescan, or library update does not lose a substantial listening position.
- Library synchronization must be optimized for frequent small changes and must not treat each change as a reason to destroy and rebuild the playback session.
- File deletion is a primary library workflow, not an exceptional maintenance operation.

## Status legend

- **Discussing**: Requirements or tradeoffs are still being decided.
- **Planned**: The problem and proposed solution have been agreed upon.
- **In progress**: Implementation has started.
- **Ready for device testing**: Implementation, unit tests, lint, and APK build have passed; phone validation is pending.
- **Implemented**: The change has been completed and verified.

## Problem 1: Playback does not resume after an audio interruption

**Status:** Implemented

### Current behavior

When Symphony is playing music and any other application or system audio source takes audio focus, Symphony pauses. After the interruption ends, playback frequently remains paused until the user returns to Symphony and presses Play. The problem is not tied to particular applications; it can occur with any source capable of taking Android audio focus.

### Root cause

`RadioFocus` records that playback should be restored when audio focus returns and then calls `Radio.pause()`. The normal pause path abandons Symphony's audio-focus request. As a result, Symphony may not receive the later `AUDIOFOCUS_GAIN` callback needed to resume playback.

The current implementation also handles permanent (`AUDIOFOCUS_LOSS`) and temporary (`AUDIOFOCUS_LOSS_TRANSIENT`) focus loss identically, even though Android assigns them different semantics.

Relevant code:

- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioFocus.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/Radio.kt`
- `app/src/main/java/io/github/zyrouge/symphony/ui/view/settings/PlayerSettingsView.kt`

### Desired behavior

- If playback was active before a temporary interruption, pause it and resume automatically when audio focus returns.
- Apply the behavior consistently regardless of which application or system component caused the interruption.
- If playback was already paused, do not start it after an interruption.
- If the user manually pauses during an interruption, cancel the pending automatic resume.
- For ducking events, lower the volume and restore it when focus returns.
- Treat permanent focus loss separately and avoid competing with the other application for focus.

### Proposed solution

1. Add a dedicated interruption-pause path that pauses the player without abandoning the audio-focus request.
2. Keep the existing user-pause path responsible for abandoning focus.
3. Track whether playback was active immediately before the focus interruption.
4. Handle focus events separately:
   - `AUDIOFOCUS_LOSS_TRANSIENT`: pause without abandoning focus and mark playback for automatic resumption.
   - `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`: reduce volume and restore it on focus gain.
   - `AUDIOFOCUS_GAIN`: resume or restore volume only when a corresponding interruption is pending.
   - `AUDIOFOCUS_LOSS`: pause, clear pending automatic resumption, and abandon focus.
5. Ensure an explicit user pause or stop clears any pending automatic resume.
6. Consider replacing the existing **Ignore audio focus loss** switch with a clearer interruption-behavior setting after reviewing the desired UX:
   - Pause and resume for temporary interruptions
   - Duck
   - Ignore focus loss

### App-agnostic handling and Android limitation

The implementation must react to Android audio-focus events and must not contain application-specific checks. Different audio-producing applications can request different types of focus. Android does not guarantee that the previous player receives a focus-gain callback after permanent loss. Symphony should not repeatedly request focus in the background because that could restart music over another source that is still playing. The proposed change guarantees correct behavior whenever Android reports a temporary interruption and later returns focus, while retaining standards-compliant behavior for permanent focus loss.

### Verification

- Temporary loss while playing pauses and subsequently resumes playback.
- Temporary interruption behavior is identical across multiple unrelated audio-producing applications.
- Temporary loss while already paused does not start playback.
- Manual pause during an interruption prevents automatic resumption.
- Ducking reduces volume and focus gain restores it without restarting the song.
- Permanent loss pauses playback and does not reclaim focus automatically.
- Repeated loss/gain events do not cause duplicate starts or stale resume state.
- Stop, queue replacement, and player destruction clear pending resumption state.
## Problem 2: Relative seek returns to the prior slider position

**Status:** Implemented and verified on the Motorola G57 Power

### Current behavior

After starting a song and jumping to an absolute timestamp with the progress slider, the selected timestamp appears to become a persistent lower boundary specifically for the backward-seek control. The song can continue playing for several minutes and can be rewound within the portion played after the jump, but repeated backward-seek commands stop at the original slider-selected timestamp. The progress slider remains fully functional and can seek into the earlier region at any time.

The problem occurs with every tested track, format, and track duration.

The same boundary is created when Symphony restores a saved non-zero playback position after reopening the app. In other words, the trigger is not specifically the progress slider; it is any absolute positioning of the player before subsequent relative backward seeks.

The same underlying problem may affect rapid consecutive seeks from the now-playing screen, mini-player, media notification, headset controls, or another media controller.

### Root cause

The exact state defect remains under investigation. The persistent lower boundary means this is not explained solely by pressing backward before the slider seek finishes. Because the absolute progress slider can always enter and play the earlier region, the media file, decoder, content URI, and platform's basic ability to seek through the track are not the primary limitation.

Android `MediaPlayer.seekTo()` is asynchronous. Symphony currently calls `seekTo()` and immediately reads and publishes `MediaPlayer.currentPosition`, without registering an `OnSeekCompleteListener` or tracking the requested target. That position may still represent the state before the seek completed. This is a confirmed implementation weakness, but further reproduction is required to determine whether it fully explains the inaccessible pre-jump region.

Relative seeking calculates its target through a separate path in `RadioShorty`, based on an instantaneous `MediaPlayer.currentPosition` read. Absolute slider seeking and restored-position seeking call `Radio.seek()` directly. Symphony does not record requested targets, completed targets, or resolved positions, so it cannot reconcile the paths or expose why the relative target stops advancing below the earlier absolute target.

Android keeps at most one active seek and only the latest additional seek request while a seek is in progress, so consecutive commands require explicit coordination.

There is also a separate settings defect: `seekBackDuration` and `seekForwardDuration` use the same `SharedPreferences` key (`seek_back_duration`). This can cause the configured forward and backward intervals to overwrite each other.

Relevant code:

- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioPlayer.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/Radio.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioShorty.kt`
- `app/src/main/java/io/github/zyrouge/symphony/ui/view/nowPlaying/BodyContent.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/Settings.kt`

### Desired behavior

- An absolute slider seek should reliably establish the new playback position.
- Restoring a saved non-zero position must not establish a lower boundary for later relative seeking.
- A subsequent backward or forward seek should be relative to the latest requested position, even if the previous seek has not completed yet.
- Rapid consecutive seek commands should resolve to the user's latest intended position.
- The progress bar and elapsed-time label should represent the requested position while seeking and the confirmed player position after completion.
- All seek entry points should share the same behavior.
- Forward and backward seek intervals should be stored independently.

### Implemented solution

The first implementation added correct asynchronous seek coordination around platform `MediaPlayer`, but device testing proved that Android itself resolved an earlier target back to the previous seek point. Both the backward control and slider flashed the requested position and then received the same old confirmed position. This ruled out the UI and target calculation as the remaining cause.

The revised experiment replaced only the low-level `RadioPlayer` engine with Jetpack Media3 ExoPlayer while preserving Symphony's queue, audio focus, media session, notification, observatory, and Compose infrastructure.

1. Uses Media3 `ExoPlayer` 1.8.1 with a `ProgressiveMediaSource` and bundled extractors rather than platform `MediaPlayer` seeking.
2. Enables constant-bitrate approximate seeking for extractors that support it, improving MP3 files without complete seek tables.
3. Retains the thread-safe `RadioSeekState` coordinator with one active seek and one latest pending target.
4. Uses Media3 position-discontinuity callbacks to settle seeks and publish confirmed positions.
5. While seeking, `RadioPlayer.playbackPosition` reports the coordinator's newest requested target, so relative controls calculate from user intent rather than stale decoder state.
6. Keeps seek arithmetic as `Long` milliseconds and clamps every target only to `0..duration`.
7. Moves player creation, restoration, playback callbacks, and position polling onto the main player looper; cached snapshots remain safe for background consumers.
8. Resets active and pending seek state when a player is destroyed and ignores unusable-player callbacks.
9. Keeps Symphony's own Stage 1 audio-focus policy by disabling ExoPlayer's internal focus management.
10. Preserves volume fading, ducking, speed, pitch, gapless preloading, audio-session ID exposure, completion, and error callbacks through the existing `RadioPlayer` interface.
11. Splits forward and backward interval persistence into `seek_forward_duration` and `seek_back_duration`, with one-time migration from the prior shared value.
12. Retains unit coverage for clamping, latest-request coalescing, redundant targets, boundary crossing, long restoration arithmetic, destroyed-player reset, and independent preference keys.

### Verification

**Device result for the first Stage 2 build:** Unsuccessful. The original lower-bound behavior persisted. The requested earlier timestamp appeared for a split second and then returned to the prior boundary. A slider request to the same nearby earlier timestamp behaved identically. This proved the target reached the playback layer but platform `MediaPlayer` resolved it back to its previous seek point.

**Device result for the Media3 build:** Failed before seek testing because the app closed during startup after installation. A later ADB trace from the Motorola G57 Power showed `NoClassDefFoundError: androidx.startup.R$string` in `androidx.startup.AppInitializer`, before Symphony's activity launched. Comparing the failed APK with the verified Stage 1 APK showed that the failed package retained the `androidx_startup` resource and referenced the generated class but omitted the class definition itself. This was an incomplete/stale APK packaging failure, not evidence that Media3 failed at runtime. The Media3 APK remains withdrawn, its source remains reverted, and a future retry must start from a clean build and verify the generated Startup class before installation.

**Rebuilt Media3 result:** Stage 2 was restarted from the verified Stage 1 source with Media3 ExoPlayer 1.8.1, a single-main-looper player wrapper, cached playback snapshots, serialized seek coordination, bundled progressive extractors, and constant-bitrate approximate seeking. AndroidX Startup 1.1.1 is now a direct dependency. A clean build passed 17 unit tests and Android lint; the final APK was v2-signed, retained `minSdk 28`, and was inspected with `dexdump` to confirm a real public `androidx.startup.R$string` class before installation. It installed with data preserved and remained alive on the Motorola G57 Power with no AndroidRuntime or ExoPlayer startup error. The user then verified that backward controls and direct slider seeking cross the former absolute/restored-position boundary without snapping back. Stage 2 is archived in `artifacts/timeline/stage-02-media3-seek/`.

**Current source state:** Media3 ExoPlayer remains the playback backend behind Symphony's `RadioPlayer` adapter. The seek coordinator, main-looper confinement, cached snapshots, independent preference keys, and AndroidX Startup packaging verification remain active.

- Seek forward with the slider, play for several minutes, and repeatedly seek backward across the original slider target into the previously skipped region.
- Restore a saved non-zero position after a cold start, then seek backward across the restored timestamp into the earlier region.
- Seek with the slider and immediately seek backward before the first seek completes.
- Seek with the slider, allow it to complete, and then seek backward.
- Repeat the two cases with forward seeking.
- Issue several rapid alternating backward and forward commands; the last intended target wins.
- Verify seeking near the beginning and end clamps correctly.
- Verify behavior while playing and paused.
- Verify slider, full-player controls, mini-player controls, notification/media-session controls, and lyrics seeking.
- Verify backward and forward interval settings persist independently after restarting the app.
- Test multiple supported formats, including MP3, M4A, FLAC, OGG, and Opus where available, while expecting identical behavior across formats.
- Include 6â€“7 hour tracks and verify position calculations across the full duration.

## Problem 3: Delayed playback after restoring a previous session

**Status:** Implemented and verified on the Motorola G57 Power

**Dependency:** Built on the verified Stage 2 Media3 seek coordinator.

### Current behavior

When a track is stopped, the app is closed, and Symphony is opened again several hours later, the previous track and its position are preserved correctly. Pressing Play does not produce audio immediately; playback takes a noticeable amount of time to start.

After playback resumes from the preserved non-zero timestamp, relative backward seeking cannot cross that restored timestamp. The earlier portion behaves like the inaccessible region described in Problem 2, although the progress slider remains the absolute-seek escape path.

When Play is pressed, the UI immediately changes from Play to Pause while audio remains silent. This confirms that the command reaches `MediaPlayer.start()` and the player reports a playing state; the delay occurs after start has been accepted and before audible decoded output begins.

The longer delay after several hours suggests that Android has terminated Symphony's process and the app is performing a cold process start rather than merely recreating the activity.

### Likely causes

This is probably a combination of several startup operations rather than a single generic cold-start delay:

1. Symphony starts a complete configured-folder scan before declaring `Groove` ready.
2. `Radio` waits for `Groove.readyDeferred` before restoring the saved queue.
3. Queue restoration creates a new `MediaPlayer`, prepares it asynchronously, and seeks asynchronously to the saved timestamp.
4. The restored-position seek has no completion listener. The player is considered usable as soon as preparation completes, even if its asynchronous seek has not completed.
5. Pressing Play can therefore call `MediaPlayer.start()` and make `isPlaying` true while the restored seek and decoder positioning are still unresolved, producing a misleading Pause icon during silence.
6. Symphony does not expose distinct restoring, preparing, seeking, buffering, and audibly advancing states.
7. Problem 2's incomplete asynchronous-seek handling creates the same relative-seek boundary when restoring a non-zero timestamp and is now the leading explanation for the silent startup interval.
8. `RadioShorty.playPause()` can still discard Play when no prepared player exists, but the observed immediate Pause icon shows that this is not the primary cause in the reported reproduction.

Relevant code:

- `app/src/main/java/io/github/zyrouge/symphony/services/groove/Groove.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/groove/MediaExposer.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/Radio.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioPlayer.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioQueue.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioShorty.kt`

### Desired behavior

- A restored track should begin playing promptly after the user presses Play, including after Android has killed the previous process.
- A Play command issued while restoration or player preparation is underway should be retained and executed when playback becomes ready.
- Restoring the current track should not unnecessarily wait for a complete scan of the entire music library.
- The UI should distinguish ready, restoring, preparing, seeking, and error states.
- Queue, track, and position restoration must remain accurate.

### Proposed investigation

Instrument elapsed time for the following cold-start checkpoints:

1. `MainActivity.onCreate()`
2. Room song-cache availability
3. Filesystem scan start and completion
4. Previous queue decoded
5. Current saved song resolved
6. `MediaPlayer` creation and preparation completion
7. Restored-position seek request and completion
8. User Play command
9. `MediaPlayer.start()`
10. First confirmed playback-position advancement

Measurements should be taken with small and large libraries, at position zero and non-zero restored positions, and for both warm and cold process starts.

### Proposed solution

The final changes should be selected from measurements, but the expected design is:

1. Restore cached library records and the saved current song from Room before starting the full filesystem refresh.
2. Prepare the restored current song as soon as its cached record is available; let the library scan continue in the background.
3. Add a pending-play intent. If Play is pressed during restoration, preparation, or the restored-position seek, automatically start when the player becomes ready unless the user subsequently cancels it.
4. Do not report the player as actively playing or call `start()` until the restored-position seek has completed. If the user presses Play earlier, retain that intent and start immediately after `OnSeekComplete`.
5. Introduce an observable playback readiness state for Compose and media-session controls.
6. Show a restoring/seeking indication instead of changing to Pause while output is still silent, and disable duplicate commands without discarding the user's intended Play action.
7. Reuse the seek coordinator from Problem 2 so restored-position seeking has a completion signal.
8. Avoid large architectural changes until timing data identifies the dominant delay.

### Implemented Stage 3 solution

1. Added a synchronized `RadioPlaybackState` with explicit Idle, Restoring, Preparing, Seeking, Ready, Playing, and Error readiness values plus a separately tracked pending-Play intent.
2. A Play command during restoration, preparation, or restored seeking is retained. It starts exactly once after the player is prepared and the restored seek settles; a second Play/Pause command, explicit Pause, Stop, focus policy, or error cancels it.
3. `Radio` now gates restored playback on Media3 seek completion and protects every callback with the current player generation.
4. Compose, the mini-player, media session, and notification consume the same state. A pending start shows a cancellable progress indicator instead of a misleading Pause icon; the media session reports Buffering until actual playback begins.
5. The intended restored timestamp and duration remain authoritative during preparation, including if Android pauses the activity and saves state before the decoder becomes ready.
6. Added a Room query that loads only records referenced by the saved queue before the full storage traversal. The current cached song is seeded into the live song repository and the player is prepared immediately while the normal scan continues in parallel.
7. Cached queue filtering preserves the current ID/index/position when possible and resets to the first surviving item only when the previous current file disappeared.
8. Song insertion is idempotent so the later filesystem scan cannot duplicate the early-seeded song or path. Cached future queue items remain available for playback/gapless preparation without publishing the whole cached library prematurely.
9. Media-session updates use a generation counter so slower artwork work cannot overwrite a newer readiness/playing state.
10. Added restoration timing logs. On the Motorola G57 Power, cached queue lookup completed in 69 ms and player preparation plus restored seek completed in 926 ms during the final diagnostic cold launch.
11. Added eight new pure unit tests for pending Play/cancellation/readiness transitions and cached queue filtering, bringing the full suite to 25 tests.

The Stage 3 APK passed a clean build, all 25 unit tests, Android lint, v2 signature verification, `minSdk 28` verification, Startup class inspection, ADB installation, two cold launches, and crash/error-log inspection. The first launch exposed and led to correction of a duplicate-path insertion; the repeated launch was clean. The user then completed focused physical-device acceptance and confirmed Stage 3 successful.

### Verification

- Compare warm activity recreation with a true cold process start.
- Restore at position zero and at several non-zero timestamps.
- Restore several positions in a 6â€“7 hour track and verify millisecond values are not truncated or overflowed.
- After each non-zero restoration, use relative backward seek to cross the restored timestamp and reach the beginning of the track.
- Press Play before, during, and after player preparation; one press must be sufficient.
- The UI must not show an actively playing/Pause state while the restored seek is still producing silence.
- Confirm that canceling or pausing while a Play command is pending prevents automatic start.
- Test with small and large music libraries and multiple storage providers.
- Confirm that background scanning does not replace, duplicate, or invalidate the restored queue.
- Verify notification and media-session Play commands during restoration.
- Record time from Play press to audible/confirmed playback and establish an acceptable target from device measurements.

## Problem 4: Previous track position briefly appears when changing tracks

**Status:** Implemented and verified on the Motorola G57 Power

### Current behavior

When a new track is selected while another track is playing, loading the new track feels visually laggy. The title changes to the new track, but the progress slider and elapsed timestamp briefly retain the previous track's playback position before eventually updating to the beginning of the new track.

### Root cause

Changing tracks updates `RadioQueue.currentSongIndex` and publishes the new queue index immediately. `RadioObservatory` therefore lets Compose render the new track metadata. However, the observatory does not reset its playback-position flow when the queue index changes or when `Radio.Events.Player.Staged` is dispatched.

The old `PlaybackPosition` remains visible until the newly prepared `MediaPlayer` starts its periodic position timer or another seek/position event occurs. New-player preparation is asynchronous, so unrelated state from two different tracks is temporarily combined in the UI.

Relevant code:

- `app/src/main/java/io/github/zyrouge/symphony/services/radio/Radio.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioObservatory.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/RadioPlayer.kt`
- `app/src/main/java/io/github/zyrouge/symphony/ui/view/NowPlaying.kt`
- `app/src/main/java/io/github/zyrouge/symphony/ui/view/nowPlaying/BodyContent.kt`

### Desired behavior

- As soon as a new track is accepted, its elapsed position should display `0:00` unless an explicit saved start position is being restored.
- The progress slider should reset immediately and use the new track's duration.
- No UI frame should combine the new track's metadata with the previous track's position or playback state.
- Actual asynchronous preparation may continue in the background with an appropriate loading state.

### Implemented Stage 4 solution

1. Publishes an initial playback position immediately when staging a track:
   - `played = 0` for a newly selected track.
   - `played = restoredPosition` for session restoration.
   - `total = Song.duration` while the platform player is still preparing.
2. Stages readiness before changing the queue index, then publishes the new queue identity and staged position together. The prior stopped state is suppressed during an immediate replacement, so there is no deliberate intermediate old-track frame.
3. Adds `RadioPlaybackSnapshot`, containing generation, song ID, queue index, position/duration, readiness, actual playing state, and pending-Play state in one `StateFlow` value.
4. Makes the full now-playing screen, seek bar, and mini-player consume this coherent snapshot instead of independently collecting track and position flows.
5. Resets any in-progress Compose seek drag when the playback generation changes, preventing a release from applying the old track's duration to the new track.
6. Guards playback-position callbacks with the originating player instance and generation before publishing them. The snapshot state also rejects older generations.
7. Makes media-session and notification updates capture and validate the same coherent snapshot around asynchronous artwork loading, preventing a slow older request from mixing metadata and playback state.
8. Removes the redundant pre-stop during automatic track completion; replacement playback now owns the single guarded stop/stage transition.

### Automated and package verification

- Clean build passed all 29 unit tests, including four new playback-snapshot generation tests.
- Android lint and debug APK assembly passed.
- APK Signature Scheme v2 verification passed.
- `minSdk 28`, `targetSdk 34`, and AndroidX Startup class packaging were verified.
- The exact test artifact installed with app data preserved and cold-launched successfully on the Motorola G57 Power in 1.9 seconds.
- The process remained alive with no AndroidRuntime, missing-class, ExoPlayer, or duplicate-path errors in the launch log.
- Focused visual tests passed on the Motorola G57 Power, including the requested track-change cases. Stage 4 is accepted and archived in `artifacts/timeline/stage-04-atomic-track-transition/`.

### Verification

- Change tracks while playing, paused, seeking, and fading out.
- Select a new track from songs, albums, playlists, search, and the queue.
- Verify the new track immediately shows `0:00` and its own duration.
- Restore a saved session and verify it immediately shows the saved position rather than zero or the previous track's position.
- Rapidly select several tracks and confirm late callbacks cannot overwrite the final selection.
- Test with gapless preloading enabled and disabled.
- Verify the mini-player, full now-playing screen, notification, and media session remain consistent.

## Problem 5: New tracks require manual rescan, and rescan loses playback state

**Status:** Implemented and verified on the Motorola G57 Power

### Current behavior

- Audio files added to configured device-storage locations are not reflected in Symphony until the user manually selects **Rescan**.
- Starting a manual rescan stops the current track and loses its playback position instead of refreshing the library around the active playback session.

### Root cause

Symphony scans configured folders only during initial `Groove` startup or an explicit refresh. It has no activity-resume refresh, storage-change observer, foreground refresh interval, or scheduled library synchronization.

The playback loss is explicit in both manual refresh paths: `Home.kt` and `GrooveSettingsView.kt` call `symphony.radio.stop()` before requesting a fetch. `Radio.stop()` destroys the active player, clears the queue and sleep timer, resets speed and pitch, and emits the ended state.

The current repository model also prevents safely calling the existing fetch repeatedly without a reset. Cached songs are emitted into live repositories again; for example, `SongRepository.onSong()` appends every emitted ID to `_all`, while aggregate repositories can recalculate or mutate existing records. Conversely, resetting the repositories exposes a temporarily empty library and can invalidate lookups used by the active queue, notification, and now-playing UI.

Relevant code:

- `app/src/main/java/io/github/zyrouge/symphony/ui/view/Home.kt`
- `app/src/main/java/io/github/zyrouge/symphony/ui/view/settings/GrooveSettingsView.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/groove/Groove.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/groove/MediaExposer.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/groove/repositories/SongRepository.kt`
- `app/src/main/java/io/github/zyrouge/symphony/services/radio/Radio.kt`

### Desired behavior

- New, changed, and removed tracks should be detected without requiring routine manual rescans.
- Opening or returning to Symphony should refresh the library when appropriate.
- Refreshing must not stop, pause, restart, or seek the active player.
- Current track position, playing/paused state, queue, shuffle order, loop mode, speed, pitch, sleep timer, and pending interruption state must survive a refresh.
- Library screens should update coherently without temporarily becoming empty or accumulating duplicate entries.
- Manual rescan should remain available as an immediate forced refresh, but should use the same non-disruptive pipeline.

### Implemented Stage 5 solution

1. Replaces destructive reset-and-repopulate refreshes with a snapshot/diff/commit pipeline:
   - Scan configured trees into a temporary snapshot.
   - Reuse Room metadata for unchanged files.
   - Determine added, changed, unchanged, and removed tracks.
   - Build derived albums, artists, album artists, genres, folders, and playlists from the completed snapshot.
   - Publish the new coherent library state atomically or in a tightly controlled commit.
2. Removes `radio.stop()` from both manual rescan entry points. Library synchronization never recreates the active player.
3. Preserves stable song identity by reusing the cached ID for the same storage path even when metadata is reparsed or the file timestamp changes.
4. Rebuilds songs, folders, albums, artists, album artists, and genres away from the Android main thread, then swaps repository indexes without publishing an empty or partially scanned library.
5. Retains queued song records through the commit. Deleted non-current entries are filtered from original and shuffled queues while the current ID/index is preserved; an externally deleted current song remains available to the live player as a hidden tombstone.
6. Serializes refreshes with a coroutine mutex and suppresses automatic foreground repeats inside a two-second interval. Manual Rescan remains an immediate forced request.
7. Adds `onResume` lifecycle propagation so returning from a downloader or file manager triggers verification of the selected Storage Access Framework trees.
8. Discards an incomplete snapshot if a configured tree cannot be opened or a tree traversal fails, retaining the last valid library and playback state.
9. Makes playlist loading idempotent and fixes stale derived-repository caches plus aggregate artist/album-artist count calculation exposed by repeatable refreshes.
10. Logs duration, added/changed/removed counts, total songs, active song, and whether the playback generation was preserved.

### Selected automatic-refresh policy

Stage 5 uses foreground/resume verification plus manual forced Rescan. It does not continuously poll storage or assume provider change notifications are reliable. This fits frequent file-manager/download workflows without adding a permanent background battery cost.

### Automated and package verification

- Clean build passed all 35 unit tests, including refresh-gate and queue-reconciliation coverage.
- Android lint, debug APK assembly, APK Signature Scheme v2, `minSdk 28`, `targetSdk 34`, and AndroidX Startup class verification passed.
- The exact artifact installed with app data preserved and cold-launched successfully on the Motorola G57 Power.
- The device committed an initial eight-track snapshot in 1,401 ms and a subsequent unchanged foreground refresh in 85 ms.
- The second device refresh reported zero added, changed, or removed entries and preserved the existing playback generation.
- No AndroidRuntime, missing-class, ExoPlayer, or duplicate-path error occurred. The user confirmed the focused storage-change and playback-continuity tests passed. Stage 5 is archived in `artifacts/timeline/stage-05-library-refresh/`.

### Verification

- Add, modify, rename, move, and delete tracks while Symphony is closed, backgrounded, and open.
- Confirm changes appear after the selected automatic-refresh trigger.
- Rescan while playing and paused at non-zero positions; position and state must remain unchanged.
- Verify queue order, shuffle, loop, speed, pitch, sleep timer, notification, and media session survive refresh.
- Verify a scan never publishes duplicate song IDs or double-counts album/artist durations.
- Verify playlists and the active queue retain stable references when metadata changes.
- Exercise deletion of the active song and deletion of future queued songs.
- Trigger several scans rapidly and confirm they are coalesced rather than run concurrently.
- Test small and large libraries, nested folders, SD cards, and different Storage Access Framework providers.
- Simulate daily add/delete churn dominated by a small number of long-form files.
- Measure scan duration, CPU, memory, I/O, and UI recomposition impact.

## Scope boundary

Problems 1–5 are the resolved foundational defects that stabilized playback, seeking, restoration, track transitions, and library refresh. Stage 6 onward is feature integration, enhancement, and optimization work tracked in `Features.md`.

If later development reveals a genuine regression or new core defect, it may be added here; new capabilities should be recorded in `Features.md`.

