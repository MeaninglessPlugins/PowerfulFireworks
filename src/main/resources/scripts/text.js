/**
 * 逐行点亮一行字：每行从同一高度起飞，用不同的初速度在 fuse 内拉开高度。
 *
 * 用法：/fireworks script run text
 */
function* main(ctx) {
    const fuse = 40;
    const shape = ctx.text({
        text: "Hi",
        font: ctx.params.font || "example",
        size: 1,
        gap: 1,
        face: "NORTH"
    });

    ctx.log("点阵 " + shape.width + " x " + shape.height + "，共 " + shape.rows.length + " 行");

    // Rhino 的两个坑：不认 for (const x of ...)；循环体里用 const 声明只会赋值一次，
    // 所以这里用下标 + var。
    for (let i = 0; i < shape.rows.length; i++) {
        var row = shape.rows[i];
        if (row.points.length === 0) {
            continue;
        }
        ctx.points({
            count: 1,
            fuse: fuse,
            velocity: row.lift / fuse,
            colors: ["#FFCC00"],
            type: "STAR"
        }, row.points);
        yield ctx.wait(1);
    }
}
