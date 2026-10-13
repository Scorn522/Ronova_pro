# Ronova Host Windows 驱动源码

此目录交付 Windows x64 内核源码、共享 IOCTL 头、集中 WDK 构建入口和显式开发部署工具。本轮用户态接线已集成主工程源码：Java ABI 56 / Native ABI 54。驱动与部署工具（含正常 Windows UAC 请求、共用 manifest 资源和新文件 open 准入）已用官方 WDK/SDK NuGet 10.0.26100.6584 头库及 Zig/LLD 实际编译链接，生成 36352 字节 SYS 与 188928 字节 EXE，正式 VS/WDK MSBuild 工程尚未执行。未安装、加载或签名，没有生成证书或修改 TestSigning；下面的部署命令未执行，模板两 altitude 仍待真实配置。

`RonovaHost.vcxproj` 使用 Visual Studio 2022 C++ 工具、匹配的 Windows SDK/WDK 与 VS WDK 扩展、`WindowsKernelModeDriver10.0`；目标 Windows 10 2004（19041）及以后，内核 C17，链接 FltMgr/Wdmsec/NtosKrnl，设置 `/INTEGRITYCHECK`。`RonovaHostManage.vcxproj` 是 x64 C17 Win32 控制台工具，链接 Newdev/Setupapi/FltLib/Advapi32/Shell32/Ole32。启动按 `asInvoker` 使用调用者当前权限，只有用户选择修改命令后才按实际 token 决定是否请求 UAC。`RonovaHost.proj` 将两者放在同一次集中构建中。

## 构建与配置包

先安装配套 VS 2022 C++、Windows SDK、WDK 和 VS WDK 扩展。源码与同包用户态接线写齐后，再运行一次集中构建。`-KitVersion` 必须是实际安装、相互匹配的 SDK/WDK 完整版本，不自动更换工具链：

```powershell
.\Build-RonovaHost.ps1 -KitVersion 10.0.26100.0 -Configuration Release
```

示例版本须替换成实际工具链版本；可用 `-MSBuildPath` 指定 VS 2022 的 MSBuild，`-KitsRoot` 指定 Windows Kits 根目录，`-OutputRoot` 指定输出目录。默认输出是 `out\x64\Release\kernel\RonovaHost.sys` 与 `out\x64\Release\tools\RonovaHostManage.exe`。构建没有安装/加载副作用，也不生成签名。

管理工具的 manifest 从 `RonovaHostManage.rc` 作为 ID 1、类型 24（`RT_MANIFEST`）资源嵌入，包含显式 `asInvoker`、`uiAccess=false` 和 Windows 10/11 的 `supportedOS`。MSVC 工程直接编译该 RC，关闭 linker 生成和再次嵌入 manifest，避免同一资源重复。直接 Zig/LLD 构建同样必须编译并链接这个资源；只给 linker 传 C 文件会丢失 OS 声明，导致 `VerifyVersionInfoW` 在实际 Windows 10/11 上误判。复用已有 SDK/WDK 编译参数时，资源步骤为：

```powershell
# driverSource / manifestResource 是本源码目录和本次输出 .res 的完整路径。
& $ZigPath rc "/I$driverSource" "/fo$manifestResource" (Join-Path $driverSource 'RonovaHostManage.rc')
# 把 $manifestResource 一并传给管理工具的现有 Zig/LLD 链接调用，
# 并链接 Windows SDK 的 Shell32.lib、Ole32.lib；不另生成 ID 1 manifest。
```

该资源和 `asInvoker` 不会在查询、帮助或程序启动时请求管理员权限；正常 UAC 由选定动作的 `runas` 调用请求。

只编译无需填写 altitude。要生成可配置的包，给 `-ObAltitude` 和 `-FilterAltitude` 传入实际取得的部署高度；两者必须同时给出。源码模板空值不改变。`-DriverVersion` 是四段整数，每段 0–65535；升级需要比当前版本大，`-DriverDate` 不早于当前包。可选 `-GenerateCatalog` 调用所选 WDK 的 Inf2Cat，并要求明确的 `-CatalogOs`；`-Inf2CatPath` 可覆盖实际工具路径：

```powershell
$buildParameters = @{
    KitVersion = $installedKitVersion
    ObAltitude = $assignedObAltitude
    FilterAltitude = $assignedFilterAltitude
    DriverVersion = '1.0.1.0'
    DriverDate = [datetime]'2026-10-13'
    GenerateCatalog = $true
    CatalogOs = $actualInf2CatTargetList
}
.\Build-RonovaHost.ps1 @buildParameters
```

