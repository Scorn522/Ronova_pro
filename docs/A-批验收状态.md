# Ronova Pro 当前进度

## 2026-10-13：原生层统一 GNU C23 / GNU C++23

本轮按用户要求统一原生语言版本与 GNU 模式。控制 DLL 明确使用 `-std=gnu23`；存储 DLL 从 C++17 改为 `-std=gnu++23`。保留用户现有 `.vscode/c_cpp_properties.json` 的 `gnu23 / gnu++23` 选择，并随源码同步。Java 仍为 17；Java ABI 49、Native ABI 47、Storage ABI 3、Journal 6 均未改变。

C++ 存储哈希的打开、更新、完成与关闭改用 `std::expected` 返回成功或实际错误；调用方直接接续这一次操作的错误，不再读共享的 Hash.error。文件定位失败直接携带该操作的 Win32 错误。哈希输入使用 `std::span`，固定摘要、传输缓冲区与两个事务槽使用 `std::array`。原事务状态、实际文件身份、准备过程中的独立读回、提交许可与失败关闭后的原句柄保留流程继续使用原合同；JNI/JVMTI 调用者判断未重写。

完整工作包写齐后的最少结果：

| 本轮检查 | 实际结果 |
| --- | --- |
| GNU C23 控制 DLL、GNU C++23 存储 DLL、嵌入载荷主包及既有夹具 | PASS；`build-native-2023-20261013.log`，完整构建 30 秒 |
| 实际 Storage DLL 与隔离临时文件事务 | PASS；`storage-native-2023-20261013.log`：外来请求拒绝、错误候选摘要拒绝、65537 字节候选多块哈希、实际事务准备与读回、无许可回滚、33001 字节原文件保留、句柄退役 |
| 实际 premain / Bootstrap / GNU C23 Native 控制入口 | PASS；`live-control-writer-native-2023-20261013.log`：真实安装、外来写者/原生读者拒绝、保护数组保留、实际原生到堆复制 |

两项定点检查退出码均为 0，复用既有 AuditFixCheck；没有运行旧元数据矩阵，也没有再次启动游戏世界。本轮存储检查使用隔离文件和夹具构造的 Ticket，不代表生产来源登记、获许可提交、Minecraft 世界或保存重启已经完成。旧候选的 WORLD_LOADED 不作为此主包的新运行成绩，B 仍未完成。构建中原有 `_WIN32_WINNT` 重定义提示和实际控制入口的 74 项文件绑定未决保留，未提升平台覆盖范围。

新主包保存在本地 `distribution/candidate-b-native2023-wip-20261013/ronova-pro-core-0.1.0-stage-b-dev.jar`。本轮同步源码、编辑器配置与简要结果；候选和原始日志继续留在本地。

---

## 2026-10-12：Native 47 实施与实际入口检查（Java 49 / Journal 6）

用户在讨论 Native 46/47 的区别并选择继续 B 后，已授权接续 Native 47。前一节的自动审批拒绝属于此前未获这项具体授权的时点；本轮应用和检查已经获准执行。主工程仍为 `D:\桌面\ronova_GPT`，接续已同步的 `bd0c3b5`。**B 仍未完成：当前还没有本候选的世界、来源/记录/任务/资源组合、实际存储读回及同世界保存重启结果。**

本完整工作包已实施：

- 原生内存权限查询接入 JVMTI 实际当前调用栈。ControlBridge 继续使用原读写者资格条件；Native 的令牌、真实线程、嵌套作用域和来源检查保留。
- 帧选择使用真实声明 Class/方法分类，控制写者和原生内存写者分别缓存各自的不可变分类事实；缓存不授予调用者资格。隐藏类、反射和 MethodHandle 路径接续原 Java 栈选择。
- Native ABI 47 拒绝混用旧控制 DLL。Java ABI 49、Journal 6 与 Storage Native 合同未改。
- 复用 AuditFixCheck 的实际写者检查，增加真实私有 JNI 读入口的外来调用者拒绝，以及实际原生地址到堆数组的复制与释放；克隆检查因共享 Native 查询/绑定路径改变而定点复查。

代码写齐后的实际结果：

