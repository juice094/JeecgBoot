// 团队业务 API —— /team/**（jeecg-module-team，自研）
import http from '@/utils/http';

export interface PageResult<T> {
  records: T[];
  total: number;
}

export interface Approval {
  id: string;
  code: string;
  category: string;
  title: string;
  reason?: string;
  status: 'pending' | 'approved' | 'rejected';
  applicantName?: string;
  createTime?: string;
}

export interface Task {
  id: string;
  taskNo?: number;
  title: string;
  description?: string;
  status: 'todo' | 'doing' | 'done';
  tag?: string;
  priority?: string;
  assigneeId?: string;
  assigneeName?: string;
  dueDate?: string;
  sortNo?: number;
  createTime?: string;
}

export interface TaskBoard {
  todo: Task[];
  doing: Task[];
  done: Task[];
}

export function fetchTaskBoard() {
  return http.get<any, { result: TaskBoard }>('/team/task/board');
}

export function addTask(body: Partial<Task>) {
  return http.post('/team/task/add', body);
}

export function editTask(body: Partial<Task>) {
  return http.put('/team/task/edit', body);
}

export function changeTaskStatus(id: string, status: string) {
  return http.put(`/team/task/status?id=${id}&status=${status}`);
}

export interface Bug {
  id: string;
  description: string;
  status: 'open' | 'processing' | 'resolved' | 'closed';
  reporterName?: string;
  createTime?: string;
}

/** 审批单分页；status 传 pending/approved/rejected，空为全部 */
export function listApprovals(params: { status?: string; applicantId?: string; pageNo?: number; pageSize?: number }) {
  return http.get<any, { result: PageResult<Approval> }>('/team/approval/list', { params });
}

/** 待我审批分页（后端两步走：wait 步骤按登录人姓名 + 主表 pending/currentStep 匹配） */
export interface DashboardSummary {
  pendingApprovalCount: number;
  myUnfinishedTaskCount: number;
  openBugCount: number;
}

/** 仪表盘 team 侧三计数聚合（sys 侧公告/未读聚合待宿裁决，保持 mock） */
export function fetchDashboardSummary() {
  return http.get<any, { result: DashboardSummary }>('/team/dashboard/summary');
}

export function listApprovalTodos(params: { pageNo?: number; pageSize?: number } = {}) {
  return http.get<any, { result: PageResult<Approval> }>('/team/approval/todo', { params });
}

// ponytail: 试点级审批链硬编码（贺致远 → 黄茜茜，与后端守卫的姓名比对口径一致）。
// 表单模板体系按 D-20261117-68 延后，模板重启后此链改由模板下发。
export const PILOT_APPROVAL_STEPS = [
  { approverName: '贺致远', approverRole: '部门主管' },
  { approverName: '黄茜茜', approverRole: '副总经理' },
] as const;

export function submitApproval(body: {
  category: string;
  title: string;
  reason?: string;
  startDate?: string;
  endDate?: string;
}) {
  return http.post('/team/approval/submit', {
    approval: body,
    steps: [...PILOT_APPROVAL_STEPS],
  });
}

/** 审批步骤链（时间线展示用） */
export interface ApprovalStep {
  id: string;
  stepNo: number;
  approverName: string;
  approverRole?: string;
  action: 'wait' | 'approved' | 'rejected';
  comment?: string;
  actedAt?: string;
}

export function fetchApprovalSteps(id: string) {
  return http.get<any, { result: ApprovalStep[] }>('/team/approval/steps', { params: { id } });
}
export function approveApproval(id: string) {
  return http.post(`/team/approval/approve?id=${id}`);
}

export function rejectApproval(id: string, comment?: string) {
  const qs = comment ? `?id=${id}&comment=${encodeURIComponent(comment)}` : `?id=${id}`;
  return http.post(`/team/approval/reject${qs}`);
}

/** 任务删除（服务端逻辑删 deleted=1，需 team:task:delete 权限且限归属人/管理员） */
export function deleteTask(id: string) {
  return http.delete(`/team/task/delete?id=${id}`);
}

/** 任务评论 */
export interface TaskComment {
  id: string;
  taskId: string;
  authorId?: string;
  authorName: string;
  body: string;
  createTime?: string;
}

/** 时间线事件（复用审计日志） */
export interface TaskEvent {
  id: string;
  bizType: string;
  bizId: string;
  bizCode?: string;
  action: string;
  fromValue?: string;
  toValue?: string;
  operatorId?: string;
  operatorName?: string;
  detail?: string;
  createTime?: string;
}

/** 任务详情聚合 */
export interface TaskDetail {
  task: Task;
  comments: TaskComment[];
  timeline: TaskEvent[];
}

/** 详情聚合：任务 + 评论 + 时间线一次拉取 */
export function fetchTaskDetail(id: string) {
  return http.get<any, { result: TaskDetail }>('/team/task/detail', { params: { id } });
}

/** 按任务查评论 */
export function listTaskComments(taskId: string) {
  return http.get<any, { result: TaskComment[] }>('/team/task/comment/list', { params: { taskId } });
}

/** 新增评论 */
export function addTaskComment(taskId: string, body: string) {
  return http.post<any, { result: TaskComment }>(`/team/task/comment/add?taskId=${taskId}`, { body });
}
export function addBug(description: string) {
  return http.post('/team/bug/add', { description });
}

/** Bug 状态流转（服务端状态机强校验，非法流转返回错误文案） */
export function changeBugStatus(id: string, status: string) {
  return http.put(`/team/bug/status?id=${id}&status=${status}`);
}

export function listBugs(params: { pageNo?: number; pageSize?: number; status?: string } = {}) {
  return http.get<any, { result: PageResult<Bug> }>('/team/bug/list', { params });
}
