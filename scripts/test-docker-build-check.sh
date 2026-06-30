#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-docker-build-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_DOCKER_BUILD_CHECK_DIR:-.local/docker-build-check-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/bin" "${test_root}/java-home/bin"

cat > "${test_root}/java-home/bin/java" <<'EOF'
#!/usr/bin/env bash
if [[ "$*" == *"-XshowSettings:properties"* ]]; then
  echo "    java.specification.version = 17" >&2
fi
exit 0
EOF

cat > "${test_root}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

case "${1:-}" in
  buildx)
    if [[ "${2:-}" == "version" && "${CREST_FAKE_BUILDX_AVAILABLE:-false}" == "true" ]]; then
      echo "github.com/docker/buildx v0.0.0-test"
      exit 0
    fi
    if [[ "${2:-}" == "version" && -n "${DOCKER_CONFIG:-}" && -x "${DOCKER_CONFIG}/cli-plugins/docker-buildx" ]]; then
      echo "github.com/docker/buildx v0.0.0-test"
      exit 0
    fi
    exit 1
    ;;
  info)
    if [[ "${2:-}" == "--format" ]]; then
      echo "${CREST_FAKE_DOCKER_ARCH:-arm64}"
    else
      echo "fake docker info"
    fi
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
    if [[ "${CREST_FAKE_DOCKER_PULL_FAIL:-false}" == "true" ]]; then
      echo "fake pull failure for ${2:-}" >&2
      exit 1
    fi
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

cat > "${test_root}/bin/node" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "scripts/verify-static-assets.mjs" ]]; then
  echo "static-check: fake pass"
  exit 0
fi
exec "${CREST_REAL_NODE}" "$@"
EOF

for command_name in mvn pnpm; do
  cat > "${test_root}/bin/${command_name}" <<'EOF'
#!/usr/bin/env bash
echo "unexpected build artifact command: $0 $*" >&2
exit 2
EOF
done

chmod +x \
  "${test_root}/java-home/bin/java" \
  "${test_root}/bin/docker" \
  "${test_root}/bin/node" \
  "${test_root}/bin/mvn" \
  "${test_root}/bin/pnpm"

grep -q 'Acquire::Retries=5' Dockerfile.backend \
  || fail "Dockerfile.backend apt commands should retry transient repository/network failures"
grep -q 'Acquire::http::Timeout=30' Dockerfile.backend \
  || fail "Dockerfile.backend apt commands should set an HTTP timeout"
grep -q 'Acquire::https::Timeout=30' Dockerfile.backend \
  || fail "Dockerfile.backend apt commands should set an HTTPS timeout"
grep -q 'for attempt in 1 2 3 4 5' Dockerfile.backend \
  || fail "Dockerfile.backend apt commands should retry the whole apt transaction"
grep -q '/var/cache/apt/archives/partial' Dockerfile.backend \
  || fail "Dockerfile.backend apt retry loop should clean partial package downloads"

