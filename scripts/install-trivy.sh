#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "install-trivy: $*" >&2
  exit 1
}

info() {
  echo "install-trivy: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  case "${value}" in
    ''|*[!0-9]*|0)
      fail "${name} must be a positive integer"
      ;;
  esac
}

require_semver() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^v?[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "${name} must be pinned to X.Y.Z or vX.Y.Z, got ${value}"
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_cmd curl
require_cmd tar
require_cmd awk

trivy_version="${CREST_TRIVY_VERSION:-0.71.2}"
require_semver CREST_TRIVY_VERSION "${trivy_version}"
trivy_version="${trivy_version#v}"
install_dir="${CREST_TRIVY_INSTALL_DIR:-/usr/local/bin}"
download_base="${CREST_TRIVY_DOWNLOAD_BASE_URL:-https://github.com/aquasecurity/trivy/releases/download/v${trivy_version}}"
connect_timeout="${CREST_DOWNLOAD_CONNECT_TIMEOUT_SECONDS:-20}"
max_time="${CREST_DOWNLOAD_MAX_TIME_SECONDS:-300}"
speed_time="${CREST_DOWNLOAD_SPEED_TIME_SECONDS:-30}"
speed_limit="${CREST_DOWNLOAD_SPEED_LIMIT_BYTES:-1024}"
download_proxy="${CREST_DOWNLOAD_PROXY:-}"

require_positive_integer CREST_DOWNLOAD_CONNECT_TIMEOUT_SECONDS "${connect_timeout}"
require_positive_integer CREST_DOWNLOAD_MAX_TIME_SECONDS "${max_time}"
require_positive_integer CREST_DOWNLOAD_SPEED_TIME_SECONDS "${speed_time}"
require_positive_integer CREST_DOWNLOAD_SPEED_LIMIT_BYTES "${speed_limit}"

curl_args=(
  -fsSL
  --retry 5
  --retry-delay 3
  --retry-all-errors
  --connect-timeout "${connect_timeout}"
  --max-time "${max_time}"
  --speed-time "${speed_time}"
  --speed-limit "${speed_limit}"
)
if [[ -n "${download_proxy}" ]]; then
  curl_args+=(--proxy "${download_proxy}")
fi

case "$(uname -s)" in
  Linux)
    os="Linux"
    ;;
  Darwin)
    os="macOS"
    ;;
  *)
    fail "unsupported OS: $(uname -s)"
    ;;
esac

case "$(uname -m)" in
  x86_64|amd64)
    arch="64bit"
    ;;
  arm64|aarch64)
    arch="ARM64"
    ;;
  *)
    fail "unsupported architecture: $(uname -m)"
    ;;
esac

asset="trivy_${trivy_version}_${os}-${arch}.tar.gz"
checksums="trivy_${trivy_version}_checksums.txt"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

info "downloading ${asset}"
curl "${curl_args[@]}" \
  "${download_base}/${checksums}" \
  -o "${tmp_dir}/${checksums}"
curl "${curl_args[@]}" \
  "${download_base}/${asset}" \
  -o "${tmp_dir}/${asset}"

expected_checksum="$(awk -v asset="${asset}" '$2 == asset { print $1 }' "${tmp_dir}/${checksums}")"
[[ -n "${expected_checksum}" ]] || fail "could not find ${asset} in ${checksums}"
actual_checksum="$(sha256_file "${tmp_dir}/${asset}")"
[[ "${actual_checksum}" == "${expected_checksum}" ]] || fail "checksum mismatch for ${asset}"

tar -xzf "${tmp_dir}/${asset}" -C "${tmp_dir}" trivy
mkdir -p "${install_dir}"
install -m 0755 "${tmp_dir}/trivy" "${install_dir}/trivy"

info "installed ${install_dir}/trivy"
