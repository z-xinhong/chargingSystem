const TOKEN_KEY = 'token';
const LEGACY_TOKEN_KEY = 'charging_user_token';
const USER_INFO_KEY = 'user_info';

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(LEGACY_TOKEN_KEY) || '';
}

export function setToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token);
  sessionStorage.setItem(LEGACY_TOKEN_KEY, token);
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(LEGACY_TOKEN_KEY);
}

export function getUserInfo() {
  const value = sessionStorage.getItem(USER_INFO_KEY);
  return value ? JSON.parse(value) : null;
}

export function setUserInfo(userInfo) {
  sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo));
}

export function clearUserInfo() {
  sessionStorage.removeItem(USER_INFO_KEY);
}

export function clearAuth() {
  clearToken();
  clearUserInfo();
  sessionStorage.removeItem('userId');
  sessionStorage.removeItem('username');
  sessionStorage.removeItem('role');
  sessionStorage.removeItem('currentRequestId');
}
