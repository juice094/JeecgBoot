<script setup lang="ts">
// 邮箱文件夹窗格（对齐 Outlook 左栏）：可折叠分组头 + 文件夹项（图标/名称/计数）
// 激活态 = 浅灰底 + 字重 600（Outlook 口径，非主题色高亮）。无卡片边框。
import { PhCaretRight, PhEnvelopeSimple } from '@phosphor-icons/vue';

export interface MailFolderItem {
  key: string;
  label: string;
  count?: number;
}

withDefaults(
  defineProps<{
    folders: MailFolderItem[];
    modelValue: string;
    /** 分组标题（如「收藏夹」「文件夹」） */
    groupTitle?: string;
  }>(),
  { groupTitle: '文件夹' },
);
const emit = defineEmits<{
  (e: 'update:modelValue', key: string): void;
}>();
</script>

<template>
  <nav class="mail-nav">
    <div class="mail-nav__group">
      <button type="button" class="mail-nav__group-title">
        <PhCaretRight :size="12" class="mail-nav__caret" />
        {{ groupTitle }}
      </button>
      <ul class="mail-nav__list">
        <li v-for="f in folders" :key="f.key">
          <button
            type="button"
            class="mail-nav__item"
            :class="{ 'mail-nav__item--active': modelValue === f.key }"
            @click="emit('update:modelValue', f.key)"
          >
            <PhEnvelopeSimple :size="15" class="mail-nav__icon" />
            <span class="mail-nav__label">{{ f.label }}</span>
            <span v-if="f.count" class="mail-nav__count">{{ f.count }}</span>
          </button>
        </li>
      </ul>
    </div>

    <slot name="sections" />
  </nav>
</template>

<style scoped>
.mail-nav {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-xs);
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.mail-nav__group-title {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  height: 28px;
  padding: 0 var(--spacing-sm);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-muted);
  text-align: left;
}
.mail-nav__group-title:hover {
  color: var(--text-main);
}
.mail-nav__caret {
  opacity: 0.7;
}

.mail-nav__list {
  display: flex;
  flex-direction: column;
}

.mail-nav__item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
  height: 32px;
  padding: 0 var(--spacing-sm);
  border-radius: var(--radius-sm);
  color: var(--text-regular);
  font-size: var(--font-size-base);
  text-align: left;
}
.mail-nav__item:hover {
  background: var(--bg-subtle);
  color: var(--text-main);
}

/* Outlook 口径：激活=浅灰底+加粗，不用主题色 */
.mail-nav__item--active {
  background: var(--bg-muted);
  color: var(--text-main);
  font-weight: 600;
}

.mail-nav__icon {
  flex-shrink: 0;
  opacity: 0.75;
}

.mail-nav__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mail-nav__count {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
</style>
