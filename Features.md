# Symphony feature integration plan

This document tracks capabilities added after the five core problems in `Problems.md` were resolved. Stage 6 onward is feature work: new integrations, usability enhancements, and optimizations built on the stabilized playback and library architecture.

## Status legend

- **Discussing**: requirements or tradeoffs are still being decided.
- **Planned**: behavior and implementation direction are agreed.
- **In progress**: implementation has started.
- **Ready for device testing**: implementation, automated tests, lint, packaging, installation, and smoke testing passed; focused phone acceptance is pending.
- **Implemented**: focused device behavior has been accepted.

## Feature design principles

- Preserve Android 9+ support (`minSdk 28`).
- Produce one checksum-verified APK after each feature stage.
- Extend the existing Radio/Groove architecture rather than duplicating playback or library state.
- Keep music, podcasts, and multi-hour audio stories equally supported.
- Keep automatic completion, manual transport commands, and Android audio focus as distinct policies.
- Treat provider failures and unavailable capabilities explicitly rather than hiding them behind destructive rescans or timing workarounds.
- Record feature regression coverage in `tests.md` and architectural decisions in `memory.md`.

## Feature 1 / Stage 6: Provider-aware in-app audio deletion

**Status:** Ready for device testing

### Purpose

Allow completed podcasts, stories, and music files to be permanently deleted from Symphony without switching to a file manager, while keeping playback, queues, playlists, and the live library coherent.

### Implemented behavior

1. Media-folder selection persists read plus write Storage Access Framework permission when available, with a safe read-only fallback.
2. `DocumentFileX` exposes provider flags and deletion proceeds only when the provider advertises delete support.
3. **Delete from device** is available from the shared song menu used by library lists and Now Playing.
4. Confirmation displays the exact title and storage path and warns that deletion is permanent.
5. `DocumentsContract.deleteDocument()` runs before Symphony changes any internal reference.
6. Permission denial, unsupported providers, missing documents, and provider failures return distinct results without partially mutating app state.
7. Deletion and Stage 5 library refreshes share one serialized library transaction.
8. Successful deletion removes every duplicate reference from original and shuffled queue forms.
9. Deleting the current item selects an available replacement while preserving playing versus paused intent; deleting the only item stops cleanly.
10. Internal playlist paths are removed and persisted. External M3U files are not rewritten automatically.
11. Room metadata plus private artwork and lyrics caches are cleaned before one forced snapshot refresh.
12. Matching `.lrc` sidecars remain untouched.
13. The current scope is single-file deletion; batch deletion is deferred.

### Permission and provider constraints

- Folders authorized by older app builds may need to be selected again once to obtain write permission.
- Some document providers are inherently read-only or do not advertise deletion support.
- A provider-confirmed failure must leave the file reference, queue, playlists, and library consistent.

### Selected policies

- Current-item deletion advances to the next available queue entry when possible.
- Playing or paused intent is preserved across replacement.
- Deleted paths are removed permanently from internal playlists.
- External M3U playlists and `.lrc` sidecars remain unchanged.

### Verification state

- The corrected candidate passed 39/39 unit tests, Android lint, APK assembly, v2 signing, `minSdk 28`, `targetSdk 34`, and AndroidX Startup class inspection.
- It installed with data preserved and cold-launched successfully on the Motorola G57 Power.
- An earlier package that expanded the generated translation serializer failed Android verification and was withdrawn; it is not an accepted artifact.
- Focused deletion acceptance remains pending. The complete `DL-01` through `DL-10` suite is in `tests.md`.

### Archived candidate

`artifacts/timeline/stage-06-in-app-deletion/Symphony-stage-6-in-app-deletion-debug.apk`

SHA-256:

`9d5eece3fa62fc82a8c6f5cc501f5371cf01d5580d71dd80cd5a67c355baafaa`

## Feature 2 / Stage 7: Cyclic manual queue navigation

**Status:** Ready for device testing

### Purpose

Remove manual queue boundaries so Previous and Next can move continuously through the current queue, independently of the selected automatic repeat mode.

### Final selected behavior

- Previous from the first entry selects and plays the final entry.
- Next from the final entry selects and plays the first entry.
- Navigation is cyclic regardless of whether an endpoint was reached manually or through automatic completion.
- After a track has played for more than three seconds, the first Previous press still restarts it at `0:00`; a subsequent press navigates to the preceding entry.
- Empty queues are inert.
- Single-entry queues seek/restart at zero without reconstructing the player.
- Repeat Off, Repeat Queue, and Repeat One govern automatic completion only and never change because of a manual transport command.

### Implementation

