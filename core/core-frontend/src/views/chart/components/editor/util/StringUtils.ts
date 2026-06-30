// 将标准模板占位格式替换为目标内容，例如 $panelName$
export function pdfTemplateReplaceAll(content, source, target) {
  return String(content).split(`$${source}$`).join(target)
}

/**
 * 生成指定长度范围内的随机字符串
 */
export function randomRange(min, max) {
  let returnStr = ''
  const range = max ? Math.round(Math.random() * (max - min)) + min : min
  const charStr = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'

  for (let i = 0; i < range; i++) {
    const index = Math.round(Math.random() * (charStr.length - 1))
    returnStr += charStr.substring(index, index + 1)
  }
  return returnStr
}

/**
 * 判断目标值是否等于任一候选值
 */
export function equalsAny(target, ...sources) {
  for (let i = 0; i < sources.length; i++) {
    if (target === sources[i]) {
      return true
    }
  }
  return false
}

/**
 * 判断目标字符串是否包含任一候选片段
 */
export function includesAny(target, ...sources) {
  if (!target || !sources) {
    return false
  }
  for (let i = 0; i < sources.length; i++) {
    if (target.includes(sources[i])) {
      return true
    }
  }
  return false
}

// 替换字符串中的国际化内容，格式为 $t('xxx')
export function replaceInlineI18n(rawString) {
  const res = []
  const reg = /\$t\('([\w.]+)'\)/gm
  let tmp
  if (!rawString) {
    return res
  }
  while ((tmp = reg.exec(rawString)) !== null) {
    res.push(tmp)
  }
  res.forEach(tmp => {
    rawString = rawString.replaceAll(tmp[0], tmp[1])
  })
  return rawString
}
