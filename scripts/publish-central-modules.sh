#!/usr/bin/env bash
# Publish just-test-boot2 and just-test-boot3 as separate Central deployments.
#
# Do NOT use -am: parent/core still write checksum dirs into central-staging even
# when skipPublishing=true, and Sonatype then rejects the bundle as missing .pom.
# The outer reactor has already installed parent/core into the local Maven repo.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "${root}"

mvn_cmd=(mvn --batch-mode --no-transfer-progress)

for module in just-test-boot2 just-test-boot3; do
  echo "::notice::Central publish ${module} (isolated deployment, no -am)"
  rm -rf \
    target/central-staging target/central-publishing \
    just-test-boot2/target/central-staging just-test-boot2/target/central-publishing \
    just-test-boot3/target/central-staging just-test-boot3/target/central-publishing \
    just-test-core/target/central-staging just-test-core/target/central-publishing

  "${mvn_cmd[@]}" \
    -Pdual-jdk-release,central-publish,central-upload \
    -pl "${module}" \
    -DskipTests \
    -Djust-test.central.split.executor=true \
    -Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE \
    deploy
done
