<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue'
import request from '@/config/axios'
import { ElMessage } from 'element-plus-secondary'
import { useAppearanceStoreWithOut } from '@/store/modules/appearance'

const appearanceStore = useAppearanceStoreWithOut()
// 标记异步操作的加载状态
const loading = ref(false)
// 维护表单数据和校验规则
const form = reactive({
  title: 'Crest'
})

const load = async () => {
  loading.value = true
  try {
    const res = await request.get({ url: '/sys-parameter/basic/list' })
    const item = res.data?.find(ele => ele.pkey === 'basic.siteTitle')
    form.title = item?.pval || 'Crest'
  } finally {
    loading.value = false
  }
}

// 校验并保存当前配置
const save = async () => {
  const title = form.title.trim() || 'Crest'
  await request.post({
    url: '/sys-parameter/basic/record',
    data: [{ pkey: 'basic.siteTitle', pval: title, type: 'text', sort: 9 }]
  })
  form.title = title
  await appearanceStore.setAppearance(false, true)
  ElMessage.success('保存成功')
}

onMounted(load)
</script>

<template>
  <div class="site-setting" v-loading="loading">
    <p class="router-title">站点设置</p>
    <div class="sys-setting-p">
      <div class="setting-panel">
        <div class="setting-head">
          <div class="setting-title">浏览器标题</div>
          <div class="setting-desc">用于登录页、工作台和系统页面的浏览器标签标题。</div>
        </div>
        <el-form label-position="top" class="site-form">
          <el-form-item>
            <el-input
              v-model.trim="form.title"
              maxlength="40"
              show-word-limit
              placeholder="请输入浏览器标题"
            />
            <div class="form-tip">实际显示为 {{ form.title || 'Crest' }}</div>
          </el-form-item>
          <el-button type="primary" @click="save">保存</el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<style lang="less" scoped>
.site-setting {
  min-height: 100%;
}
.router-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}
.sys-setting-p {
  width: 100%;
  height: auto;
  box-sizing: border-box;
  margin-top: 12px;
  overflow-y: auto;
}
.setting-panel {
  max-width: 720px;
  padding: 24px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}
.setting-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}
.setting-desc,
.form-tip {
  margin-top: 8px;
  font-size: 13px;
  color: #64748b;
}
.site-form {
  margin-top: 16px;
  width: 420px;
}
</style>
