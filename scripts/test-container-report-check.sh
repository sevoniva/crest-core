#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-container-report-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_CONTAINER_REPORT_CHECK_DIR:-.local/container-report-check-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/good" "${test_root}/bad"

write_report() {
  local file="$1"
  local vulnerabilities="$2"
  cat > "${file}" <<EOF
{
  "SchemaVersion": 2,
  "ArtifactName": "crest-service:test",
  "ArtifactType": "container_image",
  "Results": [
    {
      "Target": "crest-service:test",
      "Class": "os-pkgs",
      "Type": "ubuntu",
      "Vulnerabilities": ${vulnerabilities}
    }
  ]
}
EOF
}

write_report "${test_root}/good/trivy-backend.json" "[]"
write_report "${test_root}/good/trivy-frontend.json" "[]"
cat > "${test_root}/good/trivy-backend-java-skipped.json" <<'EOF'
{
  "SchemaVersion": 2,
  "ArtifactName": "crest-service:test",
  "ArtifactType": "container_image",
  "Metadata": {
    "CrestJavaArtifactScanSkipped": true,
    "CrestJavaArtifactScanReason": "covered-by-maven-sbom-osv-sca"
  },
  "Results": []
}
EOF
cat > "${test_root}/good/trivy-frontend-rootfs.json" <<'EOF'
{
  "SchemaVersion": 2,
  "ArtifactName": "/tmp/crest-rootfs",
  "ArtifactType": "filesystem",
  "Metadata": {
    "CrestScanMode": "rootfs-fallback",
    "CrestContainerImage": "crest-web:test"
  },
  "Results": []
}
EOF
node scripts/container-report-check.mjs "${test_root}/good" >/dev/null

high_bad="${test_root}/bad/high"
mkdir -p "${high_bad}"
write_report "${high_bad}/trivy-backend.json" '[{"VulnerabilityID":"CVE-2099-0001","Severity":"HIGH"}]'
if node scripts/container-report-check.mjs "${high_bad}" >"${high_bad}.log" 2>&1; then
  fail "container report check should fail when a HIGH vulnerability is present"
fi
grep -q 'HIGH=1' "${high_bad}.log" \
  || fail "HIGH vulnerability failure must include the severity count"

critical_bad="${test_root}/bad/critical"
mkdir -p "${critical_bad}"
write_report "${critical_bad}/trivy-backend.json" '[{"VulnerabilityID":"CVE-2099-0002","Severity":"CRITICAL"}]'
if node scripts/container-report-check.mjs "${critical_bad}" >"${critical_bad}.log" 2>&1; then
  fail "container report check should fail when a CRITICAL vulnerability is present"
fi
grep -q 'CRITICAL=1' "${critical_bad}.log" \
  || fail "CRITICAL vulnerability failure must include the severity count"

missing_bad="${test_root}/bad/missing"
mkdir -p "${missing_bad}"
if node scripts/container-report-check.mjs "${missing_bad}" >"${missing_bad}.log" 2>&1; then
  fail "container report check should fail when Trivy reports are missing"
fi
grep -q 'missing Trivy JSON reports' "${missing_bad}.log" \
  || fail "missing report failure must be explicit"

invalid_bad="${test_root}/bad/invalid"
mkdir -p "${invalid_bad}"
cat > "${invalid_bad}/trivy-backend.json" <<'EOF'
{"ArtifactName":"crest-service:test","ArtifactType":"filesystem","Results":[]}
EOF
if node scripts/container-report-check.mjs "${invalid_bad}" >"${invalid_bad}.log" 2>&1; then
  fail "container report check should fail when the report is not for a container image"
fi
grep -q 'container_image artifact' "${invalid_bad}.log" \
  || fail "invalid artifact type failure must be explicit"

unapproved_rootfs_bad="${test_root}/bad/unapproved-rootfs"
mkdir -p "${unapproved_rootfs_bad}"
cat > "${unapproved_rootfs_bad}/trivy-frontend.json" <<'EOF'
{"ArtifactName":"/tmp/rootfs","ArtifactType":"filesystem","Results":[]}
EOF
if node scripts/container-report-check.mjs "${unapproved_rootfs_bad}" >"${unapproved_rootfs_bad}.log" 2>&1; then
  fail "container report check should fail when filesystem reports are not approved rootfs fallbacks"
fi
grep -q 'approved rootfs-fallback' "${unapproved_rootfs_bad}.log" \
  || fail "unapproved rootfs failure must be explicit"

missing_java_reason_bad="${test_root}/bad/missing-java-reason"
mkdir -p "${missing_java_reason_bad}"
cat > "${missing_java_reason_bad}/trivy-backend.json" <<'EOF'
{
  "ArtifactName": "crest-service:test",
  "ArtifactType": "container_image",
  "Metadata": {
    "CrestJavaArtifactScanSkipped": true
  },
  "Results": []
}
EOF
if node scripts/container-report-check.mjs "${missing_java_reason_bad}" >"${missing_java_reason_bad}.log" 2>&1; then
  fail "container report check should fail when skipped Java artifact scans lack a reason"
fi
grep -q 'CrestJavaArtifactScanReason' "${missing_java_reason_bad}.log" \
  || fail "missing Java artifact skip reason failure must be explicit"

echo "test-container-report-check: passed"
