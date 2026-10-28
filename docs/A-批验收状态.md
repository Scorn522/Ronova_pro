# Ronova Pro 当前进度

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

## 2026-10-23：持续推进回调与 TLS 组合租约；两批代码已构建，整体目标未标完成

接续当前修改，不重做全仓库审计。HEAD 始终为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d。用户将目标明确为持续开发后，沿实际 callback → Native/TLS → LoadLibrary/worker 来源保留链继续生产修改；此前各批修改及未提交的 ClientBridge.java 全部保留。

第一批修复回调强来源租约接线。普通事件原有 sourceLeaseComplete，扩展事件未填入该字段，heap 回调则只填另一个 heapSourceLeaseComplete，而通用借用入口只读前者。现由 native_tooling_retain_sources 统一实际强引用和完整性检查，迁移普通/扩展/heap 准备方及 heap worker 消费方，删除重复 heap 完整性字段和准备循环。借用直接使用当前 NativeToolingInvocation 或真实 heapWorkerAnchor，删除历史槽位扫描，不再要求普通/扩展/ThreadEnd 调用具有仅部分受限事件才设置的环境标记。来源覆盖、unknown、停止、admitted/active 和环境退场检查保留；现有 reaper 在借用结束后归还引用，heap 同步调用结束时不会释放仍被借用的锚点。

同批将 Host 加载选择和无 JNI 的 tooling 来源采集迁移到既有纯 C 活动来源合并，完整遍历 NativeRegistration.previous 链，删除只取顶层注册的重复实现；没有输出 owners 的查询仍逐次传播未知标记。host_module_capture 的 NULL 物理地址不再拿锁遍历映像目录，实际 C/Java 来源仍由调用方原路径采集。该批 gradlew.bat jar 在上次中断前已成功（20 秒），日志 .work/pcl-world-20261021/build-local-callback-leases.log，本次仅确认该完成记录，未重跑。

第二批接齐 TLS 组合租约。host_tls_source_borrow 原先在 TLS 已保留 A、还缺 B 的情况下丢弃本次已选中加载关系，并只尝试线程上第一个非空加载候选。现将实际 selected/parentLoad/worker 起始地址查询选中记录传入，缺少的来源使用共用候选借用入口；每次仍重新核对真实生命周期和完整覆盖，已有 TLS 的 unknown/partial 不被清除。TLS、模块加载、受限退出和线程创建的全部声明/调用已迁移，失败和正常释放继续归还组合中的每份实际引用。

线程起始地址查询失败或返回 unknown 与已知 JNI-only、无 child receipt 的情况分开处理：前者保留 worker unknown/lifecycleGap，不再经新建全局引用或父/TLS 后备变成来源完整的 wrapped worker；后者继续合法后备。查询确实选中了记录但单份租约不足时，把该真实记录留给组合借用，并不预授予生命周期。另合并 host_native_only 判定供模块查询、父关系、来源保留和 worker 创建使用，补入 toolingRestricted 及真实 loader 窗口，避免在这些窗口通过 GetEnv 后新建 Java 来源租约；仍只借用已保留的原生来源。

第二批完整代码写齐后统一 gradlew.bat jar，BUILD SUCCESSFUL（32 秒）。两批均实际重编译 Control Native；全部 Java 编译和 Storage Native 为 UP-TO-DATE，Core/Bootstrap 重新打包及 reobfJar 成功，Agent/Prelaunch 复用。host_completion_port.c:355 既有 ULONG 比较警告保留。最新日志 .work/pcl-world-20261021/build-local-composite-leases.log。Java/JNI 对外接口未变，没有新建检查脚本、夹具或证明体系；未 commit/push、部署或分发复制。

静态上移除了回调历史搜索、NULL 物理地址映像扫描及重复采集逻辑；来源完整性修复不作为提速证据。实机、世界、旧夹具和性能测试均未执行。整体持续目标未标完成：旧 VirtualFree BUSY 文本仍不能区分原始系统调用失败、控制范围未知或最终门锁竞争，本批也没有新实机结果确认 source_lease/selected_unknown、出生区 0% 和正常保存退出是否改变。下一最高价值工作仍是对应实际 Native 失败分支和来源锚点的定位；不能凭旧错误码扩大放行或在暂停期间自行启动获取现场。

## 2026-10-22：补齐卸载/退出记录的租约退役同步；本批生产修复收口

接续上一批十五个未提交文件，HEAD 仍为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d。完成性核对发现上一条记录尚未覆盖 HostModuleRelease 内嵌租约的清理：TLS/嵌套加载通过 lifetime 门增加借用，而退出记录 reaper 只在 host_imports 下读取借用计数并开始释放来源，两个入口没有共同排斥。受限退出保留还使用另一份候选选择和直接计数增减。

现将卸载/线程退出保留迁移到共用候选借用入口，新增内部 tryOnly 模式；受限退出对映像门和租约门均只尝试获取，失败正确释放已取得的门并继续原后备，不改普通加载/TLS 原有等锁语义。reaper 在同一 lifetime 独占门内重新核对 calling/borrowers/retired/releasing 和 cleanupGap，认领成功后才释放来源；计数归还改用共用 drop。所有失败回退及未解决退出状态的退役标记同步使用该门。释放来源前撤销 leaseComplete，避免关闭见证句柄失败、重新开放清理重试后，已经释放来源的记录仍可被当作完整租约借用。JNI 引用释放、后备锚点释放和真实句柄关闭仍在 lifetime 门外，未知线程退出状态继续保留 GAP。

本轮源码仅继续修改 host_module_sources.c、host_tls.c 并补齐 host_module_admission.c，Java/JNI 对外签名不变；普通加载、TLS、受限退出的内部调用方和清理方已接齐。此前控制区重复扫描、共享目录读锁、AVL 释放索引、Module[] 转换及 parentLoad 保留修改全部保留。删除退出路径的重复候选选择与绕过共用门的父借用计数操作，没有新建测试、验证脚本或证据体系。

生产批次写齐后统一 gradlew.bat jar，BUILD SUCCESSFUL（24 秒）。Control Native 本轮实际重新编译；主 Java、Bootstrap、Agent、Prelaunch 及 Storage Native 均 UP-TO-DATE。Core/Bootstrap 重新打包并完成 reobfJar，Agent/Prelaunch 复用；既有 host_completion_port.c:355 比较范围警告保留。日志为 .work/pcl-world-20261021/build-local-release-lifetime.log。起止 HEAD 不变，结束工作区共十六个修改文件；未 commit/push、部署或更新分发目录。

本轮新增同步位于租约入场及退出清理，目的是防止释放与借用并发失效，不宣称提速。当前目标所要求的一批完整生产修复、必要重构、调用/退出接线、旧路径处理和统一构建已完成；这不代表 VirtualFree BUSY、受限 TLS 来源缺口、出生区 0% 或正常保存退出已通过实机验收。客户端、专服、世界、夹具与性能测试均未执行，实机暂停继续有效。

## 2026-10-22：持续推进 Native 内存与租约主线；四项关联修改统一构建

从上一轮工作区继续，起止 HEAD 均为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d。起始已有 control_ranges.c、host_memory.c、ClientBridge.java 和两份报告修改，全部保留；没有重新全仓库审计。沿 memoryWriteBegin、Host 内存读写/释放、输出发布和实际来源保留链连续完成以下生产修改后，只统一构建一次。

1. 私有目录的查询与普通内存效果统一使用 native_private_read_lock 共享锁。Win32/NT 复制、页保护/释放、JNI/JVMTI 输出、事务 I/O 状态和完成端口输出同步迁移；共享持有仍覆盖最终控制区复查及实际效果，分配、登记、搬迁、注销继续独占。受限回调仍用 Try 锁，不等待目录写者。删除独立 tooling 输出锁包装和 NT 复制相邻的重复范围查询；真实进程句柄固定前后的必要检查、最后锁内复查、停止来源、部分复制和原始错误语义保留。仍承担 LastNtStatus 不可用时兼容职责的 Win32 回退未删除。

2. Ronova 自身 VirtualFree 成功后的登记注销改用既有 AVL 索引，按实际基址、分配类型和 span 节点选择真实分配，与 CRT 头查询共用实现。删除 NativePrivateRange 的旧双向总链及维护逻辑；失败释放和成功 decommit 继续保留原登记，成功 MEM_RELEASE 才注销。不移出分配/释放临界区，也不把普通内存纳入控制目录。

3. 内存、进程/网络来源及进程暴露的四份 Module[] 转换合并为 native_owner_modules。每次重新读取实际模块身份；已发布的永久 Owner 记录直接查询，仅缺少身份时使用原写锁登记。本次局部来源链的分配不再置于整个模块数组的目录写锁内；未知标记、异常失败和消费者的实时停止检查保留，无权限缓存。

4. host_module_borrow_load 原先忽略已选中的 load->parentLoad，且第一个非空候选保留失败后不再尝试其他真实加载候选。现与 TLS 共用 host_module_borrow_current，尝试本次选中加载、实际加载/释放作用域和真实 worker 关系，按身份去重；每个候选仍须通过完整来源覆盖、停止和存活检查才增加借用。加载租约入场/离场只改为共享读取门内的原子借用计数，退役和释放仍保留独占门。worker/tooling/TLS 后备锚点与失败释放路径保留，不把旧 unknown 记录洗成已知。

完成后执行 gradlew.bat jar（已有 JDK 17/Zig 0.13.0、离线依赖），BUILD SUCCESSFUL（42 秒）。Control Native 本轮重新编译；全部 Java 编译及 Storage Native 为 UP-TO-DATE。Core/Bootstrap 重新打包并完成 reobfJar，Agent/Prelaunch 复用。host_completion_port.c:355 既有 ULONG 范围比较警告保留。日志：.work/pcl-world-20261021/build-local-memory-lifetime.log。未提交、推送、分发复制或部署；实机、世界、夹具及性能测试均未执行。

静态上减少读者之间的独占冲突、一次虚拟释放的总链扫描、相邻重复控制区查询和已知模块的目录写锁；未测量真实提速或并发效果。父租约修复仅确认源码漏接已补齐，不等同于旧 JNA/TLS 拒绝已实机消失。当前证据边界：attempt-40 的 VirtualFree 日志只有 BUSY，缺少原始调用/控制查询/最终门锁的具体失败分支；该轮另有 selected=0、unknown=0、lease_complete=0 的受限 TLS 拒绝，以及 selected_unknown=1 的记录。不能从这些旧结果选择新的安全放行条件。沿已确认调用链的本批可实施修复已完成；后续定位仍需对应 Native 失败现场，实机暂停期间不自行启动获取。出生区 0%、最终世界与正常保存退出继续未验收。

## 2026-10-22：Native 控制区查询复用统一区间索引；仅本地修改和构建

本轮起始 HEAD 为 eb26c77174280bf29a1c7e5c6f7f1a2ecfabd82d，接续并保留 ClientBridge.java、当前状态页和 B 批报告的既有未提交修改。attempt-40 已保存的 threads-world.txt 显示渲染线程经 DirectByteBuffer.get、ResourceBridge.bufferRead、ControlBridge.readReceipt 进入 NativeControl.memoryWriteBegin0；源码确认其控制区查询在统一私有区间索引之外，又拿四把目录锁扫描同一批已登记对象。此为本轮消除的重复开销，不将其判定为出生区 0% 的唯一根因。

native/control_ranges.c 删除 Owner/Binding/OwnerLink、JVMTI 私有记录/版本/扩展/入口及 heap walking 的重复区间扫描；native/host_memory.c 删除 Host 映像、导入、加载/释放历史、来源链及 IAT 的重复扫描。上述私有堆与生成入口在分配返回前已入统一索引；heap 栈范围在发布 walking 前登记，IAT 在发布前登记并沿原映像退场流程注销/恢复。实际控制范围继续逐次查询同一索引，最终实际写入的锁内复查保留，没有新增许可缓存或放行名单。

移除本查询对 native_records、native_tooling_records、native_tooling_heap_records、host_imports 的四次目录读锁；JVMTI 环境改为 acquire 读取已有 release 发布且永久保留的记录链，仅对重叠的真实 VM 环境继续原 mutation、closing/disposed/shutdown 检查。JNI 当前表、平台结构实时读取、地址溢出、整页保护、来源/停止与实际生命周期逻辑保留；Java/JNI 接口未变。

完整生产代码写齐后执行 gradlew.bat jar，使用已有 JDK 17、Zig 0.13.0 和离线依赖。首次调用在 Gradle 缓存锁访问被沙箱拒绝，尚未进入编译；提升执行权限后原构建参数通过，BUILD SUCCESSFUL（27 秒）。Control Native 本轮重新编译，保留 host_completion_port.c:355 既有类型范围比较警告；主 Java、Bootstrap、Agent、Prelaunch 与 Storage Native 均 UP-TO-DATE。Core/Bootstrap JAR 在 build/libs 更新，Agent/Prelaunch 复用。没有提交、推送、分发复制、PCL 部署或游戏启动；构建日志为 .work/pcl-world-20261021/build-local-control-range.log。

静态影响是移除上述线性历史扫描及四把目录读锁，保留统一索引和必要的实时 VM 查询；实际速度和锁等待改善未测量。客户端、专服、世界、夹具及性能测试未执行。VirtualFree BUSY、Native 来源/租约缺口和最终世界/保存退出验收继续未解决；下一优先结合已有 Native 拒绝现场定位 VirtualFree BUSY 的实际失败分支，不以本轮构建推定运行故障已消失。

## 2026-10-22：顶点快照与清除读回批量读取；仅本地修改和构建

本轮从 eb26c77、干净工作区接续，不提交、不推送、不部署或启动游戏。依据 attempt-40 的真实栈，GpuImages.complete 逐字节 DirectByteBuffer.get 重复进入 ResourceBridge.bufferRead、ControlBridge.readReceipt 和 NativeControl.memoryWriteBegin；同一实现的保留快照、CPU 比较、GPU 读回比较和 CPU 清除后检查也在逐字节读取。

ClientBridge.GpuImages 统一通过 ByteBuffer 的绝对位置批量 get 读取实际范围。顶点增量直接复制到原受保护 payload；保留快照和读回比较使用受已有弱控制目录保护的字节副本，比较在堆内完成。删除上述逐字节缓冲读取循环，保留缓冲 position/limit/mark、实际读取的来源/区间/复制保护，以及原线程、批次、存储身份、停止来源、目标顶点和未选区域读回条件。没有添加读取豁免、授权缓存或新的 JNI 接口。

完整生产修改后执行 gradlew.bat jar，BUILD SUCCESSFUL（23 秒）。compileBootstrapJava 本轮重新编译；主 Java、Agent、Prelaunch 及 Control/Storage Native 均 UP-TO-DATE，复用既有产物。Core 与 Bootstrap JAR 已在 build/libs 更新，Prelaunch JAR 复用；分发目录和 PCL 安装未更新。未运行客户端、服务器、世界、夹具或性能测试，不沿用旧候选的检查结论。

静态上，每段快照不再为每个字节调用缓冲读取入口；部分比较增加临时堆副本及其弱控制登记，实际速度和内存影响未测量。attempt-40 的出生区 0% 仍是最后实机结果；VirtualFree BUSY、Native 来源/租约缺口及最终世界验收仍未解决。下一优先工作包为结合已保存现场收敛 Native 内存操作的控制区查询与锁竞争，不能把本轮批量读取当作整体进世界问题已修复。

## 2026-10-22：按用户要求暂停；40 未进世界，41 仅构建

attempt-40 使用原 PCL 实例、原完整 Mod 集合和真实启动参数。实际达到 premain INSTALLED_FULL、Native FULL、客户端资源加载、IntegratedServer 启动和主世界出生区准备，08:44:11 最后记录出生区 0%；尚无玩家入场、ProRuntime 稳定世界 Tick 或正常保存退出通过。Bootstrap 待安装边界及 Native 来源/租约缺口仍保留，stderr 仍有 VirtualFree BUSY，不能标为已修复。

真实线程栈中，早期绘制已不再等待旧 ControlRegistry 读取监视器，但任务创建仍有来源登记开销，客户端缓冲读取仍频繁进入内存控制和调用者查询。按用户“这轮进不去就停一下”，保留最后线程栈、日志和独立测试存档；窗口激活超时后，仅结束路径及原始 UTC 创建时间匹配的本轮 JVM，08:45:26 监控确认退出 -1。这是人为结束未完成的测试，不是游戏自行崩溃或干净保存退出通过。没有启动下一轮。

候选 41 已完成同一热点工作包：普通数组写先查询原回执相关性，实际元素写守卫和公开回执入口的调用者权限仍保留；任务来源捕获仅在本次完整物理栈已遍历且为空时省去重复查询，部分/作用域入口继续原回退；Native 内存入口对实际最近调用类为 ResourceBridge/CodeSourceBridge 的情况使用短栈查询，未命中继续原完整 Java 选择器，保留真实来源、控制区、在途作用域和退场校验。没有来源/许可缓存或 Mod 白名单。

最终双 JAR 构建成功（1 分钟），既有 AuditFixCheck 的实际数组写断言已补齐并编译，但候选 41 没有运行小检查、起服或客户端，不能沿用候选 40 的检查结果。Git 分发目录更新为候选 41；PCL 安装仍是候选 40，未部署 41，也未为 41 重建启动参数。当前执行暂停，世界验收仍未完成。

## 2026-10-22：恢复实机，候选 40 修复控制目录读取的实际锁竞争

按用户“继续测试”恢复原 PCL/完整原 Mod 集合及真实 82 项参数，独立世界先备份。候选 39 实际 premain INSTALLED_FULL、Native FULL；Bootstrap 仍有 ForkJoinTask$Aux/FutureTask$WaitNode 待安装边界，未混记全平台 FULL。真实 Java 栈确认早期绘制 SimpleBufferBuilder 字段写在等待 ControlRegistry，同一时刻类变换线程持有该目录监视器，经 ExternalCodeImages.snapshot 批量登记控制身份并逐项通知 Native。匹配符号的原生读取另确认 Owner 仅 11 项、控制区 AVL 高度 18，没有把普通递归帧误记为退化树。

现保留控制目录写者串行与每项 Native 策略撤销通知，仅将普通身份读取改为读取 volatile 发布的目录和链接。扩容复制弱节点后一次发布新根，旧链不改写；GC 只移除死亡身份并保留读者后继，旧目录排队节点按实际目录世代跳过。正在登记的批次通过临时精确身份索引提前受保护，输入/索引数组也保留弱控制保护直到读者不再持有。特殊执行角色仍在原监视器下读写，不缓存停止策略、来源或许可，没有按类名或第三方包放行。

