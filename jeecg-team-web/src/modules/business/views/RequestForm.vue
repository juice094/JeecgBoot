<script setup lang="ts">
// 表单与审批 —— owner: juice094
// 职责：日常申请表单（请假/报销等模板）、审批流转、我的申请/待办列表
// 长期：填报面接 jeecg Online 表单引擎；审批流 = 自研轻量状态机（issue #3）
import { ref } from 'vue';

const templates = ref([
  { key: 'leave', name: '请假申请', desc: '事假 / 病假 / 年假', icon: '🏖️' },
  { key: 'expense', name: '费用报销', desc: '差旅 / 办公 / 招待', icon: '🧾' },
  { key: 'general', name: '通用申请', desc: '自定义事由与说明', icon: '📝' },
]);

const myRequests = ref([
  { id: 'r1', title: '请假申请（年假 2 天）', status: '审批中', submitter: 'admin', time: '11-16 10:20' },
  { id: 'r2', title: '费用报销（差旅 ¥860）', status: '已通过', submitter: 'admin', time: '11-14 15:42' },
]);

const statusClass: Record<string, string> = { 审批中: 'tag--warning', 已通过: 'tag--success', 已驳回: 'tag--info' };
</script>

<template>
  <div class="request">
    <div class="card">
      <div class="card-title">发起申请</div>
      <div class="templates">
        <div v-for="t in templates" :key="t.key" class="template">
          <div class="template__icon">{{ t.icon }}</div>
          <div class="template__name">{{ t.name }}</div>
          <div class="template__desc">{{ t.desc }}</div>
          <button class="template__btn">填写</button>
        </div>
      </div>
      <p class="placeholder-tip" style="margin-top: 12px">
        模板由管理员在 Online 表单设计器中配置；此处为统一填报入口（M3 接入真实表单渲染）。
      </p>
    </div>

    <div class="card">
      <div class="card-title">我的申请</div>
      <table class="table">
        <thead>
          <tr><th>申请单</th><th>状态</th><th>提交人</th><th>提交时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="r in myRequests" :key="r.id">
            <td>{{ r.title }}</td>
            <td><span class="tag" :class="statusClass[r.status]">{{ r.status }}</span></td>
            <td>{{ r.submitter }}</td>
            <td>{{ r.time }}</td>
            <td><a href="javascript:;">详情</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.request {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.templates {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
}

.template {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 18px;
  text-align: center;
  transition: border-color 0.2s;
}

.template:hover {
  border-color: var(--primary);
}

.template__icon {
  font-size: 30px;
}

.template__name {
  font-weight: 600;
  margin: 8px 0 4px;
}

.template__desc {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 12px;
}

.template__btn {
  border: 1px solid var(--primary);
  background: var(--primary-light);
  color: var(--primary);
  padding: 5px 22px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.table th,
.table td {
  text-align: left;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-color-light);
}

.table th {
  color: var(--text-regular);
  font-weight: 600;
  background: var(--bg-body);
}
</style>
