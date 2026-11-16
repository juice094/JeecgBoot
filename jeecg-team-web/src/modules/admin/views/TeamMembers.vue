<script setup lang="ts">
// 团队成员 —— owner: Ym3699
// 职责：成员列表、角色分配、成员增删改查
// 后端对接：/sys/user/list、/sys/user/add|edit（需 admin token）
import { ref } from 'vue';

const keyword = ref('');
const members = ref([
  { id: 'u1', username: 'admin', realname: '管理员', dept: '总经理办公室', role: '系统管理员', status: '启用' },
  { id: 'u2', username: 'qingyanjunzi', realname: '青言', dept: '研发中心/平台组', role: '开发', status: '启用' },
  { id: 'u3', username: 'Ym3699', realname: 'Ym', dept: '研发中心/业务组', role: '开发', status: '启用' },
]);
</script>

<template>
  <div class="card">
    <div class="card-title">团队成员</div>
    <div class="members__toolbar">
      <input v-model="keyword" placeholder="按用户名 / 姓名搜索" />
      <button class="primary">+ 新增成员</button>
    </div>
    <table class="table">
      <thead>
        <tr><th>用户名</th><th>姓名</th><th>部门</th><th>角色</th><th>状态</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="m in members" :key="m.id">
          <td>{{ m.username }}</td>
          <td>{{ m.realname }}</td>
          <td>{{ m.dept }}</td>
          <td><span class="tag tag--info">{{ m.role }}</span></td>
          <td><span class="tag" :class="m.status === '启用' ? 'tag--success' : 'tag--warning'">{{ m.status }}</span></td>
          <td>
            <a href="javascript:;">编辑</a> · <a href="javascript:;">禁用</a>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="placeholder-tip" style="margin-top: 12px">示例数据；联调时换 /sys/user/list（带 X-Access-Token，issue #5）</p>
  </div>
</template>

<style scoped>
.members__toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.members__toolbar input {
  width: 260px;
  height: 34px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 0 12px;
  font-size: 14px;
  outline: none;
}

.members__toolbar .primary {
  border: none;
  background: var(--primary);
  color: #fff;
  padding: 0 16px;
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
