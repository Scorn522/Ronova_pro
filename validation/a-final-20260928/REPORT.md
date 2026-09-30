# Ronova Pro V1.5 A 批收口报告

## 2026-10-01 换机接续修复

主工程：`C:\Users\Scorn\Desktop\MOD\Ronova-Pro-接续\ronova_GPT`。修复候选：`C:\Users\Scorn\Desktop\MOD\Ronova-Pro-接续\MOD\.work\pro-a-final-20260928\candidate`。以下结果均来自本次同一 Core 与修正后的现有夹具；本轮没有更改 Native 源码或原游戏/存档。

### 具体故障和处理

本次开始时，六项既有修复已经写在 candidate，尚未进入主工程。本次集中构建后运行原场景，先越过 40 tick，再在 age=80 的反射攻击处崩溃：Field.setBoolean 正确抛出 IllegalAccessException（RONOVA_PROTECTED_FIELD_WRITE_REFUSED），WorldFixture 将其包成 IllegalStateException，终止服务器循环。生产守卫当时挡住了写入，失败来自夹具的结果处理。

WorldFixture 现在只接受这个明确拒绝，随后直接读原始 remove 字段；普通 setRemoved 和 Unsafe 的 remove／level／worldPosition／blockEntities 写入同场景检查。未屏蔽其他反射异常，也未修改防护判断来让检查通过。现有运行脚本去掉旧机器绝对路径，改用 JAVA_HOME 和本目录 tools/forge-server/libraries（可通过 RONOVA_FORGE_LIBRARIES 指定）；同步脚本使用相对工程定位，并为本次新候选生成实际哈希。

修复源码、文档与对应 JAR 已同步主工程。最新安装主包为 `distribution/candidate-modwide-a-20261001/ronova-pro-core-0.1.0-stage-b-dev.jar`（860,281 字节）；前置入口为同目录 `ronova-pro-prelaunch.jar`（5,670 字节）。实际 SHA-256 见同目录 SHA256SUMS.txt。下面列出的六份集中构建与场景摘要已归档到本报告旁的 `evidence-20261001/`。

### 构建与实际效果

- `build-migration-baseline.log`：迁移后的六项既有修复集中构建成功，39 秒。
- `build-map-field-fix.log`：完整夹具修复后 jar／fixtureJar／prelaunchJar 集中构建成功，21 秒。Native 没有变化，复用旧候选中已有控制 DLL，使用 -x compileControlNative；Java 载荷重新构建。
- `protect-migration-baseline.log`：保留首次真实失败。世界启动后已达 60 tick；Map 防护检查推进，反射攻击因预期拒绝被夹具误判而崩溃。底层游戏进程虽然 exit 0，整个场景为 FAIL，不将退出码代替效果通过。
- `protect-map-field-fix.log`：BLOCK_HOLDER_MAP_FIELD_PROTECTED、CURRENT_AND_FUTURE_LIVING_AND_NONLIVING_PROTECTED、WORLD_COMMAND_RESPONDED、SERVER_EXIT=0。Map 删除／清空／条目替换和反射／Unsafe 攻击均检查准确原值；无关条目被 clear 删除。最终 current／future／object／futureObject=ACTIVE，removed=null，indexed=true，future registered=true、bound=true、conflict=false，callback=真实 PersistentEntitySectionManager$Callback，blockHolder=true。
- `dynamic-map-field-fix.log`：BODIES_CLEARED=2、BLOCKS_QUEUED=1、BODY_FAILURES=0；missing_mod 单组失败未挡住有效组。MARKER frozen=100 after=100，HIDDEN_LISTENER frozen=223 after=223；MOD_COMMAND_CALLBACK_STOPPED、TARGET_BODY_SELECTOR_ABSENT、WORLD_COMMAND_RESPONDED、SERVER_EXIT=0。7 个隐藏类不可重变换，FAILED=7／UNRESOLVED=7 保留原意，不能宣称全方法绝对停用。
- `boot-map-field-fix.log`：BOOT_TARGET_METHODS_DID_NOT_EXECUTE、WORLD_COMMAND_RESPONDED；目标状态 METHODS=16、UNRESOLVED=0、EVENT_REGISTRATION_AND_DISPATCH_INSTALLED、COMMAND_GATE=INSTALLED。SERVER_EXIT=0，服务端正常退出。

