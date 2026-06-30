import request from '@/config/axios'

interface IResourceCount {
  jobCount: number
  datasourceCount: number
  jobLogCount: number
}

export const getResourceCount = () => {
  return request
    .get({
      url: 'sync/summary/resource-count',
      method: 'get'
    })
    .then(res => {
      return res.data as IResourceCount
    })
}

export const getJobLogLienChartInfo = () => {
  return request.post({
    url: '/sync/summary/log-chart-data',
    method: 'post',
    data: ''
  })
}
