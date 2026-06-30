import request from '@/config/axios'

export interface SystemAboutInfo {
  version: string
  commitId: string
}

// 查询当前系统版本和构建标识
export const querySystemAbout = async (): Promise<SystemAboutInfo> => {
  const response = await request.get({ url: '/system/about' })
  return response.data
}
