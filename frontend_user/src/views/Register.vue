<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { register } from '../api/user';

const router = useRouter();
const loading = ref(false);
const formRef = ref();

const form = reactive({
  username: '',
  password: '',
  phone: '',
  batteryCapacity: null
});

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度应为 3 到 20 位', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度应为 6 到 20 位', trigger: 'blur' }
  ],
  batteryCapacity: [
    { required: true, message: '请输入电池容量', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value === null || value === undefined || value === '') {
          callback(new Error('请输入电池容量'));
          return;
        }

        if (Number(value) <= 0) {
          callback(new Error('电池容量必须大于 0'));
          return;
        }

        callback();
      },
      trigger: 'blur'
    }
  ]
};

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false);

  if (!valid) {
    return;
  }

  loading.value = true;

  try {
    await register({
      username: form.username,
      password: form.password,
      phone: form.phone,
      batteryCapacity: form.batteryCapacity
    });

    ElMessage.success('注册成功，请登录');
    router.push('/login');
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '注册失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="register-page">
    <div class="bg-orb orb-left"></div>
    <div class="bg-orb orb-right"></div>

    <el-card class="register-card" shadow="hover">
      <div class="register-header">
        <p class="register-tag">Charging User Portal</p>
        <h1>智能充电桩调度计费系统</h1>
        <p class="register-desc">用户注册</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @keyup.enter="handleRegister"
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

        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="请输入手机号（可选）"
            size="large"
          />
        </el-form-item>

        <el-form-item label="电池容量" prop="batteryCapacity">
          <el-input-number
            v-model="form.batteryCapacity"
            :min="1"
            :precision="0"
            controls-position="right"
            class="full-width"
            size="large"
          />
        </el-form-item>

        <el-button
          type="primary"
          class="register-button"
          size="large"
          :loading="loading"
          @click="handleRegister"
        >
          注册
        </el-button>
      </el-form>

      <div class="register-footer">
        <span>已有账号？</span>
        <router-link to="/login">返回登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.register-page {
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

.register-card {
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

.register-header {
  margin-bottom: 24px;
  text-align: center;
}

.register-tag {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.6px;
  text-transform: uppercase;
  color: #2563eb;
}

.register-header h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.3;
  color: #0f172a;
}

.register-desc {
  margin: 10px 0 0;
  font-size: 15px;
  color: #64748b;
}

.register-button {
  width: 100%;
  margin-top: 6px;
  border-radius: 12px;
}

.register-footer {
  margin-top: 22px;
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

.register-footer a {
  margin-left: 6px;
  color: #2563eb;
  font-weight: 600;
}

.full-width {
  width: 100%;
}

@media (max-width: 600px) {
  .register-page {
    padding: 16px;
  }

  .register-card {
    max-width: 100%;
  }

  .register-header h1 {
    font-size: 24px;
  }
}
</style>
