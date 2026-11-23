<script setup lang="ts">
// 日常申请（工具组）—— owner: juice094
// 布局：左侧类别 / 中间表单 / 右侧审批链预览
// 字段模板：按 category 拉取模板 fieldSchema 动态渲染；后端会按同一份 schema 复核（不只信前端）
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { PhUploadSimple, PhInfo } from '@phosphor-icons/vue';
import {
  submitApproval, fetchApprovalFieldSchema, PILOT_APPROVAL_STEPS,
  type ApprovalFieldDef,
} from '@/api/team';
import { groupApprovalCategories, getCategoryMeta, type ApprovalCategoryMeta } from '@/constants/approval';

const router = useRouter();

const groups = groupApprovalCategories();
const activeCategory = ref('加班申请');
const categoryMeta = computed<ApprovalCategoryMeta | undefined>(() => getCategoryMeta(activeCategory.value));
const needDates = computed(() => categoryMeta.value?.requireDateRange !== false);

const form = reactive({ title: '', reason: '', start: '', end: '' });
const errors = reactive<Record<string, string>>({});
const submitting = ref(false);
const submitMsg = ref('');
const submitErr = ref(false);
const lastCode = ref('');

// ---- 字段模板 ----
const fieldDefs = ref<ApprovalFieldDef[]>([]);
const schemaLoading = ref(false);
/** 动态字段值：key -> string | number | string[] */
const dynamicValues = reactive<Record<string, string | number | string[]>>({});

/** 按类别拉取字段契约；无模板或模板未配字段时退回通用表单 */
async function loadSchema() {
  schemaLoading.value = true;
  Object.keys(dynamicValues).forEach((k) => delete dynamicValues[k]);
  Object.keys(errors)
    .filter((k) => !['title', 'reason', 'start', 'end'].includes(k))
    .forEach((k) => delete errors[k]);
  try {
    const resp = await fetchApprovalFieldSchema(activeCategory.value);
    const raw = resp.result?.fieldSchema;
    fieldDefs.value = raw ? (JSON.parse(raw) as ApprovalFieldDef[]) : [];
    // 初始化默认值
    for (const f of fieldDefs.value) {
      dynamicValues[f.key] = f.type === 'multiselect' ? [] : '';
    }
  } catch {
    // schema 拉取失败不阻塞填单，退回通用表单
    fieldDefs.value = [];
  } finally {
    schemaLoading.value = false;
  }
}

watch(activeCategory, () => {
  void loadSchema();
});

function hasUnsaved(): boolean {
  if (form.title.trim() || form.reason.trim() || form.start || form.end) return true;
  return Object.values(dynamicValues).some((v) =>
    Array.isArray(v) ? v.length > 0 : String(v ?? '').trim() !== '',
  );
}

function resetFields() {
  form.title = '';
  form.reason = '';
  form.start = '';
  form.end = '';
  Object.keys(errors).forEach((k) => delete errors[k]);
  for (const f of fieldDefs.value) {
    dynamicValues[f.key] = f.type === 'multiselect' ? [] : '';
  }
}

function resetAll() {
  resetFields();
  submitMsg.value = '';
  submitErr.value = false;
  lastCode.value = '';
}

function selectCategory(key: string) {
  if (key === activeCategory.value) return;
  if (hasUnsaved() && !window.confirm('切换类别会清空已填内容，是否继续？')) return;
  activeCategory.value = key;
  resetAll();
}

/** 多选切换（checkbox 绑定用） */
function toggleMulti(key: string, option: string) {
  const cur = dynamicValues[key];
  const arr = Array.isArray(cur) ? [...cur] : [];
  const idx = arr.indexOf(option);
  if (idx >= 0) arr.splice(idx, 1);
  else arr.push(option);
  dynamicValues[key] = arr;
}

