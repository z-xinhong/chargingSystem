# 智能充电桩调度计费系统

本项目是一个基于 Spring Boot、MyBatis-Plus、MySQL 和 Vue 的智能充电桩调度计费系统，用于模拟电动车充电站的用户排队、充电调度、故障处理和计费详单流程。

系统分为后端服务、用户端前端和管理端前端。后端接口统一前缀为 `/api/v1`，数据库名为 `charging_system`。

## 技术栈

- 后端：Spring Boot 3.2.6、MyBatis-Plus、MySQL、JWT、Lombok
- 用户端：Vue 3、Vite、Vue Router、Element Plus、Axios
- 管理端：Vue 3、Vite、Vue Router、Pinia
- 数据库：MySQL 8.x 或兼容版本
- JDK：17

## 项目结构

```text
chargingSystem
├── src/main/java/com/charging
│   ├── controller       后端接口控制层
│   ├── service          业务服务接口
│   ├── service/impl     业务服务实现
│   ├── mapper           MyBatis-Plus Mapper
│   ├── entity           数据库实体
│   ├── dto              请求参数 DTO
│   ├── common           统一返回对象
│   ├── config           后端配置
│   └── util             JWT 等工具类
├── frontend_user        用户端前端
├── frontend_admin       管理端前端
├── sql                  数据库初始化和升级脚本
├── pom.xml              后端 Maven 配置
└── README.md
```

## 核心功能

### 用户端

- 用户注册、登录
- 注册时可填写手机号、车牌号、电池容量
- 查看和修改个人信息
- 提交快充或慢充请求
- 查看自己的所有未结束请求和排队状态
- 修改等候区请求
- 结束正在充电的请求
- 查看充电详单

用户端需要支持多用户演示场景：多个用户分别登录后提交充电请求。注意同一浏览器多个普通标签页会共享 localStorage，token 可能互相覆盖，所以最好支持无痕窗口/不同浏览器登录，或者页面内允许手动切换用户/token。用户端至少要有登录、提交充电请求、查看自己排队状态；管理员端要能查看充电桩状态、等待队列、桩队列，并触发调度和模拟故障。

### 管理端

- 管理员登录
- 查看工作台统计信息
- 查看等待区队列
- 查看每个充电桩的充电区队列
- 查看充电桩状态、剩余电量和剩余时间
- 手动刷新或自动刷新调度快照
- 模拟充电桩故障
- 故障恢复并重新调度
- 查看车辆信息维护列表
- 查看运营报表
- 查看充电详单
- 启动后选择调度模式

### 后端

- 用户管理接口
- 用户个人信息接口
- 充电请求提交、修改、取消、结束接口
- 计费和详单接口
- 充电桩监控接口
- 等待队列和桩队列调度接口
- 故障模拟与故障恢复接口
- 管理端配置接口
- 车辆信息维护接口
- 运营报表接口
- JWT 登录鉴权
- 统一接口返回格式

## 调度规则

## 模拟时间

系统使用统一的后端模拟时间，不直接使用电脑当前真实时间作为业务时间。

- 每次后端启动时，模拟时间从 `2026-06-13 06:00:00` 开始
- 模拟时间每天从 `06:00` 运行到 `23:00`
- 到达 `23:00` 后自动跳到下一天 `06:00`
- 时间比例为 `1:10`，即现实 1 分钟等于模拟 10 分钟
- 用户端和管理端顶部都会显示当前模拟时间
- 充电开始时间、故障时间、恢复时间、计费时间、剩余充电时间、电价时段判断均使用模拟时间

模拟时间接口：

```text
GET /api/v1/time/current
```

### 正常调度

系统默认包含 3 个快充桩和 2 个慢充桩：

- 快充桩：`F1`、`F2`、`F3`，功率 30 度/小时
- 慢充桩：`T1`、`T2`，功率 10 度/小时

充电区每个桩最多容纳 3 辆车，其中 1 辆正在充电，2 辆等待充电。等候区容量为 5。

### 故障调度

充电桩故障时，系统会处理中断车辆和该桩队列车辆，并根据管理员选择的策略重新调度：

- `PRIORITY`：优先级调度
- `TIME_ORDER`：时间顺序调度

故障恢复时，系统会按照需求将同类型充电桩中尚未充电的车辆重新合并，并按排队号码顺序重新调度。调度期间暂停叫号，调度结束后恢复叫号。

### 扩展调度

