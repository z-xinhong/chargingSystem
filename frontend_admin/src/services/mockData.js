export const mockPiles = [
  { pileId: 1, name: 'F1 快充桩', type: 'FAST', status: 'CHARGING', isWorking: true, totalCount: 18, totalDuration: 12.6, totalKwh: 378, power: 30 },
  { pileId: 2, name: 'F2 快充桩', type: 'FAST', status: 'IDLE', isWorking: true, totalCount: 15, totalDuration: 10.2, totalKwh: 306, power: 30 },
  { pileId: 3, name: 'F3 快充桩', type: 'FAST', status: 'FAULT', isWorking: false, totalCount: 11, totalDuration: 8.4, totalKwh: 252, power: 30 },
  { pileId: 4, name: 'T1 慢充桩', type: 'SLOW', status: 'CHARGING', isWorking: true, totalCount: 9, totalDuration: 20.5, totalKwh: 205, power: 10 },
  { pileId: 5, name: 'T2 慢充桩', type: 'SLOW', status: 'IDLE', isWorking: true, totalCount: 7, totalDuration: 16.1, totalKwh: 161, power: 10 }
];

export const mockWaitingArea = [
  { queueNumber: 'F4', userId: 10004, mode: 'FAST', batteryCapacity: 70, requestedKwh: 30, waitingMinutes: 12 },
  { queueNumber: 'F5', userId: 10005, mode: 'FAST', batteryCapacity: 60, requestedKwh: 20, waitingMinutes: 8 },
  { queueNumber: 'T3', userId: 10006, mode: 'SLOW', batteryCapacity: 50, requestedKwh: 25, waitingMinutes: 10 },
  { queueNumber: 'T4', userId: 10007, mode: 'SLOW', batteryCapacity: 55, requestedKwh: 18, waitingMinutes: 6 }
];

export const mockPileQueues = {
  1: [
    { queueNumber: 'F1', userId: 10001, mode: 'FAST', batteryCapacity: 65, requestedKwh: 28, waitingMinutes: 0, status: 'CHARGING', estimatedFinishMinutes: 56 },
    { queueNumber: 'F6', userId: 10008, mode: 'FAST', batteryCapacity: 80, requestedKwh: 24, waitingMinutes: 3, status: 'WAITING', estimatedFinishMinutes: 104 }
  ],
  2: [],
  3: [
    { queueNumber: 'F2', userId: 10002, mode: 'FAST', batteryCapacity: 60, requestedKwh: 18, waitingMinutes: 0, status: 'FAULT_STOPPED', estimatedFinishMinutes: '-' },
    { queueNumber: 'F3', userId: 10003, mode: 'FAST', batteryCapacity: 75, requestedKwh: 36, waitingMinutes: 20, status: 'WAITING', estimatedFinishMinutes: '-' }
  ],
  4: [
    { queueNumber: 'T1', userId: 10009, mode: 'SLOW', batteryCapacity: 55, requestedKwh: 20, waitingMinutes: 0, status: 'CHARGING', estimatedFinishMinutes: 120 }
  ],
  5: [
    { queueNumber: 'T2', userId: 10010, mode: 'SLOW', batteryCapacity: 48, requestedKwh: 16, waitingMinutes: 4, status: 'WAITING', estimatedFinishMinutes: 96 }
  ]
};

export const mockReports = [
  { period: '2026-06-07', pileId: 1, totalCount: 18, totalDuration: 12.6, totalKwh: 378, electricityFee: 298.2, serviceFee: 302.4, totalFee: 600.6 },
  { period: '2026-06-07', pileId: 2, totalCount: 15, totalDuration: 10.2, totalKwh: 306, electricityFee: 241.8, serviceFee: 244.8, totalFee: 486.6 },
  { period: '2026-06-07', pileId: 3, totalCount: 11, totalDuration: 8.4, totalKwh: 252, electricityFee: 199.1, serviceFee: 201.6, totalFee: 400.7 },
  { period: '2026-06-07', pileId: 4, totalCount: 9, totalDuration: 20.5, totalKwh: 205, electricityFee: 151.4, serviceFee: 164, totalFee: 315.4 },
  { period: '2026-06-07', pileId: 5, totalCount: 7, totalDuration: 16.1, totalKwh: 161, electricityFee: 119.5, serviceFee: 128.8, totalFee: 248.3 }
];

export const mockVehicles = [
  { userId: 10001, username: 'zhangsan', phone: '13800138000', batteryCapacity: 65, plateNo: '京A10001', currentRequest: 'F1', status: 'CHARGING' },
  { userId: 10002, username: 'lisi', phone: '13800138001', batteryCapacity: 60, plateNo: '京A10002', currentRequest: 'F2', status: 'FAULT_STOPPED' },
  { userId: 10004, username: 'wangwu', phone: '13800138003', batteryCapacity: 70, plateNo: '京A10004', currentRequest: 'F4', status: 'WAITING' },
  { userId: 10006, username: 'zhaoliu', phone: '13800138005', batteryCapacity: 50, plateNo: '京A10006', currentRequest: 'T3', status: 'WAITING' }
];
