<script setup lang="ts">
// 仪表盘（概览）——公共层页面，聚合各模块摘要
// 数据暂为示例；后端聚合接口由后续 issue 定义（juice094 裁决）
import { ref } from 'vue';
import { PhFileText, PhCurrencyCircleDollar } from '@phosphor-icons/vue';

const activeTab = ref<'todo' | 'mine'>('mine');

const myItems = [
  { id: 1, icon: 'file', title: '关于校企合作框架协议', sub: '用章申请 · 11月10日', status: '流程已完成', tag: '已通过', tagType: 'tag-success' },
  { id: 2, icon: 'money', title: '实验室招标服务费', sub: '经费申请 · 11月10日', status: '流程已完成', tag: '已通过', tagType: 'tag-success' },
];

const attendance = { absent: 2, late: 2, early: 0, day: 13, month: '11月13日', time: '08:31', pending: true };
</script>

<template>
  <div>
    <div class="greeting-area">
      <div>
        <h2 style="font-size: 24px; font-weight: 600; margin-bottom: 8px">下午好，{{ '同事' }}</h2>
        <p style="font-size: 14px; color: var(--text-muted)">在这里跟进申请进度、查看日程与本月考勤。</p>
      </div>
    </div>
    <div class="dashboard-grid">
      <div class="card">
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
          <span class="card-title">我的事务</span>
          <span style="color: var(--primary); font-size: 13px; cursor: pointer">审批中心</span>
        </div>
        <div class="tabs">
          <div class="tab-item" :class="{ active: activeTab === 'todo' }" @click="activeTab = 'todo'">待我审批 <span class="num">0</span></div>
          <div class="tab-item" :class="{ active: activeTab === 'mine' }" @click="activeTab = 'mine'">我的申请 <span class="num">14</span></div>
        </div>
        <div v-for="item in myItems" :key="item.id" class="list-item">
          <div class="item-icon">
            <PhFileText v-if="item.icon === 'file'" :size="20" />
            <PhCurrencyCircleDollar v-else :size="20" />
          </div>
          <div class="item-content">
            <div class="item-title">{{ item.title }}</div>
            <div class="item-sub">{{ item.sub }}</div>
          </div>
          <div class="item-status">
            <span>{{ item.status }}</span>
            <span class="tag" :class="item.tagType">{{ item.tag }}</span>
          </div>
        </div>
      </div>
      <div class="card">
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center">
          <span class="card-title">考勤记录</span>
          <span style="color: var(--primary); font-size: 13px; cursor: pointer">查看全部</span>
        </div>
        <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px">2026年11月 · 近4个工作日</div>
        <div class="attendance-summary">
          <div class="att-stat"><div class="num danger">{{ attendance.absent }}</div><div class="label">缺勤</div></div>
          <div class="att-stat"><div class="num warning">{{ attendance.late }}</div><div class="label">迟到</div></div>
          <div class="att-stat"><div class="num">{{ attendance.early }}</div><div class="label">早退</div></div>
        </div>
        <div class="attendance-record">
          <div class="record-date">
            <div class="day">{{ attendance.day }}</div>
            <div class="month">{{ attendance.month }}</div>
          </div>
          <div class="record-time">{{ attendance.time }}</div>
          <span class="tag" :class="attendance.pending ? 'tag-warning' : 'tag-success'">{{ attendance.pending ? '待签退' : '已签退' }}</span>
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
</style>
