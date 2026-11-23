<script setup lang="ts">
// 标签管理弹层（/issues 工具条入口）——team_label CRUD：
// 命名 + 选色（GitHub 调色板口径）+ 增删改，走 /team/label/*（需 team:task:edit）。
// 挂载到任务在 TaskDetail 侧栏 Labels 区完成；此处只管标签库本身。
import { onMounted, ref } from 'vue';
import { PhPencilSimple, PhTrash, PhX } from '@phosphor-icons/vue';
import { listLabels, addLabel, editLabel, deleteLabel, type TeamLabel } from '@/api/team';
import { LABEL_PALETTE } from '@/constants/task';

const props = defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'changed'): void }>();

function close() {
  emit('update:modelValue', false);
}

const labels = ref<TeamLabel[]>([]);
const loading = ref(false);
const errorMsg = ref('');
const busy = ref(false);

async function load() {
  loading.value = true;
  try {
    const data = await listLabels({ pageNo: 1, pageSize: 100 });
    labels.value = data.result?.records || [];
  } catch (e: any) {
    errorMsg.value = e.message || '标签加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(load);

/** 创建/编辑共享表单状态 */
const editingId = ref('');
const nameInput = ref('');
const colorPick = ref<string>(LABEL_PALETTE[0]);
const formError = ref('');

const isEditing = () => !!editingId.value;

function startEdit(lb: TeamLabel) {
  editingId.value = lb.id;
  nameInput.value = lb.name;
  colorPick.value = lb.color;
  formError.value = '';
}

function resetForm() {
  editingId.value = '';
  nameInput.value = '';
  colorPick.value = LABEL_PALETTE[0];
  formError.value = '';
}

async function submit() {
  const name = nameInput.value.trim();
  if (!name) {
    formError.value = '请填写标签名称';
    return;
  }
  if (busy.value) return;
  busy.value = true;
  formError.value = '';
  try {
    if (editingId.value) {
      await editLabel({ id: editingId.value, name, color: colorPick.value });
    } else {
      await addLabel({ name, color: colorPick.value });
    }
    resetForm();
    await load();
    emit('changed');
  } catch (e: any) {
    formError.value = e.message || '保存失败';
  } finally {
    busy.value = false;
  }
}

async function remove(lb: TeamLabel) {
  if (busy.value) return;
  if (!window.confirm(`删除标签「${lb.name}」？`)) return;
  busy.value = true;
  errorMsg.value = '';
  try {
    await deleteLabel(lb.id);
    if (editingId.value === lb.id) resetForm();
    await load();
    emit('changed');
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div v-if="props.modelValue" class="label-modal__mask" @click.self="close">
    <div class="label-modal" role="dialog" aria-label="标签管理">
      <header class="label-modal__head">
        <h3 class="label-modal__title">标签管理</h3>
        <button class="label-modal__close" title="关闭" @click="close">
          <PhX :size="15" />
        </button>
      </header>

      <div v-if="errorMsg" class="label-modal__alert">{{ errorMsg }}</div>

      <!-- 标签库列表 -->
      <ul class="label-list">
        <li v-if="loading" class="label-list__empty">加载中…</li>
        <li v-for="lb in labels" :key="lb.id" class="label-list__row">
          <span class="label-chip" :style="{ background: lb.color + '26', color: lb.color, borderColor: lb.color + '59' }">
            {{ lb.name }}
          </span>
          <span class="label-list__ops">
            <button class="label-list__btn" title="编辑" @click="startEdit(lb)">
              <PhPencilSimple :size="13" />
            </button>
            <button class="label-list__btn label-list__btn--danger" title="删除" @click="remove(lb)">
              <PhTrash :size="13" />
            </button>
          </span>
        </li>
        <li v-if="!loading && !labels.length" class="label-list__empty">暂无标签，先创建一个</li>
      </ul>

      <!-- 新建/编辑表单：命名 + 选色 -->
      <form class="label-form" @submit.prevent="submit">
        <div class="label-form__row">
          <span
            v-if="nameInput.trim()"
            class="label-chip"
            :style="{ background: colorPick + '26', color: colorPick, borderColor: colorPick + '59' }"
          >{{ nameInput.trim() }}</span>
          <input
            v-model="nameInput"
            type="text"
            maxlength="20"
            placeholder="标签名称…"
            class="label-form__input"
          />
        </div>
        <div class="label-form__palette">
          <button
            v-for="c in LABEL_PALETTE"
            :key="c"
            type="button"
            class="label-form__swatch"
            :class="{ 'label-form__swatch--active': c === colorPick }"
            :style="{ background: c }"
            :title="c"
            @click="colorPick = c"
          />
        </div>
        <div v-if="formError" class="label-form__error">{{ formError }}</div>
        <div class="label-form__actions">
          <button v-if="isEditing()" type="button" class="btn btn--sm btn-default" @click="resetForm">取消编辑</button>
          <button type="submit" class="btn btn-primary btn--sm" :disabled="busy">
            {{ isEditing() ? '保存修改' : '创建标签' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.label-modal__mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
  background: rgba(0, 0, 0, 0.4);
}
.label-modal {
  width: 420px;
  max-width: calc(100vw - 32px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg, 0 8px 24px rgba(0, 0, 0, 0.18));
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}
.label-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.label-modal__title {
  margin: 0;
  font-size: var(--font-size-lg);
  color: var(--text-main);
}
.label-modal__close {
  display: inline-flex;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  padding: 2px;
  border-radius: var(--radius-xs);
}
.label-modal__close:hover { color: var(--text-main); background: var(--gray-100); }
.label-modal__alert {
  padding: 6px 10px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-md);
  color: var(--danger-700);
  background: var(--danger-50);
  border: 1px solid var(--danger-border);
}

.label-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 240px;
  overflow-y: auto;
}
.label-list__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}
.label-list__empty { color: var(--text-muted); font-size: var(--font-size-md); padding: var(--spacing-sm) 0; }
.label-list__ops { display: inline-flex; gap: 4px; }
.label-list__btn {
  display: inline-flex;
  align-items: center;
  padding: 4px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-regular);
  cursor: pointer;
}
.label-list__btn:hover { border-color: var(--primary); color: var(--primary); }
.label-list__btn--danger:hover { border-color: var(--danger); color: var(--danger); }

.label-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 1px solid;
  border-radius: 999px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 18px;
}

.label-form {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  border-top: 1px solid var(--border-color-light);
  padding-top: var(--spacing-md);
}
.label-form__row { display: flex; align-items: center; gap: var(--spacing-sm); }
.label-form__input {
  flex: 1;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  font-size: var(--font-size-md);
  color: var(--text-main);
  outline: none;
  font-family: inherit;
}
.label-form__input:focus { border-color: var(--primary); }
.label-form__palette { display: flex; gap: 6px; flex-wrap: wrap; }
.label-form__swatch {
  width: 22px;
  height: 22px;
  border-radius: var(--radius-sm);
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
}
.label-form__swatch--active { border-color: var(--text-main); }
.label-form__error { font-size: var(--font-size-sm); color: var(--danger); }
.label-form__actions { display: flex; justify-content: flex-end; gap: var(--spacing-sm); }
</style>
