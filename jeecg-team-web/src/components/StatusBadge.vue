<script setup lang="ts">
// 状态徽章：详情页头部用的实心胶囊（对齐 GitHub prc-StateLabel）
// 与列表页的无底色符号是两种不同组件，不要混用。
defineProps<{
  /** 展示文案，如「待办」「进行中」「已完成」 */
  label: string;
  /** 语义色：open / active / done / closed */
  tone?: 'open' | 'active' | 'done' | 'closed';
}>();
</script>

<template>
  <span class="status-badge" :class="'status-badge--' + (tone || 'open')">
    <span class="status-badge__dot"></span>{{ label }}
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 12px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-base);
  font-weight: 600;
  line-height: 1;
  color: #fff;
  white-space: nowrap;
}

.status-badge__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.9;
}

.status-badge--open { background: var(--success); }
.status-badge--active { background: var(--warning); }
.status-badge--done { background: var(--primary); }
.status-badge--closed { background: var(--gray-500); }
</style>
