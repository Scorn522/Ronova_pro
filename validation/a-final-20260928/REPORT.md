# Ronova Pro V1.5 A 批收口报告

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

2026-10-05 B Native 查询：b-imagefields-live-0230.log 的 881 秒现场为读租约内重复 Java 来源扫描。已认证、已传入当前 Java 来源的读租约省去这次重复扫描，VM／原生库来源、UNKNOWN、读租约和写入／分配／I/O／追加来源原规则保持。原执行映像及来源查询的全映像扫描改为准确名字桶与隐藏映像链合并；保持发布顺序和原查询范围，仍逐项验证真实 Class／加载器、完整字节码／常量和 VM 版本。没有缓存来源结论。

b-nativeindex-build-48.log 完整构建通过（23 秒），b-nativeindex-controls-48.log 的原实际 Agent 适配、外部反射／MethodHandle 内存入口拒绝、控制对象防改写与实际直接缓冲读取通过。分发为 nativeindex（Java 48／Native 42），b-source-abi48-nativeindex 已使用同版真实 prelaunch 启动。gatepolicy／bufferlayout 旧来源实例已核对进程后停止，现场保留；其他原场景继续。尚无 B 游戏效果 PASS，B 未完成。仅公开同步代码、候选与摘要，原始日志在本地 .work。

2026-10-05 B 执行入口：b-readleaf-live-0217.log 的 1040 秒现场为 ZIP 属性扫描中的内存写入收尾。执行来源各入口统一使用已有直接调用者核对；自身 nest 的内部委托保留原扫描并认证实际 CodeSourceBridge。b-entrycaller-build-48.log 完整构建通过（24 秒），原夹具新增的外部反射／MethodHandle 内存入口拒绝与既有控制保护、适配和实际缓冲传输在 b-entrycaller-controls-48.log 中通过。

分发候选为 entrycaller（Java 48／Native 42），b-source-abi48-entrycaller 已使用同版真实 prelaunch 启动。原运行保留继续，尚无 B 游戏效果 PASS，B 未完成。原始现场仅在本地 .work，公开同步代码、候选及结果摘要。

2026-10-05 B 叶级读取：独立堆 getter 包装的来源回执没有消费方，已完整去掉 Java Unsafe／句柄和 Native 的这层空读门／回调。实际执行、复制和缓冲读取的观察、原始地址租约及所有写入拒绝保持。b-readleaf-build-final-48.log 完整构建通过（26 秒）；b-readleaf-controls-48.log 的既有实际 Agent、适配、控制对象外部改写拒绝及堆到直接缓冲传输检查通过。

随后 b-readleaf-live-0 至 5 的真实启动现场中，3 次落在 ASM 控制图字段捕获。原生扫描是旧堆读取自我观察的接续措施；该卡点修补后，已受保护的原 Field 列表恢复直接读取实际 ASM 节点，保留入口认证、声明类／类型／实际 holder 核对和完整控制闭包。b-imagefields-build-48.log 完整构建通过（24 秒），b-imagefields-controls-48.log 原实际 Agent 适配、缓冲传输与控制对象外部改写拒绝检查通过。

当前分发候选为 imagefields（Java 48／Native 42）。b-source-abi48-imagefields 已用真实 prelaunch 启动，readleaf 与旧场景继续，无游戏效果 PASS，B 未完成。公开同步源码、候选与摘要，原始日志保留本地 .work。

2026-10-05 B 缓冲布局：gatepolicy 来源实例 455 秒现场为 ZIP 中央目录读入，内部缓冲跨度的 Field.getInt 重入 Unsafe 读取门。当前缓冲位置／限额／地址／容量、backing／父视图及 cleanup 地址读回已接到现有准确 Native 字段读取，认证真实 ResourceBridge nest、核对实际 holder／声明字段／类型、保留原反射后备。未缓存读取值，数据来源与资源退役边界保持。首次构建的异常类型编译失败保留，释放读回未观察仍报告具体 gap；b-bufferlayout-build-final-48.log 完整构建通过（25 秒）。

b-bufferlayout-controls-48.log 原实际 Agent 适配及外部改写拒绝检查通过，既有分支内的实际堆→直接缓冲传输、共享只读视图及源字节保留检查通过。候选更新为 bufferlayout（Java 48／Native 42），b-source-abi48-bufferlayout 用独立端口真实 prelaunch 运行，其他五个 gatepolicy 游戏场景继续保留，尚无 B 游戏效果结果。公开同步源码／候选／摘要，原始日志仅在本地 .work。B 未完成。

2026-10-05 B 停用锁策略：readlease 来源实例 471 秒现场为准确字段门 CAS 的控制查询。gate／真实同步器的业务来源、停用和 backing 检查可能拒绝已停用模块退场过程中的控制器锁操作；现只对这两个已登记准确身份提前使用原 JDK 锁调用／直接控制者认证及控制 writer 权限，字段、反射和标量 Unsafe 路径一并接续，其他对象与批量写策略保留。b-gatepolicy-build-48.log 完整构建通过（25 秒）；b-gatepolicy-controls-48.log 的既有实际 Agent 安装、同步器外部反射／Unsafe 改写拒绝、内部堆表删除拒绝与适配检查通过。

候选更新为 gatepolicy（Java ABI 48／Native 42）。mod-group-dynamic-b48-gatepolicy、b-source-abi48-gatepolicy、b-record-abi48-gatepolicy、b-chain-abi48-gatepolicy、b-task-abi48-gatepolicy 已用同版真实 prelaunch 启动，尚无游戏效果结果。原 read／readlease 实例停止并保留本地 .work 现场；公开仓库仅同步源码、候选和摘要。保存链需先有生产 PASS，再用同一存档／夹具验证原意图重启，不能预先计为通过。B 未完成。

2026-10-05 B 内部读门：read 来源实例 691 秒现场确认内部 Unsafe 包装仍为签名读取建立未使用的来源窗口。包装回执仅用于关闭，现保留真实 receiver 读门，嵌套同线程读取沿已持有的同一门；完整来源窗口仍由实际代码执行、复制和缓冲区读取消费，原始地址读租约保留。Buffer.scope 独立资源边界未改。b-readlease-build-48.log 完整构建通过（24 秒），b-readlease-controls-48.log 的原实际 Agent 适配及控制对象外部反射／Unsafe 改写拒绝检查通过。候选更新为 readlease，Java ABI 48／Native 42 保持。来源场景 b-source-abi48-readlease 使用同版真实 prelaunch 运行，其他 read 场景继续；尚无 B 游戏效果 PASS。原始日志保留本地 .work，仅公开同步源码、候选及摘要。

2026-10-05 00:44：B access 来源／引用实例的 2444／2202 秒实际现场在 JAR 扫描及签名的 Unsafe 读取观察中持续运行，未进入游戏效果。已完整修复准确内部同步器的多余来源捕获，以及堆读取回执不使用的 Java／Native 调用栈来源捕获；内存读取窗口、区间来源、并发写入观察及原外部写入拒绝保留，原始地址读取仍捕获调用来源。NativeControl ABI 42 对应同版 JNI 回调，Java ABI 48 保持。

b-read-build-48.log 完整构建通过（31 秒）；b-read-controls-48.log 的既有实际 Agent 适配／外部反射及 Unsafe 改写拒绝检查通过。候选更新为 read 包，来源场景 b-source-abi48-read 使用真实 prelaunch 运行；旧来源实例停止，其他四个 access 场景仍无效果结果。原始日志／线程现场在本地 .work，公开仓库仅同步代码、候选与本摘要。尚无 B 游戏内 PASS，B 未完成。

2026-10-04 23:58：gates 包整组实例越过原堆表／字段门锁循环，1156 秒现场仍在实际 JAR 签名类加载中反复进行内部 trySetAccessible。内部控制 backing 已走同一认证原生字段读取，现原生分支省去该反射可访问检查；反射后备仍执行原检查。b-access-build-48.log 构建通过（29 秒），b-access-controls-48.log 的既有 B 适配器检查通过，原权限及拒绝事实保持。候选更新为 access 包，来源场景以该包重新运行；其余四个 B 场景仍在 gates 包下启动。未取得 B 游戏内 PASS，未将进程运行／安装成功计为完成。

本轮原始日志和线程现场保留在本地 .work。公开仓库仅同步源码、对应候选及检查结果摘要；自动审批未允许公开同步原始运行元数据。

2026-10-04 23:38：b-group-48-byte-installed-threads.log 记录实际来源堆表与字段门锁等待。内部构造登记 Map 的准确 writer 判定不再嵌套业务字段门；实际字段门和同步器在发布前保护，排除这两个控制身份的业务来源观察。真实 native 回调中的 JDK 加锁按当前物理调用链认证，外部反射／Unsafe 改写仍拒绝。b-gates-build-48.log（24 秒）及 b-gates-build-final-48.log（22 秒）构建通过；b-gates-controls-pass-48.log 为同一既有 B 适配器检查通过，包含同步器状态改写拒绝及来源堆表防删。初次回调拒绝、修正前检查拒绝异常及现场均保留。候选为 gates 包，整组与来源场景仍在实际运行，尚无新 B 游戏内 PASS。

2026-10-04 22:52：b-group-48-query-threads.log 的 289 秒现场确认内部映像比较通过 ArraysSupport.vectorizedMismatch 再次触发 Unsafe 来源观察。当前 Agent 内部 byte[] 映像比较统一使用 ControlImages.sameBytes；恢复用内部字节快照也逐字节比较，保留 null／长度／全部内容与实际快照范围要求。package-b-byte-48.log 与 package-b-byte-48-final.log 均构建通过（24 秒）。候选为 byte 包，整组和来源场景使用该包继续真实 prelaunch；203 秒现场已经走准确原生字段读取，未取得任何新 B 游戏内 PASS。

2026-10-04 22:43：b-group-48-frame-late-threads.log 的 307 秒现场确认内部 CallQuery.hashCode 经 Record ObjectMethods 的字段句柄进入 Unsafe 来源观察。CallQuery／MemberQuery／SelectionQuery、嵌套 Member 和 Receiver 的等价及哈希改用相同组件的直接字段读取。package-b-query-48.log 构建通过（22 秒），候选更新；原整组与来源场景切至 query 包继续运行，尚未取得游戏效果结果。没有据此增加 B 通过项。

2026-10-04 22:36 B 前置入口后续：旧来源实例 800 秒现场仍为 ASM Field.get → Unsafe → 来源读取登记；缓存 MethodHandle 后的实际线程同样经过 Unsafe，因此撤回该尝试。最终将 ASM 引用字段与内部控制容器 backing 捕获接到已有 native heapReadField，保留 Agent／控制调用者核对及实际字段读取，未取消业务来源观察。补包第一次因局部括号编译失败，修正后统一构建通过（23 秒）；b-control-read-48.log 的实际 Agent、Netty、来源根／控制表／字段门数组拒绝外部写入及原适配器检查通过。

随后实际现场推进至 JRT 层级读取；ControlClassWriter 的 bootstrap 分支补用现有 initiated 查询，命中真实 Class 后直接读层级，未命中仍走原资源路径。最终 package-b-frame-48.log 构建通过（23 秒），候选对应该包；整组和来源两份 frame 实例正在运行，没有游戏效果 PASS。所有较旧实例均为定位到具体热点后切包停止，不记作通过。保存链生产／同存档重启、引用清理及完整任务结清仍待运行结果。

2026-10-04 晚间 B 收尾（未通过）：此前 indexed 引用、保存链及来源场景最终均因 Netty void write 方法插入 ARETURN 而触发 VerifyError；没有夹具效果结果，SERVER_EXIT=0 不表示通过。本批已修正 RETURN，统一构建通过（23 秒），实际 Agent 下以 -Xverify:all 加载 Netty 类通过。既有 b-adapter-linkage 同时确认真实字段门根／桶数组拒绝外部写入，原来源目录／控制表和对象适配器、字符流入口保持通过，见 evidence-20261002/b-startup-netty-controls-48.log。

安装现场确认并修补两处前置入口问题：控制类镜像上的合法 Class.reflectionData CAS 被拒导致 JDK 重试；内部字节快照的 Arrays.equals 优化读取再次进入 Unsafe 观察。现仅放行真实 JDK 调用链及准确 reflectionData 槽，并改用内部逐字节比较。控制表直接摘除队列节点、字段门分桶弱身份查找、资源布局及 ASM 字段缓存一并编入，保留实际身份、锁和来源判断。隐藏类仅因声明模块重复项发生的来源差异不再要求重转换，真正外部来源改变仍保留未决。

候选已更新，真实 prelaunch 的 mod-group-dynamic-b48-startup-final 与 b-source-abi48-startup-final 正在运行，当前不计 B 效果通过。保存链生产／原意图重启、引用清理及任务完整结清仍待实际结果。旧失败与线程现场保留，不重复已通过且未受影响的 Journal 检查。

2026-10-04 后续启动热点修补：b-record-48-unknown 实际越过原 SensorType 失败点、Forge 初始化和方块缓存阶段，进入世界资源加载。为保留进度曾仅终止限时启动器，游戏保持运行；随后取得真实热点采样并完成针对性修补，停止旧游戏切换新包。此轮未到达夹具效果，不记通过。保留日志 b-record-48-unknown-startup.log 及 after-source-fix／config 两份线程现场。采样 112 个主线程样本中，executionPlan0 为 63，ControlRegistry.reap 为 33。隐藏类映像改为各自真实绑定链，堆目录只登记实际插入的弱键，短原型数组快照按实际 bits 相等复用；原来源、UNKNOWN、并发和 VM 验证保持。统一编译 15 秒、打包 11 秒通过，日志 build-b-hotspots-48.log、package-b-hotspots-48.log。原 SourceModuleFixture 的释放动作改用已清除身体的原操作编号，并检查实际撤销状态；该场景仅编译打包，尚未执行。当前 b-record-abi48-indexed 与 b-chain-abi48-indexed-production 分别运行，B 尚未完成。

启动失败定位补充：第三轮（b-record-48-memory-source-failure.log）确认实际加载的是新构建 DLL，仍以不含逐帧诊断的同一来源捕获错误退出。源码复核发现真实 Io scope 的 null 未知来源标记被 native_capture_network_sources 当作 Owner 分配失败；已修补已知来源与未知状态的独立传递，并沿现有资源／内存路径保留未知状态，未将未知归属放宽为独占。修补及最终失败阶段定位已统一构建通过（14 秒，package-b-native-unknown-48.log），当前候选同步更新；原引用场景正在运行，游戏效果尚未通过。

2026-10-04 B 实际运行收口（进行中）：Journal V5 → V6 迁移、大于 1 MB 的逻辑记录持久 ACK／重新打开／前置读取器读回、UTF-16 保真及截断尾帧拒绝确认已在真实日志文件上通过，记录为 evidence-20261002/b-journal-6.log。真实 Agent 下来源目录根数组与控制表根数组外部改写被拒绝，原对象适配器和字符流调用检查通过，记录为 b-control-arrays-48.log。

启动实际失败仍保留：2 GB JVM 的存活堆约 20.85 亿字节；改用 6 GB 后，引用与保存链两轮均在原版 SensorType 初始化期间因 EXTERNAL_NATIVE_CALL_SOURCE_UNOBSERVED 退出，未进入游戏效果阶段。原日志为 b-record-48-native-source-failure.log、b-chain-48-native-source-failure.log。启动包已修复文件委托字段重复查找、缓冲操作临时列表、来源目录树与控制数组查询开销；后续去除执行定义的重复完整指令树，改存精确声明表，保留完整字节、原语义图和实际版本核对。统一打包通过（15 秒，package-b-startup-memory-48.log），新一轮正在定位具体来源帧，B 未通过。

既有 ChainFixture 同时修正了成功防御的误判：原型已清掉时不再对 null 执行克隆；克隆入口或加入世界被拒绝时记录实际结果；确实加入世界的克隆必须消失，稳定阶段再次核对。仍要求真实内存清理、邻居保留、磁盘读回和原意图重启；未执行的克隆路径不再宣称 UUID 变更复活已验证。此修改仅编译通过（10 秒），尚未运行。没有新增独立探针或无关测试。

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

