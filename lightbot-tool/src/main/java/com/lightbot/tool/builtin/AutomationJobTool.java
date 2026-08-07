package com.lightbot.tool.builtin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.automation.AutomationJobSaveDTO;
import com.lightbot.service.AutomationJobService;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import com.lightbot.util.AutomationScheduleUtil;
import com.lightbot.vo.AutomationJobRunVO;
import com.lightbot.vo.AutomationJobVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 内置工具 — 自动化定时任务（单工具 + action 分发）
 * <p>仅能操作当前对话智能体名下的任务与执行记录，禁止跨 Agent / 勿传 agentId。</p>
 *
 * @author finch
 * @since 2026-07-27
 */
@Slf4j
@Component("automationJobTool")
@RequiredArgsConstructor
@SystemTool(
        displayName = "自动化定时任务",
        icon = "ThunderboltOutlined",
        description = "用 action 管理本智能体定时任务与执行记录。"
                + "任务：list/get/create/update/delete/enable/disable/run；"
                + "记录：list_runs/get_run。schedule 如 once tomorrow 09:00。勿传 agentId",
        tags = {"自动化", "定时任务"})
public class AutomationJobTool {

    private static final int SUMMARY_MAX = 800;
    private static final int DETAIL_CONTENT_MAX = 2000;

    private static final String ACTION_DESC =
            "操作类型，必填。任务配置：list|get|create|update|delete|enable|disable|run；"
                    + "执行记录：list_runs|get_run。"
                    + "【查记录】最近执行：{\"action\":\"list_runs\"}；"
                    + "失败记录：{\"action\":\"list_runs\",\"status\":\"failed\"}；"
                    + "某任务的记录：{\"action\":\"list_runs\",\"jobId\":\"2081…\"}；"
                    + "记录详情：{\"action\":\"get_run\",\"runId\":\"2081…\"}（runId 来自 list_runs 或 run 返回值）。"
                    + "【建任务】明天9点：{\"action\":\"create\",\"name\":\"查天气\",\"instruction\":\"查询天气并简报\",\"schedule\":\"once tomorrow 09:00\"}；"
                    + "每天9点：{\"action\":\"create\",\"name\":\"日报\",\"instruction\":\"汇总昨日数据\",\"schedule\":\"daily 09:00\"}；"
                    + "周一三五：{\"action\":\"create\",\"name\":\"周报\",\"instruction\":\"汇总进展\",\"schedule\":\"weekly 1,3,5 09:00\"}。"
                    + "【其它】{\"action\":\"list\"}；{\"action\":\"run\",\"jobId\":\"…\"}；"
                    + "{\"action\":\"update\",\"jobId\":\"…\",\"schedule\":\"daily 08:30\"}。"
                    + "用户问「任务跑了没/执行结果/失败原因」→ 先 list_runs 再 get_run。"
                    + "「明天X点」用 once tomorrow HH:mm，禁止猜绝对日期。";

    /** Schema 由 ToolRegistrar 替换为 AutomationScheduleUtil.scheduleParamGuide() */
    private static final String SCHEDULE_DESC = "调度规则，详见 schedule 参数指南（once tomorrow 09:00 / daily 09:00 等）";

    private final AutomationJobService automationJobService;
    private final ObjectMapper objectMapper;
    @Qualifier("lightBotExecutor")
    private final Executor lightBotExecutor;

