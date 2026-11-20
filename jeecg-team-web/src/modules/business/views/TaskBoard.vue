<script setup lang="ts">
// 任务看板（工具组）—— owner: juice094
// 数据：GET /team/task/board 三列一次拉取；拖拽/按钮流转走 PUT /team/task/status（服务端状态机）
import { onMounted, reactive, ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { PhMagnifyingGlass, PhPlus, PhArrowRight, PhTrash, PhX } from '@phosphor-icons/vue';
import { fetchTaskBoard, addTask, editTask, changeTaskStatus, deleteTask, type Task } from '@/api/team';
import { renderMarkdown } from '@/utils/markdown';
import { timeAgo } from '@/utils/time';
import IssueListHeader from '@/components/IssueListHeader.vue';
import IssueItem from '@/components/IssueItem.vue';
import { fromTask } from '@/constants/issue';
import {
  TASK_STATUSES, TASK_TAGS, TASK_PRIORITIES, TAG_CLASS, PRIORITY_CLASS,
  type TaskStatusKey, type TaskTag, type TaskPriority,
} from '@/constants/task';

const router = useRouter();
const board = reactive<Record<TaskStatusKey, Task[]>>({ todo: [], doing: [], done: [] });
const loading = ref(false);
const errorMsg = ref('');
const busyId = ref('');
const dragId = ref('');

// 视图模式与筛选条件（默认列表：对标 GitHub Issues，看板为辅助视图）
const viewMode = ref<'board' | 'list'>('list');
const searchQuery = ref('');
const filterTag = ref<string>('all');
const filterPriority = ref<string>('all');

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await fetchTaskBoard();
    board.todo = data.result.todo;
    board.doing = data.result.doing;
    board.done = data.result.done;
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}
onMounted(load);

// 数据源计算与过滤
const allTasks = computed(() => [...board.todo, ...board.doing, ...board.done]);
const filterFn = (t: Task) => {
  const q = searchQuery.value.toLowerCase();
  const matchSearch = !q || t.title.toLowerCase().includes(q) || (t.description || '').toLowerCase().includes(q);
  const matchTag = filterTag.value === 'all' || t.tag === filterTag.value;
  const matchPriority = filterPriority.value === 'all' || t.priority === filterPriority.value;
  return matchSearch && matchTag && matchPriority;
};

const filteredBoard = computed(() => ({
  todo: board.todo.filter(filterFn),
  doing: board.doing.filter(filterFn),
  done: board.done.filter(filterFn),
}));

// ---- 列表视图：Open/Closed 口径对齐 GitHub（Open = 未完成，Closed = 已完成） ----
const listScope = ref<'open' | 'closed'>('open');
const baseFiltered = computed(() => allTasks.value.filter(filterFn));
const openCount = computed(() => baseFiltered.value.filter((t) => t.status !== 'done').length);
const closedCount = computed(() => baseFiltered.value.filter((t) => t.status === 'done').length);

const filteredList = computed(() => {
  const order: Record<TaskStatusKey, number> = { todo: 0, doing: 1, done: 2 };
  return baseFiltered.value
    .filter((t) => (listScope.value === 'open' ? t.status !== 'done' : t.status === 'done'))
    .sort((a, b) => order[a.status] - order[b.status]);
});

// ---- 新增 / 编辑 ----
const editorOpen = ref(false);
const editing = ref<Task | null>(null);
const form = reactive({
  title: '', description: '', priority: '中' as TaskPriority,
  tag: '开发' as TaskTag, assigneeName: '', dueDate: '', sortNo: 0,
});
const titleError = ref(false);

// ---- 描述编辑器：编写 / 预览 双页签 ----
const composerTab = ref<'write' | 'preview'>('write');
const previewHtml = computed(() => renderMarkdown(form.description));

/** 业务板块骨架：与 Issue 正文的四段结构一致，把自由文本收敛成可校验的模板 */
const TASK_TEMPLATE = [
  '## 目标',
  '',
  '## 任务',
  '- [ ] ',
  '',
  '## 约束',
  '- ',
  '',
  '## 验收',
  '',
].join('\n');

