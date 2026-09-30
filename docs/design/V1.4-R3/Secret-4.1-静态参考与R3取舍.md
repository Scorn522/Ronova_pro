# Secret 4.1：静态参考与 R3 取舍

日期：2026-09-20。只静态读取源码、归档和 class 字节码；没有运行样本、其 DLL 或附带脚本。**阅读的是与启动、攻防和分派相关的核心路径，不声称全部 Java/Native 代码已审完，也不作实机强度排名。**

## 样本身份

| 项目 | 已独立核对 |
| --- | --- |
| 用户 ZIP | `Secret-4.1-decompiled.zip`，794,191 字节 |
| ZIP SHA-256 | `ea191b0617c6175b7001e3627d6eae9ddc7993c0abf1f265d02f3951d2821d74` |
| 解包内容 | 161 文件，1,296,063 字节；69 Java 文件、8,230 行；76 个 class 文件 |
| 恢复 payload JAR | 420,582 字节；`fc7f3cdbefa993bcec377963f2b18a4c59a8cfb889dbbd6dccdd231f3fb12c45` |
| `himitsu.secret` | 157,184 字节；PE x64（machine 0x8664）；`8413fd72a0c42903abc407bfe091ccff6a978df61c4fe3e8d7a0959e4b1195b5` |

解包目录：项目内 `reviews/secret-4.1-20260920/Secret-4.1-decompiled`。下列定位均相对于该目录的 `src/main/java/com/himitsu/secret/`。原 Mod 的其他哈希及 `NATIVE_ANALYSIS.md` 所述 Native 内部行为属于附件作者分析，本次没有将其自动认定为独立验证。设计包不含第三方源码、DLL 或恢复 JAR。

## 读到的实际机制

| 源码定位 | 静态事实 | 对 Ronova 的取舍 |
| --- | --- | --- |
| `SecretMod.java:41`；`bridge/SecretNative.java:43` | Mod 类静态初始化加载资源 DLL，调用 EarlyGuard/Bootstrap 导出，然后查找 NativeBridge；入口没有要求用户手填 Agent 参数 | 参考自加载顺序。静态初始化在自身构造前，不等于 JVM premain 或早于所有其他 Mod 活动 |
| `code/BootstrapPayload.java:58`；`util/SecretRuntime.java:43` | Java 请求 Native 解码/DefineClass；运行时先装 hook、ClassDefense，再注册自身内容并激活分派 | 参考“必需执行入口先准备、功能后开放”。解密和加密载荷不直接提升攻防，不搬外壳 |
| `hook/HookTransformer.java:127` | GUARD 插入条件返回，OVERRIDE 换方法体，CLEAR 放默认返回，RETURN 可改返回结果；跳过 native/abstract 方法 | 参考小型共享变换器，保留准确调用条件。整体覆盖不能直接泛化为任意对象防护；native 方法需另一真实边界 |
| `core/ClassDefense.java:29,93`；`core/MethodDefense.java:29` | 枚举类、取得字节、还原/变换、请求 Native RedefineClass；MethodDefense 比较并替换实例方法指令 | 可参考定义/分派接通；不把请求返回 true 当最终生效证明，不盲目清除其他正常变换 |
| `core/LaunchPipelineReplay.java:43` | 重新调用一组 ModLauncher/Mixin 处理器来构造比较基线，失败可返回原字节 | Ronova 不整体照搬：处理器未证明可重入、幂等，重复运行可能有副作用；资源字节也不必等于真实最终定义 |
| `core/SecretEntityHooks.java:524,614,632,789` | 直接重写 TickList/Lookup 的增删和查询，对 ChunkMap/卸载入口设 guard；多个 hook 包含子类 | 参考准确真实拓扑入口。旧别名、底层数组、视图、整 manager 替换仍需各自落点，不能只靠这些 hook 声称全覆盖 |
| `core/AuthoritativeEntityTopology.java:131` | 保存玩家 anchor，替换 ID/UUID map、tick 表、玩家表、parts 和 tracking，并做维护补回 | 参考不依赖可覆写 remove 的实际载体操作。全表替换必须处理合法并发、旧视图与迭代状态；补回仍是修复 |
| `core/CivilizationSurvivalHooks.java:111,191`；`core/CivilizationSurvival.java:458` | 生命/存活/移除查询可返回固定结果，维护持续写回生命、标志和索引 | 不作为“原状态从未受损”证据。Ronova 重点写真实状态变更前的拦截，查询只反映实际状态 |
| `core/CivilizationSurvival.java:238,404` | 持续清除标识对非玩家使用 EntityType，存在无范围限制的匹配；全局攻击分支还可覆盖其他非 owner 实体 | 不采用该范围推断。它能扩大压制范围，但不能证明只终结了某个准确对象的来源 |
| `core/EventBusTakeover.java:131` | ACTIVE 后普通监听器只在被认定为 essential 的事件上继续分派，本方 handler 另外调用 | 学本方必要执行不依赖可被吞掉的普通总线；不照搬整批停派。准确 listener/invocation 单独控制 |
| `client/render/NativeRenderBridge.java:59` | 请求绑定多种帧边界，回调按层补绘，并调用玩家/GUI 维护；实际回调返回 true | 可参考呈现连续性。补绘、覆盖画面不等于错误 draw 未提交；Native 实际拦截范围未被本次静态 Java 阅读证明 |
| `network/SecretNetwork.java:30`；`network/SyncPacket.java:20` | 自有频道向客户端同步，消费经 enqueueWork；解码有大小上限，出站先 encode/复制再检查长度 | 不把自有同步当通用包防护；R3 对准确 producer 在分配/排队前限额，消费时处理旧代 |
| `core/BackgroundService.java:36`；`core/SecretThreadProtection.java:9` | 后台循环定期记指标，关闭设置标志并 interrupt；线程保护通过 Native 命令调用且吞异常 | 不据“ProtectThread”名称断言实际保护成立；close 未等待真实退出，不可用于任务结算完成 |

