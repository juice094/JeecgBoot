<script setup lang="ts">
// 任务详情页（/tools/taskboard/:id）
// 布局对齐 GitHub Issue 详情：主栏描述+时间线+评论框，右侧元数据栏。
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { PhArrowLeft, PhTrash, PhPaperclip } from '@phosphor-icons/vue';
import {
  fetchTaskDetail, addTaskComment, changeTaskStatus, deleteTask,
  type Task, type TaskComment, type TaskEvent,
} from '@/api/team';
import { renderMarkdown } from '@/utils/markdown';
import { TAG_CLASS, PRIORITY_CLASS, TASK_STATUSES, type TaskTag, type TaskPriority, type TaskStatusKey } from '@/constants/task';
import StatusBadge from '@/components/StatusBadge.vue';
import TimelineItem from '@/components/TimelineItem.vue';

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

/** 正文 Markdown 渲染 */
const bodyHtml = computed(() => renderMarkdown(task.value?.description || ''));

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
  comment: '发表了评论',
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

function statusLabel(code: string): string {
  const found = TASK_STATUSES.find((s) => s.key === code);
  return found ? found.title : code;
}

function formatTime(t?: string): string {
  if (!t) return '';
  return t.replace('T', ' ').slice(0, 16);
}

// ---- 评论 ----
const composerTab = ref<'write' | 'preview'>('write');
const commentBody = ref('');
const previewHtml = computed(() => renderMarkdown(commentBody.value));

async function submitComment() {
  if (!commentBody.value.trim() || busy.value) return;
  busy.value = true;
  errorMsg.value = '';
  try {
    await addTaskComment(taskId.value, commentBody.value.trim());
    commentBody.value = '';
    composerTab.value = 'write';
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
              {{ task.createBy || '未知' }} 创建于 {{ formatTime(task.createTime) || '—' }}
            </span>
          </div>
        </header>

        <!-- 描述卡片 -->
        <section class="comment-card">
          <div class="comment-card__head">
            <span class="avatar avatar--sm">{{ (task.createBy || '?').charAt(0).toUpperCase() }}</span>
            <span><strong>{{ task.createBy || '未知' }}</strong> 创建了此任务</span>
          </div>
          <div class="comment-card__body">
            <div v-if="bodyHtml" class="md-body" v-html="bodyHtml"></div>
            <div v-else class="comment-card__empty">暂无描述</div>
          </div>
        </section>

        <!-- 时间线 -->
        <ul class="timeline">
          <TimelineItem v-for="ev in timeline" :key="ev.id">
            <template #badge>●</template>
            <div class="timeline__line">
              <strong>{{ ev.operatorName || ev.operatorId || '系统' }}</strong>
              {{ eventText(ev) }}
              <span class="timeline__ago">{{ formatTime(ev.createTime) }}</span>
            </div>
          </TimelineItem>

          <!-- 评论事件渲染在时间线上 -->
          <TimelineItem v-for="c in comments" :key="'c-' + c.id">
            <template #badge>💬</template>
            <div class="timeline__comment">
              <div class="timeline__line">
                <strong>{{ c.authorName }}</strong> 评论了
                <span class="timeline__ago">{{ formatTime(c.createTime) }}</span>
              </div>
              <div class="comment-card comment-card--inline">
                <div class="comment-card__body">
                  <div class="md-body" v-html="renderMarkdown(c.body)"></div>
                </div>
              </div>
            </div>
          </TimelineItem>

          <div v-if="!timeline.length && !comments.length" class="empty-state">暂无活动</div>
        </ul>

        <!-- 评论框 -->
        <section class="composer">
          <h2 class="composer__title">添加评论</h2>
          <div class="composer__box">
            <div class="composer__head">
              <div class="composer__tabs">
                <button type="button" class="composer__tab" :class="{ active: composerTab === 'write' }" @click="composerTab = 'write'">编写</button>
                <button type="button" class="composer__tab" :class="{ active: composerTab === 'preview' }" @click="composerTab = 'preview'">预览</button>
              </div>
            </div>
            <textarea
              v-if="composerTab === 'write'"
              v-model="commentBody"
              class="composer__input"
              rows="6"
              placeholder="支持 Markdown：## 标题、- [ ] 待办、**加粗**"
            ></textarea>
            <div v-else class="composer__preview md-body" v-html="previewHtml || '<p class=&quot;muted&quot;>暂无内容</p>'"></div>
            <div class="composer__footer">
              <span class="composer__hint"><PhPaperclip :size="14" /> 暂不支持附件</span>
              <div class="composer__actions">
                <button class="btn btn-default btn--sm" :disabled="busy" @click="submitComment">评论</button>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- 侧栏 -->
      <aside class="detail-side">
        <div class="side-block">
          <div class="side-block__title">负责人</div>
          <div class="side-block__content">
            <span v-if="task.assigneeName" class="side-user">
              <span class="avatar avatar--sm">{{ task.assigneeName.charAt(0) }}</span>
              {{ task.assigneeName }}
            </span>
            <span v-else class="side-empty">未指派</span>
          </div>
        </div>

        <div class="side-block">
          <div class="side-block__title">标签</div>
          <div class="side-block__content side-block__content--wrap">
            <span v-if="task.tag" class="tag" :class="TAG_CLASS[task.tag as TaskTag]">{{ task.tag }}</span>
            <span v-if="task.priority" class="tag" :class="PRIORITY_CLASS[task.priority as TaskPriority]">{{ task.priority }}优先级</span>
            <span v-if="!task.tag && !task.priority" class="side-empty">无</span>
          </div>
        </div>

        <div class="side-block">
          <div class="side-block__title">截止日期</div>
          <div class="side-block__content">
            <span v-if="task.dueDate">{{ task.dueDate }}</span>
            <span v-else class="side-empty">未设置</span>
          </div>
        </div>

        <div class="side-block">
          <div class="side-block__title">状态流转</div>
          <div class="side-block__content side-block__content--col">
            <button
              v-for="s in nextStatuses"
              :key="s.key"
              class="side-action"
              :disabled="busy"
              @click="transit(s.key)"
            >标记为「{{ s.title }}」</button>
          </div>
        </div>

        <div class="side-block side-block--danger">
          <button class="side-action side-action--danger" :disabled="busy" @click="remove">
            <PhTrash :size="13" /> 删除任务
          </button>
        </div>
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

/* 评论/描述卡片 */
.comment-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  overflow: hidden;
}
.comment-card--inline { margin-top: 8px; }

.comment-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: var(--gray-50);
  border-bottom: 1px solid var(--border-color);
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.comment-card__head strong { color: var(--text-main); font-weight: 600; }

.comment-card__body { padding: 16px; }
.comment-card__empty {
  color: var(--text-muted);
  font-size: var(--font-size-md);
}

/* 时间线 */
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
}

