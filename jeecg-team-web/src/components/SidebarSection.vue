<script setup lang="ts">
// 详情页右侧栏区块（对齐 GitHub .sidebar-section）：
// 标题行（右侧 action 插槽放齿轮/编辑图标）+ 内容插槽。
defineProps<{
  title: string;
}>();
</script>

<template>
  <section class="sidebar-section">
    <div class="sidebar-section__title">
      {{ title }}
      <span class="sidebar-section__action"><slot name="action" /></span>
    </div>
    <div class="sidebar-section__content">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.sidebar-section {
  border-bottom: 1px solid var(--border-color-light);
  padding-bottom: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}
.sidebar-section:last-child {
  border-bottom: none;
}

.sidebar-section__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-sm);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-muted);
}

.sidebar-section__action {
  display: inline-flex;
  align-items: center;
  color: var(--text-muted);
  cursor: pointer;
}
.sidebar-section__action:hover {
  color: var(--text-regular);
}

.sidebar-section__content {
  font-size: var(--font-size-base);
  color: var(--text-regular);
}
</style>
