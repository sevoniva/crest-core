#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-go-no-go-summary-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_GO_NO_GO_SUMMARY_DIR:-.local/go-no-go-summary-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}"

sha256_file() {
  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${path}" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${path}" | awk '{print $1}'
  else
    fail "missing shasum or sha256sum for test digest"
  fi
}

external_evidence_summary="${test_root}/external-evidence-summary.txt"
external_evidence_dir="${test_root}/external-evidence"
redis_namespace_report="${external_evidence_dir}/redis-namespace-check.txt"
clean_source_summary_file="${test_root}/crest-core-1.0.0-source.summary.txt"
clean_source_archive_file="${test_root}/crest-core-1.0.0-source.tar.gz"
clean_source_archive_root="${test_root}/clean-source-archive"
clean_source_archive_name="crest-core-1.0.0-source"
clean_source_secret_scan_report="${test_root}/gitleaks-clean-source.json"
clean_source_history_scan_report="${test_root}/gitleaks-history.json"
clean_source_empty_history_scan_report="${test_root}/gitleaks-history-empty.json"
clean_source_generated_at_utc="20260628T000000Z"
clean_source_version="1.0.0"
clean_source_source_branch="release/1.0"
clean_source_source_commit="0123456789abcdef0123456789abcdef01234567"
clean_source_source_file_count="1234"
security_report_dir="${test_root}/security"
security_report_manifest="${security_report_dir}/security-report-manifest.sha256"
github_actions_policy_report="${test_root}/github-actions-policy.txt"
ci_toolchain_policy_report="${test_root}/ci-toolchain-policy.txt"
container_report_dir="${test_root}/container"
container_report_manifest="${container_report_dir}/container-report-manifest.sha256"
container_scan_waiver_file="${test_root}/container-scan-waiver.md"
container_base_image_policy_report="${test_root}/container-base-image-policy.txt"
docker_build_base_image_policy_report="${test_root}/docker-build-base-image-policy.txt"
production_evidence_dir="${test_root}/evidence"
production_evidence_summary="${production_evidence_dir}/summary.txt"
production_evidence_manifest="${production_evidence_dir}/evidence-manifest.sha256"
production_overlay_evidence_dir="${test_root}/production-overlay-evidence"
production_overlay_evidence_summary="${production_overlay_evidence_dir}/summary.txt"
production_overlay_evidence_manifest="${production_overlay_evidence_dir}/overlay-evidence-manifest.sha256"
production_overlay_sanitized_resources="${production_overlay_evidence_dir}/resources-sanitized.json"
production_overlay_sanitized_secrets="${production_overlay_evidence_dir}/secrets-sanitized.json"

external_evidence_required_files=(
  ob-oracle-init.md
  ob-oracle-backup.md
  ob-oracle-restore.md
  redis-cluster.md
  redis-failover.md
  credential-rotation.md
  tls-ingress.md
  storage-rwx.md
  business-smoke.md
  failure-drill.md
)

mkdir -p "${external_evidence_dir}"

write_external_evidence_summary() {
  local evidence_dir="${1:-${external_evidence_dir}}"
  local summary_path="${2:-${external_evidence_summary}}"
  local index=0
  local file digest
  {
    echo "Crest Core external production evidence check"
    echo "status=passed"
    echo "evidence_dir=${evidence_dir}"
    echo "required_evidence_files=${#external_evidence_required_files[@]}"
    echo "evidence_file_count=${#external_evidence_required_files[@]}"
    for file in "${external_evidence_required_files[@]}"; do
      index=$((index + 1))
      digest="$(sha256_file "${evidence_dir}/${file}")"
      echo "${file}: present sha256=${digest}"
      echo "evidence_file_${index}=${file}"
      echo "evidence_file_${index}_sha256=${digest}"
      echo "evidence_file_${index}_description=test evidence for ${file}"
    done
  } > "${summary_path}"
}

for file in "${external_evidence_required_files[@]}"; do
  cat > "${external_evidence_dir}/${file}" <<EOF
status: passed
environment: go-no-go-test
evidence_date: 2026-06-28
owner: platform
artifact_reference: ${file}-ticket
notes: sanitized Go/No-Go test evidence for ${file}
EOF
done
cat > "${redis_namespace_report}" <<EOF
status=passed
redis_cluster_nodes_count=3
redis_node=redis-0:6379
redis_key_prefix={ops01-prod-crest-core}:prod
redis_hash_tag=ops01-prod-crest-core
redis_acl_user=crest-prod
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF
cat >> "${external_evidence_dir}/redis-cluster.md" <<EOF
redis_key_prefix: {ops01-prod-crest-core}:prod
redis_hash_tag: ops01-prod-crest-core
redis_acl_user: crest-prod
redis_namespace_check_report: ${redis_namespace_report}
redis_namespace_check_report_sha256: $(sha256_file "${redis_namespace_report}")
EOF
mkdir -p "${clean_source_archive_root}/${clean_source_archive_name}/docs"
cat > "${clean_source_archive_root}/${clean_source_archive_name}/SOURCE_MANIFEST.txt" <<EOF
generated_at_utc=${clean_source_generated_at_utc}
version=${clean_source_version}
source_commit=${clean_source_source_commit}
EOF
printf '# Crest Core clean source\n' > "${clean_source_archive_root}/${clean_source_archive_name}/README.md"
tar -czf "${clean_source_archive_file}" -C "${clean_source_archive_root}" "${clean_source_archive_name}"
printf '[]\n' > "${clean_source_secret_scan_report}"
cat > "${clean_source_history_scan_report}" <<'JSON'
[
  {"RuleID":"generic-api-key","File":"deploy/kubernetes/crest-ob-oracle-redis.yaml","Commit":"abc"},
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml","Commit":"def"}
]
JSON
printf '[]\n' > "${clean_source_empty_history_scan_report}"
cat >> "${external_evidence_dir}/credential-rotation.md" <<EOF
history_scan_report: ${clean_source_history_scan_report}
history_scan_report_sha256: $(sha256_file "${clean_source_history_scan_report}")
history_findings_remaining: 2
affected_credential_classes: initial-admin-password,application-encryption-key
credential_rotation_status: rotated-before-delivery
delivery_path: clean-source
rotation_evidence_id: SEC-12345
approved_by: platform-security
approval_date: 2026-06-28
EOF
cat >> "${external_evidence_dir}/tls-ingress.md" <<'EOF'
ingress_host: crest-smoke.example.internal
tls_expiry_monitor: platform-cert-monitor
EOF
cat >> "${external_evidence_dir}/storage-rwx.md" <<'EOF'
pvc_name: crest-data
access_mode: ReadWriteMany
EOF
cat >> "${external_evidence_dir}/business-smoke.md" <<'EOF'
smoke_scope: login,dashboard,dataset-preview,export,async-task,websocket
EOF
cat >> "${external_evidence_dir}/failure-drill.md" <<'EOF'
drill_scope: rollout-restart,api-pod-delete,worker-pod-delete
EOF
write_external_evidence_summary
mkdir -p "${security_report_dir}" "${container_report_dir}"
cat > "${security_report_dir}/semgrep.json" <<'JSON'
{"results":[]}
JSON
printf '[]\n' > "${security_report_dir}/gitleaks.json"
printf 'io.crest:crest:1.0.0\n' > "${security_report_dir}/maven-dependency-tree.txt"
cat > "${security_report_dir}/crest-bom.json" <<'JSON'
{"components":[{"name":"crest-service","version":"1.0.0"}]}
JSON
cat > "${security_report_dir}/pnpm-audit.json" <<'JSON'
{"metadata":{"vulnerabilities":{"info":0,"low":0,"moderate":0,"high":0,"critical":0,"total":0}}}
JSON
cat > "${security_report_dir}/frontend-licenses.json" <<'JSON'
{"MIT":[{"name":"crest-web","versions":["1.0.0"]}]}
JSON
cat > "${security_report_dir}/license-policy.txt" <<'EOF'
status=passed
policy_file=config/license-policy.json
maven_bom=reports/security/crest-bom.json
frontend_licenses=reports/security/frontend-licenses.json
maven_components=1
frontend_license_groups=1
license_items=2
reviewed_exceptions=0
violations=0
EOF
cat > "${security_report_dir}/osv-frontend.json" <<'JSON'
{"results":[]}
JSON
cat > "${security_report_dir}/osv-maven-sbom.json" <<'JSON'
{"results":[]}
JSON
{
  echo "$(sha256_file "${security_report_dir}/semgrep.json")  semgrep.json"
  echo "$(sha256_file "${security_report_dir}/gitleaks.json")  gitleaks.json"
  echo "$(sha256_file "${security_report_dir}/maven-dependency-tree.txt")  maven-dependency-tree.txt"
  echo "$(sha256_file "${security_report_dir}/crest-bom.json")  crest-bom.json"
  echo "$(sha256_file "${security_report_dir}/pnpm-audit.json")  pnpm-audit.json"
  echo "$(sha256_file "${security_report_dir}/frontend-licenses.json")  frontend-licenses.json"
  echo "$(sha256_file "${security_report_dir}/license-policy.txt")  license-policy.txt"
  echo "$(sha256_file "${security_report_dir}/osv-frontend.json")  osv-frontend.json"
  echo "$(sha256_file "${security_report_dir}/osv-maven-sbom.json")  osv-maven-sbom.json"
} > "${security_report_manifest}"
cat > "${github_actions_policy_report}" <<'EOF'
status=passed
workflow_dir=.github/workflows
workflow_files=5
action_references=31
github_action_refs_sha_pinned=true
EOF
cat > "${ci_toolchain_policy_report}" <<'EOF'
status=passed
workflow_dir=.github/workflows
workflow_files=5
semgrep_version=1.155.0
osv_scanner_version=v1.9.2
gitleaks_version=v8.28.0
actionlint_version=v1.7.7
trivy_version=0.71.2
centralized_ci_tool_installs=true
EOF
cat > "${container_report_dir}/trivy-backend.json" <<'JSON'
{"ArtifactType":"container_image","ArtifactName":"registry.example.internal/crest-core-service:v1.0.0","Results":[]}
JSON
cat > "${container_report_dir}/trivy-frontend.json" <<'JSON'
{"ArtifactType":"container_image","ArtifactName":"registry.example.internal/crest-core-web:v1.0.0","Results":[]}
JSON
{
  echo "$(sha256_file "${container_report_dir}/trivy-backend.json")  trivy-backend.json"
  echo "$(sha256_file "${container_report_dir}/trivy-frontend.json")  trivy-frontend.json"
} > "${container_report_manifest}"
cat > "${container_base_image_policy_report}" <<'EOF'
status=passed
require_base_image_digests=true
jdk_image=registry.example.internal/eclipse-temurin:17-jdk-jammy@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
jdk_image_digest_pinned=true
runtime_image=registry.example.internal/ubuntu:24.04@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
runtime_image_digest_pinned=true
nginx_image=registry.example.internal/nginx:1.29-alpine@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
nginx_image_digest_pinned=true
EOF
cat > "${docker_build_base_image_policy_report}" <<'EOF'
status=passed
require_base_image_digests=true
jdk_image=registry.example.internal/eclipse-temurin:17-jdk-jammy@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
jdk_image_digest_pinned=true
runtime_image=registry.example.internal/ubuntu:24.04@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
runtime_image_digest_pinned=true
nginx_image=registry.example.internal/nginx:1.29-alpine@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
nginx_image_digest_pinned=true
EOF
mkdir -p "${production_evidence_dir}"
cat > "${production_evidence_summary}" <<'EOF'
Crest Core production evidence bundle
timestamp_utc=20260102T030405Z
namespace=crest-prod
runtime_check_require_ingress_address=true
runtime_check=passed
evidence_file_count=7
evidence_manifest=evidence-manifest.sha256
EOF
cat > "${production_evidence_dir}/production-runtime-check.txt" <<'EOF'
runtime-check: namespace crest-prod passed live production runtime checks
EOF

