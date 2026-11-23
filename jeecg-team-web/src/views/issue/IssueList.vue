<script setup lang="ts">
// 工作项列表（/issues）——对齐 GitHub /issues 的 IssueList：
// P0：服务端筛选/排序/分页（任务 kind 走 /team/task/list，QueryGenerator 口径）；
// P1（宿裁「P1 补聚合」）：kind tabs（全部/任务/缺陷/审批）接 GET /team/issue/list 聚合端点，
// 跨 kind 统一行渲染（#T-12 绿 / #B-3 橙 / OA 单号 紫），行内操作=认领/改派（team:task:edit）。
// open/closed 语义 = GitHub：open=todo|doing，closed=done。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { PhMagnifyingGlass, PhProhibit, PhTag, PhUserPlus, PhUsers, PhX } from '@phosphor-icons/vue';
import IssueListHeader from '@/components/IssueListHeader.vue';
import LabelManagerModal from '@/components/LabelManagerModal.vue';
import {
  listTasks, listIssues, fetchTaskBoard, changeTaskStatus, editTask,
  claimTask, reassignTask,
  type Task, type TaskBoard, type IssueRow, type IssueKind, type IssueLabel,
} from '@/api/team';
import { PRIORITY_CLASS, TASK_STATUSES, TASK_PRIORITIES, type TaskStatusKey } from '@/constants/task';
import { usePermission } from '@/utils/permission';
import { useAuthStore } from '@/stores/auth';
import { timeAgo } from '@/utils/time';

const router = useRouter();
const auth = useAuthStore();
const { hasPermission } = usePermission();
// 批量/行内操作细控：指派/认领/改派走编辑权限，关闭走状态机权限（与后端 @RequiresPermissions 对齐）
const canEdit = hasPermission('team:task:edit');
const canBatchAssign = canEdit;
const canBatchClose = hasPermission('team:task:status');

const myName = computed(() => auth.userInfo.realname || auth.userInfo.username || '');
/** 管理员判定：契约未给角色字段，先按平台超管账号 + 后端服务端校验双保险 */
const isAdmin = computed(() => (auth.userInfo.username || '') === 'admin');

// ---- kind tabs（P1 聚合）----
type KindFilter = 'all' | IssueKind;
const KIND_TABS: { key: KindFilter; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'task', label: '任务' },
  { key: 'bug', label: '缺陷' },
  { key: 'approval', label: '审批' },
];
const kindFilter = ref<KindFilter>('all');
const kindCounts = ref<Record<KindFilter, number>>({ all: 0, task: 0, bug: 0, approval: 0 });

// ---- 筛选/排序/分页状态（任务 kind 全量走服务端；聚合端点仅 keyword/assigneeId/state） ----
const scope = ref<'open' | 'closed'>('open');
const statusFilter = ref<'' | TaskStatusKey>('');
const assigneeFilter = ref('');
const priorityFilter = ref('');
const tagFilter = ref('');
const sortBy = ref<'created' | 'due' | 'priority'>('created');
const pageNo = ref(1);
const pageSize = ref(20);

const searchInput = ref('');
const searchQuery = ref('');
let searchTimer = 0;

/** 统一视图行：任务/缺陷/审批同构渲染 */
interface ViewRow {
  kind: IssueKind;
  code: string;
  bizId: string;
  title: string;
  state: 'open' | 'closed';
  statusText: string;
  assigneeId?: string;
  assigneeName?: string;
  createBy?: string;
  labels: IssueLabel[];
  priority?: string;
  blocked: boolean;
  createTime?: string;
  dueDate?: string;
}

const rows = ref<ViewRow[]>([]);
const total = ref(0);
const openCount = ref(0);
const closedCount = ref(0);
const loading = ref(false);
const errorMsg = ref('');

// ---- 候选（指派人与标签从看板数据取，一次拉取） ----
const assigneeOptions = ref<{ id: string; name: string }[]>([]);
const tagOptions = ref<string[]>([]);

async function loadCandidates() {
  try {
    const data = await fetchTaskBoard();
    const board: TaskBoard = data.result;
    const all = [...board.todo, ...board.doing, ...board.done];
    const seen = new Map<string, string>();
    const tags = new Set<string>();
    all.forEach((t) => {
      if (t.assigneeId) seen.set(t.assigneeId, t.assigneeName || t.assigneeId);
      if (t.tag) tags.add(t.tag);
    });
    assigneeOptions.value = [...seen.entries()].map(([id, name]) => ({ id, name }));
    tagOptions.value = [...tags];
  } catch {
    // 候选加载失败不阻塞列表（筛选下拉退化为空）
  }
}

