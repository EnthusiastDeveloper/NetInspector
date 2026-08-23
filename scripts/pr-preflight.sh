#!/usr/bin/env bash
# Mechanical checks before opening a PR. Does not write the PR description - that needs
# judgment - it only catches the things a script can catch reliably:
#   1. the branch is rebased onto the latest origin/main (CONTRIBUTING.md's linear-history rule)
#   2. no commit message in the branch's range violates the writing conventions
#   3. a summary of which source paths changed without a matching docs/ change, as a nudge
#      for the documentation step - not a hard failure, since not every change needs docs.
set -euo pipefail
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

git fetch origin main --quiet

merge_base="$(git merge-base HEAD origin/main)"
main_head="$(git rev-parse origin/main)"
if [ "$merge_base" != "$main_head" ]; then
    echo "FAIL: branch is not rebased onto latest origin/main (run: git rebase origin/main)" >&2
    exit 1
fi
echo "OK: branch is rebased onto origin/main"

fail=0
while IFS= read -r sha; do
    if ! git log -1 --format=%B "$sha" | scripts/check-banned-text.sh; then
        echo "  ^ offending commit: $sha $(git log -1 --format=%s "$sha")" >&2
        fail=1
    fi
done < <(git rev-list origin/main..HEAD)

if [ "$fail" -ne 0 ]; then
    echo "FAIL: one or more commit messages violate the writing conventions" >&2
    exit 1
fi
echo "OK: no commit message violates the writing conventions"

changed_files="$(git diff --name-only origin/main..HEAD)"
non_doc_changes="$(grep -vE '^docs/|\.md$' <<<"$changed_files" || true)"
doc_changes="$(grep -E '^docs/|\.md$' <<<"$changed_files" || true)"
echo
echo "Changed source files:"
echo "${non_doc_changes:-  (none)}"
echo
echo "Changed docs:"
echo "${doc_changes:-  (none - confirm this change genuinely needs no documentation update)}"
