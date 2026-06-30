// 根据当前数据计算界面可用状态
export const isReadOnlyRoute = (path = '', name) =>
  path === '/preview' ||
  path === '/previewShow' ||
  path === '/dashboardPreview' ||
  path === '/chart-view' ||
  path.startsWith('/link/') ||
  name === 'link'

// 衔接当前组件交互和状态同步
export const shouldBypassAuthenticatedMenuGuard = route => {
  return isReadOnlyRoute(route?.path, route?.name)
}
