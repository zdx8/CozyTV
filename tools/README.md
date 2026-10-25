# CozyTV companion tools

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

