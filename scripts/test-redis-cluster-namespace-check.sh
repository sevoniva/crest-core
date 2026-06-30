#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-redis-cluster-namespace-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

tmp_dir=".local/redis-namespace-check-test"
fake_redis="${tmp_dir}/redis-cli"
report_file="${tmp_dir}/redis-namespace-report.txt"
log_file="${tmp_dir}/redis-cli.log"

rm -rf "${tmp_dir}"
mkdir -p "${tmp_dir}"

cat > "${fake_redis}" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

log_file="${CREST_FAKE_REDIS_LOG:?}"
if [[ "${REDISCLI_AUTH:-}" != "secret-password" ]]; then
  echo "REDISCLI_AUTH was not provided to redis-cli" >&2
  exit 2
fi
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -c|--no-auth-warning|--tls)
      shift
      ;;
    --pass)
      echo "redis password must not be passed as a process argument" >&2
      exit 2
      ;;
    -h|-p|--user|--cacert)
      shift 2
      ;;
    *)
      break
      ;;
  esac
done

cmd="$1"
shift || true
printf '%s %s\n' "${cmd}" "$*" >> "${log_file}"

if [[ "${CREST_FAKE_REDIS_FAIL_PING:-false}" == "true" && "${cmd}" == "PING" ]]; then
  echo "NOAUTH Authentication required"
  exit 1
fi

case "${cmd}" in
  PING)
    echo "PONG"
    ;;
  CLUSTER)
    case "$1" in
      INFO)
        echo "cluster_state:ok"
        ;;
      SLOTS)
        echo "0"
        ;;
      KEYSLOT)
        echo "1234"
        ;;
      *)
        echo "unexpected CLUSTER subcommand: $1" >&2
        exit 2
        ;;
    esac
    ;;
  SET)
    if [[ "${CREST_FAKE_REDIS_ALLOW_UNSCOPED_ACL:-false}" != "true" && "$1" == unscoped-crest-acl-probe:key:* ]]; then
      echo "NOPERM this user has no permissions to access one of the keys used as arguments" >&2
      exit 1
    fi
    echo "OK"
    ;;
  GET)
    echo "ok"
    ;;
  PUBLISH)
    if [[ "${CREST_FAKE_REDIS_ALLOW_UNSCOPED_ACL:-false}" != "true" && "$1" == unscoped-crest-acl-probe:channel:* ]]; then
      echo "NOPERM this user has no permissions to access the channel" >&2
      exit 1
    fi
    echo "0"
    ;;
  XGROUP)
    echo "OK"
    ;;
  XADD)
    if [[ "${CREST_FAKE_REDIS_ALLOW_UNSCOPED_ACL:-false}" != "true" && "$1" == unscoped-crest-acl-probe:stream:* ]]; then
      echo "NOPERM this user has no permissions to access one of the keys used as arguments" >&2
      exit 1
    fi
    echo "1-0"
    ;;
  XREADGROUP)
    echo "stream"
    ;;
  XACK)
    echo "1"
    ;;
  DEL)
    echo "2"
    ;;
  *)
    echo "unexpected command: ${cmd}" >&2
    exit 2
    ;;
esac
EOF
chmod +x "${fake_redis}"

run_valid_check() {
  CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_DATABASE="0" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_REDIS_CACHE_KEY_PREFIX="{ops01-prod-crest-core}:prod:cache:" \
  CREST_LOCK_KEY_PREFIX="{ops01-prod-crest-core}:prod:lock" \
  CREST_WEBSOCKET_BROADCAST_CHANNEL="{ops01-prod-crest-core}:prod:pubsub:websocket" \
  CREST_EXPORT_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:export-task" \
  CREST_EXPORT_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:export-workers" \
  CREST_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:dataset-sync-task" \
  CREST_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:dataset-sync-workers" \
  CREST_DATASOURCE_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:datasource-sync-task" \
  CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:datasource-sync-workers" \
  CREST_SCHEDULED_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:scheduled-task" \
  CREST_SCHEDULED_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:scheduled-workers" \
    bash scripts/redis-cluster-namespace-check.sh >/dev/null
}

: > "${log_file}"
run_valid_check

grep -q '^status=passed$' "${report_file}" || fail "valid check did not write passed report"
grep -q '^redis_hash_tag=ops01-prod-crest-core$' "${report_file}" || fail "report did not include redis hash tag"
grep -q '^redis_acl_user=crest-prod$' "${report_file}" || fail "report did not include redis ACL user"
grep -q '^redis_acl_key_isolation=passed$' "${report_file}" || fail "report did not include key ACL isolation"
grep -q '^redis_acl_stream_isolation=passed$' "${report_file}" || fail "report did not include stream ACL isolation"
grep -q '^redis_acl_channel_isolation=passed$' "${report_file}" || fail "report did not include channel ACL isolation"
grep -q '^PING ' "${log_file}" || fail "fake redis-cli was not invoked"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="." \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-unsafe-report.out 2>&1; then
  fail "repository root Redis namespace report path unexpectedly passed"
