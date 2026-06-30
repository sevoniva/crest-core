#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "production-external-evidence-check: $*" >&2
  exit 1
}

info() {
  echo "production-external-evidence-check: $*"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${repo_root}"

evidence_dir="${CREST_EXTERNAL_EVIDENCE_DIR:-reports/readiness/external-evidence}"
summary_file="${CREST_EXTERNAL_EVIDENCE_SUMMARY:-reports/readiness/external-evidence-summary.txt}"
evidence_today="${CREST_EVIDENCE_TODAY:-$(date -u +%F)}"

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
  local label="$2"
  local logical parent base ancestor suffix ancestor_real
  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" || -L "${logical}" ]]; then
    [[ ! -L "${logical}" ]] || fail "${label} must not be a symlink: ${path}"
    [[ -d "${logical}" ]] || fail "${label} must be a directory path: ${path}"
    (cd "${logical}" && pwd -P)
    return
  fi
  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  ancestor="${parent}"
  while [[ ! -e "${ancestor}" && "${ancestor}" != "/" ]]; do
    ancestor="$(dirname "${ancestor}")"
  done
  [[ -d "${ancestor}" ]] || fail "${label} parent path is not a directory: ${path}"
  ancestor_real="$(cd "${ancestor}" && pwd -P)"
  suffix="${parent#"${ancestor}"}"
  printf '%s%s/%s' "${ancestor_real}" "${suffix}" "${base}"
}

normalize_file_path() {
  local path="$1"
  local label="$2"
  local logical parent base normalized_parent
  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" || -L "${logical}" ]]; then
    [[ ! -L "${logical}" ]] || fail "${label} must not be a symlink: ${path}"
    [[ ! -d "${logical}" ]] || fail "${label} must be a file path: ${path}"
    (cd "$(dirname "${logical}")" && printf '%s/%s' "$(pwd -P)" "$(basename "${logical}")")
    return
  fi
  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  normalized_parent="$(normalize_dir_path "${parent}" "${label}")"
  printf '%s/%s' "${normalized_parent}" "${base}"
}

assert_safe_evidence_dir() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_EXTERNAL_EVIDENCE_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_EXTERNAL_EVIDENCE_DIR is too broad to read evidence from: ${path}"
      ;;
  esac
  normalized="$(normalize_dir_path "${path}" CREST_EXTERNAL_EVIDENCE_DIR)"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/.local"|"${repo_root}/reports"|"${repo_root}/reports/readiness"|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_EXTERNAL_EVIDENCE_DIR is too broad to read evidence from: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_EXTERNAL_EVIDENCE_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${normalized}"
}

assert_safe_summary_file() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_EXTERNAL_EVIDENCE_SUMMARY must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_EXTERNAL_EVIDENCE_SUMMARY is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_file_path "${path}" CREST_EXTERNAL_EVIDENCE_SUMMARY)"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/.local"|"${repo_root}/reports"|"${repo_root}/reports/readiness"|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_EXTERNAL_EVIDENCE_SUMMARY is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_EXTERNAL_EVIDENCE_SUMMARY must stay inside the repository: ${path}"
      ;;
  esac
  [[ ! -d "${normalized}" ]] || fail "CREST_EXTERNAL_EVIDENCE_SUMMARY must be a file path: ${path}"
  printf '%s' "${normalized}"
}

evidence_dir="$(assert_safe_evidence_dir "${evidence_dir}")"
summary_file="$(assert_safe_summary_file "${summary_file}")"

[[ -d "${evidence_dir}" ]] || fail "missing external evidence directory: ${evidence_dir}"
mkdir -p "$(dirname "${summary_file}")"
rm -f "${summary_file}"

required_files=(
  "ob-oracle-init.md:OB Oracle initialization execution record"
  "ob-oracle-backup.md:OB Oracle backup record"
  "ob-oracle-restore.md:OB Oracle restore drill record"
  "redis-cluster.md:Redis Cluster ACL, connectivity and namespace record"
  "redis-failover.md:Redis Cluster failover drill record"
  "credential-rotation.md:credential rotation and clean-source delivery record for historical secret findings"
  "tls-ingress.md:Ingress TLS certificate and real domain record"
  "storage-rwx.md:RWX storage binding and shared write record"
  "business-smoke.md:login, dashboard, dataset preview, export, async task and websocket smoke record"
  "failure-drill.md:rolling restart and pod deletion drill record"
)

