import request from './request';

export function getBillList(params) {
  return request({
    url: '/bill/list',
    method: 'get',
    params
  });
}
