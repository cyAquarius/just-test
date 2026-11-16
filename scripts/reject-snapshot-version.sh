#!/usr/bin/env bash
# Shared SNAPSHOT gate for publish-central-modules.sh and .github/workflows/publish.yml.
# Matches Maven ArtifactUtils.isSnapshot (maven-artifact 3.8/3.9):
#   - the last 8 characters are "SNAPSHOT" in any case (regionMatches)
#   - or the version matches Artifact.VERSION_FILE_PATTERN
#     ^(.*)-([0-9]{8}\.[0-9]{6})-([0-9]+)$
#     for example 1.0-20261116.120000-1
# Negative matrix (does not publish, does not contact Central, needs no credentials):
#   bash scripts/reject-snapshot-version-matrix.sh
# One-off:
#   bash -c 'source scripts/reject-snapshot-version.sh; reject_snapshot_version 1.2.10-SNAPSHOT just-test-boot2'

# True when Maven ArtifactUtils.isSnapshot(version) is true.
is_maven_snapshot() {
  local version="$1"
  local upper
  upper="$(printf '%s' "${version}" | LC_ALL=C tr '[:lower:]' '[:upper:]')"
  if [[ "${#upper}" -ge 8 && "${upper: -8}" == "SNAPSHOT" ]]; then
    return 0
  fi
  [[ "${version}" =~ ^.*-[0-9]{8}\.[0-9]{6}-[0-9]+$ ]]
}

reject_snapshot_version() {
  local version="$1"
  local origin="${2:-release}"
  if is_maven_snapshot "${version}"; then
    echo "Maven Central 不接收 SNAPSHOT（${origin}: ${version}）。含大小写变体和时间戳快照。请打正式版 tag，例如 v1.2.10。" >&2
    return 1
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "Source this file; do not execute it." >&2
  exit 2
fi
