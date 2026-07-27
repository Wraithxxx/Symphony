# Symphony regression test plan

Last updated: 2026-07-20

This document is the durable regression checklist for completed development stages. The five foundational problems are fixed; focused feature acceptance and the full integrated regression suite remain pending where noted. Every test must be repeated on the final integrated APK before release; a stage passing its original acceptance test does not replace this later regression pass.

Stages 1–5 correspond to the resolved core problems in `Problems.md`. Stage 6 onward covers feature integrations, enhancements, and optimizations tracked in `Features.md`.

## Test environment and recording

Primary physical device:

- Motorola G57 Power
- Android 9 or newer remains the supported range; the final APK must also be checked on Android 9 when a suitable device or emulator is available.
- Use locally stored media from the same Storage Access Framework locations used in normal daily operation.
- Include short music and long podcasts/audio stories.

For every manual test, record:

- APK name and SHA-256.
- Device model and Android version.
- Track format and approximate duration.
- Initial playback state and relevant settings.
- Expected and actual result.
- Pass, fail, or blocked status.
- Reproduction steps and an ADB log for every failure.

Moving an older candidate from `artifacts/test` into `artifacts/timeline` only records version history; it does not mark that stage successful. Acceptance status must remain explicit here and in `memory.md`. The full cross-stage suite below must pass before the final integrated build is considered stable.

## Media set

Prepare a reusable test library containing, where available:

- MP3: one 2–5 minute track and one multi-hour track.
- M4A/AAC: one short and one long track.
- FLAC: one short track.
- OGG/Vorbis or Opus: one short track.
- A 6–7 hour podcast or audio story.
- A variable-bitrate file and a constant-bitrate file.
- Files stored in at least two configured folders; include SD-card storage if available.
- One deliberately unsupported or damaged file for error-handling checks, kept separate from the normal library.

## Stage 1 — audio-focus handling

Goal: Symphony must react correctly to audio focus from any application or system audio source. Behavior must depend on Android's focus-loss category, not on a hard-coded list of applications.

### AF-01 — temporary interruption resumes

1. Start a Symphony track and confirm audible playback.
2. Trigger a temporary audio interruption such as navigation guidance, an assistant response, or another source that requests transient focus.
3. Allow the interruption to finish without touching Symphony.

Pass criteria: Symphony pauses for the interruption and resumes automatically after focus returns, from approximately the interrupted position.

### AF-02 — temporary interruption while already paused

1. Pause Symphony manually.
2. Trigger and finish a temporary interruption.

Pass criteria: Symphony remains paused and does not start automatically.

### AF-03 — manual pause cancels pending recovery

1. Start Symphony.
2. Trigger a transient interruption.
3. While Symphony is interrupted, issue an explicit Pause or Stop from Symphony, its notification, or its media control.
4. End the interruption.

Pass criteria: Symphony does not resume because the user canceled the pending recovery.

### AF-04 — ducking and volume restoration

1. Start Symphony at a clearly audible volume.
2. Trigger an audio source that requests ducking rather than a full pause.
3. End that source.

Pass criteria: Symphony lowers its volume without restarting, then restores the previous playback volume when focus returns. Position must continue advancing normally.

### AF-05 — permanent focus loss does not auto-resume

1. Start Symphony.
2. Start sustained media in another application, such as a video or another music player.
3. Stop the other application's media.

Pass criteria: Symphony stops or pauses for permanent focus loss and does not resume automatically. The user can return to Symphony and resume manually.

### AF-06 — interruption is application-agnostic

Repeat AF-01 and AF-05 using several available sources, for example:

- A video application.
- A social-media application.
- Another music or podcast application.
- Navigation or text-to-speech audio.
- A phone or VoIP call where practical.
- Alarm, timer, or assistant audio where practical.

Pass criteria: Behavior follows transient, ducking, and permanent focus semantics consistently regardless of the source application.

### AF-07 — repeated and overlapping focus events

1. Start Symphony.
2. Cause multiple transient or ducking events before the first recovery completes.
3. Let all interrupting audio finish.

Pass criteria: Symphony performs at most one appropriate recovery, does not oscillate between play and pause, and does not remain permanently ducked.

### AF-08 — notification and lock-screen state

Run AF-01, AF-04, and AF-05 while observing the in-app control, notification, lock-screen media control, and Bluetooth control state.

Pass criteria: All surfaces agree on whether Symphony is playing or paused. No surface shows Pause while playback is actually stopped after the focus transition settles.

### AF-09 — Bluetooth output

Repeat transient, ducking, permanent-loss, manual-pause, and manual-resume cases while listening through Bluetooth earbuds.

Pass criteria: Stage 1 behavior remains identical to speaker playback. The rejected experiment to guarantee earbud-command routing after another app becomes the active media owner is not an acceptance requirement because Android may route that command elsewhere.

### AF-10 — Ignore audio focus loss setting

1. Enable Symphony's **Ignore audio focus loss** setting.
2. Repeat representative transient and permanent interruption tests.
3. Disable the setting afterward.

Pass criteria: Symphony respects the setting's documented behavior and does not leave stale pending-resume or ducking state after the setting is disabled.

### AF-11 — lifecycle and repeated sessions

Repeat focus tests after warm activity recreation, after a cold app start, and after several play/pause cycles.

Pass criteria: behavior does not depend on stale focus state from an earlier player or process session.

## Stage 2 — Media3 playback and seeking

Goal: an absolute slider seek or restored non-zero position must never become a lower seek boundary. All seek paths must control the actual decoder position, remain accurate for multi-hour media, and preserve Stage 1 behavior.

### SK-01 — original backward-seek reproduction

1. Play a long track from the beginning.
2. Drag the slider to approximately `30:00`.
3. Let playback advance briefly.
4. Press backward seek repeatedly until crossing below `30:00`.

Pass criteria: every command moves to the expected earlier position and audible playback continues there. The position must not flash earlier and snap back to `30:00`.

Current result: passed on the Motorola G57 Power using the Stage 2 Media3 candidate.

### SK-02 — direct slider seek before the old boundary

1. Establish an absolute position near `30:00`.
2. Drag directly to approximately `29:50`, then to several earlier positions.

Pass criteria: the slider, timestamp, decoder position, and audible content remain at each selected target instead of snapping back.

Current result: passed on the Motorola G57 Power using the Stage 2 Media3 candidate.

### SK-03 — restored-position boundary

1. Stop or pause at a non-zero timestamp and allow Symphony to persist the session.
2. Close the activity and perform both a warm reopen and a true cold process restart.
3. Resume the track.
4. Seek backward across the restored timestamp and continue toward the beginning.

Pass criteria: the entire earlier region remains seekable. A restored position never becomes a lower boundary.

### SK-04 — all seek entry points

Test backward and forward seeking through:

- Full now-playing buttons.
- Mini-player controls where available.
- Slider dragging.
- Notification controls.
- Lock-screen/media-session controls.
- Bluetooth controls.

Pass criteria: all entry points use the same authoritative target and produce consistent UI and audible positions.

### SK-05 — rapid serialized seeks

1. Issue rapid alternating backward and forward commands.
2. Drag the slider several times before prior seeks can settle.
3. Mix slider and button commands.

Pass criteria: the latest requested position wins, no stale completion overrides it, playback does not freeze, and the UI does not oscillate between old targets.

### SK-06 — beginning and end boundaries

1. Seek backward repeatedly near the beginning.
2. Seek forward repeatedly near the end.
3. Drag to exactly zero and close to the duration.

Pass criteria: targets clamp to `[0, duration]`, never become negative or exceed the track, and completion behavior remains correct.

### SK-07 — long-duration accuracy

Use a 6–7 hour track and seek around:

- The first minute.
- One hour.
- The midpoint.
- The final hour.
- Near the end.

Pass criteria: positions do not overflow, truncate, wrap, or progressively drift. Backward seeking can cross every earlier absolute and restored target.

### SK-08 — format and bitrate matrix

Run SK-01, SK-02, SK-05, and SK-06 against every format in the reusable media set, including variable- and constant-bitrate files.

Pass criteria: no tested supported format recreates a void region. Small format-specific approximation is acceptable only if it settles near the requested timestamp and never creates a persistent boundary.

### SK-09 — pause, seek, and resume

1. Pause at a non-zero position.
2. Seek backward and forward while paused.
3. Resume once.

Pass criteria: playback begins from the final requested position, the UI remains paused until Play is issued, and a seek does not start playback by itself.

### SK-10 — seek during preparation or track restoration

Issue a seek as early as the UI permits after selecting or restoring a track.

