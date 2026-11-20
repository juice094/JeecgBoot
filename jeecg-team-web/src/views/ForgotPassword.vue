<script setup lang="ts">
// 忘记密码 —— 三步：手机号+短信码 → 校验验证码 → 设置新密码
// 后端口径：jeecg 原生 /sys/sms(smsmode=2) + /sys/user/phoneVerification + /sys/user/passwordChange（全部匿名）
// dev 环境短信走 mock（jeecg.smsMock=true），验证码打印在后端控制台日志
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { sendForgotPasswordSms, verifyPhoneCode, resetPasswordByPhone } from '@/api/auth';

const router = useRouter();
const step = ref<1 | 2 | 3>(1);
const loading = ref(false);
const errorMsg = ref('');
const infoMsg = ref('');
const countdown = ref(0);

const form = reactive({ phone: '', smscode: '', password: '', confirmPassword: '' });
let verifiedUsername = '';

const PHONE_RE = /^1[3-9]\d{9}$/;

async function run(fn: () => Promise<void>) {
  loading.value = true;
  errorMsg.value = '';
  try {
    await fn();
  } catch (e: any) {
    errorMsg.value = e.message || '操作失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function tick() {
  countdown.value = 60;
  const timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) clearInterval(timer);
  }, 1000);
}

function sendCode() {
  if (!PHONE_RE.test(form.phone)) {
    errorMsg.value = '请输入正确的手机号';
    return;
  }
  run(async () => {
    await sendForgotPasswordSms(form.phone);
    step.value = 2;
    infoMsg.value = '验证码已发送（dev 环境请在后端控制台日志查看）';
    tick();
  });
}

function resendCode() {
  run(async () => {
    await sendForgotPasswordSms(form.phone);
    infoMsg.value = '验证码已重新发送（dev 环境请在后端控制台日志查看）';
    tick();
  });
}

function verifyCode() {
  if (!form.smscode) {
    errorMsg.value = '请输入短信验证码';
    return;
  }
  run(async () => {
    const res = await verifyPhoneCode(form.phone, form.smscode);
    verifiedUsername = res.username;
    step.value = 3;
    infoMsg.value = '';
  });
}

function submitPassword() {
  if (form.password.length < 6) {
    errorMsg.value = '新密码至少 6 位';
    return;
  }
  if (form.password !== form.confirmPassword) {
    errorMsg.value = '两次输入的密码不一致';
    return;
  }
  run(async () => {
    await resetPasswordByPhone({
      username: verifiedUsername,
      password: form.password,
      smscode: form.smscode,
      phone: form.phone,
    });
    infoMsg.value = '';
    router.push({ name: 'Login', query: { reset: '1' } });
  });
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-card__title">找回密码</h1>
      <p class="login-card__sub">通过绑定手机号验证后重置</p>

      <form class="login-form" @submit.prevent>
        <template v-if="step === 1">
          <label class="login-form__field">
            <span>绑定手机号</span>
            <input v-model.trim="form.phone" type="tel" maxlength="11" placeholder="请输入账号绑定的手机号" />
          </label>
          <button class="login-form__submit" type="button" :disabled="loading" @click="sendCode">
            {{ loading ? '发送中…' : '发送验证码' }}
          </button>
        </template>

        <template v-else-if="step === 2">
          <label class="login-form__field">
            <span>短信验证码（已发送至 {{ form.phone }}）</span>
            <input v-model.trim="form.smscode" type="text" maxlength="6" placeholder="6 位数字验证码" />
          </label>
          <button class="login-form__submit" type="button" :disabled="loading" @click="verifyCode">
            {{ loading ? '校验中…' : '下一步' }}
          </button>
          <p class="login-form__link">
            <button type="button" class="link-btn" :disabled="countdown > 0 || loading" @click="resendCode">
              {{ countdown > 0 ? `${countdown}s 后可重发` : '重新发送验证码' }}
            </button>
          </p>
        </template>

        <template v-else>
          <label class="login-form__field">
            <span>新密码</span>
            <input v-model="form.password" type="password" autocomplete="new-password" placeholder="至少 6 位" />
          </label>
          <label class="login-form__field">
            <span>确认新密码</span>
            <input v-model="form.confirmPassword" type="password" autocomplete="new-password" placeholder="再次输入新密码" />
          </label>
          <button class="login-form__submit" type="button" :disabled="loading" @click="submitPassword">
            {{ loading ? '提交中…' : '重置密码' }}
          </button>
        </template>

        <p v-if="errorMsg" class="login-form__error">{{ errorMsg }}</p>
        <p v-if="infoMsg" class="login-form__info">{{ infoMsg }}</p>
      </form>

      <p class="login-card__foot">
        <button type="button" class="link-btn" @click="router.push({ name: 'Login' })">返回登录</button>
      </p>
    </div>
  </div>
</template>

<style scoped>
/* 复用 Login.vue 的页面骨架与令牌（保持登录域视觉一致） */
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
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
  margin-top: 12px;
}

.login-form__info {
  color: var(--text-muted);
  font-size: 13px;
  margin-top: 12px;
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

.login-form__link {
  margin-top: 12px;
  text-align: right;
}

.login-card__foot {
  margin-top: 20px;
  text-align: center;
}

.link-btn {
  background: none;
  border: none;
  color: var(--primary);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}

.link-btn:disabled {
  color: var(--text-muted);
  cursor: not-allowed;
}
</style>
