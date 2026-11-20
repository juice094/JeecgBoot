<script setup lang="ts">
// 时间线紧凑事件行（对齐 GitHub .timeline-event）：
// 2px 竖线 + 24px 圆形徽章 + 单行文本，适合「某人 added label / change_status」类元事件。
// 正文类内容用 CommentCard 挂到 TimelineItem 里，不用本组件。
import { PhDot } from '@phosphor-icons/vue';
</script>

<template>
  <div class="timeline-event">
    <div class="timeline-event__badge">
      <slot name="icon">
        <PhDot :size="14" />
      </slot>
    </div>
    <div class="timeline-event__text">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.timeline-event {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) 0;
  font-size: var(--font-size-base);
  color: var(--text-muted);
}

.timeline-event::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 2px;
  background: var(--border-color-light);
}

.timeline-event__badge {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  margin-left: -11px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-body);
  color: var(--text-muted);
}

.timeline-event__text {
  flex: 1;
  min-width: 0;
}
.timeline-event__text strong {
  color: var(--text-main);
  font-weight: 600;
}
</style>