字段写入插桩体积／方法头读取／bootstrap 候选索引三类完整包已统一构建通过，15 秒；Core／Agent／Bootstrap 40、Native 38 复用，持久格式未变。第五十三次按上述原启动超时结果结束。每个方法复用一组临时槽，同一原 catch／finally 覆盖顺序的写入共用异常收尾；实际写入留在原类、原方法中，逐次许可、正常／失败发布和门释放保留，异常收尾按原顺序回到原处理器。Mixin 方法头直接复制原头部字段，不再先复制整段方法体后清空；实际当前类读取与帧比较不变。每张图的 bootstrap 名称集合仍重新取得并按实际定义加载器／非隐藏类筛选，私有桶索引按准确全名查找、保护新索引，未缓存跨图许可。第五十二次实际报 SoundEvents.<clinit> 的 FieldWriteBoundary MethodTooLargeException，原 360 秒超时，脚本结束自建 JVM；144 秒现场为 Mixin 方法头读取的整段方法复制，未报告死锁，后期请求时 JVM 已退出，没有后期线程文件。该轮没有前次栈帧越界输出，也没有游戏内结果；B/C、O02／O05 未完成，没有新候选或 GitHub 同步。

直接方法头复制／来源图分批接续八类完整包已统一构建通过，22 秒；Core／Agent／Bootstrap ABI 41，Native 38 复用，持久格式未变。第五十四次按上述原启动超时结果结束。MethodNode 的原头部字段依本机 JDK 17 实际定义直接赋值，内容与原重放相同，避免该读取的反射写入回调；当前类、原帧和元数据比较保留。各已登记来源图在原 256 单位预算内轮转，数组／列表每块至多处理 32 个元素，游标及暂停的父容器保留，已发现子对象先处理，满队列不会丢掉父容器的未处理位置。数组写入票据只覆盖本块实际区间，原历史及读回保留；在同一实际写门中读取原数组变更记录，变化时重回起始位置，收尾后保存真实修订。ArrayList 读取同一实际门的修订，CopyOnWriteArrayList 使用真实 backing 身份，组外变化后重新处理原列表。反射或资源失败继续保留未决，不据分批代码声明 O02／O05、全部容量或 B/C 完成。第五十三次原 360 秒超时，脚本结束自建 JVM；86 秒现场为 Forge 文件读取中的缓冲来源捕获，331 秒现场为方法头的反射字段赋值／调用来源捕获，均为 RUNNABLE，未报告死锁。没有前次方法大小错误输出，但没有到该故障的效果复查，没有游戏内结果、新候选或 GitHub 同步。

当前字节对应冻结元数据／小数组区间槽查询两类完整包已统一构建通过，16 秒，第五十五次按上述 Blocks 字段插桩失败与原超时结果结束。每次元数据查询仍重新序列化当前实际树并保护新字节；只有全字节与该历史 current 的受保护快照完全相同，且实际方法／指令 opcode／异常处理器布局按原规则核对后，才使用该快照自己的冻结行。字节变化继续完整解码、保护新图和重新构造元数据，历史 before／after 及来源比较不变，不复用跨查询许可。实际数组区间比既有槽表小时按该区间的准确键查询，否则保留原槽表筛选；本次新鲜来源、原槽身份与写入历史不变，没有遍历无关槽。第五十四次原 360 秒超时，脚本结束自建 JVM；161 秒现场为类文件读取中 BufferOperation 的内部集合／控制登记，326 秒现场已在世界注册初始化的 Mixin 应用中，为元数据图保护，均为 RUNNABLE，未报告死锁，没有游戏内结果。前次字段大小错误尚未取得效果复查，B/C、O02／O05 未完成，没有新候选或 GitHub 同步。Core／Agent／Bootstrap 41、Native 38 与持久格式未变。

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

