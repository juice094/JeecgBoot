<script setup lang="ts">
// 工作项列表（/issues）——对齐 GitHub /issues 的 IssueList：
// 服务端筛选/排序/分页（GET /team/task/list，QueryGenerator 口径，后端零改动），
// open/closed 语义 = GitHub：open=todo|doing，closed=done。
// P0 范围仅任务工作项；缺陷/审批各有专属页面（/tools/bug、/tools/approval）。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { PhMagnifyingGlass, PhX } from '@phosphor-icons/vue';
import IssueListHeader from '@/components/IssueListHeader.vue';
import {
  listTasks, fetchTaskBoard, changeTaskStatus, editTask,
  type Task, type TaskBoard,
} from '@/api/team';
import { TAG_CLASS, PRIORITY_CLASS, TASK_STATUSES, TASK_PRIORITIES, type TaskStatusKey } from '@/constants/task';
import { usePermission } from '@/utils/permission';
import { timeAgo } from '@/utils/time';

const router = useRouter();
const { hasPermission } = usePermission();
// 批量操作细控：指派走编辑权限，关闭走状态机权限（与后端 @RequiresPermissions 对齐）
const canBatchAssign = hasPermission('team:task:edit');
const canBatchClose = hasPermission('team:task:status');

// ---- 筛选/排序/分页状态（全部驱动服务端） ----
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

const rows = ref<Task[]>([]);
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

// ---- 服务端参数组装 ----
type SortKey = 'created' | 'due' | 'priority';

const SORT_OPTIONS: { key: SortKey; label: string }[] = [
  { key: 'created', label: '创建时间' },
  { key: 'due', label: '到期日' },
  { key: 'priority', label: '优先级' },
];

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

const PRIORITY_WEIGHT: Record<string, number> = { 高: 0, 中: 1, 低: 2 };

const pageRows = computed<Task[]>(() => {
  if (sortBy.value !== 'priority') return rows.value;
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
    // 主列表 + Open/Closed 计数（计数同筛选口径，pageSize=1 只取 total；优先级排序不参与计数查询）
    const [listRes, openRes, closedRes] = await Promise.allSettled([
      listTasks(buildParams()),
      listTasks(buildParams({ status: 'todo,doing', pageNo: 1, pageSize: 1, column: undefined, order: undefined })),
      listTasks(buildParams({ status: 'done', pageNo: 1, pageSize: 1, column: undefined, order: undefined })),
    ]);
    if (listRes.status === 'fulfilled') {
      rows.value = listRes.value.result.records;
      total.value = listRes.value.result.total;
    } else {
      rows.value = [];
      total.value = 0;
      errorMsg.value = listRes.reason?.message || '加载失败';
    }
    openCount.value = openRes.status === 'fulfilled' ? openRes.value.result.total : 0;
    closedCount.value = closedRes.status === 'fulfilled' ? closedRes.value.result.total : 0;
  } finally {
    loading.value = false;
  }
}

