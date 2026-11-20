/**
 * 统一工作项模型（Issue）
 *
 * 设计依据：GitHub 用一个 Issues 承载 Task/Bug/Feature，差异靠 type + 自定义字段，
 * 而不是建三套独立实体。本文件是前端侧的规范化层：
 *   - 三类后端实体各自保留原有状态机，不做合并
 *   - 通过 adapter 映射到统一视图模型，供聚合列表消费
 *   - 状态语义归一化为 open / active / done / closed 四档，原文案保留在 stateLabel
 */

import type { Task } from '@/api/team';
import type { Bug, Approval } from '@/api/team';
import { TAG_CLASS, PRIORITY_CLASS, type TaskTag, type TaskPriority } from '@/constants/task';
import { BUG_STATUSES, BUG_STATUS_TAG, type BugStatusKey } from '@/constants/bug';

export type IssueKind = 'task' | 'bug' | 'approval';

/** 归一化状态：四档语义，覆盖三类实体的全部状态 */
export type UnifiedState = 'open' | 'active' | 'done' | 'closed';

export interface IssueLabel {
  text: string;
  cls: string;
}

export interface UnifiedIssue {
  /** 全局唯一键：kind + ':' + 原始 id */
  uid: string;
  kind: IssueKind;
  /** 原始 id，用于回写操作 */
  rawId: string;
  title: string;
  summary: string;
  state: UnifiedState;
  /** 原始状态文案（如「处理中」「审批中」），保留业务语义 */
  stateLabel: string;
  /** 状态标签样式类 */
  stateCls: string;
  labels: IssueLabel[];
  assignee?: string;
  dueDate?: string;
  overdue: boolean;
  createdAt?: string;
  /** 跳转到该类实体的专属详情/操作入口 */
  sourcePath: string;
}

export const ISSUE_KIND_META: Record<IssueKind, { label: string; cls: string; path: string }> = {
  task: { label: '任务', cls: 'tag--info', path: '/tools/taskboard' },
  bug: { label: '缺陷', cls: 'tag--danger', path: '/tools/bug' },
  approval: { label: '审批', cls: 'tag--warning', path: '/tools/approval' },
};

/** 归一化状态 → 展示文案与样式 */
export const STATE_META: Record<UnifiedState, { label: string; cls: string; symbol: string }> = {
  open: { label: '待处理', cls: 'tag--neutral', symbol: '○' },
  active: { label: '进行中', cls: 'tag--info', symbol: '◐' },
  done: { label: '已完成', cls: 'tag--success', symbol: '✓' },
  closed: { label: '已关闭', cls: 'tag--neutral', symbol: '✕' },
};

function isOverdue(dueDate?: string, finished?: boolean): boolean {
  if (!dueDate || finished) return false;
  const today = new Date();
  const pad = (n: number) => (n < 10 ? '0' + n : String(n));
  const todayStr = today.getFullYear() + '-' + pad(today.getMonth() + 1) + '-' + pad(today.getDate());
  return dueDate < todayStr;
}

function truncate(text: string, max: number): string {
  const flat = text.split('\n').join(' ').trim();
  return flat.length > max ? flat.slice(0, max) + '…' : flat;
}

/** Task → UnifiedIssue */
export function fromTask(task: Task): UnifiedIssue {
  const stateMap: Record<string, UnifiedState> = { todo: 'open', doing: 'active', done: 'done' };
  const state = stateMap[task.status] ?? 'open';

  const labels: IssueLabel[] = [];
  if (task.tag) {
    labels.push({ text: task.tag, cls: TAG_CLASS[task.tag as TaskTag] ?? 'tag--neutral' });
  }
  if (task.priority) {
    labels.push({ text: task.priority + '优先级', cls: PRIORITY_CLASS[task.priority as TaskPriority] ?? 'tag--neutral' });
  }

  return {
    uid: 'task:' + task.id,
    kind: 'task',
    rawId: task.id,
    title: task.title,
    summary: truncate(task.description || '', 120),
    state,
    stateLabel: state === 'done' ? '已完成' : state === 'active' ? '进行中' : '待办',
    stateCls: state === 'done' ? 'tag--success' : state === 'active' ? 'tag--info' : 'tag--neutral',
    labels,
    assignee: task.assigneeName,
    dueDate: task.dueDate,
    overdue: isOverdue(task.dueDate, task.status === 'done'),
    createdAt: task.createTime,
    sourcePath: ISSUE_KIND_META.task.path,
  };
}

/** Bug → UnifiedIssue（无 title 字段，取描述首行并截断） */
export function fromBug(bug: Bug): UnifiedIssue {
  const stateMap: Record<string, UnifiedState> = {
    open: 'open',
    processing: 'active',
    resolved: 'done',
    closed: 'closed',
  };
  const state = stateMap[bug.status] ?? 'open';
  const statusMeta = BUG_STATUSES.find((s) => s.key === bug.status);

  return {
    uid: 'bug:' + bug.id,
    kind: 'bug',
    rawId: bug.id,
    title: truncate(bug.description, 60),
    summary: truncate(bug.description, 160),
    state,
    stateLabel: statusMeta?.title ?? bug.status,
    stateCls: BUG_STATUS_TAG[bug.status as BugStatusKey] ?? 'tag--neutral',
    labels: [],
    assignee: bug.reporterName,
    overdue: false,
    createdAt: bug.createTime,
    sourcePath: ISSUE_KIND_META.bug.path,
  };
}

/** Approval → UnifiedIssue */
export function fromApproval(approval: Approval): UnifiedIssue {
  const stateMap: Record<string, UnifiedState> = {
    draft: 'open',
    pending: 'active',
    returned: 'active',
    approved: 'done',
    rejected: 'closed',
    canceled: 'closed',
  };
  const state = stateMap[approval.status] ?? 'active';
  const labelMap: Record<string, string> = {
    draft: '草稿',
    pending: '审批中',
    returned: '已退回',
    approved: '已通过',
    rejected: '已拒绝',
    canceled: '已撤销',
  };
  const clsMap: Record<string, string> = {
    draft: 'tag--neutral',
    pending: 'tag--warning',
    returned: 'tag--info',
    approved: 'tag--success',
    rejected: 'tag--danger',
    canceled: 'tag--neutral',
  };

  return {
    uid: 'approval:' + approval.id,
    kind: 'approval',
    rawId: approval.id,
    title: approval.title,
    summary: truncate(approval.reason || '', 120),
    state,
    stateLabel: labelMap[approval.status] ?? approval.status,
    stateCls: clsMap[approval.status] ?? 'tag--neutral',
    labels: [{ text: approval.category, cls: 'tag--neutral' }],
    assignee: approval.applicantName,
    overdue: false,
    createdAt: approval.createTime,
    sourcePath: ISSUE_KIND_META.approval.path,
  };
}

export const ISSUE_KINDS: IssueKind[] = ['task', 'bug', 'approval'];
export const UNIFIED_STATES: UnifiedState[] = ['open', 'active', 'done', 'closed'];
