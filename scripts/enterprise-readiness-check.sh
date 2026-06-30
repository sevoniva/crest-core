#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "enterprise-readiness-check: $*" >&2
  exit 1
}

info() {
  echo "enterprise-readiness-check: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${repo_root}"

require_cmd bash
require_cmd date
require_cmd node

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

normalize_dir_path() {
  local env_name="$1"
  local path="$2"
  local logical parent base ancestor suffix ancestor_real
  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" ]]; then
    [[ -d "${logical}" ]] || fail "${env_name} must be a directory path: ${path}"
    (cd "${logical}" && pwd -P)
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

real_path() {
  node -e 'const fs = require("node:fs"); console.log(fs.realpathSync.native(process.argv[1]));' "$1"
}

normalize_file_path() {
  local env_name="$1"
  local path="$2"
  local logical parent base normalized_parent

  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" || -L "${logical}" ]]; then
    [[ ! -d "${logical}" ]] || fail "${env_name} must be a file path: ${path}"
    [[ -e "${logical}" ]] || fail "${env_name} must not be a dangling symlink: ${path}"
    real_path "${logical}"
    return
  fi

  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  normalized_parent="$(normalize_dir_path "${env_name}" "${parent}")"
  printf '%s/%s' "${normalized_parent}" "${base}"
}

assert_safe_readiness_report_dir() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_READINESS_REPORT_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_READINESS_REPORT_DIR is too broad to write readiness into: ${path}"
      ;;
  esac
  normalized="$(normalize_dir_path CREST_READINESS_REPORT_DIR "${path}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/.local"|\
    "${repo_root}/reports"|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_READINESS_REPORT_DIR is too broad to write readiness into: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_READINESS_REPORT_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${path}"
}

assert_safe_history_summary_file() {
  local path="$1"
  local normalized base
  [[ -n "${path}" ]] || fail "CREST_HISTORY_SECRET_SUMMARY must not be empty"
  case "${path}" in
    /|.|..|*/)
      fail "CREST_HISTORY_SECRET_SUMMARY is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_file_path CREST_HISTORY_SECRET_SUMMARY "${path}")"
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
  printf '%s' "${normalized}"
}

assert_safe_gate_log_dir() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_READINESS_GATE_LOG_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_READINESS_GATE_LOG_DIR is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_dir_path CREST_READINESS_GATE_LOG_DIR "${path}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/.local"|\
    "${repo_root}/reports"|"${repo_root}/reports/readiness"|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*|\
    "${report_dir_normalized}")
      fail "CREST_READINESS_GATE_LOG_DIR is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_READINESS_GATE_LOG_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${path}"
}

report_dir="${CREST_READINESS_REPORT_DIR:-reports/readiness}"
report_dir="$(assert_safe_readiness_report_dir "${report_dir}")"
report_dir_normalized="$(normalize_dir_path CREST_READINESS_REPORT_DIR "${report_dir}")"
mkdir -p -- "${report_dir}"
summary_file="${report_dir}/enterprise-readiness-summary.txt"
: > "${summary_file}"
gate_log_dir="${CREST_READINESS_GATE_LOG_DIR:-${report_dir}/gate-logs}"
gate_log_dir="$(assert_safe_gate_log_dir "${gate_log_dir}")"
rm -rf -- "${gate_log_dir}"
mkdir -p -- "${gate_log_dir}"

record() {
  local message="$1"
  printf '%s\n' "${message}" >> "${summary_file}"
}

evidence_field_value() {
  local path="$1"
  local field="$2"
  awk -v target="${field}" '
    BEGIN { FS = ":" }
    tolower($1) == target {
      sub(/^[^:]*:[[:space:]]*/, "")
      sub(/[[:space:]]+$/, "")
      print
      exit
    }
  ' "${path}"
}

summary_field_value() {
  local path="$1"
  local field="$2"
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${path}"
}

file_sha256() {
  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${path}" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
  else
    fail "missing shasum or sha256sum for evidence digest"
  fi
}

repo_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s' "${path}"
  else
    printf '%s/%s' "${repo_root}" "${path}"
  fi
}

display_repo_path() {
  local path="$1"
  local absolute
  absolute="$(repo_path "${path}")"
  case "${absolute}" in
    "${repo_root}")
      printf '.'
      ;;
    "${repo_root}/"*)
      printf '%s' "${absolute#"${repo_root}/"}"
      ;;
    *)
      printf '%s' "${path}"
      ;;
  esac
}

write_digest_manifest() {
  local base_dir="$1"
  local manifest_path="$2"
  shift 2

  mkdir -p "$(dirname "${manifest_path}")"
  : > "${manifest_path}"
  for rel_path in "$@"; do
    [[ -s "${base_dir}/${rel_path}" ]] || fail "manifest source file is missing or empty: ${base_dir}/${rel_path}"
    printf '%s  %s\n' "$(file_sha256 "${base_dir}/${rel_path}")" "${rel_path}" >> "${manifest_path}"
  done
}

latest_clean_source_summary() {
  local output_root="${CREST_CLEAN_SOURCE_OUTPUT_DIR:-reports/release-source}"
  local latest
  latest="$(ls -t "${output_root}"/*.summary.txt 2>/dev/null | head -n 1 || true)"
  [[ -n "${latest}" ]] || fail "clean source release did not write a summary file under ${output_root}"
  printf '%s' "${latest}"
}

