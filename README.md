# PluginLift 0.1.4

## 中文介绍

### 插件简介

PluginLift 是一款适用于 Paper 1.21.11 的电梯插件。它提供 2×2 轿厢、双格电梯门、楼层显示器、呼梯面板和集选式调度，并使用自定义模型呈现完整的电梯外观。

### 主要功能

- 固定 2×2 内部空间的电梯轿厢。
- 每层配备两格宽、两格高的电梯门和门顶楼层显示器。
- 呼梯面板通过组件连接器与对应楼层站连接。
- 上行与下行呼梯请求分别排队，电梯优先处理当前运行方向上的请求。
- 轿厢内可打开楼层选择菜单。
- 电梯门检测门口玩家，在关闭过程中遇到阻挡时保持开启或重新打开。
- 使用实体承载层移动乘客，不修改玩家的速度、重力或飞行状态。
- 支持四个朝向的楼层站、呼梯面板和电梯布局。
- 自动保存楼层站、呼梯面板、已组装电梯和电梯显示名称。
- 按创建顺序分配 `0001` 至 `9999` 的四位内部编号；删除电梯后，下一台新电梯自动使用最小空缺编号。
- 支持通过命令修改电梯显示名称，并按编号列出名称和楼层数量。

### 插件物品

| 物品 | 用途 |
| --- | --- |
| 楼层站套件 | 在地面上放置楼层站、电梯门和楼层显示器。 |
| 呼梯面板 | 在地面上放置呼梯面板。 |
| 组件连接器 | 依次选择楼层站和呼梯面板并建立连接。 |
| 竖井同步器 | 将同一竖井内对齐的楼层站组装为一部电梯。 |
| 楼层设置器 | 设置楼层编号、楼层描述和到站提示音。 |

### 安装方法

1. 将 `PluginLift-0.1.4.jar` 放入服务器的 `plugins` 文件夹。
2. 启动或重新启动 Paper 1.21.11 服务器。
3. 允许服务器发送资源包，或让玩家手动安装 PluginLift 资源包。
4. 如需合成配方，将可选数据包放入世界的 `datapacks` 文件夹并执行 `/reload`。

资源包用于显示自定义物品、楼层站、电梯门、轿厢和呼梯面板。数据包仅提供合成配方，不影响插件的核心功能。

### 建造电梯

1. 管理员执行 `/pluginlift give all` 获取全部插件物品。
2. 使用楼层站套件在各楼层放置楼层站。所有楼层站必须位于相同的 X/Z 位置、保持相同朝向，并留出畅通的 2×2 竖井空间。
3. 在每层放置呼梯面板。
4. 手持组件连接器，依次右键楼层站和对应的呼梯面板。两者顺序可以互换，连接距离不得超过 16 格。
5. 使用楼层设置器右键楼层站，并在聊天栏输入 `楼层编号 | 楼层描述 | 提示音开/关`。
6. 每个楼层编号必须唯一。设置完成后，使用竖井同步器右键任意楼层站组装电梯；插件会自动分配四位编号。
7. 点击呼梯面板上半部分登记上行呼梯，点击下半部分登记下行呼梯。
8. 进入轿厢后右键内部区域，打开楼层选择菜单并选择目的楼层。

最高楼层不能登记上行呼梯，最低楼层不能登记下行呼梯。

### 命令

| 命令 | 说明 |
| --- | --- |
| `/pluginlift help` | 显示插件命令帮助。 |
| `/pluginlift give <station\|panel\|connector\|sync\|config\|all> [数量]` | 获取指定插件物品。 |
| `/pluginlift list` | 按编号查看每台电梯的编号、名称和楼层数量。 |
| `/pluginlift resourcepack` | 重新发送资源包。 |
| `/pluginlift rename <ID> <名称>` | 修改指定电梯的显示名称，名称最多 32 个字符。 |
| `/pluginlift delete <ID>` | 删除指定电梯并保留楼层站。 |
| `/pluginlift respawn` | 重新生成插件显示实体。 |
| `/pluginlift reload` | 重新载入插件配置和数据。 |

### 权限

| 权限 | 默认 | 说明 |
| --- | --- | --- |
| `pluginlift.use` | 所有玩家 | 使用 PluginLift 主命令。 |
| `pluginlift.admin` | 管理员 | 获取物品、修改名称、删除电梯、重建显示实体和重新载入插件。 |

### 配置

默认配置可调整以下内容：

- 资源包下载地址、SHA1 和是否强制接受。
- 电梯运行速度。
- 电梯门开关所需时间。
- 电梯门保持开启的时间。

### 源码结构

| 路径 | 内容 |
| --- | --- |
| `src/main/java` | 插件主体、调度、渲染、碰撞和交互逻辑。 |
| `src/main/resources` | Paper 插件信息和默认配置。 |
| `src/test/java` | 调度、布局、资源和乘客承载逻辑的冒烟测试。 |
| `resource-pack` | 自定义模型、物品定义和贴图。 |
| `datapack` | 可选合成配方。 |
| `.github/workflows` | GitHub Actions 自动构建配置。 |

### 从源码构建

需要安装 Java 21。仓库已经包含 Gradle Wrapper，不需要单独安装 Gradle。

Windows：

```powershell
.\gradlew.bat clean test release
```

Linux 或 macOS：

```bash
./gradlew clean test release
```

构建完成后，插件 JAR、资源包、数据包和 SHA1 清单位于 `build/release`。

### 许可证

插件 Java 源码按照根目录中的 MIT License 发布。资源包模型和贴图使用 `resource-pack/LICENSE-ORIGINAL.txt` 中的单独许可条款。Minecraft、Paper 和其他相关项目的商标归各自所有者所有。

