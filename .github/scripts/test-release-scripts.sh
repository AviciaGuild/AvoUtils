#!/usr/bin/env bash
set -euo pipefail

# Unset GITHUB_OUTPUT in test runner so tests do not write to the GitHub step output file
unset GITHUB_OUTPUT

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DETERMINE_SCRIPT="${SCRIPT_DIR}/determine-version.sh"
CHANGELOG_SCRIPT="${SCRIPT_DIR}/generate-changelog.sh"

PASSED=0
FAILED=0

assert_equals() {
  local expected="$1"
  local actual="$2"
  local msg="$3"
  if [[ "$expected" == "$actual" ]]; then
    echo "  [PASS] $msg"
    PASSED=$((PASSED + 1))
  else
    echo "  [FAIL] $msg"
    echo "    Expected: '$expected'"
    echo "    Actual:   '$actual'"
    FAILED=$((FAILED + 1))
  fi
}

assert_contains() {
  local haystack="$1"
  local needle="$2"
  local msg="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo "  [PASS] $msg"
    PASSED=$((PASSED + 1))
  else
    echo "  [FAIL] $msg"
    echo "    Expected to contain: '$needle'"
    echo "    Haystack: '$haystack'"
    FAILED=$((FAILED + 1))
  fi
}

assert_not_contains() {
  local haystack="$1"
  local needle="$2"
  local msg="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "  [PASS] $msg"
    PASSED=$((PASSED + 1))
  else
    echo "  [FAIL] $msg"
    echo "    Expected NOT to contain: '$needle'"
    echo "    Haystack: '$haystack'"
    FAILED=$((FAILED + 1))
  fi
}

# Helper to create a sandbox git repo
create_sandbox() {
  local sandbox_dir
  sandbox_dir="$(mktemp -d)"
  cd "$sandbox_dir"
  git init -q
  git config user.name "Test User"
  git config user.email "test@example.com"
  echo "minecraft_version=1.21.11" > gradle.properties
  echo "initial" > file.txt
  git add file.txt gradle.properties
  git commit -m "init" -q
  echo "$sandbox_dir"
}

# -------------------------------------------------------------
echo "Running Version Determination Tests..."
# -------------------------------------------------------------

SANDBOX=$(create_sandbox)
cd "$SANDBOX"
git tag v1.1.14

# Test 1: Initial Beta for new base version
output=$(bash "$DETERMINE_SCRIPT" "workflow_dispatch" "beta" "patch" "refs/heads/main" "main")
version=$(echo "$output" | sed -n 's/^version=//p')
tag_name=$(echo "$output" | sed -n 's/^tag_name=//p')
version_type=$(echo "$output" | sed -n 's/^version_type=//p')
assert_equals "1.1.14-beta.1" "$version" "Initial beta starts at 1.1.14-beta.1"
assert_equals "v1.1.14-beta.1" "$tag_name" "Tag name matches v1.1.14-beta.1"
assert_equals "beta" "$version_type" "Version type is beta"

# Test 2: Beta increments monotonically
git tag v1.1.14-beta.1
git tag v1.1.14-beta.2
output=$(bash "$DETERMINE_SCRIPT" "workflow_dispatch" "beta" "patch" "refs/heads/main" "main")
version=$(echo "$output" | sed -n 's/^version=//p')
assert_equals "1.1.14-beta.3" "$version" "Beta increments to 1.1.14-beta.3"

# Test 3: Main release with patch bump
output=$(bash "$DETERMINE_SCRIPT" "workflow_dispatch" "release" "patch" "refs/heads/main" "main")
version=$(echo "$output" | sed -n 's/^version=//p')
version_type=$(echo "$output" | sed -n 's/^version_type=//p')
assert_equals "1.1.15" "$version" "Release patch bumps 1.1.14 -> 1.1.15"
assert_equals "release" "$version_type" "Version type is release"