Pass criteria: the request is either applied when the player becomes ready or safely rejected by the UI; it must not corrupt later position updates or make part of the track unseekable.

### SK-11 — track-change regression

Change tracks while playing, paused, fading, and shortly after a seek.

Pass criteria: the final selected track plays, old-player seek callbacks cannot affect it, and no crash or audio from the discarded player occurs. The stale previous-track timestamp is tracked separately under Problem 4 and should be retested after that fix.

### SK-12 — completion, queue, shuffle, loop, and gapless behavior

Verify normal completion and automatic advance with:

- Queue loop disabled and enabled.
- Single-track loop.
- Shuffle enabled.
- Gapless playback enabled and disabled.
- Pause-on-current-song-end enabled.

Pass criteria: the correct next track and autoplay decision are preserved, no duplicate completion occurs, and preloaded players do not leak or play unexpectedly.

### SK-13 — speed and pitch

Test representative supported speed and pitch values before and after seeking, pausing, changing tracks, and restoring a session.

Pass criteria: settings apply without unwanted playback starts, survive expected queue transitions, and do not prevent subsequent seeking or playback.

### SK-14 — audio session and effects

If Android equalizer/audio effects are used, open them during playback and after changing tracks.

Pass criteria: Symphony exposes a valid current audio-session ID where supported, and changing or releasing players does not crash the effects flow.

### SK-15 — error and resource handling

Attempt the deliberately unsupported or damaged file, rapidly skip several tracks, and perform repeated open/play/stop cycles.

Pass criteria: errors are reported or skipped according to existing behavior, failed players are released, the queue remains usable, and later valid tracks still play.

### SK-16 — lifecycle and process recreation

Exercise background/foreground transitions, screen rotation or activity recreation, app swipe-away, process death, and reopening after several hours.

Pass criteria: there is no crash, duplicate playback, leaked audio, or stale player callback. Position persistence and cold-resume latency will receive their final acceptance criteria with Problem 3.

### SK-17 — Stage 1 regression on Media3

Run AF-01 through AF-11 on the integrated Media3 build.

Pass criteria: replacing the playback engine must not change the verified Stage 1 focus policy. Media3's internal focus management must remain disabled so Symphony's `RadioFocus` state machine is the sole authority.

## Stage 3 — robust cold restoration and pending Play

Goal: cold restoration must be correct independently of decoder speed or library size. A single Play request must survive preparation and restored seeking, while UI and media controls report readiness rather than claiming playback during silence.

### CR-01 — cached restoration precedes full scan

1. Save a non-zero position with a populated library and queue.
2. Force-stop Symphony and relaunch it.
3. Observe when the saved current track and position appear relative to completion of the library scan.

Pass criteria: the saved current item is restored from Room without waiting for the complete storage traversal. The scan continues in the background without replacing or duplicating the queue/current song.

ADB diagnostic result on the Motorola G57 Power: cached queue lookup completed in 69 ms; player preparation and restored seek completed in 926 ms.

### CR-02 — Play during preparation is retained

1. Cold-launch with a saved non-zero position.
2. Press Play immediately when the restored controls appear, before preparation finishes if possible.
3. Do not press Play again.

Pass criteria: one Play request is sufficient. A progress indicator appears while pending, the restored seek completes first, then playback starts once from the saved position.

### CR-03 — pending Play can be canceled

1. Repeat CR-02.
2. Press the pending progress control again, Pause, or Stop before readiness.

Pass criteria: the pending intent is cleared and playback does not start later when preparation/seek completes.

### CR-04 — no false Pause state during silence

Observe the full player, mini-player, notification, lock screen, and media session during cold restoration and a pending Play request.

Pass criteria: Compose shows a progress indicator rather than Pause until actual playback begins. Android reports Buffering while pending and Playing only when the engine is actually playing.

### CR-05 — restored seek gates playback

1. Restore several non-zero timestamps, including positions in a 6–7-hour track.
2. Request Play as early as possible.

Pass criteria: playback never begins from zero or another stale decoder position before jumping to the saved target. The first audible/advancing position is the restored target, within normal format seek accuracy.

### CR-06 — saved target survives an early lifecycle pause

1. Cold-launch with a saved non-zero position.
2. Background or close the activity while restoration is still preparing.
3. Reopen Symphony.

Pass criteria: lifecycle persistence does not overwrite the intended saved position with zero merely because the decoder snapshot was not ready.

### CR-07 — cached queue filtering

1. Save a queue and position.
2. Delete a non-current item, then repeat with the current item, using the file manager while Symphony is stopped.
3. Relaunch.

Pass criteria: missing non-current entries are filtered while the same current item/index/position is retained. If the prior current file is missing, Symphony selects the first surviving item with position zero and remains usable.

### CR-08 — background scan idempotence

Cold-launch repeatedly with the same cached current song while collecting `SymphonyLogger` output.

Pass criteria: the later scan emits no duplicate-song or duplicate-path exception, the song appears once in lists, and album/artist aggregates are not duplicated.

### CR-09 — media-session ordering

Rapidly request Play, cancel, Play again, and Pause during restoration while artwork is loading.

Pass criteria: a slower older artwork/session update cannot overwrite the newest buffering, playing, paused, metadata, or position state.

### CR-10 — fallback without usable cache

Test first launch, cleared cache, revoked/stale cached URI, and a saved queue whose cached records are unavailable.

Pass criteria: Symphony falls back to normal full-scan restoration or controlled error/queue recovery without crashing, looping through an invalid item, or starting the wrong track.

## Stage 4 — atomic track transition state

Goal: a track change must publish the selected track's identity, initial position, duration, readiness, and playing state as one coherent generation. No screen or Android media surface may temporarily combine state from two tracks.

### TT-01 — immediate initial position

Change to a different track while playing, paused, and shortly after seeking.

Pass criteria: the new title, `0:00`, its own duration, and its preparing state appear together. The previous track's elapsed position never appears with the new metadata.

### TT-02 — restored initial position

Cold-launch with a saved non-zero position.

Pass criteria: the restored title, saved position, duration, and restoring state appear together; neither zero from an ordinary selection nor state from the prior process is shown as the authoritative snapshot.

### TT-03 — rapid selection and stale callbacks

Select several tracks rapidly, including while the prior player is fading or seeking.

Pass criteria: the final selection wins, discarded-player callbacks are ignored, and the UI never jumps back to an earlier track or timestamp.

### TT-04 — all playback surfaces

Observe the full now-playing screen, mini-player, notification, lock screen, and external media-session controller during TT-01 through TT-03.

Pass criteria: every surface reports one track generation consistently. Artwork loading cannot allow an older metadata or position update to overwrite the final selection.

### TT-05 — queue and automatic transitions

Repeat transitions using queue selection, Previous/Next, normal completion, shuffle, loop modes, and gapless playback both enabled and disabled.

Pass criteria: the correct queue index and track are selected, the new position is staged immediately, and completion does not double-stop or emit a stale intermediate frame.

### TT-06 — seek interaction reset

Begin dragging the seek bar, change track, then release or begin a new drag on the selected track.

Pass criteria: temporary drag state is cleared by the new playback generation and cannot seek the selected track using the prior track's duration.

Automated coverage verifies coherent initial and restored snapshots, advancing same-generation updates, and rejection of older-generation snapshots. Stage 4 increases the unit suite from 25 to 29 tests.

Final Stage 4 result: clean build, 29/29 unit tests, Android lint, APK assembly, v2 signature, `minSdk 28`, `targetSdk 34`, and AndroidX Startup class inspection all passed. The exact artifact installed with data preserved and cold-launched on the Motorola G57 Power in 1.9 seconds; it remained alive with no relevant startup errors. The user confirmed all focused Stage 4 tests passed, and the build is archived in `artifacts/timeline/stage-04-atomic-track-transition/`.

## Stage 5 — non-destructive automatic library refresh

Goal: foreground and manual refreshes must detect storage changes by committing a completed library snapshot without stopping, pausing, recreating, or seeking the active player.

### LR-01 — automatic foreground discovery

1. Background Symphony for at least two seconds.
2. Add one or more audio files inside a configured media tree using another app.
3. Return to Symphony without pressing Rescan.

Pass criteria: the files appear after the foreground scan completes. Existing songs remain visible while scanning, and no duplicate entries appear.

### LR-02 — manual refresh preserves playback

While playing a long track at a clearly non-zero position, record the queue, shuffle/loop state, speed, pitch, and sleep timer, then press **Rescan** from both available UI locations.

Pass criteria: audio is uninterrupted; the player generation, track, position progression, playing state, queue order, and all options remain intact.

