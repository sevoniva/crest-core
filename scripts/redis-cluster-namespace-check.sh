#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "redis-cluster-namespace-check: $*" >&2
  exit 1
}

info() {
  echo "redis-cluster-namespace-check: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "${repo_root}"

redis_cli="${CREST_REDIS_CLI:-redis-cli}"
report_file="${CREST_REDIS_NAMESPACE_REPORT:-reports/readiness/redis-namespace-check.txt}"

cluster_nodes="${CREST_REDIS_CLUSTER_NODES:-}"
redis_database="${CREST_REDIS_DATABASE:-0}"
redis_username="${CREST_REDIS_USERNAME:-}"
redis_password="${CREST_REDIS_PASSWORD:-}"
redis_ssl_enabled="${CREST_REDIS_SSL_ENABLED:-false}"
redis_ca_cert="${CREST_REDIS_CA_CERT:-}"
key_prefix="${CREST_REDIS_KEY_PREFIX:-}"

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
  if [[ -e "${logical}" ]]; then
    [[ -d "${logical}" ]] || fail "path parent must be a directory: ${path}"
    (cd "${logical}" && pwd -P)
    return
  fi
  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  ancestor="${parent}"
  while [[ ! -e "${ancestor}" && "${ancestor}" != "/" ]]; do
    ancestor="$(dirname "${ancestor}")"
  done
  [[ -d "${ancestor}" ]] || fail "path parent must be a directory: ${path}"
  ancestor_real="$(cd "${ancestor}" && pwd -P)"
  suffix="${parent#"${ancestor}"}"
  printf '%s%s/%s' "${ancestor_real}" "${suffix}" "${base}"
}

normalize_file_path() {
  local path="$1"
  local logical parent base normalized_parent
  logical="$(resolve_lexical_path "${path}")"
  if [[ -e "${logical}" || -L "${logical}" ]]; then
    [[ ! -L "${logical}" ]] || fail "CREST_REDIS_NAMESPACE_REPORT must not be a symlink: ${path}"
    [[ ! -d "${logical}" ]] || fail "CREST_REDIS_NAMESPACE_REPORT must be a file path: ${path}"
    (cd "$(dirname "${logical}")" && printf '%s/%s' "$(pwd -P)" "$(basename "${logical}")")
    return
  fi
  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  normalized_parent="$(normalize_dir_path "${parent}")"
  printf '%s/%s' "${normalized_parent}" "${base}"
}

assert_safe_report_file() {
  local path="$1"
  local base normalized
  [[ -n "${path}" ]] || fail "CREST_REDIS_NAMESPACE_REPORT must not be empty"
  case "${path}" in
    /|.|..|*/)
      fail "CREST_REDIS_NAMESPACE_REPORT is too broad to overwrite: ${path}"
      ;;
  esac
  base="$(basename "${path}")"
  case "${base}" in
    ""|.|..)
      fail "CREST_REDIS_NAMESPACE_REPORT must be a file path: ${path}"
      ;;
  esac
  normalized="$(normalize_file_path "${path}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_REDIS_NAMESPACE_REPORT is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_REDIS_NAMESPACE_REPORT must stay inside the repository: ${path}"
      ;;
  esac
  [[ ! -d "${normalized}" ]] || fail "CREST_REDIS_NAMESPACE_REPORT must be a file path: ${path}"
  printf '%s' "${normalized}"
}

reserved_hash_tag() {
  case "$1" in
    app|application|cache|crest|crest-core|dataease|default|prod|production|redis|shared|system)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

