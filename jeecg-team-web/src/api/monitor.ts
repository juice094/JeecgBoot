// 系统监控 API —— jeecg 原生 /sys/**（只读口径，来源：jeecgboot-vue3 src/views/monitor/*/ *.api.ts，docs/24 A/B 档）
import http from '@/utils/http';
import { API_BASE_URL } from '@/utils/http';
import { TOKEN_STORAGE_KEY } from '@/constants/app';
import type { PageResult } from '@/api/team';

// ---- 日志管理（/sys/log，SysLog 实体字段口径） ----
export interface SysLogRow {
  id: string;
  createTime?: string;
  username?: string;
  logContent?: string;
  logType?: number; // 1=登录日志 2=操作日志
  operateType?: number;
  requestUrl?: string;
  requestType?: string;
  ip?: string;
  costTime?: number;
}

export function listLogs(params: { pageNo?: number; pageSize?: number; username?: string; logType?: number }) {
  return http.get<any, { result: PageResult<SysLogRow> }>('/sys/log/list', { params });
}

// ---- 定时任务（/sys/quartzJob） ----
export interface QuartzJobRow {
  id: string;
  jobClassName?: string;
  cronExpression?: string;
  parameter?: string;
  description?: string;
  status?: number; // 0=停止 1=运行中
}

export function listQuartzJobs(params: { pageNo?: number; pageSize?: number; jobClassName?: string }) {
  return http.get<any, { result: PageResult<QuartzJobRow> }>('/sys/quartzJob/list', { params });
}

/** 新增/编辑定时任务 POST /sys/quartzJob/add | /sys/quartzJob/edit */
export function saveQuartzJob(params: QuartzJobRow & { id?: string }, isUpdate: boolean): Promise<any> {
  return http.post(isUpdate ? '/sys/quartzJob/edit' : '/sys/quartzJob/add', params);
}

/** 删除定时任务 DELETE /sys/quartzJob/delete?id= */
export function deleteQuartzJob(id: string): Promise<any> {
  return http.delete('/sys/quartzJob/delete', { params: { id } });
}

/** 暂停/启动/立即执行 GET /sys/quartzJob/pause|resume|execute?id= */
export function controlQuartzJob(action: 'pause' | 'resume' | 'execute', id: string): Promise<any> {
  return http.get(`/sys/quartzJob/${action}`, { params: { id } });
}

// ---- 数据变更日志（/sys/dataLog，确权审计：数据修改前后快照；权限码 system:dataLog:*） ----
export interface DataLogRow {
  id: string;
  createBy?: string;
  createName?: string;
  createTime?: string;
  dataTable?: string;
  dataId?: string;
  dataContent?: string;
  dataVersion?: number;
  type?: string;
}

export function listDataLogs(params: { pageNo?: number; pageSize?: number; dataTable?: string; createBy?: string }) {
  return http.get<any, { result: PageResult<DataLogRow> }>('/sys/dataLog/list', { params });
}

/** 历史版本列表 GET /sys/dataLog/queryDataVerList?dataTable=&dataId=&type=json */
export function listDataLogVersions(params: { dataTable: string; dataId: string }) {
  return http.get<any, { result: DataLogRow[] }>('/sys/dataLog/queryDataVerList', { params: { ...params, type: 'json' } });
}

/** 两版本对比 GET /sys/dataLog/queryCompareList?dataId1=&dataId2= */
export function compareDataLogs(dataId1: string, dataId2: string) {
  return http.get<any, { result: DataLogRow[] }>('/sys/dataLog/queryCompareList', { params: { dataId1, dataId2 } });
}

// ---- 在线用户（/sys/online，SysUserOnlineVO 口径） ----
export interface OnlineUserRow {
  id: string;
  username?: string;
  realname?: string;
  phone?: string;
  sex?: number; // 1=男 2=女
  /** 会话标识（强退按此字段），注意：id 是用户主键，同用户恒相同，不能当会话ID展示 */
  token?: string;
}

export function listOnlineUsers(params: { pageNo?: number; pageSize?: number; username?: string }) {
  return http.get<any, { result: PageResult<OnlineUserRow> }>('/sys/online/list', { params });
}

// ---- Redis 监控（/sys/actuator/redis，注意：keysSize/memoryInfo 为裸 Map 返回，非 Result 包装） ----
export interface RedisInfoItem {
  key?: string;
  value?: string;
  description?: string;
}

/** key 数量（裸 Map：{ create_time, dbSize }） */
export function fetchRedisKeysSize() {
  return http.get<any, { create_time?: number; dbSize?: number }>('/sys/actuator/redis/keysSize');
}

/** 占用内存字节数（裸 Map：{ used_memory, create_time }；可能为 null） */
export function fetchRedisMemoryInfo() {
  return http.get<any, { used_memory?: string; create_time?: number } | null>('/sys/actuator/redis/memoryInfo');
}

/** Redis 信息明细（Result 包装，List<RedisInfo>） */
export function fetchRedisInfo() {
  return http.get<any, { result: RedisInfoItem[] }>('/sys/actuator/redis/info');
}

// ---- 业务事件流（SSE，GET /team/event/stream，权限 team:task:list；后端 2026-11-23 同步施工） ----

/** SSE 事件载荷（契约为准；字段同 TaskEvent 审计口径，操作人字段为 createBy） */
export interface StreamEvent {
  id: string;
  bizType: string;
  bizId: string;
  bizCode?: string;
  action: string;
  fromValue?: string;
  toValue?: string;
  createTime?: string | number;
  createBy?: string;
}

export interface EventStreamParams {
  /** 业务类型过滤，逗号多值（可选） */
  bizType?: string;
  /** 回溯条数（默认 50，后端口径） */
  tail?: number;
}

/**
 * 组装 SSE 地址。EventSource 无法设置请求头，token 走 query —— 后端 JwtFilter 已有
 * `request.getParameter("token")` 兜底（jeecg-boot-base-core JwtFilter.java），header 缺失时按 query 鉴权；
 * axios 场景仍走 X-Access-Token 头，双轨并存。
 */
export function buildEventStreamUrl(params: EventStreamParams = {}): string {
  const search = new URLSearchParams();
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) search.set('token', token);
  if (params.bizType) search.set('bizType', params.bizType);
  if (params.tail != null) search.set('tail', String(params.tail));
  const qs = search.toString();
  return `${API_BASE_URL}/team/event/stream${qs ? `?${qs}` : ''}`;
}
