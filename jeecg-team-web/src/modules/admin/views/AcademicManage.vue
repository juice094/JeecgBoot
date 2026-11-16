<script setup lang="ts">
// 教务管理 / 课程排期（管理组）—— owner: Ym3699，原型复现 mock
import { PhPlus } from '@phosphor-icons/vue';

const courses = [
  { name: '大数据开发基础 (ZX2026-09)', teacher: '李国成', room: '多媒体教室A', time: '周一 09:00 - 11:30', size: '45人' },
  { name: '前端框架Vue实战', teacher: '张老师', room: '机房 302', time: '周三 14:00 - 16:30', size: '38人' },
  { name: '职业规划与就业指导', teacher: '王立柱', room: '大礼堂', time: '周五 10:00 - 12:00', size: '200人' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">教务管理 / 课程排期</span>
      <button class="btn btn-primary"><PhPlus :size="16" /> 新建排课</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>课程名称</th><th>授课教师</th><th>上课教室</th><th>上课时间</th><th>班级人数</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in courses" :key="c.name">
            <td>{{ c.name }}</td>
            <td>{{ c.teacher }}</td>
            <td>{{ c.room }}</td>
            <td>{{ c.time }}</td>
            <td>{{ c.size }}</td>
            <td><a class="btn-text">编辑</a> <a class="btn-text">导出名单</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