function insertTemplate() {
  const existing = form.description.trimEnd();
  form.description = existing ? existing + '\n\n' + TASK_TEMPLATE : TASK_TEMPLATE;
  composerTab.value = 'write';
}

function openAdd() {
  editing.value = null;
  form.title = ''; form.description = ''; form.priority = '中'; form.tag = '开发';
  form.assigneeName = ''; form.dueDate = ''; form.sortNo = 0;
  titleError.value = false;
  composerTab.value = 'write';
  editorOpen.value = true;
}

function openEdit(task: Task) {
  editing.value = task;
  form.title = task.title;
  form.description = task.description || '';
  form.priority = (task.priority as TaskPriority) || '中';
  form.tag = (task.tag as TaskTag) || '开发';
  form.assigneeName = task.assigneeName || '';
  form.dueDate = task.dueDate || '';
  form.sortNo = task.sortNo ?? 0;
  titleError.value = false;
  composerTab.value = 'write';
  editorOpen.value = true;
}

async function save() {
  titleError.value = !form.title.trim();
  if (titleError.value) return;
  const body = {
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    priority: form.priority,
    tag: form.tag,
    assigneeName: form.assigneeName.trim() || undefined,
    dueDate: form.dueDate || undefined,
    sortNo: form.sortNo,
  };
  busyId.value = editing.value?.id || 'new';
  try {
    if (editing.value) await editTask({ id: editing.value.id, ...body });
    else await addTask(body);
    editorOpen.value = false;
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '保存失败';
  } finally {
    busyId.value = '';
  }
}

// ---- 状态流转（拖拽或按钮） ----
async function transit(task: Task, target: TaskStatusKey) {
  if (task.status === target) return;
  busyId.value = task.id;
  errorMsg.value = '';
  try {
    await changeTaskStatus(task.id, target);
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '流转失败';
  } finally {
    busyId.value = '';
  }
}

function onDrop(target: TaskStatusKey) {
  const task = allTasks.value.find((t) => t.id === dragId.value);
  dragId.value = '';
  if (task) transit(task, target);
}

// ---- 删除 ----
async function remove(task: Task) {
  if (!window.confirm(`删除任务「${task.title}」？（逻辑删除）`)) return;
  busyId.value = task.id;
  errorMsg.value = '';
  try {
    await deleteTask(task.id);
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败';
  } finally {
    busyId.value = '';
  }
}

// ---- 工具函数 ----
function isOverdue(task: Task) {
  if (!task.dueDate || task.status === 'done') return false;
  const today = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  const todayStr = `${today.getFullYear()}-${pad(today.getMonth() + 1)}-${pad(today.getDate())}`;
  return task.dueDate < todayStr;
}

function statusSymbol(status: TaskStatusKey) {
  return status === 'done' ? '✓' : status === 'doing' ? '◐' : '○';
}

function shortId(id: string) {
  return id ? id.slice(0, 7) : '';
}

/** 序号显示：优先 taskNo，回退 UUID 前 7 位 */
function taskLabel(task: Task) {
  return task.taskNo ? '#' + task.taskNo : '#' + shortId(task.id);
}

/** 列表行元信息：#序号 · 创建人 opened x ago（GitHub 口径） */
function rowMeta(task: Task): string {
  return [taskLabel(task), task.createBy || '未署名', 'opened ' + (timeAgo(task.createTime) || '—')].join(' · ');
}

/** 列表视图行数据：任务 + 归一化视图模型（状态/标签），一次映射 */
const listRows = computed(() => filteredList.value.map((t) => ({ task: t, view: fromTask(t) })));

/** 列表行点击进入详情页；看板卡片仍走 openEdit 弹窗 */
function openDetail(task: Task) {
  router.push('/tools/taskboard/' + task.id);
}
</script>