1. `RadioQueueNavigation` is a stateless index planner using only current index and current queue size.
2. Previous resolves index `0` to `lastIndex`; Next resolves `lastIndex` to index `0` for queues containing at least two entries.
3. `Radio.jumpToPrevious()`, `jumpToNext()`, and both capability checks share the planner.
4. The earlier automatic-wrap provenance and structural queue-revision machinery were removed completely.
5. Queue replacement, shuffle, deletion, and library refresh require no history invalidation because no navigation history exists.
6. UI, notification, media-session, swipe, and compatible Bluetooth commands use the same helpers.
7. Raw media-button handling recognizes Previous/Skip Backward and Next/Skip Forward without double-firing on key-up or repeated key-down events.
8. Rewind and Fast Forward remain relative seeks.
9. Cold restoration now starts the observatory and media session before the asynchronous cached-queue restore can emit player events.
10. `RadioSession.start()` performs an initial snapshot synchronization, so a fast restore cannot be lost through the non-replaying update stream.
11. Core metadata, playback state, supported actions, and session activation are published synchronously. Artwork remains asynchronous and can no longer delay Bluetooth transport readiness.
12. Session teardown invalidates pending artwork work, unsubscribes the update listener, and releases `MediaSessionCompat`, preventing stale process-lifecycle sessions.

### Bluetooth device finding

During isolated ADB captures, Symphony was confirmed as Android's active media-button session. The connected **Boult Audio Airbass** configuration nevertheless emitted `KEYCODE_MEDIA_NEXT` for both the tested left and right gestures, with identical `deviceId=-1` and `source=0` values. Symphony cannot distinguish the sides when the earbuds transmit identical commands. Cyclic Next works normally, but left=Previous requires the earbuds to emit a distinct Previous command.

The regular gray repeat icon is `LoopMode.None`, not Repeat One. Repeat One uses the icon containing `1`. Manual cyclic navigation is deliberately independent of that icon and automatic completion mode.

### Verification state

- The cold-hardened cyclic candidate passed 54/54 unit tests, Android lint, APK assembly, v2 signing, `minSdk 28`, `targetSdk 34`, checksum verification, and AndroidX Startup class inspection.
- It installed with app data preserved on the Motorola G57 Power and completed a forced cold launch in 1,874 ms without a relevant runtime error.
- The media session published `Restoring` before decoder preparation completed, then `Seeking` and `Ready`; restored seeking completed in 664 ms. When playback started, Android selected Symphony as the media-button session and routed a standard external Pause command to it successfully.
- Focused cyclic-navigation and genuine multi-hour acceptance remain pending. The complete `CQ-01` through `CQ-13` suite is in `tests.md`.

### Archived candidate

`artifacts/timeline/stage-07-cyclic-queue-navigation/Symphony-stage-7-cyclic-queue-navigation-debug.apk`

SHA-256:

`6e58f3f90c27516e0608580b74c8821773fe9f1040992f68281d632564287e5b`

## Feature 3 / Stage 8: Persistent pinned media controls

**Status:** Ready for device testing

### Purpose

Keep Android's pinned media player useful after Symphony's activity is dismissed, instead of leaving a visually present but disconnected set of controls.

### Implemented behavior

1. `SymphonyApplication` owns the shared `Radio` instance, decoupling playback and media-session lifetime from `MainActivity`.
2. The playback service is not stopped when the task is removed and returns `START_STICKY`, allowing Android to retain or recreate its media role.
3. Active or paused media is promoted to a foreground service with the existing media-style notification; an empty session leaves foreground state cleanly.
4. Task removal no longer releases the player, session, or command callbacks.
5. Notification, lock-screen, Bluetooth, and other media controls continue through the same live `RadioSession`.
6. The change does not make a user-stopped empty player permanently resident.

### Verification state

- The clean candidate passed 59/59 unit tests, Android lint, APK assembly, v2 signing, SDK checks, checksum verification, and AndroidX Startup inspection.
- On the Motorola G57 Power, dismissing the task left no Symphony activity/task but retained the same process, foreground playback service, and active media session.
- External Pause, Play, and Pause commands remained functional after task removal.
- Longer idle/cold-system acceptance remains part of the Stage 8 regression suite in `tests.md`.

### Archived candidate

`artifacts/timeline/stage-08-persistent-pinned-media-controls/Symphony-stage-8-persistent-pinned-media-controls-debug.apk`

SHA-256:

`c92286cc94904c9c8c816545ebb9c70a014e68e83dc0ca9e80729c4472277016`

## Feature 4 / Stage 9: Optional long-track position retention

**Status:** Ready for device testing

### Purpose

Let power users resume podcasts and audio stories without imposing remembered positions on ordinary music tracks.

### User-facing behavior

