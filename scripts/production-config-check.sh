#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "production-config-check: $*" >&2
  exit 1
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

overlay="${1:-deploy/kubernetes}"

command -v node >/dev/null 2>&1 || fail "missing required command: node"
command -v ruby >/dev/null 2>&1 || fail "missing required command: ruby"
if [[ "${CREST_K8S_SKIP_KUBECTL_DRY_RUN:-false}" != "true" ]]; then
  command -v kubectl >/dev/null 2>&1 || fail "missing required command: kubectl"
fi

node scripts/verify-kubernetes-production.mjs --strict-config "${overlay}"
echo "production-config-check: ${overlay} passed strict production config checks"
