# Ronova Pro B 当前 WIP 候选（2026-10-12）

此 JAR 与本目录所在提交的源码对应，Java ABI 49 / Native ABI 46 / Journal 6。它包含前置入口、Agent、Bootstrap、Windows x64 控制 DLL 与 Storage DLL。

**B 尚未完成。** 完整 Forge 构建、实际 Agent/Bootstrap/Native 安装、控制写者拒绝与密封枚举/克隆合同检查已经取得结果；仍没有当前源码的世界、来源/任务/资源组合、存储独立读回和同世界保存重启结果。这个候选供接续定位与试用，不是已验收完成版。

源编译依赖是 Forge 1.20.1-47.4.22；本轮隔离服务端实际使用 1.20.1-47.4.23、Temurin Java 17.0.15+6、Windows 11 x64。真实失败与范围见 [状态页](../../docs/A-批验收状态.md) 和 [B 实际报告](../../validation/b-final-20261007/REPORT.md)。

普通 Forge 实例把主包放入 mods 会尝试晚附加。要在应用 main 前加载控制链，需要启动器接入主包内前置 Main；本轮运行器使用该前置路径。不能把普通 mods 安装解释为启动前已生效。

本目录只包含当前主包、这个说明与对应 SHA-256；游戏运行库和原用户存档不在公开交付目录内。
