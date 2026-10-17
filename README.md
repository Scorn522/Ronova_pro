# Ronova Pro

主工程：`D:\桌面\ronova_GPT`。Java ABI99 / Native ABI89 / Storage3 / Journal6，客户端协议 `r3-client-8`。
Java 17；用户态 GNU C23 / GNU C++23；独立 Windows 驱动及管理工具 C17。
未知业务载体沿真实实例字段、实际容器槽位和原委托调用接续；构图最终复核根字段、实际子对象与已用数组槽，脱离路径不沿用。ArrayDeque 支持环形逻辑槽及原更新中间态，ConcurrentHashMap 保留实际双表迁移、转发及树节点；不调用 getter/迭代器重放数据。自定义槽位的 helper、super.get 和持久绑定核实际调用点、物理方法版本及当前 getter 定义；失配保持未决。原业务调用执行一次，只有当前准确持有的数据方法接续已有动作，未知回调和临时返回对象不自动取得写权。
自定义 Map 键只在真实槽位查询前声明当前准确的 Java 键方法，记录自然 hashCode/equals/compareTo 的结果、实际读取依赖及方法版本；后续定位直接读存储，不重放键方法。键依赖与实体槽位根分开，写入、未知调用、依赖或定义变化使旧语义失效。已观察的原生分配与 DirectBuffer 借用视图按真实分配代次关联，活视图阻止误释放和完整回滚；地址向 Native 暴露后保留不透明使用，未知裸地址不补造来源。
Native 私有存储接入已观察模块实际消费的 HeapAlloc/ReAlloc/Free/Destroy、VirtualAlloc/Ex 与 CRT malloc/calloc/realloc/free，按真实 provider、heap context 和分配代次保留来源、读写租约及 DirectBuffer 借用。失败重分配保留旧块，同址成功也换代；原异常和 errno/doserrno/LastError 保留。已观察 DLL 的可写非执行静态段作为借用存储，活视图阻止卸载准备，归还额外 pin 不冒充实际卸载，原映射确认不在才退役。实际 Native binding、回调及工作线程的数据贡献保留已知模块并附 CPU 私有状态未知标记；不以未知字节撤销猜测 C++ 对象语义。Native 全局入口发布前初始化直接依赖的来源桥，避免首次初始化的 Unsafe 回入。
文件 Native 入口以实际 bootstrap 声明、方法版本和 operation 建立前置目录；BOUND 与尚未确认原函数的 ARMED_VM_ENTRY 分开，首次真实 NativeMethodBind 捕获新本体后发布门禁，分类失败不发布裸入口。客户端异常分派只在完整组合正文被实际 VM 接受后就绪，晚加载 EventBus 接真实 ClassPrepare；Core 绑定准确 loader/module/lookup，未选事件沿原 dispatcher 与原 Throwable 返回，清理失败保留原异常。
任务按 operation 增量入窗，恢复链用当前/历史轮转队列，存储工作用 ready/deadline 调度并接续 worker 完成及新保存读回。资源绑定缓存实际布局，队列、锁、委托或资格变化即失效；运行中的读回及关闭失败仍阻止释放。停组按模块在锁外串行发布，回调只标记续接，控制入口等待所选最新策略完成。Forge EventBus 组合注册、监听、注销和异常边界；普通来源观察失败按来源保留缺口，不污染全局控制状态，明确受控目标继续硬拒绝。
停组先绑定所选返回策略，再发布停止入口并清理；同模式重复交接保留固定 UUID。Native Attach/GetEnv 的观察失败记录覆盖缺口，保留原成功结果、环境与异常，不以观察失败伪造 API 失败或回滚附件。普通任务回调保存真实根、直接来源、浅层回调引用及会被平台清空的瞬时操作数，来源图在有界 drain 中解析，policy 使用独立 tick/关闭预算；未解析关系暂缓结清，实际构造/发布前的来源判定仍同步执行。已定位丢失按任务、会话与注册代次保留，无法定位的丢失仍阻断全局结清。
类加载按实际来源关系筛选，未命中保留轻量类头/引用及原字节供后续提升，不先建立完整执行图；来源发现与实际 loader/module 关联，失效的负缓存不再永久阻止发现。定义刷新失败保留续接与受影响来源，自动重试，成功才解除对应缺口。Native 未改写的自身类事件在跨 JNI 前返回；安装结果区分 FULL/DEGRADED/FAILED，只重试尚未发布或可独立恢复的阶段，不重复安装已发布的表与 Hook。构建与相关检查的实际结果见既有状态页。
初装各目标组共用一次真实类快照，批量转换已加载目标，不强制加载可选任务/句柄类；晚加载按真实返回 image 和精确 ClassPrepare 认证，Native 恢复仅补未完成目标。热执行 hook 在已建立 watcher 时查询实际 JVMTI 方法/版本、BCI 和物理深度，失效 token 不回退授权；未建立 watcher 的普通方法保留真实栈遍历。ForkJoin 用池级存量采样、真实写入回执与准确任务位置索引，扩容、外部字段/数组写和旧 writer 帧未退出时不报缺席。能力快照只在安装 epoch 改变时重算；CF 弱链快照复用时仍读实际链头及每条 next，最终结清继续核对当前字段、队列、退出与 ACK。
本版按用户选择删除旧 JDK 容器广谱 Hook：HashMap/Node/LinkedHashMap/Map、ArrayList 及其视图/迭代器、IdentityHashMap、ConcurrentHashMap、AtomicReference、CopyOnWriteArrayList 和 AbstractCollection 不再接旧变换、构造观察或操作追踪。保留 premain、Bootstrap、JVMTI/Native、线程/任务/类定义等根入口及明确的游戏/Fastutil 目标。普通容器方法写入、来源回填冻结与依赖这些作用域的清理暂时缺失，明确报告 JDK_CONTAINER_HOOKS_REMOVED；以下既有能力描述只在保留路径范围内成立，不能理解为 Native 已补齐被删防护。
启动交接复用当前真实 Agent 状态，安装中与失败后的已有部分 hook 不重复安装；预启动仅报告准备完成，保存的停组目标与非默认返回策略按实际所选服务端世界读回。保护与禁疗共用准确当前对象、代次、身份和世界资格；吸收值、着火、自定义 setter 覆写与对应字段/Unsafe/JNI 写门接同一判定。
自定义业务包装中的八种标准 SAM 调用沿当前真实方法、原指令与实际 callback/参数接续当前能力消费者；同步交接复用原调用的来源和返回关系。BiFunction/BiConsumer 的标准 andThen 接各原回调，保持原参数、中间结果、顺序和异常。未知具体类调用、反射/MH 与原生不透明路径继续保留边界。
当前接续补齐手动冻结区域的实际 section/palette 写入判断、字段图原子恢复与已知共享来源保留，并修正 Netty 停源拒绝抢在原 promise 校验之前的误伤。未知载体与未观察底层写入仍按状态页保留边界。
停组后的原 Netty WriteTask 保留发送来源直到 run/cancel 的 finally；拒绝写入分步执行消息释放、promise 终结及原 flush，异常不跳过后续收尾。清理资格绑定实际对象关系、字段/数组槽、准确调用链和实际递归层，不向用户回调提供写权；停组任务在原回收位置丢弃，不再送回共享对象池。实际运行结果仍以状态页为准。
冻结区域的原版位存储、实际 backing 数组、调色板值表及 section/Data 替换接入同一当前世界目标判断，允许冻结格内容不变的邻居写入和原版扩容。活跃效果的真实 Optional/FactorData 连接和字段也接入原保护门，原版 tick 与准确读取回填保留。Netty 原版执行器的真实拒绝分支补 release、失败通知与取消的异常收尾；无法确认未入队的任务保留待结清。
标准全局方块状态表的正向列表、反向编号及其数组也纳入冻结判断；原版 rehash、删除与迭代器搬移按本次实际键值接续，冻结映射在副作用前拒绝，半写仅按准确旧值 CAS 收尾。效果的标准解药列表复用已有 ItemStack/NBT 保护，包含常见 JDK/Guava 不可变及单元素列表，实际 provider 覆写的返回值只在原 getter 一次发布。默认满队列拒绝支持真实 LinkedBlockingQueue 和标准有界 MPSC 两种实现，按活跃原读取/offer/REJECT 链接入原收尾；自定义队列/handler 不取得该资格。Windows JDK 的实际 Net.socket0 创建入口补入 Native 来源和停组判断，未解析的旧入口仍保持 pending。
装备与解药物品的能力图继续沿实际 provider 私有实例字段、自然解析的 LazyOptional 值和真实 getCapability 返回值接续，包含嵌套 ItemStack/NBT、常见列表、映射、包装容器及实际存储。自定义载体的真实实例方法接当前物品动作链，能力失效或 provider 脱链后解除对应关系；不主动调用 getter、序列化器或 supplier 重放能力，回调不自动继承物品动作许可。只读字段与布局来自当前 VM，未解析字段类型明确报错；世界、实体、全局定义和执行回调保持借用边界。文件映射复制保留实际父描述符、原生返回、新描述符发布及异常副本收尾。未知原生私有状态和未解析原生入口仍保留边界。
能力供应、消费、映射、筛选与失效监听沿原 callback/value 单次执行；Forge 和 Java Optional 的非空派生关系只在同一当前动作内接续。失效监听保留原 Set.forEach，分别绑定真实外层分派消费者和内层 listener；发布门仍在供应者返回后开启。VM 按真实 CP/BCI、版本、来源与最多16个物理帧接直接业务链，二次函数入口只允许准确纯转发适配，未知回调、反射/MH/动态间接链不借权。全局共享 EMPTY 不登记派生 owner，回退函数原样运行。自定义槽位仍沿自然 getter 实际读过的位置复核；标准 MapN、Guava Map、HashMap 树桶及 EnumMap 的当前字段、查询路径和值数组也已接入，支持准确 Enum/Integer/String 键语义，不调用任意 key 方法。未知自定义 getter/容器、键语义或原生状态继续保留边界。
自定义槽位 getter 的同步 Java helper 接在原调用前后，记录原 receiver、参数、返回及正常读取，不重放 getter；支持真实虚分派、private/static、祖先 super 和直接接口 super 的实际 Java 方法。最终仍按原实体、槽位和当前实际存储复核，未知平台方法、构造器、native、歧义或角色冲突保持 unresolved。JDK Function/Consumer/Predicate 的标准组合在真实私有实现体中逐原回调接续，保留中间值、短路、异常和原次数；委托沿当前已确认能力回调的物理链，未知组合及未接入的异步链不继承。
当前合法能力回调提交的标准执行器、Future/ForkJoin 包装及 CompletableFuture 回调可沿原实际任务和 callback 单次接续。worker 新建自身作用域，只接原装备的 ItemStack/NBT/能力数据，不继承换槽、实体或世界权限；执行前与写入时复核当前装备、代次、世界、dispatcher 和实际 optional 链，失效、脱链、换装或停止来源不借旧许可。票据只保留弱对象关系，不持原 Scope 或提交线程，原异常和 Future 完成路径保持。兼任 callback 的真实数据容器仍追踪实际存储；继承标准 get 的自定义 List/Map 子类只在 VM 确认其真实实现后按既有父布局读取，未知覆写/键语义仍沿原 trace 或保持 unresolved。
本轮新增旧运行帧的准确退役与来源收尾、GameMode/Item 真实覆写动作接续，以及手动冻结区域的标准方块写入阻断。Windows 当前游戏进程的外部新建/复制危险句柄保护使用独立命令 `/ronova_pro host protect-jvm true`，`false` 撤销；沿实际玩家/控制台资格，各运行实例只撤销自己的选择，显示实际驱动回执，命令不会安装或加载驱动。旧帧仅对无未知清理责任的可退出子集强退；未知 finally/锁、未观察的机器码/系统调用、既有句柄和未知自定义载体继续保留边界。
当前源码包含连接前队列逐次发送来源及 EventLoop 接续、独立禁疗、已观察 Host 效果的短提交门，以及原生 Unsafe 写入/free/realloc 的准确控制范围拒绝。外部 JVMTI/堆回调暂时移除借来的内部计数，结束后恢复。活跃效果、FoodData、标准身体的当前六槽装备及其真实 ItemStack/NBT 接入实际写入防护；换装、物品使用、食用、真实攻击与延后挖掘按准确原业务动作交接，拒绝取出时返回 EMPTY，避免留下物品副本。挖掘续接绑定实际玩家、GameMode、世界、目标坐标与当前工具，普通回调不能借用；JNI 数组副本保留获取时的已知及未知来源，提交时并入原检查。FoodData 防写保留自然消耗，不等于免饿。区域禁生成接入底层管理器准入。未观察的原生状态和未接入的正常动作仍保留边界。实际构建、网络及游戏结果以
[当前进度](docs/A-批验收状态.md) 和 [实际报告](validation/b-final-20261007/REPORT.md) 为准。自身外观及武器注册暂缓，完整 C/B 尚未完成。

