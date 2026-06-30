import { FilterXSS } from 'xss'

const safeHtmlFilter = new FilterXSS({
  css: false,
  whiteList: {
    b: [],
    br: [],
    em: [],
    i: [],
    p: [],
    span: [],
    strong: [],
    u: []
  },
  stripIgnoreTag: true,
  stripIgnoreTagBody: ['script', 'style', 'iframe', 'object', 'embed']
})

// 整理输入数据并返回工具处理结果
export const sanitizeHtml = (value?: string | null) => safeHtmlFilter.process(value || '')
