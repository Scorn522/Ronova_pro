# Ronova Pro · GPT 工作工程

GitHub 同步仓库：[Scorn522/Ronova_pro](https://github.com/Scorn522/Ronova_pro)，默认分支 `main`。后续本工程的修复、文档和对应候选产物同步到此仓库。

公开仓库包含当前源码、设计、状态、本次候选与修复报告摘要。历史游戏运行目录、存档、第三方研究证据和旧分发包保留在本地接续工程。

工作目录：`C:\Users\Scorn\Desktop\MOD\Ronova-Pro-接续\ronova_GPT`。原 DeepSeek 对照工程未随本接续包迁入。

从 [文档入口](docs/README.md) 开始阅读；实现与验收进度统一看 [当前 A 批状态](docs/A-批验收状态.md)。

遵循 [工程执行规则](AGENTS.md)：**先写完整个工作包的代码，再统一编译和验证；不准做与实际攻防强度无关的任何验证。**

| 位置 | 内容 |
| --- | --- |
| `src/`、`agent/`、`bootstrap/`、`prelaunch/`、`native/` | 生产代码与游戏进程前置入口 |
| [docs/](docs/README.md) | 当前状态、R3 设计、研究资料与历史记录 |
| `validation/` | 独立检查、隔离夹具及已有运行记录 |
| `validation/history/takeover-20260921/` | 一次性接手脚本与原始基线 |

完整工作包写齐后，有直接攻防执行需求时使用的编译命令（不是日常固定检查）：

```powershell
.\gradlew.bat compileJava compileBootstrapJava compileAgentJava compileFixtureJava
```

`validation/check_controls.py` 用于独立边界运行检查，要求 Windows、`JAVA_HOME` 指向 JDK 17。
Native 构建需要 Zig，可用 Gradle `-PcontrolZig=编译器路径` 指定。不例行构建发布 JAR。

`distribution/package-stage-b.py` 是早期 Stage B 的打包脚本，依赖当时候选与证据；不能用来宣称当前源码已完成验收。

最新候选：`distribution/candidate-modwide-a-20261001/`；当前效果和限制见 A 批状态。

## 整 Mod A 候选的使用

普通 `mods` 安装仍会自动尝试进程内附加。需要在游戏 JVM 启动前生效时，让启动器以 `ronova-pro-prelaunch.jar` 作为游戏 Java 命令的前置入口，并传入它原本要执行的全部游戏 JVM 参数与主类参数；玩家不用手填 `-javaagent`。示例：

```text
java -jar ronova-pro-prelaunch.jar --java <启动器使用的 java.exe> --core <mods 内的 ronova-pro-core.jar> <原有全部游戏参数>
```

在游戏实例根目录放置 `ronova-prelaunch.properties`，例如：

```properties
stop=target_mod_a,target_mod_b
protect=ally_mod
allow_remote_stop=target_mod_a,target_mod_b
```

`stop` 在目标 Mod 类加载前建立整组方法、构造／静态入口及事件控制；`protect` 在当前和后续实体、方块实体上建立防护。`allow_remote_stop` 是多人游戏客户端允许当前服务器请求停用的本地 Mod 列表，不会在启动时直接停用它们。运行中可用 `/ronova_pro mod stop modid1,modid2`、`/ronova_pro mod protect modid` 和 `/ronova_pro mod status [modid1,modid2]`。实际覆盖和未决路径见 [A 批状态](docs/A-批验收状态.md)。单纯把 JAR 放进 `mods` 不能在游戏 JVM 创建前运行前置入口。
