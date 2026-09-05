#!/usr/bin/env bash
set -euo pipefail

EVENT_NAME="${1:-${GITHUB_EVENT_NAME:-workflow_dispatch}}"
RELEASE_TYPE="${2:-${INPUT_RELEASE_TYPE:-beta}}"
BUMP_TYPE="${3:-${INPUT_BUMP_TYPE:-patch}}"
REF="${4:-${GITHUB_REF:-refs/heads/main}}"
REF_NAME="${5:-${GITHUB_REF_NAME:-main}}"

git fetch --tags --force 2>/dev/null || true

LATEST_TAG=$(git tag --merged HEAD -l 'v[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname 2>/dev/null | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | head -n 1 || true)
if [[ -z "$LATEST_TAG" ]]; then
  LATEST_TAG=$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' --exclude '*-beta*' --exclude '*-dev*' 2>/dev/null || echo "v1.0.0")
fi
BASE_VERSION="${LATEST_TAG#v}"

SHOULD_PUBLISH="true"
SHOULD_PUBLISH_MODRINTH="true"

if [[ "$REF" == refs/tags/v* ]]; then
  TAG_NAME="$REF_NAME"
  VERSION="${TAG_NAME#v}"
  if [[ "$VERSION" == *-* ]]; then
    VERSION_TYPE="beta"
  else
    VERSION_TYPE="release"
  fi
  PREV_TAG="$(git describe --tags --abbrev=0 --match 'v[0-9]*.[0-9]*.[0-9]*' --exclude '*-beta*' HEAD^ 2>/dev/null || echo '')"
  echo "Determined version from tag push: $VERSION ($VERSION_TYPE)"
else
  PREV_TAG="$LATEST_TAG"
  if [[ "$RELEASE_TYPE" == "beta" ]]; then
    VERSION_TYPE="beta"
    LATEST_BETA=$(git tag -l "v${BASE_VERSION}-beta.*" 2>/dev/null | sed "s/^v${BASE_VERSION}-beta\.//" | grep -E '^[0-9]+$' | sort -n | tail -n 1 || true)
    NEXT_BETA=$(( ${LATEST_BETA:-0} + 1 ))
    VERSION="${BASE_VERSION}-beta.${NEXT_BETA}"
    TAG_NAME="v${VERSION}"
    echo "Determined beta version: $VERSION (next beta index: $NEXT_BETA)"
  else
    VERSION_TYPE="release"
    IFS='.' read -r major minor patch <<< "$BASE_VERSION"
    major="${major:-0}"
    minor="${minor:-0}"
    patch="${patch:-0}"

    case "$BUMP_TYPE" in
      major)
        major=$((major + 1))
        minor=0
        patch=0
        ;;
      minor)
        minor=$((minor + 1))
        patch=0
        ;;
      patch|*)
        patch=$((patch + 1))
        ;;
    esac

    VERSION="${major}.${minor}.${patch}"
    TAG_NAME="v${VERSION}"
    echo "Determined release version: $VERSION (bump: $BUMP_TYPE from $BASE_VERSION)"
  fi
fi

MINECRAFT_VERSION="$(sed -n 's/^minecraft_version=//p' gradle.properties 2>/dev/null || echo '')"

echo "version=${VERSION}"
echo "tag_name=${TAG_NAME}"
echo "version_type=${VERSION_TYPE}"
echo "should_publish=${SHOULD_PUBLISH}"
echo "should_publish_modrinth=${SHOULD_PUBLISH_MODRINTH}"
echo "minecraft_version=${MINECRAFT_VERSION}"
echo "prev_tag=${PREV_TAG}"
echo "base_version=${BASE_VERSION}"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    echo "version=${VERSION}"
    echo "tag_name=${TAG_NAME}"
    echo "version_type=${VERSION_TYPE}"
    echo "should_publish=${SHOULD_PUBLISH}"
    echo "should_publish_modrinth=${SHOULD_PUBLISH_MODRINTH}"
    echo "minecraft_version=${MINECRAFT_VERSION}"
    echo "prev_tag=${PREV_TAG}"
    echo "base_version=${BASE_VERSION}"
  } >> "$GITHUB_OUTPUT"
fi

