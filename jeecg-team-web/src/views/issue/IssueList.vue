<script setup lang="ts">
// 统一工作项列表（我的工作台）
// 三类实体并行拉取后经 adapter 归一化，不做后端合并；单项操作跳回各自专属页面。
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { PhMagnifyingGlass, PhArrowSquareOut } from '@phosphor-icons/vue';
import { fetchTaskBoard, listBugs, listApprovals, listApprovalTodos } from '@/api/team';
import {
  fromTask, fromBug, fromApproval,
  ISSUE_KIND_META, STATE_META, ISSUE_KINDS, UNIFIED_STATES,
  type UnifiedIssue, type IssueKind, type UnifiedState,
} from '@/constants/issue';

const router = useRouter();

const items = ref<UnifiedIssue[]>([]);
const loading = ref(false);
const loadError = ref('');

const searchQuery = ref('');
const filterKind = ref<IssueKind | 'all'>('all');
const filterState = ref<UnifiedState | 'all'>('all');
const hideFinished = ref(true);

async function load() {
  loading.value = true;
  loadError.value = '';

  const results = await Promise.allSettled([
    fetchTaskBoard(),
    listBugs({ pageNo: 1, pageSize: 50 }),
    listApprovalTodos({ pageNo: 1, pageSize: 50 }),
    listApprovals({ pageNo: 1, pageSize: 50 }),
  ]);

  const failed: string[] = [];
  const merged: UnifiedIssue[] = [];

  const [taskRes, bugRes, todoRes, mineRes] = results;

  if (taskRes.status === 'fulfilled') {
    const board = taskRes.value.result;
    const all = [...board.todo, ...board.doing, ...board.done];
    all.forEach((t) => merged.push(fromTask(t)));
  } else {
    failed.push('任务');
  }

  if (bugRes.status === 'fulfilled') {
    bugRes.value.result.records.forEach((b) => merged.push(fromBug(b)));
  } else {
    failed.push('缺陷');
  }

  // 审批：待办与我发起的可能有重叠，用 Map 按 uid 去重
  const approvalMap = new Map<string, UnifiedIssue>();
  if (todoRes.status === 'fulfilled') {
    todoRes.value.result.records.forEach((a) => {
      const item = fromApproval(a);
      approvalMap.set(item.uid, item);
    });
  } else {
    failed.push('待办审批');
  }
  if (mineRes.status === 'fulfilled') {
    mineRes.value.result.records.forEach((a) => {
      const item = fromApproval(a);
      approvalMap.set(item.uid, item);
    });
  } else {
    failed.push('我的审批');
  }
  approvalMap.forEach((item) => merged.push(item));

  items.value = merged;
  loadError.value = failed.length ? failed.join('、') + ' 加载失败，其余数据正常展示' : '';
  loading.value = false;
}
onMounted(load);

const filtered = computed(() => {
  const q = searchQuery.value.trim().toLowerCase();
  return items.value
    .filter((item) => {
      if (filterKind.value !== 'all' && item.kind !== filterKind.value) return false;
      if (filterState.value !== 'all' && item.state !== filterState.value) return false;
      if (hideFinished.value && (item.state === 'done' || item.state === 'closed')) return false;
      if (q) {
        const hay = (item.title + ' ' + item.summary).toLowerCase();
        if (!hay.includes(q)) return false;
      }
      return true;
    })
    .sort((a, b) => {
      // 逾期最前，其次按状态权重，再次按创建时间倒序
      if (a.overdue !== b.overdue) return a.overdue ? -1 : 1;
      const weight: Record<UnifiedState, number> = { active: 0, open: 1, done: 2, closed: 3 };
      if (weight[a.state] !== weight[b.state]) return weight[a.state] - weight[b.state];
      return (b.createdAt || '').localeCompare(a.createdAt || '');
    });
});

const kindCounts = computed(() => {
  const counts: Record<string, number> = { all: items.value.length };
  ISSUE_KINDS.forEach((k) => { counts[k] = items.value.filter((i) => i.kind === k).length; });
  return counts;
});

function openSource(item: UnifiedIssue) {
  router.push(item.sourcePath);
}
</script>