候选 40 完整构建通过（32 秒；首次 ForgeGradle 证书检查失败后原配置重试，没有关闭校验）。复用 live-control-writer 与 system-arraycopy 实际 Native/Unsafe/反射/MethodHandle 写入检查，均退出 0：外来控制写及私有原生读取仍被拒绝，实际控制数组保持不变，普通复制、重叠、原异常和兼容前缀语义保留；未运行 metadata seam。

39 已完成全 Mod 客户端资源、配方/进度加载并启动 IntegratedServer，尚无玩家入场/稳定 Tick。窗口收到关闭操作后未及时退出，结束本轮 JVM（-1）并备份测试世界；首次备份遇 session.lock 尚未释放，监控确认退出后补齐。该轮记录 6 条 TLS 拒绝、未见 VirtualFree 错误，但不据此宣称旧 Native 缺口已修复或干净退出通过。候选 40 双 JAR 已备份更新原 PCL，重新准备参数，attempt-40 正在同一世界继续真实测试；最终世界和正常保存退出验收仍未通过。

## 2026-10-22：候选 39，收敛 Native 重复维护与历史扫描；实机继续暂停

依据 6bc6f22/候选 38 审查同包完成四项生产修改。普通 API 入场只将 image->calls 的入场读锁改为共享锁，活动计数仍用原子操作，发布/解绑/退役的独占锁及最终活动检查保留。关联查询以本次栈上 HostLoadSelection 带回 queried/status/load，在同一查询内不重复选择加载关系，0 不覆盖有效 JNI-only 关联；受限回调仍另核对实际调用者。

TLS/FLS 在实际上下文中只合并尚未保留的 value 来源修订，成功读取并保留后才标记集合成员关系。实际 API、返回值、私有索引和当前停止状态仍逐次检查，写入、异常、返回不一致和真实上下文清理使标记失效。观察历史改为单调合入，不用旧快照覆盖嵌套回调的新来源；失败读取可能保守保留已采集事实，不记为成功。原实现若在读取中重入并改写 value，返回前再次合入当前来源；该真实合并失败时拒绝返回指针。

历史身份与当前线程/纤程清理队列分离，完成项退队；值清理也从工作队列移除已完成项。活跃 TLS/FLS 索引和实际未关闭监视句柄另有当前链，常用查找不再经过已退场历史。租约实际引用数归零即进入一次性释放队列，出队重新核对计数，不再每释放一项都从总链头找。清理每轮分段处理，未完成轮次继续推进；未知纤程、未结束回调和其他真实生命周期缺口仍保留。线程句柄查询/关闭继续受同一保护门覆盖，JNI/DLL/父租约释放在目录锁外执行。

首次统一编译指出受限回调遗漏新增参数，补齐该调用点后完整构建通过（20 秒）。候选 39 双 JAR 已备份更新原 PCL，prepare-pcl.ps1 已重新生成参数，没有启动客户端、服务器、metadata seam 或助手桩。未提供本候选实际性能或世界 PASS；最后实机仍为 37 的出生区块 5%/强制结束。既有 JNA source_lease、显卡加载来源缺口、VirtualFree BUSY、稳定世界 Tick 和干净保存退出继续未完成。

## 2026-10-21：候选 38 六项来源/租约收口，已部署并按用户要求暂停

按 dd64ce3/5a54d99 合并审查同批完成：实际调用者和入口各自在依赖 JNI 索引后备时保留该映像的 relationsGap，标志使用原子发布/读取，精确活动来源不被无关索引缺口污染。普通来源查询把已采集 owners 传给 scoped 选择，保留 JNI-only 返回 0 的兼容性，失败清空 selectedLoad。普通、脱离 JNI 的线程和受限回调共用纯 C 活动作用域合并，完整采集 Binding.hostUnknown 及整条 NativeRegistration；受限路径不调用 VM 查询。

共享租约尾链快路径保留；后备合并使用一次临时真实租约身份集合与暂存节点，成功后才发布租约链，失败释放暂存引用并返回失败，避免两槽历史未变化重读的嵌套扫描。普通 TLS 成功写入且并集已保留时释放预分配备用增量；并集失败才留下该备用，FLS 的回调记录规则保留。拒绝快照区分入口查询、实际回调查询和最终有效来源，原采集循环顺手填入可选诊断字段，不为成功调用再扫注册/绑定/tooling，不为日志重复进 VM；回调来源查询失败保证拒绝而非误用零错误码继续。

完整双 JAR 构建通过（45 秒）；首次构建停在 ForgeGradle 证书校验，原配置重试通过，没有关闭校验。构建完成时尚未部署到当时仍运行的 attempt-37，也没有运行 metadata seam/独立消费者/助手桩复现。37 已实际完成全 Mod 客户端资源加载、自动打开独立常规地形世界并启动 IntegratedServer，继续生成出生区块；未确认玩家入场、稳定 Tick 和正常保存退出。

37 的短命线程先发生 JNA 已知来源 source_lease，后续选中两条显卡加载记录 unknown=1/leaseComplete=0，已用匹配 PDB 对真实记录只读核实。tls=24 是伴随的读取历史，不能直接当作调用 unknown 根。仍有 VirtualFree BUSY；这些没有据构建标为修复，也没有按 DLL 名称放行。

用户要求本轮收尾后暂停。attempt-37 最后记录出生区块 5%，没有玩家入场、稳定世界 Tick 或正常保存退出通过。游戏窗口两次激活超时后，仅结束身份已核对的本轮 JVM（退出 -1），保留独立测试世界及日志。候选 38 双 JAR 已备份更新到原 PCL，prepare-pcl.ps1 已重新生成参数；没有再启动候选 38。当前源码修复与候选构建已完成，最终世界验收及上述真实 Native 拒绝仍未完成，等待用户恢复后继续。

## 2026-10-21：候选 37 补齐 JNI-only 与拒绝快照失败路径

继续同一工作包：没有当前 LoadLibrary 关系时保留已经建立的有效 JNI 关联，不把选择器 0 当作调用失败；JNI 后备读取直接使用映像已发布关系索引。TLS 来源根在本次来源查询完成后立即保存，后续失败只补阶段、索引世代和租约完成状态，不重新查询来源；Java 来源缺口使用实际 code/process 查询的位标志，受限回调仍不进 VM。普通 TLS 也保留预先分配的本次增量租约节点，以便系统写入成功但槽位并集分配失败时仍能保留完整历史；只给 FLS 保存回调匹配所需的原始值，不复制整份旧历史。

候选 35 已实际记录 premain INSTALLED_FULL、Native FULL 并继续类变换，尚未开世界；中间 36 仅构建，没有实机结果。候选 37 完整构建通过（36 秒），双 JAR 备份更新原 PCL、参数重新准备，继续复用同一独立测试世界和原 Mod 集合，世界与干净保存退出仍不记通过。

## 2026-10-21：候选 35 收口 TLS 来源、借用及增量历史

按针对 9bc36b9/db704cc 的两份审查意见同批修改生产路径：TLS 状态查询在锁外把 Module 解析为保留到进程结束的 Owner，锁内只比较原生身份；真实选中的 HostLibraryLoad 从普通/受限来源查询传到租约准备，借用与退役使用记录短锁，并与映像 pin 退场同步。无关 host_imports TryLock 不再遮住完整父租约；仍检查实际映像、当前生命周期、停止来源及全部来源覆盖。

映像的 JNI 注册在真实加载观察处建立关系索引；无精确活动来源的载体选择同时考虑实际加载与这些当前注册。同源、完整的重复运行时加载保持来源已知，但多条记录不伪造唯一租约；额外/不完整注册保留来源或歧义。来源合并失败在创建 trampoline 前清理退出，失败不发布 selected 或入口。

TLS 来源使用引用计数的不可变共享租约尾链，值首次建立即保留 allocation；每次写入只新增本次来源/真实租约。槽位维护完整并集，普通 TLS 不再保存仅供 FLS 回调匹配的值贡献链；FLS 的实际值和在途写入记录保留。已知身份但生命周期未保留使用独立 partial，并继续拒绝借用，不靠改写 unknown 表示。拒绝诊断在清理临时状态前捕获，按阶段/API/索引世代/实际调用地址/来源根去重，单条日志自带关联信息；受限回调不进入 VM。

attempt-33 已完成客户端资源和菜单，实际启动新建测试世界 db704cc 的 IntegratedServer，出生区块到 3%。旧测试副本因其历史存档依赖当前未启用的维度包而不能打开，未更改任何第三方 Mod。真实世界生成栈另确认 SourceMap 查询反复 ThreadLocal.set/remove 和全局目录锁；现合并 holder/carrier 实时查询，使用已发布的弱身份链只读遍历，只删除已死亡身份且保留读者后继，不缓存授权结果。旧候选正常关闭请求超时后结束，测试世界另行备份，不记世界 Tick 或干净退出通过。

完整包已构建：首次补齐新增参数的 completion-port 调用点后通过，最终候选 35 构建通过（41 秒）。双 JAR 已备份更新原 PCL，prepare-pcl.ps1 重新生成参数；attempt-35 使用原实例完整 Mod 集合及真实参数自动打开该独立新世界。没有新增 seam/隔离夹具或合作式 SDK；不同来源组合、失败分配及实际退场未据构建冒充检查通过。此前 VirtualFree BUSY、受限回调 source_lease/unknown_source 及干净保存退出仍等待本次实际结果。

## 2026-10-21：候选 33 修正唯一完整载体的假 unknown

对 attempt-32 的真实 Host 链表及匹配 PDB 布局只读检查确认：jemalloc 实际只有一条存活加载关系，unknown=0、leaseComplete=1、来源是非 Mod 运行时模块；相关 API 导入项同样 unknown=0。但 host_module_select_locked 的公共载体分支仅因已知来源不是 Mod 而无条件输出 unknown=1，早期实际 TLS 槽位的 allocation_unknown 因此也为 1。这是代码与实际状态交叉定位，未喂入任何伪造来源记录。

现在仅在没有更具体的活动绑定/回调来源、且恰好一条当前完整已知非 Mod 加载关系时，直接复用这条实际关系。不会清除传入的已有 unknown；真实未知、停止来源、第二条活动加载导致的共享歧义、私有索引及 Native/JVM 控制区判断均保留，不按 DLL 名称放行。统一构建通过（32 秒）。attempt-32 于 Datafixer 之后、测试世界未打开时结束；旧 Native 符号/现场已保存。候选 33 双 JAR 及参数备份更新到原 PCL，继续全部原 Mod 的真实客户端循环。实际早期同一 TLS 索引 95 已显示 allocation_unknown=0、aggregate_unknown=0、uncertain=0、gap=0，对应加载/API 条目仍完整已知，确认本次生产调用不再制造 unknown；这不替代后续渲染或世界结果。世界和干净保存退出尚未通过。

## 2026-10-21：attempt-31 原生终点与候选 32（仍未进世界）

attempt-31 实际完成 Forge、渲染初始化、声音引擎和多个贴图图集创建，但于运行约 33 分钟后原生退出（0xc0000409 / FAST_FAIL_FATAL_APP_EXIT）。主渲染线程最后的 kind=37 是 TlsSetValue，索引 95，因 unknown_source 被拒绝，随后 jemalloc 报 Error setting TSD 并中止；此前还出现 VirtualFree 数据无效。没有 IntegratedServer/世界 Tick 或保存退出 PASS。首次短命线程的 source_lease 写入调用来自实际 JNA DLL，后续有 jemalloc/显卡/系统组件；不能把整个序列都归为显卡调用，更不能按 DLL 名称放行。

匹配 PDB 的真实崩溃转储确认中止栈，但小转储未包含 Host 来源记录所需的堆页。候选 32 将 TLS 来源借用中的纯叶目录 TryLock 改为同目录已有的实际等待，保留原完整租约/已停用来源检查；不会因无关竞争伪造没有租约。另仅在实际拒绝时，各记录一次受限/普通调用的 entry/load、TLS、Native tooling 未知根，普通安全上下文再读原有 Java 来源诊断；受限回调不进入 Java/VM。未知与停止来源的拒绝、私有 TLS 索引和控制区规则均未放宽。这还不是上述 unknown_source 致命问题已经修复的结论。

统一构建通过（32 秒），双 JAR 已备份更新原 PCL 实例、参数重新生成，attempt-32 按全部原有 Mod 和真实启动参数继续测试世界副本。没有新增隔离夹具或合作式 SDK；世界与干净退出验收仍未完成。

## 2026-10-21：候选 31 减少实际标量回执的重复控制登记

attempt-30 的真实 35 秒 JFR 及 Java/Native 栈确认：采样中已无 StackFrameBuffer.resize，但同一早期绘制标量仍在 Buffer/Handle/Native Unsafe 边界创建 RawMemoryMutation、Scalar，并在弱控制目录登记时争用全局锁。现把这两种精确 Bootstrap 类型直接判为永久控制状态，免去每个实例的重复目录发布；不是按类名放行，也不缓存调用者或许可。现有各层 Native 作用域、来源、控制区与合法区间检查均保留。

候选 31 统一构建通过（34 秒）。原 live-control-writer 增加对这两种真实控制对象的外部 Unsafe/Field.set 写入后，确认状态不变，并继续通过正常内存复制、外来调用者/私有原生读取拒绝及受保护数组检查，退出 0。首次检查已实际拒绝 Field.setBoolean，但检查未接受其 IllegalAccessException，修正该异常预期后完成复查；未放宽生产判断。

attempt-30 已到 Datafixer 初始化，测试世界仍未打开；保存现场后更新候选 31，双 JAR 与参数保留旧版备份，继续原完整 Mod 组合的真实客户端循环。TLS/FLS 拒绝、资源未完成时关闭的并发异常仍独立保留，世界与干净保存退出未通过。

## 2026-10-21：候选 30 修正实际 JDK 栈缓冲容量边界

已通过 PCL 实际 Microsoft JDK 17.0.15 的 javap 核对 StackStreamFactory：帧数组保留两个槽位，初始长度 32 在继续读取时仍会扩到 34。此前候选 28/29 的 32 缓冲不能消除此分支。现将 WALKER 和 RAW_MEMORY_WALKER 的初始预估改为 34；ORIGIN_WALKER 保持 64，遍历范围、隐藏帧选项、来源及授权规则不变。

候选 30 统一构建通过（34 秒），双 JAR 已备份更新到原 PCL 实例并重新生成参数。attempt-29 实际推进到 LWJGL、MinecraftForge 初始化和 Ronova 绘制隔离安装，未打开测试世界；关闭请求后未及时结束，更新前结束该世界尚未打开的进程，不记正常退出 PASS。attempt-30 已按原完整 Mod 组合及真实参数启动。TLS/FLS 拒绝与 TextureManager.close 并发修改异常仍分别保留；单人世界、稳定世界 Tick 和干净保存退出尚未通过。

## 2026-10-21：候选 29 实际客户端接续（未进世界）

attempt-27 已实际越过后台初始化，到达 Render thread、MinecraftForge Initialized、Ronova 客户端绘制隔离安装、贴图/字体资源加载与客户端循环。线程栈和两次实际类统计确认加载从 15584 类推进到 18649 类，并非仅依据同一加载画面判断。但直到结束前，测试存档 session.lock 仍是原副本时间，没有 IntegratedServer 或世界 Tick 证据。

本轮还记录到同一已退出线程上的 TLS/FLS unknown_source/source_lease 拒绝；已把可读调用地址对应到实际 NVIDIA 图形组件、drvstore.dll、CRYPT32.dll。未据此放宽未知来源或私有槽位限制，也未宣称这些调用已修复。因实际资源栈仍反复通过 StackFrameBuffer.resize → System.arraycopy → 控制对象登记锁，常用当前栈读取器改为初始 32 帧、保留隐藏帧的来源读取器初始 64 帧；仍遍历原全部帧，不缓存权限或来源。候选 29 统一构建通过（37 秒）。

attempt-27 于资源尚未完成时接收正常关闭请求，启动监控记录退出 0，但日志有 TextureManager.close 的 ConcurrentModificationException，故不算干净退出，更不算世界保存验收。现场与错误均保留。候选 29 已备份更新双 JAR、重新生成参数，attempt-29 正在同一原完整 Mod 组合下实际加载，已记录 AGENT_PREMAIN_READY:INSTALLED_FULL 及 NATIVE=FULL；单人世界、稳定 Tick 和无关闭异常的最终保存退出仍未通过。

## 2026-10-21：回调读侧与 TLS 句柄同步修复（候选 26）

后续实际终点：attempt-26 保存线程栈后于进入世界前主动结束；没有打开测试世界，没有取得世界 Tick/保存 PASS。JFR 与线程栈进一步定位到普通内存操作的重复 Java 来源捕获。candidate-27 将已经在本次控制器边界捕获的 Java 来源直接传入，Native 独有的当前 C/TLS/绑定/库来源仍逐次合并，停止状态和控制区检查不变；缺少该 Java 捕获时仍走完整原路径，完成复制后的贡献更新也仍重新读取。构建首次因 ForgeGradle 远端证书检查失败，原配置重试后通过（54 秒）；对应真实 live-control-writer 再次退出 0。双 JAR 已备份更新、参数重新准备，attempt-27 正在真实加载中，尚未打开测试世界。

当前候选 28 另将内存授权读取器的初始帧缓冲预估为 32，避免默认八帧扩容通过受控数组复制再进入 JVMTI 调用者查询。保留原选项、全部实际调用帧与选择规则，不设置遍历上限、不缓存调用者或许可。依据本轮真实栈及实际 JDK 17 源码确认该参数仅是初始缓冲提示；统一构建通过（38 秒）。发行候选更新为 28，PCL 仍运行 27；为保留当前实际加载进展，尚未为此缓冲调整中断该轮。世界验收仍待真实结果。

接续 9c24296。attempt-22 仍发生原生访问冲突；attempt-23 的真实失败日志将问题进一步定位到进入 API 处理前的全局目录 TryLock：正常 CRT/FLS/导出解析被无关写线程占用误报 ERROR_BUSY。现将已经完整发布且保留到进程结束的来源/映像记录用于只读回调查询，映像自己的短锁固定本次活动调用与退役关系；仍读取实际来源、停止状态和控制范围，不缓存放行结论。导出解析保持受控 API 封装。

