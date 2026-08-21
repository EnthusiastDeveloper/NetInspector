# C-10: Background execution and Doze

Status: Accepted

**Symptom** Continuous monitoring stops when the screen is off.

**Cause** Background scan throttling (1 per 30 min) plus Doze restrictions.

**Mitigation** Continuous monitoring exists only as an explicit, user-started foreground
service with a persistent notification. The app does not claim uninterrupted background
logging. `WorkManager` handles deferrable periodic work only, and its results are
timestamped so gaps are visible rather than interpolated.
