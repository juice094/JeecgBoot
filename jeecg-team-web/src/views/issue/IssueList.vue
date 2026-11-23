<script setup lang="ts">
// 统一工作项列表（我的工作台）
// 三类实体并行拉取后经 adapter 归一化，不做后端合并；单项操作跳回各自专属页面。
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { PhMagnifyingGlass, PhArrowSquareOut } from '@phosphor-icons/vue';
import IssueListHeader from '@/components/IssueListHeader.vue';
import IssueItem from '@/components/IssueItem.vue';
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

// ---- 列表头 Open/Closed 口径（对齐 GitHub：Open = 待处理+进行中，Closed = 已完成+已关闭） ----
const listScope = ref<'open' | 'closed'>('open');
const openCount = computed(() => filtered.value.filter((i) => i.state === 'open' || i.state === 'active').length);
const closedCount = computed(() => filtered.value.filter((i) => i.state === 'done' || i.state === 'closed').length);
const scoped = computed(() =>
  filtered.value.filter((i) =>
    listScope.value === 'open'
      ? i.state === 'open' || i.state === 'active'
      : i.state === 'done' || i.state === 'closed',
  ),
);

const kindCounts = computed(() => {
  const counts: Record<string, number> = { all: items.value.length };
  ISSUE_KINDS.forEach((k) => { counts[k] = items.value.filter((i) => i.kind === k).length; });
  return counts;
});

/** 行元信息：状态 · 指派人 · 截止（逾期标注） */
function rowMeta(item: UnifiedIssue): string {
  const parts = [item.stateLabel];
  if (item.assignee) parts.push(item.assignee);
  if (item.dueDate) parts.push(item.dueDate + (item.overdue ? ' 已逾期' : ''));
  return parts.join(' · ');
}

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

    <!-- 列表（GitHub Issues 列表：Open/Closed 头 + IssueItem 行） -->
    <template v-else>
      <IssueListHeader v-model="listScope" :open-count="openCount" :closed-count="closedCount">
        <template #filters>
          <span class="list-count">{{ scoped.length }} 个工作项</span>
        </template>
      </IssueListHeader>

      <ul class="issue-panel">
        <IssueItem
          v-for="item in scoped"
          :key="item.uid"
          :state="item.state"
          :title="item.title"
          :labels="[{ text: ISSUE_KIND_META[item.kind].label, cls: ISSUE_KIND_META[item.kind].cls }, ...item.labels]"
          :meta="rowMeta(item)"
          @open="openSource(item)"
        >
          <template #actions>
            <button class="icon-btn" title="前往处理" @click.stop="openSource(item)">
              <PhArrowSquareOut :size="14" />
            </button>
          </template>
        </IssueItem>
        <li v-if="!scoped.length" class="empty-state">
          {{ items.length ? '没有匹配的工作项' : '暂无工作项' }}
        </li>
      </ul>
    </template>
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

/* ---------- 列表（GitHub Issues 列表：组件 IssueListHeader + IssueItem） ---------- */
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
.list-count {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

@media (max-width: 900px) {
  .toolbar__right { flex-wrap: wrap; }
}
</style>
