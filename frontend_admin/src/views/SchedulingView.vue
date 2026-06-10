<template>
  <AppShell title="调度中心">
    <section class="content-block">
      <div class="section-heading">
        <h3>调度方式</h3>
        <span class="status-pill">{{ scheduleModeLocked ? '已锁定' : '未选择' }}</span>
      </div>
      <div class="form-grid">
        <label>
          当前后端启动周期调度方式
          <select v-model="modeForm.scheduleMode" :disabled="scheduleModeLocked">
            <option value="NORMAL">正常调度</option>
            <option value="SINGLE_SHORTEST">单次调度总充电时长最短</option>
            <option value="BATCH_SHORTEST">批量调度总充电时长最短</option>
          </select>
        </label>
      </div>
      <div class="button-row">
        <button type="button" :disabled="scheduleModeLocked" @click="handleSelectMode">确认调度方式</button>
      </div>
      <p class="muted-text">调度方式每次后端启动后只能确认一次，中途不能改变。</p>
    </section>

    <div class="grid four">
      <StatCard label="调度策略" :value="modeLabel(scheduleMode)" hint="由本次启动时选择" />
      <StatCard label="故障策略" value="2 类" hint="正常调度模式可用" />
      <StatCard label="叫号状态" :value="callingPaused ? '暂停' : '开启'" hint="按后端实时状态显示" />
      <StatCard label="刷新频率" value="3 秒" hint="自动刷新队列快照" />
    </div>

    <section class="content-block" v-if="isExtensionMode">
      <div class="section-heading">
        <h3>批量创建请求</h3>
        <button type="button" @click="handleBulkCreate">创建请求</button>
      </div>

      <div class="form-grid">
        <label v-if="isSingleMode">
          充电模式
          <select v-model="bulkForm.mode">
            <option value="FAST">快充</option>
            <option value="SLOW">慢充</option>
          </select>
        </label>
        <label>
          充电度数
          <input v-model.number="bulkForm.requestedKwh" min="0.1" step="0.1" type="number" />
        </label>
        <label>
          请求数量
          <input v-model.number="bulkForm.count" min="1" type="number" />
        </label>
      </div>

      <p class="muted-text" v-if="isSingleMode">
        单次调度会按充电模式分配对应充电桩，并在多个空位时一次叫多个号，使进入充电区车辆总完成时间尽量最短。
      </p>
      <p class="muted-text" v-else>
        批量调度不区分快慢充；待调度请求达到全部车位数量 {{ batchRequiredCount }} 后，才开始一次批量调度。
      </p>

      <table v-if="isSingleMode">
        <thead>
          <tr>
            <th>请求 ID</th>
            <th>排队号</th>
            <th>用户</th>
            <th>模式</th>
            <th>请求电量</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in createdRequests" :key="item.requestId">
            <td>{{ item.requestId }}</td>
            <td>{{ item.queueNumber || '-' }}</td>
            <td>{{ item.username || item.userId }}</td>
            <td>{{ formatMode(item.queueType || item.mode) }}</td>
            <td>{{ item.requestedKwh }} 度</td>
            <td>{{ formatRequestStatus(item.status) }}</td>
          </tr>
          <tr v-if="!createdRequests.length">
            <td colspan="6">暂无本次批量创建结果</td>
          </tr>
        </tbody>
      </table>

      <table v-if="isBatchMode" class="mt-table">
        <thead>
          <tr>
            <th>待调度请求</th>
            <th>用户</th>
            <th>请求电量</th>
            <th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in batchPending" :key="item.requestId">
            <td>{{ item.queueNumber || item.requestId }}</td>
            <td>{{ item.username || item.userId }}</td>
            <td>{{ item.requestedKwh }} 度</td>
            <td>待调度</td>
          </tr>
          <tr v-if="!batchPending.length">
            <td colspan="4">暂无批量待调度请求</td>
          </tr>
        </tbody>
      </table>
    </section>

    <section class="content-block">
      <div class="section-heading">
        <h3>站内调度视图</h3>
        <div class="button-row">
          <span class="muted-text">{{ lastUpdated ? `上次刷新 ${lastUpdated}` : '等待刷新' }}</span>
          <button type="button" @click="loadSnapshot">刷新快照</button>
        </div>
      </div>

      <div class="station-layout">
        <section class="area-panel waiting-panel">
          <div class="section-heading compact">
            <h4>等候区</h4>
            <span>{{ waitingArea.length }}/5</span>
          </div>
          <div class="vehicle-list">
            <article class="vehicle-chip" v-for="car in waitingArea" :key="car.queueNumber || car.requestId">
              <strong>{{ car.queueNumber || '-' }}</strong>
              <span>{{ formatMode(car.mode) }} · 所需 {{ car.requestedKwh ?? '-' }} 度</span>
              <small>预计充电 {{ car.requiredChargeMinutes ?? '-' }} 分钟 · 用户 {{ car.userId }}</small>
            </article>
            <p class="muted-text" v-if="!waitingArea.length">暂无等候车辆</p>
          </div>
        </section>

        <section class="area-panel charging-panel">
          <div class="section-heading compact">
            <h4>充电区</h4>
            <span>3 快充 + 2 慢充</span>
          </div>
          <div class="queue-board">
            <article class="queue-lane" v-for="pile in piles" :key="pile.pileId">
              <header>
                <strong>{{ pile.name }}</strong>
                <span class="status-pill" :class="statusClass(pile.status)">{{ formatPileStatus(pile.status) }}</span>
              </header>
              <p>{{ formatMode(pile.type) }} · {{ pile.power }} 度/小时</p>
              <div class="queue-slots">
                <div
                  v-for="slot in 3"
                  :key="slot"
                  class="queue-slot"
                  :class="{ charging: slot === 1 && queueFor(pile.pileId)[slot - 1] }"
                >
                  <template v-if="queueFor(pile.pileId)[slot - 1]">
                    <strong>{{ queueFor(pile.pileId)[slot - 1].queueNumber }}</strong>
                    <span>{{ queueFor(pile.pileId)[slot - 1].status === 'CHARGING' ? '充电中' : '等待' }}</span>
                    <small v-if="queueFor(pile.pileId)[slot - 1].status === 'CHARGING'">
                      剩 {{ queueFor(pile.pileId)[slot - 1].remainingKwh ?? '-' }} 度 /
                      {{ queueFor(pile.pileId)[slot - 1].remainingMinutes ?? '-' }} 分钟
                    </small>
                    <small v-else>
                      需 {{ queueFor(pile.pileId)[slot - 1].requestedKwh ?? '-' }} 度 /
                      {{ queueFor(pile.pileId)[slot - 1].requiredChargeMinutes ?? '-' }} 分钟
                    </small>
                  </template>
                  <template v-else>
                    <span>空位</span>
                  </template>
                </div>
              </div>
            </article>
            <p class="muted-text" v-if="!piles.length">暂无充电桩快照</p>
          </div>
        </section>
      </div>
    </section>

    <template v-if="scheduleMode === 'NORMAL'">
      <section class="content-block">
        <div class="section-heading">
          <h3>故障调度</h3>
          <button type="button" @click="handleFault">模拟故障并调度</button>
        </div>

        <div class="form-grid">
          <label>
            故障充电桩 ID
            <input v-model.number="faultForm.pileId" min="1" type="number" />
          </label>
          <label>
            调度策略
            <select v-model="faultForm.schedulePolicy">
              <option value="PRIORITY">优先级调度</option>
              <option value="TIME_ORDER">时间顺序调度</option>
            </select>
          </label>
        </div>
      </section>

      <section class="content-block">
        <div class="section-heading">
          <h3>故障恢复</h3>
          <button type="button" @click="handleRecover">恢复并重新调度</button>
        </div>
        <div class="form-grid">
          <label>
            恢复充电桩 ID
            <input v-model.number="recoverForm.pileId" min="1" type="number" />
          </label>
        </div>
      </section>
    </template>

    <p class="feedback" v-if="message">{{ message }}</p>
  </AppShell>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import StatCard from '../components/StatCard.vue';