write_statefulset_evidence() {
  local name="$1"
  local image="$2"
  cat > "${production_evidence_dir}/statefulset-${name}.json" <<EOF
{
  "apiVersion": "apps/v1",
  "kind": "StatefulSet",
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

write_statefulset_evidence crest "registry.example.internal/crest-core-web:v1.0.0"
write_statefulset_evidence crest-service "registry.example.internal/crest-core-service:v1.0.0"
cat > "${production_evidence_dir}/service-crest.json" <<'EOF'
{
  "apiVersion": "v1",
  "kind": "Service",
  "metadata": { "name": "crest" }
}
EOF
cat > "${production_evidence_dir}/service-crest-service.json" <<'EOF'
{
  "apiVersion": "v1",
  "kind": "Service",
  "metadata": { "name": "crest-service" }
}
EOF

cat > "${production_evidence_dir}/secrets-sanitized.json" <<'EOF'
{
  "items": [
    {
      "metadata": { "name": "crest-db-secret" },
      "sanitizedData": {
        "CREST_DB_PASSWORD": { "present": true, "decodedLength": 24 }
      }
    },
    {
      "metadata": { "name": "crest-redis-secret" },
      "sanitizedData": {
        "CREST_REDIS_PASSWORD": { "present": true, "decodedLength": 32 }
      }
    },
    {
      "metadata": { "name": "crest-tls" },
      "sanitizedData": {
        "tls.crt": { "present": true, "decodedLength": 2048 },
        "tls.key": { "present": true, "decodedLength": 2048 }
      }
    }
  ]
}
EOF
{
  echo "$(sha256_file "${production_evidence_dir}/statefulset-crest.json")  statefulset-crest.json"
  echo "$(sha256_file "${production_evidence_dir}/statefulset-crest-service.json")  statefulset-crest-service.json"
  echo "$(sha256_file "${production_evidence_dir}/production-runtime-check.txt")  production-runtime-check.txt"
  echo "$(sha256_file "${production_evidence_dir}/secrets-sanitized.json")  secrets-sanitized.json"
  echo "$(sha256_file "${production_evidence_dir}/service-crest.json")  service-crest.json"
  echo "$(sha256_file "${production_evidence_dir}/service-crest-service.json")  service-crest-service.json"
  echo "$(sha256_file "${production_evidence_summary}")  summary.txt"
} > "${production_evidence_manifest}"

write_evidence_manifest_for_dir() {
  local dir="$1"
  local manifest="${2:-${dir}/evidence-manifest.sha256}"
  local rel_path
  : > "${manifest}"
  while IFS= read -r rel_path; do
    printf '%s  %s\n' "$(sha256_file "${dir}/${rel_path}")" "${rel_path}" >> "${manifest}"
  done < <(
    cd "${dir}"
    find . -type f ! -name 'evidence-manifest.sha256' -print \
      | sed 's#^\./##' \
      | LC_ALL=C sort
  )
}

write_overlay_evidence_manifest_for_dir() {
  local dir="$1"
  local manifest="${2:-${dir}/overlay-evidence-manifest.sha256}"
  {
    echo "$(sha256_file "${dir}/resources-sanitized.json")  resources-sanitized.json"
    echo "$(sha256_file "${dir}/secrets-sanitized.json")  secrets-sanitized.json"
    echo "$(sha256_file "${dir}/summary.txt")  summary.txt"
  } > "${manifest}"
}

mkdir -p "${production_overlay_evidence_dir}"
cat > "${production_overlay_sanitized_resources}" <<'JSON'
{
  "kind": "List",
  "items": [
    {
      "apiVersion": "v1",
      "kind": "ConfigMap",
      "metadata": { "name": "crest-env" },
      "data": {
        "CREST_PRODUCTION_MODE": "true",
        "CREST_REDIS_KEY_PREFIX": "{ops01-prod-crest-core}:prod"
      }
    },
    {
      "apiVersion": "v1",
      "kind": "Secret",
      "metadata": { "name": "crest-db-secret" },
      "type": "Opaque",
      "sanitizedData": {
        "CREST_DB_PASSWORD": { "present": true, "decodedLength": 24 }
      }
    },
    {
      "apiVersion": "v1",
      "kind": "Secret",
      "metadata": { "name": "crest-redis-secret" },
      "type": "Opaque",
      "sanitizedData": {
        "CREST_REDIS_PASSWORD": { "present": true, "decodedLength": 32 }
      }
    }
  ]
}
JSON
cat > "${production_overlay_sanitized_secrets}" <<'JSON'
{
  "items": [
    {
      "metadata": { "name": "crest-db-secret" },
      "sanitizedData": {
        "CREST_DB_PASSWORD": { "present": true, "decodedLength": 24 }
      }
    },
    {
      "metadata": { "name": "crest-redis-secret" },
      "sanitizedData": {
        "CREST_REDIS_PASSWORD": { "present": true, "decodedLength": 32 }
      }
    }
  ]
}
JSON
cat > "${production_overlay_evidence_summary}" <<EOF
Crest Core production overlay sanitized evidence
timestamp_utc=20260628T000000Z
overlay_dir=.local/production-overlay
resource_count=3
secret_count=2
resource_kind_ConfigMap=1
resource_kind_Secret=2
secret_name=crest-db-secret
secret_name=crest-redis-secret
sanitized_resources=resources-sanitized.json
sanitized_resources_sha256=$(sha256_file "${production_overlay_sanitized_resources}")
sanitized_secrets=secrets-sanitized.json
sanitized_secrets_sha256=$(sha256_file "${production_overlay_sanitized_secrets}")
evidence_manifest=overlay-evidence-manifest.sha256
EOF
write_overlay_evidence_manifest_for_dir "${production_overlay_evidence_dir}" "${production_overlay_evidence_manifest}"

cat > "${clean_source_summary_file}" <<'EOF'
Crest Core clean source release summary
EOF
{
  echo "generated_at_utc=${clean_source_generated_at_utc}"
  echo "version=${clean_source_version}"
  echo "source_branch=${clean_source_source_branch}"
  echo "source_commit=${clean_source_source_commit}"
  echo "archive=${clean_source_archive_file}"
  echo "archive_sha256=$(sha256_file "${clean_source_archive_file}")"
  echo "source_file_count=${clean_source_source_file_count}"
  echo "secret_scan_report=${clean_source_secret_scan_report}"
  echo "secret_scan_findings=0"
  echo "source_worktree_dirty=false"
  echo "history_scan_report=${clean_source_history_scan_report}"
  echo "history_scan_report_sha256=$(sha256_file "${clean_source_history_scan_report}")"
  echo "history_findings_remaining=2"
  echo "history_delivery_path=clean-source"
  echo "credential_rotation_status=rotated-before-delivery"
  echo "affected_credential_classes=initial-admin-password,application-encryption-key"
} >> "${clean_source_summary_file}"

write_gate_report_evidence() {
  cat <<EOF
security_report_dir=${security_report_dir}
security_report_manifest=${security_report_manifest}
security_report_manifest_sha256=$(sha256_file "${security_report_manifest}")
github_actions_policy_report=${github_actions_policy_report}
github_actions_policy_report_sha256=$(sha256_file "${github_actions_policy_report}")
ci_toolchain_policy_report=${ci_toolchain_policy_report}
ci_toolchain_policy_report_sha256=$(sha256_file "${ci_toolchain_policy_report}")
container_report_dir=${container_report_dir}
container_report_manifest=${container_report_manifest}
container_report_manifest_sha256=$(sha256_file "${container_report_manifest}")
container_base_image_policy_report=${container_base_image_policy_report}
container_base_image_policy_report_sha256=$(sha256_file "${container_base_image_policy_report}")
docker_build_base_image_policy_report=${docker_build_base_image_policy_report}
docker_build_base_image_policy_report_sha256=$(sha256_file "${docker_build_base_image_policy_report}")
require_base_image_digests=true
EOF
}

write_artifact_evidence() {
  local history_findings_remaining="${1:-2}"
  local history_delivery_path="${2:-clean-source}"
  local credential_rotation_status="${3:-rotated-before-delivery}"
  local clean_source_worktree_dirty="${4:-false}"
  local selected_secret_scan_report="${5:-${clean_source_secret_scan_report}}"
  local selected_history_scan_report="${6:-${clean_source_history_scan_report}}"
  local affected_credential_classes="${7:-initial-admin-password,application-encryption-key}"
  {
    echo "Crest Core clean source release summary"
    echo "generated_at_utc=${clean_source_generated_at_utc}"
    echo "version=${clean_source_version}"
    echo "source_branch=${clean_source_source_branch}"
    echo "source_commit=${clean_source_source_commit}"
    echo "archive=${clean_source_archive_file}"
    echo "archive_sha256=$(sha256_file "${clean_source_archive_file}")"
    echo "source_file_count=${clean_source_source_file_count}"
    echo "secret_scan_report=${selected_secret_scan_report}"
    echo "secret_scan_findings=0"
    echo "source_worktree_dirty=${clean_source_worktree_dirty}"
    echo "history_scan_report=${selected_history_scan_report}"
    echo "history_scan_report_sha256=$(sha256_file "${selected_history_scan_report}")"
    echo "history_findings_remaining=${history_findings_remaining}"
    echo "history_delivery_path=${history_delivery_path}"
    echo "credential_rotation_status=${credential_rotation_status}"
    echo "affected_credential_classes=${affected_credential_classes}"
  } > "${clean_source_summary_file}"
  cat <<EOF
$(write_gate_report_evidence)
external_evidence_summary=${external_evidence_summary}
external_evidence_summary_sha256=$(sha256_file "${external_evidence_summary}")
production_overlay_evidence_dir=${production_overlay_evidence_dir}
production_overlay_evidence_summary=${production_overlay_evidence_summary}
production_overlay_evidence_summary_sha256=$(sha256_file "${production_overlay_evidence_summary}")
production_overlay_evidence_manifest=${production_overlay_evidence_manifest}
production_overlay_evidence_manifest_sha256=$(sha256_file "${production_overlay_evidence_manifest}")
production_overlay_sanitized_resources=${production_overlay_sanitized_resources}
production_overlay_sanitized_resources_sha256=$(sha256_file "${production_overlay_sanitized_resources}")
production_overlay_sanitized_secrets=${production_overlay_sanitized_secrets}
production_overlay_sanitized_secrets_sha256=$(sha256_file "${production_overlay_sanitized_secrets}")
production_evidence_dir=${production_evidence_dir}
production_evidence_summary=${production_evidence_summary}
production_evidence_summary_sha256=$(sha256_file "${production_evidence_summary}")
production_evidence_manifest=${production_evidence_manifest}
production_evidence_manifest_sha256=$(sha256_file "${production_evidence_manifest}")
production_evidence_runtime_check=passed
clean_source_generated_at_utc=${clean_source_generated_at_utc}
clean_source_version=${clean_source_version}
clean_source_source_branch=${clean_source_source_branch}
clean_source_source_commit=${clean_source_source_commit}
clean_source_summary=${clean_source_summary_file}
clean_source_summary_sha256=$(sha256_file "${clean_source_summary_file}")
clean_source_archive=${clean_source_archive_file}
clean_source_archive_sha256=$(sha256_file "${clean_source_archive_file}")
clean_source_source_file_count=${clean_source_source_file_count}
clean_source_secret_scan_report=${selected_secret_scan_report}
clean_source_secret_scan_report_sha256=$(sha256_file "${selected_secret_scan_report}")
clean_source_secret_scan_findings=0
clean_source_worktree_dirty=${clean_source_worktree_dirty}
clean_source_history_scan_report=${selected_history_scan_report}
clean_source_history_scan_report_sha256=$(sha256_file "${selected_history_scan_report}")
clean_source_history_findings_remaining=${history_findings_remaining}
clean_source_history_delivery_path=${history_delivery_path}
clean_source_credential_rotation_status=${credential_rotation_status}
clean_source_affected_credential_classes=${affected_credential_classes}
EOF
}

write_history_secret_evidence() {
  local history_findings_remaining="${1:-2}"
  local history_delivery_path="${2:-clean-source}"
  local credential_rotation_status="${3:-rotated-before-delivery}"
  local affected_credential_classes="${4:-initial-admin-password,application-encryption-key}"
  local selected_history_scan_report="${5:-}"
  if [[ -z "${selected_history_scan_report}" ]]; then
    if [[ "${history_findings_remaining}" == "0" ]]; then
      selected_history_scan_report="${clean_source_empty_history_scan_report}"
    else
      selected_history_scan_report="${clean_source_history_scan_report}"
    fi
  fi
  cat <<EOF
history_secret_evidence=passed
history_secret_scan_report=${selected_history_scan_report}
history_secret_scan_report_sha256=$(sha256_file "${selected_history_scan_report}")
history_secret_findings_remaining=${history_findings_remaining}
history_secret_affected_credential_classes=${affected_credential_classes}
history_secret_credential_rotation_status=${credential_rotation_status}
history_secret_delivery_path=${history_delivery_path}
history_secret_rotation_evidence_id=SEC-12345
history_secret_approved_by=platform-security
history_secret_approval_date=2026-06-28
EOF
}

write_container_scan_waiver_summary_fields() {
  cat <<EOF
skip_container_scan=true
container_scan_waiver=true
container_scan_waiver_file=${container_scan_waiver_file}
container_scan_waiver_file_sha256=$(sha256_file "${container_scan_waiver_file}")
container_scan_waiver_status=approved
container_scan_waiver_scope=crest-core-web,crest-core-service
container_scan_waiver_reason=temporary-user-approved-container-image-scan-exception
container_scan_waiver_approved_by=platform-security
container_scan_waiver_approval_date=${container_scan_waiver_approval_date}
container_scan_waiver_compensating_controls=sast-sca-base-image-digest-policy-docker-build-runtime-evidence
EOF
}

container_scan_waiver_approval_date="$(date -u +%F)"
cat > "${container_scan_waiver_file}" <<EOF
status: approved
scope: crest-core-web,crest-core-service
reason: temporary-user-approved-container-image-scan-exception
approved_by: platform-security
approval_date: ${container_scan_waiver_approval_date}
compensating_controls: sast-sca-base-image-digest-policy-docker-build-runtime-evidence
EOF

go_summary="${test_root}/go-no-go.txt"
cat > "${go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence)
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bash scripts/production-go-no-go-summary-check.sh "${go_summary}" >/dev/null

waived_container_summary="${test_root}/go-no-go-container-waiver.txt"
cat > "${waived_container_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_container_scan_waiver_summary_fields)
$(write_artifact_evidence | sed '/^container_report_/d')
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan-waiver: passed
container-scan: waived
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bash scripts/production-go-no-go-summary-check.sh "${waived_container_summary}" >/dev/null

bad_clean_archive_root="${test_root}/bad-clean-source-archive"
bad_clean_archive_file="${test_root}/crest-core-1.0.0-source-with-git.tar.gz"
bad_clean_archive_summary="${test_root}/crest-core-1.0.0-source-with-git.summary.txt"
mkdir -p "${bad_clean_archive_root}/${clean_source_archive_name}/.git"
printf 'manifest\n' > "${bad_clean_archive_root}/${clean_source_archive_name}/SOURCE_MANIFEST.txt"
printf '[core]\n' > "${bad_clean_archive_root}/${clean_source_archive_name}/.git/config"
tar -czf "${bad_clean_archive_file}" -C "${bad_clean_archive_root}" "${clean_source_archive_name}"
sed \
  -e "s|^archive=.*|archive=${bad_clean_archive_file}|" \
  -e "s|^archive_sha256=.*|archive_sha256=$(sha256_file "${bad_clean_archive_file}")|" \
  "${clean_source_summary_file}" > "${bad_clean_archive_summary}"
bad_clean_archive_summary_file="${test_root}/bad-clean-source-archive.txt"
sed \
  -e "s|^clean_source_summary=.*|clean_source_summary=${bad_clean_archive_summary}|" \
  -e "s|^clean_source_summary_sha256=.*|clean_source_summary_sha256=$(sha256_file "${bad_clean_archive_summary}")|" \
  -e "s|^clean_source_archive=.*|clean_source_archive=${bad_clean_archive_file}|" \
  -e "s|^clean_source_archive_sha256=.*|clean_source_archive_sha256=$(sha256_file "${bad_clean_archive_file}")|" \
  "${go_summary}" > "${bad_clean_archive_summary_file}"
bad_clean_archive_log="${test_root}/bad-clean-source-archive.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_clean_archive_summary_file}" >"${bad_clean_archive_log}" 2>&1; then
  fail "Go/No-Go summary with a clean source archive containing .git should fail"
fi

grep -q 'clean source archive contains forbidden history, private, report, dependency or build paths' "${bad_clean_archive_log}" \
  || fail "bad clean source archive failure must explain the forbidden path"

bad_runtime_output_dir="${test_root}/evidence-runtime-warning"
rm -rf "${bad_runtime_output_dir}"
cp -R "${production_evidence_dir}" "${bad_runtime_output_dir}"
bad_runtime_output_summary="${bad_runtime_output_dir}/summary.txt"
bad_runtime_output_manifest="${bad_runtime_output_dir}/evidence-manifest.sha256"
cat > "${bad_runtime_output_dir}/production-runtime-check.txt" <<'EOF'
runtime-check: warning: crest Ingress has no load balancer address yet
runtime-check: namespace crest-prod passed live production runtime checks
EOF
write_evidence_manifest_for_dir "${bad_runtime_output_dir}" "${bad_runtime_output_manifest}"
bad_runtime_output_summary_file="${test_root}/bad-runtime-output.txt"
sed \
  -e "s|^production_evidence_dir=.*|production_evidence_dir=${bad_runtime_output_dir}|" \
  -e "s|^production_evidence_summary=.*|production_evidence_summary=${bad_runtime_output_summary}|" \
  -e "s|^production_evidence_summary_sha256=.*|production_evidence_summary_sha256=$(sha256_file "${bad_runtime_output_summary}")|" \
  -e "s|^production_evidence_manifest=.*|production_evidence_manifest=${bad_runtime_output_manifest}|" \
  -e "s|^production_evidence_manifest_sha256=.*|production_evidence_manifest_sha256=$(sha256_file "${bad_runtime_output_manifest}")|" \
  "${go_summary}" > "${bad_runtime_output_summary_file}"
bad_runtime_output_log="${test_root}/bad-runtime-output.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_runtime_output_summary_file}" >"${bad_runtime_output_log}" 2>&1; then
  fail "Go/No-Go summary with runtime check warnings should fail"
fi

grep -q 'production runtime check output must not contain runtime warnings' "${bad_runtime_output_log}" \
  || fail "bad runtime output failure must explain runtime warnings"

no_ingress_evidence_dir="${test_root}/evidence-no-ingress"
rm -rf "${no_ingress_evidence_dir}"
cp -R "${production_evidence_dir}" "${no_ingress_evidence_dir}"
no_ingress_evidence_summary="${no_ingress_evidence_dir}/summary.txt"
no_ingress_evidence_manifest="${no_ingress_evidence_dir}/evidence-manifest.sha256"
sed 's/^runtime_check_require_ingress_address=.*/runtime_check_require_ingress_address=false/' \
  "${production_evidence_summary}" > "${no_ingress_evidence_summary}"
write_evidence_manifest_for_dir "${no_ingress_evidence_dir}" "${no_ingress_evidence_manifest}"
no_ingress_summary="${test_root}/no-ingress-runtime-check.txt"
sed \
  -e "s|^production_evidence_dir=.*|production_evidence_dir=${no_ingress_evidence_dir}|" \
  -e "s|^production_evidence_summary=.*|production_evidence_summary=${no_ingress_evidence_summary}|" \
  -e "s|^production_evidence_summary_sha256=.*|production_evidence_summary_sha256=$(sha256_file "${no_ingress_evidence_summary}")|" \
  -e "s|^production_evidence_manifest=.*|production_evidence_manifest=${no_ingress_evidence_manifest}|" \
  -e "s|^production_evidence_manifest_sha256=.*|production_evidence_manifest_sha256=$(sha256_file "${no_ingress_evidence_manifest}")|" \
  "${go_summary}" > "${no_ingress_summary}"
no_ingress_log="${test_root}/no-ingress-runtime-check.log"
if bash scripts/production-go-no-go-summary-check.sh "${no_ingress_summary}" >"${no_ingress_log}" 2>&1; then
  fail "Go/No-Go summary without enforced ingress address runtime check should fail"
fi

grep -q 'production evidence summary must record runtime_check_require_ingress_address=true' "${no_ingress_log}" \
  || fail "missing ingress address runtime check failure must explain the production evidence requirement"

bad_history_approval_date_summary="${test_root}/bad-history-approval-date.txt"
sed 's/^history_secret_approval_date=.*/history_secret_approval_date=2026-06-30/' \
  "${go_summary}" > "${bad_history_approval_date_summary}"
bad_history_approval_date_log="${test_root}/bad-history-approval-date.log"
if CREST_EVIDENCE_TODAY=2026-06-29 \
  bash scripts/production-go-no-go-summary-check.sh "${bad_history_approval_date_summary}" >"${bad_history_approval_date_log}" 2>&1; then
  fail "Go/No-Go summary with a future history approval date should fail"
fi

grep -q 'history_secret_approval_date must not be in the future' "${bad_history_approval_date_log}" \
  || fail "future history approval date failure must explain the date problem"

bad_overlay_future_timestamp_dir="${test_root}/bad-overlay-future-timestamp"
bad_overlay_future_timestamp_summary="${bad_overlay_future_timestamp_dir}/summary.txt"
bad_overlay_future_timestamp_manifest="${bad_overlay_future_timestamp_dir}/overlay-evidence-manifest.sha256"
rm -rf "${bad_overlay_future_timestamp_dir}"
cp -R "${production_overlay_evidence_dir}" "${bad_overlay_future_timestamp_dir}"
sed 's/^timestamp_utc=.*/timestamp_utc=20260630T000000Z/' \
  "${production_overlay_evidence_summary}" > "${bad_overlay_future_timestamp_summary}"
write_overlay_evidence_manifest_for_dir "${bad_overlay_future_timestamp_dir}" "${bad_overlay_future_timestamp_manifest}"
bad_overlay_future_timestamp_go_summary="${test_root}/bad-overlay-future-timestamp.txt"
cat > "${bad_overlay_future_timestamp_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^production_overlay_evidence_dir=.*|production_overlay_evidence_dir=${bad_overlay_future_timestamp_dir}|" \
  -e "s|^production_overlay_evidence_summary=.*|production_overlay_evidence_summary=${bad_overlay_future_timestamp_summary}|" \
  -e "s|^production_overlay_evidence_summary_sha256=.*|production_overlay_evidence_summary_sha256=$(sha256_file "${bad_overlay_future_timestamp_summary}")|" \
  -e "s|^production_overlay_evidence_manifest=.*|production_overlay_evidence_manifest=${bad_overlay_future_timestamp_manifest}|" \
  -e "s|^production_overlay_evidence_manifest_sha256=.*|production_overlay_evidence_manifest_sha256=$(sha256_file "${bad_overlay_future_timestamp_manifest}")|" \
  -e "s|^production_overlay_sanitized_resources=.*|production_overlay_sanitized_resources=${bad_overlay_future_timestamp_dir}/resources-sanitized.json|" \
  -e "s|^production_overlay_sanitized_secrets=.*|production_overlay_sanitized_secrets=${bad_overlay_future_timestamp_dir}/secrets-sanitized.json|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF
bad_overlay_future_timestamp_log="${test_root}/bad-overlay-future-timestamp.log"
if CREST_EVIDENCE_NOW_UTC=20260629T235959Z \
  bash scripts/production-go-no-go-summary-check.sh "${bad_overlay_future_timestamp_go_summary}" >"${bad_overlay_future_timestamp_log}" 2>&1; then
  fail "Go/No-Go summary with future overlay evidence timestamp should fail"
fi

grep -q 'production overlay evidence summary timestamp_utc must not be in the future' "${bad_overlay_future_timestamp_log}" \
  || fail "future overlay timestamp failure must explain the overlay evidence timestamp"

bad_production_future_timestamp_dir="${test_root}/bad-production-future-timestamp"
bad_production_future_timestamp_summary="${bad_production_future_timestamp_dir}/summary.txt"
bad_production_future_timestamp_manifest="${bad_production_future_timestamp_dir}/evidence-manifest.sha256"
rm -rf "${bad_production_future_timestamp_dir}"
cp -R "${production_evidence_dir}" "${bad_production_future_timestamp_dir}"
sed 's/^timestamp_utc=.*/timestamp_utc=20260630T000000Z/' \
  "${production_evidence_summary}" > "${bad_production_future_timestamp_summary}"
write_evidence_manifest_for_dir "${bad_production_future_timestamp_dir}" "${bad_production_future_timestamp_manifest}"
bad_production_future_timestamp_go_summary="${test_root}/bad-production-future-timestamp.txt"
cat > "${bad_production_future_timestamp_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^production_evidence_dir=.*|production_evidence_dir=${bad_production_future_timestamp_dir}|" \
  -e "s|^production_evidence_summary=.*|production_evidence_summary=${bad_production_future_timestamp_summary}|" \
  -e "s|^production_evidence_summary_sha256=.*|production_evidence_summary_sha256=$(sha256_file "${bad_production_future_timestamp_summary}")|" \
  -e "s|^production_evidence_manifest=.*|production_evidence_manifest=${bad_production_future_timestamp_manifest}|" \
  -e "s|^production_evidence_manifest_sha256=.*|production_evidence_manifest_sha256=$(sha256_file "${bad_production_future_timestamp_manifest}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF
bad_production_future_timestamp_log="${test_root}/bad-production-future-timestamp.log"
if CREST_EVIDENCE_NOW_UTC=20260629T235959Z \
  bash scripts/production-go-no-go-summary-check.sh "${bad_production_future_timestamp_go_summary}" >"${bad_production_future_timestamp_log}" 2>&1; then
  fail "Go/No-Go summary with future production evidence timestamp should fail"
fi

grep -q 'production evidence summary timestamp_utc must not be in the future' "${bad_production_future_timestamp_log}" \
  || fail "future production timestamp failure must explain the production evidence timestamp"

bad_clean_future_timestamp_summary_file="${test_root}/crest-core-1.0.0-source-future.summary.txt"
sed 's/^generated_at_utc=.*/generated_at_utc=20260630T000000Z/' \
  "${clean_source_summary_file}" > "${bad_clean_future_timestamp_summary_file}"
bad_clean_future_timestamp_go_summary="${test_root}/bad-clean-source-future-timestamp.txt"
cat > "${bad_clean_future_timestamp_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^clean_source_generated_at_utc=.*|clean_source_generated_at_utc=20260630T000000Z|" \
  -e "s|^clean_source_summary=.*|clean_source_summary=${bad_clean_future_timestamp_summary_file}|" \
  -e "s|^clean_source_summary_sha256=.*|clean_source_summary_sha256=$(sha256_file "${bad_clean_future_timestamp_summary_file}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF
bad_clean_future_timestamp_log="${test_root}/bad-clean-source-future-timestamp.log"
if CREST_EVIDENCE_NOW_UTC=20260629T235959Z \
  bash scripts/production-go-no-go-summary-check.sh "${bad_clean_future_timestamp_go_summary}" >"${bad_clean_future_timestamp_log}" 2>&1; then
  fail "Go/No-Go summary with future clean-source timestamp should fail"
fi

grep -q 'clean source generated_at_utc must not be in the future' "${bad_clean_future_timestamp_log}" \
  || fail "future clean-source timestamp failure must explain the clean-source timestamp"

checked_overlay_summary="${test_root}/go-no-go-checked-overlay.txt"
cat > "${checked_overlay_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence 0 clean-history not-applicable-clean-history false "${clean_source_secret_scan_report}" "${clean_source_empty_history_scan_report}")
$(write_history_secret_evidence 0 clean-history not-applicable-clean-history)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-check: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bash scripts/production-go-no-go-summary-check.sh "${checked_overlay_summary}" >/dev/null

missing_docker_env_summary="${test_root}/missing-docker-env.txt"
cat > "${missing_docker_env_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence)
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

missing_docker_env_log="${test_root}/missing-docker-env.log"
if bash scripts/production-go-no-go-summary-check.sh "${missing_docker_env_summary}" >"${missing_docker_env_log}" 2>&1; then
  fail "Go/No-Go summary without Docker environment preflight evidence should fail"
fi

grep -q 'docker-environment: passed' "${missing_docker_env_log}" \
  || fail "missing Docker environment failure must explain the required evidence"

missing_artifact_evidence_summary="${test_root}/missing-artifact-evidence.txt"
cat > "${missing_artifact_evidence_summary}" <<'EOF'
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
history_secret_evidence=passed
history_secret_findings_remaining=2
history_secret_affected_credential_classes=initial-admin-password,application-encryption-key
history_secret_credential_rotation_status=rotated-before-delivery
history_secret_delivery_path=clean-source
history_secret_rotation_evidence_id=SEC-12345
history_secret_approved_by=platform-security
history_secret_approval_date=2026-06-28
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

missing_artifact_evidence_log="${test_root}/missing-artifact-evidence.log"
if bash scripts/production-go-no-go-summary-check.sh "${missing_artifact_evidence_summary}" >"${missing_artifact_evidence_log}" 2>&1; then
  fail "Go/No-Go summary without clean source and external evidence digests should fail"
fi

grep -q 'security_report_dir path' "${missing_artifact_evidence_log}" \
  || fail "missing artifact evidence failure must explain the required security report path"

bad_digest_summary="${test_root}/bad-digest.txt"
cat > "${bad_digest_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed 's/^external_evidence_summary_sha256=.*/external_evidence_summary_sha256=0000000000000000000000000000000000000000000000000000000000000000/')
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_digest_log="${test_root}/bad-digest.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_digest_summary}" >"${bad_digest_log}" 2>&1; then
  fail "Go/No-Go summary with a mismatched evidence digest should fail"
fi

grep -q 'external evidence summary SHA-256 mismatch' "${bad_digest_log}" \
  || fail "bad digest failure must explain the SHA-256 mismatch"

bad_overlay_evidence_dir="${test_root}/bad-overlay-evidence"
bad_overlay_evidence_summary="${bad_overlay_evidence_dir}/summary.txt"
bad_overlay_evidence_manifest="${bad_overlay_evidence_dir}/overlay-evidence-manifest.sha256"
bad_overlay_sanitized_resources="${bad_overlay_evidence_dir}/resources-sanitized.json"
bad_overlay_sanitized_secrets="${bad_overlay_evidence_dir}/secrets-sanitized.json"
rm -rf "${bad_overlay_evidence_dir}"
cp -R "${production_overlay_evidence_dir}" "${bad_overlay_evidence_dir}"
cat > "${bad_overlay_sanitized_secrets}" <<'JSON'
{
  "items": [
    {
      "metadata": { "name": "crest-db-secret" },
      "data": { "CREST_DB_PASSWORD": "c2VjcmV0" },
      "sanitizedData": {
        "CREST_DB_PASSWORD": { "present": true, "decodedLength": 6 }
      }
    },
    {
      "metadata": { "name": "crest-redis-secret" },
      "sanitizedData": {
        "CREST_REDIS_PASSWORD": { "present": true, "decodedLength": 32 }
      }
    }
  ]
}
JSON
sed "s|^sanitized_secrets_sha256=.*|sanitized_secrets_sha256=$(sha256_file "${bad_overlay_sanitized_secrets}")|" \
  "${bad_overlay_evidence_summary}" > "${bad_overlay_evidence_summary}.tmp"
mv "${bad_overlay_evidence_summary}.tmp" "${bad_overlay_evidence_summary}"
write_overlay_evidence_manifest_for_dir "${bad_overlay_evidence_dir}" "${bad_overlay_evidence_manifest}"
bad_overlay_summary="${test_root}/bad-overlay-evidence.txt"
cat > "${bad_overlay_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^production_overlay_evidence_dir=.*|production_overlay_evidence_dir=${bad_overlay_evidence_dir}|" \
  -e "s|^production_overlay_evidence_summary=.*|production_overlay_evidence_summary=${bad_overlay_evidence_summary}|" \
  -e "s|^production_overlay_evidence_summary_sha256=.*|production_overlay_evidence_summary_sha256=$(sha256_file "${bad_overlay_evidence_summary}")|" \
  -e "s|^production_overlay_evidence_manifest=.*|production_overlay_evidence_manifest=${bad_overlay_evidence_manifest}|" \
  -e "s|^production_overlay_evidence_manifest_sha256=.*|production_overlay_evidence_manifest_sha256=$(sha256_file "${bad_overlay_evidence_manifest}")|" \
  -e "s|^production_overlay_sanitized_resources=.*|production_overlay_sanitized_resources=${bad_overlay_sanitized_resources}|" \
  -e "s|^production_overlay_sanitized_resources_sha256=.*|production_overlay_sanitized_resources_sha256=$(sha256_file "${bad_overlay_sanitized_resources}")|" \
  -e "s|^production_overlay_sanitized_secrets=.*|production_overlay_sanitized_secrets=${bad_overlay_sanitized_secrets}|" \
  -e "s|^production_overlay_sanitized_secrets_sha256=.*|production_overlay_sanitized_secrets_sha256=$(sha256_file "${bad_overlay_sanitized_secrets}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF
bad_overlay_log="${test_root}/bad-overlay-evidence.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_overlay_summary}" >"${bad_overlay_log}" 2>&1; then
  fail "Go/No-Go summary with raw Secret data in overlay evidence should fail"
fi

grep -q 'crest-db-secret must not contain Kubernetes Secret data' "${bad_overlay_log}" \
  || fail "bad overlay evidence failure must explain raw Secret data leakage"

bad_external_file_list_summary="${test_root}/bad-external-evidence-file-list-summary.txt"
sed 's/^evidence_file_6=credential-rotation.md/evidence_file_6=redis-failover.md/' \
  "${external_evidence_summary}" > "${bad_external_file_list_summary}"
bad_external_file_list_go_summary="${test_root}/bad-external-evidence-file-list.txt"
cat > "${bad_external_file_list_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_file_list_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_file_list_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_file_list_log="${test_root}/bad-external-evidence-file-list.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_file_list_go_summary}" >"${bad_external_file_list_log}" 2>&1; then
  fail "Go/No-Go summary with an incomplete external evidence file list should fail"
fi

grep -q 'external evidence summary evidence_file_6 must be credential-rotation.md' "${bad_external_file_list_log}" \
  || fail "bad external evidence file list failure must explain the missing credential evidence"

bad_external_file_digest_summary="${test_root}/bad-external-evidence-file-digest-summary.txt"
sed 's/^evidence_file_6_sha256=.*/evidence_file_6_sha256=0000000000000000000000000000000000000000000000000000000000000000/' \
  "${external_evidence_summary}" > "${bad_external_file_digest_summary}"
bad_external_file_digest_go_summary="${test_root}/bad-external-evidence-file-digest.txt"
cat > "${bad_external_file_digest_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_file_digest_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_file_digest_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_file_digest_log="${test_root}/bad-external-evidence-file-digest.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_file_digest_go_summary}" >"${bad_external_file_digest_log}" 2>&1; then
  fail "Go/No-Go summary with a mismatched external evidence file digest should fail"
