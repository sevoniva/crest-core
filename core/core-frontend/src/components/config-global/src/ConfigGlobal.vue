<script setup lang="ts">
import { computed } from 'vue'
import { ElConfigProvider } from 'element-plus-secondary'
import { useLocaleStore } from '@/store/modules/locale'

// 多语言相关
const localeStore = useLocaleStore()

// 记录当前选中项和交互焦点
const currentLocale = computed(() => localeStore.currentLocale)
</script>

<template>
  <ElConfigProvider :locale="currentLocale.elLocale" namespace="ed">
    <slot></slot>
  </ElConfigProvider>
</template>
