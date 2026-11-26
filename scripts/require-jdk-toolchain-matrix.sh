#!/usr/bin/env bash
# Dry-run matrix for require-jdk-toolchain.sh. Uses fixture files only.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
script="${root}/scripts/require-jdk-toolchain.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "${tmp}"' EXIT

failed=0
jdk8_range='[1.8,1.9),[8,9)'
jdk17_range='[17,18)'

write_xml() {
  local name="$1"
  cat > "${tmp}/${name}"
}

expect_status() {
  local name="$1"
  local expect="$2"
  local version="$3"
  local file="$4"
  local status=0
  local stderr
  stderr="$(JUST_TEST_TOOLCHAINS_XML="${file}" bash "${script}" "${version}" 2>&1)" || status=$?
  if [[ "${status}" -ne "${expect}" ]]; then
    echo "FAIL ${name}: expected exit ${expect}, got ${status}" >&2
    printf '%s\n' "${stderr}" >&2
    failed=1
    return
  fi
  printf '%s' "${stderr}"
  echo "ok ${name}"
}

expect_found() {
  local name="$1"
  local version="$2"
  local file="$3"
  local found_text="$4"
  local status=0
  local stderr
  stderr="$(JUST_TEST_TOOLCHAINS_XML="${file}" bash "${script}" "${version}" 2>&1)" || status=$?
  if [[ "${status}" -eq 0 ]]; then
    echo "FAIL ${name}: expected a toolchain miss" >&2
    failed=1
    return
  fi
  if [[ "${stderr}" != *"(found:${found_text})."* ]]; then
    echo "FAIL ${name}: expected (found:${found_text})." >&2
    printf '%s\n' "${stderr}" >&2
    failed=1
    return
  fi
  if [[ "${stderr}" != *"maven-compiler-plugin 3.15.0"* ]]; then
    echo "FAIL ${name}: missing compiler fallback warning" >&2
    failed=1
    return
  fi
  echo "ok ${name}"
}

write_xml pretty.xml << 'XML'
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>temurin</vendor>
    </provides>
    <configuration>
      <jdkHome>/usr/lib/jvm/java-8</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>17</version>
    </provides>
    <configuration>
      <jdkHome>/usr/lib/jvm/java-17</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
XML

write_xml netbeans-only.xml << 'XML'
<toolchains>
  <toolchain>
    <type>netbeans</type>
    <provides>
      <version>17</version>
    </provides>
  </toolchain>
</toolchains>
XML

write_xml jdk8-and-netbeans17.xml << 'XML'
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>8</version>
    </provides>
  </toolchain>
  <toolchain>
    <type>netbeans</type>
    <provides>
      <version>17</version>
    </provides>
  </toolchain>
</toolchains>
XML

write_xml multiline-version.xml << 'XML'
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>
        17
      </version>
    </provides>
  </toolchain>
</toolchains>
XML

write_xml multiline-type.xml << 'XML'
<toolchains>
  <toolchain>
    <type>
      jdk
    </type>
    <provides>
      <version>11</version>
    </provides>
  </toolchain>
</toolchains>
XML

write_xml compact-two-jdks.xml << 'XML'
<toolchains><toolchain><type>jdk</type><provides><version>8</version></provides></toolchain><toolchain><type>jdk</type><provides><version>17</version></provides></toolchain></toolchains>
XML

write_xml compact-mixed.xml << 'XML'
<toolchains><toolchain><type>jdk</type><provides><version>8</version></provides></toolchain><toolchain><type>netbeans</type><provides><version>17</version></provides></toolchain></toolchains>
XML

write_xml compact-two-versions.xml << 'XML'
<toolchains><toolchain><type>jdk</type><provides><version>8</version><version>17</version></provides></toolchain></toolchains>
XML

write_xml commented-jdk.xml << 'XML'
<toolchains>
  <!--
  <toolchain>
    <type>jdk</type>
    <provides><version>21</version></provides>
  </toolchain>
  -->
  <toolchain>
    <type>jdk</type>
    <provides><version>8</version></provides>
  </toolchain>
</toolchains>
XML

write_xml spaced.xml << 'XML'
<toolchains>
  <toolchain>
    <type> jdk </type>
    <provides>
      <version> 17 </version>
    </provides>
  </toolchain>
</toolchains>
XML

write_xml uppercase-type.xml << 'XML'
<toolchains>
  <toolchain>
    <type>JDK</type>
    <provides>
      <version>17</version>
    </provides>
  </toolchain>
</toolchains>
XML

