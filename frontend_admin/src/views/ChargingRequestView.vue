<template>
  <AppShell title="充电请求">
    <section class="content-block">
      <div class="section-heading">
        <h3>提交或修改请求</h3>
        <button type="button" @click="handleSubmit">{{ current.requestId ? '修改请求' : '提交请求' }}</button>
      </div>

      <div class="form-grid">
        <label>
          充电模式
          <select v-model="form.mode">
            <option value="FAST">快充 FAST</option>
            <option value="SLOW">慢充 SLOW</option>
          </select>
        </label>
        <label>
          请求充电量（度）
          <input v-model.number="form.requestedKwh" min="1" type="number" />
        </label>
      </div>
      <p class="feedback" v-if="message">{{ message }}</p>
    </section>

    <section class="content-block">
      <div class="section-heading">
        <h3>当前排队状态</h3>
        <div class="button-row">
          <button type="button" @click="refreshStatus" :disabled="!current.requestId">刷新</button>
          <button type="button" @click="handleEnd" :disabled="!current.requestId">结束充电</button>
          <button class="danger-button" type="button" @click="handleCancel" :disabled="!current.requestId">
            取消请求
          </button>
        </div>
      </div>

      <div class="grid five">
        <StatCard label="请求 ID" :value="current.requestId || '-'" />
        <StatCard label="排队号码" :value="current.queueNumber || '-'" />
        <StatCard label="类型" :value="current.queueType || '-'" />
        <StatCard label="状态" :value="current.status || '-'" />
        <StatCard label="预计等待" :value="waitText" />
      </div>
    </section>

    <section class="content-block" v-if="bill">
      <h3>本次详单</h3>
      <div class="grid three">
        <StatCard label="详单 ID" :value="bill.billId" />
        <StatCard label="实际充电量" :value="`${bill.actualKwh} 度`" />
        <StatCard label="总费用" :value="`${bill.totalFee} 元`" />
      </div>
    </section>
  </AppShell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import StatCard from '../components/StatCard.vue';
import {
  cancelRequest,
  endCharging,
  getRequestStatus,
  modifyRequest,
  submitRequest
} from '../services/api';

const form = reactive({ mode: 'FAST', requestedKwh: 30 });
const current = reactive({});
const bill = ref(null);
const message = ref('');

const waitText = computed(() =>
  current.estimatedWaitMinutes === undefined ? '-' : `${current.estimatedWaitMinutes} 分钟`
);

async function handleSubmit() {
  message.value = '';
  try {
    const data = current.requestId
      ? await modifyRequest({ requestId: current.requestId, ...form })
      : await submitRequest(form);
    Object.assign(current, data);
    message.value = '操作成功';
  } catch (error) {
    message.value = error.message;
  }
}

async function refreshStatus() {
  if (!current.requestId) return;
  try {
    Object.assign(current, await getRequestStatus(current.requestId));
  } catch (error) {
    message.value = error.message;
  }
}

async function handleCancel() {
  if (!current.requestId) return;
  try {
    await cancelRequest(current.requestId);
    Object.keys(current).forEach((key) => delete current[key]);
    message.value = '请求已取消';
  } catch (error) {
    message.value = error.message;
  }
}

async function handleEnd() {
  if (!current.requestId) return;
  try {
    bill.value = await endCharging({ requestId: current.requestId });
    Object.assign(current, { status: 'COMPLETED' });
  } catch (error) {
    message.value = error.message;
  }
}
</script>
