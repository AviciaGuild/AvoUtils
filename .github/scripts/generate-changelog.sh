#!/usr/bin/env bash
set -euo pipefail

NEW_TAG="${1:-${TAG_NAME:-}}"
VERSION_TYPE="${2:-${VERSION_TYPE:-release}}"
REPO="${3:-${GITHUB_REPOSITORY:-AviciaGuild/AvoUtils}}"
PREV_TAG="${4:-${PREV_TAG:-}}"

if [[ -z "$NEW_TAG" ]]; then
  echo "Error: NEW_TAG must be provided." >&2
  exit 1
fi

if [[ -z "$PREV_TAG" ]]; then
  if [[ "$VERSION_TYPE" == "release" ]]; then
    PREV_TAG="$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' --exclude '*-beta*' HEAD^ 2>/dev/null || echo '')"
  else
    PREV_TAG="$(git describe --tags --abbrev=0 HEAD^ 2>/dev/null || echo '')"
  fi
fi

compare_url=""
if [[ -n "$PREV_TAG" && -n "$REPO" ]]; then
  compare_url="https://github.com/${REPO}/compare/${PREV_TAG}...${NEW_TAG}"
fi

changelog=""
if [[ "$VERSION_TYPE" == "release" ]]; then
  if [[ -n "$PREV_TAG" ]]; then
    changelog="$(git log --pretty='- %s' "${PREV_TAG}..HEAD" 2>/dev/null || echo '')"
  else
    changelog="$(git log --pretty='- %s' HEAD 2>/dev/null || echo '')"
  fi
  if [[ -z "$changelog" ]]; then
    changelog="Initial release"
  fi
fi

if [[ -n "$compare_url" ]]; then
  if [[ -n "$changelog" ]]; then
    changelog="${changelog}"$'\n\n'
  fi
  changelog="${changelog}**Full Changelog**: ${compare_url}"
fi

echo "${changelog}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "changelog<<EOF" >> "$GITHUB_OUTPUT"
  echo "${changelog}" >> "$GITHUB_OUTPUT"
  echo "EOF" >> "$GITHUB_OUTPUT"
fi