record_clean_source_evidence() {
  local clean_summary generated_at version source_branch source_commit archive archive_sha256 source_file_count scan_report scan_findings dirty
  local history_scan_report history_scan_report_path history_scan_report_sha256 history_findings_remaining history_delivery_path credential_rotation_status affected_credential_classes
  clean_summary="$(latest_clean_source_summary)"
  generated_at="$(summary_field_value "${clean_summary}" generated_at_utc)"
  version="$(summary_field_value "${clean_summary}" version)"
  source_branch="$(summary_field_value "${clean_summary}" source_branch)"
  source_commit="$(summary_field_value "${clean_summary}" source_commit)"
  archive="$(summary_field_value "${clean_summary}" archive)"
  archive_sha256="$(summary_field_value "${clean_summary}" archive_sha256)"
  source_file_count="$(summary_field_value "${clean_summary}" source_file_count)"
  scan_report="$(summary_field_value "${clean_summary}" secret_scan_report)"
  scan_findings="$(summary_field_value "${clean_summary}" secret_scan_findings)"
  dirty="$(summary_field_value "${clean_summary}" source_worktree_dirty)"
  history_scan_report="$(summary_field_value "${clean_summary}" history_scan_report)"
  history_scan_report_sha256="$(summary_field_value "${clean_summary}" history_scan_report_sha256)"
  history_findings_remaining="$(summary_field_value "${clean_summary}" history_findings_remaining)"
  history_delivery_path="$(summary_field_value "${clean_summary}" history_delivery_path)"
  credential_rotation_status="$(summary_field_value "${clean_summary}" credential_rotation_status)"
  affected_credential_classes="$(summary_field_value "${clean_summary}" affected_credential_classes)"
  clean_source_worktree_dirty="${dirty}"
  clean_source_history_findings_remaining="${history_findings_remaining}"
  clean_source_history_delivery_path="${history_delivery_path}"
  clean_source_credential_rotation_status="${credential_rotation_status}"
  [[ "${generated_at}" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || fail "clean source summary generated_at_utc must use YYYYMMDDTHHMMSSZ"
  [[ -n "${version}" ]] || fail "clean source summary must record version"
  [[ -n "${source_branch}" ]] || fail "clean source summary must record source_branch"
  [[ "${source_commit}" =~ ^[0-9a-f]{40}$ ]] || fail "clean source summary source_commit must be a 40-character git commit"
  [[ -f "${archive}" ]] || fail "clean source archive referenced by summary does not exist: ${archive}"
  [[ "${archive_sha256}" =~ ^[0-9a-f]{64}$ ]] || fail "clean source summary archive_sha256 must be a SHA-256 digest"
  [[ "${source_file_count}" =~ ^[1-9][0-9]*$ ]] || fail "clean source summary source_file_count must be a positive integer"
  [[ -f "${scan_report}" ]] || fail "clean source secret scan report referenced by summary does not exist: ${scan_report}"
  [[ "${scan_findings}" == "0" ]] || fail "clean source summary must record secret_scan_findings=0"
  [[ -n "${history_scan_report}" ]] || fail "clean source summary must record history_scan_report"
  [[ "${history_findings_remaining}" =~ ^[0-9]+$|^unknown$ ]] || fail "clean source summary must record history_findings_remaining as a non-negative integer or unknown"
  history_scan_report_path="$(repo_path "${history_scan_report}")"
  record "clean_source_summary=${clean_summary}"
  record "clean_source_summary_sha256=$(file_sha256 "${clean_summary}")"
  record "clean_source_generated_at_utc=${generated_at}"
  record "clean_source_version=${version}"
  record "clean_source_source_branch=${source_branch}"
  record "clean_source_source_commit=${source_commit}"
  record "clean_source_archive=${archive}"
  record "clean_source_archive_sha256=${archive_sha256}"
  record "clean_source_source_file_count=${source_file_count}"
  record "clean_source_secret_scan_report=${scan_report}"
  record "clean_source_secret_scan_report_sha256=$(file_sha256 "${scan_report}")"
  record "clean_source_secret_scan_findings=${scan_findings}"
  record "clean_source_worktree_dirty=${dirty}"
  record "clean_source_history_scan_report=${history_scan_report}"
  if [[ -f "${history_scan_report_path}" ]]; then
    [[ "${history_scan_report_sha256}" =~ ^[0-9a-f]{64}$ ]] \
      || fail "clean source summary history_scan_report_sha256 must be a SHA-256 digest"
    [[ "$(file_sha256 "${history_scan_report_path}")" == "${history_scan_report_sha256}" ]] \
      || fail "clean source summary history_scan_report_sha256 mismatch"
    record "clean_source_history_scan_report_sha256=${history_scan_report_sha256}"
  else
    [[ "${history_scan_report_sha256}" == "missing" ]] \
      || fail "clean source summary history_scan_report_sha256 must be missing when the history scan report is absent"
    record "clean_source_history_scan_report_sha256=missing"
  fi
  record "clean_source_history_findings_remaining=${history_findings_remaining}"
  record "clean_source_history_delivery_path=${history_delivery_path}"
  record "clean_source_credential_rotation_status=${credential_rotation_status}"
  record "clean_source_affected_credential_classes=${affected_credential_classes}"
}

record_security_report_evidence() {
  local security_report_dir="${CREST_SECURITY_REPORT_DIR:-reports/security}"
  local security_manifest="${security_report_dir}/security-report-manifest.sha256"
  node scripts/security-report-check.mjs "${security_report_dir}" >/dev/null
  write_digest_manifest "${security_report_dir}" "${security_manifest}" \
    semgrep.json \
    gitleaks.json \
    maven-dependency-tree.txt \
    crest-bom.json \
    pnpm-audit.json \
    frontend-licenses.json \
    license-policy.txt \
    osv-frontend.json \
    osv-maven-sbom.json
  record "security_report_dir=${security_report_dir}"
  record "security_report_manifest=${security_manifest}"
  record "security_report_manifest_sha256=$(file_sha256 "${security_manifest}")"
}

record_container_report_evidence() {
  local container_report_dir="${CREST_CONTAINER_SCAN_REPORT_DIR:-reports/container}"
  local container_manifest="${container_report_dir}/container-report-manifest.sha256"
  local report_file report_name found
  node scripts/container-report-check.mjs "${container_report_dir}" >/dev/null
  mkdir -p "$(dirname "${container_manifest}")"
  : > "${container_manifest}"
  found="false"
  while IFS= read -r report_file; do
    report_name="$(basename "${report_file}")"
    [[ -s "${report_file}" ]] || fail "container scan report is missing or empty: ${report_file}"
    printf '%s  %s\n' "$(file_sha256 "${report_file}")" "${report_name}" >> "${container_manifest}"
    found="true"
  done < <(find "${container_report_dir}" -maxdepth 1 -type f -name 'trivy-*.json' -print | sort)
  [[ "${found}" == "true" ]] || fail "container scan report directory has no trivy JSON reports: ${container_report_dir}"
  record "container_report_dir=${container_report_dir}"
  record "container_report_manifest=${container_manifest}"
  record "container_report_manifest_sha256=$(file_sha256 "${container_manifest}")"
}

record_container_base_image_policy_evidence() {
  local policy_report="$1"
  [[ -s "${policy_report}" ]] || fail "container base image policy report is missing or empty: ${policy_report}"
  record "container_base_image_policy_report=${policy_report}"
  record "container_base_image_policy_report_sha256=$(file_sha256 "${policy_report}")"
}

record_docker_build_base_image_policy_evidence() {
  local policy_report="$1"
  [[ -s "${policy_report}" ]] || fail "Docker build base image policy report is missing or empty: ${policy_report}"
  record "docker_build_base_image_policy_report=${policy_report}"
  record "docker_build_base_image_policy_report_sha256=$(file_sha256 "${policy_report}")"
}

record_production_overlay_evidence() {
  local evidence_dir="${CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR:-${report_dir}/production-overlay-evidence}"
  local summary="${evidence_dir}/summary.txt"
  local manifest_name manifest_path sanitized_resources sanitized_secrets
  node scripts/production-overlay-evidence.mjs "${overlay_dir}" "${evidence_dir}" || return
  [[ -s "${summary}" ]] || fail "production overlay evidence did not write summary: ${summary}"
  manifest_name="$(summary_field_value "${summary}" evidence_manifest)"
  sanitized_resources="$(summary_field_value "${summary}" sanitized_resources)"
  sanitized_secrets="$(summary_field_value "${summary}" sanitized_secrets)"
  [[ -n "${manifest_name}" ]] || fail "production overlay evidence summary missing evidence_manifest"
  [[ -n "${sanitized_resources}" ]] || fail "production overlay evidence summary missing sanitized_resources"
  [[ -n "${sanitized_secrets}" ]] || fail "production overlay evidence summary missing sanitized_secrets"
  manifest_path="${evidence_dir}/${manifest_name}"
  [[ -s "${manifest_path}" ]] || fail "production overlay evidence manifest does not exist: ${manifest_path}"
  [[ -s "${evidence_dir}/${sanitized_resources}" ]] || fail "production overlay sanitized resources do not exist: ${evidence_dir}/${sanitized_resources}"
  [[ -s "${evidence_dir}/${sanitized_secrets}" ]] || fail "production overlay sanitized secrets do not exist: ${evidence_dir}/${sanitized_secrets}"
  record "production_overlay_evidence_dir=${evidence_dir}"
  record "production_overlay_evidence_summary=${summary}"
  record "production_overlay_evidence_summary_sha256=$(file_sha256 "${summary}")"
  record "production_overlay_evidence_manifest=${manifest_path}"
  record "production_overlay_evidence_manifest_sha256=$(file_sha256 "${manifest_path}")"
  record "production_overlay_sanitized_resources=${evidence_dir}/${sanitized_resources}"
  record "production_overlay_sanitized_resources_sha256=$(file_sha256 "${evidence_dir}/${sanitized_resources}")"
  record "production_overlay_sanitized_secrets=${evidence_dir}/${sanitized_secrets}"
  record "production_overlay_sanitized_secrets_sha256=$(file_sha256 "${evidence_dir}/${sanitized_secrets}")"
}

record_github_actions_policy_evidence() {
  local policy_report="$1"
  [[ -s "${policy_report}" ]] || fail "GitHub Actions policy report is missing or empty: ${policy_report}"
  record "github_actions_policy_report=${policy_report}"
  record "github_actions_policy_report_sha256=$(file_sha256 "${policy_report}")"
}

record_ci_toolchain_policy_evidence() {
  local policy_report="$1"
  [[ -s "${policy_report}" ]] || fail "CI toolchain policy report is missing or empty: ${policy_report}"
  record "ci_toolchain_policy_report=${policy_report}"
  record "ci_toolchain_policy_report_sha256=$(file_sha256 "${policy_report}")"
}

run_github_actions_policy_gate() {
  local policy_report="${CREST_GITHUB_ACTIONS_POLICY_REPORT:-${report_dir}/github-actions-policy.txt}"
  CREST_GITHUB_ACTIONS_POLICY_REPORT="${policy_report}" \
    bash scripts/github-actions-policy-check.sh || return
  record_github_actions_policy_evidence "${policy_report}"
}

run_ci_toolchain_policy_gate() {
  local policy_report="${CREST_CI_TOOLCHAIN_POLICY_REPORT:-${report_dir}/ci-toolchain-policy.txt}"
  CREST_CI_TOOLCHAIN_POLICY_REPORT="${policy_report}" \
    bash scripts/ci-toolchain-policy-check.sh || return
  record_ci_toolchain_policy_evidence "${policy_report}"
}

run_container_base_image_policy_gate() {
  local policy_report="${CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT:-${report_dir}/container-base-image-policy.txt}"
  CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS="${base_image_require_digests}" \
    CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${policy_report}" \
    bash scripts/container-base-image-policy-check.sh || return
  record_container_base_image_policy_evidence "${policy_report}"
}

record_history_secret_evidence() {
  local credential_evidence="${external_evidence_dir}/credential-rotation.md"
  local history_scan_report history_scan_report_path
  [[ -f "${credential_evidence}" ]] || fail "missing credential rotation evidence after external evidence check: ${credential_evidence}"
  history_scan_report="$(evidence_field_value "${credential_evidence}" history_scan_report)"
  history_scan_report_path="$(repo_path "${history_scan_report}")"
  [[ -f "${history_scan_report_path}" ]] || fail "credential rotation history scan report does not exist: ${history_scan_report}"
  record "history_secret_evidence=passed"
  record "history_secret_scan_report=${history_scan_report}"
  record "history_secret_scan_report_sha256=$(file_sha256 "${history_scan_report_path}")"
  record "history_secret_findings_remaining=$(evidence_field_value "${credential_evidence}" history_findings_remaining)"
  record "history_secret_affected_credential_classes=$(evidence_field_value "${credential_evidence}" affected_credential_classes)"
  record "history_secret_credential_rotation_status=$(evidence_field_value "${credential_evidence}" credential_rotation_status)"
  record "history_secret_delivery_path=$(evidence_field_value "${credential_evidence}" delivery_path)"
  record "history_secret_rotation_evidence_id=$(evidence_field_value "${credential_evidence}" rotation_evidence_id)"
  record "history_secret_approved_by=$(evidence_field_value "${credential_evidence}" approved_by)"
  record "history_secret_approval_date=$(evidence_field_value "${credential_evidence}" approval_date)"
}

run_quality_gate() {
  env \
    -u CREST_READINESS_CREATE_CLEAN_SOURCE \
    -u CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE \
    -u CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION \
    -u CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH \
    -u CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS \
    -u CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES \
    -u CREST_CLEAN_SOURCE_HISTORY_FINDINGS_REMAINING \
    -u CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT \
    -u CREST_CLEAN_SOURCE_OUTPUT_DIR \
    -u CREST_CLEAN_SOURCE_NAME \
    -u CREST_CLEAN_SOURCE_GITLEAKS_REPORT \
    -u CREST_READINESS_DOCKER_BUILDKIT \
    bash scripts/quality-check.sh
}

production_release_blockers=()
production_release_warnings=()
clean_source_exported="false"
clean_source_worktree_dirty=""
clean_source_history_findings_remaining=""
clean_source_history_delivery_path=""
clean_source_credential_rotation_status=""
production_overlay_checked="false"
live_runtime_checked="false"
external_evidence_checked="false"
static_gate_failed="false"
final_status_recorded="false"
first_gate_failure_status=0
last_gate_failed="false"

add_release_blocker() {
  production_release_blockers+=("$1")
}

add_release_warning() {
  production_release_warnings+=("$1")
}

record_readiness_action_plan() {
  local action_plan="${CREST_READINESS_ACTION_PLAN:-${report_dir}/production-readiness-action-plan.txt}"
  bash scripts/production-readiness-action-plan.sh "${summary_file}" "${action_plan}" || return
  record "readiness_action_plan=${action_plan}"
  record "readiness_action_plan_sha256=$(file_sha256 "${action_plan}")"
}

record_final_status() {
  if [[ "${final_status_recorded}" == "true" ]]; then
    return
  fi
  final_status_recorded="true"

  local readiness_status
  local skipped_static_gate="false"
  for gate in "${skip_quality}" "${skip_security}" "${skip_docker}" "${skip_container_scan}" "${skip_kind}"; do
    if [[ "${gate}" == "true" ]]; then
      skipped_static_gate="true"
      break
    fi
  done

  if [[ "${static_gate_failed}" == "true" ]]; then
    readiness_status="failed"
  elif [[ "${require_go_no_go}" == "true" ]]; then
    readiness_status="go-no-go-passed"
  elif [[ "${skipped_static_gate}" == "true" ]]; then
    readiness_status="partial-check-passed"
    add_release_blocker "one or more static gates were skipped"
  else
    readiness_status="production-candidate-passed"
  fi

  if [[ "${require_go_no_go}" != "true" ]]; then
    if [[ "${clean_source_exported}" != "true" ]]; then
      add_release_blocker "clean source release export was not generated"
    elif [[ "${clean_source_history_findings_remaining}" == "unknown" ]]; then
      add_release_blocker "clean source history finding count was not verified"
    elif [[ "${clean_source_history_findings_remaining}" =~ ^[1-9][0-9]*$ \
      && "${clean_source_history_delivery_path}" != "clean-history" \
      && "${clean_source_credential_rotation_status}" != "rotated-before-delivery" ]]; then
      add_release_blocker "clean source credential rotation evidence was not recorded"
    fi
    if [[ "${require_clean_release_source}" != "true" ]]; then
      add_release_blocker "clean source release was not required to come from a clean git worktree"
    fi
    if [[ "${production_overlay_checked}" != "true" ]]; then
      add_release_blocker "strict production overlay was not rendered or checked"
    fi
    if [[ "${live_runtime_checked}" != "true" ]]; then
      add_release_blocker "live preprod/production runtime check was not run"
    fi
    if [[ "${external_evidence_checked}" != "true" ]]; then
      add_release_blocker "external production evidence was not checked"
    fi
    if [[ "${require_history}" != "true" ]]; then
      add_release_warning "git history secret audit was not enforced; use clean-source delivery plus credential rotation evidence if history is not clean"
    fi
  fi

  record "readiness_status=${readiness_status}"
  if [[ "${readiness_status}" == "go-no-go-passed" ]]; then
    record "production_release_status=ready-for-business-approval"
  else
    record "production_release_status=not-ready"
  fi

  if [[ -n "${production_release_blockers[*]-}" ]]; then
    for blocker in "${production_release_blockers[@]}"; do
      record "production_release_blocker=${blocker}"
    done
  fi
  if [[ -n "${production_release_warnings[*]-}" ]]; then
    for warning in "${production_release_warnings[@]}"; do
      record "production_release_warning=${warning}"
    done
  fi
  if [[ "${readiness_status}" != "go-no-go-passed" ]]; then
    record_readiness_action_plan
  fi

  case "${readiness_status}" in
    go-no-go-passed)
      info "Go/No-Go readiness gates passed; summary written to ${summary_file}"
      ;;
    production-candidate-passed)
      info "production candidate gates passed; Go/No-Go evidence is incomplete; summary written to ${summary_file}"
      ;;
    failed)
      info "readiness gate failed; production release is not ready; summary written to ${summary_file}"
      ;;
    *)
      info "partial readiness checks passed; production release is not ready; summary written to ${summary_file}"
      ;;
  esac
}

