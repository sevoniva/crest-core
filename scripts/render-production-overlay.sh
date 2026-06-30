#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "render-production-overlay: $*" >&2
  exit 1
}

info() {
  echo "render-production-overlay: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

require_value() {
  local name="$1"
  local value="${!name:-}"
  [[ -n "${value}" ]] || fail "${name} is required"
  [[ "${value}" != *$'\n'* && "${value}" != *$'\r'* ]] || fail "${name} must be a single-line value"
}

redis_hash_tag() {
  local value="$1"
  [[ "${value}" != *"<"* && "${value}" != *">"* && "${value}" != *CHANGE_ME* ]] \
    || fail "CREST_REDIS_KEY_PREFIX must replace the template value with an organization/environment-specific namespace"
  [[ "${value}" =~ ^\{([a-z0-9][a-z0-9._-]{7,63})\}:[a-z0-9][a-z0-9._-]*$ ]] \
    || fail "CREST_REDIS_KEY_PREFIX must look like {<org>-<env>-crest-core}:prod"
  printf '%s' "${BASH_REMATCH[1]}"
}

validate_redis_hash_tag() {
  local tag="$1"
  case "${tag}" in
    app|application|cache|crest|crest-core|dataease|default|prod|production|redis|shared|system)
      fail "CREST_REDIS_KEY_PREFIX hash tag is too generic for shared Redis; use an environment-specific namespace"
      ;;
  esac
  case "${tag}" in
    acme-crest-core-prod|*changeme*|*change-me*|*example*|*sample*|*demo*|*template*|*placeholder*)
      fail "CREST_REDIS_KEY_PREFIX hash tag looks like an example value; replace it with a real organization/environment namespace"
      ;;
  esac
}

validate_redis_acl_user() {
  local value="$1"
  [[ "${value}" != "default" ]] || fail "CREST_REDIS_USERNAME must not be default for shared Redis"
  [[ "${value}" =~ ^[a-z0-9][a-z0-9._-]{7,63}$ ]] \
    || fail "CREST_REDIS_USERNAME must be an 8-64 character lowercase environment-specific ACL user"
  case "${value}" in
    app|application|cache|crest|crest-core|dataease|prod|production|redis|shared|system)
      fail "CREST_REDIS_USERNAME is too generic for shared Redis"
      ;;
  esac
  case "${value}" in
    acme-crest-production-acl-user|*changeme*|*change-me*|*example*|*sample*|*demo*|*template*|*placeholder*)
      fail "CREST_REDIS_USERNAME looks like an example value; replace it with a real organization/environment ACL user"
      ;;
  esac
}

yaml_quote() {
  local value="$1"
  printf "'%s'" "$(printf '%s' "${value}" | sed "s/'/''/g")"
}

