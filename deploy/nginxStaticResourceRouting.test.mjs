import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'

const configs = ['deploy/kubernetes/10-crest-nginx-configmap.yaml']

for (const configPath of configs) {
  test(`${configPath} proxies API static resources before asset regex locations`, () => {
    const config = readFileSync(configPath, 'utf8')

    assert.match(
      config,
      /location\s+\^~\s+\/api\/v1\/\s*\{/,
      'API routes must use a ^~ prefix location so uploaded images are proxied before static file regex locations'
    )
    assert.doesNotMatch(
      config,
      /location\s+\/api\/v1\/\s*\{/,
      'Plain API prefix locations can be overridden by .png/.jpg/.svg regex locations'
    )
    assert.match(
      config,
      /location\s+~\*\s+\\\.\(\?:js\|css\|png\|jpg\|jpeg\|gif\|svg\|ico\|webp\|woff2\?\|ttf\)\$/,
      'Static asset regex location should remain limited to frontend files after API proxy precedence is protected'
    )
    assert.match(
      config,
      /location\s+\^~\s+\/api\/v1\/actuator\/\s*\{[\s\S]*?return\s+404;/,
      'Frontend gateway must not expose backend actuator endpoints'
    )
    assert.match(
      config,
      /add_header\s+Referrer-Policy\s+"strict-origin-when-cross-origin"\s+always;/,
      'Frontend gateway should set a conservative Referrer-Policy'
    )
    assert.match(
      config,
      /add_header\s+Permissions-Policy\s+"camera=\(\), microphone=\(\), geolocation=\(\)"\s+always;/,
      'Frontend gateway should disable unused browser permissions'
    )
    assert.doesNotMatch(
      config,
      /proxy_set_header\s+(?:Host|X-Forwarded-Host)\s+\$(?:host|http_host);/,
      'Frontend gateway must not forward attacker-controlled Host headers to the backend'
    )
  })
}
