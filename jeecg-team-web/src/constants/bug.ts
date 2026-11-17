/* Bug 反馈业务枚举 —— 与后端 TeamBugStatus 口径一致（docs/13 §二.5，issue #11）
 * 状态机：open→processing/closed；processing→resolved/open；resolved→closed/open；closed 终态
 */

export const BUG_STATUSES = [
  { key: 'open', title: '待处理' },
  { key: 'processing', title: '处理中' },
  { key: 'resolved', title: '已解决' },
  { key: 'closed', title: '已关闭' },
] as const;

export type BugStatusKey = (typeof BUG_STATUSES)[number]['key'];

/** 合法流转表（与后端 TeamBugStatus.canTransitTo 对齐；closed 终态无出口） */
export const BUG_TRANSITIONS: Record<BugStatusKey, BugStatusKey[]> = {
  open: ['processing', 'closed'],
  processing: ['resolved', 'open'],
  resolved: ['closed', 'open'],
  closed: [],
};

export const BUG_STATUS_TAG: Record<BugStatusKey, string> = {
  open: 'tag-warning',
  processing: 'tag-info',
  resolved: 'tag-success',
  closed: 'tag-danger',
};