fi
grep -q "CREST_REDIS_NAMESPACE_REPORT is too broad to overwrite" /tmp/crest-redis-namespace-unsafe-report.out \
  || fail "unsafe Redis namespace report path failure message was not reported"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="/tmp/crest-redis-namespace-outside.txt" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-outside-report.out 2>&1; then
  fail "outside-repository Redis namespace report path unexpectedly passed"
fi
grep -q "CREST_REDIS_NAMESPACE_REPORT must stay inside the repository" /tmp/crest-redis-namespace-outside-report.out \
  || fail "outside Redis namespace report path failure message was not reported"

relative_outside_parent="../crest-redis-namespace-relative-outside-$$"
rm -rf "${relative_outside_parent}"
if CREST_REDIS_NAMESPACE_REPORT="${relative_outside_parent}/redis-namespace-report.txt" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-relative-outside-report.out 2>&1; then
  fail "relative outside-repository Redis namespace report path unexpectedly passed"
fi
grep -q "CREST_REDIS_NAMESPACE_REPORT must stay inside the repository" /tmp/crest-redis-namespace-relative-outside-report.out \
  || fail "relative outside Redis namespace report path failure message was not reported"
[[ ! -e "${relative_outside_parent}" ]] \
  || fail "relative outside Redis namespace report parent was created before rejection"

outside_link_target="/tmp/crest-redis-namespace-report-symlink-target-$$"
rm -rf "${outside_link_target}"
mkdir -p "${outside_link_target}"
ln -s "${outside_link_target}" "${tmp_dir}/outside-report-link"
if CREST_REDIS_NAMESPACE_REPORT="${tmp_dir}/outside-report-link/reports/redis-namespace-report.txt" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-symlink-report.out 2>&1; then
  fail "symlink-escaped Redis namespace report path unexpectedly passed"
fi
grep -q "CREST_REDIS_NAMESPACE_REPORT must stay inside the repository" /tmp/crest-redis-namespace-symlink-report.out \
  || fail "symlink-escaped Redis namespace report path failure message was not reported"
[[ ! -e "${outside_link_target}/reports" ]] \
  || fail "symlink-escaped Redis namespace report parent was created outside the repository"

outside_report_target="/tmp/crest-redis-namespace-report-symlink-target-$$.txt"
rm -f "${outside_report_target}"
printf 'unchanged\n' > "${outside_report_target}"
ln -s "${outside_report_target}" "${tmp_dir}/outside-report-file-link.txt"
if CREST_REDIS_NAMESPACE_REPORT="${tmp_dir}/outside-report-file-link.txt" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-symlink-file-report.out 2>&1; then
  fail "symlink Redis namespace report file unexpectedly passed"
fi
grep -q "CREST_REDIS_NAMESPACE_REPORT must not be a symlink" /tmp/crest-redis-namespace-symlink-file-report.out \
  || fail "symlink Redis namespace report file failure message was not reported"
grep -q '^unchanged$' "${outside_report_target}" \
  || fail "symlink Redis namespace report target was modified before rejection"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{crest-core}:prod" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-generic.out 2>&1; then
  fail "generic Redis hash tag unexpectedly passed"
fi
grep -q "hash tag is too generic" /tmp/crest-redis-namespace-generic.out \
  || fail "generic hash tag failure message was not reported"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{acme-crest-core-prod}:prod" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-example.out 2>&1; then
  fail "copied Redis example hash tag unexpectedly passed"
fi
grep -q "hash tag looks like an example value" /tmp/crest-redis-namespace-example.out \
  || fail "example hash tag failure message was not reported"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="default" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-default-user.out 2>&1; then
  fail "default Redis ACL user unexpectedly passed"
fi
grep -q "must not be default" /tmp/crest-redis-namespace-default-user.out \
  || fail "default ACL user failure message was not reported"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="production" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-generic-user.out 2>&1; then
  fail "generic Redis ACL user unexpectedly passed"
fi
grep -q "CREST_REDIS_USERNAME is too generic for shared Redis" /tmp/crest-redis-namespace-generic-user.out \
  || fail "generic ACL user failure message was not reported"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_REDIS_CACHE_KEY_PREFIX="{other-system}:prod:cache:" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-mismatch.out 2>&1; then
  fail "mismatched Redis scoped key unexpectedly passed"
fi
grep -q "CREST_REDIS_CACHE_KEY_PREFIX must use the same Redis Cluster hash tag" /tmp/crest-redis-namespace-mismatch.out \
  || fail "mismatched scoped key failure message was not reported"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1,redis-2:6379" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_REDIS_CACHE_KEY_PREFIX="{ops01-prod-crest-core}:prod:cache:" \
  CREST_LOCK_KEY_PREFIX="{ops01-prod-crest-core}:prod:lock" \
  CREST_WEBSOCKET_BROADCAST_CHANNEL="{ops01-prod-crest-core}:prod:pubsub:websocket" \
  CREST_EXPORT_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:export-task" \
  CREST_EXPORT_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:export-workers" \
  CREST_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:dataset-sync-task" \
  CREST_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:dataset-sync-workers" \
  CREST_DATASOURCE_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:datasource-sync-task" \
  CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:dataset-sync-workers" \
  CREST_SCHEDULED_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:scheduled-task" \
  CREST_SCHEDULED_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:scheduled-workers" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-malformed-node.out 2>&1; then
  fail "malformed Redis Cluster node unexpectedly passed"