| 当前检查 | 结果与范围 |
| --- | --- |
| 完整 Bootstrap、控制 DLL、Core 嵌入载荷和现有夹具重混淆主包 | PASS；`build-b-raw-memory-caller-fixed-20261012.log` 为 `BUILD SUCCESSFUL in 18s` |
| 实际 premain / Bootstrap / Native 47 安装 | PASS；真实输出 `NATIVE_BIND_AND_JNI_FIELD_READY` |
| 外来写者、真实保护数组、外来原生读者、实际 Unsafe 原生到堆复制 | PASS；`live-control-writer-native47-fixed-20261012.log`，退出码 0 |
| 密封枚举许可子类、数组 values 克隆、继承原生 clone、Cloneable 异常和业务覆写 | PASS；`clone-boundary-native47-20261012.log`，退出码 0 |
| 本轮数组分区查询与宿主加载修复的完整主包 | PASS；`build-b-array-region-nest-20261012.log` 为 `BUILD SUCCESSFUL in 14s` |
| 本轮实际权限拒绝与原生到堆复制 | PASS；`live-control-writer-native47-array-nest-20261012.log`，退出码 0；复用既有检查，未重跑旧矩阵 |
| 世界与 SourceModuleFixture | b-source-10 在世界加载前因 LivingChangeTargetEvent 重复定义退出 1；b-source-11 被旧运行器的 1200 秒截止终止，尚无世界与来源处置结果；b-startup-12 以 2GB 堆到达 Preparing level/world 和出生点准备，但实际发生 Java heap space，保存本地诊断后停止；b-startup-13 使用 6GB 堆于 11:23:41 出现 Done (599.615s) 和 WORLD_LOADED；随后 Core 初始化的嵌套类读取触发 Cleaner 原始释放失败，未正常保存退出；完整生命周期修复包已经构建，通过实际释放拒绝/原 Cleaner/文件读取检查，目前正在同一隔离世界重启 |

b-source-10 于 09:02:31 启动，09:11:27 记录 Forge Initialized，随后在进入世界前因同一 TransformingClassLoader 重复定义 `net.minecraftforge.event.entity.living.LivingChangeTargetEvent` 退出 1。没有世界结果，不计通过。657 秒实际加载工作线程停在 `ExecutionFlow.arraySlot → byteMemoryBefore → Unsafe.putReferenceRelease → ConcurrentHashMap.resize → TransformerAuditTrail`。

接续修复将已经有序、互不重叠的数组来源分区改为二分定位，保留实际槽位、在途写入、未知来源及重叠合并。控制写者、ownState 和原生写者判断只对 Bootstrap 类查询 Bootstrap 宿主；外部类不可能属于这些实际 Bootstrap nest，避免权限查询触发外部宿主重新加载。b-source-11 没有取得后续世界结果，不能据此确定重复定义问题已解除。此前各运行保留原 1200 秒截止及真实失败，没有新增元数据探针。用户随后明确要求“只要能进去就行，先别管慢不慢”：暂停性能改动，接续同一完整主包服务端启动，允许等待 3600 秒，只装主包，不运行来源组合等业务夹具。b-startup-12 于 09:58:01 完成 Forge Initialized，10:04:08 进入 Preparing level "world"，10:07:23 开始出生点准备，随后出现真实 Java heap space。启动器堆约 10MB，而游戏 2GB 堆已耗尽；真实存活对象主要包含控制登记、字段锁及代码镜像。离线快照中这些弱引用对应的对象多数仍存活，因此尚不能称回收失效已确认或已修复。错误日志和堆诊断只保存在本地。本机约 10GB 可用内存，接续 b-startup-13 仅把运行器堆改到 6GB，源码未追加性能改动。只有实际出现世界加载成功才记作启动通过，完整 B 业务仍未完成。

生命周期故障的实际堆栈为 ProRuntime.declareDispatchTargets → 类/JAR 读取 → IOUtil 临时缓冲区原 Cleaner → Unsafe.freeMemory0，具体拒绝为 MANAGED_BUFFER_ORIGINAL_RELEASE_REQUIRED。此前 FILE_OBSERVING 嵌套分支跳过 Java 清理与视图登记，而 Native 仍登记真实 DirectByteBuffer 分配。修复保留清理动作、实际 Native 地址绑定、视图完成以及外层观察状态；嵌套分支只复用实际 I/O 来源，缺少来源时仍记未知，不授予释放权限。新工作包 build-b-nested-buffer-lifecycle-20261012.log 构建 24 秒通过；复用 AuditFixCheck 的真实外来释放拒绝、原 Cleaner 与临时文件读取路径，buffer-release-20261012.log 退出 0。没有重跑旧矩阵。

