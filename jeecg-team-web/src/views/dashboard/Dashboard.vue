<script setup lang="ts">
// 仪表盘（概览）——公共层页面
// 数据适配（2026-11-17）：原型"考勤记录"为学校演示内容，替换为 jeecg 原生"系统公告"（真实接口）；
// 我的事务接 /team/approval 真实数据；顶部三计数接 /team/dashboard/summary（宿授权 task-7）。
// 加载策略：allSettled 局部容错——单个接口失败不导致整页空态。
import { computed, onMounted, ref } from 'vue';
import { PhFileText, PhCurrencyCircleDollar, PhMegaphone } from '@phosphor-icons/vue';
import { useAuthStore } from '@/stores/auth';
import { listApprovals, listApprovalTodos, fetchDashboardSummary, type Approval, type DashboardSummary } from '@/api/team';
import { listAnnouncements, type Announcement } from '@/api/announcement';

const auth = useAuthStore();
const activeTab = ref<'todo' | 'mine'>('mine');

/** summary 接口当前只保证三计数；公告聚合字段为可选项，缺失时回退公告接口 */
type SummaryWithNotices = DashboardSummary & {
  latestNotices?: Announcement[];
  unreadNoticeCount?: number;
};

const todoList = ref<Approval[]>([]);
const mineList = ref<Approval[]>([]);
const announcements = ref<Announcement[]>([]);
const summary = ref<SummaryWithNotices | null>(null);
const loadError = ref('');

const shownItems = computed(() => (activeTab.value === 'mine' ? mineList.value : todoList.value).slice(0, 5));
const noticeList = computed(() =>
  summary.value?.latestNotices?.length ? summary.value.latestNotices : announcements.value,
);
const unreadNoticeCount = computed(() => summary.value?.unreadNoticeCount ?? 0);

onMounted(async () => {
  const results = await Promise.allSettled([
    listApprovalTodos({ pageSize: 50 }),
    listApprovals({ applicantId: auth.userInfo.id, pageSize: 50 }),
    listAnnouncements(),
    fetchDashboardSummary(),
  ]);

  const failed: string[] = [];
  const [todo, mine, ann, sum] = results;

  if (todo.status === 'fulfilled') todoList.value = todo.value.result.records;
  else failed.push('待办审批');

  if (mine.status === 'fulfilled') mineList.value = mine.value.result.records;
  else failed.push('我的申请');

  if (ann.status === 'fulfilled') announcements.value = ann.value.slice(0, 5);
  else failed.push('系统公告');

  if (sum.status === 'fulfilled') summary.value = sum.value.result;
  else failed.push('概览计数');

  loadError.value = failed.length ? `${failed.join('、')} 加载失败，其余数据正常展示` : '';
});

const ITEM_ICONS = [PhFileText, PhCurrencyCircleDollar, PhMegaphone];

const STATUS_TEXT: Record<Approval['status'], string> = {
  draft: '草稿', pending: '审批中', returned: '已退回',
  approved: '已通过', rejected: '已拒绝', canceled: '已撤销',
};
const STATUS_CLS: Record<Approval['status'], string> = {
  draft: 'tag--neutral', pending: 'tag--warning', returned: 'tag--info',
  approved: 'tag--success', rejected: 'tag--danger', canceled: 'tag--neutral',
};
const STATUS_SYMBOL: Record<Approval['status'], string> = {
  draft: '·', pending: '○', returned: '↩',
  approved: '✓', rejected: '✕', canceled: '—',
};
</script>

