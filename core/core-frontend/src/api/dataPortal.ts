import request from '@/config/axios'

export interface DataPortalResourceRequest {
  keyword?: string
  type?: string
  page?: number
  pageSize?: number
  asc?: boolean
  queryFrom?: string
}

// 封装接口调用参数并返回请求结果
export const portalOverviewApi = () => request.get({ url: '/data-portal/overview' })

// 封装接口调用参数并返回请求结果
export const portalResourcesApi = (data: DataPortalResourceRequest) =>
  request.post({ url: '/data-portal/resources', data })

// 封装接口调用参数并返回请求结果
export const portalResourceApi = (id: string | number) =>
  request.get({ url: `/data-portal/resource/${id}` })