本次环境：Windows x64，Eclipse Adoptium Java 17.0.19+10，Gradle 8.8，编译映射 Forge 47.4.22，隔离实机 Forge 47.4.23／Minecraft 1.20.1。原始 launch.log、debug.log 和防护状态在 `C:\Users\Scorn\Desktop\MOD\Ronova-Pro-接续\MOD\.work\pro-a-final-20260928\runtime` 的对应场景目录；启动和运行现场线程栈随失败/成功记录保留。

原 protect10 的早期卡顿未在本次重现；原 30 秒的 40 tick 等待未调大。此次结论是当前完整防护场景通过及上述夹具故障已修，不证明旧机器早期停顿的具体原因。客户端、原第三方样本及完整 B/C/D 广度本轮未验。历史结果继续保留如下。

## 以下为 2026-09-28 及更早版本的历史报告


主工程：D:\桌面\ronova_GPT。隔离实现：D:\桌面\MOD\.work\pro-a-final-20260928\candidate。本轮未修改原游戏、存档或原第三方样本。只运行与当前清除、防护、回填、任务及普通安装入口直接相关的检查。

## 当前结论

**build25 在 A 已声明范围内 PASS。** 同一普通安装主包的双端正例完成，客户端和服务端 exit 0，当前身体已清除，已知来源链稳定处置，受保护玩家换身后继续覆盖，客户端撤销和邻居操作成立。完整 B 来源/任务资源结算、完整 C Native/Host 和原第三方样本实战不在这个 PASS 内。

本轮生产代码封住了公开策略修改的伪造命令入口，将真实控制台输入和准确队列记录绑定；字段、HashMap 节点及 fastutil 存储点与当前策略使用短门共同定序。任务结果发布、已确认来源和客户端已有路径保留。直接内存动作没有增加持久 ACK 前置条件。

## 当前候选及环境

| 产物 | 大小 | SHA-256 |
| --- | ---: | --- |
| ronova-pro-core-0.1.0-stage-b-dev.jar | 818,727 字节 | 6E69AF36A3E11829914866F0413EECDD1CC47C914B8D585A46734B59BC353ADB |
| ronova-pro-agent.jar | 145,655 字节 | CF3938DB4EAA34EBCD3FCA60E4B3AC3383DD42367F36929796DEBD24C70E8B20 |
| ronova-pro-bootstrap.jar | 161,148 字节 | 37164605FAB51CE68A186411C68772AFCD9962A58C39273B3957C940393BEA79 |

主包已嵌入 Agent/Bootstrap；独立文件只供核对，不需一起放入 mods。要求 Windows x64、Java 17、Minecraft 1.20.1、Forge 47.4.23 和可用的本机附加能力。普通安装免填 JVM Agent 参数。内部 Core/Agent/Bootstrap ABI 为 25；旧持久记录语义未改。

## 与攻防效果直接相关的结果

| 场景 | 证据与结论 |
| --- | --- |
| 编译与封装 | build25 的 build.log：BUILD SUCCESSFUL，22 项任务 |
| 精确防护和越权命令 | build24 GUARD_PROBE_PASS，服务端 exit 0。真实字段/索引拒写、撤销、邻居正常；直接调度与伪造队列的 release 未能撤销保护 |
| 真实控制台来源 | 无夹具、无手填 Agent 的独立服务端通过标准输入执行 summon + protect，日志有 RONOVA_OPERATION 与防护启用，exit 0；当时检查脚本先于日志落盘读到 False，事后原始日志和进程结果证明是检查时序误报 |
| 任务 A 最短链 | build24 TASK_A_PATH_PASS，服务端 exit 0；排队阻止、在途退出、周期/后继及共享任务路径有实际结果，完整 B 链明确显示 PENDING |
| 当前同包双端组合 | build25 的 dual-result.json：AUDIT_FINISHED，客户端/服务端 exit 0；PROTECTION_DURING_IDENTITY_CHANGE=ACTIVE、ACTUAL_END_RETURN_NEW_PLAYER=true、LATER_BODY_COMPLETE=true、LATER_CHAIN_SETTLED=true、CHAIN_WITH_UNRELATED_TASK_CHURN=true、CLIENT_POLICY_REVOKED=true |
| 来源与邻居 | build25 报告 CHAIN:KNOWN_RECOVERY_CHAIN_SETTLED，stableSince=904、lastSample=1074；迭代、视图 clear、邻居删除和 ticker 保留均为 true |

