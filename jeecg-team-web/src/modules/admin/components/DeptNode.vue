<script setup lang="ts">
// 部门树节点（递归）—— owner: Ym3699
// 2026-11-17 showcase-dev 修复：选中态按 node.id 匹配（原按 departName 匹配，
// 不同层级的同名组织会同时高亮）；pathText 提供完整路径悬浮提示，用于区分同名部门。
import type { DepartNode } from '@/api/sys';
import { PhBuildings, PhPlus, PhPencil, PhTrash } from '@phosphor-icons/vue';

withDefaults(
  defineProps<{
    node: DepartNode;
    depth: number;
    activeId: string;
    /** 完整组织路径（如「校本部 / 财务部」），用于同名部门的悬浮区分；可空 */
    pathText?: string;
    /** 是否显示行内操作（新增子部门/编辑/删除）；管理域编辑态开启 */
    showActions?: boolean;
  }>(),
  { showActions: false }
);

const emit = defineEmits<{
  (e: 'select', node: DepartNode): void;
  (e: 'add-child', node: DepartNode): void;
  (e: 'edit', node: DepartNode): void;
  (e: 'remove', node: DepartNode): void;
}>();
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
      <span class="dept-row__name">{{ node.departName ?? node.id }}</span>
      <span v-if="showActions" class="dept-row__ops">
        <button class="dept-row__op" title="新增子部门" @click.stop="emit('add-child', node)"><PhPlus :size="12" /></button>
        <button class="dept-row__op" title="编辑部门" @click.stop="emit('edit', node)"><PhPencil :size="12" /></button>
        <button class="dept-row__op dept-row__op--danger" title="删除部门" @click.stop="emit('remove', node)"><PhTrash :size="12" /></button>
      </span>
    </div>
    <DeptNode
      v-for="child in node.children ?? []"
      :key="child.id"
      :node="child"
      :depth="depth + 1"
      :active-id="activeId"
      :path-text="pathText && child.departName ? `${pathText} / ${child.departName}` : (child.departName ?? '')"
      :show-actions="showActions"
      @select="emit('select', $event)"
      @add-child="emit('add-child', $event)"
      @edit="emit('edit', $event)"
      @remove="emit('remove', $event)"
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
.dept-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dept-row__ops {
  display: inline-flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}
.dept-row:hover .dept-row__ops {
  opacity: 1;
}
.dept-row__op {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: var(--radius-xs, 3px);
  color: var(--text-muted);
}
.dept-row__op:hover {
  background: var(--gray-100);
  color: var(--primary);
}
.dept-row__op--danger:hover {
  color: var(--danger);
}
</style>