## 2026-10-03 B/C 堆记录 Map 回入死锁

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-execution-map-scope-deadlock.log` | PASS：四类实际回入修补包／开发包统一构建成功，17 秒；Core／Agent／Bootstrap 39、Native 37 复用。 |
| `array-runtime-40.log`、`array-runtime-40-launch.log`、`array-runtime-40-launch.json`、`array-runtime-40-threads.log` | FAIL：132 秒完整现场在 Forge 的真实解压数组观察中，为 RUNNABLE；后续 MixinInfo 转换报告 MISSING_FRAME_TYPE:org/objectweb/asm/tree/ClassNode，原场景最终超时，脚本结束自建 JVM。后期请求时 JVM 已退出，没有后期线程文件或游戏内结果。 |
| `build-frame-types-role-read.log` | PASS：实际栈帧类型接续与内部只读查询整包／开发包统一构建成功，16 秒；Native 37 复用。 |
| `array-runtime-41.log`、`array-runtime-41-launch.log`、`array-runtime-41-launch.json`、`array-runtime-41-threads.log` | FAIL：168 秒完整现场在真实文件读入中的控制目录死亡键回收，为 RUNNABLE；最终日志已到 Launching target forgeserver，仍在原时限内未到游戏内动作，脚本结束自建 JVM。后期请求时 JVM 已退出，没有后期线程文件。 |
| `build-control-registry-batch-caller.log` | PASS：控制目录登记／回收整包与开发包统一构建成功，15 秒；Native 37 复用。 |
| `array-runtime-42.log`、`array-runtime-42-launch.log`、`array-runtime-42-launch.json`、`array-runtime-42-threads.log`、`array-runtime-42-threads-late.log` | FAIL：原场景 360 秒时限内未到游戏内动作，脚本结束自建 JVM。73 秒完整现场在真实文件读取的来源捕获；311 秒完整现场在 Mixin 成员元数据快照的对象图保护，均为 RUNNABLE，未报告死锁；没有游戏内攻防结果。 |
| `build-snapshot-graph-empty-frame-package.log` | PASS：两类快照联合图与空帧查询完整包／开发包统一构建成功，17 秒；Native 37 复用。 |
| `array-runtime-43.log`、`array-runtime-43-launch.log`、`array-runtime-43-launch.json` | FAIL：SERVER_EXIT=1，premain 日志仅留下 InvocationTargetException 与 libinstrument 断言；现场请求时自建 JVM 已退出，没有线程文件，未到快照修补点或游戏内动作。 |
| `build-install-original-throw-site.log` | PASS：现有安装失败诊断补充／开发包统一构建成功，15 秒；DLL SHA-256 `BBD4D042A5CF4D6EFAC8748A01CD5812C9E243FF483F882574217850504D2156`，Native ABI 37。 |
| `array-runtime-44.log`、`array-runtime-44-launch.log`、`array-runtime-44-launch.json` | FAIL：SERVER_EXIT=1，premain 在反射字段守卫安装后提前失败；没有抛出事件诊断输出，Java 失败打印未留下原异常类型。现场请求时自建 JVM 已退出，没有线程文件或游戏内结果。 |
| `build-original-describe-cause-chain.log` | PASS：原 JNI 失败打印前异常链诊断／开发包统一构建成功，15 秒；DLL SHA-256 `968DB7DCDD41794C8917C76855C863868C6077FC25F252C85D3BCA872E63A540`，Native ABI 37。 |
| `array-runtime-45.log`、`array-runtime-45-launch.log`、`array-runtime-45-launch.json` | FAIL：SERVER_EXIT=1，原 JNI 失败打印前已读到 InvocationTargetException → StackOverflowError 的实际异常链；尚无该 Throwable 保存的栈，没有线程文件或游戏内结果。 |
| `build-original-throwable-saved-frames.log` | PASS：真实 Throwable 已保存栈读取／开发包统一构建成功，16 秒；DLL SHA-256 `69C0AE44B7C32941604159D5AB5DA843CA3B77E56D25B011FB0C2CDECA919FBA`，Native ABI 37。 |
| `array-runtime-46.log`、`array-runtime-46-launch.log`、`array-runtime-46-launch.json` | FAIL：SERVER_EXIT=1，取得实际反射包装的 6／6 帧及 StackOverflowError 的 542／542 已保存帧。递归为 actualMemoryCaller 首次 lambda 链接 → MethodHandleNatives.findMethodHandleType → MethodType.makeImpl／ConcurrentWeakInternSet.get → ConcurrentHashMap.get／tabAt → Unsafe.getReferenceAcquire → beginHandleRead → actualMemoryCaller。未到游戏内动作。 |
| `build-memory-caller-linkage-recursion.log` | PASS：内存／原生／Core／安装调用者与来源、执行桥查询整包统一构建成功，15 秒；Native 37 复用。 |
| `array-runtime-47.log`、`array-runtime-47-launch.log`、`array-runtime-47-launch.json`、`array-runtime-47-threads.log`、`array-runtime-47-threads-late.log` | FAIL：原场景 360 秒时限内未到游戏内动作，脚本结束自建 JVM。143 秒现场为内部记录写入的原生库调用者查询；383 秒现场位于原停止宽限内，已在 Minecraft 世界注册初始化的真实文件路径／数组观察中，均为 RUNNABLE，未报告死锁；没有游戏内攻防结果。 |
| `build-control-writer-library-query.log` | PASS：控制写入／原生库调用者查询完整包统一构建成功，15 秒；Core／Agent／Bootstrap 39、Native 37 与持久格式不变。 |
| `array-runtime-48.log`、`array-runtime-48-launch.log`、`array-runtime-48-launch.json` | FAIL：SERVER_EXIT=1，premain 栈溢出，实际 Throwable 保存的 1024／1024 帧显示 unsafeControlScope0 首次原生函数解析 → NativeLibraries.find／ConcurrentHashMap 迭代 → Unsafe.getReferenceAcquire → beginHandleRead／unsafeControlScope0 递归。现场请求时 JVM 已退出，没有线程文件或游戏内结果。 |
| `build-controller-native-entry-binding.log` | PASS：全部 75 个实际控制原生入口提前绑定完整包／开发包统一构建成功，16 秒；DLL SHA-256 `00E78DC81D70C1A6C1667F4D70D8E87DD75B233509B5A16DE119B1DEC1EA411E`，Native ABI 37。 |
| `array-runtime-49.log`、`array-runtime-49-launch.log`、`array-runtime-49-launch.json`、`array-runtime-49-threads.log`、`array-runtime-49-threads-late.log` | FAIL：原场景 360 秒时限内未到游戏内动作，脚本结束自建 JVM。77 秒现场为调用图接收者合并的 HashSet 构造／SourceMap 登记；323 秒现场为 Mixin 成员元数据查询的完整类快照构造，均为 RUNNABLE，未报告死锁，没有游戏内结果。 |
| `build-flow-metadata-allocation-package.log` | PASS：三类调用图集合／Mixin 元数据完整包与开发包统一构建成功，14 秒；Native 37 DLL 复用，Core／Agent／Bootstrap 39 与持久格式不变。 |
| `array-runtime-50.log`、`array-runtime-50-launch.log`、`array-runtime-50-launch.json`、`array-runtime-50-threads.log`、`array-runtime-50-threads-late.log` | FAIL：原场景 360 秒时限内未到游戏内动作，脚本结束自建 JVM。89 秒现场为 Forge 扫描中的原文件读入；313 秒现场已在 Minecraft 世界注册初始化的 Mixin 应用中，为成员创建的调用来源捕获，均为 RUNNABLE，未报告死锁，没有游戏内结果。 |
| `build-metadata-frozen-baseline-capture.log` | PASS：历史元数据行／无发布成员查询完整两类包统一构建成功，16 秒；Native 37 DLL 复用，Core／Agent／Bootstrap 39 与持久格式不变。 |
| `array-runtime-51.log`、`array-runtime-51-launch.log`、`array-runtime-51-launch.json`、`array-runtime-51-threads.log`、`array-runtime-51-threads-late.log` | FAIL：原 360 秒超时，停止宽限中实际报 PlayerLifecycleHooks$DimensionChange 的 PostApply 快照读取 ArrayIndexOutOfBoundsException（403／212）；main 退出后后台仍运行，脚本结束自建 JVM。100 秒现场为全 VM bootstrap 查询；364 秒现场处于原停止宽限，后台为候选表构造，含 DestroyJavaVM，未报告死锁，没有游戏内结果。 |
| `build-bootstrap-lookup-expanded-frames.log` | PASS：实际 bootstrap 名称查询／原钩子帧格式整包与原生／开发载荷统一构建成功，26 秒；Core／Agent／Bootstrap 40、Native 38，持久格式未变；DLL SHA-256 `8EE7BCD9D92302F956102FDE231B8E0BE02A60DDB3AF5D078A52AA2DE120ED2D`。 |
| `array-runtime-52.log`、`array-runtime-52-launch.log`、`array-runtime-52-launch.json`、`array-runtime-52-threads.log` | FAIL：实际报 SoundEvents.<clinit> 的 FieldWriteBoundary MethodTooLargeException，原 360 秒超时，脚本结束自建 JVM。144 秒现场为 Mixin 方法头读取的整段方法复制，未报告死锁；后期请求时 JVM 已退出，没有后期线程文件、前次帧越界输出或游戏内结果。 |
| `build-field-write-shared-cleanup-metadata-index.log` | PASS：三类字段插桩共享收尾／方法头／候选索引完整包与开发载荷统一构建成功，15 秒；Core／Agent／Bootstrap 40、Native 38 复用，持久格式不变。 |
| `array-runtime-53.log`、`array-runtime-53-launch.log`、`array-runtime-53-launch.json`、`array-runtime-53-threads.log`、`array-runtime-53-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。86 秒现场为 Forge 文件读取中的缓冲来源捕获，331 秒现场为方法头的反射字段赋值／调用来源捕获，均为 RUNNABLE，未报告死锁；尚未到前次字段大小错误的效果复查，没有游戏内结果。 |
| `build-metadata-direct-source-resumption-package.log` | PASS：方法头直接复制／B 来源图轮转与数组、列表分批接续八类完整包与开发载荷统一构建成功，22 秒；Core／Agent／Bootstrap 41、Native 38 复用，持久格式不变。 |
| `array-runtime-54.log`、`array-runtime-54-launch.log`、`array-runtime-54-launch.json`、`array-runtime-54-threads.log`、`array-runtime-54-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。161 秒现场为类文件读取中 BufferOperation 的内部集合／控制登记；326 秒现场已在世界注册初始化的 Mixin 应用中，为元数据图保护，均为 RUNNABLE，未报告死锁；没有游戏内结果。 |
| `build-current-image-metadata-range-slots.log` | PASS：当前字节对应冻结元数据／实际小数组区间槽查询两类完整包与开发载荷统一构建成功，16 秒；Core／Agent／Bootstrap 41、Native 38 复用，持久格式不变。 |
| `array-runtime-55.log`、`array-runtime-55-launch.log`、`array-runtime-55-launch.json`、`array-runtime-55-threads.log`、`array-runtime-55-threads-late.log` | FAIL：实际报 Blocks.<clinit> 的 FieldWriteBoundary MethodTooLargeException，原 360 秒超时，脚本结束自建 JVM。165 秒现场为类文件读取的缓冲来源捕获；329 秒现场已到火焰方块注册，为工厂类型查询的真实资源读取，均为 RUNNABLE，未报告死锁，没有游戏内结果。 |
| `build-static-field-compact-factory-buffer-package.log` | PASS：静态字段紧凑显式票据／实际工厂层级／缓冲空来源记录六类完整包与开发载荷统一构建成功，26 秒；Core／Agent／Bootstrap 42、Native 38 复用，持久格式未变。 |
| `array-runtime-56.log`、`array-runtime-56-launch.log`、`array-runtime-56-launch.json`、`array-runtime-56-threads.log`、`array-runtime-56-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。67 秒现场为早期模块资源读入中的缓冲 hb 查找，300 秒现场已在火焰方块注册的真实类文件读入，均为 RUNNABLE，未报告死锁；没有方法过大错误输出，但尚无该插桩路径的效果复查或游戏内结果。 |
| `build-buffer-layout-group-map-resumption.log` | PASS：缓冲实际 hb 字段布局／B 共享 Map 条目遍历接续两类完整包与开发载荷统一构建成功，24 秒；Core／Agent／Bootstrap 42、Native 38 复用，持久格式未变。 |
| `array-runtime-57.log`、`array-runtime-57-launch.log`、`array-runtime-57-launch.json`、`array-runtime-57-threads.log`、`array-runtime-57-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。78 秒现场为 Forge 类资源读入中的缓冲平台调用者查询；307 秒现场已到火焰方块注册，为工厂类型资源读取中缓冲 limit 的来源捕获，均为 RUNNABLE，未报告死锁；没有方法过大错误输出，但仍无该路径的效果复查或游戏内结果。 |
| `build-resource-caller-bootstrap-array-index.log` | PASS：资源实际调用者查询／普通 bootstrap 候选数组索引完整包与开发载荷统一构建成功，18 秒；该包的 Core／Agent／Bootstrap 42、Native 38 与持久格式未变，并入后续完整 B 容器包集中检查。 |
| `build-source-map-holder-capacity-package.log` | PASS：B 真实 Map／Set 接管、节点遍历和解除、持有者图接续完整六类包及开发载荷统一构建成功，25 秒；Core／Agent／Bootstrap 43、Native 38 复用，持久格式未变，包含前述资源／索引修补。 |
| `array-runtime-59.log`、`array-runtime-59-launch.log`、`array-runtime-59-launch.json`、`array-runtime-59-threads.log`、`array-runtime-59-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。232 秒现场为注册初始化的 Mixin 快照保护／控制目录扩容；323 秒现场仍在注册初始化，为工厂类型真实资源读取，均为 RUNNABLE，未报告死锁；最终没有新转换错误输出或游戏内结果。 |
| `build-buffer-state-map-adjacency-package.log` | PASS：未消费的缓冲状态来源查询／真实 HashMap 前驱接续两类完整包与开发载荷统一构建成功，23 秒；Core／Agent／Bootstrap 43、Native 38 复用，持久格式未变。 |
| `array-runtime-60.log`、`array-runtime-60-launch.log`、`array-runtime-60-launch.json`、`array-runtime-60-threads.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。175 秒现场为正常 CrashReport.preload 的资源读入／代码来源桥死亡弱键清理中的通用 Map 守卫查询，RUNNABLE，未报告死锁；缓存源码已核对 Main:116／CrashReport:214–216，未作异常结论。后期请求时 JVM 已退出，没有后期线程文件；最终没有新转换错误输出或游戏内结果。 |
| `build-code-source-private-ledger-package.log` | PASS：代码来源桥私有 LedgerMap 完整两类包及开发载荷统一构建成功，18 秒；Core／Agent／Bootstrap 43、Native 38 复用，持久格式未变。 |
| `array-runtime-61.log`、`array-runtime-61-launch.log`、`array-runtime-61-launch.json`、`array-runtime-61-threads.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。唯一线程现场 323 秒，为工厂父链资源读取中的缓冲写入记录／通用控制调用者查询，主线程 RUNNABLE，未报告死锁；未取得 70 秒或单独后期文件，最终没有新转换错误输出或游戏内结果。 |
| `build-reference-record-map-resumption-package.log` | PASS：B 引用／内存记录大容器接续及原控制调用者查询三类完整包和开发载荷统一构建成功，25 秒；Core／Agent／Bootstrap 43、Native 38 复用，持久格式未变。 |
| `array-runtime-62.log`、`array-runtime-62-launch.log`、`array-runtime-62-launch.json`、`array-runtime-62-threads.log`、`array-runtime-62-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。92 秒现场在类加载代码图构造／定义控制对象查询，343 秒现场到 FireBlock 注册初始化的真实类文件解压读入，均 RUNNABLE，未报告死锁；没有新的转换错误输出或游戏内结果。 |
| `build-accepted-source-event-resumption-package.log` | PASS：B 已接受加载事件及组对象接续／定义控制对象类型筛选三类完整包及开发载荷统一构建成功，19 秒；Core／Agent／Bootstrap 43、Native 38 复用，持久格式未变。 |
| `array-runtime-63.log`、`array-runtime-63-launch.log`、`array-runtime-63-launch.json`、`array-runtime-63-threads.log`、`array-runtime-63-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。90 秒现场为 Mod 发现／类资源读取中的 IoBridge 临时直接缓冲平台调用者查询；324 秒现场到 Forge 标签注册初始化的真实类文件解压读取，均 RUNNABLE，未报告死锁；最终没有新的转换错误输出或游戏内结果，启动超时未解决。 |
| `build-astra-flow-images-full-capture-package.log` | PASS：Astra max 的 Agent 返回依赖图／摘要队列／图像匹配完整修复与 B 全部实际捕获字段、委托对象、CF 输入及 NBT 图核对八类完整包统一构建成功，21 秒；Core／Agent／Bootstrap 43、Native 38 复用，持久格式未变。 |
| `array-runtime-64.log`、`array-runtime-64-launch.log`、`array-runtime-64-launch.json`、`array-runtime-64-threads.log`、`array-runtime-64-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。86 秒现场为 Nashorn 代码生成／加载定义中的 Native 代码版本查询；349 秒已到 CreationBoundary 的实体父链真实类资源解压读取，均 RUNNABLE，未报告死锁；最终没有新的转换错误输出或游戏内结果，启动超时未解决。 |
| `build-astra-resolution-saveddata-resumption-package.log` | PASS：Astra max 的本次完整解析事务／NEW 构造父链及 B SavedData 当前缓存原意图接续／完整保存身份核对五类完整包统一构建成功，23 秒；Core／Agent／Bootstrap 43、Native 38 复用，持久格式未变。 |
| `array-runtime-65.log`、`array-runtime-65-launch.log`、`array-runtime-65-launch.json`、`array-runtime-65-threads.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。唯一保存的 93 秒现场为 Nashorn 代码生成／真实类加载中的 DefinitionBridge 缓冲图像复制，经 ByteBuffer limit 收尾进行 ResourceBridge 平台调用者查询，RUNNABLE，未报告死锁；没有保存后期线程文件。最终没有新的转换错误输出或游戏内结果，启动超时未解决。 |
| `build-native-version-index-full-registration-package.log` | PASS：Astra max 原生实际图像／当前方法常量与 UTF Symbol 比对工作包链接成功；Native ABI 38，控制 DLL SHA-256 为 `0D10F2C99234901856DB676B2364C7058B9A504EAB1AD021C9161587BED3A69E`，新 DLL 纳入本包 Bootstrap 载荷。 |
| `build-native-full-source-task-registration-package.log` | PASS：Native 完整包与 B 来源／异步实际登记、同名不同 loader 真实内存定义四文件工作包统一构建成功，21 秒；Core／Agent／Bootstrap 43、Native ABI 38，Java 契约与持久格式未变。 |
| `array-runtime-66.log`、`array-runtime-66-launch.log`、`array-runtime-66-launch.json`、`array-runtime-66-threads.log`、`array-runtime-66-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。67 秒真实定义图像控制对象登记，323 秒 Minecraft Blocks／BlockState 字段内存写入记录与 TraceList 控制 backing 发布，均 RUNNABLE、未报告死锁；最终没有新的转换错误输出或游戏内结果，启动超时未解决。 |
| `build-astra-memoryuse-cf-stack-resumption-package.log` | PASS：Astra max 字段内存记录、同一实际读取窗口完整查询及 B CF 来源栈接续三文件工作包统一构建成功，21 秒；Core／Agent／Bootstrap 43、Native ABI 38，复用第六十六次 Native，Java 契约及持久格式未变。 |
| `array-runtime-67.log`、`array-runtime-67-launch.log`、`array-runtime-67-launch.json`、`array-runtime-67-threads.log`、`array-runtime-67-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。66 秒缓冲当前调用来源查询，323 秒 Blocks 初始化的真实类资源解压读取，均 RUNNABLE、未报告死锁；最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。 |
| `build-astra-io-shared-source-full-registration-package.log` | PASS：Astra max 缓冲／Io 当前来源链及 B 共享来源逐组接续、精确失败事件轮转、完整登记九文件工作包统一构建成功，17 秒；Core／Agent／Bootstrap 43、Native ABI 38，复用第六十六次 Native，Java 契约及持久格式未变。 |
| `array-runtime-68.log`、`array-runtime-68-launch.log`、`array-runtime-68-launch.json`、`array-runtime-68-threads.log`、`array-runtime-68-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。66 秒 Class.annotationData CAS 的字段枚举、实际 Nashorn 类加载与 Native 版本查询，321 秒 WallBlock／voxel 字段正常收尾，均 RUNNABLE、未报告死锁；最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。 |
| `build-astra-class-mirror-forkjoin-full-queues-package.log` | PASS：Astra max Class mirror 实例／静态域定位、有效字段快照与 B ForkJoin 共享／owner 完整队列接续五文件工作包统一构建成功，17 秒；Core／Agent／Bootstrap 43、Native ABI 38，复用第六十六次 Native，Java 契约及持久格式未变。 |
| `array-runtime-69.log`、`array-runtime-69-launch.log`、`array-runtime-69-launch.json`、`array-runtime-69-threads.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。唯一保存的 66 秒现场为 ASM／Nashorn 类定义期间 Native 代码声明查询与实际调用目标解析，RUNNABLE、未报告死锁；本轮没有保存后期现场，最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。 |
| `build-native-declaration-pool-package.log`、`build-astra-declaration-pool-reference-registration-package.log` | PASS：Astra max 全候选无来源保守传播、Native 声明整表快照／完整池失败释放与 B 完整引用登记轮转六文件统一链接、构建成功，Java 18 秒；Core／Agent／Bootstrap 43、Native ABI 38，新 Native SHA256 C4FE519DEE638BA99E890487B864B7D908E1AF26890B25B4F81216D9C0E5B065，Java 契约与持久格式未变。 |
| `array-runtime-70.log`、`array-runtime-70-launch.log`、`array-runtime-70-launch.json`、`array-runtime-70-threads.log`、`array-runtime-70-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。66.14 秒实际外部代码图像登记，321.71 秒 WallBlock／voxel 数组写入记录，均 RUNNABLE、未报告死锁；两次现场已保存，身份 hash 查询所处样本不证明线性查找或总体重复率。最终没有新的转换错误输出或游戏内结果，启动超时仍未解决。 |
| `build-astra-array-window-record-qualification-package.log` | PASS：Astra max 数组完整写入窗口／来源分区／控制闭包联合保护与 B 当前资格、并发缺席及真实日志帧接续八文件统一构建成功，21 秒；Core／Agent／Bootstrap 43、Native ABI 38，复用第七十次 Native，Java 契约及持久格式未变。 |
| `array-runtime-71.log`、`array-runtime-71-launch.log`、`array-runtime-71-launch.json`、`array-runtime-71-threads.log`、`array-runtime-71-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。65.75 秒 ZIP 类资源缓冲收尾等待实际堆来源锁，345.34 秒 Blocks 第 872 行的 WallBlock／voxel 初始化在实际数组写入守卫，后期 RUNNABLE；两次现场保存、未报告死锁。最终没有新的转换错误输出或游戏内结果，启动超时仍未解决，方块位置不是完整速度或效果证据。 |
| `build-native-array-bits-package.log`、`build-astra-array-bits-lossless-journal-package.log` | PASS：十二文件包统一 Native39 链接及 Java 构建成功，Java 20 秒；小数组完整原始位内联／混合图像与恢复接续、实时实际来源查询和 B 无损持久文本。Native SHA-256：628E9DBB5910C463F2387CAE2B0E2914A23FFF6E86C324581077F4C8C668ECEF；物理日志 VERSION 5 与原帧保持，逻辑文本字段新增 u16: 分支。 |
| `array-runtime-72.log`、`array-runtime-72-launch.log`、`array-runtime-72-launch.json`、`array-runtime-72-threads.log`、`array-runtime-72-threads-late.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。66.26 秒 main 等待实际 ZIP FileChannel 读取锁，持锁的 Forge 扫描线程处于 IoBridge.temporaryReturning 真实调用者检查；301.12 秒 main RUNNABLE，在 Blocks 第 451 行 WallBlock／voxel 初始化的小数组图像收尾中进行控制登记。早晚现场保存、未报告死锁；没有新的转换错误输出或游戏内结果，启动未解决，持久文本无保存／重启效果结果，不将单个 PC 或方块位置写成完整速度及攻防通过。 |
| `build-io-journal-world-package.log` | PASS：十四文件包统一 Java 构建成功，21 秒；IO／数组实际收尾、B VERSION 6 分段逻辑记录及迭代 NBT、C 雷击／袭击／scheduled tick 路径接续。Native39 未变，复用第七十二次载荷；构建通过不表示实际攻防效果通过。 |
| `array-runtime-73.log`、`array-runtime-73-launch.log`、`array-runtime-73-launch.json`、`array-runtime-73-threads.log` | FAIL：原 360 秒超时，脚本结束自建 JVM。65.98 秒现场明确报告 1 个 Java 死锁：Forge 扫描线程持 ResourceBridge.STREAMS，经 SourceMapBridge.SCOPES 的真实计数 CAS 等 TaskBridge 字段门；外部代码接续线程持同一字段门反等 STREAMS，main 同样等待 STREAMS。没有新的转换错误输出或游戏内结果；后期重复现场未采集。 |
| `build-capacity-client-nativehost-package.log` | FAIL：首次统一构建 33 秒，在 Storage DLL 已完成链接后旧脚本 Get-FileHash 命令缺失，未进入起服；保留失败日志。 |
| `build-capacity-client-nativehost-repair-package.log` | PASS：22 文件包统一构建成功，19 秒；NativeControl40、Storage Native3、Core／Agent／Bootstrap43、Journal VERSION6。Control DLL：F192A53ADCDC9788BB96D3C2675EA2C547860A336808648E8B0FAC4AED92B2F2；Storage DLL：FDF6438D1498F3F9196C600D2B59F944D5C50449B788BD6C0C039FD5D795DE60。客户端双箱／乘客合成消费者后续补包首次构建失败，整包尚无实际效果结果。 |
| `build-client-consumer-render-repair-package.log` | FAIL：客户端双箱／乘客消费者 Java 补包构建 12 秒，Chest／Dispatcher 多目标选择器找不到对应 render 映射；保留实际错误。已拆为准确单目标接点并保留严格消费注入要求，待重新编译。 |
| `build-client-consumer-render-mapping-package.log` | PASS：客户端双箱／乘客实际合成消费者及单目标映射补包 Java 构建成功，16 秒；复用本包 NativeControl40／Storage Native3，未重复原生构建。Chest 交叉泛型上界的源码匹配警告保留；独立只读核对生成 refmap／reobf 产物将准确六参 render 映射至 m_6922_，实际唯一 Float2FloatFunction.get(F)F 消费保留 require=1／allow=1。该静态核对不代替客户端运行效果，原场景最终结果见下行。 |
| `array-runtime-74.log`、`array-runtime-74-launch.log/json`、`array-runtime-74-threads.log`、`array-runtime-74-threads-late.log` | FAIL：第七十四次原 raw-backing／early 场景最终原 360 秒超时，脚本退出 1 并按原规则结束自建 JVM。65.85 秒 main 在 ZIP 实际读入的 ExecutionFlow／TaskBridge 来源观察链，301.2 秒 main 在 ZIP 读取／ModuleClassLoader／FireBlock／Bootstrap 初始化，两现场均未报告 Java deadlock；未见 TRANSFORM_FAILED、VerifyError、Done 或实际效果标记。保留全部证据，不延长超时、不声明攻防通过。 |
| `build-world-visual-native-resolution-package.log` | PASS：九文件源码包首轮 NativeControl41／Java 统一构建成功，23 秒，复用 Storage Native3；NativeControl DLL：3DB02DAD5734275596DAED0FB75854D19344CDBA33E06E9D799BD32722286CA7。集中审阅之后确认共享 delay-IAT 的首解析来源误伤及客户端四处绘制／退役问题，修补尚未统一构建；本构建不作为运行效果结果。 |
| B/C 补包统一构建请求（2026-10-04，未执行） | NOT RUN：自动审批服务返回 HTTP 403，审批未能完成，命令未启动；没有生成 build-world-visual-native-resolution-repair-package.log，不是编译失败。已回填槽修补后的定点源码复核还发现本次旧 thunk 消费的晚回填分支，继续补齐；未运行原场景75，未生成新候选或同步远端。 |
| `build-world-visual-native-resolution-repair-package.log` | PASS：十文件补包 NativeControl41／Java 统一构建成功，25 秒，复用 Storage Native3；包含已回填与本次晚回填 delay 槽来源、客户端四处绘制／退役、B 已处置 Map／holder 历史查询修补。NativeControl DLL：7BFE0AC3346AA3253755C771912EE46E80DDC8171B9D91C299E37BB03B5D5BB0；本构建不作为运行效果结果。 |
| `build-world-visual-native-resolution-boundary-package.log` | PASS：十文件补包指令边界及 B 同类引用历史查询修补 NativeControl41／Java 统一构建成功，23 秒，复用 Storage Native3；NativeControl DLL：2A22AEB672DB654B6FB6EE06F862ED6E9BF93D2BC561F0E934B0657187425263。无函数边界等未证旧 thunk 消费仍是明确缺口，不将构建作为完整效果通过。 |
| `array-runtime-75.log` / `array-runtime-75-threads.log` / `array-runtime-75-threads-late.log` | FAIL：原 360 秒启动超时；72.96 秒 main 等来源登记锁，332.19 秒 main 自己在同一锁内清理弱键；未报告 Java 死锁，未取得游戏内效果。 |
| `build-source-map-retirement-package.log` | PASS：弱键清理改为每次实际插入最多处理两个队列项，Java 统一构建 17 秒；Native 两个 DLL 未改动。此结果不包含后续 ABI 44 的 B 源码包。 |
| `array-runtime-76.log` / `array-runtime-76-threads.log` / `array-runtime-76-launch.log` / `array-runtime-76-launch.json` | FAIL：原 360 秒启动超时；66.19 秒 main RUNNABLE，经资源缓冲读取进入来源弱键清理，未报告 Java 死锁。用户转为 B 代码优先后未新增后期采样或重新起服；原脚本结束自己的 JVM，无游戏内效果结论。 |

第三十九次 102 秒完整现场明确 Found 1 deadlock：main 持 HEAP，在新建 ExecutionFlow.Heap.slots 的 HashMap 时，SourceMap.born → SCOPES.computeIfAbsent → ConcurrentHashMap.fullAddCount → 原生 Unsafe CAS 等待实际字段门；接续线程持该字段门，同样的 SCOPES 计数更新在原生 CAS 观察中等待 HEAP。原场景最终超时，脚本结束自建 JVM，没有游戏内动作。此次未触发首个 StackOverflowError 抛出诊断，不能结清原数组异常。

完整修补包将 ExecutionFlow 的五处 HashMap 构造改为私有 TraceMap。空父类构造原 born 条件不接受子类，因此不为这些记录容器先创建业务来源作用域；随后由实际私有构造类及已限定的实际创建类，在原弱身份目录中登记准确实例的独立 Map 角色。角色查询、作用域与节点发布按该实例判断；普通保护登记、复制件与其他类不取得该角色。新类在 JNI 观察守卫发布前完成定义准备。SourceMap.valueAllowed 继续先执行 BackingBridge 原控制／元素检查，业务 Map 及实际数组／字段来源、前后快照、读写历史继续按原路径处理。四个相关类已统一构建通过，17 秒；第三十九次的 Native 37 诊断 DLL 复用，Core／Agent／Bootstrap 39 不变。第四十次已按上述具体转换错误与原超时结果结束。

第四十次现场已进入 Forge 扫描 Minecraft 包，后续 MixinInfo 转换的具体失败是栈帧计算只读资源流而未能取得实际 ClassNode 类型信息。当前两类完整接续包已写齐：ControlClassWriter 先查询同一非 bootstrap 加载器已发起的实际类层级，资源不存在时以同一加载器解析并关闭类初始化；当前正在转换的类继续使用本次声明形状，资源解析及原公共父类算法保留。内部容器角色查询只读原弱身份 bit，不再为一个只读布尔结果逐次展开 Java 调用栈；真实构造入口、精确类型／对象登记与所有写入判断保持不变。该接续整包已统一构建通过，16 秒，第四十一次已按上述超时结果结束。

第四十一次没有前次 MISSING_FRAME_TYPE 输出，最终日志到达 Launching target forgeserver，但仍超时，没有游戏内结果。168 秒实际现场中，控制目录每个死亡键在 remove 内重新查询真实调用类。完整目录变更已统一构建通过，15 秒：每批实际死亡键只认证一次，按原引用队列及实际节点身份逐个解除；空队列不增加认证。登记仍认证实际调用类和固定 CONTROLS，查重返回原弱身份节点，扩容和新节点发布并入同一已认证方法；新旧 backing 的弱保护关系保留，角色只在原认证构造入口按该节点赋值。没有跨动作许可缓存、线程豁免或业务数据观察省略。第四十二次仍按上述原超时结果结束。

第四十二次 311 秒完整现场中的 snapshot 每条指令都保护同一方法共享的 parsedLabels 表，因而重复遍历同一实际对象图。完整修补包已统一构建通过，17 秒：单张新快照合并 bytes／node／methods 原根以及每个非空方法的一份实际标签表，以原身份去重遍历联合图一次，再沿原登记入口保护；没有跨快照缓存，实际类树重新序列化、指令及异常处理器身份和修改历史均保留。ExecutionFlow 的 hasFrame／sources／unknown／frameSources 先读取当次真实 CURRENT，无帧只返回原空值，有帧继续真实 CodeSourceBridge 认证，原准确帧／方法／位置／来源核对和写入策略不变。第四十三次按上述实际安装提前失败结束；原 StackOverflowError、B/C 与 O02／O05 没有结清。

第四十三次的原安装错误被 Java 报告／格式化路径遮住，Native 抛出时诊断补充已统一构建通过，15 秒。第四十四次同样提前失败，没有该事件诊断输出；不能据此推断具体原因或异常能力可用性。现已移除临时全局 Exception 事件监听，在原 JNI ExceptionDescribe 开始 Java 格式化前，用原 JNI 读取实际 Throwable 类型／消息／cause；InvocationTargetException 按真实 target 字段接续。读取有界且防循环，恢复同一原 pending Throwable 后继续原 ExceptionDescribe，不调用 Java 异常 getter，不授予来源或写入权限。这一完整诊断替换包已统一构建通过，15 秒；第四十五次复用原场景取得具体失败证据。ABI 与持久格式未变，没有新候选或 GitHub 同步。

第四十五次已读到真实 InvocationTargetException → StackOverflowError。完整补充包按已核对的 [OpenJDK 17 BacktraceIterator 布局](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/classfile/javaClasses.cpp)，直接读取同一 Throwable 保存的镜像、方法名 symbol 和指令／版本槽；symbol 字段使用本次运行 VM 的原导出布局，不调用 Java 异常 getter／格式化。该包统一构建通过，16 秒，第四十六次已捕获上述完整递归路径。该错误位于 RecoveryAgent.start 的失败报告字符串链接所触发的 SourceMap 作用域查询期间，更早安装失败原因尚未读到，不能据此结清第二十六／三十六／三十八次原数组问题。

两类完整修补包统一构建通过，15 秒：TaskBridge 的准确内存调用者、原生调用者、Core 调用者、安装调用者及调用来源查询改为预先定义的直接迭代函数；ExecutionFlow 的桥身份查询同样不再首次链接 lambda。这里的 installingCaller 是安装调用者，组控制 writer 的直接迭代改动属于下一批。实际 walker 的隐藏帧选项、原筛选条件、真实类／模块／加载器／代码来源及准确 writer 认证、Java／Native 来源合并与前后快照不变。第四十七次已到 Minecraft 世界注册初始化，但仍按上述原超时结果结束，没有游戏内动作。

第四十七次早期现场中的内部记录写入，每次先展开原生库调用栈，然后才判断既有内部写入许可。当前完整包将这些原许可先判断，最后仍执行实际组控制 writer 或原生库实现调用者认证；两个原 StackWalker 查询使用预定义函数直接迭代，筛选条件不变，没有新增写入许可或跨动作许可缓存。该包已统一构建通过，15 秒，第四十八次按上述实际栈溢出结果结束。更早安装错误、原数组失败、B/C 与 O02／O05 尚未结清。

原生控制入口提前绑定完整包已统一构建通过，16 秒，第四十九次按上述原启动超时结果结束。第四十八次在 premain 提前失败，SERVER_EXIT=1，实际 Throwable 保存的 1024／1024 帧显示：unsafeControlScope0 首次原生函数解析进入 NativeLibraries.find 的 ConcurrentHashMap 迭代，Unsafe.getReferenceAcquire 再进入 beginHandleRead／unsafeControlScope0，形成真实递归。当前在已认证的实际 bootstrap NativeControl 安装中，通过原 JNI RegisterNatives 将其全部 75 个既有私有入口绑定到同一 DLL 的实际实现地址，完成后才发布 JNI／Unsafe 守卫；不执行模拟调用，不改变业务入口、来源或写入权限。DLL SHA-256 00E78DC81D70C1A6C1667F4D70D8E87DD75B233509B5A16DE119B1DEC1EA411E，ABI 与持久格式未变。B/C 与 O02／O05 尚未收口。

调用图集合合并／Mixin 元数据读取完整三类工作包已统一构建通过，14 秒，第五十次按上述启动超时结果结束。接收者与来源值的不可变集合在并集／交集结果相同时沿用既有集合，空来源值不构造空 Map／HashSet；参数位集与原类型、来源、未知及必需条件判断不变。Mixin 元数据每次仍重新序列化并解析当前实际类、核对原方法／指令／异常处理器数量、保护这次新图像，并比较全部原元数据和帧位置；仅去掉该查询未使用的 Body／Instruction 历史构建，真实变换快照继续保留全部原指令身份及修改历史。第四十九次仍在原 360 秒时限内未到游戏内动作，脚本结束自建 JVM；77 秒现场为调用图接收者合并中 HashSet 构造的作用域登记，323 秒现场为 Mixin 成员元数据读取的完整类快照构造，均为 RUNNABLE，未报告死锁。没有新游戏内结论或候选，B/C 与 O02／O05 尚未收口。

Mixin 历史元数据行／无发布成员查询完整两类工作包已统一构建通过，16 秒，第五十一次按上述实际 Mixin 越界与原超时结果结束。每份新 Snapshot 同时保存自己那一版的不可变元数据行并保护该图像；当前活类仍每次重新序列化、解析及比较，历史 before／after 使用各自冻结行，未跨快照复用当前判定。成员构造仍先认证原入口、真实父对象并查询当前成员及上下文；两者均为空时按原逻辑返回，实际发布时再采集真实调用来源。第五十次仍在原 360 秒时限内未到游戏内动作，脚本结束自建 JVM；89 秒现场为 Forge 扫描时的原文件读入，313 秒现场已在 Minecraft 世界注册初始化的 Mixin 应用中，为成员创建的调用来源捕获，均为 RUNNABLE，未报告死锁。没有游戏内攻防结论、新候选或 GitHub 同步，B/C 与 O02／O05 尚未收口。

bootstrap 发起类查询／钩子栈映射完整工作包已统一构建通过，26 秒，第五十二次按上述字段插桩失败与原超时结果结束。原 75 个控制原生入口提前绑定扩展为含新查询的 76 个入口；普通按名查找候选每张图从实际 bootstrap 发起类取得，再按实际定义加载器及非隐藏类筛选，旧完整 bootstrap／全 VM 枚举和隐藏定义通路保留。玩家跨维度、保存、物品及其他绘制钩子生成的帧统一使用 F_NEW，已有压缩帧按原局部值／栈状态展开，生命周期 lease 的实际槽位和原正常／异常收尾保留；快照仍读取实际帧，不用跳过帧来遮住原越界。Core／Agent／Bootstrap 40、Native 38，持久格式不变。第五十一次原 360 秒超时；停止宽限中实际报 PlayerLifecycleHooks$DimensionChange 的 PostApply 快照读取 ArrayIndexOutOfBoundsException（403／212），main 已退出，后台仍运行，脚本结束自建 JVM。100 秒早期现场为调用图的全 VM bootstrap 查询；364 秒现场处于原停止宽限，后台为候选表构造，含 DestroyJavaVM，未报告死锁。没有游戏内攻防结果、新候选或 GitHub 同步，B/C 与 O02／O05 尚未收口。

第五十一次的具体故障与生成帧表示混用对应：extendFramesWithLease 将已有帧改为 F_NEW，却在同一方法追加 F_FULL。当前两处 Core 改写使用统一展开格式，保留原帧与原 lease 局部槽语义；其他保存／绘制分支先按原 frame 类型还原完整状态，再用同样的 F_NEW 追加帧。依据 [ASM visitFrame 的单方法帧格式合同](https://asm.ow2.io/javadoc/org/objectweb/asm/MethodVisitor.html#visitFrame(int,int,java.lang.Object%5B%5D,int,java.lang.Object%5B%5D))，展开与压缩表示不能在同一方法中混用；本次没有移除真实帧观察。

实际普通类查找使用 [JVMTI GetClassLoaderClasses](https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html#GetClassLoaderClasses) 的 bootstrap 发起集合；该集合用于已有按名查找，隐藏定义继续由原完整枚举及准确 Class 身份关联。当前图仍取得新集合，实际定义加载器筛选、普通名称及隐藏身份区分、当前 VM 方法版本核对不变，没有跨图复用成功结论。新增查询保留原控制调用者认证并在安装期间提前绑定；完整包已统一构建通过，26 秒，实际结果按第五十二次原场景记录。

## 2026-10-03 B/C 调用图版本核对

第三十二次现场指向真实 ModFileInfo 初始化中的调用图绑定。当前整包改动在每次图分析的定义快照内按普通类声明名／隐藏 Class 实际身份定位候选；原实际加载器、先前绑定对象和 Native 版本检查继续执行。最新候选只有在声明形状和全部非 abstract／native 方法均匹配时结束旧版本搜索，此时已达到原最大匹配数的上界且保留最新候选优先规则；局部匹配仍比较余下候选，没有跨图或定义变化复用成功结论。

Native 镜像发布前建立固定方法索引并保存其真实指令偏移，供当前类版本、真实帧来源、执行计划核对共用。实际 VM 字节码仍每次取得并重新解析，其分支、常量图、异常表与原活动版本核对不变。每次 ConstMethod 头和异常表按当前 VM 导出布局批量读取；名称／描述符、范围、来源与 code_vm_unchanged 校验保留。普通类的名称不符先筛掉，匹配仍须原真实加载器；隐藏定义继续核对真实 Class 身份。

第三十三次仍在原场景时限内未到游戏内动作，脚本结束自建 JVM。157 秒完整现场 main 正在日志初始化时解压实际包；330 秒完整现场在 Forge ModFileParser 类加载／转换中，建立候选列表的 ArrayList.add 进入 SourceMapBridge.enter → CodeSourceBridge.executionList → StackWalker.getCallerClass。新修补整包改用私有节点和受保护的固定桶数组构造候选索引，碰撞逐个执行原候选身份判断，保留最新图像先选；没有 JDK Map／List 修改或外部来源作用域的通用豁免。列表角色只能由其实际 ExecutionFlow nest 构造类登记准确对象；普通 Map／普通 ArrayList 不可能持有该角色，SourceMap 先排除这两种负例，其他列表候选继续原实际调用类认证和弱身份目录核对。valueAllowed 仍先执行原 BackingBridge 入站控制。该修补已统一构建通过，17 秒。

第三十四次 79 秒完整现场 main 在真实文件读入／HeapByteBuffer 方法的资源来源捕获中，经 NetworkBridge.currentSources → union → IdentityHashMap 构造进入底层数组继承回调。320 秒完整现场 main 在 Forge 类加载的 ZipFileSystem 文件读取中，经 HeapByteBuffer.put → ResourceBridge.finishBufferOperation → ExecutionFlow.finishArrayChange → NativeControl.heapArrayImage0 捕获实际数组快照，仍为 RUNNABLE。原场景最终超时，脚本结束自建 JVM，没有游戏内结果。

针对这个空作用域来源路径，四个 Bootstrap 类的整包代码已统一构建通过，16 秒：网络作用域直接遍历真实 Scope 链，去掉临时列表；网络／客户端合并和客户端／缓冲查询只在有原实际成员时创建身份集合；TaskBridge 的作用域、资源和原生写入来源沿同一原成员过滤做惰性合并。资源生产者过滤、原 null 未知来源、真实任务／IO／执行帧范围和身份去重继续保留；非空结果继续返回新数组。没有跨动作许可复用、源数组别名复用、C／Java 来源省略或 UNKNOWN 写入快照／历史豁免。第三十五次 125 秒完整现场为 Log4j 类转换绑定中的 loadedClasses0；363 秒仍在原结束宽限内，main 在 AccessTransformer 载入所需的实际 ZipFileSystem／FileChannel 读取中。最终仍超时，没有游戏内动作。

原生指令来源的空目录筛选整包已统一构建通过，15 秒：在不可变代码镜像发布锁内，发布首个非空指令 OwnerLink 时置位单调 presence；没有这样的镜像时，原逐帧 relevance 必为 false，不再调用空的 Native 全栈来源查询。Java 原作用域捕获及错误、Native binding／library 来源、执行计划／VM 版本核对、实际数组快照与历史仍保留。第三十六次 premain 提前退出，SERVER_EXIT=1；日志在 REFLECTION_FIELD_GUARD_INSTALLED:9 后记录数组 BEGIN 两次 StackOverflowError，长度 829／489，随后 libinstrument 提交类字节数组失败并终止。没有取得线程快照，没有游戏内结果；不能把构建通过计为此异常修复。

现有 ExceptionDescribe 输出再次进入被监控的 Java 格式化路径，未留下完整异常栈。失败日志修补已统一构建通过，15 秒，改为直接用 JVMTI 输出该失败 JNI 边界仍活动的实际帧，随后重新抛回原异常；这不是已抛出异常的完整栈，也不改变数组策略、来源和快照。第三十七次没有出现原数组失败，113 秒完整现场 main 在 Log4j 类初始化所需的实际调用图／Native codeVersion 核对中。随后推进至游戏层的 Mixin 准备，因 ACTUAL_ASM_LIBRARY_UNOBSERVED:org.objectweb.asm.tree.MethodNode 提前退出，SERVER_EXIT=1；后期请求时自建 JVM 已退出，未生成后期线程文件，没有游戏内攻防结果。不能据此把第三十六次栈溢出记为修复。

该实际成员来源查询错误已定位：MethodNode 没有 CodeSourceBoundary 库角色登记，memberMetadata 却要求该登记。完整启动接续包改为按已观察父树中的实际方法／字段对象身份确定类型；不存在于真实两张表的对象仍不返回来源，树来源、原修改记录与调用认证继续保留。该包同时把前述 codeVersion 路径中的 VM 常量池头按实际导出布局一次读取；字符串常量查找每次重新批量读取当次活的 tags／slots，再重新核对选中 UTF 槽的当前 symbol 身份。只复用临时存储，没有跨查询许可或来源缓存；字节码、分支、常量图、异常表、隐藏自身引用与活动版本核对保留。整包已统一构建通过，18 秒，第三十八次再次在 premain 提前退出，SERVER_EXIT=1；反射字段守卫安装后，原数组 BEGIN 长度 829／489 的 StackOverflowError 再次出现。失败边界的两帧均是 Throwable.printStackTrace，不能据此确定更早异常的递归根因。没有线程快照，未到 Mixin 修补点或游戏内动作。已写入仅在首个真实 StackOverflowError 抛出时输出实际 VM 帧的异常诊断，不改变防护判断；待统一构建并沿原场景定位。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-code-version-index-snapshot.log` | PASS：完整调用图版本核对包／开发包统一构建成功，18 秒；DLL SHA-256 `9C886FB061C568F069B001CC94896E1ACA5B96BC265BF8673DC09C1930A77E16`，Native ABI 37。 |
| `array-runtime-33.log`、`array-runtime-33-launch.log`、`array-runtime-33-threads.log`、`array-runtime-33-threads-late.log` | FAIL：复用原 raw-backing 场景仍超时，脚本结束自建 JVM；157／330 秒完整现场保留，后者为上述候选列表回入，最终进入 Mod 发现／依赖处理，没有游戏内结果。 |
| `build-code-candidate-private-index.log` | PASS：候选索引／角色查询完整修补包与开发包统一构建成功，17 秒，复用 Native 37。 |
| `array-runtime-34.log`、`array-runtime-34-launch.log`、`array-runtime-34-threads.log`、`array-runtime-34-threads-late.log` | FAIL：原场景超时，脚本结束自建 JVM；79／320 秒完整现场保留，没有游戏内结果。 |
| `build-empty-scoped-source-union.log` | PASS：四个 Bootstrap 类及开发包统一构建成功，16 秒，复用 Native 37。 |
| `array-runtime-35.log`、`array-runtime-35-launch.log`、`array-runtime-35-threads.log`、`array-runtime-35-threads-late.log` | FAIL：原场景超时，脚本结束自建 JVM；125／363 秒完整现场保留，没有游戏内结果。 |
| `build-native-source-empty-presence.log` | PASS：原生空目录筛选整包／开发包统一构建成功，15 秒；DLL SHA-256 `F5F1BEAE9B4DF028596AEC4EECC5D3764CAA9D624ED05343D8616955FB4EE8CA`，Native ABI 37 未变。 |
| `array-runtime-36.log`、`array-runtime-36-launch.log`、`array-runtime-36-launch.json` | FAIL：SERVER_EXIT=1，premain 数组 BEGIN 栈溢出，libinstrument 提交类字节失败；没有线程快照或游戏内结果。 |
| `build-array-failure-vm-frames.log` | PASS：现有失败日志修补／开发包统一构建成功，15 秒；DLL SHA-256 `CEDE3DCB323CEDE4F83E67630373D2514DD833058FD65401D2D00F9B559B189E`，Native ABI 37 未变。 |
| `array-runtime-37.log`、`array-runtime-37-launch.log`、`array-runtime-37-launch.json`、`array-runtime-37-threads.log` | FAIL：SERVER_EXIT=1，Mixin 准备因 MethodNode 库角色未观察而失败；113 秒完整现场保留，后期请求时 JVM 已退出，没有游戏内结果。该次没有原数组异常输出，未取得原异常发生栈。 |
| `build-asm-member-live-pool.log` | PASS：成员来源与活的 VM 常量池读取整包／开发包统一构建成功，18 秒；DLL SHA-256 `F1FE0F8E4FC467066FB9A4A99D10E07D7E9EB3FF9D336BCC763397F29172C1E9`，Native ABI 37 未变。 |
| `array-runtime-38.log`、`array-runtime-38-launch.log`、`array-runtime-38-launch.json` | FAIL：SERVER_EXIT=1，premain 原数组 BEGIN 栈溢出再次出现；现有日志的活动帧只有 Throwable.printStackTrace，未取得更早异常的抛出栈，没有线程快照或游戏内结果。 |
| `build-original-overflow-throw-frames.log` | PASS：首个实际 StackOverflowError 抛出现场诊断／开发包统一构建成功，15 秒；DLL SHA-256 `C772CF49D7A8110B56FF53ED08FBB58551426F758F075A9C5D35750589DDEF89`，Native ABI 37 未变。诊断不授予来源、执行或写入权限。 |
| `array-runtime-39.log`、`array-runtime-39-launch.log`、`array-runtime-39-launch.json`、`array-runtime-39-threads.log` | FAIL：102 秒完整现场确认上述实际 SCOPES 字段门／HEAP 死锁；原场景超时，脚本结束自建 JVM，没有游戏内结果。没有触发原始 StackOverflowError 抛出诊断。 |

