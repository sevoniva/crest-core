#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-github-actions-policy-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_GITHUB_ACTIONS_POLICY_DIR:-.local/github-actions-policy-test-$$}"
workflow_dir="${test_root}/workflows"
report_file="${test_root}/github-actions-policy.txt"
sha_a="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
sha_b="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
digest_a="cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

rm -rf "${test_root}"
mkdir -p "${workflow_dir}"

cat > "${workflow_dir}/good.yml" <<EOF
name: good
on:
  workflow_dispatch:
jobs:
  good:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@${sha_a} # v6.0.2
      - uses: ./local-action
      - uses: docker://registry.example.internal/action@sha256:${digest_a}
      - uses: owner/action/path@${sha_b}
EOF

CREST_GITHUB_ACTIONS_POLICY_WORKFLOW_DIR="${workflow_dir}" \
CREST_GITHUB_ACTIONS_POLICY_REPORT="${report_file}" \
  bash scripts/github-actions-policy-check.sh >/dev/null

grep -q '^status=passed$' "${report_file}" || fail "SHA-pinned references should pass"
grep -q '^action_references=4$' "${report_file}" || fail "report should count action references"
grep -q '^github_action_refs_sha_pinned=true$' "${report_file}" || fail "report should record pinned action refs"

cat > "${workflow_dir}/bad-tag.yml" <<'EOF'
name: bad-tag
on:
  workflow_dispatch:
jobs:
  bad:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout@v6.0.2
EOF
if CREST_GITHUB_ACTIONS_POLICY_WORKFLOW_DIR="${workflow_dir}" \
  CREST_GITHUB_ACTIONS_POLICY_REPORT="${report_file}" \
  bash scripts/github-actions-policy-check.sh >/tmp/crest-actions-policy-tag.out 2>&1; then
  fail "tag-pinned action unexpectedly passed"
fi
grep -q "third-party action ref must be a 40-character commit SHA" /tmp/crest-actions-policy-tag.out \
  || fail "tag failure message was not reported"
grep -q '^status=failed$' "${report_file}" || fail "tag failure should write failed report"

rm "${workflow_dir}/bad-tag.yml"
cat > "${workflow_dir}/bad-docker.yml" <<'EOF'
name: bad-docker
on:
  workflow_dispatch:
jobs:
  bad:
    runs-on: ubuntu-24.04
    steps:
      - uses: docker://alpine:3.20
EOF
if CREST_GITHUB_ACTIONS_POLICY_WORKFLOW_DIR="${workflow_dir}" \
  CREST_GITHUB_ACTIONS_POLICY_REPORT="${report_file}" \
  bash scripts/github-actions-policy-check.sh >/tmp/crest-actions-policy-docker.out 2>&1; then
  fail "tagged docker action unexpectedly passed"
fi
grep -q "docker action must be pinned with @sha256" /tmp/crest-actions-policy-docker.out \
  || fail "docker action failure message was not reported"

rm "${workflow_dir}/bad-docker.yml"
cat > "${workflow_dir}/bad-missing-ref.yml" <<'EOF'
name: bad-missing-ref
on:
  workflow_dispatch:
jobs:
  bad:
    runs-on: ubuntu-24.04
    steps:
      - uses: actions/checkout
EOF
if CREST_GITHUB_ACTIONS_POLICY_WORKFLOW_DIR="${workflow_dir}" \
  CREST_GITHUB_ACTIONS_POLICY_REPORT="${report_file}" \
  bash scripts/github-actions-policy-check.sh >/tmp/crest-actions-policy-missing.out 2>&1; then
  fail "missing action ref unexpectedly passed"
fi
grep -q "third-party action must include an immutable commit SHA" /tmp/crest-actions-policy-missing.out \
  || fail "missing ref failure message was not reported"

echo "test-github-actions-policy-check: passed"
