#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "test-container-image-scan-report-dir: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

test_root="${CREST_TEST_CONTAINER_IMAGE_SCAN_REPORT_DIR:-.local/container-image-scan-report-dir-test-$$}"
rm -rf "${test_root}"
mkdir -p "${test_root}/bin"

for command_name in trivy docker node; do
  cat > "${test_root}/bin/${command_name}" <<'EOF'
#!/usr/bin/env bash
echo "unexpected container scanner invocation: $0 $*" >&2
exit 2
EOF
  chmod +x "${test_root}/bin/${command_name}"
done

run_with_fake_scanners() {
  env PATH="${test_root}/bin:${PATH}" CREST_TRIVY_BIN=trivy "$@"
}

outside_dir="/tmp/crest-container-report-dir-outside-$$"
rm -rf "${outside_dir}"
outside_log="${test_root}/outside.log"
if run_with_fake_scanners \
  CREST_CONTAINER_SCAN_REPORT_DIR="${outside_dir}" \
  bash scripts/container-image-scan.sh >"${outside_log}" 2>&1; then
  fail "container scan should reject report directories outside the repository"
fi
grep -q 'CREST_CONTAINER_SCAN_REPORT_DIR must stay inside the repository' "${outside_log}" \
  || fail "outside report directory failure must be explicit"
[[ ! -e "${outside_dir}" ]] \
  || fail "outside report directory must not be created"
if grep -q 'unexpected container scanner invocation' "${outside_log}"; then
  fail "unsafe report directory must fail before invoking scanners"
fi

escape_log="${test_root}/relative-escape.log"
if run_with_fake_scanners \
  CREST_CONTAINER_SCAN_REPORT_DIR="../crest-container-report-dir-escape" \
  bash scripts/container-image-scan.sh >"${escape_log}" 2>&1; then
  fail "container scan should reject relative parent escapes"
fi
grep -q 'refusing unsafe container scan report directory' "${escape_log}" \
  || fail "relative escape failure must be explicit"

root_log="${test_root}/root.log"
if run_with_fake_scanners \
  CREST_CONTAINER_SCAN_REPORT_DIR="/" \
  bash scripts/container-image-scan.sh >"${root_log}" 2>&1; then
  fail "container scan should reject root as the report directory"
fi
grep -q 'refusing unsafe container scan report directory' "${root_log}" \
  || fail "root report directory failure must be explicit"

cat > "${test_root}/bin/trivy" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${CREST_FAKE_TRIVY_ARGS_LOG}"
output=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --output)
      output="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
if [[ -n "${output}" ]]; then
  mkdir -p "$(dirname "${output}")"
  printf '{"SchemaVersion":2,"Results":[]}\n' > "${output}"
fi
exit 0
EOF
chmod +x "${test_root}/bin/trivy"

cat > "${test_root}/bin/node" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "scripts/container-report-check.mjs" ]]; then
  exit 0
fi
exec "${CREST_REAL_NODE}" "$@"
EOF
chmod +x "${test_root}/bin/node"

args_log="${test_root}/trivy-args.log"
run_with_fake_scanners \
  CREST_REAL_NODE="$(command -v node)" \
  CREST_FAKE_TRIVY_ARGS_LOG="${args_log}" \
  CREST_CONTAINER_SCAN_REPORT_DIR="${test_root}/reports" \
  CREST_CONTAINER_SCAN_IMAGES="frontend=crest-web:test,backend=crest-service:test" \
  CREST_TRIVY_DB_REPOSITORIES="ghcr.io/aquasecurity/trivy-db:2,public.ecr.aws/aquasecurity/trivy-db:2" \
  CREST_TRIVY_JAVA_DB_REPOSITORIES="ghcr.io/aquasecurity/trivy-java-db:1,public.ecr.aws/aquasecurity/trivy-java-db:1" \
  CREST_TRIVY_SKIP_JAVA_DB_UPDATE=true \
  CREST_TRIVY_SKIP_JAVA_ARTIFACTS=true \
  bash scripts/container-image-scan.sh >"${test_root}/positive.log" 2>&1

grep -q -- '--db-repository ghcr.io/aquasecurity/trivy-db:2' "${args_log}" \
  || fail "container scan must pass the first CREST_TRIVY_DB_REPOSITORIES entry to trivy"
