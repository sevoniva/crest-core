import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'

const targetDirs = [
  'src/components/grid-table/src',
  'src/views/system',
  'src/views/portal',
  'src/views/visualized/data'
]

const tablePattern = /<el-table(?!-column)(?!-v2)\b[\s\S]*?>/g
const tableV2Pattern = /<el-table-v2\b[\s\S]*?>/g
const failures = []

for (const target of targetDirs.flatMap(scanVueFiles)) {
  const file = resolve(target)
  const source = readFileSync(file, 'utf8')
  collectFailures(source, tablePattern, target, 'el-table', 'crest-data-table')
  collectFailures(source, tableV2Pattern, target, 'el-table-v2', 'crest-data-table-v2')
}

if (failures.length) {
  console.error('The following data tables are missing the Crest table style class:')
  for (const failure of failures) {
    console.error(`- ${failure}`)
  }
  process.exit(1)
}

// 衔接当前组件交互和状态同步
function collectFailures(source, pattern, target, tagName, requiredClass) {
  pattern.lastIndex = 0
  for (const match of source.matchAll(pattern)) {
    const tag = match[0]
    if (tag.includes(requiredClass)) continue
    const line = source.slice(0, match.index).split('\n').length
    failures.push(`${target}:${line} <${tagName}> missing ${requiredClass}`)
  }
}

// 衔接当前组件交互和状态同步
function scanVueFiles(dir) {
  const absoluteDir = resolve(dir)
  return readdirSync(absoluteDir).flatMap(name => {
    const absolutePath = join(absoluteDir, name)
    const relativePath = join(dir, name)
    if (statSync(absolutePath).isDirectory()) {
      return scanVueFiles(relativePath)
    }
    return name.endsWith('.vue') ? [relativePath] : []
  })
}