fi

grep -q 'external evidence summary digest mismatch for credential-rotation.md' "${bad_external_file_digest_log}" \
  || fail "bad external evidence file digest failure must explain the digest mismatch"

bad_external_redis_single_node_dir="${test_root}/bad-external-redis-single-node"
bad_external_redis_single_node_summary="${test_root}/bad-external-redis-single-node-summary.txt"
bad_external_redis_single_node_report="${test_root}/bad-external-redis-single-node-report.txt"
cp -R "${external_evidence_dir}" "${bad_external_redis_single_node_dir}"
cat > "${bad_external_redis_single_node_report}" <<EOF
status=passed
redis_cluster_nodes_count=1
redis_node=redis-0:6379
redis_key_prefix={ops01-prod-crest-core}:prod
redis_hash_tag=ops01-prod-crest-core
redis_acl_user=crest-prod
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF
sed \
  -e "s|^redis_namespace_check_report:.*|redis_namespace_check_report: ${bad_external_redis_single_node_report}|" \
  -e "s|^redis_namespace_check_report_sha256:.*|redis_namespace_check_report_sha256: $(sha256_file "${bad_external_redis_single_node_report}")|" \
  "${bad_external_redis_single_node_dir}/redis-cluster.md" > "${bad_external_redis_single_node_dir}/redis-cluster.md.tmp"
