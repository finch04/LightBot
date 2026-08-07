package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.service.DataPoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 数据池附件上传
 *
 * @author finch
 * @since 2026-07-26
 */
@Tag(name = "数据池附件")
@RestController
@RequestMapping("/api/data-pools/attachments")
@RequiredArgsConstructor
public class DataPoolAttachmentController {

    private final DataPoolService dataPoolService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传数据池附件")
    public Result<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(dataPoolService.uploadAttachment(file));
    }
}