// ---- 任务 kind 服务端参数组装 ----
type SortKey = 'created' | 'due' | 'priority';

const SORT_OPTIONS: { key: SortKey; label: string }[] = [
  { key: 'created', label: '创建时间' },
  { key: 'due', label: '到期日' },
  { key: 'priority', label: '优先级' },
];

function statusTitle(s?: string): string {
  return TASK_STATUSES.find((x) => x.key === s)?.title || s || '';
}

/** open/closed → status 参数（服务端只有等值/IN，open 用逗号 IN 合并 todo+doing） */
function scopeStatus(): string {
  return statusFilter.value || (scope.value === 'open' ? 'todo,doing' : 'done');
}

function buildParams(overrides: Record<string, unknown> = {}) {
  const params: Record<string, unknown> = {
    status: scopeStatus(),
    pageNo: pageNo.value,
    pageSize: pageSize.value,
    ...overrides,
  };
  if (assigneeFilter.value) params.assigneeId = assigneeFilter.value;
  if (priorityFilter.value) params.priority = priorityFilter.value;
  if (tagFilter.value) params.tag = tagFilter.value;
  const q = searchQuery.value.trim();
  const noMatch = /^#(\d+)$/.exec(q);
  if (noMatch) {
    params.taskNo = Number(noMatch[1]);
  } else if (q) {
    params.title = '*' + q + '*';
  }
  // 创建/到期走服务端排序；优先级是中文枚举，库内字典序无语义，客户端在页内重排
  if (sortBy.value === 'due') {
    params.column = 'due_date';
    params.order = 'asc';
  } else if (sortBy.value === 'created') {
    params.column = 'create_time';
    params.order = 'desc';
  }
  return params;
}

/** 聚合端点公共参数：keyword/assigneeId（status/priority/tag 为任务专属，聚合不支持） */
function aggregateBase(overrides: Record<string, unknown> = {}) {
  const params: Record<string, unknown> = { ...overrides };
  const q = searchQuery.value.trim();
  if (q) params.keyword = q;
  if (assigneeFilter.value) params.assigneeId = assigneeFilter.value;
  return params;
}

function taskToView(t: Task): ViewRow {
  return {
    kind: 'task',
    code: t.taskNo ? `#T-${t.taskNo}` : `#T-${t.id.slice(0, 7)}`,
    bizId: t.id,
    title: t.title,
    state: t.status === 'done' ? 'closed' : 'open',
    statusText: statusTitle(t.status),
    assigneeId: t.assigneeId,
    assigneeName: t.assigneeName,
    createBy: t.createBy,
    labels: t.labels?.length ? t.labels : t.tag ? [{ name: t.tag, color: '' }] : [],
    priority: t.priority,
    blocked: !!t.blocked,
    createTime: t.createTime,
    dueDate: t.dueDate,
  };
}

function issueToView(r: IssueRow): ViewRow {
  return {
    kind: r.kind,
    code: r.code,
    bizId: r.bizId,
    title: r.title,
    state: r.state,
    statusText: r.state === 'open' ? '开启' : '已关闭',
    assigneeName: r.assigneeName,
    labels: r.labels || [],
    blocked: !!r.blocked,
    createTime: r.createTime,
    dueDate: r.dueDate,
  };
}

const PRIORITY_WEIGHT: Record<string, number> = { 高: 0, 中: 1, 低: 2 };

