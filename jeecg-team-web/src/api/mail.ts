// 邮件体系 API —— /team/mail/**（后端 jeecg-module-mail）
// 依据：后端 5 个 Controller 实际注解与 DTO 字段（2026-11-18 逐一核对）
// 状态：契约已与后端对齐；B-3/B-5/B-6 未实现的端点已显式标注。
// 注意：jeecg 实体主键为 String；jeecg IPage 返回 records/total/current/pages/size。
import http from '@/utils/http';

/** jeecg IPage */
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
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

/** 后端 ConnectionTestResult：IMAP 侧失败时 errorCode 前缀 imap_，SMTP 侧 smtp_ */
export interface ConnectionTestResult {
  imapOk?: boolean;
  smtpOk: boolean;
  latencyMs?: number;
  errorCode?: string | null;
}

/* ==================== A 系统发信 ==================== */

/** 后端 SystemMailAccountRespDTO（不含 password） */
export interface SystemMailAccount {
  id: string;
  host: string;
  port: number;
  username: string;
  sslEnable: boolean;
  starttlsEnable: boolean;
  fromAddress: string;
  fromName: string;
  status: number;
  createTime?: string;
}

export interface SystemMailAccountCreateReq {
  host: string;
  port: number;
  username: string;
  password: string;
  sslEnable?: boolean;
  starttlsEnable?: boolean;
  fromAddress: string;
  fromName?: string;
}

export interface SystemMailTemplate {
  id: string;
  code: string;
  name: string;
  accountId: string;
  title: string;
  content: string;
  /** 保存时扫描 {key} 自动生成 */
  params: string[];
  status: number;
}

