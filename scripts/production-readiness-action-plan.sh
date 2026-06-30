#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "production-readiness-action-plan: $*" >&2
  exit 1
}

summary_file="${1:-reports/readiness/enterprise-readiness-summary.txt}"
output_file="${2:-${CREST_READINESS_ACTION_PLAN:-reports/readiness/production-readiness-action-plan.txt}}"

[[ -f "${summary_file}" ]] || fail "missing readiness summary: ${summary_file}"

field_value() {
  local field="$1"
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${summary_file}"
}

report_field_value() {
  local path="$1"
  local field="$2"
  [[ -f "${path}" ]] || return 0
  awk -F= -v target="${field}" '$1 == target { print substr($0, length(target) + 2); exit }' "${path}"
}

append_action_header() {
  local title="$1"
  {
    echo
    echo "action=${title}"
  } >> "${output_file}"
}

append_line() {
  printf '%s\n' "$1" >> "${output_file}"
}

mkdir -p "$(dirname "${output_file}")"

readiness_status="$(field_value readiness_status)"
production_release_status="$(field_value production_release_status)"
docker_report="$(field_value docker_environment_report)"
docker_cleanup_plan="$(field_value docker_cleanup_plan)"
history_summary="$(field_value history_secret_audit_summary)"
history_report="$(field_value history_secret_audit_report)"
history_findings="$(field_value history_secret_audit_findings)"
history_commits="$(field_value history_secret_audit_commits)"

{
  echo "Crest Core production readiness action plan"
  echo "summary=${summary_file}"
  echo "readiness_status=${readiness_status:-missing}"
  echo "production_release_status=${production_release_status:-missing}"
  echo "generated_at=$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
} > "${output_file}"

blocker_count="$(grep -c '^production_release_blocker=' "${summary_file}" || true)"
append_line "blocker_count=${blocker_count}"

if [[ "${blocker_count}" == "0" ]]; then
  append_action_header "no production release blockers recorded"
  append_line "next=Keep the readiness summary, security reports, container reports, clean-source archive, runtime evidence, and external evidence bundle together for approval."
  exit 0
fi

