import request from './request';

export function endCharging(data) {
  return request({
    url: '/charging/end',
    method: 'post',
    data
  });
}
