# nomidaily 配套 PAPI 扩展

这是既有 `NomiDailyExpansion` 的独立源码归档，不是 Bukkit 插件或独立仓库。源码逐字节保留输入实现，原作者标识 `NomiTeam`、版本 `1.0.0`、必需插件 `ODailyQuests` 均不变；输入源码没有额外版权或许可证声明。这里只迁移 nomidaily。

## 构建

需要 Python 3.10+、JDK 21+（输出 Java 21 字节码）。准备本地依赖目录，包含真实 PlaceholderAPI 2.12.3、ODailyQuests 3.0.2、一个适用的 Purpur API，以及 API 所需库。可递归放置 JAR，但每个 API 类只能有一个提供者，避免多个服务端 API 版本冲突。不下载依赖，不打包第三方代码。

```sh
python3 build.py --deps-dir /path/to/dependencies
# 或设置 NOMI_DEPS_DIR 后运行
python3 build.py
python3 tests/regression.py --deps-dir /path/to/dependencies --baseline-jar /path/to/original-expansion.jar
```

产物为 `build/Expansion-nomidaily.jar`；仅包含自有 `NomiDailyExpansion.class` 和标准 manifest，没有 `plugin.yml`。`build/source-artifact.json` 记录源码、构建脚本、依赖和产物 SHA-256，以及编译器版本。固定 JDK、源码和依赖时，ZIP 条目顺序、时间、权限与 manifest 固定，可重复得到相同 JAR。依赖与产物均不纳入源码管理。

## 既有行为与回归

唯一参数为严格区分大小写的 `can_reset`（完整占位符 `%nomidaily_can_reset%`）：按 `OfflinePlayer.getName()` 精确查询 ODQ 的 `QuestsManager.getActiveQuests()`；记录存在、任务集合非空且 `getRecentlyRolled() < 1` 时返回字符串 `"1"`，否则返回 `"0"`。不递增次数，不更改任务。玩家或玩家名为空、记录尚未载入或任务为空均返回 `"0"`。

其他参数（包括 `loaded`）返回 `null`。本扩展没有额外的 loaded 占位符。负次数仍按 `< 1` 返回 `"1"`，空参数仍抛出 `NullPointerException`；这些是原实现语义，本次未修复。未覆盖 `persist()`，保留 PAPI 默认行为。

回归使用真实 ODQ `PlayerQuests`、真实静态任务表与 Bukkit `OfflinePlayer` 接口代理，不使用替代 API。测试构造空的真实任务对象后填入非空集合，并通过反射设置原对象次数，以隔离服务器生命周期；不模拟任务生成或真实玩家登录。对旧、新 JAR 各运行 27 项断言，并比较全部 `javap -c -p -s` 方法指令与描述符，检查新 JAR 条目白名单。实际 class 字节仅版本头不同：旧产物 major 69（Java 25），新产物 major 65（Java 21）；其余 class 字节完全一致。

## 归档边界与后续验证

输入 snapshot 的所有文件均校验 SHA-256 与长度；输入未包含旧构建脚本，无法核对旧脚本。原源码检查未发现服务器连接地址、域名、世界名、部署路径或秘密；本模块不含这些信息。原始输入、API、日志与迁移证据保留在忽略的 `.work/`；证据为 `.work/migration-verification.json`。

本模块没有注册入口，不向主插件注入扩展，也不部署或替换现用扩展。以后由协调者安排同一扩展的替换，必须避免与现有扩展或主插件重复注册。尚未验证实服 PAPI 发现/重载、ODQ 登录加载时序及菜单中的最终占位符展示。