record_failed_gate() {
  local gate_key="$1"
  local gate_name="$2"
  local status="$3"
  local gate_log="$4"

  static_gate_failed="true"
  last_gate_failed="true"
  record "${gate_key}: failed"
  record "${gate_key}_exit_code=${status}"
  record "${gate_key}_log_sha256=$(file_sha256 "${gate_log}")"
  add_release_blocker "${gate_name} failed"
  if [[ "${first_gate_failure_status}" -eq 0 ]]; then
    first_gate_failure_status="${status}"
  fi
}

record_failed_gate_and_exit() {
  record_failed_gate "$@"
  record_final_status
  exit "${first_gate_failure_status}"
}

run_gate() {
  local gate_key="$1"
  local gate_name="$2"
  local gate_log="${gate_log_dir}/${gate_key}.log"
  shift 2

  : > "${gate_log}"
  last_gate_failed="false"
  record "${gate_key}_log=${gate_log}"
  info "running ${gate_name}"
  if "$@" > >(tee -a "${gate_log}") 2> >(tee -a "${gate_log}" >&2); then
    record "${gate_key}_log_sha256=$(file_sha256 "${gate_log}")"
    record "${gate_key}: passed"
    info "${gate_name} passed"
  else
    local status=$?
    if [[ "${continue_on_failure}" == "true" ]]; then
      record_failed_gate "${gate_key}" "${gate_name}" "${status}" "${gate_log}"
      info "${gate_name} failed; continuing to collect remaining readiness evidence"
    else
      record_failed_gate_and_exit "${gate_key}" "${gate_name}" "${status}" "${gate_log}"
    fi
  fi
}

