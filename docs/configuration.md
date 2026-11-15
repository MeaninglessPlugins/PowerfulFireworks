# config.yml

位置：`plugins/PowerfulFireworks/config.yml`。改完用 `/fireworks reload` 重载；`fonts` 里的改动建议直接重启。

## 区间写法

`delay`、`number`、`flyTime`、节点里的 `xOff` / `yOff` / `zOff` 这些接受区间的字段，都支持三种写法：

```yaml
flyTime: 40                    # 固定值
flyTime: [25, 50]              # 25 到 50 之间随机（含两端）
flyTime:
  minimum: 25
  maximum: 50
```

两端相同就等价于固定值。插件回写配置时，两端相同会写成单值，否则写成 `minimum` / `maximum`。

## 完整示例

```yaml
debug: false

randomFirework:
  enabled: true
  turnOnByDefault: true
  delay:
    minimum: 10
    maximum: 20
  number:
    minimum: 2
    maximum: 5
  automaticDistance: true
  distance: 20
  flyTime:
    minimum: 25
    maximum: 50
  launchChance: 0.1
  explosionChance: 0.2
  blacklistWorlds:
    - world_nether
  text:
    enabled: true
    size: 1.0
    font: example
    texts:
      - Hello World!
    gap: 2
    chance: 0.05
    launchChance: 0.05
    explosionChance: 0.1

fonts:
  example: example.bdf
```

## 顶层

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `debug` | `false` | 打开后会把每场演出的节点执行、飞行时长裁剪等信息打进日志 |

## randomFirework

随机烟花：每隔一段时间，给每个收听的玩家在附近随机位置放几支。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否开启随机烟花；关掉后只保留 `/fireworks execute` 这类手动触发 |
| `turnOnByDefault` | `true` | 新玩家默认是否收听随机烟花，之后可用 `/fireworks toggle` 改，状态存在玩家的持久化数据里 |
| `delay` | `10`–`20` | 两轮之间的间隔（tick） |
| `number` | `2`–`5` | 每轮给**每个玩家**放几支 |
| `automaticDistance` | `true` | 生成半径取服务端视距 × 16；关掉则用 `distance` |
| `distance` | `20` | 手动指定生成半径（格） |
| `flyTime` | `25`–`50` | 火箭升空多久后爆炸（tick），见下面的「升空时间」 |
| `launchChance` | `0.1` | 普通烟花**发射声**的播放概率，每支独立掷一次 |
| `explosionChance` | `0.2` | 普通烟花**爆炸声**的播放概率，每支独立掷一次 |
| `blacklistWorlds` | `[world_nether]` | 不生成随机烟花的世界 |

生成位置在高空：先在以玩家为圆心、半径为上面那个值的圆内均匀取一点，再把 Y 抬到该列最高方块的上一格。

## randomFirework.text

随机文本烟花：每支烟花有 `chance` 的概率被替换成一句文字。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否允许随机出现文本烟花 |
| `chance` | `0.05` | 每支烟花变成文本烟花的概率 |
| `font` | `example` | 用 `fonts` 里的哪个字体 |
| `texts` | `[Hello World!]` | 随机取一句，单行，不支持换行 |
| `size` | `1.0` | 点阵每个点与相邻点的间距（格），调大就是更大的字 |
| `gap` | `2` | 相邻两个字符之间空几列像素 |
| `launchChance` | `0.05` | 文本烟花发射声的播放概率 |
| `explosionChance` | `0.1` | 文本烟花爆炸声的播放概率 |

文本烟花只发给触发它的那个玩家，点的数量多的句子会比较壮观，注意别让 `size` 和句子长度一起溢出。

## fonts

字体 id 到 `plugins/PowerfulFireworks/fonts/` 下文件名的映射：

```yaml
fonts:
  example: example.bdf      # /fireworks font example 'A' 可以预览
  cn: chinese.bdf
```

id 不区分大小写，`/fireworks font` 会列出全部已加载的 id。文件缺失或解析失败只会打一条 warn，不影响插件启动。

## 升空时间

`flyTime` 是服务端自己算的：到点了就发实体事件让客户端爆炸，火箭本身在服务端不 tick。

客户端的 `FireworkRocketEntity` 有一个写死的渲染范围：离玩家的平方距离必须小于 `4096`，也就是 64 格，服务端关不掉。而火箭在客户端上升的轨迹是

```
v(t) = 0.04t,  y(t) = 0.02t(t + 1)
```

大约 56 tick 就升到 64 格。所以插件会把 `flyTime` 裁剪到"爆炸那一刻仍在观看者视野内"的 tick 数（留 8 格余量）；`debug: true` 时日志里能看到 `flyTime 70 -> 56` 这样的记录。

想放得更高，只能让观看者站得更远，或者接受火箭中途从客户端消失。

## 声音

假烟花实体和普通实体一样有 `DATA_SILENT` 这一项同步数据，插件在发包时按概率把它设成静音：

- **发射声（嗖）**：客户端在实体生成后的第一个 tick 播放，需要在生成包的数据里就决定
- **爆炸声（砰）**：客户端在收到爆炸事件时读取同一项数据

因此在 26.3 上，插件会在引爆前额外补一个只含 `DATA_SILENT` 的数据包，让两种声音各自按各自的概率播放。

1.21.5 及更早的版本里爆炸由客户端无条件播放、没有声音开关，`explosionChance` 会被忽略，只有发射声可控。
