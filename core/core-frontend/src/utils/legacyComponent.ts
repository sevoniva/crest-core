const LEGACY_COMPONENT_NAME_MAP: Record<string, string> = {
  DeDecoration: 'Decoration',
  DeFrame: 'Frame',
  DeGraphical: 'Graphical',
  DeScreen: 'Screen',
  DeStreamMedia: 'StreamMedia',
  DeTabs: 'Tabs',
  DeTimeClock: 'TimeClock',
  DeVideo: 'Video'
}

// 历史模板中的 De* 组件名在当前运行时统一映射为新组件名
export function normalizeLegacyComponentName(name?: string) {
  if (!name) {
    return name
  }
  return LEGACY_COMPONENT_NAME_MAP[name] || name
}

// 递归处理组合、分组和标签页中的历史组件结构
export function normalizeLegacyComponent(component) {
  if (!component || typeof component !== 'object') {
    return component
  }

  component.component = normalizeLegacyComponentName(component.component)
  component.innerType = normalizeLegacyComponentName(component.innerType)

  if (component.component === 'Tabs') {
    component.propValue?.forEach(tabItem => {
      normalizeLegacyComponentData(tabItem.componentData)
    })
  } else if (component.component === 'Group') {
    normalizeLegacyComponentData(component.propValue)
  }

  return component
}

// 批量规范化模板或画布中的历史组件数据
export function normalizeLegacyComponentData<T>(componentData: T[]): T[] {
  componentData?.forEach(component => normalizeLegacyComponent(component))
  return componentData
}
