#!/usr/bin/env bash
# Publish just-test-boot2 / just-test-boot3 as separate Central deployments.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "${root}"

mvn_cmd=(mvn --batch-mode --no-transfer-progress)

dump_bundle() {
  local module="$1"
  echo "::group::Central staging dump for ${module}"
  find "${module}/target/central-staging" -type f 2>/dev/null | sort || true
  find target/central-staging -type f 2>/dev/null | sort || true
  for z in \
      "${module}/target/central-publishing/central-bundle.zip" \
      "${module}/target/central-staging/central-bundle.zip" \
      "target/central-publishing/central-bundle.zip" \
      "target/central-staging/central-bundle.zip"
  do
    if [[ -f "$z" ]]; then
      echo "ZIP $z"
      unzip -l "$z" || true
    fi
  done
  echo "::endgroup::"
}

for module in just-test-boot2 just-test-boot3; do
  echo "::notice::Central publish ${module}"
  rm -rf \
    target/central-staging target/central-publishing \
    just-test-boot2/target/central-staging just-test-boot2/target/central-publishing \
    just-test-boot3/target/central-staging just-test-boot3/target/central-publishing \
    just-test-core/target/central-staging just-test-core/target/central-publishing

  set +e
  "${mvn_cmd[@]}" \
    -Pdual-jdk-release,central-publish,central-upload \
    -pl "${module}" \
    -DskipTests \
    -Djust-test.central.split.executor=true \
    -Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE \
    deploy
  rc=$?
  set -e
  dump_bundle "${module}"
  if [[ "$rc" -ne 0 ]]; then
    exit "$rc"
  fi
done
