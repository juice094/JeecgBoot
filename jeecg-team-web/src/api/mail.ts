// 邮件体系 API 契约骨架 —— /team/mail/**（原型线，后端 jeecg-module-mail 未立项）
// 依据：projects/agent-collab-pilot/docs/OA 邮件体系接口文档.md v1.1.0（MAIL-API-001）
// 状态：契约占位。后端未实现，真实调用必然失败；WorkEmail.vue 默认走 mock，不计入联调基准。
// 归属：额外工作线（原型与骨架），非 docs/23 主合同施工项。
import http from '@/utils/http';

export interface PageResult<T> {
  records: T[];
  total: number;
}

const BASE = '/team/mail';

/* ==================== 通用 ==================== */

/** 发送状态：0 待发送 / 1 发送中 / 2 成功 / 3 失败 / 4 死信 */
export type MailSendStatus = 0 | 1 | 2 | 3 | 4;

/** 规范文件夹（接口文档 §4.2） */
export type MailCanonicalFolder = 'Inbox' | 'Sent' | 'Drafts' | 'Trash' | 'Spam' | 'Archive';

/** 写信模式（接口文档 §4.4） */
export type MailSendMode = 'new' | 'reply' | 'replyAll' | 'forward';

export interface MailServerConfig {
  host: string;
  port: number;
  ssl?: boolean;
  username: string;
  password: string;
}

export interface ConnectionTestResult {
  imapOk?: boolean;
  smtpOk: boolean;
  latencyMs?: number;
  errorCode?: string | null;
}

/* ==================== A 系统发信 ==================== */

export interface SystemMailAccount {
  id: number;
  host: string;
  port: number;
  username: string;
  sslEnable: boolean;
  starttlsEnable: boolean;
  fromAddress: string;
  fromName: string;
  status: number;
}

export interface SystemMailTemplate {
  id: number;
  code: string;
  name: string;
  accountId: number;
  title: string;
  content: string;
  /** 保存时扫描 {key} 自动生成 */
  params: string[];
  status: number;
}

export interface SystemMailLog {
  id: number;
  templateCode: string;
  toHash: string;
  sendStatus: MailSendStatus;
  retryCount: number;
  traceId: string;
  createTime: string;
  errorCode?: string;
  errorMsg?: string;
}

export interface SendMailReq {
  templateCode: string;
  to: string;
  cc?: string;
  bcc?: string;
  params: Record<string, unknown>;
  idempotencyKey?: string;
}

/** 3.1 账号管理 */
export function listSystemMailAccounts() {
  return http.get<any, { result: SystemMailAccount[] }>(`${BASE}/system/accounts`);
}

export function createSystemMailAccount(body: Partial<SystemMailAccount> & { password: string }) {
  return http.post(`${BASE}/system/accounts`, body);
}

export function updateSystemMailAccount(id: number, body: Partial<SystemMailAccount> & { password?: string }) {
  return http.put(`${BASE}/system/accounts/${id}`, body);
}

export function testSystemMailAccount(id: number) {
  return http.post<any, { result: ConnectionTestResult }>(`${BASE}/system/accounts/${id}/test`);
}

export function deleteSystemMailAccount(id: number) {
  return http.delete(`${BASE}/system/accounts/${id}`);
}

/** 3.2 模板管理 */
export function listSystemMailTemplates(params: { code?: string; name?: string; status?: number; pageNo?: number; pageSize?: number } = {}) {
  return http.get<any, { result: PageResult<SystemMailTemplate> }>(`${BASE}/system/templates`, { params });
}

export function createSystemMailTemplate(body: Partial<SystemMailTemplate>) {
  return http.post(`${BASE}/system/templates`, body);
}

export function updateSystemMailTemplate(id: number, body: Partial<SystemMailTemplate>) {
  return http.put(`${BASE}/system/templates/${id}`, body);
}

export function previewSystemMailTemplate(code: string, params: Record<string, unknown>) {
  return http.post(`${BASE}/system/templates/${code}/preview`, { params });
}

export function deleteSystemMailTemplate(id: number) {
  return http.delete(`${BASE}/system/templates/${id}`);
}

/** 3.3 发送 */
export function sendSystemMail(body: SendMailReq) {
  return http.post<any, { result: { logId: number } }>(`${BASE}/system/send`, body);
}

export function sendSystemMailBatch(list: SendMailReq[]) {
  return http.post(`${BASE}/system/send/batch`, list);
}

/** 3.4 日志查询与重试 */
export function listSystemMailLogs(params: {
  status?: MailSendStatus;
  templateCode?: string;
  toHash?: string;
  traceId?: string;
  beginTime?: string;
  endTime?: string;
  pageNo?: number;
  pageSize?: number;
} = {}) {
  return http.get<any, { result: PageResult<SystemMailLog> }>(`${BASE}/system/logs`, { params });
}

export function getSystemMailLog(id: number) {
  return http.get<any, { result: SystemMailLog }>(`${BASE}/system/logs/${id}`);
}

export function retrySystemMailLog(id: number) {
  return http.post(`${BASE}/system/logs/${id}/retry`);
}

