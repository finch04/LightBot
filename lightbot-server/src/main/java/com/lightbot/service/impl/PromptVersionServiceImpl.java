package com.lightbot.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Prompt;
import com.lightbot.entity.PromptVersion;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.PromptVersionStatus;
import com.lightbot.mapper.PromptVersionMapper;
import com.lightbot.service.PromptService;
import com.lightbot.service.PromptVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提示词版本服务实现类
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptVersionServiceImpl extends ServiceImpl<PromptVersionMapper, PromptVersion>
        implements PromptVersionService {

    private final PromptService promptService;

    @Override
    @Transactional
    public PromptVersion create(String promptKey, String version, String versionDesc,
                                 String template, String variables, String modelConfig,
                                 String toolConfig, String status, Long userId) {
        // 1. 校验Prompt存在
        Prompt prompt = promptService.lambdaQuery().eq(Prompt::getPromptKey, promptKey).one();
        if (prompt == null) {
            throw new BizException(ErrorCode.PROMPT_NOT_FOUND);
        }

        // 2. 解析状态
        PromptVersionStatus versionStatus = "release".equals(status)
                ? PromptVersionStatus.RELEASE : PromptVersionStatus.PRE;

        // 3. 检查是否已存在release版本
        if (versionStatus == PromptVersionStatus.RELEASE) {
            long releaseCount = lambdaQuery()
                    .eq(PromptVersion::getPromptKey, promptKey)
                    .eq(PromptVersion::getVersion, version)
                    .eq(PromptVersion::getStatus, PromptVersionStatus.RELEASE)
                    .count();
            if (releaseCount > 0) {
                throw new BizException(ErrorCode.PROMPT_VERSION_EXISTS);
            }
        }

        // 4. 删除已有的pre版本（同promptKey同version）
        lambdaUpdate()
                .eq(PromptVersion::getPromptKey, promptKey)
                .eq(PromptVersion::getVersion, version)
                .eq(PromptVersion::getStatus, PromptVersionStatus.PRE)
                .remove();

        // 5. 创建新版本
        PromptVersion pv = new PromptVersion();
        pv.setPromptKey(promptKey);
        pv.setVersion(version);
        pv.setVersionDesc(versionDesc);
        pv.setTemplate(template);
        pv.setVariables(variables);
        pv.setModelConfig(modelConfig);
        pv.setToolConfig(toolConfig);
        pv.setStatus(versionStatus);
        pv.setUserId(userId);
        save(pv);

        // 6. 更新Prompt的latestVersion
        prompt.setLatestVersion(version);
        promptService.updateById(prompt);

        return pv;
    }

    @Override
    public PromptVersion getByKeyAndVersion(String promptKey, String version) {
        return lambdaQuery()
                .eq(PromptVersion::getPromptKey, promptKey)
                .eq(PromptVersion::getVersion, version)
                .one();
    }

    @Override
    public List<PromptVersion> listByKey(String promptKey) {
        return lambdaQuery()
                .eq(PromptVersion::getPromptKey, promptKey)
                .orderByDesc(PromptVersion::getCreateTime)
                .list();
    }
}