Core／Agent／Bootstrap ABI 39，Native ABI 37，持久格式未变。B/C 与 O02／O05 未收口，A 原候选与结论保留；没有新候选或 GitHub 同步。

## 2026-10-03 B/C 原生空来源方法筛选

第三十一次沿原 raw-backing 场景仍未在原时限内到达游戏内动作。112 秒完整现场 main 正在 BootstrapLauncher 的真实 JAR 读取，不含第三十次的内部列表作用域栈；最终日志进入 Forge／Mixin 初始化。后期线程请求时自建 JVM 已退出，未生成该次后期线程文件。此结果不能证明列表修补的完整攻防效果，也不能结清第二十六次原数组失败。

原生数组入口继续同时保留原 C／Java 来源接续，因为两者覆盖的实际帧和来源分支并不完全相同。当前新的完整工作包只合并不可变数据的空集合判断：成功发布前，按真实 OwnerLink 指令数组记录方法和镜像是否曾具有非空来源行；原 code_method_relevant 在全空行上始终为 false，现在提前筛掉这类方法和镜像。非空行仍执行原定义／加载器／隐藏 Class 身份／方法选择和活的 Module 范围判断；模块专属查询仍逐来源核对，原 code_frame_sources 与活动方法的字节码、常量、异常表及 code_vm_unchanged 校验均保留。标记不随停止或弱模块回收变为 false，避免丢失原非空来源；新定义／来源关系继续按新镜像登记。没有缓存通过结果、许可或负的 Class 查询。

