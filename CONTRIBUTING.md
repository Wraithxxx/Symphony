# Contributing

Thanks for helping improve Symphony.

This repository is an independently maintained edition of
[`zyrouge/symphony`](https://github.com/zyrouge/symphony). Contributions should fit
the project's offline-first purpose and preserve the playback, storage, and UI
invariants described in [MEMORY.md](./MEMORY.md).

## Before opening an issue

- Search existing issues first.
- Use the latest available build when practical.
- For bugs, include the Android version, device model, audio format, storage
  provider, and exact reproduction steps.
- Describe what was audible and what the UI displayed when reporting playback
  state problems.
- Never attach private audio, signing credentials, local configuration, or personal
  storage paths.

Feature requests are most useful when they explain the user problem before proposing
a specific interface.

## Development setup

Use JDK 17, an Android SDK, Node.js 20, and npm. Generate required sources before
building:

```powershell
npm ci
npm run prebuild
```

Run the normal debug verification pipeline:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build.ps1 -Variant debug
```

See [TESTS.md](./TESTS.md) for the regression matrix and release gate.

## Pull requests

- Keep each pull request focused on one coherent problem or feature.
- Preserve existing behavior outside the stated scope.
- Add or update focused tests for policy and state changes.
- Run unit tests, lint, and an appropriate APK assembly.
- Report physical-device testing for lifecycle, playback, Bluetooth, notification,
  or storage-provider changes.
- Do not commit generated APKs, signing material, local SDK configuration, private
  media, or machine-specific paths.
- Update the public documentation when behavior changes.

Large architectural changes should begin with an issue so their state ownership and
compatibility impact can be discussed first.

## License

By contributing, you agree that your contribution is distributed under the
[GNU Affero General Public License v3.0](./LICENSE).
