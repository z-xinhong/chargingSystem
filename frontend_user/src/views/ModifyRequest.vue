<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import { cancelRequest, getRequestList, modifyRequest } from '../api/chargingRequest';
import { formatMode, formatStatus, getStatusTagType } from '../utils/format';

const loading = ref(false);
const actionLoadingId = ref(null);
const requests = ref([]);
const forms = reactive({});
const resultData = ref(null);

function ensureForm(requestId) {
  if (!forms[requestId]) {
    forms[requestId] = {
      mode: '',
      requestedKwh: null
    };
  }
  return forms[requestId];
}

async function loadRequests() {
  loading.value = true;
  try {
    const res = await getRequestList();
    requests.value = Array.isArray(res.data) ? res.data : [];
    requests.value.forEach((request) => ensureForm(request.requestId));
    if (requests.value.length > 0) {
      sessionStorage.setItem('currentRequestId', String(requests.value[0].requestId));
    } else {
      sessionStorage.removeItem('currentRequestId');
    }
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '获取充电请求失败');
  } finally {
    loading.value = false;
  }
}

function canModify(request) {
  return request.location === 'WAITING_AREA';
}

function canCancel(request) {
  return request.status !== 'CHARGING' && request.location !== 'CHARGING_AREA';
}

function locationText(location) {
  const map = {
    WAITING_AREA: '等候区',
    PILE_QUEUE: '充电区等待队列',
    CHARGING_AREA: '充电中',
    BATCH_PENDING: '待调度',
    NONE: '未在队列'
  };
  return map[location] || location || '-';
}

async function handleModify(request) {
  if (!canModify(request)) {
    ElMessage.warning('充电区请求不允许修改');
    return;
  }

  const form = ensureForm(request.requestId);
  const payload = {
    requestId: Number(request.requestId)
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

  actionLoadingId.value = request.requestId;
  try {
    const res = await modifyRequest(payload);
    resultData.value = res.data || null;
    form.mode = '';
    form.requestedKwh = null;
    ElMessage.success('充电请求修改成功');
    await loadRequests();
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '修改充电请求失败');
  } finally {
    actionLoadingId.value = null;
  }
}

async function handleCancel(request) {
  if (!canCancel(request)) {
    ElMessage.warning('正在充电的请求不能取消，请到当前排队状态页面点击结束充电');
    return;
  }

  try {
    await ElMessageBox.confirm(`确定要取消请求 ${request.requestId} 吗？`, '取消请求', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    actionLoadingId.value = request.requestId;
    await cancelRequest(request.requestId);
    ElMessage.success('充电请求已取消');
    await loadRequests();
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return;
    }
    ElMessage.error(error?.message || error?.data?.message || '取消充电请求失败');
  } finally {
    actionLoadingId.value = null;
  }
}

onMounted(loadRequests);
</script>

<template>
  <div class="modify-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">修改/取消充电请求</h2>
        <p class="page-desc">查看当前账号下所有未结束请求，并按业务规则修改或取消。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadRequests">
        刷新请求
      </el-button>
    </div>

    <el-alert type="info" show-icon :closable="false" class="rule-alert">
      <template #title>
        等候区允许修改充电模式，修改后重新生成排队号；等候区允许修改充电量，排队号不变。
        充电区不允许修改；正在充电的请求不能取消，只能在当前排队状态页面结束充电。
      </template>
    </el-alert>

    <el-card v-if="!requests.length" class="modify-card" shadow="hover">
      <el-empty description="当前没有未结束的充电请求，请先提交充电请求" />
    </el-card>

    <div v-else class="request-list">
      <el-card
        v-for="request in requests"
        :key="request.requestId"
        class="modify-card"
        shadow="hover"
      >
        <template #header>
          <div class="card-header">
            <span>请求 {{ request.requestId }} / {{ request.queueNumber }}</span>
            <el-tag :type="getStatusTagType(request.status)">
              {{ formatStatus(request.status) }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border class="request-info">
          <el-descriptions-item label="当前位置">
            {{ locationText(request.location) }}
          </el-descriptions-item>
          <el-descriptions-item label="充电模式">
            {{ formatMode(request.queueType) }}
          </el-descriptions-item>
          <el-descriptions-item label="请求电量">
            {{ request.requestedKwh ?? '-' }} 度
          </el-descriptions-item>
          <el-descriptions-item label="预计等待时间">
            {{ request.estimatedWaitMinutes ?? '-' }} 分钟
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="120px" class="modify-form">
          <el-form-item label="新充电模式">
            <el-radio-group v-model="ensureForm(request.requestId).mode" :disabled="!canModify(request)">
              <el-radio value="FAST">快充</el-radio>
              <el-radio value="SLOW">慢充</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="新请求电量">
            <el-input-number
              v-model="ensureForm(request.requestId).requestedKwh"
              :min="1"
              :precision="0"
              controls-position="right"
              :disabled="!canModify(request)"
            />
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              :loading="actionLoadingId === request.requestId"
              :disabled="!canModify(request)"
              @click="handleModify(request)"
            >
              提交修改
            </el-button>
            <el-button
              v-if="canCancel(request)"
              type="danger"
              plain
              :loading="actionLoadingId === request.requestId"
              @click="handleCancel(request)"
            >
              取消请求
            </el-button>
            <el-button v-else type="danger" plain disabled>
              正在充电，请使用结束充电
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

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
  </div>
</template>

<style scoped>
.modify-page,
.request-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.rule-alert,
.modify-card,
.result-card {
  border-radius: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}

.request-info {
  margin-bottom: 20px;
}

.modify-form {
  max-width: 720px;
}

.result-header {
  font-weight: 600;
  color: #1f2937;
}
</style>