- Position retention is off by default.
- **Settings → Player → Playback progress** contains a **Remember track positions** switch.
- When enabled, the user chooses a minimum eligible duration from 5 to 180 minutes in five-minute steps; the default is 20 minutes.
- Positions below 10 seconds and positions effectively at completion are cleared rather than restored.
- The completion window is the larger of 30 seconds or 1% of track duration, which scales safely to multi-hour stories.
- A remembered track exposes **Play from beginning** in its song menu.
- **Clear remembered positions** removes all long-term checkpoints.
- No “Resumed from…” snackbar, toast, or other resume message is shown.
- Current-session restoration remains separate and can still recover the active track after an app/service restart even when optional long-term retention is disabled.

### Architecture

1. `PlaybackProgressPolicy` contains pure eligibility, clearing, restoration, completion-window, and file-fingerprint rules.
2. `PlaybackProgressStore` uses a dedicated migration-free preferences file and stores one entry per song ID.
3. Each entry includes exact `Long` position/duration values plus path, file size, and modification time; replaced files cannot inherit stale progress.
4. `RadioPlaybackProgress` checkpoints every 15 seconds during playback and immediately on pause, seek, track transition, and lifecycle persistence.
5. Checkpoints are keyed to the actual player song ID, preventing queue mutations from assigning progress to the wrong track.
6. Explicit current-session positions take precedence over optional remembered positions.
7. Library refresh and deletion remove orphaned entries.
8. Clearing progress suppresses an immediate rewrite for the current track until it changes.

### Verification state

- The true clean build passed 69/69 unit tests, Android lint, APK assembly, v2 signing, `minSdk 28`, `targetSdk 34`, checksum verification, and AndroidX Startup inspection.
- The exact APK installed successfully on the Motorola G57 Power and launched with no fatal runtime error.
- The complete settings section rendered correctly on-device with the 20-minute default and no resume message.
- With retention enabled, an 82-minute Opus story produced a file-fingerprinted checkpoint at approximately 61 seconds. Switching away and returning restored that track beyond the checkpoint through the shared media-session path.
- Full threshold, completion, replacement, deletion, clearing, and multi-hour acceptance remains in the Stage 9 suite in `tests.md`.

### Archived candidate

`artifacts/timeline/stage-09-track-position-retention/Symphony-stage-9-track-position-retention-debug.apk`

SHA-256:

`d1cfbb047d1acb6a809dd8445a935ccc6c7b2acc420492d9f5a192838e920682`

## Feature 5 / Stage 10: Long-press multi-track deletion

**Status:** Ready for device testing

### Purpose

Allow users who frequently remove completed podcasts, stories, or music to select several visible tracks and delete their underlying files in one deliberate operation, while preserving Stage 6's existing per-track **Delete from device** action.

### User-facing behavior

- Long-pressing a track in a standard song list enters selection mode and selects that underlying file.
- While selection is active, normal track taps toggle selection instead of starting playback.
- Selected rows receive a highlighted background and checkbox; row overflow and favorite actions are hidden until selection ends.
- The selection bar shows the exact count and provides Exit, Select all/Deselect all, and Delete actions.
- Android Back exits selection mode without navigating away or changing files.
- Select all applies only to the current visible song list. Duplicate references collapse to one underlying song/file.
- The destructive confirmation displays the number of tracks plus every selected title and storage path in a scrollable list.
- Cancel is inert.
- During deletion the controls are disabled and display progress, preventing repeated confirmation.
- Complete success exits selection. After partial provider failure, successfully deleted tracks disappear and failed tracks remain selected for inspection or retry.
- The original single-song overflow menu and its separate confirmation/result behavior remain intact.

### Batch transaction

1. All requested IDs are deduplicated and processed within the existing serialized library transaction.
2. Each document independently receives the Stage 6 existence, write-permission, provider-capability, and `DocumentsContract.deleteDocument()` checks.
3. A failed item does not prevent other supported selected files from being deleted.
4. Only provider-confirmed successes participate in Symphony state cleanup.
5. Successful IDs are removed from original and shuffled queue forms in one atomic plan.
6. If the current item was deleted, one surviving replacement is chosen after the whole batch is known; Symphony never briefly stages another selected-for-deletion track.
7. Playing versus paused intent is preserved. Deleting the entire queue stops cleanly.
8. Internal playlists are rewritten once per affected playlist; external M3U files remain untouched.
9. Room metadata, lyrics, artwork, and Stage 9 progress are removed for every successful item.
10. One forced snapshot refresh reconciles the final storage state after the complete batch.

### Verification state

- The true clean candidate passed 79/79 unit tests, Android lint, APK assembly, v2 signing, `minSdk 28`, `targetSdk 34`, checksum verification, and AndroidX Startup inspection.
- The intermediate build installed and cold-launched on the Motorola G57 Power.
- A non-destructive phone smoke test selected two tracks, verified both checkbox/highlight states, opened a confirmation containing both exact titles and paths, and canceled without deleting any file.
- The original single-track **Delete from device** menu item was rechecked afterward and remained available.
- Provider-confirmed multi-file deletion and partial-failure acceptance remain in the Stage 10 suite in `tests.md`.

