import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

/**
 * 路由按三人分工切分，模块目录即职责边界：
 * - /message/**  用户与消息（聊天工作台）        owner: qingyanjunzi  → src/modules/message/
 * - /business/** 表单与审批（业务与工具模块）    owner: juice094      → src/modules/business/
 * - /admin/**    组织租户管理（管理端模块）      owner: Ym3699        → src/modules/admin/
 *
 * 规则：只在自己模块目录内新增/修改文件；跨模块需求开 issue 协商，勿直接改他人目录。
 *
 * ⚠️ 本文件是菜单的唯一权威来源（docs/13 §二.5，issue #11）：
 *    meta.group = 侧栏分组名，meta.title/owner = 菜单项展示。侧栏从路由派生，禁止第二份硬编码。
 */
export const layoutChildren: RouteRecordRaw[] = [
  {
    path: 'message',
    name: 'ChatWorkbench',
    component: () => import('@/modules/message/views/ChatWorkbench.vue'),
    meta: { title: '聊天工作台', owner: 'qingyanjunzi', group: '用户与消息', icon: 'chat' },
  },
  {
    path: 'business/tasks',
    name: 'TaskBoard',
    component: () => import('@/modules/business/views/TaskBoard.vue'),
    meta: { title: '任务看板', owner: 'juice094', group: '表单与审批', icon: 'kanban' },
  },
  {
    path: 'business/request',
    name: 'RequestForm',
    component: () => import('@/modules/business/views/RequestForm.vue'),
    meta: { title: '表单与审批', owner: 'juice094', group: '表单与审批', icon: 'form' },
  },
  {
    path: 'admin/org',
    name: 'OrgTenant',
    component: () => import('@/modules/admin/views/OrgTenant.vue'),
    meta: { title: '组织与租户', owner: 'Ym3699', group: '组织租户管理', icon: 'org' },
  },
  {
    path: 'admin/members',
    name: 'TeamMembers',
    component: () => import('@/modules/admin/views/TeamMembers.vue'),
    meta: { title: '团队成员', owner: 'Ym3699', group: '组织租户管理', icon: 'members' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      children: [{ path: '', redirect: '/message' }, ...layoutChildren],
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (!auth.isLogin && to.name !== 'Login') {
    return { name: 'Login', query: { redirect: to.fullPath } };
  }
  if (auth.isLogin && to.name === 'Login') {
    return { path: '/' };
  }
  return true;
});

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 团队协作效率工作台` : '团队协作效率工作台';
});

export default router;
