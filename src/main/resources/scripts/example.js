// 示例演出：每 20 tick 从原点放一发，共五发，同时撒一圈粒子。
//
// 放到 plugins/PowerfulFireworks/scripts/ 下即可，
// 用 /fireworks script run example 启动（原点取执行者当前位置）。

function* main(ctx) {
    var colors = ["#FFCC00", "#00CCFF", "#FF5C8A"];

    for (var i = 0; i < 5; i++) {
        ctx.firework({
            count: 1,
            colors: [colors[i % colors.length]],
            power: 2
        });
        // 粒子与烟花平级：同样的原点、同样的观众，只是走粒子包
        ctx.particle({
            particle: "dust",
            color: colors[i % colors.length],
            count: 16,
            spread: 0.8,
            size: 1.5
        });
        yield ctx.wait(20);
    }
}
