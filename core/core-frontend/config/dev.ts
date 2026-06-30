import os from 'node:os'

type NetworkAddress = {
  address?: string
  family?: string | number
  internal?: boolean
}

type NetworkEntry = [string, NetworkAddress[]]

// 移除浏览器 Origin 头，避免本地代理转发时触发后端跨域校验
const dropBrowserOriginHeader = proxy => {
  proxy.on('proxyReq', proxyReq => {
    proxyReq.removeHeader('origin')
  })
}

// 本地开发默认使用当前网卡地址，换网络时无需手工调整代理目标
const resolveNetworkAddress = () => {
  const explicitHost = process.env.CREST_DEV_PROXY_HOST
  if (explicitHost) {
    return explicitHost
  }

  const preferredInterface = process.env.CREST_DEV_PROXY_INTERFACE
  const interfaces = os.networkInterfaces() as Record<string, NetworkAddress[] | undefined>
  const allEntries: NetworkEntry[] = Object.entries(interfaces).map(([name, addresses]) => [
    name,
    addresses || []
  ])
  const entries: NetworkEntry[] = preferredInterface
    ? preferredInterface.split(',').map(name => {
        const trimmedName = name.trim()
        return [trimmedName, interfaces[trimmedName] || []]
      })
    : [
        ...allEntries.filter(([name]) => /^en\d+$/u.test(name)),
        ...allEntries.filter(([name]) => !/^en\d+$/u.test(name) && !/^lo\d*$/u.test(name))
      ]

  for (const [, addresses] of entries) {
    const matched = addresses.find(address => {
      const family = typeof address.family === 'string' ? address.family : `IPv${address.family}`
      return family === 'IPv4' && !address.internal
    })

    if (matched?.address) {
      return matched.address
    }
  }

  return 'localhost'
}

const backendProxyPort = process.env.CREST_DEV_PROXY_PORT || '8100'
const backendProxyTarget =
  process.env.CREST_DEV_PROXY_TARGET || `http://${resolveNetworkAddress()}:${backendProxyPort}`

export default {
  server: {
    proxy: {
      '/api/f': {
        target: backendProxyTarget,
        changeOrigin: true,
        configure: dropBrowserOriginHeader,
        rewrite: path => path.replace(/^\/api\/f/, '')
      },
      // 使用 proxy 实例
      '/api': {
        target: backendProxyTarget,
        changeOrigin: true,
        configure: dropBrowserOriginHeader
      },
      '/websocket': {
        target: backendProxyTarget,
        changeOrigin: true,
        ws: true,
        configure: dropBrowserOriginHeader
      }
    },
    port: 8080
  }
}