attempt-24 越过前述大量入口误拒，继续到 Render thread，但仍记录三次 FLS_GET preparation BUSY。该轮中断后进程已不存在，没有取得正常退出记录，测试世界也未打开，不能算通过。剩余首次 TLS 线程上下文登记现使用专用句柄同步：分配/发布/释放与外部句柄检查及实际系统调用连续受保护，不再等待全局进程/JNI 记录锁。

真实栈另显示普通缓冲区读写反复通过 JVMTI 查询写入者。现直接使用原有 Java 调用者选择器读取当前调用栈，保持相同实际类身份和允许者规则，Native 来源、内存区间与生命周期校验继续执行。candidate-25 构建通过（34 秒）；现有 live-control-writer 在 PCL 实际 Java 与 Native/Unsafe 保护安装后退出 0，确认外来调用者/私有原生读取仍被拒、受保护数组不变、正常裸内存复制可用。只修正该既有检查对现行拒绝消息的过时预期，没有新增隔离夹具。

上述完整包 candidate-26 统一构建通过（20 秒），双 JAR 已备份更新到原 PCL 实例并重新准备参数；attempt-26 已使用完整原启动参数启动。单人世界、IntegratedServer/ProRuntime 稳定 Tick 和正常保存退出仍待本轮实际结果，不以构建或小检查替代。

## 2026-10-21：真实 PCL 启动修复与直接清理收口（阶段性）

继续在 D:\桌面\ronova_GPT 开发。实机使用 PCL 原实例 1.20.1-Forge_47.4.23 的真实 Java 17、完整启动参数和原有全部第三方 Mod，自动进入目标为独立存档副本。原存档和第三方 JAR 没有改写。用户已授权持续真实启动，并于本轮改为几个关联修复完成后阶段性同步 GitHub。

启动链已补齐实际观察到的问题：JNI 表每次仍完整核对，正常读检查使用共享锁；Native 私有区间按实际范围建立索引；Forge 早期绘制只复用真实来源读取，保留原有内存、生命周期和逃逸检查；ASM 私有序列化临时字节数组不再逐字节创建业务回执；空任务来源与“正在查询来源”不再伪装为未知来源。保留 premain、Bootstrap、JVMTI/Native 和现有控制区保护。

Mixin Args 动态类由其原生成器生成到现有 GAME 包，解决生产 ModuleClassLoader 无法路由未知 synthetic 包的问题；保持原参数描述符、计数器、生成与调用语义。仅更换类名前缀时保留原栈图，修正中途触发可选 ASM 校验类缺失的失败。

TLS 的共享槽位不再把不同线程的独立值误作冲突；Free、关闭中及实际回调限制继续保留。退役改为按实际线程/FLS 上下文索引清理，已完成项退出重复扫描；纯读取不再反复唤醒没有新释放工作的清理线程。已知无 Mod 来源的平台回调不再因为没有私有 Mod 租约而被伪造为 OS 写入失败，部分保留状态仍如实报告，实际 Mod、未知来源、控制索引及停止来源判断继续生效。

针对 41e7887 审查清单，已补入：自身精确 FutureTask 取消通知合并、相关任务/整组范围的待处理门控、移除无条件整窗 20 Tick 退避、CF 结果 CAS 与 BLOCK_CLEAR 脱离刷盘前置、自有引用与 observer 在实际退出后释放、退役装备声明返回真实未安装状态及自定义语义 GAP、来源发布按准确 holder/incoming 归组、同一字段动作减少重复停止来源查询、SingleStep 缺映射改为等待实际 FramePop、当前 Native gap 替换旧值、历史全局 FramePop 失败不再封锁无关组，以及 watching 的同步读取。Native 退役推进和状态读取共用一次真实线程扫描；同轮 Mod 状态和世界 UUID 扫描复用读取结果。已完成恢复链仍保留定期真实读回，事件唤醒优化尚未完成。

实际证据保存在本地 .work/pcl-world-20261021。前序候选已通过真实活动 JNI 表保护/修复小检查；真实客户端已观察到 AGENT_PREMAIN_READY:INSTALLED_FULL，到达渲染初始化、原 Mod 构造、字体资源加载及客户端 Tick。attempt-14 的 Args 失败、attempt-15 的可选 ASM 类失败、attempt-16 的 jemalloc TSD 原生退出均保留；attempt-17 未在同一位置退出，但出现 CLIENT_SCREEN_LAYOUT_CHANGED。已查明超级史蒂夫的 public-f Screen *，按原字段/描述符/类身份接入其实际布局，继续核对三个列表的真实身份。

当前不是进世界验收通过：尚无 IntegratedServer、ProRuntime 稳定世界 Tick 或正常保存退出的本轮完成结论。候选构建、阶段性同步与后续实机结果分别记录到既有实际报告。

后续真实现场：attempt-19 在 OS 线程清理中发生 lwjgl_opengl.dll 访问冲突，转储确认回调参数为 -1 哨兵值。修复公共载体在回调/加载器阶段使用普通 TLS 的误拒；未知来源仍不获得私有租约，含实际 Mod 来源的槽位保留完整来源门控，停止来源和私有控制索引继续拒绝。TLS 纯记录锁不再把竞争伪造为 OS 操作失败，FLS 哨兵结束也不再等同仍在执行的线程结束。candidate-20 构建通过（45 秒）。

attempt-20 的真实 JFR 诊断触发 ControlImages 全局变换锁与 ThrowableTracer 类初始化等待环；同版 Microsoft JDK 的 SA 栈已定位，普通 jcmd 未能返回。修复为锁外检查/登记保护，锁内仅发布已经保护的不可变镜像快照；无关类不再争用镜像发布锁。candidate-21 构建通过（30 秒），已更新 PCL 双 JAR 并准备原启动参数继续实跑。上述失败不计世界验收通过。

attempt-21 的同一路径真实 JFR 在约 6 秒内成功启动，取得实际采样并进入 Forge 早期绘制，未再出现上述等待环；随后以 0xc0000374 原生退出，仍未开世界。真实转储进一步确认 CRT 的 FLS API 缓存不一致：FlsAlloc/FlsSetValue 已解析，FlsGetValue 却被缓存为缺失，导致 FLS 索引用于普通 TLS 查询并在退出时误释放其他库的数据。修复 host_native_resolve 在 heap/toolingRestricted 范围中无条件伪造 ERROR_BUSY/NULL；继续走现有纯 C 回调来源捕获、真实映像关系及停止来源检查，返回的控制 API 仍生成原有受控入口，不返回裸露的替代入口。

## 2026-10-20：超级史蒂夫 ItemStack 访问变换兼容

接续 `022e716`。12:21 与 12:57 报告中的 `Field f_41589_ is not private and an instance field` 已查清：实际 AccessTransformerService 在 ModLauncher BEFORE 阶段应用超级史蒂夫的 `public-f net.minecraft.world.item.ItemStack *`，随后 Forge `field_to_method.js` 调用 ASMAPI，仍按 private 实例字段校验，因此同一合法 getter 重定向被拒绝。

新增 Agent 兼容变换，仅对 ItemStack／Item 类型的 public 实例字段及准确 SRG／开发映射字段、getter 名称放宽可见性前提；继续运行 Forge 原本的静态字段、重复字段与 getter 存在／唯一性检查及实际 GETFIELD → getter 重定向。不临时修改 AST 访问标志，不关闭重定向，不改第三方 JAR。该 API 继续经过原有整组来源、写入与发布控制，没有增加可借用的 Bootstrap 放行入口。

最终构建通过（33 秒）。完整 premain／Bootstrap／Native 及原写入拦截参数下，真实超级史蒂夫 AT、Forge 5.2.4 API、生产 SRG 与开发映射 ItemStack 字节码检查通过：4 处应改写读取完成重定向，public 权限保持，原 private 路径正常，8 类无效输入仍拒绝，退出 0。相同检查换回旧候选，准确复现报告原异常并退出 1。检查进程使用简单日志后端，游戏配置未更改。

双 JAR 已更新到原 PCL 实例并重新准备参数，旧包已备份。替换前保存日志并结束旧加载进程；没有启动新游戏或 Forge，完整进入主菜单仍待用户实际重试。

## 2026-10-20：PCL 扫描卡顿与 Native 来源读锁误判

接续 `dbed510`。12:21:03 报告与现场线程栈一致：早期扫描仍在推进，但普通 Unsafe 原子操作也创建堆写入回执、采集随后被丢弃的 Native 来源数组，原生写入结束还反复解析 Unsafe 声明类。随后 playerAnimator 的正常类文件解压因 `declaring_owner_busy` 被拒绝，Mixin 初始化失败；已保存现场并结束该失败进程。

普通堆写入现在继续执行实时权限判断，只有实际受控／保护／停组目标、相关来源发布等情况才持有写入回执；未知 Unsafe 偏移额外检查父类的模块与来源字段，裸地址和涉及裸地址的复制仍保留来源采集及控制区检查。两种真实 Unsafe Class 身份按加载器解析后复用。Native 已发布的模块 Owner 使用不可变链原子发布，读取已有记录不再抢写锁，不等待被暂停的写线程，不把未知来源改成放行。

最终构建通过（41 秒）。使用 PCL 相同 Java 17 与全部原写入拦截 JVM 参数，实际命名 Mod JAR 的四线程原子／锁／原生 ZIP／直接缓冲区操作通过；JDK 三条缓存 CAS、重变换后缓存重建、外部缓存投毒拒绝、JNI 控制表保护及停组后写入拒绝通过。换回旧候选运行完全相同检查，准确复现 `Inflater.inflateBytesBytes → declaring_owner_busy`，退出 1；新候选退出 0。此检查不代表实际游戏启动速度或进入世界验收。

候选双 JAR 更新到原 PCL 实例，并仅重新准备启动参数。没有重开游戏／Forge。报告中另有超级史蒂夫 `public-f net.minecraft.world.item.ItemStack *` 与 Forge 私有字段重定向要求冲突的日志，未修改第三方 Mod；完整组合进入主菜单仍待实际重试。

## 2026-10-20：LWJGL JNI 线程环境初始化接线

接续 `dcc98db`。11:38:50 报告为 `lwjgl.dll+0x20524` 写空地址 `0x1c`，上层为 `WinBase.nGetModuleHandle → WindowsLibrary.<clinit>`；对应原生代码在 JNI 取得线程数据后未检查空返回。`ThreadLocalUtil` 此前进入通用来源织入，同时其 JNI 元数据访问与控制区读保护存在冲突。旧候选的独立对照先在该类织入时失败，未将该结果冒充同一原生 PC 的复现。

现在把实际 `ThreadLocalUtil` 的初始化、能力切换及缺失函数表操作接到明确的平台入口，校验原声明、方法描述符、实际加载器／模块与调用帧。Native 只处理当前 JNIEnv 指针的读取和当前／VM JNI 表的 reserved3 数据字；其他地址回原有受保护 Unsafe 路径，函数槽与 JNIEnv 槽位写保护不开放。接口不要求 Mod 主动提供 SDK 元数据。

最终生产构建通过（18 秒）。实际 Java 17／LWJGL 的普通类路径及 `org.lwjgl` 命名模块检查通过；最终检查保留报告顺序，由 WinBase 原生调用首次初始化 ThreadLocalUtil，完成 Windows 模块查询、GLFW 库加载、能力表建立／释放，并拒绝外部 JNI 读取、伪造 Java／Native 桥调用和控制表视图，退出 0。未调用 glfwInit、未创建窗口、未启动 Forge／游戏。

候选双 JAR 已替换到原 PCL 实例，旧包备份，参数准备成功；完整进入游戏仍待实际重试。失败过程与检查范围见既有实际报告。

## 2026-10-20：LWJGL 布局探测与 JVM 退出互锁修复

接续 `4d96d68`。用户最新 PCL 日志及 11:17:40 错误报告显示，上一处 Log4j 重复加载已越过，本次失败在 LWJGL 3.3.1 的 `MemoryUtil.getPositionOffset`：其真实代码用 `NewDirectByteBuffer(-1, 219540062)` 创建布局探测视图，Ronova 将不解引用的视图创建按实际读写处理，错误返回 `NATIVE_CONTROL_RANGE_UNAVAILABLE:ADDRESS_OVERFLOW`。

Native 缓冲区视图查询现在分段检查地址回绕的两侧；命中控制区仍拒绝，真实读写／页面修改继续沿原溢出拒绝路径。实测普通缓冲区读写正常，直接包裹活动 JNI 表及回绕后覆盖该表的视图均被拒绝。

对应小检查还发现 JVM 销毁时，原生清理线程返回会触发 LWJGL 的线程卸载回调，在加载器锁内再次 Detach，导致退出互锁。现在只在 VM teardown 后让这个进程生命周期工作线程保持静止，与既有固定驻留的调度入口一同等待进程结束；正常运行时的清理流程不变。原生栈、无 Ronova 对照及修复前超时记录均保留。

最终统一构建通过（38 秒），使用 PCL 实际 Java 17 和本地 LWJGL 的初始化／读写／保护检查通过，进程正常退出（0）。候选双 JAR 已更新到原 PCL 实例，参数准备成功，旧包已备份。没有启动游戏或 Forge，完整进游戏结果仍待实际重试。

## 2026-10-20：PCL 启动报告中的 Class 缓存递归加载修复

接续 `a14799f`。用户提供的 10:47:53 错误报告显示启动交接已完成，Agent 为 `INSTALLED_FULL`；随后 JDK 发布注解缓存时，Ronova 先调用 `mirrorStaticRange` 枚举被代表类的字段，解析字段类型重入 Forge `ModuleClassLoader`，导致 Log4j `MessagePatternConverter$RenderingPatternConverter` 重复定义。

缓存写入现在先查 `Class.class` 自身实例布局，再验证三个既有精确 JDK CAS 调用链；合法缓存维护直接完成，不进入业务静态字段解析。外部 Class 实例字段写入按真实重叠区间拒绝，静态字段与其他写边界继续执行原策略，没有关闭控制链或恢复广谱容器 Hook。

统一构建通过（33 秒）。已有实际 Agent/Native 检查新增未加载字段类型断言：首次方法反射、注解缓存建立、实际重变换后缓存重建及外部缓存投毒拒绝均通过；同一检查换回旧 Bootstrap 后准确失败于字段类型被提前加载。两次结果均保留在 `.work/pcl-class-cache-20261020`。

候选双 JAR 已更新到 `distribution/v1.5-whole-mod` 和现有 PCL `1.20.1-Forge_47.4.23` 实例，旧包已备份，启动参数准备成功。没有启动游戏或 Forge；这次确认的是报告根因及对应 JVM 回归修复，游戏启动结果仍待实际重试。

## 2026-10-20：PCL2 启动前交接配置

接续 `8170395`。Prelaunch 新增 `--prepare-args`，复用现有载荷提取、Mod 文件扫描、保存选集与 Native/JVM 参数准备，原子生成 Java 参数文件后直接返回。PCL2 同步执行准备脚本后，仍自行启动原 Forge 主类；账号、游戏、内存及其他 JVM 参数保留。生成文件不保存登录参数，准备失败会移除旧文件。

本地 `D:\MC\.minecraft\versions\1.20.1-Forge_47.4.23` 已配置，保留其指定的 Java 17，更新 Core 并将 Prelaunch 移出 mods；原配置与旧 JAR 留在实例内备份目录。正式配置前正常关闭了 PCL2。最终打包通过（21 秒），实际准备脚本成功生成 11 项 JVM 参数，原主类／游戏参数／JVM 参数读回一致。只运行参数准备，没有启动游戏、Forge 或 Native 攻防检查。

## 2026-10-20：注解缓存 CAS 回归与 JNI 非受控槽退场修复

接续 `88986aa`。`Class.reflectionData`、`annotationData`、`annotationType` 的合法 CAS 分别按真实实例字段类型、Bootstrap 声明类、方法描述符和相邻调用帧放行；AnnotationType 路径包含实际 JDK 17 `System$2` 转交帧。受控／已停类和在途已停调用中的 JDK 缓存维护也能完成，避免 CAS 永假导致注解查询自旋。外部 Class 非静态字段的 Unsafe、反射与句柄写继续拒绝；AnnotationData、AnnotationType 内部字段纳入平台结构保护，setter 字段定位改用原始 VM 声明。

JNI 增加当前批准表：合法 SetJNIFunctionTable 实际成功后才提交，发布过程中不持目录锁跨 VM safepoint。非受控槽遭裸写或来源停用时恢复 original，并更新批准目标、同步 VM 表；实际 VM 表自身也进入区间保护。真实来源刷新放在退场／必要同步边界，不让每次普通 JNI 调用重新捕获来源。

生产一次统一构建成功（59 秒）。已安装 Native/Unsafe 下，首次注解查询、实际重变换后的缓存重建、Class／注解缓存的 Unsafe、Field.set、MethodHandle 投毒拒绝均通过。JNI 检查通过未批准 GetVersion 裸改恢复、合法活跃 hook 保留，以及真实目标模块加载 DLL、合法发布 hook、现有整组停止入口执行后恢复原始槽位。

候选双 JAR 已更新，Core 3,296,245 字节，prelaunch 内容不变；具体结果及两处检查入口修正记录见既有实际报告。未起服、未启动 Minecraft／Forge，不把独立 JVM 回归等同于游戏内整组验收。

## 2026-10-20：Native 启动重变换的区间查询误拒修复

接续 `9d84e02`。根因是 `RtlIsThreadWithinLoaderCallout` 的查询入口此前只在较晚的 `host_windows_targets` 初始化；第一轮平台类重变换已经启用 Unsafe 原生内存入口，空查询入口把普通线程误归为受限回调。JNI 表登记锁发生正常竞争时，受限路径的 TryAcquire 返回未知，再抛出 `NATIVE_CONTROL_RANGE_UNAVAILABLE`，最终导致重变换 ClassFormatError。

现在在 `native_initialize` 发布 JavaVM/JNI 控制入口前初始化该查询及页面信息。真实加载回调／VM 受限上下文、平台查询不可用和控制区间写保护继续保留。区间查询失败增加具体目录／读取失败原因及 restricted、loaderQuery 状态，未把未知当成放行。