# Test 4: Main release with minor bump
output=$(bash "$DETERMINE_SCRIPT" "workflow_dispatch" "release" "minor" "refs/heads/main" "main")
version=$(echo "$output" | sed -n 's/^version=//p')
assert_equals "1.2.0" "$version" "Release minor bumps 1.1.14 -> 1.2.0"

# Test 5: Main release with major bump
output=$(bash "$DETERMINE_SCRIPT" "workflow_dispatch" "release" "major" "refs/heads/main" "main")
version=$(echo "$output" | sed -n 's/^version=//p')
assert_equals "2.0.0" "$version" "Release major bumps 1.1.14 -> 2.0.0"

# Test 6: Beta build number resets back to 1 when base version changes after release
git tag v1.1.15
output=$(bash "$DETERMINE_SCRIPT" "workflow_dispatch" "beta" "patch" "refs/heads/main" "main")
version=$(echo "$output" | sed -n 's/^version=//p')
assert_equals "1.1.15-beta.1" "$version" "Beta build number resets to 1 after release (1.1.15-beta.1)"

# Test 7: Push tag triggers preserve tag name directly
output=$(bash "$DETERMINE_SCRIPT" "push" "" "" "refs/tags/v2.1.0" "v2.1.0")
version=$(echo "$output" | sed -n 's/^version=//p')
version_type=$(echo "$output" | sed -n 's/^version_type=//p')
assert_equals "2.1.0" "$version" "Tag push parses version 2.1.0"
assert_equals "release" "$version_type" "Tag push version type is release"

# Test 8: Push tag with beta suffix sets version_type to beta
output=$(bash "$DETERMINE_SCRIPT" "push" "" "" "refs/tags/v2.1.0-beta.1" "v2.1.0-beta.1")
version_type=$(echo "$output" | sed -n 's/^version_type=//p')
assert_equals "beta" "$version_type" "Pre-release tag push sets beta version_type"

rm -rf "$SANDBOX"

# -------------------------------------------------------------
echo "Running Changelog Generation Tests..."
# -------------------------------------------------------------

SANDBOX=$(create_sandbox)
cd "$SANDBOX"
git tag v1.1.14

echo "feat 1" >> file.txt && git commit -am "feat: add party search" -q
echo "fix 1" >> file.txt && git commit -am "fix: emoji preview" -q

# Test 9: Standard release changelog
output=$(bash "$CHANGELOG_SCRIPT" "v1.1.15" "release" "AviciaGuild/AvoUtils" "v1.1.14")
assert_contains "$output" "- feat: add party search" "Release changelog contains commit 1"
assert_contains "$output" "- fix: emoji preview" "Release changelog contains commit 2"
assert_contains "$output" "**Full Changelog**: https://github.com/AviciaGuild/AvoUtils/compare/v1.1.14...v1.1.15" "Release changelog contains compare link"

# Test 10: Beta changelog has compare link and omits commit bullets
output=$(bash "$CHANGELOG_SCRIPT" "v1.1.14-beta.1" "beta" "AviciaGuild/AvoUtils" "v1.1.14")
assert_contains "$output" "**Full Changelog**: https://github.com/AviciaGuild/AvoUtils/compare/v1.1.14...v1.1.14-beta.1" "Beta changelog contains compare link"
assert_not_contains "$output" "- feat: add party search" "Beta changelog does not contain commit list"

# Test 11: Initial release with no previous tags
SANDBOX_INITIAL=$(create_sandbox)
cd "$SANDBOX_INITIAL"
output=$(bash "$CHANGELOG_SCRIPT" "v1.0.0" "release" "AviciaGuild/AvoUtils" "")
assert_contains "$output" "- init" "Initial release changelog lists initial commit"

rm -rf "$SANDBOX" "$SANDBOX_INITIAL"

# -------------------------------------------------------------
echo "----------------------------------------"
echo "Test Results: ${PASSED} passed, ${FAILED} failed"
echo "----------------------------------------"

if [[ "$FAILED" -ne 0 ]]; then
  exit 1
fi
