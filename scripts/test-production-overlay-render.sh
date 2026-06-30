#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-overlay-render: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

command -v bash >/dev/null 2>&1 || fail "missing required command: bash"
command -v tr >/dev/null 2>&1 || fail "missing required command: tr"

repeat_char() {
  local char="$1"
  local count="$2"
  printf "%${count}s" "" | tr ' ' "${char}"
}

overlay_dir="${CREST_TEST_PRODUCTION_OVERLAY_DIR:-.local/production-overlay-smoke-$$}"
aes_key="$(repeat_char A 32)"
aes_iv="$(repeat_char B 16)"
db_password="$(repeat_char D 20)"
admin_password="$(repeat_char E 20)"
token_secret="$(repeat_char T 40)"
redis_password="$(repeat_char R 20)"
prometheus_token="$(repeat_char P 40)"
missing_prefix_log=".local/production-overlay-missing-prefix.log"
example_prefix_log=".local/production-overlay-example-prefix.log"
generic_redis_user_log=".local/production-overlay-generic-redis-user.log"
missing_prometheus_token_log=".local/production-overlay-missing-prometheus-token.log"
missing_storage_class_log=".local/production-overlay-missing-storage-class.log"
service_account_token_log=".local/production-overlay-service-account-token.log"
optional_secret_ref_log=".local/production-overlay-optional-secret-ref.log"
generic_secret_redis_user_log=".local/production-overlay-generic-secret-redis-user.log"
unsafe_overlay_dir_log=".local/production-overlay-unsafe-dir.log"
outside_overlay_dir_log=".local/production-overlay-outside-dir.log"
mkdir -p .local

if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_OVERLAY_DIR="." \
  bash scripts/render-production-overlay.sh >"${unsafe_overlay_dir_log}" 2>&1; then
  fail "render-production-overlay unexpectedly accepted the repository root as overlay dir"
fi
grep -q "CREST_PRODUCTION_OVERLAY_DIR is too broad to overwrite" "${unsafe_overlay_dir_log}" \
  || fail "unsafe overlay dir failure did not explain the overwrite risk"

if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_OVERLAY_DIR="/tmp/crest-production-overlay-outside" \
  bash scripts/render-production-overlay.sh >"${outside_overlay_dir_log}" 2>&1; then
  fail "render-production-overlay unexpectedly accepted an outside-repository overlay dir"
fi
grep -q "CREST_PRODUCTION_OVERLAY_DIR must stay inside the repository" "${outside_overlay_dir_log}" \
  || fail "outside overlay dir failure did not explain the repository boundary"

outside_overlay_parent="/tmp/crest-production-overlay-missing-parent-$$"
rm -rf "${outside_overlay_parent}"
if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_OVERLAY_DIR="${outside_overlay_parent}/overlay" \
  bash scripts/render-production-overlay.sh >".local/production-overlay-outside-missing-parent-dir.log" 2>&1; then
  fail "render-production-overlay unexpectedly accepted an outside-repository overlay dir with missing parent"
fi
grep -q "CREST_PRODUCTION_OVERLAY_DIR must stay inside the repository" ".local/production-overlay-outside-missing-parent-dir.log" \
  || fail "outside missing-parent overlay dir failure did not explain the repository boundary"
[[ ! -e "${outside_overlay_parent}" ]] \
  || fail "outside missing-parent overlay dir check must not create directories before rejecting"

if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_HOST="crest.example.com" \
  CREST_ORIGIN_LIST="https://crest.example.com" \
  CREST_DB_HOST="obproxy.example.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="${db_password}" \
  CREST_AES_KEY="${aes_key}" \
  CREST_AES_IV="${aes_iv}" \
  CREST_INITIAL_PASSWORD="${admin_password}" \
  CREST_TOKEN_SECRET="${token_secret}" \
  CREST_REDIS_CLUSTER_NODES="redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379" \
  CREST_REDIS_USERNAME="ops01-prod-crest-core-acl" \
  CREST_REDIS_PASSWORD="${redis_password}" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_DATA_STORAGE_CLASS="rwx-storage" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}-missing-prefix" \
  bash scripts/render-production-overlay.sh >"${missing_prefix_log}" 2>&1; then
  fail "render-production-overlay should require explicit CREST_REDIS_KEY_PREFIX for shared Redis"
fi

grep -q "CREST_REDIS_KEY_PREFIX is required" "${missing_prefix_log}" \
  || fail "missing-prefix render failure did not explain CREST_REDIS_KEY_PREFIX"

