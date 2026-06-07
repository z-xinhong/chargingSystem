export function formatMode(mode) {
  const modeMap = {
    FAST: '快充',
    SLOW: '慢充'
  };

  return modeMap[mode] || mode || '-';
}

export function formatStatus(status) {
  const statusMap = {
    WAITING: '等待中',
    CHARGING: '充电中',
    COMPLETED: '已完成',
    CANCELLED: '已取消',
    FAULT_STOPPED: '故障停止'
  };

  return statusMap[status] || status || '-';
}

export function getStatusTagType(status) {
  const statusTagTypeMap = {
    WAITING: 'warning',
    CHARGING: 'primary',
    COMPLETED: 'success',
    CANCELLED: 'info',
    FAULT_STOPPED: 'danger'
  };

  return statusTagTypeMap[status] || 'info';
}

export function formatMoney(value) {
  if (value === undefined || value === null || value === '') {
    return '0.00';
  }

  const amount = Number(value);
  return Number.isNaN(amount) ? '0.00' : amount.toFixed(2);
}

export function formatTime(value) {
  return value || '-';
}