生产统一构建通过。真实 JNI 并发对照中，仅恢复旧的晚初始化顺序即复现 `JNI_TABLES_BUSY:restricted=1:loaderQuery=uninitialized`；修复候选同一负载完成 21,248 次 GetEnv、1,024 次普通 Unsafe 原生读写，Agent 达到 INSTALLED_FULL。候选双 JAR 从中文工作／临时目录经正式 Prelaunch 交接，活动 JNI 表及尾部写保护、改槽自愈、页面权限恢复与合法非受控槽保留检查通过。

双 JAR 位于 `distribution/v1.5-whole-mod`，Core 更新为 3,293,733 字节，prelaunch 内容不变。未起服、未启动 Minecraft／Forge；未运行 metadata seam 或全量矩阵。旧失败日志保留，具体证据与候选摘要见既有实际报告。

## 2026-10-20：Windows 中文路径 Bootstrap 装载修复

接续 `c56fecc`，主工程继续位于 `D:\桌面\ronova_GPT`。`RecoveryAgent` 的 premain／agentmain 共用 `BootstrapSearch`：Windows 非 ASCII 绝对路径优先转换为指向同一文件的 ASCII 相对路径，并保留这个相对 `JarFile.getName()` 给 JPLIS。文件名本身包含中文或跨盘不能相对表达时，自动使用内容一致、按摘要区分的本地兼容缓存；本地路径不可用时才尝试 ASCII 临时缓存。成功的 JarFile 保持打开，失败尝试关闭；Native 接续使用实际追加的那份 payload。没有移动工程、改变游戏工作目录、修改系统代码页或放宽控制策略。

生产代码写齐后统一 `jar` / `reobfJar` / 检查入口编译成功（34 秒，退出 0）。候选 Core 更新为 3,292,478 字节；prelaunch 51,042 字节，内容未变；Java105 / Native99 / Storage3 / Journal7 不变。

必要小检查：直接从本工程中文绝对路径启动实际 Agent／Bootstrap，`RELATIVE` 装载路径通过；使用候选双 JAR 的正式 Prelaunch 入口，在中文工作目录、带空格的中文缓存目录中交接启动检查主类，`LOCAL_CACHE` 路径通过。两条最终检查均为 `BOOTSTRAP_SEARCH_PASS`、`INSTALLED_FULL`、退出 0，核对了 Bootstrap 类由 bootstrap loader 定义、实际持有的 payload 与原始文件一致、Native 可用。未启动 Minecraft／Forge，没有测试 SYS 或重跑上一轮攻防矩阵。

保留失败：检查初版误用通用资源接口读取动态追加的 Bootstrap 类资源，返回空；改为读取 Agent 实际持有的 JarFile 后完成核对。直接检查另有一次在 Bootstrap 成功后重变换 DatagramChannelImpl 时出现 `NATIVE_CONTROL_RANGE_UNAVAILABLE`／ClassFormatError；随后单独运行通过，但本轮没有修改 Native 源码消除该问题，仍保留为未解决的启动失败记录。日志与详情见既有实际报告。

## 2026-10-20：JNI 活动表、Class 镜像、总线与客户端收尾

接续 `b2bb9f9`，整包补齐六项生产改动：

- JNI：保护实际 `JNIEnv` 指针槽及当前 `*env` 的完整 `JNINativeInterface_`；在实际入口和退组时检查受控槽，原子恢复并同步 VM 函数表，保留合法的非受控槽。登记随真实线程 attach/detach/ThreadEnd 生命周期维护；恢复过程中处理实际页面权限。
- Class 镜像：Unsafe 单点／批量写只接受该 mirror 的真实静态字段范围，拒绝 VM 头部、隐藏字段、Class 自身实例字段及跨越空洞的范围。仅保留实际 `Class.newReflectionData` 的缓存 CAS 例外。
- Agent：初始化来源判断、Instrumentation transformer 管理器读取全部改经 Bootstrap `PlatformMetadata` 原始 VM 声明，不再消费可被投毒的字段反射缓存。
- Forge 总线：保护 `MinecraftForge.EVENT_BUS` 静态根；实际 EventBus.post 在根漂移时恢复原总线并使用其监听器链完成当前分派。既有退组登记继续按每个真实 bus/owner 卸载，原总线与漂移总线的登记不合并成虚假身份。
- 容器：增加 PriorityQueue、Vector/Stack、EnumMap、Regular/JumboEnumSet、ConcurrentLinkedQueue/Deque、LinkedBlockingQueue 的直接清理。使用真实锁、节点 CAS、序号槽和位图；并发新入的组外条目保留。共享 PriorityQueue 非尾叶删除若需要比较器，保持 `SOURCE_PRIORITY_QUEUE_REHEAPIFY_CALLBACK_REQUIRED`，不调用目标代码或破坏堆序。
- 客户端：失败对象保留到下一 Tick，捕获失败重扫；声音/UI 跨线程交接按原对象与实际资源状态接续，已完成释放不重复执行。结果绑定当前连接、session、generation、sequence，完成后重新通知服务端；网络协议版本同步更新为 `v1.5-whole-mod-retry`。

本次用户已明确允许构建后的小检查：只运行直接针对这些路径的检查，不起服、不启动 Minecraft、不运行 metadata seam 自证。原未知私有语义与未观察入口仍如实报告 GAP/PARTIAL，SYS、自身外观和武器注册继续暂缓。

生产 `jar` / `reobfJar` 统一编译完成，Core、Agent、Bootstrap 与 Native 均通过。随后同次命令中的旧检查入口因缺少 ASM 导出参数编译失败；补齐该编译配置后，检查入口编译成功，未重新编译生产源码。当前 Core 为 3,287,932 字节，prelaunch 51,042 字节；Java105 / Native99 / Storage3 / Journal7 不变。

小检查已完成：实际 JDK 容器直接清理 PASS；Class 镜像真实静态存储／VM 头部准入及被投毒 ReflectionData 的原始读取 PASS；安装真实 premain 后的 Unsafe Class 实例／缓存／ReflectionData 写入拒绝、正常静态写和 JDK 缓存 CAS PASS；活动 JNI 表与尾部 VirtualProtect／WriteProcessMemory 拒绝、实际改槽后的恢复、只读页同步与非受控 GetVersion 保留 PASS。JNI 检查没有伪造 Owner 或后端 ready 状态。

首次直接使用中文构建路径启动独立检查 JVM，在 JDK appendToBootstrapClassLoaderSearch 处退出，尚未进入检查；改用同一产物的纯英文临时路径后，实际 premain 安装为 INSTALLED_FULL 并通过检查。未据此宣称正式启动入口的中文路径问题已修复。Java 准入检查的初次模块开放不足和未先初始化临时测试类也已修正，原日志保留。

未启动 Minecraft／Forge，事件总线与客户端的游戏内效果仍未做本候选实机检查。客户端与服务端需一起更新到本候选协议。详细结果见既有实际报告，日志保留在 `.work/v1.5-hardening-20261020`。

## 2026-10-20：三个 P0 与反射／JVMTI 控制链修复

接续 `1542cfc`，按整包生产代码方式实现，继续沿用 premain + Bootstrap + JVMTI/Native。没有恢复 JDK 容器广谱 Hook，没有合作式 SDK、SYS 扩展、自身外观或武器注册。

- SourceMaps：在既有精确后端上补 TreeMap、IdentityHashMap、TreeSet（实际 TreeMap backing）、LinkedList、ArrayDeque 的直接遍历与整组清理。使用真实节点、槽位和对象身份，不调用目标自定义 comparator/hashCode/equals。清空已归属的私有容器前保留子图继续处理；共享载体保留组外条目，未知元素和并发结构变化继续报告缺口。
- EntityRecords：重新定位当前 manager/tick/lookup/sections、getter、回调、分区可见性和实体索引，修复后重新发布当前载体保护，仅退掉该实体的旧关联。Binding 固定原对象和原 Class；在实体访问前恢复被切换的 `_klass`。原类型位值来自实际 Class mirror 与 VM 导出的 narrowKlass 参数，恢复比较完整字段布局、大小和引用类型，并用已校验类型的位值执行 CAS，不采信竞争期间重新读到的任意对象头。恢复失败的状态查询仍按原对象报告 GAP。
- 在途调用：已接受的外部方法绑定实际 VM Method/ConstMethod/常量池版本，旧帧继续按原版本归源；独立线程及当前任务链的真实来源进入写入、提交、注册和 Native 效果边界。未知的已停组旧帧不借用当前新方法或历史来源并集。
- 反射缓存：阻止外部调用者修改 `Class$ReflectionData` 内部字段及 `InstrumentationImpl` 平台状态，包括尚未停用的 Mod。关键字段、方法及构造声明读取改用原始 VM 声明入口，不消费被投毒的 ReflectionData；Core 的入口绑定也不依赖该反射缓存。
- 原生控制链：从实际 InstrumentationImpl 的 `mNativeAgent` 定位 JPLISAgent，保护实际 normal/retransform JvmtiEnv、Ronova 自身环境及已观察环境的完整受支持结构区间，覆盖 `_next`，并接入后续 Instrumentation 构造交接。外部裸地址读、写、CAS、批量内存及现有 Host 内存边界共用该范围判定。

原生私有结构适用于已确认布局的 OpenJDK 17 Win64。未知 VM 布局、未捕获或布局不兼容的实体类型、未知容器语义及未观察 Native 入口继续报告 GAP/PARTIAL，不把候选编译当作实际攻防通过。

整包代码写齐后统一构建。首轮 Java 编译通过，Native 编译因 JVMTI 版本掩码常量名错误退出；按本机 JDK 17 头文件修正后，继续同一 `jar` / `reobfJar` 构建，19 秒成功、退出 0。没有执行起服、实机、测试或夹具。日志分别为 `.work/v1.5-p0-20261020/build.log`、`build-complete.log`；原有弃用、Mixin 映射及 Native 比较警告保留。

候选双 JAR 已更新到 `distribution/v1.5-whole-mod`：Core 3,253,883 字节，prelaunch 51,042 字节（启动器源码本轮未变，构建产物不变）。配套 Java105 / Native99 / Storage3 / Journal7 不变。此记录只确认源码和候选编译完成，没有新增游戏启动或攻防效果通过结论。

## 2026-10-18：启动扫描与嵌套作用组收尾

按用户新方向，SYS 保留源码和可选接线，暂缓部署与扩展。本轮接续 `e4928eb`，集中修复用户态启动与整组解析：

- Prelaunch 将成功扫描但没有 Mod 声明的 JAR 一并写入原文件索引；Agent 复用这类成功空结果，避免后续每个类定义都重新打开同一库 JAR。读取失败和早期 Module reader 未就绪仍可重试，不把它们缓存成永久非 Mod。
- 模块资源与实际外层文件的别名在同一个 metadata 锁域合并，避免并发发现把外层／内嵌别名覆盖掉。Jar URL 先保留 URI 转义，Forge union 路径只移除实际数字挂载后缀，避免 `+`、空格和文件名内 `#` 导致物理来源解析错误。
- Core 对新增别名继续查询实际 Module，直到同组别名／模块展开完成，再交给原停用、防护和存量清理流程；有实例时使用实例的真实 Module，构造被抑制时沿 Agent 的定义观察接续，不用外层 Module 替代所有内嵌执行模块。

生产源码收尾后，唯一一次统一 `jar` / `reobfJar` 构建成功（1 分 57 秒，退出 0，20 个任务执行），重新生成此前清理的构建缓存。日志为 `.work/v1.5-whole-mod-20261018/startup-groups-build.log`。配套候选已更新到 `distribution/v1.5-whole-mod`：Core 3,209,544 字节，prelaunch 51,042 字节，Java105 / Native99 / Storage3 / Journal7 不变。SYS 未构建或部署。

本轮没有运行起服、实机、夹具或检查任务；没有新的运行效果或启动耗时结论。原 Mixin 映射、弃用及 Native 编译警告保留，不因警告追加运行检查。

## 2026-10-18：V1.5 三项收尾，仅重建候选

按用户明确收尾范围，启动入口 `PrelaunchMain` 与 `ModGroupBoundary` 共用递归元数据读取：遍历顶层 JAR 的内嵌子 JAR 及更深层后代，将各自 `META-INF/mods.toml` 的 modId 合并到同一外层实际文件。没有外层 descriptor 的嵌套载体也会读取；原文件绑定、别名展开与整组策略继续复用。

删除四个 `ronova_object_state` / `ronova_allocator_state` 头文件和两个未编译的 `host_object_state.c` / `host_allocator_state.c` 实现；独立 `ronova_allocator_adapter.hpp` 已不存在。未恢复合作式 SDK 或私有语义猜测后端。

`objectModule` 在 producer 定义命中时不创建查询键，未命中时一次锁域读取全部真实来源表。普通未命中的 `beginFieldMutation` 复用一个身份键和首次来源解析，不再重复四次创建弱引用；策略调用后仍用同一键重读真实来源，必要时进入原 gate 并重新判断。没有跨调用缓存来源、负结果或许可。

源码收尾后统一执行 `jar` / `reobfJar`，49 秒成功、退出 0。首次沙箱内 Gradle 无法加载用户级 native-platform.dll，尚未进入编译；取得正常构建缓存访问后完成本次构建，没有源码修补或追加构建。日志为 `.work/v1.5-whole-mod-20261018/final-three-build.log`。候选双 JAR 已更新到 `distribution/v1.5-whole-mod`：Core 3,231,087 字节，prelaunch 51,052 字节，配套版本仍为 Java105 / Native99 / Storage3 / Journal7。

未知自定义语义、未观察 Native 入口、第三方不透明格式和未部署驱动按 V1.5 继续如实报告 GAP/PARTIAL；不据此扩写后端。本轮未运行起服、实机、夹具或检查任务。

## 2026-10-18：A/B/C/D 生产源码集中收尾，停止运行检查

按用户最新执行安排，旧 Forge 起服进程已终止；本轮后续不运行 `runServer`、起服轮询、实机、`validation/checks` 或隔离夹具。先把生产调用接齐，再进行一次统一候选构建。旧启动失败与局部通过各保留原范围，不升级为本候选通过。

- A/B：已声明 holder 及变更增量回到原整组游标，私有容器整份处置、混合载体保留组外份额；SavedData 真实首读字节/文件身份经实际解码、DataFixer、holder 与缓存绑定进入原 Snapshot，私有整图复用原存储投影。任务纳入已归属但尚未 submit 的 Future/CF 和迟到后继，私有调度器/线程/资源退役沿原整组意图，未知释放不另起调用重放。恢复链按依赖变化重开 20 tick 稳定窗口，采样间隔不超过 10 tick。
- C：实际外部方法贡献并入原 CodeImage，动态/隐藏类沿真实定义作用域归源；JVMTI 的效果检查包含环境、当前调用与真实来源。事件故障按本地明确选集触发整组隔离；客户端、网络登记、监听器、multipart 和导航索引沿真实所有权清理。世界原始时间/天气与实际数据链接补入原字段/offset/内存策略，共享 backing 的维度范围单独显示，区域实体范围不扩大。
- 热路径：`fieldMutationReceiptRequired` 去掉全 JVM 的 guards/非空选集判断，只有实际受保护、停用、受控目标、来源载体、关键资源/队列或活跃发布进入原锁与回执。原即时写入策略保留；`actualFieldOwner` 按真实 symbolic Class、字段名和描述符缓存结构解析，不缓存许可结论。
- D：升级/更换/卸载交接复用原世界 writer，新增 Journal7 的实际载体绑定和交接事实；旧 Journal1–6 原字节、operation/generation/key/attempt 与未知效果保留。实际载体变化需要明确 prelaunch handoff，缺席组仍能查询；不自动转换第三方不透明格式，不将卸载文件当成资源/存储已结清。发行仍为匹配 Core/prelaunch 双 JAR。

上述生产源码和调用接线已收齐。统一构建首轮发现 prelaunch 两处包名遮蔽，续编发现 Bootstrap 一处局部变量重名；仅修这三处编译错误后，最终 `jar` / `reobfJar` 构建成功（42 秒、退出 0）。Core、Agent、Bootstrap、prelaunch 和用户态 Native 载荷配套；Storage 源码未再变更，使用同一构建图中的已有产物。没有为构建补跑任何测试，构建后也未启动游戏。

当前双 JAR 已更新到 `distribution/v1.5-whole-mod`：Core 3,226,617 字节，prelaunch 48,190 字节；Java105 / Native99 / Storage3 / Journal7，客户端协议 `v1.5-whole-mod-clock`。SavedData 独立读回会准确重定位剩余列表路径并接回新代快照；旧单体存储并入整组时，只有原效果已闭合且同源、同真实文件的后继读回成立才沿原 key 接续，未知事务不被新组成功掩盖。

自身外观、武器注册、合作式 SDK、CRT/STL 细粒度账本及源码 ZIP 均不新增。未匹配旧活栈、未观察原生调用、任意非合作私有语义以及尚不可用的 Host 后端仍有实际边界，源码交付和效果验收分开记录；本轮没有世界加载、停组、防护或保存重启的运行通过结果。

## 2026-10-18：按 V1.5 收敛为整 Mod 控制

以下为停止运行检查前的历史过程，当前执行安排和候选结论以本页首节为准。

主工程仍为 `D:\桌面\ronova_GPT`，本轮接续 `e4ba753`，版本 `1.5.0-dev`，客户端协议 `v1.5-whole-mod`。以下历史记录保留各自候选与场景范围，其中旧 allocator/object SDK、CRT/STL 字段账本与隔离 seam 的结论不代表本版活动能力。

本轮整包生产实现包括：启动前自动参数交接和已保存多 Mod 选集；按实际文件与 Module 展开别名的 stop/protect/status；先发布策略再全方法入口返回；存量实体、BlockEntity、订阅、任务、恢复引用与客户端载体的共用限额收尾。共享加载器不作为归组依据，客户端范围与服务端结果独立报告。

Storage 已去除 IdentityLifetime、RootAllocation 与自发回执包装，使用直接 RAII、真实 NTFS 身份和 TxF。Native 活动构建去除合作式 SDK、CRT/STL/第三方分配器与静态 TLS 字段账本，保留整组控制、JNI/JVMTI、NativeMethodBind、实际线程/句柄和自身控制范围。旧 SDK 文件作为未编译历史源码保留，不能计入当前覆盖。