printf '<toolchains>\r\n<toolchain>\r\n<type>jdk</type>\r\n<provides>\r\n<version>17</version>\r\n</provides>\r\n</toolchain>\r\n</toolchains>\r\n' > "${tmp}/crlf.xml"

write_xml patch17.xml << 'XML'
<toolchains><toolchain><type>jdk</type><provides><version>17.0.13</version></provides></toolchain></toolchains>
XML
write_xml legacy17.xml << 'XML'
<toolchains><toolchain><type>jdk</type><provides><version>1.17</version></provides></toolchain></toolchains>
XML

expect_status "old exact request 8 misses jdk 1.8" 1 8 "${tmp}/pretty.xml"
expect_status "old exact request 17 misses jdk 17.0.13" 1 17 "${tmp}/patch17.xml"
expect_status "Java 17 range matches jdk 17.0.13" 0 "${jdk17_range}" "${tmp}/patch17.xml"
expect_status "legacy 1.17 cannot satisfy the Java 17 range" 1 "${jdk17_range}" "${tmp}/legacy17.xml"

expect_status "pretty jdk 1.8 matches Java 8 range" 0 "${jdk8_range}" "${tmp}/pretty.xml"
expect_status "pretty jdk 17 matches request 17" 0 "${jdk17_range}" "${tmp}/pretty.xml"
expect_found "pretty has no jdk 21" 21 "${tmp}/pretty.xml" " 1.8 17"

expect_found "non-jdk version is not a jdk toolchain" 17 "${tmp}/netbeans-only.xml" " none"
expect_status "jdk 8 still matches beside a netbeans 17" 0 "${jdk8_range}" "${tmp}/jdk8-and-netbeans17.xml"
expect_found "netbeans 17 does not satisfy jdk 17" 17 "${tmp}/jdk8-and-netbeans17.xml" " 8"

expect_status "multiline version element matches" 0 "${jdk17_range}" "${tmp}/multiline-version.xml"
expect_status "multiline type element matches" 0 11 "${tmp}/multiline-type.xml"

expect_status "compact line keeps the first jdk" 0 "${jdk8_range}" "${tmp}/compact-two-jdks.xml"
expect_status "compact line keeps the second jdk" 0 "${jdk17_range}" "${tmp}/compact-two-jdks.xml"
expect_status "compact mixed line keeps jdk 8" 0 "${jdk8_range}" "${tmp}/compact-mixed.xml"
expect_found "compact netbeans 17 is not jdk 17" 17 "${tmp}/compact-mixed.xml" " 8"

expect_status "one entry uses its last version tag" 1 "${jdk8_range}" "${tmp}/compact-two-versions.xml"
expect_status "one entry with two version tags matches 17" 0 "${jdk17_range}" "${tmp}/compact-two-versions.xml"

expect_status "commented jdk 21 is ignored and jdk 8 matches" 0 "${jdk8_range}" "${tmp}/commented-jdk.xml"
expect_found "commented jdk 21 is not selected" 21 "${tmp}/commented-jdk.xml" " 8"

expect_status "whitespace inside type and version matches" 0 "${jdk17_range}" "${tmp}/spaced.xml"
expect_found "type JDK is not type jdk" 17 "${tmp}/uppercase-type.xml" " none"
expect_status "CRLF toolchains.xml matches" 0 "${jdk17_range}" "${tmp}/crlf.xml"

home="$(mktemp -d "${tmp}/home.XXXXXX")"
mkdir -p "${home}/.m2"
cp "${tmp}/pretty.xml" "${home}/.m2/toolchains.xml"
status=0
stderr="$(env -u JUST_TEST_TOOLCHAINS_XML HOME="${home}" bash "${script}" 17 2>&1)" || status=$?
if [[ "${status}" -ne 0 ]]; then
  echo "FAIL default path reads \$HOME/.m2/toolchains.xml" >&2
  printf '%s\n' "${stderr}" >&2
  failed=1
else
  echo "ok default path reads \$HOME/.m2/toolchains.xml"
fi

missing_home="$(mktemp -d "${tmp}/missing.XXXXXX")"
status=0
stderr="$(env -u JUST_TEST_TOOLCHAINS_XML HOME="${missing_home}" bash "${script}" 8 2>&1)" || status=$?
if [[ "${status}" -eq 0 || "${stderr}" != *"is missing"* ]]; then
  echo "FAIL missing toolchains.xml" >&2
  printf '%s\n' "${stderr}" >&2
  failed=1
else
  echo "ok missing toolchains.xml"
fi

exit "${failed}"
