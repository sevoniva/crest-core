#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "install-osv-scanner: $*" >&2
  exit 1
}

info() {
  echo "install-osv-scanner: $*"
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

osv_scanner_version="${CREST_OSV_SCANNER_VERSION:-v1.9.2}"
install_dir="${CREST_OSV_SCANNER_INSTALL_DIR:-/usr/local/bin}"
require_go_module_version CREST_OSV_SCANNER_VERSION "${osv_scanner_version}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

mkdir -p "${install_dir}"

info "installing osv-scanner ${osv_scanner_version} into ${install_dir}"
GOBIN="${tmp_dir}" go install "github.com/google/osv-scanner/cmd/osv-scanner@${osv_scanner_version}"
install -m 0755 "${tmp_dir}/osv-scanner" "${install_dir}/osv-scanner"

info "installed ${install_dir}/osv-scanner"
