#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-overlay-evidence: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

command -v node >/dev/null 2>&1 || fail "missing required command: node"
command -v ruby >/dev/null 2>&1 || fail "missing required command: ruby"

repeat_char() {
  local char="$1"
  local count="$2"
  printf "%${count}s" "" | tr ' ' "${char}"
}

test_root="${CREST_TEST_PRODUCTION_OVERLAY_EVIDENCE_DIR:-.local/production-overlay-evidence-test-$$}"
overlay_dir="${test_root}/overlay"
evidence_dir="${test_root}/evidence"
unsafe_log="${test_root}/unsafe.log"
outside_output_log="${test_root}/outside-output.log"
unsafe_overlay_log="${test_root}/unsafe-overlay.log"
outside_overlay_log="${test_root}/outside-overlay.log"
db_password="test-db-password"
aes_key="$(repeat_char A 32)"
redis_username="ops01-prod-crest-core-acl"
redis_password="$(repeat_char R 20)"
redis_username_b64="$(printf '%s' "${redis_username}" | base64 | tr -d '\n')"
redis_password_b64="$(printf '%s' "${redis_password}" | base64 | tr -d '\n')"

rm -rf "${test_root}"
mkdir -p "${overlay_dir}"

cat > "${overlay_dir}/00-configmap.yaml" <<'YAML'
apiVersion: v1
kind: ConfigMap
metadata:
  name: crest-env
data:
  CREST_ORIGIN_LIST: "https://crest.example.internal"
  CREST_REDIS_KEY_PREFIX: "{ops01-prod-crest-core}:prod"
YAML

cat > "${overlay_dir}/01-db-secret.yaml" <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: crest-db-secret
type: Opaque
stringData:
  CREST_DB_USERNAME: "crest_app"
  CREST_DB_PASSWORD: "${db_password}"
  CREST_AES_KEY: "${aes_key}"
YAML

cat > "${overlay_dir}/02-redis-secret.yaml" <<YAML
apiVersion: v1
kind: Secret
metadata:
  name: crest-redis-secret
type: Opaque
data:
  CREST_REDIS_USERNAME: "${redis_username_b64}"
  CREST_REDIS_PASSWORD: "${redis_password_b64}"
YAML

if node scripts/production-overlay-evidence.mjs "${overlay_dir}" "." >"${unsafe_log}" 2>&1; then
  fail "production-overlay-evidence unexpectedly accepted the repository root as evidence dir"
fi
grep -q "CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR is too broad to overwrite" "${unsafe_log}" \
  || fail "unsafe evidence dir failure must explain the overwrite risk"

if node scripts/production-overlay-evidence.mjs "${overlay_dir}" "/tmp/crest-overlay-evidence-outside" >"${outside_output_log}" 2>&1; then
  fail "production-overlay-evidence unexpectedly accepted an outside-repository evidence dir"
fi
grep -q "CREST_PRODUCTION_OVERLAY_EVIDENCE_DIR must stay inside the repository" "${outside_output_log}" \
  || fail "outside evidence dir failure must explain the repository boundary"

if node scripts/production-overlay-evidence.mjs "deploy/kubernetes" "${evidence_dir}-from-deploy" >"${unsafe_overlay_log}" 2>&1; then
  fail "production-overlay-evidence unexpectedly accepted deploy/kubernetes as the overlay source"
fi
grep -q "CREST_PRODUCTION_OVERLAY_DIR is too broad to read overlay from" "${unsafe_overlay_log}" \
  || fail "unsafe overlay source failure must explain the read risk"

if node scripts/production-overlay-evidence.mjs "/tmp/crest-production-overlay-outside" "${evidence_dir}-from-outside" >"${outside_overlay_log}" 2>&1; then
  fail "production-overlay-evidence unexpectedly accepted an outside-repository overlay source"
fi
grep -q "CREST_PRODUCTION_OVERLAY_DIR must stay inside the repository" "${outside_overlay_log}" \
  || fail "outside overlay source failure must explain the repository boundary"

node scripts/production-overlay-evidence.mjs "${overlay_dir}" "${evidence_dir}" >/dev/null

summary="${evidence_dir}/summary.txt"
resources="${evidence_dir}/resources-sanitized.json"
secrets="${evidence_dir}/secrets-sanitized.json"
manifest="${evidence_dir}/overlay-evidence-manifest.sha256"

[[ -f "${summary}" ]] || fail "summary.txt was not written"
[[ -f "${resources}" ]] || fail "resources-sanitized.json was not written"
[[ -f "${secrets}" ]] || fail "secrets-sanitized.json was not written"
[[ -f "${manifest}" ]] || fail "overlay evidence manifest was not written"

grep -q '^resource_count=3$' "${summary}" \
  || fail "summary must record all overlay resources"
grep -q '^secret_count=2$' "${summary}" \
  || fail "summary must record secret count"
grep -q '^secret_name=crest-db-secret$' "${summary}" \
  || fail "summary must record crest-db-secret"
grep -q '^secret_name=crest-redis-secret$' "${summary}" \
  || fail "summary must record crest-redis-secret"
grep -q '^sanitized_resources=resources-sanitized.json$' "${summary}" \
  || fail "summary must record sanitized resources"
grep -q '^sanitized_secrets=secrets-sanitized.json$' "${summary}" \
  || fail "summary must record sanitized secrets"
grep -q '^evidence_manifest=overlay-evidence-manifest.sha256$' "${summary}" \
  || fail "summary must record evidence manifest"

node scripts/verify-sanitized-kubernetes-secrets.mjs \
  "${secrets}" \
  crest-db-secret \
  crest-redis-secret >/dev/null

grep -q 'decodedLength' "${secrets}" \
  || fail "sanitized secret output must retain length metadata"
if grep -Fq "${db_password}" "${resources}" "${secrets}" \
  || grep -Fq "${aes_key}" "${resources}" "${secrets}" \
  || grep -Fq "${redis_password}" "${resources}" "${secrets}" \
  || grep -Fq "${redis_password_b64}" "${resources}" "${secrets}" \
  || grep -Eq '"stringData"' "${resources}" "${secrets}"; then
  fail "sanitized overlay evidence leaked Secret material"
fi

grep -q 'summary.txt$' "${manifest}" \
  || fail "manifest must include summary.txt"
grep -q 'resources-sanitized.json$' "${manifest}" \
  || fail "manifest must include sanitized resources"
grep -q 'secrets-sanitized.json$' "${manifest}" \
  || fail "manifest must include sanitized secrets"

echo "test-production-overlay-evidence: passed"
