#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-history-secret-audit: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_HISTORY_SECRET_AUDIT_DIR:-.local/history-secret-audit-test-$$}"
fake_bin="${test_root}/bin"
report_dir="${test_root}/security"
summary_file="${report_dir}/gitleaks-history-summary.txt"

rm -rf "${test_root}"
mkdir -p "${fake_bin}" "${report_dir}"

cat > "${fake_bin}/gitleaks" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
report_path=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --report-path)
      report_path="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
[[ -n "${report_path}" ]] || exit 2
mkdir -p "$(dirname "${report_path}")"
if [[ "${CREST_FAKE_GITLEAKS_FINDINGS:-false}" == "true" ]]; then
  cat > "${report_path}" <<'JSON'
[
  {"RuleID":"generic-api-key","File":"deploy/kubernetes/crest-ob-oracle-redis.yaml","Commit":"abc"},
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml","Commit":"def"}
]
JSON
  exit 1
fi
printf '[]\n' > "${report_path}"
EOF
chmod +x "${fake_bin}/gitleaks"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="." \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-unsafe-report-dir.out 2>&1; then
  fail "history secret audit unexpectedly accepted repository root as report dir"
fi

grep -q "CREST_SECURITY_REPORT_DIR is too broad to overwrite" /tmp/crest-history-unsafe-report-dir.out \
  || fail "unsafe history report dir failure message was not reported"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="/tmp/crest-history-security-outside" \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-outside-report-dir.out 2>&1; then
  fail "history secret audit unexpectedly accepted outside-repository report dir"
fi

grep -q "CREST_SECURITY_REPORT_DIR must stay inside the repository" /tmp/crest-history-outside-report-dir.out \
  || fail "outside history report dir failure message was not reported"

missing_report_parent="/tmp/crest-history-report-missing-parent-$$"
rm -rf "${missing_report_parent}"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${missing_report_parent}/reports" \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-missing-parent-report-dir.out 2>&1; then
  fail "history secret audit unexpectedly accepted outside missing-parent report dir"
fi

grep -q "CREST_SECURITY_REPORT_DIR must stay inside the repository" /tmp/crest-history-missing-parent-report-dir.out \
  || fail "outside missing-parent history report dir failure message was not reported"
[[ ! -e "${missing_report_parent}" ]] \
  || fail "outside missing-parent history report dir was created before rejection"

outside_link_target="/tmp/crest-history-report-symlink-target-$$"
rm -rf "${outside_link_target}"
mkdir -p "${outside_link_target}"
ln -s "${outside_link_target}" "${test_root}/outside-report-link"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${test_root}/outside-report-link/reports" \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-symlink-report-dir.out 2>&1; then
  fail "history secret audit unexpectedly accepted symlink-escaped report dir"
fi

grep -q "CREST_SECURITY_REPORT_DIR must stay inside the repository" /tmp/crest-history-symlink-report-dir.out \
  || fail "symlink-escaped history report dir failure message was not reported"
[[ ! -e "${outside_link_target}/reports" ]] \
  || fail "symlink-escaped history report dir was created outside the repository"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${report_dir}" \
  CREST_HISTORY_SECRET_SUMMARY="." \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-unsafe-summary.out 2>&1; then
  fail "history secret audit unexpectedly accepted repository root as summary file"
fi

grep -q "CREST_HISTORY_SECRET_SUMMARY is too broad to overwrite" /tmp/crest-history-unsafe-summary.out \
  || fail "unsafe history summary failure message was not reported"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${report_dir}" \
  CREST_HISTORY_SECRET_SUMMARY="/tmp/crest-history-summary-outside.txt" \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-outside-summary.out 2>&1; then
  fail "history secret audit unexpectedly accepted outside-repository summary file"
fi

grep -q "CREST_HISTORY_SECRET_SUMMARY must stay inside the repository" /tmp/crest-history-outside-summary.out \
  || fail "outside history summary failure message was not reported"

