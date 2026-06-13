<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div>
        <p class="brand-kicker">BUPT</p>
        <h1>智能充电站</h1>
      </div>

      <nav>
        <RouterLink v-for="item in navItems" :key="item.to" :to="item.to">
          {{ item.label }}
        </RouterLink>
      </nav>

      <button class="ghost-button" type="button" @click="handleLogout">退出登录</button>
    </aside>

    <main class="main-panel">
      <header class="topbar">
        <div>
          <p>{{ roleLabel }}</p>
          <h2>{{ title }}</h2>
        </div>
        <div class="topbar-right">
          <span class="sim-time">模拟时间：{{ simulatedTime || '-' }}</span>
          <strong>{{ auth.username }}</strong>
        </div>
      </header>
      <slot />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getCurrentTime } from '../services/api';
import { useAuthStore } from '../stores/auth';

defineProps({
  title: {
    type: String,
    required: true
  }
});

const auth = useAuthStore();
const router = useRouter();
const simulatedTime = ref('');
let timeTimer = null;

const roleLabel = computed(() => (auth.role === 'ADMIN' ? '管理员端' : '用户端'));
const navItems = computed(() => [
  { label: '工作台', to: '/admin' },
  { label: '调度中心', to: '/admin/scheduling' },
  { label: '车辆维护', to: '/admin/vehicles' },
  { label: '运营报表', to: '/admin/reports' }
]);

function handleLogout() {
  auth.logout();
  router.push('/login');
}

async function loadSimulatedTime() {
  try {
    const data = await getCurrentTime();
    simulatedTime.value = data.displayTime || data.currentDateTime || '';
  } catch (error) {
    simulatedTime.value = '';
  }
}

onMounted(() => {
  loadSimulatedTime();
  timeTimer = window.setInterval(loadSimulatedTime, 3000);
});

onUnmounted(() => {
  if (timeTimer) {
    window.clearInterval(timeTimer);
    timeTimer = null;
  }
});
</script>