<template>
  <div class="task-page">
    <!-- 页面标题行 -->
    <div class="page-head">
      <h1 class="page-head__title">任务</h1>
      <div class="page-head__actions">
        <div class="segmented">
          <button :class="{ active: viewMode === 'board' }" @click="viewMode = 'board'">看板</button>
          <button :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'">列表</button>
        </div>
        <button class="btn btn-primary btn--sm" @click="openAdd">
          <PhPlus :size="14" /> 新建任务
        </button>
      </div>
    </div>

    <!-- 搜索（属性筛选在列表头 filters 插槽内，看板视图下隐藏） -->
    <div class="filter-row">
      <div class="search-box search-box--wide">
        <PhMagnifyingGlass :size="15" class="search-box__icon" />
        <input v-model="searchQuery" type="text" placeholder="搜索任务标题与描述…" class="search-box__input" />
      </div>
    </div>

    <div v-if="errorMsg" class="alert alert--danger">{{ errorMsg }}</div>

    <div v-if="loading" class="empty-state">加载中…</div>

    <!-- 看板视图 -->
    <div v-else-if="viewMode === 'board'" class="board">
      <section
        v-for="col in TASK_STATUSES"
        :key="col.key"
        class="board-col"
        @dragover.prevent
        @drop="onDrop(col.key)"
      >
        <header class="board-col__header">
          <span class="board-col__title">
            <span class="status-symbol" :class="`status-symbol--${col.key}`">{{ statusSymbol(col.key) }}</span>
            {{ col.title }}
          </span>
          <span class="counter">{{ filteredBoard[col.key].length }}</span>
        </header>

        <article
          v-for="task in filteredBoard[col.key]"
          :key="task.id"
          class="task-card"
          :class="{ 'task-card--busy': busyId === task.id, 'task-card--done': task.status === 'done' }"
          draggable="true"
          @dragstart="dragId = task.id"
          @click="openEdit(task)"
        >
          <h4 class="task-card__title">
            <span class="status-symbol" :class="`status-symbol--${task.status}`">{{ statusSymbol(task.status) }}</span>
            <span class="task-card__title-text">{{ task.title }}</span>
          </h4>
          <div v-if="task.tag || task.priority" class="task-card__labels">
            <span v-if="task.tag" class="tag" :class="TAG_CLASS[task.tag as TaskTag]">{{ task.tag }}</span>
            <span v-if="task.priority" class="tag" :class="PRIORITY_CLASS[task.priority as TaskPriority]">{{ task.priority }}</span>
          </div>
          <footer class="task-card__footer">
            <span class="assignee">
              <span class="avatar avatar--sm">{{ (task.assigneeName || '?').charAt(0) }}</span>
              <span>{{ task.assigneeName || '未指派' }}</span>
            </span>
            <span v-if="task.dueDate" class="due" :class="{ 'due--overdue': isOverdue(task) }">
              {{ task.dueDate }}
            </span>
          </footer>
          <div class="task-card__actions" @click.stop>
            <button
              v-for="s in TASK_STATUSES.filter((s) => s.key !== task.status)"
              :key="s.key"
              class="link-btn"
              :disabled="busyId === task.id"
              @click="transit(task, s.key)"
            >
              <PhArrowRight :size="11" /> {{ s.title }}
            </button>
            <button class="link-btn link-btn--danger" :disabled="busyId === task.id" @click="remove(task)">
              <PhTrash :size="11" /> 删除
            </button>
          </div>
        </article>

        <div v-if="!filteredBoard[col.key].length" class="board-col__empty">暂无任务</div>
      </section>
    </div>

    <!-- 列表视图（对标 GitHub Issues 列表：Open/Closed 头 + 行 Grid） -->
    <template v-else>
      <IssueListHeader v-model="listScope" :open-count="openCount" :closed-count="closedCount">
        <template #filters>
          <select v-model="filterTag" class="select-sm">
            <option value="all">所有标签</option>
            <option v-for="t in TASK_TAGS" :key="t" :value="t">{{ t }}</option>
          </select>
          <select v-model="filterPriority" class="select-sm">
            <option value="all">所有优先级</option>
            <option v-for="p in TASK_PRIORITIES" :key="p" :value="p">{{ p }}</option>
          </select>
        </template>
      </IssueListHeader>

      <ul class="issue-panel">
        <IssueItem
          v-for="row in listRows"
          :key="row.task.id"
          :state="row.view.state"
          :title="row.task.title"
          :labels="row.view.labels"
          :meta="rowMeta(row.task)"
          :assignee-name="row.task.assigneeName"
          @open="openDetail(row.task)"
        >
          <template #actions>
            <button class="icon-btn" title="删除" @click.stop="remove(row.task)"><PhTrash :size="14" /></button>
          </template>
        </IssueItem>
        <li v-if="!listRows.length" class="empty-state">没有匹配的任务</li>
      </ul>
    </template>

    <!-- 编辑 / 新建弹窗 -->
    <div v-if="editorOpen" class="modal-overlay" @click.self="editorOpen = false">
      <div class="modal" role="dialog" aria-modal="true">
        <header class="modal__header">
          <h3 class="modal__title">{{ editing ? '编辑任务' : '新建任务' }}</h3>
          <button class="icon-btn" title="关闭" @click="editorOpen = false"><PhX :size="16" /></button>
        </header>
        <div class="modal__body">
          <div class="form-group">
            <label class="form-label">任务标题 <span class="req">*</span></label>
            <input v-model="form.title" type="text" class="form-control" placeholder="一句话描述这个任务" />
            <div v-if="titleError" class="field-error">请填写任务标题。</div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">标签</label>
              <select v-model="form.tag" class="form-control">
                <option v-for="t in TASK_TAGS" :key="t" :value="t">{{ t }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">优先级</label>
              <select v-model="form.priority" class="form-control">
                <option v-for="p in TASK_PRIORITIES" :key="p" :value="p">{{ p }}</option>
              </select>
            </div>
          </div>
          <div class="form-row">
            <div class="form-group">
              <label class="form-label">负责人</label>
              <input v-model="form.assigneeName" type="text" class="form-control" placeholder="姓名" />
            </div>
            <div class="form-group">
              <label class="form-label">截止日期</label>
              <input v-model="form.dueDate" type="date" class="form-control" />
            </div>
          </div>
          <div class="form-group" style="margin-bottom: 0">
            <div class="composer__head">
              <label class="form-label" style="margin-bottom: 0">任务描述</label>
              <div class="composer__tabs">
                <button type="button" class="composer__tab" :class="{ active: composerTab === 'write' }" @click="composerTab = 'write'">编写</button>
                <button type="button" class="composer__tab" :class="{ active: composerTab === 'preview' }" @click="composerTab = 'preview'">预览</button>
                <button type="button" class="composer__tab composer__tab--action" @click="insertTemplate">插入模板</button>
              </div>
            </div>
            <textarea
              v-if="composerTab === 'write'"
              v-model="form.description"
              class="form-control"
              rows="8"
              placeholder="支持 Markdown：## 标题、- [ ] 待办、**加粗**、行内代码"
            ></textarea>
            <div v-else-if="previewHtml" class="md-body composer__preview" v-html="previewHtml"></div>
            <div v-else class="composer__empty">暂无内容</div>
          </div>
        </div>
        <footer class="modal__footer">
          <button class="btn btn-default" @click="editorOpen = false">取消</button>
          <button class="btn btn-primary" :disabled="busyId === 'new' || (!!editing && busyId === editing.id)" @click="save">
            {{ editing ? '保存修改' : '创建任务' }}
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* ---------- 页面标题行 ---------- */
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}
.page-head__title {
  font-size: var(--font-size-3xl);
  font-weight: 400;
  letter-spacing: -0.015em;
  color: var(--text-main);
  line-height: 1.25;
  margin: 0;
}
.page-head__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

