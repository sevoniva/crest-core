#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-docker-production-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_DOCKER_PRODUCTION_DIR:-.local/docker-production-check-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}"
trap 'rm -rf "${test_root}"' EXIT

good_env="${test_root}/good.env"
cat > "${good_env}" <<'EOF'
CREST_COMPOSE_PROJECT_NAME=crest-core-prod
CREST_BACKEND_IMAGE=ghcr.io/sevoniva/crest-core-service:v1.0.0
CREST_FRONTEND_IMAGE=ghcr.io/sevoniva/crest-core-web:v1.0.0
CREST_HTTP_BIND=127.0.0.1
CREST_HTTP_PORT=18080
CREST_ORIGIN_LIST=https://crest.company.internal
CREST_DB_HOST=obproxy.company.internal
CREST_DB_PORT=2883
CREST_DB_URL=jdbc:oceanbase://obproxy.company.internal:2883
CREST_DB_USERNAME=crest_core_prod@obtenant#obcluster
CREST_DB_PASSWORD=Db7Kc2Mhf9vPq4TzYx8R
CREST_AES_KEY=1234567890abcdef1234567890abcdef
CREST_AES_IV=1234567890abcdef
CREST_INITIAL_PASSWORD=Init7Kc2Mhf9vPq4
CREST_TOKEN_SECRET=Token7Kc2Mhf9vPq4TzYx8R3AaBbCcDdEeFf
CREST_REDIS_CLUSTER_NODES=redis-a.company.internal:6379,redis-b.company.internal:6379,redis-c.company.internal:6379
CREST_REDIS_USERNAME=company-prod-crest-core-acl
CREST_REDIS_PASSWORD=Redis7Kc2Mhf9vPq4Tz
CREST_REDIS_SSL_ENABLED=false
CREST_REDIS_KEY_PREFIX={company-prod-crest-core}:prod
CREST_REDIS_CACHE_KEY_PREFIX={company-prod-crest-core}:prod:cache:
CREST_LOCK_KEY_PREFIX={company-prod-crest-core}:prod:lock
CREST_WEBSOCKET_BROADCAST_CHANNEL={company-prod-crest-core}:prod:pubsub:websocket
CREST_EXPORT_TASK_STREAM={company-prod-crest-core}:prod:stream:export-task
CREST_EXPORT_TASK_CONSUMER_GROUP={company-prod-crest-core}:prod:group:export-workers
CREST_SYNC_TASK_STREAM={company-prod-crest-core}:prod:stream:dataset-sync-task
CREST_SYNC_TASK_CONSUMER_GROUP={company-prod-crest-core}:prod:group:dataset-sync-workers
CREST_DATASOURCE_SYNC_TASK_STREAM={company-prod-crest-core}:prod:stream:datasource-sync-task
CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP={company-prod-crest-core}:prod:group:datasource-sync-workers
CREST_SCHEDULED_TASK_STREAM={company-prod-crest-core}:prod:stream:scheduled-task
CREST_SCHEDULED_TASK_CONSUMER_GROUP={company-prod-crest-core}:prod:group:scheduled-workers
CREST_BACKEND_CPUS=2.0
CREST_BACKEND_MEMORY_LIMIT=2g
CREST_FRONTEND_CPUS=0.5
CREST_FRONTEND_MEMORY_LIMIT=512m
CREST_PROMETHEUS_ENABLED=false
CREST_PROMETHEUS_TOKEN=
EOF

node scripts/verify-docker-production.mjs deploy/docker --strict-config "${good_env}" >/dev/null

bad_redis_user="${test_root}/bad-redis-user.env"
cp "${good_env}" "${bad_redis_user}"
perl -0pi -e 's/^CREST_REDIS_USERNAME=.*$/CREST_REDIS_USERNAME=default/m' "${bad_redis_user}"
if node scripts/verify-docker-production.mjs deploy/docker --strict-config "${bad_redis_user}" >"${test_root}/bad-redis-user.out" 2>&1; then
  fail "default Redis user unexpectedly passed strict config"
fi
grep -q 'must not use the shared default Redis user' "${test_root}/bad-redis-user.out" \
  || fail "default Redis user failure message was not reported"

bad_origin="${test_root}/bad-origin.env"
cp "${good_env}" "${bad_origin}"
perl -0pi -e 's#^CREST_ORIGIN_LIST=.*$#CREST_ORIGIN_LIST=http://127.0.0.1:8080#m' "${bad_origin}"
if node scripts/verify-docker-production.mjs deploy/docker --strict-config "${bad_origin}" >"${test_root}/bad-origin.out" 2>&1; then
  fail "localhost non-HTTPS origin unexpectedly passed strict config"
fi
grep -q 'CREST_ORIGIN_LIST must use https origins' "${test_root}/bad-origin.out" \
  || fail "bad origin failure message was not reported"

bad_prefix="${test_root}/bad-prefix.env"
cp "${good_env}" "${bad_prefix}"
perl -0pi -e 's/^CREST_REDIS_KEY_PREFIX=.*$/CREST_REDIS_KEY_PREFIX=crest-core:prod/m' "${bad_prefix}"
if node scripts/verify-docker-production.mjs deploy/docker --strict-config "${bad_prefix}" >"${test_root}/bad-prefix.out" 2>&1; then
  fail "Redis prefix without hash tag unexpectedly passed strict config"
fi
grep -q 'must use a Redis Cluster hash tag' "${test_root}/bad-prefix.out" \
  || fail "bad Redis prefix failure message was not reported"

bad_image="${test_root}/bad-image.env"
cp "${good_env}" "${bad_image}"
perl -0pi -e 's/^CREST_BACKEND_IMAGE=.*$/CREST_BACKEND_IMAGE=ghcr.io\/sevoniva\/crest-core-service:latest/m' "${bad_image}"
if node scripts/verify-docker-production.mjs deploy/docker --strict-config "${bad_image}" >"${test_root}/bad-image.out" 2>&1; then
  fail "latest backend image unexpectedly passed strict config"
fi
grep -q 'CREST_BACKEND_IMAGE must not use latest' "${test_root}/bad-image.out" \
  || fail "latest image failure message was not reported"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  cp -R deploy/docker "${test_root}/docker"
  cp "${good_env}" "${test_root}/docker/production.env"
  (
    cd "${test_root}/docker"
    docker compose --env-file production.env -f compose.yaml config >/dev/null
  )
fi

echo "test-docker-production-check: passed"
