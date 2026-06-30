#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-external-evidence-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

evidence_dir="${CREST_TEST_EXTERNAL_EVIDENCE_DIR:-.local/external-evidence-smoke-$$}"
summary_file="${evidence_dir}/summary.txt"
redis_namespace_report="${evidence_dir}/redis-namespace-check.txt"
history_report="${evidence_dir}/gitleaks-history.json"

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

rm -rf "${evidence_dir}"
mkdir -p "${evidence_dir}"

cat > "${redis_namespace_report}" <<EOF
status=passed
redis_cluster_nodes_count=3
redis_node=redis-0:6379
redis_key_prefix={ops01-prod-crest-core}:prod
redis_hash_tag=ops01-prod-crest-core
redis_acl_user=crest-smoke
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF

cat > "${history_report}" <<'EOF'
[
  {"RuleID":"generic-api-key","File":"deploy/kubernetes/crest-ob-oracle-redis.yaml"},
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml"}
]
EOF

required_files=(
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

write_evidence_file() {
  local file="$1"
  cat > "${evidence_dir}/${file}" <<EOF
status: passed
environment: smoke
evidence_date: 2026-06-28
owner: platform
artifact_reference: smoke-record
notes: sanitized smoke evidence for ${file}
EOF
  case "${file}" in
    credential-rotation.md)
      cat >> "${evidence_dir}/${file}" <<EOF
history_scan_report: ${history_report}
history_scan_report_sha256: $(sha256_file "${history_report}")
history_findings_remaining: 2
affected_credential_classes: initial-admin-password,application-encryption-key
credential_rotation_status: rotated-before-delivery
delivery_path: clean-source
rotation_evidence_id: SEC-12345
approved_by: platform-security
approval_date: 2026-06-28
EOF
      ;;
    redis-cluster.md)
      cat >> "${evidence_dir}/${file}" <<EOF
redis_key_prefix: {ops01-prod-crest-core}:prod
redis_hash_tag: ops01-prod-crest-core
redis_acl_user: crest-smoke
redis_namespace_check_report: ${redis_namespace_report}
redis_namespace_check_report_sha256: $(sha256_file "${redis_namespace_report}")
EOF
      ;;
    tls-ingress.md)
      cat >> "${evidence_dir}/${file}" <<EOF
ingress_host: crest-smoke.example.internal
tls_expiry_monitor: platform-cert-monitor
EOF
      ;;
    storage-rwx.md)
      cat >> "${evidence_dir}/${file}" <<EOF
pvc_name: crest-data
access_mode: ReadWriteMany
EOF
      ;;
    business-smoke.md)
      cat >> "${evidence_dir}/${file}" <<EOF
smoke_scope: login,dashboard,dataset-preview,export,async-task,websocket
EOF
      ;;
    failure-drill.md)
      cat >> "${evidence_dir}/${file}" <<EOF
drill_scope: rollout-restart,api-pod-delete,worker-pod-delete
EOF
      ;;
  esac
}

for file in "${required_files[@]}"; do
  write_evidence_file "${file}"
done

if CREST_EXTERNAL_EVIDENCE_DIR="." \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-unsafe-dir.out 2>&1; then
  fail "external evidence check unexpectedly accepted the repository root as evidence dir"
fi

if ! grep -q "CREST_EXTERNAL_EVIDENCE_DIR is too broad to read evidence from" /tmp/crest-external-evidence-unsafe-dir.out; then
  fail "unsafe external evidence dir failure message was not reported"
fi

outside_evidence_parent="/tmp/crest-external-evidence-missing-parent-$$"
rm -rf "${outside_evidence_parent}"
if CREST_EXTERNAL_EVIDENCE_DIR="${outside_evidence_parent}/external-evidence" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-outside-dir.out 2>&1; then
  fail "external evidence check unexpectedly accepted an outside-repository evidence dir"
fi

if ! grep -q "CREST_EXTERNAL_EVIDENCE_DIR must stay inside the repository" /tmp/crest-external-evidence-outside-dir.out; then
  fail "outside external evidence dir failure message was not reported"
