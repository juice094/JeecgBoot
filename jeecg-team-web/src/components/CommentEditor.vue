<script setup lang="ts">
// 评论编辑器（对齐 GitHub .comment-editor）：Write/Preview 页签 + 输入区 + 底部操作
// v-model 绑定正文；submit 事件由父级接后端；close 事件仅在使用 show-close 时出现。
import { computed, ref } from 'vue';
import { renderMarkdown } from '@/utils/markdown';

const props = withDefaults(
  defineProps<{
    modelValue: string;
    placeholder?: string;
    submitText?: string;
    showClose?: boolean;
    closeText?: string;
    busy?: boolean;
  }>(),
  {
    placeholder: '使用 Markdown 格式撰写评论',
    submitText: '评论',
    showClose: false,
    closeText: '关闭',
    busy: false,
  },
);
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'submit'): void;
  (e: 'close'): void;
}>();

const tab = ref<'write' | 'preview'>('write');
const previewHtml = computed(() => renderMarkdown(props.modelValue));
const canSubmit = computed(() => props.modelValue.trim().length > 0 && !props.busy);
</script>

<template>
  <div class="comment-editor">
    <div class="comment-editor__tabs">
      <div class="comment-editor__tabs-left">
        <button
          type="button"
          class="comment-editor__tab"
          :class="{ 'comment-editor__tab--active': tab === 'write' }"
          @click="tab = 'write'"
        >
          Write
        </button>
        <button
          type="button"
          class="comment-editor__tab"
          :class="{ 'comment-editor__tab--active': tab === 'preview' }"
          :disabled="!props.modelValue.trim()"
          @click="tab = 'preview'"
        >
          Preview
        </button>
      </div>
      <div class="comment-editor__toolbar">
        <slot name="toolbar" />
      </div>
    </div>

    <textarea
      v-show="tab === 'write'"
      class="comment-editor__textarea"
      :value="props.modelValue"
      :placeholder="props.placeholder"
      @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    ></textarea>
    <div v-show="tab === 'preview'" class="comment-editor__preview md-body" v-html="previewHtml"></div>

    <div class="comment-editor__footer">
      <span class="comment-editor__hint"><slot name="hint" /></span>
      <div class="comment-editor__actions">
        <button v-if="props.showClose" type="button" class="btn btn-default" @click="emit('close')">
          {{ props.closeText }}
        </button>
        <button type="button" class="btn btn-primary" :disabled="!canSubmit" @click="emit('submit')">
          {{ props.submitText }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.comment-editor {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-body);
  overflow: hidden;
}

.comment-editor__tabs {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
}

.comment-editor__tabs-left {
  display: flex;
  gap: var(--spacing-lg);
}

.comment-editor__tab {
  padding: 0;
  color: var(--text-muted);
  font-size: var(--font-size-base);
}
.comment-editor__tab:hover:not(:disabled) {
  color: var(--text-regular);
}
.comment-editor__tab--active {
  color: var(--text-main);
  font-weight: 600;
}
.comment-editor__tab:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.comment-editor__toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  color: var(--text-muted);
}

.comment-editor__textarea {
  display: block;
  width: 100%;
  min-height: 120px;
  padding: var(--spacing-lg);
  background: var(--bg-body);
  border: none;
  outline: none;
  resize: vertical;
  color: var(--text-main);
  font-family: inherit;
  font-size: var(--font-size-base);
  line-height: 1.6;
}

.comment-editor__preview {
  min-height: 120px;
  padding: var(--spacing-lg);
}

.comment-editor__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-lg);
  border-top: 1px solid var(--border-color);
}

.comment-editor__hint {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.comment-editor__actions {
  display: flex;
  gap: var(--spacing-sm);
}
</style>