### LR-03 — deleted future queue entries

Queue several tracks, delete one or more non-current files externally, then return to Symphony or press Rescan.

Pass criteria: deleted future entries disappear from the library and queue, the current index is adjusted to preserve the same current track, and playback is not restarted.

### LR-04 — externally deleted current track

Delete the currently playing file externally, then return to Symphony.

Pass criteria: the live player and its metadata/position are retained without a refresh-triggered stop. If the storage provider permits the already-open stream to finish, completion advances normally. A later refresh after changing tracks removes the unavailable tombstone.

### LR-05 — changed file keeps stable identity

Modify or replace a file at the same path, then refresh while its ID is referenced by a queue or internal playlist.

Pass criteria: metadata updates, but the stable song ID and queue/playlist reference remain valid.

### LR-06 — rename, move, and removal

Rename and move files within configured trees, and remove completed podcasts or stories in batches.

Pass criteria: the committed library reflects the storage snapshot, obsolete non-current IDs are removed, folder browsing is complete, and aggregate album/artist/genre counts are correct.

### LR-07 — failed or unavailable storage tree

Temporarily revoke or make one configured tree unavailable and trigger a refresh.

Pass criteria: the incomplete snapshot is discarded; the last valid in-memory library and active playback remain intact instead of being replaced by an empty or partial library.

### LR-08 — repeated refresh requests

Resume the app repeatedly and invoke manual Rescan during or immediately after an automatic scan.

Pass criteria: scans never run concurrently, immediate automatic repeats are throttled, repository entries and playlists do not duplicate, and the app remains responsive.

### LR-09 — metadata reparse / Clear cache

Use the settings action that clears cached metadata while playing.

Pass criteria: all files are reparsed with stable IDs, obsolete private artwork/lyrics cache entries are trimmed, and playback plus queue state remain intact.

### LR-10 — library scale and long-form regression

Repeat LR-01, LR-02, LR-03, and LR-08 with a realistically large nested library and a 6–7-hour active track.

Pass criteria: position arithmetic remains accurate, repository rebuilding does not block the Android main thread, and scan time is recorded from `SymphonyLogger` without audible interruption.

Automated Stage 5 coverage currently verifies first/forced/throttled refresh decisions plus queue filtering, current-song retention, and shuffled-index reconciliation. The suite increases from 29 to 35 tests.

Final Stage 5 result: clean build, 35/35 unit tests, Android lint, APK assembly, v2 signature, `minSdk 28`, `targetSdk 34`, and AndroidX Startup class inspection passed. On the Motorola G57 Power, the exact artifact committed eight tracks in 1,401 ms and an unchanged foreground snapshot in 85 ms; the latter preserved the active playback generation. The user confirmed the focused Stage 5 tests passed, and the build is archived in `artifacts/timeline/stage-05-library-refresh/`.

## Stage 6 — provider-aware in-app deletion

Goal: permanently delete a single audio document only after explicit confirmation, and reconcile every Symphony reference exactly once after the storage provider confirms success.

### DL-01 — delete a non-current song

1. Open a song menu from the library.
2. Choose **Delete from device** and inspect the title/path confirmation.
3. Cancel once, verify nothing changes, then confirm.

Pass criteria: cancellation is inert. Confirmation deletes the actual file, removes it from the library, queue, internal playlists, Room metadata, artwork, and lyrics cache, while current playback remains uninterrupted.

### DL-02 — delete the current playing song

Queue at least three songs, play the middle item, and delete it from Now Playing.

Pass criteria: after provider-confirmed deletion, Symphony removes all references and selects the following available queue item. If playback was active, the replacement plays; if paused, it remains paused. There is no stale metadata/position frame or replay of the deleted item.

### DL-03 — delete the last/only queue item

Delete the current item when it is last in the queue, then when it is the only item.

Pass criteria: the prior remaining item is selected when appropriate; an empty queue stops cleanly and clears the session without a crash.

### DL-04 — shuffled and duplicate queue references

Delete current and non-current songs with shuffle enabled and with duplicate queue references.

Pass criteria: every duplicate reference is removed from original and shuffled forms, the current identity/index remains correct, and the intended replacement is selected.

### DL-05 — internal versus external playlists

Place the target in Favorites, another internal playlist, and an external M3U playlist before deletion.

Pass criteria: internal playlist paths are removed and persisted before refresh. External M3U files are not rewritten automatically. Their unavailable entry simply no longer resolves.

### DL-06 — missing write permission

Attempt deletion using a media folder that was authorized by an older build with read-only persistence.

Pass criteria: the file and all Symphony state remain unchanged, and the app directs the user to reselect the media folder. After reselecting the same folder in Settings, retry succeeds if the provider grants write access.

### DL-07 — provider does not support deletion

Attempt deletion from a read-only or non-deletable document provider.

Pass criteria: Symphony reports that deletion is unsupported and does not alter library, queue, playlist, or cache state.

### DL-08 — provider failure or missing file

Cause a provider error, or externally remove the file between opening the menu and confirming.

Pass criteria: the app reports the specific failure category, remains usable, and never claims successful deletion. Missing files can still be reconciled by Stage 5 foreground refresh.

### DL-09 — concurrent refresh and repeated action

Trigger deletion while a refresh is active and attempt repeated confirmation.

Pass criteria: deletion and refresh are serialized under one library transaction, there is no partial reappearance, and a second attempt resolves safely as missing rather than corrupting state.

### DL-10 — sidecars, formats, and long-form playback

Delete representative MP3, M4A, FLAC, OGG, and Opus files where available, including a multi-hour item with a matching `.lrc` sidecar.

Pass criteria: supported providers delete every audio format identically. The `.lrc` sidecar is not deleted automatically, long positions do not affect queue reconciliation, and later playback remains healthy.

Automated Stage 6 coverage verifies current, last-current, non-current, shuffled, and duplicate queue-removal planning. The suite increases from 35 to 39 tests.

Candidate verification result: the corrected APK passed a true clean no-daemon build, 39/39 unit tests, Android lint, v2 signature, `minSdk 28`, `targetSdk 34`, and AndroidX Startup class inspection. It installed with data preserved and cold-launched `MainActivity` on the Motorola G57 Power in 2.0 seconds, remained alive, and completed a seven-track refresh in 1,407 ms without relevant errors. An earlier package with an expanded generated translation serializer failed the physical launch verifier; it was withdrawn, overwritten, and is not an accepted artifact. DL-01 through DL-10 remain the manual acceptance suite.

## Stage 7 — cyclic manual queue navigation

Goal: manual Previous and Next commands should move through the queue without endpoint boundaries, independently of automatic repeat mode.

### CQ-01 — Previous wraps first to final

Start the first entry manually and press Previous within three seconds.

Pass criteria: Symphony selects and plays the final entry, regardless of how the first entry was reached.

### CQ-02 — Next wraps final to first

Start the final entry and press Next.

Pass criteria: Symphony selects and plays the first entry without staging it paused or requiring a second Play command.

### CQ-03 — preserve the three-second Previous rule

Let the first or any middle entry play beyond three seconds, then press Previous twice.

Pass criteria: the first press restarts the current entry at `0:00`; the second navigates to the preceding entry, wrapping first to final where applicable.

### CQ-04 — automatic repeat modes remain independent

Repeat CQ-01 and CQ-02 with Repeat Off, Repeat Queue, and Repeat One. Separately let tracks finish naturally in each mode.

Pass criteria: manual navigation remains cyclic in every mode. Natural completion retains its existing mode-specific behavior; manual controls never toggle the repeat icon or underlying mode.

### CQ-05 — shuffle, duplicates, and mutations

Repeat boundary navigation with shuffle enabled and duplicate song IDs. Then add, remove, delete, reorder, replace, and refresh queue entries before navigating.

Pass criteria: cyclic targets use the currently displayed queue order immediately. There is no stale historical index, crash, or wrong duplicate occurrence caused by prior queue state.

### CQ-06 — empty and single-entry queues

Use Previous and Next with an empty queue and with a single playing entry at a non-zero position.

Pass criteria: an empty queue is inert. A single entry seeks/restarts at zero without rebuilding the player, changing readiness incorrectly, or becoming paused unexpectedly.

### CQ-07 — all command surfaces and gapless modes

Repeat CQ-01 through CQ-03 using the full player, mini-player, queue swipes, notification, media session, and physical controls, with gapless playback enabled and disabled.

Pass criteria: every surface delegates to the same cyclic navigation and no preloaded player changes the chosen target.

### CQ-08 — Bluetooth transport commands

