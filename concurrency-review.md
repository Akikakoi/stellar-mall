# stellar-mall 并发正确性审查报告

审查时间：2026-08-31
审查范围：订单、库存、支付、钱包、积分、优惠券主流程
审查方式：源码走查 + MySQL 隔离级别实测验证

---

## 零、先直接回答：有没有脏读 / 不可重复读 / 幻读？

**基本没有。** 这三类经典读问题在本项目不成立，原因如下：

| 问题 | 是否存在 | 原因 |
|---|---|---|
| 脏读 | 否 | 全项目无 READ_UNCOMMITTED；MySQL 默认 REPEATABLE READ + InnoDB MVCC，读不到未提交数据 |
| 不可重复读 | 否 | RR 下事务内快照读复用同一 ReadView，同一行重复读结果一致 |
| 幻读 | 否（快照读） | RR 下快照读不会看到新插入行；当前读（UPDATE / FOR UPDATE）由 InnoDB 间隙锁保护 |

**但是**，真正的问题不在"读"，而在以下 7 处并发缺陷，其中 3 处是已验证的真实 bug。

隔离级别使用现状：
- 全项目仅 `SkuStockServiceImpl` 显式声明 `Isolation.READ_COMMITTED`
- 其余全部走 MySQL 默认的 `REPEATABLE READ`
- **关键点**：内层方法声明的隔离级别，在加入已有事务时会被 Spring 静默忽略（DataSourceTransactionManager 默认 `validateExistingTransaction=false`，不报错）

---

## 一、P0：乐观锁重试机制在 REPEATABLE READ 下完全失效

**这是本次审查最严重的问题，已通过实测验证。**

### 位置

- `SkuStockServiceImpl.deductWithOptimisticLock()`（第 131-153 行）：20 次重试 + 指数退避
- 调用方 `OrderServiceImpl.submit()` / `submitDirect()`（第 71、102 行）

### 原因

`submit()` 的 `@Transactional(rollbackFor = Exception.class)` **未指定 isolation**，使用 MySQL 默认 RR。
`deduct()` 声明的 `READ_COMMITTED` 因加入已有事务被忽略。

于是重试循环里的 `skuMapper.getById(skuId)` 每次都是**快照读**，永远返回事务开始时的旧 version；而 `UPDATE ... WHERE version = #{version}` 是**当前读**，比对的是真实最新版本。两者永远对不上。

### 实测证据

在同一张表上复刻重试循环（并发冲突发生一次）：

```
【REPEATABLE READ = 外层 submit() 事务实际级别】
  [A] 重试读到 version = 1
  [A] 用该 version 更新 -> 影响行数 = 0  => rows=0，重试失败

【READ COMMITTED = 库存服务声明、但被外层覆盖的级别】
  [A] 重试读到 version = 2
  [A] 用该 version 更新 -> 影响行数 = 1  => 重试成功，扣减生效
```

### 后果

并发抢购同一 SKU 时，后到的请求会重试 20 次（退避累计约 2-4 秒）**全部失败**，最终抛出
"库存不足（并发冲突，已重试 20 次）"——而此时库存其实充足。表现为高并发下大面积下单失败。

### 修复方案（推荐 A）

**方案 A（推荐，最简洁）**：去掉对外部 version 的依赖，改单条原子条件扣减。
`UPDATE` 是当前读，在 RR / RC 下都正确，还少一次 SELECT。

```sql
UPDATE stellar_sku
SET stock = stock - #{qty}, version = version + 1
WHERE id = #{id} AND stock >= #{qty}
```

rows == 0 即表示库存不足，无需读-判-写循环，重试逻辑可直接删除。
（现有 `deductStock` 第 95-100 行已经是这个写法，只是 Redis 模式在用。）

**方案 B**：把 `submit()` / `submitDirect()` 的隔离级别一并改为 READ_COMMITTED，
让内层声明真正生效。改动小，但仍依赖"读-判-写"三步，不如 A 稳。

**方案 C**：重试循环内的 SELECT 改为 `SELECT ... FOR UPDATE`（当前读，能拿到最新 version）。
可行，但会把乐观锁退化成悲观锁，高并发下锁等待增加。

---

## 二、P0：`completeRefund` 无幂等保护，重复退款会双倍回滚库存

### 位置

- `OrderServiceImpl.completeRefund()`（第 830-853 行）
- `MallOrderMapper.markRefunded`（第 182-186 行）

### 原因

```sql
-- markRefunded：无条件更新，无 status 前置校验，无 is_refunded 判断
UPDATE stellar_mall_order SET status = 'REFUNDED', is_refunded = 1 WHERE id = #{id}
```

`completeRefund` 只校验了"不是 CANCELLED"，**没有校验是否已退款**，也没有用 `casUpdateStatus`。
方法体内 `skuStockService.rollback()` 直接执行。

### 后果

