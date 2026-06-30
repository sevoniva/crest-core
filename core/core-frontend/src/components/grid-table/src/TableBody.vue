<script lang="ts">
// 衔接当前组件交互和状态同步
const TableBody = (props, context) => {
  const nodes = []
  const { columns } = props
  const [slots] = context.slots.default()
  const { children = [] } = slots || {}
  if (!columns?.length) return children
  children.forEach(ele => {
    const { prop, key, type } = ele.props || {}
    if (columns.includes(prop) || key === '_operation' || type === 'selection') {
      nodes.push(ele)
    }
  })
  return nodes
}
export default TableBody
</script>
