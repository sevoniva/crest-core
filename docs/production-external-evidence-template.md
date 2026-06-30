# Crest Core External Production Evidence

`scripts/production-external-evidence-check.sh` validates evidence that cannot be proven from the repository or Kubernetes API alone. Keep the real files under a private, ignored path such as `reports/readiness/external-evidence/`; do not commit customer names, passwords, tokens, private certificates, raw backups or real business data.

Each required file must be non-empty, contain no placeholder text, and include this exact line:

```text
status: passed
```

Required common fields:

```text
status: passed
environment: preprod-or-prod-name
evidence_date: YYYY-MM-DD
owner: team-or-reviewer
artifact_reference: internal-ticket-or-report-url
notes: short sanitized summary
```

`evidence_date` must use `YYYY-MM-DD`, must be a real calendar date, and must not be in the future. The checker writes SHA-256 digests of all evidence files to the summary so the approval package can prove which sanitized records were reviewed. The summary is written atomically only after all checks pass and includes `status=passed`, `required_evidence_files=10`, `evidence_file_count=10`, and indexed `evidence_file_N` / `evidence_file_N_sha256` lines. Go/No-Go validation recalculates those digests from the evidence directory.

Required files:

| File | Evidence to retain |
| --- | --- |
| `ob-oracle-init.md` | DBA-created OceanBase Oracle tenant/schema/account and initialization SQL execution record |
| `ob-oracle-backup.md` | backup policy, last successful backup, retention and monitoring record |
| `ob-oracle-restore.md` | restore drill record showing a backup was restored and verified |
| `redis-cluster.md` | Redis Cluster nodes, ACL user, key prefix/hash tag, connectivity and key/stream/channel ACL isolation record |
| `redis-failover.md` | Redis Cluster failover drill and Crest recovery behavior record |
| `credential-rotation.md` | historical secret scan review, credential rotation/expiration record, and clean-source or clean-history delivery decision |
| `tls-ingress.md` | Ingress host, TLS certificate issuer/expiry monitoring and real HTTPS access record |
| `storage-rwx.md` | `crest-data` RWX storage binding and shared write/read behavior record |
| `business-smoke.md` | login, dashboard access, dataset preview, export, async task and WebSocket refresh smoke record |
| `failure-drill.md` | rolling restart, API pod deletion and worker pod deletion drill record |

`credential-rotation.md` should identify the latest redacted history scan report, the report SHA-256, the remaining finding count, the affected credential classes, the rotation or expiration ticket for each class, the approval owner/date, and the approved delivery path. `approval_date` must use `YYYY-MM-DD`, must not be in the future, and must not be later than `evidence_date`. If the Git history is not rewritten, the evidence must state that the release uses a `clean-source` or `fresh-repository` delivery path and that historical credentials were rotated before delivery. If `delivery_path` is `clean-history`, `history_findings_remaining` must be `0`.

For shared Redis, `redis_acl_user` must be an 8-64 character lowercase environment-specific ACL user, not `default` and not a generic value such as `crest`, `prod`, `redis`, or `shared`.

Additional required fields for selected files:

```text
credential-rotation.md:
history_scan_report: reports/security/gitleaks-history.json
history_scan_report_sha256: <sha256-of-reports/security/gitleaks-history.json>
history_findings_remaining: 2
affected_credential_classes: initial-admin-password,application-encryption-key
credential_rotation_status: rotated-before-delivery
delivery_path: clean-source
rotation_evidence_id: SEC-12345
approved_by: platform-security
approval_date: 2026-06-28

redis-cluster.md:
redis_key_prefix: {<org>-<env>-crest-core}:prod
redis_hash_tag: <org>-<env>-crest-core
redis_acl_user: ops01-prod-crest-core-acl
redis_namespace_check_report: reports/readiness/redis-namespace-check.txt
redis_namespace_check_report_sha256: <sha256-of-reports/readiness/redis-namespace-check.txt>

redis namespace report:
status=passed
redis_cluster_nodes_count=3
redis_node=redis-0.example.internal:6379
redis_key_prefix={real-org-prod-crest-core}:prod
redis_hash_tag=real-org-prod-crest-core
redis_acl_user=ops01-prod-crest-core-acl
redis_acl_key_isolation=passed
redis_acl_stream_isolation=passed
redis_acl_channel_isolation=passed

tls-ingress.md:
ingress_host: crest.example.com
tls_expiry_monitor: internal-monitor-reference

storage-rwx.md:
pvc_name: crest-data
access_mode: ReadWriteMany

business-smoke.md:
smoke_scope: login,dashboard,dataset-preview,export,async-task,websocket

failure-drill.md:
drill_scope: rollout-restart,api-pod-delete,worker-pod-delete
```

Run:

```bash
CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence \
bash scripts/prepare-production-external-evidence.sh

CREST_REDIS_CLUSTER_NODES=redis-0.example.internal:6379,redis-1.example.internal:6379,redis-2.example.internal:6379 \
CREST_REDIS_USERNAME=ops01-prod-crest-core-acl \
CREST_REDIS_PASSWORD='...' \
CREST_REDIS_KEY_PREFIX='{<org>-<env>-crest-core}:prod' \
CREST_REDIS_CACHE_KEY_PREFIX='{<org>-<env>-crest-core}:prod:cache:' \
CREST_LOCK_KEY_PREFIX='{<org>-<env>-crest-core}:prod:lock' \
CREST_WEBSOCKET_BROADCAST_CHANNEL='{<org>-<env>-crest-core}:prod:pubsub:websocket' \
CREST_EXPORT_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:export-task' \
CREST_EXPORT_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:export-workers' \
CREST_SYNC_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:dataset-sync-task' \
CREST_SYNC_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:dataset-sync-workers' \
CREST_DATASOURCE_SYNC_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:datasource-sync-task' \
CREST_DATASOURCE_SYNC_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:datasource-sync-workers' \
CREST_SCHEDULED_TASK_STREAM='{<org>-<env>-crest-core}:prod:stream:scheduled-task' \
CREST_SCHEDULED_TASK_CONSUMER_GROUP='{<org>-<env>-crest-core}:prod:group:scheduled-workers' \
bash scripts/redis-cluster-namespace-check.sh

CREST_EXTERNAL_EVIDENCE_DIR=reports/readiness/external-evidence \
bash scripts/production-external-evidence-check.sh
```

The preparation script creates a private draft directory, copies the current
history secret scan report SHA-256 and finding count when available, and copies
the Redis namespace report fields when available. It intentionally leaves
`CHANGE_ME` placeholders for human-owned approval, drill, backup, TLS, storage
and business smoke records, so the external evidence check will fail until real
sanitized evidence replaces those placeholders.

Go/No-Go mode runs the same check automatically when `CREST_READINESS_REQUIRE_GO_NO_GO=true`.
