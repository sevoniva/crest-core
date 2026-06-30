import type { App } from 'vue'

const components = ['circle-shape']

// 更新当前配置并同步相关状态
export const setupCustomComponent = (app: App<Element>) => {
  components.forEach(key => {
    app.component(key, () => import(`@/custom-component/${key}/Component.vue`))
    app.component(key + '-attr', () => import(`@/custom-component/${key}/Attr.vue`))
  })
}
