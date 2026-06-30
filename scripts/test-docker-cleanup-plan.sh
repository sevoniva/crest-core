#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-docker-cleanup-plan: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_DOCKER_CLEANUP_PLAN_DIR:-.local/docker-cleanup-plan-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/bin" "${test_root}/reports"

cat > "${test_root}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "$*" in
  info)
    echo "fake docker info"
    ;;
  "system df")
    echo "TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE"
    echo "Images          37        8         21.02GB   17.43GB (82%)"
    echo "Containers      9         6         761.8MB   49.15kB (0%)"
    echo "Local Volumes   50        6         60.85GB   39.57GB (65%)"
    echo "Build Cache     113       0         9.571GB   9.571GB"
    ;;
  "builder du")
    echo "ID              RECLAIMABLE     SIZE"
    echo "fake-cache      true            9.571GB"
    ;;
  prune*|builder\ prune*|image\ prune*|container\ prune*|volume\ prune*)
    echo "cleanup command must not be executed by docker-cleanup-plan.sh: $*" >&2
    exit 99
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
free_kib="${CREST_FAKE_DF_FREE_KIB:-2097152}"
echo "Filesystem 1024-blocks Used Available Capacity Mounted on"
echo "/dev/fake 99999999 1 ${free_kib} 1% /"
EOF

chmod +x "${test_root}/bin/docker" "${test_root}/bin/df"

report_file="${test_root}/reports/docker-cleanup-plan.txt"
env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_DF_FREE_KIB=2097152 \
  CREST_DOCKER_MIN_FREE_GB=12 \
  CREST_DOCKER_CLEANUP_REPORT="${report_file}" \
  bash scripts/docker-cleanup-plan.sh >/dev/null

[[ -f "${report_file}" ]] || fail "cleanup plan report was not written"
grep -q '^status=cleanup-needed$' "${report_file}" \
  || fail "cleanup plan must mark low disk as cleanup-needed"
grep -q '^available_gib=2$' "${report_file}" \
  || fail "cleanup plan must record available free space"
grep -q '^required_gib=12$' "${report_file}" \
  || fail "cleanup plan must record required free space"
grep -q '^shortfall_gib=10$' "${report_file}" \
  || fail "cleanup plan must record the free-space shortfall"
grep -q '^owner_approval_required=true$' "${report_file}" \
  || fail "cleanup plan must record owner approval requirement"
grep -q '^destructive_commands_executed=false$' "${report_file}" \
  || fail "cleanup plan must prove it did not execute cleanup"
grep -q '^recommended_first_cleanup_command=docker builder prune -af$' "${report_file}" \
  || fail "cleanup plan must record the recommended first cleanup command"
grep -q 'docker builder prune -af' "${report_file}" \
  || fail "cleanup plan must suggest build cache cleanup"
grep -q '^Docker builder cache usage:$' "${report_file}" \
  || fail "cleanup plan must include builder cache usage"
grep -q 'fake-cache.*9.571GB' "${report_file}" \
  || fail "cleanup plan must include builder cache details"
grep -q '^Approval checklist before cleanup:$' "${report_file}" \
  || fail "cleanup plan must include cleanup approval checklist"
grep -q 'docker volume prune -f' "${report_file}" \
  || fail "cleanup plan must include high-risk volume cleanup warning"
grep -q 'read-only; this script does not delete' "${report_file}" \
  || fail "cleanup plan must state that it is read-only"

enough_report="${test_root}/reports/docker-cleanup-plan-enough.txt"
env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_DF_FREE_KIB=20971520 \
  CREST_DOCKER_MIN_FREE_GB=12 \
  CREST_DOCKER_CLEANUP_REPORT="${enough_report}" \
  bash scripts/docker-cleanup-plan.sh >/dev/null

grep -q '^status=enough-free-space$' "${enough_report}" \
  || fail "cleanup plan must mark enough disk as enough-free-space"
grep -q '^shortfall_gib=0$' "${enough_report}" \
  || fail "cleanup plan must record zero shortfall when enough space is available"
grep -q '^owner_approval_required=false$' "${enough_report}" \
  || fail "cleanup plan must not require owner approval when no cleanup is needed"

echo "test-docker-cleanup-plan: passed"
