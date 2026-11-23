<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, type Component} from 'vue';
import {useRoute, useRouter} from 'vue-router';

import {
  PhSquaresFour,
  PhUsersThree,
  PhNotePencil,
  PhCheckSquareOffset,
  PhEnvelopeSimple,
  PhFolderOpen,
  PhBug,
  PhChartBar,
  PhChatTeardropText,
  PhFlowArrow,
  PhWrench,
  PhCaretLeft,
  PhCaretRight,
} from '@phosphor-icons/vue';
import {useWorkbenchStore} from '@/stores/workbench';
import {useBadgeStore} from '@/stores/badges';
import {hasPermission} from '@/utils/permission';

const route = useRoute();
const router = useRouter();
const workbench = useWorkbenchStore();

interface MenuChild {
  title: string;
  path: string;
  owner: string;
  icon: string;
}

interface MenuGroup {
  title: string;
  children: MenuChild[];
}

/** 路由 meta.icon → Phosphor 图标组件（唯一权威在 router/index.ts） */
const MENU_ICONS: Record<string, Component> = {
  dashboard: PhSquaresFour,
  issues: PhCheckSquareOffset,
  hr: PhUsersThree,
  request: PhNotePencil,
  approval: PhCheckSquareOffset,
  flow: PhFlowArrow,
  email: PhEnvelopeSimple,
  resource: PhFolderOpen,
  bug: PhBug,
  report: PhChartBar,
};

// 侧栏从路由派生，禁止在这里硬编码菜单项（docs/13 §二.5，issue #11）
// meta.perm 过滤：无权限码的菜单项不渲染；权限码未加载时 hasPermission 兜底放行（渐进增强）
const menus = computed<MenuGroup[]>(() => {
  const children = router.options.routes.find((r) => r.path === '/')?.children ?? [];
  const groups = new Map<string, MenuChild[]>();
  for (const child of children) {
    const meta = child.meta as {
      title?: string;
      owner?: string;
      group?: string;
      icon?: string;
      perm?: string;
    } | undefined;
    if (!meta?.group || !child.path) continue;
    if (meta.perm && !hasPermission(meta.perm)) continue;
    if (!groups.has(meta.group)) groups.set(meta.group, []);
    groups.get(meta.group)!.push({
      title: meta.title ?? child.path,
      path: `/${child.path}`,
      owner: meta.owner ?? '',
      icon: meta.icon ?? '',
    });
  }
  return [...groups.entries()]
    .map(([title, items]) => ({title, children: items}))
    .filter((g) => g.children.length > 0);
});

// 角标真实计数来自 badgeStore（原 meta.badge 硬编码占位已移除）；按路由 path 关联
const badges = useBadgeStore();
const badgeFor = (path: string): number =>
  path === '/tools/approval' ? badges.approvalTodo : path === '/tools/email' ? badges.mailUnread : 0;

onMounted(() => badges.startPolling());
onBeforeUnmount(() => badges.stopPolling());

const isActive = (path: string) => route.path === path;

function switchMode(mode: 'tool' | 'chat') {
  if (mode === 'chat') {
    router.push('/message');
  } else if (route.path === '/message') {
    // 从会话切回工具：回到上次打开的页面
    router.push(workbench.lastToolPath || '/dashboard');
  }
}
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': workbench.sidebarCollapsed }">
    <div class="sidebar__logo">
      <PhSquaresFour :size="22" class="sidebar__logo-icon"/>
      <span v-if="!workbench.sidebarCollapsed" class="sidebar__logo-text">团队协作工作台</span>
    </div>

    <nav v-show="workbench.mode === 'tool'" class="sidebar__nav">
      <div v-for="group in menus" :key="group.title" class="menu-group">
        <div v-if="!workbench.sidebarCollapsed" class="menu-group__title">{{ group.title }}</div>
        <router-link
            v-for="item in group.children"
            :key="item.path"
            :to="item.path"
            class="menu-item"
            :class="{ 'menu-item--active': isActive(item.path), 'menu-item--slim': workbench.sidebarCollapsed }"
            :title="workbench.sidebarCollapsed ? item.title : ''"
        >
          <component :is="MENU_ICONS[item.icon] ?? PhSquaresFour" :size="18" class="menu-item__icon"/>
          <span v-if="!workbench.sidebarCollapsed" class="menu-item__text">{{ item.title }}</span>
          <span v-if="badgeFor(item.path) && !workbench.sidebarCollapsed" class="menu-badge">{{ badgeFor(item.path) }}</span>
        </router-link>
      </div>
    </nav>

    <!-- 会话模式：侧栏仅保留头部与底栏，主区域由会话工作台占满 -->
    <div v-show="workbench.mode === 'chat'" class="sidebar__chat-hint">
      <PhChatTeardropText :size="22"/>
      <span v-if="!workbench.sidebarCollapsed">会话工作台</span>
    </div>

    <!-- 底栏：模式切换（收起按钮边上）+ 收起/展开 -->
    <div class="sidebar__footer">
      <div class="mode-switch" role="tablist" aria-label="工作台模式切换">
        <button
            class="mode-switch__btn"
            :class="{ 'mode-switch__btn--active': workbench.mode === 'tool' }"
            title="工具工作台"
            @click="switchMode('tool')"
        >
          <PhWrench :size="14"/>
          <span v-if="!workbench.sidebarCollapsed">工具</span>
        </button>
        <button
            class="mode-switch__btn"
            :class="{ 'mode-switch__btn--active': workbench.mode === 'chat' }"
            title="会话工作台"
            @click="switchMode('chat')"
        >
          <PhChatTeardropText :size="14"/>
          <span v-if="!workbench.sidebarCollapsed">会话</span>
        </button>
      </div>
      <button class="sidebar__collapse" :title="workbench.sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
              @click="workbench.toggleSidebar()">
        <PhCaretLeft v-if="!workbench.sidebarCollapsed" :size="16"/>
        <PhCaretRight v-else :size="16"/>
      </button>
    </div>
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
  z-index: 10;
  transition: width var(--duration-normal) var(--ease-out);
}

