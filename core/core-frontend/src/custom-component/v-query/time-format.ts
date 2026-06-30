import type { ManipulateType } from 'dayjs'
import dayjs from 'dayjs'
// 获取今年起始时间
function getThisYear() {
  return new Date(dayjs().startOf('year').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取去年起始时间
function getLastYear() {
  return new Date(dayjs().subtract(1, 'year').startOf('year').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取明年起始时间
function getNextYear() {
  return new Date(dayjs().add(1, 'year').startOf('year').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取本月起始时间
function getThisMonth() {
  return new Date(dayjs().startOf('month').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取指定时间单位的上一周期起始时间
function getLastStart(val = 'month' as ManipulateType) {
  return new Date(dayjs().subtract(1, val).startOf(val).format('YYYY/MM/DD HH:mm:ss'))
}

// 获取上月起始时间
function getLastMonth() {
  return getLastStart()
}

// 获取下月起始时间
function getNextMonth() {
  return new Date(dayjs().add(1, 'month').startOf('month').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取今天起始时间
function getToday() {
  return new Date(dayjs().startOf('day').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取昨天起始时间
function getYesterday() {
  return new Date(dayjs().subtract(1, 'day').startOf('day').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取本月月初时间
function getMonthBeginning() {
  return new Date(dayjs().startOf('month').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取本月月末时间
function getMonthEnd() {
  return new Date(dayjs().endOf('month').format('YYYY/MM/DD HH:mm:ss'))
}

// 获取今年年初时间
function getYearBeginning() {
  return new Date(dayjs().startOf('year').format('YYYY/MM/DD HH:mm:ss'))
}

// 根据配置转换年或月范围边界
function getYearMonthRange(result, sort, type) {
  const [direction, scene] = (sort || '').split('-')
  if (direction === 'start') {
    return new Date(result.startOf(type).format('YYYY/MM/DD HH:mm:ss'))
  } else if (direction === 'end') {
    if (scene === 'config') {
      return new Date(result.format('YYYY/MM/DD HH:mm:ss'))
    } else if (scene === 'panel') {
      return new Date(dayjs(result).endOf(type).format('YYYY/MM/DD HH:mm:ss'))
    }
  }
}

// 根据相对时间配置生成自定义时间点
function getCustomTime(
  timeNum: number,
  timeType: ManipulateType | 'date',
  timeGranularity: string,
  around: string,
  arbitraryTime?: Date,
  timeGranularityMultiple?: string,
  sort?: string
) {
  const type = around === 'f' ? 'subtract' : 'add'

  const result = dayjs()[type](timeNum, timeType === 'date' ? 'day' : timeType)

  if (['monthrange', 'yearrange', 'daterange'].includes(timeGranularityMultiple)) {
    return getYearMonthRange(result, sort, timeGranularityMultiple.split('range')[0])
  }

  if (!!arbitraryTime) {
    const time = dayjs(arbitraryTime).format('YYYY/MM/DD HH:mm:ss')
    // eslint-disable-next-line
    const [_, q] = time.split(' ')
    const [s] = result.format('YYYY/MM/DD HH:mm:ss').split(' ')

    return new Date(`${s} ${q}`)
  }

  const [k] = timeGranularity.split('range')
  return new Date(result.startOf(k as ManipulateType).format('YYYY/MM/DD HH:mm:ss'))
}

// 根据动态时间范围配置生成查询时间区间
function getDynamicRange({
  relativeToCurrent,
  timeNum,
  relativeToCurrentType,
  around,
  arbitraryTime,
  timeGranularity
}) {
  let selectValue = null
  if (relativeToCurrent === 'custom') {
    const startTime = getCustomTime(timeNum, relativeToCurrentType, timeGranularity, around)
    const endTime = getCustomTime(
      timeNum + (around === 'f' ? -1 : 1),
      relativeToCurrentType,
      timeGranularity,
      around
    )
    switch (timeGranularity) {
      case 'year':
        selectValue = [startTime.getTime(), endTime.getTime() - 1000]
        break
      case 'month':
        selectValue = [startTime.getTime(), endTime.getTime() - 1000]
        break
      case 'date':
        const dateVal = getCustomTime(timeNum, relativeToCurrentType, timeGranularity, around)
        selectValue = [dateVal.getTime(), dateVal.getTime() + 24 * 3600 * 1000 - 1000]
        break
      case 'datetime':
        const datetimeVal = getCustomTime(
          timeNum,
          relativeToCurrentType,
          timeGranularity,
          around,
          arbitraryTime
        )
        selectValue = [datetimeVal.getTime(), datetimeVal.getTime()]
        break
      default:
        break
    }
  } else {
    const isDateTime = timeGranularity === 'datetime'
    switch (relativeToCurrent) {
      case 'thisYear':
        selectValue = [getThisYear().getTime(), getNextYear().getTime() - 1000]
        break
      case 'lastYear':
        selectValue = [getLastYear().getTime(), getYearBeginning().getTime() - 1000]
        break
      case 'thisMonth':
        selectValue = [getThisMonth().getTime(), getNextMonth().getTime() - 1000]
        break
      case 'lastMonth':
        selectValue = [getLastMonth().getTime(), getMonthBeginning().getTime() - 1000]
        break
      case 'today':
        const todayVal = getToday().getTime()
        selectValue = [todayVal, isDateTime ? todayVal : todayVal + 24 * 3600 * 1000 - 1000]
        break
      case 'yesterday':
        const yesterdayVal = getYesterday().getTime()
        selectValue = [
          yesterdayVal,
          isDateTime ? yesterdayVal : yesterdayVal + 24 * 3600 * 1000 - 1000
        ]
        break
      case 'monthBeginning':
        const monthBeginningVal = getMonthBeginning().getTime()
        selectValue = [
          monthBeginningVal,
          isDateTime ? monthBeginningVal : monthBeginningVal + 24 * 3600 * 1000 - 1000
        ]
        break
      case 'monthEnd':
        const monthEndVal = getMonthEnd().getTime()
        selectValue = isDateTime
          ? [monthEndVal, monthEndVal]
          : [monthEndVal - 24 * 3600 * 1000 + 1000, monthEndVal]
        break
      case 'yearBeginning':
        const yearBeginningVal = getYearBeginning().getTime()
        selectValue = [
          yearBeginningVal,
          isDateTime ? yearBeginningVal : yearBeginningVal + 24 * 3600 * 1000 - 1000
        ]
        break

      default:
        break
    }
  }

  return selectValue
}
interface TimeRange {
  intervalType: string
  dynamicWindow: boolean
  maximumSingleQuery: number
  regularOrTrends: string
  regularOrTrendsValue: string
  relativeToCurrent: string
  relativeToCurrentRange: string
  timeNum: number
  relativeToCurrentType: ManipulateType
  around: string
  timeNumRange: number
  relativeToCurrentTypeRange: ManipulateType
  aroundRange: string
  timeGranularityMultiple?: string
}
export {
  TimeRange,
  getThisYear,
  getLastYear,
  getThisMonth,
  getLastMonth,
  getToday,
  getYesterday,
  getMonthBeginning,
  getMonthEnd,
  getYearBeginning,
  getCustomTime,
  getDynamicRange
}
