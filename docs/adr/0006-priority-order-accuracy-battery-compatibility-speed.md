# ADR-0006: Priority order - accuracy > battery > device compatibility > implementation speed

Status: Accepted

## Context

Network diagnostic tools are only useful if their results can be trusted; a fast, cheap,
universally-compatible probe that gives a wrong answer is worse than no answer. But
those four goals genuinely conflict - e.g. a second verification pass costs battery, and
an OEM-specific workaround improves compatibility at the cost of engineering time and
sometimes accuracy. Without a stated order, each conflict gets re-litigated ad hoc.

## Decision

When goals conflict, resolve in this order: **accuracy > battery > device compatibility >
implementation speed.**

## Consequences

- **Accuracy over battery**: hosts that fail the first ICMP pass get a second pass and a
  TCP fallback rather than being silently dropped from the LAN sweep results.
- **Accuracy over compatibility**: where an OEM-specific workaround would produce results
  that can't be verified, the app surfaces "unknown" instead of guessing - see
  [C-14](c-14-oem-divergence.md).
- **Battery over compatibility**: no polling loops are kept alive to work around a broken
  broadcast on one vendor's ROM.
- **Everything over implementation speed**: the build plan deliberately includes spikes,
  golden-file parser tests, and a multi-OEM device matrix rather than shipping the first
  approach that compiles. This is the most expensive line in the ordering and was budgeted
  accordingly (~39 developer-days end to end).
- Practical effect: when a new platform quirk is discovered, the default response is
  "detect and report" (see [C-14](c-14-oem-divergence.md)), not "paper over with a
  best-effort guess."
