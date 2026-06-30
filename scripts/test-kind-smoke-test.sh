#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-kind-smoke-test: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_KIND_SMOKE_DIR:-.local/kind-smoke-test-$$}"
fake_bin="${test_root}/bin"
manifest_dir="${test_root}/manifests"
log_file="${test_root}/calls.log"

rm -rf "${test_root}"
mkdir -p "${fake_bin}" "${manifest_dir}"
: > "${log_file}"

cat > "${fake_bin}/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "docker $*" >> "${CREST_KIND_TEST_LOG}"
if [[ "${1:-}" == "info" ]]; then
  exit 0
fi
case "$*" in
  "system df")
    echo "TYPE            TOTAL     ACTIVE    SIZE      RECLAIMABLE"
    echo "Build Cache     1         0         1GB       1GB"
    ;;
  "builder du")
    echo "ID              RECLAIMABLE     SIZE"
    echo "fake-cache      true            1GB"
    ;;
esac
EOF

cat > "${fake_bin}/df" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
free_kib="${CREST_KIND_TEST_FREE_KIB:-20971520}"
echo "Filesystem 1024-blocks Used Available Capacity Mounted on"
echo "/dev/fake 99999999 1 ${free_kib} 1% /"
EOF

cat > "${fake_bin}/kind" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "kind $*" >> "${CREST_KIND_TEST_LOG}"
if [[ "${1:-}" == "get" && "${2:-}" == "clusters" ]]; then
  if [[ "${CREST_KIND_TEST_EXISTING_CLUSTER:-true}" == "true" ]]; then
    echo "${CREST_KIND_CLUSTER:-crest-core}"
  fi
  exit 0
fi
if [[ "${1:-}" == "create" && "${2:-}" == "cluster" ]]; then
  exit 0
fi
EOF

cat > "${fake_bin}/kubectl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "kubectl $*" >> "${CREST_KIND_TEST_LOG}"
args=" $* "
if [[ "${args}" == *" create namespace "* ]]; then
  cat <<YAML
apiVersion: v1
kind: Namespace
metadata:
  name: ${CREST_KIND_NAMESPACE:-crest-smoke}
YAML
  exit 0
fi
if [[ "${args}" == *" apply "* && "${args}" == *" -f -"* ]]; then
  cat >/dev/null
  exit 0
fi
if [[ "${args}" == *" create --dry-run=server "* ]]; then
  echo "statefulset.apps/crest"
  exit 0
fi
EOF

cat > "${fake_bin}/node" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "node $*" >> "${CREST_KIND_TEST_LOG}"
case "${1:-}" in
  scripts/verify-kubernetes-production.mjs|scripts/production-runtime-check.mjs)
    exit 0
    ;;
  *)
    echo "unexpected node call: $*" >&2
    exit 1
    ;;
esac
EOF

chmod +x "${fake_bin}/docker" "${fake_bin}/df" "${fake_bin}/kind" "${fake_bin}/kubectl" "${fake_bin}/node"

env \
  PATH="${fake_bin}:${PATH}" \
  CREST_KIND_TEST_LOG="${log_file}" \
  CREST_KIND_MIN_FREE_GB=8 \
  CREST_KIND_CLUSTER="crest-kind-test" \
  CREST_KIND_NAMESPACE="crest-kind-smoke" \
  CREST_KIND_MANIFEST_DIR="${manifest_dir}" \
  bash scripts/kind-smoke-test.sh >/dev/null

grep -Fq "node scripts/verify-kubernetes-production.mjs ${manifest_dir}" "${log_file}" \
  || fail "dry-run must verify the configured manifest directory"
grep -Fq "docker info" "${log_file}" \
  || fail "dry-run must run Docker environment preflight"
grep -Fq "kubectl --context kind-crest-kind-test -n crest-kind-smoke create --dry-run=server -f ${manifest_dir} -o name" "${log_file}" \
  || fail "dry-run must use server-side dry-run against the configured manifest directory"
if grep -Fq "node scripts/production-runtime-check.mjs" "${log_file}"; then
  fail "dry-run mode must not run runtime checks"
fi

: > "${log_file}"
env \
  PATH="${fake_bin}:${PATH}" \
  CREST_KIND_TEST_LOG="${log_file}" \
  CREST_KIND_MIN_FREE_GB=8 \
  CREST_KIND_CLUSTER="crest-kind-test" \
  CREST_KIND_NAMESPACE="crest-kind-smoke" \
  CREST_KIND_MANIFEST_DIR="${manifest_dir}" \
  CREST_KIND_APPLY=true \
  CREST_KIND_ROLLOUT_TIMEOUT="17s" \
  bash scripts/kind-smoke-test.sh >/dev/null

