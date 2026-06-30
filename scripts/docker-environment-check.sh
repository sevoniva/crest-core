#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "docker-environment-check: $*" >&2
  exit 1
}

info() {
  echo "docker-environment-check: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

require_cmd docker
require_cmd awk
require_cmd date
require_cmd df

min_free_gib="${CREST_DOCKER_MIN_FREE_GB:-12}"
purpose="${CREST_DOCKER_ENVIRONMENT_PURPOSE:-Docker build}"
case "${min_free_gib}" in
  ''|*[!0-9]*)
    fail "CREST_DOCKER_MIN_FREE_GB must be a non-negative integer"
    ;;
esac

disk_path="${CREST_DOCKER_DISK_PATH:-.}"
skip_disk_check="${CREST_DOCKER_PRECHECK_SKIP_DISK:-false}"
environment_report="${CREST_DOCKER_ENVIRONMENT_REPORT:-}"

available_kib=""
available_gib="unknown"
required_kib=$(( min_free_gib * 1024 * 1024 ))
shortfall_gib="unknown"

write_environment_report() {
  local status="$1"
  local reason="$2"
  [[ -n "${environment_report}" ]] || return 0

  mkdir -p "$(dirname "${environment_report}")"
  if [[ -z "${available_kib}" ]]; then
    available_kib="$(df -Pk "${disk_path}" | awk 'NR == 2 {print $4}' || true)"
    if [[ "${available_kib}" =~ ^[0-9]+$ ]]; then
      available_gib=$(( available_kib / 1024 / 1024 ))
      if (( available_kib < required_kib )); then
        shortfall_gib=$(( (required_kib - available_kib + 1024 * 1024 - 1) / 1024 / 1024 ))
      else
        shortfall_gib=0
      fi
    fi
  fi

  {
    echo "Crest Docker environment report"
    echo "generated_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
    echo "status=${status}"
    echo "reason=${reason}"
    echo "purpose=${purpose}"
    echo "disk_path=${disk_path}"
    echo "available_gib=${available_gib}"
    echo "required_gib=${min_free_gib}"
    echo "shortfall_gib=${shortfall_gib}"
    echo "skip_disk_check=${skip_disk_check}"
    echo "docker_context=$(docker context show 2>/dev/null || true)"
    echo
    echo "Docker daemon probe:"
    docker info >/dev/null 2>&1 && echo "docker_info=available" || echo "docker_info=unavailable"
    echo
    echo "Operator actions:"
    echo "  1. Start or restart Docker Desktop / Docker daemon when docker_info=unavailable."
    echo "  2. Re-run scripts/docker-environment-check.sh before image build or kind smoke."
    echo "  3. If status=insufficient-disk, review the read-only Docker cleanup plan before pruning."
  } > "${environment_report}"
}

if ! docker info >/dev/null 2>&1; then
  write_environment_report "daemon-unavailable" "Docker daemon is not available"
  fail "Docker daemon is not available"
fi

if [[ "${skip_disk_check}" == "true" ]]; then
  write_environment_report "passed" "disk check skipped"
  info "disk free-space check skipped by CREST_DOCKER_PRECHECK_SKIP_DISK=true"
  info "passed"
  exit 0
fi

available_kib="$(df -Pk "${disk_path}" | awk 'NR == 2 {print $4}')"
[[ -n "${available_kib}" ]] || fail "could not determine free disk space for ${disk_path}"
case "${available_kib}" in
  ''|*[!0-9]*)
    fail "unexpected free disk value for ${disk_path}: ${available_kib}"
    ;;
esac

available_gib=$(( available_kib / 1024 / 1024 ))
if (( available_kib < required_kib )); then
  shortfall_gib=$(( (required_kib - available_kib + 1024 * 1024 - 1) / 1024 / 1024 ))
else
  shortfall_gib=0
fi

if (( available_kib < required_kib )); then
  write_environment_report "insufficient-disk" "${purpose} requires at least ${min_free_gib}GiB free"
  cleanup_report="${CREST_DOCKER_CLEANUP_REPORT:-reports/readiness/docker-cleanup-plan.txt}"
  {
    echo "Docker disk usage:"
    docker system df || true
    if [[ "${CREST_DOCKER_CLEANUP_PLAN_ON_FAILURE:-true}" == "true" && -x scripts/docker-cleanup-plan.sh ]]; then
      echo
      echo "Docker cleanup plan:"
      CREST_DOCKER_CLEANUP_REPORT="${cleanup_report}" \
        CREST_DOCKER_MIN_FREE_GB="${min_free_gib}" \
        CREST_DOCKER_DISK_PATH="${disk_path}" \
        bash scripts/docker-cleanup-plan.sh || true
      echo "Review read-only cleanup plan at ${cleanup_report}"
    fi
  } >&2
  fail "${purpose} requires at least ${min_free_gib}GiB free on ${disk_path}: only ${available_gib}GiB available. Free disk space or prune Docker build cache/unused data after approval."
fi

write_environment_report "passed" "Docker daemon and disk preflight passed"
info "Docker daemon is available; ${available_gib}GiB free on ${disk_path}"
info "passed"
