<template>
  <main class="auth-page">
    <section class="auth-panel">
      <p class="brand-kicker">BUPT Charging</p>
      <h1>管理端登录</h1>
      <form @submit.prevent="handleSubmit">
        <label>
          用户名
          <input v-model.trim="form.username" required autocomplete="username" />
        </label>
        <label>
          密码
          <input v-model="form.password" required type="password" autocomplete="current-password" />
        </label>
        <button type="submit" :disabled="loading">{{ loading ? '登录中' : '登录' }}</button>
      </form>
      <p class="feedback" v-if="message">{{ message }}</p>
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
const form = reactive({ username: '', password: '' });

async function handleSubmit() {
  loading.value = true;
  message.value = '';
  try {
    const data = await auth.login(form);
    if (data.role !== 'ADMIN') {
      auth.logout();
      message.value = '当前前端仅负责管理端，请使用管理员账号登录';
      return;
    }
    router.push('/admin');
  } catch (error) {
    message.value = error.message;
  } finally {
    loading.value = false;
  }
}
</script>
