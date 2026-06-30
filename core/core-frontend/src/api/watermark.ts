import request from '@/config/axios'

// 封装接口调用参数并返回请求结果
export const watermarkSave = params => request.post({ url: '/watermark/record', data: params })

// 封装接口调用参数并返回请求结果
export const watermarkFind = async () => request.get({ url: 'watermark/list' })
