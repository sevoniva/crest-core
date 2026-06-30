#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "production-evidence-bundle: $*" >&2
  exit 1
}

info() {
  echo "production-evidence-bundle: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

resolve_lexical_path() {
  local path="$1"
  local raw part joined remainder
  local -a stack
  if [[ "${path}" == /* ]]; then
    raw="${path}"
  else
    raw="${repo_root}/${path}"
  fi
  remainder="${raw#/}"
  while [[ -n "${remainder}" ]]; do
    if [[ "${remainder}" == */* ]]; then
      part="${remainder%%/*}"
      remainder="${remainder#*/}"
    else
      part="${remainder}"
      remainder=""
    fi
    case "${part}" in
      ""|.)
        ;;
      ..)
        if ((${#stack[@]} > 0)); then
          unset 'stack[${#stack[@]}-1]'
        fi
        ;;
      *)
        stack+=("${part}")
        ;;
    esac
  done
  if ((${#stack[@]} == 0)); then
    printf '/'
  else
    joined="${stack[0]}"
    for part in "${stack[@]:1}"; do
      joined="${joined}/${part}"
    done
    printf '/%s' "${joined}"
  fi
}

normalize_path() {
  local path="$1"
  local logical parent base ancestor suffix ancestor_real
  logical="$(resolve_lexical_path "${path}")"
  parent="$(dirname "${logical}")"
  base="$(basename "${logical}")"
  ancestor="${parent}"
  while [[ ! -e "${ancestor}" && "${ancestor}" != "/" ]]; do
    ancestor="$(dirname "${ancestor}")"
  done
  [[ -d "${ancestor}" ]] || fail "CREST_EVIDENCE_DIR parent path is not a directory: ${path}"
  ancestor_real="$(cd "${ancestor}" && pwd -P)"
  suffix="${parent#"${ancestor}"}"
  printf '%s%s/%s' "${ancestor_real}" "${suffix}" "${base}"
}

assert_safe_evidence_dir() {
  local path="$1"
  local normalized
  [[ -n "${path}" ]] || fail "CREST_EVIDENCE_DIR must not be empty"
  case "${path}" in
    /|.|..)
      fail "CREST_EVIDENCE_DIR is too broad to write evidence into: ${path}"
      ;;
  esac
  normalized="$(normalize_path "${path}")"
  case "${normalized}" in
    "${repo_root}"|"$(dirname "${repo_root}")"|\
    "${repo_root}/.local"|"${repo_root}/reports"|"${repo_root}/reports/readiness"|\
    "${repo_root}/.git"|"${repo_root}/.git/"*|\
    "${repo_root}/deploy"|"${repo_root}/deploy/"*)
      fail "CREST_EVIDENCE_DIR is too broad to write evidence into: ${path}"
      ;;
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_EVIDENCE_DIR must stay inside the repository: ${path}"
      ;;
  esac
  printf '%s' "${normalized}"
}

require_cmd kubectl
require_cmd node
require_cmd date

namespace="${CREST_K8S_NAMESPACE:-crest}"
context="${CREST_KUBE_CONTEXT:-}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output_dir="${CREST_EVIDENCE_DIR:-reports/readiness/evidence-${namespace}-${timestamp}}"
output_dir="$(assert_safe_evidence_dir "${output_dir}")"
require_ingress_address="${CREST_EVIDENCE_REQUIRE_INGRESS_ADDRESS:-${CREST_REQUIRE_INGRESS_ADDRESS:-true}}"
case "${require_ingress_address}" in
  true|false)
    ;;
  *)
    fail "CREST_EVIDENCE_REQUIRE_INGRESS_ADDRESS must be true or false"
    ;;
esac

kubectl_base=()
if [[ -n "${context}" ]]; then
  kubectl_base+=(--context "${context}")
fi

k() {
  kubectl "${kubectl_base[@]}" -n "${namespace}" "$@"
}

k_cluster() {
  kubectl "${kubectl_base[@]}" "$@"
}

