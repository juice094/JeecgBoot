<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  PhSquaresFour,
  PhUsersThree,
  PhBuildings,
  PhBooks,
  PhPackage,
  PhMegaphone,
  PhBriefcase,
  PhStudent,
  PhNotePencil,
  PhCheckSquareOffset,
  PhEnvelopeSimple,
  PhFolderOpen,
  PhBug,
  PhChartBar,
  PhChatTeardropText,
  PhWrench,
  PhCaretLeft,
  PhCaretRight,
  type FunctionalComponent,
} from '@phosphor-icons/vue';
import { useWorkbenchStore } from '@/stores/workbench';

const route = useRoute();
const router = useRouter();
const workbench = useWorkbenchStore();

interface MenuChild {
  title: string;
  path: string;
  owner: string;
  icon: string;
  badge?: number;
}

interface MenuGroup {
  title: string;
  children: MenuChild[];
}

/** 路由 meta.icon → Phosphor 图标组件（唯一权威在 router/index.ts） */
const MENU_ICONS: Record<string, FunctionalComponent> = {
  dashboard: PhSquaresFour,
  hr: PhUsersThree,
  assets: PhBuildings,
  academic: PhBooks,
  repair: PhPackage,
  marketing: PhMegaphone,
  vocational: PhBriefcase,
  student: PhStudent,
  request: PhNotePencil,
  approval: PhCheckSquareOffset,
  email: PhEnvelopeSimple,
  resource: PhFolderOpen,
  bug: PhBug,
  report: PhChartBar,
};

// 侧栏从路由派生，禁止在这里硬编码菜单项（docs/13 §二.5，issue #11）
const menus = computed<MenuGroup[]>(() => {
  const children = router.options.routes.find((r) => r.path === '/')?.children ?? [];
  const groups = new Map<string, MenuChild[]>();
  for (const child of children) {
    const meta = child.meta as { title?: string; owner?: string; group?: string; icon?: string; badge?: number } | undefined;
    if (!meta?.group || !child.path) continue;
    if (!groups.has(meta.group)) groups.set(meta.group, []);
    groups.get(meta.group)!.push({
      title: meta.title ?? child.path,
      path: `/${child.path}`,
      owner: meta.owner ?? '',
      icon: meta.icon ?? '',
      badge: meta.badge,
    });
  }
  return [...groups.entries()].map(([title, items]) => ({ title, children: items }));
});

const isActive = (path: string) => route.path === path;

function switchMode(mode: 'tool' | 'chat') {
  workbench.setMode(mode);
  if (mode === 'chat') {
    router.push('/message');
  }
}
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': workbench.sidebarCollapsed }">
    <div class="sidebar__logo">
      <PhSquaresFour :size="24" class="sidebar__logo-icon" />
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
          <component :is="MENU_ICONS[item.icon] ?? PhSquaresFour" :size="18" class="menu-item__icon" />
          <span v-if="!workbench.sidebarCollapsed" class="menu-item__text">{{ item.title }}</span>
          <span v-if="item.badge && !workbench.sidebarCollapsed" class="menu-badge">{{ item.badge }}</span>
        </router-link>
      </div>
    </nav>
    <div v-show="workbench.mode === 'tool'" class="sidebar__spacer" />

    <!-- 会话模式：侧栏仅保留头部与底栏，主区域由会话工作台占满 -->
    <div v-show="workbench.mode === 'chat'" class="sidebar__chat-hint">
      <PhChatTeardropText :size="22" />
      <span v-if="!workbench.sidebarCollapsed">会话工作台</span>
    </div>
    <div v-show="workbench.mode === 'chat'" class="sidebar__spacer" />

    <!-- 底栏：模式切换（收起按钮边上）+ 收起/展开 -->
    <div class="sidebar__footer">
      <div class="mode-switch" role="tablist" aria-label="工作台模式切换">
        <button
          class="mode-switch__btn"
          :class="{ 'mode-switch__btn--active': workbench.mode === 'tool' }"
          title="工具工作台"
          @click="switchMode('tool')"
        >
          <PhWrench :size="16" />
          <span v-if="!workbench.sidebarCollapsed">工具</span>
        </button>
        <button
          class="mode-switch__btn"
          :class="{ 'mode-switch__btn--active': workbench.mode === 'chat' }"
          title="会话工作台"
          @click="switchMode('chat')"
        >
          <PhChatTeardropText :size="16" />
          <span v-if="!workbench.sidebarCollapsed">会话</span>
        </button>
      </div>
      <button class="sidebar__collapse" :title="workbench.sidebarCollapsed ? '展开侧栏' : '收起侧栏'" @click="workbench.toggleSidebar()">
        <PhCaretLeft v-if="!workbench.sidebarCollapsed" :size="16" />
        <PhCaretRight v-else :size="16" />
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
  transition: width 0.2s ease;
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
  font-size: 18px;
  font-weight: 600;
  color: var(--primary);
  border-bottom: 1px solid var(--border-color);
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
}

.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: 15px 0;
}

.menu-group {
  margin-bottom: 15px;
}

.menu-group__title {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  padding: 0 20px 8px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  font-size: var(--font-size-base);
  color: var(--text-regular);
  transition:
    color 0.2s,
    background 0.2s;
  position: relative;
}

.menu-item__icon {
  color: var(--text-muted);
  flex-shrink: 0;
  transition: color 0.2s;
}

.menu-item:hover {
  color: var(--primary);
  background: var(--border-color-light);
}

.menu-item:hover .menu-item__icon {
  color: var(--primary);
}

.menu-item--active {
  color: var(--primary);
  background: var(--primary-light);
  font-weight: 500;
}

.menu-item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--primary);
}

.menu-item--active .menu-item__icon {
  color: var(--primary);
}

.menu-item--slim {
  justify-content: center;
  padding: 10px 0;
}

.menu-item--slim::before {
  left: 0;
}

/* 菜单角标（原型 .badge：审批中心 34 / 工作邮箱 3，接口就绪后接真实计数） */
.menu-badge {
  margin-left: auto;
  background: var(--danger);
  color: #fff;
  font-size: var(--font-size-sm);
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
  border-radius: 9px;
  font-weight: normal;
}

.sidebar__spacer {
  flex: 1;
}

.sidebar__chat-hint {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  color: var(--primary);
  font-size: var(--font-size-base);
  font-weight: 500;
}

.sidebar--collapsed .sidebar__chat-hint {
  justify-content: center;
  padding: 18px 0;
}

.sidebar__footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--border-color);
}

.sidebar--collapsed .sidebar__footer {
  flex-direction: column;
  align-items: stretch;
}

.mode-switch {
  flex: 1;
  display: flex;
  background: var(--bg-body);
  border-radius: var(--radius-sm);
  padding: 3px;
  gap: 3px;
}

.mode-switch__btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: none;
  background: transparent;
  color: var(--text-regular);
  font-size: var(--font-size-md);
  padding: 6px 8px;
  border-radius: 3px;
  cursor: pointer;
  transition:
    background 0.2s,
    color 0.2s;
  white-space: nowrap;
}

.mode-switch__btn:hover {
  color: var(--primary);
}

.mode-switch__btn--active {
  background: var(--bg-card);
  color: var(--primary);
  font-weight: 500;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.sidebar__collapse {
  border: none;
  background: transparent;
  color: var(--text-muted);
  padding: 6px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition:
    background 0.2s,
    color 0.2s;
}

.sidebar__collapse:hover {
  background: var(--border-color-light);
  color: var(--primary);
}
</style>