生产包完整编写后已统一构建 Core、Agent、Bootstrap、prelaunch、用户态 Native 和 Storage。真实 Forge 启动暴露的中文 bootstrap 载荷路径、selector 类循环、ASM 导出前校验、普通类全局引用图、数组复制导入表重扫及绑定准备顺序，均沿实际失败路径修正。另移除给已退役内存账本捕获来源的空工作；准确 JDK Class 反射缓存 CAS 不再被表示类的控制对象判断拒绝；普通 ASM 树只冻结已核输入、Module 与父子关系，在真实变换消费时才物化。原始失败不改写为通过。

用户曾明确授权在真实 Forge 1.20.1 / 47.4.23 服务端使用 `-Dronova.pro.validation=true -Dronova.pro.fixture=mod-group` 和已有 WorldFixture JAR。随后用户要求停止全部运行检查，该场景已终止；不能将 premain 完整或 ModLauncher 启动当成停组效果通过。没有运行 `validation/checks` 自证检查或独立 C++ 消费者夹具。

授权后实机继续定位普通 ASM 复制与 Mixin 元数据承接的强制序列化、无策略普通字段/数组的逐写锁，已收敛为冻结输入和实际父属的粗根来源，以及保留原写入策略的无回执通行；真实控制、来源载体、资源、队列、发布作用域及活策略仍走原门禁。首次通行暴露 PUTSTATIC 的空值拒绝约定，已正确适配。随后实机已过 177+5 个 Mixin 准备并进入 `Minecraft.server.Main`，暴露 JOML 46.0 类中新增 Class 常量不合法；共用写出器对旧类最低设为 49.0，保留原边界。修正续编成功，正在重开实际场景，尚无世界停组/保存重启通过结果；`ForkJoinTask$Aux` 的实际待验收状态保留。

随后 `f627327` 已继续通过游戏注册表初始化，但真实 EventBus 6.2.33 在完整性检查报 `EVENT_DISPATCH_IMAGE_INCOMPLETE` 后退出；原因是注入器已使用三参监听注册回调，校验器仍查旧双参。现共用正确描述符并核对实际参数与成功返回，保留分派保护。另修正 Brigadier 同名合并注册的节点身份，注销失败保留登记与 gap。该批修复统一编译成功（35 秒），已重开同一授权实机场景，结果待收尾。

本版不宣称任意非合作原生私有语义、未观察调用或外部旧活栈已完全控制；这些范围保持实际 gap/partial。自身外观与武器注册未加入本轮，完整 B/C 未统一标为完成。

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

接续 `f996c18`，Java ABI93 / Native ABI84 / SourceMapBridge2 / Storage3 / Journal6，协议 `r3-client-8`。本包先写齐所有源码再统一构建；继续保留上一包的停组策略顺序、原 Native 返回语义与准确丢失范围，没有恢复旧容器 Hook。

类加载先按实际 producer、受控 image、已注册合同和来源引用筛选。未命中只解码类头/常量池调用引用并保留原字节，不先进入 ExternalCodeRuntime/ClassReader 建立执行图；之后出现真实 producer/contributor 时，保留的调用者可按实际 loader/VM body 提升。跨接口/父类返回按方法合同保留候选，候选名称不授予来源权。删除全局 sourceObserved 单向开关，来源登记绑定实际 loader/module/name；producerModule 只缓存确认的 true，早期缺少元数据可重新发现。已编码 image 使用不可变目录，主要选择、回放及 Native/执行图路径不持全局 image 锁；原 tree 事务锁仍保留。

普通任务回调保存真实根、直接来源、准确 JDK/Inline 瞬时操作数及一层回调引用字段，不解析来源/包装图；图解析移到每次最多256条的 drain，policy 单独使用32项 tick 预算，关闭工作线程也显式推进这一步。浅层引用保住回调把来源移到局部变量并清空字段的情况，构造/发布前的实际来源判定消费同一快照，仍同步执行。未解析的本 runtime 图与跨 runtime 共享根继续阻止过早结清；已知失败保持相关范围，未知丢失仍全局拒绝。

定义刷新失败不再清空全部 continuation：保留未完成类及源 owner 的并集，无新事件也会以100毫秒至5秒间隔重试，真实新变化可唤醒。成功只移除对应完成项；单个 helper 提升/解析失败和整批异常均保留来源义务。重复失败日志按待处理类压缩，未把失败当完成。Native 自身未改写的 class 事件在 C 层直接返回，改写事件先做轻量候选判断再复制 byte[]，未知来源/缺名仍走完整路径。安装显式区分 FULL/DEGRADED/FAILED，只重试发布前 IO/表获取 OOM 或独立目录恢复，不重装已发布 Hook；Java 可用路径不因 Native 缺失而全局终止。普通 executionStack 不再留存完整帧列表，只保存当前/父帧与准确深度；物理遍历、BCI、递归及 retirement 核对仍保留。

统一构建 `build-c-java93-native84-root-lazy-20261016.log` **首轮 PASS，1分19秒**，21项任务中15项执行、6项保持最新，含 Native84 编译链接；检查器编译退出0。四项现有 AuditFixCheck 定向入口通过：`TASK_LAZY_CAPTURE_PASS`、`DEFINITION_RETRY_PASS`、`NATIVE_INSTALL_CLASSIFICATION_PASS`、`EXTERNAL_ROOT_CANDIDATES_PASS`。前三项首轮退出0；类筛选夹具初次缺少启动期 Agent.codeSourceBridge 字段，原 `c-java93-native84-external-root-candidates-20261016.log` 保留；补齐隔离桥接字段后仅重编检查器并重跑该入口，`c-java93-native84-external-root-candidates-fixture-fixed-20261016.log` 退出0。主构建及已通过入口均未重复，未运行旧矩阵。

检查实际覆盖回调清空后的浅层来源、FutureTask/CF/Inline 操作数、ENTER/EXIT 顺序、构造前判定、共享待解析根、丢失路由/ACK；实际 worker 在 Instrumentation 代理下无新事件重试并保留旧 owner，成功只移除对应项；真实缺文件 IO 可重试而缺 payload 不报就绪；真实类头引用保留、非候选不进入 ASM、后发现来源和早期负缓存重新判定。均为隔离检查，未安装真实 Agent/Core/Native 控制器、未执行实际 VM retransform，不能代称 premain 安装或游戏效果。游戏进服、性能及 Native 实际阶段恢复 **NOTRUN**。

匹配候选 `distribution/candidate-c-java93-native84-root-lazy-wip-20261016`：Core2960410、prelaunch23226字节，随源码和本摘要同步 GitHub，无源码 ZIP。未命中保留仍需遍历类头/常量池，不能声称所有 class 均为 O(1)；浅层回调引用读取仍同步。启动重变换减量、物理栈查询的 Native token 替代、全池队列定位和 certification/readback 快照等尚未整体改写，已删容器防护及未知载体缺口保留；完整 C/B 不标完成。

---
## 2026-10-16：C 接续——停组策略发布、Native 返回语义与任务捕获

接续 `16ae71a`，Java ABI92 / Native ABI83 / SourceMapBridge2 / Storage3 / Journal6，客户端协议 `r3-client-8`。本包按用户指定只收口这三项；整包源码写齐后统一构建，没有恢复旧容器 Hook。

停组先验证完整目标和返回模式，为所有所选 Module 绑定不可变策略，再按 DefinitionBridge 策略、Bootstrap 停止门、Native 停止门的顺序发布，最后捕获根并清理。普通停止、客户端停组及启动/存档恢复均走所选模式；同模式 prepare/stop 复用固定 UUID。非法模式或目标不发布停止状态；有效目标发生发布故障时保留实际已发布围栏并跳过后续清理。

Native Attach/GetEnv 将实际 API 结果和观察结果分开：JNI 槽登记或 JVMTI 包装失败记录覆盖缺口，保留原成功环境、pending Throwable 身份及原 Win32 LastError，不以观察失败伪造 JNI_ERR、Detach 或 Dispose 回滚。受限/已确认停止来源和准确受保护输出目标继续拒绝；真正交付失败的未发布 JVMTI 环境仍清理。表交换成功后才发布观察记录，失败释放未发布记录、范围和来源租约；诊断改为 JVMTI_FAILURES，不把观察失败误称为全部拒绝成功。

任务字段解析、来源重捕获、克隆通知和构造回调移出 LISTENERS 全局锁；短锁只预留在途、维护注册快照及合并真实结果。来源/绑定读取同步，图解析不持长期运行时锁，避免与 TAGS 的锁顺序反转。同步取消/请求退役重入只投递既有任务收据。事件绑定 owner、session、Registration 和 epoch，能定位的丢失按实际任务/主体保留；无法定位的首次捕获失败仍全局阻断，DROPPED 只累计诊断，不重置以掩盖历史丢失。共享发布在途和未归并失败仍暂缓结清，关闭不能丢弃这些义务。

统一构建 `build-c-java92-native83-policy-capture-20261016.log` **PASS，1分21秒**，21项任务中15项执行、6项保持最新，含 Native83 编译链接。Java 检查器编译退出0。Native 夹具首次编译因既有 Host 辅助函数仍用旧参数形式失败，修正为现有参数数组后仅重编夹具；原 `compile-c-java92-native83-policy-native-fixture-20261016.log` 保留。首次隔离运行缺少生产 Agent 原本开放的 sun.nio.ch 等包，`c-java92-native83-policy-capture-20261016.log` 保留；补齐同样的模块开放后，`c-java92-native83-policy-capture-access-fixed-20261016.log` **退出0**，三项 `STOP_POLICY_PUBLICATION_PASS`、`TASK_CAPTURE_SCOPE_PASS`、`NATIVE_VM_RESULTS_PASS` 全部通过。未重复主构建、已通过检查或旧矩阵。

策略检查调用实际 prepare/publish，覆盖所选非默认模式、重复固定 UUID 与非法选择；使用隔离元数据和空类快照 Instrumentation 代理。任务检查在实际 receive 内阻塞解析，确认全局监视器可获取、在途结清被拒、发布后无关 owner 可用，并覆盖准确失败行保留/确认及未知丢失拒绝。Native 夹具调用真实 JVM 的 GetEnv/Attach、原 JVMTI 环境和 Windows worker，以夹具内 TLS 不可用/表交换失败覆盖原返回、pending Throwable、LastError、输出目标拒绝和失败记录释放。未安装真实 Agent/Core/Native 控制器，不能代称实际安装、游戏启动或游戏攻防通过；这些场景 **NOTRUN**。

匹配候选 `distribution/candidate-c-java92-native83-policy-capture-wip-20261016`：Core2928135、prelaunch23226字节。源码、匹配 JAR 与本摘要同步 GitHub，不生成源码 ZIP。已删容器防护、未知载体及间接/原生不透明路径仍有缺口，完整 C/B 尚未完成。

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

接续 `6fc3ea4`，Java ABI90 / Native ABI82 / Storage3 / Journal6，协议 `r3-client-8`。本包先写齐再统一构建，吸收 Buer 的阶段交接与集中入口组织思路，没有复制其包名信任、全局属性授权或函数表回写。

Agent 在首次发布前认领安装状态，安装中、失败或部分安装不再重复追加 hook；Core 每次查询真实 Agent 状态，FULL 按现有 Java 边界重新计算。保留 Java-only 合同，非 FULL 明确报告，不新增全局游戏启动阻断。预启动只报告 PREPARED；修复启动选项与六种返回策略被无条件改成 default，按实际选中的服务端世界读回停组策略，持久记录真实损坏不再当空目标继续。Native 的 JNI 表发布与必要 Unsafe/arraycopy 完成分别记录，部分失败后的重复交接不会被误报成功。

伤害、死亡/移除、生命写入、防降/禁疗、吸收值、着火、字段和 Unsafe/JNI 写门共用当前准确 body、代次、地址及世界记录资格；旧发布不跟随可变 binding 进入新代。补自定义不调 super 的两个 setter 入口，吸收读取真实基础字段或 Player 同步数据，不依赖自定义吸收 getter。生命数据整体、items 表、实际 health/受保护 absorption 数据项及底层存储的替换接同一关系，避免先换载体再绕过值写门；标准 map 内部搬移保留。防护与禁疗独立，原合法生命周期沿既有准确 permit，客户端镜像同样接续。

首轮统一构建 **PASS，1分23秒**（`build-c-java90-native82-startup-life-20261016.log`），检查器编译及 `startup-protection-bytecode` **退出0**：实际 JournalLineage 所选服务端世界的 STOP/RETURN 和六种策略读回通过；真实继承合同的生产 DispatchBoundary 织入及独立 setter 投影拒绝/允许次数与原异常身份通过。声明元数据仅在检查进程临时设置后恢复，Core guards 未安装；不能代称真实 Agent 注册、启动或带策略保护通过。实际启动、Native 安装与游戏场景 **NOTRUN**。

收尾将生命表键限定为真实 Integer，避免调用自定义 Number 方法；重编时遗漏工具参数的失败日志 `build-c-java90-native82-startup-life-exact-key-20261016.log` 保留。恢复原配置后最终构建 **PASS，51秒**（`build-c-java90-native82-startup-life-final-20261016.log`），未重复已通过检查。匹配候选 `distribution/candidate-c-java90-native82-startup-life-wip-20261016`：Core2968376、prelaunch23226字节，随源码与既有摘要同步 GitHub。未知反射/MH、复杂自定义载体及原生不透明状态仍有边界，完整 C/B 尚未完成；自身外观、武器注册、驱动部署与源码 ZIP 未做。

---
## 2026-10-15：C 接续——自定义包装回调与双参数组合

接续 `a7e1a47`，Java ABI89 / Native ABI81 / Storage3 / Journal6，协议 `r3-client-8`。本包接齐源码后统一构建；Native 和驱动源码未改。

自定义业务方法中的 Runnable、Callable、Supplier、Function、Consumer、Predicate、BiFunction、BiConsumer 原接口调用接当前能力消费者。交接核当前 VM Plan、原指令和实际 callback/参数；同步 handoff 只消费当前原 Call 一次，callee 的真实参数 hook 逐项确认后复用原来源 Cell，并沿原 Call 回填返回/异常。缺失或不匹配保留 UNKNOWN，不搜索外层任务、不新建异步票据或扩大原动作作用域。标准 BiFunction.andThen(Function) 与 BiConsumer.andThen(BiConsumer) 接准确原工厂及私有组合体，保留原参数、中间结果、次数、顺序和 Throwable。

`build-c-java89-native81-defined-bi-20261015.log` **首轮 PASS，1分17秒**，21项任务中14项执行、7项保持最新；Native81 使用原未改产物。检查器编译退出0，现有 `equipment-defined-bi-bytecode` **退出0**：`EQUIPMENT_DEFINED_CALLBACK_BYTECODE_PASS`、`EQUIPMENT_BI_COMPOSITION_BYTECODE_PASS`。实际 RunnableAdapter.call 经生产 ExecutionBoundary/ControlClassWriter 织入后隔离加载，token=null 普通分支保留一次原回调、原结果和 Throwable；两类实际 JDK 双参数组合体的四处 Bridge 调用保留参数、null、中间值、顺序及 RuntimeException/Error 身份。未安装 Core/Native 授权、未发布人工 Plan/Scope，不能代称真实 java.base 安装、带授权 handoff 或游戏保护通过；这些场景 **NOTRUN**。未重复旧矩阵，旧失败记录保留。

匹配候选 `distribution/candidate-c-java89-native81-defined-bi-wip-20261015`：Core2958026、prelaunch23309字节，随源码与既有摘要同步 GitHub。未知具体类调用、反射/MH、未知执行器/键语义与原生不透明状态仍有边界，完整 C/B 尚未完成。另已在本机忽略的 VS Code settings 中将 Gradle 导入 JDK 指到现有 Java17；未观察编辑器重新导入结果。

---
## 2026-10-16：C 接续——异步能力与复杂自定义载体

接续已推送的 `02c61a0`，Java ABI88 / Native ABI81 / Storage3 / Journal6，协议 `r3-client-8`。本批源码全部写齐后统一构建；自身外观、武器注册、驱动部署和源码 ZIP 继续暂缓。

当前合法能力回调提交的标准执行器、Future/ForkJoin 包装和 CompletableFuture 原回调接弱上下文。阶段仅绑定本次真实 callback/Completion.fn，包装交接核当前认证 JDK body 和真实 callable/task/runnable 字段；仅当前实际任务帧在原 callback 位置领取一次，不跨内层任务借外层关系。成功 FutureTask 取消按原真实 CAS 清理；无 body 的 tryFire 和未知提交结果不伪记完成。原回调、返回、Throwable 和 Future 发布/异常处理顺序保留，未安装 Core 时直通原接口。

worker 新建当前线程作用域，只接原装备的 ItemStack/NBT/能力数据；不持提交 Scope/Thread，不授换槽、实体、世界或新能力生命周期许可。每次使用仍核 owner/代次、原物品与槽位、世界、当前有效 dispatcher 和准确 optional/派生 marker 链，失效/脱链/换装及未知或停止来源拒绝借权。派生 Optional 在 worker 内自然消费或再次提交时沿准确当前链续接；正常/异常都关闭委托并恢复原线程状态。Native 增加准确 Runnable/Callable/BiFunction/BiConsumer 四个接口契约，原 CP/BCI/版本/来源和二次函数边限制保留。

复杂槽位 helper 补祖先 super、直接 interface.super 与实际继承 default 的准确选择。继承标准 get 的自定义 ArrayList/LinkedList/HashMap/LinkedHashMap/IdentityHashMap/EnumMap/NonNullList 等子类，先由 VM 确认真实声明，再按既有标准父存储复核；自定义覆写仍走原 helper trace。兼任 callback 的实际 Collection/Map/ItemStack/Tag 数据不再整颗跳过，回调方法本身仍不能冒充普通业务入口；custom Number 数据 holder 不再误当不可变标量。没有调用 getter/key/iterator 重放或新增泛用写权。

统一构建 `build-c-java88-native81-async-carriers-20261016.log` **首轮 PASS，54秒**，21项任务中15项执行、6项保持最新；检查器编译退出0。`equipment-async-carrier-bytecode` **退出0**：19个实际 JDK 任务类、30处原 callback 的生产转换及 hook 连接通过；真实 RunnableAdapter 的隔离 body 在单线程执行器上保留一次执行、worker 线程、原结果及 ExecutionException.cause 身份。Core handles 始终未安装，只证明上述转换和普通直通行为；本批真实 java.base 安装、带 Core 的异步授权/撤销、复杂载体 Native 保护和游戏效果 **NOTRUN**。未重跑旧矩阵，旧失败记录保留。

