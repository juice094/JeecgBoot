<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();

interface MenuChild {
  title: string;
  path: string;
  owner: string;
}

interface MenuGroup {
  title: string;
  children: MenuChild[];
}

/** 分组图标（纯展示层映射；分组名本身来自路由 meta.group，唯一权威在 router/index.ts） */
const GROUP_ICONS: Record<string, string> = {
  用户与消息: '💬',
  表单与审批: '📋',
  组织租户管理: '🏢',
};

// 侧栏从路由派生，禁止在这里硬编码菜单项（docs/13 §二.5，issue #11）
const menus = computed<MenuGroup[]>(() => {
  const children = router.options.routes.find((r) => r.path === '/')?.children ?? [];
  const groups = new Map<string, MenuChild[]>();
  for (const child of children) {
    const meta = child.meta as { title?: string; owner?: string; group?: string } | undefined;
    if (!meta?.group || !child.path) continue;
    if (!groups.has(meta.group)) groups.set(meta.group, []);
    groups.get(meta.group)!.push({ title: meta.title ?? child.path, path: `/${child.path}`, owner: meta.owner ?? '' });
  }
  return [...groups.entries()].map(([title, items]) => ({ title, children: items }));
});

const isActive = (path: string) => route.path === path;
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__logo">团队协作工作台</div>
    <nav class="sidebar__nav">
      <div v-for="group in menus" :key="group.title" class="menu-group">
        <div class="menu-group__title">{{ GROUP_ICONS[group.title] ?? '📁' }} {{ group.title }}</div>
        <router-link
          v-for="item in group.children"
          :key="item.path"
          :to="item.path"
          class="menu-item"
          :class="{ 'menu-item--active': isActive(item.path) }"
        >
          {{ item.title }}
        </router-link>
      </div>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  background: var(--bg-card);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar__logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--primary);
  border-bottom: 1px solid var(--border-color-light);
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: var(--spacing-md);
}

.menu-group__title {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  padding: var(--spacing-md) var(--spacing-sm) var(--spacing-xs);
}

.menu-item {
  display: block;
  padding: 9px var(--spacing-md);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-base);
  color: var(--text-regular);
  margin-bottom: 2px;
}

.menu-item:hover {
  background: var(--border-color-light);
  color: var(--primary);
}

.menu-item--active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
}
</style>
