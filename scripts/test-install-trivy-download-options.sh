#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-install-trivy-download-options: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_INSTALL_TRIVY_OPTIONS_DIR:-.local/install-trivy-options-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/bin" "${test_root}/install"

cat > "${test_root}/bin/curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf 'curl %q\n' "$@" >> "${CREST_FAKE_CURL_LOG}"

output=""
url=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -o)
      output="$2"
      shift 2
      ;;
    http*)
      url="$1"
      shift
      ;;
    *)
      shift
      ;;
  esac
done

[[ -n "${output}" ]] || exit 2
mkdir -p "$(dirname "${output}")"
if [[ "${url}" == *checksums.txt ]]; then
  payload="${CREST_FAKE_TRIVY_ASSET_PAYLOAD}"
  checksum="$(printf '%s' "${payload}" | shasum -a 256 | awk '{print $1}')"
  for asset in \
    trivy_0.71.2_Linux-64bit.tar.gz \
    trivy_0.71.2_Linux-ARM64.tar.gz \
    trivy_0.71.2_macOS-64bit.tar.gz \
    trivy_0.71.2_macOS-ARM64.tar.gz; do
    printf '%s  %s\n' "${checksum}" "${asset}"
  done > "${output}"
else
  printf '%s' "${CREST_FAKE_TRIVY_ASSET_PAYLOAD}" > "${output}"
fi
EOF

cat > "${test_root}/bin/tar" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
target_dir=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    -C)
      target_dir="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
[[ -n "${target_dir}" ]] || exit 2
cat > "${target_dir}/trivy" <<'SCRIPT'
#!/usr/bin/env bash
echo fake-trivy
SCRIPT
chmod +x "${target_dir}/trivy"
EOF

chmod +x "${test_root}/bin/curl" "${test_root}/bin/tar"

log_file="${test_root}/curl.log"
env \
  PATH="${test_root}/bin:${PATH}" \
  CREST_FAKE_CURL_LOG="${log_file}" \
  CREST_FAKE_TRIVY_ASSET_PAYLOAD="fake-trivy-archive" \
  CREST_TRIVY_INSTALL_DIR="${test_root}/install" \
  CREST_DOWNLOAD_CONNECT_TIMEOUT_SECONDS=3 \
  CREST_DOWNLOAD_MAX_TIME_SECONDS=7 \
  CREST_DOWNLOAD_SPEED_TIME_SECONDS=4 \
  CREST_DOWNLOAD_SPEED_LIMIT_BYTES=5 \
  CREST_DOWNLOAD_PROXY="http://proxy.example:8080" \
  bash scripts/install-trivy.sh >/dev/null

"${test_root}/install/trivy" | grep -q '^fake-trivy$' \
  || fail "fake trivy binary should be installed"

grep -q -- '--connect-timeout' "${log_file}" \
  || fail "curl args must include connect timeout"
grep -q -- '3' "${log_file}" \
  || fail "curl args must include configured connect timeout value"
grep -q -- '--max-time' "${log_file}" \
  || fail "curl args must include max time"
grep -q -- '7' "${log_file}" \
  || fail "curl args must include configured max time value"
grep -q -- '--speed-time' "${log_file}" \
  || fail "curl args must include speed time"
grep -q -- '--speed-limit' "${log_file}" \
  || fail "curl args must include speed limit"
grep -q -- '--proxy' "${log_file}" \
  || fail "curl args must include proxy when configured"
grep -q -- 'http://proxy.example:8080' "${log_file}" \
  || fail "curl args must include configured proxy value"

echo "test-install-trivy-download-options: passed"
