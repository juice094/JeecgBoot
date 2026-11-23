# Admin 模块接口文档

> 版本：v1.0　更新时间：2026-11-17
> 模块：`jeecg-module-admin`　负责人：Ym3699
> 定位：**全队人员/组织数据的唯一出口**。message（成员展示）、business（选人）只调本模块接口，不直接查 `sys_user`。

---

## 一、基础信息

| 项 | 值 |
|---|---|
| Base URL | `http://localhost:8080/jeecg-boot` |
| 鉴权方式 | Header `X-Access-Token: <JWT>` |
| 文档地址 | `http://localhost:8080/jeecg-boot/doc.html`（Knife4j） |
| 统一响应 | `{"code":200,"message":"","result":...,"success":true,"timestamp":...}` |
| 分页响应 | `result` = `{"current":1,"size":10,"total":N,"records":[...]}` |

### 获取 Token

1. 浏览器登录 `http://localhost:3100`（admin / 123456）
2. F12 → Application → Local Storage → 复制 token
3. Knife4j「文档管理 → 全局参数设置」添加 `X-Access-Token`

### 错误码

| code | 含义 | 处理 |
|---|---|---|
| 200 | 成功 | — |
| 401 | Token 缺失/失效 | 重新登录 |
| 510 | 无权限 | 检查 `sys_permission` 是否配置权限码并授权到角色 |

---

## 二、权限码清单

| 权限码 | 说明 |
|---|---|
| `admin:user:list` | 人员列表 / 批量查人员 |
| `admin:user:detail` | 人员详情 |
| `admin:depart:list` | 部门树 |
| `admin:hr:statistics` | 人事统计 |
| `admin:tenant:list` | 租户列表 / 按租户查用户 |

> 改完权限数据后必须清 Redis 缓存（`docker exec jeecg-redis redis-cli FLUSHDB`）并重新登录，否则一直返回 510。

---

## 三、已实现接口

### 1. 人员选择器列表

`GET /admin/user/selector`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 姓名 / 工号 / 账号 模糊匹配 |
| departId | string | 否 | 部门 ID，传了则只查该部门 |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

权限：`admin:user:list`

响应示例：

```json
{
  "code": 200,
  "result": {
    "current": 1,
    "size": 2,
    "total": 4,
    "records": [
      {
        "id": "3d464b4ea0d2491aab8a7bde74c57e95",
        "username": "zhangsan",
        "realname": "张三",
        "workNo": "0005",
        "avatar": "https://static.jeecg.com/temp/jmlogo_1606575041993.png",
        "email": "111@1.com",
        "phone": "13426411111",
        "sex": null,
        "departId": "57197590443c44f083d42ae24ef26a2c",
        "departName": "研发部",
        "post": "开发工程师",
        "status": 1,
        "createTime": "2020-05-14 21:26:24"
      }
    ]
  },
  "success": true
}
```

---

### 2. 按 ID 批量查人员

`POST /admin/user/listByIds`

权限：`admin:user:list`

请求体：

```json
["3d464b4ea0d2491aab8a7bde74c57e95", "1714471285016895490"]
```

响应：`result` 为人员数组，字段同上（不含分页）。

> 用途：message 展示群成员、business 回显已选人员。**不要传空数组以外的超大 ID 列表**（建议 ≤ 200）。

---

### 3. 人员详情

`GET /admin/user/detail/{id}`

权限：`admin:user:detail`

响应示例：

```json
{
  "code": 200,
  "result": {
    "id": "3d464b4ea0d2491aab8a7bde74c57e95",
    "username": "zhangsan",
    "realname": "张三",
    "workNo": "0005",
    "avatar": "https://static.jeecg.com/temp/jmlogo_1606575041993.png",
    "email": "111@1.com",
    "phone": "13426411111",
    "sex": null,
    "birthday": null,
    "status": 1,
    "orgCode": null,
    "createTime": "2020-05-14 21:26:24",
    "departIds": ["57197590443c44f083d42ae24ef26a2c"],
    "departNames": ["研发部"],
    "positionIds": [],
    "positionNames": []
  },
  "success": true
}
```

用户不存在时返回 `{"code":500,"message":"用户不存在"}`。

---

### 4. 部门树

`GET /admin/depart/tree`

权限：`admin:depart:list`

参数：无

响应示例：

```json
{
  "code": 200,
  "result": [
    {
      "key": "67fc001af12a4f9b8458005d3f19934a",
      "title": "财务部",
      "value": "67fc001af12a4f9b8458005d3f19934a",
      "parentId": null,
      "children": null
    },
    {
      "key": "57197590443c44f083d42ae24ef26a2c",
      "title": "研发部",
      "value": "57197590443c44f083d42ae24ef26a2c",
      "parentId": null,
      "children": [
        {
          "key": "xxx",
          "title": "前端组",
          "value": "xxx",
          "parentId": "57197590443c44f083d42ae24ef26a2c",
          "children": null
        }
      ]
    }
  ],
  "success": true
}
```

