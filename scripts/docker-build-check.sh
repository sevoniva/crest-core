#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "docker-build-check: $*" >&2
  exit 1
}

info() {
  echo "docker-build-check: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

validate_bool() {
  local name="$1"
  local value="$2"
  case "${value}" in
    true|false)
      ;;
    *)
      fail "${name} must be true or false"
      ;;
  esac
}

check_base_image_available() {
  local env_name="$1"
  local image="$2"

  if [[ "${base_image_pull_check}" != "true" ]]; then
    info "base image availability preflight skipped for ${env_name}"
    return 0
  fi

  if docker image inspect "${image}" >/dev/null 2>&1; then
    info "base image already available for ${env_name}: ${image}"
    return 0
  fi

  info "pulling base image for ${env_name}: ${image}"
  if docker pull "${image}" >/dev/null; then
    info "base image pull passed for ${env_name}: ${image}"
    return 0
  fi

  fail "base image ${env_name} is not available locally and could not be pulled: ${image}. Configure ${env_name} to a reachable internal mirror with the same digest, or pre-pull/load the digest-pinned image before rerunning."
}

tag_local_base_image_alias() {
  local env_name="$1"
  local image="$2"
  local out_var="$3"
  local digest alias_name alias

  printf -v "${out_var}" '%s' "${image}"
  if [[ "${use_local_base_aliases}" != "true" || "${base_image_pull_check}" != "true" ]]; then
    return 0
  fi

  digest="${image##*@sha256:}"
  if [[ ! "${digest}" =~ ^[0-9a-fA-F]{64}$ ]]; then
    return 0
  fi

  alias_name="$(printf '%s' "${env_name}" | tr '[:upper:]_' '[:lower:]-' | tr -cd 'a-z0-9.-')"
  alias="crest-local-base/${alias_name}:sha256-${digest:0:16}"
  docker tag "${image}" "${alias}" \
    || fail "failed to create local build alias ${alias} for ${env_name}: ${image}"
  info "using local base image alias for ${env_name}: ${alias}"
  printf -v "${out_var}" '%s' "${alias}"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

# shellcheck source=scripts/lib/ensure-java17.sh
source "${repo_root}/scripts/lib/ensure-java17.sh"
ensure_java17

require_cmd docker
require_cmd mvn
require_cmd node
require_cmd pnpm

if [[ "${CREST_DOCKER_SKIP_ENV_CHECK:-false}" != "true" ]]; then
  bash scripts/docker-environment-check.sh
fi

build_artifacts="${CREST_DOCKER_BUILD_ARTIFACTS:-true}"
backend_mode="${CREST_DOCKER_BACKEND_MODE:-full}"

default_nginx_image="nginx:1.29-alpine@sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de"
jdk_image="${CREST_DOCKER_JDK_IMAGE:-eclipse-temurin:17-jdk-jammy@sha256:beabb759e6f9653c843958d1d1f5cecb881dfb85aa6081e2bef099ab1260344e}"
runtime_image="${CREST_DOCKER_RUNTIME_IMAGE:-ubuntu:24.04@sha256:786a8b558f7be160c6c8c4a54f9a57274f3b4fb1491cf65146521ae77ff1dc54}"
nginx_image="${CREST_DOCKER_NGINX_IMAGE:-${default_nginx_image}}"

backend_image="${CREST_DOCKER_BACKEND_TAG:-crest-service:local-check}"
frontend_image="${CREST_DOCKER_FRONTEND_TAG:-crest-web:local-check}"
jre_check_image="${CREST_DOCKER_JRE_CHECK_TAG:-crest-service-jre-build:local-check}"
commit_id="${CREST_DOCKER_COMMIT_ID:-local}"
frontend_buildkit="${CREST_DOCKER_FRONTEND_BUILDKIT:-false}"
backend_buildkit="${CREST_DOCKER_BACKEND_BUILDKIT:-false}"
base_image_require_digests="${CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS:-${CREST_READINESS_REQUIRE_BASE_IMAGE_DIGESTS:-true}}"
base_image_policy_report="${CREST_DOCKER_BUILD_BASE_IMAGE_POLICY_REPORT:-${CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT:-reports/readiness/docker-build-base-image-policy.txt}}"
base_image_pull_check="${CREST_DOCKER_BASE_IMAGE_PULL_CHECK:-true}"
use_local_base_aliases="${CREST_DOCKER_USE_LOCAL_BASE_ALIASES:-true}"

ensure_buildx_plugin_visible() {
  local docker_config="${DOCKER_CONFIG:-}"
  local plugin plugin_abs

  docker buildx version >/dev/null 2>&1 && return 0
  [[ -n "${docker_config}" ]] || return 0

  for plugin in \
    "${HOME:-}/.docker/cli-plugins/docker-buildx" \
    "/Applications/Docker.app/Contents/Resources/cli-plugins/docker-buildx"; do
    [[ -x "${plugin}" ]] || continue
    plugin_abs="$(cd "$(dirname "${plugin}")" && pwd -P)/$(basename "${plugin}")" || continue
    mkdir -p "${docker_config}/cli-plugins" 2>/dev/null || return 0
    ln -sf "${plugin_abs}" "${docker_config}/cli-plugins/docker-buildx" 2>/dev/null || return 0
    return 0
  done
}

ensure_buildx_plugin_visible
if [[ -z "${DOCKER_BUILDKIT:-}" ]] && docker buildx version >/dev/null 2>&1; then
  export DOCKER_BUILDKIT=1
fi

if [[ -z "${CREST_DOCKER_NGINX_IMAGE:-}" && "${DOCKER_BUILDKIT:-}" != "1" ]]; then
  docker_arch="$(docker info --format '{{.Architecture}}' 2>/dev/null || true)"
  case "${docker_arch}" in
    amd64|x86_64)
      nginx_image="nginx:1.29-alpine@sha256:3bcf852aed06467cf075c6105892e4d5a6ebbbafa0ce22d35062db9e90ddef4c"
      info "using linux/amd64 nginx manifest digest because Docker BuildKit is unavailable"
      ;;
    arm64|aarch64)
      nginx_image="nginx:1.29-alpine@sha256:7dd09a6c4f8cab9a2d2cb98fb39790f220e8bc2ea106b2cebde64b90405e0be8"
      info "using linux/arm64 nginx manifest digest because Docker BuildKit is unavailable"
      ;;
  esac