export function getSystemMailLogStat() {
  return http.get(`${BASE}/system/logs/stat`);
}

/* ==================== B 员工邮箱 ==================== */

export interface MyMailAccount {
  id: number;
  email: string;
  testStatus: number;
  lastSyncTime?: string;
  syncRunning?: boolean;
}

export interface BindMailAccountReq {
  email: string;
  imap: MailServerConfig;
  smtp: MailServerConfig;
}

export interface MailFolder {
  remoteName: string;
  /** null 表示未识别，需通过映射接口维护 */
  canonical: MailCanonicalFolder | null;
  lastUid?: number;
}

export interface MailSyncStatus {
  lastUid: number;
  lastSyncTime?: string;
  running: boolean;
}

export interface MailMessageIndex {
  id: number;
  accountId: number;
  canonicalFolder: MailCanonicalFolder;
  subject: string;
  fromName: string;
  /** 脱敏 hash，非明文地址 */
  fromAddressHash: string;
  receivedTime: string;
  read: boolean;
  hasAttachment: boolean;
  snippet: string;
}

export interface MailAttachmentMeta {
  name: string;
  size: number;
  mime: string;
}

export interface MailMessageDetail extends MailMessageIndex {
  html: string;
  text?: string;
  attachments: MailAttachmentMeta[];
  fetchedFromImap: boolean;
  fetchLatencyMs?: number;
}

export interface SendMailMessageReq {
  accountId: number;
  mode: MailSendMode;
  replyToMessageId?: number;
  to: string;
  cc?: string[];
  bcc?: string[];
  subject: string;
  html: string;
  attachments?: string[];
  idempotencyKey?: string;
}

/** 4.1 绑定与测连 */
export function listMyMailAccounts() {
  return http.get<any, { result: MyMailAccount[] }>(`${BASE}/accounts`);
}

export function bindMyMailAccount(body: BindMailAccountReq) {
  return http.post(`${BASE}/accounts`, body);
}

export function updateMyMailAccount(id: number, body: Partial<BindMailAccountReq>) {
  return http.put(`${BASE}/accounts/${id}`, body);
}

export function testMyMailAccount(id: number) {
  return http.post<any, { result: ConnectionTestResult }>(`${BASE}/accounts/${id}/test`);
}

export function unbindMyMailAccount(id: number) {
  return http.delete(`${BASE}/accounts/${id}`);
}

/** 4.2 文件夹与同步 */
export function listMailFolders(accountId: number) {
  return http.get<any, { result: { folders: MailFolder[] } }>(`${BASE}/accounts/${accountId}/folders`);
}

export function updateMailFolderMapping(accountId: number, mapping: Array<{ remoteName: string; canonical: MailCanonicalFolder }>) {
  return http.put(`${BASE}/accounts/${accountId}/folders/mapping`, { mapping });
}

export function triggerMailSync(accountId: number) {
  return http.post<any, { result: { taskId: string } }>(`${BASE}/accounts/${accountId}/sync`);
}

export function getMailSyncStatus(accountId: number) {
  return http.get<any, { result: MailSyncStatus }>(`${BASE}/accounts/${accountId}/sync/status`);
}

/** 4.3 列表与详情（列表只查索引表） */
export function listMailMessages(params: {
  accountId: number;
  canonicalFolder?: MailCanonicalFolder;
  keyword?: string;
  unreadOnly?: boolean;
  pageNo?: number;
  pageSize?: number;
}) {
  return http.get<any, { result: PageResult<MailMessageIndex> }>(`${BASE}/messages`, { params });
}

export function getMailMessage(id: number) {
  return http.get<any, { result: MailMessageDetail }>(`${BASE}/messages/${id}`);
}

export function markMailRead(id: number, read: boolean) {
  return http.patch(`${BASE}/messages/${id}/read`, { read });
}

export function deleteMailMessage(id: number) {
  return http.delete(`${BASE}/messages/${id}`);
}

/** 4.4 发送与业务关联 */
export function sendMailMessage(body: SendMailMessageReq) {
  return http.post(`${BASE}/messages/send`, body);
}

export function mailMessageToTodo(id: number, body: { bizType: string; bizId: string }) {
  return http.post(`${BASE}/messages/${id}/to-todo`, body);
}

export function listMailBizLinks(id: number) {
  return http.get(`${BASE}/messages/${id}/biz-links`);
}

/* ==================== C Agent 受控接口（封存线，本期不实施） ==================== */

/** 封存线占位：仅在解封后启用，调用前须有 confirmToken。 */
export function draftWeeklyReport(body: { period: string; scope: string[]; recipients: string[] }) {
  return http.post(`${BASE}/agent/weekly-report/draft`, body);
}

export function confirmWeeklyReport(body: { draftId: string; confirmToken: string; confirmedBy: string }) {
  return http.post(`${BASE}/agent/weekly-report/confirm`, body);
}

export function searchAgentMessages(params: { keyword: string; pageNo?: number; pageSize?: number }) {
  return http.get(`${BASE}/agent/messages/search`, { params });
}

export function triageMail(body: { messageId: number }) {
  return http.post(`${BASE}/agent/triage`, body);
}
