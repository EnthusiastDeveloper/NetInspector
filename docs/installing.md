# Installing NetInspector

There's no Play Store listing. Every release is a signed APK attached to a
[GitHub Release](../../releases). Pick whichever path fits:

- **Just trying it out?** → [Manual install](#manual-install-no-auto-updates)
- **Using it long-term?** → [Install via Obtainium](#install-via-obtainium-recommended-for-regular-use)

All releases are signed with the same key, so any of these methods can install over
an existing copy without uninstalling first - your data is preserved either way.

## Manual install (no auto-updates)

1. Open the [Releases](../../releases) page and download the `.apk` file from the
   latest release (avoid Draft/Pre-release entries unless you specifically want them).
2. *(Optional but recommended)* Verify the download against the `.sha256` file
   attached to the same release:
   ```bash
   sha256sum -c NetInspector-vX.Y.Z.apk.sha256
   ```
3. On your phone, open the downloaded APK (via the notification, or your file
   manager / Downloads app). Android will prompt you to allow installs from that
   source ("Install unknown apps") the first time - approve it for the app you used
   to open the file (e.g. your browser or file manager).
4. Confirm the install prompt.

**To update later**, repeat the same steps with the new release's APK. Installing
over the existing app keeps your scan history and settings, since it's signed with
the same certificate.

## Install via Obtainium (recommended for regular use)

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub Releases and gives
you a normal "update available" experience without the Play Store, similar to
F-Droid but for repos that don't publish there.

1. Install Obtainium itself first (it isn't on the Play Store either) - get it from
   its [own releases page](https://github.com/ImranR98/Obtainium/releases) or
   [obtainium.imranr.dev](https://obtainium.imranr.dev), using the manual-install
   steps above.
2. Do a one-time [manual install](#manual-install-no-auto-updates) of NetInspector
   itself, so there's something for Obtainium to attach to and manage going forward.
3. In Obtainium, tap **Add App** and paste this repo's URL:
   ```
   https://github.com/EnthusiastDeveloper/NetInspector
   ```
4. Leave the source as GitHub. Since each release only publishes one APK, no asset
   filter is needed. Save.
5. Obtainium will now show an update whenever a new release is published, and can
   check automatically in the background if you enable that in its settings.

## Why sideloading at all?

There's currently no Play Store listing, so GitHub Releases plus Obtainium (or
manual install) is the distribution channel. See the
[Scope](../README.md#scope) section in the README for what the app does and
doesn't do on your network.
