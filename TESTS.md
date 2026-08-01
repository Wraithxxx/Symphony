# Testing and regression coverage

Symphony handles real files, long-running playback, Android lifecycle events, media
sessions, storage providers, and Bluetooth controls. A successful compile is not a
sufficient release signal, so verification is split across unit tests, Android
build checks, APK inspection, and physical-device scenarios.

## Current verification snapshot

The `2026.08.01` build line has the following verified state:

- 118 unit tests passed with zero failures.
- Android lint completed with zero errors.
- Debug and R8 release APKs assembled successfully from the same source tree.
- Debug package: `io.github.wraithxxx.symphony.debug`
- Release package: `io.github.wraithxxx.symphony`
- Version code: `1`
- Minimum SDK: Android 9 / API 28
- Target SDK: API 34
- APK signature and page-aware ZIP alignment verified.
- The signed R8 release installed and cold-launched successfully.
- The primary physical test device was a Motorola G57 Power.

The release APK is approximately 9.87 MB after R8 optimization. Its mapping file is
retained for future crash deobfuscation.

## Automated coverage

Unit tests focus on deterministic policy and state transitions—the areas where a
small regression can otherwise create a large playback or storage failure.

### Playback and audio focus

- temporary focus-loss pause and safe resume
- manual-pause cancellation of pending recovery
- permanent focus loss and ducking behavior
- playback state publication during preparation
- pending Play fulfillment and cancellation
- seek generation, clamping, and stale-event rejection
- restored-position validation

### Queue and media controls

- cyclic Previous and Next behavior
- the three-second Previous restart rule
- repeat-mode independence and persistence
- media-button command resolution
- cold-restored command routing
- queue reconciliation after library changes
- removal of current, future, duplicate, and final queue entries

### Library and storage policy

- serialized/coalesced refresh requests
- stable filename and media identity rules
- deletion planning and retry boundaries
- rename validation and conflict detection
- metadata-edit validation
- partial-failure handling
- selection and Select all state

### Session restoration and retention

- cached queue filtering and restoration
- process/lifecycle foreground-service policy
- long-track duration thresholds
- checkpoint throttling
- completion, near-start, deletion, and replacement cleanup
- active-session precedence over stale persisted state

### Settings and navigation

- settings migration and persistence
- navigation page assignment and paging rules
- label-mode and transition-button state
- repeat and launcher-related preference behavior

## Physical-device regression matrix

The following behaviors were exercised on packaged APKs rather than only from IDE
previews or isolated unit tests.

| Area | Scenarios covered | Expected result |
| --- | --- | --- |
| Audio interruptions | temporary competing audio, repeated interruptions, manual pause, permanent takeover | Resume only after a matching temporary loss and never override user intent |
| Seeking | slider jumps, backward/forward seeks, restored positions, rapid seeks, multi-hour tracks | Any valid timestamp remains playable; no snap-back or unseekable region |
| Cold restoration | hours-long process absence, Play during preparation, canceled Play, cached queue | Correct track and position restore; no silent false-Pause state |
| Track changes | rapid selection, queue transition, automatic completion, artwork changes | Identity and position update atomically; stale callbacks cannot overwrite the new item |
| Library refresh | new downloads, external deletion, manual rescan, repeated foreground entry | Library updates without losing current playback, queue, or progress |
| Deletion | single and multi-select, short and long tracks, active and inactive items | Row dims immediately, Android authorization appears where required, and verified files disappear from all views |
| Rename and metadata | filename, text tags, lyrics, artwork, active long-form playback | Changes appear immediately and persist in the file without an audible restart |
| Queue navigation | first/last wrapping, Bluetooth gestures, physical controls, notification controls | Every control surface follows the same cyclic navigation rules |
| Pinned controls | task removal while playing/paused, service recovery, reopening the app | Notification controls remain live or shut down cleanly; no indefinite spinner |
| Position retention | default off, duration threshold, completion cleanup, Play from beginning | Only eligible tracks resume and stored state never creates a seek boundary |
| Navigation UI | labels on/off, two pages, hold-and-drag, edge buttons, persistence | Compact geometry remains stable and page changes are deliberate |
| Selection UI | entry/exit, ring-and-dot state, Select all, playlist addition, deletion | Selection is clear, artwork remains visible, and actions match the current screen |
| Modal surfaces | context actions, sorting, playlists, destructive confirmation, keyboard editor | Consistent sheets, no nested-modal conflict, and editor actions remain visible above the keyboard |
| Launcher identity | Wraith/classic switching, process death, notification launch | One launcher remains active, Settings stays usable, and intents target the enabled component |
| Native splash | cold start, restored task, browser round-trip, both icon modes | Fixed icon geometry, no flicker/handoff, no black system-bar frame, and no inactive-task crash |
| R8 release | install, launch, package/version inspection | Signed optimized build opens normally with no fatal startup exception |

## High-risk regression suites

### Audio interruption recovery

1. Start playback, then trigger temporary audio from another application.
2. Confirm Symphony pauses and resumes after focus returns.
3. Repeat, but manually pause Symphony during the interruption.
4. Confirm focus return does not resume it.
5. Trigger a permanent media takeover and confirm Symphony remains paused.

### Long-form seeking and restoration

1. Use a recording at least one hour long.
2. Drag far forward, then seek backward before the dragged timestamp.
3. Repeat through the slider, relative controls, notification, and Bluetooth input.
4. Save a later position, force-stop the app, and restore the session.
5. Confirm the entire earlier region remains seekable and Play is honored during
   preparation.

### Storage mutation integrity

1. Delete, rename, and edit both active and inactive tracks.
2. Include a long recording and an item referenced by a queue and playlist.
3. Confirm playback continuity where the audio payload is unchanged.
4. Confirm library, queue, playlist, notification, artwork, and retained-position
   state reconcile immediately.
5. Reopen or rescan and verify the physical file result remains authoritative.

### Process and media-session recovery

1. Start playback and remove the app task.
2. Exercise notification and Bluetooth controls while the activity is absent.
3. Reopen the app and verify the existing session is reused.
4. Repeat after a genuine process death and with both launcher identities.
5. Confirm repeat mode, queue navigation, and saved progress remain coherent.

## Release gate

Every candidate intended for distribution should pass this sequence:

1. Generate translations and other required sources.
2. Run all unit tests.
3. Run Android lint with zero errors.
4. Assemble the intended build type from source.
5. For release builds, confirm R8 produced a mapping file.
6. Verify package ID, version code/name, minimum and target SDK.
7. Verify APK signature and signer count.
8. Verify page-aware ZIP alignment and native-library packaging.
9. Generate and validate SHA-256 checksums.
10. Install the exact final APK, not an earlier intermediate output.
11. Cold-launch it and inspect fatal runtime logs.
12. Exercise the feature-specific physical regression scenarios.

## Device coverage and honest limits

The application targets Android 9 and later, but the deepest physical validation so
far has been performed on one Motorola device. Unit, lint, package, and platform
checks reduce risk across Android versions; they do not substitute for a broad OEM
device matrix.

Community testing is particularly valuable for:

- OEM-specific background-service and pinned-media behavior
- document providers and removable storage
- Bluetooth headsets with nonstandard gesture mappings
- Android versions and display scales outside the primary test environment
- uncommon audio containers and metadata combinations

When reporting a regression, include the Android version, device model, audio
format, storage provider, exact action sequence, and whether the issue survives a
cold process restart. Never include private file contents or signing information.
