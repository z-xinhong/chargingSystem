<script setup>
import { computed, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import { cancelRequest, modifyRequest } from '../api/chargingRequest';
import { formatMode, formatStatus } from '../utils/format';

const loading = ref(false);
const resultData = ref(null);
const currentRequestId = ref(sessionStorage.getItem('currentRequestId') || '');

const form = reactive({
  mode: '',
  requestedKwh: null
});

const hasRequestId = computed(() => Boolean(currentRequestId.value));

async function handleModify() {
  if (!currentRequestId.value) {
    ElMessage.warning('当前没有充电请求，请先提交充电请求');
    return;
  }

  const payload = {
    requestId: Number(currentRequestId.value)
  };

  if (form.mode) {
    payload.mode = form.mode;
  }

  if (form.requestedKwh !== null && form.requestedKwh !== undefined && form.requestedKwh !== '') {
    if (Number(form.requestedKwh) <= 0) {
      ElMessage.warning('新请求充电量必须大于 0');
      return;
    }

    payload.requestedKwh = form.requestedKwh;
  }

  if (!payload.mode && payload.requestedKwh === undefined) {
    ElMessage.warning('至少填写新的充电模式或新的请求充电量之一');
    return;
  }

  loading.value = true;

  try {
    const res = await modifyRequest(payload);
    resultData.value = res.data || null;
    ElMessage.success('充电请求修改成功');
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '修改充电请求失败');
  } finally {
    loading.value = false;
  }
}

async function handleCancel() {
  if (!currentRequestId.value) {
    ElMessage.warning('当前没有充电请求，请先提交充电请求');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要取消当前充电请求吗？', '取消请求', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    loading.value = true;

    await cancelRequest(currentRequestId.value);
    sessionStorage.removeItem('currentRequestId');
    currentRequestId.value = '';
    resultData.value = null;
    form.mode = '';
    form.requestedKwh = null;
    ElMessage.success('充电请求已取消');
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return;
    }

    ElMessage.error(error?.message || error?.data?.message || '取消充电请求失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="modify-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">修改/取消充电请求</h2>
        <p class="page-desc">在等候区可调整请求信息，系统会根据业务规则更新排队结果。</p>
      </div>
    </div>

    <el-alert type="info" show-icon :closable="false" class="rule-alert">
      <template #title>
        等候区允许修改充电模式，修改后重新生成排队号；
        等候区允许修改充电量，排队号不变；
        充电区不允许修改请求，只能取消或结束充电。
      </template>
    </el-alert>

    <el-card v-if="!hasRequestId" class="modify-card" shadow="hover">
      <el-empty description="当前没有充电请求，请先提交充电请求" />
    </el-card>

    <template v-else>
      <el-card class="modify-card" shadow="hover">
        <div class="current-request">
          当前请求 ID：<strong>{{ currentRequestId }}</strong>
        </div>

        <el-form label-width="120px" class="modify-form">
          <el-form-item label="新充电模式">
            <el-radio-group v-model="form.mode">
              <el-radio value="FAST">快充</el-radio>
              <el-radio value="SLOW">慢充</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="新请求充电量">
            <el-input-number
              v-model="form.requestedKwh"
              :min="1"
              :precision="0"
              controls-position="right"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="loading" @click="handleModify">
              提交修改
            </el-button>
            <el-button type="danger" plain :loading="loading" @click="handleCancel">
              取消请求
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card v-if="resultData" class="result-card" shadow="hover">
        <template #header>
          <div class="result-header">
            <span>新的排队信息</span>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="请求 ID">
            {{ resultData.requestId }}
          </el-descriptions-item>
          <el-descriptions-item label="排队号">
            {{ resultData.queueNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="充电模式">
            {{ formatMode(resultData.queueType) }}
          </el-descriptions-item>
          <el-descriptions-item label="前车等待数量">
            {{ resultData.waitingCount }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            {{ formatStatus(resultData.status) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.modify-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.rule-alert,
.modify-card,
.result-card {
  border-radius: 16px;
}

.current-request {
  margin-bottom: 20px;
  font-size: 15px;
  color: #475569;
}

.modify-form {
  max-width: 620px;
}

.result-header {
  font-weight: 600;
  color: #1f2937;
}
</style>