### Archived candidate

`artifacts/timeline/stage-10-multi-select-deletion/Symphony-stage-10-multi-select-deletion-debug.apk`

SHA-256:

`0e75e4ac56a0b0b6f29a204d0b51ce7f74c21d306c17e5056340eec118d74844`

## Future feature work

New capabilities after Stage 10 should be added here under one of these categories:

- **Feature integration:** a new user-facing capability.
- **Enhancement:** a meaningful expansion or refinement of existing behavior.
- **Optimization:** measurable improvements to latency, resource use, battery impact, scalability, or robustness without changing the core user contract.

The five fixed foundational defects remain in `Problems.md`; future feature work should not be added there unless it exposes a genuine regression or new core defect.

## Feature 6 / Stage 11: Physical audio-file renaming

**Status:** Ready for focused device testing

### Purpose

Rename an audio file from Symphony and make the change visible to Android storage and other apps without turning the file into a new logical track.

### User-facing behavior

- In the current integrated build, filename editing is the first field inside the single **Edit audio details** form; Stage 11's separate prototype menu action is no longer shown.
- The field edits only the base filename and explicitly preserves the original extension. Renaming cannot be mistaken for audio format conversion.
- Blank names, path separators, reserved `.` / `..` names, unchanged names, and visible-library conflicts are rejected before storage mutation.
- Provider-specific missing-file, permission, unsupported-operation, conflict, and failure results are reported.

### Coherent identity migration

1. The provider rename runs inside the same serialized library transaction used by refresh and deletion.
2. `DocumentsContract.renameDocument()` performs the physical storage operation only when the provider advertises rename support.
3. The returned URI and actual provider display name are used rather than assuming the requested result.
4. The renamed file is reparsed while retaining the original Symphony song ID.
5. Internal playlists replace the old path in place; external M3U files remain external and untouched.
6. A valid remembered position migrates to the new path, modification time, size, and duration fingerprint.
7. Queue and shuffle identities remain valid because the song ID is unchanged.
8. One forced snapshot refresh reconciles folder browsing, derived album/artist/genre repositories, and URI maps.

### Verification state

- Five filename-policy tests cover extension preservation, dotted base names, extensionless files, invalid/reserved names, trimming, and unchanged-name detection.
- The true clean candidate passed 84/84 unit tests, Android lint, APK assembly, v2 signing, `minSdk 28`, `targetSdk 34`, and checksum verification.
- The exact APK installed and cold-launched on the Motorola G57 Power without an AndroidRuntime crash.
- A real provider rename and active-playback continuity remain in the Stage 11 focused suite in `tests.md`.

### Archived candidate

`artifacts/timeline/stage-11-file-renaming/Symphony-stage-11-file-renaming-debug.apk`

SHA-256:

`7e8a0b49a39979b9805fe7c57df47f684ee79abab3e270b19d584e8baee51e8a`

## Feature 7 / Stage 12: Embedded audio metadata and artwork editing

**Status:** Ready for focused device testing

### Purpose

Allow edits to travel with the real audio file and remain visible to other players, file indexers, and future Symphony scans instead of storing a private display-only override.

### User-facing behavior

- The song menu contains one **Edit details** action for both physical filename and embedded tag/artwork changes.
- Editable fields are filename, artists, album, album artists, composers, genres, date/year, track number/total, disc number/total, lyrics, and embedded artwork.
- Filename is the single display-title source globally. Parsers and repositories normalize cached, restored, scanned, and edited songs to the extension-free filename. Saving also removes the embedded `TITLE` property so future reads preserve the same presentation.
- Long textual values wrap to their natural height instead of remaining clipped in a single-line viewport, keeping the caret and selection handle reachable at the end of the text.
- Artists, album artists, composers, and genres accept semicolon-separated values. Commas remain valid inside a single name.
- Artwork can be replaced with a validated image up to 20 MB or removed.
- The original extension and encoded audio format are unchanged.
- Save dismisses the editor immediately and continues in Symphony's application scope. Edit and deletion operations do not display completion or failure toasts.

### Performance and integrity design

