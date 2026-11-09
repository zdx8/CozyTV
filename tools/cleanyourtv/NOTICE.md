# CleanYourTV integration notice

The files under `profiles/` and `LICENSE` are from
[CleanYourTV](https://github.com/Gathin23/cleanyourtv), copyright (c) 2026 Gathin,
licensed under the MIT License.

`clean.sh`, `select_profile.py`, and `restore.sh` are CozyTV wrappers around
`tools/tvclean.py`. They select a device-specific community profile, default to
a read-only dry run, create a recovery record before applying changes, and use
`pm disable-user` rather than uninstalling packages.
