<script setup lang="ts">
// 市场活动 / 招生活动（业务组）—— owner: juice094，原型复现 mock
import { PhPlus } from '@phosphor-icons/vue';

const activities = [
  { name: '2026秋季大数据专业招生宣讲会', time: '2026-11-20 14:00', owner: '李国成', signup: '120人', status: '报名中', tag: 'tag-warning', action: '查看详情' },
  { name: '校园开放日暨专业体验活动', time: '2026-10-15 09:00', owner: '黄茜茜', signup: '350人', status: '已结束', tag: 'tag-success', action: '活动复盘' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">市场活动 / 招生活动列表</span>
      <button class="btn btn-primary"><PhPlus :size="16" /> 创建活动</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>活动名称</th><th>活动时间</th><th>负责人</th><th>报名人数</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in activities" :key="a.name">
            <td>{{ a.name }}</td>
            <td>{{ a.time }}</td>
            <td>{{ a.owner }}</td>
            <td>{{ a.signup }}</td>
            <td><span class="tag" :class="a.tag">{{ a.status }}</span></td>
            <td><a class="btn-text">{{ a.action }}</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
