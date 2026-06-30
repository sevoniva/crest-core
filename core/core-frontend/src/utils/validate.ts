// 根据当前数据计算界面可用状态
export function isExternal(path) {
  return /^(https?:|mailto:|tel:)/.test(path) || /^(http?:|mailto:|tel:)/.test(path)
}

// 根据当前数据计算界面可用状态
export function validUsername(str) {
  const valid_map = ['admin', 'cyw']
  return valid_map.indexOf(str.trim()) >= 0
}

export const PHONE_REGEX = '^1[3|4|5|7|8][0-9]{9}$'

export const EMAIL_REGEX = '^[a-zA-Z0-9_._-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$'