    @Tool(name = "manage_automation_job",
            description = "管理本智能体定时任务与执行记录。"
                    + "action=list|get|create|update|delete|enable|disable|run|list_runs|get_run。"
                    + "查执行记录用 list_runs（可选 status/jobId），详情用 get_run+runId。"
                    + "create需name+instruction+schedule；明天某时刻用 once tomorrow HH:mm。"
                    + "改删启停执行需jobId。勿传agentId。")
    @SystemTool(displayName = "自动化定时任务", tags = {"自动化", "定时任务"})
    public String manage(
            @ToolParam(description = ACTION_DESC)
            @ToolParamMeta(example = "list_runs") String action,
            @ToolParam(description = "任务 ID。get/update/delete/enable/disable/run 必填；list_runs 可选（只看该任务的记录）", required = false)
            @ToolParamMeta(example = "2081371376924332033", required = false) String jobId,
            @ToolParam(description = "执行记录 ID。get_run 必填（来自 list_runs 的 id 或 run 返回的 runId）", required = false)
            @ToolParamMeta(example = "2081372000000000001", required = false) String runId,
            @ToolParam(description = "任务名称；create 必填，update 可选", required = false)
            @ToolParamMeta(example = "明早查天气", required = false) String name,
            @ToolParam(description = "发给当前智能体的指令；create 必填，update 可选", required = false)
            @ToolParamMeta(example = "查询明天天气并简要汇报", required = false) String instruction,
            @ToolParam(description = SCHEDULE_DESC, required = false)
            @ToolParamMeta(example = "once tomorrow 09:00", required = false) String schedule,
            @ToolParam(description = "create/update：是否启用；list：按任务启用状态过滤", required = false)
            @ToolParamMeta(example = "true", required = false) Boolean enabled,
            @ToolParam(description = "list / list_runs：按名称或指令关键词过滤", required = false)
            @ToolParamMeta(example = "天气", required = false) String keyword,
            @ToolParam(description = "list_runs：执行状态过滤，可选 success|failed|running|skipped", required = false)
            @ToolParamMeta(example = "failed", required = false) String status,
            @ToolParam(description = "list_runs：页码，从 1 开始，默认 1", required = false)
            @ToolParamMeta(example = "1", required = false) Integer pageNum,
            @ToolParam(description = "list_runs：每页条数，默认 10，最大 50", required = false)
            @ToolParamMeta(example = "10", required = false) Integer pageSize,
            ToolContext toolContext) {
        try {
            Scope scope = requireScope(toolContext);
            String act = normalizeAction(action);
            return switch (act) {
                case "list" -> doList(scope, keyword, enabled);
                case "get" -> doGet(scope, jobId);
                case "create" -> doCreate(scope, name, instruction, schedule, enabled);
                case "update" -> doUpdate(scope, jobId, name, instruction, schedule, enabled);
                case "delete" -> doDelete(scope, jobId);
                case "enable" -> doSetEnabled(scope, jobId, true);
                case "disable" -> doSetEnabled(scope, jobId, false);
                case "run" -> doRun(scope, jobId);
                case "list_runs" -> doListRuns(scope, jobId, keyword, status, pageNum, pageSize);
                case "get_run" -> doGetRun(scope, runId);
                default -> throw new IllegalArgumentException(
                        "未知 action「" + action + "」，应为 list/get/create/update/delete/enable/disable/run/list_runs/get_run");
            };
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    private String doList(Scope scope, String keyword, Boolean enabled) {
        List<AutomationJobVO> list = automationJobService.listByAgent(
                scope.userId(), scope.agentId(), blankToNull(keyword), enabled);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", list.size());
        out.put("jobs", list.stream().map(this::briefJob).toList());
        return toJson(out);
    }

    private String doGet(Scope scope, String jobId) {
        AutomationJobVO vo = automationJobService.getByAgent(
                scope.userId(), scope.agentId(), parseId(jobId, "jobId"));
        return toJson(detailJob(vo));
    }

    private String doCreate(Scope scope, String name, String instruction, String schedule, Boolean enabled) {
        AutomationJobSaveDTO dto = new AutomationJobSaveDTO();
        dto.setName(requireText(name, "name（create 时必填）"));
        dto.setInstruction(requireText(instruction, "instruction（create 时必填）"));
        dto.setEnabled(enabled);
        String scheduleRaw = requireText(schedule, "schedule（create 时必填）");
        log.info("[Tool:manage_automation_job] create schedule={}", scheduleRaw);
        AutomationScheduleUtil.applyScheduleExpr(scheduleRaw, dto);
        dto.setAgentId(scope.agentId());
        AutomationJobVO vo = automationJobService.createForAgent(scope.userId(), scope.agentId(), dto);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "创建成功");
        out.put("job", detailJob(vo));
        return toJson(out);
    }

    private String doUpdate(Scope scope, String jobId, String name, String instruction,
                            String schedule, Boolean enabled) {
        Long id = parseId(jobId, "jobId");
        AutomationJobVO existing = automationJobService.getByAgent(scope.userId(), scope.agentId(), id);
        if (!StringUtils.hasText(name) && !StringUtils.hasText(instruction)
                && !StringUtils.hasText(schedule) && enabled == null) {
            throw new IllegalArgumentException("update 请至少传入 name / instruction / schedule / enabled 之一");
        }
        AutomationJobSaveDTO dto = toSaveDto(existing);
        if (StringUtils.hasText(name)) {
            dto.setName(name.trim());
        }
        if (StringUtils.hasText(instruction)) {
            dto.setInstruction(instruction.trim());
        }
        if (StringUtils.hasText(schedule)) {
            AutomationScheduleUtil.applyScheduleExpr(schedule, dto);
        }
        if (enabled != null) {
            dto.setEnabled(enabled);
        }
        dto.setAgentId(scope.agentId());
        AutomationJobVO vo = automationJobService.updateForAgent(scope.userId(), scope.agentId(), id, dto);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "更新成功");
        out.put("job", detailJob(vo));
        return toJson(out);
    }

