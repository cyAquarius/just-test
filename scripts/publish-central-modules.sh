#!/usr/bin/env bash
# Publish just-test-boot2 and just-test-boot3 as separate Central deployments.
# A full-reactor central-publishing upload stages both lines into one bundle and
# Sonatype rejects it with: Bundle has content that does NOT have a .pom file.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "${root}"

mvn_cmd=(mvn --batch-mode --no-transfer-progress)
if [[ -n "${MAVEN_ARGS:-}" ]]; then
  # allow CI to inject extra args if needed
  # shellcheck disable=SC2206
  mvn_cmd+=( ${MAVEN_ARGS} )
fi

for module in just-test-boot2 just-test-boot3; do
  echo "::notice::Central publish ${module} (isolated deployment)"
  rm -rf \
    target/central-staging target/central-publishing \
    just-test-boot2/target/central-staging just-test-boot2/target/central-publishing \
    just-test-boot3/target/central-staging just-test-boot3/target/central-publishing \
    just-test-core/target/central-staging just-test-core/target/central-publishing

  "${mvn_cmd[@]}" \
    -Pdual-jdk-release,central-publish,central-upload \
    -pl "${module}" -am \
    -DskipTests \
    -Djust-test.central.split.executor=true \
    -Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE \
    deploy
done