1. Metadata writing is user-triggered only. Library scans, browsing, playback startup, and ordinary track changes do not load or invoke the writer.
2. TagLib Android 1.0.5 is pinned because it passes Symphony's compile-SDK-35 gate while retaining Android 9 compatibility. Version 1.0.6 was rejected because its AAR requires compile SDK 37.
3. The writer uses the persisted Storage Access Framework file descriptor directly, avoiding a full 500 MB–1 GB copy merely to update a small tag block.
4. Existing properties are read first and copied; only exposed fields are replaced, so unrelated supported tags remain present.
5. Numeric fields reject invalid or negative values. Blank exposed fields deliberately remove that property.
6. If the target is active or preloaded for gapless playback, Symphony releases the relevant decoder before writing. The active track is restored at its exact position with playing/paused intent preserved after the atomic live update.
7. The stable song ID, queue identity, and remembered progress migrate across the new modification fingerprint.
8. After a confirmed write, Symphony projects the values it just wrote into the stable `Song`, persists that exact row, and publishes it directly to the live song, album, artist, album-artist, genre, folder, playlist, and media-session indexes. It no longer blocks on a metadata reparse or whole-library scan.
9. Filename and metadata edits share one library transaction and one decoder release/restore cycle. If a provider completes the rename but rejects a later tag write, Symphony reconciles the real renamed state and reports a partial result.
10. A lightweight verifier rereads the exposed metadata/artwork after writing and re-queries deleted documents. Transient provider failures or mismatches receive at most two retries after 120 ms and 350 ms; permanent permission, conflict, missing-source, and unsupported-provider states stop immediately. Successful deletions update all repositories directly instead of forcing a media-tree rescan.

### Verification state

- Six filename-policy tests and five metadata-policy tests cover extension/control-character validation, semicolon/newline splitting, blank removal, comma preservation, and optional non-negative numeric validation.
- The latest candidate passed 93/93 unit tests, Android lint, APK assembly, v2 signing, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum verification, and AndroidX Startup inspection.
- The exact APK installed and cold-launched in 2.035 seconds on the arm64 Motorola G57 Power without an AndroidRuntime crash.
- Real writes across disposable format samples and active-playback restoration remain in the Stage 12 focused suite in `tests.md`.

### Current candidate

`artifacts/test/Symphony-stage-12-metadata-editing-debug.apk`

SHA-256:

`9eb08eab42877c9de66daad729d97753826d94d1e2e77bac4dc3c330e2304de4`

## UI/UX Feature 1 / Stage 13: Minimal bottom-navigation selection

**Status:** Ready for focused device testing

### User-facing behavior

- The large Material 3 oval selection indicator is removed.
- The selected tab uses Symphony's primary accent color and a 28 dp filled icon.
- Unselected tabs use 24 dp outlined icons and the existing muted color.
- Every icon remains centered in the same invisible 32 dp container, preventing movement as selection changes.
- Size, color, and filled/outlined state transition over 160 ms.
- With labels enabled, the selected label uses the accent color and semibold weight.
- With labels disabled, selection remains clear through icon color, size, and filled state alone.
- Navigation height, touch targets, ordering, mini-player, tab sheet, and navigation behavior are unchanged.

### Verification state

- The integrated build passed 93/93 unit tests, Android lint, and APK assembly.
- The exact artifact passed v2 signing, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum verification, and AndroidX Startup inspection.
- It installed with app data and the label-visibility preference preserved, cold-launched in 1.922 seconds, and remained alive without a relevant startup error.
- A real-device screenshot confirmed the label-free Albums state has no oval, uses the larger cyan selected icon, retains equal icon positions, and preserves the compact bar height.
- Selecting Songs through the existing tabs sheet completed successfully and left the process alive.

### Archived candidate

`artifacts/timeline/stage-13-minimal-navigation/Symphony-stage-13-minimal-navigation-debug.apk`

SHA-256:

`90c9d6442c78cc1037693905c6105bf26e8b38967f185ff003d6d7a6cd7180a9`

## UI/UX Feature 2 / Stage 14: Two-page bottom navigation

**Status:** Implemented and device-smoke-tested

### User-facing behavior

- The old long-press modal sheet and upward expansion path are removed.
- The ten Home destinations are divided into two horizontal pages of five while retaining Stage 13's transparent indicator, 24/28 dp outlined/filled icons, primary accent, fixed icon centers, and 160 ms selection transition.
- A tiny two-dot indicator identifies the visible page without adding labels or increasing the navigation-bar height.
- Touching and holding the bar triggers the platform long-press haptic and a restrained 6 dp movement. Continuing to drag tracks the two-page strip directly; releasing beyond 18% of the bar width commits a 220 ms snap, while a shorter drag returns to the source page.
- The navigation strip is clipped at its bounds. The mini-player remains stationary while pages move.
- The visible page is persisted. A destination opened through another route automatically reveals the page containing that destination.
- Settings - Home now exposes **Navigation page 1** and **Navigation page 2**. Each must contain exactly five destinations; changing either derives the complementary page, so duplicates and unreachable destinations cannot be saved.
- Legacy configurations with two to four Home tabs retain their existing choices and are deterministically completed to five during migration.
- **Enable transition buttons** adds slim 48 dp edge controls as an alternative to the gesture. Only the direction leading to the other page is enabled; there is no page wraparound. The setting defaults to off.
- The Settings page explains the hold-and-drag gesture directly below the transition-button toggle.

### Verification state