export interface SystemMailLog {
  id: string;
  accountId?: string;
  templateCode: string;
  toMails?: string;
  toHash: string;
  sendStatus: MailSendStatus;
  retryCount: number;
  traceId?: string;
  templateTitle?: string;
  templateContent?: string;
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

/** 批量单条结果（后端 MailBatchSendItemResult） */
export interface MailBatchSendItemResult {
  index: number;
  templateCode: string;
  success: boolean;
  logId: string | null;
  errorMessage: string | null;
}

/** 3.1 账号管理 */
export function listSystemMailAccounts(params: { pageNo?: number; pageSize?: number; keyword?: string } = {}) {
  return http.get<any, { result: PageResult<SystemMailAccount> }>(`${BASE}/system/accounts`, { params });
}

export function createSystemMailAccount(body: SystemMailAccountCreateReq) {
  return http.post<any, { result: string }>(`${BASE}/system/accounts`, body);
}

export function updateSystemMailAccount(id: string, body: Partial<SystemMailAccountCreateReq>) {
  return http.put<any, { result: string }>(`${BASE}/system/accounts/${id}`, body);
}

export function testSystemMailAccount(id: string) {
  return http.post<any, { result: ConnectionTestResult }>(`${BASE}/system/accounts/${id}/test`);
}

export function deleteSystemMailAccount(id: string) {
  return http.delete<any, { result: string }>(`${BASE}/system/accounts/${id}`);
}

/** 3.2 模板管理 */
export function listSystemMailTemplates(params: { code?: string; name?: string; status?: number; pageNo?: number; pageSize?: number } = {}) {
  return http.get<any, { result: PageResult<SystemMailTemplate> }>(`${BASE}/system/templates`, { params });
}

export function createSystemMailTemplate(body: Partial<SystemMailTemplate>) {
  return http.post<any, { result: string }>(`${BASE}/system/templates`, body);
}

export function updateSystemMailTemplate(id: string, body: Partial<SystemMailTemplate>) {
  return http.put<any, { result: string }>(`${BASE}/system/templates/${id}`, body);
}

export function getSystemMailTemplateByCode(code: string) {
  return http.get<any, { result: SystemMailTemplate }>(`${BASE}/system/templates/${code}`);
}

/** 预览返回 MailTemplatePreviewDTO：{ title, content, params } */
export function previewSystemMailTemplate(code: string, params: Record<string, unknown>) {
  return http.post<any, { result: { title: string; content: string; params: string[] } }>(
    `${BASE}/system/templates/${code}/preview`,
    { params },
  );
}

export function deleteSystemMailTemplate(id: string) {
  return http.delete<any, { result: string }>(`${BASE}/system/templates/${id}`);
}

/** 3.3 发送（后端返回 Map<String,String> → { logId }） */
export function sendSystemMail(body: SendMailReq) {
  return http.post<any, { result: { logId: string } }>(`${BASE}/system/send`, body);
}

export function sendSystemMailBatch(list: SendMailReq[]) {
  return http.post<any, { result: MailBatchSendItemResult[] }>(`${BASE}/system/send/batch`, list);
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

export function getSystemMailLog(id: string) {
  return http.get<any, { result: SystemMailLog }>(`${BASE}/system/logs/${id}`);
}

export function retrySystemMailLog(id: string) {
  return http.post<any, { result: string }>(`${BASE}/system/logs/${id}/retry`);
}

/** 注意：后端为 POST /stat（非 GET），返回 MailLogStatDTO */
export function getSystemMailLogStat() {
  return http.post<any, { result: { statusCounts: Record<string, number>; templateCounts: Record<string, number> } }>(
    `${BASE}/system/logs/stat`,
  );
}

/* ==================== B 员工邮箱 ==================== */

/** 后端 OaMailAccountRespDTO（不含密码） */
export interface MyMailAccount {
  id: string;
  email: string;
  imapHost?: string;
  imapPort?: number;
  imapSsl?: boolean;
  imapUsername?: string;
  smtpHost?: string;
  smtpPort?: number;
  smtpSsl?: boolean;
  smtpUsername?: string;
  testStatus: number;
  lastSyncTime?: string;
}

export interface BindMailAccountReq {
  email: string;
  imap: MailServerConfig;
  smtp: MailServerConfig;
}

/** 后端 OaMailFolderState */
export interface MailFolder {
  id: string;
  accountId: string;
  remoteName: string;
  /** null 表示未识别，需通过映射接口维护 */
  canonical: MailCanonicalFolder | null;
  lastUid?: number;
}

/** 后端 MailSyncStatusDTO：无聚合 lastUid，各文件夹游标在 folders[] 中 */
export interface MailSyncStatus {
  accountId: string;
  running: boolean;
  lastSyncTime?: string;
  folders: MailFolder[];
}

/** 后端 OaMailMessageIndex：注意字段为 readFlag（非 read） */
export interface MailMessageIndex {
  id: string;
  accountId: string;
  canonicalFolder: MailCanonicalFolder;
  imapUid?: number;
  messageId?: string;
  subject: string;
  fromName: string;
  /** 脱敏 hash，非明文地址 */
  fromAddressHash: string;
  toSummary?: string;
  receivedTime: string;
  readFlag: boolean;
  hasAttachment: boolean;
  snippet: string;
}

/** B-3 未实现：详情结构按接口文档预留，后端端点尚未提供 */
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
  accountId: string;
  mode: MailSendMode;
  replyToMessageId?: string;
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
  return http.post<any, { result: string }>(`${BASE}/accounts`, body);
}

export function updateMyMailAccount(id: string, body: Partial<BindMailAccountReq>) {
  return http.put<any, { result: string }>(`${BASE}/accounts/${id}`, body);
}

export function testMyMailAccount(id: string) {
  return http.post<any, { result: ConnectionTestResult }>(`${BASE}/accounts/${id}/test`);
}

export function unbindMyMailAccount(id: string) {
  return http.delete<any, { result: string }>(`${BASE}/accounts/${id}`);
}

/** 4.2 文件夹与同步 */

/** 后端直接返回 List<OaMailFolderState>，无 { folders } 包装 */
export function listMailFolders(accountId: string) {
  return http.get<any, { result: MailFolder[] }>(`${BASE}/accounts/${accountId}/folders`);
}

/** 后端 MailFolderMappingReqDTO 为单条 { remoteName, canonical }，非数组 */
export function updateMailFolderMapping(accountId: string, mapping: { remoteName: string; canonical: MailCanonicalFolder }) {
  return http.put<any, { result: string }>(`${BASE}/accounts/${accountId}/folders/mapping`, mapping);
}

export function triggerMailSync(accountId: string) {
  return http.post<any, { result: { taskId: string } }>(`${BASE}/accounts/${accountId}/sync`);
}

export function getMailSyncStatus(accountId: string) {
  return http.get<any, { result: MailSyncStatus }>(`${BASE}/accounts/${accountId}/sync/status`);
}

/** 4.3 列表与详情（列表只查索引表） */
export function listMailMessages(params: {
  accountId: string;
  canonicalFolder?: MailCanonicalFolder;
  keyword?: string;
  unreadOnly?: boolean;
  pageNo?: number;
  pageSize?: number;
}) {
  return http.get<any, { result: PageResult<MailMessageIndex> }>(`${BASE}/messages`, { params });
}

/** 4.3 列表与详情（列表查索引表；详情实时 FETCH 正文，B-3） */
export function getMailMessage(id: string) {
  return http.get<any, { result: MailMessageDetail }>(`${BASE}/messages/${id}`);
}

/** B-3 已读回写：IMAP \Seen + 索引 read_flag */
export function markMailRead(id: string, read: boolean) {
  return http.patch<any, { result: string }>(`${BASE}/messages/${id}/read`, { read });
}

/** B-5 删除：Trash 映射优先移入，否则永久删除 */
export function deleteMailMessage(id: string) {
  return http.delete<any, { result: string }>(`${BASE}/messages/${id}`);
}

/** 4.4 发送（B-6）：经绑定账号 SMTP 直发 */
export function sendMailMessage(body: SendMailMessageReq) {
  return http.post<any, { result: { logId: string } }>(`${BASE}/messages/send`, body);
}

export function mailMessageToTodo(id: string, body: { bizType: string; bizId: string }) {
  return http.post<any, { result: string }>(`${BASE}/messages/${id}/to-todo`, body);
}

export function listMailBizLinks(id: string) {
  return http.get<any, { result: unknown[] }>(`${BASE}/messages/${id}/biz-links`);
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

export function triageMail(body: { messageId: string }) {
  return http.post(`${BASE}/agent/triage`, body);
}
