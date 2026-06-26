## ADDED Requirements

### Requirement: Self-service push subscription management UI surface location

自助 PUSH(Pushover) 订阅管理 UI（`PushSubscriptionManager`，调用 "Push subscription self-service management API"）SHALL 经由登录用户右上角顶栏的个人设置入口（「개인설정」）访问，并 SHALL NOT 默认呈现在主页（`HomePage`）首屏。该 UI 对未登录访客 SHALL NOT 可见或可达。

本需求仅约束自助管理 UI 在前端的露出位置；后端 "Push subscription self-service management API"、PUSH 投递生命周期、`push_subscriptions` 表结构均不受影响、保持不变。

#### Scenario: 登录用户经个人设置入口访问订阅管理

- **WHEN** 一个已登录用户点击右上角顶栏的用户名下拉并选择「개인설정」
- **THEN** 打开个人设置弹窗，其中呈现 `PushSubscriptionManager`（Pushover user key 注册、구독 启停、해지），用户可在此注册并管理自己的 PUSH 订阅

#### Scenario: 主页首屏不再呈现订阅管理框

- **WHEN** 一个已登录用户访问主页（`/`）
- **THEN** 主页不再渲染 `PushSubscriptionManager` / Pushover user key 输入框；订阅管理仅经个人设置弹窗可达

#### Scenario: 未登录访客不可见订阅管理 UI

- **WHEN** 一个未登录访客（无会话）访问应用
- **THEN** 顶栏不呈现用户名下拉与「개인설정」入口（仍为"로그인"按钮），且无任何路径暴露 `PushSubscriptionManager`
