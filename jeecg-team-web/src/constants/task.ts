/* 任务看板业务枚举 —— 替代组件内字面量（docs/13 §二.5，issue #11）
 * 注意：正式接入后端后，标签/优先级应迁移到 jeecg 字典（sys_dict），本文件仅作 MVP 过渡。
 */

export const TASK_STATUSES = [
  { key: 'todo', title: '待办' },
  { key: 'doing', title: '进行中' },
  { key: 'done', title: '已完成' },
] as const;

export type TaskStatusKey = (typeof TASK_STATUSES)[number]['key'];

export const TASK_TAGS = ['需求', '开发', '缺陷'] as const;
export type TaskTag = (typeof TASK_TAGS)[number];

export const TASK_PRIORITIES = ['高', '中', '低'] as const;
export type TaskPriority = (typeof TASK_PRIORITIES)[number];

/** 标签 → 样式类名映射（配合全局 .tag--* 样式） */
export const TAG_CLASS: Record<TaskTag, string> = { 需求: 'tag--info', 开发: 'tag--success', 缺陷: 'tag--warning' };

/** 优先级 → 样式类名映射 */
export const PRIORITY_CLASS: Record<TaskPriority, string> = { 高: 'tag--warning', 中: 'tag--info', 低: 'tag--success' };

/** 标签选色调色板（GitHub/Primer light 实测值，LabelManagerModal 与 TaskDetail 共用） */
export const LABEL_PALETTE = [
  '#1a7f37',
  '#9a6700',
  '#cf222e',
  '#8250df',
  '#0969da',
  '#1b7c83',
  '#bf3989',
  '#59636e',
] as const;
