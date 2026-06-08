import request from './request';

export function submitRequest(data) {
  return request({
    url: '/request/submit',
    method: 'post',
    data
  });
}

export function modifyRequest(data) {
  return request({
    url: '/request/modify',
    method: 'put',
    data
  });
}

export function cancelRequest(requestId) {
  return request({
    url: '/request/cancel',
    method: 'delete',
    params: {
      requestId
    }
  });
}

export function getRequestStatus(requestId) {
  return request({
    url: '/request/status',
    method: 'get',
    params: {
      requestId
    }
  });
}

export function getRequestList() {
  return request({
    url: '/request/list',
    method: 'get'
  });
}
