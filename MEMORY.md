# Architecture and maintainer notes

This is the public-safe architectural reference for the Wraithxxx edition of
Symphony. It describes the decisions that future changes must preserve without
including local machine paths, device identifiers, signing material, private
artifacts, or development-session transcripts.

User-visible behavior is documented in [FEATURES.md](./FEATURES.md), the problems
that motivated the work in [PROBLEMS.md](./PROBLEMS.md), and verification coverage
in [TESTS.md](./TESTS.md).

## Project identity

- Application name: **Symphony**
- Application ID and namespace: `io.github.wraithxxx.symphony`
- Minimum Android version: Android 9 / API 28
- Upstream project: [`zyrouge/symphony`](https://github.com/zyrouge/symphony)
- License: GNU Affero General Public License v3.0

The application source uses the Wraithxxx namespace. Embedded or published upstream
components keep their established identities—for example, Metaphony remains under
`io.github.zyrouge.metaphony` and Phrasey packages retain their published names.

## System overview

Symphony is a single-activity Compose application with three broad layers:

1. **Groove** owns the media library and storage-facing operations.
2. **Radio** owns playback, queue state, audio focus, media session, notification,
   restoration, and progress persistence.
3. **UI** renders observable state and sends explicit commands to those services.

The layers cooperate through stable media identity. A UI row, queue item, playback
item, playlist entry, and storage record must refer to the same logical file even
when its filename, metadata, or artwork changes.

## Application and lifecycle

`SymphonyApplication` creates process-wide services and repairs launcher-component
state before the main activity is shown. `MainActivity` installs the Android splash
API and hosts Compose. Two launcher aliases provide the default Wraith icon and the
classic icon without changing the installed application package.

Launcher switching follows a strict order:

1. Enable the destination launcher component.
2. Update the persisted preference.
3. Disable the previous component.
4. Restart only the activity task when required.

Notification content intents always resolve the currently active launcher.

## Settings

Settings are persistent application preferences and are treated as part of the
player's state contract, not merely UI configuration. Important values include:

- audio-interruption policy
- repeat mode
- queue/navigation configuration
- optional long-track position retention and duration threshold
- launcher identity
- theme, typography, and navigation presentation

Changes that must survive immediate process termination use synchronous committed
writes. Less critical presentation preferences may use normal asynchronous writes.

## Groove: library and storage

Groove scans configured storage trees and exposes repositories for songs, albums,
artists, album artists, genres, and playlists. Refresh is a reconciliation process:
it updates the library while preserving valid playback state rather than replacing
the session wholesale.

### Refresh invariants

- Refresh requests are serialized and may be coalesced.
- The current song and confirmed position survive a normal refresh.
- Missing future queue items are removed safely.
- Stable identity prevents unchanged files from appearing as new tracks.
- A full scan may update metadata without publishing stale intermediate state.

### Storage mutations

Deletion, renaming, metadata changes, and artwork changes are provider-aware. A
mutation follows this general shape:

1. Validate the request and resolve the current media identity.
2. Prepare expensive file work away from the UI thread.
3. Request Android or provider authorization when required.
4. Commit the storage operation.
5. Verify the resulting file/provider state with bounded retries.
6. Reconcile library, queue, playlists, caches, progress, and visible state.

No operation should report success merely because a request was submitted. The
actual storage result is authoritative.

### Metadata editing

Tag editing is format-aware and preserves audio payloads. Filename changes and
embedded metadata changes share one editor but remain distinct storage operations.
The displayed title follows the physical filename by product decision.

For the active item, textual metadata and artwork are published immediately after a
verified commit. Playback continuity takes priority: a metadata-only change should
not rebuild the audio source or interrupt output.

## Radio: playback and media integration

Radio is the single authority for playable media, current item, confirmed position,
queue navigation, focus ownership, media session, and notification state.

### Playback state invariants

- The UI never invents a position independently of the player.
- A callback is applied only if it belongs to the active media identity and current
  preparation generation.
- A Play request during preparation is retained until it can be fulfilled or is
  explicitly invalidated.
- Restored seeking completes before restored playback begins.
- Changing tracks publishes identity and initial position atomically.
- Queue clearing, rebuilding, or refresh must not silently reset repeat mode.

### Seeking

All seek surfaces route through one serialized coordinator: progress slider,
relative seek, notification, Bluetooth/media buttons, and restored position. The
coordinator clamps only to the real media duration and rejects stale player events.
No previous slider or restored position may become a synthetic lower boundary.

### Audio focus

Audio focus distinguishes temporary loss, ducking, gain, permanent loss, and user
pause. Automatic resume is allowed only when Radio recorded that it paused because
of the matching temporary focus loss. Any explicit pause or incompatible session
change cancels that intent.

### Queue navigation

Manual navigation is cyclic and independent from automatic completion/repeat
behavior. The media-button command resolver is shared across UI, notification,
headset, and Bluetooth input so cold restoration cannot fall back to different
navigation rules.

### Restoration and progress

A compact cached queue permits fast process restoration before the library scan.
Optional per-track position retention is keyed by stable file identity, duration
threshold, and validity checks. Active-session state wins over older persisted
checkpoints.

### Foreground service and notification

The foreground service, media session, and notification publish from the same Radio
snapshot. Pinned controls may restart or reconnect to a valid session after task
removal. Loading indicators are bounded by real preparation state and must not
remain indefinitely after an error or stale command.

## UI architecture

The Compose UI observes service state and emits commands; it does not own playback
truth. Shared components provide modal sheets, selection indicators, setting rows,
playlist workflows, and buffered transient feedback.

### Navigation

Home navigation contains two configurable horizontal pages. A hold-and-drag gesture
or optional edge buttons changes pages. The selected destination uses color, slight
icon scaling, and a microbar rather than a large Material indicator.

### Modal and selection surfaces

Context actions, sorting, confirmation, playback controls, and editors use one
modal-sheet language. Nested modal workflows replace the parent sheet before
opening a child. Long-press selection uses flat rows and a ring-and-dot marker,
leaving artwork unobstructed.

### Feedback

Application feedback is buffered through one message channel and rendered by the
root snackbar host. Platform-owned storage confirmation remains a system message
where Android requires or provides it.

## Splash and icon architecture

The Wraith splash is one Android-owned animated-vector drawable. Its geometry is
constant while six color animations progress from violet to teal. There is no
programmatic exit-listener replacement or drawable handoff. This avoids restored
task crashes, black system-bar frames, and icon-size discontinuities.

Classic mode uses the original static launcher identity and splash.

## Build and release model

- Debug builds use the `.debug` application-ID suffix.
- Release builds use R8 shrinking and optimization.
- Signing configuration is supplied only through ignored local environment data or
  repository secrets; credentials are never committed.
- APK verification covers signature, manifest metadata, Android SDK levels,
  page-aware ZIP alignment, and SHA-256 integrity.
- R8 mapping files must be retained with the corresponding release for crash
  deobfuscation.

Generated translations, Room schemas, build directories, APKs, signing files, and
machine-specific configuration follow the repository's ignore policy.

## Compatibility boundaries

- Storage behavior ultimately depends on Android's document/media providers and the
  permissions they expose.
- Permanent audio-focus takeover does not trigger automatic resume.
- WebM is not a still cover-art format and is not accepted as embedded artwork.
- Android 9+ is the supported platform range; behavior across additional OEMs should
  be expanded through community device testing.

## Maintainer checklist

Before accepting a change:

1. Preserve the state invariants above.
2. Add or update focused unit tests for pure policy/state logic.
3. Run unit tests, lint, and the appropriate Gradle assembly.
4. Exercise the affected flow on a physical device, including process restoration
   when lifecycle behavior is involved.
5. Verify the final APK rather than assuming a successful compile is sufficient.
6. Keep signing material, local paths, device identifiers, and generated artifacts
   out of commits.
