# CleanYourTV cleanup toolbox

This directory gives coding agents everything needed to inspect and debloat a
TV over ADB without adding cleanup controls to the CozyTV app.

The 174 device profiles and their MIT license come from
[CleanYourTV](https://github.com/Gathin23/cleanyourtv). Profiles are starting
points, not universal lists. Always verify the TV model and firmware notes.

## Quick start

```bash
# Read-only plan for the connected TV
TV_HOST=<TV_IP>:5555 tools/cleanyourtv/clean.sh

# After reviewing every package and receiving user approval
TV_HOST=<TV_IP>:5555 tools/cleanyourtv/clean.sh --apply
```

The wrapper reads the model, device, product, and board from the TV, selects the
closest profile, prints it, and runs `tools/tvclean.py`. The default is always a
dry run.

Cleanup stays outside the CozyTV app. The user or Agent chooses scopes on the
computer with `CLEAN_SCOPES`:

- `ads`: advertising, sponsored rows, shops and recommendation services.
- `telemetry`: analytics, tracking, logging, cloud control and push services.
- `optional`: remaining preloaded packages; opt-in only.

The default is `ads,telemetry`. Use `CLEAN_SCOPES=all` only after reviewing the
entire device profile, or pass a custom comma-separated subset.

To force a profile:

```bash
TV_HOST=<TV_IP>:5555 \
  PROFILE=tools/cleanyourtv/profiles/xiaomi-mitv-astp0.txt \
  tools/cleanyourtv/clean.sh
```

To list known profiles:

```bash
python3 tools/cleanyourtv/select_profile.py --list ignored
```

## Recovery

`tvclean.py --apply` writes the recovery record before changing the TV and
prints its path. Undo with:

```bash
TV_HOST=<TV_IP>:5555 tools/cleanyourtv/restore.sh \
  /path/to/recovery.json
```

Packages are disabled with `pm disable-user --user 0`; nothing is uninstalled.
Core packages and the current HOME package are protected. Review device-specific
notes before applying a profile, especially packages affecting HDMI inputs,
voice, casting, Wi-Fi setup, firmware updates, or keyboards.

The adapter also refuses packages requesting `TV_INPUT_HARDWARE` and the Google
setup wizard by default. `--allow-input` and `--force-setupwraith` exist only for
an explicitly reviewed recovery plan; they should not be used routinely.