run_clean_source_release() {
  env "CREST_CLEAN_SOURCE_REQUIRE_CLEAN_WORKTREE=${require_clean_release_source}" \
    "CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=${clean_source_require_credential_rotation}" \
    "CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT=${clean_source_history_scan_report}" \
    bash scripts/create-clean-source-release.sh || return
  clean_source_exported="true"
  record_clean_source_evidence
}

run_docker_environment_preflight() {
  local cleanup_report="${CREST_DOCKER_CLEANUP_REPORT:-${report_dir}/docker-cleanup-plan.txt}"
  local environment_report="${CREST_DOCKER_ENVIRONMENT_REPORT:-${report_dir}/docker-environment-report.txt}"
  local status=0
  rm -f "${cleanup_report}"
  rm -f "${environment_report}"
  CREST_DOCKER_CLEANUP_REPORT="${cleanup_report}" \
    CREST_DOCKER_ENVIRONMENT_REPORT="${environment_report}" \
    bash scripts/docker-environment-check.sh || status=$?
  if [[ -s "${environment_report}" ]]; then
    record "docker_environment_report=${environment_report}"
    record "docker_environment_report_sha256=$(file_sha256 "${environment_report}")"
  fi
  if [[ -s "${cleanup_report}" ]]; then
    record "docker_cleanup_plan=${cleanup_report}"
    record "docker_cleanup_plan_sha256=$(file_sha256 "${cleanup_report}")"
  fi
  return "${status}"
}