    private String doDelete(Scope scope, String jobId) {
        String id = jobId;
        automationJobService.deleteForAgent(scope.userId(), scope.agentId(), parseId(jobId, "jobId"));
        return toJson(Map.of("message", "删除成功", "jobId", id));
    }

    private String doSetEnabled(Scope scope, String jobId, boolean enabled) {
        AutomationJobVO vo = automationJobService.setEnabledForAgent(
                scope.userId(), scope.agentId(), parseId(jobId, "jobId"), enabled);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", enabled ? "已启用" : "已停用");
        out.put("job", briefJob(vo));
        return toJson(out);
    }

    private String doRun(Scope scope, String jobId) {
        Long runId = automationJobService.prepareManualRunForAgent(
                scope.userId(), scope.agentId(), parseId(jobId, "jobId"));
        lightBotExecutor.execute(() -> {
            try {
                automationJobService.executeClaimedRun(runId);
            } catch (Exception e) {
                log.warn("[Tool:manage_automation_job] 执行失败 runId={}", runId, e);
            }
        });
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "已提交执行，可用 action=get_run 查询结果（稍等片刻）");
        out.put("runId", String.valueOf(runId));
        out.put("jobId", jobId);
        return toJson(out);
    }

    private String doListRuns(Scope scope, String jobId, String keyword, String status,
                              Integer pageNum, Integer pageSize) {
        Long jobIdLong = StringUtils.hasText(jobId) ? parseId(jobId, "jobId") : null;
        int pn = pageNum != null ? pageNum : 1;
        int ps = pageSize != null ? pageSize : 10;
        Page<AutomationJobRunVO> page = automationJobService.pageRunsByAgent(
                scope.userId(), scope.agentId(), jobIdLong,
                blankToNull(keyword), blankToNull(status), pn, ps);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", page.getTotal());
        out.put("pageNum", page.getCurrent());
        out.put("pageSize", page.getSize());
        out.put("runs", page.getRecords().stream().map(this::briefRun).toList());
        out.put("hint", "查看某条详情请用 {\"action\":\"get_run\",\"runId\":\"…\"}；status 可选 success/failed/running/skipped");
        return toJson(out);
    }

    private String doGetRun(Scope scope, String runId) {
        AutomationJobRunVO vo = automationJobService.getRunByAgent(
                scope.userId(), scope.agentId(), parseId(runId, "runId"));
        return toJson(detailRun(vo));
    }

    // ---------- helpers ----------

    private record Scope(Long userId, Long agentId) {
    }

    private Scope requireScope(ToolContext toolContext) {
        Long agentId = longVal(contextValue(toolContext, "agentId"));
        Long userId = longVal(contextValue(toolContext, "userId"));
        if (userId == null) {
            Object chatCtx = contextValue(toolContext, "chatContext");
            if (chatCtx != null) {
                try {
                    Object uid = chatCtx.getClass().getMethod("getUserId").invoke(chatCtx);
                    userId = longVal(uid);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        if (userId == null) {
            try {
                userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
            } catch (Exception ignored) {
                // ignore
            }
        }
        if (userId == null || agentId == null) {
            throw new IllegalStateException("缺少用户或智能体上下文，无法管理定时任务");
        }
        return new Scope(userId, agentId);
    }

    private static String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            throw new IllegalArgumentException(
                    "action 不能为空，应为 list/get/create/update/delete/enable/disable/run/list_runs/get_run");
        }
        String a = action.trim().toLowerCase(Locale.ROOT);
        return switch (a) {
            case "list", "list_automation_jobs", "list_jobs" -> "list";
            case "get", "get_automation_job", "detail", "job_detail" -> "get";
            case "create", "create_automation_job", "add" -> "create";
            case "update", "update_automation_job", "edit" -> "update";
            case "delete", "delete_automation_job", "remove" -> "delete";
            case "enable", "set_enabled_true" -> "enable";
            case "disable", "set_enabled_false" -> "disable";
            case "set_automation_job_enabled", "set_enabled" ->
                    throw new IllegalArgumentException("请使用 action=enable 或 action=disable");
            case "run", "run_automation_job", "execute" -> "run";
            case "list_runs", "list_run", "runs", "list_records", "records" -> "list_runs";
            case "get_run", "run_detail", "get_record", "record_detail" -> "get_run";
            default -> a;
        };
    }

    private AutomationJobSaveDTO toSaveDto(AutomationJobVO vo) {
        AutomationJobSaveDTO dto = new AutomationJobSaveDTO();
        dto.setName(vo.getName());
        dto.setInstruction(vo.getInstruction());
        dto.setScheduleType(vo.getScheduleType());
        dto.setTime(vo.getTime());
        dto.setOnceAt(vo.getOnceAt());
        dto.setWeekdays(vo.getWeekdays());
        dto.setMonthDay(vo.getMonthDay());
        dto.setCron(vo.getCron());
        dto.setEnabled(vo.getEnabled());
        dto.setAgentId(vo.getAgentId());
        return dto;
    }

    private Map<String, Object> briefJob(AutomationJobVO vo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", str(vo.getId()));
        m.put("name", vo.getName());
        m.put("agentId", str(vo.getAgentId()));
        m.put("agentName", vo.getAgentName());
        m.put("schedule", formatSchedule(vo));
        m.put("scheduleType", vo.getScheduleType());
        m.put("enabled", vo.getEnabled());
        m.put("nextRunAt", vo.getNextRunAt() != null ? vo.getNextRunAt().toString() : null);
        m.put("instruction", vo.getInstruction());
        return m;
    }

    private Map<String, Object> detailJob(AutomationJobVO vo) {
        Map<String, Object> m = briefJob(vo);
        m.put("time", vo.getTime());
        m.put("onceAt", vo.getOnceAt());
        m.put("weekdays", vo.getWeekdays());
        m.put("monthDay", vo.getMonthDay());
        m.put("cron", vo.getCron());
        m.put("lastRunAt", vo.getLastRunAt() != null ? vo.getLastRunAt().toString() : null);
        return m;
    }

    private Map<String, Object> briefRun(AutomationJobRunVO vo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runId", str(vo.getId()));
        m.put("jobId", str(vo.getJobId()));
        m.put("jobName", vo.getJobName());
        m.put("status", vo.getStatus());
        m.put("triggerType", vo.getTriggerType());
        m.put("triggerTime", vo.getTriggerTime() != null ? vo.getTriggerTime().toString() : null);
        m.put("duration", vo.getDuration());
        m.put("summary", truncate(vo.getSummary(), SUMMARY_MAX));
        m.put("error", truncate(vo.getError(), SUMMARY_MAX));
        m.put("sessionId", str(vo.getSessionId()));
        return m;
    }

    private Map<String, Object> detailRun(AutomationJobRunVO vo) {
        Map<String, Object> m = briefRun(vo);
        m.put("instruction", vo.getInstruction());
        m.put("durationMs", vo.getDurationMs());
        // detail 可能很大：只抽取正文摘要给模型，避免撑爆上下文
        m.put("resultText", extractResultText(vo.getDetail()));
        if (StringUtils.hasText(vo.getError())) {
            m.put("error", vo.getError());
        }
        return m;
    }

    /**
     * 从 detail 快照中尽量抽出助手回复正文
     */
    @SuppressWarnings("unchecked")
    private String extractResultText(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            if (detail instanceof String s) {
                return truncate(s, DETAIL_CONTENT_MAX);
            }
            if (detail instanceof Map<?, ?> map) {
                Object content = map.get("content");
                if (content != null && StringUtils.hasText(String.valueOf(content))) {
                    return truncate(String.valueOf(content), DETAIL_CONTENT_MAX);
                }
                Object summary = map.get("summary");
                if (summary != null) {
                    return truncate(String.valueOf(summary), DETAIL_CONTENT_MAX);
                }
            }
            String json = objectMapper.writeValueAsString(detail);
            return truncate(json, DETAIL_CONTENT_MAX);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatSchedule(AutomationJobVO vo) {
        return AutomationScheduleUtil.formatScheduleExpr(
                vo.getScheduleType(), vo.getTime(), vo.getOnceAt(),
                vo.getWeekdays(), vo.getMonthDay(), vo.getCron());
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private String requireText(String raw, String field) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return raw.trim();
    }

    private Long parseId(String raw, String field) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 不是合法 ID");
        }
    }

    private Object contextValue(ToolContext toolContext, String key) {
        return toolContext != null && toolContext.getContext() != null
                ? toolContext.getContext().get(key) : null;
    }

    private Long longVal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            long v = n.longValue();
            return v > 0 ? v : null;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            long v = Long.parseLong(s);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"JSON 序列化失败\"}";
        }
    }

    private String errorJson(Exception e) {
        String msg = e instanceof BizException be ? be.getMessage()
                : (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        log.warn("[Tool:manage_automation_job] {}", msg);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("error", msg);
        return toJson(err);
    }
}
