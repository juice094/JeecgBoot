<script setup lang="ts">
// 任务详情页（/tools/taskboard/:id）
// 布局对齐 GitHub Issue 详情（.refs/raw/github-issue-3-detail.htm）：
// 主栏 = 描述卡片 + 时间线（事件与评论按时间混排）+ 评论编辑器；右侧 296px 元数据栏。
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  PhArrowLeft, PhArrowCounterClockwise, PhArrowsLeftRight, PhChatCircle, PhGearSix, PhPlusCircle, PhTrash,
} from '@phosphor-icons/vue';
import {
  fetchTaskDetail, addTaskComment, changeTaskStatus, deleteTask,
  type Task, type TaskComment, type TaskEvent,
} from '@/api/team';
import { timeAgo } from '@/utils/time';
import { TASK_STATUSES, TAG_CLASS, PRIORITY_CLASS, type TaskTag, type TaskPriority, type TaskStatusKey } from '@/constants/task';
import StatusBadge from '@/components/StatusBadge.vue';
import TimelineItem from '@/components/TimelineItem.vue';
import TimelineEvent from '@/components/TimelineEvent.vue';
import CommentCard from '@/components/CommentCard.vue';
import CommentEditor from '@/components/CommentEditor.vue';
import SidebarSection from '@/components/SidebarSection.vue';

const route = useRoute();
const router = useRouter();
const taskId = computed(() => String(route.params.id || ''));

const task = ref<Task | null>(null);
const comments = ref<TaskComment[]>([]);
const timeline = ref<TaskEvent[]>([]);
const loading = ref(false);
const errorMsg = ref('');
const busy = ref(false);

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const data = await fetchTaskDetail(taskId.value);
    // 业务错误信封（success=false/result=null，如端点缺失、无权查看）降级为可读文案，不崩溃
    if (!data.result || !data.result.task) {
      errorMsg.value = (data as { message?: string }).message || '任务不存在或无权查看';
      return;
    }
    task.value = data.result.task;
    comments.value = data.result.comments || [];
    timeline.value = data.result.timeline || [];
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
}
onMounted(load);

/** 状态文案与色调 */
const statusMeta = computed(() => {
  const s = task.value?.status;
  if (s === 'done') return { label: '已完成', tone: 'done' as const };
  if (s === 'doing') return { label: '进行中', tone: 'active' as const };
  return { label: '待办', tone: 'open' as const };
});

/** 序号显示：优先 taskNo，回退 UUID 前 7 位 */
const taskLabel = computed(() => {
  const t = task.value;
  if (!t) return '';
  if (t.taskNo) return '#' + t.taskNo;
  return '#' + t.id.slice(0, 7);
});

/** 事件动作 -> 中文文案 */
const ACTION_TEXT: Record<string, string> = {
  create: '创建了此任务',
  submit: '提交了任务',
  change_status: '变更了状态',
  approve: '通过了审批',
  reject: '拒绝了审批',
};

function eventText(ev: TaskEvent): string {
  const base = ACTION_TEXT[ev.action] || ev.action;
  if (ev.action === 'change_status' && ev.fromValue && ev.toValue) {
    return '将状态从 ' + statusLabel(ev.fromValue) + ' 变更为 ' + statusLabel(ev.toValue);
  }
  return base;
}

/** 事件动作 -> 徽章图标（comment 事件不渲染：由评论卡片自身代表） */
const ACTION_ICON: Record<string, unknown> = {
  create: PhPlusCircle,
  change_status: PhArrowsLeftRight,
};

function eventIcon(ev: TaskEvent): unknown {
  return ACTION_ICON[ev.action] || PhArrowCounterClockwise;
}

function statusLabel(code: string): string {
  const found = TASK_STATUSES.find((s) => s.key === code);
  return found ? found.title : code;
}

type TimelineNode =
  | { kind: 'event'; time: string; event: TaskEvent }
  | { kind: 'comment'; time: string; comment: TaskComment };

