# 脚本（scripts/*.js）

`plugins/PowerfulFireworks/scripts/` 下的每个 `.js` 会用 Rhino 编译成一个脚本，**文件名（去掉 `.js`）就是脚本 id**。脚本适合写 YAML 节点表达不了的逐 tick 编排；`/fireworks reload` 会重新编译全部脚本，某个脚本编译失败只会在日志里留一条诊断并跳过，不影响插件启动。目录里一个脚本都没有时，插件会释放 `example.js` 和 `text.js` 两份示例。

## 跑起来

```js
function* main(ctx) {
    ctx.firework({ colors: ["#FF0000"], fuse: 40 });
    yield ctx.wait(20);
    ctx.firework({ colors: ["#00FF00"], count: 3 });
}
```

| 命令 | 说明 |
| --- | --- |
| `/fireworks script run <id> [seed]` | 以执行者当前位置为原点启动一次；`seed` 固定整场演出的随机（`ctx.rand`、颜色/形状/抖动、发射与爆炸声） |
| `/fireworks stop` | 停止所有正在跑的脚本；已发射的火箭会按原 `fuse` 飞完后消失 |
| `/fireworks reload` | 先停掉所有运行，再重新编译配置与脚本 |

权限：`powerfulfireworks.cmd.fireworks.script`、`powerfulfireworks.cmd.fireworks.stop`。`script run` 只能由玩家执行。

## 执行模型

- 加载期编译一次；启动期执行脚本顶层并调用 `main(ctx)` 拿到生成器。
- 之后由服务端按 tick 恢复执行，`yield` 让出执行权；脚本之间互不阻塞。
- 脚本在主线程执行，**不要写阻塞代码**。每个 tick 的指令数有预算，超了这次运行会失败。
- 脚本对象（含 `ctx.params`）不会跨运行共享；`ctx` 是脚本影响服务器的唯一出口。

## yield 协议

`yield` 的值决定下一步：

| 写法 | 含义 |
| --- | --- |
| `yield ctx.wait(n)` | 等 n tick（1 ≤ n ≤ 24000，超出按 24000 处理） |
| `yield ctx.run("other", {...})` | 启动子脚本，等它结束后继续 |
| `yield`（不带值） | 等 1 tick |
| 其它值 | 运行失败，日志里会给出这个值 |

`ctx.run` 会立即启动子脚本，返回值必须 `yield` 才会等待；`ctx.spawn` 只启动、不等待。

## ctx API

| 成员 | 说明 |
| --- | --- |
| `ctx.tick` | 当前服务端 tick |
| `ctx.params` | 父脚本通过 `ctx.run`/`ctx.spawn` 传入的参数对象 |
| `ctx.log(...)` / `ctx.warn(...)` | 写日志，自动加 `[脚本]` 前缀 |
| `ctx.wait(n)` | 构造等待指令，必须 `yield` |
| `ctx.firework(spec[, audience])` | 发射烟花 |
| `ctx.particle(spec[, audience])` | 发射粒子 |
| `ctx.points(spec, [[x, y, z], ...][, audience])` | 在点集上发射，最多 1024 个点 |
| `ctx.text({font, text, size, gap, face})` | 把文字摊成点阵，返回 `{width, height, rows}`，不发射 |
| `ctx.run(id[, params])` | 启动子脚本，返回 `{join}`；必须 `yield` 才会等待 |
| `ctx.spawn(id[, params])` | 启动子脚本，返回 `undefined`；发射后不管 |
| `ctx.rand.int/float/pick/chance` | 随机数，受启动时的 `seed` 控制 |
| `ctx.audience.default/all/none/world/radius/players` | 观众规则 |

省略 `audience` 时按原版规则：烟花发给实体跟踪范围内的玩家，粒子发给 32/512 格内的玩家。

`ctx.audience` 的用法：`default()` 原版规则；`all()` / `world([名字])` 整个世界（默认发射所在的世界）；`none()` 谁都不发；`players([名字])` 点名；`radius(r[, center])` 按半径（中心相对原点，省略时用原点）。

`ctx.spawn` 返回 `undefined`：`yield ctx.spawn(...)` 会被当成未知的 yield 值报错，想等子脚本结束请用 `yield ctx.run(...)`。

