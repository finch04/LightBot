package com.lightbot.tool.builtin;

import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内置工具 — 向用户提问
 * <p>当 Agent 需要向用户确认信息、请求补充说明或提供选项时调用此工具。
 * 工具执行后返回提示文本，前端展示为交互式提问气泡，等待用户回答后继续对话。</p>
 *
 * @author finch
 * @since 2026-06-17
 */
@Slf4j
@Component("askUserTool")
@SystemTool(displayName = "向用户提问", description = "向用户提问并等待回答，用于确认信息或请求补充说明", tags = {"交互"})
public class AskUserTool {

    @Tool(name = "ask_user",
          description = "向用户提问并等待回答。当需要确认信息、请求补充说明、让用户选择选项时调用此工具。调用后会暂停执行，等待用户回复后继续。")
    public String askUser(
            @ToolParam(description = "要向用户提出的问题或提示")
            @ToolParamMeta(example = "请问您想查询哪个时间段的数据？") String question,
            @ToolParam(description = "可选的选项列表，用逗号分隔（如：选项A,选项B,选项C）。留空则为开放式提问")
            @ToolParamMeta(example = "最近7天,最近30天,自定义", required = false) String options) {
        log.info("[Tool:ask_user] 向用户提问: question={}, options={}", question, options);

        ToolEventEmitter.emit("等待用户回答...");

        StringBuilder sb = new StringBuilder();
        sb.append("【提问】").append(question).append("\n");

        if (options != null && !options.isBlank()) {
            String[] optionList = options.split(",");
            sb.append("选项：\n");
            for (int i = 0; i < optionList.length; i++) {
                sb.append(String.format("  %d. %s\n", i + 1, optionList[i].trim()));
            }
            sb.append("\n请用户选择或输入回答。");
        } else {
            sb.append("请用户输入回答。");
        }

        return sb.toString();
    }
}
