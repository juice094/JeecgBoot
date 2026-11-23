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
  status: 'draft' | 'pending' | 'returned' | 'approved' | 'rejected' | 'canceled';
  applicantId?: string;
  applicantName?: string;
  currentStep?: number;
  /** 类别专属字段值（JSON 字符串，按模板 fieldSchema 校验） */
  formData?: string;
  /** 金额（后端从 formData.amount 提取） */
  amount?: number;
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
  createBy?: string;
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

/** GET /team/task/list 分页参数（QueryGenerator 口径，见 TeamTaskController#queryPageList） */
export interface TaskListParams {
  /** 状态过滤：单值等值；多值逗号分隔走 IN（如 open 口径 'todo,doing'，closed 口径 'done'） */
  status?: string;
  assigneeId?: string;
  priority?: string;
  tag?: string;
  createBy?: string;
  /** 标题模糊：QueryGenerator 以值含 * 判定 LIKE，调用方需自带通配（如 '*关键字*'） */
  title?: string;
  /** 租户内序号精确匹配（#编号搜索） */
  taskNo?: number;
  /** 排序字段（库列名，如 create_time / due_date；优先级为中文枚举，语义序只能客户端排） */
  column?: string;
  /** 排序方向：asc / desc */
  order?: string;
  pageNo?: number;
  pageSize?: number;
}

/** 任务分页（服务端筛选/排序，records/total 口径同 /list 其他端点） */
export function listTasks(params: TaskListParams) {
  return http.get<any, { result: PageResult<Task> }>('/team/task/list', { params });
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

/**
 * 审批单分页。
 * - status 传 pending/returned/approved/rejected/canceled，空为全部
 * - applicantId 过滤我发起的
 * - approverId 过滤我参与审批的（后端按 step 表 approver_id 匹配，历史行按姓名兜底）
 */
export function listApprovals(params: {
  status?: string;
  applicantId?: string;
  approverId?: string;
  pageNo?: number;
  pageSize?: number;
}) {
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

// 试点级审批链：**仅作 fallback**。后端 v2 按 category 查启用模板优先；
// 模板缺失时回退到本链，保证试点期零配置也能提交。模板配好后前端无需改动即自动切换。
// 姓名与后端守卫的姓名比对口径一致（历史行 approver_id 为空时按姓名兜底）。
export const PILOT_APPROVAL_STEPS = [
  { approverName: '贺致远', approverRole: '部门主管' },
  { approverName: '黄茜茜', approverRole: '副总经理' },
] as const;

/**
 * 提交申请。
 * 后端 v2 优先按 category 查启用模板生成审批链；模板缺失时回退到 body.steps。
 * 保留 PILOT_APPROVAL_STEPS 作为 fallback，避免试点期模板未配置时报错。
 */
/** 字段契约元素（与后端 field_schema 元素结构一致） */
export interface ApprovalFieldDef {
  key: string;
  label: string;
  type: 'text' | 'textarea' | 'number' | 'date' | 'select' | 'multiselect';
  required?: boolean;
  options?: string[];
  min?: number;
  max?: number;
}

/** 按类别取启用模板的字段契约；无模板/无 schema 返回空数组 */
export function fetchApprovalFieldSchema(category: string) {
  return http.get<any, { result: { fieldSchema?: string } | null }>('/team/approval/template/schema', {
    params: { category },
  });
}

export function submitApproval(body: {
  category: string;
  title: string;
  reason?: string;
  startDate?: string;
  endDate?: string;
  formData?: string;
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
  approverId?: string;
  action: 'wait' | 'approved' | 'rejected' | 'returned';
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

/** 审批人退回至申请人（状态转 returned，可 resubmit） */
export function returnApproval(id: string, comment?: string) {
  const qs = comment ? `?id=${id}&comment=${encodeURIComponent(comment)}` : `?id=${id}`;
  return http.post(`/team/approval/return${qs}`);
}

/** 申请人撤回（仅 pending 且所有步骤均未操作，终态 canceled） */
export function cancelApproval(id: string) {
  return http.post(`/team/approval/cancel?id=${id}`);
}

/** 申请人重提（仅 returned，按模板重建审批链，状态回 pending） */
export function resubmitApproval(id: string, steps?: { approverName: string; approverRole?: string }[]) {
  return http.post('/team/approval/resubmit', { id, steps });
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