- Three navigation-partition tests cover the default split, legacy short-set migration, and a complete duplicate-free 5+5 partition.
- The true clean candidate passed 96/96 unit tests, Android lint, and APK assembly.
- The exact artifact passed v2 signing, `minSdk 28`, `targetSdk 34`, and 16 KB native-library ZIP alignment checks.
- Dex inspection confirmed `androidx.startup.R$string`, `AppInitializer`, and `InitializationProvider` are present in the clean APK.
- The exact clean APK installed and cold-launched on the Motorola G57 Power in 2.07 seconds. The process remained alive with no `SymphonyLogger` or `AndroidRuntime` errors.
- Hold-and-drag transitions passed in both directions. Both complete five-item pages rendered correctly, the mini-player remained stationary, and the optional edge buttons moved in both directions without wrapping.
- The Settings explanation now aligns exactly with its surrounding rows; device UI bounds place all three relevant text blocks at `x=109`.
- Page-assignment mutation, Android 9, and TalkBack remain part of the focused manual regression suite in `tests.md`.

### Archived candidate

`artifacts/timeline/stage-14-paged-navigation/Symphony-stage-14-paged-navigation-debug.apk`

SHA-256:

`5a2665bd0d2caea7bc43d42f7e406f5cdde36dd795fd74aca34d83a86e74fa95`

## UI/UX Feature 3 / Stage 15: Unified modal surfaces

**Status:** Implemented and device-smoke-tested

### User-facing behavior

- The Home overflow button is removed. Rescan and Settings are exposed as two adjacent, visually quiet app-bar icons with standard touch targets. Their glyphs are shifted inward to a compact 40 dp visual spacing while the Search and action regions reserve equal width, keeping the page title at the physical screen center.
- Every app-owned context menu and sorting menu now opens as an opaque, rounded modal sheet rather than a floating dropdown.
- Tracks use the approved grouped layout: cached artwork and identity header, Favorite/Play next/Add to queue/Add to playlist quick actions, conditional **Play from beginning** with Symphony's existing `RestartAlt` icon, navigation actions, file/detail actions, and a separated destructive action.
- Album, artist, album-artist, playlist, folder, generic song-list, Search, list/detail-page and Now Playing action surfaces share the same modal foundation while retaining their existing actions.
- Sorting, ordering and settings-choice surfaces use a compact titled, scrollable choice-sheet variant with their existing radio/selection semantics.
- Information, confirmation, playlist, folder, playback speed, pitch, sleep timer, grid-size and settings input/control dialogs all inherit the same sheet shape, drag handle, surface, typography, insets and action treatment.
- Singular and batch permanent deletion use dedicated sheet confirmations. The destructive action is labelled **Delete permanently** and remains separated from ordinary actions.
- Android-owned storage authorization, file pickers and artwork pickers remain system UI because applications cannot restyle them.
- The Add-to-playlist → New playlist path replaces one sheet with the next instead of stacking two modal layers. A playlist created from that path receives the pending tracks directly.

### Keyboard-aware editor

- **Edit details** opens as the expanded form variant with a fixed header/action region and an independently scrollable form.
- `adjustResize`, IME/navigation-bar padding and focus-driven bring-into-view handling keep the active field and Cancel/Save controls above the software keyboard.
- Single-line fields expose keyboard **Next** navigation; Lyrics retains multiline keyboard behavior.
- Long filename and tag content retains normal selection handles, cursor dragging and horizontal movement.
- Artwork-picker state, immediate sheet closure on Save, background verified writing and real-time repository publication remain unchanged.

### Verification state

- Incremental compilation and APK assembly passed throughout the migration.
- The true clean candidate passed 96/96 unit tests, Android lint, and APK assembly.
- The exact packaged artifact passed v2 signing, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum verification, and explicit AndroidX Startup Dex inspection.
- The exact clean APK installed and cold-launched on the Motorola G57 Power in 2.189 seconds with the process alive and no relevant `SymphonyLogger` or `AndroidRuntime` error.
- Physical inspection passed the direct Home actions, grouped track actions, adaptive sorting sheet, permanent-deletion confirmation, expanded editor, real IME resize, cursor handle visibility, fixed Cancel/Save actions and keyboard Next focus transition.
- Final packaged-build UI hierarchy measurement on the 1080 px Motorola display placed the **Songs** page-title center at `x=539.5`, the nearest possible half-pixel midpoint to the physical `x=540` center. Rescan and Settings glyph centers measured 78 px/39 dp apart while retaining independent 48 dp button bounds.
- No deletion or metadata write was executed during UI smoke testing.

### Archived candidate

`artifacts/timeline/stage-15-unified-modal-surfaces/Symphony-stage-15-unified-modal-surfaces-debug.apk`

SHA-256:

`2d53eb6f7aa800407c1a61ebf079ce9fab487d5def2e6efa7dc89fb65e52f7d7`

