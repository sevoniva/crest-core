#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "create-clean-source-release: $*" >&2
  exit 1
}

info() {
  echo "create-clean-source-release: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

require_cmd date
require_cmd git
require_cmd node
require_cmd rsync
require_cmd tar

default_gitleaks_bin="gitleaks"
if ! command -v "${default_gitleaks_bin}" >/dev/null 2>&1 && [[ -x "${repo_root}/.local/tools/gitleaks" ]]; then
  default_gitleaks_bin="${repo_root}/.local/tools/gitleaks"
fi
gitleaks_bin="${CREST_GITLEAKS_BIN:-${default_gitleaks_bin}}"
require_cmd "${gitleaks_bin}"

sha256_file() {
  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${path}" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
  else
    fail "missing shasum or sha256sum for archive digest"
  fi
}

assert_no_path() {
  local path="$1"
  local description="$2"
  [[ ! -e "${path}" && ! -L "${path}" ]] || fail "export unexpectedly contains ${description}: ${path}"
}

json_array_count() {
  local path="$1"
  node -e "const fs=require('fs'); const p=process.argv[1]; const doc=JSON.parse(fs.readFileSync(p,'utf8') || '[]'); if (!Array.isArray(doc)) process.exit(2); console.log(doc.length)" "${path}"
}

resolve_lexical_path() {
  local path="$1"
  local raw part joined remainder
  local -a stack
  if [[ "${path}" == /* ]]; then
    raw="${path}"
  else
    raw="${repo_root}/${path}"
  fi
  remainder="${raw#/}"
  while [[ -n "${remainder}" ]]; do
    if [[ "${remainder}" == */* ]]; then
      part="${remainder%%/*}"
      remainder="${remainder#*/}"
    else
      part="${remainder}"
      remainder=""
    fi
    case "${part}" in
      ""|.)
        ;;
      ..)
        if ((${#stack[@]} > 0)); then
          unset 'stack[${#stack[@]}-1]'
        fi
        ;;
      *)
        stack+=("${part}")
        ;;
    esac
  done
  if ((${#stack[@]} == 0)); then
    printf '/'
  else
    joined="${stack[0]}"
    for part in "${stack[@]:1}"; do
      joined="${joined}/${part}"
    done
    printf '/%s' "${joined}"
  fi
}

real_path() {
  node -e 'const fs = require("node:fs"); console.log(fs.realpathSync.native(process.argv[1]));' "$1"
}

normalize_dir_path() {
  local env_name="$1"
  local path="$2"
  local logical parent base ancestor suffix ancestor_real
  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" || -L "${logical}" ]]; then
    [[ ! -L "${logical}" ]] || fail "${env_name} must not be a symlink: ${path}"
    [[ -d "${logical}" ]] || fail "${env_name} must be a directory path: ${path}"
    real_path "${logical}"
    return
  fi

  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  ancestor="${parent}"
  while [[ ! -e "${ancestor}" && "${ancestor}" != "/" ]]; do
    ancestor="$(dirname "${ancestor}")"
  done
  [[ -d "${ancestor}" ]] || fail "${env_name} parent path is not a directory: ${path}"
  ancestor_real="$(cd "${ancestor}" && pwd -P)"
  suffix="${parent#"${ancestor}"}"
  printf '%s%s/%s' "${ancestor_real}" "${suffix}" "${base}"
}

normalize_file_path() {
  local env_name="$1"
  local path="$2"
  local logical parent base normalized_parent

  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" || -L "${logical}" ]]; then
    [[ ! -L "${logical}" ]] || fail "${env_name} must not be a symlink: ${path}"
    [[ ! -d "${logical}" ]] || fail "${env_name} must be a file path: ${path}"
    real_path "${logical}"
    return
  fi

  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  normalized_parent="$(normalize_dir_path "${env_name}" "${parent}")"
  printf '%s/%s' "${normalized_parent}" "${base}"
}

assert_safe_output_root() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_CLEAN_SOURCE_OUTPUT_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_CLEAN_SOURCE_OUTPUT_DIR is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_dir_path CREST_CLEAN_SOURCE_OUTPUT_DIR "${path}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/.local"|"${repo_root}/reports"|"${repo_root}/reports/readiness"|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_CLEAN_SOURCE_OUTPUT_DIR is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_CLEAN_SOURCE_OUTPUT_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${normalized}"
}

assert_safe_output_file() {
  local env_name="$1"
  local path="$2"
  local normalized base
  [[ -n "${path}" ]] || fail "${env_name} must not be empty"
  case "${path}" in
    /|.|..|*/)
      fail "${env_name} is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_file_path "${env_name}" "${path}")"
  base="$(basename "${normalized}")"
  case "${base}" in
    ""|.|..)
      fail "${env_name} must be a file path: ${path}"
      ;;
  esac
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "${env_name} is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "${env_name} must stay inside the repository: ${path}"
      ;;
  esac
  [[ ! -d "${normalized}" ]] || fail "${env_name} must be a file path: ${path}"
  printf '%s' "${normalized}"
}

repo_display_path() {
  local normalized="$1"
  case "${normalized}" in
    "${repo_root}/"*)
      printf '%s' "${normalized#"${repo_root}/"}"
      ;;
    *)
      printf '%s' "${normalized}"
      ;;
  esac
}

