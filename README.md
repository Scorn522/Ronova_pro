# Ronova Pro

主工程：`D:\桌面\ronova_GPT`。Java ABI68 / Native ABI64 / Storage3 / Journal6。
Java 17；用户态 GNU C23 / GNU C++23；独立 Windows 驱动及管理工具 C17。
当前包构建通过；补齐实际绘制队列的逐次来源交接、控制线程句柄别名保护及冻结区域的方块事件拦截，受影响的普通原生线程恢复/退出检查通过。这三项的客户端、私有线程别名和活塞实机案例仍未运行。完整 C/B 尚未完成，真实冻结保存的结果与剩余范围看
[当前进度](docs/A-批验收状态.md) 和 [实际报告](validation/b-final-20261007/REPORT.md)。

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

本地整理包：`distribution/Ronova-Pro-C-Effects-WIP-Java68-Native64-20261014.zip`。包含当前源码、Core/prelaunch JAR 和未签名驱动；只打包指定候选，不带缓存、旧布局、游戏存档或历史分发包：

```powershell
.\distribution\package-current.ps1 -CandidateDirectory <本次候选目录> -OutputPath <新ZIP路径>
```

驱动构建、签名和显式部署见 [驱动说明](native/driver/README.md)。
本次 WIP 中的 SYS 未签名、未加载；完整游戏、来源收口和保存重启结果仍按状态页解释。
执行范围遵循 [工程规则](AGENTS.md)。
