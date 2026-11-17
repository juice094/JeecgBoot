<script setup lang="ts">
// 职业教育 / 培训班管理（业务组）—— owner: juice094，原型复现 mock
import { PhPlus } from '@phosphor-icons/vue';

const classes = [
  { name: '2026级大数据01班', major: '大数据技术', start: '2026-09-01', teacher: '张老师', status: '正常教学' },
  { name: 'Java全栈开发脱产班', major: '软件开发', start: '2026-10-10', teacher: '李老师', status: '正常教学' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">职业教育 / 培训班管理</span>
      <button class="btn btn-primary"><PhPlus :size="16" /> 新建培训班级</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>班级名称</th><th>专业方向</th><th>开班日期</th><th>班主任</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in classes" :key="c.name">
            <td>{{ c.name }}</td>
            <td>{{ c.major }}</td>
            <td>{{ c.start }}</td>
            <td>{{ c.teacher }}</td>
            <td><span class="tag tag-success">{{ c.status }}</span></td>
            <td><a class="btn-text">学员名单</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
