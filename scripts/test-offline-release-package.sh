#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-offline-release-package: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_OFFLINE_PACKAGE_DIR:-.local/offline-package-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/images" "${test_root}/out" "${test_root}/work" "${test_root}/extract"
trap 'rm -rf "${test_root}"' EXIT

printf 'backend-image-archive\n' > "${test_root}/images/backend.tar"
printf 'frontend-image-archive\n' > "${test_root}/images/frontend.tar"

CREST_OFFLINE_RELEASE_VERSION=v1.0.0 \
  CREST_OFFLINE_PACKAGE_ARCH=amd64 \
  CREST_OFFLINE_BACKEND_IMAGE_TAR="${test_root}/images/backend.tar" \
  CREST_OFFLINE_FRONTEND_IMAGE_TAR="${test_root}/images/frontend.tar" \
  CREST_OFFLINE_OUTPUT_DIR="${test_root}/out" \
  CREST_OFFLINE_WORK_DIR="${test_root}/work" \
  bash scripts/create-offline-release-package.sh >/dev/null

archive="${test_root}/out/crest-core-v1.0.0-linux-amd64-offline.tar.gz"
checksum="${archive}.sha256"
[[ -f "${archive}" ]] || fail "offline package archive was not created"
[[ -f "${checksum}" ]] || fail "offline package archive checksum was not created"

tar -xzf "${archive}" -C "${test_root}/extract"
package_dir="${test_root}/extract/crest-core-v1.0.0-linux-amd64-offline"

for required_path in \
  README.md \
  README.project.md \
  LICENSE \
  VERSION \
  CHANGELOG.md \
  MANIFEST.txt \
  SHA256SUMS \
  docs/release/v1.0.0.md \
  deploy/docker/compose.yaml \
  deploy/docker/production.env.example \
  deploy/kubernetes/00-crest-env-configmap.yaml \
  deploy/nginx/default.conf \
  installer/init-sql/ob-oracle/crest-core-schema.sql \
  scripts/verify-docker-production.mjs \
  scripts/verify-kubernetes-production.mjs \
  images/crest-core-service-v1.0.0-linux-amd64.tar \
  images/crest-core-web-v1.0.0-linux-amd64.tar; do
  [[ -e "${package_dir}/${required_path}" ]] || fail "missing package content: ${required_path}"
done

grep -q '^package=crest-core-v1.0.0-linux-amd64-offline$' "${package_dir}/MANIFEST.txt" \
  || fail "manifest must record the package name"
grep -q 'external OceanBase Oracle and external Redis Cluster' "${package_dir}/README.md" \
  || fail "offline package README must state external dependencies"
grep -q -- '--scale crest-core-service=2' "${package_dir}/README.md" \
  || fail "offline package README must document the two-replica Docker command"

if tar -tzf "${archive}" | grep -Eq '(^|/)(\.git|\.local|reports|target|node_modules)(/|$)'; then
  fail "offline package must not include local, generated, or VCS directories"
fi

(
  cd "${package_dir}"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum -c SHA256SUMS >/dev/null
  else
    shasum -a 256 -c SHA256SUMS >/dev/null
  fi
)

if CREST_OFFLINE_RELEASE_VERSION=v1.0.0 \
  CREST_OFFLINE_PACKAGE_ARCH=x64 \
  CREST_OFFLINE_BACKEND_IMAGE_TAR="${test_root}/images/backend.tar" \
  CREST_OFFLINE_FRONTEND_IMAGE_TAR="${test_root}/images/frontend.tar" \
  CREST_OFFLINE_OUTPUT_DIR="${test_root}/bad-out" \
  CREST_OFFLINE_WORK_DIR="${test_root}/bad-work" \
  bash scripts/create-offline-release-package.sh >"${test_root}/bad-arch.log" 2>&1; then
  fail "invalid package architecture unexpectedly passed"
fi
grep -q 'CREST_OFFLINE_PACKAGE_ARCH must be amd64 or arm64' "${test_root}/bad-arch.log" \
  || fail "invalid architecture failure message was not reported"

echo "test-offline-release-package: passed"
