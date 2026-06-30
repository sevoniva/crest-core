import { useI18n } from '@/hooks/web/useI18n'
const { t } = useI18n()

export const dsTypes = [
  {
    type: 'obOracle',
    name: 'OceanBase Oracle',
    catalog: 'OLTP',
    extraParams: '',
    charset: [
      'Default',
      'US7ASCII',
      'GBK',
      'BIG5',
      'ISO-8859-1',
      'UTF-8',
      'UTF-16',
      'CP850',
      'EUC_JP',
      'EUC_KR'
    ],
    targetCharset: ['Default', 'GBK', 'UTF-8']
  },
  {
    type: 'API',
    name: 'API',
    catalog: 'OTHER',
    extraParams: ''
  },
  {
    type: 'Excel',
    name: t('common.local_excel'),
    catalog: 'LOCAL',
    extraParams: ''
  },
  {
    type: 'ExcelRemote',
    name: t('common.remote_excel'),
    catalog: 'LOCAL',
    extraParams: ''
  }
]

export const typeList = ['OLTP', 'OTHER', 'LOCAL']
export const nameMap = {
  OLTP: 'OLTP',
  OTHER: t('data_source.api_data'),
  LOCAL: t('datasource.local_file')
}

export interface Configuration {
  dataBase: string
  jdbcUrl: string
  urlType: string
  connectionType: string
  schema: string
  extraParams: string
  username: string
  password: string
  host: string
  authMethod: string
  port: string
  initialPoolSize: string
  minPoolSize: string
  maxPoolSize: string
  queryTimeout: string
  charset: string
  targetCharset: string
  readOnly: boolean
  useSSH: boolean
  sshHost: string
  sshPort: string
  sshUserName: string
  sshType: string
  sshPassword: string
  sslCA: string
  sslCert: string
  sslKey: string
  url?: string
  [key: string]: any
}

export interface ApiConfiguration {
  id: string
  name: string
  type: string
  displayTableName: string
  method: string
  copy: boolean
  url: string
  status: string
  useJsonPath: boolean
  serialNumber: number
  fields?: any[]
  jsonFields?: any[]
  updateTime?: string | number
  [key: string]: any
}

export interface SyncSetting {
  id: string
  updateType: string
  syncRate: string
  simpleCronValue: number
  simpleCronType: string
  startTime: number
  endTime: number
  endLimit: string
  cron: string
  [key: string]: any
}

export interface Node {
  name: string
  createBy: string
  creator: string
  copy?: boolean
  createTime: string
  id: number | string
  size: number
  description: string
  type: string
  nodeType: string
  fileName: string
  syncSetting?: SyncSetting
  editType?: number
  configuration?: Configuration
  apiConfiguration?: ApiConfiguration[]
  paramsConfiguration?: ApiConfiguration[]
  weight?: number
  enableDataFill?: boolean
  extraFlag?: number
  lastSyncTime?: number | string
}