fi

[[ ! -e "${outside_evidence_parent}" ]] \
  || fail "outside external evidence dir check must not create directories before rejecting"

outside_evidence_target="/tmp/crest-external-evidence-symlink-target-$$"
rm -rf "${outside_evidence_target}"
mkdir -p "${outside_evidence_target}"
ln -s "${outside_evidence_target}" "${evidence_dir}/outside-evidence-link"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}/outside-evidence-link" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-symlink-dir.out 2>&1; then
  fail "external evidence check unexpectedly accepted a symlink evidence dir"
fi

if ! grep -q "CREST_EXTERNAL_EVIDENCE_DIR must not be a symlink" /tmp/crest-external-evidence-symlink-dir.out; then
  fail "symlink external evidence dir failure message was not reported"
fi

if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="." \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-unsafe-summary.out 2>&1; then
  fail "external evidence check unexpectedly accepted the repository root as summary file"
fi

if ! grep -q "CREST_EXTERNAL_EVIDENCE_SUMMARY is too broad to overwrite" /tmp/crest-external-evidence-unsafe-summary.out; then
  fail "unsafe external evidence summary failure message was not reported"
fi

outside_summary_parent="/tmp/crest-external-evidence-summary-missing-parent-$$"
rm -rf "${outside_summary_parent}"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${outside_summary_parent}/summary.txt" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-outside-summary.out 2>&1; then
  fail "external evidence check unexpectedly accepted an outside-repository summary file"
fi

if ! grep -q "CREST_EXTERNAL_EVIDENCE_SUMMARY must stay inside the repository" /tmp/crest-external-evidence-outside-summary.out; then
  fail "outside external evidence summary failure message was not reported"
fi

[[ ! -e "${outside_summary_parent}" ]] \
  || fail "outside external evidence summary check must not create directories before rejecting"

outside_summary_target="/tmp/crest-external-evidence-summary-symlink-target-$$.txt"
rm -f "${outside_summary_target}"
printf 'unchanged\n' > "${outside_summary_target}"
ln -s "${outside_summary_target}" "${evidence_dir}/summary-link.txt"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${evidence_dir}/summary-link.txt" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-symlink-summary.out 2>&1; then
  fail "external evidence check unexpectedly accepted a symlink summary file"
fi

if ! grep -q "CREST_EXTERNAL_EVIDENCE_SUMMARY must not be a symlink" /tmp/crest-external-evidence-symlink-summary.out; then
  fail "symlink external evidence summary failure message was not reported"
fi
grep -q '^unchanged$' "${outside_summary_target}" \
  || fail "symlink external evidence summary target was modified before rejection"

CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/dev/null

if ! grep -q "failure-drill.md: present sha256=" "${summary_file}"; then
  fail "summary did not include all required evidence files"
fi

if ! grep -qx "status=passed" "${summary_file}"; then
  fail "summary did not record status=passed"
fi

if ! grep -qx "required_evidence_files=10" "${summary_file}"; then
  fail "summary did not record required evidence file count"
fi

if ! grep -qx "evidence_file_count=10" "${summary_file}"; then
  fail "summary did not record evidence file count"
fi

for index in "${!required_files[@]}"; do
  file="${required_files[${index}]}"
  field_index=$((index + 1))
  digest="$(sha256_file "${evidence_dir}/${file}")"
  if ! grep -qx "evidence_file_${field_index}=${file}" "${summary_file}"; then
    fail "summary did not record evidence_file_${field_index}=${file}"
  fi
  if ! grep -qx "evidence_file_${field_index}_sha256=${digest}" "${summary_file}"; then
    fail "summary did not record digest for ${file}"
  fi
done

printf 'status: passed\nnotes: CHANGE_ME\n' > "${evidence_dir}/redis-failover.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-negative.out 2>&1; then
  fail "placeholder evidence unexpectedly passed"
fi

if ! grep -q "redis-failover.md still contains placeholder text" /tmp/crest-external-evidence-negative.out; then
  fail "placeholder failure message was not reported"
