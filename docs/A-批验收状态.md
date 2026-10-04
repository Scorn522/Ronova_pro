# Ronova Pro 当前进度与 A 批状态

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

集中编译同时修复定时任务反射声明、重复实体访问器、世界结构恢复方法名和来源守卫中过期的受检异常捕获。Core／Agent／Bootstrap Java 编译、Native DLL 编译和实际安装失败记录在 [本轮实际报告](../validation/a-final-20260928/REPORT.md)；没有新增或运行无关测试，尚未发布新候选或同步 GitHub。完整 B/C 的其余后端仍需继续，不能沿用 A 的通过结果代替它们。

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

本轮集中构建、消费者及实机结果见 [A 收口报告](../validation/a-final-20260928/REPORT.md)，摘要在该报告旁 `evidence-20261001/`。旧候选和旧失败均保留。

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

当前候选交付位置：`distribution/candidate-modwide-a-20261001/`。本轮未重跑客户端和原第三方样本；完整隐藏定义、Native／Host、来源／资源／持久链仍按 B/C 接续。报告及原始证据见 [A 收口报告](../validation/a-final-20260928/REPORT.md)。

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

[旧 A 验收报告](../validation/a-final-20260928/REPORT.md) 保存 build25 的构建、运行结果和产物哈希；它不覆盖上文的新整 Mod 候选。[V1.5 设计](design/Ronova-Pro-设计方案-V1.5.md) 保留后续 B/C/D 目标；此前状态在 history 目录。