重复调用一次 → 库存被回滚两次 → **库存虚增**，进而导致后续超卖。
例如用户买 1 件（stock 10→9），重复退款两次后 stock 变回 11。

> 补充：管理端 `AfterSaleAdminController.confirmRefund` 标注了
> `@Idempotent(keyPrefix = "admin-aftersale-refund", windowSeconds = 300)`，
> 但该机制依赖客户端传入的 `X-Idempotency-Key`，而前端每次请求都生成新的随机 UUID，
> 因此这层保护**实际不生效**（详见第七节）。此外它只覆盖 HTTP 入口，
> 服务内部调用、定时任务、Redis 降级放行等路径依然能绕过。
> 数据层的幂等约束不可省略。

### 修复方案

1. SQL 加幂等条件（必需）：
```sql
UPDATE stellar_mall_order
SET status = 'REFUNDED', is_refunded = 1
WHERE id = #{id} AND is_refunded = 0
```
2. 方法层用 CAS 占位：`casUpdateStatus(orderId, REFUNDING, REFUNDED)`，rows==0 直接返回，不回滚库存。
3. 回滚库存前判断订单是否已退款。

---

## 三、P0：`cancelExpiredOrders` 缺少事务，异常会导致库存永久丢失

### 位置

- `OrderServiceImpl.cancelExpiredOrders()`（第 884-903 行）——**无 `@Transactional`**
- 内部 private 方法 `cancelOrderInternal()`（第 911-937 行）

### 原因

对比 `cancel()`（第 462 行）有 `@Transactional`，`cancelExpiredOrders()` 漏了。
且 `cancelOrderInternal` 是 private，即便补注解也因同类内部调用而不生效。

于是 CAS 改状态、回滚库存、解冻积分、退优惠券成为**四个独立自动提交**。
第 897 行的 `catch (Exception e)` 又把异常吞掉，只打日志。

### 后果

CAS 成功（订单已变 CANCELLED）→ 回滚库存抛异常 → 被 catch 吞掉 →
**订单取消了，库存却没还回去**，这部分库存永久丢失（少卖）。

### 修复方案

1. 给 `cancelExpiredOrders()` 加 `@Transactional(rollbackFor = Exception.class)`。
2. 把 `cancelOrderInternal` 抽到独立的 Spring Bean（如 `OrderCancelService`），
   或改为自注入代理调用，确保事务生效。
3. 定时任务每笔订单单独开事务（可用 `REQUIRES_NEW` 逐个处理），避免一批订单里
   某笔异常影响其他笔。

---

## 四、P1：`markRefunding` 无前置状态校验

### 位置

`OrderServiceImpl.markRefunding()`（第 817-821 行）

```java
mallOrderMapper.updateStatus(orderId, OrderStatus.REFUNDING.getBackendValue());
```

`updateStatus` 是无条件 UPDATE（Mapper 第 76-78 行），不校验当前状态。

### 后果

任何状态的订单都能被改成 REFUNDING，包括尚未付款的 PENDING 订单。

### 修复

改用 `casUpdateStatus(orderId, PAID, REFUNDING)` 或 `casUpdateStatus(orderId, SHIPPED, REFUNDING)`，
并校验 rows。

---

## 五、P1：Redis 锁在事务提交前释放（redis 锁模式）

### 位置

`SkuStockServiceImpl.deductWithRedisLock()`（第 94-126 行）、`rollbackWithRedisLock()`（第 171-197 行）

### 原因

`unlock` 在 `finally` 中执行，而事务是在外层 `deduct()` 方法返回后才提交。
**锁的释放早于事务提交**，临界区保护不完整。

### 现状

目前没有出问题，靠 InnoDB 行锁兜底：另一线程的 UPDATE 会阻塞等待前事务提交，
因此不会超卖。但锁的设计意图被削弱——锁保护范围小于事务范围，
一旦后续在扣减后增加其他校验逻辑，就会出现不一致。

### 修复

把锁提升到事务外层：在 Controller 或新建的 `StockLockTemplate` 中先加锁，
再调用事务方法，最后解锁。确保 加锁 → 开启事务 → 提交 → 解锁 的顺序。

---

## 六、P1：乐观锁 SQL 缺少库存兜底，存在潜在超卖路径

### 位置

`SkuMapper.xml` 第 76-91 行

```sql
UPDATE stellar_sku SET stock = stock - #{qty}, version = version + 1
WHERE id = #{id} AND version = #{version}      -- 没有 AND stock >= #{qty}
```

同时 `SkuMapper.update`（第 52-73 行）管理端改库存时 `SET stock = #{stock}`，
**不推进 version**。

### 组合风险路径

1. 用户 A 读取 sku：stock=10, version=5
2. 管理端将 stock 改为 3（version 仍为 5）
3. 用户 A 扣减 8 件：version 匹配成功，且无 `stock >= 8` 约束
4. 结果：stock = 3 - 8 = **-5**，超卖