完整定点输出：

`LIVE_CONTROL_WRITER_PASS actual_native foreign_caller_refused protected_array_preserved foreign_raw_reader_refused actual_unsafe_raw_copy`

`CLONE_BOUNDARY_PASS actual_agent_native sealed_enum_permits values_clone inherited_owner distinct_identity payload non_cloneable_exception business_override`

本轮真实失败保留：应用脚本的替换范围过大，误删相邻 FieldGateWriter 的 host 声明，首次 Bootstrap 编译失败，恢复原声明后构建通过。私有 JNI 读入口实际按原合同抛 IllegalStateException 并携带 `ACTUAL_NATIVE_MEMORY_READER_REQUIRED`；初版检查误断言 SecurityException 而失败，补充具体原因后确认正确拒绝，再修正检查合同，通过真实复制检查。原构建失败、两份失败检查与最终日志均保留，不把初版失败计作通过。

Native 文件绑定日志仍保留 74/75 项未决；当前结果不代表所有平台入口已受控。Native 46 的已同步 WIP 主包继续作为回退候选保留，其旧结果不替代本轮 Native 47 的实际运行结果。

---

## 2026-10-12：B 实际启动故障修复（Java 49 / Native 46 / Journal 6）

主工程仍为 `D:\桌面\ronova_GPT`，接续已同步的 `9f79183`。当前已构建包含 premain Agent、Bootstrap、Windows JVMTI 控制 DLL、Storage DLL 和前置启动入口的重混淆游戏主包。**B 未完成：这一轮仍没有取得世界、来源组合、存储读回和同世界重启结果。** 下方 Native 45、旧候选 43 和旧环境限制均为各自日期的历史证据。

完整实现工作包已补充以下实际启动路径：

- 文件操作只在真实入口捕获一次当前来源，保留嵌套、实际线程和 LIFO 结束检查。未观察的文件对象不补造释放记录；空来源与未知来源继续区分，缓冲区操作复用本次真实文件或 IO 作用域。
- 控制写门每次查询实际活调用栈。Native 46 的 JVMTI 查询使用原 Java 帧选择规则，不以缓存授予控制身份；帧分类缓存匹配实际声明 Class 和方法，弱引用允许卸载，当前策略仍在 Java 端逐次检查。Agent 写者仅缓存实际 Class 的原 CodeSource 身份事实。准确保护列表的普通 add 保留真实写者检查和 JDK 后备数组发布检查，减少同一次调用的重复检查。
- CloneBoundary 先检查真实常量池，再流式改写准确的 Object.clone 调用；继承解析、Cloneable 异常和业务覆写仍使用原合同。JDK 17 密封类的 PermittedSubclasses 属性原样传递，修复 `Direction$Axis` 的 `ASM9_EXPERIMENTAL` 改写失败。
- acceptedExecution 保留实际指令、标签、异常处理器和描述符；这个分析图不再解析行号、局部变量调试表和存储的帧。输出定义和来源行号恢复仍使用原实际字节，两个分析器继续计算自己的帧。

工作包写齐后才集中构建。当前最少相关结果：

| 实际路径 | 当前结果与边界 |
| --- | --- |
| 完整 Core/Bootstrap/Agent、现有夹具、Windows 控制与存储载荷、前置入口和重混淆主包 | PASS；最新 `build-b-sealed-frame-cache-20261011.log` 为 `BUILD SUCCESSFUL in 26s` |
| 当前 premain + Bootstrap + Windows Native 46 安装 | PASS，实际输出 `NATIVE_BIND_AND_JNI_FIELD_READY`；不代表全部平台入口已受控 |
| 外部 system loader 调用者修改真实已安装保护数组 | PASS，实际 Native 路径拒绝该调用者，数组原身份与元素保留 |
| 密封枚举属性、values 数组克隆、继承 Object.clone、非 Cloneable 异常、业务 clone 覆写 | PASS，实际 Agent/Native；既有 AuditFixCheck 的 clone-boundary 模式直接复用并补充本次密封类故障 |
| Forge 启动与世界 | b-source-01 至 06 在取得真实线程栈后人工停止，不是通过，也不是 1200 秒自然超时；b-source-07 自然达到原 1200 秒期限，只有 Forge Initialized，没有 Done/world 结果 |
| 来源/记录/任务/迟到后继/资源组合、无 NBT 新 UUID、实际存储与同世界保存重启、作用组整体 | 仍待实际运行，未沿用旧候选成绩 |

