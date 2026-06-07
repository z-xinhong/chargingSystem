<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import { endCharging } from '../api/charging';
import { getRequestStatus } from '../api/chargingRequest';
import {
  formatMode,
  formatMoney,
  formatStatus,
  formatTime,
  getStatusTagType
} from '../utils/format';

const router = useRouter();
const loading = ref(false);
const endLoading = ref(false);
const statusData = ref(null);
const endResult = ref(null);
const currentRequestId = ref(sessionStorage.getItem('currentRequestId') || '');

const hasRequestId = computed(() => Boolean(currentRequestId.value));
const isFinished = computed(() => {
  const status = statusData.value?.status;
  return status === 'COMPLETED' || status === 'CANCELLED';
});

async function fetchStatus() {
  if (!currentRequestId.value) {
    return;
  }

  loading.value = true;

  try {
    const res = await getRequestStatus(currentRequestId.value);
    statusData.value = res.data || null;

    if (isFinished.value) {
      ElMessage.info('当前请求已结束');
    }
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '获取排队状态失败');
  } finally {
    loading.value = false;
  }
}

async function handleEndCharging() {
  if (!currentRequestId.value) {
    ElMessage.warning('当前没有充电请求，请先提交充电请求');
    return;
  }

  try {
    await ElMessageBox.confirm('确定要结束当前充电吗？', '结束充电', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    endLoading.value = true;

    const res = await endCharging({
      requestId: Number(currentRequestId.value)
    });

    endResult.value = res.data || null;
    sessionStorage.removeItem('currentRequestId');
    currentRequestId.value = '';
    statusData.value = null;
    ElMessage.success('结束充电成功');
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return;
    }

    ElMessage.error(error?.message || error?.data?.message || '结束充电失败');
  } finally {
    endLoading.value = false;
  }
}

function goToBills() {
  router.push('/bills');
}

onMounted(() => {
  if (currentRequestId.value) {
    fetchStatus();
  }
});
</script>

<template>
  <div class="queue-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">当前排队状态</h2>
        <p class="page-desc">查看当前充电请求的排队进度、状态与预计等待时间。</p>
      </div>
    </div>

    <el-card v-if="!hasRequestId && !endResult" class="queue-card" shadow="hover">
      <el-empty description="当前没有充电请求，请先提交充电请求" />
    </el-card>

    <template v-if="hasRequestId">
      <el-card class="queue-card" shadow="hover">
        <div class="toolbar">
          <div class="request-info">
            当前请求 ID：<strong>{{ currentRequestId }}</strong>
          </div>
          <div class="toolbar-actions">
            <el-button type="primary" :loading="loading" @click="fetchStatus">
              刷新状态
            </el-button>
            <el-button type="danger" :loading="endLoading" @click="handleEndCharging">
              结束充电
            </el-button>
          </div>
        </div>

        <el-descriptions v-if="statusData" :column="2" border>
          <el-descriptions-item label="请求 ID">
            {{ statusData.requestId }}
          </el-descriptions-item>
          <el-descriptions-item label="排队号">
            {{ statusData.queueNumber }}
          </el-descriptions-item>
          <el-descriptions-item label="充电模式">
            {{ formatMode(statusData.queueType) }}
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusTagType(statusData.status)">
              {{ formatStatus(statusData.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="前车等待数量">
            {{ statusData.waitingCount }}
          </el-descriptions-item>
          <el-descriptions-item label="预计等待时间">
            {{ formatTime(statusData.estimatedWaitMinutes ? `${statusData.estimatedWaitMinutes} 分钟` : '') }}
          </el-descriptions-item>
        </el-descriptions>

        <el-empty
          v-else
          description="点击“刷新状态”获取当前排队信息"
        />
      </el-card>

      <el-alert
        v-if="isFinished"
        title="当前请求已结束"
        type="info"
        show-icon
        :closable="false"
      />
    </template>

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
.queue-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
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

.toolbar-actions {
  display: flex;
  gap: 12px;
}

.request-info {
  font-size: 15px;
  color: #475569;
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
  .toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-actions {
    width: 100%;
  }

  .toolbar-actions .el-button {
    flex: 1;
  }

  .result-actions {
    justify-content: stretch;
  }

  .result-actions .el-button {
    width: 100%;
  }
}
</style>
