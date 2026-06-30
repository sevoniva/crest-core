export const TREE_LEVEL_SEPARATOR = ' > '

export const normalizeTreeLevelValue = (value: string) => value

export const encodeTreeLevelValue = (value: string) => value.replace(/,/g, TREE_LEVEL_SEPARATOR)

export const decodeTreeLevelValue = (value: string) =>
  normalizeTreeLevelValue(value).split(TREE_LEVEL_SEPARATOR).join(',')

export const splitTreeLevelValue = (value: string) =>
  normalizeTreeLevelValue(value).split(TREE_LEVEL_SEPARATOR)

export const joinTreeLevelParts = (parts: string[]) => parts.join(TREE_LEVEL_SEPARATOR)
