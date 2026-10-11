> **2026-10-12 当前主工程：`D:\桌面\ronova_GPT`。** Java 49 / Native 46 / Journal 6。当前完整主包已构建，实际 premain、Bootstrap、Windows Native 控制写门及密封类克隆检查通过；Forge 仍未进入世界，最新完成运行达到原 1200 秒期限，B 未完成。详见 [当前状态](docs/A-批验收状态.md) 和 [实际报告](validation/b-final-20261007/REPORT.md)。 候选：[当前 B WIP 主包](distribution/candidate-b-wip-20261012/README.md)。

以下 R1/R2、候选 43、旧主包与旧环境结果保留其原日期和证据范围。已有发行目录不是本轮源码对应的新游戏安装包。

> **2026-10-11 当前接续源码：JDK Control Plane R1。** 接续 fast-exec R2，已完成早期生产/任务根登记、按定义惰性解析和容器轻量写入路径。Java 17 的 Bootstrap/Agent 共 61 个文件编译通过；未取得 Forge/Windows/游戏组合运行结果，无对应新游戏 JAR，B 尚未完成。详见 [当前状态](docs/A-批验收状态.md)。

> **当前：2026-10-09 B WIP 候选 43。** Java ABI 48、Native ABI 44、Journal 6。源码与候选 JAR 已集中构建通过；最新场景未进入世界，上一候选启动超时，B 尚未完成。当前结论以 [状态页](docs/A-批验收状态.md) 和 [B 报告](validation/b-final-20261007/REPORT.md) 为准。下方旧发行、旧目录、六种返回模式及历史结果按其原日期理解，不替代本候选。

2026-10-11 接续：当前包为 fast-exec R2 修订源码，不含对应新 JAR，也未提升 Forge／Windows 实机已验收基线。改动与限制见 `FAST-EXEC-TRIM-20261010.md` 的 R2 条目及 `docs/A-批验收状态.md`。


当前主包 `ronova-pro-core-0.1.0-stage-b-dev.jar` 已包含 Agent、Bootstrap、Windows x64 Native 载荷和前置 Main。普通安装只将这个主包放进 Forge 1.20.1 实例的 `mods`；该路径自动尝试附加，无需手填 JVM 参数。启动前生效仍需启动器调用前置入口，不能把单放 mods 宣称为启动前控制。当前六种返回配置归一为固定类型默认合同。

以下为原交接历史说明。

> **2026-10-07 R7 源码候选**：在 R6 上补构造期外部对象写守卫与取消后完成栈异常接续。定点结果及保留缺口见 [R7 报告](validation/r7-20261007/REPORT.md)。没有新的游戏安装 JAR，未覆盖原主工程。

# Ronova Pro · GPT 工作工程

> **2026-10-05 R4 源码补修**：本包以用户提供的 repaired-r3 为直接基线，只补 WorldArchive 与 RecoveryTasks 中新确认的计划 tick 分支、处置提交竞争窗口和普通 ForkJoin 关闭退役接线。R1/R2/R3 保留；本轮检查及限制见 [当前状态](docs/A-批验收状态.md) 的 R4 首节。下方 R3 与更早结果均为历史记录，不替代 R4 结果。


> **2026-10-05 R3 补修**：策略围栏已接入既有 admission 释放/关闭流程；在途来源交付结束前不安装准确对象围栏，已完整发布但未合并的普通事件不额外等待空队列。完整 RecoveryTasks + 实际 Bootstrap/文件日志的独立定点回归通过；世界/来源依赖为替身，未跑 Forge/Java17/Windows 实机。删除 FixtureCommands 的生产特权控制台分支、Core/Native 人工故障注入和可选 dump/调试输出；旧依赖这些测试分支的夹具不能直接沿用历史成绩。R3 包含下方 R1/R2；下方记录均按原范围保留。详情见 `R3-补修说明.md`。


> **2026-10-05 R2 补修**：已补截图三项：世界受理保存并消费原 append ACK，失败/取消不发布世界动作；生成失败重试接续同一未退役生成器及其队列；policyGate 通过原 withBoundaries 授权作用域安装真实围栏，不改旧任务资格或删 Bootstrap 权限。额外只改 WorldArchive/SeedChunks/RecoveryTasks 三个生产文件。Linux/JDK21 的定点世界控制流与真实 Bootstrap 授权检查通过；未进行完整 Forge/Java17/Windows 实机验证，仍为源码候选。R2 包含下方 R1 全部修改；下方“本轮”均为其历史记录，不混为 R2 结果。


> **2026-10-05 修订交付说明**：本包为 c565e97 的修复源码，不包含新的游戏安装 JAR。输入包已有的 `build/libs`／`distribution` JAR 是旧候选，已从本次源码 ZIP 移出；原始源码包仍保留它们。增量补丁不更新旧二进制。修复与检查范围见 [当前状态](docs/A-批验收状态.md) 的首节。完整 Forge 构建本轮因 Gradle 下载 DNS 失败而未完成，不能沿用下文历史 PASS 为本轮背书。没有公开推送或替换仓库候选。


