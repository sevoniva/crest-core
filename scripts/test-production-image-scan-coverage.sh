#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-image-scan-coverage: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_IMAGE_SCAN_COVERAGE_DIR:-.local/image-scan-coverage-test-$$}"
evidence_dir="${test_root}/evidence"
container_dir="${test_root}/container"
rm -rf "${test_root}"
mkdir -p "${evidence_dir}" "${container_dir}"

write_deployment() {
  local name="$1"
  local image="$2"
  cat > "${evidence_dir}/deployment-${name}.json" <<EOF
{
  "apiVersion": "apps/v1",
  "kind": "Deployment",
  "metadata": { "name": "${name}" },
  "spec": {
    "template": {
      "spec": {
        "containers": [
          { "name": "${name}", "image": "${image}" }
        ]
      }
    }
  }
}
EOF
}

write_trivy_report() {
  local file="$1"
  local image="$2"
  cat > "${container_dir}/${file}" <<EOF
{
  "ArtifactType": "container_image",
  "ArtifactName": "sha256:test-${file}",
  "Metadata": {
    "CrestScanMode": "container-image",
    "CrestContainerImage": "${image}",
    "CrestContainerName": "backend"
  },
  "Results": []
}
EOF
}

write_trivy_rootfs_fallback_report() {
  local file="$1"
  local image="$2"
  cat > "${container_dir}/${file}" <<EOF
{
  "ArtifactType": "filesystem",
  "ArtifactName": "/tmp/crest-container-rootfs",
  "Metadata": {
    "CrestScanMode": "rootfs-fallback",
    "CrestContainerImage": "${image}",
    "CrestContainerName": "frontend"
  },
  "Results": []
}
EOF
}

write_deployment crest "registry.example.internal/crest-web:v1.0.0"
write_deployment crest-service "registry.example.internal/crest-service:v1.0.0"
write_trivy_rootfs_fallback_report trivy-frontend.json "registry.example.internal/crest-web:v1.0.0"
write_trivy_report trivy-backend.json "registry.example.internal/crest-service:v1.0.0"

node scripts/verify-production-image-scan-coverage.mjs "${evidence_dir}" "${container_dir}" >/dev/null

cat > "${container_dir}/trivy-backend.json" <<'EOF'
{
  "ArtifactType": "container_image",
  "ArtifactName": "registry.example.internal/other-service:v1.0.0",
  "Results": []
}
EOF
if node scripts/verify-production-image-scan-coverage.mjs "${evidence_dir}" "${container_dir}" >"${test_root}/missing-coverage.log" 2>&1; then
  fail "image scan coverage verifier should reject unscanned deployed images"
fi
grep -q 'deployed images missing Trivy scan coverage: registry.example.internal/crest-service:v1.0.0' "${test_root}/missing-coverage.log" \
  || fail "missing image coverage failure must name the unscanned image"

write_trivy_report trivy-backend.json "registry.example.internal/crest-service:v1.0.0"
write_deployment crest-service "registry.example.internal/crest-service:latest"
if node scripts/verify-production-image-scan-coverage.mjs "${evidence_dir}" "${container_dir}" >"${test_root}/latest.log" 2>&1; then
  fail "image scan coverage verifier should reject latest deployment tags"
fi
grep -q 'deployment-crest-service.json must not deploy latest image tags' "${test_root}/latest.log" \
  || fail "latest tag failure must name the deployment evidence file"

write_deployment crest-service "registry.example.internal/crest-service:v1.0.0"
cat > "${container_dir}/trivy-frontend.json" <<'EOF'
{
  "ArtifactType": "filesystem",
  "ArtifactName": "/tmp/crest-container-rootfs",
  "Results": []
}
EOF
if node scripts/verify-production-image-scan-coverage.mjs "${evidence_dir}" "${container_dir}" >"${test_root}/unidentified-rootfs.log" 2>&1; then
  fail "image scan coverage verifier should reject rootfs scans without image metadata"
fi
grep -q 'trivy-frontend.json must describe a container_image artifact or rootfs-fallback scan' "${test_root}/unidentified-rootfs.log" \
  || fail "unidentified rootfs failure must explain missing fallback metadata"

echo "test-production-image-scan-coverage: passed"
