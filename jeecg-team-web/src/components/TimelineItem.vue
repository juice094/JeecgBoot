<script setup lang="ts">
// 时间线单项：2px 竖线 + 32px 圆形徽章（对齐 GitHub prc-Timeline）
// 徽章用负边距压在竖线上，加 2px 卡片色描边制造镂空感。
</script>

<template>
  <li class="timeline-item">
    <div class="timeline-item__badge">
      <slot name="badge">●</slot>
    </div>
    <div class="timeline-item__body">
      <slot />
    </div>
  </li>
</template>

<style scoped>
.timeline-item {
  position: relative;
  display: flex;
  padding: var(--spacing-lg) 0;
}

.timeline-item::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 2px;
  background: var(--border-color-light);
}

.timeline-item__badge {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin-left: -15px;
  margin-right: var(--spacing-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--gray-100);
  border: 2px solid var(--bg-card);
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.timeline-item__body {
  flex: 1;
  min-width: 0;
  margin-top: 5px;
}
</style>
