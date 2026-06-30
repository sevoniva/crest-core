export default async function runAnimation($el, animations = []) {
  // 整理输入数据并返回工具处理结果
  const play = animation =>
    new Promise<void>(resolve => {
      const { animationTime, value = '', isLoop } = animation
      $el.style.setProperty('--time', animationTime + 's')
      $el.classList.add(value, 'animated', utilsHandle(isLoop))
      // 移除当前数据并同步关联状态
      const removeAnimation = () => {
        $el.removeEventListener('animationend', removeAnimation)
        $el.removeEventListener('animationcancel', removeAnimation)
        $el.classList.remove(value, 'animated', utilsHandle(isLoop))
        $el.style.removeProperty('--time')
        resolve()
      }

      $el.addEventListener('animationend', removeAnimation)
      $el.addEventListener('animationcancel', removeAnimation)
    })

  for (let i = 0, len = animations.length; i < len; i++) {
    await play(animations[i])
  }
}

// 整理输入数据并返回工具处理结果
function utilsHandle(isLoop) {
  return isLoop ? 'infinite' : 'no-infinite'
}
