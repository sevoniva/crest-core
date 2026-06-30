#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "history-secret-audit: $*" >&2
  exit 1
}

info() {
  echo "history-secret-audit: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${repo_root}"

default_gitleaks_bin="gitleaks"
if ! command -v "${default_gitleaks_bin}" >/dev/null 2>&1 && [[ -x "${repo_root}/.local/tools/gitleaks" ]]; then
  default_gitleaks_bin="${repo_root}/.local/tools/gitleaks"
fi
gitleaks_bin="${CREST_GITLEAKS_BIN:-${default_gitleaks_bin}}"
report_dir="${CREST_SECURITY_REPORT_DIR:-reports/security}"

require_cmd "${gitleaks_bin}"
require_cmd node

resolve_path() {
  node -e 'const path = require("node:path"); console.log(path.resolve(process.argv[1]));' "$1"
}

real_path() {
  node -e 'const fs = require("node:fs"); console.log(fs.realpathSync.native(process.argv[1]));' "$1"
}

normalize_dir_path() {
  local path="$1"
  local lexical current suffix next real_current

  lexical="$(resolve_path "${path}")"
  if [[ -e "${lexical}" ]]; then
    [[ -d "${lexical}" ]] || fail "path must be a directory: ${path}"
    real_path "${lexical}"
    return
  fi

  current="${lexical}"
  suffix=""
  while [[ ! -e "${current}" ]]; do
    suffix="/$(basename "${current}")${suffix}"
    next="$(dirname "${current}")"
    [[ "${next}" != "${current}" ]] || fail "path has no existing parent directory: ${path}"
    current="${next}"
  done

  [[ -d "${current}" ]] || fail "path parent must be a directory: ${path}"
  real_current="$(real_path "${current}")"
  printf '%s%s' "${real_current}" "${suffix}"
}

normalize_file_path() {
  local path="$1"
  local lexical parent base normalized_parent

  lexical="$(resolve_path "${path}")"
  if [[ -e "${lexical}" || -L "${lexical}" ]]; then
    [[ ! -d "${lexical}" ]] || fail "path must be a file: ${path}"
    [[ -e "${lexical}" ]] || fail "path must not be a dangling symlink: ${path}"
    real_path "${lexical}"
    return
  fi

  parent="$(dirname "${lexical}")"
  base="$(basename "${lexical}")"
  normalized_parent="$(normalize_dir_path "${parent}")"
  printf '%s/%s' "${normalized_parent}" "${base}"
}

assert_safe_report_dir() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_SECURITY_REPORT_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_SECURITY_REPORT_DIR is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_dir_path "${path}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_SECURITY_REPORT_DIR is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_SECURITY_REPORT_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${normalized}"
}

assert_safe_summary_file() {
  local path="$1"
  local normalized base
  [[ -n "${path}" ]] || fail "CREST_HISTORY_SECRET_SUMMARY must not be empty"
  case "${path}" in
    /|.|..|*/)
      fail "CREST_HISTORY_SECRET_SUMMARY is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_file_path "${path}")"
  base="$(basename "${path}")"
  case "${base}" in
    ""|.|..)
      fail "CREST_HISTORY_SECRET_SUMMARY must be a file path: ${path}"
      ;;
  esac
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_HISTORY_SECRET_SUMMARY is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_HISTORY_SECRET_SUMMARY must stay inside the repository: ${path}"
      ;;
  esac
  [[ ! -d "${normalized}" ]] || fail "CREST_HISTORY_SECRET_SUMMARY must be a file path: ${path}"
  printf '%s' "${normalized}"
}

report_dir="$(assert_safe_report_dir "${report_dir}")"
report_file="${report_dir}/gitleaks-history.json"
summary_file="${CREST_HISTORY_SECRET_SUMMARY:-${report_dir}/gitleaks-history-summary.txt}"
summary_file="$(assert_safe_summary_file "${summary_file}")"

mkdir -p "${report_dir}"
mkdir -p "$(dirname "${summary_file}")"
: > "${summary_file}"

info "running redacted Gitleaks scan across git history"
scan_status=0
"${gitleaks_bin}" detect \
  --config gitleaks.toml \
  --source . \
  --redact \
  --exit-code 1 \
  --report-format json \
  --report-path "${report_file}" \
  --log-level warn || scan_status=$?

if [[ ! -s "${report_file}" ]]; then
  {
    echo "Crest Core history secret audit summary"
    echo "status=passed"
    echo "report=${report_file}"
    echo "findings=0"
    echo "commits=0"
    echo "remediation_required=false"
    echo "delivery_options=clean-history"
  } > "${summary_file}"
  info "passed; no history leaks found"
  exit 0
fi

node - "${report_file}" "${summary_file}" <<'NODE'
const fs = require("node:fs");
const rows = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const summaryFile = process.argv[3];
const byRule = new Map();
const byFile = new Map();
const commits = new Set();
for (const row of rows) {
  byRule.set(row.RuleID, (byRule.get(row.RuleID) || 0) + 1);
  byFile.set(row.File, (byFile.get(row.File) || 0) + 1);
  if (row.Commit) {
    commits.add(row.Commit);
  }
}
console.log(`history-secret-audit: findings=${rows.length}, commits=${commits.size}`);
console.log("history-secret-audit: rules:");
for (const [rule, count] of [...byRule.entries()].sort((left, right) => right[1] - left[1])) {
  console.log(`history-secret-audit:   ${rule}: ${count}`);
}
console.log("history-secret-audit: files:");
for (const [file, count] of [...byFile.entries()].sort((left, right) => right[1] - left[1])) {
  console.log(`history-secret-audit:   ${file}: ${count}`);
}
const lines = [
  "Crest Core history secret audit summary",
  `status=${rows.length === 0 ? "passed" : "findings"}`,
  `report=${process.argv[2]}`,
  `findings=${rows.length}`,
  `commits=${commits.size}`,
  `remediation_required=${rows.length === 0 ? "false" : "true"}`,
  `delivery_options=${rows.length === 0 ? "clean-history" : "rotate-credentials-and-use-clean-source-or-fresh-repository"}`,
];
for (const [rule, count] of [...byRule.entries()].sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))) {
  lines.push(`rule=${rule}:${count}`);
}
for (const [file, count] of [...byFile.entries()].sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))) {
  lines.push(`file=${file}:${count}`);
}
fs.writeFileSync(summaryFile, `${lines.join("\n")}\n`);
NODE

if [[ "${CREST_HISTORY_SECRET_AUDIT_ALLOW_FINDINGS:-false}" == "true" ]]; then
  info "history findings allowed for this run; redacted report written to ${report_file}; summary written to ${summary_file}"
  exit 0
fi

[[ "${scan_status}" -eq 0 ]] || fail "history secret audit found findings; rotate any exposed credentials and publish from a cleaned history or fresh repository"
