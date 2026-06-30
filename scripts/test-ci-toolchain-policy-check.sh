#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-ci-toolchain-policy-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_CI_TOOLCHAIN_POLICY_DIR:-.local/ci-toolchain-policy-test-$$}"
workflow_dir="${test_root}/workflows"
report_file="${test_root}/ci-toolchain-policy.txt"

rm -rf "${test_root}"
mkdir -p "${workflow_dir}"

cat > "${workflow_dir}/good.yml" <<'EOF'
name: good
on:
  workflow_dispatch:
jobs:
  good:
    runs-on: ubuntu-24.04
    steps:
      - run: bash scripts/install-semgrep.sh
      - run: bash scripts/install-osv-scanner.sh
      - run: bash scripts/install-gitleaks.sh
      - run: bash scripts/install-actionlint.sh
      - run: bash scripts/install-trivy.sh
EOF

CREST_CI_TOOLCHAIN_POLICY_WORKFLOW_DIR="${workflow_dir}" \
CREST_CI_TOOLCHAIN_POLICY_REPORT="${report_file}" \
  bash scripts/ci-toolchain-policy-check.sh >/dev/null

grep -q '^status=passed$' "${report_file}" || fail "centralized CI tool installs should pass"
grep -q '^semgrep_version=1.155.0$' "${report_file}" || fail "report should record Semgrep version"
grep -q '^centralized_ci_tool_installs=true$' "${report_file}" || fail "report should record centralized installs"

cat > "${workflow_dir}/bad-go.yml" <<'EOF'
name: bad-go
on:
  workflow_dispatch:
jobs:
  bad:
    runs-on: ubuntu-24.04
    steps:
      - run: go install github.com/google/osv-scanner/cmd/osv-scanner@v1.9.2
EOF
if CREST_CI_TOOLCHAIN_POLICY_WORKFLOW_DIR="${workflow_dir}" \
  CREST_CI_TOOLCHAIN_POLICY_REPORT="${report_file}" \
  bash scripts/ci-toolchain-policy-check.sh >/tmp/crest-ci-toolchain-go.out 2>&1; then
  fail "inline go install unexpectedly passed"
fi
grep -q "not inline go install" /tmp/crest-ci-toolchain-go.out \
  || fail "inline go install failure message was not reported"
grep -q '^status=failed$' "${report_file}" || fail "inline go install should write failed report"

rm "${workflow_dir}/bad-go.yml"
cat > "${workflow_dir}/bad-pipx.yml" <<'EOF'
name: bad-pipx
on:
  workflow_dispatch:
jobs:
  bad:
    runs-on: ubuntu-24.04
    steps:
      - run: python3 -m pipx install semgrep==1.155.0
EOF
if CREST_CI_TOOLCHAIN_POLICY_WORKFLOW_DIR="${workflow_dir}" \
  CREST_CI_TOOLCHAIN_POLICY_REPORT="${report_file}" \
  bash scripts/ci-toolchain-policy-check.sh >/tmp/crest-ci-toolchain-pipx.out 2>&1; then
  fail "inline pipx install unexpectedly passed"
fi
grep -q "not inline pipx install" /tmp/crest-ci-toolchain-pipx.out \
  || fail "inline pipx install failure message was not reported"

echo "test-ci-toolchain-policy-check: passed"
