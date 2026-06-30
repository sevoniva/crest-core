#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "release-guard: $*" >&2
  exit 1
}

info() {
  echo "release-guard: $*"
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

current_version="$(tr -d '[:space:]' < VERSION)"
[[ "${current_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "VERSION must use x.y.z format."

root_pom_version="$(sed -n 's/.*<version>\([^<]*\)<\/version>.*/\1/p' pom.xml | head -n 1)"
crest_property_version="$(sed -n 's/.*<crest.version>\([^<]*\)<\/crest.version>.*/\1/p' pom.xml | head -n 1)"
frontend_version="$(sed -n 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' core/core-frontend/package.json | head -n 1)"

[[ "${root_pom_version}" == "${current_version}" ]] || fail "pom.xml version ${root_pom_version} does not match VERSION ${current_version}."
[[ "${crest_property_version}" == "${current_version}" ]] || fail "pom.xml crest.version ${crest_property_version} does not match VERSION ${current_version}."
[[ "${frontend_version}" == "${current_version}" ]] || fail "frontend package version ${frontend_version} does not match VERSION ${current_version}."

[[ -f "docs/release/v${current_version}.md" ]] || fail "Missing docs/release/v${current_version}.md."
grep -Eq "^## v${current_version}[[:space:]-]" CHANGELOG.md || fail "CHANGELOG.md is missing v${current_version} section."

node scripts/generate-ob-oracle-init-schema.mjs --check
node scripts/verify-kubernetes-production.mjs deploy/kubernetes

target_branch="${GITHUB_BASE_REF:-${GITHUB_REF_NAME:-}}"
source_branch="${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-}}"

case "${target_branch}" in
  main)
    case "${source_branch}" in
      dev | release/* | hotfix/* | fix/* | chore/* | ci/* | docs/* | main) ;;
      feat/*) fail "Feature branches must target dev first, not main." ;;
      *) fail "PRs to main must come from dev, release/*, hotfix/*, fix/*, chore/*, ci/*, or docs/*." ;;
    esac
    ;;
  dev)
    case "${source_branch}" in
      feat/* | fix/* | chore/* | ci/* | docs/* | refactor/* | test/* | security/* | hotfix/* | dev) ;;
      *) fail "PRs to dev must use feat/*, fix/*, chore/*, ci/*, docs/*, refactor/*, test/*, security/*, or hotfix/*." ;;
    esac
    ;;
  release/*)
    case "${source_branch}" in
      hotfix/* | fix/* | chore/* | ci/* | docs/* | release/*) ;;
      feat/*) fail "Feature branches must not target release branches." ;;
      *) fail "PRs to release branches must use hotfix/*, fix/*, chore/*, ci/*, docs/*, or release/*." ;;
    esac
    ;;
esac

changed_files=""
if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
  git fetch --no-tags --prune origin "${GITHUB_BASE_REF}:${GITHUB_BASE_REF}" >/dev/null 2>&1 || true
  if git rev-parse --verify "${GITHUB_BASE_REF}" >/dev/null 2>&1; then
    changed_files="$(git diff --name-only "${GITHUB_BASE_REF}...HEAD")"
  else
    changed_files="$(git diff --name-only "origin/${GITHUB_BASE_REF}...HEAD")"
  fi
elif [[ -n "${EVENT_BEFORE:-}" && ! "${EVENT_BEFORE}" =~ ^0+$ ]]; then
  changed_files="$(git diff --name-only "${EVENT_BEFORE}...HEAD" || true)"
fi

db_change=0
while IFS= read -r file; do
  [[ -n "${file}" ]] || continue
  case "${file}" in
    core/core-backend/src/main/resources/db/* | installer/init-sql/* | *.sql)
      db_change=1
      ;;
  esac
done <<< "${changed_files}"

if [[ "${db_change}" == "1" ]]; then
  node scripts/generate-ob-oracle-init-schema.mjs --check
fi

info "passed for v${current_version}"
