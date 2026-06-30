#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-clean-source-release: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_CLEAN_SOURCE_DIR:-.local/clean-source-release-test-$$}"
fake_bin="${test_root}/bin"
output_dir="${test_root}/release-source"
security_dir="${test_root}/security"
history_report="${security_dir}/gitleaks-history.json"
clean_scan_report="${security_dir}/gitleaks-clean-source.json"

rm -rf "${test_root}"
mkdir -p "${fake_bin}" "${security_dir}"

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
printf '[]\n' > "${report_path}"
EOF
chmod +x "${fake_bin}/gitleaks"

cat > "${history_report}" <<'EOF'
[
  {"RuleID":"generic-api-key","File":"deploy/kubernetes/crest-ob-oracle-redis.yaml"},
  {"RuleID":"generic-api-key","File":"core/core-backend/src/main/resources/application.yml"}
]
EOF

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="." \
  CREST_CLEAN_SOURCE_NAME="unsafe-clean-source" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-unsafe-dir.out 2>&1; then
  fail "clean-source release unexpectedly accepted the repository root as output dir"
fi
grep -q "CREST_CLEAN_SOURCE_OUTPUT_DIR is too broad to overwrite" /tmp/crest-clean-source-unsafe-dir.out \
  || fail "unsafe clean-source output dir failure message was not reported"

outside_parent="/tmp/crest-clean-source-missing-parent-$$"
rm -rf "${outside_parent}"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${outside_parent}/release-source" \
  CREST_CLEAN_SOURCE_NAME="outside-clean-source" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-outside-dir.out 2>&1; then
  fail "clean-source release unexpectedly accepted an outside-repository output dir"
fi
grep -q "CREST_CLEAN_SOURCE_OUTPUT_DIR must stay inside the repository" /tmp/crest-clean-source-outside-dir.out \
  || fail "outside clean-source output dir failure message was not reported"
[[ ! -e "${outside_parent}" ]] \
  || fail "outside clean-source output dir check must not create directories before rejecting"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${output_dir}" \
  CREST_CLEAN_SOURCE_NAME="outside-scan-report" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="/tmp/crest-clean-source-scan-report-outside.json" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-outside-scan-report.out 2>&1; then
  fail "clean-source release unexpectedly accepted an outside-repository scan report"
fi
grep -q "CREST_CLEAN_SOURCE_GITLEAKS_REPORT must stay inside the repository" /tmp/crest-clean-source-outside-scan-report.out \
  || fail "outside clean-source scan report failure message was not reported"

outside_symlink_target="/tmp/crest-clean-source-output-link-target-$$"
rm -rf "${outside_symlink_target}"
mkdir -p "${outside_symlink_target}"
printf 'keep\n' > "${outside_symlink_target}/sentinel.txt"
ln -s "${outside_symlink_target}" "${test_root}/release-source-link"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${test_root}/release-source-link" \
  CREST_CLEAN_SOURCE_NAME="symlink-output" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-output-symlink.out 2>&1; then
  fail "clean-source release unexpectedly accepted a symlink output dir"
fi
grep -q "CREST_CLEAN_SOURCE_OUTPUT_DIR must not be a symlink" /tmp/crest-clean-source-output-symlink.out \
  || fail "symlink clean-source output dir failure message was not reported"
grep -q '^keep$' "${outside_symlink_target}/sentinel.txt" \
  || fail "symlink output dir rejection must not modify the symlink target"
[[ ! -e "${outside_symlink_target}/symlink-output" ]] \
  || fail "symlink output dir rejection must not create files under the symlink target"

outside_report_target="/tmp/crest-clean-source-scan-report-link-target-$$.json"
rm -f "${outside_report_target}"
printf 'keep\n' > "${outside_report_target}"
ln -s "${outside_report_target}" "${test_root}/scan-report-link.json"
if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${output_dir}" \
  CREST_CLEAN_SOURCE_NAME="symlink-scan-report" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${test_root}/scan-report-link.json" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-scan-report-symlink.out 2>&1; then
  fail "clean-source release unexpectedly accepted a symlink scan report"
fi
grep -q "CREST_CLEAN_SOURCE_GITLEAKS_REPORT must not be a symlink" /tmp/crest-clean-source-scan-report-symlink.out \
  || fail "symlink clean-source scan report failure message was not reported"
grep -q '^keep$' "${outside_report_target}" \
  || fail "symlink scan report rejection must not modify the symlink target"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${output_dir}" \
  CREST_CLEAN_SOURCE_NAME="../escape" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-unsafe-name.out 2>&1; then
  fail "clean-source release unexpectedly accepted an unsafe release name"
fi
grep -q "CREST_CLEAN_SOURCE_NAME must be a single safe directory name" /tmp/crest-clean-source-unsafe-name.out \
  || fail "unsafe clean-source release name failure message was not reported"

