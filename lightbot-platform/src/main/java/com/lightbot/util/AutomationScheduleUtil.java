package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.automation.AutomationJobSaveDTO;
import com.lightbot.enums.AutomationScheduleType;
import com.lightbot.enums.ErrorCode;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 自动化调度：校验、配置序列化、下次触发计算（分钟级即可）。
 * <p>内部统一转为 Spring 6 段 Cron（秒 分 时 日 月 周），周字段用 Quartz 语义（1=周日…7=周六）。
 *
 * @author finch
 * @since 2026-07-26
 */
public final class AutomationScheduleUtil {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final DateTimeFormatter ONCE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter ONCE_FMT_SEC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AutomationScheduleUtil() {
    }

    public static AutomationScheduleType parseType(String raw) {
        try {
            return AutomationScheduleType.fromValue(raw);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "未知触发方式");
        }
    }

    private static final Pattern RELATIVE_DAYS = Pattern.compile("^\\+(\\d+)d$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CN_TIME = Pattern.compile("^(\\d{1,2})点(\\d{1,2})?$");

    /**
     * Tool 参数说明用：丰富示例（静态部分，便于写入 Schema）。
     */
    public static String scheduleParamGuide() {
        return ""
                + "【时区】Asia/Shanghai。"
                + "【一次性 once｜用户说「明天/后天/今天X点」务必用相对日，禁止猜绝对日期】"
                + " once tomorrow 09:00（明天9:00）；once tomorrow 09:30；"
                + " once 明天 09:00；once today 18:00；once 今天 21:00；"
                + " once +1d 09:00（+1天=明天）；once +2d 09:00（后天）；once +7d 10:00；"
                + " once 2026-08-01 10:00（绝对日期，必须晚于当前时间）。"
                + "【每天 daily】daily 09:00；daily 9:00；daily 09:30；每天 09:00；每日 08:30。"
                + "【每周 weekly｜1=周一…7=周日】weekly 1,3,5 09:00（周一三五）；"
                + " weekly 1 09:00（每周一）；weekly 一,三,五 09:00；weekly mon,wed,fri 09:00；每周 1,5 18:00。"
                + "【每月 monthly】monthly 1 09:00（每月1号9点）；monthly 15 18:30；每月 1 09:00。"
                + "【Cron】cron 0 9 * * *（每天9:00，5段：分 时 日 月 周）；cron 0 9 * * 1-5（工作日9:00）。"
                + "【反例勿用】once 2026-07-27 09:00 表示「今天」易过期；勿写 once 明天早上九点（须 HH:mm）；"
                + "勿把星期写成 0=周日；勿省略类型写成 09:00。"
                + "【对照】用户「明天早上9点查天气」→ schedule=once tomorrow 09:00；"
                + "「每天下午6点」→ daily 18:00；「每周一三五早上9点」→ weekly 1,3,5 09:00。";
    }

    /**
     * 解析失败 / 过期时的完整帮助（含当前时间）。
     */
    public static String scheduleExprHelp() {
        return scheduleParamGuide() + " 当前时间 " + LocalDateTime.now(ZONE).format(ONCE_FMT) + "。";
    }

    /**
     * 解析 Tool 入参 schedule 串，写入 DTO 的调度相关字段。
     *
     * @param schedule 调度串
     * @param dto      目标 DTO（只改调度字段）
     */
    public static void applyScheduleExpr(String schedule, AutomationJobSaveDTO dto) {
        if (dto == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "dto 不能为空");
        }
        if (!StringUtils.hasText(schedule)) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "schedule 不能为空。" + scheduleExprHelp());
        }
        String raw = normalizeScheduleRaw(schedule);
        String[] parts = raw.split(" ");
        String typeToken = parts[0].toLowerCase(Locale.ROOT);
        try {
            switch (typeToken) {
                case "once" -> {
                    if (parts.length < 3) {
                        throw invalidSchedule("once 格式应为：once tomorrow 09:00 或 once yyyy-MM-dd HH:mm");
                    }
                    LocalDateTime onceAt = resolveOnceDateTime(parts);
                    dto.setScheduleType("once");
                    dto.setOnceAt(onceAt.format(ONCE_FMT));
                    dto.setTime(null);
                    dto.setWeekdays(null);
                    dto.setMonthDay(null);
                    dto.setCron(null);
                }
                case "daily" -> {
                    if (parts.length != 2) {
                        throw invalidSchedule("daily 格式应为：daily HH:mm，例如 daily 09:00");
                    }
                    dto.setScheduleType("daily");
                    dto.setTime(normalizeTime(parts[1]));
                    dto.setOnceAt(null);
                    dto.setWeekdays(null);
                    dto.setMonthDay(null);
                    dto.setCron(null);
                }
                case "weekly" -> {
                    // 末段为时间，中间为星期（兼容 weekly 1, 3, 5 09:00）
                    if (parts.length < 3) {
                        throw invalidSchedule("weekly 格式应为：weekly 星期列表 HH:mm，例如 weekly 1,3,5 09:00（1=周一…7=周日）");
                    }
                    String timeToken = parts[parts.length - 1];
                    String daysToken = String.join(",", java.util.Arrays.copyOfRange(parts, 1, parts.length - 1));
                    dto.setScheduleType("weekly");
                    dto.setWeekdays(parseWeekdayToken(daysToken));
                    dto.setTime(normalizeTime(timeToken));
                    dto.setOnceAt(null);
                    dto.setMonthDay(null);
                    dto.setCron(null);
                }
                case "monthly" -> {
                    if (parts.length != 3) {
                        throw invalidSchedule("monthly 格式应为：monthly 日 HH:mm，例如 monthly 1 09:00");
                    }
                    int day;
                    try {
                        day = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        throw invalidSchedule("monthly 的日期须为 1-31 的整数，例如 monthly 1 09:00");
                    }
                    if (day < 1 || day > 31) {
                        throw invalidSchedule("monthly 的日期须在 1-31");
                    }
                    dto.setScheduleType("monthly");
                    dto.setMonthDay(day);
                    dto.setTime(normalizeTime(parts[2]));
                    dto.setOnceAt(null);
                    dto.setWeekdays(null);
                    dto.setCron(null);
                }
                case "cron" -> {
                    if (parts.length < 2) {
                        throw invalidSchedule("cron 格式应为：cron <5段表达式>，例如 cron 0 9 * * *");
                    }
                    String cron5 = raw.substring("cron".length()).trim();
                    dto.setScheduleType("cron");
                    dto.setCron(cron5);
                    dto.setTime(null);
                    dto.setOnceAt(null);
                    dto.setWeekdays(null);
                    dto.setMonthDay(null);
                }
                default -> throw invalidSchedule("未知类型「" + parts[0] + "」。" + scheduleExprHelp());
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw invalidSchedule(e.getMessage() != null ? e.getMessage() : "解析失败");
        }
    }

    /**
     * 将已有任务调度格式化为 Tool 侧 schedule 串，便于模型阅读与回写。
     */
    public static String formatScheduleExpr(String scheduleType, String time, String onceAt,
                                           List<Integer> weekdays, Integer monthDay, String cron) {
        if (!StringUtils.hasText(scheduleType)) {
            return null;
        }
        return switch (scheduleType.toLowerCase()) {
            case "once" -> "once " + (onceAt != null ? onceAt.trim() : "");
            case "daily" -> "daily " + (time != null ? time.trim() : "");
            case "weekly" -> {
                String days = weekdays == null || weekdays.isEmpty() ? ""
                        : weekdays.stream().map(String::valueOf).collect(Collectors.joining(","));
                yield "weekly " + days + " " + (time != null ? time.trim() : "");
            }
            case "monthly" -> "monthly " + (monthDay != null ? monthDay : "") + " "
                    + (time != null ? time.trim() : "");
            case "cron" -> "cron " + (cron != null ? cron.trim() : "");
            default -> scheduleType;
        };
    }

    private static BizException invalidSchedule(String detail) {
        return new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, detail + "。" + scheduleExprHelp());
    }

    /**
     * 规范化模型常见写法：去引号、中文类型别名、省略 once 的相对日等。
     */
    static String normalizeScheduleRaw(String schedule) {
        String s = schedule.trim().replace('\u00a0', ' ').replaceAll("\\s+", " ");
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        // 去掉误加前缀
        if (s.toLowerCase(Locale.ROOT).startsWith("schedule:")) {
            s = s.substring("schedule:".length()).trim();
        }
        if (s.toLowerCase(Locale.ROOT).startsWith("schedule ")) {
            s = s.substring("schedule ".length()).trim();
        }
        String[] p = s.split(" ");
        if (p.length == 0) {
            return s;
        }
        String head = p[0];
        String headLower = head.toLowerCase(Locale.ROOT);
        // 相对日省略 once：tomorrow 09:00 / 明天 09:00 / +2d 09:00
        if (isRelativeDayToken(head) && p.length >= 2) {
            return "once " + s;
        }
        // 中文 / 别名类型
        String mappedType = switch (head) {
            case "每天", "每日", "每天一次" -> "daily";
            case "每周" -> "weekly";
            case "每月" -> "monthly";
            case "一次", "一次性", "单次" -> "once";
            default -> null;
        };
        if (mappedType == null) {
            mappedType = switch (headLower) {
                case "every-day", "everyday" -> "daily";
                case "every-week", "everyweek" -> "weekly";
                case "every-month", "everymonth" -> "monthly";
                default -> null;
            };
        }
        if (mappedType != null) {
            String rest = p.length > 1 ? s.substring(head.length()).trim() : "";
            return rest.isEmpty() ? mappedType : mappedType + " " + rest;
        }
        return s;
    }

    private static boolean isRelativeDayToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String t = token.trim();
        String lower = t.toLowerCase(Locale.ROOT);
        return "tomorrow".equals(lower) || "today".equals(lower)
                || "明天".equals(t) || "今天".equals(t) || "后天".equals(t)
                || RELATIVE_DAYS.matcher(t).matches();
    }

    private static List<Integer> parseWeekdayToken(String token) {
        List<Integer> days = new ArrayList<>();
        for (String part : token.split("[,，、]+")) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            Integer d = parseOneWeekday(part.trim());
            if (d == null) {
                throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID,
                        "无法识别星期「" + part + "」，请用 1-7 或 周一/mon（1=周一…7=周日）");
            }
            days.add(d);
        }
        return normalizeWeekdays(days);
    }

    /** @return ISO 1=周一…7=周日；无法识别返回 null */
    private static Integer parseOneWeekday(String raw) {
        String t = raw.trim();
        if (t.matches("[1-7]")) {
            return Integer.parseInt(t);
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "一", "周一", "星期一", "mon", "monday" -> 1;
            case "二", "周二", "星期二", "tue", "tues", "tuesday" -> 2;
            case "三", "周三", "星期三", "wed", "wednesday" -> 3;
            case "四", "周四", "星期四", "thu", "thur", "thurs", "thursday" -> 4;
            case "五", "周五", "星期五", "fri", "friday" -> 5;
            case "六", "周六", "星期六", "sat", "saturday" -> 6;
            case "日", "天", "周日", "周天", "星期日", "星期天", "sun", "sunday" -> 7;
            default -> null;
        };
    }

    /** 规范化 HH:mm，兼容 9:00 / 9点 / 9点30 */
    private static String normalizeTime(String time) {
        int[] hm = parseHm(time);
        return String.format("%02d:%02d", hm[0], hm[1]);
    }

    /**
     * 校验并生成 schedule_config JSON
     */
    public static String buildConfigJson(AutomationScheduleType type, AutomationJobSaveDTO dto) {
        Map<String, Object> cfg = new LinkedHashMap<>();
        switch (type) {
            case ONCE -> {
                LocalDateTime once = parseOnceAt(dto.getOnceAt());
                LocalDateTime now = LocalDateTime.now(ZONE);
                if (!once.isAfter(now)) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, pastOnceMessage(once, now));
                }
                cfg.put("onceAt", once.format(ONCE_FMT));
            }
            case DAILY -> {
                cfg.put("time", requireTime(dto.getTime()));
            }
            case WEEKLY -> {
                List<Integer> days = normalizeWeekdays(dto.getWeekdays());
                if (days.isEmpty()) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "请选择星期");
                }
                cfg.put("weekdays", days);
                cfg.put("time", requireTime(dto.getTime()));
            }
            case MONTHLY -> {
                int day = dto.getMonthDay() == null ? 0 : dto.getMonthDay();
                if (day < 1 || day > 31) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "每月日期须在 1-31");
                }
                cfg.put("monthDay", day);
                cfg.put("time", requireTime(dto.getTime()));
            }
            case CRON -> {
                String cron5 = dto.getCron() == null ? "" : dto.getCron().trim();
                if (!StringUtils.hasText(cron5)) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "Cron 不能为空");
                }
                String springCron = toSpringCron(cron5);
                try {
                    CronExpression.parse(springCron);
                } catch (Exception e) {
                    throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "Cron 无效: " + e.getMessage());
                }
                cfg.put("cron", cron5);
            }
            default -> throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "未知触发方式");
        }
        try {
            return MAPPER.writeValueAsString(cfg);
        } catch (Exception e) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "配置序列化失败");
        }
    }

    public static Map<String, Object> parseConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 计算下一次触发时间；once 已过期返回 null；禁用场景由调用方处理。
     *
     * @param fromExclusive 从此刻之后找下一次（不含等于）
     */
    public static LocalDateTime computeNextRun(AutomationScheduleType type, String configJson, LocalDateTime fromExclusive) {
        Map<String, Object> cfg = parseConfig(configJson);
        LocalDateTime base = fromExclusive != null ? fromExclusive : LocalDateTime.now(ZONE);
        return switch (type) {
            case ONCE -> {
                LocalDateTime once = parseOnceAt(str(cfg.get("onceAt")));
                yield once.isAfter(base) ? once : null;
            }
            case DAILY, WEEKLY, MONTHLY, CRON -> nextByCron(toSpringCronExpression(type, cfg), base);
        };
    }

    private static LocalDateTime nextByCron(String springCron, LocalDateTime base) {
        CronExpression expr = CronExpression.parse(springCron);
        var next = expr.next(base.atZone(ZONE));
        return next != null ? next.toLocalDateTime() : null;
    }

    public static String toSpringCronExpression(AutomationScheduleType type, Map<String, Object> cfg) {
        return switch (type) {
            case DAILY -> {
                int[] hm = parseHm(str(cfg.get("time")));
                yield String.format("0 %d %d * * *", hm[1], hm[0]);
            }
            case WEEKLY -> {
                int[] hm = parseHm(str(cfg.get("time")));
                @SuppressWarnings("unchecked")
                List<Integer> isoDays = normalizeWeekdays((List<Integer>) cfg.get("weekdays"));
                String dow = isoDays.stream().map(AutomationScheduleUtil::isoToQuartzDow)
                        .map(String::valueOf).collect(Collectors.joining(","));
                yield String.format("0 %d %d * * %s", hm[1], hm[0], dow);
            }
            case MONTHLY -> {
                int[] hm = parseHm(str(cfg.get("time")));
                int day = ((Number) cfg.get("monthDay")).intValue();
                yield String.format("0 %d %d %d * *", hm[1], hm[0], day);
            }
            case CRON -> toSpringCron(str(cfg.get("cron")));
            case ONCE -> throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "一次性任务无需 Cron");
        };
    }

    /** 5 段 unix → 6 段 Spring；已是 6 段则原样 */
    public static String toSpringCron(String cron) {
        String c = cron.trim().replaceAll("\\s+", " ");
        String[] parts = c.split(" ");
        if (parts.length == 5) {
            return "0 " + c;
        }
        if (parts.length == 6) {
            return c;
        }
        throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "Cron 须为 5 段或 6 段");
    }

    /** ISO 1=周一…7=周日 → Quartz 1=周日…7=周六 */
    public static int isoToQuartzDow(int iso) {
        if (iso < 1 || iso > 7) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "星期取值须在 1-7");
        }
        return iso == 7 ? 1 : iso + 1;
    }

    public static LocalDateTime parseOnceAt(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "请选择执行时刻");
        }
        String v = raw.trim();
        try {
            if (v.length() <= 16) {
                return LocalDateTime.parse(v, ONCE_FMT);
            }
            return LocalDateTime.parse(v, ONCE_FMT_SEC);
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "执行时刻格式应为 yyyy-MM-dd HH:mm");
        }
    }

    /**
     * 解析 once 调度：支持相对日（tomorrow/today/+Nd/明天/今天）与绝对日期。
     * <p>parts[0] 固定为 once。</p>
     */
    static LocalDateTime resolveOnceDateTime(String[] parts) {
        String dayToken = parts[1].trim();
        String dayLower = dayToken.toLowerCase(Locale.ROOT);
        String timeRaw = String.join(" ", java.util.Arrays.copyOfRange(parts, 2, parts.length));
        LocalTime time = parseLocalTime(timeRaw);
        LocalDate today = LocalDate.now(ZONE);

        if ("tomorrow".equals(dayLower) || "明天".equals(dayToken) || "+1d".equalsIgnoreCase(dayToken)) {
            return LocalDateTime.of(today.plusDays(1), time);
        }
        if ("后天".equals(dayToken) || "+2d".equalsIgnoreCase(dayToken)) {
            return LocalDateTime.of(today.plusDays(2), time);
        }
        if ("today".equals(dayLower) || "今天".equals(dayToken)) {
            return LocalDateTime.of(today, time);
        }
        Matcher m = RELATIVE_DAYS.matcher(dayToken);
        if (m.matches()) {
            int days = Integer.parseInt(m.group(1));
            return LocalDateTime.of(today.plusDays(days), time);
        }
        // 绝对日期：yyyy-MM-dd HH:mm
        String onceRaw = dayToken + " " + timeRaw;
        return parseOnceAt(onceRaw);
    }

    private static LocalTime parseLocalTime(String raw) {
        String token = raw.trim().split("\\s+")[0];
        // 兼容 HH:mm:ss → 取到分钟
        if (token.matches("\\d{1,2}:\\d{2}:\\d{2}")) {
            token = token.substring(0, token.lastIndexOf(':'));
        }
        int[] hm = parseHm(token);
        return LocalTime.of(hm[0], hm[1]);
    }

    /** once 已过期时的提示（含当前时间与建议写法） */
    public static String pastOnceMessage(LocalDateTime once, LocalDateTime now) {
        return "执行时刻不能早于当前时间（传入 "
                + once.format(ONCE_FMT) + "，当前 Asia/Shanghai "
                + now.format(ONCE_FMT)
                + "）。若要明天早上请用 once tomorrow 09:00，不要用今天的日期";
    }

    private static String requireTime(String time) {
        return normalizeTime(time);
    }

    /** @return [hour, minute] */
    private static int[] parseHm(String time) {
        if (!StringUtils.hasText(time)) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "请选择执行时间 HH:mm");
        }
        String t = time.trim();
        // 9点 / 9点30
        Matcher cn = CN_TIME.matcher(t);
        if (cn.matches()) {
            int h = Integer.parseInt(cn.group(1));
            int m = cn.group(2) != null ? Integer.parseInt(cn.group(2)) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "执行时间不合法");
            }
            return new int[]{h, m};
        }
        if (!t.matches("\\d{1,2}:\\d{2}")) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID,
                    "执行时间须为 HH:mm（如 09:00）或「9点」「9点30」");
        }
        String[] p = t.split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        if (h < 0 || h > 23 || m < 0 || m > 59) {
            throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "执行时间不合法");
        }
        return new int[]{h, m};
    }

    private static List<Integer> normalizeWeekdays(List<Integer> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>();
        for (Integer d : weekdays) {
            if (d == null) {
                continue;
            }
            if (d < 1 || d > 7) {
                throw new BizException(ErrorCode.AUTOMATION_SCHEDULE_INVALID, "星期取值须在 1-7");
            }
            if (!out.contains(d)) {
                out.add(d);
            }
        }
        out.sort(Integer::compareTo);
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
