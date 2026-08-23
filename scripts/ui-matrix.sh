#!/usr/bin/env bash
# UI validation helper: layout/orientation/posture sweeps across an emulator or a real
# device, driven entirely through adb so the same commands work on both. Screenshots land
# in a plain folder tree for a human or Claude to review afterward - no live tap-by-tap
# navigation needed for the orientation/size/posture axis itself.
#
# Background: design.md §12's device matrix splits testing in two: physical devices for
# anything radio-dependent (C-13 - emulators have no Wi-Fi), and layout (window size
# classes, §11.2 fold posture) on either. This script only ever touches the layout axis;
# it never drives Wi-Fi scanning, ping, or anything else feature-specific.
#
# Usage:
#   scripts/ui-matrix.sh devices
#   scripts/ui-matrix.sh boot <avd-name>
#   scripts/ui-matrix.sh install <serial> <apk-path>
#   scripts/ui-matrix.sh rotate <serial> <0|90|180|270>
#   scripts/ui-matrix.sh size <serial> <WIDTHxHEIGHT|reset>
#   scripts/ui-matrix.sh posture <serial> <closed|half-opened|opened>
#   scripts/ui-matrix.sh screenshot <serial> <output-file.png>
#   scripts/ui-matrix.sh sweep <serial> <output-dir> [label]
#   scripts/ui-matrix.sh kill <serial>
#
# `sweep` is the entry point: it cycles all four rotations, three width-size-class
# targets (compact/medium/expanded, computed from the device's actual density), and -
# only if the target reports foldable device-states - the three posture states, taking one
# screenshot per combination into <output-dir>/<label>/. It always restores rotation,
# size override and posture to their defaults afterward, even on failure.
#
# Run `sweep` once per screen you need validated: it captures whatever is on screen right
# now, so navigate to the screen first, then sweep, then navigate to the next screen and
# sweep again with a different label.
set -euo pipefail

usage() {
    grep '^#' "${BASH_SOURCE[0]}" | sed '1d;s/^# \{0,1\}//'
    exit 1
}

require_serial() {
    if [ -z "${1:-}" ]; then
        echo "error: missing serial. Run 'scripts/ui-matrix.sh devices' to list attached devices." >&2
        exit 1
    fi
}

cmd_devices() {
    adb devices -l
}

cmd_boot() {
    local avd="$1"
    export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
    export PATH="$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools"
    local before after serial
    before="$(adb devices | awk '/^emulator-/{print $1}')"
    nohup emulator -avd "$avd" -no-audio -no-boot-anim -gpu swiftshader_indirect \
        >/tmp/ui-matrix-"$avd".log 2>&1 &
    disown
    echo "booting $avd, log at /tmp/ui-matrix-$avd.log" >&2
    for _ in $(seq 1 40); do
        after="$(adb devices | awk '/^emulator-/{print $1}')"
        serial="$(comm -13 <(echo "$before" | sort) <(echo "$after" | sort) | head -1)"
        [ -n "$serial" ] && break
        sleep 3
    done
    if [ -z "$serial" ]; then
        echo "error: emulator did not appear in 'adb devices' within 2 minutes" >&2
        exit 1
    fi
    adb -s "$serial" wait-for-device
    until [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 3
    done
    echo "$serial"
}

cmd_install() {
    local serial="$1" apk="$2"
    require_serial "$serial"
    adb -s "$serial" install -r "$apk"
}

cmd_rotate() {
    local serial="$1" degrees="$2" n
    require_serial "$serial"
    case "$degrees" in
        0) n=0 ;;
        90) n=1 ;;
        180) n=2 ;;
        270) n=3 ;;
        *)
            echo "error: rotation must be one of 0, 90, 180, 270" >&2
            exit 1
            ;;
    esac
    adb -s "$serial" shell settings put system accelerometer_rotation 0
    adb -s "$serial" shell settings put system user_rotation "$n"
}

