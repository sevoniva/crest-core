<template>
  <div class="observability-page" v-loading="loading">
    <div class="observability-toolbar">
      <div>
        <p class="observability-title">监控接入</p>
        <p class="observability-subtitle">确认 Prometheus 抓取地址、访问凭据和 Grafana 入口。</p>
      </div>
      <el-button secondary :icon="Refresh" @click="loadStatus">刷新</el-button>
    </div>

    <div class="observability-grid">
      <section class="observability-panel">
        <div class="panel-head">
          <div>
            <p class="panel-title">Prometheus</p>
            <p class="panel-desc">后端暴露给 Prometheus 的抓取配置。</p>
          </div>
          <span class="status-pill" :class="{ enabled: prometheus.enabled }">
            {{ prometheus.enabled ? '已开启' : '未开启' }}
          </span>
        </div>

        <div class="setting-row">
          <span>采集端点</span>
          <div class="setting-value">
            <code>{{ prometheus.endpoint || '/api/v1/actuator/prometheus' }}</code>
            <el-button
              text
              :icon="CopyDocument"
              @click="copyValue(prometheus.endpoint || '/api/v1/actuator/prometheus')"
            />
          </div>
        </div>
        <div class="setting-row">
          <span>认证方式</span>
          <strong>{{ prometheus.authType || 'Bearer Token' }}</strong>
        </div>
        <div class="setting-row">
          <span>Token 状态</span>
          <span class="token-state" :class="{ ready: prometheus.tokenConfigured }">
            {{ prometheus.tokenConfigured ? '已配置' : '未配置' }}
          </span>
        </div>
        <div class="setting-row">
          <span>URI 标签上限</span>
          <strong>{{ prometheus.maxUriTags || 200 }}</strong>
        </div>
      </section>

      <section class="observability-panel">
        <div class="panel-head">
          <div>
            <p class="panel-title">Grafana</p>
            <p class="panel-desc">已配置的访问入口和预置看板。</p>
          </div>
          <span class="status-pill" :class="{ enabled: grafana.enabled }">
            {{ grafana.enabled ? '已开启' : '未开启' }}
          </span>
        </div>

        <div class="setting-row">
          <span>访问地址</span>
          <div class="setting-value">
            <code>{{ grafana.publicUrl || '未配置' }}</code>
            <el-button
              v-if="grafana.publicUrl"
              text
              :icon="Link"
              @click="openGrafana(grafana.publicUrl)"
            />
          </div>
        </div>
        <div v-if="grafana.provisionedDashboards.length" class="dashboard-list">
          <span v-for="item in grafana.provisionedDashboards" :key="item">{{ item }}</span>
        </div>
        <div v-else class="empty-text">暂无预置看板</div>
      </section>
    </div>

    <section class="observability-panel full">
      <div class="panel-head compact">
        <div>
          <p class="panel-title">部署提示</p>
          <p class="panel-desc">按实际部署方式确认抓取链路和密钥来源。</p>
        </div>
        <el-icon class="guard-icon"><Monitor /></el-icon>
      </div>
      <div class="security-list">
        <div>
          <el-icon><CircleCheck /></el-icon>
          <span>默认不开放指标端点，需显式设置 <code>CREST_PROMETHEUS_ENABLED=true</code>。</span>
        </div>
        <div>
          <el-icon><CircleCheck /></el-icon>
          <span>启用后必须配置 <code>CREST_PROMETHEUS_TOKEN</code>，否则请求会被拒绝。</span>
        </div>
        <div>
          <el-icon><CircleCheck /></el-icon>
          <span>Prometheus 请访问后端服务地址，不要从前端域名转发。</span>
        </div>
        <div>
          <el-icon><Lock /></el-icon>
          <span>Token 只从环境变量、Kubernetes Secret 或外部密钥系统读取，本页不展示明文。</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue'
import { CircleCheck, CopyDocument, Link, Lock, Monitor, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus-secondary'
import { observabilityStatusApi } from '@/api/observability'

interface PrometheusStatus {
  enabled?: boolean
  endpoint?: string
  authType?: string
  tokenConfigured?: boolean
  maxUriTags?: number
}

interface GrafanaStatus {
  enabled?: boolean
  publicUrl?: string
  provisionedDashboards?: string[]
}

// 页面加载状态，刷新监控配置时用于展示遮罩
const loading = ref(false)
// 后端返回的 Prometheus 与 Grafana 当前配置状态
const status = ref<{ prometheus?: PrometheusStatus; grafana?: GrafanaStatus }>({})

// Prometheus 状态对象，未返回时使用空对象避免模板空判断
const prometheus = computed(() => status.value.prometheus || {})
// Grafana 状态对象，补齐默认看板列表
const grafana = computed(() => ({
  provisionedDashboards: [],
  ...(status.value.grafana || {})
}))

// 从后端加载当前监控接入配置状态
const loadStatus = async () => {
  loading.value = true
  try {
    const res = await observabilityStatusApi()
    status.value = res.data || {}
  } finally {
    loading.value = false
  }
}

// 复制端点地址，并在浏览器权限不足时提示失败
const copyValue = async (value?: string) => {
  if (!value) {
    return
  }
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('已复制')
  } catch (e) {
    ElMessage.warning('复制失败')
  }
}

// 在新窗口打开 Grafana 公开访问地址
const openGrafana = (url: string) => {
  window.open(url, '_blank', 'noopener,noreferrer')
}

onMounted(loadStatus)
</script>

<style lang="less" scoped>
.observability-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.observability-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.observability-title {
  margin: 0;
  color: #0f172a;
  font-size: 16px;
  font-weight: 700;
  line-height: 24px;
}

.observability-subtitle,
.panel-desc {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 20px;
}

.observability-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.observability-panel {
  padding: 18px 20px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.observability-panel.full {
  width: 100%;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.panel-head.compact {
  margin-bottom: 14px;
}

.panel-title {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  line-height: 22px;
}

.status-pill {
  flex: none;
  padding: 3px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 999px;
  color: #475569;
  background: #f8fafc;
  font-size: 12px;
  font-weight: 700;
  line-height: 18px;
}

.status-pill.enabled {
  border-color: #86efac;
  color: #15803d;
  background: #f0fdf4;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 38px;
  border-top: 1px solid #f1f5f9;
  color: #475569;
  font-size: 13px;
}

.setting-row:first-of-type {
  border-top: 0;
}

.setting-row strong {
  color: #0f172a;
}

.setting-value {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

code {
  overflow: hidden;
  max-width: 360px;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.token-state {
  color: #b45309;
  font-weight: 700;
}

.token-state.ready {
  color: #15803d;
}

.dashboard-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard-list span {
  padding: 5px 10px;
  border-radius: 999px;
  color: #1e293b;
  background: #f1f5f9;
  font-size: 12px;
  font-weight: 600;
}

.empty-text {
  color: #94a3b8;
  font-size: 13px;
  line-height: 28px;
}

.guard-icon {
  color: #2563eb;
  font-size: 22px;
}

.security-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 18px;
}

.security-list div {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: #334155;
  font-size: 13px;
  line-height: 20px;
}

.security-list .ed-icon {
  margin-top: 2px;
  color: #2563eb;
}

.security-list code {
  max-width: none;
  color: #0f172a;
  white-space: normal;
}

@media (max-width: 960px) {
  .observability-grid,
  .security-list {
    grid-template-columns: 1fr;
  }
}
</style>
