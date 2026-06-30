import { useCache } from '@/hooks/web/useCache'
const { wsCache } = useCache()

// 根据当前数据计算界面可用状态
export const isDesktop = () => {
  const desktop = wsCache.get('app.desktop')
  return desktop
}
