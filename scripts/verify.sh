#!/usr/bin/env bash
# Local build gate. Mirrors .github/workflows/ci.yml step for step, so a pass here means
# CI passes too. Used directly by contributors (CONTRIBUTING.md), by githooks/pre-push,
# and by the dev-feature skill (.claude/skills/dev-feature/SKILL.md).
set -euo pipefail
cd "$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"

./gradlew ktlintCheck detekt test assembleDebug "$@"
