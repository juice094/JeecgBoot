<script setup lang="ts">
// 部门树节点（递归）—— owner: Ym3699
import type { DepartNode } from '@/api/sys';
import { PhBuildings } from '@phosphor-icons/vue';

defineProps<{
  node: DepartNode;
  depth: number;
  activeName: string;
}>();

const emit = defineEmits<{ (e: 'select', node: DepartNode): void }>();
</script>

<template>
  <div>
    <div
      class="dept-row"
      :class="{ 'dept-row--active': node.departName === activeName }"
      :style="{ paddingLeft: `${depth * 16}px` }"
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
      :active-name="activeName"
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
