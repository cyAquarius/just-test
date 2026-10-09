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

auth_header="Authorization: Bearer $(printf '%s:%s' "${CENTRAL_USERNAME}" "${CENTRAL_PASSWORD}" | base64 | tr -d '\n')"
api="https://central.sonatype.com/api/v1/publisher"
mvn_cmd=(mvn --batch-mode --no-transfer-progress)

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

  response="$(curl -sS -X POST \
    -H "${auth_header}" \
    -F "bundle=@${zip};type=application/octet-stream" \
    "${api}/upload?name=${name}&publishingType=AUTOMATIC")"
  echo "Central upload response: ${response}"

  deployment_id="$(printf '%s' "${response}" | tr -d '\r' | tr -d '"' | tr -d '\n')"
  if [[ ! "${deployment_id}" =~ ^[0-9a-fA-F-]{36}$ ]]; then
    deployment_id="$(printf '%s' "${response}" | sed -n 's/.*"deploymentId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  fi
  if [[ -z "${deployment_id}" || ! "${deployment_id}" =~ ^[0-9a-fA-F-]{36}$ ]]; then
    echo "Failed to parse deployment id from: ${response}" >&2
    exit 1
  fi
  echo "Deployment id: ${deployment_id}"

  for i in $(seq 1 90); do
    state="$(curl -sS -X POST \
      -H "${auth_header}" \
      "${api}/status?id=${deployment_id}")"
    echo "status[${i}]: ${state}"
    if printf '%s' "${state}" | rg -q '"deploymentState"[[:space:]]*:[[:space:]]*"PUBLISHED"'; then
      echo "Published ${name}"
      return 0
    fi
    if printf '%s' "${state}" | rg -q '"deploymentState"[[:space:]]*:[[:space:]]*"FAILED"'; then
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
    find "${module}/target" -maxdepth 3 -type d -print >&2 || true
    exit 1
  fi

  scrub_staging "${staging}"

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
  if unzip -l "${zip_path}" | rg -q 'maven-metadata|_remote\.repositories'; then
    echo "Clean zip still contains local-repo metadata" >&2
    exit 1
  fi

  version="$(mvn --quiet -pl "${module}" help:evaluate -Dexpression=project.version -DforceStdout)"
  upload_and_wait "${zip_path}" "${module}-${version}"
done
