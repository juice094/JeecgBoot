<script setup lang="ts">
// 读信窗格（对齐 Outlook reading pane）：
// 主题（17px/600）+ 发件人行（40px 头像 / 姓名<地址>主题色 / 右侧回复转发动作+时间）
// + 信息条（警告底 + 右侧操作）+ 正文区（占位/mock）。
import { PhArrowUUpLeft, PhArrowUUpRight, PhInfo } from '@phosphor-icons/vue';

const props = withDefaults(
  defineProps<{
    subject?: string;
    senderName?: string;
    /** 脱敏地址 hash 或占位 */
    senderAddress?: string;
    /** 收件人行说明（如 收件人: xxx） */
    recipientLine?: string;
    time?: string;
    /** 信息条文本（为空则不显示） */
    notice?: string;
    noticeActionText?: string;
    body?: string;
  }>(),
  { noticeActionText: '知道了' },
);
const emit = defineEmits<{
  (e: 'reply'): void;
  (e: 'replyAll'): void;
  (e: 'forward'): void;
  (e: 'noticeAction'): void;
}>();

function initial(name?: string): string {
  return name ? name.trim().charAt(0).toUpperCase() : '?';
}
</script>

<template>
  <article class="reader">
    <h2 class="reader__subject">{{ props.subject || '（无主题）' }}</h2>

    <div class="reader__sender-row">
      <span class="reader__avatar">{{ initial(props.senderName) }}</span>
      <div class="reader__sender-meta">
        <div class="reader__sender-name">
          {{ props.senderName || '未知发件人' }}
          <span v-if="props.senderAddress" class="reader__sender-addr">&lt;{{ props.senderAddress }}&gt;</span>
        </div>
        <div v-if="props.recipientLine" class="reader__recipient">{{ props.recipientLine }}</div>
      </div>
      <div class="reader__actions">
        <button type="button" class="icon-btn" title="回复" @click="emit('reply')">
          <PhArrowUUpLeft :size="16" />
        </button>
        <button type="button" class="icon-btn" title="全部答复" @click="emit('replyAll')">
          <PhArrowUUpRight :size="16" style="transform: scaleX(-1)" />
        </button>
        <button type="button" class="icon-btn" title="转发" @click="emit('forward')">
          <PhArrowUUpRight :size="16" />
        </button>
        <span v-if="props.time" class="reader__time">{{ props.time }}</span>
      </div>
    </div>

    <div v-if="props.notice" class="reader__notice">
      <PhInfo :size="15" class="reader__notice-icon" />
      <span class="reader__notice-text">{{ props.notice }}</span>
      <button type="button" class="btn btn--sm btn-default" @click="emit('noticeAction')">
        {{ props.noticeActionText }}
      </button>
    </div>

    <div class="reader__body">
      {{ props.body || '（正文占位）' }}
    </div>
  </article>
</template>

<style scoped>
.reader {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg) var(--spacing-2xl);
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-left: none;
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  min-height: 420px;
  overflow-y: auto;
}

.reader__subject {
  margin: 0;
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-main);
  line-height: 1.4;
}

.reader__sender-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.reader__avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--primary-200);
  color: var(--primary-700);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-lg);
  font-weight: 600;
  flex-shrink: 0;
}

.reader__sender-meta {
  flex: 1;
  min-width: 0;
}

.reader__sender-name {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reader__sender-addr {
  font-weight: 400;
}

.reader__recipient {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.reader__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}

.reader__time {
  margin-left: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  white-space: nowrap;
}

/* 信息条（Outlook infobar 口径：浅色警告底 + 右侧操作） */
.reader__notice {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 10px var(--spacing-lg);
  background: var(--warning-50);
  border: 1px solid var(--warning-border);
  border-radius: var(--radius-sm);
  color: var(--warning-700);
  font-size: var(--font-size-md);
}
.reader__notice-icon {
  flex-shrink: 0;
}
.reader__notice-text {
  flex: 1;
  min-width: 0;
}

.reader__body {
  font-size: var(--font-size-base);
  line-height: 1.6;
  color: var(--text-regular);
  white-space: pre-wrap;
}
</style>
