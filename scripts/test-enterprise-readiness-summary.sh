#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-enterprise-readiness-summary: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

unset \
  CREST_READINESS_REQUIRE_GO_NO_GO \
  CREST_READINESS_SKIP_QUALITY \
  CREST_READINESS_SKIP_SECURITY \
  CREST_READINESS_SKIP_DOCKER \
  CREST_READINESS_SKIP_CONTAINER_SCAN \
  CREST_READINESS_SKIP_KIND \
  CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS \
  CREST_READINESS_CONTINUE_ON_FAILURE \
  CREST_READINESS_CREATE_CLEAN_SOURCE \
  CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE \
  CREST_READINESS_REQUIRE_CLEAN_HISTORY \
  CREST_READINESS_RENDER_OVERLAY \
  CREST_PRODUCTION_OVERLAY_DIR \
  CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR \
  CREST_READINESS_LIVE_CHECK \
  CREST_READINESS_COLLECT_EVIDENCE \
  CREST_READINESS_CHECK_EXTERNAL_EVIDENCE \
  CREST_EXTERNAL_EVIDENCE_SUMMARY \
  CREST_EVIDENCE_REQUIRE_INGRESS_ADDRESS \
  CREST_READINESS_DOCKER_BUILDKIT \
  CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH \
  CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS \
  CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT \
  CREST_HISTORY_SECRET_REPORT \
  CREST_HISTORY_SECRET_SUMMARY \
  CREST_CREDENTIAL_ROTATION_APPROVED_BY \
  CREST_CREDENTIAL_ROTATION_EVIDENCE_ID \
  CREST_EXTERNAL_EVIDENCE_DIR \
  CREST_EVIDENCE_DIR \
  CREST_K8S_NAMESPACE \
  CREST_KUBE_CONTEXT \
  CREST_PRODUCTION_HOST \
  CREST_ORIGIN_LIST \
  CREST_INGRESS_CLASS_NAME \
  CREST_DB_HOST \
  CREST_DB_PORT \
  CREST_DB_USERNAME \
  CREST_DB_PASSWORD \
  CREST_AES_KEY \
  CREST_AES_IV \
  CREST_INITIAL_PASSWORD \
  CREST_TOKEN_SECRET \
  CREST_REDIS_CLUSTER_NODES \
  CREST_REDIS_USERNAME \
  CREST_REDIS_PASSWORD \
  CREST_REDIS_KEY_PREFIX \
  CREST_REDIS_SSL_ENABLED \
  CREST_PROMETHEUS_ENABLED \
  CREST_PROMETHEUS_TOKEN \
  CREST_BACKEND_IMAGE \
  CREST_FRONTEND_IMAGE \
  CREST_DATA_STORAGE_CLASS \
  CREST_DATA_STORAGE_SIZE \
  CREST_READINESS_CONTAINER_SCAN_WAIVER \
  CREST_CONTAINER_SCAN_WAIVER_FILE

test_root="${CREST_TEST_READINESS_SUMMARY_DIR:-.local/enterprise-readiness-summary-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}"
mkdir -p "${test_root}/bin"

cat > "${test_root}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  buildx)
    if [[ "${2:-}" == "version" && "${CREST_FAKE_BUILDX_AVAILABLE:-false}" == "true" ]]; then
      echo "github.com/docker/buildx v0.0.0-test"
      exit 0
    fi
    exit 1
    ;;
  info)
    if [[ "${2:-}" == "--format" ]]; then
      echo "${CREST_FAKE_DOCKER_ARCH:-arm64}"
      exit 0
    fi
    echo "fake docker info"
    ;;
  system)
    [[ "${2:-}" == "df" ]] || {
      echo "unexpected docker args: $*" >&2
      exit 2
    }
    echo "TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE"
    echo "Images          1         0         1GB       1GB (100%)"
    ;;
  build)
    printf 'DOCKER_BUILDKIT=%s %s\n' "${DOCKER_BUILDKIT:-}" "$*" >> "${CREST_FAKE_DOCKER_BUILD_LOG}"
    ;;
  image)
    if [[ "${2:-}" == "inspect" ]]; then
      printf '%s\n' "${3:-}" >> "${CREST_FAKE_DOCKER_INSPECT_LOG:-/dev/null}"
      exit "${CREST_FAKE_DOCKER_IMAGE_INSPECT_STATUS:-1}"
    fi
    echo "unexpected docker image args: $*" >&2
    exit 2
    ;;
  pull)
    printf '%s\n' "${2:-}" >> "${CREST_FAKE_DOCKER_PULL_LOG:-/dev/null}"
    ;;
  tag)
    printf '%s -> %s\n' "${2:-}" "${3:-}" >> "${CREST_FAKE_DOCKER_TAG_LOG:-/dev/null}"
    ;;
  run)
    printf '%s\n' "$*" >> "${CREST_FAKE_DOCKER_RUN_LOG}"
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

cat > "${test_root}/bin/gitleaks" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
report_path=""
no_git="false"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --report-path)
      report_path="$2"
      shift 2
      ;;
    --no-git)
      no_git="true"
      shift
      ;;
    *)
      shift
      ;;
  esac
done
[[ -n "${report_path}" ]] || exit 2
mkdir -p "$(dirname "${report_path}")"
if [[ "${CREST_FAKE_GITLEAKS_FINDINGS:-false}" == "true" && "${no_git}" != "true" ]]; then
  cat > "${report_path}" <<'JSON'
[
  {"RuleID":"generic-api-key","File":"deploy/kubernetes/crest-ob-oracle-redis.yaml","Commit":"abc"},
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml","Commit":"def"}
]
JSON
  exit 1
fi
printf '[]\n' > "${report_path}"
EOF

