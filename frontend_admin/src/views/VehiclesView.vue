<template>
  <AppShell title="车辆信息维护">
    <section class="content-block">
      <div class="section-heading">
        <h3>车辆查询</h3>
        <div class="button-row">
          <input v-model.trim="keyword" placeholder="用户名 / 手机号 / 车牌" />
          <button type="button" @click="loadVehicles">查询</button>
        </div>
      </div>

      <table>
        <thead>
          <tr>
            <th>用户 ID</th>
            <th>用户名</th>
            <th>手机号</th>
            <th>车牌号</th>
            <th>电池容量</th>
            <th>当前排队号</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="vehicle in vehicles" :key="vehicle.userId">
            <td>{{ vehicle.userId }}</td>
            <td>{{ vehicle.username }}</td>
            <td>{{ vehicle.phone || '-' }}</td>
            <td>{{ vehicle.plateNo || '-' }}</td>
            <td>{{ vehicle.batteryCapacity }} 度</td>
            <td>{{ vehicle.currentRequest || '-' }}</td>
            <td>{{ formatStatus(vehicle.status) }}</td>
            <td>
              <button class="text-button" type="button" @click="editVehicle(vehicle)">编辑</button>
            </td>
          </tr>
          <tr v-if="!vehicles.length">
            <td colspan="8">暂无车辆数据</td>
          </tr>
        </tbody>
      </table>
      <p class="feedback" v-if="message">{{ message }}</p>
    </section>

    <section class="content-block" v-if="form.userId">
      <div class="section-heading">
        <h3>编辑车辆</h3>
        <div class="button-row">
          <button type="button" @click="handleSave">保存</button>
          <button class="ghost-light-button" type="button" @click="clearSelection">取消编辑</button>
        </div>
      </div>
      <div class="form-grid">
        <label>
          用户 ID
          <input v-model.number="form.userId" min="1" type="number" disabled />
        </label>
        <label>
          用户名
          <input v-model.trim="form.username" disabled />
        </label>
        <label>
          手机号
          <input v-model.trim="form.phone" />
        </label>
        <label>
          车牌号
          <input v-model.trim="form.plateNo" />
        </label>
        <label>
          电池容量（度）
          <input v-model.number="form.batteryCapacity" min="1" type="number" />
        </label>
        <label>
          状态
          <input :value="formatStatus(form.status)" disabled />
        </label>
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import { getVehicles, saveVehicle } from '../services/api';
import { mockVehicles } from '../services/mockData';

const keyword = ref('');
const vehicles = ref([]);
const message = ref('');
const form = reactive(emptyVehicle());

async function loadVehicles() {
  message.value = '';
  try {
    const data = await getVehicles(keyword.value);
    vehicles.value = Array.isArray(data) ? data : data.records || data.list || [];
  } catch (error) {
    vehicles.value = mockVehicles.filter((item) => {
      const text = `${item.username}${item.phone}${item.plateNo}`;
      return !keyword.value || text.includes(keyword.value);
    });
    message.value = '车辆维护接口暂未连接，当前展示演示数据';
  }
}

function editVehicle(vehicle) {
  Object.assign(form, vehicle);
}

async function handleSave() {
  if (!form.userId || !form.batteryCapacity) {
    message.value = '请先选择要编辑的车辆，并填写电池容量';
    return;
  }

  try {
    await saveVehicle({
      ...form,
      id: form.userId,
      role: 'USER'
    });
    message.value = '车辆信息已保存';
    await loadVehicles();
  } catch (error) {
    const index = vehicles.value.findIndex((item) => item.userId === form.userId);
    if (index >= 0) {
      vehicles.value[index] = { ...form };
    }
    message.value = '后端暂未连接，已在页面内更新演示数据';
  }
}

function clearSelection() {
  Object.assign(form, emptyVehicle());
}

function emptyVehicle() {
  return {
    userId: null,
    username: '',
    phone: '',
    plateNo: '',
    batteryCapacity: 60,
    currentRequest: '',
    status: 'WAITING'
  };
}

function formatStatus(status) {
  const map = {
    WAITING: '等待中',
    CHARGING: '充电中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FAULT_STOPPED: '故障停止'
  };
  return map[status] || status || '-';
}

onMounted(loadVehicles);
</script>
