<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import { endCharging } from '../api/charging';
import { getRequestList } from '../api/chargingRequest';
import {
  formatMode,
  formatMoney,
  formatStatus,
  formatTime,
  getStatusTagType
} from '../utils/format';

const router = useRouter();
const loading = ref(false);
const endLoadingId = ref(null);
const requests = ref([]);
const endResult = ref(null);

const hasRequests = computed(() => requests.value.length > 0);

async function fetchRequests() {
  loading.value = true;

  try {
    const res = await getRequestList();
    requests.value = Array.isArray(res.data) ? res.data : [];
    syncCurrentRequestId();
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '获取排队状态失败');
  } finally {
    loading.value = false;
  }
}

function syncCurrentRequestId() {
  if (requests.value.length > 0) {
    sessionStorage.setItem('currentRequestId', String(requests.value[0].requestId));
  } else {
    sessionStorage.removeItem('currentRequestId');
  }
}

function isFinished(request) {
  return request.status === 'COMPLETED' || request.status === 'CANCELLED';
}

function canEndCharging(request) {
  return request.status === 'CHARGING' && !isFinished(request);
}

async function handleEndCharging(request) {
  try {
    await ElMessageBox.confirm(`确定要结束请求 ${request.requestId} 的充电吗？`, '结束充电', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    endLoadingId.value = request.requestId;
    const res = await endCharging({
      requestId: Number(request.requestId)
    });

    endResult.value = res.data || null;
    ElMessage.success('结束充电成功');
    await fetchRequests();
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return;
    }

    ElMessage.error(error?.message || error?.data?.message || '结束充电失败');
  } finally {
    endLoadingId.value = null;
  }
}

function goToBills() {
  router.push('/bills');
}

onMounted(fetchRequests);
</script>

<template>
  <div class="queue-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">当前排队状态</h2>
        <p class="page-desc">查看当前账号下所有未完成或已取消充电请求的排队进度、状态与预计等待时间。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="fetchRequests">
        刷新状态
      </el-button>
    </div>

    <el-card v-if="!hasRequests && !endResult" class="queue-card" shadow="hover">
      <el-empty description="当前没有可查看的充电请求，请先提交充电请求" />
    </el-card>

    <div v-if="hasRequests" class="request-list">
      <el-card
        v-for="request in requests"
        :key="request.requestId"
        class="queue-card"
        shadow="hover"
      >
        <div class="toolbar">
          <div class="request-info">
            请求 ID：<strong>{{ request.requestId }}</strong>
            <span class="queue-number">排队号：{{ request.queueNumber || '-' }}</span>
          </div>
          <el-button
            v-if="canEndCharging(request)"
            type="danger"
            :loading="endLoadingId === request.requestId"
            @click="handleEndCharging(request)"
          >
            结束充电
          </el-button>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="充电模式">
            {{ formatMode(request.queueType) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusTagType(request.status)">
              {{ formatStatus(request.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="request.message" label="状态说明">
            {{ request.message }}
          </el-descriptions-item>
          <el-descriptions-item label="请求电量">
            {{ request.requestedKwh ?? '-' }} 度
          </el-descriptions-item>
          <el-descriptions-item label="前车等待数量">
            {{ request.waitingCount }}
          </el-descriptions-item>
          <el-descriptions-item label="预计等待时间">
            {{ formatTime(request.estimatedWaitMinutes ? `${request.estimatedWaitMinutes} 分钟` : '') }}
          </el-descriptions-item>
          <el-descriptions-item label="已充电量">
            {{ request.chargedKwh ?? 0 }} 度
          </el-descriptions-item>
          <el-descriptions-item label="剩余电量">
            {{ request.remainingKwh ?? request.requestedKwh ?? '-' }} 度
          </el-descriptions-item>
          <el-descriptions-item label="剩余充电时间">
            {{ request.remainingMinutes ?? '-' }} 分钟
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>

    <el-card v-if="endResult" class="result-card" shadow="hover">
      <template #header>
        <div class="result-header">
          <span>本次费用结果</span>
        </div>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="详单 ID">
          {{ endResult.billId }}
        </el-descriptions-item>
        <el-descriptions-item label="实际充电量">
          {{ formatMoney(endResult.actualKwh) }}
        </el-descriptions-item>
        <el-descriptions-item label="充电时长（小时）">
          {{ formatMoney(endResult.durationHours) }}
        </el-descriptions-item>
        <el-descriptions-item label="电费（元）">
          {{ formatMoney(endResult.electricityFee) }}
        </el-descriptions-item>
        <el-descriptions-item label="服务费（元）">
          {{ formatMoney(endResult.serviceFee) }}
        </el-descriptions-item>
        <el-descriptions-item label="总费用（元）">
          {{ formatMoney(endResult.totalFee) }}
        </el-descriptions-item>
      </el-descriptions>

      <div class="result-actions">
        <el-button type="primary" @click="goToBills">查看充电详单列表</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.queue-page,
.request-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-title {
  margin: 0 0 8px;
  color: #111827;
}

.page-desc {
  margin: 0;
  color: #64748b;
}

.queue-card,
.result-card {
  border-radius: 16px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.request-info {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 15px;
  color: #475569;
}

.queue-number {
  color: #64748b;
}

.result-header {
  font-weight: 600;
  color: #1f2937;
}

.result-actions {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .page-header,
  .toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar .el-button,
  .result-actions .el-button {
    width: 100%;
  }

  .result-actions {
    justify-content: stretch;
  }
}
</style>
