<script setup lang="ts">
// 任务看板 —— owner: juice094
// 职责：团队任务/看板视图/指派/截止时间
// 表结构 + 后端接口见 issue #2；当前为带示例数据的看板骨架
// 业务枚举统一来自 constants/task（docs/13 §二.5，issue #11），禁止在组件内新增字面量
import { reactive, ref } from 'vue';
import { PRIORITY_CLASS, TAG_CLASS, TASK_STATUSES, type TaskPriority, type TaskStatusKey, type TaskTag } from '@/constants/task';

interface Task {
  id: string;
  title: string;
  assignee: string;
  due: string;
  tag: TaskTag;
  priority: TaskPriority;
}

const columns = reactive(TASK_STATUSES.map((s) => ({ key: s.key as TaskStatusKey, title: s.title, tasks: [] as Task[] })));

columns[0].tasks = [
  { id: 't1', title: '申请表单模板配置页', assignee: 'juice094', due: '11-20', tag: '需求', priority: '高' },
  { id: 't2', title: '任务表 DDL 评审', assignee: 'juice094', due: '11-18', tag: '开发', priority: '中' },
];
columns[1].tasks = [
  { id: 't3', title: '审批流状态机设计', assignee: 'juice094', due: '11-22', tag: '需求', priority: '高' },
];
columns[2].tasks = [{ id: 't4', title: 'jeecg 裁剪清单修订', assignee: 'juice094', due: '11-16', tag: '开发', priority: '低' }];

const newTitle = ref('');

function addTask(colKey: string) {
  const title = newTitle.value.trim();
  if (!title) return;
  const col = columns.find((c) => c.key === colKey);
  col?.tasks.push({ id: `t${Date.now()}`, title, assignee: 'juice094', due: '-', tag: '开发', priority: '中' });
  newTitle.value = '';
}

const tagClass = TAG_CLASS;
const prioClass = PRIORITY_CLASS;
</script>

<template>
  <div class="board">
    <div class="board__toolbar">
      <input v-model="newTitle" placeholder="输入任务标题，添加到「待办」" @keydown.enter="addTask('todo')" />
      <button @click="addTask('todo')">+ 新建任务</button>
    </div>
    <div class="board__columns">
      <div v-for="col in columns" :key="col.key" class="column">
        <div class="column__head">
          {{ col.title }}
          <span class="column__count">{{ col.tasks.length }}</span>
        </div>
        <div class="column__body">
          <div v-for="t in col.tasks" :key="t.id" class="task">
            <div class="task__title">{{ t.title }}</div>
            <div class="task__meta">
              <span class="tag" :class="tagClass[t.tag]">{{ t.tag }}</span>
              <span class="tag" :class="prioClass[t.priority]">{{ t.priority }}</span>
            </div>
            <div class="task__foot">
              <span>👤 {{ t.assignee }}</span>
              <span>📅 {{ t.due }}</span>
            </div>
          </div>
          <p v-if="!col.tasks.length" class="column__empty">暂无任务</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.board {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
}

.board__toolbar {
  display: flex;
  gap: 10px;
}

.board__toolbar input {
  flex: 1;
  height: 36px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 0 12px;
  font-size: 14px;
  outline: none;
}

.board__toolbar button {
  border: none;
  background: var(--primary);
  color: #fff;
  padding: 0 18px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}

.board__columns {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.column {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.column__head {
  padding: 12px 16px;
  font-weight: 600;
  border-bottom: 1px solid var(--border-color-light);
  display: flex;
  justify-content: space-between;
}

.column__count {
  color: var(--text-muted);
  font-weight: 400;
}

.column__body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: var(--bg-body);
}

.column__empty {
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
  padding: 24px 0;
}

.task {
  background: #fff;
  border-radius: var(--radius-sm);
  padding: 12px;
  box-shadow: var(--shadow-card);
}

.task__title {
  font-size: 14px;
  margin-bottom: 8px;
}

.task__meta {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.task__foot {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
