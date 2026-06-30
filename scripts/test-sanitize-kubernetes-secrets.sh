#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-sanitize-kubernetes-secrets: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

command -v node >/dev/null 2>&1 || fail "missing required command: node"

input='{
  "kind": "List",
  "items": [
    {
      "apiVersion": "v1",
      "kind": "Secret",
      "metadata": {
        "name": "crest-db-secret",
        "namespace": "crest",
        "creationTimestamp": "2026-01-01T00:00:00Z"
      },
      "type": "Opaque",
      "data": {
        "SAMPLE_ALPHA": "YWxwaGEtdmFsdWU=",
        "SAMPLE_BRAVO": "YnJhdm8tdmFsdWU="
      }
    }
  ]
}'

output="$(printf '%s' "${input}" | node scripts/sanitize-kubernetes-secrets.mjs)"

printf '%s' "${output}" | node -e '
let input = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", (chunk) => {
  input += chunk;
});
process.stdin.on("end", () => {
  const parsed = JSON.parse(input);
  const secret = parsed.items?.[0];
  if (secret?.metadata?.name !== "crest-db-secret") {
    throw new Error("secret name was not preserved");
  }
  if (secret?.data) {
    throw new Error("sanitized output must not contain data");
  }
  if (secret?.stringData) {
    throw new Error("sanitized output must not contain stringData");
  }
  if (secret?.sanitizedData?.SAMPLE_ALPHA?.decodedLength !== 11) {
    throw new Error("decoded length for SAMPLE_ALPHA was not preserved");
  }
  if (secret?.sanitizedData?.SAMPLE_BRAVO?.decodedLength !== 11) {
    throw new Error("decoded length for SAMPLE_BRAVO was not preserved");
  }
});
'

printf '%s' "${output}" \
  | node scripts/verify-sanitized-kubernetes-secrets.mjs /dev/stdin crest-db-secret >/dev/null

if printf '%s' "${output}" | grep -Eq 'alpha-value|bravo-value|YWxwaGEtdmFsdWU=|YnJhdm8tdmFsdWU='; then
  fail "sanitized output leaked secret material"
fi

bad_output='{"items":[{"metadata":{"name":"crest-db-secret"},"data":{"SAMPLE_ALPHA":"YWxwaGE="},"sanitizedData":{"SAMPLE_ALPHA":{"present":true,"decodedLength":5}}}]}'
if printf '%s' "${bad_output}" \
  | node scripts/verify-sanitized-kubernetes-secrets.mjs /dev/stdin crest-db-secret >/tmp/crest-sanitized-secret-negative.out 2>&1; then
  fail "sanitized secret verifier accepted raw data"
fi

if ! grep -q "must not contain Kubernetes Secret data" /tmp/crest-sanitized-secret-negative.out; then
  fail "sanitized secret verifier did not explain raw data rejection"
fi

echo "test-sanitize-kubernetes-secrets: passed"