grep -q -- '--db-repository public.ecr.aws/aquasecurity/trivy-db:2' "${args_log}" \
  || fail "container scan must pass the fallback CREST_TRIVY_DB_REPOSITORIES entry to trivy"
grep -q -- '--java-db-repository ghcr.io/aquasecurity/trivy-java-db:1' "${args_log}" \
  || fail "container scan must pass the first CREST_TRIVY_JAVA_DB_REPOSITORIES entry to trivy"
grep -q -- '--java-db-repository public.ecr.aws/aquasecurity/trivy-java-db:1' "${args_log}" \
  || fail "container scan must pass the fallback CREST_TRIVY_JAVA_DB_REPOSITORIES entry to trivy"
grep -q -- '--skip-java-db-update' "${args_log}" \
  || fail "container scan must pass CREST_TRIVY_SKIP_JAVA_DB_UPDATE to trivy"
grep -q -- '--pkg-types os' "${args_log}" \
  || fail "container scan must limit Trivy package types to OS packages when Java artifacts are skipped"
grep -q -- '--skip-files /opt/apps/app.jar' "${args_log}" \
  || fail "container scan must skip the backend Java artifact when requested"
grep -qF -- '--skip-files /opt/apps/drivers/*.jar' "${args_log}" \
  || fail "container scan must skip external Java driver artifacts when requested"
grep -q '"CrestJavaArtifactScanSkipped": true' "${test_root}/reports/trivy-backend.json" \
  || fail "container scan report must record skipped Java artifact scanning"
grep -q '"CrestJavaArtifactScanReason": "covered-by-maven-sbom-osv-sca"' "${test_root}/reports/trivy-backend.json" \
  || fail "container scan report must record why Java artifact scanning was skipped"
grep -q '"CrestTrivyPkgTypes": "os"' "${test_root}/reports/trivy-backend.json" \
  || fail "container scan report must record effective Trivy package types"
grep -q '"CrestContainerImage": "crest-service:test"' "${test_root}/reports/trivy-backend.json" \
  || fail "container scan report must record the original image reference"

cat > "${test_root}/bin/trivy" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
mode="${1:-}"
output=""
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --output)
      output="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
case "${mode}" in
  image)
    echo "simulated image scan export failure" >&2
    exit 1
    ;;
  rootfs)
    [[ -n "${output}" ]] || exit 2
    mkdir -p "$(dirname "${output}")"
    cat > "${output}" <<'JSON'
{
  "SchemaVersion": 2,
  "ArtifactName": "/tmp/fake-rootfs",
  "ArtifactType": "filesystem",
  "Results": []
}
JSON
    exit 0
    ;;
  *)
    exit 2
    ;;
esac
EOF
chmod +x "${test_root}/bin/trivy"

cat > "${test_root}/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
case "${1:-}" in
  create)
    echo "fake-container-id"
    ;;
  export)
    tmpdir="$(mktemp -d)"
    mkdir -p "${tmpdir}/etc"
    echo "fake" > "${tmpdir}/etc/os-release"
    /usr/bin/tar -C "${tmpdir}" -cf - .
    rm -rf "${tmpdir}"
    ;;
  rm)
    exit 0
    ;;
  *)
    echo "unexpected docker fallback invocation: $*" >&2
    exit 2
    ;;
esac
EOF
chmod +x "${test_root}/bin/docker"

fallback_report_dir="${test_root}/fallback-reports"
fallback_log="${test_root}/fallback.log"
run_with_fake_scanners \
  CREST_REAL_NODE="$(command -v node)" \
  CREST_CONTAINER_SCAN_REPORT_DIR="${fallback_report_dir}" \
  CREST_CONTAINER_SCAN_IMAGES="frontend=crest-web:test" \
  bash scripts/container-image-scan.sh >"${fallback_log}" 2>&1

grep -q 'rootfs fallback' "${fallback_log}" \
  || fail "container scan must explain rootfs fallback in logs"
grep -q '"CrestScanMode": "rootfs-fallback"' "${fallback_report_dir}/trivy-frontend.json" \
  || fail "rootfs fallback report must record CrestScanMode"
grep -q '"CrestContainerImage": "crest-web:test"' "${fallback_report_dir}/trivy-frontend.json" \
  || fail "rootfs fallback report must record the original image reference"

echo "test-container-image-scan-report-dir: passed"
