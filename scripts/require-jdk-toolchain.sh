#!/usr/bin/env bash
# dual-jdk-release must fail when toolchains.xml has no matching JDK.
# maven-compiler-plugin 3.15.0 otherwise compiles with the JDK running Maven.
#
# Only <type>jdk</type> entries count. Multiline tags and several toolchains
# written on one line are both read. Other toolchain types are ignored.
# Dry-run against a fixture (does not read the real toolchains.xml):
#   JUST_TEST_TOOLCHAINS_XML=/tmp/toolchains.xml bash scripts/require-jdk-toolchain.sh 17
# Matrix:
#   bash scripts/require-jdk-toolchain-matrix.sh
set -euo pipefail

requested="${1:?usage: require-jdk-toolchain.sh <jdk-version>}"
file="${JUST_TEST_TOOLCHAINS_XML:-${HOME}/.m2/toolchains.xml}"

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

# Print one provides <version> per line for toolchain entries whose type is jdk.
# mawk-compatible: comments are removed, then tags are split on '<', so a version
# element split across lines and several entries on one line both match.
jdk_toolchain_versions() {
  awk '
    function strip_comments(src,    out, start, rel, endpos) {
      out = ""
      while (1) {
        start = index(src, "<!--")
        if (start == 0) {
          return out src
        }
        rel = index(substr(src, start + 4), "-->")
        if (rel == 0) {
          return out substr(src, 1, start - 1)
        }
        endpos = start + 3 + rel + 3
        out = out substr(src, 1, start - 1)
        src = substr(src, endpos)
      }
    }
    function emit(    n, i, parts) {
      if (in_tc && type == "jdk" && versions != "") {
        n = split(versions, parts, "\n")
        for (i = 1; i <= n; i++) {
          if (parts[i] != "") print parts[i]
        }
      }
      in_tc = 0
      type = ""
      versions = ""
    }
    {
      buf = buf $0 "\n"
    }
    END {
      gsub(/\r/, "", buf)
      src = strip_comments(buf)
      nrec = split(src, recs, "<")
      in_tc = 0
      type = ""
      versions = ""
      for (i = 1; i <= nrec; i++) {
        rec = recs[i]
        gt = index(rec, ">")
        if (gt == 0) continue
        tag = substr(rec, 1, gt - 1)
        body = substr(rec, gt + 1)
        gsub(/[[:space:]]/, "", body)
        closing = 0
        if (substr(tag, 1, 1) == "/") {
          closing = 1
          tag = substr(tag, 2)
        }
        sub(/[[:space:]].*/, "", tag)
        if (tag ~ /^[?!]/) continue
        if (!closing && tag == "toolchain") {
          emit()
          in_tc = 1
        } else if (closing && tag == "toolchain") {
          emit()
        } else if (in_tc && !closing && tag == "type") {
          if (type == "") type = body
        } else if (in_tc && !closing && tag == "version") {
          if (body != "") versions = versions body "\n"
        }
      }
      if (in_tc) emit()
    }
  ' "${file}"
}

wanted="$(major_of "${requested}")"
if [[ -z "${wanted}" ]]; then
  echo "dual-jdk-release: empty JDK toolchain version" >&2
  exit 1
fi

versions_text="$(jdk_toolchain_versions)"
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
done <<< "${versions_text}"

echo "dual-jdk-release: ${file} has no JDK ${requested} toolchain (found:${found:- none})." >&2
echo "maven-compiler-plugin 3.15.0 would compile with the current JDK." >&2
exit 1