.sidebar--collapsed {
  width: 64px;
}

.sidebar__logo {
  height: var(--header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  font-size: 17px;
  font-weight: 600;
  color: var(--primary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.sidebar--collapsed .sidebar__logo {
  justify-content: center;
  padding: 0;
}

.sidebar__logo-icon {
  flex-shrink: 0;
}

.sidebar__logo-text {
  white-space: nowrap;
  overflow: hidden;
  letter-spacing: -0.01em;
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px 8px;
}

.menu-group {
  margin-bottom: 12px;
}

.menu-group__title {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  padding: 8px 12px 4px;
  font-weight: 500;
  letter-spacing: 0.02em;
  text-transform: uppercase;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 12px;
  font-size: var(--font-size-md);
  color: var(--text-regular);
  transition:
    color var(--duration-fast) var(--ease-out),
    background-color var(--duration-fast) var(--ease-out);
  position: relative;
  border-radius: var(--radius-sm);
  margin-bottom: 1px;
}

.menu-item__icon {
  color: var(--text-muted);
  flex-shrink: 0;
  transition: color var(--duration-fast) var(--ease-out);
}

.menu-item:hover {
  color: var(--text-main);
  background: var(--gray-100);
}
.menu-item:hover .menu-item__icon {
  color: var(--text-regular);
}

.menu-item--active {
  color: var(--primary);
  background: var(--primary-50);
  font-weight: 500;
}
.menu-item--active .menu-item__icon {
  color: var(--primary);
}
.menu-item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  background: var(--primary);
  border-radius: 0 2px 2px 0;
}

.menu-item--slim {
  justify-content: center;
  padding: 8px 0;
  border-radius: var(--radius-sm);
}
.menu-item--slim::before {
  display: none;
}

.menu-badge {
  margin-left: auto;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  padding: 0 6px;
  height: 16px;
  line-height: 16px;
  border-radius: var(--radius-full);
  font-weight: 500;
  min-width: 16px;
  text-align: center;
}

.sidebar__chat-hint {
  flex: 1;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 18px 20px;
  color: var(--primary);
  font-size: var(--font-size-md);
  font-weight: 500;
}

.sidebar--collapsed .sidebar__chat-hint {
  justify-content: center;
  padding: 18px 0;
}

.sidebar__footer {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 8px;
  border-top: 1px solid var(--border-color);
}

.sidebar--collapsed .sidebar__footer {
  flex-direction: column;
  align-items: stretch;
}

.mode-switch {
  flex: 1;
  display: flex;
  background: var(--gray-100);
  border-radius: var(--radius-sm);
  padding: 2px;
  gap: 2px;
}

.mode-switch__btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  padding: 5px 6px;
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
  white-space: nowrap;
  font-weight: 500;
}

.mode-switch__btn:hover {
  color: var(--text-main);
}

.mode-switch__btn--active {
  background: var(--bg-card);
  color: var(--primary);
  box-shadow: var(--shadow-xs);
}

.sidebar__collapse {
  border: none;
  background: transparent;
  color: var(--text-muted);
  padding: 5px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    background-color var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out);
}
.sidebar__collapse:hover {
  background: var(--gray-100);
  color: var(--text-main);
}
</style>
