<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { getProfile, updateProfile } from '../api/user';

const loading = ref(false);
const saving = ref(false);
const formRef = ref();

const form = reactive({
  username: '',
  userId: '',
  phone: '',
  plateNo: '',
  batteryCapacity: null
});

const rules = {
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

function fillProfile(data = {}) {
  form.userId = data.userId ?? sessionStorage.getItem('userId') ?? '';
  form.username = data.username ?? sessionStorage.getItem('username') ?? '';
  form.phone = data.phone ?? '';
  form.plateNo = data.plateNo ?? '';
  form.batteryCapacity = data.batteryCapacity ?? null;
}

function cacheProfile(data = {}) {
  sessionStorage.setItem('userId', String(data.userId ?? form.userId ?? ''));
  sessionStorage.setItem('username', data.username || form.username || '');
  sessionStorage.setItem('phone', data.phone || '');
  sessionStorage.setItem('plateNo', data.plateNo || '');
  sessionStorage.setItem('batteryCapacity', data.batteryCapacity == null ? '' : String(data.batteryCapacity));
}

async function loadProfile() {
  loading.value = true;
  try {
    const res = await getProfile();
    fillProfile(res.data || {});
    cacheProfile(res.data || {});
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '获取个人信息失败');
    fillProfile({
      userId: sessionStorage.getItem('userId'),
      username: sessionStorage.getItem('username'),
      phone: sessionStorage.getItem('phone'),
      plateNo: sessionStorage.getItem('plateNo'),
      batteryCapacity: sessionStorage.getItem('batteryCapacity')
    });
  } finally {
    loading.value = false;
  }
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  saving.value = true;
  try {
    const res = await updateProfile({
      phone: form.phone,
      plateNo: form.plateNo,
      batteryCapacity: form.batteryCapacity
    });
    fillProfile(res.data || {});
    cacheProfile(res.data || {});
    ElMessage.success('个人信息已保存');
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '保存个人信息失败');
  } finally {
    saving.value = false;
  }
}

onMounted(loadProfile);
</script>

<template>
  <div class="profile-page">
    <el-card class="profile-card" shadow="hover" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>个人信息</span>
          <el-button type="primary" :loading="saving" @click="handleSave">
            保存修改
          </el-button>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="profile-form"
      >
        <el-row :gutter="20">
          <el-col :xs="24" :md="12">
            <el-form-item label="用户 ID">
              <el-input v-model="form.userId" disabled />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-form-item label="用户名">
              <el-input v-model="form.username" disabled />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-form-item label="手机号">
              <el-input v-model="form.phone" placeholder="请输入手机号（可选）" />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-form-item label="车牌号">
              <el-input v-model="form.plateNo" placeholder="请输入车牌号（可选）" />
            </el-form-item>
          </el-col>

          <el-col :xs="24" :md="12">
            <el-form-item label="电池容量" prop="batteryCapacity">
              <el-input-number
                v-model="form.batteryCapacity"
                :min="1"
                :precision="0"
                controls-position="right"
                class="full-width"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-card {
  border-radius: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-weight: 600;
  color: #1f2937;
}

.profile-form {
  max-width: 860px;
}

.full-width {
  width: 100%;
}
</style>
