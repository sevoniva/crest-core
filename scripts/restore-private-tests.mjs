import { copyFileSync, existsSync, mkdirSync, readdirSync, statSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..')
const privateRoot = join(repoRoot, 'private-tests')
const sourceRoot = join(privateRoot, 'core')
const targetRoot = join(repoRoot, 'core')

if (!existsSync(sourceRoot)) {
  console.error('未找到私有测试仓库内容，请先执行：git submodule update --init private-tests')
  process.exit(1)
}

let copied = 0

// 按私有测试仓库中的原始目录结构恢复测试文件。
const restoreDirectory = (sourceDir, targetDir) => {
  for (const entry of readdirSync(sourceDir)) {
    const sourcePath = join(sourceDir, entry)
    const targetPath = join(targetDir, entry)
    const sourceStat = statSync(sourcePath)

    if (sourceStat.isDirectory()) {
      restoreDirectory(sourcePath, targetPath)
      continue
    }

    mkdirSync(dirname(targetPath), { recursive: true })
    copyFileSync(sourcePath, targetPath)
    copied += 1
  }
}

restoreDirectory(sourceRoot, targetRoot)
console.log(`已恢复 ${copied} 个私有测试文件。`)
