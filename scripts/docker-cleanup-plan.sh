#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "docker-cleanup-plan: $*" >&2
  exit 1
}

info() {
  echo "docker-cleanup-plan: $*"
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

docker info >/dev/null 2>&1 || fail "Docker daemon is not available"

min_free_gib="${CREST_DOCKER_MIN_FREE_GB:-12}"
case "${min_free_gib}" in
  ''|*[!0-9]*)
    fail "CREST_DOCKER_MIN_FREE_GB must be a non-negative integer"
    ;;
esac

disk_path="${CREST_DOCKER_DISK_PATH:-.}"
report_file="${CREST_DOCKER_CLEANUP_REPORT:-reports/readiness/docker-cleanup-plan.txt}"
mkdir -p "$(dirname "${report_file}")"

available_kib="$(df -Pk "${disk_path}" | awk 'NR == 2 {print $4}')"
[[ -n "${available_kib}" ]] || fail "could not determine free disk space for ${disk_path}"
case "${available_kib}" in
  ''|*[!0-9]*)
    fail "unexpected free disk value for ${disk_path}: ${available_kib}"
    ;;
esac

required_kib=$(( min_free_gib * 1024 * 1024 ))
available_gib=$(( available_kib / 1024 / 1024 ))

if (( available_kib < required_kib )); then
  status="cleanup-needed"
  shortfall_gib=$(( (required_kib - available_kib + 1024 * 1024 - 1) / 1024 / 1024 ))
  owner_approval_required="true"
else
  status="enough-free-space"
  shortfall_gib=0
  owner_approval_required="false"
fi

{
  echo "Crest Docker cleanup plan"
  echo "generated_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "status=${status}"
  echo "disk_path=${disk_path}"
  echo "available_gib=${available_gib}"
  echo "required_gib=${min_free_gib}"
  echo "shortfall_gib=${shortfall_gib}"
  echo "owner_approval_required=${owner_approval_required}"
  echo "destructive_commands_executed=false"
  echo "recommended_first_cleanup_command=docker builder prune -af"
  echo
  echo "Docker disk usage:"
  docker system df || true
  echo
  echo "Docker builder cache usage:"
  docker builder du || true
  echo
  echo "Recommended non-destructive review:"
  echo "  docker system df -v"
  echo "  docker builder du"
  echo "  docker image ls"
  echo "  docker volume ls"
  echo
  echo "Suggested cleanup order after owner approval:"
  echo "  docker builder prune -af"
  echo "  docker image prune -af"
  echo "  docker container prune -f"
  echo
  echo "Approval checklist before cleanup:"
  echo "  1. Confirm no production or shared local build depends on the builder cache."
  echo "  2. Confirm unused images are not needed by another local test environment."
  echo "  3. Keep named volumes unless the data owner explicitly approves deletion."
  echo
  echo "High-risk cleanup requiring explicit data-owner approval:"
  echo "  docker volume prune -f"
  echo
  echo "Notes:"
  echo "  This report is read-only; this script does not delete images, containers, build cache, or volumes."
  echo "  Volumes may contain database or application data from other systems and must not be pruned blindly."
} > "${report_file}"

info "wrote read-only cleanup plan to ${report_file}"
if [[ "${status}" == "cleanup-needed" ]]; then
  info "Docker cleanup is recommended before production image builds: ${available_gib}GiB free, ${min_free_gib}GiB required"
else
  info "Docker free-space target is already satisfied: ${available_gib}GiB free"
fi
