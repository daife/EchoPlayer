# EchoPlayer

EchoPlayer 是面向 **Minecraft 1.20.1 / Forge** 的持久化虚拟玩家模组。你可以创建多个玩家分身，直接附身操控，并通过 **按住左 Alt 打开轮盘、松开切换**，在本体与不同分身之间快速转换视角和操作对象，无需启动额外的游戏客户端。

项目借鉴了 [Advanced Fake Player](https://modrinth.com/mod/advanced-fake-player)（MIT 协议）的部分思路，并围绕快速切换、玩家状态同步、身份与归属关系、模组兼容及自动化控制接口进行了深入优化和扩展。

适合多角色游玩、联机机制测试、整合包兼容性验证、场景摆拍，以及为扩展模组提供可接管的玩家实体。

## 核心功能

### 直接附身与 Alt 快速切换

- 接管分身的移动、视角、攻击、物品使用和方块交互，也可以打开容器、合成和管理背包。
- 按住 `左 Alt` 打开附身轮盘，鼠标指向目标后松开按键即可切换；已附身时也能直接切换到另一个分身。
- 轮盘包含本体和当前有权管理的分身。选中本体可返回原来的角色，将鼠标移回中心后松开可取消，`Esc` 也可关闭轮盘。
- 按 `O` 立即解除附身。两个快捷键均可在 Minecraft 的按键设置中修改。
- 切换时处理各角色的视角与朝向，衔接骑乘、睡眠等状态。

轮盘不会暂停游戏，且需要在未打开背包、聊天等其他界面时唤出。目标是否可接管仍由服务端检查；有管理权限不代表能接管正在被其他玩家或自动化控制器占用的分身。

### 独立角色状态与持久化

- 分身拥有自己的玩家身份、背包、装备和状态；附身时使用目标分身的数据，解除附身后恢复本体状态。
- 同步生命值、伤害吸收、饥饿与饱和度、经验、药水效果、属性、物品冷却及动作状态。
- 分身列表、创建者和公共访问设置随世界保存，重新加载世界或重启服务器后恢复持久化分身，并加载其玩家数据。
- 分身死亡后通过重生流程恢复；附身期间的死亡也有对应的退出与状态恢复处理。
- 附身时，本体以留在世界中的实体承接自身状态和关系；本体仍可能受伤或死亡，需要为它安排合适的位置。

### 世界交互与多人可见性

- 围绕攻击与受伤、物品和经验拾取、投射物伤害来源进行适配。
- 处理船只放置、骑乘、钓鱼浮标和拴绳关系在附身、切换与解除附身时的衔接。
- 对玩家选择器、命令执行身份和睡眠人数统计进行处理，减少附身内部实体造成的重复计数或目标偏差。
- 同步旁观玩家看到的角色、朝向与骑乘关系，并隐藏附身过程中用于承接操作的内部控制实体。

### 自定义皮肤

支持按 Minecraft 玩家名设置皮肤、从图片 URL 设置皮肤，以及清除自定义皮肤。皮肤设置命令见下方命令表。

按玩家名查询皮肤和 URL 皮肤生成需要服务端能够访问对应的在线服务；URL 方式使用 MineSkin 服务，可能受到网络状况、服务可用性或限流影响。

## 模组兼容

EchoPlayer 对以下模组提供了专门的兼容处理。这些是可选集成，使用基础附身功能无需安装它们；安装时仍需满足各模组自身的依赖要求。

| 模组 / 系统 | 当前适配内容 |
| --- | --- |
| **Touhou Little Maid（东方女仆）** | 将女仆的主人查询映射到对应的角色实体，衔接依赖主人身份的行为；适配魂符（Soul Spell / `ItemSmartSlab`）的主人校验，使附身期间可使用归属于本体或当前分身的魂符，包括放出已存储的女仆，同时保留物品原有的归属记录。 |
| **Curios API** | 在附身、角色切换及恢复过程中同步和恢复饰品栏，保留角色各自的饰品状态。当前声明的可选依赖范围为 `5.0.0+`。 |
| **Player Collars** | 适配项圈的主人身份读写与查找、拴绳交互和持有者转移、牵引物理、Clicker 响应、染色界面以及项圈荆棘伤害。当前声明的可选依赖范围为 `1.2.4+`。 |
| **Yes Steve Model（YSM）** | 在角色切换时复制并重新同步模型选择数据，让控制端与同维度的其他玩家收到对应角色的模型选择。 |
| **Palladium** | 兼容数据驱动 superpower：切换时转移能力、能量条、能力冷却/激活计时、运行时属性、能力属性、饰品、飞行与双持状态；避免控制端和可见角色重复 tick，并转发 Palladium 自定义同步消息、存档和数据包内容。Palladium 未安装时不会加载兼容代码。 |
| **PantheonSent** | 将 Khonshu、招募计时和 Ushabti 的 UUID 归属映射到当前可见角色；附身、切换、死亡和解除附身后，Moon Knight 能力、Khonshu 显隐及 Ushabti 交互继续使用正确角色。 |
| **原版驯服动物** | 适配驯服时记录的主人身份和附身期间的归属判断。 |

以上描述对应仓库中已实现的适配范围，不代表覆盖这些模组的所有版本与全部功能。其他模组的自定义背包、能力数据或特殊身份校验可能仍需要单独适配。

## 安装与快速上手

### 运行环境

| 项目 | 版本 / 要求 |
| --- | --- |
| Minecraft Java Edition | `1.20.1` |
| 模组加载器 | Forge，当前构建基线为 `47.4.22` |
| Java | `17` |
| 安装位置 | 客户端与服务端均需安装；单人游戏安装在客户端的 `mods` 目录即可 |

将模组主 JAR 放入对应实例的 `mods` 目录。联机时，服务器及参与游戏的客户端应使用一致的 EchoPlayer 版本。源码包 `-sources.jar` 用于开发查看，不作为运行模组安装。

### 创建并操控第一个分身

进入世界后，在游戏聊天栏执行：

```mcfunction
/echoplayer spawn EchoOne
/echoplayer control EchoOne
```

第一条命令在命令执行位置及朝向创建分身，第二条命令接管它。接下来可以像操作自己的角色一样移动、使用物品和与世界交互。

再创建一个分身，即可体验轮盘切换：

```mcfunction
/echoplayer spawn EchoTwo
```

按住 `左 Alt`，指向 `EchoTwo`，然后松开。返回本体时选择轮盘中的本体名称，或按 `O`，也可执行：

```mcfunction
/echoplayer unpossess
```

分身名称不能含空格，最长为 16 个字符；创建时会检查名称或身份冲突。

## 命令与权限

根命令为 `/echoplayer`。涉及现有分身的命令支持按当前管理权限进行名称补全。

| 命令 | 用途 |
| --- | --- |
| `/echoplayer spawn <name>` | 在当前位置创建持久化分身，并记录执行玩家为创建者。 |
| `/echoplayer control <name>` | 附身指定分身，或从当前分身直接切换过去。 |
| `/echoplayer unpossess` | 解除附身，返回本体。 |
| `/echoplayer remove <name>` | 移除指定分身，并删除其持久化记录及玩家数据文件。 |
| `/echoplayer skin set <name> <skin_name>` | 使用指定 Minecraft 玩家名对应的皮肤。 |
| `/echoplayer skin url <name> <url>` | 从皮肤图片 URL 设置皮肤。 |
| `/echoplayer skin clear <name>` | 清除自定义皮肤。 |
| `/echoplayer config allow_other_players_control` | 查询当前世界是否开放公共访问。 |
| `/echoplayer config allow_other_players_control <enabled>` | 开启（`true`）或关闭（`false`）公共访问，需要权限等级 2（通常为 OP）。 |

默认只有创建者可以控制、移除分身或修改其皮肤；基础创建与管理命令本身没有统一的 OP 限制。开启 `allow_other_players_control` 后，其他玩家也可以进行上述管理操作，**包括移除分身和修改皮肤**。此设置随世界保存，默认关闭。

同一分身同一时间只能由一位真实玩家附身。若自动化控制器已占用目标，能否临时接管取决于该控制器的让出策略。

`remove` 用于删除角色；如果只是想结束操控并保留分身和物品，请使用 `unpossess`。

## 自动化与 AI 扩展接口

EchoPlayer 提供控制权仲裁 API，供附属模组接入自动化或 AI 控制器。具体的寻路、任务规划和 AI 行为由扩展实现；安装 EchoPlayer 本身不会让分身自动执行任务。

入口为 [`EchoPlayerControlApi`](src/main/java/com/echoplayer/api/control/EchoPlayerControlApi.java)。接入时需遵循以下约定：

- 在写入分身的控制输入前，调用 `tryAcquireAutomation(...)` 获取 `AutomationControlLease`。控制状态的读取、变更和租约关闭均需在 Minecraft 服务端线程执行。
- 默认重载使用 `PossessionPreemptionPolicy.DENY`，租约有效期间拒绝玩家附身。
- 扩展可显式选择 `PossessionPreemptionPolicy.ALLOW`。有权管理分身的玩家尝试附身时，系统同步调用监听器，请求自动化控制器释放所有可写输入；成功后暂停租约，保留原 token，解除附身后归还控制权。
- `AutomationControlListener` 接收附身抢占、解除附身以及租约被撤销的生命周期通知。目标被删除、在正常死亡流程之外卸载，或服务器停止时，系统会撤销租约。
- 租约可以跨越死亡与重生，并转移到 UUID 相同的新分身实例。扩展应在实例不可用期间暂停操作，重生后恢复或重新规划任务。
- 扩展应在每个正常退出路径关闭租约，并自行校验请求用户的权限；API 只负责控制权仲裁。

## 开发与构建

依赖版本以 [`gradle.properties`](gradle.properties) 为准：Minecraft `1.20.1`、Forge `47.4.22`、Java `17`，Parchment mappings 为 `2023.08.20-1.20.1`。

必须使用完整的 JDK 17 和仓库内的 Gradle Wrapper。Windows PowerShell 示例（JDK 路径按实际安装位置调整）：

```powershell
$jdk17 = 'C:\Program Files\Java\graalvm-jdk-17'
$env:JAVA_HOME = $jdk17
$env:Path = "$jdk17\bin;$env:Path"
java -version
.\gradlew.bat --version
.\gradlew.bat clean build --console=plain
```

先确认 `java -version` 与 Wrapper 版本信息中的 Java/JVM 均为 17，再执行构建。构建完成后，主 JAR 和 sources JAR 位于 `build/libs`。

仓库规范见 [`AGENTS.md`](AGENTS.md)。

## 致谢与许可证

感谢 [Advanced Fake Player](https://modrinth.com/mod/advanced-fake-player) 提供的虚拟玩家与直接附身思路，该项目在 Modrinth 上标注为 MIT 协议。EchoPlayer 在借鉴部分思路的基础上，进一步扩展了交互方式、状态处理和模组适配。

EchoPlayer 使用 MIT 协议，完整条款见 [`LICENSE`](LICENSE)。