import {
  createScheduleBulkRequests,
  getSchedulingSnapshot,
  recoverPile,
  selectScheduleMode,
  simulateFault
} from '../services/api';

const REFRESH_INTERVAL_MS = 3000;

const message = ref('');
const piles = ref([]);
const waitingArea = ref([]);
const pileQueues = ref({});
const batchPending = ref([]);
const createdRequests = ref([]);
const callingPaused = ref(false);
const scheduleMode = ref('NORMAL');
const scheduleModeLocked = ref(false);
const batchRequiredCount = ref(20);
const lastUpdated = ref('');
let snapshotTimer = null;

const modeForm = reactive({ scheduleMode: 'NORMAL' });
const bulkForm = reactive({ mode: 'FAST', requestedKwh: 10, count: 1 });
const faultForm = reactive({ pileId: 3, schedulePolicy: 'PRIORITY' });
const recoverForm = reactive({ pileId: 3 });

const isSingleMode = computed(() => scheduleMode.value === 'SINGLE_SHORTEST');
const isBatchMode = computed(() => scheduleMode.value === 'BATCH_SHORTEST');
const isExtensionMode = computed(() => isSingleMode.value || isBatchMode.value);

function queueFor(pileId) {
  return pileQueues.value[pileId] || [];
}

async function handleSelectMode() {
  try {
    const data = await selectScheduleMode(modeForm.scheduleMode);
    scheduleMode.value = data.scheduleMode || modeForm.scheduleMode;
    scheduleModeLocked.value = Boolean(data.locked);
    message.value = '调度方式已确认';
    await loadSnapshot(false);
    startSnapshotTimer();
  } catch (error) {
    message.value = `调度方式确认失败：${error.message}`;
  }
}