function validate(): boolean {
  Object.keys(errors).forEach((k) => delete errors[k]);
  errors.title = form.title.trim() ? '' : '请填写此字段。';
  errors.reason = form.reason.trim() ? '' : '请填写此字段。';
  if (needDates.value) {
    errors.start = form.start ? '' : '请填写此字段。';
    errors.end = form.end ? '' : '请填写此字段。';
    if (form.start && form.end && form.start > form.end) {
      errors.end = '结束日期不能早于开始日期';
    }
  }

  for (const f of fieldDefs.value) {
    const v = dynamicValues[f.key];
    const blank = Array.isArray(v) ? v.length === 0 : String(v ?? '').trim() === '';
    if (f.required && blank) {
      errors[f.key] = '请填写此字段。';
      continue;
    }
    if (blank) continue;
    if (f.type === 'number') {
      const n = Number(v);
      if (Number.isNaN(n)) {
        errors[f.key] = '应为数字';
      } else if (f.min != null && n < f.min) {
        errors[f.key] = `不能小于 ${f.min}`;
      } else if (f.max != null && n > f.max) {
        errors[f.key] = `不能大于 ${f.max}`;
      }
    }
  }

  return !Object.values(errors).some((v) => v);
}

/** 收集动态字段：数字转 Number，多选保持数组，空值不入 JSON */
function buildFormData(): string | undefined {
  if (fieldDefs.value.length === 0) return undefined;
  const out: Record<string, unknown> = {};
  for (const f of fieldDefs.value) {
    const v = dynamicValues[f.key];
    if (Array.isArray(v)) {
      if (v.length) out[f.key] = v;
    } else if (String(v ?? '').trim() !== '') {
      out[f.key] = f.type === 'number' ? Number(v) : v;
    }
  }
  return Object.keys(out).length ? JSON.stringify(out) : undefined;
}

async function submit() {
  if (submitting.value) return;
  submitMsg.value = '';
  submitErr.value = false;
  lastCode.value = '';
  if (!validate()) return;

  submitting.value = true;
  try {
    const res: any = await submitApproval({
      category: activeCategory.value,
      title: form.title.trim(),
      reason: form.reason.trim() || undefined,
      startDate: form.start || undefined,
      endDate: form.end || undefined,
      formData: buildFormData(),
    });
    const code = res?.result?.code ?? '';
    lastCode.value = code;
    submitMsg.value = code ? `已提交，单号 ${code}，可在审批中心跟进` : '已提交，可在审批中心跟进';
    submitErr.value = false;
    resetFields();
  } catch (e: any) {
    submitMsg.value = e.message || '提交失败';
    submitErr.value = true;
  } finally {
    submitting.value = false;
  }
}

function goApprovalCenter() {
  router.push('/tools/approval');
}
</script>