missing_summary_parent="/tmp/crest-history-summary-missing-parent-$$"
rm -rf "${missing_summary_parent}"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${report_dir}" \
  CREST_HISTORY_SECRET_SUMMARY="${missing_summary_parent}/summary.txt" \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-missing-parent-summary.out 2>&1; then
  fail "history secret audit unexpectedly accepted outside missing-parent summary file"
fi

grep -q "CREST_HISTORY_SECRET_SUMMARY must stay inside the repository" /tmp/crest-history-missing-parent-summary.out \
  || fail "outside missing-parent history summary failure message was not reported"
[[ ! -e "${missing_summary_parent}" ]] \
  || fail "outside missing-parent history summary directory was created before rejection"

outside_summary_target="/tmp/crest-history-summary-symlink-target-$$.txt"
rm -f "${outside_summary_target}"
printf 'unchanged\n' > "${outside_summary_target}"
ln -s "${outside_summary_target}" "${test_root}/outside-summary-link.txt"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${report_dir}" \
  CREST_HISTORY_SECRET_SUMMARY="${test_root}/outside-summary-link.txt" \
  bash scripts/history-secret-audit.sh >/tmp/crest-history-symlink-summary.out 2>&1; then
  fail "history secret audit unexpectedly accepted symlink-escaped summary file"
fi

grep -q "CREST_HISTORY_SECRET_SUMMARY must stay inside the repository" /tmp/crest-history-symlink-summary.out \
  || fail "symlink-escaped history summary failure message was not reported"
grep -q '^unchanged$' "${outside_summary_target}" \
  || fail "symlink-escaped history summary target was modified before rejection"

env \
  PATH="${fake_bin}:${PATH}" \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${report_dir}" \
  bash scripts/history-secret-audit.sh >/dev/null

[[ -f "${summary_file}" ]] || fail "clean history summary was not written"
grep -q '^status=passed$' "${summary_file}" \
  || fail "clean history summary must record status=passed"
grep -q '^findings=0$' "${summary_file}" \
  || fail "clean history summary must record zero findings"
grep -q '^remediation_required=false$' "${summary_file}" \
  || fail "clean history summary must record remediation_required=false"

findings_log="${test_root}/findings.log"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_FAKE_GITLEAKS_FINDINGS=true \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${report_dir}" \
  bash scripts/history-secret-audit.sh >"${findings_log}" 2>&1; then
  fail "history secret audit with findings should fail by default"
fi

grep -q '^status=findings$' "${summary_file}" \
  || fail "findings summary must record status=findings"
grep -q '^findings=2$' "${summary_file}" \
  || fail "findings summary must record finding count"
grep -q '^commits=2$' "${summary_file}" \
  || fail "findings summary must record commit count"
grep -q '^remediation_required=true$' "${summary_file}" \
  || fail "findings summary must record remediation_required=true"
grep -q '^delivery_options=rotate-credentials-and-use-clean-source-or-fresh-repository$' "${summary_file}" \
  || fail "findings summary must record delivery guidance"
grep -q '^rule=generic-api-key:2$' "${summary_file}" \
  || fail "findings summary must record rule distribution"
grep -q '^file=deploy/kubernetes/crest-ob-oracle-redis.yaml:1$' "${summary_file}" \
  || fail "findings summary must record redacted file distribution"
grep -q 'history secret audit found findings' "${findings_log}" \
  || fail "findings failure must keep the operator-facing failure message"

env \
  PATH="${fake_bin}:${PATH}" \
  CREST_FAKE_GITLEAKS_FINDINGS=true \
  CREST_GITLEAKS_BIN="${fake_bin}/gitleaks" \
  CREST_SECURITY_REPORT_DIR="${report_dir}" \
  CREST_HISTORY_SECRET_AUDIT_ALLOW_FINDINGS=true \
  bash scripts/history-secret-audit.sh >/dev/null

grep -q '^status=findings$' "${summary_file}" \
  || fail "allowed findings run must still preserve findings status in the summary"

echo "test-history-secret-audit: passed"
