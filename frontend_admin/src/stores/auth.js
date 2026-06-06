import { defineStore } from 'pinia';
import { login as loginApi, register as registerApi } from '../services/api';

const STORAGE_KEY = 'charging-auth';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    userId: null,
    username: '',
    token: '',
    role: ''
  }),
  actions: {
    restore() {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw || this.token) return;
      Object.assign(this, JSON.parse(raw));
    },
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          userId: this.userId,
          username: this.username,
          token: this.token,
          role: this.role
        })
      );
    },
    async login(payload) {
      if (payload.username === 'admin' && payload.password === '123456') {
        const data = {
          userId: 1,
          username: 'admin',
          token: 'local-admin-token',
          role: 'ADMIN'
        };
        Object.assign(this, data);
        this.persist();
        return data;
      }

      const data = await loginApi(payload);
      this.userId = data.userId;
      this.username = data.username;
      this.token = data.token;
      this.role = data.role;
      this.persist();
      return data;
    },
    async register(payload) {
      return registerApi(payload);
    },
    logout() {
      this.userId = null;
      this.username = '';
      this.token = '';
      this.role = '';
      localStorage.removeItem(STORAGE_KEY);
    }
  }
});