real_node="$(command -v node)"
cat > "${test_root}/bin/node" <<EOF
#!/usr/bin/env bash
set -euo pipefail
if [[ "\${1:-}" == "scripts/verify-static-assets.mjs" ]]; then
  echo "static-check: fake pass"
  exit 0
fi
exec "${real_node}" "\$@"
EOF

chmod +x "${test_root}/bin/docker" "${test_root}/bin/df" "${test_root}/bin/gitleaks" "${test_root}/bin/node"

if CREST_READINESS_REPORT_DIR="." \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/unsafe-report-dir.log" 2>&1; then
  fail "enterprise readiness unexpectedly accepted the repository root as report dir"
fi
grep -q "CREST_READINESS_REPORT_DIR is too broad to write readiness into" "${test_root}/unsafe-report-dir.log" \
  || fail "unsafe readiness report dir failure message was not reported"

outside_report_parent="/tmp/crest-readiness-report-missing-parent-$$"
rm -rf "${outside_report_parent}"
if CREST_READINESS_REPORT_DIR="${outside_report_parent}/readiness" \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/outside-report-dir.log" 2>&1; then
  fail "enterprise readiness unexpectedly accepted an outside-repository report dir"
fi
grep -q "CREST_READINESS_REPORT_DIR must stay inside the repository" "${test_root}/outside-report-dir.log" \
  || fail "outside readiness report dir failure message was not reported"
[[ ! -e "${outside_report_parent}" ]] \
  || fail "outside readiness report dir check must not create directories before rejecting"

if CREST_READINESS_REPORT_DIR="${test_root}/readiness-path-safety" \
  CREST_READINESS_GATE_LOG_DIR="." \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/unsafe-gate-log-dir.log" 2>&1; then
  fail "enterprise readiness unexpectedly accepted the repository root as gate log dir"
fi
grep -q "CREST_READINESS_GATE_LOG_DIR is too broad to overwrite" "${test_root}/unsafe-gate-log-dir.log" \
  || fail "unsafe readiness gate log dir failure message was not reported"

outside_gate_parent="/tmp/crest-readiness-gate-log-missing-parent-$$"
rm -rf "${outside_gate_parent}"
if CREST_READINESS_REPORT_DIR="${test_root}/readiness-path-safety" \
  CREST_READINESS_GATE_LOG_DIR="${outside_gate_parent}/gate-logs" \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/outside-gate-log-dir.log" 2>&1; then
  fail "enterprise readiness unexpectedly accepted an outside-repository gate log dir"
fi
grep -q "CREST_READINESS_GATE_LOG_DIR must stay inside the repository" "${test_root}/outside-gate-log-dir.log" \
  || fail "outside readiness gate log dir failure message was not reported"
[[ ! -e "${outside_gate_parent}" ]] \
  || fail "outside readiness gate log dir check must not create directories before rejecting"

partial_log="${test_root}/partial.log"
partial_report_dir="${test_root}/partial"

env \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_REPORT_DIR="${partial_report_dir}" \
  bash scripts/enterprise-readiness-check.sh >"${partial_log}" 2>&1

partial_summary="${partial_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${partial_summary}" ]] || fail "partial summary was not written"
grep -q '^readiness_status=partial-check-passed$' "${partial_summary}" \
  || fail "partial summary must record readiness_status=partial-check-passed"
grep -q '^production_release_status=not-ready$' "${partial_summary}" \
  || fail "partial summary must record production_release_status=not-ready"
grep -q '^require_base_image_digests=true$' "${partial_summary}" \
  || fail "partial summary must default to require_base_image_digests=true"
grep -q '^docker_buildkit=default$' "${partial_summary}" \
  || fail "partial summary must record default Docker BuildKit mode"
grep -q '^production_release_blocker=one or more static gates were skipped$' "${partial_summary}" \
  || fail "partial summary must explain skipped static gates"
grep -q '^docker-environment: skipped$' "${partial_summary}" \
  || fail "partial summary must record skipped Docker environment preflight"
grep -q '^container-base-image-policy: skipped$' "${partial_summary}" \
  || fail "partial summary must record skipped container base image policy"
grep -q '^ci-toolchain-policy: passed$' "${partial_summary}" \
  || fail "partial summary must record CI toolchain policy: passed"
grep -q "^ci_toolchain_policy_report=${partial_report_dir}/ci-toolchain-policy.txt$" "${partial_summary}" \
  || fail "partial summary must record CI toolchain policy report under readiness report dir"
grep -q '^production_release_blocker=live preprod/production runtime check was not run$' "${partial_summary}" \
  || fail "partial summary must explain missing live runtime evidence"
grep -q "^readiness_action_plan=${partial_report_dir}/production-readiness-action-plan.txt$" "${partial_summary}" \
  || fail "partial summary must record readiness action plan path"
grep -Eq '^readiness_action_plan_sha256=[0-9a-f]{64}$' "${partial_summary}" \
  || fail "partial summary must record readiness action plan digest"
grep -q 'partial readiness checks passed; production release is not ready' "${partial_log}" \
  || fail "partial run must not print an unconditional production-ready message"

