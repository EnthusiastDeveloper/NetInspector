# C-13: Emulator cannot scan Wi-Fi

Status: Accepted

**Symptom** Empty scan results in every emulator image.

**Mitigation** Physical devices are mandatory for any scanning work. The device matrix in
`docs/testing.md` §6 is a hard requirement, not a nice-to-have - both deployment devices
must pass every phase. CI runs JVM unit tests only.