const pageRows = computed<ViewRow[]>(() => {
  if (sortBy.value !== 'priority' || kindFilter.value !== 'task') return rows.value;
  return [...rows.value].sort((a, b) => {
    const diff = (PRIORITY_WEIGHT[a.priority || ''] ?? 3) - (PRIORITY_WEIGHT[b.priority || ''] ?? 3);
    if (diff !== 0) return diff;
    return (b.createTime || '').localeCompare(a.createTime || '');
  });
});

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const countBase = aggregateBase({ pageNo: 1, pageSize: 1 });
    // 计数：kind 四档（全部/任务/缺陷/审批，口径=scope+keyword+assignee）+ 当前 kind 的 open/closed
    const countReqs = (['all', 'task', 'bug', 'approval'] as const).map((k) =>
      listIssues({ ...countBase, kind: k }),
    );
    const openReq = listIssues({ ...countBase, kind: kindFilter.value, state: 'open' });
    const closedReq = listIssues({ ...countBase, kind: kindFilter.value, state: 'closed' });
    // 主列表：任务 kind 保留 P0 全量服务端筛选；其余走聚合端点
    const mainReq =
      kindFilter.value === 'task'
        ? listTasks(buildParams())
        : listIssues({
            ...aggregateBase(),
            kind: kindFilter.value,
            state: scope.value,
            pageNo: pageNo.value,
            pageSize: pageSize.value,
          });
    const settled = await Promise.allSettled([mainReq, ...countReqs, openReq, closedReq]);
    const [listRes, allRes, taskRes, bugRes, approvalRes, openRes, closedRes] = settled;
    if (listRes.status === 'fulfilled') {
      const result = listRes.value.result;
      rows.value =
        kindFilter.value === 'task'
          ? (result.records as Task[]).map(taskToView)
          : (result.records as IssueRow[]).map(issueToView);
      total.value = result.total;
    } else {
      rows.value = [];
      total.value = 0;
      errorMsg.value = listRes.reason?.message || '加载失败';
    }
    const countOf = (r: PromiseSettledResult<{ result: { records: unknown[]; total: number } }>) =>
      r.status === 'fulfilled' ? r.value.result.total : 0;
    kindCounts.value = {
      all: countOf(allRes),
      task: countOf(taskRes),
      bug: countOf(bugRes),
      approval: countOf(approvalRes),
    };
    openCount.value = countOf(openRes);
    closedCount.value = countOf(closedRes);
  } finally {
    loading.value = false;
  }
}

// 任一筛选/排序/分页口径变化 → 回第一页重查
watch(
  [scope, kindFilter, statusFilter, assigneeFilter, priorityFilter, tagFilter, sortBy, pageSize],
  () => {
    pageNo.value = 1;
    selected.value = new Set();
    reassignOpenId.value = '';
    load();
  },
);
watch(searchQuery, () => {
  pageNo.value = 1;
  selected.value = new Set();
  load();
});
watch(searchInput, (val) => {
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => {
    searchQuery.value = val;
  }, 300);
});
onMounted(() => {
  loadCandidates();
  load();
});
onBeforeUnmount(() => window.clearTimeout(searchTimer));

// ---- 行展示 ----
/** 到期日相对格式：今天 / 明天 / 逾期 N 天，其余绝对日期 */
function dueText(due?: string): { text: string; overdue: boolean } | null {
  if (!due) return null;
  const today = new Date();
  const pad = (n: number) => (n < 10 ? '0' + n : String(n));
  const todayStr = today.getFullYear() + '-' + pad(today.getMonth() + 1) + '-' + pad(today.getDate());
  if (due === todayStr) return { text: '今天到期', overdue: false };
  const todayTime = new Date(todayStr + 'T00:00:00').getTime();
  const dueTime = new Date(due + 'T00:00:00').getTime();
  const days = Math.round((dueTime - todayTime) / 86400000);
  if (days === 1) return { text: '明天到期', overdue: false };
  if (days < 1) return { text: `逾期 ${Math.abs(days)} 天`, overdue: true };
  return { text: due, overdue: false };
}

function openDetail(row: ViewRow) {
  if (row.kind === 'task') router.push(`/tools/taskboard/${row.bizId}`);
  else if (row.kind === 'bug') router.push('/tools/bug');
  else router.push('/tools/approval');
}

// ---- 行内操作：认领 / 改派（仅任务行；失败信封单个透出） ----
const reassignOpenId = ref('');
const reassignTargetId = ref('');
const rowActionBusy = ref(false);
const rowActionMsg = ref('');

function canClaim(row: ViewRow): boolean {
  return row.kind === 'task' && canEdit && row.assigneeName !== myName.value;
}

function canReassign(row: ViewRow): boolean {
  if (row.kind !== 'task' || !canEdit) return false;
  return !!row.createBy && (row.createBy === auth.userInfo.username || isAdmin.value);
}

