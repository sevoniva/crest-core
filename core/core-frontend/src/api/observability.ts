import request from '@/config/axios'

// 封装接口调用参数并返回请求结果
export const observabilityStatusApi = () => request.get({ url: '/observability/status' })
