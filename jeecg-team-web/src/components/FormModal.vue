<script setup lang="ts">
// 通用表单弹窗（字段 schema 驱动）—— 管理域 P0-P2 复用。
// 用法：v-model 控制显隐；fields 描述字段；initialValues 打开时回填；submit 事件带校验后的 values。
// 校验：required 必填 + rules 自定义（返回错误字符串，空串=通过）。
import { reactive, watch } from 'vue';
import { PhX } from '@phosphor-icons/vue';

export interface FormFieldOption {
  label: string;
  value: string | number;
}

export interface FormField {
  field: string;
  label: string;
  type?: 'input' | 'textarea' | 'number' | 'password' | 'select' | 'switch' | 'checkboxes';
  placeholder?: string;
  required?: boolean;
  options?: FormFieldOption[];
  disabled?: boolean | ((values: Record<string, any>) => boolean);
  /** 1=半宽(双列时)，2=整行；默认 1 */
  span?: 1 | 2;
  /** 自定义校验：返回错误字符串，空串通过 */
  rules?: ((value: any, values: Record<string, any>) => string)[];
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title: string;
    fields: FormField[];
    initialValues?: Record<string, any>;
    loading?: boolean;
    width?: number;
    submitText?: string;
  }>(),
  {
    initialValues: () => ({}),
    loading: false,
    width: 560,
    submitText: '保存',
  }
);

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void;
  (e: 'submit', values: Record<string, any>): void;
  (e: 'cancel'): void;
}>();

const form = reactive<Record<string, any>>({});
const errors = reactive<Record<string, string>>({});

function resetForm() {
  for (const k of Object.keys(form)) delete form[k];
  for (const k of Object.keys(errors)) delete errors[k];
  for (const f of props.fields) {
    form[f.field] = props.initialValues[f.field] ?? (f.type === 'switch' ? false : f.type === 'checkboxes' ? [] : '');
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) resetForm();
  }
);

function isDisabled(f: FormField): boolean {
  return typeof f.disabled === 'function' ? f.disabled(form) : !!f.disabled;
}

function validate(): boolean {
  let ok = true;
  for (const f of props.fields) {
    errors[f.field] = '';
    const v = form[f.field];
    if (f.required && (v === '' || v === null || v === undefined || (Array.isArray(v) && v.length === 0))) {
      errors[f.field] = '请输入' + f.label;
      ok = false;
      continue;
    }
    for (const rule of f.rules ?? []) {
      const msg = rule(v, form);
      if (msg) {
        errors[f.field] = msg;
        ok = false;
        break;
      }
    }
  }
  return ok;
}

function close() {
  emit('update:modelValue', false);
  emit('cancel');
}

function submit() {
  if (!validate()) return;
  emit('submit', { ...form });
}
</script>

<template>
  <div v-if="modelValue" class="fm__mask" @click.self="close">
    <div class="fm" :style="{ width: width + 'px' }">
      <div class="fm__head">
        <h3 class="fm__title">{{ title }}</h3>
        <button class="icon-btn" title="关闭" @click="close"><PhX :size="16" /></button>
      </div>

      <div class="fm__body">
        <div
          v-for="f in fields"
          :key="f.field"
          class="fm__item"
          :class="{ 'fm__item--full': f.span === 2 || f.type === 'textarea' }"
        >
          <label class="fm__label">
            <span v-if="f.required" class="fm__req">*</span>{{ f.label }}
          </label>

          <textarea
            v-if="f.type === 'textarea'"
            v-model="form[f.field]"
            class="fm__control"
            :placeholder="f.placeholder || ('请输入' + f.label)"
            :disabled="isDisabled(f)"
            rows="3"
          />
          <select
            v-else-if="f.type === 'select'"
            v-model="form[f.field]"
            class="fm__control"
            :disabled="isDisabled(f)"
          >
            <option value="">{{ f.placeholder || ('请选择' + f.label) }}</option>
            <option v-for="opt in f.options ?? []" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <label v-else-if="f.type === 'switch'" class="fm__switch">
            <input v-model="form[f.field]" type="checkbox" :disabled="isDisabled(f)" />
            <span>{{ form[f.field] ? '是' : '否' }}</span>
          </label>
          <div v-else-if="f.type === 'checkboxes'" class="fm__checks">
            <label v-for="opt in f.options ?? []" :key="opt.value" class="fm__check">
              <input v-model="form[f.field]" type="checkbox" :value="opt.value" :disabled="isDisabled(f)" />
              <span>{{ opt.label }}</span>
            </label>
          </div>
          <input
            v-else
            v-model="form[f.field]"
            class="fm__control"
            :type="f.type === 'number' ? 'number' : f.type === 'password' ? 'password' : 'text'"
            :placeholder="f.placeholder || ('请输入' + f.label)"
            :disabled="isDisabled(f)"
          />

          <div v-if="errors[f.field]" class="fm__err">{{ errors[f.field] }}</div>
        </div>
      </div>

      <div class="fm__foot">
        <button class="btn btn-default" @click="close">取消</button>
        <button class="btn btn-primary" :disabled="loading" @click="submit">
          {{ loading ? '提交中…' : submitText }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.fm__mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
  animation: fadeIn 0.15s ease-out;
}
.fm {
  max-width: calc(100vw - 32px);
  max-height: calc(100vh - 64px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
}
.fm__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}
.fm__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
}
.fm__body {
  padding: 20px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  overflow-y: auto;
}

@media (max-width: 640px) {
  .fm__body {
    grid-template-columns: 1fr;
  }
}
.fm__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.fm__item--full {
  grid-column: 1 / -1;
}
.fm__label {
  font-size: 13px;
  color: var(--text-regular);
}
.fm__req {
  color: var(--danger);
  margin-right: 2px;
}
.fm__control {
  width: 100%;
  padding: 7px 10px;
  font-size: 13px;
  color: var(--text-main);
  background: var(--bg-card);
  border: 1px solid var(--border-strong, var(--border-color));
  border-radius: var(--radius-sm);
  outline: none;
  transition: border-color 0.15s;
}
.fm__control:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--primary) 18%, transparent);
}
.fm__control:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.fm__switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-regular);
}
.fm__checks {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  padding: 4px 0;
}
.fm__check {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-regular);
  cursor: pointer;
}
.fm__err {
  font-size: 12px;
  color: var(--danger);
}
.fm__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--border-color);
}
</style>
