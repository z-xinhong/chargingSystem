# BUPT 智能充电站管理端

这是基于 Vue 3 + Vite 的前后端分离管理端项目，面向“智能充电桩调度计费系统”。

## 当前实现

- 管理员登录，本地测试账号：`admin / 123456`
- 工作台：充电桩状态、累计数据、启停控制、故障模拟、队列车辆
- 调度中心：等候区 + 充电区队列可视化、优先级调度、时间顺序调度、故障恢复、单次/批量调度入口
- 车辆维护：车辆查询、新增、编辑展示
- 运营报表：日/周/月筛选，完整费用和运营字段
- 后端未连接时自动展示演示数据，方便先看页面效果

详细说明见：[docs/需求说明.md](/Users/apple/Documents/cdz/docs/需求说明.md)

## 本地运行

固定使用端口 `5173`：

```bash
npm install
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

访问：

```text
http://127.0.0.1:5173/#/login
```

## 后端地址

默认后端地址：

```text
http://localhost:8080/api/v1
```

如需修改，复制 `.env.example` 为 `.env` 并调整：

```bash
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## 页面路径

| 页面 | 路径 |
| --- | --- |
| 登录 | `/#/login` |
| 管理员工作台 | `/#/admin` |
| 调度中心 | `/#/admin/scheduling` |
| 车辆维护 | `/#/admin/vehicles` |
| 运营报表 | `/#/admin/reports` |

## 目录说明

```text
src/
  components/     通用布局和指标组件
  router/         页面路由与登录校验
  services/       后端接口封装和演示数据
  stores/         登录用户状态
  styles/         全局样式
  views/          管理端页面
docs/
  需求说明.md      管理端需求、功能和接口说明
```
