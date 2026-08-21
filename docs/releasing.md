# Cutting a release

Releases are tag-triggered. `.github/workflows/release.yml` does the rest: lint,
static analysis, unit tests, a signed release build, signature/version
verification, an emulator install-and-launch smoke test, and finally publishing
a GitHub Release with the APK attached. See that file for the exact steps.

## Steps

1. Bump `appVersionCode` and `appVersionName` in `gradle/libs.versions.toml`.
   `appVersionCode` must be strictly greater than the previous release's - the
   workflow checks this against the previous git tag and fails the build
   otherwise, since both Android's package installer and Obtainium key updates
   off it.
2. Commit that bump on `main`.
3. Tag it and push the tag:
   ```bash
   git tag v0.2.0
   git push origin v0.2.0
   ```
   The tag (minus the `v`) must exactly match `appVersionName` - the workflow
   checks this too.
4. Watch the [Actions tab](../../actions) for the `Release` workflow. If every
   job passes, the release is published automatically - there's no separate
   manual approval step (see below for why).

To dry-run the pipeline without publishing anything (e.g. after changing the
workflow itself), trigger it manually from the Actions tab
(`workflow_dispatch`) on any branch. It runs the same build, verification and
smoke-test gates but skips the tag/version checks and never reaches `publish`.

## Auto-publish vs. draft

The `publish` job creates the release directly (no `draft: true`), so a
passing tag push is immediately live and visible to Obtainium. This matches
the fact that tagging itself is already the deliberate "this is ready"
decision. If you'd rather review or hand-edit the auto-generated release
notes before users see them, add `draft: true` to the `softprops/action-gh-release`
step and publish manually from the Releases page instead.

## Secrets

Configured under **Settings → Secrets and variables → Actions**. All five are
required for `assembleRelease` to produce a correctly-signed, verifiable APK:

| Secret | Purpose |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | base64 of the release `.jks` keystore file |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | signing key alias within the keystore |
| `RELEASE_KEY_PASSWORD` | signing key password |
| `RELEASE_CERT_SHA256` | expected SHA-256 fingerprint of the signing cert - the workflow refuses to publish if the built APK's actual signature doesn't match, to catch a stale/wrong keystore secret before it ships something existing users can't update to |

The keystore itself lives outside the repo (never committed - see the comment
at `app/build.gradle.kts:10-13`) and isn't reproducible if lost: losing it
means every future release gets a different signature, and Android refuses to
install a differently-signed APK over an existing one. Existing users would
have to uninstall and reinstall from scratch, losing local history. Keep an
encrypted backup of the keystore file and its passwords somewhere durable
(password manager, encrypted archive) - not just on one machine.
