#!/usr/bin/env bash
# Release-gate dry runs. No publish, no Central, no real toolchains.xml.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
bash "${root}/scripts/reject-snapshot-version-matrix.sh"
bash "${root}/scripts/require-jdk-toolchain-matrix.sh"