候选 `distribution/candidate-c-java88-native81-async-carriers-wip-20261016`：Core2949381、prelaunch23309字节，随源码与既有摘要同步 GitHub。未知执行器/自定义包装、未知键语义、反射等间接链、间接接口 special 和不透明原生状态仍有边界，完整 C/B 尚未完成。

---
## 2026-10-16：C 接续——槽位 helper 与标准函数组合

接续已推送的 `ca5edc8`，Java ABI87 / Native ABI80 / Storage3 / Journal6，协议 `r3-client-8`。本批源码全部写齐后统一构建；自身外观、武器注册、驱动部署和源码 ZIP 继续暂缓。

自定义槽位 getter 的原同步 Java 调用接准确 receiver、原参数、实际 helper HEAD、原返回及嵌套读取，仍归同一实体/代次/槽位。私有、静态、真实虚分派、唯一 default 和直接父类普通 super 方法通过 VM 只读声明识别，不调用 getter、加载器或类初始化。原操作只执行一次，异常保留原对象及原 catch 顺序；原异常分支、歧义、native、构造器、未知平台方法、超限或 helper/root 角色冲突保持 unresolved。已有根入口先拒绝重复 mint，父类已作 root 时不会误抛子类 anchor 异常。最终关系仍按当前原存储复核，未知槽不伪造 EMPTY。

JDK Function.compose/andThen、Consumer.andThen、Predicate.and/or/negate 在真实工厂指向的准确私有实现体接原回调；保留中间值、顺序、短路和原 Throwable。子回调委托要求同线程、同一当前能力作用域和物理调用链，由现有 Native callback 边检查接续，结束恢复原层。任意自定义组合、异步、未知间接链、复杂 interface/super 特殊调用及不透明原生状态仍有边界。

统一构建 `build-c-java87-native80-helper-composition-20261016.log` **首轮 PASS，52秒**，21项任务中15项执行、6项保持最新。检查器编译退出0；现有 AuditFixCheck 的 `equipment-helper-composition-bytecode` **退出0**，实际 Mob getter、ItemStackHandler 四个引用/primitive/void helper 体按生产织入隔离加载通过；真实 JDK 三接口组合体的11个 Bridge 调用通过顺序、中间值、短路及 RuntimeException/Error 身份检查。Bridge callbacks 全程未安装，只确认隔离字节码及直通语义，不代表实际 java.base 重变换、Native 分派或游戏授权动作通过。没有重跑旧 Forge/Optional、Netty、世界或退役矩阵；本批游戏场景 **NOTRUN**，旧失败记录保留。

候选 `distribution/candidate-c-java87-native80-helper-composition-wip-20261016`：Core2930187、prelaunch23309字节。源码、匹配 JAR 与既有报告摘要同步 GitHub；完整 C/B 尚未完成。

---
## 2026-10-16：C 接续——能力回调链与复杂容器

接续已推送的 `ddd92bc`，Java ABI86 / Native ABI79 / Storage3 / Journal6，协议仍为 `r3-client-8`。本包将能力回调、Java Optional 后续调用及复杂标准载体写齐后统一构建；自身外观、武器注册、驱动部署与源码 ZIP 不在本包。

真实 Forge LazyOptional 的供应者、消费、映射、筛选、回退供应者、即时监听及失效监听接原 callback/value 操作数，原回调只执行一次，所有 Throwable 保留原对象。getValue 的原 monitor 和异常出口保留，发布 Gate 仍在供应者返回及原 MutableObject 构造后才开启。失效仍调用原 Set.forEach 一次；只对当前真实 HashSet/lifecycle，用 Core 包装分别接真实外层分派 Consumer 和当前 listener，原 clear 与异常迭代行为保留，没有重放 iterator 或猜隐藏捕获。

Forge map/filter 的 Java Optional 和 lazyMap 非空结果沿同一当前 Scope、原 parent、实际 value/supplier 记录弱派生关系。JDK Optional 的 ifPresent/map/filter/flatMap/orElseGet/orElseThrow(Supplier) 在原接口调用点接入同一门，Core 尚未安装时直接执行原 JDK 回调。当前 owner/root、optional 状态、原 supplier、真实 resolved 值及生命周期持续复核；动作关闭、脱链或其他 owner/stack 不借权。全局共享 Optional.empty 没有按对象登记派生 owner，其 fallback 原样执行，但不因此获得装备写入委托；不把临时映射值伪造为实体持有的 payload。

Native 支持 Forge/JDK 的 accept/get/apply/test 八个准确接口契约，按当前真实 CP/BCI、Method 版本、来源及最多16个物理帧接直接业务调用边。普通 helper 不因同时实现 Runnable 等被整类拒绝。二次函数入口仅允许真实 bridge/已匹配隐藏映像的准确纯转发适配；只读现有 Plan 的实际指令、调用点和原始 callbackSafe（非构造器、非同步、无原 handler），唯一 invocation 与当前 CP selector 一致才认可。旧 retirementSafe 计算和退役资格未放松，未新增 JVMTI capability、JNI 导出或泛用写许可；未知/停止来源、反射/MH/动态不可解析路径及超限链继续拒绝。

复杂槽位查询补真实 MapN probe、Guava RegularImmutableMap 桶/mask/smear/nextInKeyBucket、JdkBacked 委托与 Singleton 的实际 equals 方向；HashMap 树桶沿原 next 前置判断、parent/root/left/right 和原 find 回退路径读取，不用链表 next 代替树查询。Enum/Integer/String 按准确身份及实际字段、String 缓存/hash/value/coder/UTF16 字节读取，不调用任意 key.hashCode/equals/compareTo。EnumMap 值数组补入通用 payload 图，NULL sentinel 过滤，keyUniverse 仍是借用数据。原 owner/generation、拟写值预测、字段/数组/offset/memory 和 backing observer 保持接续。未知 key/Map/容器覆写、异常或超既有容量的载体仍明确 unsupported。

`build-c-java86-native79-callbacks-20261016.log` **首轮 PASS，1分6秒**，21项任务中15项执行、6项保持最新，Java86/Native79 编译链接完成；`compile-c-java86-native79-callbacks-20261016.log` **退出0**。现有 AuditFixCheck 的 `capability-callback-bytecode` 复用 Forge 三类织入，并将真实已变换 JDK Optional 单类重命名后按原字段/方法/frame 隔离定义，**退出0、CAPABILITY_BYTECODE_VERIFY_PASS、CAPABILITY_CALLBACK_BYTECODE_PASS**。检查确认未安装 Bridge handles 时六处回调的原参数、次数、返回及 Throwable identity；只证明隔离字节码及该直通语义，不代表实际 java.base 重变换或游戏授权动作通过。未重跑旧槽位、退役、Netty/世界矩阵，未启动游戏。

Native 当前 VM 深链、复杂键/树桶实际保护、失效/换装/停组等游戏效果 **NOTRUN**，旧失败记录全部保留。候选 `distribution/candidate-c-java86-native79-callbacks-wip-20261016` 含 Core2913173、prelaunch23309字节；源码、匹配 JAR 与原摘要同步 GitHub。任意异步/未知函数组合、未知自定义 getter/原生状态等仍有边界，完整 C/B 未宣称完成。

---
## 2026-10-16：C 接续——能力消费者与自定义装备槽

接续已推送的 `2fdef27`，Java ABI85 / Native ABI78 / Storage3 / Journal6，协议仍为 `r3-client-8`。本包写齐能力消费者委托、自然 getter 读取轨迹和当前槽位的实际写门后统一构建；自身外观、武器注册与驱动部署继续暂缓。

`LazyOptional.ifPresent` 在原调用位置保留同一 consumer/value，消费者只执行一次。资格绑定当前装备动作、真实有效 optional/payload、当前 root 和每个共享 owner；Core 私有调用点与 Native 按实际 jmethod/BCI、Method/ConstMethod/CP 版本、栈底深度及来源复核消费者本体，或真实 bridge/已绑定隐藏 accept 的一层直接目标。没有新申请 JVMTI locals capability；已有能力时再核对实际 receiver。额外用户层、未知/停止来源与版本变化不取得许可，正常/异常退出关闭委托并恢复上一层。未知 supplier/listener/其他 functional 回调没有因此泛化。

自定义实体及覆写槽位 getter 按准确 Class 弱登记、单类重变换，在原 dispatch/action fallback 之前记录原 getter 实际读过的字段、数组位置、受支持 Map/List 查询及 EquipmentSlot/Type selector，原操作和返回只执行一次。关系绑定实体、当前代次、真实 root 与终端存储；原异常、吞异常分支、未知调用、ItemStack 条件分支或不完整路径不冒认为完整槽位，也不虚构六个 EMPTY。未解析槽的 setter 保留明确 pending。只读取轨迹已到达的位置，不重放 getter、supplier、序列化或扫描实体/世界对象图。

实际槽位接当前 shares/main/hand、物品/NBT/能力归属及字段、数组、offset、memory 与容器入口。标准布局按本次拟写值解析当前位置，内容未变的受支持写入保持；引用裸内存写仍拒绝。准确实际 setItemSlot body 接原动作链，不能凭同类或同模块借权。成功 store 接现有 observer，标准容器换 backing 后登记当前载体/物品；自定义中间引用或 selector 改变则旧路径退役，等待下一次自然 getter 重绑。静态依赖使用真实初始化字段及实际 Unsafe static base，B/C/S/boolean 数组 load 按原 JVM 值归一；HashMap 树桶、未知查询/key 语义、未知容器覆写、计算/native opaque 路径继续保留边界。

首轮编译因两处 `Map<?>` 参数数目错误 **FAILED，38秒**，原日志 `build-c-java85-native78-equipment-first-failed-20261016.log` 保留；修为 `Map<?,?>` 并补 EquipmentSlot.Type.ordinal 后，统一构建 **PASS，1分9秒**，21项任务中15项执行、6项保持最新，含 Java85/Native78 编译链接。现有 AuditFixCheck 首次独立加载完整 Mob 时因未修改方法与父加载器 LivingEntity 的 runtime package 不同而 **VerifyError**，原日志 `c-java85-native78-equipment-slot-bytecode-first-failed-20261016.log` 保留。检查随后只保留本次修改的真实 Mob getter 本体，复用同一实际字段/父类和生产 writer；仅重编检查并执行当前 getter 及三个 Forge 类的织入加载，**退出0、EQUIPMENT_SLOT_BYTECODE_VERIFY_PASS、CAPABILITY_BYTECODE_VERIFY_PASS**。未重复已通过的主构建，未运行旧矩阵或启动游戏。

本批 Native 回调核对、实际能力使用、换装、共享槽与停组效果 **NOTRUN**；字节码通过只确认隔离 getter 本体与 Forge 织入可被 JVM 接受，不代表游戏安装/行为通过。Native75 的旧预启动中止记录保留，完整 C/B 尚未宣称完成。匹配候选 `distribution/candidate-c-java85-native78-equipment-wip-20261016`：Core2890897、prelaunch23309字节；源码、匹配 JAR 与原报告摘要同步 GitHub，未生成源码 ZIP。

---
## 2026-10-15：C 接续——自定义能力与实际载体

接续已推送的 `5ff82f3`，Java ABI84 / Native ABI77 / Storage3 / Journal6，协议 `r3-client-8`。本包先写齐自定义能力、深层载体、自然返回观察及动作接续，再统一构建；自身外观、武器注册与驱动部署继续暂缓。

能力图沿当前 ItemStack 的实际 dispatcher/provider 实例字段、自然解析的 LazyOptional 值和真实 getCapability 返回值延伸，不再局限于固定流体 provider。实际 provider 调用返回后、Forge 的 isPresent 之前观察原返回；正常只保留真正选中的弱关系，异常和非选中结果结清。关系逐当前 root、provider 在 caps 中的身份及 optional 有效状态复核；invalid optional 不沿 resolved 残留追 payload，Provider.valid=false 不沿 query 弱观察曝光，其他仍被实际实例字段持有的独立数据根保留。

新增实际 VM 只读字段通道，返回准确 Field、声明类、描述符、类型、offset、宽度及当前值。两个元数据原入口只来自本机真实 Unsafe 注册表；查询时才读取已初始化的真实 Unsafe 实例。字段类型尚未解析、必要元数据不可用或静态声明尚未初始化时明确失败，不执行自定义类加载、getter、序列化器、supplier 或构造器重放，不以 null 冒充成功，不遍历自定义 static 图。

自定义数据、嵌套 ItemStack/ItemStackHandler/NBT、custom Tag 和自定义 curative List 接原当前归属及字段、元素、数组、offset、memory 门。常见 JDK/Guava 列表、映射、集合、包装器、NonNullList 和原子容器读取实际字段/backing/node；可变 Map key 与 value 都进入数据图。子视图只登记其实际逻辑槽，临时快照和未使用容量不冒充 payload。保留原容器分步更新，成功写通知复用现有 observer 回读新图；数组准确 index 检查后不再重复无 index 的装备判断，其他来源、世界及实体门保持。浮点 no-op 按真实 NaN 位判断。

载体按实际 Class 弱登记并逐类安装实例 Java body，实际 default interface 和合法递归也按当前 receiver、方法、父调用、层次及物品动作绑定。共享 owner 分别核对，脱链旧目标失活；回调、同类其他实例或同模块身份不能借用动作。世界、实体、注册表、类加载器、线程、运行时资源等为借用叶。直接业务调用可接续；未知 functional/supplier/listener 回调不自动获得写权。

统一构建 **PASS，1分16秒**，21项任务中16项执行、5项保持最新，Java84/Native77 实际编译链接完成。复用现有 AuditFixCheck，仅对实际 Forge ItemStackHandler 的 carrier-call 包装，以及本批 CapabilityProvider/CapabilityDispatcher/LazyOptional 织入执行 `-Xverify:all` 加载，**退出0、CARRIER_BYTECODE_VERIFY_PASS、CAPABILITY_BYTECODE_VERIFY_PASS**；Forge 保留生产 frame、只计算 maxs。没有重跑旧 FileDispatcher/Netty/世界矩阵，没有启动游戏。本批自定义能力实际使用、失效、共享 owner 和停组行为 **NOTRUN**；Native75 的旧预启动中止记录保留，编译/字节码结果不代称游戏通过。

原生 opaque 状态、未解析字段类型、不可安装类及未知自定义槽语义仍保留明确边界，完整 C/B 尚未宣称完成。匹配候选为 `distribution/candidate-c-java84-native77-custom-carriers-wip-20261015`，Core2832142、prelaunch23309字节；源码、匹配 JAR 与原报告摘要同步 GitHub，未生成源码 ZIP，未安装或加载驱动。

---
## 2026-10-15：C 接续——文件副本与能力容器

接续已推送的 `426c1b5`，Java ABI83 / Native ABI76 / Storage3 / Journal6，协议仍为 `r3-client-8`。继续写齐两个明确缺口后才统一构建；自身外观、武器注册、驱动部署仍不做。

实际 Windows JDK `duplicateForMapping → duplicateHandle(J)J → 新 FileDescriptor 发布` 现已接齐。保留原 IoBoundary，按实际父描述符和 handle、同一次原方法/当前 VM 版本及栈层接入 op10，原来源、停组、owner、host 和字段门不绕过。实际 JNI 返回寄存器先进入本次自有元数据，再执行原 setHandle/registerCleanup；只有同一新目标、真实返回及 FileCleanable.referent/fd/handle 一致才完成分配和来源登记。此入口也取得实际 NativeFileFrame，不漏过 controller 内部调用。

原生成功后 Java 发布失败的副本在进入 native 前已有强 pending 位置和未分配的 DescriptorRecord，正常发布后移除；异常保留本次实际返回、父/目标、来源和版本，正常/异常 finally 均关闭原 FileOperation。RecoverySources 的现有 remaining/retireGroup 能枚举该事实，仅已按原规则选中的准确目标才进原异步 retire/release。handle 未安装、目标/清理关联改变、共享或未知来源继续 pending，不凭 long 制造描述符或关闭资格。

当前装备/curative ItemStack 的标准 CapabilityProvider、已初始化 CapabilityDispatcher、caps/writers/names 数组与实际监听集合接入原字段、数组、offset 和 memory 门。有限标准 provider 为本机 Forge FluidBucketWrapper、FluidHandlerItemStack/Simple 及其 Consumable/SwapEmpty，按其真实 holder/容器字段继续追踪非 EMPTY LazyOptional、MutableObject 与 HashSet/HashMap backing，pending lazyData 也接原 NBT 图。关系逐当前 owner 复查，脱链解除，共享 owner 分别核对。Guava 单元素 SingletonImmutableList 的实际 element 也补齐，临时读取视图不冒充 backing。

原 doGatherCapabilities 和 LazyOptional.getValue 只在供应者回调返回后的实际字段发布段持原 target FieldPolicyGate；不跨回调持锁，正常/异常关闭。listener/lifecycle 接续沿现有实际装备动作许可，不给未知回调泛用写权。任意 provider 私有 payload、未知可执行 supplier/listener 内部状态与非上述标准实现仍未覆盖，不把元数据图等同完整能力行为。

统一构建 **PASS，58秒**，21项任务中16项执行、5项保持最新，包含 Java83 与 Native76 实际编译链接。复用 AuditFixCheck 只检查本批实际 JDK FileDispatcherImpl 及三个 Forge 类的当前织入，**退出0、RESOURCE_BYTECODE_VERIFY_PASS、CAPABILITY_BYTECODE_VERIFY_PASS**；Forge 仅 COMPUTE_MAXS 保留实际 frame。没有重跑已通过的 Netty/世界表或启动游戏；本批真实映射复制/失败清理、能力使用/失效与停组行为 **NOTRUN**。上一批 Native75 的预启动中止仍按原记录保留，不能代称当前运行通过。

匹配候选为 `distribution/candidate-c-java83-native76-io-capability-wip-20261015`，Core2774289、prelaunch23309字节；源码、匹配 JAR 和原摘要直接同步 GitHub。未生成源码 ZIP，未安装/加载驱动，完整 C/B 未宣称完成。

---

## 2026-10-15：C 接续——删除搬移、标准队列与 socket 创建

