#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "quality-check: $*" >&2
  exit 1
}

info() {
  echo "quality-check: $*"
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing required command: $1"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

# shellcheck source=scripts/lib/ensure-java17.sh
source "${repo_root}/scripts/lib/ensure-java17.sh"
ensure_java17

require_cmd node
require_cmd pnpm
require_cmd mvn
require_cmd ruby
require_cmd kubectl

info "validating generated OB Oracle init SQL and Kubernetes manifests"
node scripts/generate-ob-oracle-init-schema.mjs --check
node scripts/verify-kubernetes-production.mjs deploy/kubernetes
kubectl create --dry-run=client --validate=false -f deploy/kubernetes -o name >/dev/null
bash scripts/test-production-overlay-render.sh
bash scripts/test-production-overlay-evidence.sh
bash scripts/test-production-runtime-check.sh
bash scripts/test-docker-environment-check.sh
bash scripts/test-docker-cleanup-plan.sh
bash scripts/test-docker-build-check.sh
bash scripts/test-docker-context-policy-check.sh
bash scripts/test-docker-production-check.sh
bash scripts/test-kind-smoke-test.sh
bash scripts/test-production-evidence-bundle.sh
bash scripts/test-production-external-evidence-check.sh
bash scripts/test-prepare-production-external-evidence.sh
bash scripts/test-production-readiness-action-plan.sh
bash scripts/test-enterprise-readiness-summary.sh
bash scripts/test-production-go-no-go-summary-check.sh
bash scripts/test-redis-cluster-namespace-check.sh
bash scripts/test-clean-source-release.sh
bash scripts/test-history-secret-audit.sh
bash scripts/test-license-policy-check.sh
bash scripts/test-security-report-check.sh
bash scripts/test-security-scan-report-dir.sh
bash scripts/test-container-report-check.sh
bash scripts/test-container-image-scan-report-dir.sh
bash scripts/test-production-image-scan-coverage.sh
bash scripts/test-container-base-image-policy-check.sh
bash scripts/test-github-actions-policy-check.sh
bash scripts/test-ci-toolchain-policy-check.sh
bash scripts/test-ci-tool-version-validation.sh
bash scripts/test-install-trivy-download-options.sh

info "running repository code style checks"
bash scripts/code-style-check.sh

info "checking frontend dependencies, types, lint and production build"
(
  cd core/core-frontend
  pnpm install --frozen-lockfile
  pnpm run ts:check
  pnpm run lint:check
  pnpm run build:base
  pnpm run build:lite:check
)

info "running backend tests with OpenJDK 17"
mvn -s .mvn/settings.xml \
  -pl :core-backend \
  -am \
  clean test \
  -Pstandalone \
  -Dcrest.copy.frontend.skip=false

info "checking frontend dist and backend static resource parity"
node scripts/verify-static-assets.mjs

info "checking release metadata and branch policy"
bash scripts/release-guard.sh

info "passed"
