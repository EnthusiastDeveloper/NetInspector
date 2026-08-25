#!/usr/bin/env bash
# Diffs benchmarks/current.csv (from scripts/run-benchmarks.sh) against the committed
# baseline (benchmarks/baseline.csv) and flags any median regression past
# REGRESSION_THRESHOLD_PERCENT (default 50). Always exits 0 - see
# docs/adr/0009-hand-rolled-benchmark-harness.md for why this suite is informational only,
# never a build gate: a single noisy CI runner can swing a wall-clock benchmark far more than
# a real regression would, so failing the build on it would mostly train contributors to
# ignore or bypass the check. `::warning::` lines still surface a real-looking regression as
# a GitHub Actions annotation for a human to look at.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

baseline="benchmarks/baseline.csv"
current="${1:-benchmarks/current.csv}"
threshold_percent="${REGRESSION_THRESHOLD_PERCENT:-50}"

if [ ! -f "$baseline" ]; then
    echo "No baseline at $baseline - skipping comparison."
    exit 0
fi
if [ ! -f "$current" ]; then
    echo "No current results at $current - skipping comparison."
    exit 0
fi

awk -F, -v threshold="$threshold_percent" '
    NR == FNR {
        if (FNR > 1) baseline_median[$1 "," $2] = $5
        next
    }
    FNR > 1 {
        key = $1 "," $2
        if (!(key in baseline_median)) {
            printf "%-55s (no baseline entry - new benchmark)\n", key
            next
        }
        base = baseline_median[key] + 0
        cur = $5 + 0
        if (base <= 0) next
        change = (cur - base) / base * 100
        printf "%-55s baseline=%9.4fms current=%9.4fms change=%+7.1f%%\n", key, base, cur, change
        if (change > threshold) {
            printf "::warning::Benchmark regression: %s median went from %.4fms to %.4fms (%+.1f%%)\n", key, base, cur, change
        }
    }
' "$baseline" "$current"
