import request from '@/config/axios'

export const queryVisualizationBackground = () =>
  request.get({ url: '/visualization-background/list' }).catch(() => ({ data: {} }))