grep -Fq "kubectl --context kind-crest-kind-test -n crest-kind-smoke apply -f ${manifest_dir}" "${log_file}" \
  || fail "apply mode must apply the configured manifest directory"
grep -Fq "node scripts/verify-kubernetes-production.mjs --strict-config ${manifest_dir}" "${log_file}" \
  || fail "apply mode must run strict production config checks before apply"
grep -Fq "node scripts/production-runtime-check.mjs --namespace crest-kind-smoke --context kind-crest-kind-test --timeout 17s" "${log_file}" \
  || fail "apply mode must run runtime checks by default"

: > "${log_file}"
local_kind_deps_output="${test_root}/local-kind-deps.out"
env \
  PATH="${fake_bin}:${PATH}" \
  CREST_KIND_TEST_LOG="${log_file}" \
  CREST_KIND_MIN_FREE_GB=8 \
  CREST_KIND_CLUSTER="crest-kind-test" \
  CREST_KIND_NAMESPACE="crest-kind-smoke" \
  CREST_KIND_MANIFEST_DIR="${manifest_dir}" \
  CREST_KIND_APPLY=true \
  CREST_KIND_CREATE_LOCAL_RWX_STORAGE=true \
  CREST_KIND_CREATE_LOCAL_TLS_SECRET=true \
  bash scripts/kind-smoke-test.sh >"${local_kind_deps_output}"

grep -Fq "kind-smoke: creating local kind RWX hostPath PV crest-kind-smoke-crest-data" "${local_kind_deps_output}" \
  || fail "local kind dependency mode must explain RWX PV creation"
grep -Fq "kind-smoke: creating local kind placeholder TLS Secret crest-tls" "${local_kind_deps_output}" \
  || fail "local kind dependency mode must explain placeholder TLS Secret creation"
grep -Fq "kubectl --context kind-crest-kind-test -n crest-kind-smoke create secret generic crest-tls --type=kubernetes.io/tls --from-literal=tls.crt=kind-local-placeholder --from-literal=tls.key=kind-local-placeholder --dry-run=client -o yaml" "${log_file}" \
  || fail "local kind dependency mode must create a placeholder TLS Secret"
apply_stdin_count="$(grep -Fc "kubectl --context kind-crest-kind-test apply -f -" "${log_file}" || true)"
[[ "${apply_stdin_count}" -ge 2 ]] \
  || fail "local kind dependency mode must apply namespace and local RWX PV manifests"

: > "${log_file}"
env \
  PATH="${fake_bin}:${PATH}" \
  CREST_KIND_TEST_LOG="${log_file}" \
  CREST_KIND_MIN_FREE_GB=8 \
  CREST_KIND_CLUSTER="crest-kind-test" \
  CREST_KIND_NAMESPACE="crest-kind-smoke" \
  CREST_KIND_MANIFEST_DIR="${manifest_dir}" \
  CREST_KIND_APPLY=true \
  CREST_KIND_LOAD_LOCAL_IMAGES=true \
  CREST_KIND_BACKEND_IMAGE="crest-core-service:local-check" \
  CREST_KIND_FRONTEND_IMAGE="crest-core-web:local-check" \
  CREST_KIND_LOCAL_IMAGE_TAG="sha-0123456" \
  CREST_KIND_ROLLOUT_TIMEOUT="19s" \
  bash scripts/kind-smoke-test.sh >/dev/null

grep -Fq "docker image inspect crest-core-service:local-check" "${log_file}" \
  || fail "local image mode must verify the backend image exists locally"
grep -Fq "docker image inspect crest-core-web:local-check" "${log_file}" \
  || fail "local image mode must verify the frontend image exists locally"
grep -Fq "docker tag crest-core-service:local-check crest-core-service:sha-0123456" "${log_file}" \
  || fail "local image mode must tag backend image with an immutable runtime tag"
grep -Fq "docker tag crest-core-web:local-check crest-core-web:sha-0123456" "${log_file}" \
  || fail "local image mode must tag frontend image with an immutable runtime tag"
grep -Fq "kind load docker-image --name crest-kind-test crest-core-service:sha-0123456 crest-core-web:sha-0123456" "${log_file}" \
  || fail "local image mode must load immutable images into kind"
