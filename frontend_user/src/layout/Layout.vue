<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

const route = useRoute();
const router = useRouter();

const activeMenu = computed(() => route.path);
const username = computed(() => sessionStorage.getItem('username') || '用户');

const menuList = [
  { index: '/home', title: '首页' },
  { index: '/submit', title: '提交充电请求' },
  { index: '/queue', title: '当前排队状态' },
  { index: '/modify', title: '修改/取消请求' },
  { index: '/bills', title: '充电详单' },
  { index: '/profile', title: '个人信息' }
];

function handleSelect(path) {
  router.push(path);
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出当前账号吗？', '退出登录', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    });

    sessionStorage.clear();
    ElMessage.success('已退出登录');
    router.push('/login');
  } catch (error) {
    return error;
  }
}
</script>

<template>
  <el-container class="layout-container">
    <el-header class="layout-header">
      <div class="header-left">
        <h1 class="system-title">智能充电桩调度计费系统 - 用户端</h1>
      </div>

      <div class="header-right">
        <span class="username-text">当前用户：{{ username }}</span>
        <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="220px" class="layout-aside">
        <el-menu
          :default-active="activeMenu"
          class="side-menu"
          router
          @select="handleSelect"
        >
          <el-menu-item
            v-for="item in menuList"
            :key="item.index"
            :index="item.index"
          >
            {{ item.title }}
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="layout-main">
        <div class="main-card">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout-container {
  min-height: 100vh;
  background: #f5f7fb;
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.05);
}

.system-title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.username-text {
  font-size: 14px;
  color: #475569;
}

.layout-aside {
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
}

.side-menu {
  height: 100%;
  border-right: none;
}

.layout-main {
  padding: 24px;
}

.main-card {
  min-height: calc(100vh - 112px);
  padding: 24px;
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
}

@media (max-width: 768px) {
  .layout-header {
    height: auto;
    padding: 16px;
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .system-title {
    font-size: 20px;
  }

  .layout-main {
    padding: 16px;
  }

  .main-card {
    padding: 16px;
  }
}
</style>
