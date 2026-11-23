// 流程中心 API —— /flow/**（jeecg-module-flow，Flowable W2 端点）
// 契约源：FlowProcessController.java（W2）；请假启动走 /flow/demo/leave/start（W1 冒烟端点，
// W2 未提供请假发起端点，process/start 仅覆盖报销，属后端缺口，后端补齐后前端只改本文件）。
import http from '@/utils/http';

export enum Api {
  ProcessList = '/flow/process/list',
  ProcessStart = '/flow/process/start',
  ApproveTodo = '/flow/approve/todo',
  ApproveDone = '/flow/approve/done',
  ApproveHandle = '/flow/approve/handle',
  InstanceDetail = '/flow/instance/detail',
  /** W1 冒烟端点（@IgnoreAuth），W2 无请假发起端点前的过渡消费 */
  DemoLeaveStart = '/flow/demo/leave/start',
}

/** 流程定义摘要（listProcessDefinitions） */
export interface FlowProcessDef {
  processDefinitionKey: string;
  processDefinitionName?: string;
  version?: number;
  deploymentId?: string;
  suspended?: boolean;
  runningInstances?: number;
}

/** 待办任务摘要（listTodoTasks） */
export interface FlowTodoTask {
  taskId: string;
  taskName?: string;
  processInstanceId?: string;
  processDefinitionName?: string;
  createTime?: string;
}

/** 已办任务摘要（listDoneTasks） */
export interface FlowDoneTask {
  taskId: string;
  taskName?: string;
  processInstanceId?: string;
  processDefinitionName?: string;
  startTime?: string;
  endTime?: string;
  durationInMillis?: number;
}

/** 办理动作（大小写不敏感，FlowWebConverterConfig 已注册宽松转换） */
export type FlowApproveAction = 'approve' | 'reject';

/** 活动节点轨迹（instanceDetail.activityTrace 元素） */
export interface FlowActivityNode {
  activityId?: string;
  activityName?: string;
  activityType?: string;
  startTime?: string;
  endTime?: string;
  durationInMillis?: number;
  assignee?: string;
}

/** 任务轨迹（instanceDetail.taskTrace 元素） */
export interface FlowTaskNode {
  taskId?: string;
  taskName?: string;
  assignee?: string;
  startTime?: string;
  endTime?: string;
  durationInMillis?: number;
  deleteReason?: string;
  comments?: string[];
}

/** 流程实例详情（instanceDetail） */
export interface FlowInstanceDetail {
  processInstanceId: string;
  processDefinitionKey?: string;
  processDefinitionName?: string;
  startTime?: string;
  endTime?: string;
  durationInMillis?: number;
  finished: boolean;
  /** 完结端：rejectedEnd=驳回 / approvedEnd=通过 / 其他（如 endEvent） */
  endActivityId?: string;
  variables?: Record<string, unknown>;
  activityTrace?: FlowActivityNode[];
  taskTrace?: FlowTaskNode[];
  openTasks?: string[];
}

export function fetchProcessList() {
  return http.get<any, { result: FlowProcessDef[] }>(Api.ProcessList);
}

/**
 * 发起报销流程。
 * 后端校验口径（FlowExpenseService）：金额 >0、≤1000000、最多两位小数；事由必填 ≤500 字；
 * 附件说明可空 ≤500 字；部门主管/副总办理人必填非空。
 */
export function startExpenseProcess(params: {
  amount: number;
  reason: string;
  attachmentNote?: string;
  deptManager: string;
  vp: string;
}) {
  return http.post<any, { result: string }>(Api.ProcessStart, undefined, { params });
}

/**
 * 发起请假流程（W1 冒烟端点）。
 * 后端校验口径（FlowLeaveService）：天数整数 1-365；申请人必填（前端传当前登录 username）。
 */
export function startLeaveProcess(params: { applicant: string; days: number; reason?: string }) {
  return http.post<any, { result: string }>(Api.DemoLeaveStart, undefined, { params });
}

export function fetchTodoList() {
  return http.get<any, { result: FlowTodoTask[] }>(Api.ApproveTodo);
}

export function fetchDoneList() {
  return http.get<any, { result: FlowDoneTask[] }>(Api.ApproveDone);
}

/** 审批办理（通过/驳回）；并发冲突/任务被他人办理由后端转 409 业务提示 */
export function handleApproveTask(taskId: string, action: FlowApproveAction, comment?: string) {
  return http.post<any, { result: string }>(Api.ApproveHandle, undefined, {
    params: { taskId, action, ...(comment ? { comment } : {}) },
  });
}

export function fetchInstanceDetail(processInstanceId: string) {
  return http.get<any, { result: FlowInstanceDetail }>(Api.InstanceDetail, { params: { processInstanceId } });
}
