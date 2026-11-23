<script setup lang="ts">
// 斜杠命令面板 —— owner: qingyanjunzi
// 输入 '/' 唤起；↑↓ 选择，Enter 执行，Esc 关闭；点击亦可。
import { onMounted, ref } from 'vue';
import { matchCommands, type ChatCommand } from '../commands';

const props = defineProps<{ input: string }>();
const emit = defineEmits<{ (e: 'pick', cmd: ChatCommand): void; (e: 'close'): void }>();

const activeIndex = ref(0);
const list = matchCommands(props.input);
activeIndex.value = 0;

function pick(cmd: ChatCommand) {
  emit('pick', cmd);
}

function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault();
    activeIndex.value = (activeIndex.value + 1) % list.length;
  } else if (event.key === 'ArrowUp') {
    event.preventDefault();
    activeIndex.value = (activeIndex.value - 1 + list.length) % list.length;
  } else if (event.key === 'Enter') {
    event.preventDefault();
    if (list[activeIndex.value]) pick(list[activeIndex.value]);
  } else if (event.key === 'Escape') {
    event.preventDefault();
    emit('close');
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeyDown, true);
});
defineExpose({
  destroy: () => window.removeEventListener('keydown', onKeyDown, true),
});
</script>

<template>
  <div class="palette">
    <div v-if="!list.length" class="palette__empty">无匹配命令</div>
    <div
      v-for="(cmd, i) in list"
      :key="cmd.name"
      class="palette__item"
      :class="{ 'palette__item--active': i === activeIndex }"
      @mouseenter="activeIndex = i"
      @click="pick(cmd)"
    >
      <component :is="cmd.icon" :size="18" class="palette__icon" />
      <div class="palette__main">
        <div class="palette__title">/{{ cmd.name }} <span>{{ cmd.title }}</span></div>
        <div class="palette__desc">{{ cmd.description }}</div>
      </div>
      <div v-if="cmd.action.type === 'form'" class="palette__tag">会话内填表</div>
      <div v-else class="palette__tag palette__tag--nav">打开工作台</div>
    </div>
  </div>
</template>

<style scoped>
.palette {
  position: absolute;
  bottom: calc(100% + 8px);
  left: 0;
  right: 0;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-popover);
  overflow: hidden;
  z-index: 20;
  max-height: 320px;
  overflow-y: auto;
}
.palette__empty {
  padding: 16px;
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}
.palette__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  cursor: pointer;
}
.palette__item--active {
  background: var(--primary-light);
}
.palette__icon {
  color: var(--primary);
  flex-shrink: 0;
}
.palette__main {
  flex: 1;
  min-width: 0;
}
.palette__title {
  font-size: var(--font-size-base);
  font-weight: 500;
}
.palette__title span {
  font-weight: normal;
  color: var(--text-regular);
  margin-left: 6px;
}
.palette__desc {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.palette__tag {
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--primary);
  background: var(--primary-light);
  border-radius: 9px;
  padding: 2px 8px;
}
.palette__tag--nav {
  color: var(--text-muted);
  background: var(--bg-body);
}
</style>