async function claim(row: ViewRow) {
  if (rowActionBusy.value) return;
  rowActionBusy.value = true;
  rowActionMsg.value = '';
  try {
    const res = (await claimTask(row.bizId)) as { success?: boolean; message?: string };
    if (res && res.success === false) throw new Error(res.message || '认领失败');
    rowActionMsg.value = `已认领「${row.title}」`;
    await load();
  } catch (e: any) {
    rowActionMsg.value = `「${row.title}」认领失败：${e.message || '失败'}`;
  } finally {
    rowActionBusy.value = false;
  }
}

async function doReassign(row: ViewRow) {
  const target = assigneeOptions.value.find((o) => o.id === reassignTargetId.value);
  if (!target || rowActionBusy.value) return;
  rowActionBusy.value = true;
  rowActionMsg.value = '';
  try {
    const res = (await reassignTask({ id: row.bizId, assigneeId: target.id, assigneeName: target.name })) as {
      success?: boolean;
      message?: string;
    };
    if (res && res.success === false) throw new Error(res.message || '改派失败');
    reassignOpenId.value = '';
    reassignTargetId.value = '';
    rowActionMsg.value = `「${row.title}」已改派给 ${target.name}`;
    await load();
  } catch (e: any) {
    rowActionMsg.value = `「${row.title}」改派失败：${e.message || '失败'}`;
  } finally {
    rowActionBusy.value = false;
  }
}

// ---- 标签管理弹层 ----
const labelMgrVisible = ref(false);

// ---- 分页 ----
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));

const pageNumbers = computed<(number | '…')[]>(() => {
  const count = pageCount.value;
  const cur = pageNo.value;
  if (count <= 7) return Array.from({ length: count }, (_, i) => i + 1);
  const nums: (number | '…')[] = [1];
  const lo = Math.max(2, cur - 1);
  const hi = Math.min(count - 1, cur + 1);
  if (lo > 2) nums.push('…');
  for (let i = lo; i <= hi; i++) nums.push(i);
  if (hi < count - 1) nums.push('…');
  nums.push(count);
  return nums;
});

function gotoPage(p: number) {
  if (p < 1 || p > pageCount.value || p === pageNo.value) return;
  pageNo.value = p;
  selected.value = new Set();
  load();
}

// ---- 批量操作（勾选仅任务 kind 开放，缺陷/审批走各自页面操作） ----
const selected = ref<Set<string>>(new Set());
const batchBusy = ref(false);
const batchMsg = ref('');
const batchAssigneeId = ref('');

/** 勾选/批量入口仅任务 kind 显示 */
const showBatch = computed(
  () => kindFilter.value === 'task' && (canBatchAssign || canBatchClose),
);

const allChecked = computed(
  () => pageRows.value.length > 0 && pageRows.value.every((t) => selected.value.has(t.bizId)),
);

function toggleAll() {
  const next = new Set(selected.value);
  if (allChecked.value) {
    pageRows.value.forEach((t) => next.delete(t.bizId));
  } else {
    pageRows.value.forEach((t) => next.add(t.bizId));
  }
  selected.value = next;
}

