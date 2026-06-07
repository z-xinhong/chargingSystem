<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { login } from '../api/user';

const router = useRouter();
const loading = ref(false);
const formRef = ref();

const form = reactive({
  username: '',
  password: ''
});

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
};

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false);

  if (!valid) {
    return;
  }

  loading.value = true;

  try {
    const res = await login({
      username: form.username,
      password: form.password
    });

    const { token, userId, username, role } = res.data || {};

    if (!token) {
      ElMessage.error('登录成功但未获取到 token');
      return;
    }

    if (role === 'ADMIN') {
      ElMessage.warning('管理员账号请进入管理端');
      return;
    }

    if (role !== 'USER') {
      ElMessage.error('当前账号角色无权进入用户端');
      return;
    }

    sessionStorage.setItem('token', token);
    sessionStorage.setItem('charging_user_token', token);
    sessionStorage.setItem('userId', String(userId ?? ''));
    sessionStorage.setItem('username', username || form.username);
    sessionStorage.setItem('role', role);

    ElMessage.success('登录成功');
    router.push('/home');
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '登录失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="bg-orb orb-left"></div>
    <div class="bg-orb orb-right"></div>

    <el-card class="login-card" shadow="hover">
      <div class="login-header">
        <p class="login-tag">Charging User Portal</p>
        <h1>智能充电桩调度计费系统</h1>
        <p class="login-desc">用户端登录</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            size="large"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="login-button"
          size="large"
          :loading="loading"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <div class="login-footer">
        <span>还没有账号？</span>
        <router-link to="/register">前往注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  overflow: hidden;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(10px);
  opacity: 0.8;
}

.orb-left {
  top: 8%;
  left: 10%;
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgba(37, 99, 235, 0.28), rgba(37, 99, 235, 0));
}

.orb-right {
  right: 8%;
  bottom: 10%;
  width: 340px;
  height: 340px;
  background: radial-gradient(circle, rgba(14, 165, 233, 0.24), rgba(14, 165, 233, 0));
}

.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 460px;
  border: none;
  border-radius: 24px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(14px);
}

.login-header {
  margin-bottom: 24px;
  text-align: center;
}

.login-tag {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.6px;
  text-transform: uppercase;
  color: #2563eb;
}

.login-header h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.3;
  color: #0f172a;
}

.login-desc {
  margin: 10px 0 0;
  font-size: 15px;
  color: #64748b;
}

.login-button {
  width: 100%;
  margin-top: 6px;
  border-radius: 12px;
}

.login-footer {
  margin-top: 22px;
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.login-footer a {
  margin-left: 6px;
  color: #2563eb;
  font-weight: 600;
}

@media (max-width: 600px) {
  .login-page {
    padding: 16px;
  }

  .login-card {
    max-width: 100%;
  }

  .login-header h1 {
    font-size: 24px;
  }
}
</style>
