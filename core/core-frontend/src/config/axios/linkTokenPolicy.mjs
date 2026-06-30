const AUTH_REQUEST_PREFIXES = ['/login/', '/sso/']
const AUTH_REQUEST_PATHS = ['/public-key', 'public-key']

// 生成跳转地址或资源链接
export const shouldAttachLinkToken = (url, routeHash = '') => {
  const requestUrl = String(url || '')
  const path = requestUrl.split('?')[0]
  const hash = String(routeHash || '')
  if (hash && !hash.startsWith('#/link/')) {
    return false
  }
  if (AUTH_REQUEST_PATHS.includes(path)) {
    return false
  }
  return !AUTH_REQUEST_PREFIXES.some(prefix => path.startsWith(prefix))
}
