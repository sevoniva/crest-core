export const DATASET_PARAMETER_FIELD_MARKER = '|DATASET_PARAM|'

export const isDatasetParameterFieldId = (fieldId?: string) =>
  typeof fieldId === 'string' && fieldId.includes(DATASET_PARAMETER_FIELD_MARKER)