env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${output_dir}" \
  CREST_CLEAN_SOURCE_NAME="crest-core-test-source" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH="clean-source" \
  CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS="rotated-before-delivery" \
  CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES="initial-admin-password,application-encryption-key" \
  CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true \
  bash scripts/create-clean-source-release.sh >/dev/null

summary="${output_dir}/crest-core-test-source.summary.txt"
archive="${output_dir}/crest-core-test-source.tar.gz"
[[ -f "${summary}" ]] || fail "summary was not written"
[[ -f "${archive}" ]] || fail "archive was not written"
grep -q '^secret_scan_findings=0$' "${summary}" \
  || fail "summary must record clean source secret scan findings"
grep -Eq '^generated_at_utc=[0-9]{8}T[0-9]{6}Z$' "${summary}" \
  || fail "summary must record clean source generation timestamp"
grep -q "^version=$(tr -d '[:space:]' < VERSION)$" "${summary}" \
  || fail "summary must record clean source version"
grep -Eq '^source_branch=[^[:space:]]+$' "${summary}" \
  || fail "summary must record clean source branch"
grep -Eq '^source_commit=[0-9a-f]{40}$' "${summary}" \
  || fail "summary must record clean source git commit"
grep -Eq '^source_file_count=[1-9][0-9]*$' "${summary}" \
  || fail "summary must record clean source file count"
grep -q '^history_findings_remaining=2$' "${summary}" \
  || fail "summary must record history findings count from report"
grep -Eq '^history_scan_report_sha256=[0-9a-f]{64}$' "${summary}" \
  || fail "summary must record history scan report digest"
grep -q '^history_delivery_path=clean-source$' "${summary}" \
  || fail "summary must record clean-source delivery path"
grep -q '^credential_rotation_status=rotated-before-delivery$' "${summary}" \
  || fail "summary must record credential rotation status"
tar -tzf "${archive}" | grep -q 'SOURCE_MANIFEST.txt' \
  || fail "archive must contain SOURCE_MANIFEST.txt"
tar -xOf "${archive}" crest-core-test-source/SOURCE_MANIFEST.txt | grep -Eq '^history_scan_report_sha256=[0-9a-f]{64}$' \
  || fail "SOURCE_MANIFEST.txt must record history scan report digest"
if tar -tzf "${archive}" | grep -Eq '(^|/)(\.git|reports|\.local|node_modules|target|dist)(/|$)'; then
  fail "archive must not contain forbidden generated or private paths"
fi

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${output_dir}" \
  CREST_CLEAN_SOURCE_NAME="crest-core-test-source-negative" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH="clean-source" \
  CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS="not-recorded" \
  CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-negative.out 2>&1; then
  fail "clean-source delivery with history findings and no rotation unexpectedly passed"
fi
grep -q 'requires CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS=rotated-before-delivery' /tmp/crest-clean-source-negative.out \
  || fail "missing credential rotation failure message was not reported"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${output_dir}" \
  CREST_CLEAN_SOURCE_NAME="crest-core-test-source-clean-history-negative" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH="clean-history" \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-clean-history-negative.out 2>&1; then
  fail "clean-history delivery with remaining findings unexpectedly passed"
fi
grep -q 'clean-history delivery requires history_findings_remaining=0' /tmp/crest-clean-source-clean-history-negative.out \
  || fail "clean-history failure message was not reported"

if env \
  PATH="${fake_bin}:${PATH}" \
  CREST_CLEAN_SOURCE_OUTPUT_DIR="${output_dir}" \
  CREST_CLEAN_SOURCE_NAME="crest-core-test-source-affected-negative" \
  CREST_CLEAN_SOURCE_GITLEAKS_REPORT="${clean_scan_report}" \
  CREST_CLEAN_SOURCE_HISTORY_SCAN_REPORT="${history_report}" \
  CREST_CLEAN_SOURCE_HISTORY_DELIVERY_PATH="fresh-repository" \
  CREST_CLEAN_SOURCE_CREDENTIAL_ROTATION_STATUS="rotated-before-delivery" \
  CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES="not-recorded" \
  CREST_CLEAN_SOURCE_REQUIRE_CREDENTIAL_ROTATION=true \
  bash scripts/create-clean-source-release.sh >/tmp/crest-clean-source-affected-negative.out 2>&1; then
  fail "fresh-repository delivery with history findings and no affected credential classes unexpectedly passed"
fi
grep -q 'requires CREST_CLEAN_SOURCE_AFFECTED_CREDENTIAL_CLASSES' /tmp/crest-clean-source-affected-negative.out \
  || fail "missing affected credential classes failure message was not reported"

echo "test-clean-source-release: passed"