docker_default_log="${test_root}/docker-default-buildkit.log"
docker_default_report_dir="${test_root}/docker-default-buildkit"
docker_default_build_log="${test_root}/docker-default-buildkit-build.log"
docker_default_inspect_log="${test_root}/docker-default-buildkit-inspect.log"
docker_default_pull_log="${test_root}/docker-default-buildkit-pull.log"
docker_default_tag_log="${test_root}/docker-default-buildkit-tag.log"
docker_default_run_log="${test_root}/docker-default-buildkit-run.log"
: > "${docker_default_build_log}"
: > "${docker_default_inspect_log}"
: > "${docker_default_pull_log}"
: > "${docker_default_tag_log}"
: > "${docker_default_run_log}"
env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_DF_FREE_KIB=20971520 \
  CREST_FAKE_DOCKER_ARCH=arm64 \
  CREST_FAKE_BUILDX_AVAILABLE=false \
  CREST_FAKE_DOCKER_BUILD_LOG="${docker_default_build_log}" \
  CREST_FAKE_DOCKER_INSPECT_LOG="${docker_default_inspect_log}" \
  CREST_FAKE_DOCKER_PULL_LOG="${docker_default_pull_log}" \
  CREST_FAKE_DOCKER_TAG_LOG="${docker_default_tag_log}" \
  CREST_FAKE_DOCKER_RUN_LOG="${docker_default_run_log}" \
  CREST_DOCKER_BUILD_ARTIFACTS=false \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_REPORT_DIR="${docker_default_report_dir}" \
  bash scripts/enterprise-readiness-check.sh >"${docker_default_log}" 2>&1

docker_default_summary="${docker_default_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${docker_default_summary}" ]] || fail "Docker default BuildKit summary was not written"
grep -q '^docker_buildkit=default$' "${docker_default_summary}" \
  || fail "Docker default BuildKit summary must record default Docker BuildKit mode"
grep -q '^docker-build: passed$' "${docker_default_summary}" \
  || fail "Docker default BuildKit run must pass the Docker build gate"
grep -q '^docker_build_base_image_policy_report=' "${docker_default_summary}" \
  || fail "Docker default BuildKit run must record Docker build base image policy evidence"
grep -q -- '-f Dockerfile.frontend' "${docker_default_build_log}" \
  || fail "Docker default BuildKit run must invoke the frontend image build"
grep -q -- 'eclipse-temurin:17-jdk-jammy@sha256:beabb759e6f9653c843958d1d1f5cecb881dfb85aa6081e2bef099ab1260344e' "${docker_default_pull_log}" \
  || fail "Docker default BuildKit run must pre-pull the JDK base image"
grep -q -- 'ubuntu:24.04@sha256:786a8b558f7be160c6c8c4a54f9a57274f3b4fb1491cf65146521ae77ff1dc54' "${docker_default_pull_log}" \
  || fail "Docker default BuildKit run must pre-pull the runtime base image"
grep -q -- 'nginx:1.29-alpine@sha256:7dd09a6c4f8cab9a2d2cb98fb39790f220e8bc2ea106b2cebde64b90405e0be8' "${docker_default_pull_log}" \
  || fail "Docker default BuildKit run must pre-pull the effective Nginx base image"
grep -q -- 'nginx:1.29-alpine@sha256:7dd09a6c4f8cab9a2d2cb98fb39790f220e8bc2ea106b2cebde64b90405e0be8 -> crest-local-base/crest-docker-nginx-image:sha256-7dd09a6c4f8cab9a' "${docker_default_tag_log}" \
  || fail "Docker default BuildKit run must tag the effective Nginx base image with a local alias"
grep -q -- '--build-arg NGINX_IMAGE=crest-local-base/crest-docker-nginx-image:sha256-7dd09a6c4f8cab9a' "${docker_default_build_log}" \
  || fail "Docker default BuildKit run must build frontend with the local Nginx alias"
grep -q -- 'DOCKER_BUILDKIT=0 build -f Dockerfile.frontend ' "${docker_default_build_log}" \
  || fail "Docker default BuildKit run must keep the frontend legacy builder default"
grep -q -- 'DOCKER_BUILDKIT=0 .* -f Dockerfile.backend ' "${docker_default_build_log}" \
  || fail "Docker default BuildKit run must keep the backend legacy builder default"

invalid_buildkit_log="${test_root}/invalid-buildkit.log"
if env \
  CREST_READINESS_DOCKER_BUILDKIT=bad \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_REPORT_DIR="${test_root}/invalid-buildkit" \
  bash scripts/enterprise-readiness-check.sh >"${invalid_buildkit_log}" 2>&1; then
  fail "readiness check should reject invalid CREST_READINESS_DOCKER_BUILDKIT"
fi

grep -q 'CREST_READINESS_DOCKER_BUILDKIT must be empty, 0 or 1' "${invalid_buildkit_log}" \
  || fail "invalid Docker BuildKit mode must be rejected with a clear message"

go_no_go_log="${test_root}/go-no-go.log"
if env \
  CREST_READINESS_REQUIRE_GO_NO_GO=true \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_REPORT_DIR="${test_root}/go-no-go" \
  bash scripts/enterprise-readiness-check.sh >"${go_no_go_log}" 2>&1; then
  fail "Go/No-Go mode should fail when a required static gate is skipped"
fi

grep -q 'Go/No-Go mode requires quality' "${go_no_go_log}" \
  || fail "Go/No-Go failure must identify the skipped quality gate"

go_no_go_container_skip_log="${test_root}/go-no-go-container-skip.log"
if env \
  CREST_READINESS_REQUIRE_GO_NO_GO=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_REPORT_DIR="${test_root}/go-no-go-container-skip" \
  bash scripts/enterprise-readiness-check.sh >"${go_no_go_container_skip_log}" 2>&1; then
  fail "Go/No-Go mode should fail when container scan is skipped without an approved waiver"
fi

grep -q 'Go/No-Go mode requires container-scan' "${go_no_go_container_skip_log}" \
  || fail "Go/No-Go container scan skip failure must explain the required waiver"

