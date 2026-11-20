<script setup lang="ts">
// 写信弹窗（对齐 QQ 邮箱写信页骨架，试点裁剪版）：
// 发送（主按钮）｜预览切换 + 收件人/主题（下划线字段行）+ 正文 textarea。
// 不搬：富文本工具栏、联系人侧栏、超大附件（见 .refs/extracted/qq-mail-spec.md §三）。
import { ref, watch } from 'vue';
import { PhX } from '@phosphor-icons/vue';

const props = withDefaults(
  defineProps<{
    open: boolean;
    title?: string;
    initial?: { to?: string; subject?: string };
    senderHint?: string;
    busy?: boolean;
  }>(),
  { title: '写信', senderHint: '', busy: false },
);
const emit = defineEmits<{
  (e: 'submit', payload: { to: string; subject: string; html: string }): void;
  (e: 'close'): void;
}>();

const to = ref('');
const subject = ref('');
const body = ref('');
const preview = ref(false);

watch(
  () => props.open,
  (open) => {
    if (open) {
      to.value = props.initial?.to || '';
      subject.value = props.initial?.subject || '';
      body.value = '';
      preview.value = false;
    }
  },
);

function submit() {
  if (!to.value.trim() || !subject.value.trim() || props.busy) return;
  emit('submit', { to: to.value.trim(), subject: subject.value.trim(), html: body.value });
}
</script>

<template>
  <div v-if="props.open" class="mail-compose-overlay" @click.self="emit('close')">
    <div class="mail-compose" role="dialog" aria-modal="true">
      <header class="mail-compose__head">
        <span class="mail-compose__title">{{ props.title }}</span>
        <div class="mail-compose__head-actions">
          <button type="button" class="btn btn-primary btn--sm" :disabled="props.busy" @click="submit">
            发送
          </button>
          <button
            type="button"
            class="mail-compose__preview-tab"
            :class="{ 'mail-compose__preview-tab--active': preview }"
            @click="preview = !preview"
          >
            {{ preview ? '返回编辑' : '预览' }}
          </button>
          <button type="button" class="icon-btn" title="关闭" @click="emit('close')">
            <PhX :size="16" />
          </button>
        </div>
      </header>

      <div class="mail-compose__field">
        <span class="mail-compose__label">收件人</span>
        <input v-model="to" type="text" class="mail-compose__input" placeholder="name@company.com" />
      </div>
      <div class="mail-compose__field">
        <span class="mail-compose__label">主题</span>
        <input v-model="subject" type="text" class="mail-compose__input" />
      </div>

      <textarea
        v-show="!preview"
        v-model="body"
        class="mail-compose__body"
        placeholder="输入正文"
      ></textarea>
      <div v-show="preview" class="mail-compose__preview">{{ body || '（空）' }}</div>

      <footer class="mail-compose__foot">
        <span v-if="props.senderHint" class="mail-compose__sender">发件人：{{ props.senderHint }}</span>
        <span v-else></span>
        <div class="mail-compose__foot-actions">
          <button type="button" class="btn btn-default btn--sm" @click="emit('close')">取消</button>
          <button type="button" class="btn btn-primary btn--sm" :disabled="props.busy" @click="submit">
            {{ props.busy ? '发送中…' : '发送' }}
          </button>
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.mail-compose-overlay {
  position: fixed;
  inset: 0;
  background: var(--overlay-backdrop);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  padding: var(--spacing-2xl);
}

.mail-compose {
  display: flex;
  flex-direction: column;
  width: 720px;
  max-width: 100%;
  max-height: 90vh;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}

.mail-compose__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}
.mail-compose__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-main);
}
.mail-compose__head-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.mail-compose__preview-tab {
  font-size: var(--font-size-md);
  color: var(--text-muted);
  padding: 4px var(--spacing-sm);
  border-radius: var(--radius-sm);
}
.mail-compose__preview-tab:hover {
  color: var(--text-main);
  background: var(--bg-subtle);
}
.mail-compose__preview-tab--active {
  color: var(--primary);
}

.mail-compose__field {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: 10px var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
}
.mail-compose__label {
  width: 48px;
  flex-shrink: 0;
  font-size: var(--font-size-base);
  color: var(--text-regular);
}
.mail-compose__input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: inherit;
  font-size: var(--font-size-base);
  color: var(--text-main);
}

.mail-compose__body {
  flex: 1;
  min-height: 240px;
  padding: var(--spacing-lg);
  border: none;
  outline: none;
  resize: vertical;
  background: var(--bg-card);
  font-family: inherit;
  font-size: var(--font-size-base);
  line-height: 1.6;
  color: var(--text-main);
}

.mail-compose__preview {
  flex: 1;
  min-height: 240px;
  padding: var(--spacing-lg);
  white-space: pre-wrap;
  color: var(--text-regular);
}

.mail-compose__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
}
.mail-compose__sender {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.mail-compose__foot-actions {
  display: flex;
  gap: var(--spacing-sm);
}
</style>
