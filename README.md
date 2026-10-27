# NomiQuestExtras 1.1.0

每日花费 **500 米粒**刷新 ODQ 整组任务，每人 Asia/Shanghai 自然日一次，未完成、部分完成或全部完成均可刷新。费用只由 TrMenu 原生动作处理；本插件不依赖 Vault，不扣费、不退款，不引入钱包、事务日志或恢复框架。

本插件使用独立共享 SQL 表实现全服自然日日限，并通过服务器原生库加载机制声明 MariaDB JDBC 驱动。源码审查与离线检查不代表玩家本人真实付款或跨服流程已经验收。

## 原生优先的边界

ODQ 3.0.2 的玩家 reroll 只刷新单项；管理员 `dqadmin reset quests <玩家>` 可刷新整组，但重建 timestamp、清零 recentRerolls，且该路径不转交目标玩家实体线程。TrMenu 原生日期标记可以拼接正常流程，不能因缺少额外原子性就否定它；当前保留最小桥以在本人实体线程抽齐整组、复核任务状态并在收费前原子获取独立日限。日限表唯一键为 UUID、上海日期，不读取 ODQ recent_rerolls 判断额度。

TrMenu 3.12.5 的 JEXL 每次复制上下文，可用于调用本桥；共享 JavaScript 的玩家绑定有已知串绑风险，不用于本流程。真实 JAR 字节码及 API 的离线核验不等同于真实菜单、玩家实体线程或付款流程的运行验收。

## 行为与入口

玩家 `/nomiquestreset`、旧别名 `/dailyquestreset` 及命名空间入口只打开 `DailyQuestReset` 确认菜单；本机控制台 `dailyquestreset <在线玩家>` 也只打开该玩家菜单。玩家不能通过参数执行 apply/confirm 或指定其他玩家。权限 `nomiquestreset.use` 默认允许，不授予 ODQ 管理权限。原生单任务 reroll、动态子命令别名、主命令别名和命名空间受事件及命令执行器拦截。

整组刷新使用 `QuestsManager.selectRandomQuests`，按本人当前分类数量验证抽齐并使用全新进度；当日完成数归零，保留累计总数、分类累计及原周期 timestamp，设置 `recentlyRolled=1` 并请求 ODQ 保存。抽中的任务可以与原任务相同，不保证每项不同。

自然日复用 ODQ 的实际解析设置：`timestamp_mode=1`、`renew_time=00:00`、`renew_interval=1d`、时区 `Asia/Shanghai`。设置不符时启动警告且拒绝准备，不自动修改 ODQ。旧周期过期时等待 ODQ 刷新；昨日准备不得在今日应用。

## 最小菜单 API

从 Bukkit 插件管理器获取本插件实例，调用：

```java
public void begin(org.bukkit.entity.Player player, Object trMenuSession);
public boolean prepare(org.bukkit.entity.Player player);
public String apply(org.bukkit.entity.Player player);
public void cancel(org.bukkit.entity.Player player);
```

点击在本人实体线程调用 begin(player, session)，后台 JDBC INSERT 成功后回到该实体线程，验证原会话、owner、在线状态、午夜及有效期，再调用隐藏 quota_continue 图标的真实 TrMenu handleClick。该回调先收集条件及动作，再执行普通动作；apply 仅在普通 JEXL 动作中调用一次，直接按返回值发送提示。业务顺序为：TrMenu `money 500` 条件 → prepare 成功 → TrMenu `take-money: 500` → apply 一次 → 结果提示。不要插入 delay、提前 close、切换 agent 或全局控制台派发。begin 是异步入口，不能当 boolean 使用；其余 API 不替调用者调度；prepare/apply 使用真实 Paper `Bukkit.isOwnedByCurrentRegion` 拒绝错误线程。

prepare 仅允许当前已提交资格请求调用，并在返回 true 前将本请求置为不可释放；不修改任务或余额，预抽并保存60秒内有效的一次性内存准备；失败取消本次准备，取消/关闭/退出/禁用后不能复活。apply 重新核验玩家对象、任务/进度、周期、累计、分类数量和日限，再关闭库存、复核回调后的状态并替换。准备不跨退出、重启或午夜保存。错误线程和重入调用不改正在处理的准备。明确未开始收费链的取消可按请求 UUID 删除占位；已开始收费链或数据库结果不确定时保留占位，不自动重放。无租约自动过期删除，崩溃可能保留当天额度。

| 返回值 | 含义 |
| --- | --- |
| SUCCESS | 内存应用完成，ODQ void 保存调用正常返回；不是数据库提交回执。 |
| REJECTED | 本次明确未应用任务变更，不表示未扣费。 |
| UNCERTAIN | 库存关闭回调或应用/保存已有可能副作用，需核对，不盲目重试、回滚或退款。 |

TrMenu 原生 take-money 丢弃经济回执，余额预检后仍可能扣款失败；本桥不声称经济与任务原子成功。ODQ SQL 非停服保存为异步，保留三条存储缺口：提交前可能读取旧进度、旧写可能覆盖新任务、void 保存调用没有数据库提交回执。独立日限不被 ODQ 写入覆盖。全部实例必须指向同一权威 SQL 表，且数据库具备唯一键原子性和提交持久性。未配置时入口关闭，不回退单服。

## 本地构建与检查

JDK25环境以 Java21目标编译，真实依赖仅用于编译与测试，不打包、不下载：

```sh
python3 build.py --deps-dir /path/to/dependencies --test
python3 tests/trmenu_regression.py <TrMenu-3.12.5.jar>
python3 tests/native_capabilities.py <ODailyQuests-3.0.2.jar> <TrMenu-3.12.5.jar>
```

依赖目录须含唯一 stable Purpur26.3 API、唯一 ODQ3.0.2及所需库；产物 `build/NomiQuestExtras-1.1.0.jar`，实际编译依赖指纹 `build/dependencies.json`。

Java 回归涵盖取消与重入、完整分类与新进度、ODQ 随机整组抽取、累计与周期保留、自然日日限、跨午夜、一次性及保存异常。回调测试使用夹具；真实 ODQ 选择器测试使用固定类别，不包含在线玩家权限或占位符条件。源代码、字节码、ABI 检查和离线测试均不代表玩家本人真实付款或跨服流程已经验收，也不表示上述 ODQ 存储缺口已修复。

`compat/nomidaily` 的既有源码、测试和行为原样保留，不注册、替换或部署其 PAPI 扩展。保留已有许可证声明。当前插件未退役；完整入口迁移及验证前不可卸载。
