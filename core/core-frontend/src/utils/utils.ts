import { BusiTreeNode } from '@/models/tree/TreeNode'
import { useCache } from '@/hooks/web/useCache'
import { loadScript } from '@/utils/RemoteJs'
import { ElMessage } from 'element-plus-secondary'

const { wsCache } = useCache()
// 深拷贝普通对象、数组和日期对象，用于隔离编辑态和展示态数据
export function deepCopy(target) {
  if (target === null || target === undefined) {
    return target
  } else if (typeof target == 'object') {
    const result = Array.isArray(target) ? [] : {}
    for (const key in target) {
      if (target[key] === null || target[key] === undefined) {
        result[key] = target[key]
      } else if (target[key] instanceof Date) {
        // 日期对象需要复制实例，避免共享引用
        result[key] = new Date(target[key])
      } else if (typeof target[key] == 'object') {
        result[key] = deepCopy(target[key])
      } else {
        result[key] = target[key]
      }
    }
    return result
  }
  return target
}

// 交换数组中两个位置的元素
export function swap(arr, i, j) {
  const temp = arr[i]
  arr[i] = arr[j]
  arr[j] = temp
}

// 查询单个 DOM 节点，保留历史命名入口
export function _$(selector) {
  return document.querySelector(selector)
}

// 查询单个 DOM 节点
export function $(selector) {
  return document.querySelector(selector)
}

const components = ['VText', 'RectShape', 'CircleShape']
// 判断组件是否禁止直接拖放到画布
export function isPreventDrop(component) {
  return !components.includes(component) && !component.startsWith('SVG')
}

// 为缺少协议的地址补齐 HTTP 协议
export function checkAddHttp(url) {
  if (!url) {
    return url
  } else if (/^(http(s)?:\/\/)/.test(url.toLowerCase())) {
    return url
  } else {
    return 'http://' + url
  }
}

// 给匹配关键字的名称生成高亮 HTML 片段
export const setColorName = (obj, keyword: string, key?: string, colorKey?: string) => {
  key = key || 'name'
  colorKey = colorKey || 'colorName'
  if (!keyword) {
    obj[colorKey] = null
    return
  }
  const name = obj[key]
  const index = name.indexOf(keyword)
  if (index > -1) {
    const textCode =
      name.substring(0, index) +
      '<span class="search-key-span">' +
      keyword +
      '</span>' +
      name.substring(index + keyword.length, name.length)
    obj[colorKey] = textCode
    return
  }
  obj[colorKey] = null
}

// 从当前页面查询串中读取指定参数
export const getQueryString = (name: string) => {
  return new URLSearchParams(window.location.search).get(name)
}

// 判断当前回调参数是否来自飞书平台登录
export const isLarkPlatform = () => {
  return !!getQueryString('state') && !!getQueryString('code')
}

// 判断当前回调参数是否来自第三方客户端登录
export const isPlatformClient = () => {
  return !!getQueryString('client') || getQueryString('state')?.includes('client')
}

// 校验当前访问路径是否属于外部认证平台流程
export const checkPlatform = () => {
  const flagArray = ['/casbi', 'oidcbi']
  const pathname = window.location.pathname
  if (
    !flagArray.some(flag => pathname.includes(flag)) &&
    !isLarkPlatform() &&
    !isPlatformClient()
  ) {
    return cleanPlatformFlag()
  }
  return true
}
// 清理外部认证平台缓存标记
export const cleanPlatformFlag = () => {
  const platformKey = 'out_auth_platform'
  wsCache.delete(platformKey)
  return false
}
// 判断当前页面是否运行在 iframe 内
export const isInIframe = () => {
  try {
    return window.top !== window.self
  } catch (error) {
    console.error(error)
    return true
  }
}

// 根据配置值判断按钮是否应展示
export const isBtnShow = (val: string) => {
  if (!val || val === '0') {
    return true
  } else if (val === '1') {
    return false
  } else {
    return !isInIframe()
  }
}
// 判断当前设备是否为手机端
export function isMobile() {
  return (
    navigator.userAgent.match(
      /(phone|pad|pod|iPhone|iPod|ios|iPad|Android|Mobile|BlackBerry|IEMobile|MQQBrowser|JUC|Fennec|wOSBrowser|BrowserNG|WebOS|Symbian|Windows Phone)/i
    ) && !isTablet()
  )
}

// 判断当前设备是否为 iOS 移动设备
export function isISOMobile() {
  return navigator.userAgent.match(/(iPhone|iPad|iPod)/i) && !isTablet()
}

export const isDingTalk = window.navigator.userAgent.toLowerCase().includes('dingtalk')

