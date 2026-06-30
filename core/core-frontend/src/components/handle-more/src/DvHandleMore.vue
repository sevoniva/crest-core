<script lang="ts" setup>
import { Icon } from '@/components/icon-custom'
import icon_more_outlined from '@/assets/svg/icon_more_outlined.svg'
import { propTypes } from '@/utils/propTypes'
import type { Placement } from 'element-plus-secondary'
import { computed, ref, PropType } from 'vue'
import ShareHandler from '@/views/share/share/ShareHandler.vue'
import { useShareStoreWithOut } from '@/store/modules/share'
import { useI18n } from '@/hooks/web/useI18n'
const shareStore = useShareStoreWithOut()
const { t } = useI18n()

export interface Menu {
  svgName?: string
  label?: string
  command: string
  divided?: boolean
  disabled?: boolean
  hidden?: boolean
}

// 定义更多操作菜单的展示参数和资源上下文
const props = defineProps({
  menuList: {
    type: Array as PropType<Menu[]>
  },
  placement: {
    type: String as () => Placement,
    default: 'bottom-end'
  },
  iconName: propTypes.string.def(''),
  inTable: propTypes.bool.def(false),
  resourceType: propTypes.string.def('dashboard'),
  node: {
    type: Object,
    default() {
      return {}
    }
  },
  anyManage: propTypes.bool.def(false)
})

// 计算当前分享功能是否被禁用
const shareDisable = computed(() => shareStore.getShareDisable)
// 持有分享处理组件实例
const shareComponent = ref(null)
// 根据权限状态生成可展示的菜单项
const menus = ref([
  ...props.menuList.map(item => {
    if (!props.anyManage && (item.command === 'copy' || item.command === 'move')) {
      item.hidden = true
    }
    return item
  })
])
// 处理菜单命令并分发给分享或父组件
const handleCommand = (command: string | number | object) => {
  if (command === 'share') {
    shareComponent.value.execute()
    return
  }
  emit('handleCommand', command)
}
// 在分享可用时将分享菜单追加到资源菜单中
const callBack = param => {
  if (shareDisable.value) {
    return
  }
  if (props.node.leaf && props.node?.weight >= 7) {
    menus.value.splice(0, 0, param)
  }
}
// 声明菜单命令事件
const emit = defineEmits(['handleCommand'])

// 判断菜单项是否需要禁用
const menuDisabledCheck = ele => {
  // do return
  return ele.disabled || (props.node.extraFlag1 === 0 && ['share', 'copy'].includes(ele.command))
}
</script>

<template>
  <el-dropdown
    popper-class="menu-more-dv_popper"
    :placement="placement"
    trigger="click"
    @command="handleCommand"
  >
    <el-icon class="hover-icon" :class="inTable && 'hover-icon-in-table'" @click.stop>
      <Icon><component class="svg-icon" :is="iconName || icon_more_outlined"></component></Icon>
    </el-icon>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          :divided="ele.divided"
          :command="ele.command"
          v-for="ele in menus"
          :key="ele.label"
          :disabled="menuDisabledCheck(ele)"
          :class="{
            'crest-hidden-drop-item':
              ele.hidden || (ele.command === 'cancelPublish' && node.extraFlag1 === 0)
          }"
        >
          <el-icon class="handle-icon" color="#646a73" size="16" v-if="ele.svgName">
            <Icon
              ><component
                class="svg-icon"
                :class="{ 'custom-disable': menuDisabledCheck(ele) }"
                :is="ele.svgName"
              ></component
            ></Icon>
          </el-icon>
          <el-tooltip
            class="box-item"
            effect="dark"
            :content="t('visualization.publish_tips2', [ele.label])"
            :disabled="!menuDisabledCheck(ele)"
            placement="top-start"
          >
            {{ ele.label }}
          </el-tooltip>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
  <ShareHandler
    v-if="!shareDisable"
    ref="shareComponent"
    :resource-id="props.node.id"
    :resource-type="props.resourceType"
    :weight="node.weight"
    @loaded="callBack"
  />
</template>

<style lang="less">
.custom-disable {
  color: var(--ed-text-color-disabled) !important;
}
.crest-hidden-drop-item {
  display: none !important;
}
.menu-more-dv_popper {
  min-width: 120px;
  margin-top: -2px !important;
}

.handle-icon {
  font-size: 16px;
  color: #646a73;
}
</style>
