# NetInspector - Documentation

Design, decision, and process documentation for the app. User-facing install instructions
live in [`installing.md`](installing.md); everything else here is for contributors.

> **Package ID**: `dev.enthusiastdev.netinspector`. For a sideloaded app this is the one
> identifier that is painful to change later: it keys the app's data directory, and a
> collision with a Play Store package causes signature-mismatch install failures that are
> tedious to diagnose. It was chosen as a personal namespace specifically to make that
> collision impossible.

## Documents

| File | Purpose | Read when |
|---|---|---|
| [`design.md`](design.md) | End-to-end technical design: architecture, domain model, subsystem specs | Before writing any code; the source of truth for *what* and *why* |
| [`adr/`](adr/README.md) | One file per decision - project-level choices (`ADR-XXXX`) and Android platform constraints with their mitigations (`C-XX`) | Whenever something doesn't work and you suspect the platform, or before revisiting a settled choice |
| [`implementation-plan.md`](implementation-plan.md) | Phased build order with tasks, acceptance criteria, spikes and estimates | During implementation; work top to bottom |
| [`feature-development.md`](feature-development.md) | End-to-end process for a feature: requirements, plan, implementation, tests, build gate, UI validation, documentation, PR - and which parts `scripts/` automates | Starting non-trivial feature work; also available as the `/feature-dev` Claude Code skill |
| [`testing.md`](testing.md) | The full test plan: JVM/instrumented/manual split, boundary and negative-path coverage, the UI interaction and gesture matrix, the per-capability corner-case checklist, the manual device matrix, and the UI validation process | Before writing tests for a change, or planning a UI validation pass |
| [`open-items.md`](open-items.md) | What's left after the phased plan shipped: still-open plan items, and gaps found afterward | Before picking up new work on a "finished" app |
| [`improvement-ideas.md`](improvement-ideas.md) | Brainstormed feature/UX ideas, ranked by ROI | When looking for what to build next |
| [`device-identification-ideas.md`](device-identification-ideas.md) | Brainstormed techniques to make LAN device labels more specific than the TTL/port-signature fallbacks, ranked by ROI | When improving how confidently the app names a device |
| [`references/dependency-versions.md`](references/dependency-versions.md) | Dependency version notes | When bumping a dependency |
| [`installing.md`](installing.md) | End-user install instructions (sideload, Obtainium) | Linked from the root README |
| [`troubleshooting.md`](troubleshooting.md) | Crash reports vs. debug-bundle export: what each captures, redaction, when to use which | Reporting or diagnosing a bug without ADB |
| [`releasing.md`](releasing.md) | Release process, keystore handling, versioning | Cutting a release |

## Fixed decisions

The project-level decisions settled before drafting, and every Android platform
constraint discovered since, are recorded as individual ADRs in [`adr/`](adr/README.md)
- see that folder's index for the full list and the two ID schemes it uses
(`ADR-XXXX` for project decisions, `C-XX` for platform constraints). Changing any of
them invalidates parts of the design; each ADR notes what it affects.

## A note on scope of use

Active host discovery and port scanning against networks you do not own or administer
is, depending on jurisdiction, somewhere between impolite and unlawful. The app shows a
one-time acknowledgement on first LAN scan (see [`design.md`](design.md) §11.4). Keep it.