// 设置页面标题，钉钉环境优先调用钉钉导航 API
export const setTitle = (title?: string) => {
  if (!isDingTalk) {
    document.title = title || 'Crest'
    return
  }
  const jsUrl = 'https://g.alicdn.com/dingding/dingtalk-jsapi/3.0.25/dingtalk.open.js'
  const jsId = 'crest-platform-client-dingtalk'
  if (window['dd'] && window['dd'].biz?.navigation?.setTitle) {
    window['dd'].biz.navigation.setTitle({
      title: title
    })
    return
  }
  const awaitMethod = loadScript(jsUrl, jsId)
  awaitMethod
    .then(() => {
      window['dd'].ready(() => {
        window['dd'].biz.navigation.setTitle({
          title: title
        })
      })
    })
    .catch(() => {
      document.title = title || 'Crest'
    })
}

// 判断当前设备是否为平板
export function isTablet() {
  const userAgent = navigator.userAgent
  const tabletRegex = /iPad|Silk|Galaxy Tab|PlayBook|BlackBerry|(tablet|ipad|playbook)/i
  return tabletRegex.test(userAgent)
}
// 从树结构中移除指定目标节点
export function cutTargetTree(tree: BusiTreeNode[], targetId: string | number) {
  tree.forEach((node, index) => {
    if (node.id === targetId) {
      tree.splice(index, 1)
      return
    } else if (node.children) {
      cutTargetTree(node.children, targetId)
    }
  })
}

// 判断值是否为空值或字符串形式的 null
export const isNull = arg => {
  return typeof arg === 'undefined' || arg === null || arg === 'null'
}

// 根据资源权重和扩展位计算导出权限数组
export const exportPermission = (weight, ext) => {
  const result = [0, 0, 0]
  if (!weight || weight === 1) {
    return result
  } else if (weight === 9) {
    return [1, 1, 1]
  }
  if (!ext) {
    return result
  }
  const extArray = formatExt(ext) || []
  for (let index = 0; index < extArray.length; index++) {
    result[index] = extArray[index]
  }
  return result
}

// 将扩展权限数字拆解为倒序权限位数组
export const formatExt = (num: number): number[] | null => {
  if (!num) {
    return null
  }
  const reversedStr = num.toString().split('').reverse().join('')
  const reversedNumArray = reversedStr?.split('')?.map(Number) ?? []
  return reversedNumArray
}

// 读取浏览器语言并转换为系统使用的语言标识
export const getBrowserLocale = () => {
  const language = navigator.language
  if (!language) {
    return 'zh-CN'
  }
  if (language.startsWith('en')) {
    return 'en'
  }
  if (language.toLowerCase().startsWith('zh')) {
    const temp = language.toLowerCase().replace('_', '-')
    return temp === 'zh' ? 'zh-CN' : temp === 'zh-cn' ? 'zh-CN' : 'tw'
  }
  return language
}
// 获取当前用户语言，优先使用缓存配置
export const getLocale = () => {
  return wsCache.get('user.language') || getBrowserLocale() || 'zh-CN'
}

// 判断资源树节点是否位于当前组织的自由目录下
export const isFreeFolder = (node, flag) => {
  const oid = wsCache.get('user.oid')
  if (!oid) {
    return false
  }
  const freeRootId = (Number(oid) + flag).toString()
  let cNode = node
  while (cNode) {
    const data = cNode.data
    const id = data['id']
    if (id === freeRootId) {
      return true
    }
    cNode = cNode['parent']
  }
  return false
}

// 从资源树列表中过滤当前组织的自由目录
export const filterFreeFolder = (list, flagText) => {
  const flagArray = ['dashboard', 'dataV', 'dataset', 'datasource']
  const index = flagArray.findIndex(item => item === flagText)
  const oid = wsCache.get('user.oid')
  if (!oid || index < 0) {
    return
  }
  const freeRootId = (Number(oid) + index + 1).toString()
  let len = list.length
  while (len--) {
    const node = list[len]
    if (node['id'] === freeRootId) {
      list.splice(len, 1)
      return
    }
    if (node['id'] === '0') {
      const children = node['children']
      let innerLen = children?.length
      while (innerLen--) {
        const kid = children[innerLen]
        if (kid['id'] === freeRootId) {
          children.splice(innerLen, 1)
          return
        }
      }
    }
  }
}
// 修剪名称并校验 1 到 64 个字符长度
export const nameTrim = (target: Record<string, any>, msg = '名称字段长度1-64个字符') => {
  if (target.name) {
    target.name = target.name.trim()
    if (target.name.length < 1 || target.name.length > 64) {
      ElMessage.warning(msg)
      throw new Error(msg)
    }
  }
}

// 统计内容中启用展示的分类名称集合
export const getActiveCategories = contents => {
  const result = ['最近使用']
  if (contents) {
    contents.forEach(item => {
      if (item.showFlag) {
        item.categories.forEach(category => {
          result.push(category.name)
        })
      }
    })
  }
  return new Set(result)
}