第三十二次 240 秒完整现场 main 正在真实 ModFileInfo 初始化的类加载／转换中，经 ExternalCodeDefinitions.expandGraph → resolve → target → bind → NativeControl.codeVersion0 核对相关实际定义。最终日志进入 Forge Mod 发现和嵌套依赖处理，仍在原场景时限内未进入游戏内动作，脚本结束自建 JVM。后期线程请求时该 JVM 已退出，没有该次后期线程文件。继续处理这个真实绑定路径；早期现场只证明该时刻执行位置，不能证明后续无死锁、数组防护或历史恢复通过。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `array-runtime-31.log`、`array-runtime-31-launch.log`、`array-runtime-31-threads.log` | FAIL：同一实际场景仍超时，脚本结束自建 JVM；112 秒完整现场保留，未取得后期线程文件，没有游戏内结果。 |
| `build-native-empty-owner-query.log` | PASS：原生空来源筛选／开发包统一构建成功，15 秒；DLL SHA-256 `D2B944CBDDB245B18F7DB595776EDE787ABE830441810D115FEF1B486DB90425`，Native ABI 37 未变。 |
| `array-runtime-32.log`、`array-runtime-32-launch.log`、`array-runtime-32-threads.log` | FAIL：同一实际场景仍超时，脚本结束自建 JVM；240 秒完整现场为上述真实代码版本绑定，最终进入 Mod 发现／依赖处理。未取得后期线程文件，没有游戏内结果。 |

Core／Agent／Bootstrap ABI 39，Native ABI 37，持久格式未变。只复用已有场景及阻止实际动作的必要诊断。B/C 与 O02／O05 未收口，A 原候选与结论不变，没有新候选或 GitHub 同步。

## 2026-10-03 B/C 来源记录内部列表回入

第三十次 100 秒完整现场确认真实 Inflater 提交返回后，ExecutionFlow.replaceRegion 的内部 ArrayList.add 重新进入 SourceMapBridge.enter。该列表只持有运行时来源元数据，并非业务 holder；重复建立应用来源作用域不能提供额外业务来源证据。当前完整工作包将内部区域、读写窗口、快照列表、字段／数组历史、局部值／控制记录及临时列表改为私有 TraceList，构造入口核对真实直接调用类，只接受 ExecutionFlow、Heap、ArrayChange、MemoryUse、Frame 五个实际类。

准确实例在既有 ControlRegistry 的弱身份键中登记内部列表角色；泛用保护登记不会设置角色，角色没有放在可被 clone 或反序列化复制的列表字段内。目录角色登记及 SourceMap 查询分别认证实际 ExecutionFlow nest 与实际 SourceMapBridge，修改仍受固定目录接收对象和真实内部调用类约束。SourceMap 的应用作用域／通知只跳过已登记角色的准确列表；valueAllowed 先执行原 BackingBridge 控制和元素判断再跳过额外应用来源查询。私有列表仍沿原控制目录受保护，ArrayList 原写入守卫及新 backing 的继承保护继续执行，业务字段／数组来源、前后快照和恢复核对没有省略。新类型在原 ArrayList 构造回调中不被误登记为普通 ArrayList；同类对象、克隆或反序列化件须保留原未对应分支，不继承内部角色。没有全部受控对象、Map 或线程的通用豁免。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-execution-list-scope-fix.log` | PASS：三个 Bootstrap 类及开发包统一构建成功，15 秒，复用 Native 37；没有运行其他检查。 |
| `array-runtime-31.log`、`array-runtime-31-launch.log`、`array-runtime-31-threads.log` | FAIL：同一实际场景仍超时，脚本结束自建 JVM；112 秒完整现场正在读取包，未取得后期线程文件，没有游戏内结果。 |

Core／Agent／Bootstrap ABI 39，Native ABI 37，持久格式未变。原实际失败保留，不能据编译通过结清数组栈溢出或 B/C／O02／O05。A 原候选与结论不变，没有新候选或 GitHub 同步。

## 2026-10-03 B/C 数组来源快照与专用弱身份控制目录

数组记录完整工作包保留每次实际写入前后的真实快照，把历史分段改为同一受保护快照的准确窗口。比较和引用恢复按各窗口的真实偏移执行，原生字节恢复只取对应窗口，并保护新生成的提交数组；当前值核对、并发／未知来源规则不变。方法计划中的固定指令来源与本次帧的声明来源复用不可变记录，空集合及同一单 Module 的合并保留原未知标记。根写入不重复通知同一区间，嵌套写入仍通知实际完成。

第二十九次 321 秒完整现场 main 为 RUNNABLE，在真实 JMX 类转换的 ExternalCodeImages.protectExecution → CodeSourceBridge.protect → HashMap.put → SourceMapBridge.valueAllowed → BackingBridge.deny → TaskBridge.controlCaller 路径。最终进入 Forge／Mixin 初始化，原时限内仍未进入游戏内攻防动作，脚本结束自建 JVM。该现场指向固定控制目录的内部登记重复调用守卫，不能把编译或向后启动记为实际防护／恢复通过。

新的完整工作包将这一个固定控制目录改为专用弱身份目录。登记／回收在原目录锁内按真实对象身份进行，不经过被监控的 JDK Map 写入。内部修改入口核对真实直接调用类和固定目录对象，私有节点所属真实 bootstrap nest 沿原字段／Unsafe／JNI 自有状态守卫保护，Reference.clear／enqueue 仍认证内部写入者。每代底层数组都有弱身份登记，外部仍持有的旧数组继续受保护；弱键不强留原对象。Native 固定表读取绑定实际 ControlRegistry 类、实际 CONTROLS 和实际 ControlKey[] 字段，原 JNI 引用读取及调用者认证保留。SourceMap 只移除已不存在的旧私有 HashMap 特例，其他 Map 原来源作用域及入站守卫继续执行。

Native 常量核对在一次方法比较内复用临时队列和索引存储，每次根常量仍重新核对全部实际关系；隐藏自身引用继续读取当前真实 VM 槽，方法字节码／异常表和活动版本校验不变，没有缓存通过结果。此包已包含对应 Java／JNI 固定表结构变化，Native ABI 更新为 37。

第三十次 100 秒完整现场已离开早期版本核对，在 BootstrapLauncher 的真实 JAR 读取中，经 ExecutionFlow.finishArrayChange → refreshArraySources → replaceRegion → ArrayList.add → SourceMapBridge.enter 记录内部来源列表操作。367 秒完整现场 main 为 RUNNABLE，真实调用栈推进到 Forge 的 MinecraftLocator.scanMods／CommonServerLaunchHandler.getMinecraftPaths 包读取，没有前次目录锁环。最终仍在原场景时限内未进入游戏内攻防动作，脚本结束自建 JVM。以上只限定当前实际安装路径，未证明数组防护、历史恢复、原栈溢出或整组来源终结；内部列表回入继续处理，未运行其他检查。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-array-source-image-reuse.log` | PASS：数组来源记录完整工作包／开发包统一构建成功，15 秒，Native 36 复用。 |
| `array-runtime-29.log`、`array-runtime-29-launch.log`、`array-runtime-29-threads.log`、`array-runtime-29-threads-late.log` | FAIL：原时限内未到游戏内动作，脚本结束自建 JVM；44／321 秒完整现场保留，后者为上述真实控制登记路径。 |
| `build-control-weak-registry-scratch.log` | PASS：专用弱身份目录、真实原生固定表绑定、比较临时存储及开发包统一构建成功，19 秒；DLL SHA-256 `4E9FBE5F9C7AC2356B9A8D12E1914315E4D1D3076271C9DE7334CCA69DB31766`，Native ABI 37。 |
| `array-runtime-30.log`、`array-runtime-30-launch.log`、`array-runtime-30-threads.log`、`array-runtime-30-threads-late.log` | FAIL：复用同一 raw-backing 实际场景仍超时，脚本结束自建 JVM；100／367 秒完整现场保留，后者已进入 Forge 扫描 Minecraft 包，未到游戏内动作。 |