## UI/UX Feature 4 / Stage 16: Minimal multi-track selection

**Status:** Implemented and device-smoke-tested

### User-facing behavior

- Long-press selection retains the existing selection, Select all, Back/Close, and verified batch-deletion behavior.
- The old rounded `primaryContainer` track cards and square Material checkboxes are removed.
- Selected tracks remain flat and receive only an 8% primary-colour tint plus a slim 3 dp primary accent at the leading edge.
- Artwork remains unobstructed. Unselected artwork is gently reduced to 82% opacity while selection mode is active, making the selected set easier to scan without adding another overlay.
- The trailing checkbox location now contains a 24 dp custom circular indicator inside the existing 48 dp area. Unselected tracks show one muted hollow ring; selected tracks show a thin primary ring and compact solid primary centre dot with a visible gap. No tick symbol is used.
- The media sort bar and contextual selection toolbar crossfade and move by a restrained one-fifth of their height over 120–160 ms.
- The toolbar remains transparent with a subtle 0.5 dp lower divider, semibold selected-count text, the app's existing `Icons.Filled.SelectAll`, and the isolated error-colour Delete action.
- Each row exposes selected/not-selected semantics while the full row remains the tap target in selection mode.

### Verification state

- The true clean candidate passed 96/96 unit tests, Android lint, and APK assembly.
- The exact packaged artifact passed v2 signing, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum verification, and explicit AndroidX Startup Dex inspection.
- The exact APK installed and cold-launched on the Motorola G57 Power in 2.168 seconds with a live process and no relevant `SymphonyLogger` or `AndroidRuntime` error.
- Physical inspection passed long-press entry, three-track selection, ring-and-dot rendering, flat row tint, artwork clearance, selected-count updates, the unchanged Select all icon, Select all reaching 7/7 tracks, its Deselect all accessibility state, and Back exiting selection.
- No deletion was executed during the visual smoke test.

### Archived candidate

`artifacts/timeline/stage-16-minimal-track-selection/Symphony-stage-16-minimal-track-selection-debug.apk`

SHA-256:

`369b99fdcd50fee23ff01a80ec4fe79d5d8b87502284597d9c71adfb4366aedf`

## Reliability and compatibility / Stage 17

**Status:** Implemented, clean-built, and device-smoke-tested

### Repeat-mode persistence

- The last selected repeat mode (off, repeat queue, or repeat one) is committed synchronously when it changes and restored before the first player-state publication.
- The mode survives activity closure, app force-stop, and complete process recreation. It is independent of queue snapshots, so clearing or rebuilding a queue does not silently reset the preference.
- Now Playing exposes a mode-specific accessibility description: **Repeat off**, **Repeat queue**, or **Repeat one**.

### Modern embedded artwork

- Embedded cover-art recognition now includes JPEG, PNG, GIF, BMP, WebP, HEIF, and HEIC, with normalized MIME parameters and common aliases.
- Artwork selected through a document provider falls back to Android's decoded image MIME when the provider supplies no useful MIME type.
- WebM is deliberately not accepted as cover art: it is a video/audio container rather than a still-image format. Supporting it would require frame extraction and unnecessary playback/editor overhead.
- This changes artwork recognition and editing only; it introduces no decoding work into playback.

### Clear audio-interruption behavior

- The ambiguous **Require audio focus** and **Ignore audio focus** switches are replaced by one **Audio interruptions** choice.
- **Pause and resume automatically** is the default. Symphony respects another app's focus request and automatically resumes after a temporary interruption, preserving the Stage 1 behavior.
- **Keep playing** requests focus but deliberately continues when another app takes it; overlapping audio is clearly stated in the option description.
- Requesting audio focus is now an internal playback invariant rather than a user-facing toggle. The old ignore-loss preference migrates deterministically to the equivalent new choice.

### Navigation decision

- **Artists** and **Album artists** remain separate destinations. No replacement or migration is applied.

### Verification state

- The true clean candidate passed 101/101 unit tests, Android lint, and APK assembly.
- The exact packaged artifact passed v2 signing, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum equality, and AndroidX Startup Dex inspection.
- Repeat queue survived a physical ADB force-stop and cold restart on the Motorola G57 Power.
- The consolidated audio-interruption row and both choices were inspected on-device; neither legacy switch remained.
- The exact final APK installed with data preserved and cold-launched in 3.017 seconds. Its process remained alive, restored the saved long-track state, and emitted no fatal runtime error.

### Archived candidate

`artifacts/timeline/stage-17-repeat-artwork-audio-interruptions/Symphony-stage-17-repeat-and-modern-artwork-debug.apk`

SHA-256:

`f330a0863afc1bce4e6c50e3bbdaeb9a6adbc91a9fd03ebe191f771a8bf490b2`

## UI/UX Feature 5 / Stage 18: Compact navigation and modern queue