### 运行要求

- Paper 1.21.11
- Java 21
- PluginLift 资源包
- PluginLift 数据包（可选，仅用于合成配方）

---

## English Introduction

### Overview

PluginLift is an elevator plugin for Paper 1.21.11. It provides a 2×2 cabin, two-block-wide elevator doors, floor displays, hall-call panels, and collective call scheduling, presented with a complete set of custom models.

### Main Features

- Elevator cabins with a fixed 2×2 interior.
- Two-block-wide, two-block-high elevator doors and an overhead floor display at every landing.
- Hall-call panels connected to their floor stations with the component connector.
- Separate up and down call queues, with requests in the current travel direction served first.
- An in-cabin floor selection menu.
- Doorway player detection that holds or reopens doors when the entrance is occupied.
- An entity-based carrier floor that moves passengers without changing player velocity, gravity, or flight state.
- Floor stations, hall-call panels, and elevator layouts that support all four horizontal directions.
- Automatic storage of floor stations, hall-call panels, assembled elevators, and elevator display names.
- Four-digit internal IDs from `0001` through `9999`, allocated in creation order; the next new elevator reuses the lowest available ID after deletion.
- Custom display names and an ID-sorted list containing each elevator's name and floor count.

### Plugin Items

| Item | Purpose |
| --- | --- |
| Floor Station Kit | Places a floor station, elevator doors, and floor display on the ground. |
| Hall-Call Panel | Places a hall-call panel on the ground. |
| Component Connector | Selects a floor station and hall-call panel to connect them. |
| Shaft Synchronizer | Assembles aligned floor stations in the same shaft into one elevator. |
| Floor Configurator | Sets the floor number, floor description, and arrival chime. |

### Installation

1. Place `PluginLift-0.1.4.jar` in the server's `plugins` folder.
2. Start or restart the Paper 1.21.11 server.
3. Allow the server resource pack, or install the PluginLift resource pack manually on each client.
4. For crafting recipes, place the optional datapack in the world's `datapacks` folder and run `/reload`.

The resource pack displays the custom items, floor stations, elevator doors, cabin, and hall-call panels. The datapack only provides crafting recipes and is not required for the plugin's core features.

### Building an Elevator

1. Run `/pluginlift give all` as an administrator to obtain all plugin items.
2. Use the Floor Station Kit to place a station at every floor. All stations must share the same X/Z position and facing, with a clear 2×2 shaft behind the doors.
3. Place a Hall-Call Panel at every floor.
4. Hold the Component Connector and right-click the floor station and its hall-call panel. Either selection order works, and the maximum connection distance is 16 blocks.
5. Right-click each station with the Floor Configurator and enter `floor number | floor description | chime on/off` in chat. The accepted chime values are `开/关` or `on/off`.
6. Every floor number must be unique. After configuration, right-click any station with the Shaft Synchronizer to assemble the elevator; the plugin assigns its four-digit ID automatically.
7. Click the upper half of a hall-call panel to register an up call, or the lower half to register a down call.
8. Enter the cabin and right-click its interior to open the floor selection menu and choose a destination.

The top floor cannot register an up call, and the bottom floor cannot register a down call.

### Commands

| Command | Description |
| --- | --- |
| `/pluginlift help` | Displays command help. |
| `/pluginlift give <station\|panel\|connector\|sync\|config\|all> [amount]` | Gives the selected plugin item. |
| `/pluginlift list` | Lists each elevator ID, display name, and floor count in ID order. |
| `/pluginlift resourcepack` | Sends the resource pack again. |
| `/pluginlift rename <ID> <name>` | Changes an elevator display name, up to 32 characters. |
| `/pluginlift delete <ID>` | Deletes an elevator while keeping its floor stations. |
| `/pluginlift respawn` | Respawns the plugin's display entities. |
| `/pluginlift reload` | Reloads the plugin configuration and stored data. |

### Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `pluginlift.use` | All players | Allows access to the main PluginLift command. |
| `pluginlift.admin` | Operators | Allows item distribution, elevator renaming and deletion, display respawning, and plugin reloading. |

### Configuration

The default configuration provides settings for:

- Resource pack URL, SHA1, and acceptance requirement.
- Elevator travel speed.
- Door opening and closing duration.
- Door open hold duration.

### Project Structure

| Path | Contents |
| --- | --- |
| `src/main/java` | Plugin runtime, scheduling, rendering, collision, and interaction logic. |
| `src/main/resources` | Paper plugin metadata and default configuration. |
| `src/test/java` | Smoke tests for scheduling, layouts, resources, and passenger support. |
| `resource-pack` | Custom models, item definitions, and textures. |
| `datapack` | Optional crafting recipes. |
| `.github/workflows` | GitHub Actions build configuration. |

### Building from Source

Java 21 is required. The repository includes the Gradle Wrapper, so a separate Gradle installation is not needed.

Windows:

```powershell
.\gradlew.bat clean test release
```

Linux or macOS:

```bash
./gradlew clean test release
```

After a successful build, the plugin JAR, resource pack, datapack, and SHA1 manifest are available in `build/release`.

### License

The plugin's Java source is released under the MIT License in the repository root. Resource-pack models and textures use the separate terms in `resource-pack/LICENSE-ORIGINAL.txt`. Minecraft, Paper, and other related trademarks belong to their respective owners.

### Requirements

- Paper 1.21.11
- Java 21
- PluginLift Resource Pack
- PluginLift Datapack (optional, crafting recipes only)