<template>
  <div class="issue-page">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="segmented">
        <button :class="{ active: filterKind === 'all' }" @click="filterKind = 'all'">
          全部 <span class="count">{{ kindCounts.all }}</span>
        </button>
        <button
          v-for="kind in ISSUE_KINDS"
          :key="kind"
          :class="{ active: filterKind === kind }"
          @click="filterKind = kind"
        >
          {{ ISSUE_KIND_META[kind].label }} <span class="count">{{ kindCounts[kind] }}</span>
        </button>
      </div>

      <div class="toolbar__right">
        <div class="search-box">
          <PhMagnifyingGlass :size="15" class="search-box__icon" />
          <input v-model="searchQuery" type="text" placeholder="搜索标题与内容…" class="search-box__input" />
        </div>
        <select v-model="filterState" class="select-sm">
          <option value="all">所有状态</option>
          <option v-for="state in UNIFIED_STATES" :key="state" :value="state">{{ STATE_META[state].label }}</option>
        </select>
        <label class="checkbox-label">
          <input v-model="hideFinished" type="checkbox" />
          隐藏已完成
        </label>
      </div>
    </div>

    <div v-if="loadError" class="alert">{{ loadError }}</div>
    <div v-if="loading" class="empty-state">加载中…</div>

    <!-- 列表 -->
    <div v-else class="issue-list">
      <div class="issue-list__toolbar">
        <span>{{ filtered.length }} 个工作项</span>
      </div>

      <ul class="issue-list__body">
        <li
          v-for="item in filtered"
          :key="item.uid"
          class="issue-row"
          :class="{ 'issue-row--overdue': item.overdue }"
          @click="openSource(item)"
        >
          <span class="issue-row__status" :title="item.stateLabel">
            <span class="state-symbol" :class="'state-symbol--' + item.state">{{ STATE_META[item.state].symbol }}</span>
          </span>

          <div class="issue-row__primary">
            <span class="issue-row__title">{{ item.title }}</span>
          </div>

          <div class="issue-row__metadata">
            <span class="tag tag--sm" :class="ISSUE_KIND_META[item.kind].cls">{{ ISSUE_KIND_META[item.kind].label }}</span>
            <span class="tag tag--sm" :class="item.stateCls">{{ item.stateLabel }}</span>
            <template v-if="item.assignee">
              <span class="meta-sep">·</span>
              <span>{{ item.assignee }}</span>
            </template>
            <template v-if="item.dueDate">
              <span class="meta-sep">·</span>
              <span :class="{ 'meta-overdue': item.overdue }">
                {{ item.dueDate }}{{ item.overdue ? ' 已逾期' : '' }}
              </span>
            </template>
          </div>

          <div class="issue-row__labels">
            <span
              v-for="label in item.labels"
              :key="label.text"
              class="tag"
              :class="label.cls"
            >{{ label.text }}</span>
          </div>

          <div class="issue-row__actions">
            <button class="icon-btn" title="前往处理" @click.stop="openSource(item)">
              <PhArrowSquareOut :size="14" />
            </button>
          </div>
        </li>
      </ul>

      <div v-if="!filtered.length" class="empty-state">
        {{ items.length ? '没有匹配的工作项' : '暂无工作项' }}
      </div>
    </div>
  </div>
</template>

<style scoped>
.issue-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* ---------- 工具栏 ---------- */
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}
.toolbar__right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.segmented {
  display: flex;
  background: var(--gray-100);
  border-radius: var(--radius-sm);
  padding: 2px;
  gap: 2px;
}
.segmented button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  padding: 4px 12px;
  height: 28px;
  font-size: var(--font-size-md);
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
.count {
  font-size: var(--font-size-xs);
  font-weight: 500;
  color: var(--text-muted);
  background: var(--gray-100);
  padding: 0 5px;
  height: 16px;
  line-height: 16px;
  border-radius: var(--radius-full);
  min-width: 16px;
  text-align: center;
}
.segmented button.active .count {
  background: var(--gray-100);
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
  width: 190px;
  font-family: inherit;
}
.search-box__input::placeholder { color: var(--text-placeholder); }

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

.checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: var(--font-size-md);
  color: var(--text-regular);
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}

.alert {
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
  color: var(--warning-700);
  background: var(--warning-50);
  border: 1px solid var(--warning-border);
}

/* ---------- 列表（复用 GitHub Issue 行结构） ---------- */
.issue-list {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
}
.issue-list__toolbar {
  height: 44px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  background: var(--gray-100);
  border-bottom: 1px solid var(--border-color-light);
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--text-main);
}
.issue-list__body {
  list-style: none;
  margin: 0;
  padding: 0;
}

.issue-row {
  display: grid;
  grid-template-columns: min-content minmax(30%, 1fr) minmax(0, max-content) min-content;
  grid-template-rows: auto auto;
  grid-template-areas:
    "status primary labels actions"
    "status metadata metadata actions";
  gap: 2px 8px;
  padding: 8px 16px;
  min-height: 32px;
  cursor: pointer;
  transition: background-color var(--duration-fast) var(--ease-out);
}
.issue-row:hover { background: var(--gray-50); }
.issue-row:not(:last-child) { border-bottom: 1px solid var(--border-color-light); }

.issue-row__status {
  grid-area: status;
  padding-top: 3px;
  display: flex;
  align-items: flex-start;
}
.state-symbol {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  font-size: 12px;
  line-height: 1;
}
.state-symbol--open { color: var(--gray-400); }
.state-symbol--active { color: var(--warning); }
.state-symbol--done { color: var(--success); }
.state-symbol--closed { color: var(--gray-500); }

.issue-row__primary {
  grid-area: primary;
  min-width: 0;
  display: flex;
  align-items: baseline;
}
.issue-row__title {
  font-size: var(--font-size-lg);
  font-weight: 500;
  line-height: 1.5;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-word;
}
.issue-row:hover .issue-row__title { color: var(--primary); }

.issue-row__metadata {
  grid-area: metadata;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: var(--font-size-sm);
  font-weight: 400;
  color: var(--text-muted);
  line-height: 1.5;
}
.meta-sep { opacity: 0.6; }
.meta-overdue { color: var(--danger); font-weight: 500; }

.issue-row__labels {
  grid-area: labels;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 4px;
}

.issue-row__actions {
  grid-area: actions;
  display: flex;
  align-items: center;
  opacity: 0;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.issue-row:hover .issue-row__actions { opacity: 1; }

.tag--sm {
  height: 18px;
  padding: 0 6px;
  font-size: var(--font-size-xs);
}

@media (max-width: 900px) {
  .issue-row {
    grid-template-columns: min-content minmax(0, 1fr) min-content;
    grid-template-areas:
      "status primary actions"
      "status metadata actions"
      "status labels labels";
  }
  .issue-row__labels { justify-content: flex-start; }
  .toolbar__right { flex-wrap: wrap; }
}
</style>