/* ---------- 搜索与属性筛选行 ---------- */
.filter-row {
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
.search-box__icon {
  color: var(--text-muted);
  flex-shrink: 0;
}
.search-box--wide {
  flex: 1;
  min-width: 220px;
  max-width: 480px;
}
.search-box--wide .search-box__input {
  width: 100%;
}
.search-box__input {
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--font-size-md);
  color: var(--text-main);
  width: 180px;
  font-family: inherit;
}
.search-box__input::placeholder {
  color: var(--text-placeholder);
}

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
  transition: border-color var(--duration-fast) var(--ease-out);
}
.select-sm:hover { border-color: var(--gray-400); }
.select-sm:focus { border-color: var(--primary); }

.segmented {
  display: flex;
  background: var(--gray-100);
  border-radius: var(--radius-sm);
  padding: 2px;
  gap: 2px;
}
.segmented button {
  border: none;
  background: transparent;
  padding: 4px 12px;
  height: 26px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-muted);
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out);
}
.segmented button:hover { color: var(--text-main); }
.segmented button.active {
  background: var(--bg-card);
  color: var(--text-main);
  box-shadow: var(--shadow-xs);
}

.alert {
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
}
.alert--danger {
  color: var(--danger-700);
  background: var(--danger-50);
  border: 1px solid var(--danger-border);
}

