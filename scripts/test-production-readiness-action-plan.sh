#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-readiness-action-plan: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_READINESS_ACTION_PLAN_DIR:-.local/readiness-action-plan-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}"

summary_file="${test_root}/enterprise-readiness-summary.txt"
action_plan="${test_root}/production-readiness-action-plan.txt"
docker_report="${test_root}/docker-environment-report.txt"
docker_cleanup_plan="${test_root}/docker-cleanup-plan.txt"
history_summary="${test_root}/gitleaks-history-summary.txt"
history_report="${test_root}/gitleaks-history.json"

cat > "${docker_report}" <<'EOF'
Crest Docker environment report
status=insufficient-disk
available_gib=8
required_gib=12
shortfall_gib=4
EOF
printf 'cleanup plan\n' > "${docker_cleanup_plan}"
cat > "${history_summary}" <<'EOF'
Crest Core history secret audit summary
status=findings
findings=2
commits=2
EOF
printf '[{"RuleID":"generic-api-key"}]\n' > "${history_report}"

cat > "${summary_file}" <<EOF
Crest Core enterprise readiness check
docker_environment_report=${docker_report}
docker_cleanup_plan=${docker_cleanup_plan}
history_secret_audit_summary=${history_summary}
history_secret_audit_report=${history_report}
history_secret_audit_findings=2
history_secret_audit_commits=2
docker-environment: failed
kind-smoke: passed
history-secret-audit: failed
readiness_status=failed
production_release_status=not-ready
production_release_blocker=Docker environment preflight failed
production_release_blocker=git history secret audit failed
production_release_blocker=production overlay render and strict config gate failed
production_release_blocker=clean source release export was not generated
production_release_blocker=clean source history finding count was not verified
production_release_blocker=clean source credential rotation evidence was not recorded
production_release_blocker=live preprod/production runtime check was not run
production_release_blocker=external production evidence was not checked
EOF

bash scripts/production-readiness-action-plan.sh "${summary_file}" "${action_plan}"

[[ -s "${action_plan}" ]] || fail "action plan was not written"
grep -q '^blocker_count=8$' "${action_plan}" \
  || fail "action plan must record blocker count"
grep -q '^action=Docker environment preflight failed$' "${action_plan}" \
  || fail "action plan must include Docker blocker"
grep -q '^docker_available_gib=8$' "${action_plan}" \
  || fail "action plan must include Docker available space"
grep -q '^docker_shortfall_gib=4$' "${action_plan}" \
  || fail "action plan must include Docker free-space shortfall"
grep -q '^action=git history secret audit failed$' "${action_plan}" \
  || fail "action plan must include history blocker"
grep -q '^history_findings=2$' "${action_plan}" \
  || fail "action plan must include history finding count"
grep -q 'history_scan_report_sha256' "${action_plan}" \
  || fail "action plan must mention credential-rotation history scan digest"
grep -q '^template=deploy/kubernetes/production.env.example$' "${action_plan}" \
  || fail "action plan must include production overlay env template path"
grep -q '^command=set -a; source .local/crest-production.env; set +a; CREST_READINESS_RENDER_OVERLAY=true bash scripts/enterprise-readiness-check.sh$' "${action_plan}" \
  || fail "action plan must include production overlay render command"
grep -q 'CREST_READINESS_COLLECT_EVIDENCE=true' "${action_plan}" \
  || fail "action plan must include live evidence command guidance"
grep -q '^action=clean source history finding count was not verified$' "${action_plan}" \
  || fail "action plan must include clean-source history count blocker"
grep -q 'CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT' "${action_plan}" \
  || fail "action plan must explain how to provide clean-source history scan evidence"
grep -q '^action=clean source credential rotation evidence was not recorded$' "${action_plan}" \
  || fail "action plan must include clean-source credential rotation blocker"
grep -q 'CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery' "${action_plan}" \
  || fail "action plan must explain clean-source credential rotation requirements"
grep -q 'CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true' "${action_plan}" \
  || fail "action plan must enforce clean-source credential rotation"
grep -q 'CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH=clean-source' "${action_plan}" \
  || fail "action plan must record clean-source history delivery path"
grep -q '^prepare=CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence bash scripts/prepare-production-external-evidence.sh$' "${action_plan}" \
  || fail "action plan must include external evidence preparation command"
grep -q "CREST_REDIS_KEY_PREFIX='{<org>-<env>-crest-core}:prod'" "${action_plan}" \
  || fail "action plan must include Redis hash tag namespace guidance"
grep -q "CREST_REDIS_USERNAME=<redis-acl-user>" "${action_plan}" \
  || fail "action plan must include Redis ACL user guidance"
for redis_scope in \
  CREST_REDIS_CACHE_KEY_PREFIX \
  CREST_LOCK_KEY_PREFIX \
  CREST_WEBSOCKET_BROADCAST_CHANNEL \
  CREST_EXPORT_TASK_STREAM \
  CREST_EXPORT_TASK_CONSUMER_GROUP \
  CREST_SYNC_TASK_STREAM \
  CREST_SYNC_TASK_CONSUMER_GROUP \
  CREST_DATASOURCE_SYNC_TASK_STREAM \
  CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP \
  CREST_SCHEDULED_TASK_STREAM \
  CREST_SCHEDULED_TASK_CONSUMER_GROUP; do
  grep -q "${redis_scope}=" "${action_plan}" \
    || fail "action plan must include ${redis_scope} guidance"
done
grep -q 'CREST_REDIS_NAMESPACE_REPORT=reports/readiness/redis-namespace-check.txt bash scripts/redis-cluster-namespace-check.sh$' "${action_plan}" \
  || fail "action plan must include Redis namespace report command"
grep -q '^command=CREST_READINESS_CHECK_EXTERNAL_EVIDENCE=true CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence bash scripts/enterprise-readiness-check.sh$' "${action_plan}" \
  || fail "action plan must include external evidence readiness command"

ready_summary="${test_root}/ready-summary.txt"
ready_plan="${test_root}/ready-plan.txt"
cat > "${ready_summary}" <<'EOF'
Crest Core enterprise readiness check
readiness_status=go-no-go-passed
production_release_status=ready-for-business-approval
EOF
bash scripts/production-readiness-action-plan.sh "${ready_summary}" "${ready_plan}"
grep -q '^blocker_count=0$' "${ready_plan}" \
  || fail "ready action plan must record zero blockers"
grep -q '^action=no production release blockers recorded$' "${ready_plan}" \
  || fail "ready action plan must explain there are no blockers"

echo "test-production-readiness-action-plan: passed"
