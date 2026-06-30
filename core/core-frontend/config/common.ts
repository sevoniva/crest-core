import path from 'path'
import fs from 'node:fs'
import { execSync } from 'node:child_process'
import { resolve } from 'path'
import Vue from '@vitejs/plugin-vue'
import eslintPlugin from 'vite-plugin-eslint'
import VueJsx from '@vitejs/plugin-vue-jsx'
import viteStylelint from 'vite-plugin-stylelint'
import {
  createStyleImportPlugin,
  ElementPlusSecondaryResolve
} from 'vite-plugin-style-import-secondary'
import VueI18nPlugin from '@intlify/unplugin-vue-i18n/vite'
import svgLoader from 'vite-svg-loader'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components-secondary/vite'
import { ElementPlusResolver } from 'unplugin-vue-components-secondary/resolvers'
const root = process.cwd()
const frontendPackage = JSON.parse(fs.readFileSync(path.resolve(root, 'package.json'), 'utf8')) as {
  version?: string
}
const frontendVersion = process.env.CREST_FRONTEND_VERSION || frontendPackage.version || 'unknown'
const resolveFrontendCommitId = () => {
  const commitId = process.env.CREST_FRONTEND_COMMIT_ID || process.env.GITHUB_SHA
  if (commitId) {
    return commitId
  }
  try {
    return execSync('git rev-parse --short=12 HEAD', { cwd: root }).toString().trim()
  } catch {
    return 'unknown'
  }
}
// 根据当前配置计算界面样式
const elementPlusSecondaryStyleDeps = (() => {
  const componentsDir = path.resolve(root, 'node_modules/element-plus-secondary/es/components')
  if (!fs.existsSync(componentsDir)) {
    return []
  }
  return fs
    .readdirSync(componentsDir, { withFileTypes: true })
    .filter(item => {
      const safeName = /^[A-Za-z0-9_-]+$/.test(item.name)
      return (
        safeName &&
        item.isDirectory() &&
        fs.existsSync(path.join(componentsDir, item.name, 'style/css.mjs')) // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
      )
    })
    .map(item => `element-plus-secondary/es/components/${item.name}/style/css`)
})()
const skipViteLint = process.env.CREST_SKIP_VITE_LINT === 'true'
const lintPlugins = skipViteLint
  ? []
  : [
      eslintPlugin({
        cache: false,
        include: [
          'src/**/*.ts',
          'src/**/*.tsx',
          'src/**/*.js',
          'src/**/*.vue',
          'src/*.ts',
          'src/*.js',
          'src/*.vue'
        ]
      }),
      viteStylelint()
    ]

// 衔接当前组件交互和状态同步
const patchElementPlusSecondaryTypes = (code: string) =>
  code
    .replace("import { inject } from 'vue';", "import { getCurrentInstance, inject } from 'vue';")
    .replace(
      'const customStyle = inject("$custom-style-filter", {});',
      'const customStyle = getCurrentInstance() ? inject("$custom-style-filter", {}) : {};'
    )

// 衔接当前组件交互和状态同步
const elementPlusSecondaryTypesPatch = () => ({
  name: 'crest-element-plus-secondary-types-patch',
  enforce: 'pre',
  config() {
    return {
      optimizeDeps: {
        esbuildOptions: {
          plugins: [
            {
              name: 'crest-element-plus-secondary-types-patch',
              setup(build) {
                build.onLoad(
                  { filter: /element-plus-secondary[\\/]es[\\/]utils[\\/]types\.mjs$/ },
                  async args => {
                    const fs = await import('node:fs/promises')
                    const code = await fs.readFile(args.path, 'utf-8')
                    return {
                      contents: patchElementPlusSecondaryTypes(code),
                      loader: 'js'
                    }
                  }
                )
              }
            }
          ]
        }
      }
    }
  },
  transform(code: string, id: string) {
    if (!id.replace(/\\/g, '/').endsWith('/element-plus-secondary/es/utils/types.mjs')) {
      return null
    }
    return {
      code: patchElementPlusSecondaryTypes(code),
      map: null
    }
  }
})

// 衔接当前组件交互和状态同步
export function pathResolve(dir: string) {
  // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal
  return resolve(root, '.', dir)
}
export default {
  base: './',
  define: {
    __CREST_FRONTEND_VERSION__: JSON.stringify(frontendVersion),
    __CREST_FRONTEND_COMMIT_ID__: JSON.stringify(resolveFrontendCommitId())
  },
  plugins: [
    elementPlusSecondaryTypesPatch(),
    Vue(),
    svgLoader({
      svgo: false,
      defaultImport: 'component' // or 'raw'
    }),
    VueJsx(),
    createStyleImportPlugin({
      resolves: [ElementPlusSecondaryResolve()],
      libs: [
        {
          libraryName: 'element-plus-secondary',
          esModule: true,
          resolveStyle: name => {
            return `element-plus-secondary/es/components/${name.substring(3)}/style/css`
          }
        }
      ]
    }),
    AutoImport({
      resolvers: [ElementPlusResolver()]
    }),
    Components({
      resolvers: [ElementPlusResolver()]
    }),
    VueI18nPlugin({
      runtimeOnly: false,
      compositionOnly: true,
      include: [resolve(__dirname, 'src/locales/**')]
    }),
    ...lintPlugins
  ],
  css: {
    preprocessorOptions: {
      less: {
        modifyVars: {
          hack: `true; @import (reference) "${path.resolve('src/style/variable.less')}";`
        },
        javascriptEnabled: true
      }
    }
  },
  resolve: {
    extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.less', '.css'],
    alias: [
      {
        find: '@',
        replacement: `${pathResolve('src')}`
      }
    ]
  },
  optimizeDeps: {
    include: [
      'vue',
      'vue-router',
      'vue-types',
      'element-plus-secondary/es/locale/lang/zh-cn',
      'element-plus-secondary/es/locale/lang/en',
      '@vueuse/core',
      'axios',
      ...elementPlusSecondaryStyleDeps
    ]
  }
}
