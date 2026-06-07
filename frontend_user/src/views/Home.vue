<script setup>
import { computed } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

const username = computed(() => sessionStorage.getItem('username') || '未登录用户');
const userId = computed(() => sessionStorage.getItem('userId') || '-');
const currentRequestId = computed(() => sessionStorage.getItem('currentRequestId'));
const hasCurrentRequest = computed(() => Boolean(currentRequestId.value));

function goTo(path) {
  router.push(path);
}
</script>

<template>
  <div class="home-page">
    <el-card class="welcome-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>欢迎首页</span>
        </div>
      </template>

      <h2 class="welcome-title">欢迎使用智能充电桩调度计费系统用户端</h2>
      <p class="welcome-text">当前登录用户信息如下：</p>

      <el-descriptions :column="2" border class="user-info">
        <el-descriptions-item label="用户名">
          {{ username }}
        </el-descriptions-item>
        <el-descriptions-item label="用户 ID">
          {{ userId }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-row :gutter="20" class="content-row">
      <el-col :xs="24" :md="12">
        <el-card class="status-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>当前请求状态</span>
            </div>
          </template>

          <div v-if="hasCurrentRequest" class="status-box success-box">
            <p class="status-title">当前存在充电请求</p>
            <p class="status-desc">请求编号：{{ currentRequestId }}</p>
          </div>

          <div v-else class="status-box empty-box">
            <p class="status-title">当前没有充电请求，请先提交充电请求</p>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card class="action-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>快捷入口</span>
            </div>
          </template>

          <div class="action-list">
            <el-button type="primary" size="large" @click="goTo('/submit')">
              提交充电请求
            </el-button>
            <el-button type="success" size="large" @click="goTo('/queue')">
              查看排队状态
            </el-button>
            <el-button type="warning" size="large" @click="goTo('/bills')">
              查看充电详单
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: #1f2937;
}

.welcome-card,
.status-card,
.action-card {
  border-radius: 16px;
}

.welcome-title {
  margin: 0 0 12px;
  font-size: 28px;
  color: #111827;
}

.welcome-text {
  margin: 0 0 18px;
  color: #6b7280;
  font-size: 15px;
}

.user-info {
  margin-top: 8px;
}

.content-row {
  margin-top: 0;
}

.status-box {
  padding: 18px;
  border-radius: 14px;
}

.success-box {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
}

.empty-box {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.status-title {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.status-desc {
  margin: 0;
  color: #4b5563;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-list .el-button {
  width: 100%;
  justify-content: center;
}

@media (max-width: 768px) {
  .welcome-title {
    font-size: 22px;
  }
}
</style>
