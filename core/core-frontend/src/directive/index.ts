import { checkPermission } from './Permission'
import { vClickOutside } from './ClickOutside'
import type { App } from 'vue'
// 衔接当前组件交互和状态同步
export const installDirective = (app: App<Element>) => {
  app.directive('permission', checkPermission)
  app.directive('click-outside', vClickOutside)
}
