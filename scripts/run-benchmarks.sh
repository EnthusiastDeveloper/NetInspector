#!/usr/bin/env bash
# Runs the LAN sweep pipeline's benchmark suite (docs/ideas.md #32) and writes
# the combined results to benchmarks/current.csv. Used by contributors iterating on the
# sweep pipeline and by .github/workflows/ci.yml's non-blocking `benchmark` job - see
# docs/adr/0009-hand-rolled-benchmark-harness.md for why this suite is never part of
# scripts/verify.sh's build gate.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

./gradlew :core:common:benchmark :core:model:benchmark :data:lan:benchmark --no-daemon "$@"

mkdir -p benchmarks
out="benchmarks/current.csv"
echo "module,benchmark,iterations,min_ms,median_ms,p95_ms,max_ms" >"$out"
for csv in core/common/build/benchmark-results.csv core/model/build/benchmark-results.csv data/lan/build/benchmark-results.csv; do
    if [ -f "$csv" ]; then
        tail -n +2 "$csv" >>"$out"
    fi
done

echo
echo "Combined results written to $out:"
cat "$out"