if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_HOST="crest.example.com" \
  CREST_ORIGIN_LIST="https://crest.example.com" \
  CREST_DB_HOST="obproxy.example.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="${db_password}" \
  CREST_AES_KEY="${aes_key}" \
  CREST_AES_IV="${aes_iv}" \
  CREST_INITIAL_PASSWORD="${admin_password}" \
  CREST_TOKEN_SECRET="${token_secret}" \
  CREST_REDIS_CLUSTER_NODES="redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379" \
  CREST_REDIS_USERNAME="ops01-prod-crest-core-acl" \
  CREST_REDIS_PASSWORD="${redis_password}" \
  CREST_REDIS_KEY_PREFIX="{acme-crest-core-prod}:prod" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_DATA_STORAGE_CLASS="rwx-storage" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}-example-prefix" \
  bash scripts/render-production-overlay.sh >"${example_prefix_log}" 2>&1; then
  fail "render-production-overlay should reject copied Redis example hash tags"
fi

grep -q "hash tag looks like an example value" "${example_prefix_log}" \
  || fail "example-prefix render failure did not explain the copied Redis example hash tag"

if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_HOST="crest.example.com" \
  CREST_ORIGIN_LIST="https://crest.example.com" \
  CREST_DB_HOST="obproxy.example.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="${db_password}" \
  CREST_AES_KEY="${aes_key}" \
  CREST_AES_IV="${aes_iv}" \
  CREST_INITIAL_PASSWORD="${admin_password}" \
  CREST_TOKEN_SECRET="${token_secret}" \
  CREST_REDIS_CLUSTER_NODES="redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379" \
  CREST_REDIS_USERNAME="production" \
  CREST_REDIS_PASSWORD="${redis_password}" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_DATA_STORAGE_CLASS="rwx-storage" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}-generic-redis-user" \
  bash scripts/render-production-overlay.sh >"${generic_redis_user_log}" 2>&1; then
  fail "render-production-overlay should reject generic Redis ACL users"
fi

grep -q "CREST_REDIS_USERNAME is too generic for shared Redis" "${generic_redis_user_log}" \
  || fail "generic Redis ACL user render failure did not explain the shared Redis risk"

if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_HOST="crest.example.com" \
  CREST_ORIGIN_LIST="https://crest.example.com" \
  CREST_DB_HOST="obproxy.example.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="${db_password}" \
  CREST_AES_KEY="${aes_key}" \
  CREST_AES_IV="${aes_iv}" \
  CREST_INITIAL_PASSWORD="${admin_password}" \
  CREST_TOKEN_SECRET="${token_secret}" \
  CREST_REDIS_CLUSTER_NODES="redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379" \
  CREST_REDIS_USERNAME="ops01-prod-crest-core-acl" \
  CREST_REDIS_PASSWORD="${redis_password}" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_PROMETHEUS_ENABLED="true" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_DATA_STORAGE_CLASS="rwx-storage" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}-missing-prometheus-token" \
  bash scripts/render-production-overlay.sh >"${missing_prometheus_token_log}" 2>&1; then
  fail "render-production-overlay should require CREST_PROMETHEUS_TOKEN when Prometheus is enabled"
fi

grep -q "CREST_PROMETHEUS_TOKEN is required" "${missing_prometheus_token_log}" \
  || fail "missing Prometheus token failure did not explain CREST_PROMETHEUS_TOKEN"

if env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_HOST="crest.example.com" \
  CREST_ORIGIN_LIST="https://crest.example.com" \
  CREST_DB_HOST="obproxy.example.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="${db_password}" \
  CREST_AES_KEY="${aes_key}" \
  CREST_AES_IV="${aes_iv}" \
  CREST_INITIAL_PASSWORD="${admin_password}" \
  CREST_TOKEN_SECRET="${token_secret}" \
  CREST_REDIS_CLUSTER_NODES="redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379" \
  CREST_REDIS_USERNAME="ops01-prod-crest-core-acl" \
  CREST_REDIS_PASSWORD="${redis_password}" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}-missing-storage-class" \
  bash scripts/render-production-overlay.sh >"${missing_storage_class_log}" 2>&1; then
  fail "render-production-overlay should require CREST_DATA_STORAGE_CLASS for production RWX storage"
fi

grep -q "CREST_DATA_STORAGE_CLASS is required" "${missing_storage_class_log}" \
  || fail "missing storage class failure did not explain CREST_DATA_STORAGE_CLASS"

env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_HOST="crest.example.com" \
  CREST_ORIGIN_LIST="https://crest.example.com" \
  CREST_DB_HOST="obproxy.example.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="${db_password}" \
  CREST_AES_KEY="${aes_key}" \
  CREST_AES_IV="${aes_iv}" \
  CREST_INITIAL_PASSWORD="${admin_password}" \
  CREST_TOKEN_SECRET="${token_secret}" \
  CREST_REDIS_CLUSTER_NODES="redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379" \
  CREST_REDIS_USERNAME="ops01-prod-crest-core-acl" \
  CREST_REDIS_PASSWORD="${redis_password}" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_DATA_STORAGE_CLASS="rwx-storage" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}" \
  bash scripts/render-production-overlay.sh

