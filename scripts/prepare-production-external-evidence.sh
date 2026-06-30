#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "prepare-production-external-evidence: $*" >&2
  exit 1
}

info() {
  echo "prepare-production-external-evidence: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${repo_root}"

require_cmd date
require_cmd node

evidence_dir="${CREST_EXTERNAL_EVIDENCE_DIR:-reports/readiness/external-evidence}"
readiness_summary="${CREST_READINESS_SUMMARY:-reports/readiness/enterprise-readiness-summary.txt}"
redis_namespace_report="${CREST_REDIS_NAMESPACE_REPORT:-reports/readiness/redis-namespace-check.txt}"
evidence_date="${CREST_EVIDENCE_DATE:-$(date -u +%F)}"
environment="${CREST_EVIDENCE_ENVIRONMENT:-CHANGE_ME_ENVIRONMENT}"
owner="${CREST_EVIDENCE_OWNER:-CHANGE_ME_OWNER}"
artifact_reference="${CREST_EVIDENCE_ARTIFACT_REFERENCE:-CHANGE_ME_TICKET_OR_REPORT}"
approval_date="${CREST_CREDENTIAL_ROTATION_APPROVAL_DATE:-${evidence_date}}"
approved_by="${CREST_CREDENTIAL_ROTATION_APPROVED_BY:-CHANGE_ME_APPROVER}"
rotation_evidence_id="${CREST_CREDENTIAL_ROTATION_EVIDENCE_ID:-CHANGE_ME_ROTATION_TICKET}"
affected_credential_classes="${CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES:-CHANGE_ME_AFFECTED_CREDENTIAL_CLASSES}"

required_files=(
  ob-oracle-init.md
  ob-oracle-backup.md
  ob-oracle-restore.md
  redis-cluster.md
  redis-failover.md
  credential-rotation.md
  tls-ingress.md
  storage-rwx.md
  business-smoke.md
  failure-drill.md
)

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
  local path="$1"
  local logical parent base ancestor suffix ancestor_real
  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" || -L "${logical}" ]]; then
    [[ ! -L "${logical}" ]] || fail "CREST_EXTERNAL_EVIDENCE_DIR must not be a symlink: ${path}"
    [[ -d "${logical}" ]] || fail "CREST_EXTERNAL_EVIDENCE_DIR must be a directory path: ${path}"
    (cd "${logical}" && pwd -P)
    return
  fi
  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  ancestor="${parent}"
  while [[ ! -e "${ancestor}" && "${ancestor}" != "/" ]]; do
    ancestor="$(dirname "${ancestor}")"
  done
  [[ -d "${ancestor}" ]] || fail "CREST_EXTERNAL_EVIDENCE_DIR parent path is not a directory: ${path}"
  ancestor_real="$(cd "${ancestor}" && pwd -P)"
  suffix="${parent#"${ancestor}"}"
  printf '%s%s/%s' "${ancestor_real}" "${suffix}" "${base}"
}

assert_safe_evidence_dir() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_EXTERNAL_EVIDENCE_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_EXTERNAL_EVIDENCE_DIR is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_dir_path "${path}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/reports"|"${repo_root}/reports/readiness"|\
    "${repo_root}/.local"|"${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_EXTERNAL_EVIDENCE_DIR is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_EXTERNAL_EVIDENCE_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${normalized}"
}

summary_field_value() {
  local path="$1"
  local field="$2"
  [[ -f "${path}" ]] || return 0
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${path}"
}

report_field_value() {
  local path="$1"
  local field="$2"
  [[ -f "${path}" ]] || return 0
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${path}"
}

json_array_count() {
  local path="$1"
  node -e '
    const fs = require("fs");
    const path = process.argv[1];
    const doc = JSON.parse(fs.readFileSync(path, "utf8") || "[]");
    if (!Array.isArray(doc)) {
      process.exit(2);
    }
    console.log(doc.length);
  ' "${path}"
}

