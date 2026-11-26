#!/usr/bin/env bash
# 双 JDK 发布构建与 Maven 编译器使用同一套版本匹配规则。
set -euo pipefail

requested="${1:?usage: require-jdk-toolchain.sh <jdk-version-range> [maven-home]}"
file="${JUST_TEST_TOOLCHAINS_XML:-${HOME}/.m2/toolchains.xml}"

if [[ ! -f "${file}" ]]; then
  echo "dual-jdk-release: ${file} is missing, so JDK ${requested} is not selected." >&2
  echo "maven-compiler-plugin 3.15.0 would compile with the current JDK." >&2
  exit 1
fi

maven_dir="${2:-$(mvn -B -v | sed -n 's/^Maven home: //p')}"
script_dir="$(cd "$(dirname "$0")" && pwd)"
classes="$(mktemp -d)"
trap 'rm -rf "${classes}"' EXIT
javac -proc:none -cp "${maven_dir}/lib/*" -d "${classes}" "${script_dir}/RequireJdkToolchain.java"
java -cp "${classes}:${maven_dir}/lib/*" RequireJdkToolchain "${file}" "${requested}"