grep -Fq "kubectl --context kind-crest-kind-test -n crest-kind-smoke set image statefulset/crest crest=crest-core-web:sha-0123456" "${log_file}" \
  || fail "local image mode must pin the web StatefulSet to the local image"
grep -Fq "kubectl --context kind-crest-kind-test -n crest-kind-smoke set image statefulset/crest-service crest-service=crest-core-service:sha-0123456" "${log_file}" \
  || fail "local image mode must pin the service StatefulSet to the local image"
if grep -Fq "statefulset/crest-worker" "${log_file}" || grep -Fq "statefulset/crest-scheduler" "${log_file}"; then
  fail "local image mode must not reference removed worker or scheduler StatefulSets"
fi
grep -Fq "node scripts/production-runtime-check.mjs --namespace crest-kind-smoke --context kind-crest-kind-test --timeout 19s" "${log_file}" \
  || fail "local image mode must still run runtime checks after pinning images"

: > "${log_file}"
local_image_dry_run_log="${test_root}/local-image-dry-run.log"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_KIND_TEST_LOG="${log_file}" \
  CREST_KIND_MIN_FREE_GB=8 \
  CREST_KIND_CLUSTER="crest-kind-test" \
  CREST_KIND_NAMESPACE="crest-kind-smoke" \
  CREST_KIND_MANIFEST_DIR="${manifest_dir}" \
  CREST_KIND_LOAD_LOCAL_IMAGES=true \
  bash scripts/kind-smoke-test.sh >"${local_image_dry_run_log}" 2>&1; then
  fail "local image loading must require apply mode"
fi
grep -q "CREST_KIND_LOAD_LOCAL_IMAGES=true requires CREST_KIND_APPLY=true" "${local_image_dry_run_log}" \
  || fail "local image dry-run failure must explain the apply requirement"
if grep -Fq "kind load docker-image" "${log_file}"; then
  fail "local image dry-run failure must happen before loading images"
fi

: > "${log_file}"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_KIND_TEST_LOG="${log_file}" \
  CREST_KIND_TEST_FREE_KIB=2097152 \
  CREST_KIND_MIN_FREE_GB=8 \
  CREST_KIND_CLUSTER="crest-kind-test" \
  CREST_KIND_NAMESPACE="crest-kind-smoke" \
  CREST_KIND_MANIFEST_DIR="${manifest_dir}" \
  bash scripts/kind-smoke-test.sh >/dev/null; then
  :
else
  fail "existing-cluster dry-run should not fail on low disk"
fi
grep -Fq "kind get clusters" "${log_file}" \
  || fail "existing-cluster dry-run should query clusters after Docker daemon check"
if grep -Fq "kind create cluster" "${log_file}"; then
  fail "existing-cluster dry-run must not create a cluster"
fi

: > "${log_file}"
low_disk_log="${test_root}/low-disk.log"
low_disk_cleanup_plan="${test_root}/docker-cleanup-plan.txt"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_KIND_TEST_LOG="${log_file}" \
  CREST_KIND_TEST_EXISTING_CLUSTER=false \
  CREST_KIND_TEST_FREE_KIB=2097152 \
  CREST_KIND_MIN_FREE_GB=8 \
  CREST_DOCKER_CLEANUP_REPORT="${low_disk_cleanup_plan}" \
  CREST_KIND_CLUSTER="crest-kind-test" \
  CREST_KIND_NAMESPACE="crest-kind-smoke" \
  CREST_KIND_MANIFEST_DIR="${manifest_dir}" \
  bash scripts/kind-smoke-test.sh >"${low_disk_log}" 2>&1; then
  fail "kind smoke test should fail before creating a missing cluster when disk is below threshold"
fi
grep -q 'kind smoke test requires at least 8GiB free' "${low_disk_log}" \
  || fail "low disk kind failure must explain the kind free-space threshold"
grep -q "Review read-only cleanup plan at ${low_disk_cleanup_plan}" "${low_disk_log}" \
  || fail "low disk kind failure must use the isolated Docker cleanup plan path"
[[ -f "${low_disk_cleanup_plan}" ]] \
  || fail "low disk kind failure must write the isolated Docker cleanup plan"
grep -Fq "kind get clusters" "${log_file}" \
  || fail "missing-cluster low disk check should query clusters before deciding whether creation is needed"
if grep -Fq "kind create cluster" "${log_file}"; then
  fail "low disk kind failure must happen before creating clusters"
fi

echo "test-kind-smoke-test: passed"