run_security_gate() {
  bash scripts/security-scan.sh || return
  record_security_report_evidence
}

run_container_scan_gate() {
  CREST_TRIVY_SKIP_JAVA_ARTIFACTS="${CREST_TRIVY_SKIP_JAVA_ARTIFACTS:-true}" \
    CREST_TRIVY_SKIP_JAVA_DB_UPDATE="${CREST_TRIVY_SKIP_JAVA_DB_UPDATE:-true}" \
    bash scripts/container-image-scan.sh || return
  record_container_report_evidence
}

run_docker_build_gate() {
  local policy_report="${CREST_DOCKER_BUILD_BASE_IMAGE_POLICY_REPORT:-${report_dir}/docker-build-base-image-policy.txt}"
  if [[ -n "${docker_buildkit}" ]]; then
    DOCKER_BUILDKIT="${docker_buildkit}" \
    CREST_DOCKER_SKIP_ENV_CHECK=true \
    CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS="${base_image_require_digests}" \
    CREST_DOCKER_BUILD_BASE_IMAGE_POLICY_REPORT="${policy_report}" \
      bash scripts/docker-build-check.sh || return
  else
    CREST_DOCKER_SKIP_ENV_CHECK=true \
    CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS="${base_image_require_digests}" \
    CREST_DOCKER_BUILD_BASE_IMAGE_POLICY_REPORT="${policy_report}" \
      bash scripts/docker-build-check.sh || return
  fi
  record_docker_build_base_image_policy_evidence "${policy_report}"
}