go_no_go_digest_log="${test_root}/go-no-go-digest.log"
if env \
  CREST_READINESS_REQUIRE_GO_NO_GO=true \
  CREST_READINESS_SKIP_QUALITY=false \
  CREST_READINESS_SKIP_SECURITY=false \
  CREST_READINESS_SKIP_DOCKER=false \
  CREST_READINESS_SKIP_CONTAINER_SCAN=false \
  CREST_READINESS_SKIP_KIND=false \
  CREST_READINESS_CREATE_CLEAN_SOURCE=true \
  CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true \
  CREST_READINESS_RENDER_OVERLAY=true \
  CREST_READINESS_COLLECT_EVIDENCE=true \
  CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS=false \
  CREST_EXTERNAL_EVIDENCE_DIR="${test_root}/external-evidence" \
  CREST_READINESS_REPORT_DIR="${test_root}/go-no-go-digest" \
  bash scripts/enterprise-readiness-check.sh >"${go_no_go_digest_log}" 2>&1; then
  fail "Go/No-Go mode should fail when base image digest enforcement is disabled"
fi

grep -q 'Go/No-Go mode requires CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS=true' "${go_no_go_digest_log}" \
  || fail "Go/No-Go digest failure must identify disabled base image digest enforcement"

docker_failure_log="${test_root}/docker-failure.log"
docker_failure_report_dir="${test_root}/docker-failure"
if env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_DF_FREE_KIB=2097152 \
  CREST_DOCKER_MIN_FREE_GB=12 \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_REPORT_DIR="${docker_failure_report_dir}" \
  bash scripts/enterprise-readiness-check.sh >"${docker_failure_log}" 2>&1; then
  fail "readiness check should fail when Docker environment preflight fails"
fi

docker_failure_summary="${docker_failure_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${docker_failure_summary}" ]] || fail "Docker failure summary was not written"
grep -q '^docker-environment: failed$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record docker-environment: failed"
grep -q '^container-base-image-policy: passed$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record container-base-image-policy: passed before Docker preflight"
grep -q '^ci-toolchain-policy: passed$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record CI toolchain policy: passed before Docker preflight"
grep -q "^ci_toolchain_policy_report=${docker_failure_report_dir}/ci-toolchain-policy.txt$" "${docker_failure_summary}" \
  || fail "Docker failure CI toolchain policy report must stay under the readiness report dir"
grep -Eq '^ci_toolchain_policy_report_sha256=[0-9a-f]{64}$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the CI toolchain policy report digest"
grep -q "^container_base_image_policy_report=${docker_failure_report_dir}/container-base-image-policy.txt$" "${docker_failure_summary}" \
  || fail "Docker failure base image policy report must stay under the readiness report dir"
grep -Eq '^container_base_image_policy_report_sha256=[0-9a-f]{64}$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the base image policy report digest"
grep -q '^docker-environment_exit_code=1$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the failing exit code"
grep -Eq '^docker-environment_log=.*docker-environment\.log$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record a Docker environment gate log path"
grep -Eq '^docker-environment_log_sha256=[0-9a-f]{64}$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the Docker environment gate log digest"
grep -q "^docker_environment_report=${docker_failure_report_dir}/docker-environment-report.txt$" "${docker_failure_summary}" \
  || fail "Docker failure summary must record the Docker environment report"
grep -Eq '^docker_environment_report_sha256=[0-9a-f]{64}$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the Docker environment report digest"
grep -Eq '^docker_cleanup_plan=.*docker-cleanup-plan\.txt$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the cleanup plan path"
grep -q "^docker_cleanup_plan=${docker_failure_report_dir}/docker-cleanup-plan.txt$" "${docker_failure_summary}" \
  || fail "Docker failure cleanup plan must stay under the readiness report dir"
grep -Eq '^docker_cleanup_plan_sha256=[0-9a-f]{64}$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the cleanup plan digest"
grep -q '^readiness_status=failed$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record readiness_status=failed"
grep -q '^production_release_status=not-ready$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record production_release_status=not-ready"
grep -q '^production_release_blocker=Docker environment preflight failed$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record the failed gate blocker"
grep -q "^readiness_action_plan=${docker_failure_report_dir}/production-readiness-action-plan.txt$" "${docker_failure_summary}" \
  || fail "Docker failure summary must record readiness action plan path"
grep -Eq '^readiness_action_plan_sha256=[0-9a-f]{64}$' "${docker_failure_summary}" \
  || fail "Docker failure summary must record readiness action plan digest"
grep -q 'readiness gate failed; production release is not ready' "${docker_failure_log}" \
  || fail "Docker failure log must explain that readiness failed"

docker_failure_gate_log="$(awk -F= '$1 == "docker-environment_log" { print substr($0, length($1) + 2); exit }' "${docker_failure_summary}")"
[[ -f "${docker_failure_gate_log}" ]] || fail "Docker failure gate log does not exist"
grep -q 'at least 12GiB free' "${docker_failure_gate_log}" \
  || fail "Docker failure gate log must include the free-space failure reason"
docker_failure_environment_report="$(awk -F= '$1 == "docker_environment_report" { print substr($0, length($1) + 2); exit }' "${docker_failure_summary}")"
[[ -f "${docker_failure_environment_report}" ]] || fail "Docker environment report does not exist"
grep -q '^status=insufficient-disk$' "${docker_failure_environment_report}" \
  || fail "Docker environment report must record insufficient-disk"

continue_failure_log="${test_root}/continue-failure.log"
continue_failure_report_dir="${test_root}/continue-failure"
continue_failure_security_dir="${test_root}/continue-security"
if env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_DF_FREE_KIB=2097152 \
  CREST_DOCKER_MIN_FREE_GB=12 \
  CREST_GITLEAKS_BIN="${test_root}/bin/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${continue_failure_security_dir}" \
  CREST_READINESS_CONTINUE_ON_FAILURE=true \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=false \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_REQUIRE_CLEAN_HISTORY=true \
  CREST_READINESS_REPORT_DIR="${continue_failure_report_dir}" \
  bash scripts/enterprise-readiness-check.sh >"${continue_failure_log}" 2>&1; then
  fail "continue-on-failure readiness check should still exit non-zero when Docker preflight fails"
fi