assert_safe_release_name() {
  local name="$1"
  [[ -n "${name}" ]] || fail "CREST_CLEAN_SOURCE_NAME must not be empty"
  case "${name}" in
    .|..|*/*|*\\*)
      fail "CREST_CLEAN_SOURCE_NAME must be a single safe directory name"
      ;;
  esac
  [[ "${name}" =~ ^[A-Za-z0-9._-]+$ ]] \
    || fail "CREST_CLEAN_SOURCE_NAME may only contain letters, numbers, dot, underscore and dash"
}

validate_history_delivery_evidence() {
  case "${history_delivery_path}" in
    clean-source|clean-history|fresh-repository)
      ;;
    *)
      fail "CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH must be clean-source, clean-history or fresh-repository"
      ;;
  esac

  if [[ "${history_findings_remaining}" != "unknown" && ! "${history_findings_remaining}" =~ ^[0-9]+$ ]]; then
    fail "CREST_CLEAN_SOURCE_HISTORY_FINDINGS_REMAINING must be a non-negative integer or unknown"
  fi

  case "${credential_rotation_status}" in
    rotated-before-delivery|not-applicable-clean-history|not-recorded)
      ;;
    *)
      fail "CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS must be rotated-before-delivery, not-applicable-clean-history or not-recorded"
      ;;
  esac

  if [[ "${history_delivery_path}" == "clean-history" && "${history_findings_remaining}" != "0" ]]; then
    fail "clean-history delivery requires history_findings_remaining=0"
  fi

  if [[ "${CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION:-false}" == "true" ]]; then
    [[ "${history_findings_remaining}" =~ ^[0-9]+$ ]] \
      || fail "credential rotation enforcement requires a numeric history_findings_remaining value"
    if [[ "${history_delivery_path}" != "clean-history" && "${history_findings_remaining}" != "0" ]]; then
      [[ "${credential_rotation_status}" == "rotated-before-delivery" ]] \
        || fail "${history_delivery_path} delivery with history findings requires CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery"
      case "${affected_credential_classes}" in
        ""|not-recorded|unknown|none|n/a|N/A)
          fail "${history_delivery_path} delivery with history findings requires CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES"
          ;;
      esac
    fi
    if [[ "${history_delivery_path}" == "clean-history" ]]; then
      [[ "${credential_rotation_status}" == "not-applicable-clean-history" || "${credential_rotation_status}" == "rotated-before-delivery" ]] \
        || fail "clean-history delivery requires credential rotation status to be not-applicable-clean-history or rotated-before-delivery"
    fi
  fi
}

version="$(tr -d '[:space:]' < VERSION)"
[[ -n "${version}" ]] || fail "VERSION is empty"

branch="$(git branch --show-current || true)"
commit="$(git rev-parse --verify HEAD 2>/dev/null || true)"
dirty="false"
if ! git diff --quiet || ! git diff --cached --quiet || [[ -n "$(git ls-files --others --exclude-standard)" ]]; then
  dirty="true"
fi

if [[ "${dirty}" == "true" && "${CREST_CLEAN_SOURCE_REQUIRE_CLEAN_WORKTREE:-false}" == "true" ]]; then
  fail "clean source release requires a clean git worktree when CREST_CLEAN_SOURCE_REQUIRE_CLEAN_WORKTREE=true; commit or remove pending changes, then regenerate the artifact"
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_root="${CREST_CLEAN_SOURCE_OUTPUT_DIR:-reports/release-source}"
release_name="${CREST_CLEAN_SOURCE_NAME:-crest-core-${version}-source-${timestamp}}"
output_root="$(assert_safe_output_root "${output_root}")"
assert_safe_release_name "${release_name}"
work_dir="${output_root}/${release_name}"
archive_path="${output_root}/${release_name}.tar.gz"
scan_report_input="${CREST_CLEAN_SOURCE_GITLEAKS_REPORT:-reports/security/gitleaks-clean-source.json}"
summary_path="${output_root}/${release_name}.summary.txt"
manifest_path="${work_dir}/SOURCE_MANIFEST.txt"
history_scan_report="${CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT:-reports/security/gitleaks-history.json}"
history_delivery_path="${CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH:-clean-source}"
credential_rotation_status="${CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS:-not-recorded}"
affected_credential_classes="${CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES:-not-recorded}"
history_findings_remaining="${CREST_CLEAN_SOURCE_HISTORY_FINDINGS_REMAINING:-}"
history_scan_report_sha256="missing"
scan_report_path="$(assert_safe_output_file CREST_CLEAN_SOURCE_GITLEAKS_REPORT "${scan_report_input}")"
scan_report="$(repo_display_path "${scan_report_path}")"
file_list="$(mktemp)"
archive_listing="$(mktemp)"
trap 'rm -f "${file_list}" "${archive_listing}"' EXIT

if [[ -z "${history_findings_remaining}" ]]; then
  if [[ -f "${history_scan_report}" ]]; then
    history_findings_remaining="$(json_array_count "${history_scan_report}")" \
      || fail "history scan report must be a JSON array: ${history_scan_report}"
  else
    history_findings_remaining="unknown"
  fi
fi
if [[ -f "${history_scan_report}" ]]; then
  history_scan_report_sha256="$(sha256_file "${history_scan_report}")"
elif [[ "${CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION:-false}" == "true" ]]; then
  fail "credential rotation enforcement requires CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT to exist"
fi
validate_history_delivery_evidence

mkdir -p "${output_root}" "$(dirname "${scan_report_path}")"
rm -rf "${work_dir}" "${archive_path}" "${archive_path}.sha256" "${summary_path}"

info "building clean source file list"
while IFS= read -r -d '' path; do
  [[ -e "${path}" || -L "${path}" ]] || continue
  case "${path}" in
    .git/*|.cache/*|.local/*|.crest-local/*|.crest-verify-*/*|reports/*)
      continue
      ;;
    private-tests|private-tests/*|scripts/restore-private-tests.mjs)
      continue
      ;;
    node_modules/*|*/node_modules/*|target/*|*/target/*|dist/*|*/dist/*)
      continue
      ;;
    core/core-backend/src/main/resources/static/*|core/core-frontend/node/*|core/core-frontend/lib/*)
      continue
      ;;
    .gitmodules|.flattened-pom.xml|*/.flattened-pom.xml|.DS_Store|*/.DS_Store|.idea/*|.vscode/*)
      continue
      ;;
    .env|.env.*|*/.env|*/.env.*|*.local|*.local.*|*.local.sql|*_local.sql)
      continue
      ;;
    *.dump|*.bak|*.backup|*.db|*.sqlite|*.sqlite3)
      continue
      ;;
    *.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|*.csr|*.crt)
      continue
      ;;
    private/*|secrets/*|release/*|releases/*|artifacts/*|packages/*|tmp/*|temp/*|.tmp/*|.temp/*)
      continue
      ;;
  esac
  printf '%s\0' "${path}"
done < <(git ls-files -z --cached --modified --others --exclude-standard) > "${file_list}"

source_file_count="$(tr '\0' '\n' < "${file_list}" | sed '/^$/d' | wc -l | tr -d '[:space:]')"
[[ "${source_file_count}" -gt 0 ]] || fail "clean source file list is empty"

info "copying current tracked and unignored source files to ${work_dir}"
mkdir -p "${work_dir}"
rsync -a --relative --from0 --files-from="${file_list}" "${repo_root}/" "${work_dir}/"

assert_no_path "${work_dir}/.git" ".git"
assert_no_path "${work_dir}/.gitmodules" ".gitmodules"
assert_no_path "${work_dir}/.local" ".local"
assert_no_path "${work_dir}/reports" "reports"
assert_no_path "${work_dir}/private" "private"
assert_no_path "${work_dir}/private-tests" "private-tests"
assert_no_path "${work_dir}/scripts/restore-private-tests.mjs" "private test restore script"
assert_no_path "${work_dir}/core/core-backend/src/main/resources/static" "generated backend static resources"

if find "${work_dir}" \( -name node_modules -o -name target -o -name dist \) -prune -print | grep -q .; then
  fail "export unexpectedly contains generated dependency or build directories"
fi

{
  echo "Crest Core clean source release"
  echo "generated_at_utc=${timestamp}"
  echo "version=${version}"
  echo "source_branch=${branch:-unknown}"
  echo "source_commit=${commit:-unknown}"
  echo "source_worktree_dirty=${dirty}"
  echo "source_file_count=${source_file_count}"
  echo "history_policy=This artifact intentionally excludes .git history. Rotate any exposed historical credentials before using it as an enterprise delivery path."
  echo "history_scan_report=${history_scan_report}"
  echo "history_scan_report_sha256=${history_scan_report_sha256}"
  echo "history_findings_remaining=${history_findings_remaining}"
  echo "history_delivery_path=${history_delivery_path}"
  echo "credential_rotation_status=${credential_rotation_status}"
  echo "affected_credential_classes=${affected_credential_classes}"
  echo "secret_scan_report=${scan_report}"
} > "${manifest_path}"

info "running Gitleaks secret scan on clean source export"
"${gitleaks_bin}" detect \
  --config "${repo_root}/gitleaks.toml" \
  --source "${work_dir}" \
  --no-git \
  --redact \
  --exit-code 1 \
  --report-format json \
  --report-path "${scan_report_path}" \
  --log-level warn

node -e "const fs=require('fs'); const p=process.argv[1]; const doc=JSON.parse(fs.readFileSync(p,'utf8') || '[]'); if (!Array.isArray(doc) || doc.length !== 0) process.exit(1)" "${scan_report_path}" \
  || fail "clean source Gitleaks report must be an empty JSON array"

info "creating ${archive_path}"
tar -czf "${archive_path}" -C "${output_root}" "${release_name}"

archive_sha256="$(sha256_file "${archive_path}")"
printf '%s  %s\n' "${archive_sha256}" "${archive_path}" > "${archive_path}.sha256"

tar -tzf "${archive_path}" > "${archive_listing}"
if grep -Eq '(^|/)(\.git|\.gitmodules|\.local|reports|private|private-tests|node_modules|target|dist)(/|$)' "${archive_listing}"; then
  fail "clean source archive contains forbidden history, private, report, dependency or build paths"
fi
if grep -Eq '(^|/)scripts/restore-private-tests\.mjs$|(^|/)core/core-backend/src/main/resources/static(/|$)' "${archive_listing}"; then
  fail "clean source archive contains private restore tooling or generated backend static resources"
fi

{
  echo "Crest Core clean source release summary"
  echo "generated_at_utc=${timestamp}"
  echo "version=${version}"
  echo "source_branch=${branch:-unknown}"
  echo "source_commit=${commit:-unknown}"
  echo "archive=${archive_path}"
  echo "archive_sha256=${archive_sha256}"
  echo "source_file_count=${source_file_count}"
  echo "secret_scan_report=${scan_report}"
  echo "secret_scan_findings=0"
  echo "source_worktree_dirty=${dirty}"
  echo "history_policy=no-git-history; credential rotation evidence still required for historical findings"
  echo "history_scan_report=${history_scan_report}"
  echo "history_scan_report_sha256=${history_scan_report_sha256}"
  echo "history_findings_remaining=${history_findings_remaining}"
  echo "history_delivery_path=${history_delivery_path}"
  echo "credential_rotation_status=${credential_rotation_status}"
  echo "affected_credential_classes=${affected_credential_classes}"
} > "${summary_path}"

info "passed; clean source archive written to ${archive_path}"
