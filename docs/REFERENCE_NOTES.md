# Reference notes

CozyTV is an independent UI implementation. Its product and engineering decisions were informed by:

- [Aerial TV](https://github.com/zdx8/aerial-tv): Android TV HOME behavior, TV-sized canvas,
  remote-focus clarity, app discovery across TV and regular launcher categories, and performance
  constraints on low-end television hardware. The MiTV `TvService` backlight bridge
  (`MitvPanel.kt`) is adapted from Aerial TV's verified ASTP0 implementation under its MIT
  license; the source attribution is retained in the file.
- [TVLive](https://github.com/zdx8/TVLive): the complete channel-list, drawer, subscription and
  Media3 playback app is vendored as the `:tvlive` library module. TVLive is Apache-2.0 licensed;
  its LICENSE and NOTICE are kept in `tvlive/` and packaged under `assets/tvlive/` in the APK.
  CozyTV's manual GitHub Releases update flow, download progress, release-asset selection and
  system-installer handoff are adapted from TVLive's update approach.
- [CleanYourTV](https://github.com/Gathin23/cleanyourtv): the safe package-list workflow, explicit
  recovery records, and the principle that irreversible cleanup must be opt-in and device-specific.
  Its MIT-licensed device profiles are integrated under `tools/cleanyourtv/`; the upstream license
  is retained there, and the helper scripts retain dry-run and recovery safeguards.

TVLive is the only wholesale vendored source module, with its upstream license and attribution
preserved. The CleanYourTV adapter reuses its public `debloat.txt` profiles and adds independent
dry-run, device-selection, input-protection, and recovery wrappers without copying its launcher application source.

