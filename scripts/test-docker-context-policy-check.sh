#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-docker-context-policy-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_DOCKER_CONTEXT_POLICY_DIR:-.local/docker-context-policy-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}"

cp .dockerignore Dockerfile.frontend Dockerfile.backend "${test_root}/"

node scripts/docker-context-policy-check.mjs "${test_root}" >/dev/null

printf '\n!reports/**\n' >> "${test_root}/.dockerignore"
if node scripts/docker-context-policy-check.mjs "${test_root}" >/tmp/crest-docker-context-reopen.out 2>&1; then
  fail "docker context policy with reopened reports unexpectedly passed"
fi
grep -q 'must not reopen broad or sensitive paths: !reports/\*\*' /tmp/crest-docker-context-reopen.out \
  || fail "reopened reports failure message was not reported"

cp .dockerignore "${test_root}/.dockerignore"
sed 's#COPY --chown=10001:10001 core/core-frontend/dist/ /usr/share/nginx/html/#COPY . /usr/share/nginx/html/#' \
  Dockerfile.frontend > "${test_root}/Dockerfile.frontend"
if node scripts/docker-context-policy-check.mjs "${test_root}" >/tmp/crest-docker-context-copy-dot.out 2>&1; then
  fail "docker context policy with COPY . unexpectedly passed"
fi
grep -q 'must not COPY the entire repository build context' /tmp/crest-docker-context-copy-dot.out \
  || fail "COPY . failure message was not reported"

cp Dockerfile.frontend "${test_root}/Dockerfile.frontend"
sed '/^\.local$/d' .dockerignore > "${test_root}/.dockerignore"
if node scripts/docker-context-policy-check.mjs "${test_root}" >/tmp/crest-docker-context-missing-ignore.out 2>&1; then
  fail "docker context policy missing .local ignore unexpectedly passed"
fi
grep -q '\.dockerignore must include \.local' /tmp/crest-docker-context-missing-ignore.out \
  || fail "missing .local ignore failure message was not reported"

rm -rf "${test_root}"
echo "test-docker-context-policy-check: passed"
