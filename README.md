# Symphony

### Your music. Your files. Your rules.

Symphony is an offline Android audio player built for the awkwardly wide space
between a three-minute song and a seven-hour story.

This edition began with a simple personal need: keep the elegance of
[Symphony by zyrouge](https://github.com/zyrouge/symphony), but make it dependable
for a library that changes every day, contains both music and long-form audio, and
is controlled as often from earbuds and notifications as from the app itself.

It grew into an independently maintained edition focused on reliable playback,
direct control over local files, and a calmer interface.

> **Stay out of the way. Never lose the place. Keep the files yours.**

## Why this exists

Offline playback sounds simple until ordinary life gets involved:

- another app interrupts the audio;
- a backward seek snaps to an old timestamp;
- a six-hour recording restores silently while the UI claims it is playing;
- today's downloads do not appear until a rescan;
- that rescan forgets where you were;
- a pinned media control survives, but the player behind it does not;
- renaming or changing artwork interrupts the track you are listening to.

Symphony addresses those problems as one connected system. Playback, storage,
queue state, notifications, and UI all agree on the same active media identity.

The goal is not to turn an offline player into a streaming platform. It is to make
local audio feel trustworthy.

## What changed

### Playback that tells the truth

- Safe automatic recovery after temporary audio interruptions
- Complete backward and forward seeking, including multi-hour recordings
- Fast cached session restoration with pending Play support
- Atomic track transitions with no stale timestamp flash
- Repeat mode and queue behavior preserved across process death
- Optional duration-based position memory for podcasts and audio stories
- One navigation model for UI, notification, physical, and Bluetooth controls
- Functional pinned media controls after the app task is removed

### A library that follows your storage

- Automatic, non-destructive foreground refresh
- Manual rescan without losing playback state
- Single and multi-track permanent deletion
- Immediate visual reconciliation after storage operations
- Physical file renaming from inside the player
- Embedded metadata, lyrics, and cover-art editing
- Live title and artwork updates without restarting active playback
- Modern artwork support including WebP, HEIF, and HEIC

### Power without visual noise

- Compact bottom navigation with a subtle microbar indicator
- Two configurable navigation pages with hold-and-drag paging
- Optional edge buttons for users who prefer explicit controls
- Consistent modal sheets for context actions, sorting, playlists, and editors
- Minimal ring-and-dot multi-selection that leaves artwork unobstructed
- Modern queue selection and playlist actions
- Keyboard-aware metadata editing
- A native violet-to-teal splash with fixed geometry and no activity handoff
- Optional classic violet launcher identity

See [FEATURES.md](./FEATURES.md) for the complete capability overview and
[PROBLEMS.md](./PROBLEMS.md) for the reliability issues that shaped the project.

## Built for long-form audio, without forgetting music

Position memory is deliberately optional. By default, every track behaves like
music and starts from the beginning. Enable retention, choose a duration threshold,
and only longer recordings remember their place.

That small distinction captures the project's approach: sensible defaults, precise
control when requested, and no forced classification system for the user's files.

## Current release line

- Version: `2026.08.01`
- Android version code: `1`
- Minimum Android version: Android 9 / API 28
- Application ID: `io.github.wraithxxx.symphony`
- Release build: signed, R8 optimized, approximately 9.87 MB

Releases use a separate package ID from upstream Symphony, so the two editions can
coexist. Downloadable APKs and checksums are published through
[GitHub Releases](https://github.com/Wraithxxx/Symphony/releases).

## Build from source

Requirements:

- JDK 17
- Android SDK
- Node.js 20 and npm
- PowerShell on Windows for the included helper scripts

Generate the translation sources after cloning:

```powershell
npm ci
npm run prebuild
```

Run unit tests, lint, and build a debug APK:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Variant debug
```

Build an unsigned R8 release:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Variant release -Unsigned
```

Signed personal releases read these values from the ignored
`secrets/symphony-release.env` file:

- `SIGNING_KEYSTORE_FILE`
- `SIGNING_KEYSTORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Build and verify a signed release:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Variant release
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-apk.ps1 -ApkPath app\build\outputs\apk\release\app-release.apk
```

Signing credentials, local SDK configuration, generated APKs, and release mappings
are excluded from version control.

## Testing

The current build line passed 118 unit tests, Android lint with zero errors, APK
signature and alignment verification, debug-device regression checks, and a signed
R8 install/cold-launch smoke test.

[TESTS.md](./TESTS.md) describes the automated coverage, physical regression matrix,
release gate, and current device-testing limits.

## Project documentation

- [PROBLEMS.md](./PROBLEMS.md) — the five core reliability failures and their fixes
- [FEATURES.md](./FEATURES.md) — playback, file management, and UI capabilities
- [TESTS.md](./TESTS.md) — regression coverage and release verification
- [MEMORY.md](./MEMORY.md) — sanitized architecture and maintainer invariants

## Upstream and license

This project is based on
[`zyrouge/symphony`](https://github.com/zyrouge/symphony). The upstream author and
contributors created the foundation that made this edition possible and remain
credited in the application and repository history.

Symphony is distributed under the
[GNU Affero General Public License v3.0](./LICENSE), consistent with the upstream
project.
