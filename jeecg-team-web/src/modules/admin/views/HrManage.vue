<script setup lang="ts">
// 人事管理（管理组）—— owner: Ym3699
// 后端：组织/人员数据唯一出口（issue #4/#5），当前为原型复现 mock
import { ref } from 'vue';
import { PhBuildings, PhUsersThree, PhUserMinus } from '@phosphor-icons/vue';

const activeDept = ref('总经办');

const stats = [
  { title: '部门总数', value: '16', icon: PhBuildings, bg: '', color: '' },
  { title: '员工总数', value: '67', extra: '未分配1人', icon: PhUsersThree, bg: '#e6f7eb', color: 'var(--success)' },
  { title: '未设负责人', value: '2', icon: PhUserMinus, bg: '#fdf6ec', color: 'var(--warning)' },
];

const deptTree = [
  {
    name: '总经办', count: 2, children: [
      { name: '外联部', count: 1 },
      { name: '市场部', count: 6, active: true },
      { name: '职业教育业务部', count: 0 },
    ],
  },
];

const members = [
  { name: '常鹏亮', role: '厨师长', phone: '13991367577', status: '在职' },
];
</script>

<template>
  <div class="card">
    <h2 style="font-size: 20px; margin-bottom: 8px">人事管理 / 部门与员工</h2>
    <p style="font-size: 14px; color: var(--text-muted); margin-bottom: 24px">组织架构、员工档案与账号管理</p>

    <div class="stats-grid">
      <div v-for="s in stats" :key="s.title" class="stat-card">
        <div class="stat-icon" :style="{ background: s.bg || '', color: s.color || '' }">
          <component :is="s.icon" :size="24" />
        </div>
        <div class="stat-info">
          <div class="title">{{ s.title }}</div>
          <div class="value">{{ s.value }} <span v-if="s.extra" style="font-size: 12px; font-weight: normal; color: var(--text-muted)">{{ s.extra }}</span></div>
        </div>
      </div>
    </div>

    <div class="org-layout">
      <div class="dept-pane">
        <input type="text" class="form-control dept-search" placeholder="搜索部门、负责人或职责" />
        <div class="dept-head"><span>部门名称</span><span>人数</span></div>
        <div class="dept-body">
          <template v-for="dept in deptTree" :key="dept.name">
            <div class="dept-root">
              <PhBuildings :size="16" />
              <span>{{ dept.name }}</span>
              <span class="dept-count">{{ dept.count }}人</span>
            </div>
            <div class="dept-children">
              <div
                v-for="child in dept.children"
                :key="child.name"
                class="dept-child"
                :class="{ 'dept-child--active': child.active }"
                @click="activeDept = child.name"
              >
                <span>{{ child.name }}</span>
                <span>{{ child.count }}人</span>
              </div>
            </div>
          </template>
        </div>
      </div>
      <div class="member-pane">
        <div class="member-pane__head">
          <div class="dept-badge"><PhBuildings :size="20" /></div>
          <div>
            <div style="font-size: 16px; font-weight: 600">
              {{ activeDept }} <span class="tag tag-success" style="font-size: 12px">启用中</span>
            </div>
            <div style="font-size: 12px; color: var(--text-muted)">顶级部门</div>
          </div>
        </div>
        <div class="tabs" style="margin-bottom: 12px">
          <div class="tab-item active" style="padding-bottom: 8px">在职 <span class="num">2</span></div>
          <div class="tab-item" style="padding-bottom: 8px">休假 <span class="num">0</span></div>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>员工</th><th>岗位/角色</th><th>手机号</th><th>状态</th></tr>
            </thead>
            <tbody>
              <tr v-for="m in members" :key="m.name">
                <td>
                  <div style="display: flex; align-items: center; gap: 8px">
                    <div class="avatar" style="width: 24px; height: 24px; font-size: 12px; background: var(--primary-light); color: var(--primary)">{{ m.name[0] }}</div>
                    {{ m.name }}
                  </div>
                </td>
                <td>{{ m.role }}</td>
                <td>{{ m.phone }}</td>
                <td><span class="tag tag-success">{{ m.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.org-layout {
  display: flex;
  border-top: 1px solid var(--border-color);
  padding-top: 24px;
}
.dept-pane {
  width: 300px;
  border-right: 1px solid var(--border-color);
  padding-right: 20px;
}
.dept-search {
  background: var(--bg-body);
  border-color: transparent;
  margin-bottom: 16px;
}
.dept-head {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--text-muted);
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color-light);
}
.dept-body {
  padding: 12px 0;
  font-size: 14px;
}
.dept-root {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--primary);
  font-weight: 500;
}
.dept-count {
  margin-left: auto;
  font-weight: normal;
  color: var(--text-muted);
}
.dept-children {
  margin-top: 15px;
  margin-left: 16px;
  border-left: 1px solid var(--border-color);
  padding-left: 16px;
}
.dept-child {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 13px;
  cursor: pointer;
  color: var(--text-regular);
}
.dept-child span:last-child {
  color: var(--text-muted);
}
.dept-child--active {
  color: var(--primary);
}
.dept-child--active span:last-child {
  color: var(--primary);
}
.member-pane {
  flex: 1;
  padding-left: 24px;
}
.member-pane__head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.dept-badge {
  width: 40px;
  height: 40px;
  background: var(--primary-light);
  color: var(--primary);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
</style>
