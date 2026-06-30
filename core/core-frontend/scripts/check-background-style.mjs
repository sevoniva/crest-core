import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { test } from 'node:test'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

const source = relativePath => readFileSync(path.join(root, relativePath), 'utf8')

const backgroundStyleSource = source('src/utils/backgroundStyleUtils.ts')
const styleSource = source('src/utils/style.ts')
const screenSource = source('src/custom-component/screen/Component.vue')
const tabsSource = source('src/custom-component/tabs/Component.vue')

test('background style utility maps uploaded images to independent CSS properties', () => {
  assert.match(backgroundStyleSource, /function toCssUrl\(url: string\): string/)
  assert.match(backgroundStyleSource, /export function setBackgroundImageStyle/)
  assert.match(backgroundStyleSource, /style\.backgroundImage = toCssUrl\(url\)/)
  assert.match(backgroundStyleSource, /style\.backgroundRepeat = 'no-repeat'/)
  assert.match(backgroundStyleSource, /style\.backgroundPosition = 'center'/)
  assert.match(backgroundStyleSource, /style\.backgroundSize = '100% 100%'/)
})

test('background style utility no longer uses background shorthand for uploaded images', () => {
  assert.doesNotMatch(backgroundStyleSource, /background:\s*`url\(\$\{imgUrlTrans/)
  assert.doesNotMatch(backgroundStyleSource, /style\[['"]background['"]\]\s*=\s*`url/)
  assert.match(backgroundStyleSource, /setBackgroundImageStyle\(style, outerImage!\)/)
  assert.match(backgroundStyleSource, /setBackgroundImageStyle\(style, outerImage\)/)
})

test('canvas background image styles use the shared static-resource helper', () => {
  assert.match(styleSource, /import \{ setBackgroundImageStyle \}/)
  assert.doesNotMatch(styleSource, /import \{ imgUrlTrans \}/)
  assert.match(styleSource, /setBackgroundImageStyle\(result, commonBackground\.outerImage\)/)
  assert.match(styleSource, /setBackgroundImageStyle\(style, background\)/)
  assert.match(styleSource, /function clearBackgroundStyle\(style\)/)
  assert.doesNotMatch(styleSource, /style\[['"]background['"]\]\s*=\s*`url/)
  assert.doesNotMatch(styleSource, /result\[['"]background['"]\]\s*=\s*`url/)
})

test('tab title background image styles use the shared static-resource helper', () => {
  for (const itemSource of [screenSource, tabsSource]) {
    assert.match(itemSource, /import \{ setBackgroundImageStyle \}/)
    assert.doesNotMatch(itemSource, /import \{ imgUrlTrans \}/)
    assert.match(itemSource, /setBackgroundImageStyle\(style, outerImage\)/)
    assert.doesNotMatch(itemSource, /style\[['"]background['"]\]\s*=\s*`url/)
  }
})
