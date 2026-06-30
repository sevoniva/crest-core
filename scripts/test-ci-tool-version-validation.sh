#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-ci-tool-version-validation: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

expect_failure() {
  local description="$1"
  local expected="$2"
  shift 2
  if "$@" >/tmp/crest-tool-version-validation.out 2>&1; then
    fail "${description} unexpectedly passed"
  fi
  grep -q "${expected}" /tmp/crest-tool-version-validation.out \
    || fail "${description} did not report expected message"
}

expect_failure \
  "Semgrep latest version" \
  "CREST_SEMGREP_VERSION must be pinned to X.Y.Z" \
  env CREST_SEMGREP_VERSION=latest bash scripts/install-semgrep.sh

expect_failure \
  "OSV Scanner branch version" \
  "CREST_OSV_SCANNER_VERSION must be pinned to vX.Y.Z" \
  env CREST_OSV_SCANNER_VERSION=main bash scripts/install-osv-scanner.sh

expect_failure \
  "Gitleaks unprefixed version" \
  "CREST_GITLEAKS_VERSION must be pinned to vX.Y.Z" \
  env CREST_GITLEAKS_VERSION=8.28.0 bash scripts/install-gitleaks.sh

expect_failure \
  "actionlint branch version" \
  "CREST_ACTIONLINT_VERSION must be pinned to vX.Y.Z" \
  env CREST_ACTIONLINT_VERSION=master bash scripts/install-actionlint.sh

expect_failure \
  "Trivy branch version" \
  "CREST_TRIVY_VERSION must be pinned to X.Y.Z or vX.Y.Z" \
  env CREST_TRIVY_VERSION=main bash scripts/install-trivy.sh

echo "test-ci-tool-version-validation: passed"
