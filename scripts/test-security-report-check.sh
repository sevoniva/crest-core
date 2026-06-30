#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-security-report-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_SECURITY_REPORT_CHECK_DIR:-.local/security-report-check-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/good" "${test_root}/bad"

write_good_reports() {
  local dir="$1"
  mkdir -p "${dir}"
  cat > "${dir}/semgrep.json" <<'EOF'
{"results":[]}
EOF
  cat > "${dir}/gitleaks.json" <<'EOF'
[]
EOF
  cat > "${dir}/maven-dependency-tree.txt" <<'EOF'
io.crest:crest:pom:1.0.0
EOF
  cat > "${dir}/crest-bom.json" <<'EOF'
{"bomFormat":"CycloneDX","components":[{"name":"crest","version":"1.0.0"}]}
EOF
  cat > "${dir}/pnpm-audit.json" <<'EOF'
{"metadata":{"vulnerabilities":{"info":0,"low":0,"moderate":0,"high":0,"critical":0,"total":0}}}
EOF
  cat > "${dir}/frontend-licenses.json" <<'EOF'
{"MIT":[{"name":"crest-web","versions":["1.0.0"]}]}
EOF
  cat > "${dir}/license-policy.txt" <<'EOF'
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
  cat > "${dir}/osv-frontend.json" <<'EOF'
{"results":[]}
EOF
  cat > "${dir}/osv-maven-sbom.json" <<'EOF'
{"results":[]}
EOF
}

write_good_reports "${test_root}/good"
node scripts/security-report-check.mjs "${test_root}/good" >/dev/null

semgrep_bad="${test_root}/bad/semgrep"
write_good_reports "${semgrep_bad}"
cat > "${semgrep_bad}/semgrep.json" <<'EOF'
{"results":[{"check_id":"java.lang.security.audit"}]}
EOF
if node scripts/security-report-check.mjs "${semgrep_bad}" >"${semgrep_bad}.log" 2>&1; then
  fail "security report check should fail when Semgrep reports findings"
fi
grep -q 'Semgrep findings must be 0' "${semgrep_bad}.log" \
  || fail "Semgrep finding failure must be explicit"

gitleaks_bad="${test_root}/bad/gitleaks"
write_good_reports "${gitleaks_bad}"
cat > "${gitleaks_bad}/gitleaks.json" <<'EOF'
[{"RuleID":"generic-api-key"}]
EOF
if node scripts/security-report-check.mjs "${gitleaks_bad}" >"${gitleaks_bad}.log" 2>&1; then
  fail "security report check should fail when Gitleaks reports findings"
fi
grep -q 'Gitleaks findings must be 0' "${gitleaks_bad}.log" \
  || fail "Gitleaks finding failure must be explicit"

pnpm_bad="${test_root}/bad/pnpm"
write_good_reports "${pnpm_bad}"
cat > "${pnpm_bad}/pnpm-audit.json" <<'EOF'
{"metadata":{"vulnerabilities":{"info":0,"low":1,"moderate":0,"high":0,"critical":0,"total":1}}}
EOF
if node scripts/security-report-check.mjs "${pnpm_bad}" >"${pnpm_bad}.log" 2>&1; then
  fail "security report check should fail when pnpm reports any production vulnerability"
fi
grep -q 'pnpm production audit vulnerabilities must be 0' "${pnpm_bad}.log" \
  || fail "pnpm vulnerability failure must be explicit"

osv_bad="${test_root}/bad/osv"
write_good_reports "${osv_bad}"
cat > "${osv_bad}/osv-frontend.json" <<'EOF'
{"results":[{"packages":[{"package":{"name":"demo"},"vulnerabilities":[{"id":"GHSA-demo"}]}]}]}
EOF
if node scripts/security-report-check.mjs "${osv_bad}" >"${osv_bad}.log" 2>&1; then
  fail "security report check should fail when OSV reports vulnerabilities"
fi
grep -q 'osv-frontend.json OSV vulnerabilities must be 0' "${osv_bad}.log" \
  || fail "OSV vulnerability failure must be explicit"

missing_bad="${test_root}/bad/missing"
write_good_reports "${missing_bad}"
rm "${missing_bad}/crest-bom.json"
if node scripts/security-report-check.mjs "${missing_bad}" >"${missing_bad}.log" 2>&1; then
  fail "security report check should fail when required reports are missing"
fi
grep -q 'missing crest-bom.json' "${missing_bad}.log" \
  || fail "missing report failure must be explicit"

license_bad="${test_root}/bad/license"
write_good_reports "${license_bad}"
sed -i.bak 's/^status=passed$/status=failed/' "${license_bad}/license-policy.txt"
if node scripts/security-report-check.mjs "${license_bad}" >"${license_bad}.log" 2>&1; then
  fail "security report check should fail when license policy report fails"
fi
grep -q 'license-policy.txt must record status=passed' "${license_bad}.log" \
  || fail "license policy failure must be explicit"

echo "test-security-report-check: passed"
