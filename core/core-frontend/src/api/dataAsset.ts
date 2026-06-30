import request from '@/config/axios'

// 数据资产分页查询条件
export interface DataAssetRequest {
  keyword?: string
  assetType?: string
  certified?: boolean
  recommended?: boolean
  deprecated?: boolean
  ownerId?: string | number
}

// 数据资产基础信息
export interface DataAsset {
  assetType: string
  assetTypeLabel?: string
  assetId: string
  name: string
  extraType?: string
  parentAssetType?: string
  parentAssetId?: string
  description?: string
  tags?: string
  certified?: boolean
  recommended?: boolean
  deprecated?: boolean
  canManage?: boolean
  upstreamCount?: number
  downstreamCount?: number
  ownerId?: string | number
  ownerName?: string
  creatorId?: string | number
  creatorName?: string
  orgId?: string | number
  orgName?: string
  createTime?: number
  updateTime?: number
}

// 数据资产影响分析明细项
export interface DataAssetImpactItem {
  assetType: string
  assetTypeLabel?: string
  assetId: string
  name: string
  relation?: string
  updateTime?: number
}

// 数据资产影响分析结果
export interface DataAssetImpact {
  assetType: string
  assetId: string
  summary: Record<string, number>
  items: DataAssetImpactItem[]
}

// 数据资产详情数据
export interface DataAssetDetail {
  asset: DataAsset
  upstream: DataAssetImpactItem[]
  downstream: DataAssetImpactItem[]
  impact: DataAssetImpact
}

// 数据资产负责人信息
export interface DataAssetOwner {
  id: string | number
  name: string
  account?: string
}

// 数据资产画像保存请求
export interface DataAssetProfileRequest {
  assetType: string
  assetId: string
  description?: string
  tags?: string
  ownerId?: string | number | null
  certified?: boolean
  recommended?: boolean
  deprecated?: boolean
}

// 分页查询数据资产
export const dataAssetPageApi = (page: number, pageSize: number, data: DataAssetRequest = {}) =>
  request.post({ url: `/data-assets/page/${page}/${pageSize}`, data })

// 查询数据资产详情
export const dataAssetDetailApi = (assetType: string, assetId: string | number) =>
  request.get({ url: `/data-assets/${assetType}/${assetId}` })

// 查询数据资产影响范围
export const dataAssetImpactApi = (assetType: string, assetId: string | number) =>
  request.get({ url: `/data-assets/${assetType}/${assetId}/impact` })

// 保存数据资产画像
export const saveDataAssetProfileApi = (data: DataAssetProfileRequest) =>
  request.post({ url: '/data-assets/profile', data })

// 查询数据资产负责人列表
export const dataAssetOwnersApi = () => request.get({ url: '/data-assets/owners' })
