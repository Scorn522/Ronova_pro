# Ronova Host Windows 驱动源码

此目录交付独立 Windows x64 内核源码和共享 IOCTL 头。本轮用户态接线已集成主工程源码：Java ABI 54 / Native ABI 51。驱动仍为独立 WDK C17 源码，尚未编译、安装、加载或签名；没有生成证书或修改 TestSigning。

`RonovaHost.vcxproj` 使用 Visual Studio 2022 C++ 工具、匹配的 Windows SDK/WDK 和 `WindowsKernelModeDriver10.0`；目标 Windows 10 2004（19041）及以后，内核 C17，链接 FltMgr/Wdmsec/NtosKrnl，设置 `/INTEGRITYCHECK`。源码与同包用户态接线全部写齐后，才由工程的集中构建步骤编译。当前机器的标准 Windows Kits Include/build 与 Visual Studio 2022 目录缺失，尚未取得 WDK 编译或内核运行结果。

## 实际规则

- `\\.\RonovaHost` 控制设备 ACL 为 SYSTEM/管理员；每个实际设备 FILE_OBJECT 只绑定打开时的原始进程。所有 IOCTL 必须来自同一实际进程和句柄表，拒绝 WOW64、kernel requestor、陌生会话与不准确结构长度。
- PROCESS_ADD 用 UserMode、`*PsProcessType` 和查询/设置权限验证真实 HANDLE，保留 PEPROCESS 引用；拒绝会话自己的 JVM/controller 和 System 对象。opaque Scope 只表示现有 Host 记录，不授予内核权限，也不按 PID、modid、线程名或名称匹配。
- ObRegisterCallbacks 在实际 process/thread handle 创建与复制时，移除微软允许修改的危险访问位。复制按实际接收进程判断；controller 和目标自身只获得各自对象的例外。已有句柄、继承、只读权限和 kernel handles 保留缺口。
- PsSetCreateProcessNotifyRoutineEx 在真实创建线程上下文中检查当前创建者的 PEPROCESS；选定对象的后续子进程通过 CreationStatus 被拒绝。退出通知撤销原对象的效力，不沿 PID 复用迁移。此通知也是 session owner/actor 生命周期的必要依赖；Ob 单独注册成功不会宣称整个进程策略可用。
- FILE_ADD 必须绑定同一会话、同一 Scope 的真实进程 TargetId。UserMode、`*IoFileObjectType` 验证原打开文件，保留 FileObject、kernel keep-open HANDLE、实际 Volume/Instance 和 128 位 FileIdInformation；只对该实际 actor 对象访问这一文件的规则生效。只接 NTFS/ReFS 的已安装实例和普通文件。文件自身及同 ID 的新别名经过真实 read/write 和适用元数据写 preoperation 拒绝；不能解析 ID 的旧别名保留缺口。
- 对新的 FILE_EXECUTE open，在实际 post-create 查询 volume/file ID，调用 FltCancelFileOpen，在用户句柄生成前拒绝。已经发生的创建/截断副作用不能回滚。既有 section/mapping、paging 和 kernel I/O 未服务；section 同步只记录实际尝试，不伪造 STATUS_INSUFFICIENT_RESOURCES，也不使用 image-load 通知冒充加载拒绝。

## 协议和生命周期

以 `ronova_host_protocol.h` 的固定结构为准：请求头32、响应头32、session reply88、process add64、file add72、target request48、target reply152字节。METHOD_BUFFERED IOCTL 要求 read/write access，输入和输出长度必须准确。合法 transport 的真实动作结果放在响应 Status（NTSTATUS）；transport 失败时没有可使用的回执。HELLO 的 Ob、Ps、FltRegister、FltStart 状态独立来自实际调用，Capabilities 只发布已安装且依赖可用的分支。

同会话同对象同 Scope 的 ADD 原子替换 Effects 并保留 TargetId；同对象不同 Scope 拒绝。移除实际 process 前先移除该会话绑定的文件，否则返回 STATUS_DEVICE_BUSY。REMOVE 只撤销本会话登记对象。CLEANUP 立即关闭该会话发布、等待在途 IOCTL，释放其实际文件与进程引用；CLOSE 释放会话本身。没有终止进程、读写任意内存、按路径重新打开目标或在 kernel 中自动扩大来源的 IOCTL。

关闭实际 device 会话后，通过公开非强制 FilterUnload/FltUnloadFilter 路径卸载。活跃会话返回 STATUS_FLT_DO_NOT_DETACH；FLTFL_REGISTRATION_DO_NOT_SUPPORT_SERVICE_STOP 明确禁用 service stop 的强制卸载。实际 unload 关闭新发布、等待 rundown，注销 Ob/Ps/Flt 回调、释放 altitude 与 device。session 和 target 回执只在原设备会话有效；用户态须结合原观察 token 和保留的真实 HANDLE 校验，设备重开不能复用旧回执。

## 外部部署条件与未服务范围

`RonovaHost.inf` 是源码配套配置，ObAltitude 和 FilterAltitude 尚未分配，保持空值。后续部署需要实际合适的 Ob altitude、微软分配的 Security Enhancer minifilter altitude、Windows 接受的驱动签名和真实系统授权；源码不会挑另一个数字跳过冲突。空配置、签名/完整性失败、altitude 冲突、无实例或不支持的文件系统会返回真实 NTSTATUS。CAT/.sys 签名、安装、升级和发行卸载不在此次源码交付中执行。

本版本不具有单个 Java Mod 的内核执行身份；共享 JVM 内的模块必须继续由现有 Java/Native 来源截点处理。用户态只应提交已证明专属的实际 Host 子进程与已授权文件，unknown/shared/transfer gaps 不能转成内核目标。它未提供完整 Windows 映像准入、已有映射清除、旧句柄权限追溯、任意内核来源阻断或全 OS 完成结论。

依据微软正式 DDI：

- [ObReferenceObjectByHandle](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/nf-wdm-obreferenceobjectbyhandle)、[ObRegisterCallbacks](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/nf-wdm-obregistercallbacks)、[可修改的 handle 访问位](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/ns-wdm-_ob_pre_create_handle_information)。
- [PsSetCreateProcessNotifyRoutineEx](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/ntddk/nf-ntddk-pssetcreateprocessnotifyroutineex)、[FltQueryInformationFile](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/fltkernel/nf-fltkernel-fltqueryinformationfile)、[FltCancelFileOpen](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/fltkernel/nf-fltkernel-fltcancelfileopen)。
- [section 同步允许的失败](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/flt-parameters-for-irp-mj-acquire-for-section-synchronization)、[卸载回调的触发方式](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/when-the-filterunloadcallback-routine-is-called)、[minifilter INF](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/creating-an-inf-file-for-a-minifilter-driver)、[altitude 分配](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/load-order-groups-and-altitudes-for-minifilter-drivers)。