continue_failure_summary="${continue_failure_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${continue_failure_summary}" ]] || fail "continue-on-failure summary was not written"
grep -q '^continue_on_failure=true$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must record continue_on_failure=true"
grep -q '^docker-environment: failed$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must record docker-environment: failed"
grep -q '^docker-build: skipped-after-failed-prerequisite$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must skip Docker build after failed Docker prerequisite"
grep -q '^container-scan: skipped-after-failed-prerequisite$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must skip container scan after failed Docker prerequisite"
grep -q '^history-secret-audit: passed$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must continue to run the history audit"
grep -q "^history_secret_audit_summary=${continue_failure_security_dir}/gitleaks-history-summary.txt$" "${continue_failure_summary}" \
  || fail "continue-on-failure summary must record history audit evidence"
grep -q "^history_secret_audit_report=${continue_failure_security_dir}/gitleaks-history.json$" "${continue_failure_summary}" \
  || fail "continue-on-failure summary must record history audit report evidence"
grep -Eq '^history_secret_audit_report_sha256=[0-9a-f]{64}$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must record history audit report digest"
grep -q '^readiness_status=failed$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must still record readiness_status=failed"
grep -q '^production_release_blocker=Docker environment preflight failed$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must preserve the Docker preflight blocker"
grep -q '^production_release_blocker=Docker image build gate skipped after failed Docker prerequisite$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must explain why Docker build was skipped"
grep -q '^production_release_blocker=container image CVE gate skipped after failed Docker prerequisite$' "${continue_failure_summary}" \
  || fail "continue-on-failure summary must explain why container scan was skipped"
grep -q "^readiness_action_plan=${continue_failure_report_dir}/production-readiness-action-plan.txt$" "${continue_failure_summary}" \
  || fail "continue-on-failure summary must record readiness action plan path"
grep -q '^action=Docker environment preflight failed$' "${continue_failure_report_dir}/production-readiness-action-plan.txt" \
  || fail "continue-on-failure action plan must include Docker remediation"
grep -q 'Docker environment preflight failed; continuing to collect remaining readiness evidence' "${continue_failure_log}" \
  || fail "continue-on-failure log must explain that evidence collection continued"

history_failure_log="${test_root}/history-failure.log"
history_failure_report_dir="${test_root}/history-failure"
history_security_dir="${test_root}/history-security"
if env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_GITLEAKS_FINDINGS=true \
  CREST_GITLEAKS_BIN="${test_root}/bin/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${history_security_dir}" \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_REQUIRE_CLEAN_HISTORY=true \
  CREST_READINESS_REPORT_DIR="${history_failure_report_dir}" \
  bash scripts/enterprise-readiness-check.sh >"${history_failure_log}" 2>&1; then
  fail "readiness check should fail when required git history secret audit has findings"
fi

history_failure_summary="${history_failure_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${history_failure_summary}" ]] || fail "history failure summary was not written"
grep -q '^history-secret-audit: failed$' "${history_failure_summary}" \
  || fail "history failure summary must record history-secret-audit: failed"
grep -q '^history_secret_audit_status=findings$' "${history_failure_summary}" \
  || fail "history failure summary must record findings status"
grep -q '^history_secret_audit_findings=2$' "${history_failure_summary}" \
  || fail "history failure summary must record finding count"
grep -q '^history_secret_audit_commits=2$' "${history_failure_summary}" \
  || fail "history failure summary must record commit count"
grep -q '^history_secret_audit_remediation_required=true$' "${history_failure_summary}" \
  || fail "history failure summary must record remediation_required=true"
grep -q '^history_secret_audit_delivery_options=rotate-credentials-and-use-clean-source-or-fresh-repository$' "${history_failure_summary}" \
  || fail "history failure summary must record delivery guidance"
grep -Eq '^history_secret_audit_summary=.*gitleaks-history-summary\.txt$' "${history_failure_summary}" \
  || fail "history failure summary must record history audit summary path"
grep -q "^history_secret_audit_summary=${history_security_dir}/gitleaks-history-summary.txt$" "${history_failure_summary}" \
  || fail "history failure audit summary must stay under the configured security report dir"
grep -Eq '^history_secret_audit_summary_sha256=[0-9a-f]{64}$' "${history_failure_summary}" \
  || fail "history failure summary must record history audit summary digest"
grep -q "^history_secret_audit_report=${history_security_dir}/gitleaks-history.json$" "${history_failure_summary}" \
  || fail "history failure summary must record history audit report path"
grep -Eq '^history_secret_audit_report_sha256=[0-9a-f]{64}$' "${history_failure_summary}" \
  || fail "history failure summary must record history audit report digest"
grep -q '^production_release_blocker=git history secret audit failed$' "${history_failure_summary}" \
  || fail "history failure summary must record history audit blocker"

clean_source_log="${test_root}/clean-source.log"
clean_source_report_dir="${test_root}/clean-source-readiness"
clean_source_output_dir="${test_root}/clean-source-output"
clean_source_security_dir="${test_root}/clean-source-security"
clean_source_history_report="${clean_source_security_dir}/gitleaks-history.json"
mkdir -p "${clean_source_security_dir}"
cat > "${clean_source_history_report}" <<'JSON'
[
  {"RuleID":"generic-api-key","File":"deploy/kubernetes/crest-ob-oracle-redis.yaml","Commit":"abc"},
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml","Commit":"def"}
]
JSON

env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_GITLEAKS_BIN="${test_root}/bin/gitleaks" \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_CREATE_CLEAN_SOURCE=true \
  CREST_READINESS_REPORT_DIR="${clean_source_report_dir}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${clean_source_output_dir}" \
  CREST_CLEAN_SOURCE_NAME="crest-core-enterprise-test-source" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_source_security_dir}/gitleaks-clean-source.json" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${clean_source_history_report}" \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH="clean-source" \
  CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS="rotated-before-delivery" \
  CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES="initial-admin-password,application-encryption-key" \
  CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true \
  bash scripts/enterprise-readiness-check.sh >"${clean_source_log}" 2>&1

