<template>
  <AppShell title="调度中心">
    <div class="grid four">
      <StatCard label="正常策略" value="最短完成时长" hint="等待时间 + 自身充电时间" />
      <StatCard label="故障策略" value="2 类" hint="优先级 / 时间顺序" />
      <StatCard label="扩展策略" value="2 类" hint="单次 / 批量" />
      <StatCard label="叫号状态" :value="callingPaused ? '暂停' : '开启'" hint="故障调度期间暂停" />
    </div>

    <section class="content-block">
      <div class="section-heading">
        <h3>站内调度视图</h3>
        <button type="button" @click="loadSnapshot">刷新快照</button>
      </div>

      <div class="station-layout">
        <section class="area-panel waiting-panel">
          <div class="section-heading compact">
            <h4>等候区</h4>
            <span>{{ waitingArea.length }}/10</span>
          </div>
          <div class="vehicle-list">
            <article class="vehicle-chip" v-for="car in waitingArea" :key="car.queueNumber">
              <strong>{{ car.queueNumber }}</strong>
              <span>{{ formatMode(car.mode) }} · 所需 {{ car.requestedKwh ?? '-' }} 度</span>
              <small>预计充电 {{ car.requiredChargeMinutes ?? '-' }} 分钟 · 用户 {{ car.userId }}</small>
            </article>
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
                <span class="status-pill" :class="statusClass(pile.status)">{{ formatStatus(pile.status) }}</span>
              </header>
              <p>{{ formatMode(pile.type) }} · {{ pile.power }} 度/小时</p>
              <div class="queue-slots">
                <div
                  v-for="slot in 5"
                  :key="slot"
                  class="queue-slot"
                  :class="{ charging: slot === 1 && queueFor(pile.pileId)[slot - 1] }"
                >
                  <template v-if="queueFor(pile.pileId)[slot - 1]">
                    <strong>{{ queueFor(pile.pileId)[slot - 1].queueNumber }}</strong>
                    <span>{{ slot === 1 ? '充电中' : '等待' }}</span>
                    <small v-if="queueFor(pile.pileId)[slot - 1].status === 'CHARGING'">
                      剩 {{ queueFor(pile.pileId)[slot - 1].remainingKwh ?? '-' }} 度 / {{ queueFor(pile.pileId)[slot - 1].remainingMinutes ?? '-' }} 分钟
                    </small>
                    <small v-else>
                      需 {{ queueFor(pile.pileId)[slot - 1].requestedKwh ?? '-' }} 度 / {{ queueFor(pile.pileId)[slot - 1].requiredChargeMinutes ?? '-' }} 分钟
                    </small>
                  </template>
                  <template v-else>
                    <span>空位</span>
                  </template>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>
    </section>

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

      <div class="strategy-grid">
        <article class="strategy-card">
          <h4>优先级调度</h4>
          <p>暂停等候区叫号。同类型其它充电桩有空位时，优先调度故障队列车辆，全部迁移后恢复叫号。</p>
        </article>
        <article class="strategy-card">
          <h4>时间顺序调度</h4>
          <p>暂停等候区叫号。合并故障队列与同类型未充电车辆，按排队号码先后顺序重新分配。</p>
        </article>
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
      <p class="muted-text">恢复后若其它同类型充电桩仍有未充电车辆，则暂停等候区叫号，合并后重新调度。</p>
    </section>

    <section class="content-block">
      <div class="section-heading">
        <h3>扩展调度</h3>
        <span class="status-pill">接口预留</span>
      </div>

      <div class="strategy-grid">
        <article class="strategy-card">
          <h4>单次调度总充电时长最短</h4>
          <p>充电区出现多个空位时一次叫多个号，按充电模式分配对应充电桩，使进入车辆总完成时长最短。</p>
          <button type="button" @click="handleDispatchOnce">执行单次调度</button>
        </article>
        <article class="strategy-card">
          <h4>批量调度总充电时长最短</h4>
          <p>车辆达到全部车位容量后整批调度，不区分快慢充和到达顺序，使整批车辆总完成时长最短。</p>
          <button type="button" @click="handleDispatchBatch">执行批量调度</button>
        </article>
      </div>
      <p class="feedback" v-if="message">{{ message }}</p>
    </section>
  </AppShell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import AppShell from '../components/AppShell.vue';
import StatCard from '../components/StatCard.vue';
import {
  dispatchBatch,
  dispatchOnce,
  getSchedulingSnapshot,
  recoverPile,
  simulateFault
} from '../services/api';
import { mockPileQueues, mockPiles, mockWaitingArea } from '../services/mockData';

const message = ref('');
const piles = ref(mockPiles);
const waitingArea = ref(mockWaitingArea);
const pileQueues = ref(mockPileQueues);

const faultForm = reactive({ pileId: 3, schedulePolicy: 'PRIORITY' });
const recoverForm = reactive({ pileId: 3 });

const callingPaused = computed(() => piles.value.some((pile) => pile.status === 'FAULT'));

function queueFor(pileId) {
  return pileQueues.value[pileId] || [];
}

async function handleFault() {
  message.value = '';
  try {
    await simulateFault(faultForm.pileId, faultForm.schedulePolicy);
    message.value = '故障调度请求已提交';
    await loadSnapshot();
  } catch (error) {
    message.value = `故障调度接口暂不可用，当前保留演示状态：${error.message}`;
  }
}

async function handleRecover() {
  message.value = '';
  try {
    await recoverPile(recoverForm.pileId);
    message.value = '故障恢复调度请求已提交';
    await loadSnapshot();
  } catch (error) {
    message.value = `故障恢复接口暂不可用，当前保留演示状态：${error.message}`;
  }
}

async function handleDispatchOnce() {
  await callReservedApi(() => dispatchOnce({ mode: 'BY_MIN_TOTAL_TIME' }));
}

async function handleDispatchBatch() {
  await callReservedApi(() => dispatchBatch({ mode: 'BY_MIN_TOTAL_TIME' }));
}

async function callReservedApi(action) {
  message.value = '';
  try {
    await action();
    message.value = '扩展调度请求已提交';
    await loadSnapshot();
  } catch (error) {
    message.value = `扩展调度接口暂不可用：${error.message}`;
  }
}

async function loadSnapshot() {
  try {
    const data = await getSchedulingSnapshot();
    const snapshot = data.snapshot || data;
    piles.value = snapshot.piles || piles.value;
    waitingArea.value = snapshot.waitingArea || waitingArea.value;
    pileQueues.value = snapshot.pileQueues || pileQueues.value;
    message.value = '调度快照已刷新';
  } catch (error) {
    message.value = '后端暂未连接，当前展示演示调度视图';
  }
}

function formatMode(mode) {
  return mode === 'FAST' ? '快充' : mode === 'SLOW' ? '慢充' : mode || '-';
}

function formatStatus(status) {
  const map = {
    IDLE: '空闲',
    CHARGING: '充电中',
    FAULT: '故障',
    OFFLINE: '关闭'
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
</script>
