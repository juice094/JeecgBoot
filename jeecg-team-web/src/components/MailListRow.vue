<script setup lang="ts">
// 邮箱消息行（对齐 Outlook 列表行）：
// 头像 32px（悬停/选中时复选框覆盖头像）｜发件人+主题（未读加粗）+摘要｜日期右对齐
// 选中 = 左侧 3px 主题色指示条 + 浅色主题底。
import { PhPaperclip } from '@phosphor-icons/vue';

const props = defineProps<{
  sender: string;
  subject: string;
  snippet?: string;
  /** 已读传 true；未读行发件人/主题加粗、日期主题色 */
  read?: boolean;
  selected?: boolean;
  time?: string;
  attachment?: boolean;
}>();
const emit = defineEmits<{
  (e: 'open'): void;
  (e: 'toggle', selected: boolean): void;
}>();

function initial(name: string): string {
  return name ? name.trim().charAt(0).toUpperCase() : '?';
}
</script>

<template>
  <li
    class="mail-row"
    :class="{ 'mail-row--unread': !props.read, 'mail-row--selected': props.selected }"
    @click="emit('open')"
  >
    <span class="mail-row__avatar">
      <span class="mail-row__initial">{{ initial(props.sender) }}</span>
      <input
        type="checkbox"
        class="mail-row__check"
        :checked="props.selected"
        :aria-label="'选择 ' + props.sender"
        @click.stop
        @change="emit('toggle', ($event.target as HTMLInputElement).checked)"
      />
    </span>
    <span class="mail-row__texts">
      <span class="mail-row__line1">
        <span class="mail-row__sender">{{ props.sender }}</span>
        <PhPaperclip v-if="props.attachment" :size="12" />
      </span>
      <span class="mail-row__line2">
        <span class="mail-row__subject">{{ props.subject }}</span>
        <span v-if="props.snippet" class="mail-row__snippet">{{ props.snippet }}</span>
      </span>
    </span>
    <span class="mail-row__time">{{ props.time }}</span>
  </li>
</template>

<style scoped>
.mail-row {
  position: relative;
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) 64px;
  align-items: start;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  cursor: pointer;
  font-size: var(--font-size-base);
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color-light);
}
.mail-row:hover {
  background: var(--bg-subtle);
}
.mail-row--selected {
  background: var(--primary-50);
}
/* 左侧选中指示条（Outlook 口径） */
.mail-row--selected::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--primary);
}

/* 头像与复选框互斥（悬停/选中时复选框覆盖头像） */
.mail-row__avatar {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary-200);
  color: var(--primary-700);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-base);
  font-weight: 600;
  flex-shrink: 0;
}
.mail-row__check {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  opacity: 0;
  accent-color: var(--primary);
  cursor: pointer;
}
.mail-row:hover .mail-row__check,
.mail-row--selected .mail-row__check {
  opacity: 1;
  background: var(--bg-card);
  border-radius: 50%;
  appearance: none;
  border: 1px solid var(--border-strong);
}
.mail-row:hover .mail-row__check:checked,
.mail-row--selected .mail-row__check:checked {
  appearance: auto;
  border: none;
}

.mail-row__texts {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mail-row__line1,
.mail-row__line2 {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  min-width: 0;
}

.mail-row__sender {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 400;
}
.mail-row--unread .mail-row__sender {
  font-weight: 600;
  color: var(--text-main);
}

.mail-row__subject {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 400;
}
.mail-row--unread .mail-row__subject {
  font-weight: 600;
  color: var(--text-main);
}
.mail-row__snippet {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.mail-row__time {
  text-align: right;
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.mail-row--unread .mail-row__time {
  color: var(--primary);
  font-weight: 600;
}
</style>
