#!/usr/bin/env bash
# Publish just-test-boot2 / just-test-boot3 to Maven Central.
#
# Do not trust central-publishing's zip: it packs maven-metadata-local.xml and
# _remote.repositories, and Sonatype rejects with "missing .pom".
# Build+sign with Maven, assemble a clean bundle by hand, upload via API.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "${root}"

: "${CENTRAL_USERNAME:?CENTRAL_USERNAME is required}"
: "${CENTRAL_PASSWORD:?CENTRAL_PASSWORD is required}"

auth_header="Authorization: Bearer $(printf '%s:%s' "${CENTRAL_USERNAME}" "${CENTRAL_PASSWORD}" | base64 | tr -d '\n')"
api="https://central.sonatype.com/api/v1/publisher"
mvn_cmd=(mvn --batch-mode --no-transfer-progress)
group_path="io/github/cyaquarius"

write_checksums() {
  local file="$1"
  md5sum "$file" | awk '{print $1}' > "${file}.md5"
  sha1sum "$file" | awk '{print $1}' > "${file}.sha1"
  sha256sum "$file" | awk '{print $1}' > "${file}.sha256"
  sha512sum "$file" | awk '{print $1}' > "${file}.sha512"
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

  "${mvn_cmd[@]}" \
    -Pdual-jdk-release,central-publish \
    -pl "${module}" \
    -DskipTests \
    -Djust-test.central.split.executor=true \
    -Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE \
    clean verify

  version="$(mvn --quiet -pl "${module}" help:evaluate -Dexpression=project.version -DforceStdout)"
  base="${module}-${version}"
  target="${module}/target"
  pom_src="${module}/.flattened-pom.xml"

  for required in \
      "${pom_src}" \
      "${target}/${base}.jar" \
      "${target}/${base}-sources.jar" \
      "${target}/${base}-javadoc.jar" \
      "${target}/${base}.jar.asc" \
      "${target}/${base}.pom.asc" \
      "${target}/${base}-sources.jar.asc" \
      "${target}/${base}-javadoc.jar.asc"
  do
    if [[ ! -f "${required}" ]]; then
      echo "Missing required artifact: ${required}" >&2
      ls -la "${target}" >&2 || true
      exit 1
    fi
  done

  stage_root="${target}/central-staging-clean"
  stage_dir="${stage_root}/${group_path}/${module}/${version}"
  rm -rf "${stage_root}"
  mkdir -p "${stage_dir}"

  cp "${pom_src}" "${stage_dir}/${base}.pom"
  cp "${target}/${base}.jar" "${stage_dir}/"
  cp "${target}/${base}-sources.jar" "${stage_dir}/"
  cp "${target}/${base}-javadoc.jar" "${stage_dir}/"
  cp "${target}/${base}.jar.asc" "${stage_dir}/"
  cp "${target}/${base}.pom.asc" "${stage_dir}/"
  cp "${target}/${base}-sources.jar.asc" "${stage_dir}/"
  cp "${target}/${base}-javadoc.jar.asc" "${stage_dir}/"

  for f in \
      "${stage_dir}/${base}.pom" \
      "${stage_dir}/${base}.jar" \
      "${stage_dir}/${base}-sources.jar" \
      "${stage_dir}/${base}-javadoc.jar"
  do
    write_checksums "$f"
  done

  out_dir="${target}/central-publishing"
  mkdir -p "${out_dir}"
  zip_path="${out_dir}/central-bundle.zip"
  rm -f "${zip_path}"
  ( cd "${stage_root}" && zip -r -X "${root}/${zip_path}" . )

  echo "Clean bundle:"
  unzip -l "${zip_path}"
  if unzip -l "${zip_path}" | rg -q 'maven-metadata|_remote\.repositories'; then
    echo "Bundle contains local-repo metadata" >&2
    exit 1
  fi
  if ! unzip -l "${zip_path}" | rg -q "${base}\\.pom$"; then
    echo "Bundle missing pom" >&2
    exit 1
  fi

  upload_and_wait "${zip_path}" "${module}-${version}"
done