Connect Bluetooth controls that emit distinct Previous and Next commands and repeat CQ-01/CQ-02. Verify Rewind and Fast Forward remain relative seeks.

Pass criteria: each raw key-down produces exactly one appropriate cyclic queue command; key-up/repeat events do not double-trigger.

Device finding: the tested Boult Audio Airbass configuration emitted `KEYCODE_MEDIA_NEXT` for both isolated left and right gestures, with identical `deviceId=-1` and `source=0`, while Symphony was the active media session. The app cannot distinguish earbud sides in that state. Cyclic Next still works at the final boundary, but left=Previous requires the earbuds to emit a distinct Previous command.

### CQ-09 — genuine cold-process restoration

Pause on a known queue entry, leave Symphony unused until Android can kill its process (target 7–8 hours), reopen it, start playback, and immediately test Previous and Next from both a middle entry and a boundary.

Pass criteria: the restored queue uses the same cyclic rules as a warm session; no gesture falls back to the original bounded navigation behavior.

### CQ-10 — command during restoration

Force-stop Symphony, cold-launch it, press Play, and issue a distinct Bluetooth Previous or Next command as soon as the restored track becomes controllable. Repeat with a long podcast or audio story.

Pass criteria: the session has already published its transport actions and routes the command through `RadioShorty`; artwork loading does not delay or replace the command target.

### CQ-11 — slow or missing artwork

Repeat CQ-10 with a track whose artwork is large, slow to decode, absent, or invalid.

Pass criteria: basic metadata and playback state appear immediately, Bluetooth navigation works, and later artwork completion only enriches metadata rather than changing playback state.

### CQ-12 — process recreation and Developer Options

Repeat cold launch and Bluetooth boundary navigation after disabling Developer Options, after enabling it again, and after several force-stop/reopen cycles.

Pass criteria: behavior depends only on the current queue and session state. There is one live Symphony session for the process, no stale callback, and no difference attributable to Developer Options.

### CQ-13 — active media-button ownership

After every cold launch, start playback and inspect or exercise notification, lock-screen, wired/physical, and Bluetooth controls.

Pass criteria: Android selects Symphony as the media-button session while it is playing, and every distinct Previous/Next event reaches the same cyclic resolver exactly once.

Automated Stage 7 coverage includes six stateless cyclic-navigation tests, four raw Bluetooth media-button resolution tests, and five cold-session publication-state tests.

Candidate verification result: the cold-hardened cyclic clean no-daemon build passed 54/54 unit tests, Android lint, and APK assembly. The exact artifact passed v2 signature, `minSdk 28`, `targetSdk 34`, checksum, and AndroidX Startup class inspection. It installed with app data preserved and cold-launched `MainActivity` on the Motorola G57 Power in 1,874 ms. The session published `Restoring` before decoder preparation completed, progressed through `Seeking` to `Ready`, and completed restored seeking in 664 ms without a relevant crash, verifier, or ExoPlayer error. After playback began, Android selected Symphony as the media-button session and routed an external standard Pause key to it. CQ-01 through CQ-13 remain the focused manual acceptance suite, including the genuine 7–8 hour cooldown test.

## Stage 8 — persistent pinned media controls

Goal: Android's pinned media player must remain backed by a live service and session after Symphony's activity/task is dismissed.

### PM-01 — task removal while playing

Start playback, dismiss Symphony from Recents, and leave the pinned media player visible.

Pass criteria: no Symphony activity/task remains, but audio, foreground service, media session, metadata, position, and transport controls remain live.

### PM-02 — task removal while paused

Pause a track, dismiss the task, then use the pinned player's Play and Pause buttons.

Pass criteria: the same track resumes without launching the UI, the controls update promptly, and another Pause works.

### PM-03 — all external controls after removal

After removing the task, test notification, lock-screen, wired controls, Bluetooth Play/Pause/Previous/Next, and seek actions where Android exposes them.

Pass criteria: every command reaches the one current `RadioSession`; cyclic Stage 7 navigation and seek behavior remain unchanged.

### PM-04 — reopen while the service remains alive

Dismiss the task during playback, operate the pinned player, then reopen Symphony.

Pass criteria: the UI attaches to the existing player and shows the same song, position, queue, loop/shuffle state, and play/pause state without creating a second session.

### PM-05 — process recreation

Leave paused media pinned long enough for Android to reclaim the process, then use the pinned player or reopen Symphony.

Pass criteria: sticky service/session reconstruction uses the cached queue and position, controls become live again, and no stale notification remains bound to a dead callback.

### PM-06 — empty/stopped state

Stop and clear playback, dismiss the task, and observe the service/notification.

Pass criteria: Symphony does not keep an empty foreground service or misleading active media session indefinitely.

### PM-07 — repeated lifecycle churn

Repeat remove/reopen, rotate, screen lock/unlock, and task removal while rapidly issuing controls.

Pass criteria: there is one service and one media session, no duplicate audio, no notification-channel error, and no foreground-service timeout/crash.

Automated Stage 8 coverage increases the suite from 54 to 59 tests and verifies the service foreground-state planner and task-removal contract.

Candidate verification result: the clean build passed 59/59 unit tests, Android lint, APK assembly, v2 signature, SDK checks, checksum verification, and AndroidX Startup inspection. On the Motorola G57 Power, task removal left no Symphony activity/task while the same process, foreground service, and media session remained. External Pause → Play → Pause succeeded. Extended idle/process-recreation acceptance remains pending.

## Stage 9 — optional long-track position retention

Goal: remember qualifying long-track positions only when explicitly enabled, without confusing them with the active-session restore path or creating stale checkpoints.

### PR-01 — default off

Install/update with no prior Stage 9 setting, play and leave several tracks, then return to them.

Pass criteria: optional per-track positions are not restored or written. The active current session may still recover its cached position after restart.

### PR-02 — duration threshold boundaries

Enable retention and set a known threshold. Test tracks just below, exactly equal to, and just above it.

Pass criteria: below-threshold tracks are ignored; equal and above-threshold tracks can be remembered. Changing the threshold immediately affects eligibility.

### PR-03 — music and long-form extremes

Test a 2–3-minute music track and a 6–7-hour podcast/story with the default 20-minute threshold.

Pass criteria: the music track starts normally from zero, the long-form track restores its exact position, and all calculations remain accurate with `Long` millisecond values.

### PR-04 — checkpoint triggers and throttling

On an eligible track, play normally, pause, seek, switch tracks, dismiss the task, and restart the service/app.

Pass criteria: ordinary writes are throttled to roughly 15-second intervals; pause, seek, transition, and lifecycle events force a checkpoint. Current-session persistence is refreshed even when optional retention is disabled.

### PR-05 — safe restored seeking

Restore a long track, seek backward before the saved point, seek forward across it, and drag repeatedly through both regions.

Pass criteria: Stage 2 seek behavior remains intact; no region becomes void, snaps back, or becomes unseekable.

### PR-06 — near-start and completion clearing

Leave an eligible track below 10 seconds, then separately within the larger of 30 seconds or 1% of its end.

Pass criteria: both checkpoints are cleared. Reopening the track starts from zero instead of resuming trivial or effectively completed progress.

### PR-07 — explicit active-session precedence

Create a long-term checkpoint, then leave the same track as the explicit active session at a different position and restart.

Pass criteria: the cached active-session position wins. The optional older checkpoint never overrides a more specific current-session restore.

### PR-08 — file replacement protection

Create a checkpoint, replace the underlying file at the same logical location with different size, modification time, or duration, and refresh the library.

Pass criteria: the fingerprint mismatch rejects and removes stale progress; the replacement begins at zero.

### PR-09 — deletion and library cleanup

Create checkpoints for several tracks, delete one in Symphony, remove another externally, and run automatic/manual refresh.

Pass criteria: successful deletion removes its record immediately; library reconciliation removes orphaned IDs; live entries remain unchanged.

### PR-10 — clear all

While an eligible current track is paused at a restorable position, choose **Clear remembered positions**, confirm, and remain on that track beyond another checkpoint interval.

Pass criteria: the count becomes zero and the current entry is not immediately recreated. It may become eligible again only after changing away and returning.

### PR-11 — Play from beginning

Open the song menu for a track with a valid checkpoint and choose **Play from beginning**.

Pass criteria: playback starts explicitly at `0:00`, the record is removed, and no transition callback recreates it from the outgoing player.

### PR-12 — disabled and re-enabled retention

Create records, disable retention, use those tracks, then re-enable it without clearing.

Pass criteria: disabled mode neither reads nor updates optional records. Existing records remain dormant and are available again after re-enabling, subject to current threshold/fingerprint rules.

