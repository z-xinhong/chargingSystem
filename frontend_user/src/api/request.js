import axios from 'axios';
import { ElMessage } from 'element-plus';

import router from '../router';

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
});

request.interceptors.request.use(
  (config) => {
    const token = sessionStorage.getItem('token') || sessionStorage.getItem('charging_user_token');

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
  (error) => Promise.reject(error)
);

request.interceptors.response.use(
  (response) => {
    const responseData = response.data;

    if (responseData.code === 200) {
      return responseData;
    }

    ElMessage.error(responseData.message || '请求失败');
    return Promise.reject(responseData);
  },
  (error) => {
    if (error.response?.status === 401) {
      sessionStorage.clear();
      ElMessage.error('登录已过期，请重新登录');
      router.push('/login');
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常');
    }

    return Promise.reject(error);
  }
);

export default request;
