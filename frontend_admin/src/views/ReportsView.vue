<template>
  <AppShell title="运营报表">
    <div class="grid four">
      <StatCard label="累计充电次数" :value="summary.totalCount" hint="次" />
      <StatCard label="累计充电时长" :value="`${summary.totalDuration} 小时`" />
      <StatCard label="累计充电量" :value="`${summary.totalKwh} 度`" />
      <StatCard label="累计总费用" :value="`${summary.totalFee} 元`" />
    </div>

    <section class="content-block">
      <div class="section-heading">
        <h3>报表筛选</h3>
        <div class="button-row">
          <select v-model="period" @change="loadReports">
            <option value="DAY">日</option>
            <option value="WEEK">周</option>
            <option value="MONTH">月</option>
          </select>
          <button type="button" @click="loadReports">刷新</button>
        </div>
      </div>

      <table>
        <thead>
          <tr>
            <th>时间</th>
            <th>充电桩编号</th>
            <th>累计次数</th>
            <th>累计时长</th>
            <th>累计电量</th>
            <th>充电费用</th>
            <th>服务费用</th>
            <th>总费用</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in reports" :key="`${row.period}-${row.pileId}`">
            <td>{{ row.period || row.time }}</td>
            <td>{{ row.pileId }}</td>
            <td>{{ row.totalCount }}</td>
            <td>{{ row.totalDuration }} 小时</td>
            <td>{{ row.totalKwh }} 度</td>
            <td>{{ money(row.electricityFee) }} 元</td>
            <td>{{ money(row.serviceFee) }} 元</td>
            <td>{{ money(row.totalFee) }} 元</td>
          </tr>
          <tr v-if="!reports.length">
            <td colspan="8">暂无报表数据</td>
          </tr>
        </tbody>
      </table>
      <p class="feedback" v-if="message">{{ message }}</p>
    </section>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import StatCard from '../components/StatCard.vue';
import { getReports } from '../services/api';
import { mockReports } from '../services/mockData';

const period = ref('DAY');
const reports = ref([]);
const message = ref('');

const summary = computed(() =>
  reports.value.reduce(
    (acc, row) => ({
      totalCount: acc.totalCount + Number(row.totalCount || 0),
      totalDuration: round(acc.totalDuration + Number(row.totalDuration || 0)),
      totalKwh: round(acc.totalKwh + Number(row.totalKwh || 0)),
      totalFee: round(acc.totalFee + Number(row.totalFee || 0))
    }),
    { totalCount: 0, totalDuration: 0, totalKwh: 0, totalFee: 0 }
  )
);

async function loadReports() {
  message.value = '';
  try {
    const data = await getReports(period.value);
    reports.value = Array.isArray(data) ? data : data.records || data.list || [];
  } catch (error) {
    reports.value = mockReports;
    message.value = '报表接口暂未连接，当前展示演示数据';
  }
}

function money(value) {
  return Number(value || 0).toFixed(2);
}

function round(value) {
  return Math.round(value * 100) / 100;
}

onMounted(loadReports);
</script>
