<script setup lang="ts">
// 评论卡片（对齐 GitHub .comment-box）：头部作者/时间/徽章 + 正文 + 底部操作区
// 正文默认按 Markdown 渲染（utils/markdown 先转义再拼标签，无注入面），纯文本传 :markdown="false"。
import { computed } from 'vue';
import { renderMarkdown } from '@/utils/markdown';

const props = withDefaults(
  defineProps<{
    author: string;
    /** 头部右侧说明，如「opened 1d ago」 */
    subtitle?: string;
    /** 头衔徽章，如 ['Owner', 'Author'] */
    badges?: string[];
    body: string;
    markdown?: boolean;
  }>(),
  { markdown: true },
);

const bodyHtml = computed(() => (props.markdown ? renderMarkdown(props.body) : ''));
</script>

<template>
  <div class="comment-card">
    <div class="comment-card__header">
      <div class="comment-card__author">
        <strong>{{ props.author }}</strong>
        <span v-if="props.subtitle" class="comment-card__subtitle">{{ props.subtitle }}</span>
      </div>
      <div v-if="props.badges?.length" class="comment-card__badges">
        <span v-for="(badge, i) in props.badges" :key="i" class="comment-card__badge">{{ badge }}</span>
      </div>
    </div>
    <div v-if="props.markdown" class="comment-card__body md-body" v-html="bodyHtml" />
    <div v-else class="comment-card__body">{{ props.body }}</div>
    <div v-if="$slots.footer" class="comment-card__footer">
      <slot name="footer" />
    </div>
  </div>
</template>

<style scoped>
.comment-card {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-body);
  overflow: hidden;
}

.comment-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  font-size: var(--font-size-base);
  color: var(--text-muted);
}

.comment-card__author {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  min-width: 0;
}
.comment-card__author strong {
  color: var(--text-main);
}

.comment-card__badges {
  display: flex;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}

.comment-card__badge {
  border: 1px solid var(--border-color);
  padding: 2px var(--spacing-sm);
  border-radius: var(--radius-full);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.comment-card__body {
  padding: var(--spacing-lg);
  font-size: var(--font-size-base);
  color: var(--text-regular);
  line-height: 1.6;
}

.comment-card__footer {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
}
</style>