placeholder_pattern='CHANGE_ME|change-me|TODO|FIXME|<[^>]+>'

common_required_fields=(
  status
  environment
  evidence_date
  owner
  artifact_reference
  notes
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

json_array_count() {
  local path="$1"
  command -v node >/dev/null 2>&1 || fail "missing node for JSON evidence verification"
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

repo_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s' "${path}"
  else
    printf '%s/%s' "${repo_root}" "${path}"
  fi
}

require_field() {
  local path="$1"
  local file="$2"
  local field="$3"
  if ! grep -Eiq "^${field}:[[:space:]]*[^[:space:]].*$" "${path}"; then
    fail "${file} must include a non-empty ${field}: field"
  fi
}

validate_date_field() {
  local path="$1"
  local file="$2"
  local field="$3"
  local value status
  value="$(field_value "${path}" "${field}")"
  [[ "${value}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
    || fail "${file} ${field} must use YYYY-MM-DD"
  status=0
  node -e '
    const [value, today] = process.argv.slice(1);
    function epochDay(date) {
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
        return null;
      }
      const parsed = new Date(`${date}T00:00:00Z`);
      if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
        return null;
      }
      return Math.floor(parsed.getTime() / 86400000);
    }
    const valueDay = epochDay(value);
    const todayDay = epochDay(today);
    if (valueDay === null) process.exit(2);
    if (todayDay === null) process.exit(3);
    if (valueDay > todayDay) process.exit(4);
  ' "${value}" "${evidence_today}" || status=$?
  case "${status}" in
    0)
      ;;
    2)
      fail "${file} ${field} must be a real calendar date"
      ;;
    3)
      fail "CREST_EVIDENCE_TODAY must use YYYY-MM-DD"
      ;;
    4)
      fail "${file} ${field} must not be in the future"
      ;;
    *)
      fail "${file} ${field} date validation failed"
      ;;
  esac
}

require_date_field() {
  local path="$1"
  local file="$2"
  validate_date_field "${path}" "${file}" evidence_date
}

require_named_date_field() {
  local path="$1"
  local file="$2"
  local field="$3"
  validate_date_field "${path}" "${file}" "${field}"
}

require_date_not_after() {
  local path="$1"
  local file="$2"
  local earlier_field="$3"
  local later_field="$4"
  local earlier later
  earlier="$(field_value "${path}" "${earlier_field}")"
  later="$(field_value "${path}" "${later_field}")"
  if [[ "${earlier}" > "${later}" ]]; then
    fail "${file} ${earlier_field} must not be later than ${later_field}"
  fi
}

field_value() {
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

report_field_value() {
  local path="$1"
  local field="$2"
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${path}"
}

redis_hash_tag_from_prefix() {
  local prefix="$1"
  local file="$2"
  [[ "${prefix}" =~ ^\{([a-z0-9][a-z0-9._-]{7,63})\}:[a-z0-9][a-z0-9._-]*$ ]] \
    || fail "${file} redis_key_prefix must look like {<org>-<env>-crest-core}:prod"
  printf '%s' "${BASH_REMATCH[1]}"
}

validate_redis_hash_tag() {
  local tag="$1"
  local file="$2"
  [[ "${tag}" =~ ^[a-z0-9][a-z0-9._-]{7,63}$ ]] \
    || fail "${file} redis_hash_tag must be an 8-64 character lowercase namespace"
  case "${tag}" in
    app|application|cache|crest|crest-core|dataease|default|prod|production|redis|shared|system)
      fail "${file} redis_hash_tag is too generic for shared Redis"
      ;;
  esac
  case "${tag}" in
    acme-crest-core-prod|*changeme*|*change-me*|*example*|*sample*|*demo*|*template*|*placeholder*)
      fail "${file} redis_hash_tag looks like an example value; replace it with a real organization/environment namespace"
      ;;
  esac
}