async function handleBulkCreate() {
  if (!bulkForm.requestedKwh || bulkForm.requestedKwh <= 0 || !bulkForm.count || bulkForm.count <= 0) {
    message.value = '充电度数和请求数量必须大于 0';
    return;
  }
  try {
    const payload = {
      requestedKwh: bulkForm.requestedKwh,
      count: bulkForm.count
    };
    if (isSingleMode.value) {
      payload.mode = bulkForm.mode;
    }
    const data = await createScheduleBulkRequests(payload);
    createdRequests.value = isSingleMode.value ? data.requests || [] : [];
    message.value = `已创建 ${data.createdCount || 0} 条请求`;
    await loadSnapshot(false);
  } catch (error) {
    message.value = `批量创建请求失败：${error.message}`;
  }
}

async function handleFault() {
  message.value = '';
  try {
    await simulateFault(faultForm.pileId, faultForm.schedulePolicy);
    message.value = '故障调度请求已提交';
    await loadSnapshot(false);
  } catch (error) {
    message.value = `故障调度接口暂不可用：${error.message}`;
  }
}

async function handleRecover() {
  message.value = '';
  try {
    await recoverPile(recoverForm.pileId);
    message.value = '故障恢复调度请求已提交';
    await loadSnapshot(false);
  } catch (error) {
    message.value = `故障恢复接口暂不可用：${error.message}`;
  }
}

async function loadSnapshot(showMessage = true) {
  try {
    const data = await getSchedulingSnapshot();
    const snapshot = data.snapshot || data;
    piles.value = snapshot.piles || [];
    waitingArea.value = snapshot.waitingArea || [];
    pileQueues.value = snapshot.pileQueues || {};
    batchPending.value = snapshot.batchPending || [];
    callingPaused.value = Boolean(snapshot.pausedCalling);
    scheduleMode.value = snapshot.scheduleMode || 'NORMAL';
    scheduleModeLocked.value = Boolean(snapshot.scheduleModeLocked);
    if (scheduleModeLocked.value) {
      modeForm.scheduleMode = scheduleMode.value;
    }
    batchRequiredCount.value = snapshot.batchRequiredCount || 20;
    lastUpdated.value = new Date().toLocaleTimeString();
    if (showMessage) {
      message.value = '调度快照已刷新';
    }
  } catch (error) {
    if (showMessage) {
      message.value = `调度快照暂时无法刷新：${error.message}`;
    }
  }
}

function modeLabel(mode) {
  const map = {
    NORMAL: '正常调度',
    SINGLE_SHORTEST: '单次最短',
    BATCH_SHORTEST: '批量最短'
  };
  return map[mode] || mode || '-';
}

function formatMode(mode) {
  if (mode === 'FAST') return '快充';
  if (mode === 'SLOW') return '慢充';
  if (mode === 'BATCH') return '快充';
  return mode || '-';
}

function formatPileStatus(status) {
  const map = {
    IDLE: '空闲',
    CHARGING: '充电中',
    FAULT: '故障',
    OFFLINE: '关闭'
  };
  return map[status] || status || '-';
}

function formatRequestStatus(status) {
  const map = {
    WAITING: '等待中',
    CHARGING: '充电中',
    BATCH_PENDING: '待调度'
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

function startSnapshotTimer() {
  if (!scheduleModeLocked.value || snapshotTimer) {
    return;
  }
  snapshotTimer = window.setInterval(() => loadSnapshot(false), REFRESH_INTERVAL_MS);
}

onMounted(() => {
  loadSnapshot().then(startSnapshotTimer);
});

onUnmounted(() => {
  if (snapshotTimer) {
    window.clearInterval(snapshotTimer);
    snapshotTimer = null;
  }
});
</script>
