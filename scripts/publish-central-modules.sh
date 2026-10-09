#!/usr/bin/env bash
# Publish just-test-boot2 / just-test-boot3 to Maven Central.
#
# The central-publishing plugin packs local-repo junk into the bundle:
#   io/.../<artifact>/maven-metadata-local.xml
#   io/.../<artifact>/<version>/_remote.repositories*
# Sonatype then rejects with: Bundle has content that does NOT have a .pom file.
#
# Flow: signed deploy with skipPublishing → scrub staging → zip cleanly →
# upload via Central Publisher API → wait until PUBLISHED.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "${root}"

: "${CENTRAL_USERNAME:?CENTRAL_USERNAME is required}"
: "${CENTRAL_PASSWORD:?CENTRAL_PASSWORD is required}"

mvn_cmd=(mvn --batch-mode --no-transfer-progress)
api="https://central.sonatype.com/api/v1/publisher"

scrub_staging() {
  local dir="$1"
  [[ -d "$dir" ]] || return 0
  find "$dir" -name 'maven-metadata*.xml' -delete
  find "$dir" -name '_remote.repositories*' -delete
  find "$dir" -name 'central-bundle.zip' -delete
}

find_staging_dir() {
  local module="$1"
  if [[ -d "${module}/target/central-staging/io" ]]; then
    echo "${module}/target/central-staging"
  elif [[ -d "target/central-staging/io" ]]; then
    echo "target/central-staging"
  else
    echo ""
  fi
}

upload_and_wait() {
  local zip="$1"
  local name="$2"
  local response deployment_id state i

  response="$(curl -sS -u "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" \
    -F "bundle=@${zip}" \
    "${api}/upload?name=${name}&publishingType=AUTOMATIC")"
  echo "Central upload response: ${response}"

  deployment_id="$(printf '%s' "${response}" | tr -d '\r' | tr -d '"')"
  if [[ ! "${deployment_id}" =~ ^[0-9a-fA-F-]{36}$ ]]; then
    # some API versions return JSON {"deploymentId":"..."}
    deployment_id="$(printf '%s' "${response}" | sed -n 's/.*"deploymentId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  fi
  if [[ -z "${deployment_id}" ]]; then
    echo "Failed to parse deployment id from: ${response}" >&2
    exit 1
  fi
  echo "Deployment id: ${deployment_id}"

  for i in $(seq 1 90); do
    state="$(curl -sS -u "${CENTRAL_USERNAME}:${CENTRAL_PASSWORD}" \
      "${api}/status?id=${deployment_id}")"
    echo "status[${i}]: ${state}"
    if printf '%s' "${state}" | rg -q '"deploymentState"[[:space:]]*:[[:space:]]*"PUBLISHED"'; then
      echo "Published ${name}"
      return 0
    fi
    if printf '%s' "${state}" | rg -q '"deploymentState"[[:space:]]*:[[:space:]]*"(FAILED|REJECTED)"'; then
      echo "Central deployment failed: ${state}" >&2
      exit 1
    fi
    sleep 10
  done
  echo "Timed out waiting for PUBLISHED: ${state}" >&2
  exit 1
}

for module in just-test-boot2 just-test-boot3; do
  echo "::notice::Central publish ${module}"
  rm -rf \
    target/central-staging target/central-publishing \
    just-test-boot2/target/central-staging just-test-boot2/target/central-publishing \
    just-test-boot3/target/central-staging just-test-boot3/target/central-publishing \
    just-test-core/target/central-staging just-test-core/target/central-publishing

  # Sign/install/stage only (central-publish profile forces skipPublishing=true).
  "${mvn_cmd[@]}" \
    -Pdual-jdk-release,central-publish \
    -pl "${module}" \
    -DskipTests \
    -Djust-test.central.split.executor=true \
    -Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE \
    deploy

  staging="$(find_staging_dir "${module}")"
  if [[ -z "${staging}" ]]; then
    echo "No central-staging directory found for ${module}" >&2
    exit 1
  fi

  scrub_staging "${staging}"

  # Guard: refuse to ship local-repo metadata even if scrub missed something.
  if find "${staging}" \( -name 'maven-metadata*.xml' -o -name '_remote.repositories*' \) | rg -q .; then
    echo "Staging still contains local-repo metadata:" >&2
    find "${staging}" \( -name 'maven-metadata*.xml' -o -name '_remote.repositories*' \) >&2
    exit 1
  fi
  if ! find "${staging}" -name '*.pom' | rg -q .; then
    echo "Staging has no .pom for ${module}" >&2
    find "${staging}" -type f | sort >&2
    exit 1
  fi

  out_dir="${module}/target/central-publishing"
  mkdir -p "${out_dir}"
  zip_path="${out_dir}/central-bundle.zip"
  rm -f "${zip_path}"
  ( cd "${staging}" && zip -r -X "${root}/${zip_path}" . )
  echo "Clean bundle:"
  unzip -l "${zip_path}"

  version="$(mvn --quiet -pl "${module}" help:evaluate -Dexpression=project.version -DforceStdout)"
  upload_and_wait "${zip_path}" "${module}-${version}"
done