run_history_secret_audit() {
  local history_report_dir="${CREST_SECURITY_REPORT_DIR:-reports/security}"
  local history_summary_requested="${CREST_HISTORY_SECRET_SUMMARY:-${history_report_dir}/gitleaks-history-summary.txt}"
  local history_summary history_summary_display history_report history_report_display
  local status=0
  history_summary="$(assert_safe_history_summary_file "${history_summary_requested}")"
  history_summary_display="$(display_repo_path "${history_summary}")"
  rm -f -- "${history_summary}"
  CREST_HISTORY_SECRET_SUMMARY="${history_summary}" bash scripts/history-secret-audit.sh || status=$?
  if [[ -s "${history_summary}" ]]; then
    history_report="$(summary_field_value "${history_summary}" report)"
    record "history_secret_audit_summary=${history_summary_display}"
    record "history_secret_audit_summary_sha256=$(file_sha256 "${history_summary}")"
    if [[ -n "${history_report}" && -s "${history_report}" ]]; then
      history_report_display="$(display_repo_path "${history_report}")"
      record "history_secret_audit_report=${history_report_display}"
      record "history_secret_audit_report_sha256=$(file_sha256 "${history_report}")"
    fi
    record "history_secret_audit_status=$(summary_field_value "${history_summary}" status)"
    record "history_secret_audit_findings=$(summary_field_value "${history_summary}" findings)"
    record "history_secret_audit_commits=$(summary_field_value "${history_summary}" commits)"
    record "history_secret_audit_remediation_required=$(summary_field_value "${history_summary}" remediation_required)"
    record "history_secret_audit_delivery_options=$(summary_field_value "${history_summary}" delivery_options)"
  fi
  return "${status}"
}

run_history_secret_audit_for_clean_source() {
  CREST_HISTORY_SECRET_AUDIT_ALLOW_FINDINGS=true run_history_secret_audit
}

run_production_overlay_render() {
  bash scripts/render-production-overlay.sh || return
  overlay_dir="${CREST_PRODUCTION_OVERLAY_DIR:-.local/production-overlay}"
  production_overlay_checked="true"
  record "production-overlay-path=${overlay_dir}"
  record_production_overlay_evidence
}

run_production_overlay_check() {
  bash scripts/production-config-check.sh "${overlay_dir}" || return
  production_overlay_checked="true"
  record "production-overlay-path=${overlay_dir}"
  record_production_overlay_evidence
}

run_production_evidence_bundle() {
  local namespace="${CREST_K8S_NAMESPACE:-crest}"
  local evidence_dir="${CREST_EVIDENCE_DIR:-${report_dir}/evidence-${namespace}-$(date -u +%Y%m%dT%H%M%SZ)}"
  local evidence_summary="${evidence_dir}/summary.txt"
  local evidence_manifest_name evidence_manifest runtime_check runtime_check_require_ingress_address
  CREST_EVIDENCE_DIR="${evidence_dir}" \
    CREST_EVIDENCE_REQUIRE_INGRESS_ADDRESS="${evidence_require_ingress_address}" \
    bash scripts/production-evidence-bundle.sh || return
  [[ -s "${evidence_summary}" ]] || fail "production evidence bundle did not write summary: ${evidence_summary}"
  evidence_manifest_name="$(summary_field_value "${evidence_summary}" evidence_manifest)"
  runtime_check="$(summary_field_value "${evidence_summary}" runtime_check)"
  runtime_check_require_ingress_address="$(summary_field_value "${evidence_summary}" runtime_check_require_ingress_address)"
  [[ -n "${evidence_manifest_name}" ]] || fail "production evidence summary missing evidence_manifest"
  [[ "${runtime_check}" == "passed" ]] || fail "production evidence summary must record runtime_check=passed"
  [[ "${runtime_check_require_ingress_address}" == "true" ]] \
    || fail "production evidence summary must record runtime_check_require_ingress_address=true"
  evidence_manifest="${evidence_dir}/${evidence_manifest_name}"
  [[ -s "${evidence_manifest}" ]] || fail "production evidence manifest does not exist: ${evidence_manifest}"
  live_runtime_checked="true"
  record "production_evidence_dir=${evidence_dir}"
  record "production_evidence_summary=${evidence_summary}"
  record "production_evidence_summary_sha256=$(file_sha256 "${evidence_summary}")"
  record "production_evidence_manifest=${evidence_manifest}"
  record "production_evidence_manifest_sha256=$(file_sha256 "${evidence_manifest}")"
  record "production_evidence_runtime_check=${runtime_check}"
  record "production_evidence_require_ingress_address=${runtime_check_require_ingress_address}"
}

run_live_runtime_check() {
  node scripts/production-runtime-check.mjs || return
  live_runtime_checked="true"
}

