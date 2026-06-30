<template>
  <editor v-model="myValue" :init="init" :disabled="disabled" :id="tinymceId"></editor>
</template>

<script setup lang="ts">
import { formatEmbeddedUrl } from '@/utils/url'
// TinyMCE 主题和基础组件。
import tinymce from 'tinymce/tinymce'
import Editor from '@tinymce/tinymce-vue'
import 'tinymce/themes/silver'
import 'tinymce/themes/silver/theme'
import 'tinymce/icons/default' // 编辑器图标。
import 'tinymce/models/dom' // TinyMCE DOM 模型依赖。

// TinyMCE 插件。
import 'tinymce/icons/default/icons'
import 'tinymce/plugins/table' // 插入表格插件
import 'tinymce/plugins/lists' // 列表插件
import 'tinymce/plugins/wordcount' // 字数统计插件
import 'tinymce/plugins/code' // 源码
import './plugins' // 自定义插件。
import '@npkg/tinymce-plugins/letterspacing'

import { reactive, ref } from 'vue'
import { onMounted, watch } from 'vue'
import axios from 'axios'
// import { updateImg } from '@/api/order/order'
const emits = defineEmits(['getContent'])
// 通过 props 暴露编辑器配置，便于复用到不同富文本场景。
const props = defineProps({
  value: {
    type: String,
    default: () => {
      return ''
    }
  },
  baseUrl: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  plugins: {
    type: [String, Array],
    default: 'lists  table'
  }, // TinyMCE 插件配置。
  toolbar: {
    type: [String, Array],
    default:
      'codesample bold italic underline alignleft aligncenter alignright alignjustify | undo redo | formatselect | fontselect | fontsizeselect | forecolor backcolor | bullist numlist outdent indent | lists link table code | removeformat letterspacing '
  } // TinyMCE 工具栏配置。
})
// 与外部 value 保持同步。
const myValue = ref(props.value)
// 衔接当前组件交互和状态同步
const tinymceId = ref('vue-tinymce-' + +new Date() + ((Math.random() * 1000).toFixed(0) + ''))
// TinyMCE 初始化配置。
const init = reactive({
  selector: '#' + tinymceId.value, // 富文本编辑器实例 ID。
  language_url: formatEmbeddedUrl('./tinymce-crest-private/langs/zh_CN.js'), // 中文语言包。
  language: 'zh_CN',
  skin_url: formatEmbeddedUrl('./tinymce-crest-private/skins/ui/oxide'), // 编辑器皮肤资源。
  height: 400,
  branding: false,
  menubar: true,
  image_dimensions: false, // 上传图片不写入固定宽高。
  plugins: props.plugins,
  toolbar: props.toolbar,
  font_formats:
    'Arial=arial,helvetica,sans-serif; 宋体=SimSun; 微软雅黑=Microsoft Yahei; Impact=impact,chicago;', // 字体清单。
  fontsize_formats: '11px 12px 14px 16px 18px 24px 36px 48px 64px 72px',
  paste_webkit_styles: 'all',
  paste_merge_formats: true,
  nonbreaking_force_tab: false,
  paste_auto_cleanup_on_paste: false,
  file_picker_types: 'file',
  content_css: formatEmbeddedUrl('./tinymce-crest-private/skins/content/default/content.css'), // 编辑区内容样式。
  // 图片上传。
  images_upload_handler: blobInfo =>
    new Promise((resolve, reject) => {
      if (blobInfo.blob().size / 1024 / 1024 > 2) {
        reject({ message: '上传失败，图片大小请控制在 2M 以内', remove: true })
        return
      } else {
        const ph = import.meta.env.VITE_BASE_PATH + ':' + import.meta.env.VITE_SERVER_PORT + '/'
        let params = new FormData()
        params.append('file', blobInfo.blob())

        let config = {
          headers: {
            'Content-Type': 'multipart/form-data'
          }
        }

        axios
          .post('xxxx', params, config)
          .then(res => {
            if (res.data.code == 200) {
              resolve(ph + res.data.msg) // 返回可访问的图片地址。
            } else {
              reject('HTTP Error: 上传失败' + res.data.code)
              return
            }
          })
          .catch(() => {
            reject('上传出错，服务器开小差了呢')
            return
          })
      }
    }),

  // 文件上传
  file_picker_callback: (callback, value, meta) => {
    // Provide file and text for the link dialog
    if (meta.filetype == 'file') {
      callback('mypage.html', { text: 'My text' })
    }

    // Provide image and alt text for the image dialog
    if (meta.filetype == 'image') {
      callback('myimage.jpg', { alt: 'My alt text' })
    }

    // Provide alternative source and posted for the media dialog
    if (meta.filetype == 'media') {
      callback('movie.mp4', { source2: 'alt.ogg', poster: 'image.jpg' })
    }
  }
})

// 同步外部传入内容。
watch(
  () => props.value,
  () => {
    myValue.value = props.value
    emits('getContent', myValue.value)
  }
)
// 将编辑器内容变更向外抛出。
watch(
  () => myValue.value,
  () => {
    emits('getContent', myValue.value)
  }
)
// 组件挂载后初始化编辑器。
onMounted(() => {
  tinymce.init({})
})
</script>
<style scoped lang="less"></style>
