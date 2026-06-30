import request from '@/config/axios'

// 字体资源信息
export interface Font {
  id: string
  name: string
  fileName: string
  fileTransName?: string
  isDefault: boolean
  isBuiltin?: boolean
}

// 查询字体列表
export const list = () => {
  return request.get({ url: '/typeface/fonts' }).then(res => {
    return res?.data
  })
}

// 创建字体配置
export const create = (data = {}) => {
  return request.post({ url: '/typeface', data }).then(res => {
    return res?.data
  })
}

// 编辑字体配置
export const edit = (data = {}) => {
  return request.put({ url: '/typeface', data }).then(res => {
    return res?.data
  })
}

// 删除指定字体
export const deleteById = id => {
  return request.delete({ url: '/typeface/' + id, data: {} }).then(res => {
    return res?.data
  })
}

// 查询默认字体
export const defaultFont = () => {
  return request.get({ url: '/typeface/default' }).then(res => {
    return res?.data
  })
}

// 上传字体文件
export const uploadFontFile = async (data): Promise<IResponse> => {
  return request
    .post({
      url: '/typeface/files/upload',
      data,
      loading: true,
      headersType: 'multipart/form-data;'
    })
    .then(res => {
      return res
    })
}
