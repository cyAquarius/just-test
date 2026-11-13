#!/usr/bin/env bash
# Shared SNAPSHOT gate for publish-central-modules.sh and .github/workflows/publish.yml.
# Negative check (does not publish, does not contact Central, needs no credentials):
#   bash -c 'source scripts/reject-snapshot-version.sh; reject_snapshot_version 1.2.10-SNAPSHOT just-test-boot2'

reject_snapshot_version() {
  local version="$1"
  local origin="${2:-release}"
  if [[ "${version}" == *-SNAPSHOT ]]; then
    echo "Maven Central 不接收 SNAPSHOT（${origin}: ${version}）。请打正式版 tag，例如 v1.2.10。" >&2
    return 1
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  echo "Source this file; do not execute it." >&2
  exit 2
fi
