/* 邮件体系业务枚举 —— 原型线骨架（依据 OA 邮件体系接口文档 v1.1.0）
 * 与 constants/task.ts 同风格；正式立项后建议迁移到 jeecg 字典（sys_dict）。
 * 归属：额外工作线（原型与骨架），非 docs/23 主合同施工项。
 */
import type { MailCanonicalFolder, MailSendMode, MailSendStatus } from '@/api/mail';

/** 发送状态展示（0 待发送 / 1 发送中 / 2 成功 / 3 失败 / 4 死信） */
export const MAIL_SEND_STATUS_META: Record<MailSendStatus, { label: string; className: string }> = {
  0: { label: '待发送', className: 'tag--info' },
  1: { label: '发送中', className: 'tag--info' },
  2: { label: '成功', className: 'tag--success' },
  3: { label: '失败', className: 'tag--danger' },
  4: { label: '死信', className: 'tag--warning' },
};

/** 规范文件夹展示顺序与中文名 */
export const MAIL_FOLDER_META: Array<{ key: MailCanonicalFolder; label: string }> = [
  { key: 'Inbox', label: '收件箱' },
  { key: 'Sent', label: '已发送' },
  { key: 'Drafts', label: '草稿箱' },
  { key: 'Trash', label: '已删除' },
  { key: 'Spam', label: '垃圾邮件' },
  { key: 'Archive', label: '归档' },
];

/** 写信模式 */
export const MAIL_SEND_MODES: Array<{ key: MailSendMode; label: string }> = [
  { key: 'new', label: '新邮件' },
  { key: 'reply', label: '回复' },
  { key: 'replyAll', label: '回复全部' },
  { key: 'forward', label: '转发' },
];

/** 接口文档 §7 结构化错误码（用于日志、指标标签与扩展字段） */
export const MAIL_ERROR_CODES = {
  TEMPLATE_NOT_FOUND: 'MAIL_TEMPLATE_NOT_FOUND',
  TEMPLATE_PARAM_MISS: 'MAIL_SEND_TEMPLATE_PARAM_MISS',
  ACCOUNT_DISABLED: 'MAIL_ACCOUNT_DISABLED',
  IDEMPOTENT_CONFLICT: 'MAIL_IDEMPOTENT_CONFLICT',
  AGENT_CONFIRM_REQUIRED: 'MAIL_AGENT_CONFIRM_REQUIRED',
  AGENT_TOKEN_EXPIRED: 'MAIL_AGENT_TOKEN_EXPIRED',
} as const;

/** 死信前最大重试次数（接口文档 §3.4 / §6） */
export const MAIL_MAX_RETRY = 5;

/** 列表分页上限（接口文档 §1） */
export const MAIL_PAGE_SIZE_MAX = 100;