fi

validate_bool CREST_DOCKER_FRONTEND_BUILDKIT "${frontend_buildkit}"
validate_bool CREST_DOCKER_BACKEND_BUILDKIT "${backend_buildkit}"
validate_bool CREST_DOCKER_BASE_IMAGE_PULL_CHECK "${base_image_pull_check}"
validate_bool CREST_DOCKER_USE_LOCAL_BASE_ALIASES "${use_local_base_aliases}"

CREST_DOCKER_JDK_IMAGE="${jdk_image}" \
  CREST_DOCKER_RUNTIME_IMAGE="${runtime_image}" \
  CREST_DOCKER_NGINX_IMAGE="${nginx_image}" \
  CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS="${base_image_require_digests}" \
  CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${base_image_policy_report}" \
  bash scripts/container-base-image-policy-check.sh

check_base_image_available CREST_DOCKER_JDK_IMAGE "${jdk_image}"
check_base_image_available CREST_DOCKER_RUNTIME_IMAGE "${runtime_image}"
check_base_image_available CREST_DOCKER_NGINX_IMAGE "${nginx_image}"

build_jdk_image="${jdk_image}"
build_runtime_image="${runtime_image}"
build_nginx_image="${nginx_image}"
tag_local_base_image_alias CREST_DOCKER_JDK_IMAGE "${jdk_image}" build_jdk_image
tag_local_base_image_alias CREST_DOCKER_RUNTIME_IMAGE "${runtime_image}" build_runtime_image
tag_local_base_image_alias CREST_DOCKER_NGINX_IMAGE "${nginx_image}" build_nginx_image

