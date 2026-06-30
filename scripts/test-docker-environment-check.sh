#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-docker-environment-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_DOCKER_ENVIRONMENT_DIR:-.local/docker-environment-check-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/bin"

cat > "${test_root}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  info)
    if [[ "${CREST_FAKE_DOCKER_INFO_FAIL:-false}" == "true" ]]; then
      exit 1
    fi
    echo "fake docker info"
    ;;
  "system df")
    echo "TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE"
    echo "Images          1         0         1GB       1GB (100%)"
    ;;
  *)
    echo "unexpected docker args: $*" >&2
    exit 2
    ;;
esac
EOF

cat > "${test_root}/bin/df" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
free_kib="${CREST_FAKE_DF_FREE_KIB:-20971520}"
echo "Filesystem 1024-blocks Used Available Capacity Mounted on"
echo "/dev/fake 99999999 1 ${free_kib} 1% /"
EOF

chmod +x "${test_root}/bin/docker" "${test_root}/bin/df"

run_with_fake_path() {
  env PATH="${test_root}/bin:${PATH}" "$@"
}

run_with_fake_path bash scripts/docker-environment-check.sh >/dev/null

low_disk_log="${test_root}/low-disk.log"
low_disk_plan="${test_root}/low-disk-plan.txt"
low_disk_report="${test_root}/low-disk-report.txt"
if run_with_fake_path \
  CREST_FAKE_DF_FREE_KIB=2097152 \
  CREST_DOCKER_MIN_FREE_GB=12 \
  CREST_DOCKER_CLEANUP_REPORT="${low_disk_plan}" \
  CREST_DOCKER_ENVIRONMENT_REPORT="${low_disk_report}" \
  bash scripts/docker-environment-check.sh >"${low_disk_log}" 2>&1; then
  fail "docker environment check should fail when free disk is below the threshold"
fi
grep -q 'at least 12GiB free' "${low_disk_log}" \
  || fail "low disk failure must include the required free-space threshold"
grep -q 'only 2GiB available' "${low_disk_log}" \
  || fail "low disk failure must include the available free-space value"
grep -q 'Docker disk usage:' "${low_disk_log}" \
  || fail "low disk failure must print docker system df context"
grep -q 'Review read-only cleanup plan' "${low_disk_log}" \
  || fail "low disk failure must print the Docker cleanup plan path"
[[ -f "${low_disk_plan}" ]] || fail "low disk failure must write a Docker cleanup plan report"
[[ -f "${low_disk_report}" ]] || fail "low disk failure must write a Docker environment report"
grep -q '^status=insufficient-disk$' "${low_disk_report}" \
  || fail "low disk environment report must record insufficient-disk"
grep -q '^shortfall_gib=10$' "${low_disk_report}" \
  || fail "low disk environment report must record the free-space shortfall"
grep -q '^status=cleanup-needed$' "${low_disk_plan}" \
  || fail "low disk cleanup plan must mark cleanup-needed"
grep -q '^shortfall_gib=10$' "${low_disk_plan}" \
  || fail "low disk cleanup plan must record the free-space shortfall"
grep -q '^owner_approval_required=true$' "${low_disk_plan}" \
  || fail "low disk cleanup plan must record owner approval requirement"
grep -q '^destructive_commands_executed=false$' "${low_disk_plan}" \
  || fail "low disk cleanup plan must prove it did not execute cleanup"
grep -q '^recommended_first_cleanup_command=docker builder prune -af$' "${low_disk_plan}" \
  || fail "low disk cleanup plan must record the recommended first cleanup command"
grep -q 'docker builder prune -af' "${low_disk_plan}" \
  || fail "low disk cleanup plan must suggest build cache cleanup"

run_with_fake_path \
  CREST_FAKE_DF_FREE_KIB=2097152 \
  CREST_DOCKER_PRECHECK_SKIP_DISK=true \
  bash scripts/docker-environment-check.sh >/dev/null

purpose_log="${test_root}/purpose.log"
if run_with_fake_path \
  CREST_FAKE_DF_FREE_KIB=2097152 \
  CREST_DOCKER_ENVIRONMENT_PURPOSE="kind smoke test" \
  CREST_DOCKER_MIN_FREE_GB=12 \
  CREST_DOCKER_CLEANUP_PLAN_ON_FAILURE=false \
  bash scripts/docker-environment-check.sh >"${purpose_log}" 2>&1; then
  fail "docker environment check should fail with the configured purpose in the message"
fi
grep -q 'kind smoke test requires at least 12GiB free' "${purpose_log}" \
  || fail "configured purpose was not included in low disk failure"

docker_down_log="${test_root}/docker-down.log"
docker_down_report="${test_root}/docker-down-report.txt"
if run_with_fake_path \
  CREST_FAKE_DOCKER_INFO_FAIL=true \
  CREST_DOCKER_ENVIRONMENT_REPORT="${docker_down_report}" \
  bash scripts/docker-environment-check.sh >"${docker_down_log}" 2>&1; then
  fail "docker environment check should fail when Docker daemon is unavailable"
fi
grep -q 'Docker daemon is not available' "${docker_down_log}" \
  || fail "docker daemon failure must be explicit"
[[ -f "${docker_down_report}" ]] || fail "docker daemon failure must write an environment report"
grep -q '^status=daemon-unavailable$' "${docker_down_report}" \
  || fail "docker daemon environment report must record daemon-unavailable"
grep -q '^docker_info=unavailable$' "${docker_down_report}" \
  || fail "docker daemon environment report must record docker_info=unavailable"

echo "test-docker-environment-check: passed"
