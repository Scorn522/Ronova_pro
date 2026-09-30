# Legacy Traceability Annex V1.4

本附录沿用 V1.3 附录的历史内容，只更新设计入口；不新增能力、实现状态或通过统计。旧动作与能力名称对照最初来自 V1.1。
它不规定新 Pro 的模块数、按钮数、档位、执行路由或主验收计数。

主设计与验收以 [设计方案](Ronova-Pro-设计方案-V1.4.md) 和
[Primitive / Executor / Outcome / Scenario 矩阵](能力与验收矩阵-V1.4.md) 为入口。
开发者先实现原语和场景，仅在迁移、查漏或定位旧需求时查此附录。
对应 Cxx 场景指向新矩阵；旧名称无需生成独立 capability/wiring/evidence 组件。
本附录无 PASS 统计，也不继承旧版实现进度。


## A. 14 种历史动作语义

保留这些必要语义的内部执行入口，可合并在少量玩家操作中；组合动作必须报告实际步骤和结果。
改血、准确移除等作为高强度链路的原语保留，不再据此单列一套弱档产品。
不能用“已保存配置”替代执行，也不能通过改名掩盖功能实际缺失。

| 动作 | 执行职责 | 真实结果 | 场景 |
|---|---|---|---|
| strongAttack | 实际动作编排 | 按目标和开启项生成具体步骤、依赖与部分结果 | C35,C04 |
| rewriteLife | Entity | 当前/最大/附加及真实存活来源按合同改写 | C01,C16 |
| terminalize | 统一记录 + Reconstruction | 持久终结和资格撤销，后续已确认来源沿同一真值处理 | C04,C13,C17 |
| directClear | Entity | 准确移除；生命周期副作用单独记账 | C02,C01 |
| clearEntityRecords | Entity / World / Storage | 准确索引、容器、存储记录及其他条目保留 | C02,C12,C20 |
| suppressRespawn | Reconstruction | 当前目标关系的重生入口与保存资格 | C05,C15 |
| suppressRebuild | Reconstruction / Task | 对象替换、换 UUID、producer 与后继链 | C05,C07,C09 |
| suppressAdmission | Reconstruction / Presence / Storage | 普通生成、网络重建、保存加载入口 | C05,C12,C23 |
| attackRender | Presence | 准确 renderer、纯渲染对象与重建入口 | C24,C33 |
| clearTracking | Presence | 额外跟踪、订阅、缓存与重连 | C23,C24 |
| clearHud | Presence | BossBar、HUD、overlay 的对象和后继 | C24 |
| stripCodeDefense | Code / Runtime | 准确防御代码及补丁 producer 的真实处置 | C25,C26,C27 |
| attackCallChain | Task / Runtime | 已绑定调用、回调、后继及其资源 | C07,C09,C10 |
| allReturn | Code / Dispatch | 封闭返回合同，异常/取消/资源不被伪装成功 | C25,C26 |

## B. 54 个历史能力名称

沿用名称便于对照，不继承旧版分层实现。
不复制 54 键乘以多个强度档位的产品矩阵；等价后端复用同一能力合同。
以下语义范围是必要入口，完整执行还要满足主设计的身份、持久、资源与完成合同。
source_trace 与 anomalous_call_log 以真实观察/诊断为其功能，不冒充攻击执行。

