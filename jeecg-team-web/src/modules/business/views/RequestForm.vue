<script setup lang="ts">
// 日常申请（工具组）—— owner: juice094
// 原型复现：分类列表 + 申请表单 + 审批流时间线
// 后端：轻量状态机审批（issue 待建，Flyway/team_approval 骨架由 Kimi Code 先行）
import { reactive, ref } from 'vue';
import { PhUploadSimple, PhInfo } from '@phosphor-icons/vue';

const categories = [
  { group: '考勤与假期', items: ['请假申请', '出差申请', '外出申请', '补卡申请', '加班申请'] },
  { group: '费用与财务', items: ['费用报销', '采购申请'] },
];
const activeCategory = ref('加班申请');

const form = reactive({ title: '关于国庆期间迎新筹备加班申请', reason: '', start: '', end: '' });
const titleError = ref(false);

const flowSteps = [
  { name: '贺致远', role: '部门主管 · 教学总监' },
  { name: '黄茜茜', role: '常务副总经理' },
  { name: '王立柱', role: '总经理' },
];

function submit() {
  titleError.value = !form.title.trim();
  if (titleError.value) return;
  // TODO: POST /team/approval/add（后端骨架就绪后对接）
}
</script>

<template>
  <div class="request-layout">
    <div class="card" style="padding: 10px">
      <template v-for="cat in categories" :key="cat.group">
        <div class="category-group">{{ cat.group }}</div>
        <div
          v-for="item in cat.items"
          :key="item"
          class="category-item"
          :class="{ 'category-item--active': item === activeCategory }"
          @click="activeCategory = item"
        >
          {{ item }}
        </div>
      </template>
    </div>

    <div class="card form-area">
      <div class="form-header">
        <h3>{{ activeCategory }}</h3>
        <p>非工作时间加班含周末节假日</p>
      </div>
      <div class="form-group">
        <div class="form-label">事项标题 <span class="req">*</span></div>
        <input v-model="form.title" type="text" class="form-control" />
        <div v-if="titleError" class="field-error">请填写此字段。</div>
      </div>
      <div class="form-group">
        <div class="form-label">申请事由 <span class="req">*</span></div>
        <textarea v-model="form.reason" class="form-control" placeholder="请说明申请背景及必要性..."></textarea>
      </div>
      <div class="tip-box"><PhInfo :size="16" /> 工作日 9:00-18:00 为正常上班时段；在此时间段以外工作请提交本申请。</div>
      <div class="flex-row">
        <div class="form-group">
          <div class="form-label">开始日期 <span class="req">*</span></div>
          <input v-model="form.start" type="text" class="form-control" placeholder="年-月-日" />
        </div>
        <div class="form-group">
          <div class="form-label">结束日期 <span class="req">*</span></div>
          <input v-model="form.end" type="text" class="form-control" placeholder="年-月-日" />
        </div>
      </div>
      <div class="form-group">
        <div class="form-label">附件</div>
        <div class="upload-box">
          <PhUploadSimple :size="32" style="color: var(--text-muted); margin-bottom: 8px" />
          <p>点击上传 或拖拽文件到此处</p>
        </div>
      </div>
      <div class="form-footer">
        <button class="btn btn-default">清空</button>
        <button class="btn btn-primary" @click="submit">提交申请</button>
      </div>
    </div>

    <div class="card flow-area">
      <div class="flow-header">审批流程</div>
      <div class="flow-desc">默认按部门主管 → 常务副总经理 → 总经理审批</div>
      <div class="flow-timeline">
        <div v-for="(step, i) in flowSteps" :key="step.name" class="flow-step active">
          <div class="step-icon">{{ i + 1 }}</div>
          <div class="step-content">
            <div class="step-name">
              {{ step.name }}
              <span class="step-change">更换</span>
            </div>
            <div class="step-role">{{ step.role }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.request-layout {
  display: grid;
  grid-template-columns: 220px 1fr 300px;
  gap: 24px;
  height: calc(100vh - 120px);
}
.category-group {
  padding: 10px 16px;
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  margin-bottom: 8px;
  margin-top: 10px;
}
.category-group:first-child {
  margin-top: 0;
}
.category-item {
  padding: 12px 16px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: var(--font-size-base);
  margin-bottom: 4px;
  color: var(--text-regular);
  transition: 0.2s;
}
.category-item:hover {
  background: var(--bg-body);
}
.category-item--active {
  background: var(--primary-light);
  color: var(--primary);
  font-weight: 500;
}
.form-area {
  padding: 24px;
  overflow-y: auto;
}
.form-header {
  margin-bottom: 24px;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 16px;
}
.form-header h3 {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}
.form-header p {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}
.flow-area {
  padding: 24px;
  overflow-y: auto;
}
.step-change {
  font-size: var(--font-size-sm);
  color: var(--primary);
  float: right;
  cursor: pointer;
}
</style>