当前检查继续复用 `AuditFixCheck`、WorldFixture 和原隔离运行器，没有运行旧 TaskState 元数据矩阵。源编译依赖为 Forge 1.20.1-47.4.22；实际可用隔离服务器为 Forge 1.20.1-47.4.23，Java 为 Temurin 17.0.15+6。运行器沿用原 1200 秒启动期限和 240 秒业务期限，实际运行目录为 `.work/b-completion-20261011/runtime`，不触碰原用户世界。

真实失败保留：`build-b-writer-nest-20261011.log` 的未定义 nest 编译错误已修复，随后构建成功；第一次独立 JVM 命令的 PowerShell 参数分词错误修正后才进入实际检查。前六轮现场依次定位文件/缓冲区重复来源捕获、控制写者 StackWalker、nest host 查询及 ASM 整树读取。第七轮的密封类失败和原期限超时保留在其真实 launch.log。本次两项实际定点输出是：

`LIVE_CONTROL_WRITER_PASS actual_native foreign_caller_refused protected_array_preserved`

`CLONE_BOUNDARY_PASS actual_agent_native sealed_enum_permits values_clone inherited_owner distinct_identity payload non_cloneable_exception business_override`

文件绑定日志仍有 `NATIVE_FILE_CURRENT_BINDINGS_PENDING:74` 或 75；这些未绑定入口保留未支持，不提升 C 批平台控制范围。B 的实际结果以本节和后续同轮现场更新为准。

### 本轮后续现场

b-source-08 使用密封类和帧分类缓存工作包运行，取得 210 秒处的实际主线程栈后人工停止，没有世界结果。现场仍为 `NativeControl.controlWriter0 → BackingBridge.listMutation → MethodNode.visitLocalVariable → CodeSourceBoundary.transform`；不是 1200 秒自然超时。

接续完整工作包已经把零来源普通定义接到现有按需解析路径：只有未观察到任何生产主体/贡献指令、当前实际声明也不是 producer、并且当前字节没有已登记外部图像时，先构建真实方法头；accepted 保留原始语义字节、实际常量池调用引用与正常定义登记。随后真实生产主体和定义变化仍经 prepared/changed 刷新调用者，并由已有 Definition.analysis() 解析原完整方法体。已归属的定义、已有贡献指令、字段和常量来源继续走原完整来源图，不使用空头替代它们。

工作包写齐后的 `build-b-lazy-zero-source-20261012.log` 为 `BUILD SUCCESSFUL in 20s`。本次变化是来源语义图的按需接续；未重复运行已经通过且未改变的 Native 写者与 clone 检查。b-source-09 使用此工作包对应主包运行，也达到原 1200 秒期限：00:17:43 记录 Forge Initialized，未出现 Done/world 或 source-module-result。683 秒处实际主线程栈为 `RawMemoryCaller → NativeControl.memoryReadSources0 → finishFieldMutation → Unsafe.copyMemory → FileChannelImpl/ZipFileSystem`；相关原始线程栈和 launch.log 在本地保留。B 的真实来源、记录、任务、资源、存储及保存重启仍未取得结果。

下一工作包拟将这个真实原生内存权限查询接到 JVMTI 活帧读取，保留实际读写令牌、线程、嵌套作用域及原资格检查，并兼容隐藏/反射帧；拟升级 Native ABI 至 47，补充既有 AuditFixCheck 的实际外来读者拒绝与原生到堆内存复制检查。**自动审批拒绝了应用和运行该工作包，理由为原生授权/调用者选择修改及 Unsafe 检查可能导致未授权进程内存操作，当前一般 B 请求缺少该具体高影响动作的明确授权。该脚本未执行，Native 47 没有写入生产源码、没有构建、没有运行、没有发布；当前源码和候选仍为 Native 46。** 等待用户对这项具体改动的明确授权；已获授权的源码、证据与 WIP 交付继续同步。

当前可接续主包保存在 `distribution/candidate-b-wip-20261012`，明确标为 WIP；与本轮源码一并同步，未提升 B 验收结论。

---

## 2026-10-11：最终工程接入与来源处置补修（Java 49 / Native 45 / Journal 6）

