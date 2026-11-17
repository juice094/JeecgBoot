<script setup lang="ts">
// 日常申请（工具组）—— owner: juice094
// 原型复现：分类列表 + 申请表单 + 审批流时间线
// 后端：轻量状态机审批（issue 待建，Flyway/team_approval 骨架由 Kimi Code 先行）
import { reactive, ref } from 'vue';
import { PhUploadSimple, PhInfo } from '@phosphor-icons/vue';
import { submitApproval, PILOT_APPROVAL_STEPS } from '@/api/team';

const categories = [
  { group: '考勤与假期', items: ['请假申请', '出差申请', '外出申请', '补卡申请', '加班申请'] },
  { group: '费用与财务', items: ['费用报销', '采购申请'] },
];
const activeCategory = ref('加班申请');

const form = reactive({ title: '', reason: '', start: '', end: '' });
const titleError = ref(false);
const submitting = ref(false);
const submitMsg = ref('');
const submitErr = ref(false);

// 试点审批链（与后端守卫姓名比对口径一致）；模板体系延后后改由模板下发
const flowSteps = [...PILOT_APPROVAL_STEPS];

async function submit() {
  titleError.value = !form.title.trim();
  submitMsg.value = '';
  if (titleError.value || submitting.value) return;
  submitting.value = true;
  try {
    const res: any = await submitApproval({
      category: activeCategory.value,
      title: form.title.trim(),
      reason: form.reason.trim() || undefined,
      startDate: form.start || undefined,
      endDate: form.end || undefined,
    });
    submitMsg.value = `已提交，单号 ${res?.result?.code ?? ''}，进入审批流（可在审批中心跟进）`;
    submitErr.value = false;
    form.title = '';
    form.reason = '';
    form.start = '';
    form.end = '';
  } catch (e: any) {
    submitMsg.value = e.message || '提交失败';
    submitErr.value = true;
  } finally {
    submitting.value = false;
  }
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
        <button class="btn btn-default" @click="form.title = ''; form.reason = ''; form.start = ''; form.end = ''; submitMsg = ''">清空</button>
        <span v-if="submitMsg" class="submit-msg" :class="{ 'submit-msg--err': submitErr }">{{ submitMsg }}</span>
        <button class="btn btn-primary" :disabled="submitting" @click="submit">{{ submitting ? '提交中…' : '提交申请' }}</button>
      </div>
    </div>

    <div class="card flow-area">
      <div class="flow-header">审批流程</div>
      <div class="flow-desc">按 部门主管 → 副总经理 两级审批（试点固定链，人员选择器接入后可调整）</div>
      <div class="flow-timeline">
        <div v-for="(step, i) in flowSteps" :key="step.approverName" class="flow-step active">
          <div class="step-icon">{{ i + 1 }}</div>
          <div class="step-content">
            <div class="step-name">{{ step.approverName }}</div>
            <div class="step-role">{{ step.approverRole }}</div>
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
.submit-msg--err {
  color: var(--danger);
}
.submit-msg {
  margin-right: auto;
  font-size: var(--font-size-sm);
  color: var(--success);
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