build24 的单项结果与 build25 的当前组合结果分开存档；build25 唯一后续代码变化是对象门目录的数据结构。当前候选的双端正例均来自 build25，不把旧单项结果冒充同一 JAR 的运行结果。

## 失败记录和修复解释

- build23 任务场景崩溃于 ConcurrentHashMap.computeIfAbsent 的 Recursive update。门目录触发来源观察并重入自身。build24 改用不受观察的弱引用桶后，任务 A 场景通过。
- build24 的双端复查出现约 40 秒长 tick、客户端连接超时。该次线程栈显示服务器线程在 FieldGateBucket.get 为大量临时 VoxelShape 扫描线性链。build25 改为身份哈希定位、弱引用回收；同一双端场景完成并正常退出。原失败日志与线程栈保留。
- 更早的 build08 停服 45 秒超时没有当时线程栈，具体原因仍不能证实。旧 R8 的 INLINE_RETIREMENT_EXIT_OR_CAPTURE_PENDING 仍为任务资源未决，不能因这次 exit 0 或 A 最短链通过而结清。
- 晚附加前的活动仍显示 AGENT_LATE_ATTACH_COVERAGE_GAP。未知来源及 OPAQUE 效果维持未决；未运行原第三方样本。

源改动清单为 source-delta-final.json 的 12 个文件。A 的当前结论与后续 B/C/D 边界统一维护在 docs/A-批验收状态.md。

## 2026-09-28 整 Mod A 扩展候选（上一候选记录）