GitHub 同步仓库：[Scorn522/Ronova_pro](https://github.com/Scorn522/Ronova_pro)，默认分支 `main`。后续本工程的修复、文档和对应候选产物同步到此仓库。

公开仓库包含当前源码、设计、状态、本次候选与修复报告摘要。历史游戏运行目录、存档、第三方研究证据和旧分发包保留在本地接续工程。

工作目录：`D:\桌面\ronova_GPT`。原 DeepSeek 对照工程未随本接续包迁入。

从 [文档入口](docs/README.md) 开始阅读；实现与验收进度统一看 [当前 A 批状态](docs/A-批验收状态.md)。

遵循 [工程执行规则](AGENTS.md)：**先写完整个工作包的代码，再统一编译和验证；不准做与实际攻防强度无关的任何验证。**

| 位置 | 内容 |
| --- | --- |
| `src/core/` | 主程序源码，Mixin 放在其中的 `mixin/` |
| `src/agent/`、`src/bootstrap/` | Agent 与 Bootstrap 源码 |
| `src/prelaunch/`、`src/journal/` | 前置启动器与共用持久日志源码 |
| `src/resources/` | Mod 元数据、Mixin 配置与资源 |
| `native/` | Native 源码与构建入口 |
| [docs/](docs/README.md) | 当前状态、R3 设计、研究资料与历史记录 |
| `validation/` | 独立检查、隔离夹具及已有运行记录 |
| `validation/history/takeover-20260921/` | 一次性接手脚本与原始基线 |

Java 源码按组件直接存放，目录不再重复 `src/main/java/dev/ronova/pro/...`。源码内的包名保持原样，Gradle 已显式配置各组件的源码位置；编译后的类路径、Mixin 类名和 JAR 入口保持原有结构。

现有验证源码分为 `validation/fixture/`、`validation/checks/`、`validation/agent-check/`、`validation/agent-policy/`，夹具资源位于 `validation/resources/`。历史报告中的旧源码路径按当时记录保留，可按上述组件目录查找同名文件。

完整工作包写齐后，有直接攻防执行需求时使用的编译命令（不是日常固定检查）：

```powershell
.\gradlew.bat compileJava compileBootstrapJava compileAgentJava compileFixtureJava
```

`validation/check_controls.py` 用于独立边界运行检查，要求 Windows、`JAVA_HOME` 指向 JDK 17。
Native 构建需要 Zig，可用 Gradle `-PcontrolZig=编译器路径` 指定。不例行构建发布 JAR。

`distribution/package-stage-b.py` 是早期 Stage B 的打包脚本，依赖当时候选与证据；不能用来宣称当前源码已完成验收。

最新候选：distribution/candidate-b-source-20261004/（ABI 48）；B 的已知剩余来源适配代码已补齐，游戏内清理与保存重启效果仍待验证，详见当前状态。

## 整 Mod A 候选的使用

普通 `mods` 安装仍会自动尝试进程内附加。需要在游戏 JVM 启动前生效时，让启动器以 `ronova-pro-prelaunch.jar` 作为游戏 Java 命令的前置入口，并传入它原本要执行的全部游戏 JVM 参数与主类参数；玩家不用手填 `-javaagent`。示例：

```text
java -jar ronova-pro-prelaunch.jar --java <启动器使用的 java.exe> --core <mods 内的 ronova-pro-core.jar> <原有全部游戏参数>
```

在游戏实例根目录放置 `ronova-prelaunch.properties`，例如：

```properties
stop=target_mod_a,target_mod_b
returns=default
protect=ally_mod
allow_remote_stop=target_mod_a,target_mod_b
```

`stop` 在目标 Mod 类加载前建立整组方法、构造／静态入口及事件控制；`protect` 在当前和后续实体、方块实体上建立防护。`allow_remote_stop` 是多人游戏客户端允许当前服务器请求停用的本地 Mod 列表，不会在启动时直接停用它们。运行中可用 `/ronova_pro mod stop modid1,modid2 [returns=策略]`、`/ronova_pro mod protect modid` 和 `/ronova_pro mod status [modid1,modid2]`。实际覆盖和未决路径见 [A 批状态](docs/A-批验收状态.md)。单纯把 JAR 放进 `mods` 不能在游戏 JVM 创建前运行前置入口。

选择同一实际 Module 内的一个 Mod 会纳入该模块的其他 `[[mods]]` 所有者，结果会列出完整作用组。多人客户端必须在本地 `allow_remote_stop` 中允许该组的每个 modid。客户端与服务端需使用本轮同版本主包，基础同步协议为 `r3-client-5`。

`returns` 支持以下策略；启动配置与运行命令使用相同名称。所有策略都跳过目标普通业务方法，void 直接结束，基本类型默认返回零值。

| 策略 | 引用与专用返回 |
| --- | --- |
| `default` | 普通对象和数组为 null；标准集合、Optional、迭代器、流提供合法空结果，标准 Future 提供已完成空结果 |
| `null` | 引用和数组统一为 null；不会启动原来的异步生产 |
| `empty` | 在 default 基础上，数组返回类型正确的零长度数组 |
| `uuid-fixed` | UUID／String 返回同一模块的固定随机 UUID 值；其他返回沿用 default |
| `uuid-each` | UUID／String 每次调用产生新的 UUID 值；其他返回沿用 default |
| `invalid-id` | 所有 int 返回 -1；其他返回沿用 default |

UUID 策略只控制查询返回，真实实体标识和登记不会被它直接重写。清除使用已有真实引用。普通业务构造会抛出 `RONOVA_MOD_GROUP_CREATION_REFUSED`，避免返回半对象；Forge 为完成装载所需的 `@Mod` 承载实例只保留必要父类初始化，业务后缀停用。无法控制的承载前缀、隐藏类或 Native 路径继续报告未控，不能把单组部分结果读成全部停用。