### PR-13 — UI and messaging

Inspect **Settings → Player → Playback progress** with the switch off and on, open the duration control, and exercise restore.

Pass criteria: dependent controls appear only when enabled; the slider spans 5–180 minutes in five-minute steps; the default is 20 minutes; the stored count is accurate; no “Resumed from…” toast, snackbar, or message appears.

### PR-14 — queue mutations, shuffle, and duplicates

Create a checkpoint, then reorder, shuffle, refresh, delete adjacent entries, and use duplicate queue references before returning to the saved track.

Pass criteria: checkpoints remain keyed to the actual player song ID and cannot be assigned to whichever queue index happens to be current during a transition.

Automated Stage 9 coverage adds 10 policy tests and increases the complete suite from 59 to 69 tests. It verifies disabled/below/equal threshold decisions, near-start and both completion windows, exact restoration, completed-entry rejection, matching file fingerprints, and replacement rejection.

Candidate verification result: the true clean no-daemon build passed 69/69 unit tests, Android lint, APK assembly, v2 signature, `minSdk 28`, `targetSdk 34`, checksum verification, and AndroidX Startup inspection. The exact artifact installed and launched on the Motorola G57 Power without a fatal runtime error. The settings UI rendered correctly. With the feature enabled, an 82-minute Opus story wrote a fingerprinted checkpoint near 61 seconds; after leaving and returning, the same track resumed beyond that checkpoint. PR-01 through PR-14 remain the focused manual acceptance suite.

## Stage 10 — long-press multi-track deletion

Goal: select several visible songs and delete their physical files through one coherent provider-aware transaction, without regressing singular deletion.

### MD-01 — enter, toggle, and exit selection

Long-press one song, tap two more, deselect one, then press Android Back.

Pass criteria: long press does not start playback; taps toggle only while selection is active; count, highlights, and checkboxes remain exact; Back exits selection and the next normal tap plays as before.

### MD-02 — select all and duplicate references

Use Select all in a normal list and in a playlist containing duplicate references to one song.

Pass criteria: all visible underlying song IDs are selected, the action changes to Deselect all, and duplicate references count/delete the physical file only once.

### MD-03 — confirmation and cancellation

Select enough songs to require scrolling and open Delete.

Pass criteria: the dialog shows the exact count, every selected title and storage path, and an irreversible warning. Scrolling reaches every target. Cancel changes no file or Symphony state.

### MD-04 — successful non-current batch

Select several non-current files from writable providers and confirm.

Pass criteria: every provider-confirmed file is gone; playback remains uninterrupted; the files disappear from library, queue duplicates, internal playlists, progress, and private caches after one refresh.

### MD-05 — current plus adjacent queued tracks

While playing a middle queue item, select it, the following item, and another non-current item.

Pass criteria: Symphony computes one final queue state and starts only the first surviving replacement after the former current position. It never stages the selected successor, and playing versus paused intent is preserved.

### MD-06 — queue tail and entire queue

Delete the current final item plus adjacent predecessors, then separately delete every queued file.

Pass criteria: tail deletion falls back to the final survivor. Deleting the complete queue stops and clears playback cleanly without a stale notification/session.

### MD-07 — partial provider failure

Select a mixture of writable, read-only/unsupported, missing, and permission-denied files.

Pass criteria: supported documents are deleted and reconciled; failures retain their original files/references and remain selected. The result message reports deleted/requested counts and grouped failure categories.

### MD-08 — permission reauthorization and retry

Attempt a batch containing files under an older read-only persisted tree permission, then reselect that folder with write access and retry the retained failures.

Pass criteria: the first attempt does not mutate denied files; successful unrelated files remain deleted; retry deletes only the remaining selected targets.

### MD-09 — playlists, shuffle, progress, and caches

Select files referenced in Favorites, multiple internal playlists, shuffled/duplicate queues, and Stage 9 progress records.

Pass criteria: each affected internal playlist is persisted once with all successful paths removed; external M3U files and `.lrc` sidecars are untouched; queue forms, progress, Room metadata, lyrics, and artwork are coherent.

### MD-10 — concurrent refresh and repeated confirmation

Start a refresh, submit a batch, and attempt rapid repeated actions.

Pass criteria: the shared library mutex serializes operations, controls disable while deleting, the provider receives no duplicate request, and only one final forced snapshot refresh runs.

### MD-11 — list scopes and changing data

Exercise selection from Songs, album, artist, album artist, genre, playlist, and folder song-list views. Remove or refresh a selected item externally before confirmation.

Pass criteria: selection is scoped to the current screen, selects only its visible list, and prunes IDs that leave that list without affecting another screen.

### MD-12 — singular deletion regression

With no selection active, open a song's overflow menu and run the complete Stage 6 singular deletion suite.

Pass criteria: **Delete from device**, its exact single-file confirmation, result messages, current-item behavior, provider failures, and one-file cleanup remain unchanged.

Automated Stage 10 coverage adds five atomic multi-ID queue-removal tests and five selection-policy tests, increasing the complete suite from 69 to 79 tests.

Candidate verification result: the true clean build passed 79/79 unit tests, Android lint, APK assembly, v2 signature, `minSdk 28`, `targetSdk 34`, checksum verification, and AndroidX Startup inspection. An intermediate candidate installed and cold-launched on the Motorola G57 Power. The non-destructive device smoke selected two songs, verified count/highlight/checkbox state, displayed both exact titles and paths in confirmation, canceled safely, and confirmed the original single-track menu remained intact. MD-01 through MD-12 remain the focused acceptance suite, especially real provider success and partial-failure cases.

## Stage 11 — physical filename renaming

### RN-01 — ordinary supported rename

Choose a disposable track, rename its base filename, and inspect it in Symphony and the Android Files app.

Pass criteria: the provider file has the new name with the exact original extension; Symphony updates immediately; no duplicate old/new song appears.

### RN-02 — queue, playback, and progress identity

Rename the current playing and paused long track after creating a Stage 9 checkpoint.

Pass criteria: playback does not reset to another item, queue/shuffle references remain valid, and the remembered position survives under the same logical song ID and new file fingerprint.

### RN-03 — playlists and derived views

Rename a file present in Favorites and multiple internal playlists, then browse Songs, Folders, albums, artists, and genres.

Pass criteria: internal playlists still resolve the track, the folder path uses the new name, and derived views contain one coherent item. External M3U content is unchanged.

### RN-04 — validation and conflict

Try blank, unchanged, `.` / `..`, slash/backslash-bearing, dotted base, and colliding names.

Pass criteria: invalid/conflicting inputs do not touch storage; dotted bases retain the old extension; clear feedback is shown.

### RN-05 — provider and permission failures

Exercise a provider without rename support and a folder whose persisted write permission was revoked.

Pass criteria: no cache, queue, playlist, or progress migration occurs unless the provider actually renames the document; reauthorization enables a clean retry.

Automated Stage 11 coverage adds five filename-policy tests, increasing the complete suite from 79 to 84 tests. The clean candidate passed all 84 tests, lint, APK assembly, v2 signature, `minSdk 28`, `targetSdk 34`, checksum, installation, and cold launch. RN-01 through RN-05 remain focused file-mutation acceptance.

## Stage 12 — embedded metadata and artwork editing

### TG-01 — core text tags

On disposable MP3, FLAC, M4A, Ogg Vorbis, and Opus files, edit filename, album, artists, album artists, composers, genres, and date/year.

Pass criteria: Symphony immediately uses the extension-free filename as title, the embedded `TITLE` property is removed, other values survive a forced rescan and app restart, and another tag-aware player sees the physical-file changes.

### TG-02 — multi-value semantics

Enter multiple values with semicolons/newlines and an artist name containing a comma.

Pass criteria: semicolon/newline entries become separate tag values where the format supports them, while the comma remains inside one value.

### TG-03 — numbering and blank removal

Set track/total and disc/total, attempt invalid input, then blank several previously populated fields.

Pass criteria: only non-negative integers can be saved; blank fields remove their exposed property; unrelated tags remain unchanged.

### TG-04 — lyrics

Add, replace, and remove embedded lyrics on supported formats, including a file with an external `.lrc` sidecar.

Pass criteria: embedded lyrics persist physically. An external sidecar remains untouched and may continue to take display precedence under Symphony's existing lyrics lookup.

### TG-05 — artwork

Replace artwork with JPEG/PNG, remove it, cancel a selection, try a non-image, and try an image over 20 MB.

Pass criteria: valid embedded artwork survives rescan/restart and appears in another player; removal is physical; canceled/invalid/oversized choices do not mutate the audio.