接续 `563ddfb`，Java ABI82 / Native ABI75 / Storage3 / Journal6，协议 `r3-client-8`。三个 6.1 Sol max 子代理分别完成世界表、网络队列和 Native 入口；主线程接齐解药载体及版本，整包源码写齐后才统一构建。自身外观和武器注册继续暂缓。

标准全局 BlockState reverse map 的 removeEntry、removeNullEntry、clear 与 MapIterator.remove 在原副作用前核对冻结映射。无关项保留原 shiftKeys 循环、size 和 wrapped 更新，仅按本次实际 map/数组/键值/索引接续原两个连续 store。store 被其他门拒绝即显式中止；半写只对实际旧值作准确 CAS 收尾，继续经过原来源、目标和 FieldGate，保留原异常及并发变化，不覆盖后来写入。正常和异常出口均关闭本次作用域。

效果的 curativeItems 扩至本机 JDK ListN/List12、单元素/空列表及 Guava RegularImmutableList，读取实际字段和 backing，不调用未知容器；List12 等临时读取视图不冒充实际数组。实际 provider 覆写在无发布许可下执行，只有原 getter 的真实返回能一次发布；返回列表的 ItemStack/NBT 复用现有保护。任意自定义列表和未序列化 capability 仍保留边界。

Netty 标准有界 MpscChunkedArrayQueue 及 atomic 实现的真实 getter→容量不足→full→offer false→默认 REJECT 链接入既有异常清理和一次 cancel。准确队列、task、当前方法版本和真实栈层逐段绑定；本次 Submission 的未知来源查询不会被重试洗白。未知队列/custom handler/backoff/接受后抛异常仍不取得未入队资格。无限队列没有原版满队列分支。

本机 Windows JDK 的实际 `Net.socket0(ZZZZ)I` 补入原 Native 来源、停组、owner 和 host 门，op9 用原 NativeFileFrame 回执接 Java 的 socket-open 判断，拒绝保留 SocketException；没有拿 Class 对象建立资源释放所有权。既有 unresolved 仍是 pending，新入口若尚未解析也保留；没有主动执行未解析 native、猜 DLL 原地址或把数量改成 ready。`duplicateHandle(J)J` 的准确父 descriptor 关联尚未接齐。

统一构建 **PASS，1分6秒**，27项任务中21项执行、6项保持最新，包含 Java82 与 Native75 实际编译链接。复用 AuditFixCheck，对本批真实 Netty 14 类及 fastutil map/iterator 的织入结果按 `-Xverify:all` 加载，**退出0、NETWORK_BYTECODE_VERIFY_PASS、WORLD_REGISTRY_BYTECODE_VERIFY_PASS**；fastutil 仅 COMPUTE_MAXS，保留生产 frame。现有 socket 场景通过真实 prelaunch 启动，初始化仍报告 `NATIVE_FILE_CURRENT_BINDINGS_PENDING:74`（含尚未首次调用的 socket0）。实际线程栈先后位于 BufferBoundary/ASM 输出和 HandleWriteBoundary/ExecutionFlow 来源记录，尚未进入 socket 场景；已保存两份栈并核对身份后结束本次自建 JVM115716，GAME_EXIT=-1，该业务检查记 **NOTRUN/未完成**，不计 PASS，也未为等待结果扩大检查或优化旧启动流程。当前游戏冻结/效果/provider 与停组队列场景仍 **NOTRUN**，不能由字节码加载代称通过。

匹配候选为 `distribution/candidate-c-java82-native75-lifecycle-wip-20261015`，Core2747025、prelaunch23309字节。源码、匹配 JAR 和原有摘要直接同步 GitHub；未生成 ZIP，未安装或加载驱动。完整 C/B 未完成。

---

## 2026-10-15：C 接续——标准全局表、解药图与满队列

接续 `646a30e`，Java ABI81 / Native ABI74 / Storage3 / Journal6，协议 `r3-client-8`。上一整包已构建并同步后，本轮继续补已确认的标准载体，不把未验证项改成已完成。

标准 BlockState IdMapper 的实际 idToT ArrayList/size/backing、tToId identity map 的 key/value/mask/strategy/defaultReturn 及其数组进入原五查询。所有冻结格保留网络序列化所用的 state→id；Global palette 另保留实际读取的 byId 槽。addMapping 在发布反向条目前先核对真实 state/id，负 id 也不能先破坏冻结反查再靠原异常退出。原版 rehash 的完整本次 tuple 经冻结内容比较后，逐字段按实际 receiver/值/原顺序与同栈层接续，正常及异常 finally 撤销；未用 ID 的新增及原版扩容保留。未知 List/registry/strategy 不执行，私有表 shiftKeys 的移除重排仍可能被拒绝，没有增加搬移许可。

效果的当前 activeEffects/hidden→curativeItems→标准列表/backing→真实 ItemStack/NBT 复用已有装备图与写门，多 owner 分别核对，删除或替换后旧根立即失去当前归属。首次 getter 只接受真实 IForgeMobEffect 默认 provider 及本次实际返回列表的 null→值发布，正常/异常 finally 结清；原 setDetailsFrom/setCurativeItems 沿原合法 scope，发布前捕获新图。不借装备动作修改 recipe；未知自定义 List/ItemStack/provider override 和未序列化 capability 保留边界。

默认满队列拒绝补真实 offerTask false→addTask 假分支→原 RejectedExecutionHandlers$1 ATHROW 事件，绑定同一 Submission/task/executor、实际 LinkedBlockingQueue 与默认 REJECT 单例、实际栈底和当前 VM 方法/BCI/版本。确认未入队后沿既有 Cleanup0/1、原 cause 和一次 cancel 结清；custom/backoff/MPSC/接受后 throw 或任何身份/读取变化仍 pending。没有新增 Native 接口或许可层。

统一构建 **PASS，57秒**，27项任务中20项执行、7项保持最新；匹配 AuditFixCheck 编译退出0。真实 Netty 四目标及原 fastutil rehash 经本批转换/织入后，`-Xverify:all` 加载检查 **退出0、NETWORK_BYTECODE_VERIFY_PASS、WORLD_REGISTRY_BYTECODE_VERIFY_PASS**；rehash 检查保留织入器生成的 frame，仅计算 maxs，不用重算 frame 掩盖安装问题。真实游戏冻结/同步、解药及停组/满队列分支仍 **NOTRUN**；驱动未部署，完整 C/B 未完成。匹配候选已写入 `distribution/candidate-c-java81-native74-carriers-wip-20261015`，Core2728834、prelaunch23309字节；源码、匹配 JAR 和原摘要同步 GitHub，未生成源码 ZIP。

---

## 2026-10-15：C 接续——冻结载体、效果内部状态与拒绝队列

接续 `ecae56e`，Java ABI80 / Native ABI74 / Storage3 / Journal6，协议 `r3-client-8`。本包继续实际强度源码；Native/驱动未改，自身外观与武器注册暂缓。

冻结区域的原版 SimpleBitStorage 两个写入口及其真实 long[]、标准调色板值表、section/PalettedContainer.Data/storage 替换接入原字段、数组、offset 与 bulk 门。每次按当前 visible holder/full chunk/section/palette 关系逐冻结格比较，不把旧图或同类对象当授权；共享 storage/数组的所有实际冻结 owner 保留，未冻结 bits、padding 和保持冻结内容的原版扩容可继续。部分字节写入按 VM 数组布局与实际字节序比较，无法取得值的 bulk 只拒真实接触冻结内容的区间。record 字段使用实际 VM offset，引用宽度由真实 Object[] 布局取得。未知区块、palette/storage 及未观察原生机器码写入仍无泛化覆盖。

效果保护跟进当前 activeEffects/hidden 链的真实 Optional→FactorData，禁止连接替换及外部字段/offset/bulk 改写。原版 tick 仅接受真实 lambda/Optional/instance.tick/已有 effects-tick 完整链；getFactor 只准同 owner 实体、同一 FactorData、原 current 值回填 previousFrame。作用域按实际重入层次配对，正常及异常 finally 关闭；curativeItems 深层图和自定义载体仍保留边界。

原 Netty SingleThreadEventExecutor 的真实静态 reject Throwable、实际提交 tuple、完整调用链和栈底深度共同确认未入队。仅此分支逐段尝试消息 release、原 cause 的 promise setFailure，并在异常退出时配对原 cancel；正常 false 继续原 caller 位置取消，一次提交只消费一次取消资格。未知 executor/handler、接受后抛异常或无法确认未入队的任务保留来源持有及 pending。WriteTask 仅在原 ctx/msg/promise 已实际清空后退役记录，不因回收入口先删回执而丢失仍持有的来源。

共享全局 BlockState registry 内部 IdMapper/List 本体、object-null 绝对地址及未知候选不属于本包可定位的私有载体链；不提升为已覆盖。

本包统一构建 **PASS，40秒**，Core/Agent/Bootstrap 与既有夹具实际编译，Native74/Storage3 源码未改、构建任务保持最新。匹配 AuditFixCheck 编译退出0；真实 Netty write/WriteTask/SingleThreadEventExecutor 经当前转换器及 `-Xverify:all` 加载，**退出0、NETWORK_BYTECODE_VERIFY_PASS**。真实游戏冻结/解除、效果 tick/读取及停止后的队列运行仍 **NOTRUN**；构建和字节码加载不代称实际游戏通过。完整 C/B 未完成，匹配候选已写入 `distribution/candidate-c-java80-native74-world-wip-20261015`，Core2704678、prelaunch23309字节；源码、匹配 JAR 和现有摘要同步 GitHub，未生成源码 ZIP。

---

## 2026-10-15：C 接续——停组后的原队列收尾

接续 `b084f1f`，Java ABI79 / Native ABI74 / Storage3 / Journal6，协议仍为 `r3-client-8`。本包集中处理原 Netty WriteTask 已接受入队后停组的收尾；自身外观与武器注册继续暂缓。

原 task 的 ctx/message/promise 及发送来源保留至 run/cancel 的实际 finally。拒绝写入按消息 release、promise tryFailure、原 context flush 分段执行；每段失败仍尝试后续必要动作，清理 token 退出后重抛实际异常。原 promise 校验顺序保持。停组任务在原 recycle 已清空字段的位置丢弃，不再写共享对象池，未从仍在队列的位置提前回收。

清理资格按标准 Netty 4.1.82 的实际 context、outbound buffer、消息、promise、旧 Entry、池块/handle/cache、计数器及 leak tracker 派生关系核对，保留脱链前的实际对象。字段、数组槽及 VM CAS/PUT 仍经过原目标策略、门和写入记录。只读 JVMTI 分16帧核对完整框架调用链、实际栈底深度和当前方法版本来源；未知或被停来源改写的方法不能冒充原框架。读取对象关系只读字段，不反射执行外部库方法。用户 listener、自定义 deallocate 及回调重入不能借清理资格；不支持的停组清理 CAS/PUT 明确报错，避免返回 false 后无限重试。

冷字段读取改为 JVMTI 实际声明枚举及原 JNI 只读访问，避免首次 Class.reflectionData 缓存 CAS 在清理门内自拒；不执行库方法、包装类构造或 valueOf，也不初始化未知静态类。只有每个准确获准且实际成功的收尾写入才在原恢复历史中形成终态基线，失败 CAS 不建立；原来源和 unknown 保留。后续业务写可恢复至此基线，不能重新抬高 refCnt 或把 promise 改回未完成；有效终态与未收尾 pending 分开，未知/重叠/更晚写入仍保守处理。

本包统一构建 **PASS，1分1秒**，Java 与 Native74 实际编译链接完成；匹配 AuditFixCheck 编译退出0。新插桩使用真实 Netty 类字节经 `-Xverify:all` 加载，**退出0、NETWORK_BYTECODE_VERIFY_PASS**，仅说明 JVM 字节码校验通过。实际游戏 active→queued→stop→run、池化/并发释放及两阶段检查消费者仍 **NOTRUN**，不把构建或字节码加载视为游戏通过；未知 allocator/载体、直接原生释放与旧平台边界继续保留。完整 C/B 未完成。匹配候选已写入 `distribution/candidate-c-java79-native74-queue-wip-20261015`，Core2674826、prelaunch23309字节；源码、匹配 JAR 与本摘要同步原 GitHub，未生成源码 ZIP。

---

## 2026-10-15：C 接续——底层区域写入、字段图竞态与网络 promise

接续 `839375e`，Java ABI78 / Native ABI73 / Storage3 / Journal6，协议保持 `r3-client-8`。本包先写齐源码再集中构建；三个 6.1 Sol max 子代理分别核对网络、装备和世界，装备未确认新缺口，保留原实现。自身外观及武器注册继续暂缓。

手动冻结补到实际 LevelChunkSection 五参写入及 PalettedContainer 两个私有物理索引写入口，在区块计数与 palette idFor/resize 之前拒绝；四参转发、checked/unchecked/set 和非标准 xyz 别名都经过同一实际槽位判断。按 ChunkMap 的实际 volatile 可见快照、holder 当前 full chunk、section 数组和 palette identity 逐次确认，worker 不依赖返回 null 的 getChunkNow；同 palette 的共享 section 不能绕过，独立初载 copy、未选邻居和解除后的坐标保留原路径。原 PostProcessing/UpgradeData/归档流程沿用。未知 chunk/section/palette 实现、直接 BitStorage/数组/Unsafe 写入及整体载体替换仍未覆盖。

字段图按 VM 实际声明/数组布局使用各 primitive 与引用 CAS，包含原始浮点位及 hidden/record 字段，避免“比较后直接 putVolatile”覆盖后写。任务的首个因果 origin 继续留作来源线索，冲突观察另记共享身份，不再用于独占破坏资格；每槽复查，并在已开原写门后将最终独占复核与 CAS 放在同一 origin 短锁内，原回执准备/收尾及资源释放不在该锁内。已知共享节点保留并报告 PARTIAL；未观察的共享关系与任意 native 写入不提升为完成。

网络停源 gate 移到实际 Netty isNotValidPromise 的成功分支后，保留原 null/message、异 channel、已完成和取消 promise 的校验/回收语义；取消的 writeAndFlush 不再额外 flush 之前的无关写入。合法停源拒绝仍沿实际 context flush。池化 WriteTask 不从仍在队列的位置提前 cancel/recycle，原发送与 finally 回收保持。

当前 Class 消息检查不进入引用计数释放，拒绝检查读取消息来源后也不发布该来源作用域。真实 WriteTask.run 的来源作用域则保持至原 finally；其中的 ByteBuf 引用计数释放与 promise CAS 收尾仍未实跑，停源下的自拒尚未排除。活跃排队后再停组的收尾不计为闭合，不能由当前 Class 消息检查代称通过。

真实 bootStop 检查还定位了来源查询自递归：方法字符串拼接及来源/停源 Walker 自身扩批触发 arraycopy，重新进入同一查询。现移除拼接，真实空 selector/负 BCI 直接返回；SourceQuery 在同线程保留已有来源和显式 unknown，Native 出口也保留该状态，finally 撤销。停源查询在 Native 可用时用固定16帧的只读 JVMTI reader，按真实模块、实际 jmethod/BCI/VM 版本与原 ExecutionFlow 父层来源判断；父层用实际栈底深度区分同方法同位置递归。查询不发放写许可，原目标守卫/回执照常。Native 不可用的外层保留原 fallback，无法读取的重入明确失败。

第四次真实栈确认准确的 Class.reflectionData 缓存 CAS 被 represented-module 停源门误拒，JDK 在缓存为空时反复重试。现仅对既有准确 JDK 调用链及实际 Class 实例字段区分该目标归属；真实 stoppedInvocation、原 offset/控制/来源与字段守卫继续执行，其他 Class mirror 写入沿原限制，未改检查顺序。

首轮78/72源码统一构建39秒通过；前四次直接检查受阻的日志及实际线程栈保留，均核对当前进程身份后只结束自建检查 JVM，退出-1。最新 `build-c-java78-native73-strength-final2-20261015.log` **PASS，13秒**，Core2636524、prelaunch23309字节；Native73已在前一次22秒构建实际编译链接，Storage/驱动未改，驱动未部署。匹配 AuditFixCheck 编译退出0。最新 `c-network-promise-java78-native73-final2-20261015.log` 按原 bootStop/EmbeddedChannel 路径实际结束，**退出0、NETWORK_PROMISE_STOP_PASS**：真实 named fixture Mod Class 消息的异 channel promise 保持原异常且未改写，已完成 promise 保持原异常，取消写不额外 flush，合法停源消息被拒绝且之前无关写入仍正常 flush。该结果仅覆盖此 Class 消息分支，未改检查顺序或参数。世界底层写入及字段图游戏场景 **NOTRUN**，活跃排队后再停组的 ByteBuf/promise 收尾仍未验证，不由本次通过代称闭合。候选 `distribution/candidate-c-java78-native73-strength-wip-20261015`；本轮只同步 GitHub 的源码、匹配 JAR 与既有摘要，未生成源码 ZIP。完整 C/B 仍未完成。

---

## 2026-10-14：C 强度接续——旧帧、实际覆写、区域加载与 JVM 外部保护

接续 `aeb7e34`，Java ABI77 / Native ABI72 / Storage3 / Journal6，协议仍为 `r3-client-8`。本包将旧执行帧、装备动作覆写、手动区域锁和 Windows 游戏进程外部保护整包写齐后统一构建；自身外观及武器注册继续暂缓。

停止组的生产入口现接入真实线程观察、独立早期 JVMTI 能力申请与逐线程 SingleStep。事件中重读当前方法版本、实际归属及执行 token；只对无未知业务清理责任的可退出子集按既有返回策略 ForceEarlyReturn，由真实 FramePop 以异常式关闭原来源 token。递归帧按方法与距栈底深度匹配，只恢复自己成功建立的暂停；未知 finally/锁、框架/共享代码及原生或未结来源作用域保持 pending，不使用会重调 invoke 的 PopFrame，也不宣称回滚。

GameMode、Item/IForgeItem 的标准契约及实际覆写接入完整 receiver/参数与正常/异常退出。override→super 续接同一动作；同模块外部回调也不能借权。补当前 Item、BlockItem/瓶子转换及标准回填助手的准确链，消费效果仅接受实际当前 finishUsingItem。未知 capability 内部、任意 helper、自定义上下文与未观察 native 方法体仍保留边界。