### ctx.firework

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `count` | `1` | 火箭数量，1..64 |
| `colors` | 随机两色 | `#RRGGBB` 或字符串数组 |
| `fade` | 空 | 渐隐色，写法同 `colors` |
| `type` | 随机 | `BALL` / `BALL_LARGE` / `STAR` / `BURST` / `CREEPER` |
| `power` | `2` | 引信长度 0..5 |
| `trail` / `flicker` | `false` | 拖尾 / 闪烁 |
| `velocity` | `0` | 额外初速度（格/tick） |
| `offset` | `[0, 0, 0]` | 相对原点，支持 `[x, y, z]` 或 `{x, y, z}` |
| `launchChance` | `0.1` | 发射声的播放概率 |
| `explosionChance` | `0.2` | 爆炸声的播放概率 |
| `fuse` | `40` | 从升空到爆炸的 tick 数，1..200 |

`ctx.particle` 的键：`particle`（必填）、`count`、`spread`（数字或 `[x, y, z]`）、`speed`、`size`、`color`、`fade`、`offset`、`force`（默认 `true`）。

`color` / `fade` / `size` 只对 `DUST` / `DUST_COLOR_TRANSITION` 生效，其它粒子会忽略这些键。

`ctx.text` 的 `face` 只接受水平四向（`EAST` / `WEST` / `NORTH` / `SOUTH`，默认 `NORTH`）；`rotate` 暂不支持。返回的每一行带 `lift`，点本身 `y` 恒为 0，需要脚本自己按行调整等待与速度。

## 可见性由脚本负责

这一点和 YAML 节点不同：**宿主不裁剪 `fuse`**，飞多远、什么时候爆炸都由脚本决定。

火箭是纯客户端假实体，客户端只渲染 64 格以内（服务端无法关闭）。超出后火箭会中途消失，爆炸落在视野外。上升高度近似：

```text
rise(t) = 0.02 * t * (t + 1) + velocity * t
```

按 8 格安全余量、观众站在原点算，约第 54 tick 是安全上限（硬性的 64 格边界约在第 57 tick）：

| 写法 | 爆炸时高度 | 结果 |
| --- | --- | --- |
| `fuse: 40, velocity: 0`（默认） | 32.8 格 | 可见 |
| `fuse: 60` | 73.2 格 | 爆炸在视野外（约第 54 tick 起超出安全范围） |
| `velocity: 1, fuse: 40` | 72.8 格 | 爆炸在视野外（约第 34 tick 起超出安全范围） |

当 `fuse` 超过这个安全上限（也就是 YAML 节点会被裁剪到上限的情况），宿主会在日志里**告警一次**（带发射坐标、`fuse`、`velocity` 和可见上限），但**不会改变行为**。这是有意的：远处观众本来就可能看不清，是否照顾他们由脚本作者决定。

自查：把 `velocity * fuse + 0.02 * fuse * (fuse + 1)` 和 56 比一下，或直接 `/fireworks script run` 在原点看效果。YAML 节点的 `flyTime` 由宿主自动裁剪，脚本没有这层保护。

## 坐标系

- `ctx.firework.offset`、`ctx.points`、`ctx.text` 返回的点：相对**演出原点**（`script run` 时执行者的位置）。
- `ctx.audience.radius(r, center)` 的中心：相对**演出原点**，写法同 `offset`；省略中心时用原点。半径只在原点所在的世界生效。

## 声音

`launchChance` 与 `explosionChance` 分别控制发射声、爆炸声是否播放，默认 `0.1` 与 `0.2`（即默认大部分时间静音），需要声音时显式写 `1`。

## Rhino 1.9.2 的语言限制

- `const` 在循环体内只初始化一次（`for (...) { const v = ... }` 只有第一轮生效），循环里用 `let` 或 `var`。
- `for (const x of arr)` / `for (let x of arr)` 是语法错误；用 `for (var x of arr)`。

## 已知边界

- 运行期抛错会让整个运行失败；YAML 节点则是“单个节点出错就跳过”。
- `/fireworks stop` 之后不再触发新的爆炸；已经发射的火箭会按原定 `fuse` 飞到最后再消失（最长约 10 秒），不会补爆炸声或粒子。
- 入口必须是 `function* main(ctx)`；`this.main = function* ...` 这类写法不会被识别。
- 单个 tick 有指令预算，超了这次运行会失败；不要在一个 tick 里写超长循环。
- 脚本目前只能通过 `/fireworks script run` 启动，不能挂到 YAML 演出或物品触发上。

## 示例

`scripts/example.js` 演示烟花与粒子，`scripts/text.js` 演示逐行文字。
