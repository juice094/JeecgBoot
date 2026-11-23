import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { hasPermission } from '@/utils/permission';

/**
 * 路由 = 菜单唯一权威（docs/13 §二.5，issue #11）。
 * 菜单树按原型复现（2026-11-17 宿裁定）：概览/管理/业务/工具 四组；
 * 在线沟通不在侧栏菜单——入口在侧栏底栏"会话"切换按钮（双模式设计）。
 *
 * meta.group = 侧栏分组；meta.icon = 图标 key（SidebarMenu 映射 Phosphor 组件）。
 * meta.perm = 路由所需后端权限码（可选）：侧栏按它过滤，路由守卫校验，null/缺省 = 全员可见。
 * 只挂后端确实 @RequiresPermissions  enforcement 且 admin 已持有的码，避免误伤可见性。
 * 菜单角标不在此硬编码：由 stores/badges 按路由 path 供真实计数（60s 轮询）。
 */
export const layoutChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/Dashboard.vue'),
    meta: { title: '仪表盘', owner: 'juice094', group: '概览', icon: 'dashboard', perm: 'team:dashboard:summary' },
  },
  {
    path: 'issues',
    name: 'IssueList',
    component: () => import('@/views/issue/IssueList.vue'),
    meta: { title: '工作项', owner: 'juice094', group: '概览', icon: 'issues', perm: 'team:task:list' },
  },
  {
    path: 'admin/hr',
    name: 'HrManage',
    component: () => import('@/modules/admin/views/HrManage.vue'),
    meta: { title: '人事管理', owner: 'Ym3699', group: '管理', icon: 'hr', perm: 'system:user:list' },
  },
  {
    path: 'admin/role',
    name: 'RoleManage',
    component: () => import('@/modules/admin/views/RoleManage.vue'),
    meta: { title: '角色管理', owner: 'Ym3699', group: '管理', icon: 'role', perm: 'system:role:list' },
  },
  {
    path: 'admin/monitor/logs',
    name: 'MonitorLogs',
    component: () => import('@/modules/business/views/monitor/MonitorLogs.vue'),
    meta: { title: '日志管理', owner: 'showcase-dev', group: '管理', icon: 'logs' },
  },
  {
    path: 'admin/monitor/quartz',
    name: 'MonitorQuartz',
    component: () => import('@/modules/business/views/monitor/MonitorQuartz.vue'),
    meta: { title: '定时任务', group: '管理', icon: 'quartz' },
  },
  {
    path: 'admin/monitor/online',
    name: 'MonitorOnline',
    component: () => import('@/modules/business/views/monitor/MonitorOnline.vue'),
    meta: { title: '在线用户', group: '管理', icon: 'online', perm: 'system:online:list' },
  },
  {
    path: 'admin/monitor/redis',
    name: 'MonitorRedis',
    component: () => import('@/modules/business/views/monitor/MonitorRedis.vue'),
    meta: { title: 'Redis 监控', group: '管理', icon: 'redis' },
  },
  {
    path: 'admin/monitor/datalog',
    name: 'MonitorDataLog',
    component: () => import('@/modules/business/views/monitor/MonitorDataLog.vue'),
    meta: { title: '数据日志', group: '管理', icon: 'logs' },
  },
  {
    path: 'tools/request',
    name: 'RequestForm',
    component: () => import('@/modules/business/views/RequestForm.vue'),
    meta: { title: '日常申请', owner: 'juice094', group: '工具', icon: 'request', perm: 'team:approval:submit' },
  },
  {
    path: 'tools/approval',
    name: 'ApprovalCenter',
    component: () => import('@/modules/business/views/ApprovalCenter.vue'),
    meta: { title: '审批中心', owner: 'juice094', group: '工具', icon: 'approval', perm: 'team:approval:list' },
  },
  {
    path: 'tools/email',
    name: 'WorkEmail',
    component: () => import('@/modules/business/views/WorkEmail.vue'),
    meta: { title: '工作邮箱', owner: 'juice094', group: '工具', icon: 'email', fullHeight: true },
  },
  {
    path: 'tools/resource',
    name: 'ResourceCenter',
    component: () => import('@/modules/business/views/ResourceCenter.vue'),
    meta: { title: '资料中心', owner: 'juice094', group: '工具', icon: 'resource' },
  },
  {
    path: 'tools/bug',
    name: 'BugReport',
    component: () => import('@/modules/business/views/BugReport.vue'),
    meta: { title: 'Bug 反馈', owner: 'juice094', group: '工具', icon: 'bug' },
  },
  {
    path: 'tools/taskboard',
    name: 'TaskBoard',
    component: () => import('@/modules/business/views/TaskBoard.vue'),
    meta: { title: '任务看板', owner: 'juice094', group: '工具', icon: 'taskboard' },
  },
  {
    // 详情页不进侧栏菜单（不设 group），从列表行点击进入
    path: 'tools/taskboard/:id',
    name: 'TaskDetail',
    component: () => import('@/views/issue/TaskDetail.vue'),
    meta: { title: '任务详情', owner: 'juice094' },
  },
  {
    path: 'tools/report',
    name: 'PersonalReport',
    component: () => import('@/modules/business/views/PersonalReport.vue'),
    meta: { title: '个人报表', owner: 'juice094', group: '工具', icon: 'report' },
  },
  {
    // 在线沟通：不在侧栏菜单，入口在侧栏底栏"会话"模式切换（双模式设计）
    path: 'message',
    name: 'ChatWorkbench',
    component: () => import('@/modules/message/views/ChatWorkbench.vue'),
    meta: { title: '会话工作台', owner: 'qingyanjunzi' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'Login', component: () => import('@/views/Login.vue') },
    { path: '/forgot-password', name: 'ForgotPassword', component: () => import('@/views/ForgotPassword.vue') },
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      children: [{ path: '', redirect: '/dashboard' }, ...layoutChildren],
    },
  ],
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  // 匿名页面白名单：登录与忘记密码
  if (!auth.isLogin && to.name !== 'Login' && to.name !== 'ForgotPassword') {
    return { name: 'Login', query: { redirect: to.fullPath } };
  }
  // 路由权限：meta.perm 要求的后端权限码不满足时回仪表盘（权限码未加载时放行，渐进增强）
  if (auth.isLogin && to.meta.perm && !hasPermission(to.meta.perm as string)) {
    // 仪表盘是兜底落地页：对其本身放行，避免重定向自环（vue-router 检测到重复导航会中止，页面全白）
    if (to.path === '/dashboard') return true;
    return { path: '/dashboard' };
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
