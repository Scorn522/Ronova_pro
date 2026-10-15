# Ronova Pro

主工程：`D:\桌面\ronova_GPT`。Java ABI83 / Native ABI76 / Storage3 / Journal6，客户端协议 `r3-client-8`。
Java 17；用户态 GNU C23 / GNU C++23；独立 Windows 驱动及管理工具 C17。
当前接续补齐手动冻结区域的实际 section/palette 写入判断、字段图原子恢复与已知共享来源保留，并修正 Netty 停源拒绝抢在原 promise 校验之前的误伤。未知载体与未观察底层写入仍按状态页保留边界。
停组后的原 Netty WriteTask 保留发送来源直到 run/cancel 的 finally；拒绝写入分步执行消息释放、promise 终结及原 flush，异常不跳过后续收尾。清理资格绑定实际对象关系、字段/数组槽、准确调用链和实际递归层，不向用户回调提供写权；停组任务在原回收位置丢弃，不再送回共享对象池。实际运行结果仍以状态页为准。
冻结区域的原版位存储、实际 backing 数组、调色板值表及 section/Data 替换接入同一当前世界目标判断，允许冻结格内容不变的邻居写入和原版扩容。活跃效果的真实 Optional/FactorData 连接和字段也接入原保护门，原版 tick 与准确读取回填保留。Netty 原版执行器的真实拒绝分支补 release、失败通知与取消的异常收尾；无法确认未入队的任务保留待结清。
标准全局方块状态表的正向列表、反向编号及其数组也纳入冻结判断；原版 rehash、删除与迭代器搬移按本次实际键值接续，冻结映射在副作用前拒绝，半写仅按准确旧值 CAS 收尾。效果的标准解药列表复用已有 ItemStack/NBT 保护，包含常见 JDK/Guava 不可变及单元素列表，实际 provider 覆写的返回值只在原 getter 一次发布。默认满队列拒绝支持真实 LinkedBlockingQueue 和标准有界 MPSC 两种实现，按活跃原读取/offer/REJECT 链接入原收尾；自定义队列/handler 不取得该资格。Windows JDK 的实际 Net.socket0 创建入口补入 Native 来源和停组判断，未解析的旧入口仍保持 pending。
装备和解药物品中的标准 Forge 能力容器、明确的流体处理器字段及实际 LazyOptional 存储继续按当前物品关系保护；首次延迟初始化只接原回调返回后的字段发布，回调本身不取得写许可。文件映射复制已连接实际父描述符、原生返回和新描述符发布；异常产生的未结清副本进入原恢复与报告入口，不能准确关联的保持 pending。任意 provider 私有状态、未知回调内部载体和未解析原生入口仍保留边界。
本轮新增旧运行帧的准确退役与来源收尾、GameMode/Item 真实覆写动作接续，以及手动冻结区域的标准方块写入阻断。Windows 当前游戏进程的外部新建/复制危险句柄保护使用独立命令 `/ronova_pro host protect-jvm true`，`false` 撤销；沿实际玩家/控制台资格，各运行实例只撤销自己的选择，显示实际驱动回执，命令不会安装或加载驱动。旧帧仅对无未知清理责任的可退出子集强退；未知 finally/锁、未观察的机器码/系统调用、既有句柄和未知自定义载体继续保留边界。
当前源码包含连接前队列逐次发送来源及 EventLoop 接续、独立禁疗、已观察 Host 效果的短提交门，以及原生 Unsafe 写入/free/realloc 的准确控制范围拒绝。外部 JVMTI/堆回调暂时移除借来的内部计数，结束后恢复。活跃效果、FoodData、标准身体的当前六槽装备及其真实 ItemStack/NBT 接入实际写入防护；换装、物品使用、食用、真实攻击与延后挖掘按准确原业务动作交接，拒绝取出时返回 EMPTY，避免留下物品副本。挖掘续接绑定实际玩家、GameMode、世界、目标坐标与当前工具，普通回调不能借用；JNI 数组副本保留获取时的已知及未知来源，提交时并入原检查。FoodData 防写保留自然消耗，不等于免饿。区域禁生成接入底层管理器准入。自定义载体及尚未接入的正常动作仍保留缺口。实际构建、网络及游戏结果以
[当前进度](docs/A-批验收状态.md) 和 [实际报告](validation/b-final-20261007/REPORT.md) 为准。自身外观及武器注册暂缓，完整 C/B 尚未完成。

禁疗复用现有管理命令，目标为单个实体选择器或 UUID：

```text
/ronova_pro no-heal <目标> true
/ronova_pro no-heal <目标> false
```

单独禁疗拒绝实际升血，正常伤害继续生效；可与 `protect` 组合，单独关闭禁疗保留防护，`release` 撤销两者。服务端与客户端使用同版 JAR；旧 `r3-client-7` 不解码新增策略字段。

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