replace_image() {
  local file="$1"
  local image_name="$2"
  local to="$3"
  CREST_RENDER_IMAGE_NAME="${image_name}" CREST_RENDER_TO="${to}" perl -0pi -e '
    my $name = quotemeta($ENV{CREST_RENDER_IMAGE_NAME});
    s/image: $name(?::\S+|@sha256:[0-9a-fA-F]{64})/image: $ENV{CREST_RENDER_TO}/g;
  ' "${file}"
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

normalize_path() {
  local path="$1"
  local logical parent base ancestor suffix ancestor_real
  logical="$(resolve_lexical_path "${path}")"
  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  ancestor="${parent}"
  while [[ ! -e "${ancestor}" && "${ancestor}" != "/" ]]; do
    ancestor="$(dirname "${ancestor}")"
  done
  [[ -d "${ancestor}" ]] || fail "CREST_PRODUCTION_OVERLAY_DIR parent path is not a directory: ${path}"
  ancestor_real="$(cd "${ancestor}" && pwd -P)"
  suffix="${parent#"${ancestor}"}"
  printf '%s%s/%s' "${ancestor_real}" "${suffix}" "${base}"
}

assert_safe_overlay_dir() {
  local path="$1"
  local normalized base_normalized
  [[ -n "${path}" ]] || fail "CREST_PRODUCTION_OVERLAY_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_PRODUCTION_OVERLAY_DIR is too broad to overwrite: ${path}"
      ;;
  esac
  normalized="$(normalize_path "${path}")"
  base_normalized="$(normalize_path "${base_dir}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.local"|"${repo_root}/reports"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*|\
    "${base_normalized}"|"${base_normalized}/"*)
      fail "CREST_PRODUCTION_OVERLAY_DIR is too broad to overwrite: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_PRODUCTION_OVERLAY_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${normalized}"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

require_cmd kubectl
require_cmd node
require_cmd perl
require_cmd sed

base_dir="${CREST_K8S_BASE_DIR:-deploy/kubernetes}"
overlay_dir="${CREST_PRODUCTION_OVERLAY_DIR:-.local/production-overlay}"
overlay_dir="$(assert_safe_overlay_dir "${overlay_dir}")"
version="$(tr -d '[:space:]' < VERSION)"

require_value CREST_PRODUCTION_HOST
require_value CREST_DB_HOST
require_value CREST_DB_USERNAME
require_value CREST_DB_PASSWORD
require_value CREST_AES_KEY
require_value CREST_AES_IV
require_value CREST_INITIAL_PASSWORD
require_value CREST_TOKEN_SECRET
require_value CREST_REDIS_CLUSTER_NODES
require_value CREST_REDIS_USERNAME
require_value CREST_REDIS_PASSWORD
require_value CREST_REDIS_KEY_PREFIX
require_value CREST_DATA_STORAGE_CLASS

db_port="${CREST_DB_PORT:-2883}"
db_url="${CREST_DB_URL:-jdbc:oceanbase://${CREST_DB_HOST}:${db_port}}"
origin_list="${CREST_ORIGIN_LIST:-https://${CREST_PRODUCTION_HOST}}"
redis_prefix="${CREST_REDIS_KEY_PREFIX}"
ingress_class="${CREST_INGRESS_CLASS_NAME:-nginx}"
data_storage_size="${CREST_DATA_STORAGE_SIZE:-50Gi}"
data_storage_class="${CREST_DATA_STORAGE_CLASS}"
backend_image="${CREST_BACKEND_IMAGE:-ghcr.io/sevoniva/crest-core-service:v${version}}"
frontend_image="${CREST_FRONTEND_IMAGE:-ghcr.io/sevoniva/crest-core-web:v${version}}"
prometheus_enabled="${CREST_PROMETHEUS_ENABLED:-false}"

[[ "${#CREST_AES_KEY}" -eq 32 ]] || fail "CREST_AES_KEY must be exactly 32 characters"
[[ "${#CREST_AES_IV}" -eq 16 ]] || fail "CREST_AES_IV must be exactly 16 characters"
[[ "${#CREST_DB_PASSWORD}" -ge 12 ]] || fail "CREST_DB_PASSWORD must be at least 12 characters"
[[ "${#CREST_INITIAL_PASSWORD}" -ge 12 ]] || fail "CREST_INITIAL_PASSWORD must be at least 12 characters"
[[ "${#CREST_TOKEN_SECRET}" -ge 32 ]] || fail "CREST_TOKEN_SECRET must be at least 32 characters"
[[ "${#CREST_REDIS_PASSWORD}" -ge 12 ]] || fail "CREST_REDIS_PASSWORD must be at least 12 characters"
validate_redis_acl_user "${CREST_REDIS_USERNAME}"
case "${prometheus_enabled}" in
  true)
    require_value CREST_PROMETHEUS_TOKEN
    [[ "${#CREST_PROMETHEUS_TOKEN}" -ge 32 ]] || fail "CREST_PROMETHEUS_TOKEN must be at least 32 characters when CREST_PROMETHEUS_ENABLED=true"
    ;;
  false)
    ;;
  *)
    fail "CREST_PROMETHEUS_ENABLED must be true or false"
    ;;
