#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "production-go-no-go-summary-check: $*" >&2
  exit 1
}

summary_file="${1:-reports/readiness/enterprise-readiness-summary.txt}"
[[ -f "${summary_file}" ]] || fail "missing readiness summary: ${summary_file}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
go_no_go_today="${CREST_EVIDENCE_TODAY:-$(date -u +%F)}"
go_no_go_now_utc="${CREST_EVIDENCE_NOW_UTC:-$(date -u +%Y%m%dT%H%M%SZ)}"

field_value() {
  local field="$1"
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${summary_file}"
}

artifact_field_value() {
  local path="$1"
  local field="$2"
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${path}"
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

repo_path() {
  local path="$1"
  if [[ "${path}" = /* ]]; then
    printf '%s' "${path}"
  else
    printf '%s/%s' "${repo_root}" "${path}"
  fi
}

file_sha256() {
  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${path}" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
  else
    fail "missing shasum or sha256sum for evidence digest verification"
  fi
}

json_array_count() {
  local path="$1"
  command -v node >/dev/null 2>&1 || fail "missing node for JSON report verification"
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

validate_date_value() {
  local value="$1"
  local label="$2"
  local status
  [[ "${value}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] \
    || fail "${label} must use YYYY-MM-DD"
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
  ' "${value}" "${go_no_go_today}" || status=$?
  case "${status}" in
    0)
      ;;
    2)
      fail "${label} must be a real calendar date"
      ;;
    3)
      fail "CREST_EVIDENCE_TODAY must use YYYY-MM-DD"
      ;;
    4)
      fail "${label} must not be in the future"
      ;;
    *)
      fail "${label} date validation failed"
      ;;
  esac
}

validate_timestamp_utc_value() {
  local value="$1"
  local label="$2"
  local status
  [[ "${value}" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] \
    || fail "${label} must use YYYYMMDDTHHMMSSZ"
  status=0
  node -e '
    const [value, now] = process.argv.slice(1);
    function epochMs(timestamp) {
      if (!/^\d{8}T\d{6}Z$/.test(timestamp)) {
        return null;
      }
      const iso = `${timestamp.slice(0, 4)}-${timestamp.slice(4, 6)}-${timestamp.slice(6, 8)}T${timestamp.slice(9, 11)}:${timestamp.slice(11, 13)}:${timestamp.slice(13, 15)}Z`;
      const parsed = new Date(iso);
      if (Number.isNaN(parsed.getTime())) {
        return null;
      }
      const normalized = parsed.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
      return normalized === timestamp ? parsed.getTime() : null;
    }
    const valueMs = epochMs(value);
    const nowMs = epochMs(now);
    if (valueMs === null) process.exit(2);
    if (nowMs === null) process.exit(3);
    if (valueMs > nowMs) process.exit(4);
  ' "${value}" "${go_no_go_now_utc}" || status=$?
  case "${status}" in
    0)
      ;;
    2)
      fail "${label} must be a real UTC timestamp"
      ;;
    3)
      fail "CREST_EVIDENCE_NOW_UTC must use YYYYMMDDTHHMMSSZ"
      ;;
    4)
      fail "${label} must not be in the future"
      ;;
    *)
      fail "${label} timestamp validation failed"
      ;;
  esac
}

validate_external_evidence_date_field() {
  local path="$1"
  local file="$2"
  local field="$3"
  local value
  value="$(evidence_field_value "${path}" "${field}")"
  validate_date_value "${value}" "${file} ${field}"
}

require_external_date_not_after() {
  local path="$1"
  local file="$2"
  local earlier_field="$3"
  local later_field="$4"
  local earlier later
  earlier="$(evidence_field_value "${path}" "${earlier_field}")"
  later="$(evidence_field_value "${path}" "${later_field}")"
  if [[ "${earlier}" > "${later}" ]]; then
    fail "${file} ${earlier_field} must not be later than ${later_field}"
  fi
}

external_evidence_required_files=(
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

external_evidence_placeholder_pattern='CHANGE_ME|change-me|TODO|FIXME|<[^>]+>'

external_evidence_common_fields=(
  status
  environment
  evidence_date
  owner
  artifact_reference
  notes
)

require_line() {
  local pattern="$1"
  local description="$2"
  grep -Eq "${pattern}" "${summary_file}" || fail "readiness summary missing ${description}"
}

require_external_evidence_field() {
  local path="$1"
  local file="$2"
  local field="$3"
  if ! grep -Eiq "^${field}:[[:space:]]*[^[:space:]].*$" "${path}"; then
    fail "${file} must include a non-empty ${field}: field"
  fi
}

verify_external_evidence_file_body() {
  local path="$1"
  local file="$2"
  local field

  if grep -Eq "${external_evidence_placeholder_pattern}" "${path}"; then
    fail "${file} still contains placeholder text"
  fi
  if ! grep -Eiq '^status:[[:space:]]*passed[[:space:]]*$' "${path}"; then
    fail "${file} must include a line: status: passed"
  fi
  for field in "${external_evidence_common_fields[@]}"; do
    require_external_evidence_field "${path}" "${file}" "${field}"
  done

  case "${file}" in
    tls-ingress.md)
      require_external_evidence_field "${path}" "${file}" ingress_host
      require_external_evidence_field "${path}" "${file}" tls_expiry_monitor
      ;;
    storage-rwx.md)
      require_external_evidence_field "${path}" "${file}" pvc_name
      require_external_evidence_field "${path}" "${file}" access_mode
      ;;
    business-smoke.md)
      require_external_evidence_field "${path}" "${file}" smoke_scope
      ;;
    failure-drill.md)
      require_external_evidence_field "${path}" "${file}" drill_scope
      ;;
  esac
}

require_digest_match() {
  local path_field="$1"
  local digest_field="$2"
  local description="$3"
  local path digest actual
  path="$(field_value "${path_field}")"
  digest="$(field_value "${digest_field}")"
  [[ -n "${path}" ]] || fail "readiness summary missing ${path_field}"
  [[ -f "${path}" ]] || fail "${description} does not exist: ${path}"
  [[ "${digest}" =~ ^[0-9a-f]{64}$ ]] || fail "${digest_field} must be a SHA-256 digest"
  actual="$(file_sha256 "${path}")"
  [[ "${actual}" == "${digest}" ]] || fail "${description} SHA-256 mismatch: expected ${digest}, got ${actual}"
}

require_clean_source_summary_field() {
  local field="$1"
  local expected="$2"
  local description="$3"
  local actual
  actual="$(artifact_field_value "${clean_source_summary_path}" "${field}")"
  [[ -n "${actual}" ]] || fail "clean source summary missing ${field}"
  [[ "${actual}" == "${expected}" ]] || fail "${description} mismatch between readiness summary and clean source summary: expected ${expected}, got ${actual}"
}

verify_digest_manifest_entries() {
  local evidence_dir="$1"
  local manifest_path="$2"
  local description="$3"
  local count=0
  local digest rel_path actual

  while read -r digest rel_path; do
    [[ -n "${digest:-}" || -n "${rel_path:-}" ]] || continue
    [[ "${digest}" =~ ^[0-9a-f]{64}$ ]] || fail "${description} contains an invalid digest: ${digest}"
    [[ -n "${rel_path}" ]] || fail "${description} contains an empty path"
    [[ "${rel_path}" != /* && "${rel_path}" != *".."* ]] \
      || fail "${description} contains an unsafe path: ${rel_path}"
    [[ -f "${evidence_dir}/${rel_path}" ]] \
      || fail "${description} references a missing file: ${rel_path}"
    actual="$(file_sha256 "${evidence_dir}/${rel_path}")"
    [[ "${actual}" == "${digest}" ]] \
      || fail "${description} digest mismatch for ${rel_path}: expected ${digest}, got ${actual}"
    count=$((count + 1))
  done < "${manifest_path}"

  [[ "${count}" -gt 0 ]] || fail "${description} must contain at least one file digest"
  digest_manifest_entry_count="${count}"
}

verify_clean_source_archive() {
  local archive_path="$1"
  local archive_listing

  command -v tar >/dev/null 2>&1 || fail "missing required command: tar"
  archive_listing="$(mktemp)"
  if ! tar -tzf "${archive_path}" > "${archive_listing}"; then
    rm -f "${archive_listing}"
    fail "clean source archive is not a readable tar.gz: ${archive_path}"
  fi
  if ! grep -Eq '(^|/)SOURCE_MANIFEST\.txt$' "${archive_listing}"; then
    rm -f "${archive_listing}"
    fail "clean source archive must contain SOURCE_MANIFEST.txt"
  fi
  if grep -Eq '^/|(^|/)\.\.(/|$)' "${archive_listing}"; then
    rm -f "${archive_listing}"
    fail "clean source archive contains unsafe absolute or parent-relative paths"
  fi
  if grep -Eq '(^|/)(\.git|\.gitmodules|\.local|\.cache|\.crest-local|reports|private|private-tests|node_modules|target|dist)(/|$)' "${archive_listing}"; then
    rm -f "${archive_listing}"
    fail "clean source archive contains forbidden history, private, report, dependency or build paths"
  fi
  if grep -Eq '(^|/)scripts/restore-private-tests\.mjs$|(^|/)core/core-backend/src/main/resources/static(/|$)' "${archive_listing}"; then
    rm -f "${archive_listing}"
    fail "clean source archive contains private restore tooling or generated backend static resources"
  fi
  if grep -Eq '(^|/)\.env(\..*)?$|(^|/)[^/]+\.(pem|key|p12|pfx|jks|keystore|csr|crt|dump|bak|backup|db|sqlite|sqlite3)$' "${archive_listing}"; then
    rm -f "${archive_listing}"
    fail "clean source archive contains environment, key, certificate, backup or database files"
  fi
  rm -f "${archive_listing}"
}

verify_production_runtime_output() {
  local runtime_output="$1"
  [[ -s "${runtime_output}" ]] || fail "production runtime check output is missing or empty: ${runtime_output}"
  grep -Eq '^runtime-check: namespace [^[:space:]]+ passed live production runtime checks$' "${runtime_output}" \
    || fail "production runtime check output must contain the live runtime success line"
  if grep -Eq '^runtime-check: warning:' "${runtime_output}"; then
    fail "production runtime check output must not contain runtime warnings"
  fi
}

verify_production_overlay_evidence() {
  local evidence_dir summary_path manifest_path resources_path secrets_path
  local summary_dir manifest_name resources_name resources_digest secrets_name secrets_digest
  local resource_count secret_count timestamp_utc

  evidence_dir="$(field_value production_overlay_evidence_dir)"
  summary_path="$(field_value production_overlay_evidence_summary)"
  manifest_path="$(field_value production_overlay_evidence_manifest)"
  resources_path="$(field_value production_overlay_sanitized_resources)"
  secrets_path="$(field_value production_overlay_sanitized_secrets)"

  [[ -n "${evidence_dir}" ]] || fail "readiness summary missing production_overlay_evidence_dir"
  [[ -d "${evidence_dir}" ]] || fail "production overlay evidence directory does not exist: ${evidence_dir}"
  [[ -n "${summary_path}" ]] || fail "readiness summary missing production_overlay_evidence_summary"
  [[ -n "${manifest_path}" ]] || fail "readiness summary missing production_overlay_evidence_manifest"
  [[ -n "${resources_path}" ]] || fail "readiness summary missing production_overlay_sanitized_resources"
  [[ -n "${secrets_path}" ]] || fail "readiness summary missing production_overlay_sanitized_secrets"

  summary_dir="$(dirname "${summary_path}")"
  [[ "${summary_dir}" == "${evidence_dir}" ]] \
    || fail "production_overlay_evidence_summary must be inside production_overlay_evidence_dir"

  manifest_name="$(artifact_field_value "${summary_path}" evidence_manifest)"
  resources_name="$(artifact_field_value "${summary_path}" sanitized_resources)"
  resources_digest="$(artifact_field_value "${summary_path}" sanitized_resources_sha256)"
  secrets_name="$(artifact_field_value "${summary_path}" sanitized_secrets)"
  secrets_digest="$(artifact_field_value "${summary_path}" sanitized_secrets_sha256)"
  resource_count="$(artifact_field_value "${summary_path}" resource_count)"
  secret_count="$(artifact_field_value "${summary_path}" secret_count)"
  timestamp_utc="$(artifact_field_value "${summary_path}" timestamp_utc)"

  validate_timestamp_utc_value "${timestamp_utc}" "production overlay evidence summary timestamp_utc"
  [[ "${manifest_name}" == "overlay-evidence-manifest.sha256" ]] \
    || fail "production overlay evidence summary must record evidence_manifest=overlay-evidence-manifest.sha256"
  [[ "${resources_name}" == "resources-sanitized.json" ]] \
    || fail "production overlay evidence summary must record sanitized_resources=resources-sanitized.json"
  [[ "${secrets_name}" == "secrets-sanitized.json" ]] \
    || fail "production overlay evidence summary must record sanitized_secrets=secrets-sanitized.json"
  [[ "${manifest_path}" == "${evidence_dir}/${manifest_name}" ]] \
    || fail "production_overlay_evidence_manifest must match evidence_manifest recorded in production overlay summary"
  [[ "${resources_path}" == "${evidence_dir}/${resources_name}" ]] \
    || fail "production_overlay_sanitized_resources must match sanitized_resources recorded in production overlay summary"
  [[ "${secrets_path}" == "${evidence_dir}/${secrets_name}" ]] \
    || fail "production_overlay_sanitized_secrets must match sanitized_secrets recorded in production overlay summary"
  [[ "${resources_digest}" == "$(field_value production_overlay_sanitized_resources_sha256)" ]] \
    || fail "production overlay sanitized resources digest must match the overlay evidence summary"
  [[ "${secrets_digest}" == "$(field_value production_overlay_sanitized_secrets_sha256)" ]] \
    || fail "production overlay sanitized secrets digest must match the overlay evidence summary"
  [[ "${resource_count}" =~ ^[1-9][0-9]*$ ]] \
    || fail "production overlay evidence summary resource_count must be a positive integer"
  [[ "${secret_count}" =~ ^[1-9][0-9]*$ ]] \
    || fail "production overlay evidence summary secret_count must be a positive integer"

  grep -Eq '^[0-9a-f]{64}[[:space:]]+summary\.txt$' "${manifest_path}" \
    || fail "production overlay evidence manifest must include summary.txt"
  grep -Eq '^[0-9a-f]{64}[[:space:]]+resources-sanitized\.json$' "${manifest_path}" \
    || fail "production overlay evidence manifest must include resources-sanitized.json"
  grep -Eq '^[0-9a-f]{64}[[:space:]]+secrets-sanitized\.json$' "${manifest_path}" \
    || fail "production overlay evidence manifest must include secrets-sanitized.json"
  verify_digest_manifest_entries "${evidence_dir}" "${manifest_path}" "production overlay evidence manifest"
  [[ "${digest_manifest_entry_count}" == "3" ]] \
    || fail "production overlay evidence manifest must contain exactly 3 entries"

  node scripts/verify-sanitized-kubernetes-secrets.mjs \
    "${secrets_path}" \
    crest-db-secret \
    crest-redis-secret >/dev/null
}

verify_external_evidence_summary() {
  local summary_path="$1"
  local evidence_dir required_count evidence_count index expected_file actual_file expected_digest actual_digest

  [[ "$(artifact_field_value "${summary_path}" status)" == "passed" ]] \
    || fail "external evidence summary must record status=passed"
  evidence_dir="$(artifact_field_value "${summary_path}" evidence_dir)"
  required_count="$(artifact_field_value "${summary_path}" required_evidence_files)"
  evidence_count="$(artifact_field_value "${summary_path}" evidence_file_count)"
  [[ -n "${evidence_dir}" ]] || fail "external evidence summary missing evidence_dir"
  [[ -d "${evidence_dir}" ]] || fail "external evidence directory does not exist: ${evidence_dir}"
  [[ "${required_count}" =~ ^[0-9]+$ ]] \
    || fail "external evidence summary required_evidence_files must be numeric"
  [[ "${evidence_count}" =~ ^[0-9]+$ ]] \
    || fail "external evidence summary evidence_file_count must be numeric"
  [[ "${required_count}" == "${#external_evidence_required_files[@]}" ]] \
    || fail "external evidence summary required_evidence_files must be ${#external_evidence_required_files[@]}"
  [[ "${evidence_count}" == "${#external_evidence_required_files[@]}" ]] \
    || fail "external evidence summary evidence_file_count must be ${#external_evidence_required_files[@]}"

  for ((index = 1; index <= evidence_count; index++)); do
    expected_file="${external_evidence_required_files[$((index - 1))]}"
    actual_file="$(artifact_field_value "${summary_path}" "evidence_file_${index}")"
    expected_digest="$(artifact_field_value "${summary_path}" "evidence_file_${index}_sha256")"
    [[ "${actual_file}" == "${expected_file}" ]] \
      || fail "external evidence summary evidence_file_${index} must be ${expected_file}, got ${actual_file:-missing}"
    [[ "${actual_file}" != /* && "${actual_file}" != *".."* ]] \
      || fail "external evidence summary contains an unsafe evidence path: ${actual_file}"
    [[ "${expected_digest}" =~ ^[0-9a-f]{64}$ ]] \
      || fail "external evidence summary ${actual_file} must include a SHA-256 digest"
    [[ -f "${evidence_dir}/${actual_file}" ]] \
      || fail "external evidence summary references a missing file: ${actual_file}"
    verify_external_evidence_file_body "${evidence_dir}/${actual_file}" "${actual_file}"
    validate_external_evidence_date_field "${evidence_dir}/${actual_file}" "${actual_file}" evidence_date
    actual_digest="$(file_sha256 "${evidence_dir}/${actual_file}")"
    [[ "${actual_digest}" == "${expected_digest}" ]] \
      || fail "external evidence summary digest mismatch for ${actual_file}: expected ${expected_digest}, got ${actual_digest}"
  done
  verified_external_evidence_dir="${evidence_dir}"
}

verify_external_credential_evidence() {
  local evidence_dir="$1"
  local credential_evidence="${evidence_dir}/credential-rotation.md"
  [[ -f "${credential_evidence}" ]] \
    || fail "external credential rotation evidence does not exist: ${credential_evidence}"
  validate_external_evidence_date_field "${credential_evidence}" "credential-rotation.md" evidence_date
  validate_external_evidence_date_field "${credential_evidence}" "credential-rotation.md" approval_date
  require_external_date_not_after "${credential_evidence}" "credential-rotation.md" approval_date evidence_date
}

redis_hash_tag_from_prefix() {
  local prefix="$1"
  local file="$2"
  [[ "${prefix}" =~ ^\{([a-z0-9][a-z0-9._-]{7,63})\}:[a-z0-9][a-z0-9._-]*$ ]] \
    || fail "${file} redis_key_prefix must look like {<org>-<env>-crest-core}:prod"
  printf '%s' "${BASH_REMATCH[1]}"
}

validate_external_redis_hash_tag() {
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

validate_external_redis_acl_user() {
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

verify_external_redis_evidence() {
  local evidence_dir="$1"
  local redis_evidence="${evidence_dir}/redis-cluster.md"
  local redis_key_prefix redis_hash_tag redis_acl_user redis_namespace_check_report redis_namespace_check_report_sha256
  local prefix_hash_tag report_path report_sha256 report_status report_redis_cluster_nodes_count report_redis_node
  local report_redis_key_prefix report_redis_hash_tag report_redis_acl_user
  local report_acl_key_isolation report_acl_stream_isolation report_acl_channel_isolation

  [[ -f "${redis_evidence}" ]] || fail "external Redis evidence does not exist: ${redis_evidence}"
  redis_key_prefix="$(evidence_field_value "${redis_evidence}" redis_key_prefix)"
  redis_hash_tag="$(evidence_field_value "${redis_evidence}" redis_hash_tag)"
  redis_acl_user="$(evidence_field_value "${redis_evidence}" redis_acl_user)"
  redis_namespace_check_report="$(evidence_field_value "${redis_evidence}" redis_namespace_check_report)"
  redis_namespace_check_report_sha256="$(evidence_field_value "${redis_evidence}" redis_namespace_check_report_sha256)"
  [[ -n "${redis_key_prefix}" ]] || fail "redis-cluster.md must include redis_key_prefix"
  [[ -n "${redis_hash_tag}" ]] || fail "redis-cluster.md must include redis_hash_tag"
  [[ -n "${redis_acl_user}" ]] || fail "redis-cluster.md must include redis_acl_user"
  [[ -n "${redis_namespace_check_report}" ]] || fail "redis-cluster.md must include redis_namespace_check_report"
  [[ -n "${redis_namespace_check_report_sha256}" ]] || fail "redis-cluster.md must include redis_namespace_check_report_sha256"

  validate_external_redis_hash_tag "${redis_hash_tag}" "redis-cluster.md"
  validate_external_redis_acl_user "${redis_acl_user}" "redis-cluster.md"
  prefix_hash_tag="$(redis_hash_tag_from_prefix "${redis_key_prefix}" "redis-cluster.md")"
  [[ "${prefix_hash_tag}" == "${redis_hash_tag}" ]] \
    || fail "redis-cluster.md redis_hash_tag must match redis_key_prefix hash tag"

  report_path="$(repo_path "${redis_namespace_check_report}")"
  [[ -f "${report_path}" ]] \
    || fail "redis-cluster.md redis_namespace_check_report does not exist: ${redis_namespace_check_report}"
  [[ "${redis_namespace_check_report_sha256}" =~ ^[0-9a-f]{64}$ ]] \
    || fail "redis-cluster.md redis_namespace_check_report_sha256 must be a SHA-256 digest"
  report_sha256="$(file_sha256 "${report_path}")"
  [[ "${report_sha256}" == "${redis_namespace_check_report_sha256}" ]] \
    || fail "redis-cluster.md redis_namespace_check_report_sha256 must match redis_namespace_check_report"
  report_status="$(artifact_field_value "${report_path}" status)"
  report_redis_cluster_nodes_count="$(artifact_field_value "${report_path}" redis_cluster_nodes_count)"
  report_redis_node="$(artifact_field_value "${report_path}" redis_node)"
  report_redis_key_prefix="$(artifact_field_value "${report_path}" redis_key_prefix)"
  report_redis_hash_tag="$(artifact_field_value "${report_path}" redis_hash_tag)"
  report_redis_acl_user="$(artifact_field_value "${report_path}" redis_acl_user)"
  report_acl_key_isolation="$(artifact_field_value "${report_path}" redis_acl_key_isolation)"
  report_acl_stream_isolation="$(artifact_field_value "${report_path}" redis_acl_stream_isolation)"
  report_acl_channel_isolation="$(artifact_field_value "${report_path}" redis_acl_channel_isolation)"
  [[ "${report_status}" == "passed" ]] \
    || fail "redis-cluster.md redis namespace check report must contain status=passed"
  [[ "${report_redis_cluster_nodes_count}" =~ ^[0-9]+$ ]] \
    || fail "redis-cluster.md redis namespace check report must contain numeric redis_cluster_nodes_count"
  [[ "${report_redis_cluster_nodes_count}" -ge 3 ]] \
    || fail "redis-cluster.md redis namespace check report must prove at least 3 Redis Cluster nodes"
  [[ "${report_redis_node}" =~ ^[^[:space:]:]+:[0-9]+$ ]] \
    || fail "redis-cluster.md redis namespace check report must contain redis_node as host:port"
  [[ "${report_redis_key_prefix}" == "${redis_key_prefix}" ]] \
    || fail "redis-cluster.md redis namespace check report redis_key_prefix must match evidence"
  [[ "${report_redis_hash_tag}" == "${redis_hash_tag}" ]] \
    || fail "redis-cluster.md redis namespace check report redis_hash_tag must match evidence"
  [[ "${report_redis_acl_user}" == "${redis_acl_user}" ]] \
    || fail "redis-cluster.md redis namespace check report redis_acl_user must match evidence"
  [[ "${report_acl_key_isolation}" == "passed" ]] \
    || fail "redis-cluster.md redis namespace check report must contain redis_acl_key_isolation=passed"
  [[ "${report_acl_stream_isolation}" == "passed" ]] \
    || fail "redis-cluster.md redis namespace check report must contain redis_acl_stream_isolation=passed"
  [[ "${report_acl_channel_isolation}" == "passed" ]] \
    || fail "redis-cluster.md redis namespace check report must contain redis_acl_channel_isolation=passed"
}

readiness_status="$(field_value readiness_status)"
production_release_status="$(field_value production_release_status)"

[[ "${readiness_status}" == "go-no-go-passed" ]] \
  || fail "readiness_status must be go-no-go-passed, got ${readiness_status:-missing}"

[[ "${production_release_status}" == "ready-for-business-approval" ]] \
  || fail "production_release_status must be ready-for-business-approval, got ${production_release_status:-missing}"

if grep -q '^production_release_blocker=' "${summary_file}"; then
  grep '^production_release_blocker=' "${summary_file}" >&2
  fail "readiness summary still contains production release blockers"
fi

require_line '^require_go_no_go=true$' 'require_go_no_go=true'
require_line '^create_clean_source=true$' 'create_clean_source=true'
require_line '^require_clean_release_source=true$' 'require_clean_release_source=true'
require_line '^clean_source_require_credential_rotation=true$' 'clean_source_require_credential_rotation=true'
require_line '^collect_evidence=true$' 'collect_evidence=true'
require_line '^check_external_evidence=true$' 'check_external_evidence=true'
require_line '^security_report_dir=[^[:space:]]+' 'security_report_dir path'
require_line '^security_report_manifest=[^[:space:]]+/security-report-manifest\.sha256$' 'security_report_manifest path'
require_line '^security_report_manifest_sha256=[0-9a-f]{64}$' 'security_report_manifest_sha256 digest'
require_line '^github_actions_policy_report=[^[:space:]]+\.txt$' 'github_actions_policy_report path'
require_line '^github_actions_policy_report_sha256=[0-9a-f]{64}$' 'github_actions_policy_report_sha256 digest'
require_line '^ci_toolchain_policy_report=[^[:space:]]+\.txt$' 'ci_toolchain_policy_report path'
require_line '^ci_toolchain_policy_report_sha256=[0-9a-f]{64}$' 'ci_toolchain_policy_report_sha256 digest'
require_line '^container_report_dir=[^[:space:]]+' 'container_report_dir path'
require_line '^container_report_manifest=[^[:space:]]+/container-report-manifest\.sha256$' 'container_report_manifest path'
require_line '^container_report_manifest_sha256=[0-9a-f]{64}$' 'container_report_manifest_sha256 digest'
require_line '^container_base_image_policy_report=[^[:space:]]+\.txt$' 'container_base_image_policy_report path'
require_line '^container_base_image_policy_report_sha256=[0-9a-f]{64}$' 'container_base_image_policy_report_sha256 digest'
require_line '^docker_build_base_image_policy_report=[^[:space:]]+\.txt$' 'docker_build_base_image_policy_report path'
require_line '^docker_build_base_image_policy_report_sha256=[0-9a-f]{64}$' 'docker_build_base_image_policy_report_sha256 digest'
require_line '^external_evidence_summary=[^[:space:]]+' 'external_evidence_summary path'
require_line '^external_evidence_summary_sha256=[0-9a-f]{64}$' 'external_evidence_summary_sha256 digest'
require_line '^production_evidence_dir=[^[:space:]]+' 'production_evidence_dir path'
require_line '^production_evidence_summary=[^[:space:]]+/summary\.txt$' 'production_evidence_summary path'
require_line '^production_evidence_summary_sha256=[0-9a-f]{64}$' 'production_evidence_summary_sha256 digest'
require_line '^production_evidence_manifest=[^[:space:]]+/evidence-manifest\.sha256$' 'production_evidence_manifest path'
require_line '^production_evidence_manifest_sha256=[0-9a-f]{64}$' 'production_evidence_manifest_sha256 digest'
require_line '^production_evidence_runtime_check=passed$' 'production_evidence_runtime_check=passed'
require_line '^production_overlay_evidence_dir=[^[:space:]]+' 'production_overlay_evidence_dir path'
require_line '^production_overlay_evidence_summary=[^[:space:]]+/summary\.txt$' 'production_overlay_evidence_summary path'
require_line '^production_overlay_evidence_summary_sha256=[0-9a-f]{64}$' 'production_overlay_evidence_summary_sha256 digest'
require_line '^production_overlay_evidence_manifest=[^[:space:]]+/overlay-evidence-manifest\.sha256$' 'production_overlay_evidence_manifest path'
require_line '^production_overlay_evidence_manifest_sha256=[0-9a-f]{64}$' 'production_overlay_evidence_manifest_sha256 digest'
require_line '^production_overlay_sanitized_resources=[^[:space:]]+/resources-sanitized\.json$' 'production_overlay_sanitized_resources path'
require_line '^production_overlay_sanitized_resources_sha256=[0-9a-f]{64}$' 'production_overlay_sanitized_resources_sha256 digest'
require_line '^production_overlay_sanitized_secrets=[^[:space:]]+/secrets-sanitized\.json$' 'production_overlay_sanitized_secrets path'
require_line '^production_overlay_sanitized_secrets_sha256=[0-9a-f]{64}$' 'production_overlay_sanitized_secrets_sha256 digest'
require_line '^clean_source_summary=[^[:space:]]+\.summary\.txt$' 'clean_source_summary path'
require_line '^clean_source_summary_sha256=[0-9a-f]{64}$' 'clean_source_summary_sha256 digest'
require_line '^clean_source_generated_at_utc=[0-9]{8}T[0-9]{6}Z$' 'clean_source_generated_at_utc timestamp'
require_line '^clean_source_version=[^[:space:]]+' 'clean_source_version value'
require_line '^clean_source_source_branch=[^[:space:]]+' 'clean_source_source_branch value'
require_line '^clean_source_source_commit=[0-9a-f]{40}$' 'clean_source_source_commit value'
require_line '^clean_source_archive=[^[:space:]]+\.tar\.gz$' 'clean_source_archive path'
require_line '^clean_source_archive_sha256=[0-9a-f]{64}$' 'clean_source_archive_sha256 digest'
require_line '^clean_source_source_file_count=[1-9][0-9]*$' 'clean_source_source_file_count value'
require_line '^clean_source_secret_scan_report=[^[:space:]]+\.json$' 'clean_source_secret_scan_report path'
require_line '^clean_source_secret_scan_report_sha256=[0-9a-f]{64}$' 'clean_source_secret_scan_report_sha256 digest'
require_line '^clean_source_secret_scan_findings=0$' 'clean_source_secret_scan_findings=0'
require_line '^clean_source_worktree_dirty=false$' 'clean_source_worktree_dirty=false'
require_line '^clean_source_history_scan_report=[^[:space:]]+\.json$' 'clean_source_history_scan_report path'
require_line '^clean_source_history_scan_report_sha256=[0-9a-f]{64}$' 'clean_source_history_scan_report_sha256 digest'
require_line '^clean_source_history_findings_remaining=([0-9]+|unknown)$' 'clean_source_history_findings_remaining value'
require_line '^clean_source_history_delivery_path=(clean-source|clean-history|fresh-repository)$' 'clean_source_history_delivery_path value'
require_line '^clean_source_credential_rotation_status=(rotated-before-delivery|not-applicable-clean-history|not-recorded)$' 'clean_source_credential_rotation_status value'
require_line '^clean_source_affected_credential_classes=[^[:space:]]+' 'clean_source_affected_credential_classes value'
require_line '^require_base_image_digests=true$' 'require_base_image_digests=true'
require_line '^history_secret_scan_report=[^[:space:]]+\.json$' 'history_secret_scan_report path'
require_line '^history_secret_scan_report_sha256=[0-9a-f]{64}$' 'history_secret_scan_report_sha256 digest'

require_digest_match security_report_manifest security_report_manifest_sha256 "security report manifest"
require_digest_match github_actions_policy_report github_actions_policy_report_sha256 "GitHub Actions policy report"
require_digest_match ci_toolchain_policy_report ci_toolchain_policy_report_sha256 "CI toolchain policy report"
require_digest_match container_report_manifest container_report_manifest_sha256 "container report manifest"
require_digest_match container_base_image_policy_report container_base_image_policy_report_sha256 "container base image policy report"
require_digest_match docker_build_base_image_policy_report docker_build_base_image_policy_report_sha256 "Docker build base image policy report"
require_digest_match external_evidence_summary external_evidence_summary_sha256 "external evidence summary"
require_digest_match production_overlay_evidence_summary production_overlay_evidence_summary_sha256 "production overlay evidence summary"
require_digest_match production_overlay_evidence_manifest production_overlay_evidence_manifest_sha256 "production overlay evidence manifest"
require_digest_match production_overlay_sanitized_resources production_overlay_sanitized_resources_sha256 "production overlay sanitized resources"
require_digest_match production_overlay_sanitized_secrets production_overlay_sanitized_secrets_sha256 "production overlay sanitized secrets"
require_digest_match production_evidence_summary production_evidence_summary_sha256 "production evidence summary"
require_digest_match production_evidence_manifest production_evidence_manifest_sha256 "production evidence manifest"
require_digest_match clean_source_summary clean_source_summary_sha256 "clean source summary"
require_digest_match clean_source_archive clean_source_archive_sha256 "clean source archive"
require_digest_match clean_source_secret_scan_report clean_source_secret_scan_report_sha256 "clean source secret scan report"
require_digest_match clean_source_history_scan_report clean_source_history_scan_report_sha256 "clean source history scan report"
require_digest_match history_secret_scan_report history_secret_scan_report_sha256 "history secret scan report"

security_report_dir="$(field_value security_report_dir)"
security_report_manifest_path="$(field_value security_report_manifest)"
container_report_dir="$(field_value container_report_dir)"
container_report_manifest_path="$(field_value container_report_manifest)"
github_actions_policy_report_path="$(field_value github_actions_policy_report)"
ci_toolchain_policy_report_path="$(field_value ci_toolchain_policy_report)"
container_base_image_policy_report_path="$(field_value container_base_image_policy_report)"
docker_build_base_image_policy_report_path="$(field_value docker_build_base_image_policy_report)"
external_evidence_summary_path="$(field_value external_evidence_summary)"
production_evidence_dir="$(field_value production_evidence_dir)"
production_evidence_summary_path="$(field_value production_evidence_summary)"
production_evidence_manifest_path="$(field_value production_evidence_manifest)"
production_evidence_runtime_check="$(field_value production_evidence_runtime_check)"
production_evidence_summary_dir="$(dirname "${production_evidence_summary_path}")"
production_evidence_manifest_name="$(artifact_field_value "${production_evidence_summary_path}" evidence_manifest)"
production_evidence_summary_runtime_check="$(artifact_field_value "${production_evidence_summary_path}" runtime_check)"
production_evidence_summary_require_ingress_address="$(artifact_field_value "${production_evidence_summary_path}" runtime_check_require_ingress_address)"
production_evidence_file_count="$(artifact_field_value "${production_evidence_summary_path}" evidence_file_count)"
production_evidence_summary_timestamp_utc="$(artifact_field_value "${production_evidence_summary_path}" timestamp_utc)"

[[ -d "${security_report_dir}" ]] || fail "security report directory does not exist: ${security_report_dir}"
[[ "${security_report_manifest_path}" == "${security_report_dir}/security-report-manifest.sha256" ]] \
  || fail "security_report_manifest must be inside security_report_dir"
for security_report in \
  semgrep.json \
  gitleaks.json \
  maven-dependency-tree.txt \
  crest-bom.json \
  pnpm-audit.json \
  frontend-licenses.json \
  license-policy.txt \
  osv-frontend.json \
  osv-maven-sbom.json; do
  grep -Eq "^[0-9a-f]{64}[[:space:]]+${security_report}$" "${security_report_manifest_path}" \
    || fail "security report manifest must include ${security_report}"
done
verify_digest_manifest_entries "${security_report_dir}" "${security_report_manifest_path}" "security report manifest"
node scripts/security-report-check.mjs "${security_report_dir}" >/dev/null

[[ "$(artifact_field_value "${github_actions_policy_report_path}" status)" == "passed" ]] \
  || fail "GitHub Actions policy report must record status=passed"
[[ "$(artifact_field_value "${github_actions_policy_report_path}" github_action_refs_sha_pinned)" == "true" ]] \
  || fail "GitHub Actions policy report must record github_action_refs_sha_pinned=true"
[[ "$(artifact_field_value "${ci_toolchain_policy_report_path}" status)" == "passed" ]] \
  || fail "CI toolchain policy report must record status=passed"
[[ "$(artifact_field_value "${ci_toolchain_policy_report_path}" centralized_ci_tool_installs)" == "true" ]] \
  || fail "CI toolchain policy report must record centralized_ci_tool_installs=true"

[[ -d "${container_report_dir}" ]] || fail "container report directory does not exist: ${container_report_dir}"
[[ "${container_report_manifest_path}" == "${container_report_dir}/container-report-manifest.sha256" ]] \
  || fail "container_report_manifest must be inside container_report_dir"
grep -Eq '^[0-9a-f]{64}[[:space:]]+trivy-.+\.json$' "${container_report_manifest_path}" \
  || fail "container report manifest must include at least one Trivy JSON report"
verify_digest_manifest_entries "${container_report_dir}" "${container_report_manifest_path}" "container report manifest"
node scripts/container-report-check.mjs "${container_report_dir}" >/dev/null

[[ "$(artifact_field_value "${container_base_image_policy_report_path}" status)" == "passed" ]] \
  || fail "container base image policy report must record status=passed"
[[ "$(artifact_field_value "${container_base_image_policy_report_path}" require_base_image_digests)" == "true" ]] \
  || fail "container base image policy report must record require_base_image_digests=true"
for digest_field in jdk_image_digest_pinned runtime_image_digest_pinned nginx_image_digest_pinned; do
  [[ "$(artifact_field_value "${container_base_image_policy_report_path}" "${digest_field}")" == "true" ]] \
    || fail "container base image policy report must record ${digest_field}=true"
done
[[ "$(artifact_field_value "${docker_build_base_image_policy_report_path}" status)" == "passed" ]] \
  || fail "Docker build base image policy report must record status=passed"
[[ "$(artifact_field_value "${docker_build_base_image_policy_report_path}" require_base_image_digests)" == "true" ]] \
  || fail "Docker build base image policy report must record require_base_image_digests=true"
for digest_field in jdk_image_digest_pinned runtime_image_digest_pinned nginx_image_digest_pinned; do
  [[ "$(artifact_field_value "${docker_build_base_image_policy_report_path}" "${digest_field}")" == "true" ]] \
    || fail "Docker build base image policy report must record ${digest_field}=true"
done

verify_external_evidence_summary "${external_evidence_summary_path}"
verify_external_redis_evidence "${verified_external_evidence_dir}"
verify_external_credential_evidence "${verified_external_evidence_dir}"
verify_production_overlay_evidence

[[ -d "${production_evidence_dir}" ]] || fail "production evidence directory does not exist: ${production_evidence_dir}"
[[ "${production_evidence_summary_dir}" == "${production_evidence_dir}" ]] \
  || fail "production_evidence_summary must be inside production_evidence_dir"
[[ "${production_evidence_manifest_name}" == "evidence-manifest.sha256" ]] \
  || fail "production evidence summary must record evidence_manifest=evidence-manifest.sha256"
[[ "${production_evidence_manifest_path}" == "${production_evidence_dir}/${production_evidence_manifest_name}" ]] \
  || fail "production_evidence_manifest must match evidence_manifest recorded in production evidence summary"
[[ "${production_evidence_summary_runtime_check}" == "passed" ]] \
  || fail "production evidence summary must record runtime_check=passed"
validate_timestamp_utc_value "${production_evidence_summary_timestamp_utc}" "production evidence summary timestamp_utc"
[[ "${production_evidence_runtime_check}" == "${production_evidence_summary_runtime_check}" ]] \
  || fail "production_evidence_runtime_check must match runtime_check in production evidence summary"
[[ "${production_evidence_summary_require_ingress_address}" == "true" ]] \
  || fail "production evidence summary must record runtime_check_require_ingress_address=true"
[[ "${production_evidence_file_count}" =~ ^[0-9]+$ ]] \
  || fail "production evidence summary evidence_file_count must be numeric"
[[ "${production_evidence_file_count}" -ge 7 ]] \
  || fail "production evidence summary evidence_file_count must be at least 7"
grep -Eq '^[0-9a-f]{64}[[:space:]]+summary\.txt$' "${production_evidence_manifest_path}" \
  || fail "production evidence manifest must include summary.txt"
grep -Eq '^[0-9a-f]{64}[[:space:]]+production-runtime-check\.txt$' "${production_evidence_manifest_path}" \
  || fail "production evidence manifest must include production-runtime-check.txt"
grep -Eq '^[0-9a-f]{64}[[:space:]]+secrets-sanitized\.json$' "${production_evidence_manifest_path}" \
  || fail "production evidence manifest must include secrets-sanitized.json"
for deployment_evidence in \
  deployment-crest.json \
  deployment-crest-service.json; do
  grep -Eq "^[0-9a-f]{64}[[:space:]]+${deployment_evidence}$" "${production_evidence_manifest_path}" \
    || fail "production evidence manifest must include ${deployment_evidence}"
done
verify_digest_manifest_entries "${production_evidence_dir}" "${production_evidence_manifest_path}" "production evidence manifest"
[[ "${digest_manifest_entry_count}" == "${production_evidence_file_count}" ]] \
  || fail "production evidence summary evidence_file_count must match production evidence manifest entry count: expected ${digest_manifest_entry_count}, got ${production_evidence_file_count}"
verify_production_runtime_output "${production_evidence_dir}/production-runtime-check.txt"
node scripts/verify-sanitized-kubernetes-secrets.mjs \
  "${production_evidence_dir}/secrets-sanitized.json" \
  crest-db-secret \
  crest-redis-secret \
  crest-tls >/dev/null
node scripts/verify-production-image-scan-coverage.mjs \
  "${production_evidence_dir}" \
  "${container_report_dir}" >/dev/null

clean_source_summary_path="$(field_value clean_source_summary)"
clean_source_generated_at_utc="$(field_value clean_source_generated_at_utc)"
clean_source_version="$(field_value clean_source_version)"
clean_source_source_branch="$(field_value clean_source_source_branch)"
clean_source_source_commit="$(field_value clean_source_source_commit)"
clean_source_archive_path="$(field_value clean_source_archive)"
clean_source_archive_digest="$(field_value clean_source_archive_sha256)"
clean_source_source_file_count="$(field_value clean_source_source_file_count)"
clean_source_secret_scan_report="$(field_value clean_source_secret_scan_report)"
clean_source_secret_scan_findings="$(field_value clean_source_secret_scan_findings)"
clean_source_worktree_dirty="$(field_value clean_source_worktree_dirty)"
clean_source_history_scan_report="$(field_value clean_source_history_scan_report)"
clean_source_history_scan_report_sha256="$(field_value clean_source_history_scan_report_sha256)"
clean_source_history_findings_remaining="$(field_value clean_source_history_findings_remaining)"
clean_source_history_delivery_path="$(field_value clean_source_history_delivery_path)"
clean_source_credential_rotation_status="$(field_value clean_source_credential_rotation_status)"
clean_source_affected_credential_classes="$(field_value clean_source_affected_credential_classes)"
clean_source_secret_scan_report_findings="$(json_array_count "${clean_source_secret_scan_report}")" \
  || fail "clean source secret scan report must be a JSON array"

validate_timestamp_utc_value "${clean_source_generated_at_utc}" "clean source generated_at_utc"
require_clean_source_summary_field archive "${clean_source_archive_path}" "clean source archive path"
require_clean_source_summary_field archive_sha256 "${clean_source_archive_digest}" "clean source archive digest"
require_clean_source_summary_field generated_at_utc "${clean_source_generated_at_utc}" "clean source generated timestamp"
require_clean_source_summary_field version "${clean_source_version}" "clean source version"
require_clean_source_summary_field source_branch "${clean_source_source_branch}" "clean source source branch"
require_clean_source_summary_field source_commit "${clean_source_source_commit}" "clean source source commit"
require_clean_source_summary_field source_file_count "${clean_source_source_file_count}" "clean source source file count"
require_clean_source_summary_field secret_scan_report "${clean_source_secret_scan_report}" "clean source secret scan report path"
require_clean_source_summary_field secret_scan_findings "${clean_source_secret_scan_findings}" "clean source secret scan finding count"
require_clean_source_summary_field source_worktree_dirty "${clean_source_worktree_dirty}" "clean source worktree dirty flag"
require_clean_source_summary_field history_scan_report "${clean_source_history_scan_report}" "clean source history scan report path"
require_clean_source_summary_field history_scan_report_sha256 "${clean_source_history_scan_report_sha256}" "clean source history scan report digest"
require_clean_source_summary_field history_findings_remaining "${clean_source_history_findings_remaining}" "clean source history finding count"
require_clean_source_summary_field history_delivery_path "${clean_source_history_delivery_path}" "clean source history delivery path"
require_clean_source_summary_field credential_rotation_status "${clean_source_credential_rotation_status}" "clean source credential rotation status"
require_clean_source_summary_field affected_credential_classes "${clean_source_affected_credential_classes}" "clean source affected credential classes"
[[ "${clean_source_secret_scan_report_findings}" == "0" ]] \
  || fail "clean source secret scan report must contain 0 findings, got ${clean_source_secret_scan_report_findings}"
verify_clean_source_archive "${clean_source_archive_path}"

history_secret_evidence="$(field_value history_secret_evidence)"
history_secret_scan_report="$(field_value history_secret_scan_report)"
history_secret_scan_report_sha256="$(field_value history_secret_scan_report_sha256)"
history_secret_findings_remaining="$(field_value history_secret_findings_remaining)"
history_secret_affected_credential_classes="$(field_value history_secret_affected_credential_classes)"
history_secret_credential_rotation_status="$(field_value history_secret_credential_rotation_status)"
history_secret_delivery_path="$(field_value history_secret_delivery_path)"
history_secret_rotation_evidence_id="$(field_value history_secret_rotation_evidence_id)"
history_secret_approved_by="$(field_value history_secret_approved_by)"
history_secret_approval_date="$(field_value history_secret_approval_date)"

[[ "${history_secret_evidence}" == "passed" ]] \
  || fail "readiness summary must record history_secret_evidence=passed from credential-rotation.md"
[[ "${history_secret_findings_remaining}" =~ ^[0-9]+$ ]] \
  || fail "history_secret_findings_remaining must be a non-negative integer"
[[ -n "${history_secret_affected_credential_classes}" ]] \
  || fail "history_secret_affected_credential_classes must be recorded from credential-rotation.md"
case "${history_secret_credential_rotation_status}" in
  rotated-before-delivery|not-applicable-clean-history)
    ;;
  *)
    fail "history_secret_credential_rotation_status must be rotated-before-delivery or not-applicable-clean-history"
    ;;
esac
case "${history_secret_delivery_path}" in
  clean-source|clean-history|fresh-repository)
    ;;
  *)
    fail "history_secret_delivery_path must be clean-source, clean-history or fresh-repository"
    ;;
esac
[[ -n "${history_secret_rotation_evidence_id}" ]] \
  || fail "history_secret_rotation_evidence_id must be recorded from credential-rotation.md"
[[ -n "${history_secret_approved_by}" ]] \
  || fail "history_secret_approved_by must be recorded from credential-rotation.md"
validate_date_value "${history_secret_approval_date}" "history_secret_approval_date"
if [[ "${history_secret_delivery_path}" == "clean-history" && "${history_secret_findings_remaining}" != "0" ]]; then
  fail "clean-history delivery requires history_secret_findings_remaining=0"
fi
if [[ "${history_secret_delivery_path}" != "clean-history" && "${history_secret_credential_rotation_status}" != "rotated-before-delivery" ]]; then
  fail "${history_secret_delivery_path} delivery requires history_secret_credential_rotation_status=rotated-before-delivery"
fi
if [[ "${history_secret_findings_remaining}" != "0" ]]; then
  case "${history_secret_affected_credential_classes}" in
    not-recorded|unknown|none|n/a|N/A)
      fail "history_secret_affected_credential_classes must list rotated credential classes when history findings remain"
      ;;
  esac
  case "${history_secret_rotation_evidence_id}" in
    not-recorded|unknown|none|n/a|N/A)
      fail "history_secret_rotation_evidence_id must reference the rotation ticket or audit record"
      ;;
  esac
fi
case "${history_secret_approved_by}" in
  not-recorded|unknown|none|n/a|N/A)
    fail "history_secret_approved_by must name the approving team or reviewer"
    ;;
esac
if [[ "${clean_source_history_findings_remaining}" != "${history_secret_findings_remaining}" ]]; then
  fail "clean_source_history_findings_remaining must match history_secret_findings_remaining"
fi
if [[ "${clean_source_history_scan_report_sha256}" != "${history_secret_scan_report_sha256}" ]]; then
  fail "clean_source_history_scan_report_sha256 must match history_secret_scan_report_sha256"
fi
clean_source_history_scan_report_findings="$(json_array_count "${clean_source_history_scan_report}")" \
  || fail "clean source history scan report must be a JSON array"
if [[ "${clean_source_history_scan_report_findings}" != "${history_secret_findings_remaining}" ]]; then
  fail "clean source history scan report finding count must match history_secret_findings_remaining"
fi
history_secret_scan_report_findings="$(json_array_count "${history_secret_scan_report}")" \
  || fail "history secret scan report must be a JSON array"
if [[ "${history_secret_scan_report_findings}" != "${history_secret_findings_remaining}" ]]; then
  fail "history secret scan report finding count must match history_secret_findings_remaining"
fi
if [[ "${clean_source_history_delivery_path}" != "${history_secret_delivery_path}" ]]; then
  fail "clean_source_history_delivery_path must match history_secret_delivery_path"
fi
if [[ "${clean_source_credential_rotation_status}" != "${history_secret_credential_rotation_status}" ]]; then
  fail "clean_source_credential_rotation_status must match history_secret_credential_rotation_status"
fi
if [[ "${clean_source_affected_credential_classes}" != "${history_secret_affected_credential_classes}" ]]; then
  fail "clean_source_affected_credential_classes must match history_secret_affected_credential_classes"
fi

for gate in \
  github-actions-policy \
  ci-toolchain-policy \
  quality \
  security \
  docker-environment \
  docker-build \
  container-scan \
  kind-smoke \
  clean-source-release \
  production-evidence-bundle \
  external-production-evidence; do
  require_line "^${gate}: passed([[:space:]]|$)" "${gate}: passed"
done

require_line '^production-overlay-(render|check): passed([[:space:]]|$)' 'production overlay render/check passed'

echo "production-go-no-go-summary-check: passed"
