# PowerfulFireworks

_~~充满力量的烟花（大雾~~_

[![Cyka](https://s21.ax1x.com/2025/01/15/pEFuSKS.png)](https://imgse.com/i/pEFuSKS)

所有烟花都以数据包创建：服务端不生成实体，几百支烟花同时升空也不会拖慢 TPS。

Wiki: https://nullpepper.github.io/PowerfulFireworkWiki/

## 特性

- **烟花写字**：用 BDF 点阵字体把文字渲染成点阵烟花，可固定朝向，也可朝向每个玩家
- **发包烟花**：只向客户端发送生成包与实体事件，飞行和爆炸都在客户端本地渲染
- **随机烟花**：可调数量、间隔、升空时间、世界黑名单，玩家可自行开关
- **动作节点系统**：在 `fireworks/*.yml` 里用 `original` / `text` / `wait` / `reference` 节点编排整场演出，也可以做成烟花道具，燃放后自动触发
- **逐支声音控制**：发射声（嗖）与爆炸声（砰）按概率播放，普通烟花和文本烟花分开配置

## 环境要求

| 服务端 | 所需 Java | 使用的 NMS 实现 |
| --- | --- | --- |
| Paper 26.3 | 25 | `v26_3`，发射声与爆炸声都能单独控制 |
| Paper 1.21.4 / 1.21.5 | 21 | `v1_21_4`，只能控制发射声 |
| Paper 1.20.2 – 1.20.4 | 17 | `v1_20_2` / `v1_20_4`，只能控制发射声 |
| Paper 1.18.2 / 1.19.4 | 17 | `v1_18_2` / `v1_19_4`，只能控制发射声 |

- 只支持 Paper 与其衍生端（Purpur、Pufferfish 等），Spigot 不行
- **Vault** 是硬依赖，会随插件一起加载
- 插件按 `Bukkit.getMinecraftVersion()` 自动挑选 NMS 实现，jar 里同时带着上面所有版本
- `paper-plugin.yml` 的 `api-version` 是 `1.18`，与最低支持的 1.18.2 对齐

## 安装

1. 把 `PowerfulFireworks-<version>-all.jar` 与 Vault 一起放进 `plugins/`
2. 启动一次服务端，生成 `plugins/PowerfulFireworks/`
3. 按需编辑 `config.yml`，往 `fonts/` 放字体，往 `fireworks/` 放演出配置
4. `/fireworks reload` 重载（改动字体后建议直接重启）

```
plugins/PowerfulFireworks/
├── config.yml       # 主配置
├── fonts/           # BDF 点阵字体
│   └── example.bdf
└── fireworks/       # 每份 yml 编译成一场演出
    └── example.yml
```

## 命令

| 命令 | 说明 |
| --- | --- |
| `/fireworks help` | 帮助 |
| `/fireworks font` | 列出已加载的字体 |
| `/fireworks font <字体> <字符>` | 在聊天框里预览某个字符的点阵 |
| `/fireworks execute <演出> <x> <y> <z> [世界]` | 在指定坐标放一场演出 |
| `/fireworks item <演出>` | 拿一枚右键燃放该演出的烟花道具 |
| `/fireworks toggle` | 开关自己的随机烟花 |
| `/fireworks reload` | 重载配置与演出 |
| `/fireworks check` | 只校验配置，不重载也不写文件 |
| `/firework-test` | 用手上的烟花试放一个假烟花实体（调试用） |

## 权限

所有权限都在 `powerfulfireworks` 下，根节点默认仅 OP。

| 权限 | 对应命令 |
| --- | --- |
| `powerfulfireworks.cmd.fireworks` | `/fireworks` 本身与 `help`、`font` |
| `powerfulfireworks.cmd.fireworks.font` | `/fireworks font <字体> <字符>` |
| `powerfulfireworks.cmd.fireworks.execute` | `/fireworks execute` |
| `powerfulfireworks.cmd.fireworks.item` | `/fireworks item` |
| `powerfulfireworks.cmd.fireworks.toggle` | `/fireworks toggle` |
| `powerfulfireworks.cmd.fireworks.reload` | `/fireworks reload` |
| `powerfulfireworks.cmd.fireworks.check` | `/fireworks check` |
| `powerfulfireworks.cmd.test` | `/firework-test` |

## 文档

- [config.yml 配置项](docs/configuration.md)
- [演出与节点（fireworks/*.yml）](docs/schedulers.md)
- [点阵字体（BDF）](docs/configuration.md#字体)

## 从源码构建

需要 Gradle 能发现的 JDK 17、21、25（旧 NMS 模块的 dev bundle 要用 17 反编译，1.21.4 用 21，26.3 用 25）：

```bash
./gradlew shadowJar
```

产物在 `build/libs/PowerfulFireworks-<version>-all.jar`。

如果 Gradle 找不到这几个 JDK，在 `~/.gradle/gradle.properties` 里指定：

```properties
org.gradle.java.installations.paths=/path/to/jdk-17,/path/to/jdk-21,/path/to/jdk-25
```

## 第三方

- 配置框架的源码取自 PepperLib（MIT 许可，见 `LICENSE-pepperlib.txt`），已经内置在插件里，不需要额外安装前置。
