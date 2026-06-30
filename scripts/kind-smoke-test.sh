#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "kind-smoke: $*" >&2
  exit 1
}

info() {
  echo "kind-smoke: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

cluster="${CREST_KIND_CLUSTER:-crest-core}"
namespace="${CREST_KIND_NAMESPACE:-crest-smoke}"
context="kind-${cluster}"
manifest_dir="${CREST_KIND_MANIFEST_DIR:-deploy/kubernetes}"
apply_manifests="${CREST_KIND_APPLY:-false}"
collect_evidence="${CREST_KIND_COLLECT_EVIDENCE:-false}"
run_runtime_check="${CREST_KIND_RUN_RUNTIME_CHECK:-true}"
rollout_timeout="${CREST_KIND_ROLLOUT_TIMEOUT:-300s}"
load_local_images="${CREST_KIND_LOAD_LOCAL_IMAGES:-false}"
create_local_rwx_storage="${CREST_KIND_CREATE_LOCAL_RWX_STORAGE:-false}"
create_local_tls_secret="${CREST_KIND_CREATE_LOCAL_TLS_SECRET:-false}"
local_image_tag="${CREST_KIND_LOCAL_IMAGE_TAG:-sha-0000000}"
backend_source_image="${CREST_KIND_BACKEND_IMAGE:-crest-service:local-check}"
frontend_source_image="${CREST_KIND_FRONTEND_IMAGE:-crest-web:local-check}"
backend_runtime_image="${CREST_KIND_BACKEND_RUNTIME_IMAGE:-crest-service:${local_image_tag}}"
frontend_runtime_image="${CREST_KIND_FRONTEND_RUNTIME_IMAGE:-crest-web:${local_image_tag}}"
local_rwx_pv_name="${CREST_KIND_LOCAL_RWX_PV_NAME:-${namespace}-crest-data}"
local_rwx_host_path="${CREST_KIND_LOCAL_RWX_HOST_PATH:-/tmp/crest-kind/${namespace}/data}"
local_rwx_storage_size="${CREST_KIND_LOCAL_RWX_STORAGE_SIZE:-50Gi}"

require_cmd docker
require_cmd kind
require_cmd kubectl
require_cmd node

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

validate_image_tag() {
  local image="$1"
  local name="$2"
  local tag
  if [[ "${image}" == *"@sha256:"* ]]; then
    return
  fi
  tag="${image##*:}"
  if [[ "${tag}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ || "${tag}" =~ ^sha-[0-9a-f]{7,40}$ ]]; then
    return
  fi
  fail "${name} must use an immutable vX.Y.Z tag, sha-<commit> tag, or digest"
}

prepare_local_kind_images() {
  if [[ "${load_local_images}" != "true" ]]; then
    return 0
  fi
  [[ "${apply_manifests}" == "true" ]] \
    || fail "CREST_KIND_LOAD_LOCAL_IMAGES=true requires CREST_KIND_APPLY=true"

  validate_image_tag "${backend_runtime_image}" "CREST_KIND_BACKEND_RUNTIME_IMAGE"
  validate_image_tag "${frontend_runtime_image}" "CREST_KIND_FRONTEND_RUNTIME_IMAGE"

  info "validating local Docker images for kind"
  docker image inspect "${backend_source_image}" >/dev/null
  docker image inspect "${frontend_source_image}" >/dev/null

  if [[ "${backend_source_image}" != "${backend_runtime_image}" ]]; then
    docker tag "${backend_source_image}" "${backend_runtime_image}"
  fi
  if [[ "${frontend_source_image}" != "${frontend_runtime_image}" ]]; then
    docker tag "${frontend_source_image}" "${frontend_runtime_image}"
  fi

  info "loading local images into kind cluster ${cluster}"
  kind load docker-image --name "${cluster}" "${backend_runtime_image}" "${frontend_runtime_image}"
}

apply_local_kind_images() {
  if [[ "${load_local_images}" != "true" ]]; then
    return 0
  fi
  info "pinning kind deployments to local images"
  kubectl --context "${context}" -n "${namespace}" set image deployment/crest "crest=${frontend_runtime_image}"
  kubectl --context "${context}" -n "${namespace}" set image deployment/crest-service "crest-service=${backend_runtime_image}"
}

prepare_local_kind_storage() {
  if [[ "${create_local_rwx_storage}" != "true" ]]; then
    return 0
  fi

  info "creating local kind RWX hostPath PV ${local_rwx_pv_name}"
  kubectl --context "${context}" apply -f - <<EOF
apiVersion: v1
kind: PersistentVolume
metadata:
  name: ${local_rwx_pv_name}
  labels:
    app.kubernetes.io/name: crest
    app.kubernetes.io/component: kind-local-storage
spec:
  capacity:
    storage: ${local_rwx_storage_size}
  accessModes:
    - ReadWriteMany
  persistentVolumeReclaimPolicy: Delete
  storageClassName: standard
  hostPath:
    path: ${local_rwx_host_path}
    type: DirectoryOrCreate
EOF
}

prepare_local_kind_tls_secret() {
  if [[ "${create_local_tls_secret}" != "true" ]]; then
    return 0
  fi

  info "creating local kind placeholder TLS Secret crest-tls"
  kubectl --context "${context}" -n "${namespace}" create secret generic crest-tls \
    --type=kubernetes.io/tls \
    --from-literal=tls.crt=kind-local-placeholder \
    --from-literal=tls.key=kind-local-placeholder \
    --dry-run=client \
    -o yaml \
    | kubectl --context "${context}" -n "${namespace}" apply -f -
}

validate_bool "CREST_KIND_APPLY" "${apply_manifests}"
validate_bool "CREST_KIND_COLLECT_EVIDENCE" "${collect_evidence}"
validate_bool "CREST_KIND_RUN_RUNTIME_CHECK" "${run_runtime_check}"
validate_bool "CREST_KIND_LOAD_LOCAL_IMAGES" "${load_local_images}"
validate_bool "CREST_KIND_CREATE_LOCAL_RWX_STORAGE" "${create_local_rwx_storage}"
validate_bool "CREST_KIND_CREATE_LOCAL_TLS_SECRET" "${create_local_tls_secret}"
if [[ "${load_local_images}" == "true" && "${apply_manifests}" != "true" ]]; then
  fail "CREST_KIND_LOAD_LOCAL_IMAGES=true requires CREST_KIND_APPLY=true"
fi

run_docker_environment_check() {
  local skip_disk="$1"
  if [[ "${CREST_KIND_SKIP_ENV_CHECK:-false}" == "true" ]]; then
    return
  fi
  if [[ "${skip_disk}" == "true" ]]; then
    env \
      CREST_DOCKER_ENVIRONMENT_PURPOSE="kind smoke test" \
      CREST_DOCKER_PRECHECK_SKIP_DISK=true \
      bash scripts/docker-environment-check.sh
    return
  fi
  env \
    CREST_DOCKER_ENVIRONMENT_PURPOSE="kind smoke test" \
    CREST_DOCKER_MIN_FREE_GB="${CREST_KIND_MIN_FREE_GB:-8}" \
    bash scripts/docker-environment-check.sh
}

if [[ "${CREST_KIND_SKIP_ENV_CHECK:-false}" != "true" ]]; then
  run_docker_environment_check true
fi

existing_clusters="$(kind get clusters)"
if ! grep -Fxq "${cluster}" <<< "${existing_clusters}"; then
  run_docker_environment_check false
  info "creating kind cluster ${cluster}"
  kind create cluster --name "${cluster}"
elif [[ "${apply_manifests}" == "true" ]]; then
  run_docker_environment_check false
else
  info "using existing kind cluster ${cluster}; disk free-space check is not required for server-side dry-run"
fi

kubectl cluster-info --context "${context}" >/dev/null
[[ -d "${manifest_dir}" ]] || fail "missing manifest directory: ${manifest_dir}"

kubectl --context "${context}" create namespace "${namespace}" --dry-run=client -o yaml | kubectl --context "${context}" apply -f - >/dev/null

node scripts/verify-kubernetes-production.mjs "${manifest_dir}"
kubectl --context "${context}" -n "${namespace}" create --dry-run=server -f "${manifest_dir}" -o name >/dev/null

info "server-side dry-run passed in ${context}/${namespace}"

if [[ "${apply_manifests}" == "true" ]]; then
  info "CREST_KIND_APPLY=true, running strict production config gate before apply"
  bash scripts/production-config-check.sh "${manifest_dir}"

  prepare_local_kind_images
  prepare_local_kind_storage

  info "CREST_KIND_APPLY=true, applying manifests from ${manifest_dir}"
  kubectl --context "${context}" -n "${namespace}" apply -f "${manifest_dir}"
  prepare_local_kind_tls_secret
  apply_local_kind_images

  if [[ "${collect_evidence}" == "true" ]]; then
    info "CREST_KIND_COLLECT_EVIDENCE=true, collecting kind evidence bundle"
    CREST_K8S_NAMESPACE="${namespace}" \
      CREST_KUBE_CONTEXT="${context}" \
      CREST_EVIDENCE_DIR="${CREST_KIND_EVIDENCE_DIR:-reports/readiness/kind-evidence-${namespace}}" \
      bash scripts/production-evidence-bundle.sh
  elif [[ "${run_runtime_check}" == "true" ]]; then
    info "running kind runtime check after apply"
    node scripts/production-runtime-check.mjs \
      --namespace "${namespace}" \
      --context "${context}" \
      --timeout "${rollout_timeout}"
  else
    fail "CREST_KIND_APPLY=true requires runtime validation; set CREST_KIND_COLLECT_EVIDENCE=true to keep evidence, or leave CREST_KIND_APPLY=false for dry-run only"
  fi
fi
