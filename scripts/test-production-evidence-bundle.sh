#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-production-evidence-bundle: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_EVIDENCE_BUNDLE_DIR:-.local/production-evidence-bundle-test-$$}"
fake_bin="${test_root}/bin"
output_dir="${test_root}/evidence"
log_file="${test_root}/calls.log"

rm -rf "${test_root}"
mkdir -p "${fake_bin}"
: > "${log_file}"

cat > "${fake_bin}/date" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "20260102T030405Z"
EOF

cat > "${fake_bin}/kubectl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "kubectl $*" >> "${CREST_EVIDENCE_TEST_LOG}"
args=" $* "
if [[ "${args}" == *" version --short "* || "${args}" == *" version --short" ]]; then
  echo "Client Version: v1.34.0"
  echo "Server Version: v1.34.0"
  exit 0
fi
if [[ "${args}" == *" get secret "* && "${args}" == *" -o json"* ]]; then
  cat <<'JSON'
{"kind":"List","items":[{"metadata":{"name":"crest-db-secret"},"data":{"CREST_DB_PASSWORD":"c2VjcmV0"}},{"metadata":{"name":"crest-redis-secret"},"data":{"CREST_REDIS_PASSWORD":"cmVkaXMtc2VjcmV0"}},{"metadata":{"name":"crest-tls"},"data":{"tls.crt":"Y2VydA==","tls.key":"a2V5"}}]}
JSON
  exit 0
fi
if [[ "${args}" == *" -o json"* ]]; then
  cat <<'JSON'
{"kind":"Object","metadata":{"name":"crest-test"}}
JSON
  exit 0
fi
echo "NAME READY STATUS"
EOF

cat > "${fake_bin}/node" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
echo "node $*" >> "${CREST_EVIDENCE_TEST_LOG}"
case "${1:-}" in
  scripts/sanitize-kubernetes-secrets.mjs)
    cat >/dev/null
    cat <<'JSON'
{"items":[{"metadata":{"name":"crest-db-secret"},"sanitizedData":{"CREST_DB_PASSWORD":{"present":true,"decodedLength":6}}},{"metadata":{"name":"crest-redis-secret"},"sanitizedData":{"CREST_REDIS_PASSWORD":{"present":true,"decodedLength":12}}},{"metadata":{"name":"crest-tls"},"sanitizedData":{"tls.crt":{"present":true,"decodedLength":4},"tls.key":{"present":true,"decodedLength":3}}}]}
JSON
    ;;
  scripts/verify-sanitized-kubernetes-secrets.mjs)
    secrets_file="${2:-}"
    shift 2
    for required_secret in "$@"; do
      grep -q "\"name\":\"${required_secret}\"" "${secrets_file}" \
        || { echo "missing ${required_secret}" >&2; exit 1; }
    done
    if grep -q '"data"' "${secrets_file}" || grep -q '"stringData"' "${secrets_file}"; then
      echo "raw secret data leaked" >&2
      exit 1
    fi
    echo "verify-sanitized-kubernetes-secrets: passed"
    ;;
  scripts/production-runtime-check.mjs)
    echo "runtime-check: namespace crest-evidence-test passed live production runtime checks"
    ;;
  *)
    echo "unexpected node call: $*" >&2
    exit 1
    ;;
esac
EOF

chmod +x "${fake_bin}/date" "${fake_bin}/kubectl" "${fake_bin}/node"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_EVIDENCE_TEST_LOG="${log_file}" \
  CREST_EVIDENCE_DIR="." \
  bash scripts/production-evidence-bundle.sh >"${test_root}/unsafe-dir.log" 2>&1; then
  fail "production-evidence-bundle unexpectedly accepted the repository root as evidence dir"
fi
grep -q "CREST_EVIDENCE_DIR is too broad to write evidence into" "${test_root}/unsafe-dir.log" \
  || fail "unsafe evidence dir failure must explain the write risk"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_EVIDENCE_TEST_LOG="${log_file}" \
  CREST_EVIDENCE_DIR="/tmp/crest-production-evidence-outside" \
  bash scripts/production-evidence-bundle.sh >"${test_root}/outside-dir.log" 2>&1; then
  fail "production-evidence-bundle unexpectedly accepted an outside-repository evidence dir"
