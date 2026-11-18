<script setup lang="ts">
// 标签胶囊：列表行/详情侧栏的标签（对齐 GitHub IssueLabel）
// 配色用全局 .tag--* 类（constants/task.ts 的 TAG_CLASS / PRIORITY_CLASS 已产出这些类名）。
defineProps<{
  text: string;
  /** 全局 tag--* 类名，缺省中性色 */
  cls?: string;
}>();
</script>

<template>
  <span class="tag" :class="cls">{{ text }}</span>
</template>