mv "${bad_external_redis_single_node_dir}/redis-cluster.md.tmp" "${bad_external_redis_single_node_dir}/redis-cluster.md"
write_external_evidence_summary "${bad_external_redis_single_node_dir}" "${bad_external_redis_single_node_summary}"
bad_external_redis_single_node_go_summary="${test_root}/bad-external-redis-single-node.txt"
cat > "${bad_external_redis_single_node_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_redis_single_node_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_redis_single_node_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_redis_single_node_log="${test_root}/bad-external-redis-single-node.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_redis_single_node_go_summary}" >"${bad_external_redis_single_node_log}" 2>&1; then
  fail "Go/No-Go summary with a single-node Redis namespace report should fail"
fi

grep -q 'redis namespace check report must prove at least 3 Redis Cluster nodes' "${bad_external_redis_single_node_log}" \
  || fail "single-node Redis namespace report failure must explain the cluster size problem"

bad_external_future_date_dir="${test_root}/bad-external-future-date"
bad_external_future_date_summary="${test_root}/bad-external-future-date-summary.txt"
cp -R "${external_evidence_dir}" "${bad_external_future_date_dir}"
sed 's/^evidence_date:.*/evidence_date: 2026-06-30/' \
  "${bad_external_future_date_dir}/business-smoke.md" > "${bad_external_future_date_dir}/business-smoke.md.tmp"
