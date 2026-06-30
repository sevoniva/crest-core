import { Base64 } from 'js-base64'

// 计算字段表达式在传输前编码，避免特殊字符影响 JSON 和 SQL 表达式解析
const originNameHandle = (arr = []) => {
  arr.forEach(ele => {
    if (ele.extField === 2) {
      ele.originName = Base64.encode(ele.originName)
    }
  })
}

// 接口返回后恢复计算字段表达式，供编辑器继续展示原始内容
const originNameHandleBack = (arr = []) => {
  arr.forEach(ele => {
    if (ele.extField === 2) {
      ele.originName = Base64.decode(ele.originName)
    }
  })
}

// 批量处理对象中的多个字段数组
const originNameHandleWithArr = (obj = {}, fields) => {
  fields.forEach(ele => {
    originNameHandle(obj?.[ele] || [])
  })
}

// 批量恢复对象中的多个字段数组
const originNameHandleBackWithArr = (obj = {}, fields) => {
  fields.forEach(ele => {
    originNameHandleBack(obj?.[ele] || [])
  })
}

export {
  originNameHandle,
  originNameHandleBack,
  originNameHandleWithArr,
  originNameHandleBackWithArr
}
