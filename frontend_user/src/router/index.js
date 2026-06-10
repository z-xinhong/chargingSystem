import { createRouter, createWebHistory } from 'vue-router';

import Layout from '../layout/Layout.vue';
import BillList from '../views/BillList.vue';
import Home from '../views/Home.vue';
import Login from '../views/Login.vue';
import ModifyRequest from '../views/ModifyRequest.vue';
import Profile from '../views/Profile.vue';
import QueueStatus from '../views/QueueStatus.vue';
import Register from '../views/Register.vue';
import SubmitRequest from '../views/SubmitRequest.vue';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login,
    meta: {
      title: '用户登录',
      public: true
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: Register,
    meta: {
      title: '用户注册',
      public: true
    }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/home',
    children: [
      {
        path: '/home',
        name: 'Home',
        component: Home,
        meta: {
          title: '用户首页'
        }
      },
      {
        path: '/submit',
        name: 'SubmitRequest',
        component: SubmitRequest,
        meta: {
          title: '提交充电请求'
        }
      },
      {
        path: '/queue',
        name: 'QueueStatus',
        component: QueueStatus,
        meta: {
          title: '当前排队状态'
        }
      },
      {
        path: '/modify',
        name: 'ModifyRequest',
        component: ModifyRequest,
        meta: {
          title: '修改或取消充电请求'
        }
      },
      {
        path: '/bills',
        name: 'BillList',
        component: BillList,
        meta: {
          title: '充电详单列表'
        }
      },
      {
        path: '/profile',
        name: 'Profile',
        component: Profile,
        meta: {
          title: '个人信息'
        }
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('token') || sessionStorage.getItem('charging_user_token');
  const role = sessionStorage.getItem('role');

  if (to.meta.public) {
    next();
    return;
  }

  if (!token) {
    next('/login');
    return;
  }

  if (role !== 'USER') {
    sessionStorage.clear();
    next('/login');
    return;
  }

  next();
});

export default router;
