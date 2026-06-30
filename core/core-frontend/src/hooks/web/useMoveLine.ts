import { ref, onBeforeUnmount, onMounted } from 'vue'
import { useCache } from '@/hooks/web/useCache'
import { useEmitt } from '@/hooks/web/useEmitt'

// 支持保存拖拽宽度的侧边栏类型
type Sidebar = 'DATASET' | 'DASHBOARD' | 'DATASOURCE' | 'DATA-FILLING'

// 创建侧边栏拖拽分隔线，并持久化拖拽后的宽度
export const useMoveLine = (type: Sidebar) => {
  // 本地缓存记录各侧边栏上次拖拽后的宽度
  const { wsCache } = useCache('localStorage')
  // 当前侧边栏宽度，默认回落到 280
  const width = ref(wsCache.get(type) || 280)
  wsCache.set('current-collapse_bar', width.value)

  // 鼠标按下时进入拖拽态并绑定全局事件
  const getCoordinates = () => {
    if (document.querySelector('.sidebar-move-line')) {
      document.querySelector('.sidebar-move-line').className = 'sidebar-move-line dragging'
    }
    document.addEventListener('mousemove', setCoordinates)
    document.addEventListener('mouseup', cancelEvent)
    document.querySelector('body').style['user-select'] = 'none'
  }

  // 同步当前宽度给缓存和折叠栏监听者
  const setCollapseBarWidth = () => {
    wsCache.set('current-collapse_bar', width.value)
    useEmitt().emitter.emit('current-collapse_bar')
  }

  // 根据鼠标横坐标更新侧边栏宽度，并限制在允许区间内
  const setCoordinates = (e: MouseEvent) => {
    const x = e.clientX
    if (x > 401 || x < 279) {
      width.value = Math.max(Math.min(401, x), 279)
      ele.style.left = width.value - 5 + 'px'
      setCollapseBarWidth()
      return
    }
    ele.style.left = width.value - 5 + 'px'
    width.value = x
    setCollapseBarWidth()
  }

  // 结束拖拽并移除全局鼠标移动监听
  const cancelEvent = () => {
    if (document.querySelector('.sidebar-move-line')) {
      document.querySelector('.sidebar-move-line').className = 'sidebar-move-line'
    }
    document.querySelector('body').style['user-select'] = 'auto'
    wsCache.set(type, width.value)
    document.removeEventListener('mousemove', setCoordinates)
  }

  // 外层容器引用，用于挂载分隔线元素
  const node = ref()

  // 拖拽分隔线 DOM 元素
  const ele = document.createElement('div')
  ele.className = 'sidebar-move-line'
  ele.style.top = '0'
  ele.style.left = width.value - 5 + 'px'
  ele.addEventListener('mousedown', getCoordinates)

  onMounted(() => {
    ;(node.value?.$el || node.value)?.appendChild(ele)
  })

  onBeforeUnmount(() => {
    cancelEvent()
    ele.removeEventListener('mousedown', getCoordinates)
    ;(node.value?.$el || node.value)?.removeChild?.(ele)
    width.value = null
  })

  return {
    width,
    node
  }
}