/* ---------- 状态符号 ---------- */
.status-symbol {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  font-size: 12px;
  line-height: 1;
  flex-shrink: 0;
}
.status-symbol--todo { color: var(--gray-400); }
.status-symbol--doing { color: var(--warning); }
.status-symbol--done { color: var(--success); }

.counter {
  font-size: var(--font-size-xs);
  font-weight: 500;
  color: var(--text-muted);
  background: var(--gray-100);
  padding: 0 6px;
  height: 18px;
  line-height: 18px;
  border-radius: var(--radius-full);
  min-width: 18px;
  text-align: center;
}

/* ---------- 看板 ---------- */
.board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-lg);
  align-items: start;
}

.board-col {
  background: var(--gray-50);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  min-height: 360px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.board-col__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 2px 6px;
}
.board-col__title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--text-main);
}

.board-col__empty {
  margin-top: var(--spacing-sm);
  padding: 28px 0;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-sm);
}

/* ---------- 任务卡片（看板） ---------- */
.task-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: grab;
  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
}
.task-card:hover {
  border-color: var(--gray-300);
  box-shadow: var(--shadow-sm);
}
.task-card--busy { opacity: 0.5; pointer-events: none; }
.task-card--done .task-card__title-text { color: var(--text-muted); }

.task-card__title {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: var(--font-size-base);
  font-weight: 500;
  line-height: 1.45;
  color: var(--text-main);
  margin: 0;
}
.task-card__title-text {
  word-break: break-word;
}

.task-card__labels {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.task-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.assignee {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.due { font-size: var(--font-size-sm); flex-shrink: 0; }
.due--overdue { color: var(--danger); font-weight: 500; }

.task-card__actions {
  display: flex;
  gap: 2px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color-light);
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.task-card:hover .task-card__actions { opacity: 1; }

.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  font-size: var(--font-size-xs);
  border: 1px solid transparent;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}
.link-btn:hover { color: var(--primary); background: var(--primary-50); }
.link-btn--danger:hover { color: var(--danger); background: var(--danger-50); }
.link-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ---------- 列表视图（GitHub Issues 列表：组件 IssueListHeader + IssueItem） ---------- */
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

/* ---------- 弹窗 ---------- */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--overlay-backdrop);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: var(--z-modal);
  padding: 24px;
}
.modal {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  width: 540px;
  max-width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
}
.modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-color);
}
.modal__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-main);
  margin: 0;
}
.modal__body {
  padding: 20px;
  overflow-y: auto;
}
.modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
  padding: 12px 20px;
  border-top: 1px solid var(--border-color);
}

/* ---------- 描述编辑器（编写 / 预览） ---------- */
.composer__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.composer__tabs {
  display: flex;
  gap: 2px;
  background: var(--gray-100);
  border-radius: var(--radius-sm);
  padding: 2px;
}
.composer__tab {
  border: none;
  background: transparent;
  padding: 3px 10px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: background var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out);
}
.composer__tab:hover { color: var(--text-main); }
.composer__tab.active {
  background: var(--bg-card);
  color: var(--text-main);
  box-shadow: var(--shadow-xs);
}
.composer__tab--action {
  margin-left: 4px;
  color: var(--primary);
}
.composer__preview {
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  min-height: 176px;
  max-height: 320px;
  overflow-y: auto;
  background: var(--bg-card);
}
.composer__empty {
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-sm);
  padding: 40px 0;
  text-align: center;
  font-size: var(--font-size-md);
  color: var(--text-muted);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--spacing-lg);
}

/* 响应式：中等宽度看板降为双列，窄屏单列 */
@media (max-width: 1100px) {
  .board { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .board { grid-template-columns: 1fr; }
  .form-row { grid-template-columns: 1fr; }
}
</style>
