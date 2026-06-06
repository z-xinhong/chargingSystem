const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

function authHeader() {
  const raw = localStorage.getItem('charging-auth');
  if (!raw) return {};
  const { token } = JSON.parse(raw);
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...authHeader(),
      ...options.headers
    },
    ...options
  });

  const result = await response.json().catch(() => ({
    code: response.status,
    message: '服务器响应格式错误'
  }));

  if (!response.ok || result.code !== 200) {
    throw new Error(result.message || '请求失败');
  }

  return result.data ?? result;
}

export function get(path) {
  return request(path);
}

export function post(path, body) {
  return request(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body)
  });
}

export function put(path, body) {
  return request(path, {
    method: 'PUT',
    body: JSON.stringify(body)
  });
}

export function del(path) {
  return request(path, { method: 'DELETE' });
}