/** 时间线混排：事件（剔除 comment 类，避免与评论卡重复）+ 评论，按时间升序 */
const nodes = computed<TimelineNode[]>(() => {
  const events: TimelineNode[] = timeline.value
    .filter((ev) => ev.action !== 'comment')
    .map((ev) => ({ kind: 'event' as const, time: ev.createTime || '', event: ev }));
  const commentNodes: TimelineNode[] = comments.value.map((c) => ({
    kind: 'comment' as const,
    time: c.createTime || '',
    comment: c,
  }));
  return [...events, ...commentNodes].sort((a, b) => a.time.localeCompare(b.time));
});

function nodeKey(node: TimelineNode): string {
  return node.kind + '-' + (node.kind === 'event' ? node.event.id : node.comment.id);
}

// ---- 评论 ----
const commentBody = ref('');

async function submitComment() {
  if (!commentBody.value.trim() || busy.value) return;
  busy.value = true;
  errorMsg.value = '';
  try {
    await addTaskComment(taskId.value, commentBody.value.trim());
    commentBody.value = '';
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '评论失败';
  } finally {
    busy.value = false;
  }
}

// ---- 状态流转 ----
const nextStatuses = computed(() => {
  const s = task.value?.status;
  return TASK_STATUSES.filter((x) => x.key !== s);
});

async function transit(target: TaskStatusKey) {
  if (!task.value || busy.value) return;
  busy.value = true;
  errorMsg.value = '';
  try {
    await changeTaskStatus(task.value.id, target);
    await load();
  } catch (e: any) {
    errorMsg.value = e.message || '状态变更失败';
  } finally {
    busy.value = false;
  }
}

async function remove() {
  if (!task.value) return;
  if (!window.confirm('删除任务「' + task.value.title + '」？（逻辑删除）')) return;
  busy.value = true;
  try {
    await deleteTask(task.value.id);
    router.push('/tools/taskboard');
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败';
    busy.value = false;
  }
}

function back() {
  router.push('/tools/taskboard');
}
</script>

<template>
  <div class="detail-page">
    <button class="back-btn" @click="back">
      <PhArrowLeft :size="14" /> 返回任务列表
    </button>

    <div v-if="errorMsg" class="alert">{{ errorMsg }}</div>
    <div v-if="loading" class="empty-state">加载中…</div>

    <div v-else-if="task" class="detail-layout">
      <!-- 主栏 -->
      <div class="detail-main">
        <header class="detail-header">
          <h1 class="detail-title">
            {{ task.title }}
            <span class="detail-number">{{ taskLabel }}</span>
          </h1>
          <div class="detail-header__meta">
            <StatusBadge :label="statusMeta.label" :tone="statusMeta.tone" />
            <span class="detail-header__by">
              {{ task.createBy || '未知' }} opened {{ timeAgo(task.createTime) || '—' }}
            </span>
          </div>
        </header>

        <!-- 描述卡片（时间线首项） -->
        <TimelineItem>
          <template #badge>
            <span class="avatar avatar--sm">{{ (task.createBy || '?').charAt(0).toUpperCase() }}</span>
          </template>
          <CommentCard
            :author="task.createBy || '未知'"
            :subtitle="'opened ' + (timeAgo(task.createTime) || '—')"
            :badges="['Owner']"
            :body="task.description || '_暂无描述_'"
          />
        </TimelineItem>

        <!-- 时间线：事件与评论按时间混排 -->
        <div class="timeline">
          <template v-for="node in nodes" :key="nodeKey(node)">
            <TimelineEvent v-if="node.kind === 'event'">
              <template #icon>
                <component :is="eventIcon(node.event)" :size="14" />
              </template>
              <strong>{{ node.event.operatorName || node.event.operatorId || '系统' }}</strong>
              {{ eventText(node.event) }}
              <span class="timeline__ago">{{ timeAgo(node.event.createTime) }}</span>
            </TimelineEvent>

            <TimelineItem v-else>
              <template #badge>
                <PhChatCircle :size="14" />
              </template>
              <CommentCard
                :author="node.comment.authorName"
                :subtitle="timeAgo(node.comment.createTime)"
                :body="node.comment.body"
              />
            </TimelineItem>
          </template>

          <div v-if="!nodes.length" class="empty-state">暂无活动</div>
        </div>

        <!-- 评论编辑器 -->
        <section class="composer">
          <h2 class="composer__title">添加评论</h2>
          <CommentEditor
            v-model="commentBody"
            submit-text="评论"
            :busy="busy"
            @submit="submitComment"
          >
            <template #hint>支持 Markdown：## 标题、- [ ] 待办、**加粗**</template>
          </CommentEditor>
        </section>
      </div>

      <!-- 侧栏（296px） -->
      <aside class="detail-side">
        <SidebarSection title="Assignees · 负责人">
          <template #action><PhGearSix :size="14" /></template>
          <span v-if="task.assigneeName" class="side-user">
            <span class="avatar avatar--sm">{{ task.assigneeName.charAt(0) }}</span>
            {{ task.assigneeName }}
          </span>
          <span v-else class="side-empty">未指派</span>
        </SidebarSection>

        <SidebarSection title="Labels · 标签">
          <template #action><PhGearSix :size="14" /></template>
          <div class="side-tags">
            <span v-if="task.tag" class="tag" :class="TAG_CLASS[task.tag as TaskTag]">{{ task.tag }}</span>
            <span v-if="task.priority" class="tag" :class="PRIORITY_CLASS[task.priority as TaskPriority]">{{ task.priority }}优先级</span>
            <span v-if="!task.tag && !task.priority" class="side-empty">无</span>
          </div>
        </SidebarSection>

        <SidebarSection title="Milestone · 截止日期">
          <span v-if="task.dueDate">{{ task.dueDate }}</span>
          <span v-else class="side-empty">未设置</span>
        </SidebarSection>

        <SidebarSection title="状态流转">
          <div class="side-actions">
            <button
              v-for="s in nextStatuses"
              :key="s.key"
              class="side-action"
              :disabled="busy"
              @click="transit(s.key)"
            >标记为「{{ s.title }}」</button>
          </div>
        </SidebarSection>

        <SidebarSection title="危险区">
          <button class="side-action side-action--danger" :disabled="busy" @click="remove">
            <PhTrash :size="13" /> 删除任务
          </button>
        </SidebarSection>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  padding: 4px 8px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: var(--font-size-md);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}