### TG-06 — active long-track edit

While playing and while paused several hours into a long podcast, edit a tag.

Pass criteria: the decoder is released before mutation; afterward the same queue item is rebuilt at the exact captured position with playing/paused intent preserved. The earlier region remains seekable.

### TG-07 — performance isolation

Measure launch, unchanged scan, browsing, playback start, and track transition before and after Stage 12 without opening the editor.

Pass criteria: no TagLib write/read path runs during these operations and there is no material regression. Cost is confined to opening/saving the explicit editor.

### TG-08 — provider, format, and interrupted save failures

Exercise revoked write permission, a non-seekable/read-only provider, an unsupported file, and an interrupted write on disposable copies.

Pass criteria: permanent failures stop without retry storms or user-facing result messages; transient provider failures receive no more than three total attempts; the player is restored when it was released, and a subsequent refresh reflects the physical truth.

### TG-09 — format and compatibility invariants

Compare the file extension, codec, duration, and playable audio before/after edits on Android 9 and a current Android device.

Pass criteria: no audio transcoding or format change occurs; duration/audio remain playable; Android 9 compatibility is retained.

### TG-10 — unified form and long-text cursor navigation

Open a song with a title, artist, or filename longer than the field width. Drag the caret and selection handle from the beginning through the final character, edit both filename and one tag, then save once.

Pass criteria: only one **Edit details** menu action exists; the separate rename action and Title field are absent; long text wraps and every character remains reachable; the extension stays protected; filename and tags commit through one atomic save.

### TG-11 — immediate dismissal and live propagation

On a disposable track visible in Songs, Search, an album/artist grouping, folders, the queue, now-playing UI, and Android's notification player, change its title and at least one grouping tag and press Save.

Pass criteria: the dialog disappears immediately with no saving animation or result toast; after the physical write succeeds, every open surface reflects the new values without rescan, navigation away, process restart, or app relaunch. Sorting/group membership and current media-session metadata also update. A rejected write does not publish false metadata.

Automated Stage 12 coverage includes six filename-policy, five metadata-policy, and three bounded-retry-controller tests, taking the complete suite to 93 tests. The latest candidate passed all 93 tests, Android lint, APK assembly, v2 signature, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum, and AndroidX Startup inspection. The exact artifact installed with data preserved and cold-launched in 2.035 seconds on the Motorola G57 Power, remained alive, and emitted no relevant runtime error. TG-01 through TG-11 remain focused real-write acceptance on disposable files.

## Stage 13 — minimal bottom-navigation selection

### BN-01 — labels disabled

Set bottom-bar label visibility to Invisible and move through every configured tab.

Pass criteria: no selection oval or label appears; the active filled icon is primary-colored and subtly larger; inactive icons remain muted and outlined; icon centers and bar height do not move.

### BN-02 — label modes

Repeat navigation with Always visible and Visible when active.

Pass criteria: existing label visibility semantics remain intact; the selected label is primary-colored and semibold; the icon treatment is identical to invisible mode.

### BN-03 — transition and input behavior

Navigate rapidly through adjacent tabs, tap the active tab, and use the existing swipe/tabs-sheet paths.

Pass criteria: the 160 ms icon transitions complete without flashing the old oval, overlapping icons, shifting touch targets, duplicate navigation, or changing the tabs-sheet behavior.

### BN-04 — theme and configuration

Check light/dark themes, another primary accent, portrait restart, and Android 9.

Pass criteria: selected color follows the theme, contrast remains legible, the active tab survives recreation, and no system-version-specific layout regression appears.

Stage 13 retained the 93-test suite; all tests, Android lint, and assembly passed. The exact APK passed v2 signature, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum, and AndroidX Startup inspection. It installed with the label preference preserved and cold-launched in 1.922 seconds on the Motorola G57 Power without a relevant runtime error. Real-device inspection confirmed the label-free selected state, and a subsequent Songs selection completed successfully. BN-01 through BN-04 remain the focused manual acceptance suite.

## Stage 14 — two-page bottom navigation

### PN-01 — hold, drag, cancel, and commit

With transition buttons disabled, touch and hold an empty or icon-adjacent area of the navigation bar, drag less than 18% of its width, and release. Repeat with a drag beyond the threshold in both directions.

Pass criteria: the long press produces one haptic and a restrained initial movement; a short drag returns to its source page; a committed drag follows the finger and snaps to the other page without opening a sheet, moving the mini-player, changing the active track, or leaving partial icons onscreen.

### PN-02 — navigation and Stage 13 styling

Navigate through all five destinations on each page with labels Invisible, Always visible, and Visible when active.

Pass criteria: each destination opens correctly; the selected item retains the transparent Stage 13 treatment, 28 dp primary filled icon, 24 dp muted outlined icons, stable centers, and label semantics. The two-dot indicator reflects the visible page and bar height remains stable.

### PN-03 — optional edge buttons

Enable **Transition buttons** in Settings - Home. Use the left and right controls repeatedly from both pages, then disable the setting.

Pass criteria: the controls appear without crowding or vertically resizing the bar, provide full 48 dp touch targets, animate through the same page transition, disable the unavailable direction, never wrap, and disappear immediately when the setting is disabled. The gesture remains available in both states.

### PN-04 — page assignment integrity

Open both page editors. Attempt to save fewer or more than five destinations, then save several valid complementary arrangements.

Pass criteria: invalid counts cannot be committed; changing either page produces exactly five unique destinations on each page; all ten destinations remain reachable once; no duplicate appears; and the navigation bar updates without restarting the app.

### PN-05 — migration, persistence, and external navigation

Upgrade from a build configured with two to four Home tabs, switch to page two, recreate/cold-start the activity, and open a destination whose assignment is on the hidden page through another navigation route.

Pass criteria: prior selections remain present and are completed deterministically; stored settings do not crash or lose a destination; the visible page persists when compatible with the active route; and external navigation reveals the page containing the active destination.

### PN-06 — rapid input and accessibility

Rapidly alternate taps, committed drags, canceled drags, and edge-button presses. Repeat on Android 9 and a current Android version, with TalkBack if available.

Pass criteria: no stale animation, duplicate page commit, accidental destination activation, stuck drag offset, modal sheet, or crash occurs. Icons and buttons expose meaningful destination/page descriptions and remain usable with system accessibility input.

Stage 14 adds three navigation-partition tests, taking the complete suite to 96 tests. The true clean build passed all 96, Android lint, assembly, v2 signature, `minSdk 28`, `targetSdk 34`, 16 KB alignment, checksum generation, and explicit AndroidX Startup Dex inspection. The exact APK installed and cold-launched on the Motorola G57 Power in 2.07 seconds without relevant errors. ADB smoke testing passed the core PN-01, PN-02, and PN-03 paths: hold-and-drag worked in both directions, both five-item pages were complete, the mini-player and bar height remained stable, and optional edge controls moved right and left without wrap. The Settings explanation and neighboring row text were measured at the identical `x=109` left edge. PN-04 page-assignment mutation, PN-05 legacy migration/persistence, and PN-06 Android 9/TalkBack stress remain focused manual regressions.

## Stage 15 — unified modal surfaces

### MS-01 — direct Home actions

From each Home page, use the new Rescan and Settings icons repeatedly, including while playback is active.

Pass criteria: both icons have distinct standard touch targets and accessible descriptions; their glyphs form a compact pair without overlapping hit areas; Settings opens directly; Rescan starts only one scan per accepted tap; the page-title midpoint matches the physical screen midpoint despite the asymmetric action count; playback, position and navigation state remain unchanged.

### MS-02 — grouped track actions

Open a track sheet from Songs, Search, Tree, Queue/detail lists and Now Playing. Exercise Favorite/Unfavorite, Play next, Add to queue, Add to playlist and conditional Play from beginning.

Pass criteria: one shared opaque sheet opens smoothly; artwork/title/artist are correct; four quick actions remain readable; RestartAlt is used for Play from beginning; actions execute once and dismiss the sheet; no per-row hidden sheet or new metadata/artwork query causes list or playback lag.

### MS-03 — navigation, details and destructive grouping

Use View artist, View album artist, View album, Share, Details and Edit details from representative tracks with zero, one and multiple artist values.

Pass criteria: every available target remains reachable; dynamic artist entries scroll without clipping; file/detail actions remain grouped; Edit details opens only after the action sheet is gone; Delete from device is isolated in the error colour.

### MS-04 — other entity context sheets

Open menus for albums, artists, album artists, playlists, folders and generic song collections from Home, Search and their detail pages. Exercise all non-destructive actions.