function toggleOne(id: string) {
  const next = new Set(selected.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  selected.value = next;
}

function isBizError(res: unknown): res is { success: boolean; message?: string } {
  return typeof res === 'object' && res !== null && (res as { success?: boolean }).success === false;
}

/** 逐个执行，失败单个透出（标题 + 原因），成功项静默 */
async function batchRun(action: (t: ViewRow) => Promise<unknown>, okText: string) {
  if (batchBusy.value) return;
  const targets = pageRows.value.filter((t) => selected.value.has(t.bizId));
  if (!targets.length) return;
  batchBusy.value = true;
  batchMsg.value = '';
  const failures: string[] = [];
  const results = await Promise.allSettled(targets.map(action));
  results.forEach((r, i) => {
    if (r.status === 'rejected') {
      failures.push(`${targets[i].title}：${r.reason?.message || '失败'}`);
    } else if (isBizError(r.value)) {
      failures.push(`${targets[i].title}：${r.value.message || '失败'}`);
    }
  });
  const okCount = targets.length - failures.length;
  batchMsg.value = failures.length
    ? `${okText}成功 ${okCount} 项；失败 ${failures.length} 项 —— ` + failures.join('；')
    : `${okText}成功 ${okCount} 项`;
  selected.value = new Set();
  batchAssigneeId.value = '';
  await load();
  batchBusy.value = false;
}

function batchClose() {
  if (!window.confirm(`关闭选中的 ${selected.value.size} 个工作项？`)) return;
  return batchRun((t) => changeTaskStatus(t.bizId, 'done'), '关闭');
}

function batchAssign() {
  const target = assigneeOptions.value.find((o) => o.id === batchAssigneeId.value);
  if (!target) return;
  return batchRun(
    (t) => editTask({ id: t.bizId, assigneeId: target.id, assigneeName: target.name }),
    `指派给 ${target.name}`,
  );
}
</script>

<template>
  <div class="issue-page">
    <!-- 工具栏：搜索 + 筛选器 + 标签管理 -->
    <div class="toolbar">
      <div class="search-box">
        <PhMagnifyingGlass :size="15" class="search-box__icon" />
        <input
          v-model="searchInput"
          type="text"
          :placeholder="kindFilter === 'task' ? '搜索 #编号 或 标题…' : '搜索关键字…'"
          class="search-box__input"
        />
        <button v-if="searchInput" class="search-box__clear" title="清空" @click="searchInput = ''">
          <PhX :size="13" />
        </button>
      </div>
      <div class="toolbar__filters">
        <button v-if="canEdit" class="btn btn--sm btn-default" title="标签管理" @click="labelMgrVisible = true">
          <PhTag :size="13" /> 标签
        </button>
        <select v-model="assigneeFilter" class="select-sm" aria-label="指派人">
          <option value="">全部指派</option>
          <option v-for="o in assigneeOptions" :key="o.id" :value="o.id">{{ o.name }}</option>
        </select>
        <!-- 状态/优先级/标签筛选为任务专属（聚合端点不支持），切 kind 时隐藏 -->
        <template v-if="kindFilter === 'task'">
          <select v-model="statusFilter" class="select-sm" aria-label="状态">
            <option value="">全部状态</option>
            <option v-for="s in TASK_STATUSES" :key="s.key" :value="s.key">{{ s.title }}</option>
          </select>
          <select v-model="priorityFilter" class="select-sm" aria-label="优先级">
            <option value="">全部优先级</option>
            <option v-for="p in TASK_PRIORITIES" :key="p" :value="p">{{ p }}优先级</option>
          </select>
          <select v-model="tagFilter" class="select-sm" aria-label="标签">
            <option value="">全部标签</option>
            <option v-for="t in tagOptions" :key="t" :value="t">{{ t }}</option>
          </select>
        </template>
      </div>
    </div>

    <!-- kind tabs（P1 聚合：全部/任务/缺陷/审批，计数与 scope/搜索同口径） -->
    <nav class="kind-tabs" aria-label="工作项类型">
      <button
        v-for="k in KIND_TABS"
        :key="k.key"
        type="button"
        class="kind-tabs__tab"
        :class="{ 'kind-tabs__tab--active': kindFilter === k.key }"
        @click="kindFilter = k.key"
      >
        {{ k.label }}<span class="kind-tabs__count">{{ kindCounts[k.key] }}</span>
      </button>
    </nav>

    <div v-if="errorMsg" class="alert">
      {{ errorMsg }}
      <button class="alert__retry" @click="load">重试</button>
    </div>
    <div v-if="rowActionMsg" class="row-msg">{{ rowActionMsg }}</div>

    <!-- Open/Closed 头（对齐 GitHub list-header） -->
    <IssueListHeader v-model="scope" :open-count="openCount" :closed-count="closedCount">
      <template #filters>
        <span class="list-count">{{ total }} 个工作项</span>
        <select v-if="kindFilter === 'task'" v-model="sortBy" class="select-sm" aria-label="排序">
          <option v-for="o in SORT_OPTIONS" :key="o.key" :value="o.key">按{{ o.label }}</option>
        </select>
      </template>
    </IssueListHeader>

    <!-- 批量操作栏（勾选后出现，对齐 GitHub bulk-update 头） -->
    <div v-if="selected.size" class="batch-bar">
      <span class="batch-bar__info">已选 {{ selected.size }} 项</span>
      <template v-if="canBatchAssign">
        <select v-model="batchAssigneeId" class="select-sm" aria-label="批量指派">
          <option value="" disabled>选择指派人…</option>
          <option v-for="o in assigneeOptions" :key="o.id" :value="o.id">{{ o.name }}</option>
        </select>
        <button class="btn btn-primary btn--sm" :disabled="batchBusy || !batchAssigneeId" @click="batchAssign">
          批量指派
        </button>
      </template>
      <button v-if="canBatchClose" class="btn btn--sm btn--danger" :disabled="batchBusy" @click="batchClose">
        批量关闭
      </button>
      <button class="btn btn--sm btn-default" :disabled="batchBusy" @click="selected = new Set()">取消选择</button>
      <span v-if="batchMsg" class="batch-bar__msg">{{ batchMsg }}</span>
    </div>

    <!-- 列表（跨 kind 统一行） -->
    <ul class="issue-panel">
      <li v-if="loading" class="empty-state">加载中…</li>
      <template v-else>
        <li v-if="pageRows.length && showBatch" class="issue-row issue-row--head">
          <label class="issue-row__check">
            <input type="checkbox" :checked="allChecked" @change="toggleAll" />
          </label>
          <span class="issue-row__dot-spacer" />
          <span class="issue-row__head-note">全选本页</span>
        </li>
        <li v-for="row in pageRows" :key="row.kind + '-' + row.bizId" class="issue-row" @click="openDetail(row)">
          <label v-if="showBatch" class="issue-row__check" @click.stop>
            <input type="checkbox" :checked="selected.has(row.bizId)" @change="toggleOne(row.bizId)" />
          </label>
          <span
            class="state-dot"
            :class="row.state === 'closed' ? 'state-dot--closed' : 'state-dot--open'"
            :title="row.statusText"
          />
          <div class="issue-row__main">
            <div class="issue-row__title-line">
              <a class="issue-row__no" :class="'issue-row__no--' + row.kind">{{ row.code }}</a>
              <a class="issue-row__title" @click.stop="openDetail(row)">{{ row.title }}</a>
              <span v-if="row.blocked" class="blocked-badge" title="存在未完成的依赖">
                <PhProhibit :size="12" />阻塞
              </span>
              <span
                v-for="l in row.labels"
                :key="l.name"
                class="label-chip"
                :class="{ 'label-chip--plain': !l.color }"
                :style="l.color ? { background: l.color + '26', color: l.color, borderColor: l.color + '59' } : {}"
              >{{ l.name }}</span>
              <span v-if="row.priority" class="tag" :class="PRIORITY_CLASS[row.priority as keyof typeof PRIORITY_CLASS] || 'tag--neutral'">{{ row.priority }}优先级</span>
            </div>
            <div class="issue-row__meta">
              <span>{{ row.statusText }}</span>
              <span v-if="row.assigneeName">· {{ row.assigneeName }}</span>
              <span v-if="dueText(row.dueDate)" :class="{ 'issue-row__overdue': dueText(row.dueDate)!.overdue }">
                · {{ dueText(row.dueDate)!.text }}
              </span>
              <span>· opened {{ timeAgo(row.createTime) || '—' }}</span>
            </div>
          </div>
          <div class="issue-row__right">
            <!-- 行内操作：认领（本人未指派）/ 改派（创建人/管理员） -->
            <div v-if="canClaim(row) || canReassign(row)" class="issue-row__ops" @click.stop>
              <button
                v-if="canClaim(row)"
                class="row-op"
                :disabled="rowActionBusy"
                title="认领此任务"
                @click="claim(row)"
              >
                <PhUserPlus :size="13" />认领
              </button>
              <template v-if="canReassign(row)">
                <button
                  v-if="reassignOpenId !== row.bizId"
                  class="row-op"
                  title="改派给他人"
                  @click="reassignOpenId = row.bizId"
                >
                  <PhUsers :size="13" />改派
                </button>
                <template v-else>
                  <select v-model="reassignTargetId" class="select-sm" aria-label="改派目标">
                    <option value="" disabled>改派给…</option>
                    <option v-for="o in assigneeOptions" :key="o.id" :value="o.id">{{ o.name }}</option>
                  </select>
                  <button
                    class="row-op"
                    :disabled="rowActionBusy || !reassignTargetId"
                    @click="doReassign(row)"
                  >确定</button>
                  <button class="row-op" @click="reassignOpenId = ''">取消</button>
                </template>
              </template>
            </div>
            <span v-if="row.dueDate" class="issue-row__due" :class="{ 'issue-row__overdue': dueText(row.dueDate)!.overdue }">
              {{ dueText(row.dueDate)!.text }}
            </span>
            <span v-if="row.assigneeName" class="avatar avatar--sm">{{ row.assigneeName.charAt(0) }}</span>
          </div>
        </li>
        <li v-if="!pageRows.length" class="empty-state">没有匹配的工作项</li>
      </template>
    </ul>

    <!-- 分页器 -->
    <div class="pager">
      <span class="pager__total">共 {{ total }} 条</span>
      <div class="pager__pages">
        <button class="pager__btn" :disabled="pageNo <= 1 || loading" @click="gotoPage(pageNo - 1)">‹ 上一页</button>
        <template v-for="(p, i) in pageNumbers" :key="i">
          <span v-if="p === '…'" class="pager__ellipsis">…</span>
          <button
            v-else
            class="pager__btn"
            :class="{ 'pager__btn--active': p === pageNo }"
            :disabled="loading"
            @click="gotoPage(p)"
          >{{ p }}</button>
        </template>
        <button class="pager__btn" :disabled="pageNo >= pageCount || loading" @click="gotoPage(pageNo + 1)">下一页 ›</button>
      </div>
      <select v-model.number="pageSize" class="select-sm" aria-label="每页条数">
        <option :value="10">10 条/页</option>
        <option :value="20">20 条/页</option>
        <option :value="50">50 条/页</option>
      </select>
    </div>

    <!-- 标签管理弹层（team_label CRUD） -->
    <LabelManagerModal v-model="labelMgrVisible" />
  </div>
</template>

<style scoped>
.issue-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

/* ---------- 工具栏 ---------- */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}
.toolbar__filters {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.search-box {
  display: flex;
  align-items: center;
  height: 32px;
  padding: 0 10px;
  gap: 6px;
  background: var(--bg-card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
}
.search-box:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 12%, transparent);
}
.search-box__icon { color: var(--text-muted); flex-shrink: 0; }
.search-box__input {
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--font-size-md);
  color: var(--text-main);
  width: 200px;
  font-family: inherit;
}
.search-box__input::placeholder { color: var(--text-placeholder); }
.search-box__clear {
  display: inline-flex;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  padding: 2px;
  border-radius: var(--radius-xs);
}
.search-box__clear:hover { color: var(--text-main); background: var(--gray-100); }