当前主工程为 `D:\桌面\ronova_GPT`。本轮在保留 `Scorn522/Ronova_pro` 原提交历史的基础上接入用户提供的 B 收口源码，并修复源码审查中的 F01–F03。导入前的冲突文件已备份；旧生产源码树保留在本地，但不再属于当前 Gradle 源码集。下方原候选、原环境及原失败记录保留为历史，不代替本节结果。

- F01：RecoveryRecords 两处字段消费回调统一调用实际定义的 `observedField`，恢复完整 Core 类型编译。
- F02：SourceMaps 的四个来源缓存方法改为从实际所有者 ControlBridge 解析，恢复 holder/carrier 登记、发布锁与恢复变更通知接线。
- F03：列表处置在调用方已有的真实边界、来源及列表写门内，按对象身份移除全部重复引用。NBT 投影同步排除全部相同身份；值相同但身份不同的邻居保留。退役列表的既有移动保护仍使用同一写入作用域。
- 复用现有 `AuditFixCheck` 的 `source-list-fixes` 模式，只检查这些实际失效；未运行旧元数据检查。现有 ChainFixture 增加 ArrayList 与 CopyOnWriteArrayList 重复引用及合法邻居的组合场景，已通过夹具编译，尚未进入游戏运行。

工作包写齐后使用 Windows、Temurin Java 17.0.15+6、缓存的 Forge 1.20.1-47.4.22 和 Zig 0.13.0 集中检查：

| 实际检查 | 本轮结果与范围 |
| --- | --- |
| 完整 Forge/Mixin Core、Bootstrap、Agent、现有夹具与 Windows 控制 DLL 编译 | PASS，Gradle `BUILD SUCCESSFUL in 54s`；弃用提示保留 |
| 新 Bootstrap 载荷构建与实际四个来源方法缓存初始化 | PASS，四个缓存方法的声明类均为实际 ControlBridge |
| ArrayList、CopyOnWriteArrayList、fastutil ObjectArrayList 的重复准确身份移除 | PASS；全部重复目标移除，等值邻居保留原身份，第二次删除返回未变更 |
| 实际 RecoveryRecords NBT 投影与删除后列表一致 | PASS；使用真实 CompoundTag 与当前生产辅助方法 |
| Minecraft 世界/客户端、来源/任务/资源组合、Storage/TxF、真实保存与同世界重启 | NOT_RUN，B 实际运行验收仍未完成 |

本轮使用的最少任务为 `clean compileJava compileBootstrapJava compileAgentJava compileFixtureJava compileControlNative`，随后仅构建 `recoveryBootstrapJar` 并运行上述既有定点模式。没有构建新的重混淆游戏安装主包，没有加载 Windows 控制 DLL，也没有把独立 Java 检查解释成 Forge 世界验收。

本节对应日志均保留在本报告目录：
`build-final-source-fixes-20261011.log`、
`build-source-list-payload-20261011.log`、
`compile-source-list-fixes-20261011.log`、
`source-list-fixes-20261011.log`。
最终定点输出为 `SOURCE_LIST_FIXES_PASS bridge_owner array_list copy_on_write fastutil duplicate_identity preserved_equal_neighbor exact_nbt_projection`。

下方“当前环境没有 Forge/Windows 工具链”“Core 仅语法解析”“GitHub 403”等文字描述的是原附件的先前工作环境。当前本地工程已有本节编译及定点结果；远端同步结果按本轮实际提交说明记录。旧候选 JAR 保留原日期与范围，不能当成本轮源码产物。

## 2026-10-11：B 剩余代码收口（Java 49 / Native 45 / Journal 6）

当前源码接续 Control Plane R1。B 的剩余实现已写入；B 的实际运行验收仍未完成，下方旧候选和 R1/R2 结论保留为历史。