手动区域冻结拦住标准 Level/LevelChunk 方块写入、破坏与已绑定方块实体替换，先拒绝再产生掉落、监听/ticker/onLoad 副作用；时间锁同时覆盖直接时间/天气修改及睡眠重置。规则发布为只读区域快照，归档自身暂停与手动写锁分开，归档与手动锁重叠时等待。原版初次后处理的冻结坐标保留在原 ShortList/数组 slot，解除后只续跑剩余坐标；卸载只忘旧对象索引，存盘/重载保留原载体。非空 UpgradeData 的实际升级范围与锁交叠时整次延后，含原版箱子邻接与叶级联，不重跑已完成的后处理尾部。未知自定义区块/升级器没有泛化完成承诺。

Windows 新独立 owner 规则绑定设备会话实际游戏进程，限制外部新建/复制危险进程、线程句柄；不借 Mod 身份代表整个 JVM。现有管理员命令 `/ronova_pro host protect-jvm true|false` 沿实际玩家/控制台资格，各 runtime 保存自己的选择，最后撤销/关闭时交接。状态分开显示请求、实际回执与 pending；查询不打开设备，旧驱动缺 capability 如实不支持。既有/继承/kernel 句柄及同 JVM Mod 身份不提升为完成，未安装、签名或加载驱动。

最初沙箱构建分别被 Gradle 原生 DLL 加载与 Zig 自路径限制阻断，原失败日志保留。改用已有本机工具链后，`build-c-java77-native72-strength-final-20261014.log` **PASS，1分7秒**，Core/Agent/Bootstrap/Native 与现有夹具统一生成；`build-c-driver72-owner-final-20261014.log` **PASS**，本轮 SYS/管理器重新编译链接，未部署。AuditFixCheck 对匹配运行类路径编译通过。一次真实 prelaunch 入口检查 `c-system-arraycopy-java77-native72-strength-20261014.log` **实际退出0、SYSTEM_ARRAYCOPY_PASS**，新匹配载荷完成原生桥/绑定安装，真实受控数组写拒和原复制语义通过。旧帧强退、装备动作与世界接续游戏分支仍未运行，不能由入口检查或构建代称通过；未重跑旧矩阵。

候选 `distribution/candidate-c-java77-native72-strength-wip-20261014`，源码包 `distribution/Ronova-Pro-C-Strength-WIP-Java77-Native72-20261014.zip`。完整 C/B 尚未完成。

---

## 2026-10-14：C 接续——启动期写入内联与真实数组复制入口

接续 `80ac1a8`，Java ABI76 / Native ABI71 / Storage3 / Journal6；协议保持 `r3-client-8`。prelaunch 为新 HotSpot JDK17 游戏 JVM 禁用对应的 Unsafe 写入及 System.arraycopy 编译器内联，涵盖 plain/volatile/opaque/release、非对齐、CAS/exchange/weak CAS、getAndAdd/getAndSet 与 copyMemory；保留用户参数原序，原有 DisableIntrinsic 清单继续累积。读、fence、分配及无关数学内联不在本包范围。仅直接加载 Mod 的晚挂载方式没有这项启动期约束。

真实 System.arraycopy 从独立、准确的 bootstrap System 方法身份接入原生绑定，不当作 Unsafe 分类。原已发布入口沿既有 seed 重读和版本核对；参数/类型/范围/零长交原实现，重叠复制使用本次私有同类型快照。目标使用既有数组策略与写入记录，受控数组不因反射或 MethodHandle 原入口而放行。源读取窗口在快照前建立，保留获取时和窗口内已有来源，目的只记录实际兼容前缀；引用数组 ArrayStoreException 保留原异常及已写前缀。源请求后缀来源会保守并入该前缀，未声称逐元素来源精确拆分。读端、写端与嵌套 MemoryCopy 均按原 finally 结束。

内部复制不能仅凭 control 计数放行：要求准确当前绑定、未停 owner 及实际最近 controller writer，新查询/写入桥不能借自身身份。安装期间使用已准备的原生 writer 查询，避开 available 尚未发布时的 StackWalker 递归。首次真实启动仍退出1，记录到 ResourceBridge.arrayLayout→反射注解解析→System.arraycopy 的128层递归；原日志保留。一次地址诊断确认本机实际入口为 java.dll 的 FF25 跳板，精确导入 jvm.dll!JVM_ArrayCopy。现只接受对应方法及这个实际导入项，核对名称与当前 IAT 值后直接调用真实 VM 导出，避免核对后再执行可变跳板。临时诊断已移除；另补上新 arraycopy.c 的 Gradle 输入，防止增量构建打包旧 DLL。

首次统一构建37秒 PASS；最终 `build-c-java76-native71-intrinsics-final-20261014.log` **PASS22秒**，Core2586557、prelaunch23309字节。AuditFixCheck 新 system-arraycopy 分支编译通过。最终通过真实 prelaunch 重跑，`c-system-arraycopy-java76-native71-final-20261014.log` **实际退出0、SYSTEM_ARRAYCOPY_PASS**：Method.invoke/MethodHandle 原入口均不能修改真实受控 transformer 数组；普通 primitive/reference 复制、双向 overlap、零长、原参数异常、类型不匹配兼容前缀及异常后继续操作通过。此检查不代称逐元素来源回收、停组或真实游戏检查通过。未重跑旧矩阵或新增游戏实例。

上一包 `c-network-java75-native70-equipment-actions-20261014.log` 现已实际退出0、NETWORK_PRECONNECT_PASS，首次发送及逐次队列来源/原 scope 收尾通过；NETWORK_PRECONNECT_STOP_NOTRUN，不能代称停组效果已运行。候选 `distribution/candidate-c-java76-native71-intrinsics-wip-20261014`，源码包 `distribution/Ronova-Pro-C-Intrinsics-WIP-Java76-Native71-20261014.zip`。旧运行帧自动强退、未观察原生机器码写入及直接 syscall 等边界仍保留；驱动未部署，实际游戏攻防未由本检查代称。自身外观及武器注册继续暂缓，完整 C/B 未完成。

---

## 2026-10-14：C 接续——真实攻击、延后挖掘与 JNI 数组来源

接续 `fea56f9`，Java ABI75 / Native ABI70 / Storage3 / Journal6；协议仍为 `r3-client-8`。标准连接玩家的实际 handleInteract packet→Player.attack 接入本次主手与准确目标委托。挖掘从真实 START/有效 STOP 建立续接，ServerPlayer.tick 的实际调用同时传入玩家和 GameMode；只允许当前同一世界、目标坐标、选择槽、主手栈和代次。无挖掘续接的普通 tick 只有原状态推进，没有装备修改权。实际 removeBlock 前复查目标；外部 Forge 回调、嵌套 tick、另一方块、旧 hasDelayed 残值不能借权。GameMode 关键字段与 ServerPlayer.gameMode 引用接入原字段/Unsafe/内存路径；当前 Item 的委托不授予 GameMode 写权，方法名与实际 descriptor 同时匹配。换工具、状态变化、ABORT、世界交接、release 或异常均撤销续接。既有生命周期许可可供准确策略查询，不扩大来源或授权。

旧 `c-network-java74-native69-equipment-20261014.log` 已退出1：首次发送在 `Inflater.inflateBytesBytes` 读取类文件时失败，完整 cause 为 `EXTERNAL_NATIVE_CALL_SOURCE_UNOBSERVED`、`stage=scoped_sources`，其余两次发送成功。原 JNI 数组副本只请求 owner，误将网络中的未知贡献者当成捕获失败。现保留独立 unknown 标记和原 owner，并在八种 primitive 数组提交中合并获取时及提交时的来源。原准确目标、停组、私有提交快照及 COMMIT/ABORT 配对保持；未清空来源、预热类或放宽规则。Native 升70，配套 Java 桥升75，避免旧载荷混用。

整包写齐后统一构建 `build-c-java75-native70-equipment-actions-20261014.log` **PASS，1分14秒**：Core2580259、prelaunch22440字节，匹配 Agent/Bootstrap/Native70 与现有夹具均已生成。复用 DefenseFixture/ClientDefenseFixture 的 equipment-actions 分支包含真实客户端攻击木镐耗损、START/提前 STOP/真实 tick 延后挖掘、外部回调字段篡改与借权拒绝、普通邻居和 release；**游戏分支 NOTRUN**。AuditFixCheck 对新运行类路径编译通过；原网络场景已用75/70启动，日志 `c-network-java75-native70-equipment-actions-20261014.log`，当前仍运行，未宣称通过。没有重复旧 Native/Host 矩阵。

原 Java72/Native68 禁疗实例 PID112724 本次只读核对仍在，latest.log 最后仍为19:39的 forgeserver 启动行；未出现 WORLD_LOADED/Done/NO_HEAL_PASS，未停或重启。候选 `distribution/candidate-c-java75-native70-equipment-actions-wip-20261014`，源码包 `distribution/Ronova-Pro-C-EquipmentActions-WIP-Java75-Native70-20261014.zip`。驱动/管理器源码未改，原未签名产物沿用且未部署。未知自定义装备、GameMode 覆写及其它未接入动作仍保留缺口；自身外观、武器注册继续暂缓，完整 C/B 未完成。

---

## 2026-10-14：C 接续——当前装备、ItemStack 与 NBT

接续 `24f5dc3`，Java ABI74 / Native ABI69 / Storage3 / Journal6，客户端协议 `r3-client-8`。标准 Player、Mob、ArmorStand 的当前六槽、真实 NonNullList/Arrays$ArrayList/Object[]、ItemStack 与当前 NBT 图接入现有方法、集合、字段、反射、句柄、Unsafe/CAS/相对 bulk 写入规则。按准确身体、代次、当前实际成员和全部共享 owner 判断，旧槽/节点/release 即时失活。EMPTY、全局 Item/Holder 定义、Tag.TYPE 不纳入装备归属；未知自定义槽载体/NBT 实现明确保留 gap，原生命/索引保护继续。

拆分、取出、丢弃入口先拒绝再进入业务：被拒的 split/copyAndClear/remove 返回 EMPTY，直接 Arrays$ArrayList.set 返回旧元素，避免清槽被挡但副本仍发布。15个原版动作入口按真实 actor、手、栈、菜单与连续来源交接本次许可，保留原线程调度及正常/异常回收；空 token 和同名嵌套不能借旧 scope。当前 Item 的准确使用方法只委托本次真实栈，其它回调/来源仍拒。引用宽度取当前 JVM 实值，避免压缩引用误伤邻槽；全参分派带实际 receiver，静态帮助入口用准确定义 Class。修正 bootstrap 转换回传和抽象 setItemSlot 无方法体却被要求转换的安装阻塞，对象拒绝结果处理器先安装。

未知自定义 capability 内部、物品全局定义，以及 Player.attack/handleInteract、GameMode.tick 延后挖掘耐久的正常委托尚未覆盖；这些正常装备修改仍会被拒，不能称所有动作已支持。自身外观与武器注册继续暂缓。

整包写齐后首次统一构建只因两处 final ItemStack 的 Mixin Access 强转失败，保留原日志；改为经 Object 转换后，`build-c-java74-native69-equipment-final-20261014.log` **PASS45秒**，Core2560279、prelaunch22440字节，匹配 Agent/Bootstrap/Native69 和现有夹具已生成。DefenseFixture equipment 分支编译完成，包含真实 Cow 的数量、拆分无副本泄露、NBT/数组/叶值/实际载体写拒、邻居及 release；**装备游戏分支 NOTRUN**。未重复已通过的 Native raw/Host 检查或旧矩阵。

旧网络 `c-network-java72-native69-live-state-20261014.log` 已退出1：三次 outbound、两次收包，首次 promise 为 EXTERNAL_NATIVE_CALL_SOURCE_UNOBSERVED/scoped_sources，其余两次成功。现有 AuditFixCheck 仅补原 Throwable cause；原发送、等待和10秒 deadline 不变。本次匹配版本重跑 `c-network-java74-native69-equipment-20261014.log` 当前仍运行，没有生产网络修复或通过结论。

原 Java72/Native68 禁疗实例 PID112724 已核对仍在，未停或重启；一次线程栈 main RUNNABLE，仍在 Mixin 初始化的类资源读取/来源查询链，尚无 WORLD_LOADED/Done/NO_HEAL_PASS。本包 Native69 和驱动/管理器源码未改，沿用既有未签名系统产物，未部署。候选 `distribution/candidate-c-java74-native69-equipment-wip-20261014`，源码包 `distribution/Ronova-Pro-C-Equipment-WIP-Java74-Native69-20261014.zip`。源码、匹配 JAR 和既有摘要同步原公开仓库；ZIP、原始日志、存档留本地。完整 C/B 仍未完成。

---

## 2026-10-14：C 接续——活跃效果、饥饿数据与底层禁生成

接续 `fd77b99`，Java ABI73 / Native ABI69 / Storage3 / Journal6，客户端协议 `r3-client-8`。本包新增 `LiveStatePolicy`，按准确身体、保护代次及当前真实载体关联 activeEffects、实际效果实例和 FoodData；接入现有方法分派、集合节点/视图、字段、反射、句柄、Unsafe/CAS 及相对 bulk 写入判断。原状态引用不能偷换，旧代次与 release 即时失活，单独禁疗不启用这些保护。

实际方法分派接收三参数入口与对象返回拒绝（null），保留原 void/boolean 和生命返回语义；该新 Core 需要匹配代理，所以 Java 桥统一升73。另修正 Core 安装处遗留的 ABI55 常量，否则即使前版实际桥已72仍会拒绝安装整组生产守卫；不把本次编译当作真实游戏安装已通过。

正常效果 tick、完成食用、FoodData tick 及既有玩家消耗动作按实际原版入口建立许可；本次入口必须持有自己的回执，外部回调和嵌套新入口不能借旧许可，正常与异常退出均恢复。实际 SRG 名来自本地版本映射，保留正常效果减时/到期和原版食物、药水、牛奶、蜂蜜流程。FoodData 防护阻止外来篡改，保留自然消耗，**不等于免饿**。未知效果 Map、损坏链或超预算明确保留缺口，原有生命/索引保护继续；自定义物品流程及完整装备/ItemStack/NBT 防护仍未收口。

区域禁生成接入 PersistentEntitySectionManager 的 addEntity 和 Forge addEntityWithoutEvent 两个真实新准入入口，并按实际目标 manager/ServerLevel 定位世界，避免实体自己的 level 字段把规则引向另一世界。保留 existing=true 存量加载、玩家例外与原截止；外线程查询读取现有规则发布的不可变区域快照，不迭代服务器的可变规则 Map。

整包代码写齐后初次 ABI72 构建26秒 PASS；修正上述实际安装阻塞并统一 ABI73 后，以独立输出目录集中重建 `build-c-java73-native69-live-state-20261014.log`，30秒 PASS，Core/agent/生产载荷及两个现有夹具分支编译完成；网络故障记录的 AuditFixCheck 单独编译通过。effects 的真实到期、外部回调/集合/Unsafe 攻击及邻居/release 分支，底层真实 manager 准入分支均已放入原夹具，**本轮未起第三个游戏实例，实际效果与 FoodData 检查仍 NOTRUN**。未重跑已通过的 Native69 raw 内存或旧矩阵。

上一网络实例 `c-network-java72-native69-20261014.log` 最终退出1：writtenRemaining=0、receivedRemaining=1、failure=null。当前保留原等待及原发送路径，只在同一场景观察真实 promise、flush、read/readComplete 与失败时的对端队列，重跑日志为 `c-network-java72-native69-live-state-20261014.log`；尚无新结果，不能认定丢包已修复。Native68 的原禁疗游戏实例继续运行，尚无进世界或禁疗结果，不由本包构建代称。

匹配候选 `distribution/candidate-c-java73-native69-live-state-wip-20261014`，源码包 `distribution/Ronova-Pro-C-LiveState-WIP-Java73-Native69-20261014.zip`。本包源码、匹配候选与既有摘要同步原公开仓库；原始日志、ZIP 和存档留本地。自身外观、武器注册保持暂缓；完整 C/B 仍未完成。

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

本地候选：`distribution/candidate-c-java52-native49-wip-20261013/ronova-pro-core-0.1.0-stage-b-dev.jar`。源码、直接相关既有检查与简要结果同步Git；候选及原始日志/崩溃文件留在本地，所有旧候选保留。详情见 [实际报告](../validation/b-final-20261007/REPORT.md)。

---

## 2026-10-13：C 接续——Host A/W、直接传输与客户端镜像

继续当前主工程；用户提供的 `ronova-c565e97-b-r10-20261006-source.zip` 只作对照。GNU C23 / GNU C++23、Java 17 保持；Java ABI 49 / Native ABI 48 / Storage ABI 3 / Journal 6。**本工作包代码已写齐并构建通过，C 整体与 B 的实际游戏收口仍未完成。**

- Host：已登记原生映像的普通导入、已解析延迟导入和实际观察到的 GetProcAddress 结果同时接入 CreateProcessA/W，复用原来源、暂停创建、Job 与句柄退役路径。
- 文件：真实 `FileChannelImpl.transferTo0` 同时接入源、目标描述符作用域，同一描述符只登记一次，失败与返回逆序结束。实际运行暴露的描述符登记死锁已修复，五个登记点改用原锁内直接登记，权限与来源条件保留。
- 客户端：有效 Terminal 清当前准确 ID+UUID 镜像及其相机、命中引用；当前 ClientLevel 与其实际底层 manager 阻止同一身份重新发布。保护、作用域和真实换身条件继续生效；仍被 `Minecraft.player` 引用的目标保持未确认。

已取得：完整主包/既有夹具构建通过；描述符修复后的主包重建 26 秒通过；Windows A/W 真实子进程检查、实际原生控制入口检查，以及实际文件到本机 Socket 的数据/长度、失败退出后作用域恢复和后续传输检查通过。相关失败日志保留在既有报告目录，详情见 [实际报告](../validation/b-final-20261007/REPORT.md)。没有重复旧矩阵或再次启动游戏。

客户端本轮只有编译结果，尚无实机清除/低层回填/玩家交接结果；独立 JVM 未覆盖合法退役写者封口后的 transferTo0 拒绝。JNI_OnLoad 首次加载窗口、未观察到的 Host 入口/句柄交接及纯绘制 owner 接线仍未完成，不能据此称完整 C 可用。

本地 WIP 主包：`distribution/candidate-c-native48-wip-20261013/ronova-pro-core-0.1.0-stage-b-dev.jar`。源码与简要结果同步 Git；主包及原始日志留在本地，原 Native 47 候选保留。

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