.select-sm {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  font-size: var(--font-size-md);
  color: var(--text-regular);
  outline: none;
  font-family: inherit;
  cursor: pointer;
}
.select-sm:hover { border-color: var(--gray-400); }
.select-sm:focus { border-color: var(--primary); }

.alert {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
  color: var(--danger-700);
  background: var(--danger-50);
  border: 1px solid var(--danger-border);
}
.alert__retry {
  border: 1px solid var(--danger-border);
  background: transparent;
  color: var(--danger-700);
  border-radius: var(--radius-sm);
  padding: 2px 10px;
  font-size: var(--font-size-md);
  cursor: pointer;
}

/* 行内操作结果（认领/改派单条透出） */
.row-msg {
  padding: 6px 14px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
  color: var(--text-regular);
  background: var(--bg-muted);
  border: 1px solid var(--border-color);
}

/* ---------- kind tabs ---------- */
.kind-tabs {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}
.kind-tabs__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--border-strong);
  border-radius: 999px;
  background: var(--bg-card);
  color: var(--text-regular);
  font-size: var(--font-size-md);
  cursor: pointer;
  font-family: inherit;
}
.kind-tabs__tab:hover { border-color: var(--primary); color: var(--primary); }
.kind-tabs__tab--active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}
.kind-tabs__count { font-weight: 600; opacity: 0.85; }

