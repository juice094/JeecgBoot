<script setup lang="ts">
// 列表筛选栏（对齐 GitHub .list-header，48px 口径）：
// 左侧 Open/Closed 计数切换（v-model），右侧筛选/排序下拉用 filters 插槽注入。
import { PhCheckCircle, PhCircle } from '@phosphor-icons/vue';

const props = withDefaults(
  defineProps<{
    openCount: number;
    closedCount: number;
    modelValue: 'open' | 'closed';
    openText?: string;
    closedText?: string;
  }>(),
  { openText: 'Open', closedText: 'Closed' },
);
const emit = defineEmits<{ (e: 'update:modelValue', value: 'open' | 'closed'): void }>();
</script>

<template>
  <div class="issue-list-header">
    <div class="issue-list-header__left">
      <button
        type="button"
        class="issue-list-header__tab"
        :class="{ 'issue-list-header__tab--active': props.modelValue === 'open' }"
        @click="emit('update:modelValue', 'open')"
      >
        <PhCircle :size="16" />{{ props.openText }}<span class="issue-list-header__count">{{ props.openCount }}</span>
      </button>
      <button
        type="button"
        class="issue-list-header__tab"
        :class="{ 'issue-list-header__tab--active': props.modelValue === 'closed' }"
        @click="emit('update:modelValue', 'closed')"
      >
        <PhCheckCircle :size="16" />{{ props.closedText }}<span class="issue-list-header__count">{{ props.closedCount }}</span>
      </button>
    </div>
    <div class="issue-list-header__right">
      <slot name="filters" />
    </div>
  </div>
</template>

<style scoped>
.issue-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  font-size: var(--font-size-md);
  color: var(--text-muted);
}

.issue-list-header__left {
  display: flex;
  gap: var(--spacing-lg);
}

.issue-list-header__tab {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 0;
  color: var(--text-muted);
  font-size: var(--font-size-md);
}
.issue-list-header__tab:hover {
  color: var(--text-regular);
}

.issue-list-header__tab--active {
  color: var(--text-main);
  font-weight: 600;
}

.issue-list-header__count {
  font-weight: 500;
}

.issue-list-header__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}
</style>