Pass criteria: every prior action remains present and behaves identically; the sheet title identifies its entity; long titles ellipsize safely; content scrolls on small screens; no floating dropdown remains.

### MS-05 — sorting and settings choices

Open every media sorting surface, Tree's dual sorter, responsive-grid controls and representative single/multi-choice Settings controls.

Pass criteria: each uses the shared compact sheet language; current choices are indicated correctly; long lists scroll; selecting or reversing a sort applies once; settings validation and minimum/maximum selection rules remain intact.

### MS-06 — information and playback controls

Open song/playlist information, speed, pitch, sleep timer, Now Playing extra options and representative settings slider/text/folder dialogs.

Pass criteria: all app-owned surfaces share the rounded opaque sheet, title treatment and insets; values and controls retain existing behavior; scrollable or lazy content is not clipped; no nested sheet remains after dismissal.

### MS-07 — singular and batch permanent deletion

Use a disposable file for singular deletion and several disposable files for batch deletion. Cancel once, then confirm. Include a device/API combination that requires Android's MediaStore authorization.

Pass criteria: Symphony's confirmation is a destructive sheet with the item/path or bounded batch list and **Delete permanently** action; Cancel changes nothing; confirmation invokes each deletion once; system authorization may follow; the bounded verifier reconciles the library without a full rescan or success toast.

### MS-08 — metadata editor and keyboard

Open Edit details for tracks with short and very long filenames/tags. Focus the first, middle, numeric and Lyrics fields; use keyboard Next; drag the cursor/selection handle to both ends; dismiss the keyboard; choose artwork; cancel and save in separate runs.

Pass criteria: the editor opens expanded; focused fields are automatically brought fully above the keyboard; Cancel and Save remain visible; Next advances through single-line fields; Lyrics accepts newlines; cursor dragging reaches all content; scroll position remains coherent; artwork-picker return preserves the draft; Save closes immediately and the existing verified write publishes changes in real time.

### MS-09 — modal workflow replacement

From a track sheet choose Add to playlist, then New playlist. Cancel once and complete once.

Pass criteria: only one modal layer exists at a time; cancel returns to the playlist picker; completion creates the playlist with the pending tracks already included; back/outside dismissal never leaves a stale scrim or unresponsive screen.

### MS-10 — configuration, accessibility and performance

Repeat representative sheets in light/dark themes, large system font, landscape where supported, Android 9 and a current Android version, with TalkBack if available. Rapidly open/dismiss sheets while playback and scanning continue.

Pass criteria: text, drag handles, actions and destructive colours remain legible; 48 dp targets and meaningful semantics are preserved; keyboard/system insets do not overlap content; playback does not stutter; no duplicate action, stacked modal, stuck scrim, state loss or runtime crash occurs.

Motorola G57 Power smoke testing passed MS-01's visual header path, the core MS-02/MS-03 track layout, MS-05 sorting, the non-destructive portion of MS-07, and the primary MS-08 IME path. On the exact packaged build, UI hierarchy inspection confirmed the **Songs** title midpoint is `x=539.5` on the 1080 px display—the nearest possible half-pixel midpoint to physical center—with compact 39 dp action-glyph spacing inside independent 48 dp button bounds. Inspection also confirmed all approved track actions, visible Cancel/Save controls above a genuinely shown keyboard, and Next focus movement from Filename to Artists. No destructive deletion or metadata write was executed. Destructive execution, all entity variants, Android 9 and TalkBack remain focused acceptance tests.

The true clean Stage 15 build passed 96/96 unit tests, Android lint and APK assembly. The packaged APK passed v2 signature, `minSdk 28`, `targetSdk 34`, 16 KB alignment, SHA-256 verification and AndroidX Startup Dex inspection. The exact APK installed and cold-launched in 2.189 seconds on the Motorola G57 Power with no relevant runtime error. Candidate SHA-256: `2d53eb6f7aa800407c1a61ebf079ce9fab487d5def2e6efa7dc89fb65e52f7d7`.

## Stage 16 — minimal multi-track selection

### TS-01 — enter, toggle, and exit

Long-press a track, select and deselect tracks from several list positions, then exit with Close and Android Back in separate runs.

Pass criteria: long press selects exactly the initiating track; one haptic is emitted; subsequent whole-row taps toggle once; the selected count stays accurate; Back/Close restores the normal sort bar and ordinary track-tap playback behavior.

### TS-02 — visual states

Compare selected and unselected tracks in light/dark themes and with multiple primary accent colours.

Pass criteria: selected rows remain flat with no rounded container; the 8% tint and 3 dp leading accent are visible but restrained; artwork is never obscured; unselected tracks use one muted hollow ring; selected tracks use a primary hollow ring plus compact centre dot with a clear gap and no tick.

### TS-03 — contextual toolbar and transitions

Repeatedly enter and leave selection mode, including rapid toggles near the final selected item.

Pass criteria: sort and contextual controls crossfade/slide without changing list width, jumping scroll position, stacking content, or leaving a stale divider. Count text is semibold; the original Select all icon remains; Delete is the only error-colour action; all controls retain 48 dp targets.

### TS-04 — Select all lifecycle

Use Select all on short and long lists, verify the accessibility description, deselect individual items, and activate Deselect all.

Pass criteria: every unique visible song is selected once; the icon remains unchanged; its description changes between Select all and Deselect all; partial selection updates correctly; no duplicate ID or stale selected row remains after library changes.

### TS-05 — playback and deletion regression

Enter selection while another track is playing, scroll extensively, cancel once, then use disposable tracks to exercise batch deletion and any required Android authorization.

Pass criteria: playback and mini-player remain uninterrupted; current-playing colour remains distinct from selection; Cancel deletes nothing; confirmed deletion uses the existing Stage 10/12 verified pipeline exactly once and reconciles the list without a full rescan or success toast.

### TS-06 — accessibility and stress

Repeat with large font, TalkBack, Android 9, and a current Android version while rapidly selecting items and changing library contents.

Pass criteria: rows report selected/not-selected semantics; indicators remain legible; titles do not collide with the trailing 48 dp indicator region; no index mismatch, stuck selection, duplicate action, frame stall, or crash occurs.

Motorola G57 Power smoke testing passed TS-01, the dark-theme portion of TS-02, the primary TS-03 path, and Select all to 7/7 tracks with the Deselect all accessibility state in TS-04. Back exited selection successfully, the original Select all icon remained in place, and no deletion was invoked.

The true clean Stage 16 build passed 96/96 unit tests, Android lint and APK assembly. The packaged APK passed v2 signature, `minSdk 28`, `targetSdk 34`, 16 KB alignment, SHA-256 verification and AndroidX Startup Dex inspection. The exact APK installed and cold-launched in 2.168 seconds on the Motorola G57 Power with no relevant runtime error. Candidate SHA-256: `369b99fdcd50fee23ff01a80ec4fe79d5d8b87502284597d9c71adfb4366aedf`.

## Stage 17 — repeat persistence, artwork compatibility, and audio interruptions

### RP-01 — every repeat mode across process death

Select repeat off, repeat queue, and repeat one in separate runs. For each mode, close Symphony, force-stop its package, wait briefly, cold-launch, and open Now Playing.

Pass criteria: the exact selected mode and icon/content description return every time; no mode resets to the default and no playback begins merely because the mode was restored.

### RP-02 — repeat mode independent of queue lifecycle

Select each repeat mode, then exhaust, clear, replace, reorder, rescan, and cold-restore the queue where applicable.

Pass criteria: queue mutations never overwrite the preference; automatic last-to-first navigation still follows the selected mode; previous/next hardware and Bluetooth controls retain the Stage 7 boundary logic.

### AR-01 — supported artwork formats

Use disposable tracks with valid embedded JPEG, PNG, GIF, BMP, WebP, HEIF, and HEIC artwork. Cover uppercase/lowercase MIME values, MIME parameters, and common aliases where the tag format permits them.

Pass criteria: every still image is recognized without a full rescan loop, displays in lists/Now Playing/editor, and does not delay audio preparation.

### AR-02 — artwork replacement and persistence

Through Edit details, replace artwork with representative JPEG, PNG, WebP, HEIF, and HEIC files, then cold-launch and rescan.

Pass criteria: Save closes immediately; the new art appears in real time, survives cold launch/rescan, and playback position and intent remain intact. Provider MIME omissions fall back to decoded image detection.

### AR-03 — malformed and non-image input

Attempt artwork selection with corrupt image data, an image filename carrying a non-image MIME, and WebM media.

Pass criteria: no invalid artwork is written, existing artwork remains intact, the editor and player do not crash, and WebM is not misclassified as a still cover image.

