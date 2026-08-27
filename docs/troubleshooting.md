# Troubleshooting & bug reports

NetInspector has two local, opt-in-where-it-matters tools for turning a problem into
something you can actually attach to a bug report, without a live ADB session:
**crash reports** and the **debug bundle**. Both live entirely on-device until you
explicitly share them - nothing is ever uploaded automatically, matching the app's
no-telemetry, no-accounts stance (see the root [README](../README.md)).

Both are reachable from **Settings → Debug & diagnostics**.

## The two options, and when to use each

| | Local crash reporting | Debug bundle export |
|---|---|---|
| **Use it when** | NetInspector actually crashed | Something's wrong but the app didn't crash - a stuck scan, a missing/misidentified device, an unexpected diagnostic result |
| **Opt-in?** | Yes - off by default, enabled in Settings | No - always available, nothing to turn on |
| **Captures** | The crash's stack trace, app/device info, and the last 50 lines of that session's app log | A snapshot of your current connection, discovered LAN hosts, latest Wi-Fi scan, and recent diagnostic runs, plus recent app log lines |
| **When it's written** | Automatically, the moment a crash happens (if enabled) | On demand, whenever you tap "Export debug bundle" |
| **Where it's stored** | App-private storage, kept until the 20 most recent reports fill up | App-private cache, regenerated fresh each time you export |
| **How you're prompted** | A dismissible banner on the Dashboard the next time you open the app after a crash | N/A - you trigger it yourself |

If you're not sure which applies, ask: did the app close on you? Crash report. Is it
still running but doing something wrong? Debug bundle.

## Local crash reporting

Off by default. Turn it on in **Settings → Debug & diagnostics → Local crash
reporting**.

Once enabled, if NetInspector crashes, it writes a plain-text report to app-private
storage before handing off to the normal Android crash handling (the system crash
dialog still appears exactly as it would otherwise - this only adds a local file
write first). The report contains:

- A timestamp, the app version, Android version, and device model
- The crash's stack trace
- The last 50 lines the app itself logged this session (e.g. "sweep started") -
  **not** a record of what you tapped or which screens you visited

The next time you open the app after a crash, the Dashboard shows a dismissible
"Crash report available" banner. Exporting from there (or from Settings' "Export
crash report" button, which always targets the most recent report) opens the normal
Android share sheet, so you choose the destination - email, a GitHub issue attachment,
a chat app, wherever. Dismissing the banner doesn't delete the report; it's still
reachable later via Settings.

The 20 most recent crash reports are kept; older ones are pruned automatically.

## Debug bundle export

Always available, no opt-in required. **Settings → Debug & diagnostics → Export
debug bundle** builds a zip on the spot containing:

- `snapshot.txt` - current connection details, discovered LAN hosts and sweep
  progress, the latest Wi-Fi scan results, and a summary of recent diagnostic runs
  (ping, traceroute, port scans, ...)
- `logs.txt` - recent app log output from this session

This is a live snapshot of what the app currently has in memory, not your full
persisted scan history - for that, use the existing CSV/JSON export on the History
screens instead. Exporting opens the same share sheet as a crash report.

## Redaction

Both reports strip identifying network details before the text is ever written to
disk: local IPv4 addresses (the private/link-local/loopback ranges an active scan can
actually produce) are replaced with `<redacted-ip>`, and any SSID the app currently
knows about (your connected network, anything visible in the latest Wi-Fi scan) is
replaced with `<redacted-ssid>`. This happens unconditionally, whether or not you ever
export the file.

## For contributors

The crash handler, debug-bundle builder, redaction, and log ring buffer live under
[`app/.../debug/`](../app/src/main/kotlin/dev/enthusiastdev/netinspector/debug/) and
[`core/common/.../redact/`](../core/common/src/main/kotlin/dev/enthusiastdev/netinspector/core/common/redact/Redactor.kt)
/ [`log/`](../core/common/src/main/kotlin/dev/enthusiastdev/netinspector/core/common/log/LogRingBuffer.kt).
Both features share the same redaction pass and the same `ShareFileLauncher` (a thin
wrapper around `FileProvider` + `Intent.ACTION_SEND`) rather than duplicating
share-sheet plumbing. See ideas.md items
[#21](ideas.md#21-opt-in-local-crash-reporting) and
[#22](ideas.md#22-in-app-debug-bundle-export) for the original scoping.
