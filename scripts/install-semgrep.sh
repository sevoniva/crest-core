#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "install-semgrep: $*" >&2
  exit 1
}

info() {
  echo "install-semgrep: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

require_semver() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "${name} must be pinned to X.Y.Z, got ${value}"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

require_cmd python3

semgrep_version="${CREST_SEMGREP_VERSION:-1.155.0}"
install_dir="${CREST_SEMGREP_INSTALL_DIR:-}"
require_semver CREST_SEMGREP_VERSION "${semgrep_version}"

pipx_args=(install --force --python "$(command -v python3)" "semgrep==${semgrep_version}")
if [[ -n "${install_dir}" ]]; then
  mkdir -p "${install_dir}"
  export PIPX_BIN_DIR="${install_dir}"
fi

info "installing semgrep ${semgrep_version}"
python3 -m pipx "${pipx_args[@]}"

info "installed semgrep ${semgrep_version}"
