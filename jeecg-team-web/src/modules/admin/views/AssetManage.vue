<script setup lang="ts">
// 行政管理 / 固定资产（管理组）—— owner: Ym3699，原型复现 mock
import { ref } from 'vue';
import { PhPlus } from '@phosphor-icons/vue';

const keyword = ref('');

const assets = [
  { code: 'ZC-2023-001', name: 'MacBook Pro 14寸', dept: '市场部', user: '李国成', status: '正常使用', tag: 'tag-success', action: '查看' },
  { code: 'ZC-2023-002', name: '戴尔显示器 27寸', dept: '教学部', user: '黄茜茜', status: '正常使用', tag: 'tag-success', action: '查看' },
  { code: 'ZC-2023-045', name: '投影仪 Epson', dept: '后勤部', user: '-', status: '闲置中', tag: 'tag-info', action: '领用' },
];
</script>

<template>
  <div class="card">
    <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <span class="card-title">行政管理 / 固定资产管理</span>
      <button class="btn btn-primary"><PhPlus :size="16" /> 新增资产</button>
    </div>
    <div class="search-bar">
      <input v-model="keyword" type="text" class="search-input" placeholder="搜索资产编号/名称..." />
      <button class="btn btn-default">查询</button>
    </div>
    <div class="table-wrap">
      <table class="data-table">
        <thead>
          <tr><th>资产编号</th><th>资产名称</th><th>所属部门</th><th>当前使用人</th><th>状态</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in assets" :key="a.code">
            <td>{{ a.code }}</td>
            <td>{{ a.name }}</td>
            <td>{{ a.dept }}</td>
            <td>{{ a.user }}</td>
            <td><span class="tag" :class="a.tag">{{ a.status }}</span></td>
            <td><a class="btn-text">{{ a.action }}</a></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