<template>
  <div class="dashboard">
    <header class="page-header">
      <h2>你好，{{ auth.displayName }}</h2>
      <p>跟进申请进度、查看公告与团队动态。</p>
    </header>

    <div v-if="loadError" class="alert">{{ loadError }}</div>

    <div class="stats-row">
      <router-link to="/tools/approval" class="stat-tile">
        <span class="stat-tile__num">{{ summary?.pendingApprovalCount ?? '—' }}</span>
        <span class="stat-tile__label">待审审批</span>
      </router-link>
      <router-link to="/tools/taskboard" class="stat-tile">
        <span class="stat-tile__num">{{ summary?.myUnfinishedTaskCount ?? '—' }}</span>
        <span class="stat-tile__label">我的未完成任务</span>
      </router-link>
      <router-link to="/tools/bug" class="stat-tile">
        <span class="stat-tile__num">{{ summary?.openBugCount ?? '—' }}</span>
        <span class="stat-tile__label">未关闭 Bug</span>
      </router-link>
    </div>

    <div class="panels">
      <section class="panel">
        <header class="panel__header">
          <h3 class="panel__title">我的事务</h3>
          <router-link class="panel__link" to="/tools/approval">审批中心 →</router-link>
        </header>

        <div class="tabs">
          <div class="tab-item" :class="{ active: activeTab === 'todo' }" @click="activeTab = 'todo'">
            待我审批 <span class="num">{{ todoList.length }}</span>
          </div>
          <div class="tab-item" :class="{ active: activeTab === 'mine' }" @click="activeTab = 'mine'">
            我的申请 <span class="num">{{ mineList.length }}</span>
          </div>
        </div>

        <ul class="row-list">
          <li v-for="(item, i) in shownItems" :key="item.id" class="row">
            <span class="row__icon">
              <component :is="ITEM_ICONS[i % ITEM_ICONS.length]" :size="15" />
            </span>
            <div class="row__main">
              <div class="row__title">{{ item.title }}</div>
              <div class="row__meta">{{ item.category }} · {{ item.code }}</div>
            </div>
            <span class="tag" :class="STATUS_CLS[item.status]">
              <span class="tag__symbol">{{ STATUS_SYMBOL[item.status] }}</span>{{ STATUS_TEXT[item.status] }}
            </span>
          </li>
        </ul>
        <div v-if="!shownItems.length" class="empty-state">暂无数据</div>
      </section>

      <section class="panel">
        <header class="panel__header">
          <h3 class="panel__title">
            系统公告
            <span v-if="unreadNoticeCount" class="tag tag--neutral">{{ unreadNoticeCount }} 未读</span>
          </h3>
          <span class="panel__link panel__link--muted">全部</span>
        </header>

        <ul class="row-list">
          <li v-for="ann in noticeList" :key="ann.id" class="row">
            <span class="row__icon row__icon--accent"><PhMegaphone :size="15" /></span>
            <div class="row__main">
              <div class="row__title row__title--truncate">{{ ann.titile || '（无标题）' }}</div>
              <div class="row__meta">{{ ann.sender || '系统' }} · {{ (ann.sendTime || '').slice(0, 10) }}</div>
            </div>
          </li>
        </ul>
        <div v-if="!noticeList.length" class="empty-state">暂无公告</div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
}

/* ---------- 页头 ---------- */
.page-header h2 {
  font-size: var(--font-size-3xl);
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--text-main);
  line-height: 1.3;
}
.page-header p {
  margin-top: 4px;
  font-size: var(--font-size-md);
  color: var(--text-muted);
}

.alert {
  font-size: var(--font-size-md);
  color: var(--warning-700);
  background: var(--warning-50);
  border: 1px solid var(--warning-border);
  border-radius: var(--radius-sm);
  padding: 8px 14px;
}

/* ---------- 统计磁贴 ---------- */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-lg);
}
.stat-tile {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: border-color var(--duration-fast) var(--ease-out), box-shadow var(--duration-fast) var(--ease-out);
}
.stat-tile:hover {
  border-color: var(--gray-300);
  box-shadow: var(--shadow-sm);
}
.stat-tile__num {
  font-size: var(--font-size-3xl);
  font-weight: 600;
  line-height: 1.1;
  color: var(--text-main);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}
.stat-tile__label {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

/* ---------- 双栏面板 ---------- */
.panels {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: var(--spacing-lg);
  align-items: start;
}

.panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px 18px;
}
.panel__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}
.panel__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-main);
  margin: 0;
}
.panel__link {
  font-size: var(--font-size-sm);
  color: var(--primary);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-out);
}
.panel__link:hover { color: var(--primary-hover); }
.panel__link--muted { color: var(--text-muted); cursor: default; }

/* ---------- 行列表 ---------- */
.row-list { display: flex; flex-direction: column; }
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 0;
  border-bottom: 1px solid var(--border-color-light);
}
.row:last-child { border-bottom: none; padding-bottom: 2px; }

.row__icon {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--gray-50);
  color: var(--text-muted);
}
.row__icon--accent {
  color: var(--primary);
  background: var(--primary-50);
}

.row__main { flex: 1; min-width: 0; }
.row__title {
  font-size: var(--font-size-md);
  font-weight: 500;
  color: var(--text-main);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row__title--truncate { max-width: 100%; }
.row__meta {
  margin-top: 1px;
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tag__symbol {
  font-size: 9px;
  line-height: 1;
  opacity: 0.9;
}

@media (max-width: 1024px) {
  .panels { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .stats-row { grid-template-columns: 1fr; }
}
</style>