`key` / `value` 均为部门 ID，可直接给前端 Tree 组件或 Ant Design 的 `fieldNames`。

---

### 5. 人事统计

`GET /admin/hr/statistics`

权限：`admin:hr:statistics`

参数：无

响应示例：

```json
{
  "code": 200,
  "result": {
    "departCount": 6,
    "userCount": 4,
    "activeUserCount": 4,
    "frozenUserCount": 0,
    "newUserThisMonth": 0,
    "tenantCount": 2
  },
  "success": true
}
```

| 字段 | 口径 |
|---|---|
| departCount | `sys_depart` 中 `del_flag=0` |
| userCount | `sys_user` 中 `del_flag=0` |
| activeUserCount | 同上且 `status=1` |
| frozenUserCount | 同上且 `status=2` |
| newUserThisMonth | 同上且 `create_time >= 本月 1 日` |
| tenantCount | `sys_tenant` 中 `del_flag=0` |

---

### 6. 租户列表

`GET /admin/tenant/list`

权限：`admin:tenant:list`

响应示例：

```json
{
  "code": 200,
  "result": [
    { "id": 1000, "name": "北京国贸信息技术有限公司" },
    { "id": 1001, "name": "上海某某科技有限公司" }
  ],
  "success": true
}
```

数据源：`sys_tenant`（`del_flag=0`）。

---

### 7. 按租户分页查用户

`GET /admin/tenant/user/page`

权限：`admin:tenant:list`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| tenantId | int | **是** | 租户 ID |
| keyword | string | 否 | 姓名 / 工号 / 账号 |
| pageNo | int | 否 | 默认 1 |
| pageSize | int | 否 | 默认 10 |

数据源：`sys_user_tenant`（`status='1'`）+ `sys_user`（`del_flag=0`）。

响应同「人员选择器列表」。

> **会话场景**：message 模块要展示某租户成员时调此接口，拿到 `id` 后再查 `admin_user_channel` 换取 Mattermost `external_id`。

---

## 四、数据来源对照

| 接口 | 主表 | 关联表 |
|---|---|---|
| 人员列表/详情 | `sys_user` | `sys_user_depart`、`sys_depart`、`sys_user_position`、`sys_position` |
| 部门树 | `sys_depart` | — |
| 人事统计 | `sys_depart`、`sys_user`、`sys_tenant` | — |
| 租户用户 | `sys_user_tenant` | `sys_user` |

**本模块不新建用户表、不修改原表结构**，只在业务层组装和过滤。

---

## 五、注意事项

1. **租户隔离需手动处理**
   项目多租户总开关 `OPEN_SYSTEM_TENANT_CONTROL=false`，MyBatis-Plus 不会自动加 `tenant_id` 条件。所有跨租户接口必须显式传 `tenantId`，并在 Service 校验「当前登录用户是否属于该租户」。

2. **权限缓存**
   权限数据变更后需 `FLUSHDB` 或重启后重新登录，否则 Shiro 仍用旧权限集合。

3. **N+1 已优化**
   列表接口用批量查询 + Map 组装部门/岗位，单页不会产生逐行查询。

4. **不返回敏感字段**
   VO 中不含 `password`、`salt`、`thirdId` 等字段，禁止直接返回 `SysUser` 实体。

---

## 六、待实现接口（下一阶段）

| 模块 | 接口 | 表 |
|---|---|---|
| 人员异动 | 异动列表 / 发起 / 执行 / 撤销 | `hr_employee_change` |
| 会话映射 | 渠道绑定 / 解绑 / 查询 | `admin_user_channel` |
| 后勤报修 | 报修 CRUD、指派、解决、关闭 | `log_repair_order` |
| 行政管理 | 事务 CRUD | `oa_affair` |
| 业务管理 | 主表 CRUD、成员管理 | `biz_workspace`、`biz_workspace_member` |

> 以上 6 张表的建表脚本已放在 `jeecg-module-admin/src/main/resources/flyway/sql/mysql/V4.0.0_1__admin_init.sql`，启动后端由 Flyway 自动执行。

---

## 七、给其他模块的调用约定

| 调用方 | 允许调用的接口 | 禁止行为 |
|---|---|---|
| message | 3、5、6、7、2 | 直接 `SELECT` `sys_user` / `sys_user_tenant` |
| business | 1、2、3、5 | 直接 `UPDATE` `sys_user` / `sys_user_depart` |
| 前端 | 全部（按权限） | 硬编码用户 ID |

审批类业务（请假、调动）走 business 模块的审批流，**审批通过后由 business 回调 admin 的异动执行接口**，不允许 business 直接改组织数据。