run_check() {
  local name="$1"
  local arch="$2"
  local buildx_available="$3"
  local expected_nginx_image="$4"
  local explicit_nginx_image="${5:-}"
  local case_dir="${test_root}/${name}"
  local expected_nginx_build_image
  local expected_nginx_digest
  local default_jdk_image="eclipse-temurin:17-jdk-jammy@sha256:beabb759e6f9653c843958d1d1f5cecb881dfb85aa6081e2bef099ab1260344e"
  local default_runtime_image="ubuntu:24.04@sha256:786a8b558f7be160c6c8c4a54f9a57274f3b4fb1491cf65146521ae77ff1dc54"
  local expected_jdk_build_image="crest-local-base/crest-docker-jdk-image:sha256-beabb759e6f9653c"
  local expected_runtime_build_image="crest-local-base/crest-docker-runtime-image:sha256-786a8b558f7be160"
  mkdir -p "${case_dir}"

  local build_log="${case_dir}/docker-build.log"
  local inspect_log="${case_dir}/docker-inspect.log"
  local pull_log="${case_dir}/docker-pull.log"
  local tag_log="${case_dir}/docker-tag.log"
  local run_log="${case_dir}/docker-run.log"
  local policy_report="${case_dir}/base-image-policy.txt"
  local stdout_log="${case_dir}/stdout.log"
  : > "${build_log}"
  : > "${inspect_log}"
  : > "${pull_log}"
  : > "${tag_log}"
  : > "${run_log}"
  expected_nginx_digest="${expected_nginx_image##*@sha256:}"
  expected_nginx_build_image="crest-local-base/crest-docker-nginx-image:sha256-${expected_nginx_digest:0:16}"

  local -a env_args=(
    PATH="${test_root}/bin:${PATH}"
    HOME="${CREST_FAKE_DOCKER_HOME:-${HOME}}"
    DOCKER_CONFIG="${CREST_FAKE_DOCKER_CONFIG:-}"
    JAVA_HOME="${test_root}/java-home"
    CREST_REAL_NODE="$(command -v node)"
    CREST_FAKE_DOCKER_ARCH="${arch}"
    CREST_FAKE_BUILDX_AVAILABLE="${buildx_available}"
    CREST_FAKE_DOCKER_BUILD_LOG="${build_log}"
    CREST_FAKE_DOCKER_INSPECT_LOG="${inspect_log}"
    CREST_FAKE_DOCKER_PULL_LOG="${pull_log}"
    CREST_FAKE_DOCKER_TAG_LOG="${tag_log}"
    CREST_FAKE_DOCKER_RUN_LOG="${run_log}"
    CREST_DOCKER_SKIP_ENV_CHECK=true
    CREST_DOCKER_BUILD_ARTIFACTS=false
    CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${policy_report}"
  )
  if [[ -n "${explicit_nginx_image}" ]]; then
    env_args+=(CREST_DOCKER_NGINX_IMAGE="${explicit_nginx_image}")
  fi

  env "${env_args[@]}" bash scripts/docker-build-check.sh >"${stdout_log}" 2>&1

  grep -q -- "--build-arg NGINX_IMAGE=${expected_nginx_build_image}" "${build_log}" \
    || fail "${name} should build frontend with local alias ${expected_nginx_build_image}"
  grep -q -- "${default_jdk_image}" "${pull_log}" \
    || fail "${name} should pre-pull the digest-pinned JDK base image"
  grep -q -- "${default_runtime_image}" "${pull_log}" \
    || fail "${name} should pre-pull the digest-pinned runtime base image"
  grep -q -- "${expected_nginx_image}" "${pull_log}" \
    || fail "${name} should pre-pull the effective Nginx base image"
  grep -q -- "${expected_nginx_image} -> ${expected_nginx_build_image}" "${tag_log}" \
    || fail "${name} should tag the effective Nginx base image with a local alias"
  grep -q -- "${default_jdk_image} -> ${expected_jdk_build_image}" "${tag_log}" \
    || fail "${name} should tag the JDK base image with a local alias"
  grep -q -- "${default_runtime_image} -> ${expected_runtime_build_image}" "${tag_log}" \
    || fail "${name} should tag the runtime base image with a local alias"
  grep -q -- "--pull=false" "${build_log}" \
    || fail "${name} should disable implicit Docker pulls during image builds"
  grep -q -- "DOCKER_BUILDKIT=0 build -f Dockerfile.frontend " "${build_log}" \
    || fail "${name} should build frontend with the legacy builder by default"
  grep -q -- "DOCKER_BUILDKIT=0 .* -f Dockerfile.backend " "${build_log}" \
    || fail "${name} should build backend with the legacy builder by default"
  grep -q -- "--build-arg JDK_IMAGE=${expected_jdk_build_image}" "${build_log}" \
    || fail "${name} should build backend with the local JDK alias"
  grep -q -- "--build-arg RUNTIME_IMAGE=${expected_runtime_build_image}" "${build_log}" \
    || fail "${name} should build backend with the local runtime alias"
  grep -q "^nginx_image=${expected_nginx_image}$" "${policy_report}" \
    || fail "${name} should validate the effective Nginx image"
  grep -q '^require_base_image_digests=true$' "${policy_report}" \
    || fail "${name} should require digest-pinned base images by default"
  grep -q 'crest-web:local-check' "${run_log}" \
    || fail "${name} should still run frontend nginx syntax check"
}

default_index="nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de"
amd64_manifest="nginx:1.29-alpine@sha256:3bcf852aed06467cf075c6105892e4d5a6ebbbafa0ce22d35062db9e90ddef4c"
arm64_manifest="nginx:1.29-alpine@sha256:7dd09a6c4f8cab9a2d2cb98fb39790f220e8bc2ea106b2cebde64b90405e0be8"
custom_manifest="registry.example.internal/nginx:1.29-alpine@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

run_check "legacy-arm64" "arm64" "false" "${arm64_manifest}"
run_check "legacy-amd64" "amd64" "false" "${amd64_manifest}"
run_check "buildx-default-index" "arm64" "true" "${default_index}"
run_check "explicit-nginx-image" "arm64" "false" "${custom_manifest}" "${custom_manifest}"

isolated_home="${test_root}/isolated-home"
isolated_config="${test_root}/isolated-docker-config"
mkdir -p "${isolated_home}/.docker/cli-plugins" "${isolated_config}"
cat > "${isolated_home}/.docker/cli-plugins/docker-buildx" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${isolated_home}/.docker/cli-plugins/docker-buildx"
CREST_FAKE_DOCKER_HOME="${isolated_home}" \
  CREST_FAKE_DOCKER_CONFIG="${isolated_config}" \
  run_check "isolated-config-buildx" "arm64" "false" "${default_index}"