Core／Agent／Bootstrap ABI 39，Native ABI 37，持久格式未变。第二十六次原失败保留，不能据此包的编译改写为修复。只复用既有场景和阻止实际动作的必要诊断；B/C 与 O02／O05 未收口，A 原候选与结论不变，没有新候选或 GitHub 同步。

## 2026-10-03 B/C 固定控制目录重复来源作用域死锁

第二十七次 169 秒完整现场明确 Found 1 deadlock。main 在 SHA 输出的真实 VarHandle 写入中登记 UnsafeMutation，持 CONTROLS，通过 HashMap.newNode → SourceMapBridge.node 为固定控制目录建立来源作用域；ConcurrentHashMap.computeIfAbsent 的 ThreadLocalRandom.getProbe 经 Unsafe.getInt 来源读取等待 HEAP。后台接续线程在实际 ASM 读取／容器创建中持 HEAP，来源合并创建的 IdentityHashMap 发布新 backing 时等待 CONTROLS。此次与第二十二次的 table getter 问题是不同的实际回入路径。

修补只识别真实 CodeSourceBridge.CONTROLS 对象，身份查询由实际 SourceMapBridge 直接调用。该固定私有目录不再注册应用来源作用域、节点来源或变更通知；valueAllowed 仍先调用原 BackingBridge 入站控制／元素策略，再跳过这个私有目录的额外来源查询。目录及当前 table 的原保护、HashMap 实际写入 gate 保留；私有 CodeSourceBridge.Key 所属节点继续由 TaskBridge.criticalEntry 判为控制节点，其他映射及应用来源登记不变。没有全局或线程级豁免。

第二十八次 320 秒完整现场 main 为 RUNNABLE，CPU 303 秒，正在 Inflater／ZipFS／ModuleClassLoader 读取 Log4j 类字节，未见前次锁环。最终日志进入 ModLauncher、Forge 配置和 Mixin 初始化，仍未在原时限内到达游戏内实际攻防动作，脚本结束自建 JVM。该现场证明当前向后运行的路径，不能替代完整防护、恢复或 B/C 通过结果。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `array-runtime-27.log`、`array-runtime-27-launch.log`、`array-runtime-27-threads.log` | FAIL：169 秒现场确认上述实际死锁；原时限内未进入游戏内动作，脚本结束自建 JVM。该次在死锁前没有新的数组异常栈，不能据此把第二十六次栈溢出记为修复。 |
| `build-control-registry-scope-deadlock-fix.log` | PASS：CodeSourceBridge／SourceMapBridge 及开发包统一构建成功，15 秒；Core／Agent／Native 未改。 |
| `array-runtime-28.log`、`array-runtime-28-launch.log`、`array-runtime-28-threads.log`、`array-runtime-28-threads-late.log` | FAIL：同一个既有 raw-backing 场景进入 Forge／Mixin 初始化，原时限内仍未到游戏内动作，脚本结束自建 JVM；43／320 秒现场保留，后者未见前次锁环。 |

Core／Agent／Bootstrap ABI 39，Native ABI 36，持久格式未变。第二十六次原失败仍保留。A 原候选与结论不变，B/C 尚未完成，没有新候选或 GitHub 同步。

## 2026-10-03 B/C 控制登记重复查询与实际数组栈溢出

第二十五次 335 秒完整现场中，main CPU 316 秒，处于 ModLauncher 的日志配置加载，CodeSourceBridge.protect 重复 CONTROLS.put 经 SourceMapBridge.valueAllowed 与 BackingBridge.incomingAllowed 两次执行同一个控制资格判断。当前修补在原锁内按同一个真实对象的弱身份键查重，仍回收已死亡键，未登记对象仍 put；Map 写入由 BackingBridge.deny 的原第一行核对控制资格，随后执行原元素策略。没有新增写入权限、线程豁免或全局缓存。

第二十六次原时限内仍未进入游戏内动作，日志两次记录 RONOVA_NATIVE_ARRAY_FAILURE:BEGIN，长度分别 56404／40495，InternalError 包装的最终原因为 StackOverflowError，随后 libinstrument 报不能提交类字节数组的断言。337 秒完整现场 main CPU 317 秒，在 Inflater 的真实数组写入 → ExecutionFlow 新 Heap → 控制登记／死亡键回收路径。该现场不是异常抛出栈，不能仅据它断定根因；该失败保留，不能把其他安装记录视为此路径通过。

为取得已发生失败的原异常栈，在既有 native_array_failure 中调用原 VM ExceptionDescribe，并清掉打印阶段的异常后重新抛回原 failure。本变更只扩展已有失败输出，原数组判断、写入及异常对象保留；JNI 对外接口与 ABI 未变。原生与开发包统一构建通过后复用同一个实际场景，没有新增夹具、测试或自证探针。后续 Astra 只读审查未取得可用返回，没有新的审查通过结论。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `array-runtime-25.log`、`array-runtime-25-launch.log`、`array-runtime-25-threads.log`、`array-runtime-25-threads-late.log` | FAIL：原时限内未到游戏内动作，脚本结束自建 JVM；44／335 秒实际现场均保留。 |
| `build-control-registration-query-fix.log` | PASS：Bootstrap／开发包统一构建成功，18 秒；Core／Agent／Native 未改。 |
| `array-runtime-26.log`、`array-runtime-26-launch.log`、`array-runtime-26-threads.log`、`array-runtime-26-threads-late.log` | FAIL：实际数组 BEGIN 栈溢出及类字节数组提交断言，原时限内未到游戏内动作，脚本结束自建 JVM；36／337 秒现场保留。 |
| `build-array-failure-stack.log` | PASS：Native／开发包统一构建成功，20 秒；DLL SHA-256 `508715BE3A25586AA0B57AD0CC96566C73C7FF947C63AC35D2AEE434149F03DA`，Native ABI 36 未变。 |
| `array-runtime-27.log`、`array-runtime-27-launch.log`、`array-runtime-27-threads.log` | FAIL：同一个既有 raw-backing 场景确认固定控制目录来源作用域造成的死锁，原时限内未到游戏内动作，脚本结束自建 JVM；没有新的数组异常栈。 |

Core／Agent／Bootstrap ABI 39，Native ABI 36，持久格式未变。B/C 尚未完成；A 原候选与结论不变，没有发布新候选或同步 GitHub。

## 2026-10-03 B/C 缓冲区来源查询工作包

第二十三次使用 Native 36 完整构建，仍在原 360 秒内超时；线程请求时自建 JVM 已退出，未生成第二十三次线程文件。第二十四次及时保存 33 秒完整线程现场：main 为 RUNNABLE，CPU 28 秒，经 DirectByteBuffer.get → ix → bufferOperation → invokingSources 逐字节展开来源调用栈，底层正在读取 JDK 镜像以取得实际重转换字节。该现场没有前次 CONTROLS／HEAP 死锁，不能据此把整次启动记为通过。

当前工作包一起修改四个 Bootstrap 类后统一构建：CodeSourceBridge 的来源方法先按类名取桶，桶内仍按原加载器／bootstrap 身份／精确实际定义与方法选择器判断，登记与回收仍在原锁内，桶及条目沿原控制保护；ResourceBridge 暴露同次缓冲区时复用本次已经捕获的 Module 来源，TaskBridge 新内部入口核对真实直接调用类；ExecutionFlow 没有 CURRENT 执行帧时，平台返回绑定原本必为 null，现提前结束该查询。实际停止来源、未知值、读写记录、当前指令与 Native 来源查询没有放宽。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `array-runtime-23.log`、`array-runtime-23-launch.log` | FAIL：原时限内未到游戏内动作，脚本结束自建 JVM；未取得该次线程文件。 |
| `array-runtime-24.log`、`array-runtime-24-launch.log`、`array-runtime-24-threads.log` | FAIL：原时限内未到游戏内动作，脚本结束自建 JVM；33 秒现场为上述实际缓冲区来源查询。 |
| `build-buffer-source-query-fix.log` | PASS：四个 Bootstrap 类及开发包统一构建成功，20 秒；Core／Agent／Native 未改，Native ABI 36 复用。 |
| `array-runtime-25.log`、`array-runtime-25-launch.log`、`array-runtime-25-threads.log`、`array-runtime-25-threads-late.log` | FAIL：同一个既有 raw-backing 场景使用上述完整构建仍超时，脚本结束自建 JVM；44 秒现场为实际调用图绑定／Native codeVersion 查询，335 秒已进入 ModLauncher 日志配置加载，没有游戏内效果。 |

Core／Agent／Bootstrap ABI 39，Native ABI 36，持久格式未变。只复用既有实际攻防场景及其必要启动诊断，没有新增测试或探针。A 原候选与结论不变，B/C 仍未完成，没有新候选或 GitHub 同步。

## 2026-10-03 B/C 控制表读取与堆来源锁死锁

第二十二次沿用第二十一次完整构建，及时保存的 233 秒线程现场明确 `Found 1 deadlock`。main 在 premain 的来源种子处理／JAR 解压中，持 CodeSourceBridge.CONTROLS，通过固定 table 的 MethodHandle getter → Unsafe.getReference → ExecutionFlow.byteMemoryReadBefore 等待 HEAP。Common-Cleaner 持 HEAP 处理真实 eraseThreadLocals 写入，构造 FieldCapture 时经 executionControls → protect 等待 CONTROLS。main CPU 仅 546 毫秒，此次为实际锁序环，不能只解释为启动耗时。

修补将这一固定元数据读取接到原 JNI GetObjectField：原生安装核对实际 bootstrap CodeSourceBridge／HashMap，绑定真实 CONTROLS 对象与 table 字段；Java／JNI 入口均核对实际 CodeSourceBridge 调用者，没有通用接收对象参数。原 VM 引用屏障和同一 CONTROLS 锁下的当前数组身份比较保留，不修改外部数组／字段写入权限，不添加线程级豁免。Astra 已只读复核实际认证、初始化、GC 与现场锁路径，未发现确定缺陷，未修改代码或运行测试。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `array-runtime-22.log`、`array-runtime-22-launch.log`、`array-runtime-22-threads.log` | FAIL：原时限内仍未到游戏内动作，脚本结束自建 JVM；233 秒完整现场确认上述死锁。 |
| `build-control-table-read-fix.log` | PASS：Bootstrap／Native／开发包统一构建成功，21 秒；Core／Agent 未改。Native ABI 36，DLL SHA-256 `E894D74F89894A8832965B3064AF74082F687AD1DC5347EB9FC5BF14917AD837`。 |
| `array-runtime-23.log`、`array-runtime-23-launch.log` | FAIL：同一个既有 raw-backing 实际场景使用上述完整开发包仍超时，脚本结束自建 JVM；线程请求时该 JVM 已退出，没有第二十三次线程文件或游戏内效果结果。 |

Core／Agent／Bootstrap ABI 39，Native ABI 36，持久格式未变。A 原候选与结论不变，B/C 仍未完成，没有新候选或 GitHub 同步。

## 2026-10-03 B/C 固定目录控制槽原子发布

不可变目录构建通过后，第十九次实际安装进入 Forge／Mixin 启动，但原 360 秒内未到游戏内攻防动作。155 秒完整线程现场中，main 在包读取／解压，接续线程 park 等待队列，没有第十八次的目录类锁死锁；这一个现场不能证明整个场景通过，脚本已超时并结束自建 JVM，失败日志保留。

源码审查进一步定位普通 CAS 与堆来源锁的相反锁序：AtomicReference 写入守卫持字段 gate 等堆来源锁；堆来源读取中的反射懒加载可进入 transformer 并发布同一槽。修复只绑定 ExternalCodeDefinitions 的 DIRECTORY／WORKER 两个实际 AtomicReference：Java 与 JNI 核对原真实调用类，原生安装取得固定实际对象与 value 偏移，调用已认证的实际 VM 原 compareAndSetReference，保留 GC 屏障，其他写入仍受原守卫约束。后台队列领取／接续仍为同一不可变状态原子更新。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-atomic-directory-fix.log` | PASS：不可变 Directory、锁外构造／分析的开发包构建成功，13 秒；Native 未改。 |
| `array-runtime-19.log`、`array-runtime-19-launch.log`、`array-runtime-19-threads.log` | FAIL：既有场景在原时限内仍未到实际攻防动作；155 秒现场未见前次死锁，不能记为整体通过。 |
| `build-native-directory-cas-fix.log` | PASS：固定真实控制槽 CAS 的 Agent／Bootstrap／Native／开发包构建成功，20 秒。Native ABI 35，DLL SHA-256 `E98DE22B695791D14ABC01BB91C9F7FD67C8A9FFEECE1B7F766E328FFA647235`。 |
| `build-native-directory-control-binding.log` | PASS：补齐实际反射控制方法保护后开发包统一构建成功，20 秒；Native 未再改。 |
| `array-runtime-20.log`、`array-runtime-20-launch.log`、`array-runtime-20-threads.log` | FAIL：确认原生后端、Map／反射／Unsafe／句柄守卫安装成功，但仍在原时限内未到游戏内动作。369 秒完整现场 main 为 RUNNABLE，CPU 354 秒，路径为 Buffer.limit → ResourceBridge.bufferOperation → TaskBridge.invokingSources → 每帧 ExecutionFlow.bridge 的重复 StackWalker 检查；未见目录死锁。脚本已结束自建 JVM。 |
| `build-source-frame-and-migration-fix.log` | PASS：三个直接来源桥入口的实际调用类认证、同一迁移记录位置恢复后的待处理解除，Core／Bootstrap／开发包统一构建成功，24 秒；Agent／Native 复用，实际 Native ABI 35。构建不证明完整迁移效果。 |
| `array-runtime-21.log`、`array-runtime-21-launch.log` | FAIL：同一个 raw-backing 实际场景使用上述完整构建后仍超时，脚本结束自建 JVM；日志到主线程终端环境提示，尚未取得游戏内结果。线程现场请求时该 JVM 已退出，未生成第二十一次线程栈文件。启动阻塞尚未收口。 |

查询修补只将三个已核对的 CodeSourceBridge 直接调用点改为直接真实调用类认证，不重复展开整栈；实际帧、当前执行、方法／描述符／指令位置与 Module 来源判断不变。迁移修补按同一 owner、真实 record 对象身份与目标 UUID 解除待处理：DataFixer 输出按实际图核对，SavedData 消费在原图锁内复核后才决定拒绝或过滤，未知及冲突来源不解除。B 的完整来源、共享贡献、任务／资源／容量和 O02／O05 仍待完成及实际验证。

Java ABI 39／Native ABI 35，持久格式未变。A 的既有候选和结论保持；B/C 未取得游戏内效果结果，没有新候选或 GitHub 同步。

## 2026-10-02 B/C 调用图、实际句柄写入帧与目录发布死锁

第十六次后台持目录锁展开完整类图，阻塞 Forge 加载。调用图现从根类全部实际方法出发，仅接续真实可达的被调用方法；被调用类的完整声明图用于原继承／接口分派，方法返回参数、控制来源和原 VM 版本核对保留。调用边同时按图身份与实际指令保存，避免多个隐藏定义共用指令节点时混用边。

第十七次已越过原包扫描阻塞，随后真实 `VarHandleReferences$FieldStaticReadWrite.compareAndSet → EventHelper.isLoggingSecurity` 被 `ACTUAL_HANDLE_WRITE_REQUIRED` 拒绝，进程以 1 退出。实际安装 JDK 的 `src.zip` 中 StackWalker.getCallerClass 明确始终过滤 MethodHandle 和隐藏帧；现使用已有带隐藏帧的 walk 取桥之外第一实际帧，继续核对原真实平台类及 nest，不添加通用 JDK 写入豁免。来源待处理查询也统一覆盖排队／分析中的宿主类实际 applied／analysis 来源；Astra 已只读复核该修补，没有运行测试。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-callgraph-continuation-fix.log` | PASS：缩小实际调用图并将分析移出目录锁的 Agent／开发包构建成功，14 秒；原 Native／Bootstrap 复用。 |
| `array-runtime-17.log`、`array-runtime-17-launch.log` | FAIL：原守卫安装后进入 Forge 包扫描，真实 JDK VarHandle 写入身份被拒绝，以 1 退出；没有游戏内数组效果。 |
| `build-handle-frame-continuation-fix.log` | PASS：真实句柄帧及排队来源修补的 Agent／Bootstrap／开发包统一构建成功，15 秒；Native 未改。 |
| `array-runtime-18.log`、`array-runtime-18-launch.log`、`array-runtime-18-threads.log`、`array-runtime-18-stop.log` | FAIL：94 秒完整现场明确 `Found 1 deadlock`。main 持 ExecutionFlow 的堆来源目录锁，懒加载反射 accessor 时等待 ExternalCodeDefinitions 类锁；后台持类锁执行 ControlImages.protect，反射缓存 CAS 等待同一堆来源锁。已核对本次 launch.json、原 JVM 参数后仅终止该自建 JVM；脚本记录 SERVER_EXIT=4294967295。没有游戏内效果结果。 |