run_external_production_evidence() {
  env "CREST_EXTERNAL_EVIDENCE_SUMMARY=${external_evidence_summary}" \
    bash scripts/production-external-evidence-check.sh || return
  external_evidence_checked="true"
  [[ -s "${external_evidence_summary}" ]] || fail "external production evidence check did not write summary: ${external_evidence_summary}"
  record "external_evidence_summary_sha256=$(file_sha256 "${external_evidence_summary}")"
  record_history_secret_evidence
}

skip_quality="${CREST_READINESS_SKIP_QUALITY:-false}"
skip_security="${CREST_READINESS_SKIP_SECURITY:-false}"
skip_docker="${CREST_READINESS_SKIP_DOCKER:-false}"
skip_container_scan="${CREST_READINESS_SKIP_CONTAINER_SCAN:-false}"
skip_kind="${CREST_READINESS_SKIP_KIND:-false}"
require_history="${CREST_READINESS_REQUIRE_CLEAN_HISTORY:-false}"
create_clean_source="${CREST_READINESS_CREATE_CLEAN_SOURCE:-false}"
require_clean_release_source="${CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE:-false}"
render_overlay="${CREST_READINESS_RENDER_OVERLAY:-false}"
overlay_dir="${CREST_PRODUCTION_OVERLAY_DIR:-}"
live_check="${CREST_READINESS_LIVE_CHECK:-false}"
collect_evidence="${CREST_READINESS_COLLECT_EVIDENCE:-false}"
require_go_no_go="${CREST_READINESS_REQUIRE_GO_NO_GO:-false}"
external_evidence_dir="${CREST_EXTERNAL_EVIDENCE_DIR:-}"
external_evidence_summary="${CREST_EXTERNAL_EVIDENCE_SUMMARY:-reports/readiness/external-evidence-summary.txt}"
check_external_evidence="${CREST_READINESS_CHECK_EXTERNAL_EVIDENCE:-false}"
base_image_require_digests="${CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS:-true}"
clean_source_require_credential_rotation="${CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION:-false}"
evidence_require_ingress_address="${CREST_EVIDENCE_REQUIRE_INGRESS_ADDRESS:-${CREST_REQUIRE_INGRESS_ADDRESS:-true}}"
docker_buildkit="${CREST_READINESS_DOCKER_BUILDKIT:-}"
default_clean_source_history_scan_report="${CREST_SECURITY_REPORT_DIR:-reports/security}/gitleaks-history.json"
clean_source_history_scan_report="${CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT:-${default_clean_source_history_scan_report}}"
continue_on_failure="${CREST_READINESS_CONTINUE_ON_FAILURE:-false}"

case "${docker_buildkit}" in
  ""|0|1)
    ;;
  *)
    fail "CREST_READINESS_DOCKER_BUILDKIT must be empty, 0 or 1"
    ;;
esac

case "${evidence_require_ingress_address}" in
  true|false)
    ;;
  *)
    fail "CREST_EVIDENCE_REQUIRE_INGRESS_ADDRESS must be true or false"
    ;;
esac

if [[ "${require_go_no_go}" == "true" ]]; then
  for skipped_gate in \
    "CREST_READINESS_SKIP_QUALITY:${skip_quality}:quality" \
    "CREST_READINESS_SKIP_SECURITY:${skip_security}:security" \
    "CREST_READINESS_SKIP_DOCKER:${skip_docker}:docker-build" \
    "CREST_READINESS_SKIP_CONTAINER_SCAN:${skip_container_scan}:container-scan" \
    "CREST_READINESS_SKIP_KIND:${skip_kind}:kind-smoke"; do
    IFS=":" read -r env_name env_value gate_name <<< "${skipped_gate}"
    [[ "${env_value}" != "true" ]] || fail "Go/No-Go mode requires ${gate_name}; unset ${env_name}"
  done
  [[ "${create_clean_source}" == "true" ]] || fail "Go/No-Go mode requires CREST_READINESS_CREATE_CLEAN_SOURCE=true"
  [[ "${require_clean_release_source}" == "true" ]] || fail "Go/No-Go mode requires CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true"
  [[ "${render_overlay}" == "true" || -n "${overlay_dir}" ]] || fail "Go/No-Go mode requires CREST_READINESS_RENDER_OVERLAY=true or CREST_PRODUCTION_OVERLAY_DIR"
  [[ "${collect_evidence}" == "true" ]] || fail "Go/No-Go mode requires CREST_READINESS_COLLECT_EVIDENCE=true"
  [[ "${CREST_EVIDENCE_RUN_RUNTIME_CHECK:-true}" != "false" ]] || fail "Go/No-Go mode requires the evidence bundle to run production-runtime-check"
  [[ "${evidence_require_ingress_address}" == "true" ]] || fail "Go/No-Go mode requires CREST_EVIDENCE_REQUIRE_INGRESS_ADDRESS=true"
  [[ "${base_image_require_digests}" == "true" ]] || fail "Go/No-Go mode requires CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS=true"
  [[ -n "${external_evidence_dir}" ]] || fail "Go/No-Go mode requires CREST_EXTERNAL_EVIDENCE_DIR"
  check_external_evidence="true"
fi

if [[ "${require_go_no_go}" == "true" || "${require_clean_release_source}" == "true" ]]; then
  clean_source_require_credential_rotation="true"
fi

