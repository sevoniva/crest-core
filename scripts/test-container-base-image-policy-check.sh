#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-container-base-image-policy-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_CONTAINER_BASE_IMAGE_POLICY_DIR:-.local/container-base-image-policy-test-$$}"
report_file="${test_root}/policy.txt"
digest_a="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
digest_b="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
digest_c="cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"

rm -rf "${test_root}"
mkdir -p "${test_root}"

CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${report_file}" \
  bash scripts/container-base-image-policy-check.sh >/dev/null

grep -q '^status=passed$' "${report_file}" || fail "default policy check should pass"
grep -q '^require_base_image_digests=false$' "${report_file}" || fail "default policy should not require digests"
grep -q '^jdk_image_digest_pinned=true$' "${report_file}" || fail "default JDK image should be recorded as digest-pinned"
grep -q '^runtime_image_digest_pinned=true$' "${report_file}" || fail "default runtime image should be recorded as digest-pinned"
grep -q '^nginx_image_digest_pinned=true$' "${report_file}" || fail "default Nginx image should be recorded as digest-pinned"

if CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${report_file}" \
  CREST_DOCKER_NGINX_IMAGE="nginx:latest" \
  bash scripts/container-base-image-policy-check.sh >/tmp/crest-base-image-latest.out 2>&1; then
  fail "latest base image unexpectedly passed"
fi
grep -q "must not use the mutable latest tag" /tmp/crest-base-image-latest.out \
  || fail "latest tag failure message was not reported"
grep -q '^status=failed$' "${report_file}" || fail "latest tag failure should write failed report"

if CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${report_file}" \
  CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS=true \
  CREST_DOCKER_JDK_IMAGE="eclipse-temurin:17-jdk-jammy" \
  CREST_DOCKER_RUNTIME_IMAGE="ubuntu:24.04" \
  CREST_DOCKER_NGINX_IMAGE="nginx:1.29-alpine" \
  bash scripts/container-base-image-policy-check.sh >/tmp/crest-base-image-digest-required.out 2>&1; then
  fail "tag-only base images unexpectedly passed when digest pinning is required"
fi
grep -q "CREST_DOCKER_JDK_IMAGE image reference must be pinned with @sha256" /tmp/crest-base-image-digest-required.out \
  || fail "digest-required failure message was not reported"

if CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${report_file}" \
  CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS=true \
  CREST_DOCKER_JDK_IMAGE="registry.example.internal/eclipse-temurin:17-jdk-jammy@sha256:${digest_a}" \
  CREST_DOCKER_RUNTIME_IMAGE="registry.example.internal/ubuntu:24.04@sha256:${digest_b}" \
  CREST_DOCKER_NGINX_IMAGE="registry.example.internal/nginx:1.29-alpine@sha256:${digest_c}" \
  bash scripts/container-base-image-policy-check.sh >/tmp/crest-base-image-digest-pinned.out 2>&1; then
  :
else
  cat /tmp/crest-base-image-digest-pinned.out >&2
  fail "digest-pinned base images should pass"
fi
grep -q '^status=passed$' "${report_file}" || fail "digest-pinned policy check should write passed report"
grep -q '^require_base_image_digests=true$' "${report_file}" || fail "digest-pinned policy should record digest requirement"
grep -q '^jdk_image_digest_pinned=true$' "${report_file}" || fail "digest-pinned JDK image should be recorded"
grep -q '^runtime_image_digest_pinned=true$' "${report_file}" || fail "digest-pinned runtime image should be recorded"
grep -q '^nginx_image_digest_pinned=true$' "${report_file}" || fail "digest-pinned Nginx image should be recorded"

if CREST_CONTAINER_BASE_IMAGE_POLICY_REPORT="${report_file}" \
  CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS=maybe \
  bash scripts/container-base-image-policy-check.sh >/tmp/crest-base-image-bool.out 2>&1; then
  fail "invalid boolean unexpectedly passed"
fi
grep -q "CREST_DOCKER_REQUIRE_BASE_IMAGE_DIGESTS must be true or false" /tmp/crest-base-image-bool.out \
  || fail "invalid boolean failure message was not reported"

echo "test-container-base-image-policy-check: passed"