本节是新候选的独立记录，不把上文 build25 的旧 A PASS 自动扩展为整 Mod PASS。当前 Core 为 837,576 字节，SHA-256 `4E135C5DC1C954213ACA6FE43C5B2B97EDFBDB4E4C44935D7AB295B1DDC87B54`；前置入口 JAR 为 5,583 字节，SHA-256 `15986F7C8249B73A1D247B4A5EDD4C8959D6ACF9E4A053925C18793B81081680`。`jar`、`fixtureJar`、`prelaunchJar` 在同一源码候选构建成功。原始隔离日志位于 `D:\桌面\MOD\.work\pro-a-final-20260928\runtime\`。

| 直接场景 | 实际结果 |
| --- | --- |
| 游戏内整组停用 | `mod-group-dynamic8`：生物与 Mod 自定义掉落物在停用前均被选择器找到，`/ronova_pro mod stop pro_fixture` 报 `BODIES_CLEARED=2`，之后两者均查不到；目标 tick、隐藏监听器停止，世界命令响应，服务端 exit 0。 |
| 事件委托独立对照 | `mod-group-dynamic5`：直接委托到 Java 标准库的隐藏监听器在停用前触发 111 次，停用后计数不再增长；这条调用不能靠改写夹具普通方法体解释。 |
| 游戏 JVM 前置入口 | `mod-group-boot4`：引导报告 `targets=pro_fixture`，目标普通方法与隐藏监听器从启动起未产生效果，世界命令响应，服务端 exit 0；状态仍列构造／静态初始化缺口。 |
| 整组防护 | `mod-group-protect7`：当前和后续进入世界的生物与 Mod 自定义掉落物都抵御了服务端 `/kill`；四个对象原始移除标记为空、UUID 索引准确且策略均为 `ACTIVE`，后续目标 callback 为真实 `PersistentEntitySectionManager$Callback`，服务端 exit 0。 |

失败记录没有覆盖：最初动态版本错误排除了夹具包；最初前置版本把 Forge 扫描器／事件变换器当作世界代码改写，使 Agent 状态降为部分安装；第一次组防护把受保护实体的 UUID **加入**误判为非法**删除**，后续实体无法加入世界；第二至第五次组防护虽挡住 `/kill`，初次 callback 写入仍被 backing 守卫拒绝。上述均定位并修改，相关效果已按表中后续场景复查。非事件隐藏类、构造／静态初始化、BlockEntity 保护及 B/C 广度仍不宣称全控；整 Mod A 继续未完成。

## 整 Mod A 本轮直接结果

本轮源码补齐了已加载非 ticker 方块实体的枚举、方块实体实际移除／替换防护、目标模块构造后缀和静态入口、基础实体继承入口、已注册 Brigadier 命令回调、多目标逐组继续与远端客户端策略约束。没有增加逐动作持久许可，也没有运行原第三方样本。

| 场景 | 实际结果 |
| --- | --- |
| `mod-group-boot6` | 修正构造入口后，启动前 `stop=pro_fixture` 正常进入世界；目标普通方法、隐藏事件监听器没有执行，普通命令响应，状态 `UNRESOLVED=0`、命令与事件门已安装，服务端 exit 0。 |
| `mod-group-dynamic9` | 当前生物、Mod 掉落物与非 ticker 方块实体被清除；tick 和隐藏事件监听器停止，普通命令响应，服务端 exit 0。 |
| `mod-group-dynamic11` | 逗号批量请求中的 `missing_mod` 报单组错误，`pro_fixture` 仍清除两个实体和一个方块实体；已注册命令回调、目标 tick、隐藏事件监听器停止，邻居命令响应，服务端 exit 0。 |
| `mod-group-protect9` | 当前和后续生命、非生命实体经 `/kill` 后仍在；受保护方块经 `setblock air` 后仍有原方块实体。该次使用最终方块快照实现，服务端 exit 0。 |

`boot5` 的失败是静态初始化被停用后，夹具构造函数仍访问已清空静态字段；修正构造来源判定后 `boot6` 通过。`dynamic10` 的失败是 Brigadier `word` 参数拒绝逗号，不是清除失败；改为完整字符串后 `dynamic11` 通过。`dynamic12` 使用新的方块清除实现，目标实体、方块、事件及命令回调的直接效果仍通过；隔离脚本在停服保存约 30 秒时终止进程，记录为效果通过、退出未确认，不能写 exit 0。运行中停用报告仍有不可重变换的隐藏 lambda。已登记事件与命令回调的实际调用点受控，但其他使用点未证明全控；整组客户端实际联机场景未在本轮候选运行。当前结果不能扩写成整 Mod A 全面 PASS。

最后补上多人客户端本地 `allow_remote_stop` 选集与已安装 Mod 筛选。`mod-group-client-1` 使用同一交付 Core 与前置 JAR：客户端在本地允许 `pro_fixture` 后加入隔离服务器，服务器发起整组停用，客户端日志记录 `RONOVA_CLIENT_MOD_GROUP TARGETS=[pro_fixture]`，客户端随后仍运行，服务器 exit 0。客户端 Agent 同时报告 6 个不能重变换的隐藏类，因此这是基础同步和实际委托入口通过，不是“所有客户端方法绝对停用”。交付候选 Core 为 854,966 字节，SHA-256 `B956F4572FC71F1E5A7ABB889C51A3D285773F3E6D8A88036FFC3B9CFC1F5177`；前置 JAR 为 5,670 字节，SHA-256 `904F4F8391EE9F4B7AADA752B244B11B072DCBFC51999483BDAA81C59B63B094`。交付 Core 与 `dynamic12` 相比只修改了客户端授权筛选，服务端动态结果不因这项变更重跑。