### AF-01 — settings migration and presentation

Upgrade installations previously configured with legacy ignore-focus off and on, then open Settings → Player → Audio interruptions.

Pass criteria: off migrates to **Pause and resume automatically**, on migrates to **Keep playing**; only one audio-interruption row is shown; the legacy Require/Ignore switches are absent; both choices and overlap warning are readable at large font sizes.

### AF-02 — pause and resume automatically

Choose **Pause and resume automatically** and interrupt Symphony with temporary audio from several unrelated apps, navigation prompts, calls/communications where safe, and short notification sounds.

Pass criteria: behavior follows the Stage 1 focus matrix; temporary loss pauses and resumes only playback that Symphony paused, permanent loss remains stopped, and manual pause during interruption suppresses automatic resume.

### AF-03 — keep playing

Choose **Keep playing** and repeat representative transient and full-focus interruptions.

Pass criteria: Symphony remains playing without focus-loss pause; overlap is expected; changing back to **Pause and resume automatically** takes effect without app restart and restores the Stage 1 behavior.

The clean Stage 17 build passed 101/101 unit tests, Android lint, and APK assembly. Automated coverage includes legacy audio-interruption migration and artwork MIME/alias classification. The exact artifact passed v2 signature, `minSdk 28`, `targetSdk 34`, 16 KB alignment, checksum equality, and AndroidX Startup Dex inspection. Repeat queue survived physical package force-stop/cold restart, and the consolidated settings surface passed on-device inspection. Exact candidate SHA-256: `f330a0863afc1bce4e6c50e3bbdaeb9a6adbc91a9fd03ebe191f771a8bf490b2`.

## Stage 18 — compact navigation and modern queue

### CN-01 — navigation height and label modes

Exercise Always visible, Visible when active, and Invisible label modes at default and large system font scales.

Pass criteria: labelled content is 64 dp and label-free content is 56 dp before the mandatory system inset; icon-to-label spacing is 2 dp; text does not clip; active-only icons do not jump; all items retain at least 48 dp touch targets.

### CN-02 — navigation paging regression

With labels enabled and disabled, use hold-and-drag in both directions, the optional edge buttons, external destination navigation, and process restoration.

Pass criteria: both five-item pages remain complete and duplicate-free; dots, clipping, sticky drag, persistence, active icon animation, edge-button direction, and stationary mini-player behavior match Stage 14.

### CQ-01 — queue normal state

Open Queue with the current item at the beginning, middle, and end. Tap several rows and open representative per-track action sheets.

Pass criteria: indices appear outside artwork; the current row alone carries the waveform, subtle tint, accent, and primary title; played rows are subdued but legible; tapping jumps once; grouped track sheets open without modal stacking.

### CQ-02 — queue selection lifecycle

Long-press current, played, and upcoming rows; toggle several rows; use Select all, Deselect all, Exit selection, and Android Back.

Pass criteria: long-press selects exactly one row; ring-and-dot indicators and row tint match Stage 16; artwork remains unobstructed; count and full-row toggles stay correct; exiting changes neither queue nor playback.

### CQ-03 — add selected to playlists

Select multiple non-adjacent queue items. Cancel the picker, add them to an existing disposable playlist, and repeat through New playlist.

Pass criteria: cancellation preserves selection and makes no mutation; selected IDs retain queue order; existing playlist receives each selected occurrence once; New playlist opens without stacked sheets and contains the selected items; successful completion clears selection but does not alter the playback queue.

### CQ-04 — queue removal and clearing

Using disposable queue state, remove selected tracks before, at, and after the current item. Separately invoke Clear queue.

Pass criteria: selected removal uses the existing radio queue operation once, recalculates the active index correctly, and does not delete device files; Clear queue stops playback and empties the queue; destructive actions remain error-coloured.

### CQ-05 — save whole queue and state changes

Save the entire queue to a new playlist, then change tracks while Queue is open and mutate queue contents through other supported actions.

Pass criteria: the saved playlist preserves complete queue order; active and played styling updates in real time; stale selected indices are discarded after queue shrink; no duplicate key, index error, crash, or unexpected scroll jump occurs.

Motorola G57 Power smoke testing passed the labelled CN-01 state, CQ-01 visual/current-item state, CQ-02 long-press/Select all/Exit paths, and CQ-03 picker/cancellation path on the exact artifact. No playlist mutation, selected removal, or Clear queue was executed.

The true clean Stage 18 build passed 101/101 unit tests, Android lint, and APK assembly. The exact artifact passed v2 signature, `minSdk 28`, `targetSdk 34`, 16 KB alignment, checksum equality, and AndroidX Startup Dex inspection. It installed and cold-launched in 2.195 seconds with a live process and no fatal runtime error. Exact candidate SHA-256: `20f3e45f63bfb9d1d38002e49137c9fde6d6912301fee330cceabf4e5a39f70c`.

## Stage 19 — complete legacy-UI convergence

### LU-01 — expanded lyrics sheet

Set Lyrics layout to Separate page, open lyrics from Now Playing, seek within the
sheet, use playback controls, change tracks, and dismiss by Close, Back, and downward
gesture.

Pass criteria: only one modal layer exists; lyrics, active-line following, seeking,
and controls remain live; dismissal returns to unchanged Now Playing state.

### LU-02 — playlist selectors

Open Manage songs for an internal playlist and Add to playlist for one and several
tracks.

Pass criteria: artwork is never covered; management uses flat tint and trailing
ring-and-dot state; membership appears at the row edge; cancellation and New playlist
behavior remain unchanged.

### LU-03 — speed, pitch, and sleep timer

Exercise every preset, arbitrary slider values, persistence toggles, custom sleep
duration, quit-on-end, reset, stop, cancel, and done.

Pass criteria: active presets update immediately; switches reflect stored state;
timer math and playback parameters are unchanged; sheets never stack or close
unexpectedly.

### LU-04 — settings editors

Test every single-choice setting, both Home navigation page editors, numeric presets,
ignored-folder lists, folder addition/removal, custom extensions, and list reordering.

Pass criteria: flat selection state is unambiguous; every original value can still be
selected, ordered, added, removed, cancelled, and committed; long content scrolls
without clipping.

### LU-05 — introduction and transient feedback

Test the first-launch update switches, copy a detail value, provoke only safe
non-destructive import/share/equalizer/artwork errors, and trigger update feedback
when available.

Pass criteria: no Android Toast appears; one app snackbar appears above navigation
and retains an early message; disabled onboarding choices cannot be changed.

The Stage 19 candidate passed 101/101 automated unit tests, Android lint, compilation,
assembly, v2 signature verification, SDK 28/34 inspection, page-aware ZIP alignment,
installation, launch, live-process, and AndroidRuntime crash checks. Destructive
operations were excluded from automated device testing. Candidate SHA-256:
`2684467a124da7e14b3c61a837f8ec064a523e84be953ce1f40ba9e9eee4928e`.

## Automated checks required for every candidate APK

Run and record:

- `testDebugUnitTest` with zero failures.
- `lintDebug` with zero errors.
- `assembleDebug` from a clean build whenever playback dependencies or packaging change.
- APK signature verification.
- Manifest verification for `minSdk 28` and the intended target SDK.
- SHA-256 generation and verification after copying into `artifacts/test`.
- ADB installation with app data preserved when appropriate.
- Cold launch followed by an AndroidRuntime crash-log check.
- For builds containing AndroidX Startup, confirm that the final APK contains a real `androidx.startup.R$string` class before installation.

The completed Stage 3 result is 25 unit tests passed, Android lint passed, clean APK assembly passed, v2 signature verified, `minSdk 28` verified, Startup class verified, repeated physical cold startup verified, and focused user acceptance passed on the Motorola G57 Power. The final diagnostic launch completed cached lookup in 69 ms and preparation/restored seeking in 926 ms without crash, ExoPlayer, or duplicate-path errors.

## Final integrated regression order

After the remaining problems are implemented:

1. Install the final candidate while preserving realistic app data and a saved long-track position.
2. Run smoke tests for launch, library visibility, playback, pause, track change, and notification controls.
3. Run the complete Stage 1 audio-focus suite.
4. Run the complete Stage 2 seek and playback suite.
5. Run the complete Stage 3 cold-restoration suite.
6. Run the later-stage checklists that will be appended to this document.
7. Repeat cold-start, restored-position, and multi-hour tests after all other suites.
8. Capture final APK metadata, checksum, test results, device details, and any accepted limitations.
9. Only then promote the APK from `artifacts/test` into its successful `artifacts/timeline` stage folder or final release location.
