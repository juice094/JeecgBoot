<script setup lang="ts">
// 人事管理 / 部门与员工（管理组）—— owner: Ym3699
// 数据（2026-11-17）：jeecg 原生 sys 接口先接入——
//   部门树 /sys/sysDepart/queryTreeList；成员 /sys/user/list。
//   Ym3699 的自建表立项后可替换/扩展；选中部门精确过滤成员待其按 orgCode 接。
import { computed, onMounted, ref } from 'vue';
import { PhBuildings, PhUsersThree, PhUserMinus } from '@phosphor-icons/vue';
import { listDepartTree, listUsers, type DepartNode, type SysUser } from '@/api/sys';
import DeptNode from '../components/DeptNode.vue';

const tree = ref<DepartNode[]>([]);
const users = ref<SysUser[]>([]);
const totalUsers = ref(0);
const activeDept = ref('');
const loading = ref(false);
const errorMsg = ref('');

function flatten(nodes: DepartNode[]): DepartNode[] {
  return nodes.flatMap((n) => [n, ...flatten(n.children ?? [])]);
}

const flat = computed(() => flatten(tree.value));
// SysDepartTreeModel 无负责人字段，统计不到时显示 '-'，不自造假数
const noLeader = computed(() => {
  const withField = flat.value.filter((n) => 'leaderName' in (n as Record<string, unknown>));
  if (!withField.length) return null;
  return withField.filter((n) => !n.leaderName).length;
});

const stats = computed(() => [
  { title: '部门总数', value: String(flat.value.length || '-'), icon: PhBuildings, bg: '', color: '' },
  { title: '员工总数', value: String(totalUsers.value || '-'), icon: PhUsersThree, bg: '#e6f7eb', color: 'var(--success)' },
  { title: '未设负责人', value: noLeader.value === null ? '-' : String(noLeader.value), icon: PhUserMinus, bg: '#fdf6ec', color: 'var(--warning)' },
]);

onMounted(async () => {
  loading.value = true;
  try {
    const [t, u] = await Promise.all([listDepartTree(), listUsers({ pageNo: 1, pageSize: 100 })]);
    tree.value = t;
    users.value = u.result.records;
    totalUsers.value = u.result.total;
    activeDept.value = t[0]?.departName ?? '';
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败';
  } finally {
    loading.value = false;
  }
});

function selectDept(node: DepartNode) {
  activeDept.value = node.departName ?? '';
  // ponytail: 选中部门按 orgCode 精确过滤待 Ym3699 接；当前展示全量成员
}

const statusText = (s?: number) => (s === 1 ? '在职' : s === 2 ? '冻结' : '未知');
const statusCls = (s?: number) => (s === 1 ? 'tag-success' : 'tag-danger');
</script>

<template>
  <div class="card">
    <h2 style="font-size: 20px; margin-bottom: 8px">人事管理 / 部门与员工</h2>
    <p style="font-size: 14px; color: var(--text-muted); margin-bottom: 24px">
      组织架构与成员（数据源：sys_user / sys_depart，管理组唯一出口）
    </p>

    <div class="stats-grid">
      <div v-for="s in stats" :key="s.title" class="stat-card">
        <div class="stat-icon" :style="{ background: s.bg || '', color: s.color || '' }">
          <component :is="s.icon" :size="24" />
        </div>
        <div class="stat-info">
          <div class="title">{{ s.title }}</div>
          <div class="value">{{ s.value }}</div>
        </div>
      </div>
    </div>

    <div class="org-layout">
      <div class="dept-pane">
        <div class="dept-head"><span>部门名称</span></div>
        <div v-if="loading" style="color: var(--text-muted); font-size: 13px; padding: 12px 0">加载中…</div>
        <DeptNode v-for="node in tree" :key="node.id" :node="node" :depth="0" :active-name="activeDept" @select="selectDept" />
      </div>
      <div class="member-pane">
        <div class="member-pane__head">
          <div class="dept-badge"><PhBuildings :size="20" /></div>
          <div>
            <div style="font-size: 16px; font-weight: 600">{{ activeDept || '全部成员' }}</div>
            <div style="font-size: 12px; color: var(--text-muted)">共 {{ users.length }} 人（本页）</div>
          </div>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr><th>员工</th><th>登录名</th><th>手机号</th><th>状态</th></tr>
            </thead>
            <tbody>
              <tr v-if="errorMsg">
                <td colspan="4" style="color: var(--danger)">{{ errorMsg }}</td>
              </tr>
              <tr v-for="m in users" :key="m.id">
                <td>
                  <div style="display: flex; align-items: center; gap: 8px">
                    <div class="avatar" style="width: 24px; height: 24px; font-size: 12px; background: var(--primary-light); color: var(--primary)">
                      {{ (m.realname || m.username || '?')[0] }}
                    </div>
                    {{ m.realname || m.username }}
                  </div>
                </td>
                <td>{{ m.username }}</td>
                <td>{{ m.phone || '-' }}</td>
                <td><span class="tag" :class="statusCls(m.status)">{{ statusText(m.status) }}</span></td>
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
  max-height: 560px;
  overflow-y: auto;
}
.dept-head {
  font-size: 13px;
  color: var(--text-muted);
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color-light);
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
}
</style>
