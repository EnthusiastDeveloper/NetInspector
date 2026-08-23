#!/usr/bin/env bash
# One-time setup: installs githooks/* into the real git hooks directory. Git does not
# version that folder, so this has to be run once after cloning (see CONTRIBUTING.md).
# Uses `git rev-parse --git-path hooks` rather than a hardcoded `.git/hooks` because in a
# worktree checkout `.git` is a file, not a directory, and hooks live in the common git
# dir shared by the main checkout and every worktree.
set -euo pipefail
repo_root="$(git rev-parse --show-toplevel)"
hooks_dir="$(git -C "$repo_root" rev-parse --git-path hooks)"
mkdir -p "$hooks_dir"

for hook in "$repo_root"/githooks/*; do
    name="$(basename "$hook")"
    install -m 755 "$hook" "$hooks_dir/$name"
    echo "installed $name"
done