record "Crest Core enterprise readiness check"
record "skip_quality=${skip_quality}"
record "skip_security=${skip_security}"
record "skip_docker=${skip_docker}"
record "skip_container_scan=${skip_container_scan}"
record "skip_kind=${skip_kind}"
record "require_base_image_digests=${base_image_require_digests}"
record "docker_buildkit=${docker_buildkit:-default}"
record "require_clean_history=${require_history}"
record "create_clean_source=${create_clean_source}"
record "require_clean_release_source=${require_clean_release_source}"
record "clean_source_require_credential_rotation=${clean_source_require_credential_rotation}"
record "render_overlay=${render_overlay}"
record "live_check=${live_check}"
record "collect_evidence=${collect_evidence}"
record "require_go_no_go=${require_go_no_go}"
record "continue_on_failure=${continue_on_failure}"
record "check_external_evidence=${check_external_evidence}"
record "evidence_require_ingress_address=${evidence_require_ingress_address}"
record "external_evidence_dir=${external_evidence_dir:-}"
record "external_evidence_summary=${external_evidence_summary}"

run_gate "github-actions-policy" "GitHub Actions immutable reference policy gate" run_github_actions_policy_gate
run_gate "ci-toolchain-policy" "CI toolchain centralization policy gate" run_ci_toolchain_policy_gate

if [[ "${skip_quality}" != "true" ]]; then
  run_gate "quality" "quality gate" run_quality_gate
else
  record "quality: skipped"
fi

if [[ "${skip_security}" != "true" ]]; then
  run_gate "security" "SAST/SCA gate" run_security_gate
else
  record "security: skipped"
fi

docker_prerequisite_failed="false"
if [[ "${skip_docker}" != "true" ]]; then
  run_gate "container-base-image-policy" "container base image policy gate" run_container_base_image_policy_gate
  if [[ "${last_gate_failed}" == "true" ]]; then
    docker_prerequisite_failed="true"
  fi
  run_gate "docker-environment" "Docker environment preflight" run_docker_environment_preflight
  if [[ "${last_gate_failed}" == "true" ]]; then
    docker_prerequisite_failed="true"
  fi
  if [[ "${docker_prerequisite_failed}" == "true" ]]; then
    static_gate_failed="true"
    record "docker-build: skipped-after-failed-prerequisite"
    add_release_blocker "Docker image build gate skipped after failed Docker prerequisite"
  else
    run_gate "docker-build" "Docker image build gate" run_docker_build_gate
  fi
else
  record "container-base-image-policy: skipped"
  record "docker-environment: skipped"
  record "docker-build: skipped"
fi

if [[ "${skip_container_scan}" != "true" ]]; then
  if [[ "${docker_prerequisite_failed}" == "true" ]]; then
    static_gate_failed="true"
    record "container-scan: skipped-after-failed-prerequisite"
    add_release_blocker "container image CVE gate skipped after failed Docker prerequisite"
  else
    run_gate "container-scan" "container image CVE gate" run_container_scan_gate
  fi
else
  record "container-scan: skipped"
fi

if [[ "${skip_kind}" != "true" ]]; then
  run_gate "kind-smoke" "kind server-side dry-run" bash scripts/kind-smoke-test.sh
else
  record "kind-smoke: skipped"
fi

if [[ "${require_history}" == "true" ]]; then
  run_gate "history-secret-audit" "git history secret audit" run_history_secret_audit
elif [[ "${create_clean_source}" == "true" && -z "${CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT:-}" ]]; then
  info "running fresh non-blocking git history secret audit to support clean-source evidence"
  run_gate "history-secret-audit" "git history secret audit evidence" run_history_secret_audit_for_clean_source
elif [[ "${create_clean_source}" == "true" ]]; then
  info "skipping git history secret audit; using CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT"
  record "history-secret-audit: skipped"
else
  info "skipping git history secret audit; set CREST_READINESS_REQUIRE_CLEAN_HISTORY=true to enforce"
  record "history-secret-audit: skipped"
fi

if [[ "${create_clean_source}" == "true" ]]; then
  run_gate "clean-source-release" "clean source release export" run_clean_source_release
else
  info "skipping clean source release export; set CREST_READINESS_CREATE_CLEAN_SOURCE=true to generate a no-history source artifact"
  record "clean-source-release: skipped"
fi

if [[ "${render_overlay}" == "true" ]]; then
  run_gate "production-overlay-render" "production overlay render and strict config gate" run_production_overlay_render
elif [[ -n "${overlay_dir}" && -d "${overlay_dir}" ]]; then
  run_gate "production-overlay-check" "production overlay strict config gate" run_production_overlay_check
else
  info "skipping production overlay strict config gate; set CREST_READINESS_RENDER_OVERLAY=true or CREST_PRODUCTION_OVERLAY_DIR"
  record "production-overlay-check: skipped"
fi

if [[ "${collect_evidence}" == "true" ]]; then
  run_gate "production-evidence-bundle" "production evidence bundle" run_production_evidence_bundle
elif [[ "${live_check}" == "true" ]]; then
  run_gate "live-runtime-check" "live production runtime gate" run_live_runtime_check
else
  info "skipping live runtime gate; set CREST_READINESS_LIVE_CHECK=true or CREST_READINESS_COLLECT_EVIDENCE=true after applying to preprod or production"
  record "live-runtime-check: skipped"
fi

if [[ "${check_external_evidence}" == "true" ]]; then
  run_gate "external-production-evidence" "external production evidence gate" run_external_production_evidence
else
  info "skipping external production evidence gate; set CREST_READINESS_CHECK_EXTERNAL_EVIDENCE=true and CREST_EXTERNAL_EVIDENCE_DIR"
  record "external-production-evidence: skipped"
fi

record_final_status

if [[ "${first_gate_failure_status}" -ne 0 ]]; then
  exit "${first_gate_failure_status}"
fi

if [[ "${require_go_no_go}" == "true" ]]; then
  bash scripts/production-go-no-go-summary-check.sh "${summary_file}"
fi
