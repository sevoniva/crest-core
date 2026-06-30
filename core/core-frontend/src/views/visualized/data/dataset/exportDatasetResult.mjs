export const EXPORT_DATASET_RESULT = {
  QUEUED: 'queued',
  CANCELED: 'canceled',
  FAILED: 'failed'
}

const DEFAULT_ERROR_MESSAGE = '数据集导出请求失败，请稍后再试'

// 转换当前值并同步表单状态
const headerValue = (headers, key) => {
  if (!headers) {
    return ''
  }
  if (typeof headers.get === 'function') {
    return headers.get(key) || headers.get(key.toLowerCase()) || ''
  }
  const normalizedKey = key.toLowerCase()
  const matchedKey = Object.keys(headers).find(item => item.toLowerCase() === normalizedKey)
  return matchedKey ? headers[matchedKey] : ''
}

// 衔接当前组件交互和状态同步
const readBodyText = async body => {
  if (!body) {
    return ''
  }
  if (typeof body.text === 'function') {
    return body.text()
  }
  if (typeof body === 'string') {
    return body
  }
  if (typeof body === 'object') {
    return JSON.stringify(body)
  }
  return ''
}

// 衔接当前组件交互和状态同步
const parseJsonBody = async response => {
  const data = response?.data
  const contentType = `${headerValue(response?.headers, 'content-type')} ${data?.type || ''}`
  const shouldParse =
    data && (typeof data === 'string' || typeof data === 'object') && contentType.includes('json')

  if (!shouldParse) {
    return null
  }

  const text = await readBodyText(data)
  if (!text) {
    return null
  }

  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

// 整理当前数据并同步界面状态
export const resolveExportDatasetResult = async response => {
  if (response?.canceled || response?.code === 499) {
    return {
      status: EXPORT_DATASET_RESULT.CANCELED,
      message: response?.msg || 'request canceled'
    }
  }

  if (!response) {
    return {
      status: EXPORT_DATASET_RESULT.FAILED,
      message: DEFAULT_ERROR_MESSAGE
    }
  }

  if (response.code !== undefined && response.code !== 0 && response.code !== 50002) {
    return {
      status: EXPORT_DATASET_RESULT.FAILED,
      message: response.msg || DEFAULT_ERROR_MESSAGE
    }
  }

  const httpStatus = Number(response.status)
  if (httpStatus && (httpStatus < 200 || httpStatus >= 300)) {
    return {
      status: EXPORT_DATASET_RESULT.FAILED,
      message: response.statusText || DEFAULT_ERROR_MESSAGE
    }
  }

  const json = await parseJsonBody(response)
  if (json && json.code !== undefined && json.code !== 0 && json.code !== 50002) {
    return {
      status: EXPORT_DATASET_RESULT.FAILED,
      message: json.msg || DEFAULT_ERROR_MESSAGE
    }
  }

  return { status: EXPORT_DATASET_RESULT.QUEUED }
}
