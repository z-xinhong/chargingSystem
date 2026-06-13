import request from './request';

export function register(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  });
}

export function login(data) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  });
}

export function getProfile() {
  return request({
    url: '/user/profile',
    method: 'get'
  });
}

export function updateProfile(data) {
  return request({
    url: '/user/profile',
    method: 'put',
    data
  });
}

export function getCurrentTime() {
  return request({
    url: '/time/current',
    method: 'get'
  });
}
