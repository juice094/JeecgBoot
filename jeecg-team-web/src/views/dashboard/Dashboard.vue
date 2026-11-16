<script setup lang="ts">
// 仪表盘（概览）——公共层页面
// 数据适配（2026-11-17）：原型"考勤记录"为学校演示内容，替换为 jeecg 原生"系统公告"（真实接口）；
// 我的事务接 /team/approval 真实数据。
import { computed, onMounted, ref } from 'vue';
import { PhFileText, PhCurrencyCircleDollar, PhMegaphone } from '@phosphor-icons/vue';
import { useAuthStore } from '@/stores/auth';
import { listApprovals, type Approval } from '@/api/team';
import { listAnnouncements, type Announcement } from '@/api/announcement';

const auth = useAuthStore();
const activeTab = ref<'todo' | 'mine'>('mine');

const todoList = ref<Approval[]>([]);
const mineList = ref<Approval[]>([]);
const announcements = ref<Announcement[]>([]);
const shownItems = computed(() => (activeTab.value === 'mine' ? mineList.value : todoList.value).slice(0, 5));

onMounted(async () => {
  try {
    const [todo, mine, ann] = await Promise.all([
      listApprovals({ status: 'pending', pageSize: 50 }),
      listApprovals({ applicantId: auth.userInfo.id, pageSize: 50 }),
      listAnnouncements(),
    ]);
    todoList.value = todo.result.records;
    mineList.value = mine.result.records;
    announcements.value = ann.slice(0, 5);
  } catch {
    // 首页聚合失败不阻塞页面，保持空态
  }
});

const ITEM_ICONS = [PhFileText, PhCurrencyCircleDollar, PhMegaphone];

const STATUS_TEXT: Record<Approval['status'], string> = { pending: '审批中', approved: '已通过', rejected: '已拒绝' };
const STATUS_CLS: Record<Approval['status'], string> = { pending: 'tag-warning', approved: 'tag-success', rejected: 'tag-danger' };
</script>

<template>
  <div>
    <div class="greeting-area">
      <div>
        <h2 style="font-size: 24px; font-weight: 600; margin-bottom: 8px">你好，{{ auth.displayName }}</h2>
        <p style="font-size: 14px; color: var(--text-muted)">在这里跟进申请进度、查看公告与团队动态。</p>
      </div>
    </div>
    <div class="dashboard-grid">
      <div class="card">
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
          <span class="card-title">我的事务</span>
          <router-link to="/tools/approval" style="color: var(--primary); font-size: 13px">审批中心</router-link>
        </div>
        <div class="tabs">
          <div class="tab-item" :class="{ active: activeTab === 'todo' }" @click="activeTab = 'todo'">
            待我审批 <span class="num">{{ todoList.length }}</span>
          </div>
          <div class="tab-item" :class="{ active: activeTab === 'mine' }" @click="activeTab = 'mine'">
            我的申请 <span class="num">{{ mineList.length }}</span>
          </div>
        </div>
        <div v-if="!shownItems.length" style="color: var(--text-muted); font-size: 13px; padding: 12px 0">暂无数据</div>
        <div v-for="(item, i) in shownItems" :key="item.id" class="list-item">
          <div class="item-icon">
            <component :is="ITEM_ICONS[i % ITEM_ICONS.length]" :size="20" />
          </div>
          <div class="item-content">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-sub">{{ item.category }} · {{ item.code }}</div>
          </div>
          <div class="item-status">
            <span>{{ STATUS_TEXT[item.status] }}</span>
            <span class="tag" :class="STATUS_CLS[item.status]">{{ STATUS_TEXT[item.status] }}</span>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center">
          <span class="card-title">系统公告</span>
          <span style="color: var(--primary); font-size: 13px">全部</span>
        </div>
        <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px">来自管理员发布（sys/annountCement）</div>
        <div v-if="!announcements.length" style="color: var(--text-muted); font-size: 13px; padding: 16px 0">暂无公告</div>
        <div v-for="ann in announcements" :key="ann.id" class="ann-item">
          <PhMegaphone :size="16" class="ann-item__icon" />
          <div class="ann-item__body">
            <div class="ann-item__title">{{ ann.titile || '（无标题）' }}</div>
            <div class="ann-item__meta">{{ ann.sender || '系统' }} · {{ (ann.sendTime || '').slice(0, 10) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.greeting-area {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: 2fr 1.1fr;
  gap: 24px;
}
.ann-item {
  display: flex;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color-light);
  align-items: flex-start;
}
.ann-item:last-child {
  border-bottom: none;
}
.ann-item__icon {
  color: var(--primary);
  margin-top: 2px;
  flex-shrink: 0;
}
.ann-item__title {
  font-size: var(--font-size-base);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 320px;
}
.ann-item__meta {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-top: 3px;
}
</style>
