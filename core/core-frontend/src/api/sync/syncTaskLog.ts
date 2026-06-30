import request from '@/config/axios'

// 分页查询同步任务日志
export const getTaskLogListApi = (current: number, size: number, data: any) => {
  return request.post({
    url: `/sync/task/log/page/${current}/${size}`,
    data: data
  })
}

// 删除指定同步任务日志
export const removeApi = (logId: string) => {
  return request.delete({ url: `/sync/task/log/${logId}` })
}

// 查询同步任务日志详情
export const getTaskLogDetailApi = (logId: string, fromLineNum: number) => {
  return request.get({ url: `/sync/task/log/detail/${logId}/${fromLineNum}` })
}

// 清理同步任务日志
export const clear = (clearData: {}) => {
  return request.post({ url: `/sync/task/log/clear`, data: clearData })
}

// 终止正在执行的同步任务
export const terminationTaskApi = (logId: string) => {
  return request.post({ url: `/sync/task/log/termination/${logId}`, data: {} })
}
