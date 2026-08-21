# C-14: OEM divergence

Status: Accepted

See also [ADR-0006](0006-priority-order-accuracy-battery-compatibility-speed.md).

**Symptom** Behaviour differs sharply on Xiaomi, Samsung, Huawei and others: extra
background limits, additional permission prompts, modified Wi-Fi stacks, and in some cases
suppressed scan broadcasts.

**Mitigation** Given `accuracy > compatibility`, the policy is: detect and report, do not
paper over. If a device fails to deliver scan broadcasts, the app surfaces "no scan
results received - this device may restrict background Wi-Fi scanning" rather than
falling back to a polling loop that burns battery and still produces stale data.

**Reduced scope here** With only two known deployment devices, this risk is largely
theoretical - but it becomes live the moment the app is installed on a third device. Keep
the detect-and-report behaviour rather than hard-coding around whatever the two known
devices happen to do.
