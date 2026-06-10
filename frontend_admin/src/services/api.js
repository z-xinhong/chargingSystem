import { del, get, post, put } from './http';

export const register = (payload) => post('/user/register', payload);
export const login = (payload) => post('/user/login', payload);

export const submitRequest = (payload) => post('/request/submit', payload);
export const modifyRequest = (payload) => put('/request/modify', payload);
export const cancelRequest = (requestId) => del(`/request/cancel?requestId=${requestId}`);
export const getRequestStatus = (requestId) => get(`/request/status?requestId=${requestId}`);

export const endCharging = (payload) => post('/charging/end', payload);
export const getBills = (page = 1, size = 10) => get(`/bill/list?page=${page}&size=${size}`);

export const getPileStatus = () => get('/pile/status');
export const getPileQueue = (pileId) => get(`/pile/queue/${pileId}`);
export const startPile = (pileId) => post(`/pile/start/${pileId}`);
export const stopPile = (pileId) => post(`/pile/stop/${pileId}`);
export const simulateFault = (pileId, schedulePolicy) =>
  post(`/fault/simulate/${pileId}`, { schedulePolicy });
export const recoverPile = (pileId) => post(`/fault/recover/${pileId}`, { schedulePolicy: 'TIME_ORDER' });

export const getSchedulingSnapshot = () => get('/schedule/snapshot');
export const getReports = (period = 'DAY') => get(`/report/list?period=${period}`);
export const getVehicles = (keyword = '') => get(`/vehicle/list?keyword=${encodeURIComponent(keyword)}`);
export const saveVehicle = (payload) => post('/vehicle/save', payload);
export const deleteVehicle = (userId) => del(`/vehicle/delete?userId=${userId}`);
export const getSystemConfig = () => get('/config/system');
export const saveSystemConfig = (payload) => post('/config/system', payload);
