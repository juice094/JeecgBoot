<script setup lang="ts">
// 组织与租户 —— owner: Ym3699
// 职责：组织架构树、租户管理、部门维护
// 后端对接：/sys/sysDepart/*、/sys/tenant/list（admin token）
import { ref } from 'vue';

const depts = ref([
  { id: 'd1', name: '总经理办公室', children: [] as any[] },
  {
    id: 'd2',
    name: '研发中心',
    children: [
      { id: 'd21', name: '平台组', children: [] },
      { id: 'd22', name: '业务组', children: [] },
    ],
  },
  { id: 'd3', name: '产品部', children: [] as any[] },
]);

const tenants = ref([
  { id: 'tn1', name: '默认租户', status: '正常', expire: '永久' },
  { id: 'tn2', name: '试点租户', status: '试用', expire: '2026-12-31' },
]);
</script>

<template>
  <div class="org">
    <div class="card org__tree">
      <div class="card-title">组织架构</div>
      <ul class="tree">
        <template v-for="d in depts" :key="d.id">
          <li class="tree__node">
            <span class="tree__label">🏢 {{ d.name }}</span>
            <ul v-if="d.children.length" class="tree__children">
              <li v-for="c in d.children" :key="c.id">📁 {{ c.name }}</li>
            </ul>
          </li>
        </template>
      </ul>
      <p class="placeholder-tip">树数据来自 /sys/sysDepart/queryDepartTreeSync（issue #4 对接）</p>
    </div>

    <div class="card org__tenants">
      <div class="card-title">租户列表</div>
      <table class="table">
        <thead>
          <tr><th>租户</th><th>状态</th><th>有效期</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="t in tenants" :key="t.id">
            <td>{{ t.name }}</td>
            <td><span class="tag" :class="t.status === '正常' ? 'tag--success' : 'tag--warning'">{{ t.status }}</span></td>
            <td>{{ t.expire }}</td>
            <td><a href="javascript:;">管理</a></td>
          </tr>
        </tbody>
      </table>
      <p class="placeholder-tip" style="margin-top: 12px">租户接口 /sys/tenant/list（jeecg 3.9 原生，需 admin）</p>
    </div>
  </div>
</template>

<style scoped>
.org {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  align-items: start;
}

.tree,
.tree__children {
  list-style: none;
  padding-left: 0;
}

.tree__children {
  padding-left: 24px;
  margin-top: 6px;
}

.tree__node {
  padding: 6px 0;
  font-size: 14px;
}

.tree__children li {
  padding: 4px 0;
  color: var(--text-regular);
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