针对确认死锁，类目录改为受保护的不可变 Directory，通过 AtomicReference CAS 同时发布定义、排队、分析中与刷新中工作；所有构造／保护和分析都在发布前执行，不持类目录监视器。新变化保留在队列，领取与转入分析中是同一原子更新，worker 用 park／unpark 接续。当时该最终改动尚待构建／实际运行，后续构建、实际场景及固定槽修补以上文为准，不能沿用前版构建作通过结论。该版 Java ABI 39／Native ABI 34；没有新候选或 GitHub 同步，A 原结论不变。

## 2026-10-02 B/C Map 守卫帧循环加载修复

第十五次保留到原始异常：`RONOVA_NATIVE_ARRAY_FAILURE:BEGIN:B:0:1143:Ljava/lang/ClassCircularityError;:dev/ronova/pro/bootstrap/SourceMapBridge$Frame`。原 Map 守卫在 enabled 后首次定义 Frame，libinstrument 填入其类字节数组时进入数组来源登记，登记中的 Map 操作再次要求同一 Frame，触发类循环。现将该真实 Frame 及同一作用域的 Key／OwnerRef／Gate／Scope／Refused 在原生观察和 Map 重转换之前准备；原控制映像登记仍覆盖这些类。未跳过 Map 或数组保护。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-source-frame-init-fix.log` | PASS：本批 Agent 与开发包统一构建成功，13 秒；Native／Bootstrap 未改，复用已有输出。 |
| `array-runtime-16.log`、`array-runtime-16-launch.log`、`array-runtime-16-threads.log`、`array-runtime-16-late-threads.log`、`array-runtime-16-final-threads.log` | FAIL：已越过原 Frame 类循环位置，Map／反射／Unsafe／30 个句柄工厂记录安装成功，进入 Forge 包扫描。105 秒现场经过已启用的 Map 来源作用域；283 秒主线程等待 ExternalCodeDefinitions 锁，332 秒完整线程记录显示后台接续线程持该锁进行 refreshes／接收对象分析。仍未在原 360 秒内完成实际场景，脚本终止自建 JVM，没有游戏内效果结果。 |

当前剩余等待点是后台来源分析持锁阻塞加载；未将其误记为已解决或死锁结论。第十六次前两份线程摘录只保留开头，完整持锁线程证据以 `array-runtime-16-final-threads.log` 为准。此次未发布新候选或同步 GitHub，A 的完成结论与交付范围不变。

## 2026-10-02 B/C 接收对象分析与类字节数组失败接续

第十四次在启动后约 38 秒观察到控制入口安装成功；94 秒实际线程现场为 `ExactTypes.merge → ASM Analyzer → ExternalReceiverFlow.calls`。本轮接收对象分析也改为在完整 BasicValue／接收对象集合／unknown 等同时复用不可变结果；不包含虚调用或接口调用的方法没有接收对象结果，故不再为它运行该分析。全部实际静态／特殊调用解析与来源图分析保留。Astra 只读复核未发现这批改动改变实际分派或丢失 unknown。

此次运行随后直接失败，JVM 报 `can't set byte array region` 及 premain 失败，以 1 退出。没有游戏效果结果。由于 libinstrument 会清除最初数组写异常，现仅在该原生写入失败路径输出阶段、异常类型和原消息，然后恢复同一原异常；未改变拒绝结果。Java ABI 39／Native ABI 34。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-receiver-flow-fix.log` | PASS：本批 Agent／Native 及开发包统一构建成功，17 秒；Bootstrap 未变，复用当前编译输出。 |
| `array-runtime-15.log`、`array-runtime-15-launch.log` | FAIL：首次异常是写入 Frame 类字节数组时触发 `ClassCircularityError: SourceMapBridge$Frame`，随后 JVM instrumentation 断言并以 1 退出。没有游戏内效果记录。 |

## 2026-10-02 B/C 数组批量策略与来源分析耗时修复

第十三次的真实线程现场分别进入类加载原生解压和 `Origins.merge → ASM Analyzer → expandGraph`。源码确认原生基本类型数组提交对每个元素重复捕获当前来源并进入完整授权；现保留原同一真实接收对象写锁、来源及写前／写后历史，在原生来源停止判断和控制／数组写权限通过后，沿既有 copy／fill 的 known 契约区分整段可写与逐元素策略。受保护／停止来源仍拒绝，已知载体仍核对每个提交值，没有用 native control 作用域跳过守卫。新查询入口已列入对外守卫入口，不能被算作内部控制写者。

实际来源分析改为：相等值或结果等同旧值时复用原不可变来源值；本次图内调用指令与摘要内容不变时复用该方法结果，缓存不跨图；整个已解析图的每个实际方法均没有 Module 来源和控制变更来源时，在核对指令／来源行长度后返回空集。非空来源仍走原固定点传播，已解析 EMPTY 调用与未解析调用仍区分，未用类名或受信任调用者推断空来源。Java ABI 39，Native ABI 34。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-install-flow-fix.log` | PASS：Agent／Bootstrap／Native 及开发包统一构建成功，19 秒。 |
| `array-runtime-14.log`、`array-runtime-14-launch.log`、`array-runtime-14-threads.log` | FAIL：约 38 秒已观察到控制入口安装成功；94 秒现场在接收对象类型合并。后续类字节数组提交失败，JVM 报 instrumentation 断言并以 1 退出，没有游戏内效果记录。未包含下一批接收对象分析修复或数组原异常输出。 |

## 2026-10-02 B/C 内部已加载类查询接续

第十二次首次记录 `INSTRUMENTATION_REMOVAL_AND_PUBLICATION_GUARD:true`，证明自身控制映像登记阶段已完成；后续安装仍未在原 360 秒内结束，脚本终止自建 JVM，未进入游戏内攻防动作。沿此前已确认的枚举结果逐元素进入 JNI 数组历史路径，内部安装、分派和整组查询统一改从认证 NativeControl 入口取得 JVMTI 本次实际已加载类集合，仅用原 JNI 构造自身新结果数组。普通查询保留数组类和 hidden 类，各原调用点仍取新快照；bootstrap 专用查询仍按真实加载器筛选并在 Java 中剔除 hidden 类。未加载被查类，未缓存空结果，未改应用 JNI 写入守卫。

未安装原生后端时保留原 Instrumentation 查询；原生查询失败继续抛错，不回退成成功。Native ABI 33 同时包含前一批删除查询内多余 control 计数作用域的修正，Java ABI 39 不变。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-loaded-query-fix.log` | PASS：本批 Agent／Bootstrap／Native 与开发包统一构建成功，17 秒；不代表游戏内效果通过。 |
| `array-runtime-13.log`、`array-runtime-13-launch.log`、`array-runtime-13-threads.log`、`array-runtime-13-late-threads.log` | FAIL：仍在原 360 秒内未完成安装，脚本终止自建 JVM。78 秒现场在控制类加载的原生解压；306 秒现场在来源值合并及真实调用图分析，后续记录 `INSTRUMENTATION_REMOVAL_AND_PUBLICATION_GUARD:true`。没有游戏内效果结果。 |

## 2026-10-02 B/C 内部 bootstrap 类快照接续

调用图查询改从认证的 NativeControl 入口取得 JVMTI 本次已加载类快照，以真实 GetClassLoader 结果筛选 bootstrap 类，仅通过原 JNI 函数构造这次新建的查询结果数组。Java 仍剔除 hidden 类，仍每图建立和释放快照；没有加载被查类、缓存不存在结果或接受外来数组。任一步枚举失败均抛错并清理局部引用，不将空／部分快照当作查询成功。普通应用 JNI 写入仍走原边界，其他 `loadedClasses` 调用仍保留。