fi
grep -q "CREST_REDIS_CLUSTER_NODES must contain host:port entries" /tmp/crest-redis-namespace-malformed-node.out \
  || fail "malformed Redis Cluster node failure message was not reported"

if CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-0:6379,redis-2:6379" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_REDIS_CACHE_KEY_PREFIX="{ops01-prod-crest-core}:prod:cache:" \
  CREST_LOCK_KEY_PREFIX="{ops01-prod-crest-core}:prod:lock" \
  CREST_WEBSOCKET_BROADCAST_CHANNEL="{ops01-prod-crest-core}:prod:pubsub:websocket" \
  CREST_EXPORT_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:export-task" \
  CREST_EXPORT_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:export-workers" \
  CREST_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:dataset-sync-task" \
  CREST_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:dataset-sync-workers" \
  CREST_DATASOURCE_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:datasource-sync-task" \
  CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:dataset-sync-workers" \
  CREST_SCHEDULED_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:scheduled-task" \
  CREST_SCHEDULED_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:scheduled-workers" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-duplicate-node.out 2>&1; then
  fail "duplicate Redis Cluster node unexpectedly passed"
fi
grep -q "CREST_REDIS_CLUSTER_NODES must not contain duplicate entries" /tmp/crest-redis-namespace-duplicate-node.out \
  || fail "duplicate Redis Cluster node failure message was not reported"

if CREST_FAKE_REDIS_FAIL_PING=true \
  CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_DATABASE="0" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_REDIS_CACHE_KEY_PREFIX="{ops01-prod-crest-core}:prod:cache:" \
  CREST_LOCK_KEY_PREFIX="{ops01-prod-crest-core}:prod:lock" \
  CREST_WEBSOCKET_BROADCAST_CHANNEL="{ops01-prod-crest-core}:prod:pubsub:websocket" \
  CREST_EXPORT_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:export-task" \
  CREST_EXPORT_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:export-workers" \
  CREST_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:dataset-sync-task" \
  CREST_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:dataset-sync-workers" \
  CREST_DATASOURCE_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:datasource-sync-task" \
  CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:datasource-sync-workers" \
  CREST_SCHEDULED_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:scheduled-task" \
  CREST_SCHEDULED_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:scheduled-workers" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-ping.out 2>&1; then
  fail "Redis PING failure unexpectedly passed"
fi
grep -q "Redis PING failed" /tmp/crest-redis-namespace-ping.out \
  || fail "Redis command failure was not reported"
grep -q '^status=failed$' "${report_file}" || fail "failed Redis probe did not write failed report"

if CREST_FAKE_REDIS_ALLOW_UNSCOPED_ACL=true \
  CREST_REDIS_CLI="${fake_redis}" \
  CREST_FAKE_REDIS_LOG="${log_file}" \
  CREST_REDIS_NAMESPACE_REPORT="${report_file}" \
  CREST_REDIS_CLUSTER_NODES="redis-0:6379,redis-1:6379,redis-2:6379" \
  CREST_REDIS_DATABASE="0" \
  CREST_REDIS_USERNAME="crest-prod" \
  CREST_REDIS_PASSWORD="secret-password" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_REDIS_CACHE_KEY_PREFIX="{ops01-prod-crest-core}:prod:cache:" \
  CREST_LOCK_KEY_PREFIX="{ops01-prod-crest-core}:prod:lock" \
  CREST_WEBSOCKET_BROADCAST_CHANNEL="{ops01-prod-crest-core}:prod:pubsub:websocket" \
  CREST_EXPORT_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:export-task" \
  CREST_EXPORT_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:export-workers" \
  CREST_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:dataset-sync-task" \
  CREST_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:dataset-sync-workers" \
  CREST_DATASOURCE_SYNC_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:datasource-sync-task" \
  CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:datasource-sync-workers" \
  CREST_SCHEDULED_TASK_STREAM="{ops01-prod-crest-core}:prod:stream:scheduled-task" \
  CREST_SCHEDULED_TASK_CONSUMER_GROUP="{ops01-prod-crest-core}:prod:group:scheduled-workers" \
  bash scripts/redis-cluster-namespace-check.sh >/tmp/crest-redis-namespace-acl.out 2>&1; then
  fail "Redis ACL that allows unscoped key writes unexpectedly passed"
fi
grep -q "unscoped Redis key write was unexpectedly allowed by Redis ACL" /tmp/crest-redis-namespace-acl.out \
  || fail "unscoped ACL allowance failure message was not reported"
grep -q '^status=failed$' "${report_file}" || fail "unscoped ACL failure did not write failed report"

echo "test-redis-cluster-namespace-check: passed"
