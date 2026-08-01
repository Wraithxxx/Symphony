# Problems solved

Symphony began as an excellent offline music player, but daily use with music,
podcasts, and audio stories exposed a handful of reliability gaps. Short songs and
multi-hour recordings stress a player differently, and a frequently changing local
library makes stale state especially visible.

This document records the five core problems addressed by this edition, why they
occurred, and the behavior that replaced them.

## 1. Playback did not recover from temporary audio interruptions

### What happened

When another application temporarily took audio focus, Symphony paused correctly
but often remained paused after the interruption ended. The user had to return to
the app and press Play. This was not specific to YouTube, Instagram, or any other
particular application; any audio-producing app could trigger it.

### What changed

Audio-focus handling now distinguishes temporary interruption, permanent loss,
ducking, and deliberate user actions.

- Temporary focus loss pauses playback and records that recovery is pending.
- Focus gain resumes only when Symphony itself paused for that interruption.
- A manual pause cancels pending recovery, so the app never fights the user.
- Ducking lowers and subsequently restores volume without corrupting play state.
- Permanent focus loss remains paused, matching Android's media expectations.

The result is app-agnostic interruption recovery without unwanted playback after a
video, call, or another permanent media takeover.

## 2. Seeking created an unplayable region earlier in the track

### What happened

After dragging the progress slider to a later timestamp, a backward seek could move
briefly and then snap back. In long recordings, the first dragged position behaved
like a false lower boundary: everything before it appeared on the slider but could
not be played. The same boundary could reappear after restoring a saved position.

### What changed

Playback and seeking were moved onto a single Media3 state model. Seek requests are
serialized against the active media item, stale callbacks are rejected, and the UI
follows the player's confirmed position instead of treating a previous drag or
restored timestamp as a new origin.

Backward, forward, slider, notification, Bluetooth, and restored-position seeks now
share the same path. The entire duration remains seekable for both short music and
recordings lasting several hours.

## 3. Restored playback could show Pause while remaining silent

### What happened

After Symphony had been closed for several hours, a track position was preserved,
but pressing Play could immediately change the button to Pause while audio remained
silent during library restoration and media preparation. The restored timestamp
could also reproduce the unseekable-region problem.

### What changed

Session restoration now has an explicit preparation lifecycle.

- A lightweight cached queue is restored before the full storage scan completes.
- A Play request made during preparation is retained rather than discarded.
- The saved position is applied to the correct media item before playback begins.
- User cancellation, track changes, and stale preparation callbacks invalidate the
  pending request.
- The published media-session state reflects whether audio is genuinely ready.

Cold start is therefore deterministic: the app may need to prepare a large file,
but it no longer pretends to be playing or loses the user's request.

## 4. A new track briefly displayed the previous track's position

### What happened

Selecting a new track could leave the previous track's timestamp and slider value
visible for a moment. On slower storage or long recordings, that small delay made
the transition feel laggy and could allow an old callback to overwrite new state.

### What changed

Track identity, duration, position, artwork, and preparation state now transition as
one atomic state change. The visible position resets immediately to the new track's
start or its valid retained position, and callbacks belonging to the previous item
are ignored.

This keeps the player card, Now Playing screen, notification, and media session in
agreement throughout rapid track changes.

## 5. Storage changes required a destructive manual rescan

### What happened

New downloads and externally deleted files did not appear until a manual rescan.
Rescanning could rebuild playback state and lose the current track's progress—an
especially costly failure for podcasts and audio stories.

### What changed

Library refresh is now non-destructive and reconciles storage with the active
session.

- Foreground entry performs a lightweight refresh so routine downloads and removals
  appear without manual intervention.
- Manual rescans preserve the current item, confirmed position, queue, shuffle, and
  repeat state.
- Missing files are removed from future queue positions without disturbing a valid
  current item.
- Repeated refresh requests are serialized and coalesced.
- Stable media identity prevents an unchanged file from being mistaken for a new
  track.

The library can change daily without turning a refresh into a playback reset.

## Reliability principles established by these fixes

These problems led to a few rules that now guide the rest of the project:

1. The player is the source of truth for playback position and readiness.
2. UI state must never claim an operation has completed before it has.
3. Storage refreshes reconcile state; they do not replace a healthy session.
4. Long-form audio is a first-class use case, not an oversized music track.
5. User intent always outranks automatic recovery.

All five core problems are resolved in the current build. The broader capabilities
built on top of these foundations are documented in [FEATURES.md](./FEATURES.md),
with verification coverage in [TESTS.md](./TESTS.md).