### 现状

实测当前数据健康：204 个 SKU，无负库存，version 无 NULL（187 个为 0，说明多数从未扣减）。
漏洞尚未被触发，但路径真实存在。

### 修复

1. `deductStockWithVersion` / `rollbackStockWithVersion` 补 `AND stock >= #{qty}`。
2. 管理端改库存时同步 `version = version + 1`（更能反映"库存被外部变更"的语义）。

---

## 七、P1：幂等机制已建成，但因 Key 生成策略而实际失效

这一条值得单独说明——项目里幂等基础设施是齐的，但**没有真正生效**。

### 已有的实现（都是对的）

- 注解 `@Idempotent(keyPrefix, windowSeconds)` 与切面 `IdempotentAspect`：
  命中结果缓存直接返回 → `setIfAbsent` 抢占"处理中"标记拦截并发 → 执行后缓存成功结果。
- 后端覆盖到位：`/user/order` 的 submit / pay / cancel / confirm，
  以及管理端 order-ship、aftersale-audit、aftersale-refund 等均有标注。
- 前端在 `frontend/src/api/request.ts` 第 184-188 行对**所有写操作自动注入**
  `X-Idempotency-Key` 请求头。

### 失效点：Key 每次请求都不同

后端依赖客户端传入的 header：

```java
String clientKey = request.getHeader("X-Idempotency-Key");
if (clientKey == null || clientKey.isEmpty()) {
    return pjp.proceed();          // 无 key → 直接放行
}
String redisKey = "idempotent:" + keyPrefix + ":" + clientKey;
```

而前端生成的是**纯随机 UUID v4**（`request.ts` 第 71-81 行）：

```typescript
function generateIdempotencyKey(): string {
  return crypto.randomUUID()       // 每次请求都是新值
}
```

于是用户连点两次"提交订单"→ 两个完全不同的 UUID → 两个不同的 Redis key →
第二次请求既不会命中结果缓存，也能成功抢占"处理中"标记 → **创建两笔订单，各扣一次库存**。

### 结论

幂等目前只挡得住"同一毫秒内携带同一 key 的重放"，挡不住用户重复点击、前端重试、网络重发。

### 修复方案

1. **前端（关键）**：在"业务动作"维度生成 key，而非"HTTP 请求"维度。
   即用户点击提交时生成一次并保存在组件状态，重试 / 再次点击复用同一个 key，
   提交成功或主动取消后再重新生成。
2. **后端兜底**：无 key 时不应静默放行，可基于
   `userId + 接口 + 关键参数` 生成指纹作为降级 key，或对该接口拒绝无 key 请求。
3. **数据层兜底**：订单表对 `order_no` 建唯一索引（当前 `generateOrderNo()` 是
   时间戳 + 随机数，高并发下有碰撞风险），作为最后一道防线。

---

## 八、做得好的地方

以下几处实现是正确且值得肯定的：

- **`pay()` / `cancel()` 的 CAS 状态机**：先查状态，再用
  `casUpdateStatus(from, to)` 原子占位，rows==0 即中止。正确防住了重复支付与
  "已支付订单被取消"的竞态。
- **钱包支付**：`WalletServiceImpl.payByWallet` 先乐观锁扣余额，再 CAS 改订单状态，
  失败则抛异常回滚扣款，顺序正确。
- **优惠券领取**：`incrReceivedCount` 是条件原子更新
  （`received_count < total_count` 才 +1），正确防超发。
- **积分系统**：多处使用 `REQUIRES_NEW` 让积分流水独立于主事务提交，
  避免主流程回滚连带丢失积分记录。
- **SPU 销量累加**：`incrSaleCount` 用 `sale_count = sale_count + N` 原子递增，无丢失更新。
- **乐观锁重试的退避设计**：指数退避 + 抖动，避免同频重试空转（虽然因隔离级别问题未生效）。

---

## 九、修复优先级建议

| 优先级 | 问题 | 改动量 | 风险 |
|---|---|---|---|
| P0 | 乐观锁重试失效（方案 A：改原子条件扣减） | 小 | 低 |
| P0 | completeRefund 幂等 | 小 | 低 |
| P0 | cancelExpiredOrders 事务 | 中 | 低 |
| P1 | markRefunding 状态校验 | 小 | 低 |
| P1 | 乐观锁 SQL 补 stock 兜底 + 管理端推 version | 小 | 低 |
| P1 | Redis 锁提升出事务外 | 中 | 中 |
| P1 | 幂等 Key 改为业务动作维度生成（前端）+ 后端兜底 | 中 | 低 |

三个 P0 建议优先处理，其中第一项（改原子条件扣减）改动最小、收益最大。
第七项看似是前端改动，但它决定了整套幂等基础设施是否真的在起作用，建议一并处理。