grep -q 'CREST_PROMETHEUS_ENABLED: "false"' "${overlay_dir}/00-crest-env-configmap.yaml" \
  || fail "default overlay should keep Prometheus disabled"
if grep -q 'CREST_PROMETHEUS_TOKEN' "${overlay_dir}/01-crest-db-secret.yaml"; then
  fail "disabled Prometheus overlay must not write CREST_PROMETHEUS_TOKEN"
fi

env -i \
  PATH="${PATH}" \
  HOME="${HOME:-}" \
  TMPDIR="${TMPDIR:-/tmp}" \
  CREST_PRODUCTION_HOST="crest.example.com" \
  CREST_ORIGIN_LIST="https://crest.example.com" \
  CREST_DB_HOST="obproxy.example.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="${db_password}" \
  CREST_AES_KEY="${aes_key}" \
  CREST_AES_IV="${aes_iv}" \
  CREST_INITIAL_PASSWORD="${admin_password}" \
  CREST_TOKEN_SECRET="${token_secret}" \
  CREST_REDIS_CLUSTER_NODES="redis-a.example.internal:6379,redis-b.example.internal:6379,redis-c.example.internal:6379" \
  CREST_REDIS_USERNAME="ops01-prod-crest-core-acl" \
  CREST_REDIS_PASSWORD="${redis_password}" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_PROMETHEUS_ENABLED="true" \
  CREST_PROMETHEUS_TOKEN="${prometheus_token}" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_DATA_STORAGE_CLASS="rwx-storage" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}-prometheus" \
  bash scripts/render-production-overlay.sh

grep -q 'CREST_PROMETHEUS_ENABLED: "true"' "${overlay_dir}-prometheus/00-crest-env-configmap.yaml" \
  || fail "Prometheus overlay should enable CREST_PROMETHEUS_ENABLED"
grep -q "CREST_PROMETHEUS_TOKEN: '${prometheus_token}'" "${overlay_dir}-prometheus/01-crest-db-secret.yaml" \
  || fail "Prometheus overlay should write CREST_PROMETHEUS_TOKEN into crest-db-secret"

if [[ -e "${overlay_dir}/.git" || -e "${overlay_dir}/reports" ]]; then
  fail "overlay smoke output contains unexpected repository metadata"
fi

bad_redis_user_overlay="${overlay_dir}-generic-secret-redis-user"
rm -rf "${bad_redis_user_overlay}"
cp -R "${overlay_dir}" "${bad_redis_user_overlay}"
sed -i.bak "s/CREST_REDIS_USERNAME: 'ops01-prod-crest-core-acl'/CREST_REDIS_USERNAME: 'production'/" \
  "${bad_redis_user_overlay}/02-crest-redis-secret.yaml"
rm -f "${bad_redis_user_overlay}/02-crest-redis-secret.yaml.bak"
if node scripts/verify-kubernetes-production.mjs --strict-config "${bad_redis_user_overlay}" \
  >"${generic_secret_redis_user_log}" 2>&1; then
  fail "production Kubernetes verifier should reject generic Redis ACL users"
fi
grep -q "CREST_REDIS_USERNAME is too generic for shared Redis" "${generic_secret_redis_user_log}" \
  || fail "generic Redis ACL user strict config failure did not explain the policy"

bad_service_account_overlay="${overlay_dir}-service-account-token"
rm -rf "${bad_service_account_overlay}"
cp -R "${overlay_dir}" "${bad_service_account_overlay}"
sed -i.bak 's/^automountServiceAccountToken: false$/automountServiceAccountToken: true/' \
  "${bad_service_account_overlay}/02a-crest-serviceaccount.yaml"
rm -f "${bad_service_account_overlay}/02a-crest-serviceaccount.yaml.bak"
if node scripts/verify-kubernetes-production.mjs --strict-config "${bad_service_account_overlay}" \
  >"${service_account_token_log}" 2>&1; then
  fail "production Kubernetes verifier should reject ServiceAccount token automount"
fi
grep -q "crest ServiceAccount must not automount tokens" "${service_account_token_log}" \
  || fail "ServiceAccount token automount failure did not explain the policy"

bad_optional_secret_overlay="${overlay_dir}-optional-secret-ref"
rm -rf "${bad_optional_secret_overlay}"
cp -R "${overlay_dir}" "${bad_optional_secret_overlay}"
perl -0pi -e 's/(                name: crest-db-secret\n)/$1                optional: true\n/' \
  "${bad_optional_secret_overlay}/08-crest-service-statefulset.yaml"
if node scripts/verify-kubernetes-production.mjs --strict-config "${bad_optional_secret_overlay}" \
  >"${optional_secret_ref_log}" 2>&1; then
  fail "production Kubernetes verifier should reject optional DB Secret refs"
fi
grep -q "crest-service must require crest-db-secret" "${optional_secret_ref_log}" \
  || fail "optional DB Secret ref failure did not explain the policy"

echo "test-production-overlay-render: passed"
