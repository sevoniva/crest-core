const DEFAULT_DELAY = 1000
const DEFAULT_TTL = 60 * 1000

// 规范化导出任务状态
const toStatus = task => String(task?.exportStatus || '').toUpperCase()

// 生成导出任务去重键
const toTaskKey = task => `task:${task?.id || ''}:${toStatus(task)}`

// 清理超过有效期的任务缓存
const prune = (cache, now, ttl) => {
  cache.forEach((time, key) => {
    if (now - time > ttl) {
      cache.delete(key)
    }
  })
}

// 创建导出结果通知队列
export const createExportNoticeQueue = ({ delay = DEFAULT_DELAY, ttl = DEFAULT_TTL, now = Date.now, emit }) => {
  const seen = new Map()
  const pending = []
  let timer

  // 延迟触发队列刷新
  const schedule = () => {
    if (timer) {
      return
    }
    if (delay <= 0) {
      return
    }
    timer = setTimeout(() => {
      timer = undefined
      flush()
    }, delay)
  }

  // 合并待通知任务并派发通知
  const flush = () => {
    if (timer) {
      clearTimeout(timer)
      timer = undefined
    }
    if (!pending.length) {
      return
    }
    const grouped = pending.splice(0).reduce((result, task) => {
      const status = toStatus(task)
      if (!result[status]) {
        result[status] = []
      }
      result[status].push(task)
      return result
    }, {})

    Object.keys(grouped).forEach(status => {
      const tasks = grouped[status]
      emit?.({
        status,
        count: tasks.length,
        firstName: tasks[0]?.exportFromName || '',
        tasks
      })
    })
  }

  // 添加导出任务并按状态去重
  const push = task => {
    const status = toStatus(task)
    if (!['SUCCESS', 'FAILED'].includes(status)) {
      return false
    }

    const current = now()
    prune(seen, current, ttl)
    const taskKey = toTaskKey(task)
    if (seen.has(taskKey)) {
      return false
    }
    seen.set(taskKey, current)
    pending.push(task)
    schedule()
    return true
  }

  // 清空队列、定时器和去重缓存
  const clear = () => {
    if (timer) {
      clearTimeout(timer)
      timer = undefined
    }
    pending.splice(0)
    seen.clear()
  }

  return {
    push,
    flush,
    clear
  }
}

export const formatExportNoticeMessage = (
  notice,
  successText,
  failedText,
  bulkSuccessText,
  bulkFailedText
) => {
  if (notice.count <= 1) {
    const actionText = notice.status === 'FAILED' ? failedText : successText
    return `${notice.firstName} ${actionText}`.trim()
  }
  const template = notice.status === 'FAILED' ? bulkFailedText : bulkSuccessText
  if (template) {
    return template.replace('{count}', String(notice.count))
  }
  return notice.status === 'FAILED'
    ? `${notice.count} 个导出任务失败，前往`
    : `${notice.count} 个导出任务已成功，前往`
}
