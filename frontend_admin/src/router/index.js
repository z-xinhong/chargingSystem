import { createRouter, createWebHashHistory } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import LoginView from '../views/LoginView.vue';
import AdminDashboardView from '../views/AdminDashboardView.vue';
import SchedulingView from '../views/SchedulingView.vue';
import VehiclesView from '../views/VehiclesView.vue';
import ReportsView from '../views/ReportsView.vue';
import NotFoundView from '../views/NotFoundView.vue';

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', component: LoginView, meta: { public: true } },
  { path: '/user/:pathMatch(.*)*', redirect: '/admin' },
  { path: '/admin', component: AdminDashboardView, meta: { roles: ['ADMIN'] } },
  { path: '/admin/scheduling', component: SchedulingView, meta: { roles: ['ADMIN'] } },
  { path: '/admin/vehicles', component: VehiclesView, meta: { roles: ['ADMIN'] } },
  { path: '/admin/reports', component: ReportsView, meta: { roles: ['ADMIN'] } },
  { path: '/:pathMatch(.*)*', component: NotFoundView, meta: { public: true } }
];

const router = createRouter({
  history: createWebHashHistory(),
  routes
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  auth.restore();

  if (to.meta.public) {
    return true;
  }

  if (!auth.token) {
    return '/login';
  }

  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    return '/admin';
  }

  return true;
});

export default router;
