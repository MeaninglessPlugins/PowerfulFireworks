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

两端相同就等价于固定值。生成默认文件时，两端相同会写成单值，否则写成 `minimum` / `maximum`。

## 文件生命周期

- 第一次启动会生成带注释的完整默认文件；
- 之后每次加载只把新版本里新增的键补到对应节的末尾，已有的键、你自己写的注释和顺序都不会被动；
- 某个值写坏了会用默认值继续跑，并在控制台列出出问题的键和原因。

## 配置校验

加载时会顺带检查一遍 `config.yml`、`messages.yml` 与 `fireworks/*.yml`，问题分两档：

| 级别 | 含义 | 例子 |
| --- | --- | --- |
| `ERROR` | 这一项不会按你写的生效：已经回落成默认值，或者整份文件被跳过 | `flyTime` 最小值大于最大值、概率写成 `1.5`、引用了不存在的预设、两个文件用了同一个 `id` |
| `WARN` | 能跑，但会在已知的边界上丢效果 | `flyTime` 超过 56 tick（火箭这时候已经在 64 格外）、`automaticDistance: true` 时还写了 `distance`、节点里写了不认识的键、`fonts` 指向的 BDF 文件不存在（文本烟花退回普通烟花） |

只想看看有没有问题，不想重载：

```text
/fireworks check
```

它不写文件、也不替换正在运行的演出，只在控制台打印诊断。默认最多列 10 条警告，错误永远全列；把 `debug` 设成 `true` 可以看到全部，没有任何问题时也会打一行通过。

同一个键只报一次。类型写错（例如 `debug: "yes"`、`distance: "abc"`）也会按 `ERROR` 报出来，因为它在读取时已经静默回落成默认值了。

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
  velocity: 0.0
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
    velocity: 0.0

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
| `velocity` | `0.0` | 火箭的竖直初速度（格/tick），见下面的「初速度」 |
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
| `velocity` | `0.0` | 叠加在每个点上的竖直初速度（格/tick），会把整块字一起升高 |

文本烟花只发给触发它的那个玩家，点的数量多的句子会比较壮观，注意别让 `size` 和句子长度一起溢出。

## 初速度

`velocity` 是火箭的**竖直**初速度，单位格/tick，作用在实体生成包的速度分量上：

- 不写或写 `0` 时，火箭完全靠客户端自己施加的加速度上升：`y(t) = 0.02·t·(t+1)`，40 tick 约 33 格。
- 写成 `v` 后，高度变成 `y(t) = v·t + 0.02·t·(t+1)`，上升更快、爆炸更高。`flyTime` 不变时爆炸位置会整体抬高，可能出现"炸得太高看不见"——超过 64 格后客户端的火花会消失，所以调大 `velocity` 通常要同步减小 `flyTime`。
- 负数会让火箭先下沉再上升，一般用不到。

文本烟花内部就是用这个机制铺开的：所有点从**同一个高度**出发，第 n 行（从下往上数）额外带上 `n·size / flyTime` 的初速度，于是爆炸时的落点与"每一行起点抬高 `n·size`"完全一致。`velocity` 是在这之上再叠一个整体偏移，会让整块字一起升高。

## fonts

字体 id 到 `plugins/PowerfulFireworks/fonts/` 下文件名的映射：

```yaml
fonts:
  example: example.bdf      # /fireworks font example 'A' 可以预览
  cn: chinese.bdf
```

id 不区分大小写，`/fireworks font` 会列出全部已加载的 id。文件缺失或解析失败只会打一条 warn，不影响插件启动。

插件自带一份 `example.bdf`（6×8 的 ASCII 点阵，由 DejaVu Sans Mono 光栅化而来），配置里引用它、目录里又没有这个文件时会自动释放出来。所以想换成自己的字体，改 `fonts` 段或者在目录里放同名文件覆盖它都行；把文件删掉但配置里还留着引用，下次启动会被重新释放。

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