- 内存来源只保留 RecoveryRecords 这一份工作所有者。原 RecoveryReferences 的载体操作、真实消费关系、标量未决、holder 遍历、策略代次、后继发现和重启查询归入该实现；删除重复容器扫描、策略表、工作线程和状态组合。Map 中的 NBT 与实体使用同一份准确边工作，List/Set/数组/Atomic/直接字段继续使用各自真实写入合同。
- 已观察的普通内存边直接处置，保留准确身份、当前定义、实际共享份额、NBT 子树和在途保存条件。新列表投影的后置记录不再成为准确内存动作的前置 ACK。确认真实缺席后释放执行器强别名；原意图、后置记录及释放 ACK 的未决仍保留。回插由同一来源扫描接续，不把写过 FACT 当成当前缺席。
- 旧 RECORD/1 Map 与 RECORD/2–6 边历史按原 id/operation/source/code 进入同一重启查询，不复用旧指针，不猜文件归属；旧投影记录继续兼容。主体代次和实际消费变化使相关扫描重新接续，Map/数组/列表使用实际变更版本，未变化的已扫描 holder 不重复走第二套工作。
- ControlBridge 成为已有策略、创建/来源、字段/数组/Unsafe、控制对象、事件和 Instrumentation 登记的所有者。TaskBridge 保留任务、调度器、队列、等待者、执行与退出。Agent、Core 反射适配、Bootstrap 调用和 JNI 回调直接调用其真实所有者。Native ABI 45 拒绝旧控制 DLL；Java 两个桥均要求 ABI 49。Journal 仍为 6。
- 专属线程池/Timer 退役、共享池目标份额、Future/ForkJoin/CF 后继、真实 active invocation/helper/等待者/活动资源条件沿用既有路径。动作只序列化当前必要边界，不因无关全局边界 epoch 改变而重新认证。已声明保存源的句柄、候选、事务提交、独立读回、原意图重启和真实迁移仍沿用 RecoverySources/RecoveryStorage/StorageNative。
- 待登记组根按实际对象和主体去重，登记失败保留工作。客户端容量不足保留已有保护与撤销 tombstone；会话尚未建立的已获本地授权 Mod 策略按当前连接/实际 modid 等待，建立会话后接续，换连接清理。未归属的新对象和平台缺口不补造成已支持。
- Windows 文件工厂和 IOCP 专用类型在不存在的平台上保留未支持，避免其预加载阻止独立 Java 桥安装。Windows 实际对象入口仍匹配准确平台类，没有替代 Native/TxF 后端。

实际检查仅在工作包写齐后开始，后续复跑只针对检查发现的真实编译/安装故障：

| 检查 | 实际结果 |
| --- | --- |
| Java 17.0.20 全部 Bootstrap、Agent 类型编译 | 最终退出码均为 0；Agent 只有弃用 API 提示 |
| 启动引导与 Journal 类型编译 | 退出码 0 |
| 修改的 Core 文件语法解析 | 10 个文件，0 个语法错误；不是 Forge 类型编译 |
| 实际 premain → Bootstrap → JDK 入口安装 | 最终 JVM 退出码 0，Java 安装完成；最终日志没有安装失败或变换失败 |
| Windows 控制 DLL / TxF / Minecraft 世界与客户端 / 保存重启 | 未构建或运行，不能提升 O02/O05 或原 A/B 验收基线 |

检查中的真实失败保留：首次 Bootstrap 编译有 8 个旧 TaskBridge 方法引用错误，修正后通过；首次解析命令因缺少 ParseChanged.class 未启动，改用已有解析器源码执行后通过。premain 先遇到 WindowsChannelFactory、Iocp 强制预加载失败，修正平台入口后继续；随后 Lookup 派生的错误 owner 导致 JVM 中止，已恢复 DefinitionBridge 的实际入口；VarHandle 静态字段路径的 Unsafe/Handle 作用域仍指向旧 owner，修正 MemoryWriteScope 后安装完成。对应实际失败日志和最终日志保留在本报告目录。本包没有新增测试/探针、成功回执、稳定窗口或验收矩阵。

当前环境没有 Forge/Minecraft 依赖与 Windows 工具链；没有完整游戏安装 JAR，不声称实际世界、资源、保存重启或 Windows Native 通过。交付完整源码及相对 R1 的 B-SOURCE-COMPLETE.patch。原附件旧包只作对照，未覆盖为新基线。

远端写入仍受上一轮 GitHub 403 Resource not accessible by integration 限制；本轮未再次尝试等价写操作，没有修改远端 main。


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



## 2026-10-11：fast-exec R2 源码接续（未提升已验收基线）

