#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-prepare-production-external-evidence: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_PREPARE_EXTERNAL_EVIDENCE_DIR:-.local/prepare-external-evidence-test-$$}"
evidence_dir="${test_root}/external-evidence"
redis_namespace_report="${test_root}/redis-namespace-check.txt"
history_report="${test_root}/gitleaks-history.json"

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

rm -rf "${test_root}"
mkdir -p "${test_root}"

if CREST_EXTERNAL_EVIDENCE_DIR="." \
  bash scripts/prepare-production-external-evidence.sh >"${test_root}/unsafe-dir.log" 2>&1; then
  fail "prepare-production-external-evidence unexpectedly accepted the repository root as evidence dir"
fi
grep -q "CREST_EXTERNAL_EVIDENCE_DIR is too broad to overwrite" "${test_root}/unsafe-dir.log" \
  || fail "unsafe evidence dir failure must explain the overwrite risk"

outside_parent="/tmp/crest-prepare-external-evidence-missing-parent-$$"
rm -rf "${outside_parent}"
if CREST_EXTERNAL_EVIDENCE_DIR="${outside_parent}/external-evidence" \
  bash scripts/prepare-production-external-evidence.sh >"${test_root}/outside-dir.log" 2>&1; then
  fail "prepare-production-external-evidence unexpectedly accepted an outside-repository evidence dir"
fi
grep -q "CREST_EXTERNAL_EVIDENCE_DIR must stay inside the repository" "${test_root}/outside-dir.log" \
  || fail "outside evidence dir failure message was not reported"
[[ ! -e "${outside_parent}" ]] \
  || fail "outside evidence dir check must not create directories before rejecting"

outside_symlink_target="/tmp/crest-prepare-external-evidence-symlink-target-$$"
rm -rf "${outside_symlink_target}"
mkdir -p "${outside_symlink_target}"
printf 'unchanged\n' > "${outside_symlink_target}/sentinel.txt"
ln -s "${outside_symlink_target}" "${test_root}/external-evidence-link"
if CREST_EXTERNAL_EVIDENCE_DIR="${test_root}/external-evidence-link" \
  bash scripts/prepare-production-external-evidence.sh >"${test_root}/symlink-dir.log" 2>&1; then
  fail "prepare-production-external-evidence unexpectedly accepted a symlink evidence dir"
fi
grep -q "CREST_EXTERNAL_EVIDENCE_DIR must not be a symlink" "${test_root}/symlink-dir.log" \
  || fail "symlink evidence dir failure message was not reported"
grep -q '^unchanged$' "${outside_symlink_target}/sentinel.txt" \
  || fail "symlink evidence dir target was modified before rejection"

cat > "${history_report}" <<'EOF'
[
  {"RuleID":"generic-api-key","File":"deploy/kubernetes/crest-ob-oracle-redis.yaml"},
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml"}
]
EOF

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

CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
CREST_HISTORY_SECRET_REPORT="${history_report}" \
CREST_REDIS_NAMESPACE_REPORT="${redis_namespace_report}" \
CREST_EVIDENCE_DATE="2026-06-28" \
CREST_EVIDENCE_ENVIRONMENT="preprod" \
  bash scripts/prepare-production-external-evidence.sh >"${test_root}/prepare.log"

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

for file in "${required_files[@]}"; do
  [[ -s "${evidence_dir}/${file}" ]] || fail "missing generated evidence file: ${file}"
  grep -qx "status: passed" "${evidence_dir}/${file}" \
    || fail "${file} must include the required status field"
  grep -qx "environment: preprod" "${evidence_dir}/${file}" \
    || fail "${file} must include the configured environment"
done

grep -qx "history_scan_report: ${history_report}" "${evidence_dir}/credential-rotation.md" \
  || fail "credential evidence must reference the history scan report"
grep -qx "history_scan_report_sha256: $(sha256_file "${history_report}")" "${evidence_dir}/credential-rotation.md" \
  || fail "credential evidence must include the history scan digest"
grep -qx "history_findings_remaining: 2" "${evidence_dir}/credential-rotation.md" \
  || fail "credential evidence must include the history finding count"
grep -qx "credential_rotation_status: CHANGE_ME_ROTATED_BEFORE_DELIVERY" "${evidence_dir}/credential-rotation.md" \
  || fail "credential evidence must keep rotation status as a human-confirmed placeholder"

grep -qx "redis_key_prefix: {ops01-prod-crest-core}:prod" "${evidence_dir}/redis-cluster.md" \
  || fail "Redis evidence must copy the checked key prefix"
grep -qx "redis_hash_tag: ops01-prod-crest-core" "${evidence_dir}/redis-cluster.md" \
  || fail "Redis evidence must copy the checked hash tag"
grep -qx "redis_namespace_check_report_sha256: $(sha256_file "${redis_namespace_report}")" "${evidence_dir}/redis-cluster.md" \
  || fail "Redis evidence must include the namespace report digest"

if CREST_EXTERNAL_EVIDENCE_DIR="${evidence_dir}" \
  CREST_EXTERNAL_EVIDENCE_SUMMARY="${test_root}/external-evidence-summary.txt" \
  bash scripts/production-external-evidence-check.sh >"${test_root}/external-evidence-check.log" 2>&1; then
  fail "draft evidence must not pass before CHANGE_ME fields are replaced"
fi

grep -q "still contains placeholder text" "${test_root}/external-evidence-check.log" \
  || fail "draft evidence failure must explain that placeholders remain"

echo "test-prepare-production-external-evidence: passed"
