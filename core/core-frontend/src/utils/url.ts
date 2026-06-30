import { useEmbedded } from '@/store/modules/embedded'
const embeddedStore = useEmbedded()
// 维护表单数据和校验规则
export const formatEmbeddedUrl = (url: string) => {
  return embeddedStore.baseUrl ? `${embeddedStore.baseUrl}${url}` : url
}