[[ -L "${isolated_config}/cli-plugins/docker-buildx" || -x "${isolated_config}/cli-plugins/docker-buildx" ]] \
  || fail "isolated Docker config should receive a buildx plugin link"

buildkit_case_dir="${test_root}/backend-buildkit-opt-in"
mkdir -p "${buildkit_case_dir}"
buildkit_build_log="${buildkit_case_dir}/docker-build.log"
buildkit_inspect_log="${buildkit_case_dir}/docker-inspect.log"
buildkit_pull_log="${buildkit_case_dir}/docker-pull.log"
buildkit_run_log="${buildkit_case_dir}/docker-run.log"
: > "${buildkit_build_log}"
: > "${buildkit_inspect_log}"
: > "${buildkit_pull_log}"
: > "${buildkit_run_log}"
env \
  PATH="${test_root}/bin:${PATH}" \
  JAVA_HOME="${test_root}/java-home" \
  CREST_REAL_NODE="$(command -v node)" \
  CREST_FAKE_DOCKER_ARCH="arm64" \
  CREST_FAKE_BUILDX_AVAILABLE="true" \
  CREST_FAKE_DOCKER_BUILD_LOG="${buildkit_build_log}" \
  CREST_FAKE_DOCKER_INSPECT_LOG="${buildkit_inspect_log}" \
  CREST_FAKE_DOCKER_PULL_LOG="${buildkit_pull_log}" \
  CREST_FAKE_DOCKER_TAG_LOG="${buildkit_case_dir}/docker-tag.log" \
  CREST_FAKE_DOCKER_RUN_LOG="${buildkit_run_log}" \
  CREST_DOCKER_SKIP_ENV_CHECK=true \
  CREST_DOCKER_BUILD_ARTIFACTS=false \
  CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${buildkit_case_dir}/base-image-policy.txt" \
  CREST_DOCKER_BACKEND_BUILDKIT=true \
  bash scripts/docker-build-check.sh >"${buildkit_case_dir}/stdout.log" 2>&1
grep -q -- "DOCKER_BUILDKIT=1 .* -f Dockerfile.backend " "${buildkit_build_log}" \
  || fail "backend BuildKit opt-in should keep BuildKit enabled"

pull_failure_case_dir="${test_root}/base-image-pull-failure"
mkdir -p "${pull_failure_case_dir}"
pull_failure_build_log="${pull_failure_case_dir}/docker-build.log"
pull_failure_inspect_log="${pull_failure_case_dir}/docker-inspect.log"
pull_failure_pull_log="${pull_failure_case_dir}/docker-pull.log"
pull_failure_run_log="${pull_failure_case_dir}/docker-run.log"
: > "${pull_failure_build_log}"
: > "${pull_failure_inspect_log}"
: > "${pull_failure_pull_log}"
: > "${pull_failure_run_log}"
if env \
  PATH="${test_root}/bin:${PATH}" \
  JAVA_HOME="${test_root}/java-home" \
  CREST_REAL_NODE="$(command -v node)" \
  CREST_FAKE_DOCKER_ARCH="arm64" \
  CREST_FAKE_BUILDX_AVAILABLE="true" \
  CREST_FAKE_DOCKER_BUILD_LOG="${pull_failure_build_log}" \
  CREST_FAKE_DOCKER_INSPECT_LOG="${pull_failure_inspect_log}" \
  CREST_FAKE_DOCKER_PULL_LOG="${pull_failure_pull_log}" \
  CREST_FAKE_DOCKER_TAG_LOG="${pull_failure_case_dir}/docker-tag.log" \
  CREST_FAKE_DOCKER_RUN_LOG="${pull_failure_run_log}" \
  CREST_FAKE_DOCKER_PULL_FAIL=true \
  CREST_DOCKER_SKIP_ENV_CHECK=true \
  CREST_DOCKER_BUILD_ARTIFACTS=false \
  CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${pull_failure_case_dir}/base-image-policy.txt" \
  bash scripts/docker-build-check.sh >"${pull_failure_case_dir}/stdout.log" 2>&1; then
  fail "docker-build-check should fail before artifact builds when a base image cannot be pulled"
fi
grep -q 'base image CREST_DOCKER_JDK_IMAGE is not available locally and could not be pulled' "${pull_failure_case_dir}/stdout.log" \
  || fail "base image pull failure should explain the unreachable JDK base image"
[[ ! -s "${pull_failure_build_log}" ]] \
  || fail "base image pull failure should stop before Docker image builds"

echo "test-docker-build-check: passed"
