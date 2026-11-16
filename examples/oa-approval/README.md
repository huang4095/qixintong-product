# Java + Vue 3 OA 审批示例

提交办公申请后，列表会显示「待审批」。点击「通过」或「驳回」后，可以在列表查看结果。示例使用 JDK 自带的 HTTP 服务、Vue 3 和 TypeScript。

## 在本地运行

需要 JDK 25、Node.js 22.12 或更新版本，以及 npm。从仓库根目录打开两个终端。

在第一个终端启动 Java 接口。

```sh
java examples/oa-approval/java/ApprovalServer.java
```

在第二个终端安装依赖并启动 Vue 页面。

```sh
cd examples/oa-approval/vue
npm ci
npm run dev
```

打开 [http://127.0.0.1:5173](http://127.0.0.1:5173)。Vite 会把 `/api` 请求转发给本机 `8080` 端口的 Java 服务。启动时会显示一条示例申请，也可以提交新申请。刷新页面可继续查看审批结果，重启 Java 后会恢复到初始示例数据。

## 看代码

| 文件 | 内容 |
| --- | --- |
| [ApprovalServer.java](java/ApprovalServer.java) | 提交申请、查询列表和审批接口。每条待审批申请只允许处理一次。 |
| [App.vue](vue/src/App.vue) | 申请表单、审批列表和状态反馈。 |
| [approvals.ts](vue/src/approvals.ts) | TypeScript 数据类型和接口调用。 |

示例在本机使用内存数据，无需数据库或账号。企信通正式系统包含组织与角色权限、可配置审批流程和业务交接，正式源码按商业授权交付。[了解企信通的 OA 功能](../../README.md#开源-oa-系统选型)
