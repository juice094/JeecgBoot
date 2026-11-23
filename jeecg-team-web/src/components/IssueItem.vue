<script setup lang="ts">
// 列表行单项（对齐 GitHub .issue-item）：状态符号 + 标题 + 标签 + 元信息 + 右侧计数/头像
import { PhChatCircle } from '@phosphor-icons/vue';
import IssueStateIcon from '@/components/IssueStateIcon.vue';
import LabelPill from '@/components/LabelPill.vue';
import type { IssueLabel, UnifiedState } from '@/constants/issue';

const props = defineProps<{
  state: UnifiedState;
  title: string;
  labels?: IssueLabel[];
  /** 元信息行，如「juice094#12 · opened 1d ago」 */
  meta?: string;
  commentCount?: number;
  assigneeName?: string;
}>();
const emit = defineEmits<{ (e: 'open'): void }>();

function initial(name?: string): string {
  return name ? name.trim().charAt(0).toUpperCase() : '';
}
</script>

<template>
  <li class="issue-item" @click="emit('open')">
    <div class="issue-item__state">
      <slot name="icon">
        <IssueStateIcon :state="props.state" />
      </slot>
    </div>
    <div class="issue-item__content">
      <a class="issue-item__title" @click.stop="emit('open')">{{ props.title }}</a>
      <LabelPill v-for="(label, i) in props.labels || []" :key="i" :text="label.text" :cls="label.cls" />
      <div v-if="props.meta" class="issue-item__meta">{{ props.meta }}</div>
    </div>
    <div class="issue-item__right">
      <slot name="actions" />
      <span v-if="props.commentCount" class="issue-item__count">
        <PhChatCircle :size="14" />{{ props.commentCount }}
      </span>
      <span v-if="props.assigneeName" class="avatar avatar--sm">{{ initial(props.assigneeName) }}</span>
    </div>
  </li>
</template>

<style scoped>
.issue-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  font-size: var(--font-size-base);
}
.issue-item:hover {
  background: var(--bg-subtle);
}
.issue-item:last-child {
  border-bottom: none;
}

.issue-item__state {
  flex-shrink: 0;
  margin-top: 2px;
}

.issue-item__content {
  flex: 1;
  min-width: 0;
}

.issue-item__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-main);
  margin-right: var(--spacing-xs);
}
.issue-item__title:hover {
  color: var(--primary);
}

.issue-item__meta {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.issue-item__right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  margin-top: 2px;
}

.issue-item__count {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
</style>
