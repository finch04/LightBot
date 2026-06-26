package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.Message;
import com.lightbot.enums.ErrorCode;
import com.lightbot.entity.MessageFeedback;
import com.lightbot.mapper.MessageFeedbackMapper;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.mapper.ToolCallMapper;
import com.lightbot.service.MessageService;
import com.lightbot.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;
    private final ToolCallMapper toolCallMapper;
    private final MessageFeedbackMapper messageFeedbackMapper;

    @Override
    public Page<Message> listBySessionIdPage(Long sessionId, int pageNum, int pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByDesc(Message::getCreateTime));
    }

    @Override
    public List<Message> listBySessionId(Long sessionId) {
        return list(new LambdaQueryWrapper<Message>()
                .eq(Message::getSessionId, sessionId)
                .orderByAsc(Message::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySessionId(Long sessionId) {
        // 1. 加载会话下所有消息，清理关联的 MinIO 资源
        List<Message> messages = listBySessionId(sessionId);
        cleanupMinioResources(messages);
        // 2. 批量删除关联的 ToolCall 记录
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        if (!messageIds.isEmpty()) {
            toolCallMapper.deleteByMessageIds(messageIds);
            // 3. 删除关联的反馈记录
            messageFeedbackMapper.delete(new LambdaQueryWrapper<MessageFeedback>()
                    .in(MessageFeedback::getMessageId, messageIds));
        }
        // 4. 删除消息
        remove(new LambdaQueryWrapper<Message>().eq(Message::getSessionId, sessionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long messageId, Long sessionId) {
        // 1. 加载消息，清理关联的 MinIO 资源
        Message message = getOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getSessionId, sessionId));
        if (message != null) {
            cleanupMinioResources(List.of(message));
        }
        // 2. 删除关联的 ToolCall 记录
        toolCallMapper.deleteByMessageIds(List.of(messageId));
        // 3. 删除关联的反馈记录
        messageFeedbackMapper.delete(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getMessageId, messageId));
        // 4. 删除消息
        remove(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getSessionId, sessionId));
    }

    @Override
    public Page<Message> searchBySessionId(Long sessionId, String keyword, int pageNum, int pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .like(Message::getContent, keyword)
                        .orderByDesc(Message::getCreateTime));
    }

    @Override
    public void toggleStar(Long messageId) {
        Message msg = getById(messageId);
        if (msg == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        msg.setStarred(!Boolean.TRUE.equals(msg.getStarred()));
        updateById(msg);
    }

    @Override
    public Page<Message> listStarred(int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getStarred, true)
                        .orderByDesc(Message::getCreateTime));
    }

    /**
     * 清理消息关联的 MinIO 资源
     * <p>包含两类：AI 生图生成的图片、用户上传的附件</p>
     */
    private void cleanupMinioResources(List<Message> messages) {
        for (Message msg : messages) {
            List<String> paths = new ArrayList<>();
            paths.addAll(extractImageFilePaths(msg.getMetadata()));
            paths.addAll(extractAttachmentObjectKeys(msg.getMetadata()));
            for (String path : paths) {
                try {
                    minioUtil.delete(path);
                    log.info("[Message] 清理MinIO资源: path={}", path);
                } catch (Exception e) {
                    log.warn("[Message] 清理MinIO资源失败: path={}, error={}", path, e.getMessage());
                }
            }
        }
    }

    /**
     * 从消息 metadata 中提取 image_generation 工具生成的图片 file_path 列表
     */
    private List<String> extractImageFilePaths(String metadata) {
        List<String> paths = new ArrayList<>();
        if (metadata == null || metadata.isBlank()) {
            return paths;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode toolEvents = root.get("toolEvents");
            if (toolEvents == null || !toolEvents.isArray()) {
                return paths;
            }
            for (JsonNode event : toolEvents) {
                if (!"tool_result".equals(event.path("type").asText())) {
                    continue;
                }
                if (!"image_generation".equals(event.path("toolName").asText())) {
                    continue;
                }
                String result = event.path("result").asText(null);
                if (result == null || result.isBlank()) {
                    continue;
                }
                try {
                    JsonNode resultNode = objectMapper.readTree(result);
                    String filePath = resultNode.path("file_path").asText(null);
                    if (filePath != null && !filePath.isBlank()) {
                        paths.add(filePath);
                    }
                } catch (Exception ignored) {
                    // result 不是合法 JSON，跳过
                }
            }
        } catch (Exception e) {
            log.warn("[Message] 解析metadata失败: {}", e.getMessage());
        }
        return paths;
    }

    /**
     * 从消息 metadata 中提取用户上传附件的 MinIO objectKey 列表
     */
    private List<String> extractAttachmentObjectKeys(String metadata) {
        List<String> keys = new ArrayList<>();
        if (metadata == null || metadata.isBlank()) {
            return keys;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode attachments = root.get("attachments");
            if (attachments == null || !attachments.isArray()) {
                return keys;
            }
            for (JsonNode att : attachments) {
                String objectKey = att.path("objectKey").asText(null);
                if (objectKey != null && !objectKey.isBlank()) {
                    keys.add(objectKey);
                }
            }
        } catch (Exception e) {
            log.warn("[Message] 解析附件metadata失败: {}", e.getMessage());
        }
        return keys;
    }
}