| 键 | 实现职责 | 需要产生的实际能力 | 场景 |
|---|---|---|---|
| attack.non_entity | World / Presence | 非 Entity 对象有稳定定位和实际动作 | C20,C24 |
| attack.logic_source | Task / Runtime | 准确来源与执行路径处置 | C05,C09 |
| attack.recovery_source | Reconstruction / Storage | 恢复来源、位置及保存链闭合 | C04,C12,C15 |
| attack.state_source | Entity / Integrity | 真实状态来源及后置结果 | C01,C21 |
| attack.registration_source | Entity / Reconstruction | 登记、资格和重插同步处理 | C02,C13,C16 |
| attack.task_chain | Task | 任务执行链闭合 | C06,C07,C08 |
| attack.callback_chain | Task | 回调、迟到与子任务闭合 | C07,C09 |
| attack.network_reconstruction | Presence | 网络镜像/回放/重连重建 | C23,C24 |
| attack.save_reconstruction | Storage | 保存及重启加载恢复链 | C12,C14,C15 |
| attack.dynamic_code | Code | 动态代码的实际变换与失能结果 | C25,C26 |
| attack.dispatch | Code / Runtime | 实际被调用实现及传播结果 | C25,C26 |
| attack.runtime_patch_disablement | Code / Integrity | 补丁和其 producer 失能及后续替换发现 | C26,C27 |
| attack.target_class_lineage | Runtime / Reconstruction | 实例、类、加载器和实际来源隔离 | C05,C16,C26 |
| attack.final_result_takeover | 统一记录 / Completion / Code | 最终后置条件与返回合同 | C17,C25,C35 |
| attack.source_trace | 生产观察 / 诊断 | 显示真实来源及缺口；按观察能力验收 | C05,C11 |
| intercept.mixin | Code / Runtime | Mixin 相关受测入口处置 | C26,C27 |
| intercept.asm | Code / Runtime | ASM/Javassist 实际转换入口处置 | C26,C27 |
| intercept.instrumentation | Runtime | Instrumentation 的当前实例和变换生命周期 | C26,C27 |
| intercept.jvmti | Native / Runtime | 当前 JVMTI 后端的真实入口与对象 | C26,C27 |
| intercept.unsafe_reflection | Code / Runtime | 准确写入位置及反射/Unsafe/MethodHandle 路径 | C21,C26 |
| intercept.classloader | Runtime | 加载器设置、替换及同名类隔离 | C16,C26 |
| intercept.jni_native | Native / Runtime | JNI/Native 边界及当前版本 | C13,C26 |
| intercept.modlauncher | Runtime | ModLauncher/Core 入口及生命周期 | C26,C27 |
| intercept.thread_task | Task / Runtime | 真实创建/调度入口与任务来源 | C06,C09,C11 |
| intercept.all_return | Code / Integrity | 返回被抑制后的识别、拒绝或恢复 | C25,C27 |
| protect.life | Entity | Owner 生命与当前资格 | C01,C03 |
| protect.death | Entity / 统一记录 | 合法生命周期和受干扰死亡分别处理 | C03,C01 |
| protect.removal | Entity | 准确移除防护与恢复 | C02,C03 |
| protect.entity_record | Entity / World | 真实登记位置守护、修复及重插 | C02,C20 |
| protect.identity_respawn | Reconstruction | 同主体恢复与新合法资格隔离 | C05,C16 |
| protect.display | Presence | 显示及客户端对象当前状态 | C24,C33 |
| protect.anti_interception | Integrity / Runtime | 执行入口失效后独立发现与修复 | C26,C27 |
| protect.anti_all_return | Integrity / Code | 关键返回合同及防护步骤不被吞掉 | C25,C27 |
| protect.input_communication | Presence / Runtime | 输入、通信与服务端许可 | C23,C24 |
| protect.call | Runtime | 准确调用上下文和版本 | C16,C25 |
| protect.code_integrity | Integrity | 基线、实际代码与效果三者一致 | C25,C27 |
| protect.core_authority | 统一记录 / Integrity | 唯一 writer、准确资格和 ACK 语义 | C13,C16,C31,C32 |
| protect.auto_repair | Integrity / 执行器 | 在准确范围内真实修复 | C03,C27 |
| protect.state_recovery | Entity / World | 版本化状态恢复及合法变化保留 | C03,C21 |
| protect.restart_recovery | 统一记录 / Storage | 原工作、后端和当前对象重新定位 | C15,C19,C32 |
| protect.rollback | Storage / Integrity | 旧记录/检查点不回滚当前权威 | C15,C21,C32 |
| protect.loop_guard | 调度 / Integrity | 因果、重复触发与修复循环有界 | C18,C27 |
| runtime.java_agent | Runtime | Agent 实际能力和当前生命周期 | C26,C31 |
| runtime.instrumentation | Runtime | 实际实例、代码版本与操作范围 | C25,C26 |
| runtime.jvmti | Native / Runtime | 实际类副本、入口和 Native 结果 | C26,C27 |
| runtime.native | Native | 准确原生操作及当前资格 | C10,C12,C13 |
| runtime.native_loader | Native / Runtime | 平台载荷身份、版本、装载及关闭 | C26,C27,C31 |
| runtime.render_guard | Presence / Integrity | 绘制入口、对象与资源恢复 | C24,C33 |
| runtime.critical_class_watch | Integrity | 关键集加载/替换触发失效并恢复 | C26,C27 |
| runtime.dynamic_code_watch | Integrity / Code | 新动态版本纳入当前工作与验收 | C26,C27 |
| runtime.native_module_watch | Host / Runtime | 实际模块状态与版本变化 | C27,C29 |
| runtime.thread_continuity | Task / Runtime | 线程边界、任务退出和停机接续 | C07,C19,C31 |
| runtime.background_integrity | 调度 / Integrity | 有界后台检查和有效修复 | C18,C27,C34 |
| runtime.anomalous_call_log | Runtime / 诊断 | 关联准确意图与实际异常入口 | C11,C25 |

