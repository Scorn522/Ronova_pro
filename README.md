# Ronova Pro

主工程：`D:\桌面\ronova_GPT`。当前开发方向以 [V1.5 设计方案](docs/design/Ronova-Pro-设计方案-V1.5.md) 为准：实际文件／Module 归组，整 Mod 停用、保护与存量清理。

- 控制链为游戏 JVM 创建前的 prelaunch 参数交接、main 前 premain、Bootstrap、JVMTI／Native。晚附加单独报告为运行期接续。
- `/ronova_pro mod stop|protect|status <modid[,modid...]>` 展开同一实际文件的别名；共享 ClassLoader 不作为整组依据。停止策略先发布，随后遍历全方法生成合法入口返回，并共用限额清理实体、BlockEntity、订阅、任务和恢复引用。
- 本地选集保存在游戏目录的 `ronova-prelaunch.properties`，下次引导自动读取。远端服务器的客户端规则须由本机预先授权；客户端实际结果与服务器结果分别报告。
- Native 活动路径只保留整组控制、JNI／JVMTI、NativeMethodBind、关键线程／句柄和控制范围边界。CRT／STL／第三方分配器字段账本、合作式 allocator/object SDK 和私有字节恢复已退出活动构建。Storage 使用直接 RAII、真实 NTFS 身份与 TxF 事务。
- 不恢复 JDK 容器广谱 Hook，不安装 getter／Function 组合的见证链。已知恢复载体按实际 backing 身份／CAS 清理；未知私有语义和逃逸调用继续明确报告未覆盖。

Java 17；用户态 GNU C23／GNU C++23；Windows 驱动源码与管理工具 C17。内部兼容接口仍为 Java ABI105／Native ABI99／Storage3／Journal6；客户端协议改为 `v1.5-whole-mod`。接口数字不代表能力完成比例。

当前工作包先写齐生产源码再统一编译，只检查真实整 Mod 效果；不运行 `validation/checks` 自证检查或独立 C++ 消费者夹具。构建与实际效果结论见 [当前状态](docs/A-批验收状态.md) 和 [实际报告](validation/b-final-20261007/REPORT.md)，旧结果不覆盖本版。

对应候选：[Core](distribution/v1.5-whole-mod/ronova-pro-core-1.5.0-dev.jar)、[prelaunch](distribution/v1.5-whole-mod/ronova-pro-prelaunch.jar)。候选构建成功与实际世界效果分别按状态页记录。

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
