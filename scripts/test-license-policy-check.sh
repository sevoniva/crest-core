#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-license-policy-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_LICENSE_POLICY_DIR:-.local/license-policy-test-$$}"
policy_file="${test_root}/license-policy.json"
maven_bom="${test_root}/crest-bom.json"
frontend_licenses="${test_root}/frontend-licenses.json"
report_file="${test_root}/license-policy.txt"

rm -rf "${test_root}"
mkdir -p "${test_root}"

cat > "${policy_file}" <<'JSON'
{
  "allowedLicenses": ["MIT", "Apache-2.0"],
  "deniedLicenseKeywords": ["agpl", "affero", "noassertion"],
  "reviewedExceptions": [
    {
      "ecosystem": "npm",
      "name": "known-custom",
      "version": "1.0.0",
      "license": "SEE LICENSE IN LICENSE",
      "rationale": "fixture exception"
    }
  ]
}
JSON

cat > "${maven_bom}" <<'JSON'
{
  "bomFormat": "CycloneDX",
  "components": [
    {"group": "org.example", "name": "ok", "version": "1.0.0", "licenses": [{"license": {"id": "Apache-2.0"}}]}
  ]
}
JSON

cat > "${frontend_licenses}" <<'JSON'
{
  "MIT": [{"name": "left-pad", "versions": ["1.0.0"]}],
  "SEE LICENSE IN LICENSE": [{"name": "known-custom", "versions": ["1.0.0"]}]
}
JSON

CREST_LICENSE_POLICY_FILE="${policy_file}" \
CREST_MAVEN_BOM_FILE="${maven_bom}" \
CREST_FRONTEND_LICENSES_FILE="${frontend_licenses}" \
CREST_LICENSE_POLICY_REPORT="${report_file}" \
  node scripts/license-policy-check.mjs >/dev/null

grep -q '^status=passed$' "${report_file}" || fail "allowed licenses should pass"
grep -q '^reviewed_exceptions=1$' "${report_file}" || fail "reviewed exception should be recorded"

cat > "${maven_bom}" <<'JSON'
{
  "bomFormat": "CycloneDX",
  "components": [
    {"group": "org.example", "name": "bad", "version": "1.0.0", "licenses": [{"license": {"name": "GNU Affero General Public License v3"}}]}
  ]
}
JSON
if CREST_LICENSE_POLICY_FILE="${policy_file}" \
  CREST_MAVEN_BOM_FILE="${maven_bom}" \
  CREST_FRONTEND_LICENSES_FILE="${frontend_licenses}" \
  CREST_LICENSE_POLICY_REPORT="${report_file}" \
  node scripts/license-policy-check.mjs >/tmp/crest-license-policy-agpl.out 2>&1; then
  fail "AGPL fixture unexpectedly passed"
fi
grep -q "denied license" /tmp/crest-license-policy-agpl.out \
  || fail "denied license failure message was not reported"

cat > "${maven_bom}" <<'JSON'
{
  "bomFormat": "CycloneDX",
  "components": []
}
JSON
cat > "${frontend_licenses}" <<'JSON'
{
  "Unknown": [{"name": "mystery", "versions": ["0.0.1"]}]
}
JSON
if CREST_LICENSE_POLICY_FILE="${policy_file}" \
  CREST_MAVEN_BOM_FILE="${maven_bom}" \
  CREST_FRONTEND_LICENSES_FILE="${frontend_licenses}" \
  CREST_LICENSE_POLICY_REPORT="${report_file}" \
  node scripts/license-policy-check.mjs >/tmp/crest-license-policy-unknown.out 2>&1; then
  fail "unknown license unexpectedly passed"
fi
grep -q "unreviewed license" /tmp/crest-license-policy-unknown.out \
  || fail "unknown license failure message was not reported"

echo "test-license-policy-check: passed"
