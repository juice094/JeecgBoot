<script setup lang="ts">
// 学生服务 / 学籍异动申请（业务组）—— owner: juice094，原型复现 mock
const applications = [
  { code: 'XJ-20231101', student: '张三', type: '休学', time: '2026-11-01', status: '审批中', tag: 'tag-warning', action: '催办' },
  { code: 'XJ-20231025', student: '李四', type: '转专业', time: '2026-10-25', status: '已通过', tag: 'tag-success', action: '查看' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="margin-bottom: 16px">
      <span class="card-title">学生服务 / 学籍异动申请</span>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>申请单号</th><th>学生姓名</th><th>异动类型</th><th>申请时间</th><th>审批状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in applications" :key="a.code">
            <td>{{ a.code }}</td>
            <td>{{ a.student }}</td>
            <td>{{ a.type }}</td>
            <td>{{ a.time }}</td>
            <td><span class="tag" :class="a.tag">{{ a.status }}</span></td>
            <td><a class="btn-text">{{ a.action }}</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