新 native 入口使 Native ABI 升为 32；Java ABI 仍为 39。Astra 对具体入口和安装顺序只读复查，未发现类身份、快照失败处理的新增确定缺陷。源码随后移除了该纯查询中不需要的 control 计数作用域，仅保留对新结果的精确原 JNI 操作；最终修正归入后续 ABI 33 构建，不混入本次 ABI 32 运行结论。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-bootstrap-query-fix.log` | PASS：Agent／Bootstrap／Native 及开发包统一构建成功，18 秒，Native ABI 32；尚不表示游戏效果通过。 |
| `array-runtime-12.log`、`array-runtime-12-launch.log`、`array-runtime-12-threads.log` | FAIL：同一 raw-backing 场景仍在原 360 秒内未完成，脚本终止自建 JVM。153 秒现场在自身控制映像重变换，后续首次出现 `INSTRUMENTATION_REMOVAL_AND_PUBLICATION_GUARD:true`；未见游戏内效果记录。此次使用前述已构建包，尚不含删除多余 control 计数作用域的最后源码修正。 |

证据在 [evidence-20261002](evidence-20261002/)。未发布新候选或同步 GitHub，完整 B/C 尚未完成。

## 2026-10-02 B/C 数组登记安装耗时接续

继续沿第九次实际线程现场中的 `getAllLoadedClasses0 → JNI 单元素写入 → ExecutionFlow` 检查。源码确认每次写一个元素却扫描此前所有已登记槽，连续填充 N 个元素形成累计二次扫描；现按当前区间宽度与已有槽数选择直接索引或扫描，写入来源、版本及历史处理保持原语义。已有数组先查询实际载体记录，仅缺失时创建登记键；完整图像切片复用原不可变图像，未知且无已知模块的来源合并复用 UNKNOWN，去掉构造后再登记同一 Sources 的重复调用。

控制弱键与普通来源弱键分开回收。数组历史产生的临时控制对象现在只在原控制目录锁内清理；普通来源键继续使用原来源回收路径，避免每个控制键都对多张无关来源表尝试删除。新增回收队列已纳入控制保护。本批没有跳过 JNI 写入守卫，也没有删除未知来源或写前／写后的真实内容。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `array-runtime-10.log`、`array-runtime-10-launch.log` | FAIL：使用上一批最终控制授权修复包，仍在 360 秒安装时限内未完成；脚本终止自建 JVM。通过 JVM 启动选项请求 120 秒内建采样，但目标退出前没有生成 `install.jfr`，不宣称取得采样或证明全部耗时原因。文件待绑定 77 项，未进入游戏内攻防动作。 |
| `build-array-registration-fix.log` | PASS：本批数组槽刷新、重复登记与控制键回收修复后的 Bootstrap／开发包构建成功，14 秒。未改的 Native 复用已有 DLL。 |
| `array-runtime-11.log`、`array-runtime-11-launch.log`、`array-runtime-11-threads.log` | FAIL：仍未在原 360 秒内完成安装，脚本终止自建 JVM。137 秒现场处于文件入口重变换的 native 来源捕获，未进入游戏内攻防动作。源码修复不据此记为整体安装超时已解决。 |

A 的既有完成范围和交付候选不变。日志位于 [evidence-20261002](evidence-20261002/)，Java ABI 39／Native ABI 31；尚未发布新候选或同步 GitHub，完整 B/C 继续处理中。

## 2026-10-02 B/C 控制登记与集合底层数组修复

A 的 V1.5 完成结论与既有交付候选不变。本节继续处理同一 B/C 实际安装失败，以及审查发现的控制数据绕过；没有新增场景或运行无关测试。

第七次安装保留原异常后，确认原 `CONTROL_IMAGE_BINDING` 是 `CONTROL_CAPTURE_CAPACITY`：真实控制记录超过固定 2048 项后，后续映像登记被拒。已移除该截断，继续按实际对象身份去重；旧弱引用只读取一次，避免两次读取之间回收后将 null 入队。第八次运行不再出现该容量错误，但仍在 360 秒安装时限内未完成，不能记通过。

文件入口增加原失败位置的具体原因。第八次的 77 项均为 `UNRESOLVED_ENTRY`，没有报告方法布局不可用或重绑定发布错误；这些尚未解析入口继续保留待绑定状态，实际后续调用仍须由原 NativeMethodBind 路径接入。此记录不等于文件效果已通过。

Astra 只读复查确认并推动修复：`ControlChange` 的来源／旧分支数组、独立 `applied` 的两层来源数组漏登记；外部代码图集合的真实底层数组漏登记；集合扩容后新数组失去保护。现显式登记上述来源数组，并按本批真实容器字段登记底层数组。已控 HashMap／ArrayList／IdentityHashMap 在准确数组字段发布前鉴别真实入口和写者，未授权发布（含清空）被拒；合法新数组先登记。控制目录自身在原锁内识别当前真实 table，避免扩容期间递归插入自己。待接续队列改用已接入的 ArrayList，在原锁内取出并清空。

高频 Agent 控制登记改用既有弱身份目录，只处理本批根和实际底层数组；初始静态控制根与旧目录保护保留，不再每个 accepted 都重扫、重建全部旧弱引用。此处消除了确定的重复工作，尚不将整个旧安装超时归因于它。

复查新实现还确认两处问题并完成源码修正：控制目录持锁扩容时，实际控制桥的授权不再查询逻辑来源目录，而以已核实 Class／nest 及实际 Module 身份判定，去掉与来源目录扩容之间的反向取锁；当前 table 读取在安装时固化为已登记的 getter MethodHandle，不再长期依赖可被外部 `setAccessible(false)` 撤销权限的 Field 对象。这两项最终修正只完成构建，未重跑游戏。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `array-runtime-7.log`、`array-runtime-7-launch.log` | FAIL：确认 `CONTROL_CAPTURE_CAPACITY`；定位后主动结束自建隔离 JVM，退出码 4294967295 不是成功。 |
| `build-control-capacity-fix.log` | PASS：移除截断、单次弱引用读取与文件入口失败原因记录后的 Java／Native 开发包构建成功，18 秒。 |
| `array-runtime-8.log`、`array-runtime-8-launch.log`、`array-runtime-8-threads.log` | FAIL：容量错误未再出现；文件 77 项均为 `UNRESOLVED_ENTRY`。203 秒线程现场仍在自身控制映像重变换的接续回调；360 秒未完成，脚本保留日志并终止自建 JVM。未进入游戏内攻防动作。 |
| `build-control-ledger-fix.log` | PASS：数组登记、真实集合底层数组及增量登记修复后开发包构建成功，15 秒。Native 未改，复用当前已构建 DLL。 |
| `build-control-publication-fix.log` | PASS：补齐将底层数组置 null 时的写者鉴别后，受影响 Bootstrap／开发包构建成功，14 秒；其余未改部分按已有构建复用。 |
| `array-runtime-9.log`、`array-runtime-9-launch.log`、`array-runtime-9-threads.log` | FAIL：同一 raw-backing 场景在 360 秒内未完成安装，脚本终止自建 JVM。110 秒线程现场在 `getAllLoadedClasses0 → finishFieldMutation → ExecutionFlow` 数组历史登记；最终文件待绑定为 76 项。此次没有控制容量异常，也没有游戏内效果记录。为定位该实际安装耗时发出的 JFR.start 请求没有完成、没有取得采样；目标退出后，仅结束了该等待请求。 |
| `build-control-authority-fix.log` | PASS：控制桥实际身份授权及 table getter 固化后，受影响 Bootstrap／开发包构建成功，13 秒；未重跑游戏，未重复 Native 编译。 |

日志位于 [evidence-20261002](evidence-20261002/)。Java ABI 39／Native ABI 31，未发布新交付候选、未同步 GitHub，B/C 继续处理中。

## 2026-10-02 B/C 实际安装故障修复（第 1–6 次记录）

继续复用既有 `raw-backing` 隔离游戏夹具检查本批 Unsafe／数组保护路径，没有新增测试。以下运行均使用本轮开发包；A 的既有交付候选和完成范围不变。实际检查尚未进入游戏内攻防动作，不能记为效果通过。

已按实际失败路径修复：原生字段观察所需回调在安装时准备，避免首次代码映像出现前使用空的控制入口；Unsafe 写入和复制的在途栈改按真实 Thread 身份保存到线程外，仍核对同一线程／栈顶／锁，避免 JDK 清空 ThreadLocal 后无法收尾；Java 库加载织入前的原始 native load 保存当次真实接收对象，只允许原 java.dll 对该对象发布 handle／jniVersion；同次调用图分析复用一次真实 bootstrap 类快照，结束即释放；宿主进程入口检查使用只读 acquire load，真正替换仍在 VirtualProtect 可写窗口内执行比较交换。安装异常保留完整原因链，便于定位真实失效。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| `build-array-runtime.log` | PASS：构建开发 Core／Agent／Bootstrap 和既有夹具、前置入口，未生成交付候选。 |
| `array-runtime-1.log`、`array-runtime-1-launch.log` | FAIL：原生字段观察在回调尚未准备时触发 `ACTUAL_CODE_FIELD_BRIDGE_REQUIRED`。 |
| `build-native-init-fix.log` | PASS：原生回调安装顺序修复后重新构建。 |
| `array-runtime-2.log`、`array-runtime-2-launch.log` | FAIL：Native 安装接续成功；Common-Cleaner 清空线程本地数据后触发 `UNSAFE_MUTATION_THREAD_AND_ORDER_REQUIRED`，主安装另有未展开的初始化失败。 |
| `build-thread-scope-fix.log` | PASS：线程外在途栈和安装异常原因链修复后编译／开发包构建。 |
| `array-runtime-3.log`、`array-runtime-3-launch.log` | FAIL：线程清理异常未再出现；完整异常定位到 JDK 加载 jimage 时被 `RONOVA_NATIVE_LIBRARY_METADATA_WRITE_REFUSED` 误拦。 |
| `build-library-install-fix.log` | PASS：真实原生加载调用的安装衔接修复后重新构建。 |
| `array-runtime-4.log`、`array-runtime-4-launch.log`、`array-runtime-4-threads.log` | 未通过：已越过前述错误，安装仍耗时。55 秒线程栈停在每个类型查询都调用 `getAllLoadedClasses` 的路径；定位后主动结束该自建隔离 JVM，退出码 4294967295 不是自然成功退出。 |
| `build-class-lookup-fix.log` | PASS：同次分析复用真实 bootstrap 类快照后重新构建。 |
| `array-runtime-5.log`、`array-runtime-5-launch.log`、`array-runtime-5-threads.log` | FAIL：进入 `processBoundaryInstalled` 后 JVM 原生崩溃。原崩溃记录显示在只读 java.dll 导入表执行 `cmpxchg`；对应 `host_import` 的“读取”实际要求写权限。原 hs_err 保留在隔离运行目录。事后尝试 JFR 时进程已退出，未取得采样。 |
| `build-native-import-fix.log` | PASS：导入表的四处观察改为 acquire load，Native 与开发包重新构建成功。 |
| `array-runtime-6.log`、`array-runtime-6-launch.log`、`array-runtime-6-threads.log` | FAIL：已越过 `processPrepare0` 崩溃点，并报告 `NATIVE_THREAD_CURRENT_BINDINGS_READY`。仍有 `NATIVE_FILE_CURRENT_BINDINGS_PENDING:77`、多处 `CONTROL_IMAGE_BINDING` 和 `INSTRUMENTATION_REMOVAL_AND_PUBLICATION_GUARD:false`；360 秒未完成，保留日志并结束自建隔离 JVM。没有游戏内效果通过记录。 |
| `build-control-cause.log` | PASS：控制登记保留原始异常的 Agent／开发包重新构建成功；没有据此重新运行游戏或宣称剩余安装故障已修复。 |

控制映像登记的反射包装已改为保留实际运行异常／Error，避免只报告 `CONTROL_IMAGE_BINDING` 而丢失原因；此项尚未重新运行游戏。下一步继续定位控制登记的原始失败与未接通的文件原生入口。

上述日志位于 [evidence-20261002](evidence-20261002/)。接口仍为 Java ABI 39／Native ABI 31。本轮尚未发布新候选或同步 GitHub，完整 B/C 仍在继续。

## 2026-10-02 B/C 数组恢复源码审查与编译

用户要求本轮代码写完后先查 bug。本轮实际执行的是数组历史／来源／回填及复制异常收尾的源码审查和直接编译，未运行游戏、探针或其他测试。A 的完成结论、原候选和原效果记录保持原范围，以下结果不表示 B/C 已完成或效果通过。

数组恢复源码审查确认并修复多组停止后的旧值复活、较旧不完整历史阻塞后续完整写入恢复、并发读丢失已知来源；另修复异常复制的来源收尾、引用写入种类传递、来源数组漏登记保护。Astra 只读复查涵盖数组历史重建、JNI 字节快照与恢复、BufferRead／MemoryCopy 嵌套关系；没有修改或运行测试，也没有宣称覆盖整个 ABC。

| 实际执行／日志 | 结果与范围 |
| --- | --- |
| 本机 Gradle 包装器首次启动 | 下载被环境网络限制拒绝，未进入编译。随后改用本机已有 Gradle 8.8 和离线依赖。 |
| `compile-bc-array.log` | FAIL：定时任务反射代码使用了 Java 不允许的复合 `var` 声明，修复后继续。 |
| `compile-bc-array-fix1.log` | FAIL：Bootstrap／Agent 编译完成；Core 暴露重复的实体 vehicle 访问器和不存在的结构恢复方法名，均已修复。 |
| `compile-bc-array-fix2.log` | FAIL：Core 的来源守卫保留了原调用已不再抛出的受检异常捕获，已移除。 |
| `compile-bc-array-fix3.log` | PASS：Core／Agent／Bootstrap 编译成功。仍有 Mixin 映射与已弃用 API 警告，不据此宣称运行安装通过。 |
| `compile-bootstrap-array-final.log` | PASS：Unsafe／VarHandle 引用种类与复制异常收尾修复后的最终 Bootstrap 单独编译成功；未重复其他已通过检查。 |
| `compile-native-array.log` | PASS：本机 JDK 17 头文件与 Zig 0.13.0 成功编译实际控制 DLL，Native ABI 31。旧机编译器路径失效后使用官方编译器包，源码入口支持环境变量／本机命令／工程临时工具路径。 |

日志位于 [evidence-20261002](evidence-20261002/)。Core／Agent／Bootstrap 源码 ABI 39；本轮未生成或发布新候选，GitHub 未同步，游戏内数组恢复、并发复制与共享对象效果仍未验证。

## 2026-10-01 A 批最终收口（当前候选）

**A 在 V1.5 已声明范围内完成。** 当前主工程为 `C:\Users\Scorn\Desktop\MOD\Ronova-Pro-接续\ronova_GPT`，源码、夹具及本节当前实机场景使用同一最终 Java 候选。六种返回消费者检查在工厂归属修复前通过；后续改动只补已有创建观测和实体归属，不改返回生成器，未重复无关已通过检查。

### 修复与交付

同一实际 Module 的多个 `[[mods]]` 所有者自动合并；批量请求共用一次真实已加载类枚举，单组失败继续处理有效组。逐组状态保留未控位置，部分覆盖报告 `STOPPED_PARTIAL`。普通方法按描述符提供六种策略，普通业务构造在合法异常入口拒绝创建，不执行原前缀；Forge `@Mod` 承载实例保留必要父类初始化，无法控制的前缀仍报缺口。新类转换失败时拒绝该定义，已加载类改写失败保持未控记录。

目标工厂直接创建原版 Cow／ItemEntity 的实现类属于 Minecraft，旧类模块筛选会漏清。本轮沿已认证的实际创建调用点保存弱身份归属，当前／后续实体清除、防护和准入使用实际创建 Module；自定义 Item 掉落物亦沿真实 Item 实现归属。未用实体名称、命名空间或夹具回报替代生产归属。

`returns` 支持 default、null、empty、uuid-fixed、uuid-each、invalid-id；UUID／String 查询扰动与真实标识重写分开，后者本轮未新增。提供标准 Future 停用结果时它已完成；null 策略不新建 Future，不发布挂起生产。基础同步协议为 `r3-client-5`，本地客户端必须允许共享模块所有 modid。

交付目录：[candidate-modwide-a-complete-20261001](../../distribution/candidate-modwide-a-complete-20261001/)。Core 为 866,976 字节，前置入口为 5,905 字节，SHA-256 见该目录 SHA256SUMS.txt。普通安装只放 Core 入 mods，前置入口按 README 接入实际启动器；独立 Agent／Bootstrap 已嵌入主包。Core／Agent／Bootstrap ABI 25，持久格式未变。Native 源码未改，复用已有控制 DLL，构建使用 `-x compileControlNative`，不宣称 Native 重建。

### 构建与实际效果

| 既有场景／摘要日志 | 实际结果 |
| --- | --- |
| `build-a-complete-20261001.log` | 完整返回／分组／同步代码集中构建成功，42 秒，24 项任务。 |
| `build-a-factory-20261001.log` | 工厂归属工作包写齐后 jar／fixtureJar／prelaunchJar 集中构建成功，35 秒。 |
| `return-effects-*.log`（六份） | 复用 BoundaryCheck 的真实 ModuleLayer 与夹具实际 mods.toml，模块外消费者在 `-Xverify:all` 下调用。六策略均有 MOD_GROUP_RETURN_EFFECTS_PASS，基本类型／void 无业务效果，引用和数组合法，固定／按次 UUID 与 int=-1 合同成立，标准空消费者可用，Future 消费不挂起，构造拒绝前缀执行，原始 effects=0，组外 ArrayList 正常。 |
| `dynamic-a-factory-final2.log` | BODIES_CLEARED=4、BLOCKS_QUEUED=1、BODY_FAILURES=0。before=60、frozen=140、after=140；隐藏事件委托 before=121、frozen=283、after=283。MOD_COMMAND_CALLBACK_STOPPED、TARGET_BODY_SELECTOR_ABSENT、KNOWN_FACTORY_VANILLA_BODIES_CLEARED、WORLD_COMMAND_RESPONDED、SHARED_MODULE_ALIAS_INCLUDED、SERVER_EXIT=0。missing_mod 与实际隐藏缺口合计 GROUP_FAILURES=2，不将其读成全组绝对成功。 |
| `protect-a-factory-final.log` | BLOCK_HOLDER_MAP_FIELD_PROTECTED、CURRENT_AND_FUTURE_LIVING_AND_NONLIVING_PROTECTED、CURRENT_AND_FUTURE_KNOWN_FACTORY_VANILLA_BODIES_PROTECTED、WORLD_COMMAND_RESPONDED、SHARED_MODULE_ALIAS_INCLUDED、SERVER_EXIT=0。Map／字段拒写直接核对准确原值，无关条目可清。当前／后续原版 Cow 和 ItemEntity 经 /kill 后仍由选择器找到；自定义对象 removed=null、indexed=true，未来 registered=true、bound=true、conflict=false、callback=真实 PersistentEntitySectionManager$Callback，blockHolder=true。 |
| `boot-a-thread.log` | BOOT_TARGET_METHODS_DID_NOT_EXECUTE、WORLD_COMMAND_RESPONDED、SHARED_MODULE_ALIAS_INCLUDED、SERVER_EXIT=0。TARGETS 含 pro_fixture／pro_fixture_alias，METHODS=16、UNRESOLVED=0，事件和命令门已接入。原 15 秒命令时限内通过。 |
| `client-a-final.log` | CLIENT_JOINED；CLIENT_EFFECT_COUNTER before=160 frozen=180 after=180；CLIENT_OUTSIDE_CHAT_PROCESSED；REMOTE_ALLOWED_CLIENT_MOD_GROUP_STOPPED_AND_GAME_ALIVE；SERVER_EXIT=0。真实客户端用同包、前置入口及 allow_remote_stop=pro_fixture,pro_fixture_alias，接受 returns=empty 后回调停止，普通聊天继续到达。随后由脚本清理客户端，不记客户端正常退出。 |

摘要日志位于本报告旁 [evidence-20261001](evidence-20261001/)。原始 launch.log、字段状态和存档保留在本地 `MOD/.work/pro-a-final-20260928/runtime/` 对应场景，公开仓库不上传游戏存档和依赖。沿用现有 `run-group-effect.py`、`run-client-group-effect.py`；本机 JAVA_HOME 指向 JDK 17，客户端游戏依赖在 stage/tools/forge-client-game，资源使用已有 ForgeGradle assets。

### 未控路径与保留失败

- 动态服务端 FAILED=7／UNRESOLVED=7，客户端 FAILED=6／UNRESOLVED=6，均为真实不可重变换隐藏类。已登记事件与命令委托实际被阻断，但其他隐藏使用点不冒充全控。完整 B 来源／私有池／资源／持久链、完整 C 动态定义／Native／Host／网络／视觉及原第三方样本本轮未验。
- `boot-a-complete.log` 与 `boot-a-final.log`：世界 Done 后原 15 秒命令等待超时，整个场景 FAIL；命令随后落日志，服务端 exit 0 不替代效果通过。复查 `boot-a-thread` 在原时限内成功，新增的超时线程采集未触发，前两次慢点仍没有当时栈，不能说根因已修。旧 protect10 历史根因也仍未证明。
- `dynamic-a-factory-final.log`：受限环境中 Minecraft 对隔离工作目录的真实路径访问被拒，未进入世界，场景 FAIL。获准本机运行后的 `dynamic-a-factory-final2` 通过，生产代码没有为该环境错误改动。
- `dynamic-a-complete.log` 与 `protect-a-complete.log` 是工厂归属修复前的通过结果，只包含原自定义对象；本节当前清除／防护结论来自后续最终候选场景，不能拿前次两实体结果证明工厂全量纳入。

本次实机环境：Windows x64、Eclipse Adoptium Java 17.0.19+10、Gradle 8.8、Minecraft 1.20.1／Forge 47.4.23；编译映射 Forge 47.4.22。所有当前进度统一维护在 docs/A-批验收状态.md，以下内容仅保留历史。

## 同日此前的换机接续修复

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