上述变量由部署者给出实际值。配置包写入 `out\x64\Release\package\`，包括 SYS、配置后的 INF 和选生成的 CAT。重新编包先移除旧 CAT，避免沿用旧文件的目录签名。输出仍未签名；须在外部取得 Windows 接受的 SYS/CAT 签名。后续不得改已签名 INF 或 SYS，否则需要重新生成 CAT、重新签名。

## 显式安装、查询与生命周期

部署工具仅接受固定 RonovaHost primitive 包：原始 INF 名、Provider、Class/ClassGuid、service 名、DriverStore DIRID 13、demand start 与两项已配置高度。用户在 x64 终端明确选择 `install`、`load`、`unload`、`upgrade` 或 `remove`，命令和参数解析通过后，工具复用实际 elevated token；当前 token 未提权时，使用本程序真实 EXE 路径向 Windows 请求正常 UAC 同意或管理员凭据，只有获授权的子进程继续执行原动作。`query`、帮助和无效命令不请求 UAC；普通用户查询受设备 ACL 限制时保留实际拒绝结果。工具不生成证书，不修改 TestSigning，不自动重启，没有 game 自动调用或开机启动入口。

```powershell
$manage = '.\out\x64\Release\tools\RonovaHostManage.exe'
& $manage query
& $manage install --package 'D:\RonovaDeployment\RonovaHost.inf'
& $manage load
& $manage query
& $manage unload --timeout-ms 10000
```

`install` 使用公开 `DiInstallDriverW(..., Flags=0, NeedReboot)` 处理 primitive 包，不使用强制版本覆盖，不启动驱动。`load` 才通过 `FilterLoad` 显式加载。查询读取当前 token 的实际 elevated/SeLoadDriverPrivilege、SCM 的只读配置与真实状态、DriverStore/OEM INF 和实际设备 HELLO 的 ABI、回调状态、Capabilities/Unserved；它创建的临时设备会话随即关闭，打印的会话目标数只属于该临时会话，不冒充全局活跃会话数。

UAC 重新启动只用 `GetModuleFileNameW(NULL)` 得到的当前工具路径，逐项按 Windows CRT 规则重新引用已解析参数，不接受另一个可执行程序、服务名或任意执行命令。内部 `--elevated-child` 只防止重复请求，不能替代实际 token 提权；获授权的子进程仍执行全部固定包、SCM、DriverStore 和文件身份检查。父进程请求复用原控制台并等待实际 child process 退出，输出 `ChildExitCode`、原样传回其退出码；UAC 同意只代表启动子进程，不能代表安装或加载成功。

升级与移除必须给出查询显示的当前 `InstalledInf` 完整路径，例如实际的 `C:\Windows\INF\oem42.inf`。这个名字不能照抄示例或由猜测生成。工具把该 OEM INF、DriverStore INF 和当前 service image 绑定到实际文件身份，拒绝不对应当前 RonovaHost 的包：

```powershell
& $manage upgrade --package 'D:\RonovaDeployment-vNext\RonovaHost.inf' --installed-inf $actualInstalledInf --timeout-ms 10000
# 只有明确传 --reload 才在成功升级且无需重启时加载新包。
& $manage upgrade --package 'D:\RonovaDeployment-vNext\RonovaHost.inf' --installed-inf $actualInstalledInf --reload
& $manage remove --installed-inf $actualInstalledInf --timeout-ms 10000
```

升级先读取并保留新包 INF/SYS/CAT，确认新 DriverVer，再通过 `FilterUnload` 退役旧实例；活跃设备会话被内核拒绝，升级立即停止。卸载成功后重新读取同一实际安装包和 stopped 状态，才提交安装/移除。新包安装失败保留旧包身份与已卸载结果，不隐式重启旧包；可以根据实际状态另行执行 `load`。升级不会自动清除其他旧 DriverStore 包；`remove` 只移除当前匹配的包，并检查实际 service 是否已消失，不手删 registry/DriverStore 文件。

`unload` 与升级/移除的卸载阶段仅走公开 `FilterUnload`（用户态 `FltUnloadFilter` 对应入口），绝不调用 SCM stop、强制 stop 或 `FilterDetachAtAltitude`。只在本次动作期间启用当前 token 已有的 SeLoadDriverPrivilege，并在结束时恢复。原始 Win32/HRESULT/NTSTATUS 和失败阶段均保留输出。`--timeout-ms` 只等待卸载后的真实 stopped 状态，范围 1–60000ms，默认 10000ms，不会绕过活跃会话。

退出码 `0` 表示该命令完成，`1` 表示输出中对应阶段失败，`1223` 表示用户取消 UAC；其他 UAC 启动失败保留实际 Win32 错误码和阶段。子进程的 `3010` 同样传回，表示 Windows 返回需要重启；安装/升级遇到此结果不接着加载，移除遇到此结果显示待重启，不宣称已经完成。实际 restart 由部署者安排。

## 实际规则

- `\\.\RonovaHost` 控制设备 ACL 为 SYSTEM/管理员；每个实际设备 FILE_OBJECT 只绑定打开时的原始进程。所有 IOCTL 必须来自同一实际进程和句柄表，拒绝 WOW64、kernel requestor、陌生会话与不准确结构长度。
- PROCESS_ADD 用 UserMode、`*PsProcessType` 和查询/设置权限验证真实 HANDLE，保留 PEPROCESS 引用；拒绝会话自己的 JVM/controller 和 System 对象。opaque Scope 只表示现有 Host 记录，不授予内核权限，也不按 PID、modid、线程名或名称匹配。
- ObRegisterCallbacks 在实际 process/thread handle 创建与复制时，移除微软允许修改的危险访问位。复制按实际接收进程判断；controller 和目标自身只获得各自对象的例外。已有句柄、继承、只读权限和 kernel handles 保留缺口。
- PsSetCreateProcessNotifyRoutineEx 在真实创建线程上下文中检查当前创建者的 PEPROCESS；选定对象的后续子进程通过 CreationStatus 被拒绝。退出通知撤销原对象的效力，不沿 PID 复用迁移。此通知也是 session owner/actor 生命周期的必要依赖；Ob 单独注册成功不会宣称整个进程策略可用。
- FILE_ADD 必须绑定同一会话、同一 Scope 的真实进程 TargetId。UserMode、`*IoFileObjectType` 验证原打开文件，保留 FileObject、kernel keep-open HANDLE、实际 Volume/Instance 和 128 位 FileIdInformation；只对该实际 actor 对象访问这一文件的规则生效。只接 NTFS/ReFS 的已安装实例和普通文件。文件自身及同 ID 的新别名经过真实 read/write 和适用元数据写 preoperation 拒绝；不能解析 ID 的旧别名保留缺口。
- 新用户 open 在实际 post-create 查询 volume/file ID，按同一 actor 的既有 DENY_READ、DENY_WRITE、DENY_NEW_EXECUTE_OPEN 规则，在句柄产生前调用 FltCancelFileOpen；read 对应 FILE_READ_DATA，write 包含 FILE_WRITE_DATA/FILE_APPEND_DATA，execute 对应 FILE_EXECUTE，generic rights 使用公开文件 access mapping。这阻断从被拒绝的新 backing-file handle 建立 data/image section 的前置路径。MAXIMUM_ALLOWED 只使用公开 AccessState 已完成的具体 grant；状态无法解析或 FO_HANDLE_CREATED 已存在时，对该准确对象返回 OPEN_ADMISSION_UNAVAILABLE gap，继续保留后续普通 I/O 规则。已经发生的创建/截断副作用不能回滚，已有、复制或继承的 file/section handle、现存 view、paging 和 kernel I/O 仍未服务；section 同步只记录实际尝试，不伪造 STATUS_INSUFFICIENT_RESOURCES，也不使用 image-load 通知冒充加载拒绝。

## 协议和生命周期

以 `ronova_host_protocol.h` 的固定结构为准：请求头32、响应头32、session reply88、process add64、file add72、target request48、target reply152字节。METHOD_BUFFERED IOCTL 要求 read/write access，输入和输出长度必须准确。合法 transport 的真实动作结果放在响应 Status（NTSTATUS）；transport 失败时没有可使用的回执。HELLO 的 Ob、Ps、FltRegister、FltStart 状态独立来自实际调用，Capabilities 只发布已安装且依赖可用的分支。

同会话同对象同 Scope 的 ADD 原子替换 Effects 并保留 TargetId；同对象不同 Scope 拒绝。移除实际 process 前先移除该会话绑定的文件，否则返回 STATUS_DEVICE_BUSY。REMOVE 只撤销本会话登记对象。CLEANUP 立即关闭该会话发布、等待在途 IOCTL，释放其实际文件与进程引用；CLOSE 释放会话本身。没有终止进程、读写任意内存、按路径重新打开目标或在 kernel 中自动扩大来源的 IOCTL。

关闭实际 device 会话后，通过公开非强制 FilterUnload/FltUnloadFilter 路径卸载。活跃会话返回 STATUS_FLT_DO_NOT_DETACH；FLTFL_REGISTRATION_DO_NOT_SUPPORT_SERVICE_STOP 明确禁用 service stop 的强制卸载。Filter altitude、FltRegisterFilter 或 FltStartFiltering 失败时，DriverEntry 保留原 NTSTATUS 并回滚，不发布一个缺失公开正常卸载入口的控制设备；Ob 状态仍按实际注册结果独立发布。实际 unload 关闭新发布、等待 rundown，注销 Ob/Ps/Flt 回调、释放 altitude 与 device。session 和 target 回执只在原设备会话有效；用户态须结合原观察 token 和保留的真实 HANDLE 校验，设备重开不能复用旧回执。

## 外部部署条件与未服务范围

`RonovaHost.inf` 是源码配套模板，ObAltitude 和 FilterAltitude 尚未分配，保持空值。后续部署需要实际合适的 Ob altitude、微软分配的 Security Enhancer minifilter altitude、Windows 接受的驱动签名和真实系统授权；源码不会挑另一个数字跳过冲突。空配置、签名/完整性失败、altitude 冲突、无实例或不支持的文件系统会返回真实错误。此次交付编写了安装、加载、查询、非强制卸载、升级和移除的调用路径，但未执行这些系统动作；官方 WDK/SDK 头库的 Zig/LLD 编译链接已通过；正式 WDK MSBuild、Inf2Cat 和签名未执行，不具有 C28 内核运行通过证据。

本版本不具有单个 Java Mod 的内核执行身份；共享 JVM 内的模块必须继续由现有 Java/Native 来源截点处理。用户态只应提交已证明专属的实际 Host 子进程与已授权文件，unknown/shared/transfer gaps 不能转成内核目标。它未提供完整 Windows 映像准入、已有映射清除、旧句柄权限追溯、任意内核来源阻断或全 OS 完成结论。

依据微软正式 DDI：

- [ObReferenceObjectByHandle](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/nf-wdm-obreferenceobjectbyhandle)、[ObRegisterCallbacks](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/nf-wdm-obregistercallbacks)、[可修改的 handle 访问位](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/ns-wdm-_ob_pre_create_handle_information)。
- [PsSetCreateProcessNotifyRoutineEx](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/ntddk/nf-ntddk-pssetcreateprocessnotifyroutineex)、[FltQueryInformationFile](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/fltkernel/nf-fltkernel-fltqueryinformationfile)、[FltCancelFileOpen](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/fltkernel/nf-fltkernel-fltcancelfileopen)。
- [公开文件 generic access mapping](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/ntddk/nf-ntddk-iogetfileobjectgenericmapping)、[IRP create 安全上下文](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/ns-wdm-_io_security_context)、[已授予和剩余 access state](https://learn.microsoft.com/en-us/windows-hardware/drivers/ddi/wdm/ns-wdm-_access_state)、[CreateFileMapping 对文件句柄权限的要求](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-createfilemappinga)。
- [section 同步允许的失败](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/flt-parameters-for-irp-mj-acquire-for-section-synchronization)、[卸载回调的触发方式](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/when-the-filterunloadcallback-routine-is-called)、[minifilter INF](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/creating-an-inf-file-for-a-minifilter-driver)、[altitude 分配](https://learn.microsoft.com/en-us/windows-hardware/drivers/ifs/load-order-groups-and-altitudes-for-minifilter-drivers)。
- [Primitive driver 的公开安装与移除](https://learn.microsoft.com/en-us/windows-hardware/drivers/develop/creating-a-primitive-driver)、[DiInstallDriverW](https://learn.microsoft.com/en-us/windows/win32/api/newdev/nf-newdev-diinstalldriverw)、[DiUninstallDriverW](https://learn.microsoft.com/en-us/windows/win32/api/newdev/nf-newdev-diuninstalldriverw)。
- [FilterUnload](https://learn.microsoft.com/en-us/windows/win32/api/fltuser/nf-fltuser-filterunload)、[SetupGetInfDriverStoreLocationW](https://learn.microsoft.com/en-us/windows/win32/api/setupapi/nf-setupapi-setupgetinfdriverstorelocationw)、[SetupGetInfPublishedNameW](https://learn.microsoft.com/en-us/windows/win32/api/setupapi/nf-setupapi-setupgetinfpublishednamew)。
- [ShellExecuteExW](https://learn.microsoft.com/en-us/windows/win32/api/shellapi/nf-shellapi-shellexecuteexw)、[`runas` 和真实进程句柄](https://learn.microsoft.com/en-us/windows/win32/api/shellapi/ns-shellapi-shellexecuteinfow)、[Windows CRT 参数引用](https://learn.microsoft.com/en-us/cpp/c-language/parsing-c-command-line-arguments)、[application manifest](https://learn.microsoft.com/en-us/windows/win32/sbscs/application-manifests)；[Zig 的 RC/RES 支持](https://ziglang.org/download/0.12.0/release-notes.html#Windows-Resources)。
