#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "install-actionlint: $*" >&2
  exit 1
}

info() {
  echo "install-actionlint: $*"
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

actionlint_version="${CREST_ACTIONLINT_VERSION:-v1.7.7}"
install_dir="${CREST_ACTIONLINT_INSTALL_DIR:-/usr/local/bin}"
require_go_module_version CREST_ACTIONLINT_VERSION "${actionlint_version}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

mkdir -p "${install_dir}"

info "installing actionlint ${actionlint_version} into ${install_dir}"
GOBIN="${tmp_dir}" go install "github.com/rhysd/actionlint/cmd/actionlint@${actionlint_version}"
install -m 0755 "${tmp_dir}/actionlint" "${install_dir}/actionlint"

info "installed ${install_dir}/actionlint"
