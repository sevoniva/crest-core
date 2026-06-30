#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

report_file="${CREST_CI_TOOLCHAIN_POLICY_REPORT:-reports/readiness/ci-toolchain-policy.txt}"
workflow_dir="${CREST_CI_TOOLCHAIN_POLICY_WORKFLOW_DIR:-.github/workflows}"

violations=()
workflow_files=()

fail() {
  echo "ci-toolchain-policy-check: $*" >&2
  write_report "failed" "$*"
  exit 1
}

info() {
  echo "ci-toolchain-policy-check: $*"
}

write_report() {
  local status="$1"
  local message="${2:-}"
  mkdir -p "$(dirname "${report_file}")"
  {
    echo "status=${status}"
    echo "workflow_dir=${workflow_dir}"
    echo "workflow_files=${#workflow_files[@]}"
    echo "semgrep_version=${semgrep_version:-}"
    echo "osv_scanner_version=${osv_scanner_version:-}"
    echo "gitleaks_version=${gitleaks_version:-}"
    echo "actionlint_version=${actionlint_version:-}"
    echo "trivy_version=${trivy_version:-}"
    echo "centralized_ci_tool_installs=$([[ ${#violations[@]} -eq 0 ]] && printf true || printf false)"
    if [[ -n "${message}" ]]; then
      echo "message=${message}"
    fi
    if ((${#violations[@]} > 0)); then
      echo "violations:"
      printf '%s\n' "${violations[@]}"
    fi
  } > "${report_file}"
}

require_file() {
  local path="$1"
  [[ -f "${path}" ]] || fail "missing required installer script: ${path}"
}

extract_assignment() {
  local path="$1"
  local variable="$2"
  sed -nE "s/^${variable}=\"\\\$\\{[^:]+:-([^}]+)\\}\"$/\\1/p" "${path}" | head -n 1
}

require_version() {
  local name="$1"
  local value="$2"
  local pattern="$3"
  [[ -n "${value}" ]] || fail "could not read default ${name}"
  [[ "${value}" =~ ${pattern} ]] || fail "${name} must be pinned, got ${value}"
}

check_no_workflow_match() {
  local description="$1"
  local pattern="$2"
  local matches
  matches="$(grep -RInE "${pattern}" "${workflow_files[@]}" 2>/dev/null || true)"
  if [[ -n "${matches}" ]]; then
    violations+=("${description}")
    while IFS= read -r line; do
      [[ -n "${line}" ]] && violations+=("${line}")
    done <<< "${matches}"
  fi
}

require_file scripts/install-semgrep.sh
require_file scripts/install-osv-scanner.sh
require_file scripts/install-gitleaks.sh
require_file scripts/install-actionlint.sh
require_file scripts/install-trivy.sh

semgrep_version="$(extract_assignment scripts/install-semgrep.sh semgrep_version)"
osv_scanner_version="$(extract_assignment scripts/install-osv-scanner.sh osv_scanner_version)"
gitleaks_version="$(extract_assignment scripts/install-gitleaks.sh gitleaks_version)"
actionlint_version="$(extract_assignment scripts/install-actionlint.sh actionlint_version)"
trivy_version="$(extract_assignment scripts/install-trivy.sh trivy_version)"

require_version CREST_SEMGREP_VERSION "${semgrep_version}" '^[0-9]+\.[0-9]+\.[0-9]+$'
require_version CREST_OSV_SCANNER_VERSION "${osv_scanner_version}" '^v[0-9]+\.[0-9]+\.[0-9]+$'
require_version CREST_GITLEAKS_VERSION "${gitleaks_version}" '^v[0-9]+\.[0-9]+\.[0-9]+$'
require_version CREST_ACTIONLINT_VERSION "${actionlint_version}" '^v[0-9]+\.[0-9]+\.[0-9]+$'
require_version CREST_TRIVY_VERSION "${trivy_version}" '^[0-9]+\.[0-9]+\.[0-9]+$'

shopt -s nullglob
workflow_files=("${workflow_dir}"/*.yml "${workflow_dir}"/*.yaml)
shopt -u nullglob

if ((${#workflow_files[@]} == 0)); then
  fail "no GitHub Actions workflow files found under ${workflow_dir}"
fi

check_no_workflow_match \
  "workflows must install Go-based CI tools through scripts/install-*.sh, not inline go install" \
  '(^|[[:space:]])go[[:space:]]+install[[:space:]]'

check_no_workflow_match \
  "workflows must install Semgrep through scripts/install-semgrep.sh, not inline pipx install" \
  'pipx[[:space:]]+install[[:space:]]+semgrep'

if ((${#violations[@]} > 0)); then
  printf '%s\n' "${violations[@]}" >&2
  write_report "failed" "mutable or decentralized CI toolchain installs are not allowed"
  exit 1
fi

write_report "passed"
info "passed; report written to ${report_file}"
