import { getLocale } from '@/utils/utils'

export const isEnLocal = !['zh', 'zh-cn', 'zh-CN', 'tw'].includes(getLocale())

export const formatterItem = {
  type: 'auto', // auto,value,percent
  unitLanguage: isEnLocal ? 'en' : 'ch',
  unit: 1, // 换算单位
  suffix: '', // 单位后缀
  decimalCount: 2, // 小数位数
  thousandSeparator: true // 千分符
}
