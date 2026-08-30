# EchoPlayer 仓库规范

本文件适用于仓库根目录及其所有子目录。

## 代码查询

- 建议使用 CodeGraph 对代码结构、符号和调用关系进行粗略查询，以便快速缩小检索范围；关键结论仍需回到实际源码中核实。

## 版本基线

- Minecraft 固定为 `1.20.1`，除非用户明确要求，不得升级 Minecraft 大版本。
- Forge 使用 Minecraft 1.20.1 分支，当前版本为 `47.4.22`。
- Parchment mappings 固定为 `2023.08.20-1.20.1`。
- Java 源码和构建工具链必须使用 Java 17，不得使用 Java 21 或更高版本代替。
- 依赖版本以 `gradle.properties` 为准。修改 Forge 版本时，同时检查并同步 `README.md` 和 `src/main/resources/META-INF/mods.toml`。

## Java 17 环境

运行任何 Gradle 命令前，必须在当前 PowerShell 会话中显式指定 JDK 17，并确认实际版本。本机已验证可用的 JDK 路径如下：

```powershell
$jdk17 = 'C:\Program Files\Java\graalvm-jdk-17'
$env:JAVA_HOME = $jdk17
$env:Path = "$jdk17\bin;$env:Path"
java -version
.\gradlew.bat --version
```

上述两个版本检查都必须显示 Java/JVM 17。如果该路径不存在，应查找本机其他完整 JDK 17 安装，不得静默退回系统默认 Java。

## 构建与验证

标准验证命令：

```powershell
.\gradlew.bat clean build --console=plain
```

- 必须使用仓库内的 Gradle Wrapper，不使用全局 `gradle`。
- 只有在依赖版本发生变化或缓存确有问题时，才添加 `--refresh-dependencies`。
- 修改 Java、资源、Mixin 或构建配置后，至少运行一次完整的 `clean build`。
- 构建成功后，主 JAR 和 sources JAR 应位于 `build/libs`。
- Forge 版本升级后，应从构建日志或 Gradle 解析结果确认实际使用的是预期坐标，例如 `net.minecraftforge:forge:1.20.1-47.4.22`。

## 源码与资源约束
- 保留 `echoplayer.mixins.json`、`echoplayer.forge.mixins.json`、refmap 配置及 MixinExtras Jar-in-Jar 资源，除非对应迁移已经完成并通过构建验证。
- 不要提交 `build/`、`.gradle/`、`run/` 等生成目录。
- 不要删除用户全局 Gradle 缓存。只有用户明确要求且已确认精确版本目录时，才可删除特定旧 Forge 缓存；Gradle 缓存可通过后续构建重新下载。
