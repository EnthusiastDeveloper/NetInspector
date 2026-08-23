#!/usr/bin/env bash
# Enforces this project's writing conventions (see CONTRIBUTING.md "Writing conventions"):
# no em-dash or en-dash anywhere, no Co-Authored-By trailer, no AI-attribution footer.
#
# Usage:
#   scripts/check-banned-text.sh <file>...   check each file
#   scripts/check-banned-text.sh             check stdin
#
# Exits non-zero and prints one line per violation if anything is found. Used by
# githooks/commit-msg and by the dev-feature skill before opening a PR.
set -euo pipefail

fail=0

check_content() {
    local label="$1" content="$2"
    if grep -qP '[\x{2013}\x{2014}]' <<<"$content"; then
        echo "banned character: em-dash or en-dash found in $label" >&2
        fail=1
    fi
    if grep -qiP 'co-authored-by' <<<"$content"; then
        echo "banned trailer: Co-Authored-By found in $label" >&2
        fail=1
    fi
    if grep -qiP 'generated with.{0,20}claude' <<<"$content"; then
        echo "banned footer: AI-attribution footer found in $label" >&2
        fail=1
    fi
}

if [ "$#" -eq 0 ]; then
    check_content "stdin" "$(cat)"
else
    for f in "$@"; do
        check_content "$f" "$(cat "$f")"
    done
fi

exit "$fail"
