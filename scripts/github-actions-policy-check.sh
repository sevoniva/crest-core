#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

report_file="${CREST_GITHUB_ACTIONS_POLICY_REPORT:-reports/readiness/github-actions-policy.txt}"
workflow_dir="${CREST_GITHUB_ACTIONS_POLICY_WORKFLOW_DIR:-.github/workflows}"

violations=()
action_count=0

fail() {
  echo "github-actions-policy-check: $*" >&2
  write_report "failed" "$*"
  exit 1
}

info() {
  echo "github-actions-policy-check: $*"
}

write_report() {
  local status="$1"
  local message="${2:-}"
  mkdir -p "$(dirname "${report_file}")"
  {
    echo "status=${status}"
    echo "workflow_dir=${workflow_dir}"
    echo "workflow_files=${#workflow_files[@]}"
    echo "action_references=${action_count}"
    echo "github_action_refs_sha_pinned=$([[ ${#violations[@]} -eq 0 ]] && printf true || printf false)"
    if [[ -n "${message}" ]]; then
      echo "message=${message}"
    fi
    if ((${#violations[@]} > 0)); then
      echo "violations:"
      printf '%s\n' "${violations[@]}"
    fi
  } > "${report_file}"
}

strip_uses_value() {
  local raw="$1"
  raw="${raw#*uses:}"
  raw="${raw%%#*}"
  raw="${raw#"${raw%%[![:space:]]*}"}"
  raw="${raw%"${raw##*[![:space:]]}"}"
  raw="${raw%\"}"
  raw="${raw#\"}"
  raw="${raw%\'}"
  raw="${raw#\'}"
  printf '%s' "${raw}"
}

is_commit_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]]
}

validate_uses_ref() {
  local location="$1"
  local value="$2"

  [[ -n "${value}" ]] || {
    violations+=("${location}: empty uses reference")
    return
  }

  if [[ "${value}" == ./* ]]; then
    return
  fi

  if [[ "${value}" == docker://* ]]; then
    if [[ ! "${value}" =~ @sha256:[0-9a-f]{64}$ ]]; then
      violations+=("${location}: docker action must be pinned with @sha256: ${value}")
    fi
    return
  fi

  if [[ "${value}" != *@* ]]; then
    violations+=("${location}: third-party action must include an immutable commit SHA: ${value}")
    return
  fi

  local action_path="${value%@*}"
  local ref="${value##*@}"
  if [[ ! "${action_path}" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(/[^[:space:]@]+)?$ ]]; then
    violations+=("${location}: unsupported action reference format: ${value}")
    return
  fi
  if ! is_commit_sha "${ref}"; then
    violations+=("${location}: third-party action ref must be a 40-character commit SHA, got '${ref}'")
  fi
}

shopt -s nullglob
workflow_files=("${workflow_dir}"/*.yml "${workflow_dir}"/*.yaml)
shopt -u nullglob

if ((${#workflow_files[@]} == 0)); then
  fail "no GitHub Actions workflow files found under ${workflow_dir}"
fi

for workflow_file in "${workflow_files[@]}"; do
  while IFS= read -r match; do
    line_no="${match%%:*}"
    line="${match#*:}"
    uses_value="$(strip_uses_value "${line}")"
    action_count=$((action_count + 1))
    validate_uses_ref "${workflow_file}:${line_no}" "${uses_value}"
  done < <(grep -nE '^[[:space:]]*(-[[:space:]]*)?uses:[[:space:]]*' "${workflow_file}" || true)
done

if ((${#violations[@]} > 0)); then
  printf '%s\n' "${violations[@]}" >&2
  write_report "failed" "mutable GitHub Actions references are not allowed"
  exit 1
fi

write_report "passed"
info "passed; report written to ${report_file}"