// 任一筛选/排序/分页口径变化 → 回第一页重查
watch(
  [scope, statusFilter, assigneeFilter, priorityFilter, tagFilter, sortBy, pageSize],
  () => {
    pageNo.value = 1;
    selected.value = new Set();
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
function taskNoLabel(t: Task): string {
  return t.taskNo ? '#' + t.taskNo : '#' + t.id.slice(0, 7);
}

function statusTitle(s?: string): string {
  return TASK_STATUSES.find((x) => x.key === s)?.title || s || '';
}

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

function openDetail(t: Task) {
  router.push(`/tools/taskboard/${t.id}`);
}

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

// ---- 批量操作 ----
const selected = ref<Set<string>>(new Set());
const batchBusy = ref(false);
const batchMsg = ref('');
const batchAssigneeId = ref('');

const allChecked = computed(
  () => pageRows.value.length > 0 && pageRows.value.every((t) => selected.value.has(t.id)),
);

function toggleAll() {
  const next = new Set(selected.value);
  if (allChecked.value) {
    pageRows.value.forEach((t) => next.delete(t.id));
  } else {
    pageRows.value.forEach((t) => next.add(t.id));
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
async function batchRun(action: (t: Task) => Promise<unknown>, okText: string) {
  if (batchBusy.value) return;
  const targets = pageRows.value.filter((t) => selected.value.has(t.id));
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
  return batchRun((t) => changeTaskStatus(t.id, 'done'), '关闭');
}

function batchAssign() {
  const target = assigneeOptions.value.find((o) => o.id === batchAssigneeId.value);
  if (!target) return;
  return batchRun(
    (t) => editTask({ id: t.id, assigneeId: target.id, assigneeName: target.name }),
    `指派给 ${target.name}`,
  );
}
</script>

<template>
  <div class="issue-page">
    <!-- 工具栏：搜索 + 筛选器 -->
    <div class="toolbar">
      <div class="search-box">
        <PhMagnifyingGlass :size="15" class="search-box__icon" />
        <input
          v-model="searchInput"
          type="text"
          placeholder="搜索 #编号 或 标题…"
          class="search-box__input"
        />
        <button v-if="searchInput" class="search-box__clear" title="清空" @click="searchInput = ''">
          <PhX :size="13" />
        </button>
      </div>
      <div class="toolbar__filters">
        <select v-model="statusFilter" class="select-sm" aria-label="状态">
          <option value="">全部状态</option>
          <option v-for="s in TASK_STATUSES" :key="s.key" :value="s.key">{{ s.title }}</option>
        </select>
        <select v-model="assigneeFilter" class="select-sm" aria-label="指派人">
          <option value="">全部指派</option>
          <option v-for="o in assigneeOptions" :key="o.id" :value="o.id">{{ o.name }}</option>
        </select>
        <select v-model="priorityFilter" class="select-sm" aria-label="优先级">
          <option value="">全部优先级</option>
          <option v-for="p in TASK_PRIORITIES" :key="p" :value="p">{{ p }}优先级</option>
        </select>
        <select v-model="tagFilter" class="select-sm" aria-label="标签">
          <option value="">全部标签</option>
          <option v-for="t in tagOptions" :key="t" :value="t">{{ t }}</option>
        </select>
      </div>
    </div>

    <div v-if="errorMsg" class="alert">
      {{ errorMsg }}
      <button class="alert__retry" @click="load">重试</button>
    </div>

    <!-- Open/Closed 头（对齐 GitHub list-header） -->
    <IssueListHeader v-model="scope" :open-count="openCount" :closed-count="closedCount">
      <template #filters>
        <span class="list-count">{{ total }} 个工作项</span>
        <select v-model="sortBy" class="select-sm" aria-label="排序">
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

    <!-- 列表 -->
    <ul class="issue-panel">
      <li v-if="loading" class="empty-state">加载中…</li>
      <template v-else>
        <li v-if="pageRows.length && (canBatchAssign || canBatchClose)" class="issue-row issue-row--head">
          <label class="issue-row__check">
            <input type="checkbox" :checked="allChecked" @change="toggleAll" />
          </label>
          <span class="issue-row__dot-spacer" />
          <span class="issue-row__head-note">全选本页</span>
        </li>
        <li v-for="t in pageRows" :key="t.id" class="issue-row" @click="openDetail(t)">
          <label v-if="canBatchAssign || canBatchClose" class="issue-row__check" @click.stop>
            <input type="checkbox" :checked="selected.has(t.id)" @change="toggleOne(t.id)" />
          </label>
          <span
            class="state-dot"
            :class="t.status === 'done' ? 'state-dot--closed' : 'state-dot--open'"
            :title="statusTitle(t.status)"
          />
          <div class="issue-row__main">
            <div class="issue-row__title-line">
              <a class="issue-row__no">{{ taskNoLabel(t) }}</a>
              <a class="issue-row__title" @click.stop="openDetail(t)">{{ t.title }}</a>
              <span v-if="t.tag" class="tag" :class="TAG_CLASS[t.tag as keyof typeof TAG_CLASS] || 'tag--neutral'">{{ t.tag }}</span>
              <span v-if="t.priority" class="tag" :class="PRIORITY_CLASS[t.priority as keyof typeof PRIORITY_CLASS] || 'tag--neutral'">{{ t.priority }}优先级</span>
            </div>
            <div class="issue-row__meta">
              <span>{{ statusTitle(t.status) }}</span>
              <span v-if="t.assigneeName">· {{ t.assigneeName }}</span>
              <span v-if="dueText(t.dueDate)" :class="{ 'issue-row__overdue': dueText(t.dueDate)!.overdue }">
                · {{ dueText(t.dueDate)!.text }}
              </span>
              <span>· opened {{ timeAgo(t.createTime) || '—' }}</span>
            </div>
          </div>
          <div class="issue-row__right">
            <span v-if="t.dueDate" class="issue-row__due" :class="{ 'issue-row__overdue': dueText(t.dueDate)!.overdue }">
              {{ dueText(t.dueDate)!.text }}
            </span>
            <span v-if="t.assigneeName" class="avatar avatar--sm">{{ t.assigneeName.charAt(0) }}</span>
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
.issue-row__no {
  font-size: var(--font-size-md);
  color: var(--text-muted);
  flex-shrink: 0;
}
.issue-row__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-main);
}
.issue-row__title:hover { color: var(--primary); }

.issue-row__meta {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.issue-row__right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  margin-top: 2px;
}
.issue-row__due {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  white-space: nowrap;
}
.issue-row__overdue { color: var(--danger); font-weight: 600; }

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
}
</style>
