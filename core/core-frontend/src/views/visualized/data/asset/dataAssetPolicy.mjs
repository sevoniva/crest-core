export const assetTypeOptions = [
  { label: '全部资产', value: 'all' },
  { label: '数据源', value: 'datasource' },
  { label: '数据表', value: 'table' },
  { label: '数据集', value: 'dataset' },
  { label: '图表', value: 'chart' },
  { label: '仪表盘', value: 'panel' },
  { label: '数据大屏', value: 'screen' },
  { label: '分享链接', value: 'share' }
]

export const governanceFilterOptions = [
  { label: '全部状态', value: 'all' },
  { label: '已认证', value: 'certified' },
  { label: '推荐使用', value: 'recommended' },
  { label: '已废弃', value: 'deprecated' }
]

// 将不同来源的布尔值归一为布尔真值
const truthy = value => value === true || value === 1 || value === '1'

// 归一化资产治理状态，废弃状态优先级最高
export const normalizeGovernanceStatus = asset => {
  const deprecated = truthy(asset?.deprecated)
  if (deprecated) {
    return {
      certified: false,
      recommended: false,
      deprecated: true
    }
  }
  return {
    certified: truthy(asset?.certified),
    recommended: truthy(asset?.recommended),
    deprecated: false
  }
}

// 返回当前资产可编辑的治理状态
export const editableGovernanceStatus = asset => {
  if (asset?.assetType !== 'dataset') {
    return {
      certified: false,
      recommended: false,
      deprecated: false
    }
  }
  return normalizeGovernanceStatus(asset)
}

// 根据用户切换的字段生成下一份治理状态
export const updateGovernanceStatus = (current, field, value) => {
  const next = normalizeGovernanceStatus(current)
  const checked = truthy(value)
  if (field === 'deprecated') {
    return {
      certified: false,
      recommended: false,
      deprecated: checked
    }
  }
  if (field === 'certified') {
    return {
      certified: checked,
      recommended: next.recommended,
      deprecated: checked ? false : next.deprecated
    }
  }
  if (field === 'recommended') {
    return {
      certified: next.certified,
      recommended: checked,
      deprecated: checked ? false : next.deprecated
    }
  }
  return next
}

// 生成资产列表中展示的治理状态徽标
export const assetGovernanceBadges = asset => {
  const badges = []
  const status = normalizeGovernanceStatus(asset)
  if (status.deprecated) {
    badges.push({ label: '已废弃', type: 'danger', className: 'badge-deprecated' })
  }
  if (status.certified) {
    badges.push({ label: '已认证', type: 'success', className: 'badge-certified' })
  }
  if (status.recommended) {
    badges.push({ label: '推荐使用', type: 'primary', className: 'badge-recommended' })
  }
  return badges
}

// 生成门户资源展示用的可信状态徽标
export const portalTrustBadges = resource => {
  const badges = []
  const status = normalizeGovernanceStatus(resource)
  if (status.deprecated) {
    badges.push({ label: '已废弃', type: 'danger', className: 'badge-deprecated' })
  }
  if (status.certified) {
    badges.push({ label: '已认证', type: 'success', className: 'badge-certified' })
  }
  if (status.recommended) {
    badges.push({ label: '推荐使用', type: 'primary', className: 'badge-recommended' })
  }
  return badges
}

// 判断当前用户是否可编辑资产基础信息
export const canEditProfile = asset => asset?.canManage === true || asset?.canManage === 1

// 判断当前用户是否可编辑数据集治理状态
export const canEditGovernance = asset => asset?.assetType === 'dataset' && canEditProfile(asset)

// 将治理状态筛选值转换为接口请求参数
export const governanceFilterToRequest = filter => {
  if (filter === 'certified') {
    return { certified: true }
  }
  if (filter === 'recommended') {
    return { recommended: true }
  }
  if (filter === 'deprecated') {
    return { deprecated: true }
  }
  return {}
}