mv "${bad_external_future_date_dir}/business-smoke.md.tmp" "${bad_external_future_date_dir}/business-smoke.md"
write_external_evidence_summary "${bad_external_future_date_dir}" "${bad_external_future_date_summary}"
bad_external_future_date_go_summary="${test_root}/bad-external-future-date.txt"
cat > "${bad_external_future_date_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_future_date_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_future_date_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_future_date_log="${test_root}/bad-external-future-date.log"
if CREST_EVIDENCE_TODAY=2026-06-29 \
  bash scripts/production-go-no-go-summary-check.sh "${bad_external_future_date_go_summary}" >"${bad_external_future_date_log}" 2>&1; then
  fail "Go/No-Go summary with future external evidence_date should fail"
fi

grep -q 'business-smoke.md evidence_date must not be in the future' "${bad_external_future_date_log}" \
  || fail "future external evidence date failure must explain the date problem"

bad_external_missing_smoke_scope_dir="${test_root}/bad-external-missing-smoke-scope"
bad_external_missing_smoke_scope_summary="${test_root}/bad-external-missing-smoke-scope-summary.txt"
cp -R "${external_evidence_dir}" "${bad_external_missing_smoke_scope_dir}"
sed '/^smoke_scope:/d' \
  "${bad_external_missing_smoke_scope_dir}/business-smoke.md" > "${bad_external_missing_smoke_scope_dir}/business-smoke.md.tmp"
