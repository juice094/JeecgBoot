<script setup lang="ts">
// 权限树节点（递归）—— 角色授权弹窗专用，蒸馏自旧 RolePermissionDrawer 的 BasicTree 交互。
// 勾选为「层级关联」：勾选/取消节点时级联全部子孙（与旧版默认 checkStrictly=false 一致）。
import type { PermissionNode } from '@/api/sys';

defineProps<{
  node: PermissionNode;
  checkedKeys: Set<string>;
  expandedKeys: Set<string>;
  /** 节点显示文本（父组件统一 slotTitle/title 兜底逻辑） */
  title: string;
}>();

const emit = defineEmits<{
  (e: 'toggle', node: PermissionNode): void;
  (e: 'toggle-expand', key: string): void;
}>();

function nodeKey(n: PermissionNode): string {
  return n.key ?? n.id ?? '';
}

function childTitle(n: PermissionNode): string {
  return n.slotTitle ?? n.title ?? n.label ?? nodeKey(n);
}
</script>

<template>
  <li>
    <div class="ptree__row">
      <button
        v-if="node.children?.length"
        class="ptree__caret"
        @click="emit('toggle-expand', nodeKey(node))"
      >{{ expandedKeys.has(nodeKey(node)) ? '▾' : '▸' }}</button>
      <span v-else class="ptree__caret ptree__caret--leaf"></span>
      <label class="ptree__label">
        <input
          type="checkbox"
          :checked="checkedKeys.has(nodeKey(node))"
          @change="emit('toggle', node)"
        />
        <span>{{ title }}</span>
      </label>
    </div>
    <ul v-if="node.children?.length && expandedKeys.has(nodeKey(node))" class="ptree ptree--child">
      <PermissionTreeNode
        v-for="c in node.children"
        :key="nodeKey(c)"
        :node="c"
        :checked-keys="checkedKeys"
        :expanded-keys="expandedKeys"
        :title="childTitle(c)"
        @toggle="emit('toggle', $event)"
        @toggle-expand="emit('toggle-expand', $event)"
      />
    </ul>
  </li>
</template>

<style scoped>
.ptree {
  list-style: none;
  margin: 0;
  padding: 0;
}
.ptree--child {
  padding-left: 22px;
}
.ptree__row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 0;
}
.ptree__caret {
  width: 16px;
  height: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: var(--text-muted);
}
.ptree__caret--leaf {
  visibility: hidden;
}
.ptree__label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-regular);
  cursor: pointer;
}
</style>