case "${backend_mode}" in
  full|jre-build)
    ;;
  *)
    fail "CREST_DOCKER_BACKEND_MODE must be full or jre-build"
    ;;
esac

if [[ "${build_artifacts}" == "true" ]]; then
  info "building frontend production assets"
  (
    cd core/core-frontend
    pnpm install --frozen-lockfile
    pnpm run build:base
    pnpm run build:lite:check
  )

  info "packaging backend JAR"
  mvn -s .mvn/settings.xml \
    -pl :core-backend \
    -am \
    package \
    -Pstandalone \
    -DskipTests \
    -Dmaven.test.skip=true
fi

info "checking frontend dist and backend static resource parity"
node scripts/verify-static-assets.mjs

info "building frontend image ${frontend_image} from ${build_nginx_image}"
if [[ "${frontend_buildkit}" == "true" ]]; then
  docker build \
    -f Dockerfile.frontend \
    --pull=false \
    --build-arg "NGINX_IMAGE=${build_nginx_image}" \
    --build-arg "CREST_FRONTEND_COMMIT_ID=${commit_id}" \
    -t "${frontend_image}" \
    .
else
  info "using Docker legacy builder for the frontend local image gate to avoid Docker Desktop BuildKit remote metadata lookups"
  DOCKER_BUILDKIT=0 docker build \
    -f Dockerfile.frontend \
    --pull=false \
    --build-arg "NGINX_IMAGE=${build_nginx_image}" \
    --build-arg "CREST_FRONTEND_COMMIT_ID=${commit_id}" \
    -t "${frontend_image}" \
    .
fi

info "checking frontend nginx configuration syntax"
docker run --rm \
  --add-host crest-service:127.0.0.1 \
  --entrypoint nginx \
  "${frontend_image}" \
  -t

if [[ "${backend_mode}" == "full" ]]; then
  info "building backend image ${backend_image} from ${build_jdk_image} and ${build_runtime_image}"
  if [[ "${backend_buildkit}" == "true" ]]; then
    docker build \
      -f Dockerfile.backend \
      --pull=false \
      --build-arg "JDK_IMAGE=${build_jdk_image}" \
      --build-arg "RUNTIME_IMAGE=${build_runtime_image}" \
      --build-arg "CREST_BACKEND_COMMIT_ID=${commit_id}" \
      -t "${backend_image}" \
      .
  else
    info "using Docker legacy builder for the backend local image gate to avoid Docker Desktop BuildKit unload hangs"
    DOCKER_BUILDKIT=0 docker build \
      -f Dockerfile.backend \
      --pull=false \
      --build-arg "JDK_IMAGE=${build_jdk_image}" \
      --build-arg "RUNTIME_IMAGE=${build_runtime_image}" \
      --build-arg "CREST_BACKEND_COMMIT_ID=${commit_id}" \
      -t "${backend_image}" \
      .
  fi
else
  info "building backend jlink stage ${jre_check_image} from ${build_jdk_image}"
  if [[ "${backend_buildkit}" == "true" ]]; then
    docker build \
      --target jre-build \
      -f Dockerfile.backend \
      --pull=false \
      --build-arg "JDK_IMAGE=${build_jdk_image}" \
      -t "${jre_check_image}" \
      .
  else
    info "using Docker legacy builder for the backend jlink local image gate to avoid Docker Desktop BuildKit unload hangs"
    DOCKER_BUILDKIT=0 docker build \
      --target jre-build \
      -f Dockerfile.backend \
      --pull=false \
      --build-arg "JDK_IMAGE=${build_jdk_image}" \
      -t "${jre_check_image}" \
      .
  fi
fi

info "passed"