example_hash_tag() {
  case "$1" in
    acme-crest-core-prod|*changeme*|*change-me*|*example*|*sample*|*demo*|*template*|*placeholder*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

reserved_acl_user() {
  case "$1" in
    app|application|cache|crest|crest-core|dataease|default|prod|production|redis|shared|system)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

example_acl_user() {
  case "$1" in
    acme-crest-production-acl-user|*changeme*|*change-me*|*example*|*sample*|*demo*|*template*|*placeholder*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

report_file="$(assert_safe_report_file "${report_file}")"

validate_acl_user() {
  local value="$1"
  [[ "${value}" != "default" ]] || fail "CREST_REDIS_USERNAME must not be default for shared Redis"
  [[ "${value}" =~ ^[a-z0-9][a-z0-9._-]{7,63}$ ]] \
    || fail "CREST_REDIS_USERNAME must be an 8-64 character lowercase environment-specific ACL user"
  if reserved_acl_user "${value}"; then
    fail "CREST_REDIS_USERNAME is too generic for shared Redis"
  fi
  if example_acl_user "${value}"; then
    fail "CREST_REDIS_USERNAME looks like an example value; replace it with a real organization/environment ACL user"
  fi
}

redis_hash_tag() {
  local value="$1"
  local name="$2"
  [[ "${value}" =~ ^\{([a-z0-9][a-z0-9._-]{7,63})\}:[a-z0-9:_\.-]+$ ]] \
    || fail "${name} must start with a Redis Cluster hash tag like {<org>-<env>-crest-core}:prod"
  if reserved_hash_tag "${BASH_REMATCH[1]}"; then
    fail "${name} hash tag is too generic for shared Redis"
  fi
  if example_hash_tag "${BASH_REMATCH[1]}"; then
    fail "${name} hash tag looks like an example value; replace it with a real organization/environment namespace"
  fi
  printf '%s' "${BASH_REMATCH[1]}"
}

require_same_hash_tag() {
  local name="$1"
  local value="$2"
  local expected_hash_tag="$3"
  local actual_hash_tag
  [[ -n "${value}" ]] || fail "${name} is required"
  actual_hash_tag="$(redis_hash_tag "${value}" "${name}")"
  [[ "${actual_hash_tag}" == "${expected_hash_tag}" ]] \
    || fail "${name} must use the same Redis Cluster hash tag {${expected_hash_tag}}"
}

normalize_first_node() {
  local nodes="$1"
  local first_node host port
  [[ -n "${nodes}" ]] || fail "CREST_REDIS_CLUSTER_NODES is required"
  first_node="${nodes%%,*}"
  host="${first_node%:*}"
  port="${first_node##*:}"
  [[ -n "${host}" && -n "${port}" && "${host}" != "${port}" && "${port}" =~ ^[0-9]+$ ]] \
    || fail "CREST_REDIS_CLUSTER_NODES must contain host:port entries"
  printf '%s:%s' "${host}" "${port}"
}

count_nodes() {
  local nodes="$1"
  awk -F, '{ print NF }' <<< "${nodes}"
}

validate_cluster_nodes() {
  local nodes="$1"
  local entry host port
  local seen_nodes=""
  IFS=',' read -r -a node_entries <<< "${nodes}"
  for entry in "${node_entries[@]}"; do
    [[ -n "${entry}" ]] || fail "CREST_REDIS_CLUSTER_NODES must not contain empty entries"
    [[ "${entry}" != *[[:space:]]* ]] || fail "CREST_REDIS_CLUSTER_NODES entries must not contain whitespace"
    host="${entry%:*}"
    port="${entry##*:}"
    [[ -n "${host}" && -n "${port}" && "${host}" != "${port}" && "${port}" =~ ^[0-9]+$ ]] \
      || fail "CREST_REDIS_CLUSTER_NODES must contain host:port entries"
    [[ "${host}" != *:* ]] || fail "CREST_REDIS_CLUSTER_NODES must use host:port entries without raw IPv6 literals"
    if grep -qxF "${entry}" <<< "${seen_nodes}"; then
      fail "CREST_REDIS_CLUSTER_NODES must not contain duplicate entries"
    fi
    seen_nodes="${seen_nodes}${entry}"$'\n'
  done
}

write_report() {
  local status="$1"
  local message="${2:-}"
  mkdir -p "$(dirname "${report_file}")"
  {
    echo "status=${status}"
    echo "redis_cluster_nodes_count=${node_count:-0}"
    echo "redis_node=${first_node:-}"
    echo "redis_key_prefix=${key_prefix:-}"
    echo "redis_hash_tag=${hash_tag:-}"
    echo "redis_acl_user=${redis_username:-}"
    echo "redis_acl_key_isolation=${acl_key_isolation:-unknown}"
    echo "redis_acl_stream_isolation=${acl_stream_isolation:-unknown}"
    echo "redis_acl_channel_isolation=${acl_channel_isolation:-unknown}"
    echo "redis_acl_denied_key=${acl_denied_key:-}"
    echo "redis_acl_denied_stream=${acl_denied_stream:-}"
    echo "redis_acl_denied_channel=${acl_denied_channel:-}"
    echo "redis_probe_key=${probe_key:-}"
    echo "redis_probe_stream=${probe_stream:-}"
    if [[ -n "${message}" ]]; then
      echo "message=${message}"
    fi
  } > "${report_file}"
}

[[ "${redis_database}" == "0" ]] || fail "CREST_REDIS_DATABASE must be 0 for Redis Cluster"
[[ -n "${redis_username}" ]] || fail "CREST_REDIS_USERNAME is required for shared Redis ACL isolation"
validate_acl_user "${redis_username}"
[[ -n "${redis_password}" ]] || fail "CREST_REDIS_PASSWORD is required"

hash_tag="$(redis_hash_tag "${key_prefix}" "CREST_REDIS_KEY_PREFIX")"
first_node="$(normalize_first_node "${cluster_nodes}")"
node_count="$(count_nodes "${cluster_nodes}")"
[[ "${node_count}" -ge 3 ]] || fail "CREST_REDIS_CLUSTER_NODES must contain at least 3 nodes"
validate_cluster_nodes "${cluster_nodes}"

require_same_hash_tag CREST_REDIS_CACHE_KEY_PREFIX "${CREST_REDIS_CACHE_KEY_PREFIX:-}" "${hash_tag}"
require_same_hash_tag CREST_LOCK_KEY_PREFIX "${CREST_LOCK_KEY_PREFIX:-}" "${hash_tag}"
require_same_hash_tag CREST_WEBSOCKET_BROADCAST_CHANNEL "${CREST_WEBSOCKET_BROADCAST_CHANNEL:-}" "${hash_tag}"
require_same_hash_tag CREST_EXPORT_TASK_STREAM "${CREST_EXPORT_TASK_STREAM:-}" "${hash_tag}"
require_same_hash_tag CREST_EXPORT_TASK_CONSUMER_GROUP "${CREST_EXPORT_TASK_CONSUMER_GROUP:-}" "${hash_tag}"
require_same_hash_tag CREST_SYNC_TASK_STREAM "${CREST_SYNC_TASK_STREAM:-}" "${hash_tag}"
require_same_hash_tag CREST_SYNC_TASK_CONSUMER_GROUP "${CREST_SYNC_TASK_CONSUMER_GROUP:-}" "${hash_tag}"
require_same_hash_tag CREST_DATASOURCE_SYNC_TASK_STREAM "${CREST_DATASOURCE_SYNC_TASK_STREAM:-}" "${hash_tag}"
require_same_hash_tag CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP "${CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP:-}" "${hash_tag}"
require_same_hash_tag CREST_SCHEDULED_TASK_STREAM "${CREST_SCHEDULED_TASK_STREAM:-}" "${hash_tag}"
require_same_hash_tag CREST_SCHEDULED_TASK_CONSUMER_GROUP "${CREST_SCHEDULED_TASK_CONSUMER_GROUP:-}" "${hash_tag}"

require_cmd "${redis_cli}"

host="${first_node%:*}"
port="${first_node##*:}"
redis_args=(-c --no-auth-warning -h "${host}" -p "${port}" --user "${redis_username}")
if [[ "${redis_ssl_enabled}" == "true" ]]; then
  redis_args+=(--tls)
fi
if [[ -n "${redis_ca_cert}" ]]; then
  redis_args+=(--cacert "${redis_ca_cert}")
fi

redis() {
  REDISCLI_AUTH="${redis_password}" "${redis_cli}" "${redis_args[@]}" "$@"
}

probe_id="${CREST_REDIS_NAMESPACE_PROBE_ID:-$(date +%s)-$$}"
probe_key="${key_prefix}:probe:key:${probe_id}"
probe_stream="${key_prefix}:probe:stream:${probe_id}"
probe_group="${key_prefix}:probe:group:${probe_id}"
probe_consumer="probe-${probe_id}"
probe_channel="${key_prefix}:probe:channel:${probe_id}"
acl_denied_key="unscoped-crest-acl-probe:key:${probe_id}"
acl_denied_stream="unscoped-crest-acl-probe:stream:${probe_id}"
acl_denied_channel="unscoped-crest-acl-probe:channel:${probe_id}"

cleanup() {
  redis DEL "${probe_key}" "${probe_stream}" "${acl_denied_key}" "${acl_denied_stream}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

require_acl_denied() {
  local description="$1"
  shift
  local output status normalized
  set +e
  output="$(redis "$@" 2>&1)"
  status=$?
  set -e
  normalized="$(printf '%s' "${output}" | tr '[:upper:]' '[:lower:]')"
  if [[ "${normalized}" == *noperm* || "${normalized}" == *permission* || "${normalized}" == *"not authorized"* || "${normalized}" == *acl* ]]; then
    return 0
  fi
  if [[ "${status}" -eq 0 ]]; then
    write_report failed "${description} was unexpectedly allowed by Redis ACL"
    fail "${description} was unexpectedly allowed by Redis ACL"
  fi
  write_report failed "${description} returned an unexpected Redis error: ${output}"
  fail "${description} returned an unexpected Redis error"
}

if [[ "$(redis PING)" != "PONG" ]]; then
  write_report failed "PING failed"
  fail "Redis PING failed"
fi

if ! redis CLUSTER INFO | grep -q "cluster_state:ok"; then
  write_report failed "cluster_state is not ok"
  fail "Redis Cluster state is not ok"
fi
redis CLUSTER SLOTS >/dev/null

expected_slot="$(redis CLUSTER KEYSLOT "${key_prefix}:slot-check")"
for scoped_value in \
  "${CREST_REDIS_CACHE_KEY_PREFIX}" \
  "${CREST_LOCK_KEY_PREFIX}" \
  "${CREST_WEBSOCKET_BROADCAST_CHANNEL}" \
  "${CREST_EXPORT_TASK_STREAM}" \
  "${CREST_SYNC_TASK_STREAM}" \
  "${CREST_DATASOURCE_SYNC_TASK_STREAM}" \
  "${CREST_SCHEDULED_TASK_STREAM}"; do
  slot="$(redis CLUSTER KEYSLOT "${scoped_value}:slot-check")"
  [[ "${slot}" == "${expected_slot}" ]] || fail "${scoped_value} resolves to slot ${slot}, expected ${expected_slot}"
done

[[ "$(redis SET "${probe_key}" "ok" EX 60 NX)" == "OK" ]] || fail "probe SET failed"
[[ "$(redis GET "${probe_key}")" == "ok" ]] || fail "probe GET failed"
[[ "$(redis PUBLISH "${probe_channel}" "ok")" =~ ^[0-9]+$ ]] || fail "probe PUBLISH failed"
redis XGROUP CREATE "${probe_stream}" "${probe_group}" "0" MKSTREAM >/dev/null
xadd_id="$(redis XADD "${probe_stream}" "*" type probe value ok)"
[[ -n "${xadd_id}" ]] || fail "probe XADD did not return a record id"
redis XREADGROUP GROUP "${probe_group}" "${probe_consumer}" COUNT 1 STREAMS "${probe_stream}" ">" >/dev/null
[[ "$(redis XACK "${probe_stream}" "${probe_group}" "${xadd_id}")" == "1" ]] || fail "probe XACK failed"
require_acl_denied "unscoped Redis key write" SET "${acl_denied_key}" "blocked" EX 60 NX
acl_key_isolation="passed"
require_acl_denied "unscoped Redis stream write" XADD "${acl_denied_stream}" "*" type probe value blocked
acl_stream_isolation="passed"
require_acl_denied "unscoped Redis channel publish" PUBLISH "${acl_denied_channel}" "blocked"
acl_channel_isolation="passed"
redis DEL "${probe_key}" "${probe_stream}" >/dev/null
trap - EXIT

write_report passed
info "passed; report written to ${report_file}"