fi

write_evidence_file redis-failover.md
cat > "${redis_namespace_report}" <<EOF
status=passed
redis_cluster_nodes_count=3
redis_node=redis-0:6379
redis_key_prefix={acme-crest-core-prod}:prod
redis_hash_tag=acme-crest-core-prod
redis_acl_user=crest-smoke
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF
cat > "${evidence_dir}/redis-cluster.md" <<EOF
status: passed
environment: smoke
evidence_date: 2026-06-28
owner: platform
artifact_reference: smoke-record
notes: copied example Redis evidence
redis_key_prefix: {acme-crest-core-prod}:prod
redis_hash_tag: acme-crest-core-prod
redis_acl_user: crest-smoke
redis_namespace_check_report: ${redis_namespace_report}
redis_namespace_check_report_sha256: $(sha256_file "${redis_namespace_report}")
EOF
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-redis-example.out 2>&1; then
  fail "copied Redis example evidence unexpectedly passed"
fi

if ! grep -q "redis_hash_tag looks like an example value" /tmp/crest-external-evidence-redis-example.out; then
  fail "Redis example evidence failure message was not reported"
fi

cat > "${redis_namespace_report}" <<EOF
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
cat > "${evidence_dir}/redis-cluster.md" <<EOF
status: passed
environment: smoke
evidence_date: 2026-06-28
owner: platform
artifact_reference: smoke-record
notes: generic Redis ACL user evidence
redis_key_prefix: {ops01-prod-crest-core}:prod
redis_hash_tag: ops01-prod-crest-core
redis_acl_user: production
redis_namespace_check_report: ${redis_namespace_report}
redis_namespace_check_report_sha256: $(sha256_file "${redis_namespace_report}")
EOF
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-redis-user-negative.out 2>&1; then
  fail "generic Redis ACL user evidence unexpectedly passed"
fi

if ! grep -q "redis-cluster.md redis_acl_user is too generic for shared Redis" /tmp/crest-external-evidence-redis-user-negative.out; then
  fail "generic Redis ACL user failure message was not reported"
fi

cat > "${redis_namespace_report}" <<EOF
status=passed
redis_cluster_nodes_count=3
redis_node=redis-0:6379
redis_key_prefix={ops01-prod-crest-core}:prod
redis_hash_tag=ops01-prod-crest-core
redis_acl_user=crest-smoke
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF
write_evidence_file redis-cluster.md
sed 's/^redis_namespace_check_report_sha256:.*/redis_namespace_check_report_sha256: 0000000000000000000000000000000000000000000000000000000000000000/' "${evidence_dir}/redis-cluster.md" > "${evidence_dir}/redis-cluster.md.tmp"
mv "${evidence_dir}/redis-cluster.md.tmp" "${evidence_dir}/redis-cluster.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-redis-sha-negative.out 2>&1; then
  fail "Redis evidence with mismatched namespace report digest unexpectedly passed"
fi

if ! grep -q "redis-cluster.md redis_namespace_check_report_sha256 must match redis_namespace_check_report" /tmp/crest-external-evidence-redis-sha-negative.out; then
  fail "mismatched Redis namespace report digest failure message was not reported"
fi

write_evidence_file redis-cluster.md
sed '/^redis_acl_channel_isolation=/d' "${redis_namespace_report}" > "${redis_namespace_report}.tmp"
mv "${redis_namespace_report}.tmp" "${redis_namespace_report}"
sed "s/^redis_namespace_check_report_sha256:.*/redis_namespace_check_report_sha256: $(sha256_file "${redis_namespace_report}")/" "${evidence_dir}/redis-cluster.md" > "${evidence_dir}/redis-cluster.md.tmp"
mv "${evidence_dir}/redis-cluster.md.tmp" "${evidence_dir}/redis-cluster.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-redis-acl-negative.out 2>&1; then
  fail "Redis evidence without channel ACL isolation unexpectedly passed"
fi

if ! grep -q "redis namespace check report must contain redis_acl_channel_isolation=passed" /tmp/crest-external-evidence-redis-acl-negative.out; then
  fail "missing Redis ACL isolation failure message was not reported"
