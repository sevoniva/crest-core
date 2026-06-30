import type { ComponentInfo } from '@/api/chart'
import { guid } from '@/views/visualized/data/dataset/form/util.js'
import { useI18n } from '@/hooks/web/useI18n'
const { t } = useI18n()

// 维护表单数据和校验规则
const infoFormat = (obj: ComponentInfo) => {
  const { id, name, fieldType, type, datasetId } = obj
  return {
    id: guid(),
    name,
    showError: true,
    timeGranularity: 'date',
    timeGranularityMultiple: 'datetimerange',
    field: {
      id,
      type,
      name,
      fieldType
    },
    displayId: id,
    sortId: '',
    sort: 'asc',
    defaultMapValue: [],
    mapValue: [],
    conditionType: 0,
    conditionValueOperatorF: 'eq',
    conditionValueF: '',
    conditionValueOperatorS: 'like',
    conditionValueS: '',
    defaultConditionValueOperatorF: 'eq',
    defaultConditionValueF: '',
    defaultConditionValueOperatorS: 'like',
    defaultConditionValueS: '',
    timeType: 'fixed',
    relativeToCurrent: 'custom',
    required: false,
    timeNum: 0,
    relativeToCurrentType: 'date',
    around: 'f',
    parametersStart: null,
    parametersEnd: null,
    arbitraryTime: new Date(),
    timeNumRange: 0,
    relativeToCurrentTypeRange: 'date',
    aroundRange: 'f',
    arbitraryTimeRange: new Date(),
    auto: false,
    defaultValue: undefined,
    selectValue: undefined,
    optionValueSource: 0,
    valueSource: [],
    dataset: {
      id: datasetId,
      name: '',
      fields: []
    },
    visible: true,
    defaultValueCheck: false,
    multiple: false,
    displayType: '0',
    checkedFields: [],
    parameters: [],
    parametersCheck: false,
    parametersList: [],
    checkedFieldsMap: {}
  }
}

// 创建新数据并写入当前配置
const addQueryCriteriaConfig = () => {
  const componentInfo: ComponentInfo = {
    id: '',
    name: t('v_query.unnamed'),
    fieldType: 0,
    type: 'VARCHAR',
    datasetId: ''
  }
  return infoFormat(componentInfo)
}

export { infoFormat, addQueryCriteriaConfig }