**Status:** Implemented, clean-built, and device-smoke-tested

### Compact bottom navigation

- The app-owned navigation content is reduced from 80 dp to 64 dp when labels are available and 56 dp when labels are disabled. Android's mandatory navigation/gesture inset remains untouched.
- Material's loose icon/label positioning is replaced by a stable custom item stack: the existing 32 dp icon container, a 2 dp gap, and a reserved 16 dp label line.
- Always-visible, active-only, and hidden label modes retain their prior semantics. Active-only mode reserves the label line so icons do not jump vertically when selection changes.
- Existing 24/28 dp inactive/active icon animation, primary colour, filled/outlined transition, page indicator, long-press drag, optional edge buttons, five-item page partition, and 48 dp item targets remain intact.

### Modern queue

- Queue rows no longer show permanent square checkboxes or queue numbers over the artwork.
- Position numbers use a quiet leading column; the current item uses a primary waveform icon, subtle 6% tint, and 3 dp leading accent. Already-played items use restrained opacity rather than a blocking overlay.
- Long-press enters the same ring-and-dot selection language as Stage 16. The full row toggles selection, artwork remains clear, and normal row taps continue jumping to the selected queue position.
- The selection toolbar provides Exit selection, Add selected to playlist, Select/Deselect all, and Remove selected. Removal remains isolated in the error colour.
- Add selected opens the unified playlist picker and supports both existing playlists and New playlist. Cancellation preserves selection; a successful add clears it.
- The normal toolbar replaces the legacy floppy-disk symbol with Save queue as playlist and visually isolates Clear queue as destructive.
- Queue remains a full page so each track's grouped action sheet can open as the only modal layer.

### Verification state

- The true clean candidate passed 101/101 unit tests, Android lint, and APK assembly.
- The exact packaged artifact passed v2 signing, `minSdk 28`, `targetSdk 34`, 16 KB native-library ZIP alignment, checksum equality, and AndroidX Startup Dex inspection.
- The exact APK installed with data preserved and cold-launched on the Motorola G57 Power in 2.195 seconds with a live process and no fatal runtime error.
- Physical inspection passed the compact labelled navigation bar, normal queue styling, long-press selection, Select all, Exit selection, and the Add selected playlist picker with existing and New playlist paths.
- Playlist addition, queue removal, and Clear queue were deliberately not executed during smoke testing.

### Current candidate

`artifacts/test/Symphony-stage-18-compact-navigation-modern-queue-debug.apk`

SHA-256:

`20f3e45f63bfb9d1d38002e49137c9fde6d6912301fee330cceabf4e5a39f70c`

## UI/UX Feature 6 / Stage 19: Complete legacy-UI convergence

**Status:** Implemented, clean-built, and launch-smoke-tested

### Shared modern controls

- Reusable flat choice rows, ring-and-dot indicators, switch rows, and selectable rounded preset chips now define Symphony's secondary control language.
- Choice rows use restrained primary tint instead of separate boxy cards.
- Playlist workflows share the Stage 16/18 selection language and keep artwork unobstructed.

### Lyrics and playlist workflows

- Separate-page lyrics now opens a 90%-height expanded modal sheet with the shared handle, rounded surface, identity header, lyrics, seek bar, and playback controls.
- The obsolete lyrics navigation destination and its route transitions were removed.
- Manage playlist songs uses a rounded search field, flat selected rows, subtle tint, and trailing ring-and-dot indicators.
- Add to playlist moves membership state from artwork to a trailing indicator.

### Player utilities and settings

- Speed, pitch, and sleep-timer presets use rounded selectable chips with explicit active state.
- Persistent speed/pitch and quit-on-timer-end use switch rows instead of checkboxes.
- Settings choice editors use flat rows; numeric presets, custom-value tags, folder lists, destructive affordances, and breadcrumbs now follow the same modern language.
- First-launch update choices reuse the standard switch-row treatment.

### Unified transient feedback

- A buffered application-level message channel feeds one Compose snackbar host.
- Update, clipboard, playlist import/export, sharing, equalizer, and artwork-loading feedback no longer use Android Toast UI.
- The deliberately silent verified deletion and metadata-save operations remain silent.

### Verification state

- The candidate passed 101/101 unit tests, Android lint, Kotlin compilation, and APK assembly.
- The exact APK passed v2 signature verification, `minSdk 28`, `targetSdk 34`, page-aware ZIP alignment, and checksum verification.
- It installed with existing data preserved and launched on the Motorola G57 Power; the process remained alive and emitted no AndroidRuntime crash.
- Destructive operations and playlist/file mutations were not exercised automatically.

### Current candidate

`artifacts/test/Symphony-stage-19-complete-modern-ui-debug.apk`

SHA-256:

`2684467a124da7e14b3c61a837f8ec064a523e84be953ce1f40ba9e9eee4928e`
