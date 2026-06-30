#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "workflow-lint: $*" >&2
  exit 1
}

info() {
  echo "workflow-lint: $*"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

default_actionlint_bin="actionlint"
if ! command -v "${default_actionlint_bin}" >/dev/null 2>&1 && [[ -x "${repo_root}/.local/tools/actionlint" ]]; then
  default_actionlint_bin="${repo_root}/.local/tools/actionlint"
fi
actionlint_bin="${CREST_ACTIONLINT_BIN:-${default_actionlint_bin}}"

command -v "${actionlint_bin}" >/dev/null 2>&1 || fail "missing required command: ${actionlint_bin}. Install actionlint v1.7.7, or set CREST_ACTIONLINT_BIN to its absolute path."

shopt -s nullglob
workflow_files=(.github/workflows/*.yml .github/workflows/*.yaml)
shopt -u nullglob

if ((${#workflow_files[@]} == 0)); then
  fail "no GitHub Actions workflow files found"
fi

info "checking ${#workflow_files[@]} GitHub Actions workflow file(s)"
"${actionlint_bin}" "${workflow_files[@]}"
bash scripts/github-actions-policy-check.sh
bash scripts/ci-toolchain-policy-check.sh
info "passed"
