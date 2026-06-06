<template>
  <AppShell title="充电详单">
    <section class="content-block">
      <div class="section-heading">
        <h3>历史详单</h3>
        <button type="button" @click="loadBills">刷新</button>
      </div>

      <table>
        <thead>
          <tr>
            <th>详单编号</th>
            <th>生成时间</th>
            <th>充电桩</th>
            <th>电量</th>
            <th>时长</th>
            <th>总费用</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in bills" :key="item.billId">
            <td>{{ item.billId }}</td>
            <td>{{ item.generatedAt || item.createTime || '-' }}</td>
            <td>{{ item.pileId || '-' }}</td>
            <td>{{ item.actualKwh || item.chargingKwh || '-' }}</td>
            <td>{{ item.durationHours || '-' }}</td>
            <td>{{ item.totalFee || '-' }}</td>
          </tr>
          <tr v-if="!bills.length">
            <td colspan="6">暂无详单</td>
          </tr>
        </tbody>
      </table>
      <p class="feedback" v-if="message">{{ message }}</p>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import { getBills } from '../services/api';

const bills = ref([]);
const message = ref('');

async function loadBills() {
  message.value = '';
  try {
    const data = await getBills();
    bills.value = Array.isArray(data) ? data : data.records || data.list || [];
  } catch (error) {
    message.value = error.message;
  }
}

onMounted(loadBills);
</script>
