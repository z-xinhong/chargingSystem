<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { getBillList } from '../api/bill';
import { formatMoney, formatTime } from '../utils/format';

const loading = ref(false);
const tableData = ref([]);
const total = ref(0);

const pagination = reactive({
  page: 1,
  size: 10
});

function pickValue(row, keys, fallback = '-') {
  for (const key of keys) {
    const value = row?.[key];
    if (value !== undefined && value !== null && value !== '') {
      return value;
    }
  }
  return fallback;
}

function formatBillRow(row) {
  return {
    billId: pickValue(row, ['billId', 'id']),
    generatedTime: pickValue(row, ['generatedTime', 'createdAt', 'createTime', 'createdTime', 'billTime']),
    pileId: pickValue(row, ['pileId', 'chargingPileId', 'pileNo']),
    chargedKwh: pickValue(row, ['actualKwh', 'chargedAmount', 'chargedKwh']),
    durationHours: pickValue(row, ['durationHours', 'chargingDuration']),
    startTime: pickValue(row, ['startTime', 'chargingStartTime']),
    endTime: pickValue(row, ['endTime', 'chargingEndTime']),
    electricityFee: pickValue(row, ['electricityFee', 'chargingFee']),
    serviceFee: pickValue(row, ['serviceFee']),
    totalFee: pickValue(row, ['totalFee'])
  };
}

async function fetchBillList() {
  loading.value = true;

  try {
    const res = await getBillList({
      page: pagination.page,
      size: pagination.size
    });

    const rawData = res.data;
    const list = rawData?.records || rawData?.list || rawData?.items || [];
    const count = rawData?.total ?? rawData?.totalCount ?? list.length;

    tableData.value = list.map(formatBillRow);
    total.value = Number(count) || 0;
  } catch (error) {
    ElMessage.error(error?.message || error?.data?.message || '获取详单列表失败');
  } finally {
    loading.value = false;
  }
}

function handleRefresh() {
  fetchBillList();
}

function handleCurrentChange(page) {
  pagination.page = page;
  fetchBillList();
}

function handleSizeChange(size) {
  pagination.size = size;
  pagination.page = 1;
  fetchBillList();
}

onMounted(() => {
  fetchBillList();
});
</script>

<template>
  <div class="bill-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">充电详单列表</h2>
        <p class="page-desc">查看历史充电记录、充电电量、时长和费用明细。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="handleRefresh">
        刷新
      </el-button>
    </div>

    <el-card class="bill-card" shadow="hover">
      <el-table :data="tableData" border stripe v-loading="loading">
        <el-table-column prop="billId" label="详单编号" min-width="120" />
        <el-table-column prop="generatedTime" label="生成时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.generatedTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="pileId" label="充电桩编号" min-width="120" />
        <el-table-column prop="chargedKwh" label="充电电量" min-width="120">
          <template #default="{ row }">
            {{ formatMoney(row.chargedKwh) }}
          </template>
        </el-table-column>
        <el-table-column prop="durationHours" label="充电时长(小时)" min-width="130">
          <template #default="{ row }">
            {{ formatMoney(row.durationHours) }}
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" min-width="180">
          <template #default="{ row }">
            {{ formatTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="electricityFee" label="电费(元)" min-width="110">
          <template #default="{ row }">
            {{ formatMoney(row.electricityFee) }}
          </template>
        </el-table-column>
        <el-table-column prop="serviceFee" label="服务费(元)" min-width="110">
          <template #default="{ row }">
            {{ formatMoney(row.serviceFee) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalFee" label="总费用(元)" min-width="110">
          <template #default="{ row }">
            {{ formatMoney(row.totalFee) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="handleCurrentChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.bill-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.bill-card {
  border-radius: 16px;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .pagination-wrap {
    justify-content: center;
  }
}
</style>
