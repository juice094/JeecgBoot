<script setup lang="ts">
// 审批中心（工具组）—— owner: juice094
// 待办/已办/我发起 三页签；后端对接 /team/approval/list（按 tab 过滤）
import { ref } from 'vue';

type Tab = 'todo' | 'done' | 'mine';
const activeTab = ref<Tab>('todo');

const tabs: { key: Tab; label: string; num?: number }[] = [
  { key: 'todo', label: '待我审批', num: 34 },
  { key: 'done', label: '我已审批' },
  { key: 'mine', label: '我发起的' },
];

const todoList = [
  { code: 'OA-20231113-002', applicant: '陈雨潇', type: '请假申请', title: '关于11月15日事假的申请', time: '11-13 10:30' },
  { code: 'OA-20231113-005', applicant: '房萍', type: '费用报销', title: '10月份市场推广差旅费报销', time: '11-13 09:15' },
  { code: 'OA-20231112-018', applicant: '张老师', type: '采购申请', title: '机房302电脑设备采购申请', time: '11-12 16:45' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">审批中心 / 待办事项</span>
    </div>
    <div class="tabs">
      <div
        v-for="t in tabs"
        :key="t.key"
        class="tab-item"
        :class="{ active: activeTab === t.key }"
        @click="activeTab = t.key"
      >
        {{ t.label }} <span v-if="t.num" class="num">{{ t.num }}</span>
      </div>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>申请单号</th><th>申请人</th><th>申请类型</th><th>事项标题</th><th>提交时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="item in todoList" :key="item.code">
            <td>{{ item.code }}</td>
            <td>{{ item.applicant }}</td>
            <td>{{ item.type }}</td>
            <td>{{ item.title }}</td>
            <td>{{ item.time }}</td>
            <td><button class="btn btn-primary btn--sm">审批</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.btn--sm {
  padding: 4px 10px;
  font-size: var(--font-size-sm);
}
</style>
