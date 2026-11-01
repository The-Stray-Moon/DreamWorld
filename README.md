# DreamWorld

基于 [SighsTemple](https://github.com/Tower-of-Sighs/SighsTemple) 初始化的 `common + targets/<loader>-<minecraft-version>` Minecraft 项目。

包名与 Gradle group 为 `cc.sighs.dreamworld`，mod id 为 `dreamworld`，作者 The Stray Moon，许可证 GPL-3.0-only。

将既有代码迁入本项目时，可以对 AI 这么说：

“使用 https://github.com/Tower-of-Sighs/SighsTemple 框架来重构本项目，第一步先将当前mc版本的所有源码及资源迁移至common部分，第二步将明确查询源码证实了存在明显版本差异的内容转移到targets中的对应版本下，第三步搭建spi模式，将targets中的内容抽象化，只保留具体实现差异，共通逻辑迁移回common部分，第四步将当前targets下的主版本内容移植到全部所有版本。”

## IDEA

直接打开任意 `targets/<loader>-<version>/` 目录。IDEA 会导入当前 target，并把 `../../common` 作为 composite build 的独立可编辑模块一起导入，只下载该 target 的加载器和 Minecraft 依赖。

## Target

| Target | Gradle JVM | 构建命令 |
| --- | --- | --- |
| `forge-1.20.1` | JDK 17 | `targets\forge-1.20.1\.\gradlew.bat clean build` |
| `forge-1.19.2` | JDK 17 | `targets\forge-1.19.2\.\gradlew.bat clean build` |
| `forge-1.18.2` | JDK 17 | `targets\forge-1.18.2\.\gradlew.bat clean build` |
| `fabric-1.20.1` | JDK 17 | `targets\fabric-1.20.1\.\gradlew.bat clean build` |
| `fabric-1.21.1` | JDK 21 | `targets\fabric-1.21.1\.\gradlew.bat clean build` |
| `fabric-26.1` | JDK 25 | `targets\fabric-26.1\.\gradlew.bat clean build` |
| `neoforge-1.21.1` | JDK 21 | `targets\neoforge-1.21.1\.\gradlew.bat clean build` |
| `neoforge-26.1` | JDK 25 | `targets\neoforge-26.1\.\gradlew.bat clean build` |

根项目默认构建 `neoforge-1.21.1`（当前参考版本）。也可以按 target 选择：

```powershell
.\gradlew.bat '-Ptarget=forge-1.20.1' build
.\gradlew.bat '-Ptarget=fabric-1.20.1' build
.\gradlew.bat -PallTargets=true build   # 仅在同一个 Gradle JVM 能覆盖所有 target 时使用
```

`fabric-26.1` 与 `neoforge-26.1` 需要 JDK 25 和各自的 Gradle 版本，必须独立构建；1.21.1 系需要 JDK 21；1.20.1 及更早的 Forge/Fabric target 使用 JDK 17。

### 客户端启动验证

八个 target 都已实际执行 `runClient` 到主菜单。验证命令与构建命令相同，只需把 `build` 换成 `runClient`：

```powershell
cd targets\<target>
.\gradlew.bat runClient --console plain --no-daemon --offline
```

验证标准：日志出现 `OpenAL initialized` / `Sound engine started` / `gui.png-atlas`，且 `run/crash-reports/` 没有新增崩溃报告。窗口启动后用 `Ctrl+C` 或超时结束即可。`fabric-26.1` 的 `build.gradle` 已为 dev run 注入固定的离线用户名，避免 authlib 在无网络时阻塞启动；Fabric 日志中的 `Failed to fetch user properties` / Realms 401 属于 dev 离线认证提示，不影响启动。

`forge-1.18.2` 的 Forge 发行包本身不携带 Mixin 运行时，`runClient` 可以启动，但依赖 Mixin 的梦境移动/渲染功能需要外部 Mixin bootstrap 才会生效。

## 结构

- `common/`: 所有没有确证版本差异的共享代码与共享资源，可以引用 Minecraft 类型，但不直接依赖任何 loader API；loader 能力经 `cc.sighs.dreamworld.spi` 注入。
- `targets/*`: loader 和版本专属入口、注册、config/network 适配、事件桥、Mixin、metadata 与版本专属资源。
- `gradle/target-conventions/publish.gradle`: 所有 target 共用的发布约定。

`common` 的独立编译基线是 NeoForge 1.21.1（`common/build.gradle`、`common/gradle.properties`）。所有 target 都已把主版本内容移植到自己的版本；1.21.1 系直接共享整棵树，其他版本通过 `prepareCommonSources` 的逐文件 `exclude` + target 内同名实现完成版本适配。

| Target | 状态 | 已验证 |
| --- | --- | --- |
| `neoforge-1.21.1` | 已移植，SPI 结构 | `build` + 单测 |
| `fabric-1.21.1` | 已移植，SPI 结构 | `build` + 单测 |
| `forge-1.20.1` | 已移植，1.20.1 变体 | `build` + 单测 + dev server 启动 |
| `forge-1.19.2` | 已移植，1.19.2 变体 + SRG refmap | `build` + 单测 |
| `forge-1.18.2` | 已移植，1.18.2 变体 + SRG refmap | `build` + 单测 + dev server 启动 |
| `fabric-1.20.1` | 已移植，1.20.1 变体 | `build` + 单测 + dev server 启动 |
| `neoforge-26.1` | 已移植，26.1 变体 | `build` + 单测 |
| `fabric-26.1` | 已移植，26.1 变体 | `build` + 单测 + dev server 启动 |

验证边界：所有 target 都通过了编译、单测和 jar 内容检查；上表标注 dev server 的目标做过服务端启动冒烟。客户端渲染、客户端 Mixin 和双端联机没有可用的图形环境，未做运行时验证。`forge-1.18.2` 的 Forge 发行包本身不带 Mixin 运行时，Mixin 配置已随 jar 提供，但实际加载仍需要外部 Mixin bootstrap（详见该 target 的构建注释）。

SPI 的边界：

- `DreamPlatform`：主线程调度、资源 ID、效果、实体类型 ID、追踪刷新与其他 loader 能力。
- `DreamNetwork`：按玩家/全体/范围发送和客户端到服务端发送；具体 payload 编码由 target 负责。
- `DreamConfig`：共享代码读取的六个设置项；target 决定配置文件格式。
- `DreamPersistentData`：实体上的模组持久化数据；Forge/NeoForge 用 `getPersistentData`，Fabric 用 data attachment。
- `DWObjects` + `DreamHolder`：共享代码命名所有注册项，target 注册后绑定。

每个 target 通过 source-set sharing 把 `../../common/src/main/java`（由 `prepareCommonSources` 同步到生成目录）直接编译进自己的源码集，因此 `common` 既能在任意 target 内被独立编辑，也能单独构建：

```powershell
.\gradlew.bat -p common build
```

经源码确证在某个 target 上存在版本差异的 common 文件，在该 target 的 `prepareCommonSources` 中 `exclude` 对应路径，并由该 target 提供自己的实现。

## 共享资源

将所有加载器和版本共用的资源放在 `common/src/main/resources/`。构建任意 target 时，该目录会与 target 自己的 `src/main/resources/` 合并并写入最终 jar。

加载器 metadata 仍必须保留在 target 中：Fabric 使用 `fabric.mod.json`，Forge 使用 `META-INF/mods.toml`，NeoForge 使用 `META-INF/neoforge.mods.toml`。

## 本地依赖

每个 target 都会自动将自身 `libs/` 目录中的 `*.jar` 作为 `implementation` 依赖。将 jar 放入对应目录后不需要在 `build.gradle` 中逐条声明；`*-sources.jar` 和 `*-javadoc.jar` 会被忽略。

```text
targets/forge-1.20.1/libs/
targets/forge-1.19.2/libs/
targets/forge-1.18.2/libs/
targets/fabric-1.20.1/libs/
targets/fabric-1.21.1/libs/
targets/fabric-26.1/libs/
targets/neoforge-1.21.1/libs/
targets/neoforge-26.1/libs/
```

本地 jar 的传递依赖无法自动推导。若某个 jar 还依赖其他库，需要将这些库也放入同一个 `libs/` 目录，或按常规方式声明依赖。

## 发布

每个 target 都提供 `publishMods`，可手动发布其自身的产物至 CurseForge 与 Modrinth。两个平台的项目 ID 是所有 target 共用的非敏感信息，在根 `gradle.properties` 中取消注释并填写：

```properties
publish_curseforge_project_id=你的CurseForge项目ID
publish_modrinth_project_id=你的Modrinth项目ID
```

token 只从环境变量读取，不要写入仓库。PowerShell 示例：

```powershell
$env:CURSEFORGE_TOKEN = '...'
$env:MODRINTH_TOKEN = '...'
$env:PUBLISH_CHANGELOG = '本次版本的更新说明' # 可选

cd targets\forge-1.20.1
.\gradlew.bat publishMods
```

将目录替换为其他 target 即可单独发布对应加载器和 Minecraft 版本。Fabric 会上传重映射后的 jar；Forge 与 NeoForge 上传各自的最终 jar。

`publish` 任务会把 target 的 Java component 发布到 Sighs Maven 仓库，细节见 [Publishing](docs/PUBLISHING.md)。

## 版本参考

- [Minecraft 1.20.1、1.21.1、26.1 完整迁移差异参考](docs/version-differences/README.md)
- [多版本日常维护工作流](docs/MAINTENANCE_WORKFLOW.md)
- [Target CI 自动发现](docs/CI_TARGET_DISCOVERY.md)
