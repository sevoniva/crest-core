#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: scripts/prepare-release.sh <version>"
  echo
  echo "Examples:"
  echo "  scripts/prepare-release.sh 1.0.1"
  echo "  scripts/prepare-release.sh v1.0.1"
}

if [[ $# -ne 1 ]]; then
  usage >&2
  exit 64
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

target_version="${1#v}"
if [[ ! "${target_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Version must use x.y.z format." >&2
  exit 64
fi

target_tag="v${target_version}"
current_version="$(tr -d '[:space:]' < VERSION)"
current_version="${current_version#v}"
current_tag="v${current_version}"

if [[ "${target_version}" == "${current_version}" ]]; then
  echo "Already on ${target_tag}."
  exit 0
fi

replace_literal() {
  local file="$1"
  local old_value="$2"
  local new_value="$3"

  [[ -f "${file}" ]] || return 0
  perl -0pi -e 'BEGIN { ($old, $new) = @ARGV; splice @ARGV, 0, 2 } s/\Q$old\E/$new/g' \
    "${old_value}" "${new_value}" "${file}"
}

replace_package_version() {
  local file="$1"

  perl -0pi -e 'BEGIN { ($old, $new) = @ARGV; splice @ARGV, 0, 2 } s/("version"\s*:\s*)"\Q$old\E"/$1"$new"/g' \
    "${current_version}" "${target_version}" "${file}"
}

pom_files=(
  "pom.xml"
  "sdk/pom.xml"
  "sdk/common/pom.xml"
  "sdk/api/pom.xml"
  "sdk/api/api-base/pom.xml"
  "sdk/api/api-permissions/pom.xml"
  "sdk/api/api-sync/pom.xml"
  "sdk/extensions/pom.xml"
  "sdk/extensions/extensions-datasource/pom.xml"
  "sdk/extensions/extensions-view/pom.xml"
  "sdk/extensions/extensions-datafilling/pom.xml"
  "core/pom.xml"
  "core/core-backend/pom.xml"
  "core/core-frontend/pom.xml"
)

tag_files=(
  ".github/ISSUE_TEMPLATE/bug---.md"
  ".github/workflows/docker-publish.yml"
  "README.md"
  "deploy/kubernetes/08-crest-service-deployment.yaml"
  "deploy/kubernetes/11-crest-web-deployment.yaml"
  "deploy/kubernetes/README.md"
  "docs/README.md"
  "installer/README.md"
)

printf '%s\n' "${target_version}" > VERSION

for file in "${pom_files[@]}"; do
  replace_literal "${file}" "${current_version}" "${target_version}"
done

replace_package_version "core/core-frontend/package.json"

for file in "${tag_files[@]}"; do
  replace_literal "${file}" "${current_tag}" "${target_tag}"
done

echo "Prepared ${target_tag} from ${current_tag}."
echo "Next: add docs/release/${target_tag}.md, update CHANGELOG.md, regenerate init SQL if database assets changed."
