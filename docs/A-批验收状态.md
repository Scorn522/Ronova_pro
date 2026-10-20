# Ronova Pro 当前进度

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
