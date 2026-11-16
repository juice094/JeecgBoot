import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

/**
 * 路由 = 菜单唯一权威（docs/13 §二.5，issue #11）。
 * 菜单树按原型复现（2026-11-17 宿裁定）：概览/管理/业务/工具 四组；
 * 在线沟通不在侧栏菜单——入口在侧栏底栏"会话"切换按钮（双模式设计）。
 *
 * meta.group = 侧栏分组；meta.icon = 图标 key（SidebarMenu 映射 Phosphor 组件）；
 * meta.badge = 菜单角标（数字，接口就绪后由真实计数替换）。
 */
export const layoutChildren: RouteRecordRaw[] = [
  {
    path: 'dashboard',
    name: 'Dashboard',
    component: () => import('@/views/dashboard/Dashboard.vue'),
    meta: { title: '仪表盘', owner: 'juice094', group: '概览', icon: 'dashboard' },
  },
  {
    path: 'admin/hr',
    name: 'HrManage',
    component: () => import('@/modules/admin/views/HrManage.vue'),
    meta: { title: '人事管理', owner: 'Ym3699', group: '管理', icon: 'hr' },
  },
  {
    path: 'admin/assets',
    name: 'AssetManage',
    component: () => import('@/modules/admin/views/AssetManage.vue'),
    meta: { title: '行政管理', owner: 'Ym3699', group: '管理', icon: 'assets' },
  },
  {
    path: 'admin/academic',
    name: 'AcademicManage',
    component: () => import('@/modules/admin/views/AcademicManage.vue'),
    meta: { title: '教务管理', owner: 'Ym3699', group: '管理', icon: 'academic' },
  },
  {
    path: 'admin/repair',
    name: 'RepairManage',
    component: () => import('@/modules/admin/views/RepairManage.vue'),
    meta: { title: '后勤管理', owner: 'Ym3699', group: '管理', icon: 'repair' },
  },
  {
    path: 'biz/marketing',
    name: 'Marketing',
    component: () => import('@/modules/business/views/Marketing.vue'),
    meta: { title: '市场活动', owner: 'juice094', group: '业务', icon: 'marketing' },
  },
  {
    path: 'biz/vocational',
    name: 'Vocational',
    component: () => import('@/modules/business/views/Vocational.vue'),
    meta: { title: '职业教育', owner: 'juice094', group: '业务', icon: 'vocational' },
  },
  {
    path: 'biz/student',
    name: 'StudentService',
    component: () => import('@/modules/business/views/StudentService.vue'),
    meta: { title: '学生服务', owner: 'juice094', group: '业务', icon: 'student' },
  },
  {
    path: 'tools/request',
    name: 'RequestForm',
    component: () => import('@/modules/business/views/RequestForm.vue'),
    meta: { title: '日常申请', owner: 'juice094', group: '工具', icon: 'request' },
  },
  {
    path: 'tools/approval',
    name: 'ApprovalCenter',
    component: () => import('@/modules/business/views/ApprovalCenter.vue'),
    meta: { title: '审批中心', owner: 'juice094', group: '工具', icon: 'approval', badge: 34 },
  },
  {
    path: 'tools/email',
    name: 'WorkEmail',
    component: () => import('@/modules/business/views/WorkEmail.vue'),
    meta: { title: '工作邮箱', owner: 'juice094', group: '工具', icon: 'email', badge: 3 },
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
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      children: [{ path: '', redirect: '/dashboard' }, ...layoutChildren],
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