clean_source_summary="${clean_source_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${clean_source_summary}" ]] || fail "clean source readiness summary was not written"
grep -q '^clean-source-release: passed$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source-release: passed"
grep -q '^clean_source_require_credential_rotation=true$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record credential rotation enforcement"
grep -q "^clean_source_secret_scan_report=${clean_source_security_dir}/gitleaks-clean-source.json$" "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source secret scan report"
grep -Eq '^clean_source_secret_scan_report_sha256=[0-9a-f]{64}$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source secret scan report digest"
grep -q "^clean_source_history_scan_report=${clean_source_history_report}$" "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source history scan report"
grep -Eq '^clean_source_history_scan_report_sha256=[0-9a-f]{64}$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source history scan report digest"
grep -Eq '^history_scan_report_sha256=[0-9a-f]{64}$' "${clean_source_output_dir}/crest-core-enterprise-test-source.summary.txt" \
  || fail "clean source artifact summary must record history scan report digest"
grep -Eq '^clean_source_generated_at_utc=[0-9]{8}T[0-9]{6}Z$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source generation timestamp"
grep -q "^clean_source_version=$(tr -d '[:space:]' < VERSION)$" "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source version"
grep -Eq '^clean_source_source_branch=[^[:space:]]+$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source branch"
grep -Eq '^clean_source_source_commit=[0-9a-f]{40}$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source git commit"
grep -Eq '^clean_source_source_file_count=[1-9][0-9]*$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record clean-source file count"
grep -q '^clean_source_history_findings_remaining=2$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record history finding count"
grep -q '^clean_source_history_delivery_path=clean-source$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record history delivery path"
grep -q '^clean_source_credential_rotation_status=rotated-before-delivery$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record credential rotation status"
grep -q '^clean_source_affected_credential_classes=initial-admin-password,application-encryption-key$' "${clean_source_summary}" \
  || fail "clean source readiness summary must record affected credential classes"
if grep -q '^production_release_blocker=clean source credential rotation evidence was not recorded$' "${clean_source_summary}"; then
  fail "clean source readiness summary must not report missing rotation when rotation evidence is enforced"
fi

clean_source_no_rotation_report_dir="${test_root}/clean-source-no-rotation-readiness"
clean_source_no_rotation_output_dir="${test_root}/clean-source-no-rotation-output"
env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_GITLEAKS_BIN="${test_root}/bin/gitleaks" \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_CREATE_CLEAN_SOURCE=true \
  CREST_READINESS_REPORT_DIR="${clean_source_no_rotation_report_dir}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${clean_source_no_rotation_output_dir}" \
  CREST_CLEAN_SOURCE_NAME="crest-core-enterprise-test-source-no-rotation" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_source_security_dir}/gitleaks-clean-source-no-rotation.json" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${clean_source_history_report}" \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH="clean-source" \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/clean-source-no-rotation.log" 2>&1

clean_source_no_rotation_summary="${clean_source_no_rotation_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${clean_source_no_rotation_summary}" ]] || fail "clean source no-rotation summary was not written"
grep -q '^clean-source-release: passed$' "${clean_source_no_rotation_summary}" \
  || fail "clean source no-rotation summary must still record clean-source-release: passed"
grep -q '^clean_source_history_findings_remaining=2$' "${clean_source_no_rotation_summary}" \
  || fail "clean source no-rotation summary must record history finding count"
grep -q '^clean_source_credential_rotation_status=not-recorded$' "${clean_source_no_rotation_summary}" \
  || fail "clean source no-rotation summary must record missing credential rotation status"
grep -q '^production_release_blocker=clean source credential rotation evidence was not recorded$' "${clean_source_no_rotation_summary}" \
  || fail "clean source no-rotation summary must keep production blocked"
grep -q '^action=clean source credential rotation evidence was not recorded$' "${clean_source_no_rotation_report_dir}/production-readiness-action-plan.txt" \
  || fail "clean source no-rotation action plan must include credential rotation guidance"

clean_source_auto_history_report_dir="${test_root}/clean-source-auto-history-readiness"
clean_source_auto_history_output_dir="${test_root}/clean-source-auto-history-output"
clean_source_auto_history_security_dir="${test_root}/clean-source-auto-history-security"
mkdir -p "${clean_source_auto_history_security_dir}"
printf '[]\n' > "${clean_source_auto_history_security_dir}/gitleaks-history.json"
env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_GITLEAKS_FINDINGS=true \
  CREST_GITLEAKS_BIN="${test_root}/bin/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${clean_source_auto_history_security_dir}" \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_CREATE_CLEAN_SOURCE=true \
  CREST_READINESS_REPORT_DIR="${clean_source_auto_history_report_dir}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${clean_source_auto_history_output_dir}" \
  CREST_CLEAN_SOURCE_NAME="crest-core-enterprise-test-source-auto-history" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_source_auto_history_security_dir}/gitleaks-clean-source-auto-history.json" \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH="clean-source" \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/clean-source-auto-history.log" 2>&1

clean_source_auto_history_summary="${clean_source_auto_history_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${clean_source_auto_history_summary}" ]] || fail "clean source auto-history summary was not written"
grep -q 'running fresh non-blocking git history secret audit to support clean-source evidence' "${test_root}/clean-source-auto-history.log" \
  || fail "clean source auto-history run must explain that it refreshed history audit evidence"
grep -q '"Commit":"abc"' "${clean_source_auto_history_security_dir}/gitleaks-history.json" \
  || fail "clean source auto-history run must refresh a pre-existing history report"
