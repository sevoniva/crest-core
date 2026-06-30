#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "container-base-image-policy-check: $*" >&2
  write_report "failed" "$*"
  exit 1
}

info() {
  echo "container-base-image-policy-check: $*"
}

bool_value() {
  local name="$1"
  local value="$2"
  case "${value}" in
    true|false)
      ;;
    *)
      fail "${name} must be true or false"
      ;;
  esac
}

has_sha256_digest() {
  [[ "$1" =~ @sha256:[0-9a-f]{64}$ ]]
}

has_digest_separator() {
  [[ "$1" == *@* ]]
}

uses_latest_tag() {
  [[ "$1" =~ (^|/)[^/@:]+:latest($|@) ]]
}

digest_pinned() {
  if has_sha256_digest "$1"; then
    printf 'true'
  else
    printf 'false'
  fi
}

validate_image_ref() {
  local name="$1"
  local image="$2"
  [[ -n "${image}" ]] || fail "${name} image reference must not be empty"
  [[ "${image}" != *$'\n'* && "${image}" != *$'\r'* && "${image}" != *" "* && "${image}" != *$'\t'* ]] \
    || fail "${name} image reference must be a single token"
  if has_digest_separator "${image}" && ! has_sha256_digest "${image}"; then
    fail "${name} image reference must use a sha256 digest"
  fi
  if uses_latest_tag "${image}"; then
    fail "${name} image reference must not use the mutable latest tag"
  fi
  if [[ "${require_digests}" == "true" ]] && ! has_sha256_digest "${image}"; then
    fail "${name} image reference must be pinned with @sha256 when CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS=true"
  fi
}

write_report() {
  local status="$1"
  local message="${2:-}"
  mkdir -p "$(dirname "${report_file}")"
  {
    echo "status=${status}"
    echo "require_base_image_digests=${require_digests:-}"
    echo "jdk_image=${jdk_image:-}"
    echo "jdk_image_digest_pinned=$(digest_pinned "${jdk_image:-}")"
    echo "runtime_image=${runtime_image:-}"
    echo "runtime_image_digest_pinned=$(digest_pinned "${runtime_image:-}")"
    echo "nginx_image=${nginx_image:-}"
    echo "nginx_image_digest_pinned=$(digest_pinned "${nginx_image:-}")"
    if [[ -n "${message}" ]]; then
      echo "message=${message}"
    fi
  } > "${report_file}"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

report_file="${CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT:-reports/readiness/container-base-image-policy.txt}"
require_digests="${CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS:-false}"
jdk_image="${CREST_DOCKER_JDK_IMAGE:-eclipse-temurin:17-jdk-jammy@sha256:beabb759e6f9653c843958d1d1f5cecb881dfb85aa6081e2bef099ab1260344e}"
runtime_image="${CREST_DOCKER_RUNTIME_IMAGE:-ubuntu:24.04@sha256:786a8b558f7be160c6c8c4a54f9a57274f3b4fb1491cf65146521ae77ff1dc54}"
nginx_image="${CREST_DOCKER_NGINX_IMAGE:-nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de}"

bool_value CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS "${require_digests}"
validate_image_ref "CREST_DOCKER_JDK_IMAGE" "${jdk_image}"
validate_image_ref "CREST_DOCKER_RUNTIME_IMAGE" "${runtime_image}"
validate_image_ref "CREST_DOCKER_NGINX_IMAGE" "${nginx_image}"

write_report "passed"
info "passed; report written to ${report_file}"
