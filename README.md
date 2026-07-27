# Symphony Custom

Personal Android music-player fork maintained in
[`Wraithxxx/Symphony-Custom`](https://github.com/Wraithxxx/Symphony-Custom).
The installed application is named **Symphony**, uses the independent package
ID `io.github.wraithxxx.symphony`, and supports Android 9 and later.

This fork is based on
[`zyrouge/symphony`](https://github.com/zyrouge/symphony), with extensive
playback reliability, library management, metadata editing, navigation, and
UI/UX enhancements. The upstream project and its contributors remain the
original authors of Symphony.

## Project references

- [`Features.md`](./Features.md) records implemented enhancements.
- [`Problems.md`](./Problems.md) records the resolved core problems.
- [`tests.md`](./tests.md) records regression and device-test coverage.
- [`memory.md`](./memory.md) is the development and architectural reference.

## Building

Debug build:

```powershell
.\gradlew.bat assembleDebug
```

Release builds are R8 optimized and require these environment variables:

- `SIGNING_KEYSTORE_FILE`
- `SIGNING_KEYSTORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Signing keys and local release credentials must never be committed.

## License

This fork remains licensed under the
[GNU Affero General Public License v3.0](./LICENSE), consistent with the
upstream project.
