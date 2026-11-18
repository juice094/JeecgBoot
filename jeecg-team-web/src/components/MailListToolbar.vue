<script setup lang="ts">
// 邮箱命令栏（对齐 Outlook command bar，40px 高）：
// 左侧主按钮 + 无边框文字按钮组（分隔线用 divider 插槽），右侧 right 插槽。
// 用法：<MailListToolbar><template #primary>新邮件</template><template #actions>…</template></MailListToolbar>
</script>

<template>
  <div class="cmd-bar">
    <div class="cmd-bar__left">
      <slot name="primary" />
      <slot name="actions" />
    </div>
    <div class="cmd-bar__right">
      <slot name="right" />
    </div>
  </div>
</template>

<style scoped>
.cmd-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-height: 40px;
  padding: 4px var(--spacing-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color-light);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  border-bottom: 1px solid var(--border-color);
}

.cmd-bar__left {
  display: flex;
  align-items: center;
  gap: 2px;
  min-width: 0;
}

.cmd-bar__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

/* 工具栏内无边框按钮（Outlook 口径）：消费方套 .cmd-btn 或 .btn--sm */
:slotted(.cmd-btn) {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
  color: var(--text-regular);
  white-space: nowrap;
}
:slotted(.cmd-btn:hover:not(:disabled)) {
  background: var(--bg-subtle);
  color: var(--text-main);
}
:slotted(.cmd-btn:disabled) {
  color: var(--text-placeholder);
  cursor: not-allowed;
}

/* 按钮组分隔竖线：消费方放 <span class="cmd-divider" /> */
:slotted(.cmd-divider) {
  width: 1px;
  height: 20px;
  margin: 0 var(--spacing-sm);
  background: var(--border-color);
}
</style>
