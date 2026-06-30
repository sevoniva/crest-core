#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-security-scan-report-dir: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_SECURITY_SCAN_REPORT_DIR:-.local/security-scan-report-dir-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/bin"

for command_name in semgrep gitleaks pnpm mvn osv-scanner; do
  cat > "${test_root}/bin/${command_name}" <<'EOF'
#!/usr/bin/env bash
echo "unexpected scanner invocation: $0 $*" >&2
exit 2
EOF
  chmod +x "${test_root}/bin/${command_name}"
done

run_with_fake_scanners() {
  env PATH="${test_root}/bin:${PATH}" CREST_OSV_SCANNER=osv-scanner "$@"
}

outside_dir="/tmp/crest-security-report-dir-outside-$$"
rm -rf "${outside_dir}"
outside_log="${test_root}/outside.log"
if run_with_fake_scanners \
  CREST_SECURITY_REPORT_DIR="${outside_dir}" \
  bash scripts/security-scan.sh >"${outside_log}" 2>&1; then
  fail "security scan should reject report directories outside the repository"
fi
grep -q 'CREST_SECURITY_REPORT_DIR must stay inside the repository' "${outside_log}" \
  || fail "outside report directory failure must be explicit"
[[ ! -e "${outside_dir}" ]] \
  || fail "outside report directory must not be created"
if grep -q 'unexpected scanner invocation' "${outside_log}"; then
  fail "unsafe report directory must fail before invoking scanners"
fi

escape_log="${test_root}/relative-escape.log"
if run_with_fake_scanners \
  CREST_SECURITY_REPORT_DIR="../crest-security-report-dir-escape" \
  bash scripts/security-scan.sh >"${escape_log}" 2>&1; then
  fail "security scan should reject relative parent escapes"
fi
grep -q 'refusing unsafe security report directory' "${escape_log}" \
  || fail "relative escape failure must be explicit"
if grep -q 'unexpected scanner invocation' "${escape_log}"; then
  fail "relative escape must fail before invoking scanners"
fi

root_log="${test_root}/root.log"
if run_with_fake_scanners \
  CREST_SECURITY_REPORT_DIR="/" \
  bash scripts/security-scan.sh >"${root_log}" 2>&1; then
  fail "security scan should reject root as the report directory"
fi
grep -q 'refusing unsafe security report directory' "${root_log}" \
  || fail "root report directory failure must be explicit"

echo "test-security-scan-report-dir: passed"
