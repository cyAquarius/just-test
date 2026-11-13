#!/usr/bin/env bash
# dual-jdk-release must fail when toolchains.xml has no matching JDK.
# maven-compiler-plugin 3.15.0 otherwise compiles with the JDK running Maven.
set -euo pipefail

requested="${1:?usage: require-jdk-toolchain.sh <jdk-version>}"
file="${HOME}/.m2/toolchains.xml"

if [[ ! -f "${file}" ]]; then
  echo "dual-jdk-release: ${file} is missing, so JDK ${requested} is not selected." >&2
  echo "maven-compiler-plugin 3.15.0 would compile with the current JDK. Declare the toolchain in toolchains.xml." >&2
  exit 1
fi

major_of() {
  local value="$1"
  value="${value//$'\r'/}"
  value="${value//[[:space:]]/}"
  if [[ -z "${value}" ]]; then
    echo ""
    return 0
  fi
  if [[ "${value}" == 1.* ]]; then
    local rest="${value#1.}"
    echo "${rest%%.*}"
    return 0
  fi
  echo "${value%%.*}"
}

wanted="$(major_of "${requested}")"
if [[ -z "${wanted}" ]]; then
  echo "dual-jdk-release: empty JDK toolchain version" >&2
  exit 1
fi

found=""
while IFS= read -r raw; do
  major="$(major_of "${raw}")"
  if [[ -z "${major}" ]]; then
    continue
  fi
  found="${found}${major} "
  if [[ "${major}" == "${wanted}" ]]; then
    exit 0
  fi
done < <(sed -n 's:.*<version>[[:space:]]*\([^<]*\)[[:space:]]*</version>.*:\1:p' "${file}")

echo "dual-jdk-release: ${file} has no JDK ${requested} toolchain (found:${found:- none})." >&2
echo "maven-compiler-plugin 3.15.0 would compile with the current JDK." >&2
exit 1