mv "${bad_external_missing_smoke_scope_dir}/business-smoke.md.tmp" "${bad_external_missing_smoke_scope_dir}/business-smoke.md"
write_external_evidence_summary "${bad_external_missing_smoke_scope_dir}" "${bad_external_missing_smoke_scope_summary}"
bad_external_missing_smoke_scope_go_summary="${test_root}/bad-external-missing-smoke-scope.txt"
cat > "${bad_external_missing_smoke_scope_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_missing_smoke_scope_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_missing_smoke_scope_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_missing_smoke_scope_log="${test_root}/bad-external-missing-smoke-scope.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_missing_smoke_scope_go_summary}" >"${bad_external_missing_smoke_scope_log}" 2>&1; then
  fail "Go/No-Go summary with incomplete business-smoke evidence body should fail"
fi

grep -q 'business-smoke.md must include a non-empty smoke_scope: field' "${bad_external_missing_smoke_scope_log}" \
  || fail "missing business-smoke scope failure must explain the missing evidence field"

bad_external_placeholder_dir="${test_root}/bad-external-placeholder"
bad_external_placeholder_summary="${test_root}/bad-external-placeholder-summary.txt"
cp -R "${external_evidence_dir}" "${bad_external_placeholder_dir}"
sed 's/^notes:.*/notes: CHANGE_ME/' \
  "${bad_external_placeholder_dir}/failure-drill.md" > "${bad_external_placeholder_dir}/failure-drill.md.tmp"
mv "${bad_external_placeholder_dir}/failure-drill.md.tmp" "${bad_external_placeholder_dir}/failure-drill.md"
write_external_evidence_summary "${bad_external_placeholder_dir}" "${bad_external_placeholder_summary}"
bad_external_placeholder_go_summary="${test_root}/bad-external-placeholder.txt"
cat > "${bad_external_placeholder_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_placeholder_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_placeholder_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_placeholder_log="${test_root}/bad-external-placeholder.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_placeholder_go_summary}" >"${bad_external_placeholder_log}" 2>&1; then
  fail "Go/No-Go summary with placeholder external evidence body should fail"
fi

grep -q 'failure-drill.md still contains placeholder text' "${bad_external_placeholder_log}" \
  || fail "placeholder external evidence failure must explain the placeholder"

bad_external_redis_acl_dir="${test_root}/bad-external-redis-acl"
bad_external_redis_acl_summary="${test_root}/bad-external-redis-acl-summary.txt"
cp -R "${external_evidence_dir}" "${bad_external_redis_acl_dir}"
sed '/^redis_acl_channel_isolation=/d' \
  "${bad_external_redis_acl_dir}/redis-namespace-check.txt" > "${bad_external_redis_acl_dir}/redis-namespace-check.txt.tmp"
mv "${bad_external_redis_acl_dir}/redis-namespace-check.txt.tmp" "${bad_external_redis_acl_dir}/redis-namespace-check.txt"
sed \
  -e "s|${redis_namespace_report}|${bad_external_redis_acl_dir}/redis-namespace-check.txt|" \
  -e "s|^redis_namespace_check_report_sha256:.*|redis_namespace_check_report_sha256: $(sha256_file "${bad_external_redis_acl_dir}/redis-namespace-check.txt")|" \
  "${bad_external_redis_acl_dir}/redis-cluster.md" > "${bad_external_redis_acl_dir}/redis-cluster.md.tmp"
mv "${bad_external_redis_acl_dir}/redis-cluster.md.tmp" "${bad_external_redis_acl_dir}/redis-cluster.md"
write_external_evidence_summary "${bad_external_redis_acl_dir}" "${bad_external_redis_acl_summary}"
bad_external_redis_acl_go_summary="${test_root}/bad-external-redis-acl.txt"
cat > "${bad_external_redis_acl_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_redis_acl_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_redis_acl_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_redis_acl_log="${test_root}/bad-external-redis-acl.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_redis_acl_go_summary}" >"${bad_external_redis_acl_log}" 2>&1; then
  fail "Go/No-Go summary with incomplete Redis ACL evidence should fail"
fi

grep -q 'redis namespace check report must contain redis_acl_channel_isolation=passed' "${bad_external_redis_acl_log}" \
  || fail "bad Redis ACL evidence failure must explain the missing channel isolation"

bad_external_redis_report_digest_dir="${test_root}/bad-external-redis-report-digest"
bad_external_redis_report_digest_summary="${test_root}/bad-external-redis-report-digest-summary.txt"
cp -R "${external_evidence_dir}" "${bad_external_redis_report_digest_dir}"
sed 's/^redis_namespace_check_report_sha256:.*/redis_namespace_check_report_sha256: 0000000000000000000000000000000000000000000000000000000000000000/' \
  "${bad_external_redis_report_digest_dir}/redis-cluster.md" > "${bad_external_redis_report_digest_dir}/redis-cluster.md.tmp"
mv "${bad_external_redis_report_digest_dir}/redis-cluster.md.tmp" "${bad_external_redis_report_digest_dir}/redis-cluster.md"
write_external_evidence_summary "${bad_external_redis_report_digest_dir}" "${bad_external_redis_report_digest_summary}"
bad_external_redis_report_digest_go_summary="${test_root}/bad-external-redis-report-digest.txt"
cat > "${bad_external_redis_report_digest_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_redis_report_digest_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_redis_report_digest_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_redis_report_digest_log="${test_root}/bad-external-redis-report-digest.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_redis_report_digest_go_summary}" >"${bad_external_redis_report_digest_log}" 2>&1; then
  fail "Go/No-Go summary with mismatched Redis namespace report digest should fail"
fi

grep -q 'redis-cluster.md redis_namespace_check_report_sha256 must match redis_namespace_check_report' "${bad_external_redis_report_digest_log}" \
  || fail "bad Redis namespace report digest failure must explain the digest mismatch"

bad_external_redis_user_dir="${test_root}/bad-external-redis-user"
bad_external_redis_user_summary="${test_root}/bad-external-redis-user-summary.txt"
cp -R "${external_evidence_dir}" "${bad_external_redis_user_dir}"
cat > "${bad_external_redis_user_dir}/redis-namespace-check.txt" <<EOF
status=passed
redis_cluster_nodes_count=3
redis_node=redis-0:6379
redis_key_prefix={ops01-prod-crest-core}:prod
redis_hash_tag=ops01-prod-crest-core
redis_acl_user=production
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF
cat > "${bad_external_redis_user_dir}/redis-cluster.md" <<EOF
status: passed
environment: go-no-go-test
evidence_date: 2026-06-28
owner: platform
artifact_reference: redis-user-ticket
notes: generic Redis ACL user evidence
redis_key_prefix: {ops01-prod-crest-core}:prod
redis_hash_tag: ops01-prod-crest-core
redis_acl_user: production
redis_namespace_check_report: ${bad_external_redis_user_dir}/redis-namespace-check.txt
EOF
cat >> "${bad_external_redis_user_dir}/redis-cluster.md" <<EOF
redis_namespace_check_report_sha256: $(sha256_file "${bad_external_redis_user_dir}/redis-namespace-check.txt")
EOF
write_external_evidence_summary "${bad_external_redis_user_dir}" "${bad_external_redis_user_summary}"
bad_external_redis_user_go_summary="${test_root}/bad-external-redis-user.txt"
cat > "${bad_external_redis_user_go_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^external_evidence_summary=.*|external_evidence_summary=${bad_external_redis_user_summary}|" \
  -e "s|^external_evidence_summary_sha256=.*|external_evidence_summary_sha256=$(sha256_file "${bad_external_redis_user_summary}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_external_redis_user_log="${test_root}/bad-external-redis-user.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_external_redis_user_go_summary}" >"${bad_external_redis_user_log}" 2>&1; then
  fail "Go/No-Go summary with generic Redis ACL user evidence should fail"
fi

grep -q 'redis-cluster.md redis_acl_user is too generic for shared Redis' "${bad_external_redis_user_log}" \
  || fail "bad Redis ACL user evidence failure must explain the generic ACL user"

bad_production_evidence_digest="${test_root}/bad-production-evidence-digest.txt"
cat > "${bad_production_evidence_digest}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed 's/^production_evidence_manifest_sha256=.*/production_evidence_manifest_sha256=0000000000000000000000000000000000000000000000000000000000000000/')
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_production_evidence_digest_log="${test_root}/bad-production-evidence-digest.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_production_evidence_digest}" >"${bad_production_evidence_digest_log}" 2>&1; then
  fail "Go/No-Go summary with a mismatched production evidence manifest digest should fail"
