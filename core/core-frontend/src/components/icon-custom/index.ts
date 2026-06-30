import { h } from 'vue'
import { ElIcon } from 'element-plus-secondary'
import Icon from './src/Icon.vue'
// 衔接当前组件交互和状态同步
const hIcon = (name: string) => {
  return h(ElIcon, null, {
    default: () => h(name)
  })
}
export { Icon, hIcon }