while IFS= read -r blocker_line; do
  blocker="${blocker_line#production_release_blocker=}"
  case "${blocker}" in
    "Docker environment preflight failed")
      append_action_header "${blocker}"
      append_line "evidence=${docker_report:-missing}"
      append_line "cleanup_plan=${docker_cleanup_plan:-missing}"
      if [[ -n "${docker_report}" && -f "${docker_report}" ]]; then
        append_line "docker_status=$(report_field_value "${docker_report}" status)"
        append_line "docker_available_gib=$(report_field_value "${docker_report}" available_gib)"
        append_line "docker_required_gib=$(report_field_value "${docker_report}" required_gib)"
        append_line "docker_shortfall_gib=$(report_field_value "${docker_report}" shortfall_gib)"
      fi
      append_line "next=Review the Docker environment report and cleanup plan, get owner approval, free enough space for at least 12GiB, then rerun enterprise-readiness-check.sh."
      ;;
    "Docker image build gate skipped after failed Docker prerequisite")
      append_action_header "${blocker}"
      append_line "depends_on=Docker environment preflight failed"
      append_line "next=After Docker preflight passes, rerun the readiness check so docker-build can build the frontend and backend images."
      ;;
    "container image CVE gate skipped after failed Docker prerequisite")
      append_action_header "${blocker}"
      append_line "depends_on=Docker environment preflight failed"
      append_line "next=After image build passes, rerun container-image-scan.sh and keep reports/container/trivy-*.json with zero HIGH/CRITICAL findings."
      ;;
    "git history secret audit failed")
      append_action_header "${blocker}"
      append_line "history_summary=${history_summary:-missing}"
      append_line "history_report=${history_report:-missing}"
      append_line "history_findings=${history_findings:-missing}"
      append_line "history_commits=${history_commits:-missing}"
      append_line "next=Rotate or expire the affected credential classes, record the ticket in credential-rotation.md, include history_scan_report_sha256, then use clean-source, fresh-repository, or clean-history delivery."
      ;;
    "production overlay render and strict config gate failed"|"strict production overlay was not rendered or checked")
      append_action_header "${blocker}"
      append_line "next=Create a private production env file from deploy/kubernetes/production.env.example, fill real OB Oracle, Redis Cluster, TLS host, storage class, and image values, source it, then run CREST_READINESS_RENDER_OVERLAY=true enterprise-readiness-check.sh."
      append_line "template=deploy/kubernetes/production.env.example"
      append_line "command=set -a; source .local/crest-production.env; set +a; CREST_READINESS_RENDER_OVERLAY=true bash scripts/enterprise-readiness-check.sh"
      ;;
    "clean source release export was not generated")
      append_action_header "${blocker}"
      append_line "next=After credential rotation evidence is ready, run CREST_READINESS_CREATE_CLEAN_SOURCE=true with CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true and archive reports/release-source/*.tar.gz plus its summary."
      ;;
    "clean source history finding count was not verified")
      append_action_header "${blocker}"
      append_line "next=Run CREST_READINESS_REQUIRE_CLEAN_HISTORY=true or provide CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT with a verified Gitleaks history JSON report before regenerating the clean-source archive."
      ;;
    "clean source credential rotation evidence was not recorded")
      append_action_header "${blocker}"
      append_line "next=Rotate or expire the affected historical credential classes, then rerun clean-source export with CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true, CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery, and CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES populated."
      append_line "command=CREST_READINESS_CREATE_CLEAN_SOURCE=true CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES=<rotated-classes> CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH=clean-source bash scripts/enterprise-readiness-check.sh"
      ;;
    "clean source release was not required to come from a clean git worktree")
      append_action_header "${blocker}"
      append_line "next=For final release, commit or intentionally exclude pending changes, then rerun with CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true so the source archive is traceable to a clean commit."
      append_line "command=CREST_READINESS_CREATE_CLEAN_SOURCE=true CREST_READINESS_REQUIRE_CLEAN_RELEASE_SOURCE=true CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES=<rotated-classes> CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH=clean-source bash scripts/enterprise-readiness-check.sh"
      ;;
    "live preprod/production runtime check was not run")
      append_action_header "${blocker}"
      append_line "next=Apply the production overlay to a preprod or production namespace, then run CREST_READINESS_COLLECT_EVIDENCE=true with CREST_K8S_NAMESPACE and CREST_KUBE_CONTEXT to capture runtime evidence."
      append_line "command=CREST_READINESS_COLLECT_EVIDENCE=true CREST_K8S_NAMESPACE=<namespace> CREST_KUBE_CONTEXT=<context> bash scripts/enterprise-readiness-check.sh"
      ;;
    "external production evidence was not checked")
      append_action_header "${blocker}"
      append_line "next=Populate the private external evidence directory from docs/production-external-evidence-template.md, including Redis namespace report and credential-rotation.md history_scan_report_sha256, then run CREST_READINESS_CHECK_EXTERNAL_EVIDENCE=true."
      append_line "prepare=CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence bash scripts/prepare-production-external-evidence.sh"
      append_line "redis=CREST_REDIS_CLUSTER_NODES=<redis-node-1>:6379,<redis-node-2>:6379,<redis-node-3>:6379 CREST_REDIS_USERNAME=<redis-acl-user> CREST_REDIS_PASSWORD='<redis-password>' CREST_REDIS_KEY_PREFIX='{<org>-<env>-crest-core}:prod' CREST_REDIS_CACHE_KEY_PREFIX='{<org>-<env>-crest-core}:prod:cache:' CREST_LOCK_KEY_PREFIX='{<org>-<env>-crest-core}:prod:lock' CREST_WEBSOCKET_BROADCAST_CHANNEL='{<org>-<env>-crest-core}:prod:pubsub:websocket' CREST_EXPORT_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:export-task' CREST_EXPORT_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:export-workers' CREST_SYNC_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:dataset-sync-task' CREST_SYNC_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:dataset-sync-workers' CREST_DATASOURCE_SYNC_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:datasource-sync-task' CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:datasource-sync-workers' CREST_SCHEDULED_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:scheduled-task' CREST_SCHEDULED_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:scheduled-workers' CREST_REDIS_NAMESPACE_REPORT=reports/readiness/redis-namespace-check.txt bash scripts/redis-cluster-namespace-check.sh"
      append_line "command=CREST_READINESS_CHECK_EXTERNAL_EVIDENCE=true CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence bash scripts/enterprise-readiness-check.sh"
      ;;
    "one or more static gates were skipped")
      append_action_header "${blocker}"
      append_line "next=Rerun without CREST_READINESS_SKIP_* flags before production approval."
      ;;
    *)
      append_action_header "${blocker}"
      append_line "next=Inspect the related gate log in the readiness summary, fix the failing evidence, then rerun enterprise-readiness-check.sh."
      ;;
  esac
done < <(grep '^production_release_blocker=' "${summary_file}")