`HookTransformer` 的 native/abstract 跳过与 GUARD/OVERRIDE/CLEAR 分支已用可信 JDK `javap` 静态对照 class 文件，输出保留为 `reviews/secret-4.1-20260920/HookTransformer.javap.txt`。首次以恢复 JAR 作 classpath 没找到目标类，改用解包 class 目录后完成对照；这不是运行测试。

## 额外发现的限制

- `SecretNative.java:73–74` 写固定载荷路径并截断日志；资源与提取内容比较不能替代来源信任，也没有据此证明多实例和升级锁竞争安全。Ronova 使用自己的版本/摘要缓存和准确实例生命周期。
- `PayloadTransformer.java:63` 有等客户端 Minecraft 类出现的无界 spin。是否会在某启动路径执行需继续看调用关系，不能只凭存在该方法断言专服必卡；Ronova 不采用无界等待模式。
- `MethodDefense` 的比较针对实例方法，`ClassDefense` 对部分异常吞掉并返回 null。不能把“类防护”名称理解为所有字段、静态方法、Native 和已运行栈帧都被控制。
- 部分生命周期、UI 和 EOP 相关分支针对具体类型/包名。本次不移植样本特判，也没有逐项审计这些兼容分支或完整 GUI。

## 对 R3 的直接影响

采用早期自有桥、小型共享变换、真实索引操作、必要执行独立于普通 EventBus 以及准确 Native 调用边界。进一步落实到真实槽位/别名、任务派生、资源拆除、包和 draw 的实际提交点。

**不复制后置伪装成前置、不复制全类型封杀、不复制整体停派事件。** 这些取舍不等于宣称 Secret 弱；本次仅能说明代码采取了哪些手段，以及哪些适合 Ronova 的准确通用控制。Ronova 当前实际失败仍未修复，R3 是方案，强度需之后在同条件自有场景验证。
