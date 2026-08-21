# ADR-0007: Window size classes and fold posture built in from day one

Status: Accepted

## Context

Retrofitting adaptive layout (phone/tablet/foldable) onto screens originally built
single-pane is expensive - list-detail structure has to be threaded through navigation,
state hoisting, and every screen's layout root after the fact. NetInspector's own scope
(README) explicitly targets phones, tablets, and foldables, not phones-first-then-maybe.

## Decision

Window size classes and fold-aware layout (`DevicePosture`) are foundational from the
first screen built, not a later enhancement. List-detail panes are part of each screen's
initial structure.

## Consequences

- Screen structure across the app follows a consistent adaptive pattern
  (`TabletopSplitLayout`, `DevicePostureFlow`) rather than each screen inventing its own
  large-screen behavior later.
- UI state must survive rotation and fold/unfold from the start, which shaped ViewModel
  state design everywhere, not just on large-screen-specific code paths.
- Cost: every new screen carries adaptive-layout consideration as part of its initial
  build, not as optional follow-up work - slower initial delivery per screen, in exchange
  for not needing a second pass across the whole app later.
