# Ronova Pro B-WIP Fast Execution Trim — 2026-10-10

基线：`Ronova-Pro-B-WIP-ABI48-20261009.zip`。

本轮目标只有一个：删掉已经开始长成“证明/认证系统”的重复运行时层，保留真实动作所需的当前事实判断和真正的持久化意图/释放事实。没有新增 Gate、proof、receipt 或游戏探针。

## 改动

- `ProRuntime`
  - 删除 BODY CLEAR 的 20-tick 成功认证窗口。
  - 删除 O01 成功 `RECEIPT` 的生成、重试和释放前依赖。
  - `query()` 改为只读，不再由查询触发 invalidation。
  - 当前世界/记录/客户端/intent/fact/participant 的必要现场条件满足后立即进入完成状态。
- `RecoveryChain`
  - 从“第五层证明器”降为实时聚合器。
  - 删除 fingerprint、20-tick stable window、CHAIN RECEIPT、INVALIDATED 写入。
  - status 只读。
  - `operations()` 改用现有 `bySubject` 索引，不再对 `work.values()` 全扫。
  - 旧 CHAIN RECEIPT/INVALIDATED 仍仅作为历史兼容格式接受，不再产生新记录。
- `RecoveryTasks`
  - 删除 LOCAL_FACT -> FACT 两层成功回执与 ACK 重试。
  - 删除 20-tick `observeStable()` 窗口、相关 window/revision proof 字段和等待状态。
  - task 当前真实执行、queue/waiter/helper、qualification、source/shared、intent/release ACK 满足后直接进入 live quiescent scope。
  - Core 强引用释放不再先写 `CORE_RELEASE_INTENT` 等待 ACK；仍保留最终 `CORE_RELEASED` durable fact。
  - 把若干一次性查询不可用从永久 `gap` 改成当轮 transient issue；真正 capture loss / conflicting origin 仍保持永久 fail-closed。
- 生产代码中的 `dev.ronova.pro.validation.*` 特殊待遇删除：`RecoveryReferences`、`ControlImages`、`RecoveryAgent` 不再为 validation 包开生产例外。

## 明确保留

以下没有因“精简验证”被删除：

- source / Module / generation / qualification；
- shared vs exclusive；
- pending publication/delivery；
- active invocation / execution / queue / waiter / helper reference；
- 当前对象/字段/资源 identity；
- Bootstrap authority / boundary；
- 实际 intent、release、resource、CORE_RELEASED 等需要跨重启使用的 durable records；
- 真正 capture loss、来源冲突等不可恢复 gap 的 fail-closed。

## 验证边界

当前环境没有该工程完整 Forge 依赖与 Unix Gradle wrapper，因此没有完成 Forge/Minecraft 实机构建。做了源码静态检查：

- 新代码中不再生成 CHAIN RECEIPT / CHAIN INVALIDATED；
- 不再生成 LOCAL_FACT / FACT 成功认证记录；
- 不再有 BODY CLEAR receipt/stable window；
- 生产 agent/core 中不再出现 validation 包特殊分支；
- `javac` 对修改文件进行了语法解析；输出仅为缺失 Forge/Minecraft/工程依赖的符号错误，没有发现 Java 语法错误。

这份包是“快速执行重构源码”，不是 Forge/Windows/Java17 实机通过声明。

## 2026-10-11：fast-exec R2 源码接续（未提升已验收基线）

- 查询只投影 ACK 状态，不再修改工作义务或 coverageGap。保留旧 Status.receiptDurable 接口，但新工作不生成成功回执，因而返回 false；现有 WorldFixture／ChainFixture 不再等待该已撤销的回执。WorldFixture 删除强制等待 20 tick 的成功认证断言，仍检查真实清除、客户端确认、邻居和再入场阻断。
- 实体／方块 Core 强引用释放删除 ROOT_RESOURCE INTENT 及其前置 ACK 等待。真实最终绑定／剩余记录条件满足后释放，后续 tick 追加 ROOT_RESOURCE RELEASED；缺失释放事实的 ACK 仍保持待结清。
- 已完成 Core 释放的任务不再重新比对全局 boundary epoch。实际任务动作仍使用当前所需边界；迟到活动继续成为新工作。
- 补回 fast-exec 删改后的现场条件：结算和最终释放必须确认 active invocation 与 helper 引用均为零；Bootstrap 所有权／丢事件查询当轮不可用时不执行相关结算或资源动作，重试而不制造新永久 gap。
- 存储仍执行现有独立文件身份／字节读回。相同 expected image 的解压与语义哈希改为每项工作复用，首次读回及进程重启首次查询仍重新解码。没有拉长周期或用历史 READBACK 代替当前文件读取。

