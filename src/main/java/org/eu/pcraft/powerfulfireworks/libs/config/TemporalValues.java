package org.eu.pcraft.powerfulfireworks.libs.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 时间值支持（package-private，对齐 ConfigMe {@code TemporalType} 的多格式宽容解析）：
 * {@code LocalDate}/{@code LocalTime}/{@code LocalDateTime} 从字符串或 snakeyaml 解析出的
 * {@link java.util.Date} 转换。
 *
 * <p>snakeyaml 2.6 对未加引号的 ISO 日期（{@code 2026-01-10}）与 sexagesimal 时间
 * （{@code 12:34:56}）会做隐式类型解析，管理员手写配置常因此得到 {@link java.util.Date}——
 * 本助手对此宽容（按系统时区取本地日历），字符串则按多重格式依次尝试（ConfigMe
 * {@code TemporalType} 同思路）。</p>
 */
final class TemporalValues {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private TemporalValues() {}

    /** 把值解析为指定时间类；无法解析返回 null（不抛）。 */
    static Object parse(Object raw, Class<?> target) {
        if (raw == null) {
            return null;
        }
        Date asDate = raw instanceof Date d ? d : null;
        if (target == LocalDate.class) {
            if (asDate != null) {
                return asDate.toInstant().atZone(ZONE).toLocalDate();
            }
            if (raw instanceof String s) {
                return parseDate(s);
            }
            return null;
        }
        if (target == LocalTime.class) {
            if (asDate != null) {
                return asDate.toInstant().atZone(ZONE).toLocalTime();
            }
            if (raw instanceof String s) {
                return parseTime(s);
            }
            return null;
        }
        if (target == LocalDateTime.class) {
            if (asDate != null) {
                return asDate.toInstant().atZone(ZONE).toLocalDateTime();
            }
            if (raw instanceof String s) {
                return parseDateTime(s);
            }
            return null;
        }
        return null;
    }

    private static LocalDate parseDate(String s) {
        for (String p : new String[] {"uuuu-MM-dd", "dd.MM.uuuu", "MM/dd/uuuu"}) {
            try {
                return LocalDate.parse(s, DateTimeFormatter.ofPattern(p));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private static LocalTime parseTime(String s) {
        for (String p : new String[] {"HH:mm", "HH:mm:ss", "HH.mm"}) {
            try {
                return LocalTime.parse(s, DateTimeFormatter.ofPattern(p));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private static LocalDateTime parseDateTime(String s) {
        for (String p : new String[] {
            "uuuu-MM-dd'T'HH:mm",
            "uuuu-MM-dd'T'HH:mm:ss",
            "uuuu-MM-dd HH:mm",
            "uuuu-MM-dd HH:mm:ss",
            "dd.MM.uuuu HH:mm:ss",
            "MM/dd/uuuu HH:mm:ss"
        }) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(p));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }
}
