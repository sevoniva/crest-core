// 衔接当前组件交互和状态同步
const clone = value => JSON.parse(JSON.stringify(value))

// 创建新数据并写入当前配置
export const createTableCellChangePayload = (form, prop, mergeFieldOptions = []) => {
  const payload = clone(form)

  if (prop === 'mergeCells' && payload.mergeCells && !payload.mergeFields?.length) {
    payload.mergeFields = mergeFieldOptions.map(item => item.id)
  }

  if (prop === 'mergeFields' && !payload.mergeFields?.length) {
    payload.mergeCells = false
    payload.mergeFields = []
  }

  return payload
}
