#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "container-image-scan: $*" >&2
  exit 1
}

info() {
  echo "container-image-scan: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

safe_report_name() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9_.-' '_'
}

prepare_report_dir() {
  local requested_dir="$1"
  local requested_abs parent_dir base_name resolved_parent resolved_dir

  [[ -n "${requested_dir}" ]] || fail "CREST_CONTAINER_SCAN_REPORT_DIR must not be empty"
  case "${requested_dir}" in
    /|.|..|../*|*/../*)
      fail "refusing unsafe container scan report directory: ${requested_dir}"
      ;;
  esac

  case "${requested_dir}" in
    /*)
      requested_abs="${requested_dir}"
      ;;
    *)
      requested_abs="${repo_root}/${requested_dir}"
      ;;
  esac
  case "${requested_abs}" in
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_CONTAINER_SCAN_REPORT_DIR must stay inside the repository: ${requested_dir}"
      ;;
  esac

  parent_dir="$(dirname "${requested_abs}")"
  base_name="$(basename "${requested_abs}")"
  [[ -n "${base_name}" && "${base_name}" != "." && "${base_name}" != ".." ]] \
    || fail "refusing unsafe container scan report directory: ${requested_dir}"

  mkdir -p "${parent_dir}"
  resolved_parent="$(cd "${parent_dir}" && pwd -P)"
  resolved_dir="${resolved_parent}/${base_name}"
  case "${resolved_dir}" in
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_CONTAINER_SCAN_REPORT_DIR must stay inside the repository: ${requested_dir}"
      ;;
  esac
  [[ "${resolved_dir}" != "${repo_root}" ]] || fail "refusing to use repository root as container scan report directory"

  rm -rf "${resolved_dir}"
  mkdir -p "${resolved_dir}"
  printf '%s' "${resolved_dir}"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

trivy_bin="${CREST_TRIVY_BIN:-trivy}"
trivy_docker_image="${CREST_TRIVY_DOCKER_IMAGE:-aquasec/trivy:${CREST_TRIVY_VERSION:-0.71.2}}"
report_dir="${CREST_CONTAINER_SCAN_REPORT_DIR:-reports/container}"
severity="${CREST_TRIVY_SEVERITY:-HIGH,CRITICAL}"
timeout="${CREST_TRIVY_TIMEOUT:-10m}"
ignore_unfixed="${CREST_TRIVY_IGNORE_UNFIXED:-false}"
skip_db_update="${CREST_TRIVY_SKIP_DB_UPDATE:-false}"
skip_java_db_update="${CREST_TRIVY_SKIP_JAVA_DB_UPDATE:-false}"
skip_java_artifacts="${CREST_CONTAINER_SCAN_SKIP_JAVA_ARTIFACTS:-${CREST_TRIVY_SKIP_JAVA_ARTIFACTS:-false}}"
pkg_types="${CREST_TRIVY_PKG_TYPES:-}"
cache_dir="${CREST_TRIVY_CACHE_DIR:-.cache/trivy}"
default_db_repositories="ghcr.io/aquasecurity/trivy-db:2,public.ecr.aws/aquasecurity/trivy-db:2,mirror.gcr.io/aquasec/trivy-db:2"
default_java_db_repositories="ghcr.io/aquasecurity/trivy-java-db:1,public.ecr.aws/aquasecurity/trivy-java-db:1,mirror.gcr.io/aquasec/trivy-java-db:1"
db_repositories_raw="${CREST_TRIVY_DB_REPOSITORIES:-${CREST_TRIVY_DB_REPOSITORY:-${default_db_repositories}}}"
java_db_repositories_raw="${CREST_TRIVY_JAVA_DB_REPOSITORIES:-${CREST_TRIVY_JAVA_DB_REPOSITORY:-${default_java_db_repositories}}}"
rootfs_fallback="${CREST_CONTAINER_SCAN_ROOTFS_FALLBACK:-true}"
normalized_severity="$(printf '%s' "${severity}" | tr '[:lower:]' '[:upper:]')"
java_artifact_scan_reason="covered-by-maven-sbom-osv-sca"
java_artifact_skip_files=("/opt/apps/app.jar" "opt/apps/app.jar" "/opt/apps/drivers/*.jar" "opt/apps/drivers/*.jar")
interrupted=false
db_repositories=()
java_db_repositories=()

split_repository_list() {
  local name="$1"
  local raw="$2"
  local normalized repository

  normalized="${raw//,/ }"
  for repository in ${normalized}; do
    [[ -n "${repository}" ]] || continue
    case "${repository}" in
      -*)
        fail "${name} contains an unsafe repository value: ${repository}"
        ;;
    esac
    printf '%s\n' "${repository}"
  done
}

while IFS= read -r repository; do
  db_repositories+=("${repository}")
done < <(split_repository_list "CREST_TRIVY_DB_REPOSITORIES" "${db_repositories_raw}")
while IFS= read -r repository; do
  java_db_repositories+=("${repository}")
done < <(split_repository_list "CREST_TRIVY_JAVA_DB_REPOSITORIES" "${java_db_repositories_raw}")

[[ "${#db_repositories[@]}" -gt 0 ]] || fail "CREST_TRIVY_DB_REPOSITORIES must contain at least one repository"
[[ "${#java_db_repositories[@]}" -gt 0 ]] || fail "CREST_TRIVY_JAVA_DB_REPOSITORIES must contain at least one repository"

case ",${normalized_severity}," in
  *,HIGH,*)
    ;;
  *)
    fail "CREST_TRIVY_SEVERITY must include HIGH"
    ;;
esac
case ",${normalized_severity}," in
  *,CRITICAL,*)
    ;;
  *)
    fail "CREST_TRIVY_SEVERITY must include CRITICAL"
    ;;
esac
case "${rootfs_fallback}" in
  true|false)
    ;;
  *)
    fail "CREST_CONTAINER_SCAN_ROOTFS_FALLBACK must be true or false"
    ;;
esac
case "${skip_java_db_update}" in
  true|false)
    ;;
  *)
    fail "CREST_TRIVY_SKIP_JAVA_DB_UPDATE must be true or false"
    ;;
esac
case "${skip_java_artifacts}" in
  true|false)
    ;;
  *)
    fail "CREST_CONTAINER_SCAN_SKIP_JAVA_ARTIFACTS or CREST_TRIVY_SKIP_JAVA_ARTIFACTS must be true or false"
    ;;
esac
if [[ -z "${pkg_types}" && "${skip_java_artifacts}" == "true" ]]; then
  pkg_types="os"
fi
if [[ -n "${pkg_types}" ]]; then
  normalized_pkg_types="$(printf '%s' "${pkg_types}" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]')"
  pkg_types="${normalized_pkg_types}"
  case ",${pkg_types}," in
    *,os,*)
      ;;
    *)
      fail "CREST_TRIVY_PKG_TYPES must include os"
      ;;
  esac
  case "${pkg_types}" in
    os|library|os,library|library,os)
      ;;
    *)
      fail "CREST_TRIVY_PKG_TYPES must contain only os and library"
      ;;
  esac
fi

trap 'interrupted=true' INT TERM

scanner_mode="binary"
if command -v "${trivy_bin}" >/dev/null 2>&1; then
  scanner_mode="binary"
elif [[ -n "${CREST_TRIVY_BIN:-}" ]]; then
  fail "missing required command: ${trivy_bin}"
else
  require_cmd docker
  scanner_mode="docker"
fi

report_dir="$(prepare_report_dir "${report_dir}")"
mkdir -p "${cache_dir}"

if [[ -n "${CREST_CONTAINER_SCAN_IMAGES:-}" ]]; then
  IFS=',' read -r -a image_refs <<< "${CREST_CONTAINER_SCAN_IMAGES}"
else
  image_refs=(
    "frontend=${CREST_DOCKER_FRONTEND_TAG:-crest-web:local-check}"
    "backend=${CREST_DOCKER_BACKEND_TAG:-crest-service:local-check}"
  )
fi

if [[ "${skip_java_artifacts}" == "true" ]]; then
  info "Java application artifacts will be excluded from Trivy container scans (${java_artifact_scan_reason}); Trivy pkg types: ${pkg_types:-os,library}"
fi

scan_status=0

scan_image() {
  local image_ref="$1"
  local name image output log_file image_scan_status
  if [[ "${image_ref}" == *"="* ]]; then
    name="${image_ref%%=*}"
    image="${image_ref#*=}"
  else
    name="$(safe_report_name "${image_ref}")"
    image="${image_ref}"
  fi
  [[ -n "${image}" ]] || fail "empty image reference in CREST_CONTAINER_SCAN_IMAGES"

  output="${report_dir}/trivy-$(safe_report_name "${name}").json"
  log_file="${report_dir}/trivy-$(safe_report_name "${name}").log"
  info "scanning ${image} for ${severity} vulnerabilities"

  local args=(
    image
    --scanners vuln
    --severity "${severity}"
    --exit-code 1
    --format json
    --output "${output}"
    --cache-dir "${cache_dir}"
    --timeout "${timeout}"
  )
  if [[ "${ignore_unfixed}" == "true" ]]; then
    args+=(--ignore-unfixed)
  fi
  if [[ "${skip_db_update}" == "true" ]]; then
    args+=(--skip-db-update)
  fi
  if [[ "${skip_java_db_update}" == "true" ]]; then
    args+=(--skip-java-db-update)
  fi
  local repository
  for repository in "${db_repositories[@]}"; do
    args+=(--db-repository "${repository}")
  done
  for repository in "${java_db_repositories[@]}"; do
    args+=(--java-db-repository "${repository}")
  done
  if [[ -n "${pkg_types}" ]]; then
    args+=(--pkg-types "${pkg_types}")
  fi
  if [[ "${skip_java_artifacts}" == "true" ]]; then
    local java_artifact_skip_file
    for java_artifact_skip_file in "${java_artifact_skip_files[@]}"; do
      args+=(--skip-files "${java_artifact_skip_file}")
    done
  fi

  if [[ "${scanner_mode}" == "binary" ]]; then
    image_scan_status=0
    "${trivy_bin}" "${args[@]}" "${image}" 2>"${log_file}" || image_scan_status=$?
    cat "${log_file}" >&2
    if is_valid_trivy_json "${output}"; then
      annotate_report_metadata "${output}" "${image}" "${name}" "container-image"
      return "${image_scan_status}"
    fi
    if [[ "${rootfs_fallback}" != "true" ]]; then
      return "${image_scan_status}"
    fi
    scan_rootfs_fallback "${image}" "${name}" "${output}"
  else
    local docker_scan_status
    docker_scan_status=0
    docker run --rm \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v "${repo_root}:${repo_root}" \
      -w "${repo_root}" \
      "${trivy_docker_image}" \
      "${args[@]}" "${image}" || docker_scan_status=$?
    if is_valid_trivy_json "${output}"; then
      annotate_report_metadata "${output}" "${image}" "${name}" "container-image"
    fi
    return "${docker_scan_status}"
  fi
}

is_valid_trivy_json() {
  local report_file="$1"
  [[ -s "${report_file}" ]] || return 1
  node -e 'JSON.parse(require("node:fs").readFileSync(process.argv[1], "utf8"))' "${report_file}" >/dev/null 2>&1
}

annotate_report_metadata() {
  local report_file="$1"
  local image="$2"
  local name="$3"
  local scan_mode="$4"

  CREST_TRIVY_EFFECTIVE_PKG_TYPES="${pkg_types}" node -e '
    const fs = require("node:fs");
    const [file, image, name, scanMode, skipJavaArtifacts, skipReason] = process.argv.slice(1);
    const report = JSON.parse(fs.readFileSync(file, "utf8"));
    const javaArtifactsSkipped = skipJavaArtifacts === "true";
    const pkgTypes = process.env.CREST_TRIVY_EFFECTIVE_PKG_TYPES || "";
    const metadata = {
      ...(report.Metadata || {}),
      CrestScanMode: scanMode,
      CrestContainerImage: image,
      CrestContainerName: name,
      CrestJavaArtifactScanSkipped: javaArtifactsSkipped,
      CrestTrivyPkgTypes: pkgTypes || "os,library"
    };
    if (javaArtifactsSkipped) {
      metadata.CrestJavaArtifactScanReason = skipReason;
    } else {
      delete metadata.CrestJavaArtifactScanReason;
    }
    report.Metadata = metadata;
    fs.writeFileSync(file, `${JSON.stringify(report, null, 2)}\n`);
  ' "${report_file}" "${image}" "${name}" "${scan_mode}" "${skip_java_artifacts}" "${java_artifact_scan_reason}"
}

remove_rootfs_java_artifacts() {
  local rootfs_dir="$1"

  [[ "${skip_java_artifacts}" == "true" ]] || return 0
  rm -f "${rootfs_dir}/opt/apps/app.jar"
  find "${rootfs_dir}/opt/apps/drivers" -maxdepth 1 -type f -name '*.jar' -delete 2>/dev/null || true
}

scan_rootfs_fallback() {
  local image="$1"
  local name="$2"
  local output="$3"
  local container_id rootfs_dir rootfs_status

  require_cmd docker
  require_cmd tar
  rootfs_dir="$(mktemp -d "${TMPDIR:-/tmp}/crest-container-rootfs.XXXXXX")"
  container_id=""
  cleanup_rootfs() {
    if [[ -n "${container_id:-}" ]]; then
      docker rm -f "${container_id}" >/dev/null 2>&1 || true
    fi
    if [[ -n "${rootfs_dir:-}" ]]; then
      rm -rf "${rootfs_dir}"
    fi
  }

  info "image scan did not produce valid JSON for ${image}; retrying ${name} via docker export rootfs fallback"
  container_id="$(docker create "${image}")" || {
    cleanup_rootfs
    return 1
  }
  if ! docker export "${container_id}" | tar -C "${rootfs_dir}" -xf -; then
    cleanup_rootfs
    return 1
  fi
  if ! docker rm "${container_id}" >/dev/null; then
    cleanup_rootfs
    return 1
  fi
  container_id=""
  remove_rootfs_java_artifacts "${rootfs_dir}"

  local rootfs_args=(
    rootfs
    --scanners vuln
    --severity "${severity}"
    --exit-code 1
    --format json
    --output "${output}"
    --cache-dir "${cache_dir}"
    --timeout "${timeout}"
  )
  if [[ "${ignore_unfixed}" == "true" ]]; then
    rootfs_args+=(--ignore-unfixed)
  fi
  if [[ "${skip_db_update}" == "true" ]]; then
    rootfs_args+=(--skip-db-update)
  fi
  if [[ "${skip_java_db_update}" == "true" ]]; then
    rootfs_args+=(--skip-java-db-update)
  fi
  local repository
  for repository in "${db_repositories[@]}"; do
    rootfs_args+=(--db-repository "${repository}")
  done
  for repository in "${java_db_repositories[@]}"; do
    rootfs_args+=(--java-db-repository "${repository}")
  done
  if [[ -n "${pkg_types}" ]]; then
    rootfs_args+=(--pkg-types "${pkg_types}")
  fi
  if [[ "${skip_java_artifacts}" == "true" ]]; then
    local java_artifact_skip_file
    for java_artifact_skip_file in "${java_artifact_skip_files[@]}"; do
      rootfs_args+=(--skip-files "${java_artifact_skip_file}")
    done
  fi

  rootfs_status=0
  "${trivy_bin}" "${rootfs_args[@]}" "${rootfs_dir}" || rootfs_status=$?
  if is_valid_trivy_json "${output}"; then
    annotate_report_metadata "${output}" "${image}" "${name}" "rootfs-fallback"
  fi
  cleanup_rootfs
  return "${rootfs_status}"
}

for image_ref in "${image_refs[@]}"; do
  image_status=0
  scan_image "${image_ref}" || image_status=$?
  if [[ "${interrupted}" == "true" ]]; then
    [[ "${image_status}" -ne 0 ]] || image_status=130
    exit "${image_status}"
  fi
  if [[ "${image_status}" -ne 0 ]]; then
    scan_status=1
  fi
done

if [[ "${scan_status}" -ne 0 ]]; then
  fail "container image vulnerability scan failed; reports written to ${report_dir}"
fi

node scripts/container-report-check.mjs "${report_dir}"

info "passed; reports written to ${report_dir}"
