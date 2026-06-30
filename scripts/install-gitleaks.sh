#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "install-gitleaks: $*" >&2
  exit 1
}

info() {
  echo "install-gitleaks: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

require_go_module_version() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "${name} must be pinned to vX.Y.Z, got ${value}"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

require_cmd go

gitleaks_version="${CREST_GITLEAKS_VERSION:-v8.28.0}"
install_dir="${CREST_GITLEAKS_INSTALL_DIR:-/usr/local/bin}"
require_go_module_version CREST_GITLEAKS_VERSION "${gitleaks_version}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

mkdir -p "${install_dir}"

info "installing gitleaks ${gitleaks_version} into ${install_dir}"
GOBIN="${tmp_dir}" go install "github.com/zricethezav/gitleaks/v8@${gitleaks_version}"
install -m 0755 "${tmp_dir}/gitleaks" "${install_dir}/gitleaks"

info "installed ${install_dir}/gitleaks"