.back-btn:hover { color: var(--text-main); background: var(--gray-100); }

.alert {
  padding: 8px 14px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
  color: var(--danger-700);
  background: var(--danger-50);
  border: 1px solid var(--danger-border);
}

/* 两栏布局：主栏自适应 + 侧栏固定 296px */
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 296px;
  gap: var(--spacing-2xl);
  align-items: start;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
  min-width: 0;
}

.detail-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-title {
  font-size: var(--font-size-3xl);
  font-weight: 400;
  line-height: 1.3;
  color: var(--text-main);
  margin: 0;
  letter-spacing: -0.01em;
}

.detail-number {
  color: var(--text-muted);
  font-weight: 300;
  margin-left: 6px;
}

.detail-header__meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.detail-header__by {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

/* 时间线 */
.timeline {
  display: flex;
  flex-direction: column;
}
.timeline__ago {
  color: var(--text-muted);
  margin-left: 6px;
}

/* 评论编辑器 */
.composer { display: flex; flex-direction: column; gap: 8px; }
.composer__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  margin: 0;
  color: var(--text-main);
}

/* 侧栏 */
.detail-side {
  padding-top: 2px;
}

.side-user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.side-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.side-empty { color: var(--text-muted); font-size: var(--font-size-sm); }

.side-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-action {
  width: 100%;
  text-align: left;
  padding: 6px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-regular);
  font-size: var(--font-size-md);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out), color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out);
}
.side-action:hover:not(:disabled) {
  border-color: var(--primary);
  color: var(--primary);
  background: var(--primary-50);
}
.side-action:disabled { opacity: 0.5; cursor: not-allowed; }

.side-action--danger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-content: center;
  border-color: var(--danger-border);
  color: var(--danger);
}
.side-action--danger:hover:not(:disabled) {
  border-color: var(--danger);
  color: #fff;
  background: var(--danger);
}

/* 响应式：窄屏侧栏下移 */
@media (max-width: 900px) {
  .detail-layout { grid-template-columns: 1fr; }
  .detail-side { padding-top: 0; }
}
</style>
