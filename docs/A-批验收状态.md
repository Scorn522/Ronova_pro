# Ronova Pro 当前进度与 A 批状态

日期：2026-10-01。主工程：`C:\Users\Scorn\Desktop\MOD\Ronova-Pro-接续\ronova_GPT`。Core／Agent／Bootstrap ABI 25，持久格式未变。

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