管理端调度中心支持启动后选择调度模式，选择后本次后端运行期间锁定，不能中途切换：

- `NORMAL`：正常调度，支持故障模拟和恢复
- `SINGLE_SHORTEST`：单次调度总充电时长最短
- `BATCH_SHORTEST`：批量调度总充电时长最短

扩展调度不考虑故障和用户修改请求等特殊情况。

## 数据库初始化

MySQL 数据库只存在每个成员自己的本地环境中，不需要把真实数据库文件提交到项目里。项目中只维护建库、建表和初始化数据脚本。

初始化脚本位置：

```text
sql/charging_system.sql
```

该脚本会完成：

- 创建数据库 `charging_system`
- 创建系统需要的数据表
- 初始化 5 个充电桩

在项目根目录执行：

```bash
mysql -u root -p < sql/charging_system.sql
```

也可以进入 MySQL 后执行 `source`，路径需要换成自己电脑上的项目实际路径：

```sql
source 你的项目路径/sql/charging_system.sql;
```

例如：

```sql
source D:/projects/chargingSystem/sql/charging_system.sql;
```

如果是旧数据库缺少车牌号字段，可以执行：

```sql
source 你的项目路径/sql/upgrade_add_plate_no.sql;
```

## 本地配置

后端数据库连接配置文件：

```text
src/main/resources/application.yml
```

默认配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/charging_system?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
    username: root
    password: 123456
```

每个成员只需要把 `username` 和 `password` 改成自己本地 MySQL 的账号密码，数据库名保持 `charging_system`。

## 启动方式

### 后端

在项目根目录执行：

```bash
mvn spring-boot:run
```

或者在 IDEA 中运行启动类：

```text
com.charging.ChargingSystemApplication
```

后端默认地址：

```text
http://localhost:8080
```

### 用户端

```bash
cd frontend_user
npm install
npm run dev
```

用户端默认地址通常为：

```text
http://localhost:5173
```

### 管理端

```bash
cd frontend_admin
npm install
npm run dev
```

管理端默认地址通常为：

```text
http://localhost:5174
```

如果端口被占用，以终端输出的实际地址为准。

## 常用测试账号

如果本地数据库没有账号，可以自行插入管理员和普通用户。建议演示账号如下：

```text
管理员：admin / 123456
普通用户：user1 / 123456
普通用户：user2 / 123456
普通用户：user3 / 123456
普通用户：user4 / 123456
普通用户：user5 / 123456
普通用户：user6 / 123456
```

可以按需要通过注册页面创建普通用户，或者直接用 SQL 插入。

## 接口约定

统一返回格式：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

需要登录的接口通过请求头传递 token：

```text
Authorization: Bearer {token}
```

常用枚举：

- 充电模式：`FAST`、`SLOW`
- 充电请求状态：`WAITING`、`CHARGING`、`COMPLETED`、`CANCELLED`、`BATCH_PENDING`
- 充电桩状态：`IDLE`、`CHARGING`、`FAULT`、`OFFLINE`
- 故障调度策略：`PRIORITY`、`TIME_ORDER`
- 调度模式：`NORMAL`、`SINGLE_SHORTEST`、`BATCH_SHORTEST`

## 运行数据清理

项目包含运行数据清理逻辑，后端正常关闭时会清空运行过程中的请求、队列、详单、故障日志等数据，并保留基础用户和充电桩数据。这样下次启动后更适合重新演示完整流程。

如果手动中断后端导致清理没有执行，可以重新执行初始化脚本，或手动清空运行表。

## 提交注意事项

建议提交：

- `src/main/java`
- `src/main/resources`
- `frontend_user/src`
- `frontend_user/package.json`
- `frontend_user/package-lock.json`
- `frontend_admin/src`
- `frontend_admin/package.json`
- `frontend_admin/package-lock.json`
- `sql`
- `pom.xml`
- `README.md`

不要提交：

- `target/`
- `frontend_user/dist/`
- `frontend_admin/dist/`
- `.idea/dataSources*`
- 本地数据库文件
- 本地临时日志文件

## 可选优化方向

当前系统已经可以完成课程项目的主要演示流程。后续如果还有时间，可以考虑优化：

- 增加更完整的自动化测试
- 增加接口文档页面或 Swagger
- 将密码改为加密存储
- 将运行数据清理改为可配置开关
- 给用户端和管理端增加更细的错误提示
- 对多用户同时提交请求做更严格的并发保护