cmd_size() {
    local serial="$1" size="$2"
    require_serial "$serial"
    if [ "$size" = "reset" ]; then
        adb -s "$serial" shell wm size reset
    else
        adb -s "$serial" shell wm size "$size"
    fi
}

cmd_posture() {
    local serial="$1" posture="$2" n
    require_serial "$serial"
    case "$posture" in
        closed) n=1 ;;
        half-opened) n=2 ;;
        opened) n=3 ;;
        *)
            echo "error: posture must be one of closed, half-opened, opened" >&2
            exit 1
            ;;
    esac
    if ! adb -s "$serial" shell cmd device_state print-states 2>/dev/null | grep -q CLOSED; then
        echo "error: $serial does not report foldable device-states (real phones and non-foldable" \
            "AVDs never do - use NetInspector_Fold76 or NetInspector_Resizable)" >&2
        exit 1
    fi
    adb -s "$serial" shell cmd device_state state "$n"
}

cmd_screenshot() {
    local serial="$1" out="$2"
    require_serial "$serial"
    mkdir -p "$(dirname "$out")"
    adb -s "$serial" exec-out screencap -p >"$out"
}

supports_posture() {
    adb -s "$1" shell cmd device_state print-states 2>/dev/null | grep -q CLOSED
}

cmd_sweep() {
    local serial="$1" outdir="$2" label="${3:-$(date +%Y%m%d-%H%M%S)}"
    require_serial "$serial"
    local base="$outdir/$label"
    mkdir -p "$base"

    cleanup() {
        adb -s "$serial" shell settings put system user_rotation 0 2>/dev/null || true
        adb -s "$serial" shell wm size reset 2>/dev/null || true
        if supports_posture "$serial"; then
            adb -s "$serial" shell cmd device_state state 3 2>/dev/null || true
        fi
    }
    trap cleanup EXIT

    echo "rotations..." >&2
    for pair in 0:0 1:90 2:180 3:270; do
        local n="${pair%%:*}" deg="${pair##*:}"
        adb -s "$serial" shell settings put system accelerometer_rotation 0
        adb -s "$serial" shell settings put system user_rotation "$n"
        sleep 1
        cmd_screenshot "$serial" "$base/rotation-$deg.png"
    done
    adb -s "$serial" shell settings put system user_rotation 0

    echo "width size classes..." >&2
    local density
    density="$(adb -s "$serial" shell wm density 2>/dev/null | grep -oP '\d+' | head -1)"
    density="${density:-420}"
    for pair in compact:400 medium:700 expanded:900; do
        local name="${pair%%:*}" dp="${pair##*:}"
        local px=$((dp * density / 160))
        adb -s "$serial" shell wm size "${px}x1200"
        sleep 1
        cmd_screenshot "$serial" "$base/width-$name.png"
    done
    adb -s "$serial" shell wm size reset

    if supports_posture "$serial"; then
        echo "fold postures..." >&2
        for pair in 1:closed 2:half-opened 3:opened; do
            local n="${pair%%:*}" name="${pair##*:}"
            adb -s "$serial" shell cmd device_state state "$n"
            sleep 1
            cmd_screenshot "$serial" "$base/posture-$name.png"
        done
        adb -s "$serial" shell cmd device_state state 3
    else
        echo "posture not supported on $serial, skipped (real device or non-foldable AVD)" >&2
    fi

    trap - EXIT
    cleanup
    echo "$base"
}

cmd_kill() {
    local serial="$1"
    require_serial "$serial"
    adb -s "$serial" emu kill
}

main() {
    local sub="${1:-}"
    [ -z "$sub" ] && usage
    shift
    case "$sub" in
        devices) cmd_devices "$@" ;;
        boot) cmd_boot "$@" ;;
        install) cmd_install "$@" ;;
        rotate) cmd_rotate "$@" ;;
        size) cmd_size "$@" ;;
        posture) cmd_posture "$@" ;;
        screenshot) cmd_screenshot "$@" ;;
        sweep) cmd_sweep "$@" ;;
        kill) cmd_kill "$@" ;;
        *) usage ;;
    esac
}

main "$@"
