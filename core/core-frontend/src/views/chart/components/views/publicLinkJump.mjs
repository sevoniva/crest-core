// 根据当前数据计算界面可用状态
export const isPublicLinkHash = hash => String(hash || '').startsWith('#/link/')

export const buildPublicLinkTargetUrl = ({
  hash,
  targetDvId,
  targetDvType,
  attachParamsInfo = '',
  jumpInfoParam = '',
  editPreviewParams = ''
}) => {
  const currentHash = String(hash || '')
  const [linkPath, queryText = ''] = currentHash.split('?')
  const currentParams = new URLSearchParams(queryText)
  const params = new URLSearchParams()
  const ticket = currentParams.get('ticket')
  if (ticket) {
    params.set('ticket', ticket)
  }
  params.set('targetDvId', String(targetDvId))
  if (targetDvType) {
    params.set('targetDvType', String(targetDvType))
  }
  params.set('fromLink', 'true')
  let url = `${linkPath}?${params.toString()}`
  if (attachParamsInfo) {
    url += attachParamsInfo
  } else {
    url += '&ignoreParams=true'
  }
  return url + jumpInfoParam + editPreviewParams
}
