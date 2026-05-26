package com.lightbot.util;

import com.lightbot.constant.ChatAttachmentConstants;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.dto.AgentChatCapabilitiesDTO;
import com.lightbot.dto.AgentChatCapabilitiesDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 Agent config 解析对话能力
 */
public final class AgentChatCapabilitiesUtil {

    private AgentChatCapabilitiesUtil() {
    }

    public static AgentChatCapabilitiesDTO fromConfigMap(Map<String, Object> config) {
        AgentChatCapabilitiesDTO dto = new AgentChatCapabilitiesDTO();
        if (config == null || config.isEmpty()) {
            fillDefaults(dto);
            return dto;
        }
        dto.setMultimodalEnabled(toBool(config.get(ConfigKeys.Agent.MULTIMODAL_ENABLED)));
        dto.setEnableImageInput(toBool(config.get(ConfigKeys.Agent.ENABLE_IMAGE_INPUT)));
        dto.setEnableVideoInput(toBool(config.get(ConfigKeys.Agent.ENABLE_VIDEO_INPUT)));
        dto.setEnableAudioInput(toBool(config.get(ConfigKeys.Agent.ENABLE_AUDIO_INPUT)));
        dto.setEnableWebSearch(toBool(config.get(ConfigKeys.Agent.ENABLE_WEB_SEARCH)));
        dto.setEnableTts(toBool(config.get(ConfigKeys.Agent.ENABLE_TTS)));
        dto.setEnableReasoning(toBool(config.get(ConfigKeys.Agent.ENABLE_REASONING)));

        boolean multimodal = Boolean.TRUE.equals(dto.getMultimodalEnabled());
        boolean allowFile = multimodal && (Boolean.TRUE.equals(dto.getEnableImageInput())
                || Boolean.TRUE.equals(dto.getEnableVideoInput()));
        dto.setAllowFileUpload(allowFile);

        List<String> mimes = new ArrayList<>();
        if (multimodal && Boolean.TRUE.equals(dto.getEnableImageInput())) {
            mimes.add("image/jpeg");
            mimes.add("image/png");
            mimes.add("image/webp");
            mimes.add("image/gif");
        }
        if (multimodal && Boolean.TRUE.equals(dto.getEnableVideoInput())) {
            mimes.add("video/mp4");
            mimes.add("video/webm");
            mimes.add("video/quicktime");
        }
        dto.setAllowedFileMimeTypes(mimes);
        fillUploadSizeLimits(dto);
        return dto;
    }

    private static void fillDefaults(AgentChatCapabilitiesDTO dto) {
        dto.setMultimodalEnabled(false);
        dto.setEnableImageInput(false);
        dto.setEnableVideoInput(false);
        dto.setEnableAudioInput(false);
        dto.setEnableWebSearch(false);
        dto.setEnableTts(false);
        dto.setEnableReasoning(false);
        dto.setAllowFileUpload(false);
        dto.setAllowedFileMimeTypes(List.of());
        fillUploadSizeLimits(dto);
    }

    /** 上传大小限制由后端统一定义，前端仅展示 label */
    private static void fillUploadSizeLimits(AgentChatCapabilitiesDTO dto) {
        dto.setMaxImageBytes(ChatAttachmentConstants.MAX_IMAGE_BYTES);
        dto.setMaxVideoBytes(ChatAttachmentConstants.MAX_VIDEO_BYTES);
        dto.setMaxImageSizeLabel(formatSizeLabel(ChatAttachmentConstants.MAX_IMAGE_BYTES));
        dto.setMaxVideoSizeLabel(formatSizeLabel(ChatAttachmentConstants.MAX_VIDEO_BYTES));
    }

    private static String formatSizeLabel(long bytes) {
        if (bytes % (1024 * 1024) == 0) {
            return (bytes / (1024 * 1024)) + "MB";
        }
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private static boolean toBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw != null) {
            return Boolean.parseBoolean(String.valueOf(raw));
        }
        return false;
    }
}
