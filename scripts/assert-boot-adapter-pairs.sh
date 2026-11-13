#!/usr/bin/env bash
# Fail the build when the Boot 2 and Boot 3 adapter copies drift.
# Issue #44 asked for a build-time diff. The copies stay separate.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
pairs=(
  src/main/java/com/just/test/annotation/JustTest.java
  src/main/java/com/just/test/annotation/JustTestProject.java
  src/main/java/com/just/test/lifecycle/JustTestClassValidationExtension.java
)

failed=0
for rel in "${pairs[@]}"; do
  boot2="${root}/just-test-boot2/${rel}"
  boot3="${root}/just-test-boot3/${rel}"
  if [[ ! -f "${boot2}" || ! -f "${boot3}" ]]; then
    echo "Missing boot adapter pair: ${rel}" >&2
    failed=1
    continue
  fi
  if ! cmp -s "${boot2}" "${boot3}"; then
    echo "Boot adapter drift: ${rel}" >&2
    diff -u "${boot2}" "${boot3}" >&2 || true
    failed=1
  fi
done

exit "${failed}"