hash_tag_from_prefix() {
  local prefix="$1"
  if [[ "${prefix}" =~ ^\{([^}]+)\}: ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
  fi
}

write_common() {
  local file="$1"
  local path="${evidence_dir}/${file}"
  cat > "${path}" <<EOF
status: passed
environment: ${environment}
evidence_date: ${evidence_date}
owner: ${owner}
artifact_reference: ${artifact_reference}
notes: CHANGE_ME_SANITIZED_REVIEW_SUMMARY_FOR_${file}
EOF
}

append_credential_rotation() {
  local file="${evidence_dir}/credential-rotation.md"
  cat >> "${file}" <<EOF
history_scan_report: ${history_report}
history_scan_report_sha256: ${history_report_sha256}
history_findings_remaining: ${history_findings_remaining}
affected_credential_classes: ${affected_credential_classes}
credential_rotation_status: ${credential_rotation_status}
delivery_path: ${delivery_path}
rotation_evidence_id: ${rotation_evidence_id}
approved_by: ${approved_by}
approval_date: ${approval_date}
EOF
}

append_redis_cluster() {
  local file="${evidence_dir}/redis-cluster.md"
  cat >> "${file}" <<EOF
redis_key_prefix: ${redis_key_prefix}
redis_hash_tag: ${redis_hash_tag}
redis_acl_user: ${redis_acl_user}
redis_namespace_check_report: ${redis_namespace_report}
redis_namespace_check_report_sha256: ${redis_namespace_report_sha256}
EOF
}

append_tls_ingress() {
  cat >> "${evidence_dir}/tls-ingress.md" <<EOF
ingress_host: ${CREST_PRODUCTION_HOST:-CHANGE_ME_INGRESS_HOST}
tls_expiry_monitor: ${CREST_TLS_EXPIRY_MONITOR:-CHANGE_ME_TLS_EXPIRY_MONITOR}
EOF
}

append_storage_rwx() {
  cat >> "${evidence_dir}/storage-rwx.md" <<EOF
pvc_name: crest-data
access_mode: ReadWriteMany
EOF
}

append_business_smoke() {
  cat >> "${evidence_dir}/business-smoke.md" <<EOF
smoke_scope: login,dashboard,dataset-preview,export,async-task,websocket
EOF
}

append_failure_drill() {
  cat >> "${evidence_dir}/failure-drill.md" <<EOF
drill_scope: rollout-restart,api-pod-delete,worker-pod-delete
EOF
}

write_readme() {
  cat > "${evidence_dir}/README.md" <<EOF
# Crest Core external production evidence draft

This directory was generated by scripts/prepare-production-external-evidence.sh.
It is intentionally not a passing evidence package yet. Replace every CHANGE_ME
field with sanitized review records from the real pre-production or production
environment, then run:

CREST_EXTERNAL_EVIDENCE_DIR=${evidence_dir} bash scripts/production-external-evidence-check.sh

Do not commit this directory if it contains customer names, internal ticket URLs,
certificate metadata, backup details, runtime evidence, or other private data.
EOF
}

evidence_dir="$(assert_safe_evidence_dir "${evidence_dir}")"

history_report="${CREST_HISTORY_SECRET_REPORT:-}"
if [[ -z "${history_report}" ]]; then
  history_report="$(summary_field_value "${readiness_summary}" clean_source_history_scan_report)"
fi
if [[ -z "${history_report}" || "${history_report}" == "missing" ]]; then
  history_report="$(summary_field_value "${readiness_summary}" history_secret_audit_report)"
fi
history_report="${history_report:-reports/security/gitleaks-history.json}"
history_report_path="$(repo_path "${history_report}")"
if [[ -f "${history_report_path}" ]]; then
  history_report_sha256="$(file_sha256 "${history_report_path}")"
  history_findings_remaining="$(json_array_count "${history_report_path}")" \
    || fail "history secret scan report must be a JSON array: ${history_report}"
else
  history_report_sha256="CHANGE_ME_HISTORY_SCAN_SHA256"
  history_findings_remaining="CHANGE_ME_HISTORY_FINDINGS_REMAINING"
fi

if [[ "${history_findings_remaining}" == "0" ]]; then
  delivery_path="${CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH:-clean-history}"
  credential_rotation_status="${CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS:-not-applicable-clean-history}"
else
  delivery_path="${CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH:-clean-source}"
  credential_rotation_status="${CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS:-CHANGE_ME_ROTATED_BEFORE_DELIVERY}"
fi

redis_key_prefix="$(report_field_value "${redis_namespace_report}" redis_key_prefix)"
redis_key_prefix="${redis_key_prefix:-${CREST_REDIS_KEY_PREFIX:-CHANGE_ME_REDIS_KEY_PREFIX}}"
redis_hash_tag="$(report_field_value "${redis_namespace_report}" redis_hash_tag)"
redis_hash_tag="${redis_hash_tag:-$(hash_tag_from_prefix "${redis_key_prefix}")}"
redis_hash_tag="${redis_hash_tag:-CHANGE_ME_REDIS_HASH_TAG}"
redis_acl_user="$(report_field_value "${redis_namespace_report}" redis_acl_user)"
redis_acl_user="${redis_acl_user:-${CREST_REDIS_USERNAME:-CHANGE_ME_REDIS_ACL_USER}}"
if [[ -f "${redis_namespace_report}" ]]; then
  redis_namespace_report_sha256="$(file_sha256 "${redis_namespace_report}")"
else
  redis_namespace_report_sha256="CHANGE_ME_REDIS_NAMESPACE_REPORT_SHA256"
fi

rm -rf "${evidence_dir}"
mkdir -p "${evidence_dir}"

for file in "${required_files[@]}"; do
  write_common "${file}"
done

append_redis_cluster
append_credential_rotation
append_tls_ingress
append_storage_rwx
append_business_smoke
append_failure_drill
write_readme

info "wrote draft external production evidence files to ${evidence_dir}"
info "replace CHANGE_ME fields with sanitized real evidence, then run production-external-evidence-check.sh"
