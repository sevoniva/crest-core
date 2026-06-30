#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "create-offline-release-package: $*" >&2
  exit 1
}

info() {
  echo "create-offline-release-package: $*"
}

require_file() {
  local path="$1"
  [[ -f "${path}" ]] || fail "missing required file: ${path}"
}

copy_file() {
  local source="$1"
  local target="$2"
  require_file "${source}"
  mkdir -p "$(dirname "${target}")"
  cp -p "${source}" "${target}"
}

copy_dir() {
  local source="$1"
  local target="$2"
  [[ -d "${source}" ]] || fail "missing required directory: ${source}"
  mkdir -p "$(dirname "${target}")"
  rm -rf "${target}"
  cp -R "${source}" "${target}"
}

sha256_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}" | awk '{print $1}'
  else
    fail "missing required command: sha256sum or shasum"
  fi
}

write_package_readme() {
  local target="$1"
  cat > "${target}" <<EOF
# Crest Core ${release_version} Offline Delivery

This package is the offline delivery bundle for Linux ${release_arch}. It contains the Crest Core deployment manifests, OceanBase Oracle initialization SQL, Docker Compose delivery files, Kubernetes delivery files, verification scripts, and architecture-specific container image archives.

## Contents

- images/crest-core-service-${release_version}-linux-${release_arch}.tar
- images/crest-core-web-${release_version}-linux-${release_arch}.tar
- deploy/docker/
- deploy/kubernetes/
- installer/init-sql/ob-oracle/crest-core-schema.sql
- scripts/
- docs/release/${release_version}.md
- SHA256SUMS

## Docker Compose Delivery

Load the images on the target host:

\`\`\`bash
docker load -i images/crest-core-service-${release_version}-linux-${release_arch}.tar
docker load -i images/crest-core-web-${release_version}-linux-${release_arch}.tar
\`\`\`

Prepare the production environment file:

\`\`\`bash
mkdir -p .local
cp deploy/docker/production.env.example .local/crest-docker-production.env
node scripts/verify-docker-production.mjs deploy/docker --strict-config .local/crest-docker-production.env
\`\`\`

Start the two-service production topology:

\`\`\`bash
docker compose \\
  --env-file .local/crest-docker-production.env \\
  -f deploy/docker/compose.yaml \\
  up -d --scale crest-core-service=2
\`\`\`

## Kubernetes Delivery

Render a production overlay from real environment values before applying any manifests:

\`\`\`bash
set -a
source .local/crest-production.env
set +a
bash scripts/render-production-overlay.sh
kubectl apply -n <namespace> -f .local/production-overlay
\`\`\`

## External Dependencies

Crest Core production delivery requires external OceanBase Oracle and external Redis Cluster. The package does not include database or Redis containers. Redis must use an environment-specific ACL user and a dedicated hash-tag namespace shared by all Crest Core keys, channels, streams, and consumer groups.

## Integrity Check

Verify package contents before deployment:

\`\`\`bash
sha256sum -c SHA256SUMS
\`\`\`
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

release_version="${CREST_OFFLINE_RELEASE_VERSION:-v$(tr -d '[:space:]' < VERSION)}"
release_arch="${CREST_OFFLINE_PACKAGE_ARCH:-}"
backend_image_tar="${CREST_OFFLINE_BACKEND_IMAGE_TAR:-}"
frontend_image_tar="${CREST_OFFLINE_FRONTEND_IMAGE_TAR:-}"
output_dir="${CREST_OFFLINE_OUTPUT_DIR:-reports/release-offline}"
work_dir="${CREST_OFFLINE_WORK_DIR:-.local/offline-release-package}"

case "${release_version}" in
  v[0-9]*.[0-9]*.[0-9]*)
    ;;
  *)
    fail "CREST_OFFLINE_RELEASE_VERSION must look like vX.Y.Z"
    ;;
esac

case "${release_arch}" in
  amd64|arm64)
    ;;
  *)
    fail "CREST_OFFLINE_PACKAGE_ARCH must be amd64 or arm64"
    ;;
esac

require_file "${backend_image_tar}"
require_file "${frontend_image_tar}"

package_name="crest-core-${release_version}-linux-${release_arch}-offline"
package_dir="${work_dir}/${package_name}"
archive_path="${output_dir}/${package_name}.tar.gz"

rm -rf "${package_dir}" "${archive_path}" "${archive_path}.sha256"
mkdir -p "${package_dir}/images" "${output_dir}" "${work_dir}"

copy_file README.md "${package_dir}/README.project.md"
copy_file LICENSE "${package_dir}/LICENSE"
copy_file VERSION "${package_dir}/VERSION"
copy_file CHANGELOG.md "${package_dir}/CHANGELOG.md"
copy_file "docs/release/${release_version}.md" "${package_dir}/docs/release/${release_version}.md"
copy_file installer/init-sql/ob-oracle/crest-core-schema.sql "${package_dir}/installer/init-sql/ob-oracle/crest-core-schema.sql"

copy_dir deploy/docker "${package_dir}/deploy/docker"
copy_dir deploy/kubernetes "${package_dir}/deploy/kubernetes"
copy_dir deploy/nginx "${package_dir}/deploy/nginx"

mkdir -p "${package_dir}/scripts"
for script in \
  scripts/create-offline-release-package.sh \
  scripts/generate-ob-oracle-init-schema.mjs \
  scripts/production-config-check.sh \
  scripts/redis-cluster-namespace-check.sh \
  scripts/render-production-overlay.sh \
  scripts/sanitize-kubernetes-secrets.mjs \
  scripts/verify-docker-production.mjs \
  scripts/verify-kubernetes-production.mjs \
  scripts/verify-sanitized-kubernetes-secrets.mjs; do
  copy_file "${script}" "${package_dir}/${script}"
done

copy_file "${backend_image_tar}" "${package_dir}/images/crest-core-service-${release_version}-linux-${release_arch}.tar"
copy_file "${frontend_image_tar}" "${package_dir}/images/crest-core-web-${release_version}-linux-${release_arch}.tar"
write_package_readme "${package_dir}/README.md"

{
  echo "package=${package_name}"
  echo "version=${release_version}"
  echo "arch=${release_arch}"
  echo "generated_at_utc=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo "source_commit=$(git rev-parse HEAD 2>/dev/null || printf unknown)"
  echo "backend_image_archive=images/crest-core-service-${release_version}-linux-${release_arch}.tar"
  echo "frontend_image_archive=images/crest-core-web-${release_version}-linux-${release_arch}.tar"
} > "${package_dir}/MANIFEST.txt"

(
  cd "${package_dir}"
  find . -type f ! -name SHA256SUMS -print | sort | while IFS= read -r file; do
    checksum="$(sha256_file "${file}")"
    printf '%s  %s\n' "${checksum}" "${file#./}"
  done > SHA256SUMS
)

tar -C "${work_dir}" -czf "${archive_path}" "${package_name}"
printf '%s  %s\n' "$(sha256_file "${archive_path}")" "$(basename "${archive_path}")" > "${archive_path}.sha256"

info "created ${archive_path}"
info "created ${archive_path}.sha256"
