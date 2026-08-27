<div align="center">

# Lunamura

### Minecraft Forge 1.20.1 混合服务端 &middot; 高性能 · 高兼容

[![Forge](https://img.shields.io/badge/Forge-1.20.1--47.4.22-brightgreen?logo=curseforge&logoColor=white)](https://files.minecraftforge.net/)
[![JDK](https://img.shields.io/badge/JDK-17-brightgreen?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Gradle](https://img.shields.io/badge/Gradle-8.12.1-brightgreen?logo=gradle&logoColor=white)](https://docs.gradle.org/)
[![Version](https://img.shields.io/badge/Version-1.3.0-blue.svg)]()
[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

**Forge 模组 + Bukkit/Spigot/Paper 插件双兼容 &middot; 内置 20+ 项性能与稳定性优化**


[![](https://bstats.org/signatures/server-implementation/Lunamura.svg)](https://bstats.org/plugin/server-implementation/Lunamura/33225)
</div>

---

# 及时获取更新或反馈bug请加入qq群: 743765687

(想加入交流或者水群也可以哦(๑˃ᴗ˂)ﻭ♡)

---
## 作者 / Author

**nyamura**

别称：**OoOooo0518** &middot; **O泡**

---

## 简介 / Introduction

Lunamura 是基于 [MohistMC](https://github.com/MohistMC/Mohist) 1.20.1 的高性能混合服务端。在保留 Forge 模组 + Bukkit 插件双兼容能力的基础上，移植了 **Leaf** 服务端的多项性能优化与 **CatServer** 的稳定性补丁，并在此基础上**原创设计**了多项针对混合端主线程压力的优化，同时重写了 **PROXY Protocol** 支持，使其能稳定适配 FRP / HAProxy / Nginx 等反向代理场景。
做了一些大型科技模组针对性的优化，如mek、ae的优化方案
对大型科技模组堆大量机器的情况下做了特别优化（经测试效果极佳）↓可见下文↓

---

## 主要特性 / Features

### 1. Leaf 性能优化（移植）

从 [Leaf](https://github.com/Winds-Studio/Leaf)（Paper 系高性能分支）移植，所有优化均可通过配置逐项开关。

| 配置键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `lunamura.perf_get_biome_fast` | boolean | `true` | 群系查询快速路径，绕过不必要的对象创建 |
| `lunamura.perf_game_event_prefilter` | boolean | `true` | 游戏事件监听器预声明，跳过无谓派发（降低 Sculk 系方块 CPU 占用） |
| `lunamura.perf_structure_locate_fix` | boolean | `true` | 结构定位算法修复与加速 |
| `lunamura.perf_recipe_manager_fast` | boolean | `true` | 配方查询直接返回 byType Map 的 values，省去流式过滤 |
| `lunamura.perf_natural_spawn_fast` | boolean | `false` | 绕过 SecureRandom 种子初始化，使用 LCG 快速种子（⚠️ 牺牲部分随机性） |
| `lunamura.perf_minecart_collision` | boolean | `false` | 矿车碰撞扫描按 tick 节流 |
| `lunamura.perf_minecart_collision_skip_ticks` | int | `4` | 矿车碰撞扫描的跳过间隔（tick） |
| `lunamura.perf_entity_ttl` | boolean | `false` | 实体存活时间上限（自动清理逾期实体） |
| `lunamura.perf_entity_ttl_ticks` | int | `12000` | 实体存活上限（tick，默认 10 分钟） |

### 2. CatServer 稳定性补丁（移植）

从 [CatServer](https://github.com/Luohuayu/CatServer) 移植的关键健壮性修复：

| 配置键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `cat.activation_null_guard` | boolean | `true` | ActivationRange 空守卫：防止 Forge 环境下 `world.spigotConfig` 未初始化导致 NPE |
| `cat.chunk_unload_safeguard` | boolean | `true` | 区块卸载重入保护：防止模组在卸载迭代中并发修改队列导致死循环 / CME |
| `cat.quiet_invalid_entity` | boolean | `true` | 无效实体日志降噪：将 `WARN` 级日志降为 `DEBUG`，减少控制台噪音 |
| `cat.async_entity_add_queue` | boolean | `true` | 异步加实体降级排队：非主线程的实体添加操作自动转入主线程队列，**修复真实数据竞争** |
| `cat.drain_tasks_in_chunk_tick` | boolean | `true` | 区块 tick 末尾排空主线程积压任务（依赖 `async_entity_add_queue`） |
| `cat.plugin_bytecode_fix` | boolean | `true` | 插件字节码兼容层：自动修复使用了 Java 21 API 或非标准线程池的插件 |
| `cat.plugin_executor_max_threads` | int | `0` | 插件字节码修复使用的线程池上限（0 = CPU 核数） |

#### 插件字节码兼容层

启用 `cat.plugin_bytecode_fix` 后，服务端会自动对加载的插件进行字节码修复：
- **`CompletableFuture.runAsync` 重定向**：将插件内的异步调用重定向到 TCCL 正确的 ForkJoinPool，修复 TrChat 等插件在 `EventSubclassTransformer` 中的崩溃
- **Java 21 API 降级**：在 Java 17 环境下自动将 `List#getFirst()/getLast()` 降级为 `get(0)/get(size()-1)`，修复 QuickShop-Hikari 等按 Java 21 编译的插件

### 3. 原创性能优化

针对混合端（Forge + Bukkit）主线程压力自研的优化，**不照搬任何上游实现**：

| 配置键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `lunamura.perf_blockentity_tick_cache` | boolean | `true` | **方块实体 tick 调度缓存**：缓存上一区块的 `shouldTickBlocksAt` 结果，`getLevel` 查询从"每方块实体一次"降到"每区块一次"（实测约 5 万方块实体下减少 97% 调度查询，TPS 提升约 40%） |
| `lunamura.perf_spawn_count_interval` | int | `5` | **刷怪配额计数节流**：每 N tick 才重算一次 mob cap 计数，中间复用缓存结果，省去每 tick 的全实体遍历（设 `1` 恢复原版行为） |
| `lunamura.perf_async_player_save` | boolean | `true` | **异步玩家数据保存**：主线程只做 NBT 序列化（快照），gzip 压缩 + 写盘下放后台线程池，关服时 shutdown hook 兜底 flush |
| `lunamura.perf_async_save_json` | boolean | `true` | **异步存档 JSON**：op/ban/whitelist 等用户列表的序列化留主线程，写盘下放后台线程池 |
| `lunamura.stop_save_timeout_ms` | int | `10000` | **关服存盘超时**：`/stop` 时区块排空循环超时保护，防止光照更新卡住或强制加载区块导致永真循环、无法正常关服 |
| `lunamura.async_threads` | int | `2` | **自研异步线程池大小**：玩家数据 / 用户列表等落盘任务共用的固定线程池线程数。任务为磁盘 IO 密集型，**无需按 CPU 核数设置**，默认 2 即可；SSD 且存盘频繁的服务端可提到 4，一般不超过 8 |
| `lunamura.perf_villager_brain_offload` | boolean | `true` | **村民脑机卸载**（源自 PRTS/ServerCore 移植）：将村民 `Brain` 的周期性 tick 从主线程节流，缓解村民密集村庄的卡顿 |

### 4. 其他功能

| 配置键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `lunamura.enable_fma` | boolean | `false` | **FMA 融合乘加**：对 `Vec3`（点积/平方距离）与 `Mth.lerp` 使用 `Math.fma` 加速数学计算（⚠️ 需 CPU 支持 FMA 指令集，且会改变浮点舍入 → 地形种子可能与 vanilla 不一致） |
| `lunamura.library_download_repo` | string | 空 | 插件依赖库下载仓库镜像（默认 CN 自动选阿里云、否则 Maven Central；配了 URL 就覆盖） |

### 5. PROXY Protocol 支持

- 同时支持 **v1**（文本 `PROXY TCP4/TCP6 ...`）与 **v2**（二进制头）
- **智能检测**：自动判断连接是否为 PROXY 协议，既可处理代理转发，也兼容玩家直连
- 正确处理 PROXY 头与 Minecraft 握手合并在同一 TCP 段的场景（HAProxy / Nginx `send-proxy` 极其常见）
- 启用：`lunamura.proxy_protocol: true`

### 6. 其他改进

| 配置键 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `lunamura.lang` | string | 系统默认 | 服务端语言（如 `zh_CN`、`en_US`） |
| `lunamura.ping_status_version` | string | `lunamura 1.20.1` | 服务器列表显示的版本名称 |
| `lunamura.watchdog_spigot` | boolean | `true` | 启用 Spigot Watchdog 线程监控 |
| `lunamura.watchdog_lunamura` | boolean | `false` | 启用 Lunamura 额外线程监控 |
| `threadpriority.server_thread` | int | `8` | 服务端主线程优先级（1-10） |
| `world.async_save` | boolean | `false` | 异步保存世界（减少卡顿） |
| `anvilfix.maximumrepaircost` | int | `40` | 铁砧最大修复花费 |
| `anvilfix.enchantment_fix` | boolean | `false` | 附魔等级修复 |
| `anvilfix.max_enchantment_level` | int | `32767` | 最大附魔等级 |
| `player_modlist_blacklist.enable` | boolean | `false` | 启用玩家模组黑名单 |
| `server_modlist_whitelist.enable` | boolean | `false` | 启用服务端模组白名单 |
| `forge.bukkitpermissionshandler` | boolean | `true` | 使用 Bukkit 权限处理器 |

---

## 构建 / Build from Source

### 环境要求

- **JDK 17**（推荐 [Eclipse Temurin](https://adoptium.net/)）
- **Gradle 8.12.1**（使用项目自带 wrapper）
- 网络访问（首次构建需下载 Minecraft 依赖）
- 中国大陆用户建议配置 HTTP 代理

### 构建步骤

```bash
# 1. 初始化项目（下载 Minecraft 源码并应用补丁，仅首次需要）
./gradlew setup

# 2. 打包运行时依赖库
./gradlew packageLibraries

# 3. 构建最终服务端 jar
./gradlew mohistJar
```

> ⚠️ **重要**：`setup` 与 `mohistJar` **必须分两次独立执行**。不要写成链式命令（如 `./gradlew setup mohistJar`），否则会触发 Gradle 隐式依赖冲突导致构建失败。

### 构建产物

```
projects/mohist/build/libs/lunamura-1.20.1-1.3.0-server.jar   (~136 MB，唯一分发文件)
```

---

## 运行 / Run

```bash
java -jar lunamura-1.20.1-1.3.0-server.jar
```

首次启动会自动完成安装（解压内置库并应用二进制补丁），随后即可正常进入。

---

## 配置 PROXY Protocol

编辑服务端目录下的 `lunamura-config/lunamura.yml`，在 `lunamura:` 块内加入：

```yaml
lunamura:
  proxy_protocol: true
```

保存后**重启服务端**。确保代理侧已开启 PROXY 头发送：

- **FRP**：在对应 proxy / transport 配置中开启 `proxy_protocol`（v2）
- **HAProxy**：后端 `server` 行加 `send-proxy-v2`
- **Nginx**：`proxy_pass` 所在 `listen` 块加 `proxy_protocol on;`

---

## 配置说明 / Configuration

完整配置文件位于 `lunamura-config/lunamura.yml`，首次启动时自动生成。所有优化开关均在此文件的 `lunamura:`、`cat:` 块下。

> 💡 大部分优化默认开启。如需关闭某项，将对应值改为 `false` 后重启即可。

---

## 致谢 / Credits

### 核心上游

- **[MohistMC](https://github.com/MohistMC/Mohist)** — Forge + Bukkit 混合服务端核心，本项目的基石。提供了完整的 Mod/Plugin 双兼容架构、构建系统、及大量 NMS 补丁基础设施。

### 性能优化来源

- **[Leaf](https://github.com/Winds-Studio/Leaf)** — Paper 系高性能服务端分支，本项目的性能优化主要移植来源
- **[CatServer](https://github.com/Luohuayu/CatServer)** — Forge 混合端前辈，本项目的稳定性补丁与插件字节码兼容方案来源
- **[PRTS-SERVER](https://github.com/ElainAwa/PRTS-SERVER)** — Arclight 生产 fork，本项目「村民脑机卸载」（`perf_villager_brain_offload`）的直接移植来源
- **[ServerCore](https://github.com/Wesley1808/ServerCore)** — 上述实体激活与票据传播优化的原始实现

### 上游生态

本项目基于并感谢以下项目的长期贡献：

- [MinecraftForge](https://github.com/MinecraftForge/MinecraftForge) · [Bukkit](https://github.com/Bukkit/Bukkit) · [CraftBukkit](https://github.com/Bukkit/CraftBukkit) · [Spigot](https://github.com/SpigotMC/Spigot) · [Paper](https://github.com/PaperMC/Paper) · [PRTS-SERVER](https://github.com/ElainAwa/PRTS-SERVER) · [Arclight](https://github.com/IzzelAliz/Arclight) · [Luminara](https://github.com/CraftAmethyst/Luminara) · [ServerCore](https://github.com/Wesley1808/ServerCore) · [leaf](https://github.com/Winds-Studio/Leaf) · [CatServer](https://github.com/Luohuayu/CatServer) · [MohistMC](https://github.com/MohistMC/Mohist)

---

## 许可证 / License

**GNU General Public License v3.0**

本项目是 MohistMC 的衍生作品（fork），继承其 GPL-3.0 许可证。完整条款见仓库根目录 [`LICENSE`](LICENSE) 文件。

> 本软件与 Mojang Studios / Microsoft 无任何隶属关系。Minecraft 是 Mojang Studios 的商标。