- 查询只投影 ACK 状态，不再修改工作义务或 coverageGap。保留旧 Status.receiptDurable 接口，但新工作不生成成功回执，因而返回 false；现有 WorldFixture／ChainFixture 不再等待该已撤销的回执。WorldFixture 删除强制等待 20 tick 的成功认证断言，仍检查真实清除、客户端确认、邻居和再入场阻断。
- 实体／方块 Core 强引用释放删除 ROOT_RESOURCE INTENT 及其前置 ACK 等待。真实最终绑定／剩余记录条件满足后释放，后续 tick 追加 ROOT_RESOURCE RELEASED；缺失释放事实的 ACK 仍保持待结清。
- 已完成 Core 释放的任务不再重新比对全局 boundary epoch。实际任务动作仍使用当前所需边界；迟到活动继续成为新工作。
- 补回 fast-exec 删改后的现场条件：结算和最终释放必须确认 active invocation 与 helper 引用均为零；Bootstrap 所有权／丢事件查询当轮不可用时不执行相关结算或资源动作，重试而不制造新永久 gap。
- 存储仍执行现有独立文件身份／字节读回。相同 expected image 的解压与语义哈希改为每项工作复用，首次读回及进程重启首次查询仍重新解码。没有拉长周期或用历史 READBACK 代替当前文件读取。

当前为修订源码，未生成对应 JAR，未完整 Forge 编译，未取得 Windows Native／Minecraft 进入世界或来源、任务、保存重启组合通过。旧版 TaskStateCheck 依赖已删除的稳定窗口字段，仅属历史检查，不能用于认证 fast-exec。本次只核对实际修改的代码与既有夹具接口，不运行旧全量矩阵。


更新：2026-10-09。当前工作候选：`D:\桌面\MOD\.work\ronova-b-final-20261007\Ronova-Pro`。唯一当前状态页沿用本文件名。

**B 仍未完成；当前产物为实跑候选，没有新的 B 通过交付。** Java ABI 48、Native ABI 44、Journal 6；远端基线 c565e97。

## 当前已写

- 整组状态归一为 Group，成员变化才更新选集；RecoveryChain 只观察，当前主体/代次只推进一次。
- 实际保存工作反向索引；弱身份出生目录删除 AVL；无来源且未观察的字段/数组保留 UNKNOWN，省掉没有可用基线的记录分配。
- Task/Client/Network 控制成员归入已有弱身份登记，删除平行成员数组/位图和每次全量重扫；已开放实际容器字段直接读取，减少 Native 布局查询。
- 物品与普通绘制隔离合并；固定类型返回合同；ASM 工作图与日志历史按同输入/版本复用。
- 主包包含前置入口；安装失败不重复附加；同一安装快照启动并加载游戏。
- 指令重排与异常处理器变化补入来源传播和准确还原，重叠的后来编辑保持未决。
- 普通未观察字段/数组保留真实写门，只有当前执行或已登记载体查询历史来源；删除重复数组控制查询。Java Unsafe/句柄堆写入使用同一相关性规则，无来源回执时保留对象门与发布回调，省去线程目录登记。原始地址及显式 Native 来源继续沿原实际路径结算。
- ASM opcode 读取复用类型调用句柄。返回来源只运行实际方法/接收者/VM 身体解析；删除重复的“可能来源”预筛图、选择器索引、逐方法 valueCalls 和整批类来源中转。
- 控制弱条目回收随登记批次工作量推进；准确内部容器无已有业务来源记录/当前执行时保留写守卫，省去纯集合的业务来源作用域。尚无实际 producer/contributor 且当前图像无来源时，不运行空返回图分析；首次真实绑定的调用者刷新保留。
- 编译器普通/并发集合观察按显示隐藏帧的实际调用者识别，外国回调不继承外层 Agent 身份；已有 holder/carrier 的业务观察保留。控制台与 fastutil 合用 ControlClassWriter，去掉栈帧解析中的主动类加载回退；缺失声明仍未决。
- Mixin 字段/方法说明共用单成员投影：比较实际字段或方法说明/栈帧的相应历史来源行，不再每成员重复整树核对。方法保留真实类头，准确指令/异常处理器布局、完整类观察和不匹配时原贡献者未决保留。
- 编译器集合只在准确构造入口核对实际调用者，登记已有控制弱身份的内部集合角色；后续集合操作不再每次回走全栈。该身份仍受写入守卫保护，后续 holder/carrier 登记恢复业务观察。未登记原始映射的独立 Unsafe 读取不建立没有分配/读记录/结果消费者的空回执；已登记分配、实际执行读、缓冲区和复制窗口保留原路径。
- ASM 快照、复制和身份重建统一用准确指令数组；指令/处理器直接汇总后发布不可变列表，删除逐条 ArrayList 的重复控制查询。外部 InsnList 缓存数组不原位修改，布局、身份、排列和发布保护保持。
- 编译器 Map 的准确新节点直接登记为受保护内部节点，不再创建无业务用途的来源 Scope/Gate；后续真实 holder/carrier 登记仍恢复出生及节点绑定。Map 键/值同次发布只读取一次实际写者，两次实时元素策略继续执行。
- I/O 缓冲区借用复用该操作/请求已捕获的来源，归还按准确临时缓冲区匹配并统一走同一释放实现；删除只服务旧部分释放路径的计数。共享、封存、在途计数与未知使用仍保留。

