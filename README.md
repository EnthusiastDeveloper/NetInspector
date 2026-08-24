# NetInspector

Android network analyzer - scan the Wi-Fi environment, discover hosts on your local
network, and run diagnostic tools. Runs entirely on-device: no root, no NDK, no
accounts, no telemetry, no analytics.

## Features

- **Wi-Fi analysis** - visible access points with band, channel, width, security and
  standard; channel occupancy graph; a recommendation engine for picking a clear channel;
  flags a known access point whose security, standard, or channel changes between scans.
- **LAN discovery** - enumerates hosts on the connected subnet and identifies them via
  mDNS, SSDP and vendor OUI lookup, with a confidence label on each guess.
- **Diagnostics** - ping, traceroute, reverse DNS, and port scanning, launchable directly
  from a discovered host's detail screen.
- **History & export** - scan and diagnostic runs are persisted locally and can be
  exported to CSV/JSON.
- **Automatic background scanning** - opt-in periodic Wi-Fi scans so history builds up
  without opening the app, plus optional alerts when a new device joins the LAN or a
  known one vanishes or reappears. Devices you mark "known" (a laptop that sleeps, a
  phone that leaves and returns home) are excluded from vanish/reappear alerts.
- **Adaptive UI** - built for phones, tablets and foldables from the start: window size
  classes, fold-aware layout, and UI state preserved across rotation.
- **Troubleshooting** - opt-in local crash reports and on-demand debug-bundle export for
  filing bug reports without ADB, both local-only until you choose to share them. See
  [docs/troubleshooting.md](docs/troubleshooting.md).

## Screenshots

*(placeholder - add screenshots of the Wi-Fi, Devices and Diagnostics screens here)*

## Install

Releases are published as APKs on the [Releases](../../releases) page. There is no
Play Store listing. See [docs/installing.md](docs/installing.md) for step-by-step
instructions, whether you want a quick sideload to try it out or ongoing updates via
[Obtainium](https://github.com/ImranR98/Obtainium).

## Building

```bash
./gradlew assembleDebug
```

Requires JDK 17+. Compiled against Android SDK 35 (`compileSdk`/`targetSdk`), with a
minimum supported version of Android 13 (`minSdk` 33).

Signed release builds (`assembleRelease`) require a `keystore.properties` pointing at
a release keystore, kept outside the repo - see [docs/releasing.md](docs/releasing.md)
for the full release process.

## Scope

Everything runs against the network you're currently connected to, using only
public/unprivileged Android APIs - no packet capture, no monitor mode, no
deauthentication or injection, no root requirement. Active discovery and port scanning
should only be pointed at networks you own or are authorized to test.

## Contributing

Branch off `main`, keep it rebased, and make sure `./gradlew ktlintCheck detekt test
assembleDebug` passes before opening a PR. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the full process.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
