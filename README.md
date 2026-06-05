# 智能充电桩调度计费系统

本项目是一个基于 Spring Boot、MyBatis-Plus、MySQL 和 Vue 的智能充电桩调度计费系统。系统面向电动车充电场景，支持用户提交充电请求、排队调度、充电桩状态管理、故障处理和计费详单查询。

项目后端统一包名为 `com.charging`，接口统一前缀为 `/api/v1`，数据库名为 `charging_system`。

## 主要模块

### 用户端功能

用户端主要面向普通用户，需要支持：

- 用户注册
- 用户登录
- 提交充电请求
- 修改充电请求
- 取消充电请求
- 查看自己的排队状态
- 查看充电完成后的账单或详单

用户端需要支持多用户演示场景：多个用户分别登录后提交充电请求。注意同一浏览器多个普通标签页会共享 localStorage，token 可能互相覆盖，所以最好支持无痕窗口/不同浏览器登录，或者页面内允许手动切换用户/token。用户端至少要有登录、提交充电请求、查看自己排队状态；

### 管理端功能

管理端主要面向管理员，需要支持：

- 查看所有充电桩状态
- 查看某个充电桩的排队车辆
- 启动充电桩
- 关闭充电桩
- 查看等待队列
- 查看所有充电桩队列
- 手动触发调度
- 模拟充电桩故障
- 恢复故障充电桩
- 查看调度和故障处理结果

管理员端要能查看充电桩状态、等待队列、桩队列，并触发调度和模拟故障。

### 后端功能

后端需要实现：

- 用户管理接口
- 充电请求接口
- 充电桩管理接口
- 等待队列和充电桩队列管理
- 调度算法
- 故障模拟和故障恢复
- 计费和详单接口
- JWT 登录鉴权
- 统一接口返回格式

## 调度策略

系统需要支持以下调度策略：

- `PRIORITY`：优先级调度
- `TIME_ORDER`：时间顺序调度
- `SINGLE_SHORTEST`：单次调度总充电时长最短
- `BATCH_SHORTEST`：批量调度总充电时长最短

故障发生时，系统需要将故障充电桩上的车辆重新纳入调度流程，并根据指定策略重新分配到其他可用充电桩。

## 后端分工

### 成员2：业务逻辑

负责：

- 用户管理
- 充电请求
- 计费
- 详单 API

主要文件包括：

- `UserController`
- `ChargingRequestController`
- `BillingController`
- `UserService`
- `ChargingRequestService`
- `BillingService`

### 成员1：调度核心

负责：

- 调度算法
- 充电桩监控
- 充电桩启停
- 故障模拟
- 故障恢复
- 故障处理 API

主要文件包括：

- `PileController`
- `FaultController`
- `ScheduleController`
- `PileService`
- `FaultService`
- `ScheduleService`
- `ChargingPileMapper`
- `PileQueueMapper`
- `WaitingQueueMapper`
- `FaultLogMapper`

## 数据库表

当前系统使用以下表：

- `user`
- `charging_request`
- `bill`
- `charging_pile`
- `pile_queue`
- `waiting_queue`
- `fault_log`

充电桩初始配置：

- 快充桩 3 个：`F1`、`F2`、`F3`，功率 30 度/小时
- 慢充桩 2 个：`T1`、`T2`，功率 10 度/小时

## 接口约定

统一返回格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

统一枚举值：

- 充电模式：`FAST`、`SLOW`
- 充电请求状态：`WAITING`、`CHARGING`、`COMPLETED`、`CANCELLED`
- 充电桩状态：`IDLE`、`CHARGING`、`FAULT`、`OFFLINE`

需要认证的接口通过请求头传递 token：

```text
Authorization: Bearer {token}
```
