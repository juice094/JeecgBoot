<script setup lang="ts">
// 后勤管理 / 报修工单（管理组）—— owner: Ym3699，原型复现 mock
import { PhPlus } from '@phosphor-icons/vue';

const tickets = [
  { code: 'BX-1102', content: '空调不制冷，有异响', place: '教学楼 201', user: '陈雨潇', status: '处理中', tag: 'tag-warning', action: '查看进度' },
  { code: 'BX-1101', content: '饮水机漏水', place: '办公室 305', user: '房萍', status: '已完成', tag: 'tag-success', action: '评价' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">后勤管理 / 报修工单</span>
      <button class="btn btn-primary"><PhPlus :size="16" /> 提交报修</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>工单号</th><th>报修内容</th><th>报修地点</th><th>提交人</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in tickets" :key="t.code">
            <td>{{ t.code }}</td>
            <td>{{ t.content }}</td>
            <td>{{ t.place }}</td>
            <td>{{ t.user }}</td>
            <td><span class="tag" :class="t.tag">{{ t.status }}</span></td>
            <td><a class="btn-text">{{ t.action }}</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
