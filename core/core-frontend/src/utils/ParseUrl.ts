// 整理输入数据并返回工具处理结果
function parseUrl(url) {
  const [pathname, params] = url.split('?')
  const [_, path] = pathname.split('#/')
  return {
    path,
    query: params
      .split('&')
      .map(ele => ele.split('='))
      .reduce((pre, next) => {
        const [key, value] = next
        pre[key] = value
        return pre
      }, {})
  }
}

export { parseUrl }
