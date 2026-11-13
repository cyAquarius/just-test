#!/usr/bin/env bash
# Publish just-test-boot2 / just-test-boot3 to Maven Central.
#
# Do not trust central-publishing's zip: it packs maven-metadata-local.xml and
# _remote.repositories, and Sonatype rejects with "missing .pom".
# Build+sign with Maven, assemble a clean bundle by hand, upload via API.
#
# Skip a module whose pom and jar are already on repo1, so a retry can finish
# a version that only partly reached Central. Poll PUBLISHING for 60 minutes:
# the previous 15 minute budget (90 x 10s) timed out on v1.2.5 while boot2 was
# still PUBLISHING, and that artifact reached repo1 about 21 minutes after
# upload. The publish job timeout must cover two of these waits.
#
# SNAPSHOT coordinates are refused before credentials or Central are touched.
# Negative check (no upload):
#   bash -c 'source scripts/reject-snapshot-version.sh; reject_snapshot_version 1.2.10-SNAPSHOT just-test-boot2'
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cd "${root}"
# shellcheck source=reject-snapshot-version.sh
source "${root}/scripts/reject-snapshot-version.sh"

auth_header=""
api="https://central.sonatype.com/api/v1/publisher"
repo1="https://repo1.maven.org/maven2"
mvn_cmd=(mvn --batch-mode --no-transfer-progress)
group_path="io/github/cyaquarius"
# 360 * 10s = 60 minutes per deployment.
publish_wait_attempts=360
publish_wait_interval_seconds=10

ensure_central_credentials() {
  if [[ -n "${auth_header}" ]]; then
    return 0
  fi
  : "${CENTRAL_USERNAME:?CENTRAL_USERNAME is required}"
  : "${CENTRAL_PASSWORD:?CENTRAL_PASSWORD is required}"
  auth_header="Authorization: Bearer $(printf '%s:%s' "${CENTRAL_USERNAME}" "${CENTRAL_PASSWORD}" | base64 | tr -d '\n')"
}

write_checksums() {
  local file="$1"
  md5sum "$file" | awk '{print $1}' > "${file}.md5"
  sha1sum "$file" | awk '{print $1}' > "${file}.sha1"
  sha256sum "$file" | awk '{print $1}' > "${file}.sha256"
  sha512sum "$file" | awk '{print $1}' > "${file}.sha512"
}

http_code() {
  local url="$1"
  local code
  # return, do not exit: this runs inside a command substitution.
  if ! code="$(curl -sS -o /dev/null -w '%{http_code}' --head --location --retry 3 --retry-delay 2 --max-time 60 "${url}")"; then
    echo "Failed to query ${url}" >&2
    return 1
  fi
  printf '%s' "${code}"
}

# Both the pom and the jar must already be on Central. A 200/404 split is not
# treated as published; anything other than 200 or 404 aborts before upload.
central_release_present() {
  local module="$1"
  local version="$2"
  local base="${module}-${version}"
  local pom_url="${repo1}/${group_path}/${module}/${version}/${base}.pom"
  local jar_url="${repo1}/${group_path}/${module}/${version}/${base}.jar"
  local pom_code jar_code
  if ! pom_code="$(http_code "${pom_url}")"; then
    exit 1
  fi
  if ! jar_code="$(http_code "${jar_url}")"; then
    exit 1
  fi
  echo "repo1 ${module} ${version}: pom=${pom_code} jar=${jar_code}"
  if [[ "${pom_code}" != "200" && "${pom_code}" != "404" ]]; then
    echo "Unexpected HTTP ${pom_code} for ${pom_url}" >&2
    exit 1
  fi
  if [[ "${jar_code}" != "200" && "${jar_code}" != "404" ]]; then
    echo "Unexpected HTTP ${jar_code} for ${jar_url}" >&2
    exit 1
  fi
  [[ "${pom_code}" == "200" && "${jar_code}" == "200" ]]
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
  echo "Waiting up to $((publish_wait_attempts * publish_wait_interval_seconds))s for PUBLISHED"

  for i in $(seq 1 "${publish_wait_attempts}"); do
    # Retry transport blips. The body is still the Portal state string; repo1
    # remains the skip signal because it lags PUBLISHED.
    state="$(curl -sS \
      --retry 3 \
      --retry-delay 2 \
      --retry-connrefused \
      --connect-timeout 15 \
      --max-time 60 \
      -X POST \
      -H "${auth_header}" \
      "${api}/status?id=${deployment_id}")"
    echo "status[${i}]: ${state}"
    if printf '%s' "${state}" | grep -Eq '"deploymentState"[[:space:]]*:[[:space:]]*"PUBLISHED"'; then
      echo "Published ${name}"
      return 0
    fi
    if printf '%s' "${state}" | grep -Eq '"deploymentState"[[:space:]]*:[[:space:]]*"FAILED"'; then
      echo "Central deployment failed: ${state}" >&2
      exit 1
    fi
    sleep "${publish_wait_interval_seconds}"
  done
  echo "Timed out after $((publish_wait_attempts * publish_wait_interval_seconds))s waiting for PUBLISHED: ${state}" >&2
  exit 1
}

for module in just-test-boot2 just-test-boot3; do
  version="$(mvn --quiet -pl "${module}" help:evaluate -Dexpression=project.version -DforceStdout)"
  if [[ ! "${version}" =~ ^[0-9A-Za-z][0-9A-Za-z._+-]*$ ]]; then
    echo "Failed to resolve version for ${module}: ${version}" >&2
    exit 1
  fi
  reject_snapshot_version "${version}" "${module}"
  ensure_central_credentials
  if central_release_present "${module}" "${version}"; then
    echo "::notice::Skip ${module} ${version}: pom and jar already on Maven Central"
    continue
  fi

  echo "::notice::Central publish ${module}"

  "${mvn_cmd[@]}" \
    -Pdual-jdk-release,central-publish \
    -pl "${module}" \
    -DskipTests \
    -Djust-test.central.split.executor=true \
    -Dgpg.passphraseEnvName=MAVEN_GPG_PASSPHRASE \
    clean verify

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
  if unzip -l "${zip_path}" | grep -Eq 'maven-metadata|_remote\.repositories'; then
    echo "Bundle contains local-repo metadata" >&2
    exit 1
  fi
  if ! unzip -l "${zip_path}" | grep -Eq "${base}\\.pom$"; then
    echo "Bundle missing pom" >&2
    exit 1
  fi

  upload_and_wait "${zip_path}" "${module}-${version}"
done
