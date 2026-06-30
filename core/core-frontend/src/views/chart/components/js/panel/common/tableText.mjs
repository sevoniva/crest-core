const BLOCK_END_TAG_RE = /<\/(p|div|section|article|header|footer|li|tr|h[1-6])>/gi
const LINE_BREAK_TAG_RE = /<br\s*\/?>/gi
const ENCODED_LINE_BREAK_TAG_RE = /&lt;br\s*\/?&gt;/gi
const PRESENTATION_TAG_RE =
  /<\/?(span|strong|b|em|i|u|p|div|section|article|header|footer|li|ul|ol|table|thead|tbody|tr|td|th|a|font|label|small|mark|code|pre)[^>]*>/gi

const entityMap = {
  '&nbsp;': ' ',
  '&#160;': ' ',
  '&ensp;': ' ',
  '&emsp;': '  ',
  '&thinsp;': ' ',
  '&amp;': '&',
  '&quot;': '"',
  '&#34;': '"',
  '&#39;': "'",
  '&apos;': "'"
}

// 将表格单元格内容归一化为可复制的纯文本
export function normalizeTableCellText(value) {
  if (value === null || value === undefined) {
    return ''
  }

  const text = normalizeActualNewlines(decodeEscapedWhitespace(stripDangerousHtml(String(value))))
    .replace(/&#10;|&#x0a;|&#13;|&#x0d;/gi, '\n')
    .replace(/&#9;|&#x09;/gi, '  ')
    .replace(ENCODED_LINE_BREAK_TAG_RE, '\n')
    .replace(LINE_BREAK_TAG_RE, '\n')
    .replace(BLOCK_END_TAG_RE, '\n')
    .replace(PRESENTATION_TAG_RE, '')
    .replace(/&nbsp;|&#160;|&ensp;|&emsp;|&thinsp;|&amp;|&quot;|&#34;|&#39;|&apos;/gi, match => {
      return entityMap[match.toLowerCase()] ?? match
    })

  return text
    .split('\n')
    .map(line => line.replace(/[ \f\v]+/g, ' ').trim())
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

// 解码字符串中的转义换行和制表符
function decodeEscapedWhitespace(value) {
  let result = ''
  for (let index = 0; index < value.length; index += 1) {
    const current = value[index]
    const next = value[index + 1]
    if (current !== '\\' || next === undefined) {
      result += current
      continue
    }

    if (next === 'r') {
      result += '\n'
      index += value[index + 2] === '\\' && value[index + 3] === 'n' ? 3 : 1
      continue
    }
    if (next === 'n') {
      result += '\n'
      index += 1
      continue
    }
    if (next === 't') {
      result += '  '
      index += 1
      continue
    }

    result += current
  }
  return result
}

// 统一真实换行符和制表符，保证复制文本格式稳定
function normalizeActualNewlines(value) {
  let result = ''
  for (let index = 0; index < value.length; index += 1) {
    const current = value[index]
    if (current === '\r') {
      result += '\n'
      if (value[index + 1] === '\n') {
        index += 1
      }
      continue
    }
    result += current === '\t' ? '  ' : current
  }
  return result
}

// 移除复制内容中不应保留的危险 HTML 标签
function stripDangerousHtml(value) {
  let result = value
  for (const tagName of ['script', 'style', 'iframe']) {
    result = stripDangerousTag(result, tagName)
  }
  return result
}

// 按标签名剥离成对或未闭合的危险 HTML 片段
function stripDangerousTag(value, tagName) {
  let result = ''
  let searchFrom = 0
  const lowerValue = value.toLowerCase()
  const openTag = `<${tagName}`
  const closeTagStart = `</${tagName}`
  while (searchFrom < value.length) {
    const openIndex = lowerValue.indexOf(openTag, searchFrom)
    if (openIndex < 0) {
      result += value.slice(searchFrom)
      break
    }
    if (!isTagBoundary(value[openIndex + openTag.length])) {
      result += value.slice(searchFrom, openIndex + 1)
      searchFrom = openIndex + 1
      continue
    }
    result += value.slice(searchFrom, openIndex)
    const closeIndex = lowerValue.indexOf(closeTagStart, openIndex + openTag.length)
    if (closeIndex < 0) {
      const tagEnd = value.indexOf('>', openIndex)
      searchFrom = tagEnd < 0 ? value.length : tagEnd + 1
    } else {
      const closeTagEnd = value.indexOf('>', closeIndex)
      searchFrom = closeTagEnd < 0 ? value.length : closeTagEnd + 1
    }
  }
  return result
}

// 判断标签名后续字符是否构成合法标签边界
function isTagBoundary(char) {
  return char === undefined || char === '>' || char === '/' || char === ' ' || char === '\n' || char === '\t'
}

// 根据复制选项生成单元格文本
export function getTableCellCopyText(value, options = {}) {
  const text = normalizeTableCellText(value)
  if (options.preserveLineBreaks) {
    return text
  }
  return text.replace(/\n+/g, ' ').replace(/\t/g, '  ')
}

// 将二维表格数据构造成纯文本复制内容
export function buildTableCopyText(rows, headerRows = []) {
  return [...headerRows, ...rows]
    .map(row => row.map(value => getTableCellCopyText(value)).join('\t'))
    .join('\n')
}

// 将二维表格数据构造成 HTML 表格复制内容
export function buildTableCopyHtml(rows, headerRows = []) {
  const header = headerRows
    .map(row => `<tr>${row.map(cell => `<th>${escapeHtml(getTableCellCopyText(cell))}</th>`).join('')}</tr>`)
    .join('')
  const body = rows
    .map(row => `<tr>${row.map(cell => `<td>${escapeHtml(getTableCellCopyText(cell))}</td>`).join('')}</tr>`)
    .join('')
  return `<meta charset="utf-8"><table><tbody>${header}${body}</tbody></table>`
}

// 创建剪贴板多格式转换器
export function createTableCopyTransformer(transformers = {}) {
  // 归一化矩阵中的每一个单元格文本
  const normalizeMatrix = dataMatrix =>
    dataMatrix.map(row => row.map(value => getTableCellCopyText(value)))

  return {
    'text/plain': (dataMatrix, separator = '\t') => ({
      type: 'text/plain',
      content: normalizeMatrix(dataMatrix)
        .map(row => row.join(separator))
        .join('\n')
    }),
    'text/html': dataMatrix => {
      const htmlTransformer = transformers['text/html']
      const normalizedMatrix = normalizeMatrix(dataMatrix)
      if (htmlTransformer) {
        return htmlTransformer(normalizedMatrix)
      }
      return {
        type: 'text/html',
        content: buildTableCopyHtml(normalizedMatrix)
      }
    }
  }
}

// 转义 HTML 特殊字符，避免复制内容破坏表格结构
function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
