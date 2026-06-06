<template>
  <AppShell title="管理员工作台">
    <div class="grid four">
      <StatCard label="快充桩" value="3 个" hint="30 度/小时" />
      <StatCard label="慢充桩" value="2 个" hint="10 度/小时" />
      <StatCard label="等候区容量" value="10 位" hint="WaitingAreaSize" />
      <StatCard label="队列长度" value="5 位" hint="ChargingQueueLen" />
    </div>

    <section class="content-block">
      <div class="section-heading">
        <h3>充电桩状态</h3>
        <button type="button" @click="loadPiles">刷新</button>
      </div>

      <div class="pile-grid">
        <article class="pile-card" v-for="pile in piles" :key="pile.pileId">
          <div class="section-heading">
            <h4>{{ pile.name || `充电桩 ${pile.pileId}` }}</h4>
            <span class="status-pill" :class="statusClass(pile.status)">{{ formatStatus(pile.status) }}</span>
          </div>
          <p>{{ formatMode(pile.type || pile.mode) }} · 功率 {{ pile.power || pilePower(pile.type || pile.mode) }} 度/小时</p>
          <dl>
            <div><dt>是否正常</dt><dd>{{ pile.isWorking === false || pile.status === 'FAULT' ? '否' : '是' }}</dd></div>
            <div><dt>累计次数</dt><dd>{{ pile.totalCount || 0 }}</dd></div>
            <div><dt>总时长</dt><dd>{{ pile.totalDuration || 0 }} 小时</dd></div>
            <div><dt>总电量</dt><dd>{{ pile.totalKwh || 0 }} 度</dd></div>
            <div><dt>队列长度</dt><dd>{{ queueCount(pile.pileId) }}/5</dd></div>
            <div><dt>当前任务</dt><dd>{{ currentCar(pile.pileId) }}</dd></div>
          </dl>
          <div class="button-row">
            <button type="button" @click="handleStart(pile.pileId)">启动</button>
            <button type="button" @click="handleStop(pile.pileId)">关闭</button>
            <button class="danger-button" type="button" @click="handleFault(pile.pileId)">模拟故障</button>
            <button class="text-button" type="button" @click="loadQueue(pile.pileId)">查看队列</button>
          </div>
        </article>
      </div>
      <p class="feedback" v-if="message">{{ message }}</p>
    </section>

    <section class="content-block">
      <div class="section-heading">
        <h3>{{ selectedPile ? `充电桩 ${selectedPile} 队列车辆` : '队列车辆' }}</h3>
        <select v-model="schedulePolicy">
          <option value="PRIORITY">优先级调度</option>
          <option value="TIME_ORDER">时间顺序调度</option>
        </select>
      </div>
      <table>
        <thead>
          <tr>
            <th>排队号</th>
            <th>用户 ID</th>
            <th>模式</th>
            <th>电池容量</th>
            <th>请求电量</th>
            <th>排队时长</th>
            <th>预计完成</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="car in queue" :key="`${car.userId}-${car.queueNumber || car.requestId}`">
            <td>{{ car.queueNumber || '-' }}</td>
            <td>{{ car.userId }}</td>
            <td>{{ formatMode(car.mode || car.queueType) }}</td>
            <td>{{ car.batteryCapacity }} 度</td>
            <td>{{ car.requestedKwh }} 度</td>
            <td>{{ car.waitingMinutes ?? car.waitingTime ?? '-' }} 分钟</td>
            <td>{{ car.estimatedFinishMinutes ?? '-' }} 分钟</td>
            <td>{{ formatStatus(car.status) }}</td>
          </tr>
          <tr v-if="!queue.length">
            <td colspan="8">请选择充电桩查看队列</td>
          </tr>
        </tbody>
      </table>
    </section>
  </AppShell>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import StatCard from '../components/StatCard.vue';
import { getPileQueue, getPileStatus, simulateFault, startPile, stopPile } from '../services/api';
import { mockPileQueues, mockPiles } from '../services/mockData';

const piles = ref([]);
const queue = ref([]);
const selectedPile = ref(null);
const message = ref('');
const schedulePolicy = ref('PRIORITY');

async function loadPiles() {
  message.value = '';
  try {
    const data = await getPileStatus();
    piles.value = normalizeList(data);
  } catch (error) {
    piles.value = mockPiles;
    message.value = '后端暂未连接，当前展示演示数据';
  }
}

async function loadQueue(pileId) {
  selectedPile.value = pileId;
  try {
    const data = await getPileQueue(pileId);
    queue.value = normalizeList(data);
  } catch (error) {
    queue.value = mockPileQueues[pileId] || [];
    message.value = '队列接口暂未连接，当前展示演示数据';
  }
}

async function handleStart(pileId) {
  try {
    await startPile(pileId);
    await loadPiles();
  } catch (error) {
    message.value = `启动接口暂不可用：${error.message}`;
  }
}

async function handleStop(pileId) {
  try {
    await stopPile(pileId);
    await loadPiles();
  } catch (error) {
    message.value = `关闭接口暂不可用：${error.message}`;
  }
}

async function handleFault(pileId) {
  try {
    await simulateFault(pileId, schedulePolicy.value);
    await loadPiles();
  } catch (error) {
    message.value = `故障调度接口暂不可用：${error.message}`;
  }
}

function normalizeList(data) {
  return Array.isArray(data) ? data : data.records || data.list || data.items || [];
}

function queueCount(pileId) {
  return (mockPileQueues[pileId] || []).length;
}

function currentCar(pileId) {
  const car = (mockPileQueues[pileId] || [])[0];
  return car ? car.queueNumber : '无';
}

function pilePower(mode) {
  return mode === 'SLOW' ? 10 : 30;
}

function formatMode(mode) {
  if (mode === 'FAST') return '快充';
  if (mode === 'SLOW') return '慢充';
  return mode || '-';
}

function formatStatus(status) {
  const map = {
    IDLE: '空闲',
    CHARGING: '充电中',
    FAULT: '故障',
    OFFLINE: '关闭',
    WAITING: '等待中',
    FAULT_STOPPED: '故障停止'
  };
  return map[status] || status || '-';
}

function statusClass(status) {
  return {
    'status-danger': status === 'FAULT',
    'status-muted': status === 'OFFLINE',
    'status-active': status === 'CHARGING'
  };
}

onMounted(loadPiles);
</script>
