// 根据时间粒度计算选中时间对应的起止范围
export const getRange = (outerTimeValue, timeGranularity) => {
  const selectValue = timeGranularity === 'y_M_d_H' ? outerTimeValue + ':' : outerTimeValue
  if (new Date(selectValue).toString() === 'Invalid Date') {
    return selectValue
  }
  switch (timeGranularity) {
    case 'year':
    case 'y':
      return getYearEnd(selectValue)
    case 'month':
    case 'y_M':
      return getMonthEnd(selectValue)
    case 'date':
    case 'y_M_d':
    case 'M_d':
      return getDayEnd(selectValue)
    case 'hour':
    case 'y_M_d_H':
      return getHourEnd(selectValue)
    case 'minute':
    case 'y_M_d_H_m':
      return getMinuteEnd(selectValue)
    case 'y_M_d_H_m_s':
      return getSecondEnd(selectValue)
    case 'datetime':
      return [+new Date(selectValue), +new Date(selectValue)]
    default:
      return selectValue
  }
}

// 根据粒度计算时间段的起始范围
export const getTimeBegin = (selectValue, timeGranularity) => {
  switch (timeGranularity) {
    case 'year':
      return getYearEnd(selectValue)
    case 'month':
      return getMonthEnd(selectValue)
    case 'date':
      return getDayEnd(selectValue)
    default:
      return selectValue
  }
}

// 计算指定时间所在年份的起止时间戳
const getYearEnd = timestamp => {
  const time = new Date(timestamp)
  return [
    +new Date(time.getFullYear(), 0, 1),
    +new Date(time.getFullYear(), 11, 31) + 60 * 1000 * 60 * 24 - 1000
  ]
}

// 计算指定时间所在月份的起止时间戳
const getMonthEnd = timestamp => {
  const time = new Date(timestamp)
  const date = new Date(time.getFullYear(), time.getMonth(), 1)
  date.setDate(1)
  date.setMonth(date.getMonth() + 1)
  return [+new Date(time.getFullYear(), time.getMonth(), 1), +new Date(date.getTime() - 1000)]
}

// 计算指定日期的起止时间戳
const getDayEnd = timestamp => {
  const utcTime = getUtcTime(timestamp)
  return [+utcTime, +utcTime + 60 * 1000 * 60 * 24 - 1000]
}

// 计算指定小时的起止时间戳
const getHourEnd = timestamp => {
  return [+new Date(timestamp), +new Date(timestamp) + 60 * 1000 * 60 - 1000]
}

// 计算指定分钟的起止时间戳
const getMinuteEnd = timestamp => {
  return [+new Date(timestamp), +new Date(timestamp) + 60 * 1000 - 1000]
}

// 计算指定秒的起止时间戳
const getSecondEnd = timestamp => {
  return [+new Date(timestamp), +new Date(timestamp) + 999]
}

// 将输入时间转换为本地构造的 UTC 时间对象
const getUtcTime = timestamp => {
  if (timestamp) {
    const time = new Date(timestamp)
    const utcDate = new Date(
      time.getUTCFullYear(),
      time.getUTCMonth(),
      time.getUTCDate(),
      time.getUTCHours(),
      time.getUTCMinutes(),
      time.getUTCSeconds()
    )
    return utcDate
  } else {
    return timestamp
  }
}
