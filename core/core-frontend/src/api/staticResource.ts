import request from '@/config/axios'
import { guid } from '@/views/visualized/data/dataset/form/util.js'
import { ElMessage } from 'element-plus-secondary'

const staticResourcePath = '/static-resource/'
// 封装接口调用参数并返回请求结果
export const uploadFile = (fileId: number | string, param) =>
  request.post({
    url: '/static-resource/upload/' + fileId,
    headersType: 'multipart/form-data',
    loading: true,
    data: param
  })

// 校验当前数据是否满足业务规则
export function beforeUploadCheck(file) {
  const isImage = file.type.startsWith('image/')
  const isSizeValid = file.size / 1024 / 1024 < 15 // 15MB

  if (!isImage) {
    ElMessage.error('请上传图片')
    return false
  }
  if (!isSizeValid) {
    ElMessage.error('图片大小不能超过15M')
    return false
  }
  return true
}

// 封装接口调用参数并返回请求结果
export function uploadFileResult(file, callback) {
  const fileId = guid()
  const fileName = file.name
  const newFileName = fileId + fileName.substr(fileName.lastIndexOf('.'), fileName.length)
  const fileUrl = staticResourcePath + newFileName
  const param = new FormData()
  param.append('file', file)
  return uploadFile(fileId, param)
    .then(() => {
      callback(fileUrl, null)
    })
    .catch(error => {
      callback(null, error)
    })
}

// 封装接口调用参数并返回请求结果
export function resourceBase64(params) {
  return request.post({
    url: '/static-resource/resource-base64',
    data: params
  })
}