.timeline__line {
  font-size: var(--font-size-md);
  color: var(--text-regular);
  line-height: 1.5;
}
.timeline__line strong { color: var(--text-main); font-weight: 600; }
.timeline__ago { color: var(--text-muted); margin-left: 6px; }
.timeline__comment { display: flex; flex-direction: column; }

/* 评论框 */
.composer { display: flex; flex-direction: column; gap: 8px; }
.composer__title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  margin: 0;
  color: var(--text-main);
}

.composer__box {
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  overflow: hidden;
  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
}
.composer__box:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary) 12%, transparent);
}

.composer__head {
  padding: 6px 8px;
  background: var(--gray-50);
  border-bottom: 1px solid var(--border-color);
}

.composer__tabs {
  display: inline-flex;
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
}
.composer__tab.active {
  background: var(--bg-card);
  color: var(--text-main);
  box-shadow: var(--shadow-xs);
}

.composer__input {
  width: 100%;
  border: none;
  outline: none;
  resize: vertical;
  padding: 12px;
  font-family: inherit;
  font-size: var(--font-size-base);
  line-height: 1.6;
  color: var(--text-main);
  background: var(--bg-card);
  min-height: 140px;
}

.composer__preview {
  padding: 12px;
  min-height: 140px;
}

.composer__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid var(--border-color);
  background: var(--gray-50);
}
.composer__hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

/* 侧栏 */
.detail-side {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding-top: 2px;
}

.side-block {
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}
.side-block:last-child { border-bottom: none; padding-bottom: 0; }

.side-block__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.side-block__content { font-size: var(--font-size-md); color: var(--text-main); }
.side-block__content--wrap { display: flex; flex-wrap: wrap; gap: 4px; }
.side-block__content--col { display: flex; flex-direction: column; gap: 6px; }

.side-user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.side-empty { color: var(--text-muted); font-size: var(--font-size-sm); }

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

.side-block--danger { border-bottom: none; }
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
