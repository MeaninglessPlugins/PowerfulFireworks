package org.eu.pcraft.powerfulfireworks.script;

import static org.mozilla.javascript.ScriptableObject.putProperty;

import java.util.List;
import java.util.TreeSet;

import org.eu.pcraft.powerfulfireworks.utils.BitmapFont;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

/**
 * 把 {@code ctx.text} 的参数摊成点阵对象；只产出形状，不发射。
 */
final class ScriptText {

    private ScriptText() {
    }

    /**
     * 把一段文字摊成点阵坐标，但不发射。
     *
     * <p>返回 {@code {width, height, rows: [{index, lift, points}]}}。点相对演出原点，
     * y 恒为 0——所有点从同一高度起飞，靠每行的 {@code lift} 在飞行末拉开高度；
     * 这样脚本可以决定每行用多少 tick、什么速度，甚至换粒子。
     */
    static Scriptable shape(ScriptHost host, Context cx, Scriptable scope, Scriptable spec) {
        String content = ScriptRequests.requiredString(spec, "text");
        String fontId = ScriptRequests.requiredString(spec, "font");
        double size = ScriptRequests.number(spec, "size", 1);
        int gap = (int) ScriptRequests.number(spec, "gap", 1);
        if (size <= 0) {
            throw Context.reportRuntimeError("size 必须大于 0");
        }
        if (gap < 0) {
            throw Context.reportRuntimeError("gap 不能为负");
        }
        if (ScriptRequests.bool(spec, "rotate", false)) {
            throw Context.reportRuntimeError("ctx.text 暂不支持 rotate（逐玩家朝向），请用 face 指定水平朝向");
        }

        BitmapFont font = host.font(fontId);
        if (font == null) {
            throw Context.reportRuntimeError("没有字体 " + fontId + "，可用："
                    + String.join(" / ", new TreeSet<>(host.fonts())));
        }

        ScriptRequests.Face face = ScriptRequests.face(spec);
        BitmapFont.CharBitmap bitmap = font.fromString(content, gap);
        List<BitmapFont.CharBitmap.Row> rows = bitmap.rows(size, face.xMod(), face.zMod());

        Scriptable out = cx.newObject(scope);
        putProperty(out, "width", bitmap.width());
        putProperty(out, "height", bitmap.height());
        Object[] rowArray = new Object[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            BitmapFont.CharBitmap.Row row = rows.get(r);
            Scriptable item = cx.newObject(scope);
            putProperty(item, "index", row.index());
            putProperty(item, "lift", row.lift());
            Object[] points = new Object[row.points().size()];
            for (int p = 0; p < points.length; p++) {
                double[] point = row.points().get(p);
                points[p] = cx.newArray(scope, new Object[]{point[0], point[1], point[2]});
            }
            putProperty(item, "points", cx.newArray(scope, points));
            rowArray[r] = item;
        }
        putProperty(out, "rows", cx.newArray(scope, rowArray));
        return out;
    }
}
