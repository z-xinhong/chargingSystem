<template>
  <main class="auth-page">
    <section class="auth-panel wide">
      <p class="brand-kicker">新用户</p>
      <h1>注册车辆信息</h1>
      <form @submit.prevent="handleSubmit">
        <label>
          用户名
          <input v-model.trim="form.username" required minlength="3" maxlength="20" />
        </label>
        <label>
          密码
          <input v-model="form.password" required type="password" minlength="6" maxlength="20" />
        </label>
        <label>
          手机号
          <input v-model.trim="form.phone" />
        </label>
        <label>
          电池容量（度）
          <input v-model.number="form.batteryCapacity" required min="1" type="number" />
        </label>
        <button type="submit" :disabled="loading">{{ loading ? '提交中' : '注册' }}</button>
      </form>
      <p class="feedback" v-if="message">{{ message }}</p>
      <RouterLink to="/login">已有账号，去登录</RouterLink>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const message = ref('');
const form = reactive({
  username: '',
  password: '',
  phone: '',
  batteryCapacity: 60
});

async function handleSubmit() {
  loading.value = true;
  message.value = '';
  try {
    await auth.register(form);
    message.value = '注册成功，请登录';
    setTimeout(() => router.push('/login'), 600);
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}
</script>