fi

cat > "${redis_namespace_report}" <<EOF
status=passed
redis_cluster_nodes_count=1
redis_node=redis-0:6379
redis_key_prefix={ops01-prod-crest-core}:prod
redis_hash_tag=ops01-prod-crest-core
redis_acl_user=crest-smoke
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF
write_evidence_file redis-cluster.md
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-redis-node-count-negative.out 2>&1; then
  fail "Redis evidence with a single-node namespace report unexpectedly passed"
fi

if ! grep -q "redis namespace check report must prove at least 3 Redis Cluster nodes" /tmp/crest-external-evidence-redis-node-count-negative.out; then
  fail "single-node Redis namespace report failure message was not reported"
fi

cat > "${redis_namespace_report}" <<EOF
status=passed
redis_cluster_nodes_count=3
redis_node=redis-0:6379
redis_key_prefix={ops01-prod-crest-core}:prod
redis_hash_tag=ops01-prod-crest-core
redis_acl_user=crest-smoke
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed
EOF
write_evidence_file redis-cluster.md
printf 'status: passed\nenvironment: smoke\nevidence_date: 2026-06-28\nowner: platform\nartifact_reference: smoke-record\nnotes: missing credential fields\n' > "${evidence_dir}/credential-rotation.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-field-negative.out 2>&1; then
  fail "missing credential rotation fields unexpectedly passed"
fi

if ! grep -q "credential-rotation.md must include a non-empty history_scan_report: field" /tmp/crest-external-evidence-field-negative.out; then
  fail "missing credential rotation field failure message was not reported"
fi

write_evidence_file credential-rotation.md
cat > "${evidence_dir}/credential-rotation.md" <<EOF
status: passed
environment: smoke
evidence_date: 2026-06-28
owner: platform
artifact_reference: smoke-record
notes: clean history cannot keep remaining findings
history_scan_report: ${history_report}
history_scan_report_sha256: $(sha256_file "${history_report}")
history_findings_remaining: 2
affected_credential_classes: initial-admin-password,application-encryption-key
credential_rotation_status: rotated-before-delivery
delivery_path: clean-history
rotation_evidence_id: SEC-12345
approved_by: platform-security
approval_date: 2026-06-28
EOF
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-credential-negative.out 2>&1; then
  fail "clean-history evidence with remaining findings unexpectedly passed"
fi

if ! grep -q "credential-rotation.md clean-history delivery requires history_findings_remaining: 0" /tmp/crest-external-evidence-credential-negative.out; then
  fail "clean-history remaining findings failure message was not reported"
fi

write_evidence_file credential-rotation.md
sed 's/^history_scan_report_sha256:.*/history_scan_report_sha256: 0000000000000000000000000000000000000000000000000000000000000000/' "${evidence_dir}/credential-rotation.md" > "${evidence_dir}/credential-rotation.md.tmp"
mv "${evidence_dir}/credential-rotation.md.tmp" "${evidence_dir}/credential-rotation.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-history-sha-negative.out 2>&1; then
  fail "credential rotation evidence with mismatched history scan digest unexpectedly passed"
fi

if ! grep -q "credential-rotation.md history_scan_report_sha256 must match history_scan_report" /tmp/crest-external-evidence-history-sha-negative.out; then
  fail "mismatched history scan digest failure message was not reported"
fi

write_evidence_file credential-rotation.md
sed 's/^history_findings_remaining:.*/history_findings_remaining: 1/' "${evidence_dir}/credential-rotation.md" > "${evidence_dir}/credential-rotation.md.tmp"
mv "${evidence_dir}/credential-rotation.md.tmp" "${evidence_dir}/credential-rotation.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-history-count-negative.out 2>&1; then
  fail "credential rotation evidence with mismatched history finding count unexpectedly passed"
fi

if ! grep -q "credential-rotation.md history_findings_remaining must match history_scan_report finding count" /tmp/crest-external-evidence-history-count-negative.out; then
  fail "mismatched history finding count failure message was not reported"
