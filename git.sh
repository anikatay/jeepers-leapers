#!/usr/bin/env bash
set -euo pipefail

# The history was rewritten, so teammates must fetch with force and reset
# local branches instead of using git pull.

REMOTE="${GIT_REMOTE:-origin}"
SYNC_ALL=false
BRANCH=""

usage() {
  cat <<'EOF'
Usage:
  ./git.sh                 Sync the currently checked-out branch
  ./git.sh BRANCH          Sync a specific local branch
  ./git.sh --all           Sync every existing local branch with origin
  GIT_REMOTE=upstream ./git.sh

The working tree must be clean. This script intentionally does not merge or
rebase the old history. It resets local branches to the rewritten remote
history and checks for Co-authored-by trailers.

For the safest option, make a fresh clone instead:
  git clone <repository-url>
EOF
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --all)
      [[ -z "$BRANCH" ]] || die "--all cannot be combined with a branch name"
      SYNC_ALL=true
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      die "unknown option: $1"
      ;;
    *)
      [[ -z "$BRANCH" ]] || die "only one branch may be specified"
      [[ "$SYNC_ALL" == false ]] || die "--all cannot be combined with a branch name"
      BRANCH="$1"
      ;;
  esac
  shift
done

git rev-parse --git-dir >/dev/null 2>&1 ||
  die "run this script from a Git working tree"

git remote get-url "$REMOTE" >/dev/null 2>&1 ||
  die "Git remote '$REMOTE' does not exist"

if ! git diff --quiet ||
   ! git diff --cached --quiet ||
   [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
  die "working tree is not clean; commit or stash changes before syncing"
fi

printf 'Fetching rewritten history from %s...\n' "$REMOTE"
git fetch "$REMOTE" --prune --prune-tags --force

check_trailers() {
  local ref="$1"

  if git log "$ref" --format='%B' |
     grep -iE '^[[:space:]]*co-authored-by:' >/dev/null; then
    die "Co-authored-by trailer found in $ref; stop and contact the repository administrator"
  fi
}

remote_ref_exists() {
  git show-ref --verify --quiet "refs/remotes/$REMOTE/$1"
}

sync_branch() {
  local branch="$1"
  local remote_ref="$REMOTE/$branch"
  local old_hash=""
  local new_hash

  if ! remote_ref_exists "$branch"; then
    printf 'Skipping %s: no matching remote branch\n' "$branch"
    return 0
  fi

  old_hash="$(git rev-parse --verify "$branch" 2>/dev/null || true)"
  new_hash="$(git rev-parse --verify "$remote_ref")"

  if [[ "$old_hash" == "$new_hash" ]]; then
    printf '%s is already up to date\n' "$branch"
  else
    printf 'Resetting %s to %s\n' "$branch" "$remote_ref"
    git branch --force "$branch" "$remote_ref"
  fi

  check_trailers "$remote_ref"
}

if [[ "$SYNC_ALL" == true ]]; then
  current="$(git branch --show-current)"
  git switch --detach

  while IFS= read -r branch; do
    sync_branch "$branch"
  done < <(git for-each-ref --format='%(refname:short)' refs/heads/)

  if [[ -n "$current" ]] && remote_ref_exists "$current"; then
    git switch "$current"
  elif [[ -n "$current" ]]; then
    printf 'The current branch %s has no matching remote branch; leaving HEAD detached\n' "$current"
  fi
else
  if [[ -n "$BRANCH" ]]; then
    if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
      git switch "$BRANCH"
    elif remote_ref_exists "$BRANCH"; then
      git switch --track -c "$BRANCH" "$REMOTE/$BRANCH"
    else
      die "branch '$BRANCH' does not exist locally or on '$REMOTE'"
    fi
  fi

  current="$(git branch --show-current)"
  [[ -n "$current" ]] || die "HEAD is detached; specify a branch name"

  if ! remote_ref_exists "$current"; then
    die "current branch '$current' has no matching remote branch"
  fi

  printf 'Resetting %s to %s/%s\n' "$current" "$REMOTE" "$current"
  git reset --hard "$REMOTE/$current"
  check_trailers "$REMOTE/$current"
fi

printf 'Sync complete; no Co-authored-by trailers found in synced branches.\n'