validate_redis_acl_user() {
  local user="$1"
  local file="$2"
  [[ "${user}" != "default" ]] \
    || fail "${file} redis_acl_user must not be default for shared Redis"
  [[ "${user}" =~ ^[a-z0-9][a-z0-9._-]{7,63}$ ]] \
    || fail "${file} redis_acl_user must be an 8-64 character lowercase environment-specific ACL user"
  case "${user}" in
    app|application|cache|crest|crest-core|dataease|default|prod|production|redis|shared|system)
      fail "${file} redis_acl_user is too generic for shared Redis"
      ;;
  esac
  case "${user}" in
    acme-crest-production-acl-user|*changeme*|*change-me*|*example*|*sample*|*demo*|*template*|*placeholder*)
      fail "${file} redis_acl_user looks like an example value; replace it with a real organization/environment ACL user"
      ;;
  esac
}

require_specific_fields() {
  local path="$1"
  local file="$2"
  case "${file}" in
    credential-rotation.md)
      require_field "${path}" "${file}" history_scan_report
      require_field "${path}" "${file}" history_scan_report_sha256
      require_field "${path}" "${file}" history_findings_remaining
      require_field "${path}" "${file}" affected_credential_classes
      require_field "${path}" "${file}" credential_rotation_status
      require_field "${path}" "${file}" delivery_path
      require_field "${path}" "${file}" rotation_evidence_id
      require_field "${path}" "${file}" approved_by
      require_field "${path}" "${file}" approval_date
      require_named_date_field "${path}" "${file}" approval_date
      require_date_not_after "${path}" "${file}" approval_date evidence_date
      local history_scan_report history_scan_report_sha256 history_report_path history_report_findings history_report_sha256
      local history_findings_remaining credential_rotation_status delivery_path affected_credential_classes
      local rotation_evidence_id approved_by
      history_scan_report="$(field_value "${path}" history_scan_report)"
      history_scan_report_sha256="$(field_value "${path}" history_scan_report_sha256)"
      history_report_path="$(repo_path "${history_scan_report}")"
      history_findings_remaining="$(field_value "${path}" history_findings_remaining)"
      credential_rotation_status="$(field_value "${path}" credential_rotation_status)"
      delivery_path="$(field_value "${path}" delivery_path)"
      affected_credential_classes="$(field_value "${path}" affected_credential_classes)"
      rotation_evidence_id="$(field_value "${path}" rotation_evidence_id)"
      approved_by="$(field_value "${path}" approved_by)"
      [[ "${history_findings_remaining}" =~ ^[0-9]+$ ]] \
        || fail "${file} history_findings_remaining must be a non-negative integer"
      [[ -f "${history_report_path}" ]] \
        || fail "${file} history_scan_report does not exist: ${history_scan_report}"
      [[ "${history_scan_report_sha256}" =~ ^[0-9a-f]{64}$ ]] \
        || fail "${file} history_scan_report_sha256 must be a SHA-256 digest"
      history_report_sha256="$(file_sha256 "${history_report_path}")"
      [[ "${history_report_sha256}" == "${history_scan_report_sha256}" ]] \
        || fail "${file} history_scan_report_sha256 must match history_scan_report"
      history_report_findings="$(json_array_count "${history_report_path}")" \
        || fail "${file} history_scan_report must be a JSON array: ${history_scan_report}"
      [[ "${history_report_findings}" == "${history_findings_remaining}" ]] \
        || fail "${file} history_findings_remaining must match history_scan_report finding count"
      case "${credential_rotation_status}" in
        rotated-before-delivery|not-applicable-clean-history)
          ;;
        *)
          fail "${file} credential_rotation_status must be rotated-before-delivery or not-applicable-clean-history"
          ;;
      esac
      case "${delivery_path}" in
        clean-source|clean-history|fresh-repository)
          ;;
        *)
          fail "${file} delivery_path must be clean-source, clean-history or fresh-repository"
          ;;
      esac
      if [[ "${delivery_path}" == "clean-history" && "${history_findings_remaining}" != "0" ]]; then
        fail "${file} clean-history delivery requires history_findings_remaining: 0"
      fi
      if [[ "${delivery_path}" != "clean-history" && "${credential_rotation_status}" != "rotated-before-delivery" ]]; then
        fail "${file} ${delivery_path} delivery requires credential_rotation_status: rotated-before-delivery"
      fi
      if [[ "${history_findings_remaining}" != "0" ]]; then
        case "${affected_credential_classes}" in
          not-recorded|unknown|none|n/a|N/A)
            fail "${file} affected_credential_classes must list rotated credential classes when history findings remain"
            ;;
        esac
        case "${rotation_evidence_id}" in
          not-recorded|unknown|none|n/a|N/A)
            fail "${file} rotation_evidence_id must reference the rotation ticket or audit record"
            ;;
        esac
      fi
      case "${approved_by}" in
        not-recorded|unknown|none|n/a|N/A)
          fail "${file} approved_by must name the approving team or reviewer"
          ;;
      esac
      ;;
    redis-cluster.md)
      require_field "${path}" "${file}" redis_key_prefix
      require_field "${path}" "${file}" redis_hash_tag
      require_field "${path}" "${file}" redis_acl_user
      require_field "${path}" "${file}" redis_namespace_check_report
      require_field "${path}" "${file}" redis_namespace_check_report_sha256
      local redis_key_prefix redis_hash_tag redis_acl_user redis_namespace_check_report redis_namespace_check_report_sha256
      local prefix_hash_tag report_path report_sha256
      local report_status report_redis_cluster_nodes_count report_redis_node
      local report_redis_key_prefix report_redis_hash_tag report_redis_acl_user
      local report_acl_key_isolation report_acl_stream_isolation report_acl_channel_isolation
      redis_key_prefix="$(field_value "${path}" redis_key_prefix)"
      redis_hash_tag="$(field_value "${path}" redis_hash_tag)"
      redis_acl_user="$(field_value "${path}" redis_acl_user)"
      redis_namespace_check_report="$(field_value "${path}" redis_namespace_check_report)"
      redis_namespace_check_report_sha256="$(field_value "${path}" redis_namespace_check_report_sha256)"
      validate_redis_hash_tag "${redis_hash_tag}" "${file}"
      validate_redis_acl_user "${redis_acl_user}" "${file}"
      prefix_hash_tag="$(redis_hash_tag_from_prefix "${redis_key_prefix}" "${file}")"
      [[ "${prefix_hash_tag}" == "${redis_hash_tag}" ]] \
        || fail "${file} redis_hash_tag must match redis_key_prefix hash tag"
      if [[ "${redis_namespace_check_report}" = /* ]]; then
        report_path="${redis_namespace_check_report}"
      else
        report_path="${repo_root}/${redis_namespace_check_report}"
      fi
      [[ -f "${report_path}" ]] || fail "${file} redis_namespace_check_report does not exist: ${redis_namespace_check_report}"
      [[ "${redis_namespace_check_report_sha256}" =~ ^[0-9a-f]{64}$ ]] \
        || fail "${file} redis_namespace_check_report_sha256 must be a SHA-256 digest"
      report_sha256="$(file_sha256 "${report_path}")"
      [[ "${report_sha256}" == "${redis_namespace_check_report_sha256}" ]] \
        || fail "${file} redis_namespace_check_report_sha256 must match redis_namespace_check_report"
      report_status="$(report_field_value "${report_path}" status)"
      report_redis_cluster_nodes_count="$(report_field_value "${report_path}" redis_cluster_nodes_count)"
      report_redis_node="$(report_field_value "${report_path}" redis_node)"
      report_redis_key_prefix="$(report_field_value "${report_path}" redis_key_prefix)"
      report_redis_hash_tag="$(report_field_value "${report_path}" redis_hash_tag)"
      report_redis_acl_user="$(report_field_value "${report_path}" redis_acl_user)"
      report_acl_key_isolation="$(report_field_value "${report_path}" redis_acl_key_isolation)"
      report_acl_stream_isolation="$(report_field_value "${report_path}" redis_acl_stream_isolation)"
      report_acl_channel_isolation="$(report_field_value "${report_path}" redis_acl_channel_isolation)"
      [[ "${report_status}" == "passed" ]] \
        || fail "${file} redis namespace check report must contain status=passed"
      [[ "${report_redis_cluster_nodes_count}" =~ ^[0-9]+$ ]] \
        || fail "${file} redis namespace check report must contain numeric redis_cluster_nodes_count"
      [[ "${report_redis_cluster_nodes_count}" -ge 3 ]] \
        || fail "${file} redis namespace check report must prove at least 3 Redis Cluster nodes"
      [[ "${report_redis_node}" =~ ^[^[:space:]:]+:[0-9]+$ ]] \
        || fail "${file} redis namespace check report must contain redis_node as host:port"
      [[ "${report_redis_key_prefix}" == "${redis_key_prefix}" ]] \
        || fail "${file} redis namespace check report redis_key_prefix must match evidence"
      [[ "${report_redis_hash_tag}" == "${redis_hash_tag}" ]] \
        || fail "${file} redis namespace check report redis_hash_tag must match evidence"
      [[ "${report_redis_acl_user}" == "${redis_acl_user}" ]] \
        || fail "${file} redis namespace check report redis_acl_user must match evidence"
      [[ "${report_acl_key_isolation}" == "passed" ]] \
        || fail "${file} redis namespace check report must contain redis_acl_key_isolation=passed"
      [[ "${report_acl_stream_isolation}" == "passed" ]] \
        || fail "${file} redis namespace check report must contain redis_acl_stream_isolation=passed"
      [[ "${report_acl_channel_isolation}" == "passed" ]] \
        || fail "${file} redis namespace check report must contain redis_acl_channel_isolation=passed"
      ;;
    tls-ingress.md)
      require_field "${path}" "${file}" ingress_host
      require_field "${path}" "${file}" tls_expiry_monitor
      ;;
    storage-rwx.md)
      require_field "${path}" "${file}" pvc_name
      require_field "${path}" "${file}" access_mode
      ;;
    business-smoke.md)
      require_field "${path}" "${file}" smoke_scope
      ;;
    failure-drill.md)
      require_field "${path}" "${file}" drill_scope
      ;;
  esac
}

summary_tmp="$(mktemp "${summary_file}.tmp.XXXXXX")"
cleanup_summary_tmp() {
  rm -f "${summary_tmp}"
}
trap cleanup_summary_tmp EXIT

{
  echo "Crest Core external production evidence check"
  echo "status=passed"
  echo "evidence_dir=${evidence_dir}"
  echo "required_evidence_files=${#required_files[@]}"
  echo "evidence_file_count=${#required_files[@]}"
} > "${summary_tmp}"

evidence_index=0
for item in "${required_files[@]}"; do
  evidence_index=$((evidence_index + 1))
  file="${item%%:*}"
  description="${item#*:}"
  path="${evidence_dir}/${file}"
  [[ -f "${path}" ]] || fail "missing ${file}: ${description}"
  [[ -s "${path}" ]] || fail "${file} is empty"
  if grep -Eq "${placeholder_pattern}" "${path}"; then
    fail "${file} still contains placeholder text"
  fi
  if ! grep -Eiq '^status:[[:space:]]*passed[[:space:]]*$' "${path}"; then
    fail "${file} must include a line: status: passed"
  fi
  for field in "${common_required_fields[@]}"; do
    require_field "${path}" "${file}" "${field}"
  done
  require_date_field "${path}" "${file}"
  require_specific_fields "${path}" "${file}"
  digest="$(file_sha256 "${path}")"
  {
    echo "${file}: present sha256=${digest}"
    echo "evidence_file_${evidence_index}=${file}"
    echo "evidence_file_${evidence_index}_sha256=${digest}"
    echo "evidence_file_${evidence_index}_description=${description}"
  } >> "${summary_tmp}"
done

mv "${summary_tmp}" "${summary_file}"
trap - EXIT
info "passed; summary written to ${summary_file}"