fi

grep -q 'production evidence manifest SHA-256 mismatch' "${bad_production_evidence_digest_log}" \
  || fail "bad production evidence digest failure must explain the SHA-256 mismatch"

bad_production_evidence_count_dir="${test_root}/evidence-bad-count"
rm -rf "${bad_production_evidence_count_dir}"
cp -R "${production_evidence_dir}" "${bad_production_evidence_count_dir}"
bad_production_evidence_count_summary="${bad_production_evidence_count_dir}/summary.txt"
bad_production_evidence_count_manifest="${bad_production_evidence_count_dir}/evidence-manifest.sha256"
sed 's/^evidence_file_count=.*/evidence_file_count=8/' \
  "${production_evidence_summary}" > "${bad_production_evidence_count_summary}"
write_evidence_manifest_for_dir "${bad_production_evidence_count_dir}" "${bad_production_evidence_count_manifest}"

bad_production_evidence_count="${test_root}/bad-production-evidence-count.txt"
cat > "${bad_production_evidence_count}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^production_evidence_dir=.*|production_evidence_dir=${bad_production_evidence_count_dir}|" \
  -e "s|^production_evidence_summary=.*|production_evidence_summary=${bad_production_evidence_count_summary}|" \
  -e "s|^production_evidence_summary_sha256=.*|production_evidence_summary_sha256=$(sha256_file "${bad_production_evidence_count_summary}")|" \
  -e "s|^production_evidence_manifest=.*|production_evidence_manifest=${bad_production_evidence_count_manifest}|" \
  -e "s|^production_evidence_manifest_sha256=.*|production_evidence_manifest_sha256=$(sha256_file "${bad_production_evidence_count_manifest}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_production_evidence_count_log="${test_root}/bad-production-evidence-count.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_production_evidence_count}" >"${bad_production_evidence_count_log}" 2>&1; then
  fail "Go/No-Go summary with mismatched production evidence file count should fail"
fi

grep -q 'production evidence summary evidence_file_count must match production evidence manifest entry count: expected 7, got 8' "${bad_production_evidence_count_log}" \
  || fail "bad production evidence file count failure must explain the manifest count mismatch"

bad_clean_source_scan_digest="${test_root}/bad-clean-source-scan-digest.txt"
cat > "${bad_clean_source_scan_digest}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed 's/^clean_source_secret_scan_report_sha256=.*/clean_source_secret_scan_report_sha256=0000000000000000000000000000000000000000000000000000000000000000/')
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_clean_source_scan_digest_log="${test_root}/bad-clean-source-scan-digest.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_clean_source_scan_digest}" >"${bad_clean_source_scan_digest_log}" 2>&1; then
  fail "Go/No-Go summary with a mismatched clean-source scan report digest should fail"
fi

grep -q 'clean source secret scan report SHA-256 mismatch' "${bad_clean_source_scan_digest_log}" \
  || fail "bad clean-source scan digest failure must explain the SHA-256 mismatch"

bad_clean_source_findings_report="${test_root}/gitleaks-clean-source-with-findings.json"
cat > "${bad_clean_source_findings_report}" <<'JSON'
[
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml"}
]
JSON

bad_clean_source_findings_summary="${test_root}/bad-clean-source-scan-findings.txt"
cat > "${bad_clean_source_findings_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence 2 clean-source rotated-before-delivery false "${bad_clean_source_findings_report}")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_clean_source_findings_log="${test_root}/bad-clean-source-scan-findings.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_clean_source_findings_summary}" >"${bad_clean_source_findings_log}" 2>&1; then
  fail "Go/No-Go summary with a non-empty clean-source scan report should fail"
fi

grep -q 'clean source secret scan report must contain 0 findings' "${bad_clean_source_findings_log}" \
  || fail "bad clean-source scan findings failure must explain nonzero findings"

bad_history_count_report="${test_root}/gitleaks-history-count-mismatch.json"
cat > "${bad_history_count_report}" <<'JSON'
[
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml"}
]
JSON

bad_history_count_summary="${test_root}/bad-history-count.txt"
cat > "${bad_history_count_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence 2 clean-source rotated-before-delivery false "${clean_source_secret_scan_report}" "${bad_history_count_report}")
$(write_history_secret_evidence 2 clean-source rotated-before-delivery initial-admin-password,application-encryption-key "${bad_history_count_report}")
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_history_count_log="${test_root}/bad-history-count.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_history_count_summary}" >"${bad_history_count_log}" 2>&1; then
  fail "Go/No-Go summary with a mismatched history scan report count should fail"
fi

grep -q 'clean source history scan report finding count must match history_secret_findings_remaining' "${bad_history_count_log}" \
  || fail "bad history count failure must explain the finding count mismatch"

bad_security_report_dir="${test_root}/security-with-findings"
bad_security_report_manifest="${bad_security_report_dir}/security-report-manifest.sha256"
mkdir -p "${bad_security_report_dir}"
cp "${security_report_dir}/gitleaks.json" \
  "${security_report_dir}/maven-dependency-tree.txt" \
  "${security_report_dir}/crest-bom.json" \
  "${security_report_dir}/pnpm-audit.json" \
  "${security_report_dir}/frontend-licenses.json" \
  "${security_report_dir}/license-policy.txt" \
  "${security_report_dir}/osv-frontend.json" \
  "${security_report_dir}/osv-maven-sbom.json" \
  "${bad_security_report_dir}/"
cat > "${bad_security_report_dir}/semgrep.json" <<'JSON'
{"results":[{"check_id":"java.security.example","path":"core/core-backend/src/main/java/io/crest/Example.java"}]}
JSON
{
  echo "$(sha256_file "${bad_security_report_dir}/semgrep.json")  semgrep.json"
  echo "$(sha256_file "${bad_security_report_dir}/gitleaks.json")  gitleaks.json"
  echo "$(sha256_file "${bad_security_report_dir}/maven-dependency-tree.txt")  maven-dependency-tree.txt"
  echo "$(sha256_file "${bad_security_report_dir}/crest-bom.json")  crest-bom.json"
  echo "$(sha256_file "${bad_security_report_dir}/pnpm-audit.json")  pnpm-audit.json"
  echo "$(sha256_file "${bad_security_report_dir}/frontend-licenses.json")  frontend-licenses.json"
  echo "$(sha256_file "${bad_security_report_dir}/license-policy.txt")  license-policy.txt"
  echo "$(sha256_file "${bad_security_report_dir}/osv-frontend.json")  osv-frontend.json"
  echo "$(sha256_file "${bad_security_report_dir}/osv-maven-sbom.json")  osv-maven-sbom.json"
} > "${bad_security_report_manifest}"

bad_security_report_summary="${test_root}/bad-security-report.txt"
cat > "${bad_security_report_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^security_report_dir=.*|security_report_dir=${bad_security_report_dir}|" \
  -e "s|^security_report_manifest=.*|security_report_manifest=${bad_security_report_manifest}|" \
  -e "s|^security_report_manifest_sha256=.*|security_report_manifest_sha256=$(sha256_file "${bad_security_report_manifest}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_security_report_log="${test_root}/bad-security-report.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_security_report_summary}" >"${bad_security_report_log}" 2>&1; then
  fail "Go/No-Go summary with nonzero SAST report findings should fail"
fi

grep -q 'Semgrep findings must be 0' "${bad_security_report_log}" \
  || fail "bad security report failure must explain Semgrep findings"

bad_container_report_dir="${test_root}/container-with-findings"
bad_container_report_manifest="${bad_container_report_dir}/container-report-manifest.sha256"
mkdir -p "${bad_container_report_dir}"
cp "${container_report_dir}/trivy-frontend.json" "${bad_container_report_dir}/"
cat > "${bad_container_report_dir}/trivy-backend.json" <<'JSON'
{
  "ArtifactType":"container_image",
  "ArtifactName":"crest-service:1.0.0",
  "Results":[{"Vulnerabilities":[{"VulnerabilityID":"CVE-2099-0001","Severity":"CRITICAL"}]}]
}
JSON
{
  echo "$(sha256_file "${bad_container_report_dir}/trivy-backend.json")  trivy-backend.json"
  echo "$(sha256_file "${bad_container_report_dir}/trivy-frontend.json")  trivy-frontend.json"
} > "${bad_container_report_manifest}"

bad_container_report_summary="${test_root}/bad-container-report.txt"
cat > "${bad_container_report_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^container_report_dir=.*|container_report_dir=${bad_container_report_dir}|" \
  -e "s|^container_report_manifest=.*|container_report_manifest=${bad_container_report_manifest}|" \
  -e "s|^container_report_manifest_sha256=.*|container_report_manifest_sha256=$(sha256_file "${bad_container_report_manifest}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_container_report_log="${test_root}/bad-container-report.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_container_report_summary}" >"${bad_container_report_log}" 2>&1; then
  fail "Go/No-Go summary with blocked container vulnerabilities should fail"
fi

grep -q 'blocked vulnerabilities' "${bad_container_report_log}" \
  || fail "bad container report failure must explain blocked vulnerabilities"

bad_container_coverage_dir="${test_root}/container-missing-deployed-image"
bad_container_coverage_manifest="${bad_container_coverage_dir}/container-report-manifest.sha256"
mkdir -p "${bad_container_coverage_dir}"
cp "${container_report_dir}/trivy-frontend.json" "${bad_container_coverage_dir}/"
cat > "${bad_container_coverage_dir}/trivy-backend.json" <<'JSON'
{"ArtifactType":"container_image","ArtifactName":"registry.example.internal/other-service:v1.0.0","Results":[]}
JSON
{
  echo "$(sha256_file "${bad_container_coverage_dir}/trivy-backend.json")  trivy-backend.json"
  echo "$(sha256_file "${bad_container_coverage_dir}/trivy-frontend.json")  trivy-frontend.json"
} > "${bad_container_coverage_manifest}"