esac
redis_hash_tag_value="$(redis_hash_tag "${redis_prefix}")" || exit 1
validate_redis_hash_tag "${redis_hash_tag_value}"
[[ "${backend_image}" == *@sha256:* || "${backend_image}" =~ :v[0-9]+\.[0-9]+\.[0-9]+$ || "${backend_image}" =~ :sha-[0-9a-f]{7,40}$ ]] \
  || fail "CREST_BACKEND_IMAGE must use a digest, vX.Y.Z tag, or sha-<commit> tag"
[[ "${frontend_image}" == *@sha256:* || "${frontend_image}" =~ :v[0-9]+\.[0-9]+\.[0-9]+$ || "${frontend_image}" =~ :sha-[0-9a-f]{7,40}$ ]] \
  || fail "CREST_FRONTEND_IMAGE must use a digest, vX.Y.Z tag, or sha-<commit> tag"

rm -rf "${overlay_dir}"
mkdir -p "${overlay_dir}"
find "${base_dir}" -maxdepth 1 -type f -name '*.yaml' -exec cp {} "${overlay_dir}/" \;

cat > "${overlay_dir}/00-crest-env-configmap.yaml" <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: crest-env
data:
  CREST_ORIGIN_LIST: $(yaml_quote "${origin_list}")
  CREST_PRODUCTION_MODE: "true"
  SPRING_PROFILES_ACTIVE: "standalone"
  SPRING_QUARTZ_JDBC_INITIALIZE_SCHEMA: "never"
  CREST_QUARTZ_INSTANCE_ID: "AUTO"
  CREST_QUARTZ_CLUSTERED: "true"
  CREST_QUARTZ_CLUSTER_CHECKIN_INTERVAL: "10000"
  CREST_QUARTZ_MISFIRE_THRESHOLD: "60000"
  CREST_SHUTDOWN_TIMEOUT: "45s"
  CREST_LOAD_DEMO: "false"
  CREST_FLYWAY_ENABLED: "false"
  CREST_INTERNAL_LITE_ENABLED: "true"
  CREST_API_DOCS_ENABLED: "false"
  CREST_KNIFE4J_ENABLED: "false"
  CREST_FEATURE_AI_ENABLED: "false"
  CREST_FEATURE_SQLBOT_ENABLED: "false"
  CREST_FEATURE_TEMPLATE_MARKET_ENABLED: "false"
  CREST_FEATURE_FONT_MANAGEMENT_ENABLED: "false"
  CREST_FEATURE_VISUALIZATION_BACKGROUND_ENABLED: "false"
  CREST_DB_TYPE: "ob-oracle"
  CREST_DB_DRIVER_CLASS_NAME: "com.oceanbase.jdbc.Driver"
  CREST_DB_HOST: $(yaml_quote "${CREST_DB_HOST}")
  CREST_DB_PORT: $(yaml_quote "${db_port}")
  CREST_DB_URL: $(yaml_quote "${db_url}")
  CREST_ALLOWED_DATASOURCE_TYPES: "obOracle,Excel,ExcelRemote,API"
  CREST_REDIS_CLUSTER_NODES: $(yaml_quote "${CREST_REDIS_CLUSTER_NODES}")
  CREST_REDIS_CLUSTER_MAX_REDIRECTS: "5"
  CREST_REDIS_CLUSTER_REFRESH_ADAPTIVE: "true"
  CREST_REDIS_CLUSTER_REFRESH_PERIOD: "30s"
  CREST_REDIS_CLUSTER_DYNAMIC_REFRESH_SOURCES: "true"
  CREST_REDIS_SSL_ENABLED: "${CREST_REDIS_SSL_ENABLED:-false}"
  CREST_REDIS_DATABASE: "0"
  CREST_REDIS_CONNECT_TIMEOUT: "2s"
  CREST_REDIS_TIMEOUT: "5s"
  CREST_REDIS_KEY_PREFIX: $(yaml_quote "${redis_prefix}")
  CREST_REDIS_CACHE_KEY_PREFIX: $(yaml_quote "${redis_prefix}:cache:")
  CREST_LOCK_KEY_PREFIX: $(yaml_quote "${redis_prefix}:lock")
  CREST_TASK_QUEUE_ENABLED: "true"
  CREST_WEBSOCKET_BROADCAST_ENABLED: "true"
  CREST_WEBSOCKET_BROADCAST_CHANNEL: $(yaml_quote "${redis_prefix}:pubsub:websocket")
  CREST_EXPORT_TASK_STREAM: $(yaml_quote "${redis_prefix}:stream:export-task")
  CREST_EXPORT_TASK_CONSUMER_GROUP: $(yaml_quote "${redis_prefix}:group:export-workers")
  CREST_SYNC_TASK_STREAM: $(yaml_quote "${redis_prefix}:stream:dataset-sync-task")
  CREST_SYNC_TASK_CONSUMER_GROUP: $(yaml_quote "${redis_prefix}:group:dataset-sync-workers")
  CREST_DATASOURCE_SYNC_TASK_STREAM: $(yaml_quote "${redis_prefix}:stream:datasource-sync-task")
  CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP: $(yaml_quote "${redis_prefix}:group:datasource-sync-workers")
  CREST_SCHEDULED_TASK_STREAM: $(yaml_quote "${redis_prefix}:stream:scheduled-task")
  CREST_SCHEDULED_TASK_CONSUMER_GROUP: $(yaml_quote "${redis_prefix}:group:scheduled-workers")
  CREST_HEALTH_REDIS_ENABLED: "true"
  CREST_DATASOURCE_POOL_PRELOAD_ENABLED: "false"
  CREST_PROMETHEUS_ENABLED: "${prometheus_enabled}"
