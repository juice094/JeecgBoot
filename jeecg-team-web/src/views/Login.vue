<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const form = reactive({ username: 'admin', password: '123456' });
const loading = ref(false);
const errorMsg = ref('');
const resetTip = ref(route.query.reset === '1' ? '密码已重置，请使用新密码登录' : '');

async function onSubmit() {
  if (!form.username || !form.password) {
    errorMsg.value = '请输入用户名和密码';
    return;
  }
  loading.value = true;
  errorMsg.value = '';
  try {
    await auth.login(form.username, form.password);
    router.push('/');
  } catch (e: any) {
    errorMsg.value = e.message || '登录失败';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-card__title">团队协作效率工作台</h1>
      <p class="login-card__sub">Team Collaboration Workbench</p>
      <form class="login-form" @submit.prevent="onSubmit">
        <label class="login-form__field">
          <span>用户名</span>
          <input v-model.trim="form.username" type="text" autocomplete="username" placeholder="请输入用户名" />
        </label>
        <label class="login-form__field">
          <span>密码</span>
          <input v-model="form.password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <p v-if="errorMsg" class="login-form__error">{{ errorMsg }}</p>
        <p v-if="resetTip" class="login-form__info">{{ resetTip }}</p>
        <button class="login-form__submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登 录' }}
        </button>
      </form>
      <p class="login-card__foot">
        <button type="button" class="link-btn" @click="router.push({ name: 'ForgotPassword' })">忘记密码？</button>
      </p>
      <p class="login-card__foot">账号体系：JeecgBoot RBAC · 默认 admin / 123456</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 品牌渐变随主题自适应（深色基线用提亮主色，浅色回原值系） */
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-700) 100%);
}

.login-card {
  width: 380px;
  max-width: calc(100vw - 32px);
  background: var(--bg-card);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
  padding: 36px 32px 24px;
}

.login-card__title {
  font-size: 20px;
  color: var(--primary);
  text-align: center;
}

.login-card__sub {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
  margin: 6px 0 28px;
  letter-spacing: 1px;
}

.login-form__field {
  display: block;
  margin-bottom: 18px;
}

.login-form__field span {
  display: block;
  font-size: 13px;
  color: var(--text-regular);
  margin-bottom: 6px;
}

.login-form__field input {
  width: 100%;
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 0 12px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.login-form__field input:focus {
  border-color: var(--primary);
}

.login-form__error {
  color: var(--danger);
  font-size: 13px;
  margin-bottom: 12px;
}

.login-form__info {
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 12px;
}

.link-btn {
  background: none;
  border: none;
  color: var(--primary);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}

.login-form__submit {
  width: 100%;
  height: 42px;
  border: none;
  border-radius: var(--radius-sm);
  background: var(--primary);
  color: #fff;
  font-size: 15px;
  cursor: pointer;
  transition: opacity 0.2s;
}

.login-form__submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.login-card__foot {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