/* ---------- 批量操作栏 ---------- */
.batch-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--primary-50);
  border: 1px solid var(--primary-border);
  border-radius: var(--radius-md);
  font-size: var(--font-size-md);
}
.batch-bar__info { font-weight: 600; color: var(--text-main); }
.batch-bar__msg { color: var(--warning-700); }

/* ---------- 列表 ---------- */
.issue-panel {
  list-style: none;
  margin: 0;
  padding: 0;
  background: var(--bg-body);
  border: 1px solid var(--border-color);
  border-top: none;
  border-radius: 0 0 var(--radius-md) var(--radius-md);
  overflow: hidden;
}
.issue-panel .empty-state {
  padding: var(--spacing-3xl) 0;
}

.issue-row {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  cursor: pointer;
  font-size: var(--font-size-base);
}
.issue-row:hover { background: var(--bg-subtle); }
.issue-row:last-child { border-bottom: none; }

.issue-row--head {
  padding-top: var(--spacing-sm);
  padding-bottom: var(--spacing-sm);
  background: var(--bg-card);
  cursor: default;
}
.issue-row--head:hover { background: var(--bg-card); }
.issue-row__head-note { font-size: var(--font-size-sm); color: var(--text-muted); }

.issue-row__check {
  flex-shrink: 0;
  margin-top: 3px;
  display: inline-flex;
  cursor: pointer;
}
.issue-row__dot-spacer { width: 16px; flex-shrink: 0; }

