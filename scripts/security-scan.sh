#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "security-scan: $*" >&2
  exit 1
}

info() {
  echo "security-scan: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

# shellcheck source=scripts/lib/ensure-java17.sh
source "${repo_root}/scripts/lib/ensure-java17.sh"
ensure_java17

default_tool_bin() {
  local name="$1"
  local local_path="${repo_root}/.local/tools/${name}"
  if command -v "${name}" >/dev/null 2>&1; then
    printf '%s' "${name}"
  elif [[ -x "${local_path}" ]]; then
    printf '%s' "${local_path}"
  else
    printf '%s' "${name}"
  fi
}

report_dir="${CREST_SECURITY_REPORT_DIR:-reports/security}"
semgrep_bin="${CREST_SEMGREP_BIN:-$(default_tool_bin semgrep)}"
osv_bin="${CREST_OSV_SCANNER:-$(default_tool_bin osv-scanner)}"
gitleaks_bin="${CREST_GITLEAKS_BIN:-$(default_tool_bin gitleaks)}"

prepare_report_dir() {
  local requested_dir="$1"
  local requested_abs parent_dir base_name resolved_parent resolved_dir

  [[ -n "${requested_dir}" ]] || fail "CREST_SECURITY_REPORT_DIR must not be empty"
  case "${requested_dir}" in
    /|.|..|../*|*/../*)
      fail "refusing unsafe security report directory: ${requested_dir}"
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
      fail "CREST_SECURITY_REPORT_DIR must stay inside the repository: ${requested_dir}"
      ;;
  esac

  parent_dir="$(dirname "${requested_abs}")"
  base_name="$(basename "${requested_abs}")"
  [[ -n "${base_name}" && "${base_name}" != "." && "${base_name}" != ".." ]] \
    || fail "refusing unsafe security report directory: ${requested_dir}"

  mkdir -p "${parent_dir}"
  resolved_parent="$(cd "${parent_dir}" && pwd -P)"
  resolved_dir="${resolved_parent}/${base_name}"

  case "${resolved_dir}" in
    "${repo_root}/"*)
      ;;
    *)
      fail "CREST_SECURITY_REPORT_DIR must stay inside the repository: ${requested_dir}"
      ;;
  esac
  [[ "${resolved_dir}" != "${repo_root}" ]] || fail "refusing to use repository root as security report directory"

  rm -rf "${resolved_dir}"
  mkdir -p "${resolved_dir}"
  printf '%s' "${resolved_dir}"
}

require_cmd "${semgrep_bin}"
require_cmd "${gitleaks_bin}"
require_cmd pnpm
require_cmd mvn

report_dir="$(prepare_report_dir "${report_dir}")"

info "running Semgrep SAST"
"${semgrep_bin}" \
  --config p/default \
  --config p/owasp-top-ten \
  --error \
  --json \
  --output "${report_dir}/semgrep.json" \
  --no-git-ignore \
  --exclude .git \
  --exclude .cache \
  --exclude .local \
  --exclude reports \
  --exclude core/core-backend/src/main/resources/static \
  --exclude core/core-frontend/dist \
  --exclude '**/target/**' \
  --exclude '**/node_modules/**' \
  .

info "running Gitleaks secret scan"
"${gitleaks_bin}" detect \
  --config gitleaks.toml \
  --source . \
  --no-git \
  --redact \
  --exit-code 1 \
  --report-format json \
  --report-path "${report_dir}/gitleaks.json" \
  --log-level warn

info "writing Maven dependency inventory"
mvn -s .mvn/settings.xml \
  -DskipTests \
  -DappendOutput=true \
  -DoutputFile="${report_dir}/maven-dependency-tree.txt" \
  dependency:tree

info "writing Maven CycloneDX SBOM"
mvn -s .mvn/settings.xml \
  -DskipTests \
  org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
  -DoutputFormat=json \
  -DoutputName=crest-bom \
  -DoutputDirectory="${report_dir}"

info "running pnpm production dependency audit"
(
  cd core/core-frontend
  pnpm install --frozen-lockfile --ignore-scripts
  pnpm audit --prod --audit-level=moderate --json > "${report_dir}/pnpm-audit.json"
  pnpm licenses list --prod --json > "${report_dir}/frontend-licenses.json"
)

if ! command -v "${osv_bin}" >/dev/null 2>&1; then
  fail "missing required command: ${osv_bin}. Install OSV Scanner, or set CREST_OSV_SCANNER to its absolute path."
fi

info "running OSV Scanner frontend lockfile SCA"
"${osv_bin}" scan \
  --config osv-scanner.toml \
  --lockfile core/core-frontend/pnpm-lock.yaml \
  --lockfile core/core-frontend/flushbonading/package-lock.json \
  --format json \
  --output "${report_dir}/osv-frontend.json"

info "running OSV Scanner Maven SBOM SCA"
"${osv_bin}" scan \
  --config osv-scanner.toml \
  --sbom "${report_dir}/crest-bom.json" \
  --format json \
  --output "${report_dir}/osv-maven-sbom.json"

info "checking security reports for zero unresolved findings"
CREST_LICENSE_POLICY_REPORT="${report_dir}/license-policy.txt" \
CREST_MAVEN_BOM_FILE="${report_dir}/crest-bom.json" \
CREST_FRONTEND_LICENSES_FILE="${report_dir}/frontend-licenses.json" \
  node scripts/license-policy-check.mjs
node scripts/security-report-check.mjs "${report_dir}"

info "passed; reports written to ${report_dir}"
