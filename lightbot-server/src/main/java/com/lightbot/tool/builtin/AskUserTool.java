package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工具 — 向用户提问
 * <p>当 Agent 需要向用户确认信息、请求补充说明或提供选项时调用此工具。
 * 工具执行后返回提示文本，前端展示为交互式提问弹窗，等待用户回答后继续对话。</p>
 *
 * @author finch
 * @since 2026-06-17
 */
@Slf4j
@Component("askUserTool")
@SystemTool(displayName = "向用户提问", description = "向用户提问并等待回答，用于确认信息或请求补充说明", tags = {"交互"},
        outputExample = "{\"question\":\"请问您想查询哪个时间段的数据？\",\"options\":[\"最近7天\",\"最近30天\",\"自定义\"],\"is_open_ended\":false,\"wait_for_user\":true,\"break_loop\":true}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\",\"description\":\"提出的问题\"},\"options\":{\"type\":\"array\",\"description\":\"选项列表（空数组表示开放式提问）\",\"items\":{\"type\":\"string\"}},\"is_open_ended\":{\"type\":\"boolean\",\"description\":\"是否为开放式提问\"},\"wait_for_user\":{\"type\":\"boolean\",\"description\":\"标记此工具需要等待用户回答\"},\"break_loop\":{\"type\":\"boolean\",\"description\":\"标记工具执行后应中断循环\"}}}")
@RequiredArgsConstructor
public class AskUserTool {

    public static final String TOOL_NAME = "ask_user";

    private final ObjectMapper objectMapper;

    @Tool(name = "ask_user",
          description = "向用户提问并等待回答。当需要确认信息、请求补充说明、让用户选择选项时调用此工具。" +
                "必须提供至少3个有意义的选项供用户选择（用逗号分隔）。" +
                "调用此工具后系统会自动停止执行，等待用户回复后继续。" +
                "重要：调用此工具后不要再调用其他工具，也不要输出任何内容，系统会自动处理。")
    public String askUser(
            @ToolParam(description = "要向用户提出的问题或提示")
            @ToolParamMeta(example = "请问您想查询哪个时间段的数据？") String question,
            @ToolParam(description = "可选的选项列表，用逗号分隔（如：选项A,选项B,选项C）。留空则为开放式提问")
            @ToolParamMeta(example = "最近7天,最近30天,自定义", required = false) String options) {
        log.info("[Tool:ask_user] 向用户提问: question={}, options={}", question, options);

        ToolEventEmitter.emit("等待用户回答...");

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("question", question);

        boolean hasOptions = options != null && !options.isBlank();
        List<String> optionItems = new ArrayList<>();
        if (hasOptions) {
            String[] optionList = options.split(",");
            for (String opt : optionList) {
                optionItems.add(opt.trim());
            }
            // 选项不足 3 个时返回错误，让 AI 重新生成
            if (optionItems.size() < 3) {
                log.warn("[Tool:ask_user] 选项不足3个: {}", optionItems);
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("_error", true);
                error.put("message", "选项数量不足，请提供至少3个有意义的选项供用户选择");
                try {
                    return objectMapper.writeValueAsString(error);
                } catch (Exception e) {
                    return "{\"_error\":true,\"message\":\"选项数量不足\"}";
                }
            }
            output.put("options", optionItems);
        } else {
            output.put("options", List.of());
        }
        output.put("is_open_ended", !hasOptions);
        output.put("wait_for_user", true);
        output.put("break_loop", true);

        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return "【提问】" + question;
        }
    }
}
