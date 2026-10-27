# CozyTV companion tools

## Release

`release.sh` builds, signs, verifies and publishes a release:

```bash
tools/release.sh 0.4.0              # build + sign + verify + dist/
tools/release.sh 0.4.0 --publish    # tag + push + GitHub Release
```

It bumps the version defaults in `app/build.gradle.kts`, refuses to publish an
unsigned or debug-signed APK, and writes `dist/CozyTV-vX.Y.Z-release.apk` plus
a `.sha256` file.

Signing uses `keystore.properties` in the repository root (gitignored) and the
keystore at `app/signing/cozytv-release.keystore` — see
`keystore.properties.example`. The script refuses to run without them.

## Install on the TV

`install-on-tv.sh` installs the release APK and handles the signature change:
v0.3.x was signed with the local Android debug key, v0.4.0+ uses the real
release key (`CN=CozyTV`), so Android refuses an in-place upgrade. The script
backs up `/data/data/app.cozytv` (root only), uninstalls, installs and restores
the data, so favourites, live sources and the home layout survive.

```bash
tools/install-on-tv.sh                 # dist/ 里最新的 release 包
tools/install-on-tv.sh --debug         # debug 包
```

The TV's address changes with DHCP — it was `<TV_IP>` and is
`<TV_IP>` now (MAC `00:00:00:00:00:00`, confirmed via the ARP table).
Override it with `TV_HOST=<ip>:5555`. Network debugging has to be enabled on
the TV: 设置 → 关于 → 连点版本号 7 次 → 开发者选项 → 「ADB 调试」+「网络调试」.

## On-device smoke test

`test-on-device.sh` installs the APK, launches CozyTV, captures logcat and
checks six scenarios (startup, home screen, live preview, embedded TVLive,
backlight bridge, HOME resolution):

```bash
TV_HOST=<TV_IP>:5555 tools/test-on-device.sh
```

## HOME diagnostics

`home_status.sh` is read-only. It prints the current default HOME and every enabled HOME activity;
it never disables the stock launcher.

```bash
TV_HOST=<TV_IP>:5555 tools/home_status.sh
```

## System cleanup adapter

`tvclean.py` accepts the package-list format used by CleanYourTV (`debloat.txt`). It does not
bundle or guess a device profile: use a list that has been checked against the exact TV model and
firmware.

```bash
# Read-only plan
python3 tools/tvclean.py --device <TV_IP>:5555 --list examples/my-tv/debloat.txt

# Apply one reviewed list
python3 tools/tvclean.py --device <TV_IP>:5555 --list examples/my-tv/debloat.txt --apply

# Restore exactly the packages that were enabled before that run
python3 tools/tvclean.py --device <TV_IP>:5555 \
  --restore ~/.cozytv/cleanup/recovery-YYYYMMDD-HHMMSS.json
```

Safety properties:

- Dry run is the default.
- The recovery record is written before any state change.
- Packages already disabled before the run stay disabled during restore.
- Core packages, the current HOME package and HOME-looking packages are refused by default.
- The tool never uninstalls or clears app data.
- The cleanup list runs on the computer; CozyTV does not silently change system packages.

Network ADB is plaintext TCP. Close debugging and revoke the host authorization after setup.

The integrated device profiles and wrappers are under
[`cleanyourtv/`](cleanyourtv/README.md):

```bash
TV_HOST=<TV_IP>:5555 tools/cleanyourtv/clean.sh
```