/* 状态点：open=绿（GitHub open），closed=紫灰（GitHub closed） */
.state-dot {
  flex-shrink: 0;
  width: 12px;
  height: 12px;
  margin-top: 5px;
  border-radius: 50%;
}
.state-dot--open { background: var(--success); }
.state-dot--closed { background: var(--closed); opacity: 0.75; }

.issue-row__main {
  flex: 1;
  min-width: 0;
}
.issue-row__title-line {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}
/* 编号前缀色：T=绿 / B=橙 / OA=紫（工具组语义色） */
.issue-row__no {
  font-size: var(--font-size-md);
  color: var(--text-muted);
  flex-shrink: 0;
}
.issue-row__no--task { color: var(--success); font-weight: 600; }
.issue-row__no--bug { color: var(--warning); font-weight: 600; }
.issue-row__no--approval { color: var(--closed); font-weight: 600; }

.issue-row__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-main);
}
.issue-row__title:hover { color: var(--primary); }

/* 彩色标签 chips（有 color 走行内色，无 color 退化为中性 chip） */
.label-chip {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border: 1px solid;
  border-radius: 999px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 18px;
}
.label-chip--plain {
  background: var(--gray-100);
  color: var(--text-regular);
  border-color: var(--border-strong);
}

/* 阻塞徽章：依赖未完成（工具组语义色，不加外部图标资源） */
.blocked-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: var(--font-size-sm);
  font-weight: 600;
  line-height: 18px;
  color: var(--danger-700);
  background: var(--danger-50);
  border: 1px solid var(--danger-border);
}

.issue-row__meta {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.issue-row__right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: 2px;
}
.issue-row__due {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  white-space: nowrap;
}
.issue-row__overdue { color: var(--danger); font-weight: 600; }

/* 行内操作（hover 显现，GitHub 口径） */
.issue-row__ops {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.issue-row:hover .issue-row__ops { opacity: 1; }
.row-op {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-regular);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: inherit;
  white-space: nowrap;
}
.row-op:hover:not(:disabled) { border-color: var(--primary); color: var(--primary); }
.row-op:disabled { opacity: 0.5; cursor: not-allowed; }

.list-count {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

/* 危险按钮（ApprovalCenter 同款为局部样式，此处自带对齐） */
.btn--danger {
  border-color: var(--danger-border);
  color: var(--danger);
}
.btn--danger:hover:not(:disabled) {
  border-color: var(--danger);
  color: #fff;
  background: var(--danger);
}

/* ---------- 分页器 ---------- */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}
.pager__total {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.pager__pages {
  display: flex;
  align-items: center;
  gap: 4px;
}
.pager__btn {
  min-width: 28px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-regular);
  font-size: var(--font-size-md);
  cursor: pointer;
  font-family: inherit;
}
.pager__btn:hover:not(:disabled):not(.pager__btn--active) {
  border-color: var(--primary);
  color: var(--primary);
}
.pager__btn:disabled { opacity: 0.45; cursor: not-allowed; }
.pager__btn--active {
  background: var(--primary);
  border-color: var(--primary);
  color: #fff;
}
.pager__ellipsis { color: var(--text-muted); padding: 0 2px; }

@media (max-width: 900px) {
  .toolbar__filters { flex-wrap: wrap; }
  /* 窄屏行内操作常显，避免 hover 不可用 */
  .issue-row__ops { opacity: 1; }
}
</style>
