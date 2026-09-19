#!/usr/bin/env bash

set -euo pipefail

if [[ -z "${GITHUB_PERSONAL_ACCESS_TOKEN:-}" ]]; then
  echo "ERROR: GITHUB_PERSONAL_ACCESS_TOKEN is not configured." >&2
  exit 1
fi

remote_url="$(git remote get-url origin)"
if [[ "$remote_url" != https://github.com/* ]]; then
  echo "ERROR: origin must be an HTTPS GitHub remote." >&2
  exit 1
fi

branch="$(git symbolic-ref --quiet --short HEAD || true)"
if [[ -z "$branch" ]]; then
  echo "ERROR: GitHub sync requires a named branch." >&2
  exit 1
fi

auth_header="AUTHORIZATION: basic $(printf 'x-access-token:%s' "$GITHUB_PERSONAL_ACCESS_TOKEN" | base64 | tr -d '\n')"
git_with_auth() {
  git -c "http.extraheader=$auth_header" "$@"
}

if [[ -z "$(git config user.name || true)" ]]; then
  git config user.name "${GITHUB_SYNC_AUTHOR_NAME:-Replit GitHub Sync}"
fi
if [[ -z "$(git config user.email || true)" ]]; then
  git config user.email "${GITHUB_SYNC_AUTHOR_EMAIL:-replit-github-sync@users.noreply.github.com}"
fi

echo "== GitHub sync =="
echo "Remote: $remote_url"
echo "Branch: $branch"

echo "Fetching origin/$branch..."
git_with_auth fetch origin "$branch"

if git show-ref --verify --quiet "refs/remotes/origin/$branch"; then
  if ! git merge-base --is-ancestor "origin/$branch" HEAD; then
    echo "ERROR: Local branch is behind or has diverged from origin/$branch." >&2
    echo "Resolve the branch history before pushing." >&2
    exit 1
  fi
fi

git add -A
if git diff --cached --quiet; then
  echo "No local changes to commit."
else
  commit_message="${GITHUB_SYNC_COMMIT_MESSAGE:-Sync changes from Replit}"
  git commit -m "$commit_message"
fi

echo "Pushing $branch..."
git_with_auth push origin "$branch"
echo "GitHub sync completed."