接口—实现对应与剩余架构工作见 [架构审查](architecture/实际功能接口与架构收敛审查-20261008.md)。不新增许可、证明器或验证框架。

## 当前实际结果

最新完整构建通过；修复了无 Mod 元数据且仅声明 Object 参数的辅助反射/Unsafe 写者漏接截点，按实际写调用筛选，避免只读反射类被扩大织入。真实安装后的登记弱身份清空/入队和反射表写入被拒，无关数组写入正常；这不覆盖原始 Native/编译器未织入写者。实际生成字节码的重排还原、邻居编辑保留以及给定已观察来源的传播检查通过；这些不是 Forge/Native 整组结算证明。

来源候选 16 的世界加载发生在 Agent 安装失败后，不算有效通过。17–43 均没有 B 场景完成结果（36 仅构建未启动）；21/22/23/31/42 超过原 1200 秒启动时限。26/29 的驱动日志未记录最终退出且进程已不存在，不能结清。35 的重复类定义和 38 的 FieldNode 库角色失败保留。39–41 各取得准确启动现场后停止修复；42 在原时限超时，准确现场仍有编译器 Map 节点业务门和同次 Map 发布重复写者查询。该两项已修，最终完整构建通过（26 秒）。43 已完成 Agent 安装/Mixin 准备，未取得世界或来源结果；按用户“先打包一个包”结束本次准确隔离试跑并交付 WIP，不把它算正常停服或通过。真实私有回执、控制身份/表拒绝及邻居旧检查仅代表原准确入口范围。

**没有本轮来源终结、任务/资源退出、真实保存读回或重启组合 PASS。** 过程、日志、失败及原状态页全文保存在 [B 实际报告](../validation/b-final-20261007/REPORT.md)。旧报告里的“当前”仅指当时的候选。

## 已有 A 基线与后续边界

2026-10-01 的 A 候选在其声明范围内完成服务端和基础双端效果，证据见 [A 报告](../validation/a-final-20260928/REPORT.md)：实际实体/非 ticker 方块实体清除、移除与字段/Map 防护、启动前停用、事件/命令回调、合法邻居及基础客户端同步。旧六种返回风格属于旧候选，本轮已统一；旧 PASS 不自动覆盖本轮代码。

运行中不可重变换隐藏类和启动前承载前缀缺口仍按准确路径报告。完整外部来源/任务/资源/持久链归 B，完整 Native/Host 与广度控制归 C，不把它们算作旧 A 已通过。

## 仍需完成

1. 当前普通安装候选加载世界并完成真实清除、防护、已确认来源回填及邻居组合。
2. 同一候选的来源/任务/资源结算、真实写盘读回与重启。
3. 首次停服超时、旧 R8、两类来源归属未决；新原因和效果取得后才结清。
4. 上述历史代码合并与桥职责已由 2026-10-11 收口；本轮 Minecraft/客户端组合仍未运行。

设计入口为 [V1.5](design/Ronova-Pro-设计方案-V1.5.md)。没有运行原第三方样本、改原游戏/存档、使用子代理或推送远端。

本轮实际检查：Java 17 对 3 个修改后的生产文件及 2 个既有夹具文件进行了纯语法解析，5 个文件、0 个语法错误。没有进行依赖解析、类型检查、完整 Forge 构建或实机运行，不能视为编译／攻防通过。GitHub 未同步。

远端接续限制：已读取 Scorn522/Ronova_pro 的 main；ProRuntime.java、RecoveryTasks.java、RecoveryStorage.java 均不同于本轮上传 fast-exec 输入。此修订只基于上传包，未覆盖不同的远端主线，也未把候选源码提升为远端当前基线。远端同步须先对当前主线合流。
