#!/usr/bin/env bash

ensure_java17() {
  local candidate="${JAVA_HOME:-}"
  local -a candidates=()

  if [[ -n "${candidate}" ]]; then
    candidates+=("${candidate}")
  fi

  candidates+=(
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/usr/lib/jvm/java-17-openjdk"
    "/usr/lib/jvm/java-17-openjdk-amd64"
    "/usr/lib/jvm/temurin-17-jdk-amd64"
  )

  for candidate in "${candidates[@]}"; do
    if [[ -x "${candidate}/bin/java" ]]; then
      JAVA_HOME="${candidate}"
      export JAVA_HOME
      export PATH="${JAVA_HOME}/bin:${PATH}"
      break
    fi
  done

  if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
    echo "java17: OpenJDK 17 not found. Set JAVA_HOME to an OpenJDK 17 installation." >&2
    return 1
  fi

  local spec_version
  spec_version="$("${JAVA_HOME}/bin/java" -XshowSettings:properties -version 2>&1 \
    | awk -F= '/java.specification.version/ { gsub(/[[:space:]]/, "", $2); print $2; exit }')"

  if [[ "${spec_version}" != "17" ]]; then
    echo "java17: JAVA_HOME must point to OpenJDK 17, got java.specification.version=${spec_version} at ${JAVA_HOME}." >&2
    return 1
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  ensure_java17
  "${JAVA_HOME}/bin/java" -version
fi
