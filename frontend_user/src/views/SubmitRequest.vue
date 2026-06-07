<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { submitRequest } from '../api/chargingRequest';
import { formatMode, formatStatus } from '../utils/format';

const router = useRouter();
const loading = ref(false);
const formRef = ref();
const submitResult = ref(null);

const form = reactive({
  mode: 'FAST',
  requestedKwh: null
});

const rules = {
  mode: [
    { required: true, message: '请选择充电模式', trigger: 'change' }
  ],
  requestedKwh: [
    { required: true, message: '请输入请求充电量', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value === null || value === undefined || value === '') {
          callback(new Error('请输入请求充电量'));
          return;
        }

        if (Number(value) <= 0) {
          callback(new Error('请求充电量必须大于 0'));
          return;
        }

        callback();
      },
      trigger: 'blur'
    }
  ]
};

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false);

  if (!valid) {
    return;
  }

  loading.value = true;

  try {
    const res = await submitRequest({
      mode: form.mode,
      requestedKwh: form.requestedKwh
    });

    submitResult.value = res.data || null;

    if (submitResult.value?.requestId !== undefined && submitResult.value?.requestId !== null) {
      sessionStorage.setItem('currentRequestId', String(submitResult.value.requestId));
    }

    ElMessage.success('充电请求提交成功');
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '提交充电请求失败');
  } finally {
    loading.value = false;
  }
}

function goToQueue() {
  router.push('/queue');
}
</script>

<template>
  <div class="submit-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">提交充电请求</h2>
        <p class="page-desc">
          快充排队号以 F 开头，慢充排队号以 T 开头。
        </p>
      </div>
    </div>

    <el-card class="form-card" shadow="hover">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
        class="submit-form"
      >
        <el-form-item label="充电模式" prop="mode">
          <el-radio-group v-model="form.mode">
            <el-radio value="FAST">快充</el-radio>
            <el-radio value="SLOW">慢充</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="请求充电量" prop="requestedKwh">
          <el-input-number
            v-model="form.requestedKwh"
            :min="1"
            :precision="0"
            controls-position="right"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            提交请求
          </el-button>
          <el-button type="success" plain @click="goToQueue">
            查看当前排队状态
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="submitResult" class="result-card" shadow="hover">
      <template #header>
        <div class="result-header">
          <span>提交结果</span>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="请求 ID">
          {{ submitResult.requestId }}
        </el-descriptions-item>
        <el-descriptions-item label="排队号">
          {{ submitResult.queueNumber }}
        </el-descriptions-item>
        <el-descriptions-item label="充电模式">
          {{ formatMode(submitResult.queueType) }}
        </el-descriptions-item>
        <el-descriptions-item label="前车等待数量">
          {{ submitResult.waitingCount }}
        </el-descriptions-item>
        <el-descriptions-item label="当前状态">
          {{ formatStatus(submitResult.status) }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<style scoped>
.submit-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-card,
.result-card {
  border-radius: 16px;
}

.submit-form {
  max-width: 620px;
}

.result-header {
  font-weight: 600;
  color: #1f2937;
}
</style>