<template>
  <div class="request-layout">
    <div class="card" style="padding: 10px">
      <template v-for="g in groups" :key="g.group">
        <div class="category-group">{{ g.group }}</div>
        <div
          v-for="item in g.items"
          :key="item.key"
          class="category-item"
          :class="{ 'category-item--active': item.key === activeCategory }"
          @click="selectCategory(item.key)"
        >
          {{ item.key }}
        </div>
      </template>
    </div>

    <div class="card form-area">
      <div class="form-header">
        <h3>{{ activeCategory }}</h3>
        <p>{{ categoryMeta?.description || '请填写申请信息' }}</p>
      </div>

      <!-- 动态字段（模板配置时渲染） -->
      <template v-if="fieldDefs.length">
        <div v-if="schemaLoading" class="field-loading">字段加载中…</div>
        <div v-for="f in fieldDefs" :key="f.key" class="form-group">
          <div class="form-label">
            {{ f.label }} <span v-if="f.required" class="req">*</span>
          </div>

          <input
            v-if="f.type === 'text' || f.type === 'number'"
            v-model="dynamicValues[f.key]"
            :type="f.type === 'number' ? 'number' : 'text'"
            class="form-control"
            :class="{ 'form-control--err': errors[f.key] }"
          />

          <textarea
            v-else-if="f.type === 'textarea'"
            v-model="dynamicValues[f.key]"
            class="form-control"
            :class="{ 'form-control--err': errors[f.key] }"
          ></textarea>

          <input
            v-else-if="f.type === 'date'"
            v-model="dynamicValues[f.key]"
            type="date"
            class="form-control"
            :class="{ 'form-control--err': errors[f.key] }"
          />

          <select
            v-else-if="f.type === 'select'"
            v-model="dynamicValues[f.key]"
            class="form-control"
            :class="{ 'form-control--err': errors[f.key] }"
          >
            <option value="">请选择</option>
            <option v-for="opt in f.options || []" :key="opt" :value="opt">{{ opt }}</option>
          </select>

          <div v-else-if="f.type === 'multiselect'" class="checkbox-group">
            <label v-for="opt in f.options || []" :key="opt" class="checkbox-item">
              <input
                type="checkbox"
                :checked="Array.isArray(dynamicValues[f.key]) && (dynamicValues[f.key] as string[]).includes(opt)"
                @change="toggleMulti(f.key, opt)"
              />
              {{ opt }}
            </label>
          </div>

          <div v-if="errors[f.key]" class="field-error">{{ errors[f.key] }}</div>
        </div>
      </template>
      <div v-else-if="schemaLoading" class="field-loading">字段加载中…</div>

      <div class="form-group">
        <div class="form-label">事项标题 <span class="req">*</span></div>
        <input
          v-model="form.title"
          type="text"
          class="form-control"
          :class="{ 'form-control--err': errors.title }"
          placeholder="简要概括申请事项"
        />
        <div v-if="errors.title" class="field-error">{{ errors.title }}</div>
      </div>
      <div class="form-group">
        <div class="form-label">申请事由 <span class="req">*</span></div>
        <textarea
          v-model="form.reason"
          class="form-control"
          :class="{ 'form-control--err': errors.reason }"
          placeholder="请说明申请背景及必要性..."
        ></textarea>
        <div v-if="errors.reason" class="field-error">{{ errors.reason }}</div>
      </div>
      <div v-if="categoryMeta?.tip" class="tip-box"><PhInfo :size="16" /> {{ categoryMeta.tip }}</div>
      <div class="flex-row">
        <div class="form-group">
          <div class="form-label">开始日期 <span v-if="needDates" class="req">*</span></div>
          <input
            v-model="form.start"
            type="date"
            class="form-control"
            :class="{ 'form-control--err': errors.start }"
            :max="form.end || undefined"
          />
          <div v-if="errors.start" class="field-error">{{ errors.start }}</div>
        </div>
        <div class="form-group">
          <div class="form-label">结束日期 <span v-if="needDates" class="req">*</span></div>
          <input
            v-model="form.end"
            type="date"
            class="form-control"
            :class="{ 'form-control--err': errors.end }"
            :min="form.start || undefined"
          />
          <div v-if="errors.end" class="field-error">{{ errors.end }}</div>
        </div>
      </div>
      <div class="form-group">
        <div class="form-label">附件</div>
        <div class="upload-box upload-box--disabled" title="附件能力待后端接入（P1）">
          <PhUploadSimple :size="32" style="color: var(--text-muted); margin-bottom: 8px" />
          <p>附件上传暂未开放</p>
        </div>
      </div>
      <div class="form-footer">
        <button class="btn btn-default" @click="resetAll">清空</button>
        <span v-if="submitMsg" class="submit-msg" :class="{ 'submit-msg--err': submitErr }">
          {{ submitMsg }}
          <a v-if="lastCode" href="javascript:;" class="submit-link" @click.prevent="goApprovalCenter">去审批中心 →</a>
        </span>
        <button class="btn btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交申请' }}
        </button>
      </div>
    </div>

    <div class="card flow-area">
      <div class="flow-header">审批流程</div>
      <div class="flow-desc">后端按类别模板生成审批链；未配置模板的类别使用试点默认链。</div>
      <div class="flow-timeline">
        <div v-for="(step, i) in PILOT_APPROVAL_STEPS" :key="step.approverName" class="flow-step active">
          <div class="step-icon">{{ i + 1 }}</div>
          <div class="step-content">
            <div class="step-name">{{ step.approverName }}</div>
            <div class="step-role">{{ step.approverRole }}</div>
          </div>
        </div>
      </div>
      <div class="flow-note">（试点默认链；模板配置后以模板为准）</div>
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
.submit-msg {
  margin-right: auto;
  font-size: var(--font-size-sm);
  color: var(--success);
}
.submit-msg--err {
  color: var(--danger);
}
.submit-link {
  margin-left: 8px;
  color: var(--primary);
  text-decoration: none;
}
.submit-link:hover {
  text-decoration: underline;
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
.form-control--err {
  border-color: var(--danger);
}
.upload-box--disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.flow-area {
  padding: 24px;
  overflow-y: auto;
}
.flow-note {
  margin-top: 12px;
  font-size: 12px;
  color: var(--text-muted);
}
.field-loading {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
  margin-bottom: 16px;
}
.checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.checkbox-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
  cursor: pointer;
}
</style>
