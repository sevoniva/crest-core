#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "code-style-check: $*" >&2
  exit 1
}

info() {
  echo "code-style-check: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

require_cmd git
require_cmd node
require_cmd rg

report_dir="${CREST_STYLE_REPORT_DIR:-reports/style}"
report_file="${report_dir}/code-style.txt"
rm -rf "${report_dir}"
mkdir -p "${report_dir}"
: > "${report_file}"

violations=0

record_violation() {
  local title="$1"
  local body="$2"
  violations=$((violations + 1))
  {
    echo "## ${title}"
    echo "${body}"
    echo
  } >> "${report_file}"
}

check_no_matches() {
  local title="$1"
  shift
  local output
  if output="$(rg -n "$@" 2>/dev/null)"; then
    record_violation "${title}" "${output}"
  fi
}

check_required_file_match() {
  local title="$1"
  local file="$2"
  local pattern="$3"
  if ! grep -Eq "${pattern}" "${file}"; then
    record_violation "${title}" "${file} does not contain required pattern: ${pattern}"
  fi
}

info "checking patch whitespace"
if ! git diff --check >> "${report_file}" 2>&1; then
  violations=$((violations + 1))
fi

info "checking Node.js utility script syntax"
while IFS= read -r script; do
  if ! node --check "${script}" >> "${report_file}" 2>&1; then
    violations=$((violations + 1))
  fi
done < <(find scripts -maxdepth 1 -name '*.mjs' -type f | sort)

info "checking Bash utility script syntax"
while IFS= read -r script; do
  if ! bash -n "${script}" >> "${report_file}" 2>&1; then
    violations=$((violations + 1))
  fi
done < <(find scripts -name '*.sh' -type f | sort)

info "checking admin bootstrap password seed"
if ! node scripts/verify-admin-bootstrap-security.mjs >> "${report_file}" 2>&1; then
  violations=$((violations + 1))
fi

info "checking Kubernetes Secret evidence sanitization"
if ! bash scripts/test-sanitize-kubernetes-secrets.sh >> "${report_file}" 2>&1; then
  violations=$((violations + 1))
fi

info "checking GitHub Actions workflows"
if ! bash scripts/workflow-lint.sh >> "${report_file}" 2>&1; then
  violations=$((violations + 1))
fi

info "checking Java production logging and token parsing rules"
runtime_sources="$(find core/core-backend/src/main/java/io/crest/runtime -name '*.java' -type f 2>/dev/null || true)"
if [[ -n "${runtime_sources}" ]]; then
  record_violation \
    "Runtime role infrastructure must live only in sdk/common" \
    "${runtime_sources}"
fi

check_no_matches \
  "Java sources must not write directly to stdout/stderr" \
  'System\.(out|err)\.' \
  core sdk \
  --glob '*.java' \
  --glob '!**/target/**'

check_no_matches \
  "Java sources must not use no-arg printStackTrace" \
  '\.printStackTrace\(\s*\)' \
  core sdk \
  --glob '*.java' \
  --glob '!**/target/**'

check_no_matches \
  "Java sources must not suppress all warnings" \
  '@SuppressWarnings\("all"\)' \
  core sdk \
  --glob '*.java' \
  --glob '!**/target/**'

jwt_hits="$(rg -n 'JWT\.decode\(' core sdk --glob '*.java' --glob '!**/target/**' 2>/dev/null || true)"
jwt_bad="$(printf "%s\n" "${jwt_hits}" | sed '/^$/d' | grep -v '^sdk/common/src/main/java/io/crest/utils/SignedTokenUtils.java:' || true)"
jwt_count="$(printf "%s\n" "${jwt_hits}" | sed '/^$/d' | wc -l | tr -d ' ')"
if [[ -n "${jwt_bad}" || "${jwt_count}" != "1" ]]; then
  record_violation \
    "JWT.decode must stay isolated in SignedTokenUtils.decodeUnverifiedForSecretLookup" \
    "${jwt_hits:-no JWT.decode calls found}"
fi

info "checking Spring Boot production health probes"
check_required_file_match \
  "Spring Boot must use graceful shutdown" \
  core/core-backend/src/main/resources/application.yml \
  'shutdown:[[:space:]]*graceful'
check_required_file_match \
  "Spring Boot graceful shutdown timeout must be externally configurable" \
  core/core-backend/src/main/resources/application.yml \
  'timeout-per-shutdown-phase:[[:space:]]*\$\{CREST_SHUTDOWN_TIMEOUT:45s\}'
check_required_file_match \
  "Actuator health endpoint must be exposed for Kubernetes probes" \
  core/core-backend/src/main/resources/application.yml \
  'include:[[:space:]]*health,prometheus'
check_required_file_match \
  "Readiness health group must include database and Redis" \
  core/core-backend/src/main/resources/application.yml \
  'include:[[:space:]]*readinessState,db,redis'
check_required_file_match \
  "Redis health must stay enabled by default in production" \
  core/core-backend/src/main/resources/application.yml \
  'CREST_HEALTH_REDIS_ENABLED:true'
check_no_matches \
  "Health checks must use Spring Boot Actuator, not a custom RestController" \
  '@RequestMapping\("/actuator/health"\)' \
  core/core-backend/src/main/java \
  --glob '*.java' \
  --glob '!**/target/**'

info "checking frontend application debug statements"
check_no_matches \
  "Frontend application source must not contain debugger or debug console statements" \
  '\b(console\.(log|debug|info)|debugger)\b' \
  core/core-frontend/src \
  --glob '*.ts' \
  --glob '*.tsx' \
  --glob '*.js' \
  --glob '*.jsx' \
  --glob '*.vue'

info "checking unresolved production TODO markers"
check_no_matches \
  "Production source must not contain TODO/FIXME markers" \
  '\b(TODO|FIXME)\b' \
  core/core-backend/src/main/java \
  sdk \
  core/core-frontend/src \
  --glob '*.java' \
  --glob '*.ts' \
  --glob '*.tsx' \
  --glob '*.js' \
  --glob '*.jsx' \
  --glob '*.vue' \
  --glob '!**/target/**'

if (( violations > 0 )); then
  cat "${report_file}" >&2
  fail "${violations} style check(s) failed; see ${report_file}"
fi

echo "code-style-check: passed" | tee -a "${report_file}"
