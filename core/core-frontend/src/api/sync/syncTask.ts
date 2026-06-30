import request from '@/config/axios'

export interface IGetTaskInfoReq {
  id?: string
  name?: string
}

export interface ITaskInfoInsertReq {
  [key: string]: any
}

export interface ISchedulerOption {
  interval: number
  unit: string
}

export interface ISource {
  type: string
  query: string
  tables: string
  datasourceId: string
  tableExtract: string
  dsTableList?: IDsTable[]
  dsList?: []
  fieldList?: ITableField[]
  targetFieldTypeList?: string[]
  incrementCheckbox?: string
  incrementField?: string
  esQuery?: string
}

export interface ITableField {
  id: string
  fieldSource: string
  fieldName: string
  fieldType: string
  remarks: string
  fieldSize: number
  fieldPrecision: number
  fieldPk: boolean
  fieldIndex: boolean
}

export interface ITargetProperty {
  /**
   * 启用分区on
   */
  partitionEnable: string
  /**
   * 分区类型
   * DateRange 日期
   * NumberRange 数值
   * List 列
   */
  partitionType: string
  /**
   * 启用动态分区on
   */
  dynamicPartitionEnable: string
  /**
   * 动态分区结束偏移量
   */
  dynamicPartitionEnd: number
  /**
   * 动态分区间隔单位
   * HOUR WEEK DAY MONTH YEAR
   */
  dynamicPartitionTimeUnit: string
  /**
   * 手动分区列值
   * 多个以','隔开
   */
  manualPartitionColumnValue: string
  /**
   * 手动分区数值区间开始值
   */
  manualPartitionStart: number
  /**
   * 手动分区数值区间结束值
   */
  manualPartitionEnd: number
  /**
   * 手动分区数值区间间隔
   */
  manualPartitionInterval: number
  /**
   * 手动分区日期区间
   * 2023-09-08 - 2023-09-15
   */
  manualPartitionTimeRange: string
  /**
   * 手动分区日期区间间隔单位
   */
  manualPartitionTimeUnit: string
  /**
   * 分区字段
   */
  partitionColumn: string
}

export interface ITarget {
  createTable?: string
  type: string
  fieldList: ITableField[]
  tableName: string
  datasourceId: string
  targetProperty: string
  dsList?: []
  multipleSelection?: ITableField[]
  property: ITargetProperty
  incrementSync: string
  incrementField: string
  incrementFieldType: string
  remarks: string
  faultToleranceRate: number
  incrementOffset: number
  incrementOffsetUnit: string
}

export class ITaskInfoRes {
  id: string

  name: string

  schedulerType: string

  schedulerConf: string

  schedulerOption: ISchedulerOption

  taskKey: string

  desc: string

  executorTimeout: number

  executorFailRetryCount: number

  source: ISource

  target: ITarget

  status: string
  startTime: string
  stopTime: string

  constructor(
    id: string,
    name: string,
    schedulerType: string,
    schedulerConf: string,
    taskKey: string,
    desc: string,
    executorTimeout: number,
    executorFailRetryCount: number,
    source: ISource,
    target: ITarget,
    status: string,
    startTime: string,
    stopTime: string,
    schedulerOption: ISchedulerOption
  ) {
    this.id = id
    this.name = name
    this.schedulerType = schedulerType
    this.schedulerConf = schedulerConf
    this.taskKey = taskKey
    this.desc = desc
    this.executorTimeout = executorTimeout
    this.executorFailRetryCount = executorFailRetryCount
    this.source = source
    this.target = target
    this.status = status
    this.startTime = startTime
    this.stopTime = stopTime
    this.schedulerOption = schedulerOption
  }
}

export interface ITaskInfoUpdateReq {
  [key: string]: any
}

export interface IDsTable {
  datasourceId: string
  name: string
  remark: string
  enableCheck: string
  datasetPath: string
}

// 按数据源类型查询可用于同步的数据源列表
export const datasourceListByTypeApi = (type: string) => {
  return request.get({ url: `/sync/datasource/list/${type}` })
}
// 分页查询同步任务列表
export const getTaskInfoListApi = (current: number, size: number, data) => {
  return request.post({ url: `/sync/task/page/${current}/${size}`, data: data })
}

// 立即执行单个同步任务
export const executeOneApi = (id: string) => {
  return request.get({ url: `/sync/task/runs/${id}` })
}

// 启动指定同步任务
export const startTaskApi = (id: string) => {
  return request.get({ url: `/sync/task/lifecycle/start/${id}` })
}

// 停止指定同步任务
export const stopTaskApi = (id: string) => {
  return request.get({ url: `/sync/task/lifecycle/stop/${id}` })
}

// 新增同步任务
export const addApi = (data: ITaskInfoInsertReq) => {
  return request.post({ url: `/sync/task`, data: data })
}

// 删除单个同步任务
export const removeApi = (taskId: string) => {
  return request.delete({ url: `/sync/task/${taskId}` })
}

// 批量删除同步任务
export const batchRemoveApi = (taskIds: string[]) => {
  return request.delete({ url: `/sync/task/batch`, data: taskIds })
}

// 修改同步任务配置
export const modifyApi = (data: ITaskInfoUpdateReq) => {
  return request.put({ url: `/sync/task`, data: data })
}

// 根据任务 ID 查询同步任务详情
export const findTaskInfoByIdApi = (taskId: string) => {
  return request.get({ url: `/sync/task/detail/${taskId}` })
}

// 查询指定数据源下可同步的数据表列表
export const datasourceTableListApi = (dsId: string) => {
  return request.get({ url: `/sync/datasource/table/list/${dsId}` })
}
