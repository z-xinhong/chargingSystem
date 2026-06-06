<template>
  <AppShell title="用户工作台">
    <div class="grid three">
      <StatCard label="快充功率" value="30 度/小时" hint="FAST" />
      <StatCard label="慢充功率" value="10 度/小时" hint="SLOW" />
      <StatCard label="服务费" value="0.80 元/度" hint="固定单价" />
    </div>

    <section class="content-block">
      <div class="section-heading">
        <h3>充电服务</h3>
        <RouterLink class="text-button" to="/user/request">提交请求</RouterLink>
      </div>
      <p>用户可提交快充或慢充请求，系统返回排队号码、前车等待数量和预计等待时间。</p>
    </section>
  </AppShell>
</template>

<script setup>
import AppShell from '../components/AppShell.vue';
import StatCard from '../components/StatCard.vue';
</script>
