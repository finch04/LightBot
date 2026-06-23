package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import com.lightbot.util.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 系统内置工具 — AI 文生图（SiliconFlow API）
 * <p>根据文字描述生成图片，使用 Qwen-Image 模型</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component("imageGenTool")
@SystemTool(displayName = "AI图片生成", description = "根据文字描述生成图片", tags = {"图片"},
        outputExample = "{\"image_url\":\"https://minio.example.com/generated/images/abc123.jpg?...\",\"prompt\":\"a beautiful sunset over the ocean\",\"file_path\":\"generated/images/abc123.jpg\"}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"image_url\":{\"type\":\"string\",\"description\":\"图片访问URL（预签名链接）\"},\"prompt\":{\"type\":\"string\",\"description\":\"原始描述词\"},\"file_path\":{\"type\":\"string\",\"description\":\"MinIO存储路径\"}}}}")
@RequiredArgsConstructor
public class ImageGenTool {

    private final MinioUtil minioUtil;

    @Value("${lightbot.siliconflow.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Tool(name = "image_generation",
          description = "根据文字描述生成图片。当用户需要生成图片、插画、设计图、创意图时调用此工具。建议使用英文描述以获得更好的效果。")
    public String generate(
            @ToolParam(description = "图片描述（英文效果更佳）")
            @ToolParamMeta(example = "a beautiful sunset over the ocean") String prompt,
            @ToolParam(description = "负面提示词，描述不希望出现的元素（可选）")
            @ToolParamMeta(example = "blurry, low quality", required = false) String negativePrompt) {
        log.info("[Tool:image_generation] 生成图片: prompt={}", prompt);

        if (apiKey == null || apiKey.isBlank()) {
            return "图片生成未配置，请在配置文件中设置 lightbot.siliconflow.api-key";
        }

        try {
            // 1. 调用 SiliconFlow API 生成图片
            Map<String, Object> body = Map.of(
                    "model", "Qwen/Qwen-Image",
                    "prompt", prompt,
                    "negative_prompt", negativePrompt != null ? negativePrompt : "",
                    "num_inference_steps", 20,
                    "guidance_scale", 7.5,
                    "batch_size", 1);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.siliconflow.cn/v1/images/generations"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(120))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[Tool:image_generation] API返回错误: status={}, body={}", response.statusCode(), response.body());
                return "图片生成失败，HTTP状态码: " + response.statusCode();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                return "图片生成失败：未返回图片数据";
            }

            String imageUrl = data.get(0).get("url").asText();

            // 2. 下载图片
            HttpRequest downloadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<byte[]> imageResponse = HTTP_CLIENT.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (imageResponse.statusCode() != 200) {
                return "图片下载失败，HTTP状态码: " + imageResponse.statusCode();
            }

            // 3. 上传到 MinIO
            String filePath = "generated/images/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
            minioUtil.upload(new ByteArrayInputStream(imageResponse.body()), filePath,
                    imageResponse.body().length, "image/jpeg");

            // 4. 返回访问 URL
            String presignedUrl = minioUtil.getPresignedUrl(filePath);
            log.info("[Tool:image_generation] 图片生成完成: path={}", filePath);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("image_url", presignedUrl);
            output.put("prompt", prompt);
            output.put("file_path", filePath);
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.error("[Tool:image_generation] 生成异常: prompt={}, error={}", prompt, e.getMessage());
            return "图片生成过程中发生错误: " + e.getMessage();
        }
    }
}
