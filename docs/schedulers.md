# 演出与节点（fireworks/*.yml）

`plugins/PowerfulFireworks/fireworks/` 下的每个 `.yml` / `.yaml` 会编译成一场演出，文件名随意，演出 id 由文件里的 `id` 决定。`/fireworks reload` 会重新编译全部文件；某份文件编译失败只会在日志里留一条 `Failed compiling firework from ...`，不影响其它演出。

## 骨架

```yaml
id: newyear
allowActivationByItems: true

presets:
  red:
    effects:
      - type: BALL
        colors: ["#FF0000"]
        fade: ["#FFAA00"]
        flicker: false
        trail: true

nodes:
  - type: original
    count: 3
    flyTime: 40
    xOff: [-6, 6]
    zOff: [-6, 6]
  - type: wait
    ticks: 80
  - type: text
    font: example
    text: "HAPPY NEW YEAR"
    size: 1.5
    flyTime: 45
```

`id` 必填。`presets` 和 `nodes` 也必须存在，没有内容时写 `{}` 和 `[]`。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `id` | 必填 | 演出 id，`/fireworks execute <id>` 用它，也是引用节点的 `target` |
| `allowActivationByItems` | `false` | 是否允许用 `/fireworks item <id>` 拿到的道具触发 |
| `presets` | 必填 | 预设烟花，见下 |
| `nodes` | 必填 | 节点列表，按顺序执行 |

## 校验

每份演出在加载时都会被检查一遍，规则和 `config.yml` 一样分 `ERROR` / `WARN` 两档（见 [配置校验](configuration.md#配置校验)）。这里只列演出侧特有的：

- `ERROR`：节点类型写错、`presets` / `nodes` 缺失、`id` 与别的文件重复（后一份会被跳过）、引用成环（环上所有演出整组跳过）、引用了不存在的演出或预设、字体没有加载、`face` 写成 `UP` / `DOWN`、数量或点阵间距小于等于 0；
- `WARN`：节点里写了不认识的键（拼错时最常见）、`flyTime` 超过 56 tick、`count` 大于 50、`wait.ticks` 为 0 或负数以外的异常值。

`/fireworks check` 可以只跑检查、不重载。

## presets

每个预设是一枚已经配好效果的烟花物品：

```yaml
presets:
  big_red:
    effects:
      - type: BALL_LARGE
        colors: ["#FF0000", "#FF4400"]
        fade: ["#FFAA00"]
        flicker: false
        trail: true
```

一个预设可以写多个 `effects`，会叠在同一枚烟花上（原版一次爆炸的球体数量有限制，叠太多效果看不出差别）。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `type` | `BALL` | `BALL` / `BALL_LARGE` / `STAR` / `BURST` / `CREEPER` |
| `colors` | 无 | 颜色列表，`"#RRGGBB"` 或 `"0xRRGGBB"` |
| `fade`（也接受 `fades`） | 无 | 爆炸后的渐变颜色列表，写法同上 |
| `flicker` | `false` | 闪烁 |
| `trail` | `false` | 拖尾 |

## 节点

节点按数组顺序执行，**没有 `wait` 的节点会在同一个 tick 里连续跑完**，单个 tick 最多执行 64 个节点，超出的部分顺延到下一 tick。这个额度对整条引用链共享——A 引用 B 时 B 花的是 A 剩下的额度，不会各自再拿 64 个。只有 `wait` 会主动让出时间；`blocking: true` 的 `reference` 会等对方整场跑完再继续，续跑时按新的一 tick 重新计数。整个演出用同一个起点和同一份玩家名单；每进入一个 tick 前会重新筛一遍观众，只有水平距离在服务端视距 × 16 以内的玩家才会收到包。

### 通用字段（`original` / `text`）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `flyTime` | `0` | 火箭升空多久后爆炸（tick），会被客户端 64 格渲染范围裁剪，见 [config 的升空时间](configuration.md#升空时间) |
| `presets` | 无 | 从演出预设里随机取一个；不写就用随机生成的烟花 |
| `launchChance` | `0.1` | 该节点下每支烟花发射声的播放概率 |
| `explosionChance` | `0.2` | 该节点下每支烟花爆炸声的播放概率 |

### original

在起点附近放若干支普通烟花。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `count` | `1` | 放几支 |
| `xOff` | `0` | X 方向偏移，区间写法，每一支独立取值 |
| `yOff` | `0` | Y 方向偏移 |
| `zOff` | `0` | Z 方向偏移 |

### text

把文字渲染成点阵烟花。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `font` | 必填 | `config.yml` 里 `fonts` 配置的字体 id |
| `text` | 必填 | 要写的字，单行，不支持换行 |
| `size` | `1.0` | 点与点之间的间距（格），1.0 大约 1 格一个点 |
| `gap` | `1` | 相邻字符之间空几列像素 |
| `rotate` | `false` | `true` 时让每个玩家都看到正对自己的文字 |
| `face` | `EAST` | `rotate: false` 时的固定朝向，取 `BlockFace` 的名字（`NORTH` / `SOUTH` / `EAST` / `WEST`） |

字符多的句子会生成很多支火箭，字越大、间隔越大占的格数也越多，注意别一次放飞几千支。

### wait

单纯等待，用来控制两段烟花之间的间隔。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `ticks` | `1` | 等待多少 tick（20 tick = 1 秒） |

### reference

把别的演出按同样的起点和玩家名单再放一遍，用来复用桥段。

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `target` | 必填 | 另一份演出的 `id`，不能引用自己 |
| `blocking` | `false` | 是否等被引用的演出整场跑完再继续本演出的下一个节点 |

默认是「发起后立刻继续」：被引用的演出与本演出并行，后面的节点不会等它。需要严格串行（比如开场白放完再放主烟花）就写 `blocking: true`，注意它会把整场演出的时间拉长，中间等着的时候不要再用 `wait` 重复叠加。

不要在引用上成环（A 引用 B、B 又引用 A）。加载时会做一次环检测，发现环就把环上所有演出整组跳过并在控制台报出完整路径（如 `a -> b -> c -> a`）；如果环是在演出跑起来之后才通过改文件引入的，删掉其中任意一条 `reference`、再执行 `/fireworks reload` 即可。

## 用道具触发

给演出写上 `allowActivationByItems: true` 之后：

1. `/fireworks item <id>` 拿到一枚带标记的烟花
2. 玩家点燃它，火箭正常升空
3. 火箭爆炸时插件取消这次原版爆炸，改在爆炸位置执行整场演出

所以道具烟花本身只是一次"引信"，真正的效果由 `nodes` 决定，和其它玩家的视线无关。

## 一个完整例子

```yaml
id: example
allowActivationByItems: true

presets:
  red:
    effects:
      - type: BALL
        colors: ["#FF0000"]
        fade: ["#FFAA00"]
        trail: true
  blue:
    effects:
      - type: STAR
        colors: ["#0088FF"]
        flicker: true

nodes:
  - type: original
    presets: [red]
    count: 6
    flyTime: 35
    xOff: [-8, 8]
    zOff: [-8, 8]
    launchChance: 0.15
    explosionChance: 0.4
  - type: wait
    ticks: 60
  - type: text
    font: example
    text: "HELLO"
    size: 1.2
    gap: 2
    flyTime: 40
    launchChance: 0.05
    explosionChance: 0.2
  - type: wait
    ticks: 80
  - type: original
    presets: [blue]
    count: 4
    flyTime: 30
    yOff: [0, 6]
    explosionChance: 0.5
  - type: reference
    target: other_show
```