file_sha256() {
  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${path}" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
  else
    fail "missing shasum or sha256sum for evidence manifest"
  fi
}

write_evidence_manifest() {
  local manifest="${output_dir}/evidence-manifest.sha256"
  local file_count

  file_count="$(
    cd "${output_dir}"
    find . -type f ! -name 'evidence-manifest.sha256' -print | LC_ALL=C sort | wc -l | tr -d '[:space:]'
  )"
  {
    echo "evidence_file_count=${file_count}"
    echo "evidence_manifest=evidence-manifest.sha256"
  } >> "${output_dir}/summary.txt"

  : > "${manifest}"
  while IFS= read -r file; do
    local rel_path="${file#./}"
    printf '%s  %s\n' "$(file_sha256 "${output_dir}/${rel_path}")" "${rel_path}" >> "${manifest}"
  done < <(
    cd "${output_dir}"
    find . -type f ! -name 'evidence-manifest.sha256' -print | LC_ALL=C sort
  )
}

mkdir -p "${output_dir}"

{
  echo "Crest Core production evidence bundle"
  echo "timestamp_utc=${timestamp}"
  echo "namespace=${namespace}"
  if [[ -n "${context}" ]]; then
    echo "context=${context}"
  fi
  echo "runtime_check_require_ingress_address=${require_ingress_address}"
} > "${output_dir}/summary.txt"

info "collecting cluster and namespace state into ${output_dir}"

k_cluster version --short > "${output_dir}/kubectl-version.txt" 2>&1 || true
k_cluster get namespace "${namespace}" -o json > "${output_dir}/namespace.json"
k get statefulsets -o wide > "${output_dir}/statefulsets.txt"
k get pods -o wide > "${output_dir}/pods.txt"
k get services -o wide > "${output_dir}/services.txt"
k get ingress -o wide > "${output_dir}/ingress.txt"
k get pvc -o wide > "${output_dir}/pvc.txt"
k get pdb -o wide > "${output_dir}/pdb.txt"
k get networkpolicy -o wide > "${output_dir}/networkpolicies.txt"
k get events --sort-by='.lastTimestamp' > "${output_dir}/events.txt" || true

for resource in \
  statefulset/crest \
  statefulset/crest-service \
  service/crest \
  service/crest-service \
  service/crest-headless \
  service/crest-service-headless \
  ingress/crest \
  pvc/crest-data \
  pdb/crest \
  pdb/crest-service \
  networkpolicy/crest-web \
  networkpolicy/crest-service \
  configmap/crest-env; do
  safe_name="${resource//\//-}"
  k get "${resource}" -o json > "${output_dir}/${safe_name}.json"
done

k get secret crest-db-secret crest-redis-secret crest-tls -o json \
  | node scripts/sanitize-kubernetes-secrets.mjs > "${output_dir}/secrets-sanitized.json"
node scripts/verify-sanitized-kubernetes-secrets.mjs \
  "${output_dir}/secrets-sanitized.json" \
  crest-db-secret \
  crest-redis-secret \
  crest-tls >/dev/null

if [[ "${CREST_EVIDENCE_RUN_RUNTIME_CHECK:-true}" == "true" ]]; then
  info "running live production runtime check"
  runtime_args=(--namespace "${namespace}")
  if [[ -n "${context}" ]]; then
    runtime_args+=(--context "${context}")
  fi
  if [[ "${require_ingress_address}" == "true" ]]; then
    runtime_args+=(--require-ingress-address)
  fi
  if node scripts/production-runtime-check.mjs "${runtime_args[@]}" > "${output_dir}/production-runtime-check.txt" 2>&1; then
    echo "runtime_check=passed" >> "${output_dir}/summary.txt"
  else
    cat "${output_dir}/production-runtime-check.txt" >&2 || true
    fail "production runtime check failed; evidence bundle kept at ${output_dir}"
  fi
else
  echo "runtime_check=skipped" >> "${output_dir}/summary.txt"
fi

write_evidence_manifest

info "evidence bundle written to ${output_dir}"