fi

write_evidence_file credential-rotation.md
sed '/^rotation_evidence_id:/d' "${evidence_dir}/credential-rotation.md" > "${evidence_dir}/credential-rotation.md.tmp"
mv "${evidence_dir}/credential-rotation.md.tmp" "${evidence_dir}/credential-rotation.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-rotation-id-negative.out 2>&1; then
  fail "credential rotation evidence without rotation_evidence_id unexpectedly passed"
fi

if ! grep -q "credential-rotation.md must include a non-empty rotation_evidence_id: field" /tmp/crest-external-evidence-rotation-id-negative.out; then
  fail "missing rotation_evidence_id failure message was not reported"
fi

write_evidence_file credential-rotation.md
sed 's/^approval_date:.*/approval_date: 06-28-2026/' "${evidence_dir}/credential-rotation.md" > "${evidence_dir}/credential-rotation.md.tmp"
mv "${evidence_dir}/credential-rotation.md.tmp" "${evidence_dir}/credential-rotation.md"
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-approval-date-negative.out 2>&1; then
  fail "credential rotation evidence with invalid approval_date unexpectedly passed"
fi

if ! grep -q "credential-rotation.md approval_date must use YYYY-MM-DD" /tmp/crest-external-evidence-approval-date-negative.out; then
  fail "invalid approval_date failure message was not reported"
fi

write_evidence_file credential-rotation.md
sed 's/^evidence_date:.*/evidence_date: 2026-06-30/' "${evidence_dir}/credential-rotation.md" > "${evidence_dir}/credential-rotation.md.tmp"
mv "${evidence_dir}/credential-rotation.md.tmp" "${evidence_dir}/credential-rotation.md"
if CREST_EVIDENCE_TODAY=2026-06-29 \
  CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-future-date-negative.out 2>&1; then
  fail "credential rotation evidence with future evidence_date unexpectedly passed"
fi

if ! grep -q "credential-rotation.md evidence_date must not be in the future" /tmp/crest-external-evidence-future-date-negative.out; then
  fail "future evidence_date failure message was not reported"
fi

write_evidence_file credential-rotation.md
sed 's/^approval_date:.*/approval_date: 2026-06-29/' "${evidence_dir}/credential-rotation.md" > "${evidence_dir}/credential-rotation.md.tmp"
mv "${evidence_dir}/credential-rotation.md.tmp" "${evidence_dir}/credential-rotation.md"
if CREST_EVIDENCE_TODAY=2026-06-29 \
  CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-approval-after-evidence-negative.out 2>&1; then
  fail "credential rotation evidence with approval_date after evidence_date unexpectedly passed"
fi

if ! grep -q "credential-rotation.md approval_date must not be later than evidence_date" /tmp/crest-external-evidence-approval-after-evidence-negative.out; then
  fail "approval_date ordering failure message was not reported"
fi

write_evidence_file credential-rotation.md
cat > "${evidence_dir}/redis-cluster.md" <<EOF
status: passed
environment: smoke
evidence_date: 2026-06-28
owner: platform
artifact_reference: smoke-record
notes: generic redis namespace should fail
redis_key_prefix: {crest-core}:prod
redis_hash_tag: crest-core
redis_acl_user: crest-smoke
redis_namespace_check_report: ${redis_namespace_report}
redis_namespace_check_report_sha256: $(sha256_file "${redis_namespace_report}")
EOF
if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/tmp/crest-external-evidence-redis-negative.out 2>&1; then
  fail "generic Redis hash tag evidence unexpectedly passed"
fi

if ! grep -q "redis-cluster.md redis_hash_tag is too generic for shared Redis" /tmp/crest-external-evidence-redis-negative.out; then
  fail "generic Redis hash tag failure message was not reported"
fi

write_evidence_file redis-cluster.md
CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
CREST_EXTERNAL_EVIDENCE_SUMMARY="${summary_file}" \
  bash scripts/production-external-evidence-check.sh >/dev/null

echo "test-production-external-evidence-check: passed"