禁疗复用现有管理命令，目标为单个实体选择器或 UUID：

```text
/ronova_pro no-heal <目标> true
/ronova_pro no-heal <目标> false
```

单独禁疗拒绝实际升血，正常伤害继续生效；可与 `protect` 组合，单独关闭禁疗保留防护，`release` 撤销两者。服务端与客户端使用同版 JAR；旧 `r3-client-7` 不解码新增策略字段。

## 目录

| 目录 | 用途 |
| --- | --- |
| `src/core/` | 游戏功能与 Mixin |
| `src/agent/` | Java 类转换、premain 与 attach 代理源码 |
| `src/bootstrap/` | JVM 公共桥和 Native 接线源码 |
| `src/prelaunch/` | 游戏启动前的引导入口 |
| `src/journal/` | 共用持久日志 |
| `src/resources/` | Mod 配置、Mixin 和资源 |
| `native/`、`native/driver/` | 用户态原生代码、Windows 驱动源码和构建工具 |
| `validation/` | 现有夹具、检查源码与原始运行记录 |
| `docs/` | [文档入口](docs/README.md)、设计、状态与历史 |
| `distribution/` | 当前打包脚本和各日期候选包 |
| `build/`、`.gradle/`、`.work/` | 构建产物、缓存和本地工作资料 |

