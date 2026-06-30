// 计算颜色配置并返回样式结果
export const reverseColor = colorValue => {
  colorValue = '0x' + colorValue.replace(/#/g, '')
  const str = '000000' + (0xffffff - colorValue).toString(16)
  return '#' + str.substring(str.length - 6, str.length)
}