grep -q '^history-secret-audit: passed$' "${clean_source_auto_history_summary}" \
  || fail "clean source auto-history summary must run supporting history audit"
grep -q "^history_secret_audit_report=${clean_source_auto_history_security_dir}/gitleaks-history.json$" "${clean_source_auto_history_summary}" \
  || fail "clean source auto-history summary must record supporting history audit report"
grep -q '^history_secret_audit_findings=2$' "${clean_source_auto_history_summary}" \
  || fail "clean source auto-history summary must record refreshed supporting history audit findings"
grep -q "^clean_source_history_scan_report=${clean_source_auto_history_security_dir}/gitleaks-history.json$" "${clean_source_auto_history_summary}" \
  || fail "clean source auto-history summary must use the supporting history report"
grep -Eq '^clean_source_history_scan_report_sha256=[0-9a-f]{64}$' "${clean_source_auto_history_summary}" \
  || fail "clean source auto-history summary must record supporting history report digest"
grep -q '^clean_source_history_findings_remaining=2$' "${clean_source_auto_history_summary}" \
  || fail "clean source auto-history summary must record numeric history finding count"
grep -q '^production_release_blocker=clean source credential rotation evidence was not recorded$' "${clean_source_auto_history_summary}" \
  || fail "clean source auto-history summary must block release until credential rotation is recorded"
if grep -q '^production_release_blocker=clean source history finding count was not verified$' "${clean_source_auto_history_summary}"; then
  fail "clean source auto-history summary must not leave history finding count unknown"
fi

overlay_report_dir="${test_root}/overlay-readiness"
overlay_dir="${test_root}/production-overlay"
overlay_evidence_dir="${test_root}/production-overlay-evidence"
env \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_RENDER_OVERLAY=true \
  CREST_READINESS_REPORT_DIR="${overlay_report_dir}" \
  CREST_PRODUCTION_OVERLAY_DIR="${overlay_dir}" \
  CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR="${overlay_evidence_dir}" \
  CREST_PRODUCTION_HOST="crest.ops01.internal" \
  CREST_ORIGIN_LIST="https://crest.ops01.internal" \
  CREST_DB_HOST="obproxy.ops01.internal" \
  CREST_DB_PORT="2883" \
  CREST_DB_USERNAME="crest_app@tenant#cluster" \
  CREST_DB_PASSWORD="database-secret-value-123" \
  CREST_AES_KEY="AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" \
  CREST_AES_IV="BBBBBBBBBBBBBBBB" \
  CREST_INITIAL_PASSWORD="initial-admin-secret-123" \
  CREST_TOKEN_SECRET="TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT" \
  CREST_REDIS_CLUSTER_NODES="redis-a.ops01.internal:6379,redis-b.ops01.internal:6379,redis-c.ops01.internal:6379" \
  CREST_REDIS_USERNAME="ops01-prod-crest-core-acl" \
  CREST_REDIS_PASSWORD="redis-secret-value-123" \
  CREST_REDIS_KEY_PREFIX="{ops01-prod-crest-core}:prod" \
  CREST_BACKEND_IMAGE="ghcr.io/sevoniva/crest-service:v1.0.0" \
  CREST_FRONTEND_IMAGE="ghcr.io/sevoniva/crest-web:v1.0.0" \
  CREST_DATA_STORAGE_CLASS="rwx-storage" \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/overlay-readiness.log" 2>&1

overlay_summary="${overlay_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${overlay_summary}" ]] || fail "overlay readiness summary was not written"
grep -q '^production-overlay-render: passed$' "${overlay_summary}" \
  || fail "overlay readiness summary must record production-overlay-render: passed"
grep -q "^production-overlay-path=${overlay_dir}$" "${overlay_summary}" \
  || fail "overlay readiness summary must record rendered overlay path"
grep -q "^production_overlay_evidence_dir=${overlay_evidence_dir}$" "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay evidence directory"
grep -q "^production_overlay_evidence_summary=${overlay_evidence_dir}/summary.txt$" "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay evidence summary"
grep -Eq '^production_overlay_evidence_summary_sha256=[0-9a-f]{64}$' "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay evidence summary digest"
grep -q "^production_overlay_evidence_manifest=${overlay_evidence_dir}/overlay-evidence-manifest.sha256$" "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay evidence manifest"
grep -Eq '^production_overlay_evidence_manifest_sha256=[0-9a-f]{64}$' "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay evidence manifest digest"
grep -q "^production_overlay_sanitized_resources=${overlay_evidence_dir}/resources-sanitized.json$" "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay resources"
grep -Eq '^production_overlay_sanitized_resources_sha256=[0-9a-f]{64}$' "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay resources digest"
grep -q "^production_overlay_sanitized_secrets=${overlay_evidence_dir}/secrets-sanitized.json$" "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay secrets"
grep -Eq '^production_overlay_sanitized_secrets_sha256=[0-9a-f]{64}$' "${overlay_summary}" \
  || fail "overlay readiness summary must record sanitized overlay secrets digest"
if grep -Eq 'database-secret-value-123|initial-admin-secret-123|redis-secret-value-123|AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA' \
  "${overlay_evidence_dir}/resources-sanitized.json" \
  "${overlay_evidence_dir}/secrets-sanitized.json"; then
  fail "overlay readiness sanitized evidence leaked Secret material"
fi

evidence_bin="${test_root}/evidence-bin"
evidence_log="${test_root}/production-evidence.log"
evidence_report_dir="${test_root}/production-evidence-readiness"
evidence_output_dir="${test_root}/production-evidence-output"
mkdir -p "${evidence_bin}"
: > "${evidence_log}"

cat > "${evidence_bin}/date" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "20260102T030405Z"
EOF