fi
grep -q "CREST_EVIDENCE_DIR must stay inside the repository" "${test_root}/outside-dir.log" \
  || fail "outside evidence dir failure must explain the repository boundary"

outside_parent="/tmp/crest-production-evidence-missing-parent-$$"
rm -rf "${outside_parent}"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_EVIDENCE_TEST_LOG="${log_file}" \
  CREST_EVIDENCE_DIR="${outside_parent}/evidence" \
  bash scripts/production-evidence-bundle.sh >"${test_root}/outside-missing-parent-dir.log" 2>&1; then
  fail "production-evidence-bundle unexpectedly accepted an outside-repository evidence dir with missing parent"
fi
grep -q "CREST_EVIDENCE_DIR must stay inside the repository" "${test_root}/outside-missing-parent-dir.log" \
  || fail "outside missing-parent evidence dir failure must explain the repository boundary"
[[ ! -e "${outside_parent}" ]] \
  || fail "outside missing-parent evidence dir check must not create directories before rejecting"

env \
  PATH="${fake_bin}:${PATH}" \
  CREST_EVIDENCE_TEST_LOG="${log_file}" \
  CREST_K8S_NAMESPACE="crest-evidence-test" \
  CREST_KUBE_CONTEXT="kind-evidence-test" \
  CREST_EVIDENCE_DIR="${output_dir}" \
  bash scripts/production-evidence-bundle.sh >/dev/null

summary="${output_dir}/summary.txt"
manifest="${output_dir}/evidence-manifest.sha256"

[[ -f "${summary}" ]] || fail "summary.txt was not written"
[[ -f "${manifest}" ]] || fail "evidence manifest was not written"
[[ -f "${output_dir}/production-runtime-check.txt" ]] || fail "runtime check output was not written"
[[ -f "${output_dir}/secrets-sanitized.json" ]] || fail "sanitized secret output was not written"
summary_evidence_file_count="$(awk -F= '$1 == "evidence_file_count" { print $2; exit }' "${summary}")"
manifest_entry_count="$(wc -l < "${manifest}" | tr -d '[:space:]')"

grep -q '^timestamp_utc=20260102T030405Z$' "${summary}" \
  || fail "summary must include deterministic timestamp"
grep -q '^namespace=crest-evidence-test$' "${summary}" \
  || fail "summary must include namespace"
grep -q '^context=kind-evidence-test$' "${summary}" \
  || fail "summary must include context"
grep -q '^runtime_check=passed$' "${summary}" \
  || fail "summary must record runtime_check=passed"
grep -q '^runtime_check_require_ingress_address=true$' "${summary}" \
  || fail "summary must record runtime_check_require_ingress_address=true"
grep -Eq '^evidence_file_count=[1-9][0-9]*$' "${summary}" \
  || fail "summary must record evidence_file_count"
[[ "${summary_evidence_file_count}" == "${manifest_entry_count}" ]] \
  || fail "summary evidence_file_count must match evidence manifest entry count"
grep -q '^evidence_manifest=evidence-manifest.sha256$' "${summary}" \
  || fail "summary must record evidence manifest file"

grep -q 'summary.txt$' "${manifest}" \
  || fail "manifest must include summary.txt"
grep -q 'production-runtime-check.txt$' "${manifest}" \
  || fail "manifest must include runtime check output"
grep -q 'secrets-sanitized.json$' "${manifest}" \
  || fail "manifest must include sanitized secrets"
grep -q 'decodedLength' "${output_dir}/secrets-sanitized.json" \
  || fail "sanitized secret output must contain metadata, not raw values"
if grep -Eq '"data"|"stringData"|c2VjcmV0|cmVkaXMtc2VjcmV0|Y2VydA==|a2V5' "${output_dir}/secrets-sanitized.json"; then
  fail "sanitized secret output must not contain raw Kubernetes Secret data"
fi
grep -q 'node scripts/verify-sanitized-kubernetes-secrets.mjs .*/secrets-sanitized.json crest-db-secret crest-redis-secret crest-tls' "${log_file}" \
  || fail "evidence bundle must verify sanitized secret evidence"
grep -q 'node scripts/production-runtime-check.mjs --namespace crest-evidence-test --context kind-evidence-test --require-ingress-address' "${log_file}" \
  || fail "evidence bundle must run runtime check with namespace and context"

echo "test-production-evidence-bundle: passed"