`agent` 是程序自己的 Java 启动代理模块，与对话子代理无关。
有效源码只有 `src/<组件>/` 这一套，Java 包名和运行入口保持原有结构。
旧的根目录 `agent/bootstrap/prelaunch`、`src/main/test/fixture/stateChecks`、
`validation/src` 及输入补丁已保留到 `.work/archive/source-layout-20261014/`。
整理前的完整 README 保存在 `docs/history/README-before-layout-20261014.md`。
旧候选和失败记录仍保留，不能当作当前候选的通过结果。

## 构建与打包

Windows x64 上使用 JDK17 和 Zig。先写齐工作包，再按实际需要构建：

```powershell
.\gradlew.bat jar -PcontrolZig="Zig路径" -PstorageZig="Zig路径"
```

主 JAR 已包含 Agent、Bootstrap 和用户态 Native 载荷。需要游戏 JVM 创建前生效时，
使用已有 prelaunch 入口，传入启动器原本的全部游戏 JVM 参数：

```text
java -jar ronova-pro-prelaunch.jar --java <游戏java.exe> --core <当前core.jar> <原有全部游戏参数>
```

在支持的 HotSpot JDK17 中，prelaunch 为新游戏 JVM 禁用对应 Unsafe 写入及 `System.arraycopy` 编译器内联，使其回到 Java／Native 写入守卫；用户原有的禁用清单继续累积。直接加载 Mod 的晚挂载方式不具备这项启动期约束，也不能据此声称已接管旧运行帧或任意原生机器指令写入。

源码和对应候选 JAR 直接同步 GitHub；后续不自动生成源码 ZIP。现有 `distribution/package-current.ps1` 仅在明确要求打包时手动使用。

驱动构建、签名和显式部署见 [驱动说明](native/driver/README.md)。
本次 WIP 中的 SYS 未签名、未加载；完整游戏、来源收口和保存重启结果仍按状态页解释。
执行范围遵循 [工程规则](AGENTS.md)。
