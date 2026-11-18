<script setup lang="ts">
// 部门树节点（递归）—— owner: Ym3699
// 2026-11-17 showcase-dev 修复：选中态按 node.id 匹配（原按 departName 匹配，
// 不同层级的同名组织会同时高亮）；pathText 提供完整路径悬浮提示，用于区分同名部门。
import type { DepartNode } from '@/api/sys';
import { PhBuildings } from '@phosphor-icons/vue';

defineProps<{
  node: DepartNode;
  depth: number;
  activeId: string;
  /** 完整组织路径（如「校本部 / 财务部」），用于同名部门的悬浮区分；可空 */
  pathText?: string;
}>();

const emit = defineEmits<{ (e: 'select', node: DepartNode): void }>();
</script>

<template>
  <div>
    <div
      class="dept-row"
      :class="{ 'dept-row--active': node.id === activeId }"
      :style="{ paddingLeft: `${depth * 16}px` }"
      :title="pathText || node.departName || node.id"
      @click="emit('select', node)"
    >
      <PhBuildings :size="14" />
      <span>{{ node.departName ?? node.id }}</span>
    </div>
    <DeptNode
      v-for="child in node.children ?? []"
      :key="child.id"
      :node="child"
      :depth="depth + 1"
      :active-id="activeId"
      :path-text="pathText && child.departName ? `${pathText} / ${child.departName}` : (child.departName ?? '')"
      @select="emit('select', $event)"
    />
  </div>
</template>

<style scoped>
.dept-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0;
  font-size: 13px;
  cursor: pointer;
  color: var(--text-regular);
}
.dept-row:hover {
  color: var(--primary);
}
.dept-row--active {
  color: var(--primary);
  font-weight: 500;
}
</style>
