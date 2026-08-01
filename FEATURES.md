# Features

This edition of Symphony keeps the upstream player's offline-first foundation and
extends it around three real-world needs: dependable playback, direct ownership of
local files, and an interface that stays quiet until it is needed.

## Reliable playback

### Audio interruption behavior

Audio focus is handled as a player invariant rather than a collection of
contradictory switches. The Player settings expose two clear policies:

- **Pause and resume automatically** pauses for a temporary interruption and
  resumes only when it is safe and still matches the user's intent.
- **Keep playing** ignores focus-loss callbacks for users who deliberately want
  overlapping audio.

Calls, navigation prompts, videos, social applications, and other audio sources all
use the same focus state machine.

### Accurate seeking at any duration

Media3-backed playback keeps the complete file seekable after slider jumps, session
restoration, rapid seeks, and track changes. Position updates are validated against
the active item, preventing old callbacks from creating false seek boundaries.

### Fast, honest restoration

The cached queue and current session are restored before a full library scan. Play
requests made during preparation remain pending, saved positions are applied before
audio starts, and the UI does not publish a false playing state while silent.

### Optional long-track position memory

Position retention is off by default. Power users can enable it and choose a
minimum duration, allowing long podcasts and stories to resume while ordinary music
continues to start from the beginning.

Retention includes throttled checkpoints, safe restored seeking, completion and
near-start cleanup, file-identity protection, **Play from beginning**, and a command
to clear all remembered positions.

### Persistent playback preferences

Repeat mode survives task removal and complete process death. Playback speed,
pitch, queue state, and saved progress remain independent rather than resetting one
another during restoration.

### Boundary-free queue navigation

Manual Previous on the first queue item wraps to the final item, and Next on the
final item wraps to the first. The familiar three-second Previous rule is retained:
restart the current track first, then navigate on another press. The same resolver
serves on-screen controls, notifications, physical buttons, and Bluetooth gestures.

Automatic completion and repeat modes remain separate from manual navigation.

### Persistent media controls

The playback service and media session remain authoritative when the app task is
removed. Pinned notification controls can recover the service instead of becoming
stale buttons, while empty or deliberately stopped sessions shut down cleanly.

## A library that follows the filesystem

### Automatic, non-destructive refresh

Symphony refreshes the library on foreground entry and reconciles downloads,
renames, moves, and external deletions without rebuilding a healthy playback
session. Manual rescan remains available as a direct action and preserves the
current track, position, queue, shuffle, and repeat state.

### Delete files from inside the player

Single-track deletion remains available, and long-press selection adds batch
deletion across song lists. Android's provider-aware confirmation is used where the
platform requires it. Successful rows dim immediately while storage and library
state reconcile, then disappear without waiting for a tab change.

Current playback, queue entries, playlists, cached artwork, and remembered positions
are updated as one operation. Lightweight verification and bounded retries handle
transient provider failures without blocking the interface.

### Rename files and edit audio details

One **Edit details** form handles the physical filename and embedded metadata:

- filename
- artists and album artists
- album, genre, composer, and comment
- track and disc numbering
- year/date and lyrics
- embedded cover artwork

The title displayed by Symphony follows the filename. Long fields support normal
cursor navigation, and the form remains keyboard-aware on small screens.

Edits are prepared away from the UI thread, committed atomically where the format
allows it, verified, and reflected immediately in the library, queue, Now Playing,
and notification. Renaming or changing artwork on the active track no longer forces
an audible playback restart.

### Modern cover-art compatibility

Embedded artwork recognizes JPEG, PNG, GIF, BMP, WebP, HEIF, and HEIC aliases and
MIME variants. WebM is intentionally not treated as cover art because it is a media
container rather than a still-image format.

## Playlists and queue tools

- Save the current queue to a playlist.
- Long-press queue items for modern multi-selection.
- Add selected queue tracks to an existing or new playlist.
- Remove selected items without deleting their files.
- Add songs to playlists from individual and multi-selection workflows.
- Preserve ordering when selected queue items are transferred.

Selection is cleared only after an operation succeeds; cancellation leaves the
user's work intact.

## A quieter, more capable interface

### Minimal bottom navigation

The selected destination uses a subtle color change, a slightly enlarged icon, and
a compact microbar instead of a large Material pill. Labelled and label-free modes
share the same geometry, and the bar uses less vertical space at normal content
scale.

### Two-page navigation without vertical expansion

Up to ten user-selected destinations live in two horizontal pages. Touch and hold,
then drag, produces a tactile page transition; optional edge buttons provide an
accessible alternative. Page assignments, the last page, label mode, and button
preference persist across launches.

### Consistent contextual surfaces

Legacy dropdowns and mismatched dialogs were replaced with a shared modal-sheet
language. Track actions are grouped by playback, organization, information, and
file operations. Sorting, playlist tools, playback utilities, confirmation flows,
and settings choices use the same visual grammar.

The metadata editor expands above the keyboard and keeps its actions visible rather
than being obscured by the IME.

### Modern selection

Long-press selection uses a flat tinted row, a leading accent, and a compact
ring-and-dot indicator in the former checkbox position. Artwork remains clear.
Contextual actions support select all, playlist addition, removal, and permanent
deletion as appropriate to the current screen.

### Modern queue and lyrics

The queue has a clear active-track marker, subtle played-item treatment, and the
same selection system as the library. Lyrics can open in a tall modal sheet while
retaining live timing, seeking, screen-awake behavior, and playback controls.

### Unified feedback

Non-destructive messages use one buffered in-app snackbar host, so feedback emitted
during navigation is not lost. Storage deletion retains Android's brief system
confirmation where appropriate.

## Identity and compatibility

- App name: **Symphony**
- Edition namespace: `io.github.wraithxxx.symphony`
- Minimum Android version: Android 9 / API 28
- Current version: `2026.08.01` (`versionCode 1`)
- Default identity: black and teal Symphony waveform
- Optional identity: classic violet Symphony icon

The Wraith launcher uses one native animated-vector splash: six fixed bars change
from violet to teal from left to right over 700 ms. Because Android owns the entire
animation, there is no drawable handoff, geometry jump, black status-bar frame, or
restored-task crash. Classic-icon mode remains static and survives process death.

The upstream Symphony project, its author, and contributors remain credited. The
embedded Metaphony code and published Phrasey packages retain their upstream
namespaces.

## Design principles

1. **Offline means ownership.** Library entries should represent real files, and
   file operations should affect storage—not a private shadow database.
2. **Long-form audio is ordinary audio.** A seven-hour story deserves the same
   responsive controls as a three-minute song.
3. **State should be honest.** A button, slider, notification, and audible output
   should never disagree about what the player is doing.
4. **Power should remain optional.** Advanced retention, navigation, and playback
   controls should be available without making the default experience busy.
5. **Recovery should respect intent.** Automatic behavior may restore the session,
   but it must never override an explicit user action.

The original reliability gaps behind these capabilities are summarized in
[PROBLEMS.md](./PROBLEMS.md). Test coverage is documented in
[TESTS.md](./TESTS.md).