当前为修订源码，未生成对应 JAR，未完整 Forge 编译，未取得 Windows Native／Minecraft 进入世界或来源、任务、保存重启组合通过。旧版 TaskStateCheck 依赖已删除的稳定窗口字段，仅属历史检查，不能用于认证 fast-exec。本次只核对实际修改的代码与既有夹具接口，不运行旧全量矩阵。

本轮实际检查：Java 17 对 3 个修改后的生产文件及 2 个既有夹具文件进行了纯语法解析，5 个文件、0 个语法错误。没有进行依赖解析、类型检查、完整 Forge 构建或实机运行，不能视为编译／攻防通过。GitHub 未同步。

远端接续限制：已读取 Scorn522/Ronova_pro 的 main；ProRuntime.java、RecoveryTasks.java、RecoveryStorage.java 均不同于本轮上传 fast-exec 输入。此修订只基于上传包，未覆盖不同的远端主线，也未把候选源码提升为远端当前基线。远端同步须先对当前主线合流。

## 2026-10-11：JDK Control Plane R1（fast-exec R2 接续源码）

本工作包接续 `Ronova-Pro-B-fast-exec-r2-20261011-source.zip`，采用现有 Java 17 premain、Bootstrap、JVMTI/Native 后端；没有自定义 JDK 或 patched HotSpot。Java ABI 48、Native ABI 44、Journal 6 不变。

- 已织入的真实生产方法入口，在 Core 输入观察者安装前也记录当前声明类、Module、方法和父调用。输入集合延迟分配；当前指令贡献者仍实时读取，不冻结为入口来源，也不据入口记录授予处置权限。
- JDK 任务创建与提交关系在 Core 接入前也登记；执行帧携带真实对象已有来源及未知标记，退出/退役释放引用。Core 自身事件交付不借用该任务的回退来源。
- 普通 HashMap/LinkedHashMap/ArrayList/ConcurrentHashMap 仅保留弱身份出生记录和真实在途计数，不立即创建 Source Scope/Gate。普通 JDK 写入帧按线程复用，实际 JDK 调用方负责归还；业务控制/公开载体令牌保持独立。晚登记先等待旧写入退出，再绑定当前真实节点；未观察的出生不补造为已观察。
- 方法索引按实际 Class 惰性解析，保持加载器/隐藏类身份匹配。同名定义登记只使该名称的索引失效；实际 BCI、指令来源和定义代次仍由 Native 当前查询取得。没有全局边界 epoch 重验或动作前置证明。
- 没有新增成功回执、稳定窗口、审批/许可证、探针或验收矩阵。本包保留 R2 的实际共享状态、活动调用、helper 引用、资源使用与必要 durable release 条件。

实际检查：整包实现完成后，使用本机 OpenJDK 17.0.20 集中编译全部 Bootstrap 14 个和 Agent 47 个 Java 文件，两个编译任务退出码均为 0；Agent 仅有既有弃用 API 提示。检查了修改路径的晚登记写入计数、帧异常退出及来源缓存代次，未运行历史 TaskStateCheck 或新增测试。

范围限制：未完整 Forge 编译，未构建对应游戏安装 JAR，未运行 Windows Native/Minecraft 世界或来源、任务、保存重启组合。本包不提升已验收基线，B 未完成；Native/Host 全面扩展仍属 C。

源码补丁 `CONTROL-PLANE-R1.patch` 以 fast-exec R2 为基线，不能直接覆盖不同的远端 main。已读取远端 main（c565e975d78a3b32ba72f493c72c953d24698203），准备以独立候选接续；GitHub create-tree 接口返回 403 Resource not accessible by integration，当前连接无法写入。没有创建候选分支、提交或 PR，也没有修改远端 main。本地完整源码与补丁均已保留。