bad_container_coverage_summary="${test_root}/bad-container-coverage.txt"
cat > "${bad_container_coverage_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^container_report_dir=.*|container_report_dir=${bad_container_coverage_dir}|" \
  -e "s|^container_report_manifest=.*|container_report_manifest=${bad_container_coverage_manifest}|" \
  -e "s|^container_report_manifest_sha256=.*|container_report_manifest_sha256=$(sha256_file "${bad_container_coverage_manifest}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_container_coverage_log="${test_root}/bad-container-coverage.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_container_coverage_summary}" >"${bad_container_coverage_log}" 2>&1; then
  fail "Go/No-Go summary with unscanned deployed images should fail"
fi

grep -q 'deployed images missing Trivy scan coverage: registry.example.internal/crest-core-service:v1.0.0' "${bad_container_coverage_log}" \
  || fail "bad container coverage failure must explain the unscanned deployed image"

bad_clean_summary_internal_file="${test_root}/crest-core-1.0.0-source-bad-internal.summary.txt"
cat > "${bad_clean_summary_internal_file}" <<EOF
Crest Core clean source release summary
generated_at_utc=${clean_source_generated_at_utc}
version=${clean_source_version}
source_branch=${clean_source_source_branch}
source_commit=${clean_source_source_commit}
archive=${clean_source_archive_file}
archive_sha256=$(sha256_file "${clean_source_archive_file}")
source_file_count=${clean_source_source_file_count}
secret_scan_report=${clean_source_secret_scan_report}
secret_scan_findings=1
source_worktree_dirty=false
history_scan_report=${clean_source_history_scan_report}
history_scan_report_sha256=$(sha256_file "${clean_source_history_scan_report}")
history_findings_remaining=2
history_delivery_path=clean-source
credential_rotation_status=rotated-before-delivery
affected_credential_classes=initial-admin-password,application-encryption-key
EOF

bad_clean_summary_internal="${test_root}/bad-clean-source-summary-internal.txt"
cat > "${bad_clean_summary_internal}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^clean_source_summary=.*|clean_source_summary=${bad_clean_summary_internal_file}|" \
  -e "s|^clean_source_summary_sha256=.*|clean_source_summary_sha256=$(sha256_file "${bad_clean_summary_internal_file}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_clean_summary_internal_log="${test_root}/bad-clean-source-summary-internal.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_clean_summary_internal}" >"${bad_clean_summary_internal_log}" 2>&1; then
  fail "Go/No-Go summary with mismatched clean-source summary internals should fail"
fi

grep -q 'clean source secret scan finding count mismatch' "${bad_clean_summary_internal_log}" \
  || fail "bad clean-source summary internals failure must explain the mismatch"

bad_clean_summary_history_digest_file="${test_root}/crest-core-1.0.0-source-bad-history-digest.summary.txt"
cat > "${bad_clean_summary_history_digest_file}" <<EOF
Crest Core clean source release summary
generated_at_utc=${clean_source_generated_at_utc}
version=${clean_source_version}
source_branch=${clean_source_source_branch}
source_commit=${clean_source_source_commit}
archive=${clean_source_archive_file}
archive_sha256=$(sha256_file "${clean_source_archive_file}")
source_file_count=${clean_source_source_file_count}
secret_scan_report=${clean_source_secret_scan_report}
secret_scan_findings=0
source_worktree_dirty=false
history_scan_report=${clean_source_history_scan_report}
history_scan_report_sha256=0000000000000000000000000000000000000000000000000000000000000000
history_findings_remaining=2
history_delivery_path=clean-source
credential_rotation_status=rotated-before-delivery
affected_credential_classes=initial-admin-password,application-encryption-key
EOF

bad_clean_summary_history_digest="${test_root}/bad-clean-source-summary-history-digest.txt"
cat > "${bad_clean_summary_history_digest}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence | sed \
  -e "s|^clean_source_summary=.*|clean_source_summary=${bad_clean_summary_history_digest_file}|" \
  -e "s|^clean_source_summary_sha256=.*|clean_source_summary_sha256=$(sha256_file "${bad_clean_summary_history_digest_file}")|")
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_clean_summary_history_digest_log="${test_root}/bad-clean-source-summary-history-digest.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_clean_summary_history_digest}" >"${bad_clean_summary_history_digest_log}" 2>&1; then
  fail "Go/No-Go summary with a mismatched clean-source history scan digest should fail"
fi

grep -q 'clean source history scan report digest mismatch' "${bad_clean_summary_history_digest_log}" \
  || fail "bad clean-source history digest failure must explain the mismatch"

dirty_clean_source_summary="${test_root}/dirty-clean-source.txt"
cat > "${dirty_clean_source_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence 2 clean-source rotated-before-delivery true)
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

dirty_clean_source_log="${test_root}/dirty-clean-source.log"
if bash scripts/production-go-no-go-summary-check.sh "${dirty_clean_source_summary}" >"${dirty_clean_source_log}" 2>&1; then
  fail "Go/No-Go summary with dirty clean-source export should fail"
fi

grep -q 'clean_source_worktree_dirty=false' "${dirty_clean_source_log}" \
  || fail "dirty clean-source failure must explain clean_source_worktree_dirty=false"

missing_history_evidence_summary="${test_root}/missing-history-evidence.txt"
cat > "${missing_history_evidence_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

missing_history_evidence_log="${test_root}/missing-history-evidence.log"
if bash scripts/production-go-no-go-summary-check.sh "${missing_history_evidence_summary}" >"${missing_history_evidence_log}" 2>&1; then
  fail "Go/No-Go summary without history secret remediation evidence should fail"
fi

grep -q 'history_secret_scan_report path' "${missing_history_evidence_log}" \
  || fail "missing history evidence failure must explain the required evidence"

missing_rotation_id_summary="${test_root}/missing-rotation-id.txt"
cat > "${missing_rotation_id_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence)
$(write_history_secret_evidence | sed '/^history_secret_rotation_evidence_id=/d')
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

missing_rotation_id_log="${test_root}/missing-rotation-id.log"
if bash scripts/production-go-no-go-summary-check.sh "${missing_rotation_id_summary}" >"${missing_rotation_id_log}" 2>&1; then
  fail "Go/No-Go summary without history rotation evidence id should fail"
fi

grep -q 'history_secret_rotation_evidence_id must be recorded' "${missing_rotation_id_log}" \
  || fail "missing rotation evidence id failure must explain the required evidence"

bad_clean_history_summary="${test_root}/bad-clean-history.txt"
cat > "${bad_clean_history_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence)
$(write_history_secret_evidence 2 clean-history rotated-before-delivery)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

bad_clean_history_log="${test_root}/bad-clean-history.log"
if bash scripts/production-go-no-go-summary-check.sh "${bad_clean_history_summary}" >"${bad_clean_history_log}" 2>&1; then
  fail "clean-history Go/No-Go summary with remaining history findings should fail"
fi

grep -q 'clean-history delivery requires history_secret_findings_remaining=0' "${bad_clean_history_log}" \
  || fail "bad clean-history failure must explain remaining history findings"

mismatched_clean_source_summary="${test_root}/mismatched-clean-source-history.txt"
cat > "${mismatched_clean_source_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence 2 clean-source rotated-before-delivery)
$(write_history_secret_evidence 0 clean-history not-applicable-clean-history)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

mismatched_clean_source_log="${test_root}/mismatched-clean-source-history.log"
if bash scripts/production-go-no-go-summary-check.sh "${mismatched_clean_source_summary}" >"${mismatched_clean_source_log}" 2>&1; then
  fail "Go/No-Go summary with mismatched clean-source and external history evidence should fail"
fi

grep -q 'clean_source_history_findings_remaining must match history_secret_findings_remaining' "${mismatched_clean_source_log}" \
  || fail "mismatched clean-source history failure must explain the evidence mismatch"

candidate_summary="${test_root}/candidate.txt"
cat > "${candidate_summary}" <<'EOF'
Crest Core enterprise readiness check
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
readiness_status=production-candidate-passed
production_release_status=not-ready
production_release_blocker=live preprod/production runtime check was not run
EOF

candidate_log="${test_root}/candidate.log"
if bash scripts/production-go-no-go-summary-check.sh "${candidate_summary}" >"${candidate_log}" 2>&1; then
  fail "candidate readiness summary should not satisfy Go/No-Go"
fi

grep -q 'readiness_status must be go-no-go-passed' "${candidate_log}" \
  || fail "candidate failure must explain readiness_status"

blocked_summary="${test_root}/blocked-go-no-go.txt"
cat > "${blocked_summary}" <<EOF
Crest Core enterprise readiness check
create_clean_source=true
require_clean_release_source=true
clean_source_require_credential_rotation=true
collect_evidence=true
require_go_no_go=true
check_external_evidence=true
$(write_artifact_evidence)
$(write_history_secret_evidence)
github-actions-policy: passed
ci-toolchain-policy: passed
quality: passed
security: passed
docker-environment: passed
docker-build: passed
container-scan: passed
kind-smoke: passed
clean-source-release: passed
production-overlay-render: passed (.local/production-overlay)
production-evidence-bundle: passed
external-production-evidence: passed
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
production_release_blocker=unexpected remaining blocker
EOF

blocked_log="${test_root}/blocked.log"
if bash scripts/production-go-no-go-summary-check.sh "${blocked_summary}" >"${blocked_log}" 2>&1; then
  fail "Go/No-Go summary with blockers should fail"
fi

grep -q 'production release blockers' "${blocked_log}" \
  || fail "blocked failure must explain release blockers"

forged_summary="${test_root}/forged-minimal-go-no-go.txt"
cat > "${forged_summary}" <<'EOF'
Crest Core enterprise readiness check
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF

forged_log="${test_root}/forged.log"
if bash scripts/production-go-no-go-summary-check.sh "${forged_summary}" >"${forged_log}" 2>&1; then
  fail "minimal forged Go/No-Go summary should not pass without gate evidence"
fi

grep -q 'require_go_no_go=true' "${forged_log}" \
  || fail "forged failure must explain the missing Go/No-Go mode evidence"

echo "test-production-go-no-go-summary-check: passed"