cat > "${evidence_bin}/kubectl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "kubectl $*" >> "${CREST_EVIDENCE_TEST_LOG}"
args=" $* "
if [[ "${args}" == *" version --short "* || "${args}" == *" version --short" ]]; then
  echo "Client Version: v1.34.0"
  echo "Server Version: v1.34.0"
  exit 0
fi
if [[ "${args}" == *" get secret "* && "${args}" == *" -o json"* ]]; then
  cat <<'JSON'
{"kind":"List","items":[{"metadata":{"name":"crest-db-secret"},"data":{"CREST_DB_PASSWORD":"c2VjcmV0"}},{"metadata":{"name":"crest-redis-secret"},"data":{"CREST_REDIS_PASSWORD":"cmVkaXMtc2VjcmV0"}},{"metadata":{"name":"crest-tls"},"data":{"tls.crt":"Y2VydA==","tls.key":"a2V5"}}]}
JSON
  exit 0
fi
if [[ "${args}" == *" -o json"* ]]; then
  cat <<'JSON'
{"kind":"Object","metadata":{"name":"crest-test"}}
JSON
  exit 0
fi
echo "NAME READY STATUS"
EOF

cat > "${evidence_bin}/node" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "node $*" >> "${CREST_EVIDENCE_TEST_LOG}"
case "${1:-}" in
  scripts/sanitize-kubernetes-secrets.mjs)
    cat >/dev/null
    cat <<'JSON'
{"items":[{"metadata":{"name":"crest-db-secret"},"sanitizedData":{"CREST_DB_PASSWORD":{"present":true,"decodedLength":6}}},{"metadata":{"name":"crest-redis-secret"},"sanitizedData":{"CREST_REDIS_PASSWORD":{"present":true,"decodedLength":12}}},{"metadata":{"name":"crest-tls"},"sanitizedData":{"tls.crt":{"present":true,"decodedLength":4},"tls.key":{"present":true,"decodedLength":3}}}]}
JSON
    ;;
  scripts/verify-sanitized-kubernetes-secrets.mjs)
    secrets_file="${2:-}"
    shift 2
    for required_secret in "$@"; do
      grep -q "\"name\":\"${required_secret}\"" "${secrets_file}" \
        || { echo "missing ${required_secret}" >&2; exit 1; }
    done
    if grep -q '"data"' "${secrets_file}" || grep -q '"stringData"' "${secrets_file}"; then
      echo "raw secret data leaked" >&2
      exit 1
    fi
    echo "verify-sanitized-kubernetes-secrets: passed"
    ;;
  scripts/production-runtime-check.mjs)
    echo "runtime-check: namespace crest-evidence-test passed live production runtime checks"
    ;;
  *)
    echo "unexpected node call: $*" >&2
    exit 1
    ;;
esac
EOF

chmod +x "${evidence_bin}/date" "${evidence_bin}/kubectl" "${evidence_bin}/node"

env \
  PATH="${evidence_bin}:${test_root}/bin:${PATH}" \
  CREST_EVIDENCE_TEST_LOG="${evidence_log}" \
  CREST_READINESS_SKIP_QUALITY=true \
  CREST_READINESS_SKIP_SECURITY=true \
  CREST_READINESS_SKIP_DOCKER=true \
  CREST_READINESS_SKIP_CONTAINER_SCAN=true \
  CREST_READINESS_SKIP_KIND=true \
  CREST_READINESS_COLLECT_EVIDENCE=true \
  CREST_K8S_NAMESPACE="crest-evidence-test" \
  CREST_KUBE_CONTEXT="kind-evidence-test" \
  CREST_READINESS_REPORT_DIR="${evidence_report_dir}" \
  CREST_EVIDENCE_DIR="${evidence_output_dir}" \
  bash scripts/enterprise-readiness-check.sh >"${test_root}/production-evidence-readiness.log" 2>&1

evidence_summary="${evidence_report_dir}/enterprise-readiness-summary.txt"
[[ -f "${evidence_summary}" ]] || fail "production evidence readiness summary was not written"
grep -q '^production-evidence-bundle: passed$' "${evidence_summary}" \
  || fail "production evidence readiness summary must record production-evidence-bundle: passed"
grep -q "^production_evidence_dir=${evidence_output_dir}$" "${evidence_summary}" \
  || fail "production evidence readiness summary must record evidence directory"
grep -q "^production_evidence_summary=${evidence_output_dir}/summary.txt$" "${evidence_summary}" \
  || fail "production evidence readiness summary must record evidence summary path"
grep -Eq '^production_evidence_summary_sha256=[0-9a-f]{64}$' "${evidence_summary}" \
  || fail "production evidence readiness summary must record evidence summary digest"
grep -q "^production_evidence_manifest=${evidence_output_dir}/evidence-manifest.sha256$" "${evidence_summary}" \
  || fail "production evidence readiness summary must record evidence manifest path"
grep -Eq '^production_evidence_manifest_sha256=[0-9a-f]{64}$' "${evidence_summary}" \
  || fail "production evidence readiness summary must record evidence manifest digest"
grep -q '^production_evidence_runtime_check=passed$' "${evidence_summary}" \
  || fail "production evidence readiness summary must record runtime_check=passed"
grep -q '^production_evidence_require_ingress_address=true$' "${evidence_summary}" \
  || fail "production evidence readiness summary must record ingress address runtime check requirement"
grep -q 'node scripts/verify-sanitized-kubernetes-secrets.mjs .*/secrets-sanitized.json crest-db-secret crest-redis-secret crest-tls' "${evidence_log}" \
  || fail "production evidence readiness must verify sanitized secret evidence"
grep -q 'node scripts/production-runtime-check.mjs --namespace crest-evidence-test --context kind-evidence-test --require-ingress-address' "${evidence_log}" \
  || fail "production evidence readiness must require an ingress address in runtime checks"

echo "test-enterprise-readiness-summary: passed"
