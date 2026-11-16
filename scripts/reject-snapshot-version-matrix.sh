#!/usr/bin/env bash
# Negative matrix for reject_snapshot_version. Does not publish.
# Versions below are classified the same way as Maven ArtifactUtils.isSnapshot.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=reject-snapshot-version.sh
source "${root}/scripts/reject-snapshot-version.sh"

failed=0

# Maven rejects these. Case variants and timestamp snapshots are the #140 gap.
reject=(
  "1.2.10-SNAPSHOT"
  "1.2.10-snapshot"
  "1.2.10-Snapshot"
  "1.2.10-SnApShOt"
  "SNAPSHOT"
  "snapshot"
  "1.0SNAPSHOT"
  "xSNAPSHOT"
  "1.0-20261116.120000-1"
  "1.0-alpha-20261116.120000-12"
  "1.2.11-20261116.120000-1"
  "1.0-20261116.120000-0"
  "1.0-20261116.120000-01"
  "-20261116.120000-1"
)

# Maven ArtifactUtils.isSnapshot is false. These must still pass the gate.
allow=(
  "1.2.11"
  "1.2.10"
  "1.0"
  "1.0-rc1"
  "1.0-alpha-1"
  "1.0-20261116.120000"
  "1.0-2026111.120000-1"
  "1.0-20261116.12000-1"
  "20261116.120000-1"
  "1.2.10-SNAPSHOT-1"
  "1.0-20261116.120000-1.0"
  "LATEST"
  "RELEASE"
)

check_reject() {
  local version="$1"
  local status=0
  local stderr
  stderr="$(reject_snapshot_version "${version}" matrix 2>&1)" || status=$?
  if [[ "${status}" -eq 0 ]]; then
    echo "FAIL reject: ${version} was allowed" >&2
    failed=1
    return
  fi
  if [[ "${stderr}" != *"不接收 SNAPSHOT"* || "${stderr}" != *"${version}"* ]]; then
    echo "FAIL reject: ${version} missing SNAPSHOT diagnostic" >&2
    printf '%s\n' "${stderr}" >&2
    failed=1
    return
  fi
  echo "ok reject ${version}"
}

check_allow() {
  local version="$1"
  local status=0
  local stderr
  stderr="$(reject_snapshot_version "${version}" matrix 2>&1)" || status=$?
  if [[ "${status}" -ne 0 ]]; then
    echo "FAIL allow: ${version} was rejected" >&2
    printf '%s\n' "${stderr}" >&2
    failed=1
    return
  fi
  echo "ok allow ${version}"
}

for version in "${reject[@]}"; do
  check_reject "${version}"
done
for version in "${allow[@]}"; do
  check_allow "${version}"
done

# Bare call, same shape as publish.yml under set -euo pipefail.
reject_snapshot_version "1.2.11" matrix

exit "${failed}"