EOF

cat > "${overlay_dir}/01-crest-db-secret.yaml" <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: crest-db-secret
type: Opaque
stringData:
  CREST_DB_USERNAME: $(yaml_quote "${CREST_DB_USERNAME}")
  CREST_DB_PASSWORD: $(yaml_quote "${CREST_DB_PASSWORD}")
  CREST_AES_KEY: $(yaml_quote "${CREST_AES_KEY}")
  CREST_AES_IV: $(yaml_quote "${CREST_AES_IV}")
  CREST_INITIAL_PASSWORD: $(yaml_quote "${CREST_INITIAL_PASSWORD}")
  CREST_TOKEN_SECRET: $(yaml_quote "${CREST_TOKEN_SECRET}")
EOF
if [[ "${prometheus_enabled}" == "true" ]]; then
  echo "  CREST_PROMETHEUS_TOKEN: $(yaml_quote "${CREST_PROMETHEUS_TOKEN}")" >> "${overlay_dir}/01-crest-db-secret.yaml"
fi

cat > "${overlay_dir}/02-crest-redis-secret.yaml" <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: crest-redis-secret
type: Opaque
stringData:
  CREST_REDIS_USERNAME: $(yaml_quote "${CREST_REDIS_USERNAME}")
  CREST_REDIS_PASSWORD: $(yaml_quote "${CREST_REDIS_PASSWORD}")
EOF

{
  cat <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: crest-data
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: $(yaml_quote "${data_storage_size}")
EOF
  if [[ -n "${data_storage_class}" ]]; then
    echo "  storageClassName: $(yaml_quote "${data_storage_class}")"
  fi
} > "${overlay_dir}/03-crest-data-pvc.yaml"

cat > "${overlay_dir}/13-crest-ingress.yaml" <<EOF
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: crest
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
spec:
  ingressClassName: $(yaml_quote "${ingress_class}")
  tls:
    - hosts:
        - $(yaml_quote "${CREST_PRODUCTION_HOST}")
      secretName: crest-tls
  rules:
    - host: $(yaml_quote "${CREST_PRODUCTION_HOST}")
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: crest
                port:
                  number: 8100
EOF

replace_image "${overlay_dir}/08-crest-service-statefulset.yaml" "ghcr.io/sevoniva/crest-core-service" "${backend_image}"
replace_image "${overlay_dir}/11-crest-web-statefulset.yaml" "ghcr.io/sevoniva/crest-core-web" "${frontend_image}"

bash scripts/production-config-check.sh "${overlay_dir}"
info "rendered strict production overlay at ${overlay_dir}"
info "Secret manifests are plaintext in this local output; keep ${overlay_dir} out of git and prefer SealedSecret/ExternalSecret in managed clusters."
