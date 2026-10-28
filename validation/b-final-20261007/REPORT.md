# B 批实际报告：2026-10-09 代码收敛接续

## 2026-10-28：性能优先调用点守卫 / ABI106 已构建，当前 main 分发瘦身

用户明确接受性能优先边界：保留武器、整 Mod 控制及关键/已登记目标在已观察入口上的保护，取消普通原版/库对象全量逐字段监控和全 JVM Unsafe/CAS 机械兜底；未织入桥接及选中前完整来源历史是明确覆盖缺口。随后授权更新 GitHub，并确认“100 MB 以下”仅指当前 main 文件，保留历史。起始 HEAD 为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d；此次同步包含此前未提交的相关生产修改、武器资源、既有检查及摘要，本机 IDE、世界、备份、原始日志不上传。

本批替换：ApplicationBoundary 仅为实际 Mod/已归属外部调用织入 ModMemoryBoundary；普通原版/库字段缩到核心生命、装备、世界和自身敏感字段，普通对象创建历史不再全量登记。GuardedCalls 在观察到的 Unsafe/VarHandle/Buffer 调用点执行真实目标检查，并接通反射、Unsafe MethodHandle、VarHandle.toMethodHandle 与 invoker；保留原调用者 Lookup 的访问限制、原 JDK 类型/越界/只读检查、实际偏移与收据失败清理。普通 JDK Buffer 数据方法恢复原实现；文件通道资源范围保留并迁入 ResourceBoundary。移除 UnsafeWriteBoundary、InternalUnsafeBoundary、旧全局 VarHandle/Buffer 操作织入、旧 Native 标量候选分支及禁用 JIT intrinsic 的启动参数；Native 仅对识别出的实际 VM 机械入口直通，分配/释放、库生命周期及其他 Native 入口保留。Java/Native ABI 同步至106；安装状态明确为 INSTALLED_PERFORMANCE，公开覆盖说明不再声称全 JVM 字段观察。

统一构建及最少相关回归：首次编译定位到文件通道仍引用旧 Buffer scope，随后修复；第二次定位到构建仍列入已替代 unsafe_scalars.c，删除失效输入。实际 Native 回归又分别抓到 MethodHandle 隐藏工厂帧误拒绝、Java17 ByteBuffer VarHandle 实际类型分类错误、以及新增入口被误当控制器自身写入，均已修复并保留失败日志。最终 build-performance96-r5.log：BUILD SUCCESSFUL，28s，22 tasks / 12 executed / 10 up-to-date。Core、Agent、Bootstrap、Prelaunch 与 Control/Storage Native 均在本完整批次实际构建，最终重试复用本批未再改变的产物；没有将旧 ABI105 Native 当作本批通过。

通过的实际检查：原 JDK 并发/任务/Buffer 生命周期；经真实字节码织入的外部 Unsafe/CAS、VarHandle 字段/数组/ByteBuffer 视图、反射调用者访问和参数转换、Unsafe/VarHandle MethodHandle/invoker；登记后相同目标拒绝外部修改，新增包装不得借控制器身份；复制来源隔离、跨线程/已关闭收据及失败清理；21 个 JDK 批量 Buffer helper 不再含 Ronova 数据操作调用。测试没有声称未知桥接或全局原生操作仍被覆盖。

当前 distribution/v1.5-whole-mod：Core SHA256=B70DFA18EBB18B3C4CCB8565C0C97FA495F3A35136EE1B6087D29D255F886690；Prelaunch SHA256=F6C317A53AC206E2B08AA2A341BA057AB3711B822D94FD1E126BEF537DDF7292。该 ABI106 候选尚未部署/启动 PCL，加载时间、FPS、极简武器实机效果均未执行，不能继承94或更早候选结果。实例已恢复原 Mod/启动目录及 Time/DayTime=6787 世界，没有游戏进程留在运行。

仓库瘦身：从当前 Git 索引移出91个旧 distribution 文件，约123.66 MB，91个本地原件和全部提交历史保留；当前分支仅保留最新 Core/Prelaunch 与打包脚本，旧候选忽略规则同步收敛。原 main 文件总计约141.21 MB，移除后约18 MB；这不代表历史仓库也降到100 MB以下。下一步仍是用同一 PCL Mod 环境验证 ABI106 的真实入世/FPS/武器及正常保存退出；旧 Already building 偶发故障和退出尾声任务未收敛记录仍保留。

## 2026-10-28：93 入世仍为0 FPS并退出0；94 验证精确线程快路与复制来源去重

用户“继续”恢复本主线。HEAD 仍为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d，原有修改保留，没有提交或推送。第92次使用上一批完整候选48D17D65 / ABI105，重新完整备份697文件、569.89MiB、0失败，沿用80的26个第三方文件（启用2个）及82项参数。PID54496于2026-10-28T05:17:41.6547908Z启动，05:25:11开始出生区，05:33:58完成，实际日志526888ms；较91的739178ms缩短约28.7%，不是FPS或严格总加载加速结论。

05:34:01发生RenderTarget提交画面时的BufferBuilder “Already building!”，未登录世界。三维度05:35:51保存完，Time/DayTime4192→4193；05:36:10.3221747Z自行ExitCode=-1。没有GPU_QUAD_SORTING_LAYOUT_CHANGED或StackOverflow复现记录，但未到达相同的世界内工作负载，不能记为这些路径已完全通过。load92/resource92/region92及线程、崩溃报告、失败世界保留在attempt-92；原Mod/启动目录与4192世界已恢复。窗口工具本次恢复，可实际观察画面。

生产定位暂未宣称92根因修复：ControlBridge仅在BufferBuilder.building/f_85661_写入实际被拒绝时记录分支和堆栈，正常顶点写入不新增日志，拦截结果未放宽。build-buffer93.log整体16s成功，Bootstrap重编/Core重打包，Core/Agent/Prelaunch及Control/Storage Native复用已有同批产物。诊断候选B4453A69D250DC10C2FA1D93424A20EBB9DD8A25918FDE41E27ACCEC92306CA3用于第93次，完整备份700文件、571.00MiB、0失败，PID50000，仍为原Mod和82项参数。

93实际结果：05:47:42.2897329Z启动，05:54:59开始出生区，06:03:11完成，491844ms；06:04:23记录加入游戏，约16.7分钟。第92次Already building未复现，building拒绝诊断亦未出现，因此原故障仍待复现、不能称已根治。后续通过窗口直接确认雪地/HUD，F3为0 FPS、IntegratedServer约1097ms；日志持续严重落后，性能明确失败。world93.jfr为23执行/585分配样本，含暂停菜单返回过渡；play93为14执行/303分配样本，采样期间切出F3，均非严格基准。world-threads.txt抓到Render的Uniform→FloatBuffer.putArray→copyReceipt/copySources→executionByteMemoryReadSources/getCallerClass，以及Server的LockSupport.setBlocker→Unsafe.putReferenceOpaque→beginNativeUnsafeMutation。工具命令输入不可靠，武器未完成实际操作验证。

用户正常退出后，07:23:19Z三维度保存完成，07:24:16.5136947Z自行ExitCode=0，Time/DayTime4192→6787。stderr仍有1条RONOVA_PRO_UNSETTLED_SHUTDOWN TASK_RETIREMENT_EVENTS_PENDING，以及SuperSteve线程在ClientLevel消失后的NPE；不记完全干净退出。原Mod/启动目录已恢复，推进后的世界保留在实例并另存world-on-exit，未强杀。

本轮后续生产修改：ScalarPolicy把真实Thread对象仅作为Native候选，C按bootstrap Thread的parkBlocker字段名/descriptor/实际offset/宽度筛选，并用独立callerMask只接受真实LockSupport jmethodID；Thread其余字段及外部Unsafe调用保留原守卫。HeapMemoryRead改为明确的Module[]快照，堆复制在同一收据内复用捕获来源，去掉重复clone和与自身的集合合并；外部返回仍隔离数组，线程/锁/关闭状态校验及可变Native读取来源的重新查询保留。删除CodeSourceBridge四个不再承担独立职责的executionByteMemory读取适配器，没有删除真实Native收据或扩大普通地址放行。

build-blocker94.log整体44s成功：Bootstrap与Control Native实际重编，Core/Agent/Prelaunch/Storage复用；实际LockSupport发布/清除/park生命周期、外部Unsafe拒绝和Thread其他字段保护通过。完整来源去重后的build-copy94.log整体34s成功：Bootstrap与检查代码重编、Core重打包，Native复用本轮Thread修订产物；快照隔离、跨线程/已关闭收据拒绝、复制失败释放、实际float[]到direct Buffer批量复制，以及既有Native/Atomic/Buffer/数组相关回归通过。

94最终结果（Core 2A880DC5 / ABI105）：PID73404于07:36:22.5259265Z启动，07:44:34开始出生区，07:54:07完成，572540ms；07:55:31记录加入游戏，约19分9秒。world94.jfr为26执行/793分配样本，含入世/F3过渡；Render仍在SourceMapBridge.holderChanged/getCallerClass，Server仍在JDK锁CAS/字段守卫。用户确认0 FPS或个位数帧；截图为暂停菜单背后1 FPS，不当稳定基准。08:11:35工具正常点击保存退出，08:13:58三维度保存完成；08:36:00.1468425Z最终ExitCode=-805306369，未确认原因，未强杀。stderr仍有TASK_RETIREMENT_EVENTS_PENDING，不能称干净退出。世界6787→6955已另存world-on-failure；原Mod/启动目录与测试前6787世界已恢复。随后仅补全JDK Buffer私有批量helper的95候选75E960B4，build-buffer95.log成功31s，但未部署/未启动；已被上方ABI106完整架构替换。


## 2026-10-27：92 修订批次已写齐并构建；按用户要求停下，未重启实机

起始/结束 HEAD 均为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d，保留原有未提交与未跟踪修改，没有提交、推送。最终 Core 为 48D17D65E64D4B5067B2017FF0B50B0D6648DA1CFAC8228CCD75138C79BC21FB，Prelaunch 为 CD0B414E5870BD099C4C8652933F7F8C2C947A651C8DFACE8CDAE9B1A39B0804，Native ABI 105；distribution/v1.5-whole-mod 已更新。最新用户“写完就可以停了”覆盖此前继续实机安排：本修订候选未部署到游戏，未启动完整第92次修订验证，未宣称FPS或武器实测通过。原实例Mod/启动目录及Time/DayTime=4192世界已恢复。

生产修改：GPU批次改为按真实Builder身份登记的线程私有弱索引，常见读路径无分配/无队列poll/无全局GPU锁，冷路径保留合法线程交接并重新采集其正来源。修复多渲染层非LIFO交错漏记；VertexSources补齐indexed add、bulk add、clear/truncate，数量偏差会对齐实际计数，缺失段保留未观察标记，不能冒充已完整退役。顶点异常也有finally清理在途状态。sortImage在数量不符时统一记录GPU_QUAD_SORTING_COUNT_CHANGED并返回未完成的SortImage，渲染继续；真实载体与非数量布局校验保留，未知段不填成已确认来源。

ScalarPolicy与native/unsafe_scalars.c同步加入AtomicLong/AtomicInteger/AtomicBoolean/AtomicReference自身字段路径，仍由C叶子核对真实bootstrap类、字段offset/宽度/引用种类、实际JDK调用方法与当前VM binding，不按传入类名直接授权。BufferBoundary/ResourceBridge把asChar/Short/Int/Long/Float/DoubleBuffer纳入快路，真实父存储/地址范围/别名与正来源登记继续执行，原JDK范围、只读、scope检查保留。ClientDrawFaults.State改读GlStateManager当前blend/depth/cull/scissor缓存值，固定字段Getter仅解析一次、每次读真实当前对象和值；不缓存权限。原版没有缓存的FBO/program/viewport/scissor坐标继续实际查询，改用现有int[]接口，去掉这些查询中的MemoryStack包装；不宣称零驱动查询。

同主线收敛：BackingBridge六种invokeExact形状统一为实际共享适配器，在发布策略前完成链接，修复91的JFR ThrowableTracer→AtomicLong→首次Backing offset链接递归。ClientWorldVisuals方块/液体入口改为begin/end范围令牌，由Mixin直接调用原Forge renderer，只有活跃视觉策略才创建令牌；删除每方块Runnable包装及旧block入口。删除无调用方的旧数学类白名单。原87 primitive数组快路、整Mod控制及真实防护/停止切换路径保留。极简武器仍为携带守护/飞行、左键96格视线与近身目标、右键64格非玩家清场、Shift右键/R四页按钮列表；左键允许实际射线中的其他玩家，右键仍排除玩家。没有恢复模式切换或输入框。

构建与最少回归：build-interleaved92.log整体55s成功，交错层及JFR适配器相关检查通过；build-render-batch92.log整体1m4s成功。最新五项完整批次build-fps92-r1.log整体1m8s成功（22 tasks，16 executed/6 up-to-date），Java Core/Bootstrap/Agent与Control Native实际重编，Storage Native与Prelaunch复用。新增真实Native Atomic四类CAS/update、六种typed Buffer视图与原只读/越界、既有VarHandle/锁/CF/ForkJoin/数组/外部入口拒绝检查通过；原PCL intrinsic参数+早期Native Agent下的check-pcl92-r1.log同样通过。最终仅提取统一数量兜底职责并修正独立夹具范围，build-fallback92-r1.log整体21s成功，Java Bootstrap/检查代码重编、Core重新打包，Core/Agent生产类与Native复用同批产物；check-gpu-recovery92-r1.log通过外部载体拒绝、pending保留、indexed插入、计数恢复与失败后继续跟踪。该最后检查为元数据模型，不是真实Forge载体通过。早先元数据启动缺少sun.nio.ch opens，以及把无Forge realm的类当实际载体而被拒绝的日志均保留；没有放宽生产载体鉴权来让夹具通过。

91实际结论：已越过87的AB-BA位置，Preparing start region=739178ms；13:21:01.503Z记录玩家加入，用户确认“地形已显示，仍很卡”，没有获取数值FPS。登录后login91.jfr为执行52、分配2061，ClientWorldVisuals.block链分配1465；该段包含暂停/保存，不能当稳定游戏帧率。尝试补采world91时进程已退出，未生成该世界段文件。13:27:33.1287177Z自行ExitCode=-1，GPU_QUAD_SORTING_LAYOUT_CHANGED导致Batching chunks崩溃；stderr有两组Backing offset适配器首次链接/JFR异常计数递归的StackOverflow/JPLIS断言，以及退出TASK_CAPTURE_PUBLICATION_IN_FLIGHT。三维度保存完成，实际保存世界Time/DayTime=4252；客户端崩溃报告显示4291，与服务器保存值分开记录。世界、JFR、线程和崩溃报告保留，未把保存完成写成干净退出或性能通过。

此前92预备候选200998F4于13:49:18Z启动，PID50860；新五项指令到来后为避免混算，于14:10:19Z中断，ExitCode=-1，未完成该批验收。完整现场另存attempt-92-prepatch；15:05:41Z恢复原实例和4192世界，并完成backup-before-attempt-92-r1完整备份569.89MiB/697文件/0失败。未启动修订实机。窗口工具此前两种入口均报kernel assets路径不存在，仍不能代读F3/点击武器；当前停下的直接原因是用户明确要求，非等待再次授权。下次从本最终候选开始，优先验证GPU数量兜底/Atomic与视图实际热点、稳定FPS、武器R面板及保存退出，不沿用91或旧92结果。

## 2026-10-27：加载死锁与世界热路径、极简武器；91 已入世后崩溃结束

起始 HEAD 为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d，保留原有未提交修改，本批不提交或推送。用户优先要求世界内 FPS，授权普通 primitive 字段/数组及不受限 Buffer 快路，并以极简武器替代聊天指令与填表。当前最终构建/第 91 次实际部署 Core 为 BC05A984CB27E9BE7137F37FF6A1B09B076DCD8A645784394B6C150925252250，Prelaunch 为 CD0B414E5870BD099C4C8652933F7F8C2C947A651C8DFACE8CDAE9B1A39B0804，Native ABI 105；distribution/v1.5-whole-mod 已更新。

87 失败：login-threads.txt 确认 Render 的 ModuleClassLoader 锁与 Server 的 MixinLaunchPluginLegacy 锁形成 AB-BA，字段策略回调中首次链接 RecoveryRecords 内部类。未加入世界、无 FPS/武器通过；Preparing start region 为 598321 ms。确认死锁后仅结束测试 PID 7820，ExitCode=-1；保留中断世界与原始证据，10:08:25Z 恢复原 Mod/启动目录及测试前世界 Time/DayTime=4192。play87 是加载/死锁过渡，不能称为游戏采样。

生产双层修复：TransformScope 在实际转换/序列化范围识别真实 ASM 模块及类加载器，仅隔离其内部对象的 Core 字段策略回调；受控身份/敏感登记/停止保护检查保留，不把整条转换线程放行。RuntimeClasses 从已安装自身 Mod 文件枚举并初始化运行时/全部内部类和 accessor 接口，具体 Mixin 定义由原加载生命周期处理；专服排除客户端类。同一预加载在 Mod 构造末尾完成，ProRuntime.start/客户端策略安装仍要求完成后才能发布回调；BackingPolicy 固定反射元数据单独准备。预加载失败拒绝安装，不回退到热路径首次加载。

热路径：ClientBoundary 直接传真实 vertices 计数；verticesEntering/Completed 只维护每批计数与正来源运行段，无逐顶点 StackWalker/反射/Buffer 字节读回或 GPU 全局锁。ModSources 在真实 Mod 方法边界维护正来源快照，只用于 GPU 归属，不用空快照授予权限；隐藏类用含隐藏帧的真实直接声明类校验，普通类用轻量调用类校验。顶点字节处理收敛到批次/显式退役；ensureCapacity 通知只在实际 ByteBuffer 字段写回之后执行，无扩容的每顶点分支不再爬栈，真实重分配继续校验存储/来源与已有保留数据。普通 primitive 实例字段快路扩展至常规类，仍核对当前受控对象/字段/来源发布/停止保护；RecoveryRecords 空 writer 索引、primitive 及本就排除的命名空间无锁短路。DirectByteBuffer 视图/order/批量 get/put 当前策略快路保留真实父存储别名、地址范围及 JDK 原边界/只读/scope 异常。无冻结视觉时 ClientWorldVisuals 不解析 Quad/UV；区域规则实际变化触发原版网格重建，使晚到规则取得标签。87 的 primitive 数组优化保留，不缓存权限或负面来源结论。

武器：删除七模式/保存快捷/EditBox/选择器坐标UUID表单，以及整个 Brigadier dispatcher，游戏内不注册 /ronova_pro。携带自动 protect + mayfly；移除时仅撤回本次仍持有的授予，不撤销既有保护及创造/旁观飞行。左键含空挥包执行 96 格射线和前方近身抹除，方块左键 clearBlock；右键固定 64 格非玩家清场，可一键开启连带停 Mod；Shift+右键/R 打开四页列表。Mod 停止/退役/保护，区域冻结/禁生成/快照/重置/回溯，附近实体/字段，历史撤销/宿主/诊断/只读重放均接线。服务端发不透明行标识并重新检查实际玩家/权限2/携带/线程/会话nonce期限序号间隔/允许动作；被动守护不要求权限2。世界卸载仅用原版可释放区块，不破坏活动票据。重放不再次执行攻击。沿用老版32×32刀身，移除不适合标准图集的1324×1847叠层；旧工程不改。实际武器操作未通过实机前不作成功宣称。

构建/检查：build-fps88-offline.log 整体成功26s，Java Core/Bootstrap/Agent/Prelaunch本批实际重编，Control/Storage Native ABI105复用；build-fps88-metadata.log 最终jar/元数据及9项任务检查成功25s。真实Native writer、VarHandle/CAS、JDK锁/CF/ForkJoin、Buffer与raw生命周期/边界/只读、普通数组转换/受控身份、晚到敏感登记、外部伪造入口拒绝及武器无环境权限检查通过。原PCL intrinsic参数+早期Native Agent的检查通过；日志文件实际IO/追加重开/只读重放通过。后续按实际失败仅重查相关路径：build-hidden89.log与check-hidden89-pcl-flags.log的真实隐藏类进入/退出及既有拒绝通过；build-capacity90.log使用实际BufferBuilder图验证通知仅随真实存储写回、没有无扩容分支回调，整体27s成功；build-preload90.log最终jar+相关Native检查45s成功（22 tasks，10 executed/12 up-to-date），Core实际重编、Agent复用同批扩容修正版、Bootstrap/Native复用。初版编译/夹具/离线证书连通探测失败日志保留；单次离线参数只跳过ForgeGradle连通性探测，不改系统证书/仓库设置。独立JVM检查不等同游戏通过。

88 失败已闭环：Core B5AEE8E8 / ABI105，备份570.11MiB、688文件、0失败，PID42452。新增ModSources.getCallerClass漏掉隐藏lambda帧，误拒绝SuperSteve网络初始化与SSWork/SSExDef；Forge CONSTRUCT失败、未加载世界。保留load88/resource88与堆栈；12:02:19Z仅结束该失败进程，ExitCode=-1；12:03:11Z恢复原实例与4192世界。修正为仅隐藏类可见隐藏帧，真实类不匹配仍拒绝。

89 失败已闭环：Core4EDFE051 / ABI105，备份569.82MiB、690文件、0失败，PID43404。资源/Mod构造完成；Preparing start region从12:17:55.730Z到12:26:03.467Z，日志487648ms（8.127分钟，87为598321ms；不是总入世时间）。进入服务器策略预加载后，ProRuntime$PresentationFields初始化用外层ProRuntime身份申请openBackingAccess，被真实直接调用者校验拒绝，随后自行ExitCode=-1，12:26:59.9135540Z退出；三维度保存完成但无玩家入世/FPS/武器结果。crash报告与JFR/线程保留，12:30:42Z恢复原实例和4192世界。修正为该内部类提交自身Class，Agent鉴权不放宽；预加载提前到Mod构造期，避免未来初始化错误等出生区生成后才暴露。preload-threads.txt此前是在出生区生成，不证明策略预加载已经成功。

90 失败已闭环：Core3104AB60 / ABI105，PID4756，完整备份570.11MiB、692文件、0失败。预加载提前后在Forge CONSTRUCT暴露RecoverySources$FileMutationAccess的旧循环依赖：静态初始化登记内部受控Method要求guards已发布，违背先预加载后回调的顺序。实际CONTROL_OWNER拒绝，未入世界；保留加载JFR/失败堆栈后结束唯一失败测试进程，原实例及4192世界于12:48:03Z恢复。没有用skip该内部类或提前发布空策略绕过。

最终结构修复：ControlBridge.preparePolicyOwner只接受NativeControl已由真实Agent/ProMod绑定的ProRuntime Class及真实直接调用者，绑定内部元数据登记角色而不发布Guards回调；RuntimeClasses在类初始化前经ProRuntime固定入口完成该角色准备，registerControlObjects在无Guards时仅接受该精确预备角色。正式策略发布仍要求同一owner，外部冒充在相关Native回归中被拒绝。当前所有自身运行时类仍初始化，FileMutationAccess的控制登记没有删除或延迟到热路径。

同时修正源码调用链确认的工作线程问题：block/stored来自区块网格工作线程，不能调用要求客户端主线程的ClientPresence.context。由客户端真实策略发布volatile视觉世界索引，工作线程只读当前世界/连接/交接状态；无冻结直接走原tessellator，活动规则及晚到网格重建仍保留。uploaded/draw继续在原渲染线程核对实际视觉策略。独立工作线程的空规则查询已回归，不启动Minecraft上下文。build-visual-worker91.log整体成功1m2s；合并身份准备结构后的build-prepared-owner91.log最终jar+实际Native回归成功1m3s（22 tasks，13 executed/9 up-to-date），Core/Bootstrap实际重编，Agent复用本批真实扩容边界修正版，Control/Storage Native复用ABI105。未把独立检查当作实机通过。

91 于2026-10-27T12:49Z启动，PID31976；完整备份569.86MiB、694文件、0失败，同一26个第三方文件、82项参数与4192世界。最终已登录且用户确认地形显示但仍很卡，随后GPU排序布局异常崩溃、自行ExitCode=-1；实际保存世界4252。完整结果及后续修复见顶部92修订记录，此处不再记为进行中。

## 2026-10-27：86 地形显示但 0 FPS；ABI 105 补丁完成，按用户要求暂停

起始与结束 HEAD 均为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d，原有未提交修改保留，未提交或推送。用户在第 86 次之后要求“写完停一下”：当前代码与相关独立回归已收尾，不启动第 87 次。最终工作区候选 Core 为 D269A24ECC15788D2AC40139BB5722DB0F6FD5A93C78D8396D773B5326F047F3，Native ABI 105；Prelaunch 仍为 A1756AADC4521DE9CA4E705F02A7D66D7BD68E5B8B2672C8D261A4048CF90112。distribution/v1.5-whole-mod 已更新最终候选，它没有部署或跑实机。

第 86 次经用户重新明确授权后实际执行，测试的是上一候选 837A9F692BEE2DECE6C21C94D4B5B0604569D020B10B2170F591420500F252A9 / ABI 104，不是上述 ABI 105 候选。完整备份 569.13 MiB / 685 文件 / 0 失败；使用与 80/84 相同的 26 个第三方文件和 82 项启动参数，原启动文件一致，世界 Time/DayTime 从 3889 开始。PID 203692 于 2026-10-27T06:44:19.6639589Z 启动，07:06:40.071Z 服务器记录玩家加入，间隔 1340.407 秒（22.340 分钟）。这不是可玩入世时间：用户随后明确仍在加载或黑屏；07:13:57.4196855Z 记录用户确认“有地形了，但是很卡”，此时从启动约 1777.756 秒（29.629 分钟，含反馈与记录延迟）。用户最终报告 F3 为 0 FPS，性能明确失败。早先“进度条一直动，之前动一下卡一下”的加载响应改善不能替代世界内性能通过。

出生区日志耗时 568971 ms（9.483 分钟），80 为 611943 ms（10.199 分钟），84 为 1092367 ms（18.206 分钟）；本阶段记录比 80 少约 7.0%、比 84 少约 47.9%。历史 80/84 的入世记录为 31.278/48.435 分钟，但其记录点与本次用户确认地形的时间不完全一致，不能直接宣称等工作量总加速比例。本次仍有账号 401、密钥获取与 Forge 更新网络超时；机器负载、存档进度和采样时点也不是严格控制实验。

五段各 30 秒 JFR 已保留：load86、resource86、region86、play86、world86。play86 实际发生在服务器登录后、用户尚未看到地形的加载阶段；只有 world86 在用户确认地形后采集。load/resource/region/world 的分配样本分别为 4027/7847/7829/3122，执行样本为 79/156/27/175。world86 的 175 个执行样本中，169 个包含 BackingBridge.intStore，108 个包含 arrayMutation / fieldMutationReceiptRequired；分配样本中 1344 个包含 ClientWorldVisuals.block，1777 个包含 TaskBridge.invokeEquipmentOriginal，1239 个包含 ResourceBridge.bufferOperation。含栈计数可重叠，不是独占 CPU 时间、分配字节或 FPS。84 的 play84 有 124 个执行样本，其中 89 个包含 beginFieldMutation；不能把不同阶段热点减少等同于用户体验达标。完整流式摘要在本地 attempt-86/jfr-phases.jsonl。

86 正常关闭后，三个维度于 07:20:14Z 保存完成，进程于 07:21:47.1948008Z 自行退出 0，未强杀；Time/DayTime 3889→4192。stderr 有 1 条 RONOVA_PRO_UNSETTLED_SHUTDOWN java.io.IOException: TASK_RETIREMENT_EVENTS_PENDING；TASK_CAPTURE_PUBLICATION_IN_FLIGHT、NATIVE_TLS_REFUSED、NATIVE_MEMORY_RELEASE_BUSY、ACTUAL_ORIGIN_WRITER_REQUIRED、ACTUAL_NATIVE_UNSAFE_MUTATION_REQUIRED 各为 0。退出码 0 和存档推进不覆盖未排空事件错误，不能记为完全干净退出。原 Mod 目录与启动配置已于 07:26:18Z 恢复，实例 Core 为 6869B7EE2388E59FEDD1CFEDEEA16B2046AFADCD9954068C9F77E87EA472F83D，完成保存后的世界保留，全部测试文件留在本地 attempt-86。

依据真实登录/世界线程栈，后续补丁继续收敛同一授权调用链：MemoryUtil.wrap / MemoryStack.callocInt 在构造顶点 Buffer 时，通过 Unsafe 修改堆内 Buffer 基础字段，仍进入 fieldRangeAllowed 与 BufferWrapWriter 全栈检查。ABI 105 在 native/unsafe_scalars.c 从实际 Buffer/ByteBuffer 类建立 primitive 字段 offset/宽度目录；Java 适配器只筛选实际 JDK 直接 Buffer，Native 在真实 VM binding 内匹配准确字段，并一次读取当前受控身份、SourceMap/Backing 登记及 Buffer 限制。控制对象、共享载体、引用字段、跨字段/未匹配范围保留原路径。这里仍有一次轻量 Native→Java 资格查询，不宣称完全零跨层调用；消除的是重复整栈查询与收据。没有缓存权限或负面来源结论。

BufferBoundary 将实际 putFloat→scope() 也纳入既有直接 Buffer 快路，仍执行原 JDK scope、边界和只读检查；此前 ix/nextPutIndex/checkIndex/position/limit 等路径继续保留。ForkJoinTask[] 与 WorkQueue[] 加入精确 Native 内部数组分派，安装时核对实际数组 base/scale，执行时检查真实数组元素位置与已登记 JDK 调用方法；外部调用继续走原路径。普通 primitive 数组逐元素守卫范围未调整。纯读取旧收据接口已退出，通用写入/复制、受限目标和异常恢复职责继续保留。

去掉旧重路径后，它曾隐式预加载的 ResourceBridge 内部类首次加载落入自身文件/Buffer 钩子，独立 JVM 暴露 Key/BufferEntry ClassCircularityError。已把该钩子族内部类的解析明确放在安装前，静态初始化仍按原明确顺序执行；同时删除 Java 前置层重复资格查询，让 Native 在已有防重入作用域中单次核对。初始化失败日志保留，不作为通过记录。Native 只读目录使用 release/acquire 发布，已发布后保持不可变，避免并发线程读取半初始化字段表。最终统一 jar + 真实 Native 热路径检查成功 36s，22 tasks：11 executed、11 up-to-date；本批 Java Bootstrap/Agent 与 Control Native 实际重编，末次 Control Native 重编、Java 生产类复用同批产物；Java Core、Prelaunch、Storage Native 复用。原 PCL writer intrinsic 参数及早期 Native Agent 下的独立检查再次通过，新增覆盖真实 DirectByteBuffer limit 字段 Unsafe 写入、原边界/只读异常、ForkJoin 线程交接与既有外部入口拒绝。日志为 build-scalar87-final.log、check-scalars-pcl-flags87-final.log。未把这些独立检查写成实机通过。

当前按用户要求停止：第 87 次未部署、未启动，没有 ABI 105 的实机或性能结果。剩余高价值问题是 world86 中占主导的普通 int[] 逐元素守卫及 ClientWorldVisuals.block 调用链，以及退出的 TASK_RETIREMENT_EVENTS_PENDING；本轮未继续扩展这些路径。恢复工作时先从这一实际现场和最终候选出发，不重做全仓审计，不沿用 ABI 104 或旧 JAR 的运行结果为 ABI 105 背书。

## 2026-10-27：按明确授权收缩标量防护；85 中断，修正候选待 86 实机授权

起始及结束 HEAD 均为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d，保留全部原有未提交修改与历史记录。本批未提交、未推送。最终 Core 为 837A9F692BEE2DECE6C21C94D4B5B0604569D020B10B2170F591420500F252A9，Prelaunch 为 A1756AADC4521DE9CA4E705F02A7D66D7BD68E5B8B2672C8D261A4048CF90112，Native ABI 104；distribution/v1.5-whole-mod 已更新最终候选，游戏实例未部署该最终候选。

用户本轮明确授权四项性能/极端防护取舍：取消普通堆外纯读取的查栈与收据；无停止或不受限 Buffer 的标量写入不逐次追溯来源；JDK 并发原语操作自身状态走精确快路；普通值对象 primitive 字段在无关联受控状态时短路。此次不是声称完全无能力变化：纯 scalar 读取不再保有来源/停止/在途收据，已启用的 getter intrinsic 可绕过 Native getter binding，不能承诺所有极端控制地址读取都被拦截。实际进入 Native 的读取仍有 C 控制范围检查。范围目录查询保留当前实现，不宣称所有 Native 查询均为 O(1)。

UnsafeWriteBoundary / InternalUnsafeBoundary 移除纯 getter 的 Java 插桩；删除 beginUnsafeRead、beginHandleRead、专用 memoryUnsafeReadBegin/End 的 Java/JNI 注册、实现和相关收据状态。MemoryWriteScope 收敛为写入与复制路径；复制、批量操作和通用收据关闭职责保留。普通 raw scalar 写入在 Native 叶子直接做 C 控制范围检查；有停止策略时重新核对实际 Buffer 范围及当前来源，受限或未知来源的已登记 Buffer 保留原路径。原生控制表写入、释放和重分配并未整体放行。

新增 native/unsafe_scalars.c 与 Bootstrap ScalarPolicy。JDK 候选的 Java 适配器不再先做 fieldRangeAllowed/querySources/unsafeMutationBoundary0 往返，实际 Native 叶子核对已安装 VM binding、真实字段 offset/宽度/引用种类、实际 JDK 调用方法及有限适配器前缀。字段布局和方法身份在安装时从实际 bootstrap 类解析一次；遇到未匹配字段、外部调用者或未覆盖路径回到原检查。覆盖 AQS/AQLS、ReentrantLock、ConcurrentHashMap、CompletableFuture、ForkJoinPool/Task 的已列明内部类及实际 CHM Node 数组元素。外部对 JDK 对象的操作仍经过原 Native 策略，不以传入的类名或对象类型直接授予写入权限。此快路仍做有界 Native 调用者核对，不宣称恢复了 JIT writer intrinsic。

ControlBridge / FieldWriteBoundary 对 Vec3i、BlockPos/MutableBlockPos、Vec2/Vec3 与 JOML 明确数学类型的 primitive 字段，在装箱及 beginFieldMutation 前查询当前受控身份、敏感字段登记和停止/保护归属。引用字段、静态字段、未知类型、晚到登记及相关受控目标保留原路径。未按渲染、GUI 或粒子包整体跳过防护。

第 85 次资源现场发现 DirectByteBuffer.putFloat 的内部 ix 仍调用 bufferOperation 并逐标量爬栈，故补齐 BufferBoundary 调用链：对符合当前规则的直接 Buffer，get/put、ix、nextGetIndex/nextPutIndex、checkIndex/checkScope 和 position/limit/capacity/mark/reset/clear/flip/rewind 等元数据辅助方法，均在参数数组与装箱前判断。原 JDK 方法体、越界、只读、scope 检查仍执行；视图构造、复制和批量操作继续处理。未把一次空来源结论保存在 Buffer 上。两处初版初始化失败已定点修复：不能从插桩入口调用被插桩的 isDirect；ScalarPolicy 的 ClassValue 在安装 Unsafe 钩子前预加载，未完成初始化时走原检查。

Java Bootstrap/Agent 和 Control Native 本轮实际编译，Java Core、Prelaunch、Storage Native 复用。ABI 104 最终第一批 jar 构建成功 16s；补齐辅助链后的统一 jar + Native 热路径检查成功 32s，22 tasks：14 executed、8 up-to-date，Control Native 复用同 ABI 104 产物。新 C 文件已列入 Gradle 输入，避免增量构建遗漏。元数据回归通过普通向量短路、停止归属、晚到敏感字段登记；真实 premain Native 回归通过 VarHandle、JDK 锁/CHM/CF、直接 Buffer 的索引/位置、越界/只读异常、raw 读写和外部伪造入口拒绝。使用 Attempt 84 原 writer intrinsic 参数及早期 Native Agent 的检查同样通过。既有控制地址夹具通过控制表读写、释放/重分配拒绝与普通地址生命周期；它不证明 JIT intrinsic 读取覆盖。相关本地日志：build-scalar86.log、check-scalar-values85.log、check-scalars-pcl-flags86.log、check-scalars-control-ranges85-final.log。

第 85 次先备份 568.78 MiB / 683 文件 / 0 失败，部署中间候选 A159E5E2C1432E10C5DD9CD8C73059142B8299CF0393AA7B6BD0DFE729ECD082，使用与 80/84 相同的 26 个第三方文件、82 项参数和 Time/DayTime 3889 世界。PID 172808 于 2026-10-27T06:21:20.1349340Z 启动，约 706.471 秒后开始 Preparing start region；仅该阶段到达时间约 11.8 分钟，不是入世耗时。日志有账号 401、密钥及 Forge 版本查询网络超时，属于对比干扰。load85/resource85/region85 的 30 秒 JFR 和真实线程栈已保留。85 没有完成入世/FPS/正常保存退出验证；不得据此与 80 的 31.278 分钟、84 的 48.435 分钟总入世时间直接比较。

确认 ix 遗漏后两次正常关闭未被世界生成循环及时处理，最终于 06:38:33.2278403Z 强制结束该测试进程，实际 ExitCode=1，明确记为中断。已把中断世界、mods-used、启动目录和日志保留到 attempt-85；从完整备份恢复原 Mod/启动目录及 Time/DayTime 3889 世界，未删除原件。第 86 次又完成 569.13 MiB / 685 文件 / 0 失败完整备份。最终候选本地构建/回归已完成，但第 86 次的实例替换和启动被自动审批拒绝两次：审批认为先前持续测试授权不足以覆盖第 85 次中断后的新一轮。未绕过拒绝，attempt-86 尚未创建，最终候选未部署、没有第 86 次运行结果。当前阻断是重新获得第 86 次明确授权；下一最高价值工作包为完成修正候选的同环境入世、FPS、JFR 与正常保存退出对比。

## 2026-10-26：raw 读写收口、TLS 分锁与 JNI 退役；ABI 103 候选，实机暂停

本批起始及结束 HEAD 均为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d。继续保留 Attempt 84、IDE 配置及既有未提交修改，两份状态页历史不删；未提交、未推送。最终 Core 为 FBD4098DF127A3A058BF5A84DF5B800204FD68D34B6BB6557943B9C8D2A8CB44，Prelaunch 仍为 A1756AADC4521DE9CA4E705F02A7D66D7BD68E5B8B2672C8D261A4048CF90112，Native ABI 103；候选已更新至工作区 distribution/v1.5-whole-mod。

ControlBridge / NativeControl / native/memory.c、unsafe.c、bindings.c 接齐专用 raw getter 收据入口与退出，合并原 requireUnsafeWriter 与 generic rawMemoryCaller 的重复认证。保留真实 Unsafe 类和 Native 前缀校验、当前来源/停止检查、控制地址 fence、在途标记及异常关闭；getter intrinsic 不保证进入 Native binding，因此外层收据仍保留。真实 Native scalar 写入沿用原三帧入口和六帧建收据鉴权，完成时在 C 中核对收据实际类、单调 token、当前线程、LIFO 作用域、关闭状态及读写种类，JNI 读取后复核防 ABA，先弹出 Native scope 再发布 Java closed；保留原异常。仅精确匹配的 scalar scope 可在拒绝或异常时取消，heap/copy/Buffer 走原路径。未保留不能排除外部 JVMTI 回调借用的一次性凭据捷径，也没有恢复全局 writer intrinsic。

针对 84 的 private_gate / jni_retire BUSY，native/jni_tables.c 将 JNI 范围节点移入独立生命周期记录。线程结束先退出实际 JNI 目录，再在 private 共享锁内标记范围退休并排入待回收链；区间查询忽略已退休节点，但节点内存继续存活。后续已有 JNI 登记独占事务最多回收一个记录并复用。删除线程退役单独争取 private 独占写锁的旧路径，避免范围节点悬挂于已释放 NativeThread；空未发布记录直接回收。仍读取实际 JNI 表指针和内容，不改成仅靠事件的授权索引。其他 private 写事务仍可能产生 BUSY，本轮未证明实机异常已消失。

native/host_tls.c 在既有精确索引上完成 records、thread、fiber、slot 分锁；已知活动槽的普通 get/set 走 records 共享锁及真实上下文叶锁，分配/释放、未知状态、回调、墓碑、复用和回收保持独占生命周期事务。租约引用原子维护，原 OS 调用不持记录锁，返回后核对同一代次，保留 NULL/LastError 和回调语义。

ExternalCodeImages 在字节、指令及异常处理器实际身份均一致时复用冻结内容，弱身份读取免临时键及读路径队列清理；变化图像仍逐次核对真实操作数和跳转身份。CodeSourceBridge 将实际新增控制根和当前 backing 合并登记，Native heapPoliciesChanged 一次接收本批真实新增身份，仍逐个匹配在途堆调用，保留发布事务和重入处理。FrameworkBuffers.Draw 以准确 Bootstrap 类身份保护，移除逐次短命 Draw 永久登记；sources 数组与 ThreadLocal 仍受保护。ResourceBridge 删除纯结构验证器内多余 caller walk，实际来源边界仍认证调用者；producerIdentityAssigned 用同一实际帧完成织入位置和身份校验。

KnownContainers / SourceMaps 的 Vector、Stack 批量压缩仅适用于预算内每一行均有已绑定且固定的类归属，并在实际容器锁、组提交门和 moduleSubjects 锁内重新校验全部目标。DefinitionBridge 只返回已有真实绑定，RecoveryRecords 排除任务及可跟随引用等动态类型；候选 Module 数组不能伪造归属。动态、私有或未选中行继续逐行即时重新判断后删除，不跨轮缓存负面来源。已移除会在两遍之间漏掉新共享来源的通用 selected bitmap 方案。批量压缩仍完整读回幸存者、backing、尾部和大小，整体清理不宣称线性。

本轮首次统一构建 54s，22 tasks：16 executed、6 up-to-date，Java Core/Agent/Bootstrap 和 Control Native 实际编译；Prelaunch、Storage Native 复用。末次统一 jar 与真实 Native 热路径检查成功 37s，22 tasks：12 executed、10 up-to-date，Control Native 重编，Java 生产类复用前次本轮产物，最终 jar 更新成功。独立通过：元数据热路径、真实 premain Native 的 VarHandle/CAS/JDK 锁/CF/Buffer/raw-read 生命周期及外部伪造入口拒绝、冻结快照身份与晚到 backing、容器清理及动态来源变化回退、真实 TLS/FLS IAT/GPA/线程/纤程/回调（含两个真实线程各 64 次并发操作）、真实 Unsafe 控制地址读写及释放/重分配拒绝与普通地址生命周期。旧夹具移除已失效的 HeapCreate 前置断言；raw 夹具改用 C 在本线程/当前 JNIEnv 有界捕获真实控制槽，Java 继续断言受保护读取被拒绝，未新增任意地址读取权限。未修改的任务/日志检查未重复执行。日志保留于本地 build-batch85-final.log、check-image-snapshot85-final.log、check-known-container-cleanup85.log、check-host-tls85-final.log、check-host-raw85-final.log。

第 85 次仅完成测试前备份：backup-before-attempt-85 保存完整实例约 568.78 MiB（683 文件，0 失败）、原启动文件及第三方清单。用户随后明确“先不忙跑实机”，因此没有部署候选、没有启动第 85 次、没有新 JFR 或实机性能结果；原实例 Core 6869B7EE、Mod 集合和 Time/DayTime 3889 保持。源码与独立回归完成不能替代性能通过。84 的 48.435 分钟入世、个位数帧/未响应及退出 BUSY 失败记录继续有效；当前收益只有消除重复操作与锁竞争的静态依据。下一步等用户明确恢复实机后，用原对比环境验证加载、入世、保存退出、BUSY 与 JFR；未证明完整安全替代的三/六帧鉴权和动态容器回退继续保留。

## 2026-10-26：连续变换、来源游标与精确索引；84 性能失败，保存退出 0 仍有 BUSY

起始 HEAD 为 a2e3d13998f3cf0b2bffe828e2a9342cec1b555d。本批仅修改工作区，保留原有 IDE 配置及两份状态页的本地历史；未提交、未推送。当前统一候选 Core 为 E0E9225DE92E7EF902A33D5480754BABD0076ED917CEDAFDD5E6EC122B14EE90，Prelaunch 为 A1756AADC4521DE9CA4E705F02A7D66D7BD68E5B8B2672C8D261A4048CF90112，Native ABI 102 未变。

ApplicationBoundary 合并主应用、动态定义、客户端及 Fastutil 的连续内部变换；主应用由最多四次解析和写出改为一次解析、一次最终写出。Creation 使用内存树副本保留失败回退，补齐实际 maxStack/maxLocals，最终输出后登记观察位置。迁移后无人调用的旧字节数组入口已删除，独立入口和失败处理保留。ExternalCodeImages 仅为私有冻结字节快照按需缓存 canonical；字节相同快照共享缓存，公开 baseline 返回副本，可变外部树仍读取真实身份。缓存会增加随原历史生命周期保留的字节，不能视为无代价。

接入已审查的补丁中三个 Bootstrap 源码修改，没有部署下载 JAR 或覆盖下载状态文档。CodeSourceBridge 以方法名和 descriptor 索引匹配，特殊 JVM 名称保留原匹配器，登记版本及类定义变化仍失效，命中后仍读取真实 BCI 来源。ResourceBridge 在同一次 Buffer 操作内用一个真实 StackWalker 游标完成入口校验并按需采集来源；前缀、未知/部分来源、Native 并集、线程/单次作用域校验及异常收据关闭保留。ProRuntime 的 volatile 成员快照减少不变注册表的读取锁；成员变更在原锁内失效，未缓存运行策略。

Native TLS/FLS 按槽位种类、实际编号和线程/纤程身份建立候选索引；槽位代次、历史记录、租约、回调与退出链不变。动态返回函数和延迟导入增加真实指针索引，候选筛选后仍完整解码实际指令、读取当前 IAT/目标/归属。没有把 Native 控制表改为仅信任事件，没有恢复全局 intrinsic。TLS 共享生命周期锁仍保留，尚未完成真正分锁；也未新增未经精确角色和范围认证的控制器同步器免检路径。

RecoveryTasks 的 subject→Task 索引包含未资格任务、晚到及共享来源，组归并/退休时仍校验现行组与代次。KnownContainers / SourceMaps 复用已验证的删除后图像，按真实映射身份或幸存顺序续游，删除对应旧重复快照和从头资格判断；全结构及未选中内容读回仍执行，删除总体尚非线性。IntentJournal 保留每条真实线程池任务、256 队列容量及失败/关闭语义，将已有积压最多 64 条合并 force，成功后依序 ACK；部分写入失败不当作可重试的队列拒绝。

统一 jar 构建成功。Java Core、Agent、Bootstrap、Prelaunch 与 Control Native 本轮实际重编，Storage Native 复用；最后 jar 更新成功 19s，20 tasks：8 executed、12 up-to-date。9 项任务状态与热路径元数据回归通过；真实 premain/Native 回归通过，覆盖 VarHandle/CAS、JDK 锁、任务捕获、Buffer bulk/slice/异常后继续访问及外部伪造入口拒绝。合并变换的 JVM 验证、最终观察位置、失败回退及冻结快照隔离通过；既有容器清理、真实日志批量刷盘/失败/关闭/重开检查通过。独立检查不代表完整攻防或实机性能通过；TLS 同号复用、delay-IAT 专项未执行。

第 84 次在完整备份 567.75 MiB 实例后部署，沿用 Attempt 80 的第三方 Mod 文件及 82 项启动参数。实际登录观察时间为 2026-10-26T15:13:58.9889653Z，启动至入世 2906.080 秒（48.435 分钟），出生区 1092367 ms（18.206 分钟）；Attempt 80 对应为 31.278 分钟 / 611943 ms，Attempt 82 为 39.787 分钟 / 736537 ms。本次未达到提速目标，用户确认仍然个位数帧或经常未响应，服务器还记录约 259 秒落后。不能把整轮差异全部归因于某一个修改；存档进度、具体采样时点和机器负载并非严格等工作量实验。

加载、出生区、入世后三段各 30 秒 JFR 均已保存。出生区分配样本 80→84 为 7786→5135，包含 sourceFrameWalk 的样本为 1783→679；入世后为 5133→3815、550→447。这些局部计数下降没有转化为本轮总耗时改善，不能标记性能通过。窗口工具初始化报路径不存在，未获得自动截图；用户反馈及真实日志保留。

正常关闭请求后等待游戏自行保存，三个维度于 2026-10-26T15:25:52Z 保存完成，进程于 15:29:27.6880415Z 自行退出 0，未使用强制终止。Time/DayTime 3507→3889。退出尾声 stderr 有 1 条 NATIVE_MEMORY_RELEASE_BUSY：kind=30、site=private_gate、range=NONE、restricted=1、tooling=1、loader=1、writer_sample_site=jni_retire，随后 jemalloc 报 VirtualFree 失败；原调用未执行。其余五个既有退出/来源拒绝标签为 0。ExitCode 0 不能覆盖这条 Native 异常。测试前 Mod、启动目录与 Core 6869B7EE 已于 15:33:08Z 恢复，存档推进及全部现场保留在本地 attempt-84。

关闭前线程栈进一步定位到动画纹理逐像素读取：NativeImage → MemoryUtil.memGetInt → sun.misc.Unsafe.getInt → beginUnsafeRead → rawReceipt → memoryWriteBegin0 → rawMemoryCaller 整栈遍历。外层 raw-read 收据目前没有读后来源结果消费者、也不再保有 private-allocation lease，但仍承担来源/停止检查、控制地址 fence 和阻止退役的在途标记。getter intrinsic 尚未禁用，已安装 Native binding 不保证 JIT 读会进入 Native，因此不能直接删除外层。下一完整工作包应聚焦专用 raw-read 入口/退出，合并重复认证并保留上述职责。Native 一次性凭据方案还必须防止外部 JVMTI 回调读到入口局部参数后借用、嵌套重入及异常残留；当前尚未完成全部回调窗口的闭合证明，本轮保留现有鉴权。

加载首段 30 秒 JFR 已采集。80→84 的分配样本总数为 3199→3746，snapshot 含栈分配样本为 850→732，CallerClassFinder 为 221→437；这些不是分配字节量或等工作量 CPU 对比，不能据此宣称提速。84 新游标链内仍存在多层 getCallerClass 鉴权分配。资源阶段线程现场显示加载画面顶点、纹理像素及 LWJGL 分配持续进入逐次 Unsafe/Native 写检查。后续最高价值主线是合并同一已验证操作内部的重复鉴权与收据工作，并收敛快照保护登记；不以空来源、java.base 白名单或关闭底层拦截代替精确证明。

## 2026-10-26：元数据与任务创建来源聚合；82 性能失败，83 实机未完成

本批基线为 0e5168cdb2f03b952ba96ca6aa7d17d5f3da0ef8。当前源码对应 Core 7DB5A140E23E2E71062131F8BC691901A8C42FD581658F64CB21BD15BC03701A，Native ABI 102 未变；这是待完成实机验证的开发候选，不能标记为性能通过。

PlatformMetadata 只为 CodeSourceBridge 控制载体扫描缓存私有字段声明，绑定实际 Class、VM 重定义计数及 Native 声明后端状态；公开 declaredFields / declaredField 继续返回新反射对象，避免共享可变 Field 根对象。BackingBridge 缓存实际类及重定义版本对应的 setter 声明布局，并复用固定测试句柄；每次真实写入仍进入当前权限策略。RecoverySources 复用构造路径选择器及固定 NBT 类型/签名，每次仍读取真实调用帧及当前 codec 身份，没有缓存构造授权。

CodeSourceBridge.frameSources 接入按实际类投影的 CODE_METHODS 快照，用登记修订和类定义计数检测失效；命中后仍通过 Native 读取当前 BCI/定义代的行级来源。已永久登记的控制身份避免重复进入登记锁；在途发布仍加入原事务。ExternalCodeImages 仅在完整解析模式及字节完全相同时复用元数据行，实际指令/异常处理器身份仍逐次读取，没有复用可变 ASM 树的来源结论。

ControlBridge 对相同创建来源保留已有记录，避免重复集合及 OriginMap 节点重建；未知标志、调度器登记及真实来源变化仍处理。Native 来源与 Java 来源保留显式并集，包括 null 未知标记。TaskBridge 在原来源锁和 OriginStripe 写事务内批量通知实际新增来源，保留通知重入时新增的来源。

第二批将真实 JDK created/delegated 钩子的入口校验和来源读取合并到一次带隐藏帧的遍历。已检查前缀也保留在来源游标中，捕获结果仅向本次 taskRelation 传递；不跨调用缓存，不将部分/空来源提升为完整证明。实际织入点是直接调用；隐藏或反射中介不能冒用该创建入口。其他任务入口仍采用各自原有捕获路径；Buffer、Unsafe、句柄及 producer 链路的全部重复查栈尚未收敛。

统一构建成功。Java Core、Agent、Bootstrap 在本轮实际编译；最后一批 Core/Agent 复用、Bootstrap 重编。Control/Storage Native 无修改，均复用已有产物；Prelaunch 复用。末次 jar、runTaskStateChecks、runRuntimeHotPathChecks 成功 54s（23 tasks：13 executed、10 up-to-date）。旧 TaskStateCheck 依赖已删除的 ack/稳定时间窗，已迁移到现有实时义务模型，8 项任务状态用例通过；热路径检查覆盖字段结果隔离、继承 setter、晚到方法登记、Native/未知来源并集及不变来源记录。真实 premain/Native 检查成功 23s（14 tasks：5 executed、9 up-to-date），验证 VarHandle、JDK 锁、实际 CF Completion 节点登记与执行、DirectByteBuffer，以及外部伪造入口拒绝。测试不代表完整实机性能或全部攻防边界通过。

第 81 次误用了已被调整的实例 Mod 组合，因第三方必需依赖缺失退出 1，不纳入对比。第 82 次先备份，再按第 80 次备份逐文件恢复 Mod 集合；启动文件一致，仍使用 82 项参数。Core DAAC4FF3F1B78C46EE16D14957E3B750BE7D7A927A8985A34C1446A53CFCA841 实际入世约 39.787 分钟，出生区 736537 ms；第 80 次为 31.278 分钟和 611943 ms。用户确认仍个位数帧或经常未响应，性能明确失败。

82 的加载、出生区、入世后三段各 30 秒 JFR 已保留。出生区分配样本 7786→6418，但包含 sourceFrameWalk 的分配样本 1783→1712；入世后分配样本 5133→4335，包含 sourceFrameWalk 的样本 550→656。采样计数不等于分配字节、CPU 百分比或 FPS，加载采样时点及存档进度也有差异；82 还包含此前未实测的 ABI 102 改动，不能把差异全部归因于本批。加载线程现场仍有文件读取来源遍历、rawMemoryCaller，以及 SuperSteve 图像处理中的创建来源登记。

82 正常保存三个维度后于 2026-10-26T07:53:13Z 自行退出 0，未强制终止；Time/DayTime 从 3112 到 3507，仅证明存档值推进。六个既有退出/来源拒绝标签计数均为 0。原实例配置和候选 6869B7EE 已恢复，原始现场留在本地。

第二批候选 7DB5A140 已部署并启动第 83 次，但用户中断执行后仅留下启动记录和 premain 日志：无入世、JFR、正常退出码记录。同步前核对该实例已无游戏进程，恢复了测试前 Mod 集合、启动文件与候选 6869B7EE；存档仍为 Time/DayTime 3507。83 不继承 82 的实机结果。当前剩余重点是完成第二批实测，并继续沿真实 Buffer/文件/Unsafe 来源链收敛同次操作的重复查询；整 Mod 控制、晚到登记、行级来源和恢复边界保持要求。

## 2026-10-25：收敛内存写入调用者栈分配与 Buffer 空桥接；实机仍暂停

基线为已推送的 3378b5fd6e55df6eeb09a45896dda23269090e00。第 80 次仍是性能失败：入世 31.278 分钟、出生区 611943 ms，用户报告个位数帧或经常未响应；本批不以源码或独立 JVM 结果覆盖该结论，没有启动第 81 次。核对 region80.jfr 摘要：30 秒内有 7786 个 ObjectAllocationSample 和 16121 个 GCPhaseParallel 事件；分配样本计数不是分配字节占比，不能据此承诺 10～20 倍提速。

当前生产接线不能支持“ThreadLocal 为空即无 Mod 来源”的全局免检查分支：producerEnter 只接受 CREATION_SITES，CreationBoundary 的生产入口跳过构造器／类初始化；helperEnter 记录部分 JDK FJP/CF helper，ModGroupBoundary 的方法入口目前是允许性门槛，并非完整 enter/exit 来源栈。BufferOperation.sources 也可能来自未完成的递归查询，currentBufferSources 表达的是读后贡献；空值不能升级成完整来源证明。普通 JDK 调用仍可能操作受控对象，尚未登记的 FastUtil 载体也有并发登记和发布义务，因此没有引入 java.base 白名单、空线程免查、未登记 Buffer/容器免锁或启动期静默。

已替换 actualMemoryCaller 的已安装后端路径：NativeControl.memoryWriterCaller → memoryWriter0 用实时 JVMTI 栈选出第一个不属于 ControlBridge / TaskBridge 的实际类。Native 先匹配本 native 方法、包装方法和 actualMemoryCaller 的三个真实方法 ID，再逐批读取 8 帧；批大小不是深度上限，较深桥接栈继续读取。没有按 java.base 放行、没有缓存调用者或权限结论。requireUnsafeWriter / requireHandleWriter、目标归属、停止判定、范围与写入收据保持原路径；Native 尚不可用时保留原 Java 隐藏帧选择器。Java 声明、JNI 注册、Native 实现与 ABI 102 同批接齐。

已删除 CodeSourceBridge.executionBufferCall / executionBufferObserved 两个只鉴权、无状态作用的旧桥接，以及 BufferOperation.execution 和全部调用方。读后 observed 贡献、向上一操作传播、真实读写收据、STREAMS 租约与异常清理保留。currentBufferSources 在无观察来源时复用既有空数组，不再每次新建 Module[0]；这只是空容器复用，不缓存来源判断。删除路径有 attempt-80 加载线程栈依据。

统一 build-local-memory-writer-stack.log 成功 26s（20 tasks：12 executed、8 up-to-date）：Bootstrap Java、Control Native、Storage Native 实际重编；Core、Agent、Prelaunch Java 复用；jar / reobfJar 成功。Core SHA256 23CB9EB7AD2AFA32D501D47384DCD6F161A6BA5CFE735622531E4A666EFD1CF1，Control DLL 49E24C784FEDEB29D869B06B63BDEAA627F212FF14FC0BF43AA963B9DD93C295，Native ABI 102。既有 Host 完成端口比较警告保留。

复用 runRuntimeHotPathChecks 的显式 nativeMemoryWriters 模式，只启动一次独立 JVM：真实 premain / Native 后端安装成功，VarHandle CAS/getAndAdd、ReentrantLock、CompletableFuture 阶段、DirectByteBuffer 读写及外部反射伪造写入口拒绝均通过；11s、退出码 0。未启动 Minecraft、旧验收矩阵或额外性能基准；该检查不代表完整停组／卸载恢复或游戏性能通过。当前实例仍保留第 80 次候选，新产物仅更新仓库分发 JAR。

后续仍需处理完整来源查询的重复工作和 Mixin 快照／登记成本。若要用线程来源栈完全替代物理栈，必须先建立并验证实际入口覆盖、异步与 Native 来源、未知状态、晚到登记和生命周期失效；当前没有实现或声称这项覆盖。实机保持暂停。本批源码、候选和公开摘要已暂存；当前 HEAD 仍为 3378b5fd6e55df6eeb09a45896dda23269090e00，尚未提交或推送。

## 2026-10-25：第 80 次实机完成，启动与世界内性能仍失败；本批收口后暂停

第 79 次 Core 995E7289 实际入世仍耗时约 29.5 分钟（前端约 19 分钟、出生区约 517 秒），用户反馈仍为个位数帧或经常未响应，性能失败。沿用 load79.jfr / loading-threads.txt 定位的四个根因；补充 play79.jfr 有 34 个 Java 执行样本和 7 个监视器事件，样本计数不作为 FPS 或提速比例。第 79 次三个维度保存完成，17:47:43.7207211Z 自行退出码 0，没有强制终止；Time/DayTime 从 2376 推进到 2743。TASK_CAPTURE_PUBLICATION_IN_FLIGHT、RONOVA_PRO_UNSETTLED_SHUTDOWN、NATIVE_TLS_REFUSED、NATIVE_MEMORY_RELEASE_BUSY 均为 0。

OriginSources 的增删只在既有 CREATION_ORIGINS 写事务中发布私有不可变来源数组，删除每次变化时对该数组的控制目录登记和 Native 通知；空集合、单来源与重复来源走紧凑分支。OriginMap / OriginSources 内部重复的 StackWalker 调用者检查退出，外层写入口鉴权与事务保留；OriginMap 真正的桶表扩容仍发布受控 backing 并处理重入版本。

ControlPublication 保留在途 values / index / 自身身份保护，但这些短命数组不再成为永久 ControlKey，也不再触发额外 heapPolicyChanged。最多四项的批次直接做身份线性查询；大批次仍用身份索引。字段门的 ReentrantLock.sync Field 由一次初始化的静态布局复用，gate 与真实 sync 在同一批次登记后设置原 fieldGate 角色位。TaskBridge.body 无观察者时不再遍历调用栈；delegated 合并一次边界检查，仍在无观察者时维护真实任务关系。

字段路径保留原有防护决策入口。ownState 的命名空间及 nest、ResourceBridge 的固定平台布局和资源继承分类按实际 Class 复用；真实 agent / guards / Module 归属、权限与进程管道关联不缓存。FileInputStream / FileOutputStream / RandomAccessFile、Buffer、IO 与 Unmapper 子类保持原判定。FieldTracking 将原字段／常量投影版本用于同一次写操作，后置检查在权威桶更新时重新判定；控制身份、停止状态、源登记、发布目标与 Backing 变化仍实时检查。没有将负面来源结论跨调用缓存。

统一 build-local-79-hot-paths.log 成功 1m（20 tasks：14 executed、6 up-to-date）：Bootstrap、Agent Java 与 Control、Storage Native 实际重编，Core / Prelaunch Java 复用已有产物；jar / reobfJar 成功。Core SHA256 6869B7EE2388E59FEDD1CFEDEEA16B2046AFADCD9954068C9F77E87EA472F83D，Control DLL 4E7A416A9D2298D2503B9A366C2FF8F7CEE77433A8B0AA939B3A6CB4E25363EC，Native ABI 101。既有 Zig/libunwind 属性警告保留。

按本轮明确要求运行 runRuntimeHotPathChecks，21s 成功：私有来源数组及旧快照、在途保护与临时数组退出、字段门双身份角色、晚到字段／常量登记、资源子类、实时管道关联均通过。该独立 JVM 单测没有 agent / Native 拦截，不能证明实机攻防或帧率。17:49:21Z 已先完整备份再部署第 80 次（241 目录、673 文件、559.74 MB，无失败、跳过或不匹配），27 个其他 Mod 文件及状态不变，沿用 82 项参数和 Time=2743 世界。实际启动 17:49:43.1881703Z，玩家入场 18:20:59.8872015Z，总计 31.278 分钟；前端至内置服务器启动约 19 分钟，出生区日志耗时 611943 ms。对比第 79 次约 29.5 分钟和 517 秒，没有显著改善，出生区反而更慢；用户确认仍为个位数帧或经常未响应，性能明确失败。

第 80 次已正常结束：18:28:33Z 三个维度全部区块保存完成，18:30:47.4728733Z 自行退出码 0，未强制终止。Time/DayTime 从 2743 推进到 3112，仅证明保存值推进，不证明正常 TPS。TASK_CAPTURE_PUBLICATION_IN_FLIGHT、RONOVA_PRO_UNSETTLED_SHUTDOWN、NATIVE_TLS_REFUSED、NATIVE_MEMORY_RELEASE_BUSY、ACTUAL_ORIGIN_WRITER_REQUIRED、ACTUAL_NATIVE_UNSAFE_MUTATION_REQUIRED 均为 0。捕获器正常结束，没有触发 Native 转储。

本轮 load80.jfr 的 286 个 Java 执行样本中，261 个来自 Mixin 处理线程，包含 ExternalCodeImages 快照/规范化及控制目录登记；10 个监视器事件来自早期显示线程的 CodeSourceBridge.protect。约运行 8.6 分钟时退休线程累计 CPU 4.48 秒，这与第 79 次阶段不完全相同，不换算提速比例。region80.jfr 有 12 个 Java 样本、0 个监视器事件，但渲染线程仍有 memoryWriteBegin0，服务器仍有数组复制和 Unsafe/CAS 样本。play80.jfr 有 162 个 Java 样本、0 个监视器事件，服务器热点仍包括 Unsafe CAS、ControlRegistry.find、SynchronizedMap.get、ThreadLocal.set 与 DefinitionBridge.controlled；采样不是帧率测量。

当前没有构建或部署阻断，但性能目标未达成。下一个最高价值工作包是沿本次实际栈收敛 Mixin 外部代码快照/控制登记的重复工作，并减少字段策略和 Native 写入边界内同一次操作的重复查询，保持实时归属、真实权限和恢复义务。本批按用户“这次弄完停下”的要求完成同步后暂停，不自动启动第 81 次；原始现场、存档、备份与本机 IDE 修改保留在本地。

## 2026-10-25：修复第 78 次来源表 CAS 回路；第 79 次验证中

第 78 次运行 Core EBB46A18，启动内置服务器约耗时 31 分钟，出生区阶段继续严重卡顿；15:54:07Z 才观察到玩家入场。它最终把 Time/DayTime 从 2016 推进到 2376，16:23:22Z 三个维度保存完成，16:25:43.6190191Z 自行退出码 0，没有强制终止。TASK_CAPTURE_PUBLICATION_IN_FLIGHT、NATIVE_TLS_REFUSED、NATIVE_MEMORY_RELEASE_BUSY 均为 0；这些退出结果只属于本候选。性能明确失败，不能把正常退出当成性能通过。

根因是上一批引入的 ConcurrentHashMap / CopyOnWriteArraySet 与本工程拦截链相互作用：受控来源目录的 put、computeIfAbsent、addCount 等调用进入 Unsafe CAS，再进入 Native 写入许可与调用栈查询，并长时间持有 CREATION_ORIGINS。用户提供的现场同时显示，渲染线程逐字节写入时又在四个当前线程来源查询上等待该全局锁。一次实际加载阶段 30 秒 JFR 获得 19 个 Java 执行样本及 4 次监视器事件；主线程样本含 Native CAS、controlWriter0 和 memoryWriteBegin0。加载阶段样本不与第 76 次入世采样直接换算提速。

现以包内专用 OriginMap 替代三个来源目录：volatile 桶数组、不可变节点链、实际弱身份键，查询直接用 identityHashCode 和 referent == target，没有 SourceLookup 或临时 EventKey。写入仍处于原 CREATION_ORIGINS 事务，只通过普通 Java 字段/数组赋值并以 volatile 引用发布；扩容复制存活链，控制元数据发布发生重入时按版本重做，避免覆盖已完成的登记。写入口保留实际 ControlBridge / TaskBridge 调用者检查，新桶和来源数组继续登记为受控元数据。旧 CAS 容器及共享游标路径已退出。

任务及线程来源由 OriginSources 在既有写事务中发布不可变 Module[]；没有 CopyOnWriteArraySet。ThreadOrigin 将 observedStart、platformWorker、unknown、runBoundaryMissing、diagnosticReasons、retired 和来源数组作为完整 ThreadSources 快照发布，两个退休入口同步更新退休状态。四个 currentThreadSource* 查询改为直接按当前线程身份读取并以 OriginStripe 验证一致性，保留同线程重入可见性、私有 CURRENT Frame 栈遍历和每次真实 modStopped 判断，删除全局监视器与 EventKey 分配。

CodeSourceBridge.fieldRangeAllowed 的父类查询已接入 FIELD_LOOKUPS.get(type).fields().first()，移除遗漏的 CODE_FIELDS 全表扫描和监视器。原字段描述符、实际偏移/跨度、边界溢出与重叠权限检查完整保留。核心 WeakIdentityMap 同时移除原子桶数组和 AtomicLong / AtomicInteger 修订计数，改用 volatile 桶表与普通 Java 发布；读路径仍不回收队列，写入及元数据修订保持同步，避免在受控记账对象里再引入 CAS。没有缓存权限或负面来源结论。

第 76 次的其余精确修复继续保留：装备槽位无关联时省去重复查找，字段/常量 ClassValue 投影随真实登记版本更新，源发布目录活跃计数在原锁内更新；无收据二次检查只在本次操作的登记版本确实未变且类型没有动态关联时省去重复完整关联查询，实时权限与发布检查仍执行。退出的 2 秒排空包含 PUBLICATION_IN_FLIGHT；已知且无所有者的平台调用保留完整空租约；公共 TLS/FLS 的 get/set 只查询当前上下文及必要分配/回调来源，free 仍检查全局历史。

统一 build-local-origin-snapshots.log 成功 48s：核心、Bootstrap、Agent Java 实际重编；Prelaunch Java、Control Native 和 Storage Native 复用已有产物；jar / reobfJar 成功，20 tasks：13 executed、7 up-to-date。Core SHA256 995E72899CD153C79925B7ED8F1795D28597B7D1869057B68DB2ADAB48945F7A；Native 仍为 ABI 101、Control DLL 3C7EBEFBB4349FA73AE56CFADD59FF647481B7E4EDD23A705C771C9CEDD39892。旧 Java API 弃用警告保留。

16:34:48Z 已先备份再部署第 79 次（240 目录、669 文件、556.79 MB，无失败、跳过或不匹配）。27 个其他 Mod 文件及启用状态、82 项参数保持，沿用 Time=2376 世界。本候选尚在运行，快速入世、实际帧率、世界时间推进及保存退出仍待本轮确认。当前 HEAD 为 acb82f8906ee7f7af1915b1fe66e66ab76a628aa，生产修改已保留，尚未提交或推送本批。

本地现场说明：按用户确认清理了可从 JFR 重建的 11 份 JSON（约 2.08 GiB），以及第 41、60 次旧转储（约 11.05 GiB）；第 69 次转储、原始 JFR、存档、备份、候选保留。需要复读第 76 次样本时，从 play76.jfr 定向导出所需事件，不重新生成全部大体积 JSON。

## 2026-10-25：第 76 次锁争用、字段读取与退出来源边界修复；新候选验证中

第 76 次已完成：实际入世，Time/DayTime 从 1698 推进到 2016，三个维度保存完成，自行退出码 0；用户报告世界内仍严重卡顿。退出尾声 stderr 有 1 条 TASK_CAPTURE_PUBLICATION_IN_FLIGHT 和 3 条 NATIVE_TLS_REFUSED（site=controlled_slot_sources），不能称性能或 Native 退出通过。重新读取 play76.jfr：78 次 JavaMonitorEnter 中有 68 次在 objectModule 的 HashMap 监视器入口；189 个 Java 执行样本中 ReferenceQueue.reallyPoll 叶节点 29 次、StringLatin1.hashCode 13 次。这些是实际采样计数，不是提速百分比。

来源登记读取已迁移：CREATION_ORIGINS、THREAD_ORIGINS、TASK_OWNERS 改用并发身份弱键目录；线程和任务的来源集合按不可变数组发布。读取以 identityHashCode 的 64 个分区版本验证一致性，写入沿用原全局事务并在同一分区标记开始/结束；同线程重入仍能看到自己的进行中状态，其他读者不会接收一半更新的数据。objectModule、objectSources、objectSourceUnknown、creationModule 不再获取全局 CREATION_ORIGINS 监视器。删除全局共享可变 SOURCE_LOOKUP，改为调用内不可变身份探针。arrayWriteAllowed、unsafeWriteAllowed、unsafeMemoryAllowed 在本次需要保护/停止归属时只解析一次 owner；没有相关模块时省去该归属查询，真实策略与调用来源判断保留。

WeakIdentityMap 的 get / getOrDefault / containsKey 删除全部 ReferenceQueue.poll；弱键身份查询始终排除已回收对象。桶表以 volatile 引用及原子桶头发布，节点链不可变，扩容复制存活节点，删除不会破坏已有读者的链；值及删除状态可见，保留已存 null 与缺失的区别。写入和复合更新串行，size / entrySet / 写路径负责回收。相关只读热点迁移出 SynchronizedMap 包装，仍需要保护可变值内容的外层同步保留。EquipmentPolicy 在首次关联为空、未调用动态所有者逻辑时直接采用静态槽位；字段声明不在实际槽位声明集合且接收者无关联时直接返回。

CodeSourceBridge 以 ClassValue 按实际 Class 投影字段/常量元数据，保留首个匹配字段记录、加载器/隐藏类/精确类匹配和常量记录语义。注册更新原权威目录后发布新的不可变桶版本，使相关实际类投影失效；每次字段写不再锁 CODE_FIELDS / CODE_VALUES 或以 declaring.getName() 查询字符串索引。SOURCE_PUBLICATION_TARGETS 的 volatile 活跃计数在原锁内更新，空目录不再取得该锁。

字段和 Native Unsafe 的无收据路径分别完成首次完整检查和随后实时变化检查，不再无条件重跑整个静态分类。Core / Bootstrap 同批增加登记修订接线：只对没有既存装备/生命关联、且不可能是动态世界载体的固定 bootstrap 类型，在同一次操作内以当前登记版本判断是否需要再次完整查询 BackingBridge.known；任何登记在途、版本改变、策略更换、动态容器或既有关系都走完整重查。调用者权限、停止状态、源字段、控制身份、资源状态与新发布目标仍实时检查。没有跨调用的权限缓存或负面来源缓存。

退出修复：ProRuntime.stop 的既有 2 秒有界排空覆盖 PUBLICATION_IN_FLIGHT，超时仍保留退休线程与未完成报告；没有吞掉正在运行的任务。host_module_keep_sources 对未附加 Java 且来源已知、没有任何所有者的平台调用记录完整的空来源租约，与既有退出路径一致，未知或有 Mod 所有者的调用仍要求原租约。公共 TLS/FLS 槽位 get/set 仅核对分配来源（包含分配时实际 FLS 回调来源）、当前线程/纤程值及同上下文在途操作；free 仍检查整个槽位的值、历史贡献和所有操作。没有删掉历史记录、未知标记、停止来源或回收门槛。

本批第一次统一构建中核心 Java 实际编译成功，Bootstrap 因局部变量重名失败；修正该点后的 build-local-76-contention-tls-final.log 成功 47s。Bootstrap、Agent Java 和 Control Native 实际重编；核心 Java 复用本批首次编译结果，Prelaunch Java 和 Storage Native 复用已有产物；jar / reobfJar 成功，20 tasks：13 executed、7 up-to-date。Control Native ABI 保持 101。Core SHA256 EBB46A18D3A17235F24CD9858AE5DA4665BA6A2187F6BF5D23CBC6BD0C4DD9F7，Control DLL SHA256 3C7EBEFBB4349FA73AE56CFADD59FF647481B7E4EDD23A705C771C9CEDD39892。既有 Host 完成端口比较警告保留。

第 77 次在用户本条精确要求到达前已运行上一候选，因此保留其独立记录，完整修复验证顺延为第 78 次。15:00:29Z 先完整备份后部署：238 目录、664 文件、551.96 MB，无失败、跳过、不匹配；27 个其他 Mod 文件及状态、82 项参数保持，沿用 Time=2016 世界。本候选正在运行，尚无实际帧率、保存退出或新增分支通过结论。当前静态依据是减少锁争用、回收队列轮询和重复元数据查询，不宣称实际提速；后续关注世界内采样、发布排空、TLS 以及前次尾声的内存释放 BUSY。

## 2026-10-25：世界可保存退出，性能仍失败；来源查询与内存入口继续收敛

第 72 次关闭时，已到期的服务器任务预算阻止保存所需的完成任务入队执行，现场仍有 591 个区块保存等待和 3,759 个主线程待办。ServerShutdownHooks 只在服务器已停止且为服务器本线程时放行 pollTaskInternal / shouldRun 的时间预算检查，保留原等待条件、任务身份、完成和异常处理。第 73–76 次均实际玩家入世、三个维度保存完成并自行退出码 0；这些运行各有自己的候选证据，不代表后续构建自动通过。第 73 次还出现客户端绑定为空时的类解析递归：ClientPresence 全部相关调用方现先读取 Object 绑定并判空，再进入 LocalBody 策略，原有效绑定校验与失效清理保留。

第 76 次实际运行 Core 296074BB840AC813BB5A70161C92EF0B2EB677EAD8F862B2A250322EF1FE8C3E。用户明确反馈仍为个位数帧或经常未响应，性能失败。13:27:13.8442900Z 正常关闭请求，13:32:30Z 三个维度保存完成，13:34:23.3551273Z 自行退出码 0，未强制终止；Time/DayTime 从 1698 到 2016。退出时有 TASK_CAPTURE_PUBLICATION_IN_FLIGHT 和 3 条 NATIVE_TLS_REFUSED，未取得最终任务退休完成证据。捕获器在进程退出后获取句柄失败，生成的零字节文件不是有效转储；现修正既有捕获流程的退出竞态，后续失败与转储是否有效分别记录。

本批连续收敛有实际栈与分配样本支持的重复工作：BackingBridge / ResourceBridge 保留空闲布尔线程槽并统一显式活动判断，IoBridge 清空业务引用而保留空槽；ProRuntime 在既有锁内维护不可变运行时成员快照，注册和删除使其失效，三个删除入口共用 removeRuntime；EquipmentPolicy / LiveStatePolicy 的实际关联与字段检查改为索引遍历并按需构造字段集合。WeakIdentityMap 的 get、containsKey、getOrDefault 共用一次身份查询，正确区分已存 null；单次读取最多清理 16 个过期节点，写入和大小/快照查询仍完整清理。DefinitionBridge 以实际 Class 的 ClassValue<OriginSlot> 替代旧弱键表与回收队列，晚到来源绑定仍即时可见，Agent 加载接线同步迁移。

第 76 次的渲染样本仍落在纹理插值逐像素内存操作及来源查询。ControlBridge 的权威 producer 登记改为不可变弱引用身份哈希表，原整表扫描退出；回收的键保持探测链，登记时重建，保留并发发布、重入登记和 null 来源语义。来源栈查询只按上一次实际遍历量选择初始缓冲大小，仍遍历全部所需帧；StackWalker estimateDepth 是分配提示而非深度上限，没有减少来源覆盖。frameSources 已提供的声明来源不再二次查询。TaskBridge 的调用者选择改为单次迭代，并删除每次创建 ForkJoin 适配器集合的路径。

NativeUnsafe 写入、读取、复制的调用者认证集中到实际 TLS 操作与固定三个 JVMTI 帧，Java / Native ABI 同步到 101，删除对应 Java 重复栈检查。真实 Native 标量原始地址写入进一步核对完整六帧、实际 Unsafe 绑定、地址与宽度，命中后替代通用 Java 调用者选择；其他调用、读、复制和完成保留原选择器。所有来源、停止状态、受控内存范围和作用域检查继续执行。八种基础类型装箱共用经过实际 bootstrap 身份校验的类 / valueOf 元数据，仍用本次值装箱；删除热路径反复 FindClass / GetStaticMethodID，保留非基础类型原处理。没有新增 Mod 白名单，没有缓存许可或停止结论。

按用户指定只读对照了当前 SuperSteve 6.10.12f、WoZhiZhan 的 ForeverLoveSword1.20.1、apphhzpLIB / Native，以及 Embeddium Forge 1.20.1 和 ModernFix 的相关代码。SuperSteve 有 10 ms 与 500 ms 工作循环，不能据此断言其承担全部卡顿；直接对象头、VM 内存及 Instrumentation 操作也说明不能以放宽写入防护换取帧率。未安装其他 Mod、未复制参考源码。参考：[Embeddium 顶点布局与批量写入](https://github.com/FiniteReality/embeddium/blob/dee0ebde6aea8b158613cd3b8ea5cdf0c1f6a9bf/src/main/java/me/jellysquid/mods/sodium/mixin/core/render/immediate/consumer/BufferBuilderMixin.java)、[ModernFix 区块查询](https://github.com/embeddedt/ModernFix/blob/1.20/src/main/java/org/embeddedt/modernfix/common/mixin/perf/ticking_chunk_alloc/ChunkHolderMixin.java)、[ForeverLoveSword Unsafe](https://github.com/WoZhiZhan/ForeverLoveSword1.20.1/blob/b04b172344adfc8faa5d33ffc593a77a8df001f0/src/main/java/net/wzz/forever_love_sword/util/UnsafeUtil.java)、[apphhzpLIB VM 线程](https://github.com/apphhzp/apphhzpLIB/blob/ccb1da7bf00efbc021ec05142949d8ea2449cb31/src/main/java/apphhzp/lib/hotspot/runtime/Thread.java)。

最新 Java 两次构建分别成功 22s / 30s，Bootstrap 实际重编，其余 Java 与两项 Native 复用。最终 build-local-raw-memory-authority.log 成功 34s：Control Native 实际重编，Java 与 Storage Native 复用，jar / reobfJar 成功，20 tasks：10 executed、10 up-to-date。Core SHA256 DC7ACC640DA8BDE1575F41AA978110BAF053C5E0F25A8FF2D6E05A89C3C324D7，Control DLL SHA256 8B33B7352E941EDF6293477953A178CBE251D662FC71F6B4B8E9CE0C52DF4C4F。既有 Host 完成端口 ULONG 比较警告仍在。仅能静态确认减少重复查找、分配与跨语言认证，尚无实际提速结论。

第 77 次于 14:16:32Z 完成备份部署（236 目录、659 文件、548.29 MB，无失败、跳过或不匹配）。用户随后给出新的精确根因；本次在启动期间请求正常关闭，未实际入世，14:38:55.5713971Z 自行退出码 0，Time/DayTime 仍为 2016。stderr 有 2 条 NATIVE_MEMORY_RELEASE_BUSY，首次捕获时进程已退出，没有有效转储；没有本次性能结论。本次也不代表后续根因修复的验证。起始 HEAD acb82f8906ee7f7af1915b1fe66e66ab76a628aa。公开同步仅包含生产源码、摘要和对应候选；本机 IDE、私人历史笔记、账户、原始日志、转储和存档保留本地。

## 2026-10-25：第 75 次仍有严重卡顿；Mod 对照后的登记读取与 JNI 装箱收敛

第 75 次使用 Core 294C17DD408A7EB059A21F8686B350D3CAB87ADB06CCD201380A67769E638C00、Native ABI 101。实际玩家入世并显示天空/HUD，但未独立确认完整地形和稳定帧率；用户再次报告严重卡顿，日志曾记录约 194 秒落后。12:15:30Z 请求正常关闭，12:21:46Z 三个维度保存完成，12:23:42.8229698Z 自行退出码 0，未强制终止，Time/DayTime 从 1180 到 1698。数组策略失败、StackOverflowError、Unsafe 边界拒绝、内存释放 BUSY 均为 0；退出阶段另有 3 条 NATIVE_TLS_REFUSED，以及 SuperSteve SSThread.base 在客户端世界已空时的 NPE。首次捕获器未覆盖该 TLS 标签，没有本次转储；不能称整轮 Native 无异常或性能通过。

一次入世阶段 30 秒 JFR 在服务器线程获得 95 个 Java 样本，其中 37 个经过 fieldMutationReceiptRequired，27 个包含 WeakIdentityMap.reap；这些是调用栈样本，并非可直接换算的提速比例。渲染样本仍包含 NativeImage 纹理插值逐像素内存写入及内存操作登记。WeakIdentityMap 原先在每次读取时清空整个回收队列，而继承的 Map.getOrDefault 在空值/未命中时又调用 containsKey，重复查找与清理。

本批以一个 lookup 路径统一 get、containsKey 和 getOrDefault：实际空表直接返回；getOrDefault 一次查询同时区分缺失与已存 null；普通读取每次最多回收 16 个过期节点，避免一次字段检查承担整批 GC 清理。写入、删除、size 与快照仍完整清理；已回收弱键始终无法匹配现有对象，既有调用方的同步和新绑定即时可见性保持，不缓存权限或负面来源判断。旧的双查询默认实现与读路径无界清理退出，没有新增 JNI 接口。

按用户指定，对照了当前 SuperSteve 6.10.12f 的只读字节码、WoZhiZhan 的 ForeverLoveSword1.20.1、apphhzpLIB/Native 库，以及 Embeddium 1.20.1 Forge 和 ModernFix 的相关实现。SuperSteve 的 SSThread 含 10 ms 轮询及 500 ms 反射处理循环；这些重复工作会经过监测。参考仓库中的直接对象头、VM 内存和 Instrumentation 操作说明原有精确写入防护仍须保留。参考实现与本地其他版本只作为设计对照，不冒充当前实例的运行证据；没有安装这些额外 Mod，也没有复制其源码进生产代码。

参考：[Embeddium 的顶点布局与批量写入](https://github.com/FiniteReality/embeddium/blob/dee0ebde6aea8b158613cd3b8ea5cdf0c1f6a9bf/src/main/java/me/jellysquid/mods/sodium/mixin/core/render/immediate/consumer/BufferBuilderMixin.java)、[ModernFix 的区块查询分配收敛](https://github.com/embeddedt/ModernFix/blob/1.20/src/main/java/org/embeddedt/modernfix/common/mixin/perf/ticking_chunk_alloc/ChunkHolderMixin.java)、[ForeverLoveSword 的 Unsafe 操作](https://github.com/WoZhiZhan/ForeverLoveSword1.20.1/blob/b04b172344adfc8faa5d33ffc593a77a8df001f0/src/main/java/net/wzz/forever_love_sword/util/UnsafeUtil.java)、[apphhzpLIB 的 VM 线程访问](https://github.com/apphhzp/apphhzpLIB/blob/ccb1da7bf00efbc021ec05142949d8ea2449cb31/src/main/java/apphhzp/lib/hotspot/runtime/Thread.java)。采用其中按实际变化维护布局、减少热路径重复查询与分配的思路，当前直接修改仍以本次 Ronova 栈证据为依据。

继续核对 Native 热路径发现，公共 native_box 在 JNI 字段、数组、堆与 Unsafe 调用中仍逐次 FindClass/GetStaticMethodID。现集中维护八种基础包装类型的实际 bootstrap 类和 valueOf 方法 ID；首次使用完成身份校验后原子发布，竞争失败的候选立即释放，之后仍用本次实际值调用当前方法。只保留固定类/方法元数据，不缓存装箱值、来源或许可；非基础类型合同保留原动态解析路径。所有既有调用方共用该实现，没有新增 JNI 接口，ABI 仍为 101。

统一 build-local-weak-identity-reads.log 成功 37s：核心 Java 实际重编，其余 Java 与两项 Native 复用；20 tasks：9 executed、11 up-to-date。中间 Core 64200D14BC083444E537DF86AAF540F873502D66A64BDFCB2CE7A81F1AA43692 未部署。最终 build-local-native-box-metadata.log 成功 26s：Control Native 实际重编，Java 与 Storage Native 复用，jar/reobfJar 成功；20 tasks：10 executed、10 up-to-date。最终 Core SHA256 296074BB840AC813BB5A70161C92EF0B2EB677EAD8F862B2A250322EF1FE8C3E，Control DLL SHA256 F769821F3D87EF160C669DBFFAA9CCF9A57EB4F2CDEE9E4CFCD2398F3745CCF4。

12:45:17Z 已完整备份并部署第 76 次（234 目录、654 文件、543.32 MB，无失败/跳过/不匹配）；27 个其他 Mod 文件及启用状态、82 项参数保持，正常显示窗口，继续 Time=1698 的已保存世界。第 76 次已启动，尚无本候选稳定帧率、保存退出或实际提速结论。当前 HEAD 仍为 acb82f8906ee7f7af1915b1fe66e66ab76a628aa，本批尚未提交；IDE、本地历史记录、存档、转储及原始日志保留。


## 2026-10-25：第 74 次显示世界但卡顿；Java/Native 性能候选已构建

第 74 次已完整备份实例（231 目录、645 文件、534.37 MB，无失败/跳过/不匹配），部署 Core C5B632DCDFBAE64496B469E58EA78B735310888545834A58CEE8AE43B2FD9798，保留第 73 次正常保存的 Time/DayTime=538 存档。原 27 个其他 Mod 文件及状态、82 项参数保持。10:53:32Z 启动，11:19:12Z 观察到实际玩家入世；随后已看到雪地、树木、角色手臂和 HUD；加载期间 F3 曾显示 0 FPS、服务器约 556 ms/Tick，未取得可靠稳定帧率，不能标为性能通过。11:28:49Z 请求正常关闭（未使用 /F），11:29:40Z 开始保存，11:34:32Z 三个维度均保存完成，11:36:31.8683319Z 自行退出，退出码 0，最终 Time/DayTime=1180。该轮 stderr 的数组策略失败、StackOverflowError、内存释放 BUSY 均为 0；首次 Native 捕获器返回 GAME_TERMINAL_WITHOUT_CAPTURE，仍有 PLATFORM_PENDING，不宣称 Native 全部边界通过。

黑屏/未响应时的实际线程显示：资源加载阶段画面线程等待资源任务，普通整数原子写也进入缓冲区包装许可的调用栈检查；入世阶段画面线程在 ViewArea 创建区块 VertexBuffer，经 glGenBuffers、DirectIntBufferU.get/ix 反复进入缓冲区策略。服务器线程仍执行实体 Tick，并有持续超时记录。一次 30 秒 JFR 共 49 个 Java 执行样本、1,437 个 Native 方法样本及 8,732 个分配样本；其中 1,263 个 Native 样本是文件监视等待，不能算 CPU 热点。Java 样本与分配栈指向重复来源查询、StackWalker 和任务边界检查；这是定位依据，不是提速百分比。

新增生产修改：DefinitionBridge 以每个实际 Class 的 ClassValue<OriginSlot> 直接保存权威来源绑定，volatile 发布晚到的绑定，保留注册与批量快照的同一监视器、不可变 Origin 模块、acceptedImage 更新和实际 VM 定义验证；删除 ClassKey、弱键哈希表与退休队列，全部读取/绑定调用迁移，并补齐 Agent 辅助类型加载。ControlBridge 删除 frameSources 已覆盖的第二次声明模块查询。TaskBridge 的实际 JDK 边界检查改为非捕获的单次迭代，保留原调用者与方法集合，删除每次新建 ForkJoin 适配器集合的路径。ResourceBridge 先拒绝非 Buffer 或没有对应包装作用域的候选，再做原调用者认证，真正许可仍执行原线程、实例、布局和方法检查。IoBridge 结束作用域时保留空线程槽并清空业务引用，替代 remove 后下一次读取立即重建的路径。没有缓存许可、停止状态或调用栈结论，没有新增 JNI 接口。

统一 build-local-origin-slots.log 成功 20s：Bootstrap、Agent Java 实际重编，核心与 Prelaunch Java 复用，两项 Native 均 UP-TO-DATE 复用；jar/reobfJar 成功，20 tasks：12 executed、8 up-to-date。中间 Core 31171EA95166EE70F9048F522A9B2E859D3974D0333EFE15BA987E4ECBEBE658 未部署。

入世后的 20 秒 JFR 在画面线程捕到 NativeImage 纹理插值写入与实体锁原子写，两者都重复进入 NativeControl.unsafeMutationBoundary 的 Java 调用栈扫描。现将写入、读取、复制三个入口的调用者检查合并到既有 Native 校验：先核对当前线程的实际操作、对象身份及范围，再以固定三个 JVMTI 栈帧核对 Native 入口、对应 Java 包装和实际 ControlBridge 方法；删除三个 Java StackWalker 旧检查，未省略授权校验，失败仍拒绝。初始化一次解析实际方法 ID，并纳入既有就绪条件；Java 与 Control Native ABI 同步 101，拒绝混用旧 DLL。接口签名保持，Bootstrap 与 Native 接线同批完成。

最终 build-local-native-unsafe-authority.log 成功 56s：Bootstrap Java 与 Control Native 实际重编，核心、Agent、Prelaunch Java 及 Storage Native 复用；20 tasks：11 executed、9 up-to-date。保留既有 host_completion_port.c 的 ULONG 比较警告，本批未改相关路径。最终 Core SHA256 294C17DD408A7EB059A21F8686B350D3CAB87ADB06CCD201380A67769E638C00，已更新既有分发候选，已于 11:37:11Z 备份部署到第 75 次：232 目录、649 文件、538.54 MB，无失败/跳过/不匹配；27 个其他 Mod 文件及启用状态、82 项参数保持，正常显示窗口，沿用已保存的 Time=1180 世界。第 75 次已启动，尚无本候选的稳定帧率或保存退出结论，不能继承第 74 次结果。当前起始/结束 HEAD 仍为 acb82f8906ee7f7af1915b1fe66e66ab76a628aa，尚未提交本批；原始现场及存档只保留本地。


## 2026-10-25：第 73 次显示世界并正常保存退出；性能与客户端递归修复已构建

第 73 次先完整备份测试实例（230 目录、639 文件、528.89 MB，无失败/跳过/不匹配），部署 Core 3A3102236C446E14224FDF9B757072789D4900C26D8913BA24FF5672B00F30BE。原 27 个其他 Mod 文件及启用状态、82 项参数保持，测试客户端改为正常显示窗口。实际观察到 Forge 加载界面、出生区 100%、加入世界，以及雪地、树木、云、角色手臂和 HUD；09:59:49Z 观察到玩家入世，10:07:57Z 已看到地形。用户报告个位数帧率及频繁未响应，本次不能标为性能通过。

10:32:05Z 通过不带 /F 的进程命令请求正常关闭；10:34:38Z 进入保存，10:39:18Z 三个维度均输出 All chunks are saved / All dimensions are saved，10:41:20.8154702Z 自行退出，退出码 0，没有强制终止。最终 level.dat Time/DayTime=538。关闭任务饥饿修复获得本次正常保存退出证据；该证据只属于 3A310223 候选，后续候选仍需单独验证。

该轮 stderr 还记录了两次 RONOVA_NATIVE_ARRAY_FAILURE:BEGIN:B:0:3890:StackOverflowError，随后出现 JPLIS byte array region 断言；并非整轮 Native 无异常。记录的 1,153 层活动帧反复出现 ClientPresence.currentBody → 类加载/ASM LocalVariableNode 构造 → 字段策略 → ClientPresence.clientField → currentBody。未注册客户端实体关联的普通字段仍进入带 LocalBody 参数的策略方法，触发辅助类型解析并递归检查。现先以 Object 读取实际绑定、为空时在调用方退出，再转换 LocalBody 并调用原实时校验；字段、生命值读取/写入和分派的全部可空调用方均迁移，世界时钟、方块实体、事件取消及已有有效绑定的保护逻辑保留，没有用预加载或吞异常规避根因。

上一轮 JFR 的 BackingBridge.known 分配样本中，218 项均为 ThreadLocalMap.Entry；另有 sourceRuntimes 列表复制和装备字段查询的 ListItr 分配。本批 BackingBridge 与 ResourceBridge 的布尔回调标记改为保留线程内非活动项，删除逐次 remove/recreate 路径，所有活动判定改为显式 TRUE，嵌套和异常退出恢复原状态，仅保留布尔值、不保留业务对象。ProRuntime 在既有 SERVERS 锁内复用不可变成员快照，注册及三个删除路径在 finally 中失效；删除路径统一为 removeRuntime，运行时权限状态仍逐次读取。装备字段集合按需构造，合并重复字段查找，保留两次实时关联查询与原合并顺序；字段/范围/载体查询和运行时字段检查改用索引遍历，省去临时迭代器。没有缓存许可或来源结论，没有新增 JNI 接口。

统一 build-local-policy-allocation.log 成功 38s：核心与 Bootstrap Java 实际重编，Agent/Prelaunch Java、Control Native 和 Storage Native 复用；20 tasks：11 executed、9 up-to-date。客户端递归修复后的最终 build-local-client-policy-allocation-final.log 成功 17s：核心 Java 实际重编，其余 Java 和两项 Native 复用；jar/reobfJar 成功，20 tasks：9 executed、11 up-to-date。最终 Core SHA256 为 C5B632DCDFBAE64496B469E58EA78B735310888545834A58CEE8AE43B2FD9798，已更新既有 distribution/v1.5-whole-mod 候选，尚未据此宣称实际提速。

下一步备份并重开第 73 次已正常保存的同一存档，核对实际帧数、窗口响应、客户端递归异常及保存退出；不重置已保存世界，不改变原 Mod 与启动参数。起始 HEAD acb82f8906ee7f7af1915b1fe66e66ab76a628aa，本批尚未提交；本机 IDE 与既有本地历史笔记保留，原始日志、转储及存档不上传。

## 2026-10-25：第 72 次黑屏并卡在保存；关闭任务饥饿修复已构建

第 72 次复用了已同步 acb82f8 的 956606BC 候选。部署前完整备份测试实例（229 目录、635 文件、525.72 MB，无失败/跳过/不匹配），其他 27 个 Mod 文件及启用状态、82 项参数保持。07:58:23Z 启动，08:23:14Z 已实际入世；用户提供了持续黑屏的窗口截图。08:25:00Z 请求正常关闭，08:28:24Z 到达 Saving worlds，始终未完成；保留线程、30 秒 JFR 和压缩 Java 堆现场后，08:53:39.6093321Z 强制终止，退出码 -1，Time/DayTime=0。首次 Native 失败观察返回 GAME_TERMINAL_WITHOUT_CAPTURE，没有本次 Native 转储；这不代表画面、持续 Tick 或保存通过。失败世界已保留，并从第 72 次预备份恢复一致测试副本（16 目录、32 文件、11.01 MB，无失败/跳过/不匹配）。

堆现场显示：主世界保有 1,868 个 ChunkHolder，591 个 chunkToSave Future 未完成；区块主线程队列积压 3,759 项，其中包括 27 个 UniApply 完成任务，另有距离管理和区块回调；卸载队列及待卸载集合为空。服务器栈上的迭代器已到第 1,514 项，不能把所有字段写入误判为拒绝。MinecraftServer 的关闭循环在区块 tick 前设置短时限，随后 pollTaskInternal 和 shouldRun 又按 haveTime 决定是否处理队列；区块处理消耗时限后，关闭所依赖的完成任务得不到推进。

新增 ServerShutdownHooks 并接入既有 Mixin 配置：只在服务器不再运行、且调用线程为该服务器线程时，让上述两个任务调度检查不再受正常 Tick 时限约束。仍由原事件循环执行任务，保留任务来源、权限、完成通知与异常处理；普通运行阶段和等待退出条件保持。没有新增 JNI 接口，也没有删除待保存任务或跳过世界保存。

统一 build-local-shutdown-task-progress.log 成功 23s：核心 Java 实际重编，Bootstrap、Agent、Prelaunch Java 与两项 Native 复用；jar/reobfJar 成功，20 tasks：10 executed、10 up-to-date。Core SHA256 为 3A3102236C446E14224FDF9B757072789D4900C26D8913BA24FF5672B00F30BE。关闭修复尚待本候选实机确认，黑屏直接根因仍在核对。测试客户端改为正常显示窗口，启动参数不变；后续结合真实窗口与日志检查显示、保存退出和存档重开。原始日志、堆和存档只保留在本地。

## 2026-10-24：保存热路径元数据收敛；第 71 次失败收尾，新候选待手动复测

第 71 次运行的保存线程和一次 30 秒 JFR 采样显示：字段写入反复扫描整个 Unsafe 字段表，IoBridge.platformObject 每次遍历类型层级，保存界面的 clientMethod 反复反射查找方法。本批只收敛这些有现场依据的重复工作，不缓存许可、来源、停止状态或实际读写结果。

ControlBridge 将每代不可变 Guards 的字段列表投影为按接收者类型查询的 ClassValue，三个实际调用方迁移后删除原全表扫描；IoBridge 用 ClassValue 保存不可变平台类型分类，替代逐次父类遍历。ClientBridge 只缓存成功解析的方法，键包含别名、描述符和精确参数类型，按实际类修订号失效；失败与未知修订继续走原解析。运行时来源、生命周期、范围、发布和控制检查保留。RecoveryAgent 补齐 UnsafeFields、MethodKey、MethodHint 的启动加载接线，未新增 JNI 接口。静态上减少这些热路径重复查找，未测量实际提速。

统一构建 build-local-receiver-metadata.log 成功 42s：Bootstrap 与 Agent Java 实际重编，核心与 Prelaunch Java、Control Native 与 Storage Native 复用已有产物；jar/reobfJar 成功，20 tasks：12 executed、8 up-to-date。当前 Core SHA256 为 956606BC729B8035E6E0249A2EE2460462EF561BC04D9870B78E75059E7E7AC6，Core 与 Prelaunch 候选同步到既有 distribution/v1.5-whole-mod；该新候选没有部署或运行。

第 71 次实际运行的仍是 60466FC3 候选：18:11:08Z 已实际入世，18:12:01Z 请求正常关闭，18:17:03Z 到达 Saving worlds；保存持续超过一小时未完成，19:29:23.1433819Z 强制终止，退出码 -1，最终 level.dat 的 Time/DayTime=0。CLASS_IMAGE_REJECTED、DISPATCH_DECLARATION_FAILED、FIELD_POLICY_FAILED、NATIVE_MEMORY_RELEASE_BUSY、SOURCE_FLAGS 在本次日志中均为 0；Native 捕获器此前已中断，没有第 71 次转储，不能宣称全程 Native 验证通过。线程、JFR 和失败世界保留；从第 70 次预备份恢复一致测试副本（16 目录、32 文件、11.01 MB，无失败/跳过/不匹配）。

本次源码同步包含此前连续修复且尚未提交的同一运行阻断主线：Native 内存与生命周期、JNI 元数据、字段策略重入、来源记录合并、构造拒绝和 int[] 检查，以及上述元数据收敛。仅同步相关生产源码、必要状态摘要和候选 JAR，本机 IDE 改动与可再生 bin 保留未提交；原始日志、账户信息、转储和存档不上传。起始 HEAD 为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，提交与推送按用户本轮新授权执行，结果以本轮交付为准。

用户已明确：新候选明天等其说“开始”再测，不安排自动启动。下一步是在保留原 Mod 与启动参数、先备份测试实例的前提下，核对新候选的实际入世、稳定 Tick、正常保存退出及存档重开；这些结论当前均未验证，源码同步和构建成功不视为正式发布。

## 2026-10-24：按用户明确确认清理历史原件，释放 63.22 GiB

已删除第 43、44、45、46、47、48、49、50、52、53、54、56、57、58、59 次的 15 个旧 Native 转储（49.42 GiB），以及 backup-before-attempt-42 至 69 的 28 份旧实例备份（13.80 GiB）。实际删除 67,884,945,452 字节，无失败；清理后 D 盘可用约 73.47 GiB。

保留第 41、60、69 次原始转储，以及第 41、70、71 次完整恢复备份。源码、当前候选、各次独立运行日志与匹配符号、当前实例存档和独立失败世界保留。历史条目中原始文件“完整保留”的描述以本条后续清理结果为准。本次没有提交或推送 Git。

接续按用户要求清理 C 盘：删除可由第 69 次原始现场重新生成的临时 ZIP/分卷、四个无活跃 Java 进程的 IDE 临时工作区，以及 npm、VS Code、D3D/NVIDIA 缓存，共 5,226,075,681 字节（约 4.87 GiB）。20 个被占用的图形缓存文件跳过；C 盘可用约 41.58 GiB。软件安装、账户配置与个人文件保留。

## 2026-10-24：构造拒绝临时节点根因已修复，保存 int[] 反射路径收敛；第 71 次复测中

第 70 次 D18AE6811AA838A033B25544EF45DD5FDFDB9131A917AB4E6E8DEB2303AC5602 的完整 Java 拒绝栈确认：ModGroupBoundary.entries 为构造入口的拒绝分支创建默认 MethodNode，随后 rejectConstructor → replace 对该临时节点尚为 null 的 tryCatchBlocks 调用 clear，导致 SuperSteveEntityBase 转换失败。之后 4 字节拒绝镜像、Native ClassReader 越界和分派 ClassFormatError 是连带结果。原第三方类最大方法末指令偏移仅 477，未把它误判为方法过大，也未改第三方 Mod。

ModGroupBoundary 现以 constructorRefusal 返回全新 InsnList；活动构造入口前缀与停止组的整个构造体替换共用该生成器，删除临时 MethodNode 与旧 rejectConstructor 包装路径。静态初始化体也复用 replace 清理真实方法的旧异常表、局部变量和局部变量注解，删除重复清理。ExternalCodeImages.suppressExternal 调用方仍传入真实解析方法，必要的停止组返回策略、原绑定及构造拒绝异常保持。build-local-constructor-refusal-list.log 成功 27s，Agent Java 实际重编，其他 Java 与两项 Native 复用，JAR/reobfJar 成功。

第 70 次 17:04:15Z 实际入世；完整运行到终止，FIELD_POLICY_FAILED、SOURCE_FLAGS、NATIVE_MEMORY_RELEASE_BUSY 均为 0，首次 Native 捕获器返回 GAME_TERMINAL_WITHOUT_CAPTURE，没有 Native 转储。17:07:15Z 请求正常关闭，17:10:23Z 客户端 Stopping，17:10:25Z 已到 Saving players/worlds；save-threads.txt 显示服务器仍在 PalettedContainer 区块编码的装备数组策略，渲染线程仍绘制保存界面，未据此称为死锁。保存未完成，17:37:47.9699903Z 核对身份及预备份后结束有界复现，退出码 -1，level.dat Time/DayTime 仍 0，不能算正常保存通过。原始失败/部分保存世界保留，下一次从第 70 次预备份恢复一致副本（16 目录、32 文件、11.01 MB，无失败/跳过/不匹配）。

沿实际保存栈与 PalettedContainer.m_198189_(int[], IntUnaryOperator) 的逐元素 iastore 循环，EquipmentPolicy.arrayDenied 的 int[] 路径改用真实 arraylength/iaload 判断界限与同值，去掉该路径原先必经的 Array.getLength、Array.get 和旧值装箱。值变化后仍执行 slotArrayDenied、listWriteDenied 及全部实时 carrier/link 检查；这些后续检查的旧元素分支只消费 ItemStack 身份，int[] 不可能含有它，因此无需伪造或缓存旧数值包装对象。策略未跳过实际数组存储；其他数组类型、字段比较、浮点原始 NaN/正负零比较保持。没有缓存许可或来源结论，没有测量实际提速。

最终 build-local-palette-policy-int-final.log 成功 39s：核心 Java 实际重编，Agent 复用已完成构造修复的产物，Bootstrap/Prelaunch Java、Control Native 与 Storage Native 复用，JAR/reobfJar 成功，20 tasks：9 executed、11 up-to-date。Core SHA256 60466FC3BB77F2FD544287191139260C3397536C91783494DB63DE93C3F333E1。第 71 次先完整备份实例 229 目录、634 文件、525.71 MB，无失败/跳过/不匹配，再部署该候选；其他 27 个 Mod 文件与启用状态、82 项参数保持。PID 88552，首次 Native 失败捕获和入世观察已启动，尚无本次分派、入世或保存退出通过结论。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d 不变，保留工作区修改，无 commit/push。

## 2026-10-24：第 69 次捕获 8 KiB 私有分配争用及分派拒绝类；第 70 次已部署复测

第 69 次 Core 694F6D7AEF01FD83C30D98FCE9752B4641279727A9D31C84C558AEC977704C13 于 16:24:23Z 实际 PlayerList 入世。FIELD_POLICY_FAILED、SOURCE_FLAGS 均为 0；NATIVE_MEMORY_RELEASE_BUSY 1 次，分派声明 ClassFormatError 仍为 1。新日志定位到 plz/lizi/supersteve/entity/SuperSteveEntityBase：Native 最终处理收到的已经是 Java 拒绝分支返回的 4 字节镜像，后续 ClassReader 越界是次生异常，原 Java 转换原因尚待第 70 次完整日志，不将类名定位冒充根因解决。

首次 Native 失败转储 native-first-failure.dmp（4971544467 字节）完整保留。通过系统 DbgEng 对该转储离线查看匹配候选 PDB：释放调用来自 nvoglv64；采样 writer=74744、crt_allocate、8352 字节，而转储时该线程已离开分配，private writer=0，不能把采样当作持续持锁者。NativePrivateHeader=160 字节、NativeThread=24488 字节，确认载荷为 8192 字节；私有池 ready=2，已用 slab=130/256，非池耗尽。原普通池仅覆盖到 2048 字节，因此该请求落入持全局私有目录写锁的 CRT 路径，受限 loader/tooling 释放无法取得共享锁，尚未调用原 API 即返回 BUSY。

private_memory.c 复用现有池，把普通 2 次幂分桶覆盖到 8 KiB，桶数与最大容量由同一常量导出，保留精确 NativeThread 桶。64 MiB 已保护保留区、按需页提交、独立 leaf gate、SList 复用、地址/槽身份校验、清零和 realloc/free 接线均沿用；没有增加第二套池，也没有扩大已保留范围。大请求、容量/准备/提交失败的原受保护 CRT 回退与受限等待仍保留，不宣称消除所有未来 BUSY。统一 build-local-private-control-8k.log 成功 42s：Control Native 实际重编，Java 与 Storage Native 复用，JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date；原 host_completion_port.c:355 警告保留。最终 Core SHA256 D18AE6811AA838A033B25544EF45DD5FDFDB9131A917AB4E6E8DEB2303AC5602，包含上一批分派枚举收敛和 Java 必需镜像拒绝的原异常输出。未测量实际提速。

第 69 次 16:26:43Z 请求正常关闭，close-threads.txt 中渲染线程处理区块包/来源目录竞争，未处在第 68 次的元数据 MethodHandle 自更新循环；这一个现场不能证明整次关闭通过。超过 5 分钟仍未退出，核对 PID/Java/启动时间/预备份后强制停止，16:32:47.8799654Z 退出码 -1；最后 level.dat Time/DayTime=0，没有正常保存通过。完整失败世界保留，再制作独立测试副本（16 目录、32 文件、11.01 MB，无失败/跳过/不匹配）。

第 70 次先完整备份实例 228 目录、632 文件、525.02 MB，无失败/跳过/不匹配，再部署 D18AE681 候选；其他 27 个 Mod 文件、启用状态、82 项参数保持，PID 84596，premain 已完成，首次 Native 失败捕获与实际入世观察运行中。待定位 SuperSteveEntityBase 的原始拒绝原因及核对 Native 争用，未标记完成。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d 保持，原工作区修改保留，无 commit/push。

## 2026-10-24：第 68 次真实入世后关闭卡住；VM 元数据调用改为 JNI，已构建并部署第 69 次

第 68 次 Core 86D78243FD7AB880678080FE1671E3BB399412EE68526D7AF5F57A4D7E977B38 已观察到 Scorn626 的 PlayerList 实际入世；本次 stderr 中 FIELD_POLICY_FAILED 和 Native SOURCE_FLAGS 均为 0，分派声明 ClassFormatError 仍为 1。14:56:32Z 请求正常关闭后，close-threads.txt 显示 Render thread 卡在 PlatformMetadata.declaredMethods → MethodHandle.maybeCustomize/updateForm → 受保护 form 的 CAS 更新；服务器仍有 Tick 活动，不能据此称为全局死锁。等待超过 45 分钟仍未退出，核对测试 PID 80092、Java 路径、启动时间和预备份后强制结束，15:45:41.3804997Z 退出码 -1。最后 level.dat Time/DayTime=0，没有正常保存通过结论。完整失败世界保留于 attempt-68/failed-world-db704cc，再制作独立测试副本（14 目录、29 文件、10.99 MB，无失败/跳过/不匹配）。

本批运行阶段的 fields/methods/constructors 枚举统一经 NativeControl 的三个 JNI 入口，调用原 Class.getDeclared*0(false) 的精确 jmethodID；仅保留 Class.class 和入口 ID，不保存目标类或返回数组。Native 核对真实控制类、PlatformMetadata 实际调用方和目标 Class，退出调用方检查的 control 作用域后再执行原 Class Native，保留既有绑定、停止来源检查与实时 VM 元数据。运行阶段退出受保护 MethodHandle 自更新路径；Native 接管前的必要启动路径及其原保护仍保留，没有泛化放行 MethodHandle 内部写入。Java 声明、JNI 实现和 ABI 99→100 同批接线。Agent 与 Native 最终类镜像失败分支补齐实际类名/异常诊断，Native 打印后恢复原待决异常，不改变原拒绝结果；用于定位仍在发生的分派声明失败。

统一 build-local-native-declarations.log 成功 1m 5s：Bootstrap/Agent Java、Control Native 实际重编，核心/Prelaunch Java 与 Storage Native 复用。补齐 Native 失败原因输出后的最终 build-local-native-declarations-final.log 成功 36s：Control Native 实际重编、Java 与 Storage Native 复用；JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date。原 host_completion_port.c:355 警告保留。最终 Core SHA256 694F6D7AEF01FD83C30D98FCE9752B4641279727A9D31C84C558AEC977704C13。第 69 次先完整备份实例 225 目录、627 文件、524.19 MB，无失败/跳过/不匹配，再部署并启动 PID 86992；其他 27 个 Mod 文件、启用状态、82 项参数保持。首次 Native 失败捕获和入世观察已启动，尚无本次入世/关闭通过结论。此变更影响元数据枚举路径，没有测量实际提速。起止 HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原工作区修改保留，无 commit/push。

同一分派注册链的后续批次已完成：ProRuntime.declareDispatchMethods 对每个 owner、每次安装只枚举一次实时 VM 方法列表；Entity/LivingEntity、FoodData、MobEffectInstance 和 EquipmentPolicy 的参数分派迁移到同一实现，删除旧装备 declare 函数与重复循环。装备动作仍独立处理其第四项操作语义，但同样每类每次只枚举一次；不保存跨安装元数据或权限结论，别名、描述符和登记顺序保持。Java failedTransform 的实际必需镜像拒绝补齐原异常输出，原拒绝结果不变。统一 build-local-dispatch-declaration-snapshot.log 成功 43s：核心与 Agent Java 实际重编，Bootstrap/Prelaunch Java 和两项 Native 复用，JAR/reobfJar 成功，20 tasks：11 executed、9 up-to-date。Core SHA256 F197A4BB33B48AC9B412A50FDA8CF8383681606B455FC92DFF794C45DAC478AF，尚未部署；第 69 次仍运行前述 694F6D7A 候选，不能沿用其结果作为这份新产物的通过记录。

## 2026-10-24：第 67 次首个世界 Tick 再现 reader 空字段；字段策略重入修复已构建并部署第 68 次

第 67 次 C854CEF53769FAF2E1D0E47537E2BD3E162C75F38E09B132C79B702DF4009D9B 最终不是通过：出生区域生成 100% 后，08:41:21 服务器因 PrimaryLevelData 时钟字段检查中的 FIELD_POLICY_FAILED 崩溃，根异常仍是 JarModuleReader.jar 为 null。触发链为 WorldStatePolicy.fieldDenied 首次 new Query → 类加载 → 创建/使用模块 reader；报告 0 玩家、Time/DayTime=0。随后崩溃清理保存区块，13:51:01.8240923Z 退出码 -1，没有本轮强制停止；实际 level.dat 仍 0/0，不能算入世、稳定 Tick 或正常保存。完整 stderr 来源拒绝 0，但分派声明 ClassFormatError 1。首次捕获器在进程退出前到达 90 分钟期限，没有 dump，未把观察期限当作进程结束。报告、日志、JFR 和完整失败世界均保留。

源码链进一步定位到 ControlBridge 的全线程 FIELD_POLICY_ACTIVE 布尔拒绝：外层时钟写入正在执行策略时加载 Query，reader 构造中的 jar 字段写入又经过 FieldWriteBoundary；ConstructorFlow 只跳过 super/this 初始化前的接收者写入，super 返回后的 jar 初始化仍受检查。原分支除少数 JDK 集合外一律返回 false，可把合法的依赖构造写入跳过，留下 final jar=null。此前 reader 返回后的保护与来源补齐仍承担发布/防改职责，但无法补回构造阶段被拒绝的字段初始化；保留该必要机制，没有用预加载 Query、换包或吞异常规避。

本批以实际 receiver/declaring/name 的可重入策略链替代全线程拒绝。其他对象的嵌套写入执行完整实时策略，同一待决字段的循环仍拒绝；已有普通写入的 JDK 链接器集合路径及嵌套控制对象拒绝保持。普通字段与反射/JNI 字段入口统一使用 invokeFieldPolicy，各自前置平台、控制、来源、停止和资源检查不变，反射入口不会取得集合特例。每线程复用调用节点，finally 清空 receiver/Class/name 引用并恢复深度，不缓存权限或来源结论，不为每次写入创建调用节点。Agent 启动加载名单补齐 FieldPolicyCall/FieldPolicyState。删除旧 Boolean set/remove 路径和反射入口的重复策略调用封装。实际运行效果仍待第 68 次确认，没有提速测量结论。

统一 build-local-field-policy-reentry.log 成功 40s，Bootstrap 与 Agent Java 实际重编，核心/Prelaunch Java、Control Native、Storage Native 复用；JAR/reobfJar 成功，20 tasks：12 executed、8 up-to-date。Core SHA256 86D78243FD7AB880678080FE1671E3BB399412EE68526D7AF5F57A4D7E977B38，包含此前创建来源单记录合并。第 67 次已生成区域的失败世界原样保留，再制作独立测试副本：14 目录、26 文件、10.99 MB，无失败/跳过/不匹配；副本仍 Time/DayTime=0，以此复测首次 Tick，避免再次生成同一区域。第 68 次部署前另完整备份实例 225 目录、623 文件、525.81 MB，无失败/跳过/不匹配，其他 27 个 Mod 文件及启用状态、82 项参数保持。PID 80092，premain 已完成，首次 Native 失败捕获和入世观察均已启动，尚未通过。起止 HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，无 commit/push。

## 2026-10-24：创建来源三表合一及调用方迁移完成；新 Java 候选构建，未部署

沿第 67 次真实世界生成采样中的 objectModule/HashMap 热点继续收敛：ControlBridge 原分开的 CREATION_OWNERS、CREATION_SOURCES、CREATION_UNKNOWN 已删除，改为 CREATION_ORIGINS 的单条弱身份 CreationOrigin（来源集合、持续保留的未知标记）；未知但尚无来源集合与已观察的空集合仍分别表示。objectModule/objectSources/objectSourceUnknown、创建模块查询、Native 堆来源枚举、对象组枚举、任务登记、调度器判定、线程来源和 Future 枚举均已迁移。单一所有者从同一记录导出，删除独立索引及重复扫描；原定义站点列表 CREATIONS 保持独立职责。

线程表唯一登记入口接收 Thread，因此普通对象查询跳过 THREAD_ORIGINS；来源查询键沿用一次身份哈希和 finally 恢复机制。普通非 Thread 对象的 objectModule 最多五次目录读取收敛到两次，真实记录每次重新读取，不缓存模块权限、停止或许可结论。所有相关调用方统一使用新表监视器；弱引用队列仍清理任务、线程、调度器等关联。来源通知可重入的登记路径保留通知后重新读取，未知标记不会因后续已知来源回填而消失；原 onlyAbsent、空来源和未知来源语义保留。Agent 启动预加载名单加入 CreationOrigin，避免第一笔来源登记时才加载新控制状态类。

build-local-creation-origin-record.log 成功 36s，Bootstrap Java 实际重编；补齐 Agent 加载接线后的 build-local-creation-origin-final.log 成功 27s，Agent Java 实际重编，Bootstrap/核心/Prelaunch Java、Control Native、Storage Native 复用，JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date。最终 Core SHA256 CCA89495B1771626A5B7C3B2E4A35902B6FD5FCE94C1AF7AB8A82E5ACDE84B23，未部署、未实机验证、没有实际提速结论。

第 67 次仍是 C854CEF53769FAF2E1D0E47537E2BD3E162C75F38E09B132C79B702DF4009D9B，PID 74376，08:04 本地时间世界生成 35%；旧 Native 来源拒绝/分派声明失败尚未重现，仍无玩家入世和正常保存结论。没有替换运行中的候选。当前继续观察同一测试，随后再决定新 Java 候选的必要运行验证；本批不继续扩展其他优化。起止 HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push。

## 2026-10-24：第 67 次世界生成中；查询键身份哈希收敛已构建，未部署

第 67 次仍运行 Core C854CEF53769FAF2E1D0E47537E2BD3E162C75F38E09B132C79B702DF4009D9B（PID 74376），世界出生区域生成已到 12%；此前 11 条 Native 来源拒绝及分派声明 ClassFormatError 尚未重现，未获得本次玩家入世或正常保存结论。继续同一进程，未重启、未改 Mod/参数。

针对实际世界加载缓慢，在本进程仅采集一次 30 秒 JFR，原始 world.jfr 保留在 attempt-67。113 个 ExecutionSample、1461 个 NativeMethodSample、5632 个 ObjectAllocationSample；1313 个 Native 样本为 Windows 目录通知等待，不能当成 CPU 热点。执行样本中 22 个在 objectModule 下的 HashMap.TreeNode.getTreeNode，6 个在 SourceLookup.hashCode；这些计数不是耗时或提速比例。进一步区分分配栈顶后，TaskBridge 回调下大量 Double/Long 分配属于原业务回调，未为压低采样数改写业务。字段查询的类名已由 JVM 缓存，也未另加权限或类名缓存。

ControlBridge.SourceLookup 原每次访问来源目录都重新调用 System.identityHashCode，同一次 objectModule 最多访问五张目录。现在 bind 时只计算一次身份哈希，objectSources/objectSourceUnknown/objectModule 全部迁移；finally 同时恢复上层目标与哈希，保留同一监视器、可重入语义、弱登记键、实时来源/未知/停止判断，查询键仍不进入任何登记表。旧每次 hashCode 重算路径退出，没有缓存许可结论。仅静态消除重复哈希调用，未测量实际提速。

统一 build-local-query-identity-hash.log 成功 33s：Bootstrap Java 实际重编，核心/Agent/Prelaunch Java 与两项 Native 复用；JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date。新 Core SHA256 417264E3B427A9A4EE662D004C15B4F4A5A05AECC7019FF31807B9897B82B6AA，未部署，不能沿用第 67 次作为其完整实机结果。HEAD 仍 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，无 commit/push。本批继续等待第 67 次入世/退出证据，不开启新测试。

## 2026-10-24：第 66 次有界复现结束；第 67 次已部署启动，验证 JPLIS 来源补齐

第 66 次确认来源拒绝 11 次（unsafe_allocate 10、unsafe_memory 1），首次失败捕获器未触发转储。区域生成最终观察到 4%，无 PlayerList 入世。12:07:04Z 请求正常关闭，超过 3 分钟后仍在生成；核对 PID/路径/启动时间与预备份后停止进程，监控确认 12:10:57.3700057Z 退出码 -1。线程现场 region-threads.txt 显示真实区块调度和加载界面渲染活动，没有据此声称死锁。失败世界原样保留，level.dat Time/DayTime=0；从第 66 次预备份恢复测试副本，11 目录、10 文件，无失败/跳过/不匹配。没有正常保存或物品实体构造实机通过结论。

第 67 次先完整备份 221 目录、604 文件、512.11 MB，无失败/跳过/不匹配，再部署 Core C854CEF53769FAF2E1D0E47537E2BD3E162C75F38E09B132C79B702DF4009D9B，匹配 JAR/DLL/PDB 保存至 tested-candidate。其他 27 个 Mod 文件及启用状态不变，沿用 82 项参数和恢复的 db704cc；PID 74376，premain 已完成，Native 首次失败捕获与真实入世观察运行中。07:26:07 本地时间已进入资源重载；截至该阶段，本次 stderr 未见 SOURCE_FLAGS 或 DISPATCH_DECLARATION_FAILED，旧候选的 11 次来源拒绝尚未重现。这仅是已到达阶段的运行证据，不代表完整分派覆盖或全部 Native 能力通过，07:32:36 单人服务器启动，07:35:31 开始准备主世界出生区域，07:40:20 已观察到 1% 进度；入世观察器与进程监控仍运行，尚无真实玩家入世或正常保存结论。启动日志另见 Forge 早期显示 SimpleFont.stringWidth 的空文本异常，随后继续加载；现场保留，未据此改动第三方逻辑。

本轮未新增源码或重复构建，沿用上一条已成功构建的 JPLIS 启动来源修复。起止 HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，保留原工作区修改，无 commit/push。

## 2026-10-24：第 66 次确认回调环境未知来源；JPLIS 启动来源补齐已构建，待部署

第 66 次运行 Core F61D86891C1CB6EF56405245299D33F9ECED707F8BE7C8735EE4801060CBB47A，PID 18848；完整预备份 220 目录、602 文件、512.64 MB，无失败/跳过/不匹配，原 Mod 集合、82 项参数与独立测试世界沿用。48 帧现场显示 Native 拒绝发生于 CreationBoundary.entity → Forge UnionFileSystem/ZipFileSystem → 临时 DirectByteBuffer 分配/释放，活动 JPLIS 回调 environment_unknown=1、slot_unknown=1、owners=0。当前已到 Preparing start region，尚无本次玩家入世或正常保存结论，测试仍运行。

沿实际调用链发现 native_tooling_capture 在 Java/JNI 来源尚不可查询时把未知永久并入环境与回调；已有真实 JPLIS 结构认证未补齐这一启动阶段缺口。bindings.c 现在仅在 JVMTI ONLOAD/PRIMORDIAL、无已知来源且没有其他未知原因时单独记录 startupUnknown，其他阶段或混合未知仍保留原拒绝。platform_structures.c 复用真实 InstrumentationImpl 全局引用、VM、JPLIS 环境 local-storage 回指认证，在环境 mutation 与 tooling 目录锁下核对当前 shadow/回调槽及已认证后端身份后补齐这一项。没有凭 DLL 名字、包名或空 owner 集合直接放行；已捕获来源、其他未知、停止检查、回调生命周期及原始 Native/Java 实时来源查询保留。正常环境与重转换环境均接线，旧永久混同的路径由原因明确的捕获替代。对本次现场是否足够仍需新候选运行验证。

最终 build-local-jplis-startup-sources-final.log：BUILD SUCCESSFUL in 44s，Control Native 实际重编，Java 各组件与 Storage Native 复用；JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date。本批新增格式警告已修正，原 host_completion_port.c:355 警告保留。新 Core SHA256 C854CEF53769FAF2E1D0E47537E2BD3E162C75F38E09B132C79B702DF4009D9B，未部署；不沿用第 66 次为新候选结果。变更集中于环境/回调登记及平台认证，没有实测提速结论。

用户授权的临时清理仅删除第 63/65 次可由原始 JFR 重建的 3 个 JSON 导出，共 273030973 字节；原始 JFR、日志、转储、备份、候选、失败世界及正在运行的第 66 次文件保留。HEAD 仍 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，无 commit/push。接下来结束第 66 次有界观察，再备份部署新候选核对来源拒绝和分派失败。

## 2026-10-24：本批收口；第 65 次入世后 17 Tick 崩溃，构造期准入已修复并构建，待复测

本批完成 reader 早期发布保护与来源补齐、任务关闭日志提交移出发布锁、字段名称/弱身份查询收敛，并修正本轮 MethodHandle 自更新加载回归。第 65 次运行 F3F04F4A796FFC46C10762C20C13F38B3C6E15256B2164193B9DD272EDACD7E8，11:00:11.8503334Z 观察到真实 PlayerList 入世；崩溃报告确认 1 名玩家、Time/DayTime=17。此前 JarModuleReader 空引用未在这次重现，但随后因甘蔗掉落创建 ItemEntity 时 FIELD_POLICY_FAILED 崩溃，不能算稳定运行或保存通过。

新根因是 ProRuntime.bodyModule 在 SynchedEntityData 构造写入其 owner 时调用 ItemEntity.getItem，此时 Entity.entityData 尚未赋值。现在无目标模块直接保留已有定义/创建来源；有目标时通过真实 Entity 数据字段和受原读锁保护的 getItem 访问器确认物品同步条目存在，再调用原 getItem 判断实际物品模块。没有缓存“未初始化即放行”，后续发布仍重新查询真实载荷，直接实体来源及终止主体检查不变。新增 Access.ItemBody 的 DATA_ITEM 访问器并接入现有 Mixin 配置，旧无条件读取路径退出，没有吞掉任意 getter 异常。

最新统一构建 build-local-item-construction-admission.log 成功 23s：核心 Java 与资源处理实际执行，Bootstrap/Agent/Prelaunch Java、Control Native、Storage Native 复用已成功产物；14 个 Java 警告，JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date。Control Native 已在上一完整批次实际编译成功。最新 Core SHA256 F61D86891C1CB6EF56405245299D33F9ECED707F8BE7C8735EE4801060CBB47A，包含来源查询收敛、48 帧失败诊断和本次构造期修复，未部署、未实机验证；没有性能提速结论。

第 65 次崩溃清理约 14 分钟后仍存活，最近约 8 分钟无新增日志或区域文件写入；没有据此断言死锁。为结束有界失败测试，核对身份后停止 PID 66896，监控确认退出码 -1。原报告、日志、候选、JFR、模块地址表与失败世界保留；失败世界 level.dat 仍为 Time/DayTime=0，进一步说明崩溃时的 17 Tick 尚未获得正常保存证据。随后恢复测试前副本：11 目录、10 文件，无失败/跳过/不匹配。完整 stderr：释放 BUSY/VirtualFree 错误 0，来源拒绝 11（分配 10、释放 1），ClassFormatError 分派声明失败 1；捕获器未触发 dump，未见本次任务捕获丢失或未结关闭日志，但强制终止不能证明观察器正常解绑。

用户最新目标要求有界批次并允许必要实机；本批不再扩展或自动开启下一轮。下一步是用最新候选复测物品实体生成与正常保存，随后另批定位 instrument.dll 分派中的未知来源归因和 ClassFormatError。原修改保留，起止 HEAD 均 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，无 commit/push。

## 2026-10-24：第 65 次已越过加载回归；来源读取收敛批次仅构建，未部署

第 65 次运行原 F3F04F4A796FFC46C10762C20C13F38B3C6E15256B2164193B9DD272EDACD7E8 候选，已越过第 64 次的 LevelRenderer 初始化停点并进入世界生成，最新进度 37%；尚无玩家入世、稳定 Tick、正常退出或保存通过结论。来源拒绝实际栈包含 Unsafe.freeMemory0 → DirectByteBuffer.Deallocator → ResourceBridge.BufferCleanup → Cleaner → sun.nio.ch.Util 临时缓冲区，以及 Unsafe 分配 → FileChannelImpl → ZipFileSystem 读取。12 帧仍未覆盖更外层实际 Java 分派方；不能仅凭 instrument.dll 或 JDK 包名消除未知来源。

对修改后的真实世界阶段仅采集一次 30 秒 JFR，不重跑夹具或基准。录制含 105 个 ExecutionSample、1450 个 NativeMethodSample 和 7088 个 ObjectAllocationSample；1285 个 Native 样本在 GetQueuedCompletionStatus0 等待中，不算 CPU 热点。分配样本有 919 个指向 beginFieldMutation 的临时 EventKey，真实业务链为 Vec3i/MutableBlockPos 字段更新与 SurfaceSystem 地形生成。部分 StackWalker 分配发生于 JFR 自身 EventWriter，采样数量与权重均不当作真实耗时、实际分配字节或提速比例。完整 JSON 导出膨胀至约 1.1 GB，PowerShell 解析内存不足；仅移除本轮可再生的大型导出，保留原 JFR，改用筛选 CPU 事件和流式分配摘要后完成分析，没有重新采样。

ControlBridge 将 beginFieldMutation、objectSources、objectSourceUnknown、objectModule 的纯查询统一到既有 CREATION_OWNERS 监视器内的 SourceLookup；查询对象永不进入登记表，finally 恢复重入时的上层目标。删除 objectModule(Object,EventKey) 查询重载及相关临时 EventKey 创建，全部调用方迁移；登记写入仍用原弱 EventKey，来源数组、未知/线程/任务/创建来源语义、两次实时归属读取、许可判定及实际写入门控不变。Native 失败日志追加环境和槽各自 unknown 标记、来源/producer 数量，将有界失败栈扩至 48 帧；仅诊断已发生拒绝，没有修改准入策略。

用户更新目标后按当前交付边界将后续候选保留在工作区，不再自行部署。统一 build-local-source-lookups-closure.log 成功 49s，Bootstrap Java 与 Control Native 实际重编，其余 Java 与 Storage Native 复用；原 host_completion_port.c:355 警告保留，20 tasks：11 executed、9 up-to-date，JAR/reobfJar 成功。新 Core SHA256 5B43386A7376BEF519D171EDED9EC695BA01C45A6CCDB4DCD1F2D4AFC5012876，未部署，不能用第 65 次进度作其运行结论。源码静态消除重复查询键分配，没有实测提速结论。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push；继续观察已在运行的第 65 次现场。

## 2026-10-24：第 64 次定位本轮 MethodHandle 加载回归；改用 volatile VarHandle，第 65 次运行中

第 64 次 Native 来源拒绝再次出现时保存当前进程模块地址表，实际活动回调为 instrument.dll + 0x25F0（当前 JDK java-runtime-gamma-snapshot/bin/instrument.dll），thread 72520、scopes=4、java=0；共 11 条。该事实只能识别分派后端，不能把所有来源视为可信。未据 DLL 名称放行，原未知来源拒绝保留。

加载日志 9 分钟未推进但进程仍存活并持续消耗 CPU，于同一进程用已有 jcmd Thread.print 采集 loading-threads.txt。Render thread 位于 PlatformMetadata.classRevision → MethodHandle.maybeCustomize/updateForm → Unsafe.compareAndSetBoolean/compareAndExchangeByte → 控制写许可查询路径。新加入且受保护的 getter MethodHandle 在渲染热路径触发内部可变状态更新，形成此次加载回归；不是仅凭日志静默称死锁。本轮对此回归负责，没有把第 64 次算通过。核对 PID/Java/启动时间和预备份后，于 2026-10-24T09:53:10.3418131Z 停止 PID 58488，监控确认 09:53:10.5457056Z 退出码 -1；无玩家入世、无正常保存结论。现场、匹配候选和失败世界保留，世界从该次预备份恢复：11 目录、10 文件，无失败/跳过/不匹配。

PlatformMetadata 的 classRedefinedCount 读取改为 privateLookupIn.findVarHandle + getVolatile；旧 CLASS_REVISION MethodHandle 路径删除，其余真实声明 MethodHandle 不变。既有控制对象登记、不可用时关闭可选名称提示、每次真实字段检查与重定义失效机制保留。本机 JDK 的对应 accessor 字节码确认 getVolatile 直接调用 Unsafe.getIntVolatile，没有该 getter MethodHandle 的自定义 form CAS 路径；仍需当前候选运行验证，不宣称提速。

Native 来源拒绝日志在既有回调身份记录后补充最多 12 帧实际 Java 栈，区分 JDK 分派器与实际 Transformer 调用方。只在已有拒绝分支、普通 JNI 环境且无待处理异常时执行；heap/tooling restricted、VM detach、线程结束和 loader callout 均跳过 VM 元数据查询。JNI 局部引用及 JVMTI 名称分配均释放，不改变来源/准入结论。统一 build-local-revision-varhandle-source-frames.log 成功 21s，Bootstrap Java 与 Control Native 实际重编，核心/Agent/Prelaunch Java 与 Storage Native 复用；原 ULONG 比较警告保留，20 tasks：11 executed、9 up-to-date，JAR/reobfJar 成功。

第 65 次完整预备份 219 目录、599 文件、510.21 MB，无失败/跳过/不匹配；部署 Core SHA256 F3F04F4A796FFC46C10762C20C13F38B3C6E15256B2164193B9DD272EDACD7E8，匹配 JAR/DLL/PDB 已保存。原其他 27 个 Mod 文件及启用状态、82 项参数、恢复后的 db704cc 保持。PID 66896，首次 Native 失败捕获、当前进程模块地址采集和玩家入世日志观察均已开启；尚未通过。普通沙箱对外部游戏 PID 不可见，后续进程检查使用原游戏环境并匹配路径/启动时间，不能把一次不可见误判为终止。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push。

## 2026-10-24：第 63 次首个世界 Tick 崩溃；reader 早期发布与关闭日志回调修复，第 64 次验证中

第 63 次 PID 55288 于 2026-10-24T07:07:54.3502568Z 退出码 -1，没有本轮强杀或关闭请求。02:02:09 的服务器崩溃为 FIELD_POLICY_FAILED，根异常是 JarModuleReader.jar 为 null，发生于 WorldStatePolicy.fieldDenied 第一次建立 Query 时的类加载链。报告玩家数为 0，World Time/DayTime 均 0；退出后的真实 level.dat 仍为 0/0。尾部 All chunks are saved 属于崩溃清理，不是正常退出/入世/稳定 Tick 通过。另有 ClassFormatError、TASK_CAPTURE_REQUIRES_OUTSIDE_PUBLICATION_GATE 与 TASK_OBSERVER_DETACH_UNCONFIRMED。stderr 释放 BUSY/VirtualFree 错误为 0，但含 11 条 unsafe_allocate 来源拒绝；捕获器没有触发转储。报告、日志、摘要和失败世界均保留；从本次预备份恢复世界，11 目录、10 文件，无失败/跳过/不匹配。

实际 2.1.10 依赖类字节码确认 JarModuleReader 构造器写入 final jar，open 直接读取它；没有通过换包、预加载 Query 或吞异常规避。CodeSourceBridge 原 moduleReader 只在 Module 已登记后保护 reader，未覆盖真实 open 先于层发布、reader 已进入加载器缓存的窗口。本批在已认证 open 返回时即保护真实 reader，并以弱关联暂存 reader/provider 与实际 reference 的发布关系；seed 在同一 MODULES 监视器内补齐来源再发布 Module，成功后移除暂存，引用队列回收未完成记录。已知与早期 reader/provider 共用发布逻辑，原弱身份、类卸载、provider 多来源合并与真实读取校验保留。这是已确认的源码保护缺口；本次 null 的具体写入者尚未被现场直接捕获，不能先称根因实机闭环。

RecoveryTasks.close 原在 LISTENERS 锁内 journal.append/retryRejected，IntentJournal.append 会创建 CompletableFuture 并 writer.execute，同步任务回调因此违反既有锁外来源捕获要求。关闭日志意图与事实、结果取消重试移至锁外；意图对应的观察 epoch 在重新加锁后及解绑前复核，出现新关系时保持观察并重试，原活动任务/等待者/丢失记录/真实解绑约束保留。没有接收者的事件不再制造无作用域的捕获丢失，注册不匹配原有 unknownCapture 仍保留。

此前新增的失败专用 Native 日志已编译：在 SOURCE_FLAGS 的 tooling 未知分支记录线程、真实活动回调和既有模块基址，无额外 loader/JNI 查询、锁或许可变更；build-local-source-callback-identity.log 构建 25s，Control Native 实际执行，Java/Storage 复用，原 ULONG 比较警告保留。该产物随后被本批统一构建复用。最新 build-local-reader-publication-task-close.log 成功 19s，核心 Java 与 Bootstrap Java 实际重编，Agent/Prelaunch Java 和两项 Native 复用；14 个 Java 警告，JAR/reobfJar 成功，20 tasks：11 executed、9 up-to-date。没有额外夹具或基准，未宣称实测提速。

第 64 次先完整备份 218 目录、597 文件、510.68 MB，无失败/跳过/不匹配，再部署 Core SHA256 FA3C9312451622E0949DD3C88C9BCE35F853CA4626738F0B930135AD9480B3BB，匹配 JAR/DLL/PDB 保存。原其他 27 个 Mod 文件（2 个启用第三方 JAR）与启用状态、82 项参数不变，同一恢复后的 db704cc。PID 58488，首次 Native 失败捕获与来源拒绝时模块地址表采集开启；结果待定，不沿用第 62 次释放结果。Windows 窗口查看曾返回 Computer Use app approval timed out，未获得游戏画面或据此执行窗口输入；这不影响既有日志与进程证据。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原工作区修改保留，无 commit/push。

## 2026-10-24：按真实分配采样收敛来源查询键；来源拒绝日志口径纠正

复用第 63 次已有 30 秒 JFR，读取其中 8733 条 ObjectAllocationSample；没有重新采样或额外负载。样本栈包含 DefinitionBridge.module 在来源扫描/Unsafe 许可查询中反复创建 ClassKey，以及 ControlBridge.objectModule 在 System.arraycopy 的 nativeArrayCopyRequired/BackingBridge.known 路径创建 EventKey。采样权重不作为实际分配字节或实际速度结论。

DefinitionBridge 的 ClassValue 只复用真实 Class 对应的弱身份键，全部 module/origin/acceptedImage/origins/initializing/bind 调用迁移到统一键。来源和 acceptedImage 仍逐次读取原 ConcurrentHashMap；晚绑定、批量快照监视器、弱引用队列回收、空参数原语义保留，没有缓存来源缺失或许可结果。旧每次 new ClassKey 的查询路径退出，权威登记表继续承担实时发布职责。ControlBridge.objectModule 在既有 CREATION_OWNERS 监视器内复用专用查找对象，永不插入目录；finally 恢复上层目标，覆盖重入/异常，退出最外层后不额外保留游戏对象。已有传入 EventKey 路径与所有未知/线程/任务/创建来源分支保留；singleThreadSource 接收同一查询对象，移除该热路径临时 EventKey 分配。

统一 jar 构建成功 20s，Bootstrap Java 本轮实际重编，其余 Java 和两项 Native 复用；JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date。日志 build-local-origin-lookup-keys.log；最新 Core SHA256 AFF39D2418E3516894270172E19B00E97BCDB7A0E807B8DB1BC57DE39A47B131，包含上一批字段名称提示，尚未部署。只有静态减少重复分配依据，没有实测提速或新候选实机通过结论。

纠正日志口径：native/memory.c 的 SOURCE_FLAGS 来自 unknown 且含 producer 的拒绝分支，随后拒绝实际 Unsafe 内存动作，不是普通成功日志。第 62 次完整 stderr 与第 63 次当前 stderr 各有 11 条 site=unsafe_allocate、tls=0、scopes=4、java=0，说明 tooling scope 含未知来源；即使没有未捕获异常文本，也不能称 Native 来源全通过。第 62 次退出码 0、VirtualFree/BUSY/TLS 计数为 0 的事实保留，但仅确认该次目标释放失败未复现。暂不凭 flags 推断被拒绝调用属于合法游戏还是目标 Mod，也不删除未知来源保护以消除日志。

第 63 次继续运行原 DD154 候选，世界进度已到 56%，尚未记录真实玩家入世/稳定 Tick/正常保存。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原修改保留，无 commit/push。

## 2026-10-24：真实世界采样定位资源字段回退异常；名称提示批次已构建，尚未部署

第 63 次仍运行 Core DD154C3B05E13FC9860842A61CE35AED64CEC5D33C6FE22710E450AFF6BE8A54。世界生成期间沿已有 JFR 工具采集 30 秒真实工作负载，world.jfr 与 world-samples.json 保存在 attempt-63；没有新增夹具或基准。样本明确出现 ClientBridge.resourceField 在 GpuImages.enter/buildIdentity 调用链反复抛出 NoSuchFieldException，再回退到 SRG 名称；其中部分栈包含 JFR ThrowableTracer 自身的原子计数，样本数量不能当作纯业务 CPU 比例或提速结果。

src/bootstrap/ClientBridge.java 按实际 Class 和 VM 重定义计数记录成功字段名称；只缓存名称，不缓存 Field、字段值、缺失结果或许可判定。每次仍重新 getDeclaredField、检查静态属性和描述符并设置访问；提示缺失或版本变化回到原有官方名优先解析。src/bootstrap/PlatformMetadata.java 复用既有 Class 私有查找读取 classRedefinedCount，接口不可用时关闭名称提示，原真实声明读取不变。ClassValue 与各名称表接入既有控制对象保护，类身份隔离和类卸载生命周期由 ClassValue 保持。热路径消除重复失败查找及异常构造；没有性能基准，不宣称实测提速。

统一 jar 构建成功 19s，compileBootstrapJava 本轮执行；核心 Java、Agent/Prelaunch Java、Control Native 和 Storage Native 均复用已有产物。JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date，日志 build-local-resource-field-name-hints.log。新 Core SHA256 259BCAFC402B7D8EC862CBB970472B850C70FCD98C4A9EA61E0AC85448B28747；未部署，不能沿用第 62 次正常退出结果或第 63 次运行进度作为该批实机通过。

第 63 次继续等待真实玩家进入、Tick 与正常保存证据，当前只有世界生成进度；未作通过结论。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原未提交修改保留，无 commit/push。

## 2026-10-24：第 62 次正常退出未复现 VirtualFree 释放失败；同候选第 63 次进入世界验证

第 62 次在资源重载时于 2026-10-24T05:55:55.8790895Z 发出正常关闭请求，00:56:58 本地日志 Stopping!，05:57:20.1393785Z 进程退出码 0，没有强杀。完整 stderr 计数：BUSY 0、jemalloc VirtualFree 错误 0、TLS 拒绝 0、Native 内存来源异常 0；捕获器 GAME_TERMINAL_WITHOUT_CAPTURE。日志与 failure-summary 已保存。这仅确认当前候选此次资源阶段正常退出未复现目标失败，不能外推所有场景，更不代表世界/保存通过。原认证 401/公钥网络错误与 Bootstrap 待加载类边界仍单独存在，不冒充整体功能 FULL。

复用同一 Core SHA256 DD154C3B05E13FC9860842A61CE35AED64CEC5D33C6FE22710E450AFF6BE8A54 开始第 63 次世界阶段验证，没有重复构建。启动前完整备份至 backup-before-attempt-63/instance：218 目录、595 文件、509.38 MB，无失败/跳过/不匹配；原其他 27 个 Mod 文件与启用状态、82 项参数保持。匹配 JAR/DLL/PDB 已保存。既有只读 world-time 脚本记录 db704cc 的 Time=0、DayTime=0 至 world-before.json；原 pauseOnLostFocus:true 未修改，因此不能只凭等待时间宣称真实 Tick 推进。

第 63 次 PID 55288 已启动，首次 Native/来源失败捕获开启；这次继续进入世界，不使用资源阶段关闭触发。会话内读取真实 PlayerList 登录日志并保存入世时间，未新增持久检查脚本或额外负载。当前尚无玩家进入、稳定 Tick 或正常保存通过结论。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原未提交修改保留，无 commit/push；继续同一主线。

## 2026-10-24：修复新增线程状态大小档的槽步长对齐；第 61 次无效，第 62 次进行中

源码复核发现本轮新增的线程状态精确档有对齐缺陷：24488 字节载荷加 160 字节私有头为 24648，不是 16 字节整数倍；直接作为槽步长会使后续 Header/SLIST_ENTRY 失去所需对齐。此前只验证头类型和成员偏移，未覆盖这个非 2 的幂大小档的步长。该缺陷由本轮引入，不能用构建成功或启动 FULL 掩盖。

native/private_memory.c 增加统一 native_private_small_stride，按 _Alignof(NativePrivateHeader) 向上取整完整槽跨度，分配与反向识别同时迁移。当前线程槽为 24656 字节，真实载荷容量仍按 sizeof(NativeThread)=24488 计算，realloc 仍按该实际容量复制。native/bindings.c 的编译期小区容量约束纳入最坏对齐补齐。旧直接相加步长退出；各大小档均走同一个计算，不增加另一套分配器或扩大总保留区。

发现缺陷后核对 PID/Java/启动时间与测试前备份，于 2026-10-24T05:43:38.0377872Z 结束第 61 次 PID 70904，监控确认退出码 -1。首次失败捕获结束时未触发，资源关闭监控也未发出请求；stderr BUSY/jemalloc/TLS/Native 来源异常均 0，但该候选因明确源码缺陷判为无效，没有任何运行通过结论，也未宣称观察到 JVM 崩溃。日志、原候选与 invalid failure-summary 保留，世界移动至 attempt-61/failed-world-db704cc，再从该次预备份恢复：11 目录、10 文件、无失败/跳过/不匹配。

修正后统一构建成功 17s，Control Native 实际重编，Java/Storage Native 复用，20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功，既有 host_completion_port.c:355 警告保留，日志 build-local-aligned-thread-slabs.log。该改动是分配正确性修复，不宣称提速。

第 62 次完整预备份：217 目录、593 文件、508.60 MB，无失败/跳过/不匹配；部署 Core SHA256 DD154C3B05E13FC9860842A61CE35AED64CEC5D33C6FE22710E450AFF6BE8A54，匹配 JAR/DLL/PDB 保存。原其他 27 个 Mod 文件与启用状态、82 项参数和恢复后的 db704cc 不变。PID 76432，首次失败捕获及资源重载正常关闭监控开启，结果待定。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原修改保留，无 commit/push。

## 2026-10-24：第 60 次剩余释放尺寸对应线程状态；第 61 次验证中

第 60 次资源重载时于 2026-10-24T05:26:05.2424698Z 正常关闭请求返回 true，05:27:41.1485984Z 退出码 0，无强杀。共 1 条 BUSY、1 条 jemalloc VirtualFree 错误，无 JNI_TABLES_BUSY、TLS 拒绝或 Native 来源异常。唯一写入者为 crt_free，实际登记跨度 24648 字节。首次转储 6,194,824,510 字节，05:27:33.0285120Z 至 05:27:40.4815168Z 捕获。用该次匹配符号读取 sizeof：NativeThread=0x5fa8（24488）、NativePrivateHeader=0xa0（160），合计恰为 24648；线程退出源码中存在 free(state) 路径。allocation-layout.txt、完整日志/候选/符号/摘要均保留。尺寸与退出链支持线程状态释放方向，未把延迟转储冒充持锁瞬间完整栈。

native/private_memory.c 和 native/bindings.c 为同一保留区增加按编译期 sizeof(NativeThread) 选择的精确大小档，与原 128/256/512/1024/2048 五档共存于同一个 64 MiB 总保留区。通过内部 native_private_thread_bytes 接通真实结构大小，编译期约束确保单槽能放入小区，没有写死 24488。小区调整为 256 KiB，使多个线程状态可共用一小区；槽身份、实际容量查询、calloc、free、realloc 和所有失败回退沿既有统一路径处理。仅相同实际尺寸和原小块范围进入保留区，其他较大请求仍走原受保护 CRT 路径；没有添加另一套缓存或改变真正 CRT 释放的同步保护。

静态上使这条线程状态分配/销毁链退出逐块 CRT 分配与全局范围树变化。按需提交粒度增大，会多保留部分尚未使用的槽，进程内复用到结束，总上限仍为 64 MiB；没有性能基准，不宣称实测提速。统一构建成功 17s，Control Native 实际重编，Java/Storage Native 复用，20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功，既有 host_completion_port.c:355 警告保留，日志 build-local-thread-state-slabs.log。

第 61 次先完整备份：216 目录、591 文件、507.84 MB，无失败/跳过/不匹配；部署 Core SHA256 37D63BB6448103ABF5365C64604B53A0C6961CD39ACF5B7E0197B21289E497DA，匹配 JAR/DLL/PDB 保存于 tested-candidate。原其他 27 个 Mod 文件与启用状态、82 项参数和 db704cc 不变。PID 70904，首次失败捕获及资源重载正常关闭监控开启，结果待定。第 60 次仍是释放失败；没有任何本轮玩家、稳定 Tick 或正常保存通过结论。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push。

## 2026-10-24：第 59 次正常退出仍有 2 KiB 分配/释放争用；第 60 次进行中

第 59 次于 2026-10-24T05:03:12.6879326Z 因资源重载收到正常关闭请求，05:04:19.0339797Z 退出码 0，没有强杀。共 3 条 BUSY、3 条 jemalloc VirtualFree 错误，无 JNI_TABLES_BUSY、TLS 拒绝或 Native 来源异常。本窗口没有再记录到 JNI 目录争用，不代表永远不会出现。三条均在最终 private_gate：两条 crt_free（当时未记录释放尺寸），一条 crt_allocate=2208 字节，即 2048 字节载荷加私有头，均来自同一写入线程 58280；不能仅凭线程相同断言是同一个块。首次转储 6,073,300,922 字节，05:04:12.2709619Z 至 05:04:18.4033144Z 捕获，日志/符号/摘要保留。正常退出不等于 Native 释放通过，未进行世界/保存验证。

native/private_memory.c 将已有分配器接通为 128/256/512/1024/2048 五档，以统一计算替代写死的三档选择；共用原 64 MiB 总保留区及按需 64 KiB 小区，不增加另套地址区间。原各档 free/realloc 路径按真实小区身份自动覆盖新增档，容量和失败时的 CRT 回退不变。原子 writer 编码集中到 native_private_writer_publish；真实 CRT free 在已取得原写锁并找到实际登记块后，记录该块完整跨度，未知/获取窗口仍可为 0。未新增高频日志或额外原 API 调用。

曾考虑改变真正 CRT free 的锁范围；完整记录补充了具体 2048 字节分配证据，因此本批先完整覆盖已确认尺寸，并补齐剩余释放尺寸。真正 CRT free 仍沿用原同步保护，没有新增临时退役区或降低释放期间保护。统一构建成功 17s，Control Native 实际重编，Java/Storage Native 复用，20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功，既有 host_completion_port.c:355 警告保留；日志 build-local-private-2k-slabs.log。静态上使已确认的 2 KiB 分配及其归还退出逐块全局目录变更，未宣称实测提速或失败已清零。

第 60 次启动前完整备份：215 目录、589 文件、507.07 MB，无失败/跳过/不匹配。部署 Core SHA256 F8489B7426320BDD1E152E19663CA4CAE8960241AC33A8A74DD216A5FDB792B8，匹配 JAR/DLL/PDB 已保存。原其他 27 个 Mod 文件与启用状态、82 项参数、db704cc 不变。PID 76644，首次失败捕获及同一资源重载正常关闭监控开启，结果待定。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push。

## 2026-10-23：第 58 次正常退出仍有释放失败；三档分配与 JNI 退役收敛，第 59 次进行中

第 58 次由资源重载日志触发正常窗口关闭：2026-10-24T04:40:50.4105137Z 请求返回 true，23:42:12 本地日志 Stopping!，04:42:27.7192251Z 进程退出码 0，没有强杀。它补上正常关闭释放现场，但不是通过：stderr 共 4 条 BUSY 和 4 条 jemalloc VirtualFree 错误，其中两条 JNI_TABLES_BUSY、两条最终 private_gate；后者一条未观察到写入者，另一条明确 crt_allocate、672 字节（512 字节载荷加私有头），无 TLS 拒绝/Native 来源异常。首次转储 6,455,797,368 字节，04:42:17.7813024Z 至 04:42:27.1508862Z 捕获，候选/符号、日志和 failure-summary 已保留。未进入世界，不计玩家、稳定 Tick 或保存通过。

native/private_memory.c 将单档保留区扩展为 128/256/512 三档，共享同一个 64 MiB 总保留区，不叠加额外保留区。按需提交 64 KiB 小区，每个小区有受保护、一次发布的真实大小档及已分配槽数；归还严格核对保留区、小区、槽边界及已发布槽数，不信任载荷中的伪造头。各档原子空闲链复用，跨档 realloc 先取得新块并复制原容量，再归还旧块；失败保持原块。原单档游标/页提交实现退出，容量/准备/提交失败和大于 512 字节仍保留原受保护 CRT 回退。总上限和进程内保留复用的内存代价不变，小区中尚未使用的容量也属于本控制器。

native/jni_tables.c 的线程退役先在 JNI 写锁下从可遍历目录摘除状态，然后释放该锁，再注销仍保持保护的私有范围。外国全线程读取已无法取得被摘除状态，只有拥有线程能重新观察；状态存储仍在注销完成后才释放。避免持 JNI 目录锁等待另一私有写入者，不删除半登记清理或实际范围注销。受限 JNI 范围查询与 unchanged 查询改用 native_bounded_read_lock，与私有目录共用原先最多 64 次额外获取尝试；不睡眠、不进入 VM、不无限等待，必须取得真实锁才查询，持续争用仍保留原失败原因。删除重复获取分支，不重复原内存 API。

以上完整批次统一构建成功 17s，Control Native 实际重编，Java/Storage Native 复用，20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功，既有 host_completion_port.c:355 警告保留；日志 build-local-private-slabs-jni-retirement.log。静态减少已确认 512 字节分配的全局目录写入，并缩短 JNI 退役持锁范围；没有性能基准或实测提速结论。

第 59 次启动前完整备份至 backup-before-attempt-59/instance：214 目录、586 文件、506.28 MB，无失败/跳过/不匹配。部署 Core SHA256 CB22E88F1ACEBE33E258C5BD20FF9CF5F6B48BAEB8E6B4E42F85189D35966FBD，匹配候选与符号已保存。原 27 个其他 Mod 文件和启用状态、82 项启动参数、db704cc 不变。PID 72072，首次失败捕获及同一资源重载时正常关闭监控均已开启，当前结果待定。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原修改保留，无 commit/push。

## 2026-10-23：依据 288 字节实际争用替换小块分配路径；第 58 次进行中

第 57 次新字段明确捕获 crt_allocate 的 writer_sample_bytes=288、未截断，恰为当前 128 字节小块及 160 字节私有头。失败仍是 NVIDIA 调用的 MEM_DECOMMIT 最终 private_gate 准入，原 API 未调用。首次转储 4,930,184,047 字节，捕获时间 2026-10-24T04:18:16.8410446Z 至 04:18:22.3730751Z。这是小块缓存未命中后进入全局写锁/CRT 分配的实际证据，不再仅凭泛化的 crt_allocate 标签选优化方向。

native/private_memory.c 完成小块分配结构替换：冷启动在原全局私有写锁内保留最多 64 MiB 自有地址区间，并登记完整保留区及元数据后才发布；实际需要的页面随后在独立叶锁内提交，块复用/归还使用原子 SList，均不再改写全局私有范围树。完整保留区在空闲、已用和未提交状态都属于控制器自有范围，不会把普通游戏分配标成私有。页面提交锁的持有者不取得全局范围锁、不进入 CRT/Java/JVMTI/外部回调；外部释放准入仍取得原全局真实读锁并检查实际目标范围，没有新增放行或重试原 API。

128 字节及以下的 malloc/calloc、free 和 realloc 全部接通：归还按真实保留区边界、已分配槽数和对齐识别，外部/原 CRT 指针不会因数值近似而被接收；小块内缩保持数据，扩容先成功取得受保护新块并复制原容量，再归还旧槽，分配失败保留原块。删除原 256 项 CRT 小块缓存及其计数/每块目录变更路径。保留区准备/提交失败或容量耗尽时仍走原已登记 CRT 分配和原失败处理；较大分配、显式虚拟分配、外部范围登记与 JNI 公共可写 payload 机制保留。返回值仍满足原 max_align_t/SLIST 对齐。

代价明确：保留最多 64 MiB 虚拟地址，仅按使用增长提交页面；已提交页保留到进程结束供复用，不宣称逐块 free 已向 OS 归还内存。静态收益是已确认小块路径不再因每次目录写入与普通释放争抢全局门锁；首次保留、较大分配及回退仍可能争用，不能宣称全部 BUSY 已消失。统一构建成功 27s，Control Native 实际重编，Java/Storage Native 复用，20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功，既有 host_completion_port.c:355 警告保留，日志 build-local-private-small-reservation.log。

第 57 次实际关闭请求于 04:23:04.6411641 UTC 发出时已进入世界准备，未能覆盖原计划的资源加载阶段正常退出。04:31:21.3771573 UTC 监控确认退出 -1，最后出生区 9%，1 条 BUSY，0 jemalloc/TLS/Native 来源异常，没有玩家/正常保存通过。失败世界保存在 attempt-57/failed-world-db704cc，再从测试前备份恢复，11 目录、10 文件、无失败/跳过/不匹配。

第 58 次先完整备份：213 目录、584 文件、505.79 MB，无失败/跳过/不匹配；部署 Core SHA256 F686D654F507180292478ED8C132FE7E78EF9D9FF62709AB504D0CC0A13400B6，配套匹配 JAR/DLL/PDB 已保存。原全部 Mod 文件/启用状态、82 项参数和恢复的 db704cc 不变，PID 56744，首次失败捕获开启。另由当前会话直接监测资源重载日志，届时仅发送一次正常窗口关闭请求，不强杀，用于及时覆盖实际释放阶段；没有新增持久检查脚本或额外实例。当前候选尚未完成正常退出或世界验证。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push。

## 2026-10-23：第 56 次中止归档，第 57 次部署当前全部生产修改

第 56 次 PID 73700 于 2026-10-24T04:10:24.8349434Z 确认退出，监控退出码 -1，运行约 22 分 59 秒。正常关闭请求未完成，最后出生区进度 14%，没有玩家进入、稳定 Tick、正常保存或重启通过。stderr 最终 1 条 BUSY、0 条 jemalloc VirtualFree 错误、0 条 TLS 拒绝、0 条 Native 内存来源异常；驱动释放失败转储与匹配符号、世界线程栈和日志全部保留。失败世界移动至 attempt-56/failed-world-db704cc，随后从该次测试前备份恢复 db704cc：11 目录、10 文件、无失败/跳过/不匹配。没有删除失败世界，也不把中止记为正常释放通过。

第 57 次启动前完整备份至 backup-before-attempt-57/instance：212 目录、582 文件、505.07 MB，无失败/跳过/不匹配。部署 Core SHA256 5EAF3B79F6A840B4E9E00958B5692CBF397C2681A89D3FF69286862C127EC8D2，包含截至当前全部生产修改：GPU 活跃 builder 索引、查询常量池/元数据/存储收敛、来源数组类型身份复用、弱身份目录无临时读取键，以及原子 writer 分配大小记录。配套 JAR/DLL/PDB 保存于 attempt-57/tested-candidate。

原 27 个非当前 Core 文件与启用状态保持（2 个启用第三方 JAR、25 个禁用文件），原 82 项参数和恢复后的独立世界 db704cc 不变。PID 76444 已启动，首次 Native/来源失败捕获开启；当前尚无本候选的世界或正常释放通过结论。最后一次统一构建为 Core Java 实际重编的 32s 成功构建，Native 复用本轮已成功产物，未为部署重复编译。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原工作区修改保留，无 commit/push。

## 2026-10-23：第 56 次复现 NVIDIA 释放争用；弱身份查询与失败采样已构建

第 56 次实际复现一条 NATIVE_MEMORY_RELEASE_BUSY：kind=30、site=private_gate、range=NONE、original_called=0，释放 655,360 字节、MEM_DECOMMIT，调用线程 72024，tooling=1、loader=1，写入者采样线程 36648、crt_allocate。首次转储 attempt-56/native-first-failure.dmp 为 4,987,305,647 字节，捕获 2026-10-24T03:54:43.4083466Z 至 03:54:51.6995695Z。使用该次 tested-candidate 的匹配符号确认调用映像为 nvoglv64.dll；采样线程在转储中已回到 JVM StackWalker，native_private_writer 为 0，释放线程已无法按原 TID 选取。因此不能把转储当作持锁瞬间的完整栈。日志与 first-release-stacks.txt 保留，未升级或修改驱动。

native/private_memory.c 与 host_memory.c 将原单个原子 writer 采样扩展为同一 64 位元组内的线程、操作和有上限的分配大小：32 位线程、8 位操作、24 位大小，达到上限明确标记，非按大小记录的操作为 0。CRT 分配/重分配记录含私有头的总请求量，虚拟分配记录页取整量。仅现有 BUSY 日志输出新字段，用于区分短块频繁分配与大块分配；没有新增高频日志、调用栈采样、等待或放行例外。统一构建成功 23s，Control Native 实际重编，Java/Storage Native 复用，日志 build-local-private-writer-size.log。

实际服务器栈仍经 CompletableFuture/VarHandle → Unsafe → 字段/来源判定。src/core/BackingPolicy.known 的多个实时目录共用 WeakIdentityMap，其原 get/containsKey/remove 每次创建临时 WeakReference 查询键。src/core/WeakIdentityMap.java 改为同一弱键节点直接承载身份哈希桶链接和值，读取按真实对象身份比较，退出临时查询键和内部 HashMap 包装层。登记、扩容、队列回收、移除、clear、size 与原快照迭代全部迁移；删除/清空主动解除节点值与链，避免快照持有已删除值；空值存在性、同身份替换、移除重插后的迭代读值/删除语义保留。继续由原调用方同步，不缓存目录是否命中的结论，不把 GC 当作释放成功。

该批统一构建成功 32s，Core Java 实际重编，Bootstrap/Agent/Prelaunch Java、Control/Storage Native 复用，20 tasks：9 executed、11 up-to-date，JAR/reobfJar 成功，14 条既有 Java 弃用/unchecked 警告保留，日志 build-local-weak-identity-lookup.log。静态上减少每次目录读取的弱引用对象与登记的独立 HashMap 节点；哈希冲突仍按身份链处理，未做性能基准，不宣称实测提速。本段两批与上段来源数组/VM scratch 修改尚未部署，第 56 次不代表它们通过。

第 56 次 22:57:21 本地启动集成服务器、22:59:02 开始出生区准备，23:07:18 到 8%；尚无玩家进入或正常保存。04:08:01.8185559 UTC 请求正常关闭返回 true，终态待确认。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，所有原修改保留，无 commit/push；继续同一主线。

## 2026-10-23：第 55 次中止并恢复世界，第 56 次实机进行中；来源数组与 VM 查询存储继续收敛

第 55 次 PID 34112 从 2026-10-24T03:21:29.0430224Z 运行至 03:46:35.6696871Z，退出码 -1，约 25 分 7 秒。正常关闭请求未完成；出生区最后到 12%，没有玩家进入、稳定 Tick 或正常保存证据。stderr 中 BUSY、jemalloc VirtualFree、TLS 拒绝和 Native 内存来源异常均 0，捕获器 GAME_TERMINAL_WITHOUT_CAPTURE；这不覆盖正常关闭释放，不能据此宣称失败已彻底修复。原日志、世界线程栈及 30 秒 JFR 已归档。失败世界保存在 attempt-55/failed-world-db704cc，再从该次测试前备份恢复，11 目录、10 文件、无失败/跳过/不匹配。

第 56 次完整备份至 backup-before-attempt-56/instance：211 目录、580 文件、504.31 MB，无失败/跳过/不匹配。部署 Core SHA256 C0D478F3BFD6487F8D98981FFF63409C714DD1D98131BE7B40C00C1FA8A70F58，包含 GPU builder 活跃索引、常量池小目录、帧方法选择器按需读取和进程视图类型排除。原 27 个非当前 Core 文件及启用状态保持（2 个启用的第三方 JAR、25 个禁用文件），原 82 项参数和恢复后的独立世界 db704cc 不变。PID 73700，启动时间 03:47:25.8636104 UTC，匹配 JAR/DLL/PDB 在 tested-candidate，首次失败捕获开启。Agent/Native 报告 FULL，Bootstrap 因 ForkJoinTask$Aux、FutureTask$WaitNode 等待加载仍为 PARTIAL；当前继续观察，不冒充世界通过。

第 56 次运行期间，native/bindings.c 合并 native_tooling_writers / native_tooling_sources 的重复来源数组生成，前者仅保留语义明确的无未知标记入口。内建 java.lang.Module Class 通过原 JNI 解析并原子发布一个 VM 生命周期全局引用；并发准备落败释放自身引用，失败不发布。每个调用仍新建数组、读取原实际弱模块引用，保留顺序、未知 null 标记及实时 producer 筛选；没有缓存来源数组或权限。三处模块数组构造复用同一类型身份。统一构建成功 23s，Control Native 实际重编，Java/Storage Native 复用，日志 build-local-source-array-identity.log。

继续完成 native/vm_metadata.c 与 code.c 的同一查询存储批次：ConstMethod 字段全部读出后，复用当前查询的受保护 header 缓冲读取 ConstantPool 头，容量取两种头最大值，退出仍由原 scratch 统一释放；引导常量目录在实际访问时才分配，计数/操作数边界仍在池初始化时核对，真实动态常量解析及失败处理保留；tags/slots 本地扫描副本合并为 slots 对齐在前、tags 在后的同生命周期块，统一只释放基址，原实时重读和不稳定项回退不变。统一构建成功 24s，Control Native 实际重编，Java/Storage Native 复用，日志 build-local-vm-query-storage.log。两次均 20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功，既有 host_completion_port.c:355 警告保留。

上述最后两批仅在工作区构建，尚未部署到第 56 次。静态上减少每次内存来源返回的类型解析、快照的独立池头分配和未使用引导目录，以及扫描目录的一次分配/释放；未做性能基准，不声明实测提速。HEAD 仍为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，继承修改、未跟踪 IDE 文件与 bin/ 保留，无 commit/push。当前主线继续等待第 56 次实际运行结果。

## 2026-10-23：真实世界采样支持 GPU 扩容记录索引收敛，合并候选已构建

第 55 次同一进程的世界阶段 30 秒 JFR 保存为 attempt-55/world.jfr（39,017,926 字节），仅抽取执行/Native 方法样本。GetQueuedCompletionStatus0 的 1,289 个样本包含等待，不能当作 CPU 消耗；全线程还见 Unsafe.compareAndSetInt 89、ResourceBridge.critical 28，渲染线程的 GpuImages.capacityChanged 10。样本不是耗时比例或优化前后比较，也包含采样自身影响。

沿渲染栈核对 src/bootstrap/ClientBridge.java：BufferBuilder.ensureCapacity 每次返回都会扫描整个 rendered 目录，虽然后续跳过其他 builder/已完成记录，但仍遍历它们。新增按真实 builder 弱身份分组的 activeRendered，扩容只访问该 builder 尚未完成的 CpuImage；沿用同一真实结果弱键，不强持有 builder/result。store/replacement、正常 release、discard、退役中实际 release、pending 观察已释放、弱引用 reap 及主动死键清理全部接通。完成后清除 CpuImage 对分组的引用，空分组退出；builder 回收仅移除索引，不冒充实际释放。保护登记包括索引和分组容器，操作仍在原 GpuImages 监视器内。

原 rendered 继续承担实际结果身份、上传、字节回读和退役证据职责；它不是重复旧路径，不删除。被替代的是 ensureCapacity 和 discard 的全局遍历，真实 builder/原存储身份、容量、指针、drawState 与字节一致性核对均保留。静态上将扩容遍历范围从全部历史结果缩到当前 builder 的未完成结果，同时增加存储时的分组登记成本；没有实测提速结论。

统一构建成功 21s，20 tasks：10 executed、10 up-to-date；Bootstrap Java 实际重编，Control Native 复用上一批 32s 成功产物，其他 Java/Storage Native 复用，JAR/reobfJar 成功，日志 build-local-gpu-builder-images.log。新合并候选尚未部署。第 55 次 22:43:58 本地出生区到 9%，未进入玩家/稳定 Tick；03:44:31.5252053 UTC 请求正常关闭，CloseMainWindow 返回 true，终态待确认，未宣称正常退出。仍不提交或推送 Git。

## 2026-10-23：来源查询目录与帧元数据按需读取；第 55 次仍在出生区准备

沿第 55 次真实线程栈继续修改：Render thread 位于 ClientBridge 顶点读取 → ByteBuffer.get → memoryReadSources0，Server thread 位于 CompletableFuture/VarHandle → beginNativeUnsafeMutation → unsafeMutationReceiptRequired。第 55 次使用的仍是 Core CC989F595D4BCF5C1D5FC9441BF0533FA22F6AB2817D31889BC5E14D87D14518；本段新代码尚未部署，不能沿用它的运行结果。

native/code.c、vm_metadata.c、carrier.c 收敛 CodePool 读取入口。单方法实时快照先在受保护的私有分配中保存最多 8 个实际解码项，超过后一次迁移到原完整索引目录；整个类和登记类文件仍使用完整目录。全部 VM 解析、常量比较、载体调用/适配器分析迁移到共同读取接口，条目字节在迁移中不移动；失败释放新条目，保留原目录以便统一清理。短查询不再按整个常量池清零和遍历释放，仅清理实际项；完整体/版本/异常处理器/动态常量比较保持，未保留跨查询来源或权限结论。统一构建成功 25s，Control Native 实际重编，Java/Storage Native 复用，日志 build-local-pool-working-set.log。

src/bootstrap/ResourceBridge.java 的 critical 原先对每个普通对象都创建弱键并锁定 STREAMS 查询 PROCESS_VIEWS。唯一登记端 processConstructed 只接受实际 BufferedInputStream、BufferedOutputStream、FileOutputStream 和 PIPE_INPUT 四类。现先用这些固定真实类型排除不可能登记的对象；符合类型仍检查实时弱关联，所有其他资源类型与权限检查继续执行，没有缓存负面结果。统一构建成功 19s，Bootstrap Java 实际重编，Control Native 复用上述产物，Storage Native/其他 Java 复用，日志 build-local-process-view-lookup.log。

native/code.c 将单帧的类身份元数据与方法选择器读取拆开；先用实际类/加载器筛选已发布映像，确有匹配才调用 GetMethodName。共享发布锁下发现需要方法元数据时先释放锁，完成 JVMTI 查询后从当前发布前沿重新读取；竞争时原已发布链回退仍保留。没有相关映像的普通帧省去方法名/描述符分配与释放，存在相关映像时继续全部历史、当前真实版本与歧义检查。统一构建成功 32s，Control Native 实际重编，Java/Storage Native 复用，日志 build-local-frame-selector-demand.log。三次均 JAR/reobfJar 成功，20 tasks：10 executed、10 up-to-date；既有 host_completion_port.c:355 警告未变。

性能依据仅为实际热路径和上述操作数量/目录大小变化，未做优化前后基准，不宣称实测提速；按需元数据在确有匹配且尚未读取选择器时多一次释放/重取发布锁。第 55 次 22:31:46 本地启动集成服务器，22:33:55 开始出生区准备，22:38:10 到 1%；尚无玩家进入、稳定 Tick、正常关闭保存或重启通过。持续跟进当前运行，保留原 Mods、参数和世界。HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原未提交修改保留，无 commit/push。

## 2026-10-23：世界阶段最近写入者查询收敛；attempt-55 实机进行中

第 54 次实际进入世界阶段：本地 22:05:37 启动集成服务器，22:07:45 开始主世界出生区准备，最后只到 9%。世界阶段 threads-world.txt 显示渲染线程在加载画面的 DirectByteBuffer 写入 → memoryWriteBegin0，服务器线程在 CompletableFuture/VarHandle CAS → beginHandleMutation → actualMemoryCaller。不是仅凭进度慢就宣称死锁。

ControlBridge.actualMemoryCaller 只需找到桥接类外的第一个真实写入者，却共用了首批 64 帧的深层 ORIGIN_WALKER。本批增加首批 8 帧、仍包含 SHOW_HIDDEN_FRAMES 的专用 MEMORY_WRITER_WALKER，并迁移该统一查询入口；Unsafe、VarHandle、相关调用者检查沿用原完整选择器，深栈仍续读，不用 getCallerClass 隐藏真实 VarHandle 帧。深层来源/停止查询保留原 ORIGIN_WALKER，权限与来源结论不缓存。静态上减少每次短查询预先创建的帧对象；实际性能未测量。完整统一构建成功 26s（10 executed、10 up-to-date），Bootstrap Java 实际重编译，Control Native 复用本轮有界准入 33s 的成功产物，其他 Java/Storage Native 复用，JAR/reobfJar 成功，日志 build-local-hidden-writer-batch.log。

第 54 次正常关闭请求于 2026-10-24T03:16:13.3820766Z 返回 true 但未完成。核对进程身份和测试前世界备份，保留现场后于 03:19:33.2094055 UTC 结束 PID 70096，监控确认退出码 -1，运行约 33 分 9 秒，不计正常退出/保存通过。共 2 条有界 BUSY，jemalloc VirtualFree 错误与 TLS/Native 内存来源异常未记录，但强制中止未覆盖正常释放，因此零错误不代表释放通过。首次转储 1,875,194,271 字节；短时 JFR、世界线程栈、日志及匹配符号均保留。失败世界移动归档到 attempt-54/failed-world-db704cc，再从 backup-before-attempt-54/instance/saves/db704cc 恢复同名世界，11 目录、10 文件、无失败/跳过/不匹配，没有删除失败世界。

第 55 次启动前完整备份至 backup-before-attempt-55/instance：210 目录、578 文件、503.48 MB，无失败/跳过/不匹配。部署最新 Core SHA256 CC989F595D4BCF5C1D5FC9441BF0533FA22F6AB2817D31889BC5E14D87D14518；匹配 JAR/DLL/PDB 在 attempt-55/tested-candidate。除当前 Core 外的 27 个文件、其中 2 个启用的第三方 JAR 与 25 个禁用文件全部保持，原 82 项参数和恢复后的独立世界 db704cc 不变。PID 34112 已启动，首次 Native/来源失败捕获开启；测试尚未完成，不沿用第 54 次或旧候选结果。

本轮仍在持续推进，起止 HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原修改保留，无 commit/push。下一步依据第 55 次的实际初始化、世界运行和正常释放结果继续处理剩余阻断。

## 2026-10-23：依据实机短时采样收敛数组类型查询；有界私有门锁准入已构建

第 54 次继续运行，未因出现早期失败立即结束。首次 BUSY 位于最终 private_gate，采样线程 73700、crt_allocate；捕获时间 2026-10-24T02:48:16.2324492Z 至 02:48:20.6873897Z。匹配符号的后续转储读取已落在 JIT 地址且 writer 为零，不能冒充持锁瞬间调用栈。当前仍在资源加载，没有世界/稳定 Tick/保存通过证据。

针对真实慢加载，在同一 PID 70096 上使用现有 JDK 做一次 30 秒 JFR 调用采样，没有新游戏、夹具或合成负载。loading.jfr 16,769,757 字节，抽取的两类调用事件 JSON 6,489,818 字节。较多样本顶部为 System.arraycopy（62）、Inflater.inflateBytesBytes（52）、NativeControl.memoryWriteBegin0（44）；采样也包含 JFR 自身线程（例如 emitEvent 23），这些计数不是 CPU 占比或性能基准，更不是优化前后提速证明。实际业务栈包括 UnionPath/JAR 读取、解压、DirectByteBuffer/Unsafe，以及 ControlRegistry 保护发布。

沿两条数组热路径完成 native/arrays.c 与 native/arraycopy.c 的统一类型判定：在安装数组边界前解析并原子发布八类基本类型数组和 Object[] 的真实 VM Class；并发准备失败/落败只释放自身尚未发布的全局引用。所有数组仍先读取真实 Class，基本类型按 Class 身份核对，引用及多维数组按 Object[] 实例关系判断，保留实际组件 Class 供部分复制兼容性检查。移除 System.arraycopy 两端和 JNI 数组指针每次 GetClassSignature/字符串分配释放的重复路径。来源、保护、读写租约、数组快照、越界/类型异常、重叠与引用数组的部分复制行为保持；缓存的是 VM 内建类型身份，不是权限或来源结论。统一构建成功 31s，日志 build-local-array-type-identities.log。

针对持续复现的最终门锁争用，native/private_memory.c 在受限读锁第一次尝试失败后增加最多 64 次额外 TryAcquireSRWLockShared 与处理器暂停提示。没有 Sleep、线程切换、VM 回调、私有分配或无限等待；成功仍必须持有真实锁并执行原范围/停止状态检查，重试的是锁获取，原内存 API 不会重复执行。自重入、被挂起或持续占用的写锁仍在有界尝试后返回原 BUSY，并采样最后失败处写入者。普通无争用快路径不变，争用时会增加有界 CPU 工作，不能声称已完全消除失败。统一构建成功 33s，日志 build-local-private-bounded-admission.log。

以上两批均实际重编 Control Native，Java/Storage Native 复用，20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功；既有 host_completion_port.c:355 警告保留。这两个最新批次尚未部署：第 54 次仍运行 Core SHA256 4077D94B0A278F5ECF72DE47E147CA4E047E4496BF8B5C61E523E8A9C67DC8D6 的候选，不能转用其结果。原未提交修改保留，HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，无 commit/push。

## 2026-10-23：JNI 只读与空退出路径、常量池目录存储收敛；attempt-54 实机进行中

第 52 次于 2026-10-24T02:32:48.9769222Z 正常退出（本地 10 月 23 日晚），退出码 0，未强杀。共 3 条 BUSY 和 3 条 jemalloc VirtualFree 错误，无 TLS 拒绝或 Native 内存来源异常。首个失败是 JNI_TABLES_BUSY，捕获时间 02:32:35.6292014 至 02:32:48.3685373 UTC，转储 4,548,739,837 字节；后续另有 PRIVATE_RANGES_BUSY、crt_free 采样。它补足了第 51 次中止前未覆盖的正常关闭释放现场，故不能从第 51 次短窗口零错误推断修复完成。完整日志、匹配候选、转储与失败摘要已保留。

第 53 次完整备份为 208 目录、574 文件、501.57 MB，无失败/跳过/不匹配；Core SHA256 700D4996B8D7E17DF4FDD2A84E4779A3B727DB1355295921D920C3612ABF8822。原 27 个其他 Mod 文件（2 个启用的第三方 JAR，25 个禁用文件）、82 项参数和 db704cc 不变。PID 53624，已于 2026-10-24T02:44:46.2590862Z 正常退出，退出码 0。14 条有界 BUSY、9 条 jemalloc VirtualFree 错误，无 TLS 拒绝或 Native 内存来源异常；首次转储 1,301,010,057 字节，捕获于 02:35:16.1137145 至 02:35:20.1900157 UTC。新的私有范围预检查已退出，剩余失败实际落在 private_gate，采样仍为 crt_allocate。没有世界/稳定 Tick/保存通过证据，数字变化不是性能比较。

依据上述真实释放失败，native/jni_tables.c 合并批准状态判定：native_jni_expected_read_locked 只读解析真实当前指针、批准表、正在发布的表、撤销位和停止来源；旧修复入口在确需退休批准记录时仍持写锁释放来源并置 pending。overlay 仅修改本次表副本时使用共享锁；发现真实撤销需要后切到写锁并重新读取全部状态，不把先前判断当放行凭据。unchanged 校验同步迁移到同一判定，删除重复批准分支。真实登记、全表修复、撤销和 VM 发布仍保持原排他与失败处理。native_jni_thread_end 对不在登记链且两个范围都未登记的自有状态直接结束，移除纯 C 状态及重复清理的空写锁；半登记失败和真实退出仍完整清理。该批统一构建成功 31s，日志 build-local-jni-read-overlay.log。

继续同一分配链：native/code.c 的 code_pool_arrays 将相同数量、相同生命周期的常量池 entries/sizes 两个目录合并为一个清零的私有分配。类文件解析及 native/vm_metadata.c 的实时 VM 快照两处创建方均迁移，code_pool_free 只释放共同基址，删除独立 sizes 分配/释放。常量池条目内容、真实版本读取、动态项、失败清理和原容量边界不变，没有复用旧来源结论。该批统一构建成功 31s，日志 build-local-pool-directory-storage.log。

上述两批均实际重编 Control Native，Java 与 Storage Native 复用，20 tasks：10 executed、10 up-to-date，JAR/reobfJar 成功，保留 host_completion_port.c:355 既有警告。静态上减少无实际修改的 JNI 写锁和每次常量池目录的一次分配/释放；合并块的大小类别会变化，不能只凭分配次数推断整体提速，未运行额外基准。

第 54 次先完整备份至 backup-before-attempt-54/instance：209 目录、576 文件、502.35 MB，无失败/跳过/不匹配。部署合并候选 Core SHA256 4077D94B0A278F5ECF72DE47E147CA4E047E4496BF8B5C61E523E8A9C67DC8D6，匹配 JAR/DLL/PDB 在 attempt-54/tested-candidate。原 Mod 文件及启用状态、参数、测试世界不变，PID 70096 已启动，首次失败捕获开启，测试进行中。起止 HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改全部保留，无 commit/push；未宣称开发主线完成。

## 2026-10-23：用户恢复持续工作；释放预检查与 Owner 身份查找收敛

用户明确“继续”，暂停已解除，持续开发及必要实机恢复；仍不提交或推送 Git。起始 HEAD eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原未提交与未跟踪文件全部保留。核对第 51 次确已退出且 build/libs Core 与其候选一致后，直接复用该次已成功构建产物，未为恢复重复编译。

第 52 次先完整备份实例至 backup-before-attempt-52/instance：208 目录、573 文件、501.51 MB，无失败/跳过/不匹配。原启用/禁用状态全部保持。纠正旧记录中“27 个第三方 Mod”的不精确表述：除当前 Core 外实际为 27 个原文件，其中 2 个启用的第三方 JAR，其余 25 个为禁用文件（含旧 Ronova）；本次前后全部文件名称、大小和哈希一致，没有更换 Mod 集合。部署 Core SHA256 B03E32FDAF171E06E6CCD9E7DD7135909AD7561405937D20E054CB4129DC506D，原 82 项参数与独立世界 db704cc 不变。PID 69860，开始时间 2026-10-24T02:23:27.6022262Z（本地 10 月 23 日晚），匹配候选、符号与首次失败捕获均就绪。

native/host_memory.c 的 Win32 VirtualFree/VirtualFreeEx/UnmapViewOfFile/UnmapViewOfFileEx 原先在完整预检查中取得私有读锁，随后又在最终释放前取得同一读锁并重复检查。现将这一释放预检查收敛到 native_control_range_query 的延后私有检查模式，JNI 表、平台结构、控制映像、VM/Tooling 环境及来源检查仍先完整执行；实际本地释放前仍重新解析当前完整 allocation/view 或显式页区间，持私有读锁核对全部实际影响范围，并保持锁到原 API 返回。原参数、标志、远程句柄身份、停止来源与卸载清理条件不变。独立查询、其他内存 API 仍走原完整私有检查；没有把私有判定缓存为放行结果。旧释放预检查路径和其不再可能产生的 PRIVATE_RANGES_BUSY 写入者拷贝分支退出，最终门锁争用记录保留。此完整批次统一构建成功 33s，10 executed、10 up-to-date，日志 build-local-release-private-gate.log。

随后沿同一来源采集调用链完成 native/bindings.c 的 Owner 身份查找收敛：NativeThread 内保留最多 8 个非拥有的、已发布 Owner 身份，命中仍以 IsSameObject 比较真实 Module；发布头变化即清空查找窗口，未命中仍按原最新优先链查找，不记录查找失败。Owner/链接沿用进程生命周期，模块仍为原弱引用，不新增强引用保活；producer、stopped 和来源许可由原消费方实时读取，不缓存权限结论。没有新 NativeThread 时走原查询，不为命中分配线程状态；源码调用方统一经 native_owner_published 获得这项收敛。完整合并候选统一构建成功 30s，10 executed、10 up-to-date，日志 build-local-owner-identity-lookup.log。

上述两次均实际重新编译 Control Native，全部 Java 与 Storage Native 复用，JAR/reobfJar 成功；host_completion_port.c:355 原警告保留。静态上减少释放的一次私有锁获取/树遍历以及同线程重复模块目录扫描，新增有界身份比较和查找记录维护；未运行性能基准，不声明实测提速。第 52 次仍运行恢复前的候选，本次新生产代码尚未部署，不能借第 52 次结果冒充通过。当前已请求其正常关闭以采集释放现场，终态待确认。

## 2026-10-23：用户要求第 51 轮停下，当前开发与实机暂停

用户在本轮明确“51轮停一下”，覆盖此前持续实机授权的当前执行安排。不启动第 52 次，不继续生产修改，不提交或推送 Git。第 51 次 PID 43500 于 17:53:02.1292652 UTC 启动；17:56:22.9652933 UTC 正常窗口关闭请求返回 true 但未退出，按用户停止要求核对 PID、Java 路径、启动时间及测试前世界备份后，于 17:58:22.4524889 UTC 结束该进程。监控已确认退出码 -1，属于用户中止，不能计正常退出/资源释放/保存通过。

本次运行约 5 分 20 秒，最后仍在客户端加载；没有集成服务器、玩家进入、稳定 Tick 或保存通过证据。截止中止，stderr 中 BUSY、jemalloc VirtualFree、TLS 拒绝和 Native 内存来源异常记录均为零；首次失败捕获正常结束为 GAME_TERMINAL_WITHOUT_CAPTURE，没有产生失败转储。此结果只覆盖实际运行的短窗口，不能推断剩余故障已修复，尤其强制结束未验证正常关闭释放链。process/exit、用户关闭请求、forced-stop、failure-summary、stdout/stderr/latest/debug 与匹配候选均保存在 attempt-51。

当前源码及部署候选仍为上节 Core SHA256 B03E32FDAF171E06E6CCD9E7DD7135909AD7561405937D20E054CB4129DC506D，最终统一构建成功 17s；Control Native 本批实际重编译，Bootstrap Java 来自此前 40s 成功构建，其他 Java 与 Storage Native 复用。HEAD 仍为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，所有原修改、实例备份和现场保留。后续恢复须遵循用户新的指令，不能从旧持续授权自动重启。

## 2026-10-23：小块复用池退出争用回退路径；attempt-51 已中止（终态见上）

第 50 次于 17:46:57.1897135 UTC 正常退出，退出码 0，未强杀；保留资源加载、正常关闭和完整日志。共 10 条有界 BUSY、7 条 jemalloc VirtualFree 错误，无 TLS 拒绝或 Native 内存来源异常；首个转储 1,316,797,714 字节。关闭阶段仍采样到 crt_allocate 写入者，不能判释放通过；未取得玩家、稳定 Tick 或世界保存通过证据。

native/private_memory.c 的小块池原先在池锁争用时直接退回 CRT 分配/释放，给全局私有范围写锁增加额外压力。现将池头/节点替换为 Windows Interlocked SList，删除 native_private_small_gate 和旧 next 链维护。初始化只发布一次，初始化中的其他调用仍回原路径而不等待；入池先原子预留容量，出池后归还计数，保持最多 256 个、每块 128 字节数据区的保留上限。实际池空、满和非小块仍走完整原分配/回收路径，未设置业务分配数量上限。所有缓存块始终留在原私有范围索引，不缓存来源或权限；header 和成员偏移按 MEMORY_ALLOCATION_ALIGNMENT 编译检查。微软 SList 文档说明其非阻塞同步及对齐要求：https://learn.microsoft.com/en-us/windows/win32/sync/interlocked-singly-linked-lists 。静态上移除池互斥竞争造成的分配/释放回退；不能据此声称全局范围 BUSY 已消失或实际提速。

本批统一构建成功 17s，20 tasks：10 executed、10 up-to-date；Control Native 实际重编译（包含此前完整帧身份批次），Java 与 Storage Native 复用，JAR/reobfJar 成功，日志 build-local-private-slist.log，既有 host_completion_port.c:355 警告保留。此前帧身份批次单独构建成功 26s，最终与本批一并部署，不将其成功冒充实机结果。

第 51 次启动前完整备份至 backup-before-attempt-51/instance：207 目录、571 文件、500.80 MB，无失败/跳过/不匹配。原 27 个第三方 Mod、82 项参数和独立世界 db704cc 保持；Core SHA256 B03E32FDAF171E06E6CCD9E7DD7135909AD7561405937D20E054CB4129DC506D，匹配 JAR/DLL/PDB 保存于 attempt-51/tested-candidate。实际 PID 43500，首次失败捕获已启用，测试进行中。源码/构建/实机证据分别记录，未运行额外基准。HEAD 仍为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改全部保留，无 commit/push，持续任务未标为完成或暂停。

## 2026-10-23：合并单次帧身份查询；attempt-50 释放失败已捕获

接续第 49 次实际原生/Java 栈，ControlBridge 的 RAW_MEMORY_WALKER 首批估计由 34 调为 12。本机 JDK 17 StackStreamFactory.java 确认会预先创建该批次各 StackFrameInfo，深栈仍自动续读/扩容；这不是遍历深度上限，调用者、来源、范围及停止检查均保持。此 Java 批次统一构建成功 40s（10 executed、10 up-to-date），Bootstrap Java 实际重编译，Control Native 复用前一 26s 成功产物，其他 Java 与 Storage Native 复用，JAR/reobfJar 成功。日志 build-local-caller-frame-batch.log。

attempt-49 于 17:32:09.5274526 UTC 正常退出，退出码 0，未强杀，运行约 13 分 23 秒。两次正常关闭请求为 17:28:17.5795070 与 17:31:29.4325612 UTC；后者针对从早期显示切换后的 Minecraft 主窗口。共 12 条有界 BUSY、9 条 jemalloc VirtualFree 错误，无 TLS 拒绝或 Native 内存来源异常；首个转储为 1,337,228,629 字节。未进入世界，不声明玩家/稳定 Tick/保存通过。对应 stderr/stdout/latest/debug、线程栈、首次转储及匹配候选均已保存。

随后完整备份第 50 次实例至 backup-before-attempt-50/instance（206 目录、569 文件、500.05 MB，无失败/跳过/不匹配），27 个第三方 Mod 与原 82 项启动参数保持。部署 Core SHA256 E7679D7C92063CA06CE66FCA5B80A04D05D3DDC2A830FF2F6B006340E613C92C，匹配产物保存在 attempt-50/tested-candidate；PID 55028。首次 BUSY 在 17:35:33.2388280 UTC 触发、17:35:36.6883524 UTC 完成捕获，PRIVATE_RANGES_BUSY，writer_sample_thread=0，不能推断写锁空闲或已排除争用。17:42:16.6823384 与 17:45:12.7930791 UTC 已发送正常关闭请求；当前尚待确认终态。

继续同一实际调用链完成 native/code.c 的 CodeFrameIdentity：一次查询内持有真实声明 Class、模块及按需取得的方法/类元数据，候选筛选、完整来源读取共用这些身份；不跨调用缓存来源、权限、停止状态或 VM 方法版本。当前来源遍历、停止来源回查、sourceQueryStopped0、codeFrame0、native/code_retirement.c 的退休候选/观测及 native/bindings.c 的网络收尾均迁移。方法体/常量池/版本与歧义检查继续实时执行，结束时统一释放 JNI 本地引用和 JVMTI 字符串。删除旧重复获取/清理及不再使用的停止候选包装；独立文件映射检查仍保留必要单独查询入口。

退休的物理 producer 读取也迁移到已建立的进程生命周期 Owner 发布协议，移除 try-read 争用时漏掉已发布 Owner 的分支。完整批次统一构建成功 26s（10 executed、10 up-to-date），Control Native 实际重编译，全部 Java 与 Storage Native 复用，JAR/reobfJar 成功，日志 build-local-frame-query-identity.log；保留既有 host_completion_port.c:355 警告。此最新帧身份批次尚未部署，第 50 次运行前一个候选。静态上减少同一帧重复 VM 调用和临时元数据分配；未运行额外基准，未宣称实测提速。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部既有未提交修改保留，无 commit/push。剩余阻断仍是受限释放遇到私有范围目录争用，以及尚未完成世界运行/保存；不能用各次构建成功替代实机通过。

## 2026-10-23：隐藏 Class 实际绑定索引与来源快照收敛；attempt-49 实机结束（终态见上）

native/code.c 将实时帧查询中全部 hidden image/逐 image Class 链扫描替换为实际 Class 绑定索引：键为 JVM 返回的真实签名哈希，最终仍以 IsSameObject 核对真实 Class。签名不可用时保留未索引链；弱引用回收同步移除索引和生命周期链，旧 image->classes/imageNext 退出。code_method_selected、code_frame_roots 已迁移；未绑定 ClassPrepare、完整接受历史歧义检查、索引分配失败和挂起线程无锁回退保持原完整路径，不用隐藏类命名规则推测身份，不缓存权限。另将 producer/stopped 读取统一到已有进程生命周期 Owner 发布协议，消除 try-read 争用时漏掉已发布来源的分支。

隐藏绑定批次统一构建成功 33s，20 tasks：10 executed、10 up-to-date；Control Native 实际重编译，Java/Storage Native 复用，JAR 与 reobfJar 成功。日志 build-local-hidden-binding-index.log。随后第 49 次实机部署 Core SHA256 FA63652E01FFD0FDC79869B2E1AE40303E28FE97D1D5AD28B08D15F5E9A2FDBA，匹配符号保存在 attempt-49/tested-candidate；原 27 个第三方 Mod 和 82 项参数未改。backup-before-attempt-49/instance 基础备份 194 目录、557 文件、500.13 MB，恢复世界后补齐 11 目录、10 文件的同名世界副本；两段均无失败/跳过/不匹配。PID 50596 已启动，仍在采集本次实际结果。

第 49 次已复现 BUSY：首次捕获 17:20:17.2933170 至 17:20:20.3435097 UTC，writer_sample_thread=59784、crt_allocate。对应转储采样时 writer 已为零，线程处于 JVM StackWalker，不能冒充持锁瞬间栈。后续 Java 栈显示同一早期显示线程在 DirectByteBuffer.putFloat → Unsafe.putIntUnaligned → memoryWriteBegin0 → rawMemoryCaller；主线程等待 Forge 后台加载。隐藏索引修复不能宣称消除了 BUSY 或实测提速。

沿同一来源采集链继续完成后续批次：native/code.c 先在同一目录读锁内确定全部候选及实际是否存在来源，再将需要的完整历史一次性复制到 NativeThread 内受保护的 8 项临时区；超过容量、嵌套或没有现成线程状态时按完整数量独立分配。保留无来源但参与歧义判断的历史、真实 VM 版本/方法体/停止来源检查；没有来源候选时无需临时节点。native/bindings.c 纳入使用标记，外层生命周期仍负责线程清理。旧逐候选 calloc/链表/free 退出。native/private_memory.c 仍在写锁内分配并发布完整私有保护范围，再在返回调用者前清零 payload，移除写锁内线性清零，未把裸分配或目录发布移出保护锁。

此后续完整批次统一构建成功 26s，20 tasks：10 executed、10 up-to-date；Control Native 实际重编译，Java/Storage Native 复用，JAR/reobfJar 成功。日志 build-local-source-body-scratch.log。它尚未部署，第 49 次仍运行前一个隐藏索引候选，不转用其实机结果。静态上减少临时分配和写锁内清零工作，增加一次必要候选计数扫描；未测量性能。既有 host_completion_port.c:355 警告保留。HEAD 为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原修改保留，无 commit/push。

## 2026-10-23：收敛完整栈读取与生产者模块读侧；attempt-48 实机结束

依据 attempt-46 的 zip 解压 Native 栈和 attempt-47 的生产者集合锁等待，继续同一来源采集热路径。native/code.c 引入调用内 CodeTrace 租约，复用当前已存在 NativeThread 内的 64 帧私有缓冲区；实际数据仍受原私有分配范围保护，不把原先受保护的帧数据搬到未登记的普通栈。嵌套使用或没有线程状态时独立分配；缓冲区满时扩容并从深度零重新读取，直到得到完整栈，不把不同时间的分段拼接、不截断深栈，也不复用任何旧帧/来源/权限结果。移除该完整栈路径的 GetFrameCount 预查询及常见深度逐次分配。code_current_sources、停止来源回查、codeFrame0、退休回查及全线程退休观察全部迁移到同一接口，删除旧声明和 free 路径。依赖总深度计算底部帧身份的 code_execution_frame 保留原 GetFrameCount。

native/bindings.c 为已受保护的 NativeThread 增加固定帧存储和使用标记；标记纳入原未完成状态检查。CodeTrace 关闭只归还缓冲区并释放独立扩容块，线程状态仍由原外层回调/线程结束流程回收，避免从内部代码目录锁进入 JNI/Host 清理。失败及嵌套路径不覆盖外层缓冲区。

ControlBridge 将生产者 WeakHashMap 集合替换为单一、发布式弱引用数组。读取当前弱引用，不创建查询键或缓存角色结论；模块注册仍经原 Agent 鉴权与写入监视器，清除已回收引用并先保护新数组，再发布。与 Native 通知/注册重叠的读取退回原监视器，保留发布边界和同线程重入；通知期间若发生嵌套注册，重建到最新数组，避免覆盖内层结果。空集合判断同步迁移，旧 WeakHashMap 和普通读侧同步路径退出。弱引用生命周期与原 null 键语义保留。代价是按当前模块条目线性读取，不能仅凭去锁宣称整体提速。

本批首次统一离线构建成功 41s（11 executed、9 up-to-date）；复查后修正内部缓冲释放的生命周期边界并重建成功 29s（10 executed、10 up-to-date）；补齐生产者通知/重入发布语义后最终构建成功 25s（10 executed、10 up-to-date）。Bootstrap Java、Control Native 在本批实际重新编译；最终一次为 Bootstrap Java 重编、Control Native 复用本批 29s 的成功产物。Agent/Core/Prelaunch Java、Storage Native 复用，Core/Bootstrap 重打包且 reobfJar 成功。日志为 build-local-stack-producer-read.log、build-local-stack-producer-read-lifetime.log、build-local-stack-producer-read-publication.log，保留 host_completion_port.c:355 原警告。实际性能未测量，源代码闭环不代替实机通过。HEAD 仍为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push。

attempt-47 的失败世界已归档并恢复测试前副本后，按持续实机授权完整备份实例至 backup-before-attempt-48/instance：204 目录、565 文件、498.96 MB，无失败、跳过或不匹配；27 个第三方 Mod 未变。部署最终候选，Core SHA256 为 5219F89B71E90035FAD45F60A3F76B818315C45E62259753C8865796934C64B5，匹配 JAR、DLL/PDB 保存在 attempt-48/tested-candidate。实际 PID 27800，原 82 项参数和独立世界 db704cc 保持，首次 Native/来源失败捕获已启用。当前测试进行中，不声明资源释放、世界、稳定 Tick 或保存通过。

本次 attempt-48 已结束：首次 BUSY 于 16:27:44.4379547 UTC 触发、16:27:48.0960592 UTC 完成 1,367,047,985 字节转储；共 4 条有界 BUSY、1 条 jemalloc VirtualFree 错误，未记录 TLS 拒绝或 Native 内存来源异常。后续写入者采样仍为 crt_allocate；零采样不代表锁空闲。出生区最后约 42%，无玩家进入、稳定 Tick 或保存通过证据。17:01:45.1198219 UTC 正常关闭请求返回 true，但未完成；保留 Java/Native 栈、日志及匹配候选后，于 17:17:18.7891544 UTC 结束身份已核对的测试进程，17:17:19.3118931 UTC 退出码 -1，约 51 分 10 秒，不计正常退出通过。失败世界归档至 attempt-48/failed-world-db704cc，并从第 48 次测试前备份恢复同名世界。初次恢复因 PATH 缺少 robocopy 未执行，改用 C:\Windows\System32\robocopy.exe 完成恢复，11 目录、10 文件、无失败/跳过/不匹配；启动前同时补齐第 49 次备份的世界副本。未删除失败世界。

## 2026-10-23：修复线程来源误分类并收敛缓冲区/JNI 重复开销；attempt-47 实机结束

依据 attempt-46 的 java=2020202 与实际 FileWatcher$WatcherThread 字节码，TaskBridge 原先把“run 覆盖不属于生产者且不是已知 JDK worker”直接混入来源 unknown。现拆为 runBoundaryMissing：创建、目标、Java/Native 启动来源实际缺失仍置 unknown；已真实捕获的线程调用来源不再因为 run 入口未覆盖而被抹成不完整。retireThread、allThreadSourcesStopped、ControlBridge 的对象来源未知/单一归属/分组候选与 GAP 汇总均同步保留 runBoundaryMissing 限制，未把自定义线程当成已完整接线的 worker，也未开放自动退休或强行停线程。真实停止模块、活跃任务未知和 JNI 当前调用来源检查继续执行。

ResourceBridge 中 25 个直接 recoveryWriter 调用改为 coreWriter：其额外“直接调用者是 Agent”分支对这个 Bootstrap 调用者恒为假，移除的是重复 StackWalker 查询，不缓存真实核心写入权限。PlatformBoundary 对确切 ResourceBridge 帧直接跳过，仅对可能属于同一运行时包的候选查询真实 nest；外部调用者的 Class/方法身份校验保留。BufferBoundary 为无参数方法调用新的三参数重载，ResourceBridge 内部共用空数组并进入同一完整 bufferOperation，删除逐次零长数组分配，保留有参数装箱、来源、租约、读写、异常及 finally 收尾。

native/jni_tables.c 将实际表快照/批准来源检查抽为同一只读实现，全量刷新也先持共享锁逐个核对实际已登记环境；需要登记、来源撤销或修复时仍进入原写锁路径。native_jni_revalidate 在真实 retired 扫描结果为空时不再取得一次空写锁，真实撤销仍在写锁内核对实际地址并设置 pending。没有缓存表内容或按模块放行，JNI_TABLES_BUSY 尚不能判已消失。

完整批次统一离线 jar 构建一次成功：BUILD SUCCESSFUL in 36s，20 tasks：13 executed、7 up-to-date。Bootstrap Java、Agent Java、Control Native 实际重新编译；Core/Prelaunch Java、Storage Native 复用；Agent/Bootstrap/Core 重新打包且 reobfJar 成功。保留既有过时 API 提示及 host_completion_port.c:355 警告。日志 .work/pcl-world-20261021/build-local-thread-source-buffer-jni.log。起止 HEAD 为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原修改保留，无 commit/push。热路径减少空参数数组、重复调用者查询和空写锁；实际性能未测量。

按持续实机授权，先完整备份实例至 backup-before-attempt-47/instance：203 目录、563 文件、497.66 MB，无失败、跳过或不匹配；27 个第三方 Mod 未变。部署本次候选，Core SHA256 为 B8C32D4E653159B49D9043CA7E0593D06D738E27E12C618701B2FC78E4AAFFD3；匹配 JAR、DLL/PDB 保存在 attempt-47/tested-candidate。原 82 项参数与独立世界 db704cc 不变，实际 PID 49428，首次 Native/来源失败捕获已启用。本次 15:51:44.012 UTC 已出现 Config file forge-client.toml changed, sending notifies；对照实际 ConfigWatcher 字节码，该日志位于文件读取及校验之后，原 NATIVE_MEMORY_SOURCES_UNAVAILABLE 未复现，配置内容哈希未变。资源重载后 15:59:11 UTC 启动集成服务器，16:01:57 UTC 开始主世界出生区准备，最后仅到 11%，无玩家进入或稳定 Tick 证据。16:00:59.0061974 UTC 和 16:16:22.6515088 UTC 两次正常主窗口关闭请求均返回 true，但未退出；线程采样显示渲染线程等待旧生产者集合锁，服务器仍在出生区加载等待，不能仅凭这些采样宣称死锁。为切换到已修复候选，核对 PID/路径/启动时间并保存现场后结束本任务启动的进程，16:20:24.6640297 UTC 退出，退出码 -1，运行约 43 分 26 秒。此次不算正常退出或保存通过。失败世界移动归档到 attempt-47/failed-world-db704cc，随后从 backup-before-attempt-47/instance/saves/db704cc 恢复同名测试世界（11 目录、10 文件，无失败/跳过/不匹配）；原配置修改时间已恢复，内容未改。首个 BUSY 在 15:53:20.0073836 UTC 触发，15:53:30.5027437 UTC 完成 4,916,940,577 字节转储，仍为私有范围锁拒绝；相关 process/exit/forced-stop/restore、配置触发、失败摘要、世界关闭待处理线程栈及匹配候选均保留。日志另有账号 HTTP 401、Forge 联网超时，以及第三方 supersteve:make_cutter2 配方引用未知物品 supersteve:endoftwdr，未修改原 Mod 或账号。

## 2026-10-23：依据 attempt-45 收敛私有小块分配；attempt-46 实机结束

attempt-45 正常退出后继续同一生产链。native/private_memory.c 在原 CRT 包装器内加入上限 256 块、每块 128 字节数据区的复用表；缓存块及其头部始终保留原私有范围登记，不缓存来源/权限。命中复用不需要索引写锁；小块释放在读锁下确认真实 CRT 头后尝试归还，表满或复用锁争用时回到原实际回收路径。列表锁使用 TryAcquire，锁内不调用 CRT、目录锁或外部代码；冷分配、较大分配、重分配仍保留原写锁。calloc 对返回的请求范围清零，零长/溢出行为保持，非零 realloc 失败重新链接原块；未登记 CRT 数据的原释放分支保留。头部对齐和全部调用点已一并迁移。

该复用只保留有限的控制器自有存储，不把已释放的来源关系当作活动关系，也不扩大到业务内存。静态上短命来源链等小块在命中时省去 CRT 分配/释放与索引插入/删除；代价是最多 256 个完整私有块的保留、一次短列表锁及未命中释放时额外的读侧查找。没有声称整体实测速率提升。

attempt-45 另有配置监听线程 Unsafe.allocateMemory 的 NATIVE_MEMORY_SOURCES_UNAVAILABLE。src/bootstrap/TaskBridge.java 在原 ThreadOrigin 上记录使 unknown 置位的原因：1 创建/目标来源，2 非已知生产者的 run 覆盖，4 Java 启动来源，8 Native 启动来源；当前活跃任务的不完整来源以 16 标记。ControlBridge.nativeProcessSourceFlags 将原因置入原诊断整数最高字节，接入既有失败输出。所有原 unknown、停止与来源规则保持，不以诊断位放行，也没有新增检查脚本或探针。

完整批次统一离线 jar 构建一次成功：BUILD SUCCESSFUL in 27s，20 tasks：11 executed、9 up-to-date。Bootstrap Java、Control Native 实际重编译，其他 Java、Storage Native 复用；Core/Bootstrap 重打包，reobfJar 成功。保留 host_completion_port.c:355 原警告。日志 .work/pcl-world-20261021/build-local-private-small-thread-origin.log。起止 HEAD 仍为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改保留，无 commit/push。

沿用用户“以后要实机不用问”的持续授权，完整备份实例至 backup-before-attempt-46/instance：202 目录、561 文件、496.89 MB，无失败、跳过或不匹配；27 个第三方 Mod 不变。部署本次候选，Core SHA256 为 103590C5AC73E39267BDDD8DB38B10733CDCDC4AFF2759B263E393F9D0C10FB4；匹配 JAR、DLL/PDB 保存在 attempt-46/tested-candidate。原 82 项参数和独立世界 db704cc 不变，PID 60616 于 15:05:56.6885158 UTC 启动，首次 Native/来源失败捕获已启用。本次 15:22:53.8789475 UTC 请求正常关闭，15:22:56.1938010 UTC 自行退出，退出码 0，未强杀，运行约 17 分钟。资源重载已开始，未取得入世界、稳定 Tick 或保存通过证据。共 6 条 BUSY 记录：含一条 JNI_TABLES_BUSY，其余为私有范围预检查或最终门锁；有 3 条 jemalloc VirtualFree 错误，无 NATIVE_TLS_REFUSED。首次失败于 15:19:52.0514943 UTC 触发，15:20:02.9489798 UTC 完成 5,258,521,549 字节转储；采样写入线程 60588 在匹配符号转储中位于 zip 解压的 native_array_copy → native_capture_scope_sources_detail → code_current_sources_filtered → GetFrameCount，采样时 writer 为零，不能冒充持锁瞬间栈。一次对既有 forge-client.toml 的修改时间调整（内容哈希不变）复现配置热重载失败：java=2020202，最高字节原因仅为 2，即非生产者 run 覆盖造成的分类标记；无创建、Java/Native 启动来源失败位。退出后已恢复原修改时间，配置内容未改。对应 config-reload-trigger.json、failure-summary.json、线程栈、首次转储、匹配符号及完整本轮日志均保留。87→3 的 jemalloc 日志数量变化不能作为不同阶段/时长测试间的性能结论，也不能判释放通过；本轮仍失败，后续源码修复另行构建验证。

## 2026-10-23：补齐私有写锁失败采样并收敛调用者查询；attempt-45 实机结束

接续当前源码与 attempt-44 失败现场。本批没有重新审计整仓，保留原修改；HEAD 为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，无 commit/push。用户随后明确“以后要实机不用问”：本开发主线需要实机时可直接备份、部署和测试，不再逐次请求恢复；此授权不包含 Git 提交推送。

native/private_memory.c 将所有真实私有范围写入收敛到同一锁入口/出口，覆盖 CRT 分配、释放、重分配、虚拟内存、外部登记/注销及 host.c/jni_tables.c 的 IAT/JNI 发布。锁内只用一个原子整数发布操作类型和线程 ID，出口清零；原 SRW 锁、受限 TryAcquire 行为及保护范围不变。读锁失败时保存一次一致采样，经 bindings.c 的当前线程状态传回 host_memory.c，并立即复制到本次释放调用的局部变量，避免后续清理覆盖。现有有界 NATIVE_MEMORY_RELEASE_BUSY 增加 writer_sample_thread/site，仍只在失败路径输出并恢复错误状态。零值可能是获取/释放窗口或排队写入；非零也只证明采样时的发布者，不能冒充造成争用的完整因果证据。没有重试、等待策略变化、权限缓存或模块放行。

src/bootstrap/ControlBridge.java 沿实机 rawMemoryCaller 栈保留完整当前栈遍历，统一 bootstrapControlNest 与 HandleWriters.contains：已知顶层控制类按真实 Class 身份直接判断；不可能属于控制器/VarHandle 运行时包的类无需进入 getNestHost；仍有可能的嵌套类继续核对真实 nest、加载器和原许可集合。原始内存、Unsafe/Handle 及控制写入调用者同步迁移，Java/JNI 签名不变。静态上减少热路径不必要的嵌套类查询；写锁诊断增加两次原子存储和线程 ID 读取，读锁成功路径没有新增采样。实际性能未测量。

完整批次统一离线 jar 构建一次成功：BUILD SUCCESSFUL in 35s，20 tasks：11 executed、9 up-to-date。Bootstrap Java 与 Control Native 实际重新编译，其余 Java 与 Storage Native 复用；Core/Bootstrap 重打包，reobfJar 成功。保留 host_completion_port.c:355 既有警告。日志 .work/pcl-world-20261021/build-local-private-writer-caller.log。

attempt-45 启动前完整备份实例至 .work/pcl-world-20261021/backup-before-attempt-45/instance：201 个目录、559 个文件、496.11 MB，无失败、跳过或不匹配；保留原启动命令，27 个第三方 Mod 名称、长度及哈希未变。部署本次已构建 Core/Prelaunch，原 82 项参数与独立测试世界 db704cc 不变。Core SHA256 为 2DF41D49307D6ACA750FBD192E15C231799E55C79AC2484546EB16B0CE7A1774；匹配 JAR、DLL/PDB 保存在 attempt-45/tested-candidate。实际 PID 51372 于 14:43:38.5763417 UTC 启动，14:51:22.1313613 UTC 请求正常关闭，14:57:40 UTC 日志记录 Stopping，14:57:45.3857042 UTC 自行退出，退出码 0，未强杀。总运行约 14 分 7 秒，资源重载已开始，未取得入世界、稳定 Tick 或保存通过证据。首次 BUSY 于 14:44:41.5083845 UTC 触发捕获，14:44:46.1067640 UTC 完成 1,338,798,528 字节转储。33 条有界 BUSY 记录中 8 条写入者未观察到，其余采样为 crt_allocate/crt_free；早期采样线程 19316 对应 Forge pool-2-thread-1，较晚 Java 栈在早期显示字体顶点/DirectByteBuffer 操作中。匹配符号的转储采样时 writer 已为零，Native 栈位于 JIT 地址，不能还原持锁瞬间的完整调用链。关闭仍有 87 条 jemalloc VirtualFree 错误，无 NATIVE_TLS_REFUSED，不能判资源释放通过。另有一处 FileWatcher$WatcherThread 配置监听的 NATIVE_MEMORY_SOURCES_UNAVAILABLE，前一诊断 java=20202 指向线程来源不完整；新增来源标记和池化修复尚未在本次测试中运行。鉴权仍为 HTTPS 握手失败，Forge 联网超时；没有改账号和参数。现场包含 process/exit/candidate/close-request、首次失败 JSON/DMP、first-writer-stack.txt、threads-startup.txt、native-before-close.txt、failure-summary.json、stdout/stderr/latest/debug 日志及 tested-candidate。

## 2026-10-23：恢复源码推进；收敛类来源读取、JNI 无变化登记与私有 CRT 重复索引

用户指出不应暂停整个任务后继续当前源码开发，实机仍未恢复；没有部署、commit/push。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，保留全部原有工作区修改。本批接续 attempt-44 的私有范围锁拒绝及 threads-resource-load.txt 中 Render thread、Worker-Main-1/3/4 在 DefinitionBridge.module 等待同一 HashMap 监视器的实际记录，没有重新扫描整仓或运行新夹具。

native/private_memory.c：每个私有 CRT 分配原先把完整 header+payload 与其中的范围元数据分别插入同一 AVL 索引，两者完全覆盖重复。CRT 现在只插入完整 span，注销对应只移除该节点；malloc/calloc、realloc 成功迁移、失败恢复及 free 都经过同一对 link/unlink。外部登记和 VirtualAlloc 的独立元数据继续单独索引，所有原覆盖字节、范围判断和实际写锁边界保留。这减少真实分配/回收的树修改次数，不能据此认定所有 BUSY 已消失。

native/jni_tables.c：在持有 JNI 目录锁且取得真实完整表快照后，用统一 native_jni_ranges_match 比较当前环境/表地址、长度及登记状态。全量刷新或修复遇到不变范围时不再取得私有范围写锁；真实新登记、地址变化、注销仍使用写锁。普通读侧复用同一匹配条件，表内容、批准来源、停止状态检查和原修复路径均保留。线程退出没有任何已登记范围时省去空注销的写锁，不削弱实际注销或 JNI 目录锁。

src/bootstrap/DefinitionBridge.java：ORIGINS 改为并发映射，单类 module/origin/acceptedImage 查询与初始化存在检查不再竞争登记监视器；登记写入、弱引用队列回收及 origins 批量快照继续共用原监视器。模块身份首次绑定后不覆盖，真实定义来源检查保持。隐藏类映像合入同一 Origin 记录，以 volatile 字段在原接受回调及摘要计算成功后发布，删除 ACCEPTED_IMAGES 第二张表及其读写/发布路径。旧实现用两个不同弱引用键：类回收后，队列中的来源键无法再以 referent 相等方式删除映像表中的另一个已清空键；合并后来源和映像随同一登记项移除。Origin 属于 DefinitionBridge 的实际嵌套控制类，原 ownState 保护继续适用；JNI/Java 对外签名不变。

完整批次后统一离线 jar 构建一次成功：BUILD SUCCESSFUL in 36s，20 tasks：11 executed、9 up-to-date。Bootstrap Java 与 Control Native 实际重新编译；Core/Agent/Prelaunch Java、Storage Native 复用已有产物；Core/Bootstrap 重新打包，reobfJar 成功。host_completion_port.c:355 既有警告未改。日志 .work/pcl-world-20261021/build-local-origin-jni-index.log。没有新增测试脚本或证明体系。

上述修改直接涉及类来源、JNI 刷新和私有分配热路径，静态上减少监视器进入、无变化写锁及重复索引；实际性能未测量。新候选未部署，实机、夹具和性能测试均未执行，不能沿用 attempt-44 结果。真实写入者仍可能与受限释放竞争，尚无最新候选的失败现场证明剩余原因；后续最高价值工作仍是定位残余发布/释放竞争及资源加载瓶颈，不把构建成功当作正常运行闭环。本次未再次暂停长期目标，也未宣称整体完成。

## 2026-10-23：attempt-44 后已写入修改收尾；私有范围读锁与内存作用域复用

接续 attempt-44 的两个实际 VirtualFree BUSY 拒绝分支，收尾当前已写入的生产修改并统一构建，未再部署或启动游戏。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原有未提交修改与未跟踪文件保留，无 commit/push。attempt-44 实测候选与本次构建候选不同，前者的 FULL 或运行现象不能作为后者通过证据。

源码确认 host_allocations、host_arena_allocations、host_cpp_allocations、host_crt_allocations、host_crt_aligned、host_crt_memory 六处只查询私有范围，却使用排他锁，会与其他只读查询互斥。统一改用现有 native_private_read_lock，并对应释放共享锁；保留受限上下文的非阻塞获取、原范围/来源拒绝及异常清理。实际分配、回收、范围注册和 IAT/JNI 表发布仍使用排他锁；VirtualAlloc 的最终检查至实际效果仍持有共享锁，堆调用原有租约与异常边界保持。删除这些调用点各自重复的排他锁选择表达式，未新增旁路或按模块放行。日志确认了锁拒绝位置，但没有记录当时的具体写锁持有者，不能断言这六处就是所有失败的唯一原因。

native/memory.c 与 native/bindings.c 将标量内存操作原先每次创建/销毁 NativeMemoryWrite 的路径收敛为每线程最多一个清空容器的复用。每次仍重新取得来源、检查来源状态和真实范围；结束及开始失败时释放来源并清空全部字段，嵌套活跃操作继续正常分配。操作标识改为全局递增且溢出后拒绝的独立 token，结束、读取来源、追加贡献者三处消费者同步迁移，不再把可复用地址当作操作身份。Java/JNI 签名保持，调用方继续按不透明 long 传递。Java 线程结束及三个最终 NativeThread 回收入口均释放空闲容器；保留活跃作用域和原线程生命周期保护。

完整批次仅执行一次统一离线 jar 构建，BUILD SUCCESSFUL in 26s，20 tasks：10 executed、10 up-to-date。Control Native 实际重新编译；全部 Java 编译任务与 Storage Native 均 UP-TO-DATE；Core/Bootstrap 重新打包且 reobfJar 成功，Agent/Prelaunch JAR 复用。保留 host_completion_port.c:355 既有警告。日志为 .work/pcl-world-20261021/build-local-private-read-scope.log；沿用既有 JDK 17/Zig 及此前核实的单次额外联网预检开关，没有修改永久配置。

静态影响：六处纯读不再彼此排斥；已有空闲容器的非嵌套内存操作省去该容器的一次分配/登记及回收/注销，来源链等必要工作继续，新增一次原子 token 获取，每线程最多保留一个空容器。实际性能未测量，真实写入者仍可能竞争，不能宣称 VirtualFree BUSY 已消失。本次新候选实机、夹具及性能测试均未执行。剩余重点是实际写入/发布与受限释放之间的竞争，以及资源加载耗时；Bootstrap PARTIAL、字体空文本和网络握手问题也尚未闭环。遵守单次测试后停下要求，现在暂停，不启动第二次游戏或继续新批次。

## 2026-10-23：单次实机 attempt-44 结束；定位 VirtualFree BUSY 的两个私有范围锁拒绝位置

用户再次明确授权一次实机后，先完整备份当前实例至 .work/pcl-world-20261021/backup-before-attempt-44/instance：200 个目录、557 个文件、495.33 MB，无复制失败、跳过或不匹配；另保留原启动命令和 Mod 清单。部署上一轮已构建的 Core/Prelaunch，沿用原第三方 Mod、82 项启动参数和独立测试世界 db704cc。实测 Core SHA256 为 1F4B4DEB4C5FBEA86C6AAA9C725A9FF0B1AC8E01AE329DEFDEA97F37ECA5A898，匹配 JAR、Control DLL/PDB 保存在 attempt-44/tested-candidate。HEAD 起止均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原修改全部保留，未 commit/push。

PID 53164 于 13:29:34.5729728 UTC 启动。Agent 报 INSTALLED_FULL、Native 报 FULL，Bootstrap 仍为 PARTIAL，待接入 ForkJoinTask$Aux/FutureTask$WaitNode。13:41:47 UTC Datafixer 初始化完成，13:45:47 UTC Forge 初始化完成，13:47:38 UTC 开始资源重载；未取得玩家进入世界、稳定 Tick 或保存通过证据。早期界面另外出现 SimpleFont.stringWidth 收到空文本的 NullPointerException，之后加载继续。鉴权本次为 HTTPS Remote host terminated the handshake，公钥/资料密钥请求和 Forge 联网检查失败，不能沿用上一轮 HTTP 401 描述；原账号和参数未改。

本轮首次失败捕获在 13:32:16.9456572 UTC 由新增 NATIVE_MEMORY_RELEASE_BUSY 触发，13:32:22.2325549 UTC 完成 native-first-failure.dmp，1,371,193,399 字节。记录明确为 VirtualFree（kind=30），site=control_range，range=PRIVATE_RANGES_BUSY，restricted=1、tooling=1、loader=1、original_called=0；目标长度 65536、flags=16384（MEM_DECOMMIT）。匹配模块地址确认调用映像为 nvoglv64.dll。该失败线程 59612 在转储中已不可选择，first-busy-stack.txt 保留这个限制；分支与请求地址来自失败现场日志，不是由较晚的锁状态推断。

进入纹理资源加载后保存 Java/Native 线程栈及 release-modules.json，确认 jemalloc.dll 基址 0x00007FFF576A0000、nvoglv64.dll 基址 0x00007FFFA2B20000。13:51:04.8733854 UTC 发送正常窗口关闭请求，返回 true；13:51:11.3347672 UTC 进程自行退出，约 21 分 37 秒，未强制终止，ExitCode 为 null。关闭未复现 TextureManager.close CME、Shutdown failure 或 VerifyError，但出现 100 条 jemalloc VirtualFree BUSY，不能判正常资源释放通过。本轮没有 NATIVE_TLS_REFUSED。

有界 BUSY 诊断共输出 34 条（数量受限流影响，不代表失败总数）：4 条 nvoglv64 的 control_range/PRIVATE_RANGES_BUSY；26 条 jemalloc 的 control_range/PRIVATE_RANGES_BUSY；4 条 jemalloc 的 private_gate/NONE。所有这些记录均 restricted=1、loader=1、tooling=1、original_called=0；jemalloc 请求为 bytes=0、flags=32768（MEM_RELEASE），调用地址位于 jemalloc.dll。由此确认两种具体拒绝：native_control_range 中 native_private_read_lock 的受限 TryAcquireSRWLockShared 未取得锁；以及范围预检查已通过后，host_native_memory_release 最终实际释放前的同类读锁未取得。BUSY 是控制器在这些分支产生，原 Windows VirtualFree 未执行。没有证据支持按 DLL 名直接放行或跳过最终范围检查；仍需修复索引发布/读取与实际释放准入之间的竞争关系。

关闭前栈还显示 DefinitionBridge.module 的映射锁等待和像素读取中的调用栈检查，故不能宣称整体性能问题已解决；未进行性能基准或对比测速。本轮没有新增生产源码、接口或结构修改，也没有重复构建：全部 Java、Control/Storage Native 及整体候选复用上一轮成功构建。额外夹具、专服与基准未运行。

现场保存在 .work/pcl-world-20261021/attempt-44：process.json、candidate.json、close-request.json、exit.json、stdout.log、stderr.log、latest.log、debug.log、threads-startup.txt、threads-resource-load.txt、native-before-close.txt、release-modules.json、native-first-failure.json/.dmp、first-busy-stack.txt 及 tested-candidate。下一最高价值工作是沿已确认的两个锁拒绝位置重构私有范围发布/读侧/实际释放准入，保留完整控制范围与生命周期保护，避免受限回调因无关元数据写入被误拒绝。按本次单次测试结束后停下的约定，收尾暂停，不再启动第二次游戏、部署新候选或自动继续修改。

## 2026-10-23：收敛 JPLIS 热路径读取，并补齐内存释放 BUSY 的失败位置

接续 attempt-43 已保存的退出转储，只做源码和现有现场定位，未恢复实机。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原有未提交修改保留，无部署、commit/push。本轮没有重启游戏、专服、夹具或基准。

使用 attempt-43/tested-candidate 的匹配 PDB 读取现有转储：20 个仍链接的 JNI 状态没有保留 controlRangeFailure；两个 JPLIS 记录头部 VM 指针与 native_vm 一致，native_platform_gate/native_jni_tables_gate/native_private_gate 在该采样点均为零。native_tooling_shutdown 已为 1。该结果不能排除更早发生的锁竞争或随后被覆盖的原因，也不能确定 VirtualFree 请求地址；不将转储时的可读/无锁状态当作失败瞬间的结论。新增读取结果为 attempt-43/range-failure-layout.txt 和 range-failure-state.txt。继续沿入口核对确认 host_native_call 已为加载回调建立 toolingRestricted，未重复添加一条加载回调分支。

attempt-43/native-before-close.txt 的像素读取实际栈经过 ReadProcessMemory → code_vm_read → host_memory_range → native_memory_control → memoryWriteBegin0。native_platform_range 每次检查都通过系统调用读取已锚定的本进程 JPLIS 元数据，即使目标是普通像素地址也会执行。native/platform_structures.c 现将此调用点收敛到既有 native_control_snapshot：仍逐次读取真实字节，保留 SEH 失败处理、完整结构读取、VM 身份校验、两个实际 JVMTI 环境范围及原锁，未缓存 api/权限或跳过不可读记录。删除这一热路径上的内核读取往返；通用 code_vm_read 仍承担其他 VM 元数据读取职责，保留。

VirtualFree BUSY 尚无确定分支，因此 native/host_memory.c 为实际失败补齐有界 NATIVE_MEMORY_RELEASE_BUSY 记录，区分 sources、process_handle、control_range、private_gate、final_range、original_api，保留范围查询已有失败原因、实际地址/长度/标志、调用线程/映像、是否调用原 API 及加载/VM 回调状态。仅失败且 ERROR_BUSY 时记录，前 32 次及后续二次幂次数输出；在本函数持有的范围门锁释放后记录，并恢复 LastError、errno、_doserrno。native/host.c 将内存释放入口关联失败接入现有 NATIVE_HOST_ENTRY_REFUSED 记录，避免尚未进入释放函数的拒绝混为最终门锁失败。没有重试、放行、来源补造或权限判断变更；这些诊断用于已有的实际失败，不新增检查脚本、夹具或验收体系。

完整批次后执行统一离线 jar 构建。首次在 Control Native 编译阶段因诊断引用稍后才声明的 native_tooling_shutdown 失败，日志 build-local-platform-range-release.log；定点改用当前线程与加载回调状态后必要重试，BUILD SUCCESSFUL in 32s，20 tasks：10 executed、10 up-to-date。Control Native 实际重新编译；全部 Java、Storage Native 均 UP-TO-DATE；Core/Bootstrap 重新打包且 reobfJar 成功，Agent/Prelaunch JAR 复用。保留 host_completion_port.c:355 既有警告。成功日志 .work/pcl-world-20261021/build-local-platform-range-release-fixed.log。沿用既有 JDK 17/Zig、--offline 及已核实的单次额外联网预检开关，未改永久配置。

静态上每次 JPLIS 锚点检查少一次系统调用，实际收益未测量；故障记录只在失败路径增加工作。本轮没有实机、性能或额外测试，新候选未部署，不能沿用 attempt-43 的运行结果。VirtualFree BUSY 根因仍未确认，整体目标未完成。下一步需要在明确恢复一次实机后，用本次错误位置及原参数识别真实拒绝分支，再修复具体生命周期或范围问题；不得为消除 BUSY 直接去掉控制内存检查。

## 2026-10-23：单次实机 attempt-43 结束；关闭 CME 未复现，VirtualFree BUSY 已捕获

用户明确恢复一次实机后，先将当前实例完整备份到 .work/pcl-world-20261021/backup-before-attempt-43/instance：200 个目录、556 个文件、495.34 MB，无复制失败、跳过或不匹配；另保留原 PCL 启动命令和 Mod 清单。随后部署已完成构建的 Core/Prelaunch，沿用第三方 Mod 集合、原 82 项启动参数及独立测试世界 db704cc。实测 Core SHA256：0ACB9A45B8E52C51C424AE2A26556A02EBD99C8099A789A462D47BA22F4854B7；实测 Core/Agent/Bootstrap/Prelaunch JAR 与 Control DLL/PDB 保存在 attempt-43/tested-candidate。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原有未提交修改全部保留，没有 commit/push。

实际 PID 54092 于 12:13:10.4392245 UTC 启动。Agent 报 INSTALLED_FULL，Native 报 FULL；Bootstrap 仍为 PARTIAL，初始待接入 ForkJoinTask$Aux/FutureTask$WaitNode，随后列表新增 CompletableFuture$UniHandle。12:21:58 UTC 完成 Datafixer 初始化，12:24:34 UTC Forge 初始化完成，12:25:56 UTC 开始资源重载。原账号仍报 HTTP 401、资料密钥请求及 Forge 联网检查失败，未更改原账号或参数。没有玩家入世界、稳定 Tick 或保存通过证据。

启动及资源加载各采集一次 Java/Native 线程现场。关闭前纹理线程位于 MipmapGenerator、ItemModelGenerator 的像素读取，经 NativeControl.memoryWriteBegin0、rawMemoryCaller 等原内存检查；渲染线程仍执行早期界面缓冲区写入。采样没有再出现 attempt-42 那组 CONTROLS/STREAMS 等待，但单次栈不能证明整体消除竞争，也没有进行性能基准或宣称提速。资源加载仍未完成；这次关闭针对加载阶段的提前退出路径，不代表完整世界生命周期测试。

12:30:14.3342384 UTC 发送正常窗口关闭请求，返回 true；日志 12:30:14.733 UTC 记录 Stopping。进程于 12:30:35.5744476 UTC 自行退出（约 17 分 25 秒），未强制终止，监控 ExitCode 仍为 null。本轮日志未出现 TextureManager.close 的 ConcurrentModificationException、Shutdown failure 或 VerifyError；这是本次未复现结果，不宣称关闭的所有并发路径已经通过。

退出阶段出现 52 条 <jemalloc>: Error in VirtualFree(): 请求的资源在使用中。本轮未出现 NATIVE_TLS_REFUSED。启动前段已启用首次失败捕获，12:30:18.3994700 UTC 由第一条 VirtualFree 错误触发，12:30:34.8493731 UTC 写完 native-first-failure.dmp（5,984,822,680 字节），并保留触发记录 native-first-failure.json。匹配实测 PDB 的栈读取显示主线程已在 JVM_Halt，另有 Native 退休线程和仍在来源查询的资源线程；部分线程在转储写入期间退出。当前栈没有直接保留具体 VirtualFree 拒绝分支和请求地址，不能把 BUSY 擅自解释成某个门锁或放宽放行条件，整体正常退出/资源释放仍未通过。

本轮没有新增生产源码或结构修改，也没有重复构建。Java、Control/Storage Native 及整体候选全部复用上一轮成功构建，不能写成本轮重新编译。已保存 attempt-43 下的 process.json、candidate.json、close-request.json、exit.json、stdout.log、stderr.log、latest.log、debug.log、threads-startup.txt、threads-resource-load.txt、native-startup.txt、native-before-close.txt、failure-stacks.txt、首次失败转储及 tested-candidate。没有额外夹具或基准。

下一最高价值工作是利用此次退出现场定位 VirtualFree BUSY 的准确范围/租约/退场拒绝条件，并继续处理像素读取路径的实际检查开销。按用户这一次测试结束后停下的要求，收尾后暂停，不再部署、启动第二次游戏或自动继续生产修改；整体目标未完成。

## 2026-10-23：修复纹理关闭遍历内的延迟注册路径

接续当前候选及 attempt-42 的 TextureManager.close:178 / HashMap.forEach CME。本轮没有重启游戏，没有运行夹具或额外基准，没有部署、commit/push。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原有未提交修改全部保留。上轮来源查询复用与读锁修复属于生产进展，本轮沿剩余关闭调用链继续。

定点查看实例实际 client-1.20.1-20230612.114412-srg.jar 中 TextureManager、AbstractTexture、DynamicTexture、MissingTextureAtlasSprite 的方法字节码，发现确定的同线程重入修改路径：TextureManager.close 对 byPath 执行 Map.forEach；safeClose/m_118508_ 首先调用 MissingTextureAtlasSprite.getTexture/m_118080_；后者在单例尚未创建时生成 DynamicTexture，再调用当前 Minecraft 的 TextureManager.register/m_118495_，向 byPath 执行 put。提前关闭仍在加载的客户端时，这条延迟初始化能够在 forEach 内改变同一张表。方法读取结果保存于 .work/pcl-world-20261021/attempt-42/texture-close-bytecode.txt。该证据证明生产路径存在缺陷，但日志没有保存失败时单例字段值，仍不能声称已证明本次 CME 的唯一运行时修改者。

src/agent/ClientBoundary.java 增加 textureClose 变换并接入原 TextureManager.close 分支：从实际 safeClose 方法调用取得命名/运行映射后的缺失纹理获取入口，在原 Map.forEach 前对实际栈上的 Map 做 isEmpty 判断，非空时先执行同一获取入口。保留原 Map 和 BiConsumer；空表不创建缺失纹理；原逐项 safeClose、共享缺失纹理的特殊处理、releaseId 及后续 clear 全部保留。没有集合快照、异常吞掉、按 DLL/Mod 放行、新生命周期旁路或新的 Java/JNI/Native 接口。既有 ControlClassWriter 仍计算帧和最大栈，RecoveryAgent 原接线继续运行后续字段与创建边界。没有已被替代的重复清理实现需要删除，原 safeClose 获取入口还承担其他注册/替换时的必要身份判断，保留。

完整修改后统一执行 gradlew.bat jar --offline --console=plain --max-workers=4，使用已有 JDK 17/Zig 和已核实的单次额外联网预检开关。BUILD SUCCESSFUL in 15s，20 tasks：10 executed、10 up-to-date。Agent Java 实际重新编译并重新打包；主/Bootstrap/Prelaunch Java、Control/Storage Native 均 UP-TO-DATE；Core 重新打包且 reobfJar 成功，Bootstrap/Prelaunch JAR 复用。保留 RecoveryAgent 既有弃用提示。日志 .work/pcl-world-20261021/build-texture-close-order.log。

变更仅在关闭路径增加一次空表查询及非空时的初始化获取，不改变渲染/资源读取热路径，不宣称性能提升。实机、性能与额外夹具测试未执行；本轮没有执行变换后游戏类的加载验证，构建成功不能替代实际关闭结果。当前候选同时包含前两批资源锁与来源查询修复，尚未部署。下一步需要在恢复实机授权后验证实际加载、提前关闭及正常退出，确认这条重入路径和剩余异步修改行为；TLS 历史拒绝及世界/保存验收仍未宣告完成。


## 2026-10-23：收敛来源查询重复登记与控制标记读锁

接续 attempt-42 的资源加载现场；本轮没有启动游戏、专服、夹具或基准，没有部署或 commit/push。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原未提交修改全部保留。上轮属于实际进展：完成单次运行取证及 critical(null) 修复，本轮从剩余来源登记锁竞争继续，没有重新全仓库审计。

threads-resource-load.txt 中多个纹理工作线程在 CodeSourceBridge.protect → mutationControls → ControlBridge.beginSourceQuery 等待 CONTROLS，另有调度线程在 fieldGateMetadata 等待同一锁。源码确认每次外层查询都创建 SourceQuery，随后把查询容器、同一 ThreadLocal 和 partial 再次批量登记；批量登记还构造并保护临时发布数组并执行 Native 通知。查询结束又移除 ThreadLocal，下一次重复整个过程。

ControlBridge 现按线程复用已经登记保护的 SourceQuery 容器，每次开始、结束和初始化失败均清空 partial、physicalComplete 与活动状态；每次仍重新读取真实动态来源和调用栈，不缓存来源或许可结论。新产生的 partial 数组仍单独保护并保持不可变快照，结束只断开容器引用，不修改返回数组，也不让空闲容器持有上次模块。currentSourceQuery 统一过滤活动状态，普通/重入查询、停止判断、单来源选择、Native 来源读取及诊断标记均完成迁移；线程及结束顺序检查保留，初始化失败移除容器，正常退出不再走创建/登记/移除的重复路径。

CodeSourceBridge 的 executionList、executionMap、fieldGate 三个精确对象角色位改为 volatile，读取复用 ControlRegistry 既有 volatile 表及链节点发布机制，删除三处读监视器。注册、角色赋值、重建表和弱引用清理仍由原写监视器串行执行；角色位只从 false 变为 true，重建表复制角色后发布，未改为按类、名称或历史权限放行。角色登记仍在构造方公开对象前完成；实际调用方鉴权、停止及生命周期检查保留。没有 Java/JNI/Native 接口变化。

修改写齐后统一执行 gradlew.bat jar --offline --console=plain --max-workers=4，使用原 JDK 17/Zig，沿用已核实的单次 -Dnet.minecraftforge.gradle.check.certs=false 额外联网预检开关，未写入永久配置。BUILD SUCCESSFUL in 22s，20 tasks：10 executed、10 up-to-date。Bootstrap Java 实际重新编译；主 Java、Agent/Prelaunch Java、Control/Storage Native 均 UP-TO-DATE；Core/Bootstrap 重新打包且 reobfJar 成功。日志 .work/pcl-world-20261021/build-source-query-reuse.log。新构建未部署，实机及性能测试未执行。

静态上，同一线程后续查询省去容器分配、重复控制登记及正常 ThreadLocal 移除；三个只读角色查询不再排队等待 Native 通知期间的写锁。实际加速幅度和资源重载是否恢复正常尚未测量，不能沿用 attempt-42 结果证明本候选通过。关闭调用链定点核对未发现 TextureManager.close 直接调用 Ronova 的 detachTexture；现有现场仍缺少 byPath 的实际修改者，不能仅凭 CME 推断并替换其生命周期。下一最高价值工作是对新候选的实际资源加载与关闭路径取得证据，继续定位真实修改者及剩余开销；目前实机暂停，TLS 历史拒绝及世界/保存验收均未宣告解决，整体目标保持未完成。


## 2026-10-23：单次实机 attempt-42 已结束；未复现 TLS 拒绝，修复空接收对象的资源锁竞争

用户恢复一次实机授权后，先将原实例完整备份到 .work/pcl-world-20261021/backup-before-attempt-42/instance（199 个目录、554 个文件、494.54 MB，无复制失败），另保留原启动命令及 Mod 清单。随后部署上轮统一构建的 Core/Prelaunch 候选，沿用原第三方 Mod 集合和 82 项启动参数，启动既有独立测试世界 db704cc。实测 Core SHA256 为 11D401FB8CC5D41C39ABD442B2F9D6CBE293621A8C1C11F3FB076D97AF1094F0；对应 JAR、Control DLL/PDB 已保存在 attempt-42/tested-candidate。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，保留全部原有未提交修改及 launch.json、bin/，未 commit/push。

实际 JVM 为 PID 52576，11:03:35.9667319 UTC 启动，11:28:53.6298663 UTC 记录退出。Agent 报 INSTALLED_FULL、Native 报 FULL，Bootstrap 仍为 PARTIAL，ForkJoinTask$Aux/FutureTask$WaitNode 待接入。Forge 初始化和资源重载已开始，没有玩家进入世界、稳定 Tick 或保存通过的证据。原账号鉴权返回 HTTP 401，其他联网请求超时，未更改账号或启动参数。

启动早期即启用 stderr 首次 NATIVE_TLS_REFUSED/VirtualFree 失败捕获，直至进程退出均未触发，因此本次没有对应失败转储，不能据此宣称旧失败已经解决。11:28:50.6861262 UTC 发送正常窗口关闭请求，进程自行退出，未强制终止；退出再次出现 TextureManager.close:178 / HashMap.forEach 的 ConcurrentModificationException，ExitCode 为 null，不能算正常保存退出通过。现有栈没有记录实际集合修改者，未用无依据的快照或放行修改改变关闭生命周期。

本轮 Java/Native 线程现场显示资源加载期间多个纹理工作线程及渲染线程争用来源登记和资源表锁。其中实际 Unsafe.putInt/putIntUnaligned 的原生地址写入经 unsafeWriteAllowed → ResourceBridge.unsafeMutationAllowed → critical(null)，原实现即使没有堆接收对象仍进入 STREAMS 锁。现仅在 ResourceBridge.critical 的空接收对象分支立即返回 false；各类型判断原本也均对 null 返回 false。非空对象继续查原资源表；原生地址的来源、范围、停止状态和生命周期检查仍在原内存边界执行。删除空对象进入资源表锁的无效路径，不涉及 Java/JNI/Native 接口变更。来源登记锁的竞争仍存在，未将这次局部消除报告成加载瓶颈全部解决。静态上每次该空接收对象查询少一次全局监视器获取，未测量实际提速。

代码写齐后执行一次 gradlew.bat jar --offline --console=plain --max-workers=4，沿用已有 JDK 17/Zig 和上轮已核实的单次 -Dnet.minecraftforge.gradle.check.certs=false 额外联网预检开关，未更改永久配置。BUILD SUCCESSFUL in 22s（20 tasks：10 executed、10 up-to-date）。Bootstrap Java 实际重新编译；主 Java、Agent/Prelaunch Java、Control/Storage Native 均 UP-TO-DATE；Core/Bootstrap 重新打包且 reobfJar 成功。日志 .work/pcl-world-20261021/build-attempt42-resource-lock.log。新构建未部署、未启动，不借用本轮旧二进制的实测结果。额外夹具、基准和性能测试未执行。

现场保存在 .work/pcl-world-20261021/attempt-42：process.json、candidate.json、close-request.json、exit.json、stdout.log、stderr.log、latest.log、debug.log、threads-startup.txt、threads-resource-load.txt、native-startup.txt、native-before-close.txt 及 tested-candidate。下一最高价值工作为沿当前资源加载的实际来源登记锁竞争收敛重复登记与查询，并定位关闭阶段集合的实际修改者；TLS 拒绝需保留未验证状态。按用户本轮测试结束后先停一下的要求暂停，不再自动启动第二次游戏。


## 2026-10-23：补齐租约修复的离线统一构建；运行验证仍暂停

接续上一轮已写齐且单独通过 Control Native 编译的 TLS/worker/回调租约修复，本轮没有新增生产源码改动。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原工作区修改全部保留。首先用同一 JDK 再核对 Forge HTTPS，仍返回 SSLHandshakeException: Remote host terminated the handshake，没有将网络状态报告为恢复。

定点读取本机缓存的 ForgeGradle 6.0.54 EnvironmentChecks 实现，确认 check.certs 只控制插件初始化时对 Forge/Minecraft 仓库的两次额外 HEAD 连通性预检；该开关不安装 TrustManager、不改证书信任库，也不改变 Gradle/JDK 实际 HTTPS 请求的校验。本次仅在 --offline 构建命令中使用 -Dnet.minecraftforge.gradle.check.certs=false 跳过这项联网预检，未写入 gradle.properties 或其他永久配置，没有替换依赖缓存。实现依据保存在 .work/pcl-world-20261021/forge-environment-checks.txt。

执行 gradlew.bat jar --offline --console=plain --max-workers=4，附上述单次预检开关及原 controlZig/storageZig 路径，BUILD SUCCESSFUL（18 秒，20 tasks：10 executed、10 up-to-date）。Control Native 实际重新编译；主 Java、Bootstrap、Agent、Prelaunch Java 和 Storage Native 均 UP-TO-DATE；Core/Bootstrap 重新打包且 reobfJar 成功，Agent/Prelaunch JAR 复用。本轮日志 .work/pcl-world-20261021/build-local-known-leases-offline.log。上轮失败日志和隔离 Native 结果继续保留，不再将整体构建标为未完成。

本轮不涉及新的结构或性能修改，不宣称提速。没有启动游戏、专服、旧夹具或基准，没有部署、commit/push。现有记录仍缺少失败调用瞬间的全部来源锚点，TextureManager.close 的 CME 也没有实际修改者记录；不能据此继续猜测放行条件、清除历史 unknown 或替换关闭生命周期。当前完整候选已生成，实机、稳定进世界及正常保存退出未通过，整体目标保持未完成。

## 2026-10-23：继续收敛 TLS/worker/回调租约；Control Native 通过，整体构建被 Forge HTTPS 阻断

从 IDE 修复后的实际工作区继续生产主线，未重做全仓库审计。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，保留所有原有修改及 IDE 配置、launch.json、bin/。本批继续修改 native/host_tls.c、native/host_thread_workers.c、native/host_module_admission.c、native/bindings.c，未修改 Java/JNI 对外签名。

attempt-41 的 source_lease 拒绝同时记录 caller unknown=0 与 TLS 历史 unknown 位。源码中组合借用先合并整份历史，再因历史 unknown/partial 拒绝全部引用，无法利用其中真实完整的已知来源租约。现用 host_tls_retain_known 按本次缺少的 Owner 身份选取仍存活、完整、已知且未停止的租约，在 host_tls_records 内增加实际引用；线程、fiber 和当前 FLS 回调槽的已保留引用走同一选择逻辑，剩余身份仍必须由真实 load/worker/tooling 锚点覆盖。原历史 unknown/partial、回调数据来源、GAP 和退场条件均未清除；裸 OwnerLink、部分租约及未知调用不能因此变成完整租约。全部身份覆盖及停止状态在发布前复查，失败路径和正常归还继续释放每一份实际引用。删除借用路径的整份历史合并，数据历史合并仍保留在其原职责中。

沿同一链继续修复两个下游漏接。worker 原可通过 sourceLoad/sourceTlsAnchor 完整创建，但子 worker 借用只接受 sourceLease/sourceAnchor，加载/退出借用也遗漏 sourceTlsAnchor。现将三处保留收敛到 host_thread_worker_load_borrow_mode，承认全部实际锚点，删除重复覆盖函数和退出分支；普通路径及退出 tryOnly 锁语义保持各自原行为。回调租约原仅接受 tooling 链顶与 invocation 顶层完全相等，真实加载/释放作用域或嵌套回调会遮住尚未结束的外层回调。现沿本线程实际 scope/invocation 链选择完整锚点，统一 slot hold；仍校验 admitted/active、环境退场、unknown、来源覆盖和停止状态，不搜索历史槽位。

现有转储的失败线程 40600 已不在转储线程表，部分已结束线程的 observed 已被正常清理；转储晚于拒绝日志，不能重建失败瞬间的完整锚点集合。本批修复的是调用链中可确定的丢弃完整租约和漏接问题，不宣称两条实机拒绝已消失。关闭阶段 TextureManager.close 的 ConcurrentModificationException 仍缺少实际修改者证据，未用无依据的集合快照替换原生命周期。

代码写齐后执行一次 gradlew.bat jar（已有 JDK 17/Zig、离线依赖），在 ForgeGradle 初始化阶段失败：maven.minecraftforge.net 的 HTTPS 校验未完成。使用同一 JDK 的直接 HTTPS HEAD 确认 SSLHandshakeException: Remote host terminated the handshake；TLS 1.2 也失败。未关闭证书校验、修改系统代理或在条件不变时重复 Gradle。日志 .work/pcl-world-20261021/build-local-known-leases.log。

随后仅用已有 native/build-control.ps1 对本批 Native 代码进行隔离编译/链接，成功；产物位于 .work/pcl-world-20261021/native-known-leases，日志 build-native-known-leases.log，保留 host_completion_port.c:355 既有警告。Java/Storage Native 本轮未执行，完整 JAR 未生成，不把旧产物或局部 Native 成功当作整体成功。没有启动游戏、专服、夹具或性能基准，没有部署、commit/push。静态上 TLS 借用只保留覆盖所需身份的完整租约，达到覆盖后停止扫描；回调只扫描真实活动链。此为来源生命周期修复，未测量提速。下一步先在 Forge HTTPS 恢复后完成统一构建，再继续缺失的实际运行证据；整体目标未标完成。

## 2026-10-23：修复 VS Code Java/C 工程识别；运行继续暂停

本轮只处理编辑器工程与工具配置，不修改任何生产源码或包名。Java 日志确认 Gradle 导入找不到 Java 17，原功能目录又不匹配 JDT 要求的包路径。现明确设置 JavaSE-17，并通过 .vscode/ide.gradle 的 ronovaIde 任务生成独立 Eclipse 链接工程：源码仍为原文件，Gradle 提供真实依赖，journal 中混放的两种包名逐文件映射。仅关闭 Java 扩展对同一源码的重复 Gradle 导入，实际生产 Gradle 配置与构建流程不变。重新生成使用 VS Code 任务 Ronova: Refresh Java project。

Native 配置改为 JNI include、Windows x64、实际 Control/Storage 翻译单元和 Zig 编译器查询。cpptools 无法直接查询 zig cc 或批处理入口，且其 nul 查询输入被 Zig 0.13 判为 FileNotFound；.vscode/zig-driver.c 是仅供编辑器的可执行适配器，转发原参数/退出码，将空设备查询改用真实空文件，并为 C++ 查询选择 Zig c++。.vscode/prepare-c-ide.ps1 生成适配器和 compile_commands.json，对应任务 Ronova: Refresh C project。host.c 按 control.c 的真实包含关系解析。cpptools 1.35.4 的 C23 模式对同位置自引用结构体产生 3799 冲突，最终仅编辑器 C 解析使用 gnu17 兼容模式，生产 native/build-control.ps1 保持 gnu23，C++ 保持 gnu++23。没有屏蔽错误诊断；无效的缓存关闭尝试及临时 Debug 日志设置均撤回。

实际验证：已安装的 JDT LS 完整语义检查收到 53 个文件/项目诊断条目，错误 0，非工程/仅语法检查提示 0；ClientGuard.java、NetworkBoundary.java 诊断为空。实际 VS Code C/C++ 扩展最终 control.c、host.c、storage.cpp 均为 0 条错误，普通警告/提示保留。仅运行 IDE 元数据任务、适配器编译及语言服务诊断，没有重新构建游戏 JAR、启动游戏、部署、commit 或 push。起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，原生产修改、launch.json 和 bin/ 保留。此前 Native/TLS 实机问题未因本次编辑器修复而改变，继续暂停。

## 2026-10-23：单次实机 attempt-41 已结束；保留 Native 失败现场并收敛内存授权路径

按用户本轮授权，先完整备份原实例到 .work/pcl-world-20261021/backup-before-attempt-41/instance（198 个目录、552 个文件、493.92 MB，复制无失败），另保留原 PCL 启动命令和 Mod 清单。随后仅替换当前 Core/Prelaunch 候选并重新生成 Ronova 启动交接；沿用原第三方 Mod 集合与 82 项启动参数，启动既有独立测试世界 db704cc 的 attempt-41。本轮仅启动这一次，未 commit/push。HEAD 起止均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，全部原有未提交修改保留。

实际进程 8164 于 06:58:48 UTC 启动，07:25:32 UTC 记录退出。Agent 报 INSTALLED_FULL，Native 报 FULL，但 Bootstrap 仍为 PARTIAL，ForkJoinTask$Aux/FutureTask$WaitNode 尚待接入；不能合并报告为全部 FULL。加载达到 Forge、渲染与字体资源阶段，没有玩家进入世界或稳定 Tick 的通过证据。正常窗口关闭请求后进程自行退出，未执行强制终止；退出日志出现 TextureManager.close 的 ConcurrentModificationException，监控 ExitCode 为 null，不能算正常保存退出通过。原账号鉴权另报 HTTP 401，Forge 更新请求超时，均保留原参数未改动。

本次复现两条 NATIVE_TLS_REFUSED，均为 kind=37、site=source_lease、restricted=1、unknown=0、producer=1、lease_complete=0。对应调用映像为 JNA 与 nvapi64_impl：前者 selected=0，后者选中 0x20c6503b530。匹配实测 DLL/PDB 的转储显示后者加载记录当时 unknown=0、leaseComplete=1、retired=0、releasing=0，并有实际 parentLoad/sourceParent。该记录自身完整不证明覆盖失败调用的全部来源；转储晚于拒绝日志，不能据此把瞬时借用失败归因于单一门锁。TLS 历史 unknown 与组合来源覆盖仍需定位，不按 DLL 名放行或清除 unknown。本轮不将旧 VirtualFree BUSY 判为已修复。

实际 Java/Native 线程栈同时定位了标量 Unsafe/DirectByteBuffer 写入的重复授权开销：native_memory_authority 先走八帧 JVMTI 前缀查询，而该快速选择仅接受 ResourceBridge/CodeSourceBridge，实际 Unsafe 写者还要回退到完整 rawMemoryCaller。现删除 native/memory.c 中前缀选择、重复方法缓存与初始化；native/bindings.c 移除对应准备接线；src/bootstrap/ControlBridge.java 删除 rawMemoryCallerClassFast 及其登记项。保留每次入口的真实 controller 身份校验、原完整 Java 调用栈选择，以及全部来源、范围、停止、生命周期检查；native_unsafe_prepare 仍负责解析并校验原 rawMemoryCaller 方法。无权限缓存或安全范围放宽。该改动影响实际内存热路径，静态减少一次 JVMTI 前缀遍历；未测量实际提速。

此次修复写齐后仅统一构建一次：gradlew.bat jar 成功，1 分 11 秒。主 Java、Bootstrap Java 和 Control Native 实际重新编译；Agent/Prelaunch Java 与 Storage Native 为 UP-TO-DATE。Core/Bootstrap 重新打包且 reobfJar 成功，Agent/Prelaunch JAR 复用。保留 14 条 Java 弃用等警告及 host_completion_port.c:355 既有比较警告。日志为 .work/pcl-world-20261021/build-local-memory-authority.log。此新构建未部署、未实机运行，不能借用 attempt-41 的旧二进制结果；额外夹具、基准与性能测试未执行。

现场位于 .work/pcl-world-20261021/attempt-41：stderr.log、latest.log、debug.log、threads-startup.txt、threads-final.txt、native-startup.txt、native-final.txt、native-failure.dmp（5,673,675,088 字节）、failure-types.txt、failure-owners.txt；tested-candidate 保留实测 Core/Bootstrap JAR 与 Control DLL/PDB。文件名 final 表示该次采样命名，不代表正常结束验收。下一最高价值工作为借助现有转储继续追踪 TLS 全部来源的完整锚点覆盖，随后处理关闭阶段集合修改异常。依用户“这轮测试结束后先停一下”，本轮收尾后暂停，不再重启或部署新候选。

## 2026-10-23：回调强租约与 TLS 组合来源连续修复

HEAD 保持 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，保留全部既有未提交修改。上次中断前已完成回调租约生产批次：普通/扩展/heap 强引用完整性由同一准备入口发布，heap worker 消费同一 sourceLeaseComplete，删除独立 heap 字段和重复检查；通用借用直接使用当前 invocation/heap anchor，删除历史槽位搜索及对普通/扩展/ThreadEnd 不适用的额外环境标记前提。Host/tooling 采集复用完整纯 C 注册链，NULL 物理调用地址不再扫描映像目录。该批 build-local-callback-leases.log 记录统一构建成功（20 秒），本次未重跑。

继续完成 TLS 组合来源：实际选中加载关系贯穿 TLS/模块/退出/worker 的后备保留，TLS 已持有一部分来源时，只对缺少部分借用真实选中/活动关系；所有接口声明和调用点同步迁移，已有 unknown/partial、停止及生命周期检查保留。worker 起始地址来源查询失败或未知时保留 GAP，不能被父/TLS 或新建全局引用重新解释为完整；已知且没有 child receipt 的 JNI-only 后备继续可用。模块和 worker 使用共用 native-only 判定，补齐 toolingRestricted/loader 窗口的 JNI 租约创建限制。

最新完整生产批次 gradlew.bat jar 成功，32 秒，日志 .work/pcl-world-20261021/build-local-composite-leases.log。Control Native 实际重新编译；所有 Java 编译与 Storage Native 复用 UP-TO-DATE 产物；Core/Bootstrap 重新打包及 reobfJar 成功，Agent/Prelaunch 复用。保留 host_completion_port.c:355 既有警告。没有新增验证脚本、commit/push、分发更新或部署。

实机、世界、夹具和性能测试未执行；未宣称旧 TLS 拒绝、VirtualFree BUSY 或出生区停滞已运行修复。静态删除重复搜索不等于实际提速。持续目标保持未完成，下一定位受具体 Native 失败现场缺失及实机暂停约束，不能用源码/构建代替正常进世界及保存退出证据。

## 2026-10-22：退出租约并发收口与最终统一构建

基线仍为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，保留起始十五个未提交文件，结束十六个。接续源码核对找到实际遗漏：HostModuleRelease 清理在 host_imports 下认领零借用记录，与 TLS/嵌套加载使用的 lifetime 借用门不同。现清理认领、失败状态及退役发布全部使用同一 lifetime 独占门，释放来源前撤销 leaseComplete；关闭见证句柄失败后可以重试清理，但不能再借用已释放的租约。父借用归还走共用 drop，受限退出候选选择走共用借用入口的 tryOnly 模式，保留不等锁、来源覆盖、停止、映像生命周期及后备锚点检查。

所有内部调用方与退出路径已迁移，删除退出路径原先独立的候选选择和直接计数增减；Java/JNI 对外签名无变化。前批 Native 区间索引、共享读门、来源转换和实际 parentLoad 接线均保留。此为源码闭环修复，不是新运行记录；没有新增或运行测试脚本。

完整代码写齐后统一 gradlew.bat jar 成功，24 秒；Control Native 重新编译，所有 Java 编译及 Storage Native 复用 UP-TO-DATE 产物，Core/Bootstrap 重新打包和 reobfJar 成功，Agent/Prelaunch 复用。既有 host_completion_port.c:355 比较范围警告保留。日志：.work/pcl-world-20261021/build-local-release-lifetime.log。未提交、推送、部署或更新分发目录。

本批生产代码、接线与构建交付完成。实机/世界/夹具/性能测试未执行；VirtualFree BUSY、无完整来源锚点的 TLS 拒绝、世界稳定运行与正常保存退出仍未验收，不沿用旧候选结果。新增退出同步不作为性能收益宣称。

## 2026-10-22：持续推进内存控制与来源租约；本地统一构建

起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，保留起始五个未提交文件。沿上轮 Native 控制区查询主线完成四项关联修改：只读目录及普通内存效果迁移到统一共享门，登记/分配/退役仍独占；虚拟释放注销复用 AVL 精确分配查询并删除旧分配双向总链；内存/进程/网络的 Module[] 来源转换合并，已知永久身份免目录写锁；无 Java 环境的加载来源保留补接实际 parentLoad，并与 TLS 共用完整候选借用流程。租约读者使用共享门和原子借用计数，退役独占与完整来源覆盖检查保留。

Host Win32/NT、JNI/JVMTI、事务 I/O 和完成端口调用方已同步迁移，最终锁内区间复查仍贯穿实际复制/保护/释放/输出。删除相邻重复 NT 查询、独立 tooling 输出锁包装、重复来源转换及重复加载候选选择；保留平台结构/JNI 实时查询、未知来源失败、停止状态、原始错误/部分效果、Win32 兼容回退及 worker/tooling/TLS 生命周期后备。没有增加许可缓存、DLL 白名单或检查脚本。

全部生产修改写齐后一次 gradlew.bat jar 成功，42 秒；Control Native 实际重新编译，所有 Java 编译和 Storage Native 为 UP-TO-DATE。Core/Bootstrap 重新打包及 reobfJar 成功，Agent/Prelaunch 复用。既有 host_completion_port.c:355 类型范围比较警告仍在。日志：.work/pcl-world-20261021/build-local-memory-lifetime.log。没有 commit/push、分发复制、PCL 部署或启动。

实机/世界/夹具/性能测试：未执行。性能结论仅为静态减少独占读者冲突、总链搜索、重复查询和已知身份写锁，不宣称实际提速或世界恢复。父租约漏接已修源码，但旧 source_lease、selected_unknown 和 VirtualFree BUSY 未据此标为解决。现存 attempt-40 的 BUSY 文本无法区分原始系统调用失败、控制范围未知及最终门锁竞争；selected=0/lease_complete=0 的 TLS 记录也不能凭空补出真实锚点。本批已确认调用链可实施修改完成，后续根因选择受具体 Native 失败现场缺失阻断，遵守实机暂停，不自行运行以补证据。

## 2026-10-22：本地 Native 控制区查询收敛；仅统一构建

起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d。起始 ClientBridge.java、A 批状态页及本报告已有未提交修改，全部保留。本轮生产修改仅 native/control_ranges.c、native/host_memory.c：统一私有区间索引已覆盖的 Owner/Binding、JVMTI 私有对象/生成入口、heap walking 栈范围、Host 历史对象/来源链/活动 IAT，不再重复遍历各自目录。移除该查询的四把目录读锁，JVMTI 外部环境仍按真实跨度查询，经 acquire 读取已发布且永久保留的记录，保留 mutation 及退场状态检查。JNI/平台实时读取、来源停止、整页/溢出和实际写入前锁内索引复查未改。

依据为 attempt-40 已保存渲染线程的 memoryWriteBegin0 调用栈及其生产调用链；仅据此确认重复查询开销，未证明它是世界停在出生区 0% 的唯一原因。没有新增缓存、注册协议、JNI 接口或测试脚本。

统一执行 gradlew.bat jar：首次因沙箱无法访问既有 Gradle 缓存锁退出，未编译；提升权限后同参数离线构建成功，27 秒。Control Native 重新编译成功，host_completion_port.c:355 既有比较范围警告仍在；所有 Java 编译和 Storage Native 为 UP-TO-DATE，复用已有产物。Core/Bootstrap 重新打包并完成 reobfJar，Agent/Prelaunch 复用。日志：.work/pcl-world-20261021/build-local-control-range.log。仅更新 build 产物和工作区源码/两份现有报告，无 commit/push、分发更新或 PCL 部署。

实机、世界、旧夹具及性能测试：未执行。实际提速、并发运行效果、VirtualFree BUSY 消失、来源/租约缺口解决和正常保存退出均未宣称通过；下一优先从已有 Native 拒绝现场定位 VirtualFree BUSY 的具体失败分支。

## 2026-10-22：本地顶点快照批量读取；仅统一构建

起点 eb26c77，工作区原先干净。本轮仅修改 ClientBridge.GpuImages：将顶点增量、保留快照、CPU/GPU 内容比较及 CPU 清除后的邻居检查中的逐字节 ByteBuffer.get，迁移到共用的绝对位置批量读取。真实读范围仍经过已有 ResourceBridge/ControlBridge/Native 复制与来源路径，快照副本沿用弱控制保护；原批次、线程、缓冲身份、停止来源、目标选择和读回规则保留。没有改 JNI/Native 接口或增加权限豁免。

实际执行 gradlew.bat jar，23 秒成功。Bootstrap Java 本轮重新编译，Core/Bootstrap 重新打包并完成 reobfJar；主 Java、Agent、Prelaunch、Control Native 和 Storage Native 均为 UP-TO-DATE，未重新编译 Native。既有 Prelaunch JAR 复用；本轮产物仅位于 build/libs，未复制到分发目录或 PCL，没有 Git commit/push。

没有执行游戏、世界、夹具或性能测试。按源码调用次数可判断逐字节缓冲入口已消除，但比较会新增临时堆副本，不宣称实际提速或内存改善。本轮不能提升旧世界验收状态；出生区 0%、VirtualFree BUSY、来源/租约缺口和正常保存退出未通过仍保留。

## 2026-10-22：attempt-40 终点与候选 41；执行暂停

40 的真实完整 Mod 客户端完成资源加载，08:27:29 启动 IntegratedServer，08:31:11 开始准备主世界，最后 08:44:11 记录出生区 0%。premain INSTALLED_FULL/Native FULL 不代替世界验收；Bootstrap 待安装边界、Native 来源/租约拒绝和 VirtualFree BUSY 继续保留。真实采样显示任务来源捕获仍有重复工作、客户端缓冲读写仍有调用者查询开销，未取得玩家入场、ProRuntime 稳定世界 Tick 或保存退出 PASS。

用户要求本轮进不去即暂停。最后真实 Java 栈、日志及独立测试存档已保留在本地 attempt-40；窗口激活超时后，核对可执行文件与记录中的原始 UTC 创建时间，仅结束 PID 24660。监控记录 08:45:26 退出 -1，不记自行崩溃或正常保存退出。原第三方包未改动，没有启动候选 41。

41 收敛实际数组写的不必要回执入口、完整空物理栈后的重复任务来源查询及缓冲内存入口的调用者查询。数组实际写守卫、公开回执授权、部分来源回退、Native 来源/控制区/生命周期均保留；短栈仅作准确调用类的正向快捷判断，未命中仍走原完整选择器。最终统一双 JAR 构建成功（1 分钟）；扩展的既有实际数组写检查只编译，未运行，不能据 40 的检查结果认证 41。

本次同步源码、既有检查源码和候选 41 双 JAR；PCL 保持已安装的候选 40，未部署 41、未为 41 生成参数、未进行后续游戏或夹具运行。当前暂停，整世界及正常保存退出仍未验收。

## 2026-10-22：候选 40，恢复真实 PCL；控制身份目录并发读取

39 的真实 Java 栈同时显示早期绘制线程在 ControlRegistry 等锁、变换线程持锁批量登记并调用 heapPolicyChanged。修复保留写者监视器、特殊角色同步与逐项 Native 通知，普通身份查询读取实时发布的弱身份目录。扩容复制新节点后发布新根，GC 保留旧读者链；临时批次精确索引覆盖尚在登记的对象及索引自身，索引只保存集合成员关系。没有缓存授权/停止结果或恢复容器广谱 Hook。

40 完整双 JAR 构建通过（32 秒，证书检查保持开启，同配置重试）。既有 live-control-writer 和 system-arraycopy 各退出 0，覆盖实际已安装 Native/Unsafe/反射写拒绝、私有读取拒绝、原始控制数组及普通数组复制/异常/兼容前缀。没有新增或运行 metadata seam。世界性能、并发登记全部交错与最终保存退出不据这两个检查标为通过。

39 实际达到 premain FULL/Native FULL、全 Mod 客户端加载、配方/进度及 IntegratedServer 启动；平台待安装边界仍报告 PARTIAL。正常窗口关闭未及时完成，测试 JVM 最终退出 -1，测试世界在实际进程退出后完整备份；未通过玩家入场、稳定世界 Tick 或干净退出。该轮有 6 条 TLS 拒绝、暂未见 VirtualFree 错误，不视为物理来源/租约缺口修复。40 已备份部署原 PCL 并重新生成参数，attempt-40 使用原 Mod 和真实参数继续自动进入同一独立世界。

## 2026-10-22：候选 39，四项重复工作收口；仅构建和部署

普通入场改为共享 image->calls，保持原子 active 与独占退役/发布；单次关联查询带回加载选择状态及实际 load，消除同查询重复遍历，保留 JNI-only 的 0 语义及受限实际 caller 检查。TLS/FLS 的 value 修订及实际观察上下文 epoch 仅记录已经成功保留的集合成员关系，逐次实际读取/返回核对/停止与私有索引检查不缓存。观察历史单调合入，重入改写后再次保留当前来源；读取失败不写成功标记，也不暴露未能保留来源的返回指针。

线程/纤程/值清理使用独立工作队列，历史身份不参与完成项重扫。未关闭监视句柄、当前 TLS/FLS 索引与历史分离。零引用租约直接排队，出队重查引用计数；每段限制处理量，已结束项退出，真实未知生命周期继续保留。没有把监视 HANDLE 裸拷贝出保护门，也没有把 JNI/DLL 释放移入目录锁。

首次统一构建失败于受限回调新参数漏接，补齐后统一双 JAR 构建成功（20 秒）。已备份部署原 PCL 并重新生成参数，保持游戏关闭。没有运行审查中的成功桩复现或其他测试，没有新启动时间、退场或世界验收结果。37 的出生区块 5%、退出 -1 仍是最后实机终点，JNA source_lease/实际未知加载来源/VirtualFree BUSY 与稳定世界及正常保存退出均未标为通过。

## 2026-10-21：候选 38，六项审查修复已构建

完成实际 caller 索引缺口的按需传播与原子标志、普通 scoped owners/selectedLoad 接线及失败清空、普通/受限共用纯 C Binding/Registration/Library/Tooling 合并。租约后备合并改为临时真实指针集合与暂存链一次合并，保留共享尾链和失败引用清理；普通 TLS 正式并集提交成功便释放备用增量，失败保留，FLS 历史规则不变。实际采集过程提供入口诊断，FLS callback 查询处保存其 unknown/producer，失败使用最终有效来源；没有额外诊断扫描或重新进 VM。

首次构建遇 ForgeGradle 证书校验失败，同配置重试构建通过（45 秒）。未运行助手桩/metadata seam，源码路径与编译不冒充三组故障场景已经实测。37 全 Mod 资源加载完成，独立常规世界 IntegratedServer 已启动，出生区块最后到 5%，没有玩家入场/稳定 Tick/正常退出通过。匹配 PDB 的真实来源记录显示后续显卡 load 自身 unknown=1/leaseComplete=0；JNA 初始失败是 known/source_lease；tls=24 不单独证明调用 unknown 根。VirtualFree BUSY 仍保留。

按用户“这次完了停一下”收尾：游戏窗口激活两次超时，结束已核对身份的 attempt-37 JVM，监控记录退出 -1；测试世界和现场保留，不能记为干净保存退出。候选 38 已备份部署至原 PCL 并重新生成启动参数，没有启动新进程。本轮源码/构建/部署完成，实机最终验收尚未完成，执行已暂停。

## 2026-10-21：候选 37，同一来源工作包的失败路径

有效 JNI 关联不再被没有当前 load 的选择器返回 0 覆盖为失败；后备来源读取使用映像关系索引。拒绝记录冻结本次来源查询结果，保留实际 Java code/process 缺口标志，之后只补失败阶段/索引世代/租约状态。普通 TLS 保留预分配的增量租约记录来覆盖系统写入成功后的聚合分配失败；只在 FLS 记录回调值，仍无整份历史复制。35 实机到 premain FULL/Native FULL 和类变换，36 仅构建；37 构建通过（36 秒），双 JAR 备份部署、参数重新生成并继续真实原实例。没有据此宣称最终世界或退场通过。

## 2026-10-21：候选 35，TLS 来源/生命周期同批收口

生产路径已完成两份固定提交审查指出的修改：锁外 Module→Owner；记录借用/退役短锁；所选真实 load 贯通受限 TLS 租约；映像级 JNI 关系索引与同源重复加载；来源合并失败不发布入口；不可变共享历史及本次增量；独立 partial 生命周期标记；清理前失败快照及按实际失败类别去重。保留停止来源、来源全集覆盖、真实生命周期和 Native/JVM 控制区保护，没有 DLL 名称白名单或合作式 SDK。

attempt-33 实际启动全 Mod 组合的新建测试世界，IntegratedServer 初始化、出生区块到 3%，但尚无玩家入场/稳定 Tick/保存退出通过；正常关闭请求超时后结束并备份该独立世界。旧副本依赖当前未启用的历史维度包，未为打开它修改 Mod 集合。真实生成线程另暴露 SourceMap 只读查询中的 ThreadLocal 写入和全局目录锁；现合并实时身份读取并遍历已发布弱链，保留所有实际注册及再次校验。

候选 34 初次统一构建发现 completion-port 的新增参数调用漏改，补齐后通过；候选 35 包含完整增量链和 SourceMap 读取收口，构建通过（41 秒）。双 JAR/参数已有旧版备份并更新原 PCL，attempt-35 使用真实全参数自动打开独立新世界。没有运行 metadata seam 或隔离消费者夹具，也未把构建等同于同源双记录、额外 JNI、退场、分配失败或最终世界效果检查通过。此前 VirtualFree BUSY、受限 source_lease/unknown_source、世界稳定运行与干净退出仍保留待本次实机结果。

## 2026-10-21：候选 33，真实公共载体关系的错误分类

attempt-32 的真实内存记录确认 jemalloc 只有一条当前加载关系：unknown=0、leaseComplete=1、所有者非 Mod，API 导入项 unknown=0；TLS 槽位却已得到 allocation_unknown=1。对应源码的 outside 分支对这种唯一完整关系无条件置 unknown，已定位到实际记录与错误分类之间的对应关系。

修正仅选取这一条当前完整已知关系，保留更具体的绑定/回调优先顺序；不清零任何已有未知标记，不改变停止来源、多个当前加载、控制区或私有索引保护，也没有以 DLL 名称授权。候选 33 构建通过（32 秒），旧 Native DLL/PDB 和运行现场保留，双 JAR 与参数已备份更新，继续全部原 Mod 实跑。实际早期 TLS 索引 95 的 allocation_unknown/aggregate_unknown 已由此前的 1 变为 0，uncertain/gap 仍为 0，加载及 API 条目保持完整已知，确认修正作用于真实生产调用。attempt-32 未开世界，于 Datafixer 后结束；该轮及本轮均未记录世界/保存 PASS。

## 2026-10-21：attempt-31 致命 TLS 写入拒绝与候选 32

attempt-31 已有真实 Forge/渲染/声音引擎及贴图图集完成记录，运行约 33 分钟后以 0xc0000409 退出。最后主渲染线程 kind=37（TlsSetValue，而非 FLS 分配）索引 95 的 unknown_source 拒绝紧接 jemalloc Error setting TSD；此前 VirtualFree 数据无效也保留。匹配 PDB 的转储确认 jemalloc 主动致命中止，小转储缺少 Host 堆记录，未据此猜测具体未知根。没有打开世界或干净保存退出。

候选 32 修复 TLS 已有来源借用中的纯叶锁争用误判，完整来源/租约及停止状态判断保留；拒绝路径增加受限/普通各一次的真实根分类，受限回调不调用 Java。保留 unknown_source 拒绝及 Native/JVM 控制区保护，未把本次叶锁修复宣称为未知根致命问题已解决。构建通过（32 秒），双 JAR 及参数已备份更新，attempt-32 继续完整原 Mod 组合实跑。未重复隔离/metadata seam 检查。

## 2026-10-21：候选 31 实际重复控制登记热点

attempt-30 的真实 35 秒 JFR 没有栈数组 resize 样本；剩余现场为早期窗口标量写入在多个实际边界创建回执，并在 RawMemoryMutation/Scalar 的弱控制目录发布中争用同一锁。现在仅对这两个实际 Bootstrap 类型直接判定为控制状态，覆盖整个实例生命周期，去掉逐实例的重复发布。普通数组/容器仍走原登记与判断，各层 Native 来源、作用域、区间保护及停组规则均未改变。

完整候选构建通过（34 秒）。既有 live-control-writer 在实际 Microsoft JDK、Agent/Bootstrap/Native 和原 Unsafe 参数下，额外对这两种真实控制对象执行外部 Unsafe/反射写入，状态保持不变；正常裸内存复制及原外来调用/控制数组/私有读取检查全部通过，退出 0。首次检查因生产返回 IllegalAccessException 而非检查预期的 SecurityException 退出 1，实际写入已拒绝；仅修正检查异常预期后复查。attempt-30 到达 Datafixer 初始化，未打开测试世界。更新候选 31 后继续完整原实例实跑，未宣称世界或干净保存退出 PASS。

## 2026-10-21：候选 30 栈数组保留槽位修正

PCL 实际 Microsoft JDK 17 的字节码确认 START_POS=2：初始 32 槽位仅容纳 30 帧，后续 32 帧批次仍要求 34 槽位并触发 arraycopy。WALKER/RAW_MEMORY_WALKER 已改为 34，ORIGIN_WALKER 保持 64；没有改变实际栈遍历或任何来源/内存控制规则。统一构建通过（34 秒），原实例双 JAR 已备份替换、参数重新准备，attempt-30 已使用全部原有 Mod 启动。

attempt-29 实际达到 LWJGL、Forge 初始化与 Ronova 绘制隔离安装，但未开测试世界；关闭请求未及时结束，更新前结束进程，不记录正常退出 PASS。此前 TLS/FLS unknown_source/source_lease 与 TextureManager.close 并发修改异常均未据此勾为修复。世界 Tick 与正常保存退出仍待真实客户端结果。

## 2026-10-21：attempt-27 实际终点与候选 29（世界未通过）

attempt-27 的已保存 Java/Native 栈、日志和类统计确认：后台初始化之后进入 Render thread，MinecraftForge 初始化、Ronova 客户端绘制隔离安装、贴图/字体资源处理和客户端循环均已实际发生；15584 → 18649 类是本轮实际加载统计。测试世界直到结束仍未打开，没有内置服务器或世界 Tick PASS。TLS/FLS 拒绝的实际可映射调用者为 NVIDIA 图形组件、drvstore.dll、CRYPT32.dll；未知来源/不完整租约限制保持不变，未将其视为已修复或强行放行。

资源阶段实际 Worker 栈反复出现来源栈扩容进入受控 arraycopy，并等待控制对象登记锁。候选 29 将常用 Java 栈读取器初始批大小设为 32、保留隐藏帧的来源读取器设为 64，避免内部扩容触发的重复控制查询；选项、全部帧、授权和来源规则不变。统一构建通过（37 秒），没有为这项缓冲提示重复运行隔离检查。attempt-27 经正常关闭请求后进程退出 0，但未完成资源加载的 TextureManager.close 报 ConcurrentModificationException，明确不记干净退出/保存 PASS。双 JAR 已备份部署、参数重新准备，attempt-29 已启动并记录 premain INSTALLED_FULL、Native FULL，最终世界验收仍在进行。

## 2026-10-21：候选 26 回调/TLS 收口与实际调用者检查

后续：attempt-26 已保存真实 JFR/Java/Native 栈，在未打开测试存档前主动结束，未取得世界验收。完整调用者复查没有替代此结果。JFR 的主要热点为 memoryWriteBegin0；同一动作在 Java 捕获当前来源后，Native 又回调 Java 重读。candidate-27 复用这次已授权边界提供的 Java 捕获，仍逐次捕获和合并 Native 独有根、未知标记与实际控制区，不缓存许可；无捕获参数及复制完成更新保留原完整读取。第一次构建在 ForgeGradle 证书检查阶段失败，同配置重试构建通过（54 秒）。实际 live-control-writer 再次输出外来调用/私有原生读取拒绝与正常裸内存复制 PASS，退出 0。双 JAR 更新并重新准备参数，attempt-27 正在使用完整原 Mod 组合加载，尚未打开世界。

候选 28 为同一热点工作包的缓冲调整，构建通过（38 秒）：内存授权的 Java 当前栈读取器初始预估 32 帧，避免默认八帧扩容产生受控 arraycopy 和额外 JVMTI 查询。预估不是遍历上限，选择规则/选项不变；没有缓存栈或许可。该 JAR 已列为发行候选，但当前 PCL 仍运行 27，未仅为这项调整再次重开游戏。无单人世界/稳定 Tick/保存 PASS。

接续 9c24296。attempt-22 原生崩溃保留；attempt-23 的失败日志与线程栈显示 CRT/FLS/导出入口在业务许可判断前被全局目录竞争误拒。完整发布的来源/映像记录改为原子发布后只读查询，单映像短锁维护实际调用与卸载排斥，不删除来源或停止判定。attempt-24 未再出现此前大量入口 BUSY，推进至 Render thread；剩余三次 FLS_GET preparation BUSY 来自首次线程上下文登记的全局记录锁竞争。该轮中断后没有正常退出证据且没有打开测试存档，保持未通过。

剩余 TLS 监视句柄改用专用同步：分配到发布、外部消费者检查到真实 CloseHandle/DuplicateHandle/句柄修改、退役关闭均按同一顺序连续保护，不存在查询后可关闭/复用私有句柄的窗口。该锁不在持有期间进入 VM/加载器，普通 FLS 读取不再因无关进程记录竞争返回假失败。真实调用者授权则直接使用既有 Java 栈选择器，避免每个普通内存读写先转入 JVMTI 再回 Java；仍按实际调用类授权，不缓存许可，Native 来源/控制区/生命周期保护保留。

candidate-25 构建通过（34 秒）。现有 live-control-writer 使用 PCL 同版 Java、实际 Agent/Bootstrap/Native 与 Unsafe 保护运行，输出 LIVE_CONTROL_WRITER_PASS actual_native foreign_caller_refused protected_array_preserved foreign_raw_reader_refused actual_unsafe_raw_copy，退出 0。检查仅更新了与现行生产拒绝消息一致的预期，没有新增 metadata seam 或消费者夹具。candidate-26 的完整包统一构建通过（20 秒）；双 JAR 已备份部署、参数重新生成，attempt-26 已按原完整 Mod 组合与真实 PCL 命令启动。当前仍没有单人世界、稳定世界 Tick 或保存退出 PASS。

## 2026-10-21：PCL 真实客户端循环修复（阶段性，世界验收未完成）

基线为 41e7887，主工程 D:\桌面\ronova_GPT。本轮使用原 PCL 实例、原完整 JVM/游戏参数及原有全部 Mod，仅为 --quickPlaySingleplayer 指定独立存档副本。旧候选与参数逐轮备份；私人启动参数、原始日志、线程栈、崩溃转储、存档和第三方包均未加入公开仓库。

前序真实 JNI 小检查通过，输出 JNI_LIVE_TABLE_PASS：受控及非受控活动槽位的裸写修复、合法已批准钩子保留、实际停组后的原始槽位恢复。检查自己的只读页面阶段曾错误覆盖 HotSpot 共页数据，已改用实际附着线程的独立 JNI 表后复查通过；该失败保留，不算生产启动通过。

真实客户端循环记录：

- attempt-13 采样中早期绘制已不再落到重复来源 StackWalker，Mixin 临时数组不再逐字节进入 byteStore 回执；随后定位到 EventBus 自有 MethodHandle 更新被自己的 writer 判断拒绝，引起 JDK 布尔 CAS 重试。精确修复内部写入者身份，外部入口仍排除。
- attempt-14 走过上述自旋，暴露 Mixin 原 synthetic Args 包在生产 GAME 模块中无路由；attempt-15 仅改前缀却重算栈图，触发缺失的可选 ASM CheckClassAdapter。最终保留原栈图及原生成器，attempt-16 已生成并使用 GAME 包下的 Args 类，进入资源重载。
- attempt-16 在字体加载时由 jemalloc 报 Error setting TSD 后原生退出，Windows 崩溃转储保留。TLS 同一索引的跨线程 Get/Set 不再误触全槽 busy 拒绝。
- attempt-17 到达原 Mod 构造、渲染初始化、字体资源加载和 Minecraft 客户端 Tick。真实 Native 栈抓到退役线程持有 TLS 全局锁重复扫描已结束上下文，渲染线程等待该锁；已按实际上下文建立值索引、退出完成项重复扫描，并保留在途/回调/来源释放条件。已知平台回调的部分租约不再阻止其合法 TLS 操作，实际 Mod 或未知来源的原检查仍保留。
- 同轮还暴露 CLIENT_SCREEN_LAYOUT_CHANGED：超级史蒂夫原 JAR 的 AT 包含 public-f net.minecraft.client.gui.screens.Screen *，合法移除了三个列表字段的 final。兼容其实际 public 布局，继续验证准确字段、描述符、原基类/模块身份、实际 ArrayList 和每次列表引用身份；没有修改第三方包。

审查清单对应的任务取消、直接 CF CAS/BLOCK_CLEAR、自有引用释放、真实装备声明状态、准确来源发布、FramePop 等待、当前 Native gap 替换及同步读取均已落入生产源码。退役和状态读取的两次全线程扫描合为一次，同轮重复 Mod/UUID 查询合并。恢复链完成后的事件唤醒尚未全部实现，仍做真实读回；没有将源码改动冒充相关停组、恢复、跨重启或客户端清理的实测 PASS。

candidate-19 的统一生产构建通过（54 秒），生成双 JAR。attempt-17 保存日志后正常退出 0，测试存档尚未打开。新候选更新到原 PCL 实例并重新准备参数后阶段性同步。当前尚未完成单人世界进入、IntegratedServer/ProRuntime 稳定 Tick 和世界正常保存退出验收，真实游戏循环继续。

- attempt-19 随后原生退出 0xc0000005。Windows 实际转储定位 lwjgl_opengl.dll+0x1d696，线程清理回调的 RCX 为 -1；之前拒绝的 FLS 更新会保留 CRT 的销毁哨兵。公共载体无实际 Mod 来源的普通 TLS 操作不再仅因 restricted/unknown 进入误拒；所有部分来源继续保留且不授予私有释放权限。槽位分配、值、在途写及贡献历史含实际 Mod 来源时仍要求完整来源，私有索引和停止来源保持拒绝。纯 TLS 记录锁改为实际等待，避免正常清理被伪造为 BUSY；私有 FLS 哨兵的 ended 不替代真实内核线程终止。candidate-20 构建通过（45 秒）。
- attempt-20 在加入真实 JFR 采样时停滞。普通附加命令超时后，同版 Microsoft JDK SA 读取到 Attach Listener 等待 ControlImages 变换锁，主线程等待 ThrowableTracer 初始化的循环；该进程尚未打开测试存档，结束后修复。ControlImages 改为保护登记在锁外执行，锁内仅交换已经受保护的不可变快照；仍保留原控制镜像和先保护后发布次序。candidate-21 统一构建通过（30 秒），更新/备份双 JAR、重建参数并继续真实客户端。

本轮源码复核还确认：refreshNativeGaps 的已加载类遍历仅在实际 stopped 模块状态读取时执行，并非全部活跃 Mod 的普通启动路径；完成恢复链的事件唤醒仍未全部实现。FrameworkBuffers 使用线程局部嵌套上下文，退出恢复前值，序列化用 finally 退出，数组业务登记/来源发布立即取消快捷处理；绘制仅复用来源，原内存权限及 JNI 表保护继续执行。这些源码条件不冒充逃逸、停组或世界场景的新实测 PASS。

attempt-21 的真实 JFR 在约 6 秒内启动并完成 35 秒采样，主启动线程继续进入 Forge 早期窗口；未重现 ControlImages/ThrowableTracer 等待环。随后实际退出 0xc0000374，Windows 转储和系统调试引擎确认在 DLL_THREAD_DETACH 的 CRT 清理中误释放了其他原生库的数据。转储内 STB CRT 的 API 缓存显示 FlsAlloc/FlsSetValue 有效而 FlsGetValue 为全位 1 的缺失哨兵；CRT 二进制明确在后者缺失时回退 TlsGetValue，但仍使用已分配的 FLS 索引。根因是 host_native_resolve 对 heap/toolingRestricted 无条件返回 NULL，令合法系统导出被永久缓存为缺失。删除这项无条件误拒，仍执行现有实际回调来源捕获、映像关联、停止来源判断和返回 API 受控封装；不修改第三方 DLL、不取消控制入口。这是候选后续修复，尚不代表新一轮游戏或世界通过。

candidate-22 完整生产构建通过（33 秒），双 JAR 已更新至原 PCL 实例，保留旧包和参数备份并重新准备启动参数。attempt-22 使用原全套参数与全部既有 Mod 继续独立世界副本实跑，本次阶段同步不宣称已进入世界。

## 2026-10-20：ItemStack public 访问变换后的 Forge 重定向

输入为用户 12:21、12:57 报告及 PCL 实际 Forge 47.4.23／coremods 5.2.4／AT 8.0.4／ASM 9.5。已核对实际阶段：AccessTransformerService 选择 BEFORE，超级史蒂夫 6.10.12f 原包的 ItemStack 全字段规则先将字段公开，Forge 后续 private 前提因此失败。没有执行压缩包内的启动脚本或加载第三方 Mod 类。

生产修复位于 `ForgeFieldBoundary` 与 Agent 原变换链：只兼容 ItemStack 的准确 Item 字段／getter 对，保持 AST 可见性和其余 Forge 验证、重定向主体，以及原 Ronova 来源／写入／发布控制。没有改 SDK、Native 控制区、保护资格、容器 Hook 或第三方包。

- `jar compileStateChecksJava` 成功，33 秒，生成对应候选。
- 真实小检查读取超级史蒂夫原 AT 文件，调用原 AT 引擎，处理真实 SRG 与开发映射 ItemStack 类文件，再调用实际 Forge ASMAPI。完整 Agent／Native 安装，保留 PCL 全部 DisableIntrinsic 参数；日志后端仅在检查进程改用 SimpleLoggerContextFactory。
- 新候选：4 处读取正确改为 getter 调用，AT 权限未被修改，原 private 路径通过；无关类、静态字段、错误描述符、错误 getter、缺失／重复 getter、重复字段及其他字段共 8 类输入均拒绝。`FORGE_ITEMSTACK_PASS`，退出 0，40.279 秒。
- 旧候选同输入准确失败于 `ASMAPI.redirectFieldToMethod:1069`，异常与用户报告一致，退出 1，39.802 秒。
- 首次检查超时与后续诊断栈保留在 `.work/pcl-itemstack-20261020`：当时仍在 Log4j 插件扫描，未执行到 ItemStack 变换。更换检查日志后端后完成；没有用超时记录声称生产通过，也未继续运行无关检查。

已保存旧加载进程日志并结束该进程，候选双 JAR 及启动参数更新到原 PCL 实例，备份保留。没有启动游戏／Forge、初始化游戏世界或执行保存重启。12:57 报告没有新增致命异常；原先未解决的 ItemStack 冲突由本节覆盖，完整主菜单／世界验收仍未取得。

## 2026-10-20：LWJGL WindowsLibrary 原生空返回路径

接续 `dcc98db`。用户报告 `错误报告-2026-10-20_11.38.50.zip` 含 `hs_err_pid141168.log`：`WinBase.nGetModuleHandle` 内 `lwjgl.dll+0x20524` 执行写入 `[rax+0x1c]`，当时 RAX 为 0。其前序指令通过 JNI 查找／调用 ThreadLocalUtil 的线程数据初始化。原报告没有保留该 JNI 调用内的 Java 异常，因此未把某个猜测异常写成已捕获事实。

源码检查发现该类的初始化读当前 JNIEnv 指针，能力操作访问 JNI 表的 reserved3；此前缺少相应平台接线，通用来源变换和控制区裸读拒绝均会干扰其约定。旧候选配套 Agent／Bootstrap／Native 在普通类路径及命名模块下的独立对照都先报告 `CODE_SOURCE_BOUNDARY_BINDING_FAILED:org/lwjgl/system/ThreadLocalUtil` 并以 ClassFormatError 退出，这说明旧路径不能完成该类加载，但不等同于重现用户的原生 PC。

生产变更：复用既有 BufferBoundary／ResourceBridge 平台登记，按实际类名、加载器、模块、方法描述符与调用帧接入 ThreadLocalUtil 的三处方法，检查已知地址调用布局。Native 私有入口单独认证实际 ResourceBridge 调用，仅处理当前 JNIEnv 槽位读取及当前／VM 表的 reserved3 字；不授权 JNIEnv 写入或 JNI 函数槽读写。其他普通地址沿既有 Unsafe 守卫，已停调用与目标策略仍执行。

最终生产构建 18 秒通过；必要检查使用 PCL 的实际 Java 17.0.15 和本地 LWJGL 3.3.1 原生库。最终命名模块检查输出 `LWJGL_BUFFER_STARTUP_PASS lwjgl_module=org.lwjgl`，退出 0。其顺序为 MemoryUtil 后直接初始化 WindowsLibrary，由 WinBase 原生调用首次触发 ThreadLocalUtil，未预热该类；随后实际查询 Windows 模块、加载 GLFW 库、建立／释放能力表，验证普通缓冲区读写和原布局探测。外部 JNI 槽读取、伪造 Java 平台入口、直接反射调用私有 Native 入口、直接／回绕控制表视图均被拒绝。

工作记录位于 `.work/pcl-lwjgl-crash-20261020-113850`。首轮内部认证误用 Agent 专属入口、随后嵌套类直接登记反射句柄的调用位置错误均已修正，失败日志保留。之后仅修正检查对两条有效内存拒绝标签的匹配，并调整初始化顺序、输出模块名称，单独重编检查入口；未反复重建未改动的生产包。

没有调用 glfwInit、创建窗口、运行 Forge 主类或启动游戏。候选已部署到原 PCL 实例，备份目录为 `ronova-launch/backup-lwjgl-thread-20261020-120011`，参数准备成功。小检查不代替完整游戏启动结论。

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3299951 | `69bfd9caddfa9a8fd863c654ed4853c0bc5fc884a6a1099a9962bc0b496e6d44` |
| `ronova-pro-prelaunch.jar` | 51556 | `d6bfc76997b1a115346a1cb16fa9feb2767595d0d0a419de5c944bce1016c6ab` |

## 2026-10-20：PCL LWJGL 初始化误拒及退出互锁

接续 `4d96d68`。最新实例日志与 `错误报告-2026-10-20_11.17.40.zip` 一致：`MemoryUtil.getPositionOffset → JNINativeInterface.nNewDirectByteBuffer` 被拒，原因明确为 `NATIVE_CONTROL_RANGE_UNAVAILABLE:ADDRESS_OVERFLOW:restricted=0:loaderQuery=ready`。实际 LWJGL 3.3.1 字节码以地址 `-1`、容量 `219540062` 构造 Buffer 布局探测对象；此调用只创建 Java 视图，未解引用原生地址。

`native_buffer_control` 对回绕视图分两段查询完整覆盖范围，保留两段的控制区命中／未知拒绝；实际内存访问和页面权限 API 的溢出拒绝没有改变。复用 `AuditFixCheck` / `AuditFixNative`，直接初始化实际 LWJGL，使用其真实 JNI 接口创建布局探测及普通缓冲区，并对当前线程的真实活动 JNI 表验证直接／回绕视图均被拒绝；没有提供控制器 metadata 或 Owner。

初次生产构建 46 秒成功，初始化与保护断言完成，但独立进程退出超过 50 秒，被检查驱动结束，未计完整 PASS。Java 线程现场无额外非 daemon 线程，原生现场显示 `LdrShutdownThread → lwjgl → DetachCurrentThread` 等待；不加载 Ronova、使用相同 Java/LWJGL 的退出对照为 0。[LWJGL 3.3.1 的线程卸载实现](https://github.com/LWJGL/lwjgl3/blob/3.3.1/modules/lwjgl/core/src/main/c/common_tools.c#L54-L67) 确认 DLL 回调会查询当前 JNIEnv 并 Detach。Ronova 的清理线程此前在 Agent_OnUnload 唤醒后仍带 daemon 附件返回，从而在 JVM teardown 中触发该回调。

修复在 VM teardown 后保持该进程生命周期原生工作线程静止，避免再次进入 JNI 或触发 foreign DLL_THREAD_DETACH；原有固定驻留的 DLL／入口仍保留，正常清理不变。最终统一构建 38 秒通过；同一实际 Java 17.0.15／LWJGL 检查输出 `LWJGL_BUFFER_STARTUP_PASS`，进程正常退出 0。未关闭真实内存保护，也未启动游戏或 Forge。

工作记录位于 `.work/pcl-lwjgl-20261020`。原游戏 PID 169924 已确认在窗口初始化前失败并结束；只读 jcmd 取栈超时、修复前检查超时及原生栈均保留。一次旧 Native 对照错误搭配新 Bootstrap，被摘要校验以 `EARLY_NATIVE_IMAGE_MISMATCH` 拒绝，该次不用于故障结论。

最终双 JAR 已更新到 PCL 的原 `1.20.1-Forge_47.4.23` 实例，复制核对一致，准备脚本重新生成参数。备份为 `ronova-launch/backup-lwjgl-20261020-113356`。没有本候选游戏启动或世界效果结论。

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3296853 | `0b5276f645a451ff25504024bb3d7dcc4db8163078510e33d6023c29c5b75eb0` |
| `ronova-pro-prelaunch.jar` | 51556 | `d6bfc76997b1a115346a1cb16fa9feb2767595d0d0a419de5c944bce1016c6ab` |

## 2026-10-20：PCL Class 缓存反射重入回归

接续 `a14799f`。用户错误报告 `错误报告-2026-10-20_10.47.53.zip` 中，PCL 参数交接、Bootstrap 装载及 Native 安装成功，随后 `AGENT_PREMAIN_READY:INSTALLED_FULL`。崩溃栈为 `Class$Atomic.casAnnotationData → ControlBridge.unsafeWriteAllowed → ResourceBridge.mirrorStaticRange / declaredSpans → PlatformMetadata.declaredFields → Class.getDeclaredFields0 → ModuleClassLoader`，最终重复定义 Log4j 的 `MessagePatternConverter$RenderingPatternConverter`。

修复仅涉及 Class 镜像字段判定：实例缓存查找限于 `Class.class` 布局，精确通过原有 JDK 三条 CAS 栈验证后立即放行该缓存写；不再为平台缓存访问被代表类的业务字段。外部实例字段写与 bulk 重叠写提前拒绝，真实静态字段仍走原保护策略，未放宽外部 Unsafe、反射或句柄修改。

- 统一 `jar` / `reobfJar` / `compileStateChecksJava`：33 秒，退出 0。
- `installed-platform-writes`：真实 Agent 与 Native 安装，退出 0。首次方法反射及注解查询、重变换后查询、外部缓存投毒拒绝全过程，实际 Instrumentation 类目录中的延迟字段类型始终未加载；合法静态写保持，三个 JDK 缓存 CAS 均完成。
- 保存的旧 Bootstrap 对照同一检查：退出 1，准确命中 `Class cache guard eagerly resolved a represented field type`，发生于首次方法反射之后。没有以重跑成功替代故障定位。
- PCL 本地部署：双 JAR 与候选一致，旧包及参数备份到 `ronova-launch/backup-class-cache-20261020-105714`；实际准备脚本成功生成新 JVM 参数。没有改动游戏主类或启动游戏。

工作记录在 `.work/pcl-class-cache-20261020`，原始报告保存在 `.work/pcl-crash-20261020-104753`。原失败和旧版本对照结果保留；没有 Minecraft/Forge 启动通过或世界效果结论。

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3296813 | `a4d1fb00aeacf489ec08b76aab3588943b9acf649bb53e73f28dba927c4b8294` |
| `ronova-pro-prelaunch.jar` | 51556 | `d6bfc76997b1a115346a1cb16fa9feb2767595d0d0a419de5c944bce1016c6ab` |

## 2026-10-20：PCL2 仅准备参数的启动交接

接续 `8170395`。使用 PCL2 正式版 2.99.0.0 的实际实例 `1.20.1-Forge_47.4.23`，继续指定其 Java 17.0.15。依据 [PCL 启动实现](https://github.com/Meloong-Git/PCL/blob/main/Plain%20Craft%20Launcher%202/Modules/Minecraft/ModLaunch.vb)，实例启动前命令启用等待，JVM 参数首项加入新生成的 `@ronova-jvm.args` 绝对路径；原主类 `cpw.mods.bootstraplauncher.BootstrapLauncher`、原游戏参数与原 JVM 参数均未替换。

最终实现为 Prelaunch `--prepare-args` 与 `src/prelaunch/prepare-pcl.ps1`：只输出 Ronova JVM 选项，不生成账号参数回放脚本，不创建子游戏进程。PCL 忽略启动前命令退出码，因此脚本在准备前移除旧参数文件；Java 端写临时文件后原子发布，避免失败后继续用旧文件或半份配置。

本地配置已备份，Core 更新到 mods，Prelaunch 和脚本位于实例 `ronova-launch`。PCL 当时正在运行，首次配置在任何写入前停止；随后正常关闭启动器后完成配置，没有强制结束进程或启动游戏。备份：`D:\MC\.minecraft\versions\1.20.1-Forge_47.4.23\ronova-launch\backup-20261020-8bcfc097`。

最终 `jar` / `reobfJar` 构建通过（21 秒）；实际 Windows PowerShell 准备脚本退出 0，输出 `RONOVA_PRELAUNCH_ARGUMENTS_READY`，生成 11 项选项，含早期 Native agent、Java agent、保存选集及内联约束。配置读回确认原参数保持、等待启用、PCL 选中正确实例、Prelaunch 不再位于 mods。没有执行生成的游戏启动命令，未取得本候选游戏启动结果。工作资料位于 `.work/pcl2-setup-20261020`；此前未部署的主类适配方案已删除，最终产物不含该入口。

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3296753 | `e41232f2fdfae97758efa96600efcc7680a333687a7ef56e201fd55d5a55eaa6` |
| `ronova-pro-prelaunch.jar` | 51556 | `d6bfc76997b1a115346a1cb16fa9feb2767595d0d0a419de5c944bce1016c6ab` |

## 2026-10-20：注解缓存自旋与非受控 JNI 槽位回归

接续 `88986aa`。生产修复：

- Class 缓存：精确放行 `Class$Atomic.casReflectionData`、`casAnnotationData`、`casAnnotationType`，分别匹配真实字段类型及完整必要调用帧／描述符。AnnotationType 链使用本机 OpenJDK 17 源码与声明中的 `Class.casAnnotationType → System$2.casAnnotationType → AnnotationType.getInstance`。合法平台缓存维护不因被代表的类或在途调用已停而返回假 CAS。
- 镜像／结构保护：外部反射与 setter 不再能修改 Class 非静态字段；AnnotationData、AnnotationType 内部字段只允许实际 JDK 构造路径写入。BackingBridge setter 定位改为 PlatformMetadata 原始声明，避免反射过滤／缓存投毒把真实字段误当不存在。
- JNI：保存当前批准目标与实际发布来源，初始为 guarded；只有真实 SetJNI 成功才提交，发布未返回期间的合法目标单独识别，失败不提升基准。未批准指针或已停来源恢复 original，更新批准值和 pending；退场／同步前在目录锁外刷新当前函数的实际来源，普通 JNI 入口只查已有状态。目录锁不跨 VM safepoint；实际 VM 表同时纳入独立区间保护。

生产与检查源码写齐后一次运行 `jar` / `reobfJar` / `compileStateChecksJava`：59 秒、退出 0。后续只修正并单独编译检查入口的反射查找，生产候选未重编。没有启动 Minecraft／Forge。

| 回归入口 | 结果与范围 |
| --- | --- |
| `installed-platform-writes` | PASS，实际 premain 与 NativeControl 已安装。此前未缓存的测试类及注解接口完成 getAnnotations/getAnnotation/isAnnotationPresent；真实 Instrumentation.retransformClasses 增加 classRedefinedCount 后重新发布缓存，结果保持。Class.name/classLoader/annotationData/annotationType、AnnotationData.annotations/declaredAnnotations、AnnotationType.memberTypes 的实际 Unsafe、Field.set、unreflectSetter 写入均被拒绝，正常静态字段与 ReflectionData CAS 保留。 |
| `platform-control-writes` | PASS，独立 Bootstrap 检查。真实镜像／静态字段准入、直接递增 classRedefinedCount 后的注解重建、真实 ReflectionData 被清空后的原始 VM 元数据读取；该入口本身不安装 Native，不冒充已安装状态。 |
| `jni-live-table` | PASS，实际 premain。完整 JNI 表／尾部保护、受控槽修复、只读页权限恢复；合法 GetVersion hook 保留，未经 SetJNI 的直接 GetVersion 改写恢复 original，之后合法再次发布仍成功。另从真实自动模块 `audit.jni.retirement` 的 JAR／mods.toml 加载独立 DLL，由目标类实际调用 System.load 和 SetJNI；经现有 ModGroupBoundary.prepare → publishStoppedModule → NativeControl.module 停止 `audit_jni_retire` 后，由未停主检查类确认 hook 已被 original 替换。没有注入 Owner、ready 或伪造停止标志；未覆盖 Forge 命令／世界清理。 |

检查目标源码和实际归组元数据保留在 `validation/checks/jni-retirement`，单独打成带 Automatic-Module-Name 的 JAR，不混入主检查类路径。C 检查用 `RONOVA_LIVE_JNI_ONLY` 编译，仅消费运行中的真实生产后端；第二份 DLL 使用不同物理路径由目标模块加载。Java 运行属性为 `ronova.pro.audit.native`、`ronova.pro.audit.hook.native`、`ronova.pro.audit.modJar`。

日志目录 `.work/v1.5-annotation-jni-20261020`：`build.log`、`check-native-compile.log`、`check-entry-compile.log` 与三个入口的 stdout/stderr。保留两次检查自身失败：独立入口缺少正常 premain 提供的 `sun.nio.ch` 等 opens 参数；JNI 检查把包内 modStopped 用 public getMethod 查找。分别补齐正常模块开放参数、改成 getDeclaredMethod 后，仅重跑对应失败入口。已安装注解回归没有重跑。

当前候选 Java105 / Native99 / Storage3 / Journal7：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3296245 | `bba4e4bb1ba0131c187fadb791d1797aeeb0a82830ef13290052c1a1da500f7d` |
| `ronova-pro-prelaunch.jar` | 51042 | `0b32b1e0d08e7af35afe97335d0b9d688c5c7d661913ce77e965901d6856503f` |

## 2026-10-20：NATIVE_CONTROL_RANGE_UNAVAILABLE 因果定位与修复

接续 `9d84e02`。保留此前 `direct-complete.stderr.log` 的 DatagramChannelImpl 失败。旧调用顺序为：Native initialize0 装入 Unsafe 拦截 → RecoveryAgent 第一轮 retransformStartup → libraryBoundaryInstalled/processBoundaryInstalled → host_windows_targets 才解析 `RtlIsThreadWithinLoaderCallout`。未解析时 `host_loader_callout()` 保守返回 true，把普通查询送入受限 TryAcquire 分支；真实 JNI 目录竞争因此变成重变换异常。

修复在 `native_initialize` 发布 VM/JNI 入口前解析实际 Windows 查询并初始化页面参数，较晚的 Host 安装复用同一初始化。真实受限上下文仍不等待锁，未解析／无法读取仍拒绝；JNI、JPLIS/JvmtiEnv、控制分配与 Host 目录保护不减少。异常保留原前缀，并补上实际失败目录、restricted 和 loaderQuery 状态。

完整生产代码写齐后，`jar` / `reobfJar` / `compileStateChecksJava` 一次统一构建通过，40 秒、退出 0。只有已有 Native 常量比较及 Java deprecated/unchecked 警告。检查复用 AuditFixCheck/AuditFixNative；新 `RONOVA_RANGE_CONTENTION_AGENT` 分支仅使用实际 JNI/JVMTI/Windows API，不包含生产后端副本，不注入 Owner、ready 或目录元数据。

| 检查 | 结果 |
| --- | --- |
| 旧顺序因果对照 | 本地对照 DLL 保留相同诊断和生产源码，仅撤回新增的早期初始化调用；Bootstrap 内嵌对应 DLL，加载真实 Agent。四条实际 attach/GetEnv 工作线程运行时，首次检查即在 java/net/Socket 重变换中复现 `NATIVE_CONTROL_RANGE_UNAVAILABLE:JNI_TABLES_BUSY:restricted=1:loaderQuery=uninitialized`，退出 1。它复现同一故障机制，不据此倒推旧日志未记录的具体目录。 |
| 修复候选相同负载 | 真实 GetEnv 21,248 次，普通分配内存的 Unsafe 写／读 1,024 次；Bootstrap 原 payload 一致、Native 可用、INSTALLED_FULL，退出 0。对照和候选各运行一次，没有以反复重跑代替修复。 |
| 候选双 JAR 中文 Prelaunch 与保护 | 中文工作目录及含空格的中文临时目录，正式解包和参数交接后自动 LOCAL_CACHE 追加；实际活动 JNI 表、尾部 VirtualProtect/WriteProcessMemory 拒绝、GetMethodID/GetFieldID 改槽后的恢复与 SetJNIFunctionTable 发布、只读页权限恢复和合法 GetVersion 保留全部通过，退出 0。 |

日志保留于 `.work/v1.5-native-range-20261020`：`build.log`、`late-init-compile.log`、`check-native-compile.log`、`late-init.stdout.log`／`stderr.log`、`fixed.stdout.log`／`stderr.log`、`prelaunch-protection.stdout.log`／`stderr.log`。对照 DLL/Bootstrap 仅存在本地工作目录，不进入发行候选。Prelaunch 的通用 `RONOVA_GAME_PID` 文案在此次指向 AuditFixCheck 独立检查进程；没有启动游戏主类或服务端。

当前候选 Java105 / Native99 / Storage3 / Journal7：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3293733 | `ae6b6e17edb25a224e8d506ab3390fd0dc597731e82cc745bce3bd6477f0e2eb` |
| `ronova-pro-prelaunch.jar` | 51042 | `0b32b1e0d08e7af35afe97335d0b9d688c5c7d661913ce77e965901d6856503f` |

未起服、未启动 Minecraft／Forge、未运行 metadata seam／SYS 检查，不将这些局部结果提升为游戏内整 Mod 攻防或保存重启验收。

## 2026-10-20：中文路径 Bootstrap 装载与 Prelaunch 交接

接续 `c56fecc`，只修复中文路径装载，不修改 Native 控制策略。系统 ACP 当前为 65001，当前文化 ANSI 代码页为 936；OpenJDK 17 的 [JPLIS Windows 路径转换](https://github.com/openjdk/jdk17u/blob/master/src/java.instrument/windows/native/libinstrument/EncodingSupport_md.c) 按用户区域选择代码页，[appendToClassLoaderSearch](https://github.com/openjdk/jdk17u/blob/master/src/java.instrument/share/native/libinstrument/JPLISAgent.c) 将转换结果交给 VM。因此原始中文绝对路径在此环境不能直接可靠追加。

`BootstrapSearch` 保留可用的 ASCII 相对 JarFile 名称；不能相对表达的中文文件名／路径使用与原 payload 内容一致的摘要缓存。本地缓存位于当前工作目录的 `ronova-pro/bootstrap-search`，已排除 Git 同步；另有 ASCII 临时目录回退。原工程、游戏目录和系统区域设置不变。成功 JarFile 保留到进程生命周期；失败尝试释放文件；Native 获取实际追加的 payload 路径。

生产源码写齐后统一构建 `jar` / `reobfJar` / `compileStateChecksJava`：34 秒，退出 0。后续仅修正检查入口的资源读取方式并单独编译检查类，没有重新编译生产源码。

| 检查 | 实际结果 |
| --- | --- |
| 中文工程目录直接 premain | `BOOTSTRAP_SEARCH_READY:RELATIVE:build\libs\ronova-pro-bootstrap.jar`；最终 `BOOTSTRAP_SEARCH_PASS`、`AGENT_PREMAIN_READY:INSTALLED_FULL`、退出 0。Agent、Bootstrap、早期 Native DLL 均直接来自 `D:\桌面\ronova_GPT`，没有手动暂存到英文工程。 |
| 候选双 JAR 正式 Prelaunch | 工作目录为 `.work/v1.5-bootstrap-unicode-20261020/中文启动目录`，外层临时目录为 `中文缓存 space`；实际解包和 Base64 参数交接后，自动 `LOCAL_CACHE` 追加成功。检查主类确认原 payload 内容一致、bootstrap loader 定义、Native 可用，退出 0。没有启动游戏主类。 |

最终日志：`.work/v1.5-bootstrap-unicode-20261020/build.log`、`direct-sequential.stdout.log`／`stderr.log`、`prelaunch-complete.stdout.log`／`stderr.log`。

未隐藏的失败：初次检查在实际装载已达 INSTALLED_FULL 后，通用资源接口返回空，触发检查自身的 `Bootstrap resource absent`；修正为核对真实持有的 JarFile。`direct-complete.stderr.log` 另记录一次实际 Native 启动失败：Bootstrap 已追加、Native 已安装，但 DatagramChannelImpl 的重变换因 `NATIVE_CONTROL_RANGE_UNAVAILABLE` 引发 ClassFormatError。后续直接路径单独检查通过不等于该 Native 问题已修复；本轮 Native 源码未改变，此失败继续保留。

当前候选 Java105 / Native99 / Storage3 / Journal7：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3292478 | `37d36060c3cf5aedf5b550b66a291eb5d3223c7054fcfc3e5fd2a9828835123a` |
| `ronova-pro-prelaunch.jar` | 51042 | `0b32b1e0d08e7af35afe97335d0b9d688c5c7d661913ce77e965901d6856503f` |

Prelaunch 源码／产物本次无需修改，新 Agent 通过 Core 内嵌 payload 交接。未起服、未启动 Minecraft／Forge，未运行旧矩阵、metadata seam 或 SYS 检查；未验证其他系统代码页组合或临时目录回退。

## 2026-10-20：活动 JNI 表与平台／客户端收尾

接续 `b2bb9f9`，完成当前六项生产工作包。实际范围见同日 A 批状态页；没有重新引入容器广谱 Hook、合作式 SDK、SYS 部署、自身外观或武器注册。

生产统一 `jar` / `reobfJar` 编译成功。组合命令末尾的 `compileStateChecksJava` 因历史 ASM 访问参数缺失失败，已补齐必要编译参数并单独完成检查入口编译；原日志 `build.log`、`check-compile.log` 均保留。没有因该检查编译问题重新编译生产源码。

本次用户明确授权小检查，运行结果如下，均未启动游戏：

| 实际检查 | 结果与范围 |
| --- | --- |
| `known-container-cleanup` | PASS；实际 Vector/Stack、CLQ/Deque、LBQ、EnumMap、Regular/JumboEnumSet、PriorityQueue 清理。混合份额邻居、EnumMap null、位图及并发新入条目保留，不调用目标 comparator/hashCode/equals。 |
| `platform-control-writes` | PASS；真实 Class mirror 的 VM 头部／实例字段拒绝与静态字段准入，直接毒化临时类 ReflectionData 后原始 VM 字段仍可读取。此分支检查生产准入与实际存储，不单独声称已安装拦截。 |
| `installed-platform-writes` | PASS，退出 0；实际 premain 安装后，真实 Unsafe 对 Class.name、Class.reflectionData、ReflectionData.declaredFields 的覆写被拒；真实静态 int 写和正常 JDK ReflectionData CAS 保留。 |
| `jni-live-table` | PASS；真实 premain、实际 `JNIEnv/*env`，整表尾部 VirtualProtect／WriteProcessMemory 被拒；用独立检查进程的原始系统入口损坏 GetMethodID/GetFieldID 后，实际 JNI 查询触发恢复与 VM 表发布；只读页权限恢复，合法非受控 GetVersion 保留。无伪造 Owner、table identity 或 ready 状态。 |

JNI 检查复用 `AuditFixNative.c` 的 `RONOVA_LIVE_JNI_ONLY` 分支，仅编译本次真实 JVM/Windows 操作；没有将副本控制后端、历史 seam 或旧矩阵编入本次检查 DLL。所有主动损坏均在独立检查 JVM 中，检查出口恢复原槽位和页权限。

保留的前置失败：初次检查编译缺 ASM 导出；独立 Java 准入检查最初缺少生产已有的模块开放，随后发现临时类未初始化导致静态初值覆盖了检查写入，修正检查准备后通过。JNI 首次用中文构建路径直连，在 JDK `appendToBootstrapClassLoaderSearch` 处报 IllegalArgumentException；同一构建产物暂存纯英文路径后 `AGENT_PREMAIN_READY:INSTALLED_FULL` 并通过。不把这次暂存当作生产中文启动路径的修复。

实际日志位于 `.work/v1.5-hardening-20261020`：`known-container-cleanup.log`、`platform-control-writes-complete.log`、`installed-platform.stdout.log`／`stderr.log`、`jni-live-table-staged.stdout.log`／`stderr.log`，此前失败日志同目录保留。

候选 Java105 / Native99 / Storage3 / Journal7：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3287932 | `f37c80c20dae260a90129f50ce03d9b0b6ba8da13372988c6ba309af51f35a80` |
| `ronova-pro-prelaunch.jar` | 51042 | `0b32b1e0d08e7af35afe97335d0b9d688c5c7d661913ce77e965901d6856503f` |

prelaunch 源码与产物未变化；Core 包含本轮 Agent、Bootstrap、Native 与客户端改动。网络协议已更新为 `v1.5-whole-mod-retry`，客户端／服务端需同步升级。Forge 总线漂移和客户端资源的本候选游戏内效果未运行；共享 PriorityQueue 非尾叶需目标比较器、未知私有语义等继续保留具体 GAP/PARTIAL。

## 2026-10-20：三个 P0 与 SuperSteve 两条具体漏口

接续 `1542cfc`。本轮实现已知容器整组直接清理、实体当前载体重定位与原对象类型恢复、在途旧方法及线程／任务来源的效果边界接续；补入 Class$ReflectionData 防投毒和原始 VM 声明读取，以及实际 InstrumentationImpl/JPLISAgent/JvmtiEnv 结构保护。没有恢复广谱容器 Hook，没有新增 SDK、隔离夹具或微批次验证。

实体 `_klass` 恢复使用原 Class 的实际 Klass 与 VM narrowKlass 参数，并要求已观察的原对象、相容布局和真实 CAS；未知对象头、未观察来源和不能解释的私有状态不按成功处理。原生结构范围依赖受支持 OpenJDK 17 Win64 布局和实际 VM/JPLIS 对象身份。完整改动范围见同日 A 批状态页。

遵守本轮安排：不运行起服、实机、测试、`validation/checks` 或独立消费者夹具。下列历史报告保留其原候选与原范围，不能替代本候选的游戏运行结果。

整包统一构建的首轮 Java 编译通过；Native 发现一个 JVMTI 常量名错误，修正后继续同一 `jar` / `reobfJar` 构建成功（19 秒、退出 0）。两份实际日志保留在 `.work/v1.5-p0-20261020/build.log` 和 `build-complete.log`。没有追加运行检查，保留原有编译警告。

当前 `distribution/v1.5-whole-mod` 候选为 Java105 / Native99 / Storage3 / Journal7：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3253883 | `07298d62a629549bc6be6974d903c03c6b45e85ee25460c1111c157f499608aa` |
| `ronova-pro-prelaunch.jar` | 51042 | `0b32b1e0d08e7af35afe97335d0b9d688c5c7d661913ce77e965901d6856503f` |

prelaunch 本轮无源码变动，沿用本次构建返回的匹配产物；Core 已包含本包新 Agent、Bootstrap 与 Native DLL。尚未进行本候选游戏启动及整组攻防实机确认。

## 2026-10-18：用户态启动与嵌套 Mod 分组修复

接续 `e4928eb`，成功空 JAR 扫描结果接入原索引；模块／外层文件别名改为合并发布；修正 jar/union URL 中特殊字符的物理路径解析；Core 在发现内嵌别名后继续查询其真实 Module，再执行整组策略。没有缓存早期 Module reader 的缺失，也没有缩减原写入／执行边界。

SYS 维持源码和可选接线，暂缓部署与扩展。源码收尾后唯一一次统一 `jar` / `reobfJar` 构建成功（1 分 57 秒、退出 0），20 个任务执行并重建此前清理的缓存。日志为 `.work/v1.5-whole-mod-20261018/startup-groups-build.log`。没有运行起服、实机、夹具或检查任务，也没有构建或部署 SYS。

当前候选双 JAR 位于 `distribution/v1.5-whole-mod`，Java105 / Native99 / Storage3 / Journal7 不变：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3209544 | `37f124605488326fc50a06b96e097e268777eb34f83a09b936a964fdb7e758e0` |
| `ronova-pro-prelaunch.jar` | 51042 | `0b32b1e0d08e7af35afe97335d0b9d688c5c7d661913ce77e965901d6856503f` |

构建保留既有 Mixin 映射、弃用与 Native 编译警告；没有据此扩跑检查。本候选尚无新的游戏启动耗时或整组实际效果结论。

## 2026-10-18：嵌套 Mod、废 SDK 和身份查询收尾

本轮仅实现三项明确收尾：Prelaunch/Agent 递归读取内嵌 JAR 的 Mod 别名并归入外层实际文件；删除六个未编译的合作式 SDK 文件；普通字段写入复用一个身份查询键和首次来源结果，保留策略调用后的来源重读与 gated 路径。源码收尾后 `jar` / `reobfJar` 统一构建成功，49 秒、退出 0；未运行起服、实机、夹具或检查任务。V1.5 允许的未知语义和不可用后端继续报告 GAP/PARTIAL，不计作新增 SDK 的理由。

首次沙箱调用在编译前因 Gradle native-platform.dll 加载失败退出；取得正常构建缓存访问后同一源码完成构建，无编译失败修补或追加构建。原日志保留在 `.work/v1.5-whole-mod-20261018/final-three-build.log`。当前 `distribution/v1.5-whole-mod` 的双 JAR：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3231087 | `acb49687739eeb195a2a7c5f2b0c2ece8e42065a621fc3b9aae542c287fa4643` |
| `ronova-pro-prelaunch.jar` | 51052 | `b9122df7a3caed70dd6514016aa3bfebbfe7b66608effd3a24cd4cba29ffbfdc` |

## 2026-10-18：按新安排仅交付 A/B/C/D 生产候选

用户撤销本轮后续运行检查安排，要求源码全部写齐后仅作统一编译。已终止旧 Forge 进程（PID 128892，对应会话退出 1）；此后未运行起服、实机或夹具，也不再轮询旧起服进度。本节记录源码及候选交付，不能作为整组效果通过报告。

生产改动集中接入 SavedData 首读与真实保存、声明 holder 增量/共享份额、未 submit Future/CF 与后继、私有资源退役、外部方法与动态/隐藏定义归属、Native/JVMTI 效果门、整组事件/网络/UI/绘制清理及世界原始时间字段。过滤保存的真实 native 截断/写入与当前组代次共用效果定序，未知关闭沿原结果保留。`ControlBridge` 普通字段不再因 Ronova 自保或全局选集非空而逐次加锁建回执，字段声明解析按真实 Class 缓存，原作用域与写入策略保留。

D 沿原 writer 增加 Journal7 载体与 upgrade/replace/uninstall 交接，旧日志及未决 key/attempt 不改写，匹配文件变化必须显式交接。未绑定旧载体、私有数据格式变化、未匹配旧栈和不可用后端继续报告缺口；不把手工换文件写成运行中卸载成功。

全部生产代码接齐后统一构建。前两轮编译分别发现 prelaunch 的两处 `java` 局部变量遮蔽包名和 ResourceBridge 的一处 `close` 变量重名；修正后最终 `jar` / `reobfJar` 成功（42 秒，退出 0）。构建日志为 `.work/v1.5-whole-mod-20261018/abcd-production-build.log`，包含前述失败与续编。没有运行任何检查任务。现有 Mixin 映射、弃用及 Native 比较范围警告保留，未因警告扩跑检查。

本次统一构建产出 Core、Agent、Bootstrap、prelaunch、用户态 Native，Storage 源码未再变更且任务复用已有匹配产物；没有构建或加载 SYS。当前配套候选在 `distribution/v1.5-whole-mod`，版本 `1.5.0-dev`，Java105 / Native99 / Storage3 / Journal7，协议 `v1.5-whole-mod-clock`：

| 候选 | 字节 | SHA-256 |
| --- | ---: | --- |
| `ronova-pro-core-1.5.0-dev.jar` | 3226617 | `409db8d624f327621e76e507e26839059651c88a66d32fc0a904619563e13465` |
| `ronova-pro-prelaunch.jar` | 48190 | `15d5c146d5502fb62ed1c549fd4abaa241706a2a4741695064dab548a2f8ef64` |

Storage 同组后继保留原工作 key：必须同真实 source/code/文件身份，原效果和资源已闭合，且当前后继准确覆盖原移除范围或承接其实际 expected 图像。最终提交同时复核当前 canonical 作用组和准确保存范围；原未知事务不因新组读回成功而结清。独立读回通过原 Snapshot 精确剪除与 List 重定位接回下一代保存基线，重复查询不推进代次。

构建后仅更新匹配候选与本报告，没有运行 WorldFixture、游戏客户端或实际驱动。尚无本候选的世界加载、整组停用/保护、保存或重启通过结果。

## 2026-10-18：V1.5 整 Mod 生产包与真实 Forge 场景

以下保留停止检查前的过程记录，当前候选不继承这些运行结论。

本轮接续主工程 `e4ba753`，候选 `1.5.0-dev`，协议 `v1.5-whole-mod`。生产源码先整包写齐，之后统一编译 Core/Agent/Bootstrap/prelaunch、GNU C23 Native 与 GNU C++23 Storage。当前双 JAR 集中在 `distribution/v1.5-whole-mod`；WorldFixture 只置于本地真实起服场景，不是交付 Mod。

已完成活动链收敛：实际文件/Module 归组与批量命令、全方法入口返回、在途发布门禁、共用存量清理队列、双端独立结果及保存选集交接；直接 NTFS TxF/句柄实现替代 Storage SDK 包装。Native 私有分配器/对象 SDK、CRT/STL 字段租约及 Java getter/Function 见证链退出活动安装，未观察的私有语义不伪造成功。

实际检查目录：`.work/v1.5-whole-mod-20261018/server`，使用本机已有 JDK17、Forge 1.20.1-47.4.23 libraries 和新建隔离世界。用户明确授权原有 `ronova-pro-isolated-fixture.jar` / WorldFixture 的 `mod-group` 分支作为真实目标 Mod；两个 modid 共用一个真实文件。未运行 metadata seam 检查、`validation/checks` 或独立 C++ 消费者。

集中构建日志为 `.work/v1.5-whole-mod-20261018/unified-build.log`。首次完整编译补齐缺失 LabelNode 导入后成功；后续只因真实起服故障续编相关部分。实际曾遇到中文目录 bootstrap JAR 原生重开失败、Fastutil selector 循环加载、ASM 导出前类校验、JDK 全局引用扫描、arraycopy 重扫 PE 导入表，以及错误使用不存在的独立 System.arraycopy 导出而导致的安装递归；分别改为临时目录载荷、选择器先初始化、延迟 ASM 筛选器、真实边界筛选、固定准确槽核对和注册绑定发布前准备。失败记录保留。

后续真实栈另定位两处旧路径：已返回空值的内存账本调用仍预先捕获来源，现已删除空调用及其专用包装；Class.newReflectionData 的准确实例缓存 CAS 虽已识别，却被后续 represented 控制对象检查反复拒绝，现沿真实字段/调用链保留其缓存写入，并继续检查停组来源。普通 ASM 树原先在解析时两次 serialize/snapshot，现冻结真实 parser 输入、Module 和 member parent，按实际变换/元数据消费者惰性物化，使用一致 canonical 基线，未捕获变化保留 unknown patch/gap。该完整修正续编成功。

授权后的真实栈还定位普通树复制/元数据承接的 canonical/内容序列化及普通字段/数组逐写锁；现以冻结 parser 输入、实际父属和已有真实成员来源承接粗根，不声明当前字节未改，真实变换与回放仍走原检查。无策略且非控制、来源载体、资源、队列或发布目标可省回执，原字段/数组策略仍执行；首次 PUTSTATIC 空值约定错误已修正。实机已过 Core 177 与目标 5 个 Mixin 准备，进入 `Minecraft.server.Main` 后报 JOML `AxisAngle4f.readExternal` 的非法常量池类型；实际输入为 classfile 46.0，新增 Class 常量要求 49，共用写出器仅提升旧版本至 49.0。修正续编成功，当前候选继续重开真实场景；未运行独立消费者。

`f627327` 后续已过游戏注册表初始化，真实 EventBus 6.2.33 在变换检查报 `EVENT_DISPATCH_IMAGE_INCOMPLETE` 后退出，未进入世界。实际 JAR 字节码显示三参注册注入正确，完整性校验仍查旧双参；现统一描述符并校验准确 bus/owner/listener 与成功 RETURN，保留原分派和拒绝行为。同期修正 Brigadier 同名注册合并到旧节点时的实际挂载身份，未找到注销节点不再丢登记或误报完成。该批生产源码统一编译成功（35 秒），同一真实 Forge 场景已重开。

该实际场景随后按用户新安排终止，尚不将 premain/ModLauncher/Minecraft Main 启动升级为目标停用、世界清理或保存重启通过。`ForkJoinTask$Aux` 待验收未伪造为就绪。客户端 GUI 与真实驱动未在本次头部场景运行，外部旧活栈及未观察私有状态保持未覆盖范围。

## 2026-10-18：C 接续——NT 内存传输与私有字段存储

接续 `2ea6410`，仅主工程 `D:\桌面\ronova_GPT`。Java105 / Native99 / Object SDK3（保留1/2）/ SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。整包源码写齐后统一构建。

实际 ntdll NtReadVirtualMemory/NtWriteVirtualMemory 及 Zw 别名按真实 provider、准确符号和地址接入，同址 Nt/Zw 不混淆读写及 NTSTATUS/BOOL。已有 Win32 Read/WriteProcessMemory 接同一多端存储处理，固定实际 process 对象副本后才决定远程端是否属于本 JVM；远程数值地址不制造本地归属。本地 data src/dst 和可选 SIZE_T count 输出保留准确 row/generation；同块读写和 count 别名一次准入，不互相误拒。

控制范围和 count 输出在副作用前判断；计数不借 typed SDK 权限。读取 creator/revision/raw-copy 贡献并保留未知，真正原调用之外只持计数租约，复用既有私有控制注册 gate 串行最后检查与一次非 alertable 原复制。NT 拒绝返回准确非成功状态，Win32 继续原 BOOL/LastError；线程 LastNtStatus 与返回 NTSTATUS 独立保存，不硬编码 TEB 偏移。原 count 指针不暂存、不预清、不复读，真实失败和异常仍记录可能部分效果，不把返回或 count 值当作完成/所有权权限。

统一生产构建 `build-c-java105-native99-nt-memory-20261018.log` **PASS，1分11秒**；真实 Host/C++ DLL 及 Java 入口编译退出0。Host 检查初次编译遗漏 ReadProcessMemory 函数指针声明，补齐后通过，生产代码没有为检查放宽。构建中的旧 Mixin/格式警告及一次 daemon socket type-tag 日志保留，任务正常完成；只运行新增 nt-memory 分支，没有重跑旧矩阵。

`c-java105-native99-core-nt-memory-20261018.log` **一次运行退出0，正常 JVM 结束**：真实 named Core 沿本包 ProMod/ProRuntime 启动绑定，无供给 owner/producer 元数据。4个实际 Nt/Zw 和2个 Win32 入口的 IAT/GPA、实际 SAME_ACCESS 进程副本及当前进程 pseudo handle、同 Heap row 源/目标/count、NULL count、合法 data/count 别名、零数据长度原 count 输出均通过。VM GetEnv 控制槽的数据和 count 写入在副作用前拒绝，无效 handle 不能抢过本地 count 保护。NT 非成功拒绝保留原 LastError/LastNtStatus/本模块 CRT；Win32 原错误合同保留。

真实两页 NOACCESS 场景中，Nt/ZwWriteVirtualMemory 与 WriteProcessMemory 均失败但实际 count=16、目标改变15字节；Nt/ZwReadVirtualMemory 和 ReadProcessMemory 本次 count=0、改变0字节。原 NT STATUS_PARTIAL_COPY 与 Win32 ERROR_PARTIAL_COPY、count 和已有部分效果原样保留，没有把失败当成无副作用。非 SDK C++ 私有类的实际 new、6类传输修改/读回、getter 和原 delete 通过。独立 system-loader DLL 对普通 Heap 的真实写入允许；Core/foreign 对 SDK payload/scalar 活借用及归还后的 data/count raw 写均准确拒绝，深层 Core 不借权。原 SDK 容器修改、析构和 HeapFree/HeapDestroy 通过。

本次未运行远程进程传输、并发竞争/受限收尾 Try 失败场景或真实驱动；这些路径不计实测通过。源代码保留无法发布时的未知及准确在途租约，不改已发生的原返回。

候选为 `distribution/candidate-c-java105-native99-nt-memory-wip-20261018` 的匹配双 JAR。只扩实际传输入口及准确存储关系；直接 syscall、CPU/内联字段访问、其他未观察入口和任意非合作 C++ 字段类型/所有权语义仍未推定覆盖。自身外观、武器注册、源码 ZIP 及完整游戏/存档重启未做，完整 C/B 未标完成。

## 2026-10-18：C 接续——CRT 原生内存入口与非 SDK 私有存储

接续 `f718bbf`，仅主工程 `D:\桌面\ronova_GPT`。Java105 / Native98 / Object SDK3（保留1/2）/ SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。整包源码写齐后统一构建。

补入 memcpy/memmove/memset、宽字符版本和两类 secure copy/move 共10种 CRT 签名的准确导出适配；仅存在真实导出时接入。本机 UCRT 实际导出其中7种，非 secure 的 wmemcpy/wmemmove/wmemset 无导出，不计覆盖，不伪造入口。目标按实际 provider、准确名称及地址建立，不将同址 memcpy/memmove 或不同宽度/参数 ABI 混为首个符号。已观察范围持准确 storage row/generation 的双端使用，合法同块重叠移动合并读写角色；新增租约不跨原调用持有目录或 row 锁。未观察普通双端直接保留原函数，不进 Java、不补造归属。

读取实际 creator/revision/此前 raw-copy 贡献，真实调用后将不完整贡献传到后续读取与复制，保留未知及不透明状态，不授予任意 C++ 字段、构造析构或字节恢复权限。活借用、typed 对象和控制范围继续拒绝未获准确语义授权的原始写；两端范围的任何部分命中已观察存储都不降级未知。secure 错误按原函数可能触及的范围接续，copy_s 原清零与 move_s 原无写错误分开；零访问原合同不因指针数值误拒。非零宽字符范围溢出直接拒绝，不声称保留该无效调用的原故障/handler行为。

准确活跃的 SDK 写入和 sequence 原修改体，沿当前线程、真实 provider、完整来源、原边界和准确 row/generation 接续 CRT 内部动作；新分配只记录本次实际出生关系，不赋予额外写权，不把原合法修改误记为未知 raw 写。借用期间及归还后 typed 对象的任意 raw 写仍拒绝；既有未知不会因此清除。

统一生产构建 `build-c-java105-native98-crt-bytes-20261018.log` **PASS，1分24秒**；Java 检查入口、真实 Host DLL 及既有 C++ 夹具编译退出0。Zig 无现成 `ucrt` 导入库，使用 `HostLoadUcrtBytes.def` 为系统真实7个导出生成导入描述，不含替代实现。只跑本次直接相关路径；旧编译警告未扩改。

`c-java105-native98-core-bytes-final-20261018.log` **退出0，正常 JVM 结束**：实际 named Core 沿本包 ProMod/ProRuntime 启动绑定，不供给 owner/producer 元数据；7个真实 UCRT 入口的 IAT/GPA、同块重叠移动、填充、secure 宽字符计数、原返回/errno/LastError、参数错误处理一次执行、copy_s 全容量清零和 move_s 错误不写均通过。真实 VM GetEnv 控制槽写入在原调用前拒绝，零长度数字指针保留原合同；非 SDK C++ 私有类的实际 operator new、标量/字节/宽字符 getter 读回和原 delete 通过。独立 system-loader DLL 对普通 Heap 的合法写保留，对 SDK payload/scalar 活借用期间及归还后的 raw 写均拒绝，深层 Core 不借权；SDK 原容器初次修改、析构及 HeapFree/HeapDestroy 通过。

初次检查的基准快照自身调用受控 memcpy 读取控制表，未取得原字节；改用直接逐字节读取。随后将检查的精确错误期望区分为控制区 ACCESS_DENIED/EACCES 与 typed/lifetime 冲突 BUSY/EBUSY。原中断日志保留，没有为检查放宽生产防护，也没有重跑旧矩阵。未加载驱动或启动完整游戏。

候选为 `distribution/candidate-c-java105-native98-crt-bytes-wip-20261018` 的匹配双 JAR。仅观察到的实际 CRT 调用被纳入；内联/直接 CPU、未观察的原始调用、任意非合作 C++ 字段与所有权语义仍有缺口，完整 C/B 未标完成。自身外观、武器注册、源码 ZIP 及完整游戏/存档重启未做。

## 2026-10-17：C 接续——异步完成与私有请求状态

接续 `91b1eab`，仅主工程 `D:\桌面\ronova_GPT`。Java105 / Native97 / Object SDK3（保留1/2）/ SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。整包写齐后统一构建。

补入 GetOverlappedResult/Ex、CancelIoEx、IOCP 创建/单次与批量取回/人工投递、完成通知模式共8个实际 Host 入口。已观察 Heap OVERLAPPED 使用内核消费的私有副本，绑定真实文件对象、分配代次及本次请求；公开 Internal、人工 Post 和取消成功均不赋予完成权。真实终态才回写 InternalHigh/Internal 并一次归还准确缓冲区、状态块及句柄使用，提交尚未返回时不提前结清。查询、取消和实际端口消费者核对当前真实调用图像及完整来源，不借深层 Core 帧。

IOCP 保存实际端口对象和文件首次 key，沿成功结果累积通知模式。内核私有指针只在私有临时输出中读取，返回原公开 OVERLAPPED；迟到旧 packet 不结清同地址新请求，人工公开 packet 不释放存储。等待不持 ledger 锁或长期复制端口句柄，保留原关闭唤醒、超时、失败完成及 Win32/CRT 错误状态。线程池收尾只检查本次私有请求，不扫描 JVM 或替用户消费端口。

统一构建 `build-c-java105-native97-async-final-20261017.log` **PASS，15秒**，本包 Java/Native 检查入口编译退出0。初次并行夹具编译遇输出目录未创建，脚本自行创建后通过；初次构建和运行脚本将普通 stderr 提示当异常，按真实退出码处理后继续，原中断记录保留。没有因旧编译警告扩大修改范围。

`c-java105-native97-core-async-final-20261017.log` **退出0，正常 JVM 结束**：真实 named Core 使用本包生产 ProMod/ProRuntime 启动绑定，不供给 owner/image/producer 元数据；不同 system-loader 实际 DLL 的完成查询与取消均拒绝，深层 Core 帧没有借权。实际8MiB写、带16字节偏移的读和取消候选读共观察3次 ERROR_IO_PENDING；GetOverlappedResult/Ex、IOCP 原公开指针/key/count、人工 Post 原值、真实 alias、通知模式、空队列 timeout、控制输出前拒绝且 VM 槽/count/OVERLAPPED 不变、最终 HeapFree/Close/HeapDestroy 与原回滚均通过，Java 原文件字节保留。

本次内核完成快于后续探测，`held_probes=0`、`completed_before_probe=2`、`public_Internal_CAS=0`，没有实测仍挂起时的公开状态伪造/提前释放拒绝；首代需要真实 dequeue，旧 packet 跨代场景 **NOT_OBSERVED**。取消候选实际自然完成，真实 ERROR_OPERATION_ABORTED 场景未出现；没有把取消请求当作完成。只跑新增异步路径，没有重跑旧 CNG/C++/Storage 矩阵或完整游戏。

候选为 `distribution/candidate-c-java105-native97-async-state-wip-20261017` 的匹配双 JAR，随源码同步 GitHub。未观察的栈/Java OVERLAPPED 保留原调用，真实 pending 继续未决；任意原始调用、CPU 私有字段语义和未适配 C++ 对象没有被推定覆盖。完整游戏进服、同存档重启未运行，完整 C/B 未标完成。

## 2026-10-17：C 接续——KTM/TxF 句柄与事务文件私有状态

接续 `77d122f`，仅主工程 `D:\桌面\ronova_GPT`。Java105 / Native96 / Object SDK3（保留1/2）/ SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。整包源码写齐后统一构建。

补入11个 KTM/TxF 原生入口及5类同步事务文件 I/O。真实成功才登记事务、文件和句柄代次，GUID 连接实际重开，Open 消费与前置检查相同的私有快照，不能在门后改指针重定向已知事务。内核对象比对和同权限内部句柄固定当前准确目标；应用 Close/Duplicate/Flags 沿原结果更新，CloseSource 即使原返回 FALSE 也保留其真实关闭语义，最后应用别名真关闭后同步归还内部引用，不推迟系统隐式回滚。原 Win32 调用不跨 ledger 锁，元数据失败不改写原成功，loader/受限路径只 Try。

已知目标独立核完整来源、实际 caller 图像和在途状态，深层 Core 帧不能授予外部 DLL 事务资格。已知事务文件的 read/write/count/OVERLAPPED/seek 输出接准确缓冲区代次；原始输出拒绝控制区、活借用或 typed 对象，输入合并实际已观察贡献。普通未知文件句柄保原调用及控制区保护，不接管既有 IoBridge 租约。文件位置、句柄、阶段与磁盘状态保持不透明，不做字节撤销、自动提交/回滚/取消。实际 IO_PENDING 或异常未返回保留在途句柄和存储；异步事务 pending 与立即失败分开，Undetermined 只结清已返回的同步失败，不能冒充异步完成，terminal 依实际 outcome 接续。

`build-c-java105-native96-txf-state-20261017.log` 首轮 **PASS，1分10秒**，复用 Java/Native 夹具编译退出0。`c-java105-native96-core-txf-state-20261017.log` **退出0，正常 JVM 结束**：真实 named Core 绑定和非零 SDK/CNG 原生命周期继续通过；不同实际 system-loader DLL 的已知事务回滚被拒，随后 Core 查询仍 active。真实 GUID query alias、Heap I/O buffer、同步读写/定位/截断/刷新、文件 duplicate 的共享位置、控制槽 ReadFile 输出拒绝且 count/位置不变、CloseSource 转移与原关闭、同步回滚共享 outcome 均通过，Java 临时文件原字节保留。生产双文件 TxF、双槽/第三 busy、foreign Ticket/token 拒绝和 no-permit 回滚/收尾也通过，没有绕过生产 commit permit。

收尾补异步状态区分，最终增量 `build-c-java105-native96-txf-async-final-20261017.log` **PASS，25秒**。新增检查夹具首次编译遗漏 CommitTransactionAsync IAT 声明，补声明后编译退出0，原失败日志保留。`c-java105-native96-txf-async-final-20261017.log` **退出0，正常 JVM 结束**：query-only 真实句柄的异步提交保留原 ERROR_ACCESS_DENIED，真实 Undetermined 查询后事务仍可继续原 I/O 与回滚；准确外部来源拒绝及控制输出保护继续通过。最终只复查受影响的事务分支，没有重复 CNG/C++ SDK/Storage 已通过链或旧矩阵；成功异步请求的真实完成场景未运行。

匹配候选为 `distribution/candidate-c-java105-native96-txf-state-wip-20261017` 的两份 JAR，随源码和既有摘要同步 GitHub。未观察的 OVERLAPPED 完成、非合作私有对象语义与原始调用仍有缺口；完整游戏进服、同存档重启 **NOTRUN**，完整 C/B 未标全部完成。自身外观、武器注册和源码 ZIP 未做。

## 2026-10-17：C 接续——实际 Core 来源与 BCrypt 原生状态

接续 `556f387`，主工程 `D:\桌面\ronova_GPT`。Java105 / Native95 / Object SDK3（保留1/2）/ SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包源码写齐后统一构建。

实际 ProMod/ProRuntime 的既有 loader、module、CodeSource 绑定发布独立 native-state owner，不将 Core 标作外部 producer。首次 Native 未就绪保持 pending，Storage 不锁定 attempted，可沿原绑定重试；普通独立消费者保留未覆盖行为。SDK 查询保留 Host 分配使用的完整来源和 unknown，资格核对当前真实 caller 图像及其库、绑定、加载关系，不能借深层 Core 帧取得资格。准确 Core JNI entry 补入真实 HostImage scope，静态 PE/TLS 观察沿同一角色接续，不回填旧未知分配。

新增六个真实 BCrypt 哈希入口，IAT 与精确 GPA 共用 provider、handle 代次、完整来源、在途调用及关闭门。目标检查独立于新建资格；未知调用方不能绕过已知句柄。原成功才登记，原 Destroy/Close 成功才退休，不自动销毁或重放哈希私有内容。已观察 external hashObject 持准确 allocation generation 的 users/reading，活哈希阻释放，真实 DestroyHash 后归还；已知起点越界不降级未覆盖。未知 stack/JNI/provider 缓冲区保留原调用及不透明缺口。原 flags、NTSTATUS、输出、异常及错误状态保留；登记失败不改写原成功。loader/受限回调只 Try 目录锁，冲突保留未决句柄和租约。

`build-c-java105-native95-core-cng-20261017.log` 首轮 **PASS，1分9秒**；复用 Java、两个 Native 夹具和实际 C++23 消费者编译退出0。`c-java105-native95-core-state-20261017.log` **退出0，正常 JVM 结束**：本地真实 named Core 加载本包未修改生产 ProMod/ProRuntime，运行同一 ProMod 启动绑定体；外部直接绑定被拒，Core producer 身份仍 false，没有供给 owner、image、NativeLibrary 或 source metadata。实际 Native loader/binding 链取得非零 PMR/std allocator、typed/scalar/buffer、vector/string 回执，4个旧视图拒绝；生产身份查询和真实双文件 TxF、双槽、第三请求 busy、foreign Ticket/token 拒绝、原文件保留及 admitted=0 通过。该路径未启动完整 Forge。

同一 Core 运行中，六个 BCrypt GPA 为真实 Host entry，IAT 与 GPA 原 SHA256、Open 继承 reusable flag 的两次 Finish 通过；真实 Heap 外部 hashObject 的活哈希释放被拒，Destroy 成功后原 HeapFree 成功。`c-java105-native95-custom-state-20261017.log` **退出0**：既有明确供给来源 seam 的非零 SDK、活 view/借用/typed 阻释放及原析构继续通过。`c-java105-native95-ordinary-host-20261017.log` **退出0，正常 JVM 结束**：普通来源回执仍0，不冒认 Core。没有重跑旧矩阵。

匹配候选为 `distribution/candidate-c-java105-native95-core-cng-wip-20261017` 的两份 JAR，随源码和既有摘要同步 GitHub。未适配的原生私有语义、非合作分配器和裸 CPU/原始调用仍有缺口；完整游戏进服与同存档重启 **NOTRUN**，完整 C/B 未标全部完成。自身外观、武器注册、源码 ZIP 未做。

## 2026-10-17：C 接续——实际 Storage 事务与拒绝后的生命周期

接续 `5316bcf`，仅主工程。Java104 / Native94 / Object SDK3（保留1/2）/ Storage3 / Journal6 不变，未改接口布局。写齐本包后统一构建，没有恢复容器 Hook、添加外观或武器、生成源码 ZIP。

Transaction/Hash 的实际句柄、阶段、偏移和提交回执按 opaque/use 接入，避免独立字节恢复重放外部操作；四个配置字符串接真实 allocator_adapter、公开序列和当前视图借用。关闭只对已成功的同代关系幂等，旧视图拒绝再用。身份查询及事务的独立元数据保留原 owner/token 和真实析构阶段；全序列关闭后同次执行原析构及完成回执。root 使用真实 std::allocator，原释放未执行可重试，已执行但未确认仅重试收尾；实际存储与 context 收口后才发布退休和归还 admission。身份查询拒绝节点由后续入口锁外重试。

到期保活在 slots 锁外调用 Java，LocalRef 固定原 owner，回来核对同槽 revision/token/owner；旧回调不处置移交后的状态。该锁域由源码核对，没有冒称执行了重入回调检查。SDK 补 opaque/use、scalar mutate、显式 borrow 完成，并保留 errno/_doserrno/LastError。

`build-c-java104-native94-storage-state-20261017.log` 首轮 **PASS，1分14秒**；本包 C++、Java 与两个复用 Native 入口编译退出0。`c-java104-native94-storage-opaque-20261017.log` **退出0**：明确供给 image/source 的既有实际夹具通过非零 opaque 借用、原回调一次、借用拒绝标量修改与析构、部分序列关闭重试及原析构收口。`c-java104-native94-storage-transactions-20261017.log` **退出0**：真实双文件 TxF、双槽容量、第三请求 ERROR_BUSY、错误 Ticket/token 拒绝、准确 owner 仍可继续、无许可回滚、原文件不变和 admitted=0。

`c-java104-native94-storage-host-20261017.log` **退出0，正常 JVM 结束**：本包 premain/Bootstrap/Host 为 INSTALLED_FULL，原 C++ 消费者及生产身份查询结果通过；普通来源回执仍0，coverage=uncovered，不当作 Core producer 覆盖。未跑旧矩阵；游戏进服、同存档重启及实际 Core producer 集成 **NOTRUN**。未合作的分配器、外部状态和裸 CPU 字段语义仍有缺口，完整 C/B 未全部完成。

匹配候选为 `distribution/candidate-c-java104-native94-storage-state-wip-20261017` 的两份 JAR，随本包源码和既有状态/报告同步 GitHub。

## 2026-10-17：C 接续——非 PMR 分配器与公开序列字段

接续 `f047d36`，仅主工程 `D:\桌面\ronova_GPT`。Java ABI104 / Native ABI94 / Object SDK3（保留1/2）/ SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。先写齐分配器、字段、实际 Storage 和必要夹具源码，再统一构建；没有自身外观、武器注册、容器广谱 Hook 或源码 ZIP。

新增 `allocator_state` 与 `allocator_adapter<T,Upstream>`，支持真实 raw-pointer 标准分配器；复制和 rebind 共享实际状态，等价还要求同上游，原 allocate/deallocate/construct/destroy 及异常按实际契约接续，不创建替代清理函数。标准 bad_alloc 保留旧 context/块，未约定的异常继续未决；零元素结果仅有实际生命周期，没有字节访问权，也不伪造一字节请求。CPP、jemalloc 和 mimalloc 的准确已观察 backing 接 CUSTOM 子块目录；父块释放、Heap/arena/heap bulk 检查准确后代的借用、typed lifetime 和在途操作，内层物理释放与外层 SDK 完成配对，safe-delete 迁移保留子代。未观察地址不反查私有 allocator 布局。

Object SDK3 采用实际 string/vector 的公开 data/size/capacity，变更前关闭旧视图和 storage 关系，原方法一次，正常或异常完成后按真实存活形状重绑；定长数组逐个登记真实标量。capacity 不冒充已构造元素，inline 数据仍借用，不复制 STL/vptr 或重放 resize/析构。视图查询与写入先核实际形状和准确回执，未登记的同址长度变化拒绝旧视图写入。历史版本带独立 objectView；同源也不能跨 view 合并，整条恢复链须匹配当前 view，旧历史及来源保持 pending。

生产 Storage 的两种字符字符串使用同一实际 allocator_state，状态在原字符串析构后关闭；公开序列管理 reserve、增长、读取及析构关系，原 bool 判定继续接字段门。原 identity 函数体保留，仅将两个 allocator 模板参数接入签名；requested 的实际读取借用在已获准的 identity 变更作用域内覆盖原业务调用。

整包首轮 `build-c-java104-native94-allocator-containers-20261017.log` **43秒失败**：新 allocator_prepare 签名增加 outer 参数，但 context 创建调用遗漏该参数；实际新 context 没有外层租约，补 NULL 后只续编受影响 Native 与对应 JAR。`build-c-java104-native94-allocator-containers-final-20261017.log` **PASS，37秒**；C++23 实际消费者、Java 检查器与两个既有 Native 夹具编译退出0，原编译失败日志保留。

`c-java104-native94-custom-containers-state-20261017.log` **退出0**：同一实际夹具取得非零 PMR/CUSTOM、标准分配器共享状态、private scalar、vector/string view 回执；实际标准上游 backing 是 CRT，未冒称 named C++/je/mi 运行。活 view 拒绝原 reserve/resize 且原调用计数不增；归还后真实 grow/shrink、旧分配/物理 backing 退役、原分配释放配对及旧 view 拒绝通过。不同 objectView 的实际 revision 链拒绝恢复，当前元素不变且来源/history 保留；同址同容量绕过适配器缩短 vector 后，旧长度 assign 同样拒绝且现存元素不变。真实 HeapDestroy 被活自定义后代挡住，原 heap 仍有效；标量所选恢复、payload 不变、原析构、pool.release、HeapDestroy 和两 context 收尾通过。来源及 image 为明确供给的隔离 metadata seam，不是实际 Core producer 发现证明。

`c-java104-native94-object-v1-20261017.log` **退出0**：直接受影响的 ABI1 字段快照、所选恢复、borrowed 排除、借用/析构和准确原始释放门继续通过。`c-java104-native94-host-allocator-containers-20261017.log` **退出0，正常 JVM 结束**：本包 premain/Bootstrap/Native94 为 INSTALLED_FULL，真实 PMR 和非 PMR std::allocator 的 vector/string grow/shrink 原调用、析构，以及生产 Storage identity 原结果通过；该普通来源回执均0，明确 coverage=uncovered，不以原调用成功冒充受控语义覆盖。没有跑旧矩阵，未重复已通过路径。

匹配候选 `distribution/candidate-c-java104-native94-allocator-containers-wip-20261017` 的两份 JAR 随源码、SDK 和既有报告同步 GitHub。这些入口接明确适配器和已观察事实，不自动推断任意第三方 allocator、私有对象表示或裸 CPU 状态；本机没有把新增 CUSTOM 桥对各 named CPP/je/mi provider 全部作实际运行通过声明。真实 Core producer 集成、游戏进服和同存档重启 **NOTRUN**，完整 C/B 不标全部完成。

---

## 2026-10-17：C 接续——通用分配器接入与 C++ 字段语义

接续 `5cc4543`，仅主工程 `D:\桌面\ronova_GPT`。Java ABI103 / Native ABI93 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本轮先写齐通用分配器、字段语义及实际 Storage 消费者，再统一构建；没有恢复容器广谱 Hook，没有自身外观、武器注册或源码 ZIP。

新增 `ronova_allocator_state.h/.hpp` 与 CUSTOM 分配类别：真实 caller image、完整来源、context 和独立世代约束 allocate/free/realloc/reset/destroy；没有客户端 owner 列表或清理回调。初次分配必须绑定本次原调用实际观察的 Heap/Virtual/CRT backing，后续只能沿仍存活的准确 backing 或明确父子回执接子块。裸地址、旧父代次、越界及存活兄弟重叠不取得回执；父块和 bulk 操作检查全部后代的活借用、typed lifetime 与在途操作。失败重分配保旧，成功同址也换代，异常或缺失完成继续未决；不会自动释放 CUSTOM 块。默认 PMR 包装器未取得实际 context 时保持原调用，已受控的失败不降级；成功分配后的登记丢失保留 gap，原释放执行一次并保留实际错误状态。

对象 SDK ABI2 保留 ABI1，新增实际标量/枚举成员、嵌套对象及 owned/borrowed buffer 关系。字段回执绑定对象、分配代次与本次构造，明确边界的原赋值只执行一次；pointer、handle、STL/vptr、原子表示及未知类布局不推断为普通字段。外部 buffer 必须已有真实 allocation row，owned 还需相同完整创建来源；活关系阻止原始释放，结束关系后才执行原析构和配对释放。恢复统一检查准确对象与目标代次、全部创建者/读者停止及本次 selection；borrowed 排除同样约束普通已观察行，成功恢复后更新对应字段 epoch。

生产 Storage identity 的两个真实 bool 已接 typed 字段；字符串仅关联公开的 data/size 外部只读缓冲区，原 reserve、增长、析构和释放继续由字符串实现负责，不复制私有表示。SDK 缺失或零回执仍明确为未覆盖。

整包 `build-c-java103-native93-allocator-fields-20261017.log` 首轮 **PASS，1分22秒**：Native93、Storage、Agent/Bootstrap/Core 和重混淆 JAR 均构建成功。实际 C++23 PMR 消费者、Java 检查器与两个既有 Native 夹具编译退出0。两个 Native 夹具的最初命令曾因新输出目录尚未创建而退出，补建目录后成功，原日志保留。

`c-java103-native93-object-v1-20261017.log` **退出0**，仅复查直接受影响的 ABI1：实际 CRT 分配、非零 SDK 回执、字段快照与所选恢复、borrowed 排除、活借用阻析构、原始 free/realloc 拒绝，以及原 C destroy 后配对释放均通过。

`c-java103-native93-host-custom-fields-20261017.log` **退出0，正常 JVM 结束**：实际 premain/Bootstrap/Native93 为 INSTALLED_FULL；真实 HeapCreate/Alloc/Free 支撑标准 unsynchronized_pool_resource，C++ 私有成员原赋值、owned payload 原修改、虚调用、原析构、pool.release 和 HeapDestroy 完成，实际 Storage identity 结果也通过。该普通来源的 context/object/field/buffer 回执均为0，明确报告 coverage=uncovered；不以原调用成功冒充受控语义覆盖。

新隔离路径首轮 `c-java103-native93-custom-fields-state-20261017.log` 失败：夹具在 toolingRestricted 状态通过已拦截 GetProcAddress 解析 SDK getter，被现有 ERROR_BUSY 正确拒绝，尚未进入 SDK。只改夹具准备顺序，在安装 IAT 前缓存实际 getter 地址，随后仍由真实 fixture caller 和明确供给的准确 image/source metadata 调用 SDK；未改生产门禁。仅重编两个受影响夹具，`c-java103-native93-custom-fields-getter-20261017.log` **退出0**：真实标准 PMR/Heap backing、非零 CUSTOM 分配及 typed/scalar/owned 回执、跨 provider 拒绝、活借用/typed 阻实际原 free/reset/realloc、准确标量恢复与 epoch 更新且 payload 不变、原 C++ 析构和实际 pool/Heap 收尾均通过。这里是隔离来源登记 seam，不是实际 Core producer 发现或游戏权限证明；已通过的 ABI1 和普通 Host 未重复运行，没有跑旧矩阵。

匹配候选 `distribution/candidate-c-java103-native93-allocator-fields-wip-20261017` 的两份 JAR 随源码、SDK 与既有报告同步 GitHub。未接入的自定义/静态/内联分配器、不支持的 backing、未声明对象字段、外部私有状态和裸 CPU 调用仍有边界；真实 Core producer 集成、游戏进服和同存档重启 **NOTRUN**，完整 C/B 不标全部完成。

---
## 2026-10-17：C 接续——mimalloc、CRT 对齐分配与未适配 C++ 存储

接续 `d7bf44e`，主工程 `D:\桌面\ronova_GPT`。Java ABI102 / Native ABI92 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本轮先写齐分配器、C++ 和调用衔接源码，再统一编译；没有恢复容器 Hook，没有自身外观、武器注册或源码 ZIP。

新增实际 mimalloc v2 的24个公共分配/heap 入口，按精确公开名称与实现地址接 IAT/GPA；只发现实际观察 image 及其已解析相关导入，不枚举全进程模块。provider 在途租约和 heap 世代区分真实创建、查询、delete、destroy 与 thread_done。safe delete 迁移存活块到实际 backing heap，不更换块代次，不使 alias 或 typed object 失效；thread_done 只把实际线程已观察 context 的存活块置为 abandoned。只有已观察创建的准确 heap 才能按成功 destroy 退役子块，未知堆不伪记释放。失败 realloc 保旧块，成功以后才用实际 usable/contains 观察返回范围与所在 heap；不拿未知输入指针反查分配器私有布局。mimalloc v3 与其它未识别接口继续 opaque。

CRT 的8个 `_aligned_*` ABI 使用原实现的 `_aligned_free`，保留 alignment、offset、recalloc 的独立参数和原异常/错误状态。大小查询为精确只读租约，不退役或污染其它 provider 的行。失败重分配保留旧代，无法证明的零长/无效参数结果继续未决，不伪造原释放。

C++ 增加 GNU Win64 的20个实际公开 ABI，并补 MSVC aligned/nothrow/sized-aligned 变体。scalar/array 与每个有效 alignment 保留独立配对 context，同址导出按准确名称选择参数，原操作符、构造/析构表达式与 array cookie 不被替换。实际 new 内部 CRT 存储可在同线程、准确 provider 原调用 scope 内提升为同代 CPP 行；原 delete 内部的实际 CRT free 只借用外层持有的准确分配代次。外部 free/realloc 不能借此处置 CPP 行，活 alias、typed object、读写和来源门禁继续生效。CPP scope 纳入线程终结守卫，正常和异常路径都归还。未接适配器的字段语义仍 opaque，不把 vptr/STL 或任意对象表示标为 BYTE_STATE，也不自动调用未知析构器。

整包 `build-c-java102-native92-other-allocators-cpp-20261017.log` **首轮 PASS，1分23秒**，包含 Native92、Storage、Agent/Bootstrap/Core 与重混淆主包；Java 检查器、两个既有 Native 夹具编译退出0。本地 GNU C++23 对象夹具使用现有 Git shared libstdc++，`-nostdlib++` 保留真实 DLL 导入；最初缺少 bundled header 的本地夹具编译失败，补头路径/配置后成功，没有用静态 libc++ 代替测试目标。

`c-java102-native92-other-state-20261017.log` **退出0**：复用既有隔离夹具，只跑新 mode；真实分配和 JNI alias 阻 destroy、safe delete 迁移保代次/视图、复用 heap 与旧块隔离、thread_done 保活、typed raw-free 拒绝、失败 realloc 保旧，以及同代 CPP 提升和原内部 free 的精确租约通过。context 完成结果由 seam 供给，此项不是实际 provider 发现或 Core 权限证明。

`c-java102-native92-host-other-cpp-20261017.log` **退出0，正常 JVM 结束**：本包 premain/Bootstrap/Native92 为 INSTALLED_FULL。实际 ucrtbase 的8个 aligned named GPA、失败扩容保旧字节、offset/alignment、recalloc 和配对 free；固定上游 mimalloc v2.1.7 的24个 named GPA、真实 global/heap/对齐分配、delete 保活迁移、destroy/collect/thread_done；本机真实 GNU shared runtime 的20个 named ABI、12组配对调用及自有 C++23 类构造、数组 cookie、虚调用和原析构均通过。没有制造 Core/producer 许可；普通对象原调用通过不冒称任意对象字段已覆盖。

收尾另修 C++ 错误 TLS 来源错配：公开 ABI 名称只作分类，实际消费的 FirstThunk 地址及其实现导出决定 CRT；已有 Host thunk 只有当前 live consumer、准确 IAT slot、kind、entry 和 original 一致才可还原。名义 DLL 名称不授予错误状态来源，缺失或歧义保留 gap。最终增量 `build-c-java102-native92-cpp-actual-iat-final-20261017.log` **PASS，41秒**；仅复查受影响 GNU 路径，`c-java102-native92-cpp-actual-iat-final-20261017.log` **退出0**，20个真实 named ABI 与 C++23 对象原构造/数组/虚析构继续通过，JVM 正常退出。CRT、mimalloc 和独立状态 seam 未重复运行。

匹配候选为 `distribution/candidate-c-java102-native92-other-allocators-cpp-wip-20261017`，源码、两份 JAR 和本摘要同步 GitHub。尚未识别/静态/内联/类专属分配器、未适配对象字段与外部私有状态仍有缺口；本机未提供的 MSVC aligned 导出未作运行通过声明。真实 Core producer 集成、游戏进服和同存档重启 **NOTRUN**，完整 C/B 不标全部完成。未重跑旧矩阵。

---

## 2026-10-17：C 接续——arena 子分配、静态 TLS 与明确对象状态

接续 `ee1d416`，主工程 `D:\桌面\ronova_GPT`，Java ABI101 / Native ABI91 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包完成真实 jemalloc 子分配与 bulk 生命周期、Windows x64 编译器静态 TLS，以及显式 C/C++ 对象状态适配；没有恢复容器广谱 Hook，没有增加自身外观、武器或源码 ZIP。

LWJGL jemalloc 的16个实际 `je_*` 导出按准确名称、实现 image 和 ABI 接 IAT/GPA。成功分配以后读取实际 sallocx extent 和 arenas.lookup，不拿未知输入指针反查来源。provider 在途 lease、真实 arena index 与独立 epoch 约束分配、同址重分配和 reset/destroy；失败保旧代，成功才退休，活 DirectBuffer、写入和 typed object 阻破坏。mallctlbymib 使用六参数，MIB 由实际 nametomib 解析。只发现实际观察模块和已经解析的相关导入，不扫描全进程模块，不猜静态 CRT errno/TLS。真实子块完整包含于已观察 backing 时，子块保留准确代次，backing 丧失独立自动回收权；部分重叠与其它 arena 子块冲突继续未决。

原 provider 的准确调用 scope 连接实际内部 Windows backing 释放，复用已持有的同代 lease，其余受影响块仍检查活读写、视图、typed object 和来源；失败不冒充释放。停组 private cleanup 的实际 je_free 也携带已选中且已封闭的准确块，未制造 HostImport。真实 OS loader callout 的同 provider backing 收尾独立取在途 lease，并对全部影响行取门禁；没有真实 loader-query 回执时不授予此 scope。Host 返回式入口与共享 Native 环境查询在 loader callout 内沿既有无 JNI、try-lock 来源分派，避免 DLL_THREAD_DETACH 持加载器锁进入 JVM。

静态 TLS 从真实 PE64 TLS directory、DWORD index 和当前 x64 TEB vector 定位当前实例；初始化模板不是线程实例，不使用动态 TlsGetValue 代替。每线程/每 image incarnation 是独立 borrowed tag，全部已观察实例加入 image prepare/cancel，活 alias 阻卸载；准确 OS monitor signal 只退休该线程实例，不释放 TLS 字节。正常 Binding、Host、worker 和 RunAgentThread 入口接观察；受限回调、不支持布局和不可读/变化链保留 gap。直接 CPU 访问仍 opaque。

新增 `native/include/ronova_object_state.h/.hpp` 显式适配器：实际构造后绑定已有 allocation row/generation、pinned provider 和不可变原来源；普通字节字段、借用对象、写入、借用及实际析构分开记账。所有 caller 前门 noinline；无实际线程上下文时未覆盖，受限分支不附加 JVM。只有完整的同 provider/来源能声明 byte span；未知状态不被清除。真实 before/after 进入既有 revision，仅全部创建者/读者已停且属于本次 selection 的明确字节范围可恢复。活借用/写入阻析构，typed lifetime 阻原始 free/realloc/自动释放；真实析构完成后才解除，失败继续保留。Storage identity 的真实 C++ placement construction、两个普通 decision 字段和原析构已接入；string/wstring 本体为 borrowed，不复制其表示，不猜外部 buffer 所有权。SDK 缺失或回执为0不算覆盖。

构建：整包 `build-c-java101-native91-arena-static-object-isolated-20261017.log` **PASS，50秒**。实际故障收口后最终 `build-c-java101-native91-loader-environment-final-20261017.log` **PASS，39秒**，匹配 Native/JAR 同步更新。Java 检查器、两种既有 Native 夹具编译退出0。最初复用输出目录出现并行文件写入冲突，改用本轮独立输出；原失败日志保留，没有删除工作源码或借用其它 checkout 的结果。

`c-java101-native91-state-seams-20261017.log` 的 TLS/arena 部分 **通过**：真实 compiler TLS 模板/当前实例分离、两个实际 OS 线程、peer live alias 阻整 image prepare、seal/cancel、准确 OS signal 只退休 peer；arena tracker 的 supplied-completion seam 覆盖活 alias 拒 reset、失败保 epoch、成功换代及同址旧 token 拒绝。此命令后续对象夹具首次因 control-image pin 准备不足失败，整条命令不标 PASS。修 fixture pin 与 caller 前门后，仅单独续跑对象：`c-java101-native91-object-final-20261017.log` **退出0**，非零 SDK receipt、真实 CRT 分配、准确 row/generation、原错误状态、真实字节快照与所选 plain restore、borrowed 排除、活借用阻析构、原始 free/realloc 拒绝、真实 C destroy 再配对原 free 均通过。这里的来源/image 登记为隔离 metadata seam，不冒称真实游戏权限或外部 C++ ABI 析构。

真实运行 `c-java101-native91-host-arena-loader-backing-final-20261017.log` **退出0，正常结束且无 jemalloc 页释放错误**：本包 premain/Bootstrap/Native91 为 INSTALLED_FULL，实际本地 LWJGL 3.3.1 jemalloc 16个 named GPA、malloc/calloc、失败 realloc 保旧字节、rallocx/xallocx、sallocx/usable、实际 arena lookup、reset、六参 byMib destroy 和配对释放通过；实际 Storage C++ identity 原结果也通过。没有伪造 Core/producer 许可，identity 成功不冒称 SDK 已观察此普通来源对象。

保留失败过程：首次真实 jemalloc 的内部 backing 释放被外层持有/retiring 误拒，测试阻塞后仅结束该测试进程；补精确嵌套 lease 后各调用返回，但退出又卡住。Attach/SA 未完整展开，最终读取实际 Native 栈并结合 PDB/准确 HostImport，定位 jemalloc IAT 的 TlsGetValue 在 DLL_THREAD_DETACH 进入 code_current_sources_query/JVM，修 loader callout 分派后能退出；中间一次退出0仍有三次 VirtualFree 错误，不标完整通过。补真实 loader backing scope 后，上述最终同命令正常退出且错误消失。旧失败和栈日志均保留；已通过且未受影响的隔离路径不重复跑，没有跑旧矩阵。

匹配候选 `distribution/candidate-c-java101-native91-arena-static-object-wip-20261017` 随源码、SDK 和既有摘要同步 GitHub。任意未接入的自定义 allocator、非合作 C++ 对象/异常/vtable 语义、STL 外部私有 buffer、未知或受限静态 TLS 及裸 CPU/原始调用仍有缺口；真实 Core producer 集成、游戏进服和存档重启 **NOTRUN**，完整 C/B 不标全部完成。

---

## 2026-10-17：C 接续——独立堆、动态 TLS/FLS 与 C++ 分配状态

接续 `23c286b`，主工程仍为 `D:\桌面\ronova_GPT`。本包 Java ABI100 / Native ABI90 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。三个实现分工完成后统一构建；没有恢复 JDK 容器广谱 Hook，没有添加自身外观、武器或源码 ZIP。

实际 HeapCreate 保存创建者与独立不可变世代；HeapAlloc/ReAlloc/Free/Destroy 的在途调用绑定该世代，块发布完成后才解除上下文占用。成功销毁后的晚返回、实际 HANDLE/地址复用及未知首次观察不把旧来源授予新堆。实际释放前合并准确创建者与读者；Heap/CRT/C++ 的卸载清理例外必须持有当前块，来源完整且全部相关创建者/读者已经停止，活跃共享来源不能被连带释放。HeapDestroy 没有此例外，也不自动销毁猜测独占的池。

动态 TlsAlloc/Set/Get/Free、FlsAlloc/Set/Get/Free 及实际可用的 TlsGetValue2 接受控模块 IAT/GPA。真实 OS 线程 witness、私有 FLS sentinel 与槽世代区分生命周期；Ronova 内部索引不能被外部读写或释放。成功 NULL 与错误分开，Get2 保留原 LastError。读贡献跨清槽、释放索引和 JVM detach 保留，后续 worker/load 借用完整真实来源 lease。实际 FLS 回调按创建者和全部已观察 writer 保留来源，不按 data 指针猜对象或 fiber；正常回调调用一次，停源抑制并保留缺口，SEH 原异常继续。同一实际回调的槽 Get/Set 可接原 API，递归 Free 不借权。来源完整与 CPU 字节不透明分开，FLS 原生数据贡献仍有未知标记，不以此污染准确 allocator 归属。

已加载 MSVC 运行库实际导出的 scalar/array new/delete 及明确 nothrow/sized ABI 接精确实现 provider、scalar/array 类别与代次。共址导出按实际名称和地址双重匹配，未知裸地址不猜参数数目；原函数、零长行为、errno/doserrno/LastError、原异常和 new_handler 保留。普通 throwing-new 的策略拒绝使用 Ronova 非继续 SEH `0xE0520001`，不返回 NULL 冒充成功，也不伪装 std::bad_alloc；nothrow 拒绝为 NULL。只记录实际存储函数完成，不调用猜测析构器，不以 malloc/free 替代，也不自动 delete。

必要结果：`build-c-java100-native90-pool-tls-cpp-20261017.log` 首轮 **PASS，51秒**；Java 与两个既有 Native 夹具编译退出0。`c-java100-native90-pool-state-20261017.log` 首轮 **退出0**：真实 HeapCreate/Alloc/ReAlloc/Free/Destroy、创建者加实际 java.base 借用者、在途 Free/Realloc 阻 Destroy、旧 Destroy 晚完成不退役新 heap、活读/视图阻释放及共享来源清理限制。OS 是否复用数值 HANDLE 由实际返回决定，夹具不伪造；注册/控制 gate 和模块 scope 为隔离 metadata seam，不是 Core 授权。

`c-java100-native90-host-pool-tls-cpp-20261017.log` 首轮 **退出0**，真实 premain/Bootstrap/Native90 达到 `INSTALLED_FULL`。实际 Heap IAT/GPA、原异常及失败保旧；ucrtbase/msvcrt 两套 CRT；TLS 不同内核线程、真实 fiber 切换/DeleteFiber、每个非空 FLS 值的原回调一次、FlsFree、NULL 成功与 Get2 实际 GPA 错误状态均通过。msvcrt scalar/array 两组真实 named GPA 完成 small/zero 分配及配对 delete，skip=0；保留实际 provider 错误存储。这是普通来源的真实 API 检查，没有制造 producer/Core 权限，没有 C++ 对象析构完成声明。

收尾补齐“没有 JVMTI invocation record 的真实 FLS restricted scope”原生数据未知标记后，只增量编译该 DLL/JAR，`build-c-java100-native90-pool-tls-cpp-callback-20261017.log` **PASS，24秒**；只重编并复查受影响的既有内存 mode，`c-java100-native90-pool-callback-state-20261017.log` **退出0**。未重复已通过且未受该 producer 数据分支影响的普通 Host 检查，没有跑旧矩阵。

匹配候选 `distribution/candidate-c-java100-native90-pool-tls-cpp-wip-20261017`：Core **3109666**、prelaunch **23226** 字节。自定义 arena 内部子分配、静态/内联或类专属/debug/aligned C++ 分配、未知对象布局、编译器静态 TLS、直接 TEB/CPU/原始调用仍未解析；FLS 缺少公开 callback 完成屏障，无法闭合的 provider/source 继续保留并报告 gap，释放槽索引不宣称对象已析构。真实 Core/producer 集成、游戏进服与存档重启 **NOTRUN**，不宣称整个 C/B 已全部完成。

---

## 2026-10-17：C 接续——文件首次 Native 绑定与客户端异常分派

接续 `915440d`，Java ABI99 / Native ABI89 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包完整处理文件入口的前置布防/首次绑定，以及客户端分派的晚加载、Core 绑定和失败收尾；源码写齐后统一构建，不恢复容器 Hook，不做自身外观、武器注册或 ZIP。

先纠正此前剩余项的表述：日志里的77个入口不是77个没写的函数。运行中部分文件 native Method 当前指向尚未确认本体的 VM 地址，不能猜它们的符号、注册该地址或执行文件 API 来逼迫解析。现在为13个真实 bootstrap 文件/通道类型的每个所选 native 声明保留准确 Class、Method/ConstMethod/ConstantPool 版本、name/signature、operation 与参数位置。NativeMethodBind 在首次真实调用之前，用事件给出的新 target 完成文件分类和受控入口发布；目录/元数据/分配失败拒绝发布裸原入口，失效 binding 不计 BOUND。已有可观察原本体继续用原 seed 机制，未知 VM 体只记 ARMED_VM_ENTRY，不当作已经取得原函数。

新 `fileBindingState0` 返回 BOUND、ARMED_VM_ENTRY、PENDING、TOTAL。ARMED 需要准确声明目录和当前真实 NativeMethodBind callback/event/backend 可用，未观察原 body 的事实保留；旧 `fileBindings0` 继续返回未发布总数，不把77直接改成0。首次绑定事件读取的 Method 元数据可能仍是旧地址，原调用本体始终取事件 target，不拿旧 stub 替换它，也不新增 I/O 探针。

EventBus 的注册/停组/异常/注销保护组合为同一实际正文，新桥带准确 EventBus Class。Agent 只在最终返回正文完整、实际 API 成功或准确 ClassPrepare 后确认，不在 transformer 返回时提前报已安装；按真实 loader 与弱 Class 保留回执，重变换/定义变化使旧就绪失效。晚加载的这一准确 Forge 类型通过现有 ClassPrepare 转发，未加载时报告 WAITING_FOR_EVENT_BUS。Core 绑定准确 runtime 后确认其实际可见 EventBus 并补必要重变换；ClientFaults 注册固定私有 static 回调所用的自身完整 Lookup，实际 module/loader/codeSource、bus 与共享 API 身份必须一致。就绪动态查询，避免首次 false 被永久缓存，另一 loader 的同名类不能借此 Core 回调。

客户端未选事件保留原 dispatcher、原返回和 Throwable。所选着色器注册的回调装饰纳入原 abort 范围；嵌套同一 shader list 的新资源归父 scope，其他 list 不连带清除。清理逐项继续，失败 suppressed 保存在原异常，清理失败重抛原 Throwable；绘制事件状态恢复也不替换原异常。publication/supplier 使用同一 recoverable cause/suppressed 规则，非可恢复错误不吞掉。没有增加 UI、皮肤或渲染外观功能。

`build-c-java99-native89-file-client-20261017.log` **首轮 PASS，29秒**，包括 Native89 编译链接及 Agent/Bootstrap/Core。检查器编译退出0。仅运行本包直接相关检查：`event-image-composition` **首轮退出0**，使用实际 Forge EventBus 字节码验证四个保护组合、新四参桥、原 invoke 回退、二次织入不重复，以及 ASM/JVM 校验；它不声称安装 Core 或真实游戏许可。

实际 premain 的文件部分通过：`c-java99-native89-file-client-prepared-20261017.log` 中启动为 **BOUND=11、ARMED_VM_ENTRY=77、PENDING=0、TOTAL=88**；实际 FileInputStream/FileOutputStream/RandomAccessFile 的读写、seek/length/setLength 和原 IOException 后，变为 **BOUND=19、ARMED_VM_ENTRY=69、PENDING=0**。结果、文件位置和长度核对原调用一次，临时文件按原关闭路径清除；首次绑定确实生成文件 operation 的当前 wrapper。其后同命令的客户端夹具构造失败，所以不能把该命令整体标 PASS；文件子检查通过后没有再重复文件读写。

客户端真实检查 `c-java99-native89-client-dispatch-final-20261017.log` **退出0**：真实 Native89/premain、实际晚初始化 Forge EventBus 的 ClassPrepare、准确 Class 就绪、实际监听一次、原异常与原 Forge exception handler 身份，以及无 Core handler 时的原调用回退。前面的失败日志保留：最初在 Class.forName(false) 只加载未准备时过早查询 ClassPrepare；随后夹具事件缺 Forge 要求的 public 无参构造；最后重复发布同一 Event 触发原 phase 限制，并误把 EventPriority phase marker 计作业务监听。只修夹具协议并单独续跑客户端，未改生产异常来凑通过。没有重跑旧矩阵。

匹配候选 `distribution/candidate-c-java99-native89-file-client-wip-20261017`：Core3093793、prelaunch23226字节，随源码与既有摘要同步 GitHub。启动日志有 INSTALLED_FULL，但77个 ARMED 不等于77个原函数已被调用或解析；本包完成前置布防与首次绑定交接。真实 Core 客户端/GL 异常隔离、进服、存档重启 **NOTRUN**。任意裸 CPU/原始 syscall、自定义 arena/TLS/C++ 状态及未知共享 loader 引用仍沿此前边界保留，完整 C/B 尚未宣称全部完成。

---
## 2026-10-16：C 接续——实际 Native 私有分配与 DLL 静态存储

接续 `89ad335`，Java ABI98 / Native ABI88 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包先补可观察 Native 私有状态，生产源码整包写齐后统一构建；不恢复 JDK 容器广谱 Hook，不增加自身外观、武器或源码 ZIP。

已观察模块实际消费的 Windows HeapAlloc/HeapReAlloc/HeapFree/HeapDestroy、VirtualAlloc/VirtualAllocEx 以及已加载 ucrtbase/msvcrt 的 malloc/calloc/realloc/free 接入同一内存目录。记录实际返回、精确 provider、heap context、释放函数和代次，不从裸数值地址或请求大小猜来源。重分配失败保留旧块，成功即使同址也生成新代；HeapDestroy 以准确 heap epoch 收尾，正在分配、活读写租约或借用视图阻止误销毁。原 API 调用一次，SEH finally 清准确持有，保留原异常、errno/doserrno 和 LastError。无法观察错误状态的普通 CRT 原调用保持原语义，相关旧记录封闭为未决；缺口不变成虚构的释放成功。卸载期仅准确原 scope 中已知 provider/context/代次的释放可继续，新分配和未知指针不能借清理权。

已观察的真实 DLL image 捕获可写、非执行且非 discardable 的静态数据段，作为借用的 opaque IMAGE 存储接来源与 DirectBuffer。普通已知分配的私有释放继续依赖真实释放函数；CPU 暴露后的 byte undo 不补造完整性。IMAGE 活视图、读写租约及 active native scope 阻止卸载准备；准备先封新 admission，原释放返回后才查询准确旧 MEM_IMAGE 映射。只归还 Ronova 自己的额外 pin、应用仍有引用或同址映射无法分辨时，旧 tag 保持 sealed/unresolved，不能冒称物理卸载。确认原映射不在才退役旧代，静态段从不单独 free。实际 binding、JVMTI callback、Host worker、库与加载 scope 的数据贡献保留已知模块并附一个 CPU 私有状态 unknown；不污染全局来源，不改 allocator creator 的准确所有权。

构建 Native88 编译链接通过；安装路径修复后的最终必要增量构建 `build-c-java98-native88-source-holder-handoff-20261016.log` **PASS，21秒**，Native/Bootstrap 保持同一最新构建。Java 检查器与两种 Native 夹具均编译退出0。Zig 初次在沙箱内不能解析自身路径，使用既有编译器权限后成功；检查器初次缺 ASM exports，补准确 javac 参数后成功，没有改业务源码绕过编译。

`private-native-state` 首轮 **退出0**；追加本包“仍映射 DLL 不得假报退役”的准确 guard 后，仅重编该夹具并复查同一 mode，仍 **退出0**。使用真实 HeapAlloc/HeapReAlloc/HeapFree、真实同址收缩、JNI DirectBuffer 与 GC，覆盖 active read/live alias 阻 Host/private free、known Module/null 读回、旧代拒绝、精确私有释放；读取实际 fixture DLL 的 PE 静态段、排除代码页，并覆盖 IMAGE seal/cancel/retire 与仍映射 guard。登记、许可 gate、Binding/callback/worker scope 和最终 IMAGE retire 属于隔离夹具 seam；没有安装其自身 IAT、全局 JNI 表或 Core 授权，不把 metadata 断言当成真实游戏许可。

实际 `host-thread-create` 的独立 `private-allocations` phase 最终 **退出0**，日志 `c-java98-native88-host-private-allocations-original-error-20261016.log`。它使用本包真实 premain、Bootstrap、Native88 与 Host IAT/GPA 路由，输出 `AGENT_PREMAIN_READY:INSTALLED_FULL`；实际覆盖 Heap 失败保旧与同址收缩、原 HEAP_GENERATE_EXCEPTIONS 异常穿透及后续分配、live block HeapDestroy，VirtualAlloc decommit/recommit、仅 PROCESS_VM_OPERATION 权限的本进程 alias，以及 ucrtbase/msvcrt 两套实际 CRT 的 IAT/GPA、zero/overflow、失败 realloc 保旧、realloc(p,0)、free(NULL) 与原错误状态。这是普通来源的真实 API 路由；producer-only lifecycle 权限另由上面的隔离 tracker 检查，不能互相代称。

失败记录保留在原日志：首次 JDK17 的 Unicode bootstrap JAR append 失败，检查改用同一 JAR 的 ASCII 路径副本；之后实际安装暴露 Client/Network/CodeSource 与 SourceMap 的首次初始化 Unsafe 回入，已将这四个直接依赖桥移到 Native 全局入口发布前，未增加 UI 功能。中间 `...-ascii-...` / `...-final-...` 失败均不标通过。最终入口曾因夹具要求 HeapReAlloc 失败时 LastError 保持 sentinel 而退出；按其实际 API 合同移除未定义错误值断言，只保留 NULL 和旧块内容检查，生产结果未改。没有重跑旧矩阵。

匹配候选 `distribution/candidate-c-java98-native88-private-state-wip-20261016`：Core3083370、prelaunch23226字节。源码、候选及既有摘要同步 GitHub。小 JVM 的 premain 报告 FULL，但同日志仍明确报告77个文件 Native 入口 pending、CLIENT_FAULT_DISPATCH=false；不因此宣称所有 C 防护或游戏环境全部就绪。进服、存档重启、性能 **NOTRUN**。任意 CPU 裸读写、未消费进观察路径的缓存函数/原始 syscall、自定义 arena/TLS/C++ 对象语义、未知应用 loader 引用及已删除容器 Hook 的缺口仍保留，完整 C/B 不标完成。

---
## 2026-10-16：C 接续——未知键自然语义与原生视图生命周期

接续 `1de5df3`，Java ABI97 / Native ABI87 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包先补未知键与已观察原生分配的实际缺口；不恢复旧 JDK 容器 Hook，不做自身外观、武器注册或源码 ZIP。

自定义槽位中的真实 Map.get 在原调用前，只为当前查询键（Guava 单项 Map 为实际存储键）安装准确 Java hashCode/equals/compareTo。KEY 与 getter/helper 共用一次正文织入，原调用及返回不替换；键正文和准确 helper 的字段、数组、内建 String/Integer/Enum 调用结果及方法版本单独记录，不成为实体槽位存储根。查询沿真实 HashMap 碰撞/树节点、MapN、单项 Map 和已支持的包装链定位，核实际平台调用链、原 get 的 CP/BCI 与物理方法版本。解 Map 前后重核键依赖和定义，变化、未知 native/动态调用、实际写入或 monitor 操作使旧语义失效；没有调用用户键方法来重放查找。

JNI NewDirectByteBuffer 及真实 Java DirectBuffer 视图只关联已经观察的 allocation 与 generation，空绑定也不在后来的同址分配中补造归属。视图使用持有实际分配短租约，原释放或重分配使旧代视图失效；活着的独立借用视图阻止自动释放及 managed/unmanaged 字节回滚。JNI GetDirectBufferAddress 与真实 Java address() 外泄进入同一 allocation 的不透明使用状态，视图 GC 不抹掉已外泄地址。已成功 JNI 创建的观察失败保留原结果并保留实际不透明义务，未知地址不生成来源或释放权。

统一构建首次在 Bootstrap 的不可达受检异常 catch 处失败（35秒），原 `build-c-java97-native87-key-opaque-20261016.log` 保留。修正后，`build-c-java97-native87-key-opaque-final-20261016.log` **PASS，38秒**。收口时继续修复原生外泄读来源遗漏：已绑定 allocation 的 opaque 贡献单独保存，嵌套读接续，输出保留真实 Module 并加入既有 null 未知标记，不把这块已定位的不透明性升级为全局 unmapped。必要 Native/载荷构建 `build-c-java97-native87-key-opaque-readback-20261016.log` **PASS，31秒**；Java/Agent/Bootstrap 源码保持最新。Java 检查器与两次对应 Native 夹具编译均退出0。

仅运行两个本包直接 mode，均首轮退出0。`unknown-map-key-semantics` 同时运行 KEY/Slot/Action 字节码检查，覆盖原返回及 Throwable、未装策略直通、写入/隐式 monitor 失效；真实 HashMap 同 hash 树碰撞命中/未命中、String.hashCode 和 Integer.compare 原委托、字段/数组及终槽读取中变化、一次真实 RedefineClasses，读回不重放 hashCode/equals/compareTo。注册和字段读取使用夹具 seam，物理方法由实际 JVMTI 读取；不代称真实 Core 授权或实体防护已安装。`opaque-native-state` 使用实际 VirtualAlloc/VirtualFree、同址复用和 JNI DirectBuffer，覆盖活 alias 阻止 private free、raw/managed undo、无 revision 的自动 retirement pending、租约和旧代拒绝、外泄读来源同时保留真实 Module/null 且全局 unknown 不受污染、原成功结果/Throwable/LastError 及 stopped 拒绝。分配/来源登记与观察回调属于夹具，没有安装生产 Unsafe、Agent 或全局 JNI 表。两项通过后未重复旧矩阵或未知键检查。

匹配候选 `distribution/candidate-c-java97-native87-key-opaque-wip-20261016`：Core3069972、prelaunch23226字节，随本包源码和既有摘要同步 GitHub。真实整体 premain/Core/Native 安装、进服、存档重启及性能 **NOTRUN**。首次动态补装 helper 使旧物理帧失效时等待后续自然读取，不重放 getter。键内未知 Native/动态调用、未观察的任意 Native 私有结构、已删除容器 Hook 的对应缺口仍不提升覆盖，完整 C/B 尚未完成。

---

## 2026-10-16：C 接续——未知载体、真实委托与槽位定义版本

接续 `e7dfaac`，Java ABI96 / Native ABI86 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包补实际可定位的未知 Java 载体路径，先写齐生产源码后统一构建；不恢复 JDK 容器广谱 Hook，不做自身外观、武器或源码 ZIP。

未知 wrapper、能力值和自定义子类继续沿实际实例字段取图。布局 children、字段和数组读取使用同一份原始值，完成时复核根 ItemStack 的能力/tag字段、节点字段及实际用过的数组槽；字段替换、脱链和构图中混入旧 child 明确拒绝。新增 ArrayDeque 环形逻辑槽、ConcurrentHashMap 普通/树节点的准确 key/value 路径，不把任意闲置容量当 payload。成功 store 的原观察器仍可在容器半写时重建图：Deque 保留真实发布/移除/grow 前态，CHM 沿当前 table、nextTable 与 ForwardingNode 目标取并集，Reservation 保留结构但不伪造值，树旋转期间保留实际 root/first 可达节点；不等待原方法自己释放的锁，不重放 getter/iterator/supplier。

已声明载体及物品动作中的原非 SAM 业务调用捕获实际 receiver、参数与原返回，原 invoke 只执行一次，异常清理保留原 Throwable 和原 catch。当前 Scope、来源、持有根、准确参数、实际 CP/BCI、物理深度和 Method/ConstMethod/CP 版本共同约束接续。元数据不可用的已选择调用保留为不合格调用，不能回退借用隐式 data lease；结果只作原观察，不把临时返回对象登记为持有根。自定义 SAM 只按实际接口参数签名判别 callback，普通同名业务重载不因 Object 可赋值关系被误伤；数据字段保留，callback 不自动借 data-method 权。实际 Forge provider 查询接同一单次原调用交接。

槽位 helper 支持 canonical getter 的 root/helper 角色复用、自然 super/递归交接与自定义容器覆写中的真实 super.get；无匹配原 SlotCall 的嵌套 getter 仍未决。读地址绑定保留实际 getter 当前定义，标准 get 的 Query 同时保留真实特殊/虚分派合同和当前 body 版本，读取前后失配即失效。Native 提供只读 frame/callSite/methodImage；最终代码有 Execution 观测时，解析器只消费已匹配整个实际 VM image 的 protected Plan 所对应的固定观测段，校验准确 bridge、token local、ordinal、原 local/opcode 与 selector，遇未知调用/分支不穿过。Slot 与 Action 同方法共用原槽位见证，避免同一原 invoke 被二次包裹。

`build-c-java96-native86-unknown-carriers-20261016.log` 首轮 **PASS，1分15秒**，含 Native86 编译链接；检查准备时定位并收窄 SAM 重载误判后，必要最终构建 `build-c-java96-native86-unknown-carriers-final-20261016.log` **PASS，47秒**，Native86/Agent/Bootstrap 保持最新，Core 已重新生成。Java 检查器及隔离 JNI 夹具编译均退出0。没有重跑旧矩阵或已通过的无关场景。

`equipment-carrier-delegate-bytecode` 首轮退出0：真实非 SAM 多方法接口原调用一次、引用/null/long/void 结果、long/double 参数、原 RuntimeException/Error/catch 身份、策略未装直通、最终 Slot→Action→Execution 单见证；同 mode 的实际 Forge provider/dispatcher/optional 字节码以原 frame、COMPUTE_MAXS 通过 JVM 校验。`carrier-call-sites` 首轮退出0：实际 VM 的 CP/BCI、递归外层及错 selector/opcode/不兼容 receiver 拒绝、callee 实际 tuple、一次真实 RedefineClasses 后的 image 变化、最终观测段解析与元数据不可用。它使用自己的早期 OnLoad、JVMTI 环境、读取者和 Plan 登记，不声称安装了实际 Core 授权；同 Class 的另一个 receiver 由 callee tuple 匹配区分，不把元数据查询冒称对象授权。

`unknown-carrier-graph` 初次因未初始化 Minecraft 注册表退出，补初始化后又在缺少 ModLauncher 事件构造器变换的 Forge NetworkEvent 处退出；两份原日志均保留。本夹具只使用数据图：确认实际 Bootstrap 已完成 vanilla 注册表/快照后，仅识别这一准确网络初始化缺失并显式输出 NETWORK_NOT_INITIALIZED，其他异常继续失败。`c-java96-native86-unknown-carrier-graph-data-20261016.log` 最终 **退出0**，覆盖 wrapper→CHM→可变字段及脱链，字段/数组/table 混图拒绝，Deque 环绕/闲置/半写，CHM 迁移/Reservation/树写实际并集，以及 SAM/重载区分、getter/supplier 零重放。这里只替换 FIELDS/READ 元数据 reader，生产 Graph/Layout 算法原样执行，不提供 Native/Core 写权，不代称 Forge 网络或游戏启动通过。

匹配候选 `distribution/candidate-c-java96-native86-unknown-carriers-wip-20261016`：Core3039833、prelaunch23226字节。源码、候选及既有摘要同步 GitHub。真实完整 Agent/Core/Native 授权攻防、进服、存档重启及性能 **NOTRUN**；未解析的间接/反射/原生状态、缺少持久根的临时载体、未知槽位/键语义及已删容器方法防护仍有边界，完整 C/B 不标全部完成。

---
## 2026-10-16：C 接续——任务增量调度、资源绑定与停组无锁交接

接续 `f86c94e`，Java ABI95 / Native ABI85 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。按问题总整理收口 P1-05/06/07/08/11/18 及相关热路径；先写齐本包源码，再统一构建。未恢复旧 JDK 容器 Hook，未做自身外观、武器注册或源码 ZIP。

任务 operation 索引在资格赋值、退役和关闭时增量维护，当前任务窗继续沿真实 parent/child 关系纳入迟到任务。恢复链改为当前/历史轮转队列，资格变化通知更新分类，当前工作每五 tick 选取一轮；历史义务独立推进。存储工作用 ready/deadline 和 worker 完成事件调度，队列拒绝次 tick 重排；新保存读回唤醒旧代待收尾工作，运行中的唤醒保留到 finally。查询、状态和最终提交仍读当前资格与真实资源，不以调度缓存授予清理权。

ResourceBinding 缓存准确 scheduler、queue、lock 和 scheduled owner/delegate 布局，复用前重读实际字段及当前 canonical group、qualification、backend image/epoch；变化撤销旧完成事实。最终释放 Core 的准确 Future 引用前，在真实队列锁下确认缺席并重读 isDone。scheduler 基类 MethodHandle、线程 run 定义、Timer 布局与弱身份委托链缓存元数据；归属、委托和终止仍动态读取。存储 remaining/superseded/successor 均拒绝运行中、持有 queryInput、关闭未确认或过期读回；独立读取流首次 close 抛错后保留失败，后续空 close 不生成释放收据。

停组按实际模块串行发布不可变 Returns 代次，DefinitionBridge/ControlBridge/Native 调用不持 ModGroupBoundary 类锁。Native 回调只标记 pending，不等待正在回调的发布者；发布者继续同代补交及最新策略，控制入口等待实际完成，重转换后复核策略代次。重复 uuid-fixed 保留 UUID。ProRuntime 查询停组状态也释放 moduleSubjects 监视器后才进入可回调路径，并重核所查归属与代次。

修正 Forge EventBus 提前返回漏装后续边界：注册/监听、注销保护、异常分派和已声明 dispatch 依序组合，EventFault 边界支持幂等。普通 producer 的内部转换异常保留原输入并记录实际来源 gap；已停止来源、准确选中目标与控制载体继续硬拒绝，只有实际控制载体失败影响全局控制状态。诊断摘要从 enforcement tick 拆成独立有界 listener；高频来源与客户端精确清理入口缓存反射元数据，保留真实 caller/scope 与写后读回。

主构建 `build-c-java95-native85-recovery-scheduling-20261016.log` **PASS，29秒**，21项任务中14项执行、7项保持最新；生产 Native85 未改，沿用同一构建输出。末尾检查器首次 javac 缺内部 ASM 导出参数，原 `compile-c-java95-native85-recovery-scheduling-20261016.log` 保留；仅补编译导出参数，`compile-c-java95-native85-checker-exports-20261016.log` **退出0**。隔离 JNI 夹具首次 Zig 在沙箱无法定位自身，原 `compile-c-java95-native85-native-fixture-20261016.log` 保留；使用同一现有编译器正常访问后，`compile-c-java95-native85-native-fixture-access-20261016.log` **退出0**。未重复主构建或旧矩阵。

三个直接检查均首轮退出0：`event-image-composition` 使用真实 Forge EventBus 字节调用生产组合入口，覆盖四边界、原 dispatcher fallback、二次幂等、ASM 与 JVM 定义校验；`scoped-transform-failure` 覆盖普通来源同一输入、scoped gap、已停止/选中/控制目标的 VM 硬拒绝和仅控制失败影响全局状态；`stop-policy-concurrency` 经真实 NativeControl JNI 入口检查不持类锁、双线程回调立即返回、同代 pending 与新策略接续、Java/native 返回一致和固定 UUID 复用。后两项的目标登记与 native body 使用隔离夹具，不代表真实 Agent/Native 控制器安装。

既有 StorageRaceFixture 增加单独 `ronova.pro.storageSchedulingCheck=true` 场景，源码编译 `compile-c-java95-native85-storage-fixture-20261016.log` **退出0**。它要求40个真实 SavedData 工作经历满队列拒绝后逐文件完成、保留无关字段，检查读回 worker 运行中不结清及失败 close 不冒认释放；本轮真实 Forge 场景 **NOTRUN**，不据夹具源码声明这些结果通过。真实整体安装、进服、存档重启、性能及 Windows 驱动加载亦未运行。

匹配候选 `distribution/candidate-c-java95-native85-recovery-scheduling-wip-20261016`：Core3007582、prelaunch23226字节。源码、候选和既有摘要同步 GitHub。已删容器路径的防护、未知自定义载体及间接/原生不透明路径继续按实际缺口报告，完整 C/B 不以本包构建及隔离检查标完成。

---
## 2026-10-16：C 接续——启动批次、物理帧 token、队列定位与当前读回

接续 `5e60323`，Java ABI94 / Native ABI85 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。这四项整包源码写齐后统一构建，没有恢复旧容器 Hook、生成源码 ZIP 或扩展自身外观/武器。

初装各目标组复用一次实际 loadedClasses 快照，按阶段批量转换已加载类，不强制加载可选任务、句柄和包装类。未来加载覆盖与已返回/被 VM 接受的实际 image 分开；精准 bootstrap ClassPrepare 消费对应回执，拒批清除回执。Native 恢复只重试尚未完成的实际目标，不重装 Java 转换链；Java-only 启动继续保留。

已建立 watcher 的热 hook 使用实际 JVMTI 方法、Method/ConstMethod/常量池版本、BCI 和物理深度，拒绝递归外层、跨线程及过期 token。首次进入和未建立 watcher 的普通方法仍用真实栈，构造器保持 Native 要求；已建立 watcher 失效不能回退授权。正常/异常退出由真实 FramePop 收尾，过期来源不作为正常返回接续。

ForkJoin 按池做稳定存量采样，再用真实槽位写入/取出/删除、队列登记与扩容回执维护准确任务位置；Core 只处理该任务所在队列。现有字段/Unsafe/JNI/数组写收据使对应队列图失效，未完成转移、缺失覆盖和旧物理 writer 未退役不报缺席。旧 writer 屏障只检查实际直接写入的方法，不等待长期 worker 分派循环退出；Native 不可用时索引缺席证据保持 pending。

安装能力使用不可变快照，只有实际 image/epoch 或所需边界改变才重算；提交仍要求当前 epoch。CF 反射布局缓存，弱链图复用时重读实际 head 与全部 next，链中插入、循环及弱引用失效不沿用旧缺席。最终清图重新核对当前字段、队列版本、实际执行/等待退出与必要 ACK；委托调用不冒认共享对象已清除。

统一构建 `build-c-java94-native85-root-retirement-20261016.log` 首轮 **PASS，1分17秒**，21项任务中15项执行、6项保持最新，含 Native85；检查器与 Native 夹具编译退出0。`startup-batches`、`retirement-readback`、`queue-locations` 首轮均退出0，分别覆盖拒批不认证、旧 epoch 拒绝、真实 CF 链中重插及队列迁移/扩容/字段重插；队列真实 JDK 变换输出通过数据流检查。启动 API 返回、队列安装及字段收据使用隔离夹具元数据，不代表实际 VM 安装。

物理夹具初次未按生产 prelaunch 的 Agent_OnLoad 提前申请 FramePop 能力，setup失败；补早期申请后，未登记自己的 JVMTI 环境又被实际 VM 方法版本入口拒绝。原 `c-java94-native85-physical-token-20261016.log` 与 `c-java94-native85-physical-token-onload-20261016.log` 均保留。只修正隔离夹具的早期能力及私有 JVMTI 环境交接，不改生产代码、不交换全局 JavaVM/JNI 表；`c-java94-native85-physical-token-owned-env-20261016.log` 最终 **退出0，PHYSICAL_TOKEN_PASS**，覆盖真实 JVMTI 当前/父帧、递归外层和另一线程拒绝、捕获版本失配拒绝，以及实际正常/异常 FramePop 和原异常身份。夹具也调用生产队列旧帧扫描核心检查真实有界栈及错误 Class 拒绝；没有实际制造旧 JDK writer，也没有执行真实类重定义，版本失配由夹具记录内注入。主构建及前三项不重复，未运行旧矩阵。

匹配候选 `distribution/candidate-c-java94-native85-root-retirement-wip-20261016`：Core2988093、prelaunch23226字节。源码、候选与本摘要同步 GitHub；真实 premain/Core/Native 整体安装、实际 VM 重变换攻防、进服与性能均未运行，不以本包隔离结果代称。完整 C/B、已删容器防护与未知载体等剩余缺口不标完成。

---

## 2026-10-16：C 接续——来源候选、延迟解析与失败续接

接续 `f996c18`，Java93 / Native84 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。整包源码完成后才统一构建；没有恢复容器 Hook、写自身外观、注册武器、部署驱动或生成源码 ZIP。

类加载未命中先保存轻量类头、常量池调用引用及原字节；只对来源/控制/注册目标建立执行图。实际 producer/contributor 登记后，前期调用者可精确重变换提升，跨接口/父类合同保留候选而不授来源权。来源登记使用实际 loader/module/name，producer 负结果不再终身缓存；主要 emitted image 读取/回放/Native 处理用不可变目录，保留现有 tree 事务锁。内部转换错误的非法字节硬拒绝限于确认 producer、control carrier 或精确目标。

任务入口保留直接事实、浅层回调引用和 JDK/Inline 会清空的真实操作数，来源/包装图移到有界 drain。构造/发布门消费同一快照，仍在原效果前同步判断；未解析图及共享根不提前结清。policy 使用独立32项 tick/关闭预算；已知丢失保持范围，未知丢失拒绝保留。定义刷新失败保留未完成 continuation 与旧/新 owner 并集，自动退避重试；成功只移除对应项，单 helper 或整批异常不丢来源义务。

Native 自身未改写 class 事件在 JNI 前返回，已改写事件先筛候选再复制字节，未知/缺名不跳过。安装有 FULL/DEGRADED/FAILED 和明确可重试阶段；只恢复尚未发布的准备或独立目录，不重装表/Hook。Java 可用链继续启动，依赖 Native 的路径按实际状态不可用。执行栈只保存两帧和准确深度；物理调用点/递归/BCI 与退役核对未删除，未声称已完成 Native token 替代。

`build-c-java93-native84-root-lazy-20261016.log` **首轮 PASS，1分19秒**，21项任务中15项执行、6项保持最新，含 Native84 编译链接；检查器编译退出0。仅执行四项相关入口：

- `c-java93-native84-task-lazy-capture-20261016.log` **退出0，TASK_LAZY_CAPTURE_PASS**：实际 receive/resolver，回调字段清空后仍保留来源，FutureTask/CF/Inline 清空后的操作数及顺序、构造前来源、未解析共享根、丢失范围/ACK 与未知拒绝。
- `c-java93-native84-definition-retry-20261016.log` **退出0，DEFINITION_RETRY_PASS**：实际 worker 在代理 Instrumentation 下无新事件重试未安装的 flow，旧/新来源 owner 并集保留、只移除已完成项；没有实际 VM retransform。
- `c-java93-native84-native-install-lifecycle-20261016.log` **退出0，NATIVE_INSTALL_CLASSIFICATION_PASS**：实际准备期缺文件 IO 可重试，真实空 payload 不报 ready；没有加载 DLL 或安装控制器。
- 类筛选初次隔离运行因缺 Agent.codeSourceBridge 启动字段失败，原 `c-java93-native84-external-root-candidates-20261016.log` 保留；仅补齐夹具并重编检查器，`c-java93-native84-external-root-candidates-fixture-fixed-20261016.log` **退出0，EXTERNAL_ROOT_CANDIDATES_PASS**：真实编译类头/referenceOnly、非候选坏字节不进 ASM、后续来源提升候选、无关类直通及旧负缓存重新发现。

主构建及已通过入口没有重复，旧矩阵没有运行。上述隔离检查不代表真实 Agent/Core/Native 安装、实际 VM 重变换、游戏进服/攻防或性能通过；这些 **NOTRUN**。Native 部分安装的实际阶段恢复亦未运行。匹配候选 `distribution/candidate-c-java93-native84-root-lazy-wip-20261016`：Core2960410、prelaunch23226字节，随源码与本摘要同步 GitHub。

类头/常量池保留仍为输入长度相关成本，回调浅层引用读取仍同步；启动批次、物理栈的 Native token、全池队列和 certification/readback 快照等剩余收敛未标完成。容器防护与未知载体边界保留，完整 C/B 尚未全部完成。

---
## 2026-10-16：C 接续——停组策略发布、Native 返回语义与任务捕获

接续 `16ae71a`，Java92 / Native83 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包只修用户指定的三项，源码写齐后统一构建；没有恢复旧 JDK 容器 Hook，没有生成源码 ZIP。

停组完整验证目标/模式后先绑定策略，再依次发布 DefinitionBridge、Bootstrap 与 Native 停止门，最后进入根捕获和清理。启动/存档恢复和客户端停组同样传递所选模式；重复 prepare/stop 不更换固定 UUID。非法选择不发布，实际发布故障保留已生效围栏并跳过清理。

Native Attach/GetEnv 的观察失败保留原 API 状态、环境、pending Throwable 和 LastError，以计数/覆盖缺口报告，不伪造失败或回滚已成功的附件/环境。准确受保护输出和受限/已确认停止来源的拒绝保留；真正的交付失败仍清理未发布 JVMTI 环境。表交换成功才发布记录；失败清理未发布记录、来源租约与范围，避免留下失效环境缓存。诊断字段使用 JVMTI_FAILURES。

任务重量级字段/来源解析及克隆/构造通知移出 LISTENERS，不另持长期捕获锁；短锁预留在途并发布合并结果，处理并发绑定和 TAGS 锁顺序。同步取消/退役重入只交付既有收据。事件及失败行绑定实际 Registration/epoch、runtime/session 和 task；已知丢失在相关任务/主体范围保留，无法定位丢失保持全局阻断，DROPPED 不清零。共享发布在途、未归并失败与实际退出/ACK 等必要结清条件继续保留。

主构建 `build-c-java92-native83-policy-capture-20261016.log` **首轮 PASS，1分21秒**，21项任务中15项执行、6项保持最新，含 Native83 编译链接；检查器编译退出0。Native 夹具首次因旧 Host 辅助函数调用签名失败，原 `compile-c-java92-native83-policy-native-fixture-20261016.log` 保留；适配现有参数数组后只重编夹具。首次隔离运行未设置生产 Agent 的完整模块开放，访问 sun.nio.ch 时失败，原 `c-java92-native83-policy-capture-20261016.log` 保留。按实际开放补齐后，`c-java92-native83-policy-capture-access-fixed-20261016.log` **退出0**：

- `STOP_POLICY_PUBLICATION_PASS`：实际 prepare/publish，所选模式先发布、重复固定 UUID、非法模式/目标无新增停止；隔离元数据及空类快照代理，无真实 Agent 安装。
- `TASK_CAPTURE_SCOPE_PASS`：实际 receive 解析不持全局监视器，在途结清拒绝，发布后无关 owner 可用；已知失败范围、Bootstrap 失败行保留/确认和未知丢失全局拒绝。
- `NATIVE_VM_RESULTS_PASS`：真实 JVM GetEnv、两种 Attach、已附加与独立 worker、JVMTI 原环境；观察失败保留结果、pending Throwable、LastError，准确输出目标和已停止来源拒绝，失败包装不留记录/范围。故障注入仅在夹具内，不安装生产控制器。

没有重复主构建或已通过检查，没有运行旧矩阵。真实 premain、Core/Native 安装、游戏进服和攻防效果 **NOTRUN**，不以隔离结果代称完成。候选 `distribution/candidate-c-java92-native83-policy-capture-wip-20261016`：Core2928135、prelaunch23226字节，随源码和本摘要同步 GitHub。旧容器防护与未知载体等缺口保留，完整 C/B 尚未完成。

---
## 2026-10-16：C 接续——移除旧 JDK 容器广谱 Hook

接续 `35fb3c8`，Java ABI91 / Native ABI82 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。用户明确选择先彻底删除旧容器 Hook，接受对应防护暂缺；本批不做自定义 JVM，不提前补造替代覆盖。

移除 HashMap/Node/LinkedHashMap/Map、ArrayList 及其视图/迭代器、IdentityHashMap、AbstractCollection、ConcurrentHashMap、AtomicReference 和 CopyOnWriteArrayList 的旧 JDK 本体变换、重变换安装和镜像锁定。删除 HashMapBoundary、SourceMapBoundary、ConcurrentSourceBoundary，连同已无调用的容器适配器、构造 born/StackWalker 分类、普通对象操作帧/计数、节点绑定和排空门。普通容器构造/方法不再进入这套追踪；不把 retained root 控制宣称为容器写拦截的等价替代。

保留 premain、Bootstrap、JVMTI/Native、线程/执行器/任务/类定义/Native binding 等根入口，以及显式游戏、Fastutil、字段/Unsafe/JNI 与数组控制。任务交接所需队列/引用的既有准确边界继续保留；没有宣称整个 JDK 完全不变换。Native 源码和 ABI82 本批未改。SourceMapBridge2 只在实际 holder/carrier 注册处保存弱关系，字段观察与 Fastutil 修订号独立于已删的全局作用域。Core 的剩余控制就绪不再强依赖已移除的容器安装位；容器保护缺口仍逐实际义务报告。

JDK Map/List/Set/Atomic 的一致快照、来源回填冻结、节点/视图写入防护及依赖它们的清理不再标为已覆盖。相关接口返回不可用，不执行传入动作；去掉 CHM 原始快照/直接清理和 Atomic/COW 无观察读回等旧回退。当前及历史边的缺失作用域不会产生新的完成事实；组清理、嵌套来源和客户端监听器清理记录 `JDK_CONTAINER_HOOKS_REMOVED`。自身外观、武器、驱动部署和源码 ZIP 暂缓，完整 C/B 仍未完成。

统一构建 `build-c-java91-native82-no-container-hooks-20261016.log` **PASS，1分3秒**，21项任务中14项执行、7项保持最新。Native82 沿用已构建产物，本轮没有 Native 源码变更。检查器首次编译因 Runnable 与现有 Action 接口类型不匹配 **FAILED**（原 `compile-c-java91-native82-no-container-hooks-20261016.log` 保留）；改成原动作的方法引用后，仅重编检查器，`compile-c-java91-native82-no-container-hooks-fixed-20261016.log` **退出0**，未重复主构建。

现有 AuditFixCheck 的 `container-scope-removal` **退出0，CONTAINER_SCOPE_REMOVAL_PASS**，日志 `c-java91-native82-no-container-hooks-bytecode-20261016.log`。检查确认 BackingBoundary 对真实八种 JDK 容器类不再产生变换；HashMap/CHM 的不可用快照、遍历与动作不触发操作，ArrayList/COW 的无作用域移除拒绝并保留原元素；历史 Atomic/COW/List/Map 边不据普通读回新增完成事实。Core 与 Agent 均未安装，只确认上述实际接口/变换行为；真实 premain、Native 安装、游戏场景和性能 **NOTRUN**。没有运行旧矩阵。

匹配候选 `distribution/candidate-c-java91-native82-no-container-hooks-wip-20261016`：Core2917394、prelaunch23226字节。Core 内嵌 Agent/Bootstrap 的三种已删容器变换类及旧 Frame/AnalysisCaller/ConcurrentFrame 类确认不再残留，避免增量构建带回旧实现。源码、匹配 JAR 和既有摘要同步 GitHub，未生成源码 ZIP。旧记录保留；被删容器防护没有因本批构建通过而恢复，完整 C/B 尚未完成。

---
## 2026-10-16：C 接续——启动交接与统一生命保护

接续 `6fc3ea4`，Java90 / Native82 / Storage3 / Journal6，协议 `r3-client-8`。整包源码写齐后构建，未复制 Buer 样本代码，未写自身外观、注册武器、部署驱动或生成源码 ZIP。

启动按当前实际 Agent 状态交接：安装前认领、重复入口复用、失败/部分 hook 不盲重装、FULL 实时复算，Java-only 合同保留。非 FULL 只明确报告，由实际动作门决定可用性。预启动不再提前报 READY，所选服务端世界和保存的非默认返回策略不再被 default 覆盖；durable 读回失败明确停止交付。Native 初始化新增实际完成标志，JNI 表已发布但必要 Unsafe/arraycopy 失败后，重复初始化不能返回成功。

保护/禁疗统一资格绑定当前 body、subject generation、发布地址和世界记录，值写门保持防降与禁增各自语义。精确 setter 声明接不调 super 的覆写，吸收值读取真实存储；同步值、整生命载体、items 表、数据项、底层数组及身份字段的 Java/Unsafe/JNI 接原写门。生命项归属按当前代和真实 map 关系复核，标准内部搬移保留，合法玩家生命周期沿原 permit。客户端镜像接同一生命/吸收/火焰与存储关系，修正治疗后可降至旧 floor 的差异。

`build-c-java90-native82-startup-life-20261016.log` **首轮 PASS，1分23秒**，含 Native82 编译链接；检查器编译 **退出0**。`c-java90-native82-startup-life-bytecode-20261016.log` 的 `startup-protection-bytecode` **退出0**，输出 `STARTUP_RETURN_REPLAY_PASS` 和 `PROTECTION_SETTER_BYTECODE_PASS`：前者使用实际 JournalLineage 写入/读取所选服务端世界的 STOP/RETURN、非默认策略及 client 分离；后者临时设置并恢复隔离声明记录，调用生产 DispatchBoundary 匹配真实 Entity/LivingEntity 继承合同，检查拒绝前无原副作用、允许一次执行、参数及 RuntimeException/Error 身份。执行投影仅改名、去实体父类/构造器并重定向单一 Bridge 调用到普通测试 predicate，未安装 Core guards，不证明公开声明注册、真实 Agent 安装、Native 或游戏保护。

最后将生命数据表键限定 Integer；收尾重编遗漏 Zig 与构建目录参数，`build-c-java90-native82-startup-life-exact-key-20261016.log` **FAILED，57秒**，原记录保留。恢复首轮配置后，`build-c-java90-native82-startup-life-final-20261016.log` **PASS，51秒**，未重跑已通过检查或旧矩阵。真实 premain/attach、Native 安装及带策略游戏场景 **NOTRUN**。最终候选 `distribution/candidate-c-java90-native82-startup-life-wip-20261016`：Core2968376、prelaunch23226字节，与源码及本摘要同步 GitHub。未知间接调用与不透明载体边界仍保留，完整 C/B 未宣称完成。

---
## 2026-10-15：C 接续——自定义包装回调与双参数组合

接续 `a7e1a47`，Java89 / Native81 / Storage3 / Journal6，协议 `r3-client-8`。本包源码全部写齐后才构建，Native/驱动源码未改。

ExecutionBoundary 在八种准确原 SAM 接口调用处交接原 callback/参数；CodeSourceBridge 从当前 VM 取得 Plan，ExecutionFlow 比对当前 Frame、原指令、Call 和实际 operands，再用一次同步 handoff 接真实 callee 的参数与原来源 Cell。缺 hook/错身份保留 UNKNOWN 并禁止正常回填；finally 清理，不借其它外层任务或创建新的 Scope。Core 只接当前合法 parent consumer，逐当前 root/owner、来源与物理调用链复核。BiFunction/BiConsumer 标准 andThen 的准确工厂及私有组合体逐原回调接续，原顺序、实参、中间值与异常保留。

`build-c-java89-native81-defined-bi-20261015.log` **首轮 PASS，1分17秒**，21项任务中14项执行、7项保持最新，Java89 编译完成，Native81 为原未改产物。`compile-c-java89-native81-defined-bi-20261015.log` **退出0**；`c-java89-native81-defined-bi-bytecode-20261015.log` 的现有 `equipment-defined-bi-bytecode` **退出0**，输出 `EQUIPMENT_DEFINED_CALLBACK_BYTECODE_PASS` 和 `EQUIPMENT_BI_COMPOSITION_BYTECODE_PASS`。前者是实际 RunnableAdapter.call 的生产 weave/writer、隔离 alias 加载及 token=null fallback，核对一次原 callback、原 result 和 Throwable；后者仅本次两类 JDK 双参数组合体，核对四处 Bridge 调用、实际双参数/null/中间值、void、顺序和 RuntimeException/Error 身份。Core/Native handles 未安装，无人工 Plan/Scope，不代表真实 java.base 重变换、带授权来源 handoff 或游戏效果通过。

上述实际安装、Core/Native 授权与游戏场景 **NOTRUN**；未重跑旧矩阵，旧失败记录不变。候选 `distribution/candidate-c-java89-native81-defined-bi-wip-20261015`：Core2958026、prelaunch23309字节，随源码与本摘要同步 GitHub。未知具体类调用、反射/MH、未知键语义/执行器及不透明原生状态仍未泛化覆盖，完整 C/B 未宣称完成。VS Code Gradle 导入已在本机 settings 选择 Java17，未观察重新导入结果；该绝对机器路径不上传。

---
## 2026-10-16：C 接续——异步能力与复杂载体

接续 `02c61a0`，Java88 / Native81 / Storage3 / Journal6，协议 `r3-client-8`。三个既有6.1 Sol max子代理分别完成真实任务与JDK回调交接、Native特殊调用/四类接口、复杂载体图；主线程接齐Core弱上下文、worker局部Scope、派生链、原保护门和Bootstrap安装。整包源码写齐后统一构建，自身外观、武器注册、驱动部署及源码ZIP暂缓。

TaskBridge上下文只在原合法能力callback提交时捕获，不保留原Scope/Thread；准确Inline/Completion.fn及认证JDK包装真实字段传递，当前非dispatch任务帧在原callback点单次领取，不搜索其它active外层任务。FutureTask真实cancel(Z)Z的原0→4/5 CAS成功才清共享记录；空转tryFire、未知execute结果和普通submission结束不伪造完成。Core重新核owner/generation、原root/槽位/世界、有效且相同dispatcher和逐层optional/marker，worker仅获ItemStack/NBT/能力payload作用域；新的provider/listener生命周期在首store前拒绝，避免中途撤销形成半次失效。派生optional自然回调和再次提交保留当前新链，原异常对象与finally恢复不变。未知/停止来源及原实际taskEffect守卫保留，未安装Core/无上下文仍原接口直通。

Native添加准确Runnable/Callable/BiFunction/BiConsumer契约，并按真实symbolic解析及caller父链选择祖先super和直接interface.super/default；不执行getter/loader/clinit。Graph保留兼任callback的真实数据容器，回调入口不借业务权；SlotGraph使用exact不可变scalar身份，并只对VM确认继承标准get的实际子类按既有父布局查询。自定义覆写仍自然trace，未知包装/delegate/key、间接special、native/opaque等不提升覆盖。

`build-c-java88-native81-async-carriers-20261016.log` **首轮PASS，54秒**，21项任务中15项执行、6项保持最新，Java88/Native81实际编译链接完成。`compile-c-java88-native81-async-carriers-20261016.log` **退出0**；`c-java88-native81-async-carriers-bytecode-20261016.log` 复用现有AuditFixCheck的 `equipment-async-carrier-bytecode`，**退出0、EQUIPMENT_ASYNC_CARRIER_BYTECODE_PASS、EQUIPMENT_ASYNC_CARRIER_FALLBACK_PASS**。实际JDK 19类/30处原callback生产转换及TaskBridge hook连接通过；实际RunnableAdapter先按生产TaskResultBoundary织入，再仅alias/nest元数据隔离，保留原字段/ctor/call，用真实singleThreadExecutor验证一次Runnable、worker线程、原结果和同一ExecutionException.cause。Core handles全程null，没有造票/Scope/来源身份，不能代称实际java.base安装、Core异步授权或Native载体保护通过。

本批带Core的异步写入/失效/取消/共享owner、复杂子类/getter及实际游戏场景 **NOTRUN**，未重复旧Forge/Optional、槽位、Netty、世界与退役矩阵；旧失败记录原样保留。候选 `distribution/candidate-c-java88-native81-async-carriers-wip-20261016`：Core2949381、prelaunch23309字节，源码、匹配JAR及本摘要同步原GitHub，完整C/B尚未完成。

---
## 2026-10-16：C 接续——同步 getter helper 与函数组合

接续 `ca5edc8`，Java87 / Native80 / Storage3 / Journal6，协议 `r3-client-8`。三个既有6.1 Sol max子代理分别完成准确 helper 织入、VM只读实际声明分派及标准JDK组合体；主线程接齐Core读取/嵌套作用域、Bootstrap handles与版本。整包源码完成后才统一构建，没有自身外观、武器注册、驱动部署或源码ZIP。

helper沿原调用操作数和实际方法HEAD接当前实体/槽位，原invoke、返回和Throwable只经过一次；窄失败清理仍在原try区域，保留原catch顺序。引用参数按身份、primitive按准确包装类型/原位值比对，辅助读取只沿已有真实根接续。VM仅识别真实声明、虚分派、唯一default、private/static和直接父类普通super，不执行目标方法/加载器/初始化。未知/歧义/native/构造器/平台body/超限/角色冲突不记完整轨迹；重复root先标unresolved，修正父类已为root时的错误anchor异常。最终实际存储复核及原五类写门不变。

Function.compose/andThen、Consumer.andThen、Predicate.and/or/negate准确工厂及private synthetic body接原callback，保留中间值、短路及原异常。嵌套委托只沿当前同线程能力parent、原root/owner和Native物理调用边，正常/异常恢复前层；未知自定义函数组合、异步/间接链、复杂special、不可安装helper和原生私有状态仍未泛化覆盖。

`build-c-java87-native80-helper-composition-20261016.log` **首轮PASS，52秒**，21项任务中15项执行、6项保持最新，包含Java87/Native80编译链接。`compile-c-java87-native80-helper-composition-20261016.log` **退出0**；`c-java87-native80-helper-composition-bytecode-20261016.log` 使用现有AuditFixCheck的 `equipment-helper-composition-bytecode`，**退出0、EQUIPMENT_SLOT_BYTECODE_VERIFY_PASS、EQUIPMENT_SLOT_HELPER_BYTECODE_VERIFY_PASS、EQUIPMENT_FUNCTION_COMPOSITION_BYTECODE_PASS**。实际Mob getter与ItemStackHandler四个helper体按生产weave/writer隔离加载；实际JDK组合体仅为子加载器合法定义而改alias名称、captured-this和impl owner，原方法签名/回调契约保留，11个Bridge调用的顺序/中间值/短路及RuntimeException/Error身份通过。Bridge callbacks始终未安装，没有构造Core/Scope/来源资格，不能代称真实java.base重变换、Native helper识别或装备保护通过。

没有重跑旧已通过Forge/Optional/Netty/世界/退役矩阵，未启动游戏，本批Native及实际能力/槽位/换装/停组场景 **NOTRUN**；旧失败记录保持原状。候选 `distribution/candidate-c-java87-native80-helper-composition-wip-20261016` 含Core2930187、prelaunch23309字节；源码、匹配JAR和本摘要同步原GitHub，完整C/B尚未完成。

---
## 2026-10-16：C 接续——完整本批回调与复杂载体接点

接续 `ddd92bc`，Java86 / Native79 / Storage3 / Journal6，协议 `r3-client-8`。既有三个 6.1 Sol max 子代理分别完成 VM 深链/纯转发读取、Forge/JDK Optional 真实调用点及复杂容器查询；主线程接齐 Core 四类 callback、失效监听双层分派、当前动作弱派生关系与 Bootstrap 五 handles。整包源码写齐后统一构建，自身外观、武器注册及驱动部署不做。

Forge supply/consumer/map/lazyMap/filter/监听/回退，以及 JDK Optional 六个指定入口消费原真实 operands，各 callback 只执行一次；原 Throwable 对象保留，供应者原 monitor 和返回后才开启的 publication Gate 不变。原 Set.forEach 仍执行一次，按当前真实 HashSet/lifecycle/member 接外层原分派 Consumer 及内层 listener。派生关系只记录实际非空返回及原 parent/value/supplier，在同一当前 Scope 内按真实 root/全部 owner 复核；不会给全局 Optional.empty 记 owner，空结果 fallback 原样执行但没有装备写委托。未安装 Core callbacks 时 Bootstrap 原 JDK callback 直通。

Native 八个准确 Forge/JDK SAM 契约扩为最多16个物理帧的真实直接 CP/BCI/版本/来源链；二次 functional 只准准确纯转发 bridge/隐藏适配。新增 callbackSafe 原始布尔与只读 Plan 唯一 invocation 核对，既有 retirementSafe 计算和控制职责不变，无新 capability/JNI 导出。复杂 MapN/Guava/标准树桶读取实际 probe、桶链或树路径，精确 Enum/Integer/String hash/equals/comparison 使用当前字段/字节及缓存，不执行 key 自定义方法；EnumMap 实际值存储接通用图，keyUniverse 不登记 owned 数组。旧五门、拟写值预测和当前 backing 更新继续接续；未知 Map/key/异步函数/动态间接调用/native opaque 仍有边界。

`build-c-java86-native79-callbacks-20261016.log` **首轮 PASS，1分6秒**，21项任务中15项执行、6项保持最新；`compile-c-java86-native79-callbacks-20261016.log` **退出0**。`c-java86-native79-callbacks-slot-bytecode-20261016.log` 执行现有 AuditFixCheck 的 `capability-callback-bytecode`，**退出0、CAPABILITY_BYTECODE_VERIFY_PASS、CAPABILITY_CALLBACK_BYTECODE_PASS**。Forge 保留生产 frame/COMPUTE_MAXS；JDK Optional 使用真实变换后字节码，只为合法子加载器定义而单类 rename，保持原字段/方法/frame。其六处 callback 普通分支检查了原操作数/次数/返回和原 Throwable identity，Bridge handles 始终未安装，不造 Core/Scope/module/source 资格。日志明确是 renamed isolated JVM body check，不能代称实际 java.base 重变换或游戏结果。

本包 Native 实际深链、复杂键/树桶的写门、真实能力/换装/失效/停组游戏行为 **NOTRUN**。未重复旧已通过矩阵，未启动游戏，旧 Native75 预启动失败及上一批编译/隔离检查失败记录保持原状。候选 `distribution/candidate-c-java86-native79-callbacks-wip-20261016`：Core2913173、prelaunch23309字节；源码、匹配 JAR 和本摘要同步 GitHub，未生成源码 ZIP，完整 C/B 尚未完成。

---
## 2026-10-16：C 接续——消费者委托与自定义槽位

接续 `2fdef27`，Java85 / Native78 / Storage3 / Journal6，协议 `r3-client-8`。既有三个 6.1 Sol max 子代理分别完成 VM 回调通道、实际 getter 读取织入及槽位图/Core 写门；主线程完成回调作用域、Bootstrap/Agent 接线与当前正常 selector。源码整包写齐后才构建，自身外观、武器注册、驱动部署继续暂缓。

原 LazyOptional.ifPresent consumer/value 仅调用一次；委托绑定当前动作、root、有效 optional/payload 与全部共享 owner。VM 根据真实方法/BCI、版本、CP 调用和栈底位置，只接消费者本体及实际 bridge/已绑定隐藏 accept 的一层直接目标；原 unknown/stopped 检查保留，不新增 locals capability。正常/异常关闭并恢复原作用域，其他回调没有泛用许可。自定义 getter 在准确 Class 上单类安装，记录原字段/数组、受支持 Map/List 和真实 slot/type selector 的自然读取。原异常或不完整/未知分支保持 unresolved，未知槽不伪造 EMPTY；不重放 getter 或扫描 Entity/世界图。实际关系按当前实体/代次/root 复核，接 shares/main/hand、NBT/能力以及字段、元素、offset/memory 和容器入口。准确 setter 仅接原动作，标准 backing 更新沿现有成功 store observer 重取；自定义路径变更后旧链接退役，等待自然 getter 重绑。真实 static base、窄数组值及同值拟写处理接齐，树桶/未知 Map 查询或 key 语义、未知容器/计算 getter/native opaque 仍有边界。

`build-c-java85-native78-equipment-first-failed-20261016.log` **FAILED，38秒**：两处 Map 泛型参数不完整；改为 `Map<?,?>` 并补 `EquipmentSlot.Type.ordinal` 后，`build-c-java85-native78-equipment-20261016.log` **PASS，1分9秒**，21项任务中15项执行、6项保持最新，Java85/Native78 实际编译链接完成。`compile-c-java85-native78-equipment-20261016.log` **退出0**。

现有 AuditFixCheck 首次独立加载完整 Mob 因另一个未修改方法与父加载器 LivingEntity 分属不同 runtime package 而 **VerifyError**，`c-java85-native78-equipment-slot-bytecode-first-failed-20261016.log` 原样保留。检查只保留当前实际 Mob getter 本体，字段与父类仍取真实类，使用生产 SlotBoundary.weave/ControlClassWriter；仅重编检查并加载该本体及本批三个 Forge capability 类，`c-java85-native78-equipment-slot-bytecode-20261016.log` **退出0、EQUIPMENT_SLOT_BYTECODE_VERIFY_PASS、CAPABILITY_BYTECODE_VERIFY_PASS**，Forge 保留生产 frame、只计算 maxs。未重跑已通过的主构建或旧矩阵，未启动游戏。

以上只确认编译链接及隔离 getter/Forge 的字节码安装格式，Native 回调和实际能力/槽位/换装/共享 owner/停组效果均 **NOTRUN**，不能代称游戏安装和行为通过。旧 Native75 预启动失败继续保留。候选 `distribution/candidate-c-java85-native78-equipment-wip-20261016` 含 Core2890897、prelaunch23309字节；源码、匹配 JAR 与本摘要同步 GitHub，未生成源码 ZIP，完整 C/B 未宣称完成。

---
## 2026-10-15：C 接续——自定义能力与载体闭包

接续 `5ff82f3`，Java84 / Native77 / Storage3 / Journal6，协议 `r3-client-8`。三个既有 6.1 Sol max 子代理分别完成 VM 只读字段通道、标准容器布局及 Core 数据图；主线程接齐实际载体 Class 安装、来源门、getCapability 自然返回观察、成功写通知和数组逻辑范围。源码整包写齐后才构建，自身外观及武器注册不做。

当前 provider 实例字段、自然 resolved 值及真实被选择的 getCapability 返回值接当前 ItemStack；嵌套 ItemStack/ItemStackHandler/NBT/custom Tag、自定义 curative List 的实际 effect/root 关系复用原五类写门。真实 backing/node、可变 Map key/value、列表子视图及原子数据有准确范围；临时快照不登记，分步容器更新不被错误计数不变量阻断。原成功 store observer 接回新增 payload，原来源/世界/实体判断保留。Class 弱登记、default interface、递归 token 和 actual receiver/method/动作链只接本次合法业务；未知回调不借权。invalid optional 的 resolved 残留解除 payload 关系，Provider.valid=false 不曝光 query 弱观察，实际独立字段根仍保留。共享 owner 逐个核对，旧链接按当前图退役。

VM 字段通道只读真实声明、值、offset 和宽度，不执行库 accessor、自定义加载器、supplier、序列化或构造器。所需 Unsafe 元数据入口来自实际 VM 注册表，读取单例延后到确认初始化之后。未解析类型或必要元数据不可用明确失败；字段逐项读取，不宣称全对象原子快照。全局/owner/runtime 资源留作借用叶；未知 functional 回调、native opaque、不可安装类及未知自定义槽仍未泛化覆盖。

`build-c-java84-native77-carriers-20261015.log` **PASS，1分16秒**，21项任务中16项执行、5项保持最新，包含 Java84/Native77 实际编译链接；`compile-c-java84-native77-carriers-20261015.log` **退出0**。`c-java84-native77-custom-carrier-bytecode-20261015.log` 复用现有 AuditFixCheck，只加载真实 ItemStackHandler 的实例 body 包装和本批 Forge 三类织入，**退出0、CARRIER_BYTECODE_VERIFY_PASS、CAPABILITY_BYTECODE_VERIFY_PASS**，Forge 仅 COMPUTE_MAXS 保留原 frame。只确认 JVM 安装格式，不代表 Native 字段读取、游戏能力行为或停组结果通过；这些实际场景 **NOTRUN**。未重跑旧矩阵、未启动游戏；旧 Native75 预启动中止保留为原失败记录。

候选 `distribution/candidate-c-java84-native77-custom-carriers-wip-20261015`：Core2832142、prelaunch23309字节。源码、匹配 JAR 与本摘要同步原 GitHub，未生成源码 ZIP、未部署驱动；完整 C/B 未宣称完成。

---
## 2026-10-15：C 接续——文件复制与标准能力图

接续已推送的 `426c1b5`，Java83 / Native76 / Storage3 / Journal6，协议 `r3-client-8`。本包继续完成实际 duplicateForMapping/duplicateHandle/新 FD 发布及失败副本接续、标准 Forge capability 元数据图，并补 Guava SingletonImmutableList。自身外观、武器注册与驱动部署不在本包。

op10 绑定真实父 FD/handle、实际原方法的 Method/ConstMethod/CP/栈底层及当前来源版本，保留原 Native 所有门；controller 内部的本入口也取得原 NativeFileFrame。真实 RAX 先写本次自有 receipt，再回调 finish；同一新 target 的真实 handle 和 FileCleanable.referent/fd/handle 全部一致后才分配/登记。异常保留原 Throwable并关闭 nested/parent FileOperation。native 成功但后续发布失败时，进入 native 前的强 pending 与未分配 DescriptorRecord 保留准确事实；原 RecoverySources.remaining/retireGroup 枚举，只有原规则已选中的 target 沿原异步 retire/release 尝试关闭。未知/共享、handle 从未安装、target/cleanup 改变仍 pending，不按 long 制造资源资格或伪造已结清。

标准能力图包含当前 ItemStack 的 CapabilityProvider/已初始化 dispatcher、实际 provider/序列化数组与 listener backing，以及本机已确认 FluidBucketWrapper/FluidHandlerItemStack 系列持有的 LazyOptional、MutableObject 和真实集合。pending lazyData 复用 NBT，所有链接按当前 root 和各 owner 复核。首次 lazy 发布仅在原 callback 返回后的字段 store 段持原 Gate，正常/异常关闭；listener/lifecycle 继续需要现有实际装备动作许可。未知 provider 私有字段和 callback 私有状态、未知实现不计覆盖，供应回调不获得写权。

`build-c-java83-native76-carriers-20261015.log` **PASS，58秒**，21项任务中16项执行、5项保持最新，Java83/Native76 实际编译链接。`compile-c-java83-native76-carriers-20261015.log` **退出0**。`c-java83-native76-resource-bytecode-20261015.log` 使用现有 AuditFixCheck 对真实 FileDispatcherImpl 的当前转换及 CapabilityProvider/CapabilityDispatcher/LazyOptional 的当前 plugin 织入按 `-Xverify:all` 加载，**退出0、RESOURCE_BYTECODE_VERIFY_PASS、CAPABILITY_BYTECODE_VERIFY_PASS**；Forge 保留生产 frame、仅 COMPUTE_MAXS。只确认直接字节码安装可接受，不代表实际 native 复制/异常关闭或游戏能力行为通过。未重跑旧 Netty/世界表检查，未启动游戏；相关运行场景 **NOTRUN**。Native75 的旧预启动中止保留为历史记录，没有改成 PASS。

候选 `distribution/candidate-c-java83-native76-io-capability-wip-20261015` 含 Core2774289、prelaunch23309字节；源码、匹配 JAR 和摘要直接同步 GitHub，未生成源码 ZIP、未部署驱动，完整 C/B 未宣称完成。

---

## 2026-10-15：C 接续——标准删除与队列生命周期

接续 `563ddfb`，Java82 / Native75 / Storage3 / Journal6，协议 `r3-client-8`。本包写齐标准 reverse map 删除/shiftKeys 与真实 iterator 接续、JDK/Guava 常见解药列表和实际 provider 覆写、Netty 两种标准有界 MPSC 的原版 full/default REJECT 收尾，以及 Windows JDK `Net.socket0(ZZZZ)I` 的原生创建入口判断；不改自身外观，不注册武器。

世界表的冻结映射在 size/wrapped/键值副作用前拒绝；无关项沿原循环，准确键值对的两次 store 与真实 map/数组/索引/同一 anchor 绑定。静默拒写中止，准确半写按实际旧值 CAS 回填，原来源和 FieldGate 不绕过；原异常优先，后来值不覆盖，finally 撤销作用域。解药载体不执行未知容器方法；provider 回调无发布权，只有原 getter 的真实结果取得一次初始化。

有界 MPSC full 分支绑定真实 lvProducerLimit/lvProducerIndex/lvConsumerIndex、容量与 offerSlowPath、实际 task 和原默认 REJECT 链，使用现有只读 VM 方法版本/BCI/栈底深度查询。未知查询在同一 Submission 中持续保留，不能由重试成功消除；原 release、promise 终结和 cancel 一次收口保持。自定义队列/handler/backoff 和接受后抛异常仍 pending。

`socket0` op9 经原 NativeFileFrame 回执、停组/来源/owner/host 与 Java taskEffectAllowed 判断，原异常/finish 保持，不以静态 Class 建 descriptor 清理权。既有 unresolved 未提升为 ready，不主动调用这些 native；socket0 自身未解析时也保持 pending。`duplicateHandle(J)J` 的准确父 descriptor 接续仍未完成；Native/驱动不由本包构建代称全覆盖。

`build-c-java82-native75-carriers-20261015.log` **PASS，1分6秒**，27项任务中21项执行、6项保持最新，Java82/Native75 实际编译链接。`compile-c-java82-native75-carriers-20261015.log` **退出0**。`c-java82-native75-network-bytecode-20261015.log` 使用现有 AuditFixCheck 加载实际转换后的 Netty14类与 fastutil map/MapIterator，**退出0、NETWORK_BYTECODE_VERIFY_PASS、WORLD_REGISTRY_BYTECODE_VERIFY_PASS**，仅说明插桩能被 JVM 校验接受；fastutil 保留实际 frame、仅 COMPUTE_MAXS。`c-socket-java82-native75-20261015.log` 启动了真实 prelaunch/Native75，初始化实际报告 `NATIVE_FILE_CURRENT_BINDINGS_PENDING:74`，包含本轮新增 socket0。43秒栈位于 BufferBoundary/ASM 输出，179秒栈位于 HandleWriteBoundary/ExecutionFlow 来源记录，均未进入 socket 场景；原日志与两份线程栈保留。核对命令行/创建时间后仅结束本次自建 JVM115716，`RONOVA_GAME_EXIT=-1`；socket 业务断言 **NOTRUN/未完成**，不计为通过，未修改旧启动性能路径或反复重跑。未启动游戏或重跑旧矩阵；本批真实世界冻结、效果/provider、活跃入队后停组的行为仍 **NOTRUN**。

候选 `distribution/candidate-c-java82-native75-lifecycle-wip-20261015` 含 Core2747025、prelaunch23309字节；源码及匹配 JAR 直接同步 GitHub，未生成源码 ZIP，未部署驱动，完整 C/B 未完成。

---

## 2026-10-15：C 接续——标准状态载体收口

接续 `646a30e`，Java81 / Native74 / Storage3 / Journal6，协议保持 `r3-client-8`。标准全局 BlockState 表接入准确 ArrayList 长度/backing、identity reverse map 与实际数组修改判断；所有冻结格保护反查 ID，Global 另保护前向读取槽。原 addMapping 在副作用前按实际 state/id 拒绝，包括负 id 的原异常前发布。原版 rehash 的实际新旧 tuple 在发布前核对，准确字段/值/顺序与同一真实栈层接续，异常 finally 关闭。未知 strategy/List 不调用，shiftKeys 移除重排仍保留严格拒绝边界。

curativeItems 接入当前效果/hidden 的真实列表根，标准 ArrayList/Arrays$ArrayList backing 以及 ItemStack/NBT 复用原装备遍历和写门；共享 owner 分别核对，脱链立即解除。原默认 IForgeMobEffect provider 与真实结果的一次 null→值初始化、原合法复制/替换及正常/异常 finally 写齐。未知自定义 List/ItemStack/provider override 与未序列化 capability 没有提升覆盖。

原默认队列满拒绝以真实 offer 失败、同一 task/submission、LinkedBlockingQueue/REJECT 单例、当前方法版本及栈层配对后接入原清理；未知/custom/backoff/MPSC/接受后抛异常保留 pending。新增准确变换目标仅 RejectedExecutionHandlers$1，无新 bootstrap 成员或 Native 接口。

`build-c-java81-native74-carriers-20261015.log` **PASS，57秒**，27项任务中20项执行、7项保持最新；Native74/Storage3 源码未改、构建任务保持最新。`compile-c-java81-native74-carriers-20261015.log` **退出0**。`c-java81-native74-network-bytecode-20261015.log` 复用现有 AuditFixCheck 对本批真实 Netty 四类、原 fastutil rehash 织入结果用 `-Xverify:all` 加载并读取声明方法，**退出0、NETWORK_BYTECODE_VERIFY_PASS、WORLD_REGISTRY_BYTECODE_VERIFY_PASS**；rehash 只 COMPUTE_MAXS，保留实际 frame。结果仅是本批直接字节码加载，不是游戏行为验证；未启动游戏或重跑旧矩阵。真实游戏冻结/同步、curative 与满队列/停组场景 **NOTRUN**。匹配候选已写入 `distribution/candidate-c-java81-native74-carriers-wip-20261015`，Core2728834、prelaunch23309字节；源码和匹配 JAR 直接同步 GitHub，Native/驱动未改、未部署，未生成源码 ZIP，完整 C/B 未完成。

---

## 2026-10-15：C 接续——当前世界载体与异常队列收尾

接续 `ecae56e`，Java80 / Native74 / Storage3 / Journal6，协议 `r3-client-8`。原版冻结坐标继续下接 SimpleBitStorage.set/getAndSet、实际 long[] 的整 word/部分字节/bulk 写、标准 palette 的已用 ID 值表及 section/Data/storage 替换；当前真实 owner 与逐冻结格 BlockState identity 决定拒绝，不发全类/线程许可。原版邻居写、未用 ID 追加、padding、保持冻结内容的 resize/copy 走原实现。record 实际 offset 和压缩引用宽度随 VM 布局读取；未知实现及未观察 CPU store 仍保留。

活跃效果跟踪真实 Optional/FactorData，按当前 map/hidden 链解除旧 owner。准确原版 tick 和同 owner 的 getFactor 回填各有正常/异常 finally，重入层次不能借旧 scope；字段、Unsafe offset、bulk 共用现有门。curativeItems 深层图和自定义新增字段未覆盖。

Netty 提交只有原版真实 reject 事件和完整实际方法链才取得未入队事实；保留原 Throwable，release/setFailure 分段一次，异常尾尝试原 cancel，正常 false 沿原 caller cancel。提交和取消资格绑定同一真实 task tuple；未知/可能仍排队的任务保留 WRITES。recycle 的来源退役移到原字段真实清空之后，仍排队的任务不提前回收。新 bootstrap Submission 元数据在守卫安装前预定义。

共享全局 BlockState registry 的内部 IdMapper/List 本体、object-null 绝对地址及未知候选仍保留边界。`build-c-java80-native74-world-20261015.log` **PASS，40秒**，27项任务中20项执行、7项已是最新；Core/Agent/Bootstrap 与既有夹具实际编译，Native74/Storage3 源码未改、构建任务保持最新。`compile-c-java80-native74-world-20261015.log` **退出0**；`c-java80-native74-network-bytecode-20261015.log` 对当前转换后的真实 Netty write/WriteTask/SingleThreadEventExecutor 用 `-Xverify:all` 加载并读取声明方法，**退出0、NETWORK_BYTECODE_VERIFY_PASS**，只代表字节码加载通过。当前真实游戏冻结/解除、效果读取及停组队列分支 **NOTRUN**，不由编译代称通过；未启动新游戏检查、重跑旧矩阵或生成源码 ZIP。匹配候选已写入 `distribution/candidate-c-java80-native74-world-wip-20261015`，Core2704678、prelaunch23309字节；源码、匹配 JAR 与本摘要同步 GitHub，完整 C/B 未完成。

---

## 2026-10-15：C 接续——停组队列收尾源码

接续 `b084f1f`，Java79 / Native74 / Storage3 / Journal6，协议 `r3-client-8`。本包为原 WriteTask.run/cancel 保留真实 tuple/source 至 finally；release、tryFailure、原 flush 逐段异常收口，退出 token 后保留实际异常。停组任务只在原 recycle 字段清空后跳过回池，避免共享 recycler 写入自拒。

标准 Netty 清理对象图在真实释放/脱链之前读取并保留，含实际 buffer/chunk/handle/cache、promise/旧 Entry、计数器、leak tracker 及其实际槽位。资格同时核对真实字段/数组、完整框架链、实际递归层和当前 VM 方法版本来源；来源不清空，原 fieldGate/Backing/Resource 与写入记录继续执行。metadata 查询不执行外部方法，自定义回调不能继承资格，未支持的停组 CAS/PUT 明确失败。

冷 metadata 字段读取使用实际 JVMTI fieldID 与原 JNI 读取，不经过 Class.reflectionData；静态类未初始化即不读，primitive 仅对已初始化的真实 bootstrap 包装类直接装箱，不调用构造/valueOf。成功且准确获准的逐操作 receipt 建立恢复终态基线，原 sources/unknown 不变，失败 CAS 不标记。后续业务写能恢复至真实终态，不能把 refCnt/promise 复活；有效终态历史不作为 pending，更晚写入及不完整/重叠历史照常保留。

`build-c-java79-native74-queue-20261015.log` **PASS，1分1秒**，27项任务中20项执行、7项已是最新，Java 与 Native74 实际编译链接完成。`compile-c-java79-native74-queue-20261015.log` **退出0**。`c-java79-native74-network-bytecode-20261015.log` 使用真实 Netty 类字节及当前 NetworkBoundary 转换结果，经 `-Xverify:all` 加载和读取声明方法，**退出0、NETWORK_BYTECODE_VERIFY_PASS**；未安装 Agent 或运行停组收尾。AuditFixCheck 复用原夹具增加真实活动发送→原任务入队→外部真实控制台停组→独立未停消费者 finish 的两阶段分支，检查 refCnt、promise、无关队列、回调拒绝及 scope/receipt 退出。当前独立游戏消费者尚未接线，**游戏分支 NOTRUN**；没有放宽命令资格、伪造停组来源或重跑旧矩阵。池化/并发分支也不由编译代称通过。

匹配候选已写入 `distribution/candidate-c-java79-native74-queue-wip-20261015`，Core2674826、prelaunch23309字节。Storage/驱动未改、未部署，未生成源码 ZIP；源码、匹配 JAR 和本摘要同步 GitHub。完整 C/B 未完成。

---

## 2026-10-15：C 接续——底层冻结写入与准确恢复/发送

接续 `839375e`，Java78 / Native73 / Storage3 / Journal6，协议 `r3-client-8`。当前已完成源码包：实际 section/palette 物理索引写入在计数与 idFor 前按可见 holder/current chunk/section/palette identity 拒绝，包含 worker、共享 palette、同 section 邻居与解除接续；字段图以实际 VM offset 的 typed CAS 替代 primitive 先读后写，已知冲突 origin 保留为共享，最终独占复核与 CAS 同短锁，原写门准备/收尾在锁外；Netty 停源拒绝位于实际 promise 校验成功之后。装备核对没有确认新缺口，未改其实现。

首轮78/72统一构建39秒通过。三次实际阻塞的日志和当前 JVM 线程栈均保留：首次 frameSources 拼接→arraycopy；第二次来源 Walker 扩批；第三次外层停源 Walker 扩批。只在重新核对实际命令行/创建时间后结束相应自建 JVM，退出-1。源码移除该拼接，SourceQuery 保留已知来源+null/unknown及原 Native contributors，sourceUnknown/Native出口同步保留；只读 JVMTI reader 查真实模块、当前方法/BCI/VM版本和栈底深度匹配的动态父层来源，不因control跳过，不增加copy/Unsafe许可，回执及finally保持。

第四次真实栈确认 Class.newReflectionData 的准确实例缓存 CAS 被 represented-module 停源门误拒，JDK 反复重试；核对身份后仅结束该自建 JVM，退出-1。现复用准确 JDK 调用链及实际 Class.reflectionData 字段判定，仅区分该目标归属，仍执行真实 stoppedInvocation 及后续 offset/控制/来源与字段守卫；不移动检查顺序。

`build-c-java78-native73-strength-20261015.log` **PASS，22秒**，包含Native73实际编译链接；最新修复后 `build-c-java78-native73-strength-final2-20261015.log` **PASS，13秒**，最终Core2636524、prelaunch23309字节。`compile-c-java78-native73-network-promise-20261015.log` **退出0**。最新 `c-network-promise-java78-native73-final2-20261015.log` 使用真实 prelaunch、匹配 Native73、真实 bootStop named fixture 模块及原 EmbeddedChannel 断言，现已实际结束，**退出0、NETWORK_PROMISE_STOP_PASS**。真实 Class 消息的异 channel promise 保持原异常且未改写，已完成 promise 保持原异常，取消的 writeAndFlush 不额外 flush，合法停源拒绝仍 flush 之前无关写入；原检查顺序与参数未改。世界底层冻结与字段图游戏场景 **NOTRUN**；活跃排队后再停组的网络场景仍未运行，不能由 bootStop Class 消息检查代称。Storage/驱动未改，未安装或加载驱动，未重跑旧矩阵，未生成源码 ZIP；源码和匹配候选直接同步原 GitHub。

未知载体、直接 BitStorage/数组/Unsafe 写入、未观察共享来源、任意 native store/syscall 及旧明确平台边界保留。完整 C/B 未完成，自身外观和武器注册暂缓。

只读收尾核对：当前 Class 消息的 release 不进入引用计数，outboundAllowed 不将消息来源发布到 CURRENT；DefaultPromise.tryFailure 失败后也没有 CAS 重试。真实 WriteTask.run 的来源 scope 仍保持至 finally，ByteBuf 引用计数释放与 promise CAS 在该 scope 下的自拒尚未排除。该活跃排队后停组场景仍为 NOTRUN，不能由当前 Class 消息分支代称闭合。

---

## 2026-10-14：C 强度接续——旧帧、实际覆写、区域加载与 JVM 外部保护

接续 `aeb7e34`，Java77 / Native72 / Storage3 / Journal6、协议 `r3-client-8`。本包三路子代理及主代理整合：旧帧停止入口→真实线程事件→准确方法/来源/token 再核验→可退出子集强退→FramePop 配对收尾；GameMode/Item 实际覆写与原参数动作接续；手动区域标准写入/破坏/替换、直接时间天气与睡眠重置；原 PostProcessing/UpgradeData 保留、存盘与解除后续跑；独立 Windows session-owner 句柄规则及有原命令资格的显式 JVM 保护入口。没有通用 scope/counter 写权，未注册物品或制作自身外观。

首次沙箱构建被工具链原生加载限制阻断，日志保留。现有本机工具链集中构建 `build-c-java77-native72-strength-final-20261014.log` **PASS，1分7秒**，Core2625378、prelaunch23309字节，包含匹配 Agent/Bootstrap/Native72；现有装备/世界夹具分支编译通过。`build-c-driver72-owner-final-20261014.log` **PASS**，SYS38912、管理器190464字节，使用已有官方 WDK/SDK NuGet 与 Zig/LLD，未签名、安装、加载或修改系统签名设置；不是 VS/WDK MSBuild 验收。AuditFixCheck 已针对匹配类路径编译。一次真实 prelaunch 的 `c-system-arraycopy-java77-native72-strength-20261014.log` **实际退出0、SYSTEM_ARRAYCOPY_PASS**：新原生桥/绑定安装、真实受控 transformer 数组 Method.invoke/MethodHandle 拒写及原复制语义通过，没有伪造 owner/producer/token；不代称旧帧强退、装备或世界游戏分支通过。

旧帧的未知 finally/锁、框架/共享帧与原生/未结来源作用域保持 pending；未知 capability、任意 helper、自定义区块/升级器、未观察 CPU 写入及 syscall，以及旧/继承/kernel 句柄等边界保留。旧帧、装备和世界游戏分支未运行，不把入口初始化、构建或夹具编译视为完整 C/B 通过。源码与候选收口后同步原授权公开仓库；原始日志、存档及 ZIP 留本地。

---

## 2026-10-14：C 接续——启动期写入内联与真实数组复制入口

接续 `80ac1a8`，Java ABI76 / Native ABI71 / Storage3 / Journal6，协议 `r3-client-8`。prelaunch 对新的 HotSpot17 JVM 禁用对应 Unsafe writer 与 arraycopy 内联，保留原用户参数和累积清单；实际 System.arraycopy JNI 方法独立分类、seed 重读当前入口，准确目标接原数组策略。快照前建立原 source/from/length 读取窗口，目标只登记 to/兼容 prefix，MemoryCopy finally 保留来源、关闭两端并恢复前层。部分 ArrayStoreException 保留原兼容前缀及异常；源请求后缀贡献者保守并入前缀，未宣称逐元素来源精度。

首次完整构建37秒 PASS，AuditFixCheck system-arraycopy 分支编译通过。首次真实 prelaunch 实跑退出1：RONOVA_NATIVE_SCOPE_CAPACITY，ResourceBridge.arrayLayout 反射初始化与复制守卫递归。一次最小诊断复现退出1，实际 System 入口位于 java.dll+0x14035；匹配 PE 的 FF25 指向 IAT+0x169b8，准确 jvm.dll!JVM_ArrayCopy。现核对该实际导入项及当前地址后直接调用真实 VM 导出，保留准确 writer/current/valid/stop 和目标策略，不按整个 DLL 或计数放行。诊断已移除，原日志保留。诊断首个增量 build 曾错误 UP-TO-DATE，未运行旧 DLL；已补 arraycopy.c 构建依赖，再实际重编译。

最终 `build-c-java76-native71-intrinsics-final-20261014.log` **PASS22秒**，Core2586557、prelaunch23309字节。经真实 prelaunch 的 `c-system-arraycopy-java76-native71-final-20261014.log` **实际退出0、SYSTEM_ARRAYCOPY_PASS**：使用真实受控 transformer 数组，Method.invoke/MethodHandle 拒写、普通 primitive/reference 复制、双向重叠、零长、原异常、引用类型不匹配兼容前缀和异常后继续操作均通过，未注入假 owner/producer/token。此分支不代称逐元素来源回收、停组或游戏通过。未运行旧矩阵或游戏。

上一包75/70网络日志现已实际退出0，NETWORK_PRECONNECT_PASS；停组仍为 NETWORK_PRECONNECT_STOP_NOTRUN，不混同为通过。候选 `distribution/candidate-c-java76-native71-intrinsics-wip-20261014`，源码 ZIP `distribution/Ronova-Pro-C-Intrinsics-WIP-Java76-Native71-20261014.zip`。晚挂载旧帧自动强退/未观察原生指令/direct syscall 等未覆盖边界保留，驱动未部署；自身外观、武器注册暂缓，C/B 未完成。

---

## 2026-10-14：C 接续——真实攻击、延后挖掘与 JNI 数组来源

接续 `fea56f9`，Java ABI75 / Native ABI70 / Storage3 / Journal6；协议 `r3-client-8`。实际 handleInteract→Player.attack 限本次主手和真实目标；START/有效 STOP→ServerPlayer.tick 的实际玩家/GameMode 调用续接延后挖掘，逐次复查世界、目标坐标、当前槽/栈及代次，removeBlock 前再次核对。普通 tick 只有状态推进，外部回调、嵌套 tick、另一目标及旧 delayed 残值不能借装备权。GameMode 关键字段/玩家引用进入原字段、Unsafe 和内存写检查，状态写不继承 Item 委托；委托匹配真实方法 descriptor。换工具/ABORT/世界交接/release/异常撤销续接。

旧 `c-network-java74-native69-equipment-20261014.log` 退出1，完整 cause 定位首次发送冷加载类文件时的 `Inflater.inflateBytesBytes`；`scoped_sources` 捕获把 retained unknown 当失败。Native 数组副本现保留 unknown 与原 owners，八种 primitive 提交合并获取时和当前来源，原准确目标、停组、提交快照和释放配对不变。未清来源、预热、改截止或放宽生产资格。

统一构建 `build-c-java75-native70-equipment-actions-20261014.log` **PASS，1分14秒**，Core2580259、prelaunch22440字节；匹配 Agent/Bootstrap/Native70 与两个现有夹具编译完成。equipment-actions 分支含真实玩家攻击木镐耗损、提前 STOP 后实际 tick 挖掘耗损、外部事件中的字段篡改/嵌套 tick/另一目标拒绝及邻居/release；**游戏分支 NOTRUN**。AuditFixCheck 新类路径编译通过，75/70 原网络重跑 `c-network-java75-native70-equipment-actions-20261014.log` 当前运行，暂无通过结论。旧矩阵未重跑。

Java72/Native68 原禁疗 PID112724 本次核对仍在，latest.log 仍停留19:39 forgeserver 启动行，尚无进世界/禁疗结论，未停重启。候选 `distribution/candidate-c-java75-native70-equipment-actions-wip-20261014`；源码 ZIP `distribution/Ronova-Pro-C-EquipmentActions-WIP-Java75-Native70-20261014.zip`。本包未改驱动/管理器，原未签名产物未部署。自定义装备/GameMode 覆写等未覆盖边界保留，自身外观/武器注册暂缓，C/B 未完成。

---

## 2026-10-14：C 接续——装备、ItemStack 与 NBT

接续 `24f5dc3`，Java ABI74 / Native ABI69 / Storage3 / Journal6；协议 `r3-client-8`。标准 Player/Mob/ArmorStand 的当前六槽、准确载体、ItemStack 与当前 NBT 图接入原方法/集合/字段/Unsafe 写入规则，按代次、当前实际成员和全部共享 owner 判断，旧槽/节点/release 失活。拒绝拆分/取出返回 EMPTY，直接 Arrays$ArrayList.set 返回旧元素，避免副本先发布。实际引用宽度来自当前 JVM，静态帮助方法用准确 Class 和全参；修正 bootstrap 转换回传与抽象 primary 安装阻塞，结果处理器先安装，匹配 Java 桥升74。

15个真实动作入口保留原 packet 线程交接，准确 actor/手/栈/菜单和 finally 成对，同名嵌套与空 token 不能借旧许可。当前 Item 准确使用方法有限委托本次栈，写入时复查来源；共享对象全部 owner 仍须获准。未知自定义载体/capability 内部、Player.attack 与 GameMode.tick 延后挖掘的正常装备委托尚未覆盖。

首次整包编译因两处 final ItemStack→Mixin Access 强转失败，原日志保留；改为经 Object 转换后，最终 `build-c-java74-native69-equipment-final-20261014.log` **PASS45秒**，Core2560279、prelaunch22440字节，匹配 Agent/Bootstrap/Native 及现有 DefenseFixture equipment 分支编译完成。该真实 Cow 分支包含数量、拆分无副本泄露、NBT/primitive 叶/实际载体写拒、邻居/release，**游戏分支 NOTRUN**；未起第三个重游戏实例或重复旧 Native/Host 矩阵。

旧网络69重跑已退出1：三次 outbound、两次收包，首次 promise 为 EXTERNAL_NATIVE_CALL_SOURCE_UNOBSERVED/scoped_sources，其余成功。AuditFixCheck 仅保留原 Throwable cause，未加等待/flush/read、未改 deadline 或资格；本次匹配 Java74/Native69 重跑 `c-network-java74-native69-equipment-20261014.log` 仍运行，无生产修复/通过结论。原禁疗 Java72/Native68 实例 PID112724 核对仍在，一次线程栈 main RUNNABLE，处于 Mixin 类资源读取/来源查询链，未进世界或取得 NO_HEAL_PASS；未停/重启。

本次 Native69、驱动/管理器源码未改，未签名驱动沿用、未部署。候选 `distribution/candidate-c-java74-native69-equipment-wip-20261014`，ZIP `distribution/Ronova-Pro-C-Equipment-WIP-Java74-Native69-20261014.zip`；源码与匹配 JAR 同步公开 Git，原日志/ZIP/存档留本地。自身外观及武器注册暂缓，C/B 未完成。

---

## 2026-10-14：C 接续——活跃效果、饥饿数据与底层禁生成

接续 `fd77b99`，Java ABI73 / Native ABI69 / Storage3 / Journal6，客户端协议 `r3-client-8`。effects/FoodData 准确载体与代次接入原方法、集合、字段及 Unsafe 写入路径；正常原版效果 tick、食用和 FoodData tick 保留，本次许可与异常退出成对，外部回调及嵌套新入口不能借上一层回执。单独禁疗不附带该策略。FoodData 外来篡改防护不等于免饿；未知 Map/损坏链保留缺口，继续原生命/索引保护，装备/ItemStack/NBT 仍未完成。三参数/对象返回分派需要匹配代理，Java桥统一升73；同时修正 Core 遗留的 ABI55 检查导致整组生产守卫安装被拒的问题，实际游戏安装结果仍待运行。

禁生成从 addFreshEntity 继续接到真实 manager 的新准入入口；实际目的地决定区域，existing=true、玩家与原期限不变；只读查询使用原规则发布的区域快照。完整初版 ABI72 构建26秒 PASS；修正上述安装阻塞并统一 ABI73 后，独立输出的最终 `build-c-java73-native69-live-state-20261014.log` 30秒 PASS，Core/agent/载荷与现有 DefenseFixture live-state、WorldFixture world-spawn-admission 分支编译完成；更新的网络 AuditFixCheck 编译通过。上述游戏分支及 FoodData **NOTRUN**，没有新增游戏实例或重复旧 Native raw 检查。

旧网络69实例已退出1，准确计数为三次到 outbound、对端两次收包，failure=null。新 `c-network-java72-native69-live-state-20261014.log` 保留原10秒等待与原发送流程，仅增真实 promise、flush、read/readComplete 和失败队列状态以定位原因，当前仍运行，没有生产修复或通过结论。原 Native68 禁疗实例仍初始化，未见 WORLD_LOADED/Done/NO_HEAL_PASS；旧失败原样保留。

匹配候选 `distribution/candidate-c-java73-native69-live-state-wip-20261014`；源码 ZIP `distribution/Ronova-Pro-C-LiveState-WIP-Java73-Native69-20261014.zip`。自身外观及武器注册暂缓，C/B 仍未完成。

---
## 2026-10-14：C 接续——Unsafe 裸地址写入、释放与回调借权

接续 `732aeaa`，Java ABI72 / Native ABI69 / Storage3 / Journal6；客户端协议仍为 `r3-client-8`。上一包的发送来源、Host 效果定序和独立禁疗源码保持；自身外观和武器注册继续暂缓。

已观察的 Unsafe 裸地址 put/set/copy 原入口此前能将已登记 Native 控制地址当作未跟踪业务内存写入；free/reallocate 的另一条实际分派也会在业务目录未命中后继续调用 JVM 原函数。现于这两个真实 admission 入口、业务目录和 allocation gate 之前查询准确 `native_control_range`，命中分别抛出 `NATIVE_MEMORY_CONTROL_WRITE_DENIED`、`NATIVE_MEMORY_CONTROL_FREE_DENIED` 或 `NATIVE_MEMORY_CONTROL_REALLOCATE_DENIED`，未知/溢出为 `NATIVE_MEMORY_CONTROL_RANGE_UNAVAILABLE`；保留异常并沿原清理路径撤销临时来源/分配，不返回空 token 假装准入。realloc 新长零仍检查非零旧地址；NULL、普通未知分配和原业务生命周期保留。原始读取不因该写入规则被拒绝。

实际外部 JVMTI 事件、受支持扩展和 heap callback 在成功建立原来源/hold 后保存并清零借来的 `control`，退出时按嵌套层恢复；只以原 retained image 保留本 DLL 的原控制回调。原 source、受限标志、临时 TLS、环境及 worker anchor 接续保持。扩展生成 frame 已按真实 sizeof 计算，未硬改未知 ABI。同步外部回调借权案例未实跑，不能用下述 raw JNI 回调检查代称。

本包在独立 `.work/c-raw-unsafe72-69-20261014/build/` 构建，原 native 构建脚本接受 Gradle 传入的实际输出目录，避免覆盖运行中的载荷。首个写入/回调版本构建 PASS43秒，原 raw 写入检查退出0；补齐相邻释放/重分配后一次集中重建 `build-c-java72-native69-raw-unsafe-final2-20261014.log` PASS22秒，相关现有夹具编译通过。最终 `c-raw-unsafe-java72-native69-final2-20261014.log` 实际退出0、RAW_UNSAFE_PASS，Native/JNI 与线程绑定就绪。

最终直接检查在真实 JNI 调用中取当前 JavaVM/JVMTI/JNIEnv 三个首槽，保持实际新 JVMTI 环境存活：公共 sun.misc 及准确 bootstrap 私有 native putLong、setMemory0、copyMemory0 的同值/同字节写拒绝；公共与真实 private free/reallocate（含零新长）拒绝；每次均要求准确目标拒绝异常，原八字节保持。普通真实 Unsafe allocation 的写入、复制、扩容、缩容、合法 NULL/零长度及配对释放保持，拒绝 copy 后的原读 scope 能结束，真实 Dispose 完成。只给夹具 JVM 加 internal Unsafe 反射访问参数，未加生产资格或假 producer。原 passing Host68 内存检查未重跑。

该查询只关闭已登记控制范围的确定性绕过；不跨真实 JVM Unsafe 函数持 private/domain 锁，后续并发发布 TOCTOU 仍保留。未观察原始入口、CPU store/syscall、未知扩展 ABI 和其它旧未支持边界不提升为完成。

网络 Java72/Native68 final3 最终退出1，只有原10秒收包断言失败、未记录底层异常，原日志保留，不能据此认定只是延迟。现有检查改为先等待真实 client EventLoop scope 哨兵，再等单 loop 服务端已提交任务，随后仍作原10秒收包/来源断言，并记录剩余计数；没有提高 deadline、预热框架或改生产网络规则。Java72/Native69 已用同一生产路径、真实 fixture module 和 LocalChannel 重跑，`c-network-java72-native69-20261014.log` 当前仍在执行，无发送/停组通过结论；WriteTask 真实篡改停组仍 NOTRUN。

Java72/Native68 的禁疗空白世界 `.work/c-no-heal72-20261014-01/server/` 原实例继续运行；19:00日志已进入实际 Forge/ModLauncher 的 Mixin SERVER 阶段，尚无 WORLD_LOADED/Done/NO_HEAL_PASS，不算 Native69 游戏通过。未停止或重启来替换结果。此前 Java63/Native61 run03 已不在，旧日志仍无冻结保存结论。Native69 未改驱动/管理器，仍带既有未签名 SYS37888字节、管理器190464字节，未部署。

本轮候选 `distribution/candidate-c-java72-native69-raw-unsafe-wip-20261014`，Core2474311字节、prelaunch22440字节；匹配源码包 `distribution/Ronova-Pro-C-RawUnsafe-WIP-Java72-Native69-20261014.zip`。源码、匹配候选 JAR 与本报告摘要同步公开 Git；ZIP、原始日志、研究和存档留本地。完整 C/B 仍未完成：上述实际网络/禁疗运行、同步外部回调及旧客户端重放、冻结保存重启等没有结果；活体效果、饥饿和装备状态保护仍缺完整实际写入语义。

---
## 2026-10-14：C 接续——发送来源、原生提交与独立禁疗

接续 `4697c24`，Java ABI72 / Native ABI68 / Storage3 / Journal6，客户端协议 `r3-client-8`。主工程仍为 `D:\桌面\ronova_GPT`；自身外观及武器注册暂缓。

连接前的实际 Connection/PacketHolder 队列按每次提交保留来源，真实 poll 到私有发送和 EventLoop 延后执行保持同一收据，原 Runnable 结束后归还 scope。共享 vanilla packet 的不同提交不互相继承发送者；退役只移除准确、独占、已停来源的 CLQ 节点。Netty WriteTask 的 ctx/msg/promise 配对变化保留已捕获发送者并加入实际来源与 unknown，避免失配清空发送者。真实字段和私有方法名从当前字节码的生产/消费路径解析，兼容本轮实际 SRG 载荷。

已观察 Host 的 WriteProcessMemory、VirtualProtect/Ex、VirtualFree/Ex、UnmapViewOfFile/Ex 在来源和域查询完成后，以同一短 private gate 复核准确私有分配及本地输出并执行真实 API。释放在 gate 内重查实际 allocation geometry；受限回调使用 Try 锁，不在门内进入 JNI/VM 或其它域锁。本次只衔接本方私有分配登记与这些已观察效果，不推广为任意原生 store/syscall、VM 物理回收或映像复用均已闭合。

新增 `/ronova_pro no-heal <目标> true|false`。单独禁疗只拒绝真实健康槽升值及正向 heal，普通伤害、移除照旧；与 protect 独立组合，false 保留 protect，release 撤销两位。setHealth、SynchedEntityData、实际 DataItem、既有字段/Unsafe 桥共用真实 healthItem 判断，覆盖实际继承分派。合法 Clone、换维度与最终 respawn 通过原 permit 交接；新造的健康观察不持有身体/关系快照，已有未结 Work 不被释放。客户端两位随同一 revision 发布，按实际世界、ID/UUID 重绑；旧协议不解码新增字段。`NO_HEAL/1` 仅为当前会话操作记录，未实现跨重启恢复。

完整包初次离线构建仅因实际 runtime classpath 缺少 srgutils 失败，保留 `build-c-java72-native68-network-memory-20261014.log`；正常解析 `resolve-c-runtime72-68-20261014.log` PASS22秒。源码写齐后 `build-c-java72-native68-strength-final3-20261014.log` PASS27秒。引用持有修复后的 final4 因运行中夹具 JAR 被占用而失败，原日志保留；改用独立夹具输出后 `build-c-java72-native68-strength-final5-20261014.log` PASS12秒，Agent/Bootstrap/Native 均为未变的 UP-TO-DATE 载荷。

直接相关实际结果：`c-memory-effects-java72-native68-20261014.log` 退出0、HOST_MEMORY_EFFECTS_PASS，Native/JNI 与线程绑定就绪。IAT/GPA 的普通写入、权限修改、释放/取消映射、真实 same-access alias、decommit/reservation/recommit 及文件视图效果保留，准确控制目标、本地输出和页尾范围拒绝。未确定性竞跑新私有分配与实际写入，不能据此宣布并发关闭全部通过；未重跑旧矩阵。

网络首次运行在 PropertiesUtil 的 JarURLConnection 缓存链失败，final2 又在真实 Mod 发送的数组写入回调遇到 TreeMap$AscendingSubMap 类循环；两个原失败日志保留。已收窄容器出生的分析归属，仅透过实际容器构造/clone 链，保留 ASM 图交接时的显式保护；并在安装钩子前定义 tailMap.values 实际使用的八个冷依赖类型。final3 原 JVM 正继续：18:34 现场已在真实 LocalServerChannel/Netty allocator 类转换，未取得 NETWORK_PRECONNECT_PASS，不能记为发送或停组通过。WriteTask 真实字段篡改后停组未实跑。

禁疗实际游戏使用最终 Core 和独立重映射夹具，在新空白世界 `.work/c-no-heal72-20261014-01/server/` 启动，父进程89308；截至18:36只有 Native/JNI/线程绑定就绪和 Agent 安装日志，尚无 WORLD_LOADED/Done/NO_HEAL_PASS。现有 DefenseFixture 的独立 no-heal 分支只测试普通 Cow 的四条升血入口、正常伤害、组合和独立撤销、邻居保持；没有假 Binding/ServerLevel，也不执行旧防护矩阵。

此前 Java63/Native61 run03 的原 PID 已不在，未找到新的正常退出、世界加载或冻结保存结果；旧 PID106440已被其它应用复用，不是旧网络 JVM。旧日志保持原范围，未当作本包游戏通过。驱动/管理器源码、签名及部署未改，沿用既有未签名 SYS37888字节、管理器190464字节，未加载。

本轮候选 `distribution/candidate-c-java72-native68-strength-wip-20261014`，Core2474241字节、prelaunch22440字节；匹配包 `distribution/Ronova-Pro-C-Strength-WIP-Java72-Native68-20261014.zip`。本次源码、对应候选 JAR 与既有报告摘要同步公开 Git；ZIP、未签名系统产物、原始日志、第三方研究和存档留本地。完整 C/B 未完成：本轮网络及禁疗结果仍待取得，旧客户端停组重放、真实冻结保存/重启、Native 返回策略等未跑项仍保留；活体效果、饥饿和装备状态保护尚没有对应的完整写入拒绝语义，不能由生命防护或字段图清体代称。

---
## 2026-10-14：C 接续——Windows 读取 API 的本地输出保护

接续 `f51380d`，Java ABI71 / Native ABI67 / Storage3 / Journal6。ReadProcessMemory 已接入真实 IAT/GPA 分类及分发；[原接口](https://learn.microsoft.com/en-us/windows/win32/api/memoryapi/nf-memoryapi-readprocessmemory) 的数据和字节数输出均在本地，保护不依赖源进程是否为当前进程。来源和域查询完成后，短 private gate 内复核两个实际输出范围及停止状态，再仅调用一次真实 API；不预清或暂存原输出，获准调用保留实际 BOOL、即时 LastError 和可能的部分效果。源句柄仅以 SAME_ACCESS 固定自有 alias。自有快照读取走保留真实入口；GC/ObjectFree 只补必要的受限路由和 Try 锁，不进入普通 JNI 来源路径。

控制登记通知的资格检查，继续取实际 StackWalker 调用者，仅用 CodeSourceBridge/实际 ControlRegistry 的精确类身份替代原 nest 查询；其它三类资格和发布前通知顺序保留。原 run03 的15:41实际栈显示主线程等待 CONTROLS，continuation 持锁调用该 nest 查询；前序栈显示线程仍会推进，不能定性为持续死锁。本修复去掉这一个已观察的持锁 VM 查询，未挪锁或撤销通知，不宣称世界启动已修。

完整源码写齐后构建 `build-c-java71-native67-read-20261014.log` PASS33秒；复用夹具独立 read-memory 分支编译通过，`c-host-read67-20261014.log` 实际退出0、HOST_MEMORY_READ_PASS，Native/JNI 与线程绑定就绪。IAT/实际 GPA 路径的普通复制、可选 NULL、仅 VM_READ 的真实当前进程 alias、零长度输出均通过；真实 VM/JVMTI/JNI 首槽作为数据或字节数输出均拒绝，字节和映射保持，字节数拒绝不先写普通 payload。未重跑旧矩阵；跨进程实读、真实受限回调和并发生命周期未实跑，不作扩展通过声明，原未观察入口边界保留。文件绑定仍有74 pending。

原 Java63/Native61 run03 会话本轮仍存活，日志到15:15进度加载，尚无 WORLD_LOADED/Done/冻结保存结果，未停止或重启。自身外观及武器注册暂缓；驱动/管理器源码、签名及部署未改，沿用既有未签名产物。候选 `distribution/candidate-c-java71-native67-read-wip-20261014`，Core2456819字节、prelaunch22440字节；匹配包 `distribution/Ronova-Pro-C-HostRead-WIP-Java71-Native67-20261014.zip`。源码与报告摘要同步 Git，旧包、原始日志、候选和存档留本地；完整 C/B 仍在收口。

---
## 2026-10-14：C 接续——JNI 调用者输出保护

接续 `2d6db6f`，Java ABI70 / Native ABI66 / Storage3 / Journal6。自身外观与武器物品注册继续暂缓，沿用现有命令和底层入口。

GetJavaVM、8类 GetArrayRegion、GetStringRegion/GetStringUTFRegion、GetStringChars/UTFChars 的 isCopy，以及既有数组副本和 StringCritical 的 isCopy 输出，均先用本次临时值，再通过共用短 gate 检查准确控制范围并实际复制。入口只观察真实已附加当前 JNIEnv 首槽；heap/GC 受限上下文不进入 JNI、不 attach。原 VM 调用和配对 Release 均在 gate 外，原异常保留；UTF 仅提交实际 modified-UTF8 字节和 VM 写入的 NUL，空 UTF Region 仍保持实际单字节 NUL 行为。isCopy 拒绝时清理本次尚未发布的 lease/VM 指针，不误释放已有副本。JNI 数组和字符串副本业务 contents 仍可作为普通输出。

既有 Host 控制范围查询接入同一准确私有分配目录，放开该叶锁后才查询其它域；查询命中 JNI 首槽已有实际检查。此接线不把 Host/Unsafe 原有“检查后调用原 API”升级为整个效果的原子保证。

完整工作包写齐后集中构建：`build-c-java70-native66-jni-20261014.log` PASS39秒；复用既有夹具新增 jni-output 分支，编译通过，`c-jni-outputs66-20261014.log` 实际退出0、JNI_OUTPUT_PASS，Native/JNI 与线程绑定就绪。实跑 GetJavaVM、IntArrayRegion、两种 StringRegion、StringChars/UTFChars 与现有 Elements/Critical isCopy：普通输出、modified-UTF8/NUL/空 Region 尾部保持、业务数组副本作为 Region 目标与配对释放通过；真实 VM/JVMTI/JNI 三个首槽均拒绝，字节和映射保持；真实原越界异常保留。另只检查当前 DLL 的普通 WriteProcessMemory 成功与 JNI 首槽同值写拒绝，原 written canary 保持。未重跑旧矩阵，也不将 Int 实跑推广为8类数组均已实跑；文件绑定仍有74 pending。

原 Java63/Native61 run03 会话仍存活，原日志已前进到15:09加载配方、15:15加载1271条进度；尚无 WORLD_LOADED/Done 或冻结保存结果，未重启，不能当作本包游戏通过。驱动/管理器源码未改、未重建或加载；沿用未签名 SYS37888字节、管理器190464字节。未观察缓存原表、扩展输出、任意 store/syscall、最终映像 unmap/复用等原边界保留，完整 C/B 仍未完成。

候选 `distribution/candidate-c-java70-native66-jni-wip-20261014`，Core2457423字节、prelaunch22440字节；匹配源码及产物包 `distribution/Ronova-Pro-C-JNI-WIP-Java70-Native66-20261014.zip`。源码和既有报告摘要同步 Git，ZIP、候选、原始日志及存档留本地。

---
## 2026-10-14：C 接续——JVMTI 与 JavaVM 输出保护

接续 `4c8641a`，Java ABI69 / Native ABI65 / Storage3 / Journal6。完整当前工作包写齐后集中构建；自身外观和武器注册继续暂缓。

已观察 JVMTI 表的查询输出、Allocate、Deallocate 和 CreateRawMonitor 接入真实原 API。固定值/结构、多输出、VM 分配结果及 GetStackTrace 调用者缓冲区先由 VM 写本次临时结果，成功后所有实际输出槽在同一短 gate 内复核，再复制；准确控制范围拒绝时不发布一半的 count、pointer 或帧。必需/可选 NULL 与原错误保持，拒绝后释放本次 VM buffer、嵌套字符串、JNI localref 或新 monitor。准确本 DLL 的直接 C 调用仅对本方 native_ti 使用保留原入口，外部回调不能借深层控制帧取得此路径。Suspend/ResumeThreadList 的结果预检查和实际提交接同一 gate；临时分配失败不再把尚未执行的已准入线程记为完成，提交失败也不抹掉实际已发生的效果。

本 DLL 的实际 CRT 私有分配、重分配、字符串副本与 VirtualAlloc 页覆盖分配至登记、注销至物理释放的短定序；元数据本体也保护。JNI 数组和字符串副本的业务 contents 明确保留为外部值，lease/来源仍属控制。真实观察的 IAT 槽、VM/JVMTI 首槽及当前 JNI 环境首槽登记准确范围；JNI 槽观察与 Java 生命周期标志分开，ThreadEnd 外派前仍登记有效 env，结束后撤销。实际 heap walking 栈记录保留至 finish 末尾。未登记整个堆或凭地址猜分配器。

普通 VM 查询、Java 与外部回调不持上述 gate。官方允许的 callback-safe 输出通过当前实际回调环境借用原 active hold，无 JNI、无 attach，忙时明确拒绝；GC/ObjectFree 的受限身份沿真实回调范围恢复。[JDK17 接口定义](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/hotspot/share/prims/jvmti.xml) 中的 CreateRawMonitor 可用于该回调，最终修正其标志。Deallocate 只在短 gate 内拒绝当前准确控制点，放锁后调用真实 VM 释放；Dispose 保留逻辑 API 范围至真实成功返回再撤销。二者均可能进入 VM，不能持锁等待 GC；VM 内部物理回收、无效/过期指针与并发 double-free/reuse 不宣称原子闭合。

实际检查首两轮的输出分支虽 PASS，日志出现 Native 初始化退化，未算整包通过；`c-jvmti-outputs65-20261014.log` 与 `c-jvmti-outputs65-final2-20261014.log` 保留。补充实际错误信息后定位 NATIVE_UNSAFE_VM_LAYOUT_REQUIRED：旧元数据检查把新 GetBytecodes 包装地址误认作错误 JVM。现仅从准确本方 live 环境、当前 retained shadow 与该实际 wrapper 关系解回保留的原 GetBytecodes，再核原 JVM 映像；原 GetJavaVM/GetEnv 及 HotSpot 导出布局检查保留，未放宽外部资格。

修复载荷构建 `build-c-java69-native65-outputs-final3-20261014.log` PASS21秒；复用既有 AuditFixCheck 的独立 jvmti-output 分支，夹具编译 PASS，`c-jvmti-outputs65-final3-20261014.log` 实际退出0且 JVMTI_OUTPUT_PASS：Native/JNI 与线程绑定就绪；真实 vm/api/env 三个首槽均拒绝 GetEnv/Attach/Daemon 输出、GetVersionNumber/Allocate/GetMethodName/GetStackTrace 输出及 Deallocate，原字节与页保护保持；多输出拒绝不改普通 canary/帧/count。普通 GetEnv/Attach/Daemon 对已附加当前线程返回原 JNIEnv；普通 Allocate/Deallocate、可选 NULL、原 INVALID_METHODID、当前栈，以及真实 JNI 数组副本写版本后 JNI_ABORT 释放均通过。文件绑定仍报告74 pending，不冒充全部就绪。

最终 `build-c-java69-native65-outputs-final4-20261014.log` PASS23秒；最后仅修正 CreateRawMonitor 的 callback-safe 标志，普通已通过分支未受影响，未重复运行。真实受限回调、首次 worker 附加及 ThreadEnd 并发攻击、线程列表效果与本族其余查询未逐项实跑，不作全族实机通过声明；未重跑旧矩阵。未观察原表/缓存入口、扩展 variadic 输出、任意 CPU store/syscall、最终映像 unmap 与映射复用等边界仍保留。既有 Host/Unsafe 各写入入口没有因此自动取得本次提交 gate 的原子保证。

原 Java63/Native61 run03 的既有运行会话本轮确认仍存活；日志仍停于13:46的资源阶段，没有 WORLD_LOADED、Done 或冻结保存结果，未重启或停止，不能算新包游戏通过。驱动/管理器源码未改、未重建、未加载，沿用未签名 SYS 37888字节及管理器190464字节。

本地候选 `distribution/candidate-c-java69-native65-outputs-wip-20261014`，Core JAR 2454939字节、prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Outputs-WIP-Java69-Native65-20261014.zip`。同步源码及既有报告摘要，旧包、存档与原始日志留本地。完整 C/B 继续收口。

---
## 2026-10-14：C 接续——队列消费、控制线程与方块事件

接续 `9afb934`，Java ABI68 / Native ABI64 / Storage3 / Journal6。本包三项生产入口已完整写齐后统一构建；自身外观与武器注册继续暂缓。

- 绘制：实际 RenderSystem.recordRenderCall 的 recordingQueue.add 每次发布独立 RenderCall Proxy 条目，保存本次来源和不可回退的 unknown，不把共享 delegate 的所有提交来源混为一体。真实 replayQueue.poll 在同一 SOURCES 锁内移交 ACTIVE/CURRENT，再经 bootstrap 执行和 finally 结清；发布中、原队列中及已取出尚未执行的条目接入原 retirementGap。准确类、模块、接口、队列和执行配对继续核对，停组后丢弃业务效果，组外提交沿原队列执行。异常保留，迟到的发布尾不复活已完成条目；只精确预定义五个本方回执元数据类型。
- Host：已观察的 SuspendThread/ResumeThread/TerminateThread 先以真实 DuplicateHandle 固定本次实际对象和原访问权，再用 CompareObjectHandles 与控制收集线程的实际稳定句柄比较。相同对象的外部别名也拒绝，比较不可用或失败明确返回；允许时只对固定的临时句柄执行原 API 一次，原参数、结果与 LastError 保留，所有路径关闭临时句柄。普通外部别名的 CloseHandle/flags 不因同对象被当作私有槽位。
- 世界：真实 ServerLevel.runBlockEvents 的唯一 shouldTickBlocksAt 处叠加准确位置冻结判断。冻结事件沿 vanilla 原有重排路径等待，恢复后继续原类型/状态检查及触发，不增加持有目录、存档格式或区块加载。

集中构建 `build-c-java68-native64-effects-20261014.log` PASS35秒。仅复用既有 Host free-exit 分支，`c-host-thread-effects64-20261014.log` 实际退出0：IAT/GPA 四个真实未附加 JVM 的 worker 均经改后的普通 ResumeThread 正常进入，原退出码61/62/63/64、entered1/returned0、userdata、signaled 和句柄关闭均保留；两种资源视图实际消失，准确控制目标映像保留。此结果是受影响的普通线程路径检查，不是私有收集线程别名攻击案例，也不证明内部应用引用/final unmap。

真实客户端“入队→停组→重放”、私有收集线程别名的三种线程操作、实际活塞冻结/恢复仍 NOTRUN；没有伪造客户端/ServerLevel/producer、添加控制见证出口或重跑旧矩阵。此前停组返回策略的真实 Native 早退与动态代次切换仍未实跑。已观察 JVMTI 表中仍直通原表的查询输出与 Deallocate，及其控制块分配/发布到实际提交定序，是后续原生收口范围；本包未宣称处理这些入口。

原 Java63/Native61 run03 仍由原实例运行，日志已推进至13:46的 vanilla 资源阶段；认证公钥请求报 SSL 错误，其后仍有资源日志。尚无 WORLD_LOADED、Done 或冻结保存结果，不能算作本包游戏通过，未重启或停止。驱动/管理器未改、未重建、未加载，沿用未签名 SYS 37888字节及管理器190464字节。

本地候选 `distribution/candidate-c-java68-native64-effects-wip-20261014`，Core JAR 2437401字节、prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Effects-WIP-Java68-Native64-20261014.zip`。同步本包源码和既有报告摘要，旧包、存档与原始日志保留本地。完整 C/B 继续收口。

---
## 2026-10-14：C 接续——实际停组返回策略

接续 `d6c1152`，Java ABI67 / Native ABI63 / Storage3 / Journal6。原命令接受的六种 returns 策略此前在 Java 停组表被统一丢为 default；现保留 default/null/empty/uuid-fixed/uuid-each/invalid-id 的实际选择，并在关停 Native module gate 前发布同一策略。固定 UUID 由准确 module 的策略代次生成并供 Java/Native 共用，同模式重复发布复用，切换后重新选择；没有新增返回语法或业务资格。

普通基本类型返回合法零值，invalid-id 仅将 int 设为 -1；强制 null 覆盖全部引用及 Future，只有 empty 创建零长度数组和空字符串。其他非 null 策略保留准确类型的 Optional、集合、迭代器、Stream 及已完成空结果 Future 适配，子类不按名称套用。Future 经无参工厂以准确 JDK17 CF.NIL 和私有构造器创建全新实例，避开 inline factory 的同步 observer；每次 UUID 经既有 [BCrypt 系统随机源](https://learn.microsoft.com/en-us/windows/win32/api/bcrypt/nf-bcrypt-bcryptgenrandom)生成 v4 值，再调用准确 UUID(long,long) 构造器。两条 Native 入口只构造新值，不接业务目标、值或回调，调用后归还原 control 深度，原 JNI 异常保留，停组业务写入规则不变。

实际复跑先发现 ValueIterator 循环加载、外国 nest host 被来源检查额外加载、Future 未完成，以及 UUID$Holder 首次加载被拒。修复准确 walker 后，在守卫安装前预定义 ExecutionFlow 的直接成员元数据闭包及本链已定位依赖；没有清除 unknown、伪造 producer、扩大 Native 数组放行或预热整个 bootstrap/JDK。中间轮虽有功能 PASS，仍出现 ArrayChange/JPLIS 异常，未将其算作完整通过。原失败日志与中间结果均保留。

最终集中构建 `build-c-java67-native63-return-policy-final4-20261014.log` PASS18秒。复用既有 GroupReturns 分支，以 AuditFixCheck 当前入口运行真实 fixture JAR 的实际 ModuleLayer/metadata；六个独立 JVM 均退出0，`c-return-policy67-final3-{invalid-id,uuid-fixed,uuid-each,null,empty,default}-20261014.log` 均有 MOD_GROUP_RETURN_EFFECTS_PASS，未再出现此前数组写拒绝、类循环或 JPLIS 断言。覆盖基本类型、数组、UUID、准确空适配器、Future 终态、业务副作用抑制和无半对象构造；未新增夹具角色或重跑旧矩阵。真实 Native 方法早退/ForceEarlyReturn 的各策略及动态策略代次切换未实跑，源码接线不冒充这些场景通过。

原 Java63/Native61 run03 仍由原实例运行，launch/debug 日志尚停于 Forge 加载阶段，没有世界加载或冻结保存结果，不能算作本候选游戏通过；未重启、未停止。驱动及管理器源码未改、未重建、未加载，沿用未签名 SYS 37888字节、管理器190464字节。自身外观与武器注册继续暂缓，完整 C/B 继续收口。

本地候选 `distribution/candidate-c-java67-native63-returns-wip-20261014`，Core JAR 2424274字节、prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Returns-WIP-Java67-Native63-20261014.zip`。只同步本包源码与现有报告摘要，旧包、存档和原始日志保留本地。

---

## 2026-10-14：C 接续——非返回卸库入口

接续 `21dd6bc`，Java ABI66 / Native ABI62 / Storage3 / Journal6。已观察 IAT/GPA 的 FreeLibraryAndExitThread 现接入实际来源、停组及准确控制 DLL 原始 HMODULE 检查；允许时原参数调用真实 API 一次，拒绝仅抑制卸库，仍以原退出码调用真实 ExitThread，保持 non-return。没有把它拆成普通 FreeLibrary 后再退出。接口合同见 [微软文档](https://learn.microsoft.com/en-us/windows/win32/api/libloaderapi/nf-libloaderapi-freelibraryandexitthread)。

本次来源、image active 和 callback hold 由原 HostModuleRelease 堆记录及准确当前线程的私有 SYNCHRONIZE witness 保留，真实线程 signaled 后才在原 reaper 归还；同一 worker 的本次待回收 scope 暂缓旧 gap 判定。未附加线程来补造租约，旧 JNI/加载器/回调遗留状态继续 pending。私有 witness 的准确查询与实际句柄操作处于同一目录快照，受限入口锁忙明确 ERROR_BUSY，普通句柄不因目录忙冒认为私有。

初次集中构建 PASS30秒，`build-c-java66-native62-free-exit-20261014.log`；首次入口检查在 ResumeThread 返回 -1、worker 尚未进入入口时失败，原日志 `c-host-free-exit62-20261014.log` 保留。修正上述私有句柄误判后，最终集中构建 `build-c-java66-native62-free-exit-final-20261014.log` PASS19秒，现有夹具增量编译 PASS。独立 `free-exit` phase 实际退出0，`c-host-free-exit62-final-20261014.log`：IAT/GPA 各一资源视图和一准确控制目标，四个未附加 JVM 的真实 worker 保留退出码61/62/63/64、entered1/returned0、准确 userdata，均真实 signaled 且句柄关闭；资源视图实际消失、临时文件删除；控制映像、权限和原字节保留。

控制目标使用借用的准确句柄作为应拒绝攻击请求；映像存活不证明内部引用计数未减。普通可执行 DLL 的应用引用及最终 unmap、真实 producer 的 stopped/共享来源、受限回调和完整内部退出回收仍未实跑，不记为通过。未重跑旧矩阵，没有新证明目录；驱动/管理器未改、未重建、未加载，沿用未签名 SYS 37888字节及管理器190464字节。原 Java63/Native61 run03 仍由原实例运行，尚无世界加载或冻结保存结果，不能算本包游戏通过。

本地候选 `distribution/candidate-c-java66-native62-free-exit-wip-20261014`，Core JAR 2420867字节、prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Free-Exit-WIP-Java66-Native62-20261014.zip`。自身外观与武器注册继续暂缓，完整 C/B 继续收口。

---

## 2026-10-14：C 接续——Screen/Overlay 根来源与退役

接续 `26c9cfc`，Java ABI65 / Native ABI61 / Storage3 / Journal6。ClientBridge 原根对象记录只保留已知 module，丢失 setter 及后续实际 Scope 的 unknown；已知停组来源混有未知来源时仍可能关闭整个根。现于原 SOURCES 记录内合并模块与不可回退的 unknown，真实 setter 入口、槽位写入和 Scope 继续传递；私有退役在关闭回调前与写入前复核实际单例、客户端线程、准确根及独占来源。普通合法关闭和原停止效果策略保留，未扩展自身外观或武器注册。

完整包集中构建 `build-c-java65-native61-screen-sources-20261014.log` PASS25秒。未新增夹具、状态计数或证明文件，未重跑旧矩阵；真实客户端混合来源及重入关闭场景尚未执行，不能记为实机通过。

原 Java63/Native61 run03 继续运行，12:03:14 的一次真实线程现场仍在 Forge 方块注册烘焙，main RUNNABLE、CPU 继续增加；未到世界加载或冻结保存结果，也不能计作 Java65 通过。没有重启或停止该实例。Native 与驱动未改、未重建，沿用未签名 SYS 37888字节和管理器190464字节，未加载。

本地候选 `distribution/candidate-c-java65-native61-screen-wip-20261014`，Core JAR 2418477字节、prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Screen-WIP-Java65-Native61-20261014.zip`。旧包与失败日志保留，完整 C/B 继续收口。

---

## 2026-10-14：C 接续——重载 tick 的原键保留

接续 `9c1e5c6`，Java ABI64 / Native ABI61 / Storage3 / Journal6。仅修正 WorldControl.defer 的顺序：准确 level-wide 调度器已有同类型身份、同位置 held 时，先取消重载副本；没有已有 held 才检查冻结规则并新增。原 held 在准确恢复前继续占有其逻辑键，避免解除冻结后，区块 NBT 副本先执行、END 阶段又恢复原 tick 的重复窗口。新建、标脏及原 tick 恢复入口保持原有职责，没有增加目录或持久格式。

本包集中构建 `build-c-java64-native61-held-reload-20261014.log` PASS25秒。未伪造 ServerLevel、producer 或控制权限，未新增测试块、证明文件或重跑旧矩阵；命中“真实 reload 晚于 END 恢复、早于下次 poll”的实际时序案例尚未执行，不能宣称该竞态实机通过。

此前 Java63/Native61 的 run03 仍由原进程继续启动，未因源码更新重启。最新真实线程现场已推进至 Forge GameData.freezeData 的方块状态烘焙，主线程 RUNNABLE；Forge 自身更新检查另报 HTTP 超时警告。尚未到世界加载或冻结保存结果，不能把此运行算作 Java64 或完整 C/B 通过。

本地候选 `distribution/candidate-c-java64-native61-held-reload-wip-20261014`，Core JAR 2416835字节、prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Held-Reload-WIP-Java64-Native61-20261014.zip`。Native 与驱动本轮未改、未重建；沿用未签名 SYS 37888字节和管理器190464字节，未加载。自身外观、武器注册继续暂缓，完整 C/B 继续收口。

---

## 2026-10-14：C 接续——Unsafe 原表安装与真实冻结保存场景

接续 `5017751`，Java ABI63 / Native ABI61 / Storage3 / Journal6。自身外观和武器注册继续暂缓，完整 C/B 尚未完成。

真实新空白 Forge 启动发现 Native60 在私有 Unsafe 原表安装时，把 JVM 的实际注册标成 unknown；原目标和原生帧均为 `jdk.internal.misc.Unsafe.registerNatives`，逐项来源检查拒绝，HotSpot 在 `unsafe.cpp:976` fatal。两次失败与原始 hs_err 保留在 `.work/c-world-held-save62-20261014-01/server/` 和 `-02/server/`，未进入 Forge 或世界。

本包把私有安装限定为当前控制器的真实 initialize0 帧、准确 bootstrap Unsafe 与原 registrar、单次调用深度，以及真实 VM 内的原表、字符串和全表函数地址。来源取实际 Unsafe 所属 module，method/fn 配对快照贯穿 MethodBind；外部 unknown、stopped、原目标和 Java 注册守卫保留。真实 RegisterNatives 结果原样交回 HotSpot；后置绑定捕获/seed 在私有安装返回前处理，失败按普通安装失败报告。

既有 WorldFixture 增加独立 world-held-save 分支：仅新空白世界，使用真实 force-loaded 区块、已安装 region freeze 命令和 block/fluid 调度器，等待实际 poll/defer，再检查实际 ChunkSerializer、普通保存及正常退出后关闭存储的磁盘 NBT；不直接调用控制 setter 或伪造 ServerLevel/producer。

统一构建：`build-c-java62-native60-world-held-live-20261014.log` PASS14秒；定位实际拒绝的诊断构建 `build-c-java62-native60-unsafe-diagnostic-20261014.log` PASS15秒；完整修复包 `build-c-java63-native61-unsafe-startup-20261014.log` PASS29秒。未重跑旧 Native/thread/heap/客户端矩阵，驱动及管理器未改、未重建、未加载。

本次实际运行 `.work/c-world-held-save63-20261014-03/server/` 使用新空白世界、6G 堆和原120秒 watchdog，已报告 NATIVE_BIND_AND_JNI_FIELD_READY、NATIVE_THREAD_CURRENT_BINDINGS_READY，进入 Forge 并完成 Forge 初始化；文件绑定初始仍报告74 pending，不作全覆盖声明。当前仍在加载世界之前的模组阶段，冻结保存案例尚未取得结果。此前数据 codec PASS 不代替本次真实存档结果。

本地候选 `distribution/candidate-c-java63-native61-startup-wip-20261014`，Core JAR 2416869字节，prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Startup-WIP-Java63-Native61-20261014.zip`。沿用未签名 SYS 37888字节及管理器190464字节；旧包、失败日志及运行目录保留本地。同步本包源码与简要报告，实际游戏结果后续接续同一实例。

---

## 2026-10-14：C 强度接续——HUD 未知份额、发送者来源与时停 tick 保存

接续 `3eb5c5b`，Java ABI62 / Native ABI60 / Storage3 / Journal6。自身外观与武器注册继续暂缓；本包修复的是目标 Mod 绘制/通信及实际世界 tick 数据路径，完整 C/B 尚未完成。

- HUD：真实 overlay register、Named 发布、后续来源刷新与绘制 Scope 贯穿 unknown，保留已知模块且保护新来源数组本体。未知条目不按独占移除；实际 list/map 清理在锁内重新核对来源和活动 scope，已知 module 对应且仍在真实容器的未知条目返回现有 pending 状态。原 stoppedEffects 策略保留，unknown 不提供放行身份。
- 通信：真实 Netty WriteTask.init 返回时，在 CHANNELS 锁外捕获 ControlBridge.invokingSources，合入 currentSources 与 packetSources 的任务快照；发送共享/其他模块消息也保留实际发送者来源，跨线程消费及 retirement pending 可接续。准确 task/context/message/promise 关系、recycle、原拒绝时的一次消息释放和 tryFailure 不变。
- 世界：ChunkSerializer.write 的实际 getTicksForSerialization 处合入当前 LevelChunk/level、对应 block/fluid 调度器和 chunk 坐标的 held 快照。保存只包装 SerializableTickContainer.save 的 NBT 结果，原容器只调用一次，不提前 resume、不消费 held；按真实类型身份+位置保留旧 held，替换同键新 pending，其他类型与 NBT 深拷贝保留，使用同一 now/registry 和 vanilla SavedTick 的优先级、相对 delay 格式。首次暂存仅把 getChunkNow 已加载的真实区块标记待保存，不加载区块。实际 serializer+服务器线程准入保留，closing 阶段仍可保存 held。

实际结果：集中主包 `build-c-java62-native60-client-world-sources-20261014.log` PASS 20秒；既有 AuditFixCheck 增量编译 PASS，`compile-c-scope62-world-save-20261014.log`。新增 world-held-save 分支 `c-world-held-save62-20261014.log` 实际退出0：使用真实 LevelChunkTicks/ScheduledTick/NBT、vanilla SavedTick.loadTick 读回，同键旧 held 替代新 pending、同位置不同类型保留、原优先级不变；now100/125 两次保存的已到期 held 分别保留 -10/-35 delay，原调度容器、tick 引用/时间/优先级/order 和 deferred 列表未改或消费。此检查仅数据序列化往返，无伪 ServerLevel/producer/控制权限，不代替真实区块存档。

HUD 实际停组清理、真实 producer 排队→停组→EventLoop 拒绝，以及真实区块保存/卸载/重启仍 NOTRUN，未声明通过。未重跑旧 Native/thread/heap/网络/客户端矩阵，没有启动游戏或安装驱动；Native/驱动代码未改，控制 DLL 沿用 Native60，未签名 SYS 37888字节与管理器190464字节沿用此前实际产物。旧原生/映射并发复用及未观察入口等边界仍按原范围保留。

本地候选 `distribution/candidate-c-java62-native60-scope-wip-20261014`，Core JAR 2415016字节，prelaunch 22440字节；匹配包 `distribution/Ronova-Pro-C-Scope-WIP-Java62-Native60-20261014.zip`。旧包和日志保留，同步源码与简要结果到 Git，产物留本地，强度收口目标继续执行。

---

## 2026-10-14：C 强度接续——原生释放与取消映射防拆

接续 `ba89fbb`，Java ABI61 / Native ABI60 / Storage3 / Journal6。自身外观和武器注册继续暂缓，目标 Mod 的绘制/UI 等强度路径继续；完整 C/B 尚未完成。

- VirtualFree、VirtualFreeEx、UnmapViewOfFile、UnmapViewOfFileEx 已观察 IAT/GPA 接入实际当前来源、停止准入与准确控制范围。MEM_RELEASE 及零长度 MEM_DECOMMIT 按真实完整 allocation，Unmap 按真实完整 view 检查；从 AllocationBase 逐属性段推进，不把首个 RegionSize 当整块。非零 decommit 按整页，placeholder 的非零 split/coalesce 请求保留原 flags 并检查完整显式范围；查询缺失、矛盾或锁忙保持未知并拒绝，不建新的分配器目录。
- FreeEx 沿既有 SAME_ACCESS 私有 alias 和 CompareObjectHandles 固定真实进程对象，确认当前进程后用 VirtualQuery，不额外索要 QUERY 权限；真实原 API 仍用相同权限的对象、原地址/长度/flags，仅调用一次，保留 BOOL 和即时 LastError。受限回调保持 Try 与原来源守卫。自有未发布表/跳板的失败清理改走私有原 VirtualFree，没有赋予外部回调通用 bypass。

实际结果：集中主包 `build-c-java61-native60-host-release-20261014.log` PASS 27秒；既有夹具增量编译 PASS，`compile-c-host-release60-20261014.log`。独立 release-memory phase 实际 JVM 退出0，`c-host-release60-20261014.log`：四入口真实 IAT/GPA 正常调用通过；自有页 decommit 后为 MEM_RESERVE，同址重新 commit 后零读/写正常，release 后为 MEM_FREE；FreeEx 的仅 VM_OPERATION 当前进程真实句柄正常。新临时文件的四个真实 view 在 Unmap/Ex 的 IAT/GPA 正常解除，Ex 的0及 transient-boost=1原 flags 通过，映射/文件句柄关闭、临时文件删除。真实 VM shadow 的零长度整块 decommit 和 release、真实 GPA thunk 的整块 release/整页 decommit，以及准确控制 DLL 的 Unmap/Ex 均返回 BOOL0/5，原 mapping、字节与权限保留；释放后只查询页面，不读已释放字节。

没有重跑旧写入/权限、thread、heap、定义等矩阵，也没有伪造 Mod producer。真实目标 Mod stopped/共享来源、受限回调并发和 placeholder 实机效果仍 NOTRUN；公共 Win32 查询与释放之间没有原子 allocation 身份 pin，映射并发替换仍未闭合。CPU stores、原始系统调用、未观察入口/旧缓存、未登记 JNI/特殊 VM IAT 等旧范围仍保留；本候选游戏、保存重启和内核运行未执行。驱动/管理器本轮未改、未重建，沿用此前未签名 SYS 37888字节及管理器190464字节，未安装/加载。

本地候选 `distribution/candidate-c-java61-native60-release-wip-20261014`，Core JAR 2410813字节，prelaunch 22440字节。匹配源码/产物包 `distribution/Ronova-Pro-C-Release-WIP-Java61-Native60-20261014.zip`；旧候选和日志保留。同步当前源码和简要实际结果到 Git，二进制留本地，强度收口目标继续执行。

---

## 2026-10-14：C 强度接续——Windows 原生内存防拆入口

接续 `96faf3e`，Java ABI60 / Native ABI59 / Storage3 / Journal6。自身外观继续暂缓，武器不注册，目标 Mod 的渲染/UI 控制仍在强度范围；完整 C/B 尚未收口。

- 已观察映像的 WriteProcessMemory、VirtualProtect、VirtualProtectEx 真实 IAT 与 GetProcAddress 返回接入当前来源和停止准入。进程入口用 SAME_ACCESS 私有 DuplicateHandle 固定真实对象，CompareObjectHandles 比较当前进程，不依赖 PID 查询权限；原句柄并发关闭/复用不能重定向这次调用。获准底层只调用一次，保留其他参数、BOOL、即时 LastError 与真实输出，末尾只归还本方副本。
- 当前进程目标按准确闭区间检查；权限变更按实际整页范围检查。本地 SIZE_T/DWORD 输出地址即便目标进程不同也先检查，拒绝不写输出。保护范围来自真实控制 DLL pin、已发布 Binding、VM/JVMTI shadow、callback 版本/跳板/控制元数据及来源链、Host 的实际 published import/load/release 记录和 live IAT、当前同步 heap walking；不扫描通用 malloc 或猜测 VM 私有布局。不同目录分锁；受限回调使用 Try，忙/关闭保持未知，已 disposed 环境和已释放 IAT 槽不沿历史地址保护。
- 自有控制表/跳板安装改走私有原 API，未给外部回调新增 bypass 资格。线程列表的 caller results 在任何实际线程动作前检查；原内存恢复仅在目标范围明确无重叠时走原 WPM，读回确认实际结果，忙/拒绝仍待处理。DLL 受限回调跳过会阻塞的 delay-slot 提升，保留实际返回入口并沿原当前来源守卫处理。

实际结果：首次主包构建 PASS 31秒，`build-c-java60-native59-host-memory-20261014.log`；收尾修正受限回调后最终主包 PASS 16秒，`build-c-java60-native59-host-memory-final-20261014.log`，最终候选取后者。既有夹具增量编译 PASS，`compile-c-host-memory59-20261014.log`。新增独立 memory phase 实际 JVM 退出0，`c-host-memory59-20261014.log`：真实 IAT/GPA、当前进程 pseudo handle 与仅 VM_OPERATION/VM_WRITE/VM_READ 的真实句柄；自有 VirtualAlloc 写入/读回和 RW/RO 恢复通过；准确控制代码、VM GetEnv shadow 槽与真实 hook 跳板同值写均返回 BOOL0/5，written sentinel 保留；控制页及 shadow 结构外同页尾权限修改均拒绝，三类本地输出别名拒绝且 scratch 状态保持。没有改写控制指令或伪造 producer/权限。

真实目标 Mod 停止组、共享来源、受限 DLL/heap 回调竞争及新线程 results 拒绝的实机效果仍 NOTRUN；独立夹具不能替代这些结果。CPU 直接存储、原始系统调用、未观察原生入口/旧缓存、未观察代码主动关闭本方私有句柄、特殊 VM IAT/JNI 实表未登记范围仍未闭合。未重跑旧矩阵；本候选游戏与保存重启未运行，旧74项 file binding pending 不因此算通过。驱动/管理器本轮未改、未重建，沿用 Native58 EA 包的未签名 SYS 37888字节和此前管理器190464字节，未签名/安装/加载。

本地候选 `distribution/candidate-c-java60-native59-memory-wip-20261014`，Core JAR 2409945字节，prelaunch 22440字节。源码与匹配产物包为 `distribution/Ronova-Pro-C-Memory-WIP-Java60-Native59-20261014.zip`，旧包保留；同步源码和简要实际结果到 Git，二进制留本地。强度收口目标继续执行。

---

## 2026-10-14：C 强度接续——JNI 发布、异常来源、FreeLibrary 防拆与 EA 写拦截

接续 `fa4e380`，Java ABI59 / Native ABI58 / Storage3 / Journal6。用户已明确暂停 Ronova 自身外观美化、暂不注册武器；目标 Mod 的渲染/UI 控制仍按强度功能继续。本工作包四条具体漏控链已写齐并集中构建，C/B 整体仍未完成，强度收口目标保持执行。

- JNI 防拆：SetJNIFunctionTable 校验后提交同一份私有 incoming 快照，消除再读 caller 可变表的竞态窗口；未启用 guarded table 时继续原调用。局部快照可以按原合同提交，VM 会复制表，见 [JVMTI SetJNIFunctionTable](https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#SetJNIFunctionTable)。没有放宽调用者、环境或受保护 slot 条件。
- 异常来源：原异常表指向专用 trampoline，再调用既有 executionCaught 并跳入原 handler。普通 CFG 跳入共享 handler Label 时保留真实来源栈；catch 顺序/type/原范围、业务 ordinal、构造规则和准确调用位置保留。沿原非构造 synthetic cleanup 覆盖观察器异常，没有另建通用异常体系。
- DLL 防拆与停组：普通 FreeLibrary 的已观察导入/动态入口现在经过完整来源和真实当前作用域准入；heap/受限 callback 和已停来源按原条件处理。准确已捕获 control DLL HMODULE 拒绝外部释放，不按路径/名字或屏蔽 resource tags 判断。获准动作只调用原 API 一次，保留原 HMODULE/BOOL/即时 LastError；release frame 强来源与 borrower 沿既有 daemon 清理，自有 pin 走独立实际 FreeLibrary。hostState[26] 只纳入真实未释放的来源 hold，不推断 app 引用或最终 unmap。
- Windows 文件防护：注册实际 IRP_MJ_SET_EA，将 FILE_WRITE_EA 新打开准入和准确 SET_EA 效果纳入已有 DENY_WRITE，沿真实 actor/FileObject/文件 ID 拒绝。未读写 EA 缓冲、未扩大协议或替代未知旧别名/section/kernel/paging 条件，见 [微软 SET_EA 操作合同](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/flt-parameters-for-irp-mj-set-ea)。

实际结果：主包 `build-c-java59-native58-strength-20261014.log` PASS 28秒。改动驱动官方 WDK/SDK+Zig/LLD 仅 SYS 编译链接 PASS，`build-c-driver58-ea-20261014.log`，SYS 37888字节；未重建未改的管理器，未正式 MSBuild、签名、安装/加载。既有夹具增量编译通过，`c-strength58-jni-release-20261014.log` 独立 JVM 实际退出0：真实当前 JNI 表提交被接受、之后 caller 副本变更不影响 VM、非法 DefineClass slot 被拒且表不变、真实 Dispose；四类 LoadLibrary 原结果/193错误与 resource tag1/2 正常释放；FreeLibrary 实际 import/GPA 成功 BOOL1、原 LastError 标记保留，NULL 返回 BOOL0/126，准确控制 DLL 在两入口均返回 BOOL0/5。每次成功仅归还实际申请的应用引用，拒绝目标没有补做 FreeLibrary。没有声称做过并发竞态实战或旧 VM 表屏障。

真实 Mod 的 stopped/cache/共享来源与 DLL detach 后继退役仍 NOTRUN；新异常来源路径缺少真实 source-bearing 现有独立夹具，未伪造 producer/权限；EA 内核运行、当前游戏与保存重启亦 NOTRUN。未重跑旧定义/heap/thread/Host 全矩阵。FreeLibraryAndExitThread 非返回入口、预观察 DllMain/TLS、原 VM 缓存/裸原生写者、应用引用归属与最终 unmap，以及旧内核映射等缺口仍明确保留。

本地候选 `distribution/candidate-c-java59-native58-strength-wip-20261014`，Core JAR 2406766字节，附匹配 prelaunch 和本次未签名 SYS，管理器/INF/协议沿用未改内容。包 `distribution/Ronova-Pro-C-Strength-WIP-Java59-Native58-20261014.zip` 含有效源码和匹配产物，不带旧布局、缓存、游戏存档或原始日志；旧包保留。源码与简要实际结果同步 Git，二进制留本地。

---

## 2026-10-14：C 接续——子 DLL、Screen 控件、双端时停与网络失败结算

接续 `4f7c180`，Java ABI58 / Native ABI57 / Storage3 / Journal6；GNU C23/C++23、Java17，独立驱动和管理工具 C17。本工作包源码已写齐并集中构建；完整 C/B 仍未收口。

- 子 DLL：在真实 LoadLibrary 调用前保存完整强来源，真实成功返回登记准确 parent/HMODULE，贯穿子映像 imports、GetProcAddress、JNI Binding 与原生 worker。B 的真实当前作用域及选中的缓存动态入口来源优先；共享/unknown/opaque 保持未决。资源句柄按实际低位标签处理，不以 flags 猜测可执行映像。真实 Windows API 公共载体保留加载来源并归还临时观察引用，不把共享系统 IAT 接管为私有 child。
- 原生退役：沿既有 daemon 等待实际调用和 borrower 退出，关闭准确子映像准入后只归还 Ronova 自有 pin、强来源和借用关系。没有消费应用的 loader 引用；自有引用释放与应用引用寿命/最终 unmap 分开报告。Native hostState 32项与 Java 两处消费同步。
- 客户端：Screen 的三个注册入口和 Forge addEventWidget 接入真实 List.add 前守卫、正常/异常尾及每个实际容器中的 widget 关系。render/input/focus/narration 贯穿条目来源与 unknown，focused/lastNarratable 实际发布受控；只在准确 Minecraft 线程按身份清除全部来源已停的已观察条目、焦点/拖动/旁白引用，部分清理按真实剩余条目接续。共享或未知条目保留。union 后的新来源数组本体进入原控制对象保护，避免 varargs 只登记元素而漏掉数组。
- 双端时停：冻结根实体或中间乘客仍沿原 tickPassenger 处理未冻结的下层乘客；乘客守卫位于原 removed/vehicle/Player/ticking 条件之后，保留原无效骑乘解除和组外行为。
- 网络：三处 Netty outbound 拒绝保留一次消息释放，以专用取消原因 tryFailure 结算原 promise，保留共享 flush。准确 Connection.exceptionCaught 只消费这个私有原因，其他取消和真实 I/O 错误继续原处理；没有新增 future 台账、关连接或中断线程。

实际结果：首次完整构建 `build-c-java58-native57-child-ui-cached-20261014.log` PASS 32秒；随后修正来源数组保护，最终匹配载荷 `build-c-java58-native57-child-ui-final-20261014.log` PASS 21秒。首次日志保留，没有把编译提示写成失败。既有模块夹具编译通过，`c-host-module57-child-load-20261014.log` 实际退出0：四类 API 各真实导入/动态入口的原 HMODULE、flags、有效 LastError 与无效图像193错误保持；DATAFILE 实际 tag1/flags2、IMAGE_RESOURCE 实际 tag2/flags32，均正常 FreeLibrary，临时输入删除成功。

这次夹具没有真实 Mod producer 或新可执行子 DLL，因此停止组/共享 B/worker 的子来源退役仍 NOTRUN；Screen、双端时停和网络拒绝效果尚无本候选实机结果。没有重跑旧定义/heap/thread/Host 全矩阵，没有启动游戏或安装驱动。驱动源码未改，本地包沿用上一包已编译的未签名驱动，未宣称新的内核运行结果。

本地候选 `distribution/candidate-c-java58-native57-wip-20261014`，Core JAR 2404958字节，附匹配 prelaunch 和原 unsigned-driver。源码与匹配产物包为 `distribution/Ronova-Pro-C-WIP-Java58-Native57-20261014.zip`；有效源码继续仅在 src/<组件>，不带缓存/旧布局/游戏存档/原始日志，旧包保留。源码和简要结果同步 Git，产物留本地。初次 DllMain/TLS、缓存原 VM 表、裸原生/未观察 loader、应用引用与最终 unmap、未知呈现载体及既有内核映射仍有边界；本候选完整游戏与保存重启未取得通过，旧结果保持其原候选范围。

---

## 2026-10-14：C 接续——定义通知、模块加载、Toast／FSCTL 与目录收口

接续 `2bc5f7f`，当前 Java ABI57 / Native ABI56 / Storage3 / Journal6；GNU C23/C++23、Java17，独立驱动和管理工具 C17。本包源码写齐后集中构建；C/B 整体仍未完成。

- JVMTI：真实 RedefineClasses/RetransformClasses 成功后，沿实际 Class[] 与原成功 scope 接入已有定义更新队列；失败不通知。合法 JNI 预先取得的强引用保留到接续接受，通知失败由既有 daemon 重试，失败操作和部分引用准确清理。ThreadEnd/非法 JNI 上下文不伪造通知，原返回、异常和 LastError 保留；此入口不授予一般控制写者资格。
- 模块：已观察原生映像的 LoadLibraryA/W/ExA/ExW 导入、延迟消费及真实 GetProcAddress 结果接入准确来源准入，停止来源在实际 loader 调用前拒绝，原参数、标志、HMODULE 和失败返回保留。未登记子 DLL 不冒充 JNI library，预观察加载窗口和旧缓存指针仍未控。
- 客户端：真实 Toast 提交、queued/visible、实例与占位退役已接线；在实际 Minecraft 所属线程维护，原来源与 unknown 贯穿回调和派生资源，共享/未知保留未决。没有扩大纯呈现 UUID 接口。
- Windows 驱动：按实际 actor/文件身份接 ODX、EFS、置零、trim、块克隆及列明的元数据修改 FSCTL；ODX 对准确拒绝目标使用 NOT_SUPPORTED，让正常复制回落原 I/O。新包 INF 的 SupportedFeatures=3 与管理器固定包读取同步；source 无法解析、并发句柄复用和旧 offload token 保留边界，协议结构仍为1。

目录已收口：有效源码只有 `src/{core,agent,bootstrap,prelaunch,journal,resources}`。旧根目录 agent/bootstrap/prelaunch、src/main/test/fixture/stateChecks、validation/src 及输入补丁保存在 `.work/archive/source-layout-20261014/`；原完整 README 保存在 docs/history。Java 包名和现有构建 sourceSets 没有改变，历史候选、失败和存档未删除。当前打包入口为 distribution/package-current.ps1，只取有效源码和指定候选。

实际结果：主包构建 PASS 20秒，`build-c-java57-native56-admission-cached-20261014.log`；最初旧 PowerShell 将编译提示误作异常而退出1，保留 `build-c-java57-native56-admission-ps5-wrapper-failed-20261014.log` 及本节失败说明，随后用原正常环境构建。驱动/管理器官方 WDK/SDK+Zig/LLD 编译链接 PASS，`build-c-driver56-fsctl-20261014.log`，SYS 37888字节、EXE 190464字节；最初旧 PowerShell 包装同样被 stderr 提示截断，退出1，不计通过。未运行正式 VS/MSBuild、签名、CAT、altitude 配置或安装/加载。

现有夹具新 admission 分支实际退出0，`c-host-module56-admission-20261014.log`：四个实际导入和四个真实动态入口，原 HMODULE、SEARCH_SYSTEM32、有效调用原 LastError、空图像的193错误、实际 FreeLibrary与临时文件删除通过；真实初始方法9→重转换7→重定义11，非法 count 保留103且方法仍11，真实 Dispose 通过。未重跑旧 ClassUnload/heap/thread/Host 矩阵。独立 JVM 无实际 Mod producer：停止组加载拒绝、共享 Mod caller 的定义传播与通知失败补收均 NOTRUN；Toast与内核 I/O只有源码和编译结果，未取得该候选实机客户端/内核运行。

本地候选 `distribution/candidate-c-java57-native56-wip-20261014`，Core JAR 2380374字节，附匹配 prelaunch 与 unsigned-driver。整理包 `distribution/Ronova-Pro-C-WIP-Java57-Native56-20261014.zip` 含有效源码和本次产物，不带旧布局、缓存、存档、旧候选或原始运行日志。源码和简要结果同步 Git，产物留本地。缓存原表/旧环境/内部未观察写者、loader 首窗口、现有映射/section、未知呈现 carrier 及本候选游戏/保存重启仍未收口；此前结果保留原范围。

---

## 2026-10-14：C 接续——原生 worker 来源与真实退出持有

接续 `c3b1db1`，当前 Java ABI 56 / Native ABI 55 / Storage ABI 3 / Journal 6；GNU C23 / GNU C++23、Java17，独立驱动/部署工具仍为 C17。该完整工作包写齐后集中构建，C/B 整体尚未完成。

已确认 producer 的本地 CreateThread、_beginthread、_beginthreadex 使用三个准确 start adapter，原 userdata、调用约定、stack/flags/thread-id 和真实返回值保留。创建前完整准备来源、持久 C scope 和实际 start DLL pin；正常 JNI 保存全量真实强来源，分离后的后继只借完整覆盖其实际来源的父 holder。Heap callback 只借自己实际 published anchor 的完整强来源及已经 pin 的同一映像，使用正常准备时捕获的范围；不调用 JNI/GetEnv/loader，也不从 tag、weak Module 或名称授予资格。借用结束前保留原 pin/global refs，准确 OS 退出或实际创建失败后才在既有 daemon 归还。共享环境的原创建者和当前独立回调来源分开，不因旧 profiler/其他创建来源误拒当前已确认回调。

真实 worker 在 start 入口复制当前 kernel thread handle，Win32/ex 暂停创建另保留准确返回对象的私有 witness；_beginthread 的自动关闭返回句柄不被消费。处理真实 start 早于创建调用返回的发布竞争，实际 OS signaled 后才释放 DLL/来源。JNI ThreadEnd 只幂等收尾原线程的 Java 附件，C callback、worker、heap anchor 与临时 DLL scope 继续持有 TLS；真实 Attach 可开启新周期，Detach 返回后仍保留原生来源。多个环境的 ThreadEnd 不重复减同一 binding，在正常 daemon 不用自己的 JNIEnv 收死线程的局部引用。未 Detach、未返回 callback 或不准确 scope 保持未决。当前来源登记在 worker 上继续使用原实际 library registration，避免共享 HMODULE 将 A 误混成 B；缓存 GPA 同样保留真实 unknown，不把缺失来源变成私有权限。Heap 内 Attach/Detach/GetEnv 沿既有禁止 JNI 的范围拒绝。

回收仍用原 JVMTI daemon；DLL 回调后重新取得该 daemon 的真实当前环境，必要时只重新附着本方 daemon，外部 heap 调用者被分离则把剩余 global refs 交原 anchor。私有监视句柄的 CloseHandle 与字段更新同一目录锁内完成，避免编号复用误拦无关对象。HostState 只追加实际 worker hold/exit/gap 三项，Core 退役不能在这些实际持有未结时返回完成。该实现不强杀、暂停或替用户恢复原生循环；仍在执行的旧栈保持未结。

实际结果：整包构建 PASS 26秒，`build-c-java56-native55-worker-cached-20261014.log`。原夹具同一 DLL：`phase=thread-end` 实际退出0，`c-host-thread55-thread-end-20261014.log`；UCRT 原 start/userdata、真实 Attach=0、同步 ThreadEnd 回调、Detach=0、GetEnv=-2且NULL、同一 OS 线程继续执行、回调创建与分离后的两个暂停后继在外层恢复/真实退出61/关闭通过。`phase=heap-only` 实际退出0，`c-heap55-lifetime-20261014.log`；现代/旧式原 userdata、tag/referrer 写回、无关对象保留、tag/storage 还原及真实 Dispose 通过，没有重跑 ClassUnload 或旧 Host 矩阵。53/54 的原记录及失败日志保留在后续历史节。

该独立 JVM 无真实 Mod producer 登记：producer sourceLease/start DLL pin/借用/发布竞争/真实退出租约与外层其它 callback 中 Detach 均明确 NOTRUN，不把普通 profiler 结果写成这些已通过。代码可完整持有的来源才包装；不同来源/不可 pin start/未观察入口/远程线程仍准确报告边界。驱动源码/工具未改，沿用本轮 36352 字节 SYS 与 188928 字节 EXE 的同源码官方 WDK/SDK+Zig/LLD编译结果；未执行签名、CAT、真实 altitude 配置、安装/加载。缓存原表、旧未观察 JVMTI 环境、内部 raw native 写者、DllMain/TLS 首窗口、既有句柄/映射与其他呈现 adapter 等仍存在源码边界；本候选游戏/世界与保存重启仍未验收。

本地 WIP `distribution/candidate-c-java56-native55-wip-20261014`，源码和简要结果同步 Git，产物/依赖/原始日志留本地。

---
## 2026-10-14：C 接续——扩展/堆回调、线程创建与驱动准入

接续 `591c71c`，当前 Java ABI 56 / Native ABI 54 / Storage ABI 3 / Journal 6；用户态 GNU C23 / GNU C++23、Java17，独立 Windows 驱动及部署工具 C17。两个完整工作包分别写齐后集中构建，C/B 整体仍未完成。

JVMTI：按真实 GetExtensionEvents 元数据接入受支持扩展事件，保留原环境、JNIEnv、参数和独立回调来源；替换/清空/关闭沿用在途计数和实际退役队列。同步 heap 六入口、现代与旧式九种回调保持原 userdata、tag_ptr/referrer_ptr 与控制结果。producer 写入只在正常上下文准备已观察真实 jobject 的唯一 tag 身份和当前对象来源资格，来源/控制角色变化会撤销准备中的权限；tag 值本身不授予资格，未标记、重复 tag 或无法重新确认的新 tag 写入仍保留明确未决。callback 内不进入 JNI/Java 或阻塞目录锁；可在实际环境调用 callback-safe SetEnvironmentLocalStorage 并在返回后准确读回。

Host：已观察映像的真实导入、延迟槽及新 GetProcAddress 返回接入 CreateThread、CreateRemoteThread、CreateRemoteThreadEx、_beginthread、_beginthreadex。每次消费重新核对当前映像/库/来源，停止来源拒绝新建；完整参数、真实返回和 Windows LastError 保留。UCRT/MSVCRT 分别保存自己的实际入口和 errno/doserrno 访问器，不用一个 canonical 指针替代另一运行库。heap callback 只读已观察原生来源，锁忙明确返回 ERROR_BUSY，不调用 GetEnv/Winloader。该包完成创建准入，原生 worker 创建后的来源传播、应用 DLL pin、租约与真实退出结清仍待接续；不能把调用返回写成线程已退役。

Windows 驱动：部署工具按真实 token 为明确修改命令请求正常 UAC，查询保持普通权限；只转交自身真实 EXE 和已解析参数，等待实际子进程结果。manifest 资源已纳入 MSVC/Zig 入口。准确 actor/volume/file ID 的新 open 在句柄产生前按实际读/写/追加/执行权限拒绝；MAXIMUM_ALLOWED 只使用完成的具体 grant，无法解析或句柄已产生返回准确 OPEN_ADMISSION_UNAVAILABLE gap。现存 handle/view、继承、paging/kernel I/O、完整映像准入仍未服务。官方 WDK/SDK NuGet 10.0.26100.6584 + Zig/LLD 实际生成 36352 字节 SYS 和 188928 字节管理 EXE；正式 MSBuild、CAT/签名、altitude 配置、安装/加载和 UAC 实机动作未执行。

实际结果：56/53 主包/既有夹具构建 PASS 70秒（`build-c-java56-native53-dispatch-cached-20261013.log`），56/54 整包 PASS 40秒（`build-c-java56-native54-thread-cached-20261014.log`）。53 实际 heap/ClassUnload 检查退出0（`c-jvmti-dispatch53-follow-fixed-20261013.log`），正确的额外 FollowReferences 边观察导致的夹具误判已修正，原失败和诊断日志保留；生产资格未放宽。ClassUnload 初次/替换回调实际执行，清空后的当前观察不是公开队列已排空的证明。54 实际线程入口检查退出0（`c-host-thread54-20261014.log`）：Windows 三入口在当前进程内创建、暂停/恢复、真实退出码、userdata/ThreadID 与关闭；实际选中 ucrtbase 的两 CRT private 动态入口、errno/doserrno 和各自句柄寿命；tagged heap callback 创建后在 walk 返回外恢复/等待。驱动/工具编译链接 PASS（`build-c-driver53-dispatch-20261013.log`），没有重复构建未改的内核源码。

这些独立 JVM 没有真实目标 Mod producer 登记，不代表整组停用、游戏/世界或保存重启通过。缓存原表/旧未观察环境、内部未观察写者、DllMain/TLS 首窗口、未观察入口/转交和其他呈现 adapter 仍是明确源码边界；驱动部署与本候选游戏运行另有外部缺口。本地 WIP 为 `distribution/candidate-c-java56-native54-wip-20261014`；源码和简要状态同步 Git，产物、依赖和原始日志留本地。

---
## 2026-10-13：C 接续——回调退役、世界接续与可编译驱动

接续 `8948f3b`，当前 Java ABI 55 / Native ABI 52 / Storage ABI 3 / Journal 6；用户态 GNU C23 / GNU C++23、Java17，独立 Windows 驱动与部署工具 C17。本轮工作包代码写齐并统一构建；不把源码或构建写成完整 C/B 已通过。

JVMTI：真实回调与已包裹操作计入同一在途生命周期，关闭后拒绝新进入，在途 Dispose 返回实际 NOT_AVAILABLE；最后回调退出后由正常 daemon 工作线程归还映像 pin/来源租约。只有全部来源已停且无未拦截扩展生命周期的实际环境自动 Dispose；共享/未知环境保留。危险线程动作再核对真实帧与方法位置，方法/ConstMethod/常量池版本在真实交接前保留；实际 Native 写者的 null=unknown 贯通指令与 Java 来源。未知/非 Mod 回调改动控制载体也回到原控制图像与 Task/Object 守卫，普通组外图像不全量重入；新增守卫的过早登记错误已在实际启动检查后修正。

字段与世界：恢复请求固定原槽值，已经完成的槽不反复覆盖，后来合法变化保留；继承/接口静态字段完整读取，但未显式选择的共享声明类只保留引用，输出 PARTIAL。世界工作持久保存当前原基线 `.apply.nbt`、锁定真实生成条件 `generation.nbt` 和生成结果 `.seed.nbt`，实体只在准确原世界/区域/身份与 NBT 基线上恢复，跨区/跨维度/换身不会按 UUID 拉回。实际 normal chunk/entity/player writer 保存及 flush 后才推进区块游标；重启仍未验收。HUD 策略绑定原 LerpingBossEvent 身份，原 UUID 策略不阻止后来 ADD 创建的新载体；发送来源在实际 channel 写入前登记，仍以当前 scoped 客户端缺席回复判断结果。

Windows：源内补正确 kernel access 常量，Flt 配置/注册/启动失败返回原状态并回滚。集中 WDK 工程与显式安装/查询/加载/非强制卸载/升级/移除工具已写齐；只管理准确 RonovaHost 包，安装不自动加载，活跃会话拒绝卸载，原权限和系统失败保留。使用[微软提供的 WDK/SDK NuGet](https://learn.microsoft.com/en-us/windows-hardware/drivers/install-the-wdk-using-nuget) 10.0.26100.6584 头库及现有 Zig/LLD，实际编译链接出 35840 字节 `RonovaHost.sys` 与 182784 字节 `RonovaHostManage.exe`；这是源码编译结果，正式 VS/WDK MSBuild 工程未执行。未生成 CAT/证书、未签名、未安装或加载，模板两 altitude 仍待真实配置。

实际结果：最终主包/既有夹具 build PASS 18秒，`build-c-java55-native52-agent-order-fixed-20261013.log`；原 Native 前置声明失败与 premain 登记顺序失败日志保留。既有实际 Host/JVMTI 入口复查退出0，`c-host-jvmti-native52-fixed-20261013.log`（创建/句柄、GetEnv、回调替换/关闭/Dispose、JNI表保护）；独立 JVM 无真实目标 Mod 登记，不算整组退役或游戏结果。内核编译/链接记录 `build-c-driver52-wdk-rights-20261013.log`，部署工具修正后 PASS `build-c-driver52-manager-fixed-20261013.log`，没有执行部署命令。HUD/世界历史与重启尚无本候选实际结果；可变参数扩展事件、同步堆 producer dispatch、缓存原表/旧环境、未观察内部写者、既有映射/句柄等仍是明确后端缺口，C 整体未完成。旧53/50 run5不计入本候选菜单/世界；最后记录仍为资源/字体与网络信息。本地 WIP 在 `distribution/candidate-c-java55-native52-wip-20261013`，其 unsigned-driver 产物不能视作已部署的驱动包。源码与简要状态同步 Git，产物、依赖和原始日志留本地。

---

## 2026-10-13：C 接续——54/51 用户态接线与自编 Windows 驱动源码

当前主源 Java ABI 54 / Native ABI 51 / Storage ABI 3 / Journal 6；用户态仍为 GNU C23 / GNU C++23、Java 17，新增内核项目使用 WDK C17。尚未取得最新 54/51 游戏收口，C 整体与 B 仍未完成。

已接线：真实 JVMTI GetEnv 环境、独立回调来源与退役、最终定义守卫；Host 实际创建 API、进程/线程句柄身份及 Job 处置；CPU/GPU 停止来源份额和原存储租约、音频原执行器及实际 OpenAL 删除；纯 HUD 原 ID/players 与连接/scope 缺席回复（`r3-client-7`）。缓存原表、旧环境、内部调度屏障及未观察的原生写入仍有缺口。

用户已选择自行编写 Windows 内核驱动；`native/driver/` 的 5 个文件及用户态 `native/host_kernel.c` 已实际集成，按保留的真实对象句柄接线，不给共享游戏 JVM 设置按 Mod 猜测的规则。包含选定进程/线程新句柄权限削减、子进程创建拒绝和实际文件对象规则；旧句柄、继承、映射/section、paging/kernel I/O 及完整映像准入未服务。本机无 WDK/MSVC，内核驱动未编译、加载或签名；真实签名与 minifilter altitude 待部署。

本轮统一构建：PASS，39 秒，`build-c-java54-native51-driver-source-fixed-20261013.log`（Java/用户态 Native 主包与既有夹具，未包含 WDK 驱动编译）；直接相关检查：Native51 真实 Host/JVMTI 入口 PASS、退出 0，`c-host-jvmti-native51-20261013.log`；实际创建、句柄操作、GetEnv/回调替换/关闭/Dispose 与 JNI 表入口保护通过，该独立 JVM 未登记真实目标 Mod，不代表整组游戏退役或内核运行通过。此前 53/50 Host/JVMTI 公开入口检查实际 PASS；WithTokenW 返回 1314、WithLogonW 返回 87 是准确失败，未算创建成功。旧检查及原失败记录保留，未据此提升 54/51 游戏结果。

最新客户端结果：旧 53/50 客户端 20:41:48 到实际 unifont 字体加载，PID 63500 仍在运行；没有最新 54/51 菜单或世界结果。run5（PID 63500）使用旧 53/50 第一构建，仍在资源加载阶段，尚无菜单或世界结果。HUD/UI、音频、CPU/GPU 与目标组游戏处置尚无最新 54/51 收口；候选目标目录为 `distribution/candidate-c-java54-native51-wip-20261013`（WIP）。

---

## 2026-10-13：C 接续——HUD 实际注册条目与图像 SRG 兼容

接续已同步的 `01548b5`；Java ABI 52 / Native ABI 49 / Storage ABI 3 / Journal 6，GNU C23 / GNU C++23、Java 17。当前工作包代码已写齐并统一构建通过。完整 C、B 的实际游戏收口仍未完成。

HUD 接入真实 Forge 47.4.23 GuiOverlayManager.init 与 RegisterGuiOverlaysEvent 的实际 event/map/order、private register 完成点和 ForgeGui 的准确 Named 回调。来源绑定到实际注册条目，不按命名空间推断归属，也不向共享 callback 全局回填注册者来源。主线程维护只退役全部来源已停止的已观察条目，保留其他 Named 身份及顺序，同步更新实际 ImmutableList/ImmutableMap；重新核对当前代次与两个实际字段，部分发布按当前身份继续处理。旧迭代中的已退役 Named 仍经过原作用域拒绝。当前待处理数量来自实际两个容器的 Named 身份并核对 key/callback；读失败明确为 unknown。混合来源条目保留，但既有停止效果规则仍可能拒绝其绘制，没有改变旧效果策略。纯呈现 UUID 接口仍为 UNSUPPORTED。

第三次 Java51 客户端完成 DataFixer、进入 Minecraft Render thread 和 LWJGL 后端；于15:42:45生成 Initializing game 崩溃报告，原因是 ClientBridge 使用官方字段pixels读取实际SRG NativeImage。原 `.work/c-client-20261013-03/launch.log`、两份实际线程栈与 `crash-reports/crash-2026-10-13_15.42.45-client.txt` 保留；没有菜单结果。修复按精确类和字段类型接入 NativeImage.pixels/size 与同一资源桥的 DynamicTexture.pixels 的准确SRG别名，并对齐真实getter/setter、关闭读回、解绑与原cleanup字段；未用类型搜索猜字段。

统一构建 `build-c-java52-overlay-registry-20261013.log` 37秒 PASS。复用 AuditFixCheck 的 `native-image-srg` 实际检查退出0：真实SRG类两个公开构造，各2×2图像、准确非零原指针/16字节、公开SRG像素写读四色，以及原close后实际指针归零；日志 `c-native-image-srg-java52-native49-20261013.log`。DynamicTexture要求真实RenderSystem/GL，检查明确NOTRUN。HUD登记/退役/共享来源效果只完成源码接线与构建，尚无本候选实机结果；现有夹具与目标同模块，停组后不能充当独立存活观察者，没有伪造单例、来源或临时控制资格。没有重跑已通过字体/Host/网络矩阵。

第四次隔离客户端 `.work/c-client-20261013-04`（实际 PID 80596）已用本包启动，正在加载；不把前三次或旧WORLD_LOADED计入本候选菜单/世界结果。

本地候选：`distribution/candidate-c-java52-native49-wip-20261013/ronova-pro-core-0.1.0-stage-b-dev.jar`。源码、直接相关既有检查与简要结果同步Git；候选及原始日志/崩溃文件留在本地，所有旧候选保留。详情见 下方原记录。

---

## 2026-10-13：C 接续——字体扩容后的真实切片

接续已同步的 `4fc9e49`；Java ABI 51 / Native ABI 49 / Storage ABI 3 / Journal 6，GNU C23 / GNU C++23、Java 17。当前切片启动修复工作包已写齐并统一构建通过；完整 C 和 B 的游戏收口仍未完成。

第二次客户端已实际完成 GLFW 初始化、创建 OpenGL 4.6 窗口，随后于14:25:13在 `lwjgl_stb.dll` 的 stbtt_InitFont 读取0x4而退出。真实原因是 STBHelper 读取202764字节 Monocraft.ttf，经200000→300000扩容后调用 MemoryUtil.memSlice；独立 slice 工厂的字段初始化此前未接入，返回零地址视图。原 `.work/c-client-20261013-02/launch.log` 与 `hs_err_pid78896.log` 保留；不是菜单或世界通过。

Agent 保留真实 LWJGL 3.3.1 ByteBuffer slice 原代码及父引用/字节序语义。新入口绑定实际 allocateInstance 结果、原 source 和实际登记视图/存储，限准确方法、类、加载器、模块、线程及字段和值，额外只允许实际 att 字段写入原 attachment-or-source。退出核对真实关联，JDK order 的提前观察复用同一存储；没有新建原存储、Cleaner、native绑定或释放许可。原 wrap 协议继续保留；ABI 51防止混用旧Agent/Bootstrap/Core。

统一构建 `build-c-java51-buffer-font-20261013.log` 59秒 PASS。共用初始化代码改变，因此直接复查 buffer-wrap；七类真实视图/零容量/双向内容/外来字段改写拒绝/原释放/UTF16模块查询退出0。新增 buffer-font 退出0：实际 STBHelper 完成字体扩容、切片和原资源全量内容读回，真实 Buffer/MemoryStack/STBTTFontinfo 与其实际容器地址非零且一致，最后 public stbtt_InitFont 成功。两项日志为 `c-buffer-wrap-java51-native49-20261013.log`、`c-buffer-font-java51-native49-20261013.log`。没有重跑旧Host/网络/传输矩阵。

第三次客户端 `.work/c-client-20261013-03`（实际 PID 52272）用本包启动后，完成DataFixer、进入Minecraft Render thread/LWJGL后端；随后于15:42:45的初始化崩溃中发现真实SRG pixels查找错误，具体后续修复见上节。没有菜单结果，不沿用旧候选 WORLD_LOADED。

本地候选：`distribution/candidate-c-java51-native49-wip-20261013/ronova-pro-core-0.1.0-stage-b-dev.jar`。本轮同步源码和摘要；候选、崩溃文件及原运行日志继续留在本地，Java50与更早候选保留。详情见 下方原记录。

---

## 2026-10-13：C 接续——真实 LWJGL 缓冲区初始化

接续已同步的 `36cd18a`；Java ABI 50 / Native ABI 49 / Storage ABI 3 / Journal 6。GNU C23 / GNU C++23、Java 17 保持。当前启动修复工作包已写齐，主包与既有夹具统一构建通过；完整 C 和 B 的游戏收口仍未完成。

真实 LWJGL 3.3.1 `MemoryUtil.wrap` 在 Unsafe.allocateInstance 返回后，按原字段布局初始化新 Buffer；Agent 绑定这次实际分配结果、准确 Class/模块/加载器/线程及四个字段和值。标量写入仅在原 wrap 内允许 address、mark=-1、limit、capacity；异常和正常出口结束作用域。来源、停组、字段策略与批量写保护继续生效。初始化完成只记录借用视图，不取得原存储/Cleaner/释放权限，原生来源和清理缺口继续保留。

集中构建 `build-c-java50-buffer-wrap-fixed-20261013.log` 39 秒 PASS。复用 AuditFixCheck 的实际 `buffer-wrap` 检查退出 0：七类真实 public MemoryUtil 视图（含零容量）、准确地址/标记/边界、实际双向读写、旧视图元数据改写拒绝、原 raw 分配释放，以及 WinBase.GetModuleHandle 的 UTF16 路径全部通过。首次构建的冗余 checked-exception 捕获错误已修复，原日志保留。没有重跑此前已通过的 Host/网络/传输检查，其原结果仍只代表当时候选。

第二次隔离客户端 `.work/c-client-20261013-02` 使用本包于14:22:57启动，实际 PID 78896。14:24:13线程读取显示 main 在 Log4j 注解/代理类加载中运行；随后 GLFW/GL4.6成功，但14:25:13于字体STB初始化空地址崩溃，具体后续修复见上节。没有菜单结果。第一次 GLFW remaining=0 的失败及原 launch.log 保留；这次对应底层路径的直接检查已经通过。后续菜单/UI停组、合法 sealed 目标与世界保存重启仍待实际结果。

本地候选：`distribution/candidate-c-java50-native49-wip-20261013/ronova-pro-core-0.1.0-stage-b-dev.jar`；源码与摘要同步，主包和原始日志留在本地，Java49/Native49及其他旧候选保留。详情见 下方原记录。

---

## 2026-10-13：C 接续——JNI_OnLoad 前接入、接收描述符与存量界面

本工作包基线 `f527867`；Java ABI 49 / Native ABI 49 / Storage ABI 3 / Journal 6。Host 接入真实 JDK OS 加载返回与卸载导入；Native.accept 接入双描述符；客户端增加存量 Screen/Overlay 真实生命周期。没有修改旧调用者资格、分配/释放许可或未知/共享来源规则。

Host 只信任实际 NativeLibraryScope、真实 JVM_LoadLibrary 返回的 HMODULE 及实际函数地址匹配的导入槽。当前作用域的一次性 OS 返回关联防止回调重用加载导出替换外层身份。失败 JNI_OnLoad 的 JDK 内部卸载提前使最后映像失活，libraryEnd 兜底不读已卸载 IAT；仍有实际共享登记则保留映像。就绪查询检查 load/unload 两个实际槽，ABI 49 拒绝混用旧控制 DLL。没有实现 DllMain/TLS 首窗口、未观察句柄转交或任意 Native/JVMTI 控制。

| 实际检查 | 结果与范围 |
| --- | --- |
| 主包、Agent、Bootstrap、控制 DLL 与既有夹具 | PASS；`build-c-native49-ui-accept-onload-fixed-20261013.log`，22 秒 |
| 真正 System.load / JNI_OnLoad | PASS；`c-host-load-native49-20261013.log`；A/W 普通导入与动态入口均在真实 OnLoad 内受控并实际创建/读回子进程；真实嵌套加载；JNI_ERR 卸载；原 IllegalStateException 保留；失败映像缓存入口由独立支持 DLL 再调用时拒绝；无关存活映像仍可调用 |
| 实际 Net.accept | PASS；`c-socket-accept-native49-20261013.log`；已关闭监听 FD 拒绝且不发布输出，真实异常后作用域恢复；公共与直接 native 接入均用实际输出通道完成双向回环数据 |
| 共用 pair helper 的 FileChannel.transferTo0 | PASS；`c-file-transfer-native49-20261013.log`；实际数据/长度、只读/关闭端保留、异常作用域恢复及后续传输；仅因同一生产辅助方法变化而复查 |
| sealed 目标与 UI 停组组合 | NOT_RUN；两项 I/O 输出均明确 SCOPE_UNCHECKED，不把 OS 拒绝当作生产封口许可验收；当前真实菜单运行不具备合法停组组合场景 |

三项原生检查进程退出码均为 0，没有重复原 Host-process、旧写者/克隆或元数据矩阵。首次 C 编译失败是两处 load/unload 并列指针声明导致第二变量少一层指针，已拆成完整独立声明；`build-c-native49-ui-accept-onload-20261013.log` 保留。启动时仍有 74/75 项文件入口未决，不声称全平台覆盖。

客户端新截点同时支持实际官方/SRG 名称，校验真实单例及主线程，经原版 setter 关闭专属存量界面；提交前重新比对准确根，保留关闭回调重入发布的新合法界面。普通关闭失败加入既有 FAILURES，不逐帧重试已部分释放对象；按现有 ClientFaults 规则保留 fatal 原对象及上下文恢复。运行路径为本地 `.work/c-client-20261013`，只读既有 Forge 47.4.23 的库/assets，在独立 mods/options/payload/logs 中使用本候选。首次实际启动于13:09:35进入ModLauncher，13:09:42加载早期窗口，13:09:46在 GLFW/WindowsLibrary 初始化失败，13:09:48 main退出。具体异常为 `Number of remaining elements is 0, must be at least 2`，发生在 WinBase.GetModuleHandle 的 UTF16 参数检查，尚未调用该 Windows API，也没有到本轮 Minecraft/UI截点。真实LWJGL字节码核对显示 MemoryUtil.wrap 经 allocateInstance 后写 address/mark/limit/capacity 被既有资源元数据保护拒绝，视图保留默认零容量。正在补正确初始化路径，不关闭既有保护。原 launch.log 保留；仅一次 Thread.print 因进程已退出返回 no such process，保存在 thread-print-ui.txt，不是死锁结果。菜单、UI停组和游戏世界均未计 PASS。

本地候选目录为 `distribution/candidate-c-native49-wip-20261013`。本轮同步源码、直接相关既有检查与简要结果；不上传候选、线程栈或运行原始日志。

---

## 2026-10-13：C 接续——Host A/W、直接传输与客户端镜像

主工程仍为 `D:\桌面\ronova_GPT`；B-r10 ZIP 只作对照。Java ABI 49 / Native ABI 48 / Storage ABI 3 / Journal 6；GNU C23 / GNU C++23、Java 17。代码范围为 Host 的 CreateProcessA/W 接线、直接文件传输的双描述符作用域，以及客户端准确镜像清除与重新发布限制。完整 C 和 B 的游戏业务收口没有完成。

Host 的 A/W 调用保留各自真实参数类型，复用来源受理、暂停创建、Job、原始失败及退役流程；常规导入、已解析延迟导入和实际观察的动态解析都能分类 A/W。客户端 Terminal 仅接受当前连接/session/scope/维度/修订，清理核对当前 raw level 与 ID+UUID；同 ID 新 UUID 不被清除。当前真实 manager 有独立发布截点。相机和命中引用按准确目标退役，实际玩家根仍由原版换身/断开接续，缺席回复会计入这些活动根。

| 本轮实际结果 | 范围与本地日志 |
| --- | --- |
| Host 真实 Windows 子进程：PASS | `c-host-process-20261013.log`；A/W 导入与动态解析、暂停标志、停止来源/缓存入口拒绝、无关原入口保留、映像退役拒绝及句柄收回；使用隔离夹具登记的来源/映像，不代表游戏中的原生库发现已验收 |
| 实际 premain / Bootstrap / Native 48：PASS | `live-control-writer-native48-20261013.log`；实际安装、外来写者/原生读者拒绝、保护数组保持与原生到堆复制 |
| 整组合并构建：PASS | `build-c-native48-client-transfer-20261013.log`，主包与既有夹具 38 秒；描述符死锁修复后 `build-c-native48-descriptor-fixed-20261013.log`，主包 26 秒 |
| 实际文件传输：PASS | `c-file-transfer-native48-descriptor-fixed-20261013.log`，退出 0；公共/raw native 文件到回环 Socket 的实际字节/长度、只读目标原样、关闭源/目标拒绝、同 FD 不被写入、真实异常后文件作用域恢复与后续传输 |
| 客户端实机与合法 sealed 目标 | NOT_RUN；客户端仅完成源码/mapped 方法接线及编译，独立 JVM 没有合法退役写者封口场景，检查输出明确 `FILE_TRANSFER_SCOPE_UNCHECKED` |

实际失败保留：首轮 Host 夹具因编译器缓存安装前的 GetProcAddress 导入而未观察包装结果，修正夹具调用边界后通过；原 `c-host-process-initial-20261013.log` 保留。文件传输首轮在创建临时文件时死锁，`c-file-transfer-wait-stack-20261013.log` 的 JVM 确认 main 持 STREAMS 等内存写锁，external code continuation 持内存写锁等 STREAMS，循环经首次 lambda 链接触发。仅将同描述符生命周期的五个 computeIfAbsent 改成原锁内直接 get/new/put，保留 sealed、来源、活动计数和 Unsafe 检查；停止这一个隔离 JVM 后重建并重跑同一项，未延长期限或重复其他已通过项。原启动日志保存在 `c-file-transfer-deadlock-initial-20261013.log`。最初的构建脚本中文编码错误发生在进入编译前，已修正脚本，未改生产源码。

文件绑定在启动时仍报告 74/75 个未决入口，不能把 DLL 就绪当成全部入口受控。文件检查不代表内部封口策略、来源归属或整个平台覆盖已经验收；Host 检查不证明 JNI_OnLoad 首窗口、未观察的句柄转交/关闭或其他创建入口。纯绘制接口仍缺真实 owner 与资源接线，保持 UNSUPPORTED。客户端低层回填、活动引用清理、本地玩家交接及新候选的世界/保存重启尚无实机结果。

本地主包位于 `distribution/candidate-c-native48-wip-20261013/ronova-pro-core-0.1.0-stage-b-dev.jar`，由最终 26 秒重建生成。只同步源码和简要结果；新主包、线程栈与原始日志保留本地，不替换原 Native 47 候选。

---

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


B 仍未完成。工作候选为 `D:\桌面\MOD\.work\ronova-b-final-20261007\Ronova-Pro`；Java ABI 48、Native ABI 44、Journal 6 未改。远端基线 c565e97，未推送新的通过候选。

## 本轮生产代码

### 2026-10-09 启动热路径接续

- Java 字段/数组写入仍执行真实控制门；未观察载体且没有当前执行来源时不遍历历史调用栈。Native 显式贡献者路径保留。准确字段门的 AQS 身份由已开放字段读取，未换成碰撞条带锁。
- ASM opcode 读取缓存同树类型的 MethodHandle，仍调用实际虚方法。返回来源资格先取得当前真实类来源/种子，再沿根方法所需选择器反向展开；不把选择器可达性当所有权或完成证明。
- I/O 借用直接使用该入口/请求捕获的来源，不在 ResourceBridge 重新遍历完成线程。临时借用记录保留准确缓冲区身份，归还只释放同一身份，向量/请求普通借用保持到自己的完成点。删除单缓冲区部分释放接口及多余 remaining 计数，统一使用幂等的整张借用释放。原封存判断、共享未知标记和活动使用计数保留。

| 记录 | 实际结果 |
| --- | --- |
| `build-scoped-mutation-sources.log`、`control-dir-scoped-mutation.log` | 构建通过；真实安装后控制弱身份 clear/enqueue 和辅助反射写入被拒，无关数组正常。只代表这些准确截点 |
| `scoped-mutation-source-run.log` / 候选 23 | 原 1200 秒内未完成世界加载，无来源场景结果 |
| 候选 24、`startup-scoped-24-threads.txt` | 同候选诊断运行，采样后为修补停止。45 秒 JFR 主线程样本 589 个，其中首个 Ronova 栈帧：来源资格 101、opcode 读取 52、控制目录查询 32；这是定位样本，不是性能达标或 B 证明 |
| `build-reachable-source-query.log` / 候选 25 | 构建通过；反向图在无来源种子时仍展开导致启动回退，栈记录保留，停止修补 |
| `build-reachable-source-query-fixed.log` / 候选 26 | 修复先确认真实种子后构建通过；留存栈在 I/O 缓冲区归还。后续 PID 已不存在，运行日志只有父 PID，无退出或场景终结行，因此结果不完整，不能结清 |
| `build-io-source-lifetime.log` / 候选 27 | I/O 操作来源和借用生命周期收敛后完整构建通过（14 秒）；未加载世界，采样后按准确 JVM 身份停止修补 |

候选 27 的 45 秒 JFR 共有主线程 359 个、来源续接线程 2434 个执行样本；其中首个 Ronova 帧落在 `possibleContributors` 的分别为 82、1470 个。采样文件为隔离 server 内 `startup-27.jfr` / `startup-27.json`。Thread.print 的返回管道被拒，只留 PID，不能当作成功线程转储；相关两个挂起 jcmd 已按准确 PID 参数和工具路径清理。

据此将来源资格从反复全目录 `disjoint` 扫描改成同一不可变定义批次的选择器反向索引，队列只展开相关定义，每个定义/选择器最多一次。索引只复用不可变方法头，当前类来源和停用策略仍每次重新读取；真正的方法/接收者/VM 定义解析未改。`build-return-source-index.log` 构建通过（16 秒），候选 28 在加载世界前停止，以补齐新索引发布：构造用 TreeMap，发布为不可变 Map 并把实际 backing 纳入已有控制目录，避免新增未登记的可变树节点。

`build-return-source-index-final.log` 完整构建通过（16 秒）；候选 29 位于 `runtime/b-return-source-index-29/server`，驱动日志 `return-source-index-final-run.log` 只有父 PID，接续时实际父/子进程均已不存在，没有场景结果或退出结论。因此不能记为正常停服、超时已定位或通过。保留该未完成运行。

候选 29 的 JFR 留有主线程 175 个、来源续接线程 1035 个样本；其中 `possibleContributors` 分别出现在 75、711 个样本，最集中的是整批类去重、类→模块映射两张临时身份表（519/523/527 行）。据此把本批定义的准确位置直接用于类来源、种子与已访问标记，删除四张临时 IdentityHashMap。真实类绑定只捕获一次；空位代表尚未绑定，隐藏类仍跳过普通来源种子判定，普通未绑定定义仍按已记录来源/未知来源处理。来源桥按原位置返回，查询真实 ORIGINS 后才补物理 Module，不缓存来源或停用结论。

`build-return-source-slots.log` Core/Agent/Bootstrap/Native/Prelaunch/Fixture 完整构建通过（15 秒），ABI 未变。候选 30 在 Forge 初始化时停止；同类沙盒运行的 WMI/系统查询和 jcmd 返回管道存在明确拒绝，不能把这些当候选正常环境结果。31 使用同一产物、同一来源夹具与原等待时限，在普通进程环境运行；仍在独立 server 目录和 localhost，不触碰原游戏/存档。`return-source-slots-normal-run.log` 最终记录原 1200 秒启动超时，未取得 Done/场景结果；进程已按驱动清理退出。

31 的准确线程转储已成功，后续主线程在 Forge 等待 Mod 初始化，工作线程仍在重复来源预筛的选择器建表和堆缓冲区来源结算。JFR 记录在 `runtime/b-return-source-slots-31/server/startup-31.jfr`；主线程 279、续接线程 777 个样本。沙盒拒绝消除后仍超时，因此不把慢启动仅归因于环境。退出后才发起的 `source-slots-31-heap.txt` 查询失败，没有对象计数证据。

`build-java-heap-mutation-paths.log` 完整构建通过（22 秒）：Java Unsafe/句柄堆写入沿用已观察载体/当前执行的来源采集；无来源回执时仍持实际对象门并执行发布回调，省去 UnsafeMutation 线程目录登记。字节范围在无来源、无当前执行、无既有 Heap 时不查询不存在的基线布局。复制结束仍向统一 FieldMutation 传递读窗口贡献者；原始地址、显式 Native 和已有来源路径保留。`java-mutation-receipt-guards.log` 使用当前完整安装快照，私有回执反射与 Unsafe 写入被拒，退出 0；不代表游戏效果或完整 Native 路径通过。

随后删除 `possibleContributors` 及其独立选择器索引/valueCalls/整批类来源中转，统一由现有实际 initiated class、receiver contract、accepted VM body 的调用链解析产生来源结果。保留实际定义版本匹配、未知接收者与未绑定身体的原判定；去掉的预筛不提供这些事实。`build-direct-return-source-resolution.log` 完整构建通过（13 秒）。候选 33 仍未完成世界加载，取证后按准确 JVM 身份停止。

33 在约 228 秒的真实堆对象统计中有 5,706,398 个 ControlKey、一个约 64 MiB 的控制表、474,549 个来源 Scope；总活对象占约 960 MB。记录为 `direct-source-33-heap.txt`。对象统计不能区分所有 ControlKey 的 referent 是否存活；源码则明确每次整批登记只回收最多 64 条，可能跟不上大型图像登记，因此调整为每 64 个登记输入进行一批最多 64 条的既有准确队列退役，不进行每动作全表扫描。

准确内部控制容器若没有既有 holder/carrier、Heap 或当前执行，则保留实际写守卫，省去纯 JDK 集合的业务来源作用域；不授予 executionMetadata 角色。`build-controller-directory-lifetime.log` 完整构建通过（23 秒）；`controller-lifetime-guards.log` 实际安装后控制弱身份 clear/enqueue、辅助反射控制表写入被拒，无关数组正常，退出 0。34 在世界加载前停止，以合入本包最终修订。

实际定义目录观察到 producer/contributor 的事实单调置位；尚无该事实且当前图像没有贡献者时直接返回空来源。第一次真实绑定仍走 prepared/changed 的调用者刷新，置位后不因模块停用或对象回收而关闭解析。该事实不授予来源身份、不结清操作。`build-observed-return-sources.log` 完整构建通过（23 秒）；候选 35 最终在世界加载前退出 1，`observed-return-sources-run.log` 保留失败，未取得来源结果。

35 的后续线程转储 `observed-source-35-threads.txt` 显示主线程等待 Mod 初始化；实际工作线程在 SourceInterpreter 分析临时 HashSet/HashMap 时进入全局来源 MONITOR，来源续接线程也为内部 LinkedHashMap 建作用域。未见 Java 级死锁报告，但不能据此称启动正常。最终报 `LinkageError: attempted duplicate class definition for net.minecraftforge.event.entity.living.LivingChangeTargetEvent`，完整失败保留在该隔离 launch.log。

`build-analysis-container-sources.log` 完整构建通过（19 秒）：用显示隐藏帧的实际调用者扫描识别本方已准入 Agent 的分析集合；第一个非平台调用者必须为本方 Agent，外国/隐藏回调不能借更深的 Agent 帧取得豁免。仅省去编译器新集合出生及未登记 holder/carrier 的普通/并发集合观察；真实字段、索引、数组控制继续执行。36 是仅构建的源码修订，没有单独启动或 PASS。

同时找到 ControlClassWriter 资源缺失时的主动 Class.forName 回退，以及控制台/fastutil 的重复 SafeWriter。该主动加载路径可能重入正在定义的类型，不能单凭最终错误断言唯一成因。最终统一这两个入口到 ControlClassWriter：只读实际已发起类型、准确资源或同一加载器已观察且声明一致的输入；缺失/歧义保持 MISSING_FRAME_TYPE，不用 Object 替代或主动加载。缓存输入声明仅供栈帧生成，不据此认定 VM 接管。`build-passive-frame-resolution.log` 完整构建通过（19 秒）；37 推进到 Forge Mod 初始化，未完成世界加载；取得实际卡点后按准确 JVM 身份停止。

37 的实际主线程转储 `passive-frame-37-threads.txt` 在 ClassInfo$Field 构造期间调用 `memberMetadata → mixinMetadata`，为每个字段重新序列化并分析整树。字段路径已改为实际 FieldNode 单项投影，再复用 fieldImage 的规范编码比较该 F 行；父成员身份、对应历史补丁和不匹配时的完整贡献者未决保持。完整 ClassInfo/方法说明仍由原入口处理，不以字段输入替代它们。`build-selected-field-metadata.log` 完整构建通过（16 秒）。38 在 Mixin 准备阶段因 `ACTUAL_ASM_LIBRARY_UNOBSERVED:org.objectweb.asm.tree.FieldNode` 退出 1，失败保留；不能声称该首版投影接入完成。

字段身份继续来自实际观察过的父 ClassNode 和相同 ASM tree Module，不为未单独织入边界的 FieldNode 新造注册流程；仍要求真实 accept 声明者及同父库模块。`build-selected-field-library-role.log` 完整构建通过（15 秒）。39 已通过该 Mixin 准备卡点进入 Forge 初始化，但没有 Done 或来源/任务/保存重启 PASS。

39 在约 763 秒的真实调用栈 `field-library-39-threads.txt` 中，来源线程在 Analyzer 临时 ArrayList 操作反复进入 AnalysisCaller；Mod 工作线程在栈帧类型资源查询中逐字节读取 JImage，反复结束没有登记分配的原始内存回执。取得现场后按准确 java 路径和隔离目录停止本次 JVM，没有把主动停止写成正常停服或超时通过。

本包将内部集合角色放回已有准确控制弱身份：只在真实构造入口核对显示隐藏帧的第一个实际应用调用者，后续操作用该准确身份读取角色。角色不授予写权限，仍受控制写守卫保护，外国回调不能借更深的 Agent 身份修改它；注册为真实 holder/carrier 后仍走原业务观察。独立 Unsafe 原始读在 Native 入口只查当前登记分配的准确范围，无登记时省去没有分配、读记录或结果消费者的空回执。已登记分配继续建立真实租约，执行读、外层缓冲区/复制观察、全部写入不省略。

`build-compiler-birth-raw-read.log` 首次被沙盒 Zig 自身路径阻断；`build-compiler-birth-raw-read-normal.log` 普通进程构建暴露 C 声明顺序错误。修正准确内存查询的前置声明后，`build-compiler-birth-raw-read-final.log` 完整构建通过（19 秒），Native DLL 摘要 F2DE1DE34D22165B8A15A5B923B06E2C864C13376C881FF45B7569C06538A21E。ABI/日志格式不变。40 运行同一既有来源场景，日志 `compiler-birth-raw-read-source-run.log`；尚无世界/来源组合结果。

40 的单次 35 秒 JFR 记录为 `runtime/b-compiler-birth-raw-read-40/server/startup-40.jfr`。主线程 380 个样本，来源接续线程 56 个样本；实际 OpcodeReader 路径包含 `memberMetadata → mixinMetadata → validateMetadataLayout`，方法构造仍反复核对整树，不能据字段投影已经接入就宣布整项收敛。取得该现场后停止准确隔离 JVM，没有 Done 或来源组合结果。

最终字段与方法共用 `memberImage`，实际方法保留真实父类头/版本再序列化；只核对该方法的指令/异常处理器布局与相应 M 行的说明及帧，完整 ClassInfo 类树观察保持在原入口。历史行不匹配仍返回原完整贡献者未决，不把输入投影当 VM 接管；来源行的编码复用同一 `methodMetadataRow`，没有另造缓存或来源证明。`build-selected-member-metadata.log` 完整构建通过（16 秒）。41 使用同一既有来源场景运行，日志 `selected-member-metadata-source-run.log`；当前尚无世界/来源组合结果。

41 在约 243 秒的实际调用栈 `selected-member-41-threads.txt` 中，主线程通过 `snapshot → ArrayList.add → listMutation/deny → controlMutationAllowed` 反复查当前写者，尚未加载世界。取得现场后停止准确隔离 JVM。快照构造、复制、身份重建共用一个真实指令数组提取；不修改外部 InsnList 的缓存数组，保持实际 opcode 查询及完整布局对齐。快照用固定 Instruction/Handler 数组直接发布不可变列表，删除逐条临时列表与复制；顺序、身份、标签和处理器目标不变，发布图像仍按原入口保护。

`build-direct-instruction-snapshots.log` 完整构建通过（15 秒）。42 使用同一既有来源场景运行，日志 `direct-instruction-snapshots-source-run.log`；当前尚无世界/来源组合结果。没有扩大启动/场景等待时间，没有运行第三方样本或已通过的旧检查。

42 最终在原 1200 秒启动时限超时，驱动完整失败保存在 `direct-instruction-snapshots-source-run.log`；没有 Done 或游戏内结果。约 1201 秒的 `direct-snapshot-42-threads.txt` 中，实际编译器 HashMap 的新节点仍走 `SourceMapBridge.node → Scope/Gate`，来源线程同一 Map 键/值发布重复判断控制写者。不能仅凭移除快照临时列表宣布启动问题已解决。

准确编译器新节点现直接登记到已有控制弱身份，仍拒绝节点本身的非法字段/元素写入；未建立业务来源门。父 Map 后续注册真实 holder/carrier 时恢复准确出生记录，并在原 ready() 绑定实际存活节点。键/值发布只判断一次当前写者，两次实时元素策略、短路顺序和原回调作用域保留。`build-analysis-nodes-publication.log` 完整构建通过（26 秒），ABI/Native/Journaling 不变。43 通过 Agent 安装与 Mixin 准备，未得到世界或来源结果；按用户要求先打包，结束准确隔离 JVM，交付当前 WIP，未据此宣称正常停服或 B 通过。首次停服超时、R8 及原来源归属未决保留。

以下各节保留此前实现与失败过程；其中“当前/最新”按当时记录理解，最新结论以本节和唯一状态页为准。

- 一个 Group 保存操作/别名/固定返回合同/模块和真实记录 ACK；成员变化才发布不可变 targets。查询不再反向推进来源任务，当前主体/代次只在 ProRuntime 推进一次。
- RecoveryStorage 按实际主体、代次、来源与保存代次索引匹配工作。主工作记录、实际快照身份、真实提交和未决状态保持。
- SourceMapBridge 私有出生索引改为弱身份链，去除 AVL Bucket/旋转/平衡；保持真实同门写入与退役。首轮遗留 Bucket 预加载导致安装失败，已删准确旧预加载项，失败日志保留。
- 未被来源观测且没有当前执行来源的数组/字段保留 UNKNOWN，不为普通对象初始化创建没有可用基线的 Heap 记录。真实字段/数组门和当前策略照常执行；已有来源、记录或活动执行仍走完整路径。
- ClientBridge/NetworkBridge 的控制成员接入已有 CodeSource 控制身份登记，减少字段守卫跨入客户端/网络业务锁。已开放容器字段直接读取实际 backing，不反复做 Native 字段布局查询；无直接访问时仍保留准确 Native 读取。
- ClientItemFaults 合入 ClientDrawFaults；六种返回风格统一固定类型合同；同输入 ASM draft 和同版本日志历史复用。没有声称 GL 捕获、全部字节往返或所有来源工作层已经删除。
- 主 Core JAR 包含前置启动入口；已失败安装不再重复附加同一 Agent。隔离启动父进程和 Forge 使用同一已复制的安装候选，避免正在运行的父进程锁住可变构建输出。免填 JVM Agent 参数不等于单放 mods 就拥有启动前控制。
- 已观察指令重排、异常处理器重排/改写/删除具有来源传播和准确排列还原；后来重叠修改、移动范围内未知插入继续未决。

## 集中检查与实际运行

| 记录 | 实际范围/结果 |
| --- | --- |
| `build-consolidated-b.log`、`build-consolidated-install.log` | 统一架构及准确安装名单修补构建通过 |
| `build-untracked-array.log`、`build-unified-controls.log`、`build-control-field-read.log` | 对应生产修补构建通过 |
| `build-untracked-field.log` | 运行中的早期父进程持有构建输出，reobfJar 文件占用失败；不算通过 |
| `build-untracked-field-fixed.log` | 停止准确本轮隔离 JVM 后，完整 Core/Agent/Bootstrap/Native/Prelaunch/Fixture 构建通过 |
| `reorder-flow-compile.log` | 首次检查类路径错误，编译失败；保留 |
| `reorder-flow-compile-fixed.log`、`replay-only-current.log` | 使用完整实际 Agent 输出；实际生成字节码的重排还原、保留邻居/后来编辑检查通过 |
| `reorder-source-gap-current.log`、`handler-source-gap-current.log` | 给定已观察 producer 的算法输入，真实生产来源传播和实际发出 ASM 行为通过；不证明 Mod 发现、Forge 或 Native 控制 |
| 来源候选 16 | 世界曾加载，但旧 Bucket 预加载导致 Agent 未有效安装；来源命令随后失败，不能算有效攻防或 B 通过 |
| 来源候选 17 | 已安装，卡在普通数组写入/StackWalker；为修补而停止，未加载世界 |
| 来源候选 18 | 已安装，启动推进到 Forge 初始化，后在客户端控制锁/类定义处超时；没有场景结果 |
| 来源候选 19 | 已安装，栈落在整图保护的 Native 容器字段读取；按准确 JVM 身份停止，未加载世界 |
| 来源候选 20 | 已安装，栈落在普通字段写入建立 Heap/控制 Map；按准确 JVM 身份停止，未加载世界 |
| 来源候选 21 | 原 1200 秒内未加载世界；线程栈仍有普通数组写入/来源回调的重复工作，超时失败保留 |
| 来源候选 22 | 原 1200 秒内未加载世界，启动栈在普通库数组写入的 frameSources/StackWalker；失败及退出保留 |

原始运行日志、线程栈、失败和已退出信息均保留在本目录及对应隔离运行目录。未运行第三方样本，没有修改原游戏/存档，没有使用子代理。

## 后续准确控制登记修补

TaskBridge 的平行 ControlRef 成员数组/位图和每次重扫旧记录已删除。新增根对象及实际 backing 沿用唯一 CodeSource 弱身份登记；共享容器根只首次登记。CreationDefinition 的真实类弱引用仍保留。没有缩小真实字段/数组写门、拒绝策略或语义目录。

`build-inactive-holder-observer.log`、`build-shared-control-directory.log` 和最终 `build-auxiliary-writer-selection.log` 完整构建通过。来源 observer 未安装时的纯无效果回调不再走栈；存在真实 observer 后，认证和实际回调保持。

`control-directory-current.log` 真实安装后发现辅助写者未织入、反射 Array.set 改表成功，仍记 FAIL。该日志旧标签 JNI_STORE 过宽，实际动作是反射调用，不是直接自有 JNI DLL 写入。修补按实际反射/Unsafe 调用识别辅助写者，接入既有边界，不给它 Mod 所有权。`control-directory-current-fixed.log` 使用同一安装快照早期入口：弱条目 clear/enqueue、反射登记表写入被拒，邻居数组正常。只证明这个实际辅助写入截点；原始 Native 与未织入编译器写者不因此通过。

### 辅助写者筛选最终修订

仅按类中出现反射类型名筛选过宽，已改为读取实际 MethodInsn 调用：Array/Field 的写方法、Method.invoke 或 Unsafe 的写入/CAS/交换/复制。只读反射和 Array.newInstance 不因此进入全字段/数组织入。该选择只是写截点资格，不是来源归属或策略权限。

`build-actual-auxiliary-writer.log` 构建通过，但 `control-directory-final.log` 首次实装因 RecoveryAgent 匿名访问器提前链接未导出的内部 ASM 而 premain 失败，仍保留 FAIL。将访问器放入已有 BackingBoundary，确保实际建立包访问后才加载；最终 `build-actual-auxiliary-writer-fixed.log` 完整构建通过，`control-directory-final2.log` 真实安装后的辅助反射写入拒绝及邻居正常结果通过。

当前没有运行中的本轮服务器，也没有普通安装世界组合通过。后续仍应继续处理每次数组写入的广泛来源栈查询；不重新放宽原等待时限，不用算法/安装定点 PASS 代替 B 结果。

## 未完成范围

来源终结、排队/在途任务与资源、真实写盘读回和重启，仍需要同一普通安装候选的实际组合结果。首次停服超时、旧 R8 和两类既有来源归属未决没有结清。最新代码没有新的 A 双端组合结果，不以旧 A PASS 覆盖本轮变更。架构审查中的完整 RecoveryRecords/RecoveryReferences 工作层合并、其余非任务桥职责迁移与完整客户端组合仍未完成。

以下为原记录，按各自时间和候选范围保留。

---

# 2026-10-08 本轮接续与中断状态：B 仍未完成

## 后续 14/15 接续

- JDK17 私有 ASM 节点使用直接字段遍历，精确类型之外保留原读取路径。`image-field-check.log` 核对 33 种节点的全部 155 个原保护引用字段，结果通过；这不是 Native 或游戏效果证明。
- MemoryUse/ArrayChange/Region 不再重复注册短期实例，已有 ownState 类级 Java/反射/Unsafe/JNI 字段防护保持，子数组/容器/普通弱引用的登记保持。弱控制登记按每次发布最多 64 项退役；已清空弱身份不会匹配活对象，余项留在原队列。
- `build-private-image-walk.log`、`build-receipt-retirement-package.log` 完整构建通过。真实 Prelaunch 自动安装后，MemoryUse/ArrayChange 的反射和 Unsafe 写入被阻止，`receipt-guards-fixed.log` 通过。首次探针漏接 `IllegalAccessException: RONOVA_PROTECTED_FIELD_WRITE_REFUSED` 而退出，生产拒绝实际生效；失败日志保留。
- 来源候选 14/15 未加载世界，未执行实际 clear 或保存重启。14 栈在短期写入记录与控制弱引用退役，换用 15；15 栈在 SourceMapBridge 容器 birth/scope/balance/sweep。两轮不算 B 场景通过或自然停服通过；本轮隔离父/子进程按准确路径和命令行检查清理。

**仍未完成 B。** 下述中断/旧结果保留；没有第三方样本、原游戏/存档修改、子代理或远端推送。

## 已写入的当前源码

- 早于外部图建立排除原先已排除的准确 Agent 自身图像；实际逻辑 Module 的无来源判断不再把 Forge 层本身当成来源。
- 执行传播仅携带可停用生产者；框架/本方补丁仍完整保留在 History，用于后续覆盖和回放判定。方法签名的保守可达筛选忽略 owner/loader，包含旧图像和分派候选；正例仍进入原有当前 VM 身份、字节码和接收者解析。
- 零来源定义先保留受保护的语义字节、实际声明和保守常量池调用引用；真正需要绑定时再解析/保护语义图。图构造不持定义监视器，完成后只在短门内发布。已有直接来源的图继续完整保留。
- 四个私有弱身份索引改成无公开 Map 写入面的索引。写入/退役核对实际调用类，条目、弱键及初始/增长数组在发布前保护；真实弱身份与队列退役不变。
- 从未有可归属数组来源的 UNKNOWN 写入不复制整块前后图像，仍维护当前窗口、版本、读者和 UNKNOWN；来源标记单调，一旦出现归属历史继续保留后续写入/覆盖图像。晚到贡献或重叠仍未决，不由优化改成已知成功。

## 当前构建与实际运行

Java48/Native44/Journal6。`build-execution-seeds.log`、`build-source-relevance-array-package.log`、`build-lazy-image-index-package.log`、`build-index-backing-protection.log` 构建通过。第三次构建的 varargs 警告暴露增长数组被展开、未保护数组本身，已修正为准确数组根后重新构建；未使用漏保护候选启动。

仅主包 11 的实际栈在 Inflater 输出图像复制；来源候选 12 的实际栈在指令身份 Map 和零来源定义整图保护。两轮未加载世界，为换用针对性修补候选而中断，退出码不作为自然停服结果。来源候选 13 已完成 Agent 安装、进入游戏注册初始化，实际栈仍在 Mixin 结束后的 `ExternalCodeImages.snapshot/protect`。日志没有 `Done (`，没有来源场景结果。本轮被中断后检查实际父/子进程 271104/285988 均已退出，原工具会话已不存在；完整最终退出结果未取得。

**没有本轮来源终结、任务/资源退出、保存/提交读回或重启 PASS；B 继续未完成。** 当前剩余启动阻塞是整树保护，尚未写入正在考虑的 ASM 字段直接遍历优化。旧停服/R8 和两类来源归属未决不结清。未运行第三方样本、修改原游戏/存档、使用子代理或推送远端。

以下保留此前实际记录。

---

# 2026-10-08 B 批接续：仍未完成

以下是当前源码状态；后面的 2026-10-07 记录保留原范围。

## 本次已写与实际结果

- 跨类调用解析改为核对实际被调用的方法；完整类查询仍保留。Native 方法身份、访问标志、字节码、常量、异常处理器及当前版本检查继续执行。接续批次建立一次实际类引用/继承依赖反向索引，替代多轮全目录扫描，索引不跨批保留。
- 原始数组写入保留实际图像、来源范围和重叠历史，对尚未使用的元素延后分配 Slot。Heap 的私有元数据在构造结束、发布之前一次登记。首次误把 fieldWrites 列表当成 Map 的安装失败已修正，失败日志保留。
- Bootstrap 和 Agent 的必要 SHA-256 输入核对使用 Windows BCrypt，减少 Java SHA 内部逐字读取反复进入 Unsafe 守卫；算法和实际输入核对保留。空停用模块策略直接通过该项停用判断，实体/字段等保护仍由各自守卫执行。
- Core 首次实际使用存储后端时可从主包解包加载存储 DLL，保留显式路径支持。此分支已编译，未完成实际世界中的保存/提交结果检查。
- 现有来源、保存重启和任务队列夹具改为请求外部驱动通过真实服务器控制台执行命令，不恢复生产权限后门。夹具启动失败，场景尚未执行，不能认定通过。
- Java ABI 48、Native ABI 44、Journal 6。完整 Windows/JDK17/Zig 构建通过，见 `build-agent-digest-policy-package.log`；启动定点诊断候选构建通过，见 `build-startup-trace.log`。首次接续索引局部变量重名导致编译失败已修正，记录保留。
- 实际安装的 Bootstrap/Native SHA-256 对照（0、3、64、65537 字节）与 2048 个元素的数组写入/覆盖/邻居读数通过，见 `image-array-agent-digest.log`。不覆盖完整数组来源回放、并发修改或 B 的组合效果。一次误用旧 JAR 的检查已中断，不算本次证据。
- 来源候选 06、08 均在 20 分钟内未完成世界加载；07 为换用修补候选而中断。仅主包、不放夹具的 09 也未完成世界加载，后由诊断候选替换。仅主包 10 再次超时。没有 B 的来源终结、任务资源退出或保存重启 PASS。
- 10 的 JVM 内部启动栈分别落在 `codeMethodVersion0/bindMethod`、`ExternalReceiverFlow/ASM Analyzer`、`ExternalCodeImages.snapshot/protectControls`。这些说明主包在世界建立前仍承担跨类分析和整树保护开销；不是夹具独有的问题。外部 jcmd 读取受权限限制，挂起的本轮诊断进程已清理。
- 仅本轮隔离环境被操作；没有运行第三方样本、修改原游戏/存档或推送远端。中断后的本轮目标进程已检查清理。

## 原版源码对照

已实际读取 `D:\桌面\MOD\mod` 下的 RonovaAgent、RonovaTransformer、RonovaDominion、AutoAttach、RecoverySourceTracker、RecoveryTaskObserver 和组合根。

原版先按准确目标、JDK 任务合同和类字节中的相关引用筛选，再解析/变换；Agent 先装早期变换器，完整性巡检在 FMLLoadComplete 后启动，且只重变换目标集合。当前 Pro 的每次类加载则可能进入跨类展开与整树保护，这是下一工作包应处理的实际差异。

原版源码本次仅供实现对照，未运行它，不能宣称其当前版本游戏通过或强度优于 Pro。原版吞掉变换错误、固定容量以及重证明的旧结构不作为直接移植依据。B 仍未完成，原有两类来源归属、停服和旧 R8 未决继续保留。

---

# 2026-10-07 B 批收口候选：未完成

工作目录为 `D:\桌面\MOD\.work\ronova-b-final-20261007\Ronova-Pro`，保留 R7 构造期字段守卫及任务取消排空修复。远端 `Scorn522/Ronova_pro` 当前检出的基线是 `c565e975d78a3b32ba72f493c72c953d24698203`；本轮未提交或推送。

## 已改的生产路径

- `ExternalCodeImages`：回放旧指令补丁前检查后续指令编辑历史。后续补丁把指令改回旧补丁的应用值（ABA），或删除同一指令时，不能覆盖仍有效的后续编辑；无法安全回放的效果保留未决。
- `RecoveryTasks`：任务本地结算要求取消完成栈已排空且原取消事实已获得日志 ACK，避免将仍有等待者或待登记事实的任务标成已结算。
- `RecoveryStorage`：选取待处理写入时，运行中、已隔离或已被替代的项不消耗本轮接收份额；有界工作队列拒绝后下一 tick 重试。实际写盘、ACK 和新读回要求保留。
- `CodeSourceBridge`：三个尚未发布的私有字符串声明草稿改用 `TreeMap`，避免构造草稿时逐项进入受控 `LedgerMap` 写入守卫；发布前仍冻结并保护，实际受控元数据表不变。
- `TaskBridge`：实际 StackWalker 得到调用者后，对原先已允许的准确本方 Class 提前判断，避免每个内部数组/容器写入都先查询 nest host。外部调用者鉴权和模块条件保留。

Java ABI 48、Native ABI 42、Journal 6 不变。本輪未修改 Native 实现。

## 本轮执行结果

- Windows/JDK17、现有离线 Gradle 缓存和 Zig 0.13.0：完整 Core、Agent、Bootstrap、Native、Prelaunch、Fixture 构建通过；两次针对启动修补的后续构建通过。见 `build.log`、`build-startup-fix.log`、`build-caller-fix.log`。
- 指令历史定点夹具调用实际回放判断辅助方法，并执行生成字节码：ABA、后续删除均保留后续效果，通过。见 `instruction-history.log`。这不证明完整 Mod 组终结、来源归属或全路径回放。
- 隔离 Forge 47.4.23 启动只放本方 Core 和本方夹具，通过 Prelaunch 自动带入 Agent/Bootstrap/Native；未手填 JVM agent 参数，未运行第三方样本或修改原游戏/存档。
- 首次准备因旧隔离库目录缺失失败，记录保留。改用当前隔离运行库后，三次实际启动均在等待世界加载时超时，未出现世界加载完成标记。
- 第一轮实际启动的主线程在元数据草稿 `LedgerMap` 构造/守卫路径；第二轮在 `Mth` 静态数组写入的内部调用者查询；第三轮最新栈在 `NativeControl.codeVersion0 → ExternalCodeDefinitions.bind/expandGraph`。前两条据此修补，最新启动仍未解决。
- 三轮启动的超时日志和线程快照保留；本轮记录的隔离 Java 父/子进程均已停止，未终止其他 Java 进程。

## 尚未结清

1. 普通安装候选不能在本轮隔离环境中完成世界加载，实际攻防尚无法执行。启动超时是阻塞，不能以编译成功替代。
2. 本轮没有完成 B 要求的已确认来源剪断、全部后继/共享资源退出、写盘读回、重启及迁移组合场景。旧夹具的生产调用权限和历史负例假设需与当前源码对齐，不能沿用旧成绩。
3. 保留指令身份的排列变化、仅异常处理器改写的来源归属缺口尚未解决；既有停服超时及旧 R8 未决不结清。
4. 本轮指令历史检查只覆盖实际辅助判定和对应字节码，不认证完整组回放或完整 Native/Host 控制面。

**B 继续标记未完成。** `build/libs` 内的 JAR 仅为失败启动所使用的候选，不作为已通过的最终交付。


---

## 2026-10-08 整理前状态原文（历史快照）

原页位于 `docs/A-批验收状态.md`。下文保留原候选结论和失败范围；其中“当前”均指原记录时点。实时结论只看状态页。

# 2026-10-08 B 当前接续：源码已保存，仍未完成

最新接续已加入 JDK17 私有 ASM 图的直接字段遍历，保留未知类型原读取路径；155 个原有引用字段的保留检查通过。临时 MemoryUse/ArrayChange/Region 沿用已有类级字段防护，子数组/容器仍登记；弱控制条目按每次出版最多 64 项退役。最终 `build-receipt-retirement-package.log` 完整构建通过，真实安装后私有 MemoryUse/ArrayChange 的反射及 Unsafe 改写被阻止，见 `receipt-guards-fixed.log`。首次检查未接住准确的拒绝异常，记录保留，不是生产漏防。

14 和 15 均未取得世界加载完成或 B 场景结果。14 为换修补候选而停止；15 最新栈落在 SourceMapBridge 的容器 birth/scope/balance/sweep，仍有启动阻塞。最后候选保留失败记录并收掉本轮隔离进程，**B 继续未完成，没有新增来源终结/任务资源/保存重启 PASS**。

本轮统一写入启动阻塞修补：按可停用生产者传播执行来源，保留全部补丁历史；先用保守的方法签名可达关系筛选，再做原来的真实方法/接收者解析；零来源定义保留受保护字节和声明，需要实际绑定时才建立受保护指令图；私有指令身份索引保留准确弱身份、增长和写者控制，避开通用 Map 的重复守卫；无可归属历史的未知数组写入保留窗口、版本和 UNKNOWN，省掉无效图像复制，已有来源的数组继续保留完整历史。

Java48/Native44/Journal6 未改。完整 Core/Agent/Bootstrap/Native/Prelaunch/Fixture 构建通过，最终本轮构建为 `build-index-backing-protection.log`。首次编译暴露的 varargs 展开会漏掉新索引数组保护，已修正后重新构建；首次日志保留。**编译通过不代表上述攻防路径已通过。**

仅主包 11 和来源夹具 12 均未加载世界，按实际栈换用后续修补候选并保留退出记录。13 已完成 Agent 安装并进入游戏注册初始化，但日志没有世界加载完成标记；本轮被中断，父/子 JVM 已检查均退出，未取得完整场景/退出结果。没有执行真实清除、来源终结、任务/资源退出或保存重启组合。

**B 仍未完成，当前 JAR 不是已验收交付。** 剩余直接阻塞是 `ExternalCodeImages.snapshot/protect` 的整树保护开销；两类既有来源归属缺口、首次停服与旧 R8 未决保持。没有运行第三方样本、改原游戏/存档、使用子代理或推送远端。详见 [实际报告](../../validation/b-final-20261007/REPORT.md)。

以下保留之前的接续记录。

---

# 2026-10-08 B 接续：启动阻塞仍未解决

当前 Java48/Native44/Journal6。已补实际调用方法比对、每批来源依赖索引、原始数组槽位延后分配、私有 Heap 元数据批量登记、同算法 Native SHA-256 输入核对、空停用策略的直接判断及主包存储 DLL 按需加载；现有 B 夹具改走真实控制台。

完整构建及真实 Bootstrap/Native 摘要/数组写入定点检查通过。来源候选 06/08 和仅主包 10 均未在 20 分钟内加载世界，07/09 为换候选而中断；**B 未完成，也没有保存重启、任务资源退出或整组来源终结 PASS**。最新内部启动栈落在跨类方法比对、ASM 接收者分析和整树保护。

已按用户要求对照 `D:\桌面\MOD\mod` 原版源码：它先筛选处理范围，早期装真实守卫，完整性巡检在 FMLLoadComplete 后启动且仅针对目标类。下一工作包据此处理 Pro 的无关启动展开，不能用跳过必要控制来冒充修复。首次编译和 Heap 角色登记失败、旧候选误用及权限受限诊断均保留；原游戏未动，无远端推送。详见 [当前实际报告](../../validation/b-final-20261007/REPORT.md)。

以下为历史候选结果，按原范围保留。

---

# 2026-10-07 B 批收口候选：完整构建通过，实际启动仍阻塞

保留 R7，补修指令回放的后续编辑 ABA/删除覆盖、任务结算的取消排空/ACK 条件、存储待处理项调度，并根据实际启动栈减少私有元数据草稿及准确本方调用者查询的重复守卫开销。Java48/Native42/Journal6 未变。

Windows/JDK17 下 Core、Agent、Bootstrap、Native、Prelaunch、Fixture 完整构建通过；实际指令历史辅助判定及生成字节码定点检查通过。三次实际隔离启动仍超时，世界未加载；最新主线程在 Native 字节码版本比对/跨类来源分析。因此尚未执行 B 的实际来源/任务/资源/写盘重启组合，**B 未完成**。既有停服、旧 R8 和两类来源归属缺口保留。远端基线 `c565e97`，本輪无公开推送；构建 JAR 只是失败启动候选。详见 [本轮实际报告](../../validation/b-final-20261007/REPORT.md)。

以下为此前结果，按原范围保留。

---

# 2026-10-07 R7：构造期漏防与取消异常接续源码修补

在独立 R6 副本上修正三份生产源码：字段守卫复用实际构造接收者分析，初始化前对已有对象的写入不再跳过；取消 CAS 成功即保存事实，完成栈排空异常可接续；停服在发布锁外接续原取消结果。

Windows/JDK17 下完整 Agent 编译、实际变换字节码构造场景和真实 CompletableFuture/日志的 35 项定点断言通过。构造写策略和任务 runtime 等仍有明确替身，未做完整 Forge/Native/游戏组合验证。首次夹具临时目录权限失败保留。两类来源漏记及 R6 未验证范围不结清，无新安装 JAR或公开推送。详见 [R7 报告](../../validation/r7-20261007/REPORT.md)。

以下为 R6 及更早的历史结果，按原范围保留。

---

# 2026-10-05 R6：继续查错后的定点源码候选，保留明确未修复项

直接基于 R5，仅改 ExternalCodeImages、RecoveryTasks、TaskBridge 三个生产文件。修正异常处理器回放追加到末尾造成的 catch 优先级错误、后续 handler 改写/删除误覆盖；结果取消成功或自然完成后继续原日志队列拒绝回执，并在关闭时等待真实 ACK；字段写包装入口不再作为内部控制者候选。

保留身份的指令/handler 排列变化现明确标为待处理，不再假报已回放；实际排列中和与来源归属尚未解决。仅改 catch 表的来源归属也仍漏记，四种真实异常路径已复现。没有加入未经验证的 Java/Native 异常协议改造。

Linux/JDK21 下完整 Agent/Bootstrap 独立编译通过；R5 回放对照重现 7 项错误，R6 10 项回放用例通过；真实日志/有界队列/CF 取消定点检查 R5 12 项复现、R6 19 项通过。首次夹具编译失败及修正后结果均保留。控制入口修补只有静态审查与编译，其动态验证本轮受平台限制未执行，旧 R5 夹具成绩不能补充为本次证明。

未进行完整 Forge/Core、Java17、Windows Native、游戏或启动性能验证；没有新游戏 JAR/公开推送，B 不标完成。Java48/Native42/Journal6 未变。详细实际范围、剩余缺口和复现日志见 [R6 实际运行报告](../../validation/r6-20261005/REPORT.md)。

以下 R5/R4 及更早成绩仍仅按其原有范围保留。

---

# 2026-10-05 R5：R4 上的启动重复开销修订，仍为源码候选

直接使用用户指定的 R4 完整源码包，仅修改 ExternalCodeFlow、ExternalCodeImages、ExternalCodeDefinitions、TaskBridge 四个生产文件：同次分析复用固定 CFG／后支配关系；同次树操作复用反射方法发现并删除未消费标签遍历、重复指令身份查询；内部临时声明索引避免监控 Map 回填；准确控制门的肯定身份查询在一次写操作中复用。每轮来源传播、当前节点读取、真实调用者鉴权和 Native 版本／一致性复核仍执行，没有跨分析来源／权限缓存。Java/Native 原始内存来源抓取与可变控制字段复读未盲目合并。

R4 的 WorldArchive 和 RecoveryTasks 原文保持，三项 R4 修复保留。Linux/JDK21 下完整 Agent／Bootstrap 独立编译、实际来源传播检查、6 项实时 opcode／布局拒绝检查以及 13 项准确控制门释放／外部冒用拒绝检查通过。初版门夹具触发 WindowsChannelFactory 平台缺失，保留失败记录，未改生产代码，仅收窄夹具后复查。

完整 Forge/Core 构建仍在下载 Gradle 8.8 时因域名解析失败阻断；没有 Java17、Windows Native、世界加载或启动耗时对照结果。门检查不覆盖授权获锁及完整业务回执。没有新游戏分发 JAR 或公开推送；B 不标为完成，不能声称 635 秒问题已解决。详细范围见 [R5 实际运行报告](../../validation/r5-20261005/REPORT.md)。

以下 R4 及更早结论按原范围保留，不能当作 R5 的额外新验证。

---

# Ronova Pro 当前进度与 A 批状态


## 2026-10-05 R4：R3 后续定点源码补修

直接基线：用户提供的 `ronova-c565e97-repaired-r3-20261005-source.zip`。本轮仅修改两个生产文件，保留 R1/R2/R3 原有修订；不推送远端，不生成或混入新的安装 JAR。

- **WorldArchive：局部再生成的计划 tick 捕获失败。** ProtoChunkTicks 分支缺少大括号，使最后的 `else` 绑定到内部区域筛选条件。区域外的普通 tick 会错误抛出 `WORLD_TICK_CONTAINER_UNAVAILABLE`，未知容器反而会返回空快照。已只修正分支归属，保留选区、类型、延迟、优先级和顺序。
- **RecoveryTasks：普通处置及 worker 队列动作的来源交付竞争窗口。** 外层检查通过后、进入 `withBoundaries` 之前，真实 emit 可以开始并等待 LISTENERS，原代码仍按旧的单组来源取消或移除对象。两处都在原 Bootstrap 发布监视器内、实际动作前复查在途交付及待合并事件；未执行动作时返回未执行。原资格、主体锁、来源组与边界认证继续保留；R3 的 policyGate 不新增“等待整个队列清空”的门槛。
- **RecoveryTasks：普通 ForkJoin 围栏可能在关闭时永久残留。** 已清除捕获字段但尚在等待真实 callback-release ACK 的任务，进入 close 后原分支只接续 CF 围栏。现接续已清除的普通 ForkJoin；确认原 ACK、调用/提交/worker/采用调用/助手/等待者退出、原捕获和队列不再持有对象，并在释放监视器内再次核对。只走原注册项释放流程，不在关闭阶段追加取消、清字段或删队列动作。

本轮实际结果：

- 计划 tick 方法定点检查：R3 重现异常，R4 16 项断言通过
- 任务提交/退役定点检查：R3 的两处来源竞争与关闭残留按预期重现（13 项复现断言）；R4 27 项断言通过
- 上述为从实际源码原文提取的方法/关闭分支；计划 tick 的 Minecraft/NBT 类型为替身，任务测试的 runtime/journal/coverage/waiter/helper 为明确替身，使用真实 Java 线程、ForkJoinTask/Pool 及原文提取的 Bootstrap 发布、AUTHORITY、围栏方法。不是完整 RecoveryTasks/Core 编译，也不是完整 Agent 安装或游戏效果验收
- 环境为 Linux/OpenJDK 21.0.12.1，编译使用 `-source 17 -target 17`。当前环境没有 Java 17 运行时和 `ct.sym`，未宣称 Java 17 API/运行验证
- 完整 `compileJava` 已尝试：Gradle 8.8 下载阶段 `UnknownHostException: services.gradle.org`，尚未进入 Forge/Core 类型编译。未运行 Windows DLL、Forge 世界、客户端/专服或启动耗时测试，B 状态不变

两个独立定点脚本位于 `validation/check_world_ticks.py`、`validation/check_task_commit.py`，没有接入默认 Gradle/游戏任务。原始运行记录及一次已修正的测试模板编译错误保留在 `validation/r4-20261005/`。详细范围见该目录 `REPORT.md`。下方 R3 及更早成绩均仅按历史范围保留。

---

> **2026-10-05 R3 补修**：策略围栏已接入既有 admission 释放/关闭流程；在途来源交付结束前不安装准确对象围栏，已完整发布但未合并的普通事件不额外等待空队列。完整 RecoveryTasks + 实际 Bootstrap/文件日志的独立定点回归通过；世界/来源依赖为替身，未跑 Forge/Java17/Windows 实机。删除 FixtureCommands 的生产特权控制台分支、Core/Native 人工故障注入和可选 dump/调试输出；旧依赖这些测试分支的夹具不能直接沿用历史成绩。R3 包含下方 R1/R2；下方记录均按原范围保留。详情见 `R3-补修说明.md`。


> **2026-10-05 R2 补修**：已补截图三项：世界受理保存并消费原 append ACK，失败/取消不发布世界动作；生成失败重试接续同一未退役生成器及其队列；policyGate 通过原 withBoundaries 授权作用域安装真实围栏，不改旧任务资格或删 Bootstrap 权限。额外只改 WorldArchive/SeedChunks/RecoveryTasks 三个生产文件。Linux/JDK21 的定点世界控制流与真实 Bootstrap 授权检查通过；未进行完整 Forge/Java17/Windows 实机验证，仍为源码候选。R2 包含下方 R1 全部修改；下方“本轮”均为其历史记录，不混为 R2 结果。


## 2026-10-05 c565e97 修订：源码已改，非新游戏安装候选

直接基线为 `c565e975d78a3b32ba72f493c72c953d24698203` 的用户交接 ZIP。本轮修改 9 个生产文件：任务消费者 ABI 39→48；字段图异常保留真实工作阶段；启动世界参数支持等号形式并拒绝歧义；世界作业先提交受理再发布，重试接续失败步骤，实体等待不重建方块实体／计划 tick；历史任务轮转、释放事实恢复与旧策略日志兼容。启动部分减少无停组时的整批 Mixin 准备和无关树哈希，复用经当前 Class／加载器／映像身份及修饰符核对的弱 receiver 布局，真实来源及最终写权限不缓存。

Linux／OpenJDK 21.0.11 下，Bootstrap 和 Prelaunch 全源集以 `--release 17` 编译通过；Agent 全源集使用 `-source 17 -target 17` 及所需内部 ASM 导出编译通过。实际桥 ABI 48 与原 `attach()` 消费入口接续、字段图 14 项、真实文件任务历史 20 项、前置世界选择 17 项、世界作业控制流 30 项及 receiver 布局 7 项通过。Mixin 私有准备入口原产物／修订对照为无停用贡献时 1000／0 次序列化，停用出现后仍读当前图像。这里使用明确的世界、光照、生成器和 NBT 传输替身；ABI 检查使用真实 VM Instrumentation 但未运行整套 Ronova 变换器安装。不能冒充实际游戏效果或 Windows Native 验收。

完整 Gradle `jar fixtureJar` 已尝试，下载 Gradle 8.8 时因 `UnknownHostException: services.gradle.org` 中止，尚未进入完整 Forge 类型编译。本轮没有 Java 17 实际运行、Forge 世界加载、客户端／专服、Windows DLL 或同世界重启结果，**不能宣布原 635 秒启动问题已彻底解决，也不能改写 B 的完成状态**。Native 源码未修改（ABI 42），Java ABI 48、Journal 物理格式 6、网络协议未改。普通权限、来源、运行／资源退出检查保留；未新增游戏探针、Gradle 检查阶段或一般化证明框架。

本次源码交付移出了输入的旧 `build/libs` 和 `distribution` 安装 JAR；原输入 ZIP 保留那些历史产物。增量补丁只改源码／文档，不会自动删除工作区旧产物；必须重新构建 Core、Agent、Bootstrap、Prelaunch 配套载荷，不能把旧 JAR 当成这轮修复。

以下为原 c565e97 历史记录，原通过／失败及测试范围不变。

---


2026-10-05 startup-origins：空种子分析原先对每个已登记实际 Class 单独执行反射来源读取及 StackWalker 权限核对。现每次分析仍重新收集实际 Class，在原来源登记锁内批量读取当前记录，再按本次 Module 身份去重执行原 producer／stopped 判据；未知未绑定分支和实际 VM binding 保留，没有跨分析缓存。新桥入口沿用原准确 Agent 身份核对，RecoveryAgent 的批量 helper 只允许准确 ExternalCodeDefinitions 调用。

同版完整构建通过（17 秒），原实际 prelaunch 的 B 适配／控制保护／缓冲传输检查通过，新增批量来源入口的外部反射和 MethodHandle 调用均被拒绝。当前分发候选为 startup-origins（Java 48／Native 42）。

原 Forge 双端效果夹具实际运行约 635 秒，线程从 Blocks 初始化第 77 行推进到第 149 行，主线程 CPU 约 564 秒、外部代码分析线程约 510 秒，仍未完成世界加载；未再出现此前的 StackOverflowError，没有客户端加入或整组停用效果 PASS。现场仍有控制调用者核对和 ASM／外部代码展开开销；准确控制索引已经命中的最新权限核对栈不会落入旧控制数组的线性成员扫描，因此合并两登记器不能消除这处开销。保存现场后已关闭全部项目游戏、启动器和夹具，核对剩余为 0。B 未完成，原始日志、线程和存档只保留本地 .work。

2026-10-05 B 启动阻挡接续：真实服务端现场确认来源查询、外部代码分析和 VM 元数据检查持续消耗 CPU。已在同一分析批次复用不可变定义索引及经当前 Class／加载器／修饰符核对的 receiver 布局；整类 VM 查询批读常量池并核对变化项，实际方法、字节码、常量、异常表和最终版本检查继续执行。原生入口改用当前真实 JVMTI 方法帧核对准确 CodeSourceBridge；来源登记器构造的准确锁和 Sync 接入已有控制锁身份登记。

startup-gates 的实际服务端随后因 StackOverflowError 退出：scopedSources 首次链接 Module[] 数组构造函数，LambdaMetafactory 的原子计数写入又进入同一来源查询。本包已将这条底层捕获链的数组构造改为普通数组、来源过滤改为循环，并在原锁内直接创建准确字段票据。字段定位只复用弱声明 Class／fieldID 候选；每次核对当前声明类、完整选择器和修饰符，随后读取实际 holder 当前值。

startup-capture 完整构建通过（20 秒），同版实际 prelaunch Agent 下原 B 适配／控制保护／缓冲传输检查通过；外部控制锁登记与原生数组读取入口被拒绝，实际单元素数组写入保持相邻值。首次新增原生拒绝断言误用 Java 侧异常类型的失败保留；改为核对原生原有异常及准确原因后通过。

startup-capture 在原 Forge 双端夹具中实际运行约 1039 秒，未再出现此前的 StackOverflowError；线程现场从 Blocks 初始化第 82 行推进到第 222 行，但仍未完成世界加载。主线程 CPU 约 947 秒、外部代码分析线程约 888 秒，来源查询与代码分析开销仍在；客户端未启动，未执行整组停用。已关闭本轮游戏、启动器和夹具，剩余为 0。B 未完成；原始线程、日志和存档仅保留本地 .work。

2026-10-05 B 实机测试结果：沿用原客户端／服务端整组夹具，使用当前 unknownwatch 候选、真实 prelaunch 原生 Agent 和 Forge 1.20.1／47.4.23。服务端完成 Agent 安装和 Forge 启动，但运行约 1019 秒仍在 Minecraft 方块初始化，没有出现世界加载完成标记。线程取样从标签／注册表推进到方块状态创建，最后主线程仍为 RUNNABLE；未发现死锁。现场还观察到外部代码来源分析线程持续运行，并在来源容器登记锁上造成主线程等待；这说明启动开销仍未解决，不能据此把全部耗时归结为单一路径。

本轮在启动阶段终止，客户端尚未启动／加入世界，未发出停用命令，未得到回调停止、实体清理、邻居存活或重启持久化的游戏效果结果。B 仍未完成；已有安装／适配检查通过不等于游戏效果通过。全部本轮游戏、启动器和夹具进程已关闭，核对项目游戏进程剩余为 0。原始日志、线程现场和存档仅保留本地 .work；公开同步本摘要。


2026-10-05 B UNKNOWN 字段观察接续：bootmodules 的真实 JAR 扫描现场仍大量进入实际文件／缓冲区来源路径。字段低层写入现仅在本次贡献为 UNKNOWN 且没有任何已知 Module、既有字段也全未知且没有有效 revision／已安装 VM 观察时延后首次 VM watch；原写入回执、并发计数、UNKNOWN 传播和实际 holder／FieldSlot 对齐保留。有已知贡献、已知字段状态、有效 revision 或已安装观察时仍在写入前执行原观察安装，实际指令读写路径的观察保持。

unknownwatch 完整构建通过（19 秒）。本次沿用原 BoundaryCheck 的 b-adapter-linkage，通过与游戏相同的 prelaunch 提前加载原生 JVMTI：反射 9 项、Unsafe、句柄 30 项、外部反射／MethodHandle 批量入口拒绝、原控制对象改写拒绝、B 适配与真实堆→直接缓冲／只读视图传输均通过。原始现场仅在本地 .work。

当前分发候选为 unknownwatch（Java 48／Native 42）。已核对并关闭 bootmodules 游戏／启动器／夹具，只复跑 mod-group-client-b48-unknownwatch-20261005 这一份原真实双端游戏效果测试。服务端已完成 Agent 安装并出现 Forge 初始化日志，尚未完成世界加载／停用动作，没有 B 游戏效果 PASS，B 未完成。


2026-10-05 B 实际 boot Module 接续：bootdeclaration 的真实服务端完成 Agent 安装后，JAR 扫描现场仍因未绑定的 JDK 平台加载器映像重复进入完整 receiver 分析。空来源判断现核对实际声明 Module 属于 boot layer 且映像记录的加载器与该 Module 的实际加载器一致；直接／控制来源及当前生产声明判断保留，未知声明、其他 layer、不同加载器／逻辑来源仍走原完整路径，Class 绑定后仍读取实际逻辑 Module。内部 ASM 批次使用限长 256 的局部数组，避免为这些临时工作列表进入来源容器守卫，完整闭包和逐字段检查保持。

bootmodules 完整构建通过（17 秒）；原实际 Agent 的反射 9 项、Unsafe、句柄 30 项、批量读取／登记外部反射与 MethodHandle 拒绝、控制对象改写拒绝、B 适配及直接缓冲传输检查通过。当前分发候选为 bootmodules（Java 48／Native 42）。已核对并关闭 bootdeclaration 的游戏／启动器／夹具，只复跑 mod-group-client-b48-bootmodules-20261005 的原真实双端效果测试。尚未进入世界或获得 B 游戏效果 PASS，B 未完成。原始现场只保留本地 .work。


2026-10-05 B 启动声明与控制登记修复：真实 imagebatch 服务端完成 Agent 安装后，Forge JAR 扫描仍反复分析尚未绑定 Class 的 JDK 映像。现保存变换时已观察到的实际声明 Module；仅对 bootstrap 且声明确属 boot layer、没有直接／控制来源、当前没有活动生产声明的未绑定映像沿用空来源判断。其他加载器、未知声明、逻辑来源、隐藏映像和已有来源仍保留原完整查询；Class 绑定后仍读取实际逻辑 Module，不缓存许可或来源结论。控制登记改为按批核对实际写入者，每个对象继续用原弱身份索引、扩容及回收算法。

初次实际 Agent 检查失败：默认 Map 守卫首次解析 UUID 时循环加载，导致反射／Unsafe／句柄保护没有装上。现于发布守卫前解析 UUID 类型，尚无策略时直接返回原空结果；变换准备／发布阶段异常也写入已有失败状态，防止 Instrumentation 忽略异常而漏报。修复后完整构建通过（19 秒），同一实际 Agent 检查通过：反射保护 9 项、Unsafe、句柄 30 项、批量读取／登记反射与 MethodHandle 越权拒绝、原控制对象改写拒绝、B 适配与直接缓冲传输均有实际结果。初次失败原始现场保留本地。

当前分发候选为 bootdeclaration（Java 48／Native 42）。imagebatch 游戏／启动器／夹具已核对并关闭；只复跑 mod-group-client-b48-bootdeclaration-20261005 这一份原有真实 Forge 双端效果测试。尚未进入世界或获得 B 游戏效果 PASS，B 未完成。原始日志和存档仅保留本地 .work。


2026-10-05 B 实机测试接续：此前五份后台游戏、启动器和夹具已按用户要求全部关闭。用户明确要求继续实际测试后，仅启动现有客户端／服务端整组夹具。fieldlayout 的现场仍停在 Agent 安装，内部 ASM 节点逐个跨反射入口并重复检查实际调用者；现将每批最多 256 个节点的读取合并，逐字段保留实际 bootstrap ASM 声明 Class、非静态引用类型、holder 身份核对和当前值读取，完整追踪容器、指令前后链接及 InsnList，不缓存来源结论。

imagebatch 完整构建通过（23 秒），原实际 Agent 下 B 适配、外部反射／MethodHandle 批量读取入口拒绝、控制对象改写拒绝及直接缓冲传输通过。当前分发候选为 imagebatch（Java 48／Native 42）。旧 fieldlayout 双端测试进程已关闭；mod-group-client-b48-imagebatch-20261005 正在实际 prelaunch 下运行，目前服务端仍在安装阶段，尚无 B 游戏效果 PASS，B 未完成。原始日志只保留本地 .work。


2026-10-05 B 字段布局接续：stopquery 来源实例的 ZIP 读锁 CAS 现场确认每次重扫全部声明字段并重复调用 Unsafe 布局方法。现按实际声明 Class 分开复用实例／静态字段的 VM 布局，保留真实 receiver／静态 base、准确 offset／width、重叠拒绝及原未观察后备；布局数组、Span 和 Field 登记原控制保护，字段值及来源结论不缓存。Agent 的实际调用者检查复用同一 StackWalker，原身份与权限核对保持。

fieldlayout 完整构建通过（23 秒），原实际 Agent 下 B 适配、外部反射／MethodHandle 内存入口拒绝、控制对象改写拒绝及实际直接缓冲传输通过。当前分发候选为 fieldlayout（Java 48／Native 42），b-record-abi48-fieldlayout 使用真实 prelaunch 运行引用清理／防回填，stopquery 来源及原整组／保存链／任务场景继续。四份已替代的 readleaf／imagefields／entrycaller／nativeindex 来源实例核对身份后停止，现场保留。尚无 B 游戏效果 PASS，B 未完成；原始日志只保留本地 .work。

2026-10-05 B 停用查询：真实 nativeindex 来源实例的 Unsafe getter 仍在进入 native 前置门时扫描完整调用链。现每次先在原锁下读取实际 Owner.stopped；当前没有任何停用来源时直接返回未停用，出现停用来源后仍沿完整原查询。没有缓存停用判定，来源捕获与 UNKNOWN 保持原规则。

stopquery 完整构建通过（17 秒），现有实际 Agent 下 B 适配、外部反射／MethodHandle 内存入口拒绝、控制对象防改写和直接缓冲传输检查通过。分发候选更新为 stopquery（Java 48／Native 42），b-source-abi48-stopquery 已使用真实 prelaunch 启动。此前 B 场景尚未获得游戏效果 PASS；B 未完成，原始日志仅保留本地 .work。

2026-10-05 B Native 查询工作包：imagefields 来源实例 881 秒现场确认原始地址读租约重复扫描 Java 调用链。已认证的读租约复用这次操作传入的 Java 来源；VM／原生库来源仍重新观察，UNKNOWN 与全部原租约规则保留，写入／分配／I/O／追加来源仍用原捕获。源码同时确认执行映像与来源查询每次扫描全部已发布映像；现增加准确名字候选桶和隐藏映像链，合并后保持原发布顺序。完整类名、真实加载器／隐藏 Class 绑定、全部字节码与常量及 VM 当前版本仍逐项核对，原来源查询的发布范围保持，未缓存来源判定。

nativeindex 完整构建通过（23 秒），原实际 Agent 下 B 适配、外部反射／MethodHandle 内存入口拒绝、控制数组／同步器防改写及直接缓冲传输检查通过。分发候选更新为 nativeindex（Java 48／Native 42），b-source-abi48-nativeindex 已用同版真实 prelaunch 启动。两份已被接替的 gatepolicy／bufferlayout 来源实例停止并保留现场，其他场景继续；仍无 B 游戏效果 PASS，B 未完成。原始日志仅在本地 .work。

2026-10-05 B 执行来源调用者接续：readleaf 来源实例 1040 秒现场进入 ZIP 文件属性遍历，主线程在内存写入收尾的 ExecutionFlow 调用者扫描。统一将执行来源入口接到已有直接调用者核对；只有自身 nest 内部委托仍沿原外层调用链扫描，仍要求实际 CodeSourceBridge，不扩大控制权限。完整工作包构建通过（24 秒），原 B 夹具中的外部反射／MethodHandle 内存入口拒绝、控制对象防改写及实际缓冲传输通过。

当前分发为 entrycaller，Java ABI 48／Native 42，b-source-abi48-entrycaller 使用同版真实 prelaunch 启动。先前场景仍运行，尚无 B 游戏效果 PASS，B 未完成。公开同步代码、候选及摘要，原始现场仅保留本地 .work。

2026-10-05 B 叶级堆读取接续：Java Unsafe／句柄和 Native 的独立堆 getter 包装没有来源结果消费方，现去掉这层空读门和回调。实际执行读取、复制及缓冲区读取仍保留完整观察，原始地址读取租约、写入策略和原操作异常保留。完整工作包构建通过（26 秒），既有实际 Agent 下 B 适配、控制对象外部改写拒绝及缓冲传输检查通过。

随后真实启动的 6 次采样中，3 次落在内部 ASM 图的字段捕获。叶级读取修补后，已受保护且可访问的原 Field 列表恢复直接读取实际节点字段，省去每字段重复原生字段扫描；Agent 入口认证、准确声明类／字段类型／holder 核对和控制闭包保护保持。imagefields 完整构建通过（24 秒），原 B 适配及外部改写拒绝检查通过。

分发候选更新为 imagefields，Java ABI 48／Native 42。b-source-abi48-imagefields 使用独立端口真实 prelaunch 启动；readleaf 和原场景保留继续，尚无 B 游戏效果 PASS。B 未完成，原始日志仅保留本地 .work。

2026-10-05 B 缓冲布局读回接续：gatepolicy 来源实例 455 秒现场已到 ZIP 中央目录读取，ResourceBridge 的跨度计算通过 Field.getInt 再次进入 Unsafe 读取门。缓冲的 position／limit／capacity／address、实际 backing／父视图及原 cleanup 地址读回改用已有准确 Native 字段读取；只开放给真实 ResourceBridge 及其 nest，仍读取当前 holder／真实声明字段／完整类型，native 不可用时保留反射后备，不缓存布局值。实际数据读取、复制来源窗口、共享视图及原资源退役边界保持。

首次构建保留 IllegalAccessException 异常类型接续失败；释放读回失败仍报告 BUFFER_RELEASE_RESULT_UNOBSERVED，修正后完整构建通过（25 秒）。既有 B 适配检查通过，新增的实际已安装 Agent 下堆→直接缓冲传输、共享只读视图及源字节保留检查通过，原外部改写拒绝保持。候选更新为 bufferlayout，ABI 保持 Java 48／Native 42。b-source-abi48-bufferlayout 用独立端口真实 prelaunch 运行；其他五个 gatepolicy 场景保留继续，尚无游戏效果 PASS，B 未完成。原始日志仅在本地 .work。

2026-10-05 B 停用期间的内部锁策略：readlease 来源实例 471 秒现场仍在准确字段门 CAS 的控制权限查询。源码确认 gate／真实同步器还先经过业务字段、来源停用及 backing 策略，可能在已停用模块的退场调用上拒绝控制器自己的锁状态操作。现在仅这两个构造登记的准确身份先采用原实际 JDK 锁调用／直接控制者认证及原控制 writer 权限；其他 receiver、批量内存写及业务写策略不变。反射、字段和标量 Unsafe 路径已一并接续。

gatepolicy 完整工作包构建通过（25 秒）；原 B 适配检查通过，真实同步器外部反射和 Unsafe 改写仍被拒绝，内部堆表外部删除也被拒绝。分发候选更新为 gatepolicy，ABI 保持 Java 48／Native 42。五个既有 B 游戏场景已使用同版真实 prelaunch 启动：整组清理、来源模块、准确引用、保存生产链、完整任务后继。旧 read／readlease 实例在无游戏效果时停止并保留现场。B 尚无游戏效果 PASS，仍未完成；原始日志仅保留本地 .work。

2026-10-05 B 内部读门补修：read 实例 691 秒现场仍在 JAR 签名读取，为每个内部 Unsafe 读取新建并保护随后直接关闭的来源窗口。调用方已确认：Java／Native 内部读取包装只关闭回执，不读取其来源；现保留同一真实 receiver 的读门和受保护回执，已有同线程读／写门覆盖的嵌套读取不再重复开门。实际代码执行的 Unsafe 读取、复制及缓冲区读取仍使用完整区间窗口与并发写来源；原始地址读租约不变。未删除 Buffer.scope 的独立访问边界。

完整 readlease 包构建通过（24 秒），原 B 适配／控制对象外部改写拒绝检查通过。分发候选更新为 readlease 包，来源场景 b-source-abi48-readlease 使用同版真实 prelaunch 运行（独立端口）；其余 read 场景继续运行，尚无 B 游戏效果结果。ABI 保持 Java 48／Native 42，B 尚未完成，原始现场仅在本地 .work。

2026-10-05 00:44 B 读取开销修复：access 来源实例在 2444 秒、引用实例在 2202 秒现场仍运行于 JAR 包扫描／签名读取，主线程进入 Unsafe 的读取观察，并非死锁。准确字段门及同步器现在在来源收集前识别，仍执行原写入权限；堆读取只使用原内存区间来源与并发写入观察，去掉 Java／Native 随后被丢弃的调用栈来源收集，原始地址读取及所有业务写入的来源捕获保留。NativeControl ABI 升为 42，Core／Agent／Bootstrap 48、Storage 3、Journal 物理版本 6 保持。

本工作包完整构建通过（31 秒），既有 b-adapter-linkage 在实际 Agent 下通过，包含真实同步器反射／Unsafe 状态改写拒绝、内部堆表删除拒绝及原适配入口。候选更新为 read 包。来源场景 b-source-abi48-read 已在真实 prelaunch 下运行；原 access 来源实例停止并保留现场，其他四个 access 场景尚无游戏结果。B 尚未完成，未将安装检查计为实际清理／重启通过。本轮原始日志仅保留本地 .work，公开同步源码、候选和结果摘要。

2026-10-04 23:58 B 控制读取接续：gates 整组实例的 598／1156 秒现场已越过原堆表锁循环，继续加载 Forge 文件系统和 JAR 签名类；主线程反复进入内部控制 backing 的 trySetAccessible → Module → Unsafe 观察。控制捕获已采用认证原生字段读取，现仅在 native 不可用的原反射分支要求 trySetAccessible，实际声明类／字段／类型读取与完整 backing 保护不变。完整构建通过（29 秒），同一 B 适配器检查再次通过；候选更新为 access 包。来源实例使用 access 包复跑。其他四个既有 B 场景仍在 gates 包下启动，尚无游戏效果 PASS；来源目录清理锁曾有短时竞争，未确认新的死锁。B 尚未完成。

本轮原始构建／运行日志及线程现场仅保留在本地 .work；仓库同步源码、对应候选及下述结果摘要。自动审批未允许公开同步原始运行元数据。

2026-10-04 23:38 B 内部锁等待修复：byte 实例的 1474 秒现场确认来源堆表清理嵌套字段门，而字段门 AQS.state 写入回调再次等待堆表。构造登记的准确内部 Map 现在保留实际 writer 权限检查、使用自身既有锁，不进入业务字段门；字段门及其真实 JDK 同步器在发布前登记保护，只有这两个准确身份不进入业务堆来源记录。Native 回调的真实内部加锁按当前 JDK ReentrantLock 调用及直接控制者认证，不以回调包装器授予外部写权限。

完整构建通过（24 秒、最终 22 秒）。既有 B 适配器检查通过：实际 Agent／Netty 安装、外部反射和 Unsafe 改写真实同步器状态拒绝、外部删除内部来源堆表条目拒绝、原 fastutil 对象及字符包装入口正常。最初的回调拒绝与夹具拒绝异常现场保留；这些不计为游戏效果。候选更新为 gates 包。mod-group-dynamic-b48-gates 与 b-source-abi48-gates 正在真实 prelaunch 下运行，B 仍未完成。

2026-10-04 22:52 B 内部字节比较补修：query 实例 289 秒现场已越过查询键卡点，进入内部映像 Arrays.equals → Unsafe.getLong 自我观察。映像发布／隐藏定义／创建映像／JDK 任务映像及恢复快照的字节比较统一改用原字节逐项比较，仍要求完整内容相等。两次构建均通过（24 秒），当前候选为 byte 包。mod-group-dynamic-b48-byte 与 b-source-abi48-byte 正在运行；203 秒现场为准确原生字段读取，尚无游戏效果结果。B 仍未完成。

2026-10-04 22:43 B 查询键补修：前置安装 307 秒现场位于 CallQuery 自动 hashCode → MethodHandle 字段读取 → Unsafe 来源观察。解析查询键及其嵌套 Member／Receiver 改为直接字段 equals／hashCode，保留全部组件、null 与集合等价语义；不缓存来源结论。完整构建通过（22 秒），候选更新为 query 包。mod-group-dynamic-b48-query 与 b-source-abi48-query 正在真实 prelaunch 下运行，仍未到游戏效果 PASS；B 尚未完成。

2026-10-04 22:36 B 启动继续修补：真实前置入口的 ASM 控制映像反射读取会再次进入 Unsafe 来源观察，旧来源场景运行到 800 秒仍在这一捕获链内，未到游戏。仅缓存 MethodHandle 的尝试仍触发同一路径，已撤回。最终复用已有 native heapReadField，供认证 Agent 的 ASM 引用字段及内部控制容器 backing 捕获使用，保留实际 holder／声明类／字段名／类型核对与完整保护遍历；没有给业务代码新增观察豁免。bootstrap 类层级解析也接上已有的已加载类查询，未命中仍保留原资源路径。

上述完整补包构建通过（23 秒），控制读取修改后的既有 B 适配器检查通过；最终类层级解析修改构建通过（23 秒）。原实例现场和一次括号编译失败保留在既有 evidence-20261002。候选更新为 frame 包。当前 mod-group-dynamic-b48-frame、b-source-abi48-frame 使用真实 prelaunch 运行，已跨过内部 ASM 反射读取阶段，仍未获得 B 游戏内效果结果；B 完成状态不变。

2026-10-04 晚间 B 收尾：B 尚未通过。早前引用、保存链和来源三个场景最终都在启动网络时遇到 Netty `write(Object, boolean, ChannelPromise): void` 的 `VerifyError`，没有进入效果验证；进程返回 0 不能计为通过。现已将拒绝分支的错误 ARETURN 改为 RETURN，实际 Netty 类在已安装 Agent 下以 `-Xverify:all` 加载通过。

同批补完：控制弱表回收按实际节点直接摘链；字段门改为分桶弱身份表并保护真实根数组／桶数组；Class.reflectionData 只允许实际 JDK 缓存 CAS，其他控制镜像写入仍拒绝；内部字节快照按字节比较以消除 Unsafe 读回重入；资源布局方法与 ASM 引用字段查找按实际类缓存，仍读取实际字段／VM 布局。隐藏类来源比较只剔除实际声明模块的重复项，外部来源及 UNKNOWN 差异仍要求刷新，不能据此宣称任意隐藏类重转换已支持。

统一构建通过（23 秒）；既有 B 适配器检查通过，包含实际 Netty 校验、来源目录／控制表／字段门数组的外部改写拒绝。候选已更新。整组停用与来源清理场景正在使用真实 prelaunch 入口运行，尚无新游戏效果结果；保存链生产及同存档重启、完整任务结清也仍未通过。日志见既有 evidence-20261002 下 package-b-startup-final-48.log、b-startup-netty-controls-48.log；保留原安装卡点线程现场。

2026-10-04 B 启动后续修补：修复未知来源 null 协议后，真实游戏已越过 SensorType 的原崩溃点，完成 Forge 初始化和方块状态计算，并进入世界资源加载；该轮尚未取得 B 效果结果，随后停止旧实例以应用已定位的热点修复。方块缓存阶段 30 秒采样共 112 个主线程样本，其中 63 个落在 executionPlan0、33 个落在 ControlRegistry.reap。源码确认隐藏映像逐个重扫全部绑定的乘积开销：已改为每个映像维护自己的真实类绑定链，原锁、实际 Class 身份、字节码／常量及版本核对全部保留。堆来源目录命中时不再登记随即丢弃的弱键；短原型数组跨度只在实际 bits 完全相等时复用原快照，不删除 UNKNOWN 历史或连续性判定。统一编译通过（15 秒）并打包通过（11 秒），候选已更新，引用场景和保存链场景正在分别运行，B 仍未通过。

2026-10-04 B 实际运行收口（进行中，未通过）：本轮修复了文件委托字段的重复反射查找、缓冲操作临时列表造成的来源登记开销，以及来源目录单棵树与控制数组查询的启动开销。来源目录改为固定分桶的同一弱身份树，保留原门、真实身份及清理规则；真实来源目录根数组和控制表根数组的外部改写拒绝检查通过。

启动错误已查到具体接口冲突：resourceSources 把执行来源的未知状态编码为 null，实际 Io scope 原样传给本地来源捕获，而后者把 null 当作创建 Owner 失败。这会使正常 pread 文件读取被拒绝。当前修补将已知 Owner 与可选 unknown 输出分开，所有已知已停来源仍参与拒绝；文件／内存回调继续保留 null 标记，本地内存接入已有 unknown 位。不接收 unknown 的原数组租约、注册与进程归属入口维持严格行为。修补及实际失败阶段定位已统一构建通过（14 秒，package-b-native-unknown-48.log），候选包已更新；原引用场景正在运行，尚无修复后游戏效果结果。

真实日志检查已通过：Journal 物理版本 5 → 6 迁移保留旧记录，大于 1 MB 的逻辑记录落盘 ACK 后重新打开并由前置读取器读回，UTF-16 原始单元保留；截断最后一帧时拒绝确认和写入，保留原文件。结果见 evidence-20261002/b-journal-6.log。这些结果不代替游戏保存、原生提交和原意图重启效果。

实际游戏先确认 2 GB 堆已耗尽（约 20.85 亿字节存活对象），随后 6 GB 的引用场景与保存链场景均在 SensorType 类初始化期间因 EXTERNAL_NATIVE_CALL_SOURCE_UNOBSERVED 退出，未到达夹具效果。两份原失败日志保留为 b-record-48-native-source-failure.log 与 b-chain-48-native-source-failure.log。现已将重复保留的执行指令树替换为精确方法声明表，保留完整字节和语义来源图；构建通过，继续定位实际来源核对失败。B 尚未完成，ABC 目标仍未完成。

2026-10-04 B 标准来源剩余代码收口：Core／Agent／Bootstrap ABI 48，NativeControl 41、Storage 3、Journal 物理版本 6。上轮明确剩下的 fastutil 对象列表／对象集合、HashMap 空键、字符流包装器三项代码已经一并接通；这里的完成范围是这三项已确认的源码缺口，不代替 B 的游戏内效果验收。

ObjectArrayList 和 ObjectOpenHashSet 已接入实际来源登记、底层数组别名、限额接续扫描、准确身份删除、写后登记与历史缺席查询；Set 的空槽与其他成员保留。列表的单项、批量、数组段、子列表和迭代器写入在实际 receiver 门内判定，防回填判断覆盖可变共享包装的子字段。拒绝写入后，子列表长度按实际根列表变化更新，迭代器在推进游标前检查；保留邻居引用。发布另一 fastutil 容器时，同时保持真实 key/value/link 数组的门，避免检查与实际发布之间由旧数组别名改写。来源实际写入完成后再通知，不在半完成的结构修改中登记子来源。

HashMap／LinkedHashMap 的 null 键使用独立的 null: 类型编码；记录意图、实体引用、嵌套 Map 来源与重启查询沿同一解码，普通字符串键不会与它冲突。历史既有键编码仍可读取。

BufferedReader／BufferedWriter、PushbackReader／LineNumberReader、InputStreamReader／OutputStreamWriter、FileReader／FileWriter 及当前 JDK 的 StreamDecoder／StreamEncoder 接入真实构造登记。清理在既有资源 worker 中等待原锁，核对实际来源与实际委托，只分离所选包装器的委托和缓冲；不调用 close／flush，不把共享端点随包装器一起关闭。Reader／Writer.lock 对底层委托的隐藏引用同时断开，捕获的底层资源仍独立处置；跨来源使用沿实际委托链传播，暴露变化与退役封闭之间有当前版本核对。

本包代码写齐后统一编译通过（24 秒），候选及既有夹具打包通过（35 秒）。随后真实 Agent 安装检查发现并修复 ResourceBoundary 提前加载次序导致的重复类定义；fastutil 迭代器检查发现并修复转换时读取类型关系提前加载辅助类、跳过其拦截安装的问题，对象容器改用已有元数据帧解析器。最终相关重建通过（29 秒及最后 Agent 修正 13 秒），现有 BoundaryCheck 的 b-adapter-linkage 分支在真实 premain Agent 与 -Xverify:all 下通过，六个对象容器／视图／迭代器适配器全部 INSTALLED，字符流构造和读写调用通过。未安装替代策略，也未把链接检查当作目标清理效果通过。

既有 RecordReferenceFixture 增加 null 键和普通同名键分离、对象列表／集合准确清理及邻居保留、批量／旧数组／子列表／迭代器拒绝回填；ModGroupBlockFixture 增加字符流委托、待丢弃输出和共享 stdout。两者已编译，本轮未运行游戏夹具。此前第 76 次游戏启动超时，以及完整保存／提交回读／重启／迁移／大容量效果的未验证状态保留；独立 JVM 的既有 Native 文件入口 pending 输出也保留。

当前候选为 distribution/candidate-b-source-20261004/ronova-pro-core-0.1.0-stage-b-dev.jar，包含同版 Agent／Bootstrap 与 Native 载荷，前置入口为同目录 ronova-pro-prelaunch.jar。构建、首次失败及最终安装检查记录见 evidence-20261002/*b-adapters-48* 与 b-adapter-linkage-48*。本轮无无关测试，未启动新一轮 360 秒游戏检查。ABC 总目标仍未完成。

2026-10-04 B 共享来源与旧别名源码收尾：Core／Agent／Bootstrap ABI 47，NativeControl 41、Storage 3、Journal 物理版本 6 保持。Map／Set／列表／数组与 fastutil 的已登记来源写入，现在把共享包装及可确认子容器的锁保持到真实写入、实际读回登记和全部出口收尾；同一 receiver 的重入沿最外层实际写入保留锁，失败 CAS、拒绝和异常都释放。锁记录绑定真实线程与写入作用域，不由可替换的 ThreadLocal 提供。只为已登记来源建立发布记录，未登记的内部容器不增加这项工作。HashMap、Set 后端和旧 backing 数组一并登记实际来源。

HashSet／LinkedHashSet 中的可变共享包装已加入准确子字段处置，保留包装、邻居和辅助字段。重启不使用 Set 遍历下标或内容相等猜测原对象：原父来源与代码身份成立后，在实际 Set 门内检查同定义的全部当前包装，并复用原字段／Map／列表等实际 absent 查询、旧进程结束及持久 ACK 条件；嵌套后代沿同一路径查询。该历史查询视图不授予对象写入权，活包装仍通过真实位置另行登记。父来源合法更换后允许重建查询视图。

holder 换成新容器后，已观察的旧 Map／Set／数组别名保留原来源的防回填判断，后续发布的共享包装沿原实际容器登记；底层对象数组使用原字段对应 backing 的查询位置。未完成嵌套来源保留真实包装和原容器，整组实际处置完成或来源关闭才释放；GC 不代替处置结果。来源扫描忙碌会保留并重试子来源登记，不再因父字段未改变而跳过。整组字段清理已确认原实现具有写门失败与真实读回检查，未重复改写该动作。

现有 RecordReferenceFixture 加入共享 Set 的子字段清理与邻居保留、HashMap／CHM 合法包装发布后的准确字段防回填、带旧目标包装拒绝及 holder 替换后的旧 Map 别名检查；只编译，未运行。统一 Core／Agent／Bootstrap／Prelaunch／既有夹具编译通过（24 秒）；父来源重新绑定修补的 Core 编译通过（18 秒）；新写入辅助类及 ConcurrentFrame 提前加载的 Agent 编译通过（12 秒）。对应日志为 evidence-20261002/build-b-source-closure-47.log、build-b-query-rebind-47.log、build-b-scope-linkage-47.log。无新增独立测试／验证台账，未启动游戏或执行无关检查。

本轮确认的源码缺口已补齐并编译；这不等于 B 的实际攻防验收通过。原第 76 次启动超时记录保留，完整保存／提交回读／重启／迁移／容量效果仍缺实际通过结果；未知自定义容器和平台条件继续报告具体未决。未重建候选 JAR、未提交或推送 GitHub，ABC 总目标仍未完成。

2026-10-04 B 标准来源／嵌套包装／资源接续补齐：Core／Agent／Bootstrap ABI 46，NativeControl 41、Storage 3、Journal 物理版本 6 保持。Int2ObjectOpenHashMap、Int2ObjectLinkedOpenHashMap、Long2ObjectOpenHashMap 已接入现有来源发现、准确槽位清理、替换发布、实际写后通知与重启查询；整数键和长整数键保留原类型及完整位宽。原 fastutil writer 共用实际 receiver 门，覆盖查找、compute／merge、rehash、MapEntry.setValue 与迭代器删除；恢复扫描按实际 key/value 数组及变更版本接续，零键和链接顺序沿原实现处理，不调用自定义 key 的 hashCode／equals。

嵌套共享包装按实际父字段、Map 键、原子引用或列表／数组槽位建立子来源，保存父来源和代码身份；清理准确子字段，保留其他记录及辅助字段，holder 替换后仍保留原对象的处置义务。重启只沿已登记父来源查询原位置，仍等待旧进程结束与实际持久 ACK；来源未加载、代码改变、位置不可查时保留未决。直接 final 引用复用现有准确预期值／原字段写入及读回路径；未取得写门时不把动作标为已经尝试。声明为 Object、实际装有 Map／NBT 的字段也参与引用清理。共享包装的防回填判断沿可确认子引用识别旧目标；无法观察的内容仍由来源扫描报告，不能拿未知关系扩大删除范围。

标准 Filter／Buffered／Data／Pushback 流包装先保留实际委托，再分离本包装的准确引用；底层文件／资源沿已有真实句柄处置，共享端点保持，不在退役时刷新旧输出缓冲。普通 Runnable／TimerTask 经实际队列排空与资源结束后，观察器关闭流程复用同一退出事实；已进入的任务仍要求实际退出。未支持的容器不再因没有可遍历字段而被整组扫描误报处置完成。未知自定义布局、调度语义、来源及平台条件仍是明确的支持边界，未删除这些保护分支。

验证：统一生产源码和现有 RecordReferenceFixture 编译通过（25 秒），版本查询／数组通知接续修补后的统一编译通过（22 秒）；Object 实际来源和共享包装判断的 Core 修补分别编译通过（18 秒、18 秒）。原场景已加入三个 fastutil 后端的零键清理、防回填、长键邻居、链接顺序及共享包装邻居保留检查，仅编译，未运行。日志为 evidence-20261002/build-b-completion-46.log、build-b-completion-46-final.log、build-b-object-source-46.log、build-b-shared-publication-46.log。未启动游戏，未重跑无关检查；此前启动超时及保存／提交回读／重启／迁移／容量实际效果仍待验证，不能据编译成功宣布 B 整体验收完成。候选 JAR 与 GitHub 尚未更新，ABC 总目标未完成。

2026-10-04 B 源码补包收尾：Core／Agent／Bootstrap ABI 45，NativeControl 41、Storage 3、Journal 物理版本 6 保持。本轮将已登记可变 holder 的新容器检查、实际字段写入、写后读回登记及失败 CAS／异常释放接入同一作用域；反射、Java Unsafe、VarHandle、执行／Native Unsafe 接口采用相同收尾。CHM 的正常写入保留并发，容器发布单独排空在途写入。补上 AtomicReference.value 和 CopyOnWriteArrayList.array 不经过 Unsafe 的直接字段入口，列表实际 backing 数组随成功替换登记；列表删除允许其准确移位过程保留其他既有元素。HashSet／LinkedHashSet 新增准确身份节点清理及 RECORD/6 EDGE 历史接续，嵌套不可变单目标包装沿全部委托层解析，空包装不再误报未支持。旧容器的准确引用义务在 holder 替换／清空后继续处置原对象，确认释放后的查询也沿原容器；新容器由当前来源扫描建立独立义务。Map 扫描按完整一轮发布缺口，避免合法替换后永久保留旧状态。可变字段重启查询保留当前来源、旧进程结束及 durable ACK 条件。

编译结果：完整生产源码编译通过（30 秒）；包含上述旧引用／扫描收尾及既有 RecordReferenceFixture 的统一编译通过（23 秒）；空包装在实体 Map 分支的最后修补编译通过（19 秒）。既有场景改为检查 LinkedHashMap 真实清理、防回填和邻居保留，不再把它当作未支持类型。普通权限构建未能解析 ForgeGradle 插件，原失败日志保留；复用可访问依赖缓存的构建环境后通过，没有改动依赖版本。日志保存在 evidence-20261002/build-b-*.log。

完成边界：本轮源码补包完成并编译通过，不代表 B 整体攻防验收通过。未启动游戏或运行额外测试，未新增保存／提交回读／重启／迁移／容量效果结果；先前第 76 次启动超时记录仍保留。没有实际支持合同的容器、任务及不可控制旧活栈仍报告具体未决，不以编译成功抹除。当前候选 JAR 及 GitHub 尚未更新，整体 ABC 目标未完成。

2026-10-04 目录整理：按用户要求，将生产源码统一压平到 src/core、src/agent、src/bootstrap、src/prelaunch、src/journal，资源移到 src/resources；保留 core/mixin。现有验证源码分别移到 validation/fixture、validation/checks、validation/agent-check、validation/agent-policy，资源在 validation/resources。源码内包名、类名及逻辑保持原样，Gradle、现有脚本、Git 忽略例外和 README 已更新路径；原来的空目录已移除。未进行编译或运行测试，先前 B 源码包未验证状态保持不变。历史证据中的旧路径保留原记录。

2026-10-04：按用户“先把 B 的代码写完”，暂停新增构建和启动场景，当前继续 B 源码包。已写入自定义 ForkJoinTask 的真实 JDK 取消状态及等待者唤醒路径；LinkedHashMap／LinkedHashSet 的实际节点、顺序链和清理入口；实际字段写入／反射／Unsafe 完成后的容器读回登记；普通 ArrayList、对象数组的准确引用清理及 RECORD/5 重启查询。列表连续删除发生基线变化时，追加 EDGE_IMAGE 并等待持久确认后再按实际引用删除；历史查询允许沿同一来源、代码和字段的已确认删除图像接续。LinkedHashMap 普通读取不增加写入代次，访问顺序的真实调整在持锁后判断。对象数组的只读查询使用实际数组门但不制造写入代次；准确槽位删除与整组分段扫描使用各自入口，避免查询使非零槽位清理反复重置。整组已确认断开字段后，旧引用工作改为查询原容器；仅在原目标确已移除时结清，不把断开 holder 或 GC 当作容器内容清除。Core／Agent／Bootstrap 源码 ABI 更新到 44，NativeControl 仍为 41，Storage 为 3，Journal 物理版本仍为 6。上述新代码尚未编译或验证，不代表 B 全部完成；共享可变字段发布窗口、其余必要容器／包装和完整保存迁移路径继续保留具体缺口，未删除未证实的覆盖标记；未生成候选或同步 GitHub。

日期：2026-10-04。主工程：`C:\Users\Scorn\Desktop\MOD\Ronova-Pro-接续\ronova_GPT`。Core／Agent／Bootstrap ABI 43，NativeControl 源码 ABI 41、Storage Native 源码 ABI 3，日志物理 VERSION 6 分段保存完整逻辑记录；持久文本保留无损编码分支。

## B/C 当前实现进度

B 重启引用校验、数组适配及 C 区域视觉／Native 动态与延迟入口十文件补包的指令边界及 B 全部同类历史查询修补已统一 NativeControl41／Java 构建通过，23 秒，复用 Storage Native3；此前九文件首轮 23 秒、十文件补包首轮 25 秒的通过记录均保留。调用指令从当前映像 .pdata 的真实函数起点向前解析，LEA 尾位移不再误作前缀；缺函数边界、未知编码、间接／跨函数跳转及重叠代码的在途旧 thunk 消费仍保留原来源，常规延迟槽转换不变。B 已处置原子引用／列表／实体 Map 和完整 holder 的历史查询均已编入，仍以准确来源、旧进程结束及实际 ACK 为准。当前 NativeControl41 DLL SHA-256 为 2A22AEB672DB654B6FB6EE06F862ED6E9BF93D2BC561F0E934B0657187425263。集中审阅确认共享标准 delay-IAT 的首解析者来源继承会误伤组外准确 Binding。已回填槽的独立来源修补以及停组与 helper 晚回填并发时本次已进入旧 thunk 的消费分支均已写入：原返回 PC 的实际 RIP-relative call 操作数精确对应同一映像已登记 delay 槽时取当前槽来源，独立动态指针仍保留原来源；客户端轮廓帧、draw 前原过滤参数恢复、失效对象／实际卸载位置帧及静态／未完成上传的图集记录退役已随十文件补包构建通过，尚未取得客户端运行效果。已修两处确定问题：直接字段／UUID 选择器的旧历史查询错误要求 final，现与实际可变字段处置一致，容器边继续保留 final 绑定；ArrayList 非准确类型实例在原本必定 UNHANDLED 的分支前跳过无消费参数数组和装箱，既有作用域及 backing 发布认证不变。后者不能据此认定第七十四次 360 秒超时已解决；Astra 对该实际早期调用链未发现递归观察或重复数组写门，晚期现场已进入 FireBlock 初始化。准确方块位置的原 quad／索引归属沿实际地形构建与 GPU 上传保存，选中绘制范围消费独立 sprite 帧并恢复共享 atlas 当前帧；实体／第一人称火焰和 portal／gateway 的实际 RenderType／GameTime 消费接入，connection／scope／世界／选区退出与资源重载释放帧。GetProcAddress 普通导入、真实 CreateProcessW／解析器返回入口，以及已解析延迟槽／实际 bound-delay 指针接同一当前来源控制，ABI41／hostState0十三项；首窗口、自定义未接延迟、范围外动态解析及原句柄消费缺口仍保留。视觉子任务接口中断后保留已写初稿，由 Root 与 Astra 完成剩余接点。集中只读审阅发现 final NativeImage 的直接接口强转编译阻断，已按实际 Mixin 接口经 Object 修入同包。首轮 NativeControl41 DLL SHA-256 为 3DB02DAD5734275596DAED0FB75854D19344CDBA33E06E9D799BD32722286CA7；补包首次构建请求因自动审批接口 HTTP 403 未执行；用户确认继续后，统一构建通过（25 秒），NativeControl41 DLL SHA-256 为 7BFE0AC3346AA3253755C771912EE46E80DDC8171B9D91C299E37BB03B5D5BB0。补包另接通 RECORD/1 历史 Map 在当前准确 Source 已确认字段清空或完整 holder 处置后的重启 absent 查询，保留旧进程结束、未处置容器原 final／实际 key 查询和 RECORD/2 durable ACK；未确认 null／GC 不作为处置。原场景75已按原 raw-backing／early／360 秒启动；72.96 秒现场 main 等 SourceMapBridge 登记锁，持锁接续线程正在 sweep/removeBucket 清理失效弱键，未报告 Java 死锁；该场景最终仍为原 360 秒超时；332.19 秒现场 main 正在同一来源登记锁内清理弱键，未报告 Java 死锁，未取得游戏内效果，不认定超时或完整 B/C 效果已解决。

SourceMap 真实死锁修复、B 全容量保存、C 客户端世界效果与 Native/Host 导入入口、存储后端启动交接二十二文件源码包已统一 NativeControl40／Storage Native3／Java 构建通过，19 秒；首次 33 秒构建在 Storage DLL 链接后的旧摘要命令缺失处失败，改用项目既有 SHA-256 计算后完成构建。候选写入／force 失败接续及 Native 既有未观察窗口状态均已修入。独立 C 源码审阅另外确认双箱外半开度及乘客外部载具旋转仍影响准确选中对象的实际渲染，两个消费者的合成值及单目标映射修复已统一 Java 构建通过，16 秒，复用本包 NativeControl40／Storage Native3；首次 Java 补包 12 秒的多目标选择器映射失败保留。第七十四次原 raw-backing 场景最终仍在原 360 秒超时；65.85／301.2 秒的线程现场均未报告 Java deadlock，启动已从 Forge 进入 FireBlock／Bootstrap 初始化，但未取得 Done 或游戏内效果。私有来源登记改为弱身份平衡索引，避免在 STREAMS 内触发 CHM 计数 CAS 和字段门反向锁链；独立源码审阅未发现该两文件修复的具体问题。B 使用 long 长度的分块完整图像，来源实际输入／输出、候选持久化／独立回读及同 token Native 阶段接续去除 16 MiB 总量及 4／5 秒整事务门槛，NBT 单项 wire 规则仍保留；INTENT／TRANSACTION／COMMIT_ARMED 按同 Future 完整 ACK 后接续。C 世界规则经真实 connection／session／scope／维度和 revision 完整分片发布，客户端准确实体、方块实体、粒子、相机及区域雨雪时钟接入；共享 atlas 水／火、portal shader 和未知维度回调仍未实现。已登记 JNI 映像的准确 CreateProcessW 普通导入入口重新核对当前库／Binding／OwnerLink，停组后拒绝新建；JNI_OnLoad 首窗口、动态／延迟入口及 Native 原始句柄消费者仍存在明确缺口，不能据当前 IAT 就绪报完整退役。Windows x64 存储 DLL 纳入主 JAR 构建及既有 Prelaunch 提取、参数交接，Core 仍按实际需要及 TxF 条件加载。当前源码 Core／Agent／Bootstrap ABI 43、NativeControl ABI 40、Storage Native ABI 3、Journal VERSION 6；本包 NativeControl DLL SHA-256 为 F192A53ADCDC9788BB96D3C2675EA2C547860A336808648E8B0FAC4AED92B2F2，Storage DLL SHA-256 为 FDF6438D1498F3F9196C600D2B59F944D5C50449B788BD6C0C039FD5D795DE60。最新实际场景为第七十四次 NativeControl40 的 360 秒超时，不能据死锁未复现宣布实际效果通过；Astra max 对早期实际控制链未发现递归观察或重复写门；已确认一项无消费参数构造，修入下一完整源码包，未认定为超时解法。完整 B/C、候选及 GitHub 同步仍未完成。

IO／数组收尾、B 分段日志及深层 NBT、C 世界事件十四文件工作包已统一 Java 构建通过，21 秒；第七十三次原场景 65.98 秒现场确认一个 Java 死锁，启动被阻塞：Forge 扫描线程持 ResourceBridge.STREAMS，通过实际 HashMap 节点登记进入 SourceMapBridge.SCOPES 计数 CAS，等待 TaskBridge.fieldGate；外部代码接续线程已持同一字段门，反等 STREAMS。main 的日志编码同样等待 STREAMS。线程现场已保存；最终原 360 秒超时，脚本结束自建 JVM，未修改超时，未取得游戏内效果。已明确互锁，未重复采集同一死锁的后期现场。IO 的实际租约按原顺序保留链端点，异步票据先由 Scope 持有再发布，逐项归还成功才清除并扣一次请求；数组切片避免重复登记既有 backing，仅在确实保存历史时建立 before 窗口，每次仍读取当前原始位并保留 revision／来源／恢复条件。JournalLineage 物理 VERSION 6 保持每帧 65536 字节界限，完整逻辑记录按有序片段及整记录 hash 接续，全部写入后 force 才确认；旧 VERSION 1–5 原件保留，撕裂记录只暴露此前完整历史。Core、Prelaunch 使用同一读者。Storage 来源路径去除额外长度／数量／深度门槛，NBT 的读写、复制、投影和哈希改为完整迭代处理；单项 wire 规则和实际 16 MiB 图像上限仍在。世界控制补入准确位置的降水／雷击／避雷针／铜变化、真实袭击中心及原 scheduled tick 恢复；恢复入口核对当前真实调用链、服务器线程、所选队列和待恢复 tick。Core／Agent／Bootstrap ABI 43、Native ABI 39；Native 未变，复用第七十二次载荷。完整 B 保存／重启／容量及 C 客户端／原生等剩余路径仍未完成，无新候选或 GitHub 同步。

Astra max 小数组原始位图／实时来源查询与 B 无损持久文本十二文件工作包已统一 Native 链接及 Java 构建通过，Java 20 秒；第七十二次原 360 秒超时，脚本结束自建 JVM。66.26 秒主线程等待实际 FileChannel 的 ZIP 读取锁，持锁的 Forge 扫描线程处于 IoBridge.temporaryReturning 的真实调用者检查；301.12 秒主线程 RUNNABLE，在 Blocks 第 451 行的 WallBlock／voxel 初始化中完成小数组原始位图，处于 ArrayImage 控制登记。两次现场均保存，未报告死锁；最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。不能由两次 PC 或方块位置判定完整耗时及效果通过，持久文本也未取得真实保存／重启的效果结果。最多 8 个原始数组字节以 long 内联，JNI 仍逐次核对原真实调用者、实际数组类型及完整字节范围，再在原 Critical 配对内读取全部内存位；小图像免去 byte[]、malloc 和二次复制，NaN payload、signed zero、部分 primitive 元素和 boolean 原始位均保留。packed／byte[] 图像混合比较、准确切片、失败 null 图像及恢复时全字节物化接回原比较／写入／读回，成功／失败／部分写／重入／overlap、未知来源、writer／version／revision 和读传播条件保持。TaskBridge 同次查询只复用实际身份键，同锁 ThreadOrigin 单来源判断逐一比较三个真实来源集合，不创建无消费 union；Native 元素许可直接接已有完整实时数组检查，无跨动作来源或许可缓存。B 的实际 String key 等持久文本遇到未配对 UTF-16 单元时不再经 UTF-8 替换为其他字符；JournalLineage 提供 u16: 原始单元分支，Chain／References／Records／Storage／Tasks 读写及规范编码判据全部接续，正常 UTF-8 旧字段输出保持。日志物理 VERSION 5、65536 字节帧、realm、hash、force 和迁移条件保持，但逻辑文本字段增加无损编码分支；旧读者不能把带冒号的新分支当作原 Base64url 值，历史已经丢失的字符不猜测恢复。Core／Agent／Bootstrap ABI 43，Native ABI 39，DLL SHA-256 为 628E9DBB5910C463F2387CAE2B0E2914A23FFF6E86C324581077F4C8C668ECEF。第七十二次启动超时是最新实际结果；全组保存／重启／迁移、其他 B/C 接点和实际效果仍未完成，无新候选或 GitHub 同步。

Astra max 数组同窗完整记录／控制图像保护与 B 当前资格及持久记录八文件工作包已统一构建通过，21 秒；第七十一次原 360 秒超时，脚本结束自建 JVM。65.75 秒 ZIP 类资源缓冲收尾在实际堆来源锁等待，345.34 秒在 Blocks 第 872 行的 WallBlock／voxel 初始化实际数组写入守卫，后期主线程 RUNNABLE；两次现场均已保存，均未报告死锁，不能以较后方块位置判定全部启动耗时或实际效果通过。最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。ExecutionFlow 的实际所选数组槽直接进入原 Prior 链，root 的单块 before image 内联，多块精确分配并保存全部原图像；同线程嵌套写不再建无消费列表，成功／失败／部分写／重入／并发 overlap、未知来源、writer／version、读传播及恢复判据保持。数组来源沿实际有序 range 边界接续，原分区局部替换、分割及邻接合并；相同物理跨度复用 ArrayRange，但每次仍建立新的不可变 revision head，旧恢复票据不能沿用。CodeSourceBridge 保留本次根引用快照、完整真实 Map／Collection backing 发现、失败异常和发现成功后登记，只移除普通叶根的无消费 visited／queue；同次 Definition 的 image／analysis 用一次完整联合保护遍历，仍按本次实际对象身份去重。B 工作与引用去重绑定本次 Policy 身份和实际 target／recordRoot，旧代次、其他作用组资格、已释放 selector 及已变更的实际根不能压掉新的义务。精确 CHM 的 presence 与 value 取自同一次真实 get，修复两次读取之间把存在对象误报缺席的竞态；HashMap 仍沿原真实 scope。记录 INTENT 用原 65536 字节物理帧界限，移除 48000 额外限制；实际超帧记录保留来源扫描缺口并继续后续实际节点，所有引用 adapter 在 append 前同样按真实帧界限保留缺口，不能使一个过大描述把整个 durable writer 写坏，也不把未登记目标报完成。Core／Agent／Bootstrap 43、Native ABI 38，复用第七十次 Native 载荷，Java 契约与持久格式未变。全组保存／重启／迁移、其他 B/C 接点和效果仍未完成，无新候选或 GitHub 同步。

Astra max 全目录来源传播／Native 声明完整快照与 B 引用完整登记六文件工作包已统一 Native 链接及 Java 构建通过，Java 18 秒；第七十次原 360 秒超时，脚本结束自建 JVM。66.14 秒实际外部图像控制登记与 321.71 秒 WallBlock／voxel 初始化的实际数组写入记录均停在调用链内控制记录查询，主线程 RUNNABLE、未报告死锁；ControlRegistry 已是身份 hash，不以这两次样本认定线性查询或所有动作重复。本轮早晚现场均已保存，最终没有新的转换错误输出或游戏内结果，启动超时仍未解决，B/C 效果未通过。Agent 只在本次整个实际候选超集确定没有 semantic rows／control 贡献、当前实际 Class 声明来源及未决普通定义时，提前返回原无来源结果；任一来源或实际 Class 未决保留原完整展开，隐藏、不同 loader、继承／覆写及 bootstrap 候选沿既有身份范围，不按名称排除，也不跳过后续字段／值／runtimeDirect 接续。Native 声明表先在同次查询完成全部真实名称／描述符／modifiers 校验，shape 不符不再读取最终必丢的方法体；通过后仍逐方法原字节、常量、handler 与 Method／ConstMethod／pool 当前身份比对。查询内 header／table 缓冲及输出存储复用，全部退出释放，无跨动作版本／许可缓存。修复真实 pool 后段校验失败留下 entries／sizes、下一方法误当完整初始化的 bug，只有全部原校验成功才 vmReady，失败完整释放并重读。B 字段读取／创建读取／selector／Edge 保留全部实际登记，不再因 4096／65536 丢弃；保持原身份、当前来源、资格、durable INTENT／FACT／RELEASE 和关闭条件，Edge 仍最多提交 32 项但在实际待处理集合中轮转，前项忙或长期未完成不能一直饿死后项。COW 列表的原全序每项 NBT 语义哈希及精确跳过一次身份覆盖实际完整列表，不再因 4096 长度返回空 hash；单项图像和格式边界保持。真实 String key 不因 1024 字符拒绝，原实际 INTENT 48000 字节界限与全部当前 map／record／source 资格不变。Core／Agent／Bootstrap 43、Native ABI 38，Java 契约与持久格式未变；全组保存／重启／迁移、其余 B/C 接点与实际效果仍未完成，无新候选或 GitHub 同步。

Astra max Class mirror 真实字段域／有效快照与 B ForkJoin 完整队列接续五文件工作包已统一构建通过，17 秒；第六十九次原 360 秒超时，脚本结束自建 JVM。唯一保存的 66 秒现场为 ASM／Nashorn 正常类定义期间 Native 代码声明查询与实际调用目标解析，主线程 RUNNABLE，未报告死锁；本轮未保存后期现场，不以字段错域链未出现声明完整效果通过。最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。ResourceBridge 先按当前 holder.getClass 的完整实例父链、实际 modifiers 与 fieldSpan 找精确槽，只有不属于精确实例槽时才查 mirror 所代表类本身的真实静态 base／offset，修复 Class.annotationData CAS 错枚举 CodeGenerator 声明并触发无关类加载的路径。CodeSourceBridge 的字段登记、枚举及范围许可同样区分实际实例与静态域；平台 Span 保留原真实 Field 并核对 actual holder／static base，不能按 holder 是 Class 或 holder==declaring 推定静态。布局不可用、部分跨度及来源未知仍保留原保守选择与未决。ExecutionFlow 保留 FieldCapture／Slot 身份、watch、FieldModified 去重、来源与 read 传播、writer／version／overlap 及失败还原；unknown 来源按 join 单调不可消，只省去最终不可能生成完整 revision 的无消费 before／after 读取，未删除未知票据或改变来源结果。Native 原 watch／read／restore 已正确按字段 modifiers 区分两域，故复用原载荷。B 共享 ForkJoin submission 队列按本次实际 queues 数组每轮至多 64 位置接续，换实际数组重开游标，仍取得原实际 source 锁、核对 owner／slot／array、admission 与原资格；跨度按实际数组容量，不再按 4096 拒绝。owner 安全点保持真实当前 Thread／owner、request.gate、tryRemove、8 请求轮转与全部失败／退役事件，同样按真实 array 容量。标准实际 pool 表及缺席查询保留完整实际队列／数组读取和读后身份，不再按 32768／65536 拒绝；原已支持 BlockingQueue 仍持真实锁及原边界／许可，不因 4096 大小拒绝整动作。完整缺席查询不使用缓存结果，未知后端和其他持久格式边界仍未结清。Core／Agent／Bootstrap 43、Native ABI 38，复用第六十六次 Native；Java 契约与持久格式未变，全组保存／重启／迁移及 B/C 效果仍未完成，无新候选或 GitHub 同步。

Astra max 缓冲／Io 当前来源链与 B 共享来源及完整登记九文件工作包已统一构建通过，17 秒；第六十八次原 360 秒超时，脚本结束自建 JVM。66 秒现场为 Class.annotationData 底层 CAS 引发的 represented-class 字段枚举／Nashorn 类加载与 Native 版本查询，321 秒为 Minecraft WallBlock／voxel shape 初始化的字段写入正常收尾；主线程均 RUNNABLE，未报告死锁。最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。TaskBridge 在本次进入操作直接合并全部真实 scoped 来源，不再 Set／array／Set 重建及重复查询已合并 Io；native 来源筛选、unknown 和完整真实栈回溯保留。ResourceBridge 已登记 storage 的状态操作仍立即取得来源并逐个记录，未登记 storage 的读写只在实际有效 span、外层覆盖核对后需要数据票据时查询，同一次实际借用的 views 共用该次完整查询；IoBridge 的本次停止核对及紧接连接登记共用同次实际来源，许可、storage 判据、正常／失败票据与 active／observing 收尾不变。Buffer 查询只保存实际前两帧且完整计算深度，未跟踪帧共享零长度结果；不缓存跨动作许可或来源，不整体跳过平台或 actor。B 的失败 Load／LoadRoot 保留准确原事件并轮转，Load 的当前失败原因随事件保存；日常预算、全组未决与同一关闭生产屏障保留。共享 Source 为各实际 canonical 组分别保存清理进度，不能以第一组的完成代替其他组；新的真实 root 事件重开该组已完成遍历，全部已登记组完成后才释放来源资源登记。标准 waiter 链保留实际节点／thread／循环及当前 waiterCount 检查，不按 4096 制造永久捕获缺口。TAGS 的原弱身份目录、真实 SAVING 重入登记及内存／保存义务不再按 65536 拒绝；原归属冲突、AMBIGUOUS、图锁、序列化计数、实际工作与意图、提交资格和文件身份保留。原已验证 NBT 图定位保留全部实际 Selection，不按全来源 4096 条记录拒绝；持久图像、单意图描述符、解码格式和共享池其他边界未改。Core／Agent／Bootstrap 43、Native ABI 38，复用第六十六次 Native，Java 契约及持久格式未变；全组保存／重启／迁移及 B/C 效果仍未完成，无新候选或 GitHub 同步。

Astra max 字段内存记录／同窗完整来源读取与 B CF 长来源栈接续三文件工作包已统一构建通过，21 秒；第六十七次原 360 秒超时，脚本结束自建 JVM。66 秒现场为 Jar manifest 文件读取中的缓冲当前来源查询，323 秒现场为 Minecraft Blocks 初始化期间实际类资源解压读取；主线程均 RUNNABLE，未报告死锁。最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。ExecutionFlow 用已有 Prior 自身链保存全部初始槽及动态 include，单字段捕获保存实际 Slot／FieldCapture，delegated 不再分配空集合；正常和失败写入的 writer、overlap、version、来源合并及 revision 收尾保留。同一 HEAP 锁内直接以本次实际 carrier／Heap 完整重读全部相关槽、region、pending write，公开边界仍复制 Module[]，不缓存跨动作来源。CodeSourceBridge 单对象登记保留实际 nest caller 核对，数组 backing 直接登记原算法所选同一叶对象；TaskBridge 现行调用者、许可和控制目录哈希实现未改。B 为每个实际 CF 来源保存清理游标，单次仍至多 128 节点；真实 head 或相邻 next 变化后重读，CAS 后重新从真实链核对。解除前仍新鲜完整遍历全部标准实际来源，循环、非标准节点及目标仍在均保持未决；不以 4096 节点拒绝已读标准链，完成及任务释放时清空游标。不改共享池／等待者的其他边界，不能据源码宣称全组保存／重启／迁移或 B/C 效果完成。Core／Agent／Bootstrap 43、Native ABI 38 和持久格式未变，本包复用第六十六次已构建 Native；尚无新候选或 GitHub 同步。

Astra max 原生版本完整比对去重与 B 来源／异步完整登记四文件工作包已统一 Native 链接及 Java 构建通过，Java 构建 21 秒；第六十六次原 360 秒超时，脚本结束自建 JVM。67 秒现场为 Mod 发现／版本解析期间真实定义图像的控制对象登记；323 秒已到 Minecraft Blocks／AirBlock／BlockState 初始化中的字段内存写入记录与 TraceList 控制 backing 发布，两次主线程均 RUNNABLE，未报告死锁。最终没有新的转换错误输出或游戏内结果，启动超时未解决。Native 对已经登记的不可变图像建立字节哈希查找，仍比较完整字节、实际 Class／loader／hidden 绑定，扩容保持最新优先、分配失败保留原列表；本次已读 JVMTI 方法名／描述符借给当前 ConstMethod 核对，其他入口仍取得真实选择符。非 hidden 的同一方法／实际池内复用已证常量关系，失败清表；hidden 仍逐根实时核对 self。当前实际池的 UTF Symbol 索引每次命中仍读实际 tag／Symbol，失配／未命中重读，换池及查询退出释放；实际字节、分支、bootstrap、异常表及前后版本／池身份检查保留，不存在跨动作许可或版本缓存。B 的真实捕获事件、Load、LoadRoot 采用完整队列并保留当前失败事件，日常仍按原 256／128 等批次处理。任务、worker 实际来源绑定、CF 输出／依赖索引、全部真实后继及 clone 关系不再按 8192／32768／512 截断；来源登记、SavedData 已读 holder 及原迁移关系不再按 4096 截断。集合仍只记录原实际对象／真实关系，关闭、事实落盘和未决查询保留；队列中的 Load 按整个实际组选取未决。实际 Class 来源同名同字节冲突时保留旧登记，为另一真实 Class 建立本对象登记，不能覆盖旧来源或在重启时以另一 loader 的同名类代替。原 NBT 图／持久图像及其他记录、工作登记与池／等待者的剩余边界仍在；全组保存／重启／迁移及 B/C 效果未完成。Core／Agent／Bootstrap 43、Native ABI 38，Java 契约与持久格式未变，无新候选或 GitHub 同步。

Astra max 图内解析／实际 NEW 构造父链与 B SavedData 缓存重启及保存身份接续五类完整包已统一构建通过，23 秒；第六十五次原 360 秒超时，脚本结束自建 JVM。唯一保存的 93 秒线程现场在 Nashorn 代码生成的真实类加载／DefinitionBridge 缓冲图像复制中，经 ByteBuffer limit 收尾做 ResourceBridge 平台调用者查询；主线程 RUNNABLE，未报告死锁。本轮没有保存后期线程文件；最终没有新的转换错误输出或游戏内结果，启动超时未解决。Agent 的一次 expand 独立持有真实 initiating loader 成功查询、当前 ClassNode 方法表、真实声明／继承选择和成功调用目标；实际调用形状、caller 图像身份及 receiver 集合分别索引，嵌套退出恢复外层并释放，不缓存缺失类或跨图许可。既有 bound／unavailable 保留；Native 候选仍核对原实际字节、常量、异常表，仅以本图已读取真实 method headers 排除必定不符的声明形状。第六十四次 Native 样本不证明同一版本重复查询，也不能解释全部超时。CreationBoundary 先按原最终必需的本方法 NEW 筛候选，证实实际 receiver 后再查询实体父链；完整父链采用本次 weave 缓存和迭代，不以 48 层截断，原 producer、资源、数组及静态初始化织入保留。B 对已登记 SavedData 原意图，按当前实际维度的 manager／cache／目录／键／holder 重新绑定，不调用读者、不初始化未知对象、不按 UUID 猜归属；原代码身份不符、真实 cache 条目缺失或绑定失效继续未决。cache 的正常原 mutation 不重试，代码不符的既有 Source 不抵消 expected 原来源。已知保存意图按代码、保存代次、文件身份和原始图像哈希核对，避免仅同代次抵消另一图像；历史协议与提交资格／源写门保留。任务／来源容量、全组保存／重启／迁移与 B/C 效果仍未完成，Core／Agent／Bootstrap 43、Native 38 和持久格式未变，没有新候选或同步。

Astra max 启动图／图像重复计算修复及 B 捕获图八类完整包已统一构建通过，21 秒；第六十四次原 360 秒超时，脚本结束自建 JVM。86 秒现场为 Nashorn 代码生成期间真实类加载中的 Native 代码版本查询；349 秒现场已到 CreationBoundary 实体父链类资源的真实解压读取，两次主线程均 RUNNABLE，未报告死锁。最终没有新的转换错误输出或游戏内结果，启动超时未解决。Agent 的返回摘要跨类图只纳入有实际返回值的调用；所有 root 方法和调用实参、字段／数组副作用仍按原指令分析，无返回值的调用继续 used(...)。只有尚未解析的实际虚调用才做 receiver 帧分析，不前移无 Module 的全图判断；当前图内反向调用工作队列保留递归收敛，只在被调用摘要变化时重新计算对应调用者。图像选择先找本次真实 loader／name 候选，再比较当前字节规范化哈希；同次执行／字段／常量来源取得共用一个完整捕获，不复用跨动作匹配或许可。命中 patch 的索引在本方法／patch 建立一次，接受图中的无内容来源数组共享，避免每个普通 opcode 新建控制数组。B 任务布局只保存当前实际 Class 的全部非静态引用字段，实际取值仍在本次捕获读取；已支持的 JDK 委托图以真实对象身份去重，不按 32 对象／96 声明槽截断，CF 聚合保留原真实数组复制，不再因 512 输入丢弃事件。无法取得实际字段且已有真实相关来源时保持捕获未决，不把不完整读取作为完整生产关系。单目标 wrapper 的实际完整不可变布局不再按 32 字段拒绝，目标唯一、来源及不支持成员条件保留；fieldReads 的既有 4096 满额不再默默返回，而报告真实捕获缺口。标准 NBT 记录子图持原 graph 读锁及 TAGS 锁核对全部实际节点，不按 4096 节点截断，仍核对原 owner／canonical subject、封闭 backing、serialization、ambiguous、foreign 及 opaque 条件。任务／来源登记及事件队列等容量、全组保存／重启／迁移和 B/C 效果仍未完成；Core／Agent／Bootstrap 43、Native 38 与持久格式未变，没有新候选或 GitHub 同步。

B 已接受加载事件及组对象事件接续／定义控制对象筛选三类完整包已统一构建通过，19 秒；第六十三次原 360 秒超时，脚本结束自建 JVM。90 秒现场为 Mod 发现／类资源读取中临时直接缓冲的 IoBridge 平台调用者查询；324 秒现场到 Forge 标签注册初始化的真实类文件解压读取，两次主线程均 RUNNABLE、未报告死锁。最终没有新的转换错误输出或游戏内结果，启动超时未解决。RecoverySources 为从原队列取出的准确 Load／LoadRoot 保留单一当前位置，来源登记、同一实际实体关联或真实 holder 登记失败时保持该事件，成功后才释放并更新状态。加载来源登记的当前等待原因归属于该实际事件；不因临时未观测的类定义或登记容量等待而丢掉已接受对象。ProRuntime.linkLoaded 返回原实际关联是否接受，原归属冲突、域不符、实体登记容量及保护失败仍保留原 coverageGap，未绕过它们。pending 及原队列中的组对象参与未决查询；关闭入口使用同一生产者屏障，先处理已接受事件，仍未处理完时 Source.close 报 EVENTS_PENDING、保留对象。原 4096 队列和来源登记上限未取消，未接受的溢出仍保留旧失败，不作容量全部收口结论。DefinitionBridge 查询先按实际类型排除既非私有 Scope 也非 byte[] 的对象；先单独核对本次实际 liveScopes 数组，可能匹配的 Scope／bytes 继续原全部实际作用域链的身份比较，没有新缓存、锁或许可。Core／Agent／Bootstrap 43、Native 38 与持久格式未变，B/C、O02／O05 未完成，没有新候选或同步。

B 引用／内存记录大容器接续及调用者查询三类完整包已统一构建通过，25 秒；第六十二次原 360 秒超时，脚本结束自建 JVM。92 秒现场在加载类的代码图构造／DefinitionBridge 控制对象查询；343 秒现场已在 FireBlock 注册初始化的真实类文件解压读入，两次主线程均 RUNNABLE、未报告死锁。最终没有新的转换错误输出或游戏内结果。RecoveryReferences 的真实 Map 改用既有节点游标，单次至多 32 个节点／桶位置；CopyOnWriteArrayList 直接读取本次真实不可变 backing 数组，每块至多 32 个元素，实际数组变化后从当前数组重读，不再以 4096 元素拒绝整份扫描。RecoveryRecords 在原 HashMap 写门／ConcurrentHashMap 弱一致通路下每次至多 64 个节点／桶位置，不再取 8192 上限条目快照，失败和容量等待保留准确节点。当前字段身份、真实容器、HashMap 修订及来源变更号参与完成状态；整轮完成且来源未变化才登记完成，后续变更重新未决，暂停时保留位置。已完成的一轮在来源及实际字段、底层数组仍相同期间可查询；新一轮未读完不会伪造新的完成。原准确目标筛选、实际归属、键类型、落盘意向及解除／事实确认规则保留；65536 义务登记及来源／异步捕获上限、列表持久图像上限仍未收口。TaskBridge 按原帧过滤顺序选实际调用类，仅在原需要比较方法名的分支读方法信息，不改变许可和帧选择。Core／Agent／Bootstrap 43、Native 38 与持久格式未变，B/C、O02／O05 未完成，没有新候选或 GitHub 同步。

代码来源私有账本容器两类完整包已统一构建通过，18 秒；第六十一次仍为原 360 秒超时，脚本结束自建 JVM。唯一保留的线程现场为 323 秒，主线程 RUNNABLE，实际在工厂父链资源读取中的缓冲写入记录／通用控制调用者查询，未报告死锁；没有 70 秒现场或单独后期文件，最终没有新的转换错误输出或游戏内结果。CodeSourceBridge 的 31 处内部 HashMap 构造改为私有 LedgerMap；直接构造仅接受真实 CodeSourceBridge 类，登记仅接受该私有构造类及本次准确实例，先建立原弱控制目录再构造账本。复制构造先登记新准确实例，再按原语义 putAll；普通 Map、clone 及保护复制不取得内部 Map bit。原 BackingBridge 控制和数据检查仍先于原 SourceMap 内部容器分支，业务 Map／数据、实际对象及来源查询保留。Helper 在 JNI 守卫发布前定义；原死亡弱键逐目录移除、当前图像／写入历史和来源获取方式不变，不保留跨动作许可。Core／Agent／Bootstrap 43、Native 38 与持久格式未变，尚无游戏内结果或候选，B/C、O02／O05 未完成。

缓冲状态空查询／真实 HashMap 相邻节点接续两类完整包已统一构建通过，23 秒；第六十次原 360 秒超时，脚本结束自建 JVM。175 秒现场为 Main 第 116 行正常 CrashReport.preload 的资源读入，实际开销在代码来源桥清除死亡弱键时的通用 HashMap 守卫查询，RUNNABLE、未报告死锁；本机 1.20.1 缓存源码已核对 preload，未把该帧当作新异常。后期请求时 JVM 已退出，没有后期线程文件。最终没有新转换错误输出或游戏内结果。无 storage 登记且不读写数据的缓冲状态操作，原来源捕获结果不被原读／写或 storage 分支消费，当前仅删除这一重复查询；平台调用者认证、操作许可、本次实际执行记录、正常／异常收尾保留，有 storage 或数据读写时继续原来源捕获和缓冲票据。读方法集合在原提前初始化布局中一次定义。HashMap 游标保留实际前驱，只在原门内核对当前相邻节点并按实际来源解除，不为每个节点从桶头重复搜索；查询中真实修订变化后重新读当前表，自己解除后保留准确位置，树变化后重读当前桶。ConcurrentHashMap 的原实际锁及节点查证保留。Core／Agent／Bootstrap 43、Native 38 与持久格式未变，尚无游戏内效果或新候选。

B 真实 Map／Set 节点容量及持有者图接续六类完整包已统一构建通过，25 秒；第五十九次原 360 秒超时，脚本结束自建 JVM。232 秒现场为注册初始化 Mixin 快照保护中的实际控制目录扩容；323 秒现场仍在注册初始化，为工厂类型真实资源读取，均为 RUNNABLE，未报告死锁。最终没有新的转换错误输出，没有游戏内结果，B/C、O02／O05 未完成。HashMap 首次接管不再按 8192 大小拒绝，每次在原实际门下绑定至多 128 个节点／桶位置，保留真实 table／next 的弱身份和修订；先前无门的节点调用仍按原 active 计数排空后才 ready，变化后重新绑定。Core 图遍历按真实桶及 next 分批读取，不复制整份 Map；每批保留位置及原 HashMap 修订，ConcurrentHashMap 继续实际 tabAt／扩容表／树节点通路。终结仅解除本次仍存在且按原实际来源选中的节点，树退化后重读当前桶，保留 NBT 图认证及实际计数，未调用被停 Mod 的 hashCode。HashSet 从真实 backing Map 接同一路径，并核对未换 backing，键与值的实体／记录归属均查证。持有者发现的 Map／Set／数组／List／NBT／POJO 保留暂停父游标，队列满先处理已发现子对象，字段或元素只在成功读入后前移；实际来源修订变化后重新从真实 holder 开始，失败保留位置。ArrayList 只读入口持原真实门、不因观察自行增加修订，读者计数按实际 Map／List 对称释放；COW List 仍用真实私有锁与 backing 身份。新布局在打开原 bootstrap 访问后、守卫发布前完成初始化。Core／Agent／Bootstrap 源码 ABI 43，Native 38 复用，持久格式未变。Source／异步捕获及旧引用／记录快照上限、保存重启和迁移仍待收口，没有游戏内结果或候选，B/C、O02／O05 未完成。

资源真实调用者查询／bootstrap 候选数组索引两类完整包已统一构建通过，18 秒，已并入后续 B 容器完整源码包集中检查。资源平台认证仍每次按原 StackWalker 选项取得本次真实帧并核对实际 Class／方法名；固定查询函数逐帧寻找原第一个非 ResourceBridge nest 帧，避免 filter／findFirst／Optional 的临时组合，也不首次链接该查询 lambda。普通 bootstrap 索引仍每张图从本次实际类集合重新构造、筛选真实定义加载器及非隐藏类并保护新集合；私有整数桶及后继位置直接指向本次实际 Class，查找仍按准确全名且较后候选先行，不构造逐类包装节点，不复用跨图候选或许可。Core／Agent／Bootstrap 42、Native 38 与持久格式未变；没有新游戏内结果或候选，B/C 未完成。

缓冲实际字段布局／B 共享 Map 遍历接续两类完整包已统一构建通过，24 秒；第五十七次原 360 秒超时，脚本结束自建 JVM。78 秒现场为 Forge 类资源读取中的缓冲平台真实调用者查询；307 秒现场已到火焰方块注册，为工厂类型资源读取中缓冲 limit 的来源捕获，均为 RUNNABLE，未报告死锁。没有方法过大错误输出，仍无该路径的效果复查或游戏内结果；B/C、O02／O05 未完成。缓冲 hb 字段的原按类层级查询移至同一实际 Class 的布局记录，顺序及原 TYPES 筛选保留，每次仍读取实际缓冲字段和父对象，不保留数组值或复用权限。Map 当前条目快照及位置保留，每批至多 32 个条目，为两个实际引用预留队列位置；子对象先处理后接续父 Map。HashMap 在原实际写门内读修订及快照，修订变化后重新取当前条目；ConcurrentHashMap 保留原弱一致快照通路。全条目遍历后才按原实际节点／存储哈希及 NBT 来源规则解除该组节点，失败保留游标。原 8192 快照上限、首次观察登记及整 Map 节点解除仍待完整容量收口，不据此声明 B/C 或 O02／O05 完成。Core／Agent／Bootstrap 42、Native 38 与持久格式未变。

静态字段紧凑票据／实际工厂层级／缓冲空记录六类完整包已统一构建通过，26 秒；第五十六次原 360 秒超时，脚本结束自建 JVM。67 秒现场为早期模块资源读入中反复查找缓冲 hb 字段，300 秒现场已到火焰方块注册的真实类文件读入，均为 RUNNABLE，未报告死锁。没有方法过大错误输出，但仍未取得该插桩路径的效果复查，没有游戏内结果。静态写值保留在原操作数栈，仍使用原写门及票据，在原方法直接 PUTSTATIC；自有符号字段从真实直接调用类取得 Class，外部符号仍传实际 Class，完整字段名／descriptor 合并传入再按原声明解析。拒绝时只丢弃原待写值，正常及异常票据收尾保留，原 final 写入不移至辅助方法，不增加按线程隐式取票据的入口。共享异常收尾同时区分构造前后状态及原 catch 顺序。工厂查询保留本次变换原缓存，优先读同一加载器实际已发起类的真实父链，未发起类继续原资源分支；当前类用本次声明，未主动加载或初始化目标，也未缓存跨变换权限。BufferOperation 只在读到或继承非空实际来源时创建原身份集合，含 null 的原来源仍保留；空记录不触发集合的内部 backing 发布。第五十五次原 360 秒超时，最终实际报 Blocks.<clinit> 的 FieldWriteBoundary MethodTooLargeException，脚本结束自建 JVM；165 秒现场为类文件读取中的缓冲来源捕获，329 秒现场已到火焰方块注册，为工厂类型查询的真实资源读入，均为 RUNNABLE，未报告死锁。没有游戏内结果、新候选或 GitHub 同步；B/C、O02／O05 未完成。Core／Agent／Bootstrap 源码 ABI 42，Native 38 复用，持久格式未变。

当前字节对应冻结元数据／小数组区间槽查询两类完整包已统一构建通过，16 秒，第五十五次按上述 Blocks 字段插桩失败与原超时结果结束。每次元数据查询仍重新序列化当前实际树并保护新字节；只有全字节与该历史 current 的受保护快照完全相同，且实际方法／指令 opcode／异常处理器布局按原规则核对后，才使用该快照自己的冻结行。字节变化继续完整解码、保护新图和重新构造元数据，历史 before／after 及来源比较不变，不复用跨查询许可。实际数组区间比既有槽表小时按该区间的准确键查询，否则保留原槽表筛选；本次新鲜来源、原槽身份与写入历史不变，没有遍历无关槽。第五十四次原 360 秒超时，脚本结束自建 JVM；161 秒现场为类文件读取中 BufferOperation 的内部集合／控制登记，326 秒现场已在世界注册初始化的 Mixin 应用中，为元数据图保护，均为 RUNNABLE，未报告死锁，没有游戏内结果。前次字段大小错误尚未取得效果复查，B/C、O02／O05 未完成，没有新候选或 GitHub 同步。Core／Agent／Bootstrap 41、Native 38 与持久格式未变。

直接方法头复制／来源图分批接续八类完整包已统一构建通过，22 秒；Core／Agent／Bootstrap ABI 41，Native 38 复用，持久格式未变。第五十四次按上述原启动超时结果结束。MethodNode 的原头部字段依本机 JDK 17 实际定义直接赋值，内容与原重放相同，避免该读取的反射写入回调；当前类、原帧和元数据比较保留。各已登记来源图在原 256 单位预算内轮转，数组／列表每块至多处理 32 个元素，游标及暂停的父容器保留，已发现子对象先处理，满队列不会丢掉父容器的未处理位置。数组写入票据只覆盖本块实际区间，原历史及读回保留；在同一实际写门中读取原数组变更记录，变化时重回起始位置，收尾后保存真实修订。ArrayList 读取同一实际门的修订，CopyOnWriteArrayList 使用真实 backing 身份，组外变化后重新处理原列表。反射或资源失败继续保留未决，不据分批代码声明 O02／O05、全部容量或 B/C 完成。第五十三次原 360 秒超时，脚本结束自建 JVM；86 秒现场为 Forge 文件读取中的缓冲来源捕获，331 秒现场为方法头的反射字段赋值／调用来源捕获，均为 RUNNABLE，未报告死锁。没有前次方法大小错误输出，但没有到该故障的效果复查，没有游戏内结果、新候选或 GitHub 同步。

字段写入插桩体积／方法头读取／bootstrap 候选索引三类完整包已统一构建通过，15 秒；Core／Agent／Bootstrap 40、Native 38 复用，持久格式未变。第五十三次按上述原启动超时结果结束。每个方法复用一组临时槽，同一原 catch／finally 覆盖顺序的写入共用异常收尾；实际写入留在原类、原方法中，逐次许可、正常／失败发布和门释放保留，异常收尾按原顺序回到原处理器。Mixin 方法头直接复制原头部字段，不再先复制整段方法体后清空；实际当前类读取与帧比较不变。每张图的 bootstrap 名称集合仍重新取得并按实际定义加载器／非隐藏类筛选，私有桶索引按准确全名查找、保护新索引，未缓存跨图许可。第五十二次实际报 SoundEvents.<clinit> 的 FieldWriteBoundary MethodTooLargeException，原 360 秒超时，脚本结束自建 JVM；144 秒现场为 Mixin 方法头读取的整段方法复制，未报告死锁，后期请求时 JVM 已退出，没有后期线程文件。该轮没有前次栈帧越界输出，也没有游戏内结果；B/C、O02／O05 未完成，没有新候选或 GitHub 同步。

bootstrap 发起类查询／钩子栈映射完整工作包已统一构建通过，26 秒，第五十二次按上述字段插桩失败与原超时结果结束。原 75 个控制原生入口提前绑定扩展为含新查询的 76 个入口；普通按名查找候选每张图从实际 bootstrap 发起类取得，再按实际定义加载器及非隐藏类筛选，旧完整 bootstrap／全 VM 枚举和隐藏定义通路保留。玩家跨维度、保存、物品及其他绘制钩子生成的帧统一使用 F_NEW，已有压缩帧按原局部值／栈状态展开，生命周期 lease 的实际槽位和原正常／异常收尾保留；快照仍读取实际帧，不用跳过帧来遮住原越界。Core／Agent／Bootstrap 40、Native 38，持久格式不变。第五十一次原 360 秒超时；停止宽限中实际报 PlayerLifecycleHooks$DimensionChange 的 PostApply 快照读取 ArrayIndexOutOfBoundsException（403／212），main 已退出，后台仍运行，脚本结束自建 JVM。100 秒早期现场为调用图的全 VM bootstrap 查询；364 秒现场处于原停止宽限，后台为候选表构造，含 DestroyJavaVM，未报告死锁。没有游戏内攻防结果、新候选或 GitHub 同步，B/C 与 O02／O05 尚未收口。

Mixin 历史元数据行／无发布成员查询完整两类工作包已统一构建通过，16 秒，第五十一次按上述实际 Mixin 越界与原超时结果结束。每份新 Snapshot 同时保存自己那一版的不可变元数据行并保护该图像；当前活类仍每次重新序列化、解析及比较，历史 before／after 使用各自冻结行，未跨快照复用当前判定。成员构造仍先认证原入口、真实父对象并查询当前成员及上下文；两者均为空时按原逻辑返回，实际发布时再采集真实调用来源。第五十次仍在原 360 秒时限内未到游戏内动作，脚本结束自建 JVM；89 秒现场为 Forge 扫描时的原文件读入，313 秒现场已在 Minecraft 世界注册初始化的 Mixin 应用中，为成员创建的调用来源捕获，均为 RUNNABLE，未报告死锁。没有游戏内攻防结论、新候选或 GitHub 同步，B/C 与 O02／O05 尚未收口。

调用图集合合并／Mixin 元数据读取完整三类工作包已统一构建通过，14 秒，第五十次按上述启动超时结果结束。接收者与来源值的不可变集合在并集／交集结果相同时沿用既有集合，空来源值不构造空 Map／HashSet；参数位集与原类型、来源、未知及必需条件判断不变。Mixin 元数据每次仍重新序列化并解析当前实际类、核对原方法／指令／异常处理器数量、保护这次新图像，并比较全部原元数据和帧位置；仅去掉该查询未使用的 Body／Instruction 历史构建，真实变换快照继续保留全部原指令身份及修改历史。第四十九次仍在原 360 秒时限内未到游戏内动作，脚本结束自建 JVM；77 秒现场为调用图接收者合并中 HashSet 构造的作用域登记，323 秒现场为 Mixin 成员元数据读取的完整类快照构造，均为 RUNNABLE，未报告死锁。没有新游戏内结论或候选，B/C 与 O02／O05 尚未收口。

原生控制入口提前绑定完整包已统一构建通过，16 秒，第四十九次按上述原启动超时结果结束。第四十八次在 premain 提前失败，SERVER_EXIT=1，实际 Throwable 保存的 1024／1024 帧显示：unsafeControlScope0 首次原生函数解析进入 NativeLibraries.find 的 ConcurrentHashMap 迭代，Unsafe.getReferenceAcquire 再进入 beginHandleRead／unsafeControlScope0，形成真实递归。当前在已认证的实际 bootstrap NativeControl 安装中，通过原 JNI RegisterNatives 将其全部 75 个既有私有入口绑定到同一 DLL 的实际实现地址，完成后才发布 JNI／Unsafe 守卫；不执行模拟调用，不改变业务入口、来源或写入权限。DLL SHA-256 00E78DC81D70C1A6C1667F4D70D8E87DD75B233509B5A16DE119B1DEC1EA411E，ABI 与持久格式未变。B/C 与 O02／O05 尚未收口。

控制写入／原生库调用者查询完整工作包已统一构建通过，15 秒，第四十八次已按上述 premain 递归失败结束。受控对象仍先按准确身份查询；原内部写入和自身账本写入的既有许可先判断，随后保留实际组控制 writer 或原生库实现调用者认证。两种调用栈查询改为预定义的直接迭代函数，原筛选、真实类／模块／加载器／代码来源判断不变。第四十七次原场景仍在 360 秒时限内未到游戏内动作，脚本结束自建 JVM；143 秒现场为内部记录写入中的原生库调用者查询，383 秒现场处于原停止宽限内，已到 Minecraft 世界注册初始化的真实文件路径／数组观察，均为 RUNNABLE，未报告死锁。前一批内存调用者链接递归修补已统一构建通过，15 秒；其中 installingCaller 是安装调用者，组控制 writer 的直接迭代改动属于当前批。原第二十六／三十六／三十八次数组失败及更早安装错误尚未全部结清。Core／Agent／Bootstrap 39、Native 37 与持久格式未变，B/C 与 O02／O05 尚未收口，没有游戏内强度结论、新候选或 GitHub 同步。

第四十二次仍在原场景 360 秒时限内未进入游戏内动作，脚本结束自建 JVM；73 秒完整现场为真实文件读入的调用来源捕获，311 秒完整现场为 Mixin 成员元数据快照的对象图保护，两个现场均为 RUNNABLE，未报告死锁。后者的实际代码对同一方法共享的 parsedLabels 表按每条指令重复遍历整张对象图。完整修补包已统一构建通过，17 秒：每张新快照合并原根及每个非空方法的标签表，只按准确对象身份遍历一次联合图；实际类树仍重新序列化、指令／处理器身份与修改历史不复用。ExecutionFlow 四个只读查询先读取当次真实线程局部帧，无帧只返回原空值，有帧继续认证真实 CodeSourceBridge，实际来源和写入判断保留。第四十三次按上述安装提前失败结束；实际强度、原数组异常、B/C 与 O02／O05 尚未收口。

第四十一次仍在原场景时限内未进入游戏内动作，脚本结束自建 JVM；日志已到 Launching target forgeserver，没有前次 MISSING_FRAME_TYPE 输出。168 秒完整现场为真实文件读入中的控制目录死亡键回收，仍为 RUNNABLE，未取得后期线程文件。控制目录整包已统一构建通过，15 秒：一批真实死亡键只做一次实际调用类与固定目录认证，空队列不增加认证；登记、准确身份查重、扩容与新节点发布合并在同一认证入口，旧 backing 的弱保护与原构造角色赋值保留。第四十二次按上述结果结束，没有游戏内强度结论。

第四十次 132 秒完整现场已在 Forge 扫描包的真实数组观察中运行，未出现前次锁环；后续 MixinInfo 转换报告 `MISSING_FRAME_TYPE:org/objectweb/asm/tree/ClassNode`，原场景最终超时，脚本结束自建 JVM。后期请求时 JVM 已退出，没有后期线程文件或游戏内结果。栈帧类型接续及内部角色只读查询整包已写齐：采用当前加载器已发起的实际类型层级，缺失资源时同加载器解析并关闭初始化；内部角色仍按准确对象的原弱身份 bit 查询，构造登记与写入判断不变。已统一构建通过，16 秒；第四十一次已按上述超时结果结束，原数组失败、B/C 与 O02／O05 尚未收口。

第三十九次 102 秒完整现场确认 SCOPES 字段门／HEAP 死锁：main 持 HEAP，新 Heap.slots 的 HashMap 构造经 SourceMap.born 与 ConcurrentHashMap 计数更新等待字段门；接续线程持字段门，在该计数的原生 CAS 观察中等待 HEAP。原场景最终超时，脚本结束自建 JVM，未触发原始栈溢出抛出诊断，没有游戏内结果。当前四类完整修补包已写齐：ExecutionFlow 的五处 HashMap 改用私有 TraceMap，实际构造入口与真实创建类登记准确对象的独立角色；SourceMap 的业务作用域／节点登记避开这些记录容器，原 BackingBridge 控制／元素判断和业务数据观察保留。新类在 JNI 观察守卫发布前完成定义准备。完整工作包已统一构建通过，17 秒；第四十次已按上述实际结果结束，Native 37 复用。B/C、原数组栈溢出与 O02／O05 均未收口。

第三十七次越过原 premain 失败点，113 秒现场在 Log4j 类转换的实际版本核对中；随后到达游戏层 Mixin 准备，因 `ACTUAL_ASM_LIBRARY_UNOBSERVED:org.objectweb.asm.tree.MethodNode` 提前退出，SERVER_EXIT=1。该错误已定位到 memberMetadata 查询未登记的库角色。启动接续整包已写齐：按已有父树的真实方法／字段成员身份确定类型，原来源与修改历史保留；VM 常量池头按真实布局一次读取，字符串常量查找每次重新批量读取活的 tags／slots，并再次核对选中 UTF 槽的当前 symbol 身份，原活动版本与语义检查保留。已统一构建通过，18 秒；第三十八次原场景再次在 premain 数组 BEGIN 栈溢出后提前退出，SERVER_EXIT=1，未到 Mixin 修补点或游戏内动作。日志的 VM 活动帧只有 Throwable.printStackTrace，原始递归未定位；首个实际栈溢出抛出时的直接 VM 帧诊断已统一构建通过，15 秒，第三十九次因上述真实锁环超时，脚本结束自建 JVM。该诊断不授予来源、执行或写入权限。

原生空目录筛选整包已统一构建通过，15 秒：首个非空指令来源在不可变镜像发布锁内置位 presence，空目录不再展开原本全部为 false 的 Native 帧查询。原 Java 作用域／Native binding／library 来源、执行计划和实际数组快照／历史继续保留。第三十六次 premain 提前退出，数组 BEGIN 两次 StackOverflowError 后 libinstrument 提交类字节数组失败，SERVER_EXIT=1；没有线程快照或游戏内结果。现有失败日志改用 JVMTI 直接输出失败 JNI 边界仍活动的实际帧，不调用 Java 异常格式化，随后抛回原异常；该修补已统一构建通过，15 秒，第三十七次因上述实际 Mixin 错误提前退出，未进入游戏内攻防动作。原数组栈溢出尚未结清，B/C 与 O02／O05 未收口，尚未发布候选或同步 GitHub。

调用图版本核对工作包已写齐并统一构建通过，18 秒：每次实际图分析按普通声明名／隐藏 Class 身份建立当次候选索引，再核对原加载器及真实定义；最新候选的声明形状与全部普通方法匹配时达到原最大选择上界，不再继续查询旧候选，局部匹配仍继续。Native 镜像在发布前保存固定指令偏移和按方法名／描述符排序的索引，版本、帧来源和执行计划查询共用准确方法定位；活动 VM 的字节码、常量、异常表及版本始终重新核对。原方法元数据改为按真实 VM 布局批量读取当次 ConstMethod 头和异常表，不缓存成功结论。第三十三次仍在原时限内未到游戏内动作，脚本结束自建 JVM；157 秒完整现场为日志初始化时的真实包解压，330 秒现场在 ModFileParser 类转换中建立候选列表，ArrayList.add 重新进入 SourceMap 的角色查询。现在完整候选索引改为私有节点／固定桶数组，避免 JDK 列表写入回调；同桶碰撞仍按原名称／隐藏身份／加载器逐候选核对，最新顺序不变。内部列表角色登记进一步绑定真实构造类，普通 Map／普通 ArrayList 先排除不可能的角色，带角色候选仍通过原认证目录查询。该修补整包构建通过，17 秒。第三十四次 79 秒现场在原文件读入中反复创建空网络来源集合，320 秒现场已在 Forge 类读取的缓冲提交后捕获真实数组快照，原场景最终超时，脚本结束自建 JVM，没有游戏内结果。

空作用域来源合并的完整工作包已写齐：网络不再先建立临时列表和空身份集合，客户端／缓冲／TaskBridge／资源与原生写入来源只在有实际成员时创建原身份集合。所有实际来源仍沿原范围收集并去重，资源生产者过滤及 null 未知标记不变；每次返回新的数组，不复用可写非空来源数组，也不跳过 Java／C 来源捕获、实际数组前后快照或读写历史。四个 Bootstrap 类已统一构建通过，16 秒；第三十五次在 Log4j 类转换绑定与 AccessTransformer 所需的实际文件读入中仍超时，125／363 秒完整现场保留，没有游戏内动作。Core／Agent／Bootstrap 39、Native 37、持久格式未变；B/C 与 O02／O05 未收口，尚无游戏内结果，未发布候选或同步 GitHub。

第三十次 100 秒实际现场中的内部列表回入工作包已完整写入并统一构建通过，15 秒。ExecutionFlow 的内部区域、读写窗口、历史分段、局部值／控制记录与临时列表改为私有 TraceList；构造只接受实际 ExecutionFlow、Heap、ArrayChange、MemoryUse、Frame 类，按准确实例在既有弱身份控制目录中登记内部列表角色。SourceMap 的应用来源作用域和通知不再追踪这些内部记录列表；valueAllowed 仍先经过 BackingBridge 的控制／元素检查。角色绑定目录弱键的实际对象身份，普通保护登记不赋予角色，同类对象、克隆或反序列化件不能从字段复制取得角色。原 ArrayList 外部写入守卫、底层数组继承保护、真实业务字段／数组来源与快照恢复均保留。没有 Map／全部受控对象／线程的通用豁免。第三十一次 112 秒现场正在 BootstrapLauncher 读取真实 JAR，没有前次列表作用域栈；最终仍进入 Forge／Mixin 初始化后超时，没有游戏内结果。后期线程请求时自建 JVM 已退出，未取得该次后期线程文件，不据早期现场代称整次通过。

原生空来源筛选工作包已统一构建通过，15 秒：发布前从不可变指令来源数组记录方法／镜像是否具有非空 OwnerLink；code_method_relevant 对原本必为 false 的空方法／镜像不再反复逐指令扫描或比较类身份。非空行仍核对原实际加载器／定义／方法及活的 Module 来源，后续原字节码／常量／异常表／活动版本校验保留，非空模块的范围查询没有用全局 presence 代替。没有缓存许可或跳过未知来源；C 与 Java 的来源收集覆盖并非完全相同，原两个接续收集均保留。第三十二次 240 秒完整现场已在 Forge 读取 ModFileInfo 时处理实际调用图／Native codeVersion 核对，最终日志推进到 Mod 发现和嵌套依赖处理，但仍在原场景时限内未到游戏内动作，脚本结束自建 JVM。后期线程请求时 JVM 已退出，没有该次后期线程文件，不据早期现场推断之后的运行。接下来继续处理这个实际调用图绑定的重复版本核对；尚无实际防护／恢复结果。Core／Agent／Bootstrap 39、Native 37、持久格式未变；B/C、原数组栈溢出与 O02／O05 继续处理，未打包候选或同步 GitHub。

数组来源记录工作包已统一构建通过，15 秒：方法指令的固定来源与本次执行帧的声明来源只建立一次，空来源／同一单 Module 的合并复用不可变来源；数组历史分段改为受保护快照的准确窗口，实际写入前后快照仍保留，恢复时按窗口取原值、核对当前值并提交。根写入已经发出的读取通知不重复发送，嵌套写入仍按实际完成通知。第二十九次 44 秒现场仍在实际调用图／Native 版本核对，321 秒现场在 JMX 类转换登记元数据时经固定控制目录 HashMap.put 逐次展开控制写入调用栈，最终进入 Forge／Mixin 初始化但仍超时，没有游戏内攻防结果。

针对这个实际登记路径，完整控制目录工作包改为固定的专用弱身份目录：按真实对象身份查重与回收，不进入被监控的 JDK Map 写入；目录节点沿实际 bootstrap 自有类保护，新增内部修改入口核对真实直接调用类与固定接收对象，Reference.clear／enqueue 仍核对原内部写入者。每次扩容发布的新数组也登记弱身份，旧数组仍被外部持有时继续受保护，不因目录弱键保留旧载体。原生固定表读取绑定真实 ControlRegistry／ControlKey[]，仍认证实际 CodeSourceBridge 调用者。移除只对旧私有 HashMap 生效的 SourceMap 特例，其他 Map 仍走原来源和元素策略。Native 常量核对只在一次方法比较内复用临时分配，每个根重新核对常量图，隐藏自身引用仍读取真实 VM 槽，活动版本校验保留。Native ABI 37／完整开发包统一构建通过，19 秒。第三十次 100 秒现场已在 BootstrapLauncher 读取包，来源记录自己的 replaceRegion 列表写入又进入 SourceMapBridge.enter；367 秒现场推进至 Forge 扫描 Minecraft 包，没有前次锁环，但仍在原场景时限内未到游戏内动作，脚本结束自建 JVM。继续处理这处元数据列表回入；尚无实际防护／恢复结果，不能据此结清第二十六次原栈溢出。B/C 与 O02／O05 均未收口，未发布候选或同步 GitHub。

第二十七次 169 秒完整现场确认新的 CONTROLS／HEAP 死锁：main 在固定控制目录中新建 HashMap 节点，SourceMapBridge.node 为它建立额外来源作用域；ConcurrentHashMap 的探针读取进入 Unsafe 来源记录等待 HEAP。后台接续线程持 HEAP，建立来源合并容器时等待 CONTROLS。修补只按真实对象身份识别这一个固定私有控制目录，SourceMapBridge 的来源登记／作用域／通知不再接入它；写入仍先经过 BackingBridge 原控制与元素判断，私有 Key 节点仍由 criticalEntry 保护，目录及 table 的原保护不变。新身份查询仅接受实际 SourceMapBridge 直接调用，未增加线程豁免。该完整工作包构建通过，15 秒。第二十八次 43 秒现场为实际调用图／Native 版本查询，320 秒现场 main 正在读取包，没有前次死锁；最终进入 Forge／Mixin 初始化，但仍在原时限内未到游戏内动作，脚本已结束自建 JVM，日志保留。B/C 及原栈溢出问题均尚不能报完成；继续处理实际启动阻塞与剩余代码，不重复无关测试。

控制登记修补已统一构建通过，18 秒：同一 CONTROLS 锁内已存在的真实对象不再重复 put，未登记对象仍先纳入保护；SourceMapBridge 的写入资格由 BackingBridge 原入口先核对，再执行原元素策略，移除同一次调用重复的资格查询。第二十五次最终超时，335 秒现场已进入 ModLauncher 的日志配置加载，仍未到游戏内动作。第二十六次同场景出现真实数组 BEGIN 失败，原异常最终为 StackOverflowError，JVM 随后报告类字节数组提交断言；337 秒现场在堆来源记录内部回收控制目录键，不能仅据该现场断定栈溢出根因。现在只补齐原失败的 VM 调用栈输出并重新抛回同一异常，Native／开发包构建通过，20 秒，ABI 未变。第二十七次已启动以取得这处具体失败证据；尚未修复该异常或取得游戏内效果。既有 Astra 后续审查未取得可用返回，不据此写审查通过。B/C 仍未完成，目标保持继续。

第二十三、二十四次实际场景仍在原时限内未进入游戏内动作。第二十四次及时保存的 33 秒现场没有前次死锁，main 在读取 JDK 镜像时，经每次 DirectByteBuffer.get／ix 扫描来源调用栈；该现场不能证明后续启动或整场景通过。来源查询工作包现已统一写入：来源方法按类名分组查找后继续核对实际加载器／定义／方法，保留同名不同加载器及 hidden 类的原身份规则；同次 bufferOperation 复用已经取得的来源做暴露记录；当前没有执行帧时不再为本来返回 null 的平台结果绑定展开栈。停止来源检查、当前指令及原生内存读写记录均保留。四个 Bootstrap 类统一构建通过，20 秒；第二十五次 44 秒现场进入实际外部调用图绑定／版本查询，随后仍超时。后续结果见上文，尚无游戏内效果；未发布新候选或同步 GitHub。

第二十二次及时取得 233 秒完整现场，确认另一处实际死锁：main 持 CONTROLS，读取该表自己的底层数组经 MethodHandle／Unsafe 进入 HEAP；Common-Cleaner 持 HEAP 构造真实字段写入记录，反向等待 CONTROLS。当前内部读取已改为认证的原 JNI GetObjectField：安装时绑定真实 bootstrap CodeSourceBridge 的 CONTROLS 实际 HashMap 与 table 字段，入口核对实际调用者，只读取该固定对象的当前数组；同一 CONTROLS 锁中的对象身份比较和外部写入保护不变。Native ABI 36／开发包构建通过，Astra 只读复核未发现确定缺陷，没有另跑测试。第二十三次随后超时，线程请求时 JVM 已退出，未取得该次线程文件；第二十四、二十五次的实际现场见上文。尚不能报游戏内效果通过或 B/C 收口。

本轮继续修复已确认的安装阻塞。调用图保留根类全部实际方法，只展开其可达的被调用方法；完整声明图仍用于继承／接口／隐藏定义解析，返回参数与来源按原流程传播。隐藏定义即使共用原指令节点，也按真实图身份分别保存调用边。类目录改为不可变状态原子发布，排队、分析中与刷新中工作一起接续；保护与分析不再持有类目录监视器。该版构建通过；第十九次 155 秒现场未见前版死锁，main 正在加载包、worker 停驻等待新工作，随后仍在原 360 秒内未进入游戏内攻防动作，脚本结束自建 JVM。没有实际效果通过结论。

普通 AtomicReference CAS 仍经过真实 VarHandle／Unsafe 写入守卫，可能先持字段 gate 再等待堆来源锁；堆锁中的懒加载则可能回到同一发布槽。现将 DIRECTORY／WORKER 两个固定实际对象接到内部 VM 原 CAS：安装时绑定真实 ExternalCodeDefinitions 类、两个真实 bootstrap AtomicReference 对象、真实 value 偏移和已捕获的原 VM compareAndSetReference。Java 与 JNI 入口均核对真实调用类，JNI 只接受该两个对象，GC 写屏障由原 VM CAS 保留，不增加线程或任意内存豁免。对应 Native ABI 35 与控制方法保护已统一构建通过；第二十次确认原生后端及既有守卫安装成功，但场景仍超时，未到游戏内动作。369 秒现场 main 在 Buffer.limit 的来源查询中；查询每一实际帧又展开一次栈检查写入桥身份，造成重复工作。现在三个直接桥调用入口按实际直接调用类认证，原帧／指令位置／Module 来源判断保留，该修补与下面迁移接续统一构建通过。第二十一次仍超时，脚本已结束自建 JVM，现场请求时进程已退出，没有取得新线程栈或游戏内效果；启动阻塞仍未收口。A 原候选和结论不变，未发布 B/C 候选或同步 GitHub。

迁移接续已修复“实际记录位置恢复后旧待处理状态仍残留”的问题：只有同一来源下同一记录对象、同一目标身份在实际输出图中被找到才解除对应待处理记录。DataFixer 输出与 SavedData 消费均做该核对；后者与实际输入选集共享原图锁。混合来源、不同记录以及仍未定位的记录保持待处理。该代码已构建通过，完整迁移／保存／重启效果尚未验证；B 的全部来源、后继／资源／容量与 O02／O05 整组收口仍未完成。

前一版缩小调用图与锁外分析构建通过，第十七次进入 Forge 包扫描后因 `ACTUAL_HANDLE_WRITE_REQUIRED` 退出。已从实际 JDK 源码核对，getCallerClass 无论配置什么选项都会隐藏 MethodHandle 等帧；现通过带隐藏帧的实际 walk 核对真实 VarHandle／Unsafe 写者，原平台类身份和字段／数组保护继续保留。Astra 找到的宿主类外部来源排队漏算也已补入，与分析中的工作使用同一来源判断。该版构建通过；第十八次 94 秒完整线程现场确认类目录发布／堆来源记录锁序死锁，已终止自建 JVM，失败记录保留。原子目录改动针对这处已确认死锁，未运行无关测试。B/C 仍没有游戏内效果结果，未发布新候选或同步 GitHub。

按用户最新要求，本轮数组恢复工作包写入后，已提前开展针对实际攻防错误的源码审查、编译，并复用既有 raw-backing 隔离夹具检查实际安装，不再等待全部 ABC 写齐。A 的既有完成结论和候选不变；以下增量属于 B/C，游戏内攻防动作尚未跑通。

安装检查已经定位并修复原生字段观察回调准备过晚、JDK 清空 ThreadLocal 后丢失在途写入栈、Java 库加载记录启用前误拦原始 JDK 加载，以及在只读导入表上使用比较交换“读取”导致 JVM 原生崩溃。真实线程／接收对象／原 java.dll 调用身份继续核对。同次调用图分析复用真实 bootstrap 类快照，避免每个类型查询都枚举整个 JVM。

第七次安装确认控制映像失败来自固定 2048 项登记截断，已移除；第八、九次未再出现该错误，但都未在原 360 秒内完成安装。文件详情为尚未解析的延迟绑定入口，继续保留待绑定状态，不能据数量记为效果通过。第九次线程现场显示 JVM 枚举类时仍大量进入数组来源记录；采样请求未完成，没有取得 JFR 结果，安装耗时原因仍需继续定位。隔离 JVM 与等待它的诊断请求均已结束，失败日志保留。

本轮代码另补齐 ControlChange／applied 来源数组、外部代码图容器底层数组，以及集合扩容发布前的准确数组保护；高频控制登记改按本批真实对象增量处理。复查修复了新钩子可能形成的控制目录／来源目录锁序环，并将当前目录 table 读取固化为受保护 getter，避免缓存反射对象被撤销访问权限。最终修复后的开发包构建通过，第十次起实际安装已使用这两处修正；B/C 仍没有游戏内效果通过结论。

后续第十、十一次实际安装仍在原 360 秒内未完成，失败记录保留。沿实际数组写入路径已修复单元素刷新反复扫描全表、控制键回收连带访问无关来源表，以及临时载体／来源重复登记；真实内容、未知来源和历史版本保持原语义，本批构建通过。内部 bootstrap 查询另改为认证的 JVMTI 真实类快照，构造自有的新结果数组，Native ABI 32 开发包构建通过。第十二次首次记录控制入口与发布保护安装成功，但仍超时，脚本已终止自建 JVM，没有游戏内效果结果。

内部普通已加载类查询现也接续同一真实快照入口，包含原有数组类与 hidden 类，并按各原调用点重新取快照；只改变自有查询结果的构造，没有放行应用数组写入。原生后端未安装时保留原 Instrumentation 查询；原生查询本身失败仍抛错。Native ABI 33 同时包含移除查询中多余 control 计数作用域的最终修正，实际构建与运行对应关系见既有报告。

第十三次仍超时，78 秒现场在类加载解压、306 秒现场在真实调用图来源合并，随后出现控制入口安装成功记录；均未进入游戏内攻防动作。现已写入来源值相等时复用、同图内方法调用摘要不变时复用分析结果，以及整张已解析图没有 Module 来源种子时的空集处理，仍核对各方法指令布局。原生基本类型数组继续保留实际写锁、来源与内容历史；无逐元素策略的载体按既有 copy／fill 契约做整段权限判断，已知载体继续检查每个实际写入值。此次 Native ABI 34，构建和运行结果见既有报告。

第十四次在启动后约 38 秒已记录控制入口安装成功，94 秒现场位于接收对象类型合并；后续类字节数组提交失败，JVM 报 instrumentation 断言并以 1 退出，未进入游戏内动作。接收对象分析现也复用相等的不可变值，仅对含虚调用／接口调用的方法计算接收对象；静态／特殊调用解析与完整来源分析保留。原生数组失败处另保留原异常类型、消息和失败阶段，避免 JVM 清除异常后只留下断言。此后源码和实际运行对应关系见报告。

第十五次确认最初失败为 `ClassCircularityError: SourceMapBridge$Frame`：Map 守卫启用后才首次定义自己的帧类，填入该类字节数组时再次进入 Map 守卫。现将 Frame 及同一来源作用域的 Key／OwnerRef／Gate／Scope／Refused 在原生观察与 Map 重转换之前准备，后续仍按原流程登记和保护这些真实类，没有放行失败写入。

该修复构建通过。第十六次已越过原循环加载位置，记录 Map／反射／Unsafe／30 个句柄工厂保护安装成功，并进入 Forge 包扫描；仍未在原 360 秒内完成实际场景，脚本已结束自建 JVM。283／332 秒现场显示主线程等待 ExternalCodeDefinitions 锁，后台接续线程持锁进行真实调用图的接收对象分析。当时此等待尚未修复，后续改动以上文和实际报告为准；游戏内数组攻防未取得结果，没有发布新候选或同步 GitHub。

数组恢复已接入真实数组的写前／写后内容：基本类型按字节保存，可区分同一元素的部分 Unsafe 写入；引用数组按真实对象身份保存。JVM 数组指令、批量助手、JNI 提交、Unsafe／VarHandle 与缓冲区复制共用原恢复流程。真实包含的嵌套写入合并，互相干涉的写入保持未决；恢复保留组外后写的实际内容并重接历史基线。引用写入种类沿原操作参数传递，不能因裸字节跨度碰巧对齐就认作引用写入。原生快照和恢复在 GC Critical 区间内只进行有界内存操作，释放后才发布结果。

本轮审查修复：多个组先后停止时不回填另一已停组的旧值；较早的不完整历史不再堵住后续可确认写入的恢复；直接数组读取在并发下保留窗口内已知写者；失败但已改写内容的复制仍合并来源；来源数组按数组本身登记控制保护，避免变长参数把数组展开后漏保护。数组来源完整性、实际回填失败、旧历史和在途读取继续进入原有待处理状态，没有伪造清理成功。

集中编译同时修复定时任务反射声明、重复实体访问器、世界结构恢复方法名和来源守卫中过期的受检异常捕获。Core／Agent／Bootstrap Java 编译、Native DLL 编译和实际安装失败记录在 [本轮实际报告](../../validation/a-final-20260928/REPORT.md)；没有新增或运行无关测试，尚未发布新候选或同步 GitHub。完整 B/C 的其余后端仍需继续，不能沿用 A 的通过结果代替它们。

以下为前序源码批次记录，其当时的“未构建”状态保留；本轮编译结果以上文和报告为准。

本批连续接续缓冲区读值及后续使用的来源代码。直接和堆上的各基本类型缓冲区、只读堆视图、数组批量 get／put、compact 及字符串写入共用真实载体／偏移／范围记录。堆视图从实际 hb／bb 字段和 Buffer 地址连接到原数组；字符串写入读取该真实 String 的 value／coder，输入字节范围与输出字符范围分别记录，不因 Latin-1／UTF-16 宽度不同而混淆复制长度。原生存储的释放路径仍只处理真实直接缓冲区，堆上的内容接入已有堆写入记录。

执行计划保留真实调用指令的方法声明。缓冲区观察只连接实际父调用帧、指令位置、方法名／描述符及实际接收对象一致的暂停调用；完成读取的已知来源进入该次返回值及异常来源，并在真实嵌套缓冲区调用中传回外层。不会通过返回数值相等给别的调用附加来源。未织入调用、反射中间层和未观测底层写入的来源完整性继续未决。

堆读取窗口保留实际载体与范围，在原数组／字段写入记录中接续窗口期间的已知来源，复制结束时合并前值记录和窗口内观察，避免仅查看最终来源而丢掉中途写入者。复制源不持有跨两端的互等锁；嵌套堆读取复用同次复制窗口，嵌套原生读取复用实际源分配内的读取快照并合并其观察结果。读取登记失败、原操作失败和正常完成都接入原收尾。此处补的是来源接续；数组完整修订与回填，以及未捕获底层写入仍需继续实现。

本批为未构建源码，Core／Agent／Bootstrap ABI 38、Native ABI 30；仍按整份 ABC 代码先写齐的安排执行，未运行编译、审查、测试、探针、打包或 GitHub 同步，实际候选和运行报告不变。ABC 尚未完成。

本批连续写入原生异步调用关联、直接缓冲区写入记录、共享内容恢复，以及同步／异步读入的数据记录。Windows 异步原生调用保存真实请求／通道对象、描述符、声明类和原指令的完整参数；原生入口逐项核对同一调用，原返回与异常释放该调用占用。读写和关闭沿各自真实窗口执行，未以裸句柄数值认定归属。

DirectByteBuffer 的实际构造对象与原 Unsafe 分配返回、分配代次及原 Deallocator 绑定；构造中的真实初始化作为中性基线保留。原始释放继续核对同一原清理动作，失败构造仅接续当次实际构造对象。BufferBoundary 捕获普通 put、绝对位置写入、数组／缓冲区复制和 compact 的原参数，按真实视图与存储关系记录范围、写前／写后内容及来源；内层同次包含写入合并到外层窗口，异常实际改写保持未决。字符串逐字符写入沿原执行来源记录，不据此宣称全部标量来源已覆盖。

共享缓冲区和 JDK 临时缓存保留原分配时，现先分段恢复目标来源的已记录内容，再结清共享保留。恢复以真实存储及原动作、原分配代次和已停止 Module 集合进入，在途请求未结束时继续等待；恢复期间新 Java 访问等待，原生观察写入／读取与恢复使用同一分配窗口。保留组外写入结果并重接其历史基线，整份分配仍有目标修订时不提前结束。创建来源仍保留，另记录该真实共享分配已完成本次贡献恢复，避免把合法共享存储误报成尚未释放的私有分配。

IOUtil 中实际 NativeDispatcher.read／pread／readv 调用前记录本次真实接收缓冲区与原地址、长度；原返回按实际字节数结束记录，随后才进入 JDK 位置更新或堆缓冲区回填。Windows 异步文件／socket ReadTask 在原提交前接续相同记录，真实 IOCP 完成入口按 transferred 数结束，失败或未提交收尾也结束本次记录；超时、取消不单独解除占用。同步 iovec 与异步 WSABUF 读取来自当次原调用，范围再与同次真实缓冲区占用核对。原生记录可跨提交／完成线程存活；同一向量请求中同一分配的互不重叠片段共同接续，外部并发写入、重叠片段或内容断链保持未决。安装前存在但未捕获创建的缓冲区只登记实际借用视图，不补造创建来源。

本批均为源码，接口为 Core／Agent／Bootstrap ABI 37、Native ABI 29。依用户要求，未运行编译、审查、测试、探针、打包或 GitHub 同步，实际候选仍为 `distribution/candidate-modwide-a-complete-20261001`，实际运行报告未改。直接绕过上述 JDK 调用的原生写入、映射／foreign memory、完整读取值来源、其余堆／宿主／客户端与世界后端继续接续；ABC 尚未完成。

本轮按用户要求连续写入异步读写与 JDK 网络资源代码，新增 IoBoundary／IoBridge，并同时接续 Core 资源遍历、原资源关闭队列、控制元数据保护和 Agent 初始重转换。SocketChannel／ServerSocketChannel／DatagramChannel、Windows 异步 socket／文件通道以及 NioSocketImpl 按实际对象、构造／接受结果和真实 FileDescriptor 建立关系；Socket／ServerSocket／DatagramSocket、适配器和输入输出流按实际委托字段或返回对象连接，未用描述符数值、线程名或 TCCL 推断归属。

同步读写与 IOUtil 的实际参数保留调用期间的缓冲区占用，向量参数仅接续本次 offset／length 选中的部分。异步 ReadTask／WriteTask／ConnectTask／AcceptTask／LockTask 保存真实提交来源和请求关系，运行及完成线程接续同一记录；读写缓冲区占用一直保留到原 releaseBuffers／releaseBufferIfSubstituted 收尾，Future 超时或取消本身不解除占用。原 IOCP／PendingIoCache 完成调用、重复启动拒绝、未提交失败、读写状态恢复及原异常收尾一起接入。实际 Util 临时缓冲区的取出、归还和请求期间占用同时接续；原 JDK 缓存持有的存储继续由其原缓存／Cleaner 释放，不按单一 Mod 的私有分配强制释放。

整组退休先核对真实共享来源和别名，再封住新访问、调用原 close，并继续等待正在执行的请求及原生关闭完成；同步 socket 的 tryClose 接续原延迟关闭窗口，避免只看 isOpen 就判定句柄已释放。SocketDispatcher／DatagramDispatcher／Net／DatagramChannelImpl 的真实 FileDescriptor 原生参数接入现有绑定观察；SocketDispatcher 的整数 close0 参数只在同次原 Java close 捕获的真实描述符窗口内核对。停止来源只能在已选择资源的原关闭窗口完成该关闭，其他原生访问继续经过原来源限制。

这批仍为未构建源码，Core／Agent／Bootstrap ABI 36、Native ABI 28；没有运行编译、审查、测试、探针、打包或 GitHub 同步，原候选和实际运行报告未改变。共享缓冲内容恢复、外部裸地址／foreign memory、直接原生映射以及绕过 Java 请求对象的异步原始句柄调用仍需接续，连同其余 B/C 后端继续完成；ABC 尚未完成。

本轮连续补入文件映射与直接缓冲区的创建、视图、使用及原清理路径。实际 FileChannelImpl.mapInternal 返回的 Unmapper 保存原地址、范围与复制描述符；DirectByteBuffer 的原 Cleaner.create 接续真实 Deallocator／Unmapper，并保留原 Cleaner 注销和释放统计动作。直接字节缓冲区、只读视图、各基本类型直接视图及 ByteBufferAs 视图按真实 att／bb 关系连接到原存储；地址、容量与范围仅核对该已建立关系，不以相同地址推定生产来源。堆缓冲区不进入这条原生资源释放路径。

缓冲区方法及同步文件通道实际 Buffer／Buffer[] 参数，在进入原动作前登记同次使用，正常／异常退出释放；原异常保持。创建与来源登记、视图变化、释放封口和操作计数共用现有资源元数据锁。Core 对已停止的实际整组 Module 选择私有存储，等待操作退出后调用原 Cleaner／原动作；真实共享视图保留，后续组退役可重新判断。原 Cleaner.clean 在注销前经过资源判断；直接调用原 Deallocator／Unmapper 也接入同一窗口，重复清理不再重复执行原动作。失败或无法确认的释放留在原资源状态中。

原始内存层在实际 DirectByteBuffer 构造调用中的分配返回之前标记由资源管理，整组原始内存释放与范围写回将其留给缓冲区原清理链，避免绕过 Cleaner 和配额统计释放。JNI GetDirectBufferAddress、缓冲区地址／描述符访问器以及 Unmapper 地址访问接续实际暴露记录；外部原始地址使用未结束这一范围仍保留未决，不把 Java 方法已返回当作外部使用已结束。FileChannelImpl 原生映射／传输入口接入文件操作，原生 unmap0 逐次核对实际参数；已登记映射只能在其真实清理窗口内解除。Windows 原解除映射的成功／异常契约依据 [OpenJDK 17 FileChannelImpl 原生实现](https://github.com/openjdk/jdk17u/blob/master/src/java.base/windows/native/libnio/ch/FileChannelImpl.c)。

Core 资源遍历、bootstrap 原生桥、Agent 初始重转换和后续类加载、所需模块访问以及构建输入一起接续；接口同步为 Core／Agent／Bootstrap ABI 35、Native ABI 27。原生资源来源合并仅接续实际生产 Module，平台声明所属 java.base 不单独成为共享使用者。当前均为源码，未构建、找 bug、测试、打包或同步。共享缓冲内容按来源恢复、外部裸地址／foreign memory 使用、直接原生映射的完整生命周期，以及异步／socket 和其他 B/C 后端仍需继续；ABC 尚未完成，实际候选与运行报告未改变。

本轮按完整调用链连续补入线程控制与原始内存生命周期源码。真实 Thread 的 start0、interrupt0、stop0、suspend0、resume0、setPriority0、setNativeName 接入原生绑定替换；Java stop／suspend／resume／setPriority／setName 在原字段或原生动作前调用同一保护判断，interrupt 同时接入原有退役中断窗口。判断针对真实线程、实际 target 和实际调度器，不以线程名或 TCCL 认定来源。直接原生启动接续真实生产 Module、开始计数和退出收尾；正常返回、异常及原生线程结束均释放其实际窗口。退役中断仍在共享 I/O 未退出时保留待处理请求。

文件与线程复用读取当前 Method 实现的安装接续。线程在 VM 映像内的已解析入口，用实际声明 jmethodID 和当前指针与真实 VM 注册出口核对，再包住当时读取到的实现；没有用导出名称替换未知原实现。对应声明来自 [OpenJDK 17 Thread 原生注册表](https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/native/libjava/Thread.c)。原生控制回调逐次核对同一真实接收对象、来源数组、操作及退出 token，安装前已经运行的旧调用仍不算被迁移。

Unsafe 安装接续到真实 VM 的 RegisterNatives 提交表。真实注册成功后保留其实际方法及实现关联，再触发对应方法的当前绑定接续；已经绑定且原地址未变化、因而没有再次发出 NativeMethodBind 的入口也经过同一路径。原始内存分配记录保留实际分配调用使用的 Unsafe 对象、同一 VM 提交的释放实现和分配代次。整组恢复对创建来源、实际读取者和仍保留的写入来源都属于本次已停用 Module 集合的分配，调用保存的原释放入口；共享使用、来源缺失或结果不明继续保留。释放契约依据 [OpenJDK 17 Unsafe 的分配、重分配与释放实现](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/prims/unsafe.cpp)，没有把原始地址交给控制器自己的 malloc／free。

内存实际操作在查询到分配时登记使用，等待原门的操作也计入在途；释放封口与该登记共用查询锁，再在分配原门内调用释放。重分配／释放遇到在途使用或分配代次已变化时拒绝原动作。未知贡献、缺失记录和不完整写入同时保留在该分配的状态中，未确认释放不会重试为成功或继续写回可能已失效的区域。现有整组恢复游标同时处理整份释放和共享分配内的已知来源回退，创建残留与忙碌分配继续计入未决。

Core／Agent／Bootstrap ABI 34、Native ABI 26、安装调用及原生构建输入已一起修改。以上仅为源码；完整 ABC 工作包尚未写齐，本轮没有构建、找 bug、测试、打包或 GitHub 同步。任意未观察本机写入、映射及异步／socket 资源、其他 B/C 后端继续编写，现有实测候选和运行报告未改变。

本轮继续成批补入已绑定文件入口的安装接续。复用当前 JVM 导出的 Method／ConstMethod 布局、真实 jmethodID、名称和描述符读取 native_function；通过 RegisterNatives 触发同一 NativeMethodBind 控制，在事件内重新取得当时的实际实现，再发布原有 trampoline 并读取实际安装结果。三个文件流、FileDispatcherImpl 的准确 FileDescriptor 参数方法和 FileDescriptor 实例原生方法均接入。未解析的 VM 入口、无法确认的生成代码和已经运行中的旧调用仍保留实际限制，不以安装动作代替旧调用退出。

实现依据为 [OpenJDK 17 Method 的原生函数位置](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/oops/method.hpp)及其 [原生绑定事件与入口更新顺序](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/oops/method.cpp)；运行时大小来自当前 VM 的类型表，没有写死某个发行版的偏移，也没有用 DLL 导出名称推定现有绑定。

文件通道下面的原生读写、定位、截断、同步、锁与关闭按真实 FileDescriptor 纳入使用来源和在途计数。描述符记录保存实际原生使用者；Core 为文件和进程管道释放传入本次已停止作用组的 Module 集合。全描述符关闭同时检查真实视图和原生使用者，共享或仍在登记来源时继续接续，关闭后仍等待真实操作退出。描述符 Java close 在注销原 Cleaner 前进入既有保护，原生 close 检查该描述符实际关联视图；停止来源的打开／同步／普通 I/O 分别沿 FileNotFoundException、SyncFailedException、IOException 返回。

安装调用、Core／Agent／Bootstrap ABI 33 和 Native ABI 25 已同步改写。以上仍是源码，未构建、测试、审计、打包或同步。原始数值句柄复制、未观察生成入口、映射缓冲、异步／socket 资源及其余 B/C 后端继续编写；不把本轮支持面当作整个 ABC 已完成。

本轮按完整资源链连续编写文件流、同步文件通道和相应 JNI 接入。三个实际 JDK 文件流构造器补充来源登记；其 Java 读写、定位、句柄取得和延迟通道发布接入使用者来源及在途操作记录。关闭在修改 closed 状态前接既有资源保护，停止来源的新 open 在破坏性打开前拒绝。正常返回和异常出口均归还在途操作；退休封口与来源登记相接。

WindowsChannelFactory 返回的真实 FileDescriptor 记录分配事实；FileChannelImpl 构造、parent、fd、path、原始数值句柄和 Cleaner 关联同一资源记录。专属描述符封住后续 attach／通道构造，关闭其实际文件流及通道并等待真实操作退出；共享描述符仅拆目标视图。父流仍被组外通道使用时保留；独立借用通道通过原 close 释放自身锁和等待者，其实际 Cleaner 注销后不再关闭借用的描述符。通道引用在关闭／Cleaner 结果及最后操作退出后移除，共享保留不永久标记为整份资源已释放。已完成但仍返回待处理的释放任务可在下一轮重新接续。

新增 native/file.c，接既有 NativeMethodBind 和原始 ABI trampoline：真实 bootstrap FileInputStream／FileOutputStream／RandomAccessFile 的新绑定采用其实际地址，接纳原生来源，原生打开拒绝对已捕获文件流重新覆盖句柄，正常／异常／真实 ThreadEnd 归还同一操作。原返回值与原异常沿原调用返回，控制器自身 I/O 不递归登记。Core 已纳入 FileChannel，安装列表、Java ABI 32／Native ABI 24 及原生构建输入同步修改。

这些为未构建源码。安装之前已经绑定、未再次触发 NativeMethodBind 的文件入口、原始机器地址调用、其他 NIO／异步／socket 资源、映射缓冲和其余 B/C 后端仍未闭合，不能把本轮接入当作全部 I/O 已覆盖。本轮未运行构建、测试、审计、打包或 GitHub 同步，整个 ABC 工作包继续编写。

同轮继续写入字段恢复接续：真实字段 watch 和匹配的执行写入事件可使对应已观察字段进入恢复；反射／JNI／句柄的准确字段写入，以及与真实 Field 偏移、宽度完全一致的 Unsafe 写入，保留同一操作的前后值和来源。相同线程、执行帧、指令及准确槽位的 VM 写事件与现有写入作用域合并，其他事件继续使旧记录失效；异常或 CAS 未写入沿原结果退出。部分字节、无法解析的布局、缺失 watch 和数组未覆盖入口不借此升级为可恢复。

恢复发现同时检查当前写入和历史节点。目标节点被组外完整字段写入覆盖后，仅移除该目标历史节点并重接保留节点的 before，当前组外值保持；以后撤销组外写入也不会恢复已移除的目标历史值。实际改变字段仍走原恢复锁、准确类型和当前值比较。引用回收保持待处理，浮点比较保留原始位，历史前后值不连续时转为来源未知。完成状态仍保留历史节点中的目标来源，不能只因当前值来自其他组就忽略旧恢复链。本段也是源码实现，尚未验证；其他 B/C 代码继续。

退休中断接入真实 Thread.interrupt 与 blockedOn 窗口。独立线程和私有线程池关闭使用限定到实际线程／实际池的中断作用域，普通调用保留原行为。退休请求遇到尚未解除的 I/O 阻塞回调时记录待发请求，不调用会连带关闭通道的原回调；回调真实清除后在该线程补发中断。阻塞器发布与退休中断共用线程内的操作锁，控制路径使用 tryLock，新的阻塞注册在退休状态下拒绝，避免“先设置中断位、随后新 I/O 自行关闭共享通道”的接续窗口。清除回调的最后检查／解锁与等待请求登记相接，保留迟到请求；正常及异常出口释放窗口。

已确认私有的 Timer 在取消队列后也请求同一受控中断，仍等待实际线程退出。阻塞资源自身的释放沿现有准确关联流程执行；本次没有将未解除的 I/O 等待当作线程已退出，也没有新增任意关闭通道的路径。尚未关联的 NIO 句柄、直接 native 绕行和其余共享资源处置仍需继续。Core／Agent／Bootstrap 同步 ABI 31，Native 仍为 ABI 23。本轮仍只有源码，未构建、测试、打包或同步，ABC 未完成。

独立线程的启动／退役窗口继续写入源码。真实 bootstrap Thread.start 在原 ThreadGroup 登记之前接入开始记录，原正常／异常出口均结束窗口；退役不获取目标线程监视器，不将启动尚在途时的 isAlive=false 当作已经结束。纯目标来源的独立线程在真实启动入口拒绝再次启动，Thread.run 的平台 Runnable 入口读取相同退役状态；已启动线程在原 Thread.interrupt 合同下请求中断，仍以实际退出结清。线程来源合并真实创建、任务、启动者及 run 声明来源，接入既有整组对象枚举和未知来源报告，不再只接收一个 objectModule 值。

标准 ThreadPoolExecutor.Worker、ForkJoinWorkerThread 与 TimerThread 按实际 bootstrap Class／真实字段解析所属调度器；Timer 创建和实际提交补充真实 Timer—线程关系。其工作线程继续进入原调度器循环，资源处置转交已有调度器退役，独立线程路径不直接中断这些工作线程。共享调度器继续保留，未知关系留待处理；从线程载体发现的私有调度器也进入既有持续工作。JDK 线程资源按该路径结束后，不再遍历它的全局 ThreadGroup 等平台内部结构；用户线程子类的声明资源仍由现有遍历接续。安装流程先准备实际字段访问，再改写 Thread 并接入现有覆盖状态。Core／Agent／Bootstrap 同步 ABI 30，Native 仍为 ABI 23。原始 native 启动绕行、共享 I/O 中断副作用、其他资源和 B/C 范围仍需继续；本轮未构建、测试、打包或同步，ABC 未完成。

私有线程池退役继续接入实际提交与资源交接。原 JDK 提交在记录来源前进入实际池的在途计数；私有判定与关闭封口共用同一元数据锁，已有提交未退出时不开始关闭，封口后的新提交拒绝。原 shutdownNow 调用在锁外执行，避免其队列／线程回调与观察记录互等。真实返回的任务列表由 bootstrap 保留，重复退役返回尚未接收的成员；Core 事件真正落到 Task 后才按对象身份释放交接记录，捕获队列／任务容量不足不会把这份列表随调用返回丢掉。资源结束同时等待返回成员交接、关闭调用结束和实际 isTerminated；原关闭抛异常后保留返回结果未观测状态，不以之后的空列表抹掉该缺口。

任务侧的整组调度器处置加入 ExecutorService，并保留实际调度器工作，直到资源结束；最后一条 Task 释放不再使尚未结束的池失去后续处理。真实组发生合并时接续原工作。共享池留给现有逐任务退役，未知／忙碌成员继续未决；单个调度器失败后继续其他成员，待处理状态落在对应组。Core／Agent／Bootstrap 同步 ABI 29，Native 仍为 ABI 23。独立线程、共享资源和其余 B/C 范围继续编写；本轮未构建、测试、打包或同步，ABC 未完成。

旧式 JSR／RET 子程序接入既有代码来源与执行改写路径。先在原指令上附加来源位置，再使用 JDK 自带 ASM 展开实际子程序；克隆的执行段和替代 JSR／RET 的指令接续原来源标记，旧执行帧移除后由后续改写重算。没有来源行的旧类也在帧计算前经过展开。外部代码回退阶段暂时保留原子程序和指令身份，用兼容子程序的最大栈计算输出，随后交给同一 prepare 路径；克隆位置恢复各自的原调试行。编译与 Agent 的实际模块导出同步加入 ASM commons。

根 Object 构造器新增专用执行观察入口，普通返回与异常退出接入现有执行记录。其接收对象在入口即可使用，依据 Java 17 JVM 规范 4.10.1.6 的 Object 初始帧规则；其他构造器仍沿真实 this／super 初始化路径接续。入口在创建 Java 栈遍历结果前进入已有原生线程控制作用域，核对实际 Object 构造器和真实桥调用，识别 CodeSourceBridge／ExecutionFlow 观察过程引起的递归构造，避免观察器因创建自身记录反复进入。Core／Agent／Bootstrap 同步 ABI 28，Native 同步 ABI 23；原生构建输入补入已经由 bindings.c 包含的 heap／unsafe／memory 源文件，后续集中构建会纳入其改动。这些是源码实现，根入口、旧式方法及此前 B/C 改动尚未构建或实测；任意未观测本机写入、完整资源终结等其余 B/C 范围继续编写，ABC 未完成，尚未打包或同步。

线程池／定时器整组来源处置继续写入源码。JDK 执行器包装沿真实 bootstrap Class 与真实委托字段接续到实际线程池，移除固定四层截断并识别委托环；包装器创建和实际任务提交的来源一起记录到该委托链。私有判断同时覆盖包装器与真实池的创建来源、所有已记录使用者及正在进行的提交，拒绝将“包装器属于目标组”代替“池属于目标组”。已确认组外共享继续保留，来源未明或提交未结束留待处理；进入过观察入口的提交在观察回调异常后仍保留收尾对象，退出时释放实际池的在途计数。

线程池关闭和 Timer 清理改用真实整组 Module 集合；同组多来源任务不再依赖单一 objectModule 值，来源未知的任务不再与空模块值一同匹配。Timer 队列及已公布任务按真实来源筛选，私有取消继续持有原队列监视器，组外份额保留；资源结束按私有线程实际退出或已确认共享分别处理。Core／Agent／Bootstrap 接口同步 ABI 27。构造器根入口、旧式子程序字节码、其余任务／资源与 B/C 范围继续编写；本轮未构建、测试、打包或同步，ABC 未完成。

原始内存恢复已接入现有整组恢复流程的源码。真实存活分配中的写入在原窗口内保存写前／写后字节，来源记录改为不重叠区间及修订链；相邻写入按实际交集拆分，同一实际来源的连续写入在前后值衔接时合并，保留最初基线，避免每次普通 store 累积一层记录。分配缩短时裁剪区间，原释放成功后释放记录图像；失败操作若实际改变了字节，保留不完整修订，不当作未发生。

现有 RecoverySources → ProRuntime → NativeControl 路径按真实停用 Module 集合分批恢复，每次最多处理 256 个记录、每个区间最多 64 KiB，游标接续未结部分。恢复核对同一存活分配、当前在途操作和记录预期值，实际写回后读取确认。仅由目标集合贡献的修订可以撤销；组外后写入的结果保留，其旧基线同步去除已撤销贡献，避免日后回退重新带回目标数据。混合来源、图像缺失、前后值断链、当前值已外改或操作仍在途继续未决。退休查询同步使用仍存活的修订来源，分配锁占用不会让相关范围临时漏报。Native 声明同步 ABI 22。这里接通的是已记录区间的条件恢复，整份分配释放、任意未观测本机写入及其余 B/C 范围仍需继续；未构建、测试、打包或同步，ABC 未完成。

Unsafe 读取与复制来源继续写入源码。实际 Java 包装、内部 native getter 调用和真实 native 绑定分别保留同次读取的目标与范围；已接入执行计划的 getter 将字段、数组区间或实际存活分配的已记录来源合入返回值，正常返回才接续，异常不制造读回结果。原始读取单独记录实际读取者，不增加写入贡献；其他模块读取、未知读取者与观察缺口保留在既有退休状态。来源信息仍带未知部分，不以地址或读回数值推断独占归属。

copyMemory／copySwapMemory 的源对象／地址、偏移和长度接入原调用点、Java 包装及原生绑定，复制前后的已记录来源加入准确目标范围，复制本身的实际执行来源同时保留。原始源区间使用独立读取窗口，快照与完成时不等待另一个被占用的源锁，避免相反方向及嵌套复制因观察锁互等；无法取得源观察、实际分配代次变化等继续保留未决记录。拒绝复制关闭已开窗口并跳过原操作，原调用异常继续保留，收尾异常附加到原异常。接口声明同步为 Core／Agent／Bootstrap ABI 26、Native ABI 21。任意本机直接写入、未织入读取返回值、原始范围基线与准确恢复，以及其余 B/C 范围仍待继续；本轮未构建、测试、打包或同步，ABC 未完成。

原始地址来源继续接入真实分配链：实际 bootstrap Unsafe 的 allocateMemory0／reallocateMemory0／freeMemory0 绑定记录原实参、原调用返回和真实 Java／native 生产来源。只有原分配／重分配成功返回后，才建立地址、实际长度及代次的关联；重分配移动后接续已有写入范围，缩短时裁掉不再存在的部分，释放成功后结束该分配。Java／原生 raw 写入在真实存活分配的门内记录准确偏移范围与实际成功贡献，同一范围的嵌套操作共用窗口，原异常继续保留；没有实际分配记录的操作留下未映射来源缺口，地址数字不单独作为来源证据。已有执行计划的 raw put／setMemory／copyMemory／copySwapMemory 重载也接入原调用点。实际存活分配、范围、进行中操作及未映射事件纳入既有整组退休缺口，原生声明同步为 ABI 19。读取／共享暴露、任意本机写入、原始范围基线与准确恢复继续编写，本轮未构建、审查、测试、打包或同步，ABC 未完成。

Unsafe 原调用点继续接入现有执行织入：对已经有真实 VM 执行计划的方法，按原始 INVOKEVIRTUAL 指令声明保存真实 Unsafe 接收对象、目标载体、偏移、长度和写入参数；put／ordered／volatile、CAS／exchange／getAnd 原子操作及对象 bulk 写入，在同次原调用前执行策略，正常／异常后关闭记录。这些前置检查在原调用选择 native 入口或编译器内联前生效。原调用的成功判定继续使用实际返回和 expected 参数；拒绝 CAS 返回 false，拒绝交换／取旧值操作读取该位置当前值，原异常保留。声明、回调 BCI、异常处理和来源搬移合入原执行计划，不在最终织入后追加一轮位置失配的改写。真实 Unsafe Class 身份与当前计划、原指令位置共同选择操作；Java／原生同一范围的窗口继续共用。没有执行计划的方法、JDK／隐藏／动态等尚未织入入口、原始地址、任意本机直接写入与完整低层观察继续编写。未构建、审查、测试、打包或同步，ABC 未完成。

Java／原生 Unsafe 同次写入窗口继续合并：准确目标对象、偏移和长度一致的嵌套调用共用原字段门与来源窗口，保持各自真实返回／异常的收尾顺序。原生及嵌套 Java 来源在实际写入成功后加入共用记录；失败 CAS 不增加写入贡献，内层已确认写入不会因外层随后异常而被当作从未发生。偏移或长度不同的低层操作仍独立处理。真实控制器原生作用域中的 JDK Unsafe／VarHandle 调用在 Java 观察入口和 native 入口一起避开递归记账，其他调用继续走原策略，相关 native 声明同步为 ABI 18。编译器直接内联入口、原始地址及完整 writer 覆盖继续编写；尚未构建、审查、测试、打包或同步，ABC 未完成。

原生 Unsafe 写入入口继续写入源码：只选择真实 bootstrap Unsafe Class 的准确 native 方法声明，在原绑定替换器中保存同次 JNI 实参；put／volatile put、Reference／Int／Long 比较操作以及 setMemory0／copyMemory0／copySwapMemory0 接入目标偏移、长度、实际原生来源和原字段门。实际 CAS 返回 false 按未写入收尾，compareAndExchange 以同次原返回与 expected 参数按真实引用或标量判断；原调用异常继续保留，拒绝交换写入返回该目标当前值。已绑定方法通过该真实类自身的 registerNatives 接续原入口与 MethodBind；控制器原生作用域中的 JDK 原子操作避免递归进入同一原生记账入口，线程退出收尾未结束记录。私有原生作用域核对本次真实目标及参数，原生声明同步为 ABI 17。编译器内联绕过、原始地址来源、Java／原生重复窗口合并及完整 writer 观察仍待继续；未启用完整槽覆盖。本轮仅写源码，未构建、审查、测试、打包或同步，ABC 未完成。

低层 Java 写入继续接入：现有 VarHandle 原子调用保留原实参、实际返回和异常，并在原 Unsafe 调用前后收尾；比较交换按真实成功标记结束，compareAndExchange 以同次原调用返回与原 expected 实参判断是否写入，getAndSetBoolean 的旧 false 返回不作为失败。内部 Unsafe 的 Java 实现按该类真实声明的 native 方法接入原写入调用，包含实际数据转换后的 put／CAS，以及 setMemory0、copyMemory0、copySwapMemory0 的目标区间；拒绝写入时结束原 Java 操作，避免内部 CAS 重试循环继续运行。JNI 字段与数组写入同时接续实际原生绑定、加载作用域和当前 VM 代码来源中的生产 Module 身份，并与 Java 观察来源合并，原生声明同步为 ABI 16。直接进入内部 Unsafe 的 native 方法、绕开这些 Java 调用点的编译器内联／原始地址、其余低层 writer 与恢复覆盖尚未闭合；不会据此启用所有槽的完整已观察标记。本轮只改源码，未构建、审查、测试、打包或同步，ABC 未完成。

堆来源残留恢复入口继续接入：实际字段通过 JNI／JVMTI 声明 ID 读取，未初始化的静态类不因采样而初始化；数组读取实际下标。准确指令写入保存同一槽的前值、后值、旧来源及连续版本，普通字段守卫与该指令共用窗口，避免把同次操作重复计作并发。恢复票据连接现有整组恢复链，重新取得该载体的原字段门后，校对记录版本、当前来源、存活的前后引用和实际停用状态，再走实际字段／数组恢复；已经停用的旧贡献不会借回退重新出现。浮点值按原始位比较，引用按实际对象比较，这些比较只用于同一已选槽的条件恢复，不用于推断来源。同一来源的连续修订合并；丢失的弱前值、共享／未确定来源、进行中的写入以及没有完整观察覆盖的槽均不作为可恢复票据。Native 数组恢复与字段采样声明同步为 ABI 15。来源查询覆盖普通生产 Mod，并按本次真实查询结果替换旧查询缺口；实际字段事件失败计入现有退休缺口。完整低层 writer 覆盖尚未启用，因此本轮是恢复路径源码接续，不能代称残留已清理。没有构建、审查、测试、打包或同步，ABC 仍未完成。

写入来源继续接入具体位置：普通字段、实际反射 Field 与 JNI jfieldID 接续真实声明类、字段名及描述符；Java 数组 setter 与 JNI ArrayRegion 使用实际下标／区间，按位置记录开始、完成与取消。确定位置的窗口不再使同一载体的其他位置一起失效。数组区间使用可分割、可合并的紧凑来源记录，新读取位置接续其所在区间，区间内后来物化的槽也加入进行中的写入窗口。并发或其他观察交错时保留相关来源并标记未确定，失败时只在对应窗口没有交错的条件下恢复旧来源。Unsafe Java 入口接续实际目标偏移与写入长度，依据真实数组 base／scale 和字段 offset／base 选择触及位置；无法解析的范围仍保留整载体未确定处理。来源残留检查同时纳入数组区间与进行中的窗口。Native 字段与数组入口声明同步为 ABI 14。这些均为本轮源码增量，未构建、审查、测试或同步；槽读取仍未启用完整已观察声明，直接低层 Unsafe／原始地址写入覆盖、残留值处置和其他 B/C 范围继续编写，ABC 未完成。

目标模式继续完成 B/C；当前公开候选仍是下文已验的 A 候选，工作区增量尚未打包或同步。
本轮没有构建、测试、实机运行或新增探针。完整工作包写齐后，仅集中检查实际漏清、防护绕过、确认来源回填、组外误伤、控制失效及阻止这些动作运行的编译／链接／安装问题；不重跑未受影响的已通过路径。

按用户最新要求，后续优先把剩余代码写齐，再集中找 bug 和做上述最少强度检查；本轮中途 Astra 只读审查已停止，不再边写边启动新审查。

字段写入的原生通知与写入窗口继续写入源码。通知登记以实际载体、真实声明类和 jfieldID 关联，弱引用随载体失效收尾；同一真实执行计划和指令的在途写入接续原记录，其他已观测写入使旧来源失效。此处使用 [JVMTI FieldModificationWatch](https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#SetFieldModificationWatch)，该通知覆盖 Java／JNI 字段修改，不代称全部 Unsafe 或原生直接内存修改已经可见。反射 Field 的实际 accessor 调用、已有数组写入助手、JNI 字段／数组提交及 sun.misc.Unsafe 的已准入 Java 方法已接入开始／完成／取消收尾；CAS 未写入和明确异常按未提交处理，存在重叠写入或载体版本变化时保留未知来源。字段来源和数组槽残留也接入整组停用缺口，未清份额不因活动帧消失而结清。Native Java 声明和 C 导出同步为 ABI 13。完整低层 Unsafe／VarHandle、全部数组写入、精确范围和来源份额清理仍待继续，读取尚未启用全入口已确认标记；ABC 未完成，本轮未构建、审查、测试、打包或同步。

构造器初始化前的参数、局部值、引用别名和实际分支追踪已继续写入源码。原接收引用的指令流分别定位真实 super／this 初始化调用，多条初始化路径和原异常处理保留；初始化成功后才绑定保存的实际对象，不把未初始化接收引用传给 Java 观察函数。初始化前写入自身字段的来源先暂存，成功后关联准确载体和声明槽；父类初始化可能改写的当前值继续保留未知标记，不将暂存来源冒充最新写入。退出订阅须对应实际线程帧及真实 VM 代码计划，原生正常／异常退出通知收尾同一帧，保留原异常并释放订阅引用；订阅不可用、通知不对应或回调失败继续报缺口。该路径使用 [JVMTI NotifyFramePop／FramePop](https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#NotifyFramePop)，主动 JVMTI PopFrame 不产生此通知，仍属于未观测退出。Native Java 声明和 C 导出同步为 ABI 12。根构造器、全入口堆写入观察及其余 ABC 范围仍未写齐，本轮未构建、审查、测试、打包或同步。

字段／数组的载体登记和引用接续继续写入源码：参数与正常返回绑定实际引用，NEW 的未初始化引用只保存别名，在原构造调用成功后绑定真实对象；局部复制、栈复制、类型转换及直接调用参数沿同一别名接续。原 JVMTI 按准确类的声明字段、描述符和真实接口／父类关系定位字段，不枚举会加载字段类型的 Java 反射字段。字段以实际载体与声明槽定位，数组以实际数组与下标定位；写入完成、异常取消和已观测的重叠写入分别收尾，不通过相同标量值、包装对象或 UUID 推断来源。全入口写入观察后端尚未写齐，读取保留未知标记，不把最后一次登记当成当前内存值已经确认。这个未知标记继续传递给创建记录、Core 组对象选择和既有停用缺口查询，未知对象不按已知的单一来源直接处置。创建／任务观察保存真实发出字节数组的私有收据，并按最终指令身份重新登记观察位置；定义检查的追加输出也接续这份收据，隐藏类仍按准确 Class 发布。该段接口在前轮同步为 ABI 11，本轮退出接续再升为 ABI 12。初始化前字段写入的全入口观察、完整字段／数组写入口与回收、句柄／反射／Unsafe／JNI 接续，以及其余 ABC 工作仍继续编写；本轮没有构建、审查、测试、打包或同步。

运行时值来源的第一段实现已接入源码输出：普通方法按最终 JVM 指令建立参数槽、局部值和操作数来源，记录实际执行的分支、返回及异常交接；观察调用不把业务标量装箱作为对象归属。入口通过原 JVMTI 的真实线程帧、方法字节码、自有常量池和异常表选择对应执行计划，旧活动方法只使用能对应自身代码的计划，隐藏类沿准确 Class 关联；计划由对应定义持有。调用参数交接须对应直接父帧的位置、实际栈深度和参数槽宽度，正常返回只接续实际返回来源，未观测调用保留未知标记。变更或删除跳转的已记录贡献仍按原控制区域加入实际执行指令。编织入口、调用返回、原异常处理和正常／异常退出位置在同次最终输出后登记，执行中的确认来源接入既有创建／任务／Native 来源与停用判断。控制状态按准确对象保护并沿已有弱引用收尾。构造器前缀追踪已按上文继续接入，根构造器、旧式 JSR／RET 和无法映射的退出仍保留相应来源的缺口；完整字段／数组载体、别名与未观测调用仍待继续。以上没有编译、审查、测试、打包或同步，不代表运行效果或 ABC 完成。

确定接收类的虚调用继续接入原业务图：单独保存 NEW、常量、final 类型、成功类型转换和数组元素读取的接收类信息，沿真实局部变量复制及分支汇合保留，未知分支不会作为已知类型。已发起加载的类型使用准确 Class；尚未定义的自身 NEW 使用该图像自身身份，不能替换为同名普通类。虚调用按实际类声明链及接口默认方法选择，跳过不构成覆盖的 private／static 声明，并保留默认访问方法经过 public／protected 中间声明的传递覆盖关系；运行时包比较只服务 JVM 方法选择，不用于归属判断。多个已知接收类须最终指向同一实际定义的方法体，才接入既有正常返回依赖分析；不同实现不合并成一个来源。该选择依据 [JVMS 方法覆盖与选择](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.5)。以上尚未编译、审查或运行；运行时未知接收对象、方法句柄／可变调用点和完整堆值接续仍需继续写，ABC 未完成。

跨类继承与接口默认方法继续写入源码：通过原 JVMTI 的实际声明方法、描述符和标志区分“没有声明”和“无法读取”，查询不借助会加载参数类型的反射方法枚举；声明信息不代称方法体已对应，选中体仍须与已登记的实际 VM 版本匹配。静态调用沿实际类声明链选择，接口静态方法不继承；特殊调用按实际父类起点、接口声明、Object 的 public 实例方法及唯一最具体默认方法选择。抽象子接口声明参与遮挡父默认实现，多项默认实现不能任选一个作为返回体；final 类的继承默认方法也接入确定目标。新定义类的自身继承调用只使用该图像及已实际发起加载的父类／接口，隐藏自身引用不借用同名普通类。接口变化沿实际类及接口关系继续通知既有调用者。实现依据 [JVMS 方法解析](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.3)、[invokespecial 选择](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.invokespecial) 与 [JVMTI GetClassMethods](https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#GetClassMethods)。Native 查询接口与 Java 声明同步为 ABI 9；以上仅为源码进度，尚未构建或运行，动态接收对象与其余 ABC 范围继续编写。

隐藏类的对象／工厂和任务入口继续写入：定义前在同次最终输出中加入实际 NEW、数组、资源工厂、输入字段与工厂返回观察，观察位置直接取该次输出的真实指令位置，在定义成功或进入原初始化方法时绑定准确 Class；同名或相同内容的另一个隐藏类不能沿用位置登记。创建观察的调用者遍历包含实际隐藏帧。任务入口从实际 JDK Runnable、Callable、各函数式接口、执行器／线程工厂和 ForkJoinTask.exec 合约取得方法描述，运行时还须实际接收对象实现该合约；方法名只用于选择入口。已登记的隐藏任务执行进入既有任务帧、准入和 BODY 观察，正常与异常离开关闭原帧；同一接收对象已经在执行时保留已有帧，停用返回复用原组策略。未经本次写入的观察调用不登记为有效位置。上述源码尚未编译、审查或运行；完整动态分派、堆载体／标量接续、共享任务和后继资源、其他 Native／Host／世界／通信／客户端范围仍继续编写，ABC 未完成。

隐藏定义继续接入源码：实际 Lookup 定义入口传递隐藏模式，定义前复用业务图与最终指令来源接续；实际初始化入口或原定义成功返回用本次作用域的真实字节数组及实际 Class 发布关联。同一份代码创建的多个隐藏类分别保留实际类身份与独立业务图；相同内容、不同输出数组的定义不混用来源。方法、字段和常量恢复项按准确隐藏 Class 登记，原生执行帧按该实际类对应的代码图像接续。VM 改写隐藏类自引用时，以该常量池中实际已解析到所属 Klass 的引用对应原 this_class 项，其他类常量仍沿原语义匹配；不按隐藏名称前缀推断归属。布局依据 [OpenJDK 17 隐藏类解析](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/classfile/classFileParser.cpp) 与 [VMStructs](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/runtime/vmStructs.cpp)。Native 接口与 Java 声明同步为 ABI 8。需要更新而实际隐藏类不可重转换的路径保留原未决动作；完整动态分派、隐藏定义中的对象／工厂／任务后继、堆数据流及其余 Host／世界／客户端范围继续编写。本轮没有编译、审查、测试、打包或同步，ABC 未完成。

存档版本转换继续接入原 `Dynamic.updateMapValues` 的完成返回：普通实际 Dynamic 使用原 NbtOps 改写字段映射时，原 value 与实际返回 CompoundTag 接续已有记录身份和未定位迁移关系。自定义 Dynamic 子类的可覆盖读取／创建语义不借用这条关系，任意 `Dynamic.map` 回调也不作为复制观察。此源码与此前 NBT 合并、删除字段及 builder 输出接续均未统一编译、审查或运行，ABC 尚未完成。

外部方法接续继续写入：实际业务图像与最终检查插入后的 VM 图像分别保留，方法须先对应当前实际字节码，才以原业务方法分析返回依赖；已替换为停用返回的业务体不沿用旧返回值。实际声明／动态定义来源属于普通生产 Mod 的方法，也接续其声明来源。真实 ClassPrepare 与原 Instrumentation 重转换／重定义成功返回通知私有后台更新，按实际发起加载关系找已有调用者，只更新来源行发生变化的类；原更新返回后仍对应实际方法与安装来源行，失败保持该动作待处理。未获 VM 接受的新输出保留此前能对应的图像，后续通知可重试。这里尚未构建、审查或运行，也未闭合委派加载后的首次使用、动态／隐藏分派、常量引导参数、完整异常处理布局与堆数据流，不能代称全部外部方法或 B/C 已完成。

当前 VM 方法版本继续写入 OpenJDK 17 后端：从实际 JNI／JVMTI 函数对应的 VM 映像读取导出的结构布局，由实际 jmethodID 接续该方法自己的 ConstMethod 和常量池，保留当前方法及旧活动方法各自的数据。原 classfile Code 异常表与实际 VM 异常表按顺序、真实指令边界和 catch 类型对应；原 BootstrapMethods 与实际方法常量池的引导句柄、参数及嵌套动态常量对应，不以引导表序号相同代称参数相同。常量关系采用有去重的迭代队列，实际方法／ConstMethod／常量池关联变更时保留未决；无法读取的布局、其他 VM 版本及特殊常量不猜测匹配。只读取匹配中实际引用的常量，不新增全包摘要或自证探针。源码依据 [OpenJDK 17 VMStructs](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/runtime/vmStructs.cpp)、[ConstMethod 布局](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/oops/constMethod.hpp) 与 [ConstantPool 布局](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/oops/constantPool.hpp)。Native ABI 接口仍为 7；构建输入加入新的元数据源码及已存在的 Host 源码。以上尚未编译、审查或运行，实际动态调用结果、隐藏定义、完整堆数据流及其余 Native／Host／世界／客户端范围仍需继续，ABC 未完成。

Windows 原始进程创建调用已接入 Host 源码：真实 JDK ProcessImpl.create 的实际绑定与导出对应后，只替换该代码映像中的原 CreateProcessW 导入。实际 ProcessImpl 构造中的原 create 调用也接入创建前后作用域，原生方法已绑定时仍能通过准确的 JDK 映像导入接续创建，异常保留原 Throwable 并收尾本次作用域；已存在进程不会凭 PID 追认。已知生产来源创建的新进程先保持挂起，关联到独立、未命名且不准脱离的 Job，再恢复原执行；保存实际返回句柄的副本与同次调用发布的原 Process 对象。真实流访问器与 toHandle 发布合并实际使用来源，未知或组外共享保留待处理。整组停用接入 Job 退出，后续处理等原系统计数归零、原进程退出及每个自有句柄关闭成功后收尾；Java 原管道与 Cleaner 仍沿原对象关系处置，创建失败的原进程／线程句柄也保留到实际释放。Native 接口与 Java 声明同时升为 ABI 6。上述代码尚未编译或运行；安装前既有进程、未进入已接入构造调用的旧原生绑定、绕过该 JDK 创建路径的原生创建、WMI 等不继承 Job 的派生路径、其他 Host 权限／签名驱动／文件权限与常驻后端仍未完成，不能据此宣称完整 Host。派生范围参照 [Windows Job Objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects)。

新增外部代码来源与变换使用端源码：从实际 ModuleLayer／ModuleReference／ModuleClassLoader 的字节读取关联到真实 ASM 解析、复制和输出，再关联真实 MixinInfo、MixinTargetContext、变换器包装对象及其应用调用。实际变换前后保存指令身份和异常处理表，整组停用纳入已记录的组外定义，尝试逆转选定贡献，新增普通方法复用该组返回策略。后续目标变换器跳过投票并保留输入；已知目标 Mixin 跳过应用。来源使用实际参数、对象、模块与数据流，不按 Mixin 名称猜归属。

上述外部路径尚未构建或运行。本次继续接入实际执行位置：变换贡献按原始指令接续到字段、创建和任务入口；原生端保留方法图像，并使用实际 VM 方法、字节码、常量池与位置匹配当前／旧活动帧。JNI 操作、注册和数组／字符串租约接续实际调用帧来源；无法对应的方法或未退出的调用仍报未决。对象与任务保存多个实际来源，只有这些来源全属于同一选定组时才纳入专属对象处置。当前 Native 源码 ABI 为 6，公开 A 候选不变。

外部新增字段也已接入源码：按实际定义中的新增字段贡献登记来源，真实写入入口记录实例载体；直接、反射、句柄和 Unsafe 范围写入沿实际字段限制回填。真实 VM ClassPrepare 接续后续定义，桥登记必须对应当时准备中的实际类，不能用一个类名请求冒充 VM 登记。整组清理枚举已初始化类的静态字段和已观测实例字段，只清选定贡献的字段，复用现有写门和字段清理；正在初始化的类保持未决，不为读取字段触发初始化。这些代码未构建或运行，不能作为清理通过结果。

本轮继续写入配置和安装使用端：从实际 ModuleReference、原 ModuleDataProvider 的资源定位／打开、原 URL.openStream 和 InputStreamReader 接续到实际 MixinConfig JSON 解析对象，再保存原 Config handle、父配置和真实登记调用来源。prepare、选择及原 Mixin context 使用这份对象关系；目标来源停用后限制后续安装和应用，不根据配置名称、路径前缀或 Mixin 类名猜所属。TransformerHolder 也继续合并实际实现对象、service 对象和安装调用的来源。未观测的读取路径仍没有凭字符串补出来源；这些代码未构建或运行。

既有字段改写和方法内依赖也已继续写入：未激活定义可逆转实际字段模式改写及移除，已激活定义恢复可独立提交的 signature／annotation 等属性，实际 VM 字段布局变更仍保留缺口。可对应的原 static final ConstantValue 改写保存前后值及贡献来源，当前实际字段仍与改写值匹配时，经原写门和 Native 字段后端恢复原值并回读；原初始化代码另写该字段、正在初始化或组外后来写入不同值时保留未决。恢复项按准确对象收尾，不把已有字段当新增字段整项清零。实际方法的操作数、局部值、返回和分支／异常控制边接续来源；已观测的改跳转、删跳转和新增无条件跳转保存原路径比较，按合流边界传播到后续使用点，再接入既有字段、创建、任务和 JNI 执行帧来源。以上均未构建或运行，不能作为效果通过结论。未观测实例、完整跨方法／动态控制数据接续、重叠改写、外国 Mixin 载荷重放、完整 VM 字段布局及 Native／Host 后端仍需继续；其余世界、通信、界面和绘制路径也继续编写。

本轮继续写入外国 Mixin 载荷重放：真实 ASM 输出被重新解析后，按准确载体、字节图像和实际指令／异常处理项接续原变换历史；原 ModuleReader 也须对应真实 ModuleReference.open 的发布关联，不能借另一个 reader 与 reference 配对声明来源。Mixin 初次加载尝试逆转已停用的外国贡献，未激活载荷可处理记录中的方法／字段／类属性；已经准备的载荷沿原对象与类信息缓存接续后续 context 创建，具体重建方式见下段。实际原状态、原输入及发布不对应，或改写重叠无法逆转时，原来源仍留在应用限制中。旧 context 与新载荷来源合并，不能因新状态发布就抹掉旧 context 的贡献。上述代码尚未构建或运行；配置重索引、共享缓存接续、重叠修改重放和完整跨方法／动态接续仍需继续，不作为任意外国载荷均已还原的结论。

已准备 Mixin 的类信息重建继续写入源码：可逆转的载荷经安装库的真实 ClassInfo 构造和新 State 的原 validate 流程重建成员、签名、接口及方法帧信息，再对应原 Mixin 对象和缓存中的准确旧 ClassInfo 发布。方法体重放也使用新类信息，避免继续复用旧帧数据；校验或发布失败只回退仍对应本次写入的对象，旧来源继续保留。真实 TargetClassContext 构造保存当时选择关系，选择来源按已记录的目标注解／优先级／Pseudo 改写及原配置关系接续，方法体贡献仍由实际原 clone 接续。ClassInfo 构造保存实际输入图像，原变换调用保存捕获对象和来源，结束时关联原图像及实际元数据载体。以上未编译或运行；其余共享元数据使用、跨模块依赖和重叠改写重放仍需继续，完整 VM 布局及其他 Native／Host 目标也未完成。

本次源码接入配置的真实目标查询：在原配置查询前重建已准备 Mixin，按原构造记录的插件模式重新读取目标声明，并用安装库原方法计算优先级和 Pseudo。发布同步更新原目标名字／类列表、配置 mixinMapping、unhandledTargets 及对应 ClassInfo 的原 mixins 集合；只撤下同一 Mixin 对象的关系，失败恢复本次实际写入。加载种类改变同步原 ModLauncherClassTracker 的登记：记录原 registerInvalidClass 调用、实际 HashSet／HashMap 项身份、原参数和对应 Mixin，按新 SubType 的真实 isLoadable 改变限制。来源不明、共享登记、已加载类或不支持的 tracker 保留未决；不会按类名字猜测可删除的登记。尚未编译或运行。

逐成员元数据来源继续写入：ClassInfo 的构造输入按类头、字段、方法标志和实际帧信息保存来源行，原查询命中／未命中、成员复制及已观测的改名、重映射、unique／final／mutable／conform 变化接续对应成员。Mixin 的实际准备状态和 MixinTargetContext 的实际方法调用记录这些读取，准备状态重建与旧上下文分别保留自己的依赖；已停用来源的旧上下文或元数据读取被拒绝。原变换结果同时保存实际写入者与读取依赖，仍由组外模块生成的依赖结果保留重放未决，不直接整块逆转。这里只覆盖已接入的原库构造、查询和修改入口，不代表任意共享元数据、原始内存改写、全跨过程依赖和重叠贡献已完成。以上全部为未验源码，原候选和公开仓库尚未使用这些增量宣称 B/C 通过。

跨方法依赖的源码继续写入真实类图像分析：同一已记录类中的静态、特殊调用及可确定的 final／private 调用按真实指令对象关联方法，按全部方法的返回依赖迭代接续，原来未直接改写的调用者也纳入来源行。参数符号按真实描述符和局部变量槽位建立，区分接收者及 long／double 的双槽；返回值读取所需参数与所有正常返回都携带的来源分别记录，分支汇合保留交集，既有控制依赖继续接续。返回依赖加到返回后的数据使用端，不为一个可能正常返回的来源跳过整个被调用方法；无法确定的虚分派、跨类、原生／动态调用和堆值来源仍未完成。这些源码尚未统一编译、审查或运行，完整 B/C 的收口和 GitHub 同步继续等待整批代码写齐。

本次将调用分析核心改为多图像迭代：各图像及方法按定义对象身份分别保存，调用目标解析器返回对应的定义身份和方法选择，解析结果只关联同一条真实调用指令；同名类和同名方法不合并。既有类内调用已改用这一核心。跨类图像与 JVM 实际类的登记、动态分派及未观测定义的完整接续仍需继续，当前没有新增编译、测试、审查或分发结果。

跨类登记已继续写入源码：保存变换出口实际发出的指令图像和来源行，由真实 VM ClassPrepare 通知关联实际类对象；已加载类经原 ClassLoader.findLoadedClass 的实际发起加载关系选择，查询不主动加载或初始化目标。原生端按实际类、方法标志、字节码和常量池对应保存版本，只把能对应的方法接入调用图。已登记的静态、private／final 调用与普通父类特殊调用沿实际声明类分别接续；停用选择同时纳入图中具有选定来源的已登记调用者，避免只枚举直接改写的类。原生接口源码同步为 ABI 7，公开 A 候选不变。动态调用／常量引导参数、接口默认分派、未登记的旧定义、后加载目标的即时调用者回补、实际条件值与完整堆数据流仍需继续；VM 异常处理布局的完整版本接续也未完成。以上尚未统一编译、审查、运行、打包或同步，ABC 仍未完成。

B 批继续接入普通 NBT 读写和 SavedData 的实际读取链：原 FileInputStream、PushbackInputStream、DataInputStream 的接续覆盖原版压缩与普通分支；原文件写入观察同样接入普通 DataOutput，保存和事务投影保持实际输入的压缩／普通格式。已确认来源经原加载函数的真实输入、真实返回 SavedData 和真实缓存发布关联到同一载体；已终结且能准确定位的记录先从输入投影中撤下，再交原加载函数。版本转换沿实际 DataFixTypes.update 调用接续，实际记录身份及原 CompoundTag.copy 分别保存，不能仅因同一 subject 仍有一份记录就把其余记录的丢失也视为完成。无法定位的转换结果保留该记录的未决关系，并限制已终结来源的后续加载和落盘；没有以相同 UUID、路径或字节内容猜补记录归属。观察关联随对应来源关闭收尾。上述代码未编译或运行，未观测转换路径、真实字段数据流、共享载体份额、全部持久恢复与容量收口仍需继续，不作为 B 批完成或 O02／O05 通过结论。

版本转换继续接入原 NbtOps 的实际 map 输出：单项／MapLike 合并、含实际部分结果的错误返回、删除字段和原 NbtRecordBuilder 的 prefix 合并，把原 prefix 记录身份与实际产生的新容器关联。原 error supplier 保持原样，不为观察提前执行；原 builder 的私有新 map 构造按实际类及方法类型接续图闭合关系。复制观察入口要求真实 CompoundTag 调用和核心接续，NBT 操作观察要求安装库的实际 NbtOps／NbtRecordBuilder 类对象，不能从外部直接发送复制通知冒充来源。流重建与跨编码器的完整记录接续仍需继续。全部是未验源码，没有新增编译、审查、测试或候选结果。

原生清理入口另加入实际清理调用者关联，自动 Cleaner、真实恢复调用与原始资源消费者分别识别，外部手动借用清理对象不取得卸载权限；此改动同样未验。

本次继续写入 C 的通信使用端：真实 NetworkInstance 创建、Simple／Event wrapper、codec 与消息处理器登记关联到实际模块和调用来源；共享 channel 中按具体处理器限制解码／编码，并接续主线程任务。Minecraft 的接收／发送入口与实际 Connection 包队列纳入停组，队列只移除已确认的目标包。Netty 发送、编码、WriteTask 初始化／运行／回收保存实际数据和派生操作来源；编码后的数据继续受限制，回收在对象重新发布到池之前撤下旧关系。JNI 注册及数组／字符串租约也接续这些实际通信作用域，作用域结束后的租约仍保留来源。旧分派、未结写项和无法对应的队列保持未决，局部 Promise 完成不作为远端收到消息的证明。

以上通信代码尚未构建或运行。Netty 任意 pipeline handler 的全量份额退役、低层已发布字节／共享 buffer 的完整处置仍需继续写；不会把当前几个发送入口当成所有通信路径已闭合。A 候选和实际报告未改成 B/C 通过。

本次接入客户端实际贡献调用：Entity／BlockEntity renderer、HUD overlay、界面及子控件输入、粒子、Toast 和可 tick 声音按真实贡献对象及来源限制，框架的其他调用和收尾保留。客户端作用域接续任务、派生对象和 JNI 租约。纹理登记记录实际 TextureManager 映射与成功加载的对象，不按资源命名空间判归属；原始输入与加载回退对象分开，目标登记不会把共享缺失纹理当成专属资源。实际纹理 tick 尝试退役所有准确且专属的映射份额，共享或无法释放的资源继续报未决。粒子通过实际基类 removed 字段退出，原引擎负责队列和数量收尾。这些代码未构建或运行，任意材质／声音底层资源、已经聚合的绘制份额和全部重载接续仍需收口。

字段图源码已连接现有命令权限、运行时 tick 和关闭流程：`fields snapshot/clear/restore/status/forget` 操作准确目标身体，默认只采集实例字段；明确选用 `declared-static` 才处理目标实际类自己声明的静态字段，不顺带处理基础类全局状态。遍历继承实例字段、真实数组元素和已确认的专属派生对象；共享世界容器保存引用边，不以 BackingPolicy 的保护关联冒充私有所有权。写入覆盖真实 primitive／reference 字段和数组，浮点比较保留原始位；隐藏／record 字段另接实际 JNI Field 身份的恢复入口，静态访问不触发类初始化。清空前等待身体处置完成及已关联标准文件流退役，恢复后检查实际身份冲突并重新入世；停止的整组来源从自身字段图引用中撤下。这里是会话内增量读取的活对象引用图像，不能称为持久、原子或任意共享图的完整快照；未确认资源生命周期、来源和访问仍保持未决。上述代码未构建或运行。

客户端资源退役继续写入实际生命周期：纹理映射脱离后保留原对象，等待实际基础类 GPU ID 释放；DynamicTexture 的原始像素关联与 NativeImage 构造、close 入口共同记录原分配及释放回读，外部已知份额保留，无法确认的暴露和自定义资源继续待处理。调用准确框架实现，避免把被抑制的目标 close 覆盖当成释放完成。声音清理连接实际延迟播放、循环登记、SoundEngine 原 sound／handle 配对和声音执行器，实际 stop 后仍等待原 handle 的 channel 清空及 ChannelAccess 登记退出；不会整池释放组外声音。这些代码尚未构建或运行，完整重载、聚合绘制、任意自定义资源及 Native／Host 广度继续编写。

重载接续源码已连接真实 ReloadableResourceManager 监听器登记及原监听器集合。已确认专属的目标监听器从准确集合份额撤下；已开始的实际 listener.reload 调用保存真实 future，停用分支通过原 SimpleReloadInstance 屏障继续框架等待，避免让剩余监听器一直等一个已跳过的参与者。ProfiledReloadInstance 同走实际调用点，仍生成框架自己的状态对象。普通 future 完成只撤下该本地观察项，不代称所有派生工作或旧活动帧均已退出。共享、未对应及正在运行的重载仍报告待处理，以上代码未构建或运行。

混合绘制继续写入实际图元来源与着色器后端：BufferBuilder 的真实 begin、endVertex、批量顶点、原 RenderedBuffer 和 SortState 接续到实际 VertexBuffer 上传及排序索引，按原始顶点来源识别专属／共享图元；绘制保存原 VAO、顶点及索引载体关系。GL 原片段输入、编译及链接接续真实 shader/program ID，支持的原 main 加入私有图元遮罩，原绘制和图元编号保留，自己的 uniform、texture-buffer 绑定在绘制结束后恢复；编译或链接不支持时恢复原片段源码，不把该后端记为可用。全部已确认图元均属目标时直接跳过该准确绘制；混合共享图元、未观测上传及不支持的管线保留待处理。自身遮罩资源在真实 Window 关闭前释放。这些代码尚未构建或运行。

GPU 原载荷处置已继续写入：实际上传前保存原顶点字节、真实 stride 和索引关系，只有来源明确且不被组外或共享图元使用的顶点范围才擦除；原 VBO 当前字节必须仍与观测载荷对应，擦除后回读同一范围。真实客户端 runTick 接续原 GL context 中的退役，使用实际 GL fence 等待已提交工作；准确载体、原 VBO／EBO／VAO 和对象来源均专属时调用真实框架 VertexBuffer.close，并等待原 ID 清空。共享缓冲保留组外部分，未对应载荷、变化的关联和 GPU 工作继续待处理。完整 GPU 副作用、program／shader 资源及任意原生绘制仍未收口，不能以遮罩或局部字节清除代称 O02／O03 全部完成；本轮没有构建或运行。

区域及时间控制源码已接入现有 ProRuntime 命令权限：范围实体清除、受方块遮挡的射线选择、实际传送、临时禁生成、区域／当前维度时停和提前恢复。按准确 Level 与坐标选择，冻结对象保存实际身份，玩家的连接及命令处理继续运行；暂停区域的原始 ScheduledTick 保留类型、优先级、顺序和到期时间，恢复时重新交回原容器，未加载区块保持等待。随机 block／fluid tick 与 BlockEntity tick 按选定位置限制；玩家及载具位置包在主线程交接后处理。此处尚未覆盖全部天气、raid／自定义世界作用、世界历史、种子重生成与客户端时停表现，不能把这些入口写成完整世界时停已通过。仍先写齐整个 ABC 工作包，再统一查 bug、构建及必要强度检查。

本轮继续加入世界历史与种子重生成源码。`world snapshot`、`world rollback` 和 `world regenerate` 分别受理历史采集、读取既有状态及使用当前实际世界种子／生成器生成新状态；可查询、取消和接续失败工作。历史按真实选定区块保存压缩记录及进度，保存范围、实体 UUID、BlockEntity、原计划 tick；完整选中区块同时保存 biome 和结构登记。工作只持有实际申请的目标区块 ticket，暂停对应区域的常规 tick；完成／取消／失败撤下自身规则与 ticket，保留已经发生的作用。历史记录先写临时文件并 force，再进行原子替换；日志回执与游戏世界的正常保存分开显示。

种子生成保留新的 ProtoChunk 与真实生成依赖，调用当前生成器的结构、biome、noise、surface、carver、feature 和原生物生成阶段；依赖邻区只作为生成数据使用，不替换现场邻区。提交按选定坐标逐批写回，经实际 BlockEntity、实体和计划 tick 入口处理，并等待实际光照任务后向当前 tracking 玩家发送区块数据。实体恢复继续遵守当前整组停用，不能借历史回溯复活已停用组；未结身体处置保持等待。以上尚未构建或运行；NBT 区域记录不冒充全部任意字段／引用图快照，完整外部修改截点、全字段接续和实际世界持久提交仍待收口。旧 A 候选、报告及 GitHub 分支尚未用这些源码宣称 B/C 通过。

当前增量继续补 C 的真实原生来源。新增 JDK 实际加载／卸载作用域、真实清理对象关联和准确 native load／unload 入口；桥调用须经过真实 JDK 入口，加载还须位于实际 NativeLibraries 登记调用与上下文中。DLL 来源采用实际调用者、承载类、操作参数与进程映像关联；加载中的并发注册和数组／字符串副本租约也记录实际 JNI 调用图像。仅真实 RegisterNatives 提交携带安装来源，普通首次解析与外部类自行初始化加载不沿用外层 A 的调用栈；保存已观测的旧实现供跨模块改写停用后恢复。受保护的准确声明类审核真实实现和调用来源，控制 DLL 的函数别名及虚假控制类被拒绝。加载参数使用同一私有快照，实际加载结果决定映像状态，JDK 自己的句柄登记不能因中途停组而丢失。Native 源码此前升为 ABI 3，本次接入执行帧来源后升为 ABI 4，仍未构建、链接、安装或运行，以上均为实现进度，不是通过结果。

此前 Astra max 仅只读审查当时的新路径，未运行测试；按最新要求，中途审查已停止，本次没有重新启动。当前原生库尚在加载、仍驻留、共享退出未决或实际调用未退出时保留缺口；不会把 Java／JNI 调用计数归零写成 DLL、系统线程或任意裸原生内存已经终结。完整外部注入代码、其余世界／通信／界面／绘制路径及选定 Native／Host 后端仍需继续，B/C 尚未完成，公开 A 候选及原报告结论保持原范围。

当前源码已接入整组来源／标量与引用处置、原意图和重启规则、真实 Timer 和私有池退役，以及动态／隐藏定义与 JNI 绑定／字段／数组控制。新增修复包括：Lookup 的实际创建和派生来源；私有数组副本与 COMMIT 生命周期；JNI 注销后的绑定状态；数组／字段提交和清理共用写门，清理拿不到门时保留待处理；弱引用来源读取；只有实际退出的线程和实际终止的池才结清。

本次继续修复继承静态字段的写门与组判断：先解析真实声明类，不能用组外符号子类代替；JNI 注册先固定整张方法表及名称／签名，再逐项审核真实方法声明类，防止借子类改写平台父类。JDK 文件资源已加入构造／真实 FD 附着／懒通道关联记录；不再仅凭当前可变字段认定私有资源。借用视图退役保留共享 FD，专属 FD 关闭后读取实际状态；关闭动作由有界后台队列执行，控制循环查询结果。缺失原始关联的通道、未完成的关闭及变更的关联继续未决。这些改动尚未构建或运行。

Windows Process 原资源处置已写入源码：真实 ProcessImpl 构造、原 Cleaner 登记及清理动作保存原进程句柄、原流／FD／缓冲和管道发布关联；实际 native closeHandle 的返回结果只在对应原清理动作作用域中接收，避免按可复用 HANDLE 数值认领释放。管道访问继续合并真实调用来源，共享或来源不明的视图保留；私有管道关闭后等待原 FD 和缓冲收尾，再执行原进程清理对象。实际 ProcessImpl.start 的后续创建受停组限制，startPipeline 的父端 FD 按真实 RedirectPipeImpl 发布跟踪，等待 JDK 原始关闭。整组退役和字段图清除均已调用这条路径，处置仍由有界后台队列执行。构造前未观测进程、未退出或未释放资源，以及绕开此 JDK 入口的裸原生派生进程继续未决；尚未编译或运行。

这些都是未验源码增量。B/C 尚未完成：外部注入代码的完整来源控制、共享来源与资源广度、选定 Native／Host 后端，以及世界／通信／界面／绘制／区域／时间和视觉仍需继续实现。不得把 A 的通过结果沿用为 B/C 的通过或全部来源终结。

## A 批最终收口（当前结论）

**A 在 V1.5 已声明的范围内完成实现与当前效果验证。** 当前交付为 `distribution/candidate-modwide-a-complete-20261001/`。运行中整组停用仍按实际未控路径报告部分覆盖，不能把这项 A 收口结论理解为任意 Mod 的隐藏类、Native 或完整来源链全部失效。

本轮补齐同一实际 Module 的所有 `[[mods]]` 所有者合并、多组共用一次已加载类快照、六种返回策略、普通业务构造拒绝、逐组未控状态、策略对应的基础客户端同步。普通构造在合法异常入口拒绝，不执行原构造前缀、不交出半对象；Forge 必需的 `@Mod` 承载实例保留父类初始化并停业务后缀，无法控制的承载前缀继续报缺口。

进一步修复了目标工厂直接创建原版实体时的漏清：复用已有经过实际字节码调用点认证的创建观测，记录真实创建 Module，清除和防护均纳入这些当前／后续对象；不会按名称或命名空间猜归属。Mod 自定义 Item 实现的掉落物也按实际实现 Module 纳入。

| 当前直接场景 | 实际结果 |
| --- | --- |
| 六种普通返回策略 | `default`、`null`、`empty`、`uuid-fixed`、`uuid-each`、`invalid-id` 的真实模块外消费者均通过；所有基本类型／void、引用／数组、UUID／字符串、标准空消费者与 Future 合同成立；原始业务效果计数为 0，业务构造在前缀执行前被拒，组外 Java 框架继续工作。 |
| `mod-group-dynamica-factory-final2` | 无效组不阻塞有效组；同模块两个所有者均纳入。清除 4 个实体、1 个非 ticker 方块实体，包含目标工厂创建的原版 Cow 和 ItemEntity；目标 tick／隐藏事件委托／已登记命令回调停止，世界命令响应，服务端 exit 0。 |
| `mod-group-protecta-factory-final` | 当前／后续自定义生命与非生命实体，以及工厂创建的当前／后续原版 Cow 与 ItemEntity 均抵御 /kill；方块、准确 Map 与反射／Unsafe 字段防护成立，无关条目仍能清除。原始 removed=null、准确 UUID 索引和真实未来 callback 保留；世界命令响应，服务端 exit 0。 |
| `mod-group-boota-thread` | 启动前选集生效，目标方法和隐藏监听器未执行；自动纳入两个所有者，UNRESOLVED=0，世界命令响应，服务端 exit 0，原等待时限未增加。 |
| `mod-group-client-a-final` | 同一交付 Core／前置入口真实联机，本地允许两个 modid 后接受 `returns=empty`；客户端回调计数 before=160、frozen=180、after=180，客户端继续处理普通聊天，服务端 exit 0。客户端检查后由脚本终止，不将清理动作写成客户端正常退出。 |

基础同步协议升级为 `r3-client-5`，双方使用本轮同版本主包；共享模块的每个 modid 必须在客户端本地允许列表中。用法见主工程 README。

**保留的真实限制与失败：** 动态服务端仍列 7 个不可重变换隐藏类，客户端列 6 个；已登记事件／命令委托入口的停止效果已测，其他隐藏使用点不宣称全控。完整私有池／来源／资源／持久接续归 B，完整隐藏定义／Native／Host／网络与视觉广度归 C，原第三方样本本轮未跑。Native 源码未改，复用已有 DLL，Java 载荷重新构建。

`boot-a-complete` 与 `boot-a-final` 两次在原 15 秒命令等待处失败，命令随后到达且服务端 exit 0；二者仍记 FAIL。为超时现场加入线程采集后，`boot-a-thread` 在原时限内通过，没有触发采集；前两次慢点仍缺现场，不写成根因已修。首次工厂复查还因受限环境拒绝访问隔离游戏目录失败；获准本机运行后的新场景通过，失败日志保留。

本轮集中构建、消费者及实机结果见 [A 收口报告](../../validation/a-final-20260928/REPORT.md)，摘要在该报告旁 `evidence-20261001/`。旧候选和旧失败均保留。

## 以下为同日此前的换机接续六项修复与结果

接续包中的六项源码修复已集中构建、完成下述直接场景，并同步到主工程与本次新候选 JAR：只从 `[[mods]]` 解析所属 modid；清体前通过 `prepareModGroup` 封住事件／任务发布并拒绝目标实体准入；准确登记方块实体 Map 槽位；补齐正式 SRG 字段名和 Unsafe 映射；Agent 按 ProMod 绑定的真实类、Module、ClassLoader 识别控制调用；同步清单包含最新 FieldWriteBoundary。

本机复现进一步发现：夹具的 `Field.setBoolean` 攻击被生产守卫拒绝后抛出 `RONOVA_PROTECTED_FIELD_WRITE_REFUSED`，夹具却将此预期拒绝当成错误，导致服务端崩溃。现已只接纳该明确拒绝，并直接读取原始字段确认未写入；其他反射错误继续失败。同一现有场景补查 remove／level／worldPosition 与 ChunkAccess.blockEntities 的 Unsafe 写入，未增加生产探针或许可层。

| 最新候选场景 | 实际结果 |
| --- | --- |
| `mod-group-protectmap-field-fix` | Map.remove、混合 clear、Entry.setValue 被拒，clear 仍删除无关条目；普通移除、反射和 Unsafe 未改变准确 remove／level／pos／容器；当前／后续生命与非生命实体抵御 /kill，原始 removed=null，准确 UUID 索引和未来 callback 成立；方块实体保留。世界命令响应，服务端 exit 0。 |
| `mod-group-dynamicmap-field-fix` | missing_mod 单组报错后 pro_fixture 继续；两个实体和一个非 ticker 方块实体被清除，目标 tick／隐藏监听器／命令回调停止。世界命令响应，服务端 exit 0。仍报告 7 个不可重变换的隐藏类，保留部分覆盖。 |
| `mod-group-bootmap-field-fix` | stop=pro_fixture 在游戏启动前接入；世界完成启动，目标方法和隐藏监听器未执行，Forge/Minecraft 依赖正常，世界命令响应；该夹具状态 UNRESOLVED=0。服务端 exit 0。 |

原 `protect10` 未到 40 tick 的当时根因仍缺少现场线程栈；本次两次运行均在原 30 秒等待内越过 40 tick，首次在后续字段攻击处复现上述夹具崩溃，修正后整个防护场景通过。不能把这项新证据写成已证明旧早期卡顿根因。

新机运行入口已使用 JAVA_HOME 和当前隔离 Forge libraries，源码同步脚本按自身目录定位工程并生成新候选的哈希；旧日志、旧候选和原 ZIP 保留。此次 Native 源码未变，复用原候选内已有控制 DLL，重新构建 Java 载荷。

当前候选交付位置：`distribution/candidate-modwide-a-20261001/`。本轮未重跑客户端和原第三方样本；完整隐藏定义、Native／Host、来源／资源／持久链仍按 B/C 接续。报告及原始证据见 [A 收口报告](../../validation/a-final-20260928/REPORT.md)。

## 以下为 2026-09-28 交付及历史范围


日期：2026-09-28。主工程：D:\桌面\ronova_GPT。当前 A 候选为 ABI 25，持久格式未变。

**V1.5 A 的已声明通用生产路径已写齐，并在同一交付 Core 的隔离场景中完成服务端与基础客户端效果验收。** 新候选覆盖启动前、运行中清除、非 ticker 方块实体、已注册事件／命令回调、当前／后续对象防护及本地授权后的客户端整组停用。结论限于这些准确入口，不等于任意目标 Mod 的全部隐藏方法都已失效。完整外部来源持久链归 B，完整动态隐藏定义、网络／视觉及 Native／JVMTI／Host 归 C。

## 整 Mod A 当前代码

当前隔离候选新增 `ronova-pro-prelaunch.jar`：它在创建游戏 JVM 前读取实例根目录 `ronova-prelaunch.properties` 的 `stop`／`protect` 多 modid 规则、提取主包内 Agent，并自动交接 premain。启动器必须实际调用该入口；单独 `mods` JAR 仍是晚附加，不冒充启动前控制。

`/ronova_pro mod stop modid1,modid2` 已接收逗号批量输入，按真实模块分组，单组解析失败后继续其他组；先清准确实体和所有已加载区块中的目标方块实体，再短路已加载与后续普通方法、构造函数后缀和静态初始化。继承的基础实体 tick／AI／伤害入口按目标接收者阻断。任务入口／发布、Forge 事件监听器及已注册 Brigadier 命令回调按实际 owner 阻断。`/ronova_pro mod protect modid` 覆盖当前及后续生命、非生命实体和方块实体的实际移除／替换入口；`mod status [ids]` 可查选定模块。多人客户端通过本地 `allow_remote_stop` 预选并安装了对应 Mod，才接受当前连接的整组停用请求；集成服可直接同步。

已运行的直接效果：动态停用夹具后，目标 tick 与委托给 Java 标准库的隐藏监听器均停止；同一组合中停用前生物和 Mod 自定义掉落物都被选择器找到，命令报 `BODIES_CLEARED=2`，停用后两者都查不到，世界命令继续响应且服务端 exit 0。启动前 `stop=pro_fixture` 的隔离服务端无目标 tick、无目标隐藏监听器回调，世界命令继续响应、服务端 exit 0，前置 Agent 不再因 Forge 扫描器/事件变换器降为部分安装。整组防护对当前及稍后加入世界的生命与非生命实体均挡住服务端 `/kill`：最终状态均为 `ACTIVE`、原始移除标记为空、UUID 索引准确、真实实体管理器 callback 存在，服务端 exit 0。

**A 的效果范围与剩余边界：** 启动前构造和静态入口在隔离夹具生效，动态停用的普通方法、已登记事件与命令回调、任务入口和基础客户端同步均已接入。运行中重变换仍报告不可修改的隐藏 lambda；事件、命令和任务以实际调用点控制，其他使用点不能宣称全控。任意构造前缀、已有私有池／持久来源、完整动态定义／网络／视觉及 Native／Host 广度不纳入这次 A 的通过项，分别按 B/C 继续。随机 UUID／专用返回策略仍属未实现设计。目标 Mod 有这些路径时，单组结果继续报告部分覆盖，不能冒充整 Mod 全停用。

本轮新候选的直接结果：`mod-group-boot6` 启动前停用后游戏完成启动，目标方法和监听器没有执行；`mod-group-dynamic9` 当前生物、掉落物和非 ticker 方块实体被清除，目标 tick／隐藏事件回调停止；`mod-group-dynamic11` 无效 Mod 单组报错后有效组仍清除对象，已注册命令回调也停止；最终方块快照实现的 `mod-group-protect9` 中，当前／后续实体和方块实体抵御 `/kill`、`setblock air`。这些场景服务端 exit 0。最初新增静态初始化控制后，构造函数未同步截断导致 `boot5` Forge 加载失败；修正后 `boot6` 通过。逗号命令最初被 `word` 参数拒绝，改为完整参数后 `dynamic11` 通过。最终 Core 的动态停用定点复查记录在报告中。

`dynamic12` 再次证明清除、事件与命令回调效果，隔离脚本在停服保存超过 30 秒时终止进程，因此退出结果不记为通过。随后新增的客户端本地 `allow_remote_stop` 和 Mod 安装筛选已编译并完成下述联机场景；交付候选哈希与边界见报告。
`mod-group-client-1` 使用交付 Core 和前置入口，客户端按本地 `allow_remote_stop=pro_fixture` 入服后接受当前服务器的整组停用，客户端仍保持运行，服务器退出码 0。客户端 Agent 同样报告不可重变换隐藏类，已保留为部分覆盖，不写成“整个 Mod 所有方法都停了”。

## 旧 build25 原 A 的直接证据

| 作用点 | 结果 |
| --- | --- |
| 源码与普通安装 | build25 Core／Agent／Bootstrap 共用 ABI 25；当时主包 818,727 字节；普通 mods 安装免填 JVM Agent 参数 |
| 策略修改入口 | 公开方法直接调用和伪造 Brigadier 命令被拒；伪造控制台排队输入不能解除保护；真实控制台输入能启用保护 |
| 防护与邻居 | build24 的 GUARD_PROBE_PASS，服务端 exit 0；准确字段、索引、撤销及无关实体同时成立 |
| 任务 A 路径 | build24 的 TASK_A_PATH_PASS，服务端 exit 0；完整 B 来源链仍明确待处理 |
| 最终双端 | build25 的 AUDIT_FINISHED，客户端／服务端 exit 0；玩家换身、身体清除、已知链稳定、客户端撤销、邻居不连带 |
| 构建 | build25 的 jar 与 fixtureJar 构建成功；源码交付清单为 12 个文件 |

新的对象门曾在 build23 因 ConcurrentHashMap 递归更新导致任务场景崩溃；build24 改成弱引用桶后任务场景通过，但双端高频临时对象使线性查找拖慢服务端。build25 改为按对象身份哈希定位并清理失效弱引用，同一双端场景正常完成。失败日志和线程栈保留，不用最终通过覆盖它们。

## 保留的边界和历史未决

- 旧 R8 的 INLINE_RETIREMENT_EXIT_OR_CAPTURE_PENDING 仍属任务资源结算未决；build24 任务结果也明确写出 FULL_RECOVERY_CHAIN_PENDING_NOT_CERTIFIED。它们不因 A 的任务最短路径通过而结清。
- 更早一次停服超过 45 秒且当时没有线程栈，具体旧原因尚不能证明；build25 正常退出不抹去该历史失败。
- 晚附加前活动仍报告 AGENT_LATE_ATTACH_COVERAGE_GAP。未知来源、OPAQUE 效果和不支持的持久提交不宣称完成。
- 未运行原第三方样本，当前 A PASS 不是对每个原样本的逐一实战结论。

[旧 A 验收报告](../../validation/a-final-20260928/REPORT.md) 保存 build25 的构建、运行结果和产物哈希；它不覆盖上文的新整 Mod 候选。[V1.5 设计](../../docs/design/Ronova-Pro-设计方案-V1.5.md) 保留后续 B/C/D 目标；此前状态在 history 目录。

## 2026-10-11：fast-exec R2 源码接续（未提升已验收基线）

- 查询只投影 ACK 状态，不再修改工作义务或 coverageGap。保留旧 Status.receiptDurable 接口，但新工作不生成成功回执，因而返回 false；现有 WorldFixture／ChainFixture 不再等待该已撤销的回执。WorldFixture 删除强制等待 20 tick 的成功认证断言，仍检查真实清除、客户端确认、邻居和再入场阻断。
- 实体／方块 Core 强引用释放删除 ROOT_RESOURCE INTENT 及其前置 ACK 等待。真实最终绑定／剩余记录条件满足后释放，后续 tick 追加 ROOT_RESOURCE RELEASED；缺失释放事实的 ACK 仍保持待结清。
- 已完成 Core 释放的任务不再重新比对全局 boundary epoch。实际任务动作仍使用当前所需边界；迟到活动继续成为新工作。
- 补回 fast-exec 删改后的现场条件：结算和最终释放必须确认 active invocation 与 helper 引用均为零；Bootstrap 所有权／丢事件查询当轮不可用时不执行相关结算或资源动作，重试而不制造新永久 gap。
- 存储仍执行现有独立文件身份／字节读回。相同 expected image 的解压与语义哈希改为每项工作复用，首次读回及进程重启首次查询仍重新解码。没有拉长周期或用历史 READBACK 代替当前文件读取。

当前为修订源码，未生成对应 JAR，未完整 Forge 编译，未取得 Windows Native／Minecraft 进入世界或来源、任务、保存重启组合通过。旧版 TaskStateCheck 依赖已删除的稳定窗口字段，仅属历史检查，不能用于认证 fast-exec。本次只核对实际修改的代码与既有夹具接口，不运行旧全量矩阵。

本轮实际检查：Java 17 对 3 个修改后的生产文件及 2 个既有夹具文件进行了纯语法解析，5 个文件、0 个语法错误。没有进行依赖解析、类型检查、完整 Forge 构建或实机运行，不能视为编译／攻防通过。GitHub 未同步。

远端接续限制：已读取 Scorn522/Ronova_pro 的 main；ProRuntime.java、RecoveryTasks.java、RecoveryStorage.java 均不同于本轮上传 fast-exec 输入。此修订只基于上传包，未覆盖不同的远端主线，也未把候选源码提升为远端当前基线。远端同步须先对当前主线合流。
# 2026-10-20：PCL 扫描与来源锁回归修复

本轮证据：用户的 `错误报告-2026-10-20_12.21.03.zip`，以及 `.work/pcl-scan-20261020` 中保留的两份真实游戏线程采样和日志。游戏并非固定栈自旋：12:13 已进入 forgeclient，12:18 在 playerAnimator 的类文件解压触发 `EXTERNAL_NATIVE_CALL_SOURCE_UNOBSERVED:...stage=declaring_owner_busy`。已结束失败进程，没有另行启动游戏。

- 生产修复：堆 Unsafe 写入不再采集未消费的 Native 来源数组；未命中受控范围的对象继续实时权限检查，但跳过锁／回执；包含继承字段来源的检查，真实 Unsafe 声明类只解析一次。已有原生模块记录按不可变链原子发布，查询已有 Owner 不需争抢发布写锁。
- 最终构建：`jar compileStateChecksJava` 成功，41 秒；原有 `host_completion_port.c` ULONG 比较警告仍在，未扩展修改范围。
- 最终真实小检查：PCL Java 17、相同 DisableIntrinsic 参数、实际 premain／Native 与命名 Mod JAR。四线程运行真实原子操作、锁、Deflater／Inflater 和直接缓冲区写入；同时检查三个合法 JDK 缓存 CAS、重变换、缓存投毒拒绝、JNI 表视图拒绝、真实停组后缓冲区写入拒绝。`STARTUP_MEMORY_PASS`，操作段 341 ms，进程退出 0（含安装总计 18.465 秒）。
- 修复前对照：同样参数和检查，使用 PCL 原先部署的 Agent／Bootstrap／Native，真实 `Inflater.inflateBytesBytes` 准确复现 `declaring_owner_busy`，退出 1。旧包未完成操作段，因此不计算启动加速倍数。
- 中间检查记录保留：首次使用的旧辅助 DLL 缺少 JNI 检查符号，后改用已有正确版本；停组后的预期行为既可返回终结值，也可在实际写边界拒绝，断言修正为接受已观察到的 `RONOVA_TERMINAL_BUFFER_IO`，生产保护未因此放宽。

新双 JAR 替换原 PCL 候选，旧包备份并重建参数；没有运行 Forge／Minecraft、世界或保存重启检查。另记录超级史蒂夫 ItemStack 全字段访问变换与 Forge 私有字段重定向的冲突日志，未修改第三方包。原有世界／跨重启验收边界不因本次通过而提升。